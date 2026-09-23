package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
  void givenDiagnosedSourceAndNoRepositoryRequest_whenAnalysisExecutes_thenRunIsNotEligible()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example { void run() { while (true) { } } }");
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
    assertFalse(execution.run().isComplete());
    assertEquals(RepositoryStatus.NOT_ELIGIBLE, execution.repositoryStatus());
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
  void givenDiagnosedSourceAndRepositoryRequest_whenAnalysisExecutes_thenRepositoryIsNotOpened()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example { void run() { while (true) { } } }");
    var persistedRun = new AtomicReference<AnalysisRun>();
    var repositoryOpened = new AtomicBoolean();
    var repositoryClosed = new AtomicBoolean();
    var service =
        service(
            () -> {
              repositoryOpened.set(true);
              return
                new AnalysisRunRepository() {
                  @Override
                  public void save(AnalysisRun run) {
                    persistedRun.set(run);
                  }

                  @Override
                  public void close() {
                    repositoryClosed.set(true);
                  }
                };
            });

    // Act
    var execution =
        service.execute(new AnalysisRequest(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
            RepositorySavePolicy.SAVE_COMPLETED));

    // Assert
    assertFalse(execution.run().isComplete());
    assertEquals(RepositoryStatus.NOT_ELIGIBLE, execution.repositoryStatus());
    assertNull(persistedRun.get());
    assertFalse(repositoryOpened.get());
    assertFalse(repositoryClosed.get());
  }

  @Test
  void givenCompletedRunAndNoRepositoryRequest_whenAnalysisExecutes_thenRepositoryIsNotOpened() {
    // Arrange
    var requestConfiguration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var completedRun = TestAnalysisRuns.completed(temporaryDirectory, requestConfiguration);
    var repositoryOpened = new AtomicBoolean();
    var service = new AnalysisApplicationService((sourceRoot, configuration) -> completedRun, () -> {
      repositoryOpened.set(true);
      throw new AssertionError("Repository must not be opened");
    });

    // Act
    var execution = service.execute(new AnalysisRequest(temporaryDirectory, requestConfiguration, RepositorySavePolicy.DO_NOT_SAVE));

    // Assert
    assertSame(completedRun, execution.run());
    assertEquals(RepositoryStatus.NOT_REQUESTED, execution.repositoryStatus());
    assertFalse(repositoryOpened.get());
  }

  @Test
  void givenCompletedRunAndRepositoryRequest_whenAnalysisExecutes_thenRunIsSavedAndRepositoryIsClosed() {
    // Arrange
    var requestConfiguration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var completedRun = TestAnalysisRuns.completed(temporaryDirectory, requestConfiguration);
    var persistedRun = new AtomicReference<AnalysisRun>();
    var repositoryClosed = new AtomicBoolean();
    var service = new AnalysisApplicationService((sourceRoot, configuration) -> completedRun,
        () -> repository(persistedRun, repositoryClosed));

    // Act
    var execution = service.execute(new AnalysisRequest(temporaryDirectory, requestConfiguration, RepositorySavePolicy.SAVE_COMPLETED));

    // Assert
    assertSame(completedRun, execution.run());
    assertSame(completedRun, persistedRun.get());
    assertEquals(RepositoryStatus.SAVED, execution.repositoryStatus());
    assertTrue(repositoryClosed.get());
  }

  @Test
  void givenCompletedRunAndFailingRepository_whenAnalysisExecutes_thenFailureRetainsRunAndClosesRepository() {
    // Arrange
    var requestConfiguration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var completedRun = TestAnalysisRuns.completed(temporaryDirectory, requestConfiguration);
    var repositoryFailure = new RepositoryException("database unavailable", new IllegalStateException("connection refused"));
    var repositoryClosed = new AtomicBoolean();
    var service = new AnalysisApplicationService((sourceRoot, configuration) -> completedRun, () -> new AnalysisRunRepository() {
      @Override
      public void save(AnalysisRun run) {
        throw repositoryFailure;
      }

      @Override
      public void close() {
        repositoryClosed.set(true);
      }
    });

    // Act
    var exception = assertThrows(AnalysisRepositoryException.class,
        () -> service.execute(new AnalysisRequest(temporaryDirectory, requestConfiguration, RepositorySavePolicy.SAVE_COMPLETED)));

    // Assert
    assertSame(completedRun, exception.analysisRun());
    assertSame(repositoryFailure, exception.repositoryFailure());
    assertTrue(repositoryClosed.get());
  }

  private static AnalysisRunRepository repository(AtomicReference<AnalysisRun> persistedRun, AtomicBoolean repositoryClosed) {
    return new AnalysisRunRepository() {
      @Override
      public void save(AnalysisRun run) {
        persistedRun.set(run);
      }

      @Override
      public void close() {
        repositoryClosed.set(true);
      }
    };
  }

  private static AnalysisApplicationService service(AnalysisRunRepositoryFactory repositoryFactory) {
    var analysisRunner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), new AnalysisEngine());
    return new AnalysisApplicationService(analysisRunner, repositoryFactory);
  }
}
