package org.aquilagraph.build;

import java.io.IOException;
import org.aquilagraph.build.IntegrationCatalog.Entry;
import org.aquilagraph.build.IntegrationCatalog.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/** Reconciles current fork receipts, dynamic descendants and the real Failsafe summary. */
final class IntegrationResults {
  private IntegrationResults() {
  }

  private enum CompletionStatus {
    SUCCESSFUL, FAILED, ABORTED, SKIPPED
  }

  private record Outcome(String id, Kind kind, CompletionStatus status) {
    static Outcome read(Properties data, int index) throws IOException {
      var prefix = "entry." + index + ".";
      try {
        return new Outcome(BuildFiles.required(data, prefix + "id"), Kind.valueOf(BuildFiles.required(data, prefix + "kind")),
            CompletionStatus.valueOf(BuildFiles.required(data, prefix + "status")));
      } catch (IllegalArgumentException exception) {
        throw new IOException("Invalid integration completion record", exception);
      }
    }
  }

  static void verify(IntegrationCatalog catalog, Path directory, Path summary) throws IOException {
    var expected = catalog.selected();
    var observed = outcomes(directory);
    for (var outcome : observed.values()) {
      if (outcome.status() != CompletionStatus.SUCCESSFUL && !allowedSkip(outcome, expected)) {
        throw new IOException("Integration did not succeed: " + outcome);
      }
      if (outcome.kind() == Kind.TEST && expected.stream().noneMatch(entry -> descendant(outcome.id(), entry.id()))) {
        throw new IOException("Executed integration was outside discovery/selection: " + outcome.id());
      }
    }
    for (var entry : expected) {
      var outcome = observed.get(entry.id());
      if (outcome == null && observed.values().stream().noneMatch(
          value -> value.status() == CompletionStatus.SKIPPED && descendant(entry.id(), value.id()) && allowedSkip(value, expected))) {
        throw new IOException("Discovered integration has no executed result: " + entry.id());
      }
      if (outcome != null && outcome.status() == CompletionStatus.SUCCESSFUL && entry.kind() == Kind.CONTAINER
          && observed.values().stream().noneMatch(value -> value.kind() == Kind.TEST && descendant(value.id(), entry.id()))) {
        throw new IOException("Integration factory/template produced no test descendants: " + entry.id());
      }
    }
    var cases = observed.values().stream().filter(value -> value.kind() == Kind.TEST || value.status() == CompletionStatus.SKIPPED).count();
    requireSummary(summary, cases);
    if (!summary.toAbsolutePath().normalize().equals(directory.resolve("failsafe-summary.xml").toAbsolutePath().normalize())) {
      Files.copy(summary, directory.resolve("failsafe-summary.xml"));
    }
    var verification = new Properties();
    verification.setProperty("scope", catalog.scope());
    verification.setProperty("selected.identities", Integer.toString(expected.size()));
    verification.setProperty("observed.cases", Long.toString(cases));
    verification.setProperty("execution.verified", "true");
    verification.setProperty("complete.validation", "false");
    BuildFiles.write(directory.resolve("verification.properties"), verification);
  }

  private static boolean descendant(String id, String parent) {
    return id.equals(parent) || id.startsWith(parent + "/");
  }

  private static boolean allowedSkip(Outcome outcome, List<Entry> expected) {
    var affected = expected.stream().filter(entry -> descendant(entry.id(), outcome.id()) || descendant(outcome.id(), entry.id())).toList();
    return outcome.status() == CompletionStatus.SKIPPED && !affected.isEmpty() && affected.stream().allMatch(Entry::platformSkipped);
  }

  private static Map<String, Outcome> outcomes(Path directory) throws IOException {
    var result = new HashMap<String, Outcome>();
    try (var files = Files.newDirectoryStream(directory, "execution-*.properties")) {
      var receipts = new ArrayList<Path>();
      files.forEach(receipts::add);
      if (receipts.isEmpty()) {
        throw new IOException("No current integration execution receipt");
      }
      for (var file : receipts) {
        var data = BuildFiles.read(file);
        if (!"true".equals(BuildFiles.required(data, "complete"))) {
          throw new IOException("Incomplete integration execution receipt: " + file);
        }
        var count = Integer.parseInt(BuildFiles.required(data, "count"));
        for (var index = 0; index < count; index++) {
          var outcome = Outcome.read(data, index);
          if (result.putIfAbsent(outcome.id(), outcome) != null) {
            throw new IOException("Integration identity executed more than once: " + outcome.id());
          }
        }
      }
    }
    return Map.copyOf(result);
  }

  private static void requireSummary(Path summary, long cases) throws IOException {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      var document = factory.newDocumentBuilder().parse(summary.toFile());
      var root = document.getDocumentElement();
      if (!"failsafe-summary".equals(root.getTagName()) || cases == 0
          || Long.parseLong(root.getElementsByTagName("completed").item(0).getTextContent()) != cases
          || !"0".equals(root.getElementsByTagName("failures").item(0).getTextContent())
          || !"0".equals(root.getElementsByTagName("errors").item(0).getTextContent())) {
        throw new IOException("Failsafe summary does not reconcile with current discovered/executed integrations");
      }
    } catch (ParserConfigurationException | SAXException exception) {
      throw new IOException("Cannot inspect the current Failsafe summary", exception);
    }
  }
}
