package org.aquilagraph.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.aquilagraph.application.AnalysisPublicationException;
import org.aquilagraph.application.ApplicationRepositoryOperationException;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.PublicationFailure;
import org.aquilagraph.application.port.RepositoryOperationException;
import org.aquilagraph.cli.FailureCatalog.Node;
import org.aquilagraph.cli.FailureCatalog.Operation;

/** Captures identity-aware depth-first preorder using resumable traversal frames. */
final class FailureGraphCapture {
  private final FailureDetailProjector details;
  private final FailureIdentities identities = FailureIdentities.usingReferenceIdentity();
  private final List<Node> nodes = new ArrayList<>();

  FailureGraphCapture(FailureDetailProjector details) {
    this.details = Objects.requireNonNull(details, "details");
  }

  int capture(Throwable root) {
    Objects.requireNonNull(root, "root");
    var existing = identities.find(root);
    if (existing.isPresent()) {
      return existing.getAsInt();
    }
    var stack = new ArrayDeque<Frame>();
    stack.push(encounter(root));
    var reference = identities.require(root);
    while (!stack.isEmpty()) {
      var frame = Objects.requireNonNull(stack.peek());
      if (frame.hasNext()) {
        var child = frame.next();
        if (identities.find(child).isEmpty()) {
          stack.push(encounter(child));
        }
      } else {
        nodes.add(snapshot(frame));
        stack.pop();
      }
    }
    return reference;
  }

  FailureCatalog catalog() {
    return new FailureCatalog(nodes.stream().sorted(Comparator.comparingInt(Node::reference)).toList());
  }

  private Frame encounter(Throwable failure) {
    var reference = identities.remember(failure);
    var cause = Optional.ofNullable(failure.getCause());
    var carrier = switch (failure) {
      case PublicationFailure publication -> Optional.<Throwable>of(publication.carrier());
      case AnalysisPublicationException publication -> Optional.<Throwable>of(publication.failure());
      default -> Optional.<Throwable>empty();
    };
    var primary = switch (failure) {
      case PublicationFailure publication -> Optional.<Throwable>of(publication.effectivePrimary());
      case AnalysisPublicationException publication -> Optional.<Throwable>of(publication.failure().effectivePrimary());
      case RepositoryOperationException operation -> Optional.<Throwable>of(operation.effectivePrimary());
      default -> Optional.<Throwable>empty();
    };
    var suppressed = List.of(failure.getSuppressed());
    var outgoing = new ArrayList<Throwable>();
    cause.ifPresent(outgoing::add);
    carrier.ifPresent(outgoing::add);
    primary.ifPresent(outgoing::add);
    outgoing.addAll(suppressed);
    var observation = new Observation(failure.getClass().getName(), Optional.ofNullable(failure.getMessage()), cause, carrier, primary,
        suppressed, operation(failure));
    return new Frame(reference, observation, outgoing);
  }

  private Optional<Operation> operation(Throwable failure) {
    if (!(failure instanceof RepositoryOperationException carrier)) {
      return Optional.empty();
    }
    var boundary = carrier instanceof ApplicationRepositoryOperationException application
        ? Optional.of(application.boundary())
        : Optional.<Boundary>empty();
    return Optional.of(new Operation(carrier.context(), carrier.knowledge(), boundary, details.project(carrier)));
  }

  private Node snapshot(Frame frame) {
    var value = frame.observation;
    return new Node(frame.reference, value.type(), value.message(), reference(value.cause()), reference(value.carrier()),
        reference(value.primary()), value.suppressed().stream().map(identities::require).toList(), value.operation());
  }

  private OptionalInt reference(Optional<Throwable> failure) {
    return failure.isEmpty() ? OptionalInt.empty() : OptionalInt.of(identities.require(failure.orElseThrow()));
  }

  private record Observation(String type, Optional<String> message, Optional<Throwable> cause, Optional<Throwable> carrier,
      Optional<Throwable> primary, List<Throwable> suppressed, Optional<Operation> operation) {
  }

  private static final class Frame {
    private final int reference;
    private final Observation observation;
    private final List<Throwable> outgoing;
    private int position;

    Frame(int reference, Observation observation, List<Throwable> outgoing) {
      this.reference = reference;
      this.observation = observation;
      this.outgoing = outgoing;
    }

    boolean hasNext() {
      return position < outgoing.size();
    }
    Throwable next() {
      var next = outgoing.get(position);
      position++;
      return next;
    }
  }

  private record FailureIdentities(Function<Throwable, OptionalInt> finder, ToIntFunction<Throwable> recorder) {
    static FailureIdentities usingReferenceIdentity() {
      var entries = new IdentityHashMap<Throwable, Integer>();
      return new FailureIdentities(failure -> {
        var reference = entries.get(failure);
        return reference == null ? OptionalInt.empty() : OptionalInt.of(reference);
      }, failure -> Objects.requireNonNull(entries.computeIfAbsent(failure, ignored -> entries.size() + 1)));
    }

    OptionalInt find(Throwable failure) {
      return finder.apply(failure);
    }
    int require(Throwable failure) {
      return find(failure).orElseThrow();
    }
    int remember(Throwable failure) {
      return recorder.applyAsInt(failure);
    }
  }
}
