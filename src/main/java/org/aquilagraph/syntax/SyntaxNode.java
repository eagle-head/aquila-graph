package org.aquilagraph.syntax;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceSpan;

/** Immutable, transient compiler-boundary evidence for one javac tree. */
public final class SyntaxNode {
  private static final int SINGLE_CHILD_COUNT = 1;

  private final String identity;
  private final SyntaxKind kind;
  private final SyntaxType syntaxType;
  private final SyntaxValue syntaxValue;
  private final List<SyntaxChild> childNodes;
  private final Optional<SemanticReference> reference;
  private final Optional<SourceSpan> sourceOrigin;

  private SyntaxNode(String nodeId, SyntaxKind syntaxKind, SyntaxType type, SyntaxValue value, List<SyntaxChild> children,
      Optional<SemanticReference> semanticReference, Optional<SourceSpan> origin) {
    identity = requireText(nodeId, "Syntax node identity");
    kind = Objects.requireNonNull(syntaxKind, "syntaxKind");
    syntaxType = Objects.requireNonNull(type, "type");
    syntaxValue = Objects.requireNonNull(value, "value");
    childNodes = List.copyOf(Objects.requireNonNull(children, "children"));
    reference = Objects.requireNonNull(semanticReference, "semanticReference");
    sourceOrigin = Objects.requireNonNull(origin, "origin");
    validateType(syntaxKind, type);
    validateReference(syntaxKind, semanticReference);
    validateChildPositions(childNodes);
    validateShape(syntaxKind, value, childNodes);
  }

  /**
   * Creates syntax evidence with one direct source origin.
   *
   * @param nodeId
   *          stable syntax identity
   * @param syntaxKind
   *          retained syntax category
   * @param type
   *          compiler type outcome
   * @param value
   *          typed scalar syntax facts
   * @param children
   *          role-bearing structural children
   * @param semanticReference
   *          optional declaration reference
   * @param origin
   *          direct source range
   * @return validated syntax evidence
   */
  public static SyntaxNode source(String nodeId, SyntaxKind syntaxKind, SyntaxType type, SyntaxValue value, List<SyntaxChild> children,
      Optional<SemanticReference> semanticReference, SourceSpan origin) {
    return new SyntaxNode(nodeId, syntaxKind, type, value, children, semanticReference,
        Optional.of(Objects.requireNonNull(origin, "origin")));
  }

  /**
   * Creates syntax evidence for a compiler tree that has no direct source range.
   *
   * @param nodeId
   *          stable syntax identity
   * @param syntaxKind
   *          retained syntax category
   * @param type
   *          compiler type outcome
   * @param value
   *          typed scalar syntax facts
   * @param children
   *          role-bearing structural children
   * @param semanticReference
   *          optional declaration reference
   * @return validated syntax evidence
   */
  public static SyntaxNode withoutDirectSource(String nodeId, SyntaxKind syntaxKind, SyntaxType type, SyntaxValue value,
      List<SyntaxChild> children, Optional<SemanticReference> semanticReference) {
    return new SyntaxNode(nodeId, syntaxKind, type, value, children, semanticReference, Optional.empty());
  }

  /**
   * Returns the stable syntax-evidence identifier.
   *
   * @return stable syntax identity
   */
  public String nodeId() {
    return identity;
  }

  /**
   * Returns the retained syntax category.
   *
   * @return retained syntax category
   */
  public SyntaxKind syntaxKind() {
    return kind;
  }

  /**
   * Returns the compiler type outcome.
   *
   * @return compiler type outcome
   */
  public SyntaxType type() {
    return syntaxType;
  }

  /**
   * Returns the typed scalar syntax facts.
   *
   * @return typed scalar syntax facts
   */
  public SyntaxValue value() {
    return syntaxValue;
  }

  /**
   * Returns structural children. Physical list order is not an evaluation-order contract; callers must select typed roles.
   *
   * @return immutable structural children
   */
  public List<SyntaxChild> children() {
    return childNodes;
  }

  /**
   * Returns the zero-or-one semantic declaration reference owned by this tree.
   *
   * @return optional declaration reference
   */
  public Optional<SemanticReference> semanticReference() {
    return reference;
  }

