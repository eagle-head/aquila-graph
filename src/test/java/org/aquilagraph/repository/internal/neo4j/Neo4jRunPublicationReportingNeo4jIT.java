package org.aquilagraph.repository.internal.neo4j;

import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.await;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.cleanup;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.completedRun;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.directUri;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.expectedProjection;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.loopbackTarget;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.storedProjection;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.storedState;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.withPublicationWarning;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.CliPublicationReporting;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.cli.ChannelEmission;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.repository.internal.neo4j.Neo4jCloseObservation.Phase;
import org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.DriverWork;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
class Neo4jRunPublicationReportingNeo4jIT {
  @TempDir Path sourceRoot;

  @Test
  void givenCommitResponseWithheldOnLoopbackBolt_whenPublicationFails_thenDirectQueriesObserveWholeReplacementAndIndependentRun()
      throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var target = loopbackTarget(settings.uri());
    var directUri = directUri(target);
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var primary = completedFixture("Acknowledgement" + UUID.randomUUID().toString().replace("-", ""), "previous");
    var independent = completedFixture("Independent" + UUID.randomUUID().toString().replace("-", ""), "independent");
    var replacement = withWarning(primary, "replacement");
    var mapper = new Neo4jAnalysisRunMapper();
    var expectedReplacement = expectedProjection(mapper.map(replacement));
    var runIds = List.of(primary.identity().key(), independent.identity().key());

