package org.aquilagraph.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

/** Drives the same staged launch used by the local coordinator at deliberate termination boundaries. */
public final class BuildLifecycleProbe {
  private BuildLifecycleProbe() {
  }

  public static void main(String[] arguments) throws Exception {
    var root = Path.of(arguments[0]);
    var request = BuildRequest.parse(new String[]{"--root", root.toString(), "--tool-root", arguments[1], "--maven", arguments[2], "compile"});
    request.validateEnvironment(System.getenv());
    try (var lease = BuildLease.acquire(request); var launch = CommandLaunch.maven(lease, Optional.empty())) {
      if (!arguments[3].equals("intent")) {
        launch.start();
      }
      if (arguments[3].equals("closure")) {
        launch.awaitAcknowledgement();
        launch.permit();
        if (launch.awaitExit() != 0) {
          throw new IllegalStateException("The bounded build failed before its closure boundary");
        }
      }
      Files.writeString(root.resolve("probe-stage.txt"), arguments[3]);
      while (!Files.exists(root.resolve("release-probe"))) {
        TimeUnit.MILLISECONDS.sleep(25);
      }
    }
  }
}
