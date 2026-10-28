package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Reconciles unit results before integration and observes coverage/classfile isolation afterward. */
public final class CompleteValidationCheckpoint {
  private static final String ABSENT_INPUT = "absent";
  private static final List<String> OUTPUT_PATHS = List.of("jacoco.exec", "classes", "test-classes", "build-tool-classes");

  private CompleteValidationCheckpoint() {
  }

  /**
   * Records units or reconciles integrations for the current admitted full operation.
   *
   * @param arguments
   *          canonical root and unit or integration phase
   * @throws IOException
   *           if current results, instrumentation or immutable inputs do not reconcile
   */
  public static void main(String[] arguments) throws IOException {
    var nonce = System.getenv("AQUILA_BUILD_INVOCATION");
    if (nonce == null) {
      throw new IOException("Full validation checkpoint requires an owned invocation");
    }
    var context = InvocationContext.read(Path.of(arguments[0]), nonce);
    var unit = "unit".equals(arguments[1]);
    var scope = unit ? "unit" : "all";
    var directory = SuiteCatalog.directory(context.root(), nonce, scope);
    var catalog = SuiteCatalog.read(directory.resolve("catalog.properties"));
    catalog.requireSelection(scope, "");
    SuiteResults.verify(catalog, directory,
        context.root().resolve(unit ? "target/surefire-reports" : "target/failsafe-reports/failsafe-summary.xml"));
    SuiteResults.requireInstrumentation(directory, unit);
    if (unit) {
      BuildFiles.write(CompleteValidation.directory(context).resolve("unit-outputs.properties"), captureOutputs(context));
    } else {
      requireUnchanged(context);
      BuildFiles.write(CompleteValidation.directory(context).resolve("integration-verified.properties"), context.properties());
    }
  }

  static void requireUnchanged(InvocationContext context) throws IOException {
    var captured = BuildFiles.read(CompleteValidation.directory(context).resolve("unit-outputs.properties"));
    if (!captured.equals(captureOutputs(context))) {
      throw new IOException("Unit coverage or compiled class inputs changed after unit execution");
    }
  }

  private static Properties captureOutputs(InvocationContext context) throws IOException {
    var result = new Properties();
    for (var output : OUTPUT_PATHS) {
      var digest = PreparationFiles.digest(context.root().resolve("target").resolve(output));
      if (ABSENT_INPUT.equals(digest)) {
        throw new IOException("Missing current unit execution/class input: " + output);
      }
      result.setProperty(output, digest);
    }
    return result;
  }
}