  /**
   * Returns the zero-or-one direct source range owned by this tree.
   *
   * @return optional direct source range
   */
  public Optional<SourceSpan> origin() {
    return sourceOrigin;
  }

  /**
   * Returns the compiler-resolved type independently of symbol references.
   *
   * @return resolved type when the compiler supplied one
   */
  public Optional<JavaTypeIdentity> resolvedType() {
    return syntaxType instanceof SyntaxType.Resolved resolved ? Optional.of(resolved.identity()) : Optional.empty();
  }

  /**
   * Returns the required resolved declaration reference.
   *
   * @return declaration reference required by this syntax kind
   */
  public SemanticReference requiredReference() {
    return reference.orElseThrow(() -> new IllegalStateException(kind + " requires a semantic reference"));
  }

  /**
   * Returns the single required child in a structural role.
   *
   * @param role
   *          structural role to select
   * @return the single child in that role
   */
  public SyntaxNode requiredChild(SyntaxRole role) {
    var matches = children(role);
    if (matches.size() != SINGLE_CHILD_COUNT) {
      throw new IllegalStateException(kind + " requires exactly one " + role + " child");
    }
    return matches.getFirst();
  }

  /**
   * Returns the optional child in a structural role.
   *
   * @param role
   *          structural role to select
   * @return optional child in that role
   */
  public Optional<SyntaxNode> optionalChild(SyntaxRole role) {
    var matches = children(role);
    if (matches.size() > SINGLE_CHILD_COUNT) {
      throw new IllegalStateException(kind + " permits at most one " + role + " child");
    }
    return matches.stream().findFirst();
  }

  /**
   * Returns children in one structural role and their role-local source order.
   *
   * @param role
   *          structural role to select
   * @return immutable children in role-local order
   */
  public List<SyntaxNode> children(SyntaxRole role) {
    Objects.requireNonNull(role, "role");
    return childNodes.stream().filter(child -> child.role() == role).map(SyntaxChild::node).toList();
  }

  /**
   * Returns the typed value required by a lowering operation.
   *
   * @param <T>
   *          requested syntax-value type
   * @param valueType
   *          requested syntax-value class
   * @return this node's value cast to the requested type
   */
  public <T extends SyntaxValue> T value(Class<T> valueType) {
    Objects.requireNonNull(valueType, "valueType");
    if (!valueType.isInstance(syntaxValue)) {
      throw new IllegalStateException(kind + " does not carry " + valueType.getSimpleName());
    }
    return valueType.cast(syntaxValue);
  }

