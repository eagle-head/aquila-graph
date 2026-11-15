package org.aquilagraph.source.internal;



import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;

class DiagnosticEncodingTest {
  @Test
  void givenExplicitCausesWithTheSameCode_whenRetainedInTheActiveFormat_thenExactSevenFieldBytesAndTypedMeaningSurvive() {
    // Arrange
    var causes = List.of(AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE, AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var format = AstCodec.FORMAT_VERSION;
    for (var cause : causes) {
      var diagnostic = new AnalysisDiagnostic("diagnostic:provided", "control-flow.erroneous-semantics", DiagnosticStage.CONTROL_FLOW,
          DiagnosticSeverity.ERROR, "Provided observation", List.of(), cause);
      var expected = expectedBytes(format, cause);
      var writer = new CanonicalValueWriter(format);
      var codec = new RetainedValueCodec(false);

      // Act
      codec.write(writer, diagnostic);
      var actual = writer.toByteArray();
      var decoded = codec.read(new CanonicalValueReader(expected, format));

      // Assert
      assertArrayEquals(expected, actual, format + "/" + cause);
      assertEquals(diagnostic, decoded, format + "/" + cause);
    }
  }

  private static byte[] expectedBytes(String format, AnalysisOutcomeKind cause) {
    var writer = new CanonicalValueWriter(format);
    writer.text("analysis-diagnostic");
    writer.count(7);
    field(writer, "diagnosticKey", "text", "diagnostic:provided");
    field(writer, "code", "text", "control-flow.erroneous-semantics");
    field(writer, "stage", "diagnostic-stage", "CONTROL_FLOW");
    field(writer, "severity", "diagnostic-severity", "ERROR");
    field(writer, "message", "text", "Provided observation");
    writer.text("origins");
    writer.text("list");
    writer.count(0);
    field(writer, "outcomeKind", "analysis-outcome-kind", cause.name());
    return writer.toByteArray();
  }

  private static void field(CanonicalValueWriter writer, String name, String tag, String value) {
    writer.text(name);
    writer.text(tag);
    writer.text(value);
  }
}
