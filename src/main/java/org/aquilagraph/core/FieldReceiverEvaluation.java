package org.aquilagraph.core;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.FlowValue;

/** Graph-construction facts produced while a field receiver is evaluated. */
record FieldReceiverEvaluation(GraphFragment evaluation, List<FlowValue> operands, List<ExceptionType> accessExceptions) {
  FieldReceiverEvaluation {
    Objects.requireNonNull(evaluation, "evaluation");
    operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
    accessExceptions = List.copyOf(Objects.requireNonNull(accessExceptions, "accessExceptions"));
  }
}
