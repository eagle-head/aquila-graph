package org.aquilagraph.frontend.internal;

import org.aquilagraph.source.admission.CoverageAdmission;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.FeatureContextOutcome.Mode;
import org.aquilagraph.source.api.FeatureContextOutcome.Support;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceContext.Partition;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;

class SourceContextAdmissionTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String UNIT_SOURCE = "CompilationUnitTree::CompilationUnitTree#getSourceFile()";

  @Test
  void givenRepresentedMethodTree_whenClaimedAsAttributedKind_thenFactoryRejectsTheWrongFeatureBoundary() throws IOException {
    // Arrange
    var input = analyze();
    var selected = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().partition() == Partition.TREE
            && outcome.occurrence().orElseThrow().classification().feature().identifier().equals("METHOD"))
        .findFirst().orElseThrow();
    var address = readdress(selected.address(), Partition.TYPE, selected.address().role());
    var context = new SourceContext(address, selected.occurrence().map(value -> value.classification().feature()),
        selected.occurrence().orElseThrow().origins(), Optional.empty(), false);
    var outcome = copy(selected, address, new Support(Mode.ATTRIBUTED_PAIR, List.of(), Optional.empty(), Optional.empty(), Optional.empty()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedContext(input, selected, context, outcome));

    // Assert
    assertEquals("Context policy must match its exact source evidence partition", failure.getMessage());
  }

  @Test
  void givenAttributedKind_whenClaimedAsTreeEvidence_thenFactoryRejectsTheWrongFeatureBoundary() throws IOException {
    // Arrange
    var input = analyze();
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var selected = evidence.outcomes().stream().filter(outcome -> outcome.support().mode() == Mode.ATTRIBUTED_PAIR).findFirst().orElseThrow();
    var value = selected.encounteredValue().orElseThrow();
    var address = readdress(selected.address(), Partition.TREE, "tree");
    var context = new SourceContext(address, selected.occurrence().map(item -> item.classification().feature()),
        selected.occurrence().orElseThrow().origins(), Optional.of(value), false);
    var owner = evidence.outcomes().stream().filter(outcome -> outcome.support().mode() == Mode.FACTS).findFirst().orElseThrow();
    var outcome = copy(selected, address, owner.support());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedContext(input, selected, context, outcome));

    // Assert
    assertEquals("Context policy must match its exact source evidence partition", failure.getMessage());
  }

  @Test
  void givenAttributedAccessor_whenClaimedAsWrittenAccessor_thenFactoryRejectsTheWrongOwnerSurface() throws IOException {
    // Arrange
    var input = analyze();
    var selected = property(input, Partition.ATTRIBUTED_PROPERTY, "TypeElement::TypeElement#getSimpleName()");
    var address = readdress(selected.address(), Partition.WRITTEN_PROPERTY, selected.address().role());
    var context = new SourceContext(address, selected.occurrence().map(value -> value.classification().feature()),
        selected.occurrence().orElseThrow().origins(), selected.encounteredValue(), false);
    var outcome = copy(selected, address, selected.support());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedContext(input, selected, context, outcome));

    // Assert
    assertEquals("Declaration property contexts require their exact accessor and owner surface", failure.getMessage());
  }

  @Test
  void givenWrittenEmptyImports_whenClaimedAsAttributedAccessor_thenFactoryRejectsTheWrongOwnerSurface() throws IOException {
    // Arrange
    var input = analyze();
    var selected = property(input, Partition.WRITTEN_PROPERTY, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS);
    var address = readdress(selected.address(), Partition.ATTRIBUTED_PROPERTY, selected.address().role());
    var context = new SourceContext(address, selected.occurrence().map(value -> value.classification().feature()),
        selected.occurrence().orElseThrow().origins(), selected.encounteredValue(), false);
    var outcome = copy(selected, address, selected.support());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedContext(input, selected, context, outcome));

    // Assert
    assertEquals("Declaration property contexts require their exact accessor and owner surface", failure.getMessage());
  }

  @Test
  void givenEmptyImportAccessor_whenItsRoleClaimsSnapshotSourceSupport_thenFactoryRejectsTheWrongAccessor() throws IOException {
    // Arrange
    var input = analyze();
    var selected = property(input, Partition.WRITTEN_PROPERTY, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS);
    var address = readdress(selected.address(), Partition.WRITTEN_PROPERTY, UNIT_SOURCE);
    var context = new SourceContext(address, selected.occurrence().map(value -> value.classification().feature()),
        selected.occurrence().orElseThrow().origins(), selected.encounteredValue(), false);
    var outcome = copy(selected, address,
        new Support(Mode.SOURCE_FILE, List.of(), Optional.empty(), Optional.of(input.snapshot().sourceFiles().getFirst()), Optional.empty()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedContext(input, selected, context, outcome));

    // Assert
    assertEquals("Declaration property contexts require their exact accessor and owner surface", failure.getMessage());
  }

  @Test
  void givenAbsentInitializer_whenPositiveOwnerFactsReplaceNegativeSupport_thenFactoryRejectsTheWrongMode() throws IOException {
    // Arrange
    var input = analyze();
    var selected = property(input, Partition.WRITTEN_PROPERTY, "VariableTree::VariableTree#getInitializer()");
    var owner = positiveOwner(input, selected);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedSupport(input, selected, owner.support()));

    // Assert
    assertEquals(CoveragePropertyValue.State.ABSENT, selected.encounteredValue().orElseThrow().state());
    assertEquals("Represented success requires its exact catalog support mode", failure.getMessage());
  }

  @Test
  void givenEmptyImports_whenPositiveUnitFactsReplaceNegativeSupport_thenFactoryRejectsTheWrongMode() throws IOException {
    // Arrange
    var input = analyze();
    var selected = property(input, Partition.WRITTEN_PROPERTY, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS);
    var owner = positiveOwner(input, selected);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> changedSupport(input, selected, owner.support()));

    // Assert
    assertEquals(CoveragePropertyValue.State.EMPTY, selected.encounteredValue().orElseThrow().state());
    assertEquals("Represented success requires its exact catalog support mode", failure.getMessage());
  }

  @Test
  void givenAllRepresentedSupportBoundaries_whenActualFrontendInputIsAdmitted_thenEachExactModeAndValueRemains() throws IOException {
    // Arrange
    var input = analyze();
    var evidence = input.languageFeatureCoverageResult().validationEvidence();

    // Act
    var result = CoverageAdmission.fromEvidence(input.languageFeatureCoverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), evidence.diagnosticEmissions());
    var retained = new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(),
        input.diagnostics(), result);

    // Assert
    assertEquals(input.languageFeatureCoverageResult(), retained.languageFeatureCoverageResult());
    assertTrue(evidence.outcomes().stream().map(outcome -> outcome.support().mode()).toList()
        .containsAll(Set.of(Mode.FACTS, Mode.NEGATIVE, Mode.POLICY_PAIR, Mode.SOURCE_FILE, Mode.ATTRIBUTED_PAIR)));
    assertEquals(List.of(), retained.diagnostics());
  }

  private static SyntaxAnalysisInput analyze() throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/context-check/sources"),
        List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { int field; int value(int parameter) { return parameter; } }")));
    return new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CONFIGURATION, JavaTranslationPolicy.legacyAnalysis());
  }

  private static FeatureContextOutcome property(SyntaxAnalysisInput input, Partition partition, String accessor) {
    return input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().partition() == partition && outcome.address().role().equals(accessor)).findFirst().orElseThrow();
  }

  private static FeatureContextOutcome positiveOwner(SyntaxAnalysisInput input, FeatureContextOutcome selected) {
    return input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().sourceFileKey().equals(selected.address().sourceFileKey())
            && outcome.address().ownerPath().equals(selected.address().ownerPath()) && outcome.support().mode() == Mode.FACTS)
        .findFirst().orElseThrow();
  }

  private static LanguageFeatureCoverageResult changedSupport(SyntaxAnalysisInput input, FeatureContextOutcome selected, Support support) {
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(selected) ? copy(outcome, outcome.address(), support) : outcome)
        .toList();
    return CoverageAdmission.fromEvidence(input.languageFeatureCoverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());
  }

  private static LanguageFeatureCoverageResult changedContext(SyntaxAnalysisInput input, FeatureContextOutcome selected, SourceContext context,
      FeatureContextOutcome replacement) {
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var contexts = evidence.writtenInventory().contexts().stream().map(value -> value.address().equals(selected.address()) ? context : value)
        .sorted(Comparator.comparing(value -> value.address().partition())).toList();
    var written = new SourceCoverageInventory(input.snapshot(), input.languageFeatureCoverage().release(),
        input.languageFeatureCoverage().contractIdentity(), contexts);
    var projections = evidence.attributedExpectations().projections().stream().filter(value -> !value.address().equals(selected.address()))
        .toList();
    var outcomes = evidence.outcomes().stream().map(value -> value.equals(selected) ? replacement : value)
        .sorted(Comparator.comparing(value -> value.address().partition())).toList();
    return CoverageAdmission.fromEvidence(input.languageFeatureCoverage(), written, new AttributedCoverageExpectations(written, projections),
        outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());
  }

  private static SourceContext.Address readdress(SourceContext.Address address, Partition partition, String role) {
    return new SourceContext.Address(address.sourceFileKey(), partition, address.ownerPath(), role, address.ordinal() + 1);
  }

  private static FeatureContextOutcome copy(FeatureContextOutcome outcome, SourceContext.Address address, Support support) {
    return new FeatureContextOutcome(address, outcome.occurrence(), outcome.observation(), outcome.encounteredValue(), support, outcome.emissions());
  }
}
