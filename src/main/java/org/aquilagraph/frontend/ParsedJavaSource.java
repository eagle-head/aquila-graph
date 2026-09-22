package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import java.util.List;
import java.util.Objects;

/** Parsed source trees and source-position evidence captured before compiler attribution. */
record ParsedJavaSource(List<CompilationUnitTree> units, JavaSourceEvidence sourceEvidence) {
  ParsedJavaSource {
    units = List.copyOf(Objects.requireNonNull(units, "units"));
    Objects.requireNonNull(sourceEvidence, "sourceEvidence");
  }
}
