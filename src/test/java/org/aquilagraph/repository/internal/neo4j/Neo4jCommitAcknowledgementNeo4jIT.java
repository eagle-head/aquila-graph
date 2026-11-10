package org.aquilagraph.repository.internal.neo4j;

import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.await;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.directUri;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.loopbackTarget;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.observationConfig;
import static org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.storedState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.repository.internal.neo4j.Neo4jCloseObservation.Phase;
import org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.DriverWork;
import org.aquilagraph.repository.internal.neo4j.Neo4jPublicationObservation.SessionOwner;
import org.aquilagraph.repository.internal.neo4j.StoredPublication.Namespace;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
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

  @TempDir Path sourceRoot;

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
    var retention = AnalysisCapabilities.astRetention(Neo4jRepositories.astRetention(() -> settings));
    var query = AnalysisCapabilities.astQuery(Neo4jRepositories.astQuery(() -> settings));
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

}
