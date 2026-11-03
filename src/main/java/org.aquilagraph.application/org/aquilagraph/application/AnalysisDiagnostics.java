package org.aquilagraph.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.aquilagraph.application.AnalysisDiagnosticOccurrence.Owner;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.SourceQualificationReason;

/** Canonical occurrence composition and exact native-cause correlations for an admitted application assessment. */
public final class AnalysisDiagnostics {
  private final List<AnalysisDiagnosticOccurrence> orderedOccurrences;
  private final Map<Object, List<AnalysisDiagnosticOccurrence>> associations;
  private final Map<AnalysisDiagnosticOccurrence, Integer> occurrencePositions;

  AnalysisDiagnostics(ProgramRepresentation representation, List<AnalysisDiagnostic> applicationDiagnostics,
      List<RequestQualificationReason> reasons) {
    var entries = new ArrayList<AnalysisDiagnosticOccurrence>();
    var relationships = new IdentityHashMap<Object, List<AnalysisDiagnosticOccurrence>>();
    var ast = representation.ast();
    var source = append(new Owner.Source(ast), ast.diagnostics().size(), entries);
    relationships.put(ast, source);
    representation.cfg().ifPresent(cfg -> composeCfg(representation, cfg, entries, relationships));
    var application = append(new Owner.Application(applicationDiagnostics), applicationDiagnostics.size(), entries);
    for (var reason : ast.qualification().reasons()) {
      relationships.put(reason, reason.diagnostics().stream().map(reference -> source.get(reference.position())).toList());
    }
    for (var reason : reasons) {
      var related = switch (reason) {
        case RequestQualificationReason.Source value -> require(relationships, value.outcome());
        case RequestQualificationReason.Selection value -> require(relationships, value.outcome());
        case RequestQualificationReason.Method value -> require(relationships, value.outcome());
        case RequestQualificationReason.MethodDiagnostic value -> List.of(require(relationships, value.outcome()).get(value.position()));
        case RequestQualificationReason.ApplicationDiagnostic value -> List.of(application.get(value.position()));
      };
      relationships.put(reason, related);
    }
    orderedOccurrences = List.copyOf(entries);
    associations = Collections.unmodifiableMap(relationships);
    var positions = new IdentityHashMap<AnalysisDiagnosticOccurrence, Integer>();
    for (var position = 0; position < orderedOccurrences.size(); position++) {
      positions.put(orderedOccurrences.get(position), position);
    }
    occurrencePositions = Collections.unmodifiableMap(positions);
  }

  /**
   * Returns source events, CFG events in canonical method order, then application events.
   *
   * @return immutable sequence retaining repeated equal diagnostic values
   */
  public List<AnalysisDiagnosticOccurrence> occurrences() {
    return orderedOccurrences;
  }

  /**
   * Projects native values from the canonical occurrence sequence.
   *
   * @return immutable ordered values without deduplication
   */
  public List<AnalysisDiagnostic> values() {
    return orderedOccurrences.stream().map(AnalysisDiagnosticOccurrence::diagnostic).toList();
  }

  /**
   * Resolves the canonical zero-based position of this assessment's occurrence.
   *
   * @param occurrence
   *          occurrence from this sequence
   * @return canonical position, independent of display numbering
   * @throws IllegalArgumentException
   *           if the occurrence belongs to another sequence
   */
  public int positionOf(AnalysisDiagnosticOccurrence occurrence) {
    Objects.requireNonNull(occurrence, "occurrence");
    var position = occurrencePositions.get(occurrence);
    if (position == null) {
      throw new IllegalArgumentException("Diagnostic occurrence belongs to another application assessment");
    }
    return position;
  }

  /**
   * Resolves native source event references; unassociated evidence remains outside this sequence.
   *
   * @param reason
   *          exact source reason retained by this assessment
   * @return related occurrences in the reason's native reference order
   * @throws IllegalArgumentException
   *           if the reason is not retained by this assessment
   */
  public List<AnalysisDiagnosticOccurrence> relatedTo(SourceQualificationReason reason) {
    return require(associations, reason);
  }

