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
import java.util.function.Consumer;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.application.port.RepositoryException;
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
    var publisher = new CompletedRunPublicationService(() -> {
      throw new AssertionError("Incomplete body coverage must not acquire a repository");
    });

    // Act and Assert
    for (var run : List.of(astOnly, selectedEmpty)) {
      assertEquals(AnalysisCompletion.COMPLETE, run.completion());
      var publication = publisher.publish(run);
      assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
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
    var publisher = new CompletedRunPublicationService(() -> {
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
    var publisher = new CompletedRunPublicationService(() -> new RepositoryStub(value -> {
      throw primary;
    }, () -> {
      throw cleanup;
    }));

    // Act
    var failure = assertThrows(AnalysisRepositoryException.class, () -> publisher.publish(run));

    // Assert
    assertSame(run, failure.analysisRun());
    assertSame(primary, failure.repositoryFailure());
    assertSame(primary, failure.getCause());
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
    assertSame(run, failure.analysisRun());
  }

  @Test
  void givenUnmatchedSaveFailureAndDeclaredCleanup_whenPublishing_thenOriginalRuntimeFamilyRemainsAbrupt() throws IOException {
    // Arrange
    var run = fullRun(produce("interface Stored { int value(); }"));
    var primary = new IllegalArgumentException("Unmatched work observation");
    var cleanup = new RepositoryException("Close observation", new IllegalStateException("Cleanup observation"));
    var publisher = new CompletedRunPublicationService(() -> new RepositoryStub(value -> {
      throw primary;
    }, () -> {
      throw cleanup;
    }));

    // Act and Assert
    assertSame(primary, assertThrows(IllegalArgumentException.class, () -> publisher.publish(run)));
    assertEquals(List.of(cleanup), List.of(primary.getSuppressed()));
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
