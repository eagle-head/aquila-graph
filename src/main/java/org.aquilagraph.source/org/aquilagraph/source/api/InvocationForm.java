package org.aquilagraph.source.api;

/** Compile-time source form of a Java method or constructor invocation. */
public enum InvocationForm {
  /** Unqualified static invocation. */
  STATIC_UNQUALIFIED,
  /** Type-qualified static invocation. */
  STATIC_TYPE,
  /** Static invocation through a written expression qualifier. */
  STATIC_EXPRESSION,
  /** Instance invocation through the implicit receiver. */
  INSTANCE_IMPLICIT,
  /** Instance invocation through a written expression qualifier. */
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
  /** Class instance creation with a written enclosing-instance expression. */
  QUALIFIED_OBJECT_CREATION;

  /**
   * Returns whether the source form owns a receiver-expression child.
   *
   * @return whether a written receiver expression is represented
   */
  public boolean hasReceiverExpression() {
    return this == INSTANCE_EXPRESSION || this == STATIC_EXPRESSION || this == QUALIFIED_SUPER_CONSTRUCTOR;
  }

  /**
   * Returns whether the form has a written qualifier separate from receiver evaluation.
   *
   * @return whether the source qualifier must be retained
   */
  public boolean hasWrittenQualifier() {
    return this == STATIC_TYPE || this == INSTANCE_THIS || this == INSTANCE_QUALIFIED_THIS || this == SUPER || this == QUALIFIED_SUPER;
  }

  /**
   * Returns whether the selected member is static.
   *
   * @return whether this is a static member selection
   */
  public boolean isStatic() {
    return this == STATIC_UNQUALIFIED || this == STATIC_TYPE || this == STATIC_EXPRESSION;
  }
}
