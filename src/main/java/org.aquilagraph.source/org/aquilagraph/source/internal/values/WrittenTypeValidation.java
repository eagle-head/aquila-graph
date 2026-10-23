package org.aquilagraph.source.internal.values;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.SyntaxChild;

/** Checks agreement of local written type components and selected type values. */
public final class WrittenTypeValidation {
  private WrittenTypeValidation() {
  }

  /**
   * Requires a selected type structure to agree with its written components.
   *
   * @param written
   *          written
   * @param selected
   *          selected
   */
  public static void validateKnownTypeUseStructure(SyntaxNode written, JavaTypeStructure selected) {
    if (written.type().knownIdentity().filter(identity -> identity.category() != selected.category()).isPresent()
        || !matchesWrittenNominalCategory(written, selected.category(), false)
        || (written.type() instanceof SyntaxType.Resolved resolved && resolved.structure().isPresent()
            && !resolved.structure().orElseThrow().equals(selected))
        || (written.value() instanceof SyntaxValue.PrimitiveType primitive
            && !new JavaTypeStructure.Primitive(primitive.kind()).equals(selected))
        || written.semanticReference().filter(reference -> reference.status() == ResolutionStatus.RESOLVED)
            .filter(reference -> switch (reference.referenceKind()) {
              case TYPE -> typeUseDeclaration(selected)
                  .filter(declaration -> reference.symbolKey().equals(Optional.of(declaration.declarationKey()))).isEmpty();
              case TYPE_PARAMETER -> !(selected instanceof JavaTypeStructure.Variable variable)
                  || !reference.symbolKey().equals(Optional.of(variable.parameterKey()));
              default -> false;
            }).isPresent()) {
      throw new IllegalArgumentException("A selected type must agree with its known written components");
    }
    if (written.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      validateKnownTypeUseStructure(written.requiredChild(SyntaxRole.UNDERLYING_TYPE), selected);
    } else if (written.sourceKind() == JavaSourceKind.ARRAY_TYPE) {
      if (!(selected instanceof JavaTypeStructure.Array array)) {
        throw new IllegalArgumentException("A selected type must agree with its known written components");
      }
      validateKnownTypeUseStructure(written.requiredChild(SyntaxRole.COMPONENT_TYPE), array.component());
    } else if (written.sourceKind() == JavaSourceKind.PARAMETERIZED_TYPE) {
      var declared = typeUseDeclaration(selected)
          .orElseThrow(() -> new IllegalArgumentException("A parameterized written type requires declared selection components"));
      var arguments = written.children(SyntaxRole.TYPE_ARGUMENT);
      if (!(written.value() instanceof SyntaxValue.DiamondType) && arguments.size() != declared.arguments().size()) {
        throw new IllegalArgumentException("Known selected type arguments must match written argument count");
      }
      for (var index = 0; index < arguments.size(); index++) {
        validateKnownTypeUseStructure(arguments.get(index), declared.arguments().get(index));
      }
      var name = written.requiredChild(SyntaxRole.TYPE_NAME);
      if (name.semanticReference().filter(reference -> reference.status() == ResolutionStatus.RESOLVED
          && !reference.symbolKey().equals(Optional.of(declared.declarationKey()))).isPresent()) {
        throw new IllegalArgumentException("Known selected type must match its written type name");
      }
    } else if (Set.of(JavaSourceKind.UNBOUNDED_WILDCARD, JavaSourceKind.EXTENDS_WILDCARD, JavaSourceKind.SUPER_WILDCARD)
        .contains(written.sourceKind())) {
      var kind = switch (written.sourceKind()) {
        case UNBOUNDED_WILDCARD -> JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED;
        case EXTENDS_WILDCARD -> JavaTypeStructure.Wildcard.BoundKind.EXTENDS;
        case SUPER_WILDCARD -> JavaTypeStructure.Wildcard.BoundKind.SUPER;
        default -> throw new IllegalStateException("Expected a written wildcard");
      };
      if (!(selected instanceof JavaTypeStructure.Wildcard wildcard) || wildcard.kind() != kind
          || wildcard.bound().isPresent() != (kind != JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED)) {
        throw new IllegalArgumentException("A selected wildcard must agree with its written bound form");
      }
      var role = switch (written.sourceKind()) {
        case EXTENDS_WILDCARD -> SyntaxRole.UPPER_BOUND;
        case SUPER_WILDCARD -> SyntaxRole.LOWER_BOUND;
        default -> null;
      };
      if (role != null) {
        validateKnownTypeUseStructure(written.requiredChild(role), wildcard.bound().orElseThrow());
      }
    }
  }

