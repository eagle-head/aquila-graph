package org.aquilagraph.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisRun;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.MethodAnalysis;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.ProgramRepresentation;
import org.aquilagraph.model.SoftwareVersion;
import org.aquilagraph.model.StableId;
import org.aquilagraph.syntax.SyntaxAnalysisInput;

/** Builds the accepted program representation and per-method control-flow graphs. */
public final class AnalysisEngine {
  private final ControlFlowGraphBuilder cfgBuilder;

  /** Creates an engine with the standard control-flow graph builder. */
  public AnalysisEngine() {
    this(new ControlFlowGraphBuilder());
  }

  /**
   * Creates an engine with an explicit graph builder.
   *
   * @param cfgBuilder
   *          method graph builder
   */
  public AnalysisEngine(ControlFlowGraphBuilder cfgBuilder) {
    this.cfgBuilder = Objects.requireNonNull(cfgBuilder, "cfgBuilder");
  }

  /**
   * Analyzes translated Java input and determines run completion.
   *
   * @param input
   *          transient syntax analysis input
   * @return analysis run identified by the captured software target
   */
  public AnalysisRun analyze(SyntaxAnalysisInput input) {
    Objects.requireNonNull(input, "input");
    var diagnostics = new ArrayList<>(input.diagnostics());
    var softwareVersion = SoftwareVersion.from(input.snapshot(), input.environmentSnapshot());
    var runId = StableId.sha256("analysis-run", softwareVersion.signature());

    if (input.snapshot().sourceFiles().isEmpty()) {
      diagnostics.add(diagnostic(runId, "input.empty", DiagnosticStage.INPUT, "The source root does not contain Java source files"));
      return new AnalysisRun(runId, input.snapshot(), input.environmentSnapshot(), Optional.empty(), diagnostics,
          AnalysisCompletion.REJECTED);
    }

    if (input.program().types().isEmpty()) {
      diagnostics.add(diagnostic(runId, "frontend.no-types", DiagnosticStage.FRONTEND, "No Java type declaration could be accepted"));
      return new AnalysisRun(runId, input.snapshot(), input.environmentSnapshot(), Optional.empty(), diagnostics,
          AnalysisCompletion.REJECTED);
    }

    var analyses = new ArrayList<MethodAnalysis>();
    for (var method : input.program().methods()) {
      if (!method.hasBody()) {
        continue;
      }
      try {
        var methodSyntax = input.methodSyntax(method.methodKey()).orElseThrow();
        var graphResult = cfgBuilder.build(method, methodSyntax);
        diagnostics.addAll(graphResult.diagnostics());
        if (graphResult.graph().isEmpty()) {
          continue;
        }
        var cfg = graphResult.graph().orElseThrow();
        analyses.add(new MethodAnalysis(StableId.sha256("method-analysis", method.methodKey()), method, cfg));
      } catch (IllegalArgumentException exception) {
        diagnostics.add(diagnostic(method.methodKey(), "control-flow.invalid", DiagnosticStage.CONTROL_FLOW,
            Objects.toString(exception.getMessage(), exception.getClass().getSimpleName())));
      }
    }

    var representation = new ProgramRepresentation(input.program(), analyses);
    var requiredMethodKeys = input.program().methods().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey)
        .collect(Collectors.toSet());
    var analyzedMethodKeys = analyses.stream().map(analysis -> analysis.sourceMethod().methodKey()).collect(Collectors.toSet());
    var fullyCovered = input.languageFeatureCoverageResult().observations().stream()
        .noneMatch(observation -> observation.classification().blocksCompletion());
    var complete = fullyCovered && diagnostics.stream().noneMatch(AnalysisDiagnostic::isError)
        && analyzedMethodKeys.equals(requiredMethodKeys);
    return new AnalysisRun(runId, input.snapshot(), input.environmentSnapshot(), Optional.of(representation), diagnostics,
        complete ? AnalysisCompletion.COMPLETED : AnalysisCompletion.INCOMPLETE);
  }

  private static AnalysisDiagnostic diagnostic(String context, String code, DiagnosticStage stage, String message) {
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", context, code, message), code, stage, DiagnosticSeverity.ERROR, message,
        List.of());
  }
}
