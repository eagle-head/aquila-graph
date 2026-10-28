package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Optional;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Resolves the full operation's expected tools and reports from Maven's effective configuration. */
final class CompleteConfiguration {
  private static final String ANALYSIS_REPORT_EXECUTION = "require-analysis-reports";
  private static final Set<String> VERIFY_PHASES = Set.of("validate", "initialize", "generate-sources", "process-sources",
      "generate-resources", "process-resources", "compile", "process-classes", "generate-test-sources", "process-test-sources",
      "generate-test-resources", "process-test-resources", "test-compile", "process-test-classes", "test", "prepare-package", "package",
      "pre-integration-test", "integration-test", "post-integration-test", "verify");
  private static final Set<String> REQUIRED_EXECUTIONS = Set.of("discover-unit-catalog", "capture-unit-validation",
      "discover-integration-catalog", "reconcile-integration-validation");

  private CompleteConfiguration() {
  }

  static void admit(InvocationContext context) throws IOException {
    var effective = context.root().resolve("target/complete-validation/effective-pom.xml");
    var project = BuildXml.read(effective).getDocumentElement();
    var plugins = child(child(project, "build"), "plugins");
    var expected = new Properties();
    expected.setProperty("project.artifact", child(project, "artifactId").getTextContent());
    var executions = new ArrayList<String>();
    var reports = new ArrayList<String>();
    for (var plugin : children(plugins)) {
      var artifact = child(plugin, "artifactId").getTextContent();
      var version = child(plugin, "version").getTextContent();
      var configuration = optionalChild(plugin, "configuration");
      if (configuration.isPresent()) {
        requireNoBypass(configuration.orElseThrow());
      }
      if ("maven-surefire-plugin".equals(artifact) || "maven-failsafe-plugin".equals(artifact)) {
        requireRunner(child(plugin, "configuration"), "maven-surefire-plugin".equals(artifact));
      }
      var declared = optionalChild(plugin, "executions");
      if (declared.isEmpty()) {
        continue;
      }
      for (var execution : children(declared.orElseThrow())) {
        var phase = optionalChild(execution, "phase");
        if (phase.isPresent() && !VERIFY_PHASES.contains(phase.orElseThrow().getTextContent())) {
          continue;
        }
        var id = optionalChild(execution, "id").map(Element::getTextContent).orElse("default");
        var settings = optionalChild(execution, "configuration");
        if (settings.isPresent()) {
          requireNoBypass(settings.orElseThrow());
        }
        if (phase.isPresent() || "maven-failsafe-plugin".equals(artifact) || "maven-surefire-plugin".equals(artifact)) {
          for (var goal : children(child(execution, "goals"))) {
            executions.add(artifact.replace("maven-", "").replace("-maven-plugin", "").replace("-plugin", "") + ":" + version + ":"
                + goal.getTextContent() + " (" + id + ")");
          }
        }
        if (ANALYSIS_REPORT_EXECUTION.equals(id)) {
          var available = execution.getElementsByTagName("available");
          for (var index = 0; index < available.getLength(); index++) {
            reports.add(((Element) available.item(index)).getAttribute("file"));
          }
        }
      }
    }
    var required = new ArrayList<>(REQUIRED_EXECUTIONS);
    var contract = BuildFiles.read(context.root().resolve("config/build/verification.properties"));
    var preparations = contract.getProperty("required.preparations", "");
    if (!preparations.isEmpty() && !preparations.matches("[a-z]+(,[a-z]+)*")) {
      throw new IOException("Invalid required compilation preparation selection");
    }
    expected.setProperty("preparation.batches", preparations);
    for (var id : BuildFiles.required(contract, "required.executions").split(",", -1)) {
      if (!id.matches("[a-z][a-z0-9-]*")) {
        throw new IOException("Invalid required verification execution: " + id);
      }
      required.add(id);
    }
    var sourceProject = BuildXml.read(context.root().resolve("pom.xml")).getDocumentElement();
    var original = child(child(sourceProject, "build"), "plugins").getElementsByTagName("execution");
    for (var index = 0; index < original.getLength(); index++) {
      var execution = (Element) original.item(index);
      var phase = optionalChild(execution, "phase");
      if (phase.isPresent() && "verify".equals(phase.orElseThrow().getTextContent())) {
        required.add(optionalChild(execution, "id").map(Element::getTextContent).orElse("default"));
      }
    }
    for (var id : required) {
      if (executions.stream().noneMatch(value -> value.endsWith("(" + id + ")"))) {
        throw new IOException("Complete validation is missing a required effective execution: " + id);
      }
    }
    for (var property : children(child(project, "properties"))) {
      if (Set.of("skipTests", "maven.test.skip", "maven.test.failure.ignore").contains(property.getTagName())
          && !"false".equals(property.getTextContent())) {
        throw new IOException("Complete validation refuses effective property: " + property.getTagName());
      }
    }
    expected.setProperty("configuration.digest", PreparationFiles.digest(effective));
    expected.setProperty("execution.count", Integer.toString(executions.size()));
    for (var index = 0; index < executions.size(); index++) {
      expected.setProperty("execution." + index, executions.get(index));
    }
    if (reports.isEmpty()) {
      throw new IOException("Complete validation requires the effective analysis report inventory");
    }
    if (executions.stream().anyMatch(value -> value.endsWith("(verify-production-dependency-licenses)"))) {
      reports.add(context.root().resolve("target/generated-sources/license/THIRD-PARTY.txt").toString());
    }
    expected.setProperty("report.count", Integer.toString(reports.size()));
    for (var index = 0; index < reports.size(); index++) {
      var report = PreparationFiles.owned(context.root(), Path.of(reports.get(index)));
      expected.setProperty("report." + index, report.toString());
      Files.deleteIfExists(report);
    }
    BuildFiles.write(CompleteValidation.directory(context).resolve("expected.properties"), expected);
  }

