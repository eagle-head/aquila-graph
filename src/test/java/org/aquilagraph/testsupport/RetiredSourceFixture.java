package org.aquilagraph.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import org.aquilagraph.identity.api.AstIdentity;

/** An original retained identity and payload used to observe unsupported-format refusal. */
public record RetiredSourceFixture(AstIdentity identity, byte[] payload) {
  public static RetiredSourceFixture read(String resource) throws IOException {
    var metadata = new Properties();
    try (var stream = RetiredSourceFixture.class.getResourceAsStream(resource + "/identity.properties")) {
      if (stream == null) {
        throw new IOException("Missing retired source metadata: " + resource);
      }
      metadata.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    var identity = AstIdentity.fromContent(metadata.getProperty("softwareSignature"), metadata.getProperty("format"),
        metadata.getProperty("profile"), metadata.getProperty("semanticRevision"), metadata.getProperty("digest"));
    assertEquals(metadata.getProperty("key"), identity.astResultKey());
    try (var stream = RetiredSourceFixture.class.getResourceAsStream(resource + "/source.ast.gz")) {
      if (stream == null) {
        throw new IOException("Missing retired source payload: " + resource);
      }
      try (var gzip = new GZIPInputStream(stream)) {
        return new RetiredSourceFixture(identity, gzip.readAllBytes());
      }
    }
  }
}
