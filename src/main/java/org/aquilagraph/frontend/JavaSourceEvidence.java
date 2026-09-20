package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import java.util.List;
import java.util.Objects;
import javax.tools.Diagnostic;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;

final class JavaSourceEvidence {
  private final SourcePositions positions;

  JavaSourceEvidence(SourcePositions positions) {
    this.positions = Objects.requireNonNull(positions, "positions");
  }

  List<SourceSpan> origins(CompilationUnitTree unit, SourceFile sourceFile, Tree tree) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(tree, "tree");
    var start = positions.getStartPosition(unit, tree);
    var end = positions.getEndPosition(unit, tree);
    if (start == Diagnostic.NOPOS || end == Diagnostic.NOPOS || end < start) {
      return List.of();
    }
    return List.of(new SourceSpan(sourceFile.sourceFileKey(), start, end));
  }
}
