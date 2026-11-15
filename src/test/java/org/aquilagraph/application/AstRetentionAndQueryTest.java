package org.aquilagraph.application;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

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
import java.util.function.Function;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.ComputedPublicationSubject.Ast;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.port.AstQueryRepository;
import org.aquilagraph.application.port.AstRetentionRepository;
import org.aquilagraph.application.port.AstRetentionRepositoryFactory;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.application.port.RepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
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
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AstRetentionAndQueryTest {
  @TempDir Path directory;

  @Test
  void givenIncompleteAstAndReturnedConstructionFailure_whenRetainingRun_thenStorageIsNotAcquired() throws IOException {
    // Arrange
    var ast = produce("class Stored { int field = 1; int value() { return 1; } }");
    var original = ast.result().orElseThrow();
    var key = original.program().methods().stream().filter(method -> method.name().equals("value")).findFirst().orElseThrow().methodKey();
    var cause = new IllegalStateException("Declared construction observation");
    var run = AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(key, cause)).deriveExisting(ast,
        new CfgRequest(original.identity(), List.of(key), CfgProfile.JAVA_BOUNDED));
    AstRetentionRepositoryFactory repositories = ignored -> {
      throw new AssertionError("Incomplete source must not acquire storage even when derivation fails");
    };

    // Act
    var publication = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETE_AST, repositories,
        AnalysisExecutionFixture::unexpectedRunRepository).publication();

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
    var retention = AnalysisCapabilities.astRetention(ignored -> {
      throw new AssertionError("Explicit no-save must not acquire a repository");
    });
    var complete = produce("class Stored { int value() { return 1; } }");
    var incomplete = produce("class Stored { void value() { while (false) {} } }");

    for (var ast : List.of(complete, incomplete)) {
      // Act
      var result = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.DO_NOT_SAVE));

      // Assert
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
    var retention = AnalysisCapabilities.astRetention(context -> {
      events.add("acquisition");
      assertEquals(RepositoryOperationContext.saveAst(identity), context);
      return new RetentionRepository(value -> {
        assertSame(ast, value);
        events.add("save");
      }, () -> events.add("close"));
    });

    // Act
    var result = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));

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
    var unavailable = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(capturedAbsolute(directory), List.of()),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var retention = AnalysisCapabilities.astRetention(ignored -> {
      throw new AssertionError("Ineligible source must not acquire storage");
    });

    // Act
    var partial = retention.retainAst(new AstPublicationRequest(incomplete, PublicationIntent.SAVE_COMPLETE_AST));
    var absent = retention.retainAst(new AstPublicationRequest(unavailable, PublicationIntent.SAVE_COMPLETE_AST));

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
    var runner = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard());
    var run = runner.deriveExisting(ast,
        new CfgRequest(ast.result().orElseThrow().identity(), List.of("unknown"), CfgProfile.JAVA_BOUNDED));
    var closes = new AtomicInteger();
    AstRetentionRepositoryFactory repositories = ignored -> new RetentionRepository(value -> assertSame(ast, value), closes::incrementAndGet);

    // Act
    var result = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETE_AST, repositories,
        AnalysisExecutionFixture::unexpectedRunRepository).publication();

    // Assert
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertEquals(PublicationStatus.SAVED, result.status());
    assertSame(ast, run.representation().ast());
    assertEquals(1, closes.get());
  }

  @Test
  void givenCompleteSourceAndReturnedCfgFailure_whenRetainingCoordinatedRun_thenSourceIsSavedAndCfgFailureSurvives()
      throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var source = ast.result().orElseThrow();
    var key = source.program().methods().stream().filter(method -> method.name().equals("value")).findFirst().orElseThrow().methodKey();
    var cause = new IllegalStateException("Declared construction observation");
    var run = AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(key, cause)).deriveExisting(ast,
        new CfgRequest(source.identity(), List.of(key), CfgProfile.JAVA_BOUNDED));
    var retained = new ArrayList<AstOutcome>();
    AstRetentionRepositoryFactory repositories = context -> new RetentionRepository(retained::add, () -> {});

    // Act
    var outcome = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETE_AST, repositories,
        AnalysisExecutionFixture::unexpectedRunRepository).publication();

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertSame(ast, retained.getFirst());
    assertEquals(1, retained.size());
    assertEquals(PublicationStatus.SAVED, outcome.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, outcome.knowledge());
    assertEquals(Optional.of(RepositoryOperationContext.saveAst(source.identity()).subject()), outcome.subjectIdentity());
    var execution = new AnalysisExecution(run, outcome);
    assertSame(run, execution.run());
    assertSame(cause, execution.run().representation().cfg().orElseThrow().methods().getFirst().failure().orElseThrow());
  }

  @Test
  void givenRawSaveFailureAndTypedCleanupObservation_whenRetaining_thenCleanupKnowledgeDoesNotUpgradeThePrimary() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var requested = saveContext(ast);
    var primary = new RepositoryException("Unobserved save exit", new IllegalStateException("No save observation"));
    var close = new Neo4jRepositoryOperationException(requested, Neo4jRepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.KNOWN_COMMIT,
        new RepositoryException("Close observation", new IllegalStateException("Close failed")));
    var retention = AnalysisCapabilities.astRetention(context -> new RetentionRepository(value -> {
      throw primary;
    }, () -> {
      throw close;
    }));

    // Act
    var failure = assertThrows(AnalysisPublicationException.class, () -> retention.retainAst(new AstPublicationRequest(ast,
        PublicationIntent.SAVE_COMPLETE_AST)));

    // Assert
    assertSame(ast, assertInstanceOf(Ast.class, failure.subject()).outcome());
    assertSame(primary, failure.getCause());
    assertEquals(requested, failure.context());
    var observed = assertInstanceOf(ApplicationRepositoryOperationException.class, failure.failure());
    assertEquals(Boundary.OPERATION, observed.boundary());
    assertTrue(observed.knowledge().isEmpty());
    assertEquals(List.of(close), List.of(observed.getSuppressed()));
    assertEquals(Optional.of(CommitKnowledge.KNOWN_COMMIT), close.knowledge());
  }

  @Test
  void givenRepositoryPrimaryAndOrderedCleanupFailures_whenRetaining_thenFailedOutcomePreservesExactCauseAndEveryCarrier() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var context = saveContext(ast);
    var effective = new RepositoryException("Replacement failed", new IllegalStateException("Driver observation"));
    var originalCleanup = new IllegalArgumentException("Existing original cleanup");
    effective.addSuppressed(originalCleanup);
    var primary = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.UNKNOWN, effective);
    var sessionClose = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE, CommitKnowledge.UNKNOWN,
        new RepositoryException("Session close failed", new IllegalStateException("Session observation")));
    var enclosingClose = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.UNKNOWN,
        new RepositoryException("Enclosing close failed", new IllegalStateException("Close observation")));
    primary.addSuppressed(sessionClose);
    var retention = AnalysisCapabilities.astRetention(ignored -> new RetentionRepository(value -> {
      throw primary;
    }, () -> {
      throw enclosingClose;
    }));

    // Act
    var result = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertEquals(PublicationStatus.FAILED, result.status());
    assertEquals(CommitKnowledge.UNKNOWN, result.knowledge());
    var failure = result.failure().orElseThrow();
    assertSame(primary, failure.carrier());
    assertSame(effective, failure.effectivePrimary());
    assertEquals(List.of(sessionClose, enclosingClose), List.of(primary.getSuppressed()));
    assertSame(originalCleanup, effective.getSuppressed()[0]);
    assertEquals(Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE, sessionClose.phase());
    assertEquals(Neo4jRepositoryPhase.REPOSITORY_CLOSE, enclosingClose.phase());
  }

  @Test
  void givenDeclaredNoncommitAndUncertainCleanup_whenRetaining_thenPrimaryKnowledgeAndExactSubjectArePreserved() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var context = saveContext(ast);
    var effective = new RepositoryException("Authoritative noncommit supplied by the port", new IllegalStateException("Port observation"));
    var primary = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.KNOWN_NONCOMMIT, effective);
    var cleanup = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.UNKNOWN,
        new RepositoryException("Close failed", new IllegalStateException("Cleanup observation")));
    var events = new ArrayList<String>();
    var retention = AnalysisCapabilities.astRetention(operation -> {
      assertEquals(context, operation);
      events.add("acquisition");
      return new RetentionRepository(value -> {
        assertSame(ast, value);
        events.add("save");
        throw primary;
      }, () -> {
        events.add("close");
        throw cleanup;
      });
    });

    // Act
    var outcome = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertEquals(List.of("acquisition", "save", "close"), events);
    assertEquals(PublicationStatus.FAILED, outcome.status());
    assertEquals(CommitKnowledge.KNOWN_NONCOMMIT, outcome.knowledge());
    assertEquals(Optional.of(context.subject()), outcome.subjectIdentity());
    assertSame(primary, outcome.failure().orElseThrow().carrier());
    assertSame(effective, outcome.failure().orElseThrow().effectivePrimary());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
    assertEquals(CommitKnowledge.UNKNOWN, cleanup.knowledge().orElseThrow());
  }

  @Test
  void givenUnmatchedMappingPrimaryAndRepositoryCleanupFailure_whenRetaining_thenAbruptEnvelopeKeepsTheComputedSubjectAndPrimaryFamily()
      throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var context = saveContext(ast);
    var effective = new IllegalArgumentException("Copied mapping observation");
    var primary = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.MAPPING, CommitKnowledge.NOT_STARTED, effective);
    var cleanup = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.NOT_STARTED,
        new RepositoryException("Close failed", new IllegalStateException("Cleanup observation")));
    var retention = AnalysisCapabilities.astRetention(ignored -> new RetentionRepository(value -> {
      throw primary;
    }, () -> {
      throw cleanup;
    }));

    // Act
    var failure = assertThrows(AnalysisPublicationException.class, () -> retention.retainAst(new AstPublicationRequest(ast,
        PublicationIntent.SAVE_COMPLETE_AST)));

    // Assert
    assertSame(effective, failure.getCause());
    assertSame(ast, ((Ast) failure.subject()).outcome());
    assertSame(primary, failure.failure());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
    assertEquals(Neo4jRepositoryPhase.MAPPING, assertInstanceOf(Neo4jRepositoryOperationException.class, failure.failure()).phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.failure().knowledge().orElseThrow());
  }

  @Test
  void givenCoordinatedRunAndUnmatchedFailure_whenRetaining_thenAbruptEnvelopeRetainsTheIdenticalRun() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(ast,
        new CfgRequest(ast.result().orElseThrow().identity(), List.of(), CfgProfile.JAVA_BOUNDED));
    var primary = new Neo4jRepositoryOperationException(saveContext(ast), Neo4jRepositoryPhase.SCHEMA, CommitKnowledge.NOT_STARTED,
        new IllegalStateException("Schema observation"));
    AstRetentionRepositoryFactory repositories = ignored -> new RetentionRepository(value -> {
      throw primary;
    }, () -> {
    });

    // Act
    var failure = assertThrows(AnalysisPublicationException.class, () -> AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETE_AST, repositories,
        AnalysisExecutionFixture::unexpectedRunRepository).publication());

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
    var retention = AnalysisCapabilities.astRetention(ignored -> new RetentionRepository(value -> {
    }, () -> {
      throw effective;
    }));

    // Act
    var result = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertEquals(PublicationStatus.FAILED, result.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, result.knowledge());
    assertSame(effective, result.failure().orElseThrow().effectivePrimary());
    assertEquals(Boundary.CLOSURE, assertInstanceOf(ApplicationRepositoryOperationException.class,
        result.failure().orElseThrow().carrier()).boundary());
  }

  @Test
  void givenAcquisitionFailureWithFactoryCleanupEvidence_whenRetaining_thenNoTransferredRepositoryIsClosedAndEvidenceIsPreserved()
      throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var primary = new Neo4jRepositoryOperationException(saveContext(ast), Neo4jRepositoryPhase.ACQUISITION, CommitKnowledge.NOT_STARTED,
        new RepositoryException("Acquisition failed", new IllegalStateException("Acquisition observation")));
    var cleanup = new Neo4jRepositoryOperationException(saveContext(ast), Neo4jRepositoryPhase.ACQUISITION, CommitKnowledge.NOT_STARTED,
        new IllegalStateException("Partial-resource close observation"));
    primary.addSuppressed(cleanup);
    var retention = AnalysisCapabilities.astRetention(ignored -> {
      throw primary;
    });

    // Act
    var result = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));

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
      var queryCapability = AnalysisCapabilities.astQuery(context -> {
        assertEquals(RepositoryOperationContext.queryAst(identity), context);
        events.add("acquisition");
        return new QueryRepository(requested -> {
          assertEquals(identity, requested);
          events.add("find");
          return result;
        }, () -> events.add("close"));
      });

      // Act
      var found = queryCapability.findAst(identity);

      // Assert
      assertSame(result, found);
      assertEquals(List.of("acquisition", "find", "close"), events);
      assertTrue(found.outcome().isEmpty());
    }
  }

  @Test
  void givenQueryFailureAndEnclosingCloseFailure_whenFinding_thenNoLookupIsReturnedAndExactQueryCarrierPropagates()
      throws IOException {
    for (var phase : List.of(Neo4jRepositoryPhase.READ, Neo4jRepositoryPhase.DECODE, Neo4jRepositoryPhase.READ_SESSION_CLOSE)) {
      // Arrange
      var ast = produce("class Stored { int value() { return 1; } }");
      var identity = ast.result().orElseThrow().identity();
      var context = RepositoryOperationContext.queryAst(identity);
      var primary = new Neo4jRepositoryOperationException(context, phase, CommitKnowledge.NOT_STARTED,
          new IllegalArgumentException("Read or admission observation"));
      var cleanup = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.NOT_STARTED,
          new RepositoryException("Close failed", new IllegalStateException("Close observation")));
      var queryCapability = AnalysisCapabilities.astQuery(ignored -> new QueryRepository(requested -> {
        throw primary;
      }, () -> {
        throw cleanup;
      }));

      // Act
      var failure = assertThrows(RepositoryOperationException.class, () -> queryCapability.findAst(identity));

      // Assert
      assertSame(primary, failure);
      assertEquals(context, failure.context());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
      assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    }
  }

  @Test
  void givenForeignOperationCarrier_whenFinding_thenExactQueryRejectsItAndRetainsClosure() throws IOException {
    var ast = produce("class Stored {}");
    var identity = ast.result().orElseThrow().identity();
    var other = produce("class Other {}").result().orElseThrow().identity();
    var query = RepositoryOperationContext.queryAst(identity);
    var foreignContexts = List.of(RepositoryOperationContext.saveAst(identity), RepositoryOperationContext.queryAst(other));
    for (var foreignContext : foreignContexts) {
      // Arrange
      var primary = new Neo4jRepositoryOperationException(foreignContext, Neo4jRepositoryPhase.VALIDATION, CommitKnowledge.NOT_STARTED,
          new IllegalArgumentException("Foreign operation observation"));
      var close = new IllegalStateException("Enclosing closure failed");
      var events = new ArrayList<String>();
      var queryCapability = AnalysisCapabilities.astQuery(context -> {
        assertEquals(query, context);
        events.add("open");
        return new QueryRepository(requested -> {
          assertEquals(identity, requested);
          events.add("find");
          throw primary;
        }, () -> {
          events.add("close");
          throw close;
        });
      });

      // Act
      var failure = assertThrows(ApplicationRepositoryOperationException.class, () -> queryCapability.findAst(identity));

      // Assert
      assertEquals(query, failure.context());
      assertEquals(Boundary.OPERATION, failure.boundary());
      assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failure.knowledge());
      var rejection = assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
      assertSame(primary, rejection.getCause());
      assertEquals(foreignContext, primary.context());
      assertEquals(0, primary.getSuppressed().length);
      assertEquals(1, failure.getSuppressed().length);
      var cleanup = assertInstanceOf(ApplicationRepositoryOperationException.class, failure.getSuppressed()[0]);
      assertEquals(query, cleanup.context());
      assertEquals(Boundary.CLOSURE, cleanup.boundary());
      assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), cleanup.knowledge());
      assertSame(close, cleanup.effectivePrimary());
      assertEquals(List.of("open", "find", "close"), events);
    }
  }

  @Test
  void givenNormalLookupAndEnclosingCloseFailure_whenFinding_thenCloseFailurePreventsNormalLookupReturn() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var identity = ast.result().orElseThrow().identity();
    var effective = new RepositoryException("Close failed", new IllegalStateException("Close observation"));
    var queryCapability = AnalysisCapabilities.astQuery(ignored -> new QueryRepository(
        requested -> new AstLookup(identity, AstLookupStatus.FOUND, Optional.of(ast), Optional.empty()), () -> {
          throw effective;
        }));

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> queryCapability.findAst(identity));

    // Assert
    assertSame(effective, failure.effectivePrimary());
    assertEquals(RepositoryOperationKind.QUERY_AST, failure.context().kind());
    assertEquals(RepositoryOperationContext.queryAst(identity), failure.context());
    assertEquals(Boundary.CLOSURE, assertInstanceOf(ApplicationRepositoryOperationException.class, failure).boundary());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
  }

  @Test
  void givenNullRequestedIdentity_whenFinding_thenArgumentAdmissionFailsBeforeAcquisition() {
    // Arrange
    var query = AnalysisCapabilities.astQuery(context -> {
      throw new AssertionError("Invalid query input cannot acquire a repository");
    });

    // Act
    var failure = assertThrows(NullPointerException.class, () -> query.findAst(null));

    // Assert
    assertEquals(0, failure.getSuppressed().length);
  }

  @Test
  void givenRepositoryReturnsNoLookupValue_whenFinding_thenValidationFailsBeforeEnclosingClose() throws IOException {
    // Arrange
    var identity = produce("class Stored {}").result().orElseThrow().identity();
    var closeFailure = new IllegalStateException("Query close failed");
    var query = AnalysisCapabilities.astQuery(ignored -> new QueryRepository(requested -> null, () -> {
      throw closeFailure;
    }));

    // Act
    var failure = assertThrows(ApplicationRepositoryOperationException.class, () -> query.findAst(identity));

    // Assert
    assertEquals(Boundary.VALIDATION, failure.boundary());
    assertEquals(RepositoryOperationContext.queryAst(identity), failure.context());
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failure.knowledge());
    assertInstanceOf(NullPointerException.class, failure.effectivePrimary());
    assertEquals(1, failure.getSuppressed().length);
    var cleanup = assertInstanceOf(ApplicationRepositoryOperationException.class, failure.getSuppressed()[0]);
    assertEquals(Boundary.CLOSURE, cleanup.boundary());
    assertEquals(failure.context(), cleanup.context());
    assertEquals(failure.knowledge(), cleanup.knowledge());
    assertSame(closeFailure, cleanup.effectivePrimary());
  }

  @Test
  void givenLookupWithAnotherRequestedIdentity_whenFinding_thenApplicationValidationRemainsTypedAndAbrupt() throws IOException {
    // Arrange
    var original = produce("class Stored {}").result().orElseThrow().identity();
    var foreign = produce("class Foreign {}").result().orElseThrow().identity();
    var events = new ArrayList<String>();
    var queryCapability = AnalysisCapabilities.astQuery(context -> new QueryRepository(
        requested -> new AstLookup(foreign, AstLookupStatus.NOT_FOUND, Optional.empty(), Optional.empty()),
        () -> events.add("close")));

    // Act
    var failure = assertThrows(ApplicationRepositoryOperationException.class, () -> queryCapability.findAst(original));

    // Assert
    assertEquals(Boundary.VALIDATION, failure.boundary());
    assertEquals(RepositoryOperationContext.queryAst(original), failure.context());
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failure.knowledge());
    assertInstanceOf(IllegalArgumentException.class, failure.effectivePrimary());
    assertEquals(List.of("close"), events);
  }

  @Test
  void givenSameQueryFailureFromFindAndClose_whenFinding_thenExactCarrierSurvivesWithoutSelfSuppression() throws IOException {
    // Arrange
    var identity = produce("class Stored {}").result().orElseThrow().identity();
    var context = RepositoryOperationContext.queryAst(identity);
    var cause = new IllegalStateException("Query work failed");
    var shared = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.READ, CommitKnowledge.NOT_STARTED, cause);
    var closes = new AtomicInteger();
    var query = AnalysisCapabilities.astQuery(ignored -> new QueryRepository(requested -> {
      throw shared;
    }, () -> {
      closes.incrementAndGet();
      throw shared;
    }));

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> query.findAst(identity));

    // Assert
    assertSame(shared, failure);
    assertSame(cause, failure.effectivePrimary());
    assertEquals(context, failure.context());
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), failure.knowledge());
    assertEquals(0, failure.getSuppressed().length);
    assertEquals(1, closes.get());
  }

  @Test
  void givenFatalPrimaryAndRuntimeCleanupFailure_whenRetaining_thenFatalFamilyAndApplicableCleanupEvidenceArePreserved() throws IOException {
    // Arrange
    var ast = produce("class Stored { int value() { return 1; } }");
    var fatal = new AssertionError("Fatal work observation");
    var cleanup = new Neo4jRepositoryOperationException(saveContext(ast), Neo4jRepositoryPhase.REPOSITORY_CLOSE, CommitKnowledge.NOT_STARTED,
        new RepositoryException("Close failed", new IllegalStateException("Close observation")));
    var retention = AnalysisCapabilities.astRetention(ignored -> new RetentionRepository(value -> {
      throw fatal;
    }, () -> {
      throw cleanup;
    }));

    // Act
    var failure = assertThrows(AssertionError.class, () -> retention.retainAst(new AstPublicationRequest(ast,
        PublicationIntent.SAVE_COMPLETE_AST)));

    // Assert
    assertSame(fatal, failure);
    assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));
  }

  private AstOutcome produce(String source) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")), source))), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
  }

  private static CompilerConfiguration configuration() {
    return new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of());
  }

  private static RepositoryOperationContext saveContext(AstOutcome ast) {
    return RepositoryOperationContext.saveAst(ast.result().orElseThrow().identity());
  }

  private record RetentionRepository(Consumer<AstOutcome> save, Runnable cleanup)
      implements AstRetentionRepository {
    @Override
    public void saveComplete(AstOutcome outcome) {
      save.accept(outcome);
    }

    @Override
    public void close() {
      cleanup.run();
    }
  }

  private record QueryRepository(Function<AstIdentity, AstLookup> lookup, Runnable cleanup) implements AstQueryRepository {
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
