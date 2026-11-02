package org.aquilagraph.frontend.internal;

import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.ProvidesTree;
import com.sun.source.tree.UsesTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.ModuleElement.ProvidesDirective;
import javax.lang.model.element.ModuleElement.UsesDirective;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;

/** Correlates original written operand paths with public module directive declarations. */
final class JavaModuleOperandCorrelation {
  private static final int SINGLE_DIRECTIVE_COUNT = 1;
  private final Trees trees;
  private final DeclarationIdentityRegistry identities;

  JavaModuleOperandCorrelation(Trees trees, DeclarationIdentityRegistry identities) {
    this.trees = Objects.requireNonNull(trees, "trees");
    this.identities = Objects.requireNonNull(identities, "identities");
  }

  SemanticReference resolve(TreePath operand) {
    var element = trees.getElement(operand);
    var mirror = trees.getTypeMirror(operand);
    var erroneous = mirror != null && mirror.getKind() == TypeKind.ERROR;
    var candidates = targets(operand);
    var selected = candidates.stream().filter(target -> target.equals(element)).toList();
    if (erroneous || selected.size() != SINGLE_DIRECTIVE_COUNT) {
      return new SemanticReference(ReferenceKind.TYPE, erroneous ? ResolutionStatus.ERROR : ResolutionStatus.UNRESOLVED, Optional.empty(),
          Optional.empty(), List.of(), List.of());
    }
    var target = selected.getFirst();
    var identity = identities.typeIdentity(target);
    var key = identities.keyOf(target);
    return new SemanticReference(ReferenceKind.TYPE,
        identity.isPresent() && key.isPresent() ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED, key,
        Optional.of(target.getQualifiedName().toString()), List.of(), List.of(), identity);
  }

  Optional<TypeElement> expectedTarget(TreePath operand) {
    var selected = trees.getElement(operand);
    var matches = targets(operand).stream().filter(target -> target.equals(selected)).toList();
    return matches.size() == SINGLE_DIRECTIVE_COUNT && matches.getFirst().asType().getKind() != TypeKind.ERROR
        ? Optional.of(matches.getFirst())
        : Optional.empty();
  }

  private List<? extends TypeElement> targets(TreePath operand) {
    var directivePath = operand.getParentPath();
    if (directivePath == null || directivePath.getParentPath() == null || !(directivePath.getParentPath().getLeaf() instanceof ModuleTree)
        || !(trees.getElement(directivePath.getParentPath()) instanceof ModuleElement module)) {
      return List.of();
    }
    ExpressionTree serviceTree = switch (directivePath.getLeaf()) {
      case UsesTree uses -> uses.getServiceName();
      case ProvidesTree provides -> provides.getServiceName();
      default -> null;
    };
    if (serviceTree == null) {
      return List.of();
    }
    var service = trees.getElement(new TreePath(directivePath, serviceTree));
    if (!(service instanceof TypeElement)) {
      return List.of();
    }
    var directives = module.getDirectives().stream().filter(directive -> switch (directive) {
      case UsesDirective uses -> directivePath.getLeaf() instanceof UsesTree && uses.getService().equals(service);
      case ProvidesDirective provides -> directivePath.getLeaf() instanceof ProvidesTree && provides.getService().equals(service);
      default -> false;
    }).toList();
    if (directives.size() != SINGLE_DIRECTIVE_COUNT) {
      return List.of();
    }
    return switch (directives.getFirst()) {
      case UsesDirective uses when directivePath.getLeaf() instanceof UsesTree tree
          && JavaProjectionIdentity.sameInstance(tree.getServiceName(), operand.getLeaf()) ->
        List.of(uses.getService());
      case ProvidesDirective provides when directivePath.getLeaf() instanceof ProvidesTree tree ->
        JavaProjectionIdentity.sameInstance(tree.getServiceName(), operand.getLeaf())
            ? List.of(provides.getService())
            : tree.getImplementationNames().stream().anyMatch(value -> JavaProjectionIdentity.sameInstance(value, operand.getLeaf()))
                ? provides.getImplementations()
                : List.of();
      default -> List.of();
    };
  }
}
