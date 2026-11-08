package org.aquilagraph;

import java.nio.file.Path;

/** Explicit failure catalogs for the delivered malformed-input and missing-compiler fixtures. */
final class PackagedFailureExpectations {
  private PackagedFailureExpectations() {}

  static String malformedUtf8(Path source) {
    var path = source.toAbsolutePath().normalize().toString().replace("\\", "\\\\");
    return """
        Input rejected: "Java source input could not be captured: Cannot read Java source: %1$s"
        Failure reference: 1
        Failure: 1
          Type: "org.aquilagraph.source.api.AnalysisInputException"
          Message: "Java source input could not be captured: Cannot read Java source: %1$s"
          Cause: 2
          Carrier: not-applicable
          Effective primary: not-applicable
          Suppressed: none
          Operation: not-applicable
        Failure: 2
          Type: "java.io.IOException"
          Message: "Cannot read Java source: %1$s"
          Cause: 3
          Carrier: not-applicable
          Effective primary: not-applicable
          Suppressed: none
          Operation: not-applicable
        Failure: 3
          Type: "java.nio.charset.MalformedInputException"
          Message: "Input length = 1"
          Cause: unavailable
          Carrier: not-applicable
          Effective primary: not-applicable
          Suppressed: none
          Operation: not-applicable
        """.formatted(path).replace("\n", System.lineSeparator());
  }

  static String missingCompiler() {
    return """
        Analysis failed: "A JDK system compiler is required for Java release 25"
        Failure reference: 1
        Failure: 1
          Type: "org.aquilagraph.source.api.AnalysisEnvironmentException"
          Message: "A JDK system compiler is required for Java release 25"
          Cause: unavailable
          Carrier: not-applicable
          Effective primary: not-applicable
          Suppressed: none
          Operation: not-applicable
        """.replace("\n", System.lineSeparator());
  }
}
