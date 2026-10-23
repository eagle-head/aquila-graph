package org.aquilagraph.source.admission;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationTypeLocation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication.FieldDeclaratorApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication.PropagatedCounterpart;
/** Derives complete annotation destinations from written owners and copied target evidence. */
public final class AnnotationAttachmentAdmission {
  private static final int UNIQUE_TYPE_CHILD_COUNT = 1;
  private AnnotationAttachmentAdmission() {
  }

  /**
   * Derives an application including all declarators of a shared source field header. Requires every applicable declaration and
   * closest-type destination at the written site.
   *
   * @param parent
   *          annotation's structural owner
   * @param annotationNode
   *          written annotation node
   * @param recordComponent
   *          enclosing record component, when applicable
   * @param sharedFields
   *          complete validated field group in source order
   * @return exact application including additional written destinations
   */
  public static AnnotationApplication forAttachment(SyntaxNode parent, SyntaxNode annotationNode, Optional<SyntaxNode> recordComponent,
      List<SyntaxNode> sharedFields) {
    Objects.requireNonNull(parent, "parent");
    Objects.requireNonNull(annotationNode, "annotationNode");
    Objects.requireNonNull(recordComponent, "recordComponent");
    Objects.requireNonNull(sharedFields, "sharedFields");
    if (parent.value() instanceof SyntaxValue.VariableDeclaration field && !field.sharedDeclaratorKeys().isEmpty()
        && sharedFields.isEmpty()) {
      throw new IllegalArgumentException("A shared field annotation requires its complete declarator group");
    }
    if (parent.children(SyntaxRole.ANNOTATION).stream().noneMatch(node -> node.nodeId().equals(annotationNode.nodeId()))
        || !(annotationNode.value() instanceof SyntaxValue.Annotation annotation)) {
      throw new IllegalArgumentException("An annotation application requires its identical structural owner");
    }
    var targets = annotation.applicableTargets();
    var location = annotation.location();
    var declaration = location == SyntaxValue.AnnotationLocation.DECLARATION || location == SyntaxValue.AnnotationLocation.DUAL;
    var type = location == SyntaxValue.AnnotationLocation.TYPE_USE || location == SyntaxValue.AnnotationLocation.DUAL;
    if (!declaration && !type) {
      throw new IllegalArgumentException("An unresolved or nested value annotation has no application target");
    }
    if (declaration && !declarationApplicable(parent, targets)) {
      throw new IllegalArgumentException("The annotation cannot apply to its source declaration");
    }
    if (!declaration && declarationApplicable(parent, targets)) {
      throw new IllegalArgumentException("An annotation cannot omit its applicable source declaration");
    }
    if (type && !targets.contains(ElementType.TYPE_USE)) {
      throw new IllegalArgumentException("The annotation interface is not applicable to a type use");
    }
    if (!type && supportsClosestType(parent, targets) && closestTypeLocation(parent).isPresent()) {
      throw new IllegalArgumentException("An annotation cannot omit its closest admissible source type");
    }
    if (type != (parent.syntaxKind() == SyntaxKind.TYPE_USE
        || (supportsClosestType(parent, targets) && annotation.closestTypeAdmissible()))) {
      throw new IllegalArgumentException("Annotation type applicability disagrees with the nearest source type");
    }
    var declarationNode = declaration
        && (parent.syntaxKind() != SyntaxKind.RECORD_COMPONENT_DECLARATION || targets.contains(ElementType.RECORD_COMPONENT))
            ? Optional.of(parent.nodeId())
            : Optional.<String>empty();
    var typeLocation = type ? Optional.of(typeLocation(parent, annotationNode)) : Optional.<AnnotationTypeLocation>empty();
    if (recordComponent.isPresent()) {
      var component = recordComponent.orElseThrow();
      if (parent.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION || !type
          || component.syntaxKind() != SyntaxKind.RECORD_COMPONENT_DECLARATION || !component.sourceFileKey().equals(parent.sourceFileKey())
          || component.children(SyntaxRole.DECLARED_TYPE).stream().noneMatch(root -> recordTypeContains(root, parent))) {
        throw new IllegalArgumentException("Record type propagation requires its identical written component type");
      }
    }
    var propagated = parent.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION
        ? recordCounterparts(parent.value(SyntaxValue.RecordComponentDeclaration.class), targets, typeLocation)
        : recordComponent
            .map(component -> recordCounterparts(component.value(SyntaxValue.RecordComponentDeclaration.class), Set.of(), typeLocation))
            .orElseGet(List::of);
    if (declarationNode.isEmpty() && typeLocation.isEmpty() && propagated.isEmpty()
        && parent.syntaxKind() != SyntaxKind.RECORD_COMPONENT_DECLARATION) {
      throw new IllegalArgumentException("An annotation application requires a declaration or type destination");
    }
    var fieldApplications = new ArrayList<FieldDeclaratorApplication>();
    for (var field : sharedFields) {
      if (parent.syntaxKind() == SyntaxKind.FIELD_DECLARATION || parent.syntaxKind() == SyntaxKind.LOCAL_DECLARATION) {
        if (!field.equals(parent)) {
          fieldApplications
              .add(new FieldDeclaratorApplication(field.nodeId(), declaration, type ? Optional.of(typeLocation(field)) : Optional.empty()));
        }
      } else {
        var context = SharedVariableDeclarations.commonTypeContext(parent, field);
        if (context.isPresent()) {
          fieldApplications.add(new FieldDeclaratorApplication(field.nodeId(), false,
              Optional.of(new AnnotationTypeLocation.Written(context.orElseThrow().nodeId()))));
        }
      }
    }
    return new AnnotationApplication(parent.nodeId(), declarationNode, typeLocation, propagated, fieldApplications);
  }

