package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.CfgBlocker;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class CfgRefusalValueTest {
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED);
  @TempDir Path directory;

  @Test
  void givenRealRefusal_whenPublicValuesAndApplicationRetainIt_thenOrderedCausesRemainObservable() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var fixture = fixture(profile);
      var refused = fixture.outcome().methods().getFirst();
      assertEquals(AstCompletion.COMPLETE, fixture.ast().completion());
      assertEquals(MethodCfgState.UNSUPPORTED, refused.state());
      assertEquals(2, refused.blockers().size());
      assertEquals(List.of(MethodCfgState.UNSUPPORTED, MethodCfgState.UNSUPPORTED),
          refused.blockers().stream().map(CfgBlocker::kind).toList());
      assertEquals(refused.blockers().stream().map(CfgBlocker::diagnostic).toList(), refused.diagnostics());
      assertTrue(refused.graph().isEmpty());
      var copy = copy(refused, refused.state(), refused.diagnostics());
      var copiedOutcome = new CfgOutcome(fixture.outcome().request(), CfgState.PARTIAL, List.of(copy), copy.diagnostics());
      var run = new SourceAnalysisRunner(ignored -> {
        throw new AssertionError("Existing source derivation must not acquire a producer");
      }, (ast, request) -> copiedOutcome).deriveExisting(AstOutcome.available(fixture.ast()), copiedOutcome.request());
      assertSame(fixture.ast(), run.representation().ast().result().orElseThrow());
      assertEquals(refused.diagnostics(), run.diagnostics().values());
      assertThrows(UnsupportedOperationException.class, () -> copy.blockers().clear());
      assertThrows(UnsupportedOperationException.class, () -> copy.diagnostics().clear());
    }));
  }

  @Test
  void givenRealRefusal_whenMethodSummaryContradictsBlockers_thenConstructionRejectsIt() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var refused = fixture(profile).outcome().methods().getFirst();
      assertAll(List.of(MethodCfgState.SOURCE_UNAVAILABLE, MethodCfgState.ERRONEOUS_EVIDENCE, MethodCfgState.INSUFFICIENT_EVIDENCE)
          .stream().map(state -> (Executable) () -> assertThrows(IllegalArgumentException.class,
              () -> copy(refused, state, refused.diagnostics()))));
    }));
  }

  @Test
  void givenRealRefusal_whenAdmissionSummaryContradictsBlockers_thenConstructionRejectsIt() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var fixture = fixture(profile);
      var admission = CfgAdmission.assess(fixture.ast(), fixture.outcome().request().methodKeys().getFirst(),
          fixture.outcome().request().profile());
      assertAll(List.of(MethodCfgState.SOURCE_UNAVAILABLE, MethodCfgState.ERRONEOUS_EVIDENCE, MethodCfgState.INSUFFICIENT_EVIDENCE)
          .stream().map(state -> (Executable) () -> assertThrows(IllegalArgumentException.class,
              () -> new CfgAdmissionResult(state, admission.body(), Optional.empty(), admission.blockers()))));
    }));
  }

  @Test
  void givenRealRefusal_whenDiagnosticsAreOmitted_thenConstructionRejectsIt() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var refused = fixture(profile).outcome().methods().getFirst();
      assertThrows(IllegalArgumentException.class, () -> copy(refused, refused.state(), List.of()));
    }));
  }

  @Test
  void givenRealRefusal_whenDiagnosticsAreReordered_thenConstructionRejectsIt() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var refused = fixture(profile).outcome().methods().getFirst();
      var reordered = refused.diagnostics().reversed();
      assertFalse(reordered.equals(refused.diagnostics()));
      assertThrows(IllegalArgumentException.class, () -> copy(refused, refused.state(), reordered));
    }));
  }

  @Test
  void givenRealRefusal_whenDiagnosticsGainAnExtraOccurrence_thenConstructionRejectsIt() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var refused = fixture(profile).outcome().methods().getFirst();
      var additional = new ArrayList<>(refused.diagnostics());
      additional.add(refused.diagnostics().getFirst());
      assertThrows(IllegalArgumentException.class, () -> copy(refused, refused.state(), additional));
    }));
  }

  private static MethodCfgOutcome copy(MethodCfgOutcome original, MethodCfgState state, List<AnalysisDiagnostic> diagnostics) {
    return new MethodCfgOutcome(original.astIdentity(), original.methodKey(), state, original.body(), original.graph(), original.blockers(),
        original.failure(), diagnostics);
  }

  private Fixture fixture(AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Refused.java"), """
        class Refused {
          int run(int input) {
            int first = switch (input) { case 1 -> 2; default -> 3; };
            return switch (first) { case 2 -> 4; default -> 5; };
          }
        }
        """)));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
    var method = ast.program().methods().stream().filter(value -> value.name().equals("run")).findFirst().orElseThrow();
    var request = new CfgRequest(ast.identity(), List.of(method.methodKey()), CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
    return new Fixture(ast, CfgDerivers.standard().derive(ast, request));
  }

  private record Fixture(AstResult ast, CfgOutcome outcome) {
  }
}