  /**
   * Requires the selected signature type to agree with its written occurrence.
   *
   * @param written
   *          written
   * @param selected
   *          selected
   */
  public static void validateKnownTypeUseType(SyntaxNode written, SyntaxType selected) {
    if (!matchingResolvedType(written.type(), selected)) {
      throw new IllegalArgumentException("A selected signature type must agree with its known written type");
    }
    if (!matchesWrittenArrayDimensions(written.sourceKind(), written.children(), selected)) {
      throw new IllegalArgumentException("A selected type must agree with its known written components");
    }
    if (selected instanceof SyntaxType.Resolved resolved) {
      resolved.structure().ifPresent(structure -> validateKnownTypeUseStructure(written, structure));
    }
  }

  /**
   * Requires the selected erasure to agree with the written type form.
   *
   * @param written
   *          written
   * @param erased
   *          erased
   */
  public static void validateKnownTypeUseErasure(SyntaxNode written, SyntaxType erased) {
    ExecutableTypeValidation.validateKnown(written.type(), erased);
    var category = erased.knownIdentity().orElseThrow().category();
    var matches = switch (written.sourceKind()) {
      case PRIMITIVE_TYPE -> category == written.value(SyntaxValue.PrimitiveType.class).kind();
      case ARRAY_TYPE -> category == JavaTypeCategory.ARRAY;
      case PARAMETERIZED_TYPE, IDENTIFIER, MEMBER_SELECT -> category == JavaTypeCategory.DECLARED;
      default -> true;
    };
    if (!matches || !matchesWrittenArrayDimensions(written.sourceKind(), written.children(), erased)) {
      throw new IllegalArgumentException("A selected erasure must agree with its known written type form");
    }
    if (written.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      validateKnownTypeUseErasure(written.requiredChild(SyntaxRole.UNDERLYING_TYPE), erased);
    } else if (erased instanceof SyntaxType.Resolved resolved) {
      resolved.structure().ifPresent(structure -> validateWrittenErasedStructure(written, structure));
    }
  }

  /**
   * Checks the known written and erased array ranks.
   *
   * @param sourceKind
   *          source kind
   * @param children
   *          children
   * @param selected
   *          selected
   * @return whether the local invariant holds
   */
  public static boolean matchesWrittenArrayDimensions(JavaSourceKind sourceKind, List<SyntaxChild> children, SyntaxType selected) {
    return selected.knownIdentity()
        .filter(identity -> identity.category() != JavaTypeCategory.ERROR && identity.category() != JavaTypeCategory.OTHER)
        .map(identity -> writtenArrayDimensions(sourceKind, children)
            .map(dimensions -> dimensions == ExecutableTypeValidation.erasedArrayDimensions(identity.erasedName())).orElse(true))
        .orElse(true);
  }

  private static Optional<Integer> writtenArrayDimensions(JavaSourceKind sourceKind, List<SyntaxChild> children) {
    return switch (sourceKind) {
      case ARRAY_TYPE, ANNOTATED_TYPE -> {
        var role = sourceKind == JavaSourceKind.ARRAY_TYPE ? SyntaxRole.COMPONENT_TYPE : SyntaxRole.UNDERLYING_TYPE;
        var component = children.stream().filter(child -> child.role() == role).findFirst().orElseThrow().node();
        var dimensions = writtenArrayDimensions(component.sourceKind(), component.children());
        yield sourceKind == JavaSourceKind.ARRAY_TYPE ? dimensions.map(value -> value + 1) : dimensions;
      }
      case PRIMITIVE_TYPE, PARAMETERIZED_TYPE, IDENTIFIER, MEMBER_SELECT -> Optional.of(0);
      default -> Optional.empty();
    };
  }

