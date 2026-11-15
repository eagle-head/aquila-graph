package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.testsupport.SourceFidelityFixture;
import org.aquilagraph.testsupport.SourceCompositionFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;

class SourceFidelityIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenLexicalCorrectionsInsideNestedBranches_whenProducedAndConsumed_thenExactSourceAndSeparateCfgOutcomesCompose() {
    // Arrange
    var spellings = List.of(false, true);
    // Act
    var observations = spellings.stream().map(escaped -> DynamicTest.dynamicTest(
        "given" + (escaped ? "Escaped" : "Ordinary") + "NestedBranches_whenProducedAndDecoded_thenStructureAndCfgOutcomesCompose", () -> {
          // Arrange
          var original = SourceCompositionFixture.produce(directory, escaped);
          var bytes = AstCodec.encode(original);
          var methods = List.of("supported", "refused").stream().map(name -> original.program().methods().stream()
              .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
          // Act
          var decoded = AstCodec.decode(bytes, original.identity());
          var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(AstOutcome.available(decoded),
              new CfgRequest(decoded.identity(), methods, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS));
          // Assert
          SourceCompositionFixture.assertComposition(original, escaped);
          SourceCompositionFixture.assertComposition(decoded, escaped);
          SourceCompositionFixture.assertRoundTrip(original, decoded);
          assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED),
              run.representation().cfg().orElseThrow().methods().stream().map(method -> method.state()).toList());
          assertArrayEquals(bytes, AstCodec.encode(run.representation().ast().result().orElseThrow()));
        }));
    // Assert
    return observations;
  }

  @Test
  void givenBlockDeclarationsAndNestedBranches_whenProducedAndConsumedAfterClosure_thenSourceStructureAndCfgIndependenceSurvive() {
    // Arrange
    var text = """
        @interface Top {}
        class Branches {
          @interface Member {}
          void run(boolean flag) {
            if (flag) {
              int value = 1;
              class Local {}
              interface Contract {}
              enum Choice { ONLY }
              record Data(int number) {}
              while (flag) { if (flag) break; else ; }
            } else { if (flag) ; else for (; flag;) break; }
          }
          int refused(int number) { return switch (number) { case 0 -> 1; default -> 2; }; }
        }
        """;
    var source = SourceFile.fromContent(capturedPath(Path.of("Branches.java")), text);
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(source));
    var frontend = JavaFrontends.standard();
    var request = new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    // Act
    var outcome = frontend.produce(request);
    var original = outcome.result().orElseThrow();
    var bytes = AstCodec.encode(original);
    var decoded = AstCodec.decode(bytes, original.identity());
    var conditional = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.IF)
        .min(Comparator.comparingLong(node -> node.origin().orElseThrow().startOffset())).orElseThrow();
    var statements = conditional.requiredChild(SyntaxRole.THEN_BRANCH).children();
    var refused = decoded.program().methods().stream().filter(method -> method.name().equals("refused")).findFirst().orElseThrow();
    var selected = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(AstOutcome.available(decoded),
        new CfgRequest(decoded.identity(), List.of(refused.methodKey()), CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS));
    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.coverage(), decoded.coverage());
    assertEquals(original.identity(), decoded.identity());
    assertEquals(text, decoded.snapshot().sourceFiles().getFirst().content());
    assertEquals(List.of(JavaSourceKind.VARIABLE, JavaSourceKind.CLASS, JavaSourceKind.INTERFACE, JavaSourceKind.ENUM,
        JavaSourceKind.RECORD, JavaSourceKind.WHILE_LOOP), statements.stream().map(child -> child.node().sourceKind()).toList());
    assertEquals(List.of("int value = 1;", "class Local {}", "interface Contract {}", "enum Choice { ONLY }", "record Data(int number) {}",
        "while (flag) { if (flag) break; else ; }"), statements.stream().map(child -> {
          var span = child.node().origin().orElseThrow();
          return text.substring((int) span.startOffset(), (int) span.endOffsetExclusive());
        }).toList());
    assertEquals(List.of(0, 1, 2, 3, 4, 5), statements.stream().map(SyntaxChild::position).toList());
    for (var child : statements) {
      assertEquals(conditional.requiredChild(SyntaxRole.THEN_BRANCH).structuralPath().child(SyntaxRole.STATEMENT, child.position()),
          child.node().structuralPath());
      assertEquals(source.sourceFileKey(), child.node().sourceFileKey());
    }
    assertEquals(2, decoded.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.ANNOTATION_TYPE).count());
    assertEquals(MethodCfgState.UNSUPPORTED, selected.representation().cfg().orElseThrow().methods().getFirst().state());
    assertArrayEquals(bytes, AstCodec.encode(selected.representation().ast().result().orElseThrow()));
  }

  @Test
  void givenInvalidDirectJavaDeclaration_whenPublicProducerRuns_thenRealCompilerDiagnosticsPreventCompleteQualification() {
    // Arrange
    var source = SourceFile.fromContent(capturedPath(Path.of("Invalid.java")), "class Invalid { void run(boolean flag) { if (flag) int value; } }");
    var request = new AstProductionRequest(new SourceInput.Snapshot(InputSnapshot.fromSources(capturedAbsolute(directory), List.of(source))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    // Act
    var outcome = JavaFrontends.standard().produce(request);
    // Assert
    assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")), outcome.diagnostics().toString());
    outcome.result().ifPresent(result -> {
      var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
      assertEquals(result.completion(), decoded.completion());
      assertEquals(result.diagnostics(), decoded.diagnostics());
    });
  }

  @TestFactory
  Stream<DynamicTest> givenLocalAnnotationInterfaces_whenPublicProducerRuns_thenCompilerAndProjectionDiagnosticsPreventPublication() {
    return Stream.of("void run() { @interface Invalid {} }", "static { @interface Invalid {} }", "{ @interface Invalid {} }",
        "Runnable action = () -> { @interface Invalid {} };",
        "void run(int value) { switch (value) { default: @interface Invalid {} } }")
        .map(member -> DynamicTest.dynamicTest("givenLocalAnnotationIn" + member
            + "_whenPublicProducerRuns_thenCompilerAndProjectionDiagnosticsSurvive", () -> {
          // Arrange
          var source = SourceFile.fromContent(capturedPath(Path.of("Invalid.java")), "class Invalid { " + member + " }");
          var request = new AstProductionRequest(new SourceInput.Snapshot(InputSnapshot.fromSources(capturedAbsolute(directory), List.of(source))),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
          // Act
          var outcome = JavaFrontends.standard().produce(request);
          // Assert
          assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
          assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")), outcome.diagnostics().toString());
          assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.invalid-local-type-declaration")),
              outcome.diagnostics().toString());
          outcome.result().ifPresent(result -> {
            var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
            assertEquals(result.completion(), decoded.completion());
            assertEquals(result.diagnostics(), decoded.diagnostics());
            assertTrue(decoded.aggregate().nodes().stream().noneMatch(node -> node.sourceKind() == JavaSourceKind.ANNOTATION_TYPE));
          });
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidMixturesBesideCombinedCorrections_whenFrontendIsReused_thenRealErrorsAndIndependentValidFactsSurvive() {
    // Arrange
    var mixtures = List.of("@Tag(\"bad\") @Tag(\"other\") @Tags({@Tag(\"container\")})",
        "@Tags({@Tag(\"bad\")}) @Tags({@Tag(\"other\")}) @Groups({@Tags({@Tag(\"container\")})})");

    // Act
    var observations = mixtures.stream().map(mixture -> DynamicTest.dynamicTest("given"
        + (mixture.startsWith("@Tag(") ? "RepeatedBaseAndContainer" : "RepeatedContainersAndGroup")
        + "BesideCombinedCorrections_whenFrontendIsReused_thenRealErrorsAndValidFactsSurvive", () -> {
          // Arrange
          var frontend = new JavaFrontend(new JavaSourceLoader());
          var sources = List.of(SourceFile.fromContent(capturedPath(Path.of("Hierarchy.java")), SourceFidelityFixture.SOURCE),
              SourceFile.fromContent(capturedPath(Path.of("Invalid.java")), mixture + " class Invalid { int value() { return -4; } }"));
          var invalid = new AstProductionRequest(new SourceInput.Snapshot(InputSnapshot.fromSources(capturedAbsolute(directory), sources)),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_FIDELITY);
          var valid = new AstProductionRequest(new SourceInput.Snapshot(SourceFidelityFixture.snapshot(directory)),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_FIDELITY);

          // Act
          var diagnosed = frontend.produce(invalid);
          var following = frontend.produce(valid).result().orElseThrow();
          var partial = diagnosed.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(partial), partial.identity());

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, diagnosed.completion());
          assertEquals(List.of("compiler.err.invalid.repeatable.annotation.repeated.and.container.present"),
              diagnosed.diagnostics().stream().filter(value -> value.code().startsWith("compiler.err."))
                  .map(value -> value.code()).toList(), diagnosed.diagnostics().toString());
          assertEquals(partial.diagnostics(), decoded.diagnostics());
          SourceFidelityFixture.assertSourceFacts(decoded);
          SourceFidelityFixture.assertFaithful(following);
        }));

    // Assert
    return observations;
  }

  @Test
  void givenCombinedSourceCorrections_whenDetachedValuesReachCodecAndCfg_thenExactFactsAndIndependentOutcomesSurvive() {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var request = new AstProductionRequest(new SourceInput.Snapshot(SourceFidelityFixture.snapshot(directory)),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_FIDELITY);

    // Act
    var ast = frontend.produce(request);
    var original = ast.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var methods = List.of("subject", "refused").stream().map(name -> original.program().methods().stream()
        .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
    var cfgRequest = new CfgRequest(original.identity(), methods, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
    var direct = CfgDerivers.standard().derive(decoded, cfgRequest);
    var coordinated = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(AstOutcome.available(decoded), cfgRequest);

    // Assert
    SourceFidelityFixture.assertFaithful(original);
    SourceFidelityFixture.assertFaithful(decoded);
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.coverage(), decoded.coverage());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED), direct.methods().stream().map(value -> value.state()).toList());
    assertEquals(direct, coordinated.representation().cfg().orElseThrow());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(coordinated.representation().ast().result().orElseThrow()));
    assertTrue(coordinated.representation().ast().diagnostics().isEmpty());
  }
}
