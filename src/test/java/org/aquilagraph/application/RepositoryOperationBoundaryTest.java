package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.aquilagraph.application.port.RepositoryOperationBoundary;
import org.aquilagraph.identity.api.AstIdentity;
import org.junit.jupiter.api.Test;

class RepositoryOperationBoundaryTest {
  @Test
  void givenUnmatchedRuntimePrimaryWithExistingCleanup_whenObserved_thenExactNativeFamilyAndChainSurvive() {
    // Arrange
    var primary = new RuntimeException("Unmatched primitive runtime family");
    var cleanup = new IllegalStateException("Earlier primitive cleanup");
    primary.addSuppressed(cleanup);
    var context = context();

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> RepositoryOperationBoundary.observe(context,
        RepositoryPhase.MAPPING, () -> CommitKnowledge.NOT_STARTED, () -> { throw primary; }, value -> value));

    // Assert
    assertSame(primary, failure.effectivePrimary());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
    assertEquals(context, failure.context());
    assertEquals(RepositoryPhase.MAPPING, failure.phase());
  }

  @Test
  void givenDeclaredCarrier_whenObservedAtAnOuterBoundary_thenExactCreatingPhaseAndCarrierAreRetained() {
    // Arrange
    var primary = new RepositoryOperationException(context(), RepositoryPhase.SCHEMA_SESSION_CLOSE, CommitKnowledge.NOT_STARTED,
        new IllegalArgumentException("Declared close observation"));

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> RepositoryOperationBoundary.observe(context(),
        RepositoryPhase.REPOSITORY_CLOSE, () -> CommitKnowledge.NOT_STARTED, () -> { throw primary; }, value -> value));

    // Assert
    assertSame(primary, failure);
  }

  @Test
  void givenFatalPrimary_whenObserved_thenOriginalErrorIsRethrownWithoutRuntimeRelabeling() {
    // Arrange
    var fatal = new AssertionError("Fatal primitive observation");

    // Act
    var failure = assertThrows(AssertionError.class, () -> RepositoryOperationBoundary.observe(context(), RepositoryPhase.ACQUISITION,
        () -> CommitKnowledge.NOT_STARTED, () -> { throw fatal; }, value -> { throw new AssertionError("Fatal failures cannot be translated"); }));

    // Assert
    assertSame(fatal, failure);
  }

  @Test
  void givenExistingInterruptFlag_whenExecutingSynchronously_thenCallingThreadAndFlagArePreserved() {
    // Arrange
    var caller = Thread.currentThread();
    caller.interrupt();

    try {
      // Act
      var result = RepositoryOperationBoundary.observe(context(), RepositoryPhase.READ, () -> CommitKnowledge.NOT_STARTED,
          Thread::currentThread, value -> value);

      // Assert
      assertSame(caller, result);
      assertTrue(caller.isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  private static RepositoryOperationContext context() {
    return new RepositoryOperationContext(RepositoryOperationKind.SAVE_AST,
        AstIdentity.fromContent("software", "java-source/1", "java-source-bounded/1", "javac-public/1", "content"));
  }
}
