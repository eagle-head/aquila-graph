package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** Owns one complete candidate's current inputs, reports and fail-closed result. */
final class CompleteValidation {
  private static final int SINGLE_EXECUTION_COUNT = 1;
  private static final String INSTRUCTION_COUNTER = "INSTRUCTION";
  private static final List<String> INPUT_PATHS = List.of("src", "config", ".mvn", "pom.xml", "Makefile", "Invoke-Build.ps1",
      ".gitattributes", ".editorconfig", "AGENTS.md");
  private final InvocationContext context;
  private final Properties capturedInputs;
  private final Set<Path> initialDatabases;

  private CompleteValidation(InvocationContext context, Properties inputs, Set<Path> databases) {
    this.context = context;
    capturedInputs = inputs;
    initialDatabases = Set.copyOf(databases);
  }

  static Path directory(InvocationContext context) {
    return context.root().resolve("target/complete-validation").resolve(context.nonce());
  }

  static CompleteValidation prepare(InvocationContext context) throws IOException {
    requireResourceRecovery(context);
    var result = new CompleteValidation(context, captureInputs(context.root()), databaseRecords(context.root()));
    BuildFiles.write(directory(context).resolve("inputs.properties"), result.capturedInputs);
    Files.deleteIfExists(context.root().resolve("target/complete-validation/effective-pom.xml"));
    invalidateCoverage(context.root());
    for (var name : List.of("surefire-reports", "failsafe-reports")) {
      PreparationFiles.clear(context.root(), context.root().resolve("target").resolve(name));
    }
    result.record("RUNNING", "Complete validation has not yet reconciled", -1);
    return result;
  }

  static void invalidateCoverage(Path root) throws IOException {
    Files.deleteIfExists(PreparationFiles.owned(root, root.resolve("target/jacoco.exec")));
    PreparationFiles.clear(root, root.resolve("target/site/jacoco"));
  }

  private static Properties captureInputs(Path root) throws IOException {
    var result = new Properties();
    for (var input : INPUT_PATHS) {
      result.setProperty(input, PreparationFiles.digest(root.resolve(input)));
    }
    return result;
  }

  void verify() throws IOException {
    if (!capturedInputs.equals(captureInputs(context.root()))) {
      throw new IOException("Complete validation candidate inputs changed during execution");
    }
    CompleteValidationCheckpoint.requireUnchanged(context);
    BuildFiles.read(directory(context).resolve("integration-verified.properties"));
    var expected = BuildFiles.read(directory(context).resolve("expected.properties"));
    if (!BuildFiles.required(expected, "configuration.digest")
        .equals(PreparationFiles.digest(context.root().resolve("target/complete-validation/effective-pom.xml")))) {
      throw new IOException("Effective full validation configuration changed during execution");
    }
    var log = Files.readString(directory(context).resolve("maven.log"));
    var observed = new Properties();
    for (var batch : expected.getProperty("preparation.batches", "").split(",", -1)) {
      if (!batch.isEmpty()) {
        var publication = CompilationPublication.requireCurrent(context.root(), batch);
        observed.setProperty("preparation." + batch + ".input", BuildFiles.required(publication, "input"));
        observed.setProperty("preparation." + batch + ".output", BuildFiles.required(publication, "output.digest"));
      }
    }
    for (var index = 0; index < Integer.parseInt(BuildFiles.required(expected, "execution.count")); index++) {
      var execution = BuildFiles.required(expected, "execution." + index);
      var identity = " " + execution + " @ " + BuildFiles.required(expected, "project.artifact") + " ---";
      var count = log.lines().filter(line -> line.startsWith("[INFO] --- ") && line.contains(identity)).count();
      if (count != SINGLE_EXECUTION_COUNT) {
        throw new IOException("Expected effective execution did not run exactly once: " + execution + "; observed " + count);
      }
      observed.setProperty("execution." + index, execution);
      observed.setProperty("execution." + index + ".outcome", "PASSED");
    }
    for (var index = 0; index < Integer.parseInt(BuildFiles.required(expected, "report.count")); index++) {
      var report = Path.of(BuildFiles.required(expected, "report." + index));
      requireReport(report);
      observed.setProperty("report." + index, report.toString());
      observed.setProperty("report." + index + ".digest", PreparationFiles.digest(report));
    }
    for (var report : List.of("index.html", "jacoco.xml", "jacoco.csv")) {
      requireReport(context.root().resolve("target/site/jacoco").resolve(report));
    }
    var coverage = BuildXml.read(context.root().resolve("target/site/jacoco/jacoco.xml"));
    var counters = coverage.getDocumentElement().getElementsByTagName("counter");
    long covered = 0;
    for (var index = 0; index < counters.getLength(); index++) {
      var counter = counters.item(index).getAttributes();
      if (INSTRUCTION_COUNTER.equals(counter.getNamedItem("type").getNodeValue())) {
        covered += Long.parseLong(counter.getNamedItem("covered").getNodeValue());
      }
    }
    if (covered == 0) {
      throw new IOException("Current full validation coverage contains no covered instructions");
    }
    for (var database : databaseRecords(context.root())) {
      if (!initialDatabases.contains(database) && !"closed".equals(BuildFiles.read(database).getProperty("state"))) {
        throw new IOException("Complete validation has unfinished database cleanup: " + database);
      }
    }
    BuildJournal.requireClosed(context);
    BuildFiles.write(directory(context).resolve("observed.properties"), observed);
  }

