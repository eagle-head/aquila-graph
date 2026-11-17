package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
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

class JavaAnalysisObservationTest {
  @TempDir Path directory;

  @Test
  void givenEnabledStageRecording_whenProducingSourceAst_thenStagesCorrelateWithoutChangingTheAdmittedResult() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example { java.util.List<String> names() { return java.util.List.of(\"value\"); } }")));
    var request = new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var producer = JavaFrontends.standard();
    var expected = producer.produce(request);
    var destination = directory.resolve("analysis.jfr");

    // Act
    try (var recording = new Recording()) {
      recording.enable("org.aquilagraph.JavaAnalysisStage").withoutThreshold();
      recording.start();
      var observed = producer.produce(request);
      recording.stop();
      recording.dump(destination);

      // Assert
      assertEquals(AstCompletion.COMPLETE, observed.completion());
      assertEquals(expected.result().orElseThrow().identity(), observed.result().orElseThrow().identity());
    }
    var events = RecordingFile.readAllEvents(destination).stream()
        .filter(event -> event.getEventType().getName().equals("org.aquilagraph.JavaAnalysisStage")).toList();
    assertFalse(events.isEmpty());
    var completed = events.stream().filter(event -> event.getString("phase").equals("SUCCEEDED")).toList();
    assertEquals(Set.of("production", "environment-capture", "parse", "written-inventory", "compiler-attribution", "attributed-inventory",
        "source-translation", "coverage-reconciliation", "syntax-admission", "outcome-admission"),
        completed.stream().map(event -> event.getString("stage")).collect(Collectors.toSet()));
    assertEquals(completed.size() * 2, events.size());
    assertTrue(events.stream().allMatch(event -> event.getString("inputDigest").equals(snapshot.snapshotDigest())
        && event.getInt("sourceFiles") == 1 && event.getString("profile").equals("java-source-expanded/8")));
    var total = completed.stream().filter(event -> event.getString("stage").equals("production")).findFirst().orElseThrow();
    assertEquals(expected.result().orElseThrow().aggregate().nodes().size(), total.getInt("sourceNodes"));
    assertEquals("COMPLETE", total.getString("completion"));
  }
}
