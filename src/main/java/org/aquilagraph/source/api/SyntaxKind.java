package org.aquilagraph.source.api;

/** Derived Java syntax categories used by current semantic-graph consumers. */
public enum SyntaxKind {
  /** Source-only compilation-unit and module structure outside method-body lowering. */
  COMPILATION_UNIT,
  /** One written annotation occurrence, including a nested annotation value. */
  ANNOTATION,
  /** A written, braced annotation array initializer. */
  ANNOTATION_ARRAY,
  /** Explicit source-owned annotation element-value pair. */
  ANNOTATION_ARGUMENT,
  /** Written name in an explicit annotation element-value pair. */
  ANNOTATION_ELEMENT_NAME,
  /** Constant-variable or enum-constant name in an annotation value. */
  ANNOTATION_NAME_REFERENCE,
  /** Class literal in an annotation value. */
  ANNOTATION_CLASS_LITERAL,
  /** Source cast in an annotation constant expression. */
  ANNOTATION_CAST,
  /** Source type declaration occurrence. */
  TYPE_DECLARATION,
  /** Source type parameter declaration occurrence. */
  TYPE_PARAMETER_DECLARATION,
  /** Written formal parameter declaration in an executable header. */
  PARAMETER_DECLARATION,
  /** Explicit receiver parameter in an executable header. */
  RECEIVER_PARAMETER,
  /** Written receiver name expression. */
  RECEIVER_NAME,
  /** Source type-use occurrence. */
  TYPE_USE,
  /** Source method or constructor declaration occurrence. */
  METHOD_DECLARATION,
  /** Source field declaration occurrence. */
  FIELD_DECLARATION,
  /** Enum constant written in an enum body. */
  ENUM_CONSTANT_DECLARATION,
  /** Record component written in a record header. */
  RECORD_COMPONENT_DECLARATION,
  /** Static initializer block owned by a source type. */
  STATIC_INITIALIZER_BLOCK,
  /** Instance initializer block owned by a source type. */
  INSTANCE_INITIALIZER_BLOCK,
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
  /** Written compound assignment without desugaring its storage location. */
  COMPOUND_ASSIGNMENT,
  /** Written prefix or postfix increment or decrement. */
  UPDATE,
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

