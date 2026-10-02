package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Independently queried semantic expectations frozen before emitted output.
 *
 * @param writtenInventory
 *          frozen source obligations that define the ordered semantic descriptors
 * @param projections
 *          one copied projection for each descriptor requiring attribution
 */
public record AttributedCoverageExpectations(SourceCoverageInventory writtenInventory, List<Projection> projections) {
  /** Requires exactly one ordered projection for each captured semantic descriptor. */
  public AttributedCoverageExpectations {
    Objects.requireNonNull(writtenInventory, "writtenInventory");
    projections = List.copyOf(Objects.requireNonNull(projections, "projections"));
    var descriptors = writtenInventory.contexts().stream().filter(SourceContext::requiresAttribution).map(SourceContext::address).toList();
    if (!descriptors.equals(projections.stream().map(Projection::address).toList())) {
      throw new IllegalArgumentException("Attributed projections must exactly match ordered written semantic descriptors");
    }
  }

  /**
   * One copied scalar/list projection or explicit unavailable query.
   *
   * @param address
   *          exact captured semantic descriptor
   * @param value
   *          copied accessor state and ordered values
   * @param ownerIdentity
   *          independently resolved canonical owner, when available
   */
  public record Projection(SourceContext.Address address, CoveragePropertyValue value, Optional<String> ownerIdentity) {
    /** Validates the copied projection. */
    public Projection {
      Objects.requireNonNull(address, "address");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(ownerIdentity, "ownerIdentity");
      if (address.partition() == SourceContext.Partition.ATTRIBUTED_PROPERTY && value.state() != CoveragePropertyValue.State.UNAVAILABLE
          && ownerIdentity.isEmpty()) {
        throw new IllegalArgumentException(
            "Available semantic accessor projections require an independently resolved exact owner identity");
      }
    }
  }
}
