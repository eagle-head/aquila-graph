package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.aquilagraph.testsupport.RetiredSourceFixture;

final class HistoricalAstFixtures {
  private HistoricalAstFixtures() {}

  static RetiredSourceFixture read(int version) throws IOException {
    var fixture = RetiredSourceFixture.read("/retained-ast/java-source-" + version);
    assertEquals("java-source/" + version, fixture.identity().sourceModelVersion());
    return fixture;
  }

  static Neo4jAstProjection storedRoot(RetiredSourceFixture fixture) {
    var identity = fixture.identity();
    var key = identity.astResultKey();
    var root = new Neo4jNode(key, "AstResult", Map.of("key", key, "astResultKey", key,
        "formatVersion", identity.sourceModelVersion(), "sourceModelVersion", identity.sourceModelVersion(),
        "astProfileVersion", identity.astProfileVersion(), "softwareSignature", identity.softwareSignature(),
        "semanticProductionRevision", identity.semanticProductionRevision(), "canonicalContentDigest", identity.canonicalContentDigest(),
        "payload", Base64.getEncoder().encodeToString(fixture.payload())));
    return new Neo4jAstProjection(identity, List.of(root), List.of());
  }
}
