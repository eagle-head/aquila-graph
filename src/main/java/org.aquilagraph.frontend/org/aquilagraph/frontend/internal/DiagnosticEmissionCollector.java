package org.aquilagraph.frontend.internal;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticEmission;

/** Owns creating-site allocation independently of the membership of each returned batch. */
final class DiagnosticEmissionCollector {
  private final String producer;
  private final String producingOwner;
  private final DiagnosticSubject subject;
  private final Map<DiagnosticEmission.Address, DiagnosticEntry> retained = new LinkedHashMap<>();
  private final Map<String, Long> nextOrdinals;

  DiagnosticEmissionCollector(String producer, String producingOwner, DiagnosticSubject subject) {
    this(producer, producingOwner, subject, new HashMap<>());
  }

  private DiagnosticEmissionCollector(String producer, String producingOwner, DiagnosticSubject subject, Map<String, Long> nextOrdinals) {
    this.producer = Objects.requireNonNull(producer, "producer");
    this.producingOwner = Objects.requireNonNull(producingOwner, "producingOwner");
    this.subject = Objects.requireNonNull(subject, "subject");
    this.nextOrdinals = Objects.requireNonNull(nextOrdinals, "nextOrdinals");
  }

  DiagnosticEmissionCollector newBatch() {
    return new DiagnosticEmissionCollector(producer, producingOwner, subject, nextOrdinals);
  }

  DiagnosticEmission emit(AnalysisDiagnostic diagnostic) {
    return emit(producer, diagnostic);
  }

  DiagnosticEmission emit(String creatingSite, AnalysisDiagnostic diagnostic) {
    var ordinal = nextOrdinals.getOrDefault(creatingSite, 0L);
    var address = new DiagnosticEmission.Address(creatingSite, producingOwner, Math.toIntExact(ordinal));
    var event = new DiagnosticEmission(address, diagnostic, diagnostic.outcomeKind(), List.of(), -1);
    nextOrdinals.put(creatingSite, ordinal + 1);
    retained.put(address, new DiagnosticEntry(event, subject));
    return event;
  }

  void include(DiagnosticEmissions emissions) {
    for (var entry : emissions.entries()) {
      var existing = retained.get(entry.event().address());
      if (existing != null && !existing.equals(entry)) {
        throw new IllegalArgumentException("One creating address cannot identify different diagnostic entries");
      }
    }
    for (var entry : emissions.entries()) {
      var event = entry.event();
      retained.putIfAbsent(event.address(), entry);
      if (event.address().owner().equals(producingOwner)) {
        nextOrdinals.merge(event.address().producer(), event.address().ordinal() + 1L, Math::max);
      }
    }
  }

  DiagnosticEmissions snapshot() {
    return new DiagnosticEmissions(List.copyOf(retained.values()));
  }

  void startBatch() {
    retained.clear();
  }

  int emissionCount() {
    return retained.size();
  }

  boolean isEmpty() {
    return retained.isEmpty();
  }

}
