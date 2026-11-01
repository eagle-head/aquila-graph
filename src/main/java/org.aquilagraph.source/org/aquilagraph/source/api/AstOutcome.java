package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;

import org.aquilagraph.source.internal.AdmittedAstOutcome;
/** A normal source outcome with admitted qualification and truthful captured provenance. */
public sealed interface AstOutcome permits AdmittedAstOutcome {

  /**
   * Returns requested source profile.
   *
   * @return requested source profile
   */
  AstProfile profile();

  /**
   * Returns source-only qualification.
   *
   * @return source-only qualification
   */
  default AstCompletion completion() {
    return qualification().completion();
  }

  /**
   * Returns the admitted source decision and ordered blocking explanations.
   *
   * @return immutable assessment readable without compiler or CFG acquisition
   */
  SourceQualification qualification();

  /**
   * Returns admitted available source result.
   *
   * @return admitted available source result
   */
  Optional<AstResult> result();

  /**
   * Returns available captured input.
   *
   * @return available captured input
   */
  Optional<InputSnapshot> capturedInput();

  /**
   * Returns available captured compiler environment.
   *
   * @return available captured compiler environment
   */
  Optional<AnalysisEnvironmentSnapshot> capturedEnvironment();

  /**
   * Returns requested compiler configuration.
   *
   * @return requested compiler configuration
   */
  Optional<CompilerConfiguration> requestedConfiguration();

  /**
   * Returns complete retained source coverage when captured.
   *
   * @return complete retained source coverage when captured
   */
  Optional<LanguageFeatureCoverageResult> coverageQualification();

  /**
   * Returns ordered source diagnostic occurrences.
   *
   * @return ordered source diagnostic occurrences
   */
  List<AnalysisDiagnostic> diagnostics();

  /**
   * Returns canonical content qualification of this source subject.
   *
   * @return canonical content qualification of this source subject
   */
  String sourceSubjectDigest();
  /**
   * Wraps an already admitted result without requalifying its source content.
   *
   * @param result
   *          admitted source result
   * @return exact normal source outcome
   */
  public static AstOutcome available(AstResult result) {
    Objects.requireNonNull(result, "result");
    return new AdmittedAstOutcome(result.profile(), result.qualification(), Optional.of(result), Optional.of(result.snapshot()),
        Optional.of(result.environment()), Optional.of(result.environment().compilerConfiguration()), Optional.of(result.coverage()),
        result.diagnostics(), result.identity().canonicalContentDigest());
  }
}
