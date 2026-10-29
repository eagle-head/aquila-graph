package org.aquilagraph.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Owns bounded local Windows management calls and their native process records. */
final class WindowsManagement {
  private static final int MAX_RESULT_BYTES = 64 * 1024;

  private WindowsManagement() {
  }

  static String run(Properties intent, String script) throws IOException {
    var context = InvocationContext.observation(intent);
    var journal = BuildJournal.reserve(context, "process-observation");
    var executable = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32/WindowsPowerShell/v1.0/powershell.exe")
        .toRealPath();
    journal.recordEntry(executable);
    var ownedScript = script.replace("@OBSERVER@", context.nonce()).replace("@RESERVATION@", journal.reservation());
    var encoded = Base64.getEncoder().encodeToString(ownedScript.getBytes(StandardCharsets.UTF_16LE));
    var diagnostics = context.root().resolve("target/build-observation").resolve(context.nonce()).resolve(journal.reservation())
        .resolve("stderr.log");
    Files.createDirectories(context.root().resolve("target/build-observation").resolve(context.nonce()).resolve(journal.reservation()));
    var builder = new ProcessBuilder(executable.toString(), "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
        .redirectError(diagnostics.toFile());
    try (var observation = new NativeObservation(context, journal)) {
      var process = observation.start(builder);
      try {
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
          throw new IOException("Native Windows management operation exceeded 15 seconds");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Native Windows management operation was interrupted", exception);
      }
      var bytes = process.getInputStream().readNBytes(MAX_RESULT_BYTES + 1);
      if (bytes.length > MAX_RESULT_BYTES) {
        throw new IOException("Native Windows management operation exceeded its bounded result size");
      }
      var output = new String(bytes, StandardCharsets.UTF_8);
      var diagnosticText = Files.readString(diagnostics, StandardCharsets.UTF_8);
      if (process.exitValue() != 0) {
        throw new IOException("Native Windows management operation failed: " + output + diagnosticText);
      }
      PowerShellDiagnostics.requireProgressOnly(diagnosticText);
      return output;
    }
  }

  static String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

}
