package org.aquilagraph.repository.internal.neo4j;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;

final class RepositoryTestRuns {
  private RepositoryTestRuns() {}

  static AnalysisRun analyze(Path root, String fileName, String source) {
    var snapshot = InputSnapshot.fromSources(root, source.isEmpty() ? List.of()
        : List.of(SourceFile.fromContent(Path.of(fileName), source)));
    return new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard()).analyze(
        new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
            AstProfile.JAVA_SOURCE_BOUNDED, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
  }

}
