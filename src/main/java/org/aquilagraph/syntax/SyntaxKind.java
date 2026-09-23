package org.aquilagraph.syntax;

/** Derived Java syntax categories used by current semantic-graph consumers. */
public enum SyntaxKind {
  /** Compilation-unit source structure outside method-body lowering. */
  COMPILATION_UNIT,
  /** Source type declaration occurrence. */
  TYPE_DECLARATION,
  /** Source method or constructor declaration occurrence. */
  METHOD_DECLARATION,
  /** Source field declaration occurrence. */
  FIELD_DECLARATION,
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

  boolean supports(JavaSourceKind sourceKind) {
    if (this == UNSUPPORTED) {
      return sourceKind.identifiesSourceSyntax();
    }
    return switch (this) {
      case COMPILATION_UNIT -> switch (sourceKind) {
        case COMPILATION_UNIT, PACKAGE, IMPORT, ANNOTATION, MODULE, REQUIRES, EXPORTS, OPENS, USES, PROVIDES -> true;
        default -> false;
      };
      case TYPE_DECLARATION -> switch (sourceKind) {
        case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> true;
        default -> false;
      };
      case METHOD_DECLARATION -> sourceKind == JavaSourceKind.METHOD;
      case BLOCK -> sourceKind == JavaSourceKind.BLOCK;
      case FIELD_DECLARATION, LOCAL_DECLARATION -> sourceKind == JavaSourceKind.VARIABLE;
      case EXPRESSION_STATEMENT -> sourceKind == JavaSourceKind.EXPRESSION_STATEMENT;
      case RETURN -> sourceKind == JavaSourceKind.RETURN;
      case THROW -> sourceKind == JavaSourceKind.THROW;
      case IF -> sourceKind == JavaSourceKind.IF;
      case TRY -> sourceKind == JavaSourceKind.TRY;
      case CATCH -> sourceKind == JavaSourceKind.CATCH;
      case SWITCH_EXPRESSION -> sourceKind == JavaSourceKind.SWITCH_EXPRESSION;
      case CASE -> sourceKind == JavaSourceKind.CASE;
      case YIELD -> sourceKind == JavaSourceKind.YIELD;
      case INVOCATION -> sourceKind == JavaSourceKind.METHOD_INVOCATION;
      case OBJECT_CREATION -> sourceKind == JavaSourceKind.NEW_CLASS;
      case FIELD_ACCESS, FIELD_LOCATION, RECEIVER -> sourceKind == JavaSourceKind.IDENTIFIER || sourceKind == JavaSourceKind.MEMBER_SELECT;
      case VARIABLE_ACCESS, VARIABLE_LOCATION -> sourceKind == JavaSourceKind.IDENTIFIER;
      case LITERAL -> switch (sourceKind) {
        case INT_LITERAL, LONG_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL, BOOLEAN_LITERAL, CHAR_LITERAL, STRING_LITERAL, NULL_LITERAL -> true;
        default -> false;
      };
      case UNARY -> switch (sourceKind) {
        case UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT -> true;
        default -> false;
      };
      case BINARY -> switch (sourceKind) {
        case MULTIPLY, DIVIDE, REMAINDER, PLUS, MINUS, LEFT_SHIFT, RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT, LESS_THAN, GREATER_THAN,
            LESS_THAN_EQUAL, GREATER_THAN_EQUAL, EQUAL_TO, NOT_EQUAL_TO, AND, XOR, OR ->
          true;
        default -> false;
      };
      case CONDITIONAL_AND -> sourceKind == JavaSourceKind.CONDITIONAL_AND;
      case CONDITIONAL_OR -> sourceKind == JavaSourceKind.CONDITIONAL_OR;
      case ASSIGNMENT -> sourceKind == JavaSourceKind.ASSIGNMENT;
      case ARRAY_LOCATION, ARRAY_ACCESS -> sourceKind == JavaSourceKind.ARRAY_ACCESS;
      case ARRAY_LENGTH -> sourceKind == JavaSourceKind.MEMBER_SELECT;
      case PARENTHESIZED -> sourceKind == JavaSourceKind.PARENTHESIZED;
      case TYPE_CAST -> sourceKind == JavaSourceKind.TYPE_CAST;
      case CONDITIONAL_EXPRESSION -> sourceKind == JavaSourceKind.CONDITIONAL_EXPRESSION;
      case EMPTY -> sourceKind == JavaSourceKind.EMPTY_STATEMENT;
      case UNSUPPORTED -> throw new IllegalStateException("UNSUPPORTED source-kind validation is handled separately");
    };
  }
}
