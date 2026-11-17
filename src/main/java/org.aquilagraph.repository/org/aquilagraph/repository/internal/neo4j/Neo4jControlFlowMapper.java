package org.aquilagraph.repository.internal.neo4j;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.cfg.api.CfgNode;
import org.aquilagraph.cfg.api.EntryNode;
import org.aquilagraph.cfg.api.ExitNode;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.ControlFlowGraph;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.cfg.api.OperationNode;
import org.aquilagraph.cfg.api.TransferKind;

/** Maps method analyses and control-flow graphs into a Neo4j projection. */
final class Neo4jControlFlowMapper {
  private final Neo4jEvidenceMapper evidenceMapper;

  Neo4jControlFlowMapper(Neo4jEvidenceMapper evidenceMapper) {
    this.evidenceMapper = Objects.requireNonNull(evidenceMapper, "evidenceMapper");
  }

  void map(String programKey, List<MethodCfgOutcome> analyses, Neo4jProjectionBuilder projection) {
    for (var analysisPosition = 0; analysisPosition < analyses.size(); analysisPosition++) {
      var analysis = analyses.get(analysisPosition);
      if (analysis.graph().isEmpty()) {
        continue;
      }
      var graph = analysis.graph().orElseThrow();
      var analysisKey = StableId.sha256("method-analysis", graph.graphId());
      projection.node("MethodAnalysis", analysisKey, "methodKey", analysis.methodKey(), "state", analysis.state().name());
      projection.relate("HAS_METHOD_ANALYSIS", programKey, analysisKey, Integer.toString(analysisPosition), Neo4jSchema.POSITION_PROPERTY,
          analysisPosition);
      projection.relate("ANALYZES_METHOD", analysisKey, analysis.methodKey(), "method");
      mapGraph(analysisKey, graph, projection);
    }
  }

  private void mapGraph(String analysisKey, ControlFlowGraph graph, Neo4jProjectionBuilder projection) {
    var identity = graph.identity();
    var body = graph.body();
    var graphProperties = Neo4jProjectionBuilder.properties("methodKey", graph.methodKey(), "astResultKey",
        identity.astIdentity().astResultKey(), "cfgProfileVersion", identity.cfgProfileVersion(), "graphModelVersion",
        identity.graphModelVersion(), "canonicalContentDigest", identity.canonicalContentDigest());
    body.sourceOccurrenceKey().ifPresent(key -> graphProperties.put("sourceOccurrenceKey", key));
    body.nonwrittenProvenance().ifPresent(origin -> {
      graphProperties.put("nonwrittenProvenance", origin.origin());
      graphProperties.put("bodySourceFileKey", origin.sourceFileKey());
      graphProperties.put("bodyOwnerDeclarationKey", origin.ownerDeclarationKey());
    });
    projection.node("ControlFlowGraph", graph.graphId(), graphProperties);
    projection.relate("HAS_CFG", analysisKey, graph.graphId(), "cfg");

    for (var valuePosition = 0; valuePosition < graph.values().size(); valuePosition++) {
      var value = graph.values().get(valuePosition);
      var properties = Neo4jProjectionBuilder.properties("graphId", value.graphId(), Neo4jSchema.KIND_PROPERTY, value.kind().name(),
          Neo4jSchema.POSITION_PROPERTY, valuePosition);
      value.displayName().ifPresent(displayName -> properties.put("displayName", displayName));
      value.literal().ifPresent(literal -> properties.put("literal", literal));
      value.staticType().ifPresent(staticType -> Neo4jTypeProperties.put(properties, "staticType", staticType));
      projection.node("FlowValue", value.valueId(), properties);
      projection.relate("HAS_VALUE", graph.graphId(), value.valueId(), Integer.toString(valuePosition), Neo4jSchema.POSITION_PROPERTY,
          valuePosition);
    }

    var graphNodes = graph.nodes();
    for (var nodePosition = 0; nodePosition < graphNodes.size(); nodePosition++) {
      mapNode(graphNodes.get(nodePosition), graph.graphId(), nodePosition, projection);
    }
    for (var edge : graph.edges()) {
      var properties = Neo4jProjectionBuilder.properties("edgeId", edge.edgeId());
      edge.discriminator().ifPresent(value -> properties.put("discriminator", value));
      projection.relate(relationshipType(edge.transferKind()), edge.sourceNodeId(), edge.targetNodeId(), edge.edgeId(), properties);
    }
  }

  private void mapNode(CfgNode cfgNode, String graphId, int position, Neo4jProjectionBuilder projection) {
    var label = cfgNodeLabel(cfgNode);
    if (cfgNode instanceof OperationNode operationNode) {
      var properties = Neo4jProjectionBuilder.properties("graphId", cfgNode.graphId(), Neo4jSchema.KIND_PROPERTY,
          operationNode.kind().name(), "detail", operationNode.detail(), "possibleExceptions", operationNode.possibleExceptions());
      operationNode.invocationSemantics().ifPresent(semantics -> {
        properties.put("invocationMode", semantics.mode().name());
        properties.put("invocationForm", semantics.form().name());
        properties.put("invocationSelection", semantics.selection().name());
        Neo4jTypeProperties.put(properties, "qualifyingType", semantics.qualifyingType().identity());
        semantics.qualifyingType().declarationKey().ifPresent(key -> properties.put("qualifyingTypeDeclarationKey", key));
      });
      projection.node(label, cfgNode.nodeId(), properties);
      evidenceMapper.mapSemanticReferences(operationNode.nodeId(), operationNode.semanticReferences(), projection);
      for (var operand : operationNode.operands()) {
        projection.relate("USES_VALUE", operationNode.nodeId(), operand.value().valueId(), Integer.toString(operand.position()),
            Neo4jSchema.POSITION_PROPERTY, operand.position());
      }
      operationNode.definedValue()
          .ifPresent(value -> projection.relate("DEFINES_VALUE", operationNode.nodeId(), value.valueId(), "definition"));
    } else {
      projection.node(label, cfgNode.nodeId(), "graphId", cfgNode.graphId());
    }
    projection.relate("HAS_NODE", graphId, cfgNode.nodeId(), Integer.toString(position), Neo4jSchema.POSITION_PROPERTY, position);
    evidenceMapper.mapOrigins(cfgNode.nodeId(), cfgNode.origins(), projection);
  }

  private static String cfgNodeLabel(CfgNode node) {
    return switch (node) {
      case EntryNode _ -> "EntryNode";
      case OperationNode _ -> "OperationNode";
      case ExitNode _ -> "ExitNode";
    };
  }

  private static String relationshipType(TransferKind transferKind) {
    return switch (transferKind) {
      case NORMAL -> "NORMAL_FLOW";
      case TRUE_BRANCH -> "TRUE_FLOW";
      case FALSE_BRANCH -> "FALSE_FLOW";
      case SWITCH_ALTERNATIVE -> "SWITCH_FLOW";
      case RETURN -> "RETURN_FLOW";
      case EXCEPTIONAL -> "EXCEPTIONAL_FLOW";
    };
  }
}
