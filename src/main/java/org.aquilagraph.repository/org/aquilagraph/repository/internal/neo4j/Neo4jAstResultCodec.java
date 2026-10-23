package org.aquilagraph.repository.internal.neo4j;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.retention.AstValueProjection.Node;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.jspecify.annotations.Nullable;

/** Complete AST envelope and every ordered materialized field, independent of CFG. */
final class Neo4jAstResultCodec {
  static final String FORMAT_VERSION = AstCodec.FORMAT_VERSION;

  Neo4jAstProjection map(AstOutcome outcome) {
    var nodes = new ArrayList<Neo4jNode>();
    var relationships = new ArrayList<Neo4jRelationship>();
    project(outcome, nodes::add, relationships::add);
    return new Neo4jAstProjection(outcome.result().orElseThrow().identity(), nodes, relationships);
  }

  private static void project(AstOutcome outcome, Consumer<Neo4jNode> nodes, Consumer<Neo4jRelationship> relationships) {
    Objects.requireNonNull(outcome, "outcome");
    if (outcome.completion() != AstCompletion.COMPLETE) {
      throw new IllegalArgumentException("AST retention requires complete admitted source");
    }
    var result = outcome.result().orElseThrow();
    var identity = result.identity();
    var bytes = AstCodec.encode(result);
    var values = AstCodec.materialize(result);
    var valueKeys = values.nodes().stream().map(value -> valueKey(identity, value.ordinal())).toList();
    nodes.accept(root(identity, Base64.getEncoder().encodeToString(bytes)));
    for (var value : values.nodes()) {
      nodes.accept(node(identity, value, valueKeys.get(value.ordinal())));
    }
    relationships.accept(link(identity, identity.astResultKey(), valueKeys.getFirst(), "HAS_AST_VALUES", "values", 0));
    for (var component : values.links()) {
      relationships.accept(link(identity, valueKeys.get(component.sourceOrdinal()), valueKeys.get(component.targetOrdinal()),
          Neo4jAstSchema.relationship(component.field()), component.field(), component.position()));
    }
  }

  private static Neo4jNode node(AstIdentity identity, Node value, String key) {
    var properties = scoped(identity, key);
    properties.put("ordinal", (long) value.ordinal());
    properties.put("tag", value.tag());
    value.scalarEncoding().ifPresent(encoding -> properties.put("scalarEncoding", encoding));
    value.sourceNodeKey().ifPresent(sourceKey -> properties.put("sourceNodeKey", sourceKey));
    return new Neo4jNode(key, Neo4jAstSchema.label(value.tag()), properties);
  }

  AstOutcome decode(Neo4jAstProjection stored, AstIdentity expected) {
    if (!stored.identity().equals(expected)) {
      throw new IllegalArgumentException("Stored AST projection belongs to another requested identity");
    }
    var payload = stored.root().properties().get("payload");
    if (!(payload instanceof String encoded)) {
      throw new IllegalArgumentException("Supported AST result requires its complete retained envelope");
    }
    var outcome = AstOutcome.available(AstCodec.decode(Base64.getDecoder().decode(encoded), expected));
    var nodes = byKey(stored.nodes(), Neo4jNode::key);
    var relationships = byKey(stored.relationships(), Neo4jRelationship::key);
    project(outcome, node -> requireEqual(nodes.remove(node.key()), node),
        relationship -> requireEqual(relationships.remove(relationship.key()), relationship));
    if (!nodes.isEmpty() || !relationships.isEmpty()) {
      throw materializationMismatch();
    }
    return outcome;
  }

  private static <T> void requireEqual(@Nullable T actual, T expected) {
    if (!expected.equals(actual)) {
      throw materializationMismatch();
    }
  }

  private static IllegalArgumentException materializationMismatch() {
    return new IllegalArgumentException("Materialized AST fields, order, labels, or correlations differ from the admitted envelope");
  }

  private static Neo4jNode root(AstIdentity identity, String payload) {
    var properties = scoped(identity, identity.astResultKey());
    properties.put("formatVersion", identity.sourceModelVersion());
    properties.put("softwareSignature", identity.softwareSignature());
    properties.put("sourceModelVersion", identity.sourceModelVersion());
    properties.put("astProfileVersion", identity.astProfileVersion());
    properties.put("semanticProductionRevision", identity.semanticProductionRevision());
    properties.put("canonicalContentDigest", identity.canonicalContentDigest());
    properties.put("completion", AstCompletion.COMPLETE.name());
    properties.put("payload", payload);
    return new Neo4jNode(identity.astResultKey(), "AstResult", properties);
  }

  private static Neo4jRelationship link(AstIdentity identity, String source, String target, String type, String field, int position) {
    var key = StableId.sha256("ast-field", identity.astResultKey(), source, target, type, field, Integer.toString(position));
    var properties = scoped(identity, key);
    properties.put("field", field);
    properties.put("position", (long) position);
    return new Neo4jRelationship(key, type, source, target, properties);
  }

  private static Map<String, Object> scoped(AstIdentity identity, String key) {
    var properties = new LinkedHashMap<String, Object>();
    properties.put("astResultKey", identity.astResultKey());
    properties.put("key", key);
    return properties;
  }

  private static String valueKey(AstIdentity identity, int ordinal) {
    return StableId.sha256("ast-value", identity.astResultKey(), Integer.toString(ordinal));
  }

  private static <T> Map<String, T> byKey(List<T> values, Function<T, String> key) {
    var indexed = new HashMap<String, T>();
    values.forEach(value -> indexed.put(key.apply(value), value));
    return indexed;
  }
}
