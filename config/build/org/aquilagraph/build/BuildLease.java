package org.aquilagraph.build;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Whole-invocation exclusive ownership; the stable lock file is never unlinked. */
final class BuildLease implements AutoCloseable {
  private final FileChannel channel;
  private final FileLock lock;
  private final InvocationContext invocation;
  private boolean closed;

  private BuildLease(FileChannel channel, FileLock lock, InvocationContext context) {
    this.channel = channel;
    this.lock = lock;
    invocation = context;
  }

  static BuildLease acquire(BuildRequest request) throws IOException {
    var state = Files.createDirectories(request.root().resolve(".build-state"));
    try (var acquisition = new NativeAcquisition(
        FileChannel.open(state.resolve("build.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
      acquisition.acquire(request);
      var nonce = ActiveLease.acquire(request);
      return acquisition.transfer(InvocationContext.create(request, nonce));
    }
  }

  static void requireHeld(InvocationContext context) throws IOException {
    context.requireLiveOwner();
    ActiveLease.require(context);
    try (var channel = FileChannel.open(context.root().resolve(".build-state/build.lock"), StandardOpenOption.WRITE)) {
      try (var unexpected = channel.tryLock()) {
        if (unexpected != null) {
          throw new IOException("The local build owner does not hold the required output lease");
        }
      } catch (OverlappingFileLockException exception) {
        throw new IOException("Admission must execute in the owned Maven child, not the owner JVM", exception);
      }
    }
  }

  InvocationContext context() {
    return invocation;
  }

  void requireValid() throws IOException {
    if (!lock.isValid() || !invocation.owner().equals(ProcessIdentity.observe(ProcessHandle.current()))) {
      throw new IOException("The calling process no longer owns this build lease");
    }
    ActiveLease.require(invocation);
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      if (channel.isOpen()) {
        try (channel; lock) {
          completeOwnership();
        }
      } else {
        completeOwnership();
      }
      closed = true;
    }
  }

  private void completeOwnership() throws IOException {
    if (!invocation.owner().equals(ProcessIdentity.observe(ProcessHandle.current()))) {
      throw new IOException("Only the original owner can complete its lease closure");
    }
    BuildJournal.requireClosed(invocation);
    BuildFiles.write(invocation.directory().resolve("owner-closed.properties"), invocation.properties());
    ActiveLease.release(invocation);
  }

  private static final class NativeAcquisition implements AutoCloseable {
    private final FileChannel acquiredChannel;
    private Optional<FileLock> acquiredLock = Optional.empty();
    private boolean transferred;

    NativeAcquisition(FileChannel channel) {
      acquiredChannel = channel;
    }

    void acquire(BuildRequest request) throws IOException {
      try {
        acquiredLock = Optional.ofNullable(acquiredChannel.tryLock());
      } catch (OverlappingFileLockException exception) {
        throw new IOException("Another local build owns " + request.root() + "; no mutable build work was started", exception);
      }
      if (acquiredLock.isEmpty()) {
        throw new IOException("Another local build owns " + request.root() + "; no mutable build work was started");
      }
    }

    BuildLease transfer(InvocationContext context) {
      var result = new BuildLease(acquiredChannel, acquiredLock.orElseThrow(), context);
      transferred = true;
      return result;
    }

    @Override
    public void close() throws IOException {
      if (!transferred) {
        try (acquiredChannel) {
          if (acquiredLock.isPresent()) {
            acquiredLock.orElseThrow().close();
          }
        }
      }
    }
  }
}
