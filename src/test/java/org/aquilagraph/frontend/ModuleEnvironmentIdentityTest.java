package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.spi.ToolProvider;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.DependencySemantics;
import org.aquilagraph.identity.api.EnvironmentIdentityScheme;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.ModuleCandidate;
import org.aquilagraph.identity.api.ModuleNameOrigin;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleEnvironmentIdentityTest {
  @TempDir Path directory;

  @Test
  void givenChangedPathBearingOptions_whenPublicSourceIsProduced_thenCapturedOptionsDistinguishEnvironmentAndSoftware() throws IOException {
    // Arrange
    var firstOutput = Files.createDirectories(directory.resolve("first"));
    var secondOutput = Files.createDirectories(directory.resolve("second"));
    var source = InputSnapshot.fromSources(directory, List.of(
        SourceFile.fromContent(Path.of("Example.java"), "class Example { int value() { return 7; } }")));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.produce(source, new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(),
        List.of("-d", firstOutput.toString())), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var second = frontend.produce(source, new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(),
        List.of("-d", secondOutput.toString())), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var a = first.result().orElseThrow();
    var b = second.result().orElseThrow();

    // Assert
    assertEquals(AstCompletion.COMPLETE, first.completion());
    assertEquals(AstCompletion.COMPLETE, second.completion());
    assertEquals(List.of(), first.diagnostics());
    assertEquals(List.of(), second.diagnostics());
    assertEquals(source, a.snapshot());
    assertEquals(source, b.snapshot());
    assertEquals(EnvironmentIdentityScheme.MODULE_SEMANTICS, a.environment().identityScheme());
    assertEquals(EnvironmentIdentityScheme.MODULE_SEMANTICS, b.environment().identityScheme());
    assertEquals(List.of("--release", "25", "-encoding", "UTF-8", "-proc:none", "-d", firstOutput.toString()),
        a.environment().analysisOptions());
    assertEquals(List.of("--release", "25", "-encoding", "UTF-8", "-proc:none", "-d", secondOutput.toString()),
        b.environment().analysisOptions());
    assertNotEquals(a.environment().environmentDigest(), b.environment().environmentDigest());
    assertNotEquals(a.identity().softwareSignature(), b.identity().softwareSignature());
  }

  @Test
  void givenIdenticalAutomaticArchives_whenPublicSourceIsProduced_thenNamesSignaturesAndCompilerResolutionDiffer() throws IOException {
    // Arrange
    var alpha = archive("alpha.jar", Optional.empty());
    var beta = Files.copy(alpha, directory.resolve("beta.jar"));
    var source = snapshot("module consumer.sample { requires alpha; }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var matching = frontend.produce(source, configuration(List.of(), List.of(alpha)), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var nonmatching = frontend.produce(source, configuration(List.of(), List.of(beta)), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var first = matching.result().orElseThrow();
    var secondEnvironment = nonmatching.capturedEnvironment().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(first), first.identity());

    // Assert
    assertArrayEquals(Files.readAllBytes(alpha), Files.readAllBytes(beta));
    assertEquals(AstCompletion.COMPLETE, matching.completion());
    assertNotEquals(AstCompletion.COMPLETE, nonmatching.completion());
    assertTrue(nonmatching.diagnostics().stream().anyMatch(value -> value.message().contains("alpha")));
    assertEquals(EnvironmentIdentityScheme.MODULE_SEMANTICS, first.environment().identityScheme());
    assertEquals(List.of(new ModuleCandidate(".", "alpha", Optional.empty(), ModuleNameOrigin.FILENAME)),
        first.environment().modulePath().getFirst().semantics().modules());
    assertEquals(List.of(new ModuleCandidate(".", "beta", Optional.empty(), ModuleNameOrigin.FILENAME)),
        secondEnvironment.modulePath().getFirst().semantics().modules());
    assertEquals(first.environment().modulePath().getFirst().fingerprint(), secondEnvironment.modulePath().getFirst().fingerprint());
    assertNotEquals(first.environment().environmentDigest(), secondEnvironment.environmentDigest());
    assertNotEquals(SoftwareVersion.from(source, first.environment()).signature(), SoftwareVersion.from(source, secondEnvironment).signature());
    assertEquals(source, nonmatching.capturedInput().orElseThrow());
    assertEquals("java-source/21", first.identity().sourceModelVersion());
    assertEquals(first.environment(), decoded.environment());
    assertArrayEquals(AstCodec.encode(first), AstCodec.encode(decoded));
  }

  @Test
  void givenManifestNamedArchivesAtDifferentLocations_whenProduced_thenEnvironmentIdentityIgnoresLocationOnlyChanges() throws IOException {
    // Arrange
    var original = archive("first.jar", Optional.of("stable.module"));
    var relocated = Files.copy(original, directory.resolve("second.jar"));
    var source = snapshot("module consumer.sample { requires stable.module; }");

    // Act
    var first = produce(source, List.of(), List.of(original));
    var second = produce(source, List.of(), List.of(relocated));

    // Assert
    assertEquals(AstCompletion.COMPLETE, first.completion());
    assertEquals(AstCompletion.COMPLETE, second.completion());
    assertEquals(ModuleNameOrigin.MANIFEST, first.environment().modulePath().getFirst().semantics().modules().getFirst().nameOrigin());
    assertEquals(first.environment().environmentDigest(), second.environment().environmentDigest());
    assertEquals(first.identity().softwareSignature(), second.identity().softwareSignature());
    assertNotEquals(first.identity(), second.identity());
  }

  @Test
  void givenRepeatedClassAndModulePathOccurrences_whenProduced_thenRolesPositionsAndShadowedCandidatesRemain() throws IOException {
    // Arrange
    var alpha = archive("alpha.jar", Optional.empty());
    var beta = Files.copy(alpha, directory.resolve("beta.jar"));
    var source = snapshot("module consumer.sample { requires alpha; }");

    // Act
    var result = produce(source, List.of(beta, beta), List.of(alpha, beta, alpha));

    // Assert
    assertEquals(List.of(alpha, beta, alpha), result.environment().modulePath().stream().map(value -> value.location()).toList());
    assertEquals(List.of("alpha", "beta", "alpha"), result.environment().modulePath().stream()
        .map(value -> value.semantics().modules().getFirst().lookupName()).toList());
    assertEquals(List.of(DependencySemantics.State.CLASS_PATH, DependencySemantics.State.CLASS_PATH),
        result.environment().classPath().stream().map(value -> value.semantics().state()).toList());
    assertTrue(result.environment().classPath().stream().allMatch(value -> value.semantics().modules().isEmpty()));
  }

  @Test
  void givenEmptyModuleDirectoryAndMissingEntry_whenProduced_thenSuccessfulAbsenceAndPhysicalAbsenceRemainDistinct() throws IOException {
    // Arrange
    var empty = Files.createDirectory(directory.resolve("modules"));
    var missing = directory.resolve("missing.jar");
    var source = snapshot("module consumer.sample {}");

    // Act
    var result = produce(source, List.of(missing), List.of(empty, missing));

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion());
    assertEquals(List.of(DependencySemantics.State.MODULES, DependencySemantics.State.MISSING),
        result.environment().modulePath().stream().map(value -> value.semantics().state()).toList());
    assertEquals(DependencySemantics.State.CLASS_PATH, result.environment().classPath().getFirst().semantics().state());
  }

  private AstResult produce(InputSnapshot source, List<Path> classPath, List<Path> modulePath) throws IOException {
    return new JavaFrontend(new JavaSourceLoader()).produce(source, configuration(classPath, modulePath), AstProfile.JAVA_SOURCE_MODULE_SERVICES)
        .result().orElseThrow();
  }

  @Test
  void givenExplicitArchivesAndJmods_whenProduced_thenDescriptorIdentityAndRelocationArePreserved() throws IOException {
    // Arrange
    var compiled = descriptor("compiled classes", "stable.module", 25);
    var jar = directory.resolve("unrelated.jar");
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("module-info.class"));
      output.write(Files.readAllBytes(compiled.resolve("module-info.class")));
      output.closeEntry();
    }
    var renamed = Files.copy(jar, directory.resolve("different.jar"));
    var jmod = Files.createDirectories(directory.resolve("archives!")).resolve("module archive.jmod");
    tool("jmod", "create", "--class-path", compiled.toString(), jmod.toString());
    var source = snapshot("module consumer.sample { requires stable.module; }");

    // Act
    var first = produce(source, List.of(), List.of(jar));
    var second = produce(source, List.of(), List.of(renamed));
    var third = produce(source, List.of(), List.of(jmod));

    // Assert
    assertEquals(first.environment().environmentDigest(), second.environment().environmentDigest());
    for (var result : List.of(first, second, third)) {
      assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
      assertEquals(List.of(new ModuleCandidate(".", "stable.module", Optional.of("stable.module"), ModuleNameOrigin.DESCRIPTOR)),
          result.environment().modulePath().getFirst().semantics().modules());
    }
  }

  @Test
  void givenDirectAndChildExplodedModules_whenObserved_thenCompilerLookupAndDescriptorNamesRemainSeparate() throws IOException {
    // Arrange
    var compiled = descriptor("direct", "declared.module", 25);
    var group = Files.createDirectory(directory.resolve("module-group"));
    var child = Files.createDirectory(group.resolve("child.module"));
    Files.copy(compiled.resolve("module-info.class"), child.resolve("module-info.class"));
    Files.writeString(group.resolve("ignored.txt"), "ordinary nonmodule content");
    var source = snapshot("module consumer.sample {}");

    // Act
    var direct = produce(source, List.of(), List.of(compiled));
    var nested = produce(source, List.of(), List.of(group));

    // Assert
    assertEquals(List.of(new ModuleCandidate(".", "declared.module", Optional.of("declared.module"), ModuleNameOrigin.DESCRIPTOR)),
        direct.environment().modulePath().getFirst().semantics().modules());
    assertEquals(List.of(new ModuleCandidate("child.module", "child.module", Optional.of("declared.module"), ModuleNameOrigin.DIRECTORY)),
        nested.environment().modulePath().getFirst().semantics().modules());
    assertEquals(DependencySemantics.State.MODULES, nested.environment().modulePath().getFirst().semantics().state());
  }

  @Test
  void givenDuplicateNamesWithinAModuleDirectory_whenProduced_thenAllArtifactAssociationsRemain() throws IOException {
    // Arrange
    var first = archive("one.jar", Optional.of("duplicate.module"));
    var group = Files.createDirectory(directory.resolve("duplicates"));
    Files.copy(first, group.resolve("one.jar"));
    Files.copy(first, group.resolve("two.jar"));

    // Act
    var result = produce(snapshot("module consumer.sample { requires duplicate.module; }"), List.of(), List.of(group));

    // Assert
    assertEquals(List.of("one.jar", "two.jar"), result.environment().modulePath().getFirst().semantics().modules().stream()
        .map(ModuleCandidate::member).toList());
    assertEquals(List.of("duplicate.module", "duplicate.module"), result.environment().modulePath().getFirst().semantics().modules().stream()
        .map(ModuleCandidate::lookupName).toList());
    assertNotEquals(AstCompletion.COMPLETE, result.completion());
    assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().contains("duplicate.module")), result.diagnostics().toString());
  }

  @Test
  void givenMultiReleaseModuleDescriptors_whenProduced_thenTargetSelectedDescriptorDefinesTheModule() throws IOException {
    // Arrange
    var base = descriptor("base", "base.module", 9);
    var target = descriptor("target", "selected.module", 25);
    var manifest = new Manifest();
    manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
    manifest.getMainAttributes().putValue("Multi-Release", "true");
    var jar = directory.resolve("versioned.jar");
    try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
      output.putNextEntry(new JarEntry("module-info.class"));
      output.write(Files.readAllBytes(base.resolve("module-info.class")));
      output.closeEntry();
      output.putNextEntry(new JarEntry("META-INF/versions/25/module-info.class"));
      output.write(Files.readAllBytes(target.resolve("module-info.class")));
      output.closeEntry();
    }

    // Act
    var result = produce(snapshot("module consumer.sample { requires selected.module; }"), List.of(), List.of(jar));

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(List.of(new ModuleCandidate(".", "selected.module", Optional.of("selected.module"), ModuleNameOrigin.DESCRIPTOR)),
        result.environment().modulePath().getFirst().semantics().modules());
  }

  @Test
  void givenInvalidManifestModuleName_whenProduced_thenObservationAndActualDiagnosticsRemainDistinct() throws IOException {
    // Arrange
    var invalid = archive("invalid.jar", Optional.of("not a module"));
    var source = snapshot("module consumer.sample {}");

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(source, configuration(List.of(), List.of(invalid)),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES);

    // Assert
    var observed = outcome.capturedEnvironment().orElseThrow().modulePath().getFirst().semantics();
    assertEquals(DependencySemantics.State.INVALID, observed.state());
    assertTrue(observed.modules().isEmpty());
    assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
    assertTrue(outcome.diagnostics().stream().anyMatch(value -> value.message().contains("module")));
  }

  private Path descriptor(String destination, String moduleName, int release) throws IOException {
    var root = Files.createDirectories(directory.resolve(destination));
    var source = root.resolve("module-info.java");
    Files.writeString(source, "module " + moduleName + " {}");
    var classes = Files.createDirectories(root.resolve("classes"));
    tool("javac", "--release", Integer.toString(release), "-d", classes.toString(), source.toString());
    return classes;
  }

  @Test
  void givenMalformedDescriptorOnDifferentRoles_whenProduced_thenClassPathUseDoesNotClaimOrValidateNamedModules() throws IOException {
    // Arrange
    var entry = Files.createDirectory(directory.resolve("broken-module"));
    Files.write(entry.resolve("module-info.class"), new byte[] {1, 2, 3});
    var source = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { int value() { return 1; } }")));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var classPath = frontend.produce(source, configuration(List.of(entry), List.of()), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var modulePath = frontend.produce(source, configuration(List.of(), List.of(entry)), AstProfile.JAVA_SOURCE_MODULE_SERVICES);

    // Assert
    assertEquals(AstCompletion.COMPLETE, classPath.completion(), classPath.diagnostics().toString());
    assertEquals(DependencySemantics.State.CLASS_PATH, classPath.capturedEnvironment().orElseThrow().classPath().getFirst().semantics().state());
    assertEquals(DependencySemantics.State.INVALID, modulePath.capturedEnvironment().orElseThrow().modulePath().getFirst().semantics().state());
    assertNotEquals(AstCompletion.COMPLETE, modulePath.completion());
    assertTrue(modulePath.diagnostics().stream().anyMatch(value -> value.message().contains("module")));
  }

  private static void tool(String name, String... arguments) {
    var diagnostics = new StringWriter();
    try (var writer = new PrintWriter(diagnostics)) {
      assertEquals(0, ToolProvider.findFirst(name).orElseThrow().run(writer, writer, arguments), diagnostics.toString());
    }
  }

  @Test
  void givenShadowedModulesWithDifferentDeclarations_whenPathOrderChanges_thenCompilerFirstMatchAndEveryCapturedOccurrenceAgree() throws IOException {
    // Arrange
    var primary = moduleArchive("primary.jar", true);
    var alternate = moduleArchive("alternate.jar", false);
    var source = InputSnapshot.fromSources(directory, List.of(
        SourceFile.fromContent(Path.of("module-info.java"), "module consumer.sample { requires shared.module; }"),
        SourceFile.fromContent(Path.of("sample/Consumer.java"), "package sample; class Consumer { api.Api value; }")));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.produce(source, configuration(List.of(), List.of(primary, alternate)), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var reversed = frontend.produce(source, configuration(List.of(), List.of(alternate, primary)), AstProfile.JAVA_SOURCE_MODULE_SERVICES);

    // Assert
    assertEquals(AstCompletion.COMPLETE, first.completion(), first.diagnostics().toString());
    assertNotEquals(AstCompletion.COMPLETE, reversed.completion());
    assertTrue(reversed.diagnostics().stream().anyMatch(value -> value.message().contains("api")));
    var originalEnvironment = first.capturedEnvironment().orElseThrow();
    var reversedEnvironment = reversed.capturedEnvironment().orElseThrow();
    assertEquals(List.of(primary, alternate), originalEnvironment.modulePath().stream().map(value -> value.location()).toList());
    assertEquals(List.of(alternate, primary), reversedEnvironment.modulePath().stream().map(value -> value.location()).toList());
    assertEquals(List.of("shared.module", "shared.module"), originalEnvironment.modulePath().stream()
        .map(value -> value.semantics().modules().getFirst().lookupName()).toList());
    assertNotEquals(originalEnvironment.environmentDigest(), reversedEnvironment.environmentDigest());
  }

  private Path moduleArchive(String name, boolean includeApi) throws IOException {
    var sources = Files.createDirectories(directory.resolve(name + "-source"));
    var module = sources.resolve("module-info.java");
    Files.writeString(module, includeApi ? "module shared.module { exports api; }" : "module shared.module {}");
    var classes = Files.createDirectories(sources.resolve("classes"));
    if (includeApi) {
      var api = Files.createDirectories(sources.resolve("api")).resolve("Api.java");
      Files.writeString(api, "package api; public interface Api {}");
      tool("javac", "--release", "25", "-d", classes.toString(), module.toString(), api.toString());
    } else {
      tool("javac", "--release", "25", "-d", classes.toString(), module.toString());
    }
    var destination = directory.resolve(name);
    try (var output = new JarOutputStream(Files.newOutputStream(destination)); var files = Files.walk(classes)) {
      for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
        output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
        output.write(Files.readAllBytes(file));
        output.closeEntry();
      }
    }
    return destination;
  }

  @Test
  void givenChangedManifestAfterCompilerClosure_whenCaptureIsVerified_thenFreshSemanticFactsAndInstabilityAreObserved() throws IOException {
    // Arrange
    var entry = archive("observed.jar", Optional.of("alpha"));
    var configuration = configuration(List.of(), List.of(entry));
    var compiler = getSystemJavaCompiler();
    var capture = new AnalysisEnvironmentCapture(new CompilerDependencyCapture());
    var options = JavaCompilerInvocation.from(configuration).analysisOptions();
    var expected = capture.capture(compiler, configuration, options);
    var original = produce(snapshot("module consumer.sample { requires alpha; }"), List.of(), List.of(entry));
    archive("observed.jar", Optional.of("beta"));

    // Act
    var actual = capture.capture(compiler, configuration, options);
    var failure = assertThrows(IOException.class, () -> capture.verifyUnchanged(compiler, configuration, options, expected));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals("alpha", expected.modulePath().getFirst().semantics().modules().getFirst().lookupName());
    assertEquals("beta", actual.modulePath().getFirst().semantics().modules().getFirst().lookupName());
    assertEquals("Compiler dependencies changed during analysis", failure.getMessage());
  }

  private InputSnapshot snapshot(String source) {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("module-info.java"), source)));
  }

  private static CompilerConfiguration configuration(List<Path> classPath, List<Path> modulePath) {
    return new CompilerConfiguration(JavaRelease.JAVA_25, classPath, modulePath, List.of());
  }

  private Path archive(String name, Optional<String> moduleName) throws IOException {
    var manifest = new Manifest();
    manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
    moduleName.ifPresent(value -> manifest.getMainAttributes().putValue("Automatic-Module-Name", value));
    var destination = directory.resolve(name);
    try (var output = new JarOutputStream(Files.newOutputStream(destination), manifest)) {
      output.putNextEntry(new JarEntry("sample.txt"));
      output.write("fixed content".getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
    }
    return destination;
  }
}
