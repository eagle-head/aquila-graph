package org.aquilagraph.cfg.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class CfgDerivationExceptionTest {
  private static final List<AstProfile> SOURCE_PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED);
  private static final List<CfgProfile> CFG_PROFILES = List.of(CfgProfile.JAVA_BOUNDED, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
  @TempDir Path directory;

  @Test
  void givenRealCompletedGraphForAnotherProfile_whenAdmittingAbruptProgress_thenQualificationRejectsRelabeling() {
    assertAll(SOURCE_PROFILES.stream().map(sourceProfile -> (Executable) () -> {
      var ast = fixture(sourceProfile);
      for (var profile : CFG_PROFILES) {
        var original = graphOutcome(ast, profile);
        var other = profile.equals(CfgProfile.JAVA_BOUNDED) ? CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS : CfgProfile.JAVA_BOUNDED;
        var request = new CfgRequest(ast.identity(), List.of(key(ast, "first"), key(ast, "last")), other);
        assertThrows(IllegalArgumentException.class,
            () -> new CfgDerivationException(request, List.of(original), new IllegalStateException("Construction observation")));
      }
    }));
  }

  @Test
  void givenRealCompletedGraphForAnotherModel_whenAdmittingAbruptProgress_thenQualificationRejectsRelabeling() {
    assertAll(SOURCE_PROFILES.stream().map(sourceProfile -> (Executable) () -> {
      var ast = fixture(sourceProfile);
      for (var profile : CFG_PROFILES) {
        var original = graphOutcome(ast, profile);
        var request = new CfgRequest(ast.identity(), List.of(key(ast, "first"), key(ast, "last")),
            new CfgProfile(profile.version(), "java-cfg/unsupported"));
        assertThrows(IllegalArgumentException.class,
            () -> new CfgDerivationException(request, List.of(original), new IllegalStateException("Construction observation")));
      }
    }));
  }

  @Test
  void givenRepeatedSelectedKeys_whenAdmittingCompletedProgress_thenInvalidSelectionCannotAcquireProgress() {
    assertAll(SOURCE_PROFILES.stream().map(sourceProfile -> (Executable) () -> {
      var ast = fixture(sourceProfile);
      var original = graphOutcome(ast, CfgProfile.JAVA_BOUNDED);
      var repeated = new CfgRequest(ast.identity(), List.of(key(ast, "first"), key(ast, "first"), key(ast, "last")),
          CfgProfile.JAVA_BOUNDED);
      var laterRepeated = new CfgRequest(ast.identity(), List.of(key(ast, "first"), key(ast, "last"), key(ast, "last")),
          CfgProfile.JAVA_BOUNDED);
      assertAll(
          () -> assertThrows(IllegalArgumentException.class,
              () -> new CfgDerivationException(repeated, List.of(original, original), new IllegalStateException("Construction observation"))),
          () -> assertThrows(IllegalArgumentException.class,
              () -> new CfgDerivationException(laterRepeated, List.of(original), new IllegalStateException("Construction observation"))));
    }));
  }

  @Test
  void givenCompletedGraphForAnotherAst_whenAdmittingAbruptProgress_thenExactSourceQualificationRejectsIt() {
    assertAll(SOURCE_PROFILES.stream().map(sourceProfile -> (Executable) () -> {
      var ast = fixture(sourceProfile);
      var otherProfile = sourceProfile.equals(AstProfile.JAVA_SOURCE_BOUNDED) ? AstProfile.JAVA_SOURCE_EXPANDED : AstProfile.JAVA_SOURCE_BOUNDED;
      var other = fixture(otherProfile);
      var request = new CfgRequest(other.identity(), List.of(key(other, "first"), key(other, "last")), CfgProfile.JAVA_BOUNDED);
      var completed = graphOutcome(ast, CfgProfile.JAVA_BOUNDED);
      assertThrows(IllegalArgumentException.class,
          () -> new CfgDerivationException(request, List.of(completed), new IllegalStateException("Construction observation")));
    }));
  }

  @Test
  void givenReorderedOrCompleteProgress_whenAdmittingAbruptFailure_thenOnlyAProperOrderedPrefixIsPermitted() {
    assertAll(SOURCE_PROFILES.stream().map(sourceProfile -> (Executable) () -> {
      var ast = fixture(sourceProfile);
      var request = new CfgRequest(ast.identity(), List.of(key(ast, "first"), key(ast, "last")), CfgProfile.JAVA_BOUNDED);
      var complete = CfgDerivers.standard().derive(ast, request).methods();
      assertAll(
          () -> assertThrows(IllegalArgumentException.class,
              () -> new CfgDerivationException(request, List.of(complete.getLast()), new IllegalStateException("Construction observation"))),
          () -> assertThrows(IllegalArgumentException.class,
              () -> new CfgDerivationException(request, complete, new IllegalStateException("Construction observation"))));
    }));
  }

  @Test
  void givenMatchingRealGraphPrefix_whenAdmittingAbruptFailure_thenRequestGraphAndEffectiveCauseRemainExact() {
    assertAll(SOURCE_PROFILES.stream().map(sourceProfile -> (Executable) () -> {
      var ast = fixture(sourceProfile);
      for (var profile : CFG_PROFILES) {
        var request = new CfgRequest(ast.identity(), List.of(key(ast, "first"), key(ast, "last")), profile);
        var graph = graphOutcome(ast, profile);
        var cause = new IllegalStateException("Construction observation");
        var failure = new CfgDerivationException(request, List.of(graph), cause);
        assertSame(request, failure.request());
        assertSame(cause, failure.getCause());
        assertEquals(List.of(graph), failure.completedMethods());
        assertSame(graph, failure.completedMethods().getFirst());
        assertEquals(profile.version(), graph.graph().orElseThrow().identity().orElseThrow().cfgProfileVersion());
        assertEquals(profile.graphModelVersion(), graph.graph().orElseThrow().identity().orElseThrow().graphModelVersion());
      }
    }));
  }

  @Test
  void givenRealBodylessAndRefusedPrefix_whenAdmittingAbruptFailure_thenNonGraphProgressAndDiagnosticsRemainOrdered() {
    assertAll(SOURCE_PROFILES.stream().map(sourceProfile -> (Executable) () -> {
      var ast = fixture(sourceProfile);
      var keys = List.of(key(ast, "bodyless"), key(ast, "refused"));
      var completed = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), keys, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS));
      var request = new CfgRequest(ast.identity(), List.of(keys.getFirst(), keys.getLast(), key(ast, "last")),
          CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
      var cause = new IllegalStateException("Construction observation");
      var failure = new CfgDerivationException(request, completed.methods(), cause);
      assertEquals(List.of(MethodCfgState.NOT_APPLICABLE, MethodCfgState.UNSUPPORTED),
          failure.completedMethods().stream().map(MethodCfgOutcome::state).toList());
      assertEquals(completed.methods(), failure.completedMethods());
      assertEquals(completed.diagnostics(), failure.completedMethods().stream().flatMap(value -> value.diagnostics().stream()).toList());
      assertSame(request, failure.request());
      assertSame(cause, failure.getCause());
      assertTrue(failure.completedMethods().stream().allMatch(value -> value.graph().isEmpty()));
    }));
  }

  private AstResult fixture(AstProfile profile) throws IOException {
    var input = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Progress.java"), """
        interface Progress {
          int bodyless();
          default int first() { return 7; }
          default int refused(int input) { return switch (input) { case 1 -> 2; default -> 3; }; }
          default int last() { return 9; }
        }
        """)));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(input, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    return ast;
  }

  private static MethodCfgOutcome graphOutcome(AstResult ast, CfgProfile profile) {
    var result = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), List.of(key(ast, "first")), profile));
    assertEquals(CfgState.COMPLETE, result.state());
    assertEquals(MethodCfgState.AVAILABLE, result.methods().getFirst().state());
    return result.methods().getFirst();
  }

  private static String key(AstResult ast, String name) {
    return ast.program().methods().stream().filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey();
  }
}
