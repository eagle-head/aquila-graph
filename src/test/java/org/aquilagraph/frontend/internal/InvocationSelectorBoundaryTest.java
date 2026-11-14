package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvocationSelectorBoundaryTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final AstProfile PROFILE = AstProfile.JAVA_SOURCE_MODULE_SERVICES;

  @TempDir Path directory;

  @Test
  void givenUnresolvedOrMalformedCall_whenCompilerAndProducerRun_thenExactFailureAndFollowingHealthyResultSurvive()
      throws IOException {
    for (var expected : erroneousCalls().toList()) {
      // Arrange
      var input = snapshot("class Sample { void known() {} void run() { " + expected.expression() + "; } }");
      var frontend = JavaFrontends.standard();

      // Act
      var observations = selectors(input);
      var outcome = frontend.produce(new AstProductionRequest(input, CONFIGURATION, PROFILE));
      var healthy = frontend.produce(new AstProductionRequest(snapshot("class Sample { void known() {} void run() { known(); } }"),
          CONFIGURATION, PROFILE));

      // Assert
      assertEquals(expected.selectors(), observations.stream().map(SelectorObservation::selector).toList());
      assertEquals(AstCompletion.INCOMPLETE, outcome.completion(), outcome.diagnostics().toString());
      assertEquals(expected.diagnostics(), outcome.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      assertTrue(outcome.diagnostics().stream().filter(diagnostic -> diagnostic.code().startsWith("compiler.err."))
          .allMatch(diagnostic -> diagnostic.isError() && !diagnostic.origins().isEmpty()));
      var result = outcome.result().orElseThrow();
      var invocations = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION
          && !node.value(SyntaxValue.Invocation.class).name().equals("<init>")).toList();
      assertEquals(observations.size(), invocations.size());
      for (var observed : observations) {
        var matches = invocations.stream().filter(node -> node.origin().equals(observed.origin())).toList();
        assertEquals(1, matches.size());
        var value = matches.getFirst().value(SyntaxValue.Invocation.class);
        assertEquals(observed.selector().name(), value.name());
        assertEquals(observed.selector().form(), value.form());
        assertEquals(observed.selector().selected(), value.semantics().isPresent());
      }
      assertEquals(AstCompletion.COMPLETE, healthy.completion(), healthy.diagnostics().toString());
      assertEquals(List.of(), healthy.diagnostics());
      var healthyCalls = healthy.result().orElseThrow().aggregate().nodes().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION && node.value(SyntaxValue.Invocation.class).name().equals("known"))
          .toList();
      assertEquals(1, healthyCalls.size());
      assertEquals(InvocationForm.INSTANCE_IMPLICIT, healthyCalls.getFirst().value(SyntaxValue.Invocation.class).form());
      assertTrue(healthyCalls.getFirst().requiredReference().symbolKey().isPresent());
    }
  }

  @Test
  void givenSuppliedLiteralSelector_whenMappingIsRequested_thenInvalidShapeRejectsBeforeCompilerLookup() throws IOException {
    // Arrange
    var input = snapshot("class Sample { int value() { return 1; } }");
    withCompilation(input, compilation -> {
      var literals = new ArrayList<TreePath>();
      new TreePathScanner<Void, Void>() {
        @Override
        public Void visitLiteral(LiteralTree literal, Void unused) {
          literals.add(getCurrentPath());
          return super.visitLiteral(literal, unused);
        }
      }.scan(compilation.units().getFirst(), null);
      var literal = literals.getFirst();
      var supplied = new SuppliedInvocation((ExpressionTree) literal.getLeaf());
      var path = new TreePath(literal.getParentPath(), supplied);
      var mapping = mapping(compilation);

      // Act
      var failure = assertThrows(IllegalArgumentException.class,
          () -> mapping.describe(supplied, path, SyntaxStructuralPath.sourceFileRoot()));

      // Assert
      assertEquals("Method invocation requires an identifier or member selector: INT_LITERAL", failure.getMessage());
      return null;
    });
  }

  private InputSnapshot snapshot(String content) {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Sample.java"), content)));
  }

  private static List<SelectorObservation> selectors(InputSnapshot input) throws IOException {
    return withCompilation(input, compilation -> {
      var observations = new ArrayList<SelectorObservation>();
      var mapping = mapping(compilation);
      new TreePathScanner<Void, Void>() {
        @Override
        public Void visitMethod(MethodTree method, Void unused) {
          return method.getName().contentEquals("run") ? super.visitMethod(method, unused) : null;
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
          var description = mapping.describe(invocation, getCurrentPath(), SyntaxStructuralPath.sourceFileRoot());
          var value = (SyntaxValue.Invocation) description.value();
          observations.add(new SelectorObservation(new Selector(invocation.getMethodSelect().getKind(), value.name(),
              compilation.semantics().element(getCurrentPath()) instanceof ExecutableElement, value.form()),
              compilation.sourceEvidence().origin(getCurrentPath().getCompilationUnit(), input.sourceFiles().getFirst(), invocation)));
          return super.visitMethodInvocation(invocation, unused);
        }
      }.scan(compilation.units().getFirst(), null);
      return List.copyOf(observations);
    });
  }

  private static JavaExecutableSyntaxProfile mapping(JavaCompilation compilation) {
    return new JavaExecutableSyntaxProfile(compilation.semantics(),
        JavaExecutableProjectionContext.remainingAttributedBody(JavaTranslationPolicy.forProfile(PROFILE)));
  }

  private static <T> T withCompilation(InputSnapshot input, Function<JavaCompilation, T> action) throws IOException {
    var policy = JavaTranslationPolicy.forProfile(PROFILE);
    var sources = new SnapshotSourceCatalog(input);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      return action.apply(session.analyze(session.parse(), policy));
    }
  }

  private static Stream<ErroneousCall> erroneousCalls() {
    return Stream.of(
        new ErroneousCall("missing()", List.of(new Selector(Tree.Kind.IDENTIFIER, "missing", false, InvocationForm.INSTANCE_IMPLICIT)),
            List.of("compiler.err.cant.resolve.location.args", "frontend.error-type", "frontend.unresolved-reference")),
        new ErroneousCall("this.missing()", List.of(new Selector(Tree.Kind.MEMBER_SELECT, "missing", false, InvocationForm.INSTANCE_THIS)),
            List.of("compiler.err.cant.resolve.args", "frontend.error-type", "frontend.unresolved-reference")),
        new ErroneousCall("known(", List.of(new Selector(Tree.Kind.IDENTIFIER, "known", false, InvocationForm.INSTANCE_IMPLICIT)),
            List.of("compiler.err.illegal.start.of.expr", "frontend.unsupported-tree-kind.erroneous", "frontend.error-type",
                "frontend.unresolved-reference", "frontend.unsupported-syntax", "frontend.unexpected-source-evidence")),
        new ErroneousCall("this.()", List.of(new Selector(Tree.Kind.MEMBER_SELECT, "<error>", false, InvocationForm.INSTANCE_THIS)),
            List.of("compiler.err.expected", "frontend.error-type", "frontend.unresolved-reference")),
        new ErroneousCall("(known)()", List.of(), List.of("compiler.err.expected", "compiler.err.not.stmt", "compiler.err.cant.resolve.location",
            "frontend.unsupported-tree-kind.erroneous", "frontend.unsupported-syntax", "frontend.unexpected-source-evidence")),
        new ErroneousCall("known()()", List.of(new Selector(Tree.Kind.IDENTIFIER, "known", true, InvocationForm.INSTANCE_IMPLICIT)),
            List.of("compiler.err.expected")),
        new ErroneousCall("<String>()", List.of(), List.of("compiler.err.illegal.start.of.expr", "frontend.unsupported-tree-kind.erroneous",
            "frontend.unsupported-syntax", "frontend.unexpected-source-evidence")));
  }

  private record Selector(Tree.Kind kind, String name, boolean selected, InvocationForm form) {}
  private record SelectorObservation(Selector selector, Optional<SourceSpan> origin) {}
  private record ErroneousCall(String expression, List<Selector> selectors, List<String> diagnostics) {}

  private record SuppliedInvocation(ExpressionTree selector) implements MethodInvocationTree {
    @Override public List<? extends Tree> getTypeArguments() { throw new AssertionError("Rejected selector must not read type arguments"); }
    @Override public ExpressionTree getMethodSelect() { return selector; }
    @Override public List<? extends ExpressionTree> getArguments() { throw new AssertionError("Rejected selector must not read arguments"); }
    @Override public Kind getKind() { return Kind.METHOD_INVOCATION; }
    @Override public <R, D> R accept(TreeVisitor<R, D> visitor, D data) { return visitor.visitMethodInvocation(this, data); }
  }
}
