package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validated operation and literal argument elements, shared by both native adapters. */
record BuildRequest(Path root, Path toolRoot, Path maven, BuildOperation operation, List<String> flags) {
  private static final String MAKE_ADAPTER = "--make";
  private static final int PROPERTY_PREFIX_LENGTH = "-D".length();
  private static final Set<String> OPTIONS = Set.of("-B", "--batch-mode", "--no-transfer-progress", "-o", "--offline", "-q", "--quiet",
      "-e", "--errors", "-X", "--debug", "-V", "--show-version");
  private static final Set<String> POSITIVE_PROPERTIES = Set.of("test.unit.forks", "test.integration.forks", "test.neo4j.cpu.count");

  BuildRequest {
    flags = List.copyOf(flags);
    flags.forEach(value -> validateFlag(operation, value));
  }

  static BuildRequest parse(String... arguments) throws IOException {
    var root = Path.of("").toRealPath();
    var executable = "";
    var toolRoot = "";
    var make = false;
    var operation = BuildOperation.COMPILE;
    var values = List.of(arguments).iterator();
    var flags = new ArrayList<String>();
    while (values.hasNext()) {
      var option = values.next();
      if (!option.startsWith("--")) {
        operation = BuildOperation.parse(option);
        values.forEachRemaining(flags::add);
        break;
      }
      if (MAKE_ADAPTER.equals(option)) {
        make = true;
        executable = System.getenv().getOrDefault("AQUILA_BUILD_MAVEN", "");
        continue;
      }
      if (!values.hasNext()) {
        throw new IllegalArgumentException("Missing value for " + option);
      }
      var value = values.next();
      switch (option) {
        case "--root" -> root = Path.of(value).toRealPath();
        case "--maven" -> executable = value;
        case "--tool-root" -> toolRoot = value;
        case "--skip-test-flag" -> requireSkipFlag(value);
        default -> throw new IllegalArgumentException("Unsupported build option: " + option);
      }
    }
    if (make) {
      flags.addAll(ArgumentWords.parse(System.getenv().getOrDefault("AQUILA_BUILD_FLAGS", "-B --no-transfer-progress")));
      ArgumentWords.parse(System.getenv().getOrDefault("AQUILA_BUILD_SKIP_FLAGS", "-Dmaven.test.skip=true"))
          .forEach(BuildRequest::requireSkipFlag);
    } else if (flags.isEmpty()) {
      flags.addAll(List.of("-B", "--no-transfer-progress"));
    }
    var effectiveFlags = new ArrayList<String>();
    if (operation != BuildOperation.CANCEL) {
      effectiveFlags.addAll(ArgumentWords.parse(System.getenv().getOrDefault("MAVEN_ARGS", "")));
    }
    effectiveFlags.addAll(flags);
    var maven = operation == BuildOperation.LINE_ENDINGS_CHECK || operation == BuildOperation.CANCEL
        ? root.resolve(executable.isEmpty() ? (windows() ? "mvnw.cmd" : "mvnw") : executable).normalize()
        : resolveMaven(root, executable);
    return new BuildRequest(root, toolRoot.isEmpty() ? root : Path.of(toolRoot).toRealPath(), maven, operation, effectiveFlags);
  }

  private static void requireSkipFlag(String value) {
    if (!"-Dmaven.test.skip=true".equals(value)) {
      throw new IllegalArgumentException("The local operation owns test skipping; unsupported skip flag");
    }
  }

  private static Path resolveMaven(Path root, String executable) throws IOException {
    if (executable.isEmpty()) {
      return root.resolve(windows() ? "mvnw.cmd" : "mvnw").toRealPath();
    }
    var supplied = Path.of(executable);
    if (supplied.isAbsolute() || supplied.getNameCount() > 1 || Files.isRegularFile(root.resolve(supplied))) {
      return root.resolve(supplied).toRealPath();
    }
    for (var directory : System.getenv().getOrDefault("PATH", "").split(windows() ? ";" : ":", -1)) {
      for (var suffix : windows() ? List.of("", ".cmd", ".exe", ".bat") : List.of("")) {
        var candidate = Path.of(directory).resolve(executable + suffix);
        if (Files.isRegularFile(candidate)) {
          return candidate.toRealPath();
        }
      }
    }
    throw new IOException("Maven executable was not found: " + executable);
  }

  static boolean windows() {
    return System.getProperty("os.name").startsWith("Windows");
  }

