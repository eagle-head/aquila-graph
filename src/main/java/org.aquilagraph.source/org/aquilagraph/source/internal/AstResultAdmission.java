package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.admission.CoverageAdmission;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.SourceQualification;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.EnvironmentIdentityScheme;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.AttributedCoverageExpectations;

/** Atomic source qualification and complete content identity admission. */
public final class AstResultAdmission {
  private static final int WRITTEN_EXPRESSION_FORMAT = 19;
  private static final int MODULE_SERVICE_FORMAT = 20;
  private AstResultAdmission() {
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
    return admit(input, profile, semanticRevision, formatFor(profile, input.environmentSnapshot()));
  }

  /**
   * Selects the retained format compatible with the profile and captured environment.
   *
   * @param profile
   *          selected source profile
   * @return the resulting string
   */
  public static String formatFor(AstProfile profile) {
    return profile.supportsModuleServices() ? AstResult.MODULE_SERVICE_MODEL_VERSION : AstResult.WRITTEN_EXPRESSION_MODEL_VERSION;
  }

  /**
   * Selects the retained format compatible with the profile and captured environment.
   *
   * @param profile
   *          selected source profile
   * @param environment
   *          environment
   * @return the resulting string
   */
  public static String formatFor(AstProfile profile, AnalysisEnvironmentSnapshot environment) {
    return environment.identityScheme() == EnvironmentIdentityScheme.MODULE_SEMANTICS ? AstResult.SOURCE_MODEL_VERSION : formatFor(profile);
  }

  /**
   * Reconstructs a result under the specified retained-format contract.
   *
   * @param input
   *          captured source input
   * @param profile
   *          selected source profile
   * @param semanticRevision
   *          source semantic contract revision
   * @param format
   *          retained format identifier
   * @return the resulting ast result
   */
  public static AstResult admit(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision, String format) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(semanticRevision, "semanticRevision");
    try {
      if (!AstResult.supportsSourceModelVersion(format)) {
        throw new IllegalArgumentException("Unsupported retained AST format: " + format);
      }
      if ((input.environmentSnapshot().identityScheme() == EnvironmentIdentityScheme.MODULE_SEMANTICS) != (formatVersion(format) >= 21)) {
        throw new IllegalArgumentException("Retained AST format must agree with its environment identity scheme");
      }
      if (profile.supportsModuleServices() && formatVersion(format) < MODULE_SERVICE_FORMAT) {
        throw new IllegalArgumentException("Module service targets require retained AST format java-source/20");
      }
      if (profile.supportsWrittenExpressions() && formatVersion(format) < WRITTEN_EXPRESSION_FORMAT) {
        throw new IllegalArgumentException("The written-expression source profile requires retained AST format java-source/19");
      }
      var retained = retainedInput(input, format);
      var qualification = SourceRequirements.assess(retained, profile, semanticRevision);
      if (qualification.completion() == AstCompletion.UNAVAILABLE) {
        throw new IllegalArgumentException("Copied source values cannot establish an available AST result");
      }
      return available(retained, profile, semanticRevision, format, qualification);
    } catch (IllegalArgumentException exception) {
      throw rejection(exception);
    }
  }

  /**
   * Admits production once over the exact facts retained by the selected format.
   *
   * @param input
   *          copied production input
   * @param profile
   *          original source profile
   * @param semanticRevision
   *          original production revision
   * @return available or unavailable outcome with its established assessment
   */
  public static AstOutcome outcome(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision) {
    var format = formatFor(profile, input.environmentSnapshot());
    var retained = retainedInput(input, format);
    var qualification = SourceRequirements.assess(retained, profile, semanticRevision);
    if (qualification.completion() == AstCompletion.UNAVAILABLE) {
      return new AdmittedAstOutcome(profile, qualification, Optional.empty(), Optional.of(retained.snapshot()),
          Optional.of(retained.environmentSnapshot()), Optional.of(retained.compilerConfiguration()),
          Optional.of(retained.languageFeatureCoverageResult()), retained.diagnostics(),
          CanonicalAstEncoding.canonicalDigest(retained, profile, semanticRevision));
    }
    return AstOutcome.available(available(retained, profile, semanticRevision, format, qualification));
  }

  private static AstResult available(SyntaxAnalysisInput retained, AstProfile profile, String semanticRevision, String format,
      SourceQualification qualification) {
    var software = SoftwareVersion.from(retained.snapshot(), retained.environmentSnapshot());
    var identity = AstIdentity.fromContent(software.signature(), format, profile.version(), semanticRevision,
        CanonicalAstEncoding.canonicalDigest(retained, profile, semanticRevision, format));
    return new AdmittedAstResult(identity, profile, qualification, retained.snapshot(), retained.environmentSnapshot(), retained.program(),
        retained.aggregate().orElseThrow(), retained.diagnostics(), retained.languageFeatureCoverageResult());
  }

  private static SyntaxAnalysisInput retainedInput(SyntaxAnalysisInput input, String format) {
    var coverage = input.languageFeatureCoverageResult();
    var evidence = coverage.validationEvidence();
    var written = evidence.writtenInventory();
    if (formatVersion(format) >= WRITTEN_EXPRESSION_FORMAT || written.writtenExpressions().isEmpty()) {
      return input;
    }
    if (!written.writtenExpressions().orElseThrow().isEmpty()) {
      throw new IllegalArgumentException("Written expression obligations require retained AST format java-source/19");
    }
    // Earlier formats have no proof-presence field, even when no normalized regions were encountered.
    var historical = new SourceCoverageInventory(written.snapshot(), written.release(), written.contractIdentity(), written.contexts());
    var attributed = new AttributedCoverageExpectations(historical, evidence.attributedExpectations().projections());
    var retainedCoverage = CoverageAdmission.fromEvidence(coverage.coverage(), historical, attributed, evidence.outcomes(),
        evidence.unmatchedFacts(), evidence.diagnosticEmissions());
    return new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(), input.diagnostics(),
        retainedCoverage);
  }

  private static AstContractException rejection(IllegalArgumentException exception) {
    return exception instanceof AstContractException contract
        ? contract
        : new AstContractException("Copied AST result admission failed: " + exception.getMessage(), exception);
  }

  private static int formatVersion(String format) {
    return Integer.parseInt(format.substring(format.indexOf('/') + 1));
  }
}
