package org.aquilagraph.frontend.internal;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.Objects;
import java.util.function.Consumer;

/** Finds immediate declaration boundaries inside an actual compiler subtree. */
final class JavaNestedDeclarationTraversal {
  private JavaNestedDeclarationTraversal() {
  }

  static void scan(TreePath root, Consumer<TreePath> declaration) {
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(declaration, "declaration");
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitClass(ClassTree tree, Void ignored) {
        declaration.accept(getCurrentPath());
        return null;
      }
    }.scan(root, null);
  }
}
