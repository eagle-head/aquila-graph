package org.aquilagraph.frontend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.StableId;

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
          .map(path -> path.toAbsolutePath().normalize()).sorted((left, right) -> canonicalRelativePath(normalizedRoot.relativize(left))
              .compareTo(canonicalRelativePath(normalizedRoot.relativize(right))))
          .toList();
      var sourceFiles = new ArrayList<SourceFile>(sourcePaths.size());
      for (var sourcePath : sourcePaths) {
        sourceFiles.add(readSource(normalizedRoot, sourcePath));
      }
      return new InputSnapshot(normalizedRoot, "SHA-256", snapshotDigest(sourceFiles), sourceFiles);
    }
  }

  private static SourceFile readSource(Path sourceRoot, Path sourcePath) throws IOException {
    try {
      var relativePath = sourceRoot.relativize(sourcePath).normalize();
      var content = Files.readString(sourcePath, StandardCharsets.UTF_8);
      var contentDigest = digest(content.getBytes(StandardCharsets.UTF_8));
      var sourceFileKey = StableId.sha256("source", canonicalRelativePath(relativePath), contentDigest);
      return new SourceFile(sourceFileKey, relativePath, contentDigest, content);
    } catch (IOException exception) {
      throw new IOException("Cannot read Java source: " + sourcePath, exception);
    }
  }

  private static String snapshotDigest(List<SourceFile> sourceFiles) {
    var components = sourceFiles.stream()
        .flatMap(sourceFile -> java.util.stream.Stream.of(canonicalRelativePath(sourceFile.relativePath()), sourceFile.contentDigest()))
        .toArray(String[]::new);
    return StableId.sha256("snapshot", components);
  }

  private static String digest(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
    }
  }

  private static String canonicalRelativePath(Path path) {
    return path.toString().replace('\\', '/');
  }

  private static boolean isJavaSource(Path path) {
    var fileName = path.getFileName();
    return fileName != null && fileName.toString().endsWith(".java");
  }
}
