package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationFailure;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.RepositoryException;
import org.aquilagraph.application.port.RepositoryOperationException;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.junit.jupiter.api.Test;

class FailureGraphContractTest {
  @Test
  void givenCauseSubtreeAndLaterSharedRoot_whenFailureGraphIsCaptured_thenIdsFollowDepthFirstFirstEncounterOrder() {
    // Arrange
    var c = new IllegalStateException("c");
    var b = new IllegalStateException("b", c);
    var a = new IllegalStateException("a", b);
    var d = new IllegalStateException("d");
    var e = new IllegalStateException("e");
    a.addSuppressed(d);
    var capture = new FailureGraphCapture(failure -> Optional.empty());

    // Act
    var first = capture.capture(a);
    var shared = capture.capture(d);
    var last = capture.capture(e);
    var graph = capture.catalog();
    var document = new ReportDocument(new ReportDocument.Section(List.of()), new FailureDocumentComposer().compose(graph));
    var output = new CommandTextRenderer("\n").render(document);

    // Assert
    assertEquals(1, first);
    assertEquals(4, shared);
    assertEquals(5, last);
    assertEquals(List.of(Optional.of("a"), Optional.of("b"), Optional.of("c"), Optional.of("d"), Optional.of("e")),
        graph.nodes().stream().map(FailureCatalog.Node::message).toList());
    assertEquals(OptionalInt.of(2), graph.node(1).cause());
    assertEquals(OptionalInt.of(3), graph.node(2).cause());
    assertEquals(List.of(4), graph.node(1).suppressed());
    assertEquals(OptionalInt.empty(), graph.node(3).cause());
    assertEquals("", output.output());
    assertEquals("""
        Failure: 1
          Type: "java.lang.IllegalStateException"
          Message: "a"
          Cause: 2
          Carrier: not-applicable
          Effective primary: not-applicable
          Suppressed: 4
          Operation: not-applicable
        Failure: 2
          Type: "java.lang.IllegalStateException"
          Message: "b"
          Cause: 3
          Carrier: not-applicable
          Effective primary: not-applicable
          Suppressed: none
          Operation: not-applicable
        Failure: 3
          Type: "java.lang.IllegalStateException"
          Message: "c"
          Cause: unavailable
          Carrier: not-applicable
          Effective primary: not-applicable
          Suppressed: none
          Operation: not-applicable
        Failure: 4
          Type: "java.lang.IllegalStateException"
          Message: "d"
          Cause: unavailable
          Carrier: not-applicable
          Effective primary: not-applicable
          Suppressed: none
          Operation: not-applicable
        Failure: 5
          Type: "java.lang.IllegalStateException"
          Message: "e"
          Cause: unavailable
          Carrier: not-applicable
          Effective primary: not-applicable
          Suppressed: none
          Operation: not-applicable
        """, output.error());
  }

  @Test
  void givenCyclicCausesAndDuplicateSuppressedEdges_whenCaptured_thenFiniteCatalogRetainsEveryIdentityRelationship() {
    // Arrange
    var a = new IllegalStateException("same");
    var b = new IllegalStateException("same");
    a.initCause(b);
    b.initCause(a);
    a.addSuppressed(b);
    a.addSuppressed(b);
    var capture = new FailureGraphCapture(failure -> Optional.empty());

    // Act
    capture.capture(a);
    var graph = capture.catalog();
    b.addSuppressed(new IllegalStateException("later"));

    // Assert
    assertEquals(2, graph.nodes().size());
    assertEquals(OptionalInt.of(2), graph.node(1).cause());
    assertEquals(OptionalInt.of(1), graph.node(2).cause());
    assertEquals(List.of(2, 2), graph.node(1).suppressed());
    assertEquals(List.of(), graph.node(2).suppressed());
    assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
    assertThrows(UnsupportedOperationException.class, () -> graph.node(1).suppressed().clear());
  }

