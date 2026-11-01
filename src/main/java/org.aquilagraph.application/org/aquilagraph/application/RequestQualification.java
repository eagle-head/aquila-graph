package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/** Immutable application decision bound to its exact context owners and diagnostic slots. */
public final class RequestQualification {
  private final ProgramRepresentation representation;
  private final List<AnalysisDiagnostic> diagnostics;
  private final AnalysisCompletion establishedCompletion;
  private final List<RequestQualificationReason> blockingReasons;

  RequestQualification(ProgramRepresentation representation, List<AnalysisDiagnostic> diagnostics, AnalysisCompletion completion,
      List<RequestQualificationReason> reasons) {
    this.representation = representation;
    this.diagnostics = List.copyOf(diagnostics);
    establishedCompletion = completion;
    blockingReasons = List.copyOf(reasons);
  }

  /**
   * Establishes the request decision through the application-owned assessment.
   *
   * @param representation
   *          exact source and optional CFG context
   * @param diagnostics
   *          ordered application-created diagnostic occurrences
   * @return admitted immutable assessment
   */
  public static RequestQualification assess(ProgramRepresentation representation, List<AnalysisDiagnostic> diagnostics) {
    return AnalysisQualification.assess(Objects.requireNonNull(representation, "representation"), List.copyOf(diagnostics));
  }

  /**
   * Returns the established overall decision.
   *
   * @return request completion
   */
  public AnalysisCompletion completion() {
    return establishedCompletion;
  }

  /**
   * Returns every independent blocking cause in assessment order.
   *
   * @return immutable typed context and diagnostic references
   */
  public List<RequestQualificationReason> reasons() {
    return blockingReasons;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof RequestQualification value && representation.equals(value.representation)
        && diagnostics.equals(value.diagnostics) && establishedCompletion == value.establishedCompletion
        && blockingReasons.equals(value.blockingReasons));
  }

  @Override
  public int hashCode() {
    return Objects.hash(representation, diagnostics, establishedCompletion, blockingReasons);
  }

  List<AnalysisDiagnostic> applicationDiagnostics() {
    return diagnostics;
  }

  boolean matches(ProgramRepresentation candidate, List<AnalysisDiagnostic> applicationDiagnostics) {
    return representation.ast().equals(candidate.ast())
        && representation.cfg().stream().allMatch(value -> candidate.cfg().filter(value::equals).isPresent())
        && representation.cfg().isPresent() == candidate.cfg().isPresent() && diagnostics.equals(applicationDiagnostics);
  }
}
