package org.aquilagraph.cfg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.cfg.api.ControlFlowGraph;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.OperationNode;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class SelectedDecisionLoweringTest {
  @TempDir Path sourceRoot;

  @Test
  void givenPrimitiveReturnToReference_whenFrontendAndBuilderRun_thenBoxPrecedesReturnWithSelectedTypeAndAllocationEffect() throws IOException {
    // Arrange
    var source = "class Example { Number value(int input) { return input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.BOX, OperationKind.RETURN));
    var box = operation(graph, OperationKind.BOX);
    assertEquals("java.lang.Number", box.definedValue().orElseThrow().staticType().orElseThrow().erasedName());
    assertEffect(graph, box, "java.lang.OutOfMemoryError");
  }

  @Test
  void givenWrapperReturnToLong_whenFrontendAndBuilderRun_thenUnboxDefinesLongBeforeReturn() throws IOException {
    // Arrange
    var source = "class Example { long value(Integer input) { return input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.UNBOX, OperationKind.RETURN));
    var unbox = operation(graph, OperationKind.UNBOX);
    assertEquals("long", unbox.definedValue().orElseThrow().staticType().orElseThrow().erasedName());
    assertEffect(graph, unbox, "java.lang.NullPointerException");
  }

  @Test
  void givenReferenceReturnToWiderReference_whenFrontendAndBuilderRun_thenOrdinaryUseAddsNoBoxOrUnbox() throws IOException {
    // Arrange
    var source = "class Example { Object value(String input) { return input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.RETURN));
    assertTrue(graph.operationNodes().stream().allMatch(node -> node.possibleExceptions().isEmpty()));
  }

  @Test
  void givenPrimitiveCastFromObject_whenFrontendAndBuilderRun_thenCheckedWrapperCastPrecedesDistinctUnbox() throws IOException {
    // Arrange
    var source = "class Example { int value(Object input) { return (int)input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.CAST, OperationKind.UNBOX, OperationKind.RETURN));
    var cast = operation(graph, OperationKind.CAST);
    var unbox = operation(graph, OperationKind.UNBOX);
    assertEquals("java.lang.Integer", cast.definedValue().orElseThrow().staticType().orElseThrow().erasedName());
    assertEquals(cast.definedValue().orElseThrow(), unbox.operands().getFirst().value());
    assertEffect(graph, cast, "java.lang.ClassCastException");
    assertEffect(graph, unbox, "java.lang.NullPointerException");
  }

  @Test
  void givenNarrowingPrimitiveCastFromWrapper_whenFrontendAndBuilderRun_thenUnboxPrecedesPrimitiveCastWithoutCheckedReferenceEffect() throws IOException {
    // Arrange
    var source = "class Example { byte value(Integer input) { return (byte)(int)input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.UNBOX, OperationKind.CAST, OperationKind.CAST, OperationKind.RETURN));
    assertEquals("int", operation(graph, OperationKind.UNBOX).definedValue().orElseThrow().staticType().orElseThrow().erasedName());
    var casts = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CAST).toList();
    assertEquals(List.of("int", "byte"), casts.stream().map(node -> node.definedValue().orElseThrow().staticType().orElseThrow().erasedName()).toList());
    assertTrue(casts.stream().allMatch(node -> node.possibleExceptions().isEmpty()));
  }

  @Test
  void givenStringPlusPrimitive_whenFrontendAndBuilderRun_thenBothOperandsPrecedeStringConversionAndConcatenation() throws IOException {
    // Arrange
    var source = "class Example { String value(String text, int input) { return text + input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.READ, OperationKind.STRING_CONVERSION, OperationKind.STRING_CONCAT,
        OperationKind.RETURN));
    assertEffect(graph, operation(graph, OperationKind.STRING_CONVERSION), "unchecked-string-conversion");
    assertEffect(graph, operation(graph, OperationKind.STRING_CONCAT), "java.lang.OutOfMemoryError");
    assertFalse(graph.operationNodes().stream().anyMatch(node -> node.kind() == OperationKind.BOX));
  }

  @Test
  void givenStringPlusNull_whenFrontendAndBuilderRun_thenNullConversionHasNoToStringEffect() throws IOException {
    // Arrange
    var source = "class Example { String value(String text) { return text + null; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.CONSTANT, OperationKind.STRING_CONVERSION, OperationKind.STRING_CONCAT,
        OperationKind.RETURN));
    assertTrue(operation(graph, OperationKind.STRING_CONVERSION).possibleExceptions().isEmpty());
    assertEffect(graph, operation(graph, OperationKind.STRING_CONCAT), "java.lang.OutOfMemoryError");
  }

  @Test
  void givenConstantStringPlusPrimitive_whenFrontendAndBuilderRun_thenValueFlowRemainsWithoutLocalRuntimeEffects() throws IOException {
    // Arrange
    var source = "class Example { String value() { return \"text\" + 3; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.CONSTANT, OperationKind.CONSTANT, OperationKind.STRING_CONVERSION, OperationKind.STRING_CONCAT,
        OperationKind.RETURN));
    assertTrue(graph.operationNodes().stream().allMatch(node -> node.possibleExceptions().isEmpty()));
  }

  @Test
  void givenStringPlusString_whenFrontendAndBuilderRun_thenConcatenationNeedsNoSeparateStringConversion() throws IOException {
    // Arrange
    var source = "class Example { String value(String left, String right) { return left + right; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.READ, OperationKind.STRING_CONCAT, OperationKind.RETURN));
    assertEffect(graph, operation(graph, OperationKind.STRING_CONCAT), "java.lang.OutOfMemoryError");
  }

  @Test
  void givenNumericPlusWithWrapper_whenFrontendAndBuilderRun_thenSelectedUnboxingKeepsNumericBinaryKind() throws IOException {
    // Arrange
    var source = "class Example { int value(Integer left, int right) { return left + right; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.UNBOX, OperationKind.READ, OperationKind.BINARY, OperationKind.RETURN));
    assertFalse(graph.operationNodes().stream().anyMatch(node -> node.kind() == OperationKind.STRING_CONCAT));
  }

  @Test
  void givenArrayAssignmentWithBoxedIndexAndValue_whenFrontendAndBuilderRun_thenRawRhsPrecedesTargetCheckAndRhsUnbox() throws IOException {
    // Arrange
    var source = "class Example { void value(int[] array, Integer index, Integer input) { array[index] = input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.READ, OperationKind.UNBOX, OperationKind.READ,
        OperationKind.ARRAY_TARGET_CHECK, OperationKind.UNBOX, OperationKind.WRITE));
    assertEquals(List.of("java.lang.NullPointerException", "java.lang.ArrayIndexOutOfBoundsException"),
        operation(graph, OperationKind.ARRAY_TARGET_CHECK).possibleExceptions());
    assertTrue(operation(graph, OperationKind.WRITE).possibleExceptions().isEmpty());
    var unboxes = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.UNBOX).toList();
    assertFalse(unboxes.getFirst().nodeId().equals(unboxes.getLast().nodeId()));
  }

  @Test
  void givenReferenceArrayAssignmentFromPrimitive_whenFrontendAndBuilderRun_thenTargetCheckPrecedesBoxAndStoreEffect() throws IOException {
    // Arrange
    var source = "class Example { void value(Object[] array, int index, int input) { array[index] = input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.READ, OperationKind.READ, OperationKind.ARRAY_TARGET_CHECK,
        OperationKind.BOX, OperationKind.WRITE));
    assertEffect(graph, operation(graph, OperationKind.BOX), "java.lang.OutOfMemoryError");
    assertEffect(graph, operation(graph, OperationKind.WRITE), "java.lang.ArrayStoreException");
  }

  @Test
  void givenArrayReadWithBoxedIndex_whenFrontendAndBuilderRun_thenIndexUnboxPrecedesArrayReadEffects() throws IOException {
    // Arrange
    var source = "class Example { int value(int[] array, Integer index) { return array[index]; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.READ, OperationKind.UNBOX, OperationKind.ARRAY_ACCESS,
        OperationKind.RETURN));
    assertEquals(List.of("java.lang.NullPointerException", "java.lang.ArrayIndexOutOfBoundsException"),
        operation(graph, OperationKind.ARRAY_ACCESS).possibleExceptions());
  }

  @Test
  void givenVariableArityCall_whenFrontendAndBuilderRun_thenPackingAllocationPrecedesTrailingExpressionsAndOrderedStores() throws IOException {
    // Arrange
    var source = "class Example { java.util.List<String> value() { return java.util.Arrays.asList(\"one\", \"two\"); } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.VARARGS_ARRAY, OperationKind.CONSTANT, OperationKind.WRITE, OperationKind.CONSTANT,
        OperationKind.WRITE, OperationKind.CLASS_INITIALIZATION, OperationKind.INVOKE, OperationKind.RETURN));
    var array = operation(graph, OperationKind.VARARGS_ARRAY).definedValue().orElseThrow();
    assertEquals("java.lang.String[]", array.staticType().orElseThrow().erasedName());
    var stores = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.WRITE).toList();
    assertEquals(List.of("0", "1"), stores.stream().map(node -> node.operands().get(1).value().literal().orElseThrow()).toList());
    assertTrue(stores.stream().allMatch(node -> node.possibleExceptions().isEmpty()));
    assertEquals(array, operation(graph, OperationKind.INVOKE).operands().getFirst().value());
    assertEffect(graph, operation(graph, OperationKind.VARARGS_ARRAY), "java.lang.OutOfMemoryError");
  }

  @Test
  void givenZeroTrailingVariableArityCall_whenFrontendAndBuilderRun_thenEmptyPackingArrayStillPrecedesInvocation() throws IOException {
    // Arrange
    var source = "class Example { java.util.List<String> value() { return java.util.Arrays.<String>asList(); } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.VARARGS_ARRAY, OperationKind.CLASS_INITIALIZATION, OperationKind.INVOKE,
        OperationKind.RETURN));
    assertEquals(1, operation(graph, OperationKind.INVOKE).operands().size());
  }

  @Test
  void givenFixedArrayCallToVarargsDeclaration_whenFrontendAndBuilderRun_thenNoPackingAllocationIsAdded() throws IOException {
    // Arrange
    var source = "class Example { java.util.List<String> value(String[] input) { return java.util.Arrays.asList(input); } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.CLASS_INITIALIZATION, OperationKind.INVOKE, OperationKind.RETURN));
    assertFalse(graph.operationNodes().stream().anyMatch(node -> node.kind() == OperationKind.VARARGS_ARRAY));
  }

  @Test
  void givenCreationWithConstructorArgument_whenFrontendAndBuilderRun_thenInitializationAndSingleAllocationPrecedeArgument() throws IOException {
    // Arrange
    var source = "class Example { Example(int input) {} static Example value(int input) { return new Example(input); } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.CLASS_INITIALIZATION, OperationKind.NEW_ALLOCATION, OperationKind.READ,
        OperationKind.NEW_OBJECT, OperationKind.RETURN));
    var allocation = operation(graph, OperationKind.NEW_ALLOCATION);
    var construction = operation(graph, OperationKind.NEW_OBJECT);
    assertEquals(allocation.definedValue(), construction.definedValue());
    assertEquals(allocation.definedValue().orElseThrow(), construction.operands().getFirst().value());
    assertEffect(graph, allocation, "java.lang.OutOfMemoryError");
    assertEffect(graph, construction, "unchecked-constructor");
    var initialization = operation(graph, OperationKind.CLASS_INITIALIZATION);
    assertEquals(1, initialization.semanticReferences().size());
    assertEquals(ReferenceKind.TYPE, initialization.semanticReferences().getFirst().referenceKind());
    assertEquals("Example", initialization.semanticReferences().getFirst().typeName().orElseThrow());
    assertTrue(initialization.operands().isEmpty());
  }

  @Test
  void givenInheritedStaticFieldRead_whenFrontendAndBuilderRun_thenInitializationNamesSelectedDeclaringClass() throws IOException {
    // Arrange
    var source = "class Parent { static int mutable = 1; } class Example extends Parent { int value() { return Example.mutable; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.CLASS_INITIALIZATION, OperationKind.MEMBER_ACCESS, OperationKind.RETURN));
    assertEquals("Parent", operation(graph, OperationKind.CLASS_INITIALIZATION).semanticReferences().getFirst().typeName().orElseThrow());
    assertEquals(ReferenceKind.FIELD, operation(graph, OperationKind.MEMBER_ACCESS).semanticReferences().getFirst().referenceKind());
  }

  @Test
  void givenConstantStaticFieldRead_whenFrontendAndBuilderRun_thenMemberReadRemainsWithoutInitialization() throws IOException {
    // Arrange
    var source = "class Example { int value() { return Integer.MAX_VALUE; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.MEMBER_ACCESS, OperationKind.RETURN));
    assertTrue(operation(graph, OperationKind.MEMBER_ACCESS).definedValue().isPresent());
  }

  @Test
  void givenStaticWriteRequiringUnboxing_whenFrontendAndBuilderRun_thenRhsConversionPrecedesInitializationAndWrite() throws IOException {
    // Arrange
    var source = "class Example { static int stored; void value(Integer input) { stored = input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.UNBOX, OperationKind.CLASS_INITIALIZATION, OperationKind.WRITE));
    assertEffect(graph, operation(graph, OperationKind.UNBOX), "java.lang.NullPointerException");
    assertEffect(graph, operation(graph, OperationKind.CLASS_INITIALIZATION), "unchecked-class-initialization");
    assertTrue(operation(graph, OperationKind.WRITE).possibleExceptions().isEmpty());
  }

  private ControlFlowGraph build(String source, String methodName) throws IOException {
    return build(source, "", methodName);
  }

  @Test
  void givenQualifiedCreation_whenFrontendAndBuilderRun_thenEnclosingCheckAndAllocationPrecedeEveryArgument() throws IOException {
    // Arrange
    var source = "class Outer { class Inner { Inner(Integer input) {} } } class Example { Outer.Inner value(Outer outer, Integer input) { return outer.new Inner(input); } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.ENCLOSING_INSTANCE_CHECK, OperationKind.CLASS_INITIALIZATION,
        OperationKind.NEW_ALLOCATION, OperationKind.READ, OperationKind.NEW_OBJECT, OperationKind.RETURN));
    assertEffect(graph, operation(graph, OperationKind.ENCLOSING_INSTANCE_CHECK), "java.lang.NullPointerException");
    assertEquals("Outer.Inner", operation(graph, OperationKind.CLASS_INITIALIZATION).semanticReferences().getFirst().typeName().orElseThrow());
    assertEquals(operation(graph, OperationKind.READ).definedValue().orElseThrow(),
        operation(graph, OperationKind.NEW_ALLOCATION).operands().getFirst().value());
  }

  @Test
  void givenQualifiedSuperConstructor_whenFrontendAndBuilderRun_thenEnclosingCheckPrecedesArgumentsWithoutCreationStages() throws IOException {
    // Arrange
    var source = "class Outer { class Base { Base(long input) {} } } class Child extends Outer.Base { Child(Outer outer, Integer input) { outer.super(input); } }";
    // Act
    var graph = build(source, "type:Child", "<init>");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.ENCLOSING_INSTANCE_CHECK, OperationKind.READ, OperationKind.UNBOX,
        OperationKind.INVOKE));
    assertEffect(graph, operation(graph, OperationKind.ENCLOSING_INSTANCE_CHECK), "java.lang.NullPointerException");
    assertEffect(graph, operation(graph, OperationKind.INVOKE), "unchecked-invocation");
    assertEquals(operation(graph, OperationKind.READ).definedValue().orElseThrow(), operation(graph, OperationKind.INVOKE).operands().getFirst().value());
  }

  @Test
  void givenExpressionQualifiedStaticRead_whenFrontendAndBuilderRun_thenQualifierIsEvaluatedAndDiscardedBeforeSelectedOwnerInitialization() throws IOException {
    // Arrange
    var source = "class Parent { static int stored; } class Example extends Parent { Example selected() { return this; } int value() { return selected().stored; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.INVOKE, OperationKind.CLASS_INITIALIZATION, OperationKind.MEMBER_ACCESS, OperationKind.RETURN));
    assertEquals("Parent", operation(graph, OperationKind.CLASS_INITIALIZATION).semanticReferences().getFirst().typeName().orElseThrow());
    assertTrue(operation(graph, OperationKind.MEMBER_ACCESS).operands().isEmpty());
    assertTrue(operation(graph, OperationKind.MEMBER_ACCESS).possibleExceptions().isEmpty());
  }

  @Test
  void givenExpressionQualifiedStaticWrite_whenFrontendAndBuilderRun_thenQualifierAndRhsConversionPrecedeInitializationAndWrite() throws IOException {
    // Arrange
    var source = "class Parent { static int stored; } class Example extends Parent { Example selected() { return this; } void value(Integer input) { selected().stored = input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.INVOKE, OperationKind.READ, OperationKind.UNBOX, OperationKind.CLASS_INITIALIZATION,
        OperationKind.WRITE));
    assertEquals("Parent", operation(graph, OperationKind.CLASS_INITIALIZATION).semanticReferences().getFirst().typeName().orElseThrow());
    assertEquals(1, operation(graph, OperationKind.WRITE).operands().size());
    assertTrue(operation(graph, OperationKind.WRITE).possibleExceptions().isEmpty());
  }

  @Test
  void givenExpressionQualifiedStaticCall_whenFrontendAndBuilderRun_thenQualifierAndArgumentConversionPrecedeDeclaringOwnerInitialization() throws IOException {
    // Arrange
    var source = "class Parent { static long selected(long input) { return input; } } class Example extends Parent { Example qualifier() { return this; } long value(Integer input) { return qualifier().selected(input); } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.INVOKE, OperationKind.READ, OperationKind.UNBOX, OperationKind.CLASS_INITIALIZATION,
        OperationKind.INVOKE, OperationKind.RETURN));
    assertEquals("Parent", operation(graph, OperationKind.CLASS_INITIALIZATION).semanticReferences().getFirst().typeName().orElseThrow());
    var call = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.INVOKE).toList().getLast();
    assertEquals(1, call.operands().size());
    assertEquals("long", call.operands().getFirst().value().staticType().orElseThrow().erasedName());
  }

  @Test
  void givenConstantNarrowingBeforeLocalBoxing_whenFrontendAndBuilderRun_thenByteBoxPrecedesDeclarationWrite() throws IOException {
    // Arrange
    var source = "class Example { Byte value() { Byte local = 127; return local; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.CONSTANT, OperationKind.BOX, OperationKind.WRITE, OperationKind.READ, OperationKind.RETURN));
    assertEquals("java.lang.Byte", operation(graph, OperationKind.BOX).definedValue().orElseThrow().staticType().orElseThrow().erasedName());
    assertEffect(graph, operation(graph, OperationKind.BOX), "java.lang.OutOfMemoryError");
  }

  @Test
  void givenBoxedShortCircuitOperands_whenFrontendAndBuilderRun_thenRightUnboxingIsReachableOnlyThroughSelectedBranch() throws IOException {
    // Arrange
    var source = "class Example { boolean value(Boolean left, Boolean right) { return left && right; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    var reads = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.READ).toList();
    var decision = operation(graph, OperationKind.DECISION);
    var constant = operation(graph, OperationKind.CONSTANT);
    assertEquals(2, graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.UNBOX).count());
    assertTransfer(graph, decision, reads.getLast(), TransferKind.TRUE_BRANCH);
    assertTransfer(graph, decision, constant, TransferKind.FALSE_BRANCH);
    assertEquals("false", constant.definedValue().orElseThrow().literal().orElseThrow());
    assertTransfer(graph, constant, operation(graph, OperationKind.MERGE), TransferKind.NORMAL);
  }

  @Test
  void givenBoxedConditionalOrOperands_whenFrontendAndBuilderRun_thenTrueBypassesRightAndFalseEvaluatesAndUnboxesRight() throws IOException {
    // Arrange
    var source = "class Example { boolean value(Boolean left, Boolean right) { return left || right; } }";

    // Act
    var graph = build(source, "value");

    // Assert
    var reads = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.READ).toList();
    var unboxes = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.UNBOX).toList();
    var decision = operation(graph, OperationKind.DECISION);
    var constant = operation(graph, OperationKind.CONSTANT);
    var merge = operation(graph, OperationKind.MERGE);
    assertEquals(2, reads.size());
    assertEquals(2, unboxes.size());
    assertTransfer(graph, reads.getFirst(), unboxes.getFirst(), TransferKind.NORMAL);
    assertTransfer(graph, unboxes.getFirst(), decision, TransferKind.NORMAL);
    assertEquals(unboxes.getFirst().definedValue().orElseThrow(), decision.operands().getFirst().value());
    assertEquals(2, graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(decision.nodeId())).count());
    assertTransfer(graph, decision, constant, TransferKind.TRUE_BRANCH);
    assertTransfer(graph, decision, reads.getLast(), TransferKind.FALSE_BRANCH);
    assertEquals("true", constant.definedValue().orElseThrow().literal().orElseThrow());
    assertTransfer(graph, reads.getLast(), unboxes.getLast(), TransferKind.NORMAL);
    assertTransfer(graph, unboxes.getLast(), merge, TransferKind.NORMAL);
    assertTransfer(graph, constant, merge, TransferKind.NORMAL);
    assertEquals(List.of(constant.definedValue().orElseThrow(), unboxes.getLast().definedValue().orElseThrow()),
        merge.operands().stream().map(operand -> operand.value()).toList());
    assertTransfer(graph, merge, operation(graph, OperationKind.RETURN), TransferKind.NORMAL);
    assertEquals(merge.definedValue().orElseThrow(), operation(graph, OperationKind.RETURN).operands().getFirst().value());
    for (var unbox : unboxes) {
      assertEffect(graph, unbox, "java.lang.NullPointerException");
    }
  }

  @Test
  void givenConditionalPrimitiveArmsInReferenceContext_whenFrontendAndBuilderRun_thenOnlyReachedArmFeedsMergeAndBoxFollowsNumericResult() throws IOException {
    // Arrange
    var source = "class Example { Object value(Boolean condition, int left, long right) { return condition ? left : right; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    var reads = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.READ).toList();
    var decision = operation(graph, OperationKind.DECISION);
    assertTransfer(graph, decision, reads.get(1), TransferKind.TRUE_BRANCH);
    assertTransfer(graph, decision, reads.get(2), TransferKind.FALSE_BRANCH);
    assertTransfer(graph, operation(graph, OperationKind.MERGE), operation(graph, OperationKind.BOX), TransferKind.NORMAL);
    assertEquals("java.lang.Object", operation(graph, OperationKind.BOX).definedValue().orElseThrow().staticType().orElseThrow().erasedName());
    assertEquals("long", operation(graph, OperationKind.MERGE).definedValue().orElseThrow().staticType().orElseThrow().erasedName());
  }

  @Test
  void givenBoxedUnaryOperand_whenFrontendAndBuilderRun_thenUnboxPrecedesUnaryAndItsValueFeedsTheOperation() throws IOException {
    // Arrange
    var source = "class Example { int value(Integer input) { return -input; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.UNBOX, OperationKind.UNARY, OperationKind.RETURN));
    var unbox = operation(graph, OperationKind.UNBOX);
    assertEffect(graph, unbox, "java.lang.NullPointerException");
    assertEquals(unbox.definedValue().orElseThrow(), operation(graph, OperationKind.UNARY).operands().getFirst().value());
    assertTrue(operation(graph, OperationKind.UNARY).possibleExceptions().isEmpty());
  }

  @Test
  void givenBoxedIfCondition_whenFrontendAndBuilderRun_thenUnboxPrecedesDecisionAndOnlySelectedBranchReachesItsReturn() throws IOException {
    // Arrange
    var source = "class Example { int value(Boolean condition) { if (condition) return 1; else return 2; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    var unbox = operation(graph, OperationKind.UNBOX);
    var decision = operation(graph, OperationKind.DECISION);
    var constants = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CONSTANT).toList();
    var returns = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.RETURN).toList();
    assertEquals(2, returns.size());
    assertEffect(graph, unbox, "java.lang.NullPointerException");
    assertTransfer(graph, unbox, decision, TransferKind.NORMAL);
    assertEquals(unbox.definedValue().orElseThrow(), decision.operands().getFirst().value());
    assertTransfer(graph, decision, constants.getFirst(), TransferKind.TRUE_BRANCH);
    assertTransfer(graph, decision, constants.getLast(), TransferKind.FALSE_BRANCH);
    for (var index = 0; index < returns.size(); index++) {
      var returned = returns.get(index);
      assertTransfer(graph, constants.get(index), returned, TransferKind.NORMAL);
      assertEquals(constants.get(index).definedValue().orElseThrow(), returned.operands().getFirst().value());
      assertTrue(graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(returned.nodeId()))
          .allMatch(edge -> edge.targetNodeId().equals(graph.exit().nodeId())));
    }
  }

  @Test
  void givenBoxedSwitchSelectorAndReferenceResult_whenFrontendAndBuilderRun_thenArrowAndExplicitYieldBoxBeforeTheirSharedValueMerge() throws IOException {
    // Arrange
    var source = "class Example { Object value(Integer selector) { return switch (selector) { case 0 -> 1; default -> { yield 2; } }; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    var selector = operation(graph, OperationKind.SWITCH_SELECTOR);
    var unbox = operation(graph, OperationKind.UNBOX);
    var boxes = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.BOX).toList();
    var merge = operation(graph, OperationKind.MERGE);
    assertEquals(2, boxes.size());
    assertTransfer(graph, unbox, selector, TransferKind.NORMAL);
    assertEquals(unbox.definedValue().orElseThrow(), selector.operands().getFirst().value());
    assertEffect(graph, unbox, "java.lang.NullPointerException");
    assertEquals(boxes.stream().map(node -> node.definedValue().orElseThrow()).toList(),
        merge.operands().stream().map(operand -> operand.value()).toList());
    for (var box : boxes) {
      assertEquals("java.lang.Object", box.definedValue().orElseThrow().staticType().orElseThrow().erasedName());
      assertEffect(graph, box, "java.lang.OutOfMemoryError");
      assertTransfer(graph, box, merge, TransferKind.NORMAL);
    }
    assertTransfer(graph, merge, operation(graph, OperationKind.RETURN), TransferKind.NORMAL);
    assertEquals(merge.definedValue().orElseThrow(), operation(graph, OperationKind.RETURN).operands().getFirst().value());
  }

  @Test
  void givenStringPlusPrimitiveOnLeft_whenFrontendAndBuilderRun_thenRightInvocationCompletesBeforeLeftStringConversion() throws IOException {
    // Arrange
    var source = "class Example { String text() { return \"text\"; } String value(int input) { return input + text(); } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.READ, OperationKind.INVOKE, OperationKind.STRING_CONVERSION,
        OperationKind.STRING_CONCAT, OperationKind.RETURN));
    var conversion = operation(graph, OperationKind.STRING_CONVERSION);
    var concatenation = operation(graph, OperationKind.STRING_CONCAT);
    assertEquals(operation(graph, OperationKind.READ).definedValue().orElseThrow(), conversion.operands().getFirst().value());
    assertEquals(conversion.definedValue().orElseThrow(), concatenation.operands().getFirst().value());
    assertEquals(operation(graph, OperationKind.INVOKE).definedValue().orElseThrow(), concatenation.operands().getLast().value());
    assertEffect(graph, conversion, "unchecked-string-conversion");
    assertEffect(graph, concatenation, "java.lang.OutOfMemoryError");
  }

  @Test
  void givenConstantVariableStringPlus_whenFrontendAndBuilderRun_thenBothSourceReadsRemainWithoutInitializationOrRuntimeConcatenationEffects() throws IOException {
    // Arrange
    var source = "class Example { static final String PREFIX = \"x\"; static final int SUFFIX = 7; String value() { return PREFIX + SUFFIX; } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.MEMBER_ACCESS, OperationKind.MEMBER_ACCESS,
        OperationKind.STRING_CONVERSION, OperationKind.STRING_CONCAT, OperationKind.RETURN));
    assertTrue(graph.operationNodes().stream().allMatch(node -> node.possibleExceptions().isEmpty()));
    var reads = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.MEMBER_ACCESS).toList();
    assertEquals(2, reads.size());
    assertTrue(reads.stream().allMatch(node -> node.semanticReferences().getFirst().referenceKind() == ReferenceKind.FIELD));
    assertEquals(reads.getLast().definedValue().orElseThrow(),
        operation(graph, OperationKind.STRING_CONVERSION).operands().getFirst().value());
  }

  @Test
  void givenVariableArityCreation_whenFrontendAndBuilderRun_thenObjectAllocationPrecedesFixedConversionAndPackingPrecedesTrailingConversion() throws IOException {
    // Arrange
    var source = "class Example { Example(Object fixed, int... rest) { } Example value(int fixed, Integer tail) { return new Example(fixed, tail); } }";
    // Act
    var graph = build(source, "value");
    // Assert
    assertLinear(graph, List.of(OperationKind.CLASS_INITIALIZATION, OperationKind.NEW_ALLOCATION,
        OperationKind.READ, OperationKind.BOX, OperationKind.VARARGS_ARRAY, OperationKind.READ,
        OperationKind.UNBOX, OperationKind.WRITE, OperationKind.NEW_OBJECT, OperationKind.RETURN));
    var allocation = operation(graph, OperationKind.NEW_ALLOCATION);
    var packing = operation(graph, OperationKind.VARARGS_ARRAY);
    var creation = operation(graph, OperationKind.NEW_OBJECT);
    var write = operation(graph, OperationKind.WRITE);
    assertEquals(allocation.definedValue().orElseThrow(), creation.definedValue().orElseThrow());
    assertEquals(List.of(allocation.definedValue().orElseThrow(), operation(graph, OperationKind.BOX).definedValue().orElseThrow(),
        packing.definedValue().orElseThrow()), creation.operands().stream().map(operand -> operand.value()).toList());
    assertEquals(packing.definedValue().orElseThrow(), write.operands().getFirst().value());
    assertEquals(operation(graph, OperationKind.UNBOX).definedValue().orElseThrow(), write.operands().getLast().value());
    assertTrue(write.possibleExceptions().isEmpty());
    assertEffect(graph, allocation, "java.lang.OutOfMemoryError");
    assertEffect(graph, packing, "java.lang.OutOfMemoryError");
    assertEffect(graph, creation, "unchecked-constructor");
  }

  private ControlFlowGraph build(String source, String ownerTypeKey, String methodName) throws IOException {
    var snapshot = InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var method = input.program().methods().stream().filter(candidate -> candidate.name().equals(methodName)
        && (ownerTypeKey.isEmpty() || candidate.ownerTypeKey().equals(ownerTypeKey))).findFirst().orElseThrow();
    var syntax = input.methodSyntax(method.methodKey()).orElseThrow();
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(syntax));
    var result = new ControlFlowGraphBuilder().build(method, syntax);
    assertTrue(result.diagnostics().isEmpty());
    return result.graph().orElseThrow();
  }

  private static OperationNode operation(ControlFlowGraph graph, OperationKind kind) {
    return graph.operationNodes().stream().filter(node -> node.kind() == kind).findFirst().orElseThrow();
  }

  private static void assertLinear(ControlFlowGraph graph, List<OperationKind> expectedKinds) {
    var nodes = graph.operationNodes();
    assertEquals(expectedKinds, nodes.stream().map(OperationNode::kind).toList());
    for (var position = 1; position < nodes.size(); position++) {
      var previous = nodes.get(position - 1).nodeId();
      var next = nodes.get(position).nodeId();
      assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(previous) && edge.targetNodeId().equals(next)
          && edge.transferKind() == TransferKind.NORMAL), previous + " must normally precede " + next);
    }
  }

  private static void assertEffect(ControlFlowGraph graph, OperationNode operation, String exceptionCategory) {
    assertEquals(List.of(exceptionCategory), operation.possibleExceptions());
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(operation.nodeId())
        && edge.transferKind() == TransferKind.EXCEPTIONAL && edge.discriminator().orElseThrow().equals(exceptionCategory)));
  }

  private static void assertTransfer(ControlFlowGraph graph, OperationNode source, OperationNode target, TransferKind kind) {
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(source.nodeId())
        && edge.targetNodeId().equals(target.nodeId()) && edge.transferKind() == kind));
  }
}
