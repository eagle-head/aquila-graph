package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.ExecutionSyntax;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvocationSelectorCorrespondenceTest {
  private static final String SOURCE = """
      class Base {
        Base(int value) {}
        static int shared(int first, int second) { return first + second; }
        int virtual(int first, int second) { return first + second; }
      }
      class Calls extends Base {
        Calls() { this(1); }
        Calls(int value) { super(value); }
        int subject(Calls other) {
          shared(1, 2);
          Calls.shared(3, 4);
          other.shared(5, 6);
          virtual(7, 8);
          other.virtual(9, 10);
          this.virtual(11, 12);
          super.virtual(13, 14);
          return 0;
        }
        class Nested {
          int enclosed() {
            Calls.this.virtual(15, 16);
            return Calls.super.virtual(17, 18);
          }
        }
      }
      class Outer {
        class Parent { Parent(int value) {} }
      }
      class Child extends Outer.Parent {
        Child(Outer outer) { outer.super(19); }
      }
      """;

  @TempDir
  Path directory;

  @Test
  void givenLegalInvocationForms_whenAttributedProducedAndDecoded_thenSelectorKindsAndDetachedFactsRemainExact() throws IOException {
    // Arrange
    var cases = invocationCases();
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Calls.java")), SOURCE)));
    var request = new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES);

    // Act
    var compilerSelectors = observeSelectors(cases);
    var outcome = JavaFrontends.standard().produce(request);
    var original = outcome.result().orElseThrow();
    var bytes = AstCodec.encode(original);
    var decoded = AstCodec.decode(bytes, original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
    assertEquals(cases.size(), compilerSelectors.size());
    assertEquals(original.identity(), decoded.identity());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    for (var expected : cases) {
      assertEquals(expected.selectorKind(), compilerSelectors.get(expected.text()), expected.text());
      for (var result : List.of(original, decoded)) {
        assertInvocation(result, expected);
      }
    }
  }

  @Test
  void givenRetainedMethodInvocations_whenGraphsAreDerived_thenTargetsFormsAndSourceAssociationsRemainExact() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Calls.java")), SOURCE)));
    var original = JavaFrontends.standard().produce(new AstProductionRequest(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var request = new CfgRequest(retained.identity(), List.of(methodKey("Calls", "subject(Calls)")), CfgProfile.JAVA_BOUNDED);
    var expected = invocationCases().subList(0, 7);

    // Act
    var result = CfgDerivers.standard().derive(retained, request);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(1, result.methods().size());
    var method = result.methods().getFirst();
    assertEquals(MethodCfgState.AVAILABLE, method.state(), result.diagnostics().toString());
    var calls = method.graph().orElseThrow().operationNodes().stream().filter(node -> node.kind() == OperationKind.INVOKE).toList();
    assertEquals(7, calls.size());
    assertEquals(expected.stream().map(InvocationCase::form).toList(),
        calls.stream().map(node -> node.invocationSemantics().orElseThrow().form()).toList());
    for (var index = 0; index < expected.size(); index++) {
      var call = calls.get(index);
      var invocation = expected.get(index);
      assertEquals(invocation.mode(), call.invocationSemantics().orElseThrow().mode());
      assertEquals(List.of(Optional.of(invocation.target())), call.semanticReferences().stream().map(value -> value.symbolKey()).toList());
      assertTrue(call.origins().contains(span(retained, invocation.text())), invocation.text());
    }
  }

  private Map<String, Tree.Kind> observeSelectors(List<InvocationCase> cases) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var file = SimpleJavaFileObject.forSource(directory.resolve("Calls.java").toUri(), SOURCE);
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none"), null, List.of(file));
      var units = task.parse();
      task.analyze();
      assertTrue(diagnostics.getDiagnostics().stream().noneMatch(value -> value.getKind() == Diagnostic.Kind.ERROR),
          diagnostics.getDiagnostics().toString());
      var trees = Trees.instance(task);
      var observed = new LinkedHashMap<String, Tree.Kind>();
      var texts = cases.stream().map(InvocationCase::text).toList();
      for (var unit : units) {
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
            var start = trees.getSourcePositions().getStartPosition(unit, invocation);
            var end = trees.getSourcePositions().getEndPosition(unit, invocation);
            if (start >= 0 && end >= start) {
              var text = SOURCE.substring(Math.toIntExact(start), Math.toIntExact(end));
              if (texts.contains(text)) {
                assertTrue(trees.getElement(getCurrentPath()) instanceof ExecutableElement, text);
                assertTrue(observed.put(text, invocation.getMethodSelect().getKind()) == null, text);
              }
            }
            return super.visitMethodInvocation(invocation, unused);
          }
        }.scan(unit, null);
      }
      return Map.copyOf(observed);
    }
  }

  private static void assertInvocation(AstResult result, InvocationCase expected) {
    var matches = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION
        && node.origin().equals(Optional.of(span(result, expected.text())))).toList();
    assertEquals(1, matches.size(), expected.text());
    var node = matches.getFirst();
    assertSame(node, result.aggregate().node(node.nodeId()).orElseThrow());
    assertEquals(JavaSourceKind.METHOD_INVOCATION, node.sourceKind());
    var value = node.value(SyntaxValue.Invocation.class);
    assertEquals(expected.name(), value.name());
    assertEquals(expected.form(), value.form());
    assertEquals(expected.form(), value.semantics().orElseThrow().form());
    assertEquals(expected.mode(), value.semantics().orElseThrow().mode());
    assertEquals(Optional.of(expected.target()), node.requiredReference().symbolKey());
    assertEquals(expected.mode() == InvocationMode.CONSTRUCTOR ? ReferenceKind.CONSTRUCTOR : ReferenceKind.METHOD,
        node.requiredReference().referenceKind());
    var children = new ArrayList<ChildExpectation>();
    if (expected.qualifierRole().isPresent() && expected.form() != InvocationForm.STATIC_TYPE) {
      children.add(new ChildExpectation(expected.qualifierRole().orElseThrow(), 0, expected.qualifier()));
    }
    for (var position = 0; position < expected.arguments().size(); position++) {
      children.add(new ChildExpectation(SyntaxRole.ARGUMENT, position, expected.arguments().get(position)));
    }
    if (expected.form() == InvocationForm.STATIC_TYPE) {
      children.add(new ChildExpectation(SyntaxRole.QUALIFIER, 0, expected.qualifier()));
    }
    assertEquals(children.stream().map(ChildExpectation::role).toList(), node.children().stream().map(child -> child.role()).toList());
    var invocationStart = SOURCE.indexOf(expected.text());
    for (var index = 0; index < children.size(); index++) {
      var child = node.children().get(index);
      var childExpected = children.get(index);
      assertEquals(childExpected.position(), child.position());
      var start = SOURCE.indexOf(childExpected.text(), invocationStart);
      assertEquals(new SourceSpan(node.sourceFileKey(), start, start + childExpected.text().length()), child.node().origin().orElseThrow());
      assertEquals(node.structuralPath().child(childExpected.role(), childExpected.position()), child.node().structuralPath());
      assertSame(child.node(), result.aggregate().node(child.node().nodeId()).orElseThrow());
    }
    var scope = result.aggregate().methodSyntax(expected.ownerMethod()).orElseThrow();
    assertEquals(expected.ownerMethod(), scope.scopeKey());
    assertTrue(scope.root().descendantsAndSelf().stream().anyMatch(valueNode -> valueNode.nodeId().equals(node.nodeId())), expected.text());
    assertApplicability(scope, node, expected);
  }

  private static void assertApplicability(ExecutionSyntax scope, SyntaxNode node, InvocationCase expected) {
    var applicability = scope.invocationArity(node.nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.FIXED, applicability.mode());
    assertEquals(expected.arguments().size(), applicability.formalCount());
    assertEquals(EvidenceState.KNOWN, scope.invocationEvidence(node.nodeId()).orElseThrow().applicability().state());
    for (var position = 0; position < expected.arguments().size(); position++) {
      var use = scope.useDecision(node.nodeId(), SyntaxRole.ARGUMENT, position).orElseThrow();
      assertEquals("int", use.sourceType().erasedName());
      assertEquals("int", use.selectedTarget().erasedName());
    }
    assertEquals(expected.mode() == InvocationMode.STATIC, scope.staticAccess(node.nodeId()).isPresent());
    scope.staticAccess(node.nodeId()).ifPresent(access -> assertEquals(expected.target(), access.selectedMemberKey()));
  }

  private static SourceSpan span(AstResult result, String text) {
    var start = SOURCE.indexOf(text);
    assertTrue(start >= 0, text);
    return new SourceSpan(result.aggregate().roots().getFirst().sourceFileKey(), start, start + text.length());
  }

  private static List<InvocationCase> invocationCases() {
    var subject = methodKey("Calls", "subject(Calls)");
    var enclosed = methodKey("Calls$Nested", "enclosed()");
    return List.of(
        new InvocationCase("shared(1, 2)", "shared", InvocationForm.STATIC_UNQUALIFIED, InvocationMode.STATIC,
            methodKey("Base", "shared(int,int)"), Tree.Kind.IDENTIFIER, Optional.empty(), "", List.of("1", "2"), subject),
        new InvocationCase("Calls.shared(3, 4)", "shared", InvocationForm.STATIC_TYPE, InvocationMode.STATIC,
            methodKey("Base", "shared(int,int)"), Tree.Kind.MEMBER_SELECT, Optional.of(SyntaxRole.QUALIFIER), "Calls", List.of("3", "4"), subject),
        new InvocationCase("other.shared(5, 6)", "shared", InvocationForm.STATIC_EXPRESSION, InvocationMode.STATIC,
            methodKey("Base", "shared(int,int)"), Tree.Kind.MEMBER_SELECT, Optional.of(SyntaxRole.RECEIVER), "other", List.of("5", "6"), subject),
        new InvocationCase("virtual(7, 8)", "virtual", InvocationForm.INSTANCE_IMPLICIT, InvocationMode.VIRTUAL,
            methodKey("Base", "virtual(int,int)"), Tree.Kind.IDENTIFIER, Optional.empty(), "", List.of("7", "8"), subject),
        new InvocationCase("other.virtual(9, 10)", "virtual", InvocationForm.INSTANCE_EXPRESSION, InvocationMode.VIRTUAL,
            methodKey("Base", "virtual(int,int)"), Tree.Kind.MEMBER_SELECT, Optional.of(SyntaxRole.RECEIVER), "other", List.of("9", "10"), subject),
        new InvocationCase("this.virtual(11, 12)", "virtual", InvocationForm.INSTANCE_THIS, InvocationMode.VIRTUAL,
            methodKey("Base", "virtual(int,int)"), Tree.Kind.MEMBER_SELECT, Optional.of(SyntaxRole.QUALIFIER), "this", List.of("11", "12"), subject),
        new InvocationCase("super.virtual(13, 14)", "virtual", InvocationForm.SUPER, InvocationMode.SUPER,
            methodKey("Base", "virtual(int,int)"), Tree.Kind.MEMBER_SELECT, Optional.of(SyntaxRole.QUALIFIER), "super", List.of("13", "14"), subject),
        new InvocationCase("Calls.this.virtual(15, 16)", "virtual", InvocationForm.INSTANCE_QUALIFIED_THIS, InvocationMode.VIRTUAL,
            methodKey("Base", "virtual(int,int)"), Tree.Kind.MEMBER_SELECT, Optional.of(SyntaxRole.QUALIFIER), "Calls.this", List.of("15", "16"), enclosed),
        new InvocationCase("Calls.super.virtual(17, 18)", "virtual", InvocationForm.QUALIFIED_SUPER, InvocationMode.SUPER,
            methodKey("Base", "virtual(int,int)"), Tree.Kind.MEMBER_SELECT, Optional.of(SyntaxRole.QUALIFIER), "Calls.super", List.of("17", "18"), enclosed),
        new InvocationCase("this(1)", "<init>", InvocationForm.THIS_CONSTRUCTOR, InvocationMode.CONSTRUCTOR,
            methodKey("Calls", "<init>(int)"), Tree.Kind.IDENTIFIER, Optional.empty(), "", List.of("1"), methodKey("Calls", "<init>()")),
        new InvocationCase("super(value)", "<init>", InvocationForm.SUPER_CONSTRUCTOR, InvocationMode.CONSTRUCTOR,
            methodKey("Base", "<init>(int)"), Tree.Kind.IDENTIFIER, Optional.empty(), "", List.of("value"), methodKey("Calls", "<init>(int)")),
        new InvocationCase("outer.super(19)", "<init>", InvocationForm.QUALIFIED_SUPER_CONSTRUCTOR, InvocationMode.CONSTRUCTOR,
            methodKey("Outer$Parent", "<init>(int)"), Tree.Kind.MEMBER_SELECT, Optional.of(SyntaxRole.RECEIVER), "outer", List.of("19"),
            methodKey("Child", "<init>(Outer)")));
  }

  private static String methodKey(String binaryOwner, String signature) {
    return new JavaTypeDeclarationIdentity(Optional.empty(), binaryOwner).declarationKey() + "#" + signature;
  }

  private record ChildExpectation(SyntaxRole role, int position, String text) {}

  private record InvocationCase(String text, String name, InvocationForm form, InvocationMode mode, String target,
      Tree.Kind selectorKind, Optional<SyntaxRole> qualifierRole, String qualifier, List<String> arguments, String ownerMethod) {}
}
