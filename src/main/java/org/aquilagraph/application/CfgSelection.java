package org.aquilagraph.application;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.cfg.api.CfgProfile;

/**
 * Unbound graph selection retained before source production.
 *
 * @param methodKeys
 *          unchanged ordered method selection
 * @param profile
 *          complete graph interpretation
 * @param scope
 *          explicit methods or all produced bodies
 */
public record CfgSelection(List<String> methodKeys, CfgProfile profile, Scope scope) {
  /** Selection policy bound to the produced source exactly once. */
  public enum Scope {
    /** Preserve the caller's ordered method keys, including invalid selections. */
    METHODS,
    /** Select every declared method body in canonical source order. */
    ALL_BODIES
  }

  /**
   * Creates an explicit ordered method selection.
   *
   * @param methodKeys
   *          unchanged requested keys
   * @param profile
   *          graph interpretation
   */
  public CfgSelection(List<String> methodKeys, CfgProfile profile) {
    this(methodKeys, profile, Scope.METHODS);
  }

  /**
   * Requests all method bodies without inventing keys before source production.
   *
   * @param profile
   *          graph interpretation
   * @return unbound all-body selection
   */
  public static CfgSelection allBodies(CfgProfile profile) {
    return new CfgSelection(List.of(), profile, Scope.ALL_BODIES);
  }

  /** Copies selection without repairing duplicate, blank, or unknown entries. */
  public CfgSelection {
    methodKeys = List.copyOf(Objects.requireNonNull(methodKeys, "methodKeys"));
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(scope, "scope");
    if (scope == Scope.ALL_BODIES && !methodKeys.isEmpty()) {
      throw new IllegalArgumentException("All-body selection cannot supply method keys before production");
    }
  }
}