  /**
   * Traverses this node and descendants in structural preorder while validating unique ownership.
   *
   * @return immutable structural-preorder traversal
   */
  public List<SyntaxNode> descendantsAndSelf() {
    var result = new ArrayList<SyntaxNode>();
    collect(this, result, new HashSet<>());
    return List.copyOf(result);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SyntaxNode that)) {
      return false;
    }
    return identity.equals(that.identity) && kind == that.kind && syntaxType.equals(that.syntaxType) && syntaxValue.equals(that.syntaxValue)
        && childNodes.equals(that.childNodes) && reference.equals(that.reference) && sourceOrigin.equals(that.sourceOrigin);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identity, kind, syntaxType, syntaxValue, childNodes, reference, sourceOrigin);
  }

  @Override
  public String toString() {
    return "SyntaxNode[nodeId=" + identity + ", syntaxKind=" + kind + ", type=" + syntaxType + ", value=" + syntaxValue + ", children="
        + childNodes + ", semanticReference=" + reference + ", origin=" + sourceOrigin + ']';
  }

  private static void collect(SyntaxNode node, List<SyntaxNode> result, Set<String> seen) {
    if (!seen.add(node.nodeId())) {
      throw new IllegalArgumentException("A syntax node may have only one parent: " + node.nodeId());
    }
    result.add(node);
    for (var child : node.children()) {
      collect(child.node(), result, seen);
    }
  }

  private static void validateChildPositions(List<SyntaxChild> children) {
    var nextPositions = new EnumMap<SyntaxRole, Integer>(SyntaxRole.class);
    for (var child : children) {
      Objects.requireNonNull(child, "child");
      var expected = nextPositions.getOrDefault(child.role(), 0);
      if (child.position() != expected) {
        throw new IllegalArgumentException("Syntax child positions must be contiguous within each role");
      }
      nextPositions.put(child.role(), expected + 1);
    }
  }

  private static void validateType(SyntaxKind kind, SyntaxType type) {
    if (kind == SyntaxKind.UNSUPPORTED) {
      return;
    }
    if (kind.requiresType() && type == SyntaxType.NotApplicable.INSTANCE) {
      throw new IllegalArgumentException(kind + " requires a compiler type outcome");
    }
    if (!kind.requiresType() && type != SyntaxType.NotApplicable.INSTANCE) {
      throw new IllegalArgumentException(kind + " cannot carry a compiler type outcome");
    }
  }

  private static void validateReference(SyntaxKind kind, Optional<SemanticReference> reference) {
    if (kind == SyntaxKind.UNSUPPORTED) {
      return;
    }
    if (kind == SyntaxKind.TYPE_CAST) {
      if (reference.isPresent() && reference.orElseThrow().referenceKind() != ReferenceKind.TYPE) {
        throw new IllegalArgumentException("TYPE_CAST permits only a type declaration reference");
      }
      return;
    }
    var expectedKinds = switch (kind) {
      case LOCAL_DECLARATION -> Set.of(ReferenceKind.LOCAL);
      case VARIABLE_ACCESS -> Set.of(ReferenceKind.LOCAL, ReferenceKind.PARAMETER, ReferenceKind.ENUM_CONSTANT);
      case VARIABLE_LOCATION -> Set.of(ReferenceKind.LOCAL, ReferenceKind.PARAMETER);
      case FIELD_ACCESS, FIELD_LOCATION -> Set.of(ReferenceKind.FIELD, ReferenceKind.ENUM_CONSTANT);
      case INVOCATION -> Set.of(ReferenceKind.METHOD, ReferenceKind.CONSTRUCTOR);
      case OBJECT_CREATION -> Set.of(ReferenceKind.CONSTRUCTOR);
      case TYPE_CAST -> throw new IllegalStateException("TYPE_CAST reference validation is handled separately");
      default -> Set.<ReferenceKind>of();
    };
    if (expectedKinds.isEmpty()) {
      if (reference.isPresent()) {
        throw new IllegalArgumentException(kind + " cannot carry a semantic reference");
      }
      return;
    }
    if (reference.isEmpty() || !expectedKinds.contains(reference.orElseThrow().referenceKind())) {
      throw new IllegalArgumentException(kind + " requires a semantic reference of kind " + expectedKinds);
    }
  }

  private static void validateShape(SyntaxKind kind, SyntaxValue value, List<SyntaxChild> children) {
    var expectedValue = switch (kind) {
      case LOCAL_DECLARATION -> SyntaxValue.LocalDeclaration.class;
      case VARIABLE_ACCESS, VARIABLE_LOCATION, ARRAY_LENGTH -> SyntaxValue.Symbol.class;
      case FIELD_ACCESS, FIELD_LOCATION -> SyntaxValue.FieldAccess.class;
      case INVOCATION, OBJECT_CREATION -> SyntaxValue.Invocation.class;
      case RECEIVER -> SyntaxValue.Receiver.class;
      case LITERAL -> SyntaxValue.Literal.class;
      case UNARY, BINARY, CONDITIONAL_AND, CONDITIONAL_OR -> SyntaxValue.Operator.class;
      case TYPE_CAST -> SyntaxValue.TypeName.class;
      case CASE -> SyntaxValue.SwitchCase.class;
      case UNSUPPORTED -> SyntaxValue.Unsupported.class;
      default -> SyntaxValue.None.class;
    };
    if (!expectedValue.isInstance(value)) {
      throw new IllegalArgumentException(kind + " requires syntax value " + expectedValue.getSimpleName());
    }

    switch (kind) {
      case BLOCK -> requireShape(children, Set.of(SyntaxRole.STATEMENT));
      case LOCAL_DECLARATION -> requireShape(children, Set.of(SyntaxRole.INITIALIZER), SyntaxRole.INITIALIZER, 0, 1);
      case EXPRESSION_STATEMENT, THROW, PARENTHESIZED, TYPE_CAST ->
        requireShape(children, Set.of(SyntaxRole.EXPRESSION), SyntaxRole.EXPRESSION, 1, 1);
      case RETURN -> requireShape(children, Set.of(SyntaxRole.EXPRESSION), SyntaxRole.EXPRESSION, 0, 1);
      case IF -> {
        requireShape(children, Set.of(SyntaxRole.CONDITION, SyntaxRole.THEN_BRANCH, SyntaxRole.ELSE_BRANCH));
        requireCount(children, SyntaxRole.CONDITION, 1, 1);
        requireCount(children, SyntaxRole.THEN_BRANCH, 1, 1);
        requireCount(children, SyntaxRole.ELSE_BRANCH, 0, 1);
      }
      case TRY -> {
        requireShape(children, Set.of(SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE, SyntaxRole.FINALLY_BLOCK));
        requireCount(children, SyntaxRole.TRY_BLOCK, 1, 1);
        requireCount(children, SyntaxRole.FINALLY_BLOCK, 0, 1);
      }
      case CATCH -> {
        requireShape(children, Set.of(SyntaxRole.PARAMETER, SyntaxRole.BODY));
        requireCount(children, SyntaxRole.PARAMETER, 1, 1);
        requireCount(children, SyntaxRole.BODY, 1, 1);
      }
      case SWITCH_EXPRESSION -> {
        requireShape(children, Set.of(SyntaxRole.SELECTOR, SyntaxRole.CASE));
        requireCount(children, SyntaxRole.SELECTOR, 1, 1);
      }
      case CASE -> validateSwitchCase(SyntaxValue.SwitchCase.class.cast(value), children);
      case YIELD -> requireShape(children, Set.of(SyntaxRole.VALUE), SyntaxRole.VALUE, 1, 1);
      case INVOCATION -> validateInvocation(SyntaxValue.Invocation.class.cast(value), children);
      case OBJECT_CREATION -> validateObjectCreation(SyntaxValue.Invocation.class.cast(value), children);
      case FIELD_ACCESS, FIELD_LOCATION -> validateFieldAccess(SyntaxValue.FieldAccess.class.cast(value), children);
      case ARRAY_LENGTH -> requireShape(children, Set.of(SyntaxRole.QUALIFIER), SyntaxRole.QUALIFIER, 1, 1);
      case UNARY -> requireShape(children, Set.of(SyntaxRole.OPERAND), SyntaxRole.OPERAND, 1, 1);
      case BINARY, CONDITIONAL_AND, CONDITIONAL_OR -> {
        requireShape(children, Set.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND));
        requireCount(children, SyntaxRole.LEFT_OPERAND, 1, 1);
        requireCount(children, SyntaxRole.RIGHT_OPERAND, 1, 1);
      }
      case ASSIGNMENT -> {
        requireShape(children, Set.of(SyntaxRole.LOCATION, SyntaxRole.VALUE));
        requireCount(children, SyntaxRole.LOCATION, 1, 1);
        requireCount(children, SyntaxRole.VALUE, 1, 1);
      }
      case ARRAY_ACCESS, ARRAY_LOCATION -> {
        requireShape(children, Set.of(SyntaxRole.ARRAY, SyntaxRole.INDEX));
        requireCount(children, SyntaxRole.ARRAY, 1, 1);
        requireCount(children, SyntaxRole.INDEX, 1, 1);
      }
      case CONDITIONAL_EXPRESSION -> {
        requireShape(children, Set.of(SyntaxRole.CONDITION, SyntaxRole.TRUE_EXPRESSION, SyntaxRole.FALSE_EXPRESSION));
        requireCount(children, SyntaxRole.CONDITION, 1, 1);
        requireCount(children, SyntaxRole.TRUE_EXPRESSION, 1, 1);
        requireCount(children, SyntaxRole.FALSE_EXPRESSION, 1, 1);
      }
      case VARIABLE_ACCESS, VARIABLE_LOCATION, RECEIVER, LITERAL, EMPTY -> requireShape(children, Set.of());
      case UNSUPPORTED -> {
      }
    }
  }

  private static void validateInvocation(SyntaxValue.Invocation invocation, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.RECEIVER, SyntaxRole.ARGUMENT));
    requireCount(children, SyntaxRole.RECEIVER, invocation.form().evaluatesExpression() ? 1 : 0,
        invocation.form().evaluatesExpression() ? 1 : 0);
  }

  private static void validateObjectCreation(SyntaxValue.Invocation invocation, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ENCLOSING_INSTANCE, SyntaxRole.ARGUMENT));
    var expectedEnclosingInstances = invocation.form() == InvocationForm.QUALIFIED_OBJECT_CREATION ? 1 : 0;
    if (invocation.form() != InvocationForm.OBJECT_CREATION && invocation.form() != InvocationForm.QUALIFIED_OBJECT_CREATION) {
      throw new IllegalArgumentException("OBJECT_CREATION requires a construction invocation form");
    }
    requireCount(children, SyntaxRole.ENCLOSING_INSTANCE, expectedEnclosingInstances, expectedEnclosingInstances);
  }

  private static void validateFieldAccess(SyntaxValue.FieldAccess access, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.RECEIVER));
    requireCount(children, SyntaxRole.RECEIVER, access.receiver().evaluatesExpression() ? 1 : 0,
        access.receiver().evaluatesExpression() ? 1 : 0);
  }

  private static void requireShape(List<SyntaxChild> children, Set<SyntaxRole> roles) {
    if (children.stream().map(SyntaxChild::role).anyMatch(role -> !roles.contains(role))) {
      throw new IllegalArgumentException("Syntax node contains a child role that its kind does not permit");
    }
  }

  private static void validateSwitchCase(SyntaxValue.SwitchCase switchCase, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.LABEL, SyntaxRole.BODY, SyntaxRole.STATEMENT));
    var expressionLabelCount = switchCase.labels().stream().filter(SyntaxValue.SwitchLabel.Expression.class::isInstance).count();
    requireCount(children, SyntaxRole.LABEL, (int) expressionLabelCount, (int) expressionLabelCount);
    for (var label : children.stream().filter(child -> child.role() == SyntaxRole.LABEL).map(SyntaxChild::node).toList()) {
      if (!isSupportedSwitchLabel(label)) {
        throw new IllegalArgumentException("A supported switch case label must be a literal or enum constant");
      }
    }
    switch (switchCase.form()) {
      case RULE -> {
        requireCount(children, SyntaxRole.BODY, 1, 1);
        requireCount(children, SyntaxRole.STATEMENT, 0, 0);
      }
      case STATEMENT_GROUP -> requireCount(children, SyntaxRole.BODY, 0, 0);
    }
  }

  private static boolean isSupportedSwitchLabel(SyntaxNode label) {
    if (label.syntaxKind() == SyntaxKind.LITERAL) {
      return label.resolvedType().map(JavaTypeIdentity::category).filter(category -> category != JavaTypeCategory.NULL).isPresent();
    }
    if (label.syntaxKind() != SyntaxKind.VARIABLE_ACCESS && label.syntaxKind() != SyntaxKind.FIELD_ACCESS) {
      return false;
    }
    return label.semanticReference().filter(reference -> reference.referenceKind() == ReferenceKind.ENUM_CONSTANT)
        .flatMap(SemanticReference::symbolKey).isPresent();
  }

  private static void requireShape(List<SyntaxChild> children, Set<SyntaxRole> roles, SyntaxRole countedRole, int minimum, int maximum) {
    requireShape(children, roles);
    requireCount(children, countedRole, minimum, maximum);
  }

  private static void requireCount(List<SyntaxChild> children, SyntaxRole role, int minimum, int maximum) {
    var count = children.stream().filter(child -> child.role() == role).count();
    if (count < minimum || count > maximum) {
      throw new IllegalArgumentException("Syntax role " + role + " has invalid cardinality for its parent kind");
    }
  }

  private static String requireText(String value, String label) {
    Objects.requireNonNull(value, "nodeId");
    if (value.isBlank()) {
      throw new IllegalArgumentException(label + " cannot be blank");
    }
    return value;
  }
}
