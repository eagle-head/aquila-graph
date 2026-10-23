package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

/** Evaluates field receiver forms shared by field reads and assignment locations. */
final class FieldReceiverLowerer {
  private static final String NULL_POINTER_EXCEPTION = "java.lang.NullPointerException";
  private final SyntaxGraphLowerer syntax;
  private final ControlFlowGraphConstruction graph;

  FieldReceiverLowerer(SyntaxGraphLowerer syntax, ControlFlowGraphConstruction graph) {
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.graph = Objects.requireNonNull(graph, "graph");
  }

  FieldReceiverEvaluation lower(SyntaxNode node) {
    var access = node.value(SyntaxValue.FieldAccess.class);
    var evaluation = ReceiverEvaluationPolicy.evaluatesExpression(access.receiver())
        ? syntax.lower(node.requiredChild(SyntaxRole.RECEIVER))
        : GraphFragment.empty();
    var operands = ReceiverEvaluationPolicy.consumesReceiver(access.receiver())
        ? List.of(receiverValue(node, access.receiver(), evaluation))
        : List.<DraftValue>of();
    var exceptions = access.receiver() == SyntaxValue.FieldReceiver.INSTANCE_EXPRESSION
        ? List.of(ExceptionType.runtime(NULL_POINTER_EXCEPTION))
        : List.<ExceptionType>of();
    return new FieldReceiverEvaluation(evaluation, operands, exceptions);
  }

  private DraftValue receiverValue(SyntaxNode node, SyntaxValue.FieldReceiver receiver, GraphFragment evaluation) {
    return receiver == SyntaxValue.FieldReceiver.INSTANCE_EXPRESSION
        ? evaluation.value().orElseThrow()
        : graph.receiverValue(node, receiverName(receiver), Optional.empty());
  }

  private static String receiverName(SyntaxValue.FieldReceiver receiver) {
    return switch (receiver) {
      case INSTANCE_IMPLICIT, INSTANCE_THIS -> "this";
      case INSTANCE_QUALIFIED_THIS -> "qualified-this";
      case SUPER -> "super";
      case QUALIFIED_SUPER -> "qualified-super";
      case INSTANCE_EXPRESSION, STATIC_EXPRESSION, STATIC_TYPE, STATIC_UNQUALIFIED ->
        throw new IllegalArgumentException("Field receiver does not provide an implicit runtime value: " + receiver);
    };
  }
}
