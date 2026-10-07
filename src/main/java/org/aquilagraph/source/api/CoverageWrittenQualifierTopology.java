package org.aquilagraph.source.api;

/** Independent parsed topology identifying written member qualifiers within executable source. */
public final class CoverageWrittenQualifierTopology {
  private CoverageWrittenQualifierTopology() {
  }

  /**
   * Determines whether a parsed member qualifier requires its own retained source occurrence.
   *
   * @param context
   *          independently captured parsed context
   * @param inventory
   *          frozen written source inventory
   * @return whether ancestor support would omit a required written qualifier
   */
  public static boolean requiresExactOccurrence(SourceContext context, SourceCoverageInventory inventory) {
    if (context.address().partition() != SourceContext.Partition.TREE
        || context.feature().filter(feature -> "IDENTIFIER".equals(feature.identifier()) || "MEMBER_SELECT".equals(feature.identifier()))
            .isEmpty()
        || context.address().ownerPath().lastStep().filter(step -> step.role() == SourceOwnerPath.Role.EXPRESSION).isEmpty()
        || context.address().ownerPath().steps().stream()
            .noneMatch(step -> step.role() == SourceOwnerPath.Role.BODY || step.role() == SourceOwnerPath.Role.STATEMENT)) {
      return false;
    }
    var parent = context.address().ownerPath().parent().orElseThrow();
    return inventory.contexts().stream().anyMatch(candidate -> candidate.address().partition() == SourceContext.Partition.TREE
        && candidate.address().sourceFileKey().equals(context.address().sourceFileKey()) && candidate.address().ownerPath().equals(parent)
        && candidate.feature().filter(feature -> "MEMBER_SELECT".equals(feature.identifier())).isPresent());
  }
}
