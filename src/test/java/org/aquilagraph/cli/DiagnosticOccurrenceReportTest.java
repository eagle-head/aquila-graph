package org.aquilagraph.cli;

import static org.aquilagraph.cli.NativeReportAssertions.assertNativeReport;
import static org.aquilagraph.cli.NativeReportAssertions.qualificationBlocks;
import static org.aquilagraph.cli.NativeReportAssertions.relatedFromOwner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class DiagnosticOccurrenceReportTest {
  @Test
  void givenWarningSharingTheUnsupportedSourceProtocol_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var original = CliTestFixtures.analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED)
        .representation().ast().result().orElseThrow();
    var error = original.diagnostics().getFirst();
    var warning = new AnalysisDiagnostic("source:warning", error.code(), DiagnosticStage.FRONTEND, DiagnosticSeverity.WARNING,
        error.message(), error.origins(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var evidence = original.coverage().validationEvidence();
    var emissions = new ArrayList<DiagnosticEmission>();
    emissions.add(new DiagnosticEmission(new DiagnosticEmission.Address("source-warning", "source-input", 0), warning,
        warning.outcomeKind(), List.of(), 0));
    evidence.diagnosticEmissions().forEach(event -> emissions.add(event.atSlot(emissions.size())));
    var diagnostics = emissions.stream().map(DiagnosticEmission::diagnostic).toList();
    var coverage = CoverageAdmission.fromEvidence(original.coverage().coverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), emissions);
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
        Optional.of(original.aggregate()), diagnostics, coverage);
    var ast = AstResultAdmission.admit(input, original.profile(), "copied-source/1");
    var run = CliTestFixtures.analyze(ast);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);

    // Assert
    assertNativeReport(run, runStreams);
    assertEquals(3, run.diagnostics().values().size());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
  }

  @Test
  void givenAssignmentRefusalUsingAProviderSpecificSlot_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var original = CliTestFixtures.analyze("class Example { int subject(int value) { (value) = 1; return value; } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var ast = original.representation().ast().result().orElseThrow();
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var assignment = ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ASSIGNMENT).findFirst().orElseThrow();
    var diagnostic = new AnalysisDiagnostic("cfg:assignment-target", "control-flow.assignment-target", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "Parenthesized assignment targets are outside the selected CFG profile",
        assignment.origin().stream().toList(), AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var blocker = new CfgBlocker(MethodCfgState.UNSUPPORTED, ast.identity(), method.methodKey(), ast.occurrence(assignment),
        "assignment.target", diagnostic);
    var run = CliTestFixtures.withRefusal(ast, blocker);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);

    // Assert
    var rendered = assertNativeReport(run, runStreams);
    var span = assignment.origin().orElseThrow();
    assertEquals(List.of(List.of("Qualification: CFG UNSUPPORTED", "  Profile: \"java-cfg-bounded/1\"",
        "  Subject: \"" + method.methodKey() + "\"", "  AST result: \"" + ast.identity().astResultKey() + "\"",
        "  Source occurrence: \"" + ast.occurrence(assignment).orElseThrow().nodeKey() + "\"", "  Slot: \"assignment.target\"",
        "  Origin: \"" + span.sourceFileKey() + "\" file=\"Example.java\" range=[" + span.startOffset() + "," + span.endOffsetExclusive() + ")",
        "  Related diagnostic: \"cfg:assignment-target\" occurrence=1")), qualificationBlocks(rendered, "CFG"));
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertEquals(AstCompletion.COMPLETE, ast.completion());
  }

  @Test
  void givenInputNoticeBeforeUnsupportedSourceDiagnostic_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var original = CliTestFixtures.analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED)
        .representation().ast().result().orElseThrow();
    var note = new AnalysisDiagnostic("input:notice", "input.notice", DiagnosticStage.INPUT, DiagnosticSeverity.INFO,
        "Supplied input notice", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var evidence = original.coverage().validationEvidence();
    var emissions = new ArrayList<DiagnosticEmission>();
    emissions.add(new DiagnosticEmission(new DiagnosticEmission.Address("input-notice", "source-input", 0), note,
        note.outcomeKind(), List.of(), 0));
    evidence.diagnosticEmissions().forEach(event -> emissions.add(event.atSlot(emissions.size())));
    var diagnostics = emissions.stream().map(DiagnosticEmission::diagnostic).toList();
    var coverage = CoverageAdmission.fromEvidence(original.coverage().coverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), emissions);
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
        Optional.of(original.aggregate()), diagnostics, coverage);
    var ast = AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
    var run = CliTestFixtures.analyze(ast);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);

    // Assert
    var rendered = assertNativeReport(run, runStreams);
    var related = relatedFromOwner(rendered, "SOURCE");
    assertFalse(related.isEmpty());
    assertTrue(related.stream().allMatch(line -> line.equals("  Related diagnostic: \"" + original.diagnostics().getFirst().diagnosticKey() + "\" occurrence=2")));
    assertEquals(diagnostics, ast.diagnostics());
    assertEquals(3, run.diagnostics().values().size());
  }

  @TestFactory
  Stream<DynamicTest> givenSuppliedSourceDiagnosticsOutsideTheBoundedProtocol_whenPresented_thenSourceAndGraphCausesRemainDistinct() {
    return Stream.of("Message", "Code", "Originless")
        .map(variant -> DynamicTest.dynamicTest("givenAlternative" + variant + "SourceDiagnostic_whenPresented_thenBothCausesRemain", () -> {
          // Arrange
          var original = CliTestFixtures.analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED)
              .representation().ast().result().orElseThrow();
          var sourceDiagnostic = original.diagnostics().getFirst();
          var replacement = new AnalysisDiagnostic(sourceDiagnostic.diagnosticKey(),
              variant.equals("Code") ? "source.unavailable-form" : sourceDiagnostic.code(), sourceDiagnostic.stage(),
              sourceDiagnostic.severity(), variant.equals("Message") ? "The source producer cannot represent this declaration"
                  : sourceDiagnostic.message(),
              variant.equals("Originless") ? List.of() : sourceDiagnostic.origins(), sourceDiagnostic.outcomeKind());
          var evidence = original.coverage().validationEvidence();
          var emissions = evidence.diagnosticEmissions().stream().map(event -> event.diagnostic().equals(sourceDiagnostic)
              ? new DiagnosticEmission(event.address(), replacement, replacement.outcomeKind(), event.contexts(), event.slot())
              : event).toList();
          var coverage = CoverageAdmission.fromEvidence(original.coverage().coverage(), evidence.writtenInventory(),
              evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), emissions);
          var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
              Optional.of(original.aggregate()), List.of(replacement), coverage);
          var ast = AstResultAdmission.admit(input, original.profile(), "copied-source/1");
          var run = CliTestFixtures.analyze(ast);

          var runStreams = new CliStreams();

          // Act
          CliTestFixtures.present(run, runStreams);

          // Assert
          var rendered = assertNativeReport(run, runStreams);
          assertTrue(rendered.contains("  Message: \"" + replacement.message() + "\""));
          assertEquals(List.of(replacement), ast.diagnostics());
          assertEquals(List.of(sourceDiagnostic), original.diagnostics());
          assertEquals(AnalysisCompletion.PARTIAL, run.completion());
        }));
  }

  @Test
  void givenRepeatedCfgCauseForOneSourceOccurrence_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var original = CliTestFixtures.analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var ast = original.representation().ast();
    var originalCfg = original.representation().cfg().orElseThrow();
    var refused = originalCfg.methods().stream().filter(method -> !method.blockers().isEmpty()).findFirst().orElseThrow();
    var blocker = refused.blockers().getFirst();
    var repeated = new MethodCfgOutcome(refused.astIdentity(), refused.methodKey(), refused.state(), refused.body(), refused.graph(),
        List.of(blocker, blocker), refused.failure(), List.of(blocker.diagnostic(), blocker.diagnostic()));
    var methods = originalCfg.methods().stream().map(method -> method.equals(refused) ? repeated : method).toList();
    var cfg = new CfgOutcome(originalCfg.request(), originalCfg.state(), methods,
        methods.stream().flatMap(method -> method.diagnostics().stream()).toList());
    var runner = AnalysisCapabilities.existingSourceDerivation((source, request) -> cfg);
    var run = runner.deriveExisting(ast, cfg.request());

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);
    var catalog = run.diagnostics();
    var firstBlockerOccurrence = catalog.forBlocker(repeated, 0);
    var secondBlockerOccurrence = catalog.forBlocker(repeated, 1);

    // Assert
    var rendered = assertNativeReport(run, runStreams);
    assertEquals(3, run.diagnostics().values().size());
    assertEquals(List.of(blocker.diagnostic(), blocker.diagnostic()), cfg.diagnostics());
    assertSame(catalog.occurrences().get(1), firstBlockerOccurrence);
    assertSame(catalog.occurrences().get(2), secondBlockerOccurrence);
    assertNotSame(firstBlockerOccurrence, secondBlockerOccurrence);
    assertEquals(List.of("  Related diagnostic: \"" + blocker.diagnostic().diagnosticKey() + "\" occurrence=2",
        "  Related diagnostic: \"" + blocker.diagnostic().diagnosticKey() + "\" occurrence=3"), relatedFromOwner(rendered, "CFG"));
    assertSame(ast, run.representation().ast());
  }

  @TestFactory
  Stream<DynamicTest> givenProducerNotesAtDifferentSourceLocations_whenPresented_thenBoundedRefusalKeepsItsWrittenPosition() {
    return Stream.of("Originless", "Input", "Assignment", "Target", "TargetStart", "Body", "BeforeBody", "BeyondBody", "ForeignAndTarget")
        .map(location -> DynamicTest.dynamicTest("given" + location + "ProducerNote_whenPresented_thenWrittenDiagnosticOrderIsPreserved", () -> {
          // Arrange
          var companion = SourceFile.fromContent(Path.of("Context.java"), "class Context {}");
          var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"),
              "class Example { int subject(int value) { (value) = 1; return value; } }"), companion));
          var original = CliTestFixtures.produce(snapshot, AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
          var method = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
          var body = original.body(method.methodKey()).orElseThrow().root();
          var assignment = body.descendantsAndSelf().stream().filter(value -> value.syntaxKind() == SyntaxKind.ASSIGNMENT)
              .findFirst().orElseThrow();
          var target = assignment.requiredChild(SyntaxRole.LOCATION).origin().orElseThrow();
          var origin = switch (location) {
            case "Originless", "Input" -> Optional.<SourceSpan>empty();
            case "Assignment" -> assignment.origin();
            case "Body" -> body.origin();
            case "BeforeBody" -> Optional.of(new SourceSpan(target.sourceFileKey(), body.origin().orElseThrow().startOffset() - 1,
                target.endOffsetExclusive()));
            case "BeyondBody" -> Optional.of(new SourceSpan(target.sourceFileKey(), target.startOffset(),
                body.origin().orElseThrow().endOffsetExclusive() + 1));
            case "TargetStart" -> Optional.of(new SourceSpan(target.sourceFileKey(), target.startOffset(), target.startOffset() + 1));
            case "Target", "ForeignAndTarget" -> Optional.of(target);
            default -> throw new IllegalArgumentException("Unknown source diagnostic location");
          };
          var origins = location.equals("ForeignAndTarget")
              ? List.of(new SourceSpan(companion.sourceFileKey(), 0, "class Context {}".length()), target)
              : origin.stream().toList();
          var stage = location.equals("Input") ? DiagnosticStage.INPUT : DiagnosticStage.FRONTEND;
          var diagnostic = new AnalysisDiagnostic("source:note", "source.note", stage, DiagnosticSeverity.INFO,
              "Supplied producer note", origins, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
          var evidence = original.coverage().validationEvidence();
          var emissions = new ArrayList<>(evidence.diagnosticEmissions());
          emissions.add(new DiagnosticEmission(new DiagnosticEmission.Address("source-note", "source-unit", 0), diagnostic,
              diagnostic.outcomeKind(), List.of(), emissions.size()));
          var diagnostics = new ArrayList<>(original.diagnostics());
          diagnostics.add(diagnostic);
          var coverage = CoverageAdmission.fromEvidence(original.coverage().coverage(), evidence.writtenInventory(),
              evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), emissions);
          var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
              Optional.of(original.aggregate()), diagnostics, coverage);
          var ast = AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
          var run = CliTestFixtures.analyze(ast);

          var runStreams = new CliStreams();

          // Act
          CliTestFixtures.present(run, runStreams);
          var summary = AnalysisRunSummary.from(run);

          // Assert
          assertNativeReport(run, runStreams);
          assertEquals(AstCompletion.COMPLETE, ast.completion());
          assertEquals(List.of(diagnostic), ast.diagnostics());
          assertEquals(AnalysisCompletion.PARTIAL, summary.run().completion());
        }));
  }

  @Test
  void givenInterleavedSourceAndGraphRefusalsInSeveralBodies_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var run = CliTestFixtures.analyze("""
        class Example {
          int first(int value) { (value) = 1; class First {} return value; }
          int second(int value) { class Second {} (value) = 2; class Third {} return value; }
        }
        """, AstProfile.JAVA_SOURCE_BOUNDED);
    var original = run.diagnostics().values();

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);

    // Assert
    assertNativeReport(run, runStreams);
    assertEquals(original, run.diagnostics().values());
  }

  @Test
  void givenRefusalsInSeveralFilesAndBodylessDeclarations_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(
        SourceFile.fromContent(Path.of("First.java"), "interface First { void bodyless(); default void first(boolean flag) { while (flag) {} } }"),
        SourceFile.fromContent(Path.of("Second.java"), "class Second { int second(int value) { (value) = 1; class Local {} return value; } }")));
    var ast = CliTestFixtures.produce(snapshot, AstProfile.JAVA_SOURCE_BOUNDED);
    var run = CliTestFixtures.analyze(ast);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);

    // Assert
    assertNativeReport(run, runStreams);
  }

  @Test
  void givenRepeatedApplicationSlotsEqualToGraphDiagnostic_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var original = CliTestFixtures.analyze("class Example { int value(int a) { (a) = 1; return a; } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var graphDiagnostic = original.diagnostics().values().getFirst();
    var applicationDiagnostics = List.of(graphDiagnostic, graphDiagnostic);
    var run = AnalysisRun.create(original.identity(), original.representation(), applicationDiagnostics);

    var runStreams = new CliStreams();

    // Act
    CliTestFixtures.present(run, runStreams);

    // Assert
    assertNativeReport(run, runStreams);
    assertEquals(3, run.diagnostics().values().size());
    assertEquals(List.of(graphDiagnostic, graphDiagnostic), run.applicationDiagnostics());
  }

  @Test
  void givenSourceOwnerEvidenceEqualToANativeEvent_whenPresented_thenRetainedEvidenceDoesNotAcquireAnInventedOccurrence() throws IOException {
    // Arrange
    var original = CliTestFixtures.analyze("interface Example { static int read() { return Integer.MAX_VALUE; } }", AstProfile.JAVA_SOURCE_BOUNDED)
        .representation().ast().result().orElseThrow();
    var methodKey = original.program().methods().getFirst().methodKey();
    var selected = original.body(methodKey).orElseThrow().evidence().staticMembers().getFirst();
    var error = new AnalysisDiagnostic("source:owner", "source.owner", DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR,
        "Supplied owner error", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var evidence = original.coverage().validationEvidence();
    var events = new ArrayList<>(evidence.diagnosticEmissions());
    events.add(new DiagnosticEmission(new DiagnosticEmission.Address("CopiedOwner", "owner", events.size()), error,
        error.outcomeKind(), List.of(), events.size()));
    var coverage = CoverageAdmission.fromEvidence(original.coverage().coverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), events);
    var replacement = new StaticMemberEvidence(selected.scopeKey(), selected.accessNodeId(), selected.selectedMemberKey(), selected.kind(),
        EvidenceValue.erroneous(selected.declaringOwner().origin(), "Owner erroneous", error), selected.constantVariable());
    var copied = SourceValueCapture.capture(original.aggregate());
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
        body.invocations(), body.staticAccesses().isEmpty() ? body.staticAccesses() : List.of(replacement), body.concatenations(),
        body.constructors(), body.attributedOccurrences())).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes()));
    var ast = AstResultAdmission.admit(new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
        Optional.of(aggregate), List.of(error), coverage), original.profile(), original.identity().semanticProductionRevision());
    var outcome = AstOutcome.available(ast);
    var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard())
        .deriveExisting(outcome, new CfgRequest(ast.identity(), List.of(), CfgProfile.JAVA_BOUNDED));
    var streams = new CliStreams();

    // Act
    new AnalyzeCommandPresenter().present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)),
        streams.output(), streams.error());

    // Assert
    var ownerReason = ast.qualification().reasons().stream().filter(reason -> reason.role() == EvidenceRole.STATIC_OWNER).findFirst().orElseThrow();
    assertTrue(ownerReason.diagnostics().isEmpty());
    assertEquals(error, ownerReason.evidence().getFirst().diagnostic());
    assertEquals(List.of(error), run.diagnostics().values());
    var lines = streams.errorLines();
    assertEquals(List.of("Diagnostic: \"source:owner\""), lines.stream().filter(line -> line.startsWith("Diagnostic:")).toList());
    var index = lines.indexOf("  Evidence diagnostic: \"source:owner\"");
    assertTrue(index >= 0);
    assertEquals(List.of("  Evidence diagnostic: \"source:owner\"", "    Severity: ERROR", "    Stage: FRONTEND",
        "    Cause: OTHER_ANALYSIS_ERROR", "    Code: \"source.owner\"", "    Message: \"Supplied owner error\"", "    Origins: unavailable"),
        lines.subList(index, index + 7));
    assertEquals(1, lines.stream().filter(line -> line.contains("Occurrence:")).count());
  }

  @Test
  void givenReverseMethodSelection_whenPresented_thenNativeEventsStayCanonicalAndReasonsRetainRequestOrder() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("interface Example { static int first(int x) { (x) = 1; return x; } static int second(int x) { (x) = 2; return x; } }");
    var methods = ast.result().orElseThrow().program().methods();
    var first = methods.getFirst().methodKey();
    var second = methods.getLast().methodKey();
    var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(ast, new CfgRequest(ast.result().orElseThrow().identity(), List.of(second, first), CfgProfile.JAVA_BOUNDED));
    var cfg = run.representation().cfg().orElseThrow();
    var streams = new CliStreams();
    // Act
    CliTestFixtures.present(run, streams);
    // Assert
    assertTrue(streams.outputLines().containsAll(List.of("Completion: PARTIAL", "AST: COMPLETE", "CFG: PARTIAL")));
    assertEquals(List.of(cfg.methods().getLast().diagnostics().getFirst(), cfg.methods().getFirst().diagnostics().getFirst()), run.diagnostics().values());
    assertEquals(run.diagnostics().values().stream().map(diagnostic -> "Diagnostic: \"" + diagnostic.diagnosticKey() + "\"").toList(), streams.errorLines().stream().filter(line -> line.startsWith("Diagnostic:")).toList());
    assertEquals(List.of("  Subject: \"" + second + "\"", "  Subject: \"" + first + "\"", "  Subject: \"" + second + "\"", "  Subject: \"" + first + "\""), streams.errorLines().stream().filter(line -> line.startsWith("  Subject:")).toList());
    assertEquals(List.of("  Related diagnostic: \"" + cfg.methods().getFirst().diagnostics().getFirst().diagnosticKey() + "\" occurrence=2", "  Related diagnostic: \"" + cfg.methods().getLast().diagnostics().getFirst().diagnosticKey() + "\" occurrence=1", "  Related diagnostic: \"" + cfg.methods().getFirst().diagnostics().getFirst().diagnosticKey() + "\" occurrence=2", "  Related diagnostic: \"" + cfg.methods().getLast().diagnostics().getFirst().diagnosticKey() + "\" occurrence=1"), streams.errorLines().stream().filter(line -> line.startsWith("  Related diagnostic:")).toList());
  }

  @Test
  void givenEqualAndSameKeyUnequalApplicationErrors_whenPresented_thenEveryReasonUsesItsExactNativeOccurrenceAndEscapedValue() {
    // Arrange
    var original = CliTestFixtures.analyze("interface Example { static int run(int x) { (x) = 1; return x; } }");
    var first = new AnalysisDiagnostic("shared:key", "application.test", DiagnosticStage.INPUT, DiagnosticSeverity.ERROR, "first\n\r\t\"\\\u0000\u007f", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var second = new AnalysisDiagnostic("shared:key", "application.test", DiagnosticStage.INPUT, DiagnosticSeverity.ERROR, "second", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(first, first, second));
    var streams = new CliStreams();
    // Act
    CliTestFixtures.present(run, streams);
    // Assert
    assertEquals(List.of("  Occurrence: 1", "  Occurrence: 2", "  Occurrence: 3", "  Occurrence: 4"), streams.errorLines().stream().filter(line -> line.startsWith("  Occurrence:")).toList());
    assertEquals(List.of("  Related diagnostic: \"shared:key\" occurrence=2", "  Related diagnostic: \"shared:key\" occurrence=3", "  Related diagnostic: \"shared:key\" occurrence=4"), streams.errorLines().stream().filter(line -> line.startsWith("  Related diagnostic: \"shared:key\"")).toList());
    assertEquals(List.of("  Message: \"first\\n\\r\\t\\\"\\\\\\u0000\\u007f\"", "  Message: \"first\\n\\r\\t\\\"\\\\\\u0000\\u007f\"", "  Message: \"second\""), streams.errorLines().stream().filter(line -> line.startsWith("  Message:")).skip(1).toList());
  }

  @Test
  void givenMultipleCapturedAndForeignOrigins_whenPresented_thenRangesKeysOrderAndMissingPathsRemainExact() {
    // Arrange
    var original = CliTestFixtures.completedRun();
    var captured = original.representation().ast().capturedInput().orElseThrow().sourceFiles().getFirst();
    var foreign = SourceFile.fromContent(Path.of("Outside.java"), "class Outside {}");
    var origins = List.of(new SourceSpan(captured.sourceFileKey(), 1, 3), new SourceSpan(foreign.sourceFileKey(), 2, 5), new SourceSpan(captured.sourceFileKey(), 1, 3));
    var diagnostic = new AnalysisDiagnostic("origin:notice", "application.notice", DiagnosticStage.INPUT, DiagnosticSeverity.INFO, "notice", origins, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var run = AnalysisRun.create(original.identity(), original.representation(), List.of(diagnostic));
    var streams = new CliStreams();
    // Act
    var code = CliTestFixtures.present(run, streams);
    // Assert
    assertEquals(0, code);
    assertEquals(List.of("  Origin: \"" + captured.sourceFileKey() + "\" file=\"Example.java\" range=[1,3)", "  Origin: \"" + foreign.sourceFileKey() + "\" file=unavailable range=[2,5)", "  Origin: \"" + captured.sourceFileKey() + "\" file=\"Example.java\" range=[1,3)"), streams.errorLines().stream().filter(line -> line.startsWith("  Origin:")).toList());
  }

  @Test
  void givenBlockerWithEvidenceOutsideNativeStream_whenPresented_thenEvidenceRetainsFrontendOwnershipWithoutInventedOccurrence() throws IOException {
    // Arrange
    var ast = CliTestFixtures.produce("interface Example { static void run() {} }");
    var source = ast.result().orElseThrow();
    var key = source.program().methods().getFirst().methodKey();
    var primary = new AnalysisDiagnostic("cfg:primary", "control-flow.evidence", DiagnosticStage.CONTROL_FLOW, DiagnosticSeverity.ERROR, "required evidence is erroneous", List.of(), AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var evidence = new AnalysisDiagnostic("frontend:evidence", "frontend.evidence", DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, "original producer wording", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var blocker = new CfgBlocker(MethodCfgState.ERRONEOUS_EVIDENCE, source.identity(), key, Optional.empty(), "binding", primary, Optional.of(evidence));
    var method = new MethodCfgOutcome(source.identity(), key, blocker.kind(), source.bodyRef(key), Optional.empty(), List.of(blocker), Optional.empty(), List.of(primary));
    var request = new CfgRequest(source.identity(), List.of(key), CfgProfile.JAVA_BOUNDED);
    var cfg = new CfgOutcome(request, CfgState.PARTIAL, List.of(method), List.of(primary));
    var run = AnalysisCapabilities.existingSourceDerivation((input, selected) -> cfg).deriveExisting(ast, request);
    var streams = new CliStreams();
    // Act
    CliTestFixtures.present(run, streams);
    // Assert
    assertEquals(List.of(primary), run.diagnostics().values());
    assertEquals(List.of("Diagnostic: \"cfg:primary\""), streams.errorLines().stream().filter(line -> line.startsWith("Diagnostic:")).toList());
    assertTrue(streams.errorLines().containsAll(List.of("  Evidence diagnostic: \"frontend:evidence\"", "    Severity: ERROR", "    Stage: FRONTEND", "    Cause: OTHER_ANALYSIS_ERROR", "    Code: \"frontend.evidence\"", "    Message: \"original producer wording\"", "    Origins: unavailable", "  Related diagnostic: \"cfg:primary\" occurrence=1")));
    assertEquals(1, streams.errorLines().stream().filter(line -> line.contains("Occurrence:")).count());
    assertFalse(streams.errorLines().stream().anyMatch(line -> line.startsWith("  Related diagnostic: \"frontend:evidence\"")));
  }
}
