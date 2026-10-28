package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompleteValidationTest {
  @TempDir
  Path directory;

  @Test
  void givenOlderCoverageAndReports_whenTheCompleteCandidateIsPrepared_thenOnlyCurrentRunEvidenceCanRemain() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    Files.createDirectories(root.resolve("target/site/jacoco"));
    Files.createDirectories(root.resolve("target/surefire-reports"));
    Files.writeString(root.resolve("target/jacoco.exec"), "old execution data");
    Files.writeString(root.resolve("target/site/jacoco/index.html"), "old report");
    Files.writeString(root.resolve("target/surefire-reports/TEST-old.xml"), "old test report");
    var request = new BuildRequest(root, root, root.resolve("mvnw"), BuildOperation.VERIFY_ALL, List.of());
    try (var lease = BuildLease.acquire(request)) {
      // Act
      CompleteValidation.prepare(lease.context());
      var result = BuildFiles.read(CompleteValidation.directory(lease.context()).resolve("result.properties"));

      // Assert
      assertFalse(Files.exists(root.resolve("target/jacoco.exec")));
      assertFalse(Files.exists(root.resolve("target/site/jacoco/index.html")));
      assertFalse(Files.exists(root.resolve("target/surefire-reports/TEST-old.xml")));
      assertEquals("RUNNING", result.getProperty("status"));
      assertEquals("false", result.getProperty("complete.validation"));
      assertEquals(lease.context().nonce(), result.getProperty("nonce"));
    }
  }

  @Test
  void givenChangedCandidateInputs_whenCompletionIsVerified_thenTheOriginalFailureAndCandidateRemainExplicit() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var source = Files.createDirectories(root.resolve("src/main/java")).resolve("Example.java");
    Files.writeString(source, "final class Example { }\n");
    var request = new BuildRequest(root, root, root.resolve("mvnw"), BuildOperation.VERIFY_ALL, List.of());
    try (var lease = BuildLease.acquire(request)) {
      var complete = CompleteValidation.prepare(lease.context());
      var original = BuildFiles.read(CompleteValidation.directory(lease.context()).resolve("result.properties"));
      Files.writeString(source, "final class Different { }\n");

      // Act
      var failure = assertThrows(IOException.class, complete::verify);
      complete.record("FAILED", failure.toString(), 1);
      var result = BuildFiles.read(CompleteValidation.directory(lease.context()).resolve("result.properties"));

      // Assert
      assertTrue(failure.getMessage().contains("candidate inputs changed"));
      assertEquals("FAILED", result.getProperty("status"));
      assertEquals("false", result.getProperty("complete.validation"));
      assertEquals(original.getProperty("candidate"), result.getProperty("candidate"));
      assertEquals(failure.toString(), result.getProperty("diagnostic"));
      assertEquals("false", result.getProperty("resource.closure.verified"));
      var resources = BuildFiles.read(lease.context().directory().resolve("complete-resources.properties"));
      assertEquals("true", resources.getProperty("registered.databases.closed"));
      assertEquals("false", resources.getProperty("integration.completion.verified"));
    }
  }
}
