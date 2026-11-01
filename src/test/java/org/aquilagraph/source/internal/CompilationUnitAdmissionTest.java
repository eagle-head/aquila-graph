package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.AstProductionAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class CompilationUnitAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenARequiredCapturedUnit_whenItsCopiedRootIsMissing_thenAtomicAdmissionRejectsTheWholeResult() throws IOException {
    // Arrange
    var ast = produce(List.of(SourceFile.fromContent(Path.of("Empty.java"), ""),
        SourceFile.fromContent(Path.of("Other.java"), "package example;")), AstProfile.JAVA_SOURCE_UNITS);
    var values = SourceValueCapture.capture(ast.aggregate());
    var partial = new CopiedAstValues(values.snapshot(), values.sources().subList(0, 1), values.bodies(), values.memberOrders(),
        values.associations(), values.declarationTypes(), values.scopes());
    // Act
    var error = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(partial));
    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(2, AstValueAdmission.admit(values).roots().size());
    assertTrue(error.getMessage().contains("source"), error.toString());
  }

  @Test
  void givenReversedUnitSemicolonRanges_whenCopiedWithTheSameRolePositions_thenWrittenOrderIsRejected() throws IOException {
    // Arrange
    var ast = produce(List.of(SourceFile.fromContent(Path.of("Empty.java"), "; ;")), AstProfile.JAVA_SOURCE_UNITS);
    var root = ast.aggregate().roots().getFirst();
    var first = root.children().getFirst();
    var second = root.children().getLast();
    var reversedFirst = SyntaxNode.source(first.node().sourceFileKey(), first.node().structuralPath(), first.node().sourceKind(),
        first.node().syntaxKind(), first.node().type(), first.node().value(), first.node().children(), first.node().semanticReference(), second.node().origin().orElseThrow());
    var reversedSecond = SyntaxNode.source(second.node().sourceFileKey(), second.node().structuralPath(), second.node().sourceKind(),
        second.node().syntaxKind(), second.node().type(), second.node().value(), second.node().children(), second.node().semanticReference(), first.node().origin().orElseThrow());
    var children = List.of(new SyntaxChild(first.role(), first.position(), reversedFirst), new SyntaxChild(second.role(), second.position(), reversedSecond));
    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(root.sourceFileKey(), root.structuralPath(), root.sourceKind(),
        root.syntaxKind(), root.type(), root.value(), children, root.semanticReference(), root.origin().orElseThrow()));
    // Assert
    assertTrue(error.getMessage().contains("order"), error.toString());
  }

  @Test
  void givenAnEnvelopeWithATruncatedRange_whenCopied_thenTheCapturedFileContractRejectsIt() throws IOException {
    // Arrange
    var ast = produce(List.of(SourceFile.fromContent(Path.of("Comment.java"), "// comment\n")), AstProfile.JAVA_SOURCE_UNITS);
    var values = SourceValueCapture.capture(ast.aggregate());
    var root = values.sources().getFirst().root();
    var truncated = SyntaxNode.source(root.sourceFileKey(), root.structuralPath(), root.sourceKind(), root.syntaxKind(), root.type(),
        root.value(), root.children(), root.semanticReference(), new SourceSpan(root.sourceFileKey(), 0, 0));
    var source = new SyntaxSourceContribution(root.sourceFileKey(), truncated, List.of(truncated), List.of(), List.of());
    var changed = new CopiedAstValues(values.snapshot(), List.of(source), values.bodies(), values.memberOrders(), values.associations(),
        values.declarationTypes(), values.scopes());
    // Act
    var error = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(changed));
    // Assert
    assertTrue(error.getMessage().contains("envelope"), error.toString());
  }

  @Test
  void givenMissingCompilerPositions_whenTheOriginalUnitAssociationIsDropped_thenCapturedRootProvenanceStillRejects() throws IOException {
    // Arrange
    var ast = produce(List.of(SourceFile.fromContent(Path.of("Comment.java"), "// comment\n")), AstProfile.JAVA_SOURCE_UNITS);
    var values = SourceValueCapture.capture(ast.aggregate());
    var changed = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), List.of(),
        values.declarationTypes(), values.scopes());
    // Act
    var error = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(changed));
    // Assert
    assertTrue(ast.aggregate().writtenSourceAssociations().getFirst().origin().isEmpty());
    assertTrue(error.getMessage().contains("association"), error.toString());
  }

  @TestFactory
  Stream<DynamicTest> givenChangedUnitEmptyDeclarationOrdinals_whenCopied_thenTheRealUnitOwnerRejectsEachContradiction() {
    // Arrange
    var ordinals = List.of(List.of(1, 1), List.of(1, 0), List.of(0, 99));
    // Act
    var tests = ordinals.stream().map(order -> DynamicTest.dynamicTest("given" + order + "UnitOrdinals_whenCopied_thenRejected", () -> {
      var ast = produce(List.of(SourceFile.fromContent(Path.of("Empty.java"), "; ;")), AstProfile.JAVA_SOURCE_UNITS);
      var values = SourceValueCapture.capture(ast.aggregate());
      var associations = values.associations().stream().map(value -> value.ownerPath().isEmpty() ? value
          : new WrittenSourceAssociation(value.nodeKey(), value.sourceFileKey(), SourceOwnerPath.root()
              .child(SourceOwnerPath.Role.EMPTY_DECLARATION, order.get(value.ownerPath().lastStep().orElseThrow().ordinal())),
              value.sourceKind(), value.origin())).toList();
      var changed = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), associations,
          values.declarationTypes(), values.scopes());
      var error = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(changed));
      // Assert
      assertTrue(error.getMessage().contains("ordinal"), error.toString());
      assertEquals(ast.aggregate(), AstValueAdmission.admit(values));
    }));
    // Assert
    return tests;
  }

  @Test
  void givenTheUnitProfile_whenReadmittedAsAnOlderProfileOrFormat_thenHistoricalMeaningCannotBeReinterpreted() throws IOException {
    // Arrange
    var ast = produce(List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example {}")), AstProfile.JAVA_SOURCE_UNITS);
    var input = input(ast);
    // Act
    var profileError = assertThrows(AstContractException.class, () -> AstResultAdmission.admit(input, AstProfile.JAVA_SOURCE_DECLARATIONS, "javac-public/6"));
    var formatError = assertThrows(AstContractException.class, () -> AstResultAdmission.admit(SourceAnalysisFixture.historicalInput(input), ast.profile(), "javac-public/6", AstResult.DECLARATION_SOURCE_MODEL_VERSION));
    // Assert
    assertTrue(profileError.getMessage().contains("profile"), profileError.toString());
    assertTrue(formatError.getMessage().contains("java-source/18"), formatError.toString());
    assertEquals("javac-public/14", ast.identity().semanticProductionRevision());
    assertEquals("java-source/22", ast.identity().sourceModelVersion());
    assertEquals("java-source-expanded/6", ast.profile().version());
  }

  @TestFactory
  Stream<DynamicTest> givenContradictoryOriginalAndSuccessorProfiles_whenUsingEitherPublicAdmission_thenTheInvalidValueCauseIsRetained() {
    // Arrange
    var cases = List.of(Map.entry(AstProfile.JAVA_SOURCE_DECLARATIONS, "class Example {}"),
        Map.entry(AstProfile.JAVA_SOURCE_UNITS, "class Example {}"), Map.entry(AstProfile.JAVA_SOURCE_UNITS, ""));
    // Act
    var tests = cases.stream().map(value -> DynamicTest.dynamicTest("given" + value.getKey().version() + "Values" + value.getValue() + "_whenContradicted_thenPublicAdmissionRejects", () -> {
      var profile = value.getKey();
      var ast = produce(List.of(SourceFile.fromContent(Path.of("Example.java"), value.getValue())), profile);
      var copied = input(ast);
      var contradictory = profile.equals(AstProfile.JAVA_SOURCE_UNITS) ? AstProfile.JAVA_SOURCE_DECLARATIONS : AstProfile.JAVA_SOURCE_UNITS;
      var admitted = assertThrows(AstContractException.class, () -> AstResultAdmission.admit(copied, contradictory, "javac-public/6"));
      var outcome = assertThrows(AstContractException.class, () -> AstProductionAdmission.outcome(copied, contradictory, "javac-public/6"));
      // Assert
      assertEquals(IllegalArgumentException.class, admitted.getCause().getClass());
      assertEquals(IllegalArgumentException.class, outcome.getCause().getClass());
      assertEquals(admitted.getCause().getMessage(), outcome.getCause().getMessage());
      assertTrue(outcome.getCause().getMessage().contains("profile"));
      assertEquals(ast.identity(), AstProductionAdmission.outcome(copied, profile, ast.identity().semanticProductionRevision()).result().orElseThrow().identity());
    }));
    // Assert
    return tests;
  }

  @Test
  void givenActualEmptyCoverageAndCapturedUnitValues_whenUsingZeroCaptureAdmission_thenOnlyMatchingEmptyCaptureIsUnavailable() throws IOException {
    // Arrange
    var empty = InputSnapshot.fromSources(directory, List.of());
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var produced = frontend.produce(empty, configuration(), AstProfile.JAVA_SOURCE_UNITS);
    var coverage = produced.coverageQualification().orElseThrow();
    var captured = produce(List.of(SourceFile.fromContent(Path.of("Empty.java"), "")), AstProfile.JAVA_SOURCE_UNITS);
    var foreignEmpty = InputSnapshot.fromSources(directory.resolve("foreign"), List.of());
    // Act
    var accepted = AstProductionAdmission.empty(empty, configuration(), AstProfile.JAVA_SOURCE_UNITS, coverage);
    var nonempty = assertThrows(AstContractException.class,
        () -> AstProductionAdmission.empty(captured.snapshot(), configuration(), AstProfile.JAVA_SOURCE_UNITS, captured.coverage()));
    var foreign = assertThrows(AstContractException.class,
        () -> AstProductionAdmission.empty(foreignEmpty, configuration(), AstProfile.JAVA_SOURCE_UNITS, coverage));
    // Assert
    assertEquals(AstCompletion.UNAVAILABLE, accepted.completion());
    assertTrue(accepted.result().isEmpty());
    assertEquals(produced.sourceSubjectDigest(), accepted.sourceSubjectDigest());
    assertEquals(IllegalArgumentException.class, nonempty.getCause().getClass());
    assertEquals(IllegalArgumentException.class, foreign.getCause().getClass());
    assertTrue(nonempty.getCause().getMessage().contains("empty capture"));
    assertTrue(foreign.getCause().getMessage().contains("coverage"));
  }

  @Test
  void givenAnUnrecognizedProfile_whenOfferedToEveryPublicResultAdmission_thenTheOriginalPolicyRejectionIsRetained() throws IOException {
    // Arrange
    var ast = produce(List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example {}")), AstProfile.JAVA_SOURCE_UNITS);
    var empty = InputSnapshot.fromSources(directory, List.of());
    var unavailable = new JavaFrontend(new JavaSourceLoader()).produce(empty, configuration(), AstProfile.JAVA_SOURCE_UNITS);
    var unknown = new AstProfile("java-source-expanded/unknown");
    // Act
    var admitted = assertThrows(AstContractException.class, () -> AstResultAdmission.admit(input(ast), unknown, "javac-public/6"));
    var outcome = assertThrows(AstContractException.class, () -> AstProductionAdmission.outcome(input(ast), unknown, "javac-public/6"));
    var zeroCapture = assertThrows(AstContractException.class,
        () -> AstProductionAdmission.empty(empty, configuration(), unknown, unavailable.coverageQualification().orElseThrow()));
    // Assert
    assertEquals(IllegalArgumentException.class, admitted.getCause().getClass());
    assertEquals(IllegalArgumentException.class, outcome.getCause().getClass());
    assertEquals(IllegalArgumentException.class, zeroCapture.getCause().getClass());
    assertEquals(admitted.getCause().getMessage(), outcome.getCause().getMessage());
    assertTrue(outcome.getCause().getMessage().contains(unknown.version()));
    assertTrue(zeroCapture.getCause().getMessage().contains("supported profile"));
  }

  @Test
  void givenSuppliedDeclarationValuesInThePrecedingFormat_whenClosed_thenIdentityBytesAndMaterializationRemainCompatible() throws IOException {
    // Arrange
    var ast = produce(List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { ; int value() { return 1; } }")), AstProfile.JAVA_SOURCE_DECLARATIONS);
    var historical = AstResultAdmission.admit(SourceAnalysisFixture.historicalInput(input(ast)), ast.profile(), "javac-public/5", AstResult.DECLARATION_SOURCE_MODEL_VERSION);
    var bytes = AstCodec.encode(historical);
    // Act
    var decoded = AstCodec.decode(bytes, historical.identity());
    var projection = AstCodec.materialize(decoded);
    // Assert
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(historical.identity(), decoded.identity());
    assertEquals(ast.aggregate(), decoded.aggregate());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    assertEquals(AstCodec.materialize(historical), projection);
    assertTrue(projection.nodes().stream().noneMatch(node -> node.tag().equals("syntax-value/compilation-unit-range")));
  }

  @TestFactory
  Stream<DynamicTest> givenIndependentOriginalUnitFrames_whenDecoded_thenTheOriginalRangePolicyAndBytesRemain() {
    // Arrange
    var format = AstResult.DECLARATION_SOURCE_MODEL_VERSION;
    var forms = List.of(SyntaxValue.CompilationUnitForm.ORDINARY, SyntaxValue.CompilationUnitForm.COMPACT);
    // Act
    var tests = forms.stream().map(form -> DynamicTest.dynamicTest("givenOriginal" + form + "Frame_whenDecoded_thenPolicyAndBytesRemain", () -> {
      var original = new CanonicalValueWriter(format);
      original.text("syntax-value/compilation-unit");
      original.count(1);
      original.text("form");
      original.text("syntax-value/compilation-unit-form");
      original.text(form.name());
      var reader = new CanonicalValueReader(original.toByteArray(), format);
      var retained = new RetainedValueCodec(false, format);
      var copied = new CanonicalValueWriter(format);
      var decoded = (SyntaxValue.CompilationUnit) retained.read(reader);
      retained.write(copied, decoded);
      // Assert
      reader.requireFinished();
      assertEquals(form, decoded.form());
      assertEquals(SyntaxValue.CompilationUnitRange.LEGACY_PROFILE, decoded.range());
      assertArrayEquals(original.toByteArray(), copied.toByteArray());
    }));
    // Assert
    return tests;
  }

  @Test
  void givenARealOriginalCompactUnit_whenClosedInThePrecedingFormat_thenItsRangePolicyDoesNotClaimCompilerPositions() throws IOException {
    // Arrange
    var source = SourceFile.fromContent(Path.of("Compact.java"), "void main() {}");
    var original = produce(List.of(source), AstProfile.JAVA_SOURCE_DECLARATIONS);
    var historical = AstResultAdmission.admit(SourceAnalysisFixture.historicalInput(input(original)), original.profile(), "javac-public/5", AstResult.DECLARATION_SOURCE_MODEL_VERSION);
    var bytes = AstCodec.encode(historical);
    // Act
    var decoded = AstCodec.decode(bytes, historical.identity());
    var current = produce(List.of(source), AstProfile.JAVA_SOURCE_UNITS);
    // Assert
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(historical.identity(), decoded.identity());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    assertEquals(AstCodec.materialize(historical), AstCodec.materialize(decoded));
    assertEquals(original.aggregate(), decoded.aggregate());
    var root = decoded.aggregate().roots().getFirst();
    assertEquals(SyntaxValue.CompilationUnitForm.COMPACT, root.value(SyntaxValue.CompilationUnit.class).form());
    assertEquals(SyntaxValue.CompilationUnitRange.LEGACY_PROFILE, root.value(SyntaxValue.CompilationUnit.class).range());
    assertEquals(new SourceSpan(source.sourceFileKey(), 0, source.content().length()), root.origin().orElseThrow());
    assertTrue(decoded.aggregate().writtenSourceAssociation(root.nodeId()).orElseThrow().origin().isEmpty());
    var currentRoot = current.aggregate().roots().getFirst();
    assertEquals(AstCompletion.COMPLETE, current.completion());
    assertEquals(SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE, currentRoot.value(SyntaxValue.CompilationUnit.class).range());
    assertEquals(root.origin(), currentRoot.origin());
    assertTrue(current.aggregate().writtenSourceAssociation(currentRoot.nodeId()).orElseThrow().origin().isEmpty());
  }

  @Test
  void givenEqualContentAtDifferentRootsAndChangedContentAtTheSamePath_whenProduced_thenOnlySpecifiedProvenanceIsExcluded() throws IOException {
    // Arrange
    var file = SourceFile.fromContent(Path.of("Empty.java"), "");
    var first = InputSnapshot.fromSources(directory.resolve("first"), List.of(file));
    var relocated = InputSnapshot.fromSources(directory.resolve("second"), List.of(file));
    var changed = InputSnapshot.fromSources(first.sourceRoot(), List.of(SourceFile.fromContent(Path.of("Empty.java"), "// changed\n")));
    var frontend = new JavaFrontend(new JavaSourceLoader());
    // Act
    var left = frontend.produce(first, configuration(), AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
    var right = frontend.produce(relocated, configuration(), AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
    var different = frontend.produce(changed, configuration(), AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(right), left.identity());
    // Assert
    assertEquals(left.identity(), right.identity());
    assertNotEquals(left.identity(), different.identity());
    assertEquals(relocated.sourceRoot(), decoded.snapshot().sourceRoot());
    assertEquals(0, left.aggregate().roots().getFirst().origin().orElseThrow().length());
    assertTrue(AstCodec.materialize(left).nodes().stream().anyMatch(node -> node.tag().equals("syntax-value/compilation-unit-range")));
    assertThrows(AstContractException.class, () -> AstCodec.decode(AstCodec.encode(left), different.identity()));
  }

  private AstResult produce(List<SourceFile> sources, AstProfile profile) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory, sources), configuration(), profile).result().orElseThrow();
  }

  private static SyntaxAnalysisInput input(AstResult ast) {
    return new SyntaxAnalysisInput(ast.snapshot(), ast.environment(), ast.program(), Optional.of(ast.aggregate()), ast.diagnostics(), ast.coverage());
  }

  private static CompilerConfiguration configuration() {
    return CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  }
}
