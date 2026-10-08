package org.aquilagraph.source.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Independently copied parameter and result erasures of a raw executable type.
 *
 * @param parameters
 *          ordered erased formal types
 * @param returnType
 *          erased declared result, including void for constructors
 */
public record JavaExecutableErasure(List<SyntaxType> parameters, SyntaxType returnType) {
  private static final String ARRAY_SUFFIX = "[]";

  /** Copies the ordered erasures and checks their legal categories. */
  public JavaExecutableErasure {
    parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    Objects.requireNonNull(returnType, "returnType");
    if (parameters.stream().anyMatch(value -> !legal(value, false)) || !legal(returnType, true)) {
      throw new IllegalArgumentException("Executable erasures require legal erased parameter and result categories");
    }
    parameters.forEach(JavaExecutableErasure::validateStructureIdentity);
    validateStructureIdentity(returnType);
  }

  /**
   * Returns the signature composed from the copied erasures without inferring semantic types.
   *
   * @return ordered parameter and result erasure
   */
  public String signature() {
    return "(" + String.join(",", parameters.stream().map(value -> value.knownIdentity().orElseThrow().erasedName()).toList()) + ")"
        + returnType.knownIdentity().orElseThrow().erasedName();
  }

  /**
   * Reports whether every copied erasure has a supported, nonerroneous category.
   *
   * @return true when erased identities contain no compiler type gap
   */
  public boolean isComplete() {
    return parameters.stream().allMatch(JavaExecutableErasure::complete) && complete(returnType);
  }

  void validateComponents(List<SyntaxType> parameterTypes, SyntaxType result) {
    if (parameters.size() != parameterTypes.size()) {
      throw new IllegalArgumentException("Executable erasure must preserve its parameter count");
    }
    for (var index = 0; index < parameters.size(); index++) {
      validateKnown(parameterTypes.get(index), parameters.get(index));
    }
    validateKnown(result, returnType);
  }

  static void validateKnown(SyntaxType type, SyntaxType erasedType) {
    type.knownIdentity().ifPresent(identity -> validateIdentity(identity, erasedType));
    if (type instanceof SyntaxType.Resolved semantic && erasedType instanceof SyntaxType.Resolved erased && semantic.structure().isPresent()
        && erased.structure().isPresent() && !matchesErasure(semantic.structure().orElseThrow(), erased.structure().orElseThrow())) {
      throw new IllegalArgumentException("Executable erasure conflicts with a known signature component");
    }
  }

  static void validateIdentity(JavaTypeIdentity identity, SyntaxType erasedType) {
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

  static boolean matchesOwnedErasure(JavaTypeStructure semantic, JavaTypeStructure erased, Map<String, JavaTypeStructure> bounds,
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

  private static boolean complete(SyntaxType type) {
    return type instanceof SyntaxType.Resolved resolved && resolved.structure().isPresent() && !unavailable(resolved.identity());
  }

  private static boolean legal(SyntaxType type, boolean result) {
    return type.knownIdentity().filter(identity -> legalCategory(identity.category(), result)).isPresent()
        && (!(type instanceof SyntaxType.Resolved resolved)
            || resolved.structure().map(JavaExecutableErasure::erasedStructure).orElse(true));
  }

  private static boolean erasedStructure(JavaTypeStructure structure) {
    return switch (structure) {
      case JavaTypeStructure.Primitive _ -> true;
      case JavaTypeStructure.Array array -> erasedStructure(array.component());
      case JavaTypeStructure.Declared declared ->
        declared.arguments().isEmpty() && declared.enclosingType().map(JavaExecutableErasure::erasedStructure).orElse(true);
      case JavaTypeStructure.LocalDeclared local ->
        local.arguments().isEmpty() && local.enclosingType().map(JavaExecutableErasure::erasedStructure).orElse(true);
      default -> false;
    };
  }

  private static void validateStructureIdentity(SyntaxType type) {
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

  static int erasedArrayDimensions(String erasedName) {
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
