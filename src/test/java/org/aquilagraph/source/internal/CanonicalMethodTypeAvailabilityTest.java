package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.internal.JavaCoverageTestSupport;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaParameter;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class CanonicalMethodTypeAvailabilityTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenUnavailableFormalContexts_whenRecapturedAndRetained_thenOriginalIncompleteDeclarationsRemain() {
    return profiles().flatMap(profile -> formalContexts().map(context -> DynamicTest.dynamicTest(
        "given" + context.name() + "In" + profile.version() + "_whenRecapturedAndRetained_thenOriginalIncompleteDeclarationRemains", () -> {
          // Arrange
          var original = produce(profile, context.source());
          var input = input(original);
          var captured = JavaCoverageTestSupport.capture(input, profile);
          var evidence = original.coverage().validationEvidence();

          // Act
          var unchanged = captured.withFacts(input.program(), input.aggregate());
          var admitted = AstResultAdmission.admit(unchanged, profile, "javac-public/15");
          var decoded = AstCodec.decode(AstCodec.encode(admitted), admitted.identity());

          // Assert
          assertEquals(evidence.writtenInventory(), captured.inventory());
          assertEquals(evidence.attributedExpectations(), captured.attributed());
          assertEquals(AstCompletion.INCOMPLETE, original.completion());
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(context.signature(), subject(decoded, context.methodName()).signature());
          var unavailable = subject(decoded, context.methodName()).parameters().getFirst().staticType();
          assertEquals(new JavaTypeIdentity(context.firstErasure().endsWith("[]") ? JavaTypeCategory.ARRAY : JavaTypeCategory.ERROR,
              context.firstErasure(), context.firstErasure()), unavailable);
          assertEquals(original.program(), decoded.program());
          assertEquals(original.aggregate(), decoded.aggregate());
          assertFalse(decoded.diagnostics().isEmpty());
          assertTrue(decoded.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("compiler.err.cant.resolve.location")));
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenErroneousCompactRecordComponent_whenProducing_thenUnavailableSourceRetainsItsDiagnostics() {
    return profiles().map(profile -> DynamicTest.dynamicTest(
        "givenErroneousCompactRecordComponentIn" + profile.version() + "_whenProducing_thenUnavailableSourceRetainsItsDiagnostics", () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
              "record Example(int known, Missing unavailable) { Example {} }")));

          // Act
          var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);

          // Assert
          assertEquals(AstCompletion.UNAVAILABLE, outcome.completion());
          assertTrue(outcome.result().isEmpty());
          assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("compiler.err.cant.resolve.location")));
          assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-record-component")));
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableFormalWithOriginalCompilerFacts_whenOnlySignatureChanges_thenAdmissionRejectsEveryContradiction() {
    return profiles().flatMap(profile -> formalContexts().flatMap(context -> signatureChanges(context).map(change -> DynamicTest.dynamicTest(
        "given" + context.name() + "In" + profile.version() + "_when" + change.name() + "Changes_thenCanonicalContradictionRejects", () -> {
          // Arrange
          var original = produce(profile, context.source());
          var input = input(original);
          var captured = JavaCoverageTestSupport.capture(input, profile);
          var method = subject(original, context.methodName());
          var evidence = original.coverage().validationEvidence();
          assertEquals(evidence.writtenInventory(), captured.inventory());
          assertEquals(evidence.attributedExpectations(), captured.attributed());
          var unchanged = captured.withFacts(input.program(), input.aggregate());
          assertEquals(AstCompletion.INCOMPLETE, AstResultAdmission.admit(unchanged, profile, "javac-public/15").completion());
          var changed = new JavaMethod(method.methodKey(), method.ownerTypeKey(), method.name(), change.signature(), method.returnType(),
              method.parameters(), method.modifiers(), method.constructor(), method.sourceDeclared(), method.hasBody(), method.origins());
          var program = new JavaProgram(original.program().types().stream().map(type -> withMethod(type, changed)).toList());

          // Act
          var failure = assertThrows(IllegalArgumentException.class, () -> captured.withFacts(program, input.aggregate()), changed.signature());

          // Assert
          assertTrue(failure.getMessage().contains("Canonical method declaration contradicts its retained written header"), failure.toString());
          assertEquals(method.methodKey(), changed.methodKey());
          assertEquals(method.parameters(), changed.parameters());
          assertEquals(context.signature(), method.signature());
          assertEquals(evidence.writtenInventory(), unchanged.languageFeatureCoverageResult().validationEvidence().writtenInventory());
          assertEquals(evidence.attributedExpectations(), unchanged.languageFeatureCoverageResult().validationEvidence().attributedExpectations());
        }))));
  }

  @Test
  void givenErroneousFormalWithCompilerSignature_whenRetained_thenTheOriginalSignatureAndIncompleteOutcomeRemain() throws IOException {
    // Arrange
    var original = produce();

    // Act
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var method = subject(decoded);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals("subject(Missing,int)", method.signature());
    assertEquals(new JavaTypeIdentity(JavaTypeCategory.ERROR, "Missing", "Missing"), method.parameters().getFirst().staticType());
    assertEquals(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), method.parameters().getLast().staticType());
    assertFalse(decoded.diagnostics().isEmpty());
    assertEquals(original.identity(), decoded.identity());
  }

  @Test
  void givenUnknownAndKnownFormalTypes_whenKnownCanonicalParameterChanges_thenAdmissionRejectsTheKnownContradiction() throws IOException {
    // Arrange
    var original = produce();
    var method = subject(original);
    var parameter = method.parameters().getLast();
    var changedParameter = new JavaParameter(parameter.parameterKey(), parameter.name(),
        new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long"), parameter.sourceDeclared(), parameter.origins());
    var changed = new JavaMethod(method.methodKey(), method.ownerTypeKey(), method.name(), method.signature(), method.returnType(),
        List.of(method.parameters().getFirst(), changedParameter), method.modifiers(), method.constructor(), method.sourceDeclared(),
        method.hasBody(), method.origins());
    var program = new JavaProgram(original.program().types().stream().map(type -> withMethod(type, changed)).toList());
    var captured = JavaCoverageTestSupport.capture(input(original), original.profile());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> captured.withFacts(program, Optional.of(original.aggregate())));

    // Assert
    assertTrue(failure.getMessage().contains("Canonical method declaration contradicts its retained written header"));
    assertEquals(AstCompletion.INCOMPLETE, original.completion());
  }

  private AstResult produce() throws IOException {
    return produce(AstProfile.JAVA_SOURCE_EXPANDED, "class Example { void subject(Missing unavailable, int known) {} }");
  }

  private AstResult produce(AstProfile profile, String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        profile).result().orElseThrow();
  }

  private static SyntaxAnalysisInput input(AstResult original) {
    return new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
  }

  private static Stream<AstProfile> profiles() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED, AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static Stream<FormalContext> formalContexts() {
    return Stream.of(
        new FormalContext("ErroneousScalar", "class Example { void subject(Missing unavailable, int known) {} }", "subject", "Missing", "int"),
        new FormalContext("ErroneousGeneric", "class Example { void subject(Missing<String> unavailable, int known) {} }", "subject", "Missing<java.lang.String>", "int"),
        new FormalContext("UnavailableArray", "class Example { void subject(Missing[] unavailable, int known) {} }", "subject", "Missing[]", "int"),
        new FormalContext("KnownArray", "class Example { void subject(Missing unavailable, int[] known) {} }", "subject", "Missing", "int[]"),
        new FormalContext("KnownGenericArray", "class Example { <T extends Number> void subject(Missing unavailable, T[] known) {} }", "subject", "Missing", "java.lang.Number[]"),
        new FormalContext("ErroneousConstructor", "class Example { Example(Missing unavailable, int known) {} }", "<init>", "Missing", "int"));
  }

  private record FormalContext(String name, String source, String methodName, String firstErasure, String knownErasure) {
    String signature() {
      return methodName + "(" + firstErasure + "," + knownErasure + ")";
    }
  }

  private static Stream<SignatureChange> signatureChanges(FormalContext context) {
    return Stream.of(
        new SignatureChange("KnownParameterType", context.methodName() + "(" + context.firstErasure() + ",long)"),
        new SignatureChange("ExecutableName", "changed(" + context.firstErasure() + "," + context.knownErasure() + ")"),
        new SignatureChange("RemovedParameter", context.methodName() + "(" + context.firstErasure() + ")"),
        new SignatureChange("AddedParameter", context.methodName() + "(" + context.firstErasure() + "," + context.knownErasure() + ",boolean)"),
        new SignatureChange("UnavailableParameterText", context.methodName() + "(OtherMissing," + context.knownErasure() + ")"));
  }

  private record SignatureChange(String name, String signature) {
  }

  private static JavaMethod subject(AstResult result) {
    return subject(result, "subject");
  }

  private static JavaMethod subject(AstResult result, String name) {
    return result.program().methods().stream().filter(method -> method.name().equals(name)).findFirst().orElseThrow();
  }

  private static JavaType withMethod(JavaType type, JavaMethod changed) {
    return new JavaType(type.typeKey(), type.qualifiedName(), type.kind(), type.modifiers(), type.enclosingType(), type.directSuperclass(),
        type.directInterfaces(), type.permittedSubclasses(), type.typeParameters(), type.sourceDeclared(), type.fields(), type.recordComponents(),
        type.methods().stream().map(method -> method.methodKey().equals(changed.methodKey()) ? changed : method).toList(),
        type.synthesizedDeclarations(), type.origins(), type.nesting(), type.staticType(), type.displayName());
  }
}
