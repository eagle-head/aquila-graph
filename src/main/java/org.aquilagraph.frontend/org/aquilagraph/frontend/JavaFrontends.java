package org.aquilagraph.frontend;

import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.source.api.AstProducer;

/** Independent composition of Java source production. */
public final class JavaFrontends {
  private JavaFrontends() {
  }

  /**
   * Creates a producer with deterministic capture and owned compiler sessions.
   *
   * @return Java source production port
   */
  public static AstProducer standard() {
    return new JavaFrontend(new JavaSourceLoader());
  }
}
