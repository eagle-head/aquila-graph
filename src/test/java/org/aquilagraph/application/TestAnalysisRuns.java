package org.aquilagraph.application;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;

final class TestAnalysisRuns {
  private TestAnalysisRuns() {}

  static AnalysisRun completed() {
    return completed(Path.of("."), CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
  }

  static AnalysisRun completed(Path sourceRoot, CompilerConfiguration configuration) {
    return analyze(sourceRoot, configuration, List.of(SourceFile.fromContent(Path.of("Example.java"), "interface Example {}")));
  }

  static AnalysisRun fromSource(String source) {
    return analyze(Path.of("."), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
  }

  static AnalysisRun rejected() {
    return analyze(Path.of("."), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), List.of());
  }

  private static AnalysisRun analyze(Path root, CompilerConfiguration configuration, List<SourceFile> sources) {
    var request = new AnalysisRequest(new SourceInput.Snapshot(InputSnapshot.fromSources(root, sources)), configuration,
        AstProfile.JAVA_SOURCE_BOUNDED, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE);
    return new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard()).analyze(request);
  }
}
