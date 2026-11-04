package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgDerivationException;
import org.aquilagraph.cfg.api.CfgDeriver;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class SourceAnalysisFailureTest {
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED);
  @TempDir Path directory;

  @Test
  void givenAbruptCarrierForAnotherSelection_whenCrossingEitherApplicationEntry_thenPortViolationRetainsTheOriginalCause() {
    verifyMismatch(Difference.SELECTION);
  }

  @Test
  void givenAbruptCarrierForAnotherProfile_whenCrossingEitherApplicationEntry_thenPortViolationRetainsTheOriginalCause() {
    verifyMismatch(Difference.PROFILE);
  }

  @Test
  void givenAbruptCarrierForAnotherGraphModel_whenCrossingEitherApplicationEntry_thenPortViolationRetainsTheOriginalCause() {
    verifyMismatch(Difference.MODEL);
  }

  @Test
  void givenAbruptCarrierForAnotherAst_whenCrossingEitherApplicationEntry_thenPortViolationRetainsTheOriginalCause() {
    verifyMismatch(Difference.AST);
  }

  @Test
  void givenMatchingRealProgress_whenCrossingEitherApplicationEntry_thenSourceRequestProgressAndCauseRemainExact() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      // Arrange
      var ast = produce(profile, 7);
      var result = ast.result().orElseThrow();
      var request = new CfgRequest(result.identity(), List.of(key(ast, "first"), key(ast, "second")), CfgProfile.JAVA_BOUNDED);
      var prefix = CfgDerivers.standard().derive(result,
          new CfgRequest(result.identity(), List.of(key(ast, "first")), CfgProfile.JAVA_BOUNDED));
      assertEquals(CfgState.COMPLETE, prefix.state());
      var cause = new IllegalStateException("Construction observation");
      var carrier = new CfgDerivationException(request, prefix.methods(), cause);
      var observed = new ArrayList<CfgRequest>();
      CfgDeriver cfgDeriver = (source, invoked) -> {
        observed.add(invoked);
        throw carrier;
      };
      var runner = AnalysisCapabilities.computation(ignored -> ast, cfgDeriver);
      var derivation = AnalysisCapabilities.existingSourceDerivation(cfgDeriver);
      // Act
      var existing = assertThrows(AnalysisDerivationException.class, () -> derivation.deriveExisting(ast, request));
      var production = assertThrows(AnalysisDerivationException.class, () -> runner.analyze(productionRequest(ast, request)));
      // Assert
      for (var failure : List.of(existing, production, new AnalysisDerivationException(ast, carrier))) {
        assertSame(ast, failure.ast());
        assertSame(carrier, failure.derivationFailure());
        assertSame(cause, failure.derivationFailure().getCause());
        assertSame(request, failure.derivationFailure().request());
        assertEquals(prefix.methods(), failure.derivationFailure().completedMethods());
        assertSame(prefix.methods().getFirst().graph().orElseThrow(),
            failure.derivationFailure().completedMethods().getFirst().graph().orElseThrow());
      }
      assertEquals(List.of(request, request), observed);
      assertEquals(AstCompletion.COMPLETE, ast.completion());
    }));
  }

  @Test
  void givenCarrierForAnotherSource_whenConstructingApplicationFailureDirectly_thenForeignAssociationIsRejectedWithItsCause() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var ast = produce(profile, 7);
      var other = produce(profile, 11);
      var request = new CfgRequest(other.result().orElseThrow().identity(), List.of(key(other, "first")), CfgProfile.JAVA_BOUNDED);
      var carrier = new CfgDerivationException(request, List.of(), new IllegalStateException("Construction observation"));
      var failure = assertThrows(IllegalArgumentException.class, () -> new AnalysisDerivationException(ast, carrier));
      assertSame(carrier, failure.getCause());
    }));
  }

  @Test
  void givenUnavailableSource_whenConstructingApplicationFailureDirectly_thenNoEstablishedOwnerIsInvented() {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var ast = produce(profile, 7);
      var unavailable = new JavaFrontend(new JavaSourceLoader()).produce(InputSnapshot.fromSources(directory, List.of()),
          CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
      var request = new CfgRequest(ast.result().orElseThrow().identity(), List.of(key(ast, "first")), CfgProfile.JAVA_BOUNDED);
      var carrier = new CfgDerivationException(request, List.of(), new IllegalStateException("Construction observation"));
      var failure = assertThrows(IllegalArgumentException.class, () -> new AnalysisDerivationException(unavailable, carrier));
      assertSame(carrier, failure.getCause());
    }));
  }

  private void verifyMismatch(Difference difference) {
    assertAll(PROFILES.stream().map(profile -> (Executable) () -> {
      var ast = produce(profile, 7);
      var request = new CfgRequest(ast.result().orElseThrow().identity(), List.of(key(ast, "first")), CfgProfile.JAVA_BOUNDED);
      var other = produce(profile, 11);
      var unexpected = switch (difference) {
        case SELECTION -> new CfgRequest(request.astIdentity(), List.of(key(ast, "second")), request.profile());
        case PROFILE -> new CfgRequest(request.astIdentity(), request.methodKeys(), CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
        case MODEL -> new CfgRequest(request.astIdentity(), request.methodKeys(), new CfgProfile(request.profile().version(), "java-cfg/unsupported"));
        case AST -> new CfgRequest(other.result().orElseThrow().identity(), List.of(key(other, "first")), request.profile());
      };
      var cause = new IllegalStateException("Construction observation");
      var carrier = new CfgDerivationException(unexpected, List.of(), cause);
      var observed = new ArrayList<CfgRequest>();
      CfgDeriver cfgDeriver = (source, invoked) -> {
        observed.add(invoked);
        throw carrier;
      };
      var runner = AnalysisCapabilities.computation(ignored -> ast, cfgDeriver);
      var derivation = AnalysisCapabilities.existingSourceDerivation(cfgDeriver);
      assertAll(
          () -> assertViolation(carrier, cause, () -> derivation.deriveExisting(ast, request)),
          () -> assertViolation(carrier, cause, () -> runner.analyze(productionRequest(ast, request))));
      assertEquals(List.of(request, request), observed);
      assertEquals(AstCompletion.COMPLETE, ast.completion());
    }));
  }

  private static void assertViolation(CfgDerivationException carrier, Throwable cause, Executable action) {
    var failure = assertThrows(IllegalStateException.class, action);
    assertEquals("CFG port returned a different requested identity or selection", failure.getMessage());
    assertSame(carrier, failure.getCause());
    assertSame(cause, carrier.getCause());
  }

  private AstOutcome produce(AstProfile profile, int literal) throws IOException {
    var input = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Failure.java"),
        "interface Failure { static int first() { return " + literal + "; } static int second() { return 9; } }")));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(input, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    return ast;
  }

  private static AnalysisComputationRequest productionRequest(AstOutcome ast, CfgRequest request) {
    var source = new AstProductionRequest(ast.capturedInput().orElseThrow(), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), ast.profile());
    return new AnalysisComputationRequest(source, Optional.of(new CfgSelection(request.methodKeys(), request.profile())));
  }

  private static String key(AstOutcome ast, String name) {
    return ast.result().orElseThrow().program().methods().stream().filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey();
  }

  private enum Difference {
    SELECTION, PROFILE, MODEL, AST
  }
}
