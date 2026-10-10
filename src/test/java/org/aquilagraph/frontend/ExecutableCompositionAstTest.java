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
import java.util.HashSet;
import java.util.List;
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
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ExecutableCompositionAstTest {
  @TempDir Path directory;

  @Test
  void givenMixedMethodAndConstructorBodies_whenProducedAndDecoded_thenRecursiveValuesKeepCanonicalOwners() throws IOException {
    // Arrange
    var source = mixedSource();

    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.coverage(), decoded.coverage());
    assertEquals(AstCodec.materialize(original), AstCodec.materialize(decoded));
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    assertTrue(decoded.coverage().validationEvidence().unmatchedFacts().isEmpty());
    assertTrue(decoded.coverage().validationEvidence().outcomes().stream()
        .noneMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
    var methods = decoded.program().methods().stream().filter(method -> method.constructor() || method.name().equals("subject")).toList();
    assertEquals(2, methods.size());
    var ownedKeys = new HashSet<String>();
    for (var method : methods) {
      var body = decoded.body(method.methodKey()).orElseThrow();
      var nodes = body.root().descendantsAndSelf();
      assertSame(body.root(), decoded.aggregate().node(body.root().nodeId()).orElseThrow());
      assertEquals(List.of("values", "identity", "chain", "arrays", "lengths"), body.root().children(SyntaxRole.STATEMENT).stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)
          .map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
      for (var kind : List.of(SyntaxKind.ARRAY_CREATION, SyntaxKind.LAMBDA_EXPRESSION, SyntaxKind.MEMBER_REFERENCE,
          SyntaxKind.FOR_LOOP, SyntaxKind.WHILE_LOOP, SyntaxKind.DO_WHILE_LOOP, SyntaxKind.ENHANCED_FOR_LOOP,
          SyntaxKind.LABELED_STATEMENT, SyntaxKind.BREAK, SyntaxKind.CONTINUE, SyntaxKind.ASSERT, SyntaxKind.SYNCHRONIZED,
          SyntaxKind.COMPOUND_ASSIGNMENT, SyntaxKind.UPDATE, SyntaxKind.SWITCH_EXPRESSION, SyntaxKind.INSTANCE_OF,
          SyntaxKind.BINDING_PATTERN, SyntaxKind.TRY, SyntaxKind.CATCH)) {
        assertTrue(nodes.stream().anyMatch(node -> node.syntaxKind() == kind), kind.toString());
      }
      var values = local(nodes, "values");
      assertEquals(List.of("final"), values.value(SyntaxValue.LocalDeclaration.class).writtenModifiers());
      assertEquals("@Mark", written(values.children(SyntaxRole.ANNOTATION).getFirst(), source));
      var array = values.requiredChild(SyntaxRole.INITIALIZER);
      assertEquals(List.of("seed", "seed + 1"), array.children(SyntaxRole.ARRAY_ELEMENT).stream()
          .map(node -> written(node, source)).toList());
      var valueKey = values.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow();
      assertTrue(nodes.stream().filter(node -> node.value() instanceof SyntaxValue.SourceSymbol symbol && symbol.name().equals("values"))
          .allMatch(node -> node.semanticReference().orElseThrow().symbolKey().equals(Optional.of(valueKey))));
      var chain = local(nodes, "chain").requiredChild(SyntaxRole.INITIALIZER);
      var nested = chain.requiredChild(SyntaxRole.BODY);
      assertEquals(SyntaxKind.LAMBDA_EXPRESSION, nested.syntaxKind());
      assertEquals(SyntaxKind.BLOCK, nested.requiredChild(SyntaxRole.BODY).syntaxKind());
      var statement = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.TRY).findFirst().orElseThrow();
      assertEquals(List.of(SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE, SyntaxRole.FINALLY_BLOCK),
          statement.children().stream().map(child -> child.role()).toList());
      assertEquals("reader", statement.children(SyntaxRole.RESOURCE).getFirst().value(SyntaxValue.LocalDeclaration.class).name());
      assertEquals(List.of("IOException", "IllegalArgumentException"), statement.children(SyntaxRole.CATCH_CLAUSE).getFirst()
          .requiredChild(SyntaxRole.PARAMETER).requiredChild(SyntaxRole.DECLARED_TYPE).children(SyntaxRole.UNION_ALTERNATIVE).stream()
          .map(node -> written(node, source)).toList());
      var invocation = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.Invocation value
          && value.name().equals("applyAsInt")).findFirst().orElseThrow();
      assertTrue(body.invocationEvidence(invocation.nodeId()).isPresent());
      assertTrue(body.useEvidence(invocation.nodeId(), SyntaxRole.ARGUMENT, 0).isPresent());
      for (var node : nodes) {
        assertTrue(ownedKeys.add(node.nodeId()), "Different executable owners must not share a body occurrence");
        assertSame(node, decoded.node(new SourceOccurrence(decoded.identity(), node.nodeId())).orElseThrow());
        assertEquals(1, decoded.aggregate().structuralEdges().stream().filter(edge -> edge.childKey().equals(node.nodeId())).count());
        assertTrue(node.origin().isPresent());
      }
    }
    assertEquals(decoded.aggregate().nodes().size(), decoded.aggregate().orderedNodeKeys().stream().distinct().count());
    assertFalse(decoded.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
  }

  @Test
  void givenNestedLambdaWithLoopsAndResources_whenCfgIsRequested_thenOnlyTheOuterLambdaBlocksExecution() throws IOException {
    // Arrange
    var source = """
        import java.util.function.IntSupplier;
        import java.io.StringReader;
        import java.io.IOException;
        class Example {
          IntSupplier subject(int seed) {
            return () -> {
              int[] values = {seed};
              while (values[0] < 3) { values[0]++; }
              try (var reader = new StringReader("x")) { return reader.read(); }
              catch (IOException failure) { return seed; }
            };
          }
        }
        """;
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var lambda = result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).findFirst().orElseThrow();
    var bytes = AstCodec.encode(result);

    // Act
    var cfg = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
    assertTrue(cfg.methods().getFirst().graph().isEmpty());
    assertEquals(List.of(Optional.of(lambda.nodeId())), cfg.methods().getFirst().blockers().stream()
        .map(blocker -> blocker.occurrence().map(occurrence -> occurrence.nodeKey())).toList());
    assertArrayEquals(bytes, AstCodec.encode(result));
    assertTrue(lambda.requiredChild(SyntaxRole.BODY).descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.WHILE_LOOP));
  }

  @Test
  void givenMixedSource_whenApplicationSelectsAbsentRefusedOrFailedCfg_thenCompleteSourceValuesRemainIdentical() {
    // Arrange
    var snapshot = snapshot(mixedSource());
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var runner = new SourceAnalysisRunner(frontend, CfgDerivers.standard());
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var cause = new IllegalStateException("Declared CFG construction failure");

    // Act
    var astOnly = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED,
        Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var withCfg = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED,
        Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
    var original = astOnly.representation().ast().result().orElseThrow();
    var method = original.program().methods().stream().filter(value -> value.name().equals("supported")).findFirst().orElseThrow();
    var failureRunner = new SourceAnalysisRunner(frontend, RecoverableCfgFailures.atMethod(method.methodKey(), cause));
    var failed = failureRunner.deriveExisting(astOnly.representation().ast(),
        new CfgRequest(original.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, astOnly.completion());
    assertEquals(AnalysisCompletion.PARTIAL, withCfg.completion());
    assertEquals(AnalysisCompletion.FAILED, failed.completion());
    assertSame(cause, failed.representation().cfg().orElseThrow().methods().getFirst().failure().orElseThrow());
    for (var run : List.of(astOnly, withCfg, failed)) {
      var actual = run.representation().ast().result().orElseThrow();
      assertEquals(AstCompletion.COMPLETE, actual.completion(), actual.diagnostics().toString());
      assertEquals(original.identity(), actual.identity());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(actual));
    }
  }

  @Test
  void givenForeignProfileOccurrence_whenQueryingNestedSyntax_thenTheExactSourceResultOwnerIsRequired() throws IOException {
    // Arrange
    var source = mixedSource();
    var expanded = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var bounded = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var lambda = expanded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).findFirst().orElseThrow();
    var foreign = new SourceOccurrence(bounded.identity(), lambda.nodeId());

    // Act
    var error = assertThrows(IllegalArgumentException.class, () -> expanded.node(foreign));

    // Assert
    assertEquals(AstCompletion.COMPLETE, expanded.completion(), expanded.diagnostics().toString());
    assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
    assertNotEquals(expanded.identity(), bounded.identity());
    assertFalse(error.getMessage().isBlank());
    assertSame(lambda, expanded.node(new SourceOccurrence(expanded.identity(), lambda.nodeId())).orElseThrow());
  }

  @TestFactory
  Stream<DynamicTest> givenUnsupportedDeclarationOrErroneousNestedSource_whenProduced_thenCompletionRemainsTruthful() {
    return Stream.of(
        "class Example { Runnable subject() { class Local {} return () -> {}; } }",
        "class Example { Runnable subject() { return new Runnable() { public void run() {} }; } }",
        mixedSource().replace("seed + 1", "missing + 1"))
        .map(source -> DynamicTest.dynamicTest(source.contains("missing") ? "compiler error" : source.contains("Local") ? "local type" : "anonymous type", () -> {
          // Arrange
          var profile = AstProfile.JAVA_SOURCE_EXPANDED;

          // Act
          var result = produce(source, profile);

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, result.completion());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()), result.diagnostics().toString());
          if (source.contains("missing")) {
            assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.")));
          } else {
            assertTrue(result.aggregate().nodes().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
          }
          assertEquals(result.completion(), AstCodec.decode(AstCodec.encode(result), result.identity()).completion());
        }));
  }

  @Test
  void givenMissingRequiredArgumentFactInsideNestedLambda_whenReadmitted_thenSourceIsIncompleteAndStorageIsNotAcquired() throws IOException {
    // Arrange
    var original = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot(mixedSource()), CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var captured = CopiedAstValues.capture(original.aggregate().orElseThrow());
    var invocationKeys = captured.bodies().stream().flatMap(body -> body.root().descendantsAndSelf().stream())
        .filter(node -> node.value() instanceof SyntaxValue.Invocation value && value.name().equals("applyAsInt"))
        .map(SyntaxNode::nodeId).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(),
        body.uses().stream().filter(use -> !invocationKeys.contains(use.consumerNodeId()) || use.role() != SyntaxRole.ARGUMENT).toList(),
        body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var supplied = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), captured.sources(), bodies, captured.memberOrders(),
        captured.associations(), captured.declarationTypes()));
    var input = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(supplied),
        JavaCoverageTestSupport.diagnostics(original, "copied-nested-argument-qualification"));
    var retention = new AstRetentionService(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); });

    // Act
    var outcome = AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
    var result = AstCodec.decode(AstCodec.encode(outcome.result().orElseThrow()), outcome.result().orElseThrow().identity());
    var publication = retention.retainAst(outcome);

    // Assert
    assertEquals(2, invocationKeys.size());
    assertTrue(captured.bodies().stream().flatMap(body -> body.uses().stream())
        .anyMatch(use -> invocationKeys.contains(use.consumerNodeId()) && use.role() == SyntaxRole.ARGUMENT));
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
    assertTrue(result.aggregate().nodes().stream().filter(node -> invocationKeys.contains(node.nodeId()))
        .allMatch(node -> node.children(SyntaxRole.ARGUMENT).size() == 1));
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot(source), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
  }

  private InputSnapshot snapshot(String source) {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
  }

  private static SyntaxNode local(List<SyntaxNode> nodes, String name) {
    return nodes.stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration value && value.name().equals(name))
        .findFirst().orElseThrow();
  }

  private static String written(SyntaxNode node, String source) {
    var origin = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
  }

  private static String mixedSource() {
    var body = """
        @Mark final int[] values = new int[]{seed, seed + 1};
        IntUnaryOperator identity = (var value) -> value;
        Supplier<IntSupplier> chain = () -> () -> {
          int sum = 0;
          outer: for (int index = 0; index < values.length; index++) {
            sum += values[index];
            if (sum == 0) continue outer;
            if (sum > 20) break outer;
          }
          while (sum < 1) { ++sum; }
          do { sum--; } while (sum > 30);
          for (int value : values) { sum ^= value; }
          synchronized (values) { assert sum >= 0 : "negative"; }
          try (var reader = new StringReader("value")) {
            int selected = switch (input) {
              case String text when !text.isEmpty() -> text.length();
              case null, default -> seed;
            };
            if (input instanceof String text) { sum += text.length(); }
            return identity.applyAsInt(sum + selected + reader.read());
          } catch (IOException | IllegalArgumentException failure) { return seed; }
          finally { int copy = seed; }
        };
        IntFunction<int[]> arrays = int[]::new;
        Function<String, Integer> lengths = String::length;
        """;
    return """
        import java.io.IOException;
        import java.io.StringReader;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.util.function.Function;
        import java.util.function.IntFunction;
        import java.util.function.IntSupplier;
        import java.util.function.IntUnaryOperator;
        import java.util.function.Supplier;
        @Target(ElementType.LOCAL_VARIABLE) @interface Mark {}
        class Example {
          Example(Object input, int seed) { %s chain.get(); }
          IntSupplier subject(Object input, int seed) { %s return chain.get(); }
          int supported(int seed) { return seed + 1; }
        }
        """.formatted(body, body);
  }
}
