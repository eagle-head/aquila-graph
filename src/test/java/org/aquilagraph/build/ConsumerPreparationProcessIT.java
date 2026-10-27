package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.module.FindException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("process")
class ConsumerPreparationProcessIT {
  @TempDir Path directory;

  @AfterEach
  void closeStorage() throws IOException {
    CompilationCleanup.clear(directory.toRealPath(), "clean");
  }

  @Test
  void givenPackagedInputs_whenPreparationIsRepeatedAndSourcesChange_thenReuseAndStaleReceiptRefusalAreObservable() throws Exception {
    // Arrange
    var request = fixture();
    try (var lease = lease()) {
      var invocation = lease.context().nonce();
      assertEquals(0, ConsumerPreparation.prepare(request, invocation));
      var receipt = PreparedConsumer.receiptPath(request.root(), invocation);
      var first = BuildFiles.read(receipt);
      var artifact = Path.of(first.getProperty("artifact"));
      var original = Files.readAllBytes(artifact.resolve("org/aquilagraph/retention/consumer/RetainedAstQueryProcess.class"));
      Files.writeString(request.state().resolve("compiler.log"), "compiler omitted on verified reuse");

      // Act
      var second = ConsumerPreparation.prepare(request, invocation);
      var reusedArtifact = BuildFiles.read(receipt).getProperty("artifact");
      var reusedLog = Files.readString(request.state().resolve("compiler.log"));
      var consumer = PreparedConsumer.open(request.root(), receipt, invocation);
      var source = request.root().resolve(ConsumerPreparation.SOURCES).resolve("RetainedAstQueryProcess.java");
      Files.writeString(source, Files.readString(source).replace("The query must run", "The prepared query must run"));
      assertThrows(IOException.class, () -> PreparedConsumer.open(request.root(), receipt, invocation));
      var changedSourceExit = ConsumerPreparation.prepare(request, invocation);
      var changed = Path.of(BuildFiles.read(receipt).getProperty("artifact"));
      var options = new ArrayList<>(request.arguments());
      options.set(options.indexOf("-g"), "-g:none");
      var changedOptions = new CompilationRequest(request.root(), request.batch(), request.sources(), request.resources(), request.output(),
          false, options, request.environment());
      var changedOptionsExit = ConsumerPreparation.prepare(changedOptions, invocation);

      // Assert
      assertEquals(0, second);
      assertEquals(first.getProperty("artifact"), reusedArtifact);
      assertEquals("compiler omitted on verified reuse", reusedLog);
      assertTrue(consumer.modulePath().contains(artifact.toString()));
      assertEquals(0, changedSourceExit);
      assertNotEquals(artifact, changed);
      assertFalse(Arrays.equals(original, Files.readAllBytes(changed.resolve("org/aquilagraph/retention/consumer/RetainedAstQueryProcess.class"))));
      assertArrayEquals(original, Files.readAllBytes(artifact.resolve("org/aquilagraph/retention/consumer/RetainedAstQueryProcess.class")));
      assertEquals(0, changedOptionsExit);
      assertNotEquals(changed, Path.of(BuildFiles.read(receipt).getProperty("artifact")));
    }
  }

