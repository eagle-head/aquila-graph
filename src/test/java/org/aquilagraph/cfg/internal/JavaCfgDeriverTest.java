package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.cfg.api.CfgConstructionException;
import org.aquilagraph.cfg.api.CfgDerivationException;

import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.GraphIdentity;
import org.aquilagraph.identity.api.GraphNodeKey;
import org.aquilagraph.identity.api.GraphValueKey;
import org.aquilagraph.identity.api.GraphEdgeKey;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SyntaxKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaCfgDeriverTest {
  @TempDir Path directory;

  @Test
  void givenCompleteExpandedSourceAndSuppliedConstructionFailure_whenDeriving_thenSourceSurvivesAndActualCauseIsRetained()
      throws IOException {
    // Arrange
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), "class Stored { int value() { return 1; } }"))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var cause = new IllegalStateException("Supplied graph construction failure");
    var request = new CfgRequest(ast.identity(), List.of(key(ast, "value")), CfgProfile.JAVA_BOUNDED);
    var deriver = new JavaCfgDeriver((source, plan) -> {
      throw new CfgConstructionException(CfgConstructionException.Mode.RECOVERABLE, cause);
    });
    var aggregate = ast.aggregate();
    var diagnostics = ast.diagnostics();

    // Act
    var result = deriver.derive(ast, request);

    // Assert
    assertEquals(CfgState.PARTIAL, result.state());
    assertEquals(MethodCfgState.FAILED, result.methods().getFirst().state());
    assertSame(cause, result.methods().getFirst().failure().orElseThrow());
    assertTrue(result.methods().getFirst().graph().isEmpty());
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertSame(aggregate, ast.aggregate());
    assertEquals(diagnostics, ast.diagnostics());
    assertEquals(ast.identity(), result.request().astIdentity());
    assertEquals(List.of(requestDiagnostic(request, "control-flow.construction-failed", key(ast, "value"),
        "Admitted control-flow construction failed")), result.diagnostics());
    assertEquals(result.diagnostics(), result.methods().getFirst().diagnostics());
  }

  @Test
  void givenAvailableAndBodylessDeclarations_whenDeriving_thenOrderedResultsRetainTheExactRequestAndSource() throws IOException {
    // Arrange
    var ast = produce("abstract class Stored { abstract int absent(); int value(int x) { return x + 1; } }");
    var keys = List.of(key(ast, "absent"), key(ast, "value"));
    var request = new CfgRequest(ast.identity(), keys, CfgProfile.JAVA_BOUNDED);
    var aggregate = ast.aggregate();

    // Act
    var result = CfgDerivers.standard().derive(ast, request);

    // Assert
    assertSame(request, result.request());
    assertEquals(CfgState.COMPLETE, result.state());
    assertEquals(keys, result.methods().stream().map(value -> value.methodKey()).toList());
    assertEquals(List.of(MethodCfgState.NOT_APPLICABLE, MethodCfgState.AVAILABLE),
        result.methods().stream().map(value -> value.state()).toList());
    assertTrue(result.methods().getFirst().body().isEmpty());
    assertEquals(ast.bodyRef(keys.getLast()), result.methods().getLast().body());
    assertFalse(result.methods().getLast().graph().orElseThrow().operationNodes().isEmpty());
    assertSame(aggregate, ast.aggregate());
  }

  @Test
  void givenEmptySelection_whenDeriving_thenTheExactEmptyRequestIsCompleteWithoutConstructingGraphs() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var request = new CfgRequest(ast.identity(), List.of(), CfgProfile.JAVA_BOUNDED);
    var deriver = new JavaCfgDeriver((source, plan) -> {
      throw new AssertionError("Empty selection must not construct a graph");
    });

    // Act
    var result = deriver.derive(ast, request);

    // Assert
    assertSame(request, result.request());
    assertEquals(CfgState.COMPLETE, result.state());
    assertTrue(result.methods().isEmpty());
    assertTrue(result.diagnostics().isEmpty());
  }

  @Test
  void givenDuplicateBlankAndUnknownSelections_whenDeriving_thenRejectionRetainsEveryInvalidPosition() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var method = key(ast, "value");
    var request = new CfgRequest(ast.identity(), List.of(method, method, "", "unknown"), CfgProfile.JAVA_BOUNDED);

    // Act
    var result = CfgDerivers.standard().derive(ast, request);

    // Assert
    assertSame(request, result.request());
    assertEquals(CfgState.REJECTED, result.state());
    assertTrue(result.methods().isEmpty());
    assertEquals(List.of("control-flow.duplicate-selection", "control-flow.unknown-method", "control-flow.unknown-method"),
        result.diagnostics().stream().map(value -> value.code()).toList());
    assertEquals(4, result.request().methodKeys().size());
    assertEquals(List.of(
        requestDiagnostic(request, "control-flow.duplicate-selection", "selection/1",
            "The requested selection repeats a canonical method key"),
        requestDiagnostic(request, "control-flow.unknown-method", "selection/2",
            "The requested selection contains an unknown canonical method key"),
        requestDiagnostic(request, "control-flow.unknown-method", "selection/3",
            "The requested selection contains an unknown canonical method key")), result.diagnostics());
  }

  @Test
  void givenForeignIdentityAndUnsupportedGraphVersion_whenDeriving_thenBothRequestCausesRemainVisible() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var identity = ast.identity();
    var foreign = AstIdentity.fromContent(identity.softwareSignature(), identity.sourceModelVersion(),
        identity.astProfileVersion(), identity.semanticProductionRevision(), "different-content");
    var request = new CfgRequest(foreign, List.of(key(ast, "value")), new CfgProfile("unknown-profile", "unknown-format"));

    // Act
    var result = CfgDerivers.standard().derive(ast, request);

    // Assert
    assertSame(request, result.request());
    assertEquals(CfgState.REJECTED, result.state());
    assertEquals(List.of("control-flow.foreign-ast", "control-flow.incompatible-profile"),
        result.diagnostics().stream().map(value -> value.code()).toList());
    assertTrue(result.methods().isEmpty());
    assertEquals(List.of(
        requestDiagnostic(request, "control-flow.foreign-ast", "identity",
            "Requested source identity differs from the supplied AST result"),
        requestDiagnostic(request, "control-flow.incompatible-profile", "profile",
            "Requested source and graph interpretation versions are incompatible")), result.diagnostics());
  }

  @Test
  void givenIncompleteAstAndUnaffectedSelectedBody_whenDeriving_thenSourceCompletionIsPreservedAndGraphIsAvailable() throws IOException {
    // Arrange
    var ast = produce("class Stored { int field = 3; int value() { return 1; } }");
    var request = new CfgRequest(ast.identity(), List.of(key(ast, "value")), CfgProfile.JAVA_BOUNDED);

    // Act
    var result = CfgDerivers.standard().derive(ast, request);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertEquals(CfgState.COMPLETE, result.state());
    assertEquals(MethodCfgState.AVAILABLE, result.methods().getFirst().state());
    assertFalse(ast.diagnostics().isEmpty());
  }

  @Test
  void givenLaterRecoverableConstructionFailure_whenDeriving_thenPriorAndSubsequentGraphsAndEffectiveCauseAreRetained() throws IOException {
    // Arrange
    var ast = produce("class Stored { int first() { return 1; } int second() { return 2; } int third() { return 3; } }");
    var keys = List.of(key(ast, "first"), key(ast, "second"), key(ast, "third"));
    var request = new CfgRequest(ast.identity(), keys, CfgProfile.JAVA_BOUNDED);
    var cause = new IllegalStateException("Construction observation");
    var builder = new ControlFlowGraphBuilder();
    var deriver = new JavaCfgDeriver((source, plan) -> {
      if (plan.body().methodKey().equals(keys.get(1))) {
        throw new CfgConstructionException(CfgConstructionException.Mode.RECOVERABLE, cause);
      }
      return builder.build(source, plan);
    });

    // Act
    var result = deriver.derive(ast, request);

    // Assert
    assertEquals(CfgState.PARTIAL, result.state());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.FAILED, MethodCfgState.AVAILABLE),
        result.methods().stream().map(value -> value.state()).toList());
    assertSame(cause, result.methods().get(1).failure().orElseThrow());
    assertTrue(result.methods().get(1).graph().isEmpty());
    assertEquals(ast.bodyRef(keys.get(1)), result.methods().get(1).body());
    assertEquals(1, result.diagnostics().size());
  }

  @Test
  void givenLaterDeclaredAbruptFailure_whenDeriving_thenExceptionRetainsExactRequestPriorGraphAndEffectiveCause() throws IOException {
    // Arrange
    var ast = produce("class Stored { int first() { return 1; } int second() { return 2; } }");
    var keys = List.of(key(ast, "first"), key(ast, "second"));
    var request = new CfgRequest(ast.identity(), keys, CfgProfile.JAVA_BOUNDED);
    var cause = new IllegalStateException("Construction observation");
    var builder = new ControlFlowGraphBuilder();
    var deriver = new JavaCfgDeriver((source, plan) -> {
      if (plan.body().methodKey().equals(keys.getLast())) {
        throw new CfgConstructionException(CfgConstructionException.Mode.ABRUPT, cause);
      }
      return builder.build(source, plan);
    });

    // Act
    var failure = assertThrows(CfgDerivationException.class, () -> deriver.derive(ast, request));

    // Assert
    assertSame(request, failure.request());
    assertSame(cause, failure.getCause());
    assertEquals(List.of(keys.getFirst()), failure.completedMethods().stream().map(value -> value.methodKey()).toList());
    assertEquals(MethodCfgState.AVAILABLE, failure.completedMethods().getFirst().state());
    assertEquals(AstCompletion.COMPLETE, ast.completion());
  }

  @Test
  void givenUndeclaredRuntimeOrFatalFailure_whenDeriving_thenTheOriginalFailureFamilyPropagates() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var request = new CfgRequest(ast.identity(), List.of(key(ast, "value")), CfgProfile.JAVA_BOUNDED);
    var runtime = new IllegalStateException("Unexpected construction failure");
    var fatal = new AssertionError("Fatal construction failure");
    var runtimeDeriver = new JavaCfgDeriver((source, plan) -> {
      throw runtime;
    });
    var fatalDeriver = new JavaCfgDeriver((source, plan) -> {
      throw fatal;
    });

    // Act
    var observedRuntime = assertThrows(IllegalStateException.class, () -> runtimeDeriver.derive(ast, request));
    var observedFatal = assertThrows(AssertionError.class, () -> fatalDeriver.derive(ast, request));

    // Assert
    assertSame(runtime, observedRuntime);
    assertSame(fatal, observedFatal);
  }

  @Test
  void givenEqualMethodKeysWithDifferentSourceContent_whenDeriving_thenTheEntireGraphOwnershipIsQualifiedByExactContent() throws IOException {
    // Arrange
    var first = produce("class Stored { int value() { return 1; } }");
    var second = produce("class Stored { int value() { return 2; } }");
    var firstRequest = new CfgRequest(first.identity(), List.of(key(first, "value")), CfgProfile.JAVA_BOUNDED);
    var secondRequest = new CfgRequest(second.identity(), List.of(key(second, "value")), CfgProfile.JAVA_BOUNDED);
    var deriver = CfgDerivers.standard();

    // Act
    var graph = deriver.derive(first, firstRequest).methods().getFirst().graph().orElseThrow();
    var repeated = deriver.derive(first, firstRequest).methods().getFirst().graph().orElseThrow();
    var changed = deriver.derive(second, secondRequest).methods().getFirst().graph().orElseThrow();

    // Assert
    assertEquals(firstRequest.methodKeys(), secondRequest.methodKeys());
    assertEquals(graph, repeated);
    assertNotEquals(graph.identity(), changed.identity());
    assertNotEquals(graph.graphId(), changed.graphId());
    var identity = graph.identity();
    assertEquals(identity.derivationKey(), graph.graphId());
    assertEquals(first.identity(), identity.astIdentity());
    assertEquals(first.bodyRef(key(first, "value")).orElseThrow(), graph.body());
    assertTrue(graph.nodes().stream().allMatch(node -> node.graphId().equals(graph.graphId())));
    assertTrue(graph.values().stream().allMatch(value -> value.graphId().equals(graph.graphId())));
    assertTrue(graph.edges().stream().allMatch(edge -> edge.graphId().equals(graph.graphId())));
    assertTrue(graph.operationNodes().stream().allMatch(node ->
        node.sourceRef().body().equals(graph.body())));
    assertTrue(graph.operationNodes().stream().allMatch(node ->
        node.sourceRef().occurrence().isPresent()));
  }

  @Test
  void givenQualifiedGraphQueries_whenResolving_thenOwnedUnknownAndForeignKeysHaveDistinctResults() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var request = new CfgRequest(ast.identity(), List.of(key(ast, "value")), CfgProfile.JAVA_BOUNDED);
    var graph = CfgDerivers.standard().derive(ast, request).methods().getFirst().graph().orElseThrow();
    var identity = graph.identity();
    var foreign = GraphIdentity.fromContent(identity.astIdentity(), identity.cfgProfileVersion(),
        identity.graphModelVersion(), identity.methodKey(), "different-graph-content");

    // Act
    var node = graph.node(new GraphNodeKey(identity, graph.entry().nodeId()));
    var value = graph.value(new GraphValueKey(identity, graph.values().getFirst().valueId()));
    var edge = graph.edge(new GraphEdgeKey(identity, graph.edges().getFirst().edgeId()));
    var foreignNode = assertThrows(IllegalArgumentException.class,
        () -> graph.node(new GraphNodeKey(foreign, graph.entry().nodeId())));
    var foreignValue = assertThrows(IllegalArgumentException.class,
        () -> graph.value(new GraphValueKey(foreign, graph.values().getFirst().valueId())));
    var foreignEdge = assertThrows(IllegalArgumentException.class,
        () -> graph.edge(new GraphEdgeKey(foreign, graph.edges().getFirst().edgeId())));

    // Assert
    assertSame(graph.entry(), node.orElseThrow());
    assertSame(graph.values().getFirst(), value.orElseThrow());
    assertSame(graph.edges().getFirst(), edge.orElseThrow());
    assertTrue(graph.node(new GraphNodeKey(identity, "unknown")).isEmpty());
    assertTrue(graph.value(new GraphValueKey(identity, "unknown")).isEmpty());
    assertTrue(graph.edge(new GraphEdgeKey(identity, "unknown")).isEmpty());
    assertTrue(foreignNode.getMessage().contains("identity"));
    assertTrue(foreignValue.getMessage().contains("identity"));
    assertTrue(foreignEdge.getMessage().contains("identity"));
  }

  @Test
  void givenCompleteSourceAndCompatibleRestrictiveCfgProfile_whenDeriving_thenSourceRemainsCompleteAndOtherGraphsSurvive() throws IOException {
    // Arrange
    var ast = produce("""
        class Stored {
          int choose(int x) { return switch (x) { case 1 -> 2; default -> 3; }; }
          int value() { return 1; }
        }
        """);
    var selected = List.of(key(ast, "choose"), key(ast, "value"));
    var request = new CfgRequest(ast.identity(), selected, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);

    // Act
    var outcome = CfgDerivers.standard().derive(ast, request);

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertSame(request, outcome.request());
    assertEquals(CfgState.PARTIAL, outcome.state());
    assertEquals(List.of(MethodCfgState.UNSUPPORTED, MethodCfgState.AVAILABLE), outcome.methods().stream().map(MethodCfgOutcome::state).toList());
    var refusal = outcome.methods().getFirst();
    assertTrue(refusal.graph().isEmpty());
    assertTrue(refusal.blockers().stream().anyMatch(blocker -> blocker.occurrence().isPresent()
        && ast.node(blocker.occurrence().orElseThrow()).orElseThrow().syntaxKind() == SyntaxKind.SWITCH_EXPRESSION));
    var graph = outcome.methods().getLast().graph().orElseThrow();
    assertEquals(request.profile().version(), graph.identity().cfgProfileVersion());
    assertEquals(ast.identity(), graph.identity().astIdentity());
  }

  @Test
  void givenSameSourceAndMethodWithDifferentCompatibleCfgProfiles_whenQualifying_thenGraphsCannotBeRelabeledUnderAnotherRequest() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var selection = List.of(key(ast, "value"));
    var standard = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), selection, CfgProfile.JAVA_BOUNDED));
    var restrictiveRequest = new CfgRequest(ast.identity(), selection, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);

    // Act
    var restrictive = CfgDerivers.standard().derive(ast, restrictiveRequest);

    // Assert
    assertEquals(CfgState.COMPLETE, restrictive.state());
    assertNotEquals(standard.methods().getFirst().graph().orElseThrow().identity(), restrictive.methods().getFirst().graph().orElseThrow().identity());
    assertThrows(IllegalArgumentException.class,
        () -> new CfgOutcome(restrictiveRequest, CfgState.COMPLETE, standard.methods(), standard.diagnostics()));
  }

  private static AnalysisDiagnostic requestDiagnostic(CfgRequest request, String code, String slot, String message) {
    return new AnalysisDiagnostic(StableId.sha256("cfg-request-diagnostic", request.astIdentity().astResultKey(),
        request.profile().version(), request.profile().graphModelVersion(), code, slot), code, DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, message, List.of(), AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
  }

  private static String key(AstResult ast, String name) {
    return ast.program().types().stream().flatMap(type -> type.methods().stream()).filter(method -> method.name().equals(name))
        .map(method -> method.methodKey()).findFirst().orElseThrow();
  }
}
