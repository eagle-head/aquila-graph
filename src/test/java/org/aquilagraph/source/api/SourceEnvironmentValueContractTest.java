package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.DependencyKind;
import org.aquilagraph.identity.api.DependencySnapshot;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Test;

class SourceEnvironmentValueContractTest {
  private static final List<String> ANALYSIS_OPTIONS = List.of("--release", "25", "-encoding", "UTF-8", "-proc:none");

  @Test
  void givenNullSnapshotFieldsAndSourceElements_whenValuesAreConstructed_thenRequiredInputIsRejectedAtItsOwner() {
    // Arrange
    var source = SourceFile.fromContent(Path.of("Sample.java"), "class Sample {}");
    List<Executable> invalidInputs = List.of(
        () -> new InputSnapshot(null, "SHA-256", "digest", List.of()),
        () -> new InputSnapshot(Path.of("sources"), null, "digest", List.of()),
        () -> new InputSnapshot(Path.of("sources"), "SHA-256", null, List.of()),
        () -> new InputSnapshot(Path.of("sources"), "SHA-256", "digest", null),
        () -> new InputSnapshot(Path.of("sources"), "SHA-256", "digest", Collections.singletonList(null)),
        () -> InputSnapshot.fromSources(Path.of("sources"), null),
        () -> InputSnapshot.fromSources(Path.of("sources"), Collections.singletonList(null)),
        () -> InputSnapshot.fromSources(null, List.of(source)),
        () -> SourceFile.fromContent(null, ""),
        () -> SourceFile.fromContent(Path.of("Sample.java"), null));

    // Act
    var failures = invalidInputs.stream().map(input -> assertThrows(NullPointerException.class, input)).toList();

    // Assert
    assertEquals(10, failures.size());
    assertEquals("sourceRoot", failures.getFirst().getMessage());
    assertEquals("digestAlgorithm", failures.get(1).getMessage());
    assertEquals("snapshotDigest", failures.get(2).getMessage());
    assertEquals("sourceFiles", failures.get(3).getMessage());
  }

  @Test
  void givenAmbiguousComponentConcatenationAndMultibyteText_whenStableIdsAreDerived_thenLiteralUtf8FramingDistinguishesEachInput() {
    // Arrange
    var namespace = "frame";

    // Act
    var first = StableId.sha256(namespace, "a", "bc");
    var second = StableId.sha256(namespace, "ab", "c");
    var multibyte = StableId.sha256(namespace, "é");

    // Assert
    assertEquals("frame:a718807735441f3841913428f08a06a24ace622e1003e5630e931d4055ddc939", first);
    assertEquals("frame:66cb58bcb43608093cec2022c7cccecb33d1555f1cbfb39507822d95f1324233", second);
    assertEquals("frame:2082c815697d885ebcfa9cab503916c5668273f3059a693eb0d29ebb9671ed36", multibyte);
    assertThrows(NullPointerException.class, () -> StableId.sha256(null, "component"));
    assertThrows(NullPointerException.class, () -> StableId.sha256(namespace, (String[]) null));
    assertThrows(NullPointerException.class, () -> StableId.sha256(namespace, (String) null));
  }

