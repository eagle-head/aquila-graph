package org.aquilagraph.source.internal;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
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
    return CanonicalValueWriter.hash("canonical-java-source/1", "ast-content", writer -> {
      sourceFrame(writer, profile);
      writer.text(semanticRevision);
      new RetainedValueCodec(true).write(writer, input);
    });
  }

  /**
   * Hashes the captured empty source subject with explicit absent compiler evidence.
   *
   * @param snapshot
   *          captured empty source input
   * @param configuration
   *          requested compiler configuration
   * @param profile
   *          requested source profile
   * @param coverage
   *          independently admitted empty coverage
   * @return canonical empty source subject digest
   */
  public static String emptyDigest(InputSnapshot snapshot, CompilerConfiguration configuration, AstProfile profile,
      LanguageFeatureCoverageResult coverage) {
    return CanonicalValueWriter.hash("unavailable-java-source/1", "source-subject", writer -> {
      sourceFrame(writer, profile);
      var codec = new RetainedValueCodec(true);
      codec.write(writer, Optional.of(snapshot));
      codec.write(writer, Optional.empty());
      codec.write(writer, configuration);
      codec.write(writer, coverage);
      codec.write(writer, List.of());
    });
  }

  private static void sourceFrame(CanonicalValueWriter writer, AstProfile profile) {
    writer.text(AstResult.SOURCE_MODEL_VERSION);
    writer.text(profile.version());
  }
}
