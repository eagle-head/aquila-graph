package org.aquilagraph.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.RepositoryOperationContext;

/** Finite immutable failure graph with report-local first-encounter identities. */
record FailureCatalog(List<Node> nodes) {
  FailureCatalog {
    nodes = List.copyOf(nodes);
    var size = nodes.size();
    for (var index = 0; index < size; index++) {
      var node = nodes.get(index);
      if (node.reference() != index + 1) {
        throw new IllegalArgumentException("Failure definitions must have consecutive local identities");
      }
      node.cause().ifPresent(reference -> requireReference(reference, size));
      node.carrier().ifPresent(reference -> requireReference(reference, size));
      node.effectivePrimary().ifPresent(reference -> requireReference(reference, size));
      node.suppressed().forEach(reference -> requireReference(reference, size));
    }
  }

  Node node(int reference) {
    requireReference(reference, nodes.size());
    return nodes.get(reference - 1);
  }

  static void requireReference(int reference, int size) {
    if (reference < 1 || reference > size) {
      throw new IllegalArgumentException("Reference is outside its report catalog");
    }
  }

  record Node(int reference, String type, Optional<String> message, OptionalInt cause, OptionalInt carrier, OptionalInt effectivePrimary,
      List<Integer> suppressed, Optional<Operation> operation) {
    Node {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(message, "message");
      Objects.requireNonNull(cause, "cause");
      Objects.requireNonNull(carrier, "carrier");
      Objects.requireNonNull(effectivePrimary, "effectivePrimary");
      suppressed = List.copyOf(suppressed);
      Objects.requireNonNull(operation, "operation");
    }
  }

  record Operation(RepositoryOperationContext context, Optional<CommitKnowledge> knowledge, Optional<Boundary> boundary,
      Optional<AdapterFailureDetail> adapter) {
    Operation {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(knowledge, "knowledge");
      Objects.requireNonNull(boundary, "boundary");
      Objects.requireNonNull(adapter, "adapter");
    }
  }
}
