package org.aquilagraph.source.api;

/** Structural role played by a child in its parent syntax node. */
public enum SyntaxRole {
  /** Captured source file that owns a compilation-unit occurrence tree. */
  SOURCE_FILE_ROOT("source-file-root"),
  /** Written package declaration within an ordinary compilation unit. */
  PACKAGE_DECLARATION("package-declaration"),
  /** Written import declaration within a compilation unit. */
  IMPORT_DECLARATION("import-declaration"),
  /** Written module declaration within a modular compilation unit. */
  MODULE_DECLARATION("module-declaration"),
  /** Directive written in a module declaration. */
  MODULE_DIRECTIVE("module-directive"),
  /** Written class member in a compact compilation unit. */
  COMPACT_MEMBER("compact-member"),
  /** Written annotation attached to a package declaration. */
  ANNOTATION("annotation"),
  /** Explicit element-value pair within a written annotation. */
  ANNOTATION_ARGUMENT("annotation-argument"),
  /** Written element name in a normal annotation pair. */
  ANNOTATION_ELEMENT_NAME("annotation-element-name"),
  /** Written type operand of a class literal or cast. */
  TYPE_OPERAND("type-operand"),
  /** Top-level type declaration within a compilation unit. */
  TYPE_DECLARATION("type-declaration"),
  /** Member declaration within a type. */
  MEMBER("member"),
  /** Type parameter written in a type or executable declaration header. */
  TYPE_PARAMETER("type-parameter"),
  /** Record component written in a record header. */
  RECORD_COMPONENT("record-component"),
  /** Written upper bound of a type parameter. */
  TYPE_BOUND("type-bound"),
  /** Written nominal name of a parameterized type. */
  TYPE_NAME("type-name"),
  /** Written type argument in source order. */
  TYPE_ARGUMENT("type-argument"),
  /** Explicit upper bound of a wildcard. */
  UPPER_BOUND("upper-bound"),
  /** Explicit lower bound of a wildcard. */
  LOWER_BOUND("lower-bound"),
  /** Ordered source bound of an intersection. */
  INTERSECTION_BOUND("intersection-bound"),
  /** Ordered source alternative of a union. */
  UNION_ALTERNATIVE("union-alternative"),
  /** Written qualifier of a selected type name. */
  TYPE_QUALIFIER("type-qualifier"),
  /** Written component type beneath one array dimension. */
  COMPONENT_TYPE("component-type"),
  /** Underlying written type beneath a type-use annotation wrapper. */
  UNDERLYING_TYPE("underlying-type"),
  /** Written type attachment of a parameter declaration. */
  DECLARED_TYPE("declared-type"),
  /** Explicit receiver parameter of an executable. */
  RECEIVER_PARAMETER("receiver-parameter"),
  /** Written return type of a method or annotation element. */
  RETURN_TYPE("return-type"),
  /** Written type in a throws clause. */
  THROWN_TYPE("thrown-type"),
  /** Written annotation-interface element default value. */
  DEFAULT_VALUE("default-value"),
  /** Written direct superclass type attachment. */
  SUPERCLASS("superclass"),
  /** Written implemented or extended interface type attachment. */
  INTERFACE("interface"),
  /** Written permitted subclass type attachment. */
  PERMITTED_SUBCLASS("permitted-subclass"),
  /** Statement within an ordered statement sequence. */
  STATEMENT("statement"),
  /** Initializer belonging to a declaration. */
  INITIALIZER("initializer"),
  /** Ordered expression statement in a basic for update clause. */
  UPDATE("update"),
  /** Expression supplying enhanced-for elements. */
  ITERABLE("iterable"),
  /** General expression belonging to a statement or wrapper. */
  EXPRESSION("expression"),
  /** Value produced by a statement such as {@code yield}. */
  VALUE("value"),
  /** Conditional predicate. */
  CONDITION("condition"),
  /** Optional expression describing an assertion failure. */
  DETAIL("detail"),
  /** Reference expression whose monitor protects a synchronized block. */
  MONITOR("monitor"),
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
  /** Ordered sized dimension expression of an executable array creation. */
  DIMENSION("dimension"),
  /** Ordered element of an executable array initializer. */
  ARRAY_ELEMENT("array-element"),
  /** Index expression of an array access. */
  INDEX("index"),
  /** True alternative of a conditional expression. */
  TRUE_EXPRESSION("true"),
  /** False alternative of a conditional expression. */
  FALSE_EXPRESSION("false");

  private final String stableIdentity;

  SyntaxRole(String stableId) {
    stableIdentity = stableId;
  }

  /**
   * Returns the stable domain identity for this role.
   *
   * @return role identity used in keys and tuples
   */
  public String stableId() {
    return stableIdentity;
  }
}
