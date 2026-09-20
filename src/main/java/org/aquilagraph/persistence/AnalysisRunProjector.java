package org.aquilagraph.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CfgNode;
import org.aquilagraph.model.DependencySnapshot;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.SyntaxNode;
import org.aquilagraph.model.TransferKind;

/** Converts a completed analysis run into a deterministic Neo4j graph projection. */
public final class AnalysisRunProjector {
  private static final String KIND_PROPERTY = "kind";
  private static final String POSITION_PROPERTY = "position";

  /** Creates a stateless analysis-run projector. */
  public AnalysisRunProjector() {
  }

  /**
   * Projects a completed run into validated nodes and relationships.
   *
   * @param run
   *          completed analysis run
   * @return immutable graph projection
   */
  public Neo4jGraphProjection project(AnalysisRun run) {
    Objects.requireNonNull(run, "run");
    if (run.completion() != AnalysisCompletion.COMPLETED) {
      throw new IllegalArgumentException("Only completed analysis runs may be persisted");
    }
    return new ProjectionContext(run.runId()).project(run);
  }

  private static final class ProjectionContext {
    private final String runId;
    private final List<ProjectedNode> nodes = new ArrayList<>();
    private final List<ProjectedRelationship> relationships = new ArrayList<>();

    private ProjectionContext(String runId) {
      this.runId = Objects.requireNonNull(runId, "runId");
    }

