package org.aquilagraph.source.internal.values;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeStructure;

/** Checks intrinsic executable type and erasure component invariants. */
public final class ExecutableTypeValidation {
  private ExecutableTypeValidation() {
  }

  private static final String ARRAY_SUFFIX = "[]";

  /**
   * Requires known semantic and erased components to agree.
   *
   * @param type
   *          type
   * @param erasedType
   *          erased type
   */
  public static void validateKnown(SyntaxType type, SyntaxType erasedType) {
    type.knownIdentity().ifPresent(identity -> validateIdentity(identity, erasedType));
    if (type instanceof SyntaxType.Resolved semantic && erasedType instanceof SyntaxType.Resolved erased && semantic.structure().isPresent()
        && erased.structure().isPresent() && !matchesErasure(semantic.structure().orElseThrow(), erased.structure().orElseThrow())) {
      throw new IllegalArgumentException("Executable erasure conflicts with a known signature component");
    }
  }

  /**
   * Requires the erased type to agree with its known signature identity.
   *
   * @param identity
   *          identity
   * @param erasedType
   *          erased type
   */
  public static void validateIdentity(JavaTypeIdentity identity, SyntaxType erasedType) {
    var erasure = erasedType.knownIdentity().orElseThrow();
    var compatibleCategory = identity.category() == JavaTypeCategory.TYPE_VARIABLE
        ? erasure.category() == JavaTypeCategory.DECLARED || unavailable(erasure)
        : identity.category() == erasure.category();
    if (!compatibleCategory || !identity.erasedName().equals(erasure.erasedName())) {
      throw new IllegalArgumentException("Executable erasure conflicts with a known signature component");
    }
  }

  private static boolean matchesErasure(JavaTypeStructure semantic, JavaTypeStructure erased) {
    return switch (semantic) {
      case JavaTypeStructure.Primitive primitive -> primitive.equals(erased);
      case JavaTypeStructure.Array array ->
        erased instanceof JavaTypeStructure.Array erasedArray && matchesErasure(array.component(), erasedArray.component());
      case JavaTypeStructure.Declared declared ->
        erased instanceof JavaTypeStructure.Declared erasedDeclared && declared.declarationKey().equals(erasedDeclared.declarationKey());
      case JavaTypeStructure.LocalDeclared local ->
        erased instanceof JavaTypeStructure.LocalDeclared erasedLocal && local.declarationKey().equals(erasedLocal.declarationKey());
      case JavaTypeStructure.Variable _,JavaTypeStructure.Captured _,JavaTypeStructure.CaptureReference _ ->
        erased.category() == JavaTypeCategory.DECLARED;
      default -> false;
    };
  }

  /**
   * Checks erasure recursively using available owned type-variable bounds.
   *
   * @param semantic
   *          semantic
   * @param erased
   *          erased
   * @param bounds
   *          bounds
   * @param visited
   *          visited
   * @return whether the local invariant holds
   */
  public static boolean matchesOwnedErasure(JavaTypeStructure semantic, JavaTypeStructure erased, Map<String, JavaTypeStructure> bounds,
      Set<String> visited) {
    return switch (semantic) {
      case JavaTypeStructure.Variable variable -> {
        var bound = bounds.get(variable.parameterKey());
        if (bound == null || !visited.add(variable.parameterKey())) {
          yield matchesErasure(semantic, erased);
        }
        var matches = matchesOwnedErasure(bound, erased, bounds, visited);
        visited.remove(variable.parameterKey());
        yield matches;
      }
      case JavaTypeStructure.Array array -> erased instanceof JavaTypeStructure.Array erasedArray
          && matchesOwnedErasure(array.component(), erasedArray.component(), bounds, visited);
      case JavaTypeStructure.Intersection intersection -> matchesOwnedErasure(intersection.bounds().getFirst(), erased, bounds, visited);
      default -> matchesErasure(semantic, erased);
    };
  }