  private static void validateWrittenErasedStructure(SyntaxNode written, JavaTypeStructure erased) {
    if (written.sourceKind() == JavaSourceKind.ARRAY_TYPE) {
      if (!(erased instanceof JavaTypeStructure.Array array)) {
        throw new IllegalArgumentException("A selected erasure must agree with its known written type form");
      }
      validateWrittenErasedStructure(written.requiredChild(SyntaxRole.COMPONENT_TYPE), array.component());
    } else if (written.sourceKind() == JavaSourceKind.PARAMETERIZED_TYPE) {
      validateWrittenErasedStructure(written.requiredChild(SyntaxRole.TYPE_NAME), erased);
    } else if (written.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      validateWrittenErasedStructure(written.requiredChild(SyntaxRole.UNDERLYING_TYPE), erased);
    } else if (!matchesWrittenNominalCategory(written, erased.category(), true)
        || (written.value() instanceof SyntaxValue.PrimitiveType primitive
            && !new JavaTypeStructure.Primitive(primitive.kind()).equals(erased))
        || written.semanticReference()
            .filter(reference -> reference.status() == ResolutionStatus.RESOLVED && reference.referenceKind() == ReferenceKind.TYPE)
            .filter(reference -> typeUseDeclaration(erased)
                .filter(declaration -> reference.symbolKey().equals(Optional.of(declaration.declarationKey()))).isEmpty())
            .isPresent()) {
      throw new IllegalArgumentException("A selected erasure must agree with its known written type form");
    }
  }

  private static boolean matchesWrittenNominalCategory(SyntaxNode written, JavaTypeCategory category, boolean erased) {
    return switch (written.sourceKind()) {
      case IDENTIFIER -> category == JavaTypeCategory.DECLARED || (!erased && category == JavaTypeCategory.TYPE_VARIABLE);
      case MEMBER_SELECT -> category == JavaTypeCategory.DECLARED;
      default -> true;
    };
  }

  /**
   * Extracts nominal declaration components from a declared or local type.
   *
   * @param structure
   *          structure
   * @return the extracted local type components
   */
  public static Optional<TypeUseDeclaration> typeUseDeclaration(JavaTypeStructure structure) {
    return switch (structure) {
      case JavaTypeStructure.Declared declared -> Optional.of(
          new TypeUseDeclaration(declared.declarationKey(), declared.lexicalOwnerKey(), declared.enclosingType(), declared.arguments()));
      case JavaTypeStructure.LocalDeclared local -> Optional.of(
          new TypeUseDeclaration(local.declarationKey(), Optional.of(local.lexicalOwnerKey()), local.enclosingType(), local.arguments()));
      default -> Optional.empty();
    };
  }

  /**
   * Compares available resolved identities and recursive type structures.
   *
   * @param written
   *          written
   * @param attributed
   *          attributed
   * @return whether the local invariant holds
   */
  public static boolean matchingResolvedType(SyntaxType written, SyntaxType attributed) {
    var actual = written.knownIdentity();
    var expected = attributed.knownIdentity();
    return (actual.isEmpty() || expected.isEmpty()
        || (actual.orElseThrow().category() == expected.orElseThrow().category()
            && actual.orElseThrow().erasedName().equals(expected.orElseThrow().erasedName())))
        && (!(written instanceof SyntaxType.Resolved resolved) || !(attributed instanceof SyntaxType.Resolved resolvedExpected)
            || resolved.structure().isEmpty() || resolvedExpected.structure().isEmpty()
            || resolved.structure().equals(resolvedExpected.structure()));
  }

  /**
   * Nominal components used by intrinsic written-type checks.
   *
   * @param declarationKey
   *          selected declaration identity
   * @param lexicalOwnerKey
   *          available lexical owner
   * @param enclosingType
   *          available enclosing type
   * @param arguments
   *          ordered selected type arguments
   */
  public record TypeUseDeclaration(String declarationKey, Optional<String> lexicalOwnerKey, Optional<JavaTypeStructure> enclosingType,
      List<JavaTypeStructure> arguments) {
    /**
     * Retains immutable ordered type arguments.
     */
    public TypeUseDeclaration {
      arguments = List.copyOf(arguments);
    }
  }
}
