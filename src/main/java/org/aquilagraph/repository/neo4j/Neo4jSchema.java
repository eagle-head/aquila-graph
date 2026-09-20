package org.aquilagraph.repository.neo4j;

import java.util.Set;

/** Neo4j labels, relationship types, and shared property names owned by this adapter. */
final class Neo4jSchema {
  static final String KIND_PROPERTY = "kind";
  static final String POSITION_PROPERTY = "position";

  private static final Set<String> NODE_LABELS = Set.of("AnalysisRun", "InputSnapshot", "AnalysisEnvironmentSnapshot", "DependencySnapshot",
      "CompilerConfiguration", "SourceFile", "Program", "JavaType", "JavaMethod", "JavaParameter", "MethodBody", "SyntaxNode",
      "SemanticReference", "MethodAnalysis", "ControlFlowGraph", "FlowValue", "EntryNode", "OperationNode", "ExitNode", "Diagnostic",
      "SourceOrigin");
  private static final Set<String> RELATIONSHIP_TYPES = Set.of("HAS_INPUT", "HAS_ENVIRONMENT", "HAS_CONFIGURATION", "HAS_CLASS_PATH_ENTRY",
      "HAS_MODULE_PATH_ENTRY", "HAS_SOURCE", "HAS_PROGRAM", "HAS_DIAGNOSTIC", "DECLARES_TYPE", "DECLARES_METHOD", "DECLARES_PARAMETER",
      "HAS_BODY", "HAS_SYNTAX_ROOT", "CHILD", "HAS_REFERENCE", "HAS_ORIGIN", "IN_SOURCE", "HAS_METHOD_ANALYSIS", "ANALYZES_METHOD",
      "HAS_VALUE", "USES_VALUE", "DEFINES_VALUE", "HAS_CFG", "HAS_NODE", "NORMAL_FLOW", "TRUE_FLOW", "FALSE_FLOW", "SWITCH_FLOW",
      "RETURN_FLOW", "EXCEPTIONAL_FLOW");

  private Neo4jSchema() {
  }

  static void requireNodeLabel(String label) {
    requireAllowed(label, NODE_LABELS, "node label");
  }

  static void requireRelationshipType(String type) {
    requireAllowed(type, RELATIONSHIP_TYPES, "relationship type");
  }

  private static void requireAllowed(String value, Set<String> allowed, String kind) {
    if (!allowed.contains(value)) {
      throw new IllegalArgumentException("Unsupported " + kind + ": " + value);
    }
  }
}
