package org.aquilagraph.source.admission;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CapturedValueEncoding;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.CopiedAstValues.Body;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class SourceBodyOriginAdmissionTest {
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED);
  private static final List<MethodBodyOrigin> NONWRITTEN_ORIGINS = List.of(MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY,
      MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION);
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenForeignBodyWithoutMemberOrderEvidence_whenAdmittingJointValues_thenSourceOwnerCannotBeBypassed() {
    return PROFILES.stream().map(profile -> DynamicTest.dynamicTest(profile.version(), () -> {
      // Arrange
      var original = twoFileFixture(profile);
      var foreign = foreignBody(original);
      var values = new CopiedAstValues(foreign.snapshot(), foreign.sources(), foreign.bodies(), List.of(), foreign.associations(),
          foreign.declarationTypes());
      var aggregate = AstValueAdmission.admit(values);

      // Act
      var failure = assertThrows(IllegalArgumentException.class,
          () -> new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
              original.diagnostics(), original.coverage()));

      // Assert
      assertTrue(failure.getMessage().contains("body source"), failure.toString());
      assertEquals(AstCompletion.COMPLETE, original.completion());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenAuxiliaryBodyFromAnotherCapturedFile_whenAdmittingSource_thenCanonicalSourceOwnershipIsRejected() {
    return PROFILES.stream().map(profile -> DynamicTest.dynamicTest(profile.version(), () -> {
      // Arrange
      var original = twoFileFixture(profile);
      var values = foreignBody(original);

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> {
        var aggregate = AstValueAdmission.admit(values);
        var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
            original.diagnostics(), original.coverage());
        AstResultAdmission.admit(input, profile, original.identity().semanticProductionRevision());
      });

      // Assert
      assertTrue(failure.getMessage().contains("body source"), failure.toString());
      assertEquals(AstCompletion.COMPLETE, original.completion());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenSelfConsistentStoredAuxiliaryBodyFromAnotherCapturedFile_whenDecoding_thenCanonicalSourceOwnershipIsRejected() {
    return PROFILES.stream().map(profile -> DynamicTest.dynamicTest(profile.version(), () -> {
      // Arrange
      var original = twoFileFixture(profile);
      var unchanged = stored(original, CopiedAstValues.capture(original.aggregate()), AstCompletion.COMPLETE);
      var changed = stored(original, foreignBody(original), AstCompletion.COMPLETE);
      assertEquals(original.identity(), unchanged.identity());
      assertArrayEquals(AstCodec.encode(original), unchanged.bytes());

      // Act
      var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(changed.bytes(), changed.identity()));
      var decoded = AstCodec.decode(unchanged.bytes(), unchanged.identity());

      // Assert
      assertTrue(failure.getMessage().contains("body source"), failure.toString());
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
      assertEquals(original.coverage(), decoded.coverage());
      assertArrayEquals(unchanged.bytes(), AstCodec.encode(decoded));
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenWrittenMethodOrConstructorBody_whenReclassifiedWithAnIndependentCopy_thenSourceAdmissionRejectsProvenance() {
    return PROFILES.stream().flatMap(profile -> Stream.of(false, true).flatMap(constructor -> NONWRITTEN_ORIGINS.stream()
        .map(origin -> DynamicTest.dynamicTest(profile.version() + ": constructor=" + constructor + ", " + origin,
            () -> assertAdmissionRejects(profile, constructor, origin)))));
  }

  private void assertAdmissionRejects(AstProfile profile, boolean constructor, MethodBodyOrigin origin) throws IOException {
    // Arrange
    var original = fixture(profile, constructor);
    var methodKey = writtenMethodKey(original, constructor);
    var values = copiedBody(original, methodKey, origin, true);
    var originalBody = original.body(methodKey).orElseThrow().root();
    var copy = values.bodies().stream().filter(body -> body.methodKey().equals(methodKey)).findFirst().orElseThrow().root();
    assertEquals(originalBody, copy);
    assertNotSame(originalBody, copy);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(values));

    // Assert
    assertTrue(failure.getMessage().contains("written body"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(Optional.of(originalBody.nodeId()), original.bodyRef(methodKey).orElseThrow().sourceOccurrenceKey());
  }

  @TestFactory
  Stream<DynamicTest> givenSelfConsistentStoredWrittenBodyReclassification_whenDecoding_thenIntrinsicAdmissionRejectsProvenance() {
    return PROFILES.stream().flatMap(profile -> Stream.of(false, true).flatMap(constructor -> NONWRITTEN_ORIGINS.stream()
        .map(origin -> DynamicTest.dynamicTest(profile.version() + ": constructor=" + constructor + ", " + origin,
            () -> assertCodecRejects(profile, constructor, origin)))));
  }

  private void assertCodecRejects(AstProfile profile, boolean constructor, MethodBodyOrigin origin) throws IOException {
    // Arrange
    var original = fixture(profile, constructor);
    var unchanged = stored(original, CopiedAstValues.capture(original.aggregate()), AstCompletion.COMPLETE);
    var changed = stored(original, copiedBody(original, writtenMethodKey(original, constructor), origin, true),
        origin == MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION ? AstCompletion.INCOMPLETE : AstCompletion.COMPLETE);
    assertEquals(original.identity(), unchanged.identity());
    assertArrayEquals(AstCodec.encode(original), unchanged.bytes());

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(changed.bytes(), changed.identity()));
    var decoded = AstCodec.decode(unchanged.bytes(), unchanged.identity());

    // Assert
    assertTrue(failure.getMessage().contains("written body"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(original.coverage(), decoded.coverage());
    assertEquals(original.diagnostics(), decoded.diagnostics());
    assertArrayEquals(unchanged.bytes(), AstCodec.encode(decoded));
  }

  @TestFactory
  Stream<DynamicTest> givenWrittenAndSynthesizedBodies_whenClosingSourceValues_thenEachRetainsItsActualProvenance() {
    return PROFILES.stream().map(profile -> DynamicTest.dynamicTest(profile.version(), () -> {
      // Arrange
      var original = fixture(profile, false);
      var writtenKey = writtenMethodKey(original, false);
      var synthesizedKey = original.program().methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow().methodKey();

      // Act
      var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
      var written = decoded.body(writtenKey).orElseThrow().root();
      var declaration = decoded.aggregate().nodes().stream()
          .filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration value && value.declarationKey().equals(writtenKey))
          .findFirst().orElseThrow();

      // Assert
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
      assertSame(declaration.requiredChild(SyntaxRole.BODY), written);
      assertEquals(Optional.of(MethodBodyOrigin.SOURCE_AST), decoded.aggregate().methodBodyOrigin(writtenKey));
      assertEquals(Optional.of(written.nodeId()), decoded.bodyRef(writtenKey).orElseThrow().sourceOccurrenceKey());
      assertEquals(Optional.of(MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY), decoded.aggregate().methodBodyOrigin(synthesizedKey));
      assertTrue(decoded.bodyRef(synthesizedKey).orElseThrow().nonwrittenProvenance().isPresent());
      assertTrue(decoded.occurrence(decoded.body(synthesizedKey).orElseThrow().root()).isEmpty());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenNonpublishedBodyWithUnavailablePosition_whenAdmittedAndDecoded_thenIncompleteSourceHasNoFabricatedBodyReference() {
    return PROFILES.stream().map(profile -> DynamicTest.dynamicTest(profile.version(), () -> {
      // Arrange
      var original = fixture(profile, false);
      var synthesizedKey = original.program().methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow().methodKey();
      var values = copiedBody(original, synthesizedKey, MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION, false);

      // Act
      var aggregate = AstValueAdmission.admit(values);
      var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
          original.diagnostics(), original.coverage());
      var admitted = AstResultAdmission.admit(input, profile, original.identity().semanticProductionRevision());
      var decoded = AstCodec.decode(AstCodec.encode(admitted), admitted.identity());

      // Assert
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertEquals(Optional.of(MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION), decoded.aggregate().methodBodyOrigin(synthesizedKey));
      assertTrue(decoded.body(synthesizedKey).orElseThrow().root().origin().isEmpty());
      assertTrue(decoded.bodyRef(synthesizedKey).isEmpty());
      assertEquals(original.coverage().validationEvidence().writtenInventory(), decoded.coverage().validationEvidence().writtenInventory());
      assertEquals(original.coverage().validationEvidence().attributedExpectations(),
          decoded.coverage().validationEvidence().attributedExpectations());
    }));
  }

  private AstResult fixture(AstProfile profile, boolean explicitConstructor) throws IOException {
    var source = "class BodyOrigins { " + (explicitConstructor ? "BodyOrigins() { return; } " : "")
        + "void subject() { return; } }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("BodyOrigins.java"), source)));
    var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, result.completion());
    return result;
  }

  private AstResult twoFileFixture(AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("First.java"), "class First {}"),
        SourceFile.fromContent(Path.of("Second.java"), "class Second { void subject() { return; } }")));
    var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, result.completion());
    return result;
  }

  private static CopiedAstValues foreignBody(AstResult original) {
    var first = original.program().types().stream().filter(type -> type.qualifiedName().equals("First")).findFirst().orElseThrow();
    var methodKey = first.methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow().methodKey();
    var otherKey = original.program().methods().stream().filter(method -> method.name().equals("subject")).findFirst().orElseThrow().methodKey();
    var otherRoot = original.body(otherKey).orElseThrow().root();
    var values = CopiedAstValues.capture(original.aggregate());
    var bodies = values.bodies().stream().map(body -> body.methodKey().equals(methodKey)
        ? new Body(methodKey, copy(otherRoot, true), body.origin(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
        : body).toList();
    return new CopiedAstValues(values.snapshot(), values.sources(), bodies, values.memberOrders(), values.associations(), values.declarationTypes());
  }

  private static String writtenMethodKey(AstResult original, boolean constructor) {
    return original.program().methods().stream().filter(method -> constructor ? method.constructor() : method.name().equals("subject"))
        .findFirst().orElseThrow().methodKey();
  }

  private static CopiedAstValues copiedBody(AstResult original, String methodKey, MethodBodyOrigin origin, boolean preserveOrigins) {
    var values = CopiedAstValues.capture(original.aggregate());
    var bodies = values.bodies().stream().map(body -> body.methodKey().equals(methodKey)
        ? new Body(methodKey, copy(body.root(), preserveOrigins), origin, body.uses(), body.invocations(), body.staticAccesses(),
            body.concatenations(), body.constructors(), body.attributedOccurrences())
        : body).toList();
    return new CopiedAstValues(values.snapshot(), values.sources(), bodies, values.memberOrders(), values.associations(), values.declarationTypes());
  }

  private static SyntaxNode copy(SyntaxNode node, boolean preserveOrigins) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), copy(child.node(), preserveOrigins))).toList();
    return preserveOrigins && node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children,
            node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children,
            node.semanticReference());
  }

  private static StoredSourceValue stored(AstResult original, CopiedAstValues values, AstCompletion completion) {
    var canonical = new CanonicalValueWriter("canonical-java-source/1");
    canonical.text(AstResult.SOURCE_MODEL_VERSION);
    canonical.text(original.profile().version());
    canonical.text(original.identity().semanticProductionRevision());
    canonical.bytes(CapturedValueEncoding.encode(Optional.of(original.snapshot()), Optional.of(original.environment())));
    writeInput(canonical, new RetainedValueCodec(true), original, values);
    var identity = AstIdentity.fromContent(SoftwareVersion.from(original.snapshot(), original.environment()).signature(),
        AstResult.SOURCE_MODEL_VERSION, original.profile().version(), original.identity().semanticProductionRevision(), canonical.digest("ast-content"));
    var full = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
    full.text(original.profile().version());
    full.text(original.identity().semanticProductionRevision());
    full.text(completion.name());
    var codec = new RetainedValueCodec(false);
    codec.write(full, identity);
    writeInput(full, codec, original, values);
    return new StoredSourceValue(identity, full.toByteArray());
  }

  private static void writeInput(CanonicalValueWriter writer, RetainedValueCodec codec, AstResult original, CopiedAstValues values) {
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
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
        codec.write(writer, values);
      } else {
        codec.write(writer, components.get(index));
      }
    }
  }

  private record StoredSourceValue(AstIdentity identity, byte[] bytes) {
  }
}
