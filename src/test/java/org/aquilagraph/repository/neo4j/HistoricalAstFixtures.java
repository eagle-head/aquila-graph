package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AstOutcome;

final class HistoricalAstFixtures {
  private HistoricalAstFixtures() {}

  static AstResult read(int format) throws IOException {
    var prefix = "/retained-ast/java-source-" + format + "/";
    var metadata = new Properties();
    try (var stream = HistoricalAstFixtures.class.getResourceAsStream(prefix + "identity.properties")) {
      if (stream == null) { throw new IOException("Missing historical fixture metadata: " + prefix); }
      metadata.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    var identity = AstIdentity.fromContent(metadata.getProperty("softwareSignature"), metadata.getProperty("format"),
        metadata.getProperty("profile"), metadata.getProperty("semanticRevision"), metadata.getProperty("digest"));
    assertEquals(metadata.getProperty("key"), identity.astResultKey());
    byte[] bytes;
    try (var stream = HistoricalAstFixtures.class.getResourceAsStream(prefix + "source.ast.gz")) {
      if (stream == null) { throw new IOException("Missing historical fixture payload: " + prefix); }
      try (var gzip = new GZIPInputStream(stream)) { bytes = gzip.readAllBytes(); }
    }
    var result = AstCodec.decode(bytes, identity);
    assertEquals(AstCompletion.COMPLETE, result.completion());
    assertEquals("java-source/" + format, result.identity().sourceModelVersion());
    assertEquals(identity, result.identity());
    assertArrayEquals(bytes, AstCodec.encode(result));
    List<String> expected;
    try (var stream = HistoricalAstFixtures.class.getResourceAsStream(prefix + "database.tsv.gz")) {
      if (stream == null) { throw new IOException("Missing historical materialization: " + prefix); }
      try (var gzip = new GZIPInputStream(stream)) { expected = new String(gzip.readAllBytes(), StandardCharsets.UTF_8).lines().toList(); }
    }
    var projection = new Neo4jAstResultCodec().map(AstOutcome.available(result));
    var actual = new ArrayList<String>();
    for (var node : projection.nodes()) {
      actual.add("N\t" + node.key() + "\t" + node.label() + properties(node.properties()));
    }
    for (var link : projection.relationships()) {
      actual.add("R\t" + link.key() + "\t" + link.type() + "\t" + link.sourceKey() + "\t" + link.targetKey() + properties(link.properties()));
    }
    assertIterableEquals(expected, actual);
    return result;
  }

  private static String properties(Map<String, Object> values) {
    var text = new StringBuilder();
    for (var key : values.keySet().stream().sorted().toList()) {
      var value = values.get(key);
      if (!(value instanceof String) && !(value instanceof Long)) { throw new IllegalArgumentException("Unexpected historical property type"); }
      text.append('\t').append(key).append('=').append(value instanceof Long ? "L:" + value
          : "S:" + Base64.getEncoder().encodeToString(((String) value).getBytes(StandardCharsets.UTF_8)));
    }
    return text.toString();
  }
}
