package org.aquilagraph.cli;

import static org.aquilagraph.cli.AnalysisDiagnosticRenderer.quote;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AnalysisDiagnostics;
import org.aquilagraph.application.AnalysisDiagnosticOccurrence;
import org.aquilagraph.application.RequestQualificationReason;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.SourceQualificationReason.Subject;

/** Presents established qualification causes and their exact native event relationships. */
final class AnalysisQualificationRenderer {
  private static final String UNAVAILABLE = "unavailable";
  private static final String APPLICATION_OWNER = "APPLICATION";
  private final AnalysisRun run;
  private final AnalysisDiagnostics occurrences;
  private final AnalysisDiagnosticRenderer diagnostics;
  private final PrintStream error;

  AnalysisQualificationRenderer(AnalysisRun run, AnalysisDiagnosticRenderer diagnostics, PrintStream error) {
    this.run = run;
    occurrences = run.diagnostics();
    this.diagnostics = diagnostics;
    this.error = error;
  }

  void render() {
    var ast = run.representation().ast();
    for (var reason : ast.qualification().reasons()) {
      header("SOURCE", reason.category().name(), quote(ast.profile().version()), subject(reason.subject()));
      diagnostics.origins(reason.origins(), "  ");
      occurrences.relatedTo(reason).forEach(this::related);
      reason.evidence().forEach(evidence -> diagnostics.evidence(evidence.diagnostic()));
    }
    run.representation().cfg().ifPresent(this::cfg);
    for (var reason : run.qualification().reasons()) {
      application(reason);
    }
  }

  private void cfg(CfgOutcome outcome) {
    if (outcome.state() == CfgState.REJECTED) {
      cfgHeader("REJECTED", outcome.request().astIdentity().astResultKey(), outcome);
      diagnosticOrigins(outcome.diagnostics());
      occurrences.relatedTo(outcome).forEach(this::related);
    }
    for (var method : outcome.methods()) {
      if (method.state() == MethodCfgState.FAILED) {
        cfgHeader("FAILED", method.methodKey(), outcome);
        var failure = method.failure().orElseThrow();
        error.println("  Failure type: " + quote(failure.getClass().getName()));
        error.println(
            "  Failure message: " + Optional.ofNullable(failure.getMessage()).map(AnalysisDiagnosticRenderer::quote).orElse(UNAVAILABLE));
        diagnosticOrigins(method.diagnostics());
        relatedMethod(method);
      }
      for (var position = 0; position < method.blockers().size(); position++) {
        var blocker = method.blockers().get(position);
        cfgHeader(blocker.kind().name(), blocker.methodKey(), outcome);
        error.println("  Source occurrence: " + blocker.occurrence().map(value -> quote(value.nodeKey())).orElse(UNAVAILABLE));
        error.println("  Slot: " + quote(blocker.slot()));
        diagnostics.origins(blocker.diagnostic().origins(), "  ");
        related(occurrences.forBlocker(method, position));
        blocker.evidenceDiagnostic().ifPresent(diagnostics::evidence);
      }
    }
  }

  private void application(RequestQualificationReason reason) {
    switch (reason) {
      case RequestQualificationReason.Source source -> {
        header(APPLICATION_OWNER, "SOURCE", quote(source.outcome().profile().version()),
            source.outcome().result().map(result -> quote(result.identity().astResultKey())).orElse(UNAVAILABLE));
        diagnostics.origins(source.outcome().qualification().reasons().stream().flatMap(value -> value.origins().stream()).toList(), "  ");
      }
      case RequestQualificationReason.Selection selection -> {
        var outcome = selection.outcome();
        header(APPLICATION_OWNER, "SELECTION", quote(outcome.request().profile().version()),
            quote(outcome.request().astIdentity().astResultKey()));
        diagnosticOrigins(outcome.diagnostics());
        occurrences.relatedTo(selection).forEach(this::related);
      }
      case RequestQualificationReason.Method method -> {
        header(APPLICATION_OWNER, "METHOD", quote(run.representation().cfg().orElseThrow().request().profile().version()),
            quote(method.outcome().methodKey()));
        diagnosticOrigins(method.outcome().diagnostics());
        occurrences.relatedTo(method).forEach(this::related);
      }
      case RequestQualificationReason.MethodDiagnostic method -> {
        header(APPLICATION_OWNER, "METHOD_DIAGNOSTIC", quote(run.representation().cfg().orElseThrow().request().profile().version()),
            quote(method.outcome().methodKey()));
        var diagnostic = method.outcome().diagnostics().get(method.position());
        diagnostics.origins(diagnostic.origins(), "  ");
        occurrences.relatedTo(method).forEach(this::related);
      }
      case RequestQualificationReason.ApplicationDiagnostic application -> {
        header(APPLICATION_OWNER, "APPLICATION_DIAGNOSTIC", "not-applicable", UNAVAILABLE);
        diagnostics.origins(application.diagnostic().origins(), "  ");
        occurrences.relatedTo(application).forEach(this::related);
      }
    }
  }

  private void relatedMethod(MethodCfgOutcome method) {
    occurrences.relatedTo(method).forEach(this::related);
  }

  private void related(AnalysisDiagnosticOccurrence occurrence) {
    diagnostics.related(occurrence.diagnostic(), occurrences.positionOf(occurrence) + 1);
  }

  private void diagnosticOrigins(List<AnalysisDiagnostic> values) {
    diagnostics.origins(values.stream().flatMap(value -> value.origins().stream()).toList(), "  ");
  }

  private void cfgHeader(String category, String subject, CfgOutcome outcome) {
    header("CFG", category, quote(outcome.request().profile().version()), quote(subject));
    error.println("  AST result: " + quote(outcome.request().astIdentity().astResultKey()));
  }

  private void header(String owner, String category, String profile, String subject) {
    error.println("Qualification: " + owner + " " + category);
    error.println("  Profile: " + profile);
    error.println("  Subject: " + subject);
  }

  private static String subject(Subject subject) {
    return switch (subject) {
      case SourceQualificationReason.Capture _ -> UNAVAILABLE;
      case SourceQualificationReason.Node node -> quote(node.nodeKey());
      case SourceQualificationReason.Declaration declaration -> quote(declaration.key());
      case SourceQualificationReason.Fact fact -> quote(fact.reference().identity());
      case SourceQualificationReason.Context context -> {
        var address = context.address();
        var path = address.ownerPath().steps().stream().map(step -> step.role().name() + "/" + step.ordinal())
            .collect(Collectors.joining("/"));
        yield quote(
            address.sourceFileKey() + "/" + address.partition().name() + "/" + path + "/" + address.role() + "/" + address.ordinal());
      }
    };
  }
}
