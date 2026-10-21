package org.aquilagraph.identity.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalValueWriterTest {
  @Test
  void givenRawUtf16AcrossTextChunks_whenWriting_thenFramesMatchDataOutputCodeUnits() throws IOException {
    // Arrange
    var version = "canonical-test/1";
    var spanning = "x".repeat(511) + "\ud83d\ude80" + "\ud800" + "y".repeat(511) + "\udfff" + "\u0000\u00ff\u0100\uffff";
    var values = List.of("", "a", "ab", "abc", "plain", "\u0000\u00ff\u0100\uffff\ud800\udfff",
        "x".repeat(512), "x".repeat(513), spanning);
    var expected = new ByteArrayOutputStream();
    try (var reference = new DataOutputStream(expected)) {
      reference.writeInt(version.length());
      reference.writeChars(version);
      for (var value : values) {
        reference.writeInt(value.length());
        reference.writeChars(value);
      }
    }

    // Act
    var writer = new CanonicalValueWriter(version);
    values.forEach(writer::text);

    // Assert
    assertArrayEquals(expected.toByteArray(), writer.toByteArray());
  }
}
