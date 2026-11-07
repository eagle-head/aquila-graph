package org.aquilagraph.cli;

import java.util.Objects;

/**
 * Independent observations in attempt order, stdout followed by stderr.
 * 
 * @param output
 *          standard result observation
 * @param error
 *          diagnostic observation
 */
public record CommandEmission(ChannelEmission output, ChannelEmission error) {
  /** Requires both observations, including explicitly unused or unattempted work. */
  public CommandEmission {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");
  }
}
