package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstOutcome;

/** Typed references to the independently established causes of a request decision. */
public sealed interface RequestQualificationReason {
  /**
   * Noncomplete source, retaining its exact source-owned assessment.
   *
   * @param outcome
   *          responsible source outcome
   */
  record Source(AstOutcome outcome) implements RequestQualificationReason {
    /** Retains the established context owner. */
    public Source {
      Objects.requireNonNull(outcome);
    }
  }

  /**
   * Rejected CFG request, retaining native request diagnostics.
   *
   * @param outcome
   *          responsible request outcome
   */
  record Selection(CfgOutcome outcome) implements RequestQualificationReason {
    /** Retains the established context owner. */
    public Selection {
      Objects.requireNonNull(outcome);
    }
  }

  /**
   * Refused or normally failed method, retaining blockers or original failure.
   *
   * @param outcome
   *          responsible method outcome
   */
  record Method(MethodCfgOutcome outcome) implements RequestQualificationReason {
    /** Retains the established method owner. */
    public Method {
      Objects.requireNonNull(outcome);
    }
  }

  /**
   * Native error in an available or bodyless method's diagnostic list.
   *
   * @param outcome
   *          responsible method outcome
   * @param position
   *          zero-based method diagnostic position
   */
  record MethodDiagnostic(MethodCfgOutcome outcome, int position) implements RequestQualificationReason {
    /** Validates the original diagnostic slot. */
    public MethodDiagnostic {
      Objects.requireNonNull(outcome);
      if (position < 0 || position >= outcome.diagnostics().size()) {
        throw new IllegalArgumentException("A method error requires an existing diagnostic slot");
      }
    }
  }

  /**
   * Error at an exact application diagnostic occurrence, independent of key equality.
   *
   * @param owner
   *          ordered application diagnostic list
   * @param position
   *          zero-based application diagnostic position
   */
  record ApplicationDiagnostic(List<AnalysisDiagnostic> owner, int position) implements RequestQualificationReason {
    /** Copies the owning slots without deduplication. */
    public ApplicationDiagnostic {
      owner = List.copyOf(owner);
      if (position < 0 || position >= owner.size()) {
        throw new IllegalArgumentException("An application error requires an existing diagnostic slot");
      }
    }

    /**
     * Returns this exact native diagnostic occurrence.
     *
     * @return diagnostic at the owning position
     */
    public AnalysisDiagnostic diagnostic() {
      return owner.get(position);
    }
  }
}
