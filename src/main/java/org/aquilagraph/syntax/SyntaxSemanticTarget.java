package org.aquilagraph.syntax;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;

/** Explicit project-owned endpoint of a semantic relation. */
public sealed interface SyntaxSemanticTarget {
  /** Exact type destination of one written annotation. */
  sealed interface AnnotationTypeLocation {
    /**
     * A source type-use occurrence, including its typed structural path.
     *
     * @param nodeKey
     *          source type-use node key
     */
    record Written(String nodeKey) implements AnnotationTypeLocation {
      /** Validates the source occurrence key. */
      public Written {
        SyntaxValueSupport.requireText(nodeKey, "annotation type-use nodeKey");
      }
    }

    /**
     * The constructed type of a source constructor declaration or enum constant.
     *
     * @param declarationKey
     *          owning type declaration key
     */
    record Constructed(String declarationKey) implements AnnotationTypeLocation {
      /** Validates the constructed type key. */
      public Constructed {
        SyntaxValueSupport.requireText(declarationKey, "constructed annotation declarationKey");
      }
    }
  }

  /**
   * One physical annotation attachment with its complete declaration and type applicability.
   *
   * @param ownerNodeKey
   *          source owner node key
   * @param declarationNodeKey
   *          applicable declaration node key, if any
   * @param typeLocation
   *          applicable type location, if any
   * @param propagatedCounterparts
   *          implicit record counterparts receiving declaration or type applicability
   * @param fieldDeclarators
   *          additional source declarators receiving this shared field-header annotation
   */
  record AnnotationApplication(String ownerNodeKey, Optional<String> declarationNodeKey, Optional<AnnotationTypeLocation> typeLocation,
      List<PropagatedCounterpart> propagatedCounterparts,
      List<FieldDeclaratorApplication> fieldDeclarators) implements SyntaxSemanticTarget {
    private static final int UNIQUE_TYPE_CHILD_COUNT = 1;
    /**
     * A further written field declarator receiving one shared physical annotation.
     *
     * @param fieldNodeKey
     *          written field declarator identity
     * @param declarationApplicable
     *          declaration-target applicability
     * @param typeLocation
     *          exact type context in this declarator
     */
    public record FieldDeclaratorApplication(String fieldNodeKey, boolean declarationApplicable,
        Optional<AnnotationTypeLocation> typeLocation) {
      /** Validates a nonempty source application. */
      public FieldDeclaratorApplication {
        SyntaxValueSupport.requireText(fieldNodeKey, "field declarator node key");
        Objects.requireNonNull(typeLocation, "typeLocation");
        if ((!declarationApplicable && typeLocation.isEmpty())
            || (typeLocation.isPresent() && !(typeLocation.orElseThrow() instanceof AnnotationTypeLocation.Written))) {
          throw new IllegalArgumentException("A field declarator application requires a declaration or written type destination");
        }
      }
    }
    /**
     * One compiler-correlated counterpart receiving a declaration annotation, a type annotation, or both.
     *
     * @param reference
     *          counterpart declaration identity
     * @param declarationApplicable
     *          declaration-target applicability
     * @param typeLocation
     *          source type position propagated into the counterpart's declared type
     */
    public record PropagatedCounterpart(SemanticReference reference, boolean declarationApplicable,
        Optional<AnnotationTypeLocation> typeLocation) {
      /** Validates a nonempty counterpart application. */
      public PropagatedCounterpart {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(typeLocation, "typeLocation");
        if (!declarationApplicable && typeLocation.isEmpty()) {
          throw new IllegalArgumentException("A propagated counterpart requires declaration or type applicability");
        }
        if (reference.status() != ResolutionStatus.RESOLVED || reference.symbolKey().isEmpty()
            || !Set.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER).contains(reference.referenceKind())
            || (typeLocation.isPresent() && !(typeLocation.orElseThrow() instanceof AnnotationTypeLocation.Written))) {
          throw new IllegalArgumentException("A propagated counterpart requires a resolved record member and written type position");
        }
      }
    }

    /** Validates the target's immutable source site and effective destinations. */
    public AnnotationApplication {
      SyntaxValueSupport.requireText(ownerNodeKey, "annotation ownerNodeKey");
      Objects.requireNonNull(declarationNodeKey, "declarationNodeKey")
          .ifPresent(key -> SyntaxValueSupport.requireText(key, "annotation declarationNodeKey"));
      Objects.requireNonNull(typeLocation, "typeLocation");
      propagatedCounterparts = List.copyOf(Objects.requireNonNull(propagatedCounterparts, "propagatedCounterparts"));
      fieldDeclarators = List.copyOf(Objects.requireNonNull(fieldDeclarators, "fieldDeclarators"));
      if (fieldDeclarators.stream().map(FieldDeclaratorApplication::fieldNodeKey).distinct().count() != fieldDeclarators.size()) {
        throw new IllegalArgumentException("A shared annotation requires distinct field declarator destinations");
      }
    }

