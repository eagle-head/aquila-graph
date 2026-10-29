package org.aquilagraph.build;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.aquilagraph.build.WindowsKernel.ProcessAccessDeniedException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Native job membership survives parent exit and cannot be escaped by ordinary child creation. */
final class WindowsJobObject implements AutoCloseable {
  private static final int WAIT_TIMEOUT = 0x102;
  private final MemorySegment handle;
  private final WindowsKernel kernel;

  WindowsJobObject(MemorySegment handle, WindowsKernel kernel) {
    this.handle = handle;
    this.kernel = kernel;
  }

  static WindowsJobObject create(Properties intent) throws IOException {
    requireAccess();
    var kernel = WindowsKernel.instance();
    try (var acquisition = new Acquisition(kernel.create(name(intent)), kernel)) {
      kernel.killWhenLastHandleCloses(acquisition.handle());
      return acquisition.transfer();
    }
  }

  static Optional<WindowsJobObject> open(Properties intent) throws IOException {
    requireAccess();
    var kernel = WindowsKernel.instance();
    return kernel.open(name(intent)).map(handle -> new WindowsJobObject(handle, kernel));
  }

  Child start(Path java, Path arguments, Path directory) throws IOException {
    return kernel.start(handle, java, arguments, directory);
  }

  void terminateAndObserveEmpty() throws IOException {
    var interrupted = Thread.interrupted();
    try {
      kernel.terminate(handle);
      var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
      while (kernel.activeProcesses(handle) != 0) {
        if (System.nanoTime() >= deadline) {
          throw new IOException("Owned Windows job closure remains unresolved");
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

  private static void requireAccess() throws IOException {
    if (!WindowsJobObject.class.getModule().isNativeAccessEnabled()) {
      throw new IOException("Windows descendant containment requires --enable-native-access=ALL-UNNAMED");
    }
  }

  private static String name(Properties intent) throws IOException {
    return "Local\\AquilaBuild-" + BuildFiles.required(intent, "nonce") + "-" + BuildFiles.required(intent, "reservation");
  }

  @Override
  public void close() throws IOException {
    kernel.close(handle);
  }

  private static final class Acquisition implements AutoCloseable {
    private Optional<WindowsJobObject> job;

    Acquisition(MemorySegment handle, WindowsKernel kernel) {
      job = Optional.of(new WindowsJobObject(handle, kernel));
    }

    MemorySegment handle() {
      return job.orElseThrow().handle;
    }

    WindowsJobObject transfer() {
      var result = job.orElseThrow();
      job = Optional.empty();
      return result;
    }

    @Override
    public void close() throws IOException {
      if (job.isPresent()) {
        job.orElseThrow().close();
      }
    }
  }

  static final class Child implements AutoCloseable {
    private final MemorySegment process;
    private final long pid;
    private final WindowsKernel kernel;

    Child(MemorySegment process, long pid, WindowsKernel kernel) {
      this.process = process;
      this.pid = pid;
      this.kernel = kernel;
    }

    ProcessHandle handle() throws IOException {
      return ProcessHandle.of(pid).orElseThrow(() -> new IOException("The created contained process is no longer observable: " + pid));
    }

    boolean awaitSlice() throws IOException, InterruptedException {
      if (Thread.interrupted()) {
        throw new InterruptedException("The contained Windows process wait was interrupted");
      }
      var result = kernel.invokeInt("WaitForSingleObject", process, 100);
      if (result == 0) {
        return true;
      }
      if (result == WAIT_TIMEOUT) {
        return false;
      }
      throw new IOException("The contained Windows process wait failed: " + result);
    }

    int exitValue() throws IOException {
      try (var arena = Arena.ofConfined()) {
        var result = arena.allocate(ValueLayout.JAVA_INT);
        kernel.requireSuccess("GetExitCodeProcess", process, result);
        return result.get(ValueLayout.JAVA_INT, 0);
      }
    }

    @Override
    public void close() throws IOException {
      kernel.close(process);
    }
  }

  /** Keeps exact native process objects available until their termination is complete. */
  static final class Completion implements AutoCloseable {
    private final Map<ProcessIdentity, Child> processes = new HashMap<>();
    private final Set<ProcessIdentity> superseded = new HashSet<>();

    void observe(BuildJournal journal, ProcessIdentity identity) throws IOException {
      if (BuildRequest.windows() && !processes.containsKey(identity) && !superseded.contains(identity)) {
        requireAccess();
        try {
          var process = WindowsKernel.instance().openProcess(identity);
          if (process.isPresent()) {
            processes.put(identity, process.orElseThrow());
          }
        } catch (ProcessAccessDeniedException denied) {
          Optional<ProcessIdentity> current;
          try {
            current = WindowsProcessInventory.identity(journal.invocationContext().properties(), identity.pid());
          } catch (IOException failure) {
            denied.addSuppressed(failure);
            throw denied;
          }
          if (current.isEmpty() || identity.equals(current.orElseThrow())) {
            throw denied;
          }
          superseded.add(identity);
        }
      }
    }

    void await(long deadline) throws IOException {
      var interrupted = Thread.interrupted();
      try {
        for (var entry : processes.entrySet()) {
          while (!WindowsKernel.instance().completed(entry.getValue().process)) {
            if (System.nanoTime() >= deadline) {
              throw new IOException("Native process termination remains unresolved for PID " + entry.getKey().pid());
            }
            try {
              TimeUnit.MILLISECONDS.sleep(25);
            } catch (InterruptedException exception) {
              interrupted = true;
            }
          }
        }
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }

    @Override
    public void close() throws IOException {
      Optional<IOException> failure = Optional.empty();
      for (var process : processes.values()) {
        try (var _ = process) {
          // Release every observation handle even when another release fails.
        } catch (IOException exception) {
          if (failure.isPresent()) {
            failure.orElseThrow().addSuppressed(exception);
          } else {
            failure = Optional.of(exception);
          }
        }
      }
      processes.clear();
      superseded.clear();
      if (failure.isPresent()) {
        throw failure.orElseThrow();
      }
    }
  }

}
