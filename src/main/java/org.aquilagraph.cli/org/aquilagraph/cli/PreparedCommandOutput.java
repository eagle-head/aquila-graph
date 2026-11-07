package org.aquilagraph.cli;

import java.util.Objects;

/** Both complete immutable payloads, prepared before any borrowed-channel operation. */
record PreparedCommandOutput(String output, String error) {
  PreparedCommandOutput {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");
  }
}
