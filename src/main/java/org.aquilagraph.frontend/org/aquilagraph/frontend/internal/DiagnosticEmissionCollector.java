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
  private final Map<DiagnosticEmission.Address, DiagnosticEmission> retained = new LinkedHashMap<>();
  private final Map<String, Long> nextOrdinals;

  DiagnosticEmissionCollector(String producer, String producingOwner) {
    this(producer, producingOwner, new HashMap<>());
  }

  private DiagnosticEmissionCollector(String producer, String producingOwner, Map<String, Long> nextOrdinals) {
    this.producer = Objects.requireNonNull(producer, "producer");
    this.producingOwner = Objects.requireNonNull(producingOwner, "producingOwner");
    this.nextOrdinals = Objects.requireNonNull(nextOrdinals, "nextOrdinals");
  }

  DiagnosticEmissionCollector newBatch() {
    return new DiagnosticEmissionCollector(producer, producingOwner, nextOrdinals);
  }

  DiagnosticEmission emit(AnalysisDiagnostic diagnostic) {
    return emit(producer, diagnostic);
  }

  DiagnosticEmission emit(String creatingSite, AnalysisDiagnostic diagnostic) {
    var ordinal = nextOrdinals.getOrDefault(creatingSite, 0L);
    var address = new DiagnosticEmission.Address(creatingSite, producingOwner, Math.toIntExact(ordinal));
    var event = new DiagnosticEmission(address, diagnostic, diagnostic.outcomeKind(), List.of(), -1);
    nextOrdinals.put(creatingSite, ordinal + 1);
    retained.put(address, event);
    return event;
  }

  void include(DiagnosticEmissions emissions) {
    for (var event : emissions.events()) {
      var existing = retained.get(event.address());
      if (existing != null && !existing.equals(event)) {
        throw new IllegalArgumentException("One creating address cannot identify different diagnostic events");
      }
    }
    for (var event : emissions.events()) {
      retained.putIfAbsent(event.address(), event);
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
