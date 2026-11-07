package org.aquilagraph.cli;

import org.aquilagraph.cli.CommandPresentationException.Phase;

/** The current operation of one synchronous destination attempt. */
final class EmissionAttempt {
  private Phase current = Phase.WRITE;

  void at(Phase phase) {
    current = phase;
  }

  Phase phase() {
    return current;
  }
}
