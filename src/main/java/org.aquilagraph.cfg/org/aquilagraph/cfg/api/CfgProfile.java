package org.aquilagraph.cfg.api;

import java.util.Objects;

/**
 * Versioned control-flow interpretation and retained graph format.
 *
 * @param version
 *          control-flow policy version
 * @param graphModelVersion
 *          retained graph value version
 */
public record CfgProfile(String version, String graphModelVersion) {
  /** Initial bounded Java control-flow interpretation. */
  public static final CfgProfile JAVA_BOUNDED = new CfgProfile("java-cfg-bounded/1", "java-cfg/2");

  /** Compatible bounded interpretation that explicitly refuses represented switch expressions. */
  public static final CfgProfile JAVA_WITHOUT_SWITCH_EXPRESSIONS = new CfgProfile("java-cfg-without-switch-expressions/1", "java-cfg/2");

  /** Requires explicit nonblank versions without normalizing an unsupported request. */
  public CfgProfile {
    if (Objects.requireNonNull(version, "version").isBlank() || Objects.requireNonNull(graphModelVersion, "graphModelVersion").isBlank()) {
      throw new IllegalArgumentException("CFG profile versions cannot be blank");
    }
  }
}
