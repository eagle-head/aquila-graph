package org.aquilagraph.syntax;

import java.util.Objects;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;

/**
 * Incoming enum construction context consumed by a constructor invocation, separate from source arguments.
 *
 * @param methodKey
 *          canonical containing constructor identity
 * @param invocationNodeId
 *          final registered invocation node identity
 * @param enumOwnerKey
 *          canonical source enum identity
 * @param selectedConstructorKey
 *          selected constructor identity
 * @param target
 *          superclass initialization or same-enum delegation
 * @param nameType
 *          selected String input type
 * @param ordinalType
 *          selected int input type
 */
public record EnumConstructorContextDecision(String methodKey, String invocationNodeId, String enumOwnerKey, String selectedConstructorKey,
    Target target, JavaTypeIdentity nameType, JavaTypeIdentity ordinalType) {
  /** Direction in which incoming enum name and ordinal are passed. */
  public enum Target {
    /** Compiler-inserted invocation of Enum(String,int). */
    ENUM_SUPERCLASS,
    /** Ordinary source invocation of another constructor in the same enum. */
    THIS_CONSTRUCTOR
  }

  /** Validates canonical identities and the exact ordered input types. */
  public EnumConstructorContextDecision {
    DecisionValueValidation.requireKey(methodKey, "methodKey");
    DecisionValueValidation.requireKey(invocationNodeId, "invocationNodeId");
    DecisionValueValidation.requireKey(enumOwnerKey, "enumOwnerKey");
    DecisionValueValidation.requireKey(selectedConstructorKey, "selectedConstructorKey");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(nameType, "nameType");
    Objects.requireNonNull(ordinalType, "ordinalType");
    if (!methodKey.startsWith(enumOwnerKey + "#<init>(")
        || (target == Target.ENUM_SUPERCLASS && !"type:java.lang.Enum#<init>(java.lang.String,int)".equals(selectedConstructorKey))
        || (target == Target.THIS_CONSTRUCTOR && !selectedConstructorKey.startsWith(enumOwnerKey + "#<init>("))) {
      throw new IllegalArgumentException("Enum context requires its containing and selected constructor owners");
    }
    if (nameType.category() != JavaTypeCategory.DECLARED || !"java.lang.String".equals(nameType.erasedName())
        || !"java.lang.String".equals(nameType.displayName()) || ordinalType.category() != JavaTypeCategory.INT
        || !"int".equals(ordinalType.erasedName()) || !"int".equals(ordinalType.displayName())) {
      throw new IllegalArgumentException("Enum context requires ordered String name and int ordinal input types");
    }
  }
}