  private static boolean recordTypeContains(SyntaxNode type, SyntaxNode target) {
    return type.equals(target) || type.children().stream().map(SyntaxChild::node).filter(child -> child.syntaxKind() == SyntaxKind.TYPE_USE)
        .anyMatch(child -> recordTypeContains(child, target));
  }

  private static boolean declarationApplicable(SyntaxNode parent, Set<ElementType> targets) {
    return switch (parent.syntaxKind()) {
      case COMPILATION_UNIT -> switch (parent.sourceKind()) {
        case PACKAGE -> targets.contains(ElementType.PACKAGE);
        case MODULE -> targets.contains(ElementType.MODULE);
        default -> false;
      };
      case TYPE_DECLARATION -> targets.contains(ElementType.TYPE) || targets.contains(ElementType.TYPE_USE)
          || (parent.sourceKind() == JavaSourceKind.ANNOTATION_TYPE && targets.contains(ElementType.ANNOTATION_TYPE));
      case TYPE_PARAMETER_DECLARATION -> targets.contains(ElementType.TYPE_PARAMETER) || targets.contains(ElementType.TYPE_USE);
      case METHOD_DECLARATION -> {
        var kind = parent.value(SyntaxValue.ExecutableDeclaration.class).kind();
        yield targets.contains(kind == SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR
            || kind == SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR ? ElementType.CONSTRUCTOR : ElementType.METHOD);
      }
      case FIELD_DECLARATION, ENUM_CONSTANT_DECLARATION -> targets.contains(ElementType.FIELD);
      case LOCAL_DECLARATION -> targets.contains(parent.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().filter(
          binding -> binding.context() == LocalBindingEvidence.Context.CATCH || binding.context() == LocalBindingEvidence.Context.LAMBDA)
          .isPresent() ? ElementType.PARAMETER : ElementType.LOCAL_VARIABLE);
      case PARAMETER_DECLARATION -> targets.contains(ElementType.PARAMETER);
      case RECORD_COMPONENT_DECLARATION -> targets.contains(ElementType.RECORD_COMPONENT) || targets.contains(ElementType.FIELD)
          || targets.contains(ElementType.METHOD) || targets.contains(ElementType.PARAMETER);
      default -> false;
    };
  }

  private static boolean supportsClosestType(SyntaxNode parent, Set<ElementType> targets) {
    return switch (parent.syntaxKind()) {
      case LOCAL_DECLARATION -> targets.contains(ElementType.TYPE_USE) && !parent.children(SyntaxRole.DECLARED_TYPE).isEmpty();
      case ARRAY_CREATION, FIELD_DECLARATION, ENUM_CONSTANT_DECLARATION, RECORD_COMPONENT_DECLARATION, PARAMETER_DECLARATION,
          RECEIVER_PARAMETER ->
        targets.contains(ElementType.TYPE_USE);
      case METHOD_DECLARATION -> targets.contains(ElementType.TYPE_USE)
          && (parent.value(SyntaxValue.ExecutableDeclaration.class).kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR
              || parent.value(SyntaxValue.ExecutableDeclaration.class)
                  .kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR
              || parent.children(SyntaxRole.RETURN_TYPE).stream().anyMatch(node -> node.sourceKind() != JavaSourceKind.PRIMITIVE_TYPE
                  || !(node.value() instanceof SyntaxValue.PrimitiveType primitive) || primitive.kind() != JavaTypeCategory.VOID));
      default -> false;
    };
  }

  private static AnnotationTypeLocation typeLocation(SyntaxNode parent) {
    return closestTypeLocation(parent).orElseThrow(
        () -> new IllegalArgumentException("An annotation cannot apply to a package qualifier or through a static type qualifier"));
  }

