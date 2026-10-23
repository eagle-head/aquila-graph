package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.identity.api.AstIdentity;

/**
 * Single-operation repository ownership and exact subject.
 *
 * @param kind
 *          save or query operation
 * @param astIdentity
 *          exact attempted or requested source identity
 */
public record RepositoryOperationContext(RepositoryOperationKind kind, AstIdentity astIdentity) {
  /** Requires a real qualified identity without a fabricated unavailable subject. */
  public RepositoryOperationContext {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(astIdentity, "astIdentity");
  }
}
