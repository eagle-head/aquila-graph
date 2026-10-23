package org.aquilagraph.source.api;

import java.util.List;
import org.aquilagraph.source.internal.values.ExecutableTypeValidation;
import java.util.Objects;

/**
 * Independently copied parameter and result erasures of a raw executable type.
 *
 * @param parameters
 *          ordered erased formal types
 * @param returnType
 *          erased declared result, including void for constructors
 */
public record JavaExecutableErasure(List<SyntaxType> parameters, SyntaxType returnType) {

  /** Copies the ordered erasures and checks their legal categories. */
  public JavaExecutableErasure {
    parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    Objects.requireNonNull(returnType, "returnType");
    if (parameters.stream().anyMatch(value -> !ExecutableTypeValidation.legal(value, false))
        || !ExecutableTypeValidation.legal(returnType, true)) {
      throw new IllegalArgumentException("Executable erasures require legal erased parameter and result categories");
    }
    parameters.forEach(ExecutableTypeValidation::validateStructureIdentity);
    ExecutableTypeValidation.validateStructureIdentity(returnType);
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
    return parameters.stream().allMatch(ExecutableTypeValidation::complete) && ExecutableTypeValidation.complete(returnType);
  }

  void validateComponents(List<SyntaxType> parameterTypes, SyntaxType result) {
    if (parameters.size() != parameterTypes.size()) {
      throw new IllegalArgumentException("Executable erasure must preserve its parameter count");
    }
    for (var index = 0; index < parameters.size(); index++) {
      ExecutableTypeValidation.validateKnown(parameterTypes.get(index), parameters.get(index));
    }
    ExecutableTypeValidation.validateKnown(result, returnType);
  }

}
