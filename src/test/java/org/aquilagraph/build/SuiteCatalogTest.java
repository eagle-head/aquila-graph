package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.aquilagraph.build.SuiteCatalog.Entry;
import org.aquilagraph.build.SuiteCatalog.Kind;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.TestPlan;

class SuiteCatalogTest {
  @Test
  void givenUntaggedUnitsAndFactories_whenUnitDiscoveryRuns_thenTheWholeUnitInventoryNeedsNoIntegrationTag() {
    // Arrange
    var plan = discover(UnclassifiedCase.class, ProcessCase.class);

    // Act
    var units = SuiteCatalogProbe.catalog(plan, "unit", "");

    // Assert
    assertEquals(3, units.entries().size());
    assertEquals(units.entries(), units.selected());
    assertTrue(units.entries().stream().allMatch(entry -> "unit".equals(entry.group())));
    assertEquals(1, units.entries().stream().filter(entry -> entry.kind() == Kind.CONTAINER).count());
  }

  @Test
  void givenInheritedTestsAndDynamicFactory_whenDiscovered_thenConcreteOwnersAndFactoryIdentitiesAreSelected() {
    // Arrange
    var plan = discover(ProcessCase.class, PersistenceCase.class, LifecycleCase.class);

    // Act
    var all = SuiteCatalogProbe.catalog(plan, "all", "");
    var selected = SuiteCatalogProbe.catalog(plan, "process", ProcessCase.class.getName());

    // Assert
    assertEquals(4, all.entries().size());
    assertEquals(2, selected.selected().size());
    assertTrue(selected.selected().stream().allMatch(entry -> entry.className().equals(ProcessCase.class.getName())));
    assertEquals(1, selected.selected().stream().filter(entry -> entry.kind() == Kind.CONTAINER).count());
    var union = Stream.of("process", "persistence", "lifecycle")
        .flatMap(scope -> SuiteCatalogProbe.catalog(plan, scope, "").selected().stream()).map(Entry::id).sorted().toList();
    assertEquals(all.entries().stream().map(Entry::id).sorted().toList(), union);
  }

  @Test
  void givenMissingConflictingOrUnknownOwnership_whenDiscovered_thenEachInvalidInventoryIsRefused() {
    // Arrange
    var invalid = List.of(UnclassifiedCase.class, ConflictingCase.class, UnknownCase.class, MethodConflictCase.class);

    // Act
    var failures = invalid.stream().map(type -> assertThrows(IllegalArgumentException.class,
        () -> SuiteCatalogProbe.catalog(discover(type), "all", ""))).toList();

    // Assert
    assertEquals(4, failures.size());
    failures.forEach(failure -> assertTrue(failure.getMessage().contains("functional tag")));
  }

  @Test
  void givenEmptyMisspelledOrWrongScopeSelection_whenResolved_thenNoRequestCanSilentlyPass() {
    // Arrange
    var plan = discover(ProcessCase.class);

    // Act
    var empty = assertThrows(IllegalArgumentException.class, () -> SuiteCatalogProbe.catalog(plan, "persistence", "").selected());
    var misspelled = assertThrows(IllegalArgumentException.class, () -> SuiteCatalogProbe.catalog(plan, "process", "MissingIT").selected());
    var unknown = assertThrows(IllegalArgumentException.class, () -> SuiteCatalogProbe.catalog(plan, "proces", ""));

    // Assert
    assertTrue(empty.getMessage().contains("empty"));
    assertTrue(misspelled.getMessage().contains("selector"));
    assertTrue(unknown.getMessage().contains("Unknown integration scope"));
  }

  @Test
  void givenFocusedAndFullCommands_whenRequestsAreBuilt_thenGoalsAndSelectorsStayInTheirOwnedScope() {
    // Arrange
    var selector = "-Dbuild.integration.classes=ExampleProcessIT";

    // Act
    BuildRequest.validateFlag(BuildOperation.CHECK_PROCESS, selector);
    BuildRequest.validateFlag(BuildOperation.TEST, "-Dbuild.integration.groups=persistence");
    var full = assertThrows(IllegalArgumentException.class,
        () -> BuildRequest.validateFlag(BuildOperation.INTEGRATION_TEST_NEO4J, selector));
    var empty = assertThrows(IllegalArgumentException.class,
        () -> BuildRequest.validateFlag(BuildOperation.CHECK_PROCESS, "-Dbuild.integration.classes="));

    // Assert
    assertEquals(List.of("pre-integration-test", "org.apache.maven.plugins:maven-failsafe-plugin:3.6.0:integration-test",
        "org.apache.maven.plugins:maven-failsafe-plugin:3.6.0:verify"), BuildOperation.CHECK_PROCESS.goals());
    assertTrue(full.getMessage().contains("Unsupported flag"));
    assertTrue(empty.getMessage().contains("Unsupported flag"));
  }

  private static TestPlan discover(Class<?>... types) {
    try (var session = LauncherFactory.openSession()) {
      return session.getLauncher().discover(LauncherDiscoveryRequestBuilder.request()
          .selectors(Stream.of(types).map(DiscoverySelectors::selectClass).toList()).build());
    }
  }

  abstract static class InheritedCase {
    @Test
    void givenInheritedContract_whenExecuted_thenTheConcreteClassOwnsTheResult() {
      // Arrange
      var expected = 7;
      // Act
      var actual = 3 + 4;
      // Assert
      assertEquals(expected, actual);
    }
  }

  @Tag("process")
  static class ProcessCase extends InheritedCase {
    @TestFactory
    Stream<DynamicTest> givenDynamicInputs_whenFactoryExecutes_thenChildrenAreDiscoveredAtRuntime() {
      // Arrange
      var inputs = List.of(2, 5);
      // Act
      var cases = inputs.stream().map(value -> DynamicTest.dynamicTest("value " + value, () -> assertTrue(value > 0)));
      // Assert
      return cases;
    }
  }

  @Tag("persistence")
  static class PersistenceCase extends InheritedCase {
  }

  @Tag("lifecycle")
  static class LifecycleCase extends InheritedCase {
  }

  static class UnclassifiedCase extends InheritedCase {
  }

  @Tag("process")
  @Tag("persistence")
  static class ConflictingCase extends InheritedCase {
  }

  @Tag("proces")
  static class UnknownCase extends InheritedCase {
  }

  @Tag("process")
  static class MethodConflictCase {
    @Test
    @Tag("lifecycle")
    void givenMethodOwnershipConflict_whenDiscovered_thenTheCatalogRejectsIt() {
      // Arrange
      // Act
      // Assert
      throw new AssertionError("Discovery must not execute this body");
    }
  }
}
