package org.aquilagraph.source.api;

import java.util.Objects;

/** Actual compiler address family corroborating a normalized written-expression region. */
public sealed interface CompilerExpressionAnchor permits CompilerExpressionAnchor.Execution, CompilerExpressionAnchor.AnnotationSource {
  /**
   * An actual attributed execution-tree address.
   *
   * @param path
   *          exact attributed address including compiler member and statement positions
   */
  record Execution(SyntaxStructuralPath path) implements CompilerExpressionAnchor {
    /** Requires an explicit actual execution address. */
    public Execution {
      Objects.requireNonNull(path, "path");
    }
  }

  /**
   * A physical annotation-value or annotation-default occurrence captured before attribution.
   *
   * @param ownerPath
   *          original parsed source owner, without inventing an execution-tree address
   */
  record AnnotationSource(SourceOwnerPath ownerPath) implements CompilerExpressionAnchor {
    /** Requires the exact physical source address. */
    public AnnotationSource {
      Objects.requireNonNull(ownerPath, "ownerPath");
    }
  }
}