    private Neo4jGraphProjection project(AnalysisRun run) {

      node("AnalysisRun", run.runId(), "completion", run.completion().name(), "analyzerVersion", run.analyzerVersion(), "modelVersion",
          run.modelVersion());

      var snapshot = run.inputSnapshot();
      var snapshotKey = StableId.sha256("input-snapshot", snapshot.snapshotDigest());
      node("InputSnapshot", snapshotKey, "sourceRoot", snapshot.sourceRoot().toString(), "digestAlgorithm", snapshot.digestAlgorithm(),
          "snapshotDigest", snapshot.snapshotDigest());
      relate("HAS_INPUT", run.runId(), snapshotKey, "input");
      for (var sourceFile : snapshot.sourceFiles()) {
        node("SourceFile", sourceFile.sourceFileKey(), "relativePath", sourceFile.relativePath().toString().replace('\\', '/'),
            "contentDigest", sourceFile.contentDigest(), "content", sourceFile.content());
        relate("HAS_SOURCE", snapshotKey, sourceFile.sourceFileKey(), sourceFile.relativePath().toString());
      }

      var environment = run.environmentSnapshot();
      var environmentKey = environment.environmentDigest();
      node("AnalysisEnvironmentSnapshot", environmentKey, "digestAlgorithm", environment.digestAlgorithm(), "environmentDigest",
          environment.environmentDigest(), "compilerImplementation", environment.compilerImplementation(), "compilerVendor",
          environment.compilerVendor(), "compilerVersion", environment.compilerVersion(), "analysisOptions", environment.analysisOptions());
      relate("HAS_ENVIRONMENT", run.runId(), environmentKey, "environment");

      var configuration = environment.compilerConfiguration();
      var configurationKey = StableId.sha256("compiler-configuration", configuration.canonicalForm());
      node("CompilerConfiguration", configurationKey, "release", configuration.release(), "classPath",
          configuration.classPath().stream().map(Object::toString).toList(), "modulePath",
          configuration.modulePath().stream().map(Object::toString).toList(), "options", configuration.options());
      relate("HAS_CONFIGURATION", environmentKey, configurationKey, "configuration");
      dependencies(environmentKey, "HAS_CLASS_PATH_ENTRY", "class-path", environment.classPath());
      dependencies(environmentKey, "HAS_MODULE_PATH_ENTRY", "module-path", environment.modulePath());

      for (var index = 0; index < run.diagnostics().size(); index++) {
        var diagnostic = run.diagnostics().get(index);
        node("Diagnostic", diagnostic.diagnosticKey(), "code", diagnostic.code(), "stage", diagnostic.stage().name(), "severity",
            diagnostic.severity().name(), "message", diagnostic.message());
        relate("HAS_DIAGNOSTIC", run.runId(), diagnostic.diagnosticKey(), Integer.toString(index), POSITION_PROPERTY, index);
        origins(diagnostic.diagnosticKey(), diagnostic.origins());
      }

      var representation = run.representation().orElseThrow();
      var programKey = StableId.sha256("program", run.runId());
      node("Program", programKey, "typeCount", representation.program().types().size());
      relate("HAS_PROGRAM", run.runId(), programKey, "program");
      for (var typePosition = 0; typePosition < representation.program().types().size(); typePosition++) {
        var type = representation.program().types().get(typePosition);
        node("JavaType", type.typeKey(), "qualifiedName", type.qualifiedName(), KIND_PROPERTY, type.kind().name(), "sourceDeclared",
            type.sourceDeclared());
        relate("DECLARES_TYPE", programKey, type.typeKey(), Integer.toString(typePosition), POSITION_PROPERTY, typePosition);
        origins(type.typeKey(), type.origins());
        for (var methodPosition = 0; methodPosition < type.methods().size(); methodPosition++) {
          var method = type.methods().get(methodPosition);
          node("JavaMethod", method.methodKey(), "name", method.name(), "signature", method.signature(), "returnType", method.returnType(),
              "modifiers", method.modifiers().stream().sorted().toList(), "constructor", method.constructor(), "sourceDeclared",
              method.sourceDeclared());
          relate("DECLARES_METHOD", type.typeKey(), method.methodKey(), Integer.toString(methodPosition), POSITION_PROPERTY,
              methodPosition);
          origins(method.methodKey(), method.origins());
          for (var parameterPosition = 0; parameterPosition < method.parameters().size(); parameterPosition++) {
            var parameter = method.parameters().get(parameterPosition);
            node("JavaParameter", parameter.parameterKey(), "name", parameter.name(), "staticType", parameter.staticType(),
                POSITION_PROPERTY, parameterPosition);
            relate("DECLARES_PARAMETER", method.methodKey(), parameter.parameterKey(), Integer.toString(parameterPosition),
                POSITION_PROPERTY, parameterPosition);
            origins(parameter.parameterKey(), parameter.origins());
          }
          method.body().ifPresent(body -> {
            var bodyKey = StableId.sha256("method-body", method.methodKey());
            node("MethodBody", bodyKey, "supported", body.supported());
            relate("HAS_BODY", method.methodKey(), bodyKey, "body");
            syntax(body.syntaxRoot(), bodyKey, 0, true);
          });
        }
      }

      for (var analysisPosition = 0; analysisPosition < representation.methodAnalyses().size(); analysisPosition++) {
        var analysis = representation.methodAnalyses().get(analysisPosition);
        node("MethodAnalysis", analysis.analysisKey(), "methodKey", analysis.sourceMethod().methodKey());
        relate("HAS_METHOD_ANALYSIS", programKey, analysis.analysisKey(), Integer.toString(analysisPosition), POSITION_PROPERTY,
            analysisPosition);
        relate("ANALYZES_METHOD", analysis.analysisKey(), analysis.sourceMethod().methodKey(), "method");

        var graph = analysis.controlFlowGraph();
        node("ControlFlowGraph", graph.graphId(), "methodKey", graph.methodKey());
        relate("HAS_CFG", analysis.analysisKey(), graph.graphId(), "cfg");
        for (var valuePosition = 0; valuePosition < graph.values().size(); valuePosition++) {
          var value = graph.values().get(valuePosition);
          var valueProperties = properties("graphId", value.graphId(), KIND_PROPERTY, value.kind().name(), POSITION_PROPERTY,
              valuePosition);
          value.displayName().ifPresent(displayName -> valueProperties.put("displayName", displayName));
          value.literal().ifPresent(literal -> valueProperties.put("literal", literal));
          value.staticType().ifPresent(staticType -> valueProperties.put("staticType", staticType));
          node("FlowValue", value.valueId(), valueProperties);
          relate("HAS_VALUE", graph.graphId(), value.valueId(), Integer.toString(valuePosition), POSITION_PROPERTY, valuePosition);
        }
        var graphNodes = graph.nodes();
        for (var nodePosition = 0; nodePosition < graphNodes.size(); nodePosition++) {
          var cfgNode = graphNodes.get(nodePosition);
          var label = cfgNodeLabel(cfgNode);
          if (cfgNode instanceof OperationNode operationNode) {
            node(label, cfgNode.nodeId(), "graphId", cfgNode.graphId(), KIND_PROPERTY, operationNode.kind().name(), "detail",
                operationNode.detail(), "possibleExceptions", operationNode.possibleExceptions());
            semanticReferences(operationNode.nodeId(), operationNode.semanticReferences());
            for (var operand : operationNode.operands()) {
              relate("USES_VALUE", operationNode.nodeId(), operand.value().valueId(), Integer.toString(operand.position()),
                  POSITION_PROPERTY, operand.position());
            }
            operationNode.definedValue().ifPresent(value -> relate("DEFINES_VALUE", operationNode.nodeId(), value.valueId(), "definition"));
          } else {
            node(label, cfgNode.nodeId(), "graphId", cfgNode.graphId());
          }
          relate("HAS_NODE", graph.graphId(), cfgNode.nodeId(), Integer.toString(nodePosition), POSITION_PROPERTY, nodePosition);
          origins(cfgNode.nodeId(), cfgNode.origins());
        }
        for (var edge : graph.edges()) {
          var edgeProperties = properties("edgeId", edge.edgeId());
          edge.discriminator().ifPresent(value -> edgeProperties.put("discriminator", value));
          relate(relationshipType(edge.transferKind()), edge.sourceNodeId(), edge.targetNodeId(), edge.edgeId(), edgeProperties);
        }
      }
      return new Neo4jGraphProjection(runId, nodes, relationships);
    }

