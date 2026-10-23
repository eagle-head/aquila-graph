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
import org.aquilagraph.testsupport.OwnedProcess;

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
    var windows = System.getProperty("os.name").startsWith("Windows");
    var launcher = Path.of("target/aquila-graph", windows ? "aquila-graph.cmd" : "aquila-graph").toAbsolutePath().normalize();
    var temporaryDirectory = Files.createDirectories(directory.resolve("process-tmp"));
    var vmArguments = new ArrayList<>(List.of("-Xms64m", "-Xmx512m", "-Djava.io.tmpdir=" + temporaryDirectory));
    vmArguments.addAll(javaOptions);
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
    builder.environment().putAll(environment);
    builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
    builder.environment().put("AQUILA_JAVA_OPTIONS_FILE", argumentFile.toAbsolutePath().toString());
    var process = OwnedProcess.start(builder);
    boolean completed;
    try (process) {
      completed = process.await(Duration.ofSeconds(30));
    }
    return new Result(completed, process.exitValue(), Files.readString(outputFile, StandardCharsets.UTF_8),
        Files.readString(errorFile, StandardCharsets.UTF_8));
  }

  record Result(boolean completed, int exitCode, String output, String error) {
  }
}
