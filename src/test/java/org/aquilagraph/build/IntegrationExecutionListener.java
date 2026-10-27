package org.aquilagraph.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/** Records actual static and dynamic execution identities in each Failsafe fork. */
public final class IntegrationExecutionListener implements TestExecutionListener {
  private final Map<String, TestIdentifier> identities = new LinkedHashMap<>();
  private final Map<String, String> outcomes = new LinkedHashMap<>();
  private final String directory = System.getProperty("aquila.integration.directory", "");
  private boolean valid = true;

  @Override
  public synchronized void testPlanExecutionStarted(TestPlan plan) {
    identities.clear();
    outcomes.clear();
    valid = true;
  }

  @Override
  public synchronized void executionSkipped(TestIdentifier identifier, String reason) {
    record(identifier, "SKIPPED");
  }

  @Override
  public synchronized void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
    record(identifier, result.getStatus().name());
  }

  private void record(TestIdentifier identifier, String status) {
    if (!directory.isEmpty() && (identifier.isTest() || identifier.getSource().orElse(null) instanceof MethodSource
        || identifier.getSource().orElse(null) instanceof ClassSource)) {
      if (outcomes.putIfAbsent(identifier.getUniqueId(), status) != null) {
        valid = false;
        throw new IllegalStateException("Duplicate integration completion: " + identifier.getUniqueId());
      }
      identities.put(identifier.getUniqueId(), identifier);
    }
  }

  @Override
  public synchronized void testPlanExecutionFinished(TestPlan plan) {
    if (directory.isEmpty()) {
      return;
    }
    var data = new Properties();
    data.setProperty("complete", Boolean.toString(valid));
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