  private static void requireRunner(Element configuration, boolean unit) throws IOException {
    if (!"1".equals(child(configuration, "skipAfterFailureCount").getTextContent())) {
      throw new IOException("Complete validation requires the runner to stop after its first failure");
    }
    for (var name : List.of("test", "groups", "excludedGroups", "excludes", "includesFile", "excludesFile")) {
      var value = optionalChild(configuration, name);
      if (value.isPresent() && !value.orElseThrow().getTextContent().isBlank()) {
        throw new IOException("Complete validation refuses effective runner narrowing: " + name);
      }
    }
    var pattern = child(child(configuration, "includes"), "include").getTextContent();
    if (!(unit ? "**/*Test.java" : "**/*IT.java").equals(pattern)) {
      throw new IOException("Complete validation requires the broad unit/integration includes");
    }
    var arguments = child(configuration, "argLine").getTextContent();
    if (unit != arguments.contains("@{coverageAgentArguments}") || (!unit && arguments.contains("-javaagent"))) {
      throw new IOException("Effective instrumentation is not owned exclusively by Surefire");
    }
  }

  private static void requireNoBypass(Element configuration) throws IOException {
    for (var setting : children(configuration)) {
      var name = setting.getTagName();
      var value = setting.getTextContent().strip();
      if ((((!"skipAfterFailureCount".equals(name) && name.startsWith("skip")) || name.endsWith("Skip") || name.endsWith("Ignore")
          || "testFailureIgnore".equals(name)) && !Set.of("false", "0", "").contains(value))
          || (Set.of("fail", "failOnViolation", "failsOnError", "failOnWarning", "failOnMissing", "failOnBlacklist", "failIfNoTests",
              "failIfNoSpecifiedTests").contains(name) && !"true".equals(value))
          || (Set.of("async", "ignoreFailures").contains(name) && !"false".equals(value))
          || (Set.of("successCodes", "rerunFailingTestsCount").contains(name) && !"0".equals(value))) {
        throw new IOException("Complete validation refuses effective gate bypass: " + name + "=" + value);
      }
    }
  }

  private static Element child(Element parent, String name) throws IOException {
    var result = optionalChild(parent, name);
    return result.orElseThrow(() -> new IOException("Incomplete effective build configuration: " + name));
  }

  private static Optional<Element> optionalChild(Element parent, String name) {
    return children(parent).stream().filter(element -> name.equals(element.getTagName())).findFirst();
  }

  private static List<Element> children(Element parent) {
    var result = new ArrayList<Element>();
    var nodes = parent.getChildNodes();
    for (var index = 0; index < nodes.getLength(); index++) {
      var node = nodes.item(index);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        result.add((Element) node);
      }
    }
    return List.copyOf(result);
  }
}
