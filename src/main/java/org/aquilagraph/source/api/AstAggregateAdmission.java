package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.source.admission.CopiedAstValues;

/** Privileged construction bridge for copied-value admission. */
public final class AstAggregateAdmission {
  private AstAggregateAdmission() {
  }

  /**
   * Admits a complete copied source set without returning mutable construction state.
   *
   * @param values
   *          complete copied source set
   * @return intrinsically validated immutable aggregate
   */
  public static JavaAstAggregate admit(CopiedAstValues values) {
    Objects.requireNonNull(values, "values");
    var builder = new JavaAstAggregateBuilder(values.snapshot());
    values.sources().forEach(builder::add);
    values.bodies().forEach(body -> builder.addMethodBody(body.methodKey(), body.root(), body.origin(), body.uses(), body.invocations(),
        body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences()));
    values.scopes()
        .forEach(scope -> builder.addDeclarationScope(scope.scope(), scope.root(),
            new ExecutionDecisionRegistration(scope.uses(), scope.invocations(), scope.staticAccesses(), scope.concatenations(), List.of()),
            scope.attributedOccurrences()));
    values.memberOrders().forEach(builder::addDeclarationMemberOrder);
    values.associations().forEach(builder::addWrittenSourceAssociation);
    values.declarationTypes().forEach(builder::addDeclarationTypeEvidence);
    return builder.seal();
  }

  /**
   * Constructs a qualified source value after copied-value and content admission.
   *
   * @param identity
   *          validated full-content identity
   * @param profile
   *          exact source profile
   * @param completion
   *          source-only qualification
   * @param input
   *          intrinsically validated copied values
   * @return immutable source result
   */
  public static AstResult result(AstIdentity identity, AstProfile profile, AstCompletion completion, SyntaxAnalysisInput input) {
    return new AstResult(identity, profile, completion, input);
  }

  /**
   * Retains a normally diagnosed unavailable source subject after value admission.
   *
   * @param profile
   *          requested source profile
   * @param input
   *          available captured source values
   * @param environment
   *          available captured compiler environment
   * @param configuration
   *          requested compiler configuration
   * @param coverage
   *          complete captured source coverage
   * @param diagnostics
   *          ordered source diagnostic occurrences
   * @param digest
   *          admitted canonical source subject digest
   * @return immutable unavailable source outcome
   */
  public static AstOutcome unavailable(AstProfile profile, Optional<InputSnapshot> input, Optional<AnalysisEnvironmentSnapshot> environment,
      Optional<CompilerConfiguration> configuration, Optional<LanguageFeatureCoverageResult> coverage, List<AnalysisDiagnostic> diagnostics,
      String digest) {
    return new AstOutcome(profile, AstCompletion.UNAVAILABLE, Optional.empty(), input, environment, configuration, coverage, diagnostics,
        digest);
  }
}
