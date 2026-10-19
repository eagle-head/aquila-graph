package org.aquilagraph.frontend;

import java.io.Serial;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;

/** Stops publication of a source root whose required declaration or expression projection is unavailable. */
final class JavaSourceProjectionFailure extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;
  private static final String MESSAGE = "Required source projection is unavailable";
  private final transient DiagnosticEmissions capturedEmissions;

  JavaSourceProjectionFailure(DiagnosticEmissions emissions) {
    super(MESSAGE);
    capturedEmissions = Objects.requireNonNull(emissions, "emissions");
  }

  DiagnosticEmissions emissions() {
    return capturedEmissions;
  }

  JavaSourceProjectionFailure(DiagnosticEmissions earlier, JavaSourceProjectionFailure cause) {
    super(MESSAGE, cause);
    capturedEmissions = DiagnosticEmissions.combine(earlier, cause.emissions());
  }

  @Serial
  private void writeObject(ObjectOutputStream output) throws NotSerializableException {
    Objects.requireNonNull(output, "output");
    throw new NotSerializableException("Source projection diagnostics remain in the production invocation");
  }

  @Serial
  private void readObject(ObjectInputStream input) throws NotSerializableException {
    Objects.requireNonNull(input, "input");
    throw new NotSerializableException("Source projection diagnostics require their production invocation");
  }
}
