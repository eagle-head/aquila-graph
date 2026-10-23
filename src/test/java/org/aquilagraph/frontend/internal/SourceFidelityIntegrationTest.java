package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.testsupport.SourceFidelityFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;

class SourceFidelityIntegrationTest {
  @TempDir Path directory;

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
          var sources = List.of(SourceFile.fromContent(Path.of("Hierarchy.java"), SourceFidelityFixture.SOURCE),
              SourceFile.fromContent(Path.of("Invalid.java"), mixture + " class Invalid { int value() { return -4; } }"));
          var invalid = new AstProductionRequest(new SourceInput.Snapshot(InputSnapshot.fromSources(directory, sources)),
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
    var coordinated = new SourceAnalysisRunner(frontend, CfgDerivers.standard()).deriveExisting(AstOutcome.available(decoded), cfgRequest);

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
