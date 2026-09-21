package org.aquilagraph.syntax;

/** Java syntax categories retained temporarily while semantic graphs are built. */
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
  /** Field value access with a classified receiver form. */
  FIELD_ACCESS,
  /** Local or parameter value access. */
  VARIABLE_ACCESS,
  /** {@code this}, qualified {@code this}, or {@code super} receiver value. */
  RECEIVER,
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
  /** Local or parameter storage location. */
  VARIABLE_LOCATION,
  /** Field storage location with a classified receiver form. */
  FIELD_LOCATION,
  /** Array component storage location. */
  ARRAY_LOCATION,
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
  UNSUPPORTED;

  boolean requiresType() {
    return switch (this) {
      case LOCAL_DECLARATION, SWITCH_EXPRESSION, INVOCATION, OBJECT_CREATION, FIELD_ACCESS, VARIABLE_ACCESS, RECEIVER, LITERAL, UNARY,
          BINARY, CONDITIONAL_AND, CONDITIONAL_OR, ASSIGNMENT, VARIABLE_LOCATION, FIELD_LOCATION, ARRAY_LOCATION, ARRAY_ACCESS,
          ARRAY_LENGTH, PARENTHESIZED, TYPE_CAST, CONDITIONAL_EXPRESSION ->
        true;
      default -> false;
    };
  }
}
