package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

/** Distinguishes parsed enum constant syntax from its implicit compiler envelope. */
final class JavaEnumConstantSyntax {
  private JavaEnumConstantSyntax() {
  }

  static boolean isConstant(TreePath path, JavaSourceEvidence evidence) {
    return path.getLeaf() instanceof VariableTree variable && variable.getInitializer() instanceof NewClassTree
        && path.getParentPath() != null && path.getParentPath().getLeaf() instanceof ClassTree type && type.getKind() == Tree.Kind.ENUM
        && variable.getType() instanceof IdentifierTree name && name.getName().contentEquals(type.getSimpleName())
        && evidence.rawEnd(path.getCompilationUnit(), name) < 0;
  }

  static boolean isImplicitEnvelope(TreePath path, JavaSourceEvidence evidence) {
    var parent = path.getParentPath();
    if (parent == null) {
      return false;
    }
    if (isConstant(parent, evidence) && parent.getLeaf() instanceof VariableTree variable) {
      return JavaProjectionIdentity.sameInstance(path.getLeaf(), variable.getType())
          || JavaProjectionIdentity.sameInstance(path.getLeaf(), variable.getInitializer());
    }
    return parent.getLeaf() instanceof NewClassTree creation && parent.getParentPath() != null
        && isConstant(parent.getParentPath(), evidence) && JavaProjectionIdentity.sameInstance(path.getLeaf(), creation.getIdentifier());
  }
}
