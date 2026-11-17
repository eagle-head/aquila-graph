package org.aquilagraph.source.admission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.AttributedCoverageExpectations.Projection;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceContext.Partition;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.WrittenExpressionRegion;

/** Analysis-local lookup of frozen compiler obligations, separate from actual fact indexes. */
public final class CoverageContextIndex {
  private final Map<SourceContext.Address, SourceContext> addresses;
  private final Map<Partition, Map<String, Map<SourceOwnerPath, List<SourceContext>>>> positions;
  private final Map<SourceContext.Address, Projection> projections;
  private final Map<String, Map<SourceOwnerPath, String>> declarationOwners;
  private final Map<String, Map<SourceOwnerPath, String>> attributedOwners;
  private final Map<String, Map<SourceOwnerPath, WrittenExpressionRegion>> regions;
  private final Map<String, Map<SourceOwnerPath, List<SyntaxNode>>> normalizedRootCandidates;

  /**
   * Indexes immutable obligations and optional normalized root candidates for one input.
   * 
   * @param inventory
   *          independent written obligations
   * @param attributed
   *          independently captured projections
   * @param aggregate
   *          actual source aggregate, when root lookup is needed
   */
  public CoverageContextIndex(SourceCoverageInventory inventory, AttributedCoverageExpectations attributed,
      Optional<JavaAstAggregate> aggregate) {
    var addressEntries = new HashMap<SourceContext.Address, SourceContext>();
    var positionEntries = inventory.contexts().stream().collect(Collectors.groupingBy(context -> context.address().partition(), Collectors
        .groupingBy(context -> context.address().sourceFileKey(), Collectors.groupingBy(context -> context.address().ownerPath()))));
    for (var context : inventory.contexts()) {
      if (addressEntries.putIfAbsent(context.address(), context) != null) {
        throw new IllegalArgumentException("Independent coverage contexts require unique addresses");
      }

    }
    addresses = Map.copyOf(addressEntries);
    positionEntries.replaceAll((partition, sources) -> {
      sources.replaceAll((source, values) -> freeze(values));
      return Map.copyOf(sources);
    });
    positions = Map.copyOf(positionEntries);
    var projectionEntries = new HashMap<SourceContext.Address, Projection>();
    var ownerEntries = new HashMap<String, Map<SourceOwnerPath, String>>();
    var attributedEntries = new HashMap<String, Map<SourceOwnerPath, String>>();
    for (var projection : attributed.projections()) {
      if (projectionEntries.putIfAbsent(projection.address(), projection) != null) {
        throw new IllegalArgumentException("Independent coverage projections require unique addresses");
      }
      var source = projection.address().sourceFileKey();
      var path = projection.address().ownerPath();
      projection.ownerIdentity().ifPresent(identity -> addFirst(ownerEntries, source, path, identity));
      if (projection.address().partition() == Partition.ATTRIBUTED_PROPERTY) {
        projection.ownerIdentity().ifPresent(identity -> addFirst(attributedEntries, source, path, identity));
      }
    }
    projections = Map.copyOf(projectionEntries);
    declarationOwners = freezeSources(ownerEntries);
    attributedOwners = freezeSources(attributedEntries);
    var regionEntries = new HashMap<String, Map<SourceOwnerPath, WrittenExpressionRegion>>();
    inventory.writtenExpressions().stream().flatMap(List::stream)
        .forEach(region -> addFirst(regionEntries, region.origin().sourceFileKey(), region.compilerOwner(), region));
    regions = freezeSources(regionEntries);
    var roots = new HashMap<String, Map<SourceOwnerPath, List<SyntaxNode>>>();
    aggregate.ifPresent(value -> value.nodes()
        .forEach(node -> value.writtenSourceAssociation(node.nodeId()).flatMap(association -> association.normalizedExpression())
            .filter(normalized -> normalized.occurrencePath().isEmpty())
            .ifPresent(normalized -> roots.computeIfAbsent(node.sourceFileKey(), ignored -> new HashMap<>())
                .computeIfAbsent(normalized.region().compilerOwner(), ignored -> new ArrayList<>()).add(node))));
    roots.replaceAll((source, values) -> freeze(values));
    normalizedRootCandidates = Map.copyOf(roots);
  }

