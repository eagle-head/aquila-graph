package org.aquilagraph.frontend.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Binds one compiler callable to a statically typed invocation and checked receiver. */
final class JavaDeclarationAccessor {
  private final Class<?> receiverClass;
  private final Method callableMethod;
  private final Function<Object, @Nullable Object> invocation;

  private <T> JavaDeclarationAccessor(Class<T> type, String name, Function<T, @Nullable Object> reader) {
    receiverClass = Objects.requireNonNull(type, "type");
    Objects.requireNonNull(reader, "reader");
    callableMethod = method(type, name);
    if (Modifier.isStatic(callableMethod.getModifiers()) || callableMethod.getReturnType() == void.class) {
      throw new IllegalArgumentException("Declaration accessor must be an instance value query: " + callableMethod);
    }
    invocation = owner -> reader.apply(type.cast(owner));
  }

  static <T> JavaDeclarationAccessor of(Class<T> type, String name, Function<T, @Nullable Object> reader) {
    return new JavaDeclarationAccessor(type, name, reader);
  }

  static Method method(Class<?> type, String name, Class<?>... parameters) {
    try {
      return type.getMethod(name, parameters);
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException("Declaration accessor is unavailable: " + type.getName() + '#' + name, exception);
    }
  }

  Class<?> receiverType() {
    return receiverClass;
  }

  Method method() {
    return callableMethod;
  }

  Optional<Object> read(Object owner) {
    return Optional.ofNullable(invocation.apply(owner));
  }
}
