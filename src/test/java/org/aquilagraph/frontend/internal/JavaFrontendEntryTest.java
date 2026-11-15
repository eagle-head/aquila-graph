package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaFrontendEntryTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @TempDir Path temporaryDirectory;

  @Test
  void givenMissingRootAndNullEntryArguments_whenPathAnalysisIsRequested_thenDiscoveryIsTranslatedAndNullChecksKeepTheirFamily() {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var missingRoot = temporaryDirectory.resolve("missing");

    // Act
    var missing = assertThrows(AnalysisInputException.class, () -> frontend.produce(new AstProductionRequest(missingRoot, CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED)));
    var nullRoot = assertThrows(NullPointerException.class, () -> frontend.produce(new AstProductionRequest((Path) null, CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED)));
    var nullConfiguration = assertThrows(NullPointerException.class, () -> frontend.produce(new AstProductionRequest(temporaryDirectory, null, AstProfile.JAVA_SOURCE_BOUNDED)));

    // Assert
    assertInstanceOf(IllegalArgumentException.class, missing.getCause());
    assertEquals("path", nullRoot.getMessage());
    assertEquals("configuration", nullConfiguration.getMessage());
  }

  @Test
  void givenMalformedDiskSourceAndCallerSuppliedContent_whenBothEntriesAnalyze_thenPathFailureRetainsCauseAndSnapshotUsesCapturedContent()
      throws IOException {
    // Arrange
    Files.write(temporaryDirectory.resolve("Sample.java"), new byte[] {(byte) 0xc3, 0x28});
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(temporaryDirectory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Sample.java")), "class Sample {}")));

    // Act
    var failure = assertThrows(AnalysisInputException.class, () -> frontend.produce(new AstProductionRequest(temporaryDirectory, CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED)));
    var input = frontend.produce(new AstProductionRequest(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();

    // Assert
    var readFailure = assertInstanceOf(IOException.class, failure.getCause());
    assertInstanceOf(MalformedInputException.class, readFailure.getCause());
    assertEquals(snapshot, input.snapshot());
    assertEquals("Sample", input.program().types().getFirst().qualifiedName());
  }

  @Test
  void givenEmptySourcesAndMissingRuntimeVendor_whenProductionAndCopyingRun_thenOnlyCopyingRequiresAnEnvironment() {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(temporaryDirectory), List.of());
    var originalVendor = System.getProperty("java.vendor");
    System.clearProperty("java.vendor");
    try {
      // Act
      var outcome = frontend.produce(new AstProductionRequest(temporaryDirectory, CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED));
      var snapshotFailure = assertThrows(AnalysisEnvironmentException.class, () -> new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CONFIGURATION, JavaTranslationPolicy.legacyAnalysis()));

      // Assert
      assertEquals(AstCompletion.UNAVAILABLE, outcome.completion());
      assertEquals(snapshot, outcome.capturedInput().orElseThrow());
      assertEquals(false, outcome.capturedEnvironment().isPresent());
      assertEquals("Required system property is unavailable: java.vendor", snapshotFailure.getMessage());
    } finally {
      if (originalVendor == null) {
        System.clearProperty("java.vendor");
      } else {
        System.setProperty("java.vendor", originalVendor);
      }
    }
  }
}
