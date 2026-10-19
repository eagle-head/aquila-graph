package org.aquilagraph.source.admission;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.CapturedValueEncoding;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import java.util.List;
import org.aquilagraph.source.api.AstAggregateAdmission;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.AttributedCoverageExpectations;

/** Atomic source qualification and complete content identity admission. */
public final class AstResultAdmission {
  private AstResultAdmission() {
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
        return AstAggregateAdmission.unavailable(profile, Optional.of(input.snapshot()), Optional.of(input.environmentSnapshot()),
            Optional.of(input.compilerConfiguration()), Optional.of(input.languageFeatureCoverageResult()), input.diagnostics(),
            AstCodec.canonicalDigest(input, profile, semanticRevision));
      }
      return AstOutcome.available(admit(input, profile, semanticRevision));
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
      var codec = new RetainedValueCodec(true);
      codec.write(writer, configuration);
      codec.write(writer, coverage);
      codec.write(writer, List.of());
      return AstAggregateAdmission.unavailable(profile, Optional.of(snapshot), Optional.empty(), Optional.of(configuration),
          Optional.of(coverage), List.of(), writer.digest("source-subject"));
    } catch (IllegalArgumentException exception) {
      throw rejection(exception);
    }
  }

  /**
   * Admits an available result and derives identity from all retained source content.
   *
   * @param input
   *          validated copied source and coverage values
   * @param profile
   *          exact source representation profile
   * @param semanticRevision
   *          copied semantic production revision
   * @return immutable content-qualified result
   * @throws AstContractException
   *           if copied values cannot establish an available result
   */
  public static AstResult admit(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision) {
    return admit(input, profile, semanticRevision, AstResult.SOURCE_MODEL_VERSION);
  }

  static AstResult admit(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision, String format) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(semanticRevision, "semanticRevision");
    try {
      if (!AstResult.supportsSourceModelVersion(format)) {
        throw new IllegalArgumentException("Unsupported retained AST format: " + format);
      }
      if (profile.supportsWrittenExpressions() && !AstResult.SOURCE_MODEL_VERSION.equals(format)) {
        throw new IllegalArgumentException("The written-expression source profile requires retained AST format java-source/19");
      }
      var retained = retainedInput(input, format);
      var completion = SourceRequirements.assess(retained, profile, semanticRevision);
      if (completion == AstCompletion.UNAVAILABLE) {
        throw new IllegalArgumentException("Copied source values cannot establish an available AST result");
      }
      var software = SoftwareVersion.from(retained.snapshot(), retained.environmentSnapshot());
      var identity = AstIdentity.fromContent(software.signature(), format, profile.version(), semanticRevision,
          AstCodec.canonicalDigest(retained, profile, semanticRevision, format));
      return AstAggregateAdmission.result(identity, profile, completion, retained);
    } catch (IllegalArgumentException exception) {
      throw rejection(exception);
    }
  }

  private static SyntaxAnalysisInput retainedInput(SyntaxAnalysisInput input, String format) {
    var coverage = input.languageFeatureCoverageResult();
    var evidence = coverage.validationEvidence();
    var written = evidence.writtenInventory();
    if (AstResult.SOURCE_MODEL_VERSION.equals(format) || written.writtenExpressions().isEmpty()) {
      return input;
    }
    if (!written.writtenExpressions().orElseThrow().isEmpty()) {
      throw new IllegalArgumentException("Written expression obligations require retained AST format java-source/19");
    }
    // Earlier formats have no proof-presence field, even when no normalized regions were encountered.
    var historical = new SourceCoverageInventory(written.snapshot(), written.release(), written.contractIdentity(), written.contexts());
    var attributed = new AttributedCoverageExpectations(historical, evidence.attributedExpectations().projections());
    var retainedCoverage = LanguageFeatureCoverageResult.fromEvidence(coverage.coverage(), historical, attributed, evidence.outcomes(),
        evidence.unmatchedFacts(), evidence.diagnosticEmissions());
    return new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(), input.diagnostics(),
        retainedCoverage);
  }

  private static AstContractException rejection(IllegalArgumentException exception) {
    return exception instanceof AstContractException contract
        ? contract
        : new AstContractException("Copied AST result admission failed: " + exception.getMessage(), exception);
  }
}
