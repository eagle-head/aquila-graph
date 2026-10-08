package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.AnyPatternTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.jspecify.annotations.Nullable;

/** Traverses actual parsed owner topology without consulting emitted or expected evidence. */
final class JavaCoverageTopology {
  private JavaCoverageTopology() {
  }

  static List<Owner> owners(List<CompilationUnitTree> units, SnapshotSourceCatalog sources, JavaSourceEvidence evidence) {
    return owners(units, sources::find, evidence);
  }

  static List<Owner> owners(List<CompilationUnitTree> units, Function<CompilationUnitTree, Optional<SourceFile>> sources,
      JavaSourceEvidence evidence) {
    var owners = new ArrayList<Owner>();
    var annotations = new HashSet<SourceSpan>();
    for (var unit : units) {
      var source = sources.apply(unit).orElseThrow();
      captureUnit(unit, source, evidence, owners, annotations);
    }
    return List.copyOf(owners);
  }

  private static void captureUnit(CompilationUnitTree unit, SourceFile source, JavaSourceEvidence evidence, List<Owner> owners,
      Set<SourceSpan> annotations) {
    new TreeScanner<Void, SourceOwnerPath>() {
      private @Nullable TreePath parent;
      private List<JavaParsedChildRoles.Child> children = List.of();
      private int nextChild;

      @Override
      public Void scan(@Nullable Tree tree, SourceOwnerPath path) {
        if (tree == null) {
          return null;
        }
        var actualPath = path;
        if (parent != null) {
          if (nextChild >= children.size()) {
            throw new IllegalStateException("A parsed scanner child has no declared parent role");
          }
          var child = children.get(nextChild);
          nextChild++;
          if (!JavaProjectionIdentity.sameInstance(child.tree(), tree)) {
            throw new IllegalStateException("Parsed parent roles must retain the exact compiler scanner child order");
          }
          for (var step : child.steps()) {
            actualPath = actualPath.child(step.role(), step.ordinal());
          }
        }
        if (isDerivedCompactParameter(tree, parent, unit, source, evidence)) {
          return null;
        }
        var origin = evidence.origin(unit, source, tree);
        if (tree instanceof AnnotationTree && origin.isPresent() && !annotations.add(origin.orElseThrow())) {
          return null;
        }
        var savedParent = parent;
        var savedChildren = children;
        var savedNext = nextChild;
        parent = savedParent == null ? new TreePath(unit) : new TreePath(savedParent, tree);
        children = JavaParsedChildRoles.children(tree);
        nextChild = 0;
        owners.add(new Owner(unit, source, tree, parent, actualPath, origin));
        super.scan(tree, actualPath);
        if (nextChild != children.size()) {
          throw new IllegalStateException("Every declared parsed parent role requires its exact compiler child encounter");
        }
        parent = savedParent;
        children = savedChildren;
        nextChild = savedNext;
        return null;
      }
    }.scan(unit, SourceOwnerPath.root());
  }

  static Optional<Class<?>> surface(Tree tree) {
    if (tree instanceof NewArrayTree) {
      return Optional.of(NewArrayTree.class);
    }
    if (tree instanceof CompilationUnitTree) {
      return Optional.of(CompilationUnitTree.class);
    }
    if (tree instanceof ClassTree) {
      return Optional.of(ClassTree.class);
    }
    if (tree instanceof MethodTree) {
      return Optional.of(MethodTree.class);
    }
    if (tree instanceof VariableTree) {
      return Optional.of(VariableTree.class);
    }
    return switch (tree) {
      case AssertTree _ -> Optional.of(AssertTree.class);
      case SynchronizedTree _ -> Optional.of(SynchronizedTree.class);
      case LambdaExpressionTree _ -> Optional.of(LambdaExpressionTree.class);
      case MemberReferenceTree _ -> Optional.of(MemberReferenceTree.class);
      case InstanceOfTree _ -> Optional.of(InstanceOfTree.class);
      case BindingPatternTree _ -> Optional.of(BindingPatternTree.class);
      case DeconstructionPatternTree _ -> Optional.of(DeconstructionPatternTree.class);
      case AnyPatternTree _ -> Optional.of(AnyPatternTree.class);
      case WhileLoopTree _ -> Optional.of(WhileLoopTree.class);
      case DoWhileLoopTree _ -> Optional.of(DoWhileLoopTree.class);
      case ForLoopTree _ -> Optional.of(ForLoopTree.class);
      case EnhancedForLoopTree _ -> Optional.of(EnhancedForLoopTree.class);
      case LabeledStatementTree _ -> Optional.of(LabeledStatementTree.class);
      case BreakTree _ -> Optional.of(BreakTree.class);
      case ContinueTree _ -> Optional.of(ContinueTree.class);
      default -> Optional.empty();
    };
  }

  static boolean isDerivedCompactParameter(Tree tree, @Nullable TreePath parent, CompilationUnitTree unit, SourceFile source,
      JavaSourceEvidence evidence) {
    if (!(tree instanceof VariableTree variable) || parent == null || !(parent.getLeaf() instanceof MethodTree method)
        || method.getReturnType() != null || evidence.origin(unit, source, variable).isPresent()) {
      return false;
    }
    var owner = parent.getParentPath();
    return owner != null && owner.getLeaf() instanceof ClassTree type && type.getKind() == Tree.Kind.RECORD
        && type.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast)
            .anyMatch(component -> component.getName().contentEquals(variable.getName())
                && JavaProjectionIdentity.sameInstance(component.getType(), variable.getType()));
  }

  record Owner(CompilationUnitTree unit, SourceFile source, Tree tree, TreePath treePath, SourceOwnerPath path,
      Optional<SourceSpan> origin) {
  }
}
