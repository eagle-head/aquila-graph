package org.aquilagraph.testsupport;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Owns a launched process and its descendants through normal or interrupted completion. */
public final class OwnedProcess implements AutoCloseable {
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);
  private static final long OBSERVATION_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
  private final Process process;
  private final Set<ProcessHandle> descendants = new LinkedHashSet<>();
  private boolean closed;

  private OwnedProcess(Process process) {
    this.process = process;
  }

  /** Starts one owned external operation. */
  public static OwnedProcess start(ProcessBuilder builder) throws IOException {
    return new OwnedProcess(Objects.requireNonNull(builder, "builder").start());
  }

  /** Waits for the launcher while retaining ownership of its actual descendants. */
  public boolean await(Duration timeout) throws InterruptedException {
    if (closed || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("An open process requires a positive wait duration");
    }
    var deadline = System.nanoTime() + timeout.toNanos();
    try {
      long remaining;
      do {
        rememberDescendants();
        remaining = deadline - System.nanoTime();
        if (remaining > 0 && process.waitFor(Math.min(remaining, OBSERVATION_NANOS), TimeUnit.NANOSECONDS)) {
          return true;
        }
      } while (remaining > 0);
      return !process.isAlive();
    } finally {
      rememberDescendants();
    }
  }

  /** Returns the launcher's actual exit value after normal completion or closure. */
  public int exitValue() {
    return process.exitValue();
  }

  /** Returns the exact created launcher for deliberate termination controls. */
  public ProcessHandle handle() {
    return process.toHandle();
  }

  /** Closes the parent's read handle so child stdout writes reach an actual broken pipe. */
  public void closeOutputReader() throws IOException {
    process.getInputStream().close();
  }

  /** Closes the parent's read handle so child stderr writes reach an actual broken pipe. */
  public void closeErrorReader() throws IOException {
    process.getErrorStream().close();
  }

  private void rememberDescendants() {
    process.descendants().forEach(descendants::add);
    for (var descendant : new ArrayList<>(descendants)) {
      descendant.descendants().forEach(descendants::add);
    }
  }

  /** Closes the owned tree with one bounded deadline, preserving interruption. */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    var interrupted = Thread.interrupted();
    try {
      rememberDescendants();
      var owned = new ArrayList<>(descendants);
      Collections.reverse(owned);
      owned.add(process.toHandle());
      for (var handle : owned) {
        if (handle.isAlive()) {
          handle.destroyForcibly();
        }
      }
      var deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
      for (var handle : owned) {
        while (handle.isAlive()) {
          var remaining = deadline - System.nanoTime();
          if (remaining <= 0) {
            throw new IOException("Owned process did not close: " + handle.pid());
          }
          try {
            handle.onExit().get(remaining, TimeUnit.NANOSECONDS);
          } catch (InterruptedException interruption) {
            interrupted = true;
          } catch (ExecutionException | TimeoutException failure) {
            throw new IOException("Owned process closure failed: " + handle.pid(), failure);
          }
        }
      }
      // Complete the launcher's own wait before exposing its exit value.
      while (process.isAlive()) {
        var remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          throw new IOException("Owned launcher did not publish its exit: " + process.pid());
        }
        try {
          process.waitFor(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interruption) {
          interrupted = true;
        }
      }
      try (var input = process.getInputStream(); var error = process.getErrorStream(); var output = process.getOutputStream()) {
        closed = true;
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
