package org.aquilagraph.source.admission;

import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StringEvidenceRetentionTest {
  @TempDir Path directory;

  @Test
  void givenSourceOnlyStringExpressionWithUnavailableClassification_whenDeriving_thenExecutableBodyAndSourceQualificationArePreserved()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Label { String value(); }
        class Stored { String join(Object value) { return (@Label("prefix" + Integer.MAX_VALUE) String) value; } }
        """)));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    var method = original.program().methods().stream().filter(value -> value.name().equals("join")).findFirst().orElseThrow();
    var binary = original.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.BINARY).findFirst().orElseThrow();
    var expression = new StringExpressionEvidence(method.methodKey(), binary.nodeId(), binary.resolvedType().orElseThrow(),
        EvidenceValue.known(true, new EvidenceOrigin(method.methodKey(), binary.nodeId(), StringExpressionEvidence.LEFT_SLOT, binary.origin())),
        EvidenceValue.known(false, new EvidenceOrigin(method.methodKey(), binary.nodeId(), StringExpressionEvidence.RIGHT_SLOT, binary.origin())),
        EvidenceValue.known(true, new EvidenceOrigin(method.methodKey(), binary.nodeId(), StringExpressionEvidence.CONSTANT_SLOT, binary.origin())));
    var origin = expression.rightIsString().origin();
    for (var classification : List.of(EvidenceValue.<Boolean>missing(origin, "Classification unavailable"),
        EvidenceValue.<Boolean>erroneous(origin, "Classification erroneous"))) {
      var replacement = new StringExpressionEvidence(expression.methodKey(), expression.binaryNodeId(), expression.stringType(),
          expression.leftIsString(), classification, expression.compileTimeConstant());
      // Act
      var retained = replace(original, replacement);
      var decoded = AstCodec.decode(AstCodec.encode(retained), retained.identity());
      var identity = decoded.identity();
      var outcome = CfgDerivers.standard().derive(decoded,
          new CfgRequest(identity, List.of(expression.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

      // Assert
      assertEquals(AstCompletion.COMPLETE, decoded.completion(), decoded.diagnostics().toString());
      assertEquals(MethodCfgState.AVAILABLE, outcome.state(), outcome.diagnostics().toString());
      assertEquals(identity, decoded.identity());
      assertEquals(replacement, expression(decoded));
      assertTrue(outcome.graph().orElseThrow().operationNodes().stream().allMatch(operation -> operation.sourceRef().orElseThrow()
          .occurrence().stream().noneMatch(occurrence -> occurrence.nodeKey().equals(expression.binaryNodeId()))));
    }
  }

  @Test
  void givenKnownStringResultAndOtherClassifications_whenOneOperandClassificationIsMissingOrErroneous_thenRetainKnownFactsAndRefuseOnlyThatSlot()
      throws IOException {
    // Arrange
    var original = produce();
    var expression = expression(original);
    var origin = expression.rightIsString().origin();
    for (var classification : List.of(EvidenceValue.<Boolean>missing(origin, "Classification unavailable"),
        EvidenceValue.<Boolean>erroneous(origin, "Classification erroneous"))) {
      var replacement = new StringExpressionEvidence(expression.methodKey(), expression.binaryNodeId(), expression.stringType(),
          expression.leftIsString(), classification, expression.compileTimeConstant());
      // Act
      var retained = replace(original, replacement);
      var bytes = AstCodec.encode(retained);
      var decoded = AstCodec.decode(bytes, retained.identity());
      var outcome = CfgDerivers.standard().derive(decoded,
          new CfgRequest(decoded.identity(), List.of(expression.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

      // Assert
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
      assertNotEquals(original.identity(), decoded.identity());
      assertNotEquals(original.aggregate(), decoded.aggregate());
      assertEquals(replacement, expression(decoded));
      assertEquals(original.aggregate().node(expression.binaryNodeId()), decoded.aggregate().node(expression.binaryNodeId()));
      assertEquals(Optional.of(true), expression(decoded).leftIsString().knownValue());
      assertEquals(Optional.of(false), expression(decoded).compileTimeConstant().knownValue());
      assertArrayEquals(bytes, AstCodec.encode(decoded));
      var state = classification.state() == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
      assertEquals(state, outcome.state());
      assertEquals(List.of(state), outcome.blockers().stream().map(value -> value.kind()).toList());
      assertEquals(List.of(StringExpressionEvidence.RIGHT_SLOT), outcome.blockers().stream().map(value -> value.slot()).toList());
      assertEquals(classification.explanation().orElseThrow(), outcome.blockers().getFirst().diagnostic().message());
      assertTrue(outcome.blockers().getFirst().evidenceDiagnostic().isEmpty());
      assertTrue(outcome.graph().isEmpty());
    }
  }

  private AstResult produce() throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"),
        "class Stored { String join(Object value) { return \"prefix\" + value; } }")));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
  }

  private static StringExpressionEvidence expression(AstResult ast) {
    var method = ast.program().types().stream().flatMap(type -> type.methods().stream()).filter(value -> value.name().equals("join"))
        .findFirst().orElseThrow();
    return ast.body(method.methodKey()).orElseThrow().evidence().stringExpressions().getFirst();
  }

  private static AstResult replace(AstResult original, StringExpressionEvidence replacement) {
    var copied = CopiedAstValues.capture(original.aggregate());
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
        body.invocations(), body.staticAccesses(), body.methodKey().equals(replacement.methodKey())
            ? Stream.concat(body.concatenations().stream()
                .filter(value -> !value.binaryNodeId().equals(replacement.binaryNodeId())), Stream.of(replacement)).toList()
            : body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes()));
    return AstResultAdmission.admit(new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
        Optional.of(aggregate), original.diagnostics(), original.coverage()), original.profile(), original.identity().semanticProductionRevision());
  }
}
