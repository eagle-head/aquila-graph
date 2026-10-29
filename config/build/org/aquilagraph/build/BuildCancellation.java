package org.aquilagraph.build;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Requests cancellation from the existing owner and waits for its actual resource closure. */
record BuildCancellation(InvocationContext context) {
  private static final String REQUEST = "cancellation-request.properties";
  private static final String OBSERVED = "cancellation-observed.properties";

  static BuildCancellation request(Path root) throws IOException {
    var canonical = root.toRealPath();
    Properties active;
    try {
      active = BuildFiles.read(canonical.resolve(".build-state/active-owner.properties"));
    } catch (NoSuchFileException exception) {
      throw new IOException("There is no active local build to cancel", exception);
    }
    var context = InvocationContext.read(canonical, BuildFiles.required(active, "nonce"));
    ActiveLease.require(context);
    context.requireLiveOwner();
    BuildFiles.write(context.directory().resolve(REQUEST), context.properties());
    return new BuildCancellation(context);
  }

  static void check(InvocationContext context) throws IOException {
    Properties request;
    try {
      request = BuildFiles.read(context.directory().resolve(REQUEST));
    } catch (NoSuchFileException exception) {
      return;
    }
    context.requireIdentity(request);
    BuildFiles.write(context.directory().resolve(OBSERVED), context.properties());
    throw new Requested();
  }

  boolean awaitClosure(Duration timeout) throws IOException, InterruptedException {
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("Cancellation wait must not be negative");
    }
    var deadline = System.nanoTime() + timeout.toNanos();
    while (true) {
      var closed = context.directory().resolve("owner-closed.properties");
      if (Files.isRegularFile(closed)) {
        context.requireIdentity(BuildFiles.read(closed));
        BuildJournal.requireClosed(context);
        if (leaseReleased()) {
          var observed = context.directory().resolve(OBSERVED);
          Properties acknowledgement;
          try {
            acknowledgement = BuildFiles.read(observed);
          } catch (NoSuchFileException exception) {
            return false;
          }
          context.requireIdentity(acknowledgement);
          return true;
        }
      }
      if (context.owner().liveProcess().isEmpty()) {
        throw new IOException("The build owner exited before verified cancellation closure: " + context.directory());
      }
      if (System.nanoTime() >= deadline) {
        throw new IOException("Cancellation remains pending; the owner and its resource records are preserved: " + context.directory());
      }
      TimeUnit.MILLISECONDS.sleep(25);
    }
  }

  private boolean leaseReleased() throws IOException {
    try {
      var active = BuildFiles.read(context.root().resolve(".build-state/active-owner.properties"));
      return !context.nonce().equals(BuildFiles.required(active, "nonce"));
    } catch (NoSuchFileException exception) {
      try (var channel = FileChannel.open(context.root().resolve(".build-state/build.lock"), StandardOpenOption.WRITE);
          var lock = channel.tryLock()) {
        return lock != null;
      } catch (OverlappingFileLockException held) {
        return false;
      }
    }
  }

  /** Cancellation is a non-success outcome even when all cleanup succeeds. */
  static final class Requested extends IOException {
    private static final long serialVersionUID = 1L;

    Requested() {
      super("Local build cancellation was requested");
    }
  }
}
