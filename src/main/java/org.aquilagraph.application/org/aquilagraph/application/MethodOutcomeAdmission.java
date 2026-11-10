package org.aquilagraph.application;

import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.JavaMethod;

final class MethodOutcomeAdmission {
  private MethodOutcomeAdmission() {
  }

  static void requireCompatibleBody(JavaMethod declaration, MethodCfgOutcome outcome) {
    if (outcome.state() == MethodCfgState.NOT_APPLICABLE && declaration.hasBody()) {
      throw new IllegalArgumentException("Body-bearing method " + declaration.methodKey() + " cannot have CFG state " + outcome.state());
    }
  }
}
