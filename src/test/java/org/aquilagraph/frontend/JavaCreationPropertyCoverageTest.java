package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class JavaCreationPropertyCoverageTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String ARGUMENTS = "NewClassTree::NewClassTree#getArguments()";
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenWrittenCreationForms_whenProducedAndDecoded_thenEveryAccessorHasExactIndependentEvidence() {
    // Arrange
    var cases = List.of(
        new CreationCase("ordinary empty", "new Object()", "", item("IDENTIFIER:0", "Object"), List.of(), List.of(), ""),
        new CreationCase("ordinary populated", "new Target(1, 2)", "", item("IDENTIFIER:0", "Target"), List.of(),
            List.of(item("INT_LITERAL:0", "1"), item("INT_LITERAL:1", "2")), ""),
        new CreationCase("explicit constructor type argument", "new <String> Target(\"x\")", "", item("IDENTIFIER:0", "Target"),
            List.of(item("IDENTIFIER:0", "String")), List.of(item("STRING_LITERAL:0", "\"x\"")), ""),
        new CreationCase("diamond", "new java.util.ArrayList<>()", "", item("PARAMETERIZED_TYPE:0", "java.util.ArrayList<>"),
            List.of(), List.of(), ""),
        new CreationCase("unqualified anonymous", "new Target(1, 2) { int field = 3; }", "", item("IDENTIFIER:0", "Target"),
            List.of(), List.of(item("INT_LITERAL:0", "1"), item("INT_LITERAL:1", "2")), "{ int field = 3; }"),
        new CreationCase("qualified ordinary", "owner.new Inner(\"x\")", "owner", item("IDENTIFIER:0", "Inner"), List.of(),
            List.of(item("STRING_LITERAL:0", "\"x\"")), ""),
        new CreationCase("qualified generic anonymous", "owner.new <String> Inner(\"x\") { int field = 3; }", "owner",
            item("IDENTIFIER:0", "Inner"), List.of(item("IDENTIFIER:0", "String")),
            List.of(item("STRING_LITERAL:0", "\"x\"")), "{ int field = 3; }"));

    // Act
    var observations = cases.stream().map(value -> DynamicTest.dynamicTest(value.name(), () -> {
      var source = "class Target { Target(int x, int y) {} <T> Target(T value) {} } "
          + "class Outer<T> { class Inner { <U> Inner(U value) {} } } "
          + "class Example { Outer<String> owner; Object value = " + value.expression() + "; }";
      var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
      var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS)
          .result().orElseThrow();
      var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
      var evidence = retained.coverage().validationEvidence();
      var contexts = evidence.writtenInventory().contexts().stream()
          .filter(context -> context.address().partition() == SourceContext.Partition.WRITTEN_PROPERTY
              && context.address().role().startsWith("NewClassTree::")).toList();
      var expected = Map.of("NewClassTree::Tree#getKind()", scalar(item("NEW_CLASS", "NEW_CLASS")),
          "NewClassTree::NewClassTree#getEnclosingExpression()",
          value.enclosing().isEmpty() ? absent() : scalar(item("IDENTIFIER:0", value.enclosing())),
          "NewClassTree::NewClassTree#getIdentifier()", scalar(value.identifier()),
          "NewClassTree::NewClassTree#getTypeArguments()", sequence(value.typeArguments()),
          ARGUMENTS, sequence(value.arguments()), "NewClassTree::NewClassTree#getClassBody()",
          value.body().isEmpty() ? absent() : scalar(item("CLASS:0", value.body())));

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(6, contexts.size());
      assertEquals(expected.keySet(), contexts.stream().map(context -> context.address().role()).collect(Collectors.toSet()));
      var creation = retained.aggregate().nodes().stream().filter(node -> node.origin().map(span -> source.substring(
          Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive())).equals(value.expression())).orElse(false))
          .findFirst().orElseThrow();
      for (var context : contexts) {
        var property = expected.get(context.address().role());
        assertEquals(List.of(creation.origin().orElseThrow()), context.origins());
        assertEquals(property, context.writtenValue().orElseThrow());
        var outcome = evidence.outcomes().stream().filter(candidate -> candidate.address().equals(context.address())).findFirst().orElseThrow();
        assertEquals(property, outcome.encounteredValue().orElseThrow());
        assertEquals(property.state() == State.ABSENT || property.state() == State.EMPTY
            ? FeatureContextOutcome.Mode.NEGATIVE : FeatureContextOutcome.Mode.FACTS, outcome.support().mode());
      }
      assertEquals(original.coverage(), retained.coverage());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
      assertTrue(evidence.unmatchedFacts().isEmpty());
    }));

    // Assert
    return observations;
  }

  @Test
  void givenCapturedCreationProperties_whenAnOutcomeIsOmitted_thenIndependentInventoryRejectsTheMissingAccessor() throws IOException {
    // Arrange
    var original = analyze("class Example { Object value = new Object(); }");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    assertEquals(1, evidence.outcomes().stream().filter(outcome -> outcome.address().role().equals(ARGUMENTS)).count());
    var outcomes = evidence.outcomes().stream().filter(outcome -> !outcome.address().role().equals(ARGUMENTS)).toList();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> LanguageFeatureCoverageResult.fromEvidence(
        original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(), outcomes,
        evidence.unmatchedFacts(), evidence.diagnosticEmissions()));

    // Assert
    assertEquals("Coverage outcomes must exactly match the independent context inventory in order", failure.getMessage());
  }

  @Test
  void givenWrittenCreationArgument_whenCaptureAndEncounterBothClaimEmpty_thenActualOwnerRejectsTheContradiction() throws IOException {
    // Arrange
    var original = analyze("class Example { Object value = new String(\"x\"); }");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    assertEquals(1, evidence.outcomes().stream().filter(outcome -> outcome.address().role().equals(ARGUMENTS)).count());
    var empty = sequence(List.of());
    var contexts = evidence.writtenInventory().contexts().stream().map(context -> context.address().role().equals(ARGUMENTS)
        ? new SourceContext(context.address(), context.feature(), context.origins(), Optional.of(empty), false) : context).toList();
    var written = new SourceCoverageInventory(original.snapshot(), CONFIGURATION.release(), original.languageFeatureCoverage().contractIdentity(), contexts);
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.address().role().equals(ARGUMENTS)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), Optional.of(empty),
            new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.NEGATIVE, List.of(), Optional.empty(), Optional.empty(), Optional.of(empty)), List.of())
        : outcome).toList();
    var claimed = LanguageFeatureCoverageResult.fromEvidence(original.languageFeatureCoverage(), written,
        new AttributedCoverageExpectations(written, evidence.attributedExpectations().projections()), outcomes,
        evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), claimed));

    // Assert
    assertEquals("Claimed negative coverage support requires an actual existing owner with absent or empty accessor state", failure.getMessage());
  }

  private SyntaxAnalysisInput analyze(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
    return new SyntaxAnalysisInput(result.snapshot(), result.environment(), result.program(), Optional.of(result.aggregate()),
        result.diagnostics(), result.coverage());
  }

  private static Item item(String identity, String value) {
    return new Item(identity, value, Provenance.WRITTEN);
  }

  private static CoveragePropertyValue scalar(Item item) {
    return new CoveragePropertyValue(State.SCALAR, List.of(item));
  }

  private static CoveragePropertyValue sequence(List<Item> items) {
    return new CoveragePropertyValue(items.isEmpty() ? State.EMPTY : State.ITEMS, items);
  }

  private static CoveragePropertyValue absent() {
    return new CoveragePropertyValue(State.ABSENT, List.of());
  }

  private record CreationCase(String name, String expression, String enclosing, Item identifier, List<Item> typeArguments,
      List<Item> arguments, String body) {}
}
