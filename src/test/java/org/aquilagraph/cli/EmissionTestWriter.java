package org.aquilagraph.cli;

import java.io.IOException;
import java.io.Writer;

/** Test-owned throwing destination with observable prefix, operations and lifetime. */
final class EmissionTestWriter extends Writer {
  final StringBuilder text = new StringBuilder();
  Throwable writeFailure;
  Throwable flushFailure;
  int prefix;
  int writes;
  int flushes;
  int closes;

  @Override
  public void write(char[] value, int offset, int length) throws IOException {
    writes++;
    if (writeFailure != null) {
      text.append(value, offset, Math.min(prefix, length));
      fail(writeFailure);
    }
    text.append(value, offset, length);
  }

  @Override
  public void flush() throws IOException {
    flushes++;
    if (flushFailure != null) { fail(flushFailure); }
  }

  @Override
  public void close() { closes++; }

  private static void fail(Throwable failure) throws IOException {
    if (failure instanceof IOException checked) { throw checked; }
    if (failure instanceof RuntimeException runtime) { throw runtime; }
    throw (Error) failure;
  }
}
