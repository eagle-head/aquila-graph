package org.aquilagraph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.testsupport.OwnedProcess;

/** Executes the packaged analyzer with bounded lifetime and captured, separate streams. */
final class PackagedAnalyzerProcess {
  enum ClosedReader { NONE, OUTPUT, ERROR }
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);
  private final Path directory;

  PackagedAnalyzerProcess(Path directory) {
    this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
  }

  Result run(Path sourceRoot, String... options) throws IOException, InterruptedException {
    return run(List.of(), Map.of(), sourceRoot, options);
  }

  Result run(List<String> javaOptions, Map<String, String> environment, Path sourceRoot, String... options)
      throws IOException, InterruptedException {
    return run(ClosedReader.NONE, javaOptions, environment, sourceRoot, options);
  }

  Result run(ClosedReader closedReader, List<String> javaOptions, Map<String, String> environment, Path sourceRoot, String... options)
      throws IOException, InterruptedException {
    if (closedReader == ClosedReader.NONE) {
      return launch(closedReader, javaOptions, environment, sourceRoot, Optional.empty(), options);
    }
    try (var barrier = new JvmStartBarrier()) {
      return launch(closedReader, javaOptions, environment, sourceRoot, Optional.of(barrier), options);
    }
  }

  private Result launch(ClosedReader closedReader, List<String> javaOptions, Map<String, String> environment, Path sourceRoot,
      Optional<JvmStartBarrier> barrier, String... options) throws IOException, InterruptedException {
    var windows = System.getProperty("os.name").startsWith("Windows");
    var launcher = Path.of("target/aquila-graph", windows ? "aquila-graph.cmd" : "aquila-graph").toAbsolutePath().normalize();
    var temporaryDirectory = Files.createDirectories(directory.resolve("process-tmp"));
    var vmArguments = new ArrayList<>(List.of("-Xms64m", "-Xmx512m", "-Djava.io.tmpdir=" + temporaryDirectory));
    vmArguments.addAll(javaOptions);
    barrier.ifPresent(control -> vmArguments.add(control.javaOption()));
    var argumentFile = directory.resolve("java-options.args");
    Files.write(argumentFile, vmArguments.stream().map(argument -> "\"" + argument.replace('\\', '/').replace("\"", "\\\"") + "\"").toList(),
        StandardCharsets.UTF_8);
    var command = new ArrayList<String>();
    if (windows) {
      command.addAll(List.of("cmd.exe", "/d", "/c"));
    }
    command.addAll(List.of(launcher.toString(), sourceRoot.toString()));
    command.addAll(List.of(options));
    var outputFile = directory.resolve("process-output.txt");
    var errorFile = directory.resolve("process-error.txt");
    var builder = new ProcessBuilder(command).redirectOutput(outputFile.toFile()).redirectError(errorFile.toFile());
    if (closedReader == ClosedReader.OUTPUT) {
      builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
    } else if (closedReader == ClosedReader.ERROR) {
      builder.redirectError(ProcessBuilder.Redirect.PIPE);
    }
    builder.environment().putAll(environment);
    builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
    builder.environment().put("AQUILA_JAVA_OPTIONS_FILE", argumentFile.toAbsolutePath().toString());
    boolean completed;
    int exit;
    try (var outputs = new CapturedOutputs(List.of(outputFile, errorFile))) {
      var process = OwnedProcess.start(builder);
      try (process) {
        if (barrier.isPresent()) {
          barrier.orElseThrow().awaitStart();
        }
        if (closedReader == ClosedReader.OUTPUT) {
          process.closeOutputReader();
        } else if (closedReader == ClosedReader.ERROR) {
          process.closeErrorReader();
        }
        if (barrier.isPresent()) {
          barrier.orElseThrow().resume();
        }
        try {
          completed = process.await(Duration.ofSeconds(30));
        } finally {
          outputs.beginClosing();
        }
      }
      exit = process.exitValue();
    }
    return new Result(completed, exit, closedReader == ClosedReader.OUTPUT ? "" : Files.readString(outputFile, StandardCharsets.UTF_8),
        closedReader == ClosedReader.ERROR ? "" : Files.readString(errorFile, StandardCharsets.UTF_8), closedReader);
  }

  private static final class CapturedOutputs implements AutoCloseable {
    private final List<Path> files;
    private long deadline;

    CapturedOutputs(List<Path> files) {
      this.files = files;
    }

    void beginClosing() {
      if (deadline == 0) {
        deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
      }
    }

    @Override
    public void close() throws IOException {
      beginClosing();
      var interrupted = Thread.interrupted();
      try {
        for (var file : files) {
          if (!Files.exists(file)) { continue; }
          var parked = file.resolveSibling(file.getFileName() + ".releasing");
          var moved = false;
          while (true) {
            try {
              if (!moved) { Files.move(file, parked); moved = true; }
              Files.move(parked, file);
              break;
            } catch (IOException unavailable) {
              if (System.nanoTime() >= deadline) {
                throw new IOException("Packaged analyzer output remains unavailable after process closure: " + file, unavailable);
              }
              try { Thread.sleep(25); }
              catch (InterruptedException interruption) { interrupted = true; }
            }
          }
        }
      } finally {
        if (interrupted) { Thread.currentThread().interrupt(); }
      }
    }
  }

  /** A closed reader identifies an unobserved channel; its empty text does not claim delivery. */
  record Result(boolean completed, int exitCode, String output, String error, ClosedReader closedReader) {
  }
}
