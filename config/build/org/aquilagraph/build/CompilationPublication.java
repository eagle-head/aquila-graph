package org.aquilagraph.build;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.MalformedInputException;
import java.util.Properties;

/** A success marker is valid only for the entire published output inventory. */
final class CompilationPublication {
  private final CompilationRequest request;
  private final Path marker;

  CompilationPublication(CompilationRequest request) throws IOException {
    this.request = request;
    marker = request.state().resolve("success.properties");
  }

  boolean reusable(String input) throws IOException {
    if (request.force() || !Files.isRegularFile(marker)) {
      explain(request.force() ? "fresh compilation requested" : "no successful publication marker");
      return false;
    }
    var record = readMarker(marker);
    if (!"compilation-output/1".equals(record.getProperty("schema"))
        || !request.publishedOutput(input).toString().equals(record.getProperty("output.path"))) {
      explain("invalid publication record");
      return false;
    }
    if (!input.equals(record.getProperty("input"))) {
      explain("effective inputs changed");
      return false;
    }
    if (!Files.isDirectory(request.publishedOutput(input), LinkOption.NOFOLLOW_LINKS)
        || !PreparationFiles.digest(request.publishedOutput(input)).equals(record.getProperty("output.digest"))) {
      explain("published output inventory changed");
      return false;
    }
    return true;
  }

  private void explain(String reason) {
    System.getLogger(CompilationPublication.class.getName()).log(Level.INFO,
        "Compilation " + request.batch() + ": rebuild because " + reason);
  }

  void invalidate() throws IOException {
    Files.deleteIfExists(marker);
  }

  void publish(String input) throws IOException {
    invalidate();
    var staged = request.state().resolve("output");
    var expected = PreparationFiles.digest(staged);
    var output = request.publishedOutput(input);
    PreparationFiles.clear(request.root(), output);
    PreparationFiles.copy(staged, output);
    if (!expected.equals(PreparationFiles.digest(output))) {
      throw new IOException("Published compilation output differs from the complete staged output");
    }
    var record = new Properties();
    record.setProperty("schema", "compilation-output/1");
    record.setProperty("input", input);
    record.setProperty("output.path", output.toString());
    record.setProperty("output.digest", expected);
    BuildFiles.write(marker, record);
  }

  static void requireProduction(Path root) throws IOException {
    var marker = root.resolve("target/compilation/production/success.properties");
    var record = readMarker(marker);
    var output = root.resolve("target/classes");
    if (!Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS) || !"compilation-output/1".equals(record.getProperty("schema"))
        || !output.toString().equals(record.getProperty("output.path"))
        || !PreparationFiles.digest(output).equals(record.getProperty("output.digest"))) {
      throw new IOException("Test compilation requires the exact successfully published production output");
    }
  }

  private static Properties readMarker(Path marker) throws IOException {
    if (!Files.isRegularFile(marker)) {
      return new Properties();
    }
    try {
      return BuildFiles.read(marker);
    } catch (IllegalArgumentException | MalformedInputException exception) {
      System.getLogger(CompilationPublication.class.getName()).log(Level.INFO,
          "Unreadable compilation publication record; rebuilding: " + marker, exception);
      return new Properties();
    }
  }
}
