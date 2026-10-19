package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.cfg.api.FlowValue;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.MethodSyntax;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.InvocationApplicability;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;

final class SyntaxGraphLowerer {
  private final MethodSyntax methodSyntax;
  private final MethodLoweringPlan plan;
  private final ControlFlowGraphConstruction graph;
  private final StatementGraphLowerer statements;
  private final ExpressionGraphLowerer expressions;
  private final UseConversionLowerer conversions;
  private final Deque<String> switchTargets = new ArrayDeque<>();

  SyntaxGraphLowerer(JavaMethod method, MethodSyntax methodSyntax) {
    this(method, methodSyntax, Optional.empty());
  }

  SyntaxGraphLowerer(JavaMethod method, MethodSyntax methodSyntax, Optional<GraphQualification> qualification) {
    Objects.requireNonNull(method, "method");
    this.methodSyntax = Objects.requireNonNull(methodSyntax, "methodSyntax");
    plan = MethodLoweringPlan.from(methodSyntax);
    graph = new ControlFlowGraphConstruction(method, qualification);
    conversions = new UseConversionLowerer(graph);
    var values = new FlowValueScope();
    for (var parameter : method.parameters()) {
      values.bind(parameter.parameterKey(), graph.parameterValue(parameter));
    }
    statements = new StatementGraphLowerer(this, graph, values);
    expressions = new ExpressionGraphLowerer(this, graph, values, methodSyntax.source());
  }

  ControlFlowGraphResult lower() {
    var fragment = graph.sequence(insertedSuperclassPrefix(), lower(methodSyntax.root()));
    return new ControlFlowGraphResult(Optional.of(graph.complete(fragment)), List.of());
  }

