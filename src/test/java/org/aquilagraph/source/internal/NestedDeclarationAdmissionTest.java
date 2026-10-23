package org.aquilagraph.source.internal;

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
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class NestedDeclarationAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenSubstitutedLexicalOwner_whenClosedInputIsReadmitted_thenIndependentCompilerOwnershipRejectsIt() throws IOException {
    // Arrange
    var original = produce("class Example { void subject() { class Local { int value() { return Math.abs(-2); } } } }", AstProfile.JAVA_SOURCE_NESTED);
    var types = original.program().types().stream().map(type -> type.nesting().kind() != JavaTypeNesting.Kind.LOCAL ? type
        : withNesting(type, new JavaTypeNesting(type.nesting().kind(), Optional.of("type:Foreign#subject()"), type.nesting().ownerDetails()))).toList();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environment(),
        new JavaProgram(types), Optional.of(original.aggregate()), original.diagnostics(), original.coverage()));

    // Assert
    assertTrue(failure.getMessage().contains("owner") || failure.getMessage().contains("support"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @Test
  void givenDuplicateRootAndSwappedNestedBody_whenCopiedValuesAreAdmitted_thenEachContradictoryAttachmentIsRejected() throws IOException {
    // Arrange
    var original = produce("class Example { void subject() { class Local { int value() { return 2; } } } }", AstProfile.JAVA_SOURCE_NESTED);
    var values = SourceValueCapture.capture(original.aggregate());
    var sources = new ArrayList<>(values.sources());
    sources.add(values.sources().getFirst());
    var duplicate = new CopiedAstValues(values.snapshot(), sources, values.bodies(), values.memberOrders(), values.associations(), values.declarationTypes(), values.scopes());
    var outerKey = original.program().methods().stream().filter(method -> method.name().equals("subject")).findFirst().orElseThrow().methodKey();
    var nestedKey = original.program().methods().stream().filter(method -> method.name().equals("value")).findFirst().orElseThrow().methodKey();
    var wrongBodies = values.bodies().stream().map(body -> !body.methodKey().equals(nestedKey) ? body
        : new CopiedAstValues.Body(body.methodKey(), original.body(outerKey).orElseThrow().root(), body.origin(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of())).toList();
    var swapped = new CopiedAstValues(values.snapshot(), values.sources(), wrongBodies, values.memberOrders(), values.associations(), values.declarationTypes(), values.scopes());

    // Act
    var duplicateFailure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(duplicate));
    var bodyFailure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(swapped));

    // Assert
    assertTrue(duplicateFailure.getMessage().contains("source") || duplicateFailure.getMessage().contains("Duplicate"));
    assertTrue(bodyFailure.getMessage().contains("body") || bodyFailure.getMessage().contains("method"));
  }

  @Test
  void givenMissingRequiredArgumentTargetsAndOptionalApplicability_whenNestedFactsAreQualified_thenOnlyRequiredLossPreventsCompletion() throws IOException {
    // Arrange
    var original = produce("class Example { void subject() { class Local { int value() { return Math.abs(-2); } } } }", AstProfile.JAVA_SOURCE_NESTED);
    var values = SourceValueCapture.capture(original.aggregate());
    var key = original.program().methods().stream().filter(method -> method.name().equals("value")).findFirst().orElseThrow().methodKey();
    var required = values.bodies().stream().map(body -> !body.methodKey().equals(key) ? body : new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(),
        body.uses().stream().filter(use -> use.role() != SyntaxRole.ARGUMENT).toList(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var optional = values.bodies().stream().map(body -> !body.methodKey().equals(key) ? body : new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
        body.invocations().stream().map(value -> new InvocationEvidence(EvidenceValue.missing(value.applicability().origin(), "Optional applicability unavailable"))).toList(),
        body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();

    // Act
    var incomplete = qualify(original, withBodies(values, required));
    var complete = qualify(original, withBodies(values, optional));
    var retained = AstCodec.decode(AstCodec.encode(complete), complete.identity());

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, incomplete.completion());
    assertEquals(original.aggregate().nodes(), incomplete.aggregate().nodes());
    assertEquals(AstCompletion.COMPLETE, complete.completion());
    assertTrue(retained.body(key).orElseThrow().evidence().invocations().stream().allMatch(value -> value.applicability().knownValue().isEmpty()));
  }

  @Test
  void givenHistoricalGenericOwnerAndMember_whenDecoded_thenCapturedTypesOriginalIdentityAndBytesRemainCompatible() throws IOException {
    // Arrange
    var current = produce("class Example<T> { class Member {} T subject(T value) { return value; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var previous = SourceAnalysisFixture.classLiteralResult(current);
    var input = new SyntaxAnalysisInput(previous.snapshot(), previous.environment(), previous.program(), Optional.of(previous.aggregate()), previous.diagnostics(), previous.coverage());
    var initialization = AstResultAdmission.admit(input, previous.profile(), previous.identity().semanticProductionRevision(), AstResult.INITIALIZATION_SOURCE_MODEL_VERSION);

    // Act
    var oldBytes = AstCodec.encode(previous);
    var retained = AstCodec.decode(oldBytes, previous.identity());
    var retainedInitialization = AstCodec.decode(AstCodec.encode(initialization), initialization.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertEquals(previous.identity(), retained.identity());
    assertEquals("Example<T>", retained.program().types().getFirst().staticType().displayName());
    assertEquals(current.program().types().getLast().staticType(), retained.program().types().getLast().staticType());
    assertArrayEquals(oldBytes, AstCodec.encode(retained));
    assertEquals(initialization.identity(), retainedInitialization.identity());
  }

  @Test
  void givenLocalDeclarations_whenAnOlderRetainedFormatIsRequested_thenNewStructureCannotAcquireAHistoricalIdentity() throws IOException {
    // Arrange
    var original = produce("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_NESTED);
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()), original.diagnostics(), original.coverage());

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstResultAdmission.admit(SourceAnalysisFixture.historicalInput(input), original.profile(), original.identity().semanticProductionRevision(), AstResult.INITIALIZATION_SOURCE_MODEL_VERSION));

    // Assert
    assertTrue(failure.getMessage().contains("java-source/15"), failure.toString());
  }

  @Test
  void givenGenericParentAndMembers_whenEarlierPublicTypeConstructorIsUsed_thenTheProgramRetainsItsDeclaredOwnerTypes() throws IOException {
    // Arrange
    var original = produce("class Example<T> { class Member<U> {} static class Static<V> {} }", AstProfile.JAVA_SOURCE_EXPANDED);
    var types = original.program().types().stream().map(type -> new JavaType(type.typeKey(), type.qualifiedName(), type.kind(), type.modifiers(),
        type.enclosingType(), type.directSuperclass(), type.directInterfaces(), type.permittedSubclasses(), type.typeParameters(), type.sourceDeclared(),
        type.fields(), type.recordComponents(), type.methods(), type.synthesizedDeclarations(), type.origins())).toList();

    // Act
    var copied = new JavaProgram(types);

    // Assert
    assertEquals(original.program().types().stream().map(JavaType::staticType).toList(), copied.types().stream().map(JavaType::staticType).toList());
  }

  @TestFactory
  Stream<DynamicTest> givenCoherentlyRebasedNestedCompilerPaths_whenCopiedValuesAreAdmitted_thenIndependentAbsoluteOwnersRejectThem() {
    return Stream.of(
        "class Example { Object field = new Object() { int nested() { return 7; } }.toString(); }",
        "class Example { Object field = (new Object() { int nested() { return 7; } }).toString(); }",
        "class Example { Object subject() { return new Object() { int nested() { return 7; } }.toString(); } }",
        "class Example { Example() { class Local { int nested() { return 7; } } new Local(); } }",
        "class Example { Example() { super(); class Local { int nested() { return 7; } } new Local(); } }",
        "class Example { Runnable field = () -> { class Local { int nested() { return 7; } } new Local(); }; }",
        "class Example { { Object value = new Object() { int nested() { return 7; } }.toString(); } }",
        "void main() {} Object subject() { return new Object() { int nested() { return 7; } }.toString(); }")
        .map(source -> DynamicTest.dynamicTest("givenRebasedCompilerPaths(" + source + ")_whenAdmitted_thenAbsoluteOwnerRejectsThem", () -> {
          // Arrange
          var original = produce(source, AstProfile.JAVA_SOURCE_NESTED);
          assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
          assertEquals(original.aggregate(), retained.aggregate());
          var values = SourceValueCapture.capture(retained.aggregate());
          var key = retained.program().methods().stream().filter(method -> method.name().equals("nested")).findFirst().orElseThrow().methodKey();
          var body = values.bodies().stream().filter(value -> value.methodKey().equals(key)).findFirst().orElseThrow();
          var compilerRoot = body.attributedOccurrences().stream().filter(value -> value.nodeKey().equals(body.root().nodeId())).findFirst().orElseThrow().compilerPath();
          var foreign = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 999).child(SyntaxRole.MEMBER, 999).child(SyntaxRole.BODY, 0);
          var occurrences = body.attributedOccurrences().stream().map(occurrence -> {
            var path = foreign;
            for (var step : occurrence.compilerPath().steps().subList(compilerRoot.steps().size(), occurrence.compilerPath().steps().size())) {
              path = path.child(step.role(), step.position());
            }
            return new AttributedBodyOccurrence(occurrence.scopeKey(), occurrence.nodeKey(), occurrence.sourceFileKey(), occurrence.sourceKind(), path);
          }).toList();
          var changed = new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), occurrences);
          var supplied = withBodies(values, values.bodies().stream().map(value -> value.methodKey().equals(key) ? changed : value).toList());

          // Act
          var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

          // Assert
          assertTrue(failure.getMessage().contains("independently copied declaration owner"), failure.toString());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
        }));
  }

  @Test
  void givenMissingAttributedDeclarationOrders_whenTheNestedProfileIsQualified_thenHistoricalAdmissionCannotClaimCurrentCompleteness() throws IOException {
    // Arrange
    var original = produce("class Example { int subject() { return 7; } }", AstProfile.JAVA_SOURCE_NESTED);
    var values = SourceValueCapture.capture(original.aggregate());
    var orders = values.memberOrders().stream().map(order -> new DeclarationMemberOrder(order.sourceFileKey(), order.ownerTypeKey(), order.members(), Optional.empty())).toList();
    var supplied = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), orders, values.associations(), values.declarationTypes(), values.scopes());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> qualify(original, supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(failure.getMessage().contains("actual owned fact"), failure.toString());
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile).result().orElseThrow();
  }

  private static JavaType withNesting(JavaType type, JavaTypeNesting nesting) {
    return new JavaType(type.typeKey(), type.qualifiedName(), type.kind(), type.modifiers(), type.enclosingType(), type.directSuperclass(), type.directInterfaces(), type.permittedSubclasses(),
        type.typeParameters(), type.sourceDeclared(), type.fields(), type.recordComponents(), type.methods(), type.synthesizedDeclarations(), type.origins(), nesting, type.staticType(), type.displayName());
  }

  private static CopiedAstValues withBodies(CopiedAstValues values, List<CopiedAstValues.Body> bodies) {
    return new CopiedAstValues(values.snapshot(), values.sources(), bodies, values.memberOrders(), values.associations(), values.declarationTypes(), values.scopes());
  }

  private static AstResult qualify(AstResult original, CopiedAstValues values) {
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(AstValueAdmission.admit(values)), original.diagnostics(), original.coverage());
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
  }
}
