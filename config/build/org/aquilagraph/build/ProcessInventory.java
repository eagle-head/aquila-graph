package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reconciles an unacknowledged reservation using observable same-account launch tokens. */
final class ProcessInventory {
  private ProcessInventory() {
  }

  static List<ProcessHandle> reservation(Path directory) throws IOException {
    var intent = BuildFiles.read(directory.resolve("intent.properties"));
    if (BuildRequest.windows()) {
      return WindowsProcessInventory.reservation(intent);
    }
    var account = BuildFiles.required(intent, "account");
    var reservation = intent.getProperty("match.reservation", BuildFiles.required(intent, "reservation"));
    var nonce = intent.getProperty("match.nonce", BuildFiles.required(intent, "nonce"));
    var images = List.of(BuildFiles.required(intent, "intended.images").toLowerCase(Locale.ROOT).split(",", -1));
    var matches = new ArrayList<ProcessHandle>();
    try (var processes = ProcessHandle.allProcesses()) {
      for (var process : processes.toList()) {
        var info = process.info();
        if (info.user().filter(account::equals).isPresent() && process.isAlive()) {
          var line = BuildRequest.linux() ? LinuxProcessInventory.commandLine(process) : info.commandLine();
          if (!BuildRequest.linux() && line.isEmpty() && process.isAlive()) {
            throw new IOException("Process arguments are unavailable for launch account process " + process.pid());
          }
          if (line.isEmpty() || !line.orElseThrow().contains(reservation) || !line.orElseThrow().contains(nonce)) {
            continue;
          }
          var command = info.command();
          if (command.isEmpty()) {
            throw new IOException("Process executable observation is unavailable for matching launch account process " + process.pid());
          }
          var image = Path.of(command.orElseThrow()).getFileName();
          if (image == null) {
            throw new IOException("A process executable observation has no filename: " + process.pid());
          }
          if (!images.contains(image.toString().toLowerCase(Locale.ROOT))) {
            continue;
          }
          matches.add(process);
        }
      }
    }
    return List.copyOf(matches);
  }
}
