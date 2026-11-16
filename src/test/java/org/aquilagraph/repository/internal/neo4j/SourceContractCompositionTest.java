package org.aquilagraph.repository.internal.neo4j;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.PublicationSubjectIdentity.Ast;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication.FieldDeclaratorApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationTypeLocation;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;

class SourceContractCompositionTest {
  private static final String ANNOTATIONS = """
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Target;
      @Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Both {}
      @Target(ElementType.TYPE_USE) @interface Use { int value(); }
      """;
  private static final String ANNOTATED_DECLARATIONS = """
      import java.util.function.IntUnaryOperator;
      class Sample {
        @Both int first, second;
        int supported(int input) {
          @Both int localFirst = 1, localSecond = 2;
          return input + localFirst + localSecond;
        }
        int refused(int input) {
          IntUnaryOperator next = value -> value + input;
          return next.applyAsInt(input);
        }
      }
      """;
  private static final String ANNOTATED_RECORD = """
      import java.util.List;
      record Bundle(List<? extends List<? super @Use(1) String @Use(2) []>> value) {}
      """;
  private static final String SOURCE = mixedTerminators("""
      import java.lang.annotation.*;
      @Target(ElementType.TYPE_USE) @interface Mark {}
      @Repeatable(Tags.class) @interface Tag { int value(); }
      @Repeatable(Groups.class) @interface Tags { Tag[] value(); }
      @interface Groups { Tags[] value(); }
      sealed interface Root {}
      final class Leaf implements Root {}
      @Tags({@Tag(11)}) @Tags({@Tag(22), @Tag(33)})
      class Sample {
        static {}
        {}
        Sample() {}
        static long seed = 0x1_0L;
        String text() { return "line\\n\\141"; }
        long combine(int mode, Integer boxed, Object left, Object right) {
          long selected;
          switch (mode) {
            case 0: selected = 0b11; break;
            default: selected = 0_7;
          }
          if (left instanceof @Mark String first && right instanceof @Mark String second) {
            selected = selected + first.length() + second.length();
          }
          return selected + boxed;
        }
        int supported(int input) { return input + 0x2; }
        int refused(int input) {
          java.util.function.IntUnaryOperator next = value -> value + input;
          return next.applyAsInt(input);
        }
      }
      """);

  @TempDir Path directory;

