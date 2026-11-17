package org.aquilagraph.build;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Prepares the independent query module using the shared compilation publication protocol. */
public final class ConsumerPreparation {
  static final String SOURCES = "src/test/java/org/aquilagraph/retention/consumer";
  private static final int ARGUMENT_COUNT = 3;
  private static final String MODULE_DESCRIPTOR = "module-info.java";

  private ConsumerPreparation() {
  }

  /**
   * Captures packaged inputs, prepares the consumer and publishes its invocation receipt.
   *
   * @param arguments
   *          root, provided annotation JAR and force flag
   * @throws IOException
   *           if admission, inputs or publication are invalid
   * @throws InterruptedException
   *           if compilation is canceled
   */
  public static void main(String[] arguments) throws IOException, InterruptedException {
    BuildAdmission.main(new String[]{"default"});
    if (arguments.length != ARGUMENT_COUNT || !List.of("true", "false").contains(arguments[2])) {
      throw new IOException("Consumer preparation requires root, annotation JAR and a boolean force flag");
    }
    var root = Path.of(arguments[0]).toRealPath();
    var invocation = System.getenv("AQUILA_BUILD_INVOCATION");
    if (invocation == null) {
      throw new IOException("Consumer preparation requires an explicit build invocation");
    }
    var request = request(root, Path.of(arguments[1]).toRealPath(), Boolean.parseBoolean(arguments[2]));
    var exit = prepare(request, invocation);
    if (exit != 0) {
      System.exit(exit);
    }
  }

  static int prepare(CompilationRequest request, String invocation) throws IOException, InterruptedException {
    Files.deleteIfExists(PreparedConsumer.receiptPath(request.root(), invocation));
    stage(request);
    var exit = CompilationPreparation.prepare(request);
    if (exit == 0) {
      PreparedConsumer.publish(request, invocation);
    }
    return exit;
  }

  static CompilationRequest request(Path root, Path annotations, boolean force) {
    var staging = root.resolve("target/consumer-inputs");
    var sources = staging.resolve("sources");
    var resources = staging.resolve("resources");
    var output = root.resolve("target/prepared-consumers");
    var arguments = new ArrayList<>(List.of("-J-Xms128m", "-J-Xmx1024m", "--release", "25", "-encoding", "UTF-8", "-g", "-Xlint:all",
        "-Werror", "--module-path", resources.resolve("lib") + File.pathSeparator + annotations, "-proc:none", "-d", output.toString(),
        sources.resolve(MODULE_DESCRIPTOR).toString()));
    for (var name : List.of("PublicAstObservation.java", "RetainedAstQueryProcess.java")) {
      arguments.add(sources.resolve(name).toString());
    }
    return new CompilationRequest(root, CompilationRequest.CONSUMER_BATCH, sources, resources, output, force, arguments, System.getenv());
  }

  static void stage(CompilationRequest request) throws IOException {
    var root = request.root();
    var distribution = root.resolve("target/aquila-graph/lib");
    var modules = ModuleFinder.of(distribution).findAll().stream().map(reference -> reference.descriptor().name()).toList();
    for (var name : List.of("identity", "source", "cfg", "frontend", "application", "repository", "library", "cli")) {
      if (!modules.contains("org.aquilagraph." + name)) {
        throw new IOException("Consumer preparation requires the complete packaged distribution; missing module: " + name);
      }
    }
    PreparationFiles.clear(root, request.sources());
    PreparationFiles.clear(root, request.resources());
    PreparationFiles.copy(root.resolve("config/consumer").resolve(MODULE_DESCRIPTOR), request.sources().resolve(MODULE_DESCRIPTOR));
    for (var name : List.of("PublicAstObservation.java", "RetainedAstQueryProcess.java")) {
      PreparationFiles.copy(root.resolve(SOURCES).resolve(name), request.sources().resolve(name));
    }
    PreparationFiles.copy(distribution, request.resources().resolve("lib"));
    requireStaged(request);
  }

  static void requireStaged(CompilationRequest request) throws IOException {
    for (var name : List.of("PublicAstObservation.java", "RetainedAstQueryProcess.java", MODULE_DESCRIPTOR)) {
      var original = MODULE_DESCRIPTOR.equals(name)
          ? request.root().resolve("config/consumer").resolve(name)
          : request.root().resolve(SOURCES).resolve(name);
      if (!PreparationFiles.digest(original).equals(PreparationFiles.digest(request.sources().resolve(name)))) {
        throw new IOException("Consumer staging differs from its current canonical source: " + name);
      }
    }
    if (!PreparationFiles.digest(request.root().resolve("target/aquila-graph/lib"))
        .equals(PreparationFiles.digest(request.resources().resolve("lib")))) {
      throw new IOException("Consumer staging differs from the current packaged distribution");
    }
  }
}
