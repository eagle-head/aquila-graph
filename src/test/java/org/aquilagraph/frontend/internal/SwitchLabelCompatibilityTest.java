package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.testsupport.LiteralLabelFixture;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class SwitchLabelCompatibilityTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenCompilerNormalizedLiteralLabels_whenEverySupportedProfileReachesConsumers_thenBoundedQualificationAndExactLabelsSurvive() {
    // Arrange
    var profiles = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED, AstProfile.JAVA_SOURCE_INITIALIZATION,
        AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION, AstProfile.JAVA_SOURCE_DECLARATIONS,
        AstProfile.JAVA_SOURCE_UNITS, AstProfile.JAVA_SOURCE_FIDELITY);

    // Act
    var observations = profiles.stream().map(profile -> DynamicTest.dynamicTest("given" + profile.version()
        + "NormalizedLiteralLabels_whenDecodedAndDerived_thenBoundedQualificationAndExactLabelsSurvive", () -> {
          // Arrange
          var frontend = new JavaFrontend(new JavaSourceLoader());
          var request = request(LiteralLabelFixture.SOURCE, profile);

          // Act
          var original = frontend.produce(request).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
          var methods = List.of("negative", "text").stream().map(name -> decoded.program().methods().stream()
              .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
          var selection = new CfgRequest(decoded.identity(), methods, CfgProfile.JAVA_BOUNDED);
          var direct = CfgDerivers.standard().derive(decoded, selection);
          var coordinated = new SourceAnalysisRunner(frontend, CfgDerivers.standard())
              .deriveExisting(AstOutcome.available(decoded), selection);
          var restrictive = CfgDerivers.standard().derive(decoded,
              new CfgRequest(decoded.identity(), methods, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS));

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
          assertEquals(AstCompletion.COMPLETE, decoded.completion());
          assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.AVAILABLE),
              direct.methods().stream().map(method -> method.state()).toList(), direct.toString());
          LiteralLabelFixture.assertLabels(direct, decoded);
          assertEquals(direct, coordinated.representation().cfg().orElseThrow());
          assertEquals(List.of(MethodCfgState.UNSUPPORTED, MethodCfgState.UNSUPPORTED),
              restrictive.methods().stream().map(method -> method.state()).toList());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(coordinated.representation().ast().result().orElseThrow()));
          var labels = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CASE)
              .flatMap(node -> node.children(SyntaxRole.LABEL).stream()).toList();
          assertEquals(List.of(SyntaxKind.UNARY, SyntaxKind.UNARY, SyntaxKind.BINARY, SyntaxKind.BINARY),
              labels.stream().map(SyntaxNode::syntaxKind).toList());
          assertTrue(labels.stream().allMatch(node -> decoded.aggregate().writtenSourceAssociation(node.nodeId())
              .orElseThrow().normalizedExpression().isPresent()));
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenOriginalNormalizedLabels_whenHistoricalValuesReachConsumers_thenOwnQualificationAndBytesRemain() {
    // Arrange
    var profiles = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_FIDELITY);

    // Act
    var observations = profiles.stream().map(profile -> DynamicTest.dynamicTest("givenHistorical" + profile.version()
        + "_whenDecodedAndDerived_thenOriginalQualificationAndSupportedLabelsRemain", () -> {
          // Arrange
          var original = LiteralLabelFixture.historical(profile);
          var methods = List.of("negative", "text").stream().map(name -> original.program().methods().stream()
              .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();

          // Act
          var direct = CfgDerivers.standard().derive(original, new CfgRequest(original.identity(), methods, CfgProfile.JAVA_BOUNDED));

          // Assert
          assertEquals("javac-public/9", original.identity().semanticProductionRevision());
          assertEquals(profile.equals(AstProfile.JAVA_SOURCE_BOUNDED) ? AstCompletion.INCOMPLETE : AstCompletion.COMPLETE,
              original.completion());
          assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.AVAILABLE), direct.methods().stream().map(method -> method.state()).toList());
          LiteralLabelFixture.assertLabels(direct, original);
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenOtherConstantExpressionLabels_whenBoundedConsumersAreRequested_thenExistingExclusionsRemain() {
    // Arrange
    var cases = List.of(new LabelCase("int", "-0"), new LabelCase("int", "1 + 2"), new LabelCase("int", "-0x1"), new LabelCase("String", "\"a\" + (\"b\")"));

    // Act
    var observations = cases.stream().map(value -> DynamicTest.dynamicTest("givenExcludedLabel(" + value.expression()
        + ")_whenBoundedConsumersAreRequested_thenExistingExclusionsRemain", () -> {
          // Arrange
          var source = "class Example { int subject(" + value.selector() + " input) { return switch(input) { case "
              + value.expression() + " -> 1; default -> 2; }; } }";
          var frontend = new JavaFrontend(new JavaSourceLoader());

          // Act
          var bounded = frontend.produce(request(source, AstProfile.JAVA_SOURCE_BOUNDED));
          var expanded = frontend.produce(request(source, AstProfile.JAVA_SOURCE_FIDELITY)).result().orElseThrow();
          var method = expanded.program().methods().stream().filter(candidate -> candidate.name().equals("subject")).findFirst().orElseThrow();
          var derived = CfgDerivers.standard().derive(expanded,
              new CfgRequest(expanded.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
          assertEquals(AstCompletion.COMPLETE, expanded.completion(), expanded.diagnostics().toString());
          assertEquals(MethodCfgState.UNSUPPORTED, derived.methods().getFirst().state());
        }));

    // Assert
    return observations;
  }

  private AstProductionRequest request(String source, AstProfile profile) {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new AstProductionRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
  }

  private record LabelCase(String selector, String expression) {
  }
}
