package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.source.api.AstOutcome;
import org.junit.jupiter.api.Test;

class ProgramRepresentationTest {
  @Test
  void givenCanonicalSourceAndGraphs_whenRepresentationIsCreated_thenExactIndependentOwnersAreRetained() {
    // Arrange
    var run = sample();
    var ast = run.representation().ast();
    var cfg = run.representation().cfg();
    // Act
    var representation = new ProgramRepresentation(ast, cfg);
    // Assert
    assertSame(ast, representation.ast());
    assertSame(cfg.orElseThrow(), representation.cfg().orElseThrow());
    assertEquals(ast.result().orElseThrow().program().methods().getFirst().methodKey(), cfg.orElseThrow().methods().getFirst().methodKey());
  }

  @Test
  void givenChangedMethodUnderAnotherSourceIdentity_whenRepresentationIsCreated_thenForeignGraphOwnershipIsRejected() {
    // Arrange
    var first = sample();
    var changed = TestAnalysisRuns.fromSource("interface Example { static int first() { return 2; } }");
    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new ProgramRepresentation(changed.representation().ast(), first.representation().cfg()));
    // Assert
    assertTrue(failure.getMessage().contains("exact established AST"));
  }

  @Test
  void givenDuplicateSelectedMethod_whenDerivationIsRequested_thenDuplicateOutcomeCannotBePublishedAsComplete() {
    // Arrange
    var ast = sample().representation().ast();
    var key = ast.result().orElseThrow().program().methods().getFirst().methodKey();
    // Act
    var cfg = CfgDerivers.standard().derive(ast.result().orElseThrow(),
        new CfgRequest(ast.result().orElseThrow().identity(), List.of(key, key), CfgProfile.JAVA_BOUNDED));
    var representation = new ProgramRepresentation(ast, Optional.of(cfg));
    // Assert
    assertEquals(CfgState.REJECTED, representation.cfg().orElseThrow().state());
    assertTrue(cfg.methods().isEmpty());
    assertTrue(cfg.diagnostics().stream().anyMatch(value -> value.code().equals("control-flow.duplicate-selection")));
  }

  @Test
  void givenForeignMethodSelection_whenDerivationIsRequested_thenNoCanonicalGraphIsInvented() {
    // Arrange
    var ast = sample().representation().ast();
    // Act
    var cfg = CfgDerivers.standard().derive(ast.result().orElseThrow(),
        new CfgRequest(ast.result().orElseThrow().identity(), List.of("foreign-method"), CfgProfile.JAVA_BOUNDED));
    // Assert
    assertEquals(CfgState.REJECTED, cfg.state());
    assertTrue(cfg.methods().isEmpty());
    assertTrue(cfg.diagnostics().stream().anyMatch(value -> value.code().equals("control-flow.unknown-method")));
  }

  @Test
  void givenEqualAdmittedSourceValue_whenRepresentationIsCreated_thenExactIdentityAssociationIsAccepted() {
    // Arrange
    var run = sample();
    var equal = AstOutcome.available(run.representation().ast().result().orElseThrow());
    // Act
    var representation = new ProgramRepresentation(equal, run.representation().cfg());
    // Assert
    assertSame(equal, representation.ast());
    assertSame(run.representation().cfg().orElseThrow(), representation.cfg().orElseThrow());
  }

  @Test
  void givenBodylessSelectionInsteadOfRequiredBody_whenRunIsCreated_thenSelectionCannotClaimFullPublicationCoverage() {
    // Arrange
    var run = TestAnalysisRuns.fromSource("interface Example { static int first() { return 1; } int bodyless(); }");
    var ast = run.representation().ast();
    var bodyless = ast.result().orElseThrow().program().methods().stream().filter(method -> !method.hasBody()).findFirst().orElseThrow();
    // Act
    var selected = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(ast,
        new CfgRequest(ast.result().orElseThrow().identity(), List.of(bodyless.methodKey()), CfgProfile.JAVA_BOUNDED));
    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, selected.completion());
    assertFalse(selected.isPublishable());
    assertTrue(selected.representation().cfg().orElseThrow().methods().getFirst().graph().isEmpty());
  }

  @Test
  void givenOrderedMutableOutcomeList_whenOutcomeIsComposed_thenSuppliedOrderIsFrozen() {
    // Arrange
    var run = TestAnalysisRuns.fromSource("interface Example { static int first() { return 1; } static int second() { return 2; } }");
    var original = run.representation().cfg().orElseThrow();
    var methods = new ArrayList<>(original.methods());
    // Act
    var cfg = new CfgOutcome(original.request(), original.state(), methods, original.diagnostics());
    var representation = new ProgramRepresentation(run.representation().ast(), Optional.of(cfg));
    methods.clear();
    // Assert
    assertEquals(original.methods(), representation.cfg().orElseThrow().methods());
    assertEquals(original.request().methodKeys(), cfg.methods().stream().map(value -> value.methodKey()).toList());
    assertThrows(UnsupportedOperationException.class, () -> cfg.methods().clear());
  }

  @Test
  void givenNullSourceOrOutcomePresence_whenRepresentationIsCreated_thenRequiredOwnersRejectNull() {
    // Arrange
    var ast = sample().representation().ast();
    // Act
    var absentAst = assertThrows(NullPointerException.class, () -> new ProgramRepresentation(null, Optional.empty()));
    var absentCfg = assertThrows(NullPointerException.class, () -> new ProgramRepresentation(ast, null));
    // Assert
    assertEquals("ast", absentAst.getMessage());
    assertEquals("cfg", absentCfg.getMessage());
  }

  private static AnalysisRun sample() {
    return TestAnalysisRuns.fromSource("interface Example { static int first() { return 1; } }");
  }
}
