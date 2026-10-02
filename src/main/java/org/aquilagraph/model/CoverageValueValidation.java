package org.aquilagraph.model;

import java.util.Objects;

/** Shared nonnull construction requirement for closed coverage fact and evidence values. */
final class CoverageValueValidation {
  private CoverageValueValidation() {
  }

  static <T> T requireValue(T value) {
    return Objects.requireNonNull(value, "value");
  }
}
