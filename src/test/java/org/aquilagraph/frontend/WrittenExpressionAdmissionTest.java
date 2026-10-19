package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CompilerExpressionAnchor;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.api.WrittenExpressionRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;

class WrittenExpressionAdmissionTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenCoherentlyReplacedLiteralScalars_whenCopiedValuesAreConstructedAndAdmitted_thenEachLiteralFamilyRejectsContradictoryEvidence() {
    // Arrange
    var cases = List.of(
        new LiteralScalarCase("1", JavaSourceKind.INT_LITERAL, new AnnotationScalar.StringValue("1")),
        new LiteralScalarCase("1L", JavaSourceKind.LONG_LITERAL, new AnnotationScalar.IntValue(1)),
        new LiteralScalarCase("1.5f", JavaSourceKind.FLOAT_LITERAL, new AnnotationScalar.DoubleValue(1.5)),
        new LiteralScalarCase("1.5", JavaSourceKind.DOUBLE_LITERAL, new AnnotationScalar.FloatValue(1.5f)),
        new LiteralScalarCase("\"x\"", JavaSourceKind.STRING_LITERAL, new AnnotationScalar.CharValue('x')),
        new LiteralScalarCase("'x'", JavaSourceKind.CHAR_LITERAL, new AnnotationScalar.StringValue("x")),
        new LiteralScalarCase("true", JavaSourceKind.BOOLEAN_LITERAL, new AnnotationScalar.StringValue("true")),
        new LiteralScalarCase("null", JavaSourceKind.NULL_LITERAL, new AnnotationScalar.StringValue("null")));

