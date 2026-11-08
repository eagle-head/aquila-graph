package org.aquilagraph.build;

import java.io.IOException;

/** Fixed native reservation data for isolated Windows platform collaborations. */
final class NativeJournalFixtures {
  private NativeJournalFixtures() {}

  static BuildJournal windowsGroup(InvocationContext context, String kind) throws IOException {
    return BuildJournal.reserve(context, kind, intent -> intent.setProperty("containment.kind", "windows-job"));
  }
}