  /**
   * Finds the exact independent context.
   * 
   * @param address
   *          complete context address
   * @return matching context, if present
   */
  public Optional<SourceContext> context(SourceContext.Address address) {
    return Optional.ofNullable(addresses.get(address));
  }

  /**
   * Selects same-position contexts without concealing multiple obligations.
   * 
   * @param partition
   *          evidence partition
   * @param source
   *          exact source key
   * @param path
   *          exact owner path
   * @return immutable contexts in inventory order
   */
  public List<SourceContext> at(Partition partition, String source, SourceOwnerPath path) {
    return positions.getOrDefault(partition, Map.of()).getOrDefault(source, Map.of()).getOrDefault(path, List.of());
  }

  /**
   * Returns the first independently captured tree at an obligation's position.
   * 
   * @param context
   *          captured obligation
   * @return exact tree owner
   */
  public SourceContext tree(SourceContext context) {
    return at(Partition.TREE, context.address().sourceFileKey(), context.address().ownerPath()).stream().findFirst().orElseThrow();
  }

  /**
   * Walks typed parent paths from the current owner to the source root.
   * 
   * @param context
   *          captured obligation
   * @return tree contexts, nearest first and in inventory order at each depth
   */
  public List<SourceContext> ancestors(SourceContext context) {
    var result = new ArrayList<SourceContext>();
    var path = Optional.of(context.address().ownerPath());
    while (path.isPresent()) {
      result.addAll(at(Partition.TREE, context.address().sourceFileKey(), path.orElseThrow()));
      path = path.orElseThrow().parent();
    }
    return List.copyOf(result);
  }

  /**
   * Returns the independently captured projection at an exact address.
   * 
   * @param context
   *          captured obligation
   * @return exact projection
   */
  public Projection projection(SourceContext context) {
    return Optional.ofNullable(projections.get(context.address())).orElseThrow();
  }

  /**
   * Looks up an optional projection without inferring unavailable attribution.
   * 
   * @param address
   *          exact captured address
   * @return the independently supplied projection, if present
   */
  public Optional<Projection> findProjection(SourceContext.Address address) {
    return Optional.ofNullable(projections.get(address));
  }

  /**
   * Finds the first available owner identity across projection partitions.
   * 
   * @param context
   *          captured obligation
   * @return independent owner identity
   */
  public Optional<String> declarationOwner(SourceContext context) {
    return Optional
        .ofNullable(declarationOwners.getOrDefault(context.address().sourceFileKey(), Map.of()).get(context.address().ownerPath()));
  }

  /**
   * Finds the first owner identity captured for a semantic declaration property.
   * 
   * @param context
   *          captured obligation
   * @return independent attributed declaration identity
   */
  public Optional<String> attributedDeclarationOwner(SourceContext context) {
    return Optional
        .ofNullable(attributedOwners.getOrDefault(context.address().sourceFileKey(), Map.of()).get(context.address().ownerPath()));
  }

  /**
   * Returns the written expression region at an exact source owner.
   * 
   * @param context
   *          captured obligation
   * @return independent written region
   */
  public WrittenExpressionRegion region(SourceContext context) {
    return Optional.ofNullable(regions.getOrDefault(context.address().sourceFileKey(), Map.of()).get(context.address().ownerPath()))
        .orElseThrow();
  }

  /**
   * Selects root candidates without certifying their complete region correspondence.
   * 
   * @param context
   *          captured obligation
   * @return immutable actual candidates in aggregate order
   */
  public List<SyntaxNode> normalizedRoots(SourceContext context) {
    return normalizedRootCandidates.getOrDefault(context.address().sourceFileKey(), Map.of()).getOrDefault(context.address().ownerPath(),
        List.of());
  }

  private static <V> void addFirst(Map<String, Map<SourceOwnerPath, V>> values, String source, SourceOwnerPath path, V value) {
    values.computeIfAbsent(source, ignored -> new HashMap<>()).putIfAbsent(path, value);
  }

  private static <V> Map<String, Map<SourceOwnerPath, V>> freezeSources(Map<String, Map<SourceOwnerPath, V>> values) {
    values.replaceAll((source, entries) -> Map.copyOf(entries));
    return Map.copyOf(values);
  }

  private static <K, V> Map<K, List<V>> freeze(Map<K, List<V>> entries) {
    entries.replaceAll((key, values) -> List.copyOf(values));
    return Map.copyOf(entries);
  }

}
