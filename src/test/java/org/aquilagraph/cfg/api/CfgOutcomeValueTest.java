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
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
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

class CfgOutcomeValueTest {
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED);
  @TempDir Path directory;

  @Test
  void givenRepeatedRealBodylessOutcome_whenClaimingComplete_thenPublicConstructionRejectsDuplicateSelection() {
    verifyDuplicateRejection(0, CfgState.COMPLETE);
  }

  @Test
  void givenRepeatedRealGraphOutcome_whenClaimingComplete_thenPublicConstructionRejectsDuplicateSelection() {
    verifyDuplicateRejection(1, CfgState.COMPLETE);
  }

  @Test
  void givenRepeatedRealRefusalOutcome_whenClaimingPartial_thenPublicConstructionRejectsDuplicateSelection() {
    verifyDuplicateRejection(2, CfgState.PARTIAL);
  }

  @Test
  void givenDuplicateSelections_whenStandardDerivationRejectsThem_thenPublicValueRetainsTheExactInvalidRequest() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var fixture = fixture(profile);
      for (var method : fixture.outcome().methods()) {
        var request = new CfgRequest(fixture.ast().identity(), List.of(method.methodKey(), method.methodKey()),
            fixture.outcome().request().profile());
        var rejected = CfgDerivers.standard().derive(fixture.ast(), request);
        assertSame(request, rejected.request());
        assertEquals(CfgState.REJECTED, rejected.state());
        assertTrue(rejected.methods().isEmpty());
        assertEquals(List.of("control-flow.duplicate-selection"), rejected.diagnostics().stream().map(value -> value.code()).toList());
        var copy = new CfgOutcome(request, CfgState.REJECTED, rejected.methods(), rejected.diagnostics());
        assertSame(request, copy.request());
        assertEquals(List.of(method.methodKey(), method.methodKey()), copy.request().methodKeys());
        assertEquals(rejected.diagnostics(), copy.diagnostics());
      }
    }));
  }

  @Test
  void givenDistinctRealBodylessGraphAndRefusalResults_whenConstructingPartialOutcome_thenExactSelectionAndDiagnosticsAreRetained() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var fixture = fixture(profile);
      var original = fixture.outcome();
      assertEquals(AstCompletion.COMPLETE, fixture.ast().completion());
      assertEquals(List.of(MethodCfgState.NOT_APPLICABLE, MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED),
          original.methods().stream().map(MethodCfgOutcome::state).toList());
      var copy = new CfgOutcome(original.request(), CfgState.PARTIAL, original.methods(), original.diagnostics());
      assertSame(original.request(), copy.request());
      assertEquals(original.methods(), copy.methods());
      assertEquals(original.diagnostics(), copy.diagnostics());
    }));
  }

  @Test
  void givenEmptyValidSelection_whenConstructingCompleteOutcome_thenNoMethodOrDiagnosticIsInvented() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var fixture = fixture(profile);
      var request = new CfgRequest(fixture.ast().identity(), List.of(), fixture.outcome().request().profile());
      var outcome = new CfgOutcome(request, CfgState.COMPLETE, List.of(), List.of());
      assertSame(request, outcome.request());
      assertTrue(outcome.methods().isEmpty());
      assertTrue(outcome.diagnostics().isEmpty());
    }));
  }

  private void verifyDuplicateRejection(int index, CfgState state) {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var fixture = fixture(profile);
      var method = fixture.outcome().methods().get(index);
      var request = new CfgRequest(fixture.ast().identity(), List.of(method.methodKey(), method.methodKey()),
          fixture.outcome().request().profile());
      var methods = List.of(method, method);
      var diagnostics = methods.stream().flatMap(value -> value.diagnostics().stream()).toList();
      assertThrows(IllegalArgumentException.class, () -> new CfgOutcome(request, state, methods, diagnostics));
    }));
  }

  private Fixture fixture(AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Selected.java"), """
        interface Selected {
          int bodyless();
          default int available() { return 7; }
          default int refused(int input) { return switch (input) { case 1 -> 2; default -> 3; }; }
        }
        """)));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
    var keys = List.of("bodyless", "available", "refused").stream().map(name -> ast.program().methods().stream()
        .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
    var request = new CfgRequest(ast.identity(), keys, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
    return new Fixture(ast, CfgDerivers.standard().derive(ast, request));
  }

  private record Fixture(AstResult ast, CfgOutcome outcome) {
  }
}
