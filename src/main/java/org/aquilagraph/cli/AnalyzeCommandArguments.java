package org.aquilagraph.cli;

import java.nio.file.Path;
import java.util.Objects;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.RepositorySavePolicy;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;

/** Validated command-line values required by the analysis use case. */
record AnalyzeCommandArguments(Path sourceRoot, JavaRelease release, RepositorySavePolicy repositorySavePolicy) {
  AnalyzeCommandArguments {
    Objects.requireNonNull(sourceRoot, "sourceRoot");
    Objects.requireNonNull(release, "release");
    Objects.requireNonNull(repositorySavePolicy, "repositorySavePolicy");
  }

  AnalysisRequest toAnalysisRequest() {
    return new AnalysisRequest(sourceRoot, CompilerConfiguration.forRelease(release), repositorySavePolicy);
  }
}
