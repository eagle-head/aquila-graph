package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Holds only the recovery handle; build execution authority remains with the original owner. */
public final class WindowsJobKeeper {
  private static final int ARGUMENT_COUNT = 4;
  private static final String ACCOUNT = "account";
  private WindowsJobKeeper() {
  }

  /**
   * Retains a native group across requester-tree termination and observes its actual closure.
   *
   * @param arguments
   *          canonical root, invocation nonce, command reservation and keeper reservation
   * @throws IOException
   *           if ownership, native observation or journal publication fails
   * @throws InterruptedException
   *           if waiting for an ownership transition is interrupted
   */
  public static void main(String[] arguments) throws IOException, InterruptedException {
    if (arguments.length != ARGUMENT_COUNT) {
      throw new IllegalArgumentException("A recovery holder requires one exact invocation and two reservations");
    }
    var context = InvocationContext.read(Path.of(arguments[0]), arguments[1]);
    var command = BuildJournal.open(context, arguments[2]);
    var keeper = BuildJournal.open(context, arguments[3]);
    var intent = BuildFiles.read(keeper.directory().resolve("intent.properties"));
    context.requireIdentity(intent);
    if (!"job-keeper".equals(BuildFiles.required(intent, "kind"))
        || !command.reservation().equals(BuildFiles.required(intent, "command.reservation"))) {
      throw new IOException("Recovery holder belongs to another command reservation");
    }
    var account = ProcessHandle.current().info().user().orElseThrow(() -> new IOException("Recovery holder account is unavailable"));
    if (!BuildFiles.required(intent, ACCOUNT).equalsIgnoreCase(account)) {
      throw new IOException("Recovery holder account differs from the reserved account");
    }
    var parent = ProcessHandle.current().parent().orElseThrow(() -> new IOException("Recovery holder launcher is unavailable"));
    var launcher = ProcessIdentity.observe(parent);
    try (var job = WindowsJobObject.open(BuildFiles.read(command.directory().resolve("intent.properties")))
        .orElseThrow(() -> new IOException("Recovery holder requires the original native job"))) {
      var acknowledgement = keeper.acknowledgement();
      acknowledgement.setProperty("launcher.pid", Long.toString(launcher.pid()));
      acknowledgement.setProperty("launcher.started", launcher.started().toString());
      acknowledgement.setProperty(ACCOUNT, account);
      BuildFiles.write(keeper.directory().resolve("acknowledged.properties"), acknowledgement);
      hold(context, command, job);
    }
  }

  static void hold(InvocationContext context, BuildJournal command, WindowsJobObject job) throws IOException, InterruptedException {
    while (!completed(context, command)) {
      if (context.owner().liveProcess().isEmpty()) {
        job.terminateAndObserveEmpty();
        command.containedClosed();
        return;
      }
      TimeUnit.MILLISECONDS.sleep(25);
    }
  }

  private static boolean completed(InvocationContext context, BuildJournal command) throws IOException {
    context.requireIdentity(command.invocationContext().properties());
    return command.containmentClosed();
  }

  static Lease reserve(InvocationContext context, BuildJournal command) throws IOException {
    var journal = BuildJournal.reserve(context, "job-keeper");
    journal.recordCommand(command.reservation());
    return new Lease(context, command, journal);
  }

  static ProcessIdentity acknowledgement(InvocationContext context, BuildJournal journal, Properties record, ProcessIdentity launcher)
      throws IOException {
    context.requireIdentity(record);
    if (!journal.reservation().equals(BuildFiles.required(record, "reservation"))
        || !Long.toString(launcher.pid()).equals(BuildFiles.required(record, "launcher.pid"))
        || !launcher.started().toString().equals(BuildFiles.required(record, "launcher.started"))) {
      throw new IOException("Recovery holder acknowledgement differs from the reserved launcher");
    }
    var intent = BuildFiles.read(journal.directory().resolve("intent.properties"));
    if (!BuildFiles.required(intent, ACCOUNT).equalsIgnoreCase(BuildFiles.required(record, ACCOUNT))) {
      throw new IOException("Recovery holder acknowledgement differs from the reserved account");
    }
    return WindowsProcessBroker.creation(BuildFiles.required(record, "pid") + "\t" + BuildFiles.required(record, "started"));
  }

  /** One staged keeper creation and closure, including failures before its acknowledgement. */
  static final class Lease implements AutoCloseable {
    private final InvocationContext context;
    private final BuildJournal command;
    private final BuildJournal journal;
    private Optional<ProcessIdentity> launcher = Optional.empty();
    private Optional<ProcessIdentity> holder = Optional.empty();
    private boolean closed;

    private Lease(InvocationContext context, BuildJournal command, BuildJournal journal) {
      this.context = context;
      this.command = command;
      this.journal = journal;
    }

    void start() throws IOException, InterruptedException {
      var temporary = Files.createDirectories(journal.directory().resolve("temporary"));
      var arguments = journal.directory().resolve("keeper.args");
      JavaArguments.write(arguments,
          List.of("--enable-native-access=ALL-UNNAMED", "-Xms16m", "-Xmx256m", "-Djava.io.tmpdir=" + temporary,
              context.entry("WindowsJobKeeper").toString(), context.root().toString(), context.nonce(), command.reservation(),
              journal.reservation()));
      var log = journal.directory().resolve("output.log");
      launcher = Optional.of(WindowsProcessBroker.launch(journal, arguments, log, temporary));
      var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (true) {
        BuildCancellation.check(context);
        context.requireLiveOwner();
        var acknowledged = acknowledgedHolder(log);
        if (acknowledged.isPresent()) {
          holder = acknowledged;
          return;
        }
        if (launcher.orElseThrow().liveProcess().isEmpty() || System.nanoTime() >= deadline) {
          throw new IOException("Recovery holder did not acknowledge native ownership; log: " + log);
        }
        TimeUnit.MILLISECONDS.sleep(25);
      }
    }

    private Optional<ProcessIdentity> acknowledgedHolder(Path log) throws IOException {
      Properties record;
      try {
        record = BuildFiles.read(journal.directory().resolve("acknowledged.properties"));
      } catch (NoSuchFileException exception) {
        return Optional.empty();
      }
      var identity = acknowledgement(context, journal, record, launcher.orElseThrow());
      var process = identity.liveProcess().orElseThrow(() -> new IOException("Recovery holder exited after acknowledgement; log: " + log));
      journal.observe(process);
      return Optional.of(identity);
    }

    void requireAvailable() throws IOException {
      if (holder.isEmpty() || holder.orElseThrow().liveProcess().isEmpty()) {
        if (!completed(context, command)) {
          throw new IOException("Recovery holder exited before verified native closure: " + journal.directory());
        }
      }
    }

    @Override
    public void close() throws IOException {
      if (!closed) {
        if (journal.closed()) {
          closed = true;
          return;
        }
        if (holder.isEmpty()) {
          for (var process : ProcessInventory.reservation(journal.directory())) {
            journal.observe(process);
          }
        }
        journal.closeObserved();
        closed = true;
      }
    }
  }
}
