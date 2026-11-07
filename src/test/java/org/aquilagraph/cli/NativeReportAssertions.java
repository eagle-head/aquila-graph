package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.AnalysisDiagnosticOccurrence;
import org.aquilagraph.application.RequestQualificationReason;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgState;

final class NativeReportAssertions {
  private NativeReportAssertions() {}

  static List<String> assertNativeReport(AnalysisRun run, CliStreams streams) {
    var report = CliTestFixtures.report(run);
    var nativeOccurrences = run.diagnostics().occurrences();
    assertEquals(nativeOccurrences.size(), report.diagnostics().size());
    for (var position = 0; position < nativeOccurrences.size(); position++) {
      var original = nativeOccurrences.get(position);
      var captured = report.diagnostics().get(position);
      assertEquals(position + 1, captured.reference());
      assertOwner(run, original.owner(), captured.owner());
      assertEquals(original.localPosition(), captured.nativePosition());
      assertEquals(original.diagnostic().diagnosticKey(), captured.value().key());
      assertEquals(original.diagnostic().origins(), captured.value().origins().stream().map(ReportDiagnostic.Origin::span).toList());
    }
    var sourceReasons = report.qualifications().stream().filter(ReportQualification.Source.class::isInstance)
        .map(ReportQualification.Source.class::cast).toList();
    var nativeReasons = run.representation().ast().qualification().reasons();
    assertEquals(nativeReasons.size(), sourceReasons.size());
    for (var position = 0; position < nativeReasons.size(); position++) {
      var original = nativeReasons.get(position);
      var captured = sourceReasons.get(position);
      assertEquals(original.category(), captured.category());
      assertEquals(original.role(), captured.role());
      assertEquals(original.origins(), captured.origins().stream().map(ReportDiagnostic.Origin::span).toList());
      assertEquals(original.diagnostics().stream().map(reference -> reference.emission()).toList(),
          captured.links().stream().map(ReportDiagnostic.Link::emission).toList());
      assertEquals(run.diagnostics().relatedTo(original).stream().map(occurrence -> run.diagnostics().positionOf(occurrence) + 1).toList(),
          captured.links().stream().map(ReportDiagnostic.Link::reference).toList());
    }
    var events = run.diagnostics().values();
    assertEquals(run.diagnostics().values().stream().map(value -> "Diagnostic: \"" + value.diagnosticKey() + "\"").toList(),
        streams.errorLines().stream().filter(line -> line.startsWith("Diagnostic: ")).toList());
    assertEquals(run.diagnostics().values().stream().map(value -> "  Stage: " + value.stage()).toList(),
        streams.errorLines().stream().filter(line -> line.startsWith("  Stage: ")).toList());
    assertEquals(IntStream.rangeClosed(1, events.size()).mapToObj(position -> "  Occurrence: " + position).toList(),
        streams.errorLines().stream().filter(line -> line.startsWith("  Occurrence: ")).toList());
    var lines = streams.errorLines();
    assertEquals(run.diagnostics().values().stream().map(value -> "  Code: \"" + value.code() + "\"").toList(),
        lines.stream().filter(line -> line.startsWith("  Code:")).toList());
    assertEquals(run.diagnostics().values().stream().map(value -> "  Severity: " + value.severity()).toList(),
        lines.stream().filter(line -> line.startsWith("  Severity:")).toList());
    assertEquals(run.diagnostics().values().stream().map(value -> "  Cause: " + value.outcomeKind()).toList(),
        lines.stream().filter(line -> line.startsWith("  Cause:")).toList());
    assertEquals(run.representation().ast().qualification().reasons().stream().map(reason -> "Qualification: SOURCE " + reason.category()).toList(),
        lines.stream().filter(line -> line.startsWith("Qualification: SOURCE ")).toList());
    var expectedCfg = new ArrayList<String>();
    run.representation().cfg().ifPresent(cfg -> {
      if (cfg.state() == CfgState.REJECTED) { expectedCfg.add("Qualification: CFG REJECTED"); }
      cfg.methods().forEach(method -> {
        if (method.state() == MethodCfgState.FAILED) { expectedCfg.add("Qualification: CFG FAILED"); }
        method.blockers().forEach(blocker -> expectedCfg.add("Qualification: CFG " + blocker.kind()));
      });
    });
    assertEquals(expectedCfg, lines.stream().filter(line -> line.startsWith("Qualification: CFG ")).toList());
    assertEquals(run.qualification().reasons().stream().map(reason -> "Qualification: APPLICATION " + switch (reason) {
      case RequestQualificationReason.Source _ -> "SOURCE";
      case RequestQualificationReason.Selection _ -> "SELECTION";
      case RequestQualificationReason.Method _ -> "METHOD";
      case RequestQualificationReason.MethodDiagnostic _ -> "METHOD_DIAGNOSTIC";
      case RequestQualificationReason.ApplicationDiagnostic _ -> "APPLICATION_DIAGNOSTIC";
    }).toList(), lines.stream().filter(line -> line.startsWith("Qualification: APPLICATION ")).toList());
    var sourceReferences = run.representation().ast().qualification().reasons().stream()
        .flatMap(reason -> reason.diagnostics().stream())
        .map(reference -> "  Related diagnostic: \"" + reference.diagnostic().diagnosticKey() + "\" occurrence=" + (reference.position() + 1)).toList();
    assertEquals(sourceReferences, relatedFromOwner(lines, "SOURCE"));
    var cfg = run.representation().cfg();
    var blockers = cfg.stream().flatMap(value -> value.methods().stream()).flatMap(method -> method.blockers().stream()).toList();
    var applicationMethods = run.qualification().reasons().stream()
        .filter(reason -> reason instanceof RequestQualificationReason.Method || reason instanceof RequestQualificationReason.MethodDiagnostic).count();
    var expectedAstResults = cfg.map(value -> Collections.nCopies(expectedCfg.size() + Math.toIntExact(applicationMethods),
        "  AST result: \"" + value.request().astIdentity().astResultKey() + "\"")).orElseGet(List::of);
    assertEquals(expectedAstResults, lines.stream().filter(line -> line.startsWith("  AST result:")).toList());
    assertEquals(blockers.stream().map(blocker -> "  Source occurrence: " + blocker.occurrence()
        .map(value -> "\"" + value.nodeKey() + "\"").orElse("unavailable")).toList(),
        lines.stream().filter(line -> line.startsWith("  Source occurrence:")).toList());
    assertEquals(blockers.stream().map(blocker -> "  Slot: \"" + blocker.slot() + "\"").toList(),
        lines.stream().filter(line -> line.startsWith("  Slot:")).toList());
    return lines;
  }

