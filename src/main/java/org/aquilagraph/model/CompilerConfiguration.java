package org.aquilagraph.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record CompilerConfiguration(
    int release, List<Path> classPath, List<Path> modulePath, List<String> options) {
  public CompilerConfiguration {
    if (release != 25) {
      throw new IllegalArgumentException("The supported Java release is 25");
    }
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

  public static CompilerConfiguration java25() {
    return new CompilerConfiguration(25, List.of(), List.of(), List.of());
  }

  public String canonicalForm() {
    return release + "\n" + join(classPath) + "\n" + join(modulePath) + "\n" + String.join("\u0000", options);
  }

  private static List<Path> normalizePaths(List<Path> paths, String name) {
    return Objects.requireNonNull(paths, name).stream()
        .map(path -> Objects.requireNonNull(path, name + " entry").toAbsolutePath().normalize())
        .toList();
  }

  private static String join(List<Path> paths) {
    return paths.stream().map(Path::toString).reduce((left, right) -> left + "\u0000" + right).orElse("");
  }

  private static boolean isManagedOption(String option) {
    return option.startsWith("@")
        || isOption(option, "--release")
        || option.equals("-encoding")
        || option.equals("-proc:none")
        || option.startsWith("-proc:")
        || isOption(option, "--class-path")
        || isOption(option, "-classpath")
        || isOption(option, "-cp")
        || isOption(option, "--module-path")
        || isOption(option, "-p")
        || isOption(option, "--source-path")
        || isOption(option, "-sourcepath")
        || isOption(option, "--module-source-path")
        || isOption(option, "--upgrade-module-path")
        || isOption(option, "--system")
        || isOption(option, "--patch-module")
        || isOption(option, "--processor-path")
        || isOption(option, "-processorpath")
        || isOption(option, "--processor-module-path")
        || isOption(option, "--boot-class-path")
        || isOption(option, "-bootclasspath")
        || isOption(option, "-extdirs")
        || isOption(option, "-endorseddirs")
        || option.startsWith("-Xbootclasspath:")
        || option.startsWith("-Xbootclasspath/p:")
        || option.startsWith("-Xbootclasspath/a:");
  }

  private static boolean isOption(String option, String managedName) {
    return option.equals(managedName) || option.startsWith(managedName + "=");
  }
}
