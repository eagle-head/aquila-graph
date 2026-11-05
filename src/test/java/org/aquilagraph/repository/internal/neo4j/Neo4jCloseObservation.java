package org.aquilagraph.repository.internal.neo4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.Neo4jException;

/** Records live session and driver closure and supplies publication close signals. */
final class Neo4jCloseObservation {
  enum Phase {
    PUBLICATION_SESSION(Neo4jRepositoryPhase.PUBLICATION_SESSION_CLOSE),
    DRIVER(Neo4jRepositoryPhase.REPOSITORY_CLOSE);

    private final Neo4jRepositoryPhase repositoryPhase;

    Phase(Neo4jRepositoryPhase repositoryPhase) {
      this.repositoryPhase = repositoryPhase;
    }

    Neo4jRepositoryPhase repositoryPhase() {
      return repositoryPhase;
    }
  }

  private Neo4jCloseObservation() {}

  static Driver signalPublicationClose(Driver actual, Phase phase, Neo4jException signal, List<String> events) {
    return wrap(actual, session -> observeSession(session, "executeWrite", () -> events.add("publication.write.returned"), writeReturned -> {
      events.add(writeReturned ? "publication.close" : "schema.close");
      if (writeReturned && phase == Phase.PUBLICATION_SESSION) {
        throw signal;
      }
    }), () -> {
      events.add("driver.close");
      if (phase == Phase.DRIVER) {
        throw signal;
      }
    });
  }

  static Driver observeLookup(Driver actual, List<String> events) {
    return wrap(actual, session -> observeSession(session, "executeRead", () -> events.add("lookup.read.returned"),
        readReturned -> events.add(readReturned ? "lookup.session.close" : "lookup.other.session.close")),
        () -> events.add("driver.close"));
  }

  private static Driver wrap(Driver actual, UnaryOperator<Session> observeSession, Runnable driverClosed) {
    return (Driver) Proxy.newProxyInstance(Driver.class.getClassLoader(), new Class<?>[] {Driver.class}, (proxy, method, arguments) -> {
      var result = invoke(actual, method, arguments);
      if (method.getName().equals("session") && result instanceof Session session) {
        return observeSession.apply(session);
      }
      if (method.getName().equals("close")) {
        driverClosed.run();
      }
      return result;
    });
  }

  private static Session observeSession(Session session, String workMethod, Runnable afterWork, Consumer<Boolean> afterClose) {
    var workReturned = new AtomicBoolean();
    return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[] {Session.class},
        (proxy, method, arguments) -> {
          var result = invoke(session, method, arguments);
          if (method.getName().equals(workMethod)) {
            workReturned.set(true);
            afterWork.run();
          }
          if (method.getName().equals("close")) {
            afterClose.accept(workReturned.get());
          }
          return result;
        });
  }

  private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }
}
