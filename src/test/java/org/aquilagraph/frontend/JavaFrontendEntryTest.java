package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
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
    var missing = assertThrows(AnalysisInputException.class, () -> frontend.analyze(missingRoot, CONFIGURATION));
    var nullRoot = assertThrows(NullPointerException.class, () -> frontend.analyze((Path) null, CONFIGURATION));
    var nullConfiguration = assertThrows(NullPointerException.class, () -> frontend.analyze(temporaryDirectory, null));

    // Assert
    assertInstanceOf(IllegalArgumentException.class, missing.getCause());
    assertEquals("sourceRoot", nullRoot.getMessage());
    assertEquals("configuration", nullConfiguration.getMessage());
  }

  @Test
  void givenMalformedDiskSourceAndCallerSuppliedContent_whenBothEntriesAnalyze_thenPathFailureRetainsCauseAndSnapshotUsesCapturedContent()
      throws IOException {
    // Arrange
    Files.write(temporaryDirectory.resolve("Sample.java"), new byte[] {(byte) 0xc3, 0x28});
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var snapshot = InputSnapshot.fromSources(temporaryDirectory,
        List.of(SourceFile.fromContent(Path.of("Sample.java"), "class Sample {}")));

    // Act
    var failure = assertThrows(AnalysisInputException.class, () -> frontend.analyze(temporaryDirectory, CONFIGURATION));
    var input = frontend.analyze(snapshot, CONFIGURATION);

    // Assert
    var readFailure = assertInstanceOf(IOException.class, failure.getCause());
    assertInstanceOf(MalformedInputException.class, readFailure.getCause());
    assertEquals(snapshot, input.snapshot());
    assertEquals("Sample", input.program().types().getFirst().qualifiedName());
  }

  @Test
  void givenEmptySourcesAndMissingRuntimeVendor_whenEitherEntryAnalyzes_thenInitialEnvironmentFailurePrecedesEmptyInputReturn() {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var snapshot = InputSnapshot.fromSources(temporaryDirectory, List.of());
    var originalVendor = System.getProperty("java.vendor");
    System.clearProperty("java.vendor");
    try {
      // Act
      var pathFailure = assertThrows(AnalysisEnvironmentException.class, () -> frontend.analyze(temporaryDirectory, CONFIGURATION));
      var snapshotFailure = assertThrows(AnalysisEnvironmentException.class, () -> frontend.analyze(snapshot, CONFIGURATION));

      // Assert
      assertEquals("Required system property is unavailable: java.vendor", pathFailure.getMessage());
      assertEquals(pathFailure.getMessage(), snapshotFailure.getMessage());
    } finally {
      if (originalVendor == null) {
        System.clearProperty("java.vendor");
      } else {
        System.setProperty("java.vendor", originalVendor);
      }
    }
  }
}
