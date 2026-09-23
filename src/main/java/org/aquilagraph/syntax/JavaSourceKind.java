package org.aquilagraph.syntax;

/** Exact compiler-neutral Java 25 source-tree kinds. */
public enum JavaSourceKind {
  /** Annotated type. */
  ANNOTATED_TYPE,
  /** Declaration annotation. */
  ANNOTATION,
  /** Type-use annotation. */
  TYPE_ANNOTATION,
  /** Array component access. */
  ARRAY_ACCESS,
  /** Array type. */
  ARRAY_TYPE,
  /** Assertion statement. */
  ASSERT,
  /** Simple assignment expression. */
  ASSIGNMENT,
  /** Statement block. */
  BLOCK,
  /** Break statement. */
  BREAK,
  /** Switch case. */
  CASE,
  /** Catch clause. */
  CATCH,
  /** Class declaration. */
  CLASS,
  /** Compilation unit. */
  COMPILATION_UNIT,
  /** Conditional expression. */
  CONDITIONAL_EXPRESSION,
  /** Continue statement. */
  CONTINUE,
  /** Do-while loop. */
  DO_WHILE_LOOP,
  /** Enhanced for loop. */
  ENHANCED_FOR_LOOP,
  /** Expression statement. */
  EXPRESSION_STATEMENT,
  /** Member selection. */
  MEMBER_SELECT,
  /** Member reference. */
  MEMBER_REFERENCE,
  /** Basic for loop. */
  FOR_LOOP,
  /** Identifier. */
  IDENTIFIER,
  /** If statement. */
  IF,
  /** Import declaration. */
  IMPORT,
  /** Instance-of expression. */
  INSTANCE_OF,
  /** Labeled statement. */
  LABELED_STATEMENT,
  /** Method or constructor declaration. */
  METHOD,
  /** Method invocation. */
  METHOD_INVOCATION,
  /** Modifier set. */
  MODIFIERS,
  /** Array creation. */
  NEW_ARRAY,
  /** Class instance creation. */
  NEW_CLASS,
  /** Lambda expression. */
  LAMBDA_EXPRESSION,
  /** Package declaration. */
  PACKAGE,
  /** Parenthesized expression. */
  PARENTHESIZED,
  /** Any pattern. */
  ANY_PATTERN,
  /** Binding pattern. */
  BINDING_PATTERN,
  /** Default case label. */
  DEFAULT_CASE_LABEL,
  /** Constant case label. */
  CONSTANT_CASE_LABEL,
  /** Pattern case label. */
  PATTERN_CASE_LABEL,
  /** Record deconstruction pattern. */
  DECONSTRUCTION_PATTERN,
  /** Primitive type. */
  PRIMITIVE_TYPE,
  /** Return statement. */
  RETURN,
  /** Empty statement. */
  EMPTY_STATEMENT,
  /** Switch statement. */
  SWITCH,
  /** Switch expression. */
  SWITCH_EXPRESSION,
  /** Synchronized statement. */
  SYNCHRONIZED,
  /** Throw statement. */
  THROW,
  /** Try statement. */
  TRY,
  /** Parameterized type. */
  PARAMETERIZED_TYPE,
  /** Union type. */
  UNION_TYPE,
  /** Intersection type. */
  INTERSECTION_TYPE,
  /** Type-cast expression. */
  TYPE_CAST,
  /** Type parameter. */
  TYPE_PARAMETER,
  /** Variable declaration. */
  VARIABLE,
  /** While loop. */
  WHILE_LOOP,
  /** Postfix increment expression. */
  POSTFIX_INCREMENT,
  /** Postfix decrement expression. */
  POSTFIX_DECREMENT,
  /** Prefix increment expression. */
  PREFIX_INCREMENT,
  /** Prefix decrement expression. */
  PREFIX_DECREMENT,
  /** Unary plus expression. */
  UNARY_PLUS,
  /** Unary minus expression. */
  UNARY_MINUS,
  /** Bitwise complement expression. */
  BITWISE_COMPLEMENT,
  /** Logical complement expression. */
  LOGICAL_COMPLEMENT,
  /** Multiplication expression. */
  MULTIPLY,
  /** Division expression. */
  DIVIDE,
  /** Remainder expression. */
  REMAINDER,
  /** Addition expression. */
  PLUS,
  /** Subtraction expression. */
  MINUS,
  /** Left-shift expression. */
  LEFT_SHIFT,
  /** Signed right-shift expression. */
  RIGHT_SHIFT,
  /** Unsigned right-shift expression. */
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
  /** Bitwise or boolean and expression. */
  AND,
  /** Bitwise or boolean exclusive-or expression. */
  XOR,
  /** Bitwise or boolean inclusive-or expression. */
  OR,
  /** Conditional-and expression. */
  CONDITIONAL_AND,
  /** Conditional-or expression. */
  CONDITIONAL_OR,
  /** Multiplication assignment. */
  MULTIPLY_ASSIGNMENT,
  /** Division assignment. */
  DIVIDE_ASSIGNMENT,
  /** Remainder assignment. */
  REMAINDER_ASSIGNMENT,
  /** Addition assignment. */
  PLUS_ASSIGNMENT,
  /** Subtraction assignment. */
  MINUS_ASSIGNMENT,
  /** Left-shift assignment. */
  LEFT_SHIFT_ASSIGNMENT,
  /** Signed right-shift assignment. */
  RIGHT_SHIFT_ASSIGNMENT,
  /** Unsigned right-shift assignment. */
  UNSIGNED_RIGHT_SHIFT_ASSIGNMENT,
  /** And assignment. */
  AND_ASSIGNMENT,
  /** Exclusive-or assignment. */
  XOR_ASSIGNMENT,
  /** Inclusive-or assignment. */
  OR_ASSIGNMENT,
  /** Integer literal. */
  INT_LITERAL,
  /** Long literal. */
  LONG_LITERAL,
  /** Float literal. */
  FLOAT_LITERAL,
  /** Double literal. */
  DOUBLE_LITERAL,
  /** Boolean literal. */
  BOOLEAN_LITERAL,
  /** Character literal. */
  CHAR_LITERAL,
  /** String literal. */
  STRING_LITERAL,
  /** Null literal. */
  NULL_LITERAL,
  /** Unbounded wildcard type argument. */
  UNBOUNDED_WILDCARD,
  /** Upper-bounded wildcard type argument. */
  EXTENDS_WILDCARD,
  /** Lower-bounded wildcard type argument. */
  SUPER_WILDCARD,
  /** Erroneous source tree. */
  ERRONEOUS,
  /** Interface declaration. */
  INTERFACE,
  /** Enum declaration. */
  ENUM,
  /** Annotation-interface declaration. */
  ANNOTATION_TYPE,
  /** Module declaration. */
  MODULE,
  /** Module exports directive. */
  EXPORTS,
  /** Module opens directive. */
  OPENS,
  /** Module provides directive. */
  PROVIDES,
  /** Record declaration. */
  RECORD,
  /** Module requires directive. */
  REQUIRES,
  /** Module uses directive. */
  USES,
  /** Implementation-reserved tree kind. */
  OTHER,
  /** Yield statement. */
  YIELD;

  /**
   * Returns the stable kind identity used in durable keys and tuples.
   *
   * @return exact Java 25 kind identity
   */
  public String stableId() {
    return name();
  }

  /**
   * Indicates whether this kind identifies Java source syntax.
   *
   * @return {@code false} only for the implementation-reserved kind
   */
  public boolean identifiesSourceSyntax() {
    return this != OTHER;
  }

  /**
   * Indicates whether this source kind can identify a type-use tree in a declaration header.
   *
   * @return {@code true} for type-use syntax, including erroneous source type uses
   */
  public boolean identifiesTypeUseSyntax() {
    return switch (this) {
      case ANNOTATED_TYPE, ARRAY_TYPE, IDENTIFIER, MEMBER_SELECT, PARAMETERIZED_TYPE, PRIMITIVE_TYPE, UNION_TYPE, INTERSECTION_TYPE,
          UNBOUNDED_WILDCARD, EXTENDS_WILDCARD, SUPER_WILDCARD, ERRONEOUS ->
        true;
      default -> false;
    };
  }
}
