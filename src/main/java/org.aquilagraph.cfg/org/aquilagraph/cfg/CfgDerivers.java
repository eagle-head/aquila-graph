package org.aquilagraph.cfg;

import org.aquilagraph.cfg.api.CfgDeriver;
import org.aquilagraph.cfg.internal.JavaCfgDeriver;

/** Composition entry point kept outside the immutable CFG reader contract. */
public final class CfgDerivers {
  private CfgDerivers() {
  }

  /**
   * Creates the stateless bounded Java control-flow implementation.
   *
   * @return compiler-independent derivation port
   */
  public static CfgDeriver standard() {
    return new JavaCfgDeriver();
  }
}
