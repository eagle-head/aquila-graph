package org.aquilagraph.frontend;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.StableId;

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
}
