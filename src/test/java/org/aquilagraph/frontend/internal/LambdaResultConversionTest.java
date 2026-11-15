package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class LambdaResultConversionTest {
  @TempDir
  Path directory;

  @TestFactory
  Stream<DynamicTest> givenRepresentableLambdaResults_whenBothBodiesAreProducedAndDecoded_thenExactAssignmentConversionsSurvive() {
    return Stream.of(new ResultCase("Byte", "127", "byte", "Byte"),
        new ResultCase("Short", "32767", "short", "Short"),
        new ResultCase("Character", "65535", "char", "Character"),
        new ResultCase("Number", "1", "int", "Integer"))
        .map(example -> DynamicTest.dynamicTest("given" + example.targetType()
            + "Result_whenBothBodiesAreProduced_thenExactBoxingSurvives", () -> {
              // Arrange
              var source = """
                  import java.util.function.Supplier;
                  class Example {
                    Supplier<%1$s> expression() { return () -> %2$s; }
                    Supplier<%1$s> block() { return () -> { return %2$s; }; }
                  }
                  """.formatted(example.targetType(), example.expression());

              // Act
              var original = produce(source);
              var bytes = AstCodec.encode(original);
              var retained = AstCodec.decode(bytes, original.identity());

              // Assert
              assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
              assertTrue(original.diagnostics().isEmpty());
              assertEquals(original.identity(), retained.identity());
              assertArrayEquals(bytes, AstCodec.encode(retained));
              for (var result : List.of(original, retained)) {
                assertResultConversion(result, "expression", SyntaxKind.LAMBDA_EXPRESSION, SyntaxRole.BODY, example);
                assertResultConversion(result, "block", SyntaxKind.RETURN, SyntaxRole.EXPRESSION, example);
              }
            }));
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidLambdaResultAssignments_whenProduced_thenCompilerErrorsPreventCompletionAndKnownBoxing() {
    return Stream.of(new InvalidResult("Byte", "128"), new InvalidResult("Short", "32768"),
        new InvalidResult("Character", "65536"), new InvalidResult("Byte", "value"))
        .flatMap(example -> Stream.of(false, true).map(block -> DynamicTest.dynamicTest("given" + example.targetType()
            + "Result" + example.expression() + (block ? "Block" : "Expression")
            + "_whenProduced_thenCompilerErrorsPreventCompletion", () -> {
              // Arrange
              var body = block ? "{ return " + example.expression() + "; }" : example.expression();
              var source = """
                  import java.util.function.Supplier;
                  class Example {
                    int value = 1;
                    Supplier<%s> subject() { return () -> %s; }
                  }
                  """.formatted(example.targetType(), body);

              // Act
              var original = produce(source);
              var bytes = AstCodec.encode(original);
              var retained = AstCodec.decode(bytes, original.identity());

              // Assert
              assertEquals(AstCompletion.INCOMPLETE, original.completion());
              assertTrue(original.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()
                  && diagnostic.code().startsWith("compiler.")), original.diagnostics().toString());
              assertEquals(original.qualification(), retained.qualification());
              assertEquals(original.diagnostics(), retained.diagnostics());
              assertArrayEquals(bytes, AstCodec.encode(retained));
              for (var result : List.of(original, retained)) {
                assertTrue(result.aggregate().executionScopes().stream()
                    .flatMap(scope -> result.aggregate().executionSyntax(scope.key()).orElseThrow().evidence().uses().stream())
                    .filter(use -> use.sourceType().erasedName().equals("int")
                        && use.selectedTarget().erasedName().equals("java.lang." + example.targetType()))
                    .noneMatch(use -> use.conversion().state() == EvidenceState.KNOWN));
              }
            })));
  }

  @Test
  void givenVoidCompatibleLambdaBodies_whenProducedAndDecoded_thenDiscardedCallResultsHaveNoBodyConversion() throws IOException {
    // Arrange
    var source = """
        class Example {
          int record() { return 1; }
          Runnable expression() { return () -> record(); }
          Runnable block() { return () -> { record(); }; }
        }
        """;

    // Act
    var original = produce(source);
    var bytes = AstCodec.encode(original);
    var retained = AstCodec.decode(bytes, original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertTrue(original.diagnostics().isEmpty());
    assertEquals(original.identity(), retained.identity());
    assertArrayEquals(bytes, AstCodec.encode(retained));
    for (var result : List.of(original, retained)) {
      for (var method : List.of("expression", "block")) {
        var scope = result.aggregate().executionSyntax("type:Example#" + method + "()").orElseThrow();
        var lambda = scope.root().descendantsAndSelf().stream()
            .filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).findFirst().orElseThrow();
        var invocation = lambda.requiredChild(SyntaxRole.BODY).descendantsAndSelf().stream()
            .filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
        assertEquals("int", invocation.resolvedType().orElseThrow().erasedName());
        assertTrue(scope.useEvidence(lambda.nodeId(), SyntaxRole.BODY, 0).isEmpty());
      }
    }
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return JavaFrontends.standard().produce(new AstProductionRequest(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION)).result().orElseThrow();
  }

  private static void assertResultConversion(AstResult result, String method, SyntaxKind consumerKind, SyntaxRole role, ResultCase example) {
    var scope = result.aggregate().executionSyntax("type:Example#" + method + "()").orElseThrow();
    var lambda = scope.root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).findFirst().orElseThrow();
    var consumer = lambda.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == consumerKind).findFirst().orElseThrow();
    var use = scope.useEvidence(consumer.nodeId(), role, 0).orElseThrow();
    assertEquals("int", consumer.requiredChild(role).resolvedType().orElseThrow().erasedName());
    assertEquals("int", use.sourceType().erasedName());
    assertEquals("java.lang." + example.targetType(), use.selectedTarget().erasedName());
    assertEquals(EvidenceState.KNOWN, use.conversion().state());
    var conversion = use.conversion().knownValue().orElseThrow();
    assertEquals(Optional.of(example.primitiveType()), conversion.preBoxPrimitiveType().map(type -> type.erasedName()));
    assertEquals(Optional.of("java.lang." + example.boxedType()), conversion.boxedType().map(type -> type.erasedName()));
    assertTrue(conversion.checkedReferenceTarget().isEmpty());
    assertTrue(conversion.unboxedType().isEmpty());
    assertTrue(conversion.effectivePrimitiveType().isEmpty());
    assertEquals(scope.scopeKey(), use.scopeKey());
    assertEquals(consumer.nodeId(), use.consumerNodeId());
    assertEquals(role, use.role());
    assertEquals(0, use.position());
    assertEquals(scope.scopeKey(), use.conversion().origin().scopeKey());
    assertEquals(consumer.nodeId(), use.conversion().origin().nodeKey());
    assertEquals("child-use.conversion/" + role.stableId() + "/0", use.conversion().origin().slot());
    assertEquals(consumer.origin(), use.conversion().origin().sourceRange());
    assertEquals(EvidenceState.NOT_APPLICABLE, use.referenceCheck().state());
    assertEquals(Optional.of(conversion), scope.useDecision(consumer.nodeId(), role, 0));
  }

  private record ResultCase(String targetType, String expression, String primitiveType, String boxedType) {
  }

  private record InvalidResult(String targetType, String expression) {
  }
}
