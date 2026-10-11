package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;

/** Shared correspondence between parsed Java child roles and attributed syntax addresses. */
public final class JavaSourcePathRoles {
  private JavaSourcePathRoles() {
  }

  /**
   * Maps one actual parsed relationship without requiring an executable projection of its parent.
   *
   * @param role
   *          parsed child role
   * @param parentKind
   *          independently retained parent kind, required for kind-dependent roles
   * @return corresponding attributed syntax role
   */
  public static SyntaxRole syntaxRole(SourceOwnerPath.Role role, Optional<JavaSourceKind> parentKind) {
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(parentKind, "parentKind");
    return switch (role) {
      case ENCLOSING_EXPRESSION -> SyntaxRole.ENCLOSING_INSTANCE;
      case STATEMENT -> switch (requireParent(parentKind)) {
        case BLOCK, CASE -> SyntaxRole.STATEMENT;
        default -> SyntaxRole.BODY;
      };
      case THEN_STATEMENT -> SyntaxRole.THEN_BRANCH;
      case ELSE_STATEMENT -> SyntaxRole.ELSE_BRANCH;
      case BODY -> requireParent(parentKind) == JavaSourceKind.TRY ? SyntaxRole.TRY_BLOCK : SyntaxRole.BODY;
      case CATCH -> SyntaxRole.CATCH_CLAUSE;
      case FINALLY -> SyntaxRole.FINALLY_BLOCK;
      case CONSTRUCTED_TYPE, CAST_TYPE, ARRAY_ELEMENT_TYPE -> SyntaxRole.TYPE_OPERAND;
      case ARRAY_INITIALIZER -> SyntaxRole.ARRAY_ELEMENT;
      case ASSIGNED_VARIABLE -> SyntaxRole.LOCATION;
      case YIELDED_VALUE -> SyntaxRole.VALUE;
      case METHOD_SELECT, TYPE_QUALIFIER -> SyntaxRole.QUALIFIER;
      case EXPRESSION -> expressionRole(requireParent(parentKind));
      default -> SyntaxRole.valueOf(role.name());
    };
  }

  private static SyntaxRole expressionRole(JavaSourceKind parentKind) {
    return switch (parentKind) {
      case SWITCH, SWITCH_EXPRESSION -> SyntaxRole.SELECTOR;
      case ASSIGNMENT -> SyntaxRole.VALUE;
      case MULTIPLY_ASSIGNMENT, DIVIDE_ASSIGNMENT, REMAINDER_ASSIGNMENT, PLUS_ASSIGNMENT, MINUS_ASSIGNMENT, LEFT_SHIFT_ASSIGNMENT,
          RIGHT_SHIFT_ASSIGNMENT, UNSIGNED_RIGHT_SHIFT_ASSIGNMENT, AND_ASSIGNMENT, XOR_ASSIGNMENT, OR_ASSIGNMENT ->
        SyntaxRole.RIGHT_OPERAND;
      case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT, UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT,
          LOGICAL_COMPLEMENT ->
        SyntaxRole.OPERAND;
      case ARRAY_ACCESS -> SyntaxRole.ARRAY;
      default -> SyntaxRole.EXPRESSION;
    };
  }

  private static JavaSourceKind requireParent(Optional<JavaSourceKind> parentKind) {
    return parentKind.orElseThrow(() -> new IllegalArgumentException("Parsed path correspondence requires its actual parent kind"));
  }
}
