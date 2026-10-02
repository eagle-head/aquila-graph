package org.aquilagraph.core;

import java.util.Objects;
import org.aquilagraph.model.FlowValue;

record YieldExit(String targetSwitchNodeId, String sourceEndpointId, FlowValue value) {
  YieldExit {
    Objects.requireNonNull(targetSwitchNodeId, "targetSwitchNodeId");
    Objects.requireNonNull(sourceEndpointId, "sourceEndpointId");
    Objects.requireNonNull(value, "value");
  }
}
