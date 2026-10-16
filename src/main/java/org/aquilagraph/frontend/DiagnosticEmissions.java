package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticEmission;

/** Immutable, ordered creating-site events returned by a translation operation. */
record DiagnosticEmissions(List<DiagnosticEmission> events) {
  DiagnosticEmissions {
    Objects.requireNonNull(events, "events");
    var retained = new LinkedHashMap<DiagnosticEmission.Address, DiagnosticEmission>();
    for (var event : events) {
      var existing = retained.putIfAbsent(event.address(), event);
      if (existing != null && !existing.equals(event)) {
        throw new IllegalArgumentException("One creating address cannot identify different diagnostic events");
      }
    }
    events = List.copyOf(retained.values());
  }

  static DiagnosticEmissions empty() {
    return new DiagnosticEmissions(List.of());
  }

  static DiagnosticEmissions combine(DiagnosticEmissions... groups) {
    var events = new ArrayList<DiagnosticEmission>();
    for (var group : groups) {
      events.addAll(group.events());
    }
    return new DiagnosticEmissions(events);
  }

  List<AnalysisDiagnostic> diagnostics() {
    return events.stream().map(DiagnosticEmission::diagnostic).toList();
  }
}
