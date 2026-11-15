package org.aquilagraph.source.internal;

import java.util.Optional;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;

/** Supplies retained aggregate frames independently of aggregate admission. */
final class RetainedSourceFixture {
  private RetainedSourceFixture() {
  }

  static byte[] payloadWithAggregate(AstResult result, CopiedAstValues aggregate) {
    return payloadWithAggregate(result, aggregate, result.coverage());
  }

  static byte[] payloadWithAggregate(AstResult result, CopiedAstValues aggregate, LanguageFeatureCoverageResult coverage) {
    var format = result.identity().sourceModelVersion();
    var writer = new CanonicalValueWriter(format);
    var codec = new RetainedValueCodec(false, format);
    writer.text(result.profile().version());
    writer.text(result.identity().semanticProductionRevision());
    writer.text(result.completion().name());
    codec.write(writer, result.identity());
    var input = new SyntaxAnalysisInput(result.snapshot(), result.environment(), result.program(), Optional.of(result.aggregate()),
        result.diagnostics(), result.coverage());
    var shape = AstValueSchema.type(SyntaxAnalysisInput.class);
    var components = shape.components(input);
    writer.text(shape.tag());
    writer.count(shape.fields().size());
    for (var index = 0; index < shape.fields().size(); index++) {
      var field = shape.fields().get(index);
      writer.text(field);
      if (field.equals("aggregate")) {
        writer.text("optional");
        writer.flag(true);
        writer.text("source-aggregate");
        codec.write(writer, aggregate);
      } else {
        codec.write(writer, field.equals("languageFeatureCoverageResult") ? coverage : components.get(index));
      }
    }
    return writer.toByteArray();
  }
}
