package org.aquilagraph.frontend;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticEmission;

/** Transports creating-site diagnostic addresses through nested translator returns. */
final class TranslationDiagnosticEvidence extends AbstractList<AnalysisDiagnostic> implements DiagnosticEmissionTransport {
  private final String producer;
  private final String producingOwner;
  private final List<DiagnosticEmission> retainedEmissions;
  private final boolean frozen;
  private final Map<String, Integer> nextOrdinals = new HashMap<>();

  TranslationDiagnosticEvidence(String producer, String producingOwner) {
    this(producer, producingOwner, new ArrayList<>(), false);
  }

  static List<AnalysisDiagnostic> fromEvents(String producer, List<DiagnosticEmission> emissions) {
    return new TranslationDiagnosticEvidence(producer, "retained-events", new ArrayList<>(emissions), false);
  }

  private TranslationDiagnosticEvidence(String producer, String producingOwner, List<DiagnosticEmission> events, boolean frozen) {
    this.producer = Objects.requireNonNull(producer, "producer");
    this.producingOwner = Objects.requireNonNull(producingOwner, "producingOwner");
    this.retainedEmissions = events;
    this.frozen = frozen;
    events.stream().filter(event -> event.address().owner().equals(producingOwner))
        .forEach(event -> nextOrdinals.merge(event.address().producer(), event.address().ordinal() + 1, Math::max));
  }

  static List<AnalysisDiagnostic> copyOf(List<AnalysisDiagnostic> diagnostics) {
    if (diagnostics instanceof DiagnosticEmissionTransport transport) {
      return transport.immutableCopy();
    }
    if (!diagnostics.isEmpty()) {
      throw new IllegalArgumentException("Nonempty diagnostic transport requires creating-site emission addresses");
    }
    return List.of();
  }

  static List<DiagnosticEmission> events(List<AnalysisDiagnostic> diagnostics) {
    if (diagnostics instanceof DiagnosticEmissionTransport transport) {
      return transport.emissions();
    }
    if (!diagnostics.isEmpty()) {
      throw new IllegalArgumentException("Diagnostic events cannot be reconstructed from plain diagnostic values");
    }
    return List.of();
  }

  @Override
  public List<DiagnosticEmission> emissions() {
    return List.copyOf(retainedEmissions);
  }

  @Override
  public List<AnalysisDiagnostic> immutableCopy() {
    return new TranslationDiagnosticEvidence(producer, producingOwner, List.copyOf(retainedEmissions), true);
  }

  @Override
  public AnalysisDiagnostic get(int index) {
    return retainedEmissions.get(index).diagnostic();
  }

  @Override
  public int size() {
    return retainedEmissions.size();
  }

  @Override
  public boolean add(AnalysisDiagnostic diagnostic) {
    return emit(this, producer, diagnostic);
  }

  static boolean emit(List<AnalysisDiagnostic> diagnostics, String creatingSite, AnalysisDiagnostic diagnostic) {
    if (!(diagnostics instanceof DiagnosticEmissionTransport transport)) {
      return diagnostics.add(diagnostic);
    }
    return transport.emitDiagnostic(creatingSite, diagnostic);
  }

  @Override
  public boolean emitDiagnostic(String creatingSite, AnalysisDiagnostic diagnostic) {
    requireOpen();
    var ordinal = nextOrdinals.getOrDefault(creatingSite, 0);
    nextOrdinals.put(creatingSite, ordinal + 1);
    var address = new DiagnosticEmission.Address(creatingSite, producingOwner, ordinal);
    return retainedEmissions.add(new DiagnosticEmission(address, diagnostic, diagnostic.outcomeKind(), List.of(), -1));
  }

  @Override
  public boolean addAll(Collection<? extends AnalysisDiagnostic> diagnostics) {
    requireOpen();
    if (diagnostics instanceof DiagnosticEmissionTransport transport) {
      var changed = false;
      for (var event : transport.emissions()) {
        var retained = retainedEmissions.stream().filter(value -> value.address().equals(event.address())).findFirst();
        if (retained.isEmpty()) {
          retainedEmissions.add(event);
          if (event.address().owner().equals(producingOwner)) {
            nextOrdinals.merge(event.address().producer(), event.address().ordinal() + 1, Math::max);
          }
          changed = true;
        } else if (!retained.orElseThrow().equals(event)) {
          throw new IllegalArgumentException("One transported creating address cannot identify different diagnostic events");
        }
      }
      return changed;
    }
    if (!diagnostics.isEmpty()) {
      throw new IllegalArgumentException("Nested diagnostic lists must transport their original emission addresses");
    }
    return false;
  }

  @Override
  public void clear() {
    requireOpen();
    retainedEmissions.clear();
  }

  private void requireOpen() {
    if (frozen) {
      throw new UnsupportedOperationException("Diagnostic evidence is immutable after its producer returns");
    }
  }
}
