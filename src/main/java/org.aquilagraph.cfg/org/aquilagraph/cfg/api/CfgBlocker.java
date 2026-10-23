package org.aquilagraph.cfg.api;

import org.aquilagraph.cfg.internal.values.CfgDiagnosticAdmission;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticStage;

/**
 * One observed refusal cause at an exact source occurrence or declaration slot.
 *
 * @param kind
 *          refusal category
 * @param astIdentity
 *          exact source result
 * @param methodKey
 *          selected declaration
 * @param occurrence
 *          optional owned source occurrence
 * @param slot
 *          observed requirement
 * @param diagnostic
 *          CFG admission diagnostic
 * @param evidenceDiagnostic
 *          original source diagnostic associated with the refused evidence
 */
public record CfgBlocker(MethodCfgState kind, AstIdentity astIdentity, String methodKey, Optional<SourceOccurrence> occurrence, String slot,
    AnalysisDiagnostic diagnostic, Optional<AnalysisDiagnostic> evidenceDiagnostic) {
  /**
   * Creates a refusal without inventing an originating source diagnostic.
   *
   * @param kind
   *          refusal category
   * @param astIdentity
   *          exact source result
   * @param methodKey
   *          selected declaration
   * @param occurrence
   *          optional owned source occurrence
   * @param slot
   *          observed requirement
   * @param diagnostic
   *          CFG admission diagnostic
   */
  public CfgBlocker(MethodCfgState kind, AstIdentity astIdentity, String methodKey, Optional<SourceOccurrence> occurrence, String slot,
      AnalysisDiagnostic diagnostic) {
    this(kind, astIdentity, methodKey, occurrence, slot, diagnostic, Optional.empty());
  }

  /** Rejects non-refusal states and foreign occurrence identities. */
  public CfgBlocker {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(astIdentity, "astIdentity");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(occurrence, "occurrence");
    Objects.requireNonNull(slot, "slot");
    Objects.requireNonNull(diagnostic, "diagnostic");
    CfgDiagnosticAdmission.requireCreatingStage(diagnostic);
    Objects.requireNonNull(evidenceDiagnostic, "evidenceDiagnostic");
    if (evidenceDiagnostic.isPresent() && (kind != MethodCfgState.ERRONEOUS_EVIDENCE || !evidenceDiagnostic.orElseThrow().isError()
        || evidenceDiagnostic.orElseThrow().stage() != DiagnosticStage.FRONTEND)) {
      throw new IllegalArgumentException("Only erroneous evidence may retain an originating error diagnostic");
    }
    if (methodKey.isBlank() || slot.isBlank() || kind == MethodCfgState.AVAILABLE || kind == MethodCfgState.NOT_APPLICABLE
        || kind == MethodCfgState.FAILED) {
      throw new IllegalArgumentException("A CFG blocker requires a refusal category and exact slot");
    }
    if (occurrence.isPresent() && !occurrence.orElseThrow().astIdentity().equals(astIdentity)) {
      throw new IllegalArgumentException("CFG blocker occurrence belongs to another source result");
    }
  }
}
