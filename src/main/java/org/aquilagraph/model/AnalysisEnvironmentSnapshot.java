package org.aquilagraph.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AnalysisEnvironmentSnapshot(
    CompilerConfiguration compilerConfiguration,
    String compilerImplementation,
    String compilerVendor,
    String compilerVersion,
    List<String> analysisOptions,
    List<DependencySnapshot> classPath,
    List<DependencySnapshot> modulePath) {
  public static final String DIGEST_ALGORITHM = "SHA-256";

  public AnalysisEnvironmentSnapshot {
    Objects.requireNonNull(compilerConfiguration, "compilerConfiguration");
    compilerImplementation = requireText(compilerImplementation, "compilerImplementation");
    compilerVendor = requireText(compilerVendor, "compilerVendor");
    compilerVersion = requireText(compilerVersion, "compilerVersion");
    analysisOptions = copyStrings(analysisOptions, "analysisOptions");
    classPath = List.copyOf(Objects.requireNonNull(classPath, "classPath"));
    modulePath = List.copyOf(Objects.requireNonNull(modulePath, "modulePath"));
    requireMatchingLocations(compilerConfiguration.classPath(), classPath, "classPath");
    requireMatchingLocations(compilerConfiguration.modulePath(), modulePath, "modulePath");
  }

  public String digestAlgorithm() {
    return DIGEST_ALGORITHM;
  }

  public String environmentDigest() {
    var components = new ArrayList<String>();
    components.add("compiler-implementation");
    components.add(compilerImplementation);
    components.add("compiler-vendor");
    components.add(compilerVendor);
    components.add("compiler-version");
    components.add(compilerVersion);
    components.add("analysis-options");
    components.add(Integer.toString(analysisOptions.size()));
    components.addAll(analysisOptions);
    addDependencyIdentity(components, "class-path", classPath);
    addDependencyIdentity(components, "module-path", modulePath);
    return StableId.sha256("analysis-environment", components.toArray(String[]::new));
  }

  private static List<String> copyStrings(List<String> values, String name) {
    var copy = List.copyOf(Objects.requireNonNull(values, name));
    copy.forEach(value -> Objects.requireNonNull(value, name + " entry"));
    return copy;
  }

  private static void requireMatchingLocations(
      List<Path> configured, List<DependencySnapshot> captured, String name) {
    if (configured.size() != captured.size()) {
      throw new IllegalArgumentException(name + " snapshot count must match the configuration");
    }
    for (var index = 0; index < configured.size(); index++) {
      var dependency = Objects.requireNonNull(captured.get(index), name + " entry");
      if (!configured.get(index).equals(dependency.location())) {
        throw new IllegalArgumentException(name + " snapshot order must match the configuration");
      }
    }
  }

  private static void addDependencyIdentity(
      List<String> components, String role, List<DependencySnapshot> dependencies) {
    components.add(role);
    components.add(Integer.toString(dependencies.size()));
    for (var dependency : dependencies) {
      components.add(dependency.kind().name());
      components.add(dependency.fingerprint());
    }
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
    return value;
  }
}
