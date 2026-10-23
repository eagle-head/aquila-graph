package org.aquilagraph.frontend.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;

/** Discovers Java sources and captures their normalized immutable snapshot. */
public final class JavaSourceLoader {
  /** Creates a stateless source loader. */
  public JavaSourceLoader() {
  }

  /**
   * Discovers all Java source files below a directory in deterministic order.
   *
   * @param sourceRoot
   *          source directory
   * @return content-addressed input snapshot
   * @throws IOException
   *           if a discovered source cannot be read
   */
  public InputSnapshot discover(Path sourceRoot) throws IOException {
    var normalizedRoot = Objects.requireNonNull(sourceRoot, "sourceRoot").toAbsolutePath().normalize();

    if (!Files.isDirectory(normalizedRoot)) {
      throw new IllegalArgumentException("Source root is not a directory: " + normalizedRoot);
    }

    try (var paths = Files.walk(normalizedRoot)) {
      var sourcePaths = paths.filter(Files::isRegularFile).filter(JavaSourceLoader::isJavaSource)
          .map(path -> path.toAbsolutePath().normalize()).toList();
      var sourceFiles = new ArrayList<SourceFile>(sourcePaths.size());
      for (var sourcePath : sourcePaths) {
        sourceFiles.add(readSource(normalizedRoot, sourcePath));
      }
      return InputSnapshot.fromSources(normalizedRoot, sourceFiles);
    }
  }

  private static SourceFile readSource(Path sourceRoot, Path sourcePath) throws IOException {
    try {
      var relativePath = sourceRoot.relativize(sourcePath).normalize();
      var content = Files.readString(sourcePath, StandardCharsets.UTF_8);
      return SourceFile.fromContent(relativePath, content);
    } catch (IOException exception) {
      throw new IOException("Cannot read Java source: " + sourcePath, exception);
    }
  }

  private static boolean isJavaSource(Path path) {
    var fileName = path.getFileName();
    return fileName != null && fileName.toString().endsWith(".java");
  }
}
