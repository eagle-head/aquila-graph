package org.aquilagraph.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Native Windows process observations expose arguments that ProcessHandle may omit. */
final class WindowsProcessInventory {
  private static final int MAX_RESULT_BYTES = 64 * 1024;
  private static final int IDENTITY_FIELDS = 2;
  private static final String ABSENT_IDENTITY = "absent";

  private WindowsProcessInventory() {
  }

  static List<ProcessHandle> reservation(Properties intent) throws IOException {
    var script = """
        # Owned process observation @OBSERVER@ @RESERVATION@
        $ErrorActionPreference = 'Stop'
        function Decode([string] $value) { [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($value)) }
        $account = Decode '@ACCOUNT@'
        $images = (Decode '@IMAGES@').Split(',')
        $first = Decode '@NONCE@'
        $second = Decode '@TARGET@'
        function StillPresent($entry) {
          $current = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $entry.ProcessId) -OperationTimeoutSec 5
          return ($null -ne $current -and $current.CreationDate -eq $entry.CreationDate)
        }
        foreach ($entry in Get-CimInstance Win32_Process -OperationTimeoutSec 5) {
          if ($images -notcontains $entry.Name.ToLowerInvariant()) { continue }
          $searchable = $entry.CommandLine
          if ($entry.Name -in @('powershell.exe', 'pwsh.exe') -and $searchable -match '(?i)-EncodedCommand\\s+([A-Za-z0-9+/=]+)') {
            $searchable += [Text.Encoding]::Unicode.GetString([Convert]::FromBase64String($Matches[1]))
          }
          if (-not [string]::IsNullOrEmpty($searchable) -and
              (-not $searchable.Contains($first) -or -not $searchable.Contains($second))) { continue }
          try {
            $owner = Invoke-CimMethod -InputObject $entry -MethodName GetOwner -OperationTimeoutSec 5
          } catch [Microsoft.Management.Infrastructure.CimException] {
            if (-not (StillPresent $entry)) { continue }
            throw
          }
          if ($owner.ReturnValue -ne 0) {
            if (-not (StillPresent $entry)) { continue }
            throw ('Process account observation unavailable: ' + $entry.ProcessId)
          }
          $qualified = $owner.Domain + '\\' + $owner.User
          if ($account -ine $qualified -and $account -ine $owner.User) { continue }
          if ([string]::IsNullOrEmpty($searchable)) { throw ('Process arguments unavailable: ' + $entry.ProcessId) }
          if ($searchable.Contains($first) -and $searchable.Contains($second)) {
            Write-Output ($entry.ProcessId.ToString() + "`t" + $entry.CreationDate.ToUniversalTime().ToString('o'))
          }
        }
        """.replace("@ACCOUNT@", encode(BuildFiles.required(intent, "account")))
        .replace("@IMAGES@", encode(BuildFiles.required(intent, "intended.images")))
        .replace("@NONCE@", encode(BuildFiles.required(intent, "nonce")))
        .replace("@TARGET@", encode(BuildFiles.required(intent, "reservation")));
    return admitted(observe(intent, script));
  }

  static Optional<ProcessIdentity> identity(Properties intent, long pid) throws IOException {
    if (pid <= 0 || pid > 0xffff_ffffL) {
      throw new IOException("Windows process identity is out of range: " + pid);
    }
    var script = """
        # Owned process observation @OBSERVER@ @RESERVATION@
        $ErrorActionPreference = 'Stop'
        $entries = @(Get-CimInstance Win32_Process -Filter 'ProcessId=@PID@' -OperationTimeoutSec 5)
        if ($entries.Count -eq 0) { Write-Output '@ABSENT@'; exit 0 }
        if ($entries.Count -ne 1 -or $null -eq $entries[0].CreationDate) { throw 'One complete process identity is required' }
        Write-Output ($entries[0].ProcessId.ToString() + "`t" + $entries[0].CreationDate.ToUniversalTime().ToString('o'))
        """.replace("@PID@", Long.toString(pid)).replace("@ABSENT@", ABSENT_IDENTITY);
    var output = observe(intent, script).strip();
    if (ABSENT_IDENTITY.equals(output)) {
      return Optional.empty();
    }
    var fields = output.split("\t", -1);
    if (fields.length != IDENTITY_FIELDS || !Long.toString(pid).equals(fields[0])) {
      throw new IOException("Native Windows process identity observation is invalid: " + output);
    }
    return Optional.of(new ProcessIdentity(pid, Instant.parse(fields[1]).truncatedTo(ChronoUnit.MILLIS)));
  }

  private static String observe(Properties intent, String script) throws IOException {
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
          throw new IOException("Native Windows process inventory exceeded 15 seconds");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Native Windows process inventory was interrupted", exception);
      }
      var bytes = process.getInputStream().readNBytes(MAX_RESULT_BYTES + 1);
      if (bytes.length > MAX_RESULT_BYTES) {
        throw new IOException("Native Windows process inventory exceeded its bounded result size");
      }
      var output = new String(bytes, StandardCharsets.UTF_8);
      var diagnosticText = Files.readString(diagnostics, StandardCharsets.UTF_8);
      if (process.exitValue() != 0) {
        throw new IOException("Native Windows process inventory failed: " + output + diagnosticText);
      }
      PowerShellDiagnostics.requireProgressOnly(diagnosticText);
      return output;
    }
  }

  private static String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static List<ProcessHandle> admitted(String output) throws IOException {
    var result = new ArrayList<ProcessHandle>();
    for (var line : output.lines().filter(value -> !value.isBlank()).toList()) {
      var fields = line.split("\t", -1);
      if (fields.length != IDENTITY_FIELDS) {
        throw new IOException("Native Windows process inventory returned an invalid identity record: " + output);
      }
      var process = ProcessHandle.of(Long.parseLong(fields[0]));
      if (process.isPresent() && process.orElseThrow().isAlive()) {
        var observed = ProcessIdentity.observeAvailable(process.orElseThrow());
        if (observed.isEmpty()) {
          continue;
        }
        var identity = observed.orElseThrow();
        if (!identity.started().truncatedTo(ChronoUnit.MILLIS).equals(Instant.parse(fields[1]).truncatedTo(ChronoUnit.MILLIS))) {
          throw new IOException("Native and Java process start observations differ for " + identity.pid());
        }
        result.add(process.orElseThrow());
      }
    }
    return List.copyOf(result);
  }

}