  private GraphFragment insertedSuperclassPrefix() {
    if (methodSyntax.source().methodBodyOrigin(methodSyntax.methodKey()).orElseThrow() != MethodBodyOrigin.SOURCE_AST) {
      return GraphFragment.empty();
    }
    var evidence = Optional.ofNullable(plan.constructors().get(methodSyntax.root().nodeId()));
    if (evidence.isEmpty()) {
      return GraphFragment.empty();
    }
    var selected = evidence.orElseThrow();
    var insertion = selected.corroboration().knownValue().orElseThrow().insertedInvocation().orElseThrow();
    var operands = new ArrayList<FlowValue>();
    operands.add(graph.insertedReceiver(insertion));
    var prefix = GraphFragment.empty();
    if (isEnumOwner(selected)) {
      var binding = Objects.requireNonNull(plan.enumBindings().get(selected.invocationNodeId()), "admitted enum binding plan");
      operands.add(graph.enumContextValue(binding.enumOwnerKey(), "enum-name", binding.nameType()));
      operands.add(graph.enumContextValue(binding.enumOwnerKey(), "enum-ordinal", binding.ordinalType()));
    } else {
      var applicability = Objects.requireNonNull(plan.invocations().get(selected.invocationNodeId()), "inserted invocation plan");
      if (applicability.mode() == InvocationApplicabilityMode.VARIABLE) {
        var allocationId = graph.insertedOperationId(insertion, "varargs-array");
        var array = graph.temporaryValue(allocationId, "varargs", applicability.erasedArrayType());
        var allocation = graph.insertedOperation(allocationId, methodSyntax.root(), "compiler-inserted-superclass/varargs-array",
            OperationKind.VARARGS_ARRAY, "varargs", List.of(), Optional.of(array), List.of(UseConversionLowerer.allocationFailure()),
            Optional.empty(), List.of());
        prefix = GraphFragment.value(allocation.nodeId(), array);
        operands.add(array);
      }
    }
    var operationId = graph.insertedOperationId(insertion, "invoke");
    var invocation = graph.insertedOperation(operationId, methodSyntax.root(), "compiler-inserted-superclass/invoke", OperationKind.INVOKE,
        "<init>", operands, Optional.empty(), List.of(ExceptionType.unknown(ExceptionTarget.UNKNOWN_INVOCATION_EXCEPTION)),
        Optional.of(insertion.semantics()), List.of(insertion.reference()));
    graph.connectExits(prefix, invocation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(prefix.entry().or(() -> Optional.of(invocation.nodeId())), List.of(invocation.nodeId()), Optional.empty());
  }

  private boolean isEnumOwner(ConstructorEvidence evidence) {
    return plan.enumBindings().containsKey(evidence.invocationNodeId());
  }

  GraphFragment lowerUse(SyntaxNode consumer, SyntaxRole role) {
    return lowerUse(consumer, role, 0);
  }

  GraphFragment lowerUse(SyntaxNode consumer, SyntaxRole role, int position) {
    var child = consumer.children().stream().filter(candidate -> candidate.role() == role && candidate.position() == position).findFirst()
        .orElseThrow(() -> new IllegalStateException("An admitted use requires its exact direct child"));
    return convertUse(consumer, role, position, lower(child.node()));
  }

  GraphFragment convertUse(SyntaxNode consumer, SyntaxRole role, int position, GraphFragment input) {
    return conversions.convert(consumer, useDecision(consumer, role, position), input);
  }

  ConversionEvidence useDecision(SyntaxNode consumer, SyntaxRole role, int position) {
    return Objects.requireNonNull(plan.conversions().get(new MethodLoweringPlan.Use(consumer.nodeId(), role, position)),
        "admitted conversion plan");
  }

  boolean referenceCheckRequired(SyntaxNode consumer) {
    return plan.referenceChecks().getOrDefault(new MethodLoweringPlan.Use(consumer.nodeId(), SyntaxRole.EXPRESSION, 0), false);
  }

  InvocationApplicability invocationArity(SyntaxNode invocation) {
    return Objects.requireNonNull(plan.invocations().get(invocation.nodeId()), "admitted invocation plan");
  }

  List<FlowValue> enumConstructorContextOperands(SyntaxNode invocation) {
    var binding = plan.enumBindings().get(invocation.nodeId());
    if (binding == null) {
      return List.of();
    }
    var name = graph.enumContextValue(binding.enumOwnerKey(), "enum-name", binding.nameType());
    var ordinal = graph.enumContextValue(binding.enumOwnerKey(), "enum-ordinal", binding.ordinalType());
    if (binding.form() == InvocationForm.THIS_CONSTRUCTOR) {
      return List.of(graph.receiverValue(invocation, "this", Optional.empty()), name, ordinal);
    }
    return List.of(name, ordinal);
  }

  StaticMemberEvidence staticAccess(SyntaxNode access) {
    return Objects.requireNonNull(plan.staticMembers().get(access.nodeId()), "admitted static member plan");
  }

  StringExpressionEvidence stringConcatenation(SyntaxNode binary) {
    return Objects.requireNonNull(plan.strings().get(binary.nodeId()), "admitted String expression plan");
  }

  boolean classInitializationRequired(SyntaxNode access) {
    return Objects.requireNonNull(plan.initialization().get(access.nodeId()), "admitted initialization plan");
  }

  StringOperandPlan stringOperandPlan(SyntaxNode binary) {
    return Objects.requireNonNull(plan.stringOperands().get(binary.nodeId()), "admitted String operand plan");
  }

  String yieldTarget() {
    if (switchTargets.isEmpty()) {
      throw new IllegalStateException("A yield requires an enclosing switch expression");
    }
    return switchTargets.getFirst();
  }

  <T> T withSwitchTarget(String switchNodeId, Supplier<T> action) {
    Objects.requireNonNull(switchNodeId, "switchNodeId");
    Objects.requireNonNull(action, "action");
    switchTargets.push(switchNodeId);
    try {
      return action.get();
    } finally {
      switchTargets.pop();
    }
  }

  GraphFragment lower(SyntaxNode node) {
    Objects.requireNonNull(node, "node");
    return switch (node.syntaxKind()) {
      case BLOCK, LOCAL_DECLARATION, EXPRESSION_STATEMENT, RETURN, THROW, IF, TRY, YIELD, EMPTY -> statements.lower(node);
      case LITERAL, VARIABLE_ACCESS, FIELD_ACCESS, RECEIVER, ARRAY_LENGTH, ARRAY_ACCESS, INVOCATION, OBJECT_CREATION, UNARY, BINARY,
          CONDITIONAL_AND, CONDITIONAL_OR, ASSIGNMENT, PARENTHESIZED, TYPE_CAST, CONDITIONAL_EXPRESSION, SWITCH_EXPRESSION ->
        expressions.lower(node);
      default -> throw new IllegalStateException("Syntax reached graph lowering without a semantic contract: " + node.syntaxKind());
    };
  }
}
