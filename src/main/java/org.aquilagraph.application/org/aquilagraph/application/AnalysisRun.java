package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.NotRequested;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.JavaMethod;

/**
 * Exact request identity and references to independently owned domain results.
 *
 * @param identity
 *          represented source and selection provenance
 * @param representation
 *          established source and optional derivation
 * @param qualification
 *          admitted application assessment
 * @param applicationDiagnostics
 *          ordered application-created diagnostic occurrences
 */
public record AnalysisRun(AnalysisRequestIdentity identity, ProgramRepresentation representation, RequestQualification qualification,
    List<AnalysisDiagnostic> applicationDiagnostics) {
  /** Retains the assessment's exact owners after checking the supplied context values. */
  public AnalysisRun {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(representation, "representation");
    Objects.requireNonNull(qualification, "qualification");
    applicationDiagnostics = List.copyOf(Objects.requireNonNull(applicationDiagnostics, "applicationDiagnostics"));
    if (!identity.source().equals(AnalysisRequestIdentity.sourceSubject(representation.ast()))
        || !qualification.matches(representation, applicationDiagnostics)) {
      throw new IllegalArgumentException("Analysis run must retain its source subject and actual context qualification");
    }
    applicationDiagnostics = List.copyOf(qualification.applicationDiagnostics());
    representation = qualification.representation();
    validateSelection(identity, representation);
  }

  /**
   * Assesses the exact contexts and constructs their request run.
   *
   * @param identity
   *          represented source and selection provenance
   * @param representation
   *          established source and optional derivation
   * @param applicationDiagnostics
   *          ordered application-created occurrences
   * @return admitted run with a single established assessment
   */
  public static AnalysisRun create(AnalysisRequestIdentity identity, ProgramRepresentation representation,
      List<AnalysisDiagnostic> applicationDiagnostics) {
    var diagnostics = List.copyOf(applicationDiagnostics);
    var qualification = RequestQualification.assess(representation, diagnostics);
    return new AnalysisRun(identity, representation, qualification, diagnostics);
  }

  /**
   * Returns completion from the retained application assessment.
   *
   * @return established request completion
   */
  public AnalysisCompletion completion() {
    return qualification.completion();
  }

  /**
   * Returns the admitted occurrence composition and exact native-cause correlations.
   *
   * @return immutable diagnostic catalog belonging to the retained assessment
   */
  public AnalysisDiagnostics diagnostics() {
    return qualification.diagnostics();
  }

  /**
   * Resolves canonical declaration coverage without inventing a missing graph.
   *
   * @param methodKey
   *          exact canonical declaration
   * @return explicit coverage or absence for an unknown declaration
   */
  public Optional<MethodCoverage> methodCoverage(String methodKey) {
    if (Objects.requireNonNull(methodKey, "methodKey").isBlank()) {
      throw new IllegalArgumentException("Method coverage requires a nonblank canonical key");
    }
    var declaration = representation.ast().result().flatMap(ast -> ast.method(methodKey));
    return declaration.map(method -> {
      var cfg = representation.cfg();
      var outcome = cfg.stream().flatMap(value -> value.methods().stream()).filter(value -> value.methodKey().equals(methodKey))
          .findFirst();
      var selected = switch (identity.selection()) {
        case NotRequested _ -> false;
        case ProductionSelection production -> production.methodKeys().contains(methodKey);
        case ExistingSelection existing -> existing.request().methodKeys().contains(methodKey);
      };
      var state = outcome.map(value -> MethodCoverageState.valueOf(value.state().name()))
          .orElse(selected ? MethodCoverageState.REQUEST_REJECTED : MethodCoverageState.NOT_REQUESTED);
      return new MethodCoverage(method, state, outcome);
    });
  }

  private static void validateSelection(AnalysisRequestIdentity identity, ProgramRepresentation representation) {
    Optional<CfgRequest> expected = switch (identity.selection()) {
      case NotRequested _ -> Optional.empty();
      case ProductionSelection production ->
        production.boundIdentity().map(ast -> new CfgRequest(ast, production.methodKeys(), production.selection().profile()));
      case ExistingSelection existing -> representation.ast().result().map(ignored -> existing.request());
    };
    if (!expected.equals(representation.cfg().map(value -> value.request()))) {
      throw new IllegalArgumentException("Analysis run must retain the exact requested source binding and selection");
    }
    if (identity.selection() instanceof ProductionSelection production && production.selection().scope() == CfgSelection.Scope.ALL_BODIES
        && !production.methodKeys()
            .equals(representation.ast().result()
                .map(ast -> ast.program().methods().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).toList())
                .orElseGet(List::of))) {
      throw new IllegalArgumentException("All-body selection must equal the produced source's canonical method bodies");
    }
  }

  /**
   * Checks whether this exact run has complete source and full qualified body coverage.
   *
   * @return eligibility for completed-run publication
   */
  public boolean isPublishable() {
    return CompletedRunPublicationEligibility.accepts(this);
  }
}
