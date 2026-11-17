package org.aquilagraph.library;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AquilaGraphTest {
  @TempDir Path directory;

  @Test
  void givenLibrarySession_whenProducingAndComputingWithoutPublication_thenNativeResultsAgreeWithoutStorageAcquisition() {
    // Arrange
    var request = request(directory);
    var expected = JavaFrontends.standard().produce(request).result().orElseThrow().identity();
    var computation = new AnalysisComputationRequest(request, Optional.empty());
    try (var aquila = AquilaGraph.open(ObservabilityConfiguration.disabled(),
        context -> { throw new AssertionError("AST storage must not be acquired"); },
        context -> { throw new AssertionError("Run storage must not be acquired"); })) {
      // Act
      var ast = aquila.produceAst(request);
      var analysis = aquila.analyze(computation);
      var execution = aquila.execute(new AnalysisRequest(computation, PublicationIntent.DO_NOT_SAVE));

      // Assert
      assertEquals(AstCompletion.COMPLETE, ast.completion());
      assertEquals(expected, ast.result().orElseThrow().identity());
      assertEquals(expected, analysis.representation().ast().result().orElseThrow().identity());
      assertTrue(analysis.representation().cfg().isEmpty());
      assertEquals(expected, execution.run().representation().ast().result().orElseThrow().identity());
      assertEquals(PublicationStatus.NOT_REQUESTED, execution.publication().status());
    }
  }

  @Test
  void givenClosedSession_whenCallingEveryEntryPoint_thenOperationsAreRejectedAndCloseIsIdempotent() {
    // Arrange
    var request = request(directory);
    var computation = new AnalysisComputationRequest(request, Optional.empty());
    var aquila = AquilaGraph.open(ObservabilityConfiguration.disabled());

    // Act
    aquila.close();
    aquila.close();

    // Assert
    assertThrows(IllegalStateException.class, () -> aquila.produceAst(request));
    assertThrows(IllegalStateException.class, () -> aquila.analyze(computation));
    assertThrows(IllegalStateException.class, () -> aquila.execute(new AnalysisRequest(computation, PublicationIntent.DO_NOT_SAVE)));
  }

  @Test
  void givenInvalidCallerInput_whenRequestFails_thenSessionRemainsUsable() {
    // Arrange
    var request = request(directory);
    try (var aquila = AquilaGraph.open(ObservabilityConfiguration.disabled())) {
      // Act
      assertThrows(NullPointerException.class, () -> aquila.analyze(null));
      var actual = aquila.produceAst(request);

      // Assert
      assertEquals(AstCompletion.COMPLETE, actual.completion());
    }
  }

  static AstProductionRequest request(Path root) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(root), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example { int increment(int value) { return value + 1; } }")));
    return new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }
}
