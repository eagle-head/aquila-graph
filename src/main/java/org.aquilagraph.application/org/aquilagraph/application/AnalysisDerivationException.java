package org.aquilagraph.application;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Objects;
import org.aquilagraph.cfg.api.CfgDerivationException;
import org.aquilagraph.source.api.AstOutcome;

/** Abrupt declared CFG failure retaining the established source outcome and exact progress. */
public final class AnalysisDerivationException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  private final transient AstOutcome establishedSource;

  /**
   * Preserves the established source and declared CFG progress without normal completion.
   *
   * @param source
   *          exact already established source outcome
   * @param cause
   *          declared CFG derivation failure
   */
  public AnalysisDerivationException(AstOutcome source, CfgDerivationException cause) {
    super("Analysis derivation interrupted", Objects.requireNonNull(cause, "cause"));
    establishedSource = Objects.requireNonNull(source, "source");
    if (source.result().isEmpty() || !source.result().orElseThrow().identity().equals(cause.request().astIdentity())) {
      throw new IllegalArgumentException("Abrupt CFG progress requires its exact established AST result", cause);
    }
  }

  /**
   * Returns the identical source outcome established before derivation.
   *
   * @return retained source outcome
   */
  public AstOutcome ast() {
    return establishedSource;
  }

  /**
   * Returns the declared CFG request, completed progress, and effective cause.
   *
   * @return retained declared CFG failure
   */
  public CfgDerivationException derivationFailure() {
    return (CfgDerivationException) Objects.requireNonNull(getCause(), "derivation cause");
  }

  /**
   * Rejects restoration without an intrinsically admitted source owner.
   *
   * @param input
   *          native serialization stream
   * @throws InvalidObjectException
   *           always, because domain admission is required
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("Analysis derivation requires admitted source values");
  }
}