  /**
   * Resolves the native occurrences responsible for an application qualification cause.
   *
   * @param reason
   *          exact request reason retained by this assessment
   * @return related occurrences in native order, or canonical method order for a CFG context
   * @throws IllegalArgumentException
   *           if the reason is not retained by this assessment
   */
  public List<AnalysisDiagnosticOccurrence> relatedTo(RequestQualificationReason reason) {
    return require(associations, reason);
  }

  /**
   * Returns occurrences owned by the retained CFG context.
   *
   * @param outcome
   *          exact CFG outcome retained by this assessment
   * @return request events for rejection, otherwise method events in canonical order
   * @throws IllegalArgumentException
   *           if the outcome is not retained by this assessment
   */
  public List<AnalysisDiagnosticOccurrence> relatedTo(CfgOutcome outcome) {
    return require(associations, outcome);
  }

  /**
   * Returns occurrences owned by a retained method result.
   *
   * @param outcome
   *          exact selected method outcome
   * @return method occurrences in native diagnostic order
   * @throws IllegalArgumentException
   *           if the method is not retained by this assessment
   */
  public List<AnalysisDiagnosticOccurrence> relatedTo(MethodCfgOutcome outcome) {
    return require(associations, outcome);
  }

  /**
   * Resolves one exact blocker slot through the admitted method's native correlation.
   *
   * @param outcome
   *          exact selected method outcome
   * @param blockerPosition
   *          zero-based blocker position
   * @return the occurrence associated with that blocker slot
   * @throws IllegalArgumentException
   *           if the method is foreign or the blocker slot does not exist
   */
  public AnalysisDiagnosticOccurrence forBlocker(MethodCfgOutcome outcome, int blockerPosition) {
    var method = relatedTo(outcome);
    if (blockerPosition < 0 || blockerPosition >= outcome.blockers().size()) {
      throw new IllegalArgumentException("Diagnostic correlation requires an existing blocker slot");
    }
    return method.get(blockerPosition);
  }

  private static List<AnalysisDiagnosticOccurrence> append(Owner owner, int count, List<AnalysisDiagnosticOccurrence> entries) {
    var owned = IntStream.range(0, count).mapToObj(slot -> new AnalysisDiagnosticOccurrence(owner, slot)).toList();
    entries.addAll(owned);
    return owned;
  }

  private static void composeCfg(ProgramRepresentation representation, CfgOutcome cfg, List<AnalysisDiagnosticOccurrence> entries,
      Map<Object, List<AnalysisDiagnosticOccurrence>> relationships) {
    if (cfg.state() == CfgState.REJECTED) {
      relationships.put(cfg, append(new Owner.Request(cfg), cfg.diagnostics().size(), entries));
      return;
    }
    var methods = new HashMap<String, MethodCfgOutcome>();
    cfg.methods().forEach(method -> methods.put(method.methodKey(), method));
    var owners = representation.ast().result().orElseThrow().program().methods().stream()
        .map(declaration -> methods.get(declaration.methodKey())).filter(Objects::nonNull).map(Owner.Method::new).toList();
    if (owners.size() != methods.size()) {
      throw new IllegalArgumentException("CFG diagnostic owners require canonical source declarations");
    }
    var cfgOccurrences = owners.stream().map(owner -> {
      var owned = append(owner, owner.outcome().diagnostics().size(), entries);
      relationships.put(owner.outcome(), owned);
      return owned;
    }).flatMap(List::stream).toList();
    relationships.put(cfg, cfgOccurrences);
  }

  private static List<AnalysisDiagnosticOccurrence> require(Map<Object, List<AnalysisDiagnosticOccurrence>> relationships, Object owner) {
    var related = relationships.get(Objects.requireNonNull(owner, "owner"));
    if (related == null) {
      throw new IllegalArgumentException("Diagnostic owner or reason is not retained by this application assessment");
    }
    return related;
  }
}
