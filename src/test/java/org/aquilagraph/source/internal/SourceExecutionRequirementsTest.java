package org.aquilagraph.source.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class SourceExecutionRequirementsTest {
  @Test
  void givenMatchAllPatternsInDifferentContexts_whenQualifyingPlacement_thenOnlyRecordComponentsAreAccepted() {
    // Arrange
    var matchAll = node(JavaSourceKind.ANY_PATTERN, SyntaxKind.ANY_PATTERN, SyntaxType.Unresolved.INSTANCE,
        SyntaxValue.None.INSTANCE, List.of());
    var record = node(JavaSourceKind.DECONSTRUCTION_PATTERN, SyntaxKind.DECONSTRUCTION_PATTERN,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.PATTERN_COMPONENT, 0, matchAll)));
    var test = instanceOf(matchAll);

    // Act
    var standalone = SourcePatternRequirements.unsupportedNodes(matchAll);
    var component = SourcePatternRequirements.unsupportedNodes(record);
    var topLevel = SourcePatternRequirements.unsupportedNodes(test);

    // Assert
    assertEquals(List.of(matchAll), standalone);
    assertEquals(List.of(), component);
    assertEquals(List.of(test.requiredChild(SyntaxRole.PATTERN)), topLevel);
  }

  @TestFactory
  Stream<DynamicTest> givenTopLevelPatternTypes_whenQualifyingPlacement_thenPrimitiveAndInferredBindingsAreRejected() {
    return Stream.of(JavaTypeCategory.BOOLEAN, JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.INT,
        JavaTypeCategory.LONG, JavaTypeCategory.CHAR, JavaTypeCategory.FLOAT, JavaTypeCategory.DOUBLE, JavaTypeCategory.VOID,
        JavaTypeCategory.DECLARED).map(category -> DynamicTest.dynamicTest(
            "given" + category + "Pattern_whenQualifyingPlacement_thenItsNonPreviewApplicabilityIsPreserved", () -> {
              // Arrange
              var pattern = pattern(type(category, category == JavaTypeCategory.DECLARED ? "java.lang.String" : category.name()),
                  LocalBindingEvidence.TypeForm.DECLARED);
              var test = instanceOf(pattern);

              // Act
              var rejected = SourcePatternRequirements.unsupportedNodes(test);

              // Assert
              assertEquals(category == JavaTypeCategory.DECLARED ? List.of() : List.of(test.requiredChild(SyntaxRole.PATTERN)), rejected);
            }));
  }

  @Test
  void givenInferredPatternAtTheTopLevel_whenQualifyingPlacement_thenItsBindingCannotMasqueradeAsAnExplicitReferenceType() {
    // Arrange
    var pattern = pattern(type(JavaTypeCategory.DECLARED, "java.lang.String"), LocalBindingEvidence.TypeForm.INFERRED);
    var test = instanceOf(pattern);

    // Act
    var rejected = SourcePatternRequirements.unsupportedNodes(test);
    var standalone = SourcePatternRequirements.unsupportedNodes(pattern);

    // Assert
    assertEquals(List.of(test.requiredChild(SyntaxRole.PATTERN)), rejected);
    assertEquals(List.of(), standalone);
  }

  @TestFactory
  Stream<DynamicTest> givenGuardTypeOutcomes_whenQualifyingTheSwitchCase_thenOnlyBooleanAndBoxedBooleanAreAccepted() {
    return Stream.of(new Guard(type(JavaTypeCategory.BOOLEAN, "boolean"), true), new Guard(type(JavaTypeCategory.DECLARED, "java.lang.Boolean"), true),
        new Guard(type(JavaTypeCategory.DECLARED, "java.lang.String"), false), new Guard(type(JavaTypeCategory.INT, "int"), false),
        new Guard(SyntaxType.Unresolved.INSTANCE, false))
        .map(example -> DynamicTest.dynamicTest("givenGuard" + example.type() + "_whenQualifyingTheCase_thenTheTypeRuleIsApplied", () -> {
          // Arrange
          var binding = pattern(type(JavaTypeCategory.DECLARED, "java.lang.String"), LocalBindingEvidence.TypeForm.DECLARED);
          var guard = variable(example.type(), ReferenceKind.PARAMETER);
          var rule = node(JavaSourceKind.CASE, SyntaxKind.CASE, SyntaxType.NotApplicable.INSTANCE,
              new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE, List.of(new SyntaxValue.SwitchLabel.Pattern(0))),
              List.of(new SyntaxChild(SyntaxRole.LABEL, 0, binding), new SyntaxChild(SyntaxRole.GUARD, 0, guard),
                  new SyntaxChild(SyntaxRole.BODY, 0, literal())));

          // Act
          var rejected = SourceSwitchRequirements.unsupportedNodes(rule, false);

          // Assert
          assertEquals(example.permitted() ? List.of() : List.of(rule), rejected);
        }));
  }

  @Test
  void givenStatementRuleBody_whenQualifyingSwitchContexts_thenAnExpressionCannotAcceptTheStatementForm() {
    // Arrange
    var assignment = node(JavaSourceKind.ASSIGNMENT, SyntaxKind.ASSIGNMENT, type(JavaTypeCategory.INT, "int"),
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.LOCATION, 0, variable(type(JavaTypeCategory.INT, "int"), ReferenceKind.LOCAL)),
            new SyntaxChild(SyntaxRole.VALUE, 0, literal())));
    var statement = node(JavaSourceKind.EXPRESSION_STATEMENT, SyntaxKind.EXPRESSION_STATEMENT, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, assignment)));
    var rule = node(JavaSourceKind.CASE, SyntaxKind.CASE, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE, List.of(SyntaxValue.SwitchLabel.Default.INSTANCE)),
        List.of(new SyntaxChild(SyntaxRole.BODY, 0, statement)));
    var children = List.of(new SyntaxChild(SyntaxRole.SELECTOR, 0, literal()), new SyntaxChild(SyntaxRole.CASE, 0, rule));
    var expression = node(JavaSourceKind.SWITCH_EXPRESSION, SyntaxKind.SWITCH_EXPRESSION, type(JavaTypeCategory.INT, "int"),
        SyntaxValue.None.INSTANCE, children);
    var selection = node(JavaSourceKind.SWITCH, SyntaxKind.SWITCH_STATEMENT, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, children);

    // Act
    var refused = SourceSwitchRequirements.unsupportedNodes(expression, false);
    var accepted = SourceSwitchRequirements.unsupportedNodes(selection, false);

    // Assert
    assertEquals(List.of(expression), refused);
    assertEquals(List.of(), accepted);
  }

  @Test
  void givenPatternLiteralEnumAndLocalCaseLabels_whenCheckingTheHistoricalBoundedContract_thenOnlyLiteralOrEnumSelectionIsAccepted() {
    // Arrange
    var binding = pattern(type(JavaTypeCategory.DECLARED, "java.lang.String"), LocalBindingEvidence.TypeForm.DECLARED);
    var patternCase = rule(binding, new SyntaxValue.SwitchLabel.Pattern(0));
    var literalCase = rule(literal(), new SyntaxValue.SwitchLabel.Expression(0));
    var enumCase = rule(variable(type(JavaTypeCategory.DECLARED, "Subject"), ReferenceKind.ENUM_CONSTANT), new SyntaxValue.SwitchLabel.Expression(0));
    var localCase = rule(variable(type(JavaTypeCategory.INT, "int"), ReferenceKind.LOCAL), new SyntaxValue.SwitchLabel.Expression(0));

    // Act
    var patternAccepted = SourceSwitchRequirements.isBoundedCase(patternCase);
    var literalAccepted = SourceSwitchRequirements.isBoundedCase(literalCase);
    var enumAccepted = SourceSwitchRequirements.isBoundedCase(enumCase);
    var localAccepted = SourceSwitchRequirements.isBoundedCase(localCase);

    // Assert
    assertFalse(patternAccepted);
    assertTrue(literalAccepted);
    assertTrue(enumAccepted);
    assertFalse(localAccepted);
  }

  @Test
  void givenSwitchesWithoutCases_whenQualifyingTheirContexts_thenOnlyTheStatementFormIsAccepted() {
    // Arrange
    var children = List.of(new SyntaxChild(SyntaxRole.SELECTOR, 0, literal()));
    var expression = node(JavaSourceKind.SWITCH_EXPRESSION, SyntaxKind.SWITCH_EXPRESSION, type(JavaTypeCategory.INT, "int"),
        SyntaxValue.None.INSTANCE, children);
    var statement = node(JavaSourceKind.SWITCH, SyntaxKind.SWITCH_STATEMENT, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE, children);

    // Act
    var refused = SourceSwitchRequirements.unsupportedNodes(expression, false);
    var accepted = SourceSwitchRequirements.unsupportedNodes(statement, false);

    // Assert
    assertEquals(List.of(expression), refused);
    assertEquals(List.of(), accepted);
  }

  private static SyntaxNode rule(SyntaxNode label, SyntaxValue.SwitchLabel value) {
    return node(JavaSourceKind.CASE, SyntaxKind.CASE, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE, List.of(value)),
        List.of(new SyntaxChild(SyntaxRole.LABEL, 0, label), new SyntaxChild(SyntaxRole.BODY, 0, literal())));
  }

  private static SyntaxNode pattern(SyntaxType type, LocalBindingEvidence.TypeForm form) {
    var binding = node(JavaSourceKind.VARIABLE, SyntaxKind.LOCAL_DECLARATION, type,
        new SyntaxValue.LocalDeclaration("_", Optional.empty(), List.of(), Optional.of(new LocalBindingEvidence(Optional.empty(), form,
            LocalBindingEvidence.Context.PATTERN, false, 0, List.of()))), List.of());
    return node(JavaSourceKind.BINDING_PATTERN, SyntaxKind.BINDING_PATTERN, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.PatternBinding(Optional.empty()), List.of(new SyntaxChild(SyntaxRole.PARAMETER, 0, binding)));
  }

  private static SyntaxNode instanceOf(SyntaxNode pattern) {
    return node(JavaSourceKind.INSTANCE_OF, SyntaxKind.INSTANCE_OF, type(JavaTypeCategory.BOOLEAN, "boolean"), SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, literal()), new SyntaxChild(SyntaxRole.PATTERN, 0, pattern)));
  }

  private static SyntaxNode literal() {
    return node(JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL, type(JavaTypeCategory.INT, "int"), new SyntaxValue.Literal("1"), List.of());
  }

  private static SyntaxNode variable(SyntaxType type, ReferenceKind referenceKind) {
    return SyntaxNode.withoutDirectSource("source:syntax", SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.IDENTIFIER,
        SyntaxKind.VARIABLE_ACCESS, type, new SyntaxValue.SourceSymbol("value"), List.of(), Optional.of(new SemanticReference(referenceKind,
            ResolutionStatus.RESOLVED, Optional.of("declaration:value"), Optional.empty(), List.of(), List.of())));
  }

  private static SyntaxType type(JavaTypeCategory category, String name) {
    return new SyntaxType.Resolved(new JavaTypeIdentity(category, name, name), Optional.empty());
  }

  private static SyntaxNode node(JavaSourceKind sourceKind, SyntaxKind kind, SyntaxType type, SyntaxValue value, List<SyntaxChild> children) {
    var root = SyntaxStructuralPath.sourceFileRoot();
    var rebased = children.stream().map(child -> new SyntaxChild(child.role(), child.position(),
        rebase(child.node(), root.child(child.role(), child.position())))).toList();
    return SyntaxNode.withoutDirectSource("source:syntax", root, sourceKind, kind, type, value, rebased, Optional.empty());
  }

  private static SyntaxNode rebase(SyntaxNode node, SyntaxStructuralPath path) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        rebase(child.node(), path.child(child.role(), child.position())))).toList();
    return SyntaxNode.withoutDirectSource(node.sourceFileKey(), path, node.sourceKind(), node.syntaxKind(), node.type(), node.value(),
        children, node.semanticReference());
  }

  private record Guard(SyntaxType type, boolean permitted) {}
}
