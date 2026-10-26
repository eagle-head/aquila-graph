package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Acknowledges creation before the owner permits any wrapper or Maven work. */
public final class CommandChild {
  private CommandChild() {
  }

  /**
   * Executes one acknowledged command request while its exact owner remains alive.
   *
   * @param arguments
   *          canonical root, invocation nonce, and reservation
   * @throws IOException
   *           if ownership, launch, or closure cannot be established
   * @throws InterruptedException
   *           if this managed child is interrupted
   */
  public static void main(String[] arguments) throws IOException, InterruptedException {
    var context = InvocationContext.read(Path.of(arguments[0]), arguments[1]);
    var journal = BuildJournal.open(context, arguments[2]);
    BuildLease.requireHeld(context);
    journal.acknowledge();
    var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (!Files.exists(journal.directory().resolve("permitted.properties"))) {
      context.requireLiveOwner();
      if (System.nanoTime() >= deadline) {
        throw new IOException("The build owner did not acknowledge this child within 30 seconds");
      }
      TimeUnit.MILLISECONDS.sleep(25);
    }
    BuildLease.requireHeld(context);
    var intent = BuildFiles.read(journal.directory().resolve("intent.properties"));
    var command = BuildJournal.open(context, BuildFiles.required(intent, "command.reservation"));
    int exit;
    try (var group = ProcessGroup.prepare(context, journal, command)) {
      context.requireLiveOwner();
      group.start();
      while (!group.awaitSlice()) {
        context.requireLiveOwner();
      }
      exit = group.exitValue();
    }
    System.exit(exit);
  }
}
