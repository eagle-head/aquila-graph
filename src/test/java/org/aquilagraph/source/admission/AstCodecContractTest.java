package org.aquilagraph.source.admission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class AstCodecContractTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenContradictoryKnownWrittenBodyTypeInAFullPayload_whenDecoding_thenIntrinsicRejectionPrecedesQualification() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .flatMap(profile -> Stream.of(false, true).map(local -> DynamicTest.dynamicTest(profile.version() + ": " + local,
            () -> assertContradictoryBodyTypePayload(profile, local))));
  }

  private void assertContradictoryBodyTypePayload(AstProfile profile, boolean local) throws IOException {
    // Arrange
    var source = local ? "class Example { int subject(long input) { int narrowed = (int) input; return narrowed; } }"
        : "class Example { int subject(long input) { return (int) input; } }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var original = new JavaFrontend(new JavaSourceLoader())
        .produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile).result().orElseThrow();
    var kind = local ? SyntaxKind.LOCAL_DECLARATION : SyntaxKind.TYPE_CAST;
    var role = local ? SyntaxRole.DECLARED_TYPE : SyntaxRole.TYPE_OPERAND;
    var target = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind).findFirst().orElseThrow()
        .requiredChild(role);
    var contradictory = SyntaxNode.source(target.sourceFileKey(), target.structuralPath(), target.sourceKind(), target.syntaxKind(),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long"),
            Optional.of(new JavaTypeStructure.Primitive(JavaTypeCategory.LONG))),
        new SyntaxValue.PrimitiveType(JavaTypeCategory.LONG), target.children(), target.semanticReference(), target.origin().orElseThrow());
    var bytes = AstCodec.encode(original);
    var oldContent = leafNodeContent(target);
    var newContent = leafNodeContent(contradictory);
    var offset = -1;
    var matches = 0;
    for (var index = 0; index <= bytes.length - oldContent.length; index++) {
      if (Arrays.equals(bytes, index, index + oldContent.length, oldContent, 0, oldContent.length)) {
        offset = index;
        matches++;
      }
    }
    assertEquals(1, matches, "The complete payload must define this exact written type once");
    var altered = new ByteArrayOutputStream();
    altered.write(bytes, 0, offset);
    altered.writeBytes(newContent);
    altered.write(bytes, offset + oldContent.length, bytes.length - offset - oldContent.length);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(altered.toByteArray(), original.identity()));
    var decoded = AstCodec.decode(bytes, original.identity());

    // Assert
    assertTrue(failure.getMessage().contains("written body type"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(original.identity(), decoded.identity());
    assertEquals(original.coverage().validationEvidence().writtenInventory(), decoded.coverage().validationEvidence().writtenInventory());
    assertEquals(original.coverage().validationEvidence().attributedExpectations(),
        decoded.coverage().validationEvidence().attributedExpectations());
    assertArrayEquals(bytes, AstCodec.encode(original));
  }

  private static byte[] leafNodeContent(SyntaxNode node) {
    assertTrue(node.children().isEmpty());
    var writer = new CanonicalValueWriter("");
    new RetainedValueCodec(false).write(writer, node);
    var prefix = new CanonicalValueWriter("source-node");
    prefix.integer(0);
    return Arrays.copyOfRange(writer.toByteArray(), Integer.BYTES + prefix.toByteArray().length, writer.toByteArray().length);
  }

  @TestFactory
  Stream<DynamicTest> givenSameKeyAlteredMethodPayload_whenDecoding_thenDeclarationContradictionPrecedesIdentityValidation() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED).flatMap(profile -> Stream.of("signature", "returnType")
        .map(field -> DynamicTest.dynamicTest(profile.version() + "/" + field, () -> {
          // Arrange
          var original = fixture(profile);
          var bytes = payload(original, "method", field);

          // Act
          var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(bytes, original.identity()));

          // Assert
          Throwable cause = failure;
          while (cause.getCause() != null) {
            cause = cause.getCause();
          }
          assertInstanceOf(IllegalArgumentException.class, cause);
          assertTrue(cause.getMessage().contains("Canonical method declaration contradicts"), failure.toString());
          assertEquals(AstCompletion.COMPLETE, original.completion());
          assertArrayEquals(AstCodec.encode(original), payload(original, "method", ""));
        })));
  }

  @Test
  void givenRetainedOperatorContradictingSourceKind_whenDecoding_thenIntrinsicContradictionPrecedesIdentityValidation() throws IOException {
    // Arrange: alter one operator payload in an otherwise real canonical result.
    var original = fixture();
    var bytes = AstCodec.encode(original);
    var oldValue = operatorPayload("PLUS");
    var newValue = operatorPayload("DIVIDE");
    var offset = -1;
    for (var index = 0; index <= bytes.length - oldValue.length; index++) {
      if (Arrays.equals(bytes, index, index + oldValue.length, oldValue, 0, oldValue.length)) {
        offset = index;
        break;
      }
    }
    assertTrue(offset >= 0, "The real payload must contain the PLUS operator record");
    var altered = new ByteArrayOutputStream();
    altered.write(bytes, 0, offset);
    altered.writeBytes(newValue);
    altered.write(bytes, offset + oldValue.length, bytes.length - offset - oldValue.length);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(altered.toByteArray(), original.identity()));

    // Assert
    assertTrue(failure.getMessage().contains("operator contradicts"), failure.toString());
    assertArrayEquals(bytes, AstCodec.encode(original));
  }

  private static byte[] operatorPayload(String operator) {
    var writer = new CanonicalValueWriter("syntax-value/operator");
    writer.count(1);
    writer.text("operator");
    writer.text("syntax-operator");
    writer.text(operator);
    return writer.toByteArray();
  }

  @Test
  void givenWrongAggregateAndBodyCollectionMembers_whenDecoding_thenContractRejectionRetainsTheTypeFailure() throws IOException {
    // Arrange
    var original = fixture();
    var aggregateFields = List.of("sources", "bodies", "memberOrders", "associations", "declarationTypes");
    var bodyFields = List.of("uses", "invocations", "staticAccesses", "concatenations", "constructors", "attributedOccurrences");

    // Act and assert
    assertAll(aggregateFields.stream().<Executable>map(field -> () -> assertMalformed(original, "aggregate", field)));
    assertAll(bodyFields.stream().<Executable>map(field -> () -> assertMalformed(original, "body", field)));
    assertArrayEquals(AstCodec.encode(original), payload(original, "aggregate", ""));
    assertArrayEquals(AstCodec.encode(original), payload(original, "body", ""));
    assertEquals(original.aggregate(), AstCodec.decode(payload(original, "body", ""), original.identity()).aggregate());
  }

  @Test
  void givenWrongCoverageCollectionMembers_whenDecoding_thenContractRejectionRetainsTheTypeFailure() throws IOException {
    // Arrange
    var original = fixture();

    // Act and assert
    assertAll(List.of("outcomes", "unmatchedFacts", "diagnosticEmissions").stream()
        .<Executable>map(field -> () -> assertMalformed(original, "coverage", field)));
    assertArrayEquals(AstCodec.encode(original), payload(original, "coverage", ""));
    assertEquals(original.coverage(), AstCodec.decode(payload(original, "coverage", ""), original.identity()).coverage());
  }

  @Test
  void givenWrongNestedSetAndOptionalMembers_whenDecoding_thenContractRejectionRetainsTheTypeFailure() throws IOException {
    // Arrange
    var original = fixture();

    // Act and assert
    assertAll(List.of("modifiers", "enclosingType").stream()
        .<Executable>map(field -> () -> assertMalformed(original, "type", field)));
    assertArrayEquals(AstCodec.encode(original), payload(original, "type", ""));
    assertEquals(original.program(), AstCodec.decode(payload(original, "type", ""), original.identity()).program());
  }

  private static void assertMalformed(AstResult original, String owner, String field) {
    var bytes = payload(original, owner, field);
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(bytes, original.identity()), owner + "/" + field);
    Throwable cause = failure.getCause();
    while (cause instanceof AstContractException && cause.getCause() != null) {
      cause = cause.getCause();
    }
    assertInstanceOf(ClassCastException.class, cause, owner + "/" + field);
  }

  private AstResult fixture() throws IOException {
    return fixture(AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private AstResult fixture(AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), "class Stored { int value(int x) { return x + 1; } }")));
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        profile);
    assertEquals(AstCompletion.COMPLETE, outcome.completion());
    return outcome.result().orElseThrow();
  }

  private static byte[] payload(AstResult original, String owner, String changedField) {
    var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
    writer.text(original.profile().version());
    writer.text(original.identity().semanticProductionRevision());
    writer.text(original.completion().name());
    var codec = new RetainedValueCodec(false);
    codec.write(writer, original.identity());
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    var shape = AstValueSchema.type(SyntaxAnalysisInput.class);
    var components = shape.components(input);
    writer.text(shape.tag());
    writer.count(shape.fields().size());
    for (var index = 0; index < shape.fields().size(); index++) {
      var field = shape.fields().get(index);
      writer.text(field);
      if ("program".equals(field) && "method".equals(owner) && !changedField.isEmpty()) {
        codec.write(writer, changedMethodProgram(original.program(), changedField));
      } else if ("program".equals(field) && "type".equals(owner)) {
        writer.text("java-program");
        writer.count(1);
        writer.text("types");
        writer.text("list");
        writer.count(original.program().types().size());
        for (var type : original.program().types()) {
          writeRecord(writer, codec, type, changedField);
        }
      } else if ("aggregate".equals(field) && ("aggregate".equals(owner) || "body".equals(owner))) {
        writer.text("optional");
        writer.flag(true);
        writer.text("source-aggregate");
        writeAggregate(writer, codec, CopiedAstValues.capture(original.aggregate()), owner, changedField);
      } else if ("languageFeatureCoverageResult".equals(field) && "coverage".equals(owner)) {
        writer.text("source-coverage-result");
        codec.write(writer, original.coverage().coverage());
        writeRecord(writer, codec, original.coverage().validationEvidence(), changedField);
      } else {
        codec.write(writer, components.get(index));
      }
    }
    return writer.toByteArray();
  }

  private static JavaProgram changedMethodProgram(JavaProgram program, String field) {
    return new JavaProgram(program.types().stream().map(type -> new JavaType(type.typeKey(), type.qualifiedName(), type.kind(),
        type.modifiers(), type.enclosingType(), type.directSuperclass(), type.directInterfaces(), type.permittedSubclasses(),
        type.typeParameters(), type.sourceDeclared(), type.fields(), type.recordComponents(), type.methods().stream().map(method -> {
          if (!method.name().equals("value")) {
            return method;
          }
          return new JavaMethod(method.methodKey(), method.ownerTypeKey(), method.name(),
              field.equals("signature") ? "value(long)" : method.signature(), field.equals("returnType") ? "long" : method.returnType(),
              method.parameters(), method.modifiers(), method.constructor(), method.sourceDeclared(), method.hasBody(), method.origins());
        }).toList(), type.synthesizedDeclarations(), type.origins())).toList());
  }

  private static void writeAggregate(CanonicalValueWriter writer, RetainedValueCodec codec, CopiedAstValues values, String owner,
      String changedField) {
    if ("aggregate".equals(owner)) {
      writeRecord(writer, codec, values, changedField);
      return;
    }
    var shape = AstValueSchema.type(CopiedAstValues.class);
    var components = shape.components(values);
    writer.text(shape.tag());
    writer.count(shape.fields().size());
    for (var index = 0; index < shape.fields().size(); index++) {
      var field = shape.fields().get(index);
      writer.text(field);
      if ("bodies".equals(field)) {
        writer.text("list");
        writer.count(values.bodies().size());
        for (var body : values.bodies()) {
          writeRecord(writer, codec, body, changedField);
        }
      } else {
        codec.write(writer, components.get(index));
      }
    }
  }

  private static void writeRecord(CanonicalValueWriter writer, RetainedValueCodec codec, Object value, String changedField) {
    var shape = AstValueSchema.type(value.getClass());
    var components = shape.components(value);
    writer.text(shape.tag());
    writer.count(shape.fields().size());
    for (var index = 0; index < shape.fields().size(); index++) {
      var field = shape.fields().get(index);
      writer.text(field);
      var replacement = switch (changedField) {
        case "modifiers" -> Set.of("wrong retained member");
        case "enclosingType" -> Optional.of("wrong retained member");
        default -> List.of("wrong retained member");
      };
      codec.write(writer, field.equals(changedField) ? replacement : components.get(index));
    }
  }
}
