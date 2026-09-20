package org.aquilagraph.model;

/** Normalized Java syntax categories retained by the accepted source profile. */
public enum SyntaxKind {
  /** Statement block. */
  BLOCK,
  /** Local variable declaration. */
  LOCAL_DECLARATION,
  /** Expression used as a statement. */
  EXPRESSION_STATEMENT,
  /** Return statement. */
  RETURN,
  /** Throw statement. */
  THROW,
  /** Conditional statement. */
  IF,
  /** Try statement. */
  TRY,
  /** Catch clause. */
  CATCH,
  /** Switch expression. */
  SWITCH_EXPRESSION,
  /** Switch case. */
  CASE,
  /** Yield statement. */
  YIELD,
  /** Method invocation. */
  INVOCATION,
  /** Class instance creation. */
  OBJECT_CREATION,
  /** Member selection. */
  MEMBER_ACCESS,
  /** Identifier or variable access. */
  VARIABLE_ACCESS,
  /** Literal expression. */
  LITERAL,
  /** Unary expression. */
  UNARY,
  /** Binary expression. */
  BINARY,
  /** Conditional-and expression. */
  CONDITIONAL_AND,
  /** Conditional-or expression. */
  CONDITIONAL_OR,
  /** Assignment expression. */
  ASSIGNMENT,
  /** Array access expression. */
  ARRAY_ACCESS,
  /** Array length selection. */
  ARRAY_LENGTH,
  /** Parenthesized expression. */
  PARENTHESIZED,
  /** Type-cast expression. */
  TYPE_CAST,
  /** Ternary conditional expression. */
  CONDITIONAL_EXPRESSION,
  /** Empty statement. */
  EMPTY,
  /** Syntax outside the accepted translation profile. */
  UNSUPPORTED
}
