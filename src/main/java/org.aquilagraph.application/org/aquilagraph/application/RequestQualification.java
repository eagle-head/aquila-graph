package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/** Immutable application decision bound to its exact context owners and diagnostic slots. */
public final class RequestQualification {
  private final ProgramRepresentation nativeRepresentation;
  private final List<AnalysisDiagnostic> nativeApplicationDiagnostics;
  private final AnalysisCompletion establishedCompletion;
  private final List<RequestQualificationReason> blockingReasons;
  private final AnalysisDiagnostics diagnosticOccurrences;

  RequestQualification(ProgramRepresentation representation, List<AnalysisDiagnostic> diagnostics, AnalysisCompletion completion,
      List<RequestQualificationReason> reasons) {
    nativeRepresentation = representation;
    nativeApplicationDiagnostics = List.copyOf(diagnostics);
    establishedCompletion = completion;
    blockingReasons = List.copyOf(reasons);
    diagnosticOccurrences = new AnalysisDiagnostics(representation, nativeApplicationDiagnostics, blockingReasons);
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
    return this == other || (other instanceof RequestQualification value && nativeRepresentation.equals(value.nativeRepresentation)
        && nativeApplicationDiagnostics.equals(value.nativeApplicationDiagnostics));
  }

  @Override
  public int hashCode() {
    return Objects.hash(nativeRepresentation, nativeApplicationDiagnostics);
  }

  List<AnalysisDiagnostic> applicationDiagnostics() {
    return nativeApplicationDiagnostics;
  }

  ProgramRepresentation representation() {
    return nativeRepresentation;
  }

  AnalysisDiagnostics diagnostics() {
    return diagnosticOccurrences;
  }

  boolean matches(ProgramRepresentation candidate, List<AnalysisDiagnostic> applicationDiagnostics) {
    return nativeRepresentation.equals(candidate) && nativeApplicationDiagnostics.equals(applicationDiagnostics);
  }
}
