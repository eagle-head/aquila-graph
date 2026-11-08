package org.aquilagraph;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Holds a real child JVM before main while its parent closes a selected native pipe reader. */
final class JvmStartBarrier implements AutoCloseable {
  private static final byte[] HANDSHAKE = "JDWP-Handshake".getBytes(StandardCharsets.US_ASCII);
  private static final int TIMEOUT_MILLIS = 10_000;
  private final ServerSocket listener;
  private Optional<Socket> connection = Optional.empty();

  JvmStartBarrier() throws IOException {
    listener = new ServerSocket();
    try {
      listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1);
      listener.setSoTimeout(TIMEOUT_MILLIS);
    } catch (IOException failure) {
      try (listener) {
        throw failure;
      }
    }
  }

  String javaOption() {
    return "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=127.0.0.1:" + listener.getLocalPort()
        + ",timeout=" + TIMEOUT_MILLIS;
  }

  void awaitStart() throws IOException {
    var socket = listener.accept();
    connection = Optional.of(socket);
    socket.setSoTimeout(TIMEOUT_MILLIS);
    var output = socket.getOutputStream();
    output.write(HANDSHAKE);
    output.flush();
    var input = new DataInputStream(socket.getInputStream());
    if (!Arrays.equals(HANDSHAKE, input.readNBytes(HANDSHAKE.length))) {
      throw new IOException("The child JVM did not complete its startup handshake");
    }
    var length = input.readInt();
    input.readInt();
    var flags = input.readUnsignedByte();
    var commandSet = input.readUnsignedByte();
    var command = input.readUnsignedByte();
    if (length < 17 || length > 1024 || flags != 0 || commandSet != 64 || command != 100) {
      throw new IOException("The child JVM did not supply a bounded VM-start event");
    }
    var payload = new byte[length - 11];
    input.readFully(payload);
    if (payload[0] != 2 || payload[1] != 0 || payload[2] != 0 || payload[3] != 0 || payload[4] != 1 || payload[5] != 90) {
      throw new IOException("The child JVM did not confirm suspended startup before main");
    }
  }

  void resume() throws IOException {
    var output = new DataOutputStream(connection.orElseThrow().getOutputStream());
    output.writeInt(11);
    output.writeInt(1);
    output.writeByte(0);
    output.writeByte(1);
    output.writeByte(9);
    output.flush();
  }

  @Override
  public void close() throws IOException {
    try (listener) {
      if (connection.isPresent()) {
        connection.orElseThrow().close();
      }
    }
  }
}