  /**
   * Reports whether the erasure has a supported resolved structure.
   *
   * @param type
   *          type
   * @return whether the local invariant holds
   */
  public static boolean complete(SyntaxType type) {
    return type instanceof SyntaxType.Resolved resolved && resolved.structure().isPresent() && !unavailable(resolved.identity());
  }

  /**
   * Reports whether the type has a legal parameter or result erasure category.
   *
   * @param type
   *          type
   * @param result
   *          result
   * @return whether the local invariant holds
   */
  public static boolean legal(SyntaxType type, boolean result) {
    return type.knownIdentity().filter(identity -> legalCategory(identity.category(), result)).isPresent()
        && (!(type instanceof SyntaxType.Resolved resolved)
            || resolved.structure().map(ExecutableTypeValidation::erasedStructure).orElse(true));
  }

  private static boolean erasedStructure(JavaTypeStructure structure) {
    return switch (structure) {
      case JavaTypeStructure.Primitive _ -> true;
      case JavaTypeStructure.Array array -> erasedStructure(array.component());
      case JavaTypeStructure.Declared declared ->
        declared.arguments().isEmpty() && declared.enclosingType().map(ExecutableTypeValidation::erasedStructure).orElse(true);
      case JavaTypeStructure.LocalDeclared local ->
        local.arguments().isEmpty() && local.enclosingType().map(ExecutableTypeValidation::erasedStructure).orElse(true);
      default -> false;
    };
  }

  /**
   * Requires erased rank and primitive spelling to agree with the recursive structure.
   *
   * @param type
   *          type
   */
  public static void validateStructureIdentity(SyntaxType type) {
    if (!(type instanceof SyntaxType.Resolved resolved) || resolved.structure().isEmpty()) {
      return;
    }
    var structure = resolved.structure().orElseThrow();
    var erasedName = resolved.identity().erasedName();
    var dimensionsAgree = erasedArrayDimensions(erasedName) == arrayDimensions(structure);
    if (!dimensionsAgree || primitiveErasure(structure).filter(name -> !name.equals(erasedName)).isPresent()) {
      throw new IllegalArgumentException("Executable erasure identity conflicts with its known recursive structure");
    }
  }

  /**
   * Counts trailing array dimensions in the erased spelling.
   *
   * @param erasedName
   *          erased name
   * @return number of erased array dimensions
   */
  public static int erasedArrayDimensions(String erasedName) {
    var dimensions = 0;
    var end = erasedName.length();
    while (end >= ARRAY_SUFFIX.length() && erasedName.regionMatches(end - ARRAY_SUFFIX.length(), ARRAY_SUFFIX, 0, ARRAY_SUFFIX.length())) {
      dimensions++;
      end -= ARRAY_SUFFIX.length();
    }
    return dimensions;
  }

  private static int arrayDimensions(JavaTypeStructure structure) {
    var dimensions = 0;
    var component = structure;
    while (component instanceof JavaTypeStructure.Array array) {
      dimensions++;
      component = array.component();
    }
    return dimensions;
  }

  private static Optional<String> primitiveErasure(JavaTypeStructure structure) {
    return switch (structure) {
      case JavaTypeStructure.Primitive primitive -> Optional.of(primitive.kind().name().toLowerCase(Locale.ROOT));
      case JavaTypeStructure.Array array -> primitiveErasure(array.component()).map(name -> name + ARRAY_SUFFIX);
      default -> Optional.empty();
    };
  }

  private static boolean unavailable(JavaTypeIdentity identity) {
    return identity.category() == JavaTypeCategory.ERROR || identity.category() == JavaTypeCategory.OTHER;
  }

  private static boolean legalCategory(JavaTypeCategory category, boolean result) {
    return switch (category) {
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, ARRAY, DECLARED, ERROR, OTHER -> true;
      case VOID -> result;
      case NULL, EXECUTABLE, TYPE_VARIABLE, WILDCARD, INTERSECTION, UNION -> false;
    };
  }
}
