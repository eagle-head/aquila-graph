package org.aquilagraph.model;

/** Compile-time source form of a Java method or constructor invocation. */
public enum InvocationForm {
  /** Unqualified static invocation. */
  STATIC_UNQUALIFIED,
  /** Type-qualified static invocation with no runtime receiver. */
  STATIC_TYPE,
  /** Static invocation through an evaluated expression whose result is discarded. */
  STATIC_EXPRESSION,
  /** Instance invocation through the implicit receiver. */
  INSTANCE_IMPLICIT,
  /** Instance invocation through an evaluated expression child. */
  INSTANCE_EXPRESSION,
  /** Instance invocation explicitly through {@code this}. */
  INSTANCE_THIS,
  /** Instance invocation through qualified {@code this}. */
  INSTANCE_QUALIFIED_THIS,
  /** Invocation through {@code super}. */
  SUPER,
  /** Invocation through qualified {@code super}. */
  QUALIFIED_SUPER,
  /** Explicit invocation of another constructor in the same class. */
  THIS_CONSTRUCTOR,
  /** Explicit invocation of a superclass constructor. */
  SUPER_CONSTRUCTOR,
  /** Explicit invocation of a qualified superclass constructor. */
  QUALIFIED_SUPER_CONSTRUCTOR,
  /** Ordinary class instance creation without an explicit enclosing instance. */
  OBJECT_CREATION,
  /** Class instance creation with an explicitly evaluated enclosing instance. */
  QUALIFIED_OBJECT_CREATION;

  /**
   * Returns whether this form owns one evaluated receiver child.
   *
   * @return whether an expression receiver is evaluated
   */
  public boolean evaluatesExpression() {
    return this == INSTANCE_EXPRESSION || this == STATIC_EXPRESSION || this == QUALIFIED_SUPER_CONSTRUCTOR;
  }

  /**
   * Returns whether the invocation consumes a runtime receiver value.
   *
   * @return whether invocation consumes a receiver value
   */
  public boolean consumesReceiver() {
    return switch (this) {
      case INSTANCE_IMPLICIT, INSTANCE_EXPRESSION, INSTANCE_THIS, INSTANCE_QUALIFIED_THIS, SUPER, QUALIFIED_SUPER, SUPER_CONSTRUCTOR,
          QUALIFIED_SUPER_CONSTRUCTOR ->
        true;
      case STATIC_UNQUALIFIED, STATIC_TYPE, STATIC_EXPRESSION, THIS_CONSTRUCTOR, OBJECT_CREATION, QUALIFIED_OBJECT_CREATION -> false;
    };
  }

  /**
   * Returns whether a null receiver can fail at the invocation operation.
   *
   * @return whether the invocation may fail on a null evaluated receiver
   */
  public boolean canFailOnNullReceiver() {
    return this == INSTANCE_EXPRESSION || this == QUALIFIED_SUPER_CONSTRUCTOR;
  }
}
