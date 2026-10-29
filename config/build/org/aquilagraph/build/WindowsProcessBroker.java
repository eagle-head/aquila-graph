package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Creates a recovery holder through the local provider, outside the requester's process tree. */
final class WindowsProcessBroker {
  private static final int INDEPENDENT_PROCESS_FLAGS = 0x01000410;
  private static final int IDENTITY_FIELD_COUNT = 2;
  private WindowsProcessBroker() {
  }

  static ProcessIdentity launch(BuildJournal journal, Path arguments, Path log, Path temporary) throws IOException {
    var root = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows")).toRealPath();
    var java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toRealPath();
    var script = """
        # Owned recovery holder creation @OBSERVER@ @RESERVATION@
        $ErrorActionPreference = 'Stop'
        function Decode([string] $value) { [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($value)) }
        $system = Decode '@SYSTEM@'
        $environment = @(
          ('SystemRoot=' + $system),
          ('SystemDrive=' + [IO.Path]::GetPathRoot($system).TrimEnd('\\')),
          ('TEMP=' + (Decode '@TEMP@')), ('TMP=' + (Decode '@TEMP@')),
          ('AQUILA_BUILD_KEEPER_JAVA=' + (Decode '@JAVA@')),
          ('AQUILA_BUILD_KEEPER_ARGUMENTS=' + (Decode '@ARGUMENTS@')),
          ('AQUILA_BUILD_KEEPER_LOG=' + (Decode '@LOG@'))
        )
        $startup = New-CimInstance -ClassName Win32_ProcessStartup -ClientOnly -Property @{
          ShowWindow = [uint16]0; CreateFlags = [uint32]@FLAGS@; EnvironmentVariables = [string[]]$environment
        }
        $command = '"' + [IO.Path]::Combine($system, 'System32\\cmd.exe') +
          '" /d /v:off /s /c "set "AQUILA_BUILD_INVOCATION=@INVOCATION@" & set "AQUILA_BUILD_KEEPER=@KEEPER@" & ' +
          '"%AQUILA_BUILD_KEEPER_JAVA%" "@%AQUILA_BUILD_KEEPER_ARGUMENTS%" >"%AQUILA_BUILD_KEEPER_LOG%" 2>&1"'
        $result = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
          CommandLine = $command; CurrentDirectory = (Decode '@DIRECTORY@'); ProcessStartupInformation = $startup
        } -OperationTimeoutSec 5
        if ($result.ReturnValue -ne 0) { throw ('Recovery holder creation failed with status ' + $result.ReturnValue) }
        $created = @(Get-CimInstance Win32_Process -Filter ('ProcessId=' + $result.ProcessId) -OperationTimeoutSec 5)
        if ($created.Count -ne 1 -or $null -eq $created[0].CreationDate) { throw 'Recovery holder exited before identity observation' }
        Write-Output ($created[0].ProcessId.ToString() + "`t" + $created[0].CreationDate.ToUniversalTime().ToString('o'))
        """.replace("@SYSTEM@", WindowsManagement.encode(root.toString())).replace("@JAVA@", WindowsManagement.encode(java.toString()))
        .replace("@ARGUMENTS@", WindowsManagement.encode(arguments.toString())).replace("@LOG@", WindowsManagement.encode(log.toString()))
        .replace("@TEMP@", WindowsManagement.encode(temporary.toString())).replace("@KEEPER@", journal.reservation())
        .replace("@FLAGS@", Integer.toString(INDEPENDENT_PROCESS_FLAGS)).replace("@INVOCATION@", journal.invocationContext().nonce())
        .replace("@DIRECTORY@", WindowsManagement.encode(journal.invocationContext().root().toString()));
    var result = creation(WindowsManagement.run(journal.invocationContext().properties(), script));
    var process = result.liveProcess().orElseThrow(() -> new IOException("Recovery holder exited before acknowledgement; log: " + log));
    journal.observe(process);
    return result;
  }

  static ProcessIdentity creation(String output) throws IOException {
    var fields = output.strip().split("\t", -1);
    if (fields.length != IDENTITY_FIELD_COUNT) {
      throw new IOException("Recovery holder creation requires one exact process identity");
    }
    try {
      var pid = Long.parseLong(fields[0]);
      if (pid <= 0 || pid > 0xffff_ffffL) {
        throw new IOException("Recovery holder process ID is out of range");
      }
      return new ProcessIdentity(pid, Instant.parse(fields[1]).truncatedTo(ChronoUnit.MILLIS));
    } catch (NumberFormatException | DateTimeParseException exception) {
      throw new IOException("Recovery holder creation returned an invalid process identity", exception);
    }
  }
}
