package org.aquilagraph.cfg.internal;

import java.util.Objects;
import org.aquilagraph.cfg.api.FlowValue;

record YieldExit(String targetSwitchNodeId, String sourceEndpointId, FlowValue value) {
  YieldExit {
    Objects.requireNonNull(targetSwitchNodeId, "targetSwitchNodeId");
    Objects.requireNonNull(sourceEndpointId, "sourceEndpointId");
    Objects.requireNonNull(value, "value");
  }
}
