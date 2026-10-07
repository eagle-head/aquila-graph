package org.aquilagraph.cfg.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.identity.api.GraphIdentity;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.CompilerInsertedInvocation;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/** Privileged atomic graph admission, excluded from the immutable reader contract. */
public final class GraphValueAdmission {
  private GraphValueAdmission() {
  }

  /**
   * Validates exact source ownership and computes qualification from the complete supplied graph.
   *
   * @param ast
   *          exact admitted source result
   * @param plan
   *          selected admitted body and interpretation
   * @param draft
   *          unqualified constructed graph
   * @param creatingNodes
   *          exact creating occurrence of each operation
   * @param generatedRoles
   *          explicit responsibilities of generated operations
   * @return immutable intrinsically admitted graph
   */
  public static ControlFlowGraph admit(AstResult ast, LoweringPlan plan, ControlFlowGraph draft, Map<String, SyntaxNode> creatingNodes,
      Map<String, String> generatedRoles) {
    return admit(ast, plan, draft, creatingNodes, generatedRoles, List.of());
  }

  /**
   * Admits complete graph content including its separately ordered diagnostic occurrences.
   *
   * @param ast
   *          exact admitted source result
   * @param plan
   *          exact selected body and graph interpretation
   * @param draft
   *          unqualified immutable graph
   * @param creatingNodes
   *          exact creating occurrence of every operation
   * @param generatedRoles
   *          explicit generated operation responsibilities
   * @param diagnostics
   *          ordered graph diagnostics with owned captured origins
   * @return graph intrinsically qualified over every retained public field
   */
  public static ControlFlowGraph admit(AstResult ast, LoweringPlan plan, ControlFlowGraph draft, Map<String, SyntaxNode> creatingNodes,
      Map<String, String> generatedRoles, List<AnalysisDiagnostic> diagnostics) {
    Objects.requireNonNull(ast, "ast");
    Objects.requireNonNull(plan, "plan");
    Objects.requireNonNull(draft, "draft");
    Objects.requireNonNull(creatingNodes, "creatingNodes");
    Objects.requireNonNull(generatedRoles, "generatedRoles");
    if (!ast.identity().equals(plan.body().astIdentity()) || !ast.bodyRef(plan.body().methodKey()).filter(plan.body()::equals).isPresent()
        || !draft.methodKey().equals(plan.body().methodKey()) || draft.identity().isPresent()
        || !CfgAdmission.assess(ast, plan.body().methodKey(), plan.profile()).plan().filter(plan::equals).isPresent()
        || !creatingNodes.keySet().equals(draft.operationNodes().stream().map(OperationNode::nodeId).collect(Collectors.toSet()))
        || !creatingNodes.keySet().containsAll(generatedRoles.keySet())
        || generatedRoles.values().stream().distinct().count() != generatedRoles.size()) {
      throw new IllegalArgumentException("Graph admission requires the exact admitted source body and every creating occurrence");
    }
    var body = ast.body(plan.body().methodKey()).orElseThrow();
    var graphDiagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    graphDiagnostics.forEach(CfgDiagnosticAdmission::requireCreatingStage);
    if (graphDiagnostics.stream()
        .anyMatch(diagnostic -> diagnostic.origins().stream().anyMatch(
            origin -> !origin.sourceFileKey().equals(body.root().sourceFileKey()) || ast.snapshot().sourceFiles().stream().noneMatch(
                file -> file.sourceFileKey().equals(origin.sourceFileKey()) && origin.endOffsetExclusive() <= file.content().length())))) {
      throw new IllegalArgumentException("Graph diagnostics require the owning CFG stage and captured body source");
    }
    var bodyNodes = Collections.newSetFromMap(new IdentityHashMap<SyntaxNode, Boolean>());
    bodyNodes.addAll(body.root().descendantsAndSelf());
    var sources = draft.operationNodes().stream().map(operation -> {
      var node = Objects.requireNonNull(creatingNodes.get(operation.nodeId()), "operation creating occurrence");
      if (!bodyNodes.contains(node)) {
        throw new IllegalArgumentException("Operation creating occurrence is outside the admitted method body");
      }
      if (operation.origins().stream().anyMatch(origin -> ast.snapshot().sourceFiles().stream().noneMatch(
          file -> file.sourceFileKey().equals(origin.sourceFileKey()) && origin.endOffsetExclusive() <= file.content().length()))) {
        throw new IllegalArgumentException("Operation origins require exact captured source membership and bounds");
      }
      var generated = generatedRoles.containsKey(operation.nodeId());
      if (generated
          && (!node.nodeId().equals(body.root().nodeId()) || !operation.origins().isEmpty() || body.constructorEvidence(node.nodeId())
              .flatMap(value -> value.corroboration().knownValue()).flatMap(value -> value.insertedInvocation()).isEmpty())) {
        throw new IllegalArgumentException("Inserted operations require the exact corroborated body and no written span");
      }
      if (generated) {
        validateGenerated(operation, Objects.requireNonNull(generatedRoles.get(operation.nodeId()), "generated operation role"),
            body.constructorEvidence(node.nodeId()).orElseThrow().corroboration().knownValue().orElseThrow().insertedInvocation()
                .orElseThrow());
      }
      Optional<SourceOccurrence> occurrence = generated ? Optional.empty() : ast.occurrence(node);
      if (occurrence.isEmpty() && !generated && plan.body().sourceOccurrenceKey().isPresent() && node.origin().isPresent()) {
        throw new IllegalArgumentException("A written operation requires its owned canonical source occurrence");
      }
      return new GraphSourceRef(plan.body(), occurrence,
          generatedRoles.getOrDefault(operation.nodeId(), "operation/" + operation.kind().name()));
    }).toList();
    var digest = CanonicalGraphEncoding.digest(draft, plan, sources, graphDiagnostics);
    var identity = GraphIdentity.fromContent(ast.identity(), plan.profile().version(), plan.profile().graphModelVersion(),
        plan.body().methodKey(), digest);
    var graphId = identity.derivationKey();
    var values = new HashMap<String, FlowValue>();
    for (var value : draft.values()) {
      values.put(value.valueId(), qualifiedValue(value, graphId));
    }
    var operations = IntStream.range(0, draft.operationNodes().size()).mapToObj(index -> {
      var operation = draft.operationNodes().get(index);
      return GraphOperationProjection.remap(operation, graphId, operation.nodeId(), values, Optional.of(sources.get(index)));
    }).toList();
    var edges = draft.edges().stream().map(
        edge -> new CfgEdge(edge.edgeId(), graphId, edge.sourceNodeId(), edge.targetNodeId(), edge.transferKind(), edge.discriminator()))
        .toList();
    return new ControlFlowGraph(graphId, plan.body().methodKey(), new EntryNode(draft.entry().nodeId(), graphId), operations,
        draft.values().stream().map(value -> owned(values, value.valueId())).toList(), new ExitNode(draft.exit().nodeId(), graphId), edges,
        Optional.of(identity), Optional.of(plan.body()), graphDiagnostics);
  }

