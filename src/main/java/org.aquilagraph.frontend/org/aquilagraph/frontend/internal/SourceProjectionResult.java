package org.aquilagraph.frontend.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.aquilagraph.source.api.DiagnosticEmission;

/** A projected source value or the exact event that refused its producing operation. */
record SourceProjectionResult<T>(Optional<T> value, Optional<DiagnosticEmission> failure) {
  SourceProjectionResult {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(failure, "failure");
    if (value.isPresent() == failure.isPresent()) {
      throw new IllegalArgumentException("Source projection requires exactly one value or failure event");
    }
  }

  static <T> SourceProjectionResult<T> success(T value) {
    return new SourceProjectionResult<>(Optional.of(value), Optional.empty());
  }

  static <T> SourceProjectionResult<T> failure(DiagnosticEmission event) {
    return new SourceProjectionResult<>(Optional.empty(), Optional.of(event));
  }

  <U> SourceProjectionResult<U> refusal() {
    return failure(failure.orElseThrow());
  }

  <U> SourceProjectionResult<U> map(Function<? super T, ? extends U> mapper) {
    Objects.requireNonNull(mapper, "mapper");
    return value.isPresent() ? success(mapper.apply(value.orElseThrow())) : refusal();
  }

  static Optional<DiagnosticEmission> firstFailure(Optional<DiagnosticEmission> first, Optional<DiagnosticEmission> next) {
    return first.isPresent() ? first : next;
  }
}
