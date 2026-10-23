package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Independent typed source evidence with its exact creating address.
 *
 * @param <T>
 *          copied source value type
 * @param state
 *          availability of this fact slot
 * @param knownValue
 *          present exactly for known evidence
 * @param origin
 *          exact local creating address
 * @param explanation
 *          nonblank explanation for a state without a value
 * @param diagnostic
 *          originating source error when one was actually emitted
 */
public record EvidenceValue<T>(EvidenceState state, Optional<T> knownValue, EvidenceOrigin origin, Optional<String> explanation,
    Optional<AnalysisDiagnostic> diagnostic) {
  /**
   * Creates supplied evidence without inventing a source diagnostic.
   *
   * @param state
   *          fact availability
   * @param knownValue
   *          selected value for known evidence
   * @param origin
   *          exact creating address
   * @param explanation
   *          reason for negative evidence
   */
  public EvidenceValue(EvidenceState state, Optional<T> knownValue, EvidenceOrigin origin, Optional<String> explanation) {
    this(state, knownValue, origin, explanation, Optional.empty());
  }

  /** Rejects contradictory state/value combinations and absent negative explanations. */
  public EvidenceValue {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(knownValue, "knownValue");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(explanation, "explanation");
    Objects.requireNonNull(diagnostic, "diagnostic");
    if ((state == EvidenceState.KNOWN) != knownValue.isPresent()) {
      throw new IllegalArgumentException("Known evidence alone must retain a value");
    }
    if ((state == EvidenceState.KNOWN) == explanation.isPresent() || explanation.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("Evidence without a value requires a nonblank explanation");
    }
    if (diagnostic.isPresent() && (state != EvidenceState.ERRONEOUS || !diagnostic.orElseThrow().isError()
        || diagnostic.orElseThrow().stage() != DiagnosticStage.FRONTEND)) {
      throw new IllegalArgumentException("An evidence diagnostic must be an originating source error for erroneous evidence");
    }
  }

  /**
   * Creates one independently known source fact.
   *
   * @param <T>
   *          copied value type
   * @param value
   *          copied known value
   * @param origin
   *          exact creating address
   * @return known evidence
   */
  public static <T> EvidenceValue<T> known(T value, EvidenceOrigin origin) {
    return new EvidenceValue<>(EvidenceState.KNOWN, Optional.of(value), origin, Optional.empty());
  }

  /**
   * Creates an applicable slot whose value could not be established.
   *
   * @param <T>
   *          expected copied value type
   * @param origin
   *          exact creating address
   * @param explanation
   *          reason the value is unavailable
   * @return missing evidence
   */
  public static <T> EvidenceValue<T> missing(EvidenceOrigin origin, String explanation) {
    return withoutValue(EvidenceState.MISSING, origin, explanation);
  }

  /**
   * Creates a slot whose attribution is erroneous.
   *
   * @param <T>
   *          expected copied value type
   * @param origin
   *          exact creating address
   * @param explanation
   *          attributed error explanation
   * @return erroneous evidence
   */
  public static <T> EvidenceValue<T> erroneous(EvidenceOrigin origin, String explanation) {
    return withoutValue(EvidenceState.ERRONEOUS, origin, explanation);
  }

  /**
   * Associates erroneous evidence with its already emitted source diagnostic.
   *
   * @param <T>
   *          expected copied value type
   * @param origin
   *          exact creating address
   * @param explanation
   *          reason this selected fact is erroneous
   * @param diagnostic
   *          original source error
   * @return erroneous evidence with its exact diagnostic
   */
  public static <T> EvidenceValue<T> erroneous(EvidenceOrigin origin, String explanation, AnalysisDiagnostic diagnostic) {
    return new EvidenceValue<>(EvidenceState.ERRONEOUS, Optional.empty(), origin, Optional.of(explanation), Optional.of(diagnostic));
  }

  /**
   * Creates a slot outside the applicable source context.
   *
   * @param <T>
   *          expected copied value type
   * @param origin
   *          exact creating address
   * @param explanation
   *          reason the slot does not apply
   * @return explicitly inapplicable evidence
   */
  public static <T> EvidenceValue<T> notApplicable(EvidenceOrigin origin, String explanation) {
    return withoutValue(EvidenceState.NOT_APPLICABLE, origin, explanation);
  }

  private static <T> EvidenceValue<T> withoutValue(EvidenceState state, EvidenceOrigin origin, String explanation) {
    return new EvidenceValue<>(state, Optional.empty(), origin, Optional.of(explanation));
  }
}
