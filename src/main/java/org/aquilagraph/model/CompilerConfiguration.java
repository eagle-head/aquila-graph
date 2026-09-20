package org.aquilagraph.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Normalized compiler inputs accepted by the Java frontend.
 *
 * @param release
 *          supported Java language release
 * @param classPath
 *          class-path entries
 * @param modulePath
 *          module-path entries
 * @param options
 *          caller-supplied non-managed compiler options
 */
public record CompilerConfiguration(JavaRelease release, List<Path> classPath, List<Path> modulePath, List<String> options) {
  /** Validates the release and normalizes paths and options. */
  public CompilerConfiguration {
    Objects.requireNonNull(release, "release");
    classPath = normalizePaths(classPath, "classPath");
    modulePath = normalizePaths(modulePath, "modulePath");
    options = List.copyOf(Objects.requireNonNull(options, "options"));
    for (var option : options) {
      Objects.requireNonNull(option, "option");
      if (isManagedOption(option)) {
        throw new IllegalArgumentException("Compiler option is managed by Aquila Graph: " + option);
      }
    }
  }

  /**
   * Creates an explicit configuration for a supported release without external dependencies or additional options.
   *
   * @param release
   *          supported Java language release
   * @return compiler configuration for the release
   */
  public static CompilerConfiguration forRelease(JavaRelease release) {
    return new CompilerConfiguration(release, List.of(), List.of(), List.of());
  }

  /**
   * Returns an immutable copy of normalized class-path entries.
   *
   * @return class path
   */
  @Override
  public List<Path> classPath() {
    return List.copyOf(classPath);
  }

  /**
   * Returns an immutable copy of normalized module-path entries.
   *
   * @return module path
   */
  @Override
  public List<Path> modulePath() {
    return List.copyOf(modulePath);
  }

  /**
   * Serializes the configuration deterministically for stable identity.
   *
   * @return canonical configuration text
   */
  public String canonicalForm() {
    return release.feature() + "\n" + join(classPath) + "\n" + join(modulePath) + "\n" + String.join("\u0000", options);
  }

  private static List<Path> normalizePaths(List<Path> paths, String name) {
    return Objects.requireNonNull(paths, name).stream()
        .map(path -> Objects.requireNonNull(path, name + " entry").toAbsolutePath().normalize()).toList();
  }

  private static String join(List<Path> paths) {
    return paths.stream().map(Path::toString).reduce((left, right) -> left + "\u0000" + right).orElse("");
  }

  private static boolean isManagedOption(String option) {
    return option.startsWith("@") || isOption(option, "--release") || "-encoding".equals(option) || "-proc:none".equals(option)
        || option.startsWith("-proc:") || isOption(option, "--class-path") || isOption(option, "-classpath") || isOption(option, "-cp")
        || isOption(option, "--module-path") || isOption(option, "-p") || isOption(option, "--source-path")
        || isOption(option, "-sourcepath") || isOption(option, "--module-source-path") || isOption(option, "--upgrade-module-path")
        || isOption(option, "--system") || isOption(option, "--patch-module") || isOption(option, "--processor-path")
        || isOption(option, "-processorpath") || isOption(option, "--processor-module-path") || isOption(option, "--boot-class-path")
        || isOption(option, "-bootclasspath") || isOption(option, "-extdirs") || isOption(option, "-endorseddirs")
        || option.startsWith("-Xbootclasspath:") || option.startsWith("-Xbootclasspath/p:") || option.startsWith("-Xbootclasspath/a:");
  }

  private static boolean isOption(String option, String managedName) {
    return managedName.equals(option) || option.startsWith(managedName + "=");
  }
}