  @Test
  void givenLongFiniteCauseChain_whenCapturedAndComposed_thenTraversalDoesNotDependOnRecursivePresentationDepth() {
    // Arrange
    Throwable root = new IllegalStateException();
    var depth = 10000;
    for (var index = 0; index < depth; index++) { root = new IllegalStateException("chain", root); }
    var capture = new FailureGraphCapture(failure -> Optional.empty());

    // Act
    capture.capture(root);
    var graph = capture.catalog();
    var document = new ReportDocument(new ReportDocument.Section(List.of()), new FailureDocumentComposer().compose(graph));
    var output = new CommandTextRenderer("\n").render(document);

    // Assert
    assertEquals(depth + 1, graph.nodes().size());
    assertEquals(OptionalInt.of(depth + 1), graph.node(depth).cause());
    assertEquals(OptionalInt.empty(), graph.node(depth + 1).cause());
    assertEquals(Optional.empty(), graph.node(depth + 1).message());
    assertEquals(depth + 1, output.error().lines().filter(line -> line.startsWith("Failure: ")).count());
    assertTrue(output.error().endsWith("  Operation: not-applicable\n"));
  }

  @Test
  void givenPublicationCarrierAndCleanupWithDifferentObservation_whenCaptured_thenKnowledgeAndContextRemainOnTheirOwnNodes() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var save = RepositoryOperationContext.saveCompletedRun(run.identity().key());
    var cleanupContext = RepositoryOperationContext.saveAst(run.representation().ast().result().orElseThrow().identity());
    var primary = new RepositoryException("replacement failed", new IOException("transport"));
    var carrier = new ObservedOperationFailure(save, Optional.of(CommitKnowledge.KNOWN_COMMIT), primary);
    var cleanup = new ObservedOperationFailure(cleanupContext, Optional.empty(), new IllegalStateException("cleanup"));
    carrier.addSuppressed(cleanup);
    var wrapper = new PublicationFailure(carrier);
    var capture = new FailureGraphCapture(failure -> Optional.empty());

    // Act
    capture.capture(wrapper);
    var graph = capture.catalog();

