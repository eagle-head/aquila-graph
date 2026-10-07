package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;

/**
 * A diagnostic event addressed at its producer, independently of diagnostic value equality.
 *
 * @param address
 *          creating-site event address
 * @param diagnostic
 *          emitted diagnostic value
 * @param cause
 *          outcome cause matching the diagnostic
 * @param contexts
 *          captured source contexts associated with the event
 * @param slot
 *          final diagnostic list position, or minus one before assembly
 */
public record DiagnosticEmission(Address address, AnalysisDiagnostic diagnostic, AnalysisOutcomeKind cause,
    List<SourceContext.Address> contexts, int slot) {
  /** Validates event association; a negative slot denotes an event before final assembly. */
  public DiagnosticEmission {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(diagnostic, "diagnostic");
    Objects.requireNonNull(cause, "cause");
    contexts = List.copyOf(Objects.requireNonNull(contexts, "contexts"));
    if (slot < -1 || cause != diagnostic.outcomeKind()) {
      throw new IllegalArgumentException("Diagnostic event cause or slot is inconsistent");
    }
  }

  /**
   * Assigns the final ordered list slot without changing the creating-site address.
   *
   * @param position
   *          final diagnostic list position, or minus one before assembly
   * @return a copied event with the supplied slot
   * @throws IllegalArgumentException
   *           if the position is less than minus one
   */
  public DiagnosticEmission atSlot(int position) {
    return new DiagnosticEmission(address, diagnostic, cause, contexts, position);
  }

  /**
   * Input-local producer, owner and producer-local emission position.
   *
   * @param producer
   *          event producer identity
   * @param owner
   *          producer-local owner identity
   * @param ordinal
   *          nonnegative producer-local emission position
   */
  public record Address(String producer, String owner, int ordinal) {
    /** Validates the creating-site address. */
    public Address {
      Objects.requireNonNull(producer, "producer");
      Objects.requireNonNull(owner, "owner");
      if (producer.isBlank() || owner.isBlank() || ordinal < 0) {
        throw new IllegalArgumentException("Diagnostic event addresses require producer, owner and nonnegative ordinal");
      }
    }
  }
}
