package org.aquilagraph.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/** Reads native Linux launch tokens when Java cannot expose a protected executable. */
final class LinuxProcessInventory {
  private static final String SUPPORTED_PLATFORM = "Linux";
  private static final char ZOMBIE = 'Z';
  private static final char DEAD = 'X';
  private static final int STATE_OFFSET = ") ".length();
  private static final int MAX_COMMAND_BYTES = 16 * 1024 * 1024;

  private LinuxProcessInventory() {
  }

  static Optional<String> commandLine(ProcessHandle process) throws IOException {
    if (!SUPPORTED_PLATFORM.equals(System.getProperty("os.name"))) {
      throw new IOException("A required process argument observation is unavailable on this platform");
    }
    var observed = ProcessIdentity.observeAvailable(process);
    if (observed.isEmpty()) {
      return Optional.empty();
    }
    var identity = observed.orElseThrow();
    var directory = Path.of("/proc", Long.toString(identity.pid()));
    try {
      byte[] bytes;
      try (var stream = Files.newInputStream(directory.resolve("cmdline"))) {
        bytes = stream.readNBytes(MAX_COMMAND_BYTES + 1);
      }
      if (identity.liveProcess().isEmpty()) {
        return Optional.empty();
      }
      if (bytes.length > MAX_COMMAND_BYTES) {
        throw new IOException("Native process arguments exceeded the bounded observation size: " + identity.pid());
      }
      if (bytes.length == 0) {
        return emptyCommand(identity, directory);
      }
      return Optional.of(new String(bytes, StandardCharsets.ISO_8859_1).replace('\0', ' '));
    } catch (NoSuchFileException exception) {
      if (identity.liveProcess().isEmpty()) {
        return Optional.empty();
      }
      throw new IOException("Native process observation disappeared for a still-live identity: " + identity.pid(), exception);
    }
  }

  private static Optional<String> emptyCommand(ProcessIdentity identity, Path directory) throws IOException {
    var status = Files.readString(directory.resolve("stat"), StandardCharsets.ISO_8859_1);
    var separator = status.lastIndexOf(") ");
    if (identity.liveProcess().isEmpty()) {
      return Optional.empty();
    }
    if (separator < 0 || separator + STATE_OFFSET >= status.length()) {
      throw new IOException("Native process state could not be interpreted: " + identity.pid());
    }
    var state = status.charAt(separator + STATE_OFFSET);
    if (state == ZOMBIE || state == DEAD) {
      return Optional.empty();
    }
    throw new IOException("Process arguments are unavailable for a nonterminal launch-account identity: " + identity.pid());
  }
}
