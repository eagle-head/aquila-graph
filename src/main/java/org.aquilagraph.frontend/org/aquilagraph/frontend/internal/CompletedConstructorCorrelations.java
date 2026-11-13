package org.aquilagraph.frontend.internal;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;

/** An immutable index of constructor facts for occurrences in one attributed session. */
final class CompletedConstructorCorrelations {
  private final Trees originalTrees;
  private final Function<Tree, ConstructorCorrelation> find;

  CompletedConstructorCorrelations(Trees originalTrees, List<Entry> correlations) {
    this.originalTrees = Objects.requireNonNull(originalTrees, "originalTrees");
    var index = new IdentityHashMap<Tree, ConstructorCorrelation>();
    correlations.forEach(entry -> index.put(entry.occurrence(), entry.correlation()));
    find = tree -> index.getOrDefault(tree, ConstructorCorrelation.unavailable());
  }

  ConstructorCorrelation select(TreePath path, ExecutableElement selected) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(selected, "selected");
    if (!selected.equals(originalTrees.getElement(path))) {
      throw new IllegalArgumentException("Constructor selection requires its exact original attributed element");
    }
    return find.apply(path.getLeaf());
  }

  record Entry(Tree occurrence, ConstructorCorrelation correlation) {
  }
}
