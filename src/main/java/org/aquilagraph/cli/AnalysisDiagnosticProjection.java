package org.aquilagraph.cli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.cfg.api.CfgBlocker;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue.Unsupported;

/** Projects the historical command-line diagnostic protocol without changing native diagnostic owners. */
final class AnalysisDiagnosticProjection {
  private static final String UNSUPPORTED_SYNTAX = "frontend.unsupported-syntax";
  private static final String UNSUPPORTED_MESSAGE = "Unsupported Java construct in method body: ";

  private AnalysisDiagnosticProjection() {
  }

  static List<DiagnosticSummary> from(AnalysisRun run) {
    var nativeDiagnostics = run.diagnostics();
    var ast = run.representation().ast().result();
    var cfg = run.representation().cfg();
    if (ast.isEmpty() || cfg.isEmpty() || !ast.orElseThrow().profile().equals(AstProfile.JAVA_SOURCE_BOUNDED)
        || !cfg.orElseThrow().request().profile().equals(CfgProfile.JAVA_BOUNDED)) {
      return nativeDiagnostics.stream().map(DiagnosticSummary::from).toList();
    }
    var source = ast.orElseThrow();
    var bodies = bodyPositions(source);
    var insertions = new ArrayList<Insertion>();
    var representedGraphEvents = new HashMap<String, Integer>();
    var pairedSourceSlots = new HashSet<Integer>();
    for (var method : cfg.orElseThrow().methods()) {
      for (var blocker : method.blockers()) {
        var occurrence = blocker.occurrence().flatMap(source::node);
        var body = bodies.stream().filter(value -> value.methodKey().equals(blocker.methodKey())).findFirst();
        if (occurrence.isEmpty() || body.isEmpty()) {
          continue;
        }
        var node = occurrence.orElseThrow();
        var target = parenthesizedTarget(blocker, node);
        if (target.isPresent()) {
          insertions
              .add(new Insertion(body.orElseThrow().ordinal(), target.orElseThrow(), new DiagnosticSummary(blocker.diagnostic().severity(),
                  "FRONTEND", UNSUPPORTED_SYNTAX, UNSUPPORTED_MESSAGE + "PARENTHESIZED")));
          representedGraphEvents.merge(blocker.diagnostic().diagnosticKey(), 1, Integer::sum);
        } else if (blocker.kind() == MethodCfgState.SOURCE_UNAVAILABLE && node.syntaxKind() == SyntaxKind.UNSUPPORTED
            && pairSourceOccurrence(source.diagnostics(), node, pairedSourceSlots)) {
          representedGraphEvents.merge(blocker.diagnostic().diagnosticKey(), 1, Integer::sum);
        }
      }
    }
    insertions.sort(Comparator.comparingInt(Insertion::methodPosition).thenComparingLong(value -> value.origin().startOffset()));
    var result = new ArrayList<DiagnosticSummary>();
    var nextInsertion = 0;
    for (var diagnostic : source.diagnostics()) {
      var position = bodyPosition(source, bodies, diagnostic);
      while (nextInsertion < insertions.size() && position.isPresent()
          && precedes(insertions.get(nextInsertion), position.orElseThrow(), diagnostic)) {
        result.add(insertions.get(nextInsertion).diagnostic());
        nextInsertion++;
      }
      result.add(DiagnosticSummary.from(diagnostic));
    }
    while (nextInsertion < insertions.size()) {
      result.add(insertions.get(nextInsertion).diagnostic());
      nextInsertion++;
    }
    // The application stream already owns canonical CFG order; source and application slots stay separate.
    var graphEnd = nativeDiagnostics.size() - run.applicationDiagnostics().size();
    for (var index = source.diagnostics().size(); index < graphEnd; index++) {
      var diagnostic = nativeDiagnostics.get(index);
      var remaining = representedGraphEvents.getOrDefault(diagnostic.diagnosticKey(), 0);
      if (remaining == 0) {
        result.add(DiagnosticSummary.from(diagnostic));
      } else {
        representedGraphEvents.put(diagnostic.diagnosticKey(), remaining - 1);
      }
    }
    if (representedGraphEvents.values().stream().anyMatch(value -> value != 0)) {
      throw new IllegalArgumentException("Projected CFG occurrences must exist in the application diagnostic stream");
    }
    run.applicationDiagnostics().forEach(value -> result.add(DiagnosticSummary.from(value)));
    return List.copyOf(result);
  }

