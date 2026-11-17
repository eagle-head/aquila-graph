package org.aquilagraph.identity.api;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Consumer;

/** Writes framed canonical values without changing Java string or floating-point content. */
public final class CanonicalValueWriter {
  private static final int TEXT_CHUNK_CHARACTERS = 512;
  private final Destination output;

  /**
   * Starts an encoding with its explicit version tag.
   *
   * @param version
   *          canonical encoding version
   */
  public CanonicalValueWriter(String version) {
    this(version, new Buffered(new ByteArrayOutputStream()));
  }

  private CanonicalValueWriter(String version, Destination destination) {
    output = destination;
    text(version);
  }

  /**
   * Hashes a complete framed encoding incrementally without retaining its byte sequence.
   *
   * @param version
   *          canonical encoding version
   * @param namespace
   *          explicit content namespace
   * @param encode
   *          synchronous value emission; buffered content access is unavailable
   * @return namespaced full-content SHA-256 digest
   */
  public static String hash(String version, String namespace, Consumer<CanonicalValueWriter> encode) {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(encode, "encode");
    var digest = sha256();
    encode.accept(new CanonicalValueWriter(version, new Hashed(digest)));
    return namespace + ":" + HexFormat.of().formatHex(digest.digest());
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
    output.write(value, 0, value.length);
  }

  /**
   * Returns an independent copy of the encoded content.
   *
   * @return copied canonical bytes
   */
  public byte[] toByteArray() {
    if (output instanceof Buffered buffered) {
      return buffered.bytes().toByteArray();
    }
    throw new IllegalStateException("Incremental canonical hashing does not retain encoded bytes");
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
    return namespace + ":" + HexFormat.of().formatHex(sha256().digest(toByteArray()));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
    }
  }

  private sealed interface Destination {
    void write(int value);

    void write(byte[] value, int offset, int length);
  }

  private record Buffered(ByteArrayOutputStream bytes) implements Destination {
    @Override
    public void write(int value) {
      bytes.write(value);
    }

    @Override
    public void write(byte[] value, int offset, int length) {
      bytes.write(value, offset, length);
    }
  }

  private record Hashed(MessageDigest digest) implements Destination {
    @Override
    public void write(int value) {
      digest.update((byte) value);
    }

    @Override
    public void write(byte[] value, int offset, int length) {
      digest.update(value, offset, length);
    }
  }
}
