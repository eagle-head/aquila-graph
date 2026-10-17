package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import org.aquilagraph.frontend.JavaExecutableSyntaxProjector.NodeProjection;
import org.aquilagraph.frontend.JavaExecutableSyntaxProjector.Projection;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

/** Copies constructor selection and insertion facts from exact live bodies and public declarations. */
final class JavaConstructorEvidenceProducer {
  private final SemanticResolver semantics;

  JavaConstructorEvidenceProducer(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  Optional<ConstructorEvidence> produce(Projection evidence, SyntaxNode node) {
    evidence.requireSemantics(semantics);
    var projection = evidence.binding(node);
    var methodKey = evidence.scopeKey();
    var root = evidence.translation().value();
    if (node.syntaxKind() != SyntaxKind.INVOCATION || !(node.value() instanceof SyntaxValue.Invocation invocation)
        || !(projection.path().getLeaf() instanceof MethodInvocationTree call)
        || !(call.getMethodSelect() instanceof IdentifierTree selection)
        || (invocation.form() != InvocationForm.SUPER_CONSTRUCTOR && invocation.form() != InvocationForm.THIS_CONSTRUCTOR)) {
      return Optional.empty();
    }
    var methodPath = containingConstructor(root, projection);
    if (methodPath.isEmpty() || !(semantics.element(methodPath.orElseThrow()) instanceof ExecutableElement containing)
        || containing.getKind() != ElementKind.CONSTRUCTOR || !semantics.declarationKey(containing).equals(Optional.of(methodKey))
        || !(containing.getEnclosingElement() instanceof TypeElement enumOwner) || !isSourceType(methodPath.orElseThrow(), enumOwner)
        || !(semantics.element(projection.path()) instanceof ExecutableElement selected) || selected.getKind() != ElementKind.CONSTRUCTOR) {
      return Optional.empty();
    }
    var ownerKey = semantics.declarationKey(enumOwner);
    var selectedKey = semantics.declarationKey(selected);
    var enumConstructor = enumOwner.getKind() == ElementKind.ENUM;
    if (!enumConstructor && invocation.form() != InvocationForm.SUPER_CONSTRUCTOR) {
      return Optional.empty();
    }
    var inputs = enumConstructor
        ? enumSuperclassInputs(enumOwner)
        : Optional.of(selected.getParameters().stream().map(parameter -> semantics.typeIdentity(parameter.asType())).toList());
    var reference = node.semanticReference();
    if (ownerKey.isEmpty() || selectedKey.isEmpty() || inputs.isEmpty() || reference.isEmpty()
        || reference.orElseThrow().referenceKind() != ReferenceKind.CONSTRUCTOR
        || reference.orElseThrow().status() != ResolutionStatus.RESOLVED || !reference.orElseThrow().symbolKey().equals(selectedKey)) {
      return Optional.empty();
    }
    if (invocation.form() == InvocationForm.SUPER_CONSTRUCTOR) {
      if (!selection.getName().contentEquals("super") || !call.getArguments().isEmpty() || !node.children(SyntaxRole.ARGUMENT).isEmpty()
          || node.origin().isPresent() || projection.sourceAssociation().isPresent()
          || !(selected.getEnclosingElement() instanceof TypeElement superclass)
          || (enumConstructor && (!superclass.getQualifiedName().contentEquals("java.lang.Enum") || selected.isVarArgs()
              || !selected.getTypeParameters().isEmpty()))
          || !selectedParametersMatch(projection.path(), call, selected, inputs.orElseThrow())) {
        return Optional.empty();
      }
    } else {
      if (!selection.getName().contentEquals("this") || !JavaProjectionIdentity.sameInstance(selected.getEnclosingElement(), enumOwner)) {
        return Optional.empty();
      }
    }
    var inputTypes = inputs.orElseThrow();
    return Optional.of(ConstructorEvidence.known(methodKey, node, ownerKey.orElseThrow(), selectedKey.orElseThrow(), inputTypes));
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

  private boolean isSourceType(TreePath methodPath, TypeElement owner) {
    var ownerPath = methodPath.getParentPath();
    return ownerPath != null && ownerPath.getLeaf() instanceof ClassTree declaration
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
