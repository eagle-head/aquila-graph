package org.aquilagraph.repository.neo4j;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.CfgNode;
import org.aquilagraph.model.EntryNode;
import org.aquilagraph.model.ExitNode;
import org.aquilagraph.model.MethodAnalysis;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.TransferKind;

/** Maps method analyses and control-flow graphs into a Neo4j projection. */
final class Neo4jControlFlowMapper {
  private final Neo4jEvidenceMapper evidenceMapper;

  Neo4jControlFlowMapper(Neo4jEvidenceMapper evidenceMapper) {
    this.evidenceMapper = Objects.requireNonNull(evidenceMapper, "evidenceMapper");
  }

  void map(String programKey, List<MethodAnalysis> analyses, Neo4jProjectionBuilder projection) {
    for (var analysisPosition = 0; analysisPosition < analyses.size(); analysisPosition++) {
      var analysis = analyses.get(analysisPosition);
      projection.node("MethodAnalysis", analysis.analysisKey(), "methodKey", analysis.sourceMethod().methodKey());
      projection.relate("HAS_METHOD_ANALYSIS", programKey, analysis.analysisKey(), Integer.toString(analysisPosition),
          Neo4jSchema.POSITION_PROPERTY, analysisPosition);
      projection.relate("ANALYZES_METHOD", analysis.analysisKey(), analysis.sourceMethod().methodKey(), "method");
      mapGraph(analysis, projection);
    }
  }

  private void mapGraph(MethodAnalysis analysis, Neo4jProjectionBuilder projection) {
    var graph = analysis.controlFlowGraph();
    projection.node("ControlFlowGraph", graph.graphId(), "methodKey", graph.methodKey());
    projection.relate("HAS_CFG", analysis.analysisKey(), graph.graphId(), "cfg");

    for (var valuePosition = 0; valuePosition < graph.values().size(); valuePosition++) {
      var value = graph.values().get(valuePosition);
      var properties = Neo4jProjectionBuilder.properties("graphId", value.graphId(), Neo4jSchema.KIND_PROPERTY, value.kind().name(),
          Neo4jSchema.POSITION_PROPERTY, valuePosition);
      value.displayName().ifPresent(displayName -> properties.put("displayName", displayName));
      value.literal().ifPresent(literal -> properties.put("literal", literal));
      value.staticType().ifPresent(staticType -> properties.put("staticType", staticType));
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
      projection.node(label, cfgNode.nodeId(), "graphId", cfgNode.graphId(), Neo4jSchema.KIND_PROPERTY, operationNode.kind().name(),
          "detail", operationNode.detail(), "possibleExceptions", operationNode.possibleExceptions());
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
      case EntryNode ignored -> "EntryNode";
      case OperationNode ignored -> "OperationNode";
      case ExitNode ignored -> "ExitNode";
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
