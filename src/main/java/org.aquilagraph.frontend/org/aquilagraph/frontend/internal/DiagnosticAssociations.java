package org.aquilagraph.frontend.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.SourceContext;

/** Owns invocation-local event registration, context associations, and final diagnostic slots. */
final class DiagnosticAssociations {
  private final Map<DiagnosticEmission.Address, Registration> registered = new LinkedHashMap<>();
  private final Map<DiagnosticIdentity, DiagnosticEmission.Address> instances = new HashMap<>();

  DiagnosticAssociations(DiagnosticEmissions creating) {
    for (var entry : creating.entries()) {
      register(entry, true);
    }
  }

  DiagnosticEmission.Address associateKnown(AnalysisDiagnostic diagnostic, SourceContext.Address context) {
    var address = instances.get(new DiagnosticIdentity(diagnostic));
    if (address == null) {
      throw new IllegalArgumentException("Known policy diagnostics require creating-site event transport");
    }
    associate(address, context);
    return address;
  }

  Optional<DiagnosticEmission.Address> associateFirst(Predicate<DiagnosticEntry> explains, SourceContext.Address context) {
    for (var registration : registered.values()) {
      if (registration.creating && explains.test(registration.entry)) {
        var address = registration.entry.event().address();
        associate(address, context);
        return Optional.of(address);
      }
    }
    return Optional.empty();
  }

  DiagnosticEmission.Address createFailure(AnalysisDiagnostic diagnostic, SourceContext.Address context) {
    var address = new DiagnosticEmission.Address("JavaSourceCoverageReconciler", context.toString(), registered.size());
    var event = new DiagnosticEmission(address, diagnostic, diagnostic.outcomeKind(), List.of(context), -1);
    register(new DiagnosticEntry(event, DiagnosticSubject.None.INSTANCE), false);
    return address;
  }

  Finalized finish() {
    var events = new ArrayList<DiagnosticEmission>();
    for (var registration : registered.values()) {
      var event = registration.entry.event();
      events.add(new DiagnosticEmission(event.address(), event.diagnostic(), event.cause(), registration.contexts, events.size()));
    }
    return new Finalized(events);
  }

  private void register(DiagnosticEntry entry, boolean creating) {
    var event = entry.event();
    var existing = registered.get(event.address());
    if (existing != null) {
      if (!existing.entry.equals(entry)) {
        throw new IllegalArgumentException("One creating address cannot identify different diagnostic entries");
      }
      return;
    }
    var identity = new DiagnosticIdentity(event.diagnostic());
    if (instances.containsKey(identity)) {
      throw new IllegalArgumentException("One diagnostic instance cannot be reminted as a second emission");
    }
    registered.put(event.address(), new Registration(entry, creating));
    instances.put(identity, event.address());
  }

  private void associate(DiagnosticEmission.Address event, SourceContext.Address context) {
    var registration = Objects.requireNonNull(registered.get(event), "registered diagnostic event");
    if (!registration.contexts.contains(context)) {
      registration.contexts.add(context);
    }
  }

  private static final class Registration {
    private final DiagnosticEntry entry;
    private final boolean creating;
    private final List<SourceContext.Address> contexts;

    private Registration(DiagnosticEntry entry, boolean creating) {
      this.entry = entry;
      this.creating = creating;
      contexts = new ArrayList<>(entry.event().contexts());
    }
  }

  private record DiagnosticIdentity(AnalysisDiagnostic diagnostic) {
    @Override
    public boolean equals(Object other) {
      return other instanceof DiagnosticIdentity identity && JavaProjectionIdentity.sameInstance(diagnostic, identity.diagnostic());
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(diagnostic);
    }
  }

  record Finalized(List<DiagnosticEmission> events) {
    Finalized {
      events = List.copyOf(events);
    }

    List<AnalysisDiagnostic> diagnostics() {
      return events.stream().map(DiagnosticEmission::diagnostic).toList();
    }
  }
}
