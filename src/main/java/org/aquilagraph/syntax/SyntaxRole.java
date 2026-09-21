package org.aquilagraph.syntax;

/** Structural role played by a child in its parent syntax node. */
public enum SyntaxRole {
  /** Statement within an ordered statement sequence. */
  STATEMENT("statement"),
  /** Initializer belonging to a declaration. */
  INITIALIZER("initializer"),
  /** General expression belonging to a statement or wrapper. */
  EXPRESSION("expression"),
  /** Value produced by a statement such as {@code yield}. */
  VALUE("value"),
  /** Conditional predicate. */
  CONDITION("condition"),
  /** Branch evaluated when a condition is true. */
  THEN_BRANCH("then"),
  /** Optional branch evaluated when a condition is false. */
  ELSE_BRANCH("else"),
  /** Resource declared by a try statement. */
  RESOURCE("resource"),
  /** Protected block of a try statement. */
  TRY_BLOCK("try"),
  /** Catch clause belonging to a try statement. */
  CATCH_CLAUSE("catch"),
  /** Finalization block belonging to a try statement. */
  FINALLY_BLOCK("finally"),
  /** Parameter belonging to a declaration or handler. */
  PARAMETER("parameter"),
  /** Body belonging to a case or catch clause. */
  BODY("body"),
  /** Selector of a switch expression. */
  SELECTOR("selector"),
  /** Case belonging to a switch expression. */
  CASE("case"),
  /** Constant-expression label belonging to a switch case. */
  LABEL("label"),
  /** Guard belonging to a switch case outside the accepted profile. */
  GUARD("guard"),
  /** Assignable storage location of a simple assignment. */
  LOCATION("location"),
  /** Runtime receiver expression evaluated for a field access or invocation. */
  RECEIVER("receiver"),
  /** Ordered invocation or construction argument. */
  ARGUMENT("argument"),
  /** Optional enclosing instance of an object construction. */
  ENCLOSING_INSTANCE("enclosing"),
  /** Qualifier of a member selection. */
  QUALIFIER("qualifier"),
  /** Operand of a unary expression. */
  OPERAND("operand"),
  /** Left operand of a binary expression. */
  LEFT_OPERAND("left"),
  /** Right operand of a binary expression. */
  RIGHT_OPERAND("right"),
  /** Array expression of an array access. */
  ARRAY("array"),
  /** Index expression of an array access. */
  INDEX("index"),
  /** True alternative of a conditional expression. */
  TRUE_EXPRESSION("true"),
  /** False alternative of a conditional expression. */
  FALSE_EXPRESSION("false");

  private final String stableSegment;

  SyntaxRole(String pathSegment) {
    stableSegment = pathSegment;
  }

  /**
   * Returns the stable source-structure path segment for this role.
   *
   * @return path segment used in syntax identity
   */
  public String pathSegment() {
    return stableSegment;
  }
}
