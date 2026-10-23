package org.aquilagraph.source.internal;

import java.util.Optional;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CapturedValueEncoding;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;

/** Canonical source content identity, independent of retained provenance. */
public final class CanonicalAstEncoding {
  private CanonicalAstEncoding() {
  }
  /**
   * Hashes the canonical source fields under the specified format and semantic contract.
   *
   * @param input
   *          captured source input
   * @param profile
   *          selected source profile
   * @param semanticRevision
   *          source semantic contract revision
   * @return the resulting string
   */
  public static String canonicalDigest(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision) {
    return canonicalDigest(input, profile, semanticRevision, AstResultAdmission.formatFor(profile, input.environmentSnapshot()));
  }

  /**
   * Hashes the canonical source fields under the specified format and semantic contract.
   *
   * @param input
   *          captured source input
   * @param profile
   *          selected source profile
   * @param semanticRevision
   *          source semantic contract revision
   * @param format
   *          retained format identifier
   * @return the resulting string
   */
  public static String canonicalDigest(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision, String format) {
    var writer = new CanonicalValueWriter("canonical-java-source/1");
    writer.text(format);
    writer.text(profile.version());
    writer.text(semanticRevision);
    writer.bytes(CapturedValueEncoding.encode(Optional.of(input.snapshot()), Optional.of(input.environmentSnapshot())));
    new RetainedValueCodec(true, format).write(writer, input);
    return writer.digest("ast-content");
  }

}
