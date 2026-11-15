package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.ValueKind;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConstructorBodyIdentityTest {
  @TempDir Path directory;

  @Test
  void givenGenericVariableArityCreation_whenRetainedAndDerived_thenExactArgumentSlotsReachTheCanonicalGraphBody() throws IOException {
    // Arrange
    var source = """
        class Stored {
          <T extends Number> Stored(T... values) {}
          static Stored create(Integer first, Integer second) { return new Stored(first, second); }
        }
        """;
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")), source)));
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var production = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, configuration, AstProfile.JAVA_SOURCE_BOUNDED);
    var produced = production.result().orElseThrow();
    var encoded = AstCodec.encode(produced);
    var retained = AstCodec.decode(encoded, produced.identity());
    var method = retained.program().methods().stream().filter(value -> value.name().equals("create")).findFirst().orElseThrow();
    var body = retained.body(method.methodKey()).orElseThrow();
    var creation = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        .findFirst().orElseThrow();
    var outcome = CfgDerivers.standard().derive(retained,
        new CfgRequest(retained.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

    // Assert
    assertEquals(AstCompletion.COMPLETE, production.completion(), production.diagnostics().toString());
    assertArrayEquals(encoded, AstCodec.encode(retained));
    assertSame(retained.aggregate().node(body.root().nodeId()).orElseThrow(), body.root());
    assertEquals(List.of("first", "second"), creation.children(SyntaxRole.ARGUMENT).stream().map(node -> node.origin().orElseThrow())
        .map(span -> source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive())))
        .toList());
    for (var index = 0; index < 2; index++) {
      var argument = body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, index).orElseThrow();
      assertEquals("java.lang.Integer", argument.sourceType().displayName());
      assertEquals("java.lang.Integer", argument.selectedTarget().displayName());
    }
    var arity = body.invocationArity(creation.nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, arity.mode());
    assertEquals("java.lang.Integer[]", arity.varargArrayType().orElseThrow().displayName());
    assertEquals(MethodCfgState.AVAILABLE, outcome.state(), outcome.toString());
    var graph = outcome.graph().orElseThrow();
    var packed = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.VARARGS_ARRAY).findFirst().orElseThrow();
    var constructed = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.NEW_OBJECT).findFirst().orElseThrow();
    var array = packed.definedValue().orElseThrow();
    assertEquals("java.lang.Integer[]", array.staticType().orElseThrow().displayName());
    assertEquals(array, constructed.operands().getLast().value());
    assertEquals(retained.bodyRef(method.methodKey()).orElseThrow(), constructed.sourceRef().body());
    assertEquals(retained.occurrence(creation).orElseThrow(), constructed.sourceRef().occurrence().orElseThrow());
  }

  @Test
  void givenWrittenConstructorsWithInsertedSuperclassCalls_whenRetainedAndDerived_thenGeneratedOperationsKeepTheCanonicalBody()
      throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")), """
        class Parent { Parent(int... values) {} }
        class Stored extends Parent { Stored() {} }
        class Plain { Plain() {} }
        enum Status { ; Status() {} }
        """)));
    var produced = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var ast = AstCodec.decode(AstCodec.encode(produced), produced.identity());
    var constructors = ast.program().types().stream().filter(type -> !type.qualifiedName().equals("Parent"))
        .flatMap(type -> type.methods().stream()).filter(method -> method.constructor()).toList();
    assertEquals(3, constructors.size());
    for (var constructor : constructors) {
      var syntax = ast.body(constructor.methodKey()).orElseThrow();
      var declaration = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration value
          && value.declarationKey().equals(constructor.methodKey())).findFirst().orElseThrow();
      assertSame(declaration.requiredChild(SyntaxRole.BODY), syntax.root());
      assertSame(ast.aggregate().node(syntax.root().nodeId()).orElseThrow(), syntax.root());
      assertTrue(syntax.root().children(SyntaxRole.STATEMENT).isEmpty());
      assertEquals(Optional.of(MethodBodyOrigin.SOURCE_AST), ast.aggregate().methodBodyOrigin(constructor.methodKey()));
      assertEquals(Optional.of(syntax.root().nodeId()), ast.bodyRef(constructor.methodKey()).orElseThrow().sourceOccurrenceKey());
      var outcome = CfgDerivers.standard().derive(ast,
          new CfgRequest(ast.identity(), List.of(constructor.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();
      assertEquals(MethodCfgState.AVAILABLE, outcome.state());
      var graph = outcome.graph().orElseThrow();
      assertTrue(graph.operationNodes().stream().allMatch(operation -> operation.origins().isEmpty()
          && operation.sourceRef().body().equals(outcome.body().orElseThrow())
          && operation.sourceRef().occurrence().isEmpty()));
      var call = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.INVOKE).findFirst().orElseThrow();
      assertEquals("compiler-inserted-superclass/invoke", call.sourceRef().generatedRole());
      if (constructor.ownerTypeKey().equals("type:Stored")) {
        assertEquals(List.of(OperationKind.VARARGS_ARRAY, OperationKind.INVOKE), graph.operationNodes().stream().map(operation -> operation.kind()).toList());
        assertEquals(2, call.operands().size());
      } else if (constructor.ownerTypeKey().equals("type:Status")) {
        assertEquals(List.of(ValueKind.RECEIVER, ValueKind.IMPLICIT_PARAMETER, ValueKind.IMPLICIT_PARAMETER),
            call.operands().stream().map(operand -> operand.value().kind()).toList());
      } else {
        assertEquals(1, call.operands().size());
      }
    }
  }

  @Test
  void givenWrittenThisAndSuperDelegations_whenProducedRetainedAndDerived_thenEachConstructorUsesItsOneCanonicalSourceBody()
      throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")), """
        class Parent { Parent(int n) {} }
        class Stored extends Parent {
          Stored() { this(1); }
          Stored(int n) { super(n); }
        }
        """)));
    var produced = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var ast = AstCodec.decode(AstCodec.encode(produced), produced.identity());
    var constructors = ast.program().types().stream().filter(type -> type.qualifiedName().equals("Stored"))
        .flatMap(type -> type.methods().stream()).filter(method -> method.constructor()).toList();
    assertEquals(2, constructors.size());
    for (var constructor : constructors) {
      var syntax = ast.body(constructor.methodKey()).orElseThrow();
      var declaration = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration value
          && value.declarationKey().equals(constructor.methodKey())).findFirst().orElseThrow();
      assertSame(declaration.requiredChild(SyntaxRole.BODY), syntax.root());
      assertSame(ast.aggregate().node(syntax.root().nodeId()).orElseThrow(), syntax.root());
      assertEquals(Optional.of(MethodBodyOrigin.SOURCE_AST), ast.aggregate().methodBodyOrigin(constructor.methodKey()));
      assertEquals(Optional.of(syntax.root().nodeId()), ast.bodyRef(constructor.methodKey()).orElseThrow().sourceOccurrenceKey());
      var outcome = CfgDerivers.standard().derive(ast,
          new CfgRequest(ast.identity(), List.of(constructor.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();
      assertEquals(MethodCfgState.AVAILABLE, outcome.state());
      assertTrue(outcome.graph().orElseThrow().operationNodes().stream().allMatch(operation -> operation.sourceRef() != null));
    }
  }
}
