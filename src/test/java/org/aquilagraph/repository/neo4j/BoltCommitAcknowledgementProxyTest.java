package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoltCommitAcknowledgementProxyTest {
  @Test
  void givenCommitSplitAcrossChunks_whenFrameIsRead_thenOnlyCompleteTopLevelCommitIsRecognized() throws IOException {
    // Arrange
    var wire = bytes("0001b00001120000");
    var input = new ByteArrayInputStream(wire);

    // Act
    var frame = BoltCommitAcknowledgementProxy.readFrame(input).orElseThrow();

    // Assert
    assertArrayEquals(wire, frame.wire());
    assertArrayEquals(bytes("b012"), frame.payload());
    assertTrue(BoltCommitAcknowledgementProxy.exactCommit(frame.payload()));
    assertTrue(BoltCommitAcknowledgementProxy.readFrame(input).isEmpty());
  }

  @Test
  void givenAdjacentNoopAndRunWithEmbeddedCommitBytes_whenFramesAreRead_thenNoopAndNoncommitRemainSeparate() throws IOException {
    // Arrange
    var input = new ByteArrayInputStream(bytes("00000004b310b01200000002b0120000"));

    // Act
    var noop = BoltCommitAcknowledgementProxy.readFrame(input).orElseThrow();
    var run = BoltCommitAcknowledgementProxy.readFrame(input).orElseThrow();
    var commit = BoltCommitAcknowledgementProxy.readFrame(input).orElseThrow();

    // Assert
    assertEquals(0, noop.payload().length);
    assertFalse(BoltCommitAcknowledgementProxy.exactCommit(run.payload()));
    assertTrue(BoltCommitAcknowledgementProxy.exactCommit(commit.payload()));
  }

  @Test
  void givenTruncatedChunkBody_whenFrameIsRead_thenCapabilityFailureIsExplicit() {
    // Arrange
    var input = new ByteArrayInputStream(bytes("0002b0"));

    // Act
    var failure = assertThrows(EOFException.class, () -> BoltCommitAcknowledgementProxy.readFrame(input));

    // Assert
    assertEquals("EOF within a Bolt protocol field", failure.getMessage());
  }

  @Test
  void givenCommitWithFieldsOrTrailingBytes_whenCommitIsRecognized_thenMalformedCommitIsRejected() {
    // Arrange
    var withField = bytes("b112a0");
    var withTrailing = bytes("b01200");

    // Act
    var fieldFailure = assertThrows(IOException.class, () -> BoltCommitAcknowledgementProxy.exactCommit(withField));
    var trailingFailure = assertThrows(IOException.class, () -> BoltCommitAcknowledgementProxy.exactCommit(withTrailing));

    // Assert
    assertEquals("COMMIT must be an exact zero-field Bolt structure", fieldFailure.getMessage());
    assertEquals(fieldFailure.getMessage(), trailingFailure.getMessage());
  }

  @Test
  void givenLegacyVersionNegotiation_whenHandshakeIsRelayed_thenOriginalBytesAndFirstMessageBoundaryArePreserved() throws IOException {
    // Arrange
    var proposal = bytes("6060b01700000006000208050000040400000003");
    var input = new ByteArrayInputStream(concat(proposal, bytes("0002b0010000")));
    var server = new ByteArrayInputStream(bytes("00000006"));
    var backend = new ByteArrayOutputStream();
    var driver = new ByteArrayOutputStream();

    // Act
    var version = BoltCommitAcknowledgementProxy.relayHandshake(input, backend, server, driver);

    // Assert
    assertEquals("6.0", version);
    assertArrayEquals(proposal, backend.toByteArray());
    assertArrayEquals(bytes("00000006"), driver.toByteArray());
    assertArrayEquals(bytes("b001"), BoltCommitAcknowledgementProxy.readFrame(input).orElseThrow().payload());
  }

  @Test
  void givenManifestWithRangeAndCapabilities_whenHandshakeIsRelayed_thenSelectedVersionAndPipelinedMessageStayExact() throws IOException {
    // Arrange
    var proposal = bytes("6060b017000001ff000000060002080500000404");
    var finalChoice = bytes("000007058101");
    var input = new ByteArrayInputStream(concat(proposal, finalChoice, bytes("0002b0010000")));
    var manifest = bytes("000001ff0200020805000000068101");
    var server = new ByteArrayInputStream(manifest);
    var backend = new ByteArrayOutputStream();
    var driver = new ByteArrayOutputStream();

    // Act
    var version = BoltCommitAcknowledgementProxy.relayHandshake(input, backend, server, driver);

    // Assert
    assertEquals("5.7", version);
    assertArrayEquals(concat(proposal, finalChoice), backend.toByteArray());
    assertArrayEquals(manifest, driver.toByteArray());
    assertArrayEquals(bytes("b001"), BoltCommitAcknowledgementProxy.readFrame(input).orElseThrow().payload());
  }

  @Test
  void givenFragmentedManifestReads_whenHandshakeIsRelayed_thenCompleteServerNegotiationIsEmittedTogether() throws IOException {
    // Arrange
    var proposal = bytes("6060b017000001ff000000060002080500000404");
    var finalChoice = bytes("000007058101");
    var input = new FragmentedInput(concat(proposal, finalChoice, bytes("0002b0010000")));
    var manifest = bytes("000001ff0200020805000000068101");
    var server = new FragmentedInput(manifest);
    var backend = new ByteArrayOutputStream();
    var driver = new NegotiationOutput();

    // Act
    var version = BoltCommitAcknowledgementProxy.relayHandshake(input, backend, server, driver);

    // Assert
    assertEquals("5.7", version);
    assertArrayEquals(concat(proposal, finalChoice), backend.toByteArray());
    assertEquals(List.of(manifest.length), driver.emittedByteCounts);
    assertEquals(1, driver.flushedBytes.size());
    assertArrayEquals(manifest, driver.flushedBytes.getFirst());
    assertArrayEquals(bytes("b001"), BoltCommitAcknowledgementProxy.readFrame(input).orElseThrow().payload());
  }

  @Test
  void givenManifestMissingFinalCapabilityByte_whenHandshakeIsRelayed_thenNoPartialServerNegotiationIsEmitted() {
    // Arrange
    var input = new ByteArrayInputStream(bytes("6060b017000001ff000000060002080500000404"));
    var server = new FragmentedInput(bytes("000001ff02000208050000000681"));
    var driver = new NegotiationOutput();

    // Act
    var failure = assertThrows(EOFException.class,
        () -> BoltCommitAcknowledgementProxy.relayHandshake(input, new ByteArrayOutputStream(), server, driver));

    // Assert
    assertEquals("EOF within a Bolt handshake VarInt", failure.getMessage());
    assertEquals(0, driver.size());
    assertTrue(driver.emittedByteCounts.isEmpty());
    assertTrue(driver.flushedBytes.isEmpty());
  }

  @Test
  void givenUnofferedVersionOrCapabilities_whenManifestIsRelayed_thenProxyFailsWithoutInventingNegotiation() {
    // Arrange
    var proposal = bytes("6060b017000001ff000000060002080500000404");
    var unofferedVersion = new ByteArrayInputStream(concat(proposal, bytes("0000090500")));
    var unofferedCapability = new ByteArrayInputStream(concat(proposal, bytes("0000080502")));
    var manifest = bytes("000001ff010002080501");

    // Act
    var versionFailure = assertThrows(IOException.class, () -> BoltCommitAcknowledgementProxy.relayHandshake(unofferedVersion,
        new ByteArrayOutputStream(), new ByteArrayInputStream(manifest), new ByteArrayOutputStream()));
    var capabilityFailure = assertThrows(IOException.class, () -> BoltCommitAcknowledgementProxy.relayHandshake(unofferedCapability,
        new ByteArrayOutputStream(), new ByteArrayInputStream(manifest), new ByteArrayOutputStream()));

    // Assert
    assertEquals("The negotiated Bolt version was not offered", versionFailure.getMessage());
    assertEquals("The Bolt client selected capabilities not offered by the server", capabilityFailure.getMessage());
  }

  @Test
  void givenTlsBytesOrOversizedManifest_whenHandshakeIsRelayed_thenUnsupportedCapabilityIsReported() {
    // Arrange
    var encrypted = new ByteArrayInputStream(bytes("1603030000000000000000000000000000000000"));
    var proposal = new ByteArrayInputStream(bytes("6060b017000001ff000000060002080500000404"));
    var oversizedManifest = new ByteArrayInputStream(bytes("000001ff41"));

    // Act
    var magicFailure = assertThrows(IOException.class, () -> BoltCommitAcknowledgementProxy.relayHandshake(encrypted,
        new ByteArrayOutputStream(), new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()));
    var countFailure = assertThrows(IOException.class, () -> BoltCommitAcknowledgementProxy.relayHandshake(proposal,
        new ByteArrayOutputStream(), oversizedManifest, new ByteArrayOutputStream()));

    // Assert
    assertEquals("The configured connection did not send the Bolt protocol magic", magicFailure.getMessage());
    assertEquals("The Bolt handshake manifest version count is outside this observation's bound", countFailure.getMessage());
  }

  private static byte[] bytes(String hexadecimal) {
    return HexFormat.of().parseHex(hexadecimal);
  }

  private static byte[] concat(byte[]... segments) {
    var bytes = new ByteArrayOutputStream();
    for (var segment : segments) {
      bytes.writeBytes(segment);
    }
    return bytes.toByteArray();
  }

  private static final class FragmentedInput extends InputStream {
    private final ByteArrayInputStream input;

    private FragmentedInput(byte[] bytes) {
      input = new ByteArrayInputStream(bytes);
    }

    @Override
    public int read() {
      return input.read();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) {
      return input.read(bytes, offset, Math.min(length, 1));
    }
  }

  private static final class NegotiationOutput extends ByteArrayOutputStream {
    private final List<Integer> emittedByteCounts = new ArrayList<>();
    private final List<byte[]> flushedBytes = new ArrayList<>();

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
      emittedByteCounts.add(length);
      super.write(bytes, offset, length);
    }

    @Override
    public void flush() {
      flushedBytes.add(toByteArray());
    }
  }
}
