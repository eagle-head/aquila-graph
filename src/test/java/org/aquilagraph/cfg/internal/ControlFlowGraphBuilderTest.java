package org.aquilagraph.cfg.internal;

import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.cfg.api.ValueKind;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.MethodSyntax;
import org.aquilagraph.source.api.MethodSyntaxFixture;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.ConversionEvidence;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class ControlFlowGraphBuilderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenNestedInvocations_whenCfgIsBuilt_thenLeftToRightEvaluationOrderIsPreserved()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Order.java"),
        """
        class Order {
          int first() { return 1; }
          int second() { return 2; }
          int combined() { return first() + second(); }
        }
        """);
    var method = method("combined");

    // Act
    var result = build(method);

    // Assert
    var operations = result.graph().orElseThrow().operationNodes();
    var invocationPositions =
        IntStream.range(0, operations.size())
            .filter(index -> operations.get(index).kind() == OperationKind.INVOKE)
            .boxed()
            .toList();
    assertEquals(2, invocationPositions.size());
    var binaryPosition =
        IntStream.range(0, operations.size())
            .filter(index -> operations.get(index).kind() == OperationKind.BINARY)
            .findFirst()
            .orElseThrow();
    assertTrue(invocationPositions.get(0) < invocationPositions.get(1));
    assertTrue(invocationPositions.get(1) < binaryPosition);
  }

  @Test
  void givenElementFreeBinaryExpression_whenCfgIsBuilt_thenResolvedTypeReachesDefinedFlowValue() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("TypedValue.java"),
        "final class TypedValue { long calculate() { return 1L + 2L; } }");
    var method = method("calculate");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var binary = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.BINARY).findFirst().orElseThrow();
    var type = binary.definedValue().orElseThrow().staticType().orElseThrow();
    assertEquals(JavaTypeCategory.LONG, type.category());
    assertEquals("long", type.erasedName());
  }

  @Test
  void givenUnresolvedExpressionType_whenBodyReadinessIsAssessed_thenUnresolvedSemanticsPreventLowering() {
    // Arrange
    var method = syntheticMethod(SyntaxType.Unresolved.INSTANCE);
    // Act
    var readiness = CfgAdmission.classify(method.syntax());
    // Assert
    assertEquals(CfgReadiness.UNRESOLVED_SEMANTICS, readiness);
  }

  @Test
  void givenErroneousExpressionType_whenBodyReadinessIsAssessed_thenErroneousSemanticsPreventLowering() {
    // Arrange
    var method = syntheticMethod(SyntaxType.Erroneous.INSTANCE);
    // Act
    var readiness = CfgAdmission.classify(method.syntax());
    // Assert
    assertEquals(CfgReadiness.ERRONEOUS_SEMANTICS, readiness);
  }

  @Test
  void givenAdmittedSourceAndAPlan_whenBuilderReceivesNullOrForeignQualification_thenItRejectsBeforeConstruction() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example { int value() { return 1; } }");
    var original = method("value");
    var ast = original.source();
    var plan = CfgAdmission.assess(ast, original.declaration().methodKey(), CfgProfile.JAVA_BOUNDED).plan().orElseThrow();
    Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example { int value() { return 2; } }");
    var foreign = method("value").source();
    var builder = new ControlFlowGraphBuilder();
    // Act
    var missingSource = assertThrows(NullPointerException.class, () -> builder.build(null, plan));
    var missingPlan = assertThrows(NullPointerException.class, () -> builder.build(ast, null));
    // Assert
    assertEquals("ast", missingSource.getMessage());
    assertEquals("plan", missingPlan.getMessage());
    assertThrows(IllegalArgumentException.class, () -> builder.build(foreign, plan));
  }

  @Test
  void givenErroneousAndUnresolvedFacts_whenReadinessIsAssessed_thenErroneousSemanticsHavePrecedence() {
    // Arrange
    var erroneous = syntheticMethod(SyntaxType.Erroneous.INSTANCE);
    var unresolvedLiteral = syntaxNode("mixed-unresolved-literal", JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL,
        SyntaxType.Unresolved.INSTANCE, new SyntaxValue.Literal("2"), List.of(), Optional.empty());
    var unresolvedReturn = syntaxNode("mixed-unresolved-return", JavaSourceKind.RETURN, SyntaxKind.RETURN,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, unresolvedLiteral)), Optional.empty());
    var root = syntaxNode("mixed-root", JavaSourceKind.BLOCK, SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE,
        List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, erroneous.syntax().root().children(SyntaxRole.STATEMENT).getFirst()),
            new SyntaxChild(SyntaxRole.STATEMENT, 1, unresolvedReturn)), Optional.empty());
    var original = erroneous.declaration();
    var method = new JavaMethod(original.methodKey(), original.ownerTypeKey(), original.name(), original.signature(),
        original.returnType(), original.parameters(), original.modifiers(), original.constructor(), original.sourceDeclared(), original.hasBody(),
        List.of(new SourceSpan("source", 0, 1)));
    var syntax = MethodSyntaxFixture.view(method.methodKey(), root);

    // Act
    var readiness = CfgAdmission.classify(syntax);
    // Assert
    assertEquals(CfgReadiness.ERRONEOUS_SEMANTICS, readiness);
  }

  @Test
  void givenUnsupportedBodyWithErroneousFacts_whenAdmissionIsAssessed_thenUnsupportedReadinessPrecedesErrorsAndPublicRefusalRetainsCauses() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("UnsupportedReadiness.java"), """
        class UnsupportedReadiness {
          int execute() {
            while (true) { return missingValue; }
          }
        }
        """);
    var method = method("execute");

    // Act
    var result = build(method);

    // Assert
    assertTrue(result.graph().isEmpty());
    assertEquals(CfgReadiness.UNSUPPORTED, CfgAdmission.classify(method.syntax()));
    assertEquals(MethodCfgState.UNSUPPORTED, result.state());
    assertEquals("control-flow.unsupported", result.diagnostics().getFirst().code());
    assertTrue(result.blockers().stream().anyMatch(blocker -> blocker.kind() == MethodCfgState.ERRONEOUS_EVIDENCE));
  }

  @Test
  void givenBinaryChildrenStoredOutOfOrder_whenCfgIsBuilt_thenTypedRolesDetermineEvaluationOrder() {
    // Arrange
    var left = syntaxLeaf("left", SyntaxKind.LITERAL, new SyntaxValue.Literal("1"));
    var right = syntaxLeaf("right", SyntaxKind.LITERAL, new SyntaxValue.Literal("2"));
    var binary =
        syntaxNode(
            "binary",
            JavaSourceKind.PLUS,
            SyntaxKind.BINARY,
            resolvedIntType(),
            new SyntaxValue.Operator(SyntaxOperator.PLUS),
            List.of(
                new SyntaxChild(SyntaxRole.RIGHT_OPERAND, 0, right),
                new SyntaxChild(SyntaxRole.LEFT_OPERAND, 0, left)),
            Optional.empty());
    var returnNode =
        syntaxNode(
            "return",
            JavaSourceKind.RETURN,
            SyntaxKind.RETURN,
            SyntaxType.NotApplicable.INSTANCE,
            SyntaxValue.None.INSTANCE,
            List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, binary)),
            Optional.empty());
    var root =
        syntaxNode(
            "root",
            JavaSourceKind.BLOCK,
            SyntaxKind.BLOCK,
            SyntaxType.NotApplicable.INSTANCE,
            SyntaxValue.None.INSTANCE,
            List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, returnNode)),
            Optional.empty());
    var declaration =
        new JavaMethod(
            "type:RoleOrder#evaluate()",
            "type:RoleOrder",
            "evaluate",
            "evaluate()",
            "int",
            List.of(),
            Set.of(),
            false,
            true,
            true,
            List.of());
    var integer = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");
    var syntax = MethodSyntaxFixture.view(declaration.methodKey(), root, execution -> {
      var statement = execution.requiredChild(SyntaxRole.STATEMENT);
      var expression = statement.requiredChild(SyntaxRole.EXPRESSION);
      return List.of(new ConversionEvidence(declaration.methodKey(), expression.nodeId(), SyntaxRole.LEFT_OPERAND, 0, integer, integer,
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
          new ConversionEvidence(declaration.methodKey(), expression.nodeId(), SyntaxRole.RIGHT_OPERAND, 0, integer, integer,
              Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
          new ConversionEvidence(declaration.methodKey(), statement.nodeId(), SyntaxRole.EXPRESSION, 0, integer, integer,
              Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    });

    // Act
    var graph = new SyntaxGraphLowerer(declaration, syntax).lower().content();

    // Assert
    var constants =
        graph.operationNodes().stream()
            .filter(node -> node.kind() == OperationKind.CONSTANT)
            .map(node -> node.definedValue().orElseThrow().literal().orElseThrow())
            .toList();
    assertEquals(List.of("1", "2"), constants);
  }

  @Test
  void givenShortCircuitAndThrow_whenCfgIsBuilt_thenConditionalAndAbruptTransfersAreExplicit()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Flow.java"),
        """
        class Flow {
          boolean right() { return true; }
          int test(boolean left) {
            if (left && right()) { throw new IllegalStateException(); }
            return 1;
          }
        }
        """);
    var method = method("test");

    // Act
    var result = build(method);
    var graph = result.graph().orElseThrow();

    // Assert
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.transferKind() == TransferKind.TRUE_BRANCH));
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.transferKind() == TransferKind.FALSE_BRANCH));
    assertTrue(graph.operationNodes().stream().anyMatch(node -> node.kind() == OperationKind.THROW));
    assertTrue(graph.edges().stream().anyMatch(edge -> edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenKnownExceptionAndOrderedCatches_whenCfgIsBuilt_thenOnlyFirstMatchingCatchReceivesTheTransfer()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("CatchOrder.java"),
        """
        class CatchOrder {
          void execute() {
            try {
              throw new IllegalArgumentException();
            } catch (IllegalArgumentException expected) {
            } catch (RuntimeException fallback) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var expectedCatch = graph.operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.CATCH && node.detail().endsWith("IllegalArgumentException"))
        .findFirst().orElseThrow();
    var exceptionalTransfers = graph.edges().stream()
        .filter(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.transferKind() == TransferKind.EXCEPTIONAL).toList();
    assertEquals(1, exceptionalTransfers.size());
    assertEquals(expectedCatch.nodeId(), exceptionalTransfers.getFirst().targetNodeId());
  }

  @Test
  void givenNestedTryWithUnmatchedInnerCatch_whenCfgIsBuilt_thenExceptionReachesOuterCatch()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("NestedTry.java"),
        """
        class NestedTry {
          void execute() {
            try {
              try {
                throw new IllegalStateException();
              } catch (IllegalArgumentException ignored) {
              }
            } catch (IllegalStateException expected) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var outerCatch = graph.operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.CATCH && node.detail().endsWith("IllegalStateException"))
        .findFirst().orElseThrow();
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(outerCatch.nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
    assertFalse(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(graph.exit().nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenUserDefinedExceptionHierarchy_whenCfgIsBuilt_thenNearestAssignableCatchReceivesTheTransfer()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("ExceptionHierarchy.java"),
        """
        class ExceptionHierarchy {
          static class BaseFailure extends RuntimeException {
          }

          static class SpecificFailure extends BaseFailure {
          }

          void execute() {
            try {
              throw new SpecificFailure();
            } catch (BaseFailure expected) {
            } catch (RuntimeException fallback) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var expectedCatch = graph.operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.CATCH && node.detail().endsWith("BaseFailure"))
        .findFirst().orElseThrow();
    var exceptionalTransfers = graph.edges().stream()
        .filter(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.transferKind() == TransferKind.EXCEPTIONAL).toList();
    assertEquals(1, exceptionalTransfers.size());
    assertEquals(expectedCatch.nodeId(), exceptionalTransfers.getFirst().targetNodeId());
  }

  @Test
  void givenMultiCatch_whenCfgIsBuilt_thenEachCompilerResolvedAlternativeCanHandleTheException()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("MultiCatch.java"),
        """
        class MultiCatch {
          void execute() {
            try {
              throw new IllegalStateException();
            } catch (IllegalArgumentException | IllegalStateException expected) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var catchNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH).findFirst().orElseThrow();
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(catchNode.nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenMultiCatchWithUnprovenAlternativeRelationships_whenCfgIsBuilt_thenCommonSupertypeCannotRemoveTheResidualExit()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("UnmatchedMultiCatch.java"),
        """
        class UnmatchedMultiCatch {
          void execute() {
            try {
              throw new NullPointerException();
            } catch (IllegalArgumentException | IllegalStateException unrelated) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var catchNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH).findFirst().orElseThrow();
    assertEquals(List.of("java.lang.NullPointerException"), throwNode.possibleExceptions());
    assertEquals(List.of("java.lang.IllegalArgumentException", "java.lang.IllegalStateException"),
        method.syntax().root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.CATCH)
            .findFirst().orElseThrow().requiredChild(SyntaxRole.PARAMETER).requiredReference().typeAlternativeNames());
    assertEquals(2, graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(throwNode.nodeId())).count());
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(catchNode.nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(graph.exit().nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenQualifiedHomonymousExceptionsWithUnprovenRelationship_whenCfgIsBuilt_thenPossibleCatchCannotRemoveTheResidualExit()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("QualifiedException.java"),
        """
        class QualifiedException {
          static class Left {
            static class Failure extends RuntimeException {
            }
          }

          static class Right {
            static class Failure extends RuntimeException {
            }
          }

          void execute() {
            try {
              throw new Right.Failure();
            } catch (Left.Failure unrelated) {
            }
          }
        }
        """);
    var method = method("execute");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var throwNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.THROW).findFirst().orElseThrow();
    var catchNode = graph.operationNodes().stream().filter(node -> node.kind() == OperationKind.CATCH).findFirst().orElseThrow();
    assertEquals(List.of("QualifiedException.Right.Failure"), throwNode.possibleExceptions());
    assertEquals(List.of("QualifiedException.Left.Failure"), method.syntax().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.CATCH).findFirst().orElseThrow()
        .requiredChild(SyntaxRole.PARAMETER).requiredReference().typeAlternativeNames());
    assertEquals(2, graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(throwNode.nodeId())).count());
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(catchNode.nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
    assertTrue(graph.edges().stream()
        .anyMatch(edge -> edge.sourceNodeId().equals(throwNode.nodeId()) && edge.targetNodeId().equals(graph.exit().nodeId())
            && edge.transferKind() == TransferKind.EXCEPTIONAL));
  }

  @Test
  void givenSameMethod_whenCfgIsBuiltTwice_thenGraphIdentityAndStructureAreStable()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Stable.java"),
        """
        class Stable {
          int choose(boolean condition) {
            return condition ? 1 : 2;
          }
        }
        """);
    var method = method("choose");

    // Act
    var first = build(method);
    var second = build(method);

    // Assert
    assertEquals(first, second);
  }

  @Test
  void givenBlockLocalShadowingAField_whenCfgIsBuilt_thenLocalBindingDoesNotEscapeItsScope()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("LexicalScope.java"),
        """
        class LexicalScope {
          int value;

          int read() {
            {
              int value = 1;
            }
            return value;
          }
        }
        """);
    var method = method("read");

    // Act
    var graph = build(method).graph().orElseThrow();

    // Assert
    var fieldRead = graph.operationNodes().stream()
        .filter(node -> node.kind() == OperationKind.MEMBER_ACCESS && node.detail().equals("value"))
        .findFirst().orElseThrow();
    assertEquals(ValueKind.RECEIVER, fieldRead.operands().getFirst().value().kind());
  }

  private static MethodCfgOutcome build(MethodInput method) {
    return CfgDerivers.standard().derive(method.source(), new CfgRequest(method.source().identity(), List.of(method.declaration().methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();
  }

  private static SyntaxNode syntaxLeaf(String nodeId, SyntaxKind kind, SyntaxValue value) {
    return syntaxNode(nodeId, JavaSourceKind.INT_LITERAL, kind, resolvedIntType(), value, List.of(), Optional.empty());
  }

  private static SyntaxType resolvedIntType() {
    return new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"));
  }

  private static SyntheticMethod syntheticMethod(SyntaxType expressionType) {
    var literal = syntaxNode("literal", JavaSourceKind.INT_LITERAL, SyntaxKind.LITERAL, expressionType,
        new SyntaxValue.Literal("1"), List.of(), Optional.empty());
    var returnNode = syntaxNode("return", JavaSourceKind.RETURN, SyntaxKind.RETURN, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, literal)), Optional.empty());
    var root = syntaxNode("root", JavaSourceKind.BLOCK, SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, returnNode)), Optional.empty());
    var declaration = new JavaMethod("type:Synthetic#value()", "type:Synthetic", "value", "value()", "int", List.of(), Set.of(),
        false, true, true, List.of());
    return new SyntheticMethod(declaration, MethodSyntaxFixture.view(declaration.methodKey(), root));
  }

  private static SyntaxNode syntaxNode(String identity, JavaSourceKind sourceKind, SyntaxKind kind, SyntaxType type,
      SyntaxValue value, List<SyntaxChild> children, Optional<SemanticReference> reference) {
    var path = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.STATEMENT,
        Math.floorMod(identity.hashCode(), Integer.MAX_VALUE));
    return SyntaxNode.withoutDirectSource("source", path, sourceKind, kind, type, value, children, reference);
  }

  private MethodInput method(String name) throws IOException {
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var result = frontend.produce(new AstProductionRequest(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var declaration = result.program().methods().stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
    return new MethodInput(declaration, result.aggregate().methodSyntax(declaration.methodKey()).orElseThrow(), result);
  }

  private record SyntheticMethod(JavaMethod declaration, MethodSyntax syntax) {
  }

  private record MethodInput(JavaMethod declaration, MethodSyntax syntax, AstResult source) {
  }
}
