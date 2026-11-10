package org.aquilagraph.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.aquilagraph.application.AnalysisDiagnosticOccurrence;
import org.aquilagraph.application.AnalysisDiagnostics;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.RequestQualificationReason;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cli.AnalysisRunSummary.MethodCoverage;
import org.aquilagraph.cli.AnalysisRunSummary.MethodDescriptor;
import org.aquilagraph.cli.AnalysisRunSummary.ProgramSummary;
import org.aquilagraph.cli.ReportDiagnostic.Evidence;
import org.aquilagraph.cli.ReportDiagnostic.Link;
import org.aquilagraph.cli.ReportDiagnostic.Origin;
import org.aquilagraph.cli.ReportDiagnostic.Value;
import org.aquilagraph.cli.ReportQualification.ApplicationContext;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.SourceQualificationReason;

/** The boundary that reads operational results and captures detached presentation facts. */
class CommandReportProjector {
  private final FailureDetailProjector failureDetails;

  CommandReportProjector(FailureDetailProjector failureDetails) {
    Objects.requireNonNull(failureDetails, "failureDetails");
    super();
    this.failureDetails = failureDetails;
  }

  CommandReport project(AnalyzeCommandOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    var failures = new FailureGraphCapture(failureDetails);
    return switch (outcome) {
      case AnalyzeCommandOutcome.UsageRejected rejected -> new CommandReport.Usage(rejected.reason());
      case AnalyzeCommandOutcome.InputRejected rejected -> new CommandReport.Failed(CommandReport.FailureKind.INPUT,
          rejected.failure().description(), failures.capture(rejected.failure()), failures.catalog());
      case AnalyzeCommandOutcome.EnvironmentFailed failed -> new CommandReport.Failed(CommandReport.FailureKind.ENVIRONMENT,
          failed.failure().description(), failures.capture(failed.failure()), failures.catalog());
      case AnalyzeCommandOutcome.Executed executed -> new RunProjection(executed.execution().run(), failures).project(executed.execution());
    };
  }

  private static final class RunProjection {
    private final AnalysisRun run;
    private final AnalysisDiagnostics catalog;
    private final FailureGraphCapture failures;
    private final SourceSubjectProjector subjects = new SourceSubjectProjector();
    private final Map<String, String> files = new HashMap<>();

    RunProjection(AnalysisRun run, FailureGraphCapture failures) {
      this.run = run;
      catalog = run.diagnostics();
      this.failures = failures;
      run.representation().ast().capturedInput().ifPresent(
          snapshot -> snapshot.sourceFiles().forEach(source -> files.put(source.sourceFileKey(), source.relativePath().toString())));
    }

    CommandReport.Executed project(AnalysisExecution execution) {
      var summary = summary();
      var diagnostics = catalog.occurrences().stream().map(this::diagnostic).toList();
      var qualifications = qualifications();
      var publication = execution.publication();
      var failureReference = publication.failure().isEmpty()
          ? OptionalInt.empty()
          : OptionalInt.of(failures.capture(publication.failure().orElseThrow()));
      var observation = new CommandReport.Publication(publication.intent(), publication.subjectIdentity(), publication.status(),
          Optional.of(publication.knowledge()), failureReference,
          publication.failure().map(value -> value.effectivePrimary().description()),
          publication.intent() == PublicationIntent.SAVE_COMPLETED && publication.status() == PublicationStatus.NOT_ELIGIBLE
              ? run.publicationEligibility().impediments()
              : List.of());
      return new CommandReport.Executed(summary, diagnostics, qualifications, observation, failures.catalog());
    }

    private AnalysisRunSummary summary() {
      var ast = run.representation().ast();
      var software = ast.capturedInput()
          .flatMap(snapshot -> ast.capturedEnvironment().map(environment -> SoftwareVersion.from(snapshot, environment)));
      var program = ast.result().map(result -> {
        var graphs = run.representation().cfg().stream().flatMap(cfg -> cfg.methods().stream()).flatMap(method -> method.graph().stream())
            .toList();
        return new ProgramSummary(result.program().types().size(), result.program().methodCount(), graphs.size(),
            graphs.stream().mapToInt(graph -> graph.operationNodes().size()).sum(),
            graphs.stream().mapToInt(graph -> graph.nodeCount()).sum(), graphs.stream().mapToInt(graph -> graph.edges().size()).sum());
      });
      var methods = ast.result().map(source -> run.methodCoverages().stream().map(coverage -> {
        var key = coverage.declaration().methodKey();
        var descriptor = new MethodDescriptor(source.identity(), key, source.bodyRef(key));
        return new MethodCoverage(descriptor, coverage.state());
      }).toList()).orElseGet(List::of);
      return new AnalysisRunSummary(run.identity(), run.completion(), software,
          ast.requestedConfiguration().map(configuration -> configuration.release()),
          ast.capturedInput().map(snapshot -> snapshot.sourceFileCount()).orElse(0), ast.completion(), ast.profile(),
          run.representation().cfg().map(CfgOutcome::state), program, methods);
    }

