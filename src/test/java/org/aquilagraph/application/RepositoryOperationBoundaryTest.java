package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.internal.repository.RepositoryOperationBoundary;
import org.aquilagraph.application.port.RepositoryOperationException;
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
        () -> { throw primary; }, RepositoryOperationBoundaryTest::observeFailure));

    // Assert
    assertSame(primary, failure.effectivePrimary());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
    assertEquals(context, failure.context());
    assertEquals(Boundary.OPERATION, ((ApplicationRepositoryOperationException) failure).boundary());
  }

  @Test
  void givenDeclaredCarrier_whenObservedAtAnOuterBoundary_thenExactCreatingPhaseAndCarrierAreRetained() {
    // Arrange
    var primary = new ApplicationRepositoryOperationException(context(), Boundary.CLOSURE, Optional.of(CommitKnowledge.NOT_STARTED),
        new IllegalArgumentException("Declared close observation"));

    // Act
    var failure = assertThrows(RepositoryOperationException.class, () -> RepositoryOperationBoundary.observe(context(),
        () -> { throw primary; }, RepositoryOperationBoundaryTest::observeFailure));

    // Assert
    assertSame(primary, failure);
  }

  @Test
  void givenFatalPrimary_whenObserved_thenOriginalErrorIsRethrownWithoutRuntimeRelabeling() {
    // Arrange
    var fatal = new AssertionError("Fatal primitive observation");

    // Act
    var failure = assertThrows(AssertionError.class, () -> RepositoryOperationBoundary.observe(context(),
        () -> { throw fatal; }, value -> { throw new AssertionError("Fatal failures cannot be translated"); }));

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
      var result = RepositoryOperationBoundary.observe(context(), Thread::currentThread, RepositoryOperationBoundaryTest::observeFailure);

      // Assert
      assertSame(caller, result);
      assertTrue(caller.isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void givenSameFatalErrorFromWorkAndCleanup_whenCompletingOwnedWork_thenTheOriginalErrorStillPropagates() {
    // Arrange
    var fatal = new LinkageError("Fatal driver operation and cleanup");
    var cleanupCalls = new AtomicInteger();

    // Act
    var observed = assertThrows(LinkageError.class, () -> RepositoryOperationBoundary.withResource(cleanupCalls, resource -> {
      throw fatal;
    }, resource -> {
      resource.incrementAndGet();
      throw fatal;
    }));

    // Assert
    assertSame(fatal, observed);
    assertEquals(1, cleanupCalls.get());
    assertEquals(0, observed.getSuppressed().length);
  }

  private static RepositoryOperationException observeFailure(RuntimeException primary) {
    return new ApplicationRepositoryOperationException(context(), Boundary.OPERATION, Optional.empty(), primary);
  }

  private static RepositoryOperationContext context() {
    return RepositoryOperationContext.saveAst(AstIdentity.fromContent("software", "java-source/1", "java-source-bounded/1", "javac-public/1", "content"));
  }
}