  @Test
  void givenSharedAnnotationsAndRecursiveRecordInSeparateUnits_whenRetainedAndDerived_thenExactDetachedTargetsSurviveCfgRefusal() {
    // Arrange
    var input = annotatedRequest(Optional.empty());
    var computation = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var noSave = AnalysisCapabilities.astRetention(context -> {
      throw new AssertionError("Source-only analysis must not acquire storage");
    });
    var write = new AstRepositoryDriverStub();
    var retention = AnalysisCapabilities.astRetention(context ->
        new Neo4jAstResultRepository(write.driver(), SessionConfig.defaultConfig(), context));
    var read = new AstRepositoryDriverStub();
    var query = AnalysisCapabilities.astQuery(context ->
        new Neo4jAstResultRepository(read.driver(), SessionConfig.defaultConfig(), context));

    // Act
    var analysis = computation.analyze(new AnalysisComputationRequest(input, Optional.empty()));
    var outcome = analysis.representation().ast();
    var original = outcome.result().orElseThrow();
    var unrequested = noSave.retainAst(new AstPublicationRequest(outcome, PublicationIntent.DO_NOT_SAVE));
    write.lockedAstSubject = original.identity().astResultKey();
    var publication = retention.retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
    read.stored = write.writtenAst(original.identity());
    var lookup = query.findAst(original.identity());
    var retained = lookup.outcome().orElseThrow().result().orElseThrow();
    var selected = List.of(typeKey("Sample") + "#supported(int)", typeKey("Sample") + "#refused(int)");
    var derived = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(AstOutcome.available(retained),
        new CfgRequest(retained.identity(), selected, CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, analysis.completion(), outcome.diagnostics().toString());
    assertEquals(AstCompletion.COMPLETE, outcome.completion());
    assertTrue(analysis.representation().cfg().isEmpty());
    assertEquals(PublicationStatus.NOT_REQUESTED, unrequested.status());
    assertEquals(CommitKnowledge.NOT_STARTED, unrequested.knowledge());
    assertEquals(PublicationStatus.SAVED, publication.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertEquals(original.identity(), lookup.requestedIdentity());
    assertEquals(original.identity(), retained.identity());
    assertEquals("java-source/23", retained.identity().sourceModelVersion());
    assertEquals("java-source-expanded/8", retained.identity().astProfileVersion());
    assertEquals("javac-public/14", retained.identity().semanticProductionRevision());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.coverage(), retained.coverage());
    assertEquals(original.qualification(), retained.qualification());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
    assertEquals(AnalysisCompletion.PARTIAL, derived.completion());
    var methods = derived.representation().cfg().orElseThrow().methods();
    assertEquals(selected, methods.stream().map(method -> method.methodKey()).toList());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED), methods.stream().map(method -> method.state()).toList());
    assertTrue(methods.getFirst().graph().isPresent());
    assertTrue(methods.getLast().graph().isEmpty());
    assertEquals(AstCompletion.COMPLETE, derived.representation().ast().completion());
    for (var ast : List.of(original, retained, derived.representation().ast().result().orElseThrow())) {
      assertTrue(ast.diagnostics().isEmpty(), ast.diagnostics().toString());
      assertAnnotatedTargets(ast);
    }
    assertEquals("driver.close", write.events.getLast());
    assertEquals("driver.close", read.events.getLast());
  }

  @Test
  void givenAnnotatedUnitsWithErroneousNeighbor_whenProducedAndDecoded_thenDiagnosticsAndTargetsRemainDetachedWithoutPublication() {
    // Arrange
    var invalid = SourceFile.fromContent(capturedPath(Path.of("Invalid.java")),
        "class Invalid { void invalid() { return; int unreachable = 1; } }");
    var input = annotatedRequest(Optional.of(invalid));
    var retention = AnalysisCapabilities.astRetention(context -> {
      throw new AssertionError("Incomplete source must not acquire storage");
    });

    // Act
    var outcome = JavaFrontends.standard().produce(input);
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var publication = retention.retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    var compilerErrors = outcome.diagnostics().stream().filter(diagnostic -> diagnostic.code().startsWith("compiler.err.")).toList();
    assertEquals(List.of("compiler.err.unreachable.stmt"), compilerErrors.stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(DiagnosticSeverity.ERROR, compilerErrors.getFirst().severity());
    assertEquals(List.of(invalid.sourceFileKey()), compilerErrors.getFirst().origins().stream().map(SourceSpan::sourceFileKey).toList());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
    assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
    assertEquals(original.identity(), decoded.identity());
    assertEquals(original.qualification(), decoded.qualification());
    assertEquals(original.coverage(), decoded.coverage());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    for (var ast : List.of(original, decoded)) {
      assertAnnotatedTargets(ast);
      assertEquals(outcome.diagnostics(), ast.diagnostics());
      var evidence = ast.coverage().validationEvidence();
      var events = evidence.diagnosticEmissions();
      assertFalse(events.isEmpty());
      assertEquals(IntStream.range(0, outcome.diagnostics().size()).boxed().toList(), events.stream().map(DiagnosticEmission::slot).toList());
      assertEquals(outcome.diagnostics(), events.stream().map(DiagnosticEmission::diagnostic).toList());
      assertEquals(events.size(), events.stream().map(DiagnosticEmission::address).distinct().count());
      for (var event : events) {
        assertEquals(event.diagnostic().outcomeKind(), event.cause());
        assertEquals(evidence.outcomes().stream().filter(context -> context.emissions().contains(event.address()))
            .map(context -> context.address()).toList(), event.contexts());
      }
    }
  }

  @Test
  void givenCorrectedLexicalSource_whenApplicationRetainsAndQueriesThroughAdapter_thenExactFactsSurviveCfgRefusal() {
    // Arrange
    var spelling = "tr" + "\\" + "u0000ue";
    var source = "class Box { <T> Box(T x) {} " + "\\" + "u007d class Sample { "
        + "Object make() { return new Box(1); } String literal() { return \"prefix\" + " + spelling + "; } "
        + "int refused() { java.util.function.IntSupplier next = () -> 1; return next.getAsInt(); } }";
    var input = request(source);
    var runner = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var write = new AstRepositoryDriverStub();
    var retention = AnalysisCapabilities.astRetention(context ->
        new Neo4jAstResultRepository(write.driver(), SessionConfig.defaultConfig(), context));
    var noSave = AnalysisCapabilities.astRetention(context -> {
      throw new AssertionError("No-save must not acquire storage");
    });

    // Act
    var analysis = runner.analyze(new AnalysisComputationRequest(
        new AstProductionRequest(input.input(), input.configuration(), input.profile()), Optional.empty()));
    var outcome = analysis.representation().ast();
    var original = outcome.result().orElseThrow();
    var unrequested = noSave.retainAst(new AstPublicationRequest(outcome, PublicationIntent.DO_NOT_SAVE));
    var publication = retention.retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
    var read = new AstRepositoryDriverStub();
    read.stored = new Neo4jAstResultCodec().map(outcome);
    var lookup = AnalysisCapabilities.astQuery(context ->
        new Neo4jAstResultRepository(read.driver(), SessionConfig.defaultConfig(), context)).findAst(original.identity());
    var retained = lookup.outcome().orElseThrow().result().orElseThrow();
    var refused = retained.program().methods().stream().filter(method -> method.name().equals("refused")).findFirst().orElseThrow();
    var derived = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(AstOutcome.available(retained),
        new CfgRequest(retained.identity(), List.of(refused.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(PublicationStatus.NOT_REQUESTED, unrequested.status());
    assertEquals(PublicationStatus.SAVED, publication.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertEquals(original.identity(), retained.identity());
    assertEquals("javac-public/14", retained.identity().semanticProductionRevision());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.coverage(), retained.coverage());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
    var literalStart = source.indexOf(spelling);
    var literal = retained.aggregate().nodes().stream().filter(node -> node.origin().equals(Optional.of(
        new SourceSpan(retained.snapshot().sourceFiles().getFirst().sourceFileKey(), literalStart, literalStart + spelling.length()))))
        .findFirst().orElseThrow();
    assertEquals("true", literal.value(SyntaxValue.Literal.class).text());
    var make = retained.program().methods().stream().filter(method -> method.name().equals("make")).findFirst().orElseThrow();
    var body = retained.body(make.methodKey()).orElseThrow();
    var creation = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).findFirst().orElseThrow();
    assertEquals("java.lang.Integer", body.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
    assertEquals(MethodCfgState.UNSUPPORTED, derived.representation().cfg().orElseThrow().methods().getFirst().state());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(derived.representation().ast().result().orElseThrow()));
    assertEquals("driver.close", write.events.getLast());
    assertEquals("driver.close", read.events.getLast());
  }

  @Test
  void givenMixedSourceFamilies_whenProducedAndDecoded_thenDetachedFactsKeepExactValuesAndOwners() {
    // Arrange
    var request = request(SOURCE);

    // Act
    var outcome = JavaFrontends.standard().produce(request);
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    for (var result : List.of(original, decoded)) {
      assertFacts(result);
    }
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    assertEquals(AstCodec.materialize(original), AstCodec.materialize(decoded));
  }

  @Test
  void givenClosedMixedSource_whenApplicationDerivesSupportedAndRefusedMethods_thenSourceQualificationStaysIndependent() {
    // Arrange
    var runner = AnalysisCapabilities.computation(JavaFrontends.standard(), CfgDerivers.standard());
    var input = request(SOURCE);

    // Act
    var astOnly = runner.analyze(new AnalysisComputationRequest(
        new AstProductionRequest(input.input(), input.configuration(), input.profile()), Optional.empty()));
    var original = astOnly.representation().ast().result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var selected = List.of(typeKey("Sample") + "#supported(int)", typeKey("Sample") + "#refused(int)");
    var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(AstOutcome.available(decoded),
        new CfgRequest(decoded.identity(), selected, CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, astOnly.completion());
    assertTrue(astOnly.representation().cfg().isEmpty());
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.UNSUPPORTED),
        run.representation().cfg().orElseThrow().methods().stream().map(method -> method.state()).toList());
    var graphs = run.representation().cfg().orElseThrow().methods();
    assertTrue(graphs.getFirst().graph().isPresent());
    assertTrue(graphs.getLast().graph().isEmpty());
    var lambda = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION)
        .findFirst().orElseThrow();
    assertEquals(List.of(Optional.of(new SourceOccurrence(decoded.identity(), lambda.nodeId()))),
        graphs.getLast().blockers().stream().map(blocker -> blocker.occurrence()).toList());
    assertFacts(run.representation().ast().result().orElseThrow());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(run.representation().ast().result().orElseThrow()));
  }

  @Test
  void givenMixedSourceAndSuppliedDriverRecords_whenExplicitlyRetainedAndQueried_thenRealAdapterReconstructsExactClosedFacts() {
    // Arrange
    var outcome = produce();
    var identity = outcome.result().orElseThrow().identity();
    var write = new AstRepositoryDriverStub();
    var retention = AnalysisCapabilities.astRetention(context ->
        new Neo4jAstResultRepository(write.driver(), SessionConfig.defaultConfig(), context));
    var read = new AstRepositoryDriverStub();
    read.stored = new Neo4jAstResultCodec().map(outcome);
    var lookupService = AnalysisCapabilities.astQuery(context ->
        new Neo4jAstResultRepository(read.driver(), SessionConfig.defaultConfig(), context));

    // Act
    var publication = retention.retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
    var lookup = lookupService.findAst(identity);

    // Assert
    assertEquals(PublicationStatus.SAVED, publication.status());
    assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
    assertTrue(publication.failure().isEmpty());
    assertEquals(List.of("session.open:0", "session.close:0", "session.open:1", "write.begin", "callback.return",
        "write.acknowledged", "session.close:1", "driver.close"), write.events);
    assertEquals(AstLookupStatus.FOUND, lookup.status());
    assertEquals(identity, lookup.requestedIdentity());
    var retained = lookup.outcome().orElseThrow().result().orElseThrow();
    assertFacts(retained);
    assertArrayEquals(AstCodec.encode(outcome.result().orElseThrow()), AstCodec.encode(retained));
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), read.events);
  }

  @Test
  void givenCompleteMixedSourceWithoutSaveIntent_whenRetentionIsRequested_thenNoStorageIsAcquired() {
    // Arrange
    var outcome = produce();
    var retention = AnalysisCapabilities.astRetention(context -> {
      throw new AssertionError("No-save must not acquire storage");
    });

    // Act
    var publication = retention.retainAst(new AstPublicationRequest(outcome, PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertEquals(PublicationStatus.NOT_REQUESTED, publication.status());
    assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
    assertFacts(outcome.result().orElseThrow());
  }

  @Test
  void givenMixedSourceAndDriverReadFailure_whenApplicationQueries_thenExactCauseAndResourceClosureSurvive() {
    // Arrange
    var outcome = produce();
    var driver = new AstRepositoryDriverStub();
    var cause = new Neo4jException("Controlled driver read failure");
    driver.readFailure = cause;
    var retention = AnalysisCapabilities.astQuery(context ->
        new Neo4jAstResultRepository(driver.driver(), SessionConfig.defaultConfig(), context));

    // Act
    var failure = assertThrows(Neo4jRepositoryOperationException.class,
        () -> retention.findAst(outcome.result().orElseThrow().identity()));

    // Assert
    assertEquals(Neo4jRepositoryPhase.READ, failure.phase());
    assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
    assertSame(cause, failure.effectivePrimary().getCause());
    assertEquals(outcome.result().orElseThrow().identity(), ((Ast) failure.context().subject()).identity());
    assertEquals(List.of("session.open:0", "session.close:0", "driver.close"), driver.events);
    assertFacts(outcome.result().orElseThrow());
  }

  @Test
  void givenAnotherCaptureIdentity_whenDecodingOrDerivingMixedSource_thenMismatchCannotRequalifyTheOriginal() {
    // Arrange
    var original = produce().result().orElseThrow();
    var other = JavaFrontends.standard().produce(request(SOURCE.replace("0x1_0L", "0x2_0L"))).result().orElseThrow();
    var runner = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard());

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstCodec.decode(AstCodec.encode(original), other.identity()));
    var run = runner.deriveExisting(AstOutcome.available(original), new CfgRequest(other.identity(),
        List.of(typeKey("Sample") + "#supported(int)"), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertTrue(failure.getMessage().contains("identity differs"), failure.toString());
    assertNotEquals(original.identity(), other.identity());
    assertEquals(AnalysisCompletion.REJECTED, run.completion());
    assertFacts(run.representation().ast().result().orElseThrow());
    assertTrue(run.representation().cfg().orElseThrow().methods().stream().allMatch(method -> method.graph().isEmpty()));
  }

  @TestFactory
  Stream<DynamicTest> givenCompilerInvalidNeighbors_whenMixedSourceIsProduced_thenSpecificErrorsRetainAvailableFactsAndRefusePublication() {
    // Arrange
    var variants = List.of(
        new InvalidSource("ContextualName", "class var {}", "compiler.err.restricted.type.not.allowed"),
        new InvalidSource("NumericBoundary", "class Invalid { int value = 1_; }", "compiler.err.illegal.underscore"),
        new InvalidSource("Reachability", "class Invalid { void bad() { return; int dead = 1; } }", "compiler.err.unreachable.stmt"),
        new InvalidSource("AnnotationMixture", "@Tag(1) @Tag(2) @Tags({@Tag(3)}) class Invalid {}",
            "compiler.err.invalid.repeatable.annotation.repeated.and.container.present"));

    // Act
    var observations = variants.stream().map(variant -> DynamicTest.dynamicTest(
        "given" + variant.name() + "BesideMixedSource_whenProduced_thenSpecificErrorAndValidNeighborSurvive", () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Sample.java")), SOURCE),
              SourceFile.fromContent(capturedPath(Path.of("Invalid.java")), variant.source())));
          var retention = AnalysisCapabilities.astRetention(context -> {
            throw new AssertionError("Incomplete source must not acquire storage");
          });

          // Act
          var outcome = JavaFrontends.standard().produce(new AstProductionRequest(snapshot,
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
          var original = outcome.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
          var publication = retention.retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
          var errors = outcome.diagnostics().stream().filter(value -> value.code().startsWith("compiler.err.")).toList();
          assertEquals(List.of(variant.code()), errors.stream().map(value -> value.code()).toList(), errors.toString());
          assertEquals(DiagnosticSeverity.ERROR, errors.getFirst().severity());
          assertEquals(DiagnosticStage.FRONTEND, errors.getFirst().stage());
          assertFalse(errors.getFirst().origins().isEmpty());
          for (var ast : List.of(original, decoded)) {
            assertEquals(AstCompletion.INCOMPLETE, ast.completion());
            assertEquals(outcome.diagnostics(), ast.diagnostics());
            assertNeighborFacts(ast);
          }
          assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
          assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
        }));

    // Assert
    return observations;
  }

  private AstOutcome produce() {
    return JavaFrontends.standard().produce(request(SOURCE));
  }

  private AstProductionRequest annotatedRequest(Optional<SourceFile> neighbor) {
    var files = new ArrayList<>(List.of(
        SourceFile.fromContent(capturedPath(Path.of("Annotations.java")), ANNOTATIONS),
        SourceFile.fromContent(capturedPath(Path.of("Bundle.java")), ANNOTATED_RECORD),
        SourceFile.fromContent(capturedPath(Path.of("Sample.java")), ANNOTATED_DECLARATIONS)));
    neighbor.ifPresent(files::add);
    return new AstProductionRequest(InputSnapshot.fromSources(capturedAbsolute(directory), files),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static void assertAnnotatedTargets(AstResult ast) {
    var aggregate = ast.aggregate();
    for (var names : List.of(List.of("first", "second"), List.of("localFirst", "localSecond"))) {
      var first = namedVariable(ast, names.getFirst());
      var second = namedVariable(ast, names.getLast());
      var annotation = first.requiredChild(SyntaxRole.ANNOTATION);
      assertEquals(new AnnotationApplication(first.nodeId(), Optional.of(first.nodeId()),
          Optional.of(new AnnotationTypeLocation.Written(first.requiredChild(SyntaxRole.DECLARED_TYPE).nodeId())), List.of(),
          List.of(new FieldDeclaratorApplication(second.nodeId(), true,
              Optional.of(new AnnotationTypeLocation.Written(second.requiredChild(SyntaxRole.DECLARED_TYPE).nodeId()))))),
          annotationApplication(ast, annotation));
      assertTrue(second.children(SyntaxRole.ANNOTATION).isEmpty());
      assertSame(annotation, ast.node(new SourceOccurrence(ast.identity(), annotation.nodeId())).orElseThrow());
    }
    var recordFile = ast.snapshot().sourceFiles().stream().filter(file -> file.canonicalRelativePath().equals("Bundle.java"))
        .findFirst().orElseThrow();
    var components = aggregate.nodes().stream().filter(node -> node.sourceFileKey().equals(recordFile.sourceFileKey())
        && node.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION).toList();
    assertEquals(1, components.size());
    var component = components.getFirst();
    assertEquals("value", component.value(SyntaxValue.RecordComponentDeclaration.class).name());
    var annotations = component.requiredChild(SyntaxRole.DECLARED_TYPE).descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION).toList();
    assertEquals(2, annotations.size());
    for (var value : List.of(1, 2)) {
      var spelling = "@Use(" + value + ")";
      var start = ANNOTATED_RECORD.indexOf(spelling);
      var annotation = annotations.stream().filter(node -> node.origin().equals(Optional.of(
          new SourceSpan(recordFile.sourceFileKey(), start, start + spelling.length())))).findFirst().orElseThrow();
      assertEquals(new AnnotationScalar.IntValue(value), annotation.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT)
          .value(SyntaxValue.AnnotationArgument.class).value());
      var owner = component.requiredChild(SyntaxRole.DECLARED_TYPE).descendantsAndSelf().stream()
          .filter(node -> node.children(SyntaxRole.ANNOTATION).contains(annotation)).findFirst().orElseThrow();
      var application = annotationApplication(ast, annotation);
      assertEquals(owner.nodeId(), application.ownerNodeKey());
      assertEquals(Optional.empty(), application.declarationNodeKey());
      assertEquals(Optional.of(new AnnotationTypeLocation.Written(owner.nodeId())), application.typeLocation());
      assertTrue(application.fieldDeclarators().isEmpty());
      var roles = owner.structuralPath().steps().stream().map(step -> step.role()).toList();
      assertTrue(roles.containsAll(List.of(SyntaxRole.RECORD_COMPONENT, SyntaxRole.TYPE_ARGUMENT,
          SyntaxRole.UPPER_BOUND, SyntaxRole.LOWER_BOUND)));
      assertEquals(value == 1, roles.contains(SyntaxRole.COMPONENT_TYPE));
      assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER),
          application.propagatedCounterparts().stream().map(counterpart -> counterpart.reference().referenceKind()).toList());
      assertEquals(List.of(typeKey("Bundle") + "#field:value", typeKey("Bundle") + "#value()",
          typeKey("Bundle") + "#<init>(java.util.List):parameter:0"),
          application.propagatedCounterparts().stream().map(counterpart -> counterpart.reference().symbolKey().orElseThrow()).toList());
      for (var counterpart : application.propagatedCounterparts()) {
        assertFalse(counterpart.declarationApplicable());
        assertEquals(Optional.of(new AnnotationTypeLocation.Written(owner.nodeId())), counterpart.typeLocation());
      }
      assertSame(owner, ast.node(new SourceOccurrence(ast.identity(), owner.nodeId())).orElseThrow());
    }
  }

  private static SyntaxNode namedVariable(AstResult ast, String name) {
    var matches = ast.aggregate().nodes().stream().filter(node -> switch (node.value()) {
      case SyntaxValue.VariableDeclaration field -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION && field.name().equals(name);
      case SyntaxValue.LocalDeclaration local -> local.name().equals(name);
      default -> false;
    }).toList();
    assertEquals(1, matches.size(), name);
    return matches.getFirst();
  }

  private static AnnotationApplication annotationApplication(AstResult ast, SyntaxNode annotation) {
    var edges = ast.aggregate().semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).toList();
    assertEquals(1, edges.size());
    return assertInstanceOf(AnnotationApplication.class, edges.getFirst().target());
  }

  private AstProductionRequest request(String source) {
    return new AstProductionRequest(InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Sample.java")), source))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static void assertFacts(AstResult ast) {
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertTrue(ast.diagnostics().isEmpty(), ast.diagnostics().toString());
    assertEquals(AstProfile.JAVA_SOURCE_MODULE_SERVICES, ast.profile());
    assertEquals("java-source-expanded/8", ast.identity().astProfileVersion());
    assertEquals("javac-public/14", ast.identity().semanticProductionRevision());
    assertEquals("java-source/23", ast.identity().sourceModelVersion());
    assertNeighborFacts(ast);
  }

  private static void assertNeighborFacts(AstResult ast) {
    var file = ast.snapshot().sourceFiles().stream().filter(value -> value.canonicalRelativePath().equals("Sample.java"))
        .findFirst().orElseThrow();
    assertEquals(SOURCE, file.content());
    var sample = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration value
        && value.declarationKey().equals(typeKey("Sample"))).findFirst().orElseThrow();
    var root = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration value
        && value.declarationKey().equals(typeKey("Root"))).findFirst().orElseThrow();
    assertTrue(root.children(SyntaxRole.PERMITTED_SUBCLASS).isEmpty());
    assertEquals(List.of(typeKey("Leaf")), ast.program().types().stream().filter(type -> type.typeKey().equals(typeKey("Root")))
        .findFirst().orElseThrow().permittedSubclasses().stream().map(type -> type.declarationKey().orElseThrow()).toList());
    var members = sample.children(SyntaxRole.MEMBER);
    assertEquals(List.of(SyntaxKind.STATIC_INITIALIZER_BLOCK, SyntaxKind.INSTANCE_INITIALIZER_BLOCK, SyntaxKind.METHOD_DECLARATION),
        members.subList(0, 3).stream().map(SyntaxNode::syntaxKind).toList());
    var scopes = ast.aggregate().executionScopes().stream().filter(ExecutionScope.Initializer.class::isInstance)
        .map(ExecutionScope.Initializer.class::cast).filter(scope -> scope.ownerKey().equals(typeKey("Sample"))).toList();
    assertEquals(2, scopes.size());
    assertNotEquals(scopes.getFirst().key(), scopes.getLast().key());
    for (var index = 0; index < 2; index++) {
      var block = members.get(index);
      assertTrue(block.children(SyntaxRole.STATEMENT).isEmpty());
      var scope = scopes.stream().filter(value -> value.origin().equals(block.origin().orElseThrow())).findFirst().orElseThrow();
      assertEquals(block, ast.aggregate().executionSyntax(scope.key()).orElseThrow().root());
      assertTrue(ast.aggregate().methodSyntax(scope.key()).isEmpty());
    }
    assertLiteral(ast, "0x1_0L", "16", "long");
    assertLiteral(ast, "0b11", "3", "int");
    assertLiteral(ast, "0_7", "7", "int");
    assertLiteral(ast, "\"line\\n\\141\"", "line\na", "java.lang.String");
    var body = ast.aggregate().methodSyntax(typeKey("Sample") + "#combine(int,java.lang.Integer,java.lang.Object,java.lang.Object)").orElseThrow();
    var addition = at(ast, "selected + boxed", SyntaxKind.BINARY);
    assertEquals(SyntaxOperator.PLUS, addition.value(SyntaxValue.Operator.class).operator());
    assertEquals("long", addition.resolvedType().orElseThrow().erasedName());
    assertEquals(List.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND), addition.children().stream().map(child -> child.role()).toList());
    var use = body.useDecision(addition.nodeId(), SyntaxRole.RIGHT_OPERAND, 0).orElseThrow();
    assertEquals("java.lang.Integer", use.sourceType().erasedName());
    assertEquals("long", use.selectedTarget().erasedName());
    assertEquals("int", use.unboxedType().orElseThrow().erasedName());
    assertEquals("long", use.effectivePrimitiveType().orElseThrow().erasedName());
    var binding = body.root().descendantsAndSelf().stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration value
        && value.name().equals("selected")).findFirst().orElseThrow();
    var selectedKey = binding.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow();
    var assignments = List.of(at(ast, "selected = 0b11", SyntaxKind.ASSIGNMENT), at(ast, "selected = 0_7", SyntaxKind.ASSIGNMENT));
    assertNotEquals(assignments.getFirst().structuralPath(), assignments.getLast().structuralPath());
    for (var assignment : assignments) {
      assertEquals(Optional.of(selectedKey), assignment.requiredChild(SyntaxRole.LOCATION).requiredReference().symbolKey());
    }
    assertEquals(Optional.of(selectedKey), addition.requiredChild(SyntaxRole.LEFT_OPERAND).requiredReference().symbolKey());
    var first = local(ast, "first");
    var second = local(ast, "second");
    assertNotEquals(first.nodeId(), second.nodeId());
    assertNotEquals(first.requiredReference().symbolKey(), second.requiredReference().symbolKey());
    for (var owner : List.of(first, second)) {
      var annotation = owner.requiredChild(SyntaxRole.ANNOTATION);
      var edges = ast.aggregate().semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
          && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).toList();
      assertEquals(1, edges.size());
      var application = assertInstanceOf(AnnotationApplication.class, edges.getFirst().target());
      assertEquals(owner.nodeId(), application.ownerNodeKey());
      assertTrue(application.declarationNodeKey().isEmpty());
      assertEquals(Optional.of(new AnnotationTypeLocation.Written(owner.requiredChild(SyntaxRole.DECLARED_TYPE).nodeId())),
          application.typeLocation());
      assertEquals(owner.structuralPath().child(SyntaxRole.ANNOTATION, 0), annotation.structuralPath());
    }
    var containers = sample.children(SyntaxRole.ANNOTATION);
    assertEquals(2, containers.size());
    assertNotEquals(containers.getFirst().nodeId(), containers.getLast().nodeId());
    for (var index = 0; index < 2; index++) {
      var annotation = containers.get(index);
      var argument = annotation.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT);
      var values = argument.requiredChild(SyntaxRole.VALUE).children(SyntaxRole.VALUE);
      var expected = index == 0 ? List.of(11) : List.of(22, 33);
      assertEquals(expected.size(), values.size());
      assertEquals(new AnnotationElementValue.Array(expected.stream().<AnnotationElementValue>map(ignored ->
          new AnnotationElementValue.Nested(typeKey("Tag"))).toList()), argument.value(SyntaxValue.AnnotationArgument.class).value());
      assertEquals(expected.stream().map(AnnotationScalar.IntValue::new).toList(), values.stream()
          .map(value -> value.requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).value(SyntaxValue.AnnotationArgument.class).value()).toList());
      assertEquals(sample.structuralPath().child(SyntaxRole.ANNOTATION, index), annotation.structuralPath());
    }
  }

  private static void assertLiteral(AstResult ast, String spelling, String expected, String type) {
    var node = at(ast, spelling, SyntaxKind.LITERAL);
    assertEquals(expected, node.value(SyntaxValue.Literal.class).text());
    assertEquals(type, node.resolvedType().orElseThrow().erasedName());
    assertSame(node, ast.node(new SourceOccurrence(ast.identity(), node.nodeId())).orElseThrow());
  }

  private static SyntaxNode at(AstResult ast, String spelling, SyntaxKind kind) {
    var start = SOURCE.indexOf(spelling);
    assertTrue(start >= 0, spelling);
    var fileKey = ast.snapshot().sourceFiles().stream().filter(file -> file.canonicalRelativePath().equals("Sample.java"))
        .findFirst().orElseThrow().sourceFileKey();
    var matches = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind
        && node.origin().equals(Optional.of(new SourceSpan(fileKey, start, start + spelling.length())))).toList();
    assertEquals(1, matches.size(), spelling + " " + kind);
    return matches.getFirst();
  }

  private static String typeKey(String name) {
    return new JavaTypeDeclarationIdentity(Optional.empty(), name).declarationKey();
  }

  private static SyntaxNode local(AstResult ast, String name) {
    var matches = ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration value
        && value.name().equals(name)).toList();
    assertEquals(1, matches.size(), name);
    return matches.getFirst();
  }

  private static String mixedTerminators(String source) {
    var lines = source.split("\n", -1);
    var terminators = List.of("\n", "\r", "\r\n");
    var result = new StringBuilder();
    for (var index = 0; index < lines.length; index++) {
      if (index > 0) result.append(terminators.get((index - 1) % terminators.size()));
      result.append(lines[index]);
    }
    return result.toString();
  }

  private record InvalidSource(String name, String source, String code) {}
}
