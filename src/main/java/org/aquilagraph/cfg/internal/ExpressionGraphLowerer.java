package org.aquilagraph.cfg.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.cfg.api.FlowValue;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.cfg.api.TransferKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

final class ExpressionGraphLowerer {
  private static final String NULL_POINTER_EXCEPTION = "java.lang.NullPointerException";

  private final SyntaxGraphLowerer syntax;
  private final ControlFlowGraphConstruction graph;
  private final FlowValueScope values;
  private final FieldReceiverLowerer fieldReceivers;
  private final AssignmentLocationLowerer locations;
  private final CallArgumentLowerer arguments;

  ExpressionGraphLowerer(SyntaxGraphLowerer syntax, ControlFlowGraphConstruction graph, FlowValueScope values) {
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.graph = Objects.requireNonNull(graph, "graph");
    this.values = Objects.requireNonNull(values, "values");
    fieldReceivers = new FieldReceiverLowerer(syntax, graph);
    locations = new AssignmentLocationLowerer(syntax, graph, values, fieldReceivers);
    arguments = new CallArgumentLowerer(syntax, graph);
  }

  GraphFragment lower(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case LITERAL -> buildLiteral(node);
      case VARIABLE_ACCESS -> buildVariableRead(node);
      case FIELD_ACCESS -> buildFieldRead(node);
      case RECEIVER -> buildReceiver(node);
      case ARRAY_LENGTH ->
        buildUnaryLike(node, SyntaxRole.QUALIFIER, OperationKind.ARRAY_LENGTH, runtimeExceptions(NULL_POINTER_EXCEPTION));
      case ARRAY_ACCESS -> buildArrayRead(node);
      case INVOCATION -> buildInvocation(node);
      case OBJECT_CREATION -> buildObjectCreation(node);
      case UNARY -> buildUnaryLike(node, SyntaxRole.OPERAND, OperationKind.UNARY, List.of());
      case BINARY -> buildBinary(node);
      case CONDITIONAL_AND -> buildShortCircuit(node, true);
      case CONDITIONAL_OR -> buildShortCircuit(node, false);
      case ASSIGNMENT -> buildAssignment(node);
      case PARENTHESIZED -> syntax.lower(node.requiredChild(SyntaxRole.EXPRESSION));
      case TYPE_CAST -> buildCast(node);
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
    var evaluation = receiver.evaluation();
    if (staticField(node)) {
      var selected = syntax.staticAccess(node);
      if (syntax.classInitializationRequired(node)) {
        evaluation = graph.sequence(evaluation, graph.classInitialization(node, selected.declaringType()));
      }
    }
    var operationNodeId = graph.operationNodeId(node, "field-read");
    var result = graph.temporaryValue(operationNodeId, access.name(), node.resolvedType());
    var operation = graph.addOperationWithId(operationNodeId, node, OperationKind.MEMBER_ACCESS, access.name(), receiver.operands(),
        Optional.of(result), receiver.accessExceptions());
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
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
    if (ReceiverEvaluationPolicy.evaluatesExpression(invocation.form())) {
      var receiver = syntax.lower(node.requiredChild(SyntaxRole.RECEIVER));
      evaluation = graph.sequence(evaluation, receiver);
      if (ReceiverEvaluationPolicy.consumesReceiver(invocation.form())) {
        operands.add(receiver.value().orElseThrow());
      }
      if (invocation.form() == InvocationForm.QUALIFIED_SUPER_CONSTRUCTOR) {
        evaluation = graph.sequence(evaluation, enclosingInstanceCheck(node, receiver.value().orElseThrow()));
      }
    } else if (ReceiverEvaluationPolicy.consumesReceiver(invocation.form())) {
      operands.add(graph.receiverValue(node, receiverName(invocation.form()), Optional.empty()));
    }
    var selectedArguments = arguments.lower(node, evaluation, operands);
    evaluation = selectedArguments.fragment();
    if (invocation.semantics().orElseThrow().mode() == InvocationMode.STATIC) {
      evaluation = graph.sequence(evaluation, graph.classInitialization(node, syntax.staticAccess(node).declaringType()));
    }
    var exceptions = new ArrayList<ExceptionType>();
    if (ReceiverEvaluationPolicy.canFailOnNullReceiver(invocation.form())
        && invocation.form() != InvocationForm.QUALIFIED_SUPER_CONSTRUCTOR) {
      exceptions.add(ExceptionType.runtime(NULL_POINTER_EXCEPTION));
    }
    exceptions.add(ExceptionType.unknown(ExceptionTarget.UNKNOWN_INVOCATION_EXCEPTION));
    var operationNodeId = graph.operationNodeId(node, "invoke");
    var result = node.resolvedType().filter(type -> type.category() != JavaTypeCategory.VOID)
        .map(type -> graph.temporaryValue(operationNodeId, invocation.name(), Optional.of(type)));
    var operation = graph.addInvocationOperationWithId(operationNodeId, node, OperationKind.INVOKE, invocation.name(),
        selectedArguments.operands(), result, exceptions, invocation.semantics().orElseThrow());
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()), result);
  }

  private GraphFragment buildObjectCreation(SyntaxNode node) {
    var invocation = node.value(SyntaxValue.Invocation.class);
    var evaluation = GraphFragment.empty();
    var enclosingOperands = new ArrayList<FlowValue>();
    if (invocation.form() == InvocationForm.QUALIFIED_OBJECT_CREATION) {
      var enclosing = syntax.lower(node.requiredChild(SyntaxRole.ENCLOSING_INSTANCE));
      var value = enclosing.value().orElseThrow();
      enclosingOperands.add(value);
      evaluation = graph.sequence(enclosing, enclosingInstanceCheck(node, value));
    }
    evaluation = graph.sequence(evaluation, graph.classInitialization(node, invocation.semantics().orElseThrow().qualifyingType()));
    var allocationId = graph.operationNodeId(node, "new-allocation");
    var allocated = graph.temporaryValue(allocationId, invocation.name(), node.resolvedType());
    var allocation = graph.addOperationWithId(allocationId, node, OperationKind.NEW_ALLOCATION, invocation.name(), enclosingOperands,
        Optional.of(allocated), List.of(UseConversionLowerer.allocationFailure()), List.of());
    evaluation = graph.sequence(evaluation, GraphFragment.value(allocation.nodeId(), allocated));
    var selectedArguments = arguments.lower(node, evaluation, List.of(allocated));
    evaluation = selectedArguments.fragment();
    var operationNodeId = graph.operationNodeId(node, "new_object");
    var exceptions = List.of(ExceptionType.unknown(ExceptionTarget.UNKNOWN_CONSTRUCTOR_EXCEPTION));
    var operation = graph.addInvocationOperationWithId(operationNodeId, node, OperationKind.NEW_OBJECT, invocation.name(),
        selectedArguments.operands(), Optional.of(allocated), exceptions, invocation.semantics().orElseThrow());
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()),
        Optional.of(allocated));
  }

  private GraphFragment enclosingInstanceCheck(SyntaxNode node, FlowValue value) {
    var id = graph.operationNodeId(node, "enclosing-instance-check");
    var operation = graph.addOperationWithId(id, node, OperationKind.ENCLOSING_INSTANCE_CHECK, "enclosing instance", List.of(value),
        Optional.empty(), runtimeExceptions(NULL_POINTER_EXCEPTION), List.of());
    return new GraphFragment(Optional.of(operation.nodeId()), List.of(operation.nodeId()), Optional.empty());
  }

  private GraphFragment buildUnaryLike(SyntaxNode node, SyntaxRole operandRole, OperationKind kind,
      List<ExceptionType> possibleExceptions) {
    var input = operandRole == SyntaxRole.QUALIFIER ? syntax.lower(node.requiredChild(operandRole)) : syntax.lowerUse(node, operandRole);
    var operationNodeId = graph.operationNodeId(node, kind.name().toLowerCase(Locale.ROOT));
    var description = describe(node);
    var result = graph.temporaryValue(operationNodeId, description, node.resolvedType());
    var operation = graph.addOperationWithId(operationNodeId, node, kind, description, input.value().map(List::of).orElseGet(List::of),
        Optional.of(result), possibleExceptions);
    graph.connectExits(input, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(input.entry().or(() -> Optional.of(operation.nodeId())), List.of(operation.nodeId()), Optional.of(result));
  }

  private GraphFragment buildNary(SyntaxNode node, List<SyntaxRole> operandRoles, OperationKind kind,
      List<ExceptionType> possibleExceptions) {
    var evaluation = GraphFragment.empty();
    var operands = new ArrayList<FlowValue>();
    for (var role : operandRoles) {
      var childGraphFragment = syntax.lowerUse(node, role);
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
    if (operator == SyntaxOperator.PLUS && node.resolvedType().map(type -> "java.lang.String".equals(type.erasedName())).orElse(false)) {
      return buildStringConcatenation(node);
    }
    var exceptions = List.<ExceptionType>of();
    if (operator == SyntaxOperator.DIVIDE || operator == SyntaxOperator.REMAINDER) {
      var resultType = node.resolvedType()
          .orElseThrow(() -> new IllegalStateException("Arithmetic lowering requires a resolved promoted result type"));
      exceptions = switch (resultType.category()) {
        case INT, LONG -> runtimeExceptions("java.lang.ArithmeticException");
        case FLOAT, DOUBLE -> List.of();
        default -> throw new IllegalStateException("Arithmetic lowering requires an integral or floating promoted result type");
      };
    }
    return buildNary(node, List.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND), OperationKind.BINARY, exceptions);
  }

  private GraphFragment buildArrayRead(SyntaxNode node) {
    var array = syntax.lower(node.requiredChild(SyntaxRole.ARRAY));
    var index = syntax.lowerUse(node, SyntaxRole.INDEX);
    var evaluation = graph.sequence(array, index);
    var operationId = graph.operationNodeId(node, "array_access");
    var result = graph.temporaryValue(operationId, describe(node), node.resolvedType());
    var operation = graph.addOperationWithId(operationId, node, OperationKind.ARRAY_ACCESS, describe(node),
        List.of(array.value().orElseThrow(), index.value().orElseThrow()), Optional.of(result),
        runtimeExceptions(NULL_POINTER_EXCEPTION, "java.lang.ArrayIndexOutOfBoundsException"));
    graph.connectExits(evaluation, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry(), List.of(operation.nodeId()), Optional.of(result), evaluation.yieldExits());
  }

  private GraphFragment buildCast(SyntaxNode node) {
    var decision = syntax.useDecision(node, SyntaxRole.EXPRESSION, 0);
    var input = syntax.lowerUse(node, SyntaxRole.EXPRESSION);
    if (decision.checkedReferenceTarget().isPresent()) {
      return input;
    }
    var target = node.resolvedType().orElseThrow();
    var exceptions = List.<ExceptionType>of();
    if (syntax.referenceCheckRequired(node)) {
      exceptions = runtimeExceptions("java.lang.ClassCastException");
    }
    var operationId = graph.operationNodeId(node, "cast");
    var result = graph.temporaryValue(operationId, target.displayName(), Optional.of(target));
    var operation = graph.addOperationWithId(operationId, node, OperationKind.CAST, target.displayName(),
        List.of(input.value().orElseThrow()), Optional.of(result), exceptions);
    graph.connectExits(input, operation.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(input.entry(), List.of(operation.nodeId()), Optional.of(result), input.yieldExits());
  }

  private GraphFragment buildStringConcatenation(SyntaxNode node) {
    var selected = syntax.stringConcatenation(node);
    var left = syntax.lowerUse(node, SyntaxRole.LEFT_OPERAND);
    var right = syntax.lowerUse(node, SyntaxRole.RIGHT_OPERAND);
    var evaluation = graph.sequence(left, right);
    var operands = new ArrayList<>(List.of(left.value().orElseThrow(), right.value().orElseThrow()));
    var operandPlan = syntax.stringOperandPlan(node);
    if (operandPlan.convertedSide().isPresent()) {
      var side = operandPlan.convertedSide().orElseThrow();
      var position = side == SyntaxRole.LEFT_OPERAND ? 0 : 1;
      var operationId = graph.operationNodeId(node, position == 0 ? "string-conversion:left" : "string-conversion:right");
      var result = graph.temporaryValue(operationId, "string conversion", Optional.of(selected.stringType()));
      var exceptions = operandPlan.compileTimeConstant() || definitelyNull(node.requiredChild(side))
          ? List.<ExceptionType>of()
          : List.of(ExceptionType.unknown("unchecked-string-conversion"));
      var conversion = graph.addOperationWithId(operationId, node, OperationKind.STRING_CONVERSION, "string conversion",
          List.of(operands.get(position)), Optional.of(result), exceptions, List.of());
      evaluation = graph.sequence(evaluation, GraphFragment.value(conversion.nodeId(), result));
      operands.set(position, result);
    }
    var operationId = graph.operationNodeId(node, "binary");
    var result = graph.temporaryValue(operationId, "PLUS", Optional.of(selected.stringType()));
    var concat = graph.addOperationWithId(operationId, node, OperationKind.STRING_CONCAT, "PLUS", operands, Optional.of(result),
        operandPlan.compileTimeConstant() ? List.of() : List.of(UseConversionLowerer.allocationFailure()), List.of());
    graph.connectExits(evaluation, concat.nodeId(), TransferKind.NORMAL, Optional.empty());
    return new GraphFragment(evaluation.entry(), List.of(concat.nodeId()), Optional.of(result), evaluation.yieldExits());
  }

  private static boolean definitelyNull(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case LITERAL -> node.resolvedType().map(type -> type.category() == JavaTypeCategory.NULL).orElse(false);
      case PARENTHESIZED, TYPE_CAST -> definitelyNull(node.requiredChild(SyntaxRole.EXPRESSION));
      default -> false;
    };
  }

  private static boolean staticField(SyntaxNode node) {
    return node.value() instanceof SyntaxValue.FieldAccess access && access.receiver().isStatic();
  }

  private GraphFragment buildShortCircuit(SyntaxNode node, boolean and) {
    var left = syntax.lowerUse(node, SyntaxRole.LEFT_OPERAND);
    var description = node.value(SyntaxValue.Operator.class).operator().name();
    var decision = graph.addOperation(node, and ? "and-decision" : "or-decision", OperationKind.DECISION, description,
        left.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    graph.connectExits(left, decision.nodeId(), TransferKind.NORMAL, Optional.empty());

    var right = syntax.lowerUse(node, SyntaxRole.RIGHT_OPERAND);
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
    var locationNode = node.requiredChild(SyntaxRole.LOCATION);
    var location = locations.lower(locationNode);
    var rawValue = syntax.lower(node.requiredChild(SyntaxRole.VALUE));
    var evaluation = graph.sequence(location.evaluation(), rawValue);
    if (locationNode.syntaxKind() == SyntaxKind.ARRAY_ACCESS) {
      var checkId = graph.operationNodeId(node, "array-target-check");
      var check = graph.addOperationWithId(checkId, node, OperationKind.ARRAY_TARGET_CHECK, "array target", location.operands(),
          Optional.empty(), runtimeExceptions(NULL_POINTER_EXCEPTION, "java.lang.ArrayIndexOutOfBoundsException"), List.of());
      evaluation = graph.sequence(evaluation, new GraphFragment(Optional.of(check.nodeId()), List.of(check.nodeId()), Optional.empty()));
    }
    var value = syntax.convertUse(node, SyntaxRole.VALUE, 0,
        new GraphFragment(evaluation.entry(), evaluation.normalExits(), rawValue.value(), evaluation.yieldExits()));
    evaluation = value;
    if (staticField(locationNode)) {
      evaluation = graph.sequence(evaluation, graph.classInitialization(locationNode, syntax.staticAccess(locationNode).declaringType()));
    }
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
    var condition = syntax.lowerUse(node, SyntaxRole.CONDITION);
    var decision = graph.addOperation(node, "conditional-decision", OperationKind.DECISION, "?:",
        condition.value().map(List::of).orElseGet(List::of), Optional.empty(), List.of());
    graph.connectExits(condition, decision.nodeId(), TransferKind.NORMAL, Optional.empty());
    var whenTrue = syntax.lowerUse(node, SyntaxRole.TRUE_EXPRESSION);
    var whenFalse = syntax.lowerUse(node, SyntaxRole.FALSE_EXPRESSION);
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
    return syntax.withSwitchTarget(node.nodeId(), () -> buildSwitchAlternatives(node));
  }

  private GraphFragment buildSwitchAlternatives(SyntaxNode node) {
    var selector = syntax.lowerUse(node, SyntaxRole.SELECTOR);
    var switchOperation = graph.addOperation(node, "switch-selector", OperationKind.SWITCH_SELECTOR, "switch",
        selector.value().map(List::of).orElseGet(List::of), Optional.empty(),
        runtimeExceptions(NULL_POINTER_EXCEPTION, "java.lang.MatchException"));
    graph.connectExits(selector, switchOperation.nodeId(), TransferKind.NORMAL, Optional.empty());
    var caseNodes = node.children(SyntaxRole.CASE);
    var cases = new ArrayList<GraphFragment>();
    var caseValues = new ArrayList<FlowValue>();
    var escapingYields = new ArrayList<YieldExit>();
    for (var caseNode : caseNodes) {
      var caseGraphFragment = buildCaseBody(caseNode);
      cases.add(caseGraphFragment);
      caseGraphFragment.value().ifPresent(caseValues::add);
      for (var yield : caseGraphFragment.yieldExits()) {
        if (yield.targetSwitchNodeId().equals(node.nodeId())) {
          caseValues.add(yield.value());
        } else {
          escapingYields.add(yield);
        }
      }
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
      for (var yield : caseGraphFragment.yieldExits()) {
        if (yield.targetSwitchNodeId().equals(node.nodeId())) {
          graph.addTransfer(CfgEndpoint.operation(yield.sourceEndpointId()), CfgEndpoint.operation(merge.nodeId()), TransferKind.NORMAL,
              Optional.empty());
        }
      }
    }
    return new GraphFragment(selector.entry().or(() -> Optional.of(switchOperation.nodeId())),
        caseValues.isEmpty() ? List.of() : List.of(merge.nodeId()), Optional.of(result), escapingYields);
  }

  private GraphFragment buildCaseBody(SyntaxNode caseNode) {
    var ruleBody = caseNode.requiredChild(SyntaxRole.BODY);
    return switch (ruleBody.syntaxKind()) {
      case BLOCK, THROW -> syntax.lower(ruleBody);
      default -> syntax.lowerUse(caseNode, SyntaxRole.BODY);
    };
  }

  private static String caseLabels(SyntaxNode caseNode) {
    var expressionLabels = caseNode.children(SyntaxRole.LABEL);
    return caseNode.value(SyntaxValue.SwitchCase.class).labels().stream().map(label -> switch (label) {
      case SyntaxValue.SwitchLabel.Default _ -> "default";
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
      case SyntaxValue.None _ -> node.syntaxKind().name().toLowerCase(Locale.ROOT);
      case SyntaxValue.DiamondType _ -> "<>";
      case SyntaxValue.LocalDeclaration declaration -> declaration.name();
      case SyntaxValue.Lambda _,SyntaxValue.StatementLabel _,SyntaxValue.ArrayCreation _ ->
        throw new IllegalArgumentException("Source syntax is outside the bounded CFG profile: " + node.syntaxKind());
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
