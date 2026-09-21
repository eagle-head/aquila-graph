package org.aquilagraph.frontend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.aquilagraph.application.port.AnalysisEnvironmentException;
import org.aquilagraph.application.port.AnalysisInputException;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.syntax.SyntaxAnalysisInput;

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
    if (snapshot.sourceFiles().isEmpty()) {
      return new SyntaxAnalysisInput(snapshot, environment, new JavaProgram(List.of()), List.of(), List.of());
    }

    var sources = new SnapshotSourceCatalog(snapshot);
    SyntaxAnalysisInput result;
    try (var session = openCompilerSession(compiler, invocation, sources)) {
      var compilation = session.analyze();
      var translation = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence())
          .translate(compilation.units());
      var diagnostics = combineDiagnostics(new CompilerDiagnosticTranslator(sources).translate(compilation.diagnostics()),
          translation.diagnostics());
      result = new SyntaxAnalysisInput(snapshot, environment, translation.value().program(), translation.value().methodSyntax(),
          diagnostics);
    }

    environmentCapture.verifyUnchanged(compiler, configuration, invocation.analysisOptions(), environment);
    return result;
  }

  private static JavaCompilerSession openCompilerSession(JavaCompiler compiler, JavaCompilerInvocation invocation,
      SnapshotSourceCatalog sources) {
    try {
      return JavaCompilerSession.open(compiler, invocation, sources);
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

  private static List<AnalysisDiagnostic> combineDiagnostics(List<AnalysisDiagnostic> compilerDiagnostics,
      List<AnalysisDiagnostic> translationDiagnostics) {
    var diagnostics = new ArrayList<AnalysisDiagnostic>(compilerDiagnostics.size() + translationDiagnostics.size());
    diagnostics.addAll(compilerDiagnostics);
    diagnostics.addAll(translationDiagnostics);
    return List.copyOf(diagnostics);
  }
}
