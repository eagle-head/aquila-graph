package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Properties;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** One staged process reservation, creation, acknowledgement, permission and closure. */
final class CommandLaunch implements AutoCloseable {
  private final Object lock = new Object();
  private final InvocationContext context;
  private final BuildLease lease;
  private final Optional<Path> output;
  private final BuildJournal journal;
  private final BuildJournal command;
  private Optional<WindowsJobObject> nativeJob = Optional.empty();
  private Optional<Process> process = Optional.empty();
  private enum State {
    RESERVED, CREATED, ACKNOWLEDGED, PERMITTED, CLOSED
  }

  private State state = State.RESERVED;

  private CommandLaunch(BuildLease lease, Optional<Path> output, String kind) throws IOException {
    this.lease = lease;
    this.output = output;
    context = lease.context();
    journal = BuildJournal.reserve(context, "command-launcher");
    command = BuildJournal.reserve(context, kind);
    journal.recordCommand(command.reservation());
  }

  static CommandLaunch maven(BuildLease lease, Optional<Path> output) throws IOException {
    return new CommandLaunch(lease, output, "maven-wrapper");
  }

  static CommandLaunch git(BuildLease lease, Optional<Path> output, Optional<List<String>> arguments, int expectedExit) throws IOException {
    var launch = new CommandLaunch(lease, output, "git-inspection");
    launch.command.recordInspection(arguments, expectedExit);
    return launch;
  }

  Path resultFile() throws IOException {
    return command.observationDirectory().resolve("git-result.txt");
  }

  void start() throws IOException {
    synchronized (lock) {
      if (state != State.RESERVED) {
        throw new IllegalStateException("A reserved launch can create only one process");
      }
      var java = Path.of(System.getProperty("java.home"), "bin", BuildRequest.windows() ? "java.exe" : "java");
      var builder = new ProcessBuilder(java.toString(), "--enable-native-access=ALL-UNNAMED", context.entry("CommandChild").toString(),
          context.root().toString(), context.nonce(), journal.reservation()).directory(context.root().toFile());
      for (var name : List.of("AQUILA_BUILD_INVOCATION", "AQUILA_BUILD_RESERVATION", "AQUILA_BUILD_WRAPPER", "AQUILA_BUILD_WRAPPER_COMMAND",
          "AQUILA_BUILD_PROCESS", "AQUILA_BUILD_EFFECTIVE_GOALS")) {
        builder.environment().remove(name);
      }
      builder.environment().keySet().removeIf(name -> name.startsWith(AdapterArguments.ENVIRONMENT_PREFIX));
      if (output.isPresent()) {
        builder.redirectErrorStream(true).redirectOutput(output.orElseThrow().toFile());
      } else {
        builder.inheritIO();
      }
      state = State.CREATED;
      try {
        if (BuildRequest.windows()) {
          nativeJob = Optional.of(WindowsJobObject.create(BuildFiles.read(command.directory().resolve("intent.properties"))));
        }
        process = Optional.of(builder.start());
      } catch (IOException exception) {
        journal.closeWithoutLaunch();
        throw exception;
      }
    }
  }

  void awaitAcknowledgement() throws IOException, InterruptedException {
    var child = processFor(State.CREATED);
    var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    var acknowledgement = journal.directory().resolve("acknowledged.properties");
    Properties record;
    while (true) {
      try {
        record = BuildFiles.read(acknowledgement);
        break;
      } catch (NoSuchFileException exception) {
        if (!child.isAlive()) {
          throw new IOException("Source launcher exited before process acknowledgement, exit " + child.exitValue(), exception);
        }
        if (System.nanoTime() >= deadline) {
          throw new IOException("Source launcher acknowledgement exceeded 30 seconds", exception);
        }
        TimeUnit.MILLISECONDS.sleep(25);
      }
    }
    var identity = new ProcessIdentity(Long.parseLong(BuildFiles.required(record, "pid")),
        Instant.parse(BuildFiles.required(record, "started")));
    if (!identity.equals(ProcessIdentity.observe(child.toHandle()))) {
      throw new IOException("Source launcher acknowledgement differs from the created process identity");
    }
    synchronized (lock) {
      if (state != State.CREATED) {
        throw new IOException("Source launcher ownership closed before acknowledgement");
      }
      state = State.ACKNOWLEDGED;
    }
  }

  void permit() throws IOException {
    synchronized (lock) {
      if (state != State.ACKNOWLEDGED) {
        throw new IllegalStateException("Only one acknowledged live launch may receive a mutation permit");
      }
      lease.requireValid();
      BuildFiles.write(journal.directory().resolve("permitted.properties"), context.properties());
      state = State.PERMITTED;
    }
  }

  int awaitExit() throws IOException, InterruptedException {
    var child = processFor(State.PERMITTED);
    var complete = "verify-all".equals(BuildFiles.read(context.directory().resolve("invocation.properties")).getProperty("operation"));
    var failureDeadline = Long.MAX_VALUE;
    while (!child.waitFor(100, TimeUnit.MILLISECONDS)) {
      if (complete) {
        for (var scope : List.of("unit", "all")) {
          var failure = SuiteCatalog.directory(context.root(), context.nonce(), scope).resolve("failure.properties");
          if (Files.isRegularFile(failure)) {
            if (failureDeadline == Long.MAX_VALUE) {
              failureDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            }
            if (System.nanoTime() >= failureDeadline) {
              throw new IOException("Complete validation stopped after observed test failure: " + BuildFiles.read(failure));
            }
          }
        }
      }
      try (var descendants = child.descendants()) {
        for (var descendant : descendants.toList()) {
          journal.observe(descendant);
        }
      }
    }
    return child.exitValue();
  }

  private Process processFor(State required) throws IOException {
    synchronized (lock) {
      if (state != required) {
        throw new IOException("Source launcher is " + state + "; expected " + required);
      }
      return process.orElseThrow();
    }
  }

  void stop() throws IOException {
    synchronized (lock) {
      if (state == State.CLOSED) {
        return;
      }
      if (process.isEmpty()) {
        journal.closeWithoutLaunch();
      } else {
        journal.observe(process.orElseThrow().toHandle());
        journal.closeObserved();
      }
      command.closeObserved();
      if (nativeJob.isPresent()) {
        nativeJob.orElseThrow().close();
        nativeJob = Optional.empty();
      }
      state = State.CLOSED;
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (lock) {
      try {
        stop();
      } finally {
        if (process.isPresent()) {
          ProcessStreams.close(process.orElseThrow());
        }
      }
    }
  }
}
