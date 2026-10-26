package org.aquilagraph.build;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/** Bounded exact-identity process closure, including descendants observed before termination. */
final class ProcessClosure {
  private ProcessClosure() {
  }

  static void close(BuildJournal journal, Duration timeout) throws IOException {
    var deadline = System.nanoTime() + timeout.toNanos();
    var forceAt = System.nanoTime() + timeout.dividedBy(2).toNanos();
    var interrupted = Thread.interrupted();
    var active = new ArrayList<ProcessHandle>();
    try {
      while (true) {
        active.clear();
        for (var identity : journal.identities()) {
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
