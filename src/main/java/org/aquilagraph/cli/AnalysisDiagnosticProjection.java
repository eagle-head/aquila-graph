package org.aquilagraph.cli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue.Unsupported;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.ReferenceKind;

/** Projects the historical command-line diagnostic protocol without changing native diagnostic owners. */
final class AnalysisDiagnosticProjection {
  private static final String UNSUPPORTED_SYNTAX = "frontend.unsupported-syntax";
  private static final String UNSUPPORTED_MESSAGE = "Unsupported Java construct in method body: ";
  private static final String TREE_DIAGNOSTIC_PREFIX = "frontend.unsupported-tree-kind.";
  private static final Set<String> EXPANDED_STATEMENT_KINDS = Set.of("WHILE_LOOP", "DO_WHILE_LOOP", "FOR_LOOP", "ENHANCED_FOR_LOOP",
      "LABELED_STATEMENT", "BREAK", "CONTINUE", "ASSERT", "SYNCHRONIZED", "LAMBDA_EXPRESSION", "MEMBER_REFERENCE", "INSTANCE_OF",
      "BINDING_PATTERN", "DECONSTRUCTION_PATTERN", "ANY_PATTERN", "SWITCH", "PATTERN_CASE_LABEL");

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
    var statementRoots = statementRoots(source);
    var catalog = catalogProjection(source);
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
        var statement = statementRoots.stream().filter(root -> projectedBy(root, node.origin())).findFirst();
        if (statement.isPresent() && blocker.kind() == MethodCfgState.UNSUPPORTED) {
          if (node.equals(statement.orElseThrow())) {
            insertions.add(new Insertion(body.orElseThrow().ordinal(), node.origin().orElseThrow(), new DiagnosticSummary(
                DiagnosticSeverity.ERROR, "FRONTEND", UNSUPPORTED_SYNTAX, UNSUPPORTED_MESSAGE + node.sourceKind().name())));
          }
          representedGraphEvents.merge(blocker.diagnostic().diagnosticKey(), 1, Integer::sum);
          continue;
        }
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
    var catalogInserted = false;
    for (var diagnostic : source.diagnostics()) {
      if (!catalog.isEmpty() && !catalogInserted && !diagnostic.code().startsWith("compiler.")) {
        result.addAll(catalog);
        catalogInserted = true;
      }
      if ((!catalog.isEmpty() && diagnostic.code().startsWith(TREE_DIAGNOSTIC_PREFIX))
          || (UNSUPPORTED_SYNTAX.equals(diagnostic.code()) && statementRoots.stream()
              .anyMatch(root -> diagnostic.origins().stream().anyMatch(origin -> projectedBy(root, Optional.of(origin)))))) {
        continue;
      }
      var position = bodyPosition(source, bodies, diagnostic);
      while (nextInsertion < insertions.size() && position.isPresent()
          && precedes(insertions.get(nextInsertion), position.orElseThrow(), diagnostic)) {
        result.add(insertions.get(nextInsertion).diagnostic());
        nextInsertion++;
      }
      result.add(DiagnosticSummary.from(diagnostic));
    }
    if (!catalog.isEmpty() && !catalogInserted) {
      result.addAll(catalog);
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

  private static List<SyntaxNode> statementRoots(AstResult source) {
    var nodes = source.program().methods().stream().flatMap(method -> source.body(method.methodKey()).stream())
        .flatMap(body -> body.root().descendantsAndSelf().stream())
        .filter(node -> (EXPANDED_STATEMENT_KINDS.contains(node.sourceKind().name()) || isExpandedCase(node) || isExpandedTry(node))
            && node.syntaxKind() != SyntaxKind.UNSUPPORTED && node.origin().isPresent())
        .toList();
    return nodes.stream().filter(node -> nodes.stream().noneMatch(parent -> !parent.equals(node) && projectedBy(parent, node.origin())))
        .toList();
  }

  private static boolean isExpandedCase(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.CASE
        && (node.value(SyntaxValue.SwitchCase.class).form() != SyntaxValue.SwitchCaseForm.RULE || !node.children(SyntaxRole.GUARD).isEmpty()
            || node.children(SyntaxRole.LABEL).stream().anyMatch(label -> label.syntaxKind() == SyntaxKind.LITERAL
                ? label.sourceKind() == JavaSourceKind.NULL_LITERAL
                : (label.syntaxKind() != SyntaxKind.FIELD_ACCESS && label.syntaxKind() != SyntaxKind.VARIABLE_ACCESS)
                    || label.semanticReference().filter(reference -> reference.referenceKind() == ReferenceKind.ENUM_CONSTANT).isEmpty()));
  }

  private static boolean isExpandedTry(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.TRY
        && (!node.children(SyntaxRole.RESOURCE).isEmpty() || !node.children(SyntaxRole.FINALLY_BLOCK).isEmpty());
  }

  private static boolean projectedBy(SyntaxNode root, Optional<SourceSpan> origin) {
    // Exception clauses replace their own refusal while preserving independently reported descendants.
    return within(root, origin) && (!isExpandedTry(root) || root.origin().equals(origin));
  }

  private static boolean within(SyntaxNode parent, Optional<SourceSpan> origin) {
    return origin.isPresent() && parent.origin()
        .filter(range -> range.sourceFileKey().equals(origin.orElseThrow().sourceFileKey())
            && range.startOffset() <= origin.orElseThrow().startOffset()
            && range.endOffsetExclusive() >= origin.orElseThrow().endOffsetExclusive())
        .isPresent();
  }

  private static List<DiagnosticSummary> catalogProjection(AstResult source) {
    var occurrences = new ArrayList<CatalogDiagnostic>();
    var projectedKind = false;
    source.diagnostics().stream().filter(diagnostic -> diagnostic.code().startsWith(TREE_DIAGNOSTIC_PREFIX))
        .forEach(diagnostic -> occurrences
            .add(new CatalogDiagnostic(diagnostic.origins().stream().findFirst(), DiagnosticSummary.from(diagnostic))));
    for (var occurrence : source.coverage().occurrences()) {
      var classification = occurrence.classification();
      var feature = classification.feature();
      if (feature.category() == LanguageFeatureCategory.COMPILER_TREE_KIND && classification.policy() == LanguageFeaturePolicy.REPRESENTED
          && (EXPANDED_STATEMENT_KINDS.contains(feature.identifier()) || "UNION_TYPE".equals(feature.identifier()))) {
        var kind = feature.identifier();
        projectedKind = true;
        occurrences.add(new CatalogDiagnostic(occurrence.origins().stream().findFirst(),
            new DiagnosticSummary(DiagnosticSeverity.ERROR, "FRONTEND",
                TREE_DIAGNOSTIC_PREFIX + kind.toLowerCase(Locale.ROOT).replace('_', '-'),
                "Java compiler tree kind " + kind + " is outside the supported semantic graph profile")));
      }
    }
    if (!projectedKind) {
      return List.of();
    }
    var files = source.snapshot().sourceFiles().stream().map(file -> file.sourceFileKey()).toList();
    occurrences.sort(Comparator
        .comparingInt((CatalogDiagnostic value) -> value.origin().map(origin -> files.indexOf(origin.sourceFileKey())).orElse(files.size()))
        .thenComparingLong(value -> value.origin().map(SourceSpan::startOffset).orElse(Long.MAX_VALUE)).thenComparing(Comparator
            .comparingLong((CatalogDiagnostic value) -> value.origin().map(SourceSpan::endOffsetExclusive).orElse(0L)).reversed()));
    return occurrences.stream().map(CatalogDiagnostic::diagnostic).toList();
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

  private record CatalogDiagnostic(Optional<SourceSpan> origin, DiagnosticSummary diagnostic) {
  }
}
