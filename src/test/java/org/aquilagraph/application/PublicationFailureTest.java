package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.ComputedPublicationSubject.Coordinated;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class PublicationFailureTest {
  @Test
  void givenFailureCarriersAndPublicationEnvelope_whenRestoringNativeSerialization_thenDomainAdmissionIsRequired() throws IOException {
    // Arrange
    var run = TestAnalysisRuns.completed();
    var context = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var primary = new RepositoryException("repository unavailable", new IllegalStateException("connection refused"));
    var declared = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.UNKNOWN, primary);
    var observed = new ApplicationRepositoryOperationException(context, Boundary.OPERATION, Optional.empty(), primary);
    var envelope = new AnalysisPublicationException(new Coordinated(run), observed);
    var encoded = List.of(serialize(declared), serialize(observed), serialize(new PublicationFailure(declared)), serialize(envelope));

    // Act
    var failures = encoded.stream().map(bytes -> assertThrows(InvalidObjectException.class, () -> restore(bytes))).toList();

    // Assert
    for (var failure : failures) {
      assertEquals("Repository operation requires an admitted exact subject", failure.getMessage());
    }
  }

  @Test
  void givenQueryContext_whenFailureKnowledgeIsAdmitted_thenOnlyPresentNotStartedIsAccepted() {
    // Arrange
    var context = RepositoryOperationContext.queryAst(astIdentity());
    var primary = new IllegalStateException("lookup failed");

    // Act
    var valid = new ApplicationRepositoryOperationException(context, Boundary.OPERATION, Optional.of(CommitKnowledge.NOT_STARTED), primary);
    assertThrows(IllegalArgumentException.class, () -> new ApplicationRepositoryOperationException(context, Boundary.OPERATION,
        Optional.empty(), primary));
    for (var invalid : List.of(CommitKnowledge.UNKNOWN, CommitKnowledge.KNOWN_COMMIT, CommitKnowledge.KNOWN_NONCOMMIT)) {
      assertThrows(IllegalArgumentException.class, () -> new ApplicationRepositoryOperationException(context, Boundary.OPERATION,
          Optional.of(invalid), primary));
    }

    // Assert
    assertEquals(context, valid.context());
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), valid.knowledge());
    assertSame(primary, valid.effectivePrimary());
  }

  @Test
  void givenOperationAndSubjectInDifferentNamespaces_whenCreatingContext_thenEveryContradictoryPairIsRejected() {
    // Arrange
    var source = new Ast(astIdentity());
    var run = new CompletedRun("exact-run");
    var astOperation = RepositoryOperationKind.SAVE_AST;
    var runOperation = RepositoryOperationKind.SAVE_COMPLETED_RUN;

    // Act
    var savedSource = new RepositoryOperationContext(astOperation, source);
    var savedRun = new RepositoryOperationContext(runOperation, run);

    // Assert
    assertSame(source, savedSource.subject());
    assertSame(run, savedRun.subject());
    assertThrows(IllegalArgumentException.class, () -> new RepositoryOperationContext(astOperation, run));
    assertThrows(IllegalArgumentException.class, () -> new RepositoryOperationContext(RepositoryOperationKind.QUERY_AST, run));
    assertThrows(IllegalArgumentException.class, () -> new RepositoryOperationContext(runOperation, source));
  }

  @Test
  void givenDeclaredSaveObservation_whenAssemblingFailedPublication_thenExactSubjectAndKnowledgeAreRequired() {
    // Arrange
    var context = RepositoryOperationContext.saveCompletedRun("exact-run");
    var primary = new RepositoryException("Write observation", new IllegalStateException("Driver observation"));
    var declared = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.UNKNOWN, primary);
    var failure = Optional.of(new PublicationFailure(declared));

    // Act
    var admitted = new PublicationOutcome(PublicationIntent.SAVE_COMPLETED, Optional.of(context.subject()), PublicationStatus.FAILED,
        CommitKnowledge.UNKNOWN, failure);

    // Assert
    assertSame(declared, admitted.failure().orElseThrow().carrier());
    assertThrows(IllegalArgumentException.class, () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETED,
        Optional.of(new CompletedRun("foreign-run")), PublicationStatus.FAILED, CommitKnowledge.UNKNOWN, failure));
    assertThrows(IllegalArgumentException.class, () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETED,
        Optional.of(context.subject()), PublicationStatus.FAILED, CommitKnowledge.KNOWN_NONCOMMIT, failure));
    assertThrows(IllegalArgumentException.class, () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETED,
        Optional.empty(), PublicationStatus.NOT_ELIGIBLE, CommitKnowledge.NOT_STARTED, Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new PublicationFailure(
        new ApplicationRepositoryOperationException(context, Boundary.OPERATION, Optional.empty(), primary)));
  }

  @Test
  void givenOneContradictoryResultField_whenConstructingPublicationOutcome_thenEveryForbiddenShapeIsRejected() {
    // Arrange
    var context = RepositoryOperationContext.saveAst(astIdentity());
    var subject = Optional.of(context.subject());
    var effective = new RepositoryException("Declared save failure", new IllegalStateException("Driver observation"));
    var declared = new Neo4jRepositoryOperationException(context, Neo4jRepositoryPhase.REPLACEMENT, CommitKnowledge.KNOWN_COMMIT, effective);
    var failure = Optional.of(new PublicationFailure(declared));
    var invalid = List.<Executable>of(
        () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subject, PublicationStatus.FAILED,
            CommitKnowledge.KNOWN_COMMIT, Optional.empty()),
        () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, Optional.empty(), PublicationStatus.FAILED,
            CommitKnowledge.KNOWN_COMMIT, failure),
        () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subject, PublicationStatus.SAVED,
            CommitKnowledge.KNOWN_COMMIT, failure),
        () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, Optional.empty(), PublicationStatus.SAVED,
            CommitKnowledge.KNOWN_COMMIT, Optional.empty()),
        () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subject, PublicationStatus.SAVED,
            CommitKnowledge.UNKNOWN, Optional.empty()),
        () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subject, PublicationStatus.NOT_ELIGIBLE,
            CommitKnowledge.UNKNOWN, Optional.empty()),
        () -> new PublicationOutcome(PublicationIntent.DO_NOT_SAVE, subject, PublicationStatus.NOT_REQUESTED,
            CommitKnowledge.KNOWN_NONCOMMIT, Optional.empty()),
        () -> new PublicationOutcome(PublicationIntent.DO_NOT_SAVE, subject, PublicationStatus.NOT_ELIGIBLE,
            CommitKnowledge.NOT_STARTED, Optional.empty()),
        () -> new PublicationOutcome(PublicationIntent.DO_NOT_SAVE, Optional.of(new CompletedRun("exact-run")),
            PublicationStatus.NOT_REQUESTED, CommitKnowledge.NOT_STARTED, Optional.empty()),
        () -> new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subject, PublicationStatus.NOT_REQUESTED,
            CommitKnowledge.NOT_STARTED, Optional.empty()));

    // Act
    var saved = new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subject, PublicationStatus.SAVED,
        CommitKnowledge.KNOWN_COMMIT, Optional.empty());
    var failed = new PublicationOutcome(PublicationIntent.SAVE_COMPLETE_AST, subject, PublicationStatus.FAILED,
        CommitKnowledge.KNOWN_COMMIT, failure);
    var noSaveWithAst = new PublicationOutcome(PublicationIntent.DO_NOT_SAVE, subject, PublicationStatus.NOT_REQUESTED,
        CommitKnowledge.NOT_STARTED, Optional.empty());
    var noSaveWithoutAst = new PublicationOutcome(PublicationIntent.DO_NOT_SAVE, Optional.empty(), PublicationStatus.NOT_REQUESTED,
        CommitKnowledge.NOT_STARTED, Optional.empty());

    // Assert
    assertEquals(PublicationStatus.SAVED, saved.status());
    assertEquals(subject, noSaveWithAst.subjectIdentity());
    assertEquals(Optional.empty(), noSaveWithoutAst.subjectIdentity());
    assertSame(declared, failed.failure().orElseThrow().carrier());
    for (var invalidShape : invalid) {
      assertThrows(IllegalArgumentException.class, invalidShape);
    }
  }

  @Test
  void givenQueryOrUnmatchedPrimaryWithKnowledge_whenCreatingDeclaredPublicationFailure_thenNeitherCarrierIsAdmitted() {
    // Arrange
    var query = new ApplicationRepositoryOperationException(RepositoryOperationContext.queryAst(astIdentity()), Boundary.OPERATION,
        Optional.of(CommitKnowledge.NOT_STARTED),
        new RepositoryException("Read failed", new IllegalStateException("Driver observation")));
    var unmatched = new ApplicationRepositoryOperationException(RepositoryOperationContext.saveAst(astIdentity()), Boundary.OPERATION,
        Optional.of(CommitKnowledge.UNKNOWN), new IllegalStateException("Mapping observation"));

    // Act
    var queryRejection = assertThrows(IllegalArgumentException.class, () -> new PublicationFailure(query));
    var unmatchedRejection = assertThrows(IllegalArgumentException.class, () -> new PublicationFailure(unmatched));

    // Assert
    assertEquals(queryRejection.getMessage(), unmatchedRejection.getMessage());
  }

  private static AstIdentity astIdentity() {
    return AstIdentity.fromContent("software", "java-source/1", "java-source-bounded/1", "javac-public/1", "content");
  }

  private static void restore(byte[] bytes) throws IOException, ClassNotFoundException {
    try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      input.readObject();
    }
  }

  private static byte[] serialize(RuntimeException original) throws IOException {
    try (var buffer = new ByteArrayOutputStream(); var output = new ObjectOutputStream(buffer)) {
      output.writeObject(original);
      output.flush();
      return buffer.toByteArray();
    }
  }
}
