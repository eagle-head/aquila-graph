package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.util.TreePath;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.tools.ToolProvider;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.application.AnalysisCompletion;

import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeStructure;

import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxNodeKey;
import org.aquilagraph.source.api.SyntaxNodeTuple;
import org.aquilagraph.source.api.SyntaxRangeTuple;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxStructuralEdge;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaTypeUseAstTest {
  private static final CompilerConfiguration JAVA_25 = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @TempDir Path temporaryDirectory;

  @Test
  void givenRepeatedPrimitiveAndParameterizedFields_whenAnalyzed_thenTypeOccurrencesRemainDistinctAndRecursive() throws IOException {
    // Arrange
    var source = """
        import java.util.List;
        class Uses {
          int first;
          int second;
          List<String> names;
          List<String> aliases;
        }
        """;
    Files.writeString(temporaryDirectory.resolve("Uses.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    assertTrue(result.aggregate().isPresent(), () -> result.diagnostics().toString());
    var fields = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION)
        .children(SyntaxRole.MEMBER);
    var types = fields.stream().map(field -> field.requiredChild(SyntaxRole.DECLARED_TYPE)).toList();

    // Assert
    assertEquals(List.of(JavaSourceKind.PRIMITIVE_TYPE, JavaSourceKind.PRIMITIVE_TYPE,
        JavaSourceKind.PARAMETERIZED_TYPE, JavaSourceKind.PARAMETERIZED_TYPE),
        types.stream().map(type -> type.sourceKind()).toList());
    assertNotEquals(types.getFirst().nodeId(), types.get(1).nodeId());
    assertNotEquals(types.get(2).nodeId(), types.get(3).nodeId());
    assertEquals(new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), types.getFirst().value());
    assertEquals(new JavaTypeStructure.Primitive(JavaTypeCategory.INT), structure(types.getFirst()));
    assertEquals(structure(types.getFirst()), structure(types.get(1)));
    assertNotEquals(types.getFirst().nodeId(), types.get(1).nodeId());
    assertEquals(List.of(JavaSourceKind.IDENTIFIER), types.get(2).children(SyntaxRole.TYPE_ARGUMENT).stream()
        .map(type -> type.sourceKind()).toList());
    var listKey = "type:java.util.List";
    var stringKey = "type:java.lang.String";
    assertEquals(new JavaTypeStructure.Declared(listKey, Optional.empty(),
        List.of(new JavaTypeStructure.Declared(stringKey, Optional.empty(), List.of()))), structure(types.get(2)));
    assertEquals(structure(types.get(2)), structure(types.get(3)));
    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var first = typePath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var second = typePath.child(SyntaxRole.MEMBER, 1).child(SyntaxRole.DECLARED_TYPE, 0);
    var names = typePath.child(SyntaxRole.MEMBER, 2).child(SyntaxRole.DECLARED_TYPE, 0);
    var aliases = typePath.child(SyntaxRole.MEMBER, 3).child(SyntaxRole.DECLARED_TYPE, 0);
    var expectedNodes = List.of(tuple(owner, first, JavaSourceKind.PRIMITIVE_TYPE),
        tuple(owner, second, JavaSourceKind.PRIMITIVE_TYPE), tuple(owner, names, JavaSourceKind.PARAMETERIZED_TYPE),
        tuple(owner, names.child(SyntaxRole.TYPE_ARGUMENT, 0), JavaSourceKind.IDENTIFIER),
        tuple(owner, names.child(SyntaxRole.TYPE_NAME, 0), JavaSourceKind.IDENTIFIER),
        tuple(owner, aliases, JavaSourceKind.PARAMETERIZED_TYPE),
        tuple(owner, aliases.child(SyntaxRole.TYPE_ARGUMENT, 0), JavaSourceKind.IDENTIFIER),
        tuple(owner, aliases.child(SyntaxRole.TYPE_NAME, 0), JavaSourceKind.IDENTIFIER));
    var aggregate = result.aggregate().orElseThrow();
    assertEquals(expectedNodes, aggregate.nodeTuples().stream().filter(tuple ->
        aggregate.node(tuple.nodeKey()).orElseThrow().syntaxKind() == SyntaxKind.TYPE_USE).toList());
    var firstStart = source.indexOf("int first");
    var secondStart = source.indexOf("int second");
    var namesStart = source.indexOf("List<String> names");
    var aliasesStart = source.indexOf("List<String> aliases");
    assertEquals(List.of(span(owner, first, JavaSourceKind.PRIMITIVE_TYPE, firstStart, 3),
        span(owner, second, JavaSourceKind.PRIMITIVE_TYPE, secondStart, 3),
        span(owner, names, JavaSourceKind.PARAMETERIZED_TYPE, namesStart, 12),
        span(owner, names.child(SyntaxRole.TYPE_ARGUMENT, 0), JavaSourceKind.IDENTIFIER, namesStart + 5, 6),
        span(owner, names.child(SyntaxRole.TYPE_NAME, 0), JavaSourceKind.IDENTIFIER, namesStart, 4),
        span(owner, aliases, JavaSourceKind.PARAMETERIZED_TYPE, aliasesStart, 12),
        span(owner, aliases.child(SyntaxRole.TYPE_ARGUMENT, 0), JavaSourceKind.IDENTIFIER, aliasesStart + 5, 6),
        span(owner, aliases.child(SyntaxRole.TYPE_NAME, 0), JavaSourceKind.IDENTIFIER, aliasesStart, 4)),
        aggregate.rangeTuples().stream().filter(tuple ->
            aggregate.node(tuple.nodeKey()).orElseThrow().syntaxKind() == SyntaxKind.TYPE_USE).toList());
    assertEquals(List.of(edge(owner, names, JavaSourceKind.PARAMETERIZED_TYPE, SyntaxRole.TYPE_ARGUMENT, 0,
        JavaSourceKind.IDENTIFIER), edge(owner, names, JavaSourceKind.PARAMETERIZED_TYPE, SyntaxRole.TYPE_NAME, 0,
            JavaSourceKind.IDENTIFIER), edge(owner, aliases, JavaSourceKind.PARAMETERIZED_TYPE, SyntaxRole.TYPE_ARGUMENT, 0,
                JavaSourceKind.IDENTIFIER), edge(owner, aliases, JavaSourceKind.PARAMETERIZED_TYPE, SyntaxRole.TYPE_NAME,
                    0, JavaSourceKind.IDENTIFIER)), aggregate.structuralEdges().stream().filter(edge ->
                        aggregate.node(edge.parentKey()).orElseThrow().syntaxKind() == SyntaxKind.TYPE_USE).toList());
    assertEquals(List.of(reference(owner, names, JavaSourceKind.PARAMETERIZED_TYPE, listKey),
        reference(owner, names.child(SyntaxRole.TYPE_ARGUMENT, 0), JavaSourceKind.IDENTIFIER, stringKey),
        reference(owner, names.child(SyntaxRole.TYPE_NAME, 0), JavaSourceKind.IDENTIFIER, listKey),
        reference(owner, aliases, JavaSourceKind.PARAMETERIZED_TYPE, listKey),
        reference(owner, aliases.child(SyntaxRole.TYPE_ARGUMENT, 0), JavaSourceKind.IDENTIFIER, stringKey),
        reference(owner, aliases.child(SyntaxRole.TYPE_NAME, 0), JavaSourceKind.IDENTIFIER, listKey)).stream()
            .sorted(Comparator.comparing(SyntaxSemanticEdge::sourceKey)).toList(),
        aggregate.semanticEdges().stream().filter(edge ->
            aggregate.node(edge.sourceKey()).orElseThrow().syntaxKind() == SyntaxKind.TYPE_USE).toList());
  }

  @Test
  void givenGenericExecutableHeaders_whenAnalyzed_thenTypeVariablesAndThrowsRemainAvailable() throws IOException {
    // Arrange
    var source = """
        class Host<T extends Number & java.io.Serializable> {
          public Host() {}
          <U extends Number> U convert(U value) throws java.io.IOException, ReflectiveOperationException { return value; }
          void accept(String... values) {}
          void accept(String value) {}
        }
        """;
    Files.writeString(temporaryDirectory.resolve("Host.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);

    // Assert
    assertTrue(result.aggregate().isPresent(), () -> result.diagnostics().toString());
    var types = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION);
    assertEquals(1, types.size(),
        () -> result.diagnostics().toString());
    var methods = types.getFirst().children(SyntaxRole.MEMBER).stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.METHOD_DECLARATION).toList();
    var convert = methods.stream().filter(node -> node.value(SyntaxValue.ExecutableDeclaration.class).name().equals("convert"))
        .findFirst().orElseThrow();
    var returnUse = convert.requiredChild(SyntaxRole.RETURN_TYPE);
    var parameterUse = convert.requiredChild(SyntaxRole.PARAMETER).requiredChild(SyntaxRole.DECLARED_TYPE);
    var parameterKey = "type:Host#convert(java.lang.Number)#type-parameter:0";
    assertEquals("U", excerpt(source, returnUse));
    assertEquals("U", excerpt(source, parameterUse));
    assertEquals(parameterKey, returnUse.requiredReference().symbolKey().orElseThrow());
    assertEquals(parameterKey, parameterUse.requiredReference().symbolKey().orElseThrow());
    assertEquals(new JavaTypeStructure.Variable(parameterKey), structure(returnUse));
    assertEquals(new JavaTypeStructure.Variable(parameterKey), structure(parameterUse));
    assertEquals(List.of("type:java.io.IOException", "type:java.lang.ReflectiveOperationException"),
        convert.children(SyntaxRole.THROWN_TYPE).stream()
            .map(node -> node.requiredReference().symbolKey().orElseThrow()).toList());
    assertEquals(List.of("java.io.IOException", "ReflectiveOperationException"),
        convert.children(SyntaxRole.THROWN_TYPE).stream().map(node -> excerpt(source, node)).toList());
    var accept = methods.stream().filter(node -> node.value(SyntaxValue.ExecutableDeclaration.class).name().equals("accept"))
        .findFirst().orElseThrow();
    var varargs = accept.requiredChild(SyntaxRole.PARAMETER).requiredChild(SyntaxRole.DECLARED_TYPE);
    assertEquals(new JavaTypeStructure.Array(new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of())),
        structure(varargs));

    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var hostPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var convertPath = hostPath.child(SyntaxRole.MEMBER, 1);
    var firstNumber = source.indexOf("Number &");
    var serializable = source.indexOf("java.io.Serializable");
    var secondNumber = source.indexOf("Number> U");
    var returnStart = source.indexOf("U convert");
    var parameterStart = source.indexOf("U value");
    var exceptionStart = source.indexOf("java.io.IOException");
    var reflectiveStart = source.indexOf("ReflectiveOperationException");
    var firstVoid = source.indexOf("void accept");
    var secondVoid = source.indexOf("void accept", firstVoid + 1);
    var varargsStart = source.indexOf("String... values");
    var secondString = source.indexOf("String value");
    var serializableExpected = expected(JavaSourceKind.MEMBER_SELECT, serializable, "java.io.Serializable".length(),
        "type:java.io.Serializable", child(SyntaxRole.TYPE_QUALIFIER, 0,
            expected(JavaSourceKind.MEMBER_SELECT, serializable, "java.io".length(), null,
                child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, serializable, 4, null)))));
    var exceptionExpected = expected(JavaSourceKind.MEMBER_SELECT, exceptionStart, "java.io.IOException".length(),
        "type:java.io.IOException", child(SyntaxRole.TYPE_QUALIFIER, 0,
            expected(JavaSourceKind.MEMBER_SELECT, exceptionStart, "java.io".length(), null,
                child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, exceptionStart, 4, null)))));
    var varargsExpected = expected(JavaSourceKind.ARRAY_TYPE, varargsStart, "String...".length(), null,
        child(SyntaxRole.COMPONENT_TYPE, 0,
            expected(JavaSourceKind.IDENTIFIER, varargsStart, 6, "type:java.lang.String")));
    assertExactTypeInventory(result.aggregate().orElseThrow(), owner, List.of(
        new ExpectedRoot(hostPath.child(SyntaxRole.TYPE_PARAMETER, 0).child(SyntaxRole.TYPE_BOUND, 0),
            expected(JavaSourceKind.IDENTIFIER, firstNumber, 6, "type:java.lang.Number")),
        new ExpectedRoot(hostPath.child(SyntaxRole.TYPE_PARAMETER, 0).child(SyntaxRole.TYPE_BOUND, 1), serializableExpected),
        new ExpectedRoot(convertPath.child(SyntaxRole.TYPE_PARAMETER, 0).child(SyntaxRole.TYPE_BOUND, 0),
            expected(JavaSourceKind.IDENTIFIER, secondNumber, 6, "type:java.lang.Number")),
        new ExpectedRoot(convertPath.child(SyntaxRole.RETURN_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, returnStart, 1, ReferenceKind.TYPE_PARAMETER, parameterKey)),
        new ExpectedRoot(convertPath.child(SyntaxRole.PARAMETER, 0).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, parameterStart, 1, ReferenceKind.TYPE_PARAMETER, parameterKey)),
        new ExpectedRoot(convertPath.child(SyntaxRole.THROWN_TYPE, 0), exceptionExpected),
        new ExpectedRoot(convertPath.child(SyntaxRole.THROWN_TYPE, 1),
            expected(JavaSourceKind.IDENTIFIER, reflectiveStart, "ReflectiveOperationException".length(),
                "type:java.lang.ReflectiveOperationException")),
        new ExpectedRoot(hostPath.child(SyntaxRole.MEMBER, 2).child(SyntaxRole.RETURN_TYPE, 0),
            primitive(JavaTypeCategory.VOID, firstVoid, 4)),
        new ExpectedRoot(hostPath.child(SyntaxRole.MEMBER, 2).child(SyntaxRole.PARAMETER, 0).child(SyntaxRole.DECLARED_TYPE, 0),
            varargsExpected),
        new ExpectedRoot(hostPath.child(SyntaxRole.MEMBER, 3).child(SyntaxRole.RETURN_TYPE, 0),
            primitive(JavaTypeCategory.VOID, secondVoid, 4)),
        new ExpectedRoot(hostPath.child(SyntaxRole.MEMBER, 3).child(SyntaxRole.PARAMETER, 0).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, secondString, 6, "type:java.lang.String"))));
  }

  @Test
  void givenNestedGenericRawAndVariableTypes_whenAnalyzed_thenArgumentsAndOwnersRemainStructural() throws IOException {
    // Arrange
    var source = """
        class Outer<T> { class Inner<U> {} }
        class Uses<V> {
          Outer<String>.Inner<Integer> nested;
          java.util.List raw;
          V member;
          <W> W echo(W input) { return input; }
        }
        """;
    Files.writeString(temporaryDirectory.resolve("Nested.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    assertEquals(2, result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).size(),
        () -> result.diagnostics().toString());
    var uses = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).get(1);
    var members = uses.children(SyntaxRole.MEMBER);
    var nested = members.getFirst().requiredChild(SyntaxRole.DECLARED_TYPE);
    var raw = members.get(1).requiredChild(SyntaxRole.DECLARED_TYPE);
    var variable = members.get(2).requiredChild(SyntaxRole.DECLARED_TYPE);
    var method = members.get(3);
    var returnVariable = method.requiredChild(SyntaxRole.RETURN_TYPE);
    var parameterVariable = method.requiredChild(SyntaxRole.PARAMETER).requiredChild(SyntaxRole.DECLARED_TYPE);

    // Assert
    assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, nested.sourceKind());
    assertEquals("Integer", excerpt(source, nested.requiredChild(SyntaxRole.TYPE_ARGUMENT)));
    var selected = nested.requiredChild(SyntaxRole.TYPE_NAME);
    assertEquals(JavaSourceKind.MEMBER_SELECT, selected.sourceKind());
    var enclosing = selected.requiredChild(SyntaxRole.TYPE_QUALIFIER);
    assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, enclosing.sourceKind());
    assertEquals("String", excerpt(source, enclosing.requiredChild(SyntaxRole.TYPE_ARGUMENT)));
    assertEquals("Outer", excerpt(source, enclosing.requiredChild(SyntaxRole.TYPE_NAME)));
    var outerKey = enclosing.requiredReference().symbolKey().orElseThrow();
    var innerKey = nested.requiredReference().symbolKey().orElseThrow();
    var stringKey = enclosing.requiredChild(SyntaxRole.TYPE_ARGUMENT).requiredReference().symbolKey().orElseThrow();
    var integerKey = nested.requiredChild(SyntaxRole.TYPE_ARGUMENT).requiredReference().symbolKey().orElseThrow();
    assertEquals("type:Outer", outerKey);
    assertEquals("type:Outer.Inner", innerKey);
    assertEquals("type:java.lang.String", stringKey);
    assertEquals("type:java.lang.Integer", integerKey);
    assertEquals(new JavaTypeStructure.Declared(innerKey,
        Optional.of(new JavaTypeStructure.Declared(outerKey, Optional.empty(),
            List.of(new JavaTypeStructure.Declared(stringKey, Optional.empty(), List.of())))),
        List.of(new JavaTypeStructure.Declared(integerKey, Optional.empty(), List.of()))), structure(nested));
    assertEquals(JavaSourceKind.MEMBER_SELECT, raw.sourceKind());
    assertTrue(raw.children(SyntaxRole.TYPE_ARGUMENT).isEmpty());
    assertEquals(List.of(), ((JavaTypeStructure.Declared) structure(raw)).arguments());
    assertEquals("java", excerpt(source, raw.requiredChild(SyntaxRole.TYPE_QUALIFIER)
        .requiredChild(SyntaxRole.TYPE_QUALIFIER)));
    assertEquals(JavaTypeCategory.TYPE_VARIABLE, variable.resolvedType().orElseThrow().category());
    assertEquals(ReferenceKind.TYPE_PARAMETER, variable.requiredReference().referenceKind());
    assertEquals(uses.requiredChild(SyntaxRole.TYPE_PARAMETER).value(SyntaxValue.TypeParameterDeclaration.class).declarationKey(),
        variable.requiredReference().symbolKey().orElseThrow());
    assertEquals(new JavaTypeStructure.Variable(variable.requiredReference().symbolKey().orElseThrow()), structure(variable));
    assertEquals(returnVariable.requiredReference().symbolKey(), parameterVariable.requiredReference().symbolKey());
    assertEquals(method.requiredChild(SyntaxRole.TYPE_PARAMETER).value(SyntaxValue.TypeParameterDeclaration.class).declarationKey(),
        returnVariable.requiredReference().symbolKey().orElseThrow());
    assertEquals(new JavaTypeStructure.Variable(returnVariable.requiredReference().symbolKey().orElseThrow()),
        structure(returnVariable));

    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var usesPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 1);
    var nestedStart = source.indexOf("Outer<String>.Inner<Integer> nested");
    var outerStart = nestedStart;
    var rawStart = source.indexOf("java.util.List raw");
    var variableStart = source.indexOf("V member");
    var returnStart = source.indexOf("W echo");
    var parameterStart = source.indexOf("W input");
    var outer = expected(JavaSourceKind.PARAMETERIZED_TYPE, outerStart, 13, outerKey,
        child(SyntaxRole.TYPE_NAME, 0, expected(JavaSourceKind.IDENTIFIER, outerStart, 5, outerKey)),
        child(SyntaxRole.TYPE_ARGUMENT, 0, expected(JavaSourceKind.IDENTIFIER, outerStart + 6, 6, stringKey)));
    var rawOuter = new JavaTypeStructure.Declared(outerKey, Optional.empty(), List.of());
    var selectedName = withStructure(expected(JavaSourceKind.MEMBER_SELECT, nestedStart, 19, innerKey,
        child(SyntaxRole.TYPE_QUALIFIER, 0, outer)),
        new JavaTypeStructure.Declared(innerKey, Optional.of(rawOuter), List.of()));
    var expectedNested = expected(JavaSourceKind.PARAMETERIZED_TYPE, nestedStart, 28, innerKey,
        child(SyntaxRole.TYPE_NAME, 0, selectedName),
        child(SyntaxRole.TYPE_ARGUMENT, 0, expected(JavaSourceKind.IDENTIFIER, nestedStart + 20, 7, integerKey)));
    var expectedRaw = expected(JavaSourceKind.MEMBER_SELECT, rawStart, 14, "type:java.util.List",
        child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.MEMBER_SELECT, rawStart, 9, null,
            child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, rawStart, 4, null)))));
    var methodPath = usesPath.child(SyntaxRole.MEMBER, 3);
    var variableKey = "type:Uses#type-parameter:0";
    var methodVariableKey = "type:Uses#echo(java.lang.Object)#type-parameter:0";
    assertExactTypeInventory(result.aggregate().orElseThrow(), owner, List.of(
        new ExpectedRoot(usesPath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0), expectedNested),
        new ExpectedRoot(usesPath.child(SyntaxRole.MEMBER, 1).child(SyntaxRole.DECLARED_TYPE, 0), expectedRaw),
        new ExpectedRoot(usesPath.child(SyntaxRole.MEMBER, 2).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, variableStart, 1, ReferenceKind.TYPE_PARAMETER, variableKey)),
        new ExpectedRoot(methodPath.child(SyntaxRole.RETURN_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, returnStart, 1, ReferenceKind.TYPE_PARAMETER, methodVariableKey)),
        new ExpectedRoot(methodPath.child(SyntaxRole.PARAMETER, 0).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, parameterStart, 1, ReferenceKind.TYPE_PARAMETER, methodVariableKey))));
  }

  @Test
  void givenShadowedTypeParameterName_whenAnalyzed_thenEachUseTargetsItsDeclaringOwner() throws IOException {
    // Arrange
    var source = "class Shadow<T> { T field; <T> T echo(T value) { return value; } }";
    Files.writeString(temporaryDirectory.resolve("Shadow.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var aggregate = result.aggregate().orElseThrow();
    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var methodPath = typePath.child(SyntaxRole.MEMBER, 1);
    var classParameterKey = "type:Shadow#type-parameter:0";
    var methodParameterKey = "type:Shadow#echo(java.lang.Object)#type-parameter:0";

    // Assert
    assertNotEquals(classParameterKey, methodParameterKey);
    assertExactTypeInventory(aggregate, owner, List.of(
        new ExpectedRoot(typePath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, source.indexOf("T field"), 1, ReferenceKind.TYPE_PARAMETER,
                classParameterKey)),
        new ExpectedRoot(methodPath.child(SyntaxRole.RETURN_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, source.indexOf("T echo"), 1, ReferenceKind.TYPE_PARAMETER,
                methodParameterKey)),
        new ExpectedRoot(methodPath.child(SyntaxRole.PARAMETER, 0).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, source.indexOf("T value"), 1, ReferenceKind.TYPE_PARAMETER,
                methodParameterKey))));
  }

  @Test
  void givenStaticNestedGenericTypes_whenAnalyzed_thenLexicalQualifiersRemainSeparateFromEnclosingInstances() throws IOException {
    // Arrange
    var source = """
        class Outer { static class Nested<T> {} }
        class Uses {
          Outer.Nested<String> local;
          java.util.Map.Entry<String, Integer> external;
        }
        """;
    Files.writeString(temporaryDirectory.resolve("StaticNested.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var aggregate = result.aggregate().orElseThrow();
    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var usesPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 1);
    var localStart = source.indexOf("Outer.Nested<String> local");
    var externalStart = source.indexOf("java.util.Map.Entry<String, Integer> external");
    var stringType = new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of());
    var integerType = new JavaTypeStructure.Declared("type:java.lang.Integer", Optional.empty(), List.of());
    var localName = withStructure(expected(JavaSourceKind.MEMBER_SELECT, localStart, 12, "type:Outer.Nested",
        child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, localStart, 5, "type:Outer"))),
        new JavaTypeStructure.Declared("type:Outer.Nested", Optional.of("type:Outer"), Optional.empty(), List.of()));
    var local = withStructure(expected(JavaSourceKind.PARAMETERIZED_TYPE, localStart, 20, "type:Outer.Nested",
        child(SyntaxRole.TYPE_NAME, 0, localName),
        child(SyntaxRole.TYPE_ARGUMENT, 0,
            expected(JavaSourceKind.IDENTIFIER, localStart + 13, 6, "type:java.lang.String"))),
        new JavaTypeStructure.Declared("type:Outer.Nested", Optional.of("type:Outer"), Optional.empty(), List.of(stringType)));
    var mapName = expected(JavaSourceKind.MEMBER_SELECT, externalStart, 13, "type:java.util.Map",
        child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.MEMBER_SELECT, externalStart, 9, null,
            child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, externalStart, 4, null)))));
    var entryName = withStructure(expected(JavaSourceKind.MEMBER_SELECT, externalStart, 19, "type:java.util.Map.Entry",
        child(SyntaxRole.TYPE_QUALIFIER, 0, mapName)),
        new JavaTypeStructure.Declared("type:java.util.Map.Entry", Optional.of("type:java.util.Map"), Optional.empty(), List.of()));
    var external = withStructure(expected(JavaSourceKind.PARAMETERIZED_TYPE, externalStart, 36, "type:java.util.Map.Entry",
        child(SyntaxRole.TYPE_NAME, 0, entryName),
        child(SyntaxRole.TYPE_ARGUMENT, 0,
            expected(JavaSourceKind.IDENTIFIER, externalStart + 20, 6, "type:java.lang.String")),
        child(SyntaxRole.TYPE_ARGUMENT, 1,
            expected(JavaSourceKind.IDENTIFIER, externalStart + 28, 7, "type:java.lang.Integer"))),
        new JavaTypeStructure.Declared("type:java.util.Map.Entry", Optional.of("type:java.util.Map"), Optional.empty(),
            List.of(stringType, integerType)));

    // Assert
    assertExactTypeInventory(aggregate, owner, List.of(
        new ExpectedRoot(usesPath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0), local),
        new ExpectedRoot(usesPath.child(SyntaxRole.MEMBER, 1).child(SyntaxRole.DECLARED_TYPE, 0), external)));
    assertTrue(((JavaTypeStructure.Declared) structure(aggregate.roots().getFirst().children(SyntaxRole.TYPE_DECLARATION)
        .get(1).children(SyntaxRole.MEMBER).getFirst().requiredChild(SyntaxRole.DECLARED_TYPE))).enclosingType().isEmpty());
  }

  @Test
  void givenInheritedMemberTypes_whenAnalyzed_thenWrittenQualifiersAndSelectedOwnersRemainDistinct() throws IOException {
    // Arrange
    var source = """
        class Base { static class Member<T> {} }
        class Derived extends Base {}
        class Uses {
          Derived.Member<String> parameterized;
          Derived.Member raw;
        }
        """;
    Files.writeString(temporaryDirectory.resolve("InheritedMember.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var aggregate = result.aggregate().orElseThrow();
    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var root = SyntaxStructuralPath.sourceFileRoot();
    var usesPath = root.child(SyntaxRole.TYPE_DECLARATION, 2);
    var parameterizedStart = source.indexOf("Derived.Member<String> parameterized");
    var rawStart = source.indexOf("Derived.Member raw");
    var baseKey = "type:Base";
    var derivedKey = "type:Derived";
    var memberKey = "type:Base.Member";
    var memberName = withStructure(expected(JavaSourceKind.MEMBER_SELECT, parameterizedStart, 14, memberKey,
        child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, parameterizedStart, 7, derivedKey))),
        new JavaTypeStructure.Declared(memberKey, Optional.of(baseKey), Optional.empty(), List.of()));
    var parameterized = withStructure(expected(JavaSourceKind.PARAMETERIZED_TYPE, parameterizedStart, 22, memberKey,
        child(SyntaxRole.TYPE_NAME, 0, memberName),
        child(SyntaxRole.TYPE_ARGUMENT, 0,
            expected(JavaSourceKind.IDENTIFIER, parameterizedStart + 15, 6, "type:java.lang.String"))),
        new JavaTypeStructure.Declared(memberKey, Optional.of(baseKey), Optional.empty(),
            List.of(new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of()))));
    var raw = withStructure(expected(JavaSourceKind.MEMBER_SELECT, rawStart, 14, memberKey,
        child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, rawStart, 7, derivedKey))),
        new JavaTypeStructure.Declared(memberKey, Optional.of(baseKey), Optional.empty(), List.of()));

    // Assert
    assertExactTypeInventory(aggregate, owner, List.of(
        new ExpectedRoot(root.child(SyntaxRole.TYPE_DECLARATION, 1).child(SyntaxRole.SUPERCLASS, 0),
            expected(JavaSourceKind.IDENTIFIER, source.indexOf("extends Base") + 8, 4, baseKey)),
        new ExpectedRoot(usesPath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0), parameterized),
        new ExpectedRoot(usesPath.child(SyntaxRole.MEMBER, 1).child(SyntaxRole.DECLARED_TYPE, 0), raw)));
  }

  @Test
  void givenTypeVariableQualifierWithInterfaceBound_whenAnalyzed_thenSelectedMemberUsesBoundDeclaration() throws IOException {
    // Arrange
    var source = """
        interface MemberOwner { class Member {} }
        class Holder<T extends Number & MemberOwner> { T.Member value; }
        """;
    Files.writeString(temporaryDirectory.resolve("MemberOwner.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);

    // Assert
    assertTrue(result.aggregate().isPresent(), () -> result.diagnostics().toString());
    var use = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).get(1)
        .requiredChild(SyntaxRole.MEMBER).requiredChild(SyntaxRole.DECLARED_TYPE);
    assertEquals("type:MemberOwner.Member", use.requiredReference().symbolKey().orElseThrow());
    assertEquals("type:MemberOwner", ((JavaTypeStructure.Declared) structure(use)).lexicalOwnerKey().orElseThrow());
    assertEquals("type:Holder#type-parameter:0", use.requiredChild(SyntaxRole.TYPE_QUALIFIER)
        .requiredReference().symbolKey().orElseThrow());
  }

  @Test
  void givenInheritedInnerType_whenAnalyzed_thenEnclosingInstanceRetainsCompilerType() throws IOException {
    // Arrange
    var source = """
        class Base<T> { class Inner<U> {} }
        class Derived extends Base<String> {}
        class Uses { Derived.Inner<Integer> value; }
        """;
    Files.writeString(temporaryDirectory.resolve("InheritedInner.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var aggregate = result.aggregate().orElseThrow();
    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var root = SyntaxStructuralPath.sourceFileRoot();
    var start = source.indexOf("Derived.Inner<Integer> value");
    var stringType = new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of());
    var integerType = new JavaTypeStructure.Declared("type:java.lang.Integer", Optional.empty(), List.of());
    var enclosing = new JavaTypeStructure.Declared("type:Base", Optional.empty(), List.of(stringType));
    var innerKey = "type:Base.Inner";
    var name = withStructure(expected(JavaSourceKind.MEMBER_SELECT, start, 13, innerKey,
        child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, start, 7, "type:Derived"))),
        new JavaTypeStructure.Declared(innerKey, Optional.of("type:Base"),
            Optional.of(new JavaTypeStructure.Declared("type:Base", Optional.empty(), List.of())), List.of()));
    var inner = withStructure(expected(JavaSourceKind.PARAMETERIZED_TYPE, start, 22, innerKey,
        child(SyntaxRole.TYPE_NAME, 0, name),
        child(SyntaxRole.TYPE_ARGUMENT, 0,
            expected(JavaSourceKind.IDENTIFIER, start + 14, 7, "type:java.lang.Integer"))),
        new JavaTypeStructure.Declared(innerKey, Optional.of("type:Base"), Optional.of(enclosing), List.of(integerType)));

    // Assert
    assertExactTypeInventory(aggregate, owner, List.of(
        new ExpectedRoot(root.child(SyntaxRole.TYPE_DECLARATION, 1).child(SyntaxRole.SUPERCLASS, 0),
            expected(JavaSourceKind.PARAMETERIZED_TYPE, source.indexOf("Base<String>"), 12, "type:Base",
                child(SyntaxRole.TYPE_NAME, 0,
                    expected(JavaSourceKind.IDENTIFIER, source.indexOf("Base<String>"), 4, "type:Base")),
                child(SyntaxRole.TYPE_ARGUMENT, 0,
                    expected(JavaSourceKind.IDENTIFIER, source.indexOf("Base<String>") + 5, 6, "type:java.lang.String")))),
        new ExpectedRoot(root.child(SyntaxRole.TYPE_DECLARATION, 2).child(SyntaxRole.MEMBER, 0)
            .child(SyntaxRole.DECLARED_TYPE, 0), inner)));
  }

  @Test
  void givenImplicitEnclosingTypeAndOrderedArguments_whenAnalyzed_thenCompilerComponentsNeedNoInventedSourceNode()
      throws IOException {
    // Arrange
    var source = """
        class Outer<T> {
          class Inner<U> {}
          Inner<String> member;
          java.util.Map<String, Integer> ordered;
        }
        """;
    Files.writeString(temporaryDirectory.resolve("Outer.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var outer = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION);
    var fields = outer.children(SyntaxRole.MEMBER).stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION).toList();
    var member = fields.getFirst().requiredChild(SyntaxRole.DECLARED_TYPE);
    var ordered = fields.get(1).requiredChild(SyntaxRole.DECLARED_TYPE);

    // Assert
    assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, member.sourceKind());
    assertTrue(member.requiredChild(SyntaxRole.TYPE_NAME).children(SyntaxRole.TYPE_QUALIFIER).isEmpty());
    assertEquals(new JavaTypeStructure.Declared("type:Outer.Inner", Optional.of(
        new JavaTypeStructure.Declared("type:Outer", Optional.empty(),
            List.of(new JavaTypeStructure.Variable("type:Outer#type-parameter:0")))),
        List.of(new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of()))), structure(member));
    assertEquals(new JavaTypeStructure.Declared("type:java.util.Map", Optional.empty(), List.of(
        new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of()),
        new JavaTypeStructure.Declared("type:java.lang.Integer", Optional.empty(), List.of()))), structure(ordered));
    assertEquals(List.of("type:java.lang.String", "type:java.lang.Integer"), ordered.children(SyntaxRole.TYPE_ARGUMENT)
        .stream().map(argument -> argument.requiredReference().symbolKey().orElseThrow()).toList());

    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var outerPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var memberStart = source.indexOf("Inner<String> member");
    var orderedStart = source.indexOf("java.util.Map<String, Integer> ordered");
    var implicitOuter = new JavaTypeStructure.Declared("type:Outer", Optional.empty(),
        List.of(new JavaTypeStructure.Variable("type:Outer#type-parameter:0")));
    var implicitName = withStructure(expected(JavaSourceKind.IDENTIFIER, memberStart, 5, "type:Outer.Inner"),
        new JavaTypeStructure.Declared("type:Outer.Inner",
            Optional.of(new JavaTypeStructure.Declared("type:Outer", Optional.empty(), List.of())), List.of()));
    var expectedMember = withStructure(expected(JavaSourceKind.PARAMETERIZED_TYPE, memberStart, 13, "type:Outer.Inner",
        child(SyntaxRole.TYPE_NAME, 0, implicitName),
        child(SyntaxRole.TYPE_ARGUMENT, 0,
            expected(JavaSourceKind.IDENTIFIER, memberStart + 6, 6, "type:java.lang.String"))),
        new JavaTypeStructure.Declared("type:Outer.Inner", Optional.of(implicitOuter),
            List.of(new JavaTypeStructure.Declared("type:java.lang.String", Optional.empty(), List.of()))));
    var orderedName = expected(JavaSourceKind.MEMBER_SELECT, orderedStart, 13, "type:java.util.Map",
        child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.MEMBER_SELECT, orderedStart, 9, null,
            child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, orderedStart, 4, null)))));
    var expectedOrdered = expected(JavaSourceKind.PARAMETERIZED_TYPE, orderedStart, 30, "type:java.util.Map",
        child(SyntaxRole.TYPE_NAME, 0, orderedName),
        child(SyntaxRole.TYPE_ARGUMENT, 0,
            expected(JavaSourceKind.IDENTIFIER, orderedStart + 14, 6, "type:java.lang.String")),
        child(SyntaxRole.TYPE_ARGUMENT, 1,
            expected(JavaSourceKind.IDENTIFIER, orderedStart + 22, 7, "type:java.lang.Integer")));
    assertExactTypeInventory(result.aggregate().orElseThrow(), owner, List.of(
        new ExpectedRoot(outerPath.child(SyntaxRole.MEMBER, 1).child(SyntaxRole.DECLARED_TYPE, 0), expectedMember),
        new ExpectedRoot(outerPath.child(SyntaxRole.MEMBER, 2).child(SyntaxRole.DECLARED_TYPE, 0), expectedOrdered)));
  }

  @Test
  void givenArrayDeclarationsAndBodylessSignatures_whenAnalyzed_thenDimensionsAndPrimitiveKindsRemainDistinct() throws IOException {
    // Arrange
    var source = """
        class Arrays { int[][] matrix; int mixed[][]; }
        interface Signatures { void run(int value); int next(); }
        """;
    Files.writeString(temporaryDirectory.resolve("Arrays.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var types = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION);
    assertEquals(2, types.size(), () -> result.diagnostics().toString());
    var arrays = types.getFirst().children(SyntaxRole.MEMBER);
    var signatures = types.get(1).children(SyntaxRole.MEMBER);
    var matrix = arrays.getFirst().requiredChild(SyntaxRole.DECLARED_TYPE);
    var mixed = arrays.get(1).requiredChild(SyntaxRole.DECLARED_TYPE);
    var voidReturn = signatures.getFirst().requiredChild(SyntaxRole.RETURN_TYPE);
    var primitiveParameter = signatures.getFirst().requiredChild(SyntaxRole.PARAMETER).requiredChild(SyntaxRole.DECLARED_TYPE);
    var intReturn = signatures.get(1).requiredChild(SyntaxRole.RETURN_TYPE);

    // Assert
    for (var array : List.of(matrix, mixed)) {
      assertEquals(JavaSourceKind.ARRAY_TYPE, array.sourceKind());
      var inner = array.requiredChild(SyntaxRole.COMPONENT_TYPE);
      assertEquals(JavaSourceKind.ARRAY_TYPE, inner.sourceKind());
      assertEquals(JavaSourceKind.PRIMITIVE_TYPE, inner.requiredChild(SyntaxRole.COMPONENT_TYPE).sourceKind());
      assertNotEquals(array.nodeId(), inner.nodeId());
      assertEquals(new JavaTypeStructure.Array(new JavaTypeStructure.Array(
          new JavaTypeStructure.Primitive(JavaTypeCategory.INT))), structure(array));
    }
    assertEquals(new SyntaxValue.PrimitiveType(JavaTypeCategory.VOID), voidReturn.value());
    assertEquals(new JavaTypeStructure.Primitive(JavaTypeCategory.VOID), structure(voidReturn));
    assertEquals(JavaTypeCategory.VOID, voidReturn.resolvedType().orElseThrow().category());
    assertEquals(new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), primitiveParameter.value());
    assertEquals(new SyntaxValue.PrimitiveType(JavaTypeCategory.INT), intReturn.value());
    assertEquals("void", excerpt(source, voidReturn));
    assertEquals("int", excerpt(source, intReturn));

    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var arraysPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var signaturesPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 1);
    var matrixStart = source.indexOf("int[][] matrix");
    var mixedStart = source.indexOf("int mixed[][]");
    var voidStart = source.indexOf("void run");
    var parameterStart = source.indexOf("int value");
    var returnStart = source.indexOf("int next");
    var matrixExpected = expected(JavaSourceKind.ARRAY_TYPE, matrixStart, 7, null,
        child(SyntaxRole.COMPONENT_TYPE, 0, expected(JavaSourceKind.ARRAY_TYPE, matrixStart, 7, null,
            child(SyntaxRole.COMPONENT_TYPE, 0, primitive(JavaTypeCategory.INT, matrixStart, 3)))));
    var mixedExpected = expected(JavaSourceKind.ARRAY_TYPE, mixedStart, 13, null,
        child(SyntaxRole.COMPONENT_TYPE, 0, expected(JavaSourceKind.ARRAY_TYPE, mixedStart, 13, null,
            child(SyntaxRole.COMPONENT_TYPE, 0, primitive(JavaTypeCategory.INT, mixedStart, 3)))));
    assertExactTypeInventory(result.aggregate().orElseThrow(), owner, List.of(
        new ExpectedRoot(arraysPath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0), matrixExpected),
        new ExpectedRoot(arraysPath.child(SyntaxRole.MEMBER, 1).child(SyntaxRole.DECLARED_TYPE, 0), mixedExpected),
        new ExpectedRoot(signaturesPath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.RETURN_TYPE, 0),
            primitive(JavaTypeCategory.VOID, voidStart, 4)),
        new ExpectedRoot(signaturesPath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.PARAMETER, 0)
            .child(SyntaxRole.DECLARED_TYPE, 0), primitive(JavaTypeCategory.INT, parameterStart, 3)),
        new ExpectedRoot(signaturesPath.child(SyntaxRole.MEMBER, 1).child(SyntaxRole.RETURN_TYPE, 0),
            primitive(JavaTypeCategory.INT, returnStart, 3))));
  }

  @Test
  void givenAnnotatedTypeUse_whenAnalyzed_thenWrapperOwnsAnnotationAndUnderlyingType() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Mark {}
        class Uses { java.util.List<@Mark String> names; }
        """;
    Files.writeString(temporaryDirectory.resolve("Annotated.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var uses = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).get(1);
    var wrapper = uses.requiredChild(SyntaxRole.MEMBER).requiredChild(SyntaxRole.DECLARED_TYPE)
        .requiredChild(SyntaxRole.TYPE_ARGUMENT);

    // Assert
    assertEquals(JavaSourceKind.ANNOTATED_TYPE, wrapper.sourceKind());
    assertEquals("@Mark", excerpt(source, wrapper.requiredChild(SyntaxRole.ANNOTATION)));
    assertEquals("String", excerpt(source, wrapper.requiredChild(SyntaxRole.UNDERLYING_TYPE)));
    assertEquals(SyntaxValue.AnnotationLocation.TYPE_USE,
        wrapper.requiredChild(SyntaxRole.ANNOTATION).value(SyntaxValue.Annotation.class).location());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 1)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);
    var start = source.indexOf("java.util.List<@Mark String> names");
    var packageName = expected(JavaSourceKind.MEMBER_SELECT, start, 9, null,
        child(SyntaxRole.TYPE_QUALIFIER, 0, expected(JavaSourceKind.IDENTIFIER, start, 4, null)));
    var listName = expected(JavaSourceKind.MEMBER_SELECT, start, 14, "type:java.util.List",
        child(SyntaxRole.TYPE_QUALIFIER, 0, packageName));
    var annotatedArgument = expected(JavaSourceKind.ANNOTATED_TYPE, start + 15, 12, "type:java.lang.String",
        child(SyntaxRole.ANNOTATION, 0, expected(JavaSourceKind.ANNOTATION, start + 15, 5, null)),
        child(SyntaxRole.UNDERLYING_TYPE, 0,
            expected(JavaSourceKind.IDENTIFIER, start + 21, 6, "type:java.lang.String")));
    var expectedRoot = expected(JavaSourceKind.PARAMETERIZED_TYPE, start, 28, "type:java.util.List",
        child(SyntaxRole.TYPE_NAME, 0, listName), child(SyntaxRole.TYPE_ARGUMENT, 0, annotatedArgument));
    var qualifierPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.ANNOTATION, 0).child(SyntaxRole.ANNOTATION_ARGUMENT, 0)
        .child(SyntaxRole.VALUE, 0).child(SyntaxRole.QUALIFIER, 0);
    var qualifierStart = source.indexOf("ElementType.TYPE_USE");
    assertExactTypeInventory(result.aggregate().orElseThrow(), owner, List.of(
        new ExpectedRoot(qualifierPath, expected(JavaSourceKind.IDENTIFIER, qualifierStart, 11,
            "type:java.lang.annotation.ElementType")), new ExpectedRoot(path, expectedRoot)));
    var annotationPath = path.child(SyntaxRole.TYPE_ARGUMENT, 0).child(SyntaxRole.ANNOTATION, 0);
    var annotationKey = SyntaxNodeKey.from(owner, annotationPath, JavaSourceKind.ANNOTATION).value();
    assertEquals(SyntaxKind.ANNOTATION, result.aggregate().orElseThrow().node(annotationKey).orElseThrow().syntaxKind());
    assertTrue(result.aggregate().orElseThrow().rangeTuples().contains(
        span(owner, annotationPath, JavaSourceKind.ANNOTATION, start + 15, 5)));
  }

  @Test
  void givenThreeWildcardForms_whenAnalyzed_thenBoundsRetainExclusiveSourceAndAttributedStructure() throws IOException {
    // Arrange
    var source = """
        import java.util.List;
        class Bounds<T> {
          List<?> any;
          List<? extends Number> upper;
          List<? super T> lower;
        }
        """;
    Files.writeString(temporaryDirectory.resolve("Bounds.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var declaration = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION);
    var wildcards = declaration.children(SyntaxRole.MEMBER).stream()
        .map(field -> field.requiredChild(SyntaxRole.DECLARED_TYPE).requiredChild(SyntaxRole.TYPE_ARGUMENT)).toList();
    var parameterKey = declaration.requiredChild(SyntaxRole.TYPE_PARAMETER)
        .value(SyntaxValue.TypeParameterDeclaration.class).declarationKey();

    // Assert
    assertEquals(List.of(JavaSourceKind.UNBOUNDED_WILDCARD, JavaSourceKind.EXTENDS_WILDCARD,
        JavaSourceKind.SUPER_WILDCARD), wildcards.stream().map(SyntaxNode::sourceKind).toList());
    assertEquals(List.of("?", "? extends Number", "? super T"), wildcards.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED, Optional.empty()),
        structure(wildcards.getFirst()));
    assertTrue(wildcards.getFirst().children().isEmpty());
    var upper = wildcards.get(1).requiredChild(SyntaxRole.UPPER_BOUND);
    assertEquals("Number", excerpt(source, upper));
    assertEquals(new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.EXTENDS,
        Optional.of(new JavaTypeStructure.Declared("type:java.lang.Number", Optional.empty(), List.of()))),
        structure(wildcards.get(1)));
    var lower = wildcards.get(2).requiredChild(SyntaxRole.LOWER_BOUND);
    assertEquals("T", excerpt(source, lower));
    assertEquals(new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.SUPER,
        Optional.of(new JavaTypeStructure.Variable(parameterKey))), structure(wildcards.get(2)));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-type-use-structure")));
    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var anyStart = source.indexOf("List<?>");
    var upperStart = source.indexOf("List<? extends Number>");
    var lowerStart = source.indexOf("List<? super T>");
    var anyArgument = withStructure(expected(JavaSourceKind.UNBOUNDED_WILDCARD, anyStart + 5, 1, null),
        new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED, Optional.empty()));
    var upperArgument = withStructure(expected(JavaSourceKind.EXTENDS_WILDCARD, upperStart + 5,
        "? extends Number".length(), null, child(SyntaxRole.UPPER_BOUND, 0,
            expected(JavaSourceKind.IDENTIFIER, upperStart + "List<? extends ".length(), 6, "type:java.lang.Number"))),
        new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.EXTENDS,
            Optional.of(new JavaTypeStructure.Declared("type:java.lang.Number", Optional.empty(), List.of()))));
    var lowerArgument = withStructure(expected(JavaSourceKind.SUPER_WILDCARD, lowerStart + 5,
        "? super T".length(), null, child(SyntaxRole.LOWER_BOUND, 0,
            expected(JavaSourceKind.IDENTIFIER, lowerStart + "List<? super ".length(), 1,
                ReferenceKind.TYPE_PARAMETER, parameterKey))),
        new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.SUPER,
            Optional.of(new JavaTypeStructure.Variable(parameterKey))));
    assertExactTypeInventory(result.aggregate().orElseThrow(), owner, List.of(
        new ExpectedRoot(typePath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.PARAMETERIZED_TYPE, anyStart, "List<?>".length(), "type:java.util.List",
                child(SyntaxRole.TYPE_NAME, 0, expected(JavaSourceKind.IDENTIFIER, anyStart, 4, "type:java.util.List")),
                child(SyntaxRole.TYPE_ARGUMENT, 0, anyArgument))),
        new ExpectedRoot(typePath.child(SyntaxRole.MEMBER, 1).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.PARAMETERIZED_TYPE, upperStart, "List<? extends Number>".length(), "type:java.util.List",
                child(SyntaxRole.TYPE_NAME, 0, expected(JavaSourceKind.IDENTIFIER, upperStart, 4, "type:java.util.List")),
                child(SyntaxRole.TYPE_ARGUMENT, 0, upperArgument))),
        new ExpectedRoot(typePath.child(SyntaxRole.MEMBER, 2).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.PARAMETERIZED_TYPE, lowerStart, "List<? super T>".length(), "type:java.util.List",
                child(SyntaxRole.TYPE_NAME, 0, expected(JavaSourceKind.IDENTIFIER, lowerStart, 4, "type:java.util.List")),
                child(SyntaxRole.TYPE_ARGUMENT, 0, lowerArgument)))));
  }

  @Test
  void givenMultipleTypeParameterBounds_whenAnalyzed_thenAttributedIntersectionMatchesWrittenOrder() throws IOException {
    // Arrange
    var source = """
        class Bounds<T extends Number & java.io.Serializable> { T value; }
        """;
    Files.writeString(temporaryDirectory.resolve("Bounds.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var parameter = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION)
        .requiredChild(SyntaxRole.TYPE_PARAMETER);
    var bounds = parameter.children(SyntaxRole.TYPE_BOUND);

    // Assert
    assertEquals(List.of("Number", "java.io.Serializable"), bounds.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(new JavaTypeStructure.Intersection(List.of(
        new JavaTypeStructure.Declared("type:java.lang.Number", Optional.empty(), List.of()),
        new JavaTypeStructure.Declared("type:java.io.Serializable", Optional.empty(), List.of()))),
        parameter.value(SyntaxValue.TypeParameterDeclaration.class).upperBound());
    assertEquals(List.of(SyntaxRole.TYPE_BOUND, SyntaxRole.TYPE_BOUND), parameter.children().stream()
        .map(child -> child.role()).toList());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-type-parameter-bound")));
    var owner = result.snapshot().sourceFiles().getFirst().sourceFileKey();
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var serializable = source.indexOf("java.io.Serializable");
    assertExactTypeInventory(result.aggregate().orElseThrow(), owner, List.of(
        new ExpectedRoot(typePath.child(SyntaxRole.TYPE_PARAMETER, 0).child(SyntaxRole.TYPE_BOUND, 0),
            expected(JavaSourceKind.IDENTIFIER, source.indexOf("Number"), 6, "type:java.lang.Number")),
        new ExpectedRoot(typePath.child(SyntaxRole.TYPE_PARAMETER, 0).child(SyntaxRole.TYPE_BOUND, 1),
            expected(JavaSourceKind.MEMBER_SELECT, serializable, "java.io.Serializable".length(), "type:java.io.Serializable",
                child(SyntaxRole.TYPE_QUALIFIER, 0,
                    expected(JavaSourceKind.MEMBER_SELECT, serializable, "java.io".length(), null,
                        child(SyntaxRole.TYPE_QUALIFIER, 0,
                            expected(JavaSourceKind.IDENTIFIER, serializable, 4, null)))))),
        new ExpectedRoot(typePath.child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0),
            expected(JavaSourceKind.IDENTIFIER, source.indexOf("T value"), 1, ReferenceKind.TYPE_PARAMETER,
                parameter.value(SyntaxValue.TypeParameterDeclaration.class).declarationKey()))));
  }

  @Test
  void givenMultiCatchParameter_whenAnalyzed_thenAttributedUnionRetainsOrderedAlternatives() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Catches.java"), """
        class Catches {
          void run() {
            try { throw new IllegalArgumentException(); }
            catch (IllegalArgumentException | IllegalStateException problem) { }
          }
        }
        """);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var catchParameter = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)
        .filter(node -> node.value(SyntaxValue.LocalDeclaration.class).name().equals("problem")).findFirst().orElseThrow();

    // Assert
    assertEquals(JavaSourceKind.VARIABLE, catchParameter.sourceKind());
    assertEquals(new JavaTypeStructure.Union(List.of(
        new JavaTypeStructure.Declared("type:java.lang.IllegalArgumentException", Optional.empty(), List.of()),
        new JavaTypeStructure.Declared("type:java.lang.IllegalStateException", Optional.empty(), List.of()))),
        structure(catchParameter));
    assertEquals("IllegalArgumentException | IllegalStateException problem", excerpt(Files.readString(
        temporaryDirectory.resolve("Catches.java")), catchParameter));
  }

  @Test
  void givenRequiredTypeFailureStates_whenDiagnosed_thenCodesOutcomesAndSourceRangesRemainDistinct() {
    // Arrange
    var range = new SourceSpan("source", 11, 18);
    var states = List.<SyntaxType>of(SyntaxType.Unresolved.INSTANCE, SyntaxType.Erroneous.INSTANCE,
        new SyntaxType.NoType(SyntaxType.NoType.Kind.NONE), new SyntaxType.NoType(SyntaxType.NoType.Kind.PACKAGE),
        new SyntaxType.NoType(SyntaxType.NoType.Kind.MODULE), SyntaxType.Unsupported.INSTANCE,
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Missing", "Missing")),
        new SyntaxType.StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.UNION, "First | Second", "First")));

    // Act
    var diagnostics = states.stream().map(state -> JavaTypeUseTranslator
        .requiredTypeOutcomeDiagnostic("source", state, Optional.of(range)).orElseThrow()).toList();

    // Assert
    assertEquals(List.of("frontend.unavailable-type-use-attribution", "frontend.erroneous-type-use",
        "frontend.no-type-in-required-type-use", "frontend.no-type-in-required-type-use",
        "frontend.no-type-in-required-type-use", "frontend.unsupported-attributed-type-use",
        "frontend.unavailable-type-use-structure", "frontend.unavailable-type-use-structure"),
        diagnostics.stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(List.of(AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE,
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR,
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR,
        AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR),
        diagnostics.stream().map(diagnostic -> diagnostic.outcomeKind()).toList());
    assertTrue(diagnostics.stream().allMatch(diagnostic -> diagnostic.origins().equals(List.of(range))
        && diagnostic.severity() == DiagnosticSeverity.ERROR));
    assertEquals(List.of(SyntaxType.NoType.Kind.NONE, SyntaxType.NoType.Kind.PACKAGE, SyntaxType.NoType.Kind.MODULE),
        states.stream().filter(SyntaxType.NoType.class::isInstance).map(SyntaxType.NoType.class::cast)
            .map(SyntaxType.NoType::kind).toList());
    assertTrue(JavaTypeUseTranslator.requiredTypeOutcomeDiagnostic("source",
        new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.VOID, "void", "void"),
            new JavaTypeStructure.Primitive(JavaTypeCategory.VOID)), Optional.of(range)).isEmpty());
  }

  @Test
  void givenRequiredNoTypeAndUnsupportedOutcomes_whenTranslated_thenLocatedErrorsBlockAnalysis() throws IOException {
    // Arrange
    var source = "package p; class States {}";
    Files.writeString(temporaryDirectory.resolve("States.java"), source);
    var baseline = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(baseline).completion(),
        () -> baseline.diagnostics().toString());
    var sources = new SnapshotSourceCatalog(baseline.snapshot());
    var states = List.<SyntaxType>of(new SyntaxType.NoType(SyntaxType.NoType.Kind.NONE), SyntaxType.Unsupported.INSTANCE);
    var expectedCodes = List.of("frontend.no-type-in-required-type-use", "frontend.unsupported-attributed-type-use");
    var expectedSpan = new SourceSpan(baseline.snapshot().sourceFiles().getFirst().sourceFileKey(), source.indexOf("p;"),
        source.indexOf("p;") + 1);
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);

    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(JAVA_25), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var typeTree = unit.getPackageName();
      var translator = new JavaTypeUseTranslator(compilation.semantics(), compilation.sourceEvidence(), parsed.treeInventory());

      // Act and Assert
      for (var index = 0; index < states.size(); index++) {
        var diagnostics = JavaCoverageTestSupport.diagnostics(baseline, "type-use-observation");
        var baselineCount = diagnostics.size();
        var translated = translator.translateAttributed(unit, sources.find(unit).orElseThrow(), typeTree,
            TreePath.getPath(unit, typeTree), path, path, states.get(index), diagnostics).orElseThrow();
        assertEquals(states.get(index), translated.type());
        assertEquals(baselineCount + 1, diagnostics.size());
        assertEquals(expectedCodes.get(index), diagnostics.getLast().code());
        assertEquals(List.of(expectedSpan), diagnostics.getLast().origins());
        assertEquals(DiagnosticSeverity.ERROR, diagnostics.getLast().severity());
        var analysisInput = JavaCoverageTestSupport.withFacts(baseline, baseline.program(), baseline.aggregate(), diagnostics);
        assertNotEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(analysisInput).completion());
      }
    }
  }

  @Test
  void givenUnavailableRequiredStructure_whenTypeUseIsTranslated_thenWrittenFactsSurviveAndLocatedErrorBlocksAnalysis() throws IOException {
    // Arrange
    var source = "package p; class States { String value; }";
    Files.writeString(temporaryDirectory.resolve("States.java"), source);
    var baseline = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(baseline).completion(),
        () -> baseline.diagnostics().toString());
    var sources = new SnapshotSourceCatalog(baseline.snapshot());
    var expectedSpan = new SourceSpan(baseline.snapshot().sourceFiles().getFirst().sourceFileKey(), source.indexOf("String"),
        source.indexOf("String") + "String".length());
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
        .child(SyntaxRole.MEMBER, 0).child(SyntaxRole.DECLARED_TYPE, 0);

    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(JAVA_25), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var owner = (ClassTree) unit.getTypeDecls().getFirst();
      var field = owner.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast).findFirst().orElseThrow();
      var typeTree = field.getType();
      var knownType = (SyntaxType.Resolved) compilation.semantics().explicitType(TreePath.getPath(unit, typeTree));
      var type = new SyntaxType.StructureUnavailable(knownType.identity());
      var translator = new JavaTypeUseTranslator(compilation.semantics(), compilation.sourceEvidence(), parsed.treeInventory());
      var diagnostics = JavaCoverageTestSupport.diagnostics(baseline, "type-use-observation");

      // Act
      var translated = translator.translateAttributed(unit, sources.find(unit).orElseThrow(), typeTree,
          TreePath.getPath(unit, typeTree), path, path, type, diagnostics);
      var analysisInput = JavaCoverageTestSupport.withFacts(baseline, baseline.program(), baseline.aggregate(), diagnostics);
      var completion = new SourceAnalysisFixture().analyze(analysisInput).completion();

      // Assert
      var node = translated.orElseThrow();
      assertEquals(type, node.type());
      assertEquals("String", node.value(SyntaxValue.Symbol.class).name());
      assertEquals(Optional.of(expectedSpan), node.origin());
      assertEquals(ReferenceKind.TYPE, node.requiredReference().referenceKind());
      assertEquals(ResolutionStatus.RESOLVED, node.requiredReference().status());
      assertEquals(baseline.diagnostics().size() + 1, diagnostics.size());
      assertEquals("frontend.unavailable-type-use-structure", diagnostics.getLast().code());
      assertEquals(List.of(expectedSpan), diagnostics.getLast().origins());
      assertNotEquals(AnalysisCompletion.COMPLETE, completion);
    }
  }

  @Test
  void givenSameCapturedTypeSource_whenAnalyzedTwice_thenTypeTuplesAndDiagnosticsAreIdentical() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Stable.java"), """
        import java.util.List;
        class Stable<T extends Number & java.io.Serializable> {
          List<?> first;
          List<? super T> second;
          T[] values;
          void run() {
            try { throw new IllegalArgumentException(); }
            catch (IllegalArgumentException | IllegalStateException problem) { }
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.analyze(temporaryDirectory, JAVA_25);
    var second = frontend.analyze(temporaryDirectory, JAVA_25);
    var firstAggregate = first.aggregate().orElseThrow();
    var secondAggregate = second.aggregate().orElseThrow();

    // Assert
    assertEquals(firstAggregate.nodeTuples(), secondAggregate.nodeTuples());
    assertEquals(firstAggregate.rangeTuples(), secondAggregate.rangeTuples());
    assertEquals(firstAggregate.structuralEdges(), secondAggregate.structuralEdges());
    assertEquals(firstAggregate.semanticEdges(), secondAggregate.semanticEdges());
    assertEquals(firstAggregate.nodes(), secondAggregate.nodes());
    var parameter = firstAggregate.nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.TypeParameterDeclaration).findFirst().orElseThrow();
    assertEquals(new JavaTypeStructure.Intersection(List.of(
        new JavaTypeStructure.Declared("type:java.lang.Number", Optional.empty(), List.of()),
        new JavaTypeStructure.Declared("type:java.io.Serializable", Optional.empty(), List.of()))),
        parameter.value(SyntaxValue.TypeParameterDeclaration.class).upperBound());
    var catchParameter = firstAggregate.nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local && local.name().equals("problem"))
        .findFirst().orElseThrow();
    assertEquals(new JavaTypeStructure.Union(List.of(
        new JavaTypeStructure.Declared("type:java.lang.IllegalArgumentException", Optional.empty(), List.of()),
        new JavaTypeStructure.Declared("type:java.lang.IllegalStateException", Optional.empty(), List.of()))),
        structure(catchParameter));
    assertEquals(first.diagnostics(), second.diagnostics());
  }

  private static String excerpt(String source, SyntaxNode node) {
    var range = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
  }

  private static JavaTypeStructure structure(SyntaxNode node) {
    return ((SyntaxType.Resolved) node.type()).structure().orElseThrow();
  }

  private static SyntaxNodeTuple tuple(String owner, SyntaxStructuralPath path, JavaSourceKind kind) {
    return new SyntaxNodeTuple(SyntaxNodeKey.from(owner, path, kind).value(), owner, path, kind);
  }

  private static SyntaxRangeTuple span(String owner, SyntaxStructuralPath path, JavaSourceKind kind, int start, int length) {
    return new SyntaxRangeTuple(SyntaxNodeKey.from(owner, path, kind).value(), new SourceSpan(owner, start, start + length));
  }

  private static SyntaxStructuralEdge edge(String owner, SyntaxStructuralPath parent, JavaSourceKind parentKind, SyntaxRole role,
      int ordinal, JavaSourceKind childKind) {
    return new SyntaxStructuralEdge(SyntaxNodeKey.from(owner, parent, parentKind).value(), role, ordinal,
        SyntaxNodeKey.from(owner, parent.child(role, ordinal), childKind).value());
  }

  private static SyntaxSemanticEdge reference(String owner, SyntaxStructuralPath path, JavaSourceKind kind, String declarationKey) {
    return reference(owner, path, kind, ReferenceKind.TYPE, declarationKey);
  }

  private static SyntaxSemanticEdge reference(String owner, SyntaxStructuralPath path, JavaSourceKind kind, ReferenceKind referenceKind,
      String declarationKey) {
    var target = new SemanticReference(referenceKind, ResolutionStatus.RESOLVED, Optional.of(declarationKey), Optional.empty(),
        List.of(), List.of());
    return new SyntaxSemanticEdge(SyntaxNodeKey.from(owner, path, kind).value(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(target));
  }

  private static ExpectedType expected(JavaSourceKind kind, int start, int length, String key, ExpectedChild... children) {
    return expected(kind, start, length, ReferenceKind.TYPE, key, children);
  }

  private static ExpectedType expected(JavaSourceKind kind, int start, int length, ReferenceKind referenceKind, String key,
      ExpectedChild... children) {
    return new ExpectedType(kind, start, length, referenceKind, Optional.ofNullable(key), List.of(children), Optional.empty());
  }

  private static ExpectedType withStructure(ExpectedType expected, JavaTypeStructure structure) {
    return new ExpectedType(expected.kind(), expected.start(), expected.length(), expected.referenceKind(), expected.key(),
        expected.children(), Optional.of(structure));
  }

  private static ExpectedType primitive(JavaTypeCategory kind, int start, int length) {
    return withStructure(expected(JavaSourceKind.PRIMITIVE_TYPE, start, length, null), new JavaTypeStructure.Primitive(kind));
  }

  private static ExpectedChild child(SyntaxRole role, int ordinal, ExpectedType type) {
    return new ExpectedChild(role, ordinal, type);
  }

  private static void assertExactTypeInventory(JavaAstAggregate aggregate, String owner, List<ExpectedRoot> roots) {
    var expectedNodes = new ArrayList<SyntaxNodeTuple>();
    var expectedRanges = new ArrayList<SyntaxRangeTuple>();
    var expectedEdges = new ArrayList<SyntaxStructuralEdge>();
    var expectedReferences = new ArrayList<SyntaxSemanticEdge>();
    for (var root : roots) {
      appendExpectedType(aggregate, owner, root.path(), root.type(), expectedNodes, expectedRanges, expectedEdges,
          expectedReferences);
    }
    assertEquals(Set.copyOf(expectedNodes), Set.copyOf(aggregate.nodeTuples().stream()
        .filter(tuple -> aggregate.node(tuple.nodeKey()).orElseThrow().syntaxKind() == SyntaxKind.TYPE_USE).toList()));
    assertEquals(Set.copyOf(expectedRanges), Set.copyOf(aggregate.rangeTuples().stream()
        .filter(tuple -> aggregate.node(tuple.nodeKey()).orElseThrow().syntaxKind() == SyntaxKind.TYPE_USE).toList()));
    assertEquals(Set.copyOf(expectedEdges), Set.copyOf(aggregate.structuralEdges().stream()
        .filter(edge -> aggregate.node(edge.parentKey()).orElseThrow().syntaxKind() == SyntaxKind.TYPE_USE).toList()));
    assertEquals(Set.copyOf(expectedReferences), Set.copyOf(aggregate.semanticEdges().stream()
        .filter(edge -> aggregate.node(edge.sourceKey()).orElseThrow().syntaxKind() == SyntaxKind.TYPE_USE).toList()));
  }

  private static void appendExpectedType(JavaAstAggregate aggregate, String owner, SyntaxStructuralPath path, ExpectedType expected,
      List<SyntaxNodeTuple> nodes, List<SyntaxRangeTuple> ranges, List<SyntaxStructuralEdge> edges,
      List<SyntaxSemanticEdge> references) {
    if (expected.kind() == JavaSourceKind.ANNOTATION) {
      return;
    }
    var node = aggregate.node(SyntaxNodeKey.from(owner, path, expected.kind()).value()).orElseThrow();
    var attributed = expectedStructure(expected);
    if (attributed.isPresent()) {
      assertEquals(attributed.orElseThrow(), structure(node));
    } else {
      assertEquals(SyntaxType.NotApplicable.INSTANCE, node.type());
    }
    nodes.add(tuple(owner, path, expected.kind()));
    ranges.add(span(owner, path, expected.kind(), expected.start(), expected.length()));
    expected.key().ifPresent(key -> references.add(reference(owner, path, expected.kind(), expected.referenceKind(), key)));
    for (var child : expected.children()) {
      edges.add(edge(owner, path, expected.kind(), child.role(), child.ordinal(), child.type().kind()));
      appendExpectedType(aggregate, owner, path.child(child.role(), child.ordinal()), child.type(), nodes, ranges, edges,
          references);
    }
  }

  private static Optional<JavaTypeStructure> expectedStructure(ExpectedType expected) {
    if (expected.structureOverride().isPresent()) {
      return expected.structureOverride();
    }
    if (expected.kind() == JavaSourceKind.ARRAY_TYPE) {
      return Optional.of(new JavaTypeStructure.Array(expectedStructure(childType(expected, SyntaxRole.COMPONENT_TYPE)).orElseThrow()));
    }
    if (expected.kind() == JavaSourceKind.ANNOTATED_TYPE) {
      return expectedStructure(childType(expected, SyntaxRole.UNDERLYING_TYPE));
    }
    if (expected.key().isEmpty()) {
      return Optional.empty();
    }
    var key = expected.key().orElseThrow();
    if (expected.referenceKind() == ReferenceKind.TYPE_PARAMETER) {
      return Optional.of(new JavaTypeStructure.Variable(key));
    }
    var enclosing = expected.children().stream().filter(child -> child.role() == SyntaxRole.TYPE_QUALIFIER)
        .map(child -> expectedStructure(child.type())).flatMap(Optional::stream).findFirst();
    if (expected.kind() == JavaSourceKind.PARAMETERIZED_TYPE) {
      enclosing = expected.children().stream().filter(child -> child.role() == SyntaxRole.TYPE_NAME)
          .flatMap(child -> child.type().children().stream())
          .filter(child -> child.role() == SyntaxRole.TYPE_QUALIFIER)
          .map(child -> expectedStructure(child.type())).flatMap(Optional::stream).findFirst();
    }
    var arguments = expected.children().stream().filter(child -> child.role() == SyntaxRole.TYPE_ARGUMENT)
        .map(child -> expectedStructure(child.type()).orElseThrow()).toList();
    return Optional.of(new JavaTypeStructure.Declared(key, enclosing, arguments));
  }

  private static ExpectedType childType(ExpectedType parent, SyntaxRole role) {
    return parent.children().stream().filter(child -> child.role() == role).findFirst().orElseThrow().type();
  }

  private record ExpectedRoot(SyntaxStructuralPath path, ExpectedType type) {
  }

  private record ExpectedType(JavaSourceKind kind, int start, int length, ReferenceKind referenceKind, Optional<String> key,
      List<ExpectedChild> children, Optional<JavaTypeStructure> structureOverride) {
  }

  private record ExpectedChild(SyntaxRole role, int ordinal, ExpectedType type) {
  }
}
