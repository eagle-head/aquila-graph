package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.GraphSourceRef;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProducer;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExpandedProfileTest {
  @TempDir Path directory;

  @Test
  void givenSupportedSource_whenRequestingBothProfiles_thenClosedStructureMatchesAndProfileIdentitiesDiffer() {
    // Arrange
    AstProducer producer = new JavaFrontend(new JavaSourceLoader());
    var input = snapshot("class Stored { int value(int x) { return x + 7; } void empty() { return; } }");

    // Act
    var bounded = producer.produce(new AstProductionRequest(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED));
    var expanded = producer.produce(new AstProductionRequest(input, configuration(), AstProfile.JAVA_SOURCE_EXPANDED));
    var left = bounded.result().orElseThrow();
    var right = expanded.result().orElseThrow();
    var method = right.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
    var returned = right.body(method.methodKey()).orElseThrow().root().requiredChild(SyntaxRole.STATEMENT);
    var sum = returned.requiredChild(SyntaxRole.EXPRESSION);
    var empty = right.program().methods().stream().filter(value -> value.name().equals("empty")).findFirst().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(right), right.identity());

    // Assert
    assertEquals("java-source-bounded/1", bounded.profile().version());
    assertEquals("java-source-expanded/1", expanded.profile().version());
    assertEquals(AstCompletion.COMPLETE, bounded.completion(), bounded.diagnostics().toString());
    assertEquals(AstCompletion.COMPLETE, expanded.completion(), expanded.diagnostics().toString());
    assertEquals(left.aggregate(), right.aggregate());
    assertEquals(left.program(), right.program());
    assertEquals(left.coverage(), right.coverage());
    assertEquals(left.diagnostics(), right.diagnostics());
    assertNotEquals(left.identity(), right.identity());
    assertNotEquals(left.identity().canonicalContentDigest(), right.identity().canonicalContentDigest());
    assertEquals(AstProfile.JAVA_SOURCE_EXPANDED.version(), right.identity().astProfileVersion());
    assertEquals(SyntaxKind.RETURN, returned.syntaxKind());
    assertEquals(JavaSourceKind.PLUS, sum.sourceKind());
    assertEquals(SyntaxKind.VARIABLE_ACCESS, sum.requiredChild(SyntaxRole.LEFT_OPERAND).syntaxKind());
    assertEquals(JavaSourceKind.INT_LITERAL, sum.requiredChild(SyntaxRole.RIGHT_OPERAND).sourceKind());
    assertTrue(sum.requiredChild(SyntaxRole.LEFT_OPERAND).semanticReference().orElseThrow().symbolKey().isPresent());
    assertTrue(right.body(empty.methodKey()).orElseThrow().root().requiredChild(SyntaxRole.STATEMENT)
        .optionalChild(SyntaxRole.EXPRESSION).isEmpty());
    assertEquals(right.identity(), decoded.identity());
    assertEquals(right.aggregate(), decoded.aggregate());
    assertEquals(right.coverage(), decoded.coverage());
    assertEquals(right.bodyRef(method.methodKey()), decoded.bodyRef(method.methodKey()));
    assertArrayEquals(AstCodec.encode(right), AstCodec.encode(decoded));
    assertThrows(AstContractException.class, () -> AstCodec.decode(AstCodec.encode(left), right.identity()));
  }

  @Test
  void givenSameLocalKeyUnderDifferentProfiles_whenResolvingOccurrences_thenExactOwnerIsRequired() {
    // Arrange
    AstProducer producer = new JavaFrontend(new JavaSourceLoader());
    var input = snapshot("class Stored { int value() { return 7; } }");
    var bounded = producer.produce(new AstProductionRequest(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED)).result()
        .orElseThrow();
    var expanded = producer.produce(new AstProductionRequest(input, configuration(), AstProfile.JAVA_SOURCE_EXPANDED)).result()
        .orElseThrow();
    var method = bounded.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
    var node = bounded.body(method.methodKey()).orElseThrow().root().requiredChild(SyntaxRole.STATEMENT);
    var owned = new SourceOccurrence(bounded.identity(), node.nodeId());
    var foreign = new SourceOccurrence(expanded.identity(), node.nodeId());

    // Act and Assert
    assertEquals(node.nodeId(), expanded.body(method.methodKey()).orElseThrow().root().requiredChild(SyntaxRole.STATEMENT).nodeId());
    assertNotEquals(bounded.identity(), expanded.identity());
    assertSame(node, bounded.node(owned).orElseThrow());
    assertThrows(IllegalArgumentException.class, () -> bounded.node(foreign));
    assertTrue(bounded.node(new SourceOccurrence(bounded.identity(), "unknown-occurrence")).isEmpty());
  }

  @Test
  void givenMatchingLocalKeysWithForeignProfileIdentity_whenAssociatingGraphSource_thenForeignOccurrenceIsRejected() {
    // Arrange
    AstProducer producer = new JavaFrontend(new JavaSourceLoader());
    var input = snapshot("class Stored { int value() { return 7; } }");
    var bounded = producer.produce(new AstProductionRequest(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED)).result()
        .orElseThrow();
    var expanded = producer.produce(new AstProductionRequest(input, configuration(), AstProfile.JAVA_SOURCE_EXPANDED)).result()
        .orElseThrow();
    var method = bounded.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
    var body = bounded.bodyRef(method.methodKey()).orElseThrow();
    var key = bounded.body(method.methodKey()).orElseThrow().root().requiredChild(SyntaxRole.STATEMENT).nodeId();
    var owned = new SourceOccurrence(bounded.identity(), key);
    var foreign = new SourceOccurrence(expanded.identity(), key);

    // Act and Assert
    assertEquals(key, expanded.body(method.methodKey()).orElseThrow().root().requiredChild(SyntaxRole.STATEMENT).nodeId());
    var association = new GraphSourceRef(body, Optional.of(owned), "operation/RETURN");
    assertEquals(body, association.body());
    assertEquals(Optional.of(owned), association.occurrence());
    assertThrows(IllegalArgumentException.class, () -> new GraphSourceRef(body, Optional.of(foreign), "operation/RETURN"));
  }

  @Test
  void givenEmptyCapture_whenRequestingExpanded_thenUnavailableProfileIsRetainedWithoutCompilerAcquisition() throws IOException {
    // Arrange
    var calls = new AtomicInteger();
    var frontend = new JavaFrontend(new JavaSourceLoader(), new JavaAnalysisPipeline(
        new AnalysisEnvironmentCapture(new CompilerDependencyCapture()), () -> {
          calls.incrementAndGet();
          throw new AssertionError("Empty capture must not acquire a compiler");
        }));
    var input = InputSnapshot.fromSources(directory, List.of());

    // Act
    var bounded = frontend.produce(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = frontend.produce(input, configuration(), AstProfile.JAVA_SOURCE_EXPANDED);

    // Assert
    assertEquals(AstCompletion.UNAVAILABLE, expanded.completion());
    assertEquals(AstProfile.JAVA_SOURCE_EXPANDED, expanded.profile());
    assertEquals(input, expanded.capturedInput().orElseThrow());
    assertEquals(configuration(), expanded.requestedConfiguration().orElseThrow());
    assertEquals(bounded.coverageQualification(), expanded.coverageQualification());
    assertTrue(expanded.capturedEnvironment().isEmpty());
    assertTrue(expanded.result().isEmpty());
    assertNotEquals(bounded.sourceSubjectDigest(), expanded.sourceSubjectDigest());
    assertEquals(0, calls.get());
  }

  @Test
  void givenUnknownProfile_whenProducingOrAdmitting_thenUnsupportedVersionIsRejected() throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var input = snapshot("class Stored { int value() { return 1; } }");
    var facts = frontend.analyze(input, configuration());
    var unknown = new AstProfile("java-source-expanded/99");

    // Act
    var production = assertThrows(IllegalArgumentException.class,
        () -> frontend.produce(new AstProductionRequest(input, configuration(), unknown)));
    var admission = assertThrows(AstContractException.class, () -> AstResultAdmission.admit(facts, unknown, "javac-public/1"));

    // Assert
    assertFalse(unknown.isSupported());
    assertTrue(production.getMessage().contains(unknown.version()));
    assertTrue(admission.getCause().getMessage().contains(unknown.version()));
  }

  @Test
  void givenUnsupportedFinallyOrCompilerError_whenRequestingExpanded_thenIncompleteSourcePreservesTheActualGap() {
    // Arrange
    AstProducer producer = new JavaFrontend(new JavaSourceLoader());
    var unsupported = snapshot("class Stored { int value(int x) { try {} finally { x = 0; } return x; } }");
    var erroneous = snapshot("class Stored { int value() { return missing; } }");

    // Act
    var selection = producer.produce(new AstProductionRequest(unsupported, configuration(), AstProfile.JAVA_SOURCE_EXPANDED));
    var error = producer.produce(new AstProductionRequest(erroneous, configuration(), AstProfile.JAVA_SOURCE_EXPANDED));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, selection.completion());
    assertTrue(selection.result().orElseThrow().aggregate().nodes().stream()
        .anyMatch(node -> node.sourceKind() == JavaSourceKind.TRY && node.syntaxKind() == SyntaxKind.UNSUPPORTED));
    assertTrue(selection.diagnostics().stream().anyMatch(value -> value.isError()));
    assertEquals(AstCompletion.INCOMPLETE, error.completion());
    assertTrue(error.diagnostics().stream().anyMatch(value -> value.code().contains("cant.resolve")), error.diagnostics().toString());
    var copied = AstCodec.decode(AstCodec.encode(selection.result().orElseThrow()), selection.result().orElseThrow().identity());
    assertEquals(selection.diagnostics(), copied.diagnostics());
    assertEquals(AstCompletion.INCOMPLETE, copied.completion());
  }

  @Test
  void givenExpandedApplicationRequest_whenDerivingExistingWithNativeRefusal_thenSourceQualificationAndContentStayUnchanged() {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var runner = new SourceAnalysisRunner(frontend, CfgDerivers.standard());
    var input = snapshot("class Stored { int choose(int x) { return switch (x) { default -> 7; }; } }");
    var request = new AnalysisRequest(new SourceInput.Snapshot(input), configuration(), AstProfile.JAVA_SOURCE_EXPANDED,
        Optional.empty(), PublicationIntent.DO_NOT_SAVE);

    // Act
    var sourceOnly = runner.analyze(request);
    var ast = sourceOnly.representation().ast();
    var source = ast.result().orElseThrow();
    var method = source.program().methods().stream().filter(value -> value.name().equals("choose")).findFirst().orElseThrow();
    var originalBytes = AstCodec.encode(source);
    var supported = runner.deriveExisting(ast, new CfgRequest(source.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
    var refused = runner.deriveExisting(ast,
        new CfgRequest(source.identity(), List.of(method.methodKey()), CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS));

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, sourceOnly.completion());
    assertTrue(sourceOnly.representation().cfg().isEmpty());
    assertEquals(CfgState.COMPLETE, supported.representation().cfg().orElseThrow().state());
    assertEquals(MethodCfgState.AVAILABLE, supported.representation().cfg().orElseThrow().methods().getFirst().state());
    assertFalse(supported.representation().cfg().orElseThrow().methods().getFirst().graph().orElseThrow().operationNodes().isEmpty());
    assertEquals(AnalysisCompletion.PARTIAL, refused.completion());
    assertEquals(MethodCfgState.UNSUPPORTED, refused.representation().cfg().orElseThrow().methods().getFirst().state());
    assertSame(ast, refused.representation().ast());
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertTrue(ast.diagnostics().isEmpty());
    assertArrayEquals(originalBytes, AstCodec.encode(source));
  }

  private InputSnapshot snapshot(String source) {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), source)));
  }

  private static CompilerConfiguration configuration() {
    return CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  }
}
