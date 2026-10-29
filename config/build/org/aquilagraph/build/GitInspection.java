package org.aquilagraph.build;

import java.io.IOException;
import org.aquilagraph.build.NativeObservation.Creation;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs Git observations inside one native group, including any hook-created descendants. */
public final class GitInspection {
  private final InvocationContext context;
  private final BuildJournal container;
  private final Creation creation;

  private GitInspection(InvocationContext context, BuildJournal container) {
    this(context, container, ProcessBuilder::start);
  }

  GitInspection(InvocationContext context, BuildJournal container, Creation creation) {
    this.context = context;
    this.container = container;
    this.creation = creation;
  }

  /**
   * Inspects Git text or one explicit internal observation under the acknowledged lease.
   *
   * @param arguments
   *          canonical root, invocation nonce, command reservation and launcher reservation
   * @throws IOException
   *           if admission, Git inspection or closure fails
   * @throws InterruptedException
   *           if an owned Git wait is interrupted
   */
  public static void main(String[] arguments) throws IOException, InterruptedException {
    var context = InvocationContext.read(Path.of(arguments[0]), arguments[1]);
    LaunchAdmission.require(context, arguments[3]);
    var container = BuildJournal.open(context, arguments[2]);
    container.acknowledge();
    var inspection = new GitInspection(context, container);
    var intent = BuildFiles.read(container.directory().resolve("intent.properties"));
    switch (BuildFiles.required(intent, "inspection.mode")) {
      case "single" -> {
        var values = new ArrayList<String>();
        var count = Integer.parseInt(BuildFiles.required(intent, "inspection.argument.count"));
        for (var index = 0; index < count; index++) {
          values.add(BuildFiles.required(intent, "inspection.argument." + index));
        }
        var output = inspection.git(values, Integer.parseInt(BuildFiles.required(intent, "inspection.expected.exit")));
        Files.writeString(container.observationDirectory().resolve("git-result.txt"), output);
      }
      case "line-endings" -> inspection.checkLineEndings();
      default -> throw new IOException("Unsupported contained Git inspection mode");
    }
  }

  private void checkLineEndings() throws IOException, InterruptedException {
    var index = git(List.of("ls-files", "--eol"), 0);
    if (index.lines().anyMatch(line -> line.matches("^i/(crlf|mixed)\\s.*"))) {
      throw new IOException("Non-LF text is present in the Git index: " + index);
    }
    git(List.of("grep", "--cached", "-I", "-l", "\r"), 1);
    for (var commit : git(List.of("rev-list", "HEAD"), 0).lines().toList()) {
      git(List.of("grep", "-I", "-l", "\r", commit), 1);
    }
    System.getLogger(GitInspection.class.getName()).log(Level.INFO, "LF-only Git text verified in the index and reachable commits.");
  }

  String git(List<String> arguments, int expectedExit) throws IOException, InterruptedException {
    var journal = BuildJournal.reserve(context, "git-observation");
    journal.recordContainer(container.reservation());
    var command = new ArrayList<>(
        List.of("git", "-c", "aquila.build.invocation=" + context.nonce(), "-c", "aquila.build.reservation=" + journal.reservation()));
    command.addAll(arguments);
    var outputFile = Files.createTempFile(journal.observationDirectory(), "git-", ".txt");
    var builder = new ProcessBuilder(command).directory(context.root().toFile()).redirectErrorStream(true)
        .redirectOutput(outputFile.toFile());
    try (var observation = new NativeObservation(context, journal, creation, false)) {
      var process = observation.start(builder);
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        throw new IOException("Git observation exceeded 60 seconds");
      }
      var text = Files.readString(outputFile);
      if (process.exitValue() != expectedExit) {
        throw new IOException("Git observation exited " + process.exitValue() + ": " + text);
      }
      return text;
    }
  }
}
