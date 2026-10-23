package org.aquilagraph.frontend.internal;

import org.aquilagraph.source.internal.SourceValueCapture;
import org.aquilagraph.source.admission.AstProductionAdmission;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class AssertionMonitorAstTest {
  @TempDir Path directory;

  @Test
  void givenWrittenAssertionsAndMonitors_whenProducedAndDecoded_thenExactChildrenFactsAndOriginsSurviveClosure() throws IOException {
    // Arrange
    var text = """
        class Example {
          int detail() { return 2; }
          int subject(Object lock, Object[] locks, boolean flag, Boolean boxed, int n) {
            assert flag;
            assert boxed : detail();
            assert n > 0 : n++;
            synchronized (lock) {}
            synchronized (locks[0]) {
              try {
                synchronized (this) { assert flag : "nested"; if (flag) return n; }
                throw new IllegalStateException();
              } catch (IllegalStateException error) { return n; }
            }
          }
        }
        """;
    var original = produce(text, AstProfile.JAVA_SOURCE_EXPANDED);
    // Act
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var nodes = body(decoded);
    var assertions = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.ASSERT).toList();
    var monitors = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.SYNCHRONIZED).toList();
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.coverage(), decoded.coverage());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    assertEquals(4, assertions.size());
    assertEquals(List.of(SyntaxRole.CONDITION), roles(assertions.getFirst()));
    assertTrue(assertions.getFirst().optionalChild(SyntaxRole.DETAIL).isEmpty());
    assertEquals(List.of(SyntaxRole.CONDITION, SyntaxRole.DETAIL), roles(assertions.get(1)));
    assertEquals(JavaTypeCategory.DECLARED, resolved(assertions.get(1).requiredChild(SyntaxRole.CONDITION)));
    assertEquals(SyntaxKind.INVOCATION, assertions.get(1).requiredChild(SyntaxRole.DETAIL).syntaxKind());
    assertEquals("detail", assertions.get(1).requiredChild(SyntaxRole.DETAIL).value(SyntaxValue.Invocation.class).name());
    assertEquals(SyntaxKind.UPDATE, assertions.get(2).requiredChild(SyntaxRole.DETAIL).syntaxKind());
    assertEquals(JavaTypeCategory.BOOLEAN, resolved(assertions.get(2).requiredChild(SyntaxRole.CONDITION)));
    assertEquals(3, monitors.size());
    assertEquals(List.of(SyntaxRole.MONITOR, SyntaxRole.BODY), roles(monitors.getFirst()));
    assertTrue(monitors.getFirst().requiredChild(SyntaxRole.BODY).children().isEmpty());
    assertEquals(SyntaxKind.PARENTHESIZED, monitors.get(1).requiredChild(SyntaxRole.MONITOR).syntaxKind());
    assertEquals(SyntaxKind.ARRAY_ACCESS, monitors.get(1).requiredChild(SyntaxRole.MONITOR).requiredChild(SyntaxRole.EXPRESSION).syntaxKind());
    assertTrue(monitors.stream().allMatch(node -> node.requiredChild(SyntaxRole.BODY).syntaxKind() == SyntaxKind.BLOCK));
    assertTrue(nodes.stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
    assertTrue(Stream.concat(assertions.stream(), monitors.stream()).allMatch(node -> node.semanticReference().isEmpty()
        && node.type() == SyntaxType.NotApplicable.INSTANCE && node.origin().isPresent()));
    assertEquals("assert flag;", written(assertions.getFirst(), text));
    assertEquals("synchronized (lock) {}", written(monitors.getFirst(), text));
    assertTrue(nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS).allMatch(node -> node.semanticReference().isPresent()));
    var properties = decoded.coverage().validationEvidence().outcomes().stream().filter(outcome -> outcome.occurrence()
        .map(occurrence -> occurrence.classification().feature().identifier().startsWith("AssertTree::")
            || occurrence.classification().feature().identifier().startsWith("SynchronizedTree::")).orElse(false)).toList();
    assertEquals(21, properties.size());
    assertTrue(properties.stream().noneMatch(outcome -> outcome.support().mode().name().equals("FAILED")));
  }

  @Test
  void givenExpandedAssertionMonitorRequest_whenCfgIsSelected_thenCompleteSourceContentSurvivesRealRefusal() throws IOException {
    // Arrange
    var snapshot = snapshot("class Example { void subject(Object lock, boolean flag) { assert flag; synchronized (lock) {} } }");
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    // Act
    var astOnly = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration(), AstProfile.JAVA_SOURCE_EXPANDED,
        Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var selected = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration(), AstProfile.JAVA_SOURCE_EXPANDED,
        Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, astOnly.completion());
    assertEquals(AnalysisCompletion.PARTIAL, selected.completion());
    var original = astOnly.representation().ast().result().orElseThrow();
    var refused = selected.representation().ast().result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, refused.completion());
    assertEquals(original.identity(), refused.identity());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(refused));
    assertEquals(MethodCfgState.UNSUPPORTED, selected.representation().cfg().orElseThrow().methods().stream()
        .filter(method -> refused.method(method.methodKey()).orElseThrow().name().equals("subject")).findFirst().orElseThrow().state());
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidAssertionOrMonitor_whenCompiled_thenActualCompilerErrorsPreventCompletion() {
    return Stream.of("assert 1;", "synchronized (1) {}", "assert true : missing;", "assert true : consume();", "synchronized (null) {}")
        .map(statement -> DynamicTest.dynamicTest(statement, () -> {
          // Arrange
          var source = "class Example { void consume() {} void subject() { " + statement + " } }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, result.completion());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")),
              result.diagnostics().toString());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenLegalDetailAndMonitorVariants_whenProduced_thenExpandedCompletesAndBoundedRemainsIncomplete() {
    return Stream.of("assert true : null;", "assert true : new int[0];", "assert true : 'x';", "assert true : 2.0;",
        "synchronized ((Object) null) {}", "synchronized (new int[0]) {}", "synchronized ((Object) this) { assert true; }")
        .map(statement -> DynamicTest.dynamicTest(statement, () -> {
          // Arrange
          var source = "class Example { void subject() { " + statement + " } }";
          // Act
          var expanded = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var bounded = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);
          // Assert
          assertEquals(AstCompletion.COMPLETE, expanded.completion(), expanded.diagnostics().toString());
          assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
          assertEquals(expanded.aggregate(), bounded.aggregate());
        }));
  }

  @Test
  void givenMissingOrReorderedRequiredChildren_whenConstructed_thenIntrinsicAdmissionRejectsMalformedStatements() throws IOException {
    // Arrange
    var result = produce("class Example { void subject(Object lock) { assert true : 1; synchronized (lock) {} } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var assertion = body(result).stream().filter(node -> node.syntaxKind() == SyntaxKind.ASSERT).findFirst().orElseThrow();
    var monitor = body(result).stream().filter(node -> node.syntaxKind() == SyntaxKind.SYNCHRONIZED).findFirst().orElseThrow();
    // Act
    var absent = assertThrows(IllegalArgumentException.class, () -> copy(assertion, assertion.type(), assertion.children().subList(1, 2)));
    var reversed = assertThrows(IllegalArgumentException.class, () -> copy(assertion, assertion.type(), assertion.children().reversed()));
    var missingBlock = assertThrows(IllegalArgumentException.class, () -> copy(monitor, monitor.type(), monitor.children().subList(0, 1)));
    var reversedMonitor = assertThrows(IllegalArgumentException.class, () -> copy(monitor, monitor.type(), monitor.children().reversed()));
    // Assert
    assertTrue(absent.getMessage().contains("cardinality"));
    assertTrue(missingBlock.getMessage().contains("cardinality"));
    assertTrue(reversed.getMessage().contains("condition before"));
    assertTrue(reversedMonitor.getMessage().contains("monitor before"));
  }

  @Test
  void givenMissingRequiredMonitorType_whenCopiedFactsAreAdmitted_thenIncompleteSourceCannotAcquireRetentionStorage() throws IOException {
    // Arrange
    var original = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot("class Example { void subject(Object lock) { synchronized (lock) {} } }"), configuration(), JavaTranslationPolicy.legacyAnalysis());
    var captured = SourceValueCapture.capture(original.aggregate().orElseThrow());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
        missingType(source.root(), replacements), source.nodes().stream().map(node -> missingType(node, replacements)).toList(),
        source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), missingType(body.root(), replacements),
        body.origin(), List.of(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var supplied = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes()));
    var input = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(supplied), JavaCoverageTestSupport.emissions(original));
    // Act
    var outcome = AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
    var decoded = AstCodec.decode(AstCodec.encode(outcome.result().orElseThrow()), outcome.result().orElseThrow().identity());
    var publication = new AstRetentionService(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(outcome);
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
    assertEquals(SyntaxType.Unresolved.INSTANCE, body(decoded).stream().filter(node -> node.syntaxKind() == SyntaxKind.SYNCHRONIZED)
        .findFirst().orElseThrow().requiredChild(SyntaxRole.MONITOR).type());
  }

  private static SyntaxNode missingType(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements) {
    var previous = replacements.get(node);
    if (previous != null) return previous;
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), missingType(child.node(), replacements))).toList();
    var type = node.structuralPath().steps().getLast().role() == SyntaxRole.MONITOR ? SyntaxType.Unresolved.INSTANCE : node.type();
    var copied = node.origin().isPresent() ? copy(node, type, children)
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference());
    replacements.put(node, copied);
    return copied;
  }

  private static SyntaxNode copy(SyntaxNode node, SyntaxType type, List<SyntaxChild> children) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference(), node.origin().orElseThrow());
  }

  private AstResult produce(String text, AstProfile profile) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot(text), configuration(), profile).result().orElseThrow();
  }

  private InputSnapshot snapshot(String text) {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), text)));
  }

  private static CompilerConfiguration configuration() {
    return CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  }

  private static List<SyntaxNode> body(AstResult result) {
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    return result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
  }

  private static List<SyntaxRole> roles(SyntaxNode node) {
    return node.children().stream().map(SyntaxChild::role).toList();
  }

  private static JavaTypeCategory resolved(SyntaxNode node) {
    return ((SyntaxType.Resolved) node.type()).identity().category();
  }

  private static String written(SyntaxNode node, String text) {
    var origin = node.origin().orElseThrow();
    return text.substring((int) origin.startOffset(), (int) origin.endOffsetExclusive());
  }
}
