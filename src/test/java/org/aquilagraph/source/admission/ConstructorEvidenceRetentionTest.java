package org.aquilagraph.source.admission;

import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ConstructorContext;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.CompilerInsertedInvocation;
import org.aquilagraph.source.api.InvocationApplicability;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConstructorEvidenceRetentionTest {
  @TempDir Path directory;

  @Test
  void givenSelectedEnumConstructor_whenInsertionCorroborationIsMissingOrErroneous_thenSourceSelectionRemainsAndCfgReportsThatSlot()
      throws IOException {
    // Arrange
    var original = produce();
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    var context = context(original);
    assertTrue(context.corroboration().knownValue().orElseThrow().insertedInvocation().isPresent());
    var classifications = List.of(EvidenceValue.<ConstructorContext>missing(context.corroboration().origin(), "Corroboration unavailable"),
        EvidenceValue.<ConstructorContext>erroneous(context.corroboration().origin(), "Corroboration erroneous"));
    for (var classification : classifications) {
      var replacement = new ConstructorEvidence(context.scopeKey(), context.invocationNodeId(), context.ownerTypeKey(),
          context.selectedConstructorKey(), context.form(), classification);
      // Act
      var retained = replace(original, replacement);
      var decoded = AstCodec.decode(AstCodec.encode(retained), retained.identity());
      var outcome = CfgDerivers.standard().derive(decoded,
          new CfgRequest(decoded.identity(), List.of(context.scopeKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

      // Assert
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
      assertNotEquals(original.identity(), decoded.identity());
      assertEquals(replacement, context(decoded));
      assertEquals(original.body(context.scopeKey()).orElseThrow().root(), decoded.body(context.scopeKey()).orElseThrow().root());
      var expected = classification.state() == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
      assertEquals(expected, outcome.state());
      assertEquals(List.of(expected), outcome.blockers().stream().map(value -> value.kind()).toList());
      assertEquals(List.of(ConstructorEvidence.SLOT), outcome.blockers().stream().map(value -> value.slot()).toList());
      assertTrue(outcome.graph().isEmpty());
    }
  }

  private AstResult produce() throws IOException {
    return produce("enum Stored { ; Stored() {} }");
  }

  @Test
  void givenInsertedOrdinarySuperclassSelection_whenApplicabilityIsUnavailable_thenSourceRemainsCompleteAndCfgRefusesThatExactSlot()
      throws IOException {
    // Arrange
    var original = produce("class Stored { Stored() {} }");
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    var evidence = context(original);
    var corroboration = evidence.corroboration().knownValue().orElseThrow();
    var insertion = corroboration.insertedInvocation().orElseThrow();
    var classifications = List.of(EvidenceValue.<InvocationApplicability>missing(insertion.applicability().applicability().origin(), "Applicability unavailable"),
        EvidenceValue.<InvocationApplicability>erroneous(insertion.applicability().applicability().origin(), "Applicability erroneous"));
    for (var classification : classifications) {
      var changed = new CompilerInsertedInvocation(insertion.sourceFileKey(), insertion.compilerPath(), insertion.reference(), insertion.semantics(),
          new InvocationEvidence(classification));
      var replacement = new ConstructorEvidence(evidence.scopeKey(), evidence.invocationNodeId(), evidence.ownerTypeKey(), evidence.selectedConstructorKey(),
          evidence.form(), EvidenceValue.known(new ConstructorContext(corroboration.superclassParameterTypes(), Optional.of(changed)), evidence.corroboration().origin()));
      // Act
      var retained = replace(original, replacement);
      var decoded = AstCodec.decode(AstCodec.encode(retained), retained.identity());
      var outcome = CfgDerivers.standard().derive(decoded,
          new CfgRequest(decoded.identity(), List.of(evidence.scopeKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

      // Assert
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
      assertEquals(replacement, context(decoded));
      var expected = classification.state() == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
      assertEquals(expected, outcome.state());
      assertEquals(List.of(expected), outcome.blockers().stream().map(value -> value.kind()).toList());
      assertEquals(List.of(InvocationEvidence.SLOT), outcome.blockers().stream().map(value -> value.slot()).toList());
      assertTrue(outcome.graph().isEmpty());
    }
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Stored.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
  }

  private static ConstructorEvidence context(AstResult ast) {
    var method = ast.program().types().stream().flatMap(type -> type.methods().stream()).filter(value -> value.constructor())
        .findFirst().orElseThrow();
    return ast.body(method.methodKey()).orElseThrow().evidence().constructors().getFirst();
  }

  private static AstResult replace(AstResult original, ConstructorEvidence replacement) {
    var copied = CopiedAstValues.capture(original.aggregate());
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
        body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors().stream()
            .map(value -> value.invocationNodeId().equals(replacement.invocationNodeId()) ? replacement : value).toList(),
        body.attributedOccurrences())).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes()));
    return AstResultAdmission.admit(new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
        Optional.of(aggregate), original.diagnostics(), original.coverage()), original.profile(), original.identity().semanticProductionRevision());
  }
}
