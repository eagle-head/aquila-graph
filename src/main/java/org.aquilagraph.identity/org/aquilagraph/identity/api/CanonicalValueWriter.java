package org.aquilagraph.identity.api;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Writes framed canonical values without changing Java string or floating-point content. */
public final class CanonicalValueWriter {
  private static final int TEXT_CHUNK_CHARACTERS = 512;
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();

  /**
   * Starts an encoding with its explicit version tag.
   *
   * @param version
   *          canonical encoding version
   */
  public CanonicalValueWriter(String version) {
    text(version);
  }

  /**
   * Writes a framed sequence of UTF-16 code units, including unpaired surrogates.
   *
   * @param value
   *          exact Java string
   */
  public void text(String value) {
    Objects.requireNonNull(value, "value");
    count(value.length());
    var encoded = new byte[Math.min(value.length(), TEXT_CHUNK_CHARACTERS) * Character.BYTES];
    var first = 0;
    while (first < value.length()) {
      var length = Math.min(encoded.length / Character.BYTES, value.length() - first);
      for (var index = 0; index < length; index++) {
        var character = value.charAt(first + index);
        encoded[index * Character.BYTES] = (byte) (character >>> Byte.SIZE);
        encoded[index * Character.BYTES + 1] = (byte) character;
      }
      output.write(encoded, 0, length * Character.BYTES);
      first += length;
    }
  }

  /**
   * Writes an ordered sequence count.
   *
   * @param value
   *          nonnegative element count
   */
  public void count(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("Canonical sequence count cannot be negative");
    }
    integer(value);
  }

  /**
   * Writes a big-endian signed 32-bit value.
   *
   * @param value
   *          retained integer
   */
  public void integer(int value) {
    for (var shift = 24; shift >= 0; shift -= 8) {
      output.write((value >>> shift) & 0xff);
    }
  }

  /**
   * Writes a big-endian signed 64-bit value.
   *
   * @param value
   *          retained integer
   */
  public void longInteger(long value) {
    for (var shift = 56; shift >= 0; shift -= 8) {
      output.write((int) ((value >>> shift) & 0xff));
    }
  }

  /**
   * Writes explicit presence or a retained Boolean classification.
   *
   * @param value
   *          retained Boolean
   */
  public void flag(boolean value) {
    output.write(value ? 1 : 0);
  }

  /**
   * Writes all 32 raw floating-point bits, preserving signed zero and NaN payloads.
   *
   * @param value
   *          retained float
   */
  public void floating(float value) {
    integer(Float.floatToRawIntBits(value));
  }

  /**
   * Writes all 64 raw floating-point bits, preserving signed zero and NaN payloads.
   *
   * @param value
   *          retained double
   */
  public void floating(double value) {
    longInteger(Double.doubleToRawLongBits(value));
  }

  /**
   * Writes framed copied bytes.
   *
   * @param value
   *          retained binary value
   */
  public void bytes(byte[] value) {
    Objects.requireNonNull(value, "value");
    count(value.length);
    output.writeBytes(value);
  }

  /**
   * Returns an independent copy of the encoded content.
   *
   * @return copied canonical bytes
   */
  public byte[] toByteArray() {
    return output.toByteArray();
  }

  /**
   * Derives a namespaced SHA-256 digest of the bytes written so far.
   *
   * @param namespace
   *          explicit content namespace
   * @return namespaced full-content digest
   */
  public String digest(String namespace) {
    Objects.requireNonNull(namespace, "namespace");
    try {
      return namespace + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(toByteArray()));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
    }
  }
}
