package org.aquilagraph.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Native process birth coordinates independent of wall-clock conversion. */
record LinuxProcessStart(String bootId, long ticks) {
  private static final int START_TIME_INDEX = 19;
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  LinuxProcessStart {
    Objects.requireNonNull(bootId, "bootId");
    if (ticks < 0 || !UUID.fromString(bootId).toString().equals(bootId)) {
      throw new IllegalArgumentException("Linux process birth requires a canonical boot UUID and nonnegative start ticks");
    }
  }

  static LinuxProcessStart observe(long pid) throws IOException {
    return observe(Path.of("/proc"), pid);
  }

  static LinuxProcessStart observe(Path proc, long pid) throws IOException {
    var boot = Files.readString(proc.resolve("sys/kernel/random/boot_id"), StandardCharsets.US_ASCII).strip();
    var stat = Files.readString(proc.resolve(Long.toString(pid)).resolve("stat"), StandardCharsets.ISO_8859_1);
    var command = stat.indexOf(" (");
    var end = stat.lastIndexOf(") ");
    if (command < 1 || end <= command) {
      throw new IOException("Linux process status has no complete command field: " + pid);
    }
    var fields = WHITESPACE.splitAsStream(stat.substring(end + 2).strip()).toList();
    if (fields.size() <= START_TIME_INDEX) {
      throw new IOException("Linux process status has no start-time field: " + pid);
    }
    try {
      if (Long.parseLong(stat.substring(0, command)) != pid) {
        throw new IOException("Linux process status belongs to a different PID: " + pid);
      }
      return new LinuxProcessStart(boot, Long.parseLong(fields.get(START_TIME_INDEX)));
    } catch (IllegalArgumentException exception) {
      throw new IOException("Linux process birth coordinates are malformed: " + pid, exception);
    }
  }
}
