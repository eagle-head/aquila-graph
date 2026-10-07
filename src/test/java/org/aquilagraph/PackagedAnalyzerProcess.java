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
import java.util.concurrent.TimeUnit;

/** Executes the packaged analyzer with bounded lifetime and captured, separate streams. */
final class PackagedAnalyzerProcess {
  private final Path directory;

  PackagedAnalyzerProcess(Path directory) {
    this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
  }

  Result run(Path sourceRoot, String... options) throws IOException, InterruptedException {
    return run(List.of(), Map.of(), sourceRoot, options);
  }

  Result run(List<String> javaOptions, Map<String, String> environment, Path sourceRoot, String... options)
      throws IOException, InterruptedException {
    var javaName = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaName);
    var jar = Path.of("target/aquila-graph-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
    var temporaryDirectory = Files.createDirectories(directory.resolve("process-tmp"));
    var command = new ArrayList<>(List.of(javaExecutable.toString(), "-Djava.io.tmpdir=" + temporaryDirectory));
    command.addAll(javaOptions);
    command.addAll(List.of("-jar", jar.toString(), sourceRoot.toString()));
    command.addAll(List.of(options));
    var outputFile = directory.resolve("process-output.txt");
    var errorFile = directory.resolve("process-error.txt");
    var builder = new ProcessBuilder(command).redirectOutput(outputFile.toFile()).redirectError(errorFile.toFile());
    builder.environment().putAll(environment);
    var process = builder.start();
    try {
      var completed = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
      if (!completed) {
        process.destroyForcibly().waitFor();
      }
      return new Result(completed, process.exitValue(), Files.readString(outputFile, StandardCharsets.UTF_8),
          Files.readString(errorFile, StandardCharsets.UTF_8));
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly().waitFor();
      }
    }
  }

  record Result(boolean completed, int exitCode, String output, String error) {
  }
}
