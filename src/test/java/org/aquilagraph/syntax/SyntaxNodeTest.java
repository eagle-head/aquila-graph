package org.aquilagraph.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceSpan;
import org.junit.jupiter.api.Test;

class SyntaxNodeTest {
  private static final SourceSpan ORIGIN = new SourceSpan("source", 0, 1);
  private static final JavaTypeIdentity INT_TYPE = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");

  @Test
  void givenIfNodeWithTypedRoles_whenQueried_thenStructureDoesNotDependOnChildPosition() {
    // Arrange
    var condition = leaf("condition", SyntaxKind.LITERAL, new SyntaxValue.Literal("true"));
    var thenBranch = leaf("then", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);
    var elseBranch = leaf("else", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);
    var node = source("if", SyntaxKind.IF, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.ELSE_BRANCH, 0, elseBranch), new SyntaxChild(SyntaxRole.CONDITION, 0, condition),
            new SyntaxChild(SyntaxRole.THEN_BRANCH, 0, thenBranch)), Optional.empty());

    // Act
    var actualCondition = node.requiredChild(SyntaxRole.CONDITION);
    var actualThenBranch = node.requiredChild(SyntaxRole.THEN_BRANCH);
    var actualElseBranch = node.optionalChild(SyntaxRole.ELSE_BRANCH).orElseThrow();

    // Assert
    assertEquals(condition, actualCondition);
    assertEquals(thenBranch, actualThenBranch);
    assertEquals(elseBranch, actualElseBranch);
  }

  @Test
  void givenIfNodeWithoutCondition_whenCreated_thenInvalidShapeIsRejected() {
    // Arrange
    var thenBranch = leaf("then", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("if", SyntaxKind.IF, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
            List.of(new SyntaxChild(SyntaxRole.THEN_BRANCH, 0, thenBranch)), Optional.empty()));

    // Assert
    assertTrue(exception.getMessage().contains("CONDITION"));
  }

  @Test
  void givenLocalDeclarationWithoutTypedValue_whenCreated_thenInvalidValueIsRejected() {
    // Arrange
    var reference = Optional.of(reference(ReferenceKind.LOCAL, "local"));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("local", SyntaxKind.LOCAL_DECLARATION, new SyntaxType.Resolved(INT_TYPE), SyntaxValue.None.INSTANCE, List.of(),
            reference));

    // Assert
    assertTrue(exception.getMessage().contains("LocalDeclaration"));
  }

  @Test
  void givenValueExpressionWithoutTypeOutcome_whenCreated_thenInvalidTypeContractIsRejected() {
    // Arrange
    var value = new SyntaxValue.Literal("1");

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("literal", SyntaxKind.LITERAL, SyntaxType.NotApplicable.INSTANCE, value, List.of(), Optional.empty()));

    // Assert
    assertTrue(exception.getMessage().contains("compiler type"));
  }

  @Test
  void givenBlankSourceOwner_whenCreated_thenInvalidIdentityIsRejected() {
    // Arrange
    var value = new SyntaxValue.Literal("1");

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> SyntaxNode.source(" ", path("literal"), JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL,
            new SyntaxType.Resolved(INT_TYPE), value, List.of(), Optional.empty(), ORIGIN));

    // Assert
    assertTrue(exception.getMessage().contains("Source-file key"));
  }

  @Test
  void givenCompilerGeneratedFactory_whenNodeIsCreated_thenDirectOriginIsAbsent() {
    // Arrange
    var value = new SyntaxValue.Literal("1");

    // Act
    var node = SyntaxNode.withoutDirectSource("source", path("literal"), JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL,
        new SyntaxType.Resolved(INT_TYPE), value, List.of(), Optional.empty());

    // Assert
    assertTrue(node.origin().isEmpty());
  }

  @Test
  void givenMethodTreeContainsUnsupportedDescendant_whenReadinessIsQueried_thenUnsupportedIsDerived() {
    // Arrange
    var unsupported = source("unsupported", SyntaxKind.UNSUPPORTED, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.Unsupported("WHILE_LOOP"), List.of(), Optional.empty());
    var root = source("root", SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, unsupported)), Optional.empty());
    var methodSyntax = MethodSyntaxFixture.view("type:Example#run()", root);

    // Act
    var readiness = methodSyntax.readiness();

    // Assert
    assertEquals(SyntaxReadiness.UNSUPPORTED, readiness);
  }

  @Test
  void givenRuleCaseWithoutBody_whenCreated_thenInvalidCaseShapeIsRejected() {
    // Arrange
    var value = new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE,
        List.of(SyntaxValue.SwitchLabel.Default.INSTANCE));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("case", SyntaxKind.CASE, SyntaxType.NotApplicable.INSTANCE, value, List.of(), Optional.empty()));

    // Assert
    assertTrue(exception.getMessage().contains("BODY"));
  }

  @Test
  void givenStatementGroupCaseWithRuleBody_whenCreated_thenConflictingCaseFormsAreRejected() {
    // Arrange
    var value = new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.STATEMENT_GROUP,
        List.of(SyntaxValue.SwitchLabel.Default.INSTANCE));
    var body = leaf("body", SyntaxKind.BLOCK, SyntaxValue.None.INSTANCE);

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("case", SyntaxKind.CASE, SyntaxType.NotApplicable.INSTANCE, value,
            List.of(new SyntaxChild(SyntaxRole.BODY, 0, body)), Optional.empty()));

    // Assert
    assertTrue(exception.getMessage().contains("BODY"));
  }

  @Test
  void givenDefaultAndExpressionLabels_whenCreated_thenInvalidCaseLabelsAreRejected() {
    // Arrange
    var labels = List.of(SyntaxValue.SwitchLabel.Default.INSTANCE, new SyntaxValue.SwitchLabel.Expression(0));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE, labels));

    // Assert
    assertTrue(exception.getMessage().contains("default switch label"));
  }

  @Test
  void givenFieldReferenceOnVariableAccess_whenCreated_thenReferenceKindIsRejected() {
    // Arrange
    var fieldReference = Optional.of(reference(ReferenceKind.FIELD, "field"));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> source("variable", SyntaxKind.VARIABLE_ACCESS, new SyntaxType.Resolved(INT_TYPE), new SyntaxValue.SourceSymbol("value"),
            List.of(), fieldReference));

    // Assert
    assertTrue(exception.getMessage().contains("semantic reference"));
  }

  @Test
  void givenSameSyntaxNodeOwnedTwice_whenMethodIsCreated_thenDuplicateOwnershipIsRejected() {
    // Arrange
    var statement = leaf("statement", SyntaxKind.EMPTY, SyntaxValue.None.INSTANCE);
    var root = source("root", SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, statement), new SyntaxChild(SyntaxRole.STATEMENT, 1, statement)),
        Optional.empty());

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> MethodSyntaxFixture.view("type:Example#run()", root));

    // Assert
    assertTrue(exception.getMessage().contains("one parent"));
  }

  private static SyntaxNode leaf(String id, SyntaxKind kind, SyntaxValue value) {
    var type = kind.requiresType() ? new SyntaxType.Resolved(INT_TYPE) : SyntaxType.NotApplicable.INSTANCE;
    return source(id, kind, type, value, List.of(), Optional.empty());
  }

  private static SyntaxNode source(String id, SyntaxKind kind, SyntaxType type, SyntaxValue value, List<SyntaxChild> children,
      Optional<SemanticReference> reference) {
    return SyntaxNode.source("source", path(id), sourceKind(kind, value), kind, type, value, children, reference, ORIGIN);
  }

  private static SyntaxStructuralPath path(String identity) {
    return SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT,
        Math.floorMod(identity.hashCode(), Integer.MAX_VALUE));
  }

  private static JavaSourceKind sourceKind(SyntaxKind kind, SyntaxValue value) {
    return switch (kind) {
      case BLOCK -> JavaSourceKind.BLOCK;
      case LOCAL_DECLARATION -> JavaSourceKind.VARIABLE;
      case IF -> JavaSourceKind.IF;
      case VARIABLE_ACCESS -> JavaSourceKind.IDENTIFIER;
      case LITERAL -> JavaSourceKind.INT_LITERAL;
      case CASE -> JavaSourceKind.CASE;
      case EMPTY -> JavaSourceKind.EMPTY_STATEMENT;
      case UNSUPPORTED -> JavaSourceKind.valueOf(((SyntaxValue.Unsupported) value).compilerKind());
      default -> throw new IllegalArgumentException("Missing test source kind for " + kind);
    };
  }

  private static SemanticReference reference(ReferenceKind kind, String key) {
    return new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(), List.of(), List.of());
  }
}
