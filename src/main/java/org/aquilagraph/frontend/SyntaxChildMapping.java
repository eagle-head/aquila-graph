package org.aquilagraph.frontend;

import com.sun.source.tree.Tree;
import java.util.Objects;

record SyntaxChildMapping(Tree tree, SyntaxStructuralPath structuralPath) {
  SyntaxChildMapping {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(structuralPath, "structuralPath");
  }
}
