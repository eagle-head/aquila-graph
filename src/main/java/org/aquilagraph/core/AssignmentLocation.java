package org.aquilagraph.core;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.FlowValue;
import org.aquilagraph.model.SemanticReference;

/** Graph-construction facts produced while a simple-assignment storage location is evaluated. */
record AssignmentLocation(GraphFragment evaluation, List<FlowValue> operands, List<SemanticReference> semanticReferences,
    List<ExceptionType> writeExceptions) {
  AssignmentLocation {
    Objects.requireNonNull(evaluation, "evaluation");
    operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
    semanticReferences = List.copyOf(Objects.requireNonNull(semanticReferences, "semanticReferences"));
    writeExceptions = List.copyOf(Objects.requireNonNull(writeExceptions, "writeExceptions"));
  }
}
