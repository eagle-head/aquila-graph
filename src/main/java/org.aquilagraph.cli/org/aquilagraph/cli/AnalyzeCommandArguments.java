package org.aquilagraph.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;

/** Validated command-line values required by the analysis use case. */
record AnalyzeCommandArguments(Path sourceRoot, JavaRelease release, PublicationIntent publicationIntent) {
  AnalyzeCommandArguments {
    Objects.requireNonNull(sourceRoot, "sourceRoot");
    Objects.requireNonNull(release, "release");
    Objects.requireNonNull(publicationIntent, "publicationIntent");
    if (publicationIntent == PublicationIntent.SAVE_COMPLETE_AST) {
      throw new IllegalArgumentException("This command publishes completed source and CFG runs");
    }
  }

  AnalysisRequest toAnalysisRequest() {
    var source = new AstProductionRequest(sourceRoot, CompilerConfiguration.forRelease(release), AstProfile.JAVA_SOURCE_BOUNDED);
    var computation = new AnalysisComputationRequest(source, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)));
    return new AnalysisRequest(computation, publicationIntent);
  }
}