    private void dependencies(String environmentKey, String relationshipType, String role, List<DependencySnapshot> dependencies) {
      for (var position = 0; position < dependencies.size(); position++) {
        var dependency = dependencies.get(position);
        var dependencyKey = StableId.sha256("dependency-snapshot", environmentKey, role, Integer.toString(position),
            dependency.fingerprint());
        node("DependencySnapshot", dependencyKey, "location", dependency.location().toString(), KIND_PROPERTY, dependency.kind().name(),
            "byteCount", dependency.byteCount(), "entryCount", dependency.entryCount(), "fingerprint", dependency.fingerprint(),
            POSITION_PROPERTY, position);
        relate(relationshipType, environmentKey, dependencyKey, Integer.toString(position), POSITION_PROPERTY, position);
      }
    }

    private void syntax(SyntaxNode syntaxNode, String parentKey, int position, boolean root) {
      node("SyntaxNode", syntaxNode.nodeId(), KIND_PROPERTY, syntaxNode.syntaxKind().name(), "detail", syntaxNode.detail(),
          "compilerGenerated", syntaxNode.compilerGenerated());
      relate(root ? "HAS_SYNTAX_ROOT" : "CHILD", parentKey, syntaxNode.nodeId(), Integer.toString(position), POSITION_PROPERTY, position);
      origins(syntaxNode.nodeId(), syntaxNode.origins());
      semanticReferences(syntaxNode.nodeId(), syntaxNode.semanticReferences());
      for (var childPosition = 0; childPosition < syntaxNode.children().size(); childPosition++) {
        syntax(syntaxNode.children().get(childPosition), syntaxNode.nodeId(), childPosition, false);
      }
    }

    private void semanticReferences(String ownerKey, List<org.aquilagraph.model.SemanticReference> semanticReferences) {
      for (var position = 0; position < semanticReferences.size(); position++) {
        var reference = semanticReferences.get(position);
        var key = StableId.sha256("semantic-reference", ownerKey, Integer.toString(position));
        var referenceProperties = properties(KIND_PROPERTY, reference.referenceKind().name(), "status", reference.status().name(),
            POSITION_PROPERTY, position);
        reference.symbolKey().ifPresent(value -> referenceProperties.put("symbolKey", value));
        reference.typeName().ifPresent(value -> referenceProperties.put("typeName", value));
        node("SemanticReference", key, referenceProperties);
        relate("HAS_REFERENCE", ownerKey, key, Integer.toString(position), POSITION_PROPERTY, position);
      }
    }

    private void origins(String ownerKey, List<SourceSpan> origins) {
      for (var position = 0; position < origins.size(); position++) {
        var origin = origins.get(position);
        var key = StableId.sha256("source-origin", ownerKey, Integer.toString(position), origin.sourceFileKey(),
            Long.toString(origin.startOffset()), Long.toString(origin.endOffsetExclusive()));
        node("SourceOrigin", key, "startOffset", origin.startOffset(), "endOffsetExclusive", origin.endOffsetExclusive(), POSITION_PROPERTY,
            position);
        relate("HAS_ORIGIN", ownerKey, key, Integer.toString(position), POSITION_PROPERTY, position);
        relate("IN_SOURCE", key, origin.sourceFileKey(), "source");
      }
    }

    private void node(String label, String key, Object... pairs) {
      node(label, key, properties(pairs));
    }

    private void node(String label, String key, Map<String, Object> properties) {
      var allProperties = new LinkedHashMap<String, Object>();
      allProperties.put("runId", runId);
      allProperties.put("key", key);
      allProperties.putAll(properties);
      nodes.add(new ProjectedNode(key, label, allProperties));
    }

    private void relate(String type, String sourceKey, String targetKey, String discriminator, Object... pairs) {
      relate(type, sourceKey, targetKey, discriminator, properties(pairs));
    }

    private void relate(String type, String sourceKey, String targetKey, String discriminator, Map<String, Object> properties) {
      var key = StableId.sha256("relationship", type, sourceKey, targetKey, discriminator);
      var allProperties = new LinkedHashMap<String, Object>();
      allProperties.put("runId", runId);
      allProperties.put("key", key);
      allProperties.putAll(properties);
      relationships.add(new ProjectedRelationship(key, type, sourceKey, targetKey, allProperties));
    }

    private static Map<String, Object> properties(Object... pairs) {
      if (pairs.length % 2 != 0) {
        throw new IllegalArgumentException("Property names and values must be paired");
      }
      var result = new LinkedHashMap<String, Object>();
      for (var index = 0; index < pairs.length; index += 2) {
        result.put((String) pairs[index], Objects.requireNonNull(pairs[index + 1], "property value"));
      }
      return result;
    }

    private static String cfgNodeLabel(CfgNode node) {
      return switch (node) {
        case org.aquilagraph.model.EntryNode ignored -> "EntryNode";
        case OperationNode ignored -> "OperationNode";
        case org.aquilagraph.model.ExitNode ignored -> "ExitNode";
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
}
