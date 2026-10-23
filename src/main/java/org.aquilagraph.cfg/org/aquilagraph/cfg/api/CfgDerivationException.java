package org.aquilagraph.cfg.api;

import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;
import java.util.Objects;

/** Declared abrupt derivation with exact request and completed ordered progress. */
public final class CfgDerivationException extends RuntimeException {
  @Serial
  private static final long serialVersionUID = 1L;

  private final transient CfgRequest derivationRequest;
  private final transient List<MethodCfgOutcome> completedProgress;

  /**
   * Preserves completed progress and the actual effective construction cause.
   *
   * @param request
   *          exact original request
   * @param completed
   *          ordered outcomes completed before the failing method
   * @param cause
   *          original effective failure
   */
  public CfgDerivationException(CfgRequest request, List<MethodCfgOutcome> completed, Throwable cause) {
    super("Control-flow derivation interrupted", Objects.requireNonNull(cause, "cause"));
    derivationRequest = Objects.requireNonNull(request, "request");
    completedProgress = List.copyOf(Objects.requireNonNull(completed, "completed"));
    var keys = completedProgress.stream().map(MethodCfgOutcome::methodKey).toList();
    if (!request.hasUniqueMethodSelection() || keys.size() >= request.methodKeys().size()
        || !request.methodKeys().subList(0, keys.size()).equals(keys)
        || completedProgress.stream().anyMatch(value -> !value.isQualifiedFor(request))) {
      throw new IllegalArgumentException("Abrupt CFG progress must be an exact proper request prefix");
    }
  }

  /**
   * Returns the exact original request.
   *
   * @return retained derivation request
   */
  public CfgRequest request() {
    return derivationRequest;
  }

  /**
   * Returns completed outcomes without manufacturing an outcome for the failing method.
   *
   * @return immutable ordered completed progress
   */
  public List<MethodCfgOutcome> completedMethods() {
    return completedProgress;
  }

  /**
   * Rejects native serialization because progress requires domain value admission.
   *
   * @param output
   *          serialization stream
   * @throws NotSerializableException
   *           always, because native serialization is unsupported
   */
  @Serial
  private void writeObject(ObjectOutputStream output) throws NotSerializableException {
    Objects.requireNonNull(output, "output");
    throw new NotSerializableException("CFG progress uses domain value encoding");
  }

  /**
   * Rejects restoration without an admitted request and completed progress.
   *
   * @param input
   *          serialization stream
   * @throws InvalidObjectException
   *           always, because native restoration is unsupported
   */
  @Serial
  private void readObject(ObjectInputStream input) throws InvalidObjectException {
    Objects.requireNonNull(input, "input");
    throw new InvalidObjectException("CFG progress requires domain value admission");
  }
}
