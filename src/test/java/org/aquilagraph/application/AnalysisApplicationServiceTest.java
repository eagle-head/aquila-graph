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
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisApplicationServiceTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenCompletedSourceAndNoPersistenceRequest_whenAnalysisExecutes_thenRepositoryIsNotOpened()
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
    var execution = service.execute(AnalysisRequest.java25(temporaryDirectory, false));

    // Assert
    assertTrue(execution.run().isComplete());
    assertEquals(PersistenceStatus.NOT_REQUESTED, execution.persistenceStatus());
    assertTrue(execution.persistenceReceipt().isEmpty());
    assertFalse(repositoryOpened.get());
  }

  @Test
  void givenRejectedSourceAndPersistenceRequest_whenAnalysisExecutes_thenRepositoryIsNotOpened()
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
    var execution = service.execute(AnalysisRequest.java25(temporaryDirectory, true));

    // Assert
    assertFalse(execution.run().isComplete());
    assertEquals(PersistenceStatus.NOT_ATTEMPTED, execution.persistenceStatus());
    assertTrue(execution.persistenceReceipt().isEmpty());
    assertFalse(repositoryOpened.get());
  }

  @Test
  void givenCompletedSourceAndPersistenceRequest_whenAnalysisExecutes_thenCompletedRunIsPersisted()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example {}");
    var persistedRun = new AtomicReference<AnalysisRun>();
    var repositoryClosed = new AtomicBoolean();
    var service =
        service(
            () ->
                new AnalysisRepository() {
                  @Override
                  public PersistenceReceipt persist(AnalysisRun run) {
                    persistedRun.set(run);
                    return new PersistenceReceipt(run.runId(), 7, 11);
                  }

                  @Override
                  public void close() {
                    repositoryClosed.set(true);
                  }
                });

    // Act
    var execution = service.execute(AnalysisRequest.java25(temporaryDirectory, true));

    // Assert
    assertTrue(execution.run().isComplete());
    assertEquals(PersistenceStatus.COMPLETED, execution.persistenceStatus());
    assertEquals(7, execution.persistenceReceipt().orElseThrow().nodeCount());
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
                new AnalysisRepository() {
                  @Override
                  public PersistenceReceipt persist(AnalysisRun run) {
                    throw new IllegalStateException("database unavailable");
                  }

                  @Override
                  public void close() {
                    repositoryClosed.set(true);
                  }
                });

    // Act
    var exception =
        assertThrows(
            AnalysisPersistenceException.class,
            () -> service.execute(AnalysisRequest.java25(temporaryDirectory, true)));

    // Assert
    assertTrue(exception.analysisRun().isComplete());
    assertEquals("database unavailable", exception.getCause().getMessage());
    assertTrue(repositoryClosed.get());
  }

  private static AnalysisApplicationService service(AnalysisRepositoryFactory repositoryFactory) {
    return new AnalysisApplicationService(
        new JavaFrontend(new JavaSourceLoader()), new AnalysisEngine(), repositoryFactory);
  }
}
