package org.aquilagraph.frontend.internal;

import org.aquilagraph.source.internal.SourceValueCapture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SourceExecutionTraversal;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;

class RecursiveDeclarationCompositionAstTest {
  @TempDir Path directory;

  @Test
  void givenMixedRecursiveOwners_whenProducedAndDecoded_thenExactChildrenFactsAndCanonicalOwnersRemain() throws IOException {
    // Arrange
    var source = mixedSource();

    // Act
    var original = produce(source);
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), details(original));
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.coverage(), retained.coverage());
    assertEquals(AstCodec.materialize(original), AstCodec.materialize(retained));
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
    assertFalse(retained.diagnostics().stream().anyMatch(value -> value.isError()));
    assertTrue(retained.coverage().validationEvidence().unmatchedFacts().isEmpty());
    assertCanonical(retained);
    var example = type(retained, "Example");
    assertEquals(List.of("seed", "chain", "action", "STATIC_INITIALIZER_BLOCK", "INSTANCE_INITIALIZER_BLOCK", "Example", "subject", "accept", "Choice"),
        example.children(SyntaxRole.MEMBER).stream().map(RecursiveDeclarationCompositionAstTest::memberName).toList());
    var chain = field(retained, "chain");
    assertEquals(SyntaxKind.LAMBDA_EXPRESSION, chain.requiredChild(SyntaxRole.INITIALIZER).syntaxKind());
    var nestedLambda = chain.requiredChild(SyntaxRole.INITIALIZER).requiredChild(SyntaxRole.BODY);
    assertEquals(SyntaxKind.LAMBDA_EXPRESSION, nestedLambda.syntaxKind());
    assertEquals(List.of("seed", "Math.abs(-2)"), nestedLambda.requiredChild(SyntaxRole.BODY).children(SyntaxRole.ARRAY_ELEMENT).stream()
        .map(node -> written(node, source)).toList());
    var seed = field(retained, "seed");
    var seedScope = retained.aggregate().executionSyntax(seed.value(SyntaxValue.VariableDeclaration.class).declarationKey()).orElseThrow();
    assertEquals("int", seedScope.useEvidence(seed.nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().displayName());
    var call = seed.requiredChild(SyntaxRole.INITIALIZER);
    assertEquals("int", seedScope.useEvidence(call.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
    assertTrue(seedScope.invocationEvidence(call.nodeId()).isPresent());
    var choice = type(retained, "Choice");
    var constant = choice.children(SyntaxRole.MEMBER).getFirst();
    assertEquals("FIRST", memberName(constant));
    assertEquals(List.of(SyntaxKind.LAMBDA_EXPRESSION, SyntaxKind.ARRAY_CREATION, SyntaxKind.INVOCATION),
        constant.children(SyntaxRole.ARGUMENT).stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(List.of("() -> new int[]{1, 2}", "new int[]{3, 4}", "new Object() { int value = Math.abs(-5); }.hashCode()"),
        constant.children(SyntaxRole.ARGUMENT).stream().map(node -> written(node, source)).toList());
    var enumScope = retained.aggregate().executionSyntax(constant.value(SyntaxValue.VariableDeclaration.class).declarationKey()).orElseThrow();
    assertTrue(enumScope.scope() instanceof ExecutionScope.EnumConstant);
    assertEquals(List.of("java.util.function.Supplier<int[]>", "int[]", "int"),
        List.of(0, 1, 2).stream().map(index -> enumScope.useEvidence(constant.nodeId(), SyntaxRole.ARGUMENT, index).orElseThrow().selectedTarget().displayName()).toList());
    assertEquals(List.of("count", "INSTANCE_INITIALIZER_BLOCK", "value"), constant.requiredChild(SyntaxRole.CLASS_BODY)
        .children(SyntaxRole.MEMBER).stream().map(RecursiveDeclarationCompositionAstTest::memberName).toList());
    for (var scope : retained.aggregate().executionScopes()) {
      if (scope instanceof ExecutionScope.Method && !retained.method(scope.key()).orElseThrow().sourceDeclared()) {
        continue;
      }
      assertSame(retained.aggregate().node(retained.aggregate().executionSyntax(scope.key()).orElseThrow().root().nodeId()).orElseThrow(),
          retained.aggregate().executionSyntax(scope.key()).orElseThrow().root());
      if (!(scope instanceof ExecutionScope.Method)) {
        assertTrue(retained.method(scope.key()).isEmpty());
        assertTrue(retained.body(scope.key()).isEmpty());
      }
      if (scope instanceof ExecutionScope.Field) {
        var execution = retained.aggregate().executionSyntax(scope.key()).orElseThrow();
        var name = execution.root().value(SyntaxValue.VariableDeclaration.class).name();
        var targets = Map.of("seed", "int", "chain", "java.util.function.Supplier", "action", "java.lang.Runnable",
            "nested", "java.lang.Object", "reference", "java.util.function.IntUnaryOperator", "number", "int",
            "values", "int[]", "value", "int", "count", "int");
        assertEquals(targets.get(name), execution.useEvidence(execution.root().nodeId(), SyntaxRole.INITIALIZER, 0)
            .orElseThrow().selectedTarget().erasedName(), scope.key());
      }
    }
    for (var kind : List.of(SyntaxKind.FOR_LOOP, SyntaxKind.WHILE_LOOP, SyntaxKind.DO_WHILE_LOOP, SyntaxKind.ENHANCED_FOR_LOOP,
        SyntaxKind.BREAK, SyntaxKind.CONTINUE, SyntaxKind.ASSERT, SyntaxKind.SYNCHRONIZED, SyntaxKind.COMPOUND_ASSIGNMENT,
        SyntaxKind.UPDATE, SyntaxKind.MEMBER_REFERENCE, SyntaxKind.SWITCH_EXPRESSION, SyntaxKind.SWITCH_STATEMENT,
        SyntaxKind.INSTANCE_OF, SyntaxKind.BINDING_PATTERN, SyntaxKind.TRY, SyntaxKind.CATCH)) {
      assertTrue(retained.aggregate().nodes().stream().anyMatch(node -> node.syntaxKind() == kind), kind.toString());
    }
    var locals = retained.program().types().stream().filter(value -> value.nesting().kind() == JavaTypeNesting.Kind.LOCAL)
        .sorted(Comparator.comparingLong(value -> value.origins().getFirst().startOffset())).toList();
    assertEquals(List.of("Local", "Deeper", "Local", "Local", "LocalChoice", "Local", "Local", "Local"),
        locals.stream().map(value -> retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration declaration
            && declaration.declarationKey().equals(value.typeKey())).findFirst().orElseThrow().value(SyntaxValue.TypeDeclaration.class).name()).toList());
    assertEquals(locals.size(), locals.stream().map(value -> value.typeKey()).distinct().count());
    assertNotEquals(locals.get(5).typeKey(), locals.get(6).typeKey());
    assertEquals(locals.get(5).nesting().lexicalOwnerKey(), locals.get(6).nesting().lexicalOwnerKey());
    for (var local : locals) {
      var declaration = retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration value
          && value.declarationKey().equals(local.typeKey())).findFirst().orElseThrow();
      var scope = retained.aggregate().executionScopes().stream().filter(value -> retained.aggregate().executionSyntax(value.key()).orElseThrow()
          .root().descendantsAndSelf().contains(declaration)).max(Comparator.comparingInt(value -> retained.aggregate().executionSyntax(value.key())
              .orElseThrow().root().structuralPath().steps().size())).orElseThrow();
      var owner = local.nesting().lexicalOwnerKey().orElseThrow();
      var compilerOwner = local.nesting().ownerDetails().orElseThrow();
      if (compilerOwner.kind().equals("CONSTRUCTOR") || compilerOwner.kind().equals("METHOD")) {
        var method = retained.method(owner).orElseThrow();
        assertEquals(compilerOwner.kind().equals("CONSTRUCTOR"), method.constructor());
      } else {
        assertEquals(scope.key(), owner);
      }
      if (local == locals.getFirst()) {
        assertEquals("type:Example#field:action", scope.key());
        assertEquals("type:Example#<init>()", owner);
        assertEquals("CONSTRUCTOR", compilerOwner.kind());
        var annotation = declaration.children(SyntaxRole.ANNOTATION).getFirst();
        assertEquals("@Deprecated", written(annotation, source));
        var applications = retained.aggregate().semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
            && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).map(edge -> (AnnotationApplication) edge.target()).toList();
        assertEquals(List.of(Optional.of(declaration.nodeId())), applications.stream().map(AnnotationApplication::declarationNodeKey).toList());
      }
    }
  }

  @Test
  void givenCompactMembersWithRecursiveDeclarations_whenProducedAndDecoded_thenWrittenMembersKeepUnitAttachment() throws IOException {
    // Arrange
    var source = """
        java.util.function.Supplier<int[]> field = () -> new int[]{1, 2};
        void main() {
          class Local { Object value = new Object() { int nested() { return 3; } }; }
          new Local();
        }
        """;

    // Act
    var original = produce(source);
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
    assertEquals(List.of("field", "main"), retained.aggregate().roots().getFirst().children(SyntaxRole.COMPACT_MEMBER).stream()
        .map(RecursiveDeclarationCompositionAstTest::memberName).toList());
    assertEquals("3", retained.body(retained.program().methods().stream().filter(value -> value.name().equals("nested"))
        .findFirst().orElseThrow().methodKey()).orElseThrow().root().children(SyntaxRole.STATEMENT).getFirst()
        .requiredChild(SyntaxRole.EXPRESSION).value(SyntaxValue.Literal.class).text());
    assertCanonical(retained);
  }

  @Test
  void givenNestedScopesWithRequiredAndOptionalFactLoss_whenReadmitted_thenOnlyRequiredLossMakesSourceIncomplete() throws IOException {
    // Arrange
    var original = produce(mixedSource());
    var values = SourceValueCapture.capture(original.aggregate());
    var requiredScopes = values.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(), scope.root(),
        scope.uses().stream().filter(use -> use.role() != SyntaxRole.ARGUMENT).toList(), scope.invocations(), scope.staticAccesses(),
        scope.concatenations(), scope.attributedOccurrences())).toList();
    var optionalScopes = values.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(), scope.root(), scope.uses(),
        scope.invocations().stream().map(value -> new InvocationEvidence(EvidenceValue.missing(value.applicability().origin(),
            "Optional invocation classification unavailable"))).toList(), scope.staticAccesses(), scope.concatenations(), scope.attributedOccurrences())).toList();

    // Act
    var required = qualify(original, withScopes(values, requiredScopes));
    var optional = qualify(original, withScopes(values, optionalScopes));
    var retained = AstCodec.decode(AstCodec.encode(optional), optional.identity());

    // Assert
    assertTrue(values.scopes().stream().filter(scope -> scope.scope().key().contains(":type:"))
        .anyMatch(scope -> scope.uses().stream().anyMatch(use -> use.role() == SyntaxRole.ARGUMENT)));
    assertEquals(AstCompletion.INCOMPLETE, required.completion());
    assertEquals(original.aggregate().nodes(), required.aggregate().nodes());
    assertEquals(AstCompletion.COMPLETE, optional.completion(), details(optional));
    assertEquals(optional.aggregate(), retained.aggregate());
    assertTrue(retained.aggregate().executionScopes().stream().filter(scope -> !(scope instanceof ExecutionScope.Method))
        .flatMap(scope -> retained.aggregate().executionSyntax(scope.key()).orElseThrow().evidence().invocations().stream())
        .allMatch(value -> value.applicability().knownValue().isEmpty()));
  }

  @Test
  void givenDuplicateAndForeignNestedAttachments_whenCopiedValuesAreAdmitted_thenIntrinsicOwnerBoundariesRejectBoth() throws IOException {
    // Arrange
    var original = produce(mixedSource());
    var values = SourceValueCapture.capture(original.aggregate());
    var sources = new ArrayList<>(values.sources());
    sources.add(values.sources().getFirst());
    var duplicate = new CopiedAstValues(values.snapshot(), sources, values.bodies(), values.memberOrders(), values.associations(),
        values.declarationTypes(), values.scopes());
    var fieldScope = values.scopes().stream().filter(scope -> scope.scope() instanceof ExecutionScope.Field && scope.scope().key().contains(":type:"))
        .findFirst().orElseThrow();
    var foreignScope = new CopiedAstValues.Scope(new ExecutionScope.Field("type:Foreign#field:value", "type:Foreign"), fieldScope.root(),
        fieldScope.uses(), fieldScope.invocations(), fieldScope.staticAccesses(), fieldScope.concatenations(), fieldScope.attributedOccurrences());
    var foreign = withScopes(values, values.scopes().stream().map(scope -> scope == fieldScope ? foreignScope : scope).toList());
    var duplicateScopes = new ArrayList<>(values.scopes());
    duplicateScopes.add(fieldScope);

    // Act
    var duplicateFailure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(duplicate));
    var foreignFailure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(foreign));
    var scopeFailure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(withScopes(values, duplicateScopes)));

    // Assert
    assertFalse(duplicateFailure.getMessage().isBlank());
    assertTrue(foreignFailure.getCause() instanceof IllegalArgumentException, foreignFailure.toString());
    assertFalse(scopeFailure.getMessage().isBlank());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @Test
  void givenNestedArrayAndItsDerivedBookkeepingAreOmitted_whenOriginalCaptureIsReadmitted_thenIndependentExpectationDetectsMissingChildren() throws IOException {
    // Arrange
    var original = produce("class Example { Runnable action = () -> { class Local { int[] values = {1, 2}; } new Local(); }; }");
    var values = SourceValueCapture.capture(original.aggregate());
    var roots = values.sources().stream().map(source -> omitArrayElements(source.root())).toList();
    var nodes = roots.stream().flatMap(root -> root.descendantsAndSelf().stream()).collect(Collectors.toMap(SyntaxNode::nodeId, node -> node));
    var sources = roots.stream().map(root -> new SyntaxSourceContribution(root.sourceFileKey(), root, root.descendantsAndSelf(),
        root.descendantsAndSelf().stream().flatMap(node -> node.structuralEdges().stream()).toList(),
        values.sources().stream().flatMap(source -> source.semanticEdges().stream()).filter(edge -> nodes.containsKey(edge.sourceKey())).toList())).toList();
    var scopes = values.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(), nodes.get(scope.root().nodeId()),
        scope.uses().stream().filter(use -> nodes.containsKey(use.consumerNodeId()) && nodes.get(use.consumerNodeId()).children().stream()
            .anyMatch(child -> child.role() == use.role() && child.position() == use.position())).toList(),
        scope.invocations().stream().filter(value -> nodes.containsKey(value.consumerNodeId())).toList(),
        scope.staticAccesses().stream().filter(value -> nodes.containsKey(value.accessNodeId())).toList(), scope.concatenations(),
        scope.attributedOccurrences().stream().filter(value -> nodes.containsKey(value.nodeKey())).toList())).toList();
    var bodies = values.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), nodes.getOrDefault(body.root().nodeId(), body.root()), body.origin(),
        body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var supplied = AstValueAdmission.admit(new CopiedAstValues(values.snapshot(), sources, bodies, values.memberOrders(),
        values.associations().stream().filter(value -> nodes.containsKey(value.nodeKey())).toList(), values.declarationTypes(), scopes));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environment(),
        original.program(), Optional.of(supplied), original.diagnostics(), original.coverage()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(original.aggregate().nodes().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.LITERAL));
    assertFalse(supplied.nodes().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.LITERAL));
    assertTrue(failure.getMessage().contains("source") || failure.getMessage().contains("fact"), failure.toString());
  }

  @Test
  void givenMixedDeclarationsThroughApplication_whenCfgIsAbsentRefusedOrFails_thenRealSourceAndNestedSelectionRemainIndependent() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), mixedSource())));
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var runner = new SourceAnalysisRunner(frontend, CfgDerivers.standard());
    var cause = new IllegalStateException("Supplied CFG construction failure");

    // Act
    var absent = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_UNITS, Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var refused = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_UNITS, Optional.of(new CfgSelection(List.of("type:Example#subject(java.lang.Object)"), CfgProfile.JAVA_BOUNDED)),
        PublicationIntent.DO_NOT_SAVE));
    var ast = absent.representation().ast().result().orElseThrow();
    var nested = ast.program().methods().stream().filter(value -> value.name().equals("read")).findFirst().orElseThrow();
    var failureRunner = new SourceAnalysisRunner(frontend, RecoverableCfgFailures.atMethod(nested.methodKey(), cause));
    var failed = failureRunner.deriveExisting(absent.representation().ast(), new CfgRequest(ast.identity(), List.of(nested.methodKey()), CfgProfile.JAVA_BOUNDED));
    var selected = runner.deriveExisting(absent.representation().ast(), new CfgRequest(ast.identity(), List.of(nested.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), details(ast));
    assertTrue(absent.representation().cfg().isEmpty());
    assertEquals(MethodCfgState.UNSUPPORTED, refused.representation().cfg().orElseThrow().methods().getFirst().state());
    assertEquals(MethodCfgState.FAILED, failed.representation().cfg().orElseThrow().methods().getFirst().state());
    assertSame(cause, failed.representation().cfg().orElseThrow().methods().getFirst().failure().orElseThrow());
    assertEquals(MethodCfgState.AVAILABLE, selected.representation().cfg().orElseThrow().methods().getFirst().state());
    for (var actual : List.of(refused, failed, selected)) {
      assertEquals(ast.identity(), actual.representation().ast().result().orElseThrow().identity());
      assertArrayEquals(AstCodec.encode(ast), AstCodec.encode(actual.representation().ast().result().orElseThrow()));
    }
    assertFalse(ast.body(nested.methodKey()).orElseThrow().root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.FOR_LOOP));
  }

  @Test
  void givenInvalidNestedDeclarationPlacement_whenRealCompilerProduces_thenCompilerDiagnosticPreventsCompleteness() throws IOException {
    // Arrange
    var source = "class Example { Runnable action = () -> { static class Local {} }; }";

    // Act
    var ast = produce(source);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertTrue(ast.diagnostics().stream().anyMatch(value -> value.isError() && value.code().startsWith("compiler.")), ast.diagnostics().toString());
  }

  @TestFactory
  Stream<DynamicTest> givenExecutableFamiliesAcrossDeclarationContexts_whenProducedAndDecoded_thenCanonicalPayloadAndFactsSurvive() {
    // Arrange
    var payload = executablePayload();
    var worker = "class Worker { int mixed(Object input) {" + payload + "} }";
    var anonymous = "new Object() { int mixed(Object input) {" + payload + "} }";
    var cases = List.of(
        new CompositionCase("MethodLocal", "class Example { int outer() { " + worker + " return new Worker().mixed(\"x\"); } }"),
        new CompositionCase("ConstructorLocal", "class Example { Example() { " + worker + " new Worker().mixed(\"x\"); } }"),
        new CompositionCase("InstanceFieldLambda", "class Example { IntSupplier field = () -> { " + worker + " return new Worker().mixed(\"x\"); }; }"),
        new CompositionCase("StaticFieldLambda", "class Example { static IntSupplier field = () -> { " + worker + " return new Worker().mixed(\"x\"); }; }"),
        new CompositionCase("StaticBlockLocal", "class Example { static { " + worker + " new Worker().mixed(\"x\"); } }"),
        new CompositionCase("InstanceBlockLocal", "class Example { { " + worker + " new Worker().mixed(\"x\"); } }"),
        new CompositionCase("AnonymousInvocationArgument", "class Example { void accept(Object value) {} void outer() { accept(" + anonymous + "); } }"),
        new CompositionCase("EnumArgumentAnonymous", "enum Example { ONE(" + anonymous + "); Example(Object value) {} }"),
        new CompositionCase("EnumArgumentLambda", "enum Example { ONE(() -> { " + worker + " return new Worker().mixed(\"x\"); }); Example(IntSupplier value) {} }"),
        new CompositionCase("EnumConstantBody", "enum Example { ONE { int mixed(Object input) {" + payload + "} }; }"),
        new CompositionCase("LocalEnumConstantBody", "class Example { void outer() { enum Choice { ONE { int mixed(Object input) {" + payload + "} }; } } }"),
        new CompositionCase("CompactLocal", "void main() { " + worker + " new Worker().mixed(\"x\"); }"));

    // Act
    var observations = cases.stream().map(value -> DynamicTest.dynamicTest("given" + value.name()
        + "_whenProducedAndDecoded_thenCanonicalPayloadAndFactsSurvive", () -> {
          // Arrange
          var source = """
              import java.io.IOException;
              import java.io.StringReader;
              import java.util.function.IntSupplier;
              import java.util.function.IntUnaryOperator;
              """ + value.source();

          // Act
          var original = produce(source);
          var ast = AstCodec.decode(AstCodec.encode(original), original.identity());
          var method = ast.program().methods().stream().filter(item -> item.name().equals("mixed")).findFirst().orElseThrow();
          var body = ast.body(method.methodKey()).orElseThrow();

          // Assert
          assertEquals(AstCompletion.COMPLETE, ast.completion(), details(ast));
          assertEquals(List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.LOCAL_DECLARATION, SyntaxKind.LABELED_STATEMENT,
              SyntaxKind.WHILE_LOOP, SyntaxKind.DO_WHILE_LOOP, SyntaxKind.ENHANCED_FOR_LOOP, SyntaxKind.SYNCHRONIZED,
              SyntaxKind.TRY), body.root().children(SyntaxRole.STATEMENT).stream().map(SyntaxNode::syntaxKind).toList());
          assertEquals(List.of("1", "2"), body.root().children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.INITIALIZER)
              .children(SyntaxRole.ARRAY_ELEMENT).stream().map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
          assertEquals("Math::abs", written(body.root().children(SyntaxRole.STATEMENT).get(1).requiredChild(SyntaxRole.INITIALIZER), source));
          var attempt = body.root().children(SyntaxRole.STATEMENT).getLast();
          assertEquals(List.of(SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE, SyntaxRole.FINALLY_BLOCK),
              attempt.children().stream().map(SyntaxChild::role).toList());
          var call = body.root().descendantsAndSelf().stream().filter(node -> node.value() instanceof SyntaxValue.Invocation invocation
              && invocation.name().equals("applyAsInt")).findFirst().orElseThrow();
          assertEquals("int", body.useEvidence(call.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
          assertTrue(body.invocationEvidence(call.nodeId()).isPresent());
          assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN));
          assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION));
          assertCanonical(ast);
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(ast));
        }));

    // Assert
    return observations;
  }

  @Test
  void givenAnonymousArgumentWithDeferredLoop_whenSourceTraversalAndCfgAdmissionRun_thenOuterArgumentsRemainAndNestedExecutionDoesNotLeak() throws IOException {
    // Arrange
    var ast = produce("class Example { static int accept(Object value) { return 1; } int outer() { return accept(new Object() { int nested() { while (true) { return 2; } } }); } }");
    var outer = ast.program().methods().stream().filter(value -> value.name().equals("outer")).findFirst().orElseThrow();
    var nested = ast.program().methods().stream().filter(value -> value.name().equals("nested")).findFirst().orElseThrow();

    // Act
    var cfg = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), List.of(outer.methodKey(), nested.methodKey()), CfgProfile.JAVA_BOUNDED));
    var sourceNodes = ast.body(outer.methodKey()).orElseThrow().root().descendantsAndSelf();
    var executionNodes = SourceExecutionTraversal.nodes(ast.body(outer.methodKey()).orElseThrow().root());

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), details(ast));
    assertTrue(sourceNodes.stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.WHILE_LOOP));
    assertFalse(executionNodes.stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.WHILE_LOOP));
    assertTrue(executionNodes.stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION));
    assertTrue(executionNodes.stream().anyMatch(node -> node.value() instanceof SyntaxValue.Invocation value && value.name().equals("accept")));
    assertFalse(cfg.methods().getFirst().blockers().stream().flatMap(value -> value.occurrence().stream())
        .map(value -> ast.node(value).orElseThrow()).anyMatch(node -> node.syntaxKind() == SyntaxKind.WHILE_LOOP));
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getLast().state());
    assertTrue(cfg.methods().getLast().blockers().stream().flatMap(value -> value.occurrence().stream())
        .map(value -> ast.node(value).orElseThrow()).anyMatch(node -> node.syntaxKind() == SyntaxKind.WHILE_LOOP));
  }

  private record CompositionCase(String name, String source) {}

  private static String executablePayload() {
    return """
        int[] values = {1, 2};
        IntUnaryOperator operation = Math::abs;
        outer: for (int index = 0; index < values.length; index++) {
          values[index] += index;
          if (index == 0) continue outer;
          break outer;
        }
        while (values[0] < 2) { ++values[0]; }
        do { values[0]--; } while (values[0] > 3);
        for (int value : values) { values[0] ^= value; }
        synchronized (values) { assert values.length > 0 : "values"; }
        try (var reader = new StringReader("x")) {
          int selected = switch(input) { case String text when !text.isEmpty() -> text.length(); case null, default -> 0; };
          if (input instanceof String text) { selected += text.length(); }
          return operation.applyAsInt(selected + reader.read());
        } catch (IOException | IllegalArgumentException failure) { return 0; }
        finally { values[0]++; }
        """;
  }

  private static SyntaxNode omitArrayElements(SyntaxNode node) {
    var children = node.children().stream().filter(child -> child.role() != SyntaxRole.ARRAY_ELEMENT)
        .map(child -> new SyntaxChild(child.role(), child.position(), omitArrayElements(child.node()))).toList();
    return children.equals(node.children()) ? node : SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(),
        node.syntaxKind(), node.type(), node.value(), children, node.semanticReference(), node.origin().orElseThrow());
  }

  private static CopiedAstValues withScopes(CopiedAstValues values, List<CopiedAstValues.Scope> scopes) {
    return new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), values.associations(), values.declarationTypes(), scopes);
  }

  private static AstResult qualify(AstResult original, CopiedAstValues values) {
    var aggregate = AstValueAdmission.admit(values);
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
        original.diagnostics(), original.coverage());
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
  }

  private static void assertCanonical(AstResult retained) {
    var nodes = retained.aggregate().nodes();
    assertEquals(nodes.size(), nodes.stream().map(SyntaxNode::nodeId).distinct().count());
    for (var node : nodes) {
      assertSame(node, retained.node(new SourceOccurrence(retained.identity(), node.nodeId())).orElseThrow());
      assertEquals(retained.aggregate().roots().contains(node) ? 0 : 1,
          retained.aggregate().structuralEdges().stream().filter(edge -> edge.childKey().equals(node.nodeId())).count(), node.nodeId());
      for (var child : node.children()) {
        assertEquals(node.structuralPath().child(child.role(), child.position()), child.node().structuralPath());
      }
    }
    for (var method : retained.program().methods()) {
      var body = retained.body(method.methodKey());
      if (body.isPresent() && method.sourceDeclared()) {
        var declaration = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration value
            && value.declarationKey().equals(method.methodKey())).findFirst();
        assertTrue(declaration.isPresent(), method.toString());
        assertSame(declaration.orElseThrow().requiredChild(SyntaxRole.BODY), body.orElseThrow().root());
      }
      assertEquals(method, retained.method(method.methodKey()).orElseThrow());
    }
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
  }

  private static SyntaxNode type(AstResult ast, String name) {
    return ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration value && value.name().equals(name))
        .findFirst().orElseThrow();
  }

  private static SyntaxNode field(AstResult ast, String name) {
    return ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.VariableDeclaration value && value.name().equals(name))
        .findFirst().orElseThrow();
  }

  private static String memberName(SyntaxNode node) {
    return switch (node.value()) {
      case SyntaxValue.VariableDeclaration value -> value.name();
      case SyntaxValue.ExecutableDeclaration value -> value.name();
      case SyntaxValue.TypeDeclaration value -> value.name();
      default -> node.syntaxKind().name();
    };
  }

  private static String written(SyntaxNode node, String source) {
    var span = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
  }

  private static String details(AstResult ast) {
    return ast.diagnostics() + "\n" + ast.coverage().validationEvidence().outcomes().stream()
        .filter(value -> value.support().mode() == FeatureContextOutcome.Mode.FAILED).toList();
  }

  private static String mixedSource() {
    return """
        import java.io.IOException;
        import java.io.StringReader;
        import java.util.function.IntSupplier;
        import java.util.function.IntUnaryOperator;
        import java.util.function.Supplier;
        class Example {
          static int seed = Math.abs(-1);
          Supplier<Supplier<int[]>> chain = () -> () -> new int[]{seed, Math.abs(-2)};
          Runnable action = () -> {
            @Deprecated class Local {
              Object nested = new Object() {
                IntUnaryOperator reference = Math::abs;
                int value(Object input) {
                  class Deeper { int number = 7; }
                  return switch(input) { case String text when !text.isEmpty() -> text.length(); case null, default -> new Deeper().number; };
                }
              };
            }
            new Local();
          };
          static {
            class Local { static int[] values = {1, 2}; }
            int sum = 0;
            outer: for (int n = 0; n < 2; n++) { sum += n; if (n == 0) continue outer; break outer; }
            while (sum < 2) { ++sum; }
            do { sum--; } while (sum > 3);
            for (int n : Local.values) { sum ^= n; }
            synchronized (Local.values) { assert sum >= 0 : "sum"; }
          }
          {
            class Local { int read() { return seed; } }
            IntSupplier supplier = () -> new Local().read();
            try (var reader = new StringReader("x")) { seed += reader.read(); }
            catch (IOException | IllegalArgumentException failure) { seed = 0; }
            finally { supplier.getAsInt(); }
          }
          Example() {
            enum LocalChoice { VALUE(() -> new int[]{1}); LocalChoice(Supplier<int[]> value) {} }
            LocalChoice value = LocalChoice.VALUE;
          }
          int subject(Object input) {
            { class Local { int value = 1; } new Local(); }
            { class Local { int value = 1; } new Local(); }
            accept(new Object() { int value = 9; });
            if (input instanceof String text) { seed += text.length(); }
            switch(seed) { case 0: seed++; break; default: seed--; }
            return seed;
          }
          static void accept(Object value) {}
          enum Choice {
            FIRST(() -> new int[]{1, 2}, new int[]{3, 4}, new Object() { int value = Math.abs(-5); }.hashCode()) {
              int count = 1;
              { count++; }
              int value() { class Local { int[] values = {count}; } return new Local().values[0]; }
            };
            Choice(Supplier<int[]> action, int[] values, int number) {}
          }
        }
        """;
  }
}
