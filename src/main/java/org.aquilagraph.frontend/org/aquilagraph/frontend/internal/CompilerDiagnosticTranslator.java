package org.aquilagraph.frontend.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;

final class CompilerDiagnosticTranslator {
  private final SnapshotSourceCatalog sources;

  CompilerDiagnosticTranslator(SnapshotSourceCatalog sources) {
    this.sources = Objects.requireNonNull(sources, "sources");
  }

  DiagnosticEmissions translate(List<Diagnostic<? extends JavaFileObject>> compilerDiagnostics) {
    Objects.requireNonNull(compilerDiagnostics, "compilerDiagnostics");
    var diagnostics = new DiagnosticEmissionCollector("CompilerDiagnosticTranslator", "input-compiler-diagnostics");
    compilerDiagnostics.forEach(diagnostic -> diagnostics.emit("CompilerDiagnosticTranslator", translate(diagnostic)));
    return diagnostics.snapshot();
  }

  private AnalysisDiagnostic translate(Diagnostic<? extends JavaFileObject> diagnostic) {
    var origins = origins(diagnostic);
    var severity = switch (diagnostic.getKind()) {
      case ERROR -> DiagnosticSeverity.ERROR;
      case WARNING, MANDATORY_WARNING -> DiagnosticSeverity.WARNING;
      case NOTE, OTHER -> DiagnosticSeverity.INFO;
    };
    var message = diagnostic.getMessage(Locale.ROOT);
    var code = diagnostic.getCode() == null ? "javac.unknown" : diagnostic.getCode();
    var key = StableId.sha256("diagnostic", code, severity.name(), message,
        origins.stream().map(Object::toString).collect(Collectors.joining("|")));
    var outcomeKind = diagnostic.getKind() == Diagnostic.Kind.ERROR
        ? AnalysisOutcomeKind.COMPILER_ERROR
        : AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR;
    return new AnalysisDiagnostic(key, code, DiagnosticStage.FRONTEND, severity, message, origins, outcomeKind);
  }

  private List<SourceSpan> origins(Diagnostic<? extends JavaFileObject> diagnostic) {
    var origins = new ArrayList<SourceSpan>();
    sources.find(diagnostic.getSource()).filter(ignored -> hasSourceRange(diagnostic)).ifPresent(
        sourceFile -> origins.add(new SourceSpan(sourceFile.sourceFileKey(), diagnostic.getStartPosition(), diagnostic.getEndPosition())));
    return List.copyOf(origins);
  }

  private static boolean hasSourceRange(Diagnostic<? extends JavaFileObject> diagnostic) {
    return diagnostic.getStartPosition() != Diagnostic.NOPOS && diagnostic.getEndPosition() != Diagnostic.NOPOS
        && diagnostic.getEndPosition() >= diagnostic.getStartPosition();
  }
}
