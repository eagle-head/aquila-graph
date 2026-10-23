package org.aquilagraph.repository.internal.neo4j;

import java.util.List;
import java.util.Set;
import org.aquilagraph.application.port.RepositoryException;
import org.neo4j.driver.Record;

/** Closed labels and inspected constraints for the independent AST namespace. */
final class Neo4jAstSchema {
  private static final Set<String> LABELS = Set.of("AstResult", "AstInput", "AstEnvironment", "AstSourceFile", "AstDeclaration",
      "AstOccurrence", "AstType", "AstAnnotationValue", "AstBody", "AstEvidence", "AstCoverage", "AstDiagnostic", "AstValue");
  private static final Set<String> RELATIONSHIPS = Set.of("AST_FIELD", "HAS_AST_INPUT", "HAS_AST_ENVIRONMENT", "DECLARES_AST", "AST_CHILD",
      "AST_SEMANTIC", "HAS_AST_BODY", "HAS_AST_EVIDENCE", "HAS_AST_COVERAGE", "HAS_AST_DIAGNOSTIC", "HAS_AST_VALUES");

  private Neo4jAstSchema() {
  }

  static void requireLabel(String label) {
    if (!LABELS.contains(label)) {
      throw new IllegalArgumentException("Unsupported AST storage label: " + label);
    }
  }

  static void requireRelationship(String type) {
    if (!RELATIONSHIPS.contains(type)) {
      throw new IllegalArgumentException("Unsupported AST storage relationship: " + type);
    }
  }

  static String label(String tag) {
    var exact = switch (tag) {
      case "input-snapshot" -> "AstInput";
      case "analysis-environment-snapshot" -> "AstEnvironment";
      case "source-file" -> "AstSourceFile";
      case "source-node" -> "AstOccurrence";
      default -> "";
    };
    if (!exact.isEmpty()) {
      return exact;
    }
    if ("copied-ast-values/body".equals(tag) || "method-body-origin".equals(tag)) {
      return "AstBody";
    }
    if (tag.startsWith("annotation-") || tag.startsWith("syntax-value/annotation")) {
      return "AstAnnotationValue";
    }
    if (tag.startsWith("java-type") || tag.startsWith("syntax-type")) {
      return "AstType";
    }
    if (tag.startsWith("java-") || tag.startsWith("declaration-")) {
      return "AstDeclaration";
    }
    if (tag.startsWith("coverage-") || tag.startsWith("source-coverage-") || tag.startsWith("language-feature")
        || "feature-context-outcome".equals(tag) || "attributed-coverage-expectations".equals(tag)
        || "source-coverage-inventory".equals(tag)) {
      return "AstCoverage";
    }
    if (tag.startsWith("diagnostic-") || "analysis-diagnostic".equals(tag)) {
      return "AstDiagnostic";
    }
    if (tag.startsWith("evidence-") || tag.endsWith("-evidence") || tag.endsWith("-decision") || tag.startsWith("typed-use-")
        || tag.startsWith("invocation-applicability") || tag.startsWith("enum-constructor-context-")) {
      return "AstEvidence";
    }
    return "AstValue";
  }

  static String relationship(String field) {
    return switch (field) {
      case "snapshot" -> "HAS_AST_INPUT";
      case "environmentSnapshot" -> "HAS_AST_ENVIRONMENT";
      case "program" -> "DECLARES_AST";
      case "children" -> "AST_CHILD";
      case "semanticReference", "semanticEdges" -> "AST_SEMANTIC";
      case "bodies", "root" -> "HAS_AST_BODY";
      case "uses", "invocations", "staticAccesses", "concatenations", "enumConstructors" -> "HAS_AST_EVIDENCE";
      case "languageFeatureCoverageResult", "validationEvidence" -> "HAS_AST_COVERAGE";
      case "diagnostics", "diagnosticEmissions" -> "HAS_AST_DIAGNOSTIC";
      default -> "AST_FIELD";
    };
  }

  static void requireConstraints(List<Record> definitions) {
    requireConstraint(definitions, "ast_entity_identity", "AstEntity", List.of("astResultKey", "key"));
    requireConstraint(definitions, "ast_result_identity", "AstResult", List.of("astResultKey"));
  }

  private static void requireConstraint(List<Record> definitions, String name, String label, List<String> properties) {
    var named = definitions.stream().filter(definition -> name.equals(definition.get("name").asObject())).toList();
    if (named.size() != 1 || !"NODE_PROPERTY_UNIQUENESS".equals(named.getFirst().get("type").asObject())
        || !"NODE".equals(named.getFirst().get("entityType").asObject())
        || !List.of(label).equals(named.getFirst().get("labelsOrTypes").asObject())
        || !properties.equals(named.getFirst().get("properties").asObject())) {
      throw new RepositoryException("Neo4j AST schema definition mismatch",
          new IllegalStateException("Required AST node-property uniqueness constraint is missing or incompatible: " + name));
    }
  }
}
