package org.aquilagraph.source.internal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.source.api.JavaDeclarationModifier;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

/** Source-established variable member scope along exact lexical and type attachments. */
final class SourceMemberVisibility {
  private final SourceDeclarations declarations;
  private final OccurrenceIndex<SyntaxNode> parents;

  SourceMemberVisibility(SourceDeclarations declarations, OccurrenceIndex<SyntaxNode> parents) {
    this.declarations = declarations;
    this.parents = parents;
  }

  boolean hasSelectorEnumConstant(SyntaxNode access) {
    var switchCase = parents.get(access);
    if (switchCase == null || switchCase.syntaxKind() != SyntaxKind.CASE
        || access.structuralPath().steps().getLast().role() != SyntaxRole.LABEL) {
      return false;
    }
    var selection = parents.get(switchCase);
    if (selection == null) {
      return false;
    }
    var name = access.value(SyntaxValue.SourceSymbol.class).name();
    return selection.optionalChild(SyntaxRole.SELECTOR).map(SyntaxNode::type).flatMap(SourceMemberVisibility::nominalDeclarationKey)
        .flatMap(declarations::uniqueType).filter(type -> type.sourceKind() == JavaSourceKind.ENUM).stream()
        .flatMap(type -> type.children(SyntaxRole.MEMBER).stream())
        .filter(member -> member.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION)
        .anyMatch(member -> member.value(SyntaxValue.VariableDeclaration.class).name().equals(name));
  }

  private static Optional<String> nominalDeclarationKey(SyntaxType type) {
    if (!(type instanceof SyntaxType.Resolved resolved)) {
      return Optional.empty();
    }
    return switch (resolved.structure().orElse(null)) {
      case JavaTypeStructure.Declared declared -> Optional.of(declared.declarationKey());
      case JavaTypeStructure.LocalDeclared local -> Optional.of(local.declarationKey());
      case null, default -> Optional.empty();
    };
  }

  boolean shadowsCapture(SyntaxNode access, SyntaxNode bindingExecution, String name) {
    var child = access;
    for (var parent = parents.get(child); parent != null; parent = parents.get(child)) {
      if (OccurrenceIndex.same(parent, bindingExecution)) {
        return false;
      }
      var role = child.structuralPath().steps().getLast().role();
      if (parent.syntaxKind() == SyntaxKind.TYPE_DECLARATION && (role == SyntaxRole.MEMBER || role == SyntaxRole.RECORD_COMPONENT)
          && hasFieldInScope(parent, name)) {
        return true;
      }
      child = parent;
    }
    return false;
  }

  private boolean hasFieldInScope(SyntaxNode type, String name) {
    if (declaredField(type, name).isPresent()) {
      return true;
    }
    var pending = new ArrayDeque<InheritedType>();
    addSupertypes(new InheritedType(type, true), pending);
    var visited = new HashSet<HierarchyVisit>();
    while (!pending.isEmpty()) {
      var candidate = pending.removeFirst();
      if (visited.add(new HierarchyVisit(candidate.type().nodeId(), candidate.packageAccess())) && visibleField(candidate, name, pending)) {
        return true;
      }
    }
    return false;
  }

  private boolean visibleField(InheritedType candidate, String name, Deque<InheritedType> pending) {
    var field = declaredField(candidate.type(), name);
    if (field.isPresent()) {
      // A declaration hides more distant fields on this branch even when it cannot itself be inherited.
      return switch (field.orElseThrow()) {
        case PUBLIC_OR_PROTECTED -> true;
        case PACKAGE -> candidate.packageAccess();
        case PRIVATE -> false;
      };
    }
    addSupertypes(candidate, pending);
    return false;
  }

  private void addSupertypes(InheritedType candidate, Deque<InheritedType> pending) {
    var type = candidate.type();
    directSourceSupers(type).forEach(
        supertype -> pending.addLast(new InheritedType(supertype, candidate.packageAccess() && declarations.samePackage(type, supertype))));
  }

  private List<SyntaxNode> directSourceSupers(SyntaxNode type) {
    var owner = parents.get(type);
    if (owner != null && type.structuralPath().steps().getLast().role() == SyntaxRole.CLASS_BODY) {
      return switch (owner.syntaxKind()) {
        case OBJECT_CREATION -> owner.optionalChild(SyntaxRole.TYPE_OPERAND).flatMap(this::selectedType).stream().toList();
        case ENUM_CONSTANT_DECLARATION -> Optional.ofNullable(parents.get(owner))
            .filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION && node.sourceKind() == JavaSourceKind.ENUM).stream().toList();
        default -> List.of();
      };
    }
    return type.children().stream().filter(child -> child.role() == SyntaxRole.SUPERCLASS || child.role() == SyntaxRole.INTERFACE)
        .flatMap(child -> selectedType(child.node())
            .filter(
                supertype -> child.role() == SyntaxRole.INTERFACE ? isInterface(supertype) : supertype.sourceKind() == JavaSourceKind.CLASS)
            .stream())
        .toList();
  }

  private Optional<SyntaxNode> selectedType(SyntaxNode typeUse) {
    // Unresolved, ambiguous or uncaptured declarations contribute no inherited-field evidence.
    return typeUse.semanticReference()
        .filter(reference -> reference.referenceKind() == ReferenceKind.TYPE && reference.status() == ResolutionStatus.RESOLVED)
        .flatMap(SemanticReference::symbolKey).flatMap(declarations::uniqueType);
  }

  private static Optional<FieldAccess> declaredField(SyntaxNode type, String name) {
    var field = type.children(SyntaxRole.MEMBER).stream().map(SyntaxNode::value).filter(SyntaxValue.VariableDeclaration.class::isInstance)
        .map(SyntaxValue.VariableDeclaration.class::cast).filter(member -> member.name().equals(name)).findFirst();
    if (field.isPresent()) {
      return Optional.of(accessLevel(type, field.orElseThrow()));
    }
    return type.children(SyntaxRole.RECORD_COMPONENT).stream().map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class))
        .anyMatch(component -> component.name().equals(name)) ? Optional.of(FieldAccess.PRIVATE) : Optional.empty();
  }

  private static FieldAccess accessLevel(SyntaxNode type, SyntaxValue.VariableDeclaration member) {
    var modifiers = member.effectiveModifiers();
    if (modifiers.contains(JavaDeclarationModifier.PRIVATE)) {
      return FieldAccess.PRIVATE;
    }
    if (modifiers.contains(JavaDeclarationModifier.PUBLIC) || modifiers.contains(JavaDeclarationModifier.PROTECTED) || isInterface(type)
        || member.kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT) {
      return FieldAccess.PUBLIC_OR_PROTECTED;
    }
    return FieldAccess.PACKAGE;
  }

  private static boolean isInterface(SyntaxNode type) {
    return type.sourceKind() == JavaSourceKind.INTERFACE || type.sourceKind() == JavaSourceKind.ANNOTATION_TYPE;
  }

  private enum FieldAccess {
    PUBLIC_OR_PROTECTED, PACKAGE, PRIVATE
  }

  private record InheritedType(SyntaxNode type, boolean packageAccess) {
  }

  private record HierarchyVisit(String typeOccurrence, boolean packageAccess) {
  }
}
