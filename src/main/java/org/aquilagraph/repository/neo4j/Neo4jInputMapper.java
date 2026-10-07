package org.aquilagraph.repository.neo4j;

import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.StableId;

/** Maps captured analysis input into a Neo4j projection. */
final class Neo4jInputMapper {
  void map(String runId, InputSnapshot snapshot, Neo4jProjectionBuilder projection) {
    var snapshotKey = StableId.sha256("input-snapshot", snapshot.snapshotDigest());
    projection.node("InputSnapshot", snapshotKey, "sourceRoot", snapshot.sourceRoot().toString(), "digestAlgorithm",
        snapshot.digestAlgorithm(), "snapshotDigest", snapshot.snapshotDigest());
    projection.relate("HAS_INPUT", runId, snapshotKey, "input");
    for (var sourceFile : snapshot.sourceFiles()) {
      projection.node("SourceFile", sourceFile.sourceFileKey(), "relativePath", sourceFile.relativePath().toString().replace('\\', '/'),
          "contentDigest", sourceFile.contentDigest(), "content", sourceFile.content());
      projection.relate("HAS_SOURCE", snapshotKey, sourceFile.sourceFileKey(), sourceFile.relativePath().toString());
    }
  }
}
