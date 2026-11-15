package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class EnumInitializationAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenRequiredTargetLossAndOptionalClassificationLoss_whenReadmitted_thenOnlyRequiredLossPreventsCompletion() throws IOException {
    // Arrange
    var original = produce("enum Example { FIRST(Math.abs(-2)); Example(int n) {} }", AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var values = SourceValueCapture.capture(original.aggregate());
    var required = values.scopes().stream().map(scope -> !(scope.scope() instanceof ExecutionScope.EnumConstant) ? scope
        : copy(scope, scope.scope(), scope.uses().stream().filter(use -> !use.consumerNodeId().equals(scope.root().nodeId())).toList(), scope.invocations())).toList();
    var optional = values.scopes().stream().map(scope -> !(scope.scope() instanceof ExecutionScope.EnumConstant) ? scope
        : copy(scope, scope.scope(), scope.uses(), scope.invocations().stream().map(value -> new InvocationEvidence(EvidenceValue.missing(value.applicability().origin(), "Optional applicability unavailable in supplied values"))).toList())).toList();

    // Act
    var incomplete = qualify(original, withScopes(values, required));
    var complete = qualify(original, withScopes(values, optional));
    var retained = AstCodec.decode(AstCodec.encode(complete), complete.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, incomplete.completion());
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals(original.aggregate().nodes(), incomplete.aggregate().nodes());
    assertTrue(retained.aggregate().executionScopes().stream().filter(ExecutionScope.EnumConstant.class::isInstance).map(scope -> retained.aggregate().executionSyntax(scope.key()).orElseThrow()).flatMap(scope -> scope.evidence().invocations().stream()).allMatch(value -> value.applicability().knownValue().isEmpty()));
  }

  @Test
  void givenMissingConstructorSelection_whenReadmitted_thenTheConstantRemainsIncomplete() throws IOException {
    // Arrange
    var original = produce("enum Example { FIRST; Example() {} }", AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var values = SourceValueCapture.capture(original.aggregate());
    var scopes = values.scopes().stream().map(scope -> !(scope.scope() instanceof ExecutionScope.EnumConstant constant) ? scope
        : copy(scope, new ExecutionScope.EnumConstant(constant.key(), constant.ownerKey(), Optional.empty()), scope.uses(), scope.invocations())).toList();
    // Act
    var result = qualify(original, withScopes(values, scopes));
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertEquals(original.aggregate().nodes(), result.aggregate().nodes());
  }

  @Test
  void givenForeignConstructorAndDuplicateConstantRegistrations_whenAdmitted_thenEveryContradictionIsRejected() throws IOException {
    // Arrange
    var original = produce("enum Example { FIRST(1) { int value() { return 2; } }; Example(int n) {} }", AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var values = SourceValueCapture.capture(original.aggregate());
    var scope = values.scopes().stream().filter(value -> value.scope() instanceof ExecutionScope.EnumConstant).findFirst().orElseThrow();
    var constant = (ExecutionScope.EnumConstant) scope.scope();
    var selected = constant.selectedConstructor().orElseThrow();
    var foreign = new SemanticReference(selected.referenceKind(), selected.status(), Optional.of("type:Other#<init>(int)"), selected.typeName(), selected.typeAlternativeNames(), selected.assignableToTypeNames(), Optional.empty());
    var duplicate = new ArrayList<>(values.scopes());
    duplicate.add(scope);
    var duplicateBodies = new ArrayList<>(values.bodies());
    duplicateBodies.add(values.bodies().stream().filter(body -> body.methodKey().endsWith("#value()")).findFirst().orElseThrow());
    var duplicatedBodyValues = new CopiedAstValues(values.snapshot(), values.sources(), duplicateBodies, values.memberOrders(), values.associations(), values.declarationTypes(), values.scopes());
    // Act
    var targetFailure = assertThrows(IllegalArgumentException.class, () -> new ExecutionScope.EnumConstant(constant.key(), constant.ownerKey(), Optional.of(foreign)));
    var scopeFailure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(withScopes(values, duplicate)));
    var bodyFailure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(duplicatedBodyValues));
    // Assert
    assertTrue(targetFailure.getMessage().contains("constructor"));
    assertTrue(scopeFailure.getMessage().contains("scope") || scopeFailure.getMessage().contains("registration"), scopeFailure.toString());
    assertTrue(bodyFailure.getMessage().contains("scope") || bodyFailure.getMessage().contains("body"), bodyFailure.toString());
  }

  @Test
  void givenOmittedArgumentOrBody_whenAConstantShapeIsConstructed_thenPresenceFlagsCannotSubstituteForChildren() throws IOException {
    // Arrange
    var original = produce("enum Example { FIRST(1) {}; Example(int n) {} }", AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var constant = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).findFirst().orElseThrow();
    // Act
    var argumentFailure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(constant.sourceFileKey(), constant.structuralPath(), constant.sourceKind(), constant.syntaxKind(), SyntaxType.NotApplicable.INSTANCE, constant.value(), constant.children().stream().filter(child -> child.role() != SyntaxRole.ARGUMENT).toList(), Optional.empty(), constant.origin().orElseThrow()));
    var bodyFailure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(constant.sourceFileKey(), constant.structuralPath(), constant.sourceKind(), constant.syntaxKind(), SyntaxType.NotApplicable.INSTANCE, constant.value(), constant.children().stream().filter(child -> child.role() != SyntaxRole.CLASS_BODY).toList(), Optional.empty(), constant.origin().orElseThrow()));
    // Assert
    assertTrue(argumentFailure.getMessage().contains("ARGUMENT"), argumentFailure.toString());
    assertTrue(bodyFailure.getMessage().contains("CLASS_BODY"), bodyFailure.toString());
  }

  @Test
  void givenUnrelatedConstructorSelectionsInTheSameEnum_whenCopiedScopesAreAdmitted_thenDeclarationAndSignatureMustCorroborateSelection() throws IOException {
    // Arrange
    var original = produce("enum Example { FIRST; Example() {} Example(String... values) {} }", AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var values = SourceValueCapture.capture(original.aggregate());
    var scopes = values.scopes().stream().map(scope -> {
      if (!(scope.scope() instanceof ExecutionScope.EnumConstant constant)) return scope;
      var selected = constant.selectedConstructor().orElseThrow();
      var nonexistent = new SemanticReference(selected.referenceKind(), selected.status(), Optional.of(constant.ownerKey() + "#<init>(boolean)"), selected.typeName(), selected.typeAlternativeNames(), selected.assignableToTypeNames(), Optional.empty());
      return copy(scope, new ExecutionScope.EnumConstant(constant.key(), constant.ownerKey(), Optional.of(nonexistent)), scope.uses(), scope.invocations());
    }).toList();
    var incompatible = values.scopes().stream().map(scope -> {
      if (!(scope.scope() instanceof ExecutionScope.EnumConstant constant)) return scope;
      var selected = constant.selectedConstructor().orElseThrow();
      var other = new SemanticReference(selected.referenceKind(), selected.status(), Optional.of(constant.ownerKey() + "#<init>(java.lang.String[])"), selected.typeName(), selected.typeAlternativeNames(), selected.assignableToTypeNames(), Optional.empty());
      return copy(scope, new ExecutionScope.EnumConstant(constant.key(), constant.ownerKey(), Optional.of(other)), scope.uses(), scope.invocations());
    }).toList();
    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(withScopes(values, scopes)));
    var signatureFailure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(withScopes(values, incompatible)));
    // Assert
    assertTrue(failure.getMessage().contains("constructor"), failure.toString());
    assertTrue(signatureFailure.getMessage().contains("constructor"), signatureFailure.toString());
  }

  @Test
  void givenTwoSupportedEnumProfiles_whenRetained_thenEachProfileKeepsItsOwnValues() throws IOException {
    // Arrange
    var earlier = produce("enum Example { FIRST; Example() {} }", AstProfile.JAVA_SOURCE_NESTED);
    var later = produce("enum Example { FIRST(1) {}; Example(int n) {} }", AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var earlierBytes = AstCodec.encode(earlier);
    var laterBytes = AstCodec.encode(later);

    // Act
    var earlierRetained = AstCodec.decode(earlierBytes, earlier.identity());
    var laterRetained = AstCodec.decode(laterBytes, later.identity());

    // Assert
    assertEquals(AstProfile.JAVA_SOURCE_NESTED, earlierRetained.profile());
    assertEquals(AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION, laterRetained.profile());
    assertEquals(earlier.identity(), earlierRetained.identity());
    assertEquals(later.identity(), laterRetained.identity());
    assertEquals(earlier.completion(), earlierRetained.completion());
    assertEquals(later.completion(), laterRetained.completion());
    assertArrayEquals(earlierBytes, AstCodec.encode(earlierRetained));
    assertArrayEquals(laterBytes, AstCodec.encode(laterRetained));
  }

  @TestFactory
  Stream<DynamicTest> givenContradictorySelectedConstructors_whenOptionalApplicabilityIsMissingOrAbsent_thenPublicAdmissionRejectsEveryKnownSignatureConflict() {
    return Stream.of("", "int,int", "long").flatMap(parameters -> Stream.of(false, true).map(absent -> DynamicTest.dynamicTest(
        "givenConstructor(" + parameters + ")_whenOptionalApplicabilityIs" + (absent ? "Absent" : "Missing") + "_thenContradictoryArgumentsAreRejected", () -> {
          // Arrange
          var original = produce("enum Example { FIRST(1); Example() {} Example(int n) {} Example(int a, int b) {} Example(long n) {} }", AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
          var values = SourceValueCapture.capture(original.aggregate());
          var scopes = values.scopes().stream().map(scope -> {
            if (!(scope.scope() instanceof ExecutionScope.EnumConstant constant)) return scope;
            var selected = constant.selectedConstructor().orElseThrow();
            var other = new SemanticReference(selected.referenceKind(), selected.status(), Optional.of(constant.ownerKey() + "#<init>(" + parameters + ")"), selected.typeName(), selected.typeAlternativeNames(), selected.assignableToTypeNames(), Optional.empty());
            return copy(scope, new ExecutionScope.EnumConstant(constant.key(), constant.ownerKey(), Optional.of(other)), scope.uses(), optionalInvocations(scope, absent));
          }).toList();
          // Act
          var failure = assertThrows(AstContractException.class, () -> qualify(original, withScopes(values, scopes)));
          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion());
          assertTrue(failure.getMessage().contains("constructor") || failure.getMessage().contains("parameter"), failure.toString());
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenValidVariableArityAndGenericSelections_whenOptionalApplicabilityIsMissingOrAbsent_thenCompletionAndCodecValuesSurvive() {
    return Stream.of("enum Example { FIRST, SECOND(1), THIRD(1, 2), FOURTH(new int[] {3}); Example(int... values) {} }",
        "enum Example { FIRST(1), SECOND(2); <T extends Number> Example(T value) {} }")
        .flatMap(source -> Stream.of(false, true).map(absent -> DynamicTest.dynamicTest(
            "givenValidConstructor_" + source + "_whenOptionalApplicabilityIs" + (absent ? "Absent" : "Missing") + "_thenCopiedAndRetainedValuesAreComplete", () -> {
              // Arrange
              var original = produce(source, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
              var values = SourceValueCapture.capture(original.aggregate());
              var scopes = values.scopes().stream().map(scope -> !(scope.scope() instanceof ExecutionScope.EnumConstant) ? scope
                  : copy(scope, scope.scope(), scope.uses(), optionalInvocations(scope, absent))).toList();
              // Act
              var admitted = qualify(original, withScopes(values, scopes));
              var bytes = AstCodec.encode(admitted);
              var retained = AstCodec.decode(bytes, admitted.identity());
              // Assert
              assertEquals(AstCompletion.COMPLETE, original.completion());
              assertEquals(AstCompletion.COMPLETE, retained.completion());
              assertEquals(admitted.aggregate().executionScopes(), retained.aggregate().executionScopes());
              assertArrayEquals(bytes, AstCodec.encode(retained));
            })));
  }

  @Test
  void givenReorderedEnumArgumentsAndExchangedConstantBodies_whenCopiedAttachmentsAreAdmitted_thenIndependentOriginalPathsRejectBoth() throws IOException {
    // Arrange
    var original = produce("enum Example { FIRST(1, 2) { int value() { return 3; } }, SECOND(4, 5) { int value() { return 6; } }; Example(int a, int b) {} }", AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var values = SourceValueCapture.capture(original.aggregate());
    var constants = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).toList();
    var first = constants.getFirst();
    var second = constants.getLast();
    var arguments = first.children(SyntaxRole.ARGUMENT);
    var reordered = first.children().stream().map(child -> child.role() == SyntaxRole.ARGUMENT
        ? new SyntaxChild(child.role(), child.position(), arguments.get(1 - child.position())) : child).toList();
    var firstExchanged = first.children().stream().map(child -> child.role() == SyntaxRole.CLASS_BODY
        ? new SyntaxChild(child.role(), child.position(), second.requiredChild(SyntaxRole.CLASS_BODY)) : child).toList();
    var secondExchanged = second.children().stream().map(child -> child.role() == SyntaxRole.CLASS_BODY
        ? new SyntaxChild(child.role(), child.position(), first.requiredChild(SyntaxRole.CLASS_BODY)) : child).toList();
    var reorderedValues = withAttachments(values, Map.of(first.nodeId(), reordered));
    var exchangedValues = withAttachments(values, Map.of(first.nodeId(), firstExchanged, second.nodeId(), secondExchanged));
    // Act
    var orderFailure = assertThrows(AstContractException.class, () -> qualify(original, reorderedValues));
    var bodyFailure = assertThrows(AstContractException.class, () -> qualify(original, exchangedValues));
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(orderFailure.getMessage().contains("path"), orderFailure.toString());
    assertTrue(bodyFailure.getMessage().contains("path"), bodyFailure.toString());
    assertEquals(arguments, first.children(SyntaxRole.ARGUMENT));
  }

  private static List<InvocationEvidence> optionalInvocations(CopiedAstValues.Scope scope, boolean absent) {
    return absent ? List.of() : scope.invocations().stream().map(value -> new InvocationEvidence(EvidenceValue.missing(value.applicability().origin(), "Optional applicability unavailable in supplied values"))).toList();
  }

  private static CopiedAstValues withAttachments(CopiedAstValues values, Map<String, List<SyntaxChild>> replacements) {
    var sources = values.sources().stream().map(source -> {
      var root = replaceAttachments(source.root(), replacements);
      var nodes = root.descendantsAndSelf();
      return new SyntaxSourceContribution(source.sourceFileKey(), root, nodes, nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), nodes.stream().flatMap(node -> node.semanticEdges().stream()).toList());
    }).toList();
    return new CopiedAstValues(values.snapshot(), sources, values.bodies(), values.memberOrders(), values.associations(), values.declarationTypes(), values.scopes());
  }

  private static SyntaxNode replaceAttachments(SyntaxNode node, Map<String, List<SyntaxChild>> replacements) {
    var children = replacements.getOrDefault(node.nodeId(), node.children()).stream().map(child -> new SyntaxChild(child.role(), child.position(), replaceAttachments(child.node(), replacements))).toList();
    return node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children, node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children, node.semanticReference());
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile).result().orElseThrow();
  }

  private static CopiedAstValues.Scope copy(CopiedAstValues.Scope scope, ExecutionScope owner, List<ChildUseEvidence> uses, List<InvocationEvidence> invocations) {
    return new CopiedAstValues.Scope(owner, scope.root(), uses, invocations, scope.staticAccesses(), scope.concatenations(), scope.attributedOccurrences());
  }

  private static CopiedAstValues withScopes(CopiedAstValues values, List<CopiedAstValues.Scope> scopes) {
    return new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), values.associations(), values.declarationTypes(), scopes);
  }

  private static AstResult qualify(AstResult original, CopiedAstValues values) {
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(AstValueAdmission.admit(values)), original.diagnostics(), original.coverage());
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
  }
}
