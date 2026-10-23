package org.aquilagraph.identity.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Comparator;

/**
 * Reproducibility snapshot of the compiler runtime and dependency inputs.
 *
 * @param compilerConfiguration
 *          normalized compiler configuration
 * @param compilerImplementation
 *          compiler implementation class
 * @param compilerVendor
 *          Java runtime vendor
 * @param compilerVersion
 *          Java runtime version
 * @param analysisOptions
 *          effective compiler options
 * @param classPath
 *          captured class-path entries
 * @param modulePath
 *          captured module-path entries
 * @param identityScheme
 *          explicit derivation and observation contract
 */
public record AnalysisEnvironmentSnapshot(CompilerConfiguration compilerConfiguration, String compilerImplementation, String compilerVendor,
    String compilerVersion, List<String> analysisOptions, List<DependencySnapshot> classPath, List<DependencySnapshot> modulePath,
    EnvironmentIdentityScheme identityScheme) {
  /** Digest algorithm used for environment identity. */
  public static final String DIGEST_ALGORITHM = "SHA-256";

  /**
   * Constructs a historical environment with its original assurance and identity.
   *
   * @param compilerConfiguration
   *          normalized configuration
   * @param compilerImplementation
   *          compiler class
   * @param compilerVendor
   *          runtime vendor
   * @param compilerVersion
   *          runtime version
   * @param analysisOptions
   *          effective options
   * @param classPath
   *          historical class-path captures
   * @param modulePath
   *          historical module-path captures
   */
  public AnalysisEnvironmentSnapshot(CompilerConfiguration compilerConfiguration, String compilerImplementation, String compilerVendor,
      String compilerVersion, List<String> analysisOptions, List<DependencySnapshot> classPath, List<DependencySnapshot> modulePath) {
    this(compilerConfiguration, compilerImplementation, compilerVendor, compilerVersion, analysisOptions, classPath, modulePath,
        EnvironmentIdentityScheme.HISTORICAL);
  }

  /** Validates and defensively copies environment data. */
  public AnalysisEnvironmentSnapshot {
    Objects.requireNonNull(compilerConfiguration, "compilerConfiguration");
    compilerImplementation = requireText(compilerImplementation, "compilerImplementation");
    compilerVendor = requireText(compilerVendor, "compilerVendor");
    compilerVersion = requireText(compilerVersion, "compilerVersion");
    analysisOptions = copyStrings(analysisOptions, "analysisOptions");
    if (analysisOptions.stream().anyMatch(option -> "--enable-preview".equals(option) || option.startsWith("--enable-preview="))) {
      throw new IllegalArgumentException("Preview compiler option is not supported: --enable-preview");
    }
    classPath = List.copyOf(Objects.requireNonNull(classPath, "classPath"));
    modulePath = List.copyOf(Objects.requireNonNull(modulePath, "modulePath"));
    requireMatchingLocations(compilerConfiguration.classPath(), classPath, "classPath");
    requireMatchingLocations(compilerConfiguration.modulePath(), modulePath, "modulePath");
    Objects.requireNonNull(identityScheme, "identityScheme");
    requireSemantics(classPath, false, identityScheme);
    requireSemantics(modulePath, true, identityScheme);
  }

  /**
   * Returns the environment digest algorithm.
   *
   * @return {@value #DIGEST_ALGORITHM}
   */
  public String digestAlgorithm() {
    return DIGEST_ALGORITHM;
  }

  /**
   * Returns an immutable copy of the effective compiler options.
   *
   * @return compiler options
   */
  @Override
  public List<String> analysisOptions() {
    return List.copyOf(analysisOptions);
  }

  /**
   * Computes the stable identity of compiler and dependency inputs.
   *
   * @return namespaced SHA-256 environment identifier
   */
  public String environmentDigest() {
    var components = new ArrayList<String>();
    components.add("language-release");
    components.add(Integer.toString(compilerConfiguration.release().feature()));
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
    if (identityScheme == EnvironmentIdentityScheme.MODULE_SEMANTICS) {
      components.add(identityScheme.version());
      addSemantics(components, classPath);
      addSemantics(components, modulePath);
    }
    return StableId.sha256(identityScheme == EnvironmentIdentityScheme.HISTORICAL ? "analysis-environment" : identityScheme.version(),
        components.toArray(String[]::new));
  }

  private static void requireSemantics(List<DependencySnapshot> dependencies, boolean modular, EnvironmentIdentityScheme scheme) {
    for (var dependency : dependencies) {
      var state = dependency.semantics().state();
      var valid = scheme == EnvironmentIdentityScheme.HISTORICAL
          ? state == DependencySemantics.State.UNOBSERVED
          : modular
              ? state == DependencySemantics.State.MISSING || state == DependencySemantics.State.MODULES
                  || state == DependencySemantics.State.INVALID
              : state == DependencySemantics.State.CLASS_PATH;
      if (!valid) {
        throw new IllegalArgumentException("Dependency interpretation must agree with its environment scheme and configured role");
      }
    }
  }

  private static void addSemantics(List<String> components, List<DependencySnapshot> dependencies) {
    components.add(Integer.toString(dependencies.size()));
    for (var dependency : dependencies) {
      components.add(dependency.semantics().state().name());
      var modules = dependency.semantics().modules().stream().sorted(Comparator.comparing(ModuleCandidate::lookupName)
          .thenComparing(module -> module.descriptorName().orElse("")).thenComparing(ModuleCandidate::nameOrigin)).toList();
      components.add(Integer.toString(modules.size()));
      for (var module : modules) {
        components.add(module.lookupName());
        components.add(Boolean.toString(module.descriptorName().isPresent()));
        components.add(module.descriptorName().orElse(""));
        components.add(module.nameOrigin().name());
      }
    }
  }

  private static List<String> copyStrings(List<String> values, String name) {
    return List.copyOf(Objects.requireNonNull(values, name));
  }

  private static void requireMatchingLocations(List<Path> configured, List<DependencySnapshot> captured, String name) {
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

  private static void addDependencyIdentity(List<String> components, String role, List<DependencySnapshot> dependencies) {
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
