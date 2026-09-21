package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;

/** Evaluates storage locations without reading the values currently stored in them. */
final class AssignmentLocationLowerer {
  private static final String NULL_POINTER_EXCEPTION = "java.lang.NullPointerException";
  private final SyntaxGraphLowerer syntax;
  private final ControlFlowGraphConstruction graph;
  private final FlowValueScope values;
  private final FieldReceiverLowerer fieldReceivers;

  AssignmentLocationLowerer(SyntaxGraphLowerer syntax, ControlFlowGraphConstruction graph, FlowValueScope values,
      FieldReceiverLowerer fieldReceivers) {
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.graph = Objects.requireNonNull(graph, "graph");
    this.values = Objects.requireNonNull(values, "values");
    this.fieldReceivers = Objects.requireNonNull(fieldReceivers, "fieldReceivers");
  }

  AssignmentLocation lower(SyntaxNode node) {
    Objects.requireNonNull(node, "node");
    return switch (node.syntaxKind()) {
      case VARIABLE_LOCATION -> local(node);
      case FIELD_LOCATION -> field(node);
      case ARRAY_LOCATION -> array(node);
      default -> throw new IllegalArgumentException("Expected an assignment location but found " + node.syntaxKind());
    };
  }

  private AssignmentLocation local(SyntaxNode node) {
    var declarationKey = node.requiredReference().symbolKey().orElseThrow();
    var target = values.resolve(declarationKey)
        .orElseThrow(() -> new IllegalArgumentException("Assignment location is not visible in the current lexical scope"));
    return new AssignmentLocation(GraphFragment.empty(), List.of(target), node.semanticReference().stream().toList(), List.of());
  }

  private AssignmentLocation field(SyntaxNode node) {
    var receiver = fieldReceivers.lower(node);
    return new AssignmentLocation(receiver.evaluation(), receiver.operands(), node.semanticReference().stream().toList(),
        receiver.accessExceptions());
  }

  private AssignmentLocation array(SyntaxNode node) {
    var array = syntax.lower(node.requiredChild(SyntaxRole.ARRAY));
    var index = syntax.lower(node.requiredChild(SyntaxRole.INDEX));
    var evaluation = graph.sequence(array, index);
    var exceptions = new ArrayList<ExceptionType>();
    exceptions.add(ExceptionType.runtime(NULL_POINTER_EXCEPTION));
    exceptions.add(ExceptionType.runtime("java.lang.ArrayIndexOutOfBoundsException"));
    if (node.resolvedType().map(type -> switch (type.category()) {
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> false;
      default -> true;
    }).orElse(false)) {
      exceptions.add(ExceptionType.runtime("java.lang.ArrayStoreException"));
    }
    return new AssignmentLocation(evaluation, List.of(array.value().orElseThrow(), index.value().orElseThrow()), List.of(), exceptions);
  }
}
