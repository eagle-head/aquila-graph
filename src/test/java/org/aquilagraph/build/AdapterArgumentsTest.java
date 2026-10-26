package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdapterArgumentsTest {
  @Test
  void givenLiteralUtf16Arguments_whenTheNativeEnvelopeIsRead_thenEveryCodeUnitAndEmptyValueSurvives() throws Exception {
    // Arrange
    var expected = new String[]{"--root", "D:\\working space & values", "", "quote\" pipe| bang! %PATH% ' $()", "tail\\", "\ud800\udfff\ud800"};
    var environment = new HashMap<String, String>();
    environment.put("AQUILA_BUILD_ARGUMENT_COUNT", Integer.toString(expected.length));
    for (var index = 0; index < expected.length; index++) {
      environment.put("AQUILA_BUILD_ARGUMENT_" + index, encode(expected[index]));
    }

    // Act
    var actual = AdapterArguments.read(new String[]{"--adapter-environment"}, environment);

    // Assert
    assertArrayEquals(expected, actual);
  }

  @Test
  void givenMissingOrIncompleteTransport_whenArgumentsAreRead_thenTheEnvelopeIsRejected() {
    // Arrange
    var selector = new String[]{"--adapter-environment"};
    var incomplete = Map.of("AQUILA_BUILD_ARGUMENT_COUNT", "1", "AQUILA_BUILD_ARGUMENT_0", "u16:AA==");

    // Act
    var missing = assertThrows(IOException.class, () -> AdapterArguments.read(selector, Map.of("AQUILA_BUILD_ARGUMENT_COUNT", "1")));
    var partial = assertThrows(IOException.class, () -> AdapterArguments.read(selector, incomplete));

    // Assert
    assertTrue(missing.getMessage().contains("argument field"));
    assertTrue(partial.getMessage().contains("UTF-16"));
  }

  private static String encode(String value) {
    var bytes = new byte[value.length() * Character.BYTES];
    for (var index = 0; index < value.length(); index++) {
      bytes[index * Character.BYTES] = (byte) value.charAt(index);
      bytes[index * Character.BYTES + 1] = (byte) (value.charAt(index) >>> Byte.SIZE);
    }
    return "u16:" + Base64.getEncoder().encodeToString(bytes);
  }
}
