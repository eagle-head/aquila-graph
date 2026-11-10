package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.aquilagraph.application.PublicationEligibility.Impediment;
import org.aquilagraph.application.PublicationSubjectIdentity.CompletedRun;
import org.aquilagraph.application.port.AnalysisRunRepository;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.junit.jupiter.api.Test;

class PublicationEligibilityTest {
  @Test
  void givenCompleteSourcesAndDifferentSelections_whenPublishing_thenAssessmentAndAdmissionRequireExactBodyCoverage() {
    // Arrange
    var bodies = TestAnalysisRuns.fromSource("interface Example { static int first() { return 1; } static int second() { return 2; } }")
        .representation().ast();
    var keys = bodies.result().orElseThrow().program().methods().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).toList();
    var bodyless = TestAnalysisRuns.fromSource("interface Example { int value(); }").representation().ast();
    var cases = List.of(
        new SelectionCase(derive(bodies, keys, CfgProfile.JAVA_BOUNDED), List.of()),
        new SelectionCase(sourceOnly(bodies), List.of(Impediment.CFG_NOT_COMPLETE)),
        new SelectionCase(derive(bodies, List.of(), CfgProfile.JAVA_BOUNDED), List.of(Impediment.BODY_COVERAGE_INCOMPLETE)),
        new SelectionCase(derive(bodies, keys.subList(0, 1), CfgProfile.JAVA_BOUNDED), List.of(Impediment.BODY_COVERAGE_INCOMPLETE)),
        new SelectionCase(derive(bodyless, List.of(), CfgProfile.JAVA_BOUNDED), List.of()),
        new SelectionCase(sourceOnly(bodyless), List.of(Impediment.CFG_NOT_COMPLETE)));
    for (var value : cases) {
      var run = value.run();
      var saved = new ArrayList<AnalysisRun>();
      var acquisitions = new AtomicInteger();
      var closures = new AtomicInteger();

      // Act
      var eligibility = run.publicationEligibility();
      var execution = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETED,
          AnalysisExecutionFixture::unexpectedAstRepository, context -> {
            acquisitions.incrementAndGet();
            return new AnalysisRunRepository() {
              @Override
              public void save(AnalysisRun retained) {
                saved.add(retained);
              }
              @Override
              public void close() {
                closures.incrementAndGet();
              }
            };
          });
      var publication = execution.publication();

