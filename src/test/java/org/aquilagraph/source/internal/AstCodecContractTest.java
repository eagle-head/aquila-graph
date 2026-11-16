package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.AstProductionAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.identity.api.SourceOccurrence;


import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.internal.AstValueSchema.Fields;
import org.aquilagraph.source.retention.AstValueProjection;




import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.aquilagraph.source.internal.RetainedSourceFixture.payloadWithAggregate;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.IntStream;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaCoverageTestSupport;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.testsupport.StatementBranchFixture;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoverageSourceFact;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.JavaExecutableErasure;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class AstCodecContractTest {
  @Test
  void givenRetainedAnnotationInInitializerBlock_whenReconstructedWithoutDirectSource_thenTheSharedCategoryRejectsPlacement() {
    for (var kind : List.of(SyntaxKind.BLOCK, SyntaxKind.STATIC_INITIALIZER_BLOCK, SyntaxKind.INSTANCE_INITIALIZER_BLOCK)) {
      // Arrange
      var path = SyntaxStructuralPath.sourceFileRoot();
      var leaf = SyntaxNode.withoutDirectSource("source", path.child(SyntaxRole.STATEMENT, 0), JavaSourceKind.EMPTY_STATEMENT,
          SyntaxKind.EMPTY, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty());
      var block = SyntaxNode.withoutDirectSource("source", path, JavaSourceKind.BLOCK, kind, SyntaxType.NotApplicable.INSTANCE,
          SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, leaf)), Optional.empty());
      var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
      new RetainedValueCodec(false).write(writer, block);
      var malformed = StatementBranchFixture.withDeclaration(writer.toByteArray(), AstCodec.FORMAT_VERSION, leaf,
          JavaSourceKind.ANNOTATION_TYPE, false);
      // Act
      var failure = assertThrows(IllegalArgumentException.class,
          () -> new RetainedValueCodec(false).read(new CanonicalValueReader(malformed, AstCodec.FORMAT_VERSION)));
      // Assert
      assertTrue(deepestCause(failure).getMessage().contains("child category"), failure.toString());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenBoundedAndExpandedBranches_whenDeclarationsReplaceLeafBodies_thenReconstructionRejectsTheCategory() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_MODULE_SERVICES).flatMap(profile -> Stream.of(SyntaxRole.THEN_BRANCH, SyntaxRole.ELSE_BRANCH)
        .map(role -> DynamicTest.dynamicTest("given" + profile.version() + role
            + "_whenLeafIsReplacedByDeclaration_thenReconstructionRejectsCategory", () -> {
          // Arrange
          var original = StatementBranchFixture.produce(directory, profile).result().orElseThrow();
          var bytes = AstCodec.encode(original);
          var leaf = StatementBranchFixture.branch(AstOutcome.available(original), role);
          // Act
          var decoded = AstCodec.decode(bytes, original.identity());
          // Assert
          assertEquals(original.identity(), decoded.identity());
          assertEquals(original.completion(), decoded.completion());
          assertEquals(original.aggregate(), decoded.aggregate());
          assertArrayEquals(bytes, AstCodec.encode(decoded));
          for (var kind : StatementBranchFixture.DECLARATIONS) {
            for (var unsupported : List.of(false, true)) {
              // Arrange
              var malformed = StatementBranchFixture.withDeclaration(bytes, original.identity().sourceModelVersion(), leaf, kind, unsupported);
              // Act
              var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed, original.identity()));
              // Assert
              assertTrue(deepestCause(failure).getMessage().contains("child category"), kind + " " + unsupported + " " + failure);
              assertInstanceOf(IllegalArgumentException.class, deepestCause(failure));
            }
          }
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenVoidAtAnyArrayDepth_whenCopiedOrDecoded_thenValueComponentsRejectWhileDirectVoidResultsRemainLegal() {
    return IntStream.rangeClosed(1, 2).boxed().flatMap(depth -> Stream.of("parameter", "return").map(part -> DynamicTest.dynamicTest(depth + " " + part, () -> {
      // Arrange
      JavaTypeStructure component = new JavaTypeStructure.Primitive(JavaTypeCategory.INT);
      for (var index = 0; index < depth; index++) component = new JavaTypeStructure.Array(component);
      var array = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "int" + "[]".repeat(depth), "int" + "[]".repeat(depth)), component);
      var voidType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.VOID, "void", "void"), new JavaTypeStructure.Primitive(JavaTypeCategory.VOID));
      var erasure = new JavaExecutableErasure(part.equals("parameter") ? List.of(array) : List.of(), part.equals("return") ? array : voidType);
      var signature = new JavaTypeStructure.Executable(List.of(), part.equals("parameter") ? List.of(SyntaxType.Unresolved.INSTANCE) : List.of(),
          part.equals("return") ? SyntaxType.Unresolved.INSTANCE : voidType, Optional.empty(), List.of(), erasure);
      var codec = new RetainedValueCodec(false);
      var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
      codec.write(writer, signature);
      var malformed = replaceValueFrame(writer.toByteArray(), new JavaTypeStructure.Primitive(JavaTypeCategory.INT), new JavaTypeStructure.Primitive(JavaTypeCategory.VOID));
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.VOID)));
      var decoding = assertThrows(IllegalArgumentException.class, () -> codec.read(new CanonicalValueReader(malformed, AstCodec.FORMAT_VERSION)));
      var matching = codec.read(new CanonicalValueReader(writer.toByteArray(), AstCodec.FORMAT_VERSION));
      // Assert
      assertEquals("Void cannot be a value-type component", copying.getMessage());
      assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
      assertEquals(signature, matching);
      assertTrue(new JavaExecutableErasure(List.of(), voidType).isComplete());
    })));
  }

  @TestFactory
  Stream<DynamicTest> givenUnattributedNominalArrayLeaves_whenSelectedDimensionsChange_thenPublicAndClosedBoundariesReject() {
    return Stream.of(new WrittenErasureCase("String[]", "String[][]"), new WrittenErasureCase("java.lang.String[][]", "java.lang.String[]"),
        new WrittenErasureCase("String @Mark []", "String @Mark [][]"))
        .flatMap(variant -> Stream.of("return", "constructor").flatMap(part -> Stream.of(false, true).map(identityOnly -> DynamicTest.dynamicTest(variant + " " + part + " identity-only=" + identityOnly, () -> {
          // Arrange
          var annotations = "@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Mark {} ";
          var original = referenceResult(annotations + arrayReferenceMember(variant.written(), part));
          var other = referenceResult(annotations + arrayReferenceMember(variant.contrasting(), part));
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          var removed = new HashSet<String>();
          var node = referenceNode(original);
          var written = part.equals("constructor") ? node.requiredChild(SyntaxRole.QUALIFIER) : selectedDeclaration(original).requiredChild(SyntaxRole.RETURN_TYPE);
          unavailableNominalTypes(written, replacements, removed);
          var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replaceNode(child.node(), replacements))).toList();
          replacements.put(node, copyNode(node, node.type(), partialArraySelection(node.value(SyntaxValue.MemberReference.class), part, identityOnly), children, node.semanticReference()));
          var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, removed)));
          var otherValue = referenceNode(other).value(SyntaxValue.MemberReference.class);
          var wrongSelected = partialArraySelection(otherValue, part, identityOnly).selectedType();
          var positiveNode = referenceNode(positive);
          var value = positiveNode.value(SyntaxValue.MemberReference.class);
          var wrong = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), wrongSelected, value.selection(), value.staticMember());
          var neutral = referenceCoverageValue(positive, positiveNode.nodeId());
          var otherReplacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          var otherNode = referenceNode(other);
          var otherPartial = partialArraySelection(otherValue, part, identityOnly);
          otherReplacements.put(otherNode, copyNode(otherNode, otherNode.type(), otherPartial, otherNode.children(), otherNode.semanticReference()));
          var otherPositive = reconciledResult(other, AstValueAdmission.admit(withReplacedNodes(other, otherReplacements, Set.of())));
          var selectedProjection = coverageField(referenceCoverageValue(otherPositive, referenceNode(otherPositive).nodeId()), "selectedType");
          var coverage = alteredReferenceCoverage(positive, positiveNode.nodeId(), positiveNode.semanticReference(), Optional.of(replacedCoverageField(neutral, "selectedType", selectedProjection)));
          var codec = new RetainedValueCodec(false);
          var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          codec.write(writer, positiveNode);
          var malformedNode = replaceNodeValueFrame(writer.toByteArray(), positiveNode, wrong);
          var values = SourceValueCapture.capture(positive.aggregate());
          var malformed = replaceNodeValueFrame(payloadWithAggregate(positive, values, coverage), positiveNode, wrong);
          // Act
          IllegalArgumentException copying;
          if (part.equals("constructor")) {
            copying = assertThrows(IllegalArgumentException.class, () -> copyNode(positiveNode, positiveNode.type(), wrong, positiveNode.children(), positiveNode.semanticReference()));
            var decoding = assertThrows(IllegalArgumentException.class, () -> codec.read(new CanonicalValueReader(malformedNode, AstCodec.FORMAT_VERSION)));
            assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
          } else {
            var changed = copyNode(positiveNode, positiveNode.type(), wrong, positiveNode.children(), positiveNode.semanticReference());
            copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(withReplacedNodes(positive,
                new IdentityHashMap<>(Map.of(positiveNode, changed)), Set.of())));
          }
          var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed, positive.identity()));
          var matching = AstCodec.decode(AstCodec.encode(positive), positive.identity());
          // Assert
          assertEquals(part.equals("constructor") ? "A selected type must agree with its known written components" : "A selected erasure must agree with its known written type form", copying.getMessage());
          assertEquals(copying.getMessage(), deepestCause(envelope).getMessage());
          assertEquals(AstCompletion.INCOMPLETE, matching.completion());
          assertEquals(positive.aggregate(), matching.aggregate());
          assertEquals(original.program(), positive.program());
          assertEquals(SyntaxType.Unresolved.INSTANCE, matching.aggregate().node(written.nodeId()).orElseThrow().type());
          assertTrue(removed.stream().allMatch(key -> matching.aggregate().node(key).orElseThrow().semanticReference().isEmpty()));
        }))));
  }

  private static SyntaxValue.MemberReference partialArraySelection(SyntaxValue.MemberReference value, String part, boolean identityOnly) {
    SyntaxType selectedType = value.selectedType();
    if (part.equals("constructor")) {
      if (identityOnly) selectedType = new SyntaxType.StructureUnavailable(selectedType.knownIdentity().orElseThrow());
    } else {
      var selected = (SyntaxType.Resolved) selectedType;
      var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
      var raw = signature.erasure().returnType();
      var partialRaw = identityOnly ? new SyntaxType.StructureUnavailable(raw.knownIdentity().orElseThrow()) : raw;
      var partial = new JavaTypeStructure.Executable(signature.variables(), signature.parameters(), SyntaxType.Unresolved.INSTANCE,
          signature.receiver(), signature.thrownTypes(), new JavaExecutableErasure(signature.erasure().parameters(), partialRaw));
      selectedType = new SyntaxType.Resolved(selected.identity(), partial);
    }
    return new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), selectedType, value.selection(), value.staticMember());
  }

  @TestFactory
  Stream<DynamicTest> givenOwnedVariableBoundsWithPartialHeaders_whenSelectedErasureContradictsTheLeftmostBound_thenPublicAndClosedAdmissionReject() {
    return Stream.of(
        new BoundErasureCase("static <T> T selected() { return null; } java.util.function.Supplier<Object> subject() { return Example::selected; }", "String", false),
        new BoundErasureCase("static <T extends Number> T selected() { return null; } java.util.function.Supplier<Number> subject() { return Example::selected; }", "String", false),
        new BoundErasureCase("static <U extends Number,T extends U> T selected() { return null; } java.util.function.Supplier<Number> subject() { return Example::selected; }", "String", false),
        new BoundErasureCase("static <T extends Number & Comparable<T>> T selected() { return null; } java.util.function.Supplier<Number> subject() { return Example::selected; }", "String", false),
        new BoundErasureCase("static <T extends Number> T[] selected() { return null; } java.util.function.Supplier<Number[]> subject() { return Example::selected; }", "String[]", false),
        new BoundErasureCase("static class Owner<T extends Number> { T selected() { return null; } } java.util.function.Supplier<Number> subject(Owner<Number> owner) { return owner::selected; }", "String", false),
        new BoundErasureCase("interface Ref { Number apply(Number input); } static <T extends Number> T selected(T input) { return input; } Ref subject() { return Example::selected; }", "String", true))
        .flatMap(variant -> Stream.of("selected-variable", "semantic-unresolved", "erasure-identity-only", "erasure-key-contradiction").map(state -> DynamicTest.dynamicTest(variant + " " + state, () -> {
          // Arrange
          var original = referenceResult(variant.members());
          var other = referenceResult("static " + variant.contrasting() + " selected() { return null; } java.util.function.Supplier<" + variant.contrasting() + "> subject() { return Example::selected; }");
          var node = referenceNode(original);
          var value = node.value(SyntaxValue.MemberReference.class);
          var selected = (SyntaxType.Resolved) value.selectedType();
          var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
          var written = variant.parameter() ? selectedDeclaration(original).children(SyntaxRole.PARAMETER).getFirst().requiredChild(SyntaxRole.DECLARED_TYPE)
              : selectedDeclaration(original).requiredChild(SyntaxRole.RETURN_TYPE);
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          unavailableWrittenTypes(written, replacements);
          var raw = variant.parameter() ? signature.erasure().parameters().getFirst() : signature.erasure().returnType();
          var partialRaw = state.equals("erasure-identity-only") ? new SyntaxType.StructureUnavailable(raw.knownIdentity().orElseThrow()) : raw;
          var originalSemantic = variant.parameter() ? signature.parameters().getFirst() : signature.returnType();
          var partialSemantic = state.equals("selected-variable") ? originalSemantic : SyntaxType.Unresolved.INSTANCE;
          var positiveSignature = new JavaTypeStructure.Executable(signature.variables(), variant.parameter() ? List.of(partialSemantic) : signature.parameters(),
              variant.parameter() ? signature.returnType() : partialSemantic, signature.receiver(), signature.thrownTypes(),
              new JavaExecutableErasure(variant.parameter() ? List.of(partialRaw) : signature.erasure().parameters(), variant.parameter() ? signature.erasure().returnType() : partialRaw));
          var positiveValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), new SyntaxType.Resolved(selected.identity(), positiveSignature), value.selection(), value.staticMember());
          replacements.put(node, copyNode(node, node.type(), positiveValue, node.children(), node.semanticReference()));
          var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
          var otherSignature = (JavaTypeStructure.Executable) ((SyntaxType.Resolved) referenceNode(other).value(SyntaxValue.MemberReference.class).selectedType()).structure().orElseThrow();
          SyntaxType wrongRaw = state.equals("erasure-identity-only") ? new SyntaxType.StructureUnavailable(otherSignature.erasure().returnType().knownIdentity().orElseThrow()) : otherSignature.erasure().returnType();
          if (state.equals("erasure-key-contradiction")) {
            wrongRaw = new SyntaxType.Resolved(raw.knownIdentity().orElseThrow(), ((SyntaxType.Resolved) wrongRaw).structure().orElseThrow());
          }
          var wrongSemantic = state.equals("selected-variable") ? new SyntaxType.Resolved(new JavaTypeIdentity(originalSemantic.knownIdentity().orElseThrow().category(),
              originalSemantic.knownIdentity().orElseThrow().displayName(), wrongRaw.knownIdentity().orElseThrow().erasedName()), ((SyntaxType.Resolved) originalSemantic).structure().orElseThrow()) : SyntaxType.Unresolved.INSTANCE;
          var wrongErasure = new JavaExecutableErasure(variant.parameter() ? List.of(wrongRaw) : signature.erasure().parameters(), variant.parameter() ? signature.erasure().returnType() : wrongRaw);
          var wrongSignature = new JavaTypeStructure.Executable(signature.variables(), variant.parameter() ? List.of(wrongSemantic) : signature.parameters(),
              variant.parameter() ? signature.returnType() : wrongSemantic, signature.receiver(), signature.thrownTypes(), wrongErasure);
          var wrongType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, selected.identity().displayName(), wrongErasure.signature()), wrongSignature);
          var wrongValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), wrongType, value.selection(), value.staticMember());
          var positiveNode = referenceNode(positive);
          var values = withReplacedNodes(positive, new IdentityHashMap<>(Map.of(positiveNode, copyNode(positiveNode, positiveNode.type(), wrongValue, positiveNode.children(), positiveNode.semanticReference()))), Set.of());
          var neutral = referenceCoverageValue(positive, positiveNode.nodeId());
          var selectedProjection = (CoverageEvidenceValue.Structure) coverageField(neutral, "selectedType");
          var executableProjection = (CoverageEvidenceValue.Structure) ((CoverageEvidenceValue.OptionalValue) coverageField(selectedProjection, "structure")).value().orElseThrow();
          var coverage = alteredReferenceCoverage(positive, positiveNode.nodeId(), positiveNode.semanticReference(), Optional.of(replacedCoverageField(neutral, "selectedType",
              boundErasureCoverage(selectedProjection, executableProjection, referenceCoverageValue(other, referenceNode(other).nodeId()), wrongType, wrongSignature, variant.parameter()))));
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(values));
          var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(payloadWithAggregate(positive, values, coverage), positive.identity()));
          var matching = AstCodec.decode(AstCodec.encode(positive), positive.identity());
          // Assert
          assertEquals("A selected erasure must match its available owned type-variable bounds", copying.getMessage());
          assertEquals(copying.getMessage(), deepestCause(envelope).getMessage());
          assertEquals(AstCompletion.INCOMPLETE, matching.completion());
          assertEquals(positive.aggregate(), matching.aggregate());
          assertEquals(written.semanticReference(), matching.aggregate().node(written.nodeId()).orElseThrow().semanticReference());
          assertEquals(original.program(), positive.program());
        })));
  }

  private record BoundErasureCase(String members, String contrasting, boolean parameter) {}

  private static CoverageEvidenceValue.Structure boundErasureCoverage(CoverageEvidenceValue.Structure selected,
      CoverageEvidenceValue.Structure executable, CoverageEvidenceValue.Structure otherReference, SyntaxType.Resolved wrongType,
      JavaTypeStructure.Executable wrongSignature, boolean parameter) {
    var otherSelected = (CoverageEvidenceValue.Structure) coverageField(otherReference, "selectedType");
    var otherExecutable = (CoverageEvidenceValue.Structure) ((CoverageEvidenceValue.OptionalValue) coverageField(otherSelected, "structure")).value().orElseThrow();
    var otherErasure = (CoverageEvidenceValue.Structure) coverageField(otherExecutable, "erasure");
    var wrongRaw = parameter ? wrongSignature.erasure().parameters().getFirst() : wrongSignature.erasure().returnType();
    var rawProjection = wrongRaw instanceof SyntaxType.StructureUnavailable
        ? new CoverageEvidenceValue.Structure(CoverageEvidenceValue.Tag.TYPE_STRUCTURE_UNAVAILABLE, List.of(new CoverageEvidenceValue.Field("identity", new CoverageEvidenceValue.TypeIdentity(wrongRaw.knownIdentity().orElseThrow()))))
        : replacedCoverageField((CoverageEvidenceValue.Structure) coverageField(otherErasure, "returnType"), "identity", new CoverageEvidenceValue.TypeIdentity(wrongRaw.knownIdentity().orElseThrow()));
    var originalErasure = (CoverageEvidenceValue.Structure) coverageField(executable, "erasure");
    var changedErasure = replacedCoverageField(originalErasure, parameter ? "parameters" : "returnType", parameter ? new CoverageEvidenceValue.Sequence(List.of(rawProjection)) : rawProjection);
    var semanticProjection = parameter ? ((CoverageEvidenceValue.Sequence) coverageField(executable, "parameters")).values().getFirst() : coverageField(executable, "returnType");
    var semantic = parameter ? wrongSignature.parameters().getFirst() : wrongSignature.returnType();
    if (semantic instanceof SyntaxType.Resolved resolved) {
      semanticProjection = replacedCoverageField((CoverageEvidenceValue.Structure) semanticProjection, "identity", new CoverageEvidenceValue.TypeIdentity(resolved.identity()));
    }
    var changedExecutable = replacedCoverageField(replacedCoverageField(executable, "erasure", changedErasure), parameter ? "parameters" : "returnType",
        parameter ? new CoverageEvidenceValue.Sequence(List.of(semanticProjection)) : semanticProjection);
    return replacedCoverageField(replacedCoverageField(selected, "identity", new CoverageEvidenceValue.TypeIdentity(wrongType.identity())), "structure",
        new CoverageEvidenceValue.OptionalValue(Optional.of(changedExecutable)));
  }

  private static String arrayReferenceMember(String type, String part) {
    var target = type.replace(" @Mark ", "");
    return part.equals("constructor") ? "java.util.function.IntFunction<" + target + "> subject() { return " + type + "::new; }"
        : "static " + type + " selected() { return null; } java.util.function.Supplier<" + target + "> subject() { return Example::selected; }";
  }

  private static SyntaxNode selectedDeclaration(AstResult result) {
    return result.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration declaration && declaration.name().equals("selected")).findFirst().orElseThrow();
  }

  private static SyntaxNode unavailableNominalTypes(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements, Set<String> removed) {
    if (node.syntaxKind() != SyntaxKind.TYPE_USE) return node;
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), unavailableNominalTypes(child.node(), replacements, removed))).toList();
    if (node.semanticReference().isPresent()) removed.add(node.nodeId());
    var result = copyNode(node, node.value() instanceof SyntaxValue.PackageQualifier ? node.type() : SyntaxType.Unresolved.INSTANCE, node.value(), children, Optional.empty());
    replacements.put(node, result);
    return result;
  }
  @TestFactory
  Stream<DynamicTest> givenUnavailableSemanticPeers_whenErasureContradictsItsOwnPrimitiveOrArrayStructure_thenPublicAndClosedValuesReject() {
    return Stream.of(new WrittenErasureCase("int", "long"), new WrittenErasureCase("int[]", "long[]"),
        new WrittenErasureCase("int[]", "int[][]"), new WrittenErasureCase("int[][]", "int[]"),
        new WrittenErasureCase("String[]", "String[][]"), new WrittenErasureCase("String[][]", "String[]"))
        .flatMap(variant -> Stream.of("parameter", "return").map(part -> DynamicTest.dynamicTest(variant + " " + part, () -> {
          // Arrange
          var original = referenceResult(referenceMember(variant.written(), variant.written()));
          var other = referenceResult(referenceMember(variant.contrasting(), variant.contrasting()));
          var signature = (JavaTypeStructure.Executable) ((SyntaxType.Resolved) referenceNode(original).value(SyntaxValue.MemberReference.class).selectedType()).structure().orElseThrow();
          var otherSignature = (JavaTypeStructure.Executable) ((SyntaxType.Resolved) referenceNode(other).value(SyntaxValue.MemberReference.class).selectedType()).structure().orElseThrow();
          var raw = (SyntaxType.Resolved) signature.erasure().returnType();
          var otherRaw = (SyntaxType.Resolved) otherSignature.erasure().returnType();
          var contradictory = raw.identity().category() == JavaTypeCategory.ARRAY
              ? new SyntaxType.Resolved(raw.identity(), otherRaw.structure().orElseThrow())
              : new SyntaxType.Resolved(new JavaTypeIdentity(raw.identity().category(), raw.identity().displayName(), otherRaw.identity().erasedName()), raw.structure().orElseThrow());
          var voidType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.VOID, "void", "void"), new JavaTypeStructure.Primitive(JavaTypeCategory.VOID));
          var erasure = new JavaExecutableErasure(part.equals("parameter") ? List.of(raw) : List.of(), part.equals("return") ? raw : voidType);
          var partial = new JavaTypeStructure.Executable(List.of(), part.equals("parameter") ? List.of(SyntaxType.Unresolved.INSTANCE) : List.of(),
              part.equals("return") ? SyntaxType.Unresolved.INSTANCE : voidType, Optional.empty(), List.of(), erasure);
          var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          var codec = new RetainedValueCodec(false);
          codec.write(writer, erasure);
          var malformed = replaceValueFrame(writer.toByteArray(), raw, contradictory);
          var partialWriter = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          codec.write(partialWriter, partial);
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> new JavaExecutableErasure(
              part.equals("parameter") ? List.of(contradictory) : List.of(), part.equals("return") ? contradictory : voidType));
          var decoding = assertThrows(IllegalArgumentException.class, () -> codec.read(new CanonicalValueReader(malformed, AstCodec.FORMAT_VERSION)));
          var matching = codec.read(new CanonicalValueReader(partialWriter.toByteArray(), AstCodec.FORMAT_VERSION));
          var unavailable = new JavaExecutableErasure(List.of(new SyntaxType.StructureUnavailable(raw.identity())), voidType);
          // Assert
          assertEquals("Executable erasure identity conflicts with its known recursive structure", copying.getMessage());
          assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
          assertEquals(partial, matching);
          assertEquals(raw.identity(), unavailable.parameters().getFirst().knownIdentity().orElseThrow());
          assertFalse(unavailable.isComplete());
        })));
  }

  @Test
  void givenAnnotatedPrimitiveArraySignature_whenErasedAndDecoded_thenBareErasureAndWrittenAnnotationsRemainDistinct() throws IOException {
    // Arrange
    var original = referenceResult("@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Mark {} interface Ref { int[] apply(int[] input); } static int @Mark [] selected(int @Mark [] input) { return input; } Ref subject() { return Example::selected; }");
    var signature = (JavaTypeStructure.Executable) ((SyntaxType.Resolved) referenceNode(original).value(SyntaxValue.MemberReference.class).selectedType()).structure().orElseThrow();
    // Act
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals("(int[])int[]", signature.erasure().signature());
    assertTrue(signature.parameters().getFirst().knownIdentity().orElseThrow().displayName().contains("Mark"));
    assertEquals("int[]", signature.erasure().parameters().getFirst().knownIdentity().orElseThrow().displayName());
    assertEquals(original.aggregate(), decoded.aggregate());
  }

  @TestFactory
  Stream<DynamicTest> givenPartialAttributedThrows_whenSelectedStructureContradictsWrittenTypeReference_thenPublicAndFullClosedAdmissionReject() {
    return IntStream.range(0, 2).mapToObj(position -> DynamicTest.dynamicTest("thrown type " + position, () -> {
      // Arrange
      var original = referenceResult("interface Ref { void run() throws Exception; } static void selected() throws java.io.IOException, java.lang.ReflectiveOperationException {} Ref subject() { return Example::selected; }");
      var other = referenceResult("interface Ref { void run() throws Exception; } static void selected() throws java.lang.IllegalArgumentException, java.lang.IllegalStateException {} Ref subject() { return Example::selected; }");
      var node = referenceNode(original);
      var value = node.value(SyntaxValue.MemberReference.class);
      var selected = (SyntaxType.Resolved) value.selectedType();
      var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
      var otherSignature = (JavaTypeStructure.Executable) ((SyntaxType.Resolved) referenceNode(other).value(SyntaxValue.MemberReference.class).selectedType()).structure().orElseThrow();
      var declaration = original.aggregate().nodes().stream().filter(candidate -> candidate.value() instanceof SyntaxValue.ExecutableDeclaration executable && executable.name().equals("selected")).findFirst().orElseThrow();
      var executable = declaration.value(SyntaxValue.ExecutableDeclaration.class);
      var written = declaration.children(SyntaxRole.THROWN_TYPE).get(position);
      var header = executable.attributedHeader();
      var partialTypes = new ArrayList<>(header.thrownTypes());
      partialTypes.set(position, new SyntaxType.Resolved(partialTypes.get(position).knownIdentity().orElseThrow()));
      var partialHeader = new SyntaxValue.ExecutableDeclaration.AttributedHeader(header.receiverType(), header.receiverOwnerKey(), header.receiverSimpleName(), header.enclosingTypeKey(), partialTypes, header.defaultValue());
      var partialDeclaration = new SyntaxValue.ExecutableDeclaration(executable.name(), executable.ownerKey(), executable.declarationKey(), executable.kind(), executable.writtenModifiers(), executable.effectiveModifiers(), executable.shape(), partialHeader);
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      unavailableWrittenTypes(written, replacements);
      replacements.put(declaration, copyNode(declaration, declaration.type(), partialDeclaration,
          declaration.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replaceNode(child.node(), replacements))).toList(), declaration.semanticReference()));
      var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
      var selectedThrows = new ArrayList<>(signature.thrownTypes());
      selectedThrows.set(position, new SyntaxType.Resolved(signature.thrownTypes().get(position).knownIdentity().orElseThrow(),
          ((SyntaxType.Resolved) otherSignature.thrownTypes().get(position)).structure().orElseThrow()));
      var wrongSignature = new JavaTypeStructure.Executable(signature.variables(), signature.parameters(), signature.returnType(), signature.receiver(), selectedThrows, signature.erasure());
      var wrongValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), new SyntaxType.Resolved(selected.identity(), wrongSignature), value.selection(), value.staticMember());
      var positiveNode = referenceNode(positive);
      var values = withReplacedNodes(positive, new IdentityHashMap<>(Map.of(positiveNode, copyNode(positiveNode, positiveNode.type(), wrongValue, positiveNode.children(), positiveNode.semanticReference()))), Set.of());
      var neutralValue = referenceCoverageValue(positive, positiveNode.nodeId());
      var selectedProjection = (CoverageEvidenceValue.Structure) coverageField(neutralValue, "selectedType");
      var executableProjection = (CoverageEvidenceValue.Structure) ((CoverageEvidenceValue.OptionalValue) coverageField(selectedProjection, "structure")).value().orElseThrow();
      var otherSelectedProjection = (CoverageEvidenceValue.Structure) coverageField(referenceCoverageValue(other, referenceNode(other).nodeId()), "selectedType");
      var otherExecutableProjection = (CoverageEvidenceValue.Structure) ((CoverageEvidenceValue.OptionalValue) coverageField(otherSelectedProjection, "structure")).value().orElseThrow();
      var neutralThrows = new ArrayList<>(((CoverageEvidenceValue.Sequence) coverageField(executableProjection, "thrownTypes")).values());
      var otherThrown = (CoverageEvidenceValue.Structure) ((CoverageEvidenceValue.Sequence) coverageField(otherExecutableProjection, "thrownTypes")).values().get(position);
      neutralThrows.set(position, replacedCoverageField((CoverageEvidenceValue.Structure) neutralThrows.get(position), "structure", coverageField(otherThrown, "structure")));
      var wrongExecutableProjection = replacedCoverageField(executableProjection, "thrownTypes", new CoverageEvidenceValue.Sequence(neutralThrows));
      var wrongSelectedProjection = replacedCoverageField(selectedProjection, "structure", new CoverageEvidenceValue.OptionalValue(Optional.of(wrongExecutableProjection)));
      var coverage = alteredReferenceCoverage(positive, positiveNode.nodeId(), positiveNode.semanticReference(),
          Optional.of(replacedCoverageField(neutralValue, "selectedType", wrongSelectedProjection)));
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(values));
      var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payloadWithAggregate(positive, values, coverage), positive.identity()));
      var matching = AstCodec.decode(AstCodec.encode(positive), positive.identity());
      // Assert
      assertEquals("A selected type must agree with its known written components", copying.getMessage());
      assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
      assertEquals(AstCompletion.INCOMPLETE, matching.completion());
      assertEquals(positive.aggregate(), matching.aggregate());
      assertEquals(SyntaxType.Unresolved.INSTANCE, matching.aggregate().node(written.nodeId()).orElseThrow().type());
      assertEquals(written.semanticReference(), matching.aggregate().node(written.nodeId()).orElseThrow().semanticReference());
      assertEquals(signature.thrownTypes(), ((JavaTypeStructure.Executable) ((SyntaxType.Resolved) referenceNode(matching).value(SyntaxValue.MemberReference.class).selectedType()).structure().orElseThrow()).thrownTypes());
    }));
  }

  private static CoverageEvidenceValue coverageField(CoverageEvidenceValue.Structure value, String name) {
    return value.fields().stream().filter(field -> field.name().equals(name)).findFirst().orElseThrow().value();
  }

  private static CoverageEvidenceValue.Structure replacedCoverageField(CoverageEvidenceValue.Structure value, String name, CoverageEvidenceValue replacement) {
    return new CoverageEvidenceValue.Structure(value.tag(), value.fields().stream().map(field -> field.name().equals(name)
        ? new CoverageEvidenceValue.Field(name, replacement) : field).toList());
  }

  @Test
  void givenExecutableSignatureAsArrayComponent_whenCopiedOrDecoded_thenValueTypeComponentsRejectIt() throws IOException {
    // Arrange
    var original = referenceResult("static int selected(int input) { return input; } java.util.function.IntUnaryOperator subject() { return Example::selected; }");
    var signature = ((SyntaxType.Resolved) referenceNode(original).value(SyntaxValue.MemberReference.class).selectedType()).structure().orElseThrow();
    var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
    writer.text("java-type-structure/array");
    writer.count(1);
    writer.text("component");
    new RetainedValueCodec(false).write(writer, signature);
    // Act
    var copying = assertThrows(IllegalArgumentException.class, () -> new JavaTypeStructure.Array(signature));
    var decoding = assertThrows(AstContractException.class,
        () -> new RetainedValueCodec(false).read(new CanonicalValueReader(writer.toByteArray(), AstCodec.FORMAT_VERSION)));
    // Assert
    assertEquals("An executable signature cannot be a value-type component", copying.getMessage());
    assertEquals(copying.getMessage(), decoding.getCause().getMessage());
  }

  @TestFactory
  Stream<DynamicTest> givenPartiallyAvailableSelectedSignatures_whenReconciled_thenKnownComponentsSurviveAndContradictionsStillReject() {
    return Stream.of("parameter", "return").map(part -> DynamicTest.dynamicTest(part, () -> {
      // Arrange
      var original = referenceResult("static String selected(String input) { return input; } java.util.function.UnaryOperator<String> subject() { return Example::selected; }");
      var node = referenceNode(original);
      var value = node.value(SyntaxValue.MemberReference.class);
      var selected = (SyntaxType.Resolved) value.selectedType();
      var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
      var unavailable = new SyntaxType.StructureUnavailable(signature.returnType().knownIdentity().orElseThrow());
      var partialSignature = new JavaTypeStructure.Executable(signature.variables(), part.equals("parameter") ? List.of(unavailable) : signature.parameters(),
          part.equals("return") ? unavailable : signature.returnType(), signature.receiver(), signature.thrownTypes(), signature.erasure());
      var partialValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
          new SyntaxType.Resolved(selected.identity(), partialSignature), value.selection(), value.staticMember());
      var truthful = copyNode(node, node.type(), partialValue, node.children(), node.semanticReference());
      var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original,
          new IdentityHashMap<>(Map.of(node, truthful)), Set.of())));
      var wrong = new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.Integer", "java.lang.Integer"));
      var wrongSignature = new JavaTypeStructure.Executable(signature.variables(), part.equals("parameter") ? List.of(wrong) : signature.parameters(),
          part.equals("return") ? wrong : signature.returnType(), signature.receiver(), signature.thrownTypes(),
          new JavaExecutableErasure(part.equals("parameter") ? List.of(wrong) : signature.erasure().parameters(),
              part.equals("return") ? wrong : signature.erasure().returnType()));
      var erasure = part.equals("parameter") ? "(java.lang.Integer)java.lang.String" : "(java.lang.String)java.lang.Integer";
      var contradictory = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
          new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, erasure, erasure), wrongSignature), value.selection(), value.staticMember());
      var changed = copyNode(node, node.type(), contradictory, node.children(), node.semanticReference());
      var values = withReplacedNodes(positive, new IdentityHashMap<>(Map.of(referenceNode(positive), changed)), Set.of());
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(values));
      var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(payloadWithAggregate(positive, values), positive.identity()));
      var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
      // Assert
      assertEquals("A selected signature type must agree with its known written type", copying.getMessage());
      assertEquals(copying.getMessage(), envelope.getCause().getMessage());
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertEquals(partialValue, referenceNode(decoded).value());
    }));
  }

  @Test
  void givenExecutableIdentityWithConflictingErasure_whenCopied_thenKnownComponentIdentityRejectsTheContradiction() throws IOException {
    // Arrange
    var original = referenceResult("static int selected(int input) { return input; } java.util.function.IntUnaryOperator subject() { return Example::selected; }");
    var selected = (SyntaxType.Resolved) referenceNode(original).value(SyntaxValue.MemberReference.class).selectedType();
    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, "(long)int", "(long)int"), selected.structure()));
    // Assert
    assertEquals("Executable identity erasure conflicts with its known signature components", failure.getMessage());
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableSignaturePeers_whenOuterErasureDisagrees_thenPublicAndClosedValuesRejectKnownContradictions() {
    return Stream.of("first", "second", "return", "result", "arity").map(part -> DynamicTest.dynamicTest(part, () -> {
      // Arrange
      var original = referenceResult("interface Pair { int apply(int first, int second); } static int selected(int first, int second) { return first + second; } Pair subject() { return Example::selected; }");
      var node = referenceNode(original);
      var value = node.value(SyntaxValue.MemberReference.class);
      var selected = (SyntaxType.Resolved) value.selectedType();
      var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
      var primitive = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
      assertEquals(new JavaExecutableErasure(List.of(primitive, primitive), primitive), signature.erasure());
      var parameters = List.of(part.equals("second") || part.equals("arity") ? SyntaxType.Unresolved.INSTANCE : signature.parameters().getFirst(),
          part.equals("first") || part.equals("result") || part.equals("arity") ? SyntaxType.Unresolved.INSTANCE : signature.parameters().getLast());
      var resultType = part.equals("return") || part.equals("arity") ? SyntaxType.Unresolved.INSTANCE : signature.returnType();
      var partial = new JavaTypeStructure.Executable(signature.variables(), parameters, resultType, signature.receiver(), signature.thrownTypes(), signature.erasure());
      var partialValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), new SyntaxType.Resolved(selected.identity(), partial), value.selection(), value.staticMember());
      var changed = copyNode(node, node.type(), partialValue, node.children(), node.semanticReference());
      var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, new IdentityHashMap<>(Map.of(node, changed)), Set.of())));
      var descriptor = part.equals("second") ? "(int,long)int" : part.equals("result") ? "(int,int)long" : part.equals("arity") ? "(int)int" : "(long,int)int";
      var wrongIdentity = new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, descriptor, descriptor);
      var encodedValue = new CanonicalValueWriter("");
      new RetainedValueCodec(false).write(encodedValue, partialValue);
      var malformedValue = replaceValueFrame(encodedValue.toByteArray(), selected.identity(), wrongIdentity);
      var malformedPayload = replaceNodeValueFrame(AstCodec.encode(positive), referenceNode(positive), Arrays.copyOfRange(malformedValue, Integer.BYTES, malformedValue.length));
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> new SyntaxType.Resolved(wrongIdentity, partial));
      var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(malformedPayload, positive.identity()));
      var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
      // Assert
      assertEquals("Executable identity erasure conflicts with its known signature components", copying.getMessage());
      assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
      assertEquals(AstCompletion.INCOMPLETE, positive.completion());
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertEquals(positive.aggregate(), decoded.aggregate());
      assertEquals(partialValue, referenceNode(decoded).value());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenMissingSelectedComponents_whenKnownErasureContradictsOwnedHeaders_thenPublicAndClosedAggregatesReject() {
    return Stream.of("parameter", "return").map(part -> DynamicTest.dynamicTest(part, () -> {
      // Arrange
      var original = referenceResult("interface Pair { int apply(int first, int second); } static int selected(int first, int second) { return first + second; } Pair subject() { return Example::selected; }");
      var node = referenceNode(original);
      var value = node.value(SyntaxValue.MemberReference.class);
      var selected = (SyntaxType.Resolved) value.selectedType();
      var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
      var parameters = part.equals("parameter") ? List.of(SyntaxType.Unresolved.INSTANCE, signature.parameters().getLast()) : signature.parameters();
      var resultType = part.equals("return") ? SyntaxType.Unresolved.INSTANCE : signature.returnType();
      var partial = new JavaTypeStructure.Executable(signature.variables(), parameters, resultType, signature.receiver(), signature.thrownTypes(), signature.erasure());
      var partialValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), new SyntaxType.Resolved(selected.identity(), partial), value.selection(), value.staticMember());
      var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original,
          new IdentityHashMap<>(Map.of(node, copyNode(node, node.type(), partialValue, node.children(), node.semanticReference()))), Set.of())));
      var wrong = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long"), new JavaTypeStructure.Primitive(JavaTypeCategory.LONG));
      var wrongErasure = new JavaExecutableErasure(part.equals("parameter") ? List.of(wrong, signature.erasure().parameters().getLast()) : signature.erasure().parameters(),
          part.equals("return") ? wrong : signature.erasure().returnType());
      var wrongSignature = new JavaTypeStructure.Executable(signature.variables(), parameters, resultType, signature.receiver(), signature.thrownTypes(), wrongErasure);
      var wrongValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
          new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, wrongErasure.signature(), wrongErasure.signature()), wrongSignature), value.selection(), value.staticMember());
      var changed = copyNode(node, node.type(), wrongValue, node.children(), node.semanticReference());
      var values = withReplacedNodes(positive, new IdentityHashMap<>(Map.of(referenceNode(positive), changed)), Set.of());
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(values));
      var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payloadWithAggregate(positive, values), positive.identity()));
      var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
      // Assert
      assertEquals("Executable erasure conflicts with a known signature component", copying.getMessage());
      assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertEquals(partialValue, referenceNode(decoded).value());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailablePeers_whenCopiedErasureComponentsContradictKnownFacts_thenSignatureAndClosedAdmissionReject() {
    return Stream.of("first", "second", "return", "arity").map(part -> DynamicTest.dynamicTest(part, () -> {
      // Arrange
      var original = referenceResult("interface Pair { int apply(int first, int second); } static int selected(int first, int second) { return first + second; } Pair subject() { return Example::selected; }");
      var node = referenceNode(original);
      var value = node.value(SyntaxValue.MemberReference.class);
      var selected = (SyntaxType.Resolved) value.selectedType();
      var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
      var parameters = List.of(part.equals("second") || part.equals("return") || part.equals("arity") ? SyntaxType.Unresolved.INSTANCE : signature.parameters().getFirst(),
          part.equals("first") || part.equals("arity") ? SyntaxType.Unresolved.INSTANCE : signature.parameters().getLast());
      var resultType = part.equals("arity") ? SyntaxType.Unresolved.INSTANCE : signature.returnType();
      var partial = new JavaTypeStructure.Executable(signature.variables(), parameters, resultType, signature.receiver(), signature.thrownTypes(), signature.erasure());
      var partialValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), new SyntaxType.Resolved(selected.identity(), partial), value.selection(), value.staticMember());
      var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original,
          new IdentityHashMap<>(Map.of(node, copyNode(node, node.type(), partialValue, node.children(), node.semanticReference()))), Set.of())));
      var wrong = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long"), new JavaTypeStructure.Primitive(JavaTypeCategory.LONG));
      var wrongParameters = part.equals("arity") ? List.of(signature.erasure().parameters().getFirst())
          : List.of(part.equals("first") ? wrong : signature.erasure().parameters().getFirst(), part.equals("second") ? wrong : signature.erasure().parameters().getLast());
      var wrongErasure = new JavaExecutableErasure(wrongParameters, part.equals("return") ? wrong : signature.erasure().returnType());
      var encodedValue = new CanonicalValueWriter("");
      new RetainedValueCodec(false).write(encodedValue, partialValue);
      var malformedValue = replaceValueFrame(encodedValue.toByteArray(), signature.erasure(), wrongErasure);
      var malformedPayload = replaceNodeValueFrame(AstCodec.encode(positive), referenceNode(positive), Arrays.copyOfRange(malformedValue, Integer.BYTES, malformedValue.length));
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> new JavaTypeStructure.Executable(signature.variables(), parameters, resultType,
          signature.receiver(), signature.thrownTypes(), wrongErasure));
      var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(malformedPayload, positive.identity()));
      var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
      // Assert
      assertEquals(part.equals("arity") ? "Executable erasure must preserve its parameter count" : "Executable erasure conflicts with a known signature component", copying.getMessage());
      assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertEquals(partialValue, referenceNode(decoded).value());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableSemanticResults_whenIntrinsicReferenceErasureContradictsItsDeclaration_thenPublicAndClosedNodesReject() {
    return Stream.of(false, true).map(constructor -> DynamicTest.dynamicTest(constructor ? "constructor" : "array clone", () -> {
      // Arrange
      var original = referenceResult(constructor ? "java.util.function.Supplier<Example> subject() { return Example::new; }"
          : "java.util.function.Function<int[],int[]> subject() { return int[]::clone; }");
      var node = referenceNode(original);
      var value = node.value(SyntaxValue.MemberReference.class);
      var selected = (SyntaxType.Resolved) value.selectedType();
      var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
      var partial = new JavaTypeStructure.Executable(signature.variables(), signature.parameters(), SyntaxType.Unresolved.INSTANCE,
          signature.receiver(), signature.thrownTypes(), signature.erasure());
      var partialValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), new SyntaxType.Resolved(selected.identity(), partial), value.selection(), value.staticMember());
      var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original,
          new IdentityHashMap<>(Map.of(node, copyNode(node, node.type(), partialValue, node.children(), node.semanticReference()))), Set.of())));
      var wrongReturn = constructor ? new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), new JavaTypeStructure.Primitive(JavaTypeCategory.INT))
          : new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "int[]", "int[]"), new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.INT)));
      var wrongErasure = new JavaExecutableErasure(signature.erasure().parameters(), wrongReturn);
      var wrongSignature = new JavaTypeStructure.Executable(signature.variables(), signature.parameters(), SyntaxType.Unresolved.INSTANCE,
          signature.receiver(), signature.thrownTypes(), wrongErasure);
      var wrongValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
          new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, wrongErasure.signature(), wrongErasure.signature()), wrongSignature), value.selection(), value.staticMember());
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(node, node.type(), wrongValue, node.children(), node.semanticReference()));
      var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(replaceNodeValueFrame(AstCodec.encode(positive), referenceNode(positive), wrongValue), positive.identity()));
      var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
      // Assert
      assertEquals(constructor ? "A constructor reference requires a void declaration return type" : "An intrinsic array clone requires its compiler declaration signature", copying.getMessage());
      assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertEquals(partialValue, referenceNode(decoded).value());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableWrittenAndSelectedTypes_whenErasureContradictsKnownSyntax_thenPublicAndFullClosedAdmissionReject() {
    return Stream.of(new WrittenErasureCase("int", "long"), new WrittenErasureCase("int[]", "long[]"),
        new WrittenErasureCase("int[]", "int[][]"), new WrittenErasureCase("int[][]", "int[]"), new WrittenErasureCase("String", "Integer"))
        .flatMap(variant -> Stream.of("parameter", "return").map(part -> DynamicTest.dynamicTest(variant + " " + part, () -> {
          // Arrange
          var original = referenceResult(referenceMember(variant.written(), variant.written()));
          var node = referenceNode(original);
          var value = node.value(SyntaxValue.MemberReference.class);
          var selected = (SyntaxType.Resolved) value.selectedType();
          var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
          var declaration = original.aggregate().nodes().stream().filter(candidate -> candidate.value() instanceof SyntaxValue.ExecutableDeclaration executable
              && executable.name().equals("selected")).findFirst().orElseThrow();
          var written = part.equals("parameter") ? declaration.children(SyntaxRole.PARAMETER).getFirst().requiredChild(SyntaxRole.DECLARED_TYPE) : declaration.requiredChild(SyntaxRole.RETURN_TYPE);
          var partialValue = unavailableSelectedComponent(value, part);
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          unavailableWrittenTypes(written, replacements);
          replacements.put(node, copyNode(node, node.type(), partialValue, node.children(), node.semanticReference()));
          var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
          var other = referenceResult(referenceMember(part.equals("parameter") ? variant.contrasting() : variant.written(), part.equals("return") ? variant.contrasting() : variant.written()));
          var otherNode = referenceNode(other);
          var otherValue = unavailableSelectedComponent(otherNode.value(SyntaxValue.MemberReference.class), part);
          var otherPositive = reconciledResult(other, AstValueAdmission.admit(withReplacedNodes(other,
              new IdentityHashMap<>(Map.of(otherNode, copyNode(otherNode, otherNode.type(), otherValue, otherNode.children(), otherNode.semanticReference()))), Set.of())));
          var wrongErasure = ((JavaTypeStructure.Executable) ((SyntaxType.Resolved) otherValue.selectedType()).structure().orElseThrow()).erasure();
          var partial = (JavaTypeStructure.Executable) ((SyntaxType.Resolved) partialValue.selectedType()).structure().orElseThrow();
          var wrongSignature = new JavaTypeStructure.Executable(signature.variables(), partial.parameters(), partial.returnType(), signature.receiver(), signature.thrownTypes(), wrongErasure);
          var wrongValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
              new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, wrongErasure.signature(), wrongErasure.signature()), wrongSignature), value.selection(), value.staticMember());
          assertEquals(otherValue, wrongValue);
          var positiveNode = referenceNode(positive);
          var values = withReplacedNodes(positive, new IdentityHashMap<>(Map.of(positiveNode, copyNode(positiveNode, positiveNode.type(), wrongValue, positiveNode.children(), positiveNode.semanticReference()))), Set.of());
          var coverage = alteredReferenceCoverage(positive, node.nodeId(), node.semanticReference(), Optional.of(referenceCoverageValue(otherPositive, otherNode.nodeId())));
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(values));
          var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payloadWithAggregate(positive, values, coverage), positive.identity()));
          var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
          // Assert
          assertEquals("A selected erasure must agree with its known written type form", copying.getMessage());
          assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(positive.aggregate(), decoded.aggregate());
          assertEquals(partialValue, referenceNode(decoded).value());
          assertEquals(written.value(), decoded.aggregate().node(written.nodeId()).orElseThrow().value());
        })));
  }

  @Test
  void givenMissingWrittenNominalResolution_whenSelectedErasureConflictsWithCanonicalParameter_thenPublicInputAndClosedAdmissionReject() throws IOException {
    // Arrange
    var original = referenceResult(referenceMember("String", "String"));
    var node = referenceNode(original);
    var declaration = original.aggregate().nodes().stream().filter(candidate -> candidate.value() instanceof SyntaxValue.ExecutableDeclaration executable && executable.name().equals("selected")).findFirst().orElseThrow();
    var written = declaration.children(SyntaxRole.PARAMETER).getFirst().requiredChild(SyntaxRole.DECLARED_TYPE);
    var missing = copyNode(written, SyntaxType.Unresolved.INSTANCE, written.value(), written.children(), Optional.empty());
    var partialValue = unavailableSelectedComponent(node.value(SyntaxValue.MemberReference.class), "parameter");
    var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original,
        new IdentityHashMap<>(Map.of(written, missing, node, copyNode(node, node.type(), partialValue, node.children(), node.semanticReference()))), Set.of(written.nodeId()))));
    var other = referenceResult(referenceMember("Integer", "String"));
    var otherNode = referenceNode(other);
    var wrongValue = unavailableSelectedComponent(otherNode.value(SyntaxValue.MemberReference.class), "parameter");
    var otherPositive = reconciledResult(other, AstValueAdmission.admit(withReplacedNodes(other,
        new IdentityHashMap<>(Map.of(otherNode, copyNode(otherNode, otherNode.type(), wrongValue, otherNode.children(), otherNode.semanticReference()))), Set.of())));
    var positiveNode = referenceNode(positive);
    var values = withReplacedNodes(positive, new IdentityHashMap<>(Map.of(positiveNode, copyNode(positiveNode, positiveNode.type(), wrongValue, positiveNode.children(), positiveNode.semanticReference()))), Set.of());
    var aggregate = AstValueAdmission.admit(values);
    var coverage = alteredReferenceCoverage(positive, node.nodeId(), node.semanticReference(), Optional.of(referenceCoverageValue(otherPositive, otherNode.nodeId())));
    // Act
    var copying = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(positive.snapshot(), positive.environment(), positive.program(), Optional.of(aggregate), positive.diagnostics(), coverage));
    var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payloadWithAggregate(positive, values, coverage), positive.identity()));
    var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
    // Assert
    assertEquals("Executable erasure conflicts with a known signature component", copying.getMessage());
    assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(positive.aggregate(), decoded.aggregate());
    assertEquals(SyntaxType.Unresolved.INSTANCE, decoded.aggregate().node(written.nodeId()).orElseThrow().type());
    assertTrue(decoded.aggregate().node(written.nodeId()).orElseThrow().semanticReference().isEmpty());
  }

  private record WrittenErasureCase(String written, String contrasting) {}

  @Test
  void givenUnattributedWrittenUnboundedArrayArgument_whenSelectedBoundChanges_thenPublicNodeAndBothClosedBoundariesReject() throws IOException {
    // Arrange
    var original = referenceResult("java.util.function.IntFunction<java.util.List<?>[]> subject() { return java.util.List<?>[]::new; }");
    var node = referenceNode(original);
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    unavailableWrittenTypes(node.requiredChild(SyntaxRole.QUALIFIER), replacements);
    var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
    var positiveNode = referenceNode(positive);
    var other = referenceResult("java.util.List<? extends Number>[] bound(java.util.List<? extends Number>[] input) { return input; }");
    var selectedType = other.aggregate().nodes().stream().filter(candidate -> candidate.value() instanceof SyntaxValue.ExecutableDeclaration executable && executable.name().equals("bound"))
        .findFirst().orElseThrow().requiredChild(SyntaxRole.RETURN_TYPE);
    var value = node.value(SyntaxValue.MemberReference.class);
    var wrong = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), selectedType.type(), value.selection(), value.staticMember());
    var neutral = referenceCoverageValue(positive, node.nodeId());
    var changedNeutral = new CoverageEvidenceValue.Structure(neutral.tag(), neutral.fields().stream().map(field -> field.name().equals("selectedType")
        ? new CoverageEvidenceValue.Field(field.name(), sourceCoverageType(other, selectedType.nodeId())) : field).toList());
    var coverage = alteredReferenceCoverage(positive, node.nodeId(), node.semanticReference(), Optional.of(changedNeutral));
    var nodeWriter = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
    var codec = new RetainedValueCodec(false);
    codec.write(nodeWriter, positiveNode);
    var malformedNode = replaceNodeValueFrame(nodeWriter.toByteArray(), positiveNode, wrong);
    var malformedPayload = replaceValueFrame(replaceNodeValueFrame(AstCodec.encode(positive), positiveNode, wrong), positive.coverage(), coverage);
    // Act
    var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(positiveNode, positiveNode.type(), wrong, positiveNode.children(), positiveNode.semanticReference()));
    var decoding = assertThrows(IllegalArgumentException.class, () -> codec.read(new CanonicalValueReader(malformedNode, AstCodec.FORMAT_VERSION)));
    var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(malformedPayload, positive.identity()));
    var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
    // Assert
    assertEquals("A selected wildcard must agree with its written bound form", copying.getMessage());
    assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
    assertEquals(copying.getMessage(), deepestCause(envelope).getMessage());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(positive.aggregate(), decoded.aggregate());
    assertEquals(JavaSourceKind.UNBOUNDED_WILDCARD, referenceNode(decoded).requiredChild(SyntaxRole.QUALIFIER).requiredChild(SyntaxRole.COMPONENT_TYPE).children(SyntaxRole.TYPE_ARGUMENT).getFirst().sourceKind());
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableWrittenWildcardBounds_whenSelectionContradictsKindCategoryOrBound_thenPublicAndFullClosedAdmissionReject() {
    return Stream.of("? extends Number", "? super Number").flatMap(written -> Stream.of(
        written.startsWith("? extends") ? "? super Number" : "? extends Number", "Integer", written.replace("Number", "Integer"))
        .map(contrasting -> DynamicTest.dynamicTest(written + " selected " + contrasting, () -> {
          // Arrange
          var original = referenceResult(wildcardReferenceMember(written));
          var node = referenceNode(original);
          var declaration = original.aggregate().nodes().stream().filter(candidate -> candidate.value() instanceof SyntaxValue.ExecutableDeclaration executable && executable.name().equals("selected")).findFirst().orElseThrow();
          var type = declaration.children(SyntaxRole.PARAMETER).getFirst().requiredChild(SyntaxRole.DECLARED_TYPE);
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          unavailableWrittenTypes(type, replacements);
          var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
          var other = referenceResult(wildcardReferenceMember(contrasting));
          var otherNode = referenceNode(other);
          var wrong = otherNode.value(SyntaxValue.MemberReference.class);
          var positiveNode = referenceNode(positive);
          var values = withReplacedNodes(positive, new IdentityHashMap<>(Map.of(positiveNode, copyNode(positiveNode, positiveNode.type(), wrong, positiveNode.children(), positiveNode.semanticReference()))), Set.of());
          var coverage = alteredReferenceCoverage(positive, node.nodeId(), node.semanticReference(), Optional.of(referenceCoverageValue(other, otherNode.nodeId())));
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(values));
          var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payloadWithAggregate(positive, values, coverage), positive.identity()));
          var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
          // Assert
          assertEquals(contrasting.equals(written.replace("Number", "Integer")) ? "A selected type must agree with its known written components" : "A selected wildcard must agree with its written bound form", copying.getMessage());
          assertEquals(copying.getMessage(), deepestCause(decoding).getMessage());
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(positive.aggregate(), decoded.aggregate());
          var preserved = decoded.aggregate().node(type.nodeId()).orElseThrow().children(SyntaxRole.TYPE_ARGUMENT).getFirst();
          assertEquals(written.startsWith("? extends") ? JavaSourceKind.EXTENDS_WILDCARD : JavaSourceKind.SUPER_WILDCARD, preserved.sourceKind());
          assertEquals(SyntaxType.Unresolved.INSTANCE, preserved.type());
        })));
  }

  private static String wildcardReferenceMember(String argument) {
    return "static void selected(java.util.List<" + argument + "> input) {} java.util.function.Consumer<java.util.List<" + argument + ">> subject() { return Example::selected; }";
  }

  private static CoverageEvidenceValue sourceCoverageType(AstResult result, String nodeKey) {
    return result.coverage().validationEvidence().outcomes().stream().flatMap(outcome -> outcome.support().facts().stream())
        .filter(fact -> fact.identity().equals(nodeKey)).map(CoverageFactReference::value).filter(CoverageFactValue.Source.class::isInstance)
        .map(CoverageFactValue.Source.class::cast).map(CoverageFactValue.Source::value).map(CoverageSourceFact::type).findFirst().orElseThrow();
  }

  private static String referenceMember(String parameter, String result) {
    var returned = result.equals("int") || result.equals("long") ? "0" : "null";
    return "interface Ref { " + result + " apply(" + parameter + " input); } static " + result + " selected(" + parameter + " input) { return " + returned + "; } Ref subject() { return Example::selected; }";
  }

  private static SyntaxValue.MemberReference unavailableSelectedComponent(SyntaxValue.MemberReference value, String part) {
    var selected = (SyntaxType.Resolved) value.selectedType();
    var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
    var partial = new JavaTypeStructure.Executable(signature.variables(), part.equals("parameter") ? List.of(SyntaxType.Unresolved.INSTANCE) : signature.parameters(),
        part.equals("return") ? SyntaxType.Unresolved.INSTANCE : signature.returnType(), signature.receiver(), signature.thrownTypes(), signature.erasure());
    return new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), new SyntaxType.Resolved(selected.identity(), partial), value.selection(), value.staticMember());
  }

  private static SyntaxNode unavailableWrittenTypes(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), unavailableWrittenTypes(child.node(), replacements))).toList();
    var type = node.value() instanceof SyntaxValue.PackageQualifier ? node.type() : SyntaxType.Unresolved.INSTANCE;
    var result = copyNode(node, type, node.value(), children, node.semanticReference());
    replacements.put(node, result);
    return result;
  }

  @TestFactory
  Stream<DynamicTest> givenPartialCanonicalHeaders_whenKnownTypesDisagree_thenReturnAndEachParameterRemainBinding() {
    // Arrange
    var parts = Stream.of("return", "parameter", "parameter-with-missing-peer");
    // Act
    var tests = parts.map(part -> DynamicTest.dynamicTest(switch (part) {
      case "return" -> "givenAPartialReturnHeader_whenItsKnownTypeConflicts_thenCanonicalCorrespondenceRefuses";
      case "parameter" -> "givenAPartialParameterHeader_whenItsKnownTypeConflicts_thenCanonicalCorrespondenceRefuses";
      default -> "givenAParameterWithAMissingPeerType_whenItsKnownTypeConflicts_thenCanonicalCorrespondenceRefuses";
    }, () -> {
      // Arrange
      var original = referenceResult("String subject(String value, String other) { return \"result\"; }");
      var declaration = original.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration executable
          && executable.name().equals("subject")).findFirst().orElseThrow();
      var node = part.equals("return") ? declaration.requiredChild(SyntaxRole.RETURN_TYPE)
          : declaration.children(SyntaxRole.PARAMETER).getFirst().requiredChild(SyntaxRole.DECLARED_TYPE);
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      if (part.equals("parameter-with-missing-peer")) {
        var peer = declaration.children(SyntaxRole.PARAMETER).get(1).requiredChild(SyntaxRole.DECLARED_TYPE);
        replacements.put(peer, copyNode(peer, SyntaxType.Unresolved.INSTANCE, peer.value(), peer.children(), peer.semanticReference()));
      }
      var partial = copyNode(node, new SyntaxType.StructureUnavailable(node.type().knownIdentity().orElseThrow()), node.value(), node.children(), node.semanticReference());
      replacements.put(node, partial);
      var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
      var wrong = new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.Integer", "java.lang.Integer"));
      var changed = copyNode(partial, wrong, partial.value(), partial.children(), partial.semanticReference());
      var malformed = AstValueAdmission.admit(withReplacedNodes(positive,
          new IdentityHashMap<>(Map.of(positive.aggregate().node(node.nodeId()).orElseThrow(), changed)), Set.of()));
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(positive.snapshot(), positive.environment(),
          positive.program(), Optional.of(malformed), positive.diagnostics(), positive.coverage()));
      var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(replaceNodeTypeFrame(AstCodec.encode(positive),
          positive.aggregate().node(node.nodeId()).orElseThrow(), wrong), positive.identity()));
      var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
      // Assert
      assertTrue(copying.getMessage().contains("Canonical method declaration contradicts its retained written header"), copying.toString());
      assertEquals(copying.getMessage(), deepestCause(envelope).getMessage());
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertEquals(positive.aggregate(), decoded.aggregate());
    }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenContradictoryOwnedOverloadSignatures_whenCopiedOrFullyDecoded_thenCanonicalComponentsRejectBothDirections() {
    return Stream.of("selected-type", "selected-key").flatMap(change -> Stream.of(false, true)
        .map(reverse -> DynamicTest.dynamicTest(change + " reverse " + reverse, () -> {
          // Arrange
          var original = referenceResult("static String pick(int value) { return \"int\"; } static String pick(long value) { return \"long\"; } "
              + "java.util.function.IntFunction<String> subject() { return Example::pick; } "
              + "java.util.function.LongFunction<String> alternative() { return Example::pick; }");
          var references = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).toList();
          var node = references.get(reverse ? 1 : 0);
          var other = references.get(reverse ? 0 : 1);
          var value = node.value(SyntaxValue.MemberReference.class);
          var contrasting = other.value(SyntaxValue.MemberReference.class);
          var altered = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
              change.equals("selected-type") ? contrasting.selectedType() : value.selectedType(), value.selection(), value.staticMember());
          var selection = change.equals("selected-key") ? other.semanticReference() : node.semanticReference();
          var changed = copyNode(node, node.type(), altered, node.children(), selection);
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          replacements.put(node, changed);
          var malformed = withAdditionalEdge(withReplacedNodes(original, replacements, Set.of(node.nodeId())), changed.semanticEdges().getFirst());
          var coverage = alteredReferenceCoverage(original, node.nodeId(), selection,
              change.equals("selected-type") ? Optional.of(referenceCoverageValue(original, other.nodeId())) : Optional.empty());
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(malformed));
          var decoding = assertThrows(AstContractException.class,
              () -> AstCodec.decode(payloadWithAggregate(original, malformed, coverage), original.identity()));
          var positive = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertNotEquals(node.requiredReference().symbolKey(), other.requiredReference().symbolKey());
          assertNotEquals(value.selectedType(), contrasting.selectedType());
          assertEquals("A selected signature type must agree with its known written type", copying.getMessage());
          assertEquals(copying.getMessage(), decoding.getCause().getMessage());
          assertEquals(AstCompletion.COMPLETE, positive.completion());
          assertEquals(original.aggregate(), positive.aggregate());
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenAlteredIntrinsicCloneSignature_whenCopiedOrDecoded_thenEveryKnownDeclarationComponentIsEnforced() {
    return Stream.of("return", "parameter", "throws", "receiver", "variable").map(component -> DynamicTest.dynamicTest(component, () -> {
      // Arrange
      var original = referenceResult("java.util.function.Function<int[],int[]> subject() { return int[]::clone; }");
      var node = referenceNode(original);
      var value = node.value(SyntaxValue.MemberReference.class);
      var selected = (SyntaxType.Resolved) value.selectedType();
      var signature = (JavaTypeStructure.Executable) selected.structure().orElseThrow();
      var object = signature.returnType();
      var integer = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), new JavaTypeStructure.Primitive(JavaTypeCategory.INT));
      var objectArray = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "java.lang.Object[]", "java.lang.Object[]"),
          new JavaTypeStructure.Array(((SyntaxType.Resolved) object).structure().orElseThrow()));
      var changedSignature = new JavaTypeStructure.Executable(
          component.equals("variable") ? List.of(new JavaTypeStructure.Variable("type-parameter:external:0")) : signature.variables(),
          component.equals("parameter") ? List.of(integer) : signature.parameters(),
          component.equals("return") ? objectArray : object,
          component.equals("receiver") ? Optional.of(object) : signature.receiver(),
          component.equals("throws") ? List.of(object) : signature.thrownTypes(),
          new JavaExecutableErasure(component.equals("parameter") ? List.of(integer) : signature.erasure().parameters(),
              component.equals("return") ? objectArray : signature.erasure().returnType()));
      var changedValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
          new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, "supplied clone declaration",
              component.equals("parameter") ? "(int)java.lang.Object" : component.equals("return") ? "()java.lang.Object[]" : "()java.lang.Object"),
              changedSignature), value.selection(), value.staticMember());
      var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
      new RetainedValueCodec(false).write(writer, node);
      // Act
      var copying = assertThrows(IllegalArgumentException.class,
          () -> copyNode(node, node.type(), changedValue, node.children(), node.semanticReference()));
      var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false)
          .read(new CanonicalValueReader(replaceNodeValueFrame(writer.toByteArray(), node, changedValue), AstCodec.FORMAT_VERSION)));
      var envelope = assertThrows(AstContractException.class,
          () -> AstCodec.decode(replaceNodeValueFrame(AstCodec.encode(original), node, changedValue), original.identity()));
      // Assert
      assertEquals("An intrinsic array clone requires its compiler declaration signature", copying.getMessage());
      assertEquals(copying.getMessage(), decoding.getMessage());
      assertEquals(copying.getMessage(), envelope.getCause().getMessage());
      assertEquals(new JavaTypeStructure.Declared("type:java.lang.Object", Optional.empty(), List.of()),
          ((SyntaxType.Resolved) signature.returnType()).structure().orElseThrow());
      assertEquals(original.aggregate(), AstCodec.decode(AstCodec.encode(original), original.identity()).aggregate());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenPartialWrittenTypes_whenKnownCategoryConflicts_thenPublicAndClosedAdmissionRejectWhileMatchingPartialValuesRemainIncomplete() {
    return Stream.of("array", "primitive").map(form -> DynamicTest.dynamicTest(form, () -> {
      // Arrange
      var original = referenceResult(form.equals("array") ? "String[] subject(Object input) { return (String[]) input; }"
          : "int subject(Object input) { return (int) input; }");
      var cast = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
      var node = cast.requiredChild(SyntaxRole.TYPE_OPERAND);
      var partial = new SyntaxType.StructureUnavailable(node.type().knownIdentity().orElseThrow());
      var truthful = copyNode(node, partial, node.value(), node.children(), node.semanticReference());
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      replacements.put(node, truthful);
      var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
      var wrong = new SyntaxType.StructureUnavailable(form.equals("array")
          ? new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String")
          : new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long"));
      var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
      new RetainedValueCodec(false).write(writer, truthful);
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(node, wrong, node.value(), node.children(), node.semanticReference()));
      var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false)
          .read(new CanonicalValueReader(replaceNodeTypeFrame(writer.toByteArray(), truthful, wrong), AstCodec.FORMAT_VERSION)));
      var envelope = assertThrows(AstContractException.class,
          () -> AstCodec.decode(replaceNodeTypeFrame(AstCodec.encode(positive), positive.aggregate().node(node.nodeId()).orElseThrow(), wrong), positive.identity()));
      var accepted = AstCodec.decode(AstCodec.encode(positive), positive.identity());
      // Assert
      assertEquals("Written type-use kind conflicts with known attributed category", copying.getMessage());
      assertEquals(copying.getMessage(), decoding.getMessage());
      assertEquals(copying.getMessage(), envelope.getCause().getMessage());
      assertEquals(AstCompletion.INCOMPLETE, accepted.completion());
      assertEquals(partial, accepted.aggregate().node(node.nodeId()).orElseThrow().type());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenPartialArrayTypeUses_whenKnownDimensionsConflict_thenPublicAndClosedAdmissionRejectWhileMatchingValuesRemainIncomplete() {
    return Stream.of(Map.entry("String[]", 1), Map.entry("String[][]", 2), Map.entry("String @Mark []", 1), Map.entry("String @Mark [] @Mark []", 2))
        .flatMap(form -> Stream.of("field", "cast").flatMap(context -> Stream.of(false, true).map(missingLeaf -> DynamicTest.dynamicTest(
            form.getKey() + " " + context + " missing leaf=" + missingLeaf, () -> {
              // Arrange
              var declaration = "@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Mark {} ";
              var original = referenceResult(declaration + (context.equals("field") ? form.getKey() + " value;"
                  : form.getKey() + " subject(Object input) { return (" + form.getKey() + ") input; }"));
              var role = context.equals("field") ? SyntaxRole.DECLARED_TYPE : SyntaxRole.TYPE_OPERAND;
              var node = original.aggregate().nodes().stream().filter(candidate -> candidate.syntaxKind() == SyntaxKind.TYPE_USE
                  && candidate.structuralPath().steps().getLast().role() == role).findFirst().orElseThrow();
              var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
              var truthful = partialArrayTypeUse(node, missingLeaf, replacements);
              var removedReferences = replacements.entrySet().stream().filter(entry -> entry.getValue().semanticReference().isEmpty())
                  .map(entry -> entry.getKey().nodeId()).collect(Collectors.toSet());
              var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, removedReferences)));
              var wrongName = "java.lang.String" + "[]".repeat(form.getValue() + 1);
              var wrong = new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.ARRAY, wrongName, wrongName));
              var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
              new RetainedValueCodec(false).write(writer, truthful);
              // Act
              var copying = assertThrows(IllegalArgumentException.class,
                  () -> copyNode(truthful, wrong, truthful.value(), truthful.children(), truthful.semanticReference()));
              var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false)
                  .read(new CanonicalValueReader(replaceNodeTypeFrame(writer.toByteArray(), truthful, wrong), AstCodec.FORMAT_VERSION)));
              var envelope = assertThrows(AstContractException.class,
                  () -> AstCodec.decode(replaceNodeTypeFrame(AstCodec.encode(positive), positive.aggregate().node(node.nodeId()).orElseThrow(), wrong), positive.identity()));
              var accepted = AstCodec.decode(AstCodec.encode(positive), positive.identity());
              // Assert
              assertEquals("Written type-use dimensions conflict with known attributed identity", copying.getMessage());
              assertEquals(copying.getMessage(), decoding.getMessage());
              assertEquals(copying.getMessage(), envelope.getCause().getMessage());
              assertEquals(AstCompletion.INCOMPLETE, accepted.completion());
              assertEquals(truthful, accepted.aggregate().node(node.nodeId()).orElseThrow());
              assertEquals(original.aggregate(), AstCodec.decode(AstCodec.encode(original), original.identity()).aggregate());
            }))));
  }

  private static SyntaxNode partialArrayTypeUse(SyntaxNode original, boolean missingLeaf, Map<SyntaxNode, SyntaxNode> replacements) {
    SyntaxNode partial;
    if (original.sourceKind() == JavaSourceKind.ARRAY_TYPE || original.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      var children = original.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
          child.role() == SyntaxRole.COMPONENT_TYPE || child.role() == SyntaxRole.UNDERLYING_TYPE
              ? partialArrayTypeUse(child.node(), missingLeaf, replacements) : child.node())).toList();
      partial = copyNode(original, new SyntaxType.StructureUnavailable(original.type().knownIdentity().orElseThrow()), original.value(), children, original.semanticReference());
    } else if (missingLeaf) {
      partial = copyNode(original, SyntaxType.Unresolved.INSTANCE, original.value(), original.children(), Optional.empty());
    } else {
      return original;
    }
    replacements.put(original, partial);
    return partial;
  }

  @TestFactory
  Stream<DynamicTest> givenPartialCastResult_whenKnownIdentityConflictsWithWrittenOperand_thenContainingOccurrenceRejectsBothDirections() {
    return Stream.of(false, true).map(partialParent -> DynamicTest.dynamicTest("partial parent " + partialParent, () -> {
      // Arrange
      var original = referenceResult("String[] subject(Object input) { return (String[]) input; }");
      var cast = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
      var operand = cast.requiredChild(SyntaxRole.TYPE_OPERAND);
      var wrong = new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "java.lang.Integer[]", "java.lang.Integer[]"));
      var children = partialParent ? cast.children() : cast.children().stream().map(child -> child.role() == SyntaxRole.TYPE_OPERAND
          ? new SyntaxChild(child.role(), child.position(), copyNode(operand, wrong, operand.value(), operand.children(), operand.semanticReference())) : child).toList();
      // Act
      var failure = assertThrows(IllegalArgumentException.class,
          () -> copyNode(cast, partialParent ? wrong : cast.type(), cast.value(), children, cast.semanticReference()));
      // Assert
      assertEquals("A known written body type must match its containing occurrence's type", failure.getMessage());
    }));
  }

  @Test
  void givenSelectedExecutableWithoutRecursiveSignature_whenReconciled_thenMissingEvidenceRemainsIncomplete() throws IOException {
    // Arrange
    var original = referenceResult("static int selected(int input) { return input; } java.util.function.IntUnaryOperator subject() { return Example::selected; }");
    var node = referenceNode(original);
    var value = node.value(SyntaxValue.MemberReference.class);
    var identityOnly = new SyntaxType.Resolved(value.selectedType().knownIdentity().orElseThrow());
    var changedValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), identityOnly, value.selection(), value.staticMember());
    var changed = copyNode(node, node.type(), changedValue, node.children(), node.semanticReference());
    // Act
    var admitted = AstValueAdmission.admit(withReplacedNodes(original, new IdentityHashMap<>(Map.of(node, changed)), Set.of()));
    var partial = reconciledResult(original, admitted);
    var decoded = AstCodec.decode(AstCodec.encode(partial), partial.identity());
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(identityOnly, referenceNode(decoded).value(SyntaxValue.MemberReference.class).selectedType());
    assertEquals(node.semanticReference(), referenceNode(decoded).semanticReference());
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableStructureWithContradictoryKnownReferenceFacts_whenCopiedOrDecoded_thenKnownIdentitiesStillRejectEachConflict() {
    return Stream.of("array-selected-executable", "method-selected-array", "target-array", "bound-clone-nonarray", "bound-array-named-clone", "written-array-nonarray", "constructed-array-identity")
        .map(fact -> DynamicTest.dynamicTest(fact, () -> {
          // Arrange
          var clone = fact.contains("clone") || fact.equals("written-array-nonarray");
          var bound = fact.startsWith("bound");
          var member = clone ? (bound ? "java.util.function.Supplier<int[]> subject(int[] values) { return values::clone; }"
              : "java.util.function.Function<int[],int[]> subject() { return int[]::clone; }")
              : fact.equals("method-selected-array") ? "java.util.function.ToIntFunction<String> subject() { return String::length; }"
                  : "java.util.function.IntFunction<int[]> subject() { return int[]::new; }";
          var result = referenceResult(member);
          var node = referenceNode(result);
          var qualifier = node.requiredChild(SyntaxRole.QUALIFIER);
          var value = node.value(SyntaxValue.MemberReference.class);
          var type = node.type();
          var qualifierType = qualifier.type();
          var changedValue = value;
          var expected = "A reference requires an executable selection or its exact array type";
          switch (fact) {
            case "array-selected-executable", "method-selected-array" -> changedValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
                new SyntaxType.StructureUnavailable(fact.equals("array-selected-executable") ? new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, "()void", "()void")
                    : new JavaTypeIdentity(JavaTypeCategory.ARRAY, "int[]", "int[]")), value.selection(), value.staticMember());
            case "target-array" -> {
              type = new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "int[]", "int[]"));
              expected = "A reference target must be a functional interface or intersection type";
            }
            case "bound-clone-nonarray", "written-array-nonarray" -> {
              qualifierType = new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String"));
              expected = fact.equals("written-array-nonarray") ? "A reference qualifier must retain its exact written form and constructed array type"
                  : "An intrinsic array clone reference requires its actual array qualifier";
            }
            case "bound-array-named-clone" -> {
              qualifierType = new SyntaxType.StructureUnavailable(((SyntaxType.Resolved) qualifier.type()).identity());
              changedValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), value.selectedType(), SyntaxValue.ReferenceSelection.EXECUTABLE, false);
              expected = "A reference selection must agree with its actual array qualifier";
            }
            case "constructed-array-identity" -> {
              changedValue = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
                  new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "java.lang.String[]", "java.lang.String[]")), value.selection(), false);
              expected = "A reference qualifier must retain its exact written form and constructed array type";
            }
            default -> throw new AssertionError(fact);
          }
          if (fact.equals("written-array-nonarray")) {
            // Act
            var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
            new RetainedValueCodec(false).write(writer, qualifier);
            var changedType = qualifierType;
            var copying = assertThrows(IllegalArgumentException.class,
                () -> copyNode(qualifier, changedType, qualifier.value(), qualifier.children(), qualifier.semanticReference()));
            var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false)
                .read(new CanonicalValueReader(replaceNodeTypeFrame(writer.toByteArray(), qualifier, changedType), AstCodec.FORMAT_VERSION)));
            var envelope = assertThrows(AstContractException.class,
                () -> AstCodec.decode(replaceNodeTypeFrame(AstCodec.encode(result), qualifier, changedType), result.identity()));
            // Assert
            assertEquals("Written type-use kind conflicts with known attributed category", copying.getMessage());
            assertEquals(copying.getMessage(), decoding.getMessage());
            assertEquals(copying.getMessage(), envelope.getCause().getMessage());
            return;
          }
          var changedQualifier = copyNode(qualifier, qualifierType, qualifier.value(), qualifier.children(), qualifier.semanticReference());
          var children = List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, changedQualifier));
          var selectedValue = changedValue;
          var targetType = type;
          var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          new RetainedValueCodec(false).write(writer, node);
          var malformedNode = replaceNodeTypeFrame(replaceNodeValueFrame(writer.toByteArray(), node, changedValue), node, type);
          malformedNode = replaceNodeTypeFrame(malformedNode, qualifier, qualifierType);
          var malformedEnvelope = replaceNodeTypeFrame(replaceNodeValueFrame(AstCodec.encode(result), node, changedValue), node, type);
          malformedEnvelope = replaceNodeTypeFrame(malformedEnvelope, qualifier, qualifierType);
          var nodeBytes = malformedNode;
          var envelopeBytes = malformedEnvelope;
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(node, targetType, selectedValue, children, node.semanticReference()));
          var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false).read(new CanonicalValueReader(nodeBytes, AstCodec.FORMAT_VERSION)));
          var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(envelopeBytes, result.identity()));
          // Assert
          assertEquals(expected, copying.getMessage());
          assertEquals(copying.getMessage(), decoding.getMessage());
          assertEquals(copying.getMessage(), envelope.getCause().getMessage());
          assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableNonArrayQualifier_whenArraySelectionIsSupplied_thenCopyAndBothDecodersRejectKnownWrittenContradictions() {
    return Stream.of(false, true).map(selectedKnown -> DynamicTest.dynamicTest("selected array known=" + selectedKnown, () -> {
      // Arrange
      var result = referenceResult("java.util.function.Supplier<Example> subject() { return Example::new; }");
      var node = referenceNode(result);
      var qualifier = node.requiredChild(SyntaxRole.QUALIFIER);
      var unavailable = copyNode(qualifier, SyntaxType.Unresolved.INSTANCE, qualifier.value(), qualifier.children(), qualifier.semanticReference());
      var children = List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, unavailable));
      var original = node.value(SyntaxValue.MemberReference.class);
      var partialValue = new SyntaxValue.MemberReference(original.name(), original.mode(), original.qualifier(), SyntaxType.Unresolved.INSTANCE, original.selection(), false);
      var truthful = copyNode(node, node.type(), partialValue, children, Optional.empty());
      var arrayType = referenceNode(referenceResult("java.util.function.IntFunction<int[]> subject() { return int[]::new; }")).value(SyntaxValue.MemberReference.class).selectedType();
      var contradictory = new SyntaxValue.MemberReference("new", SyntaxValue.ReferenceMode.CONSTRUCTOR, SyntaxValue.ReferenceQualifier.ARRAY_TYPE,
          selectedKnown ? arrayType : SyntaxType.Unresolved.INSTANCE, SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR, false);
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      replacements.put(qualifier, unavailable);
      replacements.put(node, truthful);
      var values = withReplacedNodes(result, replacements, Set.of(node.nodeId()));
      var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
      new RetainedValueCodec(false).write(writer, truthful);
      var malformedNode = replaceValueFrame(writer.toByteArray(), partialValue, contradictory);
      var malformedEnvelope = replaceValueFrame(payloadWithAggregate(result, values), partialValue, contradictory);
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(node, node.type(), contradictory, children, Optional.empty()));
      var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false).read(new CanonicalValueReader(malformedNode, AstCodec.FORMAT_VERSION)));
      var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(malformedEnvelope, result.identity()));
      // Assert
      assertEquals("A reference selection must agree with its actual array qualifier", copying.getMessage());
      assertEquals(copying.getMessage(), decoding.getMessage());
      assertEquals(copying.getMessage(), envelope.getCause().getMessage());
      assertEquals(truthful, AstValueAdmission.admit(values).node(node.nodeId()).orElseThrow());
    }));
  }

  @Test
  void givenUnavailableNonArrayTypeQualifier_whenIntrinsicCloneIsSupplied_thenCopyAndBothDecodersRejectTheWrittenContradiction() throws IOException {
    // Arrange
    var result = referenceResult("java.util.function.ToIntFunction<String> subject() { return String::length; }");
    var node = referenceNode(result);
    var qualifier = node.requiredChild(SyntaxRole.QUALIFIER);
    var unavailable = copyNode(qualifier, SyntaxType.Unresolved.INSTANCE, qualifier.value(), qualifier.children(), qualifier.semanticReference());
    var children = List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, unavailable));
    var original = node.value(SyntaxValue.MemberReference.class);
    var truthful = copyNode(node, node.type(), original, children, Optional.empty());
    var contradictory = new SyntaxValue.MemberReference("clone", SyntaxValue.ReferenceMode.METHOD, SyntaxValue.ReferenceQualifier.TYPE,
        original.selectedType(), SyntaxValue.ReferenceSelection.ARRAY_CLONE, false);
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    replacements.put(qualifier, unavailable);
    replacements.put(node, truthful);
    var values = withReplacedNodes(result, replacements, Set.of(node.nodeId()));
    var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
    new RetainedValueCodec(false).write(writer, truthful);
    // Act
    var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(node, node.type(), contradictory, children, Optional.empty()));
    var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false).read(new CanonicalValueReader(replaceValueFrame(writer.toByteArray(), original, contradictory), AstCodec.FORMAT_VERSION)));
    var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(replaceValueFrame(payloadWithAggregate(result, values), original, contradictory), result.identity()));
    // Assert
    assertEquals("An intrinsic array clone reference requires its actual array qualifier", copying.getMessage());
    assertEquals(copying.getMessage(), decoding.getMessage());
    assertEquals(copying.getMessage(), envelope.getCause().getMessage());
    assertEquals(truthful, AstValueAdmission.admit(values).node(node.nodeId()).orElseThrow());
  }

  @TestFactory
  Stream<DynamicTest> givenKnownNonClassConstructorQualifier_whenKnownSelectionIsSupplied_thenCopyAndBothDecodersRejectWithoutErasingCompilerErrors() {
    return Stream.of("java.util.function.Supplier<Integer> subject() { return int::new; }",
        "<T> java.util.function.Supplier<T> subject() { return T::new; }")
        .flatMap(member -> Stream.of("resolved", "unresolved", "structure-unavailable").flatMap(state -> Stream.of(false, true)
            .map(selectionPartial -> DynamicTest.dynamicTest(member + " qualifier " + state + " partial selection " + selectionPartial, () -> {
      // Arrange
      var result = referenceResult(member);
      var node = referenceNode(result);
      var qualifier = node.requiredChild(SyntaxRole.QUALIFIER);
      var qualifierType = switch (state) {
        case "unresolved" -> SyntaxType.Unresolved.INSTANCE;
        case "structure-unavailable" -> new SyntaxType.StructureUnavailable(((SyntaxType.Resolved) qualifier.type()).identity());
        default -> qualifier.type();
      };
      var partialQualifier = copyNode(qualifier, qualifierType, qualifier.value(), qualifier.children(), qualifier.semanticReference());
      var children = List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, partialQualifier));
      var truthfulNode = copyNode(node, node.type(), node.value(), children, node.semanticReference());
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      replacements.put(qualifier, partialQualifier);
      replacements.put(node, truthfulNode);
      var truthfulValues = withReplacedNodes(result, replacements, Set.of());
      var value = node.value(SyntaxValue.MemberReference.class);
      var selectedType = referenceNode(referenceResult("java.util.function.Supplier<Example> subject() { return Example::new; }")).value(SyntaxValue.MemberReference.class).selectedType();
      if (selectionPartial) selectedType = new SyntaxType.StructureUnavailable(((SyntaxType.Resolved) selectedType).identity());
      var contradictory = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), selectedType, value.selection(), false);
      var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
      new RetainedValueCodec(false).write(writer, truthfulNode);
      var malformedNode = replaceValueFrame(writer.toByteArray(), value, contradictory);
      var malformedEnvelope = replaceNodeValueFrame(payloadWithAggregate(result, truthfulValues), truthfulNode, contradictory);
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(node, node.type(), contradictory, children, node.semanticReference()));
      var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false).read(new CanonicalValueReader(malformedNode, AstCodec.FORMAT_VERSION)));
      var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(malformedEnvelope, result.identity()));
      var truthful = AstCodec.decode(AstCodec.encode(result), result.identity());
      // Assert
      assertEquals("A selected class constructor requires a declared qualifier type", copying.getMessage());
      assertEquals(copying.getMessage(), decoding.getMessage());
      assertEquals(copying.getMessage(), envelope.getCause().getMessage());
      assertEquals(AstCompletion.INCOMPLETE, truthful.completion());
      assertEquals(SyntaxType.Unresolved.INSTANCE, value.selectedType());
      assertTrue(truthful.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")));
      assertEquals(result.aggregate(), truthful.aggregate());
      assertEquals(truthfulNode, AstValueAdmission.admit(truthfulValues).node(node.nodeId()).orElseThrow());
    }))));
  }

  @TestFactory
  Stream<DynamicTest> givenOwnedSelectedMethod_whenStaticFactIsReversed_thenCopiedAggregateAndFullDecoderRejectBothDirections() {
    return Stream.of(false, true).map(staticMember -> DynamicTest.dynamicTest("owned static=" + staticMember, () -> {
      // Arrange
      var result = referenceResult((staticMember ? "static " : "") + "int selected() { return 1; } "
          + (staticMember ? "java.util.function.IntSupplier" : "java.util.function.ToIntFunction<Example>") + " subject() { return Example::selected; }");
      var node = referenceNode(result);
      var value = node.value(SyntaxValue.MemberReference.class);
      var altered = new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(), value.selectedType(), value.selection(), !staticMember);
      var changed = copyNode(node, node.type(), altered, node.children(), node.semanticReference());
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      replacements.put(node, changed);
      var malformed = withReplacedNodes(result, replacements, Set.of());
      var payload = payloadWithAggregate(result, malformed);
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(malformed));
      var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payload, result.identity()));
      // Assert
      assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
      assertEquals(staticMember, value.staticMember());
      assertTrue(copying.getMessage().contains("member reference must agree with its owned executable static modifier"));
      assertEquals(copying.getMessage(), decoding.getCause().getMessage());
      assertEquals(result.aggregate(), AstValueAdmission.admit(SourceValueCapture.capture(result.aggregate())));
      assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenOwnedSelectedMethod_whenWrittenNameDisagrees_thenCopiedAggregateAndFullDecoderRejectBothModifierForms() {
    return Stream.of(false, true).map(staticMember -> DynamicTest.dynamicTest("owned static=" + staticMember, () -> {
      // Arrange
      var result = referenceResult((staticMember ? "static " : "") + "int selected() { return 1; } "
          + (staticMember ? "java.util.function.IntSupplier" : "java.util.function.ToIntFunction<Example>") + " subject() { return Example::selected; }");
      var node = referenceNode(result);
      var value = node.value(SyntaxValue.MemberReference.class);
      var altered = new SyntaxValue.MemberReference("different", value.mode(), value.qualifier(), value.selectedType(), value.selection(), value.staticMember());
      var changed = copyNode(node, node.type(), altered, node.children(), node.semanticReference());
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      replacements.put(node, changed);
      var malformed = withReplacedNodes(result, replacements, Set.of());
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(malformed));
      var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payloadWithAggregate(result, malformed), result.identity()));
      // Assert
      assertTrue(copying.getMessage().contains("member reference must agree with its owned executable name"));
      assertEquals(copying.getMessage(), decoding.getCause().getMessage());
      assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
    }));
  }

  @Test
  void givenInheritedOwnedSelection_whenQualifierNamesASubclass_thenCanonicalNameAndClosedValuesRemainValid() throws IOException {
    // Arrange
    var result = referenceResult("static class Parent { int selected() { return 1; } } static class Child extends Parent {} "
        + "java.util.function.ToIntFunction<Child> subject() { return Child::selected; }");
    // Act
    var copied = AstValueAdmission.admit(SourceValueCapture.capture(result.aggregate()));
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(result.aggregate(), copied);
    assertEquals(result.aggregate(), decoded.aggregate());
    assertEquals("selected", referenceNode(decoded).value(SyntaxValue.MemberReference.class).name());
  }

  @TestFactory
  Stream<DynamicTest> givenKnownNonClassQualifierWithMissingSelectedType_whenNamedConstructorIsResolved_thenCopyAndBothDecodersRejectKnownSelection() {
    return Stream.of("java.util.function.Supplier<Integer> subject() { return int::new; }", "<T> java.util.function.Supplier<T> subject() { return T::new; }")
        .flatMap(member -> Stream.of("resolved", "unresolved", "structure-unavailable").map(state -> DynamicTest.dynamicTest(member + " qualifier " + state, () -> {
          // Arrange
          var result = referenceResult(member);
          var node = referenceNode(result);
          var qualifier = node.requiredChild(SyntaxRole.QUALIFIER);
          var qualifierType = switch (state) {
            case "unresolved" -> SyntaxType.Unresolved.INSTANCE;
            case "structure-unavailable" -> new SyntaxType.StructureUnavailable(((SyntaxType.Resolved) qualifier.type()).identity());
            default -> qualifier.type();
          };
          var partialQualifier = copyNode(qualifier, qualifierType, qualifier.value(), qualifier.children(), qualifier.semanticReference());
          var children = List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, partialQualifier));
          var truthful = copyNode(node, node.type(), node.value(), children, node.semanticReference());
          var selectedReference = referenceNode(referenceResult("java.util.function.Supplier<Example> subject() { return Example::new; }")).semanticReference();
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          replacements.put(qualifier, partialQualifier);
          replacements.put(node, truthful);
          var values = withReplacedNodes(result, replacements, Set.of());
          var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          new RetainedValueCodec(false).write(writer, truthful);
          var malformedNode = withSelectedReference(writer.toByteArray(), truthful, selectedReference);
          var malformedEnvelope = withSelectedReference(payloadWithAggregate(result, values), truthful, selectedReference);
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(node, node.type(), node.value(), children, selectedReference));
          var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false).read(new CanonicalValueReader(malformedNode, AstCodec.FORMAT_VERSION)));
          var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(malformedEnvelope, result.identity()));
          // Assert
          assertEquals(SyntaxType.Unresolved.INSTANCE, node.value(SyntaxValue.MemberReference.class).selectedType());
          assertEquals(ResolutionStatus.RESOLVED, selectedReference.orElseThrow().status());
          assertEquals("A selected class constructor requires a declared qualifier type", copying.getMessage());
          assertEquals(copying.getMessage(), decoding.getMessage());
          assertEquals(copying.getMessage(), envelope.getCause().getMessage());
          assertEquals(truthful, AstValueAdmission.admit(values).node(node.nodeId()).orElseThrow());
          assertEquals(AstCompletion.INCOMPLETE, AstCodec.decode(AstCodec.encode(result), result.identity()).completion());
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableArrayAttribution_whenKnownComponentsConflictWithConstruction_thenCopyAndBothDecodersRejectExactComponentAndDimensionChanges() {
    return Stream.of("int[]", "int[][]", "String @Mark []").map(written -> DynamicTest.dynamicTest(written, () -> {
      // Arrange
      var result = referenceResult("@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Mark {} "
          + "java.util.function.IntFunction<" + (written.startsWith("String") ? "String[]" : written) + "> subject() { return " + written + "::new; }");
      var node = referenceNode(result);
      var qualifier = node.requiredChild(SyntaxRole.QUALIFIER);
      var unavailable = copyNode(qualifier, SyntaxType.Unresolved.INSTANCE, qualifier.value(), qualifier.children(), qualifier.semanticReference());
      var children = List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, unavailable));
      var truthful = copyNode(node, node.type(), node.value(), children, node.semanticReference());
      var original = node.value(SyntaxValue.MemberReference.class);
      var different = referenceNode(referenceResult("java.util.function.IntFunction<" + (written.equals("int[]") ? "String[]" : "int[]") + "> subject() { return " + (written.equals("int[]") ? "String[]" : "int[]") + "::new; }")).value(SyntaxValue.MemberReference.class).selectedType();
      var contradictory = new SyntaxValue.MemberReference(original.name(), original.mode(), original.qualifier(), different, original.selection(), false);
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      replacements.put(qualifier, unavailable);
      replacements.put(node, truthful);
      var values = withReplacedNodes(result, replacements, Set.of());
      var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
      new RetainedValueCodec(false).write(writer, truthful);
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(node, node.type(), contradictory, children, node.semanticReference()));
      var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false).read(new CanonicalValueReader(replaceValueFrame(writer.toByteArray(), original, contradictory), AstCodec.FORMAT_VERSION)));
      var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(replaceValueFrame(payloadWithAggregate(result, values), original, contradictory), result.identity()));
      // Assert
      assertEquals("A selected type must agree with its known written components", copying.getMessage());
      assertEquals(copying.getMessage(), decoding.getMessage());
      assertEquals(copying.getMessage(), envelope.getCause().getMessage());
      assertEquals(truthful, AstValueAdmission.admit(values).node(node.nodeId()).orElseThrow());
    }));
  }

  private AstResult referenceResult(String members) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "class Example { " + members + " }")));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
  }

  @TestFactory
  Stream<DynamicTest> givenOwnedSelectedExecutable_whenCanonicalKindIsReversed_thenAdmissionAndReconciledClosedEnvelopeRejectBothDirections() {
    return Stream.of(false, true).flatMap(constructor -> Stream.of(false, true).map(missingType -> DynamicTest.dynamicTest(
        "constructor=" + constructor + " missing selected type=" + missingType, () -> {
          // Arrange
          var original = referenceResult(constructor
              ? "Example() {} int selected() { return 1; } java.util.function.Supplier<Example> subject() { return Example::new; }"
              : "Example() {} int Example() { return 1; } java.util.function.ToIntFunction<Example> subject() { return Example::Example; }");
          var node = referenceNode(original);
          var value = node.value(SyntaxValue.MemberReference.class);
          var partialValue = missingType ? new SyntaxValue.MemberReference(value.name(), value.mode(), value.qualifier(),
              SyntaxType.Unresolved.INSTANCE, value.selection(), value.staticMember()) : value;
          var truthful = copyNode(node, node.type(), partialValue, node.children(), node.semanticReference());
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          replacements.put(node, truthful);
          var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
          var wrong = original.aggregate().nodes().stream().filter(candidate -> candidate.value() instanceof SyntaxValue.ExecutableDeclaration)
              .map(candidate -> candidate.value(SyntaxValue.ExecutableDeclaration.class))
              .filter(declaration -> constructor ? declaration.name().equals("selected")
                  : declaration.kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR).findFirst().orElseThrow();
          var selected = truthful.requiredReference();
          var contradiction = new SemanticReference(selected.referenceKind(), selected.status(), Optional.of(wrong.declarationKey()),
              selected.typeName(), selected.typeAlternativeNames(), selected.assignableToTypeNames(), Optional.empty());
          var changed = copyNode(truthful, truthful.type(), truthful.value(), truthful.children(), Optional.of(contradiction));
          replacements.clear();
          replacements.put(referenceNode(positive), changed);
          var values = withReplacedNodes(positive, replacements, Set.of(changed.nodeId()));
          values = withAdditionalEdge(values, changed.semanticEdges().getFirst());
          var malformed = values;
          var coverage = alteredReferenceCoverage(positive, changed.nodeId(), Optional.of(contradiction), Optional.empty());
          var payload = payloadWithAggregate(positive, malformed, coverage);
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(malformed));
          var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payload, positive.identity()));
          var accepted = AstCodec.decode(AstCodec.encode(positive), positive.identity());
          // Assert
          assertTrue(copying.getMessage().contains("member reference must agree with its owned executable kind"));
          assertEquals(copying.getMessage(), decoding.getCause().getMessage());
          assertEquals(missingType ? AstCompletion.INCOMPLETE : AstCompletion.COMPLETE, accepted.completion());
          assertEquals(positive.aggregate(), accepted.aggregate());
          assertEquals(partialValue, referenceNode(accepted).value());
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenPartialKnownArrayComponent_whenSelectedTypeContradictsAvailableFacts_thenPublicCopyAndBothClosedBoundariesReject() {
    return Stream.of("category", "nominal-key", "nominal-key-only", "type-parameter", "type-parameter-key-only", "different-type-parameter-key")
        .map(fact -> DynamicTest.dynamicTest(fact, () -> {
          // Arrange
          var variable = fact.contains("type-parameter");
          var original = referenceResult(variable
              ? "<T> java.util.function.IntFunction<T[]> subject() { return T[]::new; }"
              : "java.util.function.IntFunction<String[]> subject() { return String[]::new; }");
          var node = referenceNode(original);
          var qualifier = node.requiredChild(SyntaxRole.QUALIFIER);
          var component = qualifier.requiredChild(SyntaxRole.COMPONENT_TYPE);
          var partialType = fact.endsWith("key-only") ? SyntaxType.Unresolved.INSTANCE
              : new SyntaxType.StructureUnavailable(component.type().knownIdentity().orElseThrow());
          var partialComponent = copyNode(component, partialType, component.value(), component.children(), component.semanticReference());
          var partialQualifier = copyNode(qualifier, SyntaxType.Unresolved.INSTANCE, qualifier.value(),
              List.of(new SyntaxChild(SyntaxRole.COMPONENT_TYPE, 0, partialComponent)), qualifier.semanticReference());
          var originalValue = node.value(SyntaxValue.MemberReference.class);
          var matchingValue = variable ? new SyntaxValue.MemberReference(originalValue.name(), originalValue.mode(), originalValue.qualifier(),
              qualifier.type(), originalValue.selection(), false) : originalValue;
          var children = List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, partialQualifier));
          var truthful = copyNode(node, node.type(), matchingValue, children, node.semanticReference());
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          replacements.put(component, partialComponent);
          replacements.put(qualifier, partialQualifier);
          replacements.put(node, truthful);
          var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
          var other = referenceResult(fact.equals("different-type-parameter-key")
              ? "<U> java.util.function.IntFunction<U[]> alternative() { return U[]::new; }"
              : "java.util.function.IntFunction<" + (fact.equals("category") ? "int[]" : "Integer[]")
                  + "> subject() { return " + (fact.equals("category") ? "int[]" : "Integer[]") + "::new; }");
          var otherNode = referenceNode(other);
          var selectedType = fact.equals("different-type-parameter-key") ? otherNode.requiredChild(SyntaxRole.QUALIFIER).type()
              : otherNode.value(SyntaxValue.MemberReference.class).selectedType();
          if (fact.equals("different-type-parameter-key")) {
            var selected = (JavaTypeStructure.Array) ((SyntaxType.Resolved) selectedType).structure().orElseThrow();
            assertNotEquals(component.requiredReference().symbolKey().orElseThrow(), ((JavaTypeStructure.Variable) selected.component()).parameterKey());
          }
          var contradictory = new SyntaxValue.MemberReference(matchingValue.name(), matchingValue.mode(), matchingValue.qualifier(),
              selectedType, matchingValue.selection(), false);
          var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          new RetainedValueCodec(false).write(writer, referenceNode(positive));
          var malformedNode = replaceNodeValueFrame(writer.toByteArray(), referenceNode(positive), contradictory);
          var coverageValue = referenceCoverageValue(other, otherNode.nodeId());
          if (fact.equals("different-type-parameter-key")) {
            var knownArray = referenceCoverageValue(reconciledResult(other, AstValueAdmission.admit(withReplacedNodes(other,
                new IdentityHashMap<>(Map.of(otherNode, copyNode(otherNode, otherNode.type(), contradictory, otherNode.children(), otherNode.semanticReference()))), Set.of()))), otherNode.nodeId());
            coverageValue = knownArray;
          }
          var changedCoverage = alteredReferenceCoverage(positive, truthful.nodeId(), truthful.semanticReference(), Optional.of(coverageValue));
          var values = SourceValueCapture.capture(positive.aggregate());
          var payload = replaceNodeValueFrame(payloadWithAggregate(positive, values, changedCoverage), referenceNode(positive), contradictory);
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> copyNode(node, node.type(), contradictory, children, node.semanticReference()));
          var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false)
              .read(new CanonicalValueReader(malformedNode, AstCodec.FORMAT_VERSION)));
          var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(payload, positive.identity()));
          var accepted = AstCodec.decode(AstCodec.encode(positive), positive.identity());
          // Assert
          assertEquals("A selected type must agree with its known written components", copying.getMessage());
          assertEquals(copying.getMessage(), decoding.getMessage());
          assertEquals(copying.getMessage(), envelope.getCause().getMessage());
          assertEquals(AstCompletion.INCOMPLETE, accepted.completion());
          assertEquals(positive.aggregate(), accepted.aggregate());
          assertEquals(partialType, referenceNode(accepted).requiredChild(SyntaxRole.QUALIFIER).requiredChild(SyntaxRole.COMPONENT_TYPE).type());
          assertEquals(component.semanticReference(), referenceNode(accepted).requiredChild(SyntaxRole.QUALIFIER)
              .requiredChild(SyntaxRole.COMPONENT_TYPE).semanticReference());
        }));
  }

  @Test
  void givenEntirelyMissingArrayComponentAttribution_whenCopiedAndReconciled_thenNoContradictionIsInvented() throws IOException {
    // Arrange
    var original = referenceResult("java.util.function.IntFunction<String[]> subject() { return String[]::new; }");
    var node = referenceNode(original);
    var qualifier = node.requiredChild(SyntaxRole.QUALIFIER);
    var component = qualifier.requiredChild(SyntaxRole.COMPONENT_TYPE);
    var missing = copyNode(component, SyntaxType.Unresolved.INSTANCE, component.value(), component.children(), Optional.empty());
    var partial = copyNode(qualifier, SyntaxType.Unresolved.INSTANCE, qualifier.value(),
        List.of(new SyntaxChild(SyntaxRole.COMPONENT_TYPE, 0, missing)), qualifier.semanticReference());
    var changed = copyNode(node, node.type(), node.value(), List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, partial)), node.semanticReference());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    replacements.put(component, missing);
    replacements.put(qualifier, partial);
    replacements.put(node, changed);
    // Act
    var admitted = AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of(component.nodeId())));
    var positive = reconciledResult(original, admitted);
    var decoded = AstCodec.decode(AstCodec.encode(positive), positive.identity());
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(admitted, decoded.aggregate());
    var retained = referenceNode(decoded).requiredChild(SyntaxRole.QUALIFIER).requiredChild(SyntaxRole.COMPONENT_TYPE);
    assertEquals(SyntaxType.Unresolved.INSTANCE, retained.type());
    assertTrue(retained.semanticReference().isEmpty());
    assertEquals(component.value(), retained.value());
    assertEquals(component.origin(), retained.origin());
  }

  private static AstResult reconciledResult(AstResult original, JavaAstAggregate aggregate) {
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    var reconciled = JavaCoverageTestSupport.capture(input, original.profile()).withFacts(input.program(), Optional.of(aggregate));
    return AstProductionAdmission.outcome(reconciled, original.profile(), original.identity().semanticProductionRevision()).result().orElseThrow();
  }

  @TestFactory
  Stream<DynamicTest> givenOwnedConstructorSelection_whenKnownQualifierNamesAnotherType_thenCanonicalOwnerRejectsSimpleAndParameterizedForms() {
    return Stream.of("Constructed", "Constructed<String>").flatMap(qualifier -> Stream.of("resolved", "structure-unavailable", "unresolved")
        .map(state -> DynamicTest.dynamicTest(qualifier + " " + state, () -> {
      // Arrange
      var original = referenceResult("static class Constructed<T> { Constructed() {} } static class Other { Other() {} } "
          + "java.util.function.Supplier<" + qualifier + "> subject() { return " + qualifier + "::new; }");
      var node = referenceNode(original);
      var written = node.requiredChild(SyntaxRole.QUALIFIER);
      var partialType = switch (state) {
        case "structure-unavailable" -> new SyntaxType.StructureUnavailable(written.type().knownIdentity().orElseThrow());
        case "unresolved" -> SyntaxType.Unresolved.INSTANCE;
        default -> written.type();
      };
      var partial = copyNode(written, partialType, written.value(), written.children(), written.semanticReference());
      var truthful = copyNode(node, node.type(), node.value(), List.of(new SyntaxChild(SyntaxRole.QUALIFIER, 0, partial)), node.semanticReference());
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      replacements.put(written, partial);
      replacements.put(node, truthful);
      var positive = reconciledResult(original, AstValueAdmission.admit(withReplacedNodes(original, replacements, Set.of())));
      var wrong = original.aggregate().nodes().stream().filter(candidate -> candidate.value() instanceof SyntaxValue.ExecutableDeclaration)
          .map(candidate -> candidate.value(SyntaxValue.ExecutableDeclaration.class))
          .filter(declaration -> declaration.kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR
              && declaration.name().equals("Other")).findFirst().orElseThrow();
      var selected = node.requiredReference();
      var contradiction = new SemanticReference(selected.referenceKind(), selected.status(), Optional.of(wrong.declarationKey()),
          selected.typeName(), selected.typeAlternativeNames(), selected.assignableToTypeNames(), Optional.empty());
      var changed = copyNode(truthful, truthful.type(), truthful.value(), truthful.children(), Optional.of(contradiction));
      replacements.clear();
      replacements.put(referenceNode(positive), changed);
      var values = withAdditionalEdge(withReplacedNodes(positive, replacements, Set.of(node.nodeId())), changed.semanticEdges().getFirst());
      var coverage = alteredReferenceCoverage(positive, node.nodeId(), Optional.of(contradiction), Optional.empty());
      // Act
      var copying = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(values));
      var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payloadWithAggregate(positive, values, coverage), positive.identity()));
      var accepted = AstCodec.decode(AstCodec.encode(positive), positive.identity());
      // Assert
      assertTrue(copying.getMessage().contains("member reference must agree with its owned constructor type"));
      assertEquals(copying.getMessage(), decoding.getCause().getMessage());
      assertEquals(state.equals("resolved") ? AstCompletion.COMPLETE : AstCompletion.INCOMPLETE, accepted.completion(), accepted.diagnostics().toString());
      assertEquals(positive.aggregate(), accepted.aggregate());
    })));
  }

  private static CoverageEvidenceValue.Structure referenceCoverageValue(AstResult result, String nodeKey) {
    return result.coverage().validationEvidence().outcomes().stream().flatMap(outcome -> outcome.support().facts().stream())
        .filter(fact -> fact.identity().equals(nodeKey)).map(CoverageFactReference::value).filter(CoverageFactValue.Source.class::isInstance)
        .map(CoverageFactValue.Source.class::cast).map(CoverageFactValue.Source::value).map(CoverageSourceFact::value).findFirst().orElseThrow();
  }

  private static LanguageFeatureCoverageResult alteredReferenceCoverage(AstResult result, String nodeKey,
      Optional<SemanticReference> selection, Optional<CoverageEvidenceValue.Structure> replacementValue) {
    var evidence = result.coverage().validationEvidence();
    var outcomes = evidence.outcomes().stream().map(outcome -> {
      var support = outcome.support();
      var facts = support.facts().stream().map(fact -> {
        if (!fact.identity().equals(nodeKey) || !(fact.value() instanceof CoverageFactValue.Source source)) return fact;
        var value = source.value();
        return new CoverageFactReference(fact.kind(), fact.identity(), fact.sourceFileKey(), new CoverageFactValue.Source(
            new CoverageSourceFact(value.structuralPath(), value.sourceKind(), value.syntaxKind(), value.type(),
                replacementValue.orElse(value.value()), value.children(), selection, value.sourceAssociation())), fact.origin());
      }).toList();
      return new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
          new FeatureContextOutcome.Support(support.mode(), facts, support.policyContext(), support.sourceFile(), support.negativeState()), outcome.emissions());
    }).toList();
    return CoverageAdmission.fromEvidence(result.coverage().coverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());
  }

  private static SyntaxNode referenceNode(AstResult result) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).findFirst().orElseThrow();
  }

  private static SyntaxNode copyNode(SyntaxNode original, SyntaxType type, SyntaxValue value, List<SyntaxChild> children, Optional<SemanticReference> reference) {
    return original.origin().isPresent()
        ? SyntaxNode.source(original.sourceFileKey(), original.structuralPath(), original.sourceKind(), original.syntaxKind(), type, value, children, reference, original.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(original.sourceFileKey(), original.structuralPath(), original.sourceKind(), original.syntaxKind(), type, value, children, reference);
  }

  private static CopiedAstValues withReplacedNodes(AstResult result, Map<SyntaxNode, SyntaxNode> replacements, Set<String> removedReferenceSources) {
    var captured = SourceValueCapture.capture(result.aggregate());
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(), replaceNode(source.root(), replacements),
        source.nodes().stream().map(node -> replaceNode(node, replacements)).toList(), source.structuralEdges(),
        source.semanticEdges().stream().filter(edge -> !removedReferenceSources.contains(edge.sourceKey())).toList())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), replaceNode(body.root(), replacements), body.origin(), body.uses(), body.invocations(),
        body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    return new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes(), captured.scopes());
  }

  private static SyntaxNode replaceNode(SyntaxNode original, Map<SyntaxNode, SyntaxNode> replacements) {
    var previous = replacements.get(original);
    if (previous != null) return previous;
    var children = original.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replaceNode(child.node(), replacements))).toList();
    var unchanged = IntStream.range(0, children.size()).allMatch(index -> children.get(index).node() == original.children().get(index).node());
    var changed = unchanged ? original : copyNode(original, original.type(), original.value(), children, original.semanticReference());
    replacements.put(original, changed);
    return changed;
  }

  @Test
  void givenAnnotatedWrittenArrayWithUnavailableQualifierType_whenNamedSelectionIsSupplied_thenWrittenArrayFactsStillRejectTheContradiction() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface Mark {} "
            + "class Example { java.util.function.IntFunction<int[]> subject() { return int @Mark []::new; } }")));
    var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var node = result.aggregate().nodes().stream().filter(candidate -> candidate.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).findFirst().orElseThrow();
    var qualifier = node.requiredChild(SyntaxRole.QUALIFIER);
    var unavailable = SyntaxNode.source(qualifier.sourceFileKey(), qualifier.structuralPath(), qualifier.sourceKind(), qualifier.syntaxKind(),
        SyntaxType.Unresolved.INSTANCE, qualifier.value(), qualifier.children(), qualifier.semanticReference(), qualifier.origin().orElseThrow());
    var children = node.children().stream().map(child -> child.role() == SyntaxRole.QUALIFIER ? new SyntaxChild(child.role(), child.position(), unavailable) : child).toList();
    var truthful = SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children, node.semanticReference(), node.origin().orElseThrow());
    var reference = node.value(SyntaxValue.MemberReference.class);
    var contradictory = new SyntaxValue.MemberReference("new", SyntaxValue.ReferenceMode.CONSTRUCTOR, SyntaxValue.ReferenceQualifier.TYPE,
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, "(int)int[]", "(int)int[]")), SyntaxValue.ReferenceSelection.EXECUTABLE, false);
    var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
    new RetainedValueCodec(false).write(writer, truthful);
    var malformed = replaceValueFrame(writer.toByteArray(), reference, contradictory);
    // Act
    var copying = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(),
        node.type(), contradictory, children, node.semanticReference(), node.origin().orElseThrow()));
    var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false).read(new CanonicalValueReader(malformed, AstCodec.FORMAT_VERSION)));
    var copied = (SyntaxNode) new RetainedValueCodec(false).read(new CanonicalValueReader(writer.toByteArray(), AstCodec.FORMAT_VERSION));
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(JavaSourceKind.ANNOTATED_TYPE, qualifier.sourceKind());
    assertEquals("A reference selection must agree with its actual array qualifier", copying.getMessage());
    assertEquals(copying.getMessage(), decoding.getMessage());
    assertEquals(SyntaxType.Unresolved.INSTANCE, copied.requiredChild(SyntaxRole.QUALIFIER).type());
    assertEquals(SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR, copied.value(SyntaxValue.MemberReference.class).selection());
  }

  @TestFactory
  Stream<DynamicTest> givenSelectionsContradictingKnownQualifiers_whenCopiedOrDecoded_thenIntrinsicAdmissionRejectsThem() {
    return Stream.of(
        new ReferenceCase("java.util.function.IntFunction<int[]>", "int[]::new"),
        new ReferenceCase("java.util.function.Function<int[],int[]>", "int[]::clone"),
        new ReferenceCase("java.util.function.Supplier<int[]>", "array::clone"),
        new ReferenceCase("java.util.function.Supplier<Example>", "Example::new"))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
              "class Example { " + variant.target() + " subject(int[] array) { return " + variant.expression() + "; } }")));
          var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
              AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
          var node = result.aggregate().nodes().stream().filter(candidate -> candidate.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).findFirst().orElseThrow();
          var reference = node.value(SyntaxValue.MemberReference.class);
          var namedConstructor = variant.expression().equals("Example::new");
          var altered = new SyntaxValue.MemberReference(reference.name(), reference.mode(),
              namedConstructor ? SyntaxValue.ReferenceQualifier.ARRAY_TYPE : reference.qualifier() == SyntaxValue.ReferenceQualifier.ARRAY_TYPE ? SyntaxValue.ReferenceQualifier.TYPE : reference.qualifier(),
              namedConstructor ? SyntaxType.Unresolved.INSTANCE : reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR
                  ? new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, "(int)int[]", "(int)int[]")) : reference.selectedType(),
              namedConstructor ? SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR : SyntaxValue.ReferenceSelection.EXECUTABLE, false);
          var nodeWriter = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          new RetainedValueCodec(false).write(nodeWriter, node);
          var copiedNode = namedConstructor ? withSelectedReference(nodeWriter.toByteArray(), node, Optional.empty()) : nodeWriter.toByteArray();
          var copiedEnvelope = namedConstructor ? withSelectedReference(AstCodec.encode(result), node, Optional.empty()) : AstCodec.encode(result);
          var malformedNode = replaceValueFrame(copiedNode, reference, altered);
          var malformedEnvelope = replaceValueFrame(copiedEnvelope, reference, altered);
          // Act
          var copying = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(),
              node.syntaxKind(), node.type(), altered, node.children(), namedConstructor ? Optional.empty() : node.semanticReference(), node.origin().orElseThrow()));
          var decoding = assertThrows(IllegalArgumentException.class, () -> new RetainedValueCodec(false).read(new CanonicalValueReader(malformedNode, AstCodec.FORMAT_VERSION)));
          var envelope = assertThrows(AstContractException.class, () -> AstCodec.decode(malformedEnvelope, result.identity()));
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals("A reference selection must agree with its actual array qualifier", copying.getMessage());
          assertEquals(copying.getMessage(), decoding.getMessage());
          assertEquals(copying.getMessage(), envelope.getCause().getMessage());
          assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
        }));
  }

  private static byte[] replaceValueFrame(byte[] bytes, Object original, Object replacement) {
    var originalWriter = new CanonicalValueWriter("");
    new RetainedValueCodec(false).write(originalWriter, original);
    var replacementWriter = new CanonicalValueWriter("");
    new RetainedValueCodec(false).write(replacementWriter, replacement);
    var originalFrame = Arrays.copyOfRange(originalWriter.toByteArray(), Integer.BYTES, originalWriter.toByteArray().length);
    var replacementFrame = Arrays.copyOfRange(replacementWriter.toByteArray(), Integer.BYTES, replacementWriter.toByteArray().length);
    return replaceExactFrame(bytes, originalFrame, replacementFrame);
  }

  private static Throwable deepestCause(Throwable failure) {
    var result = failure;
    while (result.getCause() != null) result = result.getCause();
    return result;
  }

  private static byte[] replaceNodeValueFrame(byte[] bytes, SyntaxNode node, SyntaxValue replacementValue) {
    var writer = new CanonicalValueWriter("");
    new RetainedValueCodec(false).write(writer, replacementValue);
    return replaceNodeValueFrame(bytes, node, Arrays.copyOfRange(writer.toByteArray(), Integer.BYTES, writer.toByteArray().length));
  }

  private static byte[] replaceNodeValueFrame(byte[] bytes, SyntaxNode node, byte[] replacementValue) {
    var original = new CanonicalValueWriter("");
    var replacement = new CanonicalValueWriter("");
    var codec = new RetainedValueCodec(false);
    writeNodePrefix(original, codec, node);
    writeNodePrefix(replacement, codec, node);
    original.text("type");
    replacement.text("type");
    codec.write(original, node.type());
    codec.write(replacement, node.type());
    original.text("value");
    replacement.text("value");
    codec.write(original, node.value());
    var replacementBytes = new ByteArrayOutputStream();
    replacementBytes.writeBytes(replacement.toByteArray());
    replacementBytes.writeBytes(replacementValue);
    return replaceExactFrame(bytes, Arrays.copyOfRange(original.toByteArray(), Integer.BYTES, original.toByteArray().length),
        Arrays.copyOfRange(replacementBytes.toByteArray(), Integer.BYTES, replacementBytes.toByteArray().length));
  }

  private static byte[] replaceNodeTypeFrame(byte[] bytes, SyntaxNode node, SyntaxType replacementType) {
    var original = new CanonicalValueWriter("");
    var replacement = new CanonicalValueWriter("");
    var codec = new RetainedValueCodec(false);
    writeNodePrefix(original, codec, node);
    writeNodePrefix(replacement, codec, node);
    original.text("type");
    replacement.text("type");
    codec.write(original, node.type());
    codec.write(replacement, replacementType);
    return replaceExactFrame(bytes, Arrays.copyOfRange(original.toByteArray(), Integer.BYTES, original.toByteArray().length),
        Arrays.copyOfRange(replacement.toByteArray(), Integer.BYTES, replacement.toByteArray().length));
  }

  private static void writeNodePrefix(CanonicalValueWriter writer, RetainedValueCodec codec, SyntaxNode node) {
    writer.count(9);
    writer.text("sourceFileKey");
    codec.write(writer, node.sourceFileKey());
    writer.text("structuralPath");
    codec.write(writer, node.structuralPath());
    writer.text("sourceKind");
    codec.write(writer, node.sourceKind());
    writer.text("syntaxKind");
    codec.write(writer, node.syntaxKind());
  }

  private static byte[] withSelectedReference(byte[] bytes, SyntaxNode node, Optional<SemanticReference> selectedReference) {
    var original = new CanonicalValueWriter("");
    var replacement = new CanonicalValueWriter("");
    var codec = new RetainedValueCodec(false);
    original.text("semanticReference");
    codec.write(original, node.semanticReference());
    original.text("origin");
    codec.write(original, node.origin());
    replacement.text("semanticReference");
    codec.write(replacement, selectedReference);
    replacement.text("origin");
    codec.write(replacement, node.origin());
    return replaceExactFrame(bytes, Arrays.copyOfRange(original.toByteArray(), Integer.BYTES, original.toByteArray().length),
        Arrays.copyOfRange(replacement.toByteArray(), Integer.BYTES, replacement.toByteArray().length));
  }

  private static byte[] replaceExactFrame(byte[] bytes, byte[] originalFrame, byte[] replacementFrame) {
    var offset = -1;
    var matches = 0;
    for (var index = 0; index <= bytes.length - originalFrame.length; index++) {
      if (Arrays.equals(bytes, index, index + originalFrame.length, originalFrame, 0, originalFrame.length)) {
        offset = index;
        matches++;
      }
    }
    assertEquals(1, matches, "The fixture contains exactly one selected reference value frame");
    var altered = new ByteArrayOutputStream();
    altered.write(bytes, 0, offset);
    altered.writeBytes(replacementFrame);
    altered.write(bytes, offset + originalFrame.length, bytes.length - offset - originalFrame.length);
    return altered.toByteArray();
  }

  @TestFactory
  Stream<DynamicTest> givenConstructorReferencesWithMethodOnlyQualifiers_whenConstructedOrDecoded_thenIntrinsicAdmissionRejectsEachContradiction() {
    return Stream.of(SyntaxValue.ReferenceQualifier.EXPRESSION, SyntaxValue.ReferenceQualifier.SUPER, SyntaxValue.ReferenceQualifier.QUALIFIED_SUPER)
        .map(qualifier -> DynamicTest.dynamicTest(qualifier.name(), () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
              "class Example { java.util.function.Supplier<Example> subject() { return Example::new; } }")));
          var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
              AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
          var value = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).findFirst().orElseThrow()
              .value(SyntaxValue.MemberReference.class);
          var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          var codec = new RetainedValueCodec(false);
          var shape = (Fields) AstValueSchema.shape(value);
          var components = shape.components(value, false);
          writer.text(shape.tag());
          writer.count(components.size());
          for (var index = 0; index < components.size(); index++) {
            var field = components.get(index).name();
            writer.text(field);
            codec.write(writer, field.equals("qualifier") ? qualifier : components.get(index).value());
          }
          // Act
          var construction = assertThrows(IllegalArgumentException.class, () -> new SyntaxValue.MemberReference(value.name(), value.mode(), qualifier,
              value.selectedType(), value.selection(), value.staticMember()));
          var decoding = assertThrows(IllegalArgumentException.class, () -> codec.read(new CanonicalValueReader(writer.toByteArray(), AstCodec.FORMAT_VERSION)));
          // Assert
          assertEquals("Reference mode, name and qualifier must agree", construction.getMessage());
          assertEquals(construction.getMessage(), decoding.getCause().getMessage());
          assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenExtraReferenceDeclarationEdges_whenCopiedOrFullyDecoded_thenSelectedAssociationConsistencyRejectsThem() {
    return Stream.of(
        new ReferenceCase("java.util.function.Supplier<Example>", "Example::selected"),
        new ReferenceCase("java.util.function.Supplier<Example>", "Example::new"),
        new ReferenceCase("java.util.function.IntFunction<int[]>", "int[]::new"),
        new ReferenceCase("java.util.function.Function<int[],int[]>", "int[]::clone"))
        .flatMap(variant -> Stream.of(false, true).map(occurrence -> DynamicTest.dynamicTest(variant.expression() + (occurrence ? " occurrence" : " declaration"), () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
              "class Example { Example() {} static Example selected() { return new Example(); } static void unrelated() {} "
                  + variant.target() + " subject() { return " + variant.expression() + "; } }")));
          var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
              AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
          var reference = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).findFirst().orElseThrow();
          var unrelated = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.METHOD_DECLARATION)
              .filter(node -> node.value(SyntaxValue.ExecutableDeclaration.class).name().equals("unrelated")).findFirst().orElseThrow();
          SyntaxSemanticTarget target = occurrence ? new SyntaxSemanticTarget.SourceOccurrence(unrelated.nodeId())
              : new SyntaxSemanticTarget.Declaration(new SemanticReference(ReferenceKind.METHOD, ResolutionStatus.RESOLVED,
                  Optional.of(unrelated.value(SyntaxValue.ExecutableDeclaration.class).declarationKey()), Optional.empty(), List.of(), List.of(), Optional.empty()));
          var captured = SourceValueCapture.capture(result.aggregate());
          var malformed = withAdditionalEdge(captured, new SyntaxSemanticEdge(reference.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE, target));
          var payload = payloadWithAggregate(result, malformed);
          // Act
          var admission = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(malformed));
          var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(payload, result.identity()));
          // Assert
          assertTrue(admission.getMessage().contains("declaration reference must match its selected target"), admission.toString());
          assertEquals(admission.getMessage(), decoding.getCause().getMessage());
          assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
          if (reference.semanticReference().isPresent()) {
            var selectedKey = reference.requiredReference().symbolKey().orElseThrow();
            var selected = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.METHOD_DECLARATION)
                .filter(node -> node.value(SyntaxValue.ExecutableDeclaration.class).declarationKey().equals(selectedKey)).findFirst().orElseThrow();
            var matching = withAdditionalEdge(captured, new SyntaxSemanticEdge(reference.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
                new SyntaxSemanticTarget.SourceOccurrence(selected.nodeId())));
            assertEquals(reference, AstValueAdmission.admit(matching).node(reference.nodeId()).orElseThrow());
          }
        })));
  }

  private record ReferenceCase(String target, String expression) {}

  @TestFactory
  Stream<DynamicTest> givenDetachedBodyWithCanonicalFormals_whenItsSelectionContradictsTheSignature_thenResultAndDecoderReject() {
    // Arrange
    var primitiveSignature = "int first(int value)";
    var referenceSignature = "Object first(java.util.List<?> value)";
    var arraySignature = "Object first(String[] value)";
    var cases = List.of(new FormalSelection("UnknownKey", primitiveSignature, "value", 1, Optional.empty()),
        new FormalSelection("WrongName", primitiveSignature, "different", 0, Optional.empty()),
        new FormalSelection("WrongPrimitiveType", primitiveSignature, "value", 0,
            Optional.of(new JavaTypeIdentity(JavaTypeCategory.CHAR, "char", "char"))),
        new FormalSelection("WrongDeclaredErasure", referenceSignature, "value", 0,
            Optional.of(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.util.Set<?>", "java.util.Set"))),
        new FormalSelection("WrongArrayErasure", arraySignature, "value", 0,
            Optional.of(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "java.lang.Integer[]", "java.lang.Integer[]"))));

    // Act
    var observations = cases.stream().map(value -> DynamicTest.dynamicTest("givenDetachedFormalWith" + value.variant()
        + "_whenTheResultIsAdmittedAndDecoded_thenTheCanonicalFormalIsRequired", () -> {
          // Arrange
          var original = formalResult(value.signature());
          var method = original.program().methods().getFirst();
          var values = SourceValueCapture.capture(original.aggregate());
          var positive = detachedBodyWithoutHeader(values, UnaryOperator.identity());
          var control = admitDetachedFormal(original, positive);
          var negative = detachedBodyWithoutHeader(values, node -> {
            var reference = node.semanticReference().orElseThrow();
            var typeName = value.type().map(JavaTypeIdentity::displayName);
            var erasedNames = value.type().map(JavaTypeIdentity::erasedName).stream().toList();
            var selected = new SemanticReference(reference.referenceKind(), reference.status(),
                Optional.of(method.methodKey() + ":parameter:" + value.ordinal()), typeName.or(reference::typeName),
                value.type().isPresent() ? erasedNames : reference.typeAlternativeNames(),
                value.type().isPresent() ? erasedNames : reference.assignableToTypeNames(), Optional.empty());
            return SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(),
                value.type().<SyntaxType>map(SyntaxType.Resolved::new).orElse(node.type()), new SyntaxValue.SourceSymbol(value.name()),
                node.children(), Optional.of(selected));
          });
          var aggregate = AstValueAdmission.admit(negative);
          var bytes = payloadWithAggregate(control, negative);

          // Act
          var decoded = AstCodec.decode(AstCodec.encode(control), control.identity());
          var copied = assertThrows(IllegalArgumentException.class, () -> AstProductionAdmission.outcome(
              new SyntaxAnalysisInput(control.snapshot(), control.environment(), control.program(), Optional.of(aggregate),
                  control.diagnostics(), control.coverage()), control.profile(), control.identity().semanticProductionRevision()));
          var retained = assertThrows(AstContractException.class, () -> AstCodec.decode(bytes, control.identity()));

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion());
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION, decoded.aggregate().methodBodyOrigin(method.methodKey()).orElseThrow());
          var operand = formalAccess(decoded);
          var parameter = method.parameters().getFirst();
          var operandType = operand.resolvedType().orElseThrow();
          assertEquals(parameter.parameterKey(), operand.requiredReference().symbolKey().orElseThrow());
          assertEquals(parameter.name(), operand.value(SyntaxValue.SourceSymbol.class).name());
          assertEquals(parameter.staticType().category(), operandType.category());
          assertEquals(parameter.staticType().erasedName(), operandType.erasedName());
          if (value.signature().equals(referenceSignature)) {
            assertNotEquals(parameter.staticType().displayName(), operandType.displayName());
            assertTrue(operandType.displayName().contains(" of ?"), operandType.displayName());
          }
          var diagnostic = value.type().isPresent() ? "detached formal reference type" : "exact canonical name and source";
          assertTrue(copied.getMessage().contains(diagnostic), copied.toString());
          var construction = assertInstanceOf(AstContractException.class, retained.getCause());
          assertEquals(copied.getMessage(), construction.getCause().getMessage());
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenDetachedFormalWithMissingOrErroneousType_whenTheResultIsAdmittedAndDecoded_thenTypeEvidenceRemainsExplicit() {
    // Arrange
    var evidence = List.of(SyntaxType.Unresolved.INSTANCE, SyntaxType.Erroneous.INSTANCE);

    // Act
    var observations = evidence.stream().map(type -> DynamicTest.dynamicTest("givenDetachedFormalWith" + type.getClass().getSimpleName()
        + "Type_whenTheResultIsAdmittedAndDecoded_thenTheTypeOutcomeIsRetained", () -> {
          // Arrange
          var original = formalResult("int first(int value)");
          var parameter = original.program().methods().getFirst().parameters().getFirst();
          var status = type == SyntaxType.Erroneous.INSTANCE ? ResolutionStatus.ERROR : ResolutionStatus.RESOLVED;
          var values = detachedBodyWithoutHeader(SourceValueCapture.capture(original.aggregate()), operand -> {
            var reference = new SemanticReference(ReferenceKind.PARAMETER, status, Optional.of(parameter.parameterKey()), Optional.empty(),
                List.of(), List.of(), Optional.empty());
            return SyntaxNode.withoutDirectSource(operand.sourceFileKey(), operand.structuralPath(), operand.sourceKind(), operand.syntaxKind(),
                type, operand.value(), operand.children(), Optional.of(reference));
          });

          // Act
          var result = admitDetachedFormal(original, values);
          var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
          var operand = formalAccess(decoded);

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(type, operand.type());
          assertEquals(status, operand.semanticReference().orElseThrow().status());
          assertEquals(Optional.of(parameter.parameterKey()), operand.semanticReference().orElseThrow().symbolKey());
        }));

    // Assert
    return observations;
  }

  private AstResult formalResult(String signature) throws IOException {
    var source = SourceFile.fromContent(capturedPath(Path.of("Example.java")), "interface Example { static " + signature + " { return value; } }");
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(source));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
  }

  private static AstResult admitDetachedFormal(AstResult original, CopiedAstValues values) {
    return AstProductionAdmission.outcome(JavaCoverageTestSupport.capture(SourceValueCapture.capture(original), original.profile()).withFacts(original.program(), Optional.of(AstValueAdmission.admit(values))),
        original.profile(), original.identity().semanticProductionRevision()).result().orElseThrow();
  }

  private static SyntaxNode formalAccess(AstResult result) {
    var method = result.program().methods().getFirst();
    return result.aggregate().methodSyntax(method.methodKey()).orElseThrow().root().requiredChild(SyntaxRole.STATEMENT)
        .requiredChild(SyntaxRole.EXPRESSION);
  }

  private record FormalSelection(String variant, String signature, String name, int ordinal, Optional<JavaTypeIdentity> type) {
  }

  private static CopiedAstValues detachedBodyWithoutHeader(CopiedAstValues values, UnaryOperator<SyntaxNode> operandChange) {
    var original = values.sources().getFirst().root();
    var root = SyntaxNode.source(original.sourceFileKey(), original.structuralPath(), original.sourceKind(), original.syntaxKind(),
        original.type(), original.value(), List.of(), original.semanticReference(), original.origin().orElseThrow());
    var source = new SyntaxSourceContribution(root.sourceFileKey(), root, List.of(root), List.of(), root.semanticEdges());
    var body = values.bodies().getFirst();
    var detached = new CopiedAstValues.Body(body.methodKey(), withoutPositions(body.root(), operandChange),
        MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    return new CopiedAstValues(values.snapshot(), List.of(source), List.of(detached), List.of(),
        values.associations().stream().filter(association -> association.nodeKey().equals(root.nodeId())).toList(), List.of(), List.of());
  }

  private static SyntaxNode withoutPositions(SyntaxNode node, UnaryOperator<SyntaxNode> operandChange) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        withoutPositions(child.node(), operandChange))).toList();
    var copied = SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(),
        node.type(), node.value(), children, node.semanticReference());
    return node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS ? operandChange.apply(copied) : copied;
  }

  private static CopiedAstValues withAdditionalEdge(CopiedAstValues captured, SyntaxSemanticEdge edge) {
    var sources = captured.sources().stream().map(source -> {
      if (source.nodes().stream().noneMatch(node -> node.nodeId().equals(edge.sourceKey()))) return source;
      var edges = new ArrayList<>(source.semanticEdges());
      edges.add(edge);
      return new SyntaxSourceContribution(source.sourceFileKey(), source.root(), source.nodes(), source.structuralEdges(), edges);
    }).toList();
    return new CopiedAstValues(captured.snapshot(), sources, captured.bodies(), captured.memberOrders(), captured.associations(), captured.declarationTypes(), captured.scopes());
  }

  @Test
  void givenCurrentLambda_whenDecoded_thenWrittenFunctionalValuesKeepTheirIdentity() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example { Runnable subject() { return () -> {}; } }")));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    // Act
    var decoded = AstCodec.decode(AstCodec.encode(current), current.identity());
    // Assert
    assertEquals(current.identity(), decoded.identity());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(current.coverage(), decoded.coverage());
    var originalProperties = current.coverage().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().startsWith("LambdaExpressionTree::")).toList();
    var retainedProperties = decoded.coverage().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().startsWith("LambdaExpressionTree::")).toList();
    assertEquals(Set.of("LambdaExpressionTree::Tree#getKind()", "LambdaExpressionTree::LambdaExpressionTree#getBodyKind()",
        "LambdaExpressionTree::LambdaExpressionTree#getBody()", "LambdaExpressionTree::LambdaExpressionTree#getParameters()"),
        retainedProperties.stream().map(outcome -> outcome.address().role()).collect(Collectors.toSet()));
    assertEquals(4, retainedProperties.size());
    assertEquals(originalProperties.stream().map(outcome -> outcome.encounteredValue()).toList(), retainedProperties.stream().map(outcome -> outcome.encounteredValue()).toList());
    assertEquals(List.of(CoveragePropertyValue.State.EMPTY), retainedProperties.stream().filter(outcome -> outcome.address().role().endsWith("#getParameters()"))
        .map(outcome -> outcome.encounteredValue().orElseThrow().state()).toList());
    assertEquals(current.coverage().validationEvidence().writtenInventory().contexts().stream().filter(context -> context.address().role().startsWith("LambdaExpressionTree::")).toList(),
        decoded.coverage().validationEvidence().writtenInventory().contexts().stream().filter(context -> context.address().role().startsWith("LambdaExpressionTree::")).toList());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
  }
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenImpossibleKnownLambdaTargets_whenConstructedOrDecoded_thenIntrinsicSourceValidationRejectsThem() {
    return Stream.of(
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int")),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.VOID, "void", "void")),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "int[]", "int[]"),
            new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.INT))),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "T", "java.lang.Object")),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.WILDCARD, "?", "java.lang.Object"),
            new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED, Optional.empty())))
        .map(target -> DynamicTest.dynamicTest(target.identity().category().name(), () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
              "class Example { Runnable subject() { return () -> {}; } }")));
          var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
              AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
          var lambda = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION)
              .findFirst().orElseThrow();
          var nodeWriter = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
          new RetainedValueCodec(false).write(nodeWriter, lambda);
          var originalType = new CanonicalValueWriter("");
          new RetainedValueCodec(false).write(originalType, lambda.type());
          var replacementType = new CanonicalValueWriter("");
          new RetainedValueCodec(false).write(replacementType, target);
          var bytes = nodeWriter.toByteArray();
          var oldType = Arrays.copyOfRange(originalType.toByteArray(), Integer.BYTES, originalType.toByteArray().length);
          var offset = -1;
          var matches = 0;
          for (var index = 0; index <= bytes.length - oldType.length; index++) {
            if (Arrays.equals(bytes, index, index + oldType.length, oldType, 0, oldType.length)) {
              offset = index;
              matches++;
            }
          }
          assertEquals(1, matches, "The closed node contains one functional target type frame");
          var altered = new ByteArrayOutputStream();
          altered.write(bytes, 0, offset);
          altered.writeBytes(Arrays.copyOfRange(replacementType.toByteArray(), Integer.BYTES, replacementType.toByteArray().length));
          altered.write(bytes, offset + oldType.length, bytes.length - offset - oldType.length);
          // Act
          var construction = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(lambda.sourceFileKey(),
              lambda.structuralPath(), lambda.sourceKind(), lambda.syntaxKind(), target, lambda.value(), lambda.children(),
              lambda.semanticReference(), lambda.origin().orElseThrow()));
          var decoding = assertThrows(IllegalArgumentException.class,
              () -> new RetainedValueCodec(false).read(new CanonicalValueReader(altered.toByteArray(), AstCodec.FORMAT_VERSION)));
          var valid = new RetainedValueCodec(false).read(new CanonicalValueReader(bytes, AstCodec.FORMAT_VERSION));
          // Assert
          assertTrue(construction.getMessage().contains("lambda target"), construction.toString());
          assertTrue(decoding.getMessage().contains("lambda target"), decoding.toString());
          assertEquals(lambda, valid);
        }));
  }

  @Test
  void givenCurrentAssertionAndMonitor_whenDecoded_thenWrittenValuesRemainReadable() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")),
        "class Stored { void value(Object lock) { assert lock != null; synchronized (lock) {} } }")));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    // Act
    var decoded = AstCodec.decode(AstCodec.encode(current), current.identity());
    // Assert
    var kinds = decoded.aggregate().nodes().stream().map(SyntaxNode::syntaxKind).collect(Collectors.toSet());
    assertTrue(kinds.containsAll(Set.of(SyntaxKind.ASSERT, SyntaxKind.SYNCHRONIZED)));
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertArrayEquals(AstCodec.encode(current), AstCodec.encode(decoded));
  }

  @Test
  void givenCurrentIteration_whenDecoded_thenLabelsRemainReadable() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")),
        "class Stored { void value(int n) { outer: for (int i = 0; i < n; i++) { if (i == 2) break outer; } } }")));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var bytes = AstCodec.encode(current);
    // Act
    var decoded = AstCodec.decode(bytes, current.identity());
    // Assert
    var labels = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LABELED_STATEMENT)
        .map(node -> node.value(SyntaxValue.StatementLabel.class).name().orElseThrow()).toList();
    assertEquals(List.of("outer"), labels);
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(current.identity(), decoded.identity());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
  }

  @Test
  void givenCurrentArrayCreation_whenDecoded_thenDimensionsStayOrdered() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")),
        "class Stored { int[][] value(int n) { return new int[n][]; } }")));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var bytes = AstCodec.encode(current);
    // Act
    var decoded = AstCodec.decode(bytes, current.identity());
    // Assert
    var creation = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ARRAY_CREATION)
        .findFirst().orElseThrow();
    assertEquals(1, creation.children(SyntaxRole.DIMENSION).size());
    assertEquals(current.identity(), decoded.identity());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
  }

  @Test
  void givenCurrentBoundedResult_whenDecodedAndDerived_thenIdentityAndValuesRemainUsable() throws IOException {
    // Arrange
    var current = fixture();
    var bytes = AstCodec.encode(current);
    var method = current.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();

    // Act
    var decoded = AstCodec.decode(bytes, current.identity());
    var cfg = CfgDerivers.standard().derive(decoded,
        new CfgRequest(decoded.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstResult.SOURCE_MODEL_VERSION, current.identity().sourceModelVersion());
    assertEquals(current.identity(), decoded.identity());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(current.coverage(), decoded.coverage());
    assertTrue(decoded.coverage().validationEvidence().writtenInventory().writtenExpressions().isPresent());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(MethodCfgState.AVAILABLE, cfg.methods().getFirst().state());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    assertEquals(AstCodec.materialize(current), AstCodec.materialize(decoded));
  }

  @Test
  void givenCurrentUpdates_whenDecodedAndDerived_thenWrittenValuesRemainReadable() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")),
        "class Stored { int value(int input) { input += 2; return input++; } }")));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var bytes = AstCodec.encode(current);
    var method = current.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
    // Act
    var decoded = AstCodec.decode(bytes, current.identity());
    var cfg = CfgDerivers.standard().derive(decoded, new CfgRequest(decoded.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
    // Assert
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(current.identity(), decoded.identity());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(current.coverage(), decoded.coverage());
    assertEquals(AstCodec.materialize(current), AstCodec.materialize(decoded));
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
    assertEquals(List.of(SyntaxKind.COMPOUND_ASSIGNMENT, SyntaxKind.UPDATE), decoded.body(method.methodKey()).orElseThrow().root()
        .descendantsAndSelf().stream().map(node -> node.syntaxKind()).filter(kind -> kind == SyntaxKind.COMPOUND_ASSIGNMENT || kind == SyntaxKind.UPDATE).toList());
  }

  @Test
  void givenValidCurrentIdentityFollowedByAnOptionalChain_whenDecoded_thenTheWrongInputShapeRejectsBeforeDescent() throws IOException {
    // Arrange
    var original = fixture();
    var malformed = retainedEnvelope(original);
    for (var index = 0; index < 20_000; index++) {
      malformed.text("optional/present");
    }
    malformed.text("optional/absent");
    var validBytes = AstCodec.encode(original);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed.toByteArray(), original.identity()));
    var decoded = AstCodec.decode(validBytes, original.identity());

    // Assert
    assertEquals(original.identity(), decoded.identity());
    var cause = assertInstanceOf(ClassCastException.class, failure.getCause());
    assertEquals("Wrong retained value type: SyntaxAnalysisInput", cause.getMessage());
  }

  @Test
  void givenDeepWellTypedOccurrencesEndingInAnUnfinishedReference_whenDecoded_thenTheExactReferenceRejects() throws IOException {
    // Arrange
    var original = fixture();
    var valid = AstCodec.encode(original);
    var nodeHeader = new CanonicalValueWriter("source-node");
    nodeHeader.integer(0);
    var prefix = nodeHeader.toByteArray();
    var offset = 0;
    while (offset <= valid.length - prefix.length
        && !Arrays.equals(valid, offset, offset + prefix.length, prefix, 0, prefix.length)) {
      offset++;
    }
    assertTrue(offset <= valid.length - prefix.length, "The admitted envelope contains source occurrence zero");

    var anchor = new CanonicalValueWriter("");
    var codec = new RetainedValueCodec(false);
    var sourceKey = original.snapshot().sourceFiles().getFirst().sourceFileKey();
    for (var ordinal = 0; ordinal < 6_000; ordinal++) {
      anchor.text("source-node");
      anchor.integer(ordinal);
      anchor.count(9);
      anchor.text("sourceFileKey");
      codec.write(anchor, sourceKey);
      anchor.text("structuralPath");
      codec.write(anchor, SyntaxStructuralPath.sourceFileRoot());
      anchor.text("sourceKind");
      codec.write(anchor, JavaSourceKind.BLOCK);
      anchor.text("syntaxKind");
      codec.write(anchor, SyntaxKind.BLOCK);
      anchor.text("type");
      codec.write(anchor, SyntaxType.NotApplicable.INSTANCE);
      anchor.text("value");
      codec.write(anchor, SyntaxValue.None.INSTANCE);
      anchor.text("children");
      anchor.text("list");
      anchor.count(1);
      anchor.text("syntax-child");
      anchor.count(3);
      anchor.text("role");
      codec.write(anchor, SyntaxRole.STATEMENT);
      anchor.text("position");
      codec.write(anchor, 0);
      anchor.text("node");
    }
    anchor.text("source-node-reference");
    anchor.integer(0);
    var part = anchor.toByteArray();
    var malformed = new ByteArrayOutputStream();
    malformed.write(valid, 0, offset);
    malformed.write(part, Integer.BYTES, part.length - Integer.BYTES);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed.toByteArray(), original.identity()));
    var decoded = AstCodec.decode(valid, original.identity());

    // Assert
    assertEquals(original.identity(), decoded.identity());
    assertEquals("Source occurrence reference requires an earlier complete occurrence", failure.getCause().getMessage());
  }

  @Test
  void givenCurrentInputWithTwoValidFieldsSwapped_whenDecoded_thenExactFieldOrderIsRequired() throws IOException {
    // Arrange
    var original = fixture();
    var malformed = retainedEnvelope(original);
    var codec = new RetainedValueCodec(false);
    malformed.text("syntax-analysis-input");
    malformed.count(6);
    malformed.text("environmentSnapshot");
    codec.write(malformed, original.environment());
    malformed.text("snapshot");
    codec.write(malformed, original.snapshot());
    malformed.text("program");
    codec.write(malformed, original.program());
    malformed.text("aggregate");
    codec.write(malformed, Optional.of(original.aggregate()));
    malformed.text("diagnostics");
    codec.write(malformed, original.diagnostics());
    malformed.text("languageFeatureCoverageResult");
    codec.write(malformed, original.coverage());

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed.toByteArray(), original.identity()));
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(original.identity(), decoded.identity());
    assertEquals("Wrong retained field order: syntax-analysis-input/snapshot", failure.getCause().getMessage());
  }

  @Test
  void givenValidCurrentAstWithAnExtraByte_whenDecoded_thenTrailingDataRejects() throws IOException {
    // Arrange
    var original = fixture();
    var valid = AstCodec.encode(original);
    var malformed = Arrays.copyOf(valid, valid.length + 1);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed, original.identity()));
    var decoded = AstCodec.decode(valid, original.identity());

    // Assert
    assertEquals(original.identity(), decoded.identity());
    assertTrue(failure.getCause().getMessage().contains("unread trailing bytes"));
  }

  private static CanonicalValueWriter retainedEnvelope(AstResult result) {
    var writer = new CanonicalValueWriter(AstCodec.FORMAT_VERSION);
    writer.text(result.profile().version());
    writer.text(result.identity().semanticProductionRevision());
    writer.text(result.completion().name());
    new RetainedValueCodec(false).write(writer, result.identity());
    return writer;
  }

  @Test
  void givenUnknownFormatIdentity_whenDecoding_thenUnsupportedFormatPrecedesPayloadDecoding() throws IOException {
    // Arrange
    var current = fixture();
    var identity = current.identity();
    var unknown = AstIdentity.fromContent(identity.softwareSignature(), "java-source/future", identity.astProfileVersion(),
        identity.semanticProductionRevision(), identity.canonicalContentDigest());

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(new byte[0], unknown));

    // Assert
    assertTrue(failure.getMessage().contains("Unsupported retained AST format: java-source/future"));
    assertEquals(AstResult.SOURCE_MODEL_VERSION, identity.sourceModelVersion());
  }

  @Test
  void givenCurrentBindings_whenDecodedAndDerived_thenCoverageAndClosedValuesRemainReadable() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")), """
        import java.lang.annotation.*;
        @Target({ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Mark { int value(); }
        class Stored {
          int named(int input) {
            final int first=input; int second; second=first+1; var inferred=second; return inferred;
          }
          int expanded(int[] input) {
            @Mark(3) final int first=1, second[]=input;
            var inferred=first; int _=7; return inferred;
          }
        }
        """)));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var bytes = AstCodec.encode(current);
    var method = current.program().methods().stream().filter(value -> value.name().equals("named")).findFirst().orElseThrow();
    var expanded = current.program().methods().stream().filter(value -> value.name().equals("expanded")).findFirst().orElseThrow();

    // Act
    var decoded = AstCodec.decode(bytes, current.identity());
    var cfg = CfgDerivers.standard().derive(decoded,
        new CfgRequest(decoded.identity(), List.of(method.methodKey(), expanded.methodKey()), CfgProfile.JAVA_BOUNDED));
    var locals = decoded.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();
    var expandedLocals = decoded.body(expanded.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();

    // Assert
    assertEquals(current.identity(), decoded.identity());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertEquals(current.coverage(), decoded.coverage());
    assertEquals(current.coverage().validationEvidence().writtenInventory().contexts(),
        decoded.coverage().validationEvidence().writtenInventory().contexts());
    assertEquals(current.coverage().validationEvidence().attributedExpectations().projections(),
        decoded.coverage().validationEvidence().attributedExpectations().projections());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(List.of("first", "second", "inferred"), locals.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
    assertTrue(locals.stream().allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().isPresent()));
    assertTrue(locals.stream().allMatch(node -> node.semanticReference().isPresent()));
    assertTrue(locals.get(1).optionalChild(SyntaxRole.INITIALIZER).isEmpty());
    assertEquals(LocalBindingEvidence.TypeForm.INFERRED, locals.get(2).value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().typeForm());
    assertEquals(List.of("first", "second", "inferred", "_"),
        expandedLocals.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
    assertEquals(List.of(expandedLocals.getFirst().requiredChild(SyntaxRole.ANNOTATION).nodeId()),
        expandedLocals.get(1).value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().sharedAnnotationKeys());
    assertTrue(expandedLocals.getLast().semanticReference().isEmpty());
    assertTrue(expandedLocals.getLast().value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().isPresent());
    assertEquals(MethodCfgState.AVAILABLE, cfg.methods().stream().filter(value -> value.methodKey().equals(method.methodKey())).findFirst().orElseThrow().state());
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().stream().filter(value -> value.methodKey().equals(expanded.methodKey())).findFirst().orElseThrow().state());
    assertEquals(AstCodec.materialize(current), AstCodec.materialize(decoded));
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    assertThrows(IllegalArgumentException.class, () -> new CanonicalValueReader(AstCodec.encode(current), "java-source/3"));
  }

  @Test
  void givenNewFormatResult_whenThePrecedingFrameReaderIsUsed_thenItRejectsTheFormatBeforeReadingValues() throws IOException {
    // Arrange
    var current = fixture();
    var bytes = AstCodec.encode(current);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new CanonicalValueReader(bytes, "java-source/2"));

    // Assert
    assertEquals("Unsupported canonical value format: " + AstResult.SOURCE_MODEL_VERSION, failure.getMessage());
  }

  @Test
  void givenCurrentPlainLocalValues_whenTheFullResultIsDecoded_thenTypesInitializersAndIdentitySurvive()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")),
        "class Stored { int subject() { int first=1; int second; second=first+2; return second; } }")));
    var current = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var bytes = AstCodec.encode(current);

    // Act
    var decoded = AstCodec.decode(bytes, current.identity());
    var locals = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(current.identity(), decoded.identity());
    assertEquals(List.of("first", "second"), locals.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
    assertTrue(locals.stream().allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().isPresent()));
    assertEquals("int", locals.getFirst().requiredChild(SyntaxRole.DECLARED_TYPE).resolvedType().orElseThrow().displayName());
    assertTrue(locals.getFirst().optionalChild(SyntaxRole.INITIALIZER).isPresent());
    assertTrue(locals.getLast().optionalChild(SyntaxRole.INITIALIZER).isEmpty());
    assertEquals(current.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS)
        .map(SyntaxNode::semanticReference).toList(), decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS)
            .map(SyntaxNode::semanticReference).toList());
    assertEquals(current.aggregate(), decoded.aggregate());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    assertEquals(AstCodec.materialize(current), AstCodec.materialize(decoded));
  }

  @Test
  void givenLocalDeclarationWithoutEvidence_whenEncoded_thenTheEvidenceFieldRecordsAbsence() {
    // Arrange
    var writer = new CanonicalValueWriter("copied-local-value");
    writer.text("syntax-value/local-declaration");
    writer.count(4);
    writer.text("name");
    writer.text("text");
    writer.text("value");
    writer.text("writtenType");
    writer.text("optional/absent");
    writer.text("writtenModifiers");
    writer.text("list");
    writer.count(0);
    writer.text("bindingEvidence");
    writer.text("optional/absent");
    var bytes = writer.toByteArray();

    // Act
    var reader = new CanonicalValueReader(bytes, "copied-local-value");
    var local = (SyntaxValue.LocalDeclaration) new RetainedValueCodec(false).read(reader);
    reader.requireFinished();
    var encoded = new CanonicalValueWriter("copied-local-value");
    new RetainedValueCodec(false).write(encoded, local);
    var materialized = new AstValueProjector().project(local);

    // Assert
    assertEquals("value", local.name());
    assertTrue(local.writtenType().isEmpty());
    assertEquals(List.of(), local.writtenModifiers());
    assertTrue(local.bindingEvidence().isEmpty());
    assertArrayEquals(bytes, encoded.toByteArray());
    assertEquals("syntax-value/local-declaration", materialized.nodes().getFirst().tag());
    assertEquals(List.of("name", "writtenType", "writtenModifiers", "bindingEvidence"), materialized.links().stream()
        .filter(link -> link.sourceOrdinal() == 0).map(AstValueProjection.Link::field).toList());
  }

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
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
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
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")), "class Stored { int value(int x) { return x + 1; } }")));
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        profile);
    assertEquals(AstCompletion.COMPLETE, outcome.completion());
    return outcome.result().orElseThrow();
  }

  private static byte[] payload(AstResult original, String owner, String changedField) {
    var writer = new CanonicalValueWriter(original.identity().sourceModelVersion());
    writer.text(original.profile().version());
    writer.text(original.identity().semanticProductionRevision());
    writer.text(original.completion().name());
    var codec = new RetainedValueCodec(false);
    codec.write(writer, original.identity());
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    var shape = (Fields) AstValueSchema.shape(input);
    var components = shape.components(input, false);
    writer.text(shape.tag());
    writer.count(components.size());
    for (var index = 0; index < components.size(); index++) {
      var field = components.get(index).name();
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
        writer.text("optional/present");
        writer.text("source-aggregate");
        writer.count(1);
        writer.text("copiedValues");
        writeAggregate(writer, codec, SourceValueCapture.capture(original.aggregate()), owner, changedField);
      } else if ("languageFeatureCoverageResult".equals(field) && "coverage".equals(owner)) {
        writer.text("source-coverage-result");
        writer.count(2);
        writer.text("coverage");
        codec.write(writer, original.coverage().coverage());
        writer.text("validationEvidence");
        writeRecord(writer, codec, original.coverage().validationEvidence(), changedField);
      } else {
        codec.write(writer, components.get(index).value());
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
        }).toList(), type.synthesizedDeclarations(), type.origins(),
    type.nesting(), type.staticType(), type.displayName())).toList());
  }

  private static void writeAggregate(CanonicalValueWriter writer, RetainedValueCodec codec, CopiedAstValues values, String owner,
      String changedField) {
    if ("aggregate".equals(owner)) {
      writeRecord(writer, codec, values, changedField);
      return;
    }
    var shape = (Fields) AstValueSchema.shape(values);
    var components = shape.components(values, false);
    writer.text(shape.tag());
    writer.count(components.size());
    for (var index = 0; index < components.size(); index++) {
      var field = components.get(index).name();
      writer.text(field);
      if ("bodies".equals(field)) {
        writer.text("list");
        writer.count(values.bodies().size());
        for (var body : values.bodies()) {
          writeRecord(writer, codec, body, changedField);
        }
      } else {
        codec.write(writer, components.get(index).value());
      }
    }
  }

  private static void writeRecord(CanonicalValueWriter writer, RetainedValueCodec codec, Object value, String changedField) {
    var shape = (Fields) AstValueSchema.shape(value);
    var components = shape.components(value, false);
    writer.text(shape.tag());
    writer.count(components.size());
    for (var index = 0; index < components.size(); index++) {
      var field = components.get(index).name();
      writer.text(field);
      var replacement = switch (changedField) {
        case "modifiers" -> Set.of("wrong retained member");
        case "enclosingType" -> Optional.of("wrong retained member");
        default -> List.of("wrong retained member");
      };
      codec.write(writer, field.equals(changedField) ? replacement : components.get(index).value());
    }
  }
}
