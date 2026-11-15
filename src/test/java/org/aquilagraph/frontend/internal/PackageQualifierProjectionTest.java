package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.admission.CoverageAdmission;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.aquilagraph.source.api.CoverageEvidenceValue.Field;
import org.aquilagraph.source.api.CoverageEvidenceValue.Named;
import org.aquilagraph.source.api.CoverageEvidenceValue.OptionalValue;
import org.aquilagraph.source.api.CoverageEvidenceValue.Origin;
import org.aquilagraph.source.api.CoverageEvidenceValue.Structure;
import org.aquilagraph.source.api.CoverageEvidenceValue.Tag;
import org.aquilagraph.source.api.CoverageEvidenceValue.Text;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoverageSourceFact;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.FeatureContextOutcome.Mode;
import org.aquilagraph.source.api.FeatureContextOutcome.Support;
import org.aquilagraph.source.admission.CoverageActualFacts;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

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
    var result = CoverageAdmission.fromEvidence(input.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
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
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(sourceRoot), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "package sample.nested; class Example {}")));
    return new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CONFIGURATION, JavaTranslationPolicy.legacyAnalysis());
  }
}
