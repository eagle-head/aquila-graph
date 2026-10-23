package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.function.Function;
import org.aquilagraph.application.ComputedPublicationSubject.Ast;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.port.AstResultRepository;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AstRetentionServiceTest {
  @TempDir Path directory;

  @Test
  void givenIncompleteAstAndReturnedConstructionFailure_whenRetainingRun_thenStorageIsNotAcquired() throws IOException {
    // Arrange
    var ast = produce("class Stored { int field = 1; int value() { return 1; } }");
    var original = ast.result().orElseThrow();
    var key = original.program().methods().stream().filter(method -> method.name().equals("value")).findFirst().orElseThrow().methodKey();
    var cause = new IllegalStateException("Declared construction observation");
    var run = new SourceAnalysisRunner(ignored -> ast, RecoverableCfgFailures.atMethod(key, cause)).deriveExisting(ast,
        new CfgRequest(original.identity(), List.of(key), CfgProfile.JAVA_BOUNDED));
    var retention = new AstRetentionService(ignored -> {
      throw new AssertionError("Incomplete source must not acquire storage even when derivation fails");
    });

    // Act
    var publication = retention.retainAst(run);

    // Assert
    assertEquals(AnalysisCompletion.FAILED, run.completion());
    assertSame(cause, run.representation().cfg().orElseThrow().methods().getFirst().failure().orElseThrow());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
    assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
    assertEquals(Optional.of(new PublicationSubjectIdentity.Ast(original.identity())), publication.subjectIdentity());
  }

  @Test
  void givenExplicitNoSaveForCompleteOrIncompleteSource_whenRetaining_thenNoRepositoryIsAcquired() throws IOException {
    // Arrange
    var retention = new AstRetentionService(ignored -> {
      throw new AssertionError("Explicit no-save must not acquire a repository");
    });
    var complete = produce("class Stored { int value() { return 1; } }");
    var incomplete = produce("class Stored { void value() { while (false) {} } }");

    // Act and Assert
    for (var ast : List.of(complete, incomplete)) {
      var result = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.DO_NOT_SAVE));
      assertEquals(PublicationStatus.NOT_REQUESTED, result.status());
      assertEquals(CommitKnowledge.NOT_STARTED, result.knowledge());
      assertEquals(ast.result().orElseThrow().identity(), ((PublicationSubjectIdentity.Ast) result.subjectIdentity().orElseThrow()).identity());
      assertThrows(IllegalArgumentException.class, () -> new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETED));
    }
  }

  @Test
  void givenCompleteAst_whenRetaining_thenExactSaveAndEnclosingClosePrecedeSavedOutcome() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var identity = ast.result().orElseThrow().identity();
    var events = new ArrayList<String>();
    var retention = new AstRetentionService(context -> {
      events.add("acquisition");
      assertEquals(new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST, identity), context);
      return new RepositoryStub(value -> {
        assertSame(ast, value);
        events.add("save");
      }, ignored -> {
        throw new AssertionError("Publication must not query");
      }, () -> events.add("close"));
    });

    // Act
    var result = retention.retainAst(ast);

    // Assert
    assertEquals(List.of("acquisition", "save", "close"), events);
    assertEquals(PublicationStatus.SAVED, result.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, result.knowledge());
    assertEquals(Optional.of(new PublicationSubjectIdentity.Ast(identity)), result.subjectIdentity());
    assertTrue(result.failure().isEmpty());
  }

  @Test
  void givenIncompleteOrUnavailableAst_whenRetaining_thenNoRepositoryIsAcquiredAndNoIdentityIsFabricated() throws IOException {
    // Arrange
    var incomplete = produce("class Stored { int field = 1; int value() { return 1; } }");
    var unavailable = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory, List.of()),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var retention = new AstRetentionService(ignored -> {
      throw new AssertionError("Ineligible source must not acquire storage");
    });

    // Act
    var partial = retention.retainAst(incomplete);
    var absent = retention.retainAst(unavailable);

    // Assert
    assertEquals(PublicationStatus.NOT_ELIGIBLE, partial.status());
    assertTrue(partial.subjectIdentity().isPresent());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, absent.status());
    assertTrue(absent.subjectIdentity().isEmpty());
    assertEquals(CommitKnowledge.NOT_STARTED, partial.knowledge());
    assertEquals(CommitKnowledge.NOT_STARTED, absent.knowledge());
  }

  @Test
  void givenRejectedCfgWithIndependentlyCompleteAst_whenRetainingCoordinatedRun_thenSourceCanBeSavedWithoutChangingTheRun() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var runner = new SourceAnalysisRunner(ignored -> {
      throw new AssertionError("Existing derivation must not produce source");
    }, CfgDerivers.standard());
    var run = runner.deriveExisting(ast,
        new CfgRequest(ast.result().orElseThrow().identity(), List.of("unknown"), CfgProfile.JAVA_BOUNDED));
    var closes = new AtomicInteger();
    var retention = new AstRetentionService(ignored -> new RepositoryStub(value -> assertSame(ast, value),
        identity -> {
          throw new AssertionError("Retention must not query");
        }, closes::incrementAndGet));

    // Act
    var result = retention.retainAst(run);

    // Assert
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertEquals(PublicationStatus.SAVED, result.status());
    assertSame(ast, run.representation().ast());
    assertEquals(1, closes.get());
  }

  @Test
  void givenRepositoryPrimaryAndOrderedCleanupFailures_whenRetaining_thenFailedOutcomePreservesExactCauseAndEveryCarrier() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var context = saveContext(ast);
    var effective = new RepositoryException("Replacement failed", new IllegalStateException("Driver observation"));
    var originalCleanup = new IllegalArgumentException("Existing original cleanup");
    effective.addSuppressed(originalCleanup);
    var primary = new RepositoryOperationException(context, RepositoryPhase.REPLACEMENT, CommitKnowledge.UNKNOWN, effective);
    var sessionClose = new RepositoryOperationException(context, RepositoryPhase.PUBLICATION_SESSION_CLOSE, CommitKnowledge.UNKNOWN,
        new RepositoryException("Session close failed", new IllegalStateException("Session observation")));
    var enclosingClose = new RepositoryOperationException(context, RepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.UNKNOWN,
        new RepositoryException("Enclosing close failed", new IllegalStateException("Close observation")));
    primary.addSuppressed(sessionClose);
    var retention = new AstRetentionService(ignored -> new RepositoryStub(value -> {
      throw primary;
    }, identity -> {
      throw new AssertionError("Publication must not query");
    }, () -> {
      throw enclosingClose;
    }));

    // Act
    var result = retention.retainAst(ast);

    // Assert
    assertEquals(PublicationStatus.FAILED, result.status());
    assertEquals(CommitKnowledge.UNKNOWN, result.knowledge());
    var failure = result.failure().orElseThrow();
    assertSame(primary, failure.carrier());
    assertSame(effective, failure.effectivePrimary());
    assertEquals(List.of(sessionClose, enclosingClose), List.of(primary.getSuppressed()));
    assertSame(originalCleanup, effective.getSuppressed()[0]);
    assertEquals(RepositoryPhase.PUBLICATION_SESSION_CLOSE, sessionClose.phase());
    assertEquals(RepositoryPhase.REPOSITORY_CLOSE, enclosingClose.phase());
  }

  @Test
  void givenDeclaredNoncommitAndUncertainCleanup_whenRetaining_thenPrimaryKnowledgeAndExactSubjectArePreserved() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var context = saveContext(ast);
    var effective = new RepositoryException("Authoritative noncommit supplied by the port", new IllegalStateException("Port observation"));
    var primary = new RepositoryOperationException(context, RepositoryPhase.REPLACEMENT, CommitKnowledge.KNOWN_NONCOMMIT, effective);
    var cleanup = new RepositoryOperationException(context, RepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.UNKNOWN,
        new RepositoryException("Close failed", new IllegalStateException("Cleanup observation")));
    var events = new ArrayList<String>();
    var retention = new AstRetentionService(operation -> {
      assertEquals(context, operation);
      events.add("acquisition");
      return new RepositoryStub(value -> {
        assertSame(ast, value);
        events.add("save");
        throw primary;
      }, identity -> {
        throw new AssertionError("Publication must not query");
      }, () -> {
        events.add("close");
        throw cleanup;
      });
    });

    // Act
    var outcome = retention.retainAst(ast);

    // Assert
    assertEquals(List.of("acquisition", "save", "close"), events);
    assertEquals(PublicationStatus.FAILED, outcome.status());
    assertEquals(CommitKnowledge.KNOWN_NONCOMMIT, outcome.knowledge());
    assertEquals(Optional.of(new PublicationSubjectIdentity.Ast(context.astIdentity())), outcome.subjectIdentity());
    assertSame(primary, outcome.failure().orElseThrow().carrier());
    assertSame(effective, outcome.failure().orElseThrow().effectivePrimary());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
    assertEquals(CommitKnowledge.UNKNOWN, cleanup.knowledge());
  }

  @Test
  void givenUnmatchedMappingPrimaryAndRepositoryCleanupFailure_whenRetaining_thenAbruptEnvelopeKeepsTheComputedSubjectAndPrimaryFamily()
      throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var context = saveContext(ast);
    var effective = new IllegalArgumentException("Copied mapping observation");
    var primary = new RepositoryOperationException(context, RepositoryPhase.MAPPING, CommitKnowledge.NOT_STARTED, effective);
    var cleanup = new RepositoryOperationException(context, RepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.NOT_STARTED,
        new RepositoryException("Close failed", new IllegalStateException("Cleanup observation")));
    var retention = new AstRetentionService(ignored -> new RepositoryStub(value -> {
      throw primary;
    }, identity -> {
      throw new AssertionError("Publication must not query");
    }, () -> {
      throw cleanup;
    }));

    // Act
    var failure = assertThrows(AnalysisPublicationException.class, () -> retention.retainAst(ast));

    // Assert
    assertSame(effective, failure.getCause());
    assertSame(ast, ((Ast) failure.subject()).outcome());
    assertSame(primary, failure.failure().carrier());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
    assertEquals(RepositoryPhase.MAPPING, failure.failure().phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.failure().knowledge());
  }

  @Test
  void givenCoordinatedRunAndUnmatchedFailure_whenRetaining_thenAbruptEnvelopeRetainsTheIdenticalRun() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var run = new SourceAnalysisRunner(ignored -> ast, CfgDerivers.standard()).deriveExisting(ast,
        new CfgRequest(ast.result().orElseThrow().identity(), List.of(), CfgProfile.JAVA_BOUNDED));
    var primary = new RepositoryOperationException(saveContext(ast), RepositoryPhase.SCHEMA, CommitKnowledge.NOT_STARTED,
        new IllegalStateException("Schema observation"));
    var retention = new AstRetentionService(ignored -> new RepositoryStub(value -> {
      throw primary;
    }, identity -> {
      throw new AssertionError("Publication must not query");
    }, () -> {
    }));

    // Act
    var failure = assertThrows(AnalysisPublicationException.class, () -> retention.retainAst(run));

    // Assert
    assertSame(run, ((Coordinated) failure.subject()).run());
    assertSame(primary.effectivePrimary(), failure.getCause());
    assertSame(ast, run.representation().ast());
  }

  @Test
  void givenAcknowledgedSaveAndEnclosingRepositoryFailure_whenRetaining_thenKnownCommitSurvivesButSavedIsNotReturned() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var effective = new RepositoryException("Close failed", new IllegalStateException("Close observation"));
    var retention = new AstRetentionService(ignored -> new RepositoryStub(value -> {
    }, identity -> {
      throw new AssertionError("Publication must not query");
    }, () -> {
      throw effective;
    }));

    // Act
    var result = retention.retainAst(ast);

    // Assert
    assertEquals(PublicationStatus.FAILED, result.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, result.knowledge());
    assertSame(effective, result.failure().orElseThrow().effectivePrimary());
    assertEquals(RepositoryPhase.REPOSITORY_CLOSE, result.failure().orElseThrow().phase());
  }

  @Test
  void givenAcquisitionFailureWithFactoryCleanupEvidence_whenRetaining_thenNoTransferredRepositoryIsClosedAndEvidenceIsPreserved()
      throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var primary = new RepositoryOperationException(saveContext(ast), RepositoryPhase.ACQUISITION, CommitKnowledge.NOT_STARTED,
        new RepositoryException("Acquisition failed", new IllegalStateException("Acquisition observation")));
    var cleanup = new RepositoryOperationException(saveContext(ast), RepositoryPhase.ACQUISITION, CommitKnowledge.NOT_STARTED,
        new IllegalStateException("Partial-resource close observation"));
    primary.addSuppressed(cleanup);
    var retention = new AstRetentionService(ignored -> {
      throw primary;
    });

    // Act
    var result = retention.retainAst(ast);

    // Assert
    assertEquals(PublicationStatus.FAILED, result.status());
    assertSame(primary, result.failure().orElseThrow().carrier());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
    assertEquals(CommitKnowledge.NOT_STARTED, result.knowledge());
  }

  @Test
  void givenAbsentOrUnsupportedStoredSubject_whenFinding_thenNormalLookupReturnsOnlyAfterEnclosingClose()
      throws IOException {
    for (var status : List.of(AstLookupStatus.NOT_FOUND, AstLookupStatus.INCOMPATIBLE)) {
      // Arrange
      var ast = produce("class Stored { int value() { return 1; } }");
      var identity = ast.result().orElseThrow().identity();
      var result = new AstLookup(identity, status, Optional.empty(),
          status == AstLookupStatus.INCOMPATIBLE ? Optional.of("unsupported-source-format") : Optional.empty());
      var events = new ArrayList<String>();
      var retention = new AstRetentionService(context -> {
        assertEquals(new RepositoryOperationContext(RepositoryOperationKind.QUERY_AST, identity), context);
        events.add("acquisition");
        return new RepositoryStub(value -> {
          throw new AssertionError("Lookup must not publish");
        }, requested -> {
          assertEquals(identity, requested);
          events.add("find");
          return result;
        }, () -> events.add("close"));
      });

      // Act
      var found = retention.findAst(identity);

      // Assert
      assertSame(result, found);
      assertEquals(List.of("acquisition", "find", "close"), events);
      assertTrue(found.outcome().isEmpty());
    }
  }

  @Test
  void givenQueryFailureAndEnclosingCloseFailure_whenFinding_thenNoLookupIsReturnedAndExactQueryCarrierPropagates()
      throws IOException {
    for (var phase : List.of(RepositoryPhase.READ, RepositoryPhase.DECODE, RepositoryPhase.READ_SESSION_CLOSE)) {
      // Arrange
      var ast = produce("class Stored { int value() { return 1; } }");
      var identity = ast.result().orElseThrow().identity();
      var context = new RepositoryOperationContext(RepositoryOperationKind.QUERY_AST, identity);
      var primary = new RepositoryOperationException(context, phase, CommitKnowledge.NOT_STARTED,
          new IllegalArgumentException("Read or admission observation"));
      var cleanup = new RepositoryOperationException(context, RepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.NOT_STARTED,
          new RepositoryException("Close failed", new IllegalStateException("Close observation")));
      var retention = new AstRetentionService(ignored -> new RepositoryStub(value -> {
        throw new AssertionError("Query must not publish");
      }, requested -> {
        throw primary;
      }, () -> {
        throw cleanup;
      }));

      // Act
      var failure = assertThrows(RepositoryOperationException.class, () -> retention.findAst(identity));

      // Assert
      assertSame(primary, failure);
      assertEquals(context, failure.context());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
      assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    }
  }

  @Test
  void givenNormalLookupAndEnclosingCloseFailure_whenFinding_thenCloseFailurePreventsNormalLookupReturn() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var identity = ast.result().orElseThrow().identity();
    var effective = new RepositoryException("Close failed", new IllegalStateException("Close observation"));
    var retention = new AstRetentionService(ignored -> new RepositoryStub(value -> {
      throw new AssertionError("Query must not publish");
    }, requested -> new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(ast), Optional.empty()), () -> {
      throw effective;
    }));

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> retention.findAst(identity));

    // Assert
    assertSame(effective, failure.effectivePrimary());
    assertEquals(RepositoryOperationKind.QUERY_AST, failure.context().kind());
    assertEquals(identity, failure.context().astIdentity());
    assertEquals(RepositoryPhase.REPOSITORY_CLOSE, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
  }

  @Test
  void givenFatalPrimaryAndRuntimeCleanupFailure_whenRetaining_thenFatalFamilyAndApplicableCleanupEvidenceArePreserved() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var fatal = new AssertionError("Fatal work observation");
    var cleanup = new RepositoryOperationException(saveContext(ast), RepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.NOT_STARTED,
        new RepositoryException("Close failed", new IllegalStateException("Close observation")));
    var retention = new AstRetentionService(ignored -> new RepositoryStub(value -> {
      throw fatal;
    }, identity -> {
      throw new AssertionError("Publication must not query");
    }, () -> {
      throw cleanup;
    }));

    // Act
    var failure = assertThrows(AssertionError.class, () -> retention.retainAst(ast));

    // Assert
    assertSame(fatal, failure);
    assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));
  }

  private AstOutcome produce(String source) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), source))), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private static CompilerConfiguration configuration() {
    return new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of());
  }

  private static RepositoryOperationContext saveContext(AstOutcome ast) {
    return new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST, ast.result().orElseThrow().identity());
  }

  private record RepositoryStub(Consumer<AstOutcome> save, Function<AstIdentity, AstLookup> lookup, Runnable cleanup)
      implements AstResultRepository {
    @Override
    public void saveComplete(AstOutcome outcome) {
      save.accept(outcome);
    }

    @Override
    public AstLookup find(AstIdentity identity) {
      return lookup.apply(identity);
    }

    @Override
    public void close() {
      cleanup.run();
    }
  }
}
