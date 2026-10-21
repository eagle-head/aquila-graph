package org.aquilagraph.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class EnvironmentSemanticsTest {
  @Test
  void givenModuleCandidates_whenConstructed_thenLegalOriginsAndExplicitAbsenceAreEnforced() {
    // Arrange
    var descriptor = new ModuleCandidate(".", "named.module", Optional.of("named.module"), ModuleNameOrigin.DESCRIPTOR);
    var directory = new ModuleCandidate("child", "child", Optional.of("declared.module"), ModuleNameOrigin.DIRECTORY);
    var manifest = new ModuleCandidate("a.jar", "manifest.module", Optional.empty(), ModuleNameOrigin.MANIFEST);
    var filename = new ModuleCandidate("b.jar", "filename.module", Optional.empty(), ModuleNameOrigin.FILENAME);
    var invalid = List.<Supplier<ModuleCandidate>>of(
        () -> new ModuleCandidate("", "name", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate(" ", "name", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate(Path.of("absolute").toAbsolutePath().toString(), "name", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate("/root", "name", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate("../parent", "name", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate("a/../b", "name", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate("a\\b", "name", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate("a//b", "name", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate("a/", "name", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate("a.jar", " ", Optional.empty(), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate("a.jar", "name", Optional.of(" "), ModuleNameOrigin.DESCRIPTOR),
        () -> new ModuleCandidate("a.jar", "lookup", Optional.of("other"), ModuleNameOrigin.DESCRIPTOR),
        () -> new ModuleCandidate("a.jar", "name", Optional.empty(), ModuleNameOrigin.DESCRIPTOR),
        () -> new ModuleCandidate("a.jar", "name", Optional.empty(), ModuleNameOrigin.DIRECTORY),
        () -> new ModuleCandidate("a.jar", "name", Optional.of("name"), ModuleNameOrigin.MANIFEST),
        () -> new ModuleCandidate("a.jar", "name", Optional.of("name"), ModuleNameOrigin.FILENAME),
        () -> new ModuleCandidate(".", "name", Optional.of("name"), ModuleNameOrigin.DIRECTORY),
        () -> new ModuleCandidate(".", ".", Optional.of("declared"), ModuleNameOrigin.DIRECTORY),
        () -> new ModuleCandidate("child", "other", Optional.of("declared"), ModuleNameOrigin.DIRECTORY));

    // Act
    var observed = List.of(descriptor, directory, manifest, filename);

    // Assert
    assertEquals(List.of(ModuleNameOrigin.DESCRIPTOR, ModuleNameOrigin.DIRECTORY, ModuleNameOrigin.MANIFEST, ModuleNameOrigin.FILENAME),
        observed.stream().map(ModuleCandidate::nameOrigin).toList());
    assertEquals(Optional.of("declared.module"), directory.descriptorName());
    invalid.forEach(create -> assertThrows(IllegalArgumentException.class, create::get));
    assertThrows(NullPointerException.class, () -> new ModuleCandidate(null, "name", Optional.empty(), ModuleNameOrigin.FILENAME));
    assertThrows(NullPointerException.class, () -> new ModuleCandidate(".", null, Optional.empty(), ModuleNameOrigin.FILENAME));
    assertThrows(NullPointerException.class, () -> new ModuleCandidate(".", "name", null, ModuleNameOrigin.FILENAME));
    assertThrows(NullPointerException.class, () -> new ModuleCandidate(".", "name", Optional.empty(), null));
  }

  @Test
  void givenUnorderedCandidatesAndExplicitStates_whenCopied_thenAssociationsMultiplicityAndImmutabilityRemain() {
    // Arrange
    var first = new ModuleCandidate("a.jar", "same", Optional.empty(), ModuleNameOrigin.FILENAME);
    var second = new ModuleCandidate("b.jar", "same", Optional.empty(), ModuleNameOrigin.FILENAME);
    var supplied = new ArrayList<>(List.of(second, first));

    // Act
    var modules = new DependencySemantics(DependencySemantics.State.MODULES, supplied);
    supplied.clear();
    var invalid = new DependencySemantics(DependencySemantics.State.INVALID, List.of(second, first));

    // Assert
    assertEquals(List.of(first, second), modules.modules());
    assertEquals(List.of(first, second), invalid.modules());
    assertThrows(UnsupportedOperationException.class, () -> modules.modules().clear());
    assertThrows(IllegalArgumentException.class, () -> new DependencySemantics(DependencySemantics.State.MODULES, List.of(first, first)));
    assertThrows(IllegalArgumentException.class, () -> new DependencySemantics(DependencySemantics.State.MODULES,
        List.of(first, new ModuleCandidate(".", "direct", Optional.empty(), ModuleNameOrigin.FILENAME))));
    for (var state : List.of(DependencySemantics.State.UNOBSERVED, DependencySemantics.State.CLASS_PATH, DependencySemantics.State.MISSING)) {
      assertEquals(List.of(), new DependencySemantics(state, List.of()).modules());
      assertThrows(IllegalArgumentException.class, () -> new DependencySemantics(state, List.of(first)));
    }
    assertEquals(List.of(), new DependencySemantics(DependencySemantics.State.MODULES, List.of()).modules());
    assertEquals(List.of(), new DependencySemantics(DependencySemantics.State.INVALID, List.of()).modules());
    assertThrows(NullPointerException.class, () -> new DependencySemantics(null, List.of()));
    assertThrows(NullPointerException.class, () -> new DependencySemantics(DependencySemantics.State.MODULES, null));
  }

  @Test
  void givenRoleSchemeAndPhysicalContradictions_whenEnvironmentIsConstructed_thenInconsistentCopiedValuesAreRejected() {
    // Arrange
    var path = Path.of("dependency.jar").toAbsolutePath();
    var automatic = new ModuleCandidate(".", "name", Optional.empty(), ModuleNameOrigin.FILENAME);
    var historical = new DependencySnapshot(path, DependencyKind.REGULAR_FILE, 7, 1, "caller-label");
    var modular = dependency(path, DependencyKind.REGULAR_FILE, DependencySemantics.State.MODULES, List.of(automatic));
    var classPath = dependency(path, DependencyKind.REGULAR_FILE, DependencySemantics.State.CLASS_PATH, List.of());

    // Act
    var accepted = environment(List.of(), List.of(modular), EnvironmentIdentityScheme.MODULE_SEMANTICS);

    // Assert
    assertEquals(List.of(modular), accepted.modulePath());
    assertEquals(EnvironmentIdentityScheme.HISTORICAL, environment(List.of(), List.of(historical), EnvironmentIdentityScheme.HISTORICAL).identityScheme());
    assertThrows(IllegalArgumentException.class, () -> environment(List.of(), List.of(historical), EnvironmentIdentityScheme.MODULE_SEMANTICS));
    assertThrows(IllegalArgumentException.class, () -> environment(List.of(), List.of(modular), EnvironmentIdentityScheme.HISTORICAL));
    assertThrows(IllegalArgumentException.class, () -> environment(List.of(modular), List.of(), EnvironmentIdentityScheme.MODULE_SEMANTICS));
    assertThrows(IllegalArgumentException.class, () -> environment(List.of(), List.of(classPath), EnvironmentIdentityScheme.MODULE_SEMANTICS));
    assertThrows(IllegalArgumentException.class, () -> dependency(path, DependencyKind.MISSING, DependencySemantics.State.MODULES, List.of()));
    assertThrows(IllegalArgumentException.class, () -> dependency(path, DependencyKind.MISSING, DependencySemantics.State.INVALID, List.of()));
    assertThrows(IllegalArgumentException.class, () -> dependency(path, DependencyKind.REGULAR_FILE, DependencySemantics.State.MISSING, List.of()));
    assertThrows(IllegalArgumentException.class, () -> dependency(path, DependencyKind.REGULAR_FILE, DependencySemantics.State.MODULES,
        List.of(new ModuleCandidate("child.jar", "name", Optional.empty(), ModuleNameOrigin.FILENAME))));
    assertEquals(DependencySemantics.State.CLASS_PATH,
        dependency(path, DependencyKind.MISSING, DependencySemantics.State.CLASS_PATH, List.of()).semantics().state());
    assertEquals(DependencySemantics.State.MISSING,
        dependency(path, DependencyKind.MISSING, DependencySemantics.State.MISSING, List.of()).semantics().state());
    assertEquals(DependencySemantics.State.INVALID,
        dependency(path, DependencyKind.DIRECTORY, DependencySemantics.State.INVALID,
            List.of(new ModuleCandidate("child.jar", "name", Optional.empty(), ModuleNameOrigin.FILENAME))).semantics().state());
    assertThrows(IllegalArgumentException.class, () -> new DependencySnapshot(path, DependencyKind.DIRECTORY, 0, 0, "label",
        new DependencySemantics(DependencySemantics.State.MODULES,
            List.of(new ModuleCandidate("child.jar", "name", Optional.empty(), ModuleNameOrigin.FILENAME)))));
    assertThrows(IllegalArgumentException.class, () -> new DependencySnapshot(path, DependencyKind.DIRECTORY, 7, 1, "label",
        new DependencySemantics(DependencySemantics.State.MODULES, List.of(
            new ModuleCandidate("a.jar", "alpha", Optional.empty(), ModuleNameOrigin.FILENAME),
            new ModuleCandidate("b.jar", "beta", Optional.empty(), ModuleNameOrigin.FILENAME)))));
    assertThrows(NullPointerException.class, () -> environment(List.of(), List.of(), null));
    assertThrows(NullPointerException.class, () -> new DependencySnapshot(path, DependencyKind.REGULAR_FILE, 7, 1, "label", null));
  }

  @Test
  void givenDirectDirectoriesAndAutomaticOrigins_whenDependenciesAreCopied_thenOnlyArchiveMembersCanBeAutomatic() {
    // Arrange
    var path = Path.of("module-directory");
    var states = List.of(DependencySemantics.State.MODULES, DependencySemantics.State.INVALID);
    var automaticOrigins = List.of(ModuleNameOrigin.FILENAME, ModuleNameOrigin.MANIFEST);
    var directExplicit = new ModuleCandidate(".", "named.module", Optional.of("named.module"), ModuleNameOrigin.DESCRIPTOR);
    var childExplicit = new ModuleCandidate("child", "child", Optional.of("named.module"), ModuleNameOrigin.DIRECTORY);

    // Act
    var accepted = new ArrayList<DependencySnapshot>();
    for (var state : states) {
      accepted.add(dependency(path, DependencyKind.DIRECTORY, state, List.of(directExplicit)));
      accepted.add(dependency(path, DependencyKind.DIRECTORY, state, List.of(childExplicit)));
      accepted.add(dependency(path, DependencyKind.DIRECTORY, state, List.of()));
      for (var origin : automaticOrigins) {
        accepted.add(dependency(path, DependencyKind.REGULAR_FILE, state,
            List.of(new ModuleCandidate(".", "automatic.module", Optional.empty(), origin))));
        accepted.add(dependency(path, DependencyKind.DIRECTORY, state,
            List.of(new ModuleCandidate("child.jar", "automatic.module", Optional.empty(), origin))));
      }
    }
    var captured = environment(List.of(), accepted, EnvironmentIdentityScheme.MODULE_SEMANTICS);

    // Assert
    assertEquals(accepted, captured.modulePath());
    assertEquals(DependencySemantics.State.UNOBSERVED,
        new DependencySnapshot(path, DependencyKind.DIRECTORY, 7, 1, "supplied").semantics().state());
    for (var state : states) {
      for (var origin : automaticOrigins) {
        var failure = assertThrows(IllegalArgumentException.class, () -> dependency(path, DependencyKind.DIRECTORY, state,
            List.of(new ModuleCandidate(".", "automatic.module", Optional.empty(), origin))));
        assertEquals("A direct directory module requires an explicit descriptor", failure.getMessage());
      }
    }
  }

  @Test
  void givenSuccessorFacts_whenDigestsAreDerived_thenIndependentFramingAndLocationExclusionHold() {
    // Arrange
    var module = new ModuleCandidate(".", "alpha", Optional.empty(), ModuleNameOrigin.FILENAME);
    var dependency = dependency(Path.of("alpha.jar"), DependencyKind.REGULAR_FILE, DependencySemantics.State.MODULES, List.of(module));
    var original = environment(List.of(), List.of(dependency), EnvironmentIdentityScheme.MODULE_SEMANTICS);
    var relocated = dependency(Path.of("other/alpha.jar"), DependencyKind.REGULAR_FILE, DependencySemantics.State.MODULES, List.of(module));
    var changed = dependency(Path.of("beta.jar"), DependencyKind.REGULAR_FILE, DependencySemantics.State.MODULES,
        List.of(new ModuleCandidate(".", "beta", Optional.empty(), ModuleNameOrigin.FILENAME)));
    var expected = StableId.sha256("analysis-environment/2", "language-release", "25", "compiler-implementation", "compiler",
        "compiler-vendor", "vendor", "compiler-version", "25", "analysis-options", "0", "class-path", "0", "module-path", "1",
        "REGULAR_FILE", "physical", "analysis-environment/2", "0", "1", "MODULES", "1", "alpha", "false", "", "FILENAME");

    // Act
    var actual = original.environmentDigest();

    // Assert
    assertEquals(expected, actual);
    assertEquals(actual, environment(List.of(), List.of(relocated), EnvironmentIdentityScheme.MODULE_SEMANTICS).environmentDigest());
    assertNotEquals(actual, environment(List.of(), List.of(changed), EnvironmentIdentityScheme.MODULE_SEMANTICS).environmentDigest());
    assertNotEquals(actual, environment(List.of(), List.of(dependency, dependency), EnvironmentIdentityScheme.MODULE_SEMANTICS).environmentDigest());
    assertEquals("analysis-environment/1", EnvironmentIdentityScheme.HISTORICAL.version());
    assertEquals("analysis-environment/2", EnvironmentIdentityScheme.MODULE_SEMANTICS.version());
  }

  private static DependencySnapshot dependency(Path path, DependencyKind kind, DependencySemantics.State state, List<ModuleCandidate> modules) {
    return new DependencySnapshot(path, kind, kind == DependencyKind.MISSING ? 0 : 7, kind == DependencyKind.MISSING ? 0 : Math.max(1, modules.size()), "physical",
        new DependencySemantics(state, modules));
  }

  @Test
  void givenEqualCandidateTuplesWithDifferentAssociations_whenDerived_thenTupleOrderAndMultiplicityHaveIndependentIdentityMeaning() {
    // Arrange
    var manifest = new ModuleCandidate("a.jar", "same", Optional.empty(), ModuleNameOrigin.MANIFEST);
    var filename = new ModuleCandidate("b.jar", "same", Optional.empty(), ModuleNameOrigin.FILENAME);
    var explicit = new ModuleCandidate("c.jar", "same", Optional.of("same"), ModuleNameOrigin.DESCRIPTOR);
    var original = dependency(Path.of("modules"), DependencyKind.DIRECTORY, DependencySemantics.State.MODULES,
        List.of(explicit, manifest, filename));
    var reassociated = dependency(Path.of("relocated"), DependencyKind.DIRECTORY, DependencySemantics.State.MODULES, List.of(
        new ModuleCandidate("x.jar", "same", Optional.of("same"), ModuleNameOrigin.DESCRIPTOR),
        new ModuleCandidate("y.jar", "same", Optional.empty(), ModuleNameOrigin.FILENAME),
        new ModuleCandidate("z.jar", "same", Optional.empty(), ModuleNameOrigin.MANIFEST)));

    // Act
    var first = environment(List.of(), List.of(original), EnvironmentIdentityScheme.MODULE_SEMANTICS);
    var second = environment(List.of(), List.of(reassociated), EnvironmentIdentityScheme.MODULE_SEMANTICS);

    // Assert
    assertEquals(first.environmentDigest(), second.environmentDigest());
    assertEquals(StableId.sha256("analysis-environment/2", "language-release", "25", "compiler-implementation", "compiler",
        "compiler-vendor", "vendor", "compiler-version", "25", "analysis-options", "0", "class-path", "0", "module-path", "1",
        "DIRECTORY", "physical", "analysis-environment/2", "0", "1", "MODULES", "3",
        "same", "false", "", "MANIFEST", "same", "false", "", "FILENAME", "same", "true", "same", "DESCRIPTOR"), first.environmentDigest());
    assertNotEquals(first, second);
    assertEquals(List.of(manifest, filename, explicit), original.semantics().modules());
    for (var modules : List.of(List.of(manifest), List.of(filename), List.of(explicit))) {
      var changed = dependency(Path.of("modules"), DependencyKind.DIRECTORY, DependencySemantics.State.MODULES, modules);
      assertNotEquals(first.environmentDigest(), environment(List.of(), List.of(changed), EnvironmentIdentityScheme.MODULE_SEMANTICS).environmentDigest());
    }
    assertNotEquals(environment(List.of(), List.of(dependency(Path.of("modules"), DependencyKind.DIRECTORY,
        DependencySemantics.State.MODULES, List.of(manifest))), EnvironmentIdentityScheme.MODULE_SEMANTICS).environmentDigest(),
        environment(List.of(), List.of(dependency(Path.of("modules"), DependencyKind.DIRECTORY,
            DependencySemantics.State.MODULES, List.of(filename))), EnvironmentIdentityScheme.MODULE_SEMANTICS).environmentDigest());
  }

  @Test
  void givenCapturedSuccessorEnvironment_whenEncoded_thenIndependentFieldReadingRetainsItsSchemeAndCompleteModuleFacts() {
    // Arrange
    var path = Path.of("alpha.jar").toAbsolutePath();
    for (var descriptorName : List.of(Optional.<String>empty(), Optional.of("alpha"))) {
      var nameOrigin = descriptorName.isPresent() ? ModuleNameOrigin.DESCRIPTOR : ModuleNameOrigin.FILENAME;
      var module = new ModuleCandidate(".", "alpha", descriptorName, nameOrigin);
    var dependency = dependency(path, DependencyKind.REGULAR_FILE, DependencySemantics.State.MODULES, List.of(module));
    var environment = environment(List.of(), List.of(dependency), EnvironmentIdentityScheme.MODULE_SEMANTICS);

    // Act
    var encoded = CapturedValueEncoding.encode(Optional.empty(), Optional.of(environment));

    // Assert
    var reader = new CanonicalValueReader(encoded, "captured-values/2");
    assertEquals("input", reader.text());
    assertEquals(false, reader.flag());
    assertEquals("environment", reader.text());
    assertEquals(true, reader.flag());
    assertEquals("SHA-256", reader.text());
    assertEquals(environment.environmentDigest(), reader.text());
    assertEquals("analysis-environment/2", reader.text());
    assertEquals(25, reader.integer());
    assertEquals(0, reader.count());
    assertEquals(1, reader.count());
    assertEquals(path.toString(), reader.text());
    assertEquals(0, reader.count());
    assertEquals(environment.compilerConfiguration().canonicalForm(), reader.text());
    assertEquals("compiler", reader.text());
    assertEquals("vendor", reader.text());
    assertEquals("25", reader.text());
    assertEquals(0, reader.count());
    assertEquals("class-path", reader.text());
    assertEquals(0, reader.count());
    assertEquals("module-path", reader.text());
    assertEquals(1, reader.count());
    assertEquals("file", reader.text());
    assertEquals(7L, reader.longInteger());
    assertEquals(1, reader.integer());
    assertEquals("physical", reader.text());
    assertEquals("MODULES", reader.text());
    assertEquals(1, reader.count());
    assertEquals(".", reader.text());
    assertEquals("alpha", reader.text());
    assertEquals(descriptorName.isPresent(), reader.flag());
    descriptorName.ifPresent(value -> assertEquals(value, reader.text()));
    assertEquals(nameOrigin.name(), reader.text());
    assertEquals("software", reader.text());
    assertEquals(false, reader.flag());
    assertEquals(0, reader.remaining());
    }
  }

  private static AnalysisEnvironmentSnapshot environment(List<DependencySnapshot> classPath, List<DependencySnapshot> modulePath,
      EnvironmentIdentityScheme scheme) {
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, classPath.stream().map(DependencySnapshot::location).toList(),
        modulePath.stream().map(DependencySnapshot::location).toList(), List.of());
    return new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "25", List.of(), classPath, modulePath, scheme);
  }
}
