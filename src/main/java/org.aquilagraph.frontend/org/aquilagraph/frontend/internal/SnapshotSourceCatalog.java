package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.aquilagraph.identity.api.CapturedLocation;
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
      var sourceLocation = snapshot.sourceRoot().resolve(sourceFile.relativePath());
      var input = SimpleJavaFileObject.forSource(sourceUri(sourceLocation), sourceFile.content());
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

  private static URI sourceUri(CapturedLocation location) {
    var scheme = switch (location.syntax()) {
      case POSIX -> "aquila-source-posix";
      case WINDOWS -> "aquila-source-windows";
    };
    var root = switch (location.rootKind()) {
      case POSIX_ABSOLUTE -> "";
      case WINDOWS_DRIVE -> location.rootComponents().getFirst() + ":/";
      case WINDOWS_UNC -> String.join("/", location.rootComponents()) + "/";
      case RELATIVE -> throw new IllegalArgumentException("Compiler source locations must have a captured root");
    };
    try {
      return new URI(scheme, null, '/' + root + String.join("/", location.components()), null, null);
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("Cannot construct the compiler URI for " + location.spelling(), exception);
    }
  }
}
