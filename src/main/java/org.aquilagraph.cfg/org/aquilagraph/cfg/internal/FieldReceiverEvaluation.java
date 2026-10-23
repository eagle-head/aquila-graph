package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Objects;

/** Graph-construction facts produced while a field receiver is evaluated. */
record FieldReceiverEvaluation(GraphFragment evaluation, List<DraftValue> operands, List<ExceptionType> accessExceptions) {
  FieldReceiverEvaluation {
    Objects.requireNonNull(evaluation, "evaluation");
    operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
    accessExceptions = List.copyOf(Objects.requireNonNull(accessExceptions, "accessExceptions"));
  }
}
