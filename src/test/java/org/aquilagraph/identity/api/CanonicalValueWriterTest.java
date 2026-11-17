package org.aquilagraph.identity.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalValueWriterTest {
  @Test
  void givenMixedCanonicalPrimitives_whenHashedIncrementally_thenDigestMatchesIndependentFramedBytes()
      throws IOException, NoSuchAlgorithmException {
    // Arrange
    var version = "canonical-test/1";
    var text = "x".repeat(513) + "\ud800\u0000\udfff";
    var binary = new byte[] {0, -1, 1, 127};
    var expected = new ByteArrayOutputStream();
    try (var reference = new DataOutputStream(expected)) {
      reference.writeInt(version.length());
      reference.writeChars(version);
      reference.writeInt(text.length());
      reference.writeChars(text);
      reference.writeInt(Integer.MIN_VALUE);
      reference.writeLong(Long.MAX_VALUE);
      reference.writeBoolean(true);
      reference.writeBoolean(false);
      reference.writeInt(0x7fc00001);
      reference.writeLong(0x8000000000000000L);
      reference.writeInt(binary.length);
      reference.write(binary);
      reference.writeInt(0);
    }

    // Act
    var actual = CanonicalValueWriter.hash(version, "content", writer -> {
      writer.text(text);
      writer.integer(Integer.MIN_VALUE);
      writer.longInteger(Long.MAX_VALUE);
      writer.flag(true);
      writer.flag(false);
      writer.floating(Float.intBitsToFloat(0x7fc00001));
      writer.floating(-0.0d);
      writer.bytes(binary);
      writer.count(0);
    });

    // Assert
    assertEquals("content:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected.toByteArray())), actual);
  }

  @Test
  void givenFailedIncrementalEmission_whenHashingAgain_thenFailurePropagatesAndLaterStateIsIndependent() {
    // Arrange
    var failure = new IllegalArgumentException("Incomplete canonical value");
    var expected = new CanonicalValueWriter("canonical-test/1");
    expected.text("complete");

    // Act
    var observed = assertThrows(IllegalArgumentException.class,
        () -> CanonicalValueWriter.hash("canonical-test/1", "content", writer -> {
          writer.text("partial");
          throw failure;
        }));
    var actual = CanonicalValueWriter.hash("canonical-test/1", "content", writer -> writer.text("complete"));

    // Assert
    assertSame(failure, observed);
    assertEquals(expected.digest("content"), actual);
    assertThrows(IllegalStateException.class,
        () -> CanonicalValueWriter.hash("canonical-test/1", "content", writer -> writer.toByteArray()));
  }

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
