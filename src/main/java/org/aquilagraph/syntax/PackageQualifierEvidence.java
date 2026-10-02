package org.aquilagraph.syntax;

import java.util.Objects;
import org.aquilagraph.model.SourceSpan;

/**
 * Copied source-tree kind and exact range of a written package name.
 *
 * @param sourceKind
 *          public source-tree kind of the package name
 * @param origin
 *          package-name range in the captured source
 */
public record PackageQualifierEvidence(JavaSourceKind sourceKind, SourceSpan origin) {
  /** Validates the written name kind and nonempty source range. */
  public PackageQualifierEvidence {
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(origin, "origin");
    if ((sourceKind != JavaSourceKind.IDENTIFIER && sourceKind != JavaSourceKind.MEMBER_SELECT) || origin.length() == 0) {
      throw new IllegalArgumentException("Package qualifier evidence requires a name tree and a nonempty source range");
    }
  }
}
