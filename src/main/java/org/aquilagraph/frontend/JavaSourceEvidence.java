package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.tools.Diagnostic;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.SyntaxStructuralPath;

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
      case Position.Missing ignored -> Optional.empty();
      case Position.Invalid ignored -> Optional.empty();
    };
  }

  Position position(CompilationUnitTree unit, SourceFile sourceFile, Tree tree) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(tree, "tree");
    var start = positions.getStartPosition(unit, tree);
    var end = positions.getEndPosition(unit, tree);
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
      case Position.Missing ignored -> {
        TranslationDiagnosticEvidence.emit(diagnostics, "JavaSourceEvidence", FrontendDiagnosticFactory.sourcePositionError(sourceFile,
            path, "frontend.missing-source-position", "A required source occurrence has no compiler position"));
        yield Optional.empty();
      }
      case Position.Invalid ignored -> {
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
