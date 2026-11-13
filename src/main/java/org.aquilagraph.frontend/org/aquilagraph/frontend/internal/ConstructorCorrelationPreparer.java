package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.aquilagraph.frontend.internal.CompletedConstructorCorrelations.Entry;
import org.aquilagraph.frontend.internal.ConstructorCorrelation.Status;

/** Prepares every eligible occurrence before exposing a completed query. */
final class ConstructorCorrelationPreparer {
  private final ConstructorWitnessCompiler compiler;
  private final ConstructorWitnessInputs inputs;
  private final Trees originalTrees;
  private final Types originalTypes;
  private final JavaTranslationPolicy policy;
  private final JavaInvocationApplicability originalApplicability;
  private final ConstructorWitnessCorrelation correspondence;

  ConstructorCorrelationPreparer(ConstructorWitnessCompiler compiler, ConstructorWitnessInputs inputs, Trees trees, Types types,
      Elements elements, JavaTranslationPolicy policy) {
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.inputs = Objects.requireNonNull(inputs, "inputs");
    originalTrees = Objects.requireNonNull(trees, "trees");
    originalTypes = Objects.requireNonNull(types, "types");
    this.policy = Objects.requireNonNull(policy, "policy");
    var originalSemantics = new SemanticResolver(trees, types, elements, new DeclarationIdentityRegistry(trees, types, elements, policy));
    originalApplicability = new JavaInvocationApplicability(originalSemantics);
    correspondence = new ConstructorWitnessCorrelation(trees, types, elements, originalSemantics, policy);
  }

  CompletedConstructorCorrelations prepare(List<CompilationUnitTree> units) throws IOException {
    var correlations = new ArrayList<Entry>();
    var paths = new ArrayList<TreePath>();
    var scanner = new TreePathScanner<Void, Void>() {
      @Override
      public Void visitNewClass(NewClassTree creation, Void ignored) {
        if (!policy.supportsNestedDeclarations() && creation.getClassBody() != null) {
          return null;
        }
        if (policy.supportsNestedDeclarations() || insideMethod(getCurrentPath())) {
          paths.add(getCurrentPath());
        }
        return super.visitNewClass(creation, null);
      }

    };
    units.forEach(unit -> scanner.scan(unit, null));
    for (var path : paths) {
      if (!(path.getLeaf() instanceof NewClassTree creation) || !(originalTrees.getElement(path) instanceof ExecutableElement original)
          || (original.getTypeParameters().isEmpty() && (creation.getClassBody() == null || creation.getArguments().isEmpty()))) {
        continue;
      }
      var target = constructorTarget(path, creation, original);
      if (target.isEmpty()) {
        continue;
      }
      correlations.add(new Entry(creation, prepare(target.orElseThrow())));
    }
    return new CompletedConstructorCorrelations(originalTrees, correlations);
  }

  private ConstructorCorrelation prepare(ConstructorOccurrence occurrence) throws IOException {
    var path = occurrence.path();
    var creation = occurrence.creation();
    var selected = occurrence.constructor();
    var created = occurrence.created();
    if (originalTypes.asMemberOf(created, selected) instanceof ExecutableType executable && executable.getTypeVariables().isEmpty()) {
      return correspondence.attributedSelection(occurrence, executable);
    }
    var fixed = creation.getArguments().size() == selected.getParameters().size()
        && !fixedArrayCannotAcceptCast(path, creation, selected, created)
            ? attribute(occurrence, false)
            : ConstructorCorrelation.inapplicable();
    return fixed.status() == Status.INAPPLICABLE && selected.isVarArgs() ? attribute(occurrence, true) : fixed;
  }

  private ConstructorCorrelation attribute(ConstructorOccurrence occurrence, boolean expanded) throws IOException {
    var input = inputs.assemble(occurrence, expanded);
    if (input.isEmpty()) {
      return ConstructorCorrelation.unavailable();
    }
    var witness = input.orElseThrow();
    var selection = witness.selection();
    var matching = correspondence;
    return compiler.attribute(witness, attribution -> matching.correlate(occurrence, selection, attribution));
  }

  private static boolean insideMethod(TreePath path) {
    for (var current = path.getParentPath(); current != null; current = current.getParentPath()) {
      if (current.getLeaf() instanceof MethodTree method) {
        return method.getBody() != null;
      }
    }
    return false;
  }

  private boolean fixedArrayCannotAcceptCast(TreePath path, NewClassTree creation, ExecutableElement selected, DeclaredType created) {
    if (!selected.isVarArgs() || creation.getArguments().isEmpty() || !(creation.getArguments().getLast() instanceof TypeCastTree)
        || !(originalTypes.asMemberOf(created, selected) instanceof ExecutableType member)) {
      return false;
    }
    var array = member.getParameterTypes().getLast();
    var source = originalTrees.getTypeMirror(new TreePath(path, creation.getArguments().getLast()));
    return source != null && !ConstructorTypeCorrespondence.retainsWitnessParameter(array, selected)
        && !originalApplicability.looseConversion(source, array);
  }

  private Optional<ConstructorOccurrence> constructorTarget(TreePath path, NewClassTree creation, ExecutableElement original) {
    if (creation.getClassBody() == null) {
      return original.getEnclosingElement() instanceof TypeElement owner
          && originalTrees.getTypeMirror(path) instanceof DeclaredType created && created.asElement().equals(owner)
              ? Optional.of(new ConstructorOccurrence(path, creation, original, created, owner))
              : Optional.empty();
    }
    var declaration = originalTrees.getPath(original);
    if (declaration == null || !(declaration.getLeaf() instanceof MethodTree method) || method.getBody() == null
        || method.getBody().getStatements().isEmpty()
        || !(method.getBody().getStatements().getFirst() instanceof ExpressionStatementTree statement)
        || !(statement.getExpression() instanceof MethodInvocationTree delegation)) {
      return Optional.empty();
    }
    var call = new TreePath(new TreePath(new TreePath(declaration, method.getBody()), statement), delegation);
    if (!(originalTrees.getElement(call) instanceof ExecutableElement selected)
        || !(selected.getEnclosingElement() instanceof TypeElement owner)
        || !(original.getEnclosingElement() instanceof TypeElement anonymous)
        || !(anonymous.getSuperclass() instanceof DeclaredType created) || !created.asElement().equals(owner)) {
      return Optional.empty();
    }
    return Optional.of(new ConstructorOccurrence(path, creation, selected, created, owner));
  }
}
