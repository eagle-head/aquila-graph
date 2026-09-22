package org.aquilagraph.frontend;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.Objects;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxStructuralPath;

record SyntaxChildMapping(SyntaxRole role, int position, Tree tree, TreePath treePath, SyntaxStructuralPath structuralPath, SyntaxUse use) {
  SyntaxChildMapping {
    Objects.requireNonNull(role, "role");
    if (position < 0) {
      throw new IllegalArgumentException("A syntax child position cannot be negative");
    }
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(treePath, "treePath");
    if (!treePath.getLeaf().equals(tree)) {
      throw new IllegalArgumentException("A syntax child path must end at its child tree");
    }
    Objects.requireNonNull(structuralPath, "structuralPath");
    Objects.requireNonNull(use, "use");
  }
}
