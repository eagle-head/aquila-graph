package org.aquilagraph.source.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;

/**
 * Written obligations frozen before attribution and translation output.
 *
 * @param snapshot
 *          immutable captured source input
 * @param release
 *          active Java release
 * @param contractIdentity
 *          active language-feature coverage contract identity
 * @param contexts
 *          distinct snapshot-owned contexts in evidence partition order
 * @param writtenExpressions
 *          independent grammar regions, absent only for historical inventories
 */
public record SourceCoverageInventory(InputSnapshot snapshot, JavaRelease release, String contractIdentity, List<SourceContext> contexts,
    Optional<List<WrittenExpressionRegion>> writtenExpressions) {
  /**
   * Creates an inventory without a written-expression proof requirement.
   *
   * @param snapshot
   *          captured source
   * @param release
   *          language release
   * @param contractIdentity
   *          coverage contract
   * @param contexts
   *          captured contexts
   */
  public SourceCoverageInventory(InputSnapshot snapshot, JavaRelease release, String contractIdentity, List<SourceContext> contexts) {
    this(snapshot, release, contractIdentity, contexts, Optional.empty());
  }
  /** Validates snapshot ownership, distinct addresses and partition order. */
  public SourceCoverageInventory {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(release, "release");
    Objects.requireNonNull(contractIdentity, "contractIdentity");
    contexts = List.copyOf(Objects.requireNonNull(contexts, "contexts"));
    writtenExpressions = Objects.requireNonNull(writtenExpressions, "writtenExpressions").map(List::copyOf);
    var sourceKeys = new HashSet<String>();
    snapshot.sourceFiles().forEach(source -> sourceKeys.add(source.sourceFileKey()));
    var addresses = new HashSet<SourceContext.Address>();
    var partition = SourceContext.Partition.TREE;
    for (var context : contexts) {
      if (!sourceKeys.contains(context.address().sourceFileKey()) || !addresses.add(context.address())
          || context.address().partition().compareTo(partition) < 0) {
        throw new IllegalArgumentException("Written inventory requires unique ordered snapshot-owned contexts");
      }
      partition = context.address().partition();
    }
    for (var region : writtenExpressions.stream().flatMap(List::stream).toList()) {
      var source = snapshot.sourceFiles().stream().filter(file -> file.sourceFileKey().equals(region.origin().sourceFileKey())).findFirst()
          .orElse(null);
      if (source == null || region.origin().endOffsetExclusive() > source.content().length()
          || contexts.stream()
              .filter(context -> context.address().partition() == SourceContext.Partition.WRITTEN_EXPRESSION
                  && context.address().sourceFileKey().equals(region.origin().sourceFileKey())
                  && context.address().ownerPath().equals(region.compilerOwner()) && context.origins().equals(List.of(region.origin())))
              .count() != 1) {
        throw new IllegalArgumentException("Every written expression region requires its exact independent source obligation");
      }
    }
  }
}
