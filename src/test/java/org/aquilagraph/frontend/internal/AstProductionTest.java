package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AstProductionTest {
  @TempDir Path directory;

  @Test
  void givenCapturedJavaSource_whenProducingAndDecoding_thenAllOwnedFactsAndIdentityAreRetained() throws IOException {
    // Arrange
    var input = snapshot(directory, """
        package sample;
        final class Stored {
          int value(int x) { return x + 7; }
          String join(Object value) { return "prefix" + value; }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var outcome = frontend.produce(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var result = outcome.result().orElseThrow();
    var bytes = AstCodec.encode(result);
    var decoded = AstCodec.decode(bytes, result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion());
    assertEquals(result.identity(), decoded.identity());
    assertEquals(result.snapshot(), decoded.snapshot());
    assertEquals(result.environment(), decoded.environment());
    assertEquals(result.program(), decoded.program());
    assertEquals(result.aggregate(), decoded.aggregate());
    assertEquals(result.diagnostics(), decoded.diagnostics());
    assertEquals(result.coverage(), decoded.coverage());
    assertArrayEquals(bytes, AstCodec.encode(decoded));
    for (var key : result.aggregate().methodBodyKeys()) {
      var original = result.aggregate().methodSyntax(key).orElseThrow();
      var retained = decoded.aggregate().methodSyntax(key).orElseThrow();
      assertEquals(original.evidence(), retained.evidence());
      assertEquals(result.bodyRef(key), decoded.bodyRef(key));
      if (decoded.bodyRef(key).orElseThrow().sourceOccurrenceKey().isPresent()) {
        assertSame(decoded.aggregate().node(retained.root().nodeId()).orElseThrow(), retained.root());
      }
    }
    assertTrue(decoded.aggregate().nodes().stream().anyMatch(node -> !node.children(SyntaxRole.MEMBER).isEmpty()));
  }

  @Test
  void givenEqualCapturedContentAtDifferentRoots_whenProducing_thenIdentityExcludesRootButStoragePreservesIt() throws IOException {
    // Arrange
    var first = snapshot(directory.resolve("first"), "class Stored { int value() { return 1; } }");
    var second = snapshot(directory.resolve("second"), "class Stored { int value() { return 1; } }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var left = frontend.produce(first, configuration(), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var right = frontend.produce(second, configuration(), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var stored = AstCodec.encode(right);
    var decoded = AstCodec.decode(stored, left.identity());

    // Assert
    assertNotEquals(first.sourceRoot(), second.sourceRoot());
    assertEquals(left.identity(), right.identity());
    assertFalse(Arrays.equals(AstCodec.encode(left), stored));
    assertEquals(second.sourceRoot(), decoded.snapshot().sourceRoot());
    assertEquals(right.coverage(), decoded.coverage());
  }

  @Test
  void givenNormalizedRelativeSources_whenCataloguedAndProduced_thenEachSourceKeepsItsOwnCorrelation() throws IOException {
    // Arrange
    var left = SourceFile.fromContent(capturedPath(Path.of("discard/../Left.java")), "class Left { int value() { return 1; } }");
    var right = SourceFile.fromContent(capturedPath(Path.of("nested/Right.java")), "class Right { int value() { return 2; } }");
    var input = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(right, left));
    var catalog = new SnapshotSourceCatalog(input);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var units = catalog.compilationUnits();
    var outcome = frontend.produce(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED);

    // Assert
    assertEquals(List.of("Left.java", "nested/Right.java"), input.sourceFiles().stream().map(SourceFile::canonicalRelativePath).toList());
    assertEquals(2, units.size());
    assertNotEquals(units.getFirst().toUri(), units.getLast().toUri());
    assertSame(left, catalog.find(units.getFirst()).orElseThrow());
    assertSame(right, catalog.find(units.getLast()).orElseThrow());
    assertEquals(left.content(), units.getFirst().getCharContent(false).toString());
    assertEquals(right.content(), units.getLast().getCharContent(false).toString());
    assertEquals(AstCompletion.COMPLETE, outcome.completion());
    assertEquals(input, outcome.result().orElseThrow().snapshot());
  }

  @Test
  void givenEmptyCaptureAndCompilerSupplierThatFails_whenProducing_thenUnavailableInputReturnsWithoutCompilerAcquisition() throws IOException {
    // Arrange
    var calls = new AtomicInteger();
    var pipeline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()), () -> {
      calls.incrementAndGet();
      throw new AssertionError("Empty source capture must not acquire a compiler");
    });
    var frontend = new JavaFrontend(new JavaSourceLoader(), pipeline);
    var input = InputSnapshot.fromSources(capturedAbsolute(directory), List.of());

    // Act
    var outcome = frontend.produce(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED);

    // Assert
    assertEquals(AstCompletion.UNAVAILABLE, outcome.completion());
    assertEquals(input, outcome.capturedInput().orElseThrow());
    assertTrue(outcome.result().isEmpty());
    assertTrue(outcome.capturedEnvironment().isEmpty());
    assertEquals(configuration(), outcome.requestedConfiguration().orElseThrow());
    assertEquals(input, outcome.coverageQualification().orElseThrow().validationEvidence().writtenInventory().snapshot());
    assertFalse(outcome.sourceSubjectDigest().isBlank());
    assertEquals(0, calls.get());
  }

  @Test
  void givenAvailableSourceAndCorruptedOrForeignPayload_whenDecoding_thenAdmissionRejectsWithoutReturningAResult() throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var original = frontend.produce(snapshot(directory, "class Stored { int value() { return 1; } }"), configuration(),
        AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var foreign = frontend.produce(snapshot(directory, "class Stored { int value() { return 2; } }"), configuration(),
        AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var bytes = AstCodec.encode(original);
    var trailing = Arrays.copyOf(bytes, bytes.length + 1);
    var truncated = Arrays.copyOf(bytes, bytes.length - 1);

    // Act
    var identityFailure = assertThrows(AstContractException.class, () -> AstCodec.decode(bytes, foreign.identity()));
    var trailingFailure = assertThrows(AstContractException.class, () -> AstCodec.decode(trailing, original.identity()));
    var truncatedFailure = assertThrows(AstContractException.class, () -> AstCodec.decode(truncated, original.identity()));

    // Assert
    assertTrue(identityFailure.getMessage().contains("identity"));
    assertTrue(trailingFailure.getMessage().contains("trailing"));
    assertTrue(truncatedFailure.getCause() instanceof IllegalArgumentException);
    assertNotEquals(original.identity(), foreign.identity());
  }

  @Test
  void givenRetainedFieldInitializationGapAndAnnotationDefaults_whenRoundTripping_thenIncompleteFactsAndDiagnosticsRemainQueryable()
      throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var input = snapshot(directory, """
        @interface Values {
          float f() default -0.0f;
          double d() default -0.0d;
          int[] repeated() default {1, 1, 2};
        }
        @Values class Stored {
          static final int CONSTANT = 7;
          int value() { return CONSTANT; }
        }
        """);

    // Act
    var outcome = frontend.produce(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertTrue(decoded.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-field-initializer")));
    assertEquals(original.diagnostics(), decoded.diagnostics());
    assertEquals(original.program(), decoded.program());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.coverage(), decoded.coverage());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
  }

  private static InputSnapshot snapshot(Path root, String source) {
    return InputSnapshot.fromSources(capturedAbsolute(root), List.of(SourceFile.fromContent(capturedPath(Path.of("Stored.java")), source)));
  }

  private static CompilerConfiguration configuration() {
    return new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of());
  }
}
