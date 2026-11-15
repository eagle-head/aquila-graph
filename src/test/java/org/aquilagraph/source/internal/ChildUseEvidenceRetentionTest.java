package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.retention.AstCodec;


import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChildUseEvidenceRetentionTest {
  @TempDir Path directory;

  @Test
  void givenRequiredBoxingTypes_whenConversionClassificationIsMissingOrErroneous_thenSourceRetainsBothTypesAndCfgReportsThatSlot()
      throws IOException {
    // Arrange
    var original = produce("class Stored { Object value(int n) { return n; } }");
    var use = use(original, SyntaxKind.RETURN);
    assertTrue(use.conversion().knownValue().orElseThrow().boxedType().isPresent());
    var states = List.of(EvidenceValue.<ConversionEvidence>missing(use.conversion().origin(), "Classification unavailable"),
        EvidenceValue.<ConversionEvidence>erroneous(use.conversion().origin(), "Classification erroneous"));
    // Act
    var replacements = states.stream().map(state -> new ChildUseEvidence(use.scopeKey(), use.consumerNodeId(), use.role(), use.position(), use.sourceType(),
        use.selectedTarget(), state, use.referenceCheck())).toList();
    var refusals = replacements.stream().map(replacement -> observeRefusal(original, replacement)).toList();

    // Assert
    for (var index = 0; index < replacements.size(); index++) {
      var state = states.get(index);
      verifyRefusal(original, replacements.get(index), state.state(), state.origin().slot(), SyntaxKind.RETURN, refusals.get(index));
    }
  }

  @Test
  void givenKnownConversionAndKnownFalseReferenceCheck_whenOnlyTheCheckBecomesUnavailable_thenConversionRemainsKnown() throws IOException {
    // Arrange
    var original = produce("class Stored { Object value(String n) { return (Object) n; } }");
    var use = use(original, SyntaxKind.TYPE_CAST);
    assertEquals(Optional.of(false), use.referenceCheck().knownValue());
    var states = List.of(EvidenceValue.<Boolean>missing(use.referenceCheck().origin(), "Check unavailable"),
        EvidenceValue.<Boolean>erroneous(use.referenceCheck().origin(), "Check erroneous"));
    // Act
    var replacements = states.stream().map(state -> new ChildUseEvidence(use.scopeKey(), use.consumerNodeId(), use.role(), use.position(), use.sourceType(),
        use.selectedTarget(), use.conversion(), state)).toList();
    var refusals = replacements.stream().map(replacement -> observeRefusal(original, replacement)).toList();

    // Assert
    for (var index = 0; index < replacements.size(); index++) {
      var state = states.get(index);
      verifyRefusal(original, replacements.get(index), state.state(), state.origin().slot(), SyntaxKind.TYPE_CAST, refusals.get(index));
    }
  }

  @Test
  void givenClassificationWithAForeignRange_whenAdmitted_thenExactCreatingOccurrenceIsEnforced() throws IOException {
    // Arrange
    var original = produce("class Stored { Object value(int n) { return n; } }");
    var use = use(original, SyntaxKind.RETURN);
    var origin = use.conversion().origin();
    var foreign = new EvidenceOrigin(origin.scopeKey(), origin.nodeKey(), origin.slot(),
        Optional.of(new SourceSpan(origin.sourceRange().orElseThrow().sourceFileKey(), 0, 1)));
    var replacement = new ChildUseEvidence(use.scopeKey(), use.consumerNodeId(), use.role(), use.position(), use.sourceType(),
        use.selectedTarget(), EvidenceValue.missing(foreign, "Classification unavailable"), use.referenceCheck());
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> replace(original, replacement));

    // Assert
    assertTrue(failure.getMessage().contains("source range"));
  }

  @Test
  void givenReferenceCast_whenReferenceCheckIsMarkedNotApplicable_thenSourceAdmissionRejectsTheContradiction() throws IOException {
    // Arrange
    var original = produce("class Stored { String value(Object n) { return (String) n; } }");
    var use = use(original, SyntaxKind.TYPE_CAST);
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(Optional.of(true), use.referenceCheck().knownValue());
    var replacement = new ChildUseEvidence(use.scopeKey(), use.consumerNodeId(), use.role(), use.position(), use.sourceType(),
        use.selectedTarget(), use.conversion(), EvidenceValue.notApplicable(use.referenceCheck().origin(), "No classification"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> replace(original, replacement));

    // Assert
    assertTrue(failure.getMessage().contains("reference-cast"));
  }

  @Test
  void givenSelectedInvocationArgument_whenItsTargetFactIsOmitted_thenSourceIsIncompleteAndTheKnownCallIsRetained() throws IOException {
    var cases = List.of(
        new ArgumentTargetCase("class Stored { int value(String n) { return Integer.parseInt(n); } }", SyntaxKind.INVOCATION),
        new ArgumentTargetCase("class Stored { String value(String n) { return new String(n); } }", SyntaxKind.OBJECT_CREATION));
    for (var example : cases) {
      // Arrange
      var original = produce(example.content());
      var use = use(original, example.kind());
      var call = original.body(use.scopeKey()).orElseThrow().invocationEvidence(use.consumerNodeId());
      assertEquals(AstCompletion.COMPLETE, original.completion());
      assertTrue(call.isPresent());

      // Act
      var retained = withUses(original, uses -> uses.stream().filter(value -> !value.equals(use)).toList());
      var decoded = AstCodec.decode(AstCodec.encode(retained), retained.identity());

      // Assert
      assertEquals(AstCompletion.INCOMPLETE, retained.completion());
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertNotEquals(original.identity(), decoded.identity());
      var body = decoded.body(use.scopeKey()).orElseThrow();
      assertTrue(body.useEvidence(use.consumerNodeId(), use.role(), use.position()).isEmpty());
      assertEquals(call, body.invocationEvidence(use.consumerNodeId()));
      assertEquals(original.aggregate().node(use.consumerNodeId()), decoded.aggregate().node(use.consumerNodeId()));
    }
  }

  private record ArgumentTargetCase(String content, SyntaxKind kind) {
  }

  @Test
  void givenOwnedSelectedParameters_whenAnArgumentTargetContradictsItsDeclaration_thenSourceAdmissionRejectsIt() {
    var cases = List.of(
        new SelectedParameterCase("class Stored { static void takes(Integer n) {} void value(int n) { takes(n); } }",
            SyntaxKind.INVOCATION, "java.lang.Integer"),
        new SelectedParameterCase("class Stored { Stored(Integer n) {} Object value(int n) { return new Stored(n); } }",
            SyntaxKind.OBJECT_CREATION, "java.lang.Integer"),
        new SelectedParameterCase("class Stored { static void takes(Integer n, String... tail) {} void value(int n) { takes(n, \"tail\"); } }",
            SyntaxKind.INVOCATION, "java.lang.Integer"),
        new SelectedParameterCase("class Stored { static void takes(Integer... n) {} void value(Integer[] n) { takes(n); } }",
            SyntaxKind.INVOCATION, "java.lang.Integer[]"),
        new SelectedParameterCase("record Stored(Integer n) { Stored {} Object value(int n) { return new Stored(n); } }",
            SyntaxKind.OBJECT_CREATION, "java.lang.Integer"));
    assertAll(cases.stream().map(example -> () -> rejectContradictoryParameterTarget(example)));
  }

  private void rejectContradictoryParameterTarget(SelectedParameterCase example) throws IOException {
    // Arrange
    var original = produce(example.content());
    var use = use(original, example.kind());
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(example.expectedErasure(), use.selectedTarget().erasedName());
    var target = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.Object", "java.lang.Object");
    var replacement = withTarget(use, target);

    // Act
    var failure = assertThrows(AstContractException.class, () -> replace(original, replacement));

    // Assert
    assertTrue(failure.getMessage().contains("parameter target"));
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    assertEquals(original.identity(), decoded.identity());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.completion(), decoded.completion());
  }

  private static ChildUseEvidence withTarget(ChildUseEvidence use, JavaTypeIdentity target) {
    var conversion = use.conversion().knownValue().orElseThrow();
    var changed = new ConversionEvidence(conversion.scopeKey(), conversion.consumerNodeId(), conversion.role(), conversion.position(),
        conversion.sourceType(), target, conversion.checkedReferenceTarget(), conversion.unboxedType(), conversion.effectivePrimitiveType(),
        conversion.preBoxPrimitiveType(), conversion.boxedType());
    return new ChildUseEvidence(use.scopeKey(), use.consumerNodeId(), use.role(), use.position(), use.sourceType(), target,
        EvidenceValue.known(changed, use.conversion().origin()), use.referenceCheck());
  }

  @Test
  void givenClosedParameterType_whenAnArgumentTargetChangesOnlyItsTypeArguments_thenSourceAdmissionRejectsIt() throws IOException {
    // Arrange
    var original = produce("class Stored { static void takes(java.util.List<String> n) {} void value(java.util.List<String> n) { takes(n); } }");
    var use = use(original, SyntaxKind.INVOCATION);
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals("java.util.List<java.lang.String>", use.selectedTarget().displayName());
    var replacement = withTarget(use,
        new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.util.List<java.lang.Integer>", "java.util.List"));

    // Act
    var failure = assertThrows(AstContractException.class, () -> replace(original, replacement));

    // Assert
    assertTrue(failure.getMessage().contains("parameter target"));
  }

  @Test
  void givenGenericAndVariableParameters_whenCompilerSelectedTargetsAreRetained_thenSourceRemainsComplete() throws IOException {
    var cases = List.of(
        "class Stored { <T> T takes(T n) { return n; } Object value(int n) { return takes(n); } }",
        "class Stored { <T> void takes(T[] n) {} void value(String[] n) { takes(n); } }",
        "class Box<T> { void takes(java.util.List<String> n) {} } class Stored { void value(Box raw, java.util.List<String> n) { raw.takes(n); } }",
        "class Outer<T> { class Inner { void takes(java.util.List<String> n) {} } } class Stored { void value(Outer.Inner raw, java.util.List<String> n) { raw.takes(n); } }",
        "class Stored { static void takes(java.util.List<?> n) {} void value(java.util.List<String> n) { takes(n); } }",
        "class Stored { static void takes(String[]... n) {} void value(String[] n) { takes(n, n); } }");
    for (var content : cases) {
      // Arrange
      var original = produce(content);

      // Act
      var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion());
      assertEquals(original.identity(), decoded.identity());
      assertEquals(original.aggregate(), decoded.aggregate());
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
    }
  }

  private record SelectedParameterCase(String content, SyntaxKind kind, String expectedErasure) {
  }

  @Test
  void givenTrailingVariableArguments_whenOptionalArityIsUnavailable_thenContradictoryTargetsStillRejectAdmission() throws IOException {
    // Arrange
    var original = produce("class Stored { static void takes(Integer... n) {} void value(int n) { takes(n, n); } }");
    var first = use(original, SyntaxKind.INVOCATION);
    var use = original.body(first.scopeKey()).orElseThrow().evidence().uses().stream()
        .filter(value -> value.consumerNodeId().equals(first.consumerNodeId()) && value.position() == 1).findFirst().orElseThrow();
    var invocation = original.body(use.scopeKey()).orElseThrow().invocationEvidence(use.consumerNodeId()).orElseThrow();
    var states = List.of(EvidenceValue.missing(invocation.applicability().origin(), "Classification unavailable"),
        EvidenceValue.erroneous(invocation.applicability().origin(), "Classification erroneous"));
    var replacement = withTarget(use, new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.Object", "java.lang.Object"));

    assertAll(states.stream().map(state -> () -> {
      UnaryOperator<List<InvocationEvidence>> unavailable = values -> values.stream()
          .map(value -> value.consumerNodeId().equals(use.consumerNodeId()) ? new InvocationEvidence(
              new EvidenceValue<>(state.state(), Optional.empty(), state.origin(), state.explanation())) : value).toList();

      // Act
      var retained = withEvidence(original, UnaryOperator.identity(), unavailable);
      var decoded = AstCodec.decode(AstCodec.encode(retained), retained.identity());
      var failure = assertThrows(AstContractException.class, () -> withEvidence(original,
          values -> values.stream().map(value -> value.equals(use) ? replacement : value).toList(), unavailable));

      // Assert
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
      assertEquals(retained.aggregate(), decoded.aggregate());
      assertEquals(state.state(), decoded.body(use.scopeKey()).orElseThrow().invocationEvidence(use.consumerNodeId()).orElseThrow()
          .applicability().state());
      assertTrue(failure.getMessage().contains("parameter target"));
    }));
  }

  private static Refusal observeRefusal(AstResult original, ChildUseEvidence replacement) {
    var retained = replace(original, replacement);
    var decoded = AstCodec.decode(AstCodec.encode(retained), retained.identity());
    var outcome = CfgDerivers.standard().derive(decoded,
        new CfgRequest(decoded.identity(), List.of(replacement.scopeKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();
    return new Refusal(decoded, outcome);
  }

  private static void verifyRefusal(AstResult original, ChildUseEvidence replacement, EvidenceState state, String slot, SyntaxKind kind,
      Refusal refusal) {
    var decoded = refusal.decoded();
    var outcome = refusal.outcome();
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertNotEquals(original.identity(), decoded.identity());
    assertEquals(replacement, use(decoded, kind));
    assertEquals(original.aggregate().node(replacement.consumerNodeId()), decoded.aggregate().node(replacement.consumerNodeId()));
    var expected = state == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
    assertEquals(expected, outcome.state());
    assertEquals(List.of(expected), outcome.blockers().stream().map(value -> value.kind()).toList());
    assertEquals(List.of(slot), outcome.blockers().stream().map(value -> value.slot()).toList());
    assertTrue(outcome.graph().isEmpty());
  }

  private record Refusal(AstResult decoded, MethodCfgOutcome outcome) {
  }

  private AstResult produce(String content) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")), content)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
  }

  private static ChildUseEvidence use(AstResult ast, SyntaxKind kind) {
    var method = ast.program().types().stream().flatMap(type -> type.methods().stream()).filter(value -> value.name().equals("value"))
        .findFirst().orElseThrow();
    var syntax = ast.body(method.methodKey()).orElseThrow();
    return syntax.evidence().uses().stream().filter(value -> ast.aggregate().node(value.consumerNodeId()).orElseThrow().syntaxKind() == kind)
        .findFirst().orElseThrow();
  }

  private static AstResult replace(AstResult original, ChildUseEvidence replacement) {
    return withUses(original,
        uses -> uses.stream().map(value -> value.scopeKey().equals(replacement.scopeKey())
            && value.consumerNodeId().equals(replacement.consumerNodeId()) && value.role() == replacement.role()
            && value.position() == replacement.position() ? replacement : value).toList());
  }

  private static AstResult withUses(AstResult original, UnaryOperator<List<ChildUseEvidence>> change) {
    return withEvidence(original, change, UnaryOperator.identity());
  }

  private static AstResult withEvidence(AstResult original, UnaryOperator<List<ChildUseEvidence>> change,
      UnaryOperator<List<InvocationEvidence>> invocationChange) {
    var copied = SourceValueCapture.capture(original.aggregate());
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(),
        change.apply(body.uses()), invocationChange.apply(body.invocations()), body.staticAccesses(), body.concatenations(), body.constructors(),
        body.attributedOccurrences())).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes(), copied.scopes()));
    return AstResultAdmission.admit(new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
        Optional.of(aggregate), original.diagnostics(), original.coverage()), original.profile(), original.identity().semanticProductionRevision());
  }
}
