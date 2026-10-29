package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.JavaDeclarationModifier;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.JavaTypeModifier;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class DeclarationOwnershipContractTest {
  @TempDir Path directory;

  @Test
  void givenEmptyStaticAndInstanceInitializers_whenProducedAndDecoded_thenDistinctNonMethodScopesAndOrderSurvive() {
    // Arrange
    var source = "class Example { static {} {} Example() {} }";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var type = ast.aggregate().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION);
      var members = type.children(SyntaxRole.MEMBER);
      assertEquals(List.of(SyntaxKind.STATIC_INITIALIZER_BLOCK, SyntaxKind.INSTANCE_INITIALIZER_BLOCK, SyntaxKind.METHOD_DECLARATION),
          members.stream().map(SyntaxNode::syntaxKind).toList());
      var scopes = ast.aggregate().executionScopes().stream().filter(ExecutionScope.Initializer.class::isInstance)
          .map(ExecutionScope.Initializer.class::cast).toList();
      assertEquals(2, scopes.size());
      assertNotEquals(scopes.getFirst().key(), scopes.getLast().key());
      for (var position = 0; position < 2; position++) {
        var block = members.get(position);
        var scope = scopes.stream().filter(value -> value.origin().equals(block.origin().orElseThrow())).findFirst().orElseThrow();
        assertEquals(typeKey("Example"), scope.ownerKey());
        assertEquals(block.syntaxKind(), scope.kind());
        assertEquals(typeKey("Example") + ":initializer:" + block.origin().orElseThrow().startOffset()
            + ":" + block.origin().orElseThrow().endOffsetExclusive(), scope.key());
        assertEquals(type.structuralPath().child(SyntaxRole.MEMBER, position), block.structuralPath());
        assertSpan(source, block, position == 0 ? "static {}" : "{}", position == 0 ? source.indexOf("static") : source.indexOf("{}", source.indexOf("static") + 9));
        assertTrue(block.children(SyntaxRole.STATEMENT).isEmpty());
        assertEquals(block, ast.aggregate().executionSyntax(scope.key()).orElseThrow().root());
        assertTrue(ast.aggregate().methodSyntax(scope.key()).isEmpty());
      }
      var constructor = members.getLast().value(SyntaxValue.ExecutableDeclaration.class);
      assertEquals(typeKey("Example") + "#<init>()", constructor.declarationKey());
      assertEquals(typeKey("Example"), constructor.ownerKey());
      assertEquals(SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR, constructor.kind());
      assertInstanceOf(ExecutionScope.Method.class, ast.aggregate().executionSyntax(constructor.declarationKey()).orElseThrow().scope());
      assertTrue(members.getLast().requiredChild(SyntaxRole.BODY).children(SyntaxRole.STATEMENT).isEmpty());
    }
  }

  @Test
  void givenInterfaceBeforeCompactMainAndLaterTypes_whenProducedAndDecoded_thenUnitOwnsExactWrittenMemberOrder() {
    // Arrange
    var source = "interface Before {} void main() {} class Later {} interface Last {}";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var root = ast.aggregate().roots().getFirst();
      assertEquals(SyntaxValue.CompilationUnitForm.COMPACT, root.value(SyntaxValue.CompilationUnit.class).form());
      assertTrue(root.children(SyntaxRole.TYPE_DECLARATION).isEmpty());
      var members = root.children(SyntaxRole.COMPACT_MEMBER);
      assertEquals(List.of(SyntaxKind.TYPE_DECLARATION, SyntaxKind.METHOD_DECLARATION, SyntaxKind.TYPE_DECLARATION, SyntaxKind.TYPE_DECLARATION),
          members.stream().map(SyntaxNode::syntaxKind).toList());
      var written = List.of("interface Before {}", "void main() {}", "class Later {}", "interface Last {}");
      for (var index = 0; index < members.size(); index++) {
        assertSpan(source, members.get(index), written.get(index), source.indexOf(written.get(index)));
        assertEquals(root.structuralPath().child(SyntaxRole.COMPACT_MEMBER, index), members.get(index).structuralPath());
      }
      assertEquals(typeKey("Example") + "#main()", members.get(1).value(SyntaxValue.ExecutableDeclaration.class).declarationKey());
      assertEquals(typeKey("Example"), members.get(1).value(SyntaxValue.ExecutableDeclaration.class).ownerKey());
      for (var index : List.of(0, 2, 3)) {
        var name = List.of("Before", "", "Later", "Last").get(index);
        var value = members.get(index).value(SyntaxValue.TypeDeclaration.class);
        assertEquals(typeKey("Example$" + name), value.declarationKey());
        var semantic = semanticType(ast, "Example$" + name);
        assertEquals(typeKey("Example"), semantic.enclosingType().orElseThrow().declarationKey().orElseThrow());
      }
      assertEquals(3, ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration).count());
    }
  }

  @Test
  void givenNonSealedStrictAndProtectedMemberTypes_whenProducedAndDecoded_thenWrittenAndEffectiveModifiersStayDistinct() {
    // Arrange
    var source = "class Example { protected class Nested {} protected interface Contract {} } "
        + "strictfp class Floating {} sealed class Base permits Open {} non-sealed class Open extends Base {} "
        + "sealed interface Parent permits Extension {} non-sealed interface Extension extends Parent {}";

    // Act
    var results = produceAndDecode(source, "compiler.warn.strictfp");

    // Assert
    for (var ast : results) {
      assertTypeModifiers(ast, "Example$Nested", Set.of(JavaTypeModifier.PROTECTED), Set.of(JavaTypeModifier.PROTECTED));
      assertTypeModifiers(ast, "Example$Contract", Set.of(JavaTypeModifier.PROTECTED),
          Set.of(JavaTypeModifier.PROTECTED, JavaTypeModifier.STATIC, JavaTypeModifier.ABSTRACT));
      assertTypeModifiers(ast, "Floating", Set.of(JavaTypeModifier.STRICTFP), Set.of(JavaTypeModifier.STRICTFP));
      assertTypeModifiers(ast, "Open", Set.of(JavaTypeModifier.NON_SEALED), Set.of(JavaTypeModifier.NON_SEALED));
      assertTypeModifiers(ast, "Extension", Set.of(JavaTypeModifier.NON_SEALED), Set.of(JavaTypeModifier.NON_SEALED, JavaTypeModifier.ABSTRACT));
      for (var name : List.of("Example$Nested", "Example$Contract")) {
        assertEquals(typeKey("Example"), semanticType(ast, name).enclosingType().orElseThrow().declarationKey().orElseThrow());
      }
    }
  }

  @Test
  void givenSealedTypesWithoutWrittenPermits_whenProducedAndDecoded_thenExactInferredTargetsHaveNoWrittenChildren() {
    // Arrange
    var source = "sealed class Example {} final class First extends Example {} non-sealed class Second extends Example {} "
        + "sealed interface Contract {} final class Implementation implements Contract {} non-sealed interface Extension extends Contract {}";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      for (var name : List.of("Example", "Contract")) {
        var expected = name.equals("Example") ? List.of(typeKey("First"), typeKey("Second"))
            : List.of(typeKey("Implementation"), typeKey("Extension"));
        assertEquals(expected, semanticType(ast, name).permittedSubclasses().stream()
            .map(value -> value.declarationKey().orElseThrow()).toList());
        assertTrue(typeNode(ast, name).children(SyntaxRole.PERMITTED_SUBCLASS).isEmpty());
        assertEquals(0, typeNode(ast, name).value(SyntaxValue.TypeDeclaration.class).shape().permittedSubclasses());
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> givenOrderedAndReorderedClassAndInterfacePermits_whenProducedAndDecoded_thenEveryOperandKeepsItsIdentityAndPosition() {
    // Arrange
    var orders = List.of(List.of("First", "Second", "Third"), List.of("Third", "First", "Second"));

    // Act
    var tests = orders.stream().map(order -> DynamicTest.dynamicTest(
        "givenPermitsStartingWith" + order.getFirst() + "_whenProducedAndDecoded_thenWrittenAndAttributedOrderAgree", () -> {
          // Arrange
          var source = "sealed class Example permits " + String.join(", ", order) + " {} "
              + "final class First extends Example {} final class Second extends Example {} final class Third extends Example {} "
              + "sealed interface Contract permits " + String.join(", ", order.stream().map(name -> name + "Face").toList()) + " {} "
              + "non-sealed interface FirstFace extends Contract {} non-sealed interface SecondFace extends Contract {} "
              + "non-sealed interface ThirdFace extends Contract {}";

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            assertHeaderOperands(ast, source, "Example", SyntaxRole.PERMITTED_SUBCLASS, order, order);
            var faces = order.stream().map(name -> name + "Face").toList();
            assertHeaderOperands(ast, source, "Contract", SyntaxRole.PERMITTED_SUBCLASS, faces, faces);
          }
        }));
    // Assert
    return tests;
  }

  @Test
  void givenGenericDiamondInterfaceInheritance_whenProducedAndDecoded_thenOrderedRecursiveOperandsAndOwnersSurvive() {
    // Arrange
    var source = "interface Root<T> { T get(); } interface Left<T> extends Root<T> {} interface Right<T> extends Root<T> {} "
        + "interface Example extends Left<String>, Right<String> {}";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      assertHeaderOperands(ast, source, "Example", SyntaxRole.INTERFACE,
          List.of("Left<String>", "Right<String>"), List.of("Left", "Right"));
      var owner = typeNode(ast, "Example");
      assertTrue(owner.children(SyntaxRole.MEMBER).isEmpty());
      for (var operand : owner.children(SyntaxRole.INTERFACE)) {
        assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, operand.sourceKind());
        var argument = operand.requiredChild(SyntaxRole.TYPE_ARGUMENT);
        assertSpan(source, argument, "String", source.indexOf("String", Math.toIntExact(operand.origin().orElseThrow().startOffset())));
        assertEquals(platformKey("java.lang.String"), argument.requiredReference().symbolKey().orElseThrow());
        var structure = assertInstanceOf(JavaTypeStructure.Declared.class, assertInstanceOf(SyntaxType.Resolved.class, operand.type()).structure().orElseThrow());
        assertEquals(List.of(new JavaTypeStructure.Declared(platformKey("java.lang.String"), Optional.empty(), List.of())), structure.arguments());
      }
    }
  }

  @Test
  void givenInterfaceFieldWithSelectedCall_whenProducedAndDecoded_thenInitializerFactsBelongToTheField() {
    // Arrange
    var source = "interface Example { int VALUE = select(1, 2L); static int select(int first, long second) { return first; } }";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var field = member(ast, "Example", "VALUE");
      var value = field.value(SyntaxValue.VariableDeclaration.class);
      var fieldKey = typeKey("Example") + "#field:VALUE";
      assertEquals(fieldKey, value.declarationKey());
      assertEquals(typeKey("Example"), value.ownerKey());
      assertEquals(Set.of(), value.writtenModifiers());
      assertEquals(Set.of(JavaDeclarationModifier.PUBLIC, JavaDeclarationModifier.STATIC, JavaDeclarationModifier.FINAL), value.effectiveModifiers());
      var execution = ast.aggregate().executionSyntax(fieldKey).orElseThrow();
      assertEquals(new ExecutionScope.Field(fieldKey, typeKey("Example")), execution.scope());
      assertEquals(field, execution.root());
      assertTrue(ast.aggregate().methodSyntax(fieldKey).isEmpty());
      var call = field.requiredChild(SyntaxRole.INITIALIZER);
      assertEquals(SyntaxKind.INVOCATION, call.syntaxKind());
      assertSpan(source, call, "select(1, 2L)", source.indexOf("select("));
      assertEquals(typeKey("Example") + "#select(int,long)", call.requiredReference().symbolKey().orElseThrow());
      assertEquals("int", call.type().knownIdentity().orElseThrow().erasedName());
      assertEquals(List.of("1", "2L"), call.children(SyntaxRole.ARGUMENT).stream().map(node -> written(source, node)).toList());
      assertEquals("int", execution.useEvidence(field.nodeId(), SyntaxRole.INITIALIZER, 0).orElseThrow().selectedTarget().erasedName());
      for (var index = 0; index < 2; index++) {
        var argument = call.children(SyntaxRole.ARGUMENT).get(index);
        assertEquals(call.structuralPath().child(SyntaxRole.ARGUMENT, index), argument.structuralPath());
        assertEquals(index == 0 ? "int" : "long", execution.useEvidence(call.nodeId(), SyntaxRole.ARGUMENT, index).orElseThrow().selectedTarget().erasedName());
      }
      assertEquals(List.of(typeKey("Example") + "#select(int,long)"), ast.aggregate().executionScopes().stream()
          .filter(ExecutionScope.Method.class::isInstance).map(ExecutionScope::key).toList());
    }
  }

  @Test
  void givenVolatileTransientFieldCombinations_whenProducedAndDecoded_thenExactModifiersAndOwnersSurvive() {
    // Arrange
    var declarations = List.of("private transient int first;", "protected volatile int second;",
        "public static volatile transient int third;", "static transient int fourth;", "volatile transient int fifth;");
    var names = List.of("first", "second", "third", "fourth", "fifth");
    var expected = List.of(Set.of(JavaDeclarationModifier.PRIVATE, JavaDeclarationModifier.TRANSIENT),
        Set.of(JavaDeclarationModifier.PROTECTED, JavaDeclarationModifier.VOLATILE),
        Set.of(JavaDeclarationModifier.PUBLIC, JavaDeclarationModifier.STATIC, JavaDeclarationModifier.VOLATILE, JavaDeclarationModifier.TRANSIENT),
        Set.of(JavaDeclarationModifier.STATIC, JavaDeclarationModifier.TRANSIENT),
        Set.of(JavaDeclarationModifier.VOLATILE, JavaDeclarationModifier.TRANSIENT));
    var source = "class Example { " + String.join(" ", declarations) + " }";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      assertEquals(5, typeNode(ast, "Example").children(SyntaxRole.MEMBER).size());
      for (var index = 0; index < names.size(); index++) {
        var node = member(ast, "Example", names.get(index));
        var value = node.value(SyntaxValue.VariableDeclaration.class);
        assertEquals(expected.get(index), value.writtenModifiers());
        assertEquals(expected.get(index), value.effectiveModifiers());
        assertEquals(typeKey("Example") + "#field:" + names.get(index), value.declarationKey());
        assertEquals(typeKey("Example"), value.ownerKey());
        assertSpan(source, node, declarations.get(index), source.indexOf(declarations.get(index)));
      }
    }
  }

  @Test
  void givenClassMethodModifierCombinations_whenProducedAndDecoded_thenExactFlagsAndBodyApplicabilitySurvive() {
    // Arrange
    var source = "abstract class Example { private static final synchronized int locked(int value) { return value; } "
        + "protected final synchronized void guarded() {} private strictfp double floating() { return 1.0; } "
        + "public native int external(); protected abstract void deferred(); public void ordinary() {} }";
    var names = List.of("locked", "guarded", "floating", "external", "deferred", "ordinary");
    var expected = List.of(Set.of(JavaDeclarationModifier.PRIVATE, JavaDeclarationModifier.STATIC, JavaDeclarationModifier.FINAL, JavaDeclarationModifier.SYNCHRONIZED),
        Set.of(JavaDeclarationModifier.PROTECTED, JavaDeclarationModifier.FINAL, JavaDeclarationModifier.SYNCHRONIZED),
        Set.of(JavaDeclarationModifier.PRIVATE, JavaDeclarationModifier.STRICTFP),
        Set.of(JavaDeclarationModifier.PUBLIC, JavaDeclarationModifier.NATIVE),
        Set.of(JavaDeclarationModifier.PROTECTED, JavaDeclarationModifier.ABSTRACT), Set.of(JavaDeclarationModifier.PUBLIC));

    // Act
    var results = produceAndDecode(source, "compiler.warn.strictfp");

    // Assert
    for (var ast : results) {
      assertEquals(names, typeNode(ast, "Example").children(SyntaxRole.MEMBER).stream()
          .map(node -> node.value(SyntaxValue.ExecutableDeclaration.class).name()).toList());
      for (var index = 0; index < names.size(); index++) {
        var node = member(ast, "Example", names.get(index));
        var value = node.value(SyntaxValue.ExecutableDeclaration.class);
        var body = index != 3 && index != 4;
        assertEquals(expected.get(index), value.writtenModifiers());
        assertEquals(expected.get(index), value.effectiveModifiers());
        assertEquals(typeKey("Example"), value.ownerKey());
        assertEquals(typeKey("Example") + "#" + names.get(index) + (index == 0 ? "(int)" : "()"), value.declarationKey());
        assertEquals(body, value.shape().body());
        assertEquals(body ? 1 : 0, node.children(SyntaxRole.BODY).size());
        assertEquals(body, ast.body(value.declarationKey()).isPresent());
      }
    }
  }

  @Test
  void givenTrailingReturnAndFormalDimensions_whenProducedAndDecoded_thenRecursiveTypesRangesAndParameterPositionsSurvive() {
    // Arrange
    var declaration = "int values(String first[], int second[][])[][] { return second; }";
    var source = "class Example { " + declaration + " }";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var method = member(ast, "Example", "values");
      var methodKey = typeKey("Example") + "#values(java.lang.String[],int[][])";
      assertEquals(methodKey, method.value(SyntaxValue.ExecutableDeclaration.class).declarationKey());
      assertEquals(typeKey("Example"), method.value(SyntaxValue.ExecutableDeclaration.class).ownerKey());
      assertSpan(source, method, declaration, source.indexOf(declaration));
      var result = method.requiredChild(SyntaxRole.RETURN_TYPE);
      assertArrayType(result, 2, "int");
      assertSpan(source, result, "int values(String first[], int second[][])[][]", source.indexOf("int values"));
      var parameters = method.children(SyntaxRole.PARAMETER);
      assertEquals(2, parameters.size());
      var names = List.of("first", "second");
      var declarations = List.of("String first[]", "int second[][]");
      for (var index = 0; index < 2; index++) {
        var parameter = parameters.get(index);
        assertEquals(methodKey + ":parameter:" + index, parameter.value(SyntaxValue.ParameterDeclaration.class).declarationKey());
        assertEquals(names.get(index), parameter.value(SyntaxValue.ParameterDeclaration.class).name());
        assertEquals(method.structuralPath().child(SyntaxRole.PARAMETER, index), parameter.structuralPath());
        assertSpan(source, parameter, declarations.get(index), source.indexOf(declarations.get(index)));
        var type = parameter.requiredChild(SyntaxRole.DECLARED_TYPE);
        assertArrayType(type, index + 1, index == 0 ? "java.lang.String" : "int");
        assertSpan(source, type, declarations.get(index), source.indexOf(declarations.get(index)));
      }
      var semantic = ast.program().methods().stream().filter(value -> value.methodKey().equals(methodKey)).findFirst().orElseThrow();
      assertEquals("int[][]", semantic.returnType());
      assertEquals(List.of("java.lang.String[]", "int[][]"), semantic.parameters().stream().map(value -> value.staticType().erasedName()).toList());
    }
  }

  @Test
  void givenStaticAndPrivateInterfaceMethods_whenProducedAndDecoded_thenReceiversOwnersParametersAndBodiesRemainDistinct() {
    // Arrange
    var source = "interface Example { public static int exposed(int value) { return value; } "
        + "private int instance(Example this, int value) { return value; } private static int hidden(int value) { return value; } }";
    var names = List.of("exposed", "instance", "hidden");
    var expected = List.of(Set.of(JavaDeclarationModifier.PUBLIC, JavaDeclarationModifier.STATIC), Set.of(JavaDeclarationModifier.PRIVATE),
        Set.of(JavaDeclarationModifier.PRIVATE, JavaDeclarationModifier.STATIC));

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      assertEquals(3, typeNode(ast, "Example").children(SyntaxRole.MEMBER).size());
      for (var index = 0; index < names.size(); index++) {
        var node = member(ast, "Example", names.get(index));
        var value = node.value(SyntaxValue.ExecutableDeclaration.class);
        var key = typeKey("Example") + "#" + names.get(index) + "(int)";
        assertEquals(key, value.declarationKey());
        assertEquals(typeKey("Example"), value.ownerKey());
        assertEquals(expected.get(index), value.writtenModifiers());
        assertEquals(expected.get(index), value.effectiveModifiers());
        assertEquals(index == 1, value.shape().receiver());
        assertEquals(index == 1 ? 1 : 0, node.children(SyntaxRole.RECEIVER_PARAMETER).size());
        assertTrue(value.shape().body());
        assertEquals(key + ":parameter:0", node.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.ParameterDeclaration.class).declarationKey());
        assertEquals("value", node.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.ParameterDeclaration.class).name());
        if (index == 1) {
          var receiver = node.requiredChild(SyntaxRole.RECEIVER_PARAMETER);
          assertEquals(key, receiver.value(SyntaxValue.ReceiverParameter.class).ownerKey());
          assertSpan(source, receiver, "Example this", source.indexOf("Example this"));
          assertEquals(typeKey("Example"), assertInstanceOf(JavaTypeStructure.Declared.class,
              assertInstanceOf(SyntaxType.Resolved.class, value.receiverType()).structure().orElseThrow()).declarationKey());
          assertEquals(Optional.of(typeKey("Example")), value.receiverOwnerKey());
        } else {
          assertEquals(SyntaxType.NotApplicable.INSTANCE, value.receiverType());
          assertTrue(value.receiverOwnerKey().isEmpty());
        }
        var body = node.requiredChild(SyntaxRole.BODY);
        assertEquals(body, ast.body(key).orElseThrow().root());
        var returned = body.requiredChild(SyntaxRole.STATEMENT).requiredChild(SyntaxRole.EXPRESSION);
        assertEquals(key + ":parameter:0", returned.requiredReference().symbolKey().orElseThrow());
      }
    }
  }

  @Test
  void givenPrivateInterfaceMethodWithoutWrittenReceiver_whenProducedAndDecoded_thenOptionalReceiverAbsenceDoesNotInventSyntax() {
    // Arrange
    var source = "interface Example { private int instance(int value) { return value; } }";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var node = member(ast, "Example", "instance");
      var value = node.value(SyntaxValue.ExecutableDeclaration.class);
      assertEquals(typeKey("Example") + "#instance(int)", value.declarationKey());
      assertEquals(Set.of(JavaDeclarationModifier.PRIVATE), value.writtenModifiers());
      assertEquals(Set.of(JavaDeclarationModifier.PRIVATE), value.effectiveModifiers());
      assertFalse(value.shape().receiver());
      assertTrue(node.children(SyntaxRole.RECEIVER_PARAMETER).isEmpty());
      assertEquals(SyntaxType.NotApplicable.INSTANCE, value.receiverType());
      assertTrue(value.receiverOwnerKey().isEmpty());
      assertEquals(node.requiredChild(SyntaxRole.BODY), ast.body(value.declarationKey()).orElseThrow().root());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenEnumTrailingCommaVariants_whenProducedAndDecoded_thenOnlyWrittenConstantsAndMembersRemain() {
    // Arrange
    var cases = List.of("enum Example { A, }", "enum Example { A,; int x; }");

    // Act
    var tests = cases.stream().map(source -> DynamicTest.dynamicTest(
        "givenTrailingComma" + (source.contains("int x") ? "WithMember" : "WithoutMember") + "_whenProducedAndDecoded_thenNoDeclarationIsInvented", () -> {
          // Arrange
          var withField = source.contains("int x");

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            var type = typeNode(ast, "Example");
            var members = type.children(SyntaxRole.MEMBER);
            assertEquals(withField ? List.of(SyntaxKind.ENUM_CONSTANT_DECLARATION, SyntaxKind.FIELD_DECLARATION)
                : List.of(SyntaxKind.ENUM_CONSTANT_DECLARATION), members.stream().map(SyntaxNode::syntaxKind).toList());
            assertEquals(List.of("A"), semanticType(ast, "Example").fields().stream().filter(value -> value.enumConstant()).map(value -> value.name()).toList());
            assertEquals(typeKey("Example") + "#enum:A", members.getFirst().value(SyntaxValue.VariableDeclaration.class).declarationKey());
            assertEquals(typeKey("Example"), members.getFirst().value(SyntaxValue.VariableDeclaration.class).ownerKey());
            assertSpan(source, members.getFirst(), "A", source.indexOf('A'));
            assertEquals(type.structuralPath().child(SyntaxRole.MEMBER, 0), members.getFirst().structuralPath());
            if (withField) {
              assertEquals(typeKey("Example") + "#field:x", members.getLast().value(SyntaxValue.VariableDeclaration.class).declarationKey());
              assertEquals(typeKey("Example"), members.getLast().value(SyntaxValue.VariableDeclaration.class).ownerKey());
              assertSpan(source, members.getLast(), "int x;", source.indexOf("int x;"));
              assertEquals(type.structuralPath().child(SyntaxRole.MEMBER, 1), members.getLast().structuralPath());
            }
            assertTrue(ast.aggregate().nodes().stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.EMPTY));
          }
        }));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenEnumWithOneOrMultipleInterfaces_whenProducedAndDecoded_thenWrittenAndAttributedInterfaceOrderAgree() {
    // Arrange
    var cases = List.of(List.of("Left<String>"), List.of("Left<String>", "Right"));

    // Act
    var tests = cases.stream().map(operands -> DynamicTest.dynamicTest(
        "givenEnumWith" + operands.size() + "Interfaces_whenProducedAndDecoded_thenExactRecursiveHeadersSurvive", () -> {
          // Arrange
          var source = "interface Left<T> {} interface Right {} enum Example implements " + String.join(", ", operands) + " { A }";

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var ast : results) {
            assertHeaderOperands(ast, source, "Example", SyntaxRole.INTERFACE, operands,
                operands.size() == 1 ? List.of("Left") : List.of("Left", "Right"));
            var left = typeNode(ast, "Example").children(SyntaxRole.INTERFACE).getFirst();
            assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, left.sourceKind());
            assertEquals(platformKey("java.lang.String"), left.requiredChild(SyntaxRole.TYPE_ARGUMENT).requiredReference().symbolKey().orElseThrow());
            assertSpan(source, left.requiredChild(SyntaxRole.TYPE_ARGUMENT), "String", source.indexOf("String"));
            var structure = assertInstanceOf(JavaTypeStructure.Declared.class, assertInstanceOf(SyntaxType.Resolved.class, left.type()).structure().orElseThrow());
            assertEquals(List.of(new JavaTypeStructure.Declared(platformKey("java.lang.String"), Optional.empty(), List.of())), structure.arguments());
          }
        }));
    // Assert
    return tests;
  }

  @Test
  void givenRecordWithCanonicalAndOverloadedConstructors_whenProducedAndDecoded_thenDelegationAndComponentCounterpartsKeepTheirOwners() {
    // Arrange
    var source = "record Example(int count, String label) { Example(int count, String label) { this.count = count; this.label = label; } "
        + "Example(int count) { this(count, \"default\"); } }";
    var canonicalKey = typeKey("Example") + "#<init>(int,java.lang.String)";
    var overloadKey = typeKey("Example") + "#<init>(int)";

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      var type = typeNode(ast, "Example");
      var constructors = type.children(SyntaxRole.MEMBER);
      assertEquals(List.of(canonicalKey, overloadKey), constructors.stream().map(node -> node.value(SyntaxValue.ExecutableDeclaration.class).declarationKey()).toList());
      assertNotEquals(constructors.getFirst().requiredChild(SyntaxRole.BODY).nodeId(), constructors.getLast().requiredChild(SyntaxRole.BODY).nodeId());
      assertEquals(List.of("count", "label"), constructors.getFirst().children(SyntaxRole.PARAMETER).stream()
          .map(node -> node.value(SyntaxValue.ParameterDeclaration.class).name()).toList());
      assertEquals(List.of(canonicalKey + ":parameter:0", canonicalKey + ":parameter:1"), constructors.getFirst().children(SyntaxRole.PARAMETER).stream()
          .map(node -> node.value(SyntaxValue.ParameterDeclaration.class).declarationKey()).toList());
      var overload = constructors.getLast();
      assertEquals(overloadKey + ":parameter:0", overload.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.ParameterDeclaration.class).declarationKey());
      var delegation = overload.requiredChild(SyntaxRole.BODY).requiredChild(SyntaxRole.STATEMENT).requiredChild(SyntaxRole.EXPRESSION);
      assertEquals(SyntaxKind.INVOCATION, delegation.syntaxKind());
      assertEquals(canonicalKey, delegation.requiredReference().symbolKey().orElseThrow());
      assertSpan(source, delegation, "this(count, \"default\")", source.indexOf("this(count"));
      assertEquals(List.of("count", "\"default\""), delegation.children(SyntaxRole.ARGUMENT).stream().map(node -> written(source, node)).toList());
      assertEquals(overloadKey + ":parameter:0", delegation.children(SyntaxRole.ARGUMENT).getFirst().requiredReference().symbolKey().orElseThrow());
      var components = semanticType(ast, "Example").recordComponents();
      assertEquals(2, components.size());
      for (var index = 0; index < components.size(); index++) {
        var name = index == 0 ? "count" : "label";
        var component = components.get(index);
        assertEquals(typeKey("Example") + "#record-component:" + index, component.componentKey());
        assertEquals(typeKey("Example"), component.ownerTypeKey());
        assertEquals(index, component.ordinal());
        assertEquals(name, component.name());
        assertEquals(Optional.of(canonicalKey + ":parameter:" + index), component.canonicalParameterKey());
        assertEquals(Optional.of(typeKey("Example") + "#field:" + name), component.backingFieldKey());
        assertEquals(Optional.of(typeKey("Example") + "#" + name + "()"), component.accessorKey());
        assertEquals(component.componentKey(), type.children(SyntaxRole.RECORD_COMPONENT).get(index).value(SyntaxValue.RecordComponentDeclaration.class).declarationKey());
      }
      for (var index = 0; index < constructors.size(); index++) {
        var constructor = constructors.get(index);
        var key = index == 0 ? canonicalKey : overloadKey;
        assertEquals(typeKey("Example"), constructor.value(SyntaxValue.ExecutableDeclaration.class).ownerKey());
        assertEquals(constructor.requiredChild(SyntaxRole.BODY), ast.body(key).orElseThrow().root());
      }
    }
  }

  @Test
  void givenExplicitAndImplicitAnnotationElementModifiers_whenProducedAndDecoded_thenWrittenTokensAndEffectiveOwnersStayDistinct() {
    // Arrange
    var source = "@interface Example { String implicit(); public abstract int explicit(); public long publicOnly(); abstract boolean abstractOnly(); }";
    var names = List.of("implicit", "explicit", "publicOnly", "abstractOnly");
    var written = List.of(Set.<JavaDeclarationModifier>of(), Set.of(JavaDeclarationModifier.PUBLIC, JavaDeclarationModifier.ABSTRACT),
        Set.of(JavaDeclarationModifier.PUBLIC), Set.of(JavaDeclarationModifier.ABSTRACT));

    // Act
    var results = produceAndDecode(source);

    // Assert
    for (var ast : results) {
      assertEquals(names, typeNode(ast, "Example").children(SyntaxRole.MEMBER).stream().map(node -> node.value(SyntaxValue.ExecutableDeclaration.class).name()).toList());
      for (var index = 0; index < names.size(); index++) {
        var node = member(ast, "Example", names.get(index));
        var value = node.value(SyntaxValue.ExecutableDeclaration.class);
        assertEquals(SyntaxValue.ExecutableDeclaration.ExecutableKind.ANNOTATION_ELEMENT, value.kind());
        assertEquals(typeKey("Example") + "#" + names.get(index) + "()", value.declarationKey());
        assertEquals(typeKey("Example"), value.ownerKey());
        assertEquals(written.get(index), value.writtenModifiers());
        assertEquals(Set.of(JavaDeclarationModifier.PUBLIC, JavaDeclarationModifier.ABSTRACT), value.effectiveModifiers());
        assertFalse(value.shape().body());
        assertTrue(node.children(SyntaxRole.BODY).isEmpty());
        assertTrue(node.children(SyntaxRole.PARAMETER).isEmpty());
        assertTrue(ast.body(value.declarationKey()).isEmpty());
      }
    }
  }

  private List<AstResult> produceAndDecode(String source, String... warnings) {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var outcome = JavaFrontends.standard().produce(new AstProductionRequest(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(List.of(warnings), outcome.diagnostics().stream().map(value -> value.code()).toList());
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(original.identity(), decoded.identity());
    assertEquals(original.diagnostics(), decoded.diagnostics());
    assertEquals(AstProfile.JAVA_SOURCE_MODULE_SERVICES, decoded.profile());
    return List.of(original, decoded);
  }

  private static String typeKey(String binaryName) {
    return new JavaTypeDeclarationIdentity(Optional.empty(), binaryName).declarationKey();
  }

  private static String platformKey(String binaryName) {
    return new JavaTypeDeclarationIdentity(Optional.of("java.base"), binaryName).declarationKey();
  }

  private static JavaType semanticType(AstResult ast, String binaryName) {
    return ast.program().types().stream().filter(value -> value.typeKey().equals(typeKey(binaryName))).findFirst().orElseThrow();
  }

  private static SyntaxNode typeNode(AstResult ast, String binaryName) {
    return ast.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration value
        && value.declarationKey().equals(typeKey(binaryName))).findFirst().orElseThrow();
  }

  private static SyntaxNode member(AstResult ast, String owner, String name) {
    return typeNode(ast, owner).children(SyntaxRole.MEMBER).stream()
        .filter(node -> node.value() instanceof SyntaxValue.Symbol value && value.name().equals(name)).findFirst().orElseThrow();
  }

  private static void assertTypeModifiers(AstResult ast, String binaryName, Set<JavaTypeModifier> written, Set<JavaTypeModifier> effective) {
    var value = typeNode(ast, binaryName).value(SyntaxValue.TypeDeclaration.class);
    assertEquals(typeKey(binaryName), value.declarationKey());
    assertEquals(written, value.writtenModifiers());
    assertEquals(effective, value.effectiveModifiers());
    assertEquals(effective, semanticType(ast, binaryName).modifiers());
  }

  private static void assertHeaderOperands(AstResult ast, String source, String owner, SyntaxRole role, List<String> spellings, List<String> targets) {
    var type = typeNode(ast, owner);
    var operands = type.children(role);
    assertEquals(spellings.size(), operands.size());
    var expectedKeys = targets.stream().map(DeclarationOwnershipContractTest::typeKey).toList();
    var semantic = semanticType(ast, owner);
    assertEquals(expectedKeys, (role == SyntaxRole.INTERFACE ? semantic.directInterfaces() : semantic.permittedSubclasses()).stream()
        .map(value -> value.declarationKey().orElseThrow()).toList());
    var start = Math.toIntExact(type.origin().orElseThrow().startOffset());
    for (var index = 0; index < operands.size(); index++) {
      var operand = operands.get(index);
      var spelling = spellings.get(index);
      start = source.indexOf(spelling, start);
      assertEquals(type.structuralPath().child(role, index), operand.structuralPath());
      assertSpan(source, operand, spelling, start);
      assertEquals(expectedKeys.get(index), operand.requiredReference().symbolKey().orElseThrow());
      assertEquals(expectedKeys.get(index), assertInstanceOf(JavaTypeStructure.Declared.class,
          assertInstanceOf(SyntaxType.Resolved.class, operand.type()).structure().orElseThrow()).declarationKey());
      start += spelling.length();
    }
  }

  private static void assertArrayType(SyntaxNode node, int dimensions, String component) {
    var structure = assertInstanceOf(SyntaxType.Resolved.class, node.type()).structure().orElseThrow();
    var written = node;
    for (var index = 0; index < dimensions; index++) {
      structure = assertInstanceOf(JavaTypeStructure.Array.class, structure).component();
      assertEquals(JavaSourceKind.ARRAY_TYPE, written.sourceKind());
      written = written.requiredChild(SyntaxRole.COMPONENT_TYPE);
    }
    assertEquals(component, written.type().knownIdentity().orElseThrow().erasedName());
    assertEquals(component + "[]".repeat(dimensions), node.type().knownIdentity().orElseThrow().erasedName());
    if (component.equals("int")) {
      assertInstanceOf(JavaTypeStructure.Primitive.class, structure);
    } else {
      assertEquals(platformKey(component), assertInstanceOf(JavaTypeStructure.Declared.class, structure).declarationKey());
    }
  }

  private static String written(String source, SyntaxNode node) {
    var span = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
  }

  private static void assertSpan(String source, SyntaxNode node, String text, int start) {
    assertTrue(start >= 0, text);
    assertEquals(new SourceSpan(node.sourceFileKey(), start, start + text.length()), node.origin().orElseThrow());
    assertEquals(text, source.substring(start, start + text.length()));
  }
}
