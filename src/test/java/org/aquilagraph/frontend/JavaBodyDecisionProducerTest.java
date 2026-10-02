package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.syntax.InvocationArityMode;
import org.aquilagraph.syntax.MethodBodyOrigin;
import org.aquilagraph.syntax.MethodSyntax;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxReadiness;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaBodyDecisionProducerTest {
  @TempDir
  Path sourceRoot;

  @Test
  void givenExternalSelectedFormals_whenFrontendProducesDecisions_thenBoxingUnboxingAndExplicitNoConversionRemainDistinct() throws IOException {
    // Arrange
    var source = """
        class Example {
          static long unbox(Integer value) { return Math.max(value, 2L); }
          static java.util.List<Object> box(int value) { return java.util.Collections.<Object>singletonList(value); }
          static String reference(String value) { return String.valueOf(value); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    var unbox = body(input, "unbox");
    var unboxCall = call(unbox, "max");
    var unboxUse = unbox.useDecision(unboxCall.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
    assertEquals("java.lang.Integer", unboxUse.sourceType().erasedName());
    assertEquals("long", unboxUse.selectedTarget().erasedName());
    assertEquals("int", unboxUse.unboxedType().orElseThrow().erasedName());
    assertEquals("long", unboxUse.effectivePrimitiveType().orElseThrow().erasedName());
    assertTrue(unboxUse.boxedType().isEmpty());
    var box = body(input, "box");
    var boxUse = box.useDecision(call(box, "singletonList").nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
    assertEquals("java.lang.Object", boxUse.selectedTarget().erasedName());
    assertEquals("int", boxUse.preBoxPrimitiveType().orElseThrow().erasedName());
    assertEquals("java.lang.Integer", boxUse.boxedType().orElseThrow().erasedName());
    var reference = body(input, "reference");
    var identity = reference.useDecision(call(reference, "valueOf").nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
    assertEquals("java.lang.String", identity.sourceType().erasedName());
    assertEquals("java.lang.Object", identity.selectedTarget().erasedName());
    assertTrue(identity.checkedReferenceTarget().isEmpty() && identity.unboxedType().isEmpty()
        && identity.effectivePrimitiveType().isEmpty() && identity.preBoxPrimitiveType().isEmpty() && identity.boxedType().isEmpty());
    assertEquals(SyntaxReadiness.LOWERABLE, unbox.readiness());
    assertEquals(SyntaxReadiness.LOWERABLE, box.readiness());
    assertEquals(SyntaxReadiness.LOWERABLE, reference.readiness());
  }

  @Test
  void givenInstantiatedCallsAndNongenericConstructor_whenFrontendProducesDecisions_thenExactArrayAndElementTargetsAreRegistered() throws IOException {
    // Arrange
    var source = """
        class Holder<T> { Holder(T value) {} }
        class Example {
          static java.util.List<String> fixed(String[] values) { return java.util.Arrays.asList(values); }
          static java.util.List<String> zero() { return java.util.Arrays.<String>asList(); }
          static java.util.List<String> variable() { return java.util.Arrays.asList("one", "two"); }
          static Holder<String> create() { return new Holder<String>("text"); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    var fixed = body(input, "fixed");
    var fixedCall = call(fixed, "asList");
    assertEquals(InvocationArityMode.FIXED, fixed.invocationArity(fixedCall.nodeId()).orElseThrow().mode());
    assertEquals("java.lang.String[]", fixed.useDecision(fixedCall.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
    var zero = body(input, "zero");
    var zeroDecision = zero.invocationArity(call(zero, "asList").nodeId()).orElseThrow();
    assertEquals(InvocationArityMode.VARIABLE, zeroDecision.mode());
    assertEquals("java.lang.String[]", zeroDecision.varargArrayType().orElseThrow().displayName());
    assertEquals("java.lang.String[]", zeroDecision.erasedArrayType().orElseThrow().displayName());
    assertEquals("java.lang.String", zeroDecision.elementType().orElseThrow().displayName());
    var variable = body(input, "variable");
    var variableCall = call(variable, "asList");
    assertEquals(InvocationArityMode.VARIABLE, variable.invocationArity(variableCall.nodeId()).orElseThrow().mode());
    for (var position = 0; position < 2; position++) {
      assertEquals("java.lang.String", variable.useDecision(variableCall.nodeId(), SyntaxRole.ARGUMENT, position).orElseThrow().selectedTarget().displayName());
    }
    var create = body(input, "create");
    var creation = nodes(create, SyntaxKind.OBJECT_CREATION).getFirst();
    assertEquals(InvocationArityMode.FIXED, create.invocationArity(creation.nodeId()).orElseThrow().mode());
    assertEquals("java.lang.String", create.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
    assertEquals(SyntaxReadiness.LOWERABLE, create.readiness());
  }

  @Test
  void givenInheritedAndExternalStaticMembers_whenFrontendProducesDecisions_thenDeclaringOwnersAndConstantReadsComeFromSelectedMembers() throws IOException {
    // Arrange
    var source = """
        class Parent {
          static int mutable = 1;
          static final int CONSTANT = 3;
          static int selected() { return 4; }
        }
        class Example extends Parent {
          static int read() { return Example.mutable + Example.CONSTANT + Integer.MAX_VALUE; }
          static void write(int value) { Example.mutable = value; }
          static int invoke() { return Example.selected(); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    var read = body(input, "read");
    var fields = nodes(read, SyntaxKind.FIELD_ACCESS);
    var mutable = fields.stream().filter(node -> node.value(SyntaxValue.FieldAccess.class).name().equals("mutable")).findFirst().orElseThrow();
    var inheritedConstant = fields.stream().filter(node -> node.value(SyntaxValue.FieldAccess.class).name().equals("CONSTANT")).findFirst().orElseThrow();
    var external = fields.stream().filter(node -> node.value(SyntaxValue.FieldAccess.class).name().equals("MAX_VALUE")).findFirst().orElseThrow();
    assertEquals("Parent", read.staticAccess(mutable.nodeId()).orElseThrow().declaringType().identity().erasedName());
    assertEquals(Optional.of("type:Parent"), read.staticAccess(mutable.nodeId()).orElseThrow().declaringType().declarationKey());
    assertTrue(read.staticAccess(mutable.nodeId()).orElseThrow().triggersInitialization());
    assertFalse(read.staticAccess(inheritedConstant.nodeId()).orElseThrow().triggersInitialization());
    assertEquals("java.lang.Integer", read.staticAccess(external.nodeId()).orElseThrow().declaringType().identity().erasedName());
    assertFalse(read.staticAccess(external.nodeId()).orElseThrow().triggersInitialization());
    var write = body(input, "write");
    assertTrue(write.staticAccess(nodes(write, SyntaxKind.FIELD_LOCATION).getFirst().nodeId()).orElseThrow().triggersInitialization());
    var invoke = body(input, "invoke");
    assertEquals("Parent", invoke.staticAccess(call(invoke, "selected").nodeId()).orElseThrow().declaringType().identity().erasedName());
    assertEquals(SyntaxReadiness.LOWERABLE, read.readiness());
  }

  @Test
  void givenNestedConstantsAndDynamicStringPlus_whenFrontendProducesDecisions_thenWholeExpressionConstantFactsAndOrdinaryUsesAreSeparate() throws IOException {
    // Arrange
    var source = """
        class Example {
          static final int SMALL = 1 + 2;
          static String constant() { return "prefix" + (SMALL * 2) + ":" + (true ? (1 << 2) : 5); }
          static String dynamic(int value) { return "prefix" + value; }
          static int numeric(int value) { return 1 + value; }
          static <T extends String> String bound(T value) { return "prefix" + value; }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    var constant = body(input, "constant");
    var constantDecisions = nodes(constant, SyntaxKind.BINARY).stream().flatMap(node -> constant.stringConcatenation(node.nodeId()).stream()).toList();
    assertEquals(3, constantDecisions.size());
    assertTrue(constantDecisions.stream().allMatch(decision -> decision.compileTimeConstant()));
    var dynamic = body(input, "dynamic");
    var plus = nodes(dynamic, SyntaxKind.BINARY).getFirst();
    var decision = dynamic.stringConcatenation(plus.nodeId()).orElseThrow();
    assertFalse(decision.compileTimeConstant());
    assertEquals(Optional.of(SyntaxRole.RIGHT_OPERAND), decision.convertedSide());
    var numericUse = dynamic.useDecision(plus.nodeId(), SyntaxRole.RIGHT_OPERAND, 0).orElseThrow();
    assertEquals("int", numericUse.selectedTarget().erasedName());
    assertTrue(numericUse.boxedType().isEmpty() && numericUse.unboxedType().isEmpty());
    var numeric = body(input, "numeric");
    assertTrue(numeric.stringConcatenation(nodes(numeric, SyntaxKind.BINARY).getFirst().nodeId()).isEmpty());
    var bound = body(input, "bound");
    var boundPlus = nodes(bound, SyntaxKind.BINARY).getFirst();
    assertEquals(Optional.of(SyntaxRole.RIGHT_OPERAND), bound.stringConcatenation(boundPlus.nodeId()).orElseThrow().convertedSide());
    var boundUse = bound.useDecision(boundPlus.nodeId(), SyntaxRole.RIGHT_OPERAND, 0).orElseThrow();
    assertEquals(JavaTypeCategory.TYPE_VARIABLE, boundUse.sourceType().category());
    assertEquals(JavaTypeCategory.TYPE_VARIABLE, boundUse.selectedTarget().category());
    assertEquals("java.lang.String", boundUse.sourceType().erasedName());
    assertTrue(boundUse.boxedType().isEmpty() && boundUse.unboxedType().isEmpty());
    assertEquals(SyntaxReadiness.LOWERABLE, bound.readiness());
    assertEquals(SyntaxReadiness.LOWERABLE, constant.readiness());
    assertEquals(SyntaxReadiness.LOWERABLE, dynamic.readiness());
  }

  @Test
  void givenNarrowBoxingAndPrimitiveCasts_whenFrontendProducesDecisions_thenSelectedStepsIncludeConstantAndCheckedWrapperProof() throws IOException {
    // Arrange
    var source = """
        class Example {
          static final int SMALL = 1 + 2;
          static Byte narrow() { return SMALL + 2; }
          static int checked(Object value) { return (int) value; }
          static int wrapper(Integer value) { return (int) value; }
          static byte narrowCast(Integer value) { return (byte) (int) value; }
          static char charCast(Integer value) { return (char) (int) value; }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    var narrow = body(input, "narrow");
    var narrowUse = narrow.useDecision(nodes(narrow, SyntaxKind.RETURN).getFirst().nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow();
    assertEquals("int", narrowUse.sourceType().erasedName());
    assertEquals("byte", narrowUse.preBoxPrimitiveType().orElseThrow().erasedName());
    assertEquals("java.lang.Byte", narrowUse.boxedType().orElseThrow().erasedName());
    var checked = body(input, "checked");
    var checkedUse = checked.useDecision(nodes(checked, SyntaxKind.TYPE_CAST).getFirst().nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow();
    assertEquals("java.lang.Integer", checkedUse.checkedReferenceTarget().orElseThrow().erasedName());
    assertEquals("int", checkedUse.unboxedType().orElseThrow().erasedName());
    var wrapper = body(input, "wrapper");
    var wrapperUse = wrapper.useDecision(nodes(wrapper, SyntaxKind.TYPE_CAST).getFirst().nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow();
    assertTrue(wrapperUse.checkedReferenceTarget().isEmpty());
    assertEquals("int", wrapperUse.unboxedType().orElseThrow().erasedName());
    for (var name : List.of("narrowCast", "charCast")) {
      var castBody = body(input, name);
      var casts = nodes(castBody, SyntaxKind.TYPE_CAST);
      var inner = casts.stream().filter(node -> node.resolvedType().orElseThrow().erasedName().equals("int")).findFirst().orElseThrow();
      var castUse = castBody.useDecision(inner.nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow();
      assertEquals("java.lang.Integer", castUse.sourceType().erasedName());
      assertEquals("int", castUse.selectedTarget().erasedName());
      assertEquals("int", castUse.unboxedType().orElseThrow().erasedName());
      assertEquals("int", castUse.effectivePrimitiveType().orElseThrow().erasedName());
      assertTrue(castUse.checkedReferenceTarget().isEmpty());
      var outerUse = castBody.useDecision(casts.getFirst().nodeId(), SyntaxRole.EXPRESSION, 0).orElseThrow();
      assertEquals(name.equals("narrowCast") ? "byte" : "char", outerUse.selectedTarget().erasedName());
      assertEquals("int", outerUse.sourceType().erasedName());
      assertTrue(outerUse.unboxedType().isEmpty() && outerUse.checkedReferenceTarget().isEmpty());
      assertEquals(SyntaxReadiness.LOWERABLE, castBody.readiness());
    }
    assertEquals(SyntaxReadiness.LOWERABLE, narrow.readiness());
  }

  @Test
  void givenNestedSwitchResultContexts_whenFrontendProducesDecisions_thenEachResultUseBindsItsOwnAttributedSwitchTarget() throws IOException {
    // Arrange
    var source = """
        class Example {
          static long result(int tag, Integer boxed) {
            return switch (tag) {
              case 0 -> boxed;
              default -> { yield (int) switch (tag) { case 1 -> boxed; default -> 1; }; }
            };
          }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    var result = body(input, "result");
    var boxedResults = nodes(result, SyntaxKind.CASE).stream()
        .filter(node -> node.children(SyntaxRole.BODY).stream().anyMatch(child -> child.syntaxKind() == SyntaxKind.VARIABLE_ACCESS))
        .map(node -> result.useDecision(node.nodeId(), SyntaxRole.BODY, 0).orElseThrow()).toList();
    assertEquals(List.of("long", "int"), boxedResults.stream().map(decision -> decision.selectedTarget().erasedName()).toList());
    assertEquals(List.of("int", "int"), boxedResults.stream().map(decision -> decision.unboxedType().orElseThrow().erasedName()).toList());
    assertEquals("long", result.useDecision(nodes(result, SyntaxKind.YIELD).getFirst().nodeId(), SyntaxRole.VALUE, 0).orElseThrow().selectedTarget().erasedName());
    assertEquals(SyntaxReadiness.LOWERABLE, result.readiness());
  }

  @Test
  void givenDetachedConstructorSourceProjection_whenFrontendProducesDecisions_thenRegisteredExecutionBodyKeepsItsOwnCallIdentities() throws IOException {
    // Arrange
    var source = """
        class Example {
          Example(Integer value) { consume(value); }
          static void consume(long value) {}
          void written(Integer value) { consume(value); }
        }
        class Auxiliary {}
        """;

    // Act
    var input = analyze(source);

    // Assert
    var aggregate = input.aggregate().orElseThrow();
    var constructor = input.methodSyntax().stream().filter(method -> method.methodKey().equals("type:Example#<init>(java.lang.Integer)")).findFirst().orElseThrow();
    var constructorCall = call(constructor, "consume");
    assertEquals(MethodBodyOrigin.EXECUTION_PROJECTION, aggregate.methodBodyOrigin(constructor.methodKey()).orElseThrow());
    assertTrue(aggregate.node(constructorCall.nodeId()).isEmpty());
    assertEquals("long", constructor.useDecision(constructorCall.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
    var written = body(input, "written");
    var writtenCall = call(written, "consume");
    assertEquals(MethodBodyOrigin.SOURCE_AST, aggregate.methodBodyOrigin(written.methodKey()).orElseThrow());
    assertTrue(aggregate.node(writtenCall.nodeId()).isPresent());
    assertEquals("long", written.useDecision(writtenCall.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().erasedName());
    var auxiliary = input.methodSyntax().stream().filter(method -> method.methodKey().equals("type:Auxiliary#<init>()")).findFirst().orElseThrow();
    assertEquals(SyntaxReadiness.LOWERABLE, auxiliary.readiness());
    assertEquals(InvocationArityMode.FIXED, auxiliary.invocationArity(nodes(auxiliary, SyntaxKind.INVOCATION).getFirst().nodeId()).orElseThrow().mode());
  }

  @Test
  void givenGenericConstructorSelection_whenFrontendProducesDecisions_thenSelectedArityAndTypedArgumentAreComplete() throws IOException {
    // Arrange
    var source = """
        class Example {
          <T> Example(T value) {}
          static Example create() { return new Example("text"); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    var create = body(input, "create");
    var creation = nodes(create, SyntaxKind.OBJECT_CREATION).getFirst();
    var arity = create.invocationArity(creation.nodeId()).orElseThrow();
    assertEquals(InvocationArityMode.FIXED, arity.mode());
    assertEquals(1, arity.formalCount());
    assertEquals(create.methodKey(), arity.methodKey());
    assertEquals(creation.nodeId(), arity.consumerNodeId());
    assertEquals(Optional.of("type:Example#<init>(java.lang.Object)"), creation.requiredReference().symbolKey());
    var use = create.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
    assertEquals("java.lang.String", use.sourceType().displayName());
    assertEquals(use.sourceType(), creation.requiredChild(SyntaxRole.ARGUMENT).resolvedType().orElseThrow());
    assertEquals("java.lang.String", use.selectedTarget().displayName());
    assertTrue(use.checkedReferenceTarget().isEmpty());
    assertTrue(use.unboxedType().isEmpty());
    assertTrue(use.effectivePrimitiveType().isEmpty());
    assertTrue(use.preBoxPrimitiveType().isEmpty());
    assertTrue(use.boxedType().isEmpty());
    assertTrue(use.referenceCheckRequired().isEmpty());
    assertEquals(SyntaxReadiness.LOWERABLE, create.readiness());
    assertTrue(input.diagnostics().stream().noneMatch(diagnostic -> diagnostic.isError()), input.diagnostics().toString());
    assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(input).completion());
  }

  private SyntaxAnalysisInput analyze(String content) throws IOException {
    var source = SourceFile.fromContent(Path.of("Example.java"), content);
    var snapshot = InputSnapshot.fromSources(sourceRoot, List.of(source));
    return new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
  }

  private static MethodSyntax body(SyntaxAnalysisInput input, String name) {
    var method = input.program().types().stream().flatMap(type -> type.methods().stream()).filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    return input.methodSyntax().stream().filter(value -> value.methodKey().equals(method.methodKey())).findFirst().orElseThrow();
  }

  private static SyntaxNode call(MethodSyntax body, String name) {
    return nodes(body, SyntaxKind.INVOCATION).stream().filter(node -> node.value(SyntaxValue.Invocation.class).name().equals(name)).findFirst().orElseThrow();
  }

  private static List<SyntaxNode> nodes(MethodSyntax body, SyntaxKind kind) {
    return body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == kind).toList();
  }
}
