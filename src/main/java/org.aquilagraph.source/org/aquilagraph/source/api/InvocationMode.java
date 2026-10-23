package org.aquilagraph.source.api;

/** Java invocation mode determined at compile time. */
public enum InvocationMode {
  /** Static invocation. */
  STATIC,
  /** Virtual class invocation. */
  VIRTUAL,
  /** Interface invocation. */
  INTERFACE,
  /** Non-overridable superclass invocation. */
  SUPER,
  /** Constructor invocation. */
  CONSTRUCTOR;

  boolean permits(InvocationForm form) {
    return switch (this) {
      case STATIC ->
        form == InvocationForm.STATIC_UNQUALIFIED || form == InvocationForm.STATIC_TYPE || form == InvocationForm.STATIC_EXPRESSION;
      case VIRTUAL, INTERFACE -> form == InvocationForm.INSTANCE_IMPLICIT || form == InvocationForm.INSTANCE_EXPRESSION
          || form == InvocationForm.INSTANCE_THIS || form == InvocationForm.INSTANCE_QUALIFIED_THIS;
      case SUPER -> form == InvocationForm.SUPER || form == InvocationForm.QUALIFIED_SUPER;
      case CONSTRUCTOR -> form == InvocationForm.THIS_CONSTRUCTOR || form == InvocationForm.SUPER_CONSTRUCTOR
          || form == InvocationForm.QUALIFIED_SUPER_CONSTRUCTOR || form == InvocationForm.OBJECT_CREATION
          || form == InvocationForm.QUALIFIED_OBJECT_CREATION;
    };
  }
}
