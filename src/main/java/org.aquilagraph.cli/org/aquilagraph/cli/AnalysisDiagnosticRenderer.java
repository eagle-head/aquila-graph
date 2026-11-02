package org.aquilagraph.cli;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;

/** Renders native diagnostic values and captured source locations without interpreting their text. */
final class AnalysisDiagnosticRenderer {
  private final Map<String, String> files;
  private final PrintStream error;

  AnalysisDiagnosticRenderer(AnalysisRun run, PrintStream error) {
    this.error = error;
    files = new HashMap<>();
    run.representation().ast().capturedInput().ifPresent(
        snapshot -> snapshot.sourceFiles().forEach(source -> files.put(source.sourceFileKey(), source.relativePath().toString())));
  }

  void nativeEvent(AnalysisDiagnostic diagnostic, int occurrence) {
    error.println("Diagnostic: " + quote(diagnostic.diagnosticKey()));
    error.println("  Occurrence: " + occurrence);
    fields(diagnostic, "  ");
  }

  void evidence(AnalysisDiagnostic diagnostic) {
    error.println("  Evidence diagnostic: " + quote(diagnostic.diagnosticKey()));
    fields(diagnostic, "    ");
  }

  private void fields(AnalysisDiagnostic diagnostic, String indent) {
    error.println(indent + "Severity: " + diagnostic.severity());
    error.println(indent + "Stage: " + diagnostic.stage());
    error.println(indent + "Cause: " + diagnostic.outcomeKind());
    error.println(indent + "Code: " + quote(diagnostic.code()));
    error.println(indent + "Message: " + quote(diagnostic.message()));
    origins(diagnostic.origins(), indent);
  }

  void origins(List<SourceSpan> origins, String indent) {
    if (origins.isEmpty()) {
      error.println(indent + "Origins: unavailable");
    }
    for (var origin : origins) {
      var file = files.get(origin.sourceFileKey());
      error.println(indent + "Origin: " + quote(origin.sourceFileKey()) + " file=" + (file == null ? "unavailable" : quote(file))
          + " range=[" + origin.startOffset() + "," + origin.endOffsetExclusive() + ")");
    }
  }

  void related(AnalysisDiagnostic diagnostic, int occurrence) {
    error.println("  Related diagnostic: " + quote(diagnostic.diagnosticKey()) + " occurrence=" + occurrence);
  }

  static String quote(String value) {
    var result = new StringBuilder("\"");
    for (var index = 0; index < value.length(); index++) {
      var character = value.charAt(index);
      switch (character) {
        case '\\' -> result.append("\\\\");
        case '"' -> result.append("\\\"");
        case '\r' -> result.append("\\r");
        case '\n' -> result.append("\\n");
        case '\t' -> result.append("\\t");
        default -> {
          if (character < ' ' || character == '\u007f') {
            result.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
          } else {
            result.append(character);
          }
        }
      }
    }
    return result.append('"').toString();
  }
}
