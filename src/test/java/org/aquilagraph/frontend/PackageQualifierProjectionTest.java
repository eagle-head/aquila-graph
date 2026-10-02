package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.CoverageEvidenceValue;
import org.aquilagraph.model.CoverageEvidenceValue.Field;
import org.aquilagraph.model.CoverageEvidenceValue.Named;
import org.aquilagraph.model.CoverageEvidenceValue.OptionalValue;
import org.aquilagraph.model.CoverageEvidenceValue.Origin;
import org.aquilagraph.model.CoverageEvidenceValue.Structure;
import org.aquilagraph.model.CoverageEvidenceValue.Tag;
import org.aquilagraph.model.CoverageEvidenceValue.Text;
import org.aquilagraph.model.CoverageFactReference;
import org.aquilagraph.model.CoverageFactValue;
import org.aquilagraph.model.CoverageSourceFact;
import org.aquilagraph.model.FeatureContextOutcome;
import org.aquilagraph.model.FeatureContextOutcome.Mode;
import org.aquilagraph.model.FeatureContextOutcome.Support;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.LanguageFeatureCoverageResult;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.CoverageActualFacts;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class PackageQualifierProjectionTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @TempDir Path sourceRoot;

  @Test
  void givenAnActualQualifiedPackageName_whenNeutralFactsAreCopied_thenTheQualifierTagKindAndRangeRemainExact() throws IOException {
    // Arrange
    var input = analyze();
    var sourceKey = input.snapshot().sourceFiles().getFirst().sourceFileKey();
    var expected = new Structure(Tag.PACKAGE_DECLARATION,
        List.of(new Field("name", new Text("sample.nested")), new Field("qualifier", qualifier(Tag.PACKAGE_QUALIFIER_EVIDENCE,
            new Named("MEMBER_SELECT"), new SourceSpan(sourceKey, 8, 21)))));

    // Act
    var actual = packageFact(input);
    var copied = assertInstanceOf(CoverageFactValue.Source.class, actual.value()).value();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(sourceKey, actual.sourceFileKey());
    assertEquals(expected, copied.value());
  }

  @TestFactory
  Stream<DynamicTest> givenFixedActualPackageSyntax_whenItsClaimedNeutralQualifierIsRemovedOrChanged_thenInputRejectsFalseSupport() {
    return Stream.of("absent", "tag", "sourceKind", "origin")
        .map(field -> DynamicTest.dynamicTest("package-qualifier/" + field, () -> assertFalseQualifierRejected(field)));
  }

  private void assertFalseQualifierRejected(String field) throws IOException {
    // Arrange
    var input = analyze();
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var actual = packageFact(input);
    var source = assertInstanceOf(CoverageFactValue.Source.class, actual.value()).value();
    var originalQualifier = new SourceSpan(input.snapshot().sourceFiles().getFirst().sourceFileKey(), 8, 21);
    var expected = qualifier(Tag.PACKAGE_QUALIFIER_EVIDENCE, new Named("MEMBER_SELECT"), originalQualifier);
    assertEquals(expected, source.value().fields().stream().filter(component -> component.name().equals("qualifier"))
        .findFirst().orElseThrow().value());
    var falseQualifier = "absent".equals(field) ? new OptionalValue(Optional.empty())
        : qualifier("tag".equals(field) ? Tag.WRITTEN_TYPE : Tag.PACKAGE_QUALIFIER_EVIDENCE,
            new Named("sourceKind".equals(field) ? "IDENTIFIER" : "MEMBER_SELECT"),
            "origin".equals(field) ? new SourceSpan(originalQualifier.sourceFileKey(), 8, 20) : originalQualifier);
    var fields = source.value().fields().stream().map(component -> component.name().equals("qualifier")
        ? new Field(component.name(), falseQualifier) : component).toList();
    var changed = new CoverageSourceFact(source.structuralPath(), source.sourceKind(), source.syntaxKind(), source.type(),
        new Structure(source.value().tag(), fields), source.children(), source.semanticReference(), source.sourceAssociation());
    var claimed = new CoverageFactReference(actual.kind(), actual.identity(), actual.sourceFileKey(), new CoverageFactValue.Source(changed), actual.origin());
    var selected = evidence.outcomes().stream().filter(outcome -> outcome.support().mode() == Mode.FACTS && outcome.support().facts().contains(actual))
        .findFirst().orElseThrow();
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(selected)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
            new Support(Mode.FACTS, outcome.support().facts().stream().map(fact -> fact.equals(actual) ? claimed : fact).toList(), Optional.empty(),
                Optional.empty(), Optional.empty()), outcome.emissions()) : outcome).toList();
    var result = LanguageFeatureCoverageResult.fromEvidence(input.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(),
        input.aggregate(), input.diagnostics(), result), field);

    // Assert
    assertEquals("Claimed positive coverage support must match an exact actual owned fact value", failure.getMessage());
    assertEquals(expected, assertInstanceOf(CoverageFactValue.Source.class, packageFact(input).value()).value().value().fields().stream()
        .filter(component -> component.name().equals("qualifier")).findFirst().orElseThrow().value());
  }

  private static OptionalValue qualifier(Tag tag, CoverageEvidenceValue kind, SourceSpan origin) {
    return new OptionalValue(Optional.of(new Structure(tag, List.of(new Field("sourceKind", kind), new Field("origin", new Origin(origin))))));
  }

  private static CoverageFactReference packageFact(SyntaxAnalysisInput input) {
    return CoverageActualFacts.capture(input.program(), input.aggregate()).stream()
        .filter(fact -> fact.value() instanceof CoverageFactValue.Source source && source.value().value().tag() == Tag.PACKAGE_DECLARATION)
        .findFirst().orElseThrow();
  }

  private SyntaxAnalysisInput analyze() throws IOException {
    var snapshot = InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Example.java"), "package sample.nested; class Example {}")));
    return new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CONFIGURATION);
  }
}