  static boolean linux() {
    return "Linux".equals(System.getProperty("os.name"));
  }

  static void validateFlag(BuildOperation operation, String flag) {
    if (operation == BuildOperation.VERIFY_ALL && Set.of("-q", "--quiet").contains(flag)) {
      throw new IllegalArgumentException("Complete validation requires observable Maven execution diagnostics");
    }
    if (OPTIONS.contains(flag)) {
      return;
    }
    if ("-Phigh-capacity-tests".equals(flag) && (operation == BuildOperation.TEST || operation == BuildOperation.COVERAGE
        || operation == BuildOperation.INTEGRATION_TEST || operation == BuildOperation.INTEGRATION_TEST_NEO4J
        || operation == BuildOperation.VERIFY_ALL || operation.focusedIntegration())) {
      return;
    }
    if (("-Pneo4j-integration".equals(flag) && operation == BuildOperation.INTEGRATION_TEST_NEO4J)
        || ("-Psupply-chain".equals(flag) && operation == BuildOperation.SUPPLY_CHAIN_CHECK)) {
      return;
    }
    if (flag.startsWith("-D") && operation != BuildOperation.CANCEL) {
      var separator = flag.indexOf('=');
      if (separator > PROPERTY_PREFIX_LENGTH) {
        var key = flag.substring(PROPERTY_PREFIX_LENGTH, separator);
        var value = flag.substring(separator + 1);
        if ("aquila.library.repository".equals(key) && operation == BuildOperation.INSTALL_LIBRARY) {
          var destination = Path.of(value);
          if (!destination.isAbsolute()) {
            throw new IllegalArgumentException("The library repository must be an absolute path");
          }
          return;
        }
        if (POSITIVE_PROPERTIES.contains(key) && value.matches("[1-9][0-9]*")) {
          return;
        }
        if ("test.integration.reuse.forks".equals(key) && Set.of("true", "false").contains(value)) {
          return;
        }
        if ("build.compilation.force".equals(key) && operation != BuildOperation.VERIFY_ALL && Set.of("true", "false").contains(value)) {
          return;
        }
        if ("test".equals(key) && operation == BuildOperation.TEST) {
          requireUnitSelector(value);
          return;
        }
        if ("build.integration.classes".equals(key) && operation.focusedIntegration()
            && value.matches("[A-Za-z_$][A-Za-z0-9_$.]*(,[A-Za-z_$][A-Za-z0-9_$.]*)*")) {
          return;
        }
        if ("build.integration.groups".equals(key) && operation == BuildOperation.TEST && SuiteCatalog.GROUPS.contains(value)) {
          return;
        }
      }
    }
    var displayed = flag.contains("=") ? flag.substring(0, flag.indexOf('=')) + "=<value>" : flag;
    throw new IllegalArgumentException("Unsupported flag for " + operation.command() + ": " + displayed
        + "; local operations own lifecycle goals, profiles and verification settings");
  }

  private static void requireUnitSelector(String value) {
    for (var selection : value.split(",", -1)) {
      var method = selection.indexOf('#');
      var type = method < 0 ? selection : selection.substring(0, method);
      if (!type.matches("([A-Za-z_$][A-Za-z0-9_$]*\\.)*([A-Za-z_$][A-Za-z0-9_$]*)?Test") || method == selection.length() - 1
          || selection.contains("\n") || selection.contains("\r")) {
        throw new IllegalArgumentException("Unit test selectors require explicit *Test class names, optionally followed by #method; "
            + "use a separate integration command for real-environment tests");
      }
    }
  }

  List<String> mavenArguments() {
    var result = new ArrayList<String>();
    result.add("-Dmaven.repo.local=" + toolRoot.resolve("target/maven-repository"));
    result.addAll(flags);
    if (operation == BuildOperation.VERIFY_ALL) {
      result.add("-Doutput=" + root.resolve("target/complete-validation/effective-pom.xml"));
      result.add("-Dstyle.color=never");
      result.add("-Dbuild.complete.skip=false");
      result.add("-Dbuild.failure.limit=1");
    }
    result.addAll(operation.arguments());
    return List.copyOf(result);
  }

  String integrationClasses() {
    var prefix = "-Dbuild.integration.classes=";
    return flags.stream().filter(value -> value.startsWith(prefix)).map(value -> value.substring(prefix.length())).findFirst().orElse("");
  }

  void validateEnvironment(Map<String, String> environment) throws IOException {
    MavenConfiguration.validate(this, environment);
  }
}
