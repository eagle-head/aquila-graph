package org.aquilagraph.cli;

import static org.aquilagraph.cli.ReportDocument.number;
import static org.aquilagraph.cli.ReportDocument.sequence;
import static org.aquilagraph.cli.ReportDocument.text;
import static org.aquilagraph.cli.ReportDocument.token;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.NotRequested;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cli.ReportDocument.Absence;
import org.aquilagraph.cli.ReportDocument.Field;
import org.aquilagraph.cli.ReportDocument.Node;
import org.aquilagraph.cli.ReportDocument.Section;
import org.aquilagraph.cli.ReportDocument.Sequence;
import org.aquilagraph.cli.ReportDocument.Value;
import org.aquilagraph.identity.api.JavaRelease;

/** Arranges semantic report facts into one complete compositional document. */
class CommandDocumentComposer {
  private final DiagnosticDocumentComposer diagnostics = new DiagnosticDocumentComposer();
  private final QualificationDocumentComposer qualifications = new QualificationDocumentComposer();
  private final FailureDocumentComposer failures = new FailureDocumentComposer();

  ReportDocument compose(CommandReport report) {
    Objects.requireNonNull(report, "report");
    var output = new ArrayList<Node>();
    var error = new ArrayList<Node>();
    switch (report) {
      case CommandReport.Usage usage -> {
        error.add(new Field("", usage(usage.reason())));
        error.add(new Field("", token(AnalyzeCommandParser.USAGE)));
      }
      case CommandReport.Failed failed -> {
        error.add(
            new Field(failed.kind() == CommandReport.FailureKind.INPUT ? "Input rejected" : "Analysis failed", text(failed.description())));
        error.add(new Field("Failure reference", number(failed.failureReference())));
      }
      case CommandReport.Executed executed -> {
        summary(executed.analysis(), output);
        executed.diagnostics().forEach(diagnostic -> error.add(diagnostics.nativeEvent(diagnostic)));
        executed.qualifications().forEach(reason -> error.add(qualifications.compose(reason, executed.diagnostics(), executed.failures())));
        var publication = executed.publication();
        output.add(new Field("Repository", token(publication.status().name())));
        output.add(new Field("Publication intent", token(publication.intent().name())));
        PublicationDocumentComposer.subject("Publication", publication.subject(), output);
        output.add(
            new Field("Commit knowledge", publication.knowledge().map(knowledge -> token(knowledge.name())).orElse(Absence.UNAVAILABLE)));
        if (publication.intent() == PublicationIntent.SAVE_COMPLETED && publication.status() == PublicationStatus.NOT_ELIGIBLE) {
          var reasons = new ArrayList<Value>();
          for (var impediment : publication.impediments()) {
            if (!reasons.isEmpty()) {
              reasons.add(token(", "));
            }
            reasons.add(token(impediment.name()));
          }
          output.add(new Field("Publication impediments", new Sequence(reasons)));
        }
        publication.failureReference().ifPresent(reference -> {
          error.add(new Field("Repository save failed", text(publication.failureDescription().orElseThrow())));
          error.add(new Field("Failure reference", number(reference)));
        });
      }
    }
    error.add(failures.compose(report.failures()));
    return new ReportDocument(new Section(output), new Section(error));
  }

  private static void summary(AnalysisRunSummary summary, List<Node> output) {
    output.add(new Field("Run", text(summary.identity().key())));
    output.add(new Field("Software version", summary.software().map(software -> text(software.signature())).orElse(Absence.UNAVAILABLE)));
    output.add(new Field("Completion", token(summary.completion().name())));
    output.add(new Field("Java release", summary.release().map(release -> number(release.feature())).orElse(Absence.UNAVAILABLE)));
    output.add(new Field("Java source files", number(summary.sourceFileCount())));
    output.add(new Field("AST", token(summary.sourceState().name())));
    output.add(new Field("AST profile", text(summary.sourceProfile().version())));
    output.add(new Field("CFG", summary.cfgState().map(state -> token(state.name()))
        .orElseGet(() -> token(summary.identity().selection() instanceof NotRequested ? "NOT_REQUESTED" : "UNAVAILABLE"))));
    switch (summary.identity().selection()) {
      case NotRequested _ -> {
        output.add(new Field("CFG profile", Absence.NOT_REQUESTED));
        output.add(new Field("CFG graph model", Absence.NOT_REQUESTED));
        output.add(new Field("CFG selection", token("NOT_REQUESTED")));
        output.add(new Field("CFG binding", token("NOT_REQUESTED")));
      }
      case ProductionSelection production -> selection(production.selection().profile(), production.selection().scope(),
          production.boundIdentity().map(identity -> text(identity.astResultKey())).orElseGet(() -> token("UNBOUND")),
          production.methodKeys(), output);
      case ExistingSelection existing -> selection(existing.request().profile(), CfgSelection.Scope.METHODS,
          text(existing.request().astIdentity().astResultKey()), existing.request().methodKeys(), output);
    }
    summary.program().ifPresent(program -> {
      output.add(new Field("Java types", number(program.typeCount())));
      output.add(new Field("Java methods", number(program.methodCount())));
      output.add(new Field("Analyzed method bodies", number(program.analyzedMethodBodyCount())));
      output.add(new Field("CFG operations", number(program.controlFlowOperationCount())));
      output.add(new Field("CFG nodes", number(program.controlFlowNodeCount())));
      output.add(new Field("CFG edges", number(program.controlFlowEdgeCount())));
    });
    summary.methods().forEach(method -> output
        .add(new Field("Method CFG", sequence(text(method.method().methodKey()), token(" "), token(method.state().name())))));
  }

  private static void selection(CfgProfile profile, CfgSelection.Scope scope, Value binding, List<String> methods, List<Node> output) {
    output.add(new Field("CFG profile", text(profile.version())));
    output.add(new Field("CFG graph model", text(profile.graphModelVersion())));
    output.add(new Field("CFG selection", token(scope.name())));
    output.add(new Field("CFG binding", binding));
    methods.forEach(method -> output.add(new Field("Selected method", text(method))));
  }

  private static Value usage(ArgumentRejection reason) {
    return switch (reason) {
      case ArgumentRejection.DuplicateOption duplicate -> sequence(token(duplicate.option()), token(" may be supplied only once"));
      case ArgumentRejection.MissingRelease _ -> token("--release requires a Java feature-release number");
      case ArgumentRejection.MalformedRelease malformed -> sequence(token("Invalid Java release: "), text(malformed.value()));
      case ArgumentRejection.UnsupportedRelease unsupported -> token(JavaRelease.unsupportedFeatureMessage(unsupported.feature()));
      case ArgumentRejection.UnknownOption unknown -> sequence(token("Unknown option: "), text(unknown.option()));
      case ArgumentRejection.RootCount _ -> token("Exactly one source root is required");
      case ArgumentRejection.InvalidRoot invalid -> sequence(token("Invalid source root: "), text(invalid.value()));
    };
  }
}
