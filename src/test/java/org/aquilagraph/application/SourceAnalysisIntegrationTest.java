package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceAnalysisIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenParenthesizedTargetBeforeOrAfterUnavailableSyntax_whenDerived_thenSourceAndCfgDiagnosticOrdersRemainIndependent() throws IOException {
    // Arrange
    var bodies = List.of("(a) = 1; synchronized (this) { a = 0; }", "synchronized (this) { a = 0; } (a) = 1;");
    var sourceMessages = new ArrayList<List<String>>();
    var graphCodes = new ArrayList<List<String>>();
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    for (var body : bodies) {
      var snapshot = InputSnapshot.fromSources(temporaryDirectory, List.of(SourceFile.fromContent(Path.of("Parenthesized.java"),
          "class Parenthesized { int value(int a) { " + body + " return a; } }")));
      var ast = frontend.produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);
      var run = new SourceAnalysisFixture().analyze(ast);
      sourceMessages.add(ast.diagnostics().stream().filter(value -> value.code().equals("frontend.unsupported-syntax"))
          .map(value -> value.message()).toList());
      graphCodes.add(run.representation().cfg().orElseThrow().methods().stream().filter(value ->
          run.representation().ast().result().orElseThrow().method(value.methodKey()).orElseThrow().name().equals("value")).findFirst().orElseThrow().diagnostics().stream()
          .map(value -> value.code()).toList());
      assertEquals(ast.diagnostics(), run.representation().ast().diagnostics());
    }

    // Assert
    assertEquals(List.of(List.of("Unsupported Java construct in method body: SYNCHRONIZED"),
        List.of("Unsupported Java construct in method body: SYNCHRONIZED")), sourceMessages);
    assertEquals(List.of(List.of("control-flow.unsupported", "control-flow.source-unavailable"),
        List.of("control-flow.source-unavailable", "control-flow.unsupported")), graphCodes);
  }

  @Test
  void givenSourceSupportedParenthesizedTargets_whenDerivedThroughNativePorts_thenCfgRefusalsRetainQualifiedOccurrencesAndOrder() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(temporaryDirectory, List.of(SourceFile.fromContent(Path.of("Parenthesized.java"),
        "class Parenthesized { int value(int a) { (a) = 1; (a) = 2; return a; } }")));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);

    // Act
    var run = new SourceAnalysisFixture().analyze(ast);
    var method = run.representation().cfg().orElseThrow().methods().stream().filter(value ->
          run.representation().ast().result().orElseThrow().method(value.methodKey()).orElseThrow().name().equals("value")).findFirst().orElseThrow();
    var blockers = method.blockers();

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertTrue(ast.diagnostics().isEmpty());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertEquals(List.of("control-flow.unsupported", "control-flow.unsupported"),
        run.diagnostics().stream().map(value -> value.code()).toList());
    assertEquals(List.of("(a) = 1", "(a) = 2"), blockers.stream().map(value -> value.diagnostic().origins().getFirst())
        .map(range -> snapshot.sourceFiles().getFirst().content().substring((int) range.startOffset(), (int) range.endOffsetExclusive())).toList());
    for (var blocker : blockers) {
      var occurrence = blocker.occurrence().orElseThrow();
      assertEquals(ast.result().orElseThrow().identity(), occurrence.astIdentity());
      assertTrue(ast.result().orElseThrow().node(occurrence).isPresent());
      assertEquals("construct", blocker.slot());
      assertEquals(StableId.sha256("cfg-diagnostic", occurrence.astIdentity().astResultKey(), method.methodKey(), occurrence.nodeKey(),
          "UNSUPPORTED", "construct"), blocker.diagnostic().diagnosticKey());
      assertEquals(AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE, blocker.diagnostic().outcomeKind());
    }
    assertEquals(blockers.stream().map(value -> value.diagnostic()).toList(), method.diagnostics());
    assertTrue(ast.diagnostics().isEmpty());
  }

  @Test
  void givenAcceptedBodylessProgram_whenAnalysisRuns_thenEmptyRequiredBodySetCanComplete() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Bodyless.java"), "interface Bodyless { int value(); }");
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Act
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    assertEquals(1, run.representation().ast().result().orElseThrow().program().methodCount());
    assertEquals(List.of(), run.representation().cfg().orElseThrow().methods());
    assertFalse(run.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
  }

  @Test
  void givenSupportedBranchingSource_whenAnalysisRuns_thenCfgPreservesTypedFlow()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Example.java"),
        """
        class Example {
          int choose(boolean enabled, int left, int right) {
            if (enabled && left > 0) {
              return left / right;
            }
            return 0;
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var engine = new SourceAnalysisFixture();

    // Act
    var run =
        engine.analyze(frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)));

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    var methodAnalysis =
        run.representation().cfg().orElseThrow().methods().stream()
            .filter(analysis -> run.representation().ast().result().orElseThrow().method(analysis.methodKey()).orElseThrow().name().equals("choose"))
            .findFirst()
            .orElseThrow();
    assertTrue(
        methodAnalysis.graph().orElseThrow().operationNodes().stream()
            .anyMatch(node -> node.kind() == OperationKind.DECISION));
    assertTrue(
        methodAnalysis.graph().orElseThrow().edges().stream()
            .anyMatch(edge -> edge.transferKind() == TransferKind.TRUE_BRANCH));
    assertTrue(
        methodAnalysis.graph().orElseThrow().edges().stream()
            .anyMatch(edge -> edge.transferKind() == TransferKind.FALSE_BRANCH));
    assertTrue(
        methodAnalysis.graph().orElseThrow().edges().stream()
            .anyMatch(
                edge ->
                    edge.transferKind() == TransferKind.EXCEPTIONAL
                        && edge.discriminator().orElse("").equals("java.lang.ArithmeticException")));
    assertFalse(methodAnalysis.graph().orElseThrow().nodes().isEmpty());
  }

  @Test
  void givenSyntaxError_whenAnalysisRuns_thenRunIsIncompleteAndDiagnosticIsRetained()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Broken.java"), "class Broken { void x( }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run =
        new SourceAnalysisFixture()
            .analyze(frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)));

    // Assert
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertTrue(run.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
  }

  @Test
  void givenUnsupportedExecutableClassMembers_whenAnalysisRuns_thenRunCannotComplete() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Initialization.java"), """
        class Initialization {
          static int staticValue = 1;
          int instanceValue = 2;
          static { staticValue = 3; }
          { instanceValue = 4; }
          void supportedMethod() { }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run = new SourceAnalysisFixture()
        .analyze(frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)));

    // Assert
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertTrue(run.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("frontend.unsupported-")));
  }

  @Test
  void givenUnavailableElementsAndErrorTypes_whenAnalysisRuns_thenStructuredDiagnosticsPreventCompletion() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("BrokenSemantics.java"), """
        class BrokenSemantics {
          MissingType field;
          MissingType evaluate(MissingType value) { return field; }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run = new SourceAnalysisFixture()
        .analyze(frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)));

    // Assert
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    var codes = run.diagnostics().stream().map(diagnostic -> diagnostic.code()).collect(Collectors.toSet());
    assertTrue(codes.contains("frontend.error-field-type"));
    assertTrue(codes.contains("frontend.error-parameter-type"));
    assertTrue(codes.contains("frontend.error-type"));
    assertTrue(codes.contains("frontend.error-reference"));
    assertTrue(codes.contains("control-flow.erroneous-evidence"));
    assertTrue(run.diagnostics().stream().filter(diagnostic -> diagnostic.code().startsWith("frontend.error-"))
        .allMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
    assertTrue(run.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("control-flow.erroneous-evidence"))
        .allMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
  }

  @Test
  void givenEmptySourceRoot_whenAnalysisRuns_thenRunIsRejected() throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run =
        new SourceAnalysisFixture()
            .analyze(frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)));

    // Assert
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertTrue(run.representation().ast().result().isEmpty());
  }

  @Test
  void givenSameCapturedTarget_whenAnalysisRunsTwice_thenRunIdentityMatchesTargetSignature() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example {}");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var engine = new SourceAnalysisFixture();
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var firstRun = engine.analyze(frontend.analyze(temporaryDirectory, configuration));
    var secondRun = engine.analyze(frontend.analyze(temporaryDirectory, configuration));

    // Assert
    assertEquals(firstRun.identity(), secondRun.identity());
    assertEquals(firstRun.identity().key(), secondRun.identity().key());
  }

  @Test
  void givenDependencyContentChangesAtSamePath_whenAnalysisRuns_thenRunIdentityChanges()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Example.java"),
        "class Example { int value() { return 1; } }");
    var dependency = Files.createDirectory(temporaryDirectory.resolve("dependency"));
    Files.writeString(dependency.resolve("marker.txt"), "first");
    var configuration =
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(dependency), List.of(), List.of());
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var engine = new SourceAnalysisFixture();

    // Act
    var firstRun = engine.analyze(frontend.analyze(temporaryDirectory, configuration));
    Files.writeString(dependency.resolve("marker.txt"), "second");
    var secondRun = engine.analyze(frontend.analyze(temporaryDirectory, configuration));

    // Assert
    assertNotEquals(firstRun.identity().key(), secondRun.identity().key());
  }

  @Test
  void givenEquivalentDependencyContentAtDifferentPaths_whenAnalysisRuns_thenContentSignatureIsStableAndExactCaptureIdentityChanges()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Example.java"),
        "class Example { int value() { return 1; } }");
    var firstDependency = Files.createDirectory(temporaryDirectory.resolve("first-dependency"));
    var secondDependency = Files.createDirectory(temporaryDirectory.resolve("second-dependency"));
    Files.writeString(firstDependency.resolve("marker.txt"), "same");
    Files.writeString(secondDependency.resolve("marker.txt"), "same");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var engine = new SourceAnalysisFixture();

    // Act
    var firstRun =
        engine.analyze(
            frontend.analyze(
                temporaryDirectory,
                new CompilerConfiguration(
                    JavaRelease.JAVA_25, List.of(firstDependency), List.of(), List.of())));
    var secondRun =
        engine.analyze(
            frontend.analyze(
                temporaryDirectory,
                new CompilerConfiguration(
                    JavaRelease.JAVA_25, List.of(secondDependency), List.of(), List.of())));

    // Assert
    var firstAst = firstRun.representation().ast().result().orElseThrow();
    var secondAst = secondRun.representation().ast().result().orElseThrow();
    assertEquals(SoftwareVersion.from(firstAst.snapshot(), firstAst.environment()),
        SoftwareVersion.from(secondAst.snapshot(), secondAst.environment()));
    assertNotEquals(firstAst.environment().compilerConfiguration(), secondAst.environment().compilerConfiguration());
    assertNotEquals(firstAst.identity(), secondAst.identity());
    assertNotEquals(firstRun.identity().key(), secondRun.identity().key());
  }
}
