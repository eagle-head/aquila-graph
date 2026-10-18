package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyDiagnosticTest {
  @TempDir Path directory;

  @Test
  void givenErrorsInCapturedAndArchivedSources_whenPublicProductionRuns_thenBothErrorsRetainOnlyTheirActualCapturedOrigins()
      throws IOException {
    // Arrange
    var dependency = dependency("missing");
    var content = "import dependency.Library; class Example { int read(Library value) { return value.value() + absent; } }";
    // Spaces and a non-ASCII filename exercise captured source URI encoding.
    var source = SourceFile.fromContent(Path.of("source files", "Example \u00dc.java"), content);
    var snapshot = InputSnapshot.fromSources(directory, List.of(source));
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(dependency), List.of(), List.of());
    var request = new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_UNITS);

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(request);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    var errors = outcome.diagnostics().stream().filter(value -> value.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR).toList();
    assertEquals(List.of("compiler.err.cant.resolve.location", "compiler.err.cant.resolve.location"),
        errors.stream().map(AnalysisDiagnostic::code).toList());
    assertTrue(errors.stream().allMatch(value -> value.severity() == DiagnosticSeverity.ERROR && value.stage() == DiagnosticStage.FRONTEND));
    var start = content.indexOf("absent");
    assertEquals(List.of(new SourceSpan(source.sourceFileKey(), start, start + "absent".length())), errors.getFirst().origins());
    assertTrue(errors.getFirst().message().contains("absent"));
    assertTrue(errors.getLast().origins().isEmpty());
    assertTrue(errors.getLast().message().contains("missing"));
    assertEquals(List.of("Example"), outcome.result().orElseThrow().program().types().stream().map(type -> type.qualifiedName()).toList());
    assertEquals(snapshot, outcome.result().orElseThrow().snapshot());
    Files.delete(dependency);
  }

  @Test
  void givenValidArchivedSourceDependency_whenPublicProductionRuns_thenCapturedSourceCompletesWithoutImportingDependencyOwnership()
      throws IOException {
    // Arrange
    var dependency = dependency("7");
    var source = SourceFile.fromContent(Path.of("Example.java"),
        "import dependency.Library; class Example { int read(Library value) { return value.value(); } }");
    var snapshot = InputSnapshot.fromSources(directory, List.of(source));
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(dependency), List.of(), List.of());
    var request = new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_UNITS);

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(request);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertTrue(outcome.diagnostics().isEmpty());
    assertEquals(List.of("Example"), outcome.result().orElseThrow().program().types().stream().map(type -> type.qualifiedName()).toList());
    assertEquals(snapshot, outcome.result().orElseThrow().snapshot());
    Files.delete(dependency);
  }

  private Path dependency(String returnedValue) throws IOException {
    var archive = directory.resolve("dependency.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(archive))) {
      jar.putNextEntry(new JarEntry("dependency/Library.java"));
      jar.write(("package dependency; public class Library { public int value() { return " + returnedValue + "; } }")
          .getBytes(StandardCharsets.UTF_8));
      jar.closeEntry();
    }
    return archive;
  }
}
