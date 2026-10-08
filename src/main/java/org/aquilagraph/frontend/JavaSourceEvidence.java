package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.tools.Diagnostic;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.SyntaxStructuralPath;

final class JavaSourceEvidence {
  private final SourcePositions positions;

  JavaSourceEvidence(SourcePositions positions) {
    this.positions = Objects.requireNonNull(positions, "positions");
  }

  List<SourceSpan> origins(CompilationUnitTree unit, SourceFile sourceFile, Tree tree) {
    return origin(unit, sourceFile, tree).stream().toList();
  }

  Optional<SourceSpan> origin(CompilationUnitTree unit, SourceFile sourceFile, Tree tree) {
    return switch (position(unit, sourceFile, tree)) {
      case Position.Available available -> Optional.of(available.span());
      case Position.Missing _ -> Optional.empty();
      case Position.Invalid _ -> Optional.empty();
    };
  }

  Position position(CompilationUnitTree unit, SourceFile sourceFile, Tree tree) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(tree, "tree");
    var start = positions.getStartPosition(unit, tree);
    var end = positions.getEndPosition(unit, tree);
    if (tree instanceof VariableTree variable && start != Diagnostic.NOPOS) {
      for (var annotation : variable.getModifiers().getAnnotations()) {
        var annotationStart = positions.getStartPosition(unit, annotation);
        if (annotationStart != Diagnostic.NOPOS) {
          start = Math.min(start, annotationStart);
        }
      }
    }
    if (start == Diagnostic.NOPOS || end == Diagnostic.NOPOS) {
      return new Position.Missing();
    }
    if (start < 0 || end < start || end > sourceFile.content().length()) {
      return new Position.Invalid(start, end);
    }
    return new Position.Available(new SourceSpan(sourceFile.sourceFileKey(), start, end));
  }

  Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      List<AnalysisDiagnostic> diagnostics) {
    return switch (position(unit, sourceFile, tree)) {
      case Position.Available available -> Optional.of(available.span());
      case Position.Missing _ -> {
        TranslationDiagnosticEvidence.emit(diagnostics, "JavaSourceEvidence", FrontendDiagnosticFactory.sourcePositionError(sourceFile,
            path, "frontend.missing-source-position", "A required source occurrence has no compiler position"));
        yield Optional.empty();
      }
      case Position.Invalid _ -> {
        TranslationDiagnosticEvidence.emit(diagnostics, "JavaSourceEvidence", FrontendDiagnosticFactory.sourcePositionError(sourceFile,
            path, "frontend.invalid-source-range", "A required source occurrence has an invalid compiler range"));
        yield Optional.empty();
      }
    };
  }

  long rawStart(CompilationUnitTree unit, Tree tree) {
    return positions.getStartPosition(Objects.requireNonNull(unit, "unit"), Objects.requireNonNull(tree, "tree"));
  }

  sealed interface Position {
    record Available(SourceSpan span) implements Position {
    }

    record Missing() implements Position {
    }

    record Invalid(long start, long end) implements Position {
    }
  }
}
