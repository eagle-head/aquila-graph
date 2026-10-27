package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Clears owned compilation storage before resource filtering or standard Maven clean. */
public final class CompilationCleanup {
  private static final int OPERATION_ARGUMENT_COUNT = 1;
  private CompilationCleanup() {
  }

  /**
   * Clears the selected preparation storage under its admitted lifecycle.
   *
   * @param arguments
   *          clean, production-resources or test-resources
   * @throws IOException
   *           if admission, path ownership or deletion cannot be established
   */
  public static void main(String[] arguments) throws IOException {
    if (arguments.length != OPERATION_ARGUMENT_COUNT) {
      throw new IOException("Compilation cleanup requires one storage operation");
    }
    BuildAdmission.main(new String[]{"clean".equals(arguments[0]) ? "clean" : "default"});
    clear(Path.of("").toRealPath(), arguments[0]);
  }

  static void clear(Path root, String operation) throws IOException {
    var paths = switch (operation) {
      case "clean" -> List.of("compilation", "resource-staging");
      case "production-resources" -> List.of("resource-staging/production");
      case "test-resources" -> List.of("resource-staging/tests");
      default -> throw new IOException("Unsupported compilation storage operation: " + operation);
    };
    for (var path : paths) {
      PreparationFiles.clear(root, root.resolve("target").resolve(path));
    }
  }
}
