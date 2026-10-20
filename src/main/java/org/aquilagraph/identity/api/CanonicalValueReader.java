package org.aquilagraph.identity.api;

import java.util.Arrays;
import java.util.Objects;

/** Reads exact framed canonical values, rejecting malformed lengths and incomplete payloads. */
public final class CanonicalValueReader {
  private final byte[] input;
  private int position;

  /**
   * Copies an encoded payload and verifies its exact format tag.
   *
   * @param encoded
   *          complete encoded bytes
   * @param expectedVersion
   *          supported exact format version
   * @throws IllegalArgumentException
   *           if the tag differs or its frame is malformed
   */
  public CanonicalValueReader(byte[] encoded, String expectedVersion) {
    input = Objects.requireNonNull(encoded, "encoded").clone();
    Objects.requireNonNull(expectedVersion, "expectedVersion");
    var version = text();
    if (!expectedVersion.equals(version)) {
      throw new IllegalArgumentException("Unsupported canonical value format: " + version);
    }
  }

  /**
   * Reads exact Java UTF-16 code units without normalization or replacement.
   *
   * @return copied Java string
   */
  public String text() {
    var length = count();
    if (length > remaining() / Character.BYTES) {
      throw malformed("String frame exceeds remaining payload");
    }
    var characters = new char[length];
    for (var index = 0; index < length; index++) {
      characters[index] = (char) ((unsignedByte() << 8) | unsignedByte());
    }
    return new String(characters);
  }

  /**
   * Reads a nonnegative ordered sequence count.
   *
   * @return retained element count
   */
  public int count() {
    var value = integer();
    if (value < 0) {
      throw malformed("Canonical sequence count cannot be negative");
    }
    return value;
  }

  /**
   * Reads a big-endian signed 32-bit value.
   *
   * @return retained integer
   */
  public int integer() {
    requireRemaining(Integer.BYTES);
    var value = 0;
    for (var index = 0; index < Integer.BYTES; index++) {
      value = (value << 8) | unsignedByte();
    }
    return value;
  }

  /**
   * Reads a big-endian signed 64-bit value.
   *
   * @return retained integer
   */
  public long longInteger() {
    requireRemaining(Long.BYTES);
    var value = 0L;
    for (var index = 0; index < Long.BYTES; index++) {
      value = (value << 8) | unsignedByte();
    }
    return value;
  }

  /**
   * Reads a Boolean encoded by the closed zero/one tags.
   *
   * @return retained Boolean
   */
  public boolean flag() {
    requireRemaining(1);
    return switch (unsignedByte()) {
      case 0 -> false;
      case 1 -> true;
      default -> throw malformed("Unknown canonical Boolean tag");
    };
  }

  /**
   * Reads all 32 retained floating-point bits.
   *
   * @return retained float
   */
  public float floating() {
    return Float.intBitsToFloat(integer());
  }

  /**
   * Reads all 64 retained floating-point bits.
   *
   * @return retained double
   */
  public double doubleFloating() {
    return Double.longBitsToDouble(longInteger());
  }

  /**
   * Reads a bounded copied byte frame.
   *
   * @return independent copied bytes
   */
  public byte[] bytes() {
    var length = count();
    requireRemaining(length);
    var value = Arrays.copyOfRange(input, position, position + length);
    position += length;
    return value;
  }

  /**
   * Returns the number of unread bytes for bounded collection decoding.
   *
   * @return unread byte count
   */
  public int remaining() {
    return input.length - position;
  }

  /** Rejects a payload with any unread trailing bytes. */
  public void requireFinished() {
    if (remaining() != 0) {
      throw malformed("Canonical payload has unread trailing bytes");
    }
  }

  private int unsignedByte() {
    var value = input[position] & 0xff;
    position++;
    return value;
  }

  private void requireRemaining(int length) {
    // Counts are validated before this check; the other callers use fixed positive widths.
    if (length > remaining()) {
      throw malformed("Canonical frame exceeds remaining payload");
    }
  }

  private IllegalArgumentException malformed(String message) {
    return new IllegalArgumentException(message + " at byte " + position);
  }
}
