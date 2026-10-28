package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Validates unit or integration ownership and selects exact identities supplied by JUnit discovery. */
record SuiteCatalog(String scope, String classes, List<Entry> entries) {
  static final String UNIT_SCOPE = "unit";
  private static final int CLASS_SELECTOR_MATCH_COUNT = 1;
  static final Set<String> GROUPS = Set.of("process", "persistence", "lifecycle");

  enum Kind {
    TEST, CONTAINER
  }

  record Entry(String id, String className, String group, Kind kind, boolean platformSkipped) {
  }

  SuiteCatalog {
    entries = List.copyOf(entries);
    if (!GROUPS.contains(scope) && !Set.of("all", "legacy-process", UNIT_SCOPE).contains(scope)) {
      throw new IllegalArgumentException("Unknown integration scope: " + scope);
    }
    var identities = new HashSet<String>();
    for (var entry : entries) {
      if (!(UNIT_SCOPE.equals(scope) ? UNIT_SCOPE.equals(entry.group()) : GROUPS.contains(entry.group())) || entry.id().isBlank()
          || entry.className().isBlank() || !identities.add(entry.id())) {
        throw new IllegalArgumentException("Invalid or duplicate integration discovery identity: " + entry);
      }
    }
  }

  static String group(Set<String> tags) {
    if (tags.size() != 1 || !GROUPS.containsAll(tags)) {
      throw new IllegalArgumentException("Every integration class/test/factory requires exactly one known functional tag: " + tags);
    }
    return tags.iterator().next();
  }

  static Path directory(Path root, String nonce) {
    if (!nonce.matches("[0-9a-f-]{36}")) {
      throw new IllegalArgumentException("Integration discovery requires the current build invocation identity");
    }
    return root.resolve("target/integration-selection").resolve(nonce);
  }

  static Path directory(Path root, String nonce, String scope) {
    var integration = directory(root, nonce);
    return UNIT_SCOPE.equals(scope) ? root.resolve("target/unit-selection").resolve(nonce) : integration;
  }

  List<Entry> selected() {
    var candidates = entries.stream().filter(entry -> switch (scope) {
      case "all", "unit" -> true;
      case "legacy-process" -> entry.className().endsWith("ProcessIT");
      default -> scope.equals(entry.group());
    }).toList();
    if (classes.isEmpty()) {
      return nonempty(candidates);
    }
    var requested = Arrays.asList(classes.split(",", -1));
    if (new HashSet<>(requested).size() != requested.size()) {
      throw new IllegalArgumentException("Duplicate explicit integration class selector");
    }
    for (var name : requested) {
      var matching = candidates.stream().map(Entry::className).distinct().filter(value -> matchesClass(value, name)).toList();
      if (matching.size() != CLASS_SELECTOR_MATCH_COUNT) {
        throw new IllegalArgumentException("Integration class selector is missing, ambiguous or outside the requested scope: " + name);
      }
    }
    return nonempty(
        candidates.stream().filter(entry -> requested.stream().anyMatch(name -> matchesClass(entry.className(), name))).toList());
  }

  private static boolean matchesClass(String className, String selector) {
    return className.equals(selector) || className.substring(className.lastIndexOf('.') + 1).equals(selector);
  }

  private static List<Entry> nonempty(List<Entry> selected) {
    if (selected.isEmpty()) {
      throw new IllegalArgumentException("Requested integration selection is empty");
    }
    return selected;
  }

  void requireSelection(String expectedScope, String expectedClasses) throws IOException {
    if (!scope.equals(expectedScope) || !classes.equals(expectedClasses)) {
      throw new IOException("Discovered integration selection differs from the admitted operation");
    }
    selected();
  }

  void write(Path file) throws IOException {
    selected();
    var data = new Properties();
    data.setProperty("scope", scope);
    data.setProperty("classes", classes);
    data.setProperty("count", Integer.toString(entries.size()));
    for (var index = 0; index < entries.size(); index++) {
      var entry = entries.get(index);
      var prefix = "entry." + index + ".";
      data.setProperty(prefix + "id", entry.id());
      data.setProperty(prefix + "class", entry.className());
      data.setProperty(prefix + "group", entry.group());
      data.setProperty(prefix + "kind", entry.kind().name());
      data.setProperty(prefix + "platform.skipped", Boolean.toString(entry.platformSkipped()));
    }
    BuildFiles.write(file, data);
  }

  static SuiteCatalog read(Path file) throws IOException {
    var data = BuildFiles.read(file);
    var entries = new ArrayList<Entry>();
    var count = Integer.parseInt(BuildFiles.required(data, "count"));
    for (var index = 0; index < count; index++) {
      var prefix = "entry." + index + ".";
      entries.add(new Entry(BuildFiles.required(data, prefix + "id"), BuildFiles.required(data, prefix + "class"),
          BuildFiles.required(data, prefix + "group"), Kind.valueOf(BuildFiles.required(data, prefix + "kind")),
          Boolean.parseBoolean(BuildFiles.required(data, prefix + "platform.skipped"))));
    }
    return new SuiteCatalog(BuildFiles.required(data, "scope"), data.getProperty("classes", ""), entries);
  }
}
