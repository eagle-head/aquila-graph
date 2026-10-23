package org.aquilagraph.cfg.internal;

import java.util.Objects;

record YieldExit(String targetSwitchNodeId, String sourceEndpointId, DraftValue value) {
  YieldExit {
    Objects.requireNonNull(targetSwitchNodeId, "targetSwitchNodeId");
    Objects.requireNonNull(sourceEndpointId, "sourceEndpointId");
    Objects.requireNonNull(value, "value");
  }
}
