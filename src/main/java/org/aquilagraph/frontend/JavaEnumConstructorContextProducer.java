package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import org.aquilagraph.frontend.JavaMethodBodyTranslator.NodeProjection;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.syntax.EnumConstructorContextDecision;
import org.aquilagraph.syntax.EnumConstructorContextDecision.Target;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxValue;

/** Copies enum construction context only from the exact live constructor body and selected public declarations. */
final class JavaEnumConstructorContextProducer {
  private final SemanticResolver semantics;

  JavaEnumConstructorContextProducer(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  Optional<EnumConstructorContextDecision> produce(String methodKey, SyntaxNode root, NodeProjection projection) {
    var node = projection.node();
    if (node.syntaxKind() != SyntaxKind.INVOCATION || !(node.value() instanceof SyntaxValue.Invocation invocation)
        || !(projection.path().getLeaf() instanceof MethodInvocationTree call)
        || !(call.getMethodSelect() instanceof IdentifierTree selection)
        || (invocation.form() != InvocationForm.SUPER_CONSTRUCTOR && invocation.form() != InvocationForm.THIS_CONSTRUCTOR)) {
      return Optional.empty();
    }
    var methodPath = containingConstructor(root, projection);
    if (methodPath.isEmpty() || !(semantics.element(methodPath.orElseThrow()) instanceof ExecutableElement containing)
        || containing.getKind() != ElementKind.CONSTRUCTOR || !semantics.declarationKey(containing).equals(Optional.of(methodKey))
        || !(containing.getEnclosingElement() instanceof TypeElement enumOwner) || enumOwner.getKind() != ElementKind.ENUM
        || !isSourceEnum(methodPath.orElseThrow(), enumOwner)
        || !(semantics.element(projection.path()) instanceof ExecutableElement selected) || selected.getKind() != ElementKind.CONSTRUCTOR) {
      return Optional.empty();
    }
    var ownerKey = semantics.declarationKey(enumOwner);
    var selectedKey = semantics.declarationKey(selected);
    var inputs = enumSuperclassInputs(enumOwner);
    var reference = node.semanticReference();
    if (ownerKey.isEmpty() || selectedKey.isEmpty() || inputs.isEmpty() || reference.isEmpty()
        || reference.orElseThrow().referenceKind() != ReferenceKind.CONSTRUCTOR
        || reference.orElseThrow().status() != ResolutionStatus.RESOLVED || !reference.orElseThrow().symbolKey().equals(selectedKey)) {
      return Optional.empty();
    }
    Target target;
    if (invocation.form() == InvocationForm.SUPER_CONSTRUCTOR) {
      if (!selection.getName().contentEquals("super") || !call.getArguments().isEmpty() || !node.children(SyntaxRole.ARGUMENT).isEmpty()
          || node.origin().isPresent() || projection.sourceAssociation().isPresent()
          || !(selected.getEnclosingElement() instanceof TypeElement superclass)
          || !superclass.getQualifiedName().contentEquals("java.lang.Enum") || selected.isVarArgs()
          || !selected.getTypeParameters().isEmpty() || !selectedParametersMatch(projection.path(), call, selected, inputs.orElseThrow())) {
        return Optional.empty();
      }
      target = Target.ENUM_SUPERCLASS;
    } else {
      if (!selection.getName().contentEquals("this") || !JavaProjectionIdentity.sameInstance(selected.getEnclosingElement(), enumOwner)) {
        return Optional.empty();
      }
      target = Target.THIS_CONSTRUCTOR;
    }
    var inputTypes = inputs.orElseThrow();
    return Optional.of(new EnumConstructorContextDecision(methodKey, node.nodeId(), ownerKey.orElseThrow(), selectedKey.orElseThrow(),
        target, inputTypes.getFirst(), inputTypes.getLast()));
  }

  private Optional<TreePath> containingConstructor(SyntaxNode root, NodeProjection projection) {
    var callPath = projection.path();
    var statementPath = callPath.getParentPath();
    var bodyPath = statementPath == null ? null : statementPath.getParentPath();
    var methodPath = bodyPath == null ? null : bodyPath.getParentPath();
    if (statementPath == null || bodyPath == null || methodPath == null
        || !(statementPath.getLeaf() instanceof ExpressionStatementTree statement)
        || !JavaProjectionIdentity.sameInstance(statement.getExpression(), callPath.getLeaf())
        || !(methodPath.getLeaf() instanceof MethodTree method)
        || !JavaProjectionIdentity.sameInstance(method.getBody(), bodyPath.getLeaf())
        || root.children(SyntaxRole.STATEMENT).stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT
            && JavaProjectionIdentity.sameInstance(node.requiredChild(SyntaxRole.EXPRESSION), projection.node()))) {
      return Optional.empty();
    }
    return Optional.of(methodPath);
  }

  private boolean isSourceEnum(TreePath methodPath, TypeElement owner) {
    var ownerPath = methodPath.getParentPath();
    return ownerPath != null && ownerPath.getLeaf() instanceof ClassTree declaration && declaration.getKind() == Tree.Kind.ENUM
        && JavaProjectionIdentity.sameInstance(semantics.element(ownerPath), owner)
        && JavaProjectionIdentity.sameInstance(semantics.sourceTree(owner), declaration);
  }

  private Optional<List<JavaTypeIdentity>> enumSuperclassInputs(TypeElement enumOwner) {
    if (!(enumOwner.getSuperclass() instanceof DeclaredType superclass) || !(superclass.asElement() instanceof TypeElement owner)
        || !owner.getQualifiedName().contentEquals("java.lang.Enum")) {
      return Optional.empty();
    }
    for (var member : owner.getEnclosedElements()) {
      if (member instanceof ExecutableElement constructor && constructor.getKind() == ElementKind.CONSTRUCTOR && !constructor.isVarArgs()
          && constructor.getParameters().size() == 2) {
        var parameters = constructor.getParameters().stream().map(parameter -> semantics.typeIdentity(parameter.asType())).toList();
        if (parameters.getFirst().category() == JavaTypeCategory.DECLARED && "java.lang.String".equals(parameters.getFirst().erasedName())
            && parameters.getLast().category() == JavaTypeCategory.INT && "int".equals(parameters.getLast().erasedName())) {
          return Optional.of(parameters);
        }
      }
    }
    return Optional.empty();
  }

  private boolean selectedParametersMatch(TreePath path, MethodInvocationTree call, ExecutableElement selected,
      List<JavaTypeIdentity> expected) {
    var mirror = semantics.attributedMirror(new TreePath(path, call.getMethodSelect()));
    if (!(mirror.orElse(null) instanceof ExecutableType executable)) {
      return false;
    }
    var declarationTypes = selected.getParameters().stream().map(parameter -> semantics.typeIdentity(parameter.asType())).toList();
    var selectedTypes = executable.getParameterTypes().stream().map(semantics::typeIdentity).toList();
    return declarationTypes.equals(expected) && selectedTypes.equals(expected);
  }
}
