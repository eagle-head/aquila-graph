package org.aquilagraph.model;

/** Semantic operation represented by a control-flow graph node. */
public enum OperationKind {
  /** Reads a program value. */
  READ,
  /** Materializes an implicit or explicit Java receiver value. */
  RECEIVER,
  /** Writes or declares a program value. */
  WRITE,
  /** Produces a literal constant. */
  CONSTANT,
  /** Applies a unary operator. */
  UNARY,
  /** Applies a binary operator. */
  BINARY,
  /** Applies a type cast. */
  CAST,
  /** Converts a primitive value to the selected wrapper and reference target. */
  BOX,
  /** Converts a wrapper value to the selected primitive target. */
  UNBOX,
  /** Converts one operand to its String concatenation value. */
  STRING_CONVERSION,
  /** Concatenates two String values. */
  STRING_CONCAT,
  /** Checks the enclosing instance before qualified construction or delegation. */
  ENCLOSING_INSTANCE_CHECK,
  /** Models possible initialization of the selected declaring class or interface. */
  CLASS_INITIALIZATION,
  /** Allocates the instance before constructor argument evaluation. */
  NEW_ALLOCATION,
  /** Checks an array assignment target after the raw right operand. */
  ARRAY_TARGET_CHECK,
  /** Allocates the selected variable-arity argument array. */
  VARARGS_ARRAY,
  /** Reads or writes an array element. */
  ARRAY_ACCESS,
  /** Reads an array length. */
  ARRAY_LENGTH,
  /** Selects an object member. */
  MEMBER_ACCESS,
  /** Invokes a method. */
  INVOKE,
  /** Constructs an object. */
  NEW_OBJECT,
  /** Selects between conditional branches. */
  DECISION,
  /** Evaluates a switch selector. */
  SWITCH_SELECTOR,
  /** Merges alternative control-flow paths. */
  MERGE,
  /** Enters an exception handler. */
  CATCH,
  /** Returns from a method. */
  RETURN,
  /** Throws an exception value. */
  THROW,
  /** Represents a structural operation without a semantic effect. */
  NO_OP
}
