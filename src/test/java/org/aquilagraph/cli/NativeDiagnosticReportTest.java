package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.RequestQualificationReason;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.frontend.internal.SourcePositionFixtures;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
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
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class NativeDiagnosticReportTest {
  @Test
  void givenWarningSharingTheUnsupportedSourceProtocol_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var original = analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED)
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
    var run = new SourceAnalysisFixture().analyze(ast);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertEquals(3, run.diagnostics().size());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
  }

  @Test
  void givenLocatedSourceUnitWithUnavailableBodyPosition_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var ast = SourcePositionFixtures.produce("class Example { int subject() { return 1; } }", tree -> tree.getKind() == Tree.Kind.BLOCK);
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var declaration = ast.aggregate().roots().getFirst();
    var diagnostic = new AnalysisDiagnostic("cfg:body-position", "control-flow.source-unavailable", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "The selected written body has unavailable source facts", declaration.origin().stream().toList(),
        AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var blocker = new CfgBlocker(MethodCfgState.SOURCE_UNAVAILABLE, ast.identity(), method.methodKey(), ast.occurrence(declaration),
        "body", diagnostic);
    var run = withRefusal(ast, blocker);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertTrue(ast.bodyRef(method.methodKey()).isEmpty());
    assertTrue(blocker.occurrence().isPresent());
  }

  @Test
  void givenUnpublishedBodyAfterDescendantPositionLoss_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var ast = SourcePositionFixtures.produce("class Example { void subject(boolean flag) { assert flag; while (flag) {} } }",
        tree -> tree.getKind() == Tree.Kind.WHILE_LOOP);
    var run = new SourceAnalysisFixture().analyze(ast);
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var loop = ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.sourceKind().name().equals("WHILE_LOOP")).findFirst().orElseThrow();
    var assertion = ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ASSERT).findFirst().orElseThrow();

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    var rendered = assertNativeReport(run, projection);
    assertTrue(rendered.contains("  Source occurrence: unavailable"));
    assertTrue(loop.origin().isEmpty());
    assertTrue(ast.occurrence(loop).isEmpty());
    assertTrue(assertion.origin().isPresent());
    assertTrue(ast.occurrence(assertion).isEmpty());
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
  }

  @Test
  void givenUnsupportedSourceWithoutOccurrencePosition_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var ast = SourcePositionFixtures.produce("class Example { void subject() { class Local {} } }",
        tree -> tree instanceof ClassTree declaration && declaration.getSimpleName().contentEquals("Local"));
    var run = new SourceAnalysisFixture().analyze(ast);
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var unsupported = ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED)
        .findFirst().orElseThrow();

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertTrue(unsupported.origin().isEmpty());
    assertTrue(ast.occurrence(unsupported).isEmpty());
  }

  @Test
  void givenBodyLevelRefusalForUnavailableDescendant_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var original = analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var ast = original.representation().ast().result().orElseThrow();
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var root = ast.body(method.methodKey()).orElseThrow().root();
    var diagnostic = new AnalysisDiagnostic("cfg:body-source", "control-flow.source-unavailable", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "The selected body contains unavailable source facts", root.origin().stream().toList(),
        AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var blocker = new CfgBlocker(MethodCfgState.SOURCE_UNAVAILABLE, ast.identity(), method.methodKey(), ast.occurrence(root), "body",
        diagnostic);
    var run = withRefusal(ast, blocker);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
  }

  @Test
  void givenAssignmentRefusalUsingAProviderSpecificSlot_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var original = analyze("class Example { int subject(int value) { (value) = 1; return value; } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var ast = original.representation().ast().result().orElseThrow();
    var method = ast.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var assignment = ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ASSIGNMENT).findFirst().orElseThrow();
    var diagnostic = new AnalysisDiagnostic("cfg:assignment-target", "control-flow.assignment-target", DiagnosticStage.CONTROL_FLOW,
        DiagnosticSeverity.ERROR, "Parenthesized assignment targets are outside the selected CFG profile",
        assignment.origin().stream().toList(), AnalysisOutcomeKind.CONTROL_FLOW_UNAVAILABLE);
    var blocker = new CfgBlocker(MethodCfgState.UNSUPPORTED, ast.identity(), method.methodKey(), ast.occurrence(assignment),
        "assignment.target", diagnostic);
    var run = withRefusal(ast, blocker);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    var rendered = assertNativeReport(run, projection);
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
  void givenClassLiteralOutsideTheBoundedCfgProfile_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = analyze("class Example { Class<?> subject() { return String.class; } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
    assertEquals(nativeDiagnostics, run.diagnostics());
  }

  @TestFactory
  Stream<DynamicTest> givenNonassignableSourceTargets_whenPresented_thenActualCompilerAndGraphRefusalsRemainVisible() {
    return Stream.of(
        new InvalidAssignment("Literal", "void subject() { 1 = 2; }"),
        new InvalidAssignment("Invocation", "int value() { return 1; } void subject() { value() = 2; }"))
        .map(example -> DynamicTest.dynamicTest("given" + example.context() + "AssignmentTarget_whenPresented_thenNativeRefusalsRemain", () -> {
          // Arrange
          var run = analyze("class Example { " + example.methods() + " }", AstProfile.JAVA_SOURCE_BOUNDED);
          var nativeDiagnostics = run.diagnostics();

          // Act
          var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

          // Assert
          assertNativeReport(run, projection);
          assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
          assertEquals(nativeDiagnostics, run.diagnostics());
        }));
  }

  @Test
  void givenInputNoticeBeforeUnsupportedSourceDiagnostic_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var original = analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED)
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
    var run = new SourceAnalysisFixture().analyze(ast);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    var rendered = assertNativeReport(run, projection);
    var related = relatedFromOwner(rendered, "SOURCE");
    assertFalse(related.isEmpty());
    assertTrue(related.stream().allMatch(line -> line.equals("  Related diagnostic: \"" + original.diagnostics().getFirst().diagnosticKey() + "\" occurrence=2")));
    assertEquals(diagnostics, ast.diagnostics());
    assertEquals(3, run.diagnostics().size());
  }

  @Test
  void givenMalformedBinaryExpression_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = analyze("class Example { int subject() { return 1 + ; } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var diagnostics = run.representation().ast().diagnostics();
    var capability = diagnostics.stream().filter(value -> value.code().equals("frontend.unsupported-tree-kind.erroneous"))
        .findFirst().orElseThrow();

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
    assertTrue(diagnostics.stream().anyMatch(value -> value.code().startsWith("compiler.")));
  }

  @TestFactory
  Stream<DynamicTest> givenSuppliedSourceDiagnosticsOutsideTheBoundedProtocol_whenPresented_thenSourceAndGraphCausesRemainDistinct() {
    return Stream.of("Message", "Code", "Originless")
        .map(variant -> DynamicTest.dynamicTest("givenAlternative" + variant + "SourceDiagnostic_whenPresented_thenBothCausesRemain", () -> {
          // Arrange
          var original = analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED)
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
          var run = new SourceAnalysisFixture().analyze(ast);

          // Act
          var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

          // Assert
          var rendered = assertNativeReport(run, projection);
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
    var original = analyze("class Example { void subject() { class Local {} } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var ast = original.representation().ast();
    var originalCfg = original.representation().cfg().orElseThrow();
    var refused = originalCfg.methods().stream().filter(method -> !method.blockers().isEmpty()).findFirst().orElseThrow();
    var blocker = refused.blockers().getFirst();
    var repeated = new MethodCfgOutcome(refused.astIdentity(), refused.methodKey(), refused.state(), refused.body(), refused.graph(),
        List.of(blocker, blocker), refused.failure(), List.of(blocker.diagnostic(), blocker.diagnostic()));
    var methods = originalCfg.methods().stream().map(method -> method.equals(refused) ? repeated : method).toList();
    var cfg = new CfgOutcome(originalCfg.request(), originalCfg.state(), methods,
        methods.stream().flatMap(method -> method.diagnostics().stream()).toList());
    var runner = new SourceAnalysisRunner(ignored -> ast, (source, request) -> cfg);
    var run = runner.deriveExisting(ast, cfg.request());

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertEquals(3, run.diagnostics().size());
    assertEquals(List.of(blocker.diagnostic(), blocker.diagnostic()), cfg.diagnostics());
    assertSame(ast, run.representation().ast());
  }

  @Test
  void givenAdmittedSourceWithUnavailableImplicitConstructorPosition_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var original = analyze("class Example { int subject() { return 1; } }", AstProfile.JAVA_SOURCE_BOUNDED)
        .representation().ast().result().orElseThrow();
    var constructorKey = original.program().methods().stream().filter(value -> value.constructor()).findFirst().orElseThrow().methodKey();
    var copied = SourceValueCapture.capture(original.aggregate());
    var bodies = copied.bodies().stream().map(body -> body.methodKey().equals(constructorKey)
        ? new CopiedAstValues.Body(body.methodKey(), body.root(), MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION, body.uses(),
            body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())
        : body).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes()));
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
        original.diagnostics(), original.coverage());
    var ast = AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
    var run = new SourceAnalysisFixture().analyze(ast);

    // Act
    var summary = AnalysisRunSummary.from(run);

    // Assert
    assertNativeReport(run, summary.occurrences().diagnostics());
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertEquals(AnalysisCompletion.PARTIAL, summary.run().completion());
    assertEquals(List.of(MethodCfgState.SOURCE_UNAVAILABLE, MethodCfgState.AVAILABLE),
        run.representation().cfg().orElseThrow().methods().stream().map(value -> value.state()).toList());
    assertEquals(1, summary.program().orElseThrow().analyzedMethodBodyCount());
  }

  @TestFactory
  Stream<DynamicTest> givenProducerNotesAtDifferentSourceLocations_whenPresented_thenBoundedRefusalKeepsItsWrittenPosition() {
    return Stream.of("Originless", "Input", "Assignment", "Target", "TargetStart", "Body", "BeforeBody", "BeyondBody", "ForeignAndTarget")
        .map(location -> DynamicTest.dynamicTest("given" + location + "ProducerNote_whenPresented_thenWrittenDiagnosticOrderIsPreserved", () -> {
          // Arrange
          var companion = SourceFile.fromContent(Path.of("Context.java"), "class Context {}");
          var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"),
              "class Example { int subject(int value) { (value) = 1; return value; } }"), companion));
          var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
              AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
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
          var run = new SourceAnalysisFixture().analyze(ast);



          // Act
          var summary = AnalysisRunSummary.from(run);

          // Assert
          assertNativeReport(run, summary.occurrences().diagnostics());
          assertEquals(AstCompletion.COMPLETE, ast.completion());
          assertEquals(List.of(diagnostic), ast.diagnostics());
          assertEquals(AnalysisCompletion.PARTIAL, summary.run().completion());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenBoundedSwitchLabels_whenPresented_thenSupportedCasesRemainFreeOfRefusals() {
    return Stream.of(
        new SwitchExample("NumericLabels", "int subject(int value) { return switch (value) { case 1, 2 -> 3; default -> 4; }; }"),
        new SwitchExample("StringLabels", "int subject(String value) { return switch (value) { case \"a\", \"b\" -> 1; default -> 2; }; }"),
        new SwitchExample("EnumLabels", "int subject(RetentionPolicy value) { return switch (value) { case SOURCE, CLASS -> 1; case RUNTIME -> 2; }; }"),
        new SwitchExample("QualifiedEnumLabel", "int subject(Object value) { return switch (value) { case RetentionPolicy.SOURCE -> 1; default -> 2; }; }"))
        .map(example -> DynamicTest.dynamicTest("given" + example.context() + "_whenPresented_thenNoRefusalsRemain", () -> {
          // Arrange
          var run = analyze("import java.lang.annotation.RetentionPolicy; class Example { " + example.method() + " }",
              AstProfile.JAVA_SOURCE_BOUNDED);

          // Act
          var summary = AnalysisRunSummary.from(run);

          // Assert
          assertNativeReport(run, summary.occurrences().diagnostics());
          assertEquals(AnalysisCompletion.COMPLETE, summary.run().completion());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenExpandedSwitchExpressionLabels_whenPresented_thenEachUnsupportedCaseKeepsItsBoundedRefusal() {
    return Stream.of(
        new SwitchExample("StatementGroups", "int subject(int value) { return switch (value) { case 1: yield 2; default: yield 3; }; }"),
        new SwitchExample("ConstantExpression", "int subject(int value) { return switch (value) { case 1 + 2 -> 3; default -> 4; }; }"),
        new SwitchExample("NullLabel", "int subject(String value) { return switch (value) { case null -> 0; default -> 1; }; }"),
        new SwitchExample("LocalConstant", "int subject(int value) { final int label = 1; return switch (value) { case label -> 2; default -> 3; }; }"))
        .map(example -> DynamicTest.dynamicTest("given" + example.context() + "_whenPresented_thenBoundedCaseRefusalsRemain", () -> {
          // Arrange
          var run = analyze("class Example { " + example.method() + " }", AstProfile.JAVA_SOURCE_BOUNDED);
          var nativeDiagnostics = run.diagnostics();

          // Act
          var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

          // Assert
          assertNativeReport(run, projection);
          assertEquals(nativeDiagnostics, run.diagnostics());
        }));
  }

  @Test
  void givenGuardedSwitchExpression_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = analyze("class Example { int subject(Object value) { return switch (value) { "
        + "case String text when text.length() > 0 -> text.length(); default -> 0; }; } }", AstProfile.JAVA_SOURCE_BOUNDED);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
  }

  @Test
  void givenAstOnlyRequestWithSourceErrors_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { int subject() { return missing; } }")));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var request = new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED, Optional.empty(), PublicationIntent.DO_NOT_SAVE);
    var run = runner.analyze(request);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertTrue(run.representation().cfg().isEmpty());
  }

  @Test
  void givenAlternativeCfgProfile_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { int subject(int value) { return switch (value) { case 1 -> 2; default -> 3; }; } }")));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var request = new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS)),
        PublicationIntent.DO_NOT_SAVE);
    var run = runner.analyze(request);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
  }

  @Test
  void givenMalformedExpressionBesideIteration_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = analyze("class Example { int subject(boolean flag) { while (flag) {} return (); } }",
        AstProfile.JAVA_SOURCE_BOUNDED);
    var sourceDiagnostics = run.representation().ast().diagnostics();
    var capability = sourceDiagnostics.stream()
        .filter(value -> value.code().equals("frontend.unsupported-tree-kind.erroneous")).findFirst().orElseThrow();
    var compilerErrors = sourceDiagnostics.stream().filter(value -> value.code().startsWith("compiler."))
        .toList();

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertFalse(compilerErrors.isEmpty());
    assertEquals(sourceDiagnostics, run.representation().ast().diagnostics());
  }

  @Test
  void givenInterleavedSourceAndGraphRefusalsInSeveralBodies_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible()
      throws IOException {
    // Arrange
    var run = analyze("""
        class Example {
          int first(int value) { (value) = 1; class First {} return value; }
          int second(int value) { class Second {} (value) = 2; class Third {} return value; }
        }
        """, AstProfile.JAVA_SOURCE_BOUNDED);
    var original = run.diagnostics();

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertEquals(original, run.diagnostics());
  }

  @Test
  void givenRefusalsInSeveralFilesAndBodylessDeclarations_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(
        SourceFile.fromContent(Path.of("First.java"), "interface First { void bodyless(); default void first(boolean flag) { while (flag) {} } }"),
        SourceFile.fromContent(Path.of("Second.java"), "class Second { int second(int value) { (value) = 1; class Local {} return value; } }")));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED);
    var run = new SourceAnalysisFixture().analyze(ast);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
  }

  @TestFactory
  Stream<DynamicTest> givenUnsupportedDeclarationWithinExceptionClauses_whenPresented_thenEachSourceCauseRemainsOnceInWrittenOrder() {
    return Stream.of(
      "try { class Local {} } finally {}",
      "try {} catch (RuntimeException failure) { class Local {} } finally {}",
      "try {} finally { class Local {} }",
      "try (input) { class Local {} }",
      "try (AutoCloseable local = input) { class Local {} } finally {}")
        .map(statement -> DynamicTest.dynamicTest("givenUnsupportedLocalDeclarationIn" + statement + "_whenPresented_thenNativeEventsAndReasonsRemainVisible", () -> {
          // Arrange
          var source = "class Example { void subject(AutoCloseable input) throws Exception { " + statement + " } }";
          var bounded = analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
          var expanded = analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var nativeBounded = bounded.diagnostics();
          var nativeExpanded = expanded.diagnostics();
          var boundedAst = bounded.representation().ast().result().orElseThrow();
          var boundedCfg = bounded.representation().cfg().orElseThrow();
          // Act
          var projected = AnalysisRunSummary.from(bounded).occurrences().diagnostics();
          var expandedProjection = AnalysisRunSummary.from(expanded).occurrences().diagnostics();
          // Assert
          assertNativeReport(bounded, projected);
          assertNativeReport(expanded, expandedProjection);
          assertEquals(List.of("Unsupported Java construct in method body: CLASS"),
              boundedAst.diagnostics().stream().map(value -> value.message()).toList());
          assertEquals(nativeBounded, bounded.diagnostics());
          assertEquals(nativeExpanded, expanded.diagnostics());
          assertSame(boundedAst, bounded.representation().ast().result().orElseThrow());
          assertSame(boundedCfg, bounded.representation().cfg().orElseThrow());
          assertEquals(AstCompletion.INCOMPLETE, boundedAst.completion());
          assertEquals(AstCompletion.INCOMPLETE, expanded.representation().ast().completion());
        }));
  }

  @Test
  void givenUnsupportedDeclarationWithinNestedTries_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = analyze("class Example { void subject() { try { try {} finally { class Local {} } } finally {} } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();
    // Act
    var projected = AnalysisRunSummary.from(run).occurrences().diagnostics();
    // Assert
    assertNativeReport(run, projected);
    assertEquals(nativeDiagnostics, run.diagnostics());
  }

  @Test
  void givenExceptionClauseWithinBoundedIterationBoundary_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = analyze("class Example { void subject(boolean flag) { while (flag) { try { class Local {} } finally {} } } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();
    // Act
    var projected = AnalysisRunSummary.from(run).occurrences().diagnostics();
    // Assert
    assertNativeReport(run, projected);
    assertEquals(nativeDiagnostics, run.diagnostics());
  }

  @Test
  void givenNestedExceptionSyntaxInBothProfiles_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var source = "class Example { void value(AutoCloseable input, boolean flag) throws Exception { try (input) { try { while(flag) {} } finally {} } finally {} } }";
    var bounded = analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var originalBounded = bounded.diagnostics();
    var originalExpanded = expanded.diagnostics();
    // Act
    var projected = AnalysisRunSummary.from(bounded).occurrences().diagnostics();
    var expandedProjection = AnalysisRunSummary.from(expanded).occurrences().diagnostics();
    // Assert
    assertNativeReport(bounded, projected);
    assertNativeReport(expanded, expandedProjection);
    assertEquals(originalBounded, bounded.diagnostics());
    assertEquals(originalExpanded, expanded.diagnostics());
    assertEquals(AstCompletion.COMPLETE, expanded.representation().ast().completion());
    assertEquals(AstCompletion.INCOMPLETE, bounded.representation().ast().completion());
  }

  @Test
  void givenMultiCatchInBothProfiles_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var source = "class Example { void value() { try {} catch (IllegalArgumentException | IllegalStateException failure) {} } }";
    var bounded = analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
    // Act
    var projected = AnalysisRunSummary.from(bounded).occurrences().diagnostics();
    var expandedProjection = AnalysisRunSummary.from(expanded).occurrences().diagnostics();
    // Assert
    assertNativeReport(bounded, projected);
    assertNativeReport(expanded, expandedProjection);
    assertEquals(List.of(), bounded.diagnostics());
    assertEquals(List.of(), expanded.diagnostics());
    assertEquals(AstCompletion.COMPLETE, expanded.representation().ast().completion());
    assertEquals(AnalysisCompletion.COMPLETE, expanded.completion());
  }

  @Test
  void givenNestedAssertionsAndMonitor_whenBoundedPresentationRuns_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = analyze("class Example { void value(Object lock, boolean flag) { assert flag; synchronized (lock) { assert flag; } } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var nativeDiagnostics = run.diagnostics();
    // Act
    var projected = AnalysisRunSummary.from(run).occurrences().diagnostics();
    // Assert
    assertNativeReport(run, projected);
    assertEquals(nativeDiagnostics, run.diagnostics());
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
  }

  @Test
  void givenNestedIterationInBothProfiles_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var source = "class Example { void value(int n) { outer: while (n > 0) { for (int i = 0; i < n; i++) { continue outer; } break; } } }";
    var bounded = analyze(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = analyze(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var originalBounded = bounded.diagnostics();
    var originalExpanded = expanded.diagnostics();
    // Act
    var boundedProjection = AnalysisRunSummary.from(bounded).occurrences().diagnostics();
    var expandedProjection = AnalysisRunSummary.from(expanded).occurrences().diagnostics();
    // Assert
    assertNativeReport(bounded, boundedProjection);
    assertNativeReport(expanded, expandedProjection);
    assertEquals(originalBounded, bounded.diagnostics());
    assertEquals(originalExpanded, expanded.diagnostics());
    assertEquals(AstCompletion.COMPLETE, expanded.representation().ast().completion());
  }

  @Test
  void givenRealCompilerErrorWithinAnIteration_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = analyze("class Example { void value() { while (missing) { } } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var genuine = run.representation().ast().diagnostics().stream().filter(diagnostic -> !diagnostic.code().startsWith("frontend.unsupported-")).toList();
    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();
    // Assert
    assertNativeReport(run, projection);
    assertEquals(AstCompletion.INCOMPLETE, run.representation().ast().completion());
    assertEquals(true, genuine.stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    for (var diagnostic : genuine) {
      assertEquals(run.representation().ast().diagnostics().stream().filter(diagnostic::equals).count(),
          projection.stream().filter(diagnostic::equals).count());
    }
  }

  @Test
  void givenBoundedAndExpandedProfiles_whenRepeatedRefusalsArePresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var source = "class Example { int value(int a) { (a) = 1; (a) = 2; return a; } }";
    for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)) {
      var run = analyze(source, profile);
      var ast = run.representation().ast().result().orElseThrow();
      var cfg = run.representation().cfg().orElseThrow();
      var nativeDiagnostics = run.diagnostics();

      // Act
      var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

      // Assert
      assertNativeReport(run, projection);
      assertEquals(AstCompletion.COMPLETE, ast.completion());
      assertEquals(List.of(), ast.diagnostics());
      assertSame(ast, run.representation().ast().result().orElseThrow());
      assertSame(cfg, run.representation().cfg().orElseThrow());
      assertEquals(nativeDiagnostics, run.diagnostics());
      assertEquals(List.of("control-flow.unsupported", "control-flow.unsupported"), nativeDiagnostics.stream().map(value -> value.code()).toList());
    }
  }

  @Test
  void givenNestedTargetsInMultipleBodies_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var run = analyze("""
        class Example {
          int first(int a) { if (a > 0) { (a) = 1; } return a; }
          int second(int a) { while (a > 0) { a = 0; } (a) = 2; return a; }
        }
        """, AstProfile.JAVA_SOURCE_BOUNDED);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
  }

  @Test
  void givenRepeatedApplicationSlotsEqualToGraphDiagnostic_whenPresented_thenNativeEventsAndOwnerReasonsRemainVisible() throws IOException {
    // Arrange
    var original = analyze("class Example { int value(int a) { (a) = 1; return a; } }", AstProfile.JAVA_SOURCE_BOUNDED);
    var graphDiagnostic = original.diagnostics().getFirst();
    var applicationDiagnostics = List.of(graphDiagnostic, graphDiagnostic);
    var run = AnalysisRun.create(original.identity(), original.representation(), applicationDiagnostics);

    // Act
    var projection = AnalysisRunSummary.from(run).occurrences().diagnostics();

    // Assert
    assertNativeReport(run, projection);
    assertEquals(3, run.diagnostics().size());
    assertEquals(List.of(graphDiagnostic, graphDiagnostic), run.applicationDiagnostics());
  }

  @Test
  void givenSourceOwnerEvidenceEqualToANativeEvent_whenPresented_thenRetainedEvidenceDoesNotAcquireAnInventedOccurrence() throws IOException {
    // Arrange
    var original = analyze("interface Example { static int read() { return Integer.MAX_VALUE; } }", AstProfile.JAVA_SOURCE_BOUNDED)
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
    var run = new SourceAnalysisRunner(ignored -> outcome, CfgDerivers.standard())
        .deriveExisting(outcome, new CfgRequest(ast.identity(), List.of(), CfgProfile.JAVA_BOUNDED));
    var output = new ByteArrayOutputStream();
    var errors = new ByteArrayOutputStream();

    // Act
    new AnalyzeCommandPresenter().present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)),
        new PrintStream(output, true, StandardCharsets.UTF_8), new PrintStream(errors, true, StandardCharsets.UTF_8));

    // Assert
    var ownerReason = ast.qualification().reasons().stream().filter(reason -> reason.role() == EvidenceRole.STATIC_OWNER).findFirst().orElseThrow();
    assertTrue(ownerReason.diagnostics().isEmpty());
    assertEquals(error, ownerReason.evidence().getFirst().diagnostic());
    assertEquals(List.of(error), run.diagnostics());
    var lines = errors.toString(StandardCharsets.UTF_8).lines().toList();
    assertEquals(List.of("Diagnostic: \"source:owner\""), lines.stream().filter(line -> line.startsWith("Diagnostic:")).toList());
    var index = lines.indexOf("  Evidence diagnostic: \"source:owner\"");
    assertTrue(index >= 0);
    assertEquals(List.of("  Evidence diagnostic: \"source:owner\"", "    Severity: ERROR", "    Stage: FRONTEND",
        "    Cause: OTHER_ANALYSIS_ERROR", "    Code: \"source.owner\"", "    Message: \"Supplied owner error\"", "    Origins: unavailable"),
        lines.subList(index, index + 7));
    assertEquals(1, lines.stream().filter(line -> line.contains("Occurrence:")).count());
  }

  private static List<String> assertNativeReport(AnalysisRun run, List<AnalysisDiagnostic> projected) {
    var summary = AnalysisRunSummary.from(run);
    var output = new ByteArrayOutputStream();
    var error = new ByteArrayOutputStream();
    new AnalyzeCommandPresenter().present(new AnalyzeCommandOutcome.Executed(CliTestFixtures.withoutRepositoryRequest(run)),
        new PrintStream(output, true, StandardCharsets.UTF_8),
        new PrintStream(error, true, StandardCharsets.UTF_8));
    var events = summary.occurrences().diagnostics();
    assertEquals(run.diagnostics(), events);
    assertEquals(run.diagnostics().stream().map(value -> "Diagnostic: \"" + value.diagnosticKey() + "\"").toList(),
        error.toString(StandardCharsets.UTF_8).lines().filter(line -> line.startsWith("Diagnostic: ")).toList());
    assertEquals(run.diagnostics().stream().map(value -> "  Stage: " + value.stage()).toList(),
        error.toString(StandardCharsets.UTF_8).lines().filter(line -> line.startsWith("  Stage: ")).toList());
    assertEquals(IntStream.rangeClosed(1, events.size()).mapToObj(position -> "  Occurrence: " + position).toList(),
        error.toString(StandardCharsets.UTF_8).lines().filter(line -> line.startsWith("  Occurrence: ")).toList());
    var lines = error.toString(StandardCharsets.UTF_8).lines().toList();
    assertEquals(run.diagnostics().stream().map(value -> "  Code: \"" + value.code() + "\"").toList(),
        lines.stream().filter(line -> line.startsWith("  Code:")).toList());
    assertEquals(run.diagnostics().stream().map(value -> "  Severity: " + value.severity()).toList(),
        lines.stream().filter(line -> line.startsWith("  Severity:")).toList());
    assertEquals(run.diagnostics().stream().map(value -> "  Cause: " + value.outcomeKind()).toList(),
        lines.stream().filter(line -> line.startsWith("  Cause:")).toList());
    assertEquals(run.representation().ast().qualification().reasons().stream().map(reason -> "Qualification: SOURCE " + reason.category()).toList(),
        lines.stream().filter(line -> line.startsWith("Qualification: SOURCE ")).toList());
    var expectedCfg = new ArrayList<String>();
    run.representation().cfg().ifPresent(cfg -> {
      if (cfg.state() == CfgState.REJECTED) { expectedCfg.add("Qualification: CFG REJECTED"); }
      cfg.methods().forEach(method -> {
        if (method.state() == MethodCfgState.FAILED) { expectedCfg.add("Qualification: CFG FAILED"); }
        method.blockers().forEach(blocker -> expectedCfg.add("Qualification: CFG " + blocker.kind()));
      });
    });
    assertEquals(expectedCfg, lines.stream().filter(line -> line.startsWith("Qualification: CFG ")).toList());
    assertEquals(run.qualification().reasons().stream().map(reason -> "Qualification: APPLICATION " + switch (reason) {
      case RequestQualificationReason.Source _ -> "SOURCE";
      case RequestQualificationReason.Selection _ -> "SELECTION";
      case RequestQualificationReason.Method _ -> "METHOD";
      case RequestQualificationReason.MethodDiagnostic _ -> "METHOD_DIAGNOSTIC";
      case RequestQualificationReason.ApplicationDiagnostic _ -> "APPLICATION_DIAGNOSTIC";
    }).toList(), lines.stream().filter(line -> line.startsWith("Qualification: APPLICATION ")).toList());
    var sourceReferences = run.representation().ast().qualification().reasons().stream()
        .flatMap(reason -> reason.diagnostics().stream())
        .map(reference -> "  Related diagnostic: \"" + reference.diagnostic().diagnosticKey() + "\" occurrence=" + (reference.position() + 1)).toList();
    assertEquals(sourceReferences, relatedFromOwner(lines, "SOURCE"));
    var cfg = run.representation().cfg();
    var blockers = cfg.stream().flatMap(value -> value.methods().stream()).flatMap(method -> method.blockers().stream()).toList();
    var expectedAstResults = cfg.map(value -> Collections.nCopies(expectedCfg.size(),
        "  AST result: \"" + value.request().astIdentity().astResultKey() + "\"")).orElseGet(List::of);
    assertEquals(expectedAstResults, lines.stream().filter(line -> line.startsWith("  AST result:")).toList());
    assertEquals(blockers.stream().map(blocker -> "  Source occurrence: " + blocker.occurrence()
        .map(value -> "\"" + value.nodeKey() + "\"").orElse("unavailable")).toList(),
        lines.stream().filter(line -> line.startsWith("  Source occurrence:")).toList());
    assertEquals(blockers.stream().map(blocker -> "  Slot: \"" + blocker.slot() + "\"").toList(),
        lines.stream().filter(line -> line.startsWith("  Slot:")).toList());
    assertSame(run, summary.run());
    assertEquals(events, projected);
    return lines;
  }

  private static List<String> relatedFromOwner(List<String> lines, String owner) {
    return qualificationBlocks(lines, owner).stream().flatMap(List::stream)
        .filter(line -> line.startsWith("  Related diagnostic:")).toList();
  }

  private static List<List<String>> qualificationBlocks(List<String> lines, String owner) {
    var result = new ArrayList<List<String>>();
    var current = new ArrayList<String>();
    var selected = false;
    for (var line : lines) {
      if (line.startsWith("Qualification: ")) {
        if (selected) { result.add(List.copyOf(current)); }
        current.clear();
        selected = line.startsWith("Qualification: " + owner + " ");
      }
      if (selected) { current.add(line); }
    }
    if (selected) { result.add(List.copyOf(current)); }
    return List.copyOf(result);
  }

  private static AnalysisRun withRefusal(AstResult ast, CfgBlocker blocker) {
    var request = new CfgRequest(ast.identity(), List.of(blocker.methodKey()), CfgProfile.JAVA_BOUNDED);
    var method = new MethodCfgOutcome(ast.identity(), blocker.methodKey(), blocker.kind(), ast.bodyRef(blocker.methodKey()),
        Optional.empty(), List.of(blocker), Optional.empty(), List.of(blocker.diagnostic()));
    var cfg = new CfgOutcome(request, CfgState.PARTIAL, List.of(method), method.diagnostics());
    var outcome = AstOutcome.available(ast);
    return new SourceAnalysisRunner(ignored -> outcome, (source, selected) -> cfg).deriveExisting(outcome, request);
  }

  private record SwitchExample(String context, String method) {}

  private record InvalidAssignment(String context, String methods) {}

  private static AnalysisRun analyze(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
    return new SourceAnalysisFixture().analyze(ast);
  }
}
