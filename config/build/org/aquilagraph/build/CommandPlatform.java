package org.aquilagraph.build;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import org.aquilagraph.build.WindowsJobKeeper.Lease;

/** Owns platform process creation and the native recovery handles required before admission. */
interface CommandPlatform extends Closeable {
  Process start(ProcessBuilder builder) throws IOException, InterruptedException;

  void requireAvailable() throws IOException;

  void stopObserved() throws IOException;

  static CommandPlatform local(InvocationContext context, BuildJournal command) {
    return new Native(context, command, Optional.empty(), Optional.empty());
  }

  /** Native resources remain owned until the command's journal certifies group closure. */
  final class Native implements CommandPlatform {
    private final InvocationContext context;
    private final BuildJournal command;
    private Optional<WindowsJobObject> job;
    private Optional<Lease> keeper;

    Native(InvocationContext context, BuildJournal command, Optional<WindowsJobObject> job, Optional<Lease> keeper) {
      this.context = context;
      this.command = command;
      this.job = job;
      this.keeper = keeper;
    }

    @Override
    public Process start(ProcessBuilder builder) throws IOException, InterruptedException {
      if (BuildRequest.windows()) {
        WindowsBootSession.capture(context);
        job = Optional.of(WindowsJobObject.create(BuildFiles.read(command.directory().resolve("intent.properties"))));
        keeper = Optional.of(WindowsJobKeeper.reserve(context, command));
        keeper.orElseThrow().start();
      }
      return builder.start();
    }

    @Override
    public void requireAvailable() throws IOException {
      if (keeper.isPresent()) {
        keeper.orElseThrow().requireAvailable();
      }
    }

    @Override
    public void stopObserved() throws IOException {
      command.stopObserved();
    }

    @Override
    public void close() throws IOException {
      try (Closeable _ = this::closeJob) {
        if (keeper.isPresent()) {
          keeper.orElseThrow().close();
        }
      }
    }

    private void closeJob() throws IOException {
      if (job.isPresent()) {
        job.orElseThrow().close();
        job = Optional.empty();
      }
    }
  }
}
