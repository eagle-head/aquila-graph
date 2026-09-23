package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.tools.Diagnostic;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;

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

  sealed interface Position {
    record Available(SourceSpan span) implements Position {
    }

    record Missing() implements Position {
    }

    record Invalid(long start, long end) implements Position {
    }
  }
}
