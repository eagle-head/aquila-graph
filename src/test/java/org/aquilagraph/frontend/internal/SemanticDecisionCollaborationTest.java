package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.ExecutionSyntax;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.StaticMemberKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticDecisionCollaborationTest {
  @TempDir
  Path directory;

  @Test
  void givenStaticCallsInsideStringsAcrossExecutionScopes_whenProducedAndRetained_thenAllSemanticFamiliesRemainIndependent()
      throws IOException {
    // Arrange
    var source = """
        class Numbers {
          static long selected(long first, long... rest) { return first; }
        }
        class Display extends Numbers {
          static String field = "field:" + Display.selected(Integer.valueOf(3), 4, 5);
          static { String text = "block:" + selected(Integer.valueOf(3), 4, 5); }
          Display() { String text = "constructor:" + Display.selected(Integer.valueOf(3), 4, 5); }
          static String subject() { return "method:" + selected(Integer.valueOf(3), 4, 5); }
        }
        enum Label {
          VALUE("enum:" + Numbers.selected(Integer.valueOf(3), 4, 5));
          Label(String text) {}
        }
        """;
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Display.java"), source)));

    // Act
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION).result().orElseThrow();
    var bytes = AstCodec.encode(original);
    var retained = AstCodec.decode(bytes, original.identity());
    var derived = CfgDerivers.standard().derive(retained,
        new CfgRequest(retained.identity(), List.of("type:Display#subject()"), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertTrue(original.diagnostics().isEmpty());
    assertEquals(original.identity(), retained.identity());
    assertArrayEquals(bytes, AstCodec.encode(retained));
    for (var result : List.of(original, retained)) {
      var scopes = selectedScopes(result);
      assertEquals(5, scopes.size());
      assertEquals(2, scopes.stream().filter(scope -> scope.scope() instanceof ExecutionScope.Method).count());
      assertEquals(1, scopes.stream().filter(scope -> scope.scope() instanceof ExecutionScope.Field).count());
      assertEquals(1, scopes.stream().filter(scope -> scope.scope() instanceof ExecutionScope.Initializer).count());
      assertEquals(1, scopes.stream().filter(scope -> scope.scope() instanceof ExecutionScope.EnumConstant).count());
      for (var scope : scopes) {
        assertCoexistingFacts(scope);
        var selected = invocation(scope, "selected");
        assertSame(selected, result.aggregate().node(selected.nodeId()).orElseThrow());
        for (var other : scopes) {
          if (!other.scopeKey().equals(scope.scopeKey())) {
            assertTrue(other.invocationArity(selected.nodeId()).isEmpty());
            assertTrue(other.staticAccess(selected.nodeId()).isEmpty());
            assertTrue(other.useEvidence(selected.nodeId(), SyntaxRole.ARGUMENT, 0).isEmpty());
          }
        }
      }
      var field = result.aggregate().executionSyntax("type:Display#field:field").orElseThrow();
      assertEquals("java.lang.String",
          field.useDecision(field.root().nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().erasedName());
      var constant = scopes.stream().filter(scope -> scope.scope() instanceof ExecutionScope.EnumConstant).findFirst().orElseThrow();
      assertEquals("java.lang.String",
          constant.useDecision(constant.root().nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
      assertEquals(Optional.of("type:Label#<init>(java.lang.String)"),
          ((ExecutionScope.EnumConstant) constant.scope()).selectedConstructor().orElseThrow().symbolKey());
    }
    assertEquals(MethodCfgState.AVAILABLE, derived.methods().getFirst().state(), derived.diagnostics().toString());
    var semanticOperations = List.of(OperationKind.INVOKE, OperationKind.UNBOX, OperationKind.VARARGS_ARRAY,
        OperationKind.STRING_CONVERSION, OperationKind.STRING_CONCAT);
    assertEquals(List.of(OperationKind.INVOKE, OperationKind.UNBOX, OperationKind.VARARGS_ARRAY, OperationKind.INVOKE,
        OperationKind.STRING_CONVERSION, OperationKind.STRING_CONCAT),
        derived.methods().getFirst().graph().orElseThrow().operationNodes().stream()
            .map(node -> node.kind()).filter(semanticOperations::contains).toList());
  }

  private static void assertCoexistingFacts(ExecutionSyntax scope) {
    var selected = invocation(scope, "selected");
    var boxed = invocation(scope, "valueOf");
    var plus = scope.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINARY).findFirst().orElseThrow();
    assertEquals(Optional.of("type:Numbers#selected(long,long[])"), selected.requiredReference().symbolKey());
    var selection = selected.value(SyntaxValue.Invocation.class);
    var unqualified = scope.scope() instanceof ExecutionScope.Initializer || scope.scopeKey().equals("type:Display#subject()");
    assertEquals(unqualified ? InvocationForm.STATIC_UNQUALIFIED : InvocationForm.STATIC_TYPE, selection.form());
    assertEquals(InvocationMode.STATIC, selection.semantics().orElseThrow().mode());
    if (!unqualified) {
      assertEquals(scope.scope() instanceof ExecutionScope.EnumConstant ? "Numbers" : "Display",
          selection.semantics().orElseThrow().qualifyingType().identity().erasedName());
    }
    var applicability = scope.invocationArity(selected.nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, applicability.mode());
    assertEquals(2, applicability.formalCount());
    assertEquals("long[]", applicability.varargArrayType().orElseThrow().erasedName());
    assertEquals("long[]", applicability.erasedArrayType().orElseThrow().erasedName());
    assertEquals("long", applicability.elementType().orElseThrow().erasedName());
    assertEquals(scope.scopeKey(), applicability.scopeKey());
    assertEquals(selected.nodeId(), applicability.consumerNodeId());
    assertEquals(EvidenceState.KNOWN, scope.invocationEvidence(selected.nodeId()).orElseThrow().applicability().state());
    assertEquals(List.of("java.lang.Integer", "int", "int"), selected.children(SyntaxRole.ARGUMENT).stream()
        .map(node -> node.resolvedType().orElseThrow().erasedName()).toList());
    for (var position = 0; position < 3; position++) {
      var use = scope.useDecision(selected.nodeId(), SyntaxRole.ARGUMENT, position).orElseThrow();
      assertEquals(scope.scopeKey(), use.scopeKey());
      assertEquals(selected.nodeId(), use.consumerNodeId());
      assertEquals("long", use.selectedTarget().erasedName());
      assertEquals(position == 0 ? Optional.of("long") : Optional.empty(),
          use.effectivePrimitiveType().map(type -> type.erasedName()));
      assertEquals(position == 0 ? Optional.of("int") : Optional.empty(), use.unboxedType().map(type -> type.erasedName()));
      assertTrue(use.checkedReferenceTarget().isEmpty());
      assertTrue(use.preBoxPrimitiveType().isEmpty());
      assertTrue(use.boxedType().isEmpty());
    }
    var fixed = scope.invocationArity(boxed.nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.FIXED, fixed.mode());
    assertEquals(1, fixed.formalCount());
    assertEquals(scope.scopeKey(), fixed.scopeKey());
    assertEquals(boxed.nodeId(), fixed.consumerNodeId());
    assertTrue(fixed.varargArrayType().isEmpty());
    assertEquals(Optional.of("type:java.lang.Integer#valueOf(int)"), boxed.requiredReference().symbolKey());
    assertEquals("int", scope.useDecision(boxed.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
    var staticAccess = scope.staticAccess(selected.nodeId()).orElseThrow();
    assertEquals("type:Numbers#selected(long,long[])", staticAccess.selectedMemberKey());
    assertEquals(StaticMemberKind.METHOD_INVOKE, staticAccess.kind());
    assertEquals("Numbers", staticAccess.declaringType().identity().erasedName());
    assertEquals(Optional.of("type:Numbers"), staticAccess.declaringType().declarationKey());
    assertEquals(EvidenceState.NOT_APPLICABLE, staticAccess.constantVariable().state());
    assertEquals(scope.scopeKey(), staticAccess.declaringOwner().origin().scopeKey());
    assertEquals(selected.nodeId(), staticAccess.declaringOwner().origin().nodeKey());
    assertEquals(selected.origin(), staticAccess.declaringOwner().origin().sourceRange());
    assertEquals("java.lang.Integer", scope.staticAccess(boxed.nodeId()).orElseThrow().declaringType().identity().erasedName());
    var string = scope.stringConcatenation(plus.nodeId()).orElseThrow();
    assertEquals("java.lang.String", string.stringType().erasedName());
    assertEquals(Optional.of(true), string.leftIsString().knownValue());
    assertEquals(Optional.of(false), string.rightIsString().knownValue());
    assertEquals(Optional.of(false), string.compileTimeConstant().knownValue());
    assertEquals(scope.scopeKey(), string.compileTimeConstant().origin().scopeKey());
    assertEquals(plus.nodeId(), string.compileTimeConstant().origin().nodeKey());
    assertEquals(plus.origin(), string.compileTimeConstant().origin().sourceRange());
    var resultUse = scope.useDecision(plus.nodeId(), SyntaxRole.RIGHT_OPERAND, 0).orElseThrow();
    assertEquals("long", resultUse.sourceType().erasedName());
    assertEquals("long", resultUse.selectedTarget().erasedName());
    assertTrue(resultUse.boxedType().isEmpty());
    assertTrue(scope.stringConcatenation(selected.nodeId()).isEmpty());
  }

  private static List<ExecutionSyntax> selectedScopes(AstResult result) {
    return result.aggregate().executionScopes().stream()
        .map(scope -> result.aggregate().executionSyntax(scope.key()).orElseThrow())
        .filter(scope -> scope.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.INVOCATION
            && node.value(SyntaxValue.Invocation.class).name().equals("selected")))
        .toList();
  }

  private static SyntaxNode invocation(ExecutionSyntax scope, String name) {
    return scope.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION
        && node.value(SyntaxValue.Invocation.class).name().equals(name)).findFirst().orElseThrow();
  }
}
