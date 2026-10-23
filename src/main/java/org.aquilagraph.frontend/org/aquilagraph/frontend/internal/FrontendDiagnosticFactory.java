package org.aquilagraph.frontend.internal;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SyntaxStructuralPath;

/** Creates source-located frontend diagnostics with occurrence-stable identities. */
final class FrontendDiagnosticFactory {
  private FrontendDiagnosticFactory() {
  }

  static AnalysisDiagnostic error(String context, String code, String message, Optional<SourceSpan> origin,
      AnalysisOutcomeKind outcomeKind) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(outcomeKind, "outcomeKind");
    var occurrence = origin.map(value -> value.sourceFileKey() + ':' + value.startOffset() + ':' + value.endOffsetExclusive())
        .orElse("no-direct-source");
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", context, code, occurrence), code, DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, message, origin.stream().toList(), outcomeKind);
  }

  static AnalysisDiagnostic capabilityError(String context, String code, String message, Optional<SourceSpan> origin) {
    return error(context, code, message, origin, AnalysisOutcomeKind.UNSUPPORTED_LANGUAGE_FEATURE);
  }

  static AnalysisDiagnostic erroneousCompilerState(String context, String code, String message, Optional<SourceSpan> origin) {
    return error(context, code, message, origin, AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE);
  }

  static AnalysisDiagnostic otherError(String context, String code, String message, Optional<SourceSpan> origin) {
    return error(context, code, message, origin, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }

  static AnalysisDiagnostic sourcePositionError(SourceFile sourceFile, SyntaxStructuralPath path, String code, String message) {
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(path, "path");
    var occurrence = String.join(":", path.identityComponents());
    var context = sourceFile.sourceFileKey() + ':' + occurrence;
    var locatedMessage = message + " in " + sourceFile.relativePath() + " at " + occurrence;
    return otherError(context, code, locatedMessage, Optional.empty());
  }
}
