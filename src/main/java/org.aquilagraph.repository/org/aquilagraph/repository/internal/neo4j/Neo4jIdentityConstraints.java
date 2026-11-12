package org.aquilagraph.repository.internal.neo4j;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.aquilagraph.application.port.RepositoryException;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

/** Required Neo4j identity constraints for the two storage namespaces. */
final class Neo4jIdentityConstraints {
  static final Neo4jIdentityConstraints ANALYSIS_RUN = new Neo4jIdentityConstraints("Neo4j repository schema definition mismatch",
      "Required node-property uniqueness constraint is missing or incompatible: ",
      List.of(new NodeUniquenessConstraint("analysis_entity_identity", "entity", "AnalysisEntity", List.of("runId", "key")),
          new NodeUniquenessConstraint("analysis_run_identity", "run", "AnalysisRun", List.of("runId"))));
  static final Neo4jIdentityConstraints AST_RESULT = new Neo4jIdentityConstraints("Neo4j AST schema definition mismatch",
      "Required AST node-property uniqueness constraint is missing or incompatible: ",
      List.of(new NodeUniquenessConstraint("ast_entity_identity", "entity", "AstEntity", List.of("astResultKey", "key")),
          new NodeUniquenessConstraint("ast_result_identity", "result", "AstResult", List.of("astResultKey"))));

  private static final String SHOW_CONSTRAINTS = "SHOW CONSTRAINTS YIELD name, type, entityType, labelsOrTypes, properties "
      + "WHERE name IN $names RETURN name, type, entityType, labelsOrTypes, properties";
  private static final int REQUIRED_NAMED_DEFINITION_COUNT = 1;
  private final String mismatchDescription;
  private final String mismatchCausePrefix;
  private final List<NodeUniquenessConstraint> required;

  private Neo4jIdentityConstraints(String mismatchDescription, String mismatchCausePrefix, List<NodeUniquenessConstraint> required) {
    this.mismatchDescription = mismatchDescription;
    this.mismatchCausePrefix = mismatchCausePrefix;
    this.required = List.copyOf(required);
  }

  void ensure(Session session) {
    for (var constraint : required) {
      session.run(constraint.createStatement()).consume();
    }
    var names = required.stream().map(NodeUniquenessConstraint::name).toList();
    var definitions = session.run(SHOW_CONSTRAINTS, Values.parameters("names", names)).list();
    for (var constraint : required) {
      requireExactDefinition(constraint, definitions);
    }
  }

  private void requireExactDefinition(NodeUniquenessConstraint constraint, List<Record> definitions) {
    var named = definitions.stream().filter(row -> constraint.name().equals(row.get("name").asObject())).toList();
    if (named.size() != REQUIRED_NAMED_DEFINITION_COUNT) {
      throw mismatch(constraint);
    }
    var row = named.getFirst();
    if (!constraint.matches(field -> row.get(field).asObject())) {
      throw mismatch(constraint);
    }
  }

  private RepositoryException mismatch(NodeUniquenessConstraint constraint) {
    return new RepositoryException(mismatchDescription, new IllegalStateException(mismatchCausePrefix + constraint.name()));
  }

  private record NodeUniquenessConstraint(String name, String variable, String label, List<String> properties) {
    private static final String TYPE = "NODE_PROPERTY_UNIQUENESS";
    private static final String ENTITY_KIND = "NODE";

    private NodeUniquenessConstraint {
      properties = List.copyOf(properties);
    }

    String createStatement() {
      var propertyReferences = properties.stream().map(property -> variable + "." + property).collect(Collectors.joining(", "));
      var requirement = properties.size() == 1 ? propertyReferences : "(" + propertyReferences + ")";
      return "CREATE CONSTRAINT %s IF NOT EXISTS FOR (%s:%s) REQUIRE %s IS UNIQUE".formatted(name, variable, label, requirement);
    }

    boolean matches(Function<String, @Nullable Object> field) {
      return TYPE.equals(field.apply("type")) && ENTITY_KIND.equals(field.apply("entityType"))
          && List.of(label).equals(field.apply("labelsOrTypes")) && properties.equals(field.apply("properties"));
    }
  }
}
