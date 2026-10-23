package org.aquilagraph.source.admission;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CapturedValueEncoding;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.internal.AdmittedAstOutcome;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.internal.CanonicalAstEncoding;
import org.aquilagraph.source.internal.RetainedValueCodec;
import org.aquilagraph.source.internal.SourceRequirements;

/** Qualifies copied production and empty captures into truthful source outcomes. */
public final class AstProductionAdmission {
  private AstProductionAdmission() {
  }

  /**
   * Qualifies intrinsically valid copied input independently of graph support.
   *
   * @param input
   *          validated copied source and coverage values
   * @param profile
   *          exact source representation profile
   * @param semanticRevision
   *          copied semantic production revision
   * @return available admitted result or truthful unavailable provenance
   * @throws AstContractException
   *           if copied values contradict the requested source contract
   */
  public static AstOutcome outcome(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(profile, "profile");
    if (Objects.requireNonNull(semanticRevision, "semanticRevision").isBlank()) {
      throw new AstContractException("Semantic production revision cannot be blank", new IllegalArgumentException("semanticRevision"));
    }
    try {
      var completion = SourceRequirements.assess(input, profile, semanticRevision);
      if (completion == AstCompletion.UNAVAILABLE) {
        return unavailable(profile, Optional.of(input.snapshot()), Optional.of(input.environmentSnapshot()),
            Optional.of(input.compilerConfiguration()), Optional.of(input.languageFeatureCoverageResult()), input.diagnostics(),
            CanonicalAstEncoding.canonicalDigest(input, profile, semanticRevision));
      }
      return AstOutcome.available(AstResultAdmission.admit(input, profile, semanticRevision));
    } catch (IllegalArgumentException exception) {
      throw rejection(exception);
    }
  }

  /**
   * Retains an empty capture and initial configuration without acquiring a compiler.
   *
   * @param snapshot
   *          empty captured source input
   * @param configuration
   *          requested compiler configuration
   * @param profile
   *          requested source profile
   * @param coverage
   *          complete independently admitted empty coverage
   * @return unavailable source subject with explicit environment absence
   * @throws AstContractException
   *           if capture, coverage, configuration, or profile values contradict the empty-source contract
   */
  public static AstOutcome empty(InputSnapshot snapshot, CompilerConfiguration configuration, AstProfile profile,
      LanguageFeatureCoverageResult coverage) {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(coverage, "coverage");
    try {
      if (!snapshot.sourceFiles().isEmpty() || !profile.isSupported()
          || !coverage.validationEvidence().writtenInventory().snapshot().equals(snapshot)
          || coverage.coverage().release() != configuration.release()) {
        throw new IllegalArgumentException("Empty source admission requires exact empty capture, coverage, and supported profile");
      }
      var writer = new CanonicalValueWriter("unavailable-java-source/1");
      writer.text(profile.version());
      writer.bytes(CapturedValueEncoding.encode(Optional.of(snapshot), Optional.empty()));
      var codec = new RetainedValueCodec(true, AstResultAdmission.formatFor(profile));
      codec.write(writer, configuration);
      codec.write(writer, coverage);
      codec.write(writer, List.of());
      return unavailable(profile, Optional.of(snapshot), Optional.empty(), Optional.of(configuration), Optional.of(coverage), List.of(),
          writer.digest("source-subject"));
    } catch (IllegalArgumentException exception) {
      throw rejection(exception);
    }
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
  private static AstOutcome unavailable(AstProfile profile, Optional<InputSnapshot> input,
      Optional<AnalysisEnvironmentSnapshot> environment, Optional<CompilerConfiguration> configuration,
      Optional<LanguageFeatureCoverageResult> coverage, List<AnalysisDiagnostic> diagnostics, String digest) {
    return new AdmittedAstOutcome(profile, AstCompletion.UNAVAILABLE, Optional.empty(), input, environment, configuration, coverage,
        diagnostics, digest);
  }

  private static AstContractException rejection(IllegalArgumentException exception) {
    return exception instanceof AstContractException contract
        ? contract
        : new AstContractException("Copied AST result admission failed: " + exception.getMessage(), exception);
  }
}
