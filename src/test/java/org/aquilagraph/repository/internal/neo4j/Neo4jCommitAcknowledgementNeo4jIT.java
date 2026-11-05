package org.aquilagraph.repository.internal.neo4j;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.port.AnalysisRunRepositoryFactory;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.repository.internal.neo4j.Neo4jCloseObservation.Phase;
import org.aquilagraph.repository.internal.neo4j.StoredPublication.Namespace;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.Values;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.exceptions.Neo4jException;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
class Neo4jCommitAcknowledgementNeo4jIT {
  private static final int DRIVER_WAIT_SECONDS = 10;
  private static final int PUBLICATION_WAIT_SECONDS = 30;
  private static final int WORKER_STOP_SECONDS = 5;

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
    var expectedReplacement = expectedState(mapper.map(replacement));
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
        assertEquals(expectedState(mapper.map(primary)), prior);
        assertEquals(expectedState(mapper.map(independent)), independentBefore);
        assertNotEquals(prior, expectedReplacement);

        try (var proxy = new BoltCommitAcknowledgementProxy(target);
            var publication = new DriverWork(proxy.uri(), settings, proxy::disconnect)) {
          AnalysisRunRepositoryFactory publicationService = context -> new Neo4jAnalysisRunRepository(publication.driver, sessionConfig, context, mapper::map);
          proxy.arm();

          // Act
          var outcome = publication.call(() -> AnalysisExecutionFixture.executeComputed(replacement, PublicationIntent.SAVE_COMPLETED,
              AnalysisExecutionFixture::unexpectedAstRepository, publicationService).publication());

          // Assert
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
        cleanup(verification.driver, sessionConfig, runIds);
      }
    }
  }

  @Test
  void givenAstCommitResponseWithheld_whenRetentionFails_thenExactReplacementIsDurableWhileCommitKnowledgeRemainsUnknown()
      throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var target = loopbackTarget(settings.uri());
    var directUri = directUri(target);
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var typeName = "Retained" + UUID.randomUUID().toString().replace("-", "");
    var previous = astFixture(typeName, sourceRoot.resolve("previous"));
    var replacement = astFixture(typeName, sourceRoot.resolve("replacement"));
    var independent = astFixture("Independent" + UUID.randomUUID().toString().replace("-", ""), sourceRoot.resolve("independent"));
    var identity = replacement.result().orElseThrow().identity();
    var independentIdentity = independent.result().orElseThrow().identity();
    var context = RepositoryOperationContext.saveAst(identity);
    var codec = new Neo4jAstResultCodec();
    var replacementProjection = codec.map(replacement);
    System.out.println("AST_TRANSPORT_PROJECTION_SIZES=" + replacementProjection.nodes().stream()
        .collect(Collectors.groupingBy(Neo4jNode::label, Collectors.summingInt(node -> node.properties().toString().length()))));
    var expectedReplacement = expectedAstState(replacementProjection);
    assertEquals(AstCompletion.COMPLETE, replacement.completion());
    assertEquals(previous.result().orElseThrow().identity(), identity);

    try (var verification = new DriverWork(directUri, settings)) {
      await(verification.driver.verifyConnectivityAsync());
      try {
        for (var outcome : List.of(previous, independent)) {
          try (var seeding = new DriverWork(directUri, settings)) {
            seeding.call(() -> {
              var saveContext = RepositoryOperationContext.saveAst(outcome.result().orElseThrow().identity());
              try (var repository = new Neo4jAstResultRepository(seeding.driver, sessionConfig, saveContext)) {
                repository.saveComplete(outcome);
              }
              return null;
            });
          }
        }
        var prior = storedAstState(verification.driver, sessionConfig, identity);
        var independentBefore = storedAstState(verification.driver, sessionConfig, independentIdentity);
        assertEquals(expectedAstState(codec.map(previous)), prior);
        assertEquals(expectedAstState(codec.map(independent)), independentBefore);
        assertNotEquals(prior, expectedReplacement);

        try (var proxy = new BoltCommitAcknowledgementProxy(target);
            var publication = new DriverWork(proxy.uri(), settings, proxy::disconnect)) {
          var retention = AnalysisCapabilities.astRetention(operation -> new Neo4jAstResultRepository(publication.driver, sessionConfig, operation));
          proxy.arm();

          // Act
          var outcome = publication.call(() -> retention.retainAst(new AstPublicationRequest(replacement,
              PublicationIntent.SAVE_COMPLETE_AST)));

          // Assert
          assertEquals(PublicationStatus.FAILED, outcome.status());
          assertEquals(CommitKnowledge.UNKNOWN, outcome.knowledge());
          assertEquals(Optional.of(new Ast(identity)), outcome.subjectIdentity());
          var failure = assertInstanceOf(Neo4jRepositoryOperationException.class, outcome.failure().orElseThrow().carrier());
          assertEquals(context, failure.context());
          assertEquals(Neo4jRepositoryPhase.REPLACEMENT, failure.phase());
          assertEquals(CommitKnowledge.UNKNOWN, failure.knowledge().orElseThrow());
          assertInstanceOf(Neo4jException.class, failure.effectivePrimary().getCause());
          proxy.requireHealthyRelay();
          var observation = proxy.observation();
          System.out.println("AST_BOLT_COMMIT_ACKNOWLEDGEMENT_OBSERVATION=" + observation);
          assertNotEquals("not negotiated", observation.negotiatedVersion());
          assertEquals(1, observation.beginMessages());
          assertEquals(1, observation.commitMessages());
          assertEquals(1, observation.withheldCommitSuccesses());
          assertEquals(0, observation.forwardedCommitResponses());
          assertEquals(expectedReplacement, storedAstState(verification.driver, sessionConfig, identity));
          assertEquals(independentBefore, storedAstState(verification.driver, sessionConfig, independentIdentity));
        }
        try (var querying = new DriverWork(directUri, settings)) {
          var events = new ArrayList<String>();
          var observedDriver = Neo4jCloseObservation.observeLookup(querying.driver, events);
          var query = AnalysisCapabilities.astQuery(operation -> new Neo4jAstResultRepository(observedDriver, sessionConfig, operation));
          var lookup = querying.call(() -> query.findAst(identity));
          assertEquals(List.of("lookup.read.returned", "lookup.session.close", "driver.close"), events);
          assertEquals(AstLookupStatus.FOUND, lookup.status());
          assertEquals(identity, lookup.outcome().orElseThrow().result().orElseThrow().identity());
          assertEquals(replacement.result().orElseThrow().snapshot(), lookup.outcome().orElseThrow().result().orElseThrow().snapshot());
        }
      } finally {
        cleanupAst(verification.driver, sessionConfig, List.of(identity, independentIdentity));
      }
    }
  }

  @Test
  void givenDistinctRetainedAsts_whenReplacementCommitsBeforeCloseFails_thenKnownCommitAndReplacementSurvive() throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var directUri = directUri(loopbackTarget(settings.uri()));
    var retention = AnalysisCapabilities.astRetention(Neo4jRepositories.astResults(() -> settings));
    var query = AnalysisCapabilities.astQuery(Neo4jRepositories.astResults(() -> settings));
    var codec = new Neo4jAstResultCodec();
    for (var phase : Phase.values()) {
      var root = sourceRoot.resolve(phase.name());
      var typeName = "Retained" + UUID.randomUUID().toString().replace("-", "");
      var previous = astFixture(typeName, root.resolve("previous"));
      var replacement = astFixture(typeName, root.resolve("replacement"));
      var independent = astFixture("Independent" + UUID.randomUUID().toString().replace("-", ""), root.resolve("independent"));
      var expected = replacement.result().orElseThrow();
      var identity = expected.identity();
      var independentIdentity = independent.result().orElseThrow().identity();
      var context = RepositoryOperationContext.saveAst(identity);
      var expectedState = expectedAstState(codec.map(replacement));
      assertEquals(previous.result().orElseThrow().identity(), identity);
      try (var verification = new DriverWork(directUri, settings)) {
        try {
          for (var initial : List.of(previous, independent)) {
            assertEquals(PublicationStatus.SAVED, retention.retainAst(
                new AstPublicationRequest(initial, PublicationIntent.SAVE_COMPLETE_AST)).status());
          }
          var prior = storedAstState(verification.driver, sessionConfig, identity);
          var independentBefore = storedAstState(verification.driver, sessionConfig, independentIdentity);
          assertEquals(expectedAstState(codec.map(previous)), prior);
          assertEquals(expectedAstState(codec.map(independent)), independentBefore);
          assertNotEquals(prior, expectedState);
          var events = new ArrayList<String>();
          var signal = new Neo4jException("Close outcome signal");
          try (var actualDriver = GraphDatabase.driver(directUri, AuthTokens.basic(settings.username(), settings.password()), observationConfig())) {
            var delegatedDriver = Neo4jCloseObservation.signalPublicationClose(actualDriver, phase, signal, events);
            var faultingRetention = AnalysisCapabilities.astRetention(operation -> new Neo4jAstResultRepository(delegatedDriver, sessionConfig, operation));

            // Act
            var outcome = faultingRetention.retainAst(new AstPublicationRequest(replacement, PublicationIntent.SAVE_COMPLETE_AST));

            // Assert
            assertEquals(PublicationStatus.FAILED, outcome.status());
            assertEquals(CommitKnowledge.KNOWN_COMMIT, outcome.knowledge());
            assertEquals(Optional.of(new Ast(identity)), outcome.subjectIdentity());
            var failure = assertInstanceOf(Neo4jRepositoryOperationException.class, outcome.failure().orElseThrow().carrier());
            assertEquals(context, failure.context());
            assertEquals(phase.repositoryPhase(), failure.phase());
            assertSame(signal, failure.effectivePrimary().getCause());
            assertEquals(List.of("schema.close", "publication.write.returned", "publication.close", "driver.close"), events);
          }
          assertEquals(expectedState, storedAstState(verification.driver, sessionConfig, identity));
          assertEquals(independentBefore, storedAstState(verification.driver, sessionConfig, independentIdentity));
          var lookup = query.findAst(identity);
          assertEquals(AstLookupStatus.FOUND, lookup.status());
          assertEquals(expected.snapshot(), lookup.outcome().orElseThrow().result().orElseThrow().snapshot());
        } finally {
          cleanupAst(verification.driver, sessionConfig, List.of(identity, independentIdentity));
        }
      }
    }
  }

  @Test
  void givenSeparateRealSessions_whenAnotherSessionClosesFirst_thenTheObserverDistinguishesTheSessionThatDidTheWork()
      throws IOException, URISyntaxException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var config = SessionConfig.forDatabase(settings.database());
    var directUri = directUri(loopbackTarget(settings.uri()));
    var publicationEvents = new ArrayList<String>();
    var lookupEvents = new ArrayList<String>();
    var signal = new Neo4jException("Publication session close signal");
    try (var actual = GraphDatabase.driver(directUri, AuthTokens.basic(settings.username(), settings.password()), observationConfig())) {
      var publication = Neo4jCloseObservation.signalPublicationClose(actual, Phase.PUBLICATION_SESSION, signal, publicationEvents);
      var lookup = Neo4jCloseObservation.observeLookup(actual, lookupEvents);
      var writing = publication.session(config);
      var otherPublication = publication.session(config);
      var reading = lookup.session(config);
      var otherLookup = lookup.session(config);
      var written = writing.executeWrite(transaction -> transaction.run("RETURN 1 AS value").single().get("value").asInt());
      var read = reading.executeRead(transaction -> transaction.run("RETURN 2 AS value").single().get("value").asInt());

      // Act
      otherPublication.close();
      otherLookup.close();
      var closeFailure = assertThrows(Neo4jException.class, writing::close);
      reading.close();

      // Assert
      assertEquals(1, written);
      assertEquals(2, read);
      assertSame(signal, closeFailure);
      assertEquals(List.of("publication.write.returned", "schema.close", "publication.close"), publicationEvents);
      assertEquals(List.of("lookup.read.returned", "lookup.other.session.close", "lookup.session.close"), lookupEvents);
    }
  }

  @Test
  void givenTwoPublicationNamespaces_whenEndpointsAreRedirected_thenStoredObservationsDistinguishScopeAndLabels() throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var directUri = directUri(loopbackTarget(settings.uri()));
    var namespaces = List.of(new NamespaceFixture(Namespace.AST, "AstEntity", "astResultKey"),
        new NamespaceFixture(Namespace.RUN, "AnalysisEntity", "runId"));
    try (var verification = new DriverWork(directUri, settings);
        var session = verification.driver.session(sessionConfig)) {
      for (var fixture : namespaces) {
        var scope = UUID.randomUUID().toString();
        var foreignScope = scope + "-foreign";
        var parameters = Values.parameters("scope", scope, "foreign", foreignScope);
        try {
          session.run("""
              CREATE (source:%1$s {key: 'source', %2$s: $scope, fixture: $scope}),
                (target:%1$s {key: 'shared', %2$s: $scope, fixture: $scope}),
                (foreign:%1$s {key: 'shared', %2$s: $foreign, fixture: $scope}),
                (unlabeled {key: 'shared', %2$s: $scope, fixture: $scope}),
                (source)-[:OBSERVED_LINK {key: 'original', %2$s: $scope}]->(target)
              """.formatted(fixture.label(), fixture.scopeProperty()), parameters).consume();
          var original = storedState(verification.driver, sessionConfig, fixture.namespace(), scope);
          assertEquals(scope, original.relationships().get("original").target().scope());

          // Act
          session.run("""
              MATCH (source {fixture: $scope, key: 'source'})-[old:OBSERVED_LINK]->(),
                (foreign:%1$s {fixture: $scope, %2$s: $foreign}),
                (unlabeled {fixture: $scope, key: 'shared'}) WHERE NOT unlabeled:%1$s
              CREATE (source)-[moved:OBSERVED_LINK]->(foreign),
                (unlabeled)-[:OBSERVED_LINK {key: 'incoming'}]->(source)
              SET moved = properties(old)
              DELETE old
              """.formatted(fixture.label(), fixture.scopeProperty()), parameters).consume();
          var observed = storedState(verification.driver, sessionConfig, fixture.namespace(), scope);

          // Assert
          assertEquals(original.nodes(), observed.nodes());
          assertEquals(Set.of("original", "incoming"), observed.relationships().keySet());
          var moved = observed.relationships().get("original");
          assertEquals(original.relationships().get("original").properties(), moved.properties());
          assertEquals("shared", moved.target().key());
          assertEquals(Set.of(fixture.label()), moved.target().labels());
          assertEquals(foreignScope, moved.target().scope());
          var incoming = observed.relationships().get("incoming");
          assertEquals("shared", incoming.source().key());
          assertEquals(Set.of(), incoming.source().labels());
          assertEquals(scope, incoming.source().scope());
          assertNotEquals(original, observed);
        } finally {
          session.run("MATCH (node {fixture: $scope}) DETACH DELETE node", parameters).consume();
        }
      }
    }
  }

  private record NamespaceFixture(Namespace namespace, String label, String scopeProperty) {}

  private static AstOutcome astFixture(String typeName, Path root) throws IOException {
    var source = SourceFile.fromContent(Path.of(typeName + ".java"), "interface " + typeName + " {}");
    var input = InputSnapshot.fromSources(root, List.of(source));
    return new JavaFrontend(new JavaSourceLoader()).produce(input, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED);
  }

  @Test
  void givenAstReplacementRejectedBeforeCommit_whenRetentionFails_thenPriorAndIndependentSubjectsRemainIntact() throws Exception {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var target = loopbackTarget(settings.uri());
    var directUri = directUri(target);
    var sessionConfig = SessionConfig.forDatabase(settings.database());
    var typeName = "Retained" + UUID.randomUUID().toString().replace("-", "");
    var previous = astFixture(typeName, sourceRoot.resolve("previous"));
    var replacement = astFixture(typeName, sourceRoot.resolve("replacement"));
    var independent = astFixture("Independent" + UUID.randomUUID().toString().replace("-", ""), sourceRoot.resolve("independent"));
    var identity = replacement.result().orElseThrow().identity();
    var independentIdentity = independent.result().orElseThrow().identity();
    var context = RepositoryOperationContext.saveAst(identity);
    var replacementCallbacks = new AtomicInteger();
    var codec = new Neo4jAstResultCodec();
    assertEquals(previous.result().orElseThrow().identity(), identity);

    try (var verification = new DriverWork(directUri, settings)) {
      await(verification.driver.verifyConnectivityAsync());
      try {
        for (var outcome : List.of(previous, independent)) {
          try (var seeding = new DriverWork(directUri, settings)) {
            seeding.call(() -> {
              var saveContext = RepositoryOperationContext.saveAst(outcome.result().orElseThrow().identity());
              try (var repository = new Neo4jAstResultRepository(seeding.driver, sessionConfig, saveContext)) {
                repository.saveComplete(outcome);
              }
              return null;
            });
          }
        }
        var prior = storedAstState(verification.driver, sessionConfig, identity);
        var independentBefore = storedAstState(verification.driver, sessionConfig, independentIdentity);
        assertEquals(expectedAstState(codec.map(previous)), prior);
        assertEquals(expectedAstState(codec.map(independent)), independentBefore);
        assertNotEquals(prior, expectedAstState(codec.map(replacement)));

        try (var proxy = new BoltCommitAcknowledgementProxy(target);
            var publication = new DriverWork(proxy.uri(), settings, proxy::disconnect)) {
          var failingDriver = rejectAfterReplacement(publication.driver, replacementCallbacks);
          var retention = AnalysisCapabilities.astRetention(operation -> new Neo4jAstResultRepository(failingDriver, sessionConfig, operation));

          // Act
          var outcome = publication.call(() -> retention.retainAst(new AstPublicationRequest(replacement,
              PublicationIntent.SAVE_COMPLETE_AST)));

          // Assert
          assertEquals(PublicationStatus.FAILED, outcome.status());
          assertEquals(CommitKnowledge.UNKNOWN, outcome.knowledge());
          assertEquals(Optional.of(new Ast(identity)), outcome.subjectIdentity());
          var failure = assertInstanceOf(Neo4jRepositoryOperationException.class, outcome.failure().orElseThrow().carrier());
          assertEquals(context, failure.context());
          assertEquals(Neo4jRepositoryPhase.REPLACEMENT, failure.phase());
          var serverFailure = assertInstanceOf(Neo4jException.class, failure.effectivePrimary().getCause());
          assertEquals("Neo.ClientError.Statement.ArithmeticError", serverFailure.code());
          assertEquals(1, replacementCallbacks.get());
          proxy.requireHealthyRelay();
          var observation = proxy.observation();
          System.out.println("AST_REJECTED_COMMIT_OBSERVATION=" + observation);
          assertNotEquals("not negotiated", observation.negotiatedVersion());
          assertEquals(1, observation.beginMessages());
          assertEquals(0, observation.commitMessages());
          assertEquals(0, observation.withheldCommitSuccesses());
          assertEquals(0, observation.forwardedCommitResponses());
          assertEquals(prior, storedAstState(verification.driver, sessionConfig, identity));
          assertEquals(independentBefore, storedAstState(verification.driver, sessionConfig, independentIdentity));
        }
      } finally {
        cleanupAst(verification.driver, sessionConfig, List.of(identity, independentIdentity));
      }
    }
  }

  private static Driver rejectAfterReplacement(Driver driver, AtomicInteger callbacks) {
    return Driver.class.cast(Proxy.newProxyInstance(Driver.class.getClassLoader(), new Class<?>[]{Driver.class},
        (ignored, method, arguments) -> {
          var value = invokeDelegate(method, driver, arguments);
          if (!(value instanceof Session session)) {
            return value;
          }
          return Session.class.cast(Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
              (sessionProxy, sessionMethod, sessionArguments) -> {
                if (sessionMethod.getName().equals("executeWrite") && sessionArguments != null
                    && sessionArguments.length > 0 && sessionArguments[0] instanceof TransactionCallback<?> callback) {
                  return session.executeWrite(transaction -> {
                    var result = callback.execute(transaction);
                    callbacks.incrementAndGet();
                    transaction.run("RETURN 1 / 0").consume();
                    return result;
                  });
                }
                return invokeDelegate(sessionMethod, session, sessionArguments);
              }));
        }));
  }

  private static @Nullable Object invokeDelegate(Method method, Object delegate, @Nullable Object @Nullable [] arguments) throws Throwable {
    try {
      return method.invoke(delegate, arguments);
    } catch (InvocationTargetException exception) {
      throw Objects.requireNonNull(exception.getCause(), "invoked cause");
    }
  }

  private static StoredPublication expectedAstState(Neo4jAstProjection projection) {
    return Namespace.AST.expected(projection.identity().astResultKey(), projection.nodes(), projection.relationships());
  }

  private static StoredPublication storedAstState(Driver driver, SessionConfig configuration, AstIdentity identity) throws IOException {
    return storedState(driver, configuration, Namespace.AST, identity.astResultKey());
  }

  private static void cleanupAst(Driver driver, SessionConfig configuration, List<AstIdentity> identities) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, configuration))) {
      await(session.value.runAsync("MATCH (entity:AstEntity) WHERE entity.astResultKey IN $scopes "
          + "CALL (entity) { DETACH DELETE entity } IN TRANSACTIONS OF 1000 ROWS",
          Values.parameters("scopes", identities.stream().map(AstIdentity::astResultKey).toList())).thenCompose(cursor -> cursor.consumeAsync()));
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

  private static InetSocketAddress loopbackTarget(URI uri) throws IOException {
    if ((!"bolt".equals(uri.getScheme()) && !"neo4j".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("The acknowledgement test requires configured unencrypted loopback Bolt");
    }
    var addresses = InetAddress.getAllByName(uri.getHost());
    for (var address : addresses) {
      if (!address.isLoopbackAddress()) {
        throw new IllegalArgumentException("The acknowledgement test cannot target a nonloopback backend");
      }
    }
    if (addresses.length == 0) {
      throw new IOException("The configured local Bolt host has no resolved loopback address");
    }
    var port = uri.getPort() < 0 ? 7687 : uri.getPort();
    var address = Arrays.stream(addresses).filter(Inet4Address.class::isInstance).findFirst().orElse(addresses[0]);
    return new InetSocketAddress(address, port);
  }

  private static URI directUri(InetSocketAddress target) throws URISyntaxException {
    return new URI("bolt", null, target.getAddress().getHostAddress(), target.getPort(), null, null, null);
  }

  private static Config observationConfig() {
    return Config.builder().withoutEncryption().withMaxTransactionRetryTime(0, TimeUnit.MILLISECONDS).withMaxConnectionPoolSize(1)
        .withConnectionTimeout(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS).withConnectionAcquisitionTimeout(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS)
        .withEventLoopThreads(2).withTelemetryDisabled(true).build();
  }

  private static StoredPublication expectedState(Neo4jGraphProjection projection) {
    return Namespace.RUN.expected(projection.runId(), projection.nodes(), projection.relationships());
  }

  private static StoredPublication storedState(Driver driver, SessionConfig configuration, String runId) throws IOException {
    return storedState(driver, configuration, Namespace.RUN, runId);
  }

  private static StoredPublication storedState(Driver driver, SessionConfig configuration, Namespace namespace, String scope) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, configuration))) {
      var parameters = Values.parameters("scope", scope);
      var nodes = await(session.value.runAsync(namespace.nodesQuery(), parameters).thenCompose(cursor -> cursor.listAsync()));
      var relationships = await(session.value.runAsync(namespace.relationshipsQuery(), parameters).thenCompose(cursor -> cursor.listAsync()));
      return StoredPublication.fromRecords(nodes, relationships);
    }
  }

  private static void cleanup(Driver driver, SessionConfig configuration, List<String> runIds) throws IOException {
    try (var session = new SessionOwner(driver.session(AsyncSession.class, configuration))) {
      await(session.value.runAsync("MATCH (entity:AnalysisEntity) WHERE entity.runId IN $runIds DETACH DELETE entity",
          Values.parameters("runIds", runIds)).thenCompose(cursor -> cursor.consumeAsync()));
    }
  }

  private static <T> T await(CompletionStage<T> stage) throws IOException {
    try {
      return stage.toCompletableFuture().get(DRIVER_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while awaiting an owned Neo4j operation", exception);
    } catch (ExecutionException exception) {
      throw new IOException("An owned Neo4j operation failed", exception);
    } catch (TimeoutException exception) {
      throw new IOException("An owned Neo4j operation did not return within the observation bound", exception);
    }
  }

  private static final class DriverWork implements AutoCloseable {
    private final Driver driver;
    private final Runnable disconnect;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
      var thread = new Thread(action, "neo4j-publication-observation");
      thread.setDaemon(true);
      return thread;
    });

    private DriverWork(URI uri, Neo4jConnectionSettings settings) {
      this(uri, settings, () -> { });
    }

    private DriverWork(URI uri, Neo4jConnectionSettings settings, Runnable disconnect) {
      this.disconnect = disconnect;
      driver = GraphDatabase.driver(uri, AuthTokens.basic(settings.username(), settings.password()), observationConfig());
    }

    private <T> T call(Callable<T> action) throws ExecutionException, IOException {
      try {
        return worker.submit(action).get(PUBLICATION_WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while awaiting the owned repository call", exception);
      } catch (TimeoutException exception) {
        throw new IOException("The owned repository call exceeded its observation bound", exception);
      }
    }

    @Override
    public void close() throws IOException {
      disconnect.run();
      try (var _ = new WorkerShutdown(worker)) {
        await(driver.closeAsync());
      }
    }
  }

  private static final class WorkerShutdown implements AutoCloseable {
    private final ExecutorService worker;

    private WorkerShutdown(ExecutorService worker) {
      this.worker = worker;
    }

    @Override
    public void close() throws IOException {
      worker.shutdownNow();
      try {
        if (!worker.awaitTermination(WORKER_STOP_SECONDS, TimeUnit.SECONDS)) {
          throw new IOException("The owned repository worker did not terminate within the bound");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while awaiting owned repository worker termination", exception);
      }
    }
  }

  private static final class SessionOwner implements AutoCloseable {
    private final AsyncSession value;

    private SessionOwner(AsyncSession value) {
      this.value = value;
    }

    @Override
    public void close() throws IOException {
      await(value.closeAsync());
    }
  }

}
