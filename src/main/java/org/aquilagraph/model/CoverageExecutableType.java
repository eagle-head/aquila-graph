package org.aquilagraph.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Copied public executable-type components for independent declaration-property support.
 *
 * @param variables
 *          ordered selected type variables
 * @param parameters
 *          ordered selected formal parameter types
 * @param returnType
 *          selected return type
 * @param thrownTypes
 *          ordered selected thrown types
 * @param receiverType
 *          selected receiver type, when present
 */
public record CoverageExecutableType(List<Variable> variables, List<JavaTypeIdentity> parameters, JavaTypeIdentity returnType,
    List<JavaTypeIdentity> thrownTypes, Optional<JavaTypeIdentity> receiverType) {
  /** Copies ordered executable components without retaining compiler objects. */
  public CoverageExecutableType {
    variables = List.copyOf(Objects.requireNonNull(variables, "variables"));
    parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    Objects.requireNonNull(returnType, "returnType");
    thrownTypes = List.copyOf(Objects.requireNonNull(thrownTypes, "thrownTypes"));
    Objects.requireNonNull(receiverType, "receiverType");
  }

  /**
   * Encodes every selected executable type component in its exact semantic order.
   *
   * @return canonical length-framed semantic text
   */
  public String canonicalText() {
    var result = new StringBuilder("X(").append(variables.size()).append(":[");
    for (var variable : variables) {
      result.append("V(");
      appendText(result, variable.declarationKey());
      appendIdentity(result, variable.type());
      result.append(')');
    }
    result.append(']');
    appendIdentities(result, parameters);
    appendIdentity(result, returnType);
    appendIdentities(result, thrownTypes);
    if (receiverType.isPresent()) {
      result.append("P(");
      appendIdentity(result, receiverType.orElseThrow());
      result.append(')');
    } else {
      result.append("O()");
    }
    return result.append(')').toString();
  }

  private static void appendIdentities(StringBuilder result, List<JavaTypeIdentity> identities) {
    result.append(identities.size()).append(":[");
    for (var identity : identities) {
      appendIdentity(result, identity);
    }
    result.append(']');
  }

  private static void appendIdentity(StringBuilder result, JavaTypeIdentity identity) {
    result.append("I(");
    appendText(result, identity.category().name());
    appendText(result, identity.displayName());
    appendText(result, identity.erasedName());
    result.append(')');
  }

  private static void appendText(StringBuilder result, String value) {
    result.append(value.length()).append(':').append(value);
  }

  /**
   * Canonical declaration identity and copied type of one ordered executable type variable.
   *
   * @param declarationKey
   *          canonical type-parameter declaration identity
   * @param type
   *          copied type-variable identity
   */
  public record Variable(String declarationKey, JavaTypeIdentity type) {
    /** Validates a canonical type-variable projection. */
    public Variable {
      Objects.requireNonNull(declarationKey, "declarationKey");
      Objects.requireNonNull(type, "type");
      if (declarationKey.isBlank() || type.category() != JavaTypeCategory.TYPE_VARIABLE) {
        throw new IllegalArgumentException("Executable variables require canonical declaration identities and type-variable values");
      }
    }
  }
}
