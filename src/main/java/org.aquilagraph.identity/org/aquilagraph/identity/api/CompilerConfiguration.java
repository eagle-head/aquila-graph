package org.aquilagraph.identity.api;

import java.util.HexFormat;
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
public record CompilerConfiguration(JavaRelease release, List<CapturedLocation> classPath, List<CapturedLocation> modulePath,
    List<String> options) {
  /** Validates the release, absolute dependency locations, and options. */
  public CompilerConfiguration {
    Objects.requireNonNull(release, "release");
    classPath = List.copyOf(Objects.requireNonNull(classPath, "classPath"));
    requireAbsolute(classPath, "classPath");
    modulePath = List.copyOf(Objects.requireNonNull(modulePath, "modulePath"));
    requireAbsolute(modulePath, "modulePath");
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
   * Serializes the configuration deterministically for stable identity.
   *
   * @return a hexadecimal representation of the framed canonical configuration
   */
  public String canonicalForm() {
    var writer = new CanonicalValueWriter("compiler-configuration/2");
    writer.integer(release.feature());
    CapturedValueEncoding.writeLocations(writer, classPath);
    CapturedValueEncoding.writeLocations(writer, modulePath);
    CapturedValueEncoding.writeStrings(writer, options);
    return HexFormat.of().formatHex(writer.toByteArray());
  }

  private static void requireAbsolute(List<CapturedLocation> locations, String name) {
    if (locations.stream().anyMatch(location -> !location.isAbsolute())) {
      throw new IllegalArgumentException(name + " requires absolute captured locations");
    }
  }

  private static boolean isManagedOption(String option) {
    return option.startsWith("@") || isOption(option, "--release") || isOption(option, "--enable-preview") || "-encoding".equals(option)
        || "-proc:none".equals(option) || option.startsWith("-proc:") || isOption(option, "--class-path") || isOption(option, "-classpath")
        || isOption(option, "-cp") || isOption(option, "--module-path") || isOption(option, "-p") || isOption(option, "--source-path")
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
