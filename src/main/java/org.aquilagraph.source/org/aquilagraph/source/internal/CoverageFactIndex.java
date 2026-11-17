package org.aquilagraph.source.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;

/** Input-local indexes over independently captured facts; queries preserve retained ordinal order. */
final class CoverageFactIndex {
  private static final SourceIndex EMPTY_SOURCE = new SourceIndex(List.of(), Map.of());
  private final Map<String, SourceIndex> sources;

  CoverageFactIndex(List<CoverageFactReference> references, Map<String, CoverageSyntaxFactValue> syntaxValues) {
    var entries = IntStream.range(0, references.size()).mapToObj(ordinal -> new Entry(ordinal, references.get(ordinal)))
        .collect(Collectors.groupingBy(entry -> entry.reference().sourceFileKey()));
    var scoped = new HashMap<String, SourceIndex>();
    entries.forEach((source, values) -> scoped.put(source, new SourceIndex(values, syntaxValues)));
    sources = Map.copyOf(scoped);
  }

  List<CoverageFactReference> inSource(String source) {
    return references(source(source).ordered);
  }

  List<CoverageFactReference> withIdentity(String source, String identity) {
    return references(source(source).identities.getOrDefault(identity, List.of()));
  }

  List<CoverageFactReference> withIdentities(String source, List<String> requested) {
    var selected = new TreeMap<Integer, Entry>();
    var scoped = source(source);
    requested.forEach(identity -> include(selected, scoped.identities.getOrDefault(identity, List.of())));
    return selected.values().stream().map(Entry::reference).toList();
  }

  List<CoverageFactReference> forDeclaration(String source, String identity) {
    return references(source(source).declarations.getOrDefault(identity, List.of()));
  }

  List<CoverageFactReference> atOwner(String source, SourceOwnerPath path) {
    return references(source(source).owners.getOrDefault(path, List.of()));
  }

  List<CoverageFactReference> atPath(String source, SyntaxStructuralPath path) {
    return references(source(source).structures.getOrDefault(path, List.of()));
  }

  int indexOf(CoverageFactReference claimed) {
    for (var entry : source(claimed.sourceFileKey()).identities.getOrDefault(claimed.identity(), List.of())) {
      if (entry.reference().equals(claimed)) {
        return entry.ordinal();
      }
    }
    return -1;
  }

  List<CoverageFactReference> ownedBy(SourceContext context) {
    return source(context.address().sourceFileKey()).ownedBy(context);
  }

  private SourceIndex source(String key) {
    return sources.getOrDefault(key, EMPTY_SOURCE);
  }

  private static final class SourceIndex {
    private final List<Entry> ordered;
    private final Map<String, List<Entry>> identities;
    private final Map<String, List<Entry>> declarations;
    private final Map<SourceOwnerPath, List<Entry>> owners;
    private final Map<SourceOwnerPath, List<Entry>> expressions;
    private final Map<SourceOwnerPath, Map<List<SourceSpan>, List<Entry>>> observations;
    private final Map<SyntaxStructuralPath, List<Entry>> structures;
    private final Map<Integer, List<String>> canonicalDeclarations;

    private SourceIndex(List<Entry> entries, Map<String, CoverageSyntaxFactValue> syntaxValues) {
      ordered = List.copyOf(entries);
      var identityEntries = new HashMap<String, List<Entry>>();
      var declarationEntries = new HashMap<String, List<Entry>>();
      var ownerEntries = new HashMap<SourceOwnerPath, List<Entry>>();
      var expressionEntries = new HashMap<SourceOwnerPath, List<Entry>>();
      var observationEntries = new HashMap<SourceOwnerPath, Map<List<SourceSpan>, List<Entry>>>();
      var structureEntries = new HashMap<SyntaxStructuralPath, List<Entry>>();
      var canonicalEntries = new HashMap<Integer, List<String>>();
      for (var entry : entries) {
        var reference = entry.reference();
        add(identityEntries, reference.identity(), entry);
        add(declarationEntries, reference.identity(), entry);
        var owner = switch (reference.value()) {
          case CoverageFactValue.MemberOrder order -> order.value().ownerTypeKey();
          case CoverageFactValue.DeclarationType type -> type.value().ownerTypeKey();
          default -> reference.identity();
        };
        if (!owner.equals(reference.identity())) {
          add(declarationEntries, owner, entry);
        }
        var syntax = reference.kind() == CoverageFactReference.Kind.SYNTAX ? syntaxValues.get(reference.identity()) : null;
        if (syntax != null) {
          canonicalEntries.put(entry.ordinal(), canonicalOwners(syntax.value()));
          add(structureEntries, syntax.structuralPath(), entry);
          syntax.sourceAssociation().ifPresent(association -> {
            add(ownerEntries, association.ownerPath(), entry);
            association.normalizedExpression().ifPresent(normalized -> {
              add(expressionEntries, normalized.region().compilerOwner(), entry);
              addObservations(entry, normalized, observationEntries);
            });
          });
        }
      }
      identities = freeze(identityEntries);
      declarations = freeze(declarationEntries);
      owners = freeze(ownerEntries);
      expressions = freeze(expressionEntries);
      observationEntries.replaceAll((owner, values) -> freeze(values));
      observations = Map.copyOf(observationEntries);
      structures = freeze(structureEntries);
      canonicalDeclarations = Map.copyOf(canonicalEntries);
    }

