package org.aquilagraph.build;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Optional;

/** Native Windows process observations expose arguments that ProcessHandle may omit. */
final class WindowsProcessInventory {
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
        """.replace("@ACCOUNT@", WindowsManagement.encode(BuildFiles.required(intent, "account")))
        .replace("@IMAGES@", WindowsManagement.encode(BuildFiles.required(intent, "intended.images")))
        .replace("@NONCE@", WindowsManagement.encode(BuildFiles.required(intent, "nonce")))
        .replace("@TARGET@", WindowsManagement.encode(BuildFiles.required(intent, "reservation")));
    return admitted(WindowsManagement.run(intent, script));
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
    var output = WindowsManagement.run(intent, script).strip();
    if (ABSENT_IDENTITY.equals(output)) {
      return Optional.empty();
    }
    var fields = output.split("\t", -1);
    if (fields.length != IDENTITY_FIELDS || !Long.toString(pid).equals(fields[0])) {
      throw new IOException("Native Windows process identity observation is invalid: " + output);
    }
    return Optional.of(new ProcessIdentity(pid, Instant.parse(fields[1]).truncatedTo(ChronoUnit.MILLIS)));
  }

  static WindowsBootSession boot(Properties intent) throws IOException {
    var script = """
        # Owned host observation @OBSERVER@ @RESERVATION@
        $ErrorActionPreference = 'Stop'
        [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
        $entries = @(Get-CimInstance Win32_OperatingSystem -OperationTimeoutSec 5)
        if ($entries.Count -ne 1 -or [string]::IsNullOrWhiteSpace($entries[0].CSName) -or $null -eq $entries[0].LastBootUpTime) {
          throw 'One complete Windows boot observation is required'
        }
        Write-Output ($entries[0].CSName + "`t" + $entries[0].LastBootUpTime.ToUniversalTime().ToString('o'))
        """;
    return WindowsBootSession.parse(WindowsManagement.run(intent, script), WindowsKernel.instance().uptimeMillis());
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
