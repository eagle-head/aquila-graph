package org.aquilagraph.identity.api;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable role-specific interpretation of a captured dependency occurrence.
 *
 * @param state
 *          interpretation outcome
 * @param modules
 *          observed candidates, including duplicate lookup names
 */
public record DependencySemantics(State state, List<ModuleCandidate> modules) {
  /** Validates and canonically orders copied candidates without opening files. */
  public DependencySemantics {
    Objects.requireNonNull(state, "state");
    modules = List.copyOf(modules.stream().sorted(Comparator.comparing(ModuleCandidate::member)).toList());
    if (state != State.MODULES && state != State.INVALID && !modules.isEmpty()) {
      throw new IllegalArgumentException("This dependency interpretation cannot contain module candidates");
    }
    if (modules.stream().map(ModuleCandidate::member).distinct().count() != modules.size()) {
      throw new IllegalArgumentException("Each module candidate must identify a distinct artifact member");
    }
    if (modules.size() > 1 && modules.stream().anyMatch(module -> ".".equals(module.member()))) {
      throw new IllegalArgumentException("A direct module cannot also contain child module candidates");
    }
  }

  /** Closed outcomes of dependency interpretation. */
  public enum State {
    /** Class-path use does not discover named modules. */
    CLASS_PATH,
    /** The configured module-path artifact is physically absent. */
    MISSING,
    /** Successful discovery, possibly with no recognized modules. */
    MODULES,
    /** Compiler module discovery reported invalid modular input. */
    INVALID
  }
}