  private static Optional<SourceSpan> parenthesizedTarget(CfgBlocker blocker, SyntaxNode node) {
    if (blocker.kind() != MethodCfgState.UNSUPPORTED || !"construct".equals(blocker.slot()) || node.syntaxKind() != SyntaxKind.ASSIGNMENT) {
      return Optional.empty();
    }
    var target = node.requiredChild(SyntaxRole.LOCATION);
    return target.syntaxKind() == SyntaxKind.PARENTHESIZED ? target.origin() : Optional.empty();
  }

  private static boolean pairSourceOccurrence(List<AnalysisDiagnostic> diagnostics, SyntaxNode node, Set<Integer> pairedSlots) {
    var message = UNSUPPORTED_MESSAGE + node.value(Unsupported.class).compilerKind();
    for (var index = 0; index < diagnostics.size(); index++) {
      var diagnostic = diagnostics.get(index);
      if (!pairedSlots.contains(index) && diagnostic.stage() == DiagnosticStage.FRONTEND && UNSUPPORTED_SYNTAX.equals(diagnostic.code())
          && diagnostic.isError() && diagnostic.message().equals(message) && node.origin().isPresent()
          && diagnostic.origins().equals(node.origin().stream().toList())) {
        pairedSlots.add(index);
        return true;
      }
    }
    return false;
  }

  private static List<BodyPosition> bodyPositions(AstResult source) {
    var result = new ArrayList<BodyPosition>();
    var methods = source.program().methods();
    for (var index = 0; index < methods.size(); index++) {
      var method = methods.get(index);
      var origin = source.body(method.methodKey()).flatMap(value -> value.root().origin());
      if (origin.isPresent()) {
        result.add(new BodyPosition(method.methodKey(), index, origin.orElseThrow()));
      }
    }
    return List.copyOf(result);
  }

  private static Optional<BodyPosition> bodyPosition(AstResult source, List<BodyPosition> bodies, AnalysisDiagnostic diagnostic) {
    if (diagnostic.stage() != DiagnosticStage.FRONTEND || source.coverage().isCapabilityDiagnostic(diagnostic)) {
      return Optional.empty();
    }
    return bodies.stream()
        .filter(body -> diagnostic.origins().stream().anyMatch(origin -> origin.sourceFileKey().equals(body.origin().sourceFileKey())
            && origin.startOffset() >= body.origin().startOffset() && origin.endOffsetExclusive() <= body.origin().endOffsetExclusive()))
        .findFirst();
  }

  private static boolean precedes(Insertion insertion, BodyPosition body, AnalysisDiagnostic diagnostic) {
    if (insertion.methodPosition() != body.ordinal()) {
      return insertion.methodPosition() < body.ordinal();
    }
    return diagnostic.origins().stream()
        .anyMatch(origin -> origin.sourceFileKey().equals(insertion.origin().sourceFileKey())
            && (insertion.origin().startOffset() < origin.startOffset() || (insertion.origin().startOffset() == origin.startOffset()
                && insertion.origin().endOffsetExclusive() >= origin.endOffsetExclusive())));
  }

  record DiagnosticSummary(DiagnosticSeverity severity, String stage, String code, String message) {
    DiagnosticSummary {
      Objects.requireNonNull(severity, "severity");
      Objects.requireNonNull(stage, "stage");
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(message, "message");
    }

    static DiagnosticSummary from(AnalysisDiagnostic diagnostic) {
      return new DiagnosticSummary(diagnostic.severity(), diagnostic.stage().name(), diagnostic.code(), diagnostic.message());
    }
  }

  private record BodyPosition(String methodKey, int ordinal, SourceSpan origin) {
  }

  private record Insertion(int methodPosition, SourceSpan origin, DiagnosticSummary diagnostic) {
  }
}
