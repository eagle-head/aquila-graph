package org.aquilagraph.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Objects;

/** A borrowed destination whose adapter owns its actual I/O observation contract. */
public abstract sealed class CommandDestination permits PrintStreamDestination, WriterDestination {
  CommandDestination() {
  }

  /**
   * Observes the supplied stream's error flag before and after report writing. The stream retains its encoding, automatic-flush
   * configuration and ownership.
   * 
   * @param stream
   *          borrowed stream
   * @return stream-error-aware destination
   */
  public static CommandDestination printStream(PrintStream stream) {
    return new PrintStreamDestination(Objects.requireNonNull(stream, "stream"));
  }

  /**
   * Borrows a writer whose complete delegate chain propagates checked I/O failures. Direct PrintWriter instances are rejected; hidden
   * suppressing delegates are outside this contract and are not inspected. The caller owns encoding and closure.
   * 
   * @param writer
   *          borrowed throwing writer
   * @return checked-I/O-aware destination
   * @throws IllegalArgumentException
   *           if the writer is a PrintWriter
   */
  public static CommandDestination throwingWriter(Writer writer) {
    Objects.requireNonNull(writer, "writer");
    if (writer instanceof PrintWriter) {
      throw new IllegalArgumentException("A throwing destination requires a writer that propagates checked I/O failures");
    }
    return new WriterDestination(writer);
  }

  abstract ChannelEmission emit(String text, EmissionAttempt attempt) throws IOException;
}
