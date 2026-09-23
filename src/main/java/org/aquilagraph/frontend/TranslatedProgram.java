package org.aquilagraph.frontend;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.syntax.JavaAstAggregate;

record TranslatedProgram(JavaProgram program, Optional<JavaAstAggregate> aggregate) {
  TranslatedProgram {
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(aggregate, "aggregate");
  }
}
