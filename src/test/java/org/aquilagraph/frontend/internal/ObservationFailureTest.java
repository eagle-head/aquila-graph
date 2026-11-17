package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.aquilagraph.frontend.AnalysisObserver;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.frontend.JavaObservations;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObservationFailureTest {
  @TempDir Path directory;

  @Test
  void givenFailingObserver_whenProducingAst_thenOriginalAdmittedIdentityIsRetained() {
    // Arrange
    var snapshot = snapshot();
    var request = new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var expected = JavaFrontends.standard().produce(request);
    AnalysisObserver observer = (_, _) -> { throw new IllegalStateException("Unavailable observation backend"); };

    // Act
    var actual = JavaFrontends.observed(observer).produce(request);

    // Assert
    assertEquals(expected.result().orElseThrow().identity(), actual.result().orElseThrow().identity());
  }

  @Test
  void givenOperationFailure_whenObserverAlsoFails_thenOriginalFailureIsPropagated() {
    // Arrange
    var failure = new IOException("Source cannot be read");
    AnalysisObserver observer = (_, _) -> { throw new IllegalStateException("Unavailable observation backend"); };

    // Act
    var actual = assertThrows(IOException.class, () -> JavaAnalysisStage.measure(observer, "parse", snapshot(),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES, () -> { throw failure; }));

    // Assert
    assertSame(failure, actual);
  }

  @Test
  void givenDisabledTelemetry_whenOpening_thenNoProviderIsRequired() {
    // Arrange
    var settings = Map.<String, String>of();

    // Act
    var observer = JavaObservations.open(settings);

    // Assert
    assertSame(AnalysisObserver.none(), observer);
  }

  private InputSnapshot snapshot() {
    return InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example {}")));
  }
}
