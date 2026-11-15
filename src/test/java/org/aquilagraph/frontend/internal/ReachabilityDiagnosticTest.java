package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ReachabilityDiagnosticTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenUnreachableOrIllegalAbruptSource_whenProducedAndDecoded_thenTheSpecificLocatedCompilerErrorPreventsCompletion() {
    return Stream.of(
        new RejectedCase("AfterReturn", "void subject() { return; int dead = 1; }", "compiler.err.unreachable.stmt", "int dead = 1;"),
        new RejectedCase("AfterThrow", "void subject() { throw new RuntimeException(); int dead = 1; }", "compiler.err.unreachable.stmt", "int dead = 1;"),
        new RejectedCase("FalseWhileBody", "void subject() { while (false) { sink(); } }", "compiler.err.unreachable.stmt", "{ sink(); }"),
        new RejectedCase("AfterInfiniteWhile", "void subject() { while (true) {} sink(); }", "compiler.err.unreachable.stmt", "sink();"),
        new RejectedCase("FalseForBody", "void subject() { for (;false;) { sink(); } }", "compiler.err.unreachable.stmt", "{ sink(); }"),
        new RejectedCase("AfterInfiniteFor", "void subject() { for (;;) {} sink(); }", "compiler.err.unreachable.stmt", "sink();"),
        new RejectedCase("AfterInfiniteDo", "void subject() { do {} while (true); sink(); }", "compiler.err.unreachable.stmt", "sink();"),
        new RejectedCase("AfterBreak", "void subject() { while (true) { break; sink(); } }", "compiler.err.unreachable.stmt", "sink();"),
        new RejectedCase("AfterContinue", "void subject() { while (true) { continue; sink(); } }", "compiler.err.unreachable.stmt", "sink();"),
        new RejectedCase("MissingReturn", "int subject(boolean flag) { if (flag) return 1; }", "compiler.err.missing.ret.stmt", "{ if (flag) return 1; }"),
        new RejectedCase("BreakOutsideLoop", "void subject() { break; }", "compiler.err.break.outside.switch.loop", "break;"),
        new RejectedCase("ContinueOutsideLoop", "void subject() { continue; }", "compiler.err.cont.outside.loop", "continue;"),
        new RejectedCase("ContinueNonLoopLabel", "void subject() { label: { continue label; } }", "compiler.err.not.loop.label", "continue label;"))
        .map(variant -> DynamicTest.dynamicTest("given" + variant.name() + "_whenProduced_thenTheIntendedCompilerErrorIsRetained", () -> {
          // Arrange
          var source = "class Example { void sink() {} " + variant.method()
              + " } class Neighbor { int preserved() { return 19; } }";

          // Act
          var outcome = produce(source);
          var original = outcome.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
          for (var ast : List.of(original, decoded)) {
            assertEquals(AstCompletion.INCOMPLETE, ast.completion());
            var errors = ast.diagnostics().stream().filter(diagnostic -> diagnostic.code().startsWith("compiler.err.")).toList();
            assertEquals(List.of(variant.code()), errors.stream().map(diagnostic -> diagnostic.code()).toList(), ast.diagnostics().toString());
            var diagnostic = errors.getFirst();
            assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
            assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
            assertEquals(AnalysisOutcomeKind.COMPILER_ERROR, diagnostic.outcomeKind());
            assertEquals(1, diagnostic.origins().size());
            var origin = diagnostic.origins().getFirst();
            var start = source.indexOf(variant.location(), source.indexOf(variant.method()));
            assertTrue(start >= 0);
            assertEquals(start, origin.startOffset(), diagnostic.toString());
            assertEquals(start + variant.location().length(), origin.endOffsetExclusive(), diagnostic.toString());
            assertEquals(original.snapshot().sourceFiles().getFirst().sourceFileKey(), origin.sourceFileKey());
            assertNeighbor(ast);
          }
          assertEquals(original.diagnostics(), decoded.diagnostics());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenLegalReachabilityContrasts_whenProducedAndDecoded_thenCompleteSourceAndNeighborRemainAvailable() {
    return Stream.of(
        "void subject(boolean flag) { if (flag) return; sink(); }",
        "void subject(boolean flag) { if (flag) throw new RuntimeException(); sink(); }",
        "void subject(boolean flag) { while (flag) { sink(); } sink(); }",
        "void subject() { while (true) { break; } sink(); }",
        "void subject(boolean flag) { for (;flag;) { sink(); } sink(); }",
        "void subject() { for (;;) { break; } sink(); }",
        "void subject() { do { break; } while (true); sink(); }",
        "void subject() { do { continue; } while (false); sink(); }",
        "void subject() { if (false) sink(); sink(); }",
        "int subject(boolean flag) { if (flag) return 1; else return 2; }",
        "int subject() { while (true) {} }",
        "void subject() { label: { break label; } sink(); }",
        "void subject(boolean flag) { label: while (flag) { continue label; } sink(); }")
        .map(method -> DynamicTest.dynamicTest(method, () -> {
          // Arrange
          var source = "class Example { void sink() {} " + method + " } class Neighbor { int preserved() { return 19; } }";

          // Act
          var outcome = produce(source);
          var original = outcome.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          for (var ast : List.of(original, decoded)) {
            assertEquals(AstCompletion.COMPLETE, ast.completion());
            assertFalse(ast.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
            assertNeighbor(ast);
          }
        }));
  }

  private AstOutcome produce(String source) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return JavaFrontends.standard().produce(new AstProductionRequest(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
  }

  private static void assertNeighbor(AstResult ast) {
    var owner = new JavaTypeDeclarationIdentity(Optional.empty(), "Neighbor").declarationKey();
    var members = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration method
        && method.declarationKey().equals(owner + "#preserved()")).toList();
    assertEquals(1, members.size());
    assertEquals(owner, members.getFirst().value(SyntaxValue.ExecutableDeclaration.class).ownerKey());
    var expression = members.getFirst().requiredChild(SyntaxRole.BODY).children(SyntaxRole.STATEMENT).getFirst()
        .requiredChild(SyntaxRole.EXPRESSION);
    assertEquals("19", expression.value(SyntaxValue.Literal.class).text());
  }

  private record RejectedCase(String name, String method, String code, String location) {}
}