  /**
   * Indicates whether the source category carries an attributed value type.
   *
   * @return whether an applicable source type is required
   */
  public boolean requiresType() {
    return switch (this) {
      case TYPE_USE, ANNOTATION_ARGUMENT, ANNOTATION_NAME_REFERENCE, ANNOTATION_CLASS_LITERAL, ANNOTATION_CAST, LOCAL_DECLARATION,
          SWITCH_EXPRESSION, INVOCATION, OBJECT_CREATION, FIELD_ACCESS, VARIABLE_ACCESS, RECEIVER, LITERAL, UNARY, BINARY, CONDITIONAL_AND,
          CONDITIONAL_OR, ASSIGNMENT, COMPOUND_ASSIGNMENT, UPDATE, ARRAY_ACCESS, ARRAY_LENGTH, PARENTHESIZED, TYPE_CAST,
          CONDITIONAL_EXPRESSION ->
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
        case COMPILATION_UNIT, PACKAGE, IMPORT, MODULE, REQUIRES, EXPORTS, OPENS, USES, PROVIDES -> true;
        default -> false;
      };
      case ANNOTATION -> sourceKind == JavaSourceKind.ANNOTATION;
      case ANNOTATION_ARRAY -> sourceKind == JavaSourceKind.NEW_ARRAY;
      case ANNOTATION_ARGUMENT -> sourceKind == JavaSourceKind.ASSIGNMENT || sourceKind == JavaSourceKind.NEW_ARRAY
          || sourceKind == JavaSourceKind.ANNOTATION || annotationScalarSyntax(sourceKind);
      case ANNOTATION_ELEMENT_NAME -> sourceKind == JavaSourceKind.IDENTIFIER;
      case ANNOTATION_NAME_REFERENCE -> sourceKind == JavaSourceKind.IDENTIFIER || sourceKind == JavaSourceKind.MEMBER_SELECT;
      case ANNOTATION_CLASS_LITERAL -> sourceKind == JavaSourceKind.MEMBER_SELECT;
      case ANNOTATION_CAST -> sourceKind == JavaSourceKind.TYPE_CAST;
      case TYPE_DECLARATION -> switch (sourceKind) {
        case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> true;
        default -> false;
      };
      case TYPE_PARAMETER_DECLARATION -> sourceKind == JavaSourceKind.TYPE_PARAMETER;
      case TYPE_USE -> sourceKind.identifiesTypeUseSyntax();
      case METHOD_DECLARATION -> sourceKind == JavaSourceKind.METHOD;
      case STATIC_INITIALIZER_BLOCK, INSTANCE_INITIALIZER_BLOCK, BLOCK -> sourceKind == JavaSourceKind.BLOCK;
      case FIELD_DECLARATION, ENUM_CONSTANT_DECLARATION, RECORD_COMPONENT_DECLARATION, LOCAL_DECLARATION, PARAMETER_DECLARATION,
          RECEIVER_PARAMETER ->
        sourceKind == JavaSourceKind.VARIABLE;
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
      case FIELD_ACCESS, RECEIVER, RECEIVER_NAME -> sourceKind == JavaSourceKind.IDENTIFIER || sourceKind == JavaSourceKind.MEMBER_SELECT;
      case VARIABLE_ACCESS -> sourceKind == JavaSourceKind.IDENTIFIER;
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
      case COMPOUND_ASSIGNMENT -> switch (sourceKind) {
        case MULTIPLY_ASSIGNMENT, DIVIDE_ASSIGNMENT, REMAINDER_ASSIGNMENT, PLUS_ASSIGNMENT, MINUS_ASSIGNMENT, LEFT_SHIFT_ASSIGNMENT,
            RIGHT_SHIFT_ASSIGNMENT, UNSIGNED_RIGHT_SHIFT_ASSIGNMENT, AND_ASSIGNMENT, XOR_ASSIGNMENT, OR_ASSIGNMENT ->
          true;
        default -> false;
      };
      case UPDATE -> switch (sourceKind) {
        case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> true;
        default -> false;
      };
      case ARRAY_ACCESS -> sourceKind == JavaSourceKind.ARRAY_ACCESS;
      case ARRAY_LENGTH -> sourceKind == JavaSourceKind.MEMBER_SELECT;
      case PARENTHESIZED -> sourceKind == JavaSourceKind.PARENTHESIZED;
      case TYPE_CAST -> sourceKind == JavaSourceKind.TYPE_CAST;
      case CONDITIONAL_EXPRESSION -> sourceKind == JavaSourceKind.CONDITIONAL_EXPRESSION;
      case EMPTY -> sourceKind == JavaSourceKind.EMPTY_STATEMENT;
      case UNSUPPORTED -> throw new IllegalStateException("UNSUPPORTED source-kind validation is handled separately");
    };
  }

  private static boolean annotationScalarSyntax(JavaSourceKind sourceKind) {
    return switch (sourceKind) {
      case BOOLEAN_LITERAL, CHAR_LITERAL, INT_LITERAL, LONG_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL, STRING_LITERAL, IDENTIFIER,
          MEMBER_SELECT, PARENTHESIZED, TYPE_CAST, CONDITIONAL_EXPRESSION, UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT,
          MULTIPLY, DIVIDE, REMAINDER, PLUS, MINUS, LEFT_SHIFT, RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT, LESS_THAN, GREATER_THAN, LESS_THAN_EQUAL,
          GREATER_THAN_EQUAL, EQUAL_TO, NOT_EQUAL_TO, AND, XOR, OR, CONDITIONAL_AND, CONDITIONAL_OR ->
        true;
      default -> false;
    };
  }
}
