package org.aquilagraph.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalValueReaderTest {
  @Test
  void givenNegativeByteFrameCount_whenBytesAreRead_thenTheCountIsRejectedBeforeReadingThePayload() {
    // Arrange
    var reader = new CanonicalValueReader(HexFormat.of().parseHex("000000010076ffffffff"), "v");

    // Act
    var failure = assertThrows(IllegalArgumentException.class, reader::bytes);

    // Assert
    assertEquals("Canonical sequence count cannot be negative at byte 10", failure.getMessage());
    assertEquals(0, reader.remaining());
  }

  @Test
  void givenLiteralUtf16AndRawScalarFrames_whenValuesAreRead_thenEveryRetainedBitAndCodeUnitSurvives() {
    // Arrange
    var bytes = HexFormat.of().parseHex("000000010076000000030041d8000042ffffffff800000000000000001800000007fc12345"
        + "80000000000000007ff8123456789abc0000000300ff7f");
    var reader = new CanonicalValueReader(bytes, "v");

    // Act and Assert
    assertEquals("A\ud800B", reader.text());
    assertEquals(-1, reader.integer());
    assertEquals(Long.MIN_VALUE, reader.longInteger());
    assertEquals(true, reader.flag());
    assertEquals(0x80000000, Float.floatToRawIntBits(reader.floating()));
    assertEquals(0x7fc12345, Float.floatToRawIntBits(reader.floating()));
    assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(reader.doubleFloating()));
    assertEquals(0x7ff8123456789abcL, Double.doubleToRawLongBits(reader.doubleFloating()));
    assertArrayEquals(new byte[]{0, (byte) 0xff, 0x7f}, reader.bytes());
    reader.requireFinished();
    assertEquals(0, reader.remaining());
  }

  @Test
  void givenMalformedLengthsAndBooleanTags_whenPayloadsAreRead_thenNoTruncatedOrUnboundedFrameIsAccepted() {
    // Arrange
    var prefix = "000000010076";

    // Act and Assert
    for (var length : List.of("ffffffff", "7fffffff", "0000000100")) {
      var reader = new CanonicalValueReader(HexFormat.of().parseHex(prefix + length), "v");
      assertThrows(IllegalArgumentException.class, reader::text);
    }
    var booleanReader = new CanonicalValueReader(HexFormat.of().parseHex(prefix + "02"), "v");
    assertThrows(IllegalArgumentException.class, booleanReader::flag);
    var byteReader = new CanonicalValueReader(HexFormat.of().parseHex(prefix + "7fffffff"), "v");
    assertThrows(IllegalArgumentException.class, byteReader::bytes);
  }

  @Test
  void givenAnUnknownVersionTrailingDataOrMutatedCallerBytes_whenTheReaderIsUsed_thenFormatAndCopyBoundariesHold() {
    // Arrange
    var bytes = HexFormat.of().parseHex("00000001007601");
    var reader = new CanonicalValueReader(bytes, "v");
    bytes[6] = 0;

    // Act and Assert
    assertThrows(IllegalArgumentException.class, reader::requireFinished);
    assertEquals(true, reader.flag());
    reader.requireFinished();
    assertThrows(IllegalArgumentException.class, reader::integer);
    assertThrows(IllegalArgumentException.class, () -> new CanonicalValueReader(bytes, "other"));
    assertThrows(IllegalArgumentException.class, () -> new CanonicalValueReader(new byte[]{0}, "v"));
  }
}
