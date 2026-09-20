package org.aquilagraph.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public record ProgramRepresentation(JavaProgram program, List<MethodAnalysis> methodAnalyses) {
  public ProgramRepresentation {
    Objects.requireNonNull(program, "program");
    methodAnalyses = List.copyOf(Objects.requireNonNull(methodAnalyses, "methodAnalyses"));
    var methodsByKey =
        program.methods().stream()
            .collect(Collectors.toMap(JavaMethod::methodKey, method -> method));
    var analyzedMethodKeys = new HashSet<String>();
    for (var analysis : methodAnalyses) {
      var sourceMethod = analysis.sourceMethod();
      var canonicalMethod = methodsByKey.get(sourceMethod.methodKey());
      if (!sourceMethod.equals(canonicalMethod)) {
        throw new IllegalArgumentException(
            "Method analysis must reference the canonical program method");
      }
      if (!analyzedMethodKeys.add(sourceMethod.methodKey())) {
        throw new IllegalArgumentException(
            "Each program method may have at most one method analysis");
      }
    }
  }

  public Optional<MethodAnalysis> methodAnalysis(String methodKey) {
    Objects.requireNonNull(methodKey, "methodKey");
    return methodAnalyses.stream()
        .filter(analysis -> analysis.sourceMethod().methodKey().equals(methodKey))
        .findFirst();
  }
}
