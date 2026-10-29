package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** A native uptime reset establishes a restart independently of wall-clock adjustments. */
record WindowsBootSession(String computerName, Instant started, long uptimeMillis) {
  WindowsBootSession {
    Objects.requireNonNull(computerName, "computerName");
    Objects.requireNonNull(started, "started");
    if (computerName.isBlank() || uptimeMillis < 0) {
      throw new IllegalArgumentException("Windows boot evidence requires a computer and nonnegative native uptime");
    }
  }

  static WindowsBootSession parse(String output, long uptimeMillis) throws IOException {
    var fields = output.strip().split("\t", -1);
    if (fields.length != 2 || fields[0].isBlank() || uptimeMillis < 0) {
      throw new IOException("Windows boot observation requires one computer, restart time and nonnegative native uptime");
    }
    try {
      return new WindowsBootSession(fields[0], Instant.parse(fields[1]), uptimeMillis);
    } catch (DateTimeParseException exception) {
      throw new IOException("Windows boot observation contains an invalid restart time", exception);
    }
  }

  static void capture(InvocationContext context) throws IOException {
    var file = context.directory().resolve("host.properties");
    try {
      var recorded = BuildFiles.read(file);
      context.requireIdentity(recorded);
      read(recorded);
    } catch (NoSuchFileException exception) {
      var observed = WindowsProcessInventory.boot(context.properties());
      BuildFiles.write(file, observed.observation(context));
    }
  }

  static Optional<Restart> reconcile(BuildJournal journal, WindowsBootSession observed) throws IOException {
    var context = journal.invocationContext();
    var intent = BuildFiles.read(journal.directory().resolve("intent.properties"));
    context.requireIdentity(intent);
    var baseline = baseline(context);
    if (baseline.isPresent()) {
      var original = baseline.orElseThrow();
      if (!original.computerName().equalsIgnoreCase(observed.computerName())) {
        throw new IOException("Restart evidence belongs to another Windows computer");
      }
      if (observed.uptimeMillis() < original.uptimeMillis()) {
        return Optional.of(new Restart(context, observed));
      }
    } else {
      var account = BuildFiles.required(intent, "account");
      var separator = account.indexOf('\\');
      if (separator < 1 || separator == account.length() - 1
          || !account.substring(0, separator).equalsIgnoreCase(observed.computerName())) {
        throw new IOException("Historical Windows ownership cannot be associated with this computer");
      }
    }
    var record = observed.observation(context);
    record.setProperty("observation.purpose", "unresolved-resource-recovery");
    BuildFiles.write(context.directory().resolve("recovery-host.properties"), record);
    return Optional.empty();
  }

  private static Optional<WindowsBootSession> baseline(InvocationContext context) throws IOException {
    return baseline(context.directory(), context.properties());
  }

  private static Optional<WindowsBootSession> baseline(Path directory, Properties ownership) throws IOException {
    for (var name : new String[]{"recovery-host.properties", "host.properties"}) {
      var recorded = recorded(directory, ownership, name);
      if (recorded.isPresent()) {
        return recorded;
      }
    }
    return Optional.empty();
  }

  static void requireRestartEvidence(Path directory, Properties ownership, Properties record) throws IOException {
    InvocationContext.requireIdentity(ownership, record);
    var original = baseline(directory, ownership).orElseThrow(() -> new IOException("Restart closure lacks its native uptime baseline"));
    var observed = read(record);
    if (record.containsKey("active.members") || !original.computerName().equalsIgnoreCase(observed.computerName())
        || observed.uptimeMillis() >= original.uptimeMillis()) {
      throw new IOException("Restart closure does not establish a same-computer native uptime reset");
    }
  }

  private static Optional<WindowsBootSession> recorded(Path directory, Properties ownership, String name) throws IOException {
    try {
      var record = BuildFiles.read(directory.resolve(name));
      InvocationContext.requireIdentity(ownership, record);
      return Optional.of(read(record));
    } catch (NoSuchFileException exception) {
      return Optional.empty();
    }
  }

  private static WindowsBootSession read(Properties record) throws IOException {
    try {
      return parse(BuildFiles.required(record, "computer.name") + "\t" + BuildFiles.required(record, "boot.started"),
          Long.parseLong(BuildFiles.required(record, "uptime.millis")));
    } catch (NumberFormatException exception) {
      throw new IOException("Recorded Windows native uptime is invalid", exception);
    }
  }

  void record(Properties record) {
    record.setProperty("computer.name", computerName);
    record.setProperty("boot.started", started.toString());
    record.setProperty("uptime.millis", Long.toString(uptimeMillis));
  }

  private Properties observation(InvocationContext context) throws IOException {
    var record = context.properties();
    record(record);
    var observer = ProcessIdentity.observe(ProcessHandle.current());
    record.setProperty("observer.pid", Long.toString(observer.pid()));
    record.setProperty("observer.started", observer.started().toString());
    return record;
  }

  /** Only reconciliation can establish this closure authority for an exact invocation. */
  static final class Restart {
    private final InvocationContext original;
    private final WindowsBootSession observed;

    private Restart(InvocationContext original, WindowsBootSession observed) {
      this.original = original;
      this.observed = observed;
    }

    void requireOwner(InvocationContext context) throws IOException {
      original.requireIdentity(context.properties());
    }

    void record(Properties record) {
      observed.record(record);
      record.setProperty("closure.evidence", "host-restarted");
    }
  }
}
