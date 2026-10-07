package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Complete immutable neutral source-occurrence projection.
 *
 * @param structuralPath
 *          exact typed-role path
 * @param sourceKind
 *          original source category
 * @param syntaxKind
 *          retained syntax category
 * @param type
 *          exact typed outcome and recursive components
 * @param value
 *          exact syntax value components
 * @param children
 *          ordered attachment identities
 * @param semanticReference
 *          exact selected semantic reference
 * @param sourceAssociation
 *          original parsed owner association
 */
public record CoverageSourceFact(List<Step> structuralPath, String sourceKind, String syntaxKind, CoverageEvidenceValue.Structure type,
    CoverageEvidenceValue.Structure value, List<Child> children, Optional<SemanticReference> semanticReference,
    Optional<Association> sourceAssociation) {
  /** Copies all ordered collections and requires closed component values. */
  public CoverageSourceFact {
    structuralPath = List.copyOf(Objects.requireNonNull(structuralPath, "structuralPath"));
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(syntaxKind, "syntaxKind");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    children = List.copyOf(Objects.requireNonNull(children, "children"));
    Objects.requireNonNull(semanticReference, "semanticReference");
    Objects.requireNonNull(sourceAssociation, "sourceAssociation");
    if (structuralPath.isEmpty() || sourceKind.isBlank() || syntaxKind.isBlank()) {
      throw new IllegalArgumentException("A source fact requires exact path and category identities");
    }
  }

  /**
   * One structural role and ordinal.
   *
   * @param role
   *          role identity
   * @param ordinal
   *          role-local ordinal
   */
  public record Step(String role, int ordinal) {
    /** Requires a role and nonnegative position. */
    public Step {
      if (Objects.requireNonNull(role, "role").isBlank() || ordinal < 0) {
        throw new IllegalArgumentException("A source step requires a role and nonnegative ordinal");
      }
    }
  }

  /**
   * Exact child attachment.
   *
   * @param role
   *          role identity
   * @param ordinal
   *          role-local ordinal
   * @param nodeKey
   *          child identity
   */
  public record Child(String role, int ordinal, String nodeKey) {
    /** Requires exact attachment identities. */
    public Child {
      if (Objects.requireNonNull(role, "role").isBlank() || ordinal < 0 || Objects.requireNonNull(nodeKey, "nodeKey").isBlank()) {
        throw new IllegalArgumentException("A source child requires role, ordinal and identity");
      }
    }
  }

  /**
   * Parsed source owner retained independently at actual creation.
   *
   * @param nodeKey
   *          actual retained source identity
   * @param sourceFileKey
   *          captured source identity
   * @param ownerPath
   *          original typed parsed owner path
   * @param sourceKind
   *          original source category
   * @param origin
   *          original available span
   */
  public record Association(String nodeKey, String sourceFileKey, SourceOwnerPath ownerPath, String sourceKind,
      Optional<SourceSpan> origin) {
    /** Copies the original owner path and checks local source consistency. */
    public Association {
      Objects.requireNonNull(ownerPath, "ownerPath");
      Objects.requireNonNull(origin, "origin");
      if (Objects.requireNonNull(nodeKey, "nodeKey").isBlank() || Objects.requireNonNull(sourceFileKey, "sourceFileKey").isBlank()
          || Objects.requireNonNull(sourceKind, "sourceKind").isBlank()
          || origin.filter(span -> !span.sourceFileKey().equals(sourceFileKey)).isPresent()) {
        throw new IllegalArgumentException("A source association requires exact source, path and category values");
      }
    }
  }
}
