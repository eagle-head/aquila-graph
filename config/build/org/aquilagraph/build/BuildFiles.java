package org.aquilagraph.build;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/** Forced file contents and atomic replacement keep interrupted state distinguishable. */
final class BuildFiles {
  private BuildFiles() {
  }

  static Properties read(Path file) throws IOException {
    var result = new Properties();
    result.load(new StringReader(Files.readString(file, StandardCharsets.UTF_8)));
    return result;
  }

  static String required(Properties properties, String key) throws IOException {
    var result = properties.getProperty(key);
    if (result == null || result.isEmpty()) {
      throw new IOException("Incomplete build record: missing " + key);
    }
    return result;
  }

  static void write(Path file, Properties properties) throws IOException {
    var parent = file.toAbsolutePath().getParent();
    if (parent == null) {
      throw new IOException("A build record requires a containing directory");
    }
    Files.createDirectories(parent);
    var writer = new StringWriter();
    properties.store(writer, "Local build ownership");
    var temporary = Files.createTempFile(parent, "record-", ".tmp");
    try {
      try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        var bytes = ByteBuffer.wrap(writer.toString().getBytes(StandardCharsets.UTF_8));
        while (bytes.hasRemaining()) {
          channel.write(bytes);
        }
        channel.force(true);
      }
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