    private List<CoverageFactReference> ownedBy(SourceContext context) {
      var path = context.address().ownerPath();
      var selected = new TreeMap<Integer, Entry>();
      include(selected, owners.getOrDefault(path, List.of()));
      if (context.address().partition() == SourceContext.Partition.WRITTEN_EXPRESSION) {
        include(selected, expressions.getOrDefault(path, List.of()));
      } else {
        include(selected, observations.getOrDefault(path, Map.of()).getOrDefault(context.origins(), List.of()));
      }
      var keys = new HashSet<String>();
      selected.values().forEach(entry -> keys.addAll(canonicalDeclarations.getOrDefault(entry.ordinal(), List.of())));
      keys.forEach(key -> include(selected, declarations.getOrDefault(key, List.of())));
      return selected.values().stream().map(Entry::reference).toList();
    }
  }

  private static void addObservations(Entry entry, NormalizedExpressionCorrespondence normalized,
      Map<SourceOwnerPath, Map<List<SourceSpan>, List<Entry>>> selected) {
    if (!normalized.occurrencePath().isEmpty()) {
      return;
    }
    var operands = new HashSet<SourceOwnerPath>();
    normalized.region().occurrences().forEach(occurrence -> occurrence.compilerOperand().ifPresent(operands::add));
    var distinct = new HashMap<SourceOwnerPath, Set<List<SourceSpan>>>();
    normalized.observations().forEach(property -> {
      var origins = property.origin().stream().toList();
      if (!operands.contains(property.ownerPath())
          && distinct.computeIfAbsent(property.ownerPath(), ignored -> new HashSet<>()).add(origins)) {
        add(selected.computeIfAbsent(property.ownerPath(), ignored -> new HashMap<>()), origins, entry);
      }
    });
  }

  private static List<String> canonicalOwners(SyntaxValue value) {
    return switch (value) {
      case SyntaxValue.TypeDeclaration declaration -> List.of(declaration.declarationKey());
      case SyntaxValue.ExecutableDeclaration declaration -> List.of(declaration.declarationKey());
      case SyntaxValue.VariableDeclaration declaration -> List.of(declaration.declarationKey());
      case SyntaxValue.ParameterDeclaration declaration -> List.of(declaration.declarationKey());
      case SyntaxValue.RecordComponentDeclaration declaration ->
        List.of(declaration.declarationKey(), declaration.counterparts().backingFieldKey());
      default -> List.of();
    };
  }

  private static void include(Map<Integer, Entry> selected, List<Entry> entries) {
    entries.forEach(entry -> selected.put(entry.ordinal(), entry));
  }

  private static List<CoverageFactReference> references(List<Entry> entries) {
    return entries.stream().map(Entry::reference).toList();
  }

  private static <K> void add(Map<K, List<Entry>> entries, K key, Entry entry) {
    entries.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
  }

  private static <K> Map<K, List<Entry>> freeze(Map<K, List<Entry>> entries) {
    entries.replaceAll((key, values) -> List.copyOf(values));
    return Map.copyOf(entries);
  }

  private record Entry(int ordinal, CoverageFactReference reference) {
  }
}
