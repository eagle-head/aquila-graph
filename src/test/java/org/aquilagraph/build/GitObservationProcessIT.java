package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("process")
class GitObservationProcessIT {
  @TempDir
  Path directory;

  @Test
  void givenGitObservation_whenTheOwnerCloses_thenOutputIsReplaceableAndTheJournalContainsOnlyOwnership() throws Exception {
    // Arrange
    String observed;

    // Act
    try (var lease = BuildLease.acquire(request()); var processes = new ManagedProcesses(lease, Optional.empty())) {
      observed = processes.git(List.of("--version"), 0);
    }

    // Assert
    assertTrue(observed.startsWith("git version "));
    try (var files = Files.walk(directory.resolve(".build-state"))) {
      assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("git-")));
    }
    try (var files = Files.walk(directory.resolve("target/build-observation"))) {
      var outputs = files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().startsWith("git-")).toList();
      assertEquals(2, outputs.size());
      for (var file : outputs) {
        assertEquals(observed, Files.readString(file));
      }
    }
  }

  private BuildRequest request() throws IOException {
    var root = directory.toRealPath();
    return new BuildRequest(root, Path.of("").toRealPath(), root.resolve("mvnw"), BuildOperation.COMPILE, List.of());
  }
}
