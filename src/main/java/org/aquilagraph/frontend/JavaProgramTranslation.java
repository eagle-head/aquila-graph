package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.JavaProgram;

record JavaProgramTranslation(JavaProgram program, List<AnalysisDiagnostic> diagnostics) {
  JavaProgramTranslation {
    Objects.requireNonNull(program, "program");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
  }
}
