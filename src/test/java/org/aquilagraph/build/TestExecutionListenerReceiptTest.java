package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

class TestExecutionListenerReceiptTest {
  @TempDir
  Path directory;

  @Test
  void givenTwoRealTestFailures_whenTheirReceiptsAreWritten_thenTheFirstCauseAndBothOriginalDiagnosticsRemain() throws Exception {
    // Arrange
    var listener = new TestExecutionListenerReceipt(directory);
    var summary = new SummaryGeneratingListener();
    var config = LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(false)
        .addTestExecutionListeners(listener, summary).build();
    var request = LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass(FailingCase.class)).build();

    // Act
    try (var session = LauncherFactory.openSession(config)) {
      session.getLauncher().execute(request);
    }
    var first = BuildFiles.read(directory.resolve("failure.properties"));

    // Assert
    assertEquals(2, summary.getSummary().getTestsFailedCount());
    assertTrue(first.getProperty("diagnostic").contains("First required value mismatch"));
    try (var files = Files.newDirectoryStream(directory, "failure-*.properties")) {
      var count = 0;
      var secondPreserved = false;
      for (var file : files) {
        count++;
        secondPreserved |= BuildFiles.read(file).getProperty("diagnostic").contains("Second required value mismatch");
      }
      assertEquals(2, count);
      assertTrue(secondPreserved);
    }
  }

  @TestMethodOrder(OrderAnnotation.class)
  static class FailingCase {
    @Test
    @Order(1)
    void givenFirstMismatch_whenCompared_thenTheOriginalAssertionFails() {
      // Arrange
      var expected = 7;
      // Act
      var actual = 11;
      // Assert
      assertEquals(expected, actual, "First required value mismatch");
    }

    @Test
    @Order(2)
    void givenSecondMismatch_whenCompared_thenItsIndependentAssertionAlsoFails() {
      // Arrange
      var expected = 13;
      // Act
      var actual = 17;
      // Assert
      assertEquals(expected, actual, "Second required value mismatch");
    }
  }
}
