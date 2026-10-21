package org.aquilagraph.identity.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Full canonical captured values, excluding only root and separate dependency-location provenance. */
public enum CapturedValueEncoding {
  ;

  /** Version of the canonical captured-value field encoding. */
  public static final String FORMAT_VERSION = "captured-values/1";
  /** Canonical captured values with explicit environment and module semantics. */
  public static final String MODULE_SEMANTICS_FORMAT_VERSION = "captured-values/2";

  /**
   * Encodes captured components with explicit absence and meaningful sequence positions.
   *
   * @param input
   *          captured input, when established
   * @param environment
   *          captured environment, when established
   * @return independent canonical byte array
   */
  public static byte[] encode(Optional<InputSnapshot> input, Optional<AnalysisEnvironmentSnapshot> environment) {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(environment, "environment");
    var successor = environment.filter(value -> value.identityScheme() == EnvironmentIdentityScheme.MODULE_SEMANTICS).isPresent();
    var writer = new CanonicalValueWriter(successor ? MODULE_SEMANTICS_FORMAT_VERSION : FORMAT_VERSION);
    writer.text("input");
    writer.flag(input.isPresent());
    input.ifPresent(snapshot -> writeInput(writer, snapshot));
    writer.text("environment");
    writer.flag(environment.isPresent());
    environment.ifPresent(snapshot -> writeEnvironment(writer, snapshot));
    writer.text("software");
    writer.flag(input.isPresent() && environment.isPresent());
    if (input.isPresent() && environment.isPresent()) {
      var version = new SoftwareVersion(input.orElseThrow().snapshotDigest(), environment.orElseThrow().environmentDigest());
      writer.text(version.signatureAlgorithm());
      writer.text(version.sourceSignature());
      writer.text(version.environmentSignature());
      writer.text(version.signature());
    }
    return writer.toByteArray();
  }

  private static void writeInput(CanonicalValueWriter writer, InputSnapshot snapshot) {
    writer.text(snapshot.digestAlgorithm());
    writer.text(snapshot.snapshotDigest());
    writer.count(snapshot.sourceFiles().size());
    for (var source : snapshot.sourceFiles()) {
      writer.text(source.sourceFileKey());
      writer.text(source.canonicalRelativePath());
      writer.text(source.contentDigest());
      writer.text(source.content());
    }
  }

  private static void writeEnvironment(CanonicalValueWriter writer, AnalysisEnvironmentSnapshot environment) {
    var configuration = environment.compilerConfiguration();
    writer.text(environment.digestAlgorithm());
    writer.text(environment.environmentDigest());
    if (environment.identityScheme() == EnvironmentIdentityScheme.MODULE_SEMANTICS) {
      writer.text(environment.identityScheme().version());
    }
    writer.integer(configuration.release().feature());
    writePaths(writer, configuration.classPath());
    writePaths(writer, configuration.modulePath());
    writeStrings(writer, configuration.options());
    writer.text(configuration.canonicalForm());
    writer.text(environment.compilerImplementation());
    writer.text(environment.compilerVendor());
    writer.text(environment.compilerVersion());
    writeStrings(writer, environment.analysisOptions());
    writer.text("class-path");
    writeDependencies(writer, environment.classPath(), environment.identityScheme());
    writer.text("module-path");
    writeDependencies(writer, environment.modulePath(), environment.identityScheme());
  }

  private static void writeDependencies(CanonicalValueWriter writer, List<DependencySnapshot> dependencies,
      EnvironmentIdentityScheme scheme) {
    writer.count(dependencies.size());
    for (var dependency : dependencies) {
      writer.text(switch (dependency.kind()) {
        case MISSING -> "missing";
        case REGULAR_FILE -> "file";
        case DIRECTORY -> "directory";
      });
      writer.longInteger(dependency.byteCount());
      writer.integer(dependency.entryCount());
      writer.text(dependency.fingerprint());
      if (scheme == EnvironmentIdentityScheme.MODULE_SEMANTICS) {
        writer.text(dependency.semantics().state().name());
        writer.count(dependency.semantics().modules().size());
        for (var module : dependency.semantics().modules()) {
          writer.text(module.member());
          writer.text(module.lookupName());
          writer.flag(module.descriptorName().isPresent());
          module.descriptorName().ifPresent(writer::text);
          writer.text(module.nameOrigin().name());
        }
      }
    }
  }

  private static void writePaths(CanonicalValueWriter writer, List<Path> paths) {
    writer.count(paths.size());
    paths.forEach(path -> writer.text(path.toString()));
  }

  private static void writeStrings(CanonicalValueWriter writer, List<String> values) {
    writer.count(values.size());
    values.forEach(writer::text);
  }
}
