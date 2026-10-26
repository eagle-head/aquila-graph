package org.aquilagraph.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** A real bounded child whose literal command-line tokens can be observed independently. */
public final class ProcessObservationClient {
  private ProcessObservationClient() {
  }

  public static void main(String[] arguments) throws Exception {
    Files.writeString(Path.of(arguments[0]), "ready");
    while (!Files.exists(Path.of(arguments[1]))) {
      TimeUnit.MILLISECONDS.sleep(25);
    }
  }
}