    // Assert
    assertSame(primary, wrapper.effectivePrimary());
    assertEquals(OptionalInt.of(2), graph.node(1).cause());
    assertEquals(OptionalInt.of(2), graph.node(1).carrier());
    assertEquals(OptionalInt.of(3), graph.node(1).effectivePrimary());
    assertEquals(List.of(5), graph.node(2).suppressed());
    var saveObservation = graph.node(2).operation().orElseThrow();
    var cleanupObservation = graph.node(5).operation().orElseThrow();
    assertEquals(save, saveObservation.context());
    assertEquals(Optional.of(CommitKnowledge.KNOWN_COMMIT), saveObservation.knowledge());
    assertEquals(cleanupContext, cleanupObservation.context());
    assertEquals(Optional.empty(), cleanupObservation.knowledge());
    assertEquals(Optional.empty(), cleanupObservation.adapter());
    assertEquals(Optional.empty(), cleanupObservation.boundary());
    assertEquals(Optional.empty(), graph.node(3).operation());
  }

  @Test
  void givenAbruptPublicationWrapperInsideCapturedGraph_whenProjected_thenDeclaredCarrierSurvivesItsDifferentCauseAccessor() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var primary = new IllegalStateException("acquisition interrupted");
    var wrapper = assertThrows(AnalysisPublicationException.class, () -> AnalysisExecutionFixture.executeComputed(run,
        PublicationIntent.SAVE_COMPLETED, AnalysisExecutionFixture::unexpectedAstRepository, context -> { throw primary; }));
    var capture = new FailureGraphCapture(failure -> Optional.empty());

    // Act
    capture.capture(wrapper);
    var graph = capture.catalog();

    // Assert
    assertSame(primary, wrapper.getCause());
    assertEquals(3, graph.nodes().size());
    assertEquals(OptionalInt.of(2), graph.node(1).cause());
    assertEquals(OptionalInt.of(3), graph.node(1).carrier());
    assertEquals(OptionalInt.of(2), graph.node(1).effectivePrimary());
    var observation = graph.node(3).operation().orElseThrow();
    assertEquals(wrapper.context(), observation.context());
    assertEquals(Optional.of(Boundary.ACQUISITION), observation.boundary());
    assertEquals(Optional.of(CommitKnowledge.NOT_STARTED), observation.knowledge());
  }

  @Test
  void givenAbruptPublicationWithSharedAndCyclicFailures_whenThrowablesChangeAfterCapture_thenRunnerEmitsOnlyDetachedEvidence() {
    // Arrange
    var run = CliTestFixtures.completedRun();
    var originalMessage = "primary\n\"quoted\"";
    var primary = new MutableMessageFailure(originalMessage);
    var shared = new IllegalStateException("shared");
    var leaf = new IllegalStateException("leaf");
    primary.initCause(shared);
    shared.initCause(primary);
    primary.addSuppressed(shared);
    primary.addSuppressed(shared);
    primary.addSuppressed(leaf);
    var publication = assertThrows(AnalysisPublicationException.class, () -> AnalysisExecutionFixture.executeComputed(run,
        PublicationIntent.SAVE_COMPLETED, AnalysisExecutionFixture::unexpectedAstRepository, context -> { throw primary; }));
    var report = new AtomicReference<CommandReport>();
    var added = new IllegalStateException("added after capture");
    var composer = new CommandDocumentComposer() {
      @Override
      ReportDocument compose(CommandReport captured) {
        report.set(captured);
        primary.message = "changed after capture";
        leaf.initCause(added);
        publication.failure().addSuppressed(added);
        return super.compose(captured);
      }
    };
    var command = new AnalyzeCommand(new AnalyzeCommandParser(JavaRelease.JAVA_25),
        new AnalyzeCommandExecutor(request -> { throw publication; }), new CommandReportProjector(failure -> Optional.empty()),
        composer, new CommandTextRenderer("\r\n"));
    var runner = new CommandRunner(command);
    var streams = new CliStreams();
    try (var output = streams.output(); var error = streams.error()) {
      // Act
      var observed = assertThrows(AnalysisPublicationException.class, () -> runner.run(new String[] {"source", "--persist"},
          CommandDestination.printStream(output), CommandDestination.printStream(error)));

      // Assert
      assertSame(publication, observed);
      assertEquals("changed after capture", primary.getMessage());
      assertSame(added, leaf.getCause());
      assertSame(added, publication.failure().getSuppressed()[0]);
      var captured = assertInstanceOf(CommandReport.Failed.class, report.get());
      assertEquals(CommandReport.FailureKind.PUBLICATION, captured.kind());
      var graph = captured.failures();
      assertEquals(5, graph.nodes().size());
      assertEquals(OptionalInt.of(5), graph.node(captured.failureReference()).carrier());
      assertEquals(OptionalInt.of(2), graph.node(captured.failureReference()).effectivePrimary());
      assertEquals(Optional.of(originalMessage), graph.node(2).message());
      assertEquals(OptionalInt.of(3), graph.node(2).cause());
      assertEquals(OptionalInt.of(2), graph.node(3).cause());
      assertEquals(List.of(3, 3, 4), graph.node(2).suppressed());
      assertEquals(OptionalInt.empty(), graph.node(4).cause());
      assertEquals(List.of(), graph.node(5).suppressed());
      assertEquals("", streams.outputText());
      assertTrue(streams.errorLines().contains("  Message: \"primary\\n\\\"quoted\\\"\""));
      assertEquals(2L, streams.errorLines().stream().filter("  Suppressed: 3"::equals).count());
      assertEquals(String.join("\r\n", streams.errorLines()) + "\r\n", streams.errorText());
      assertTrue(streams.errorLines().stream().noneMatch(line -> line.contains("after capture")));
    }
  }

  private static final class MutableMessageFailure extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private String message;

    MutableMessageFailure(String message) {
      this.message = message;
    }

    @Override
    public String getMessage() {
      return message;
    }
  }

  private static final class ObservedOperationFailure extends RepositoryOperationException {
    @Serial private static final long serialVersionUID = 1L;
    ObservedOperationFailure(RepositoryOperationContext context, Optional<CommitKnowledge> knowledge, RuntimeException primary) {
      super(context, knowledge, primary);
    }
  }

  @Test
  void givenDistinctExceptionsWithEqualValues_whenCaptured_thenEqualityCannotMergeTheirLocalIdentities() {
    // Arrange
    var first = new EqualFailure();
    var second = new EqualFailure();
    var capture = new FailureGraphCapture(failure -> Optional.empty());

    // Act
    var firstReference = capture.capture(first);
    var secondReference = capture.capture(second);

    // Assert
    assertEquals(first, second);
    assertNotSame(first, second);
    assertEquals(1, firstReference);
    assertEquals(2, secondReference);
    assertEquals(2, capture.catalog().nodes().size());
  }

  private static final class EqualFailure extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;
    EqualFailure() { super("equal value"); }
    @Override public boolean equals(Object other) { return other instanceof EqualFailure; }
    @Override public int hashCode() { return 1; }
  }
}