  @Test
  void givenEveryManagedCompilerInputAliasAndNearbyCallerOptions_whenConfigurationIsCreated_thenOnlyTheExactManagedPredicatesRejectInput() {
    // Arrange
    var managed = List.of("@options", "--release", "--release=25", "-encoding", "-proc:none", "-proc:only", "--class-path",
        "--class-path=lib", "-classpath", "-classpath=lib", "-cp", "-cp=lib", "--module-path", "--module-path=lib", "-p", "-p=lib",
        "--source-path", "--source-path=src", "-sourcepath", "-sourcepath=src", "--module-source-path", "--module-source-path=src",
        "--upgrade-module-path", "--upgrade-module-path=lib", "--system", "--system=none", "--patch-module", "--patch-module=lib",
        "--processor-path", "--processor-path=lib", "-processorpath", "-processorpath=lib", "--processor-module-path",
        "--processor-module-path=lib", "--boot-class-path", "--boot-class-path=lib", "-bootclasspath", "-bootclasspath=lib", "-extdirs",
        "-extdirs=lib", "-endorseddirs", "-endorseddirs=lib", "-Xbootclasspath:lib", "-Xbootclasspath/p:lib", "-Xbootclasspath/a:lib");
    var callerOptions = List.of("-Xlint:all", "-processor", "caller.Processor", "-encoding=UTF-8", "--release-extra=25");

    // Act
    var failures = managed.stream().map(option -> assertThrows(IllegalArgumentException.class,
        () -> new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of(option)))).toList();
    var retained = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), callerOptions);

    // Assert
    assertEquals(managed.stream().map(option -> "Compiler option is managed by Aquila Graph: " + option).toList(),
        failures.stream().map(Throwable::getMessage).toList());
    assertEquals(callerOptions, retained.options());
  }

  @Test
  void givenNormalizedAbsoluteDependencyRolesAndOrderedOptions_whenCanonicalConfigurationIsQueried_thenLiteralNullAndNewlineSeparatorsAreRetained() {
    // Arrange
    var dependencies = Path.of("Dependencies").toAbsolutePath();
    var modules = Path.of("Modules").toAbsolutePath();
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25,
        List.of(dependencies.resolve("nested").resolve("..").resolve("alpha.jar"), dependencies.resolve("beta.jar")),
        List.of(modules.resolve("alpha.jar")), List.of("-Xlint:all", "-parameters"));

    // Act
    var canonical = configuration.canonicalForm();

    // Assert
    assertEquals("25\n" + dependencies.resolve("alpha.jar") + "\u0000" + dependencies.resolve("beta.jar") + "\n"
        + modules.resolve("alpha.jar") + "\n-Xlint:all\u0000-parameters", canonical);
    assertEquals("25\n\n\n", CompilerConfiguration.forRelease(JavaRelease.JAVA_25).canonicalForm());
  }

  @Test
  void givenNullCompilerDependencyAndVersionInputs_whenValuesAreConstructed_thenRequiredFieldsAndEntriesAreRejected() {
    // Arrange
    var environment = environment(List.of(), List.of());
    var snapshot = InputSnapshot.fromSources(Path.of("sources"), List.of());
    List<Executable> invalidInputs = List.of(
        () -> new CompilerConfiguration(null, List.of(), List.of(), List.of()),
        () -> new CompilerConfiguration(JavaRelease.JAVA_25, null, List.of(), List.of()),
        () -> new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), null, List.of()),
        () -> new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), null),
        () -> new CompilerConfiguration(JavaRelease.JAVA_25, Collections.singletonList(null), List.of(), List.of()),
        () -> new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), Collections.singletonList(null), List.of()),
        () -> new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), Collections.singletonList(null)),
        () -> new DependencySnapshot(null, DependencyKind.MISSING, 0, 0, ""),
        () -> new DependencySnapshot(Path.of("missing"), null, 0, 0, ""),
        () -> new DependencySnapshot(Path.of("missing"), DependencyKind.MISSING, 0, 0, null),
        () -> SoftwareVersion.from(null, environment),
        () -> SoftwareVersion.from(snapshot, null),
        () -> new SoftwareVersion(null, "environment"),
        () -> new SoftwareVersion("source", null));

    // Act
    var failures = invalidInputs.stream().map(input -> assertThrows(NullPointerException.class, input)).toList();

    // Assert
    assertEquals(14, failures.size());
    assertEquals("release", failures.getFirst().getMessage());
    assertEquals("location", failures.get(7).getMessage());
    assertEquals("kind", failures.get(8).getMessage());
    assertEquals("fingerprint", failures.get(9).getMessage());
  }

  @Test
  void givenNullBlankAndMismatchedEnvironmentInputs_whenEnvironmentIsConstructed_thenItsLocalAdmissionBoundaryRejectsEachPartition() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var configuredDependency = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(Path.of("dependency")), List.of(), List.of());
    List<Executable> nullInputs = List.of(
        () -> new AnalysisEnvironmentSnapshot(null, "compiler", "vendor", "version", List.of(), List.of(), List.of()),
        () -> new AnalysisEnvironmentSnapshot(configuration, null, "vendor", "version", List.of(), List.of(), List.of()),
        () -> new AnalysisEnvironmentSnapshot(configuration, "compiler", null, "version", List.of(), List.of(), List.of()),
        () -> new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", null, List.of(), List.of(), List.of()),
        () -> new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "version", null, List.of(), List.of()),
        () -> new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "version", Collections.singletonList(null), List.of(), List.of()),
        () -> new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "version", List.of(), null, List.of()),
        () -> new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "version", List.of(), List.of(), null),
        () -> new AnalysisEnvironmentSnapshot(configuredDependency, "compiler", "vendor", "version", List.of(), Collections.singletonList(null), List.of()));
    List<Executable> blankInputs = List.of(
        () -> new AnalysisEnvironmentSnapshot(configuration, " ", "vendor", "version", List.of(), List.of(), List.of()),
        () -> new AnalysisEnvironmentSnapshot(configuration, "compiler", " ", "version", List.of(), List.of(), List.of()),
        () -> new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", " ", List.of(), List.of(), List.of()));

    // Act
    var nullFailures = nullInputs.stream().map(input -> assertThrows(NullPointerException.class, input)).toList();
    var blankFailures = blankInputs.stream().map(input -> assertThrows(IllegalArgumentException.class, input)).toList();
    var callerAssured = new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "version", List.of("caller-option"), List.of(), List.of());

    // Assert
    assertEquals(9, nullFailures.size());
    assertEquals(List.of("compilerImplementation cannot be blank", "compilerVendor cannot be blank", "compilerVersion cannot be blank"),
        blankFailures.stream().map(Throwable::getMessage).toList());
    assertEquals(List.of("caller-option"), callerAssured.analysisOptions());
  }

  @Test
  void givenCallerAssuredSourceAndSnapshotSignatures_whenValuesAreConstructed_thenSuppliedSignaturesAreRetainedWithoutAuthentication() {
    // Arrange
    var source = new SourceFile("", Path.of("nested/../input.txt"), "", "");
    var sources = new ArrayList<>(List.of(source));

    // Act
    var snapshot = new InputSnapshot(Path.of("source"), "caller-algorithm", "caller-signature", sources);
    sources.clear();

    // Assert
    assertEquals(Path.of("input.txt"), source.relativePath());
    assertEquals("", source.sourceFileKey());
    assertEquals("", source.contentDigest());
    assertEquals("caller-algorithm", snapshot.digestAlgorithm());
    assertEquals("caller-signature", snapshot.snapshotDigest());
    assertEquals(List.of(source), snapshot.sourceFiles());
    assertThrows(NullPointerException.class, () -> new SourceFile(null, Path.of("input.txt"), "", ""));
    assertThrows(NullPointerException.class, () -> new SourceFile("", null, "", ""));
    assertThrows(NullPointerException.class, () -> new SourceFile("", Path.of("input.txt"), null, ""));
    assertThrows(NullPointerException.class, () -> new SourceFile("", Path.of("input.txt"), "", null));
  }

  @Test
  void givenCallerAssuredDependencyMetadata_whenValuesAreConstructed_thenNarrowCountChecksAndUnauthenticatedFingerprintAreRetained() {
    // Arrange
    var location = Path.of("library.jar");

    // Act
    var file = new DependencySnapshot(location, DependencyKind.REGULAR_FILE, 3, 1, "");
    var directory = new DependencySnapshot(location, DependencyKind.DIRECTORY, 7, 0, "caller-fingerprint");
    var missing = new DependencySnapshot(location, DependencyKind.MISSING, 0, 0, "caller-fingerprint");

    // Assert
    assertEquals(location.toAbsolutePath().normalize(), file.location());
    assertEquals("", file.fingerprint());
    assertEquals(7, directory.byteCount());
    assertEquals(0, missing.entryCount());
    assertThrows(IllegalArgumentException.class, () -> new DependencySnapshot(location, DependencyKind.DIRECTORY, -1, 0, ""));
    assertThrows(IllegalArgumentException.class, () -> new DependencySnapshot(location, DependencyKind.DIRECTORY, 0, -1, ""));
    assertThrows(IllegalArgumentException.class, () -> new DependencySnapshot(location, DependencyKind.MISSING, 1, 0, ""));
    assertThrows(IllegalArgumentException.class, () -> new DependencySnapshot(location, DependencyKind.MISSING, 0, 1, ""));
    assertThrows(IllegalArgumentException.class, () -> new DependencySnapshot(location, DependencyKind.REGULAR_FILE, 0, 0, ""));
  }

  @Test
  void givenOrderedDuplicateDependencyRolesAndMutableOptions_whenEnvironmentIsConstructed_thenPositionsAndDefensiveCopiesAreRetained() {
    // Arrange
    var first = Path.of("first.jar");
    var second = Path.of("second.jar");
    var paths = new ArrayList<>(List.of(first, second, first));
    var options = new ArrayList<>(List.of("-Xlint:all"));
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, paths, List.of(first), options);
    var firstValue = new DependencySnapshot(first, DependencyKind.REGULAR_FILE, 1, 1, "first");
    var secondValue = new DependencySnapshot(second, DependencyKind.REGULAR_FILE, 1, 1, "second");
    var captured = new ArrayList<>(List.of(firstValue, secondValue, firstValue));
    var analysisOptions = new ArrayList<>(ANALYSIS_OPTIONS);

    // Act
    var environment = new AnalysisEnvironmentSnapshot(configuration, "compiler.Implementation", "Vendor", "25.0.4+1", analysisOptions,
        captured, List.of(firstValue));
    paths.clear();
    options.clear();
    analysisOptions.clear();
    captured.clear();

    // Assert
    assertEquals(List.of(first.toAbsolutePath().normalize(), second.toAbsolutePath().normalize(), first.toAbsolutePath().normalize()),
        configuration.classPath());
    assertEquals(List.of("-Xlint:all"), configuration.options());
    assertEquals(ANALYSIS_OPTIONS, environment.analysisOptions());
    assertEquals(List.of(firstValue, secondValue, firstValue), environment.classPath());
    assertEquals(List.of(firstValue), environment.modulePath());
    assertThrows(UnsupportedOperationException.class, environment.classPath()::clear);
    assertThrows(IllegalArgumentException.class, () -> new AnalysisEnvironmentSnapshot(configuration, "compiler.Implementation", "Vendor",
        "25.0.4+1", ANALYSIS_OPTIONS, List.of(firstValue, firstValue, secondValue), List.of(firstValue)));
    assertThrows(IllegalArgumentException.class, () -> new AnalysisEnvironmentSnapshot(configuration, "compiler.Implementation", "Vendor",
        "25.0.4+1", ANALYSIS_OPTIONS, List.of(firstValue), List.of(firstValue)));
  }

  @Test
  void givenDependenciesWithDistinctFingerprints_whenPositionOrRoleChanges_thenEnvironmentIdentityDistinguishesEachOrderedRole() {
    // Arrange
    var first = new DependencySnapshot(Path.of("first.jar"), DependencyKind.REGULAR_FILE, 1, 1, "first");
    var second = new DependencySnapshot(Path.of("second.jar"), DependencyKind.REGULAR_FILE, 1, 1, "second");
    var baseline = environment(List.of(first, second), List.of());

    // Act
    var reversed = environment(List.of(second, first), List.of());
    var moved = environment(List.of(), List.of(first, second));
    var duplicated = environment(List.of(first, second, first), List.of());

    // Assert
    assertNotEquals(baseline.environmentDigest(), reversed.environmentDigest());
    assertNotEquals(baseline.environmentDigest(), moved.environmentDigest());
    assertNotEquals(baseline.environmentDigest(), duplicated.environmentDigest());
  }

  @Test
  void givenFixedCompilerFactsAndSourceDigest_whenCombinedSignaturesAreDerived_thenIndependentLiteralFourByteFramesArePreserved() {
    // Arrange
    var environment = environment(List.of(), List.of());
    var snapshot = new InputSnapshot(Path.of("sources"), "SHA-256",
        "snapshot:59ea0f249b25412396bb902ec3fc0ab7cc48048f7a75d1484a86288fe005fa14", List.of());

    // Act
    var version = SoftwareVersion.from(snapshot, environment);

    // Assert
    assertEquals("analysis-environment:9fcabc1ba8f477f6713d73fa6c7dc79089046bd0c1b48b6afa80befbdcc120de", environment.environmentDigest());
    assertEquals(snapshot.snapshotDigest(), version.sourceSignature());
    assertEquals(environment.environmentDigest(), version.environmentSignature());
    assertEquals("software-version:410089fc896eebc780c559b3488447e63394a32eeba17c33f877f27c3bfcbbb6", version.signature());
    assertThrows(IllegalArgumentException.class,
        () -> SoftwareVersion.from(new InputSnapshot(Path.of("sources"), "caller-algorithm", "caller-signature", List.of()), environment));
    assertThrows(IllegalArgumentException.class, () -> new SoftwareVersion("", "environment"));
    assertThrows(IllegalArgumentException.class, () -> new SoftwareVersion("source", " "));
  }

  private static AnalysisEnvironmentSnapshot environment(List<DependencySnapshot> classPath, List<DependencySnapshot> modulePath) {
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, classPath.stream().map(DependencySnapshot::location).toList(),
        modulePath.stream().map(DependencySnapshot::location).toList(), List.of());
    return new AnalysisEnvironmentSnapshot(configuration, "compiler.Implementation", "Vendor", "25.0.4+1", ANALYSIS_OPTIONS, classPath,
        modulePath);
  }
}