    /**
     * Derives one exact application from the annotation's written owner and compiler-derived target kinds.
     *
     * @param parent
     *          annotation's structural owner
     * @param annotationNode
     *          written annotation node
     * @return exact application for the written annotation
     */
    public static AnnotationApplication forAttachment(SyntaxNode parent, SyntaxNode annotationNode) {
      return forAttachment(parent, annotationNode, Optional.empty());
    }

    /**
     * Derives an application with the enclosing record component for a written component type.
     *
     * @param parent
     *          annotation's structural owner
     * @param annotationNode
     *          written annotation node
     * @param recordComponent
     *          enclosing record component when the owner belongs to its type
     * @return exact application for the written annotation
     */
    public static AnnotationApplication forAttachment(SyntaxNode parent, SyntaxNode annotationNode, Optional<SyntaxNode> recordComponent) {
      return forAttachment(parent, annotationNode, recordComponent, List.of());
    }

    /**
     * Derives an application including all declarators of a shared source field header.
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
      var typeLocation = type ? Optional.of(typeLocation(parent)) : Optional.<AnnotationTypeLocation>empty();
      if (recordComponent.isPresent()) {
        var component = recordComponent.orElseThrow();
        if (parent.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION || !type
            || component.syntaxKind() != SyntaxKind.RECORD_COMPONENT_DECLARATION
            || !component.sourceFileKey().equals(parent.sourceFileKey())
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
        if (parent.syntaxKind() == SyntaxKind.FIELD_DECLARATION) {
          if (!field.equals(parent)) {
            fieldApplications.add(
                new FieldDeclaratorApplication(field.nodeId(), declaration, type ? Optional.of(typeLocation(field)) : Optional.empty()));
          }
        } else {
          var context = SharedFieldDeclarations.commonTypeContext(parent, field);
          if (context.isPresent()) {
            fieldApplications.add(new FieldDeclaratorApplication(field.nodeId(), false,
                Optional.of(new AnnotationTypeLocation.Written(context.orElseThrow().nodeId()))));
          }
        }
      }
      return new AnnotationApplication(parent.nodeId(), declarationNode, typeLocation, propagated, fieldApplications);
    }

    private static boolean recordTypeContains(SyntaxNode type, SyntaxNode target) {
      return type.equals(target) || type.children().stream().map(SyntaxChild::node)
          .filter(child -> child.syntaxKind() == SyntaxKind.TYPE_USE).anyMatch(child -> recordTypeContains(child, target));
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
              || kind == SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR
                  ? ElementType.CONSTRUCTOR
                  : ElementType.METHOD);
        }
        case FIELD_DECLARATION, ENUM_CONSTANT_DECLARATION -> targets.contains(ElementType.FIELD);
        case PARAMETER_DECLARATION -> targets.contains(ElementType.PARAMETER);
        case RECORD_COMPONENT_DECLARATION -> targets.contains(ElementType.RECORD_COMPONENT) || targets.contains(ElementType.FIELD)
            || targets.contains(ElementType.METHOD) || targets.contains(ElementType.PARAMETER);
        default -> false;
      };
    }

    private static boolean supportsClosestType(SyntaxNode parent, Set<ElementType> targets) {
      return switch (parent.syntaxKind()) {
        case FIELD_DECLARATION, ENUM_CONSTANT_DECLARATION, RECORD_COMPONENT_DECLARATION, PARAMETER_DECLARATION, RECEIVER_PARAMETER ->
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

    private static Optional<AnnotationTypeLocation> closestTypeLocation(SyntaxNode parent) {
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

    private static List<PropagatedCounterpart> recordCounterparts(SyntaxValue.RecordComponentDeclaration component,
        Set<ElementType> targets, Optional<AnnotationTypeLocation> typeLocation) {
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

  /**
   * A source occurrence owned by the same durable AST.
   *
   * @param nodeKey
   *          target source-occurrence key
   */
  record SourceOccurrence(String nodeKey) implements SyntaxSemanticTarget {
    /** Validates the target occurrence key. */
    public SourceOccurrence {
      Objects.requireNonNull(nodeKey, "nodeKey");
      if (nodeKey.isBlank()) {
        throw new IllegalArgumentException("A semantic source-occurrence target key cannot be blank");
      }
    }
  }

  /**
   * An explicit compiler-derived declaration reference, including external declarations.
   *
   * @param reference
   *          project-owned reference value
   */
  record Declaration(SemanticReference reference) implements SyntaxSemanticTarget {
    /** Validates the declaration reference. */
    public Declaration {
      Objects.requireNonNull(reference, "reference");
    }
  }

}
