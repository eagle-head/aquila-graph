package org.aquilagraph.application;

import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.model.CompilerConfiguration;

public record AnalysisRequest(
    Path sourceRoot,
    CompilerConfiguration compilerConfiguration,
    boolean persistenceRequested) {
  public AnalysisRequest {
    sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot").toAbsolutePath().normalize();
    Objects.requireNonNull(compilerConfiguration, "compilerConfiguration");
  }

  public static AnalysisRequest java25(Path sourceRoot, boolean persistenceRequested) {
    return new AnalysisRequest(
        sourceRoot, CompilerConfiguration.java25(), persistenceRequested);
  }
}
