package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.jspecify.annotations.Nullable;

final class SnapshotSourceCatalog {
  private final List<JavaFileObject> compilerInputs;
  private final Map<URI, SourceFile> sourceFilesByUri;

  SnapshotSourceCatalog(InputSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    var units = new ArrayList<JavaFileObject>(snapshot.sourceFiles().size());
    var sources = new LinkedHashMap<URI, SourceFile>();
    for (var sourceFile : snapshot.sourceFiles()) {
      var sourcePath = absolutePath(snapshot, sourceFile);
      var input = SimpleJavaFileObject.forSource(sourcePath.toUri(), sourceFile.content());
      sources.put(input.toUri().normalize(), sourceFile);
      units.add(input);
    }
    compilerInputs = List.copyOf(units);
    sourceFilesByUri = Map.copyOf(sources);
  }

  List<JavaFileObject> compilationUnits() {
    return compilerInputs;
  }

  Optional<SourceFile> find(CompilationUnitTree unit) {
    Objects.requireNonNull(unit, "unit");
    return find(unit.getSourceFile());
  }

  Optional<SourceFile> find(@Nullable JavaFileObject source) {
    if (source == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(sourceFilesByUri.get(source.toUri().normalize()));
  }

  private static Path absolutePath(InputSnapshot snapshot, SourceFile sourceFile) {
    return snapshot.sourceRoot().resolve(sourceFile.relativePath()).toAbsolutePath().normalize();
  }
}
