package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeaturePolicy;
import org.aquilagraph.syntax.AnnotationScalar;
import org.aquilagraph.syntax.JavaDeclarationModifier;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaExecutableDeclarationAstTest {
  private static final CompilerConfiguration JAVA_25 = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @TempDir Path sourceRoot;

  @Test
  void givenGenericOverloadedExecutables_whenAnalyzed_thenExactHeadersAndTypeParameterKeysAreRetained() throws IOException {
    // Arrange
    var source = """
        class Host<T extends Number & java.io.Serializable> {
          public Host() {}
          <U extends Number> U convert(U value) throws java.io.IOException, ReflectiveOperationException { return value; }
          void accept(String... values) {}
          void accept(String value) {}
        }
        """;
    Files.writeString(sourceRoot.resolve("Host.java"), source);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.analyze(sourceRoot, JAVA_25);
    var second = frontend.analyze(sourceRoot, JAVA_25);
    var type = first.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION);
    var typeParameter = type.requiredChild(SyntaxRole.TYPE_PARAMETER);
    var members = type.children(SyntaxRole.MEMBER);
    var constructor = members.getFirst();
    var generic = members.get(1);
    var variableArity = members.get(2);
    var fixedArity = members.get(3);
    var typeKey = first.program().types().getFirst().typeKey();
    var genericValue = generic.value(SyntaxValue.ExecutableDeclaration.class);
    var genericParameter = generic.requiredChild(SyntaxRole.TYPE_PARAMETER);

    // Assert
    assertTrue(first.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> first.diagnostics().toString());
    assertEquals(List.of("public Host() {}", "<U extends Number> U convert(U value) throws java.io.IOException, ReflectiveOperationException { return value; }",
        "void accept(String... values) {}", "void accept(String value) {}"), excerpts(source, members));
    assertEquals(typeKey + "#type-parameter:0", typeParameter.value(SyntaxValue.TypeParameterDeclaration.class).declarationKey());
    assertEquals(List.of("Number", "java.io.Serializable"), excerpts(source, typeParameter.children(SyntaxRole.TYPE_BOUND)));
    assertEquals(SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR,
        constructor.value(SyntaxValue.ExecutableDeclaration.class).kind());
    assertEquals(typeKey, constructor.value(SyntaxValue.ExecutableDeclaration.class).ownerKey());
    assertEquals("Host", constructor.value(SyntaxValue.ExecutableDeclaration.class).name());
    assertFalse(constructor.value(SyntaxValue.ExecutableDeclaration.class).shape().returnType());
    assertEquals(List.of(), constructor.children(SyntaxRole.RETURN_TYPE));
    assertEquals(Set.of(JavaDeclarationModifier.PUBLIC),
        constructor.value(SyntaxValue.ExecutableDeclaration.class).writtenModifiers());
    assertEquals(Set.of(JavaDeclarationModifier.PUBLIC),
        constructor.value(SyntaxValue.ExecutableDeclaration.class).effectiveModifiers());
    assertEquals(SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD, genericValue.kind());
    assertEquals(typeKey, genericValue.ownerKey());
    assertEquals(new SyntaxValue.ExecutableDeclaration.HeaderShape(0, 1, false, 1, true, 2, true, false),
        genericValue.shape());
    assertEquals(genericValue.declarationKey() + "#type-parameter:0",
        genericParameter.value(SyntaxValue.TypeParameterDeclaration.class).declarationKey());
    assertEquals(List.of("Number"), excerpts(source, genericParameter.children(SyntaxRole.TYPE_BOUND)));
    assertEquals(List.of("U"), excerpts(source, generic.children(SyntaxRole.RETURN_TYPE)));
    assertEquals(List.of("java.io.IOException", "ReflectiveOperationException"),
        excerpts(source, generic.children(SyntaxRole.THROWN_TYPE)));
    assertEquals(List.of("java.io.IOException", "java.lang.ReflectiveOperationException"), genericValue.thrownTypes().stream()
        .map(thrownType -> ((SyntaxType.Resolved) thrownType).identity().erasedName()).toList());
    assertEquals(List.of("U value"), excerpts(source, generic.children(SyntaxRole.PARAMETER)));
    assertEquals(SyntaxKind.PARAMETER_DECLARATION, generic.requiredChild(SyntaxRole.PARAMETER).syntaxKind());
    assertEquals(genericValue.declarationKey() + ":parameter:0",
        generic.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.ParameterDeclaration.class).declarationKey());
    assertEquals(List.of("U"), excerpts(source, generic.requiredChild(SyntaxRole.PARAMETER).children(SyntaxRole.DECLARED_TYPE)));
    assertEquals(List.of(false, true, false), List.of(genericValue.shape().varArgs(),
        variableArity.value(SyntaxValue.ExecutableDeclaration.class).shape().varArgs(),
        fixedArity.value(SyntaxValue.ExecutableDeclaration.class).shape().varArgs()));
    assertTrue(variableArity.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.ParameterDeclaration.class).varArgs());
    assertFalse(fixedArity.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.ParameterDeclaration.class).varArgs());
    assertEquals(first.aggregate().orElseThrow(), second.aggregate().orElseThrow());
    assertEquals(first.diagnostics(), second.diagnostics());
  }

  @Test
  void givenTypeVariableAndImportedThrownTypes_whenAnalyzed_thenAttributedOrderMatchesWrittenThrows() throws IOException {
    // Arrange
    var source = """
        import java.io.IOException;
        class Throws {
          <E extends Exception> void run() throws E, IOException {}
        }
        """;
    Files.writeString(sourceRoot.resolve("Throws.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var method = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION)
        .requiredChild(SyntaxRole.MEMBER);
    var value = method.value(SyntaxValue.ExecutableDeclaration.class);

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
    assertEquals(List.of("E", "IOException"), excerpts(source, method.children(SyntaxRole.THROWN_TYPE)));
    assertEquals(List.of(JavaTypeCategory.TYPE_VARIABLE, JavaTypeCategory.DECLARED), value.thrownTypes().stream()
        .map(type -> ((SyntaxType.Resolved) type).identity().category()).toList());
    assertEquals(List.of("E", "java.io.IOException"), value.thrownTypes().stream()
        .map(type -> ((SyntaxType.Resolved) type).identity().displayName()).toList());
  }

  @Test
  void givenQualifiedReceiverAndBodylessMethod_whenAnalyzed_thenReceiverAndPresenceFactsRemainDistinct() throws IOException {
    // Arrange
    var source = """
        class Outer {
          class Inner { Inner(Outer Outer.this) {} }
        }
        interface Contract { void run(); }
        """;
    Files.writeString(sourceRoot.resolve("Outer.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var types = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION);
    var constructor = types.getFirst().requiredChild(SyntaxRole.MEMBER).requiredChild(SyntaxRole.MEMBER);
    var method = types.get(1).requiredChild(SyntaxRole.MEMBER);
    var receiver = constructor.requiredChild(SyntaxRole.RECEIVER_PARAMETER);

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
    assertEquals(constructor.value(SyntaxValue.ExecutableDeclaration.class).declarationKey(),
        receiver.value(SyntaxValue.ReceiverParameter.class).ownerKey());
    assertEquals("Outer", ((SyntaxType.Resolved) constructor.value(SyntaxValue.ExecutableDeclaration.class).receiverType())
        .identity().erasedName());
    assertEquals("type:Outer", constructor.value(SyntaxValue.ExecutableDeclaration.class).receiverOwnerKey().orElseThrow());
    assertEquals("Outer Outer.this", excerpts(source, List.of(receiver)).getFirst());
    assertEquals(SyntaxKind.RECEIVER_PARAMETER, receiver.syntaxKind());
    assertEquals(SyntaxKind.RECEIVER_NAME, receiver.requiredChild(SyntaxRole.QUALIFIER).syntaxKind());
    assertEquals("Outer.this", receiver.requiredChild(SyntaxRole.QUALIFIER).value(SyntaxValue.SourceSymbol.class).name());
    assertEquals(LanguageFeaturePolicy.REPRESENTED, result.languageFeatureCoverage()
        .classificationOf(new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY,
            JavaDeclarationPropertyCoverage.VARIABLE_NAME_EXPRESSION)).orElseThrow().policy());
    assertEquals(List.of("Outer"), excerpts(source, receiver.children(SyntaxRole.DECLARED_TYPE)));
    assertEquals(List.of(), constructor.children(SyntaxRole.PARAMETER));
    assertFalse(method.value(SyntaxValue.ExecutableDeclaration.class).shape().body());
    assertTrue(method.value(SyntaxValue.ExecutableDeclaration.class).shape().returnType());
    assertEquals(Set.of(), method.value(SyntaxValue.ExecutableDeclaration.class).writtenModifiers());
    assertEquals(Set.of(JavaDeclarationModifier.PUBLIC, JavaDeclarationModifier.ABSTRACT),
        method.value(SyntaxValue.ExecutableDeclaration.class).effectiveModifiers());
  }

  @Test
  void givenUnqualifiedReceiver_whenAnalyzed_thenWrittenNameExpressionIsRetained() throws IOException {
    // Arrange
    var source = "class Receiver { void run(Receiver this) {} }";
    Files.writeString(sourceRoot.resolve("Receiver.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var method = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION)
        .requiredChild(SyntaxRole.MEMBER);
    var receiver = method.requiredChild(SyntaxRole.RECEIVER_PARAMETER);

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
    assertEquals("Receiver this", excerpt(source, receiver));
    assertEquals(SyntaxKind.RECEIVER_PARAMETER, receiver.syntaxKind());
    assertEquals(SyntaxKind.RECEIVER_NAME, receiver.requiredChild(SyntaxRole.QUALIFIER).syntaxKind());
    assertEquals("this", excerpt(source, receiver.requiredChild(SyntaxRole.QUALIFIER)));
    assertEquals("this", receiver.requiredChild(SyntaxRole.QUALIFIER).value(SyntaxValue.SourceSymbol.class).name());
    assertEquals(method.value(SyntaxValue.ExecutableDeclaration.class).declarationKey(),
        receiver.value(SyntaxValue.ReceiverParameter.class).ownerKey());
    assertEquals("Receiver", ((SyntaxType.Resolved) method.value(SyntaxValue.ExecutableDeclaration.class).receiverType())
        .identity().erasedName());
    assertEquals("type:Receiver", method.value(SyntaxValue.ExecutableDeclaration.class).receiverOwnerKey().orElseThrow());
  }

  @Test
  void givenStaticMethodWithoutReceiver_whenAnalyzed_thenAttributedReceiverTypeIsAbsent() throws IOException {
    // Arrange
    var source = "class StaticReceiver { static void run() {} }";
    Files.writeString(sourceRoot.resolve("StaticReceiver.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var declaration = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION)
        .children(SyntaxRole.MEMBER).stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration executable
            && executable.name().equals("run")).findFirst().orElseThrow();

    // Assert
    assertEquals(SyntaxType.NotApplicable.INSTANCE,
        declaration.value(SyntaxValue.ExecutableDeclaration.class).receiverType());
    assertEquals(List.of(), declaration.children(SyntaxRole.RECEIVER_PARAMETER));
  }

  @Test
  void givenConstructorWithoutBody_whenAnalyzed_thenCompilerErrorRemainsDiagnosed() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Broken.java"), "class Broken { Broken(); }");

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);

    // Assert
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
    assertTrue(result.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.code().equals("frontend.invalid-executable-header")),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenMethodWithQualifiedReceiverName_whenAnalyzed_thenInvalidHeaderIsDiagnosed() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Broken.java"), "class Broken { void run(Broken Broken.this) {} }");

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);

    // Assert
    assertTrue(result.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.code().equals("frontend.invalid-executable-header")),
        () -> result.diagnostics().toString());
    assertEquals(List.of(), result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION));
  }

  @Test
  void givenAnnotationElementsWithOptionalDefaults_whenAnalyzed_thenDeclarationFormsAndDefaultPresenceAreRetained() throws IOException {
    // Arrange
    var source = "@interface Settings { String required(); int count() default 1; }";
    Files.writeString(sourceRoot.resolve("Settings.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var elements = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION)
        .children(SyntaxRole.MEMBER);

    // Assert
    assertEquals(List.of("required", "count"), elements.stream()
        .map(element -> element.value(SyntaxValue.ExecutableDeclaration.class).name()).toList());
    assertTrue(elements.stream().allMatch(element -> element.value(SyntaxValue.ExecutableDeclaration.class).kind()
        == SyntaxValue.ExecutableDeclaration.ExecutableKind.ANNOTATION_ELEMENT));
    assertEquals(List.of(false, true), elements.stream()
        .map(element -> element.value(SyntaxValue.ExecutableDeclaration.class).defaultValue().isPresent()).toList());
    assertEquals(List.of(), elements.getFirst().children(SyntaxRole.DEFAULT_VALUE));
    assertEquals(List.of("1"), excerpts(source, elements.get(1).children(SyntaxRole.DEFAULT_VALUE)));
    assertEquals(List.of("String", "int"), elements.stream()
        .flatMap(element -> element.children(SyntaxRole.RETURN_TYPE).stream()).map(node -> excerpt(source, node)).toList());
    assertEquals(new AnnotationScalar.IntValue(1), elements.get(1).value(SyntaxValue.ExecutableDeclaration.class).defaultValue().orElseThrow());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenAnnotatedUnboundedParametersAndBodylessMethods_whenAnalyzed_thenWrittenAttachmentsRemainExact() throws IOException {
    // Arrange
    var source = """
        @interface Mark {}
        abstract class Host<@Mark T> {
          @Mark <@Mark U> U convert(@Mark U value) { return value; }
          abstract void absent();
          native void nativeCall();
        }
        """;
    Files.writeString(sourceRoot.resolve("Host.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var host = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).get(1);
    var typeParameter = host.requiredChild(SyntaxRole.TYPE_PARAMETER);
    var members = host.children(SyntaxRole.MEMBER);
    var method = members.getFirst();
    var methodParameter = method.requiredChild(SyntaxRole.TYPE_PARAMETER);
    var formal = method.requiredChild(SyntaxRole.PARAMETER);

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
    assertEquals(List.of("@Mark"), excerpts(source, typeParameter.children(SyntaxRole.ANNOTATION)));
    assertEquals(List.of(), typeParameter.children(SyntaxRole.TYPE_BOUND));
    assertEquals(List.of("@Mark"), excerpts(source, method.children(SyntaxRole.ANNOTATION)));
    assertEquals(List.of("@Mark"), excerpts(source, methodParameter.children(SyntaxRole.ANNOTATION)));
    assertEquals(List.of(), methodParameter.children(SyntaxRole.TYPE_BOUND));
    assertEquals(List.of("@Mark"), excerpts(source, formal.children(SyntaxRole.ANNOTATION)));
    assertEquals(List.of("convert", "absent", "nativeCall"), members.stream()
        .map(member -> member.value(SyntaxValue.ExecutableDeclaration.class).name()).toList());
    assertEquals(List.of(true, false, false), members.stream()
        .map(member -> member.value(SyntaxValue.ExecutableDeclaration.class).shape().body()).toList());
    assertEquals(List.of(Set.of(), Set.of(JavaDeclarationModifier.ABSTRACT), Set.of(JavaDeclarationModifier.NATIVE)), members.stream()
        .map(member -> member.value(SyntaxValue.ExecutableDeclaration.class).writtenModifiers()).toList());
    assertEquals(List.of(Set.of(), Set.of(JavaDeclarationModifier.ABSTRACT), Set.of(JavaDeclarationModifier.NATIVE)), members.stream()
        .map(member -> member.value(SyntaxValue.ExecutableDeclaration.class).effectiveModifiers()).toList());
  }

  @Test
  void givenFixedAndVarargsCompactConstructors_whenAnalyzed_thenMandatedFormalsStayOutsideSourceAst() throws IOException {
    // Arrange
    var source = """
        record Fixed(int x, String y) { Fixed { } }
        record Variadic(String... values) { Variadic { } }
        """;
    Files.writeString(sourceRoot.resolve("Records.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var types = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION);
    var constructors = types.stream().map(type -> type.requiredChild(SyntaxRole.MEMBER)).toList();

    // Assert
    assertEquals(List.of("Fixed { }", "Variadic { }"), excerpts(source, constructors));
    assertEquals(List.of(SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR,
        SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR), constructors.stream()
            .map(node -> node.value(SyntaxValue.ExecutableDeclaration.class).kind()).toList());
    assertEquals(List.of(false, true), constructors.stream()
        .map(node -> node.value(SyntaxValue.ExecutableDeclaration.class).shape().varArgs()).toList());
    assertTrue(constructors.stream().allMatch(node -> node.children(SyntaxRole.PARAMETER).isEmpty()
        && node.children(SyntaxRole.RETURN_TYPE).isEmpty() && node.children(SyntaxRole.BODY).size() == 1));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")
        || diagnostic.code().equals("frontend.missing-source-position")
        || diagnostic.code().equals("frontend.unavailable-written-parameter")
        || diagnostic.code().equals("frontend.inconsistent-formal-parameters")), () -> result.diagnostics().toString());
  }

  @Test
  void givenGenericConstructorAndMultipleOrderedHeaders_whenAnalyzed_thenEveryOwnerAndOrdinalMatchesSource() throws IOException {
    // Arrange
    var source = """
        class Host<A, B> {
          <X extends Number, Y extends CharSequence> Host(X number, Y text) throws java.io.IOException { }
          <P, Q> Q exchange(P first, Q second, int count) { return second; }
        }
        """;
    Files.writeString(sourceRoot.resolve("Host.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var type = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION);
    var constructor = type.children(SyntaxRole.MEMBER).getFirst();
    var method = type.children(SyntaxRole.MEMBER).get(1);
    var constructorValue = constructor.value(SyntaxValue.ExecutableDeclaration.class);
    var methodValue = method.value(SyntaxValue.ExecutableDeclaration.class);

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
    assertEquals(List.of("A", "B"), excerpts(source, type.children(SyntaxRole.TYPE_PARAMETER)));
    assertEquals(List.of("X extends Number", "Y extends CharSequence"),
        excerpts(source, constructor.children(SyntaxRole.TYPE_PARAMETER)));
    assertEquals(List.of("X number", "Y text"), excerpts(source, constructor.children(SyntaxRole.PARAMETER)));
    assertEquals(List.of("P", "Q"), excerpts(source, method.children(SyntaxRole.TYPE_PARAMETER)));
    assertEquals(List.of("P first", "Q second", "int count"), excerpts(source, method.children(SyntaxRole.PARAMETER)));
    assertEquals(List.of("java.io.IOException"), excerpts(source, constructor.children(SyntaxRole.THROWN_TYPE)));
    assertEquals(List.of(), constructor.children(SyntaxRole.RETURN_TYPE));
    assertEquals(List.of("Q"), excerpts(source, method.children(SyntaxRole.RETURN_TYPE)));
    assertEquals(SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR, constructorValue.kind());
    assertEquals(List.of(0, 1), constructor.children(SyntaxRole.TYPE_PARAMETER).stream()
        .map(node -> node.value(SyntaxValue.TypeParameterDeclaration.class).ordinal()).toList());
    assertEquals(List.of(constructorValue.declarationKey() + "#type-parameter:0",
        constructorValue.declarationKey() + "#type-parameter:1"), constructor.children(SyntaxRole.TYPE_PARAMETER).stream()
            .map(node -> node.value(SyntaxValue.TypeParameterDeclaration.class).declarationKey()).toList());
    assertEquals(List.of(methodValue.declarationKey() + ":parameter:0", methodValue.declarationKey() + ":parameter:1",
        methodValue.declarationKey() + ":parameter:2"), method.children(SyntaxRole.PARAMETER).stream()
            .map(node -> node.value(SyntaxValue.ParameterDeclaration.class).declarationKey()).toList());
  }

  @Test
  void givenOverloadedGenericMethodsAndConstructors_whenAnalyzed_thenEachErasedKeyOwnsItsWrittenHeader() throws IOException {
    // Arrange
    var source = """
        class Overload {
          <T extends Number> Overload(T number) {}
          <T extends CharSequence> Overload(T text) {}
          <T extends Number> void run(T number) {}
          <T extends CharSequence> void run(T text) {}
        }
        """;
    Files.writeString(sourceRoot.resolve("Overload.java"), source);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.analyze(sourceRoot, JAVA_25);
    var second = frontend.analyze(sourceRoot, JAVA_25);
    var members = first.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION)
        .children(SyntaxRole.MEMBER);
    var values = members.stream().map(node -> node.value(SyntaxValue.ExecutableDeclaration.class)).toList();

    // Assert
    assertTrue(first.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> first.diagnostics().toString());
    assertEquals(List.of("<T extends Number> Overload(T number) {}", "<T extends CharSequence> Overload(T text) {}",
        "<T extends Number> void run(T number) {}", "<T extends CharSequence> void run(T text) {}"), excerpts(source, members));
    assertEquals(List.of("type:Overload#<init>(java.lang.Number)", "type:Overload#<init>(java.lang.CharSequence)",
        "type:Overload#run(java.lang.Number)", "type:Overload#run(java.lang.CharSequence)"), values.stream()
            .map(SyntaxValue.ExecutableDeclaration::declarationKey).toList());
    assertEquals(values.stream().map(value -> value.declarationKey() + "#type-parameter:0").toList(), members.stream()
        .map(node -> node.requiredChild(SyntaxRole.TYPE_PARAMETER).value(SyntaxValue.TypeParameterDeclaration.class).declarationKey())
        .toList());
    assertEquals(List.of(List.of("Number"), List.of("CharSequence"), List.of("Number"), List.of("CharSequence")), members.stream()
        .map(node -> excerpts(source, node.requiredChild(SyntaxRole.TYPE_PARAMETER).children(SyntaxRole.TYPE_BOUND))).toList());
    assertEquals(List.of(List.of("T number"), List.of("T text"), List.of("T number"), List.of("T text")), members.stream()
        .map(node -> excerpts(source, node.children(SyntaxRole.PARAMETER))).toList());
    assertEquals(List.of(false, false, true, true), values.stream().map(value -> value.shape().returnType()).toList());
    assertEquals(List.of(List.of(), List.of(), List.of("void"), List.of("void")), members.stream()
        .map(node -> excerpts(source, node.children(SyntaxRole.RETURN_TYPE))).toList());
    assertEquals(first.aggregate().orElseThrow(), second.aggregate().orElseThrow());
    assertEquals(first.diagnostics(), second.diagnostics());
  }

  private static List<String> excerpts(String source, List<SyntaxNode> nodes) {
    return nodes.stream().map(node -> excerpt(source, node)).toList();
  }

  private static String excerpt(String source, SyntaxNode node) {
    var range = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
  }
}
