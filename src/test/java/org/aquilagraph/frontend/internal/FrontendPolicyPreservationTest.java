package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AnalysisEnvironmentException;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrontendPolicyPreservationTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED,
      AstProfile.JAVA_SOURCE_INITIALIZATION, AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION,
      AstProfile.JAVA_SOURCE_DECLARATIONS, AstProfile.JAVA_SOURCE_UNITS);
  private static final String ORDINARY = "class Example { int subject() { return 1; } }";
  @TempDir Path directory;

  @Test
  void givenEveryExactProfile_whenAllProductionEntriesAlternateWithLegacy_thenCanonicalClosedResultsRemainDistinctAndStable()
      throws IOException {
    // Arrange
    var root = sourceRoot("ordinary");
    Files.createDirectories(root);
    Files.writeString(root.resolve("Example.java"), ORDINARY);
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var snapshot = new JavaSourceLoader().discover(root);

    for (var index = PROFILES.size() - 1; index >= 0; index--) {
      var profile = PROFILES.get(index);

      // Act
      var direct = frontend.produce(snapshot, CONFIGURATION, profile);
      var captured = frontend.produce(new AstProductionRequest(snapshot, CONFIGURATION, profile));
      var discovered = frontend.produce(new AstProductionRequest(root, CONFIGURATION, profile));
      var legacySnapshot = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CONFIGURATION, JavaTranslationPolicy.legacyAnalysis());
      var legacyPath = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(root), CONFIGURATION, JavaTranslationPolicy.legacyAnalysis());

      // Assert
      assertEquals(AstCompletion.COMPLETE, direct.completion(), direct.diagnostics().toString());
      var result = direct.result().orElseThrow();
      assertEquals(profile, result.profile());
      assertEquals("javac-public/14", result.identity().semanticProductionRevision());
      assertEquals(List.of("subject"), result.program().methods().stream().filter(method -> method.name().equals("subject"))
          .map(method -> method.name()).toList());
      var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
      assertTrue(result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
          .anyMatch(node -> node.syntaxKind() == SyntaxKind.RETURN));
      assertArrayEquals(AstCodec.encode(result), AstCodec.encode(captured.result().orElseThrow()));
      assertArrayEquals(AstCodec.encode(result), AstCodec.encode(discovered.result().orElseThrow()));
      assertEquals(legacySnapshot.program(), legacyPath.program());
      assertEquals(legacySnapshot.diagnostics(), legacyPath.diagnostics());
      assertArrayEquals(AstCodec.encode(result), AstCodec.encode(AstCodec.decode(AstCodec.encode(result), result.identity())));
      observeBaseline("ordinary", profile, direct);
    }
  }

  @Test
  void givenEachCapabilityBoundary_whenProfilesAlternate_thenExactQualificationAndCanonicalValuesArePreserved() throws IOException {
    // Arrange
    var sources = List.of(
        "class Example { Class<?> subject() { return String.class; } }",
        "class Example { int value = 1; { value++; } int subject() { return value; } }",
        "class Example { Object subject() { class Local {} return new Local(); } }",
        "enum Example { FIRST(1) { int subject() { return 2; } }; Example(int value) {} int subject() { return 1; } }",
        "class Example { ; int subject() { return 1; } }",
        "package sample;");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    for (var index = 0; index < sources.size(); index++) {
      var snapshot = InputSnapshot.fromSources(capturedAbsolute(sourceRoot("boundary-" + index)),
          List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), sources.get(index))));
      var earlier = PROFILES.get(index);
      var capable = PROFILES.get(index + 1);

      // Act
      var expanded = frontend.produce(snapshot, CONFIGURATION, capable);
      var bounded = frontend.produce(snapshot, CONFIGURATION, earlier);
      var repeated = frontend.produce(snapshot, CONFIGURATION, capable);

      // Assert
      assertEquals(AstCompletion.COMPLETE, expanded.completion(), expanded.diagnostics().toString());
      assertNotEquals(AstCompletion.COMPLETE, bounded.completion(), earlier.version());
      assertEquals(capable, expanded.profile());
      assertEquals(earlier, bounded.profile());
      assertArrayEquals(AstCodec.encode(expanded.result().orElseThrow()), AstCodec.encode(repeated.result().orElseThrow()));
      if (index == 0) {
        assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
        assertTrue(bounded.result().orElseThrow().aggregate().nodes().stream()
            .anyMatch(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL));
      }
      observeBaseline("boundary-" + index, capable, expanded);
      observeBaseline("boundary-" + index, earlier, bounded);
    }
  }

  @Test
  void givenUnknownProfileAndMissingRoot_whenRefusedThenValidWorkRuns_thenProfileRefusalPrecedesCaptureWithoutLeakingPolicy()
      throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var unknown = new AstProfile("java-source-expanded/unknown");
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(sourceRoot("valid")), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), ORDINARY)));

    // Act
    var refusal = assertThrows(IllegalArgumentException.class,
        () -> frontend.produce(new AstProductionRequest(directory.resolve("missing"), CONFIGURATION, unknown)));
    var outcome = frontend.produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED);

    // Assert
    assertEquals("Unsupported source profile: " + unknown.version(), refusal.getMessage());
    assertEquals(AstCompletion.COMPLETE, outcome.completion());
    assertEquals(AstProfile.JAVA_SOURCE_BOUNDED, outcome.profile());
  }

  private Path sourceRoot(String name) {
    var preservationDirectory = System.getProperty("frontend.preservation.directory");
    return (preservationDirectory == null ? directory : Path.of(preservationDirectory).resolve("inputs")).resolve(name);
  }

  @Test
  void givenInvalidDirectArguments_whenPublicEntriesAreCalled_thenExistingNullAndInputFailureFamiliesRemain() throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), ORDINARY)));
    var missing = directory.resolve("missing-root");

    // Act
    var inputFailure = assertThrows(AnalysisInputException.class,
        () -> frontend.produce(new AstProductionRequest(missing, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS)));

    // Assert
    assertTrue(inputFailure.getCause() instanceof IOException || inputFailure.getCause() instanceof IllegalArgumentException);
    assertThrows(NullPointerException.class, () -> frontend.produce(null));
    assertThrows(NullPointerException.class, () -> frontend.produce(snapshot, CONFIGURATION, null));
    assertThrows(NullPointerException.class, () -> frontend.produce(null, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS));
    assertThrows(NullPointerException.class, () -> frontend.produce(snapshot, null, AstProfile.JAVA_SOURCE_UNITS));
    assertThrows(NullPointerException.class, () -> new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover((Path) null), CONFIGURATION, JavaTranslationPolicy.legacyAnalysis()));
    assertThrows(NullPointerException.class, () -> new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze((InputSnapshot) null, CONFIGURATION, JavaTranslationPolicy.legacyAnalysis()));
    assertThrows(NullPointerException.class, () -> new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, null, JavaTranslationPolicy.legacyAnalysis()));
  }

  @Test
  void givenControlledDependencyReadFailure_whenProductionEntriesCaptureEnvironment_thenCheckedAndUniformFailureCausesRemainExact()
      throws IOException {
    for (var entry : List.of("direct", "snapshot-request", "root-request")) {
      // Arrange
      var root = Files.createDirectories(directory.resolve(entry));
      Files.writeString(root.resolve("Example.java"), ORDINARY);
      var dependency = Files.createDirectories(root.resolve("dependency"));
      Files.writeString(dependency.resolve("state.bin"), "dependency-state");
      var failure = new IOException("Supplied dependency read failure");
      var snapshot = new JavaSourceLoader().discover(root);
      try (var observation = new DependencyFileSystemObservation(dependency)) {
        observation.inputOpenFailureAttempt = 1;
        observation.inputOpenFailure = failure;
        var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(capturedAbsolute(dependency)), List.of(), List.of());
        var environmentCapture = new AnalysisEnvironmentCapture(path -> new CompilerDependencyCapture().capture(observation.observe(path)));
        var frontend = new JavaFrontend(new JavaSourceLoader(), new JavaAnalysisPipeline(environmentCapture));

        // Act
        var observed = entry.equals("direct")
            ? assertThrows(IOException.class, () -> frontend.produce(snapshot, configuration, AstProfile.JAVA_SOURCE_UNITS))
            : assertThrows(AnalysisEnvironmentException.class, () -> frontend.produce(entry.equals("snapshot-request")
                ? new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_UNITS)
                : new AstProductionRequest(root, configuration, AstProfile.JAVA_SOURCE_UNITS))).getCause();

        // Assert
        assertSame(failure, observed);
        assertEquals(1, observation.inputOpenAttempts);
        assertEquals(observation.directoryOpenAttempts, observation.directoryCloseAttempts);
      }
    }
  }

  private static void observeBaseline(String name, AstProfile profile, AstOutcome outcome) throws IOException {
    var preservationDirectory = System.getProperty("frontend.preservation.directory");
    if (preservationDirectory == null) {
      return;
    }
    var baseline = Path.of(preservationDirectory).resolve("baseline");
    var key = name + "-" + profile.version().replace('/', '-');
    var bytes = outcome.result().map(AstCodec::encode).orElseGet(() -> new byte[0]);
    var qualification = (outcome.profile() + "\n" + outcome.completion() + "\n" + outcome.sourceSubjectDigest() + "\n"
        + outcome.diagnostics()).getBytes(StandardCharsets.UTF_8);
    if (Boolean.getBoolean("frontend.preservation.capture")) {
      Files.createDirectories(baseline);
      Files.write(baseline.resolve(key + ".ast"), bytes);
      Files.write(baseline.resolve(key + ".qualification"), qualification);
    } else {
      assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".ast")), bytes, key);
      assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".qualification")), qualification, key);
    }
  }
}
