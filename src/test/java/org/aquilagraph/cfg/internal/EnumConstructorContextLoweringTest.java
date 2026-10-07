package org.aquilagraph.cfg.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.cfg.api.ControlFlowGraph;
import org.aquilagraph.cfg.api.FlowValue;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.ValueKind;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class EnumConstructorContextLoweringTest {
  @TempDir
  Path sourceRoot;

  @Test
  void givenImplicitEnumConstructorWithSeveralConstants_whenFrontendAndBuilderRun_thenIncomingNameAndOrdinalInitializeEnumWithoutSourceArguments()
      throws IOException {
    // Arrange
    var source = "enum Status { FIRST, SECOND, THIRD }";

    // Act
    var input = analyze(source);
    var graph = constructor(input, "Status", 0);

    // Assert
    var method = input.program().types().getFirst().methods().stream().filter(candidate -> candidate.constructor()).findFirst().orElseThrow();
    var syntax = input.methodSyntax(method.methodKey()).orElseThrow();
    var call = syntax.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
    assertTrue(method.parameters().isEmpty());
    assertTrue(call.children(SyntaxRole.ARGUMENT).isEmpty());
    assertTrue(syntax.invocationArity(call.nodeId()).isEmpty());
    assertEquals(InvocationForm.SUPER_CONSTRUCTOR, syntax.constructorEvidence(call.nodeId()).orElseThrow().form());
    assertEnumInvocation(graph, InvocationForm.SUPER_CONSTRUCTOR, List.of());
    assertEquals(List.of(OperationKind.INVOKE), graph.operationNodes().stream().map(operation -> operation.kind()).toList());
  }

  @Test
  void givenWrittenEnumConstructor_whenFrontendAndBuilderRun_thenSourceParameterRemainsSeparateFromEnumContext() throws IOException {
    // Arrange
    var source = "enum Amount { ONE(1), TWO(2); Amount(int amount) {} }";

    // Act
    var input = analyze(source);
    var graph = constructor(input, "Amount", 1);

    // Assert
    var parameters = graph.values().stream().filter(value -> value.kind() == ValueKind.PARAMETER).toList();
    assertEquals(1, parameters.size());
    assertEquals(Optional.of("amount"), parameters.getFirst().displayName());
    assertEnumInvocation(graph, InvocationForm.SUPER_CONSTRUCTOR, List.of());
    assertFalse(graph.operationNodes().stream().anyMatch(operation -> operation.kind() == OperationKind.CONSTANT));
  }

  @Test
  void givenEnumConstructorDelegationChain_whenFrontendAndBuilderRun_thenEachThisInvocationForwardsContextBeforeItsOrdinaryArgument()
      throws IOException {
    // Arrange
    var source = """
        enum Delegated {
          FIRST, SECOND;
          Delegated() { this(7); }
          Delegated(int input) { this((long)input); }
          Delegated(long input) {}
        }
        """;

    // Act
    var input = analyze(source);
    var methods = input.program().types().getFirst().methods().stream().filter(method -> method.constructor()).toList();

    // Assert
    assertEquals(3, methods.size());
    for (var method : methods) {
      var syntax = input.methodSyntax(method.methodKey()).orElseThrow();
      assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(syntax));
      var graph = new ControlFlowGraphBuilder().build(method, syntax).graph().orElseThrow();
      var context = syntax.evidence().constructors().getFirst();
      if (method.signature().equals("<init>(long)")) {
        assertEquals(InvocationForm.SUPER_CONSTRUCTOR, context.form());
        assertEnumInvocation(graph, InvocationForm.SUPER_CONSTRUCTOR, List.of());
      } else {
        var call = syntax.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
        assertEquals(InvocationForm.THIS_CONSTRUCTOR, context.form());
        assertEquals(1, syntax.invocationArity(call.nodeId()).orElseThrow().formalCount());
        assertEquals(1, call.children(SyntaxRole.ARGUMENT).size());
        var invoke = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.INVOKE).findFirst().orElseThrow();
        assertEquals(4, invoke.operands().size());
        assertEnumInvocation(graph, InvocationForm.THIS_CONSTRUCTOR, List.of(invoke.operands().getLast().value()));
      }
    }
  }

  @Test
  void givenVarargsEnumDelegationAndConstantSpecificBody_whenFrontendAndBuilderRun_thenContextIsDistinctFromOrderedVarargsPacking()
      throws IOException {
    // Arrange
    var source = """
        enum Choices {
          FIRST { int code() { return 1; } }, SECOND;
          Choices() { this(1, 2); }
          Choices(int... values) {}
          int code() { return 0; }
        }
        """;

    // Act
    var input = analyze(source);
    var graph = constructor(input, "Choices", 0);

    // Assert
    var method = input.program().types().stream().filter(type -> type.qualifiedName().equals("Choices")).findFirst().orElseThrow()
        .methods().stream().filter(candidate -> candidate.constructor() && candidate.parameters().isEmpty()).findFirst().orElseThrow();
    var syntax = input.methodSyntax(method.methodKey()).orElseThrow();
    var call = syntax.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, syntax.invocationArity(call.nodeId()).orElseThrow().mode());
    assertEquals(1, syntax.invocationArity(call.nodeId()).orElseThrow().formalCount());
    assertEquals(2, call.children(SyntaxRole.ARGUMENT).size());
    var allocation = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.VARARGS_ARRAY).findFirst().orElseThrow();
    assertEnumInvocation(graph, InvocationForm.THIS_CONSTRUCTOR, List.of(allocation.definedValue().orElseThrow()));
    var writes = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.WRITE).toList();
    assertEquals(List.of(Optional.of("0"), Optional.of("1")), writes.stream().map(operation -> operation.operands().get(1).value().literal()).toList());
    assertEquals(List.of(Optional.of("1"), Optional.of("2")), writes.stream().map(operation -> operation.operands().get(2).value().literal()).toList());
    assertEnumInvocation(constructor(input, "Choices", 1), InvocationForm.SUPER_CONSTRUCTOR, List.of());
  }

  private SyntaxAnalysisInput analyze(String content) throws IOException {
    var source = SourceFile.fromContent(Path.of("Enums.java"), content);
    return new JavaFrontend(new JavaSourceLoader()).analyze(InputSnapshot.fromSources(sourceRoot, List.of(source)),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
  }

  private static ControlFlowGraph constructor(SyntaxAnalysisInput input, String owner, int sourceParameters) {
    var method = input.program().types().stream().filter(type -> type.qualifiedName().equals(owner)).findFirst().orElseThrow()
        .methods().stream().filter(candidate -> candidate.constructor() && candidate.parameters().size() == sourceParameters)
        .findFirst().orElseThrow();
    var syntax = input.methodSyntax(method.methodKey()).orElseThrow();
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(syntax));
    return new ControlFlowGraphBuilder().build(method, syntax).graph().orElseThrow();
  }

  private static void assertEnumInvocation(ControlFlowGraph graph, InvocationForm form, List<FlowValue> sourceArguments) {
    var invoke = graph.operationNodes().stream().filter(operation -> operation.kind() == OperationKind.INVOKE).findFirst().orElseThrow();
    assertEquals(form, invoke.invocationSemantics().orElseThrow().form());
    var operands = invoke.operands().stream().map(operand -> operand.value()).toList();
    assertEquals(3 + sourceArguments.size(), operands.size());
    assertEquals(ValueKind.RECEIVER, operands.getFirst().kind());
    assertEquals(List.of(Optional.of("enum-name"), Optional.of("enum-ordinal")), operands.subList(1, 3).stream().map(FlowValue::displayName).toList());
    assertEquals(List.of("java.lang.String", "int"), operands.subList(1, 3).stream().map(value -> value.staticType().orElseThrow().erasedName()).toList());
    assertTrue(operands.subList(1, 3).stream().allMatch(value -> value.kind() == ValueKind.IMPLICIT_PARAMETER && value.literal().isEmpty()
        && value.graphId().equals(graph.graphId()) && graph.values().contains(value)));
    assertEquals(sourceArguments, operands.subList(3, operands.size()));
    assertTrue(graph.operationNodes().stream().noneMatch(operation -> operation.definedValue().filter(operands.subList(1, 3)::contains).isPresent()));
    assertTrue(graph.operationNodes().stream().noneMatch(operation -> operation.kind() == OperationKind.NEW_ALLOCATION));
  }
}
