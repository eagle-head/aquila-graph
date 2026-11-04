package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.AstQuery;
import org.aquilagraph.application.AstRetention;
import org.aquilagraph.repository.neo4j.Neo4jRepositories;
import org.aquilagraph.application.RepositoryOperationContext;
import org.aquilagraph.repository.neo4j.Neo4jConnectionSettings;


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
import org.aquilagraph.application.AstLookupStatus;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryOperationException;
import org.aquilagraph.repository.neo4j.Neo4jRepositoryPhase;

import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.SyntaxType;
import java.util.stream.Collectors;
import java.util.Set;
import org.aquilagraph.testsupport.AnalysisExecutionFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aquilagraph.testsupport.Neo4jIntegration;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;

@ExtendWith(Neo4jIntegration.class)
@Tag("persistence")
class Neo4jAstResultRepositoryNeo4jIT {
  @TempDir Path directory;

  @Test
  void givenAnnotatedMembersInEnumConstantBodies_whenSavedClosedAndIndependentlyQueried_thenWrittenOwnersAndQualificationSurvive()
      throws IOException {
    // Arrange
    var settings = settings();
    var outcome = frontend().produce(snapshot(
        "enum Stored { FIRST { @Deprecated class First {} }, SECOND { @Deprecated class Second {} }; }"),
        configuration(), AstProfile.JAVA_SOURCE_UNITS);
    var original = outcome.result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(List.of(), original.diagnostics());
    assertEquals(2, original.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Annotation).count());
    try {
      // Act
      var publication = retention(settings).retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retained = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(original.identity(), retained.identity());
      assertEquals(original.aggregate(), retained.aggregate());
      assertEquals(original.coverage(), retained.coverage());
      assertEquals(List.of(), retained.diagnostics());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenRealCompilerWarningOnCompleteSource_whenSavedClosedAndIndependentlyQueried_thenNonemptyCreatingEventsAndFinalSlotsSurvive()
      throws IOException {
    // Arrange
    var settings = settings();
    var outcome = frontend().produce(snapshot("@Deprecated(forRemoval=true) class Legacy {} class Stored { Legacy value; }"),
        configuration(), AstProfile.JAVA_SOURCE_UNITS);
    var original = outcome.result().orElseThrow();
    var events = original.coverage().validationEvidence().diagnosticEmissions();
    var control = frontend().produce(snapshot("class Stored { Object value; }"), configuration(), AstProfile.JAVA_SOURCE_UNITS);

    try {
      // Act
      var publication = retention(settings).retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retained = lookup.outcome().orElseThrow().result().orElseThrow();

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(AstCompletion.COMPLETE, control.completion(), control.diagnostics().toString());
      assertEquals(List.of(), control.diagnostics());
      assertEquals(1, events.size());
      assertEquals("compiler.warn.has.been.deprecated.for.removal", original.diagnostics().getFirst().code());
      assertEquals(0, events.getFirst().address().ordinal());
      assertEquals(0, events.getFirst().slot());
      assertTrue(events.stream().anyMatch(event -> event.diagnostic().severity() == DiagnosticSeverity.WARNING));
      for (var event : events) {
        assertEquals("CompilerDiagnosticTranslator", event.address().producer());
        assertEquals("input-compiler-diagnostics", event.address().owner());
        assertSame(original.diagnostics().get(event.slot()), event.diagnostic());
      }
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(CommitKnowledge.KNOWN_COMMIT, publication.knowledge());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(original.identity(), retained.identity());
      assertEquals(events, retained.coverage().validationEvidence().diagnosticEmissions());
      assertEquals(original.diagnostics(), retained.diagnostics());
      assertEquals(original.aggregate(), retained.aggregate());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
      for (var event : retained.coverage().validationEvidence().diagnosticEmissions()) {
        assertEquals(retained.diagnostics().get(event.slot()), event.diagnostic());
      }
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenDeclarationFidelityWithSharedAnnotationsAndNestedEmptyMembers_whenSavedClosedAndIndependentlyQueried_thenAllSourceValuesSurvive()
      throws IOException {
    // Arrange
    var settings = settings();
    var outcome = frontend().produce(snapshot("""
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Dual { int value() default 7; }
        @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
        @interface Component {}
        class Stored {
          ; @Dual int first = 1, second = 2; ;
          record Part(@Component String text) { ; Part {} }
          enum Choice { FIRST { ; int value() { return 2; } }; ; }
        }
        """), configuration(), AstProfile.JAVA_SOURCE_DECLARATIONS);
    var original = outcome.result().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retained = lookup.outcome().orElseThrow().result().orElseThrow();
      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(original.identity(), retained.identity());
      assertEquals(AstProfile.JAVA_SOURCE_DECLARATIONS, retained.profile());
      assertEquals(original.aggregate(), retained.aggregate());
      assertEquals(original.coverage(), retained.coverage());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
      var empty = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.EMPTY).toList();
      assertEquals(5, empty.size());
      assertTrue(empty.stream().allMatch(node -> retained.aggregate().writtenSourceAssociation(node.nodeId()).isPresent()));
      assertEquals(2, retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION
          && node.semanticReference().flatMap(reference -> reference.symbolKey()).filter(Set.of("type:Dual", "type:Component")::contains)
              .isPresent()).count());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenEnumArgumentsAndConstantBody_whenSavedClosedAndIndependentlyQueried_thenSourceSelectionOwnershipAndFactsSurvive() throws IOException {
    // Arrange
    var settings = settings();
    var outcome = frontend().produce(snapshot("enum Stored { FIRST(Math.abs(-2)) { int field = 3; int nested() { return field; } }, SECOND(4); Stored(int n) {} }"), configuration(), AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var original = outcome.result().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retained = lookup.outcome().orElseThrow().result().orElseThrow();
      var constants = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).toList();
      var first = constants.getFirst();
      var scope = retained.aggregate().executionSyntax(first.value(SyntaxValue.VariableDeclaration.class).declarationKey()).orElseThrow();
      var body = first.requiredChild(SyntaxRole.CLASS_BODY);
      var method = body.children(SyntaxRole.MEMBER).getLast();

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(original.identity(), retained.identity());
      assertEquals(original.aggregate(), retained.aggregate());
      assertEquals(original.aggregate().executionScopes(), retained.aggregate().executionScopes());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
      assertEquals("int", scope.useEvidence(first.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
      assertEquals(method.requiredChild(SyntaxRole.BODY), retained.body(method.value(SyntaxValue.ExecutableDeclaration.class).declarationKey()).orElseThrow().root());
      assertEquals("4", constants.getLast().children(SyntaxRole.ARGUMENT).getFirst().value(SyntaxValue.Literal.class).text());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenLocalAndAnonymousDeclarations_whenSavedClosedAndIndependentlyQueried_thenCanonicalOwnershipAndNestedBodyFactsSurvive() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("class Stored { static class Base { Base(int n) {} } int field = new Base(1) { int count = Math.abs(-1); int nested() { return count; } }.nested(); void subject() { class Local { int nested() { return Math.abs(-2); } } } }"), configuration(), AstProfile.JAVA_SOURCE_NESTED);
    var original = ast.result().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retained = lookup.outcome().orElseThrow().result().orElseThrow();
      var local = retained.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.LOCAL).findFirst().orElseThrow();
      var anonymous = retained.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.ANONYMOUS).findFirst().orElseThrow();
      var nested = local.methods().stream().filter(method -> method.name().equals("nested")).findFirst().orElseThrow();
      var call = retained.body(nested.methodKey()).orElseThrow().root().children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.EXPRESSION);
      var enclosingField = retained.aggregate().executionSyntax("type:Stored#field:field").orElseThrow();
      var creation = enclosingField.root().descendantsAndSelf().stream().filter(node -> !node.children(SyntaxRole.CLASS_BODY).isEmpty()).findFirst().orElseThrow();

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(original.identity(), retained.identity());
      assertEquals(original.program(), retained.program());
      assertEquals(original.aggregate(), retained.aggregate());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
      assertEquals("type:Stored#subject()", local.nesting().lexicalOwnerKey().orElseThrow());
      assertEquals("type:Stored#field:field", anonymous.nesting().lexicalOwnerKey().orElseThrow());
      assertEquals("type:java.lang.Math#abs(int)", call.requiredReference().symbolKey().orElseThrow());
      assertEquals("int", retained.body(nested.methodKey()).orElseThrow().useEvidence(call.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
      assertEquals("int", enclosingField.useEvidence(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
      var field = anonymous.fields().getFirst();
      assertTrue(retained.aggregate().executionSyntax(field.fieldKey()).isPresent());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompactFieldInitialization_whenRetainedAndQueriedWithSeparateConnections_thenImplicitOwnerAndSelectedFactsSurviveRealStorage() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("int count = Math.abs(-2); Object input=\"abc\"; boolean matched=input instanceof String value && value.length()>0; void main() { System.out.println(count); }"), configuration(),
        AstProfile.JAVA_SOURCE_INITIALIZATION);
    var original = ast.result().orElseThrow();
    try {
      // Act
      var before = query(settings).findAst(original.identity());
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var later = query(settings).findAst(original.identity());
      var retained = later.outcome().orElseThrow().result().orElseThrow();
      var root = retained.aggregate().roots().getFirst();
      var field = retained.aggregate().executionSyntax("type:Stored#field:count").orElseThrow();
      var call = field.root().requiredChild(SyntaxRole.INITIALIZER);

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(AstLookupStatus.NOT_FOUND, before.status());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, later.status());
      assertEquals(original.identity(), retained.identity());
      assertEquals(original.aggregate(), retained.aggregate());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
      assertEquals(new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.COMPACT), root.value());
      assertTrue(root.children(SyntaxRole.TYPE_DECLARATION).isEmpty());
      assertSame(root.children(SyntaxRole.COMPACT_MEMBER).getFirst(), field.root());
      assertEquals("type:Stored", field.root().value(SyntaxValue.VariableDeclaration.class).ownerKey());
      assertEquals("type:java.lang.Math#abs(int)", call.requiredReference().symbolKey().orElseThrow());
      assertEquals("int", field.useEvidence(field.root().nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().erasedName());
      assertEquals("int", field.useEvidence(call.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
      assertEquals("type:java.lang.Math", field.staticAccess(call.nodeId()).orElseThrow()
          .declaringOwner().knownValue().orElseThrow().declarationKey().orElseThrow());
      assertTrue(retained.aggregate().methodSyntax(field.scopeKey()).isEmpty());
      assertTrue(retained.aggregate().declarationMemberOrder("type:Stored").orElseThrow().attributedTreeOrder().isPresent());
      var pattern = retained.aggregate().executionSyntax("type:Stored#field:matched").orElseThrow().root().descendantsAndSelf().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN).findFirst().orElseThrow();
      assertEquals(1, pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow().size());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompleteMemberInitialization_whenRetainedAndQueriedWithSeparateConnections_thenSourceScopesAndSelectedTargetsSurviveRealStorage() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        class Stored {
          int[] absent;
          int[] empty = {};
          int[] populated = {Math.abs(-2)};
          static int counter = Integer.parseInt("1");
          static Object input = "abc";
          boolean matched = input instanceof String value && value.length() > 0;
          static { counter++; if (input instanceof String value) { counter += value.length(); } }
          { absent = new int[3]; if (input instanceof String value) { counter += value.length(); } }
        }
        """), configuration(), AstProfile.JAVA_SOURCE_INITIALIZATION);
    var original = ast.result().orElseThrow();
    try {
      // Act
      var before = query(settings).findAst(original.identity());
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var later = query(settings).findAst(original.identity());
      var retained = later.outcome().orElseThrow().result().orElseThrow();
      var field = retained.aggregate().executionSyntax("type:Stored#field:populated").orElseThrow();
      var call = field.root().requiredChild(SyntaxRole.INITIALIZER).children(SyntaxRole.ARRAY_ELEMENT).getFirst();

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
      assertEquals(AstLookupStatus.NOT_FOUND, before.status());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, later.status());
      assertEquals(original.identity(), retained.identity());
      assertEquals(original.aggregate(), retained.aggregate());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
      assertEquals("int[]", field.useEvidence(field.root().nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().displayName());
      assertEquals("int", field.useEvidence(call.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
      assertTrue(retained.aggregate().methodSyntax(field.scopeKey()).isEmpty());
      assertEquals(2, retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.STATIC_INITIALIZER_BLOCK
          || node.syntaxKind() == SyntaxKind.INSTANCE_INITIALIZER_BLOCK).count());
      assertTrue(AstCodec.materialize(retained).links().stream().anyMatch(link -> link.field().equals("scopes")));
      assertTrue(AstCodec.materialize(retained).links().stream().anyMatch(link -> link.field().equals("attributedTreeOrder")));
      var patterns = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN).toList();
      assertEquals(3, patterns.size());
      assertTrue(patterns.stream().allMatch(node -> node.value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow().size() == 1));
      assertTrue(retained.aggregate().declarationMemberOrder("type:Stored").orElseThrow().attributedTreeOrder().isPresent());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompleteExceptionSyntax_whenExplicitlyRetainedAndQueried_thenRealStoragePreservesClausesBindingsAndUnionAlternatives() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        class Stored {
          static AutoCloseable open() { return null; }
          void subject() throws Exception {
            AutoCloseable existing = open();
            try (existing; final AutoCloseable first = open(); var _ = first) { first.toString(); }
            catch (IllegalArgumentException | IllegalStateException failure) { failure.toString(); }
            finally { existing.toString(); }
          }
        }
        """), configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    try {
      // Act
      var before = query(settings).findAst(original.identity());
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var method = retrieved.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(AstLookupStatus.NOT_FOUND, before.status());
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion(), retrieved.diagnostics().toString());
      assertEquals(AstResult.SOURCE_MODEL_VERSION, retrieved.identity().sourceModelVersion());
      assertEquals(original.identity(), retrieved.identity());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      var statement = retrieved.aggregate().nodes().stream().filter(value -> value.syntaxKind() == SyntaxKind.TRY).findFirst().orElseThrow();
      assertEquals(List.of(SyntaxRole.RESOURCE, SyntaxRole.RESOURCE, SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE, SyntaxRole.FINALLY_BLOCK),
          statement.children().stream().map(child -> child.role()).toList());
      assertEquals(SyntaxKind.VARIABLE_ACCESS, statement.children(SyntaxRole.RESOURCE).getFirst().syntaxKind());
      assertEquals(List.of("first", "_"), statement.children(SyntaxRole.RESOURCE).stream().skip(1)
          .map(value -> value.value(SyntaxValue.LocalDeclaration.class).name()).toList());
      var parameter = statement.requiredChild(SyntaxRole.CATCH_CLAUSE).requiredChild(SyntaxRole.PARAMETER);
      assertEquals(List.of("java.lang.IllegalArgumentException", "java.lang.IllegalStateException"),
          parameter.requiredChild(SyntaxRole.DECLARED_TYPE).children(SyntaxRole.UNION_ALTERNATIVE).stream()
              .map(value -> value.resolvedType().orElseThrow().erasedName()).toList());
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
      for (var node : statement.descendantsAndSelf()) {
        assertSame(node, retrieved.aggregate().node(node.nodeId()).orElseThrow());
      }
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompatibleSwitchFrame_whenExplicitlyRetainedAndQueried_thenRealStorageKeepsThePreExceptionFormatAndIdentity() throws IOException {
    // Arrange
    var settings = settings();
    var current = frontend().produce(snapshot("class Stored { int subject(int value) { return switch(value) { case 1: yield 2; default: yield 3; }; } }"),
        configuration(), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var previous = SourceAnalysisFixture.switchResult(current);
    try {
      // Act
      var publication = retention(settings).retainAst(new AstPublicationRequest(AstOutcome.available(previous),
          PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(previous.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals("java-source/11", retrieved.identity().sourceModelVersion());
      assertEquals(previous.identity(), retrieved.identity());
      assertEquals(current.aggregate(), retrieved.aggregate());
      assertArrayEquals(AstCodec.encode(previous), AstCodec.encode(retrieved));
    } finally {
      deleteAst(settings, previous.identity());
    }
  }

  @Test
  void givenCompleteSwitchForms_whenExplicitlyRetainedAndLaterQueried_thenRealStoragePreservesLabelsGuardsGroupsAndYieldOwnership() throws IOException {
    // Arrange
    var settings = settings();
    var ast = frontend().produce(snapshot("""
        class Stored {
          record Pair(Object text, int count) {}
          int colon(int value) { switch (value) { case 1: case 2: return 3; default: } return 0; }
          int constant(int value) { return switch (value) { case 1 + 2 -> 3; default -> 4; }; }
          int pattern(Object value) { return switch (value) { case Pair(String text, int count) when text.length() > count -> { yield count; } case null, default -> 0; }; }
        }
        """), configuration(), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = ast.result().orElseThrow();
    try {
      // Act
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var method = retrieved.program().methods().stream().filter(candidate -> candidate.name().equals("pattern")).findFirst().orElseThrow();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion(), retrieved.diagnostics().toString());
      assertEquals(AstResult.SOURCE_MODEL_VERSION, retrieved.identity().sourceModelVersion());
      assertEquals(original.identity(), retrieved.identity());
      assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retrieved));
      var statement = retrieved.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.SWITCH_STATEMENT).findFirst().orElseThrow();
      assertEquals(List.of(0, 1, 0), statement.children(SyntaxRole.CASE).stream().map(node -> node.children(SyntaxRole.STATEMENT).size()).toList());
      var body = retrieved.body(method.methodKey()).orElseThrow();
      var selection = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION).findFirst().orElseThrow();
      var cases = selection.children(SyntaxRole.CASE);
      assertEquals(List.of(new SyntaxValue.SwitchLabel.Pattern(0)), cases.getFirst().value(SyntaxValue.SwitchCase.class).labels());
      assertEquals(List.of(new SyntaxValue.SwitchLabel.Expression(0), SyntaxValue.SwitchLabel.Default.INSTANCE), cases.getLast().value(SyntaxValue.SwitchCase.class).labels());
      assertTrue(cases.getFirst().optionalChild(SyntaxRole.GUARD).isPresent());
      assertEquals("boolean", body.useEvidence(cases.getFirst().nodeId(), SyntaxRole.GUARD, 0).orElseThrow().selectedTarget().erasedName());
      var yield = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.YIELD).findFirst().orElseThrow();
      assertEquals("int", body.useEvidence(yield.nodeId(), SyntaxRole.VALUE, 0).orElseThrow().selectedTarget().erasedName());
      assertTrue(cases.getFirst().requiredChild(SyntaxRole.LABEL).descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN).allMatch(node -> node.value(SyntaxValue.PatternBinding.class).resolvedAccesses().isPresent()));
      assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
    } finally {
      deleteAst(settings, original.identity());
    }
  }

  @Test
  void givenCompatiblePatternFrame_whenRetainedAndQueried_thenActualRepositoryKeepsItsOriginalFormatAndBindingScope() throws IOException {
    // Arrange
    var settings = settings();
    var current = frontend().produce(snapshot("class Stored { boolean value(Object input) { return input instanceof String text && text.length() > 0; } }"), configuration(), AstProfile.JAVA_SOURCE_EXPANDED).result().orElseThrow();
    var previous = SourceAnalysisFixture.patternResult(current);
    try {
      // Act
      var publication = retention(settings).retainAst(new AstPublicationRequest(AstOutcome.available(previous),
          PublicationIntent.SAVE_COMPLETE_AST));
      var queried = query(settings).findAst(previous.identity()).outcome().orElseThrow().result().orElseThrow();
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals("java-source/10", queried.identity().sourceModelVersion());
      assertEquals(previous.identity(), queried.identity());
      assertEquals(current.aggregate(), queried.aggregate());
      assertArrayEquals(AstCodec.encode(previous), AstCodec.encode(queried));
    } finally {
      deleteAst(settings, previous.identity());
    }
  }

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
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var method = retrieved.program().methods().stream().filter(candidate -> candidate.name().equals("named")).findFirst().orElseThrow();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion());
      assertEquals(AstResult.SOURCE_MODEL_VERSION, retrieved.identity().sourceModelVersion());
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
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var references = retrieved.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).toList();
      var arrays = references.stream().filter(node -> node.value(SyntaxValue.MemberReference.class).qualifier() == SyntaxValue.ReferenceQualifier.ARRAY_TYPE).findFirst().orElseThrow();
      var method = retrieved.program().methods().stream().filter(candidate -> candidate.name().equals("arrays")).findFirst().orElseThrow();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion(), retrieved.diagnostics().toString());
      assertEquals(AstResult.SOURCE_MODEL_VERSION, retrieved.identity().sourceModelVersion());
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
      var publication = retention(settings).retainAst(new AstPublicationRequest(AstOutcome.available(previous),
          PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(previous.identity());
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
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var retrieved = lookup.outcome().orElseThrow().result().orElseThrow();
      var lambdas = retrieved.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).toList();
      var cfg = CfgDerivers.standard().derive(retrieved, new CfgRequest(retrieved.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
      // Assert
      assertEquals(PublicationStatus.SAVED, publication.status());
      assertEquals(AstLookupStatus.FOUND, lookup.status());
      assertEquals(AstCompletion.COMPLETE, retrieved.completion(), retrieved.diagnostics().toString());
      assertEquals(AstResult.SOURCE_MODEL_VERSION, retrieved.identity().sourceModelVersion());
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
      var publication = retention(settings).retainAst(new AstPublicationRequest(AstOutcome.available(previous),
          PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(previous.identity());
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
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
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
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
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
      var legacyPublication = retention(settings).retainAst(new AstPublicationRequest(legacy, PublicationIntent.SAVE_COMPLETE_AST));
      var previousPublication = retention(settings).retainAst(new AstPublicationRequest(previous, PublicationIntent.SAVE_COMPLETE_AST));
      var currentPublication = retention(settings).retainAst(new AstPublicationRequest(current, PublicationIntent.SAVE_COMPLETE_AST));
      var updatePublication = retention(settings).retainAst(new AstPublicationRequest(AstOutcome.available(updateResult),
          PublicationIntent.SAVE_COMPLETE_AST));
      var arrayPublication = retention(settings).retainAst(new AstPublicationRequest(AstOutcome.available(arrayResult),
          PublicationIntent.SAVE_COMPLETE_AST));
      var iterationPublication = retention(settings).retainAst(new AstPublicationRequest(AstOutcome.available(iterationResult),
          PublicationIntent.SAVE_COMPLETE_AST));
      var referencePublication = retention(settings).retainAst(new AstPublicationRequest(AstOutcome.available(referenceResult),
          PublicationIntent.SAVE_COMPLETE_AST));
      var referenceLookup = query(settings).findAst(referenceResult.identity());
      var iterationLookup = query(settings).findAst(iterationResult.identity());
      var arrayLookup = query(settings).findAst(arrayResult.identity());
      var legacyLookup = query(settings).findAst(legacyResult.identity());
      var previousLookup = query(settings).findAst(previousResult.identity());
      var currentLookup = query(settings).findAst(currentResult.identity());
      var updateLookup = query(settings).findAst(updateResult.identity());
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
      assertEquals(AstResult.SOURCE_MODEL_VERSION, currentResult.identity().sourceModelVersion());
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
      withSession(settings, session -> assertEquals(List.of("java-source/2", AstResult.SOURCE_MODEL_VERSION, "java-source/3", "java-source/4", "java-source/5", "java-source/6", "java-source/9"), session.run(
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
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
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
      withSession(settings, session -> assertEquals(AstResult.SOURCE_MODEL_VERSION, session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) RETURN result.formatVersion AS format",
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
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
      var previousPublication = retention(settings).retainAst(new AstPublicationRequest(AstOutcome.available(previous),
          PublicationIntent.SAVE_COMPLETE_AST));
      var previousLookup = query(settings).findAst(previous.identity());
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
      var publication = retention(settings).retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
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
      var boundedSave = retention.retainAst(new AstPublicationRequest(bounded, PublicationIntent.SAVE_COMPLETE_AST));
      var expandedSave = retention.retainAst(new AstPublicationRequest(expanded, PublicationIntent.SAVE_COMPLETE_AST));
      var boundedLookup = query(settings).findAst(bounded.result().orElseThrow().identity());
      var expandedLookup = query(settings).findAst(expanded.result().orElseThrow().identity());

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
    var runner = AnalysisCapabilities.existingSourceDerivation(RecoverableCfgFailures.atMethod(keys.get(1), cause));
    var run = runner.deriveExisting(ast, new CfgRequest(original.identity(), keys, CfgProfile.JAVA_BOUNDED));

    try {
      // Act
      var publication = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETE_AST, Neo4jRepositories.astResults(() -> settings),
          AnalysisExecutionFixture::unexpectedRunRepository).publication();
      var lookup = query(settings).findAst(original.identity());

      // Assert
      assertEquals(AnalysisCompletion.FAILED, run.completion());
      assertEquals(AstCompletion.COMPLETE, ast.completion());
      assertSame(ast, run.representation().ast());
      var methods = run.representation().cfg().orElseThrow().methods();
      assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.FAILED, MethodCfgState.AVAILABLE),
          methods.stream().map(method -> method.state()).toList());
      assertSame(cause, methods.get(1).failure().orElseThrow());
      assertTrue(methods.get(1).graph().isEmpty());
      assertEquals(original.identity(), methods.getFirst().graph().orElseThrow().identity().astIdentity());
      assertEquals(original.identity(), methods.getLast().graph().orElseThrow().identity().astIdentity());
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
    var run = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard()).deriveExisting(ast,
        new CfgRequest(original.identity(), keys, CfgProfile.JAVA_WITHOUT_SWITCH_EXPRESSIONS));

    try {
      // Act
      var publication = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETE_AST, Neo4jRepositories.astResults(() -> settings),
          AnalysisExecutionFixture::unexpectedRunRepository).publication();
      var lookup = query(settings).findAst(original.identity());

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
      var publication = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(original.identity());
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
    var runner = AnalysisCapabilities.computation(frontend(), CfgDerivers.standard());
    var run = runner.analyze(new AnalysisComputationRequest(
        new AstProductionRequest(input, configuration(), AstProfile.JAVA_SOURCE_BOUNDED), Optional.of(new CfgSelection(List.of("unknown-method-key"), CfgProfile.JAVA_BOUNDED))));
    var identity = run.representation().ast().result().orElseThrow().identity();

    try {
      // Act
      var publication = AnalysisExecutionFixture.executeComputed(run, PublicationIntent.SAVE_COMPLETE_AST, Neo4jRepositories.astResults(() -> settings),
          AnalysisExecutionFixture::unexpectedRunRepository).publication();
      var lookup = query(settings).findAst(identity);

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
      retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      withSession(settings, session -> session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) "
          + "SET result.formatVersion = 'future-source-format', result.payload = 'invalid-base64'",
          Values.parameters("scope", identity.astResultKey())).consume());

      // Act
      var incompatible = query(settings).findAst(identity);
      withSession(settings, session -> session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) "
          + "SET result.formatVersion = $format", Values.parameters("scope", identity.astResultKey(), "format", AstCodec.FORMAT_VERSION)).consume());
      var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query(settings).findAst(identity));

      // Assert
      assertEquals(AstLookupStatus.INCOMPATIBLE, incompatible.status());
      assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
      assertTrue(failure.effectivePrimary() instanceof IllegalArgumentException);
    } finally {
      deleteAst(settings, identity);
    }
    assertEquals(AstLookupStatus.NOT_FOUND, query(settings).findAst(identity).status());
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
      retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      try (var repository = Neo4jRepositories.completedRuns(() -> settings).open(RepositoryOperationContext.saveCompletedRun(completedRun.identity().key()))) {
        repository.save(completedRun);
        repository.save(completedRun);
      }
      var lookup = query(settings).findAst(identity);

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
      assertEquals(PublicationStatus.SAVED, retention.retainAst(new AstPublicationRequest(ast,
          PublicationIntent.SAVE_COMPLETE_AST)).status());
      var baseline = query(settings).findAst(identity);
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
      var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query(settings).findAst(identity));

      // Assert
      assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
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
      retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      withSession(settings, session -> session.run("MATCH (result:AstEntity:AstResult {astResultKey: $scope}) DETACH DELETE result",
          Values.parameters("scope", identity.astResultKey())).consume());

      // Act
      var failure = assertThrows(Neo4jRepositoryOperationException.class, () -> query(settings).findAst(identity));

      // Assert
      assertEquals(Neo4jRepositoryPhase.DECODE, failure.phase());
      assertEquals(CommitKnowledge.NOT_STARTED, failure.knowledge().orElseThrow());
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
      retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      withSession(settings, session -> {
        session.run("DROP CONSTRAINT ast_entity_identity").consume();
        session.run("CREATE CONSTRAINT ast_entity_identity FOR (entity:AstEntity) REQUIRE entity.incompatibleIdentity IS UNIQUE").consume();
      });

      // Act
      var publication = retention.retainAst(new AstPublicationRequest(ast, PublicationIntent.SAVE_COMPLETE_AST));
      var lookup = query(settings).findAst(identity);

      // Assert
      assertEquals(PublicationStatus.FAILED, publication.status());
      assertEquals(Neo4jRepositoryPhase.SCHEMA, assertInstanceOf(Neo4jRepositoryOperationException.class, publication.failure().orElseThrow().carrier()).phase());
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
    return Neo4jIntegration.settings();
  }

  private static AstQuery query(Neo4jConnectionSettings settings) {
    return AnalysisCapabilities.astQuery(Neo4jRepositories.astResults(() -> settings));
  }

  private static AstRetention retention(Neo4jConnectionSettings settings) {
    return AnalysisCapabilities.astRetention(new Neo4jAstResultRepositoryFactory(() -> settings));
  }

  private static void deleteAst(Neo4jConnectionSettings settings, AstIdentity identity) {
    withSession(settings, session -> Neo4jIntegration.discardAstFixture(session, identity.astResultKey()));
  }

  private static void withSession(Neo4jConnectionSettings settings, Consumer<Session> action) {
    try (var driver = GraphDatabase.driver(settings.uri(), AuthTokens.basic(settings.username(), settings.password()));
        var session = driver.session(SessionConfig.forDatabase(settings.database()))) {
      action.accept(session);
    }
  }
}
