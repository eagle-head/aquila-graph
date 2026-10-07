package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;

/** A normal source outcome with admitted qualification and truthful captured provenance. */
public final class AstOutcome {
  private final AstProfile requestedProfile;
  private final AstCompletion sourceCompletion;
  private final Optional<AstResult> sourceResult;
  private final Optional<InputSnapshot> capturedSource;
  private final Optional<AnalysisEnvironmentSnapshot> capturedCompilerEnvironment;
  private final Optional<CompilerConfiguration> compilerConfiguration;
  private final Optional<LanguageFeatureCoverageResult> sourceCoverage;
  private final List<AnalysisDiagnostic> sourceDiagnostics;
  private final String canonicalSubject;

  AstOutcome(AstProfile profile, AstCompletion completion, Optional<AstResult> result, Optional<InputSnapshot> capturedInput,
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
  public AstProfile profile() {
    return requestedProfile;
  }

  /**
   * Returns source-only qualification.
   *
   * @return source-only qualification
   */
  public AstCompletion completion() {
    return sourceCompletion;
  }

  /**
   * Returns admitted available source result.
   *
   * @return admitted available source result
   */
  public Optional<AstResult> result() {
    return sourceResult;
  }

  /**
   * Returns available captured input.
   *
   * @return available captured input
   */
  public Optional<InputSnapshot> capturedInput() {
    return capturedSource;
  }

  /**
   * Returns available captured compiler environment.
   *
   * @return available captured compiler environment
   */
  public Optional<AnalysisEnvironmentSnapshot> capturedEnvironment() {
    return capturedCompilerEnvironment;
  }

  /**
   * Returns requested compiler configuration.
   *
   * @return requested compiler configuration
   */
  public Optional<CompilerConfiguration> requestedConfiguration() {
    return compilerConfiguration;
  }

  /**
   * Returns complete retained source coverage when captured.
   *
   * @return complete retained source coverage when captured
   */
  public Optional<LanguageFeatureCoverageResult> coverageQualification() {
    return sourceCoverage;
  }

  /**
   * Returns ordered source diagnostic occurrences.
   *
   * @return ordered source diagnostic occurrences
   */
  public List<AnalysisDiagnostic> diagnostics() {
    return sourceDiagnostics;
  }

  /**
   * Returns canonical content qualification of this source subject.
   *
   * @return canonical content qualification of this source subject
   */
  public String sourceSubjectDigest() {
    return canonicalSubject;
  }

  /**
   * Wraps an already admitted result without requalifying its source content.
   *
   * @param result
   *          admitted source result
   * @return exact normal source outcome
   */
  public static AstOutcome available(AstResult result) {
    Objects.requireNonNull(result, "result");
    return new AstOutcome(result.profile(), result.completion(), Optional.of(result), Optional.of(result.snapshot()),
        Optional.of(result.environment()), Optional.of(result.environment().compilerConfiguration()), Optional.of(result.coverage()),
        result.diagnostics(), result.identity().canonicalContentDigest());
  }
}
