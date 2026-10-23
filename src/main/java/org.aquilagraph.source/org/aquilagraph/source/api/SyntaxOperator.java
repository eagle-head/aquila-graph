package org.aquilagraph.source.api;

/** Operators retained from written Java expressions without desugaring. */
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
  CONDITIONAL_OR,
  /** Multiplication assignment. */
  MULTIPLY_ASSIGNMENT,
  /** Division assignment. */
  DIVIDE_ASSIGNMENT,
  /** Remainder assignment. */
  REMAINDER_ASSIGNMENT,
  /** Addition or string concatenation assignment. */
  PLUS_ASSIGNMENT,
  /** Subtraction assignment. */
  MINUS_ASSIGNMENT,
  /** Signed left shift assignment. */
  LEFT_SHIFT_ASSIGNMENT,
  /** Signed right shift assignment. */
  RIGHT_SHIFT_ASSIGNMENT,
  /** Unsigned right shift assignment. */
  UNSIGNED_RIGHT_SHIFT_ASSIGNMENT,
  /** Bitwise or boolean conjunction assignment. */
  AND_ASSIGNMENT,
  /** Bitwise or boolean exclusive disjunction assignment. */
  XOR_ASSIGNMENT,
  /** Bitwise or boolean disjunction assignment. */
  OR_ASSIGNMENT,
  /** Prefix increment. */
  PREFIX_INCREMENT,
  /** Prefix decrement. */
  PREFIX_DECREMENT,
  /** Postfix increment. */
  POSTFIX_INCREMENT,
  /** Postfix decrement. */
  POSTFIX_DECREMENT
}
