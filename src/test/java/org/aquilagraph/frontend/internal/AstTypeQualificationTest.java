package org.aquilagraph.frontend.internal;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.aquilagraph.source.admission.AstProductionAdmission;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AstTypeQualificationTest {
  @TempDir Path directory;

  @Test
  void givenSuppliedErroneousRequiredType_whenQualifyingAndRetaining_thenSourceRemainsIncomplete() throws IOException {
    verifyIncompleteType(JavaTypeCategory.ERROR, MethodCfgState.ERRONEOUS_EVIDENCE);
  }

  @Test
  void givenSuppliedUnsupportedRequiredType_whenQualifyingAndRetaining_thenSourceRemainsIncomplete() throws IOException {
    verifyIncompleteType(JavaTypeCategory.OTHER, MethodCfgState.INSUFFICIENT_EVIDENCE);
  }

  private void verifyIncompleteType(JavaTypeCategory category, MethodCfgState expectedCfgState) throws IOException {
    // Arrange: the semantic category is an isolated copied-value variant, not a compiler observation.
    var input = copiedInput(category);
    assertFalse(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()), input.diagnostics().toString());
    assertTrue(input.languageFeatureCoverageResult().validationEvidence().unmatchedFacts().isEmpty());
    assertTrue(input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .noneMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));

    // Act
    var outcome = AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1");
    var ast = outcome.result().orElseThrow();
    var bytes = AstCodec.encode(ast);
    var decoded = AstCodec.decode(bytes, ast.identity());
    var method = decoded.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
    var request = new CfgRequest(decoded.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED);
    var admission = CfgAdmission.assess(decoded, method.methodKey(), request.profile());
    var cfg = CfgDerivers.standard().derive(decoded, request);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(input.diagnostics(), decoded.diagnostics());
    assertEquals(category, decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LITERAL)
        .findFirst().orElseThrow().resolvedType().orElseThrow().category());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    assertEquals(expectedCfgState, admission.state());
    assertEquals(CfgState.PARTIAL, cfg.state());
    assertEquals(expectedCfgState, cfg.methods().getFirst().state());
    assertTrue(cfg.methods().getFirst().graph().isEmpty());
    assertEquals(admission.blockers(), cfg.methods().getFirst().blockers());
    var semanticBlocker = admission.blockers().stream().filter(blocker -> blocker.slot().equals("semantics")).findFirst().orElseThrow();
    assertEquals(expectedCfgState, semanticBlocker.kind());
    assertEquals(SyntaxKind.LITERAL, decoded.node(semanticBlocker.occurrence().orElseThrow()).orElseThrow().syntaxKind());
    assertTrue(semanticBlocker.evidenceDiagnostic().isEmpty());
    assertEquals(decoded.bodyRef(method.methodKey()), cfg.methods().getFirst().body());
    assertEquals(cfg.methods().getFirst().blockers().stream().map(blocker -> blocker.diagnostic()).toList(), cfg.diagnostics());
    var publication = AnalysisCapabilities.astRetention(ignored -> {
      throw new AssertionError("Incomplete source must not acquire a repository");
    }).retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
    assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
  }

  @Test
  void givenSuppliedResolvedRequiredTypeWithoutOptionalUseFacts_whenQualifying_thenSourceRemainsComplete() throws IOException {
    // Arrange
    var input = copiedInput(JavaTypeCategory.INT);

    // Act
    var outcome = AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1");
    var ast = outcome.result().orElseThrow();

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(AstCompletion.COMPLETE, AstCodec.decode(AstCodec.encode(ast), ast.identity()).completion());
  }

  private SyntaxAnalysisInput copiedInput(JavaTypeCategory category) throws IOException {
    return copiedInput(new SyntaxType.Resolved(new JavaTypeIdentity(category, "int", "int")));
  }

  @Test
  void givenExpandedProfileWithMissingRequiredType_whenAdmitting_thenIncompleteSourceSurvivesCodec() throws IOException {
    // Arrange: supplied missing type, not a compiler observation.
    var input = copiedInput(SyntaxType.Unresolved.INSTANCE);

    // Act
    var outcome = AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(input.diagnostics(), decoded.diagnostics());
    assertEquals(SyntaxType.Unresolved.INSTANCE, decoded.aggregate().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).findFirst().orElseThrow().type());
  }

  @Test
  void givenExpandedProfileWithoutOptionalCfgEvidence_whenAdmitting_thenSourceIsCompleteAndCfgRefuses() throws IOException {
    // Arrange: remove optional conversion slots from real copied source facts.
    var input = copiedInput(JavaTypeCategory.INT);

    // Act
    var outcome = AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
    var ast = outcome.result().orElseThrow();
    var key = ast.program().methods().stream().filter(method -> method.name().equals("value")).findFirst().orElseThrow().methodKey();
    var cfg = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), List.of(key), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion());
    assertEquals(AstCompletion.COMPLETE, AstCodec.decode(AstCodec.encode(ast), ast.identity()).completion());
    assertEquals(CfgState.PARTIAL, cfg.state());
    assertEquals(MethodCfgState.INSUFFICIENT_EVIDENCE, cfg.methods().getFirst().state());
    assertTrue(cfg.methods().getFirst().graph().isEmpty());
    assertEquals(input.diagnostics(), ast.diagnostics());
  }

  private SyntaxAnalysisInput copiedInput(SyntaxType type) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { int value() { return 1; } }")));
    var original = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var method = original.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
    var aggregate = original.aggregate().orElseThrow();
    var literal = aggregate.methodSyntax(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).findFirst().orElseThrow();
    var copied = SourceValueCapture.capture(aggregate);
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = copied.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
        copyNode(source.root(), literal, type, replacements),
        source.nodes().stream().map(node -> copyNode(node, literal, type, replacements)).toList(), source.structuralEdges(), source.semanticEdges()))
        .toList();
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(),
        copyNode(body.root(), literal, type, replacements), body.origin(), body.methodKey().equals(method.methodKey()) ? List.of() : body.uses(),
        body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var supplied = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), sources, bodies, copied.memberOrders(), copied.associations(),
        copied.declarationTypes()));
    return JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(supplied),
        JavaCoverageTestSupport.emissions(original));
  }

  private static SyntaxNode copyNode(SyntaxNode node, SyntaxNode selected, SyntaxType type, Map<SyntaxNode, SyntaxNode> replacements) {
    var retained = replacements.get(node);
    if (retained != null) {
      return retained;
    }
    var children = node.children().stream()
        .map(child -> new SyntaxChild(child.role(), child.position(), copyNode(child.node(), selected, type, replacements))).toList();
    var copiedType = JavaProjectionIdentity.sameInstance(node, selected) ? type : node.type();
    var replacement = node.origin().map(origin -> SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(),
        node.syntaxKind(), copiedType, node.value(), children, node.semanticReference(), origin))
        .orElseGet(() -> SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(),
            copiedType, node.value(), children, node.semanticReference()));
    replacements.put(node, replacement);
    return replacement;
  }
}
