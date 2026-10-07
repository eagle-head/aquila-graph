package org.aquilagraph.source.admission;

import java.util.Objects;
import org.aquilagraph.source.api.AstAggregateAdmission;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.JavaAstAggregate;

/** Atomic admission of copied source values; mutable construction never escapes. */
public final class AstValueAdmission {
  private AstValueAdmission() {
  }

  /**
   * Validates the complete copied source set before exposing an immutable aggregate.
   *
   * @param values
   *          complete copied source values
   * @return admitted immutable aggregate
   * @throws AstContractException
   *           if supplied source values violate an intrinsic invariant
   */
  public static JavaAstAggregate admit(CopiedAstValues values) {
    Objects.requireNonNull(values, "values");
    try {
      return AstAggregateAdmission.admit(values);
    } catch (IllegalArgumentException exception) {
      throw new AstContractException(Objects.toString(exception.getMessage(), "Invalid copied source values"), exception);
    }
  }
}
