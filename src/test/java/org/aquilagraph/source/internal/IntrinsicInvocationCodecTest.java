package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.InvocationSelection;
import org.aquilagraph.source.api.InvocationSemantics;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntrinsicInvocationCodecTest {
  @TempDir Path directory;

  @Test
  void givenArrayCloneSource_whenRetainedAndDecoded_thenIntrinsicMeaningTypesAndFixedApplicabilitySurviveCompilerClosure()
      throws IOException {
    // Arrange
    var original = produce("class Example { int[] copy(int[] values) { return values.clone(); } }");
    var bytes = AstCodec.encode(original);
    var expectedType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "int[]", "int[]"),
        new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.INT)));

    // Act
    var decoded = AstCodec.decode(bytes, original.identity());
    var clone = cloneCall(decoded);
    var execution = decoded.aggregate().methodBodyKeys().stream().map(key -> decoded.aggregate().methodSyntax(key).orElseThrow())
        .filter(body -> body.root().descendantsAndSelf().stream().anyMatch(node -> node.nodeId().equals(clone.nodeId())))
        .findFirst().orElseThrow();
    var applicability = execution.invocationArity(clone.nodeId()).orElseThrow();

    // Assert
    assertEquals("java-source/24", decoded.identity().sourceModelVersion());
    assertEquals("javac-public/15", decoded.identity().semanticProductionRevision());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(new InvocationSemantics(InvocationMode.VIRTUAL, new JavaTypeReference(expectedType.identity(), Optional.empty()),
        InvocationForm.INSTANCE_EXPRESSION, InvocationSelection.ARRAY_CLONE), clone.value(SyntaxValue.Invocation.class).semantics().orElseThrow());
    assertEquals(expectedType, clone.type());
    assertEquals(expectedType, clone.requiredChild(SyntaxRole.RECEIVER).type());
    assertTrue(clone.semanticReference().isEmpty());
    assertEquals(InvocationApplicabilityMode.FIXED, applicability.mode());
    assertEquals(0, applicability.formalCount());
    assertEquals(original.identity(), decoded.identity());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
  }

  @Test
  void givenIntrinsicSelection_whenEncoded_thenCanonicalFieldsMatchIndependentFramingAndNamedSelectionChangesIdentity() {
    // Arrange
    var type = new JavaTypeReference(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "int[]", "int[]"), Optional.empty());
    var intrinsic = new InvocationSemantics(InvocationMode.VIRTUAL, type, InvocationForm.INSTANCE_EXPRESSION, InvocationSelection.ARRAY_CLONE);
    var named = new InvocationSemantics(InvocationMode.VIRTUAL, type, InvocationForm.INSTANCE_EXPRESSION, InvocationSelection.NAMED_EXECUTABLE);
    var expected = expectedSemantics();
    var intrinsicWriter = new CanonicalValueWriter("source-value/1");
    var namedWriter = new CanonicalValueWriter("source-value/1");

    // Act
    new RetainedValueCodec(true).write(intrinsicWriter, intrinsic);
    new RetainedValueCodec(true).write(namedWriter, named);
    var decoded = new RetainedValueCodec(false).read(new CanonicalValueReader(expected, "source-value/1"), InvocationSemantics.class);

    // Assert
    assertArrayEquals(expected, intrinsicWriter.toByteArray());
    assertEquals(intrinsic, decoded);
    assertNotEquals(intrinsicWriter.digest("invocation"), namedWriter.digest("invocation"));
  }

  @Test
  void givenRetiredSourceFormat_whenDecoding_thenTheOldIdentityIsRefusedBeforeReadingPayload() throws IOException {
    // Arrange
    var current = produce("class Example { int[] copy(int[] values) { return values.clone(); } }");
    var identity = current.identity();
    var retired = AstIdentity.fromContent(identity.softwareSignature(), "java-source/23", identity.astProfileVersion(),
        identity.semanticProductionRevision(), identity.canonicalContentDigest());

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(new byte[0], retired));

    // Assert
    assertFalse(AstResult.supportsSourceModelVersion("java-source/23"));
    assertTrue(failure.getMessage().contains("Unsupported retained AST format: java-source/23"));
  }

  @Test
  void givenDifferentArrayTypesAndOccurrences_whenAnalyzed_thenTheContentIdentityChanges() throws IOException {
    // Arrange
    var integerArray = produce("class Example { int[] copy(int[] values) { return values.clone(); } }");
    var longArray = produce("class Example { long[] copy(long[] values) { return values.clone(); } }");
    var repeated = produce("class Example { int[] copy(int[] values) { values.clone(); return values.clone(); } }");

    // Act
    var first = AstCodec.decode(AstCodec.encode(integerArray), integerArray.identity());
    var changed = AstCodec.decode(AstCodec.encode(longArray), longArray.identity());
    var occurrences = AstCodec.decode(AstCodec.encode(repeated), repeated.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, first.completion());
    assertEquals(AstCompletion.COMPLETE, changed.completion());
    assertEquals(AstCompletion.COMPLETE, occurrences.completion());
    assertNotEquals(first.identity().canonicalContentDigest(), changed.identity().canonicalContentDigest());
    assertNotEquals(first.identity().canonicalContentDigest(), occurrences.identity().canonicalContentDigest());
    var clones = occurrences.aggregate().nodes().stream().filter(IntrinsicInvocationCodecTest::isClone).toList();
    assertEquals(2, clones.size());
    assertNotEquals(clones.getFirst().nodeId(), clones.getLast().nodeId());
  }

  private AstResult produce(String content) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), content)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
  }

  private static SyntaxNode cloneCall(AstResult result) {
    return result.aggregate().nodes().stream().filter(IntrinsicInvocationCodecTest::isClone).findFirst().orElseThrow();
  }

  private static boolean isClone(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.INVOCATION && node.value(SyntaxValue.Invocation.class).name().equals("clone");
  }

  private static byte[] expectedSemantics() {
    var writer = new CanonicalValueWriter("source-value/1");
    writer.text("invocation-semantics");
    writer.count(4);
    writer.text("mode");
    writer.text("invocation-mode");
    writer.text("VIRTUAL");
    writer.text("qualifyingType");
    writer.text("java-type-reference");
    writer.count(2);
    writer.text("identity");
    writer.text("java-type-identity");
    writer.count(3);
    writer.text("category");
    writer.text("java-type-category");
    writer.text("ARRAY");
    writer.text("displayName");
    writer.text("text");
    writer.text("int[]");
    writer.text("erasedName");
    writer.text("text");
    writer.text("int[]");
    writer.text("declarationKey");
    writer.text("optional/absent");
    writer.text("form");
    writer.text("invocation-form");
    writer.text("INSTANCE_EXPRESSION");
    writer.text("selection");
    writer.text("invocation-selection");
    writer.text("ARRAY_CLONE");
    return writer.toByteArray();
  }
}
