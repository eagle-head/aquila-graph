package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaSourcePathRoles;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;

/** Maps an actual attributed declaration or body chain to a typed source-root-relative path. */
final class JavaSyntaxStructuralPath {
  private JavaSyntaxStructuralPath() {
  }

  static SyntaxStructuralPath executionRoot(TreePath creatingPath) {
    Objects.requireNonNull(creatingPath, "creatingPath");
    var containingPath = creatingPath.getParentPath();
    if (containingPath == null) {
      if (!(creatingPath.getLeaf() instanceof CompilationUnitTree)) {
        throw new IllegalArgumentException("An execution structural path must begin at a compilation unit");
      }
      return SyntaxStructuralPath.sourceFileRoot();
    }
    var path = executionRoot(containingPath);
    var parent = containingPath.getLeaf();
    var child = creatingPath.getLeaf();
    if (parent instanceof CompilationUnitTree unit) {
      path = path.child(SyntaxRole.TYPE_DECLARATION, identityIndex(unit.getTypeDecls(), child));
    } else if (parent instanceof ClassTree type) {
      path = path.child(SyntaxRole.MEMBER, identityIndex(type.getMembers(), child));
    } else if (parent instanceof MethodTree method && JavaProjectionIdentity.sameInstance(method.getBody(), child)) {
      path = path.child(SyntaxRole.BODY, 0);
    } else {
      var step = JavaParsedChildRoles.children(parent).stream().filter(slot -> JavaProjectionIdentity.sameInstance(slot.tree(), child))
          .findFirst().orElseThrow(() -> new IllegalArgumentException("An execution path child is absent from its actual parent")).step();
      var role = JavaSourcePathRoles.syntaxRole(step.role(), Optional.of(JavaSourceKind.valueOf(parent.getKind().name())));
      path = path.child(role, step.ordinal());
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
