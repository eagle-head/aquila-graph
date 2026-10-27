package org.aquilagraph.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Objects;

/** Content inventories and bounded tree operations for owned preparation paths. */
final class PreparationFiles {
  private PreparationFiles() {
  }

  static String digest(Path path) throws IOException {
    var digest = sha256();
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      return "absent";
    }
    requirePlain(path);
    try (var files = Files.walk(path)) {
      for (var file : files.sorted().toList()) {
        requirePlain(file);
        var relative = path.relativize(file).toString().replace('\\', '/');
        add(digest, relative);
        add(digest, Files.isDirectory(file) ? "directory" : "file");
        if (Files.isRegularFile(file)) {
          add(digest, Long.toString(Files.size(file)));
          update(digest, file);
        }
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  static String digest(Properties properties) {
    var digest = sha256();
    for (var key : properties.stringPropertyNames().stream().sorted().toList()) {
      add(digest, key);
      add(digest, Objects.requireNonNull(properties.getProperty(key)));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  static String digest(String value) {
    return HexFormat.of().formatHex(sha256().digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static void add(MessageDigest digest, String value) {
    var bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
    digest.update((byte) ':');
    digest.update(bytes);
  }

  private static void update(MessageDigest digest, Path file) throws IOException {
    try (InputStream input = Files.newInputStream(file)) {
      var buffer = new byte[8192];
      var count = input.read(buffer);
      while (count != -1) {
        digest.update(buffer, 0, count);
        count = input.read(buffer);
      }
    }
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Preparation requires SHA-256", exception);
    }
  }

  static void copy(Path source, Path destination) throws IOException {
    requirePlain(source);
    if (Files.isDirectory(source)) {
      try (var entries = Files.walk(source)) {
        for (var entry : entries.sorted().toList()) {
          requirePlain(entry);
          var target = destination.resolve(source.relativize(entry));
          if (Files.isDirectory(entry)) {
            Files.createDirectories(target);
          } else {
            Files.copy(entry, target, StandardCopyOption.REPLACE_EXISTING);
            writable(target);
          }
        }
      }
    } else {
      var parent = destination.getParent();
      if (parent == null) {
        throw new IOException("Captured files require a containing directory: " + destination);
      }
      Files.createDirectories(parent);
      Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
      writable(destination);
    }
  }

  static Path owned(Path root, Path path) throws IOException {
    var target = root.resolve("target");
    var absolute = path.toAbsolutePath().normalize();
    if (!absolute.startsWith(target) || absolute.equals(target)) {
      throw new IOException("Preparation mutation must remain below the owned target directory: " + path);
    }
    for (var ancestor = absolute; ancestor != null && ancestor.startsWith(root); ancestor = ancestor.getParent()) {
      if (Files.isSymbolicLink(ancestor)) {
        throw new IOException("Preparation does not follow symbolic links: " + ancestor);
      }
      if (Files.exists(ancestor) && !ancestor.toRealPath().equals(ancestor)) {
        throw new IOException("Preparation does not mutate aliased paths: " + ancestor);
      }
    }
    return absolute;
  }

  static void clear(Path root, Path path) throws IOException {
    var owned = owned(root, path);
    if (Files.exists(owned, LinkOption.NOFOLLOW_LINKS)) {
      try (var entries = Files.walk(owned)) {
        var paths = entries.sorted(Comparator.reverseOrder()).toList();
        for (var entry : paths) {
          requirePlain(entry);
        }
        for (var entry : paths) {
          if (BuildRequest.windows() && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
            WindowsKernel.instance().deleteFile(entry);
          } else {
            Files.delete(entry);
          }
        }
      }
    }
    Files.createDirectories(owned);
  }

  static Path freshFile(Path root, Path path) throws IOException {
    var owned = owned(root, path);
    if (Files.exists(owned, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(owned, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Preparation requires a regular owned artifact file: " + owned);
    }
    Files.deleteIfExists(owned);
    return Files.createFile(owned);
  }

  static void freeze(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
      for (var file : files.filter(Files::isRegularFile).toList()) {
        if (!file.toFile().setReadOnly()) {
          throw new IOException("Captured input could not be made read-only: " + file);
        }
      }
    }
  }

  private static void writable(Path file) throws IOException {
    if (!file.toFile().setWritable(true)) {
      throw new IOException("Owned output could not be made writable: " + file);
    }
  }

  private static void requirePlain(Path path) throws IOException {
    var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (attributes.isSymbolicLink() || attributes.isOther() || !(attributes.isDirectory() || attributes.isRegularFile())) {
      throw new IOException("Preparation requires regular files and directories: " + path);
    }
  }
}
