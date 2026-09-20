package org.example.calculator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CalculatorProcessTest {
  @Test
  void givenValidArguments_whenMainProcessRuns_thenProcessExitsSuccessfully() throws Exception {
    // Arrange
    var arguments = new String[] {"8", "/", "2"};

    // Act
    var result = execute(arguments);

    // Assert
    assertAll(
        () -> assertEquals(0, result.exitCode()),
        () -> assertEquals("4" + System.lineSeparator(), result.standardOutput()),
        () -> assertEquals("", result.standardError()));
  }

  @Test
  void givenZeroDivisor_whenMainProcessRuns_thenProcessExitsWithArithmeticError() throws Exception {
    // Arrange
    var arguments = new String[] {"8", "/", "0"};

    // Act
    var result = execute(arguments);

    // Assert
    assertAll(
        () -> assertEquals(1, result.exitCode()),
        () -> assertEquals("", result.standardOutput()),
        () ->
            assertEquals(
                "Error: Division by zero is not allowed." + System.lineSeparator(),
                result.standardError()));
  }

  @Test
  void givenUnsupportedOperator_whenMainProcessRuns_thenProcessExitsWithUsageError()
      throws Exception {
    // Arrange
    var arguments = new String[] {"8", "%", "2"};

    // Act
    var result = execute(arguments);

    // Assert
    assertAll(
        () -> assertEquals(2, result.exitCode()),
        () -> assertEquals("", result.standardOutput()),
        () ->
            assertEquals(
                "Error: Unsupported operator: %. Supported operators: +, -, *, /."
                    + System.lineSeparator()
                    + "Usage: calculator <left> <+|-|*|/> <right>"
                    + System.lineSeparator(),
                result.standardError()));
  }

  private static CommandResult execute(String... arguments) throws Exception {
    var javaExecutable =
        Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
            .toString();
    var applicationClassPath =
        Path.of(
                CalculatorApplication.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .toString();
    var command = new ArrayList<String>();
    Collections.addAll(
        command,
        javaExecutable,
        "-cp",
        applicationClassPath,
        CalculatorApplication.class.getName());
    Collections.addAll(command, arguments);

    var process = new ProcessBuilder(command).start();
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("Calculator process did not finish within 10 seconds.");
    }

    return new CommandResult(
        process.exitValue(),
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
        new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
  }

  private record CommandResult(int exitCode, String standardOutput, String standardError) {}
}
