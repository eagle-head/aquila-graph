package org.aquilagraph.source.internal.values;

import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;

import java.util.Objects;

/** Checks intrinsic type and key invariants shared by immutable decision values. */
public final class DecisionValueValidation {
  private DecisionValueValidation() {
  }

  /**
   * Rejects a null or blank intrinsic key.
   *
   * @param key
   *          key
   * @param name
   *          name used in the failure diagnostic
   */
  public static void requireKey(String key, String name) {
    if (Objects.requireNonNull(key, name).isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }

  /**
   * Rejects categories that cannot denote an executable value.
   *
   * @param type
   *          required value type
   * @param name
   *          name used in the failure diagnostic
   */
  public static void requireValueType(JavaTypeIdentity type, String name) {
    Objects.requireNonNull(type, name);
    if (switch (type.category()) {
      case VOID, ERROR, EXECUTABLE, OTHER -> true;
      default -> false;
    }) {
      throw new IllegalArgumentException(name + " must identify a resolved value type");
    }
  }

  /**
   * Reports whether the identity denotes a primitive value type.
   *
   * @param type
   *          required value type
   * @return whether the facts satisfy the required relation
   */
  public static boolean isPrimitive(JavaTypeIdentity type) {
    return switch (type.category()) {
      case BOOLEAN, BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE -> true;
      default -> false;
    };
  }

  /**
   * Reports whether the identity denotes a reference value type.
   *
   * @param type
   *          required value type
   * @return whether the facts satisfy the required relation
   */
  public static boolean isReference(JavaTypeIdentity type) {
    return switch (type.category()) {
      case ARRAY, DECLARED, TYPE_VARIABLE, WILDCARD, UNION, INTERSECTION -> true;
      default -> false;
    };
  }

  /**
   * Checks whether the reference identity is the wrapper of the primitive identity.
   *
   * @param wrapper
   *          wrapper
   * @param primitive
   *          primitive
   * @return whether the facts satisfy the required relation
   */
  public static boolean isWrapper(JavaTypeIdentity wrapper, JavaTypeIdentity primitive) {
    return wrapper.category() == JavaTypeCategory.DECLARED && wrapper.erasedName().equals(switch (primitive.category()) {
      case BOOLEAN -> "java.lang.Boolean";
      case BYTE -> "java.lang.Byte";
      case SHORT -> "java.lang.Short";
      case CHAR -> "java.lang.Character";
      case INT -> "java.lang.Integer";
      case LONG -> "java.lang.Long";
      case FLOAT -> "java.lang.Float";
      case DOUBLE -> "java.lang.Double";
      default -> "";
    });
  }

  /**
   * Reports whether the source/target primitive pair permits widening conversion.
   *
   * @param source
   *          source
   * @param target
   *          target
   * @return whether the facts satisfy the required relation
   */
  public static boolean isWideningPrimitive(JavaTypeIdentity source, JavaTypeIdentity target) {
    if (source.category() == target.category()) {
      return isPrimitive(source);
    }
    var destination = target.category();
    return switch (source.category()) {
      case BYTE -> switch (destination) {
        case SHORT, INT, LONG, FLOAT, DOUBLE -> true;
        default -> false;
      };
      case SHORT, CHAR -> switch (destination) {
        case INT, LONG, FLOAT, DOUBLE -> true;
        default -> false;
      };
      case INT -> destination == JavaTypeCategory.LONG || destination == JavaTypeCategory.FLOAT || destination == JavaTypeCategory.DOUBLE;
      case LONG -> destination == JavaTypeCategory.FLOAT || destination == JavaTypeCategory.DOUBLE;
      case FLOAT -> destination == JavaTypeCategory.DOUBLE;
      default -> false;
    };
  }

  /**
   * Checks the primitive type retained immediately before a boxing conversion.
   *
   * @param source
   *          source
   * @param preBox
   *          pre box
   * @return whether the facts satisfy the required relation
   */
  public static boolean isPreBoxType(JavaTypeIdentity source, JavaTypeIdentity preBox) {
    return source.category() == preBox.category() || switch (source.category()) {
      case BYTE, SHORT, CHAR, INT -> switch (preBox.category()) {
        case BYTE, SHORT, CHAR -> true;
        default -> false;
      };
      default -> false;
    };
  }
}
