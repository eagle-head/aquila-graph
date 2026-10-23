package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.cfg.internal.RecoverableCfgFailures;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication.FieldDeclaratorApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationTypeLocation.Written;
import org.aquilagraph.source.api.JavaSourceKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.io.TempDir;

class MemberInitializationAstTest {
  @TempDir Path directory;

  @Test
  void givenEmptyBodyDeclarationsAlongsideInitialization_whenProducedDecodedAndOfferedForRetention_thenIncompleteQualificationPreservesRepresentedMembers() throws IOException {
    // Arrange
    var source = "class Example { ; static int field = Math.abs(-1); static { field = 2; } ; }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var publication = new AstRetentionService(context -> {
      throw new AssertionError("Incomplete source must not acquire a repository");
    });

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_INITIALIZATION);
    var original = outcome.result().orElseThrow();
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var attempt = publication.retainAst(outcome);
    var type = retained.aggregate().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).getFirst();
    var field = type.children(SyntaxRole.MEMBER).getFirst();
    var block = type.children(SyntaxRole.MEMBER).getLast();
    var fieldScope = retained.aggregate().executionSyntax("type:Example#field:field").orElseThrow();

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, retained.completion());
    assertEquals(List.of(new SourceSpan(field.sourceFileKey(), source.indexOf(';'), source.indexOf(';') + 1),
        new SourceSpan(field.sourceFileKey(), source.lastIndexOf(';'), source.lastIndexOf(';') + 1)),
        retained.diagnostics().stream().filter(value -> value.code().equals("frontend.unsupported-class-member"))
            .flatMap(value -> value.origins().stream()).toList());
    assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.STATIC_INITIALIZER_BLOCK),
        type.children(SyntaxRole.MEMBER).stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(type.structuralPath().child(SyntaxRole.MEMBER, 0), field.structuralPath());
    assertEquals(type.structuralPath().child(SyntaxRole.MEMBER, 1), block.structuralPath());
    assertEquals("Math.abs(-1)", written(source, field.requiredChild(SyntaxRole.INITIALIZER)));
    assertEquals("field = 2;", written(source, block.children(SyntaxRole.STATEMENT).getFirst()));
    assertEquals("int", fieldScope.useEvidence(field.nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().erasedName());
    assertTrue(retained.aggregate().methodSyntax(fieldScope.scopeKey()).isEmpty());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.diagnostics(), retained.diagnostics());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
    assertEquals(PublicationStatus.NOT_ELIGIBLE, attempt.status());
    assertEquals(CommitKnowledge.NOT_STARTED, attempt.knowledge());
  }

  @Test
  void givenSemicolonsInStatementsAndLiteralText_whenProducedAndDecoded_thenSupportedOccurrencesRemainComplete() throws IOException {
    // Arrange
    var source = "class Example { /* ; */ String value = \";\"; static { ; } void subject() { ; } // ;\n }";

    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertTrue(retained.diagnostics().isEmpty());
    assertEquals(2, retained.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.EMPTY_STATEMENT).count());
    assertEquals("\";\"", written(source, fields(retained).get("value").requiredChild(SyntaxRole.INITIALIZER)));
    assertEquals(original.aggregate(), retained.aggregate());
  }

  @Test
  void givenHistoricallyIgnoredEmptyBodyDeclarations_whenProducedAndDecodedWithTheEarlierProfile_thenHistoricalQualificationIsPreserved() throws IOException {
    // Arrange
    var source = "class Example { ; int field; void subject() { ; } }";

    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertTrue(retained.diagnostics().isEmpty());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.identity(), retained.identity());
    assertTrue(retained.aggregate().executionScopes().stream().allMatch(scope -> scope instanceof ExecutionScope.Method));
  }

  @Test
  void givenUnicodeEscapedEmptyDeclaration_whenProducedAndDecoded_thenUnsupportedOriginRetainsTheRawEscapeRange() throws IOException {
    // Arrange
    var source = "class Example { \\u003b int field = 1; }";
    var start = source.indexOf("\\u003b");

    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, retained.completion());
    assertEquals(List.of(new SourceSpan(fields(retained).get("field").sourceFileKey(), start, start + 6)),
        retained.diagnostics().stream().filter(value -> value.code().equals("frontend.unsupported-class-member"))
            .flatMap(value -> value.origins().stream()).toList());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.diagnostics(), retained.diagnostics());
  }

  @Test
  void givenUnicodeEscapedCommentAroundASemicolon_whenProduced_thenCommentTextDoesNotBecomeAnUnsupportedDeclaration() throws IOException {
    // Arrange
    var source = "class Example { \\u002f\\u002a ; \\u002a\\u002f int field = 1; }";

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertTrue(result.diagnostics().isEmpty());
    assertEquals("1", written(source, fields(result).get("field").requiredChild(SyntaxRole.INITIALIZER)));
  }

  @Test
  void givenAnEnumConstantListSeparator_whenProduced_thenItIsNotMisclassifiedAsAnEmptyBodyDeclaration() throws IOException {
    // Arrange
    var source = "enum Example { VALUE; static int field = 1; }";

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(value -> value.code().equals("frontend.unsupported-class-member")));
    assertEquals("1", written(source, fields(result).get("field").requiredChild(SyntaxRole.INITIALIZER)));
  }

  @Test
  void givenAnEmptyDeclarationAfterTheEnumSeparator_whenProduced_thenOnlyTheAdditionalDeclarationIsDiagnosed() throws IOException {
    // Arrange
    var source = "enum Example { VALUE; ; static int field = 1; }";
    var start = source.indexOf("; ;") + 2;

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);

    // Assert
    assertEquals(List.of(new SourceSpan(fields(result).get("field").sourceFileKey(), start, start + 1)),
        result.diagnostics().stream().filter(value -> value.code().equals("frontend.unsupported-class-member"))
            .flatMap(value -> value.origins().stream()).toList());
  }

  @Test
  void givenCompactFieldsWithAnImportAndMethodStatements_whenProducedAndDecoded_thenOnlyActualDeclarationGapsAffectCompletion() throws IOException {
    // Arrange
    var source = "import java.util.List; int field = 1; void main() { ; }";

    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertTrue(retained.diagnostics().isEmpty());
    assertEquals("1", written(source, fields(retained).get("field").requiredChild(SyntaxRole.INITIALIZER)));
    assertEquals(original.aggregate(), retained.aggregate());
  }

  @TestFactory
  Stream<DynamicTest> givenCompactOmittedMemberDeclarations_whenProducedDecodedAndOfferedForRetention_thenExactGapsPreventAcquisitionAndPreserveFields() {
    return Stream.of(
        new EmptyDeclarationCase("InterMember", "int field = Math.abs(-1); ; void main() { ; }", "; ;", 2, 1),
        new EmptyDeclarationCase("Trailing", "int field = Math.abs(-1); void main() { ; } ;", "} ;", 2, 1),
        new EmptyDeclarationCase("Imported", "import java.util.List; int field = Math.abs(-1); ; void main() { ; }", "; ;", 2, 1),
        new EmptyDeclarationCase("Unicode", "import java.util.List; /* ; */ int field = Math.abs(-1); \\u003b void main() { String text = \";\"; ; } // ;", "\\u003b", 0, 6))
        .map(test -> DynamicTest.dynamicTest("given" + test.name() + "EmptyDeclaration_whenProducedDecodedAndOfferedForRetention_thenExactGapPreventsAcquisition", () -> {
          // Arrange
          var source = test.source();
          var start = source.indexOf(test.marker()) + test.offset();
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
          var publication = new AstRetentionService(context -> {
            throw new AssertionError("Incomplete compact source must not acquire a repository");
          });

          // Act
          var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_INITIALIZATION);
          var original = outcome.result().orElseThrow();
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
          var attempt = publication.retainAst(outcome);
          var root = retained.aggregate().roots().getFirst();
          var field = root.children(SyntaxRole.COMPACT_MEMBER).getFirst();
          var scope = retained.aggregate().executionSyntax("type:Example#field:field").orElseThrow();

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, original.completion());
          assertEquals(AstCompletion.INCOMPLETE, retained.completion());
          assertEquals(List.of(new SourceSpan(field.sourceFileKey(), start, start + test.length())),
              retained.diagnostics().stream().filter(value -> value.code().equals("frontend.unsupported-class-member"))
                  .flatMap(value -> value.origins().stream()).toList());
          assertEquals(1, retained.diagnostics().size(), retained.diagnostics().toString());
          assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION),
              root.children(SyntaxRole.COMPACT_MEMBER).stream().map(SyntaxNode::syntaxKind).toList());
          assertEquals(root.structuralPath().child(SyntaxRole.COMPACT_MEMBER, 0), field.structuralPath());
          assertEquals("Math.abs(-1)", written(source, field.requiredChild(SyntaxRole.INITIALIZER)));
          assertSame(field, scope.root());
          assertEquals("int", scope.useEvidence(field.nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().erasedName());
          assertEquals("type:java.lang.Math#abs(int)", field.requiredChild(SyntaxRole.INITIALIZER).requiredReference().symbolKey().orElseThrow());
          assertTrue(retained.aggregate().methodSyntax(scope.scopeKey()).isEmpty());
          assertEquals(original.aggregate(), retained.aggregate());
          assertEquals(original.diagnostics(), retained.diagnostics());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
          assertEquals(PublicationStatus.NOT_ELIGIBLE, attempt.status());
          assertEquals(CommitKnowledge.NOT_STARTED, attempt.knowledge());
        }));
  }

  private record EmptyDeclarationCase(String name, String source, String marker, int offset, int length) {
  }

  @TestFactory
  Stream<DynamicTest> givenCompactFieldsWithAbsentScalarAndArrayInitializers_whenPubliclyProducedAndRetained_thenActualOwnerPathsAndFactsSurvive() {
    return Stream.of("int count;", "int count = Math.abs(-1);", "int[] count = {};", "int[] count = {Math.abs(-1)};")
        .map(fieldSource -> DynamicTest.dynamicTest("givenCompactField(" + fieldSource + ")_whenProducedAndRetained_thenActualOwnerPathsAndFactsSurvive", () -> {
          // Arrange
          var source = fieldSource + " void main() { System.out.println(count); }";

          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);
          var retained = AstCodec.decode(AstCodec.encode(result), result.identity());
          var root = retained.aggregate().roots().getFirst();
          var field = root.children(SyntaxRole.COMPACT_MEMBER).getFirst();
          var execution = retained.aggregate().executionSyntax("type:Example#field:count").orElseThrow();

          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.COMPACT), root.value());
          assertTrue(root.children(SyntaxRole.TYPE_DECLARATION).isEmpty());
          assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION),
              root.children(SyntaxRole.COMPACT_MEMBER).stream().map(SyntaxNode::syntaxKind).toList());
          assertEquals(SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.COMPACT_MEMBER, 0), field.structuralPath());
          assertEquals(new SourceSpan(field.sourceFileKey(), 0, fieldSource.length()), field.origin().orElseThrow());
          assertEquals(new ExecutionScope.Field("type:Example#field:count", "type:Example"), execution.scope());
          assertSame(field, execution.root());
          assertTrue(retained.aggregate().methodSyntax(execution.scopeKey()).isEmpty());
          assertEquals(result.aggregate(), retained.aggregate());
          assertArrayEquals(AstCodec.encode(result), AstCodec.encode(retained));
          if (fieldSource.contains("=")) {
            var initializer = field.requiredChild(SyntaxRole.INITIALIZER);
            assertEquals(field.structuralPath().child(SyntaxRole.INITIALIZER, 0), initializer.structuralPath());
            assertEquals(fieldSource.contains("[]") ? "int[]" : "int",
                execution.useEvidence(field.nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().erasedName());
            if (fieldSource.contains("{}")) {
              assertEquals(SyntaxKind.ARRAY_CREATION, initializer.syntaxKind());
              assertTrue(initializer.children(SyntaxRole.ARRAY_ELEMENT).isEmpty());
            } else {
              var call = fieldSource.contains("[]") ? initializer.children(SyntaxRole.ARRAY_ELEMENT).getFirst() : initializer;
              assertEquals("type:java.lang.Math#abs(int)", call.requiredReference().symbolKey().orElseThrow());
              assertEquals("int", execution.useEvidence(call.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
              assertEquals("type:java.lang.Math", execution.staticAccess(call.nodeId()).orElseThrow()
                  .declaringOwner().knownValue().orElseThrow().declarationKey().orElseThrow());
            }
          } else {
            assertTrue(field.children(SyntaxRole.INITIALIZER).isEmpty());
          }
        }));
  }

  @Test
  void givenAbsentEmptyAndPopulatedFieldsAndOrderedBlocks_whenProduced_thenCanonicalSyntaxAndDeclarationFactsSurviveClosure() throws IOException {
    // Arrange
    var source = """
        class Example {
          int[] absent;
          int[] empty = {};
          int[] populated = {compute(1)};
          static int count = compute(2);
          static { count = compute(3); count++; }
          { int local = compute(4); absent = new int[local]; }
          static int compute(int value) { return value; }
        }
        """;

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var fields = fields(result);
    assertTrue(fields.get("absent").children(SyntaxRole.INITIALIZER).isEmpty());
    var absent = fields.get("absent");
    var absentKey = absent.value(SyntaxValue.VariableDeclaration.class).declarationKey();
    var absentScope = result.aggregate().executionSyntax(absentKey).orElseThrow();
    assertEquals(new ExecutionScope.Field("type:Example#field:absent", "type:Example"), absentScope.scope());
    assertSame(absent, absentScope.root());
    assertTrue(absentScope.useEvidence(absent.nodeId(), SyntaxRole.INITIALIZER, 0).isEmpty());
    assertTrue(absentScope.evidence().uses().isEmpty());
    assertEquals(List.of(absent.nodeId()), absentScope.attributedOccurrences().stream()
        .map(occurrence -> occurrence.nodeKey()).toList());
    assertEquals(List.of(), fields.get("empty").requiredChild(SyntaxRole.INITIALIZER).children(SyntaxRole.ARRAY_ELEMENT));
    var populated = fields.get("populated");
    var array = populated.requiredChild(SyntaxRole.INITIALIZER);
    assertEquals(SyntaxKind.ARRAY_CREATION, array.syntaxKind());
    assertEquals(1, array.children(SyntaxRole.ARRAY_ELEMENT).size());
    var call = array.children(SyntaxRole.ARRAY_ELEMENT).getFirst();
    assertEquals(SyntaxKind.INVOCATION, call.syntaxKind());
    assertEquals("type:Example#compute(int)", call.requiredReference().symbolKey().orElseThrow());
    var key = populated.value(SyntaxValue.VariableDeclaration.class).declarationKey();
    var scope = result.aggregate().executionSyntax(key).orElseThrow();
    assertTrue(scope.scope() instanceof ExecutionScope.Field);
    assertSame(populated, scope.root());
    assertFalse(result.aggregate().methodSyntax(key).isPresent());
    assertEquals("int[]", scope.useEvidence(populated.nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().displayName());
    assertEquals("int", scope.useEvidence(call.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
    var blocks = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.STATIC_INITIALIZER_BLOCK
        || node.syntaxKind() == SyntaxKind.INSTANCE_INITIALIZER_BLOCK).toList();
    assertEquals(List.of(SyntaxKind.STATIC_INITIALIZER_BLOCK, SyntaxKind.INSTANCE_INITIALIZER_BLOCK), blocks.stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(List.of(2, 2), blocks.stream().map(node -> node.children(SyntaxRole.STATEMENT).size()).toList());
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    assertEquals(List.of(typePath.child(SyntaxRole.MEMBER, 4), typePath.child(SyntaxRole.MEMBER, 5)),
        blocks.stream().map(SyntaxNode::structuralPath).toList());
    assertEquals(List.of("count = compute(3);", "count++;"),
        blocks.getFirst().children(SyntaxRole.STATEMENT).stream().map(node -> written(source, node)).toList());
    assertEquals(List.of("int local = compute(4);", "absent = new int[local];"),
        blocks.getLast().children(SyntaxRole.STATEMENT).stream().map(node -> written(source, node)).toList());
    assertEquals(List.of(SyntaxKind.EXPRESSION_STATEMENT, SyntaxKind.EXPRESSION_STATEMENT),
        blocks.getFirst().children(SyntaxRole.STATEMENT).stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.EXPRESSION_STATEMENT),
        blocks.getLast().children(SyntaxRole.STATEMENT).stream().map(SyntaxNode::syntaxKind).toList());
    for (var block : blocks) {
      var owner = (ExecutionScope.Initializer) result.aggregate().executionScopes().stream()
          .filter(value -> value instanceof ExecutionScope.Initializer initializer && initializer.origin().equals(block.origin().orElseThrow()))
          .findFirst().orElseThrow();
      assertEquals("type:Example", owner.ownerKey());
      var startText = block.syntaxKind() == SyntaxKind.STATIC_INITIALIZER_BLOCK ? "static { count" : "{ int local";
      var start = source.indexOf(startText);
      var end = source.indexOf('}', start) + 1;
      assertEquals(new SourceSpan(block.sourceFileKey(), start, end), block.origin().orElseThrow());
      assertEquals("type:Example:initializer:" + start + ":" + end, owner.key());
      assertSame(block, result.aggregate().executionSyntax(owner.key()).orElseThrow().root());
      var execution = result.aggregate().executionSyntax(owner.key()).orElseThrow();
      var blockCall = block.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
      assertEquals("type:Example#compute(int)", execution.staticAccess(blockCall.nodeId()).orElseThrow().selectedMemberKey());
      assertEquals("type:Example", execution.staticAccess(blockCall.nodeId()).orElseThrow().declaringOwner().knownValue().orElseThrow().declarationKey().orElseThrow());
      assertEquals(SourceOwnerPath.root().child(SourceOwnerPath.Role.TYPE_DECLARATION, 0)
          .child(SourceOwnerPath.Role.MEMBER, block.syntaxKind() == SyntaxKind.STATIC_INITIALIZER_BLOCK ? 4 : 5),
          result.aggregate().writtenSourceAssociations().stream().filter(value -> value.nodeKey().equals(block.nodeId()))
              .findFirst().orElseThrow().ownerPath());
      for (var index = 0; index < 2; index++) {
        var statement = block.children(SyntaxRole.STATEMENT).get(index);
        assertEquals(block.structuralPath().child(SyntaxRole.STATEMENT, index), statement.structuralPath());
        var text = written(source, statement);
        var offset = source.indexOf(text, start);
        assertEquals(new SourceSpan(block.sourceFileKey(), offset, offset + text.length()), statement.origin().orElseThrow());
      }
    }
    var instance = blocks.getLast();
    var local = instance.children(SyntaxRole.STATEMENT).getFirst();
    var localStart = source.indexOf("int local = compute(4);");
    var localKey = "type:Example:initializer:" + source.indexOf("{ int local") + ":"
        + (source.indexOf('}', source.indexOf("{ int local")) + 1) + ":local_variable:" + localStart + ":" + (localStart + "int local = compute(4);".length());
    assertEquals(localKey, local.requiredReference().symbolKey().orElseThrow());
    var access = instance.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS).findFirst().orElseThrow();
    assertEquals("local", access.value(SyntaxValue.SourceSymbol.class).name());
    assertEquals(localKey, access.requiredReference().symbolKey().orElseThrow());
    assertEquals(instance.structuralPath().child(SyntaxRole.STATEMENT, 1).child(SyntaxRole.EXPRESSION, 0)
        .child(SyntaxRole.VALUE, 0).child(SyntaxRole.DIMENSION, 0), access.structuralPath());
    var staticScope = result.aggregate().executionSyntax("type:Example#field:count").orElseThrow();
    var staticCall = staticScope.root().requiredChild(SyntaxRole.INITIALIZER);
    assertEquals("type:Example", staticScope.staticAccess(staticCall.nodeId()).orElseThrow().declaringOwner().knownValue().orElseThrow().declarationKey().orElseThrow());
    assertEquals(2, result.aggregate().executionScopes().stream().filter(ExecutionScope.Initializer.class::isInstance).count());
    assertTrue(result.aggregate().methodBodyKeys().stream().noneMatch(value -> value.contains("initializer")));
    assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
  }

  @Test
  void givenDeclarationExecutionScopes_whenEncodedAndDecoded_thenFactsTopologyAndProfileRemainIdentical() throws IOException {
    // Arrange
    var original = produce("class Example { static int field = compute(1); static { field++; } static int compute(int x) { return x; } }",
        AstProfile.JAVA_SOURCE_INITIALIZATION);

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(original.identity(), retained.identity());
    assertEquals(original.aggregate(), retained.aggregate());
    assertEquals(original.coverage(), retained.coverage());
    assertEquals(AstProfile.JAVA_SOURCE_INITIALIZATION, retained.profile());
    assertEquals(AstResult.ENVIRONMENT_IDENTITY_MODEL_VERSION, retained.identity().sourceModelVersion());
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
  }

  @TestFactory
  Stream<DynamicTest> givenSupportedInitializationVariants_whenProduced_thenExecutableDescendantsAndOwnersRemainComplete() {
    return Stream.of(
        "class Example { int[] left = {1}, right[] = {{2}}; }",
        "interface Example { int CONSTANT = Integer.parseInt(\"3\"); }",
        "class Example { static int count; java.util.function.IntUnaryOperator action = x -> x + count; }",
        "class Example { java.util.function.IntUnaryOperator action = x -> { int value = x + 1; return value; }; }",
        "class Example { java.util.function.Supplier<int[]> action = () -> new int[]{1, 2}; }",
        "class Example { java.util.function.IntUnaryOperator action = Math::abs; }",
        "class Example { int value = 1; java.util.function.IntSupplier action = () -> this.value; }",
        "class Example { java.util.function.IntUnaryOperator action = _ -> 1; { var _ = Math.abs(-2); } }",
        "class Example { java.util.function.Supplier<String> action = String::new; }",
        "class Example { int total; { for (int i = 0; i < 2; i++) { total += i; } } }",
        "class Example { int total; { try (var reader = new java.io.StringReader(\"abc\")) { total = reader.read(); } "
            + "catch (java.io.IOException | RuntimeException problem) { total = 0; } finally { total++; } } }",
        "class Example { static {} {} Example() {} }",
        "class Example { int[][] matrix = new int[2][]; int value = switch (1) { case 1 -> 2; default -> 3; }; }")
        .map(source -> DynamicTest.dynamicTest("givenSupportedInitialization(" + source + ")_whenProduced_thenSourceCompletenessAndCodecValuesSurvive", () -> {
          // Arrange
          var profile = AstProfile.JAVA_SOURCE_INITIALIZATION;

          // Act
          var result = produce(source, profile);
          var retained = AstCodec.decode(AstCodec.encode(result), result.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(result.aggregate(), retained.aggregate());
          assertTrue(result.aggregate().executionScopes().stream().filter(scope -> !(scope instanceof ExecutionScope.Method))
              .allMatch(scope -> result.aggregate().methodSyntax(scope.key()).isEmpty()));
          assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
          for (var field : fields(result).values()) {
            var declaration = field.value(SyntaxValue.VariableDeclaration.class);
            assertEquals(declaration.initializer() ? 1 : 0, field.children(SyntaxRole.INITIALIZER).size());
          }
        }));
  }

  @Test
  void givenFieldAndBlockInitialization_whenHistoricalProfilesProduce_thenTheirExistingCapabilityBoundaryRemainsIncomplete() throws IOException {
    // Arrange
    var source = "class Example { int field = 1; { field++; } }";

    // Act
    var bounded = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var expanded = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
    assertEquals(AstCompletion.INCOMPLETE, expanded.completion());
    assertTrue(bounded.aggregate().executionScopes().stream().allMatch(ExecutionScope.Method.class::isInstance));
    assertTrue(expanded.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.unsupported-field-initializer")));
  }

  @Test
  void givenSharedAnnotatedFieldsWithDifferentDimensions_whenProduced_thenEachInitializerHasItsOwnTypeAndOneWrittenAnnotationOccurrence() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Mark {}
        class Example { @Mark int @Mark [] first = {1}, second @Mark [] = {{2}}; }
        """;

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var fields = fields(result);
    assertEquals("int[]", fields.get("first").value(SyntaxValue.VariableDeclaration.class).declaredType().erasedName());
    assertEquals("int[][]", fields.get("second").value(SyntaxValue.VariableDeclaration.class).declaredType().erasedName());
    assertEquals("@Mark int[]", fields.get("first").requiredChild(SyntaxRole.INITIALIZER).resolvedType().orElseThrow().displayName());
    assertEquals("@Mark int[] @Mark []", fields.get("second").requiredChild(SyntaxRole.INITIALIZER).resolvedType().orElseThrow().displayName());
    var annotations = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION).toList();
    assertEquals(annotations.size(), annotations.stream().map(node -> node.origin().orElseThrow()).distinct().count());
    assertEquals(List.of("type:Example#field:first", "type:Example#field:second"),
        fields.get("first").value(SyntaxValue.VariableDeclaration.class).sharedDeclaratorKeys());
  }

  @Test
  void givenDeclarationInitializationAndUnsupportedMethodSyntax_whenCfgRefuses_thenCompleteSourceAndItsEncodedContentRemainUnchanged() throws IOException {
    // Arrange
    var result = produce("class Example { int field = 1; { field++; } int subject() { for (int i=0; i<1; i++) { field++; } return field; } }",
        AstProfile.JAVA_SOURCE_INITIALIZATION);
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var before = AstCodec.encode(result);

    // Act
    var cfg = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
    assertEquals(AstCompletion.COMPLETE, result.completion());
    assertArrayEquals(before, AstCodec.encode(result));
    assertEquals(1, result.aggregate().executionScopes().stream().filter(ExecutionScope.Initializer.class::isInstance).count());
  }

  @Test
  void givenInvalidInitializerAssignment_whenCompilerReportsItsError_thenSourceCannotBeComplete() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { int field = \"invalid\"; }")));

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_INITIALIZATION);

    // Assert
    assertFalse(outcome.completion() == AstCompletion.COMPLETE);
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")));
  }

  @TestFactory
  Stream<DynamicTest> givenSharedTypeUseAnnotationsInFieldMethodAndInitializerScopes_whenProducedAndDecoded_thenAllConsumersRetainOnePhysicalAnnotation() {
    return Stream.of("@Mark int @Mark [] first={1}, second @Mark []={{2}};",
        "void subject() { @Mark int @Mark [] first={1}, second @Mark []={{2}}; }",
        "{ @Mark int @Mark [] first={1}, second @Mark []={{2}}; }")
        .map(member -> DynamicTest.dynamicTest("givenSharedTypeUseAnnotations(" + member + ")_whenProducedAndDecoded_thenOnePhysicalAnnotationIsRetained", () -> {
          // Arrange
          var source = "import java.lang.annotation.ElementType; import java.lang.annotation.Target; "
              + "@Target({ElementType.FIELD,ElementType.TYPE_USE,ElementType.LOCAL_VARIABLE}) @interface Mark {} class Example { " + member + " }";

          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);
          var retained = AstCodec.decode(AstCodec.encode(result), result.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(result.aggregate(), retained.aggregate());
          var shared = result.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.SharedTypeAnnotations).toList();
          assertEquals(1, shared.size());
          var annotationKey = shared.getFirst().value(SyntaxValue.SharedTypeAnnotations.class).annotationNodeKeys().getFirst();
          assertEquals(1, result.aggregate().nodes().stream().filter(node -> node.nodeId().equals(annotationKey)).count());
          var declarations = result.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.VariableDeclaration variable
              && List.of("first", "second").contains(variable.name()) || node.value() instanceof SyntaxValue.LocalDeclaration local
                  && List.of("first", "second").contains(local.name())).toList();
          var first = declarations.getFirst();
          var second = declarations.getLast();
          var firstType = first.requiredChild(SyntaxRole.DECLARED_TYPE);
          var secondType = second.requiredChild(SyntaxRole.DECLARED_TYPE);
          var firstPrimitive = firstType.descendantsAndSelf().stream().filter(node -> node.sourceKind() == JavaSourceKind.PRIMITIVE_TYPE).findFirst().orElseThrow();
          var secondPrimitive = secondType.descendantsAndSelf().stream().filter(node -> node.sourceKind() == JavaSourceKind.PRIMITIVE_TYPE).findFirst().orElseThrow();
          var marker = first.children(SyntaxRole.ANNOTATION).getFirst();
          assertEquals(List.of(new AnnotationApplication(first.nodeId(), Optional.of(first.nodeId()), Optional.of(new Written(firstPrimitive.nodeId())), List.of(),
              List.of(new FieldDeclaratorApplication(second.nodeId(), true, Optional.of(new Written(secondPrimitive.nodeId())))))),
              annotationTargets(result, marker.nodeId()));
          assertEquals(List.of(new AnnotationApplication(firstType.nodeId(), Optional.empty(), Optional.of(new Written(firstType.nodeId())), List.of(),
              List.of(new FieldDeclaratorApplication(second.nodeId(), false, Optional.of(new Written(shared.getFirst().nodeId())))))),
              annotationTargets(result, annotationKey));
          var secondDimension = secondType.children(SyntaxRole.ANNOTATION).getFirst();
          assertEquals(List.of(new AnnotationApplication(secondType.nodeId(), Optional.empty(), Optional.of(new Written(secondType.nodeId())), List.of(), List.of())),
              annotationTargets(result, secondDimension.nodeId()));
        }));
  }

  private static Map<String, SyntaxNode> fields(AstResult result) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION)
        .collect(Collectors.toMap(node -> node.value(SyntaxValue.VariableDeclaration.class).name(), Function.identity()));
  }

  private static List<AnnotationApplication> annotationTargets(AstResult result, String annotationKey) {
    return result.aggregate().semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotationKey)
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).map(edge -> (AnnotationApplication) edge.target()).toList();
  }

  private static String written(String source, SyntaxNode node) {
    var origin = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
  }

  @Test
  void givenNumericFieldAndNestedLambdasInFieldAndBlockScopes_whenProduced_thenSelectedTypesAndLexicalIdentitiesAreExact() throws IOException {
    // Arrange
    var source = "class Example { long widened = 1; java.util.function.IntFunction<java.util.function.IntUnaryOperator> field = outer -> inner -> outer + inner; "
        + "{ java.util.function.IntFunction<java.util.function.IntUnaryOperator> local = outer -> inner -> outer + inner; } }";

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var widened = fields(result).get("widened");
    var conversion = result.aggregate().executionSyntax("type:Example#field:widened").orElseThrow()
        .useDecision(widened.nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow();
    assertEquals("int", conversion.sourceType().displayName());
    assertEquals("long", conversion.selectedTarget().displayName());
    assertTrue(conversion.unboxedType().isEmpty());
    assertTrue(conversion.boxedType().isEmpty());
    var field = result.aggregate().executionSyntax("type:Example#field:field").orElseThrow();
    var initializer = result.aggregate().executionScopes().stream().filter(ExecutionScope.Initializer.class::isInstance).findFirst().orElseThrow();
    var block = result.aggregate().executionSyntax(initializer.key()).orElseThrow();
    for (var scope : List.of(field, block)) {
      var lambdas = scope.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).toList();
      assertEquals(2, lambdas.size());
      assertEquals("java.util.function.IntUnaryOperator", scope.useEvidence(lambdas.getFirst().nodeId(), SyntaxRole.BODY, 0).orElseThrow().selectedTarget().erasedName());
      assertEquals("int", scope.useEvidence(lambdas.getLast().nodeId(), SyntaxRole.BODY, 0).orElseThrow().selectedTarget().displayName());
      for (var index = 0; index < 2; index++) {
        var parameter = lambdas.get(index).children(SyntaxRole.PARAMETER).getFirst();
        var name = index == 0 ? "outer" : "inner";
        var offset = source.indexOf(name + " ->", Math.toIntExact(scope.root().origin().orElseThrow().startOffset()));
        var key = scope.scopeKey() + ":parameter:" + offset + ":" + (offset + name.length());
        assertEquals(name, parameter.value(SyntaxValue.LocalDeclaration.class).name());
        assertEquals("int", parameter.resolvedType().orElseThrow().displayName());
        assertEquals(key, parameter.requiredReference().symbolKey().orElseThrow());
        var accesses = scope.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS
            && node.value(SyntaxValue.SourceSymbol.class).name().equals(name)).toList();
        assertEquals(1, accesses.size());
        assertEquals(key, accesses.getFirst().requiredReference().symbolKey().orElseThrow());
      }
    }
  }

  @Test
  void givenMemberInitializationAndSimpleMethods_whenStandardApplicationSelectsAllBodies_thenOnlyActualMethodGraphsAreAvailable() throws IOException {
    // Arrange
    var source = "class Example { int field=41; static { int staticLocal=42; } { int instanceLocal=43; } Object input=\"abc\"; boolean matched=input instanceof String value && value.length()>0; int subject() { return 7; } }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());

    // Act
    var absent = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_INITIALIZATION, Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var run = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_INITIALIZATION, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));

    // Assert
    var result = run.representation().ast().result().orElseThrow();
    var cfg = run.representation().cfg().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, result.completion());
    assertArrayEquals(AstCodec.encode(absent.representation().ast().result().orElseThrow()), AstCodec.encode(result));
    assertEquals(List.of("type:Example#<init>()", "type:Example#subject()"), cfg.request().methodKeys());
    assertEquals(List.of(MethodCfgState.AVAILABLE, MethodCfgState.AVAILABLE), cfg.methods().stream().map(method -> method.state()).toList());
    var graph = cfg.methods().getLast().graph().orElseThrow();
    assertEquals(List.of(OperationKind.CONSTANT, OperationKind.RETURN), graph.operationNodes().stream().map(node -> node.kind()).toList());
    assertEquals(List.of("7"), graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CONSTANT)
        .map(node -> node.detail()).toList());
    assertEquals(List.of("7"), graph.values().stream().flatMap(value -> value.literal().stream()).toList());
    assertEquals(List.of(new SourceSpan(snapshot.sourceFiles().getFirst().sourceFileKey(), source.indexOf("7;"), source.indexOf("7;") + 1)),
        graph.operationNodes().getFirst().origins());
    assertTrue(cfg.methods().getFirst().graph().orElseThrow().values().stream().flatMap(value -> value.literal().stream()).toList().isEmpty());
    var start = source.indexOf("{ return 7;");
    assertTrue(graph.operationNodes().stream().flatMap(node -> node.origins().stream())
        .allMatch(origin -> origin.startOffset() >= start && origin.endOffsetExclusive() <= start + "{ return 7; }".length()));
    for (var scope : result.aggregate().executionScopes().stream().filter(value -> !(value instanceof ExecutionScope.Method)).toList()) {
      var rejected = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(scope.key()), CfgProfile.JAVA_BOUNDED));
      assertEquals(CfgState.REJECTED, rejected.state());
      assertTrue(rejected.diagnostics().stream().anyMatch(value -> value.code().equals("control-flow.unknown-method")));
      assertTrue(rejected.methods().isEmpty());
    }
    assertArrayEquals(AstCodec.encode(absent.representation().ast().result().orElseThrow()), AstCodec.encode(result));
  }

  @Test
  void givenMemberInitializationThroughApplicationComposition_whenCfgIsAbsentOrRefused_thenTheRealProducerResultKeepsItsCanonicalDeclarationScopes() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { int field = Math.abs(-1); { field++; } int subject() { for (int i=0;i<1;i++) { field++; } return field; } }")));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());

    // Act
    var withoutCfg = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_INITIALIZATION, Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var refusedCfg = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_INITIALIZATION, Optional.of(new CfgSelection(List.of("type:Example#subject()"), CfgProfile.JAVA_BOUNDED)),
        PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertEquals(AstCompletion.COMPLETE, withoutCfg.representation().ast().completion());
    assertEquals(AstCompletion.COMPLETE, refusedCfg.representation().ast().completion());
    assertArrayEquals(AstCodec.encode(withoutCfg.representation().ast().result().orElseThrow()),
        AstCodec.encode(refusedCfg.representation().ast().result().orElseThrow()));
    assertTrue(withoutCfg.representation().cfg().isEmpty());
    assertEquals(MethodCfgState.UNSUPPORTED, refusedCfg.representation().cfg().orElseThrow().methods().getFirst().state());
  }

  @Test
  void givenRealMemberInitializationAndControlledCfgConstructionFailure_whenApplicationCoordinates_thenTheEstablishedSourceAndActualCauseSurvive() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"),
        "class Example { int field = 1; { field++; } int subject() { return field; } }")));
    var cause = new IllegalStateException("Controlled construction failure");
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), RecoverableCfgFailures.atMethod("type:Example#subject()", cause));

    // Act
    var run = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_INITIALIZATION,
        Optional.of(new CfgSelection(List.of("type:Example#subject()"), CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));

    // Assert
    assertEquals(AstCompletion.COMPLETE, run.representation().ast().completion());
    var failedMethod = run.representation().cfg().orElseThrow().methods().getFirst();
    assertEquals(MethodCfgState.FAILED, failedMethod.state());
    assertSame(cause, failedMethod.failure().orElseThrow());
    assertEquals(1, run.representation().ast().result().orElseThrow().aggregate().executionScopes().stream().filter(ExecutionScope.Field.class::isInstance).count());
  }

  @TestFactory
  Stream<DynamicTest> givenPatternsInDeclarationExecutions_whenPubliclyProducedAndDecoded_thenExactBindingFlowScopesSurviveCompilerClosure() {
    return Stream.of(
        new PatternCase("FieldInstanceof", "class Example { Object input=\"abc\"; boolean matched=input instanceof String value && value.length()>0; }"),
        new PatternCase("GuardedPatternSwitch", "class Example { static Object input=\"abc\"; static int length=switch(input) { case String value when value.length()>0 -> value.length(); default -> 0; }; }"),
        new PatternCase("RecordPattern", "record Box(String text) {} class Example { Object input=new Box(\"abc\"); boolean matched=input instanceof Box(String value) && value.length()>0; }"),
        new PatternCase("StaticBlock", "class Example { static Object input=\"abc\"; static { if(input instanceof String value) { int length=value.length(); } } }"),
        new PatternCase("InstanceBlock", "class Example { Object input=\"abc\"; { if(input instanceof String value) { int length=value.length(); } } }"),
        new PatternCase("DeferredFieldLambda", "class Example { java.util.function.Function<Object,Integer> function=candidate -> candidate instanceof String value ? value.length() : 0; }"),
        new PatternCase("UnusedFieldBinding", "class Example { Object input=\"abc\"; boolean matched=input instanceof String value; }"),
        new PatternCase("CompactField", "Object input=\"abc\"; boolean matched=input instanceof String value && value.length()>0; void main() {}"))
        .map(test -> DynamicTest.dynamicTest("given" + test.name() + "_whenProducedAndDecoded_thenExactBindingAndAccessOccurrencesSurvive", () -> {
          // Arrange
          var source = test.source();
          var declarationStart = source.indexOf("String value");
          var declarationEnd = declarationStart + "String value".length();
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
          var sourceKey = snapshot.sourceFiles().getFirst().sourceFileKey();
          var expectedAccesses = new ArrayList<SourceSpan>();
          for (var start = source.indexOf("value.length"); start >= 0; start = source.indexOf("value.length", start + 1)) {
            expectedAccesses.add(new SourceSpan(sourceKey, start, start + "value".length()));
          }
          var expectedScope = switch (test.name()) {
            case "StaticBlock" -> "type:Example:initializer:" + source.indexOf("static {") + ':' + (source.lastIndexOf('}') - 1);
            case "InstanceBlock" -> "type:Example:initializer:" + source.indexOf("{ if") + ':' + (source.lastIndexOf('}') - 1);
            case "GuardedPatternSwitch" -> "type:Example#field:length";
            case "DeferredFieldLambda" -> "type:Example#field:function";
            default -> "type:Example#field:matched";
          };
          var expectedKey = expectedScope + ":binding_variable:" + declarationStart + ':' + declarationEnd;

          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
          var scope = retained.aggregate().executionScopes().stream()
              .map(value -> retained.aggregate().executionSyntax(value.key()).orElseThrow())
              .filter(value -> value.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN))
              .findFirst().orElseThrow();
          var pattern = scope.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN).findFirst().orElseThrow();
          var binding = pattern.requiredChild(SyntaxRole.PARAMETER);

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
          assertTrue(original.diagnostics().isEmpty());
          assertEquals(expectedScope, scope.scopeKey());
          assertEquals(Optional.of(expectedAccesses), pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses());
          assertEquals(Optional.of(expectedKey), binding.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey());
          assertEquals(Optional.of(new SourceSpan(sourceKey, declarationStart, declarationEnd)), binding.origin());
          var accesses = scope.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS)
              .filter(node -> node.value(SyntaxValue.SourceSymbol.class).name().equals("value")).toList();
          assertEquals(expectedAccesses, accesses.stream().map(node -> node.origin().orElseThrow()).toList());
          assertTrue(accesses.stream().allMatch(node -> node.semanticReference().orElseThrow().symbolKey().equals(Optional.of(expectedKey))));
          assertEquals(original.aggregate(), retained.aggregate());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
          assertTrue(retained.aggregate().methodSyntax(scope.scopeKey()).isEmpty());
        }));
  }

  private record PatternCase(String name, String source) {
  }
}
