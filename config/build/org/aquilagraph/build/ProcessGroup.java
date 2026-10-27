package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.aquilagraph.build.WindowsJobObject.Child;

/** Owns the contained source bridge and complete native descendant lifetime. */
final class ProcessGroup implements AutoCloseable {
  private static final String CONTAINMENT_KIND = "containment.kind";
  private static final String WINDOWS_JOB = "windows-job";
  private static final String LINUX_SCOPE = "systemd-user-scope";
  private final InvocationContext context;
  private final BuildJournal journal;
  private final ProcessBuilder builder;
  private final Path arguments;
  private final Optional<WindowsJobObject> job;
  private Optional<Process> process = Optional.empty();
  private Optional<Child> nativeChild = Optional.empty();
  private boolean started;
  private boolean closed;

  private ProcessGroup(InvocationContext context, BuildJournal journal, ProcessBuilder builder, Path arguments,
      Optional<WindowsJobObject> job) {
    this.context = context;
    this.journal = journal;
    this.builder = builder;
    this.arguments = arguments;
    this.job = job;
  }

  static void record(Properties intent) throws IOException {
    if (BuildRequest.windows()) {
      intent.setProperty(CONTAINMENT_KIND, WINDOWS_JOB);
    } else if (BuildRequest.linux()) {
      intent.setProperty(CONTAINMENT_KIND, LINUX_SCOPE);
      LinuxScope.record(intent);
    } else {
      throw new IOException("Native descendant containment is unavailable on this operating system");
    }
  }

  static ProcessGroup prepare(InvocationContext context, BuildJournal launcher, BuildJournal command) throws IOException {
    var arguments = command.directory().resolve("command.args");
    var intent = BuildFiles.read(command.directory().resolve("intent.properties"));
    var bridge = switch (BuildFiles.required(intent, "kind")) {
      case "maven-wrapper" -> "MavenWrapper";
      case "git-inspection" -> "GitInspection";
      default -> throw new IOException("The reservation has an unsupported contained command kind");
    };
    JavaArguments.write(arguments, List.of("--enable-native-access=ALL-UNNAMED", context.entry(bridge).toString(),
        context.root().toString(), context.nonce(), command.reservation(), launcher.reservation()));
    var java = Path.of(System.getProperty("java.home"), "bin", BuildRequest.windows() ? "java.exe" : "java");
    var builder = new ProcessBuilder(java.toString(), "@" + arguments).directory(context.root().toFile()).inheritIO();
    if (WINDOWS_JOB.equals(BuildFiles.required(intent, CONTAINMENT_KIND))) {
      var job = WindowsJobObject.open(intent);
      if (job.isEmpty()) {
        throw new IOException("The build owner no longer holds the reserved Windows job");
      }
      return new ProcessGroup(context, command, builder, arguments, job);
    }
    if (!LINUX_SCOPE.equals(BuildFiles.required(intent, CONTAINMENT_KIND))) {
      throw new IOException("The reservation has an unsupported native containment kind");
    }
    LinuxScope.configure(builder, intent);
    return new ProcessGroup(context, command, builder, arguments, Optional.empty());
  }

  void start() throws IOException {
    if (started || closed) {
      throw new IOException("A native process group can start one command before closure");
    }
    started = true;
    journal.containedStart();
    if (job.isPresent()) {
      var java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
      nativeChild = Optional.of(job.orElseThrow().start(java, arguments, context.root()));
      journal.observe(nativeChild.orElseThrow().handle());
    } else {
      var child = builder.start();
      process = Optional.of(child);
      journal.observe(child.toHandle());
    }
  }

  boolean awaitSlice() throws IOException, InterruptedException {
    if (nativeChild.isPresent()) {
      return nativeChild.orElseThrow().awaitSlice();
    }
    return process.orElseThrow().waitFor(100, TimeUnit.MILLISECONDS);
  }

  int exitValue() throws IOException {
    return nativeChild.isPresent() ? nativeChild.orElseThrow().exitValue() : process.orElseThrow().exitValue();
  }

  static void close(BuildJournal journal) throws IOException {
    var intent = BuildFiles.read(journal.directory().resolve("intent.properties"));
    if (!List.of("maven-wrapper", "git-inspection").contains(BuildFiles.required(intent, "kind"))) {
      return;
    }
    switch (BuildFiles.required(intent, CONTAINMENT_KIND)) {
      case WINDOWS_JOB -> closeWindows(journal, intent);
      case LINUX_SCOPE -> {
        LinuxScope.close(intent);
        journal.containedClosed();
      }
      default -> throw new IOException("The reservation has an unsupported native containment kind");
    }
  }

  private static void closeWindows(BuildJournal journal, Properties intent) throws IOException {
    var existing = WindowsJobObject.open(intent);
    if (existing.isEmpty()) {
      if (Files.exists(journal.directory().resolve("contained-start.properties"))
          && !Files.exists(journal.directory().resolve("contained-closed.properties"))) {
        throw new IOException("All native Windows job handles were lost before complete closure was observed; mutation remains refused: "
            + journal.directory());
      }
      return;
    }
    try (var job = existing.orElseThrow()) {
      job.terminateAndObserveEmpty();
      journal.containedClosed();
    }
  }

  @Override
  public void close() throws IOException {
    closed = true;
    try {
      journal.closeObserved();
    } finally {
      try {
        if (nativeChild.isPresent()) {
          try (var _ = nativeChild.orElseThrow()) {
            // Release the native process handle after group closure has been reconciled.
          }
        }
        if (process.isPresent()) {
          ProcessStreams.close(process.orElseThrow());
        }
      } finally {
        if (job.isPresent()) {
          try (var _ = job.orElseThrow()) {
            // Keep the job handle alive through child and journal cleanup.
          }
        }
      }
    }
  }

  static final class RetainedJobs implements AutoCloseable {
    private final List<WindowsJobObject> jobs = new ArrayList<>();

    void retain(BuildJournal journal) throws IOException {
      var intent = BuildFiles.read(journal.directory().resolve("intent.properties"));
      if (List.of("maven-wrapper", "git-inspection").contains(BuildFiles.required(intent, "kind"))
          && WINDOWS_JOB.equals(BuildFiles.required(intent, CONTAINMENT_KIND))) {
        WindowsJobObject.open(intent).ifPresent(jobs::add);
      }
    }

    @Override
    public void close() throws IOException {
      Optional<IOException> failure = Optional.empty();
      for (var job : jobs.reversed()) {
        try (var _ = job) {
          // Attempt every retained handle even if an earlier release failed.
        } catch (IOException exception) {
          if (failure.isPresent()) {
            failure.orElseThrow().addSuppressed(exception);
          } else {
            failure = Optional.of(exception);
          }
        }
      }
      jobs.clear();
      if (failure.isPresent()) {
        throw failure.orElseThrow();
      }
    }
  }
}
