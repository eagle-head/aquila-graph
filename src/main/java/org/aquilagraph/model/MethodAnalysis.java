package org.aquilagraph.model;

import java.util.Objects;

/**
 * Associates a translated source method with its validated control-flow graph.
 *
 * @param analysisKey
 *          stable method-analysis identifier
 * @param sourceMethod
 *          analyzed source method
 * @param controlFlowGraph
 *          resulting method graph
 */
public record MethodAnalysis(String analysisKey, JavaMethod sourceMethod, ControlFlowGraph controlFlowGraph) {
  /** Validates method-analysis identity and graph ownership. */
  public MethodAnalysis {
    Objects.requireNonNull(analysisKey, "analysisKey");
    Objects.requireNonNull(sourceMethod, "sourceMethod");
    Objects.requireNonNull(controlFlowGraph, "controlFlowGraph");
    if (!sourceMethod.methodKey().equals(controlFlowGraph.methodKey())) {
      throw new IllegalArgumentException("Method analysis artifacts must describe the same source method");
    }
  }
}
