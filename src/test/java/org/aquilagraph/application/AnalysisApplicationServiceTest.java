package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisApplicationServiceTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenCompletedSourceAndNoRepositoryRequest_whenAnalysisExecutes_thenRepositoryIsNotOpened()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example {}");
    var repositoryOpened = new AtomicBoolean();
    var service =
        service(
            () -> {
              repositoryOpened.set(true);
              throw new AssertionError("Repository must not be opened");
            });

    // Act
    var execution =
        service.execute(new AnalysisRequest(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
            RepositorySavePolicy.DO_NOT_SAVE));

    // Assert
    assertTrue(execution.run().isComplete());
    assertEquals(RepositoryStatus.NOT_REQUESTED, execution.repositoryStatus());
    assertFalse(repositoryOpened.get());
  }

  @Test
  void givenRejectedSourceAndRepositoryRequest_whenAnalysisExecutes_thenRepositoryIsNotOpened()
      throws IOException {
    // Arrange
    var repositoryOpened = new AtomicBoolean();
    var service =
        service(
            () -> {
              repositoryOpened.set(true);
              throw new AssertionError("Repository must not be opened");
            });

    // Act
    var execution =
        service.execute(new AnalysisRequest(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
            RepositorySavePolicy.SAVE_COMPLETED));

    // Assert
    assertFalse(execution.run().isComplete());
    assertEquals(RepositoryStatus.NOT_ELIGIBLE, execution.repositoryStatus());
    assertFalse(repositoryOpened.get());
  }

  @Test
  void givenCompletedSourceAndRepositoryRequest_whenAnalysisExecutes_thenCompletedRunIsSaved()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example {}");
    var persistedRun = new AtomicReference<AnalysisRun>();
    var repositoryClosed = new AtomicBoolean();
    var service =
        service(
            () ->
                new AnalysisRunRepository() {
                  @Override
                  public void save(AnalysisRun run) {
                    persistedRun.set(run);
                  }

                  @Override
                  public void close() {
                    repositoryClosed.set(true);
                  }
                });

    // Act
    var execution =
        service.execute(new AnalysisRequest(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
            RepositorySavePolicy.SAVE_COMPLETED));

    // Assert
    assertTrue(execution.run().isComplete());
    assertEquals(RepositoryStatus.SAVED, execution.repositoryStatus());
    assertSame(execution.run(), persistedRun.get());
    assertTrue(repositoryClosed.get());
  }

  @Test
  void givenRepositoryFailure_whenAnalysisExecutes_thenFailureRetainsCompletedRunAndCause()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example {}");
    var repositoryClosed = new AtomicBoolean();
    var service =
        service(
            () ->
                new AnalysisRunRepository() {
                  @Override
                  public void save(AnalysisRun run) {
                    throw new RepositoryException("database unavailable", new IllegalStateException("connection refused"));
                  }

                  @Override
                  public void close() {
                    repositoryClosed.set(true);
                  }
                });

    // Act
    var exception =
        assertThrows(
            AnalysisRepositoryException.class,
            () -> service.execute(new AnalysisRequest(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
                RepositorySavePolicy.SAVE_COMPLETED)));

    // Assert
    assertTrue(exception.analysisRun().isComplete());
    assertEquals("database unavailable", exception.getCause().getMessage());
    assertTrue(repositoryClosed.get());
  }

  private static AnalysisApplicationService service(AnalysisRunRepositoryFactory repositoryFactory) {
    return new AnalysisApplicationService(
        new JavaFrontend(new JavaSourceLoader()), new AnalysisEngine(), repositoryFactory);
  }
}
