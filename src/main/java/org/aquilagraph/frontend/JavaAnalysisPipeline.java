package org.aquilagraph.frontend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.aquilagraph.application.port.AnalysisEnvironmentException;
import org.aquilagraph.application.port.AnalysisInputException;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.aquilagraph.syntax.JavaAstAggregate;

final class JavaAnalysisPipeline {
  private final AnalysisEnvironmentCapture environmentCapture;

  JavaAnalysisPipeline(AnalysisEnvironmentCapture environmentCapture) {
    this.environmentCapture = Objects.requireNonNull(environmentCapture, "environmentCapture");
  }

  SyntaxAnalysisInput analyze(InputSnapshot snapshot, CompilerConfiguration configuration) throws IOException {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(configuration, "configuration");
    var compiler = systemCompiler(configuration);
    var invocation = JavaCompilerInvocation.from(configuration);
    var environment = environmentCapture.capture(compiler, configuration, invocation.analysisOptions());
    var languageFeatureCoverage = JavaLanguageFeatureCoverage.fromConfiguration(configuration);
    if (snapshot.sourceFiles().isEmpty()) {
      var coverageResult = JavaLanguageFeatureCoverage.result(languageFeatureCoverage, List.of(), List.of(), List.of());
      return new SyntaxAnalysisInput(snapshot, environment, new JavaProgram(List.of()),
          Optional.of(JavaAstAggregate.builder(snapshot).seal()), List.of(), coverageResult);
    }

    var sources = new SnapshotSourceCatalog(snapshot);
    SyntaxAnalysisInput result;
    try (var session = openCompilerSession(compiler, invocation, sources)) {
      var parsedSource = session.parse();
      var kindEvidence = new JavaCompilerKindDiagnostics(sources, parsedSource.sourceEvidence(), languageFeatureCoverage)
          .observe(parsedSource.units());
      var compilation = session.analyze(parsedSource);
      var typeEvidence = new JavaTypeKindObserver(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), languageFeatureCoverage).observe(compilation.units());
      var translation = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), languageFeatureCoverage).translate(snapshot, compilation.units());
      var occurrences = new ArrayList<>(kindEvidence.occurrences());
      occurrences.addAll(typeEvidence.occurrences());
      occurrences.addAll(translation.featureOccurrences());
      var observations = new ArrayList<>(kindEvidence.observations());
      observations.addAll(typeEvidence.observations());
      observations.addAll(translation.featureObservations());
      var kindDiagnostics = kindEvidence.observations().stream().flatMap(observation -> observation.diagnostic().stream()).toList();
      var diagnostics = combineDiagnostics(new CompilerDiagnosticTranslator(sources).translate(compilation.diagnostics()), kindDiagnostics,
          typeEvidence.diagnostics(), translation.diagnostics());
      var coverageResult = JavaLanguageFeatureCoverage.result(languageFeatureCoverage, occurrences, observations, diagnostics);
      result = new SyntaxAnalysisInput(snapshot, environment, translation.value().program(), translation.value().aggregate(), diagnostics,
          coverageResult);
    }

    environmentCapture.verifyUnchanged(compiler, configuration, invocation.analysisOptions(), environment);
    return result;
  }

  private static CompilerSession openCompilerSession(JavaCompiler compiler, JavaCompilerInvocation invocation,
      SnapshotSourceCatalog sources) {
    try {
      return CompilerSession.open(compiler, invocation, sources);
    } catch (IllegalArgumentException exception) {
      throw new AnalysisInputException("The compiler rejected the analysis configuration", exception);
    }
  }

  private static JavaCompiler systemCompiler(CompilerConfiguration configuration) {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new AnalysisEnvironmentException("A JDK system compiler is required for Java release " + configuration.release().feature());
    }
    try {
      JavaCompilerReleaseSupport.requireSupported(compiler.getSourceVersions(), configuration.release());
    } catch (IllegalStateException exception) {
      throw new AnalysisEnvironmentException("The system compiler cannot analyze Java release " + configuration.release().feature(),
          exception);
    }
    return compiler;
  }

  @SafeVarargs
  private static List<AnalysisDiagnostic> combineDiagnostics(List<AnalysisDiagnostic>... diagnosticGroups) {
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    for (var diagnosticGroup : diagnosticGroups) {
      diagnostics.addAll(diagnosticGroup);
    }
    return List.copyOf(diagnostics);
  }
}
