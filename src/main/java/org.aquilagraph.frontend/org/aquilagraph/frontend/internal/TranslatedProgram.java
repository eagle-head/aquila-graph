package org.aquilagraph.frontend.internal;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaAstAggregate;

record TranslatedProgram(JavaProgram program, Optional<JavaAstAggregate> aggregate) {
  TranslatedProgram {
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(aggregate, "aggregate");
  }
}
