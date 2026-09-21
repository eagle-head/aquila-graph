package org.aquilagraph.syntax;

/** Operators retained from supported Java unary and binary expressions. */
public enum SyntaxOperator {
  /** Unary numeric identity. */
  UNARY_PLUS,
  /** Unary numeric negation. */
  UNARY_MINUS,
  /** Bitwise complement. */
  BITWISE_COMPLEMENT,
  /** Logical complement. */
  LOGICAL_COMPLEMENT,
  /** Multiplication. */
  MULTIPLY,
  /** Division. */
  DIVIDE,
  /** Remainder. */
  REMAINDER,
  /** Addition or string concatenation. */
  PLUS,
  /** Subtraction. */
  MINUS,
  /** Signed left shift. */
  LEFT_SHIFT,
  /** Signed right shift. */
  RIGHT_SHIFT,
  /** Unsigned right shift. */
  UNSIGNED_RIGHT_SHIFT,
  /** Less-than comparison. */
  LESS_THAN,
  /** Greater-than comparison. */
  GREATER_THAN,
  /** Less-than-or-equal comparison. */
  LESS_THAN_EQUAL,
  /** Greater-than-or-equal comparison. */
  GREATER_THAN_EQUAL,
  /** Equality comparison. */
  EQUAL_TO,
  /** Inequality comparison. */
  NOT_EQUAL_TO,
  /** Bitwise or eager boolean conjunction. */
  AND,
  /** Bitwise or boolean exclusive disjunction. */
  XOR,
  /** Bitwise or eager boolean disjunction. */
  OR,
  /** Short-circuit boolean conjunction. */
  CONDITIONAL_AND,
  /** Short-circuit boolean disjunction. */
  CONDITIONAL_OR
}
