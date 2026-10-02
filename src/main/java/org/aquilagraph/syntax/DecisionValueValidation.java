package org.aquilagraph.syntax;

import java.util.Objects;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;

final class DecisionValueValidation {
  private DecisionValueValidation() {
  }

  static void requireKey(String key, String name) {
    if (Objects.requireNonNull(key, name).isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }

  static void requireValueType(JavaTypeIdentity type, String name) {
    Objects.requireNonNull(type, name);
    if (switch (type.category()) {
      case VOID, ERROR, EXECUTABLE, OTHER -> true;
      default -> false;
    }) {
      throw new IllegalArgumentException(name + " must identify a resolved value type");
    }
  }

  static boolean isPrimitive(JavaTypeIdentity type) {
    return switch (type.category()) {
      case BOOLEAN, BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE -> true;
      default -> false;
    };
  }

  static boolean isReference(JavaTypeIdentity type) {
    return switch (type.category()) {
      case ARRAY, DECLARED, TYPE_VARIABLE, WILDCARD, UNION, INTERSECTION -> true;
      default -> false;
    };
  }

  static boolean isWrapper(JavaTypeIdentity wrapper, JavaTypeIdentity primitive) {
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

  static boolean isWideningPrimitive(JavaTypeIdentity source, JavaTypeIdentity target) {
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

  static boolean isPreBoxType(JavaTypeIdentity source, JavaTypeIdentity preBox) {
    return source.category() == preBox.category() || switch (source.category()) {
      case BYTE, SHORT, CHAR, INT -> switch (preBox.category()) {
        case BYTE, SHORT, CHAR -> true;
        default -> false;
      };
      default -> false;
    };
  }
}
