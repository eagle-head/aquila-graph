package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AnalysisExecutionTest {
  @Test
  void givenRejectedRunAndSavePolicy_whenNotEligibleExecutionCreated_thenIntentAndStatusAreRetained() {
    // Arrange
    var run = TestAnalysisRuns.rejected();

    // Act
    var execution = AnalysisExecution.notEligibleForRepository(run, RepositorySavePolicy.SAVE_COMPLETED);

    // Assert
    assertSame(run, execution.run());
    assertEquals(RepositorySavePolicy.SAVE_COMPLETED, execution.repositorySavePolicy());
    assertEquals(RepositoryStatus.NOT_ELIGIBLE, execution.repositoryStatus());
  }

  @Test
  void givenCompletedRun_whenExecutionWithoutRepositoryRequestCreated_thenNoSaveDecisionIsRetained() {
    // Arrange
    var run = TestAnalysisRuns.completed();

    // Act
    var execution = AnalysisExecution.withoutRepositoryRequest(run);

    // Assert
    assertSame(run, execution.run());
    assertEquals(RepositorySavePolicy.DO_NOT_SAVE, execution.repositorySavePolicy());
    assertEquals(RepositoryStatus.NOT_REQUESTED, execution.repositoryStatus());
  }

  @Test
  void givenCompletedRun_whenSavedExecutionCreated_thenSaveOutcomeIsRetained() {
    // Arrange
    var run = TestAnalysisRuns.completed();

    // Act
    var execution = AnalysisExecution.saved(run);

    // Assert
    assertSame(run, execution.run());
    assertEquals(RepositorySavePolicy.SAVE_COMPLETED, execution.repositorySavePolicy());
    assertEquals(RepositoryStatus.SAVED, execution.repositoryStatus());
  }

  @Test
  void givenCompletedRun_whenMarkedNotEligible_thenStateIsRejected() {
    // Arrange
    var run = TestAnalysisRuns.completed();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new AnalysisExecution(run, RepositorySavePolicy.SAVE_COMPLETED, RepositoryStatus.NOT_ELIGIBLE));

    // Assert
    assertEquals("A completed analysis run is eligible for a repository decision", exception.getMessage());
  }

  @Test
  void givenRejectedRun_whenRepositoryMarkedNotRequested_thenStateIsRejected() {
    // Arrange
    var run = TestAnalysisRuns.rejected();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new AnalysisExecution(run, RepositorySavePolicy.DO_NOT_SAVE, RepositoryStatus.NOT_REQUESTED));

    // Assert
    assertEquals("Repository storage can be not requested only for a completed run with no-save policy", exception.getMessage());
  }

  @Test
  void givenCompletedRunWithSavePolicy_whenRepositoryMarkedNotRequested_thenStateIsRejected() {
    // Arrange
    var run = TestAnalysisRuns.completed();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new AnalysisExecution(run, RepositorySavePolicy.SAVE_COMPLETED, RepositoryStatus.NOT_REQUESTED));

    // Assert
    assertEquals("Repository storage can be not requested only for a completed run with no-save policy", exception.getMessage());
  }

  @Test
  void givenRejectedRun_whenRepositoryMarkedSaved_thenStateIsRejected() {
    // Arrange
    var run = TestAnalysisRuns.rejected();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new AnalysisExecution(run, RepositorySavePolicy.SAVE_COMPLETED, RepositoryStatus.SAVED));

    // Assert
    assertEquals("Repository storage requires a completed run with save-completed policy", exception.getMessage());
  }

  @Test
  void givenCompletedRunWithNoSavePolicy_whenRepositoryMarkedSaved_thenStateIsRejected() {
    // Arrange
    var run = TestAnalysisRuns.completed();

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new AnalysisExecution(run, RepositorySavePolicy.DO_NOT_SAVE, RepositoryStatus.SAVED));

    // Assert
    assertEquals("Repository storage requires a completed run with save-completed policy", exception.getMessage());
  }
}
