package org.aquilagraph.application;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.aquilagraph.application.RequestQualificationReason.ApplicationDiagnostic;
import org.aquilagraph.application.RequestQualificationReason.Method;
import org.aquilagraph.application.RequestQualificationReason.MethodDiagnostic;
import org.aquilagraph.application.RequestQualificationReason.Selection;
import org.aquilagraph.application.RequestQualificationReason.Source;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstCompletion;

/** Application-owned combination of independently established context outcomes. */
final class AnalysisQualification {
  private AnalysisQualification() {
  }

  static RequestQualification assess(ProgramRepresentation representation, List<AnalysisDiagnostic> diagnostics) {
    var assessment = new Accumulator();
    var ast = representation.ast();
    var sourceCompletion = ast.completion();
    if (sourceCompletion != AstCompletion.COMPLETE) {
      assessment.add(new Source(ast),
          sourceCompletion == AstCompletion.UNAVAILABLE ? AnalysisCompletion.REJECTED : AnalysisCompletion.PARTIAL);
    }
    representation.cfg().ifPresent(outcome -> {
      if (outcome.state() == CfgState.REJECTED) {
        assessment.add(new Selection(outcome), AnalysisCompletion.REJECTED);
      }
      outcome.methods().forEach(method -> assessMethod(assessment, method));
    });
    errorPositions(diagnostics)
        .forEachOrdered(position -> assessment.add(new ApplicationDiagnostic(diagnostics, position), AnalysisCompletion.FAILED));
    return new RequestQualification(representation, diagnostics, assessment.completion, assessment.reasons);
  }

  private static void assessMethod(Accumulator assessment, MethodCfgOutcome method) {
    var state = method.state();
    switch (state) {
      case SOURCE_UNAVAILABLE, UNSUPPORTED, ERRONEOUS_EVIDENCE, INSUFFICIENT_EVIDENCE ->
        assessment.add(new Method(method), AnalysisCompletion.PARTIAL);
      case FAILED -> assessment.add(new Method(method), AnalysisCompletion.FAILED);
      case AVAILABLE, NOT_APPLICABLE -> {
        var contribution = state == MethodCfgState.AVAILABLE ? AnalysisCompletion.FAILED : AnalysisCompletion.PARTIAL;
        errorPositions(method.diagnostics())
            .forEachOrdered(position -> assessment.add(new MethodDiagnostic(method, position), contribution));
      }
    }
  }

  private static IntStream errorPositions(List<AnalysisDiagnostic> diagnostics) {
    return IntStream.range(0, diagnostics.size()).filter(position -> diagnostics.get(position).isError());
  }

  private static final class Accumulator {
    private final List<RequestQualificationReason> reasons = new ArrayList<>();
    private AnalysisCompletion completion = AnalysisCompletion.COMPLETE;

    private void add(RequestQualificationReason reason, AnalysisCompletion contribution) {
      reasons.add(reason);
      if (precedence(contribution) > precedence(completion)) {
        completion = contribution;
      }
    }

    private static int precedence(AnalysisCompletion value) {
      return switch (value) {
        case COMPLETE -> 0;
        case PARTIAL -> 1;
        case FAILED -> 2;
        case REJECTED -> 3;
      };
    }
  }
}
