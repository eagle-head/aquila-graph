package org.aquilagraph.repository.internal.neo4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.application.AstLookup;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.jspecify.annotations.Nullable;

/** Interprets detached stored AST facts and delegates intrinsic admission to the source codec. */
final class Neo4jStoredAstAdmission {
  private static final String ENTITY_LABEL = "AstEntity";
  private static final String INVALID_ENDPOINT = "AST storage relationships require endpoints in their exact owned namespace";
  private final Neo4jAstResultCodec codec;

  Neo4jStoredAstAdmission(Neo4jAstResultCodec codec) {
    this.codec = Objects.requireNonNull(codec, "codec");
  }

  static @Nullable String formatVersion(Map<String, Object> root) {
    return textProperty(root, "formatVersion");
  }

  static @Nullable String sourceProfile(Map<String, Object> root) {
    return textProperty(root, "astProfileVersion");
  }

  private static @Nullable String textProperty(Map<String, Object> root, String key) {
    // Missing and non-text values both leave the admission phase without a textual property.
    return root.get(key) instanceof String text ? text : null;
  }

  static boolean requiresMaterialization(@Nullable String format) {
    return format != null && AstResult.supportsSourceModelVersion(format);
  }

  AstLookup admit(AstIdentity identity, StoredRead stored) {
    return switch (stored) {
      case MissingRoot missing -> admitMissingRoot(identity, missing);
      case UnmaterializedRoot root -> admitUnmaterializedRoot(identity, root.format());
      case MaterializedRoot root -> admitMaterializedRoot(identity, root);
    };
  }

  private static AstLookup admitMissingRoot(AstIdentity identity, MissingRoot missing) {
    if (missing.hasScopedEntities()) {
      throw new IllegalArgumentException("Stored AST entities have no exact result root");
    }
    return new AstLookup(identity, AstLookupStatus.NOT_FOUND, Optional.empty(), Optional.empty());
  }

  private static AstLookup admitUnmaterializedRoot(AstIdentity identity, @Nullable String format) {
    if (format == null || format.isBlank()) {
      throw new IllegalArgumentException("Stored AST result requires an explicit format version");
    }
    return incompatible(identity, format);
  }

  private AstLookup admitMaterializedRoot(AstIdentity identity, MaterializedRoot root) {
    var profile = root.profile();
    if (profile != null) {
      if (!profile.equals(identity.astProfileVersion())) {
        throw new IllegalArgumentException("Stored AST source profile differs from the exact requested identity");
      }
      if (!new AstProfile(profile).isSupported()) {
        return incompatible(identity, profile);
      }
    }
    var rows = root.rows().orElseThrow(root.decodingFailure()::orElseThrow);
    var outcome = codec.decode(new Neo4jAstProjection(identity, rows.nodes(), rows.relationships()), identity);
    return new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(outcome), Optional.empty());
  }

  private static AstLookup incompatible(AstIdentity identity, String version) {
    return new AstLookup(identity, AstLookupStatus.INCOMPATIBLE, Optional.empty(), Optional.of(version));
  }

  static String nodeLabel(List<String> labels) {
    if (labels.size() != 2 || !labels.contains(ENTITY_LABEL)) {
      throw new IllegalArgumentException("AST storage requires its exact namespace and one closed domain label");
    }
    return labels.stream().filter(label -> !ENTITY_LABEL.equals(label)).findFirst().orElseThrow();
  }

  static void requireEndpointNamespace(List<String> labels) {
    if (!labels.contains(ENTITY_LABEL)) {
      throw new IllegalArgumentException(INVALID_ENDPOINT);
    }
  }

  static void requireEndpointScope(AstIdentity identity, @Nullable Object scope) {
    if (!identity.astResultKey().equals(scope)) {
      throw new IllegalArgumentException(INVALID_ENDPOINT);
    }
  }

  sealed interface StoredRead permits MissingRoot, UnmaterializedRoot, MaterializedRoot {
  }

  record MissingRoot(boolean hasScopedEntities) implements StoredRead {
  }

  record UnmaterializedRoot(@Nullable String format) implements StoredRead {
  }

  record MaterializedRoot(@Nullable String profile, Optional<MaterializedRows> rows,
      Optional<RuntimeException> decodingFailure) implements StoredRead {
    MaterializedRoot {
      if (rows.isPresent() == decodingFailure.isPresent()) {
        throw new IllegalArgumentException("AST materialization requires complete rows or a deferred failure");
      }
    }
  }

  record MaterializedRows(List<Neo4jNode> nodes, List<Neo4jRelationship> relationships) {
    MaterializedRows {
      nodes = List.copyOf(nodes);
      relationships = List.copyOf(relationships);
    }
  }
}
