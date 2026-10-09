package org.aquilagraph.cfg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgAdmissionResult;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CfgAdmissionOutcomeTest {
  @TempDir Path directory;

  @Test
  void givenUnavailableSourceAndOtherRefusalKinds_whenDeriving_thenSourceUnavailabilityWinsWithoutDroppingAnyCause() throws IOException {
    verifyMixedRefusals(true, true, true, MethodCfgState.SOURCE_UNAVAILABLE);
  }

  @Test
  void givenRepresentedUnsupportedConstructAndEvidenceGaps_whenDeriving_thenUnsupportedWinsWithoutDroppingEvidenceCauses() throws IOException {
    verifyMixedRefusals(false, true, true, MethodCfgState.UNSUPPORTED);
  }

  @Test
  void givenSuppliedErroneousAndMissingEvidence_whenDeriving_thenErroneousWinsRegardlessOfSourceOrder() throws IOException {
    verifyMixedRefusals(false, false, true, MethodCfgState.ERRONEOUS_EVIDENCE);
  }

  @Test
  void givenOnlySuppliedMissingEvidence_whenDeriving_thenInsufficientEvidenceRetainsEverySlot() throws IOException {
    verifyMixedRefusals(false, false, false, MethodCfgState.INSUFFICIENT_EVIDENCE);
  }

  private void verifyMixedRefusals(boolean unavailableSource, boolean unsupportedConstruct, boolean erroneousEvidence,
      MethodCfgState expectedSummary) throws IOException {
    var source = """
        class Stored {
          static int target(int value) { return value; }
          int run(boolean flag) {
            target(1);
            target(2);
        """ + (unsupportedConstruct ? "int result = switch (1) { case 1 -> 2; default -> 3; };\n" : "")
        + (unavailableSource ? "class Local {}\n" : "") + "return 0; } }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), source)));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var method = original.program().methods().stream().filter(value -> value.name().equals("run")).findFirst().orElseThrow();
    var invocations = original.body(method.methodKey()).orElseThrow().evidence().invocations();
    assertEquals(2, invocations.size());
    for (var reverse : List.of(false, true)) {
      // Arrange: only the optional call classifications are supplied variants.
      var copied = CopiedAstValues.capture(original.aggregate());
      var erroneousKey = invocations.get(reverse ? 0 : 1).consumerNodeId();
      var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
          body.invocations().stream().map(value -> body.methodKey().equals(method.methodKey())
              ? new InvocationEvidence(erroneousEvidence && value.consumerNodeId().equals(erroneousKey)
                  ? EvidenceValue.erroneous(value.applicability().origin(), "Supplied erroneous call classification")
                  : EvidenceValue.missing(value.applicability().origin(), "Supplied missing call classification"))
              : value).toList(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
      var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
          copied.associations(), copied.declarationTypes()));
      var ast = AstResultAdmission.admit(new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
          Optional.of(aggregate), original.diagnostics(), original.coverage()), original.profile(),
          original.identity().semanticProductionRevision());
      var profile = unsupportedConstruct ? CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS : CfgProfile.JAVA_BOUNDED;
      var request = new CfgRequest(ast.identity(), List.of(method.methodKey()), profile);
      var retainedBytes = AstCodec.encode(ast);

      // Act
      var admission = CfgAdmission.assess(ast, method.methodKey(), profile);
      var outcome = CfgDerivers.standard().derive(ast, request);
      var refused = outcome.methods().getFirst();
      var run = new SourceAnalysisRunner(ignored -> {
        throw new AssertionError("Existing derivation must not produce source");
      }, CfgDerivers.standard()).deriveExisting(AstOutcome.available(ast), request);

      // Assert: summary expectations do not depend on enum ordinals or source order.
      var expectedKinds = new ArrayList<MethodCfgState>();
      expectedKinds.add(erroneousEvidence && reverse ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE);
      expectedKinds.add(erroneousEvidence && !reverse ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE);
      if (unsupportedConstruct) {
        expectedKinds.add(MethodCfgState.UNSUPPORTED);
      }
      if (unavailableSource) {
        expectedKinds.add(MethodCfgState.SOURCE_UNAVAILABLE);
      }
      assertEquals(expectedSummary, admission.state());
      assertEquals(expectedSummary, refused.state());
      assertEquals(expectedKinds, refused.blockers().stream().map(blocker -> blocker.kind()).toList());
      assertEquals(invocations.stream().map(value -> value.consumerNodeId()).toList(),
          refused.blockers().subList(0, 2).stream().map(blocker -> blocker.occurrence().orElseThrow().nodeKey()).toList());
      assertEquals(List.of(InvocationEvidence.SLOT, InvocationEvidence.SLOT),
          refused.blockers().subList(0, 2).stream().map(blocker -> blocker.slot()).toList());
      assertEquals(admission.blockers(), refused.blockers());
      for (var wrongSummary : List.of(MethodCfgState.SOURCE_UNAVAILABLE, MethodCfgState.UNSUPPORTED,
          MethodCfgState.ERRONEOUS_EVIDENCE, MethodCfgState.INSUFFICIENT_EVIDENCE)) {
        if (wrongSummary != expectedSummary) {
          assertThrows(IllegalArgumentException.class,
              () -> new CfgAdmissionResult(wrongSummary, admission.body(), Optional.empty(), admission.blockers()));
          assertThrows(IllegalArgumentException.class, () -> new MethodCfgOutcome(refused.astIdentity(), refused.methodKey(), wrongSummary,
              refused.body(), refused.graph(), refused.blockers(), refused.failure(), refused.diagnostics()));
        }
      }
      assertEquals(refused.blockers().stream().map(blocker -> blocker.diagnostic()).toList(), outcome.diagnostics());
      assertTrue(refused.blockers().stream().allMatch(blocker -> blocker.astIdentity().equals(ast.identity())
          && blocker.methodKey().equals(method.methodKey()) && ast.node(blocker.occurrence().orElseThrow()).isPresent()));
      assertTrue(refused.blockers().subList(0, 2).stream().allMatch(blocker -> blocker.evidenceDiagnostic().isEmpty()));
      assertEquals(ast.bodyRef(method.methodKey()), refused.body());
      assertTrue(refused.graph().isEmpty());
      assertEquals(CfgState.PARTIAL, outcome.state());
      assertSame(request, outcome.request());
      assertEquals(AnalysisCompletion.PARTIAL, run.completion());
      assertSame(ast, run.representation().ast().result().orElseThrow());
      assertEquals(unavailableSource ? AstCompletion.INCOMPLETE : AstCompletion.COMPLETE, ast.completion());
      assertEquals(original.program(), ast.program());
      assertArrayEquals(retainedBytes, AstCodec.encode(ast));
    }
  }
}