    private ReportDiagnostic diagnostic(AnalysisDiagnosticOccurrence occurrence) {
      var owner = switch (occurrence.owner()) {
        case AnalysisDiagnosticOccurrence.Owner.Source _ -> new ReportDiagnostic.Owner.Source(run.identity().source());
        case AnalysisDiagnosticOccurrence.Owner.Request request -> new ReportDiagnostic.Owner.Request(request.outcome().request());
        case AnalysisDiagnosticOccurrence.Owner.Method method -> new ReportDiagnostic.Owner.Method(descriptor(method.outcome()));
        case AnalysisDiagnosticOccurrence.Owner.Application _ -> new ReportDiagnostic.Owner.Application(run.identity());
      };
      return new ReportDiagnostic(catalog.positionOf(occurrence) + 1, owner, occurrence.localPosition(), value(occurrence.diagnostic()));
    }

    private List<ReportQualification> qualifications() {
      var result = new ArrayList<ReportQualification>();
      result.addAll(run.representation().ast().qualification().reasons().stream().map(this::sourceReason).toList());
      run.representation().cfg().ifPresent(cfg -> cfg(cfg, result));
      for (var reason : run.qualification().reasons()) {
        result.add(application(reason));
      }
      return List.copyOf(result);
    }

    private ReportQualification.Source sourceReason(SourceQualificationReason reason) {
      var associated = catalog.relatedTo(reason);
      var links = IntStream.range(0, associated.size())
          .mapToObj(position -> new Link(catalog.positionOf(associated.get(position)) + 1, reason.diagnostics().get(position).emission()))
          .toList();
      var evidence = reason.evidence().stream().map(item -> new Evidence(Optional.of(item.role()), value(item.diagnostic()))).toList();
      return new ReportQualification.Source(reason.category(), reason.role(), run.representation().ast().profile(),
          subjects.project(reason.subject()), origins(reason.origins()), links, evidence);
    }

    private void cfg(CfgOutcome cfg, List<ReportQualification> result) {
      if (cfg.state() == CfgState.REJECTED) {
        result.add(new ReportQualification.Request(cfg.request(), diagnosticOrigins(cfg.diagnostics()), links(catalog.relatedTo(cfg))));
      }
      for (var method : cfg.methods()) {
        if (method.state() == MethodCfgState.FAILED) {
          result.add(new ReportQualification.Failed(descriptor(method), cfg.request().profile(),
              failures.capture(method.failure().orElseThrow()), diagnosticOrigins(method.diagnostics()), links(catalog.relatedTo(method))));
        }
        for (var position = 0; position < method.blockers().size(); position++) {
          var blocker = method.blockers().get(position);
          result.add(new ReportQualification.Blocker(descriptor(method), cfg.request().profile(), blocker.kind(), blocker.occurrence(),
              blocker.slot(), origins(blocker.diagnostic().origins()), List.of(link(catalog.forBlocker(method, position))),
              blocker.evidenceDiagnostic().map(diagnostic -> new Evidence(Optional.empty(), value(diagnostic)))));
        }
      }
    }

    private ReportQualification.Application application(RequestQualificationReason reason) {
      var context = switch (reason) {
        case RequestQualificationReason.Source source -> new ApplicationContext.Source(run.identity().source(), source.outcome().profile());
        case RequestQualificationReason.Selection selection -> new ApplicationContext.Selection(selection.outcome().request());
        case RequestQualificationReason.Method method -> new ApplicationContext.Method(descriptor(method.outcome()),
            run.representation().cfg().orElseThrow().request().profile(), OptionalInt.empty());
        case RequestQualificationReason.MethodDiagnostic method -> new ApplicationContext.Method(descriptor(method.outcome()),
            run.representation().cfg().orElseThrow().request().profile(), OptionalInt.of(method.position()));
        case RequestQualificationReason.ApplicationDiagnostic diagnostic -> new ApplicationContext.Diagnostic(diagnostic.position());
      };
      var locations = switch (reason) {
        case RequestQualificationReason.Source source ->
          origins(source.outcome().qualification().reasons().stream().flatMap(item -> item.origins().stream()).toList());
        case RequestQualificationReason.Selection selection -> diagnosticOrigins(selection.outcome().diagnostics());
        case RequestQualificationReason.Method method -> diagnosticOrigins(method.outcome().diagnostics());
        case RequestQualificationReason.MethodDiagnostic method -> origins(method.outcome().diagnostics().get(method.position()).origins());
        case RequestQualificationReason.ApplicationDiagnostic diagnostic -> origins(diagnostic.diagnostic().origins());
      };
      return new ReportQualification.Application(context, locations, links(catalog.relatedTo(reason)));
    }

    private static MethodDescriptor descriptor(MethodCfgOutcome method) {
      return new MethodDescriptor(method.astIdentity(), method.methodKey(), method.body());
    }

    private List<Link> links(List<AnalysisDiagnosticOccurrence> occurrences) {
      return occurrences.stream().map(this::link).toList();
    }
    private Link link(AnalysisDiagnosticOccurrence occurrence) {
      return new Link(catalog.positionOf(occurrence) + 1, Optional.empty());
    }
    private List<Origin> diagnosticOrigins(List<AnalysisDiagnostic> diagnostics) {
      return origins(diagnostics.stream().flatMap(diagnostic -> diagnostic.origins().stream()).toList());
    }
    private List<Origin> origins(List<SourceSpan> spans) {
      return spans.stream().map(span -> new Origin(span, Optional.ofNullable(files.get(span.sourceFileKey())))).toList();
    }
    private Value value(AnalysisDiagnostic diagnostic) {
      return new Value(diagnostic.diagnosticKey(), diagnostic.severity(), diagnostic.stage(), diagnostic.outcomeKind(), diagnostic.code(),
          diagnostic.message(), origins(diagnostic.origins()));
    }
  }
}
