package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisExecutionTest {
  @Test
  void givenRejectedRun_whenPublicationDeclines_thenExactRunAndDecisionAreRetained() {
    // Arrange
    var run = TestAnalysisRuns.rejected();
    // Act
    var execution = execution(run, PublicationIntent.SAVE_COMPLETED, PublicationStatus.NOT_ELIGIBLE);
    // Assert
    assertSame(run, execution.run());
    assertEquals(PublicationIntent.SAVE_COMPLETED, execution.publication().intent());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, execution.publication().status());
  }

  @Test
  void givenCompletedRun_whenNoStorageIsRequested_thenNoSaveIntentIsRetained() {
    // Arrange
    var run = TestAnalysisRuns.completed();
    // Act
    var execution = execution(run, PublicationIntent.DO_NOT_SAVE, PublicationStatus.NOT_REQUESTED);
    // Assert
    assertSame(run, execution.run());
    assertEquals(PublicationStatus.NOT_REQUESTED, execution.publication().status());
  }

  @Test
  void givenCompletedRun_whenSaveIsAcknowledged_thenExactSubjectAndCommitAreRetained() {
    // Arrange
    var run = TestAnalysisRuns.completed();
    // Act
    var execution = execution(run, PublicationIntent.SAVE_COMPLETED, PublicationStatus.SAVED);
    // Assert
    assertSame(run, execution.run());
    assertEquals(new PublicationSubjectIdentity.CompletedRun(run.identity().key()), execution.publication().subjectIdentity().orElseThrow());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, execution.publication().knowledge());
  }

  @Test
  void givenCompleteFullCoverage_whenPublicationClaimsIneligible_thenContradictionIsRejected() {
    // Arrange
    var run = TestAnalysisRuns.completed();
    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> execution(run, PublicationIntent.SAVE_COMPLETED, PublicationStatus.NOT_ELIGIBLE));
    // Assert
    assertTrue(failure.getMessage().contains("complete source"));
  }

  @Test
  void givenUnavailableSource_whenNoStorageIsRequested_thenSourceFailureAndNoSaveDecisionRemainIndependent() {
    // Arrange
    var run = TestAnalysisRuns.rejected();
    // Act
    var execution = execution(run, PublicationIntent.DO_NOT_SAVE, PublicationStatus.NOT_REQUESTED);
    // Assert
    assertEquals(AnalysisCompletion.REJECTED, execution.run().completion());
    assertTrue(execution.publication().subjectIdentity().isEmpty());
    assertEquals(PublicationStatus.NOT_REQUESTED, execution.publication().status());
  }

  @Test
  void givenSaveIntent_whenNoRequestStatusIsSupplied_thenContradictoryIntentIsRejected() {
    // Arrange
    var run = TestAnalysisRuns.completed();
    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> execution(run, PublicationIntent.SAVE_COMPLETED, PublicationStatus.NOT_REQUESTED));
    // Assert
    assertNotNull(failure.getMessage());
  }

  @Test
  void givenRejectedRun_whenPublicationClaimsSaved_thenMissingCoverageIsRejected() {
    // Arrange
    var run = TestAnalysisRuns.rejected();
    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> execution(run, PublicationIntent.SAVE_COMPLETED, PublicationStatus.SAVED));
    // Assert
    assertTrue(failure.getMessage().contains("complete source"));
  }

  @Test
  void givenNoSaveIntent_whenPublicationClaimsSaved_thenContradictoryCommitIsRejected() {
    // Arrange
    var run = TestAnalysisRuns.completed();
    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> execution(run, PublicationIntent.DO_NOT_SAVE, PublicationStatus.SAVED));
    // Assert
    assertNotNull(failure.getMessage());
  }

  private static AnalysisExecution execution(AnalysisRun run, PublicationIntent intent, PublicationStatus status) {
    Optional<PublicationSubjectIdentity> subject = intent == PublicationIntent.SAVE_COMPLETED
        ? Optional.of(new PublicationSubjectIdentity.CompletedRun(run.identity().key()))
        : run.representation().ast().result().map(ast -> new PublicationSubjectIdentity.Ast(ast.identity()));
    return new AnalysisExecution(run, new PublicationOutcome(intent, subject, status,
        status == PublicationStatus.SAVED ? CommitKnowledge.KNOWN_COMMIT : CommitKnowledge.NOT_STARTED, Optional.empty()));
  }
}
