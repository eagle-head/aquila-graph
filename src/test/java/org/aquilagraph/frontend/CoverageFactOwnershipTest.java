package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.UnmatchedSourceFact;
import org.aquilagraph.source.api.CoverageActualFacts;
import org.aquilagraph.source.api.AggregateFixture;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class CoverageFactOwnershipTest {
  private static final String CLASS_SOURCE = "class First {} class Second { int first; void value() {} int second; }";
  private static final String RECORD_SOURCE = "class First {} record Second(int value) {}";
  private static final SourceOwnerPath SECOND_TYPE = SourceOwnerPath.root().child(SourceOwnerPath.Role.TYPE_DECLARATION, 1);

  @TempDir
  Path directory;

  @Test
  void givenActualNoOriginConstructor_whenMemberDisplayChanges_thenFrozenExpectationsRejectSuccessAndRetainItsDeclaringOwner()
      throws IOException {
    // Arrange
    var original = analyze(CLASS_SOURCE);
    assertTrue(original.diagnostics().isEmpty(), () -> original.diagnostics().toString());
    var changed = changedDisplay(original);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> suppliedInput(original, changed));
    var partial = reconcile(original, changed);

    // Assert
    assertEquals("Claimed positive coverage support must match an exact actual owned fact value", failure.getMessage());
    assertFrozenExpectations(original, partial);
    assertEquals(FeatureContextOutcome.Mode.FAILED, enclosedOutcome(partial).support().mode());
    assertTrue(partial.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-source-evidence")));
    var unmatched = constructorExtra(partial);
    assertEquals(SECOND_TYPE, unmatched.owner().ownerPath());
    assertEquals(CoverageFactReference.Kind.METHOD, unmatched.fact().kind());
    assertEquals("type:Second#<init>()", unmatched.fact().identity());
    assertEquals(Optional.empty(), unmatched.fact().origin());
  }

  @Test
  void givenActualDerivedConstructor_whenSidecarProvenanceChanges_thenFrozenExpectationsRejectSuccessfulSupport() throws IOException {
    // Arrange
    var original = analyze(CLASS_SOURCE);
    var changed = alteredOrder(original, member -> member.declarationKey().equals("type:Second#<init>()")
        ? new DeclarationMemberOrder.Member(member.declarationKey(), member.kind(), member.displayValue(),
            CoveragePropertyValue.Provenance.WRITTEN, member.writtenOrdinal())
        : member);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> suppliedInput(original, changed));
    var partial = reconcile(original, changed);

    // Assert
    assertEquals("Claimed positive coverage support must match an exact actual owned fact value", failure.getMessage());
    assertFrozenExpectations(original, partial);
    assertEquals(FeatureContextOutcome.Mode.FAILED, enclosedOutcome(partial).support().mode());
    assertEquals(SECOND_TYPE, constructorExtra(partial).owner().ownerPath());
    assertEquals(CoveragePropertyValue.Provenance.COMPILER_DERIVED,
        original.aggregate().orElseThrow().declarationMemberOrder("type:Second").orElseThrow().members().stream()
            .filter(member -> member.declarationKey().equals("type:Second#<init>()")).findFirst().orElseThrow().provenance());
  }

  @Test
  void givenDiagnosedNoOriginConstructor_whenClaimNamesUnrelatedSibling_thenInputRejectsTheWrongCapturedOwner() throws IOException {
    // Arrange
    var original = analyze(CLASS_SOURCE);
    var partial = reconcile(original, changedDisplay(original));
    var first = capturedType(partial, "CLASS", SourceOwnerPath.root().child(SourceOwnerPath.Role.TYPE_DECLARATION, 0));
    var claimed = claimedOwner(partial, constructorExtra(partial), first);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(partial.snapshot(), partial.environmentSnapshot(),
        partial.program(), partial.aggregate(), claimed.validationEvidence().diagnosticEmissions().stream().map(DiagnosticEmission::diagnostic).toList(),
        claimed));

    // Assert
    assertEquals("Diagnosed unmatched facts must form an exact bijection with actual extra fact slots", failure.getMessage());
    assertEquals(partial.languageFeatureCoverageResult().validationEvidence().writtenInventory(), claimed.validationEvidence().writtenInventory());
    assertEquals(partial.languageFeatureCoverageResult().validationEvidence().attributedExpectations(), claimed.validationEvidence().attributedExpectations());
    assertEquals(partial.languageFeatureCoverageResult().validationEvidence().outcomes(), claimed.validationEvidence().outcomes());
    assertEquals(constructorExtra(partial).fact(), claimed.validationEvidence().unmatchedFacts().stream()
        .filter(fact -> fact.fact().identity().equals("type:Second#<init>()")).findFirst().orElseThrow().fact());
  }

  @Test
  void givenDiagnosedNoOriginConstructor_whenClaimKeepsItsDeclaringType_thenBlockedPartialInputRemainsAdmissible() throws IOException {
    // Arrange
    var original = analyze(CLASS_SOURCE);
    var partial = reconcile(original, changedDisplay(original));
    var second = capturedType(partial, "CLASS", SECOND_TYPE);
    var extra = constructorExtra(partial);
    var claimed = claimedOwner(partial, extra, second);

    // Act
    var admitted = new SyntaxAnalysisInput(partial.snapshot(), partial.environmentSnapshot(), partial.program(), partial.aggregate(),
        claimed.validationEvidence().diagnosticEmissions().stream().map(DiagnosticEmission::diagnostic).toList(), claimed);

    // Assert
    assertFrozenExpectations(original, admitted);
    assertEquals(FeatureContextOutcome.Mode.FAILED, enclosedOutcome(admitted).support().mode());
    assertEquals(extra.fact(), constructorExtra(admitted).fact());
    assertEquals(extra.ordinal(), constructorExtra(admitted).ordinal());
    assertEquals(extra.emission(), constructorExtra(admitted).emission());
    assertEquals(second.address(), constructorExtra(admitted).owner());
    assertTrue(admitted.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unexpected-source-evidence")));
    assertEquals(partial.diagnostics(), admitted.diagnostics());
  }

  @Test
  void givenActualRecordDerivedMembers_whenDeclaringOwnerIsResolved_thenOriginlessFieldMethodParameterAndAccessorKeepTheRecordOwner()
      throws IOException {
    // Arrange
    var original = analyze(RECORD_SOURCE);
    assertTrue(original.diagnostics().isEmpty(), () -> original.diagnostics().toString());
    var facts = CoverageActualFacts.capture(original.program(), original.aggregate());
    var expected = Map.of("type:Second#field:value", CoverageFactReference.Kind.FIELD,
        "type:Second#<init>(int)", CoverageFactReference.Kind.METHOD,
        "type:Second#<init>(int):parameter:0", CoverageFactReference.Kind.PARAMETER,
        "type:Second#value()", CoverageFactReference.Kind.SYNTHESIZED_DECLARATION);

    // Act
    var owners = expected.keySet().stream().map(key -> {
      var fact = facts.stream().filter(candidate -> candidate.identity().equals(key)).findFirst().orElseThrow();
      var evidence = original.languageFeatureCoverageResult().validationEvidence();
      return Map.entry(fact, CoverageActualFacts.capturedDeclarationOwner(fact,
          evidence.writtenInventory(), evidence.attributedExpectations(), facts).orElseThrow());
    }).toList();

    // Assert
    for (var selected : owners) {
      var owner = selected.getValue();
      assertEquals(expected.get(selected.getKey().identity()), selected.getKey().kind());
      assertEquals(Optional.empty(), selected.getKey().origin());
      assertEquals(SECOND_TYPE, owner.address().ownerPath());
      assertEquals("RECORD", owner.feature().orElseThrow().identifier());
      assertEquals(List.of(new SourceSpan(original.snapshot().sourceFiles().getFirst().sourceFileKey(), 15, 42)), owner.origins());
    }
  }

  @Test
  void givenActualCompactDeclarationsWithoutOrigins_whenDeclaringOwnerIsResolved_thenTheRealCompactUnitOwnsTheTypeAndSidecars()
      throws IOException {
    // Arrange
    var original = analyze("int value; void main() {}");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var facts = CoverageActualFacts.capture(original.program(), original.aggregate());
    var expected = Map.of("type:Sample", CoverageFactReference.Kind.TYPE,
        "type:Sample#enclosed-order", CoverageFactReference.Kind.DECLARATION_MEMBER_ORDER,
        "type:Sample#declared-type", CoverageFactReference.Kind.DECLARATION_TYPE,
        "type:Sample#<init>()", CoverageFactReference.Kind.METHOD);
    var diagnostics = List.copyOf(original.diagnostics());

    // Act
    var owners = expected.keySet().stream().map(key -> {
      var fact = facts.stream().filter(candidate -> candidate.identity().equals(key)).findFirst().orElseThrow();
      return Map.entry(fact, CoverageActualFacts.capturedDeclarationOwner(fact,
          evidence.writtenInventory(), evidence.attributedExpectations(), facts).orElseThrow());
    }).toList();

    // Assert
    for (var selected : owners) {
      assertEquals(expected.get(selected.getKey().identity()), selected.getKey().kind());
      assertEquals(Optional.empty(), selected.getKey().origin());
      assertEquals(SourceOwnerPath.root(), selected.getValue().address().ownerPath());
      assertEquals("COMPILATION_UNIT", selected.getValue().feature().orElseThrow().identifier());
      assertEquals(List.of(), selected.getValue().origins());
    }
    assertEquals(diagnostics, original.diagnostics());
    assertEquals(diagnostics, evidence.diagnosticEmissions().stream().map(DiagnosticEmission::diagnostic).toList());
  }

  private SyntaxAnalysisInput analyze(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Sample.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
  }

  private static JavaAstAggregate changedDisplay(SyntaxAnalysisInput original) {
    return alteredOrder(original, member -> member.declarationKey().equals("type:Second#field:first")
        ? new DeclarationMemberOrder.Member(member.declarationKey(), member.kind(), "FIELD:altered", member.provenance(), member.writtenOrdinal())
        : member);
  }

  private static JavaAstAggregate alteredOrder(SyntaxAnalysisInput original, UnaryOperator<DeclarationMemberOrder.Member> change) {
    var actual = original.aggregate().orElseThrow();
    var builder = AggregateFixture.builder(original.snapshot());
    for (var root : actual.roots()) {
      var nodes = root.descendantsAndSelf();
      builder.add(new SyntaxSourceContribution(root.sourceFileKey(), root, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), actual.semanticEdges()));
    }
    actual.writtenSourceAssociations().forEach(builder::addWrittenSourceAssociation);
    actual.declarationTypeEvidence().forEach(builder::addDeclarationTypeEvidence);
    actual.declarationMemberOrders().stream().map(order -> order.ownerTypeKey().equals("type:Second")
        ? new DeclarationMemberOrder(order.sourceFileKey(), order.ownerTypeKey(), order.members().stream().map(change).toList()) : order)
        .forEach(builder::addDeclarationMemberOrder);
    for (var key : actual.methodBodyKeys()) {
      var syntax = actual.methodSyntax(key).orElseThrow();
      var nodes = syntax.root().descendantsAndSelf();
      var uses = nodes.stream().flatMap(node -> node.children().stream()
          .flatMap(child -> syntax.useDecision(node.nodeId(), child.role(), child.position()).stream())).toList();
      var calls = nodes.stream().flatMap(node -> syntax.invocationArity(node.nodeId()).stream()).toList();
      var accesses = nodes.stream().flatMap(node -> syntax.staticAccess(node.nodeId()).stream()).toList();
      var concatenations = nodes.stream().flatMap(node -> syntax.stringConcatenation(node.nodeId()).stream()).toList();
      builder.addMethodBody(key, syntax.root(), actual.methodBodyOrigin(key).orElseThrow(), uses, calls, accesses, concatenations);
    }
    return builder.seal();
  }

  private static SyntaxAnalysisInput suppliedInput(SyntaxAnalysisInput original, JavaAstAggregate changed) {
    return new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), Optional.of(changed),
        original.diagnostics(), original.languageFeatureCoverageResult());
  }

  private static SyntaxAnalysisInput reconcile(SyntaxAnalysisInput original, JavaAstAggregate changed) {
    return JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(changed),
        JavaCoverageTestSupport.diagnostics(original, "OwnershipContrast"));
  }

  private static FeatureContextOutcome enclosedOutcome(SyntaxAnalysisInput input) {
    return input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals(JavaDeclarationPropertyCoverage.TYPE_ELEMENT_ENCLOSED_ELEMENTS)
            && outcome.address().ownerPath().equals(SECOND_TYPE)).findFirst().orElseThrow();
  }

  private static UnmatchedSourceFact constructorExtra(SyntaxAnalysisInput input) {
    return input.languageFeatureCoverageResult().validationEvidence().unmatchedFacts().stream()
        .filter(fact -> fact.fact().identity().equals("type:Second#<init>()")).findFirst().orElseThrow();
  }

  private static SourceContext capturedType(SyntaxAnalysisInput input, String kind, SourceOwnerPath path) {
    return input.languageFeatureCoverageResult().validationEvidence().writtenInventory().contexts().stream()
        .filter(context -> context.address().partition() == SourceContext.Partition.TREE
            && context.feature().filter(feature -> feature.identifier().equals(kind)).isPresent()
            && context.address().ownerPath().equals(path)).findFirst().orElseThrow();
  }

  private static void assertFrozenExpectations(SyntaxAnalysisInput original, SyntaxAnalysisInput changed) {
    var frozen = original.languageFeatureCoverageResult().validationEvidence();
    var actual = changed.languageFeatureCoverageResult().validationEvidence();
    assertEquals(frozen.writtenInventory(), actual.writtenInventory());
    assertEquals(frozen.attributedExpectations(), actual.attributedExpectations());
  }

  private static LanguageFeatureCoverageResult claimedOwner(SyntaxAnalysisInput partial, UnmatchedSourceFact selected, SourceContext owner) {
    var evidence = partial.languageFeatureCoverageResult().validationEvidence();
    var unmatched = evidence.unmatchedFacts().stream().map(fact -> fact.equals(selected)
        ? new UnmatchedSourceFact(owner.address(), fact.fact(), fact.ordinal(), fact.reason(), fact.emission()) : fact).toList();
    var events = evidence.diagnosticEmissions().stream().map(event -> event.address().equals(selected.emission())
        ? new DiagnosticEmission(event.address(), new AnalysisDiagnostic(event.diagnostic().diagnosticKey(), event.diagnostic().code(),
            event.diagnostic().stage(), event.diagnostic().severity(), event.diagnostic().message(), owner.origins(), event.diagnostic().outcomeKind()),
            event.cause(), List.of(owner.address()), event.slot()) : event).toList();
    return LanguageFeatureCoverageResult.fromEvidence(partial.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        evidence.outcomes(), unmatched, events);
  }
}
