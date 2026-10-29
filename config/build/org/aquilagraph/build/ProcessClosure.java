package org.aquilagraph.build;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.aquilagraph.build.WindowsJobObject.Completion;

/** Bounded exact-identity process closure, including descendants observed before termination. */
final class ProcessClosure {
  private ProcessClosure() {
  }

  static void close(Process process, Duration timeout) throws IOException {
    var interrupted = Thread.interrupted();
    try {
      if (process.isAlive()) {
        process.destroy();
        try {
          process.waitFor(timeout.toMillis() / 2, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
          interrupted = true;
        }
        if (process.isAlive()) {
          process.destroyForcibly();
          try {
            process.waitFor(timeout.toMillis() / 2, TimeUnit.MILLISECONDS);
          } catch (InterruptedException exception) {
            interrupted = true;
          }
        }
        if (process.isAlive()) {
          throw new IOException("A returned process remains alive after bounded physical closure");
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  static void close(BuildJournal journal, Duration timeout) throws IOException {
    var deadline = System.nanoTime() + timeout.toNanos();
    var forceAt = System.nanoTime() + timeout.dividedBy(2).toNanos();
    var interrupted = Thread.interrupted();
    var active = new ArrayList<ProcessHandle>();
    try (var completion = new Completion()) {
      while (true) {
        active.clear();
        for (var identity : journal.identities()) {
          completion.observe(journal, identity);
          var live = identity.liveProcess();
          if (live.isPresent()) {
            var handle = live.orElseThrow();
            if (handle.pid() == ProcessHandle.current().pid()) {
              throw new IOException("A process cannot certify its own closure");
            }
            try (var descendants = handle.descendants()) {
              for (var descendant : descendants.toList()) {
                journal.observe(descendant);
              }
            }
            active.add(handle);
          }
        }
        if (active.isEmpty()) {
          completion.await(deadline);
          return;
        }
        for (var handle : active.reversed()) {
          if (System.nanoTime() >= forceAt) {
            handle.destroyForcibly();
          } else {
            handle.destroy();
          }
        }
        if (System.nanoTime() >= deadline) {
          throw new IOException("Owned process closure remains unresolved for reservation " + journal.reservation());
        }
        try {
          TimeUnit.MILLISECONDS.sleep(25);
        } catch (InterruptedException exception) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
