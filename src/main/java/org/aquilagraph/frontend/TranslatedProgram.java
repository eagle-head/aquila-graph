package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.JavaProgram;
import org.aquilagraph.syntax.MethodSyntax;

record TranslatedProgram(JavaProgram program, List<MethodSyntax> methodSyntax) {
  TranslatedProgram {
    Objects.requireNonNull(program, "program");
    methodSyntax = List.copyOf(Objects.requireNonNull(methodSyntax, "methodSyntax"));
  }
}
