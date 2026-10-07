package org.aquilagraph.repository.neo4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.aquilagraph.repository.neo4j.BoltCommitAcknowledgementProxy.Frame;
import org.aquilagraph.repository.neo4j.BoltSchemaInspectionMessages.QueryKind;
import org.aquilagraph.repository.neo4j.BoltSchemaInspectionMessages.SchemaAcknowledgements;
import org.jspecify.annotations.Nullable;

/** Terminates its owned transport at schema inspection after both constraint results complete. */
final class BoltMetadataInspectionFailureProxy implements AutoCloseable {
  private static final int SOCKET_TIMEOUT_MILLIS = 20_000;
  private static final int SHUTDOWN_SECONDS = 5;
  private static final int BEGIN = 0x11;
  private static final int GOODBYE = 0x02;
  private static final int RECORD = 0x71;
  private static final int SUCCESS = 0x70;
  private static final int IGNORED = 0x7E;
  private static final int FAILURE = 0x7F;

  private final InetSocketAddress target;
  private final ServerSocket listener;
  private final ExecutorService relays = Executors.newFixedThreadPool(3, action -> {
    var thread = new Thread(action, "bolt-metadata-inspection-relay");
    thread.setDaemon(true);
    return thread;
  });
  private final ArrayBlockingQueue<PendingRequest> pending = new ArrayBlockingQueue<>(64);
  private final SchemaAcknowledgements schema = new SchemaAcknowledgements();
  private final AtomicReference<Socket> clientSocket = new AtomicReference<>();
  private final AtomicReference<Socket> backendSocket = new AtomicReference<>();
  private final AtomicReference<IOException> relayFailure = new AtomicReference<>();
  private final AtomicReference<String> negotiatedVersion = new AtomicReference<>("not negotiated");
  private final AtomicBoolean intercepted = new AtomicBoolean();
  private final AtomicBoolean stopped = new AtomicBoolean();
  private final AtomicInteger begins = new AtomicInteger();
  private final AtomicInteger inspectionRequests = new AtomicInteger();
  private final AtomicInteger forwardedInspections = new AtomicInteger();

  BoltMetadataInspectionFailureProxy(InetSocketAddress target) throws IOException {
    if (target.isUnresolved() || !target.getAddress().isLoopbackAddress()) {
      throw new IllegalArgumentException("The metadata observation requires a resolved loopback Bolt target");
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

  Observation observation() {
    return new Observation(negotiatedVersion.get(), schema.entityCompletions(), schema.runCompletions(), inspectionRequests.get(),
        forwardedInspections.get(), begins.get());
  }

  void requireHealthyRelay() throws IOException {
    var failure = relayFailure.get();
    if (failure != null) {
      throw failure;
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
      negotiatedVersion.set(BoltCommitAcknowledgementProxy.relayHandshake(client.getInputStream(), backend.getOutputStream(),
          backend.getInputStream(), client.getOutputStream()));
      relays.submit(() -> relayClient(client, backend));
      relays.submit(() -> relayServer(backend, client));
    } catch (IOException exception) {
      recordUnexpectedFailure(exception);
      disconnect();
    }
  }

  private void relayClient(Socket client, Socket backend) {
    var activeQuery = QueryKind.OTHER;
    try {
      Optional<Frame> next;
      while ((next = BoltCommitAcknowledgementProxy.readFrame(client.getInputStream())).isPresent()) {
        var frame = next.orElseThrow();
        if (frame.payload().length != 0) {
          var tag = BoltCommitAcknowledgementProxy.messageTag(frame.payload());
          if (tag == BEGIN) {
            begins.incrementAndGet();
          }
          if (tag == BoltSchemaInspectionMessages.RUN) {
            activeQuery = BoltSchemaInspectionMessages.queryKind(frame.payload());
            if (activeQuery == QueryKind.INSPECTION) {
              inspectionRequests.incrementAndGet();
              schema.requireInspectionPhase();
              if (begins.get() != 0 || !intercepted.compareAndSet(false, true)) {
                throw new IOException("The exact metadata request did not precede publication on its owned transport");
              }
              return;
            }
          }
          if (tag != GOODBYE && !pending.offer(new PendingRequest(tag, activeQuery), SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
            throw new IOException("The bounded schema request queue did not drain");
          }
        }
        backend.getOutputStream().write(frame.wire());
        backend.getOutputStream().flush();
        if (activeQuery == QueryKind.INSPECTION) {
          forwardedInspections.incrementAndGet();
        }
      }
    } catch (IOException exception) {
      recordUnexpectedFailure(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      recordUnexpectedFailure(new IOException("The schema client relay was interrupted", exception));
    } finally {
      disconnect();
    }
  }

  private void relayServer(Socket backend, Socket client) {
    try {
      Optional<Frame> next;
      while ((next = BoltCommitAcknowledgementProxy.readFrame(backend.getInputStream())).isPresent()) {
        var frame = next.orElseThrow();
        if (frame.payload().length != 0) {
          var tag = BoltCommitAcknowledgementProxy.messageTag(frame.payload());
          if (tag != RECORD) {
            if (tag != SUCCESS && tag != IGNORED && tag != FAILURE) {
              throw new IOException("The schema server returned an unsupported response tag");
            }
            var request = pending.poll(SHUTDOWN_SECONDS, TimeUnit.SECONDS);
            if (request == null) {
              throw new IOException("A schema response has no corresponding request");
            }
            schema.acceptSummary(request.tag(), request.query(), frame.payload());
          }
        }
        client.getOutputStream().write(frame.wire());
        client.getOutputStream().flush();
      }
    } catch (IOException exception) {
      recordUnexpectedFailure(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      recordUnexpectedFailure(new IOException("The schema server relay was interrupted", exception));
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
    if (!stopped.get() && !intercepted.get()) {
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
        throw new IOException("Owned metadata relay tasks did not terminate within the bound");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while awaiting owned metadata relay termination", exception);
    }
    requireHealthyRelay();
  }

  record Observation(String negotiatedVersion, int entityConstraintCompletions, int runConstraintCompletions, int inspectionRequests,
      int forwardedInspections, int beginMessages) {
  }

  private record PendingRequest(int tag, QueryKind query) {
  }

  private static final class RelayShutdown implements AutoCloseable {
    private final BoltMetadataInspectionFailureProxy proxy;

    private RelayShutdown(BoltMetadataInspectionFailureProxy proxy) {
      this.proxy = proxy;
    }

    @Override
    public void close() throws IOException {
      proxy.stopRelays();
    }
  }
}
