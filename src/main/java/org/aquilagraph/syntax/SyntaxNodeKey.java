package org.aquilagraph.syntax;

import java.util.ArrayList;
import java.util.Objects;
import org.aquilagraph.model.StableId;

/** Stable key for one Java source occurrence. */
public final class SyntaxNodeKey {
  private static final String NAMESPACE = "syntax-node";
  private final String identity;

  private SyntaxNodeKey(String nodeKey) {
    identity = nodeKey;
  }

  /**
   * Derives a key from the complete durable occurrence identity.
   *
   * @param sourceFileKey
   *          captured source owner
   * @param structuralPath
   *          root-relative typed path
   * @param sourceKind
   *          exact Java source kind
   * @return derived node key
   */
  public static SyntaxNodeKey from(String sourceFileKey, SyntaxStructuralPath structuralPath, JavaSourceKind sourceKind) {
    requireText(sourceFileKey, "Source-file key");
    Objects.requireNonNull(structuralPath, "structuralPath");
    Objects.requireNonNull(sourceKind, "sourceKind");
    if (!sourceKind.identifiesSourceSyntax()) {
      throw new IllegalArgumentException("Java source kind does not identify source syntax: " + sourceKind);
    }
    var components = new ArrayList<String>();
    components.add(sourceFileKey);
    components.addAll(structuralPath.identityComponents());
    components.add(sourceKind.stableId());
    return new SyntaxNodeKey(StableId.sha256(NAMESPACE, components.toArray(String[]::new)));
  }

  /**
   * Returns the stable textual key.
   *
   * @return namespaced SHA-256 key
   */
  public String value() {
    return identity;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || other instanceof SyntaxNodeKey that && identity.equals(that.identity);
  }

  @Override
  public int hashCode() {
    return identity.hashCode();
  }

  @Override
  public String toString() {
    return identity;
  }

  private static void requireText(String text, String label) {
    Objects.requireNonNull(text, label);
    if (text.isBlank()) {
      throw new IllegalArgumentException(label + " cannot be blank");
    }
  }
}
