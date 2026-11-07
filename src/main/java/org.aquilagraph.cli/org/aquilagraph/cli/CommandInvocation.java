package org.aquilagraph.cli;

import java.util.Objects;

/**
 * A normally returned invocation retaining original operational failures and both channel observations. This value does not advertise deep
 * immutability of original exceptions.
 * 
 * @param outcome
 *          established command outcome
 * @param emission
 *          completed adapter observations, including explicitly unused channels
 */
public record CommandInvocation(AnalyzeCommandOutcome outcome, CommandEmission emission) {
  /** Requires established results and refuses interrupted ordinary completion. */
  public CommandInvocation {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(emission, "emission");
    CommandExitPolicy.classify(outcome, emission);
  }

  /**
   * Classifies the established semantic outcome and observed emission failures.
   * 
   * @return semantic code 0/1/2/3, or 4 for semantic success with output failure
   */
  public int exitCode() {
    return CommandExitPolicy.classify(outcome, emission);
  }
}
