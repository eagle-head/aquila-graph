package org.aquilagraph.repository.internal.neo4j;

import java.util.Set;

/** Closed labels and relationship types for the independent AST namespace. */
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
}
