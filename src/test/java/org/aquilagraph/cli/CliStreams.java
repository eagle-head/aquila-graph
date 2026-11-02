package org.aquilagraph.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class CliStreams {
  private final Charset charset;
  private final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
  private final PrintStream output;
  private final PrintStream error;

  CliStreams() {
    this(StandardCharsets.UTF_8);
  }

  CliStreams(Charset charset) {
    this.charset = charset;
    output = new PrintStream(outputBytes, true, charset);
    error = new PrintStream(errorBytes, true, charset);
  }

  PrintStream output() {
    return output;
  }

  PrintStream error() {
    return error;
  }

  String outputText() {
    return outputBytes.toString(charset);
  }

  String errorText() {
    return errorBytes.toString(charset);
  }

  List<String> outputLines() {
    return outputText().lines().toList();
  }

  List<String> errorLines() {
    return errorText().lines().toList();
  }
}
