package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;

/**
 * A policy encounter and its separate represented-support disposition.
 *
 * @param address
 *          exact captured source context
 * @param occurrence
 *          known policy occurrence, paired with its observation
 * @param observation
 *          known policy observation, paired with its occurrence
 * @param encounteredValue
 *          actual copied accessor state, when present
 * @param support
 *          represented-support disposition and its boundary-specific evidence
 * @param emissions
 *          creating-site diagnostic addresses associated with the context
 */
public record FeatureContextOutcome(SourceContext.Address address, Optional<LanguageFeatureOccurrence> occurrence,
    Optional<LanguageFeatureObservation> observation, Optional<CoveragePropertyValue> encounteredValue, Support support,
    List<DiagnosticEmission.Address> emissions) {
  /** Validates paired policy evidence without erasing pairs on support failure. */
  public FeatureContextOutcome {
    Objects.requireNonNull(address, "address");
    Objects.requireNonNull(occurrence, "occurrence");
    Objects.requireNonNull(observation, "observation");
    Objects.requireNonNull(encounteredValue, "encounteredValue");
    Objects.requireNonNull(support, "support");
    emissions = List.copyOf(Objects.requireNonNull(emissions, "emissions"));
    if (occurrence.isPresent() != observation.isPresent()
        || observation.filter(value -> !LanguageFeatureOccurrence.fromObservation(value).equals(occurrence.orElseThrow())).isPresent()
        || occurrence.filter(value -> !value.sourceFileKey().equals(address.sourceFileKey())).isPresent()) {
      throw new IllegalArgumentException("Context policy occurrence and observation must form one exact source-owned pair");
    }
  }

  /**
   * Explicit successful support or a separately diagnosed support failure.
   *
   * @param mode
   *          boundary-specific support disposition
   * @param facts
   *          exact retained positive facts in facts mode
   * @param policyContext
   *          same-owner policy context in policy-pair mode
   * @param sourceFile
   *          captured source in source-file mode
   * @param negativeState
   *          observed absent or empty accessor in negative mode
   */
  public record Support(Mode mode, List<CoverageFactReference> facts, Optional<SourceContext.Address> policyContext,
      Optional<SourceFile> sourceFile, Optional<CoveragePropertyValue> negativeState) {
    /** Validates support shape. */
    public Support {
      Objects.requireNonNull(mode, "mode");
      facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
      Objects.requireNonNull(policyContext, "policyContext");
      Objects.requireNonNull(sourceFile, "sourceFile");
      Objects.requireNonNull(negativeState, "negativeState");
      if ((mode == Mode.FACTS) != !facts.isEmpty() || (mode == Mode.POLICY_PAIR) != policyContext.isPresent()
          || (mode == Mode.SOURCE_FILE) != sourceFile.isPresent() || (mode == Mode.NEGATIVE) != negativeState.isPresent()) {
        throw new IllegalArgumentException("Support mode requires exactly its own evidence shape");
      }
      negativeState.ifPresent(value -> {
        if (value.state() != CoveragePropertyValue.State.ABSENT && value.state() != CoveragePropertyValue.State.EMPTY) {
          throw new IllegalArgumentException("Negative support requires observed ABSENT or EMPTY");
        }
      });
    }
  }

  /** Boundary-specific support tags. */
  public enum Mode {
    /** Diagnosed or irrelevant policy needs no represented fact. */
    NOT_REQUIRED,
    /** The attributed-kind observation is its represented fact. */
    ATTRIBUTED_PAIR,
    /** Exact same-owner tree-kind pair. */
    POLICY_PAIR,
    /** Captured source file. */
    SOURCE_FILE,
    /** Exact retained positive facts. */
    FACTS,
    /** Owner-checked successful absence or empty list. */
    NEGATIVE,
    /** Blocking support failure; known policy pair remains. */
    FAILED
  }
}
