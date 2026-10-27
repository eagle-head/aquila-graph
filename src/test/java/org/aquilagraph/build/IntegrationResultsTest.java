package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.aquilagraph.build.IntegrationCatalog.Entry;
import org.aquilagraph.build.IntegrationCatalog.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationResultsTest {
  private static final String CLASS_ID = "[engine:junit-jupiter]/[class:fixture.ExampleProcessIT]";
  private static final String STATIC_ID = CLASS_ID + "/[method:staticCase()]";
  private static final String FACTORY_ID = CLASS_ID + "/[test-factory:factoryCase()]";

  @TempDir
  Path directory;

  @Test
  void givenCurrentStaticAndDynamicOutcomes_whenReconciled_thenEveryDiscoveredIdentityAndRealizedChildIsRequired() throws Exception {
    // Arrange
    var catalog = catalog(false);
    var receipt = receipt("SUCCESSFUL", true);
    BuildFiles.write(directory.resolve("execution-fork.properties"), receipt);
    var summary = summary(3);
    catalog.write(directory.resolve("catalog.properties"));

    // Act
    var restored = IntegrationCatalog.read(directory.resolve("catalog.properties"));
    IntegrationResults.verify(restored, directory, summary);

    // Assert
    assertEquals(catalog, restored);
    assertEquals(2, restored.selected().size());
    var verification = BuildFiles.read(directory.resolve("verification.properties"));
    assertEquals("process", verification.getProperty("scope"));
    assertEquals("3", verification.getProperty("observed.cases"));
    assertEquals("false", verification.getProperty("complete.validation"));
  }

  @Test
  void givenFailedAbortedOrUnexpectedlySkippedOutcomes_whenReconciled_thenNoStatusCanSupplySuccess() throws Exception {
    // Arrange
    var statuses = List.of("FAILED", "ABORTED", "SKIPPED");
    var catalog = catalog(false);
    var summary = summary(3);

    // Act
    for (var status : statuses) {
      BuildFiles.write(directory.resolve("execution-fork.properties"), receipt(status, true));
      var failure = assertThrows(IOException.class, () -> IntegrationResults.verify(catalog, directory, summary));

      // Assert
      assertTrue(failure.getMessage().contains("did not succeed"), failure.getMessage());
    }
  }

  @Test
  void givenOmittedOrDuplicateExecutionAndEmptyFactory_whenReconciled_thenIncompleteOrRepeatedWorkFails() throws Exception {
    // Arrange
    var catalog = catalog(false);
    var summary = summary(1);
    var empty = receipt("SUCCESSFUL", false);
    BuildFiles.write(directory.resolve("execution-fork.properties"), empty);

    // Act
    var factory = assertThrows(IOException.class, () -> IntegrationResults.verify(catalog, directory, summary));
    var missing = new Properties();
    missing.setProperty("complete", "true");
    missing.setProperty("count", "0");
    BuildFiles.write(directory.resolve("execution-fork.properties"), missing);
    var omitted = assertThrows(IOException.class, () -> IntegrationResults.verify(catalog, directory, summary));
    BuildFiles.write(directory.resolve("execution-fork.properties"), empty);
    BuildFiles.write(directory.resolve("execution-duplicate.properties"), empty);
    var duplicate = assertThrows(IOException.class, () -> IntegrationResults.verify(catalog, directory, summary));

    // Assert
    assertTrue(factory.getMessage().contains("no test descendants"));
    assertTrue(omitted.getMessage().contains("no executed result"));
    assertTrue(duplicate.getMessage().contains("more than once"));
  }

  @Test
  void givenMissingIncompleteOrMismatchedReports_whenReconciled_thenStaleOrPartialEvidenceCannotPass() throws Exception {
    // Arrange
    var catalog = catalog(false);
    var summary = summary(8);

    // Act
    var absent = assertThrows(IOException.class, () -> IntegrationResults.verify(catalog, directory, summary));
    var incomplete = receipt("SUCCESSFUL", true);
    incomplete.setProperty("complete", "false");
    BuildFiles.write(directory.resolve("execution-fork.properties"), incomplete);
    var partial = assertThrows(IOException.class, () -> IntegrationResults.verify(catalog, directory, summary));
    BuildFiles.write(directory.resolve("execution-fork.properties"), receipt("SUCCESSFUL", true));
    var mismatch = assertThrows(IOException.class, () -> IntegrationResults.verify(catalog, directory, summary));

    // Assert
    assertTrue(absent.getMessage().contains("No current"));
    assertTrue(partial.getMessage().contains("Incomplete"));
    assertTrue(mismatch.getMessage().contains("does not reconcile"));
  }

  @Test
  void givenKnownPlatformCondition_whenItsClassIsSkipped_thenOnlyTheDeclaredPlatformSkipCanPass() throws Exception {
    // Arrange
    var catalog = catalog(true);
    var data = new Properties();
    data.setProperty("complete", "true");
    data.setProperty("count", "1");
    outcome(data, 0, CLASS_ID, "CONTAINER", "SKIPPED");
    BuildFiles.write(directory.resolve("execution-fork.properties"), data);
    var summary = summary(1);

    // Act
    IntegrationResults.verify(catalog, directory, summary);
    var unexpected = assertThrows(IOException.class, () -> IntegrationResults.verify(catalog(false), directory, summary));

    // Assert
    assertTrue(unexpected.getMessage().contains("did not succeed"));
  }

  @Test
  void givenUnknownCompletionKindOrStatus_whenDecoded_thenUnrecognizedRecordsCannotCertifyExecution() throws Exception {
    // Arrange
    var catalog = catalog(false);
    var summary = summary(3);
    var fields = List.of("kind", "status");

    // Act
    for (var field : fields) {
      var data = receipt("SUCCESSFUL", true);
      data.setProperty("entry.0." + field, "UNRECOGNIZED");
      BuildFiles.write(directory.resolve("execution-fork.properties"), data);
      var failure = assertThrows(IOException.class, () -> IntegrationResults.verify(catalog, directory, summary));

      // Assert
      assertTrue(failure.getMessage().contains("Invalid integration completion record"));
      assertFalse(Files.exists(directory.resolve("verification.properties")));
    }
  }

  private static IntegrationCatalog catalog(boolean platformSkipped) {
    return new IntegrationCatalog("process", "", List.of(
        new Entry(STATIC_ID, "fixture.ExampleProcessIT", "process", Kind.TEST, platformSkipped),
        new Entry(FACTORY_ID, "fixture.ExampleProcessIT", "process", Kind.CONTAINER, platformSkipped)));
  }

  private static Properties receipt(String status, boolean children) {
    var data = new Properties();
    data.setProperty("complete", "true");
    data.setProperty("count", children ? "4" : "2");
    outcome(data, 0, STATIC_ID, "TEST", status);
    outcome(data, 1, FACTORY_ID, "CONTAINER", "SUCCESSFUL");
    if (children) {
      outcome(data, 2, FACTORY_ID + "/[dynamic-test:1]", "TEST", "SUCCESSFUL");
      outcome(data, 3, FACTORY_ID + "/[dynamic-test:2]", "TEST", "SUCCESSFUL");
    }
    return data;
  }

  private static void outcome(Properties data, int index, String id, String kind, String status) {
    var prefix = "entry." + index + ".";
    data.setProperty(prefix + "id", id);
    data.setProperty(prefix + "kind", kind);
    data.setProperty(prefix + "status", status);
  }

  private Path summary(int completed) throws IOException {
    var file = directory.resolve("failsafe-summary.xml");
    Files.writeString(file, "<failsafe-summary><completed>" + completed
        + "</completed><errors>0</errors><failures>0</failures><skipped>0</skipped></failsafe-summary>");
    return file;
  }
}