      // Assert
      var expectedEligible = value.impediments().isEmpty();
      assertEquals(AnalysisCompletion.COMPLETE, run.completion());
      assertEquals(value.impediments(), eligibility.impediments());
      assertEquals(expectedEligible, eligibility.eligible());
      assertEquals(expectedEligible, run.isPublishable());
      assertEquals(expectedEligible ? PublicationStatus.SAVED : PublicationStatus.NOT_ELIGIBLE, publication.status());
      assertEquals(expectedEligible ? CommitKnowledge.KNOWN_COMMIT : CommitKnowledge.NOT_STARTED, publication.knowledge());
      assertEquals(Optional.of(new CompletedRun(run.identity().key())), publication.subjectIdentity());
      assertTrue(publication.failure().isEmpty());
      assertEquals(expectedEligible ? List.of(run) : List.of(), saved);
      assertEquals(expectedEligible ? 1 : 0, acquisitions.get());
      assertEquals(expectedEligible ? 1 : 0, closures.get());
      assertSame(run, new AnalysisExecution(run, publication).run());
      var contradiction = new PublicationOutcome(PublicationIntent.SAVE_COMPLETED, publication.subjectIdentity(),
          expectedEligible ? PublicationStatus.NOT_ELIGIBLE : PublicationStatus.SAVED,
          expectedEligible ? CommitKnowledge.NOT_STARTED : CommitKnowledge.KNOWN_COMMIT, Optional.empty());
      assertThrows(IllegalArgumentException.class, () -> new AnalysisExecution(run, contradiction));
    }
  }

  @Test
  void givenUnavailableSourceWithAnError_whenAssessingAndPublishing_thenAllIndependentReasonsRemainAndDependentCoverageIsAbsent() {
    // Arrange
    var original = TestAnalysisRuns.rejected();
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(error()));

    // Act
    var assessment = run.publicationEligibility();
    var publication = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETED,
        AnalysisExecutionFixture::unexpectedAstRepository, AnalysisExecutionFixture::unexpectedRunRepository).publication();

    // Assert
    assertTrue(run.representation().ast().result().isEmpty());
    assertTrue(run.representation().cfg().isEmpty());
    assertEquals(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.SOURCE_UNAVAILABLE, Impediment.CFG_NOT_COMPLETE,
        Impediment.ERROR_DIAGNOSTICS), assessment.impediments());
    assertFalse(assessment.eligible());
    assertFalse(run.isPublishable());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
    assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
    assertTrue(publication.failure().isEmpty());
  }

  @Test
  void givenIncompleteSourceWithCompleteEmptyCfgAndAnError_whenAssessing_thenCoverageReasonsFollowIndependentReasons() {
    // Arrange
    var ast = TestAnalysisRuns.fromSource("class Example { Absent field; int value() { return 1; } }").representation().ast();
    var original = derive(ast, List.of(), CfgProfile.JAVA_BOUNDED);
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(error()));

    // Act
    var assessment = run.publicationEligibility();

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertEquals(CfgState.COMPLETE, run.representation().cfg().orElseThrow().state());
    assertTrue(ast.result().orElseThrow().coverage().observations().stream().anyMatch(value -> value.classification().blocksCompletion()));
    assertEquals(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.ERROR_DIAGNOSTICS, Impediment.BODY_COVERAGE_INCOMPLETE,
        Impediment.SOURCE_COVERAGE_BLOCKED), assessment.impediments());
    assertFalse(assessment.eligible());
    assertFalse(run.isPublishable());
  }

  @Test
  void givenCompleteSourceAndRefusedCfgWithAnError_whenAssessing_thenGraphCoverageIsNotFabricated() {
    // Arrange
    var ast = TestAnalysisRuns.fromSource("interface Example { static int value(int input) { return switch (input) { case 1 -> 2; default -> 3; }; } }")
        .representation().ast();
    var keys = ast.result().orElseThrow().program().methods().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).toList();
    var original = derive(ast, keys, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS);
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(error()));

    // Act
    var assessment = run.publicationEligibility();

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion());
    assertEquals(CfgState.PARTIAL, run.representation().cfg().orElseThrow().state());
    assertEquals(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.CFG_NOT_COMPLETE, Impediment.ERROR_DIAGNOSTICS), assessment.impediments());
    assertFalse(assessment.eligible());
    assertFalse(run.isPublishable());
  }

  @Test
  void givenExactCoverageAndApplicationErrors_whenAssessing_thenOnlyCompletionAndDiagnosticsPreventPublication() {
    // Arrange
    var original = TestAnalysisRuns.fromSource("interface Example { static int value() { return 1; } }");
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(error()));

    // Act
    var assessment = run.publicationEligibility();

    // Assert
    assertEquals(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.ERROR_DIAGNOSTICS), assessment.impediments());
    assertFalse(assessment.eligible());
    assertFalse(run.isPublishable());
  }

  @Test
  void givenEligibleAndIneligibleRuns_whenPublicationIsNotRequested_thenNoRepositoryIsAcquiredOrRefusalReturned() {
    // Arrange
    var eligible = TestAnalysisRuns.completed();
    var ineligible = TestAnalysisRuns.rejected();
    for (var run : List.of(eligible, ineligible)) {
      // Act
      var execution = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.DO_NOT_SAVE,
          AnalysisExecutionFixture::unexpectedAstRepository, AnalysisExecutionFixture::unexpectedRunRepository);

      // Assert
      assertSame(run, execution.run());
      assertEquals(PublicationStatus.NOT_REQUESTED, execution.publication().status());
      assertEquals(CommitKnowledge.NOT_STARTED, execution.publication().knowledge());
      assertTrue(execution.publication().failure().isEmpty());
    }
  }

  @Test
  void givenMutableRepeatedUnorderedImpediments_whenConstructingAssessment_thenCanonicalSnapshotIsImmutable() {
    // Arrange
    var supplied = new ArrayList<>(List.of(Impediment.SOURCE_COVERAGE_BLOCKED, Impediment.CFG_NOT_COMPLETE,
        Impediment.RUN_NOT_COMPLETE, Impediment.CFG_NOT_COMPLETE));

    // Act
    var assessment = new PublicationEligibility(supplied);
    supplied.clear();

    // Assert
    assertEquals(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.CFG_NOT_COMPLETE, Impediment.SOURCE_COVERAGE_BLOCKED),
        assessment.impediments());
    assertFalse(assessment.eligible());
    assertThrows(UnsupportedOperationException.class, () -> assessment.impediments().clear());
    assertEquals(new PublicationEligibility(List.of(Impediment.RUN_NOT_COMPLETE, Impediment.CFG_NOT_COMPLETE,
        Impediment.SOURCE_COVERAGE_BLOCKED)), assessment);
  }

  @Test
  void givenNoImpediments_whenConstructingAssessment_thenItIsEligible() {
    // Arrange
    var impediments = List.<Impediment>of();

    // Act
    var assessment = new PublicationEligibility(impediments);

    // Assert
    assertTrue(assessment.eligible());
    assertEquals(List.of(), assessment.impediments());
  }

  @Test
  void givenNullListOrMember_whenConstructingAssessment_thenInvalidInputsAreRejected() {
    // Arrange
    var member = new ArrayList<Impediment>();
    member.add(null);

    // Act
    var nullList = assertThrows(NullPointerException.class, () -> new PublicationEligibility(null));
    var nullMember = assertThrows(NullPointerException.class, () -> new PublicationEligibility(member));

    // Assert
    assertEquals(NullPointerException.class, nullList.getClass());
    assertEquals(NullPointerException.class, nullMember.getClass());
  }

  private static AnalysisRun derive(AstOutcome ast, List<String> keys, CfgProfile profile) {
    return AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard())
        .deriveExisting(ast, new CfgRequest(ast.result().orElseThrow().identity(), keys, profile));
  }

  private static AnalysisRun sourceOnly(AstOutcome ast) {
    var request = new AstProductionRequest(ast.capturedInput().orElseThrow(), ast.requestedConfiguration().orElseThrow(), ast.profile());
    return AnalysisCapabilities.computation(ignored -> ast, (source, selection) -> {
      throw new AssertionError("An AST-only request must not derive graphs");
    }).analyze(new AnalysisComputationRequest(request, Optional.empty()));
  }

  private static AnalysisDiagnostic error() {
    return new AnalysisDiagnostic("application:eligibility", "application.observation", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, "Supplied application error", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }

  private record SelectionCase(AnalysisRun run, List<Impediment> impediments) {}
}
