package org.aquilagraph.identity.api;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, content-addressed set of source files discovered below a root.
 *
 * @param sourceRoot
 *          normalized absolute source root
 * @param digestAlgorithm
 *          source content digest algorithm
 * @param snapshotDigest
 *          stable snapshot digest
 * @param sourceFiles
 *          files in case-sensitive lexicographic canonical relative-path order
 */
public record InputSnapshot(CapturedLocation sourceRoot, String digestAlgorithm, String snapshotDigest, List<SourceFile> sourceFiles) {
  private static final Comparator<SourceFile> SOURCE_ORDER = Comparator.comparing(SourceFile::canonicalRelativePath);

  /** Validates snapshot identity, uniqueness, and deterministic ordering. */
  public InputSnapshot {
    Objects.requireNonNull(sourceRoot, "sourceRoot");
    if (!sourceRoot.isAbsolute()) {
      throw new IllegalArgumentException("A captured source root must be absolute");
    }
    Objects.requireNonNull(digestAlgorithm, "digestAlgorithm");
    Objects.requireNonNull(snapshotDigest, "snapshotDigest");
    sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
    var keys = new HashSet<String>();
    @Nullable
    SourceFile previous = null;
    for (var sourceFile : sourceFiles) {
      Objects.requireNonNull(sourceFile, "sourceFile");
      if (sourceFile.relativePath().syntax() != sourceRoot.syntax()) {
        throw new IllegalArgumentException("A source file must use the syntax of its captured root");
      }
      if (!keys.add(sourceFile.sourceFileKey())) {
        throw new IllegalArgumentException("Source-file keys must be unique");
      }
      if (previous != null && SOURCE_ORDER.compare(previous, sourceFile) >= 0) {
        throw new IllegalArgumentException("Source files must have deterministic relative-path order");
      }
      previous = sourceFile;
    }
  }

  /**
   * Constructs a content-addressed snapshot in canonical source-path order without modifying the input list.
   *
   * @param sourceRoot
   *          source directory
   * @param sourceFiles
   *          captured source files in any encounter order
   * @return immutable snapshot with a digest derived from canonical paths and content digests
   * @throws IllegalArgumentException
   *           if the root is relative, source syntax differs, or source keys or canonical paths are duplicated
   */
  public static InputSnapshot fromSources(CapturedLocation sourceRoot, List<SourceFile> sourceFiles) {
    var ordered = Objects.requireNonNull(sourceFiles, "sourceFiles").stream().sorted(SOURCE_ORDER).toList();
    var components = ordered.stream().flatMap(source -> Stream.of(source.canonicalRelativePath(), source.contentDigest()))
        .toArray(String[]::new);
    return new InputSnapshot(sourceRoot, "SHA-256", StableId.sha256("snapshot", components), ordered);
  }

  /**
   * Returns the number of captured source files.
   *
   * @return source-file count
   */
  public int sourceFileCount() {
    return sourceFiles.size();
  }
}
