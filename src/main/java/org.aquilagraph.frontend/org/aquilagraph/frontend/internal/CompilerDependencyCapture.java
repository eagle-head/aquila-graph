package org.aquilagraph.frontend.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.aquilagraph.frontend.internal.AnalysisEnvironmentCapture.DependencyContentCapture;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.DependencyKind;

final class CompilerDependencyCapture implements DependencyContentCapture {
  private static final int BUFFER_SIZE = 8192;

  @Override
  public PhysicalDependency capture(Path location) throws IOException {
    Objects.requireNonNull(location, "location");
    if (Files.notExists(location)) {
      return new PhysicalDependency(DependencyKind.MISSING, 0, 0, emptyFingerprint("missing"));
    }
    if (Files.isRegularFile(location)) {
      return captureFile(location);
    }
    if (Files.isDirectory(location)) {
      return captureDirectory(location);
    }
    throw new IOException("Unsupported compiler dependency entry: " + location);
  }

  private static PhysicalDependency captureFile(Path location) throws IOException {
    var digest = sha256();
    updateString(digest, "regular-file");
    var expectedSize = Files.size(location);
    updateLong(digest, expectedSize);
    var capturedSize = updateContent(digest, location);
    if (capturedSize != expectedSize) {
      throw new IOException("Compiler dependency changed while being captured: " + location);
    }
    return new PhysicalDependency(DependencyKind.REGULAR_FILE, capturedSize, 1, formattedFingerprint(digest));
  }

  private static PhysicalDependency captureDirectory(Path location) throws IOException {
    List<Path> files;
    try (var paths = Files.walk(location, FileVisitOption.FOLLOW_LINKS)) {
      files = paths.filter(Files::isRegularFile).sorted(Comparator.comparing(path -> canonicalPath(location.relativize(path)))).toList();
    } catch (UncheckedIOException exception) {
      throw new IOException("Failed to enumerate compiler dependency directory: " + location, exception);
    }

    var digest = sha256();
    updateString(digest, "directory");
    updateLong(digest, files.size());
    long byteCount = 0;
    for (var file : files) {
      updateString(digest, canonicalPath(location.relativize(file)));
      var expectedSize = Files.size(file);
      updateLong(digest, expectedSize);
      var capturedSize = updateContent(digest, file);
      if (capturedSize != expectedSize) {
        throw new IOException("Compiler dependency changed while being captured: " + file);
      }
      byteCount = Math.addExact(byteCount, capturedSize);
    }
    return new PhysicalDependency(DependencyKind.DIRECTORY, byteCount, files.size(), formattedFingerprint(digest));
  }

  private static long updateContent(MessageDigest digest, Path path) throws IOException {
    long byteCount = 0;
    var buffer = new byte[BUFFER_SIZE];
    try (var input = Files.newInputStream(path)) {
      while (true) {
        var read = input.read(buffer);
        if (read < 0) {
          break;
        }
        if (read > 0) {
          digest.update(buffer, 0, read);
          byteCount += read;
        }
      }
    }
    return byteCount;
  }

  private static String emptyFingerprint(String kind) {
    var digest = sha256();
    updateString(digest, kind);
    return formattedFingerprint(digest);
  }

  private static String formattedFingerprint(MessageDigest digest) {
    return "sha256:" + HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance(AnalysisEnvironmentSnapshot.DIGEST_ALGORITHM);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
    }
  }

  private static void updateString(MessageDigest digest, String value) {
    var bytes = value.getBytes(StandardCharsets.UTF_8);
    updateLong(digest, bytes.length);
    digest.update(bytes);
  }

  private static void updateLong(MessageDigest digest, long value) {
    digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
  }

  private static String canonicalPath(Path path) {
    return NativeLocations.capture(path).canonicalRelativeName();
  }
}
