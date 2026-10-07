package org.aquilagraph.repository.neo4j;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/** Owns a single loopback Bolt connection whose selected commit response is withheld. */
final class BoltCommitAcknowledgementProxy implements AutoCloseable {
  private static final int SOCKET_TIMEOUT_MILLIS = 20_000;
  private static final int SHUTDOWN_SECONDS = 5;
  private static final int MAX_MESSAGE_BYTES = 1_048_576;
  private static final int MAX_VERSION_OFFERS = 64;
  private static final int MAX_VARINT_BYTES = 10;
  private static final int STRUCTURE_MARKER = 0xB0;
  private static final int RUN_TAG = 0x10;
  private static final int BEGIN_TAG = 0x11;
  private static final int COMMIT_TAG = 0x12;
  private static final int GOODBYE_TAG = 0x02;
  private static final int SUCCESS_TAG = 0x70;
  private static final int RECORD_TAG = 0x71;
  private static final int IGNORED_TAG = 0x7E;
  private static final int FAILURE_TAG = 0x7F;
  private static final byte[] MAGIC = {0x60, 0x60, (byte) 0xB0, 0x17};
  private static final byte[] MANIFEST_V1 = {0, 0, 1, (byte) 0xFF};

  private final InetSocketAddress target;
  private final ServerSocket listener;
  private final ExecutorService relays = Executors.newFixedThreadPool(3, action -> {
    var thread = new Thread(action, "bolt-acknowledgement-relay");
    thread.setDaemon(true);
    return thread;
  });
  private final ArrayBlockingQueue<PendingRequest> pending = new ArrayBlockingQueue<>(64);
  private final AtomicReference<Socket> clientSocket = new AtomicReference<>();
  private final AtomicReference<Socket> backendSocket = new AtomicReference<>();
  private final AtomicReference<IOException> relayFailure = new AtomicReference<>();
  private final AtomicReference<String> negotiatedVersion = new AtomicReference<>("not negotiated");
  private final AtomicBoolean armed = new AtomicBoolean();
  private final AtomicBoolean withholding = new AtomicBoolean();
  private final AtomicBoolean goodbyeReceived = new AtomicBoolean();
  private final AtomicBoolean stopped = new AtomicBoolean();
  private final AtomicInteger begins = new AtomicInteger();
  private final AtomicInteger commits = new AtomicInteger();
  private final AtomicInteger withheldSuccesses = new AtomicInteger();
  private final AtomicInteger forwardedCommitResponses = new AtomicInteger();

