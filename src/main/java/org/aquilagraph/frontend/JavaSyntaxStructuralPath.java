package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxStructuralPath;

/** Maps the currently represented declaration/body chain to a typed source-root-relative path. */
final class JavaSyntaxStructuralPath {
  private JavaSyntaxStructuralPath() {
  }

  static SyntaxStructuralPath methodBody(TreePath bodyPath) {
    Objects.requireNonNull(bodyPath, "bodyPath");
    var descendants = new ArrayDeque<Tree>();
    var cursor = bodyPath;
    while (cursor.getParentPath() != null) {
      descendants.addFirst(cursor.getLeaf());
      cursor = cursor.getParentPath();
    }
    if (!(cursor.getLeaf() instanceof CompilationUnitTree)) {
      throw new IllegalArgumentException("A method-body structural path must begin at a compilation unit");
    }

    var path = SyntaxStructuralPath.sourceFileRoot();
    Tree parent = cursor.getLeaf();
    while (!descendants.isEmpty()) {
      var child = descendants.removeFirst();
      if (parent instanceof CompilationUnitTree unit) {
        path = path.child(SyntaxRole.TYPE_DECLARATION, identityIndex(unit.getTypeDecls(), child));
      } else if (parent instanceof ClassTree type) {
        path = path.child(SyntaxRole.MEMBER, identityIndex(type.getMembers(), child));
      } else if (parent instanceof MethodTree method && JavaProjectionIdentity.sameInstance(method.getBody(), child)) {
        path = path.child(SyntaxRole.BODY, 0);
      } else {
        throw new IllegalArgumentException("A method-body structural path contains an unsupported declaration step");
      }
      parent = child;
    }
    return path;
  }

  private static int identityIndex(List<? extends Tree> values, Tree expected) {
    for (var index = 0; index < values.size(); index++) {
      if (JavaProjectionIdentity.sameInstance(values.get(index), expected)) {
        return index;
      }
    }
    throw new IllegalArgumentException("A structural-path child is absent from its parent");
  }
}
