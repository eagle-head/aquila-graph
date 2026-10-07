package org.aquilagraph.source.admission;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CapturedValueEncoding;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxAnalysisInput;

/** Full retained AST format with intrinsic re-admission and exact identity verification. */
public final class AstCodec {
  /** Exact supported retained AST format. */
  public static final String FORMAT_VERSION = AstResult.SOURCE_MODEL_VERSION;

  private AstCodec() {
  }

  /**
   * Encodes every admitted source value, including excluded identity provenance.
   *
   * @param result
   *          admitted source result
   * @return independent retained bytes
   */
  public static byte[] encode(AstResult result) {
    Objects.requireNonNull(result, "result");
    var writer = new CanonicalValueWriter(FORMAT_VERSION);
    writer.text(result.profile().version());
    writer.text(result.identity().semanticProductionRevision());
    writer.text(result.completion().name());
    var codec = new RetainedValueCodec(false);
    codec.write(writer, result.identity());
    codec.write(writer, copiedInput(result));
    return writer.toByteArray();
  }

  /**
   * Materializes every retained field and canonical source sharing relationship.
   *
   * @param result
   *          admitted source result
   * @return complete immutable tagged value projection
   */
  public static AstValueProjection materialize(AstResult result) {
    Objects.requireNonNull(result, "result");
    return new AstValueProjector().project(copiedInput(result));
  }

  /**
   * Decodes present supported facts through intrinsic source and result admission.
   *
   * @param bytes
   *          complete retained payload
   * @param expected
   *          exact requested source identity
   * @return validated source result with its original qualification
   * @throws AstContractException
   *           if content, ownership, format, or qualification is invalid
   */
  public static AstResult decode(byte[] bytes, AstIdentity expected) {
    Objects.requireNonNull(expected, "expected");
    try {
      var reader = new CanonicalValueReader(bytes, FORMAT_VERSION);
      var profile = new AstProfile(reader.text());
      var semanticRevision = reader.text();
      var completion = AstCompletion.valueOf(reader.text());
      var codec = new RetainedValueCodec(false);
      var identity = RetainedValueCodec.required(codec.read(reader), AstIdentity.class);
      if (!identity.equals(expected)) {
        throw new IllegalArgumentException("Retained AST identity differs from the exact requested identity");
      }
      var input = RetainedValueCodec.required(codec.read(reader), SyntaxAnalysisInput.class);
      reader.requireFinished();
      var result = AstResultAdmission.admit(input, profile, semanticRevision);
      if (!result.identity().equals(identity) || result.completion() != completion) {
        throw new IllegalArgumentException("Retained AST content or source qualification differs from its admitted identity");
      }
      return result;
    } catch (IllegalArgumentException | ClassCastException exception) {
      throw new AstContractException("Retained AST decoding failed: " + exception.getMessage(), exception);
    }
  }

  static String canonicalDigest(SyntaxAnalysisInput input, AstProfile profile, String semanticRevision) {
    var writer = new CanonicalValueWriter("canonical-java-source/1");
    writer.text(FORMAT_VERSION);
    writer.text(profile.version());
    writer.text(semanticRevision);
    writer.bytes(CapturedValueEncoding.encode(Optional.of(input.snapshot()), Optional.of(input.environmentSnapshot())));
    new RetainedValueCodec(true).write(writer, input);
    return writer.digest("ast-content");
  }

  private static SyntaxAnalysisInput copiedInput(AstResult result) {
    return new SyntaxAnalysisInput(result.snapshot(), result.environment(), result.program(), Optional.of(result.aggregate()),
        result.diagnostics(), result.coverage());
  }
}
