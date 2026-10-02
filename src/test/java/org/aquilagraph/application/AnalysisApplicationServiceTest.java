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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisApplicationServiceTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenEveryCompletionAndSaveIntent_whenRequestExecutes_thenAllSixEligibilityRowsArePreserved() {
    // Arrange
    var rows = List.of(
        new EligibilityRow(AnalysisCompletion.REJECTED, RepositorySavePolicy.DO_NOT_SAVE, RepositoryStatus.NOT_ELIGIBLE, 0),
        new EligibilityRow(AnalysisCompletion.REJECTED, RepositorySavePolicy.SAVE_COMPLETED, RepositoryStatus.NOT_ELIGIBLE, 0),
        new EligibilityRow(AnalysisCompletion.INCOMPLETE, RepositorySavePolicy.DO_NOT_SAVE, RepositoryStatus.NOT_ELIGIBLE, 0),
        new EligibilityRow(AnalysisCompletion.INCOMPLETE, RepositorySavePolicy.SAVE_COMPLETED, RepositoryStatus.NOT_ELIGIBLE, 0),
        new EligibilityRow(AnalysisCompletion.COMPLETED, RepositorySavePolicy.DO_NOT_SAVE, RepositoryStatus.NOT_REQUESTED, 0),
        new EligibilityRow(AnalysisCompletion.COMPLETED, RepositorySavePolicy.SAVE_COMPLETED, RepositoryStatus.SAVED, 1));
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var completed = TestAnalysisRuns.completed(temporaryDirectory, configuration);
    for (var row : rows) {
      var run = new AnalysisRun(completed.runId(), completed.inputSnapshot(), completed.environmentSnapshot(),
          row.completion() == AnalysisCompletion.REJECTED ? Optional.empty() : completed.representation(), List.of(), row.completion());
      var opens = new AtomicInteger();
      var saves = new AtomicInteger();
      var closes = new AtomicInteger();
      var service = new AnalysisApplicationService((sourceRoot, requestedConfiguration) -> run, () -> {
        opens.incrementAndGet();
        return new AnalysisRunRepository() {
          @Override
          public void save(AnalysisRun supplied) {
            assertSame(run, supplied);
            saves.incrementAndGet();
          }

          @Override
          public void close() {
            closes.incrementAndGet();
          }
        };
      });

      // Act
      var execution = service.execute(new AnalysisRequest(temporaryDirectory, configuration, row.policy()));

      // Assert
      assertSame(run, execution.run());
      assertEquals(row.policy(), execution.repositorySavePolicy());
      assertEquals(row.status(), execution.repositoryStatus());
      assertEquals(row.attempts(), opens.get());
      assertEquals(row.attempts(), saves.get());
      assertEquals(row.attempts(), closes.get());
    }
  }

  @Test
  void givenRepositoryOpeningFailure_whenRequestExecutes_thenCompletedRunAndEffectiveFailureAreRetained() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var run = TestAnalysisRuns.completed(temporaryDirectory, configuration);
    var primary = new RepositoryException("Opening failed", new IllegalStateException("No repository returned"));
    var service = new AnalysisApplicationService((sourceRoot, requestedConfiguration) -> run, () -> { throw primary; });

    // Act
    var failure = assertThrows(AnalysisRepositoryException.class,
        () -> service.execute(new AnalysisRequest(temporaryDirectory, configuration, RepositorySavePolicy.SAVE_COMPLETED)));

    // Assert
    assertSame(run, failure.analysisRun());
    assertSame(primary, failure.repositoryFailure());
    assertEquals(0, primary.getSuppressed().length);
  }

  @Test
  void givenSuccessfulSaveAndFailedClose_whenRequestExecutes_thenNoSavedExecutionReturnsAndRunIsRetained() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var run = TestAnalysisRuns.completed(temporaryDirectory, configuration);
    var saved = new AtomicReference<AnalysisRun>();
    var primary = new RepositoryException("Close failed", new IllegalStateException("Driver close failed"));
    var service = new AnalysisApplicationService((sourceRoot, requestedConfiguration) -> run, () -> new AnalysisRunRepository() {
      @Override
      public void save(AnalysisRun supplied) {
        saved.set(supplied);
      }

      @Override
      public void close() {
        throw primary;
      }
    });

    // Act
    var failure = assertThrows(AnalysisRepositoryException.class,
        () -> service.execute(new AnalysisRequest(temporaryDirectory, configuration, RepositorySavePolicy.SAVE_COMPLETED)));

    // Assert
    assertSame(run, saved.get());
    assertSame(run, failure.analysisRun());
    assertSame(primary, failure.repositoryFailure());
    assertEquals(AnalysisCompletion.COMPLETED, failure.analysisRun().completion());
  }

  @Test
  void givenUnmatchedSavePrimaryAndRepositoryCleanupFailure_whenRequestExecutes_thenPrimaryFamilyAndSuppressionArePreserved() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var run = TestAnalysisRuns.completed(temporaryDirectory, configuration);
    var primary = new IllegalStateException("Mapping failed");
    var cleanup = new RepositoryException("Close failed", new IllegalStateException("Driver close failed"));
    var service = failingService(run, primary, cleanup);

    // Act
    var failure = assertThrows(IllegalStateException.class,
        () -> service.execute(new AnalysisRequest(temporaryDirectory, configuration, RepositorySavePolicy.SAVE_COMPLETED)));

    // Assert
    assertSame(primary, failure);
    assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));
  }

  @Test
  void givenRepositorySavePrimaryAndUnmatchedCleanupFailure_whenRequestExecutes_thenRepositoryPrimaryRetainsSuppression() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var run = TestAnalysisRuns.completed(temporaryDirectory, configuration);
    var primary = new RepositoryException("Save failed", new IllegalStateException("Write failed"));
    var cleanup = new IllegalStateException("Close failed");
    var service = failingService(run, primary, cleanup);

    // Act
    var failure = assertThrows(AnalysisRepositoryException.class,
        () -> service.execute(new AnalysisRequest(temporaryDirectory, configuration, RepositorySavePolicy.SAVE_COMPLETED)));

    // Assert
    assertSame(primary, failure.repositoryFailure());
    assertSame(run, failure.analysisRun());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
  }

  private static AnalysisApplicationService failingService(AnalysisRun run, RuntimeException primary, RuntimeException cleanup) {
    return new AnalysisApplicationService((sourceRoot, requestedConfiguration) -> run, () -> new AnalysisRunRepository() {
      @Override
      public void save(AnalysisRun supplied) {
        throw primary;
      }

      @Override
      public void close() {
        throw cleanup;
      }
    });
  }

  private record EligibilityRow(AnalysisCompletion completion, RepositorySavePolicy policy, RepositoryStatus status, int attempts) {
  }

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
