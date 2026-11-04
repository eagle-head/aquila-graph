package org.aquilagraph.repository.internal.neo4j;

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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;
import org.aquilagraph.application.SourceAnalysisRunner;
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
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
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
  void givenCorrectedLexicalSource_whenApplicationRetainsAndQueriesThroughAdapter_thenExactFactsSurviveCfgRefusal() {
    // Arrange
    var spelling = "tr" + "\\" + "u0000ue";
    var source = "class Box { <T> Box(T x) {} " + "\\" + "u007d class Sample { "
        + "Object make() { return new Box(1); } String literal() { return \"prefix\" + " + spelling + "; } "
        + "int refused() { java.util.function.IntSupplier next = () -> 1; return next.getAsInt(); } }";
    var input = request(source);
    var runner = new SourceAnalysisRunner(JavaFrontends.standard(), CfgDerivers.standard());
    var write = new AstRepositoryDriverStub();
    var retention = new AstRetentionService(context ->
        new Neo4jAstResultRepository(write.driver(), SessionConfig.defaultConfig(), context));
    var noSave = new AstRetentionService(context -> {
      throw new AssertionError("No-save must not acquire storage");
    });

    // Act
    var analysis = runner.analyze(new AnalysisRequest(input.input(), input.configuration(), input.profile(),
        Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var outcome = analysis.representation().ast();
    var original = outcome.result().orElseThrow();
    var unrequested = noSave.retainAst(new AstPublicationRequest(outcome, PublicationIntent.DO_NOT_SAVE));
    var publication = retention.retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
    var read = new AstRepositoryDriverStub();
    read.stored = new Neo4jAstResultCodec().map(outcome);
    var lookup = new AstRetentionService(context ->
        new Neo4jAstResultRepository(read.driver(), SessionConfig.defaultConfig(), context)).findAst(original.identity());
    var retained = lookup.outcome().orElseThrow().result().orElseThrow();
    var refused = retained.program().methods().stream().filter(method -> method.name().equals("refused")).findFirst().orElseThrow();
    var derived = runner.deriveExisting(AstOutcome.available(retained),
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
    var runner = new SourceAnalysisRunner(JavaFrontends.standard(), CfgDerivers.standard());
    var input = request(SOURCE);

    // Act
    var astOnly = runner.analyze(new AnalysisRequest(input.input(), input.configuration(), input.profile(),
        Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var original = astOnly.representation().ast().result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var selected = List.of(typeKey("Sample") + "#supported(int)", typeKey("Sample") + "#refused(int)");
    var run = runner.deriveExisting(AstOutcome.available(decoded),
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
    var retention = new AstRetentionService(context ->
        new Neo4jAstResultRepository(write.driver(), SessionConfig.defaultConfig(), context));
    var read = new AstRepositoryDriverStub();
    read.stored = new Neo4jAstResultCodec().map(outcome);
    var lookupService = new AstRetentionService(context ->
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
    var retention = new AstRetentionService(context -> {
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
    var retention = new AstRetentionService(context ->
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
    var runner = new SourceAnalysisRunner(JavaFrontends.standard(), CfgDerivers.standard());

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
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Sample.java"), SOURCE),
              SourceFile.fromContent(Path.of("Invalid.java"), variant.source())));
          var retention = new AstRetentionService(context -> {
            throw new AssertionError("Incomplete source must not acquire storage");
          });

          // Act
          var outcome = JavaFrontends.standard().produce(new AstProductionRequest(snapshot,
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
          var original = outcome.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
          var publication = retention.retainAst(outcome);

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

  private AstProductionRequest request(String source) {
    return new AstProductionRequest(InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Sample.java"), source))),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static void assertFacts(AstResult ast) {
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertTrue(ast.diagnostics().isEmpty(), ast.diagnostics().toString());
    assertEquals(AstProfile.JAVA_SOURCE_MODULE_SERVICES, ast.profile());
    assertEquals("java-source-expanded/8", ast.identity().astProfileVersion());
    assertEquals("javac-public/14", ast.identity().semanticProductionRevision());
    assertEquals("java-source/22", ast.identity().sourceModelVersion());
    assertNeighborFacts(ast);
  }

  private static void assertNeighborFacts(AstResult ast) {
    var file = ast.snapshot().sourceFiles().stream().filter(value -> value.relativePath().equals(Path.of("Sample.java")))
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
    var fileKey = ast.snapshot().sourceFiles().stream().filter(file -> file.relativePath().equals(Path.of("Sample.java")))
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
