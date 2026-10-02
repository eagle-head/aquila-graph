package org.aquilagraph.repository.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.TransferKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jSelectedGraphProjectionTest {
  private static final Set<String> TRANSFER_TYPES = Set.of("NORMAL_FLOW", "TRUE_FLOW", "FALSE_FLOW", "SWITCH_FLOW", "RETURN_FLOW",
      "EXCEPTIONAL_FLOW");

  @TempDir Path sourceRoot;

  @Test
  void givenNullableExplicitThrow_whenCompletedRunIsMapped_thenNonNullCategoryPrecedesNullInGraphAndStoredProperty() throws IOException {
    // Arrange
    var produced = analyze("class Example { void execute(Exception failure) throws Exception { throw failure; } }", "type:Example", "execute");
    var thrown = operation(produced.graph(), OperationKind.THROW);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertEquals(List.of("java.lang.Exception", "java.lang.NullPointerException"), thrown.possibleExceptions());
    assertOperation(produced, projection, thrown, "THROW", "throw", List.of("java.lang.Exception", "java.lang.NullPointerException"));
    assertEquals(List.of("java.lang.Exception", "java.lang.NullPointerException"), produced.graph().edges().stream()
        .filter(edge -> edge.sourceNodeId().equals(thrown.nodeId()) && edge.transferKind() == TransferKind.EXCEPTIONAL)
        .map(edge -> edge.discriminator().orElseThrow()).toList());
    assertExceptional(produced, projection, thrown, "java.lang.Exception");
    assertExceptional(produced, projection, thrown, "java.lang.NullPointerException");
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenPrimitiveReturnedAsNumber_whenCompletedRunIsMapped_thenBoxResultTypeAndExceptionTransferArePreserved() throws IOException {
    // Arrange
    var produced = analyze("class Example { Number value(int input) { return input; } }", "type:Example", "value");
    var box = operation(produced.graph(), OperationKind.BOX);
    var read = operation(produced.graph(), OperationKind.READ);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, box, "BOX", "BOX", List.of("java.lang.OutOfMemoryError"));
    assertTemporary(produced, projection, box, "BOX", "DECLARED", "java.lang.Number", "java.lang.Number");
    assertOperands(box, List.of(defined(read)));
    assertExceptional(produced, projection, box, "java.lang.OutOfMemoryError");
    assertNoReferences(projection, box);
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenIntegerReturnedAsLong_whenCompletedRunIsMapped_thenUnboxCarriesSelectedPrimitiveAndNullFailure() throws IOException {
    // Arrange
    var produced = analyze("class Example { long value(Integer input) { return input; } }", "type:Example", "value");
    var unbox = operation(produced.graph(), OperationKind.UNBOX);
    var read = operation(produced.graph(), OperationKind.READ);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, unbox, "UNBOX", "UNBOX", List.of("java.lang.NullPointerException"));
    assertTemporary(produced, projection, unbox, "UNBOX", "LONG", "long", "long");
    assertOperands(unbox, List.of(defined(read)));
    assertExceptional(produced, projection, unbox, "java.lang.NullPointerException");
    assertNoReferences(projection, unbox);
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenPrimitiveLeftAndInvokedStringRight_whenMapped_thenSeparateStringConversionAndConcatenationFactsSurvive() throws IOException {
    // Arrange
    var produced = analyze("class Example { String text() { return \"text\"; } String value(int input) { return input + text(); } }",
        "type:Example", "value");
    var read = operation(produced.graph(), OperationKind.READ);
    var invocation = operation(produced.graph(), OperationKind.INVOKE);
    var conversion = operation(produced.graph(), OperationKind.STRING_CONVERSION);
    var concat = operation(produced.graph(), OperationKind.STRING_CONCAT);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, conversion, "STRING_CONVERSION", "string conversion", List.of("unchecked-string-conversion"));
    assertOperation(produced, projection, concat, "STRING_CONCAT", "PLUS", List.of("java.lang.OutOfMemoryError"));
    assertTemporary(produced, projection, conversion, "string conversion", "DECLARED", "java.lang.String", "java.lang.String");
    assertTemporary(produced, projection, concat, "PLUS", "DECLARED", "java.lang.String", "java.lang.String");
    assertOperands(conversion, List.of(defined(read)));
    assertOperands(concat, List.of(defined(conversion), defined(invocation)));
    assertTransfer(produced, projection, invocation.nodeId(), conversion.nodeId(), TransferKind.NORMAL, Optional.empty());
    assertExceptional(produced, projection, conversion, "unchecked-string-conversion");
    assertExceptional(produced, projection, concat, "java.lang.OutOfMemoryError");
    assertNoReferences(projection, conversion);
    assertNoReferences(projection, concat);
    assertFalse(produced.graph().operationNodes().stream().anyMatch(node -> node.kind() == OperationKind.BOX));
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenConstantStringConcatenation_whenMapped_thenConversionAndConcatenationHaveExactEmptyExceptionLists() throws IOException {
    // Arrange
    var produced = analyze("class Example { String value() { return \"text\" + 3; } }", "type:Example", "value");
    var conversion = operation(produced.graph(), OperationKind.STRING_CONVERSION);
    var concat = operation(produced.graph(), OperationKind.STRING_CONCAT);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, conversion, "STRING_CONVERSION", "string conversion", List.of());
    assertOperation(produced, projection, concat, "STRING_CONCAT", "PLUS", List.of());
    assertTemporary(produced, projection, conversion, "string conversion", "DECLARED", "java.lang.String", "java.lang.String");
    assertTemporary(produced, projection, concat, "PLUS", "DECLARED", "java.lang.String", "java.lang.String");
    assertEquals(List.of(), produced.graph().edges().stream().filter(edge -> edge.transferKind() == TransferKind.EXCEPTIONAL).toList());
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenNullStringOperand_whenMapped_thenNullConversionOmitsFailureWhileConcatenationRetainsAllocationFailure() throws IOException {
    // Arrange
    var produced = analyze("class Example { String value(String text) { return text + null; } }", "type:Example", "value");
    var conversion = operation(produced.graph(), OperationKind.STRING_CONVERSION);
    var concat = operation(produced.graph(), OperationKind.STRING_CONCAT);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, conversion, "STRING_CONVERSION", "string conversion", List.of());
    assertOperation(produced, projection, concat, "STRING_CONCAT", "PLUS", List.of("java.lang.OutOfMemoryError"));
    assertTemporary(produced, projection, conversion, "string conversion", "DECLARED", "java.lang.String", "java.lang.String");
    var nullValue = conversion.operands().getFirst().value();
    assertConstant(produced, projection, nullValue, "null", "NULL", "<nulltype>", "<nulltype>");
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenPrimitiveWrittenToReferenceArray_whenMapped_thenTargetCheckBoxAndStoreRemainSeparateEffectSites() throws IOException {
    // Arrange
    var produced = analyze("class Example { void value(Object[] array, int index, int input) { array[index] = input; } }",
        "type:Example", "value");
    var reads = operations(produced.graph(), OperationKind.READ);
    var check = operation(produced.graph(), OperationKind.ARRAY_TARGET_CHECK);
    var box = operation(produced.graph(), OperationKind.BOX);
    var write = operation(produced.graph(), OperationKind.WRITE);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, check, "ARRAY_TARGET_CHECK", "array target",
        List.of("java.lang.NullPointerException", "java.lang.ArrayIndexOutOfBoundsException"));
    assertOperation(produced, projection, box, "BOX", "BOX", List.of("java.lang.OutOfMemoryError"));
    assertOperation(produced, projection, write, "WRITE", "assignment", List.of("java.lang.ArrayStoreException"));
    assertTemporary(produced, projection, box, "BOX", "DECLARED", "java.lang.Object", "java.lang.Object");
    assertOperands(check, List.of(defined(reads.get(0)), defined(reads.get(1))));
    assertOperands(box, List.of(defined(reads.get(2))));
    assertOperands(write, List.of(defined(reads.get(0)), defined(reads.get(1)), defined(box)));
    assertNoDefinition(projection, check);
    assertNoReferences(projection, check);
    assertTransfer(produced, projection, reads.get(2).nodeId(), check.nodeId(), TransferKind.NORMAL, Optional.empty());
    assertTransfer(produced, projection, check.nodeId(), box.nodeId(), TransferKind.NORMAL, Optional.empty());
    assertExceptional(produced, projection, check, "java.lang.NullPointerException");
    assertExceptional(produced, projection, check, "java.lang.ArrayIndexOutOfBoundsException");
    assertExceptional(produced, projection, write, "java.lang.ArrayStoreException");
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenBoxedArrayIndexAndRightHandSide_whenMapped_thenTargetUsesConvertedIndexBeforeRightHandSideUnboxing() throws IOException {
    // Arrange
    var produced = analyze("class Example { void value(int[] array, Integer index, Integer input) { array[index] = input; } }",
        "type:Example", "value");
    var reads = operations(produced.graph(), OperationKind.READ);
    var unboxes = operations(produced.graph(), OperationKind.UNBOX);
    var check = operation(produced.graph(), OperationKind.ARRAY_TARGET_CHECK);
    var write = operation(produced.graph(), OperationKind.WRITE);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, check, "ARRAY_TARGET_CHECK", "array target",
        List.of("java.lang.NullPointerException", "java.lang.ArrayIndexOutOfBoundsException"));
    for (var unbox : unboxes) {
      assertOperation(produced, projection, unbox, "UNBOX", "UNBOX", List.of("java.lang.NullPointerException"));
      assertTemporary(produced, projection, unbox, "UNBOX", "INT", "int", "int");
    }
    assertOperands(check, List.of(defined(reads.get(0)), defined(unboxes.get(0))));
    assertOperands(write, List.of(defined(reads.get(0)), defined(unboxes.get(0)), defined(unboxes.get(1))));
    assertOperation(produced, projection, write, "WRITE", "assignment", List.of());
    assertTransfer(produced, projection, check.nodeId(), unboxes.get(1).nodeId(), TransferKind.NORMAL, Optional.empty());
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenVariableArityConstructor_whenMapped_thenInitializationAllocationConversionsPackingAndConstructionArePreserved() throws IOException {
    // Arrange
    var produced = analyze("class Example { Example(Object fixed, int... rest) { } Example value(int fixed, Integer tail) { return new Example(fixed, tail); } }",
        "type:Example", "value");
    var initialization = operation(produced.graph(), OperationKind.CLASS_INITIALIZATION);
    var allocation = operation(produced.graph(), OperationKind.NEW_ALLOCATION);
    var box = operation(produced.graph(), OperationKind.BOX);
    var array = operation(produced.graph(), OperationKind.VARARGS_ARRAY);
    var unbox = operation(produced.graph(), OperationKind.UNBOX);
    var store = operation(produced.graph(), OperationKind.WRITE);
    var construction = operation(produced.graph(), OperationKind.NEW_OBJECT);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertInitialization(produced, projection, initialization, "Example", "type:Example");
    assertOperation(produced, projection, allocation, "NEW_ALLOCATION", "<init>", List.of("java.lang.OutOfMemoryError"));
    assertOperation(produced, projection, array, "VARARGS_ARRAY", "varargs", List.of("java.lang.OutOfMemoryError"));
    assertOperation(produced, projection, box, "BOX", "BOX", List.of("java.lang.OutOfMemoryError"));
    assertOperation(produced, projection, unbox, "UNBOX", "UNBOX", List.of("java.lang.NullPointerException"));
    assertOperation(produced, projection, store, "WRITE", "varargs element", List.of());
    assertTemporary(produced, projection, allocation, "<init>", "DECLARED", "Example", "Example");
    assertTemporary(produced, projection, array, "varargs", "ARRAY", "int[]", "int[]");
    assertTemporary(produced, projection, box, "BOX", "DECLARED", "java.lang.Object", "java.lang.Object");
    assertTemporary(produced, projection, unbox, "UNBOX", "INT", "int", "int");
    assertOperands(construction, List.of(defined(allocation), defined(box), defined(array)));
    assertOperands(store, List.of(defined(array), store.operands().get(1).value(), defined(unbox)));
    assertConstant(produced, projection, store.operands().get(1).value(), "0", "INT", "int", "int");
    assertEquals(defined(allocation), defined(construction));
    assertNoDefinition(projection, store);
    assertInvocation(produced, projection, construction, "NEW_OBJECT", "<init>", List.of("unchecked-constructor"), "CONSTRUCTOR",
        "OBJECT_CREATION", "Example", "Example", "type:Example");
    assertTransfer(produced, projection, initialization.nodeId(), allocation.nodeId(), TransferKind.NORMAL, Optional.empty());
    assertExceptional(produced, projection, allocation, "java.lang.OutOfMemoryError");
    assertExceptional(produced, projection, array, "java.lang.OutOfMemoryError");
    assertNoReferences(projection, allocation);
    assertNoReferences(projection, array);
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenQualifiedInnerCreation_whenMapped_thenEnclosingCheckAndAllocationConsumeTheSameSelectedOuterValue() throws IOException {
    // Arrange
    var produced = analyze("class Outer { class Inner { Inner(Integer input) {} } } class Example { Outer.Inner value(Outer outer, Integer input) { return outer.new Inner(input); } }",
        "type:Example", "value");
    var reads = operations(produced.graph(), OperationKind.READ);
    var check = operation(produced.graph(), OperationKind.ENCLOSING_INSTANCE_CHECK);
    var initialization = operation(produced.graph(), OperationKind.CLASS_INITIALIZATION);
    var allocation = operation(produced.graph(), OperationKind.NEW_ALLOCATION);
    var construction = operation(produced.graph(), OperationKind.NEW_OBJECT);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, check, "ENCLOSING_INSTANCE_CHECK", "enclosing instance", List.of("java.lang.NullPointerException"));
    assertOperation(produced, projection, allocation, "NEW_ALLOCATION", "<init>", List.of("java.lang.OutOfMemoryError"));
    assertInitialization(produced, projection, initialization, "Outer.Inner", "type:Outer.Inner");
    assertTemporary(produced, projection, allocation, "<init>", "DECLARED", "Outer.Inner", "Outer.Inner");
    assertOperands(check, List.of(defined(reads.get(0))));
    assertOperands(allocation, List.of(defined(reads.get(0))));
    assertOperands(construction, List.of(defined(allocation), defined(reads.get(1))));
    assertEquals(defined(allocation), defined(construction));
    assertNoDefinition(projection, check);
    assertNoReferences(projection, check);
    assertInvocation(produced, projection, construction, "NEW_OBJECT", "<init>", List.of("unchecked-constructor"), "CONSTRUCTOR",
        "QUALIFIED_OBJECT_CREATION", "Outer.Inner", "Outer.Inner", "type:Outer.Inner");
    assertTransfer(produced, projection, check.nodeId(), initialization.nodeId(), TransferKind.NORMAL, Optional.empty());
    assertExceptional(produced, projection, check, "java.lang.NullPointerException");
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenQualifiedSuperConstructor_whenMapped_thenEnclosingAndConvertedArgumentRemainInvocationOperandsWithoutAllocation() throws IOException {
    // Arrange
    var produced = analyze("class Outer { class Base { Base(long input) {} } } class Child extends Outer.Base { Child(Outer outer, Integer input) { outer.super(input); } }",
        "type:Child", "<init>");
    var read = operations(produced.graph(), OperationKind.READ).getFirst();
    var check = operation(produced.graph(), OperationKind.ENCLOSING_INSTANCE_CHECK);
    var unbox = operation(produced.graph(), OperationKind.UNBOX);
    var invocation = operation(produced.graph(), OperationKind.INVOKE);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, check, "ENCLOSING_INSTANCE_CHECK", "enclosing instance", List.of("java.lang.NullPointerException"));
    assertOperation(produced, projection, unbox, "UNBOX", "UNBOX", List.of("java.lang.NullPointerException"));
    assertTemporary(produced, projection, unbox, "UNBOX", "LONG", "long", "long");
    assertOperands(invocation, List.of(defined(read), defined(unbox)));
    assertInvocation(produced, projection, invocation, "INVOKE", "<init>", List.of("unchecked-invocation"), "CONSTRUCTOR",
        "QUALIFIED_SUPER_CONSTRUCTOR", "Outer.Base", "Outer.Base", "type:Outer.Base");
    assertNoDefinition(projection, invocation);
    assertTrue(produced.graph().operationNodes().stream()
        .noneMatch(node -> node.kind() == OperationKind.CLASS_INITIALIZATION || node.kind() == OperationKind.NEW_ALLOCATION));
    assertExceptional(produced, projection, check, "java.lang.NullPointerException");
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenInheritedStaticField_whenMapped_thenInitializationNamesActualDeclaringTypeAndHasOneTypeReference() throws IOException {
    // Arrange
    var produced = analyze("class Parent { static int mutable; } class Example extends Parent { int value() { return Example.mutable; } }",
        "type:Example", "value");
    var initialization = operation(produced.graph(), OperationKind.CLASS_INITIALIZATION);
    var field = operation(produced.graph(), OperationKind.MEMBER_ACCESS);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertInitialization(produced, projection, initialization, "Parent", "type:Parent");
    assertOperation(produced, projection, field, "MEMBER_ACCESS", "mutable", List.of());
    assertTransfer(produced, projection, initialization.nodeId(), field.nodeId(), TransferKind.NORMAL, Optional.empty());
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenTwoTrailingInvocationArguments_whenMapped_thenPackingStoresPreserveArrayTypeIndexLiteralsAndArgumentOrder() throws IOException {
    // Arrange
    var produced = analyze("class Example { java.util.List<String> value() { return java.util.Arrays.asList(\"one\", \"two\"); } }",
        "type:Example", "value");
    var array = operation(produced.graph(), OperationKind.VARARGS_ARRAY);
    var stores = operations(produced.graph(), OperationKind.WRITE);
    var constants = operations(produced.graph(), OperationKind.CONSTANT);
    var invocation = operation(produced.graph(), OperationKind.INVOKE);
    var initialization = operation(produced.graph(), OperationKind.CLASS_INITIALIZATION);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, array, "VARARGS_ARRAY", "varargs", List.of("java.lang.OutOfMemoryError"));
    assertTemporary(produced, projection, array, "varargs", "ARRAY", "java.lang.String[]", "java.lang.String[]");
    for (var position = 0; position < stores.size(); position++) {
      var store = stores.get(position);
      assertOperation(produced, projection, store, "WRITE", "varargs element", List.of());
      assertOperands(store, List.of(defined(array), store.operands().get(1).value(), defined(constants.get(position))));
      assertConstant(produced, projection, store.operands().get(1).value(), Integer.toString(position), "INT", "int", "int");
      assertNoDefinition(projection, store);
    }
    assertEquals(2, stores.size());
    assertOperands(invocation, List.of(defined(array)));
    assertInitialization(produced, projection, initialization, "java.util.Arrays", "type:java.util.Arrays");
    assertTransfer(produced, projection, stores.getLast().nodeId(), initialization.nodeId(), TransferKind.NORMAL, Optional.empty());
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenFixedArrayArgument_whenMapped_thenInvocationUsesExistingArrayAndEmitsNoVariableArityAllocation() throws IOException {
    // Arrange
    var produced = analyze("class Example { java.util.List<String> value(String[] input) { return java.util.Arrays.asList(input); } }",
        "type:Example", "value");
    var read = operation(produced.graph(), OperationKind.READ);
    var invocation = operation(produced.graph(), OperationKind.INVOKE);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperands(invocation, List.of(defined(read)));
    assertTrue(produced.graph().operationNodes().stream().noneMatch(node -> node.kind() == OperationKind.VARARGS_ARRAY));
    assertTemporary(produced, projection, read, "input", "ARRAY", "java.lang.String[]", "java.lang.String[]");
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenBoxedIfCondition_whenMapped_thenTrueFalseNormalReturnAndExceptionalTransfersHaveExactEndpoints() throws IOException {
    // Arrange
    var produced = analyze("class Example { int value(Boolean condition) { if (condition) return 1; else return 2; } }",
        "type:Example", "value");
    var unbox = operation(produced.graph(), OperationKind.UNBOX);
    var decision = operation(produced.graph(), OperationKind.DECISION);
    var constants = operations(produced.graph(), OperationKind.CONSTANT);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, unbox, "UNBOX", "UNBOX", List.of("java.lang.NullPointerException"));
    assertTemporary(produced, projection, unbox, "UNBOX", "BOOLEAN", "boolean", "boolean");
    assertTransfer(produced, projection, unbox.nodeId(), decision.nodeId(), TransferKind.NORMAL, Optional.empty());
    assertTransfer(produced, projection, decision.nodeId(), constants.get(0).nodeId(), TransferKind.TRUE_BRANCH, Optional.empty());
    assertTransfer(produced, projection, decision.nodeId(), constants.get(1).nodeId(), TransferKind.FALSE_BRANCH, Optional.empty());
    assertEquals(Set.of(TransferKind.NORMAL, TransferKind.TRUE_BRANCH, TransferKind.FALSE_BRANCH, TransferKind.RETURN, TransferKind.EXCEPTIONAL),
        produced.graph().edges().stream().map(edge -> edge.transferKind()).collect(Collectors.toSet()));
    assertGraphBoundary(produced, projection);
  }

  @Test
  void givenBoxedSwitchAndBoxedResults_whenMapped_thenAlternativeDiscriminatorsAndSelectedResultValueLinksArePreserved() throws IOException {
    // Arrange
    var produced = analyze("class Example { Object value(Integer selector) { return switch (selector) { case 0 -> 1; default -> { yield 2; } }; } }",
        "type:Example", "value");
    var unbox = operation(produced.graph(), OperationKind.UNBOX);
    var selector = operation(produced.graph(), OperationKind.SWITCH_SELECTOR);
    var constants = operations(produced.graph(), OperationKind.CONSTANT);
    var boxes = operations(produced.graph(), OperationKind.BOX);
    var merge = operation(produced.graph(), OperationKind.MERGE);

    // Act
    var projection = new Neo4jAnalysisRunMapper().map(produced.run());

    // Assert
    assertOperation(produced, projection, unbox, "UNBOX", "UNBOX", List.of("java.lang.NullPointerException"));
    assertTemporary(produced, projection, unbox, "UNBOX", "INT", "int", "int");
    for (var box : boxes) {
      assertOperation(produced, projection, box, "BOX", "BOX", List.of("java.lang.OutOfMemoryError"));
      assertTemporary(produced, projection, box, "BOX", "DECLARED", "java.lang.Object", "java.lang.Object");
    }
    assertOperands(merge, boxes.stream().map(Neo4jSelectedGraphProjectionTest::defined).toList());
    assertTransfer(produced, projection, selector.nodeId(), constants.get(0).nodeId(), TransferKind.SWITCH_ALTERNATIVE,
        Optional.of("literal:" + StableId.sha256("java-type", "INT", "int", "int") + ":0"));
    assertTransfer(produced, projection, selector.nodeId(), constants.get(1).nodeId(), TransferKind.SWITCH_ALTERNATIVE, Optional.of("default"));
    assertGraphBoundary(produced, projection);
  }

  private ProducedGraph analyze(String source, String owner, String name) throws IOException {
    var snapshot = InputSnapshot.fromSources(sourceRoot, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var run = new AnalysisEngine().analyze(input);
    assertEquals(AnalysisCompletion.COMPLETED, run.completion(), () -> run.diagnostics().toString());
    assertTrue(run.diagnostics().stream().noneMatch(AnalysisDiagnostic::isError));
    var graph = run.representation().orElseThrow().methodAnalyses().stream()
        .filter(analysis -> analysis.sourceMethod().ownerTypeKey().equals(owner) && analysis.sourceMethod().name().equals(name))
        .map(analysis -> analysis.controlFlowGraph()).findFirst().orElseThrow();
    return new ProducedGraph(run, graph);
  }

  private static OperationNode operation(ControlFlowGraph graph, OperationKind kind) {
    var matches = operations(graph, kind);
    assertEquals(1, matches.size(), () -> "Expected one " + kind + " in " + graph.operationNodes());
    return matches.getFirst();
  }

  private static List<OperationNode> operations(ControlFlowGraph graph, OperationKind kind) {
    return graph.operationNodes().stream().filter(node -> node.kind() == kind).toList();
  }

  private static FlowValue defined(OperationNode operation) {
    return operation.definedValue().orElseThrow();
  }

  private static Neo4jNode node(Neo4jGraphProjection projection, String key) {
    return projection.nodes().stream().filter(candidate -> candidate.key().equals(key)).findFirst().orElseThrow();
  }

  private static void assertOperation(ProducedGraph produced, Neo4jGraphProjection projection, OperationNode operation, String kind,
      String detail, List<String> exceptions) {
    var expected = headers(produced.run().runId(), operation.nodeId(), "graphId", produced.graph().graphId(), "kind", kind, "detail", detail,
        "possibleExceptions", exceptions);
    assertEquals(new NodeExpectation("OperationNode", expected), new NodeExpectation(node(projection, operation.nodeId()).label(),
        node(projection, operation.nodeId()).properties()));
  }

  private static void assertInvocation(ProducedGraph produced, Neo4jGraphProjection projection, OperationNode operation, String kind,
      String detail, List<String> exceptions, String mode, String form, String typeDisplay, String typeErasure, String declaration) {
    var expected = headers(produced.run().runId(), operation.nodeId(), "graphId", produced.graph().graphId(), "kind", kind, "detail", detail,
        "possibleExceptions", exceptions, "invocationMode", mode, "invocationForm", form, "qualifyingTypeDeclarationKey", declaration);
    addType(expected, "qualifyingType", "DECLARED", typeDisplay, typeErasure);
    assertEquals(new NodeExpectation("OperationNode", expected), new NodeExpectation(node(projection, operation.nodeId()).label(),
        node(projection, operation.nodeId()).properties()));
  }

  private static void assertTemporary(ProducedGraph produced, Neo4jGraphProjection projection, OperationNode operation, String display,
      String category, String typeDisplay, String erasure) {
    var value = defined(operation);
    assertValue(produced, projection, value, "TEMPORARY", "displayName", display, category, typeDisplay, erasure);
  }

  private static void assertConstant(ProducedGraph produced, Neo4jGraphProjection projection, FlowValue value, String literal,
      String category, String typeDisplay, String erasure) {
    assertValue(produced, projection, value, "CONSTANT", "literal", literal, category, typeDisplay, erasure);
  }

  private static void assertValue(ProducedGraph produced, Neo4jGraphProjection projection, FlowValue value, String kind, String valueProperty,
      String valueText, String category, String typeDisplay, String erasure) {
    var position = produced.graph().values().indexOf(value);
    assertTrue(position >= 0);
    var expected = headers(produced.run().runId(), value.valueId(), "graphId", produced.graph().graphId(), "kind", kind, "position", position,
        valueProperty, valueText);
    addType(expected, "staticType", category, typeDisplay, erasure);
    assertEquals(new NodeExpectation("FlowValue", expected), new NodeExpectation(node(projection, value.valueId()).label(),
        node(projection, value.valueId()).properties()));
  }

  private static void assertOperands(OperationNode operation, List<FlowValue> expected) {
    assertEquals(expected, operation.operands().stream().map(operand -> operand.value()).toList());
    assertEquals(expected.size(), operation.operands().size());
    for (var position = 0; position < expected.size(); position++) {
      assertEquals(position, operation.operands().get(position).position());
    }
  }

  private static void assertNoDefinition(Neo4jGraphProjection projection, OperationNode operation) {
    assertTrue(projection.relationships().stream()
        .noneMatch(relation -> relation.type().equals("DEFINES_VALUE") && relation.sourceKey().equals(operation.nodeId())));
  }

  private static void assertNoReferences(Neo4jGraphProjection projection, OperationNode operation) {
    assertTrue(projection.relationships().stream()
        .noneMatch(relation -> relation.type().equals("HAS_REFERENCE") && relation.sourceKey().equals(operation.nodeId())));
  }

  private static void assertInitialization(ProducedGraph produced, Neo4jGraphProjection projection, OperationNode initialization,
      String typeName, String declaration) {
    assertOperation(produced, projection, initialization, "CLASS_INITIALIZATION", typeName, List.of("unchecked-class-initialization"));
    assertOperands(initialization, List.of());
    assertNoDefinition(projection, initialization);
    var key = StableId.sha256("semantic-reference", initialization.nodeId(), "0");
    var expected = headers(produced.run().runId(), key, "kind", "TYPE", "status", "RESOLVED", "position", 0, "symbolKey", declaration,
        "typeName", typeName, "typeAlternativeNames", List.of(), "assignableToTypeNames", List.of());
    assertEquals(new NodeExpectation("SemanticReference", expected), new NodeExpectation(node(projection, key).label(), node(projection, key).properties()));
    assertEquals(List.of(expectedRelation(produced.run().runId(), "HAS_REFERENCE", initialization.nodeId(), key, "0", "position", 0)),
        projection.relationships().stream().filter(relation -> relation.type().equals("HAS_REFERENCE")
            && relation.sourceKey().equals(initialization.nodeId())).map(Neo4jSelectedGraphProjectionTest::relationship).toList());
    assertExceptional(produced, projection, initialization, "unchecked-class-initialization");
  }

  private static void assertExceptional(ProducedGraph produced, Neo4jGraphProjection projection, OperationNode source, String discriminator) {
    assertTransfer(produced, projection, source.nodeId(), produced.graph().exit().nodeId(), TransferKind.EXCEPTIONAL, Optional.of(discriminator));
  }

  private static void assertTransfer(ProducedGraph produced, Neo4jGraphProjection projection, String source, String target, TransferKind kind,
      Optional<String> discriminator) {
    var matches = produced.graph().edges().stream().filter(edge -> edge.sourceNodeId().equals(source) && edge.targetNodeId().equals(target)
        && edge.transferKind() == kind && edge.discriminator().equals(discriminator)).toList();
    assertEquals(1, matches.size());
    var edge = matches.getFirst();
    var expected = discriminator.isPresent()
        ? expectedRelation(produced.run().runId(), transferType(kind), source, target, edge.edgeId(), "edgeId", edge.edgeId(), "discriminator",
            discriminator.orElseThrow())
        : expectedRelation(produced.run().runId(), transferType(kind), source, target, edge.edgeId(), "edgeId", edge.edgeId());
    assertEquals(expected, projection.relationships().stream().filter(relation -> relation.key().equals(expected.properties().get("key")))
        .map(Neo4jSelectedGraphProjectionTest::relationship).findFirst().orElseThrow());
  }

  private static void assertGraphBoundary(ProducedGraph produced, Neo4jGraphProjection projection) {
    var graph = produced.graph();
    var graphNodeKeys = graph.nodes().stream().map(graphNode -> graphNode.nodeId()).collect(Collectors.toSet());
    var expected = new HashSet<RelationshipExpectation>();
    for (var position = 0; position < graph.nodes().size(); position++) {
      var graphNode = graph.nodes().get(position);
      expected.add(expectedRelation(produced.run().runId(), "HAS_NODE", graph.graphId(), graphNode.nodeId(), Integer.toString(position),
          "position", position));
    }
    for (var position = 0; position < graph.values().size(); position++) {
      var value = graph.values().get(position);
      expected.add(expectedRelation(produced.run().runId(), "HAS_VALUE", graph.graphId(), value.valueId(), Integer.toString(position),
          "position", position));
    }
    for (var operation : graph.operationNodes()) {
      for (var operand : operation.operands()) {
        expected.add(expectedRelation(produced.run().runId(), "USES_VALUE", operation.nodeId(), operand.value().valueId(),
            Integer.toString(operand.position()), "position", operand.position()));
      }
      operation.definedValue().ifPresent(value -> expected.add(expectedRelation(produced.run().runId(), "DEFINES_VALUE", operation.nodeId(),
          value.valueId(), "definition")));
      assertReferencesAndOrigins(produced, projection, operation);
    }
    for (var edge : graph.edges()) {
      expected.add(edge.discriminator().isPresent()
          ? expectedRelation(produced.run().runId(), transferType(edge.transferKind()), edge.sourceNodeId(), edge.targetNodeId(), edge.edgeId(),
              "edgeId", edge.edgeId(), "discriminator", edge.discriminator().orElseThrow())
          : expectedRelation(produced.run().runId(), transferType(edge.transferKind()), edge.sourceNodeId(), edge.targetNodeId(), edge.edgeId(),
              "edgeId", edge.edgeId()));
    }
    var actual = projection.relationships().stream().filter(relation ->
        ((relation.type().equals("HAS_NODE") || relation.type().equals("HAS_VALUE")) && relation.sourceKey().equals(graph.graphId()))
            || ((relation.type().equals("USES_VALUE") || relation.type().equals("DEFINES_VALUE") || TRANSFER_TYPES.contains(relation.type()))
                && graphNodeKeys.contains(relation.sourceKey())))
        .map(Neo4jSelectedGraphProjectionTest::relationship).collect(Collectors.toSet());
    assertEquals(expected, actual);
    assertEquals(expected.size(), projection.relationships().stream().filter(relation -> actual.contains(relationship(relation))).count());
    assertEquals(graphNodeKeys, projection.nodes().stream().filter(candidate -> graph.graphId().equals(candidate.properties().get("graphId")))
        .filter(candidate -> !candidate.label().equals("FlowValue")).map(Neo4jNode::key).collect(Collectors.toSet()));
    assertEquals(Set.of("EntryNode", "OperationNode", "ExitNode"), projection.nodes().stream()
        .filter(candidate -> graphNodeKeys.contains(candidate.key())).map(Neo4jNode::label).collect(Collectors.toSet()));
  }

  private static void assertReferencesAndOrigins(ProducedGraph produced, Neo4jGraphProjection projection, OperationNode operation) {
    var expectedReferences = new ArrayList<RelationshipExpectation>();
    for (var position = 0; position < operation.semanticReferences().size(); position++) {
      var reference = operation.semanticReferences().get(position);
      var key = StableId.sha256("semantic-reference", operation.nodeId(), Integer.toString(position));
      var properties = headers(produced.run().runId(), key, "kind", reference.referenceKind().name(), "status", reference.status().name(),
          "position", position, "typeAlternativeNames", reference.typeAlternativeNames(), "assignableToTypeNames", reference.assignableToTypeNames());
      reference.symbolKey().ifPresent(value -> properties.put("symbolKey", value));
      reference.typeName().ifPresent(value -> properties.put("typeName", value));
      assertEquals(new NodeExpectation("SemanticReference", properties), new NodeExpectation(node(projection, key).label(), node(projection, key).properties()));
      expectedReferences.add(expectedRelation(produced.run().runId(), "HAS_REFERENCE", operation.nodeId(), key, Integer.toString(position),
          "position", position));
    }
    assertEquals(expectedReferences, projection.relationships().stream().filter(relation -> relation.type().equals("HAS_REFERENCE")
        && relation.sourceKey().equals(operation.nodeId())).map(Neo4jSelectedGraphProjectionTest::relationship).toList());
    var expectedOrigins = new ArrayList<RelationshipExpectation>();
    for (var position = 0; position < operation.origins().size(); position++) {
      var origin = operation.origins().get(position);
      var key = StableId.sha256("source-origin", operation.nodeId(), Integer.toString(position), origin.sourceFileKey(),
          Long.toString(origin.startOffset()), Long.toString(origin.endOffsetExclusive()));
      var properties = headers(produced.run().runId(), key, "position", position, "startOffset", origin.startOffset(), "endOffsetExclusive",
          origin.endOffsetExclusive());
      assertEquals(new NodeExpectation("SourceOrigin", properties), new NodeExpectation(node(projection, key).label(), node(projection, key).properties()));
      expectedOrigins.add(expectedRelation(produced.run().runId(), "HAS_ORIGIN", operation.nodeId(), key, Integer.toString(position), "position", position));
      assertEquals(List.of(expectedRelation(produced.run().runId(), "IN_SOURCE", key, origin.sourceFileKey(), "source")),
          projection.relationships().stream().filter(relation -> relation.type().equals("IN_SOURCE") && relation.sourceKey().equals(key))
              .map(Neo4jSelectedGraphProjectionTest::relationship).toList());
    }
    assertEquals(expectedOrigins, projection.relationships().stream().filter(relation -> relation.type().equals("HAS_ORIGIN")
        && relation.sourceKey().equals(operation.nodeId())).map(Neo4jSelectedGraphProjectionTest::relationship).toList());
  }

  private static String transferType(TransferKind kind) {
    return switch (kind) {
      case NORMAL -> "NORMAL_FLOW";
      case TRUE_BRANCH -> "TRUE_FLOW";
      case FALSE_BRANCH -> "FALSE_FLOW";
      case SWITCH_ALTERNATIVE -> "SWITCH_FLOW";
      case RETURN -> "RETURN_FLOW";
      case EXCEPTIONAL -> "EXCEPTIONAL_FLOW";
    };
  }

  private static RelationshipExpectation expectedRelation(String runId, String type, String source, String target, String discriminator,
      Object... pairs) {
    var key = StableId.sha256("relationship", type, source, target, discriminator);
    return new RelationshipExpectation(type, source, target, headers(runId, key, pairs));
  }

  private static RelationshipExpectation relationship(Neo4jRelationship relationship) {
    return new RelationshipExpectation(relationship.type(), relationship.sourceKey(), relationship.targetKey(), relationship.properties());
  }

  private static Map<String, Object> headers(String runId, String key, Object... pairs) {
    var properties = new LinkedHashMap<String, Object>();
    properties.put("runId", runId);
    properties.put("key", key);
    for (var position = 0; position < pairs.length; position += 2) {
      properties.put((String) pairs[position], pairs[position + 1]);
    }
    return properties;
  }

  private static void addType(Map<String, Object> properties, String prefix, String category, String display, String erasure) {
    properties.put(prefix, display);
    properties.put(prefix + "Key", StableId.sha256("java-type", category, display, erasure));
    properties.put(prefix + "Erasure", erasure);
    properties.put(prefix + "Category", category);
  }

  private record ProducedGraph(AnalysisRun run, ControlFlowGraph graph) {
  }

  private record NodeExpectation(String label, Map<String, Object> properties) {
  }

  private record RelationshipExpectation(String type, String source, String target, Map<String, Object> properties) {
  }
}
