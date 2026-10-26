package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Encodes literal JDK argument-file elements without evaluating shell syntax. */
final class JavaArguments {
  private JavaArguments() {
  }

  static void write(Path file, List<String> arguments) throws IOException {
    var contents = new StringBuilder();
    for (var argument : arguments) {
      contents.append('"').append(argument.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n");
    }
    Files.writeString(file, contents);
  }
}
