package org.aquilagraph.build;

import java.nio.file.Path;
import java.util.List;

/** Drives the production publication boundary in an owned interruption-control process. */
public final class CompilationPublicationProbe {
  private CompilationPublicationProbe() {
  }

  public static void main(String[] arguments) throws Exception {
    var root = Path.of(arguments[0]).toRealPath();
    var request = new CompilationRequest(root, "production", root.resolve("src/main/java"), root.resolve("target/resource-staging/production"),
        root.resolve("target/classes"), false, List.of(), System.getenv());
    new CompilationPublication(request).publish("controlled-publication");
  }
}
