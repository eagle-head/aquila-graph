package org.aquilagraph.frontend;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.Optional;

/** Selects the nearest source declaration that owns executable syntax. */
final class JavaExecutionOwner {
  private JavaExecutionOwner() {
  }

  static Optional<TreePath> find(TreePath path) {
    for (var cursor = path; cursor != null; cursor = cursor.getParentPath()) {
      if (cursor.getLeaf() instanceof MethodTree) {
        return Optional.of(cursor);
      }
      if (cursor.getParentPath() != null && cursor.getParentPath().getLeaf() instanceof ClassTree
          && (cursor.getLeaf() instanceof VariableTree || cursor.getLeaf() instanceof BlockTree)) {
        return Optional.of(cursor);
      }
    }
    return Optional.empty();
  }
}
