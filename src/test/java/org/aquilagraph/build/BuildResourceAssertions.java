package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

/** Shared observations of durable invocation closure and exact process absence. */
final class BuildResourceAssertions {
  private BuildResourceAssertions() {
  }

  static void assertClosed(Path root) throws Exception {
    try (var files = Files.walk(root.resolve(".build-state/invocations"))) {
      for (var intent : files.filter(path -> path.getFileName().toString().equals("intent.properties")).toList()) {
        assertTrue(Files.exists(intent.getParent().resolve("closed.properties")), intent.toString());
        try (var identities = Files.newDirectoryStream(intent.getParent(), "process-*.properties")) {
          for (var file : identities) {
            var record = BuildFiles.read(file);
            assertTrue(ProcessIdentity.read(record, "").liveProcess().isEmpty(), file.toString());
          }
        }
      }
    }
  }
}
