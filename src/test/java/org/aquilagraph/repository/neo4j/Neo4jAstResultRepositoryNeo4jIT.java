package org.aquilagraph.repository.neo4j;

import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.RepositoryOperationException;
import org.aquilagraph.application.RepositoryPhase;
import org.aquilagraph.application.SourceAnalysisRunner;

import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxType;
import java.util.stream.Collectors;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

class Neo4jAstResultRepositoryNeo4jIT {
  @TempDir Path directory;

  @Test
  void givenCompletedPatterns_whenRetainedAndQueried_thenActualStoragePreservesTopologyBindingsScopeAndIndependentCfgRefusal() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        class Stored {
          record Empty() {}
          record Pair(Object text, int count) {}
          record Outer(Pair pair, Empty empty) {}
          record Box<T>(T item) {}
          boolean traditional(Object value) { return value instanceof String; }
          boolean named(Object value) { return value instanceof String text && text.length() > 0; }
          boolean unnamed(Object value) { return value instanceof String _; }
          boolean empty(Object value) { return value instanceof Empty(); }
          boolean nested(Object value) { return value instanceof Outer(Pair(String text, int _), Empty()) && text.length() > 0; }
          boolean all(Object value) { return value instanceof Pair(_, _); }
          boolean inferred(Object value) { return value instanceof Box(var item); }
          boolean generic(Box<java.util.List<String>> value) { return value instanceof Box(_); }
        }
        """), configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var method = retrieved.program().methods().stream().filter(candidate -> candidate.name().equals("named")).findFirst().orElseThrow();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
      assertEquals("java-source/10", retrieved.identity().sourceModelVersion());
      assertEquals(original.identity(), retrieved.identity());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertEquals(8, retrieved.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INSTANCE_OF).count());
      assertEquals(7, retrieved.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.DECONSTRUCTION_PATTERN).count());
      assertEquals(3, retrieved.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANY_PATTERN).count());
      assertEquals(List.of("java.lang.Object", "int", "java.util.List<java.lang.String>"), retrieved.aggregate().nodes().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.ANY_PATTERN)
          .sorted(Comparator.comparingLong(node -> node.origin().orElseThrow().startOffset()))
          .map(node -> node.resolvedType().orElseThrow().displayName()).toList());
      assertTrue(retrieved.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN)
          .allMatch(node -> node.value(SyntaxValue.PatternBinding.class).resolvedAccesses().isPresent()));
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompletedReferenceVariants_whenRetainedAndQueried_thenRealStoragePreservesWrittenFormsAndApplicableSelections() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        import java.util.function.*;
        class Stored {
          static <T> T identity(T value) { return value; }
          IntSupplier bound(String text) { return text::length; }
          ToIntFunction<String> unbound() { return String::length; }
          IntFunction<String> selected() { return String::valueOf; }
          Function<String,String> generic() { return Stored::<String>identity; }
          Supplier<String> lexical() { return super::toString; }
          Supplier<Stored> construction() { return Stored::new; }
          IntFunction<int[][]> arrays() { return int[][]::new; }
          Function<int[],int[]> cloneArray() { return int[]::clone; }
          <T extends Runnable> Consumer<T> typeParameter() { return T::run; }
        }
        """), configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var references = retrieved.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).toList();
      var arrays = references.stream().filter(node -> node.value(SyntaxValue.MemberReference.class).qualifier() == SyntaxValue.ReferenceQualifier.ARRAY_TYPE).findFirst().orElseThrow();
      var method = retrieved.program().methods().stream().filter(candidate -> candidate.name().equals("arrays")).findFirst().orElseThrow();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion(), retrieved.diagnostics().toString());
      assertEquals("java-source/10", retrieved.identity().sourceModelVersion());
      assertEquals(9, references.size());
      assertEquals("int[][]", ((SyntaxType.Resolved) arrays.value(SyntaxValue.MemberReference.class).selectedType()).identity().displayName());
      assertTrue(arrays.semanticReference().isEmpty());
      var clone = references.stream().filter(node -> node.value(SyntaxValue.MemberReference.class).selection() == SyntaxValue.ReferenceSelection.ARRAY_CLONE).findFirst().orElseThrow();
      assertTrue(clone.semanticReference().isEmpty());
      assertEquals(JavaTypeCategory.ARRAY, ((SyntaxType.Resolved) clone.requiredChild(SyntaxRole.QUALIFIER).type()).identity().category());
      assertEquals(JavaTypeCategory.EXECUTABLE, ((SyntaxType.Resolved) clone.value(SyntaxValue.MemberReference.class).selectedType()).identity().category());
      assertEquals(original.aggregate(), retrieved.aggregate());
      assertEquals(original.coverage(), retrieved.coverage());
      assertEquals(AstCodec.materialize(original), AstCodec.materialize(retrieved));
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertTrue(cfg.methods().getFirst().graph().isEmpty());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenPreReferenceLambdaFormat_whenRetainedAndQueried_thenActualRepositoryPreservesItsOriginalValuesAndIdentity() throws IOException {
    // Arrange
    var settings = settings();
    var current = frontend().produce(snapshot("class Stored { Runnable subject() { return () -> {}; } }"), configuration(), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var previous = SourceAnalysisFixture.lambdaResult(current);
    try {
      // Act
      var publication = retention(settings).retainAst(AstOutcome.available(previous));
      var lookup = retention(settings).findAst(previous.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals("java-source/8", retrieved.identity().sourceModelVersion());
      assertEquals(previous.identity(), retrieved.identity());
      assertEquals(current.aggregate(), retrieved.aggregate());
      assertEquals(previous.coverage(), retrieved.coverage());
      var originalProperties = current.coverage().validationEvidence().outcomes().stream()
          .filter(outcome -> outcome.address().role().startsWith("LambdaExpressionTree::")).toList();
      var retainedProperties = retrieved.coverage().validationEvidence().outcomes().stream()
          .filter(outcome -> outcome.address().role().startsWith("LambdaExpressionTree::")).toList();
      assertEquals(4, retainedProperties.size());
      assertEquals(Set.of("LambdaExpressionTree::Tree#getKind()", "LambdaExpressionTree::LambdaExpressionTree#getBodyKind()",
          "LambdaExpressionTree::LambdaExpressionTree#getBody()", "LambdaExpressionTree::LambdaExpressionTree#getParameters()"),
          retainedProperties.stream().map(outcome -> outcome.address().role()).collect(Collectors.toSet()));
      assertEquals(originalProperties.stream().map(outcome -> outcome.encounteredValue()).toList(), retainedProperties.stream().map(outcome -> outcome.encounteredValue()).toList());
      assertEquals(current.coverage().validationEvidence().writtenInventory().contexts().stream().filter(context -> context.address().role().startsWith("LambdaExpressionTree::")).toList(),
          retrieved.coverage().validationEvidence().writtenInventory().contexts().stream().filter(context -> context.address().role().startsWith("LambdaExpressionTree::")).toList());
      assertArrayEquals(AstCodec.encode(previous), AstCodec.encode(retrieved));
    } finally {
      deleteAst(settings, previous.identity());
    }
  }

  @Test
  void givenCompletedNestedLambdaValues_whenRetainedAndQueried_thenRealStoragePreservesFunctionalScopesAndLaterCfgRefusal() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        import java.util.function.*;
        import java.util.List;
        import java.lang.annotation.*;
        @Target(ElementType.PARAMETER) @interface Mark { String value() default ""; }
        class Stored {
          interface A { List<String> foo(List<String> arg); }
          interface B { List foo(List<String> arg); }
          interface C { List foo(List arg); }
          interface D extends A, B {}
          interface E extends A, B, C {}
          D parameterized() { return (List<String> arg) -> { return arg; }; }
          E raw() { return (List arg) -> { return arg; }; }
          static class Box { <T> Box(T value) {} }
          Supplier<Box> construction() { return () -> new Box("value"); }
          Function<String,String> annotated() { return (@Mark(TEXT_BLOCK_VALUE) String value) -> value; }
          interface F { int apply(String[] values); }
          F arrays() { return (String... values) -> values.length; }
          Function<Integer, IntSupplier> subject(int captured) {
            return (@Mark var parameter) -> () -> { return parameter + captured; };
          }
        }
        """.replace("TEXT_BLOCK_VALUE", "\"\"\"\n    \" ...\n    \"\"\"")), configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    var method = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var lambdas = retrieved.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).toList();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion(), retrieved.diagnostics().toString());
      assertEquals("java-source/10", retrieved.identity().sourceModelVersion());
      assertEquals(original.aggregate(), retrieved.aggregate());
      assertEquals(original.coverage(), retrieved.coverage());
      assertEquals(AstCodec.materialize(original), AstCodec.materialize(retrieved));
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertEquals(2, lambdas.size());
      assertEquals(SyntaxValue.LambdaBodyKind.EXPRESSION, lambdas.getFirst().value(SyntaxValue.Lambda.class).bodyKind());
      assertEquals(SyntaxValue.LambdaBodyKind.BLOCK, lambdas.getLast().value(SyntaxValue.Lambda.class).bodyKind());
      var parameter = lambdas.getFirst().children(SyntaxRole.PARAMETER).getFirst();
      assertEquals(LocalBindingEvidence.TypeForm.INFERRED, parameter.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().typeForm());
      assertEquals(1, parameter.children(SyntaxRole.ANNOTATION).size());
      var annotatedMethod = retrieved.program().methods().stream().filter(value -> value.name().equals("annotated")).findFirst().orElseThrow();
      var annotatedLambda = retrieved.body(annotatedMethod.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).findFirst().orElseThrow();
      assertFalse(annotatedLambda.value(SyntaxValue.Lambda.class).parameters().getFirst().variableArity());
      var constructionMethod = retrieved.program().methods().stream().filter(value -> value.name().equals("construction")).findFirst().orElseThrow();
      var argumentUses = retrieved.aggregate().methodSyntax(constructionMethod.methodKey()).orElseThrow().evidence().uses().stream()
          .filter(value -> value.role() == SyntaxRole.ARGUMENT).toList();
      assertEquals(List.of("java.lang.String"), argumentUses.stream().map(value -> value.selectedTarget().erasedName()).toList());
      for (var raw : List.of(false, true)) {
        var functionalMethod = retrieved.program().methods().stream().filter(value -> value.name().equals(raw ? "raw" : "parameterized"))
            .findFirst().orElseThrow();
        var functionalLambda = retrieved.body(functionalMethod.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
            .filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).findFirst().orElseThrow();
        var expected = new JavaTypeStructure.Declared("type:java.util.List", Optional.empty(), raw ? List.of()
            : List.of(new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of())));
        var descriptor = (SyntaxType.Resolved) functionalLambda.value(SyntaxValue.Lambda.class).returnType();
        assertEquals(Optional.of(expected), descriptor.structure());
        assertEquals(Optional.of(expected), ((SyntaxType.Resolved) functionalLambda.children(SyntaxRole.PARAMETER).getFirst().type()).structure());
        var innerReturn = functionalLambda.requiredChild(SyntaxRole.BODY).descendantsAndSelf().stream()
            .filter(node -> node.syntaxKind() == SyntaxKind.RETURN).findFirst().orElseThrow();
        assertEquals(List.of(descriptor.identity()), retrieved.aggregate().methodSyntax(functionalMethod.methodKey()).orElseThrow().evidence()
            .uses().stream().filter(value -> value.consumerNodeId().equals(innerReturn.nodeId())).map(value -> value.selectedTarget()).toList());
      }
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenPreLambdaAssertionFormat_whenSavedAndQueried_thenActualRepositoryPreservesItsOriginalIdentityAndCatalog() throws IOException {
    // Arrange
    var settings = settings();
    var current = frontend().produce(snapshot("class Stored { void subject(Object lock) { assert lock != null; synchronized (lock) {} } }"),
        configuration(), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var previous = SourceAnalysisFixture.assertionResult(current);
    try {
      // Act
      var publication = retention(settings).retainAst(AstOutcome.available(previous));
      var lookup = retention(settings).findAst(previous.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals("java-source/7", retrieved.identity().sourceModelVersion());
      assertEquals(previous.coverage(), retrieved.coverage());
      assertEquals(previous.aggregate(), retrieved.aggregate());
      assertArrayEquals(AstCodec.encode(previous), AstCodec.encode(retrieved));
    } finally {
      deleteAst(settings, previous.identity());
    }
  }

  @Test
  void givenCompleteAssertionsAndMonitors_whenSavedAndQueried_thenRealDatabasePreservesWrittenContentAndCfgRefusal() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        class Stored {
          int subject(Object lock, boolean flag, int n) {
            assert flag;
            assert n > 0 : n++;
            synchronized (lock) {}
            synchronized (this) { try { assert flag : "nested"; return n; } catch (RuntimeException error) { throw error; } }
          }
        }
        """), configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    var method = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var statements = retrieved.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
      var assertions = statements.stream().filter(node -> node.syntaxKind() == SyntaxKind.ASSERT).toList();
      var monitors = statements.stream().filter(node -> node.syntaxKind() == SyntaxKind.SYNCHRONIZED).toList();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion(), retrieved.diagnostics().toString());
      assertEquals(original.identity(), retrieved.identity());
      assertEquals(original.aggregate(), retrieved.aggregate());
      assertEquals(original.coverage(), retrieved.coverage());
      assertEquals(AstCodec.materialize(original), AstCodec.materialize(retrieved));
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertEquals(3, assertions.size());
      assertEquals(List.of(SyntaxRole.CONDITION), assertions.getFirst().children().stream().map(child -> child.role()).toList());
      assertTrue(assertions.getFirst().optionalChild(SyntaxRole.DETAIL).isEmpty());
      assertEquals(SyntaxKind.UPDATE, assertions.get(1).requiredChild(SyntaxRole.DETAIL).syntaxKind());
      assertEquals(2, monitors.size());
      assertTrue(monitors.getFirst().requiredChild(SyntaxRole.BODY).children().isEmpty());
      assertEquals(SyntaxKind.TRY, monitors.getLast().requiredChild(SyntaxRole.BODY).children(SyntaxRole.STATEMENT).getFirst().syntaxKind());
      assertTrue(statements.stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompleteIterationSyntax_whenRetainedAndQueried_thenRealDatabasePreservesHeadersBindingsLabelsAndCfgRefusal() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        import java.lang.annotation.*;
        @Target(ElementType.LOCAL_VARIABLE) @interface Mark {}
        class Stored { void subject(int n, int[] values, Iterable<String> iterable) {
          outer: while (n > 0) {
            inner: do { n--; if (n == 1) continue outer; break inner; } while (n > 0);
          }
          for (@Mark final int i = 0, j = 1; i < n; n--) { if (i == j) break; }
          for (;;) break;
          for (var value : values) { int copy = value; }
          for (var _ : iterable) ;
        } }
        """), configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    var method = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var nodes = retrieved.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(original.identity(), retrieved.identity());
      assertEquals(original.aggregate(), retrieved.aggregate());
      assertEquals(original.coverage(), retrieved.coverage());
      assertEquals(AstCodec.materialize(original), AstCodec.materialize(retrieved));
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
      assertEquals(List.of(Optional.of("outer"), Optional.of("inner")), nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.LABELED_STATEMENT).map(node -> node.value(SyntaxValue.StatementLabel.class).name()).toList());
      var loops = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.FOR_LOOP).toList();
      assertEquals(List.of("i", "j"), loops.getFirst().children(SyntaxRole.INITIALIZER).stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
      assertEquals(List.of("final"), loops.getFirst().children(SyntaxRole.INITIALIZER).getFirst().value(SyntaxValue.LocalDeclaration.class).writtenModifiers());
      assertEquals(1, loops.getFirst().children(SyntaxRole.INITIALIZER).getFirst().children(SyntaxRole.ANNOTATION).size());
      assertEquals(List.of(SyntaxRole.BODY), loops.getLast().children().stream().map(child -> child.role()).toList());
      var enhanced = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.ENHANCED_FOR_LOOP).toList();
      assertEquals(List.of("value", "_"), enhanced.stream().map(node -> node.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.LocalDeclaration.class).name()).toList());
      assertTrue(enhanced.stream().allMatch(node -> node.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.LocalDeclaration.class).bindingEvidence().filter(binding -> binding.context() == LocalBindingEvidence.Context.ENHANCED_FOR && binding.typeForm() == LocalBindingEvidence.TypeForm.INFERRED).isPresent()));
      assertEquals(SyntaxKind.EMPTY, enhanced.getLast().requiredChild(SyntaxRole.BODY).syntaxKind());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompatibleOlderAndCurrentResults_whenSavedAndQueried_thenRealDatabasePreservesTheirDistinctFormatsAndIdentities()
      throws IOException {
    // Arrange
    var settings = settings();
    var current = frontend().produce(snapshot("class Stored { int value(int input) { int first=input; int second; second=first+1; return second; } }"), configuration(),
        AstProfile.JAVA_SOURCE_BOUNDED);
    var legacy = AstOutcome.available(SourceAnalysisFixture.legacyResult(current.result().orElseThrow()));
    var previous = AstOutcome.available(SourceAnalysisFixture.previousResult(
        SourceAnalysisFixture.withPreviousCoverage(current.result().orElseThrow())));
    var currentResult = current.result().orElseThrow();
    var legacyResult = legacy.result().orElseThrow();
    var previousResult = previous.result().orElseThrow();
    var updateResult = SourceAnalysisFixture.updateResult(currentResult);
    var arrayResult = SourceAnalysisFixture.arrayResult(currentResult);
    var iterationResult = SourceAnalysisFixture.iterationResult(currentResult);
    var referenceResult = SourceAnalysisFixture.referenceResult(currentResult);

    try {
      // Act
      var legacyPublication = retention(settings).retainAst(legacy);
      var previousPublication = retention(settings).retainAst(previous);
      var currentPublication = retention(settings).retainAst(current);
      var updatePublication = retention(settings).retainAst(AstOutcome.available(updateResult));
      var arrayPublication = retention(settings).retainAst(AstOutcome.available(arrayResult));
      var iterationPublication = retention(settings).retainAst(AstOutcome.available(iterationResult));
      var referencePublication = retention(settings).retainAst(AstOutcome.available(referenceResult));
      var referenceLookup = retention(settings).findAst(referenceResult.identity());
      var iterationLookup = retention(settings).findAst(iterationResult.identity());
      var arrayLookup = retention(settings).findAst(arrayResult.identity());
      var legacyLookup = retention(settings).findAst(legacyResult.identity());
      var previousLookup = retention(settings).findAst(previousResult.identity());
      var currentLookup = retention(settings).findAst(currentResult.identity());
      var updateLookup = retention(settings).findAst(updateResult.identity());
      var retrievedPrevious = previousLookup.outcome().orElseThrow().result().orElseThrow();
      var method = retrievedPrevious.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
      var cfg = CfgDerivers.standard().derive(retrievedPrevious,
          new CfgRequest(retrievedPrevious.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

      // Assert
      assertEquals(PublicationStatus.SAVED, referencePublication.status());
      assertEquals(AstLookupStatus.FOUND, referenceLookup.status());
      assertEquals("java-source/9", referenceLookup.outcome().orElseThrow().result().orElseThrow().identity().sourceModelVersion());
      assertArrayEquals(AstCodec.encode(referenceResult), AstCodec.encode(referenceLookup.outcome().orElseThrow().result().orElseThrow()));
      assertEquals(PublicationStatus.SAVED, iterationPublication.status());
      assertEquals(AstLookupStatus.FOUND, iterationLookup.status());
      assertEquals("java-source/6", iterationLookup.outcome().orElseThrow().result().orElseThrow().identity().sourceModelVersion());
      assertArrayEquals(AstCodec.encode(iterationResult), AstCodec.encode(iterationLookup.outcome().orElseThrow().result().orElseThrow()));
      assertEquals(PublicationStatus.SAVED, arrayPublication.status());
      assertEquals(AstLookupStatus.FOUND, arrayLookup.status());
      assertEquals("java-source/5", arrayLookup.outcome().orElseThrow().result().orElseThrow().identity().sourceModelVersion());
      assertArrayEquals(AstCodec.encode(arrayResult), AstCodec.encode(arrayLookup.outcome().orElseThrow().result().orElseThrow()));
      assertEquals("java-source/2", legacyResult.identity().sourceModelVersion());
      assertEquals("java-source/3", retrievedPrevious.identity().sourceModelVersion());
      assertEquals("java-source/10", currentResult.identity().sourceModelVersion());
      assertEquals("java-source/4", updateLookup.outcome().orElseThrow().result().orElseThrow().identity().sourceModelVersion());
      assertEquals(PublicationStatus.SAVED, updatePublication.status());
      assertEquals(AstLookupStatus.FOUND, updateLookup.status());
      assertArrayEquals(AstCodec.encode(updateResult), AstCodec.encode(updateLookup.outcome().orElseThrow().result().orElseThrow()));
      assertEquals(PublicationStatus.SAVED, legacyPublication.status());
      assertEquals(PublicationStatus.SAVED, previousPublication.status());
      assertEquals(PublicationStatus.SAVED, currentPublication.status());
      assertEquals(AstLookupStatus.FOUND, legacyLookup.status());
      assertEquals(AstLookupStatus.FOUND, previousLookup.status());
      assertEquals(AstLookupStatus.FOUND, currentLookup.status());
      assertEquals(previousResult.identity(), retrievedPrevious.identity());
      assertEquals(AstCompletion.COMPLETE, retrievedPrevious.completion());
      assertEquals(currentResult.aggregate(), retrievedPrevious.aggregate());
      assertEquals(previousResult.coverage(), retrievedPrevious.coverage());
      assertEquals("java-language-feature-coverage:e0968b3e0d4da804090dff9f60d557181733dff543864f096e1a854a0e0d3ac2",
          retrievedPrevious.coverage().coverage().contractIdentity());
      assertNotEquals(currentResult.coverage().coverage(), retrievedPrevious.coverage().coverage());
      assertEquals(MethodCfgState.AVAILABLE, cfg.methods().getFirst().state());
      assertEquals(legacyResult.identity(), legacyLookup.outcome().orElseThrow().result().orElseThrow().identity());
      assertEquals(currentResult.identity(), currentLookup.outcome().orElseThrow().result().orElseThrow().identity());
      var retainedLocals = legacyLookup.outcome().orElseThrow().result().orElseThrow().aggregate().nodes().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();
      assertEquals(List.of("first", "second"), retainedLocals.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
      assertTrue(retainedLocals.stream().allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().isEmpty()));
      assertTrue(retainedLocals.getFirst().optionalChild(SyntaxRole.INITIALIZER).isPresent());
      assertTrue(retainedLocals.getLast().optionalChild(SyntaxRole.INITIALIZER).isEmpty());
      var previousLocals = retrievedPrevious.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();
      assertEquals(List.of("first", "second"), previousLocals.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
      assertTrue(previousLocals.stream().allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().isPresent()));
      assertTrue(previousLocals.stream().allMatch(node -> node.semanticReference().isPresent()));
      assertTrue(previousLocals.getFirst().optionalChild(SyntaxRole.INITIALIZER).isPresent());
      assertTrue(previousLocals.getLast().optionalChild(SyntaxRole.INITIALIZER).isEmpty());
      assertArrayEquals(AstCodec.encode(previousResult), AstCodec.encode(retrievedPrevious));
      assertArrayEquals(AstCodec.encode(legacyResult), AstCodec.encode(legacyLookup.outcome().orElseThrow().result().orElseThrow()));
      assertArrayEquals(AstCodec.encode(currentResult), AstCodec.encode(currentLookup.outcome().orElseThrow().result().orElseThrow()));
      withSession(settings, session -> assertEquals(List.of("java-source/10", "java-source/2", "java-source/3", "java-source/4", "java-source/5", "java-source/6", "java-source/9"), session.run(
          "MATCH (result:AstEntity:AstResult) WHERE result.astResultKey IN $subjects RETURN result.formatVersion AS format ORDER BY format",
          Values.parameters("subjects", List.of(legacyResult.identity().astResultKey(), previousResult.identity().astResultKey(), updateResult.identity().astResultKey(), arrayResult.identity().astResultKey(), iterationResult.identity().astResultKey(), referenceResult.identity().astResultKey(), currentResult.identity().astResultKey())))
          .list(record -> record.get("format").asString())));
    } finally {
      deleteAst(settings, legacyResult.identity());
      deleteAst(settings, previousResult.identity());
      deleteAst(settings, updateResult.identity());
      deleteAst(settings, arrayResult.identity());
      deleteAst(settings, iterationResult.identity());
      deleteAst(settings, referenceResult.identity());
      deleteAst(settings, currentResult.identity());
    }
  }

  @Test
  void givenCompleteExecutableArrays_whenRetainedAndQueriedAfterCfgRefusal_thenRealDatabasePreservesDimensionsAnnotationsAndInitializers()
      throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
        class Stored { int[][] subject(int n) {
          int[][] sized = new @Mark(1) int @Mark(2)[n] @Mark(3)[];
          int[][] initialized = new int[][]{{1}, {2, 3}, {}};
          int[] empty = {};
          return initialized;
        } }
        """), configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    var method = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var cfg = CfgDerivers.standard().derive(original, new CfgRequest(original.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
    try {
      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var nodes = retrieved.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
      var arrays = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.ARRAY_CREATION).toList();
      var retrievedCfg = CfgDerivers.standard().derive(retrieved,
          new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertEquals(MethodCfgState.UNSUPPORTED, retrievedCfg.methods().getFirst().state());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
      assertEquals(original.identity(), retrieved.identity());
      assertEquals(original.aggregate(), retrieved.aggregate());
      assertEquals(original.coverage(), retrieved.coverage());
      assertEquals(AstCodec.materialize(original), AstCodec.materialize(retrieved));
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      var sized = arrays.getFirst();
      assertEquals(new SyntaxValue.ArrayCreation(true, false, List.of(1)), sized.value());
      assertEquals("n", sized.children(SyntaxRole.DIMENSION).getFirst().value(SyntaxValue.SourceSymbol.class).name());
      assertEquals(1, sized.children(SyntaxRole.ANNOTATION).size());
      assertEquals(2, sized.requiredChild(SyntaxRole.TYPE_OPERAND).descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION).count());
      assertEquals(List.of(1, 2, 0), arrays.get(1).children(SyntaxRole.ARRAY_ELEMENT).stream().map(node -> node.children(SyntaxRole.ARRAY_ELEMENT).size()).toList());
      assertEquals(List.of("2", "3"), arrays.get(1).children(SyntaxRole.ARRAY_ELEMENT).get(1).children(SyntaxRole.ARRAY_ELEMENT).stream().map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
      assertEquals(new SyntaxValue.ArrayCreation(false, true, List.of()), arrays.getLast().value());
      withSession(settings, session -> assertEquals("java-source/10", session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) RETURN result.formatVersion AS format",
          Values.parameters("scope", original.identity().astResultKey())).single().get("format").asString()));
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompleteExpandedBindings_whenRetainedAndRetrievedAfterCfgRefusal_thenRealDatabasePreservesBindingsAndAnnotations()
      throws IOException {
    // Arrange
    var settings = settings();
    var input = snapshot("""
        import java.lang.annotation.*;
        @Target({ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Dual { int value(); }
        class Stored { int subject(int[] input) {
          @Dual(3) final int first = 1, second[] = input;
          var inferred = first; int _ = 7; return inferred;
        } }
        """);
    var ast = frontend().produce(input, configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    var previous = SourceAnalysisFixture.previousResult(SourceAnalysisFixture.withPreviousCoverage(original));
    var method = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var cfg = CfgDerivers.standard().derive(original,
        new CfgRequest(original.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

    try {
      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var previousPublication = retention(settings).retainAst(AstOutcome.available(previous));
      var previousLookup = retention(settings).findAst(previous.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var retrievedPrevious = previousLookup.outcome().orElseThrow().result().orElseThrow();
      var bindings = retrieved.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();
      var previousCfg = CfgDerivers.standard().derive(retrievedPrevious,
          new CfgRequest(retrievedPrevious.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(PublicationStatus.SAVED, previousPublication.status());
      assertEquals(AstLookupStatus.FOUND, previousLookup.status());
      assertEquals("java-source/3", retrievedPrevious.identity().sourceModelVersion());
      assertEquals(previous.identity(), retrievedPrevious.identity());
      assertEquals(AstCompletion.COMPLETE, retrievedPrevious.completion());
      assertEquals(original.aggregate(), retrievedPrevious.aggregate());
      assertEquals(previous.coverage(), retrievedPrevious.coverage());
      assertEquals("java-language-feature-coverage:e0968b3e0d4da804090dff9f60d557181733dff543864f096e1a854a0e0d3ac2",
          retrievedPrevious.coverage().coverage().contractIdentity());
      assertEquals(bindings, retrievedPrevious.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList());
      assertEquals(MethodCfgState.UNSUPPORTED, previousCfg.methods().getFirst().state());
      assertArrayEquals(AstCodec.encode(previous), AstCodec.encode(retrievedPrevious));
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertEquals(List.of("first", "second", "inferred", "_"),
          bindings.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
      var annotation = bindings.get(0).requiredChild(SyntaxRole.ANNOTATION);
      assertEquals(List.of(annotation.nodeId()),
          bindings.get(1).value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().sharedAnnotationKeys());
      assertEquals(LocalBindingEvidence.TypeForm.INFERRED,
          bindings.get(2).value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().typeForm());
      assertTrue(bindings.get(3).semanticReference().isEmpty());
      assertTrue(bindings.get(3).value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().isPresent());
    } finally {
      deleteAst(settings, original.identity());
      deleteAst(settings, previous.identity());
    }
  }

  @Test
  void givenCompleteUpdatesAndCastBounds_whenExplicitlyRetainedAfterCfgRefusal_thenRealQueriesPreserveOperatorsAndOrderedBounds()
      throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Use { int value(); }
        interface First {} interface Second {} interface Third {}
        class Stored {
          Object subject(int value, Object input) {
            value *= 2; value /= 2; value %= 3; value += 2; value -= 1;
            value <<= 1; value >>= 1; value >>>= 1; value &= 7; value ^= 3; value |= 2;
            ++value; --value; value++; value--;
            return (@Use(1) First & @Use(2) Second & @Use(3) Third) input;
          }
        }
        """), configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    var method = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var cfg = CfgDerivers.standard().derive(original,
        new CfgRequest(original.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
    try {
      // Act
      var publication = retention(settings).retainAst(ast);
      var lookup = retention(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var nodes = retrieved.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
      var intersection = nodes.stream().filter(node -> node.sourceKind() == JavaSourceKind.INTERSECTION_TYPE).findFirst().orElseThrow();
      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
      assertEquals(original.identity(), retrieved.identity());
      assertEquals(original.aggregate(), retrieved.aggregate());
      assertEquals(original.coverage(), retrieved.coverage());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      assertEquals(List.of(SyntaxOperator.MULTIPLY_ASSIGNMENT, SyntaxOperator.DIVIDE_ASSIGNMENT, SyntaxOperator.REMAINDER_ASSIGNMENT,
          SyntaxOperator.PLUS_ASSIGNMENT, SyntaxOperator.MINUS_ASSIGNMENT, SyntaxOperator.LEFT_SHIFT_ASSIGNMENT,
          SyntaxOperator.RIGHT_SHIFT_ASSIGNMENT, SyntaxOperator.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT, SyntaxOperator.AND_ASSIGNMENT,
          SyntaxOperator.XOR_ASSIGNMENT, SyntaxOperator.OR_ASSIGNMENT), nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.COMPOUND_ASSIGNMENT)
              .map(node -> node.value(SyntaxValue.Operator.class).operator()).toList());
      assertEquals(List.of(SyntaxOperator.PREFIX_INCREMENT, SyntaxOperator.PREFIX_DECREMENT, SyntaxOperator.POSTFIX_INCREMENT,
          SyntaxOperator.POSTFIX_DECREMENT), nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.UPDATE)
              .map(node -> node.value(SyntaxValue.Operator.class).operator()).toList());
      assertEquals(List.of("First", "Second", "Third"), intersection.children(SyntaxRole.INTERSECTION_BOUND).stream()
          .map(bound -> bound.requiredChild(SyntaxRole.UNDERLYING_TYPE).value(SyntaxValue.SourceSymbol.class).name()).toList());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenBoundedAndExpandedProfiles_whenSavingAndQueryingRealRepository_thenDistinctResultsRetainClosedSourceValues()
      throws IOException {
    // Arrange
    var settings = settings();
    var input = snapshot("class Stored { int value(int x) { return x + 7; } }");
    var bounded = frontend().produce(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = frontend().produce(input, configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var retention = retention(settings);

    try {
      // Act
      var boundedSave = retention.retainAst(bounded);
      var expandedSave = retention.retainAst(expanded);
      var boundedLookup = retention.findAst(bounded.result().orElseThrow().identity());
      var expandedLookup = retention.findAst(expanded.result().orElseThrow().identity());

      // Assert
      assertEquals(PublicationStatus.SAVED, boundedSave.status());
      assertEquals(PublicationStatus.SAVED, expandedSave.status());
      assertEquals(AstLookupStatus.FOUND, boundedLookup.status());
      assertEquals(AstLookupStatus.FOUND, expandedLookup.status());
      assertEquals(AstProfile.JAVA_SOURCE_BOUNDED, boundedLookup.outcome().orElseThrow().profile());
      assertEquals(AstProfile.JAVA_SOURCE_EXPANDED, expandedLookup.outcome().orElseThrow().profile());
      assertArrayEquals(AstCodec.encode(bounded.result().orElseThrow()), AstCodec.encode(boundedLookup.outcome().orElseThrow().result().orElseThrow()));
      assertArrayEquals(AstCodec.encode(expanded.result().orElseThrow()), AstCodec.encode(expandedLookup.outcome().orElseThrow().result().orElseThrow()));
      assertEquals(expanded.result().orElseThrow().aggregate(), expandedLookup.outcome().orElseThrow().result().orElseThrow().aggregate());
      withSession(settings, session -> assertEquals(2, session.run(
          "MATCH (result:AstEntity:AstResult) WHERE result.astResultKey IN $subjects RETURN count(result) AS count",
          Values.parameters("subjects", List.of(bounded.result().orElseThrow().identity().astResultKey(),
              expanded.result().orElseThrow().identity().astResultKey()))).single().get("count").asInt()));
    } finally {
      deleteAst(settings, bounded.result().orElseThrow().identity());
      deleteAst(settings, expanded.result().orElseThrow().identity());
    }
  }

  @Test
  void givenCompleteSourceAndLaterReturnedConstructionFailure_whenRetainingAst_thenEverySourceValueRemainsIndependentlyQueryable()
      throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int first() { return 1; } int second() { return 2; } int third() { return 3; } }"),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var original = ast.result().orElseThrow();
    var keys = List.of("first", "second", "third").stream().map(name -> original.program().methods().stream()
        .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
    var cause = new IllegalStateException("Declared construction observation");
    var runner = new SourceAnalysisRunner(ignored -> {
      throw new AssertionError("Existing derivation must not produce source");
    }, RecoverableCfgFailures.atMethod(keys.get(1), cause));
    var run = runner.deriveExisting(ast, new CfgRequest(original.identity(), keys, CfgProfile.JAVA_BOUNDED));

    try {
      // Act
      var retention = retention(settings);
      var publication = retention.retainAst(run);
      var lookup = retention.findAst(original.identity());

      // Assert
      assertEquals(AnalysisCompletion.FAILED, run.completion());
      assertEquals(AstCompletion.COMPLETE, ast.completion());
      assertSame(ast, run.representation().ast());
      var methods = run.representation().cfg().orElseThrow().methods();
      assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.FAILED, MethodCfgState.AVAILABLE),
          methods.stream().map(method -> method.state()).toList());
      assertSame(cause, methods.get(1).failure().orElseThrow());
      assertTrue(methods.get(1).graph().isEmpty());
      assertEquals(original.identity(), methods.getFirst().graph().orElseThrow().identity().orElseThrow().astIdentity());
      assertEquals(original.identity(), methods.getLast().graph().orElseThrow().identity().orElseThrow().astIdentity());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(lookup.outcome().orElseThrow().result().orElseThrow()));
      withSession(settings, session -> assertEquals(0,
          session.run("MATCH (entity:AstEntity:ControlFlowGraph {astResultKey: $scope}) RETURN count(entity) AS count",
              Values.parameters("scope", original.identity().astResultKey())).single().get("count").asInt()));
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompleteSourceAndPerMethodCfgRefusal_whenRetainingAst_thenRefusedAndAvailableBodiesRemainQueryable() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int choose(int value) { return switch (value) { default -> 1; }; } int value() { return 2; } }"),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var original = ast.result().orElseThrow();
    var keys = List.of("choose", "value").stream().map(name -> original.program().methods().stream()
        .filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey()).toList();
    var run = new SourceAnalysisRunner(frontend(), CfgDerivers.standard()).deriveExisting(ast,
        new CfgRequest(original.identity(), keys, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS));

    try {
      // Act
      var retention = retention(settings);
      var publication = retention.retainAst(run);
      var lookup = retention.findAst(original.identity());

      // Assert
      assertEquals(AnalysisCompletion.PARTIAL, run.completion());
      assertEquals(AstCompletion.COMPLETE, ast.completion());
      assertSame(ast, run.representation().ast());
      var methods = run.representation().cfg().orElseThrow().methods();
      assertEquals(List.of(MethodCfgState.UNSUPPORTED, MethodCfgState.AVAILABLE), methods.stream().map(method -> method.state()).toList());
      assertTrue(methods.getFirst().graph().isEmpty());
      assertEquals(original.bodyRef(keys.getFirst()), methods.getFirst().body());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(lookup.outcome().orElseThrow().result().orElseThrow()));
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompleteSourceWithoutCfg_whenSavingClosingAndFinding_thenDatabaseRoundTripRetainsEveryPublicValue() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("@Deprecated class Stored { int value(int x) { return x + 7; } String text(Object x) { return \"a\" + x; } }"),
        configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var original = ast.result().orElseThrow();
    var retention = retention(settings);

    try {
      // Act
      var publication = retention.retainAst(ast);
      var lookup = retention.findAst(original.identity());
      var decoded = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(original.identity(), decoded.identity());
      assertEquals(original.snapshot(), decoded.snapshot());
      assertEquals(original.environment(), decoded.environment());
      assertEquals(original.program(), decoded.program());
      assertEquals(original.aggregate(), decoded.aggregate());
      assertEquals(original.coverage(), decoded.coverage());
      assertEquals(original.diagnostics(), decoded.diagnostics());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
      withSession(settings, session -> {
        var record = session.run("MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count",
            Values.parameters("scope", original.identity().astResultKey())).single();
        assertEquals(AstCodec.materialize(original).nodes().size() + 1, record.get("count").asInt());
        assertEquals(0, session.run("MATCH (entity:AstEntity:ControlFlowGraph {astResultKey: $scope}) RETURN count(entity) AS count",
            Values.parameters("scope", original.identity().astResultKey())).single().get("count").asInt());
      });
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenRejectedCfgWithCompleteSource_whenExplicitlyRetainingAst_thenSourceRemainsIndependentlyQueryable() throws IOException {
    // Arrange
    var settings = settings();
    var input = snapshot("class Stored { int value() { return 2; } }");
    var runner = new SourceAnalysisRunner(frontend(), CfgDerivers.standard());
    var run = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(input), configuration(), AstProfile.JAVA_SOURCE_BOUNDED,
        Optional.of(new CfgSelection(List.of("unknown-method-key"), CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
    var identity = run.representation().ast().result().orElseThrow().identity();

    try {
      // Act
      var retention = retention(settings);
      var publication = retention.retainAst(run);
      var lookup = retention.findAst(identity);

      // Assert
      assertEquals(AnalysisCompletion.REJECTED, run.completion());
      assertEquals(AstCompletion.COMPLETE, run.representation().ast().completion());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(identity, lookup.requestedIdentity());
    } finally {
      deleteAst(settings, identity);
    }
  }

  @Test
  void givenUnsupportedVersionAndMalformedEnvelope_whenFinding_thenIncompatibilityBypassesDecodeAndSupportedCorruptionFails() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int value() { return 3; } }"), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var identity = ast.result().orElseThrow().identity();
    var retention = retention(settings);

    try {
      retention.retainAst(ast);
      withSession(settings, session -> session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) "
          + "SET result.formatVersion = 'future-source-format', result.payload = 'invalid-base64'",
          Values.parameters("scope", identity.astResultKey())).consume());

      // Act and assert
      assertEquals(AstLookupStatus.INCOMPATIBLE, retention.findAst(identity).status());
      withSession(settings, session -> session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) "
          + "SET result.formatVersion = $format", Values.parameters("scope", identity.astResultKey(), "format", AstCodec.FORMAT_VERSION)).consume());
      var failure = assertThrows(RepositoryOperationException.class, () -> retention.findAst(identity));
      assertEquals(RepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    } finally {
      deleteAst(settings, identity);
    }
    assertEquals(AstLookupStatus.NOT_FOUND, retention.findAst(identity).status());
  }

  @Test
  void givenRetainedAstAndCompletedRun_whenReplacingTheCompletedRun_thenAstNamespaceAndContentSurvive() throws IOException {
    // Arrange
    var settings = settings();
    var input = snapshot("class Stored { int value() { return 4; } }");
    var frontend = frontend();
    var ast = frontend.produce(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var identity = ast.result().orElseThrow().identity();
    var completedRun = new SourceAnalysisFixture().analyze(ast);
    var retention = retention(settings);
    assertEquals(identity, completedRun.representation().ast().result().orElseThrow().identity());

    try {
      // Act
      retention.retainAst(ast);
      try (var repository = new Neo4jAnalysisRunRepository(settings)) {
        repository.save(completedRun);
        repository.save(completedRun);
      }
      var lookup = retention.findAst(identity);

      // Assert
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertArrayEquals(AstCodec.encode(ast.result().orElseThrow()), AstCodec.encode(lookup.outcome().orElseThrow().result().orElseThrow()));
    } finally {
      deleteAst(settings, identity);
      withSession(settings, session -> session.run("MATCH (entity:AnalysisEntity {runId: $runId}) DETACH DELETE entity",
          Values.parameters("runId", completedRun.identity().key())).consume());
    }
  }

  @Test
  void givenForeignSameKeySourceEndpoint_whenFinding_thenSupportedCorrelationsAreRejected() throws IOException {
    assertRejectedEndpointAlias(true, true, false);
  }

  @Test
  void givenForeignSameKeyTargetEndpoint_whenFinding_thenSupportedCorrelationsAreRejected() throws IOException {
    assertRejectedEndpointAlias(false, true, false);
  }

  @Test
  void givenSameKeyTargetWithoutAstNamespace_whenFinding_thenSupportedCorrelationsAreRejected() throws IOException {
    assertRejectedEndpointAlias(false, false, false);
  }

  @Test
  void givenAdditionalIncomingRelationshipWithoutAstNamespace_whenFinding_thenHiddenCorruptionIsRejected() throws IOException {
    assertRejectedEndpointAlias(true, false, true);
  }

  private void assertRejectedEndpointAlias(boolean aliasSource, boolean foreignScope, boolean retainOriginalRelationship) throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int value() { return 7; } }"), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var result = ast.result().orElseThrow();
    var identity = result.identity();
    var scope = identity.astResultKey();
    var retention = retention(settings);
    var endpoint = aliasSource ? "source" : "target";
    var aliasLabels = foreignScope ? ":AstEntity:AstValue" : "";
    var mutation = "MATCH (source:AstEntity)-[relation:HAS_AST_VALUES {astResultKey: $scope}]->(target:AstEntity) "
        + "CREATE (alias" + aliasLabels + ") SET alias = " + endpoint
        + " {.*, astResultKey: $aliasScope, endpointAliasFor: $scope} CREATE (" + (aliasSource ? "alias" : "source")
        + ")-[replacement:HAS_AST_VALUES]->(" + (aliasSource ? "target" : "alias") + ") SET replacement = properties(relation) "
        + (retainOriginalRelationship ? "" : "DELETE relation ") + "RETURN count(replacement) AS count";

    try {
      assertEquals(PublicationStatus.SAVED, retention.retainAst(ast).status());
      var baseline = retention.findAst(identity);
      assertEquals(AstLookupStatus.FOUND, baseline.status());
      assertArrayEquals(AstCodec.encode(result), AstCodec.encode(baseline.outcome().orElseThrow().result().orElseThrow()));
      withSession(settings, session -> {
        var parameters = Values.parameters("scope", scope, "aliasScope", foreignScope ? "foreign-endpoint:" + scope : scope);
        var countQuery = "MATCH (entity:AstEntity {astResultKey: $scope}) RETURN count(entity) AS count";
        var countBefore = session.run(countQuery, parameters).single().get("count").asLong();
        var replacement = session.run(mutation, parameters);
        assertEquals(1L, replacement.single().get("count").asLong());
        replacement.consume();
        assertEquals(countBefore, session.run(countQuery, parameters).single().get("count").asLong());
        var payload = session.run("MATCH (root:AstEntity:AstResult {astResultKey: $scope}) RETURN root.payload AS payload", parameters)
            .single().get("payload").asString();
        assertEquals(Base64.getEncoder().encodeToString(AstCodec.encode(result)), payload);
      });

      // Act
      var failure = assertThrows(RepositoryOperationException.class, () -> retention.findAst(identity));

      // Assert
      assertEquals(RepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    } finally {
      withSession(settings, session -> session.run("MATCH (alias {endpointAliasFor: $scope}) DETACH DELETE alias",
          Values.parameters("scope", scope)).consume());
      deleteAst(settings, identity);
    }
  }

  private InputSnapshot snapshot(String source) {
    return InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), source)));
  }

  @Test
  void givenRetainedEntitiesWithoutTheirResultRoot_whenFinding_thenCorruptionCannotBecomeAbsence() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int value() { return 5; } }"), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var identity = ast.result().orElseThrow().identity();
    var retention = retention(settings);
    try {
      retention.retainAst(ast);
      withSession(settings, session -> session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) DETACH DELETE result",
          Values.parameters("scope", identity.astResultKey())).consume());

      // Act
      var failure = assertThrows(RepositoryOperationException.class, () -> retention.findAst(identity));

      // Assert
      assertEquals(RepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    } finally {
      deleteAst(settings, identity);
    }
  }

  @Test
  void givenIncompatibleNamedConstraint_whenSavingAgain_thenSchemaFailurePreventsReplacementAndPriorAstSurvives() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { int value() { return 6; } }"), configuration(), AstProfile.JAVA_SOURCE_BOUNDED);
    var identity = ast.result().orElseThrow().identity();
    var retention = retention(settings);
    try {
      retention.retainAst(ast);
      withSession(settings, session -> {
        session.run("DROP CONSTRAINT ast_entity_identity").consume();
        session.run("CREATE CONSTRAINT ast_entity_identity FOR (entity:AstEntity) REQUIRE entity.incompatibleIdentity IS UNIQUE").consume();
      });

      // Act
      var publication = retention.retainAst(ast);
      var lookup = retention.findAst(identity);

      // Assert
      assertEquals(PublicationStatus.FAILED, publication.status());
      assertEquals(RepositoryPhase.SCHEMA, publication.failure().orElseThrow().phase());
      assertEquals(CommitKnowledge.NOT_STARTED, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertArrayEquals(AstCodec.encode(ast.result().orElseThrow()), AstCodec.encode(lookup.outcome().orElseThrow().result().orElseThrow()));
    } finally {
      withSession(settings, session -> {
        session.run("DROP CONSTRAINT ast_entity_identity IF EXISTS").consume();
        session.run("CREATE CONSTRAINT ast_entity_identity FOR (entity:AstEntity) REQUIRE (entity.astResultKey, entity.key) IS UNIQUE").consume();
      });
      deleteAst(settings, identity);
    }
  }

  private static JavaFrontend frontend() {
    return new JavaFrontend(new JavaSourceLoader());
  }

  private static CompilerConfiguration configuration() {
    return CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  }

  private static Neo4jConnectionSettings settings() {
    return Neo4jConnectionSettings.fromEnvironment(System.getenv());
  }

  private static AstRetentionService retention(Neo4jConnectionSettings settings) {
    return new AstRetentionService(new Neo4jAstResultRepositoryFactory(() -> settings));
  }

  private static void deleteAst(Neo4jConnectionSettings settings, AstIdentity identity) {
    withSession(settings, session -> session.run("MATCH (entity:AstEntity {astResultKey: $scope}) DETACH DELETE entity",
        Values.parameters("scope", identity.astResultKey())).consume());
  }

  private static void withSession(Neo4jConnectionSettings settings, Consumer<Session> action) {
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      action.accept(session);
    }
  }
}
