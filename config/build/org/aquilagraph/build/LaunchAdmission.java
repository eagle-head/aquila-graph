package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;

/** A mutation permit belongs to one acknowledged live ancestor under the root lease. */
final class LaunchAdmission {
  private LaunchAdmission() {
  }

  private static IOException missingAncestor() {
    return new IOException("The command is not a descendant of its acknowledged source launcher");
  }

  static BuildJournal require(InvocationContext context, String reservation) throws IOException {
    BuildLease.requireHeld(context);
    var journal = BuildJournal.open(context, reservation);
    if (!Files.exists(journal.directory().resolve("permitted.properties"))) {
      throw new IOException("The source launcher does not have the owner's mutation permit");
    }
    var record = BuildFiles.read(journal.directory().resolve("acknowledged.properties"));
    var launcher = new ProcessIdentity(Long.parseLong(BuildFiles.required(record, "pid")),
        Instant.parse(BuildFiles.required(record, "started")));
    if (launcher.liveProcess().isEmpty()) {
      throw new IOException("The acknowledged source launcher is no longer alive");
    }
    var ancestor = ProcessHandle.current();
    while (ancestor.pid() != launcher.pid()) {
      var parent = ancestor.parent();
      if (parent.isEmpty()) {
        throw missingAncestor();
      }
      ancestor = parent.orElseThrow();
    }
    if (!launcher.equals(ProcessIdentity.observe(ancestor))) {
      throw new IOException("The source launcher start time differs from its acknowledgement");
    }
    return journal;
  }
}
