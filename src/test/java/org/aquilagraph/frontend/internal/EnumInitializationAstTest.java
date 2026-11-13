package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnumInitializationAstTest {
  @TempDir Path directory;

  @Test
  void givenEnumArgumentAndInitializedConstantBody_whenProducedAndDecoded_thenDirectChildrenAndSeparateMethodRemain() throws IOException {
    // Arrange
    var source = "enum Example { FIRST(Math.abs(-2)) { int count = 3; int value() { return count; } }, SECOND(4); Example(int value) {} }";

    // Act
    var ast = produce(source);
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    var constants = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.coverage().validationEvidence().outcomes().stream()
        .filter(value -> value.support().mode() == FeatureContextOutcome.Mode.FAILED)
        .map(value -> value.address() + "=" + value.encounteredValue()).toList().toString());
    assertEquals(2, constants.size());
    var first = constants.getFirst();
    assertEquals(SyntaxKind.INVOCATION, first.children(SyntaxRole.ARGUMENT).getFirst().syntaxKind());
    var body = first.requiredChild(SyntaxRole.CLASS_BODY);
    assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION), body.children(SyntaxRole.MEMBER).stream().map(node -> node.syntaxKind()).toList());
    var method = body.children(SyntaxRole.MEMBER).getLast();
    assertEquals(method.requiredChild(SyntaxRole.BODY), retained.body(method.value(SyntaxValue.ExecutableDeclaration.class).declarationKey()).orElseThrow().root());
    assertTrue(constants.getLast().children(SyntaxRole.CLASS_BODY).isEmpty());
    assertEquals("4", constants.getLast().children(SyntaxRole.ARGUMENT).getFirst().value(SyntaxValue.Literal.class).text());
  }

  @Test
  void givenAbsentEmptyAndPopulatedConstantForms_whenProduced_thenWrittenParenthesesAndBodiesRemainDistinct() throws IOException {
    // Arrange
    var source = "enum Example { PLAIN, EMPTY(), BODY {}, BOTH() {}, VALUE(2) { int same() { return 2; } }, OTHER(3) { int same() { return 3; } }; Example() {} Example(int value) {} }";

    // Act
    var ast = produce(source);
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    var constants = constants(retained);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
    assertEquals(List.of(false, true, false, true, true, true), constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).argumentList()).toList());
    assertEquals(List.of(false, false, true, true, true, true), constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).constantBody()).toList());
    assertTrue(constants.get(2).requiredChild(SyntaxRole.CLASS_BODY).children(SyntaxRole.MEMBER).isEmpty());
    var bodies = constants.subList(4, 6).stream().map(node -> node.requiredChild(SyntaxRole.CLASS_BODY)).toList();
    assertEquals(2, bodies.stream().map(node -> node.value(SyntaxValue.TypeDeclaration.class).declarationKey()).distinct().count());
    assertEquals(2, bodies.stream().map(node -> node.children(SyntaxRole.MEMBER).getFirst().value(SyntaxValue.ExecutableDeclaration.class).declarationKey()).distinct().count());
    for (var constant : constants) {
      var key = constant.value(SyntaxValue.VariableDeclaration.class).declarationKey();
      var execution = retained.aggregate().executionSyntax(key).orElseThrow();
      assertEquals(constant, execution.root());
      assertTrue(((ExecutionScope.EnumConstant) execution.scope()).selectedConstructor().orElseThrow().symbolKey().orElseThrow().contains("#<init>("));
      assertEquals(constant.value(SyntaxValue.VariableDeclaration.class).arguments(), execution.evidence().uses().stream().filter(use -> use.consumerNodeId().equals(constant.nodeId())).count());
    }
  }

  @Test
  void givenOverloadedAndVariableArityConstructors_whenProduced_thenWrittenArgumentTargetsAndSourceConstructorSelectionRemain() throws IOException {
    // Arrange
    var source = "enum Example { INTEGER(1), LONG(2L), NONE(), MANY(\"a\", \"b\"), ARRAY(new String[] {\"c\"}) {}; Example(int value) {} Example(long value) {} Example(String... values) {} }";

    // Act
    var ast = produce(source);
    var constants = constants(ast);
    var executions = constants.stream().map(node -> ast.aggregate().executionSyntax(node.value(SyntaxValue.VariableDeclaration.class).declarationKey()).orElseThrow()).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), details(ast));
    assertEquals(List.of("int", "long", "java.lang.String", "java.lang.String[]"), List.of(
        executions.get(0).useEvidence(constants.get(0).nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName(),
        executions.get(1).useEvidence(constants.get(1).nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName(),
        executions.get(3).useEvidence(constants.get(3).nodeId(), SyntaxRole.ARGUMENT, 1).orElseThrow().selectedTarget().displayName(),
        executions.get(4).useEvidence(constants.get(4).nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName()));
    assertEquals(List.of(InvocationApplicabilityMode.FIXED, InvocationApplicabilityMode.FIXED, InvocationApplicabilityMode.VARIABLE, InvocationApplicabilityMode.VARIABLE, InvocationApplicabilityMode.FIXED), executions.stream().map(execution -> execution.evidence().invocations().stream().filter(value -> value.consumerNodeId().equals(execution.root().nodeId())).findFirst().orElseThrow().applicability().knownValue().orElseThrow().mode()).toList());
    assertEquals(2, constants.get(3).children(SyntaxRole.ARGUMENT).size());
    assertEquals(List.of("a", "b"), constants.get(3).children(SyntaxRole.ARGUMENT).stream().map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
  }

  @TestFactory
  Stream<DynamicTest> givenNestedEnumInitializationVariants_whenProducedAndDecoded_thenCanonicalRecursiveValuesRemainCases() {
    // Arrange
    var sources = Stream.of(
        "enum Example { FIRST((java.util.function.IntSupplier) () -> { class Local { int value() { return 3; } } return new Local().value(); }) { int[] array = {}; { int n = 1; n++; } int value() { return array.length; } }; Example(java.util.function.IntSupplier action) {} }",
        "class Example { int subject() { enum Choice { FIRST(1) { int value() { return 2; } }, SECOND(3); Choice(int n) {} } return Choice.FIRST.ordinal(); } }",
        "enum Example { FIRST(new Object() { int field = 2; int value() { return field; } }) { class Member { int value() { return 4; } } }; Example(Object value) {} }",
        "enum Example { FIRST /* parentheses () and braces {} */ (), SECOND {} ; Example() {} }",
        "enum Example { F" + "\\" + "u0049RST " + "\\" + "u0028" + "\\" + "u0029" + " {}; Example() {} }",
        "import java.lang.annotation.*; @Target(ElementType.FIELD) @interface Marker {} enum Example { @Marker FIRST(1) {}; Example(int n) {} }",
        "enum Example { FIRST; Object field = new Object(); Example() {} }");
    // Act
    var cases = sources.map(source -> DynamicTest.dynamicTest("givenNestedEnumInitializationVariant_whenProducedAndDecoded_thenCanonicalRecursiveValuesRemain [" + source + "]", () -> {
      // Arrange
      var original = produce(source);
      // Act
      var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
      // Assert
      assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
      assertEquals(original.aggregate().nodes(), retained.aggregate().nodes());
      assertEquals(original.aggregate().executionScopes(), retained.aggregate().executionScopes());
      for (var type : retained.program().types()) {
        if (type.nesting().kind() == JavaTypeNesting.Kind.ANONYMOUS) {
          assertEquals(1, retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration declaration && declaration.declarationKey().equals(type.typeKey())).count());
        }
      }
      for (var constant : constants(retained)) {
        var declaration = constant.value(SyntaxValue.VariableDeclaration.class);
        assertEquals(declaration.arguments(), constant.children(SyntaxRole.ARGUMENT).size());
        assertEquals(declaration.constantBody() ? 1 : 0, constant.children(SyntaxRole.CLASS_BODY).size());
        assertEquals(declaration.ownerKey(), ((ExecutionScope.EnumConstant) retained.aggregate().executionSyntax(declaration.declarationKey()).orElseThrow().scope()).ownerKey());
      }
    }));
    // Assert
    return cases;
  }

  @Test
  void givenGenericEnumConstantsWithAndWithoutABody_whenProducedAndDecoded_thenEachRetainsItsInferredArgumentTarget() throws IOException {
    // Arrange
    var source = """
        enum Example {
          TEXT("a"), WITH_BODY("b") {}, BUILDER(new StringBuilder()) {};
          <T extends CharSequence> Example(T value) {}
        }
        """;

    // Act
    var original = produce(source);
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    for (var ast : List.of(original, retained)) {
      assertEquals(AstCompletion.COMPLETE, ast.completion(), details(ast));
      var constants = constants(ast);
      assertEquals(List.of(0, 1, 1), constants.stream().map(node -> node.children(SyntaxRole.CLASS_BODY).size()).toList());
      var expectedTargets = List.of("java.lang.String", "java.lang.String", "java.lang.StringBuilder");
      for (var index = 0; index < constants.size(); index++) {
        var constant = constants.get(index);
        var declaration = constant.value(SyntaxValue.VariableDeclaration.class);
        var execution = ast.aggregate().executionSyntax(declaration.declarationKey()).orElseThrow();
        assertEquals(constant, execution.root());
        assertEquals(declaration.ownerKey(), ((ExecutionScope.EnumConstant) execution.scope()).ownerKey());
        assertEquals(1, constant.children(SyntaxRole.ARGUMENT).size());
        var argument = execution.useEvidence(constant.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
        assertEquals(expectedTargets.get(index), argument.selectedTarget().displayName());
        var applicability = execution.invocationEvidence(constant.nodeId()).orElseThrow().applicability().knownValue().orElseThrow();
        assertEquals(InvocationApplicabilityMode.FIXED, applicability.mode());
        assertEquals(1, applicability.formalCount());
      }
    }
    assertEquals(original.aggregate().nodes(), retained.aggregate().nodes());
    assertEquals(original.aggregate().executionScopes(), retained.aggregate().executionScopes());
  }

  @Test
  void givenInvalidEnumConstructorArgument_whenProduced_thenRealCompilerErrorsPreventCompletion() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "enum Example { FIRST(\"invalid\"); Example(int n) {} }")));
    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    // Assert
    assertTrue(outcome.result().isEmpty() || outcome.result().orElseThrow().completion() != AstCompletion.COMPLETE);
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.") && diagnostic.isError()), outcome.diagnostics().toString());
  }

  @Test
  void givenAnEmptyBodyDeclarationInAConstant_whenProduced_thenTheRemainingDeclarationFidelityGapStaysTruthful() throws IOException {
    // Arrange
    var source = "enum Example { FIRST { ; }; Example() {} }";
    // Act
    var ast = produce(source);
    // Assert
    assertEquals(AstCompletion.INCOMPLETE, ast.completion());
    assertTrue(ast.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-class-member")));
  }

  private static List<SyntaxNode> constants(AstResult ast) {
    return ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).toList();
  }

  private static String details(AstResult ast) {
    return ast.diagnostics() + "\n" + ast.coverage().validationEvidence().outcomes().stream().filter(value -> value.support().mode() == FeatureContextOutcome.Mode.FAILED).map(value -> value.address() + "=" + value.encounteredValue()).toList();
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    assertTrue(outcome.result().isPresent(), outcome.diagnostics().toString());
    return outcome.result().orElseThrow();
  }
}
