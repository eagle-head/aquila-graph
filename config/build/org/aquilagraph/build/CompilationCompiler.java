package org.aquilagraph.build;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/** Runs the selected runtime's compiler with literal arguments and preserved diagnostics. */
final class CompilationCompiler implements AutoCloseable {
  private static final int CLOSURE_SECONDS = 10;
  private final Process process;

  private CompilationCompiler(Process process) {
    this.process = process;
  }

  static int execute(CompilationRequest request, CompilationInputs inputs) throws IOException, InterruptedException {
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", BuildRequest.windows() ? "javac.exe" : "javac").toString());
    var compilerArguments = new ArrayList<String>();
    for (var argument : inputs.compilerArguments()) {
      if (argument.startsWith("-J")) {
        command.add(argument);
      } else {
        compilerArguments.add(argument);
      }
    }
    var argumentsFile = PreparationFiles.freshFile(request.root(), request.state().resolve("compiler.args"));
    JavaArguments.write(argumentsFile, compilerArguments);
    command.add("@" + argumentsFile);
    var log = PreparationFiles.freshFile(request.root(), request.state().resolve("compiler.log"));
    var builder = new ProcessBuilder(command).directory(request.state().resolve("capture").toFile()).redirectErrorStream(true)
        .redirectOutput(log.toFile());
    builder.environment().clear();
    builder.environment().putAll(CompilationInputs.compilerEnvironment(request.environment()));
    try (var child = new CompilationCompiler(builder.start())) {
      return child.process.waitFor();
    } finally {
      var diagnostic = Files.readString(log);
      if (!diagnostic.isEmpty()) {
        System.getLogger(CompilationCompiler.class.getName()).log(Level.INFO, diagnostic);
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (process.isAlive()) {
      process.destroyForcibly();
      try {
        if (!process.waitFor(CLOSURE_SECONDS, TimeUnit.SECONDS)) {
          throw new IOException("Compiler process closure remains unresolved: " + process.pid());
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Compiler process closure was interrupted: " + process.pid(), exception);
      }
    }
  }
}
