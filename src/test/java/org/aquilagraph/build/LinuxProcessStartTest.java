package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxProcessStartTest {
  @TempDir Path directory;

  @Test
  void givenAProcessCommandContainingClosingParentheses_whenBirthIsObserved_thenTheExactBootAndStartTicksAreRetained() throws Exception {
    // Arrange
    write("00000000-0000-0000-0000-000000000001\n", status("worker ) name", "123456"));

    // Act
    var result = LinuxProcessStart.observe(directory, 42);

    // Assert
    assertEquals(new LinuxProcessStart("00000000-0000-0000-0000-000000000001", 123456), result);
  }

  @Test
  void givenMalformedOrForeignProcessStatus_whenBirthIsObserved_thenNoPartialIdentityIsAdmitted() throws Exception {
    // Arrange
    var invalid = List.of("42 worker S", "42 (worker) S 0", status("worker", "invalid"), status("worker", "-1"),
        status("worker", "123456").replaceFirst("42", "43"));

    // Act
    for (var value : invalid) {
      write("00000000-0000-0000-0000-000000000001", value);

      // Assert
      assertThrows(IOException.class, () -> LinuxProcessStart.observe(directory, 42));
    }
  }

  @Test
  void givenMissingOrEmptyBootEvidence_whenBirthIsObserved_thenTheObservationFailsClosed() throws Exception {
    // Arrange
    write("", status("worker", "123456"));

    // Act
    var empty = assertThrows(IOException.class, () -> LinuxProcessStart.observe(directory, 42));
    Files.delete(directory.resolve("sys/kernel/random/boot_id"));
    var missing = assertThrows(IOException.class, () -> LinuxProcessStart.observe(directory, 42));

    // Assert
    assertEquals("Linux process birth coordinates are malformed: 42", empty.getMessage());
    assertEquals(directory.resolve("sys/kernel/random/boot_id").toString(), assertInstanceOf(NoSuchFileException.class, missing).getFile());
  }

  private void write(String boot, String stat) throws IOException {
    Files.createDirectories(directory.resolve("sys/kernel/random"));
    Files.createDirectories(directory.resolve("42"));
    Files.writeString(directory.resolve("sys/kernel/random/boot_id"), boot, StandardCharsets.US_ASCII);
    Files.writeString(directory.resolve("42/stat"), stat, StandardCharsets.ISO_8859_1);
  }

  private static String status(String command, String ticks) {
    return "42 (" + command + ") S " + "0 ".repeat(18) + ticks + " 0\n";
  }
}
