package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.application.RequestQualificationReason;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgState;

final class NativeReportAssertions {
  private NativeReportAssertions() {}

  static List<String> assertNativeReport(AnalysisRun run, CliStreams streams) {
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
    var expectedAstResults = cfg.map(value -> Collections.nCopies(expectedCfg.size(),
        "  AST result: \"" + value.request().astIdentity().astResultKey() + "\"")).orElseGet(List::of);
    assertEquals(expectedAstResults, lines.stream().filter(line -> line.startsWith("  AST result:")).toList());
    assertEquals(blockers.stream().map(blocker -> "  Source occurrence: " + blocker.occurrence()
        .map(value -> "\"" + value.nodeKey() + "\"").orElse("unavailable")).toList(),
        lines.stream().filter(line -> line.startsWith("  Source occurrence:")).toList());
    assertEquals(blockers.stream().map(blocker -> "  Slot: \"" + blocker.slot() + "\"").toList(),
        lines.stream().filter(line -> line.startsWith("  Slot:")).toList());
    return lines;
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
      if (line.startsWith("Qualification: ")) {
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
