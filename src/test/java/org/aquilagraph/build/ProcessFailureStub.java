package org.aquilagraph.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

final class ProcessFailureStub extends Process {
  final UnsupportedOperationException stopFailure = new UnsupportedOperationException("process handle unavailable");
  final IOException outputFailure = new IOException("output endpoint failed");
  final IOException errorFailure = new IOException("error endpoint failed");
  final IOException inputFailure = new IOException("input endpoint failed");
  boolean failEndpoints;
  boolean inputClosed;
  boolean errorClosed;
  boolean outputClosed;
  boolean alive;
  int destructions;
  boolean refuseStop;

  ProcessFailureStub(boolean failEndpoints) {
    this.failEndpoints = failEndpoints;
  }

  @Override
  public OutputStream getOutputStream() {
    return new OutputStream() {
      @Override
      public void write(int value) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close() throws IOException {
        outputClosed = true;
        if (failEndpoints) {
          throw outputFailure;
        }
      }
    };
  }

  @Override
  public InputStream getInputStream() {
    return new InputStream() {
      @Override
      public int read() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close() throws IOException {
        inputClosed = true;
        if (failEndpoints) {
          throw inputFailure;
        }
      }
    };
  }

  @Override
  public InputStream getErrorStream() {
    return new InputStream() {
      @Override
      public int read() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close() throws IOException {
        errorClosed = true;
        if (failEndpoints) {
          throw errorFailure;
        }
      }
    };
  }

  @Override
  public int waitFor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int exitValue() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void destroy() {
    destructions++;
    alive = refuseStop && alive;
  }

  @Override
  public boolean isAlive() {
    return alive;
  }

  @Override
  public boolean waitFor(long timeout, TimeUnit unit) {
    return !alive;
  }

  @Override
  public Process destroyForcibly() {
    destroy();
    return this;
  }

  @Override
  public ProcessHandle toHandle() {
    throw stopFailure;
  }
}
