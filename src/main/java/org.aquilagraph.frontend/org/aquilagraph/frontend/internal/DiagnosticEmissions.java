package org.aquilagraph.frontend.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticEmission;

/** Immutable, ordered creating-site entries returned by a translation operation. */
record DiagnosticEmissions(List<DiagnosticEntry> entries) {
  DiagnosticEmissions {
    Objects.requireNonNull(entries, "entries");
    var retained = new LinkedHashMap<DiagnosticEmission.Address, DiagnosticEntry>();
    for (var entry : entries) {
      var existing = retained.putIfAbsent(entry.event().address(), entry);
      if (existing != null && !existing.equals(entry)) {
        throw new IllegalArgumentException("One creating address cannot identify different diagnostic entries");
      }
    }
    entries = List.copyOf(retained.values());
  }

  static DiagnosticEmissions empty() {
    return new DiagnosticEmissions(List.of());
  }

  static DiagnosticEmissions combine(DiagnosticEmissions... groups) {
    var entries = new ArrayList<DiagnosticEntry>();
    for (var group : groups) {
      entries.addAll(group.entries());
    }
    return new DiagnosticEmissions(entries);
  }

  List<DiagnosticEmission> events() {
    return entries.stream().map(DiagnosticEntry::event).toList();
  }

  List<AnalysisDiagnostic> diagnostics() {
    return entries.stream().map(entry -> entry.event().diagnostic()).toList();
  }
}
