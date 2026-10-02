package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticEmission;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.StableId;

final class CompilerDiagnosticTranslator {
  private final SnapshotSourceCatalog sources;

  CompilerDiagnosticTranslator(SnapshotSourceCatalog sources) {
    this.sources = Objects.requireNonNull(sources, "sources");
  }

  List<AnalysisDiagnostic> translate(List<Diagnostic<? extends JavaFileObject>> compilerDiagnostics) {
    Objects.requireNonNull(compilerDiagnostics, "compilerDiagnostics");
    var diagnostics = new TranslationDiagnosticEvidence("CompilerDiagnosticTranslator", "input-compiler-diagnostics");
    compilerDiagnostics
        .forEach(diagnostic -> TranslationDiagnosticEvidence.emit(diagnostics, "CompilerDiagnosticTranslator", translate(diagnostic)));
    return TranslationDiagnosticEvidence.copyOf(diagnostics);
  }

  List<DiagnosticEmission> translateWithEvents(List<Diagnostic<? extends JavaFileObject>> compilerDiagnostics) {
    return TranslationDiagnosticEvidence.events(translate(compilerDiagnostics));
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
