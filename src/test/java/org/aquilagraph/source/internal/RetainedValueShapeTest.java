package org.aquilagraph.source.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.retention.AstValueProjection;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class RetainedValueShapeTest {
  @Test
  void givenSharedAndEqualSeparateOccurrences_whenRetained_thenOnlyTheSharedInstanceReusesItsTarget() {
    // Arrange
    var first = emptyStatement();
    var second = emptyStatement();
    var values = List.of(first, second, first);
    var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);

    // Act
    new RetainedValueCodec(false).write(writer, values);
    var read = new CanonicalValueReader(writer.toByteArray(), AstCodec.FORMAT_VERSION);
    var decoded = (List<?>) new RetainedValueCodec(false).read(read);
    read.requireFinished();
    var projection = new AstValueProjector().project(values);

    // Assert
    assertEquals(first, second);
    assertNotSame(first, second);
    assertEquals(values, decoded);
    assertNotSame(decoded.get(0), decoded.get(1));
    assertSame(decoded.get(0), decoded.get(2));
    var targets = projection.links().stream().filter(link -> link.sourceOrdinal() == 0)
        .map(AstValueProjection.Link::targetOrdinal).toList();
    assertEquals(List.of(targets.getFirst(), targets.get(1), targets.getFirst()), targets);
    assertEquals(2, targets.stream().distinct().count());
  }

  @Test
  void givenAnnotationWithMissingOptionalFields_whenProjected_thenItsWrittenAndExplicitAbsenceFieldsStayOrdered() {
    // Arrange
    var annotation = new SyntaxValue.Annotation(SyntaxValue.AnnotationForm.MARKER, List.of(), SyntaxValue.AnnotationLocation.VALUE);
    var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);

    // Act
    new RetainedValueCodec(false).write(writer, annotation);
    var read = new CanonicalValueReader(writer.toByteArray(), AstCodec.FORMAT_VERSION);
    var decoded = new RetainedValueCodec(false).read(read);
    var projection = new AstValueProjector().project(decoded);

    // Assert
    read.requireFinished();
    assertEquals(annotation, decoded);
    var links = projection.links().stream().filter(link -> link.sourceOrdinal() == 0).toList();
    assertEquals(List.of("form", "arguments", "location", "applicableTargets", "closestTypeAdmissible", "argumentFailureCode",
        "repeatableContainerKey"), links.stream().map(AstValueProjection.Link::field).toList());
    assertEquals(List.of("syntax-value/annotation-form", "list", "syntax-value/annotation-location", "set", "flag", "optional/absent",
        "optional/absent"), links.stream().map(link -> projection.nodes().get(link.targetOrdinal()).tag()).toList());
    var marker = projection.nodes().get(links.getFirst().targetOrdinal()).scalarEncoding().orElseThrow();
    var scalar = new CanonicalValueReader(Base64.getDecoder().decode(marker), "source-value/1");
    assertEquals("syntax-value/annotation-form", scalar.text());
    assertEquals("MARKER", scalar.text());
    scalar.requireFinished();
  }

  @Test
  void givenWrongNestedValueInTypedEvidence_whenDecoded_thenItsOwnerRejectsBeforeEnteringTheWrongShape() {
    // Arrange
    var stringType = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String");
    var left = EvidenceValue.known(true, origin(StringExpressionEvidence.LEFT_SLOT));
    var right = EvidenceValue.known(false, origin(StringExpressionEvidence.RIGHT_SLOT));
    var constant = EvidenceValue.known(false, origin(StringExpressionEvidence.CONSTANT_SLOT));
    var evidence = new StringExpressionEvidence("scope", "node", stringType, left, right, constant);
    var valid = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
    var malformed = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
    var codec = new RetainedValueCodec(false);
    codec.write(valid, evidence);
    malformed.text("string-expression-evidence");
    malformed.count(6);
    malformed.text("scopeKey");
    codec.write(malformed, "scope");
    malformed.text("binaryNodeId");
    codec.write(malformed, "node");
    malformed.text("stringType");
    codec.write(malformed, stringType);
    malformed.text("leftIsString");
    malformed.text("evidence-value");
    malformed.count(5);
    malformed.text("state");
    codec.write(malformed, EvidenceState.KNOWN);
    malformed.text("knownValue");
    malformed.text("optional/present");
    for (var index = 0; index < 20_000; index++) {
      malformed.text("optional/present");
    }
    malformed.text("optional/absent");

    // Act
    var decoded = codec.read(new CanonicalValueReader(valid.toByteArray(), AstCodec.FORMAT_VERSION), StringExpressionEvidence.class);
    var failure = assertThrows(ClassCastException.class, () -> codec.read(
        new CanonicalValueReader(malformed.toByteArray(), AstCodec.FORMAT_VERSION), StringExpressionEvidence.class));

    // Assert
    assertEquals(evidence, decoded);
    assertEquals("Wrong retained value type: evidence-value/knownValue", failure.getMessage());
  }

  private static EvidenceOrigin origin(String slot) {
    return new EvidenceOrigin("scope", "node", slot, Optional.empty());
  }

  @TestFactory
  Stream<DynamicTest> givenMalformedRetainedFrames_whenDecoded_thenTheSpecificShapeViolationRejects() {
    return Stream.of(
        new Malformed("AnUnknownTag", "Unknown retained source tag", writer -> writer.text("foreign-shape")),
        new Malformed("AWrongFieldCount", "Wrong retained field count", writer -> {
          writer.text("syntax-value/statement-label");
          writer.count(0);
        }),
        new Malformed("AnUnexpectedFieldName", "Wrong retained field order", writer -> {
          writer.text("syntax-value/statement-label");
          writer.count(1);
          writer.text("label");
        }),
        new Malformed("AnOutOfRangeScalar", "outside its tagged width", writer -> {
          writer.text("int8");
          writer.integer(128);
        }),
        new Malformed("AnOversizedSequence", "count exceeds", writer -> {
          writer.text("list");
          writer.count(Integer.MAX_VALUE);
        }),
        new Malformed("ARepeatedSetMember", "repeated entries", writer -> {
          writer.text("set");
          writer.count(2);
          writer.text("text");
          writer.text("repeated");
          writer.text("text");
          writer.text("repeated");
        }),
        new Malformed("AnUnresolvedOccurrenceReference", "earlier complete occurrence", writer -> {
          writer.text("source-node-reference");
          writer.integer(0);
        }),
        new Malformed("AnUnfinishedOccurrenceReference", "earlier complete occurrence", RetainedValueShapeTest::unfinishedReference))
        .map(example -> DynamicTest.dynamicTest(
            "given" + example.context() + "_whenDecoded_thenTheExactShapeViolationRejects", () -> {
          // Arrange
          var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          example.write().accept(writer);
          var reader = new CanonicalValueReader(writer.toByteArray(), AstCodec.FORMAT_VERSION);

          // Act
          var failure = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false).read(reader));

          // Assert
          assertTrue(failure.getMessage().contains(example.diagnostic()), failure.toString());
        }));
  }

  private static void unfinishedReference(CanonicalValueWriter writer) {
    var codec = new RetainedValueCodec(false);
    writer.text("source-node");
    writer.integer(0);
    writer.count(9);
    writer.text("sourceFileKey");
    codec.write(writer, "source");
    writer.text("structuralPath");
    codec.write(writer, SyntaxStructuralPath.sourceFileRoot());
    writer.text("sourceKind");
    codec.write(writer, JavaSourceKind.BLOCK);
    writer.text("syntaxKind");
    codec.write(writer, SyntaxKind.BLOCK);
    writer.text("type");
    codec.write(writer, SyntaxType.NotApplicable.INSTANCE);
    writer.text("value");
    codec.write(writer, SyntaxValue.None.INSTANCE);
    writer.text("children");
    writer.text("list");
    writer.count(1);
    writer.text("syntax-child");
    writer.count(3);
    writer.text("role");
    codec.write(writer, SyntaxRole.STATEMENT);
    writer.text("position");
    codec.write(writer, 0);
    writer.text("node");
    writer.text("source-node-reference");
    writer.integer(0);
  }

  private static SyntaxNode emptyStatement() {
    return SyntaxNode.withoutDirectSource("source", SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.EMPTY_STATEMENT,
        SyntaxKind.EMPTY, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty());
  }

  private record Malformed(String context, String diagnostic, Consumer<CanonicalValueWriter> write) {
  }
}
