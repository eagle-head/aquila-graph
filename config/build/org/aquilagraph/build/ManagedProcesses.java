package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Owns the same staged and contained lifetime for Maven and Git commands. */
final class ManagedProcesses implements AutoCloseable {
  private final BuildLease lease;
  private final Optional<Path> output;
  private final List<CommandLaunch> launches = new ArrayList<>();
  private boolean closing;

  ManagedProcesses(BuildLease lease, Optional<Path> output) {
    this.lease = lease;
    this.output = output;
  }

  int maven() throws IOException, InterruptedException {
    try (var launch = reserveMaven()) {
      return execute(launch);
    }
  }

  void checkLineEndings() throws IOException, InterruptedException {
    try (var launch = reserveGit(Optional.empty(), 0)) {
      if (execute(launch) != 0) {
        throw new IOException("The contained Git text verification failed");
      }
    }
  }

  String git(List<String> arguments, int expectedExit) throws IOException, InterruptedException {
    try (var launch = reserveGit(Optional.of(arguments), expectedExit)) {
      if (execute(launch) != 0) {
        throw new IOException("The contained Git observation failed");
      }
      return Files.readString(launch.resultFile());
    }
  }

  private int execute(CommandLaunch launch) throws IOException, InterruptedException {
    synchronized (this) {
      requireOpen();
      launch.start();
    }
    launch.awaitAcknowledgement();
    synchronized (this) {
      requireOpen();
      launch.permit();
    }
    return launch.awaitExit();
  }

  private synchronized CommandLaunch reserveMaven() throws IOException {
    requireOpen();
    var launch = CommandLaunch.maven(lease, output);
    launches.add(launch);
    return launch;
  }

  private synchronized CommandLaunch reserveGit(Optional<List<String>> arguments, int expectedExit) throws IOException {
    requireOpen();
    var launch = CommandLaunch.git(lease, output, arguments, expectedExit);
    launches.add(launch);
    return launch;
  }

  private void requireOpen() throws IOException {
    if (closing) {
      throw new IOException("Local process ownership is closing");
    }
  }

  @Override
  public synchronized void close() throws IOException {
    closing = true;
    for (var launch : List.copyOf(launches)) {
      try (launch) {
        launch.stop();
      }
    }
    BuildJournal.closeInvocation(lease.context());
  }
}