  private static void requireReport(Path report) throws IOException {
    if (!Files.isRegularFile(report) || Files.size(report) == 0) {
      throw new IOException("Missing or empty current complete validation report: " + report);
    }
  }

  private static Set<Path> databaseRecords(Path root) throws IOException {
    var directory = root.resolve("target/neo4j-integration");
    if (!Files.isDirectory(directory)) {
      return Set.of();
    }
    try (var files = Files.walk(directory)) {
      return files.filter(file -> Files.isRegularFile(file) && file.endsWith("resources.properties"))
          .collect(Collectors.toUnmodifiableSet());
    }
  }

  void record(String status, String diagnostic, int exit) throws IOException {
    var result = context.properties();
    result.setProperty("status", status);
    result.setProperty("complete.validation", Boolean.toString("PASSED".equals(status)));
    result.setProperty("candidate", PreparationFiles.digest(capturedInputs));
    result.setProperty("diagnostic", diagnostic);
    result.setProperty("exit", Integer.toString(exit));
    result.setProperty("maven.log", directory(context).resolve("maven.log").toString());
    result.setProperty("expected", directory(context).resolve("expected.properties").toString());
    result.setProperty("unit.catalog",
        SuiteCatalog.directory(context.root(), context.nonce(), "unit").resolve("catalog.properties").toString());
    result.setProperty("integration.catalog",
        SuiteCatalog.directory(context.root(), context.nonce()).resolve("catalog.properties").toString());
    result.setProperty("unit.outputs", directory(context).resolve("unit-outputs.properties").toString());
    result.setProperty("coverage.reports", context.root().resolve("target/site/jacoco").toString());
    result.setProperty("observed", directory(context).resolve("observed.properties").toString());
    var resources = new Properties();
    var index = 0;
    var closed = true;
    for (var database : databaseRecords(context.root())) {
      if (!initialDatabases.contains(database)) {
        resources.setProperty("database." + index, database.toString());
        index++;
        closed &= "closed".equals(BuildFiles.read(database).getProperty("state"));
      }
    }
    resources.setProperty("database.count", Integer.toString(index));
    resources.setProperty("registered.databases.closed", Boolean.toString(closed));
    var integrationsReconciled = integrationsReconciled(context);
    resources.setProperty("integration.completion.verified", Boolean.toString(integrationsReconciled));
    closed &= integrationsReconciled;
    resources.setProperty("closure.verified", Boolean.toString(closed));
    BuildFiles.write(context.directory().resolve("complete-resources.properties"), resources);
    if ("PASSED".equals(status) && !closed) {
      throw new IOException("Complete validation cannot publish success with unfinished resource cleanup");
    }
    result.setProperty("resource.closure.verified", Boolean.toString(closed));
    result.setProperty("owner.closure.record", context.directory().resolve("owner-closed.properties").toString());
    BuildFiles.write(directory(context).resolve("result.properties"), result);
  }

  private static boolean integrationsReconciled(InvocationContext context) throws IOException {
    var receipt = SuiteCatalog.directory(context.root(), context.nonce()).resolve("verification.properties");
    if (!Files.isRegularFile(receipt)) {
      return false;
    }
    var verification = BuildFiles.read(receipt);
    return "all".equals(verification.getProperty("scope")) && "true".equals(verification.getProperty("execution.verified"));
  }

  private static void requireResourceRecovery(InvocationContext context) throws IOException {
    var invocations = context.root().resolve(".build-state/invocations");
    try (var directories = Files.newDirectoryStream(invocations)) {
      for (var invocation : directories) {
        var record = invocation.resolve("complete-resources.properties");
        if (!Files.isRegularFile(record)) {
          continue;
        }
        var resources = BuildFiles.read(record);
        if (!"true".equals(BuildFiles.required(resources, "closure.verified"))) {
          for (var index = 0; index < Integer.parseInt(BuildFiles.required(resources, "database.count")); index++) {
            var database = Path.of(BuildFiles.required(resources, "database." + index));
            if (!Files.isRegularFile(database) || !"closed".equals(BuildFiles.read(database).getProperty("state"))) {
              throw new IOException("Earlier complete validation requires resource recovery: " + record + "; resource " + database);
            }
          }
        }
      }
    }
  }
}