  private static AnnotationTypeLocation typeLocation(SyntaxNode parent, SyntaxNode annotation) {
    if (parent.syntaxKind() != SyntaxKind.ARRAY_CREATION) {
      return typeLocation(parent);
    }
    var position = parent.children().stream()
        .filter(child -> child.role() == SyntaxRole.ANNOTATION && child.node().nodeId().equals(annotation.nodeId())).findFirst()
        .orElseThrow().position();
    var groups = parent.value(SyntaxValue.ArrayCreation.class).dimensionAnnotationCounts();
    var offset = 0;
    for (var dimension = 0; dimension < groups.size(); dimension++) {
      offset += groups.get(dimension);
      if (position < offset) {
        return new AnnotationTypeLocation.ArrayDimension(parent.nodeId(), dimension);
      }
    }
    throw new IllegalArgumentException("An array annotation requires its written dimension group");
  }

  private static Optional<AnnotationTypeLocation> closestTypeLocation(SyntaxNode parent) {
    if (parent.syntaxKind() == SyntaxKind.ARRAY_CREATION) {
      return Optional.of(new AnnotationTypeLocation.ArrayDimension(parent.nodeId(), 0));
    }
    if (parent.syntaxKind() == SyntaxKind.TYPE_USE) {
      return Optional.of(new AnnotationTypeLocation.Written(parent.nodeId()));
    }
    if (parent.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION) {
      return Optional.of(new AnnotationTypeLocation.Constructed(parent.value(SyntaxValue.VariableDeclaration.class).ownerKey()));
    }
    if (parent.syntaxKind() == SyntaxKind.METHOD_DECLARATION) {
      var method = parent.value(SyntaxValue.ExecutableDeclaration.class);
      if (method.kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR
          || method.kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR) {
        return Optional.of(new AnnotationTypeLocation.Constructed(method.ownerKey()));
      }
    }
    var role = parent.syntaxKind() == SyntaxKind.METHOD_DECLARATION ? SyntaxRole.RETURN_TYPE : SyntaxRole.DECLARED_TYPE;
    var roots = parent.children(role);
    if (roots.size() != UNIQUE_TYPE_CHILD_COUNT) {
      throw new IllegalArgumentException("An annotation has no unique written closest type");
    }
    var current = roots.getFirst();
    while (true) {
      var nextRole = switch (current.sourceKind()) {
        case ARRAY_TYPE -> SyntaxRole.COMPONENT_TYPE;
        case ANNOTATED_TYPE -> SyntaxRole.UNDERLYING_TYPE;
        case PARAMETERIZED_TYPE -> SyntaxRole.TYPE_NAME;
        case MEMBER_SELECT -> SyntaxRole.TYPE_QUALIFIER;
        default -> null;
      };
      if (nextRole == null) {
        break;
      }
      var next = current.children(nextRole);
      if (next.size() != UNIQUE_TYPE_CHILD_COUNT) {
        throw new IllegalArgumentException("An annotation has no complete written closest type");
      }
      if (current.sourceKind() == JavaSourceKind.MEMBER_SELECT && !(next.getFirst().value() instanceof SyntaxValue.PackageQualifier)
          && current.type() instanceof SyntaxType.Resolved resolved
          && resolved.structure().orElseThrow() instanceof JavaTypeStructure.Declared declared && declared.enclosingType().isEmpty()) {
        return Optional.empty();
      }
      current = next.getFirst();
    }
    if (current.value() instanceof SyntaxValue.PackageQualifier) {
      return Optional.empty();
    }
    return Optional.of(new AnnotationTypeLocation.Written(current.nodeId()));
  }

  private static List<PropagatedCounterpart> recordCounterparts(SyntaxValue.RecordComponentDeclaration component, Set<ElementType> targets,
      Optional<AnnotationTypeLocation> typeLocation) {
    var counterparts = component.counterparts();
    var result = new ArrayList<PropagatedCounterpart>();
    appendCounterpart(result, ReferenceKind.FIELD, counterparts.backingFieldKey(), targets.contains(ElementType.FIELD), typeLocation);
    if (counterparts.accessorImplicit()) {
      appendCounterpart(result, ReferenceKind.METHOD, counterparts.accessorKey(), targets.contains(ElementType.METHOD), typeLocation);
    }
    if (counterparts.canonicalParameterDerived()) {
      appendCounterpart(result, ReferenceKind.PARAMETER, counterparts.canonicalParameterKey(), targets.contains(ElementType.PARAMETER),
          typeLocation);
    }
    return List.copyOf(result);
  }

  private static void appendCounterpart(List<PropagatedCounterpart> result, ReferenceKind kind, String key, boolean declarationApplicable,
      Optional<AnnotationTypeLocation> typeLocation) {
    if (declarationApplicable || typeLocation.isPresent()) {
      result.add(new PropagatedCounterpart(reference(kind, key), declarationApplicable, typeLocation));
    }
  }

  private static SemanticReference reference(ReferenceKind kind, String key) {
    return new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(), List.of(), List.of());
  }
}
