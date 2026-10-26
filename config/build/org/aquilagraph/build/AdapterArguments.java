package org.aquilagraph.build;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/** Transfers literal native-adapter arguments without creating pre-lease files. */
final class AdapterArguments {
  static final String ENVIRONMENT_PREFIX = "AQUILA_BUILD_ARGUMENT_";
  private static final String SELECTOR = "--adapter-environment";
  private static final String ENCODING_PREFIX = "u16:";
  private static final int SELECTOR_ARGUMENT_COUNT = 1;

  private AdapterArguments() {
  }

  static String[] read(String[] arguments, Map<String, String> environment) throws IOException {
    if (arguments.length != SELECTOR_ARGUMENT_COUNT || !SELECTOR.equals(arguments[0])) {
      return arguments.clone();
    }
    var count = Integer.parseInt(required(environment, "COUNT"));
    if (count < SELECTOR_ARGUMENT_COUNT || count > environment.size()) {
      throw new IOException("The native adapter argument count is unavailable or inconsistent");
    }
    var result = new String[count];
    for (var index = 0; index < count; index++) {
      result[index] = decode(required(environment, Integer.toString(index)));
    }
    return result;
  }

  private static String required(Map<String, String> environment, String suffix) throws IOException {
    var value = environment.get(ENVIRONMENT_PREFIX + suffix);
    if (value == null) {
      throw new IOException("Missing native adapter argument field: " + suffix);
    }
    return value;
  }

  private static String decode(String encoded) throws IOException {
    if (!encoded.startsWith(ENCODING_PREFIX)) {
      throw new IOException("The native adapter argument encoding is unavailable");
    }
    var bytes = Base64.getDecoder().decode(encoded.substring(ENCODING_PREFIX.length()));
    if (bytes.length % Character.BYTES != 0) {
      throw new IOException("The native adapter argument has an incomplete UTF-16 code unit");
    }
    var units = new char[bytes.length / Character.BYTES];
    for (var index = 0; index < units.length; index++) {
      units[index] = (char) (Byte.toUnsignedInt(bytes[index * Character.BYTES])
          | Byte.toUnsignedInt(bytes[index * Character.BYTES + 1]) << Byte.SIZE);
    }
    return new String(units);
  }
}
