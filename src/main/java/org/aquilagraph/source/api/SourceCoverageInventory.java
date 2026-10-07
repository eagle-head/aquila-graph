package org.aquilagraph.source.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
 */
public record SourceCoverageInventory(InputSnapshot snapshot, JavaRelease release, String contractIdentity, List<SourceContext> contexts) {
  /** Validates snapshot ownership, distinct addresses and partition order. */
  public SourceCoverageInventory {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(release, "release");
    Objects.requireNonNull(contractIdentity, "contractIdentity");
    contexts = List.copyOf(Objects.requireNonNull(contexts, "contexts"));
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
  }
}
