package org.aquilagraph.application;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.application.port.AnalysisUseCase;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisUseCaseTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenAbruptCfgFailure_whenExecutingEitherSaveIntent_thenTheOriginalFailurePrecedesRepositoryAcquisition() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(temporaryDirectory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "interface Example { static int value() { return 1; } }")));
    var source = new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);
    var request = new AnalysisComputationRequest(source, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)));
    var primary = new IllegalStateException("Unexpected CFG invocation failure");
    var computation = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), (ast, selection) -> {
      throw primary;
    });
    var execution = AnalysisCapabilities.execution(computation, AnalysisExecutionFixture::unexpectedAstRepository,
        AnalysisExecutionFixture::unexpectedRunRepository);

    // Act
    var astFailure = assertThrows(IllegalStateException.class,
        () -> execution.execute(new AnalysisRequest(request, PublicationIntent.SAVE_COMPLETE_AST)));
    var runFailure = assertThrows(IllegalStateException.class,
        () -> execution.execute(new AnalysisRequest(request, PublicationIntent.SAVE_COMPLETED)));

    // Assert
    assertSame(primary, astFailure);
    assertSame(primary, runFailure);
  }

  @Test
  void givenEveryCompletionAndSaveIntent_whenRequestExecutes_thenAllSixEligibilityRowsArePreserved() {
    // Arrange
    var rows = List.of(
        new EligibilityRow(AnalysisCompletion.REJECTED, PublicationIntent.DO_NOT_SAVE, PublicationStatus.NOT_REQUESTED, 0),
        new EligibilityRow(AnalysisCompletion.REJECTED, PublicationIntent.SAVE_COMPLETED, PublicationStatus.NOT_ELIGIBLE, 0),
        new EligibilityRow(AnalysisCompletion.PARTIAL, PublicationIntent.DO_NOT_SAVE, PublicationStatus.NOT_REQUESTED, 0),
        new EligibilityRow(AnalysisCompletion.PARTIAL, PublicationIntent.SAVE_COMPLETED, PublicationStatus.NOT_ELIGIBLE, 0),
        new EligibilityRow(AnalysisCompletion.COMPLETE, PublicationIntent.DO_NOT_SAVE, PublicationStatus.NOT_REQUESTED, 0),
        new EligibilityRow(AnalysisCompletion.COMPLETE, PublicationIntent.SAVE_COMPLETED, PublicationStatus.SAVED, 1));
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var completed = TestAnalysisRuns.completed(temporaryDirectory, configuration);
    for (var row : rows) {
      var run = row.completion() == AnalysisCompletion.COMPLETE ? completed : row.completion() == AnalysisCompletion.REJECTED ? TestAnalysisRuns.rejected() : partialRun(temporaryDirectory);
      var opens = new AtomicInteger();
      var saves = new AtomicInteger();
      var closes = new AtomicInteger();
      var service = service(run, context -> {
        opens.incrementAndGet();
        return new AnalysisRunRepository() {
          @Override
          public void save(AnalysisRun supplied) {
            assertEquals(run, supplied);
            saves.incrementAndGet();
          }

          @Override
          public void close() {
            closes.incrementAndGet();
          }
        };
      });

      // Act
      var execution = service.execute(request(temporaryDirectory, configuration, row.policy()));

      // Assert
      assertEquals(run, execution.run());
      assertEquals(row.policy(), execution.publication().intent());
      assertEquals(row.status(), execution.publication().status());
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
    var service = service(run, context -> { throw primary; });

    // Act
    var execution = service.execute(request(temporaryDirectory, configuration, PublicationIntent.SAVE_COMPLETED));

    // Assert
    assertEquals(run, execution.run());
    assertEquals(PublicationStatus.FAILED, execution.publication().status());
    assertEquals(CommitKnowledge.NOT_STARTED, execution.publication().knowledge());
    assertSame(primary, execution.publication().failure().orElseThrow().effectivePrimary());
    assertEquals(0, execution.publication().failure().orElseThrow().carrier().getSuppressed().length);
  }

  @Test
  void givenSuccessfulSaveAndFailedClose_whenRequestExecutes_thenNoSavedExecutionReturnsAndRunIsRetained() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var run = TestAnalysisRuns.completed(temporaryDirectory, configuration);
    var saved = new AtomicReference<AnalysisRun>();
    var primary = new RepositoryException("Close failed", new IllegalStateException("Driver close failed"));
    var service = service(run, context -> new AnalysisRunRepository() {
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
    var execution = service.execute(request(temporaryDirectory, configuration, PublicationIntent.SAVE_COMPLETED));

    // Assert
    assertEquals(run, saved.get());
    assertEquals(run, execution.run());
    assertEquals(PublicationStatus.FAILED, execution.publication().status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, execution.publication().knowledge());
    assertSame(primary, execution.publication().failure().orElseThrow().effectivePrimary());
    assertEquals(AnalysisCompletion.COMPLETE, execution.run().completion());
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
    var failure = assertThrows(AnalysisPublicationException.class,
        () -> service.execute(request(temporaryDirectory, configuration, PublicationIntent.SAVE_COMPLETED)));

    // Assert
    assertSame(primary, failure.getCause());
    assertTrue(failure.failure().knowledge().isEmpty());
    var close = assertInstanceOf(ApplicationRepositoryOperationException.class, failure.failure().getSuppressed()[0]);
    assertSame(cleanup, close.effectivePrimary());
    assertTrue(close.knowledge().isEmpty());
  }

  @Test
  void givenRawRepositorySaveFailureAndUnmatchedCleanup_whenRequestExecutes_thenPublicationRemainsAbrupt() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var run = TestAnalysisRuns.completed(temporaryDirectory, configuration);
    var primary = new RepositoryException("Save failed", new IllegalStateException("Write failed"));
    var cleanup = new IllegalStateException("Close failed");
    var service = failingService(run, primary, cleanup);

    // Act
    var failure = assertThrows(AnalysisPublicationException.class,
        () -> service.execute(request(temporaryDirectory, configuration, PublicationIntent.SAVE_COMPLETED)));

    // Assert
    assertSame(primary, failure.getCause());
    assertEquals(run, assertInstanceOf(Coordinated.class, failure.subject()).run());
    assertTrue(failure.failure().knowledge().isEmpty());
    var close = assertInstanceOf(ApplicationRepositoryOperationException.class, failure.failure().getSuppressed()[0]);
    assertSame(cleanup, close.effectivePrimary());
    assertTrue(close.knowledge().isEmpty());
  }

  private static AnalysisUseCase failingService(AnalysisRun run, RuntimeException primary, RuntimeException cleanup) {
    return service(run, context -> new AnalysisRunRepository() {
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

  private record EligibilityRow(AnalysisCompletion completion, PublicationIntent policy, PublicationStatus status, int attempts) {
  }

  @Test
  void givenDiagnosedSourceAndNoRepositoryRequest_whenAnalysisExecutes_thenPublicationIsNotRequested()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example { void run() { while (true) { } } }");
    var repositoryOpened = new AtomicBoolean();
    var service =
        service(
            context -> {
              repositoryOpened.set(true);
              throw new AssertionError("Repository must not be opened");
            });

    // Act
    var execution =
        service.execute(request(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertFalse(execution.run().completion() == AnalysisCompletion.COMPLETE);
    assertEquals(PublicationStatus.NOT_REQUESTED, execution.publication().status());
    assertFalse(repositoryOpened.get());
  }

  @Test
  void givenRejectedSourceAndRepositoryRequest_whenAnalysisExecutes_thenRepositoryIsNotOpened()
      throws IOException {
    // Arrange
    var repositoryOpened = new AtomicBoolean();
    var service =
        service(
            context -> {
              repositoryOpened.set(true);
              throw new AssertionError("Repository must not be opened");
            });

    // Act
    var execution =
        service.execute(request(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), PublicationIntent.SAVE_COMPLETED));

    // Assert
    assertFalse(execution.run().completion() == AnalysisCompletion.COMPLETE);
    assertEquals(PublicationStatus.NOT_ELIGIBLE, execution.publication().status());
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
            context -> {
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
        service.execute(request(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), PublicationIntent.SAVE_COMPLETED));

    // Assert
    assertFalse(execution.run().completion() == AnalysisCompletion.COMPLETE);
    assertEquals(PublicationStatus.NOT_ELIGIBLE, execution.publication().status());
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
    var service = service(completedRun, context -> {
      repositoryOpened.set(true);
      throw new AssertionError("Repository must not be opened");
    });

    // Act
    var execution = service.execute(request(temporaryDirectory, requestConfiguration, PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertEquals(completedRun, execution.run());
    assertEquals(PublicationStatus.NOT_REQUESTED, execution.publication().status());
    assertFalse(repositoryOpened.get());
  }

  @Test
  void givenCompletedRunAndRepositoryRequest_whenAnalysisExecutes_thenRunIsSavedAndRepositoryIsClosed() {
    // Arrange
    var requestConfiguration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var completedRun = TestAnalysisRuns.completed(temporaryDirectory, requestConfiguration);
    var persistedRun = new AtomicReference<AnalysisRun>();
    var repositoryClosed = new AtomicBoolean();
    var service = service(completedRun,
        context -> repository(persistedRun, repositoryClosed));

    // Act
    var execution = service.execute(request(temporaryDirectory, requestConfiguration, PublicationIntent.SAVE_COMPLETED));

    // Assert
    assertEquals(completedRun, execution.run());
    assertSame(execution.run(), persistedRun.get());
    assertEquals(PublicationStatus.SAVED, execution.publication().status());
    assertTrue(repositoryClosed.get());
  }

  @Test
  void givenCompletedRunAndFailingRepository_whenAnalysisExecutes_thenFailureRetainsRunAndClosesRepository() {
    // Arrange
    var requestConfiguration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var completedRun = TestAnalysisRuns.completed(temporaryDirectory, requestConfiguration);
    var repositoryFailure = new RepositoryException("database unavailable", new IllegalStateException("connection refused"));
    var repositoryClosed = new AtomicBoolean();
    var service = service(completedRun, context -> new AnalysisRunRepository() {
      @Override
      public void save(AnalysisRun run) {
        throw new ApplicationRepositoryOperationException(context, Boundary.OPERATION, Optional.of(CommitKnowledge.UNKNOWN), repositoryFailure);
      }

      @Override
      public void close() {
        repositoryClosed.set(true);
      }
    });

    // Act
    var execution = service.execute(request(temporaryDirectory, requestConfiguration, PublicationIntent.SAVE_COMPLETED));

    // Assert
    assertEquals(completedRun, execution.run());
    assertEquals(PublicationStatus.FAILED, execution.publication().status());
    assertEquals(CommitKnowledge.UNKNOWN, execution.publication().knowledge());
    assertSame(repositoryFailure, execution.publication().failure().orElseThrow().effectivePrimary());
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

  private static AnalysisUseCase service(AnalysisRunRepositoryFactory repositoryFactory) {
    var runner = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    return application(runner, repositoryFactory);
  }
  private static AnalysisUseCase service(AnalysisRun run, AnalysisRunRepositoryFactory factory) {
    return application(AnalysisCapabilities.computation(ignored -> run.representation().ast(), (ast, request) -> run.representation().cfg().orElseThrow()), factory);
  }

  private static AnalysisUseCase application(AnalysisComputation runner, AnalysisRunRepositoryFactory factory) {
    return AnalysisCapabilities.execution(runner, context -> {
      throw new AssertionError("Independent AST storage must not open");
    }, factory);
  }

  private static AnalysisRequest request(Path root, CompilerConfiguration configuration, PublicationIntent intent) {
    return new AnalysisRequest(new AnalysisComputationRequest(
        new AstProductionRequest(root, configuration, AstProfile.JAVA_SOURCE_BOUNDED), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))), intent);
  }

  private static AnalysisRun partialRun(Path root) {
    var input = InputSnapshot.fromSources(capturedAbsolute(root), List.of(SourceFile.fromContent(capturedPath(Path.of("Partial.java")),
        "class Partial { void run() { while (true) {} } }")));
    return AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard()).analyze(
        new AnalysisComputationRequest(
            new AstProductionRequest(input, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))));
  }

}
