package org.aquilagraph.frontend;

import java.io.Serial;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/** Stops publication of a source root whose required nested declaration is unavailable. */
final class JavaSourceProjectionFailure extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;
  private final transient List<AnalysisDiagnostic> capturedDiagnostics;

  JavaSourceProjectionFailure(List<AnalysisDiagnostic> diagnostics) {
    super("Required nested source declaration is unavailable");
    capturedDiagnostics = TranslationDiagnosticEvidence.copyOf(diagnostics);
  }

  List<AnalysisDiagnostic> diagnostics() {
    return capturedDiagnostics;
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
