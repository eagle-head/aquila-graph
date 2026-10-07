package org.aquilagraph.source.api;

import java.io.Serial;
import java.util.Objects;

/** Intrinsic copied source values violate the Source Model admission contract. */
public final class AstContractException extends IllegalArgumentException {
  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a contract rejection preserving the actual invalid-value cause.
   *
   * @param description
   *          intrinsic invariant failure
   * @param cause
   *          original copied-value or decoding failure
   */
  public AstContractException(String description, Throwable cause) {
    super(Objects.requireNonNull(description, "description"), Objects.requireNonNull(cause, "cause"));
  }
}
