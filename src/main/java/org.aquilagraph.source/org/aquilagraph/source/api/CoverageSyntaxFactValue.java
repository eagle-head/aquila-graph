package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Complete immutable source-occurrence support projection.
 *
 * @param structuralPath
 *          exact retained syntax role path
 * @param sourceKind
 *          original public source-tree category
 * @param syntaxKind
 *          retained syntax category
 * @param type
 *          copied syntax type outcome
 * @param value
 *          complete retained syntax value
 * @param children
 *          ordered child attachment identities
 * @param semanticReference
 *          selected semantic reference, when present
 * @param sourceAssociation
 *          independently copied parsed owner association, when present
 */
public record CoverageSyntaxFactValue(SyntaxStructuralPath structuralPath, JavaSourceKind sourceKind, SyntaxKind syntaxKind,
    SyntaxType type, SyntaxValue value, List<Child> children, Optional<SemanticReference> semanticReference,
    Optional<WrittenSourceAssociation> sourceAssociation) {
  /** Copies exact local syntax facts and ordered attachments. */
  public CoverageSyntaxFactValue {
    Objects.requireNonNull(structuralPath, "structuralPath");
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(syntaxKind, "syntaxKind");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    children = List.copyOf(Objects.requireNonNull(children, "children"));
    Objects.requireNonNull(semanticReference, "semanticReference");
    Objects.requireNonNull(sourceAssociation, "sourceAssociation");
  }

  /**
   * Returns the category actually observed by a compiler accessor at this attachment.
   *
   * @return original exact or normalization-root category; absent for a derived descendant
   */
  public Optional<JavaSourceKind> observedCompilerKind() {
    var normalized = sourceAssociation.flatMap(WrittenSourceAssociation::normalizedExpression);
    return normalized.isEmpty()
        ? Optional.of(sourceAssociation.map(WrittenSourceAssociation::sourceKind).orElse(sourceKind))
        : normalized.filter(value -> value.occurrencePath().isEmpty()).map(NormalizedExpressionCorrespondence::compilerKind);
  }

  /**
   * Exact child attachment identity in retained order.
   *
   * @param role
   *          direct child attachment role
   * @param ordinal
   *          position within the attachment role
   * @param nodeKey
   *          exact retained child identity
   */
  public record Child(SyntaxRole role, int ordinal, String nodeKey) {
  }
}
