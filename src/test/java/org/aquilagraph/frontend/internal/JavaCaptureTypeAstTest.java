package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxType;
import org.junit.jupiter.api.Test;

class JavaCaptureTypeAstTest {
  @Test
  void givenUnboundedUpperAndLowerWildcards_whenAnalyzed_thenCapturedResultsRetainBothBounds() throws IOException {
    // Arrange
    var source = """
        class CapturedTypes {
          static Object unbounded(java.util.List<?> values) { return values.get(0); }
          static Number upper(java.util.List<? extends Number> values) { return values.get(0); }
          static Object lower(java.util.List<? super Integer> values) { return values.get(0); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals(new JavaTypeStructure.Captured(0, declared("java.lang.Object"), Optional.empty()),
        structure(node(input, "unbounded", SyntaxKind.INVOCATION)));
    assertEquals(new JavaTypeStructure.Captured(0, declared("java.lang.Number"), Optional.empty()),
        structure(node(input, "upper", SyntaxKind.INVOCATION)));
    assertEquals(new JavaTypeStructure.Captured(0, declared("java.lang.Object"), Optional.of(declared("java.lang.Integer"))),
        structure(node(input, "lower", SyntaxKind.INVOCATION)));
  }

  @Test
  void givenRecursiveAndIndependentCaptures_whenAnalyzed_thenRecursiveReferencesAndDistinctBindersRemainComplete() throws IOException {
    // Arrange
    var source = """
        interface Recursive<T extends Recursive<T>> { T self(); }
        interface Pair<L, R> { Pair<L, R> both(); }
        class CapturedTypes {
          static Object recursive(Recursive<?> value) { return value.self(); }
          static Object independent(Pair<?, ?> value) { return value.both(); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals(new JavaTypeStructure.Captured(0,
        new JavaTypeStructure.Declared("type:Recursive", Optional.empty(), List.of(new JavaTypeStructure.CaptureReference(0))),
        Optional.empty()), structure(node(input, "recursive", SyntaxKind.INVOCATION)));
    assertEquals(new JavaTypeStructure.Declared("type:Pair", Optional.empty(), List.of(
        new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED, Optional.empty()),
        new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED, Optional.empty()))),
        structure(node(input, "independent", SyntaxKind.VARIABLE_ACCESS)));
    assertEquals(new JavaTypeStructure.Declared("type:Pair", Optional.empty(), List.of(
        new JavaTypeStructure.Captured(0, declared("java.lang.Object"), Optional.empty()),
        new JavaTypeStructure.Captured(1, declared("java.lang.Object"), Optional.empty()))),
        structure(node(input, "independent", SyntaxKind.INVOCATION)));
  }

  @Test
  void givenDeclaredTypeParameter_whenAnalyzed_thenItsDeclarationKeyRemainsSeparateFromCaptureBinders() throws IOException {
    // Arrange
    var source = """
        class CapturedTypes {
          static <T extends Number> T declared(T value) { return value; }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals(new JavaTypeStructure.Variable("type:CapturedTypes#declared(java.lang.Number)#type-parameter:0"),
        structure(node(input, "declared", SyntaxKind.VARIABLE_ACCESS)));
  }

  @Test
  void givenSameCapturedSource_whenAnalyzedInSeparateSessions_thenCaptureStructureUsesTheSameLocalOrdinals() throws IOException {
    // Arrange
    var source = """
        interface Recursive<T extends Recursive<T>> { T self(); }
        class CapturedTypes {
          static Object recursive(Recursive<?> value) { return value.self(); }
        }
        """;

    // Act
    var first = analyze(source);
    var second = analyze(source);

    // Assert
    assertCompleted(first);
    assertCompleted(second);
    assertEquals(structure(node(first, "recursive", SyntaxKind.INVOCATION)),
        structure(node(second, "recursive", SyntaxKind.INVOCATION)));
  }

  private static SyntaxAnalysisInput analyze(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/compiler-input"),
        List.of(SourceFile.fromContent(Path.of("CapturedTypes.java"), source)));
    return new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
  }

  private static void assertCompleted(SyntaxAnalysisInput input) {
    assertTrue(input.diagnostics().stream().noneMatch(diagnostic -> diagnostic.isError()), input.diagnostics().toString());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(input).completion());
  }

  private static SyntaxNode node(SyntaxAnalysisInput input, String methodName, SyntaxKind kind) {
    return input.methodSyntax().stream().filter(method -> method.methodKey().startsWith("type:CapturedTypes#" + methodName + "("))
        .findFirst().orElseThrow().root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == kind).findFirst().orElseThrow();
  }

  private static JavaTypeStructure structure(SyntaxNode node) {
    return ((SyntaxType.Resolved) node.type()).structure().orElseThrow();
  }

  private static JavaTypeStructure.Declared declared(String name) {
    return new JavaTypeStructure.Declared("type:" + name, Optional.empty(), List.of());
  }
}
