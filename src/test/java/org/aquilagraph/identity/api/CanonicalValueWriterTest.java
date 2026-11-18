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
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class CanonicalValueWriterTest {
  private static final int CHUNK_BYTES = 256;
  private static final String VERSION = "canonical-test/1";

  @TestFactory
  Stream<DynamicTest> givenMixedFieldsNearChunkBoundary_whenEncoded_thenBothDestinationsMatchIndependentFraming() {
    return IntStream.of(-1, 0, 1).mapToObj(adjustment -> DynamicTest.dynamicTest(
        "givenPrimitiveStartsAtChunkBoundaryOffset" + adjustment + "_whenEncoded_thenBothDestinationsMatchIndependentFraming",
        () -> assertMixedFieldsHaveIndependentFraming(adjustment)));
  }

  private static void assertMixedFieldsHaveIndependentFraming(int adjustment) throws IOException, NoSuchAlgorithmException {
    // Arrange
    var versionSize = Integer.BYTES + VERSION.length() * Character.BYTES;
    var padding = new byte[CHUNK_BYTES - versionSize - Integer.BYTES + adjustment];
    var text = "x".repeat(513) + "\ud800\u0000\udfff";
    var binary = new byte[3 * CHUNK_BYTES + 13];
    for (var index = 0; index < binary.length; index++) {
      binary[index] = (byte) index;
    }
    var expected = new ByteArrayOutputStream();
    try (var reference = new DataOutputStream(expected)) {
      textFrame(reference, VERSION);
      reference.writeInt(padding.length);
      reference.write(padding);
      reference.writeLong(Long.MAX_VALUE);
      reference.writeLong(Long.MIN_VALUE);
      reference.writeInt(Integer.MIN_VALUE);
      reference.writeInt(Integer.MAX_VALUE);
      reference.writeInt(Integer.MIN_VALUE);
      textFrame(reference, text);
      reference.writeBoolean(true);
      reference.writeBoolean(false);
      reference.writeInt(0x80000000);
      reference.writeInt(0x7fc00001);
      reference.writeInt(0x7fc00002);
      reference.writeLong(0x8000000000000000L);
      reference.writeLong(0x7ff8000000000001L);
      reference.writeLong(0x7ff8000000000002L);
      reference.writeInt(binary.length);
      reference.write(binary);
      reference.writeInt(0);
      reference.writeInt(3);
    }
    Consumer<CanonicalValueWriter> emit = writer -> {
      writer.bytes(padding);
      writer.longInteger(Long.MAX_VALUE);
      writer.longInteger(Long.MIN_VALUE);
      writer.integer(Integer.MIN_VALUE);
      writer.integer(Integer.MAX_VALUE);
      writer.integer(Integer.MIN_VALUE);
      writer.text(text);
      writer.flag(true);
      writer.flag(false);
      writer.floating(-0.0f);
      writer.floating(Float.intBitsToFloat(0x7fc00001));
      writer.floating(Float.intBitsToFloat(0x7fc00002));
      writer.floating(-0.0d);
      writer.floating(Double.longBitsToDouble(0x7ff8000000000001L));
      writer.floating(Double.longBitsToDouble(0x7ff8000000000002L));
      writer.bytes(binary);
      writer.count(0);
      writer.count(3);
    };

    // Act
    var buffered = new CanonicalValueWriter(VERSION);
    emit.accept(buffered);
    var actual = CanonicalValueWriter.hash(VERSION, "content", emit);

    // Assert
    assertArrayEquals(expected.toByteArray(), buffered.toByteArray());
    assertEquals(digest("content", expected.toByteArray()), actual);
  }

  @TestFactory
  Stream<DynamicTest> givenEmptyOrExactlyFullPayload_whenFinalized_thenNoBytesAreLostOrAdded() {
    return IntStream.of(0, CHUNK_BYTES - 2 * Integer.BYTES).mapToObj(size -> DynamicTest.dynamicTest(
        "givenPayloadOf" + size + "Bytes_whenFinalized_thenNoBytesAreLostOrAdded", () -> assertPayloadFraming(size)));
  }

  private static void assertPayloadFraming(int payloadSize) throws IOException, NoSuchAlgorithmException {
    // Arrange
    var payload = new byte[payloadSize];
    var expected = new ByteArrayOutputStream();
    try (var reference = new DataOutputStream(expected)) {
      textFrame(reference, "");
      reference.writeInt(payloadSize);
      reference.write(payload);
    }

    // Act
    var buffered = new CanonicalValueWriter("");
    buffered.bytes(payload);
    var actual = CanonicalValueWriter.hash("", "content", writer -> writer.bytes(payload));

    // Assert
    assertArrayEquals(expected.toByteArray(), buffered.toByteArray());
    assertEquals(digest("content", expected.toByteArray()), actual);
  }

  @Test
  void givenLateEmissionFailure_whenNestedAndLaterOperationsHash_thenEachOperationHasIndependentState()
      throws IOException, NoSuchAlgorithmException {
    // Arrange
    var failure = new IllegalArgumentException("Incomplete canonical value");
    var expected = new ByteArrayOutputStream();
    try (var reference = new DataOutputStream(expected)) {
      textFrame(reference, VERSION);
      textFrame(reference, "complete");
    }
    var nested = new String[1];

    // Act
    var observed = assertThrows(IllegalArgumentException.class,
        () -> CanonicalValueWriter.hash(VERSION, "content", writer -> {
          writer.text("partial".repeat(CHUNK_BYTES));
          nested[0] = CanonicalValueWriter.hash(VERSION, "nested", other -> other.text("complete"));
          writer.flag(true);
          throw failure;
        }));
    var later = CanonicalValueWriter.hash(VERSION, "later", writer -> writer.text("complete"));

    // Assert
    assertSame(failure, observed);
    assertEquals(digest("nested", expected.toByteArray()), nested[0]);
    assertEquals(digest("later", expected.toByteArray()), later);
    assertThrows(IllegalStateException.class,
        () -> CanonicalValueWriter.hash(VERSION, "content", CanonicalValueWriter::toByteArray));
  }

  @Test
  void givenOuterHashHasPendingBytes_whenNestedHashCompletes_thenBothDigestsMatchTheirFramedInputs()
      throws IOException, NoSuchAlgorithmException {
    // Arrange
    var prefix = "outer".repeat(CHUNK_BYTES);
    var childBytes = new ByteArrayOutputStream();
    var parentBytes = new ByteArrayOutputStream();
    try (var child = new DataOutputStream(childBytes); var parent = new DataOutputStream(parentBytes)) {
      textFrame(child, VERSION);
      child.writeBoolean(false);
      textFrame(parent, VERSION);
      textFrame(parent, prefix);
      parent.writeBoolean(true);
    }
    var nested = new String[1];

    // Act
    var outer = CanonicalValueWriter.hash(VERSION, "outer", writer -> {
      writer.text(prefix);
      nested[0] = CanonicalValueWriter.hash(VERSION, "inner", other -> other.flag(false));
      writer.flag(true);
    });

    // Assert
    assertEquals(digest("inner", childBytes.toByteArray()), nested[0]);
    assertEquals(digest("outer", parentBytes.toByteArray()), outer);
  }

  @Test
  void givenBufferedTail_whenDigestingRepeatedlyAndAppending_thenSnapshotsAndDigestsReflectTheirOwnPrefix()
      throws IOException, NoSuchAlgorithmException {
    // Arrange
    var text = "value".repeat(CHUNK_BYTES);
    var expected = new ByteArrayOutputStream();
    byte[] prefix;
    try (var reference = new DataOutputStream(expected)) {
      textFrame(reference, VERSION);
      textFrame(reference, text);
      prefix = expected.toByteArray();
      reference.writeInt(42);
    }

    // Act
    var writer = new CanonicalValueWriter(VERSION);
    writer.text(text);
    var firstDigest = writer.digest("prefix");
    var repeatDigest = writer.digest("prefix");
    var firstSnapshot = writer.toByteArray();
    var callerCopy = writer.toByteArray();
    callerCopy[0] = 1;
    writer.integer(42);
    var finalSnapshot = writer.toByteArray();
    var finalDigest = writer.digest("extended");

    // Assert
    assertArrayEquals(prefix, firstSnapshot);
    assertArrayEquals(expected.toByteArray(), finalSnapshot);
    assertEquals(digest("prefix", prefix), firstDigest);
    assertEquals(firstDigest, repeatDigest);
    assertEquals(digest("extended", expected.toByteArray()), finalDigest);
  }

  @Test
  void givenRawUtf16AcrossTextChunks_whenWriting_thenFramesMatchDataOutputCodeUnits() throws IOException, NoSuchAlgorithmException {
    // Arrange
    var prefixSize = Integer.BYTES + VERSION.length() * Character.BYTES + Integer.BYTES;
    var beforePair = (CHUNK_BYTES - prefixSize) / Character.BYTES - 1;
    var spanning = "x".repeat(beforePair) + "\ud83d\ude80" + "\ud800" + "y".repeat(CHUNK_BYTES) + "\udfff" + "\u0000\u00ff\u0100\uffff";
    var values = List.of(spanning, "", "a", "ab", "abc", "plain", "\u0000\u00ff\u0100\uffff\ud800\udfff");
    var expected = new ByteArrayOutputStream();
    try (var reference = new DataOutputStream(expected)) {
      textFrame(reference, VERSION);
      for (var value : values) {
        textFrame(reference, value);
      }
    }

    // Act
    var writer = new CanonicalValueWriter(VERSION);
    values.forEach(writer::text);
    var actualDigest = CanonicalValueWriter.hash(VERSION, "content", target -> values.forEach(target::text));

    // Assert
    assertArrayEquals(expected.toByteArray(), writer.toByteArray());
    assertEquals(digest("content", expected.toByteArray()), actualDigest);
  }

  private static void textFrame(DataOutputStream reference, String value) throws IOException {
    reference.writeInt(value.length());
    reference.writeChars(value);
  }

  private static String digest(String namespace, byte[] bytes) throws NoSuchAlgorithmException {
    return namespace + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
