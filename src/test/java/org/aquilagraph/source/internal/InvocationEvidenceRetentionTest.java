package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.retention.AstCodec;


import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvocationEvidenceRetentionTest {
  @TempDir Path directory;

  @Test
  void givenSuppliedErroneousApplicabilityWithAnActualSourceDiagnostic_whenRetainingAndDeriving_thenExactCauseAndExplanationSurvive()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")),
        "class Stored { int call() { return missing(); } }")));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var fact = invocation(original);
    var node = original.aggregate().node(fact.consumerNodeId()).orElseThrow();
    var error = original.diagnostics().stream().filter(value -> value.code().equals("frontend.error-type")
        && value.origins().equals(node.origin().stream().toList())).findFirst().orElseThrow();
    var replacement = new InvocationEvidence(EvidenceValue.erroneous(fact.applicability().origin(),
        "Supplied erroneous applicability associated with the emitted source error", error));
    var source = replace(original, replacement);
    var changed = new AnalysisDiagnostic(error.diagnosticKey(), error.code(), error.stage(), error.severity(),
        error.message() + " changed", error.origins(), error.outcomeKind());
    var unowned = new InvocationEvidence(EvidenceValue.erroneous(fact.applicability().origin(), "Unowned diagnostic", changed));
    var warning = new AnalysisDiagnostic("supplied-warning", "supplied-warning", error.stage(), DiagnosticSeverity.WARNING,
        "Supplied warning", error.origins(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);

    assertThrows(IllegalArgumentException.class,
        () -> new EvidenceValue<>(EvidenceState.KNOWN, Optional.of(true), fact.applicability().origin(), Optional.empty(), Optional.of(error)));
    assertThrows(IllegalArgumentException.class, () -> EvidenceValue.erroneous(fact.applicability().origin(), "Invalid warning", warning));
    var failure = assertThrows(IllegalArgumentException.class, () -> replace(original, unowned));
    assertTrue(failure.getMessage().contains("exact emitted source diagnostic"));

    // Act
    var bytes = AstCodec.encode(source);
    var decoded = AstCodec.decode(bytes, source.identity());
    var retained = invocation(decoded).applicability();
    var outcome = CfgDerivers.standard().derive(decoded,
        new CfgRequest(decoded.identity(), List.of(fact.scopeKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();
    var blocker = outcome.blockers().stream().filter(value -> value.slot().equals(InvocationEvidence.SLOT)).findFirst().orElseThrow();

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(replacement.applicability(), retained);
    assertEquals(Optional.of(error), retained.diagnostic());
    assertEquals(Optional.of(error), blocker.evidenceDiagnostic());
    assertSame(retained.diagnostic().orElseThrow(), blocker.evidenceDiagnostic().orElseThrow());
    assertEquals("Supplied erroneous applicability associated with the emitted source error", blocker.diagnostic().message());
    assertEquals(MethodCfgState.ERRONEOUS_EVIDENCE, outcome.state());
    assertTrue(outcome.graph().isEmpty());
    assertEquals(source.identity(), decoded.identity());
    assertEquals(original.diagnostics(), decoded.diagnostics());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
  }

  @Test
  void givenKnownTargetAndMissingApplicability_whenRetainingAndDeriving_thenSourceRemainsCompleteAndCfgRefusesOnlyThatSlot()
      throws IOException {
    // Arrange
    var original = produce();
    var invocation = invocation(original);
    var replacement = new InvocationEvidence(EvidenceValue.missing(invocation.applicability().origin(), "Classification unavailable"));

    // Act
    var retained = replace(original, replacement);
    var bytes = AstCodec.encode(retained);
    var decoded = AstCodec.decode(bytes, retained.identity());
    var outcome = CfgDerivers.standard().derive(decoded,
        new CfgRequest(decoded.identity(), List.of(invocation.scopeKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertNotEquals(original.identity(), retained.identity());
    assertNotEquals(original.aggregate(), decoded.aggregate());
    assertNotEquals(original.body(invocation.scopeKey()), decoded.body(invocation.scopeKey()));
    assertEquals(original.program(), decoded.program());
    assertEquals(original.aggregate().node(invocation.consumerNodeId()), decoded.aggregate().node(invocation.consumerNodeId()));
    assertEquals(Optional.of(replacement), decoded.body(invocation.scopeKey()).orElseThrow().invocationEvidence(invocation.consumerNodeId()));
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    assertEquals(MethodCfgState.INSUFFICIENT_EVIDENCE, outcome.state());
    assertEquals(List.of(InvocationEvidence.SLOT), outcome.blockers().stream().map(value -> value.slot()).toList());
    assertTrue(outcome.graph().isEmpty());
  }

  @Test
  void givenErroneousOptionalApplicability_whenRetainingAndDeriving_thenErrorStateAndExactOriginSurviveWithoutAMissingDuplicate()
      throws IOException {
    // Arrange
    var original = produce();
    var invocation = invocation(original);
    var replacement = new InvocationEvidence(EvidenceValue.erroneous(invocation.applicability().origin(), "Classification is erroneous"));

    // Act
    var retained = replace(original, replacement);
    var decoded = AstCodec.decode(AstCodec.encode(retained), retained.identity());
    var outcome = CfgDerivers.standard().derive(decoded,
        new CfgRequest(decoded.identity(), List.of(invocation.scopeKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

    // Assert
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(EvidenceState.ERRONEOUS, invocation(decoded).applicability().state());
    assertEquals(replacement.applicability().origin(), invocation(decoded).applicability().origin());
    assertEquals(MethodCfgState.ERRONEOUS_EVIDENCE, outcome.state());
    assertEquals(List.of(MethodCfgState.ERRONEOUS_EVIDENCE), outcome.blockers().stream().map(value -> value.kind()).toList());
    assertEquals(List.of(InvocationEvidence.SLOT), outcome.blockers().stream().map(value -> value.slot()).toList());
    assertEquals("Classification is erroneous", outcome.blockers().getFirst().diagnostic().message());
    assertTrue(outcome.blockers().getFirst().evidenceDiagnostic().isEmpty());
  }

  @Test
  void givenWrongRangeForAnOwnedInvocation_whenAdmitting_thenIntrinsicSourceAdmissionRejectsTheContradiction() throws IOException {
    // Arrange
    var original = produce();
    var invocation = invocation(original);
    var origin = new EvidenceOrigin(invocation.scopeKey(), invocation.consumerNodeId(), InvocationEvidence.SLOT, Optional.empty());
    var replacement = new InvocationEvidence(EvidenceValue.missing(origin, "Classification unavailable"));

    // Act
    var failure = assertThrows(AstContractException.class, () -> replace(original, replacement));

    // Assert
    assertTrue(failure.getMessage().contains("source range"));
  }

  @Test
  void givenActualInvocation_whenQueryingAndConstructingInapplicableEvidence_thenUnknownKeysAreEmptyAndInapplicableStateRejects()
      throws IOException {
    // Arrange
    var original = produce();
    var invocation = invocation(original);
    var syntax = original.body(invocation.scopeKey()).orElseThrow();

    // Act
    var unknown = syntax.invocationEvidence("unknown");

    // Assert
    assertTrue(unknown.isEmpty());
    assertThrows(IllegalArgumentException.class, () -> new InvocationEvidence(
        EvidenceValue.notApplicable(invocation.applicability().origin(), "Not applicable")));
  }

  private AstResult produce() throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")),
        "class Stored { static int target(int x) { return x; } int call() { return target(7); } }")));
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of());
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, configuration, AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
  }

  private static InvocationEvidence invocation(AstResult ast) {
    var method = ast.program().types().stream().flatMap(type -> type.methods().stream()).filter(value -> value.name().equals("call"))
        .findFirst().orElseThrow();
    return ast.body(method.methodKey()).orElseThrow().evidence().invocations().getFirst();
  }

  private static AstResult replace(AstResult original, InvocationEvidence replacement) {
    var copied = SourceValueCapture.capture(original.aggregate());
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
        body.invocations().stream().map(value -> body.methodKey().equals(replacement.scopeKey())
            && value.consumerNodeId().equals(replacement.consumerNodeId()) ? replacement : value).toList(),
        body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes(), copied.scopes()));
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
        original.diagnostics(), original.coverage());
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
  }
}
