package org.aquilagraph.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.Optional;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/** Records actual static and dynamic execution identities and instrumentation in each test fork. */
public final class TestExecutionListenerReceipt implements TestExecutionListener {
  private final Map<String, TestIdentifier> identities = new LinkedHashMap<>();
  private final Map<String, String> outcomes = new LinkedHashMap<>();
  private final String directory;
  private boolean valid = true;

  public TestExecutionListenerReceipt() {
    directory = System.getProperty("aquila.unit.directory", System.getProperty("aquila.integration.directory", ""));
  }

  TestExecutionListenerReceipt(Path directory) {
    this.directory = directory.toString();
  }

  @Override
  public synchronized void testPlanExecutionStarted(TestPlan plan) {
    identities.clear();
    outcomes.clear();
    valid = true;
  }

  @Override
  public synchronized void executionSkipped(TestIdentifier identifier, String reason) {
    record(identifier, "SKIPPED", Optional.empty());
  }

  @Override
  public synchronized void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
    record(identifier, result.getStatus().name(), result.getThrowable());
  }

  private void record(TestIdentifier identifier, String status, Optional<Throwable> cause) {
    if (!directory.isEmpty() && (identifier.isTest() || identifier.getSource().orElse(null) instanceof MethodSource
        || identifier.getSource().orElse(null) instanceof ClassSource)) {
      if (outcomes.putIfAbsent(identifier.getUniqueId(), status) != null) {
        valid = false;
        throw new IllegalStateException("Duplicate integration completion: " + identifier.getUniqueId());
      }
      identities.put(identifier.getUniqueId(), identifier);
      if ("FAILED".equals(status) || "ABORTED".equals(status)) {
        var failure = new Properties();
        failure.setProperty("id", identifier.getUniqueId());
        failure.setProperty("status", status);
        var diagnostic = new StringWriter();
        cause.ifPresent(value -> value.printStackTrace(new PrintWriter(diagnostic)));
        failure.setProperty("diagnostic", diagnostic.toString());
        try {
          var detail = Path.of(directory).resolve("failure-" + UUID.randomUUID() + ".properties");
          BuildFiles.write(detail, failure);
          try {
            Files.createLink(Path.of(directory).resolve("failure.properties"), detail);
          } catch (FileAlreadyExistsException exception) {
            // Keep the first failure as the cancellation cause and retain every diagnostic.
          }
        } catch (IOException exception) {
          throw new UncheckedIOException("Observed test failure could not be persisted", exception);
        }
      }
    }
  }

  @Override
  public synchronized void testPlanExecutionFinished(TestPlan plan) {
    if (directory.isEmpty()) {
      return;
    }
    var data = new Properties();
    data.setProperty("complete", Boolean.toString(valid));
    data.setProperty("coverage.agent", Boolean.toString(ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .anyMatch(argument -> argument.startsWith("-javaagent:") && argument.contains("jacoco"))));
    data.setProperty("count", Integer.toString(outcomes.size()));
    var index = 0;
    for (var entry : outcomes.entrySet()) {
      var prefix = "entry." + index++ + ".";
      data.setProperty(prefix + "id", entry.getKey());
      data.setProperty(prefix + "status", entry.getValue());
      data.setProperty(prefix + "kind", identities.get(entry.getKey()).isTest() ? "TEST" : "CONTAINER");
    }
    try {
      BuildFiles.write(Path.of(directory).resolve("execution-" + UUID.randomUUID() + ".properties"), data);
    } catch (IOException exception) {
      throw new UncheckedIOException("Integration execution receipt could not be written", exception);
    }
  }
}
