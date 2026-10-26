package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.time.Instant;
import java.util.Optional;

/** A process identifier is meaningful only together with its observed start time. */
record ProcessIdentity(long pid, Instant started) {
  static String namespace() throws IOException {
    if (!BuildRequest.linux()) {
      return System.getProperty("os.name");
    }
    var identity = new StringBuilder(Files.readString(Path.of("/proc/sys/kernel/random/boot_id")).strip());
    for (var name : List.of("pid", "mnt", "user")) {
      identity.append('\n').append(Files.readSymbolicLink(Path.of("/proc/self/ns", name)));
    }
    identity.append('\n').append(System.getenv().getOrDefault("XDG_RUNTIME_DIR", "")).append('\n')
        .append(System.getenv().getOrDefault("DBUS_SESSION_BUS_ADDRESS", ""));
    try {
      return "Linux:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IOException("The required process namespace identity cannot be encoded", exception);
    }
  }

  static ProcessIdentity observe(ProcessHandle process) throws IOException {
    return new ProcessIdentity(process.pid(), process.info().startInstant()
        .orElseThrow(() -> new IOException("Process start time is unavailable for owned process " + process.pid())));
  }

  Optional<ProcessHandle> liveProcess() throws IOException {
    var process = ProcessHandle.of(pid);
    if (process.isEmpty() || !process.orElseThrow().isAlive()) {
      return Optional.empty();
    }
    var observed = observe(process.orElseThrow());
    return equals(observed) ? process : Optional.empty();
  }
}
