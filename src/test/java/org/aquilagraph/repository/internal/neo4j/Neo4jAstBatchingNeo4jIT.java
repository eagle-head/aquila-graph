package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.testsupport.Neo4jIntegration;
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
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
class Neo4jAstBatchingNeo4jIT {
  @TempDir Path directory;

  @Test
  void givenLargeAstProjection_whenLaterWriteBatchFails_thenEarlierWritesRollBackAndFailureRemainsTruthful() throws IOException {
    // Arrange
    var settings = Neo4jIntegration.settings();
    var name = "BatchSubject" + UUID.randomUUID().toString().replace("-", "");
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of(name + ".java"), "class " + name + " { int value(int input) { return input + 1; } }"))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED);
    var identity = ast.result().orElseThrow().identity();
    var projection = new Neo4jAstResultCodec().map(ast);
    assertTrue(projection.nodes().stream().filter(node -> node.label().equals("AstValue")).count() > 512,
        "The fixture must exercise more than one bounded scalar-value batch");
    var scalarKeys = projection.nodes().stream().filter(node -> node.label().equals("AstValue")).map(Neo4jNode::key)
        .collect(Collectors.toSet());
    var injected = new IllegalStateException("Injected failure after a completed AST write batch");
    var batches = new AtomicInteger();
    var uncommitted = new AtomicLong();
    var driver = failingDriver(GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password())),
        identity.astResultKey(), scalarKeys, injected, batches, uncommitted);
    var context = RepositoryOperationContext.saveAst(identity);

    // Act
    Neo4jRepositoryOperationException failure;
    try (var repository = new Neo4jAstResultRepository(driver, SessionConfig.forDatabase(settings.database()), context)) {
      failure = assertThrows(Neo4jRepositoryOperationException.class, () -> repository.saveComplete(ast));
    }

    // Assert
    assertTrue(uncommitted.get() > 512, "The failure must follow actual uncommitted database writes");
    assertEquals(Neo4jRepositoryPhase.REPLACEMENT, failure.phase());
    assertEquals(CommitKnowledge.UNKNOWN, failure.knowledge().orElseThrow());
    assertSame(injected, failure.effectivePrimary());
    try (var observer = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = observer.session(SessionConfig.forDatabase(settings.database()))) {
      assertEquals(0, session.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count",
          Values.parameters("scope", identity.astResultKey())).single().get("count").asLong(),
          "A failed later batch must not publish earlier batches");
    }
  }

  private static Driver failingDriver(Driver driver, String scope, Set<String> scalarKeys, RuntimeException injected, AtomicInteger batches,
      AtomicLong uncommitted) {
    return proxy(Driver.class, (ignored, method, arguments) -> {
      var result = invoke(method, driver, arguments);
      if (method.getName().equals("session")) {
        var session = (Session) result;
        return proxy(Session.class, (ignoredSession, sessionMethod, sessionArguments) -> {
          if (sessionMethod.getName().equals("executeWrite")) {
            var callback = (TransactionCallback<?>) sessionArguments[0];
            return session.executeWrite(transaction -> callback.execute(proxy(TransactionContext.class, (ignoredTransaction, queryMethod, queryArguments) -> {
              if (queryMethod.getName().equals("run") && containsScalarNodes(queryArguments, scalarKeys) && batches.incrementAndGet() == 2) {
                uncommitted.set(transaction.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count",
                    Values.parameters("scope", scope)).single().get("count").asLong());
                throw injected;
              }
              return invoke(queryMethod, transaction, queryArguments);
            })));
          }
          return invoke(sessionMethod, session, sessionArguments);
        });
      }
      return result;
    });
  }

  private static boolean containsScalarNodes(Object[] arguments, Set<String> scalarKeys) {
    return arguments.length == 2 && arguments[1] instanceof Value parameters && parameters.containsKey("nodes")
        && parameters.get("nodes").asList(node -> node.get("key").asString(null)).stream().anyMatch(scalarKeys::contains);
  }

  private static <T> T proxy(Class<T> contract, InvocationHandler handler) {
    return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] {contract}, handler));
  }

  private static Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException failure) {
      throw failure.getCause();
    }
  }
}
