package org.aquilagraph.application;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeKind;
import org.aquilagraph.model.ProgramRepresentation;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.StableId;

final class TestAnalysisRuns {
  private TestAnalysisRuns() {
  }

  static AnalysisRun completed() {
    return completed(Path.of(".").toAbsolutePath().normalize(), CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
  }

  static AnalysisRun completed(Path sourceRoot, CompilerConfiguration configuration) {
    var sourceFile = new SourceFile(StableId.sha256("source", "Example.java", "class Example {}"), Path.of("Example.java"),
        StableId.sha256("source-content", "class Example {}"), "class Example {}");
    var inputSnapshot = new InputSnapshot(sourceRoot.toAbsolutePath().normalize(), "SHA-256",
        StableId.sha256("snapshot", sourceFile.sourceFileKey()), List.of(sourceFile));
    var environmentSnapshot = new AnalysisEnvironmentSnapshot(configuration, "test-compiler", "test-vendor", "25-test",
        List.of("--release", "25"), List.of(), List.of());
    var type = new JavaType(StableId.sha256("type", "Example"), "Example", JavaTypeKind.CLASS, Set.of(), Optional.empty(),
        Optional.empty(), List.of(), List.of(), List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of());
    var representation = new ProgramRepresentation(new JavaProgram(List.of(type)), List.of());
    return new AnalysisRun(StableId.sha256("analysis-run", "completed"), inputSnapshot, environmentSnapshot,
        Optional.of(representation), List.of(), AnalysisCompletion.COMPLETED);
  }

  static AnalysisRun rejected() {
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var inputSnapshot = new InputSnapshot(Path.of(".").toAbsolutePath().normalize(), "SHA-256",
        StableId.sha256("snapshot", "empty"), List.of());
    var environmentSnapshot = new AnalysisEnvironmentSnapshot(configuration, "test-compiler", "test-vendor", "25-test",
        List.of("--release", "25"), List.of(), List.of());
    return new AnalysisRun(StableId.sha256("analysis-run", "rejected"), inputSnapshot, environmentSnapshot, Optional.empty(), List.of(),
        AnalysisCompletion.REJECTED);
  }
}
