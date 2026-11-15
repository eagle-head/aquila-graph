package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.internal.SourceValueCapture;
import org.aquilagraph.source.admission.AstProductionAdmission;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaWrittenQualifierTest {
  @TempDir Path directory;

  @Test
  void givenQualifierWithoutItsIndependentSourceAssociation_whenReconcilingAndDecoding_thenContainingCallCannotHideTheGap() throws IOException {
    // Arrange: remove one supplied provenance association, preserving the real captured expectations.
    var input = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example { static int call() { return 1; } int value() { return Example.call(); } }")));
    var original = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(input, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var aggregate = original.aggregate().orElseThrow();
    var qualifier = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION
        && node.value(SyntaxValue.Invocation.class).form() == InvocationForm.STATIC_TYPE).findFirst().orElseThrow()
        .requiredChild(SyntaxRole.QUALIFIER);
    var copied = SourceValueCapture.capture(aggregate);
    var supplied = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), copied.bodies(), copied.memberOrders(),
        copied.associations().stream().filter(value -> !value.nodeKey().equals(qualifier.nodeId())).toList(), copied.declarationTypes(), copied.scopes()));

    // Act
    var reconciled = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(supplied),
        JavaCoverageTestSupport.emissions(original));
    var outcome = AstProductionAdmission.outcome(reconciled, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
    var ast = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(ast), ast.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, AstProductionAdmission.outcome(original, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1").completion());
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
    assertEquals(qualifier, decoded.aggregate().node(qualifier.nodeId()).orElseThrow());
    assertEquals(outcome.diagnostics(), decoded.diagnostics());
  }

  @Test
  void givenTypeThisAndSuperQualifiers_whenProducingAndDecoding_thenWrittenOccurrencesSurviveWithoutChangingEvaluation() {
    // Arrange
    var text = """
        class Base {
          int field;
          static int staticField;
          int call() { return 1; }
          static int staticCall() { return 2; }
        }
        class Outer extends Base {
          int value() { return this.call() + super.call() + Base.staticCall() + this.field + super.field + Base.staticField; }
          class Inner extends Base {
            int value() { return Outer.this.call() + Outer.super.call() + Outer.this.field + Outer.super.field; }
          }
        }
        """;
    var input = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Outer.java")), text)));
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var forms = Set.of(InvocationForm.STATIC_TYPE, InvocationForm.INSTANCE_THIS, InvocationForm.INSTANCE_QUALIFIED_THIS,
        InvocationForm.SUPER, InvocationForm.QUALIFIED_SUPER);

    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      // Act
      var outcome = frontend.produce(new AstProductionRequest(input, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile));
      var ast = outcome.result().orElseThrow();
      var calls = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION
          && forms.contains(node.value(SyntaxValue.Invocation.class).form())).toList();
      var fields = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_ACCESS).toList();
      var decoded = AstCodec.decode(AstCodec.encode(ast), ast.identity());
      var methods = ast.program().methods().stream().filter(method -> method.name().equals("value")).toList();
      var cfg = CfgDerivers.standard().derive(ast,
          new CfgRequest(ast.identity(), methods.stream().map(method -> method.methodKey()).toList(), CfgProfile.JAVA_BOUNDED));

      // Assert
      assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
      assertEquals(forms, calls.stream().map(node -> node.value(SyntaxValue.Invocation.class).form()).collect(Collectors.toSet()));
      assertEquals(5, calls.size());
      assertEquals(5, fields.size());
      for (var selection : Stream.concat(calls.stream(), fields.stream()).toList()) {
        assertTrue(selection.optionalChild(SyntaxRole.QUALIFIER).isPresent(), selection.value().toString());
        var qualifier = selection.requiredChild(SyntaxRole.QUALIFIER);
        var range = qualifier.origin().orElseThrow();
        var written = text.substring((int) range.startOffset(), (int) range.endOffsetExclusive());
        assertTrue(Set.of("Base", "this", "super", "Outer.this", "Outer.super").contains(written), written);
        assertEquals(qualifier, decoded.aggregate().node(qualifier.nodeId()).orElseThrow());
        if (written.startsWith("Outer.")) {
          var owner = qualifier.requiredChild(SyntaxRole.QUALIFIER);
          assertEquals(SyntaxKind.TYPE_USE, owner.syntaxKind());
          var ownerRange = owner.origin().orElseThrow();
          assertEquals("Outer", text.substring((int) ownerRange.startOffset(), (int) ownerRange.endOffsetExclusive()));
        }
      }
      assertEquals(CfgState.COMPLETE, cfg.state(), cfg.diagnostics().toString());
      var operations = cfg.methods().stream().flatMap(method -> method.graph().stream())
          .flatMap(graph -> graph.operationNodes().stream()).toList();
      var qualifierKeys = Stream.concat(calls.stream(), fields.stream())
          .flatMap(selection -> selection.requiredChild(SyntaxRole.QUALIFIER).descendantsAndSelf().stream())
          .map(node -> node.nodeId()).collect(Collectors.toSet());
      assertEquals(5L, operations.stream().filter(operation -> operation.kind() == OperationKind.INVOKE).count());
      assertEquals(5L, operations.stream().filter(operation -> operation.kind() == OperationKind.MEMBER_ACCESS).count());
      assertTrue(operations.stream().noneMatch(operation -> operation.sourceRef().occurrence()
          .map(occurrence -> qualifierKeys.contains(occurrence.nodeKey())).orElse(false)));
      assertArrayEquals(AstCodec.encode(ast), AstCodec.encode(decoded));
    }
  }
}
