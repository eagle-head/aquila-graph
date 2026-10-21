package org.aquilagraph.cfg.api;

import org.aquilagraph.cfg.internal.ControlFlowGraphBuilder;
import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.GraphEdgeKey;
import org.aquilagraph.identity.api.GraphNodeKey;
import org.aquilagraph.identity.api.GraphValueKey;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphValueAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenTypedAndUntypedReferenceValues_whenSupportedGraphsAreAdmitted_thenTheirCompletePublicContentQualifiesIdentity() throws IOException {
    // Arrange
    var fixture = fixture("class Stored { int value() { return 1; } }", false, AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var identity = new JavaTypeDeclarationIdentity(Optional.empty(), "Stored");
    var plain = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of(identity.declarationKey()), Optional.of("Stored"), List.of("Stored"), List.of("Stored"));
    var typed = new SemanticReference(plain.referenceKind(), plain.status(), plain.symbolKey(), plain.typeName(), plain.typeAlternativeNames(), plain.assignableToTypeNames(), Optional.of(identity));
    // Act
    var first = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), withReferences(fixture.draft(), List.of(plain)), fixture.creating(), Map.of());
    var second = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), withReferences(fixture.draft(), List.of(typed)), fixture.creating(), Map.of());
    var repeated = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), withReferences(fixture.draft(), List.of(typed)), fixture.creating(), Map.of());
    var ordered = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), withReferences(fixture.draft(), List.of(plain, typed)), fixture.creating(), Map.of());
    var reversed = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), withReferences(fixture.draft(), List.of(typed, plain)), fixture.creating(), Map.of());
    // Assert
    assertEquals(List.of(plain), first.operationNodes().getFirst().semanticReferences());
    assertEquals(List.of(typed), second.operationNodes().getFirst().semanticReferences());
    assertNotEquals(first.identity().orElseThrow().canonicalContentDigest(), second.identity().orElseThrow().canonicalContentDigest());
    assertNotEquals(first.identity(), second.identity());
    assertEquals(second.identity(), repeated.identity());
    assertEquals(List.of(plain, typed), ordered.operationNodes().getFirst().semanticReferences());
    assertEquals(List.of(typed, plain), reversed.operationNodes().getFirst().semanticReferences());
    assertNotEquals(ordered.identity(), reversed.identity());
    assertThrows(IllegalArgumentException.class, () -> first.node(new GraphNodeKey(second.identity().orElseThrow(), first.operationNodes().getFirst().nodeId())));
  }

  @Test
  void givenHistoricalReferenceShapes_whenCurrentGraphsAreAdmitted_thenOriginalContentAndDerivationIdentitiesRemainExact() throws IOException {
    // Arrange
    var fixture = fixture("class Stored { int value() { return 1; } }", false, AstProfile.JAVA_SOURCE_BOUNDED, true);
    var target = new JavaTypeDeclarationIdentity(Optional.empty(), "Stored");
    var reference = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of(target.declarationKey()), Optional.of("Stored"), List.of("Stored"), List.of("Stored"));
    // Act
    var supplied = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), withReferences(fixture.draft(), List.of(reference)), fixture.creating(), Map.of());
    // Assert
    assertEquals("ast-result:278fc3c35fbe7636b8314238585763f7bb89bc3053eb40bf4d1862f7279d5c06", fixture.ast().identity().astResultKey());
    assertEquals("cfg-content:97a2aea3f6915e7e7c123fda6e0c2e8d91628394c1fc648a5b7a8b7850b52756", fixture.graph().identity().orElseThrow().canonicalContentDigest());
    assertEquals("cfg-derivation:e7726cce96195fd339a4b580435e96e321ca34b45be0fe77fba9d12db05e0848", fixture.graph().identity().orElseThrow().derivationKey());
    assertEquals("cfg-content:3365797f513ac3467dd43f130eb0cfbad6a1f08ff5752ecbabdfa63053c812e5", supplied.identity().orElseThrow().canonicalContentDigest());
    assertEquals("cfg-derivation:be9cc6f4208b9e25908316fbc4331e3a24d4ca71122c3983627227658920fe12", supplied.identity().orElseThrow().derivationKey());
    assertTrue(supplied.operationNodes().getFirst().semanticReferences().getFirst().declarationIdentity().isEmpty());
  }

  private static ControlFlowGraph withReferences(ControlFlowGraph draft, List<SemanticReference> references) {
    var first = draft.operationNodes().getFirst();
    var changed = new OperationNode(first.nodeId(), first.graphId(), first.kind(), first.detail(), first.operands(), first.definedValue(), first.invocationSemantics(), references, first.possibleExceptions(), first.origins());
    return new ControlFlowGraph(draft.graphId(), draft.methodKey(), draft.entry(), draft.operationNodes().stream().map(node -> node.equals(first) ? changed : node).toList(), draft.values(), draft.exit(), draft.edges());
  }

  @Test
  void givenBlankEntryIdentity_whenGraphIsQualified_thenTheUnqueryableMemberIsRejected() throws IOException {
    // Arrange
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var fixture = fixture("class Stored { int value() { return 1; } }", false, profile);
      for (var blank : List.of("", " \t\n")) {
        var draft = withNodeIdentity(fixture.draft(), fixture.draft().entry().nodeId(), blank);

        // Act and Assert
        assertThrows(IllegalArgumentException.class,
            () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), draft, fixture.creating(), Map.of()));
      }
    }
  }

  @Test
  void givenBlankExitIdentity_whenGraphIsQualified_thenTheUnqueryableMemberIsRejected() throws IOException {
    // Arrange
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var fixture = fixture("class Stored { int value() { return 1; } }", false, profile);
      for (var blank : List.of("", " \t\n")) {
        var draft = withNodeIdentity(fixture.draft(), fixture.draft().exit().nodeId(), blank);

        // Act and Assert
        assertThrows(IllegalArgumentException.class,
            () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), draft, fixture.creating(), Map.of()));
      }
    }
  }

  @Test
  void givenBlankOperationIdentityWithItsExactSource_whenGraphIsQualified_thenTheUnqueryableMemberIsRejected() throws IOException {
    // Arrange
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var fixture = fixture("class Stored { int value() { return 1; } }", false, profile);
      var original = fixture.draft().operationNodes().getFirst().nodeId();
      for (var blank : List.of("", " \t\n")) {
        var draft = withNodeIdentity(fixture.draft(), original, blank);
        var creating = new HashMap<>(fixture.creating());
        creating.put(blank, creating.remove(original));

        // Act and Assert
        assertThrows(IllegalArgumentException.class,
            () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), draft, creating, Map.of()));
      }
    }
  }

  @Test
  void givenBlankValueIdentityWithConsistentOperands_whenGraphIsQualified_thenTheUnqueryableMemberIsRejected() throws IOException {
    // Arrange
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var fixture = fixture("class Stored { int value() { return 1; } }", false, profile);
      for (var blank : List.of("", " \t\n")) {
        var draft = withValueIdentity(fixture.draft(), fixture.draft().values().getFirst().valueId(), blank);

        // Act and Assert
        assertThrows(IllegalArgumentException.class,
            () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), draft, fixture.creating(), Map.of()));
      }
    }
  }

  @Test
  void givenBlankTransferIdentityWithOwnedEndpoints_whenGraphIsQualified_thenTheUnqueryableMemberIsRejected() throws IOException {
    // Arrange
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var fixture = fixture("class Stored { int value() { return 1; } }", false, profile);
      var original = fixture.draft().edges().getFirst();
      for (var blank : List.of("", " \t\n")) {
        var edges = fixture.draft().edges().stream().map(edge -> edge.equals(original)
            ? new CfgEdge(blank, edge.graphId(), edge.sourceNodeId(), edge.targetNodeId(), edge.transferKind(), edge.discriminator())
            : edge).toList();
        var draft = new ControlFlowGraph(fixture.draft().graphId(), fixture.draft().methodKey(), fixture.draft().entry(),
            fixture.draft().operationNodes(), fixture.draft().values(), fixture.draft().exit(), edges);

        // Act and Assert
        assertThrows(IllegalArgumentException.class,
            () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), draft, fixture.creating(), Map.of()));
      }
    }
  }

  @Test
  void givenValidGraphMemberIdentities_whenGraphIsQualified_thenEveryTypedQueryReturnsItsCanonicalMember() throws IOException {
    // Arrange
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var fixture = fixture("class Stored { int value() { return 1; } }", false, profile);

      // Act
      var graph = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of());
      var identity = graph.identity().orElseThrow();

      // Assert
      assertEquals(fixture.graph(), graph);
      graph.nodes().forEach(node -> assertSame(node, graph.node(new GraphNodeKey(identity, node.nodeId())).orElseThrow()));
      graph.values().forEach(value -> assertSame(value, graph.value(new GraphValueKey(identity, value.valueId())).orElseThrow()));
      graph.edges().forEach(edge -> assertSame(edge, graph.edge(new GraphEdgeKey(identity, edge.edgeId())).orElseThrow()));
    }
  }

  private static ControlFlowGraph withNodeIdentity(ControlFlowGraph graph, String original, String replacement) {
    var operations = graph.operationNodes().stream().map(node -> node.nodeId().equals(original)
        ? new OperationNode(replacement, node.graphId(), node.kind(), node.detail(), node.operands(), node.definedValue(),
            node.invocationSemantics(), node.semanticReferences(), node.possibleExceptions(), node.origins())
        : node).toList();
    var edges = graph.edges().stream().map(edge -> new CfgEdge(edge.edgeId(), edge.graphId(),
        edge.sourceNodeId().equals(original) ? replacement : edge.sourceNodeId(),
        edge.targetNodeId().equals(original) ? replacement : edge.targetNodeId(), edge.transferKind(), edge.discriminator())).toList();
    return new ControlFlowGraph(graph.graphId(), graph.methodKey(),
        new EntryNode(graph.entry().nodeId().equals(original) ? replacement : graph.entry().nodeId(), graph.graphId()), operations,
        graph.values(), new ExitNode(graph.exit().nodeId().equals(original) ? replacement : graph.exit().nodeId(), graph.graphId()), edges);
  }

  private static ControlFlowGraph withValueIdentity(ControlFlowGraph graph, String original, String replacement) {
    var values = new HashMap<String, FlowValue>();
    graph.values().forEach(value -> values.put(value.valueId(), value.valueId().equals(original)
        ? new FlowValue(replacement, value.graphId(), value.kind(), value.displayName(), value.literal(), value.staticType()) : value));
    var operations = graph.operationNodes().stream()
        .map(node -> GraphOperationProjection.remap(node, graph.graphId(), node.nodeId(), values, Optional.empty())).toList();
    return new ControlFlowGraph(graph.graphId(), graph.methodKey(), graph.entry(), operations,
        graph.values().stream().map(value -> values.get(value.valueId())).toList(), graph.exit(), graph.edges());
  }

  @Test
  void givenRealSourceProfiles_whenStandardDeriverQualifiesGraphs_thenLocalRoleAlgorithmsAndExactQueriesRemainIntact()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), "class Stored { int value(int input) { return input + 7; } }")));
    var graphs = new ArrayList<ControlFlowGraph>();

    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var produced = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
          profile).result().orElseThrow();
      var ast = AstCodec.decode(AstCodec.encode(produced), produced.identity());
      var method = ast.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
      var nodes = ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
      var returned = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.RETURN).findFirst().orElseThrow();
      var literal = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).findFirst().orElseThrow();
      var binary = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.BINARY).findFirst().orElseThrow();
      var localGraph = StableId.sha256("cfg", method.methodKey());
      var returnKey = StableId.sha256("cfg-node", localGraph, "operation",
          StableId.sha256("operation", method.methodKey(), returned.nodeId(), "return"));
      var constantKey = StableId.sha256("cfg-node", localGraph, "operation",
          StableId.sha256("operation", method.methodKey(), literal.nodeId(), "constant"));
      var binaryKey = StableId.sha256("cfg-node", localGraph, "operation",
          StableId.sha256("operation", method.methodKey(), binary.nodeId(), "binary"));
      var constantValue = StableId.sha256("value", constantKey);
      var parameterValue = StableId.sha256("value", method.parameters().getFirst().parameterKey());
      var transferKey = StableId.sha256("cfg-edge", localGraph,
          StableId.sha256("transfer", method.methodKey(), "OPERATION:" + returnKey, "EXIT", "RETURN", ""));

      // Act
      var graph = CfgDerivers.standard().derive(ast,
          new CfgRequest(ast.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst().graph().orElseThrow();
      var identity = graph.identity().orElseThrow();
      graphs.add(graph);

      // Assert
      assertEquals(StableId.sha256("cfg-node", localGraph, "entry"), graph.entry().nodeId());
      assertEquals(StableId.sha256("cfg-node", localGraph, "exit"), graph.exit().nodeId());
      assertEquals(OperationKind.RETURN,
          assertInstanceOf(OperationNode.class, graph.node(new GraphNodeKey(identity, returnKey)).orElseThrow()).kind());
      assertEquals(OperationKind.BINARY,
          assertInstanceOf(OperationNode.class, graph.node(new GraphNodeKey(identity, binaryKey)).orElseThrow()).kind());
      assertEquals(OperationKind.CONSTANT,
          assertInstanceOf(OperationNode.class, graph.node(new GraphNodeKey(identity, constantKey)).orElseThrow()).kind());
      assertEquals(ValueKind.CONSTANT, graph.value(new GraphValueKey(identity, constantValue)).orElseThrow().kind());
      assertEquals(ValueKind.PARAMETER, graph.value(new GraphValueKey(identity, parameterValue)).orElseThrow().kind());
      var transfer = graph.edge(new GraphEdgeKey(identity, transferKey)).orElseThrow();
      assertEquals(returnKey, transfer.sourceNodeId());
      assertEquals(graph.exit().nodeId(), transfer.targetNodeId());
      assertEquals(TransferKind.RETURN, transfer.transferKind());
      assertTrue(graph.nodes().stream().allMatch(node -> node.graphId().equals(graph.graphId())));
      assertTrue(graph.values().stream().allMatch(value -> value.graphId().equals(graph.graphId())));
      assertTrue(graph.edges().stream().allMatch(edge -> edge.graphId().equals(graph.graphId())));
    }
    var bounded = graphs.getFirst();
    var expanded = graphs.getLast();
    assertNotEquals(bounded.identity(), expanded.identity());
    assertEquals(bounded.operationNodes().stream().map(OperationNode::nodeId).toList(),
        expanded.operationNodes().stream().map(OperationNode::nodeId).toList());
    assertThrows(IllegalArgumentException.class,
        () -> bounded.node(new GraphNodeKey(expanded.identity().orElseThrow(), bounded.operationNodes().getFirst().nodeId())));
    assertThrows(IllegalArgumentException.class,
        () -> bounded.value(new GraphValueKey(expanded.identity().orElseThrow(), bounded.values().getFirst().valueId())));
    assertThrows(IllegalArgumentException.class,
        () -> bounded.edge(new GraphEdgeKey(expanded.identity().orElseThrow(), bounded.edges().getFirst().edgeId())));
  }

  @Test
  void givenChangedSuppliedTransferRoleKey_whenGraphIsAdmitted_thenRetainedLocalIdentityParticipatesInContentQualification()
      throws IOException {
    // Arrange
    var fixture = fixture();
    var first = fixture.draft().edges().getFirst();
    var changed = new CfgEdge("alternate-transfer-role", first.graphId(), first.sourceNodeId(), first.targetNodeId(),
        first.transferKind(), first.discriminator());
    var edges = fixture.draft().edges().stream().map(edge -> edge.equals(first) ? changed : edge).toList();
    var draft = new ControlFlowGraph(fixture.draft().graphId(), fixture.draft().methodKey(), fixture.draft().entry(),
        fixture.draft().operationNodes(), fixture.draft().values(), fixture.draft().exit(), edges);

    // Act
    var graph = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), draft, fixture.creating(), Map.of());

    // Assert
    assertNotEquals(fixture.graph().identity(), graph.identity());
    assertEquals("alternate-transfer-role", graph.edges().getFirst().edgeId());
  }

  @Test
  void givenPublicGraphConstructors_whenInspected_thenOnlyUnqualifiedLegacyConstructionIsPublic() {
    // Arrange
    var graphType = ControlFlowGraph.class;

    // Act
    var constructors = graphType.getConstructors();

    // Assert
    assertTrue(Arrays.stream(constructors).allMatch(constructor -> constructor.getParameterCount() == 7));
  }

  @Test
  void givenOrderedGraphDiagnostics_whenContentIsAdmitted_thenEveryFieldAndRepeatedOccurrenceQualifiesIdentity() throws IOException {
    // Arrange
    var fixture = fixture();
    var origin = fixture.ast().body(fixture.plan().body().methodKey()).orElseThrow().root().origin().orElseThrow();
    var first = new AnalysisDiagnostic("diagnostic-one", "control-flow.observation", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.WARNING, "First observation", List.of(origin), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var variants = List.of(
        new AnalysisDiagnostic("diagnostic-two", first.code(), first.stage(), first.severity(), first.message(), first.origins(), first.outcomeKind()),
        new AnalysisDiagnostic(first.diagnosticKey(), "control-flow.other", first.stage(), first.severity(), first.message(), first.origins(), first.outcomeKind()),
        new AnalysisDiagnostic(first.diagnosticKey(), first.code(), first.stage(), DiagnosticSeverity.INFO, first.message(), first.origins(), first.outcomeKind()),
        new AnalysisDiagnostic(first.diagnosticKey(), first.code(), first.stage(), first.severity(), "Changed observation", first.origins(), first.outcomeKind()),
        new AnalysisDiagnostic(first.diagnosticKey(), first.code(), first.stage(), first.severity(), first.message(), List.of(), first.outcomeKind()),
        new AnalysisDiagnostic(first.diagnosticKey(), first.code(), first.stage(), first.severity(), first.message(), first.origins(), AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
    // Act
    var original = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of(), List.of(first));
    var changedGraphs = variants.stream().map(variant ->
        GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of(), List.of(variant))).toList();
    var repeated = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of(), List.of(first, first));
    var ordered = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of(), List.of(first, variants.getFirst()));
    var reversed = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of(), List.of(variants.getFirst(), first));
    var error = new AnalysisDiagnostic(first.diagnosticKey(), first.code(), first.stage(), DiagnosticSeverity.ERROR, first.message(),
        first.origins(), first.outcomeKind());
    var graphWithError = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of(), List.of(error));
    var request = new CfgRequest(fixture.ast().identity(), List.of(fixture.plan().body().methodKey()), fixture.plan().profile());
    var available = new MethodCfgOutcome(fixture.ast().identity(), fixture.plan().body().methodKey(), MethodCfgState.AVAILABLE,
        Optional.of(fixture.plan().body()), Optional.of(original), List.of(), Optional.empty(), original.diagnostics());
    var methodWithError = new MethodCfgOutcome(fixture.ast().identity(), fixture.plan().body().methodKey(), MethodCfgState.AVAILABLE,
        Optional.of(fixture.plan().body()), Optional.of(graphWithError), List.of(), Optional.empty(), List.of(error));

    // Assert
    assertEquals(List.of(first), original.diagnostics());
    assertThrows(UnsupportedOperationException.class, () -> original.diagnostics().clear());
    for (var changed : changedGraphs) {
      assertNotEquals(original.identity(), changed.identity());
    }
    assertEquals(List.of(first, first), repeated.diagnostics());
    assertNotEquals(original.identity(), repeated.identity());
    assertNotEquals(ordered.identity(), reversed.identity());
    assertThrows(IllegalArgumentException.class, () -> new MethodCfgOutcome(fixture.ast().identity(), fixture.plan().body().methodKey(),
        MethodCfgState.AVAILABLE, Optional.of(fixture.plan().body()), Optional.of(original), List.of(), Optional.empty(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new CfgOutcome(request, CfgState.COMPLETE, List.of(available), List.of()));
    assertNotEquals(original.identity(), graphWithError.identity());
    assertThrows(IllegalArgumentException.class, () -> new CfgOutcome(request, CfgState.COMPLETE, List.of(methodWithError), List.of(error)));
    assertEquals(CfgState.PARTIAL, new CfgOutcome(request, CfgState.PARTIAL, List.of(methodWithError), List.of(error)).state());
    var foreignStage = new AnalysisDiagnostic(first.diagnosticKey(), first.code(), DiagnosticStage.FRONTEND, first.severity(), first.message(), first.origins(), first.outcomeKind());
    assertThrows(IllegalArgumentException.class, () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of(), List.of(foreignStage)));
  }

  @Test
  void givenActualRetainedSourceAndGraph_whenCompleteValuesAreReadmitted_thenIdentityComesFromAllContent() throws IOException {
    // Arrange
    var fixture = fixture();
    var first = fixture.draft().operationNodes().getFirst();
    var changed = new OperationNode(first.nodeId(), first.graphId(), first.kind(), "changed detail", first.operands(), first.definedValue(),
        first.invocationSemantics(), first.semanticReferences(), first.possibleExceptions(), first.origins());
    var operations = fixture.draft().operationNodes().stream().map(operation -> operation.equals(first) ? changed : operation).toList();
    var draft = new ControlFlowGraph(fixture.draft().graphId(), fixture.draft().methodKey(), fixture.draft().entry(), operations,
        fixture.draft().values(), fixture.draft().exit(), fixture.draft().edges());
    // Act
    var admitted = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of());
    var different = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), draft, fixture.creating(), Map.of());

    // Assert
    assertEquals(fixture.graph(), admitted);
    assertNotEquals(admitted.identity(), different.identity());
  }

  @Test
  void givenForeignCreatingOccurrenceOrInventedGeneratedRole_whenAdmitted_thenEachSourceAssociationRejects() throws IOException {
    // Arrange
    var fixture = fixture();
    var operationKey = fixture.draft().operationNodes().getFirst().nodeId();
    var creating = new HashMap<>(fixture.creating());
    creating.put(operationKey, fixture.ast().aggregate().roots().getFirst());

    // Act
    var foreign = assertThrows(IllegalArgumentException.class,
        () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), creating, Map.of()));

    // Assert
    assertTrue(foreign.getMessage().contains("admitted method body"));
    assertThrows(IllegalArgumentException.class,
        () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), creating, Map.of()));
    assertThrows(IllegalArgumentException.class,
        () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), Map.of(operationKey, "invented")));
    creating.put("unknown-operation", fixture.ast().body(fixture.plan().body().methodKey()).orElseThrow().root());
    assertThrows(IllegalArgumentException.class,
        () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), creating, Map.of()));
  }

  private Fixture fixture() throws IOException {
    return fixture("class Stored { int value() { return 1; } }", false);
  }

  @Test
  void givenValueEqualCreatingNodeFromAnotherDecodedOwner_whenAdmittingGraph_thenUnownedInstanceIsRejected() throws IOException {
    // Arrange
    var fixture = fixture();
    var copiedAst = AstCodec.decode(AstCodec.encode(fixture.ast()), fixture.ast().identity());
    var operationKey = fixture.draft().operationNodes().getFirst().nodeId();
    var owned = fixture.creating().get(operationKey);
    var unowned = copiedAst.aggregate().node(owned.nodeId()).orElseThrow();
    var creating = new HashMap<>(fixture.creating());
    creating.put(operationKey, unowned);

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), creating, Map.of()));

    // Assert
    assertEquals(owned, unowned);
    assertNotSame(owned, unowned);
    assertEquals("Operation creating occurrence is outside the admitted method body", failure.getMessage());
  }

  @Test
  void givenForeignOrOutOfCaptureOperationOrigins_whenQualifyingGraph_thenEachInvalidOriginIsRejected() throws IOException {
    // Arrange
    var fixture = fixture();
    var source = fixture.ast().snapshot().sourceFiles().getFirst();
    var invalidOrigins = List.of(new SourceSpan("foreign-source", 0, 1),
        new SourceSpan(source.sourceFileKey(), 0, source.content().length() + 1L));

    // Act
    var failures = invalidOrigins.stream().map(origin -> assertThrows(IllegalArgumentException.class,
        () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), withOrigins(fixture.draft(), List.of(origin)),
            fixture.creating(), Map.of()))).toList();

    // Assert
    assertEquals(2, failures.size());
  }

  @Test
  void givenOrderedCapturedContributingOrigins_whenQualifyingGraph_thenOrderIsRetainedAndQualifiesIdentity() throws IOException {
    // Arrange
    var fixture = fixture();
    var source = fixture.ast().snapshot().sourceFiles().getFirst();
    var origins = List.of(new SourceSpan(source.sourceFileKey(), 0, 1),
        new SourceSpan(source.sourceFileKey(), source.content().length() - 1L, source.content().length()));

    // Act
    var graph = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), withOrigins(fixture.draft(), origins), fixture.creating(), Map.of());
    var reversed = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), withOrigins(fixture.draft(), origins.reversed()),
        fixture.creating(), Map.of());

    // Assert
    assertEquals(origins, graph.operationNodes().getFirst().origins());
    assertEquals(origins, withOrigins(fixture.draft(), origins).operationNodes().getFirst().origins());
    assertNotEquals(graph.identity(), reversed.identity());
  }

  private static ControlFlowGraph withOrigins(ControlFlowGraph graph, List<SourceSpan> origins) {
    var first = graph.operationNodes().getFirst();
    var changed = new OperationNode(first.nodeId(), first.graphId(), first.kind(), first.detail(), first.operands(), first.definedValue(),
        first.invocationSemantics(), first.semanticReferences(), first.possibleExceptions(), origins);
    var operations = graph.operationNodes().stream().map(operation -> operation.equals(first) ? changed : operation).toList();
    return new ControlFlowGraph(graph.graphId(), graph.methodKey(), graph.entry(), operations, graph.values(), graph.exit(), graph.edges());
  }

  @Test
  void givenActualImplicitVarargsConstructor_whenGeneratedRolesAreAltered_thenAdmissionRejectsInventedOrContradictoryRoles()
      throws IOException {
    // Arrange
    var fixture = fixture("class Parent { Parent(int... values) {} } class Stored extends Parent { Stored() {} }", true);
    var roles = new HashMap<String, String>();
    for (var index = 0; index < fixture.graph().operationNodes().size(); index++) {
      var reference = fixture.graph().operationNodes().get(index).sourceRef().orElseThrow();
      if (reference.occurrence().isEmpty()) {
        roles.put(fixture.draft().operationNodes().get(index).nodeId(), reference.generatedRole());
      }
    }
    // Act
    var admitted = GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), roles);

    // Assert
    assertEquals(fixture.graph(), admitted);
    var allocation = fixture.draft().operationNodes().stream().filter(value -> value.kind() == OperationKind.VARARGS_ARRAY)
        .findFirst().orElseThrow().nodeId();
    roles.put(allocation, "invented");
    assertThrows(IllegalArgumentException.class,
        () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), roles));
    roles.put(allocation, "compiler-inserted-superclass/invoke");
    assertThrows(IllegalArgumentException.class,
        () -> GraphValueAdmission.admit(fixture.ast(), fixture.plan(), fixture.draft(), fixture.creating(), roles));
  }

  private Fixture fixture(String source, boolean constructor) throws IOException {
    return fixture(source, constructor, AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private Fixture fixture(String source, boolean constructor, AstProfile profile) throws IOException {
    return fixture(source, constructor, profile, false);
  }

  private Fixture fixture(String source, boolean constructor, AstProfile profile, boolean historical) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), source)));
    var produced = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        profile).result().orElseThrow();
    var selected = historical ? SourceAnalysisFixture.historicalResult(produced) : produced;
    var ast = AstCodec.decode(AstCodec.encode(selected), selected.identity());
    var method = ast.program().types().stream().filter(type -> type.qualifiedName().equals("Stored"))
        .flatMap(type -> type.methods().stream()).filter(value -> constructor ? value.constructor() : value.name().equals("value"))
        .findFirst().orElseThrow();
    var graph = CfgDerivers.standard().derive(ast,
        new CfgRequest(ast.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst().graph().orElseThrow();
    var draft = new ControlFlowGraphBuilder().build(method, ast.body(method.methodKey()).orElseThrow()).graph().orElseThrow();
    var creating = new HashMap<String, SyntaxNode>();
    for (var index = 0; index < graph.operationNodes().size(); index++) {
      var operation = graph.operationNodes().get(index);
      creating.put(draft.operationNodes().get(index).nodeId(), operation.sourceRef().orElseThrow().occurrence().flatMap(ast::node)
          .orElse(ast.body(method.methodKey()).orElseThrow().root()));
    }
    return new Fixture(ast, new LoweringPlan(ast.bodyRef(method.methodKey()).orElseThrow(), CfgProfile.JAVA_BOUNDED), graph, draft, creating);
  }

  private record Fixture(AstResult ast, LoweringPlan plan, ControlFlowGraph graph, ControlFlowGraph draft, Map<String, SyntaxNode> creating) {
  }
}
