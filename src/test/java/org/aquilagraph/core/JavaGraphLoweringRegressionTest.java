package org.aquilagraph.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.ControlFlowGraph;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.InvocationMode;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.OperationNode;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.model.ValueKind;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaGraphLoweringRegressionTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenInstanceAndStaticInvocations_whenAnalysisRuns_thenSelectorsAreNotLoweredAsValueReads() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Calls.java"), """
        class Calls {
          static void staticCall() { }
          void instanceCall() { }
          Calls receiver() { return this; }

          void execute(Calls object) {
            instanceCall();
            object.instanceCall();
            Calls.staticCall();
            java.util.Objects.requireNonNull(object);
            receiver().staticCall();
          }
        }
        """);

    // Act
    var input = analyze();
    var graph = graph(input, "execute");

    // Assert
    var invocations = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.INVOKE).toList();
    assertEquals(6, invocations.size());
    assertFalse(graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.READ)
        .anyMatch(node -> Set.of("instanceCall", "staticCall", "Calls").contains(node.detail())));
    assertFalse(graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.MEMBER_ACCESS)
        .anyMatch(node -> Set.of("instanceCall", "staticCall").contains(node.detail())));
    var methods = input.program().methods();
    var instanceKey = methods.stream().filter(method -> method.name().equals("instanceCall")).findFirst().orElseThrow().methodKey();
    var staticKey = methods.stream().filter(method -> method.name().equals("staticCall")).findFirst().orElseThrow().methodKey();
    var receiverKey = methods.stream().filter(method -> method.name().equals("receiver")).findFirst().orElseThrow().methodKey();
    assertEquals(2, invocations.stream().filter(node -> node.detail().equals("instanceCall"))
        .filter(node -> node.semanticReferences().getFirst().symbolKey().orElseThrow().equals(instanceKey)).count());
    assertEquals(2, invocations.stream().filter(node -> node.detail().equals("staticCall"))
        .filter(node -> node.semanticReferences().getFirst().symbolKey().orElseThrow().equals(staticKey)).count());
    var receiverCall = invocations.stream().filter(node -> node.detail().equals("receiver")).findFirst().orElseThrow();
    assertEquals(receiverKey, receiverCall.semanticReferences().getFirst().symbolKey().orElseThrow());
    var implicitCall = invocations.stream().filter(node -> node.detail().equals("instanceCall"))
        .filter(node -> node.operands().getFirst().value().kind() == ValueKind.RECEIVER).findFirst().orElseThrow();
    var explicitCall = invocations.stream().filter(node -> node.detail().equals("instanceCall"))
        .filter(node -> node.operands().getFirst().value().kind() == ValueKind.TEMPORARY).findFirst().orElseThrow();
    assertEquals(1, implicitCall.operands().size());
    assertTrue(graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.READ && node.detail().equals("object"))
        .anyMatch(node -> node.definedValue().orElseThrow().valueId().equals(explicitCall.operands().getFirst().value().valueId())));
    var packageCall = invocations.stream().filter(node -> node.detail().equals("requireNonNull")).findFirst().orElseThrow();
    assertEquals(ReferenceKind.METHOD, packageCall.semanticReferences().getFirst().referenceKind());
    assertEquals(1, packageCall.operands().size());
    var expressionQualifiedStaticCall = invocations.stream().filter(node -> node.detail().equals("staticCall"))
        .filter(node -> graph.operationNodes().stream().filter(initialization -> initialization.kind() == OperationKind.CLASS_INITIALIZATION)
            .anyMatch(initialization -> graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(receiverCall.nodeId())
                && edge.targetNodeId().equals(initialization.nodeId()) && edge.transferKind() == TransferKind.NORMAL)
                && graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(initialization.nodeId())
                    && edge.targetNodeId().equals(node.nodeId()) && edge.transferKind() == TransferKind.NORMAL)))
        .findFirst().orElseThrow();
    assertTrue(expressionQualifiedStaticCall.operands().isEmpty());
  }

  @Test
  void givenSimpleAssignments_whenAnalysisRuns_thenLocationsAreEvaluatedWithoutReadingOldValues() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Assignments.java"), """
        class Assignments {
          static int staticField;
          int field;

          int execute(Assignments object, Object[] array, int index, int value, Object arrayValue) {
            int local = 0;
            local = value;
            object.field = value;
            array[index] = arrayValue;
            Assignments.staticField = value;
            return local;
          }
        }
        """);

    // Act
    var input = analyze();
    var graph = graph(input, "execute");

    // Assert
    assertEquals(4, graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.WRITE
        && node.detail().equals("assignment")).count());
    assertEquals(1, graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.READ
        && node.detail().equals("local")).count());
    assertFalse(graph.operationNodes().stream().anyMatch(node -> node.kind() == OperationKind.ARRAY_ACCESS));
    var arrayCheck = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.ARRAY_TARGET_CHECK).findFirst().orElseThrow();
    var arrayWrite = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.WRITE)
        .filter(node -> node.possibleExceptions().contains("java.lang.ArrayStoreException")).findFirst().orElseThrow();
    assertEquals(List.of("java.lang.NullPointerException", "java.lang.ArrayIndexOutOfBoundsException"), arrayCheck.possibleExceptions());
    assertEquals(List.of("java.lang.ArrayStoreException"), arrayWrite.possibleExceptions());
    assertEquals(arrayWrite.operands().subList(0, 2), arrayCheck.operands());
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(arrayCheck.nodeId())
        && edge.targetNodeId().equals(arrayWrite.nodeId()) && edge.transferKind() == TransferKind.NORMAL));
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(arrayWrite.nodeId())
        && edge.transferKind() == TransferKind.EXCEPTIONAL));
    var localWrite = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.WRITE)
        .filter(node -> node.detail().equals("assignment"))
        .filter(node -> node.semanticReferences().stream().anyMatch(reference -> reference.referenceKind() == ReferenceKind.LOCAL))
        .findFirst().orElseThrow();
    var fields = input.program().types().stream().filter(type -> type.qualifiedName().equals("Assignments")).findFirst().orElseThrow()
        .fields();
    var instanceFieldKey = fields.stream().filter(field -> field.name().equals("field")).findFirst().orElseThrow().fieldKey();
    var staticFieldKey = fields.stream().filter(field -> field.name().equals("staticField")).findFirst().orElseThrow().fieldKey();
    var instanceWrite = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.WRITE)
        .filter(node -> node.semanticReferences().stream()
            .anyMatch(reference -> reference.symbolKey().orElseThrow().equals(instanceFieldKey)))
        .findFirst().orElseThrow();
    var staticWrite = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.WRITE)
        .filter(node -> node.semanticReferences().stream().anyMatch(reference -> reference.symbolKey().orElseThrow().equals(staticFieldKey)))
        .findFirst().orElseThrow();
    assertEquals(2, localWrite.operands().size());
    assertEquals(2, instanceWrite.operands().size());
    assertEquals(List.of("java.lang.NullPointerException"), instanceWrite.possibleExceptions());
    assertEquals(3, arrayWrite.operands().size());
    assertEquals(1, staticWrite.operands().size());
    assertTrue(List.of(localWrite, instanceWrite, arrayWrite, staticWrite).stream().allMatch(node -> node.definedValue().isPresent()));
    assertOperandEvaluationOrder(graph, instanceWrite);
    assertOperandEvaluationOrder(graph, arrayWrite);
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(instanceWrite.nodeId())
        && edge.transferKind() == TransferKind.EXCEPTIONAL
        && edge.discriminator().orElseThrow().equals("java.lang.NullPointerException")));
  }

  @Test
  void givenFieldReceiverForms_whenAnalysisRuns_thenReadsHaveExactReceiversReferencesAndEdges() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("FieldReads.java"), """
        class FieldBase { int inherited; }
        class FieldReads extends FieldBase {
          static int shared;
          int own;
          int execute(FieldReads object) {
            FieldReads local = object;
            int first = own;
            int second = this.own;
            int third = object.own;
            int fourth = local.own;
            int fifth = FieldReads.shared;
            int sixth = super.inherited;
            return first + second + third + fourth + fifth + sixth;
          }
        }
        """);

    // Act
    var input = analyze();
    var graph = graph(input, "execute");

    // Assert
    var members = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.MEMBER_ACCESS).toList();
    assertEquals(6, members.size());
    var ownKey = input.program().types().stream().filter(type -> type.qualifiedName().equals("FieldReads")).findFirst().orElseThrow()
        .fields().stream().filter(field -> field.name().equals("own")).findFirst().orElseThrow().fieldKey();
    var sharedKey = input.program().types().stream().filter(type -> type.qualifiedName().equals("FieldReads")).findFirst().orElseThrow()
        .fields().stream().filter(field -> field.name().equals("shared")).findFirst().orElseThrow().fieldKey();
    var inheritedKey = input.program().types().stream().filter(type -> type.qualifiedName().equals("FieldBase")).findFirst().orElseThrow()
        .fields().stream().filter(field -> field.name().equals("inherited")).findFirst().orElseThrow().fieldKey();
    var ownReads = members.stream().filter(node -> node.semanticReferences().getFirst().symbolKey().orElseThrow().equals(ownKey)).toList();
    assertEquals(4, ownReads.size());
    assertEquals(2, ownReads.stream().filter(node -> node.operands().getFirst().value().kind() == ValueKind.RECEIVER).count());
    assertEquals(2, ownReads.stream().filter(node -> node.operands().getFirst().value().kind() == ValueKind.TEMPORARY).count());
    var staticRead = members.stream().filter(node -> node.semanticReferences().getFirst().symbolKey().orElseThrow().equals(sharedKey))
        .findFirst().orElseThrow();
    assertTrue(staticRead.operands().isEmpty());
    var superRead = members.stream().filter(node -> node.semanticReferences().getFirst().symbolKey().orElseThrow().equals(inheritedKey))
        .findFirst().orElseThrow();
    assertEquals(ValueKind.RECEIVER, superRead.operands().getFirst().value().kind());
    assertFalse(graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.READ)
        .anyMatch(node -> Set.of("own", "shared", "inherited", "FieldReads").contains(node.detail())));
    for (var explicitRead : ownReads.stream().filter(node -> node.operands().getFirst().value().kind() == ValueKind.TEMPORARY).toList()) {
      assertTrue(graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.READ)
          .filter(node -> node.definedValue().isPresent())
          .anyMatch(node -> node.definedValue().orElseThrow().valueId().equals(explicitRead.operands().getFirst().value().valueId())
              && graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(node.nodeId())
                  && edge.targetNodeId().equals(explicitRead.nodeId()) && edge.transferKind() == TransferKind.NORMAL)));
    }
  }

  @Test
  void givenJavaInvocationForms_whenAnalysisRuns_thenQualifyingTypesAndModesSurviveInDurableOperations() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("DispatchSubject.java"), """
        interface ParentApi {
          default int defaultValue() { return 1; }
        }
        interface ChildApi extends ParentApi { }
        class Base {
          Base() { }
          int inheritedClass() { return 2; }
        }
        class DispatchSubject extends Base implements ChildApi {
          static void staticMethod() { }
          DispatchSubject() { this(0); }
          DispatchSubject(int value) { super(); }

          int exercise(DispatchSubject concrete, ChildApi api) {
            defaultValue();
            concrete.defaultValue();
            api.defaultValue();
            concrete.inheritedClass();
            api.toString();
            staticMethod();
            DispatchSubject.staticMethod();
            concrete.staticMethod();
            super.inheritedClass();
            ChildApi.super.defaultValue();
            ((DispatchSubject & java.io.Serializable) concrete).defaultValue();
            ((ChildApi & java.io.Serializable) api).defaultValue();
            return new DispatchSubject(1).inheritedClass();
          }
        }
        """);

    // Act
    var input = analyze();
    var run = new AnalysisEngine().analyze(input);
    var operations = run.representation().orElseThrow().methodAnalyses().stream()
        .flatMap(analysis -> analysis.controlFlowGraph().operationNodes().stream()).toList();

    // Assert
    assertEquals(AnalysisCompletion.INCOMPLETE, run.completion());
    assertTrue(operations.stream().filter(node -> node.kind() == OperationKind.INVOKE || node.kind() == OperationKind.NEW_OBJECT)
        .allMatch(node -> node.invocationSemantics().isPresent()));
    assertTrue(operations.stream().filter(node -> node.kind() != OperationKind.INVOKE && node.kind() != OperationKind.NEW_OBJECT)
        .allMatch(node -> node.invocationSemantics().isEmpty()));
    assertInvocation(operations, "defaultValue", InvocationForm.INSTANCE_IMPLICIT, InvocationMode.VIRTUAL,
        "type:DispatchSubject");
    assertInvocation(operations, "defaultValue", InvocationForm.INSTANCE_EXPRESSION, InvocationMode.VIRTUAL,
        "type:DispatchSubject");
    assertInvocation(operations, "defaultValue", InvocationForm.INSTANCE_EXPRESSION, InvocationMode.INTERFACE, "type:ChildApi");
    assertInvocation(operations, "inheritedClass", InvocationForm.INSTANCE_EXPRESSION, InvocationMode.VIRTUAL,
        "type:DispatchSubject");
    assertInvocation(operations, "toString", InvocationForm.INSTANCE_EXPRESSION, InvocationMode.VIRTUAL, "type:java.lang.Object");
    assertInvocation(operations, "staticMethod", InvocationForm.STATIC_UNQUALIFIED, InvocationMode.STATIC,
        "type:DispatchSubject");
    assertInvocation(operations, "staticMethod", InvocationForm.STATIC_TYPE, InvocationMode.STATIC, "type:DispatchSubject");
    assertInvocation(operations, "staticMethod", InvocationForm.STATIC_EXPRESSION, InvocationMode.STATIC,
        "type:DispatchSubject");
    assertInvocation(operations, "inheritedClass", InvocationForm.SUPER, InvocationMode.SUPER, "type:Base");
    assertInvocation(operations, "defaultValue", InvocationForm.QUALIFIED_SUPER, InvocationMode.SUPER, "type:ChildApi");
    assertInvocation(operations, "<init>", InvocationForm.THIS_CONSTRUCTOR, InvocationMode.CONSTRUCTOR, "type:DispatchSubject");
    assertInvocation(operations, "<init>", InvocationForm.SUPER_CONSTRUCTOR, InvocationMode.CONSTRUCTOR, "type:Base");
    assertInvocation(operations, "<init>", InvocationForm.OBJECT_CREATION, InvocationMode.CONSTRUCTOR, "type:DispatchSubject");
    assertEquals(2L,
        invocationCount(operations, "defaultValue", InvocationForm.INSTANCE_EXPRESSION, InvocationMode.VIRTUAL, "type:DispatchSubject"));
    assertEquals(2L,
        invocationCount(operations, "defaultValue", InvocationForm.INSTANCE_EXPRESSION, InvocationMode.INTERFACE, "type:ChildApi"));
  }

  @Test
  void givenIntegralDivisionAndRemainder_whenGraphsAreBuilt_thenEachOperationRetainsItsArithmeticRoute() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("IntegralArithmetic.java"), """
        class IntegralArithmetic {
          int divideInt(int left, int right) { return left / right; }
          long divideLong(long left, long right) { return left / right; }
          int remainderInt(int left, int right) { return left % right; }
          long remainderLong(long left, long right) { return left % right; }
        }
        """);

    // Act
    var input = analyze();
    var graphs = List.of("divideInt", "divideLong", "remainderInt", "remainderLong").stream()
        .map(name -> graph(input, name)).toList();

    // Assert
    for (var graph : graphs) {
      var arithmetic = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.BINARY).findFirst().orElseThrow();
      assertEquals(List.of("java.lang.ArithmeticException"), arithmetic.possibleExceptions());
      var exceptionalEdges = graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(arithmetic.nodeId()))
          .filter(edge -> edge.transferKind() == TransferKind.EXCEPTIONAL).toList();
      assertEquals(1, exceptionalEdges.size());
      assertEquals("java.lang.ArithmeticException", exceptionalEdges.getFirst().discriminator().orElseThrow());
      assertEquals(graph.exit().nodeId(), exceptionalEdges.getFirst().targetNodeId());
      assertEquals(2, arithmetic.operands().size());
    }
  }

  @Test
  void givenFloatingDivisionAndRemainder_whenGraphsAreBuilt_thenOperationsHaveNoArithmeticRoute() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("FloatingArithmetic.java"), """
        class FloatingArithmetic {
          float divideFloat(float left, float right) { return left / right; }
          double divideDouble(double left, double right) { return left / right; }
          float remainderFloat(float left, float right) { return left % right; }
          double remainderDouble(double left, double right) { return left % right; }
          double dividePromoted(int left, double right) { return left / right; }
        }
        """);

    // Act
    var input = analyze();
    var graphs = List.of("divideFloat", "divideDouble", "remainderFloat", "remainderDouble", "dividePromoted").stream()
        .map(name -> graph(input, name)).toList();

    // Assert
    for (var graph : graphs) {
      var arithmetic = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.BINARY).findFirst().orElseThrow();
      assertTrue(arithmetic.possibleExceptions().isEmpty());
      assertFalse(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(arithmetic.nodeId())
          && edge.transferKind() == TransferKind.EXCEPTIONAL));
      assertEquals(2, arithmetic.operands().size());
      assertTrue(graph.successors(arithmetic).stream().anyMatch(node -> node instanceof OperationNode operation
          && operation.kind() == OperationKind.RETURN));
    }
  }

  @Test
  void givenFloatingOperandsWithInvocationEffects_whenGraphIsBuilt_thenOperandEffectsAndOrderRemain() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("ArithmeticOperands.java"), """
        class ArithmeticOperands {
          double left() { return 1.0; }
          double right() { return 2.0; }
          double divide() { return left() / right(); }
        }
        """);

    // Act
    var graph = graph(analyze(), "divide");

    // Assert
    var left = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.INVOKE && node.detail().equals("left"))
        .findFirst().orElseThrow();
    var right = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.INVOKE && node.detail().equals("right"))
        .findFirst().orElseThrow();
    var division = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.BINARY).findFirst().orElseThrow();
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(left.nodeId())
        && edge.targetNodeId().equals(right.nodeId()) && edge.transferKind() == TransferKind.NORMAL));
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(right.nodeId())
        && edge.targetNodeId().equals(division.nodeId()) && edge.transferKind() == TransferKind.NORMAL));
    for (var invocation : List.of(left, right)) {
      assertTrue(invocation.possibleExceptions().contains("unchecked-invocation"));
      assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(invocation.nodeId())
          && edge.transferKind() == TransferKind.EXCEPTIONAL
          && edge.discriminator().filter("unchecked-invocation"::equals).isPresent()));
    }
    assertTrue(division.possibleExceptions().isEmpty());
  }

  @Test
  void givenEmptyTryBodies_whenGraphsAreBuilt_thenNormalFlowBypassesCatches() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("EmptyTry.java"), """
        class EmptyTry {
          void consume() { }
          void standalone() {
            try { } catch (RuntimeException failure) { consume(); }
          }
          void followed() {
            try { } catch (RuntimeException failure) { consume(); }
            consume();
          }
        }
        """);

    // Act
    var input = analyze();
    var standalone = graph(input, "standalone");
    var followed = graph(input, "followed");

    // Assert
    for (var graph : List.of(standalone, followed)) {
      var catchEntry = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH).findFirst().orElseThrow();
      var merge = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.MERGE).findFirst().orElseThrow();
      assertEquals(List.of(merge), graph.successors(graph.entry()));
      assertTrue(graph.predecessors(catchEntry).isEmpty());
    }
    var standaloneMerge = standalone.operationNodes().stream().filter(node -> node.kind() == OperationKind.MERGE)
        .findFirst().orElseThrow();
    assertEquals(List.of(standalone.exit()), standalone.successors(standaloneMerge));
    var followedMerge = followed.operationNodes().stream().filter(node -> node.kind() == OperationKind.MERGE)
        .findFirst().orElseThrow();
    var followingOperation = followed.successors(followedMerge).getFirst();
    assertTrue(followingOperation instanceof OperationNode operation && operation.kind() == OperationKind.INVOKE
        && operation.detail().equals("consume"));
    assertEquals(List.of(followed.exit(), followed.exit()), followed.successors(followingOperation));
    var followingEdges = followed.edges().stream().filter(edge -> edge.sourceNodeId().equals(followingOperation.nodeId())).toList();
    assertEquals(List.of(TransferKind.EXCEPTIONAL, TransferKind.NORMAL), followingEdges.stream().map(edge -> edge.transferKind()).toList());
    assertEquals("unchecked-invocation", followingEdges.getFirst().discriminator().orElseThrow());
  }

  @Test
  void givenBranchYields_whenGraphIsBuilt_thenBothValuesReachOnlyTheirSwitchMerge() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("BranchYields.java"), """
        class BranchYields {
          int first() { return 1; }
          int second() { return 2; }
          int third() { return 3; }
          int execute(int selector, boolean flag) {
            return switch (selector) {
              case 0 -> { if (flag) { yield first(); } else { yield second(); } }
              default -> third();
            };
          }
        }
        """);

    // Act
    var graph = graph(analyze(), "execute");

    // Assert
    var first = invocation(graph, "first");
    var second = invocation(graph, "second");
    var third = invocation(graph, "third");
    var merge = switchMerge(graph, List.of(first, second, third));
    for (var producer : List.of(first, second, third)) {
      assertNormalTarget(graph, producer, merge);
    }
    var branchMerge = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.MERGE && node.detail().equals("if"))
        .findFirst().orElseThrow();
    assertTrue(graph.predecessors(branchMerge).isEmpty());
    assertTrue(graph.successors(branchMerge).isEmpty());
    var selector = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.SWITCH_SELECTOR).findFirst().orElseThrow();
    var alternatives = graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(selector.nodeId()))
        .filter(edge -> edge.transferKind() == TransferKind.SWITCH_ALTERNATIVE).toList();
    assertEquals(2, alternatives.size());
    assertEquals("default", alternatives.getLast().discriminator().orElseThrow());
    assertEquals(third.nodeId(), alternatives.getLast().targetNodeId());
  }

  @Test
  void givenTryAndCatchYields_whenGraphIsBuilt_thenAbruptResultsBypassTheTryJoin() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("CatchYields.java"), """
        class CatchYields {
          int first() { return 1; }
          int second() { return 2; }
          int third() { return 3; }
          int execute(int selector) {
            return switch (selector) {
              case 0 -> { try { yield first(); } catch (RuntimeException failure) { yield second(); } }
              default -> third();
            };
          }
        }
        """);

    // Act
    var graph = graph(analyze(), "execute");

    // Assert
    var first = invocation(graph, "first");
    var second = invocation(graph, "second");
    var third = invocation(graph, "third");
    var merge = switchMerge(graph, List.of(first, second, third));
    for (var producer : List.of(first, second, third)) {
      assertNormalTarget(graph, producer, merge);
    }
    var catchEntry = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH).findFirst().orElseThrow();
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(first.nodeId())
        && edge.targetNodeId().equals(catchEntry.nodeId()) && edge.transferKind() == TransferKind.EXCEPTIONAL
        && edge.discriminator().filter("unchecked-invocation"::equals).isPresent()));
    assertNormalTarget(graph, catchEntry, second);
    var tryMerge = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.MERGE && node.detail().equals("try"))
        .findFirst().orElseThrow();
    assertTrue(graph.predecessors(tryMerge).isEmpty());
    assertTrue(graph.successors(tryMerge).isEmpty());
  }

  @Test
  void givenNestedAndSequentialSwitches_whenGraphsAreBuiltRepeatedly_thenYieldTargetsStayDistinct() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("NestedYields.java"), """
        class NestedYields {
          int first() { return 1; }
          int second() { return 2; }
          int third() { return 3; }
          int fourth() { return 4; }
          int fifth() { return 5; }
          int execute(int outer, int inner) {
            int selected = switch (outer) {
              case 0 -> {
                int nested = switch (inner) {
                  case 0 -> { yield first(); }
                  default -> { yield second(); }
                };
                yield nested;
              }
              default -> third();
            };
            return switch (inner) {
              case 0 -> { yield fourth(); }
              default -> { yield fifth(); }
            };
          }
        }
        """);

    // Act
    var input = analyze();
    var graph = graph(input, "execute");
    var repeated = graph(input, "execute");

    // Assert
    assertEquals(graph, repeated);
    var first = invocation(graph, "first");
    var second = invocation(graph, "second");
    var third = invocation(graph, "third");
    var fourth = invocation(graph, "fourth");
    var fifth = invocation(graph, "fifth");
    var innerMerge = switchMerge(graph, List.of(first, second));
    var finalMerge = switchMerge(graph, List.of(fourth, fifth));
    var nestedRead = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.READ && node.detail().equals("nested"))
        .findFirst().orElseThrow();
    var outerMerge = switchMerge(graph, List.of(nestedRead, third));
    assertEquals(3, Set.of(innerMerge.nodeId(), outerMerge.nodeId(), finalMerge.nodeId()).size());
    assertNormalTarget(graph, first, innerMerge);
    assertNormalTarget(graph, second, innerMerge);
    assertNormalTarget(graph, nestedRead, outerMerge);
    assertNormalTarget(graph, third, outerMerge);
    assertNormalTarget(graph, fourth, finalMerge);
    assertNormalTarget(graph, fifth, finalMerge);
    var nestedWrite = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.WRITE && node.detail().equals("nested"))
        .findFirst().orElseThrow();
    assertNormalTarget(graph, innerMerge, nestedWrite);
    assertNormalTarget(graph, nestedWrite, nestedRead);
    assertEquals(innerMerge.definedValue().orElseThrow(), nestedWrite.operands().getFirst().value());
  }

  @Test
  void givenNestedSwitchScopes_whenActionsReturnOrFail_thenTheEnclosingTargetIsRestored() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("SwitchScope.java"), "class SwitchScope { void execute() { } }");
    var input = analyze();
    var method = input.program().methods().stream().filter(candidate -> candidate.name().equals("execute")).findFirst().orElseThrow();
    var lowerer = new SyntaxGraphLowerer(method, input.methodSyntax(method.methodKey()).orElseThrow());
    var failure = new IllegalStateException("lowering interrupted");

    // Act
    var result = lowerer.withSwitchTarget("outer", () -> {
      assertEquals("outer", lowerer.yieldTarget());
      var nested = lowerer.withSwitchTarget("inner", lowerer::yieldTarget);
      assertEquals("inner", nested);
      assertEquals("outer", lowerer.yieldTarget());
      var thrown = assertThrows(IllegalStateException.class, () -> lowerer.withSwitchTarget("failing-inner", () -> {
        assertEquals("failing-inner", lowerer.yieldTarget());
        throw failure;
      }));
      assertSame(failure, thrown);
      return lowerer.yieldTarget();
    });

    // Assert
    assertEquals("outer", result);
    assertThrows(IllegalStateException.class, lowerer::yieldTarget);
    assertEquals("later", lowerer.withSwitchTarget("later", lowerer::yieldTarget));
    assertThrows(IllegalStateException.class, lowerer::yieldTarget);
  }

  private static OperationNode invocation(ControlFlowGraph graph, String name) {
    return graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.INVOKE && node.detail().equals(name))
        .findFirst().orElseThrow();
  }

  private static OperationNode switchMerge(ControlFlowGraph graph, List<OperationNode> producers) {
    var values = producers.stream().map(node -> node.definedValue().orElseThrow()).toList();
    var merges = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.MERGE && node.detail().equals("switch"))
        .filter(node -> node.operands().stream().map(operand -> operand.value()).toList().equals(values)).toList();
    assertEquals(1, merges.size());
    return merges.getFirst();
  }

  private static void assertNormalTarget(ControlFlowGraph graph, OperationNode source, OperationNode target) {
    var normal = graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(source.nodeId()))
        .filter(edge -> edge.transferKind() == TransferKind.NORMAL).toList();
    assertEquals(1, normal.size());
    assertEquals(target.nodeId(), normal.getFirst().targetNodeId());
    assertTrue(normal.getFirst().discriminator().isEmpty());
  }

  private SyntaxAnalysisInput analyze() throws IOException {
    var frontend = new JavaFrontend(new JavaSourceLoader());
    return frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
  }

  private static ControlFlowGraph graph(SyntaxAnalysisInput input, String methodName) {
    var method = input.program().methods().stream().filter(candidate -> candidate.name().equals(methodName)).findFirst().orElseThrow();
    return new ControlFlowGraphBuilder().build(method, input.methodSyntax(method.methodKey()).orElseThrow()).graph().orElseThrow();
  }

  private static void assertOperandEvaluationOrder(ControlFlowGraph graph, OperationNode write) {
    var operations = graph.operationNodes();
    var writePosition = operations.indexOf(write);
    var producerPositions = write.operands().stream().map(operand -> operations.stream()
        .filter(node -> node.definedValue().isPresent())
        .filter(node -> node.definedValue().orElseThrow().valueId().equals(operand.value().valueId()))
        .findFirst().map(operations::indexOf).orElse(-1)).toList();
    assertTrue(producerPositions.stream().allMatch(position -> position >= 0 && position < writePosition));
    for (var index = 1; index < producerPositions.size(); index++) {
      assertTrue(producerPositions.get(index - 1) < producerPositions.get(index));
    }
  }

  private static void assertInvocation(List<OperationNode> operations, String detail, InvocationForm form, InvocationMode mode,
      String declarationKey) {
    assertTrue(operations.stream().filter(node -> node.detail().equals(detail)).flatMap(node -> node.invocationSemantics().stream())
        .anyMatch(semantics -> semantics.form() == form && semantics.mode() == mode
            && semantics.qualifyingType().declarationKey().filter(declarationKey::equals).isPresent()));
  }

  private static long invocationCount(List<OperationNode> operations, String detail, InvocationForm form, InvocationMode mode,
      String declarationKey) {
    return operations.stream().filter(node -> node.detail().equals(detail)).flatMap(node -> node.invocationSemantics().stream())
        .filter(semantics -> semantics.form() == form && semantics.mode() == mode
            && semantics.qualifyingType().declarationKey().filter(declarationKey::equals).isPresent())
        .count();
  }
}
