package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Exact process birth identity, retaining the observed wall-clock time as metadata. */
record ProcessIdentity(long pid, Instant started, Optional<LinuxProcessStart> linuxStart) {
  private static final String BOOT_ID = "start.boot-id";
  private static final String START_TICKS = "start.ticks";

  ProcessIdentity {
    Objects.requireNonNull(started, "started");
    Objects.requireNonNull(linuxStart, "linuxStart");
  }

  ProcessIdentity(long pid, Instant started) {
    this(pid, started, Optional.empty());
  }

  static ProcessIdentity read(Properties record, String prefix) throws IOException {
    try {
      var pid = Long.parseLong(BuildFiles.required(record, prefix + "pid"));
      var started = Instant.parse(BuildFiles.required(record, prefix + "started"));
      var nativeStart = Optional.<LinuxProcessStart>empty();
      if (record.containsKey(prefix + BOOT_ID) || record.containsKey(prefix + START_TICKS)) {
        nativeStart = Optional.of(new LinuxProcessStart(BuildFiles.required(record, prefix + BOOT_ID),
            Long.parseLong(BuildFiles.required(record, prefix + START_TICKS))));
      }
      return new ProcessIdentity(pid, started, nativeStart);
    } catch (IllegalArgumentException | DateTimeException exception) {
      throw new IOException("Recorded process identity is malformed: " + prefix, exception);
    }
  }

  void write(Properties record, String prefix) {
    record.setProperty(prefix + "pid", Long.toString(pid));
    record.setProperty(prefix + "started", started.toString());
    record.remove(prefix + BOOT_ID);
    record.remove(prefix + START_TICKS);
    linuxStart.ifPresent(value -> {
      record.setProperty(prefix + BOOT_ID, value.bootId());
      record.setProperty(prefix + START_TICKS, Long.toString(value.ticks()));
    });
  }

  static void requireNativeIdentity(Properties expected, Properties record, String prefix) throws IOException {
    for (var name : List.of(BOOT_ID, START_TICKS)) {
      if (!Objects.equals(expected.getProperty(prefix + name), record.getProperty(prefix + name))) {
        throw new IOException("Build record differs from the exact native process owner: " + prefix + name);
      }
    }
  }
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
    return observeAvailable(process)
        .orElseThrow(() -> new IOException("Process start time is unavailable for owned process " + process.pid()));
  }

  static Optional<ProcessIdentity> observeAvailable(ProcessHandle process) throws IOException {
    var started = process.info().startInstant();
    if (started.isPresent()) {
      if (BuildRequest.linux()) {
        try {
          var nativeStart = LinuxProcessStart.observe(process.pid());
          return process.isAlive()
              ? Optional.of(new ProcessIdentity(process.pid(), started.orElseThrow(), Optional.of(nativeStart)))
              : Optional.empty();
        } catch (NoSuchFileException exception) {
          if (process.isAlive()) {
            throw new IOException("Linux process birth is unavailable for a live process: " + process.pid(), exception);
          }
          return Optional.empty();
        }
      }
      return Optional.of(new ProcessIdentity(process.pid(), started.orElseThrow()));
    }
    if (process.isAlive()) {
      throw new IOException("Process start time is unavailable for owned process " + process.pid());
    }
    return Optional.empty();
  }

  Optional<ProcessHandle> liveProcess() throws IOException {
    if (linuxStart.isPresent() && !BuildRequest.linux()) {
      throw new IOException("A Linux process identity requires observation on its native platform");
    }
    var process = ProcessHandle.of(pid);
    if (process.isEmpty() || !process.orElseThrow().isAlive()) {
      return Optional.empty();
    }
    var observed = observeAvailable(process.orElseThrow());
    if (observed.isEmpty()) {
      return Optional.empty();
    }
    if (linuxStart.isPresent()) {
      return equals(observed.orElseThrow()) ? process : Optional.empty();
    }
    if (started.equals(observed.orElseThrow().started())) {
      return process;
    }
    if (BuildRequest.linux()) {
      throw new IOException("Legacy Linux process identity cannot resolve a live PID without native birth evidence: " + pid);
    }
    return Optional.empty();
  }

  @Override
  public boolean equals(Object value) {
    if (!(value instanceof ProcessIdentity other) || pid != other.pid) {
      return false;
    }
    return (linuxStart.isPresent() || other.linuxStart.isPresent()) ? linuxStart.equals(other.linuxStart) : started.equals(other.started);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pid, linuxStart.isPresent() ? linuxStart.orElseThrow() : started);
  }
}
