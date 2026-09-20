package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.SourceFile;

final class SnapshotSourceCatalog {
  private final List<JavaFileObject> compilationUnits;
  private final Map<Path, SourceFile> sourceFilesByPath;

  SnapshotSourceCatalog(InputSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    var units = new ArrayList<JavaFileObject>(snapshot.sourceFiles().size());
    var sources = new LinkedHashMap<Path, SourceFile>();
    for (var sourceFile : snapshot.sourceFiles()) {
      var sourcePath = absolutePath(snapshot, sourceFile);
      sources.put(sourcePath, sourceFile);
      units.add(SimpleJavaFileObject.forSource(sourcePath.toUri(), sourceFile.content()));
    }
    compilationUnits = List.copyOf(units);
    sourceFilesByPath = Map.copyOf(sources);
  }

  List<JavaFileObject> compilationUnits() {
    return compilationUnits;
  }

  Optional<SourceFile> find(CompilationUnitTree unit) {
    Objects.requireNonNull(unit, "unit");
    return find(unit.getSourceFile());
  }

  Optional<SourceFile> find(JavaFileObject source) {
    if (source == null) {
      return Optional.empty();
    }
    var path = Path.of(source.toUri()).toAbsolutePath().normalize();
    return Optional.ofNullable(sourceFilesByPath.get(path));
  }

  private static Path absolutePath(InputSnapshot snapshot, SourceFile sourceFile) {
    return snapshot
        .sourceRoot()
        .resolve(sourceFile.relativePath())
        .toAbsolutePath()
        .normalize();
  }
}
