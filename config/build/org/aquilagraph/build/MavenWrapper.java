package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;

/** Runs the native Maven wrapper only after its source bridge is contained and acknowledged. */
public final class MavenWrapper {
  private MavenWrapper() {
  }

  /**
   * Starts the exact recorded wrapper from inside the native descendant group.
   *
   * @param arguments
   *          canonical root, invocation nonce, wrapper reservation and launcher reservation
   * @throws IOException
   *           if admission, launch or stream closure fails
   * @throws InterruptedException
   *           if the contained wrapper wait is interrupted
   */
  public static void main(String[] arguments) throws IOException, InterruptedException {
    var context = InvocationContext.read(Path.of(arguments[0]), arguments[1]);
    var wrapper = BuildJournal.open(context, arguments[2]);
    var launcher = LaunchAdmission.require(context, arguments[3]);
    wrapper.acknowledge();
    var request = BuildFiles.read(context.directory().resolve("invocation.properties"));
    var mavenArguments = new ArrayList<String>();
    var count = Integer.parseInt(BuildFiles.required(request, "argument.count"));
    for (var index = 0; index < count; index++) {
      mavenArguments.add(BuildFiles.required(request, "argument." + index));
    }
    var builder = new ProcessBuilder().directory(context.root().toFile()).inheritIO();
    builder.environment().put("MAVEN_SKIP_RC", "true");
    builder.environment().remove("MAVEN_ARGS");
    builder.environment().put("MAVEN_BASEDIR", context.root().toString());
    builder.environment().put("AQUILA_BUILD_INVOCATION", context.nonce());
    builder.environment().put("AQUILA_BUILD_RESERVATION", launcher.reservation());
    builder.environment().put("AQUILA_BUILD_WRAPPER", wrapper.reservation());
    mavenArguments.add("-Daquila.build.invocation=" + context.nonce());
    mavenArguments.add("-Daquila.build.reservation=" + wrapper.reservation());
    var complete = "verify-all".equals(BuildFiles.required(request, "operation"));
    if (complete) {
      mavenArguments.add("-Dbuild.unit.directory=" + SuiteCatalog.directory(context.root(), context.nonce(), "unit"));
      builder.redirectErrorStream(true).redirectOutput(Redirect.PIPE);
    }
    WrapperCommand.configure(builder, Path.of(BuildFiles.required(request, "maven")), mavenArguments);
    context.requireLiveOwner();
    var process = builder.start();
    int exit;
    try {
      wrapper.observe(process.toHandle());
      if (complete) {
        copyOutput(process, CompleteValidation.directory(context).resolve("maven.log"));
      }
      exit = process.waitFor();
    } finally {
      ProcessStreams.close(process);
    }
    System.exit(exit);
  }

  private static void copyOutput(Process process, Path log) throws IOException {
    var parent = log.getParent();
    if (parent == null) {
      throw new IOException("Managed build output requires a containing directory");
    }
    Files.createDirectories(parent);
    try (var file = Files.newOutputStream(log)) {
      var buffer = new byte[8192];
      var count = process.getInputStream().read(buffer);
      while (count != -1) {
        file.write(buffer, 0, count);
        System.out.write(buffer, 0, count);
        System.out.flush();
        count = process.getInputStream().read(buffer);
      }
    }
  }
}