    // Act
    var observations = cases.stream().map(value -> DynamicTest.dynamicTest(
        "given" + value.kind().name() + "WithContradictoryScalar_whenCopiedValuesAreConstructedAndAdmitted_thenTheContradictionIsRejected", () -> {
      // Arrange
      var original = produce("class Example { String value() { return " + value.expression() + " + \"a\" + \"b\"; } }");
      var copied = CopiedAstValues.capture(original.aggregate());
      var literal = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == value.kind()).findFirst().orElseThrow();

      // Act
      var unchanged = AstValueAdmission.admit(copied);
      var failure = assertThrows(IllegalArgumentException.class,
          () -> AstValueAdmission.admit(withLiteralScalar(copied, literal, value.contradiction(), true)));

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion());
      assertEquals(original.aggregate(), unchanged);
      assertTrue(failure.getMessage() != null && !failure.getMessage().isBlank());
    }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenRestrictedMagnitudeWithAnIndependentScalar_whenCopiedValuesAreConstructedAndAdmitted_thenBothIntegralWidthsRejectCapturedOrDerivedRuntimeValues() {
    // Arrange
    var expressions = List.of("-2147483648", "-9223372036854775808L");

    // Act
    var observations = expressions.stream().flatMap(expression -> Stream.of(false, true).map(captured ->
        DynamicTest.dynamicTest("givenRestrictedMagnitude(" + expression + ")With" + (captured ? "CapturedAndDerived" : "DerivedOnly")
            + "RuntimeScalar_whenCopiedValuesAreConstructedAndAdmitted_thenTheContradictionIsRejected", () -> {
          // Arrange
          var original = produce("class Example { long value() { return " + expression + "; } }");
          var copied = CopiedAstValues.capture(original.aggregate());
          var operand = original.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Literal literal
              && literal.restrictedDecimal().isPresent()).findFirst().orElseThrow();
          AnnotationScalar contradiction = expression.endsWith("L") ? new AnnotationScalar.LongValue(0) : new AnnotationScalar.IntValue(0);

          // Act
          var unchanged = AstValueAdmission.admit(copied);
          var failure = assertThrows(IllegalArgumentException.class,
              () -> AstValueAdmission.admit(withLiteralScalar(copied, operand, contradiction, captured)));

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion());
          assertEquals(original.aggregate(), unchanged);
          assertTrue(failure.getMessage() != null && !failure.getMessage().isBlank());
        })));

    // Assert
    return observations;
  }

  private record LiteralScalarCase(String expression, JavaSourceKind kind, AnnotationScalar contradiction) {
  }

  @TestFactory
  Stream<DynamicTest> givenContradictoryDerivedLiteralFacts_whenCopiedValuesAreAdmitted_thenEachActualBoundaryRejectsTheContradiction() {
    // Arrange
    var controls = List.of("type", "text", "kind", "owner");

    // Act
    var observations = controls.stream().map(control -> DynamicTest.dynamicTest(
        "givenContradictoryDerivedLiteralFact(" + control + ")_whenCopiedValuesAreAdmitted_thenTheContradictionIsRejected", () -> {
      // Arrange
      var original = produce("@interface Example { int value() default -1; String text() default \"x\"; }");
      var values = CopiedAstValues.capture(original.aggregate());
      var operand = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.INT_LITERAL).findFirst().orElseThrow();
      var association = original.aggregate().writtenSourceAssociation(operand.nodeId()).orElseThrow();
      var wrongType = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.STRING_LITERAL)
          .findFirst().orElseThrow().type();

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> {
        if (control.equals("owner")) {
          new WrittenSourceAssociation(association.nodeKey(), association.sourceFileKey(),
              association.ownerPath().child(SourceOwnerPath.Role.WRITTEN_EXPRESSION, 1), association.sourceKind(), association.origin(),
              association.normalizedExpression());
        } else {
          var root = replace(values.sources().getFirst().root(), operand.nodeId(), node -> SyntaxNode.source(node.sourceFileKey(), node.structuralPath(),
              control.equals("kind") ? JavaSourceKind.LONG_LITERAL : node.sourceKind(), node.syntaxKind(),
              control.equals("type") ? wrongType : node.type(), control.equals("text") ? new SyntaxValue.Literal("2") : node.value(),
              node.children(), node.semanticReference(), node.origin().orElseThrow()));
          AstValueAdmission.admit(withRoot(values, root, values.associations()));
        }
      });

      // Assert
      assertTrue(failure.getMessage() != null && !failure.getMessage().isBlank());
    }));

    // Assert
    return observations;
  }

  @Test
  void givenChangedDerivedRange_whenCopiedValuesAreAdmitted_thenTheOriginalSourceRangeIsEnforced() {
    // Arrange
    var original = produce("@interface Example { int value() default -123; }");
    var values = CopiedAstValues.capture(original.aggregate());
    var operand = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.INT_LITERAL).findFirst().orElseThrow();
    var span = operand.origin().orElseThrow();
    var root = replace(values.sources().getFirst().root(), operand.nodeId(), node -> SyntaxNode.source(node.sourceFileKey(), node.structuralPath(),
        node.sourceKind(), node.syntaxKind(), node.type(), node.value(), node.children(), node.semanticReference(),
        new SourceSpan(span.sourceFileKey(), span.startOffset() + 1, span.endOffsetExclusive())));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(withRoot(values, root, values.associations())));

    // Assert
    assertTrue(failure.getMessage().contains("source") || failure.getMessage().contains("written"), failure.toString());
  }

  @Test
  void givenSwappedWrittenOperandOrder_whenTheIndependentRegionIsConstructed_thenGrammarRoleOrderIsEnforced() {
    // Arrange
    var original = produce("@interface Example { String value() default \"a\" + \"b\"; }");
    var region = original.coverage().validationEvidence().writtenInventory().writtenExpressions().orElseThrow().getFirst();
    var swapped = new ArrayList<>(region.occurrences());
    Collections.swap(swapped, 1, 2);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new WrittenExpressionRegion(region.compilerOwner(), region.origin(), swapped));

    // Assert
    assertTrue(failure.getMessage().contains("ordered operand roles"), failure.toString());
  }

  @Test
  void givenFoldedReplacementWithWholeRawSliceAndValue_whenCoverageIsReconciled_thenMissingWrittenOperatorsPreventComplete() {
    // Arrange
    var original = produce("@interface Example { String value() default \"a\" + \"b\"; }");
    var values = CopiedAstValues.capture(original.aggregate());
    var expression = original.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.PLUS).findFirst().orElseThrow();
    var normalized = original.aggregate().writtenSourceAssociation(expression.nodeId()).orElseThrow().normalizedExpression().orElseThrow();
    var folded = SyntaxNode.source(expression.sourceFileKey(), expression.structuralPath(), JavaSourceKind.STRING_LITERAL, SyntaxKind.LITERAL,
        expression.type(), new SyntaxValue.Literal("ab"), List.of(), Optional.empty(), expression.origin().orElseThrow());
    var root = replace(values.sources().getFirst().root(), expression.nodeId(), ignored -> folded);
    var associations = new ArrayList<>(values.associations().stream()
        .filter(value -> root.descendantsAndSelf().stream().anyMatch(node -> node.nodeId().equals(value.nodeKey()))).toList());
    associations.add(new WrittenSourceAssociation(folded.nodeId(), folded.sourceFileKey(), normalized.region().compilerOwner(),
        JavaSourceKind.STRING_LITERAL, folded.origin()));
    var aggregate = AstValueAdmission.admit(withRoot(values, root, associations));
    var evidence = original.coverage().validationEvidence();

    // Act
    var reconciled = new JavaSourceCoverageReconciler(evidence.writtenInventory(), evidence.attributedExpectations(), original.program(),
        Optional.of(aggregate), new DiagnosticEmissions(evidence.diagnosticEmissions()))
        .reconcileRetained(original.coverage().coverage(), evidence.outcomes());
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
        reconciled.diagnostics(), reconciled.coverageResult());
    var result = AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());

    // Assert
    assertEquals(expression.origin(), folded.origin());
    assertEquals("ab", folded.value(SyntaxValue.Literal.class).text());
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertEquals(List.of(SourceContext.Partition.WRITTEN_EXPRESSION), result.coverage().validationEvidence().outcomes().stream()
        .filter(value -> value.support().mode() == FeatureContextOutcome.Mode.FAILED).map(value -> value.address().partition()).toList());
    assertTrue(result.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.missing-source-evidence")));
  }

  @Test
  void givenWrongDerivedConstant_whenCopiedValuesAreAdmitted_thenTheCapturedOperandDerivationIsRejected() {
    // Arrange
    var original = produce("class Example { String value() { return \"a\" + \"b\"; } }");
    var values = CopiedAstValues.capture(original.aggregate());
    var selected = values.associations().stream().filter(value -> value.normalizedExpression()
        .filter(correspondence -> !correspondence.occurrencePath().isEmpty()).isPresent()).findFirst().orElseThrow();
    var relation = selected.normalizedExpression().orElseThrow();
    var wrong = new NormalizedExpressionCorrespondence(relation.region(), relation.occurrencePath(), relation.compilerAnchor(),
        relation.compilerKind(), relation.compilerType(), Optional.of(new AnnotationScalar.StringValue("different")),
        relation.derivedConstantExpression(), relation.observations());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(withAssociation(values, selected, wrong)));

    // Assert
    assertTrue(failure.getMessage().contains("Derived written values"), failure.toString());
  }

  @Test
  void givenWrongCompilerAnchorOnADerivedChild_whenCopiedValuesAreAdmitted_thenTheSharedActualRegionIsEnforced() {
    // Arrange
    var original = produce("class Example { int value() { return -1; } }");
    var values = CopiedAstValues.capture(original.aggregate());
    var selected = values.associations().stream().filter(value -> value.normalizedExpression()
        .filter(correspondence -> !correspondence.occurrencePath().isEmpty()).isPresent()).findFirst().orElseThrow();
    var relation = selected.normalizedExpression().orElseThrow();
    var anchor = (CompilerExpressionAnchor.Execution) relation.compilerAnchor();
    var wrong = new NormalizedExpressionCorrespondence(relation.region(), relation.occurrencePath(),
        new CompilerExpressionAnchor.Execution(anchor.path().child(SyntaxRole.OPERAND, 0)), relation.compilerKind(), relation.compilerType(),
        relation.derivedConstant(), relation.derivedConstantExpression(), relation.observations());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(withAssociation(values, selected, wrong)));

    // Assert
    assertTrue(failure.getMessage().contains("same written region"), failure.toString());
  }

  @Test
  void givenMinimumOperandWithoutItsRestrictedMagnitude_whenCopiedValuesAreAdmitted_thenTheRequiredContextEvidenceCannotBeOmitted() {
    // Arrange
    var original = produce("@interface Example { int value() default -2147483648; }");
    var values = CopiedAstValues.capture(original.aggregate());
    var operand = original.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Literal literal
        && literal.restrictedDecimal().isPresent()).findFirst().orElseThrow();
    var root = replace(values.sources().getFirst().root(), operand.nodeId(), node -> SyntaxNode.source(node.sourceFileKey(), node.structuralPath(),
        node.sourceKind(), node.syntaxKind(), node.type(), new SyntaxValue.Literal(node.value(SyntaxValue.Literal.class).text()),
        node.children(), node.semanticReference(), node.origin().orElseThrow()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(withRoot(values, root, values.associations())));

    // Assert
    assertInstanceOf(AstContractException.class, failure);
  }

  @Test
  void givenAbsentWrittenInventoryProof_whenTheSameClosedValuesAreQualified_thenOnlyTheSuccessorRequiresThatProof() {
    // Arrange
    var original = produce("class Example { int value() { return 1; } }");
    var evidence = original.coverage().validationEvidence();
    var written = evidence.writtenInventory();
    var absent = new SourceCoverageInventory(written.snapshot(), written.release(), written.contractIdentity(), written.contexts());
    var attributed = new AttributedCoverageExpectations(absent, evidence.attributedExpectations().projections());
    var coverage = LanguageFeatureCoverageResult.fromEvidence(original.coverage().coverage(), absent, attributed,
        evidence.outcomes(), evidence.unmatchedFacts(), evidence.diagnosticEmissions());
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), coverage);

    // Act
    var previous = AstResultAdmission.admit(input, AstProfile.JAVA_SOURCE_UNITS, original.identity().semanticProductionRevision());
    var successor = AstResultAdmission.admit(input, AstProfile.JAVA_SOURCE_FIDELITY, original.identity().semanticProductionRevision());

    // Assert
    assertEquals(AstCompletion.COMPLETE, previous.completion());
    assertEquals(AstCompletion.INCOMPLETE, successor.completion());
  }

  private AstResult produce(String source) {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_FIDELITY)).result().orElseThrow();
  }

  private static SyntaxNode replace(SyntaxNode node, String key, UnaryOperator<SyntaxNode> operation) {
    if (node.nodeId().equals(key)) {
      return operation.apply(node);
    }
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replace(child.node(), key, operation))).toList();
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(),
        children, node.semanticReference(), node.origin().orElseThrow());
  }

  private static CopiedAstValues withRoot(CopiedAstValues values, SyntaxNode root, List<WrittenSourceAssociation> associations) {
    var nodes = root.descendantsAndSelf();
    var source = new SyntaxSourceContribution(root.sourceFileKey(), root, nodes,
        nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(),
        values.sources().stream().flatMap(value -> value.semanticEdges().stream())
            .filter(edge -> nodes.stream().anyMatch(node -> node.nodeId().equals(edge.sourceKey()))).toList());
    return new CopiedAstValues(values.snapshot(), List.of(source), values.bodies(), values.memberOrders(), associations, values.declarationTypes(), values.scopes());
  }

  private static CopiedAstValues withAssociation(CopiedAstValues values, WrittenSourceAssociation selected, NormalizedExpressionCorrespondence relation) {
    var associations = values.associations().stream().map(value -> value.equals(selected)
        ? new WrittenSourceAssociation(value.nodeKey(), value.sourceFileKey(), value.ownerPath(), value.sourceKind(), value.origin(), Optional.of(relation))
        : value).toList();
    return new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), associations, values.declarationTypes(), values.scopes());
  }

  private static CopiedAstValues withLiteralScalar(CopiedAstValues values, SyntaxNode literal, AnnotationScalar scalar, boolean captured) {
    var selected = values.associations().stream().filter(value -> value.nodeKey().equals(literal.nodeId())).findFirst().orElseThrow()
        .normalizedExpression().orElseThrow();
    var region = captured
        ? new WrittenExpressionRegion(selected.region().compilerOwner(), selected.region().origin(), selected.region().occurrences().stream()
            .map(occurrence -> occurrence.path().equals(selected.occurrencePath())
                ? new WrittenExpressionRegion.Occurrence(occurrence.path(), occurrence.sourceKind(), occurrence.origin(),
                    occurrence.compilerOperand(), occurrence.restrictedDecimal(), Optional.of(scalar))
                : occurrence).toList())
        : selected.region();
    var associations = values.associations().stream().map(association -> {
      if (association.normalizedExpression().filter(value -> value.region().equals(selected.region())).isEmpty()) {
        return association;
      }
      var relation = association.normalizedExpression().orElseThrow();
      var replacement = new NormalizedExpressionCorrespondence(region, relation.occurrencePath(), relation.compilerAnchor(),
          relation.compilerKind(), relation.compilerType(), relation.occurrencePath().equals(selected.occurrencePath())
              ? Optional.of(scalar) : relation.derivedConstant(), relation.derivedConstantExpression(), relation.observations());
      return new WrittenSourceAssociation(association.nodeKey(), association.sourceFileKey(), association.ownerPath(), association.sourceKind(),
          association.origin(), Optional.of(replacement));
    }).toList();
    return new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), associations, values.declarationTypes(), values.scopes());
  }
}
