package org.aquilagraph.frontend.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import org.aquilagraph.source.api.InvocationApplicabilityMode;

/** Rechecks invocation conversion phases against an attributed selected executable. */
final class JavaInvocationApplicability {
  private final SemanticResolver semantics;

  JavaInvocationApplicability(SemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  Optional<Selection> select(ExecutableType executable, boolean variableDeclaration, List<? extends TypeMirror> arguments) {
    Objects.requireNonNull(executable, "executable");
    Objects.requireNonNull(arguments, "arguments");
    var formals = executable.getParameterTypes();
    if (arguments.stream().anyMatch(type -> !valueType(type)) || formals.stream().anyMatch(type -> !valueType(type))) {
      return Optional.empty();
    }
    if (fixedApplicable(formals, arguments, false)) {
      return Optional.of(new Selection(Phase.STRICT_FIXED, formals.size(), List.copyOf(formals), Optional.empty()));
    }
    if (fixedApplicable(formals, arguments, true)) {
      return Optional.of(new Selection(Phase.LOOSE_FIXED, formals.size(), List.copyOf(formals), Optional.empty()));
    }
    if (!variableDeclaration || formals.isEmpty() || arguments.size() < formals.size() - 1
        || !(formals.getLast() instanceof ArrayType array)) {
      return Optional.empty();
    }
    var targets = new ArrayList<TypeMirror>();
    for (var index = 0; index < arguments.size(); index++) {
      var target = index < formals.size() - 1 ? formals.get(index) : array.getComponentType();
      if (!looseConversion(arguments.get(index), target)) {
        return Optional.empty();
      }
      targets.add(target);
    }
    return Optional.of(new Selection(Phase.VARIABLE, formals.size(), targets, Optional.of(array)));
  }

  private boolean fixedApplicable(List<? extends TypeMirror> formals, List<? extends TypeMirror> arguments, boolean loose) {
    if (formals.size() != arguments.size()) {
      return false;
    }
    for (var index = 0; index < formals.size(); index++) {
      if (!(loose
          ? looseConversion(arguments.get(index), formals.get(index))
          : strictConversion(arguments.get(index), formals.get(index)))) {
        return false;
      }
    }
    return true;
  }

  boolean strictConversion(TypeMirror source, TypeMirror target) {
    if (!valueType(source) || !valueType(target) || source.getKind().isPrimitive() != target.getKind().isPrimitive()) {
      return false;
    }
    return semantics.assignable(source, target);
  }

  boolean looseConversion(TypeMirror source, TypeMirror target) {
    if (strictConversion(source, target)) {
      return true;
    }
    if (!valueType(source) || !valueType(target)) {
      return false;
    }
    if (source instanceof PrimitiveType primitive && !target.getKind().isPrimitive()) {
      return semantics.assignable(semantics.boxedType(primitive), target);
    }
    if (!source.getKind().isPrimitive() && target.getKind().isPrimitive()) {
      return semantics.unboxedType(source).map(primitive -> semantics.assignable(primitive, target)).orElse(false);
    }
    return false;
  }

  private static boolean valueType(TypeMirror type) {
    Objects.requireNonNull(type, "type");
    return switch (type.getKind()) {
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, ARRAY, DECLARED, TYPEVAR, WILDCARD, NULL, INTERSECTION -> true;
      case VOID, NONE, ERROR, EXECUTABLE, PACKAGE, MODULE, UNION, OTHER -> false;
    };
  }

  enum Phase {
    STRICT_FIXED, LOOSE_FIXED, VARIABLE
  }

  record Selection(Phase phase, int formalCount, List<TypeMirror> argumentTargets, Optional<ArrayType> variableArray) {
    Selection {
      Objects.requireNonNull(phase, "phase");
      argumentTargets = List.copyOf(argumentTargets);
      Objects.requireNonNull(variableArray, "variableArray");
    }

    InvocationApplicabilityMode mode() {
      return phase == Phase.VARIABLE ? InvocationApplicabilityMode.VARIABLE : InvocationApplicabilityMode.FIXED;
    }
  }
}
