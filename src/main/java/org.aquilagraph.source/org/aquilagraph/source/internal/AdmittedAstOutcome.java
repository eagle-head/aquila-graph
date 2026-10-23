package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;

/** A normal source outcome with admitted qualification and truthful captured provenance. */
public final class AdmittedAstOutcome implements AstOutcome {
  private final AstProfile requestedProfile;
  private final AstCompletion sourceCompletion;
  private final Optional<AstResult> sourceResult;
  private final Optional<InputSnapshot> capturedSource;
  private final Optional<AnalysisEnvironmentSnapshot> capturedCompilerEnvironment;
  private final Optional<CompilerConfiguration> compilerConfiguration;
  private final Optional<LanguageFeatureCoverageResult> sourceCoverage;
  private final List<AnalysisDiagnostic> sourceDiagnostics;
  private final String canonicalSubject;

  /**
   * Owns the admitted outcome and enforces its state/result correspondence.
   *
   * @param profile
   *          selected source profile
   * @param completion
   *          completion
   * @param result
   *          result
   * @param capturedInput
   *          captured input
   * @param capturedEnvironment
   *          captured environment
   * @param requestedConfiguration
   *          requested configuration
   * @param coverageQualification
   *          coverage qualification
   * @param diagnostics
   *          diagnostics
   * @param sourceSubjectDigest
   *          source subject digest
   */
  public AdmittedAstOutcome(AstProfile profile, AstCompletion completion, Optional<AstResult> result, Optional<InputSnapshot> capturedInput,
      Optional<AnalysisEnvironmentSnapshot> capturedEnvironment, Optional<CompilerConfiguration> requestedConfiguration,
      Optional<LanguageFeatureCoverageResult> coverageQualification, List<AnalysisDiagnostic> diagnostics, String sourceSubjectDigest) {
    requestedProfile = Objects.requireNonNull(profile, "profile");
    sourceCompletion = Objects.requireNonNull(completion, "completion");
    sourceResult = Objects.requireNonNull(result, "result");
    capturedSource = Objects.requireNonNull(capturedInput, "capturedInput");
    capturedCompilerEnvironment = Objects.requireNonNull(capturedEnvironment, "capturedEnvironment");
    compilerConfiguration = Objects.requireNonNull(requestedConfiguration, "requestedConfiguration");
    sourceCoverage = Objects.requireNonNull(coverageQualification, "coverageQualification");
    sourceDiagnostics = List.copyOf(diagnostics);
    sourceDiagnostics.forEach(SourceDiagnosticAdmission::requireCreatingStage);
    canonicalSubject = Objects.requireNonNull(sourceSubjectDigest, "sourceSubjectDigest");
    if ((completion == AstCompletion.UNAVAILABLE) == result.isPresent() || sourceSubjectDigest.isBlank()) {
      throw new IllegalArgumentException("A source outcome requires exact result presence and content qualification");
    }
    if (result.isPresent()) {
      var value = result.orElseThrow();
      if (!value.profile().equals(profile) || value.completion() != completion || !capturedInput.equals(Optional.of(value.snapshot()))
          || !capturedEnvironment.equals(Optional.of(value.environment()))
          || !requestedConfiguration.equals(Optional.of(value.environment().compilerConfiguration()))
          || !coverageQualification.equals(Optional.of(value.coverage())) || !diagnostics.equals(value.diagnostics())
          || !sourceSubjectDigest.equals(value.identity().canonicalContentDigest())) {
        throw new IllegalArgumentException("Source outcome must retain its exact admitted result and provenance");
      }
    }
  }

  /**
   * Returns requested source profile.
   *
   * @return requested source profile
   */
  @Override
  public AstProfile profile() {
    return requestedProfile;
  }

  /**
   * Returns source-only qualification.
   *
   * @return source-only qualification
   */
  @Override
  public AstCompletion completion() {
    return sourceCompletion;
  }

  /**
   * Returns admitted available source result.
   *
   * @return admitted available source result
   */
  @Override
  public Optional<AstResult> result() {
    return sourceResult;
  }

  /**
   * Returns available captured input.
   *
   * @return available captured input
   */
  @Override
  public Optional<InputSnapshot> capturedInput() {
    return capturedSource;
  }

  /**
   * Returns available captured compiler environment.
   *
   * @return available captured compiler environment
   */
  @Override
  public Optional<AnalysisEnvironmentSnapshot> capturedEnvironment() {
    return capturedCompilerEnvironment;
  }

  /**
   * Returns requested compiler configuration.
   *
   * @return requested compiler configuration
   */
  @Override
  public Optional<CompilerConfiguration> requestedConfiguration() {
    return compilerConfiguration;
  }

  /**
   * Returns complete retained source coverage when captured.
   *
   * @return complete retained source coverage when captured
   */
  @Override
  public Optional<LanguageFeatureCoverageResult> coverageQualification() {
    return sourceCoverage;
  }

  /**
   * Returns ordered source diagnostic occurrences.
   *
   * @return ordered source diagnostic occurrences
   */
  @Override
  public List<AnalysisDiagnostic> diagnostics() {
    return sourceDiagnostics;
  }

  /**
   * Returns canonical content qualification of this source subject.
   *
   * @return canonical content qualification of this source subject
   */
  @Override
  public String sourceSubjectDigest() {
    return canonicalSubject;
  }

}
