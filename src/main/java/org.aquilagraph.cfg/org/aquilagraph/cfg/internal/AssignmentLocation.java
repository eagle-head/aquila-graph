package org.aquilagraph.cfg.internal;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.source.api.SemanticReference;

/** Graph-construction facts produced while a simple-assignment storage location is evaluated. */
record AssignmentLocation(GraphFragment evaluation, List<DraftValue> operands, List<SemanticReference> semanticReferences,
    List<ExceptionType> writeExceptions) {
  AssignmentLocation {
    Objects.requireNonNull(evaluation, "evaluation");
    operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
    semanticReferences = List.copyOf(Objects.requireNonNull(semanticReferences, "semanticReferences"));
    writeExceptions = List.copyOf(Objects.requireNonNull(writeExceptions, "writeExceptions"));
  }
}
