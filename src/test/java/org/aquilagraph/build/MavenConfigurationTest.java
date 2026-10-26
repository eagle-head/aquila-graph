package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenConfigurationTest {
  @TempDir
  Path directory;

  @Test
  void givenSupportedAndHiddenSettingsProperties_whenConfigurationIsAdmitted_thenUnknownOverridesFailWithoutTheirValues() throws Exception {
    // Arrange
    var supported = directory.resolve("supported.xml");
    var hidden = directory.resolve("hidden.xml");
    Files.writeString(supported, """
        <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"><profiles><profile><properties>
        <test.unit.forks>2</test.unit.forks></properties></profile></profiles></settings>
        """);
    Files.writeString(hidden, """
        <s:settings xmlns:s="http://maven.apache.org/SETTINGS/1.0.0"><s:profiles><s:profile><s:properties>
        <s:exec.skip>synthetic-private-value</s:exec.skip></s:properties></s:profile></s:profiles></s:settings>
        """);

    // Act
    MavenConfiguration.validateSettings(supported, BuildOperation.TEST);
    var failure = assertThrows(IllegalArgumentException.class, () -> MavenConfiguration.validateSettings(hidden, BuildOperation.COMPILE));

    // Assert
    assertTrue(failure.getMessage().contains("exec.skip"));
    assertFalse(failure.getMessage().contains("synthetic-private-value"));
  }
}