  private static void validateGenerated(OperationNode operation, String role, CompilerInsertedInvocation insertion) {
    var valid = switch (role) {
      case "compiler-inserted-superclass/invoke" ->
        operation.kind() == OperationKind.INVOKE && operation.invocationSemantics().filter(insertion.semantics()::equals).isPresent()
            && operation.semanticReferences().equals(List.of(insertion.reference())) && operation.definedValue().isEmpty()
            && !operation.operands().isEmpty() && operation.operands().getFirst().value().kind() == ValueKind.RECEIVER;
      case "compiler-inserted-superclass/varargs-array" -> operation.kind() == OperationKind.VARARGS_ARRAY
          && insertion.applicability().applicability().knownValue().filter(value -> value.mode() == InvocationApplicabilityMode.VARIABLE)
              .isPresent()
          && operation.invocationSemantics().isEmpty() && operation.semanticReferences().isEmpty() && operation.operands().isEmpty()
          && operation.definedValue().isPresent();
      default -> false;
    };
    if (!valid) {
      throw new IllegalArgumentException("Generated operation role and payload must match the actual corroborated compiler insertion");
    }
  }

  private static FlowValue qualifiedValue(FlowValue value, String graphId) {
    return new FlowValue(value.valueId(), graphId, value.kind(), value.displayName(), value.literal(), value.staticType());
  }

  private static <T> T owned(Map<String, T> values, String key) {
    return Objects.requireNonNull(values.get(key), "owned graph local value");
  }
}
