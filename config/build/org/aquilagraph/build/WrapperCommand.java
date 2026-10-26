package org.aquilagraph.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Native wrapper launch syntax is confined to the operating-system boundary. */
final class WrapperCommand {
  private static final Pattern BATCH_BACKSLASHES = Pattern.compile("(\\\\+)(?=\"|$)");

  private WrapperCommand() {
  }

  static void configure(ProcessBuilder builder, Path executable, List<String> arguments) {
    var result = new ArrayList<String>();
    var name = executable.toString().toLowerCase(Locale.ROOT);
    if (BuildRequest.windows() && (name.endsWith(".cmd") || name.endsWith(".bat"))) {
      var command = new StringBuilder(quote(executable.toString()));
      for (var argument : arguments) {
        command.append(' ').append(quote(argument));
      }
      builder.environment().put("AQUILA_BUILD_WRAPPER_COMMAND", command.toString());
      result.addAll(List.of(System.getenv().getOrDefault("ComSpec", "cmd.exe"), "/d", "/v:off", "/s", "/c",
          "set AQUILA_BUILD_PROCESS=" + builder.environment().get("AQUILA_BUILD_INVOCATION") + "/"
              + builder.environment().get("AQUILA_BUILD_WRAPPER") + "& %AQUILA_BUILD_WRAPPER_COMMAND%"));
    } else {
      result.add(executable.toString());
      result.addAll(arguments);
    }
    builder.command(result);
  }

  private static String quote(String value) {
    if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
      throw new IllegalArgumentException("A Windows batch argument contains an unsupported expansion or line boundary");
    }
    var escaped = BATCH_BACKSLASHES.matcher(value).replaceAll("$1$1").replace("\"", "\"\"");
    return '"' + escaped + '"';
  }
}