    try (var verification = new DriverWork(directUri, settings)) {
      await(verification.driver.verifyConnectivityAsync());
      try {
        try (var seeding = new DriverWork(directUri, settings)) {
          seeding.call(() -> {
            try (var repository = new Neo4jAnalysisRunRepository(seeding.driver, sessionConfig,
                RepositoryOperationContext.saveCompletedRun(primary.identity().key()), mapper::map)) {
              repository.save(primary);
              repository.save(independent);
            }
            return null;
          });
        }
        var prior = storedState(verification.driver, sessionConfig, primary.identity().key());
        var independentBefore = storedState(verification.driver, sessionConfig, independent.identity().key());
        assertEquals(expectedProjection(mapper.map(primary)), prior);
        assertEquals(expectedProjection(mapper.map(independent)), independentBefore);
        assertNotEquals(prior, expectedReplacement);

        try (var proxy = new BoltCommitAcknowledgementProxy(target);
            var publication = new DriverWork(proxy.uri(), settings, proxy::disconnect)) {
          AnalysisRunRepositoryFactory publicationService = context -> new Neo4jAnalysisRunRepository(publication.driver, sessionConfig, context, mapper::map);
          proxy.arm();

          // Act
          var returned = new AtomicReference<AnalysisExecution>();
          var reporting = publication.call(() -> CliPublicationReporting.run(replacement, publicationService, returned));
          var outcome = returned.get().publication();

          // Assert
          assertEquals(3, reporting.invocation().exitCode());
          assertEquals(ChannelEmission.Status.COMPLETED, reporting.invocation().emission().output());
          assertEquals(ChannelEmission.Status.COMPLETED, reporting.invocation().emission().error());
          reporting.assertPublication("UNKNOWN", "REPLACEMENT");
          assertEquals(PublicationStatus.FAILED, outcome.status());
          assertEquals(CommitKnowledge.UNKNOWN, outcome.knowledge());
          var failure = outcome.failure().orElseThrow();
          assertEquals(RepositoryOperationContext.saveCompletedRun(replacement.identity().key()), failure.carrier().context());
          assertEquals(Neo4jRepositoryPhase.REPLACEMENT, assertInstanceOf(Neo4jRepositoryOperationException.class, failure.carrier()).phase());
          assertInstanceOf(Neo4jException.class, failure.effectivePrimary().getCause());
          proxy.requireHealthyRelay();
          var observation = proxy.observation();
          System.out.println("BOLT_COMMIT_ACKNOWLEDGEMENT_OBSERVATION=" + observation);
          assertNotEquals("not negotiated", observation.negotiatedVersion());
          assertEquals(1, observation.beginMessages());
          assertEquals(1, observation.commitMessages());
          assertEquals(1, observation.withheldCommitSuccesses());
          assertEquals(0, observation.forwardedCommitResponses());
          var observedReplacement = storedState(verification.driver, sessionConfig, primary.identity().key());
          assertEquals(expectedReplacement, observedReplacement);
          assertNotEquals(prior, observedReplacement);
          assertEquals(independentBefore, storedState(verification.driver, sessionConfig, independent.identity().key()));
        }
      } finally {
        Neo4jPublicationObservation.cleanup(verification.driver, sessionConfig, runIds);
      }
    }
  }

  @Test
  void givenDistinctStoredRuns_whenReplacementCommitsBeforeCloseFails_thenKnownCommitAndReplacementSurvive()
      throws IOException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var mapper = new Neo4jAnalysisRunMapper();

    for (var phase : Phase.values()) {
      var sourceRoot = Files.createDirectories(this.sourceRoot.resolve(phase.name()));
      var original = completedRun(sourceRoot, "PublishedSample" + UUID.randomUUID().toString().replace("-", ""));
      var independentRoot = Files.createDirectory(sourceRoot.resolve("independent"));
      var independent = completedRun(independentRoot, "Independent" + UUID.randomUUID().toString().replace("-", ""));
      var previous = withPublicationWarning(original, "previous");
      var run = withPublicationWarning(original, "replacement");
      var expectedReplacement = expectedProjection(mapper.map(run));
      var runId = run.identity().key();
      var independentId = independent.identity().key();
      var context = RepositoryOperationContext.saveCompletedRun(runId);
      var events = new ArrayList<String>();
      var signal = new Neo4jException("Close outcome signal");
      try (var actualDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()))) {
        try (var repository = Neo4jRepositories.completedRuns(() -> settings).open(context)) {
          repository.save(previous);
          repository.save(independent);
        }
        final StoredPublication establishedProjection;
        final StoredPublication independentProjection;
        try (var verificationSession = actualDriver.session(sessionConfig)) {
          establishedProjection = storedProjection(verificationSession, runId);
          independentProjection = storedProjection(verificationSession, independentId);
          assertEquals(expectedProjection(mapper.map(previous)), establishedProjection);
          assertEquals(expectedProjection(mapper.map(independent)), independentProjection);
          assertNotEquals(establishedProjection, expectedReplacement);
        }
        var delegatedDriver = Neo4jCloseObservation.signalPublicationClose(actualDriver, phase, signal, events);
        AnalysisRunRepositoryFactory publisher = operation -> new Neo4jAnalysisRunRepository(delegatedDriver, sessionConfig, operation, mapper::map);

        // Act
        var returned = new AtomicReference<AnalysisExecution>();
        var reporting = CliPublicationReporting.runWithOutputFailure(run, publisher, returned);
        var execution = returned.get();

        // Assert
        assertEquals(3, reporting.invocation().exitCode());
        assertInstanceOf(ChannelEmission.IoFailure.class, reporting.invocation().emission().output());
        assertEquals(ChannelEmission.Status.COMPLETED, reporting.invocation().emission().error());
        reporting.assertPublication("KNOWN_COMMIT", phase.repositoryPhase().name());
        var outcome = execution.publication();
        assertSame(run, execution.run());
        assertEquals(PublicationStatus.FAILED, outcome.status());
        assertEquals(CommitKnowledge.KNOWN_COMMIT, outcome.knowledge());
        assertEquals(new CompletedRun(runId), outcome.subjectIdentity().orElseThrow());
        var failure = outcome.failure().orElseThrow();
        assertEquals(context, failure.carrier().context());
        assertEquals(phase.repositoryPhase(),
            assertInstanceOf(Neo4jRepositoryOperationException.class, failure.carrier()).phase());
        assertSame(signal, failure.effectivePrimary().getCause());
        assertEquals(List.of("schema.close", "publication.write.returned", "publication.close", "driver.close"), events);
        try (var verificationDriver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
            var verificationSession = verificationDriver.session(sessionConfig)) {
          assertEquals(expectedReplacement, storedProjection(verificationSession, runId));
          assertEquals(independentProjection, storedProjection(verificationSession, independentId));
        }
      } finally {
        cleanup(settings, List.of(runId, independentId));
      }
    }
  }

  private AnalysisRun completedFixture(String typeName, String warningCode) {
    var source = "class " + typeName + " { int value(int input) { return input; } }";
    return withWarning(RepositoryTestRuns.analyze(sourceRoot, typeName + ".java", source), warningCode);
  }

  private static AnalysisRun withWarning(AnalysisRun run, String suffix) {
    var origin = run.representation().ast().result().orElseThrow().program().types().getFirst().origins().getFirst();
    var warning = new AnalysisDiagnostic(StableId.sha256("diagnostic", run.identity().key(), suffix), "publication.acknowledgement." + suffix,
        DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING, "Supplied publication warning: " + suffix, List.of(origin),
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    return AnalysisRun.create(run.identity(), run.representation(), List.of(warning));
  }

}
