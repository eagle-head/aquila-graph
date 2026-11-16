package org.aquilagraph.frontend.internal;

import java.util.Objects;
import org.aquilagraph.source.api.DiagnosticEmission;

/** One creating event and its complete frontend-local semantic subject. */
record DiagnosticEntry(DiagnosticEmission event, DiagnosticSubject subject) {
  DiagnosticEntry {
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(subject, "subject");
  }
}
