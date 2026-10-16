package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class LambdaAstTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenLegalParameterAndBodyForms_whenProducedAndDecoded_thenWrittenValuesAndTargetSurviveCompilerClosure() {
    return Stream.of(
        new Variant("() -> 7", 0, true, LocalBindingEvidence.TypeForm.IMPLICIT, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("x -> x + 1", 1, false, LocalBindingEvidence.TypeForm.IMPLICIT, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("(x) -> x + 1", 1, true, LocalBindingEvidence.TypeForm.IMPLICIT, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("(x, y) -> x + y", 2, true, LocalBindingEvidence.TypeForm.IMPLICIT, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("(int x) -> x + 1", 1, true, LocalBindingEvidence.TypeForm.DECLARED, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("(final int x, int y) -> { return x + y; }", 2, true, LocalBindingEvidence.TypeForm.DECLARED, SyntaxValue.LambdaBodyKind.BLOCK),
        new Variant("(var x) -> x + 1", 1, true, LocalBindingEvidence.TypeForm.INFERRED, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("(final var x, var y) -> { return x + y; }", 2, true, LocalBindingEvidence.TypeForm.INFERRED, SyntaxValue.LambdaBodyKind.BLOCK),
        new Variant("_ -> 7", 1, false, LocalBindingEvidence.TypeForm.IMPLICIT, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("(_, _) -> 7", 2, true, LocalBindingEvidence.TypeForm.IMPLICIT, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("(int _, int _) -> 7", 2, true, LocalBindingEvidence.TypeForm.DECLARED, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("(var _, var _) -> 7", 2, true, LocalBindingEvidence.TypeForm.INFERRED, SyntaxValue.LambdaBodyKind.EXPRESSION),
        new Variant("() -> { return 7; }", 0, true, LocalBindingEvidence.TypeForm.IMPLICIT, SyntaxValue.LambdaBodyKind.BLOCK))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var types = Stream.generate(() -> "int").limit(variant.parameters()).toList();
          var source = "class Example { interface F { int apply(" + String.join(", ",
              Stream.iterate(0, index -> index + 1).limit(types.size()).map(index -> "int p" + index).toList())
              + "); } F subject() { return " + variant.expression() + "; } }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          var lambda = lambdas(result).getFirst();
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(variant.expression(), written(lambda, source));
          var value = lambda.value(SyntaxValue.Lambda.class);
          assertEquals(variant.body(), value.bodyKind());
          assertEquals(variant.parenthesized(), value.parenthesized());
          assertEquals(variant.parameters(), lambda.children(SyntaxRole.PARAMETER).size());
          assertTrue(lambda.type() instanceof SyntaxType.Resolved);
          assertTrue(lambda.semanticReference().isEmpty());
          for (var parameter : lambda.children(SyntaxRole.PARAMETER)) {
            var local = parameter.value(SyntaxValue.LocalDeclaration.class);
            var binding = local.bindingEvidence().orElseThrow();
            assertEquals(variant.typeForm(), binding.typeForm());
            assertEquals(LocalBindingEvidence.Context.LAMBDA, binding.context());
            assertTrue(binding.declarationKey().isPresent());
            assertEquals(JavaTypeCategory.INT, parameter.resolvedType().orElseThrow().category());
            assertEquals(variant.typeForm() == LocalBindingEvidence.TypeForm.DECLARED, parameter.optionalChild(SyntaxRole.DECLARED_TYPE).isPresent());
            assertEquals(!local.name().equals("_"), parameter.semanticReference().isPresent());
            assertTrue(parameter.optionalChild(SyntaxRole.INITIALIZER).isEmpty());
          }
          assertEquals(original.aggregate(), result.aggregate());
          assertEquals(original.coverage(), result.coverage());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(result));
          var properties = result.coverage().validationEvidence().outcomes().stream()
              .filter(outcome -> outcome.occurrence().map(occurrence -> occurrence.classification().feature().identifier().startsWith("LambdaExpressionTree::")).orElse(false)).toList();
          assertEquals(4, properties.size());
          assertTrue(properties.stream().noneMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenDeclaredArrayAndAnnotatedParameters_whenProduced_thenTypeStructureAndEllipsisAreDistinct() {
    return Stream.of("(String[] x) -> x.length", "(String x[]) -> x.length", "(String... x) -> x.length",
        "(@P final String @T [] x) -> x.length", "(@P var x) -> x.length", "(String @T ... x) -> x.length", "(String... _) -> 7", "(String[] _) -> 7")
        .map(expression -> DynamicTest.dynamicTest(expression, () -> {
          // Arrange
          var source = """
              import java.lang.annotation.*;
              @Target(ElementType.PARAMETER) @interface P {}
              @Target(ElementType.TYPE_USE) @interface T {}
              class Example { interface F { int apply(String[] x); } F subject() { return %s; } }
              """.formatted(expression);
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var lambda = lambdas(result).getFirst();
          var parameter = lambda.children(SyntaxRole.PARAMETER).getFirst();
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(expression.contains("..."), lambda.value(SyntaxValue.Lambda.class).parameters().getFirst().variableArity());
          assertTrue(parameter.type() instanceof SyntaxType.Resolved);
          assertEquals(JavaTypeCategory.ARRAY, parameter.resolvedType().orElseThrow().category());
          assertEquals(expression.contains("@P") ? 1 : 0, parameter.children(SyntaxRole.ANNOTATION).size());
          assertEquals(expression.contains("final") ? List.of("final") : List.of(), parameter.value(SyntaxValue.LocalDeclaration.class).writtenModifiers());
          assertEquals(expression, written(lambda, source));
          if (!expression.contains("var")) {
            assertTrue(parameter.requiredChild(SyntaxRole.DECLARED_TYPE).descendantsAndSelf().stream()
                .anyMatch(node -> node.sourceKind() == JavaSourceKind.ARRAY_TYPE));
          }
          assertArrayEquals(AstCodec.encode(result), AstCodec.encode(AstCodec.decode(AstCodec.encode(result), result.identity())));
        }));
  }

  @Test
  void givenNestedLambdasCapturesAndLexicalReceivers_whenProduced_thenSourceBindingsRemainInActualScopes() throws IOException {
    // Arrange
    var source = """
        import java.util.function.*;
        class Base { int field; int value() { return field; } }
        class Example extends Base {
          int own;
          Function<Integer, IntSupplier> subject(int parameter) {
            int local = 4;
            return x -> () -> { int nested = 5; return x + local + parameter + nested + this.own + super.value(); };
          }
        }
        """;
    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var nodes = body(result);
    var lambdas = lambdas(result);
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(2, lambdas.size());
    var key = lambdas.getFirst().children(SyntaxRole.PARAMETER).getFirst().value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey();
    assertTrue(nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS && node.value(SyntaxValue.SourceSymbol.class).name().equals("x"))
        .allMatch(node -> node.semanticReference().orElseThrow().symbolKey().equals(key)));
    assertTrue(nodes.stream().anyMatch(node -> node.value() instanceof SyntaxValue.Receiver receiver && receiver.kind() == SyntaxValue.ReceiverKind.THIS));
    assertTrue(nodes.stream().anyMatch(node -> node.value() instanceof SyntaxValue.Receiver receiver && receiver.kind() == SyntaxValue.ReceiverKind.SUPER));
    assertTrue(nodes.stream().filter(node -> node.value() instanceof SyntaxValue.Receiver receiver && receiver.kind() == SyntaxValue.ReceiverKind.THIS)
        .allMatch(node -> node.resolvedType().orElseThrow().erasedName().equals("Example")));
    assertTrue(nodes.stream().filter(node -> node.value() instanceof SyntaxValue.Receiver receiver && receiver.kind() == SyntaxValue.ReceiverKind.SUPER)
        .allMatch(node -> node.resolvedType().orElseThrow().erasedName().equals("Base")));
    assertTrue(lambdas.get(1).structuralPath().steps().containsAll(lambdas.getFirst().structuralPath().steps()));
    assertEquals(2, result.program().methods().stream().filter(method -> !method.constructor()).count());
  }

  @TestFactory
  Stream<DynamicTest> givenVoidCompatibleBodies_whenProduced_thenWrittenExpressionAndEmptyBlockAreRetained() {
    return Stream.of("() -> ping()", "() -> {}", "() -> { ping(); return; }", "() -> new Example()")
        .map(expression -> DynamicTest.dynamicTest(expression, () -> {
          // Arrange
          var source = "class Example { void ping() {} Runnable subject() { return " + expression + "; } }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(expression, written(lambdas(result).getFirst(), source));
          assertTrue(lambdas(result).getFirst().children(SyntaxRole.PARAMETER).isEmpty());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenIllegalLambdaSource_whenProduced_thenActualCompilerErrorsPreventCompletion() {
    return Stream.of("(int x, y) -> x + y", "(var x, int y) -> x + y", "(_, _) -> _", "(x, x) -> x",
        "(x, y) -> missing", "(x, y) -> { return; }")
        .map(expression -> DynamicTest.dynamicTest(expression, () -> {
          // Arrange
          var source = "class Example { interface F { int apply(int x, int y); } F subject() { return " + expression + "; } }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertNotEquals(AstCompletion.COMPLETE, result.completion());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")), result.diagnostics().toString());
        }));
  }

  @Test
  void givenLambdaWithSideEffectsAndNestedReturn_whenCfgIsRequested_thenOuterMethodRefusesWithoutExecutingTheBody() throws IOException {
    // Arrange
    var source = "class Example { int field; Runnable subject() { return () -> { while (field < 9) { field++; } return; }; } }";
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var bytes = AstCodec.encode(result);
    // Act
    var cfg = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
    var bounded = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
    assertTrue(cfg.methods().getFirst().graph().isEmpty());
    assertEquals(List.of(Optional.of(lambdas(result).getFirst().nodeId())), cfg.methods().getFirst().blockers().stream().map(blocker -> blocker.occurrence().map(occurrence -> occurrence.nodeKey())).toList());
    assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
    assertArrayEquals(bytes, AstCodec.encode(result));
  }

  @Test
  void givenCopiedLambdaWithMissingOrContradictoryChildren_whenConstructed_thenIntrinsicShapeRejectsIt() throws IOException {
    // Arrange
    var lambda = lambdas(produce("class Example { Runnable subject() { return () -> {}; } }", AstProfile.JAVA_SOURCE_EXPANDED)).getFirst();
    // Act
    var missing = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(lambda.sourceFileKey(), lambda.structuralPath(), lambda.sourceKind(),
        lambda.syntaxKind(), lambda.type(), lambda.value(), List.of(), Optional.empty(), lambda.origin().orElseThrow()));
    var incompatible = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(lambda.sourceFileKey(), lambda.structuralPath(), lambda.sourceKind(),
        lambda.syntaxKind(), lambda.type(), new SyntaxValue.Lambda(SyntaxValue.LambdaBodyKind.EXPRESSION, true, List.of(), lambda.value(SyntaxValue.Lambda.class).returnType()), lambda.children(), Optional.empty(), lambda.origin().orElseThrow()));
    // Assert
    assertTrue(missing.getMessage().contains("BODY"));
    assertTrue(incompatible.getMessage().contains("body kind"));
    assertFalse(lambda.type() == SyntaxType.Unresolved.INSTANCE);
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile).result().orElseThrow();
  }

  @Test
  void givenUnnamedLambdaWithPostNameDimensions_whenCompiled_thenTheActualBracketDiagnosticPreventsCompletion() throws IOException {
    // Arrange
    var source = "class Example { interface F { int apply(String[] values); } F subject() { return (String _[]) -> 7; } }";
    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()
        && diagnostic.code().equals("compiler.err.use.of.underscore.not.allowed.with.brackets")));
  }

  @TestFactory
  Stream<DynamicTest> givenIllegalVariableArityParameters_whenCompiled_thenCompilerErrorsRemainAvailableThroughTheProducer() {
    return Stream.of("(String... values, String next) -> values.length", "(var... values, var next) -> values.length")
        .map(expression -> DynamicTest.dynamicTest(expression, () -> {
          // Arrange
          var source = "class Example { interface F { int apply(String[] values, String next); } F subject() { return " + expression + "; } }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, result.completion());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")), result.diagnostics().toString());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenSupplementaryUnicodeParameterNames_whenProduced_thenConciseParametersKeepTheirExactWrittenForm() {
    return Stream.of(false, true).map(parenthesized -> DynamicTest.dynamicTest(Boolean.toString(parenthesized), () -> {
      // Arrange
      // The code point is an exact Java identifier compatibility input.
      var name = new String(Character.toChars(0x10400));
      var parameter = parenthesized ? "(" + name + ")" : name;
      var source = "import java.util.function.IntUnaryOperator; class Example { IntUnaryOperator subject() { return " + parameter + " -> " + name + "; } }";
      // Act
      var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
      var result = AstCodec.decode(AstCodec.encode(original), original.identity());
      var binding = lambdas(result).getFirst().children(SyntaxRole.PARAMETER).getFirst().value(SyntaxValue.LocalDeclaration.class);
      // Assert
      assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
      assertEquals(name, binding.name());
      assertEquals(LocalBindingEvidence.TypeForm.IMPLICIT, binding.bindingEvidence().orElseThrow().typeForm());
      assertEquals(original.aggregate(), result.aggregate());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenUnavailableFunctionalTargets_whenCopiedValuesAreAdmitted_thenIncompleteSourceCannotAcquireRetentionStorage() {
    return Stream.of(SyntaxType.Unresolved.INSTANCE, SyntaxType.Erroneous.INSTANCE, SyntaxType.Unsupported.INSTANCE,
        new SyntaxType.NoType(SyntaxType.NoType.Kind.NONE),
        new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.Runnable", "java.lang.Runnable")),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ERROR, "missing", "missing")),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.OTHER, "unknown", "unknown")))
        .map(target -> DynamicTest.dynamicTest(target.toString(), () -> {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { Runnable subject() { return () -> {}; } }")));
    var original = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var captured = CopiedAstValues.capture(original.aggregate().orElseThrow());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
        replaceTargetType(source.root(), replacements, target), source.nodes().stream().map(node -> replaceTargetType(node, replacements, target)).toList(),
        source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), replaceTargetType(body.root(), replacements, target),
        body.origin(), List.of(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var supplied = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes()));
    var input = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(supplied), JavaCoverageTestSupport.emissions(original));
    // Act
    var outcome = AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
    var decoded = AstCodec.decode(AstCodec.encode(outcome.result().orElseThrow()), outcome.result().orElseThrow().identity());
    var publication = new AstRetentionService(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(outcome);
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
    assertEquals(target, lambdas(decoded).getFirst().type());
    }));
  }

  @Test
  void givenExpandedLambdaRequests_whenApplicationSelectsAstOnlyOrCfg_thenActualRefusalPreservesTheCompleteSource() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { Runnable subject() { return () -> {}; } }")));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    // Act
    var astOnly = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED,
        Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var selected = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED,
        Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, astOnly.completion());
    assertEquals(AnalysisCompletion.PARTIAL, selected.completion());
    var original = astOnly.representation().ast().result().orElseThrow();
    var refused = selected.representation().ast().result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, refused.completion());
    assertEquals(original.identity(), refused.identity());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(refused));
    assertEquals(MethodCfgState.UNSUPPORTED, selected.representation().cfg().orElseThrow().methods().stream()
        .filter(method -> refused.method(method.methodKey()).orElseThrow().name().equals("subject")).findFirst().orElseThrow().state());
  }

  @Test
  void givenSiblingLambdasInAnArray_whenProduced_thenSameNamedParametersKeepDistinctOwnedBindings() throws IOException {
    // Arrange
    var source = """
        import java.util.function.IntUnaryOperator;
        class Example { IntUnaryOperator[] subject() { return new IntUnaryOperator[] { x -> x + 1, x -> x + 2 }; } }
        """;
    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var functions = lambdas(result);
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(2, functions.size());
    var keys = functions.stream().map(lambda -> lambda.children(SyntaxRole.PARAMETER).getFirst().value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow()).toList();
    assertNotEquals(keys.getFirst(), keys.getLast());
    for (var index = 0; index < functions.size(); index++) {
      var key = keys.get(index);
      assertTrue(functions.get(index).requiredChild(SyntaxRole.BODY).descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS)
          .allMatch(node -> node.semanticReference().orElseThrow().symbolKey().equals(Optional.of(key))));
    }
  }

  private static SyntaxNode replaceTargetType(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements, SyntaxType target) {
    var previous = replacements.get(node);
    if (previous != null) return previous;
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replaceTargetType(child.node(), replacements, target))).toList();
    var type = node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION ? target : node.type();
    var copied = node.origin().isPresent() ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type,
        node.value(), children, node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference());
    replacements.put(node, copied);
    return copied;
  }

  @TestFactory
  Stream<DynamicTest> givenInheritedGenericOrIntersectionFunctionalTargets_whenProduced_thenBodyReturnFactsUseTheFunctionalDescriptor() {
    return Stream.of(
        "import java.util.function.Supplier; class Example { Supplier<String> subject() { return () -> { return \"value\"; }; } }",
        "class Example { interface A { Object get(); } interface B { String get(); } interface F extends A, B { boolean equals(Object other); } F subject() { return () -> { return \"value\"; }; } }",
        "import java.io.Serializable; import java.util.function.Supplier; class Example { Supplier<String> subject() { return (Supplier<String> & Serializable) () -> { return \"value\"; }; } }",
        "import java.util.function.Function; class Example { Function<? super String, ? extends String> subject() { return value -> value; } }")
        .map(source -> DynamicTest.dynamicTest(source, () -> {
          // Arrange
          var expectedReturnName = "java.lang.String";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var lambda = lambdas(result).getFirst();
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(expectedReturnName, ((SyntaxType.Resolved) lambda.value(SyntaxValue.Lambda.class).returnType()).identity().erasedName());
          assertTrue(lambda.semanticReference().isEmpty());
        }));
  }

  private static List<SyntaxNode> body(AstResult result) {
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    return result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
  }

  @TestFactory
  Stream<DynamicTest> givenRawAndParameterizedInheritedSignatures_whenProducedAndDecoded_thenTheEntireDescriptorAndReturnTargetAgree() {
    return Stream.of(false, true).flatMap(raw -> Stream.of(false, true).map(intersection -> DynamicTest.dynamicTest(
        "raw=" + raw + ", intersection=" + intersection, () -> {
          // Arrange
          var interfaces = "import java.util.List; class Example { interface A { List<String> foo(List<String> arg); } "
              + "interface B { List foo(List<String> arg); } interface C { List foo(List arg); } "
              + "interface D extends A, B {} interface E extends A, B, C {} ";
          var target = raw ? "E" : "D";
          var cast = intersection ? (raw ? "(A & B & C) " : "(A & B) ") : "";
          var source = interfaces + (intersection ? "A" : target) + " subject() { return " + cast + "(List" + (raw ? "" : "<String>")
              + " arg) -> { return arg; }; } }";
          var expected = new JavaTypeStructure.Declared("type:java.util.List", Optional.empty(), raw ? List.of()
              : List.of(new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of())));
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          var lambda = lambdas(result).getFirst();
          var descriptor = (SyntaxType.Resolved) lambda.value(SyntaxValue.Lambda.class).returnType();
          var innerReturn = lambda.requiredChild(SyntaxRole.BODY).descendantsAndSelf().stream()
              .filter(node -> node.syntaxKind() == SyntaxKind.RETURN).findFirst().orElseThrow();
          var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
          var use = result.aggregate().methodSyntax(method.methodKey()).orElseThrow().evidence().uses().stream()
              .filter(value -> value.consumerNodeId().equals(innerReturn.nodeId())).findFirst().orElseThrow();
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(Optional.of(expected), descriptor.structure());
          assertEquals(Optional.of(expected), ((SyntaxType.Resolved) lambda.children(SyntaxRole.PARAMETER).getFirst().type()).structure());
          assertEquals(descriptor.identity(), use.selectedTarget());
          assertEquals(raw ? "java.util.List" : "java.util.List<java.lang.String>", use.selectedTarget().displayName());
          assertEquals(intersection ? JavaTypeCategory.INTERSECTION : JavaTypeCategory.DECLARED, lambda.resolvedType().orElseThrow().category());
          assertEquals(original.aggregate(), result.aggregate());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(result));
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenNonFunctionalJavaTargets_whenCompiled_thenActualDiagnosticsSurviveIncompleteSourceAndCodec() {
    return Stream.of("int", "int[]", "Object", "T").map(target -> DynamicTest.dynamicTest(target, () -> {
      // Arrange
      var source = "class Example { " + (target.equals("T") ? "<T extends Runnable> " : "") + target
          + " subject() { return () -> {}; } }";
      // Act
      var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
      var result = AstCodec.decode(AstCodec.encode(original), original.identity());
      // Assert
      assertEquals(AstCompletion.INCOMPLETE, result.completion());
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()
          && diagnostic.code().startsWith("compiler.")), result.diagnostics().toString());
      assertEquals(original.diagnostics(), result.diagnostics());
      assertEquals(original.aggregate(), result.aggregate());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenLambdaInSupportedExpressionContexts_whenProduced_thenRealSelectedConsumersRetainTheFunctionalExpression() {
    return Stream.of(
        "import java.util.function.IntSupplier; class Example { static void consume(IntSupplier value) {} void subject() { consume(() -> 3); } }",
        "class Example { Thread subject() { return new Thread(() -> {}); } }",
        "import java.util.function.IntSupplier; class Example { IntSupplier subject(boolean flag) { return flag ? () -> 3 : () -> 4; } }")
        .map(source -> DynamicTest.dynamicTest(source, () -> {
          // Arrange
          var expectedBodies = source.contains("flag ?") ? 2 : 1;
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(expectedBodies, lambdas(result).size());
          assertTrue(lambdas(result).stream().allMatch(node -> node.type() instanceof SyntaxType.Resolved && node.semanticReference().isEmpty()));
        }));
  }

  private static List<SyntaxNode> lambdas(AstResult result) {
    return body(result).stream().filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).toList();
  }

  @TestFactory
  Stream<DynamicTest> givenGenericConstructionInLambdaBodies_whenProducedAndDecoded_thenSelectedArgumentTargetsRemainComplete() {
    return Stream.of(
        new Construction("<T> Box(T value) {}", "Box subject() { return new Box(\"value\"); }", List.of("java.lang.String")),
        new Construction("<T> Box(T value) {}", "Supplier<Box> subject() { return () -> new Box(\"value\"); }", List.of("java.lang.String")),
        new Construction("<T> Box(T value) {}", "Supplier<Box> subject() { return () -> { return new Box(\"value\"); }; }", List.of("java.lang.String")),
        new Construction("<T> Box(T value) {}", "Supplier<Supplier<Box>> subject() { return () -> () -> new Box(\"value\"); }", List.of("java.lang.String")),
        new Construction("<T> Box(T value) {}", "Function<String,Box> subject() { return value -> new Box(value); }", List.of("java.lang.String")),
        new Construction("@SafeVarargs <T> Box(T... values) {}", "Supplier<Box> subject() { return () -> new Box(\"a\", \"b\"); }", List.of("java.lang.String", "java.lang.String")),
        new Construction("@SafeVarargs <T> Box(T... values) {}", "Supplier<Box> subject() { return () -> new Box(new String[] {\"a\"}); }", List.of("java.lang.String[]")))
        .map(variant -> DynamicTest.dynamicTest(variant.method(), () -> {
          // Arrange
          var source = "import java.util.function.*; class Example { static class Box { " + variant.constructor() + " } " + variant.method() + " }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
          var uses = CopiedAstValues.capture(result.aggregate()).bodies().stream().filter(value -> value.methodKey().equals(method.methodKey()))
              .findFirst().orElseThrow().uses().stream().filter(value -> value.role() == SyntaxRole.ARGUMENT).toList();
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(variant.targets(), uses.stream().map(value -> value.selectedTarget().erasedName()).toList());
          assertEquals(original.aggregate(), result.aggregate());
          if (variant.method().contains("->")) {
            assertEquals(AstCompletion.INCOMPLETE, produce(source, AstProfile.JAVA_SOURCE_BOUNDED).completion());
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenAnnotationLiteralsAndCommentsContainingEllipses_whenProduced_thenOnlyWrittenParameterEllipsesSetVariableArity() {
    return Stream.of("\"\"\"\n    \" ...\n    \"\"\"", "\"\\\" ...\"", "\"...\" /* ... */")
        .flatMap(literal -> Stream.of(false, true).map(variableArity -> DynamicTest.dynamicTest(literal + variableArity, () -> {
          // Arrange
          var type = variableArity ? "String..." : "String";
          var source = "import java.lang.annotation.*; import java.util.function.*; @Target(ElementType.PARAMETER) @interface Mark { String value(); } "
              + "class Example { Function<" + (variableArity ? "String[]" : "String") + ",String> subject() { return (@Mark(" + literal + ") "
              + type + " value) -> " + (variableArity ? "value[0]" : "value") + "; } }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(variableArity, lambdas(result).getFirst().value(SyntaxValue.Lambda.class).parameters().getFirst().variableArity());
          assertEquals(original.aggregate(), result.aggregate());
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenMissingOrInvalidFunctionalReturnTypes_whenCopiedValuesAreQualified_thenIncompleteValuesSurviveCodecAndCannotAcquireStorage() {
    return Stream.of(SyntaxType.Unresolved.INSTANCE, SyntaxType.NotApplicable.INSTANCE, SyntaxType.Erroneous.INSTANCE,
        SyntaxType.Unsupported.INSTANCE, new SyntaxType.NoType(SyntaxType.NoType.Kind.NONE),
        new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String")),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.ERROR, "missing", "missing")),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.OTHER, "unknown", "unknown")),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.EXECUTABLE, "executable", "executable")),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.UNION, "java.io.IOException | java.lang.RuntimeException", "java.lang.Exception"),
            new JavaTypeStructure.Union(List.of(new JavaTypeStructure.Declared("type:java.io.IOException", Optional.empty(), List.of()),
                new JavaTypeStructure.Declared("type:java.lang.RuntimeException", Optional.empty(), List.of())))),
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.NULL, "null", "null")))
        .map(returnType -> DynamicTest.dynamicTest(returnType.toString(), () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
              "class Example { Runnable subject() { return () -> {}; } }")));
          var original = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
          var captured = CopiedAstValues.capture(original.aggregate().orElseThrow());
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
              replaceReturnType(source.root(), replacements, returnType), source.nodes().stream().map(node -> replaceReturnType(node, replacements, returnType)).toList(),
              source.structuralEdges(), source.semanticEdges())).toList();
          var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), replaceReturnType(body.root(), replacements, returnType),
              body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
          var supplied = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes()));
          var input = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(supplied), JavaCoverageTestSupport.emissions(original));
          // Act
          var outcome = AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
          var result = AstCodec.decode(AstCodec.encode(outcome.result().orElseThrow()), outcome.result().orElseThrow().identity());
          var publication = new AstRetentionService(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(outcome);
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, result.completion());
          assertEquals(returnType, lambdas(result).getFirst().value(SyntaxValue.Lambda.class).returnType());
          assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
        }));
  }

  private static SyntaxNode replaceReturnType(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements, SyntaxType returnType) {
    var previous = replacements.get(node);
    if (previous != null) return previous;
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replaceReturnType(child.node(), replacements, returnType))).toList();
    var value = node.value();
    if (value instanceof SyntaxValue.Lambda lambda) {
      value = new SyntaxValue.Lambda(lambda.bodyKind(), lambda.parenthesized(), lambda.parameters(), returnType);
    }
    var copied = node.origin().isPresent() ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(),
        value, children, node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value, children, node.semanticReference());
    replacements.put(node, copied);
    return copied;
  }

  private record Construction(String constructor, String method, List<String> targets) {}

  private static String written(SyntaxNode node, String source) {
    var origin = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
  }

  private record Variant(String expression, int parameters, boolean parenthesized, LocalBindingEvidence.TypeForm typeForm, SyntaxValue.LambdaBodyKind body) {}
}
