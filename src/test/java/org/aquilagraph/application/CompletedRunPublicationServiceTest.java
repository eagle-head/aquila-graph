package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompletedRunPublicationServiceTest {
  @TempDir Path directory;

  @Test
  void givenAstOnlyOrPartialSelection_whenCompletedPublicationIsRequested_thenRepositoryIsNeverAcquired() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var runner = runner(ast);
    var astOnly = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(ast.capturedInput().orElseThrow()), configuration(),
        AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), PublicationIntent.SAVE_COMPLETED));
    var selectedEmpty = runner.deriveExisting(ast, new CfgRequest(ast.result().orElseThrow().identity(), List.of(), CfgProfile.JAVA_BOUNDED));
    var publisher = new CompletedRunPublicationService(context -> {
      throw new AssertionError("Incomplete body coverage must not acquire a repository");
    });

    for (var run : List.of(astOnly, selectedEmpty)) {
      // Act
      var publication = publisher.publish(run);

      // Assert
      assertEquals(AnalysisCompletion.COMPLETE, run.completion());
      assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
      assertEquals(Optional.of(new CompletedRun(run.identity().key())), publication.subjectIdentity());
      assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
      assertSame(run, new AnalysisExecution(run, publication).run());
      var inventedSuccess = new PublicationOutcome(PublicationIntent.SAVE_COMPLETED, publication.subjectIdentity(), PublicationStatus.SAVED,
          CommitKnowledge.KNOWN_COMMIT, Optional.empty());
      assertThrows(IllegalArgumentException.class, () -> new AnalysisExecution(run, inventedSuccess));
    }
  }

  @Test
  void givenFullDerivedCoverage_whenPublishing_thenExactNativeRunIsSavedAndSaveAndClosePrecedeSuccess() throws IOException {
    // Arrange
    var run = fullRun(produce("class Stored { int value() { return 1; } }"));
    var events = new ArrayList<String>();
    var stored = new ArrayList<AnalysisRun>();
    var publisher = new CompletedRunPublicationService(context -> {
      events.add("open");
      return new RepositoryStub(value -> {
        events.add("save");
        stored.add(value);
      }, () -> events.add("close"));
    });

    // Act
    var publication = publisher.publish(run);

    // Assert
    assertEquals(List.of("open", "save", "close"), events);
    assertEquals(PublicationStatus.SAVED, publication.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
    assertEquals(new CompletedRun(stored.getFirst().identity().key()), publication.subjectIdentity().orElseThrow());
    assertSame(run, stored.getFirst());
    assertSame(run, new AnalysisExecution(run, publication).run());
  }

  @Test
  void givenDeclaredSaveAndCloseFailures_whenPublishing_thenFailureRetainsExactComputedRunAndOrderedCleanup() throws IOException {
    // Arrange
    var run = fullRun(produce("interface Stored { int value(); }"));
    var primary = new RepositoryException("Save observation", new IllegalStateException("Primary observation"));
    var cleanup = new RepositoryException("Close observation", new IllegalArgumentException("Cleanup observation"));
    var context = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var carrier = new ApplicationRepositoryOperationException(context, Boundary.OPERATION, Optional.of(CommitKnowledge.KNOWN_NONCOMMIT), primary);
    var publisher = new CompletedRunPublicationService(requested -> new RepositoryStub(value -> {
      assertEquals(context, requested);
      throw carrier;
    }, () -> {
      throw cleanup;
    }));

    // Act
    var outcome = publisher.publish(run);

    // Assert
    assertSame(run, new AnalysisExecution(run, outcome).run());
    assertEquals(PublicationStatus.FAILED, outcome.status());
    assertEquals(CommitKnowledge.KNOWN_NONCOMMIT, outcome.knowledge());
    assertSame(carrier, outcome.failure().orElseThrow().carrier());
    assertSame(primary, outcome.failure().orElseThrow().effectivePrimary());
    var close = assertInstanceOf(ApplicationRepositoryOperationException.class, carrier.getSuppressed()[0]);
    assertSame(cleanup, close.effectivePrimary());
    assertEquals(context, close.context());
    assertEquals(Optional.of(CommitKnowledge.KNOWN_NONCOMMIT), close.knowledge());
  }

  @Test
  void givenUnmatchedSaveFailureAndDeclaredCleanup_whenPublishing_thenOriginalRuntimeFamilyRemainsAbrupt() throws IOException {
    // Arrange
    var run = fullRun(produce("interface Stored { int value(); }"));
    var primary = new IllegalArgumentException("Unmatched work observation");
    var cleanup = new RepositoryException("Close observation", new IllegalStateException("Cleanup observation"));
    var publisher = new CompletedRunPublicationService(context -> new RepositoryStub(value -> {
      throw primary;
    }, () -> {
      throw cleanup;
    }));

    // Act
    var failure = assertThrows(AnalysisPublicationException.class, () -> publisher.publish(run));

    // Assert
    assertSame(primary, failure.getCause());
    assertSame(run, assertInstanceOf(Coordinated.class, failure.subject()).run());
    assertTrue(failure.failure().knowledge().isEmpty());
    var close = assertInstanceOf(ApplicationRepositoryOperationException.class, failure.failure().getSuppressed()[0]);
    assertSame(cleanup, close.effectivePrimary());
    assertEquals(Boundary.CLOSURE, close.boundary());
    assertEquals(failure.context(), close.context());
    assertTrue(close.knowledge().isEmpty());
  }

  @Test
  void givenForeignSaveCarrierAndRawCloseFailure_whenPublishing_thenRejectionRetainsForeignEvidenceAndNoKnowledgeIsBorrowed() throws IOException {
    // Arrange
    var run = fullRun(produce("interface Stored { int value(); }"));
    var requested = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var foreign = new Neo4jRepositoryOperationException(RepositoryOperationContext.saveCompletedRun("foreign-run"),
        Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.KNOWN_COMMIT,
        new RepositoryException("Foreign save observation", new IllegalStateException("Driver observation")));
    var cleanup = new RepositoryException("Close observation", new IllegalStateException("Cleanup observation"));
    var publisher = new CompletedRunPublicationService(context -> new RepositoryStub(value -> {
      throw foreign;
    }, () -> {
      throw cleanup;
    }));

    // Act
    var failure = assertThrows(AnalysisPublicationException.class, () -> publisher.publish(run));

    // Assert
    assertSame(run, assertInstanceOf(Coordinated.class, failure.subject()).run());
    assertEquals(requested, failure.context());
    assertSame(foreign, assertInstanceOf(IllegalArgumentException.class, failure.getCause()).getCause());
    assertTrue(failure.failure().knowledge().isEmpty());
    var close = assertInstanceOf(ApplicationRepositoryOperationException.class, failure.failure().getSuppressed()[0]);
    assertEquals(requested, close.context());
    assertSame(cleanup, close.effectivePrimary());
    assertTrue(close.knowledge().isEmpty());
    assertEquals(Optional.of(CommitKnowledge.KNOWN_COMMIT), foreign.knowledge());
  }

  @Test
  void givenAdmittedRuntimePrimaryAndFatalCleanup_whenPublishing_thenPrimaryDecisionAndExactFatalEvidenceAreRetained() throws IOException {
    // Arrange
    var run = fullRun(produce("interface Stored { int value(); }"));
    var fatal = new LinkageError("Fatal cleanup");
    var primary = new RepositoryException("Write observation", new IllegalStateException("Driver observation"));
    var carrier = new Neo4jRepositoryOperationException(RepositoryOperationContext.saveCompletedRun(run.identity().key()),
        Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.UNKNOWN, primary);
    var publisher = new CompletedRunPublicationService(context -> new RepositoryStub(value -> {
      throw carrier;
    }, () -> {
      throw fatal;
    }));

    // Act
    var publication = publisher.publish(run);

    // Assert
    assertEquals(PublicationStatus.FAILED, publication.status());
    assertEquals(CommitKnowledge.UNKNOWN, publication.knowledge());
    assertSame(primary, publication.failure().orElseThrow().effectivePrimary());
    assertEquals(List.of(fatal), List.of(publication.failure().orElseThrow().carrier().getSuppressed()));
  }

  @Test
  void givenAcknowledgedFirstSaveAndUnobservedSecondSave_whenClosingTheSameOwnedPort_thenPriorCommitDoesNotEnterEitherFailure()
      throws IOException {
    var run = fullRun(produce("interface Stored { int value(); }"));
    var context = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var raw = new RepositoryException("Second save reported no observation", new IllegalStateException("Driver outcome absent"));
    var foreign = new Neo4jRepositoryOperationException(RepositoryOperationContext.saveCompletedRun("foreign-run"),
        Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.KNOWN_COMMIT, raw);

    for (var secondExit : List.of(raw, foreign)) {
      // Arrange
      var saves = new AtomicInteger();
      var events = new ArrayList<String>();
      var closeFailure = new IllegalStateException("Enclosing cleanup failed");
      var repository = new RepositoryStub(supplied -> {
        assertSame(run, supplied);
        if (saves.incrementAndGet() == 1) {
          events.add("first save acknowledged");
        } else {
          events.add("second save failed");
          throw secondExit;
        }
      }, () -> {
        events.add("close failed");
        throw closeFailure;
      });
      var owned = new OwnedRepository(context);

      // Act
      var primary = assertThrows(ApplicationRepositoryOperationException.class, () -> owned.use(requested -> {
        assertEquals(context, requested);
        return repository;
      }, port -> {
        owned.save(() -> port.save(run));
        owned.save(() -> port.save(run));
        return null;
      }, AnalysisRunRepository::close));

      // Assert
      assertEquals(List.of("first save acknowledged", "second save failed", "close failed"), events);
      assertEquals(context, primary.context());
      assertEquals(Boundary.OPERATION, primary.boundary());
      assertTrue(primary.knowledge().isEmpty());
      if (secondExit == foreign) {
        assertSame(foreign, assertInstanceOf(IllegalArgumentException.class, primary.effectivePrimary()).getCause());
      } else {
        assertSame(raw, primary.effectivePrimary());
      }
      assertEquals(1, primary.getSuppressed().length);
      var close = assertInstanceOf(ApplicationRepositoryOperationException.class, primary.getSuppressed()[0]);
      assertEquals(context, close.context());
      assertEquals(Boundary.CLOSURE, close.boundary());
      assertTrue(close.knowledge().isEmpty());
      assertSame(closeFailure, close.effectivePrimary());
    }
  }

  @Test
  void givenDifferentSubjectOrContradictoryIntent_whenPublicationIsComposed_thenValueAdmissionRejectsIt() throws IOException {
    // Arrange
    var run = fullRun(produce("interface Stored { int value(); }"));
    var wrongSubject = new PublicationOutcome(PublicationIntent.SAVE_COMPLETED, Optional.of(new CompletedRun("foreign-run")),
        PublicationStatus.SAVED, CommitKnowledge.KNOWN_COMMIT, Optional.empty());

    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> new AnalysisExecution(run, wrongSubject));
    assertThrows(IllegalArgumentException.class, () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST,
        Optional.of(new Ast(run.representation().ast().result().orElseThrow().identity())), PublicationStatus.NOT_REQUESTED,
        CommitKnowledge.NOT_STARTED, Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST,
        wrongSubject.subjectIdentity(), PublicationStatus.SAVED, CommitKnowledge.KNOWN_COMMIT, Optional.empty()));
    assertTrue(run.representation().cfg().orElseThrow().methods().isEmpty());
  }

  private AstOutcome produce(String source) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), source))), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private static AnalysisRun fullRun(AstOutcome ast) {
    return runner(ast).deriveExisting(ast, new CfgRequest(ast.result().orElseThrow().identity(), ast.result().orElseThrow().program()
        .methods().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).toList(), CfgProfile.JAVA_BOUNDED));
  }

  private static SourceAnalysisRunner runner(AstOutcome ast) {
    return new SourceAnalysisRunner(ignored -> ast, CfgDerivers.standard());
  }

  private static CompilerConfiguration configuration() {
    return new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of());
  }

  private record RepositoryStub(Consumer<AnalysisRun> save, Runnable cleanup) implements AnalysisRunRepository {
    @Override
    public void save(AnalysisRun run) {
      save.accept(run);
    }

    @Override
    public void close() {
      cleanup.run();
    }
  }
}
