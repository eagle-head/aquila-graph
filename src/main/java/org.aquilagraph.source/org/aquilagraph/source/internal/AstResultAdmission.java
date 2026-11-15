package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.SyntaxAnalysisInput;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.SourceQualification;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;

/** Atomic source qualification and complete content identity admission. */
public final class AstResultAdmission {
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
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(semanticRevision, "semanticRevision");
    try {
      var qualification = SourceRequirements.assess(input, profile, semanticRevision);
      if (qualification.completion() == AstCompletion.UNAVAILABLE) {
        throw new IllegalArgumentException("Copied source values cannot establish an available AST result");
      }
      return available(input, profile, semanticRevision, qualification);
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
    var qualification = SourceRequirements.assess(input, profile, semanticRevision);
    if (qualification.completion() == AstCompletion.UNAVAILABLE) {
      return new AdmittedAstOutcome(profile, qualification, Optional.empty(), Optional.of(input.snapshot()),
          Optional.of(input.environmentSnapshot()), Optional.of(input.compilerConfiguration()),
          Optional.of(input.languageFeatureCoverageResult()), input.diagnostics(),
          CanonicalAstEncoding.canonicalDigest(input, profile, semanticRevision));
    }
    return AstOutcome.available(available(input, profile, semanticRevision, qualification));
  }

  private static AstResult available(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision,
      SourceQualification qualification) {
    var software = SoftwareVersion.from(input.snapshot(), input.environmentSnapshot());
    var identity = AstIdentity.fromContent(software.signature(), AstResult.SOURCE_MODEL_VERSION, profile.version(), semanticRevision,
        CanonicalAstEncoding.canonicalDigest(input, profile, semanticRevision));
    return new AdmittedAstResult(identity, profile, qualification, input.snapshot(), input.environmentSnapshot(), input.program(),
        input.aggregate().orElseThrow(), input.diagnostics(), input.languageFeatureCoverageResult());
  }

  private static AstContractException rejection(IllegalArgumentException exception) {
    return exception instanceof AstContractException contract
        ? contract
        : new AstContractException("Copied AST result admission failed: " + exception.getMessage(), exception);
  }

}
