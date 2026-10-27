package org.aquilagraph.build;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.List;
import java.util.Objects;

/** Validates the explicit invocation handoff before an independent query process is launched. */
public final class PreparedConsumer {
  private static final String SCHEMA = "prepared-consumer/1";
  private final Path artifact;

  private PreparedConsumer(Path artifact) {
    this.artifact = artifact;
  }

  /**
   * Opens only a complete artifact corresponding to current inputs and the supplied invocation.
   *
   * @param root
   *          canonical checkout
   * @param receipt
   *          explicit runner-provided receipt
   * @param invocation
   *          expected producer invocation
   * @return verified prepared artifact
   * @throws IOException
   *           if the handoff is missing, foreign, stale or incomplete
   */
  public static PreparedConsumer open(Path root, Path receipt, String invocation) throws IOException {
    var canonical = root.toRealPath();
    if (!receiptPath(canonical, invocation).equals(receipt.toAbsolutePath().normalize())) {
      throw new IOException("Prepared consumer receipt belongs to another invocation or checkout");
    }
    var record = BuildFiles.read(receipt);
    if (!SCHEMA.equals(record.getProperty("schema")) || !canonical.toString().equals(record.getProperty("root"))
        || !invocation.equals(record.getProperty("invocation"))) {
      throw new IOException("Invalid prepared consumer receipt identity");
    }
    InvocationContext.read(canonical, invocation);
    var request = ConsumerPreparation.request(canonical, Path.of(BuildFiles.required(record, "annotations")), false);
    var manifest = BuildFiles.read(request.state().resolve("inputs.properties"));
    CompilationInputs.requireCurrentInputs(manifest);
    requireMetadata(record, manifest, canonical);
    ConsumerPreparation.requireStaged(request);
    var identity = PreparationFiles.digest(manifest);
    var output = request.publishedOutput(identity);
    if (!identity.equals(record.getProperty("input")) || !output.toString().equals(record.getProperty("artifact"))
        || !new CompilationPublication(request).reusable(identity)
        || !PreparationFiles.digest(output).equals(record.getProperty("output"))) {
      throw new IOException("Prepared consumer receipt or artifact is stale or incomplete");
    }
    requireModule(output);
    return new PreparedConsumer(output);
  }

  /**
   * Returns the captured module path for a fresh restricted query process.
   *
   * @return artifact and its captured runtime libraries
   */
  public String modulePath() {
    return artifact + File.pathSeparator + artifact.resolve("lib");
  }

  static Path receiptPath(Path root, String invocation) throws IOException {
    if (!invocation.matches("[0-9a-f-]{36}")) {
      throw new IOException("Prepared consumer requires a valid invocation identity");
    }
    return PreparationFiles.owned(root, root.resolve("target/consumer-receipts").resolve(invocation + ".properties"));
  }

  static void publish(CompilationRequest request, String invocation) throws IOException {
    var inputs = new CompilationInputs(request);
    var input = inputs.identity();
    var artifact = request.publishedOutput(input);
    requireModule(artifact);
    PreparationFiles.freeze(artifact);
    var record = new Properties();
    record.setProperty("schema", SCHEMA);
    record.setProperty("root", request.root().toString());
    record.setProperty("invocation", invocation);
    record.setProperty("input", input);
    record.setProperty("output", PreparationFiles.digest(artifact));
    record.setProperty("artifact", artifact.toString());
    var modulePathIndex = request.arguments().indexOf("--module-path") + 1;
    var modulePath = request.arguments().get(modulePathIndex);
    record.setProperty("annotations", modulePath.substring(modulePath.lastIndexOf(File.pathSeparator) + 1));
    record.setProperty("jdk.home", System.getProperty("java.home"));
    record.setProperty("jdk.version", Runtime.version().toString());
    record.setProperty("packages", PreparationFiles.digest(request.root().resolve("target/aquila-graph/lib")));
    record.setProperty("sources", PreparationFiles.digest(request.root().resolve(ConsumerPreparation.SOURCES)));
    inputs.requireUnchanged();
    BuildFiles.write(receiptPath(request.root(), invocation), record);
  }

  private static void requireMetadata(Properties receipt, Properties manifest, Path root) throws IOException {
    for (var field : List.of("jdk.home", "jdk.version")) {
      if (!Objects.equals(receipt.getProperty(field), manifest.getProperty(field))) {
        throw new IOException("Prepared consumer receipt differs from its compiler toolchain: " + field);
      }
    }
    var annotations = Path.of(BuildFiles.required(receipt, "annotations")).toRealPath();
    if (!PreparationFiles.digest(annotations).equals(manifest.getProperty("input." + annotations))
        || !PreparationFiles.digest(root.resolve(ConsumerPreparation.SOURCES)).equals(receipt.getProperty("sources"))
        || !PreparationFiles.digest(root.resolve("target/aquila-graph/lib")).equals(receipt.getProperty("packages"))) {
      throw new IOException("Prepared consumer receipt differs from its source or package inputs");
    }
  }

  private static void requireModule(Path artifact) throws IOException {
    if (!Files.isRegularFile(artifact.resolve("module-info.class"))) {
      throw new IOException("Prepared consumer has no compiled module descriptor");
    }
    var descriptor = ModuleFinder.of(artifact).find("org.aquilagraph.retention.consumer")
        .orElseThrow(() -> new IOException("Prepared consumer has the wrong module identity")).descriptor();
    if (descriptor.requires().stream()
        .anyMatch(requirement -> requirement.name().contains("compiler") || "org.aquilagraph.frontend".equals(requirement.name()))) {
      throw new IOException("Prepared consumer must remain independent of frontend and compiler modules");
    }
    for (var name : new String[]{"source", "cfg", "application", "repository"}) {
      if (descriptor.requires().stream().noneMatch(requirement -> requirement.name().equals("org.aquilagraph." + name))) {
        throw new IOException("Prepared consumer is missing its required public module: " + name);
      }
    }
  }
}
