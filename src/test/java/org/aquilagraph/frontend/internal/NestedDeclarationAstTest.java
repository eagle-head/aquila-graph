package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationTypeLocation.Written;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication.FieldDeclaratorApplication;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeModifier;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.AstOutcome;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NestedDeclarationAstTest {
  @TempDir Path directory;

  @Test
  void givenLocalClassWithMemberBody_whenProducedAndDecoded_thenLexicalOwnerAndCanonicalBodyRemainQueryable() throws IOException {
    // Arrange
    var source = "class Example { int outer() { class Local { int nested() { return 7; } } return new Local().nested(); } }";

    // Act
    var ast = produce(source);
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    var local = retained.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.LOCAL).findFirst().orElseThrow();
    var outer = retained.program().types().getFirst().methods().stream().filter(method -> method.name().equals("outer")).findFirst().orElseThrow();
    var nested = local.methods().stream().filter(method -> method.name().equals("nested")).findFirst().orElseThrow();

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics() + "\n" + retained.coverage().validationEvidence().outcomes().stream().filter(value -> value.support().mode() == FeatureContextOutcome.Mode.FAILED).toList());
    assertEquals(outer.methodKey(), local.nesting().lexicalOwnerKey().orElseThrow());
    assertEquals(nested, retained.method(nested.methodKey()).orElseThrow());
    assertEquals("7", retained.body(nested.methodKey()).orElseThrow().root().children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.EXPRESSION).value(SyntaxValue.Literal.class).text());
    assertTrue(retained.body(outer.methodKey()).orElseThrow().root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION));
  }

  @Test
  void givenAnonymousFieldInitializerWithInitializedMember_whenProducedAndDecoded_thenNamelessBodyAndMembersAreRetained() throws IOException {
    // Arrange
    var source = "class Example { Runnable value = new Runnable() { int count = Math.abs(-2); public void run() { count++; } }; }";

    // Act
    var ast = produce(source);
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    var field = retained.aggregate().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).getFirst().children(SyntaxRole.MEMBER).getFirst();
    var creation = field.requiredChild(SyntaxRole.INITIALIZER);
    var body = creation.requiredChild(SyntaxRole.CLASS_BODY);
    var anonymous = retained.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.ANONYMOUS).findFirst().orElseThrow();

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics() + "\n" + retained.coverage().validationEvidence().outcomes().stream().filter(value -> value.support().mode() == FeatureContextOutcome.Mode.FAILED).toList());
    assertEquals("", body.value(SyntaxValue.TypeDeclaration.class).name());
    assertEquals(anonymous.typeKey(), body.value(SyntaxValue.TypeDeclaration.class).declarationKey());
    assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION), body.children(SyntaxRole.MEMBER).stream().map(node -> node.syntaxKind()).toList());
    assertTrue(retained.aggregate().executionSyntax(body.children(SyntaxRole.MEMBER).getFirst().value(SyntaxValue.VariableDeclaration.class).declarationKey()).isPresent());
  }

  @Test
  void givenLegalLocalDeclarationForms_whenProducedAndDecoded_thenKindsHeadersAndImplicitStaticDistinctionsRemain() throws IOException {
    // Arrange
    var source = "class Example { void subject() { final class Local {} interface Contract { int value(); } record Entry(int value) implements Contract {} enum Choice {} } }";

    // Act
    var ast = produce(source);
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    var subject = retained.program().methods().stream().filter(method -> method.name().equals("subject")).findFirst().orElseThrow();
    var declarations = retained.body(subject.methodKey()).orElseThrow().root().children(SyntaxRole.STATEMENT);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
    assertEquals(List.of(JavaSourceKind.CLASS, JavaSourceKind.INTERFACE, JavaSourceKind.RECORD, JavaSourceKind.ENUM), declarations.stream().map(node -> node.sourceKind()).toList());
    assertEquals(List.of("Local", "Contract", "Entry", "Choice"), declarations.stream().map(node -> node.value(SyntaxValue.TypeDeclaration.class).name()).toList());
    assertEquals(List.of(JavaTypeModifier.FINAL), declarations.getFirst().value(SyntaxValue.TypeDeclaration.class).writtenModifiers().stream().toList());
    for (var declaration : declarations.subList(1, declarations.size())) {
      assertTrue(declaration.value(SyntaxValue.TypeDeclaration.class).writtenModifiers().isEmpty());
      assertTrue(declaration.value(SyntaxValue.TypeDeclaration.class).effectiveModifiers().contains(JavaTypeModifier.STATIC));
    }
    assertEquals("value", declarations.get(2).children(SyntaxRole.RECORD_COMPONENT).getFirst().value(SyntaxValue.RecordComponentDeclaration.class).name());
  }

  @TestFactory
  Stream<DynamicTest> givenLocalDeclarationInSupportedExecutionContext_whenProducedAndDecoded_thenOneCanonicalMemberBodyRemainsCases() {
    // Arrange
    var sources = Stream.of(
      "class Example { Example() { class Local { int value() { return 1; } } new Local().value(); } }",
      "class Example { static { class Local { static int value() { return 1; } } Local.value(); } }",
      "class Example { { class Local { int value() { return 1; } } new Local().value(); } }",
      "class Example { Runnable field = () -> { class Local { int value() { return 1; } } new Local().value(); }; }",
      "class Example { static Runnable field = () -> { class Local { int value() { return 1; } } new Local().value(); }; }",
      "class Example { void subject() { Runnable action = () -> { class Local { int value() { return 1; } } new Local().value(); }; } }",
      "class Example { void subject(boolean enabled) { if (enabled) { class Local { int value() { return 1; } } } else { ; } } }",
      "class Example { void subject() { for(int n=0; n<1; n++) { class Local { int value() { return 1; } } } } }",
      "class Example { void subject() { try { class Local { int value() { return 1; } } } finally { ; } } }",
      "class Example { void subject() { try { throw new RuntimeException(); } catch(RuntimeException failure) { class Local { int value() { return 1; } } } } }",
      "class Example { void subject() { try { ; } finally { class Local { int value() { return 1; } } } } }",
      "class Example { void subject(int input) { switch(input) { default: class Local { int value() { return 1; } } } } }",
      "class Example { void subject() { synchronized(this) { class Local { int value() { return 1; } } } } }"
    );
    // Act
    var cases = sources.map(source -> DynamicTest.dynamicTest("givenLocalDeclarationInSupportedExecutionContext_whenProducedAndDecoded_thenOneCanonicalMemberBodyRemains [" + source + "]", () -> givenLocalDeclarationInSupportedExecutionContext_whenProducedAndDecoded_thenOneCanonicalMemberBodyRemains(source)));
    // Assert
    return cases;
  }

  private void givenLocalDeclarationInSupportedExecutionContext_whenProducedAndDecoded_thenOneCanonicalMemberBodyRemains(String source) throws IOException {
    // Arrange
    var original = produce(source);

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var local = retained.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.LOCAL).findFirst().orElseThrow();
    var method = local.methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
    var declaration = retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration executable && executable.declarationKey().equals(method.methodKey())).findFirst().orElseThrow();

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
    assertSame(declaration.requiredChild(SyntaxRole.BODY), retained.body(method.methodKey()).orElseThrow().root());
    assertEquals("1", declaration.requiredChild(SyntaxRole.BODY).children(SyntaxRole.STATEMENT).getFirst().requiredChild(SyntaxRole.EXPRESSION).value(SyntaxValue.Literal.class).text());
    assertEquals(1, retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration value && value.declarationKey().equals(local.typeKey())).count());
  }

  @Test
  void givenEqualSpellingInDisjointScopesAndCapturedBindings_whenProducedAndDecoded_thenIdentitiesAndReferencesRemainDistinct() throws IOException {
    // Arrange
    var source = "class Example { int subject(int input) { final int captured = 2; { class Local { int value() { return input + captured; } } new Local().value(); } { class Local { int value() { return 4; } } new Local().value(); } return input; } }";

    // Act
    var original = produce(source);
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var locals = retained.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.LOCAL).toList();
    var first = locals.getFirst().methods().stream().filter(method -> method.name().equals("value")).findFirst().orElseThrow();
    var references = retained.body(first.methodKey()).orElseThrow().root().descendantsAndSelf().stream().flatMap(node -> node.semanticReference().stream()).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
    assertEquals(2, locals.size());
    assertNotEquals(locals.getFirst().typeKey(), locals.getLast().typeKey());
    assertEquals(locals.getFirst().nesting().lexicalOwnerKey(), locals.getLast().nesting().lexicalOwnerKey());
    assertTrue(references.stream().anyMatch(reference -> reference.referenceKind() == ReferenceKind.PARAMETER));
    assertTrue(references.stream().anyMatch(reference -> reference.referenceKind() == ReferenceKind.LOCAL));
  }

  @TestFactory
  Stream<DynamicTest> givenAnonymousCreationVariant_whenProducedAndDecoded_thenCreationOperandsAndCanonicalNamelessBodyRemainCases() {
    // Arrange
    var sources = Stream.of(
      "class Example { java.util.function.Supplier<String> field = new java.util.function.Supplier<>() { public String get() { return \"ok\"; } }; }",
      "class Example { class Inner { Inner(int value) {} } Object field = this.new Inner(3) { int value() { return 4; } }; }",
      "class Example { static class Box { <T> Box(T value) {} } Object field = new <String> Box(\"x\") { int value() { return 9; } }; }",
      "class Example { Object field = new Object() { int value() { return 2; } }.toString(); }",
      "class Example { void subject() { Object value = new Object() { int nested() { return 3; } }; } }",
      "class Example { void subject() { class Local { Object field = new Object() { int nested() { return 2; } }; } } }",
      "class Example { Object field = new Object() { int number = Math.abs(-1); { number++; } class Member { int value() { return number; } } }; }"
    );
    // Act
    var cases = sources.map(source -> DynamicTest.dynamicTest("givenAnonymousCreationVariant_whenProducedAndDecoded_thenCreationOperandsAndCanonicalNamelessBodyRemain [" + source + "]", () -> givenAnonymousCreationVariant_whenProducedAndDecoded_thenCreationOperandsAndCanonicalNamelessBodyRemain(source)));
    // Assert
    return cases;
  }

  @Test
  void givenAnnotatedGenericLocalHeader_whenProducedAndDecoded_thenWrittenAnnotationsBoundsAndMemberOrderSurvive() throws IOException {
    // Arrange
    var original = produce("class Example { void subject() { @Deprecated class Local<T extends Number> { T field; Local(T input) { field = input; } T value() { return field; } } } }");

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var local = retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration declaration && declaration.name().equals("Local")).findFirst().orElseThrow();
    var parameter = local.children(SyntaxRole.TYPE_PARAMETER).getFirst();

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
    assertEquals(1, local.children(SyntaxRole.ANNOTATION).size());
    assertEquals("type:java.lang.Deprecated", local.children(SyntaxRole.ANNOTATION).getFirst().requiredReference().symbolKey().orElseThrow());
    assertEquals("java.lang.Number", parameter.children(SyntaxRole.TYPE_BOUND).getFirst().resolvedType().orElseThrow().erasedName());
    assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION, SyntaxKind.METHOD_DECLARATION), local.children(SyntaxRole.MEMBER).stream().map(node -> node.syntaxKind()).toList());
  }

  @TestFactory
  Stream<DynamicTest> givenAnonymousConstructorWithTypedConsumer_whenProducedAndDecoded_thenExactArgumentTargetsAndCanonicalBodySurvive() {
    var integerBase = "static class Base { Base(int n) {} } ";
    var creation = "new Base(1) { int extra() { return 2; } }";
    var cases = Stream.of(
        new ConstructorConsumer("class Example { " + integerBase + "int subject() { return " + creation + ".extra(); } }", List.of("int")),
        new ConstructorConsumer("class Example { " + integerBase + "int field = " + creation + ".extra(); }", List.of("int")),
        new ConstructorConsumer("class Example { " + integerBase + "static int field = " + creation + ".extra(); }", List.of("int")),
        new ConstructorConsumer("class Example { " + integerBase + "Example() { " + creation + "; } }", List.of("int")),
        new ConstructorConsumer("class Example { " + integerBase + "{ " + creation + ".extra(); } }", List.of("int")),
        new ConstructorConsumer("class Example { " + integerBase + "static { " + creation + ".extra(); } }", List.of("int")),
        new ConstructorConsumer("class Example { " + integerBase + "Runnable field = () -> { " + creation + ".extra(); }; }", List.of("int")),
        new ConstructorConsumer("class Example { " + integerBase + "void subject() { Runnable action = () -> { " + creation + ".extra(); }; } }", List.of("int")),
        new ConstructorConsumer("class Example { " + integerBase + "int field = new Base(1) { int number=2; int extra() { return number; } }.number; }", List.of("int")),
        new ConstructorConsumer("import java.util.function.IntSupplier; class Example { " + integerBase + "IntSupplier field = " + creation + "::extra; }", List.of("int")),
        new ConstructorConsumer("class Example { class Base { <T> Base(T n) {} } int subject() { return this.new Base(1) { int extra() { return 2; } }.extra(); } }", List.of("java.lang.Integer")),
        new ConstructorConsumer("class Example { static class Base { <T> Base(T n) {} } int field = new <String> Base(\"x\") { int extra() { return 2; } }.extra(); }", List.of("java.lang.String")),
        new ConstructorConsumer("class Example { static class Base<T> { Base(T n) {} } Base<String> field = new Base<>(null) { private int extra() { return 2; } }; }", List.of("java.lang.String")),
        new ConstructorConsumer("class Example { static class Base { Base(int... n) {} } int field = new Base(1,2) { int extra() { return 2; } }.extra(); }", List.of("int", "int")),
        new ConstructorConsumer("class Example { static class Base { <T> Base(T... n) {} } int field = new Base(\"x\",\"y\") { int extra() { return 2; } }.extra(); }", List.of("java.lang.String", "java.lang.String")),
        new ConstructorConsumer("class Example { static class Base { <T> Base(T... n) {} } int field = new Base(new String[]{\"x\"}) { int extra() { return 2; } }.extra(); }", List.of("java.lang.String[]")),
        new ConstructorConsumer("import java.util.function.Supplier; class Example { static class Base { <T> Base(Supplier<T> n) {} } int field = new Base(() -> \"x\") { int extra() { return 2; } }.extra(); }", List.of("java.util.function.Supplier<java.lang.String>")),
        new ConstructorConsumer("import java.util.function.Supplier; class Example { static class Base { <T> Base(Supplier<T> n) {} } int field = new Base(String::new) { int extra() { return 2; } }.extra(); }", List.of("java.util.function.Supplier<java.lang.String>")),
        new ConstructorConsumer("class Example { " + integerBase + "int subject() { int n=1; return new Base(n++) { int extra() { return 2; } }.extra(); } }", List.of("int")),
        new ConstructorConsumer("class Example { " + integerBase + "int subject() { final int n; return new Base(n=1) { int extra() { return n; } }.extra(); } }", List.of("int")),
        new ConstructorConsumer("class Example { static class Base { Base(int n,int m) {} } int subject() { final int n,m; return new Base(n=1,m=2) { int extra() { return n+m; } }.extra(); } }", List.of("int", "int")),
        new ConstructorConsumer("class Example { static class Base { Base(Object n) {} } int field = new Base(new Object() { int argumentMethod() { return 1; } }) { int extra() { return 2; } }.extra(); }", List.of("java.lang.Object")),
        new ConstructorConsumer("class Example { static class Base { <T> Base(T n) {} } int field = new Base(new Object() { int argumentMethod() { return 1; } }) { int extra() { return 2; } }.extra(); }", List.of("argument-type")),
        new ConstructorConsumer("class Example { static class Base { <T> Base() {} } int field = new Base() { int extra() { return 2; } }.extra(); }", List.of()),
        new ConstructorConsumer("class Example { static class Base { <T> Base(T... n) {} } int field = new Base() { int extra() { return 2; } }.extra(); }", List.of()));
    return cases.map(value -> DynamicTest.dynamicTest("givenAnonymousConstructor(" + value.source() + ")_whenClosed_thenTargetsAndBodySurvive", () -> {
      // Arrange
      var original = produce(value.source());
      var bytes = AstCodec.encode(original);

      // Act
      var retained = AstCodec.decode(bytes, original.identity());
      var expression = retained.aggregate().nodes().stream().filter(node -> !node.children(SyntaxRole.CLASS_BODY).isEmpty()).findFirst().orElseThrow();
      var anonymous = expression.requiredChild(SyntaxRole.CLASS_BODY);
      var scope = retained.aggregate().executionScopes().stream().map(owner -> retained.aggregate().executionSyntax(owner.key()).orElseThrow())
          .filter(execution -> execution.root().descendantsAndSelf().contains(expression)).findFirst().orElseThrow();
      var extra = retained.program().methods().stream().filter(method -> method.name().equals("extra")).findFirst().orElseThrow();
      var declaration = anonymous.children(SyntaxRole.MEMBER).stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration executable
          && executable.declarationKey().equals(extra.methodKey())).findFirst().orElseThrow();

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion(), details(original));
      assertTrue(original.diagnostics().isEmpty(), original.diagnostics().toString());
      assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
      assertEquals(value.targets().size(), expression.children(SyntaxRole.ARGUMENT).size());
      for (var index = 0; index < value.targets().size(); index++) {
        var target = scope.useEvidence(expression.nodeId(), SyntaxRole.ARGUMENT, index).orElseThrow().selectedTarget();
        if (value.targets().get(index).equals("argument-type")) {
          assertEquals(expression.children(SyntaxRole.ARGUMENT).get(index).resolvedType().orElseThrow(), target);
        } else {
          assertEquals(value.targets().get(index), target.displayName());
        }
      }
      assertEquals("", anonymous.value(SyntaxValue.TypeDeclaration.class).name());
      assertSame(declaration.requiredChild(SyntaxRole.BODY), retained.body(extra.methodKey()).orElseThrow().root());
      assertEquals(original.program(), retained.program());
      assertEquals(original.aggregate(), retained.aggregate());
      assertArrayEquals(bytes, AstCodec.encode(retained));
    }));
  }

  private record ConstructorConsumer(String source, List<String> targets) {
  }

  private void givenAnonymousCreationVariant_whenProducedAndDecoded_thenCreationOperandsAndCanonicalNamelessBodyRemain(String source) throws IOException {
    // Arrange
    var original = produce(source);

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var creation = retained.aggregate().nodes().stream().filter(node -> !node.children(SyntaxRole.CLASS_BODY).isEmpty()).findFirst().orElseThrow();
    var anonymous = creation.requiredChild(SyntaxRole.CLASS_BODY);

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
    assertEquals("", anonymous.value(SyntaxValue.TypeDeclaration.class).name());
    assertTrue(anonymous.children(SyntaxRole.SUPERCLASS).isEmpty());
    assertTrue(anonymous.children(SyntaxRole.INTERFACE).isEmpty());
    assertTrue(creation.optionalChild(SyntaxRole.TYPE_OPERAND).isPresent());
    assertEquals(1, retained.program().types().stream().filter(type -> type.nesting().kind() == JavaTypeNesting.Kind.ANONYMOUS).count());
    if (source.contains("Inner(3)")) {
      assertEquals("3", creation.children(SyntaxRole.ARGUMENT).getFirst().value(SyntaxValue.Literal.class).text());
      assertTrue(creation.optionalChild(SyntaxRole.ENCLOSING_INSTANCE).isPresent());
    }
    if (source.contains("new <String>")) {
      assertEquals(1, creation.children(SyntaxRole.TYPE_ARGUMENT).size());
      assertEquals("x", creation.children(SyntaxRole.ARGUMENT).getFirst().value(SyntaxValue.Literal.class).text());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidNestedSource_whenProduced_thenActualCompilerErrorsPreventCompletionCases() {
    // Arrange
    var sources = Stream.of(
      "class Example { void subject() { @interface Invalid {} } }",
      "class Example { void subject() { outer: while(true) { class Local { void value() { break outer; } } } } }",
      "class Example { void subject() { class Local { int value() { return missing; } } } }"
    );
    // Act
    var cases = sources.map(source -> DynamicTest.dynamicTest("givenInvalidNestedSource_whenProduced_thenActualCompilerErrorsPreventCompletion [" + source + "]", () -> givenInvalidNestedSource_whenProduced_thenActualCompilerErrorsPreventCompletion(source)));
    // Assert
    return cases;
  }

  @Test
  void givenLocalEnumWithUnrepresentedConstantInitialization_whenProducedAndDecoded_thenContainerAndOrderRemainTruthfullyIncomplete() throws IOException {
    // Arrange
    var original = produce("class Example { void subject() { enum Choice { FIRST, SECOND } } }");

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var declaration = retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration type && type.name().equals("Choice")).findFirst().orElseThrow();

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, retained.completion(), details(retained));
    assertTrue(retained.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-enum-constant-initialization")));
    assertEquals(List.of("FIRST", "SECOND"), declaration.children(SyntaxRole.MEMBER).stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).name()).toList());
  }

  @TestFactory
  Stream<DynamicTest> givenMixedAcceptedExecutableFamiliesInNestedMember_whenProducedAndDecoded_thenFactsAndOccurrencesRemainOwnedCases() {
    // Arrange
    var member = """
        int value(Object input) {
          @Deprecated final int first = 1, second = 2;
          final int[] values = {1, 2};
          values[0] += first + second;
          for (int number : values) { if (number == 0) continue; values[0] += number; }
          Runnable action = () -> { assert values.length > 0; };
          java.util.function.IntUnaryOperator reference = Math::abs;
          action.run();
          synchronized(this) { values[0] += reference.applyAsInt(-2); }
          try (var resource = new java.io.StringReader("x")) { resource.read(); }
          catch (java.io.IOException | IllegalStateException failure) { values[0] = 0; }
          finally { values[0]++; }
          if (input instanceof String text && !text.isEmpty()) { return (int)(long)text.length(); }
          return switch(values[0]) { case 0 -> String.class.getName().length(); default -> { yield values[0]; } };
        }
        """;
    var sources = Stream.of("class Example { void subject() { class Local { " + member + " } } }",
        "class Example { Object field = new Object() { " + member + " }; }");
    // Act
    var cases = sources.map(source -> DynamicTest.dynamicTest("givenMixedAcceptedExecutableFamiliesInNestedMember_whenProducedAndDecoded_thenFactsAndOccurrencesRemainOwned [" + source + "]", () -> {
      var original = produce(source);
      var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
      var method = retained.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
      var body = retained.body(method.methodKey()).orElseThrow();
      assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
      var pattern = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN).findFirst().orElseThrow();
      for (var access : pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow()) {
        assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.origin().equals(Optional.of(access))));
      }
      assertTrue(body.evidence().uses().stream().anyMatch(use -> use.role() == SyntaxRole.ARGUMENT));
      assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE));
      assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION));
      assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.YIELD));
    }));
    // Assert
    return cases;
  }

  @TestFactory
  Stream<DynamicTest> givenCapturedPatternBindingInNestedType_whenProducedAndDecoded_thenCompilerEstablishedAccessScopeSurvivesCases() {
    // Arrange
    var sources = Stream.of(
        "class Example { int subject(Object input) { if(input instanceof String text) { class Local { int value() { return text.length(); } } return new Local().value(); } return 0; } }",
        "class Example { void subject(Object input) { if(input instanceof String text) { Object value = new Object() { int nested() { return text.length(); } }; } } }");
    // Act
    var cases = sources.map(source -> DynamicTest.dynamicTest("givenCapturedPatternBindingInNestedType_whenProducedAndDecoded_thenCompilerEstablishedAccessScopeSurvives [" + source + "]", () -> {
      var original = produce(source);
      var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
      var pattern = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN).findFirst().orElseThrow();
      var accesses = pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow();
      assertEquals(AstCompletion.COMPLETE, retained.completion(), details(retained));
      assertEquals(1, accesses.size());
      assertTrue(retained.aggregate().nodes().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS && node.origin().equals(Optional.of(accesses.getFirst()))));
    }));
    // Assert
    return cases;
  }

  private void givenInvalidNestedSource_whenProduced_thenActualCompilerErrorsPreventCompletion(String source) throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));

    // Act
    AstOutcome outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_NESTED);

    // Assert
    assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")), outcome.diagnostics().toString());
  }

  @TestFactory
  Stream<DynamicTest> givenSharedArrayAnnotationsAcrossNestedDeclarationCopies_whenProducedAndDecoded_thenPhysicalKeysAndApplicationsRemainExact() {
    var members = List.of("@Mark int @Mark [] first={1}, second @Mark []={{2}};",
        "void subject() { @Mark int @Mark [] first={1}, second @Mark []={{2}}; }",
        "{ @Mark int @Mark [] first={1}, second @Mark []={{2}}; }");
    return members.stream().flatMap(member -> Stream.of("class Example { void outer() { class Local { " + member + " } } }",
        "class Example { Object field = new Object() { " + member + " }; }"))
        .map(container -> DynamicTest.dynamicTest("givenSharedArrayAnnotations(" + container + ")_whenCopied_thenKeysAndApplicationsRemainExact", () -> {
          // Arrange
          var source = "import java.lang.annotation.ElementType; import java.lang.annotation.Target; "
              + "@Target({ElementType.FIELD,ElementType.TYPE_USE,ElementType.LOCAL_VARIABLE}) @interface Mark {} " + container;

          // Act
          var original = produce(source);
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion(), details(original));
          assertEquals(original.aggregate(), retained.aggregate());
          var shared = retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.SharedTypeAnnotations).toList();
          assertEquals(1, shared.size());
          var annotationKeys = shared.getFirst().value(SyntaxValue.SharedTypeAnnotations.class).annotationNodeKeys();
          assertEquals(1, annotationKeys.size());
          var annotationKey = annotationKeys.getFirst();
          assertEquals(1, retained.aggregate().nodes().stream().filter(node -> node.nodeId().equals(annotationKey)).count());
          var declarations = retained.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.VariableDeclaration variable
              && List.of("first", "second").contains(variable.name()) || node.value() instanceof SyntaxValue.LocalDeclaration local
                  && List.of("first", "second").contains(local.name())).toList();
          assertEquals(2, declarations.size());
          var first = declarations.getFirst();
          var second = declarations.getLast();
          var firstType = first.requiredChild(SyntaxRole.DECLARED_TYPE);
          var secondType = second.requiredChild(SyntaxRole.DECLARED_TYPE);
          var firstPrimitive = firstType.descendantsAndSelf().stream().filter(node -> node.sourceKind() == JavaSourceKind.PRIMITIVE_TYPE).findFirst().orElseThrow();
          var secondPrimitive = secondType.descendantsAndSelf().stream().filter(node -> node.sourceKind() == JavaSourceKind.PRIMITIVE_TYPE).findFirst().orElseThrow();
          var marker = first.children(SyntaxRole.ANNOTATION).getFirst();
          assertEquals(List.of(new AnnotationApplication(first.nodeId(), Optional.of(first.nodeId()), Optional.of(new Written(firstPrimitive.nodeId())), List.of(),
              List.of(new FieldDeclaratorApplication(second.nodeId(), true, Optional.of(new Written(secondPrimitive.nodeId())))))),
              annotationTargets(retained, marker.nodeId()));
          assertEquals(List.of(new AnnotationApplication(firstType.nodeId(), Optional.empty(), Optional.of(new Written(firstType.nodeId())), List.of(),
              List.of(new FieldDeclaratorApplication(second.nodeId(), false, Optional.of(new Written(shared.getFirst().nodeId())))))),
              annotationTargets(retained, annotationKey));
          var secondDimension = secondType.children(SyntaxRole.ANNOTATION).getFirst();
          assertEquals(List.of(new AnnotationApplication(secondType.nodeId(), Optional.empty(), Optional.of(new Written(secondType.nodeId())), List.of(), List.of())),
              annotationTargets(retained, secondDimension.nodeId()));
        }));
  }

  private static List<AnnotationApplication> annotationTargets(AstResult result, String annotationKey) {
    return result.aggregate().semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotationKey)
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).map(edge -> (AnnotationApplication) edge.target()).toList();
  }

  private static String details(AstResult retained) {
    return retained.diagnostics() + "\n" + retained.coverage().validationEvidence().outcomes().stream().filter(value -> value.support().mode() == FeatureContextOutcome.Mode.FAILED).map(value -> value.address() + "=" + value.encounteredValue()).toList();
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_NESTED);
    assertTrue(outcome.result().isPresent(), outcome.diagnostics().toString());
    return outcome.result().orElseThrow();
  }
}
