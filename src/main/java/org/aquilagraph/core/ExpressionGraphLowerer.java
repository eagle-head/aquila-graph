package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.OperationKind;
import org.aquilagraph.model.TransferKind;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxOperator;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxValue;

final class ExpressionGraphLowerer {
  private static final String NULL_POINTER_EXCEPTION = "java.lang.NullPointerException";

  private final SyntaxGraphLowerer syntax;
  private final ControlFlowGraphConstruction graph;
  private final FlowValueScope values;
  private final FieldReceiverLowerer fieldReceivers;
  private final AssignmentLocationLowerer locations;

  ExpressionGraphLowerer(SyntaxGraphLowerer syntax, ControlFlowGraphConstruction graph, FlowValueScope values) {
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.graph = Objects.requireNonNull(graph, "graph");
    this.values = Objects.requireNonNull(values, "values");
    fieldReceivers = new FieldReceiverLowerer(syntax, graph);
    locations = new AssignmentLocationLowerer(syntax, graph, values, fieldReceivers);
  }

  GraphFragment lower(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case LITERAL -> buildLiteral(node);
      case VARIABLE_ACCESS -> buildVariableRead(node);
      case FIELD_ACCESS -> buildFieldRead(node);
      case RECEIVER -> buildReceiver(node);
      case ARRAY_LENGTH ->
        buildUnaryLike(node, SyntaxRole.QUALIFIER, OperationKind.ARRAY_LENGTH, runtimeExceptions(NULL_POINTER_EXCEPTION));
      case ARRAY_ACCESS -> buildNary(node, List.of(node.requiredChild(SyntaxRole.ARRAY), node.requiredChild(SyntaxRole.INDEX)),
          OperationKind.ARRAY_ACCESS, runtimeExceptions(NULL_POINTER_EXCEPTION, "java.lang.ArrayIndexOutOfBoundsException"));
      case INVOCATION -> buildInvocation(node);
      case OBJECT_CREATION -> buildObjectCreation(node);
      case UNARY -> buildUnaryLike(node, SyntaxRole.OPERAND, OperationKind.UNARY, List.of());
      case BINARY -> buildBinary(node);
      case CONDITIONAL_AND -> buildShortCircuit(node, true);
      case CONDITIONAL_OR -> buildShortCircuit(node, false);
      case ASSIGNMENT -> buildAssignment(node);
      case PARENTHESIZED -> syntax.lower(node.requiredChild(SyntaxRole.EXPRESSION));
      case TYPE_CAST -> buildUnaryLike(node, SyntaxRole.EXPRESSION, OperationKind.CAST, runtimeExceptions("java.lang.ClassCastException"));
      case CONDITIONAL_EXPRESSION -> buildConditionalExpression(node);
      case SWITCH_EXPRESSION -> buildSwitchExpression(node);
      default -> throw new IllegalArgumentException("Expected expression syntax but found " + node.syntaxKind());
    };
  }

  private GraphFragment buildLiteral(SyntaxNode node) {
    var literal = node.value(SyntaxValue.Literal.class).text();
    var operationNodeId = graph.operationNodeId(node, "constant");
    var value = graph.constantValue(operationNodeId, literal, node.resolvedType());
    var operation = graph.addOperationWithId(operationNodeId, node, OperationKind.CONSTANT, literal, List.of(), Optional.of(value),
        List.of());
    return GraphFragment.value(operation.nodeId(), value);
  }

  private GraphFragment buildVariableRead(SyntaxNode node) {
    var name = node.value(SyntaxValue.Symbol.class).name();
    var declarationKey = node.requiredReference().symbolKey().orElseThrow();
    var source = values.resolve(declarationKey).orElseGet(() -> graph.symbolValue(declarationKey, name, node.resolvedType()));
    var operationNodeId = graph.operationNodeId(node, "read");
    var result = graph.temporaryValue(operationNodeId, name, node.resolvedType());
    var operation = graph.addOperationWithId(operationNodeId, node, OperationKind.READ, name, List.of(source), Optional.of(result),
        List.of());
    return GraphFragment.value(operation.nodeId(), result);
  }

  private GraphFragment buildFieldRead(SyntaxNode node) {
    var access = node.value(SyntaxValue.FieldAccess.class);
    var receiver = fieldReceivers.lower(node);
    var operationNodeId = graph.operationNodeId(node, "field-read");
    var result = graph.temporaryValue(operationNodeId, access.name(), node.resolvedType());
    var operation = graph.addOperationWithId(operationNodeId, node, OperationKind.MEMBER_ACCESS, access.name(), receiver.operands(),
        Optional.of(result), receiver.accessExceptions());
    graph.connectExits(receiver.evaluation(), operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(receiver.evaluation().entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildReceiver(SyntaxNode node) {
    var name = receiverName(node.value(SyntaxValue.Receiver.class).kind());
    var operationNodeId = graph.operationNodeId(node, "receiver");
    var result = graph.receiverValue(node, name, node.resolvedType());
    var operation = graph.addOperationWithId(operationNodeId, node, OperationKind.RECEIVER, name, List.of(), Optional.of(result),
        List.of());
    return GraphFragment.value(operation.nodeId(), result);
  }

  private GraphFragment buildInvocation(SyntaxNode node) {
    var invocation = node.value(SyntaxValue.Invocation.class);
    var evaluation = GraphFragment.empty();
    var operands = new ArrayList<FlowValue>();
    if (invocation.form().evaluatesExpression()) {
      var receiver = syntax.lower(node.requiredChild(SyntaxRole.RECEIVER));
      evaluation = graph.sequence(evaluation, receiver);
      if (invocation.form().consumesReceiver()) {
        operands.add(receiver.value().orElseThrow());
      }
    } else if (invocation.form().consumesReceiver()) {
      operands.add(graph.receiverValue(node, receiverName(invocation.form()), Optional.empty()));
    }
    for (var argumentNode : node.children(SyntaxRole.ARGUMENT)) {
      var argument = syntax.lower(argumentNode);
      evaluation = graph.sequence(evaluation, argument);
      argument.value().ifPresent(operands::add);
    }
    var exceptions = new ArrayList<ExceptionType>();
    if (invocation.form().canFailOnNullReceiver()) {
      exceptions.add(ExceptionType.runtime(NULL_POINTER_EXCEPTION));
    }
    exceptions.add(ExceptionType.unknown(ExceptionTarget.UNKNOWN_INVOCATION_EXCEPTION));
    var operationNodeId = graph.operationNodeId(node, "invoke");
    var result = node.resolvedType().filter(type -> type.category() != JavaTypeCategory.VOID)
        .map(type -> graph.temporaryValue(operationNodeId, invocation.name(), Optional.of(type)));
    var operation = graph.addInvocationOperationWithId(operationNodeId, node, OperationKind.INVOKE, invocation.name(), operands, result,
        exceptions, invocation.semantics().orElseThrow());
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()), result);
  }

  private GraphFragment buildObjectCreation(SyntaxNode node) {
    var invocation = node.value(SyntaxValue.Invocation.class);
    var evaluation = GraphFragment.empty();
    var operands = new ArrayList<FlowValue>();
    for (var child : constructionOperands(node)) {
      var childGraphFragment = syntax.lower(child);
      evaluation = graph.sequence(evaluation, childGraphFragment);
      childGraphFragment.value().ifPresent(operands::add);
    }
    var operationNodeId = graph.operationNodeId(node, "new_object");
    var result = graph.temporaryValue(operationNodeId, invocation.name(), node.resolvedType());
    var exceptions = List.of(ExceptionType.unknown(ExceptionTarget.UNKNOWN_CONSTRUCTOR_EXCEPTION));
    var operation = graph.addInvocationOperationWithId(operationNodeId, node, OperationKind.NEW_OBJECT, invocation.name(), operands,
        Optional.of(result), exceptions, invocation.semantics().orElseThrow());
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildUnaryLike(SyntaxNode node, SyntaxRole operandRole, OperationKind kind,
      List<ExceptionType> possibleExceptions) {
    var input = syntax.lower(node.requiredChild(operandRole));
    var operationNodeId = graph.operationNodeId(node, kind.name().toLowerCase(Locale.ROOT));
    var description = describe(node);
    var result = graph.temporaryValue(operationNodeId, description, node.resolvedType());
    var operation = graph.addOperationWithId(operationNodeId, node, kind, description, input.value().map(List::of).orElseGet(List::of),
        Optional.of(result), possibleExceptions);
    graph.connectExits(input, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(input.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()), Optional.of(result));
  }

  private GraphFragment buildNary(SyntaxNode node, List<SyntaxNode> operandNodes, OperationKind kind,
      List<ExceptionType> possibleExceptions) {
    var evaluation = GraphFragment.empty();
    var operands = new ArrayList<FlowValue>();
    for (var child : operandNodes) {
      var childGraphFragment = syntax.lower(child);
      evaluation = graph.sequence(evaluation, childGraphFragment);
      childGraphFragment.value().ifPresent(operands::add);
    }
    var operationNodeId = graph.operationNodeId(node, kind.name().toLowerCase(Locale.ROOT));
    var description = describe(node);
    var result = graph.temporaryValue(operationNodeId, description, node.resolvedType());
    var operation = graph.addOperationWithId(operationNodeId, node, kind, description, operands, Optional.of(result), possibleExceptions);
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildBinary(SyntaxNode node) {
    var operator = node.value(SyntaxValue.Operator.class).operator();
    var exceptions = operator == SyntaxOperator.DIVIDE || operator == SyntaxOperator.REMAINDER
        ? runtimeExceptions("java.lang.ArithmeticException")
        : List.<ExceptionType>of();
    return buildNary(node, List.of(node.requiredChild(SyntaxRole.LEFT_OPERAND), node.requiredChild(SyntaxRole.RIGHT_OPERAND)),
        OperationKind.BINARY, exceptions);
  }

  private static List<SyntaxNode> constructionOperands(SyntaxNode node) {
    var operands = new ArrayList<SyntaxNode>();
    node.optionalChild(SyntaxRole.ENCLOSING_INSTANCE).ifPresent(operands::add);
    operands.addAll(node.children(SyntaxRole.ARGUMENT));
    return List.copyOf(operands);
  }

  private GraphFragment buildShortCircuit(SyntaxNode node, boolean and) {
    var left = syntax.lower(node.requiredChild(SyntaxRole.LEFT_OPERAND));
    var description = node.value(SyntaxValue.Operator.class).operator().name();
    var decision = graph.addOperation(node, and ? "and-decision" : "or-decision", OperationKind.DECISION, description,
        left.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    graph.connectExits(left, decision.nodeId(), TransferKind.NORMAL, Optional.empty());

    var right = syntax.lower(node.requiredChild(SyntaxRole.RIGHT_OPERAND));
    var shortCircuitLiteral = and ? "false" : "true";
    var constantId = graph.operationNodeId(node, and ? "and-false" : "or-true");
    var constantValue = graph.constantValue(constantId, shortCircuitLiteral, node.resolvedType());
    var constant = graph.addOperationWithId(constantId, node, OperationKind.CONSTANT, shortCircuitLiteral, List.of(),
        Optional.of(constantValue), List.of());
    if (and) {
      graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(right.entry().orElseThrow()),
          TransferKind.TRUE_BRANCH, Optional.empty());
      graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(constant.nodeId()), TransferKind.FALSE_BRANCH,
          Optional.empty());
    } else {
      graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(constant.nodeId()), TransferKind.TRUE_BRANCH,
          Optional.empty());
      graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(right.entry().orElseThrow()),
          TransferKind.FALSE_BRANCH, Optional.empty());
    }
    var mergeId = graph.operationNodeId(node, and ? "and-merge" : "or-merge");
    var result = graph.temporaryValue(mergeId, description, node.resolvedType());
    var merge = graph.addOperationWithId(mergeId, node, OperationKind.MERGE, description,
        List.of(constantValue, right.value().orElseThrow()), Optional.of(result), List.of());
    graph.addTransfer(CfgEndpoint.operation(constant.nodeId()), CfgEndpoint.operation(merge.nodeId()), TransferKind.NORMAL,
        Optional.empty());
    graph.connectExits(right, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(left.entry().or(() -> Optional.of(decision.nodeId())), List.of(merge.nodeId()), Optional.of(result));
  }

  private GraphFragment buildAssignment(SyntaxNode node) {
    var location = locations.lower(node.requiredChild(SyntaxRole.LOCATION));
    var value = syntax.lower(node.requiredChild(SyntaxRole.VALUE));
    var evaluation = graph.sequence(location.evaluation(), value);
    var operationNodeId = graph.operationNodeId(node, "assignment");
    var result = graph.temporaryValue(operationNodeId, "assignment", node.resolvedType());
    var operands = new ArrayList<>(location.operands());
    value.value().ifPresent(operands::add);
    var operation = graph.addOperationWithId(operationNodeId, node, OperationKind.WRITE, "assignment", operands, Optional.of(result),
        location.writeExceptions(), location.semanticReferences());
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildConditionalExpression(SyntaxNode node) {
    var condition = syntax.lower(node.requiredChild(SyntaxRole.CONDITION));
    var decision = graph.addOperation(node, "conditional-decision", OperationKind.DECISION, "?:",
        condition.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    graph.connectExits(condition, decision.nodeId(), TransferKind.NORMAL, Optional.empty());
    var whenTrue = syntax.lower(node.requiredChild(SyntaxRole.TRUE_EXPRESSION));
    var whenFalse = syntax.lower(node.requiredChild(SyntaxRole.FALSE_EXPRESSION));
    graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(whenTrue.entry().orElseThrow()),
        TransferKind.TRUE_BRANCH, Optional.empty());
    graph.addTransfer(CfgEndpoint.operation(decision.nodeId()), CfgEndpoint.operation(whenFalse.entry().orElseThrow()),
        TransferKind.FALSE_BRANCH, Optional.empty());
    var mergeId = graph.operationNodeId(node, "conditional-merge");
    var result = graph.temporaryValue(mergeId, "?:", node.resolvedType());
    var merge = graph.addOperationWithId(mergeId, node, OperationKind.MERGE, "?:",
        List.of(whenTrue.value().orElseThrow(), whenFalse.value().orElseThrow()), Optional.of(result), List.of());
    graph.connectExits(whenTrue, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
    graph.connectExits(whenFalse, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(condition.entry().or(() -> Optional.of(decision.nodeId())), List.of(merge.nodeId()), Optional.of(result));
  }

  private GraphFragment buildSwitchExpression(SyntaxNode node) {
    var selector = syntax.lower(node.requiredChild(SyntaxRole.SELECTOR));
    var switchOperation = graph.addOperation(node, "switch-selector", OperationKind.SWITCH_SELECTOR, "switch",
        selector.value().map(List::of).orElseGet(List::of), Optional.empty(),
        runtimeExceptions(NULL_POINTER_EXCEPTION, "java.lang.MatchException"));
    graph.connectExits(selector, switchOperation.nodeId(), TransferKind.NORMAL, Optional.empty());
    var caseNodes = node.children(SyntaxRole.CASE);
    var cases = new ArrayList<GraphFragment>();
    var caseValues = new ArrayList<FlowValue>();
    for (var caseNode : caseNodes) {
      var caseGraphFragment = buildCaseBody(caseNode);
      cases.add(caseGraphFragment);
      caseGraphFragment.value().ifPresent(caseValues::add);
      if (caseGraphFragment.entry().isPresent()) {
        graph.addTransfer(CfgEndpoint.operation(switchOperation.nodeId()), CfgEndpoint.operation(caseGraphFragment.entry().orElseThrow()),
            TransferKind.SWITCH_ALTERNATIVE, Optional.of(caseLabels(caseNode)));
      }
    }
    var mergeId = graph.operationNodeId(node, "switch-merge");
    var result = graph.temporaryValue(mergeId, "switch", node.resolvedType());
    var merge = graph.addOperationWithId(mergeId, node, OperationKind.MERGE, "switch", caseValues, Optional.of(result), List.of());
    for (var index = 0; index < cases.size(); index++) {
      var caseGraphFragment = cases.get(index);
      if (caseGraphFragment.entry().isEmpty()) {
        graph.addTransfer(CfgEndpoint.operation(switchOperation.nodeId()), CfgEndpoint.operation(merge.nodeId()),
            TransferKind.SWITCH_ALTERNATIVE, Optional.of(caseLabels(caseNodes.get(index))));
      } else {
        graph.connectExits(caseGraphFragment, merge.nodeId(), TransferKind.NORMAL, Optional.empty());
      }
    }
    return new GraphFragment(selector.entry().or(() -> Optional.of(switchOperation.nodeId())), List.of(merge.nodeId()),
        Optional.of(result));
  }

  private GraphFragment buildCaseBody(SyntaxNode caseNode) {
    var body = caseNode.optionalChild(SyntaxRole.BODY);
    if (body.isPresent()) {
      return syntax.lower(body.orElseThrow());
    }
    var result = GraphFragment.empty();
    for (var statement : caseNode.children(SyntaxRole.STATEMENT)) {
      result = graph.sequence(result, syntax.lower(statement));
    }
    return result;
  }

  private static String caseLabels(SyntaxNode caseNode) {
    var expressionLabels = caseNode.children(SyntaxRole.LABEL);
    return caseNode.value(SyntaxValue.SwitchCase.class).labels().stream().map(label -> switch (label) {
      case SyntaxValue.SwitchLabel.Default ignored -> "default";
      case SyntaxValue.SwitchLabel.Expression expression -> switchLabel(expressionLabels.get(expression.childPosition()));
    }).collect(Collectors.joining(","));
  }

  private static String switchLabel(SyntaxNode label) {
    if (label.syntaxKind() == SyntaxKind.LITERAL) {
      var typeKey = label.resolvedType().map(JavaTypeIdentity::typeKey).orElse("unresolved");
      return "literal:" + typeKey + ':' + label.value(SyntaxValue.Literal.class).text();
    }
    return label.semanticReference().flatMap(reference -> reference.symbolKey()).map(symbolKey -> "symbol:" + symbolKey)
        .orElse("syntax:" + label.nodeId());
  }

  private static String describe(SyntaxNode node) {
    return switch (node.value()) {
      case SyntaxValue.None ignored -> node.syntaxKind().name().toLowerCase(Locale.ROOT);
      case SyntaxValue.LocalDeclaration declaration -> declaration.name();
      case SyntaxValue.Symbol symbol -> symbol.name();
      case SyntaxValue.FieldAccess field -> field.name();
      case SyntaxValue.Invocation invocation -> invocation.name();
      case SyntaxValue.Receiver receiver -> receiver.kind().name().toLowerCase(Locale.ROOT);
      case SyntaxValue.Literal literal -> literal.text();
      case SyntaxValue.Operator operator -> operator.operator().name();
      case SyntaxValue.TypeName typeName -> typeName.name();
      case SyntaxValue.SwitchCase switchCase -> switchCase.form().name().toLowerCase(Locale.ROOT);
      case SyntaxValue.Unsupported unsupported -> unsupported.compilerKind();
    };
  }

  private static String receiverName(InvocationForm form) {
    return switch (form) {
      case INSTANCE_IMPLICIT, INSTANCE_THIS -> "this";
      case INSTANCE_QUALIFIED_THIS -> "qualified-this";
      case SUPER, SUPER_CONSTRUCTOR -> "super";
      case QUALIFIED_SUPER, QUALIFIED_SUPER_CONSTRUCTOR -> "qualified-super";
      case STATIC_UNQUALIFIED, STATIC_TYPE, STATIC_EXPRESSION, INSTANCE_EXPRESSION, THIS_CONSTRUCTOR, OBJECT_CREATION,
          QUALIFIED_OBJECT_CREATION ->
        throw new IllegalArgumentException("Invocation form does not provide an implicit runtime receiver: " + form);
    };
  }

  private static String receiverName(SyntaxValue.ReceiverKind receiver) {
    return switch (receiver) {
      case THIS -> "this";
      case QUALIFIED_THIS -> "qualified-this";
      case SUPER -> "super";
      case QUALIFIED_SUPER -> "qualified-super";
    };
  }

  private static List<ExceptionType> runtimeExceptions(String... typeNames) {
    return Arrays.stream(typeNames).map(ExceptionType::runtime).toList();
  }
}
