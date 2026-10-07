package org.aquilagraph.source.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable root-relative sequence of typed structural role and position steps.
 *
 * @param steps
 *          path steps in root-to-occurrence order
 */
public record SyntaxStructuralPath(List<Step> steps) {
  /** Creates an immutable validated path. */
  public SyntaxStructuralPath {
    steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    steps.forEach(step -> Objects.requireNonNull(step, "step"));
    if (steps.isEmpty() || steps.getFirst().role() != SyntaxRole.SOURCE_FILE_ROOT || steps.getFirst().position() != 0) {
      throw new IllegalArgumentException("A syntax structural path must begin with source-file root position 0");
    }
    if (steps.stream().skip(1).anyMatch(step -> step.role() == SyntaxRole.SOURCE_FILE_ROOT)) {
      throw new IllegalArgumentException("A syntax structural path cannot contain a second source-file root");
    }
  }

  /**
   * Starts a path at the captured source-file boundary.
   *
   * @return source-root path
   */
  public static SyntaxStructuralPath sourceFileRoot() {
    return new SyntaxStructuralPath(List.of(new Step(SyntaxRole.SOURCE_FILE_ROOT, 0)));
  }

  /**
   * Appends one typed structural step.
   *
   * @param role
   *          structural role
   * @param position
   *          role-local position
   * @return extended immutable path
   */
  public SyntaxStructuralPath child(SyntaxRole role, int position) {
    var extended = new ArrayList<>(steps);
    extended.add(new Step(role, position));
    return new SyntaxStructuralPath(extended);
  }

  /**
   * Returns the stable identity components in path order.
   *
   * @return alternating role identities and decimal positions
   */
  public List<String> identityComponents() {
    var components = new ArrayList<String>(steps.size() * 2);
    for (var step : steps) {
      components.add(step.role().stableId());
      components.add(Integer.toString(step.position()));
    }
    return List.copyOf(components);
  }

  /**
   * One structural path step.
   *
   * @param role
   *          typed role
   * @param position
   *          zero-based role-local position
   */
  public record Step(SyntaxRole role, int position) {
    /** Validates the typed role and position. */
    public Step {
      Objects.requireNonNull(role, "role");
      if (position < 0) {
        throw new IllegalArgumentException("A syntax structural path position cannot be negative");
      }
    }
  }
}
