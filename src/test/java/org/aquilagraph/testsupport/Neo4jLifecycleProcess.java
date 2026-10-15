package org.aquilagraph.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;

/** Exercises database ownership across real process completion and termination. */
public final class Neo4jLifecycleProcess {
  private Neo4jLifecycleProcess() {}

  public static void main(String[] arguments) throws Exception {
    var output = Path.of(arguments[0]);
    var mode = arguments[1];
    try (var database = mode.equals("resume")
        ? ManagedNeo4jDatabase.resume(output, Path.of(arguments[2]))
        : ManagedNeo4jDatabase.start(output, mode.equals("preserve"))) {
      var settings = database.settings();
      try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
          var session = driver.session()) {
        if (mode.equals("resume")) {
          var value = session.run("MATCH (witness:LifecycleWitness) RETURN witness.value AS value").single().get("value").asInt();
          if (value != 42) {
            throw new IllegalStateException("Preserved database content was lost");
          }
        } else {
          session.run("CREATE (:LifecycleWitness {value: 42})").consume();
        }
      }
      Files.writeString(output.resolve("query-ready"), "42");
      if (mode.equals("failure")) {
        throw new IllegalStateException("Deliberate lifecycle failure");
      }
      if (mode.equals("wait")) {
        Thread.sleep(Duration.ofHours(1));
      }
    }
  }
}
