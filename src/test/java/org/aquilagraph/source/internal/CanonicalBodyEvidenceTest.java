package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.retention.AstCodec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoverageSourceFact;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.UnmatchedSourceFact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanonicalBodyEvidenceTest {
  @TempDir Path directory;

  @Test
  void givenPermutedCrossRoleAttachments_whenReadingAndQualifyingBodyFacts_thenCanonicalOrderAndIdentityAgree() throws IOException {
    // Arrange
    var input = fixture();
    var method = input.program().methods().stream().filter(value -> "run".equals(value.name())).findFirst().orElseThrow();
    var body = input.methodSyntax(method.methodKey()).orElseThrow();
    var binary = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINARY).findFirst()
        .orElseThrow();
    var expectedInvocations = List.of(binary.requiredChild(SyntaxRole.LEFT_OPERAND).nodeId(),
        binary.requiredChild(SyntaxRole.RIGHT_OPERAND).nodeId());
    var permutedInput = permute(input, binary.nodeId());

    // Act
    var original = AstResultAdmission.admit(input, AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1");
    var permuted = AstResultAdmission.admit(permutedInput, AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1");
    var originalInvocations = original.body(method.methodKey()).orElseThrow().evidence().invocations().stream()
        .map(value -> value.consumerNodeId()).toList();
    var permutedInvocations = permuted.body(method.methodKey()).orElseThrow().evidence().invocations().stream()
        .map(value -> value.consumerNodeId()).toList();
    var decoded = AstCodec.decode(AstCodec.encode(permuted), permuted.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.COMPLETE, permuted.completion());
    assertEquals(expectedInvocations, originalInvocations);
    assertAll(() -> assertEquals(expectedInvocations, permutedInvocations),
        () -> assertEquals(original.identity(), permuted.identity()));
    assertNotEquals(List.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND),
        permuted.aggregate().node(binary.nodeId()).orElseThrow().children().stream().map(SyntaxChild::role).toList());
    assertEquals(List.of(SyntaxRole.RIGHT_OPERAND, SyntaxRole.LEFT_OPERAND),
        decoded.aggregate().node(binary.nodeId()).orElseThrow().children().stream().map(SyntaxChild::role).toList());
    assertEquals(permuted.coverage(), decoded.coverage());
    assertEquals(permuted.identity(), decoded.identity());
  }

  @Test
  void givenChangedRolePositionOrChildIdentity_whenAdmittingCoverageSupport_thenContradictorySourceFactsAreRejected() throws IOException {
    // Arrange
    var input = fixture();
    var binary = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINARY).findFirst()
        .orElseThrow();
    var reference = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .flatMap(outcome -> outcome.support().facts().stream()).filter(fact -> fact.identity().equals(binary.nodeId())).findFirst()
        .orElseThrow();
    var fact = ((CoverageFactValue.Source) reference.value()).value();
    var left = fact.children().getFirst();
    var right = fact.children().getLast();
    var changes = List.of(new CoverageSourceFact.Child(right.role(), left.ordinal(), left.nodeKey()),
        new CoverageSourceFact.Child(left.role(), left.ordinal() + 1, left.nodeKey()),
        new CoverageSourceFact.Child(left.role(), left.ordinal(), right.nodeKey()));

    // Act
    var failures = changes.stream().map(child -> {
      var changed = new CoverageSourceFact(fact.structuralPath(), fact.sourceKind(), fact.syntaxKind(), fact.type(), fact.value(),
          List.of(child, right), fact.semanticReference(), fact.sourceAssociation());
      var changedReference = new CoverageFactReference(reference.kind(), reference.identity(), reference.sourceFileKey(),
          new CoverageFactValue.Source(changed), reference.origin());
      return assertThrows(IllegalArgumentException.class,
          () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(),
              input.diagnostics(), coverageFor(input, value -> value.identity().equals(binary.nodeId()) ? changedReference : value)));
    }).toList();

    // Assert
    assertEquals(3, failures.size());
    assertTrue(failures.stream().allMatch(failure -> failure.getMessage().contains("exact actual owned fact value")));
  }

  private SyntaxAnalysisInput fixture() throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Calls.java"), """
        class Calls {
          static int left() { return 1; }
          static int right() { return 2; }
          int run() { return left() + right(); }
        }
        """)));
    return SourceValueCapture.capture(new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow());
  }

  @Test
  void givenPermutedOperandsAndUnavailableCallClassifications_whenDeriving_thenOrderedBlockersAndDiagnosticsAgree() throws IOException {
    verifyRefusalOrder(false);
  }

  @Test
  void givenPermutedOperandsAndUnavailableUseClassifications_whenDeriving_thenOrderedBlockersAndDiagnosticsAgree() throws IOException {
    verifyRefusalOrder(true);
  }

  private void verifyRefusalOrder(boolean useClassifications) throws IOException {
    var captured = fixture();
    var methodKey = captured.program().methods().stream().filter(value -> "run".equals(value.name())).findFirst().orElseThrow().methodKey();
    var binary = captured.methodSyntax(methodKey).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.BINARY).findFirst().orElseThrow();
    for (var state : List.of(EvidenceState.MISSING, EvidenceState.ERRONEOUS)) {
      var input = withUnavailableClassification(captured, binary, state, useClassifications);
      var original = AstResultAdmission.admit(input, AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1");
      var permuted = AstResultAdmission.admit(permute(input, binary.nodeId()), original.profile(),
          original.identity().semanticProductionRevision());
      var decoded = AstCodec.decode(AstCodec.encode(permuted), original.identity());
      assertEquals(AstCompletion.COMPLETE, original.completion());
      assertEquals(original.identity(), permuted.identity());
      assertEquals(original.completion(), decoded.completion());
      var request = new CfgRequest(original.identity(), List.of(methodKey), CfgProfile.JAVA_BOUNDED);
      var expected = CfgDerivers.standard().derive(original, request);
      var expectedMethod = expected.methods().getFirst();
      assertEquals(state == EvidenceState.MISSING ? MethodCfgState.INSUFFICIENT_EVIDENCE : MethodCfgState.ERRONEOUS_EVIDENCE,
          expectedMethod.state());
      assertEquals(useClassifications ? List.of("child-use.conversion/left/0", "child-use.conversion/right/0")
          : List.of("invocation-applicability", "invocation-applicability"), expectedMethod.blockers().stream().map(value -> value.slot()).toList());
      assertEquals(useClassifications ? List.of(binary.nodeId(), binary.nodeId())
          : List.of(binary.requiredChild(SyntaxRole.LEFT_OPERAND).nodeId(), binary.requiredChild(SyntaxRole.RIGHT_OPERAND).nodeId()),
          expectedMethod.blockers().stream().map(value -> value.occurrence().orElseThrow().nodeKey()).toList());
      var runner = new SourceAnalysisRunner(ignored -> {
        throw new AssertionError("Existing-source refusal must not acquire a producer");
      }, CfgDerivers.standard());
      var expectedRun = runner.deriveExisting(AstOutcome.available(original), request);
      for (var retained : List.of(permuted, decoded)) {
        var actual = CfgDerivers.standard().derive(retained, request);
        assertAll(() -> assertEquals(expectedMethod.blockers(), actual.methods().getFirst().blockers()),
            () -> assertEquals(expectedMethod.diagnostics(), actual.methods().getFirst().diagnostics()),
            () -> assertEquals(expected.diagnostics(), actual.diagnostics()),
            () -> assertEquals(expectedRun.diagnostics().values(), runner.deriveExisting(AstOutcome.available(retained), request).diagnostics().values()));
      }
    }
  }

  private static SyntaxAnalysisInput withUnavailableClassification(SyntaxAnalysisInput input, SyntaxNode binary, EvidenceState state,
      boolean useClassifications) {
    var copied = SourceValueCapture.capture(input.aggregate().orElseThrow());
    var invocationKeys = List.of(binary.requiredChild(SyntaxRole.LEFT_OPERAND).nodeId(), binary.requiredChild(SyntaxRole.RIGHT_OPERAND).nodeId());
    var bodies = copied.bodies().stream().map(body -> {
      var uses = body.uses().stream().map(value -> useClassifications && value.consumerNodeId().equals(binary.nodeId())
          ? new ChildUseEvidence(value.scopeKey(), value.consumerNodeId(), value.role(), value.position(), value.sourceType(),
              value.selectedTarget(), unavailable(value.conversion(), state), value.referenceCheck())
          : value).toList();
      var invocations = body.invocations().stream().map(value -> !useClassifications && invocationKeys.contains(value.consumerNodeId())
          ? new InvocationEvidence(unavailable(value.applicability(), state)) : value).toList();
      return new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), uses, invocations, body.staticAccesses(),
          body.concatenations(), body.constructors(), body.attributedOccurrences());
    }).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes()));
    return new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), Optional.of(aggregate),
        input.diagnostics(), input.languageFeatureCoverageResult());
  }

  private static <T> EvidenceValue<T> unavailable(EvidenceValue<T> value, EvidenceState state) {
    return state == EvidenceState.ERRONEOUS ? EvidenceValue.erroneous(value.origin(), "Controlled erroneous classification")
        : EvidenceValue.missing(value.origin(), "Controlled missing classification");
  }

  private static SyntaxAnalysisInput permute(SyntaxAnalysisInput input, String binaryKey) {
    var copied = SourceValueCapture.capture(input.aggregate().orElseThrow());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    copied.sources().forEach(source -> copyNode(source.root(), binaryKey, replacements));
    copied.bodies().forEach(body -> {
      if (!replacements.containsKey(body.root())) {
        copyNode(body.root(), binaryKey, replacements);
      }
    });
    var sources = copied.sources().stream()
        .map(source -> new SyntaxSourceContribution(source.sourceFileKey(), replacements.get(source.root()),
            source.nodes().stream().map(replacements::get).toList(), source.structuralEdges(), source.semanticEdges()))
        .toList();
    var bodies = copied.bodies().stream()
        .map(body -> new CopiedAstValues.Body(body.methodKey(), replacements.get(body.root()), body.origin(), body.uses(),
            body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences()))
        .toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), sources, bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes()));
    return new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), Optional.of(aggregate),
        input.diagnostics(), coverageFor(input, reference -> permuteFact(reference, binaryKey)));
  }

  private static LanguageFeatureCoverageResult coverageFor(SyntaxAnalysisInput input, UnaryOperator<CoverageFactReference> transform) {
    var coverage = input.languageFeatureCoverageResult();
    var evidence = coverage.validationEvidence();
    var outcomes = evidence.outcomes().stream().map(outcome -> {
      var support = outcome.support();
      return new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
          new FeatureContextOutcome.Support(support.mode(), support.facts().stream().map(transform).toList(),
              support.policyContext(), support.sourceFile(), support.negativeState()),
          outcome.emissions());
    }).toList();
    var unmatched = evidence.unmatchedFacts().stream()
        .map(fact -> new UnmatchedSourceFact(fact.owner(), transform.apply(fact.fact()), fact.ordinal(), fact.reason(), fact.emission()))
        .toList();
    return CoverageAdmission.fromEvidence(coverage.coverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), outcomes, unmatched, evidence.diagnosticEmissions());
  }

  private static SyntaxNode copyNode(SyntaxNode original, String binaryKey, Map<SyntaxNode, SyntaxNode> replacements) {
    var children = original.children().stream()
        .map(child -> new SyntaxChild(child.role(), child.position(), copyNode(child.node(), binaryKey, replacements))).toList();
    if (original.nodeId().equals(binaryKey)) {
      children = children.reversed();
    }
    var copied = original.origin().isPresent()
        ? SyntaxNode.source(original.sourceFileKey(), original.structuralPath(), original.sourceKind(), original.syntaxKind(),
            original.type(), original.value(), children, original.semanticReference(), original.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(original.sourceFileKey(), original.structuralPath(), original.sourceKind(), original.syntaxKind(),
            original.type(), original.value(), children, original.semanticReference());
    replacements.put(original, copied);
    return copied;
  }

  private static CoverageFactReference permuteFact(CoverageFactReference reference, String binaryKey) {
    if (!reference.identity().equals(binaryKey) || !(reference.value() instanceof CoverageFactValue.Source source)) {
      return reference;
    }
    var fact = source.value();
    var changed = new CoverageSourceFact(fact.structuralPath(), fact.sourceKind(), fact.syntaxKind(), fact.type(), fact.value(),
        fact.children().reversed(), fact.semanticReference(), fact.sourceAssociation());
    return new CoverageFactReference(reference.kind(), reference.identity(), reference.sourceFileKey(), new CoverageFactValue.Source(changed),
        reference.origin());
  }
}