  BoltCommitAcknowledgementProxy(InetSocketAddress target) throws IOException {
    if (target.isUnresolved() || !target.getAddress().isLoopbackAddress()) {
      throw new IllegalArgumentException("The Bolt acknowledgement proxy requires a resolved loopback target");
    }
    this.target = target;
    listener = new ServerSocket();
    try {
      listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1);
      listener.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
    } catch (IOException exception) {
      try {
        listener.close();
      } catch (IOException closing) {
        exception.addSuppressed(closing);
      }
      relays.shutdownNow();
      throw exception;
    }
    relays.submit(this::acceptConnection);
  }

  URI uri() {
    return URI.create("bolt://127.0.0.1:" + listener.getLocalPort());
  }

  void arm() {
    if (!armed.compareAndSet(false, true)) {
      throw new IllegalStateException("Commit acknowledgement interception can be armed only once");
    }
  }

  Observation observation() {
    return new Observation(negotiatedVersion.get(), begins.get(), commits.get(), withheldSuccesses.get(), forwardedCommitResponses.get());
  }

  void requireHealthyRelay() throws IOException {
    var failure = relayFailure.get();
    if (failure != null) {
      throw new IOException("The Bolt relay failed", failure);
    }
  }

  private void acceptConnection() {
    try {
      var client = listener.accept();
      clientSocket.set(client);
      client.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
      var backend = new Socket();
      backendSocket.set(backend);
      backend.connect(target, SOCKET_TIMEOUT_MILLIS);
      backend.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
      negotiatedVersion.set(relayHandshake(client.getInputStream(), backend.getOutputStream(), backend.getInputStream(), client.getOutputStream()));
      relays.submit(() -> relayClient(client, backend));
      relays.submit(() -> relayServer(backend, client));
    } catch (IOException exception) {
      recordUnexpectedFailure(exception);
      disconnect();
    }
  }

  private void relayClient(Socket client, Socket backend) {
    try {
      var input = client.getInputStream();
      var output = backend.getOutputStream();
      while (relayClientFrame(input, output, prefix -> {
          var tag = messageTag(prefix);
          var intercept = exactCommit(prefix) && armed.compareAndSet(true, false);
          if (tag == BEGIN_TAG) {
            begins.incrementAndGet();
          }
          if (tag == COMMIT_TAG) {
            commits.incrementAndGet();
          }
          if (tag == GOODBYE_TAG) {
            goodbyeReceived.set(true);
          }
          if (tag != GOODBYE_TAG && !pending.offer(new PendingRequest(tag, intercept), SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
            throw new IOException("The bounded Bolt request queue did not drain");
          }
      })) {
        requireHealthyRelay();
      }
    } catch (IOException exception) {
      recordUnexpectedFailure(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      recordUnexpectedFailure(new IOException("The Bolt client relay was interrupted", exception));
    } finally {
      disconnect();
    }
  }

  private void relayServer(Socket backend, Socket client) {
    try {
      var input = backend.getInputStream();
      var output = client.getOutputStream();
      Optional<Frame> next;
      while ((next = readFrame(input)).isPresent()) {
        var frame = next.orElseThrow();
        if (frame.payload().length != 0) {
          var tag = messageTag(frame.payload());
          if (tag != RECORD_TAG) {
            if (tag != SUCCESS_TAG && tag != IGNORED_TAG && tag != FAILURE_TAG) {
              throw new IOException("The Bolt server returned an unsupported response tag");
            }
            var request = pending.poll(SHUTDOWN_SECONDS, TimeUnit.SECONDS);
            if (request == null) {
              throw new IOException("A Bolt summary has no corresponding request");
            }
            if (request.intercept()) {
              if (tag != SUCCESS_TAG || Byte.toUnsignedInt(frame.payload()[0]) != STRUCTURE_MARKER + 1) {
                throw new IOException("The selected COMMIT did not receive a one-field SUCCESS summary");
              }
              withholding.set(true);
              withheldSuccesses.incrementAndGet();
              return;
            }
            if (request.tag() == COMMIT_TAG) {
              forwardedCommitResponses.incrementAndGet();
            }
          }
        }
        output.write(frame.wire());
        output.flush();
      }
    } catch (IOException exception) {
      recordUnexpectedFailure(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      recordUnexpectedFailure(new IOException("The Bolt server relay was interrupted", exception));
    } finally {
      disconnect();
    }
  }

  void disconnect() {
    closeSocket(clientSocket.get());
    closeSocket(backendSocket.get());
  }

  private void closeSocket(@Nullable Socket socket) {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException exception) {
        relayFailure.compareAndSet(null, exception);
      }
    }
  }

  private void recordUnexpectedFailure(IOException exception) {
    if (exception instanceof SocketException && goodbyeReceived.get()
        && ((clientSocket.get() != null && clientSocket.get().isClosed())
            || (backendSocket.get() != null && backendSocket.get().isClosed()))) {
      return;
    }
    if (!stopped.get() && !withholding.get()) {
      relayFailure.compareAndSet(null, exception);
    }
  }

  @Override
  public void close() throws IOException {
    stopped.set(true);
    try (var _ = new RelayShutdown(this)) {
      listener.close();
    }
  }

  private void stopRelays() throws IOException {
    disconnect();
    relays.shutdownNow();
    try {
      if (!relays.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
        throw new IOException("Owned Bolt relay tasks did not terminate within the bound");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while awaiting owned Bolt relay termination", exception);
    }
    requireHealthyRelay();
  }

  static Optional<Frame> readFrame(InputStream input) throws IOException {
    var wire = new ByteArrayOutputStream();
    var payload = new ByteArrayOutputStream();
    while (true) {
      var high = input.read();
      if (high < 0) {
        if (wire.size() == 0) {
          return Optional.empty();
        }
        throw new EOFException("EOF within a Bolt message");
      }
      var low = input.read();
      if (low < 0) {
        throw new EOFException("EOF within a Bolt chunk header");
      }
      wire.write(high);
      wire.write(low);
      var size = (high << 8) | low;
      if (size == 0) {
        return Optional.of(new Frame(wire.toByteArray(), payload.toByteArray()));
      }
      if (payload.size() + size > MAX_MESSAGE_BYTES) {
        throw new IOException("The Bolt message exceeds this observation's bounded fixture size");
      }
      var chunk = readExactly(input, size);
      wire.writeBytes(chunk);
      payload.writeBytes(chunk);
    }
  }

  static boolean relayClientFrame(InputStream input, OutputStream output, ClientMessageObserver observer)
      throws IOException, InterruptedException {
    var prefix = new ByteArrayOutputStream(2);
    long payloadBytes = 0;
    var started = false;
    while (true) {
      var high = input.read();
      if (high < 0) {
        if (!started) {
          return false;
        }
        throw new EOFException("EOF within a Bolt message");
      }
      started = true;
      var low = input.read();
      if (low < 0) {
        throw new EOFException("EOF within a Bolt chunk header");
      }
      var size = (high << 8) | low;
      if (size == 0) {
        if (payloadBytes != 0) {
          var messagePrefix = prefix.toByteArray();
          var tag = messageTag(messagePrefix);
          if (tag == COMMIT_TAG && payloadBytes != 2) {
            throw new IOException("COMMIT must be an exact zero-field Bolt structure");
          }
          if (tag == GOODBYE_TAG && (payloadBytes != 2 || Byte.toUnsignedInt(messagePrefix[0]) != STRUCTURE_MARKER)) {
            throw new IOException("GOODBYE must be an exact zero-field Bolt structure");
          }
          exactCommit(messagePrefix);
          // Register the request before its terminator makes a response possible.
          observer.accept(messagePrefix);
        }
        output.write(high);
        output.write(low);
        output.flush();
        return true;
      }
      var chunk = readExactly(input, size);
      prefix.write(chunk, 0, Math.min(2 - prefix.size(), chunk.length));
      payloadBytes += size;
      // RUN parameters are opaque and forwarded one bounded chunk at a time.
      if (prefix.size() == 2 && messageTag(prefix.toByteArray()) != RUN_TAG && payloadBytes > MAX_MESSAGE_BYTES) {
        throw new IOException("The Bolt message exceeds this observation's bounded fixture size");
      }
      output.write(high);
      output.write(low);
      output.write(chunk);
    }
  }

  @FunctionalInterface
  interface ClientMessageObserver {
    void accept(byte[] prefix) throws IOException, InterruptedException;
  }

  static boolean exactCommit(byte[] payload) throws IOException {
    if (messageTag(payload) != COMMIT_TAG) {
      return false;
    }
    if (payload.length != 2 || Byte.toUnsignedInt(payload[0]) != STRUCTURE_MARKER) {
      throw new IOException("COMMIT must be an exact zero-field Bolt structure");
    }
    return true;
  }

  static int messageTag(byte[] payload) throws IOException {
    if (payload.length < 2 || (Byte.toUnsignedInt(payload[0]) & 0xF0) != STRUCTURE_MARKER) {
      throw new IOException("The framed Bolt message is not a supported PackStream structure");
    }
    return Byte.toUnsignedInt(payload[1]);
  }

  static String relayHandshake(InputStream client, OutputStream backend, InputStream server, OutputStream driver) throws IOException {
    var proposal = readExactly(client, 20);
    if (!Arrays.equals(MAGIC, Arrays.copyOf(proposal, MAGIC.length))) {
      throw new IOException("The configured connection did not send the Bolt protocol magic");
    }
    backend.write(proposal);
    backend.flush();
    var selection = readExactly(server, 4);
    if (!Arrays.equals(selection, MANIFEST_V1)) {
      driver.write(selection);
      driver.flush();
      requireSelectedVersion(selection);
      requireOfferedVersion(selection, Arrays.copyOfRange(proposal, 4, proposal.length));
      return versionText(selection);
    }
    var manifestOffered = false;
    for (var offset = 4; offset < proposal.length; offset += 4) {
      manifestOffered |= Arrays.equals(MANIFEST_V1, Arrays.copyOfRange(proposal, offset, offset + 4));
    }
    if (!manifestOffered) {
      throw new IOException("The Bolt server selected a manifest not offered by the client");
    }
    var count = readVarInt(server);
    if (count.value() < 1 || count.value() > MAX_VERSION_OFFERS) {
      throw new IOException("The Bolt handshake manifest version count is outside this observation's bound");
    }
    var offers = readExactly(server, Math.toIntExact(count.value()) * 4);
    var offeredCapabilities = readVarInt(server);
    var serverNegotiation = new ByteArrayOutputStream();
    serverNegotiation.writeBytes(selection);
    serverNegotiation.writeBytes(count.wire());
    serverNegotiation.writeBytes(offers);
    serverNegotiation.writeBytes(offeredCapabilities.wire());
    driver.write(serverNegotiation.toByteArray());
    driver.flush();
    var chosen = readExactly(client, 4);
    var chosenCapabilities = readVarInt(client);
    requireSelectedVersion(chosen);
    requireOfferedVersion(chosen, offers);
    if ((chosenCapabilities.value() & ~offeredCapabilities.value()) != 0) {
      throw new IOException("The Bolt client selected capabilities not offered by the server");
    }
    backend.write(chosen);
    backend.write(chosenCapabilities.wire());
    backend.flush();
    return versionText(chosen);
  }

  private static void requireSelectedVersion(byte[] version) throws IOException {
    var major = Byte.toUnsignedInt(version[3]);
    if (version[0] != 0 || version[1] != 0 || major < 3 || major > 6) {
      throw new IOException("The negotiated Bolt version is outside the supported commit-message observation");
    }
  }

  private static void requireOfferedVersion(byte[] selected, byte[] offers) throws IOException {
    var minor = Byte.toUnsignedInt(selected[2]);
    for (var offset = 0; offset < offers.length; offset += 4) {
      var offeredMinor = Byte.toUnsignedInt(offers[offset + 2]);
      var range = Byte.toUnsignedInt(offers[offset + 1]);
      if (offers[offset] == 0 && offers[offset + 3] == selected[3] && minor <= offeredMinor && minor >= offeredMinor - range) {
        return;
      }
    }
    throw new IOException("The negotiated Bolt version was not offered");
  }

  private static String versionText(byte[] version) {
    return Byte.toUnsignedInt(version[3]) + "." + Byte.toUnsignedInt(version[2]);
  }

  private static VarInt readVarInt(InputStream input) throws IOException {
    var wire = new ByteArrayOutputStream();
    long value = 0;
    for (var position = 0; position < MAX_VARINT_BYTES; position++) {
      var next = input.read();
      if (next < 0) {
        throw new EOFException("EOF within a Bolt handshake VarInt");
      }
      wire.write(next);
      if (position == MAX_VARINT_BYTES - 1 && (next & 0xFE) != 0) {
        throw new IOException("The Bolt handshake VarInt exceeds 64 bits");
      }
      value |= (long) (next & 0x7F) << (7 * position);
      if ((next & 0x80) == 0) {
        return new VarInt(wire.toByteArray(), value);
      }
    }
    throw new IOException("The Bolt handshake VarInt did not terminate");
  }

  private static byte[] readExactly(InputStream input, int count) throws IOException {
    var bytes = input.readNBytes(count);
    if (bytes.length != count) {
      throw new EOFException("EOF within a Bolt protocol field");
    }
    return bytes;
  }

  record Observation(String negotiatedVersion, int beginMessages, int commitMessages, int withheldCommitSuccesses,
      int forwardedCommitResponses) {
  }

  record Frame(byte[] wire, byte[] payload) {
  }

  private record PendingRequest(int tag, boolean intercept) {
  }

  private record VarInt(byte[] wire, long value) {
  }

  private static final class RelayShutdown implements AutoCloseable {
    private final BoltCommitAcknowledgementProxy proxy;

    private RelayShutdown(BoltCommitAcknowledgementProxy proxy) {
      this.proxy = proxy;
    }

    @Override
    public void close() throws IOException {
      proxy.stopRelays();
    }
  }
}
