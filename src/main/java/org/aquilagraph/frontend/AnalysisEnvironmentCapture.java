package org.aquilagraph.frontend;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import javax.tools.JavaCompiler;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.DependencyKind;
import org.aquilagraph.model.DependencySnapshot;

final class AnalysisEnvironmentCapture {
  AnalysisEnvironmentSnapshot capture(JavaCompiler compiler, CompilerConfiguration configuration, List<String> analysisOptions)
      throws IOException {
    return new AnalysisEnvironmentSnapshot(configuration, compiler.getClass().getName(), requiredSystemProperty("java.vendor"),
        requiredSystemProperty("java.runtime.version"), analysisOptions, captureDependencies(configuration.classPath()),
        captureDependencies(configuration.modulePath()));
  }

  private static List<DependencySnapshot> captureDependencies(List<Path> locations) throws IOException {
    var dependencies = new ArrayList<DependencySnapshot>(locations.size());
    for (var location : locations) {
      dependencies.add(captureDependency(location));
    }
    return List.copyOf(dependencies);
  }

  private static DependencySnapshot captureDependency(Path location) throws IOException {
    if (Files.notExists(location)) {
      return new DependencySnapshot(location, DependencyKind.MISSING, 0, 0, fingerprint("missing", List.of()));
    }
    if (Files.isRegularFile(location)) {
      var digest = sha256();
      updateString(digest, "regular-file");
      var byteCount = updateContent(digest, location);
      return new DependencySnapshot(location, DependencyKind.REGULAR_FILE, byteCount, 1, formattedFingerprint(digest));
    }
    if (Files.isDirectory(location)) {
      return captureDirectory(location);
    }
    throw new IOException("Unsupported compiler dependency entry: " + location);
  }

  private static DependencySnapshot captureDirectory(Path location) throws IOException {
    List<Path> files;
    try (var paths = Files.walk(location, FileVisitOption.FOLLOW_LINKS)) {
      files = paths.filter(Files::isRegularFile).sorted(Comparator.comparing(path -> canonicalRelativePath(location.relativize(path))))
          .toList();
    } catch (UncheckedIOException exception) {
      throw new IOException("Failed to enumerate compiler dependency directory: " + location, exception);
    }

    var digest = sha256();
    updateString(digest, "directory");
    updateLong(digest, files.size());
    long byteCount = 0;
    for (var file : files) {
      updateString(digest, canonicalRelativePath(location.relativize(file)));
      var expectedSize = Files.size(file);
      updateLong(digest, expectedSize);
      var capturedSize = updateContent(digest, file);
      if (capturedSize != expectedSize) {
        throw new IOException("Compiler dependency changed while being captured: " + file);
      }
      byteCount = Math.addExact(byteCount, capturedSize);
    }
    return new DependencySnapshot(location, DependencyKind.DIRECTORY, byteCount, files.size(), formattedFingerprint(digest));
  }

  private static long updateContent(MessageDigest digest, Path path) throws IOException {
    long byteCount = 0;
    var buffer = new byte[8192];
    try (var input = Files.newInputStream(path)) {
      while (true) {
        var read = input.read(buffer);
        if (read < 0) {
          break;
        }
        if (read == 0) {
          continue;
        }
        digest.update(buffer, 0, read);
        byteCount += read;
      }
    }
    return byteCount;
  }

  private static String fingerprint(String kind, List<String> components) {
    var digest = sha256();
    updateString(digest, kind);
    components.forEach(component -> updateString(digest, component));
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

  private static String canonicalRelativePath(Path path) {
    return path.toString().replace('\\', '/');
  }

  private static String requiredSystemProperty(String name) {
    var value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Required system property is unavailable: " + name);
    }
    return value;
  }
}
