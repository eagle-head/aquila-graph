package org.aquilagraph.repository.neo4j;

import java.util.Set;
import java.util.List;
import org.aquilagraph.application.port.RepositoryException;
import org.neo4j.driver.Record;

/** Neo4j labels, relationship types, and shared property names owned by this adapter. */
final class Neo4jSchema {
  static final String KIND_PROPERTY = "kind";
  static final String POSITION_PROPERTY = "position";
  private static final long EXPECTED_NAMED_DEFINITION_COUNT = 1;

  private static final Set<String> NODE_LABELS = Set.of("AnalysisRun", "SoftwareVersion", "InputSnapshot", "AnalysisEnvironmentSnapshot",
      "DependencySnapshot", "CompilerConfiguration", "SourceFile", "Program", "JavaType", "JavaField", "JavaRecordComponent",
      "JavaSynthesizedDeclaration", "JavaMethod", "JavaParameter", "JavaTypeParameter", "JavaTypeReference", "SemanticReference",
      "MethodAnalysis", "ControlFlowGraph", "FlowValue", "EntryNode", "OperationNode", "ExitNode", "Diagnostic", "SourceOrigin");
  private static final Set<String> RELATIONSHIP_TYPES = Set.of("ANALYZED_SOFTWARE", "HAS_INPUT", "HAS_ENVIRONMENT", "HAS_CONFIGURATION",
      "HAS_CLASS_PATH_ENTRY", "HAS_MODULE_PATH_ENTRY", "HAS_SOURCE", "HAS_PROGRAM", "HAS_DIAGNOSTIC", "DECLARES_TYPE", "DECLARES_FIELD",
      "DECLARES_RECORD_COMPONENT", "DECLARES_SYNTHESIZED", "DECLARES_METHOD", "DECLARES_PARAMETER", "DECLARES_TYPE_PARAMETER",
      "ENCLOSED_BY", "HAS_DIRECT_SUPERCLASS", "HAS_DIRECT_INTERFACE", "PERMITS_SUBCLASS", "HAS_TYPE_BOUND", "HAS_REFERENCE", "HAS_ORIGIN",
      "IN_SOURCE", "HAS_METHOD_ANALYSIS", "ANALYZES_METHOD", "HAS_VALUE", "USES_VALUE", "DEFINES_VALUE", "HAS_CFG", "HAS_NODE",
      "NORMAL_FLOW", "TRUE_FLOW", "FALSE_FLOW", "SWITCH_FLOW", "RETURN_FLOW", "EXCEPTIONAL_FLOW");

  private Neo4jSchema() {
  }

  static void requireNodeLabel(String label) {
    requireAllowed(label, NODE_LABELS, "node label");
  }

  static void requireRelationshipType(String type) {
    requireAllowed(type, RELATIONSHIP_TYPES, "relationship type");
  }

  static void requireIdentityConstraints(List<Record> definitions) {
    requireIdentityConstraint(definitions, "analysis_entity_identity", "AnalysisEntity", List.of("runId", "key"));
    requireIdentityConstraint(definitions, "analysis_run_identity", "AnalysisRun", List.of("runId"));
  }

  private static void requireIdentityConstraint(List<Record> definitions, String name, String label, List<String> properties) {
    var namedDefinitions = definitions.stream().filter(definition -> name.equals(definition.get("name").asObject())).toList();
    var matching = namedDefinitions.stream()
        .filter(definition -> "NODE_PROPERTY_UNIQUENESS".equals(definition.get("type").asObject())
            && "NODE".equals(definition.get("entityType").asObject()) && List.of(label).equals(definition.get("labelsOrTypes").asObject())
            && properties.equals(definition.get("properties").asObject()))
        .count();
    if (namedDefinitions.size() != EXPECTED_NAMED_DEFINITION_COUNT || matching != EXPECTED_NAMED_DEFINITION_COUNT) {
      throw new RepositoryException("Neo4j repository schema definition mismatch",
          new IllegalStateException("Required node-property uniqueness constraint is missing or incompatible: " + name));
    }
  }

  private static void requireAllowed(String value, Set<String> allowed, String kind) {
    if (!allowed.contains(value)) {
      throw new IllegalArgumentException("Unsupported " + kind + ": " + value);
    }
  }
}
