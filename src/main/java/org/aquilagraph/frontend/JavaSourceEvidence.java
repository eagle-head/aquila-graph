package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.AnyPatternTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.tools.Diagnostic;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
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
    // A match-all compiler leaf can be anchored immediately after its written underscore token.
    if (tree instanceof AnyPatternTree && start == end) {
      var tokenStart = unnamedPatternStart(sourceFile.content(), Math.toIntExact(end));
      if (tokenStart < 0) {
        return new Position.Invalid(start, end);
      }
      start = tokenStart;
    }
    return new Position.Available(new SourceSpan(sourceFile.sourceFileKey(), start, end));
  }

  private static int unnamedPatternStart(String source, int end) {
    if (end > 0 && source.charAt(end - 1) == '_') {
      return end - 1;
    }
    if (end < 6 || !source.regionMatches(true, end - 4, "005f", 0, 4)) {
      return -1;
    }
    var cursor = end - 4;
    while (cursor > 0 && source.charAt(cursor - 1) == 'u') {
      cursor--;
    }
    return cursor < end - 4 && cursor > 0 && source.charAt(cursor - 1) == '\\' ? cursor - 1 : -1;
  }

  Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      DiagnosticEmissionCollector diagnostics) {
    return switch (position(unit, sourceFile, tree)) {
      case Position.Available available -> Optional.of(available.span());
      case Position.Missing _ -> {
        diagnostics.emit("JavaSourceEvidence", FrontendDiagnosticFactory.sourcePositionError(sourceFile, path,
            "frontend.missing-source-position", "A required source occurrence has no compiler position"));
        yield Optional.empty();
      }
      case Position.Invalid _ -> {
        diagnostics.emit("JavaSourceEvidence", FrontendDiagnosticFactory.sourcePositionError(sourceFile, path,
            "frontend.invalid-source-range", "A required source occurrence has an invalid compiler range"));
        yield Optional.empty();
      }
    };
  }

  long rawStart(CompilationUnitTree unit, Tree tree) {
    return positions.getStartPosition(Objects.requireNonNull(unit, "unit"), Objects.requireNonNull(tree, "tree"));
  }

  SourceSpan capturedUnitRange(SourceFile source) {
    return new SourceSpan(source.sourceFileKey(), 0, source.content().length());
  }

  long rawEnd(CompilationUnitTree unit, Tree tree) {
    return positions.getEndPosition(Objects.requireNonNull(unit, "unit"), Objects.requireNonNull(tree, "tree"));
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
