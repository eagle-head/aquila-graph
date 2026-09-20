package org.aquilagraph.model;

import java.util.Objects;

public record MethodAnalysis(
    String analysisKey,
    JavaMethod sourceMethod,
    ControlFlowGraph controlFlowGraph) {
  public MethodAnalysis {
    Objects.requireNonNull(analysisKey, "analysisKey");
    Objects.requireNonNull(sourceMethod, "sourceMethod");
    Objects.requireNonNull(controlFlowGraph, "controlFlowGraph");
    if (!sourceMethod.methodKey().equals(controlFlowGraph.methodKey())) {
      throw new IllegalArgumentException(
          "Method analysis artifacts must describe the same source method");
    }
  }
}
