package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreparedConsumerTest {
  @TempDir Path directory;

  @Test
  void givenConsumerPreparation_whenCompilerArgumentsAreCreated_thenAllWarningsAreErrors() throws Exception {
    // Arrange
    var root = directory.toRealPath();

    // Act
    var request = ConsumerPreparation.request(root, root.resolve("annotations.jar"), false);

    // Assert
    assertTrue(request.arguments().contains("-Xlint:all"));
    assertTrue(request.arguments().contains("-Werror"));
  }

  @Test
  void givenMissingOrForeignReceipt_whenTheCallerRequestsAnArtifact_thenNoAmbientArtifactIsSelected() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var invocation = UUID.randomUUID().toString();
    var receipt = PreparedConsumer.receiptPath(root, invocation);

    // Act
    var absent = assertThrows(IOException.class, () -> PreparedConsumer.open(root, receipt, invocation));

    // Assert
    assertTrue(absent.getMessage().contains(invocation));
    assertThrows(IOException.class, () -> PreparedConsumer.open(root, receipt, UUID.randomUUID().toString()));
    assertThrows(IOException.class, () -> PreparedConsumer.receiptPath(root, "../foreign"));
  }

  @Test
  void givenCompilerInputManifest_whenSourceOrToolchainChanges_thenTheQueryHandoffRejectsStaleInputs() throws Exception {
    // Arrange
    var source = directory.resolve("Subject.java");
    Files.writeString(source, "class Subject {}");
    var manifest = new Properties();
    manifest.setProperty("schema", "compilation-inputs/1");
    manifest.setProperty("batch", "consumers");
    manifest.setProperty("input." + source, PreparationFiles.digest(source));
    manifest.setProperty("jdk.home", System.getProperty("java.home"));
    manifest.setProperty("jdk.version", Runtime.version().toString());
    manifest.setProperty("environment.digest", "producer compiler environment");

    // Act
    CompilationInputs.requireCurrentInputs(manifest);
    Files.writeString(source, "class Subject { int value; }");
    var changed = assertThrows(IOException.class, () -> CompilationInputs.requireCurrentInputs(manifest));

    // Assert
    assertTrue(changed.getMessage().contains("input changed"));
    manifest.setProperty("input." + source, PreparationFiles.digest(source));
    manifest.setProperty("jdk.version", "different compilation runtime");
    assertTrue(assertThrows(IOException.class, () -> CompilationInputs.requireCurrentInputs(manifest)).getMessage().contains("toolchain"));
  }

  @Test
  void givenConsumerPreparationRequest_whenInputIdentityChanges_thenArtifactsHaveDistinctOwnedLocations() throws Exception {
    // Arrange
    var root = directory.toRealPath();
    var annotations = directory.resolve("annotations.jar");
    var request = ConsumerPreparation.request(root, annotations, false);

    // Act
    var first = request.publishedOutput("first-input");
    var second = request.publishedOutput("second-input");

    // Assert
    assertNotEquals(first, second);
    assertEquals(root.resolve("target/prepared-consumers/first-input"), first);
    assertTrue(request.arguments().contains(annotations.toString()) || request.arguments().stream().anyMatch(value -> value.endsWith(annotations.toString())));
    assertThrows(IOException.class, () -> ConsumerPreparation.stage(request));
  }
}