  @Test
  void givenPreparedArtifact_whenDescriptorJarOrOutputChanges_thenEachChangeInvalidatesTheExplicitHandoff() throws Exception {
    // Arrange
    var request = fixture();
    try (var lease = lease()) {
      var invocation = lease.context().nonce();
      assertEquals(0, ConsumerPreparation.prepare(request, invocation));
      var receipt = PreparedConsumer.receiptPath(request.root(), invocation);
      var initial = BuildFiles.read(receipt).getProperty("input");
      var descriptor = request.root().resolve("config/consumer/module-info.java");

      // Act
      Files.writeString(descriptor, Files.readString(descriptor).replace("requires org.aquilagraph.source;", "requires java.logging; requires org.aquilagraph.source;"));

      assertThrows(IOException.class, () -> PreparedConsumer.open(request.root(), receipt, invocation));
      var descriptorExit = ConsumerPreparation.prepare(request, invocation);
      var beforeJar = BuildFiles.read(receipt).getProperty("input");
      Path jar;
      try (var jars = Files.list(request.root().resolve("target/aquila-graph/lib"))) {
        jar = jars.filter(path -> path.getFileName().toString().endsWith("-source.jar")).findFirst().orElseThrow();
      }
      try (var archive = FileSystems.newFileSystem(jar, Map.of())) {
        Files.writeString(archive.getPath("/preparation-control.txt"), "changed packaged content");
      }
      assertThrows(IOException.class, () -> PreparedConsumer.open(request.root(), receipt, invocation));
      var jarExit = ConsumerPreparation.prepare(request, invocation);
      var afterJar = BuildFiles.read(receipt).getProperty("input");
      var artifact = Path.of(BuildFiles.read(receipt).getProperty("artifact"));
      var compiled = artifact.resolve("module-info.class");
      PreparationFiles.clear(request.root(), artifact);
      assertThrows(IOException.class, () -> PreparedConsumer.open(request.root(), receipt, invocation));
      var repairExit = ConsumerPreparation.prepare(request, invocation);
      var saved = Files.readString(receipt);
      Files.writeString(receipt, "malformed receipt");
      assertThrows(IOException.class, () -> PreparedConsumer.open(request.root(), receipt, invocation));
      Files.writeString(receipt, saved);
      assertThrows(IOException.class, () -> PreparedConsumer.open(request.root(), receipt, UUID.randomUUID().toString()));
      var changedMetadata = BuildFiles.read(receipt);
      changedMetadata.setProperty("packages", "foreign package identity");
      BuildFiles.write(receipt, changedMetadata);
      assertThrows(IOException.class, () -> PreparedConsumer.open(request.root(), receipt, invocation));

      // Assert
      assertEquals(0, descriptorExit);
      assertNotEquals(initial, beforeJar);
      assertEquals(0, jarExit);
      assertNotEquals(beforeJar, afterJar);
      assertEquals(0, repairExit);
      assertTrue(Files.isRegularFile(compiled));
    }
  }

  @Test
  void givenCompilationOrPackageFailure_whenPreparationRuns_thenNoSuccessfulReceiptSurvives() throws Exception {
    // Arrange
    var request = fixture();
    try (var lease = lease()) {
      var invocation = lease.context().nonce();
      assertEquals(0, ConsumerPreparation.prepare(request, invocation));
      var receipt = PreparedConsumer.receiptPath(request.root(), invocation);
      Files.writeString(request.root().resolve(ConsumerPreparation.SOURCES).resolve("RetainedAstQueryProcess.java"), "invalid Java");

      // Act
      var exit = ConsumerPreparation.prepare(request, invocation);
      var receiptAfterCompilerFailure = Files.exists(receipt);
      var publicationAfterCompilerFailure = Files.exists(request.state().resolve("success.properties"));
      var lib = request.root().resolve("target/aquila-graph/lib");
      PreparationFiles.clear(request.root(), lib);
      assertThrows(IOException.class, () -> ConsumerPreparation.prepare(request, invocation));
      Files.writeString(lib.resolve("unusable.jar"), "invalid packaged input");
      assertThrows(FindException.class, () -> ConsumerPreparation.prepare(request, invocation));

      // Assert
      assertNotEquals(0, exit);
      assertFalse(receiptAfterCompilerFailure);
      assertFalse(publicationAfterCompilerFailure);
      assertFalse(Files.exists(receipt));
    }
  }

  private BuildLease lease() throws IOException {
    var root = directory.toRealPath();
    return BuildLease.acquire(new BuildRequest(root, Path.of("").toRealPath(), root.resolve("mvnw"), BuildOperation.PREPARE_CONSUMERS, List.of()));
  }

  private CompilationRequest fixture() throws Exception {
    var root = directory.toRealPath();
    var project = Path.of("").toRealPath();
    PreparationFiles.copy(project.resolve("config/consumer"), root.resolve("config/consumer"));
    PreparationFiles.copy(project.resolve(ConsumerPreparation.SOURCES), root.resolve(ConsumerPreparation.SOURCES));
    PreparationFiles.copy(project.resolve("target/aquila-graph/lib"), root.resolve("target/aquila-graph/lib"));
    Files.writeString(root.resolve("pom.xml"), "<project />");
    var annotations = Path.of(NullMarked.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    return ConsumerPreparation.request(root, annotations, false);
  }
}
