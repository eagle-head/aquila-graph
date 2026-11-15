package org.aquilagraph.repository.internal.neo4j;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;

final class RepositoryTestRuns {
  private RepositoryTestRuns() {}

  static AnalysisRun analyze(Path root, String fileName, String source) {
    return analyze(capturedAbsolute(root), capturedPath(Path.of(fileName)), source);
  }

  static AnalysisRun analyze(CapturedLocation root, CapturedLocation file, String source) {
    var snapshot = InputSnapshot.fromSources(root, source.isEmpty() ? List.of() : List.of(SourceFile.fromContent(file, source)));
    return AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard()).analyze(
        new AnalysisComputationRequest(
            new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))));
  }

}
