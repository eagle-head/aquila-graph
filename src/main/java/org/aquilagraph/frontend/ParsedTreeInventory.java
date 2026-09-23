package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.TreePath;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Identifies written syntax before compiler attribution can insert tree nodes. */
final class ParsedTreeInventory {
  private final Set<Tree> writtenTrees;

  private ParsedTreeInventory(Set<Tree> writtenTrees) {
    this.writtenTrees = writtenTrees;
  }

  static ParsedTreeInventory capture(List<CompilationUnitTree> units) {
    Objects.requireNonNull(units, "units");
    var writtenTrees = Collections.newSetFromMap(new IdentityHashMap<Tree, Boolean>());
    var scanner = new TreeScanner<Void, Void>() {
      @Override
      public Void scan(Tree tree, Void unused) {
        if (tree != null) {
          writtenTrees.add(tree);
        }
        return super.scan(tree, null);
      }
    };
    units.forEach(unit -> scanner.scan(unit, null));
    return new ParsedTreeInventory(writtenTrees);
  }

  boolean isWritten(Tree tree) {
    return writtenTrees.contains(Objects.requireNonNull(tree, "tree"));
  }

  boolean isCompilerInsertedSuperCall(TreePath statementPath) {
    Objects.requireNonNull(statementPath, "statementPath");
    var tree = statementPath.getLeaf();
    if (!(tree instanceof ExpressionStatementTree statement) || !(statement.getExpression() instanceof MethodInvocationTree invocation)
        || !(invocation.getMethodSelect() instanceof IdentifierTree selector) || !selector.getName().contentEquals("super")
        || !(statementPath.getParentPath().getLeaf() instanceof BlockTree block) || block.getStatements().isEmpty()
        || !block.getStatements().getFirst().equals(tree)
        || !(statementPath.getParentPath().getParentPath().getLeaf() instanceof MethodTree method) || method.getReturnType() != null) {
      return false;
    }
    return !isWritten(tree);
  }
}
