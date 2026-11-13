package org.aquilagraph.frontend.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.JavaTypeIdentity;

record ConstructorCorrelation(Status status, Optional<SelectedConstructorFacts> facts) {
  ConstructorCorrelation {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(facts, "facts");
    if ((status == Status.SELECTED) != facts.isPresent()) {
      throw new IllegalArgumentException("Only a selected witness may retain constructor facts");
    }
  }

  static ConstructorCorrelation selected(SelectedConstructorFacts facts) {
    return new ConstructorCorrelation(Status.SELECTED, Optional.of(facts));
  }

  static ConstructorCorrelation inapplicable() {
    return new ConstructorCorrelation(Status.INAPPLICABLE, Optional.empty());
  }

  static ConstructorCorrelation rejected() {
    return new ConstructorCorrelation(Status.WITNESS_REJECTED, Optional.empty());
  }

  static ConstructorCorrelation unavailable() {
    return new ConstructorCorrelation(Status.UNAVAILABLE, Optional.empty());
  }

  enum Status {
    SELECTED, INAPPLICABLE, UNAVAILABLE, WITNESS_REJECTED
  }

  record SelectedConstructorFacts(InvocationApplicabilityMode mode, int formalCount, List<ArgumentFacts> arguments,
      Optional<JavaTypeIdentity> varargArrayType, Optional<JavaTypeIdentity> erasedArrayType, Optional<JavaTypeIdentity> elementType) {
    SelectedConstructorFacts {
      Objects.requireNonNull(mode, "mode");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(varargArrayType, "varargArrayType");
      Objects.requireNonNull(erasedArrayType, "erasedArrayType");
      Objects.requireNonNull(elementType, "elementType");
    }
  }

  record ArgumentFacts(JavaTypeIdentity sourceType, JavaTypeIdentity selectedTarget, Optional<JavaTypeIdentity> unboxedType,
      Optional<JavaTypeIdentity> effectivePrimitiveType, Optional<JavaTypeIdentity> preBoxPrimitiveType,
      Optional<JavaTypeIdentity> boxedType) {
    ArgumentFacts {
      Objects.requireNonNull(sourceType, "sourceType");
      Objects.requireNonNull(selectedTarget, "selectedTarget");
      Objects.requireNonNull(unboxedType, "unboxedType");
      Objects.requireNonNull(effectivePrimitiveType, "effectivePrimitiveType");
      Objects.requireNonNull(preBoxPrimitiveType, "preBoxPrimitiveType");
      Objects.requireNonNull(boxedType, "boxedType");
    }
  }
}