  private static void assertOwner(AnalysisRun run, AnalysisDiagnosticOccurrence.Owner original, ReportDiagnostic.Owner captured) {
    switch (original) {
      case AnalysisDiagnosticOccurrence.Owner.Source _ ->
          assertEquals(run.identity().source(), assertInstanceOf(ReportDiagnostic.Owner.Source.class, captured).subject());
      case AnalysisDiagnosticOccurrence.Owner.Request request ->
          assertEquals(request.outcome().request(), assertInstanceOf(ReportDiagnostic.Owner.Request.class, captured).request());
      case AnalysisDiagnosticOccurrence.Owner.Method method -> {
        var descriptor = assertInstanceOf(ReportDiagnostic.Owner.Method.class, captured).method();
        assertEquals(method.outcome().astIdentity(), descriptor.astIdentity());
        assertEquals(method.outcome().methodKey(), descriptor.methodKey());
        assertEquals(method.outcome().body(), descriptor.body());
      }
      case AnalysisDiagnosticOccurrence.Owner.Application _ ->
          assertEquals(run.identity(), assertInstanceOf(ReportDiagnostic.Owner.Application.class, captured).identity());
    }
  }

  static List<String> relatedFromOwner(List<String> lines, String owner) {
    return qualificationBlocks(lines, owner).stream().flatMap(List::stream)
        .filter(line -> line.startsWith("  Related diagnostic:")).toList();
  }

  static List<List<String>> qualificationBlocks(List<String> lines, String owner) {
    var result = new ArrayList<List<String>>();
    var current = new ArrayList<String>();
    var selected = false;
    for (var line : lines) {
      if (line.startsWith("Qualification: ") || (selected && !line.startsWith(" "))) {
        if (selected) { result.add(List.copyOf(current)); }
        current.clear();
        selected = line.startsWith("Qualification: " + owner + " ");
      }
      if (selected) { current.add(line); }
    }
    if (selected) { result.add(List.copyOf(current)); }
    return List.copyOf(result);
  }

}
