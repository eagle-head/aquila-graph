package org.aquilagraph.build;

import java.io.IOException;
import java.io.Closeable;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Atomic complete ownership claims complement native locks across process namespaces. */
final class ActiveLease {
  private ActiveLease() {
  }

  static String acquire(BuildRequest request) throws IOException {
    return acquire(request, Files::createLink);
  }

  static String acquire(BuildRequest request, Publication publication) throws IOException {
    var active = request.root().resolve(".build-state/active-owner.properties");
    if (Files.exists(active)) {
      var old = BuildFiles.read(active);
      if (!ProcessIdentity.namespace().equals(BuildFiles.required(old, "owner.namespace"))) {
        throw new IOException(
            "Another process namespace owns unresolved build resources; reconcile the recorded owner on its operating system");
      }
      var identity = ProcessIdentity.read(old, "owner.");
      if (identity.liveProcess().isPresent()) {
        throw new IOException("Another live local build owner has unresolved resources; mutation is refused");
      }
      BuildJournal.recover(request.root());
      Files.delete(active);
    }
    BuildJournal.recover(request.root());
    var nonce = UUID.randomUUID().toString();
    var directory = request.root().resolve(".build-state/lease-claims");
    var claim = directory.resolve(nonce + ".properties");
    var properties = new InvocationContext(request.root(), directory, request.toolRoot(), nonce,
        ProcessIdentity.observe(ProcessHandle.current()), ProcessIdentity.namespace()).properties();
    BuildFiles.write(claim, properties);
    try {
      publication.create(active, claim);
    } catch (FileAlreadyExistsException exception) {
      try (Closeable _ = () -> Files.delete(claim)) {
        throw new IOException("Another local build acquired the shared filesystem ownership claim", exception);
      }
    }
    return nonce;
  }

  /** Atomic filesystem publication is the ownership claim's external boundary. */
  @FunctionalInterface
  interface Publication {
    void create(Path active, Path claim) throws IOException;
  }

  static void require(InvocationContext context) throws IOException {
    var claim = BuildFiles.read(context.root().resolve(".build-state/active-owner.properties"));
    ProcessIdentity.requireNativeIdentity(context.properties(), claim, "owner.");
    if (!context.namespace().equals(ProcessIdentity.namespace())
        || !context.namespace().equals(BuildFiles.required(claim, "owner.namespace"))) {
      throw new IOException("The active filesystem claim belongs to another process namespace");
    }
    if (!context.nonce().equals(BuildFiles.required(claim, "nonce"))
        || !Long.toString(context.owner().pid()).equals(BuildFiles.required(claim, "owner.pid"))
        || !context.owner().started().toString().equals(BuildFiles.required(claim, "owner.started"))) {
      throw new IOException("The active filesystem claim differs from this exact invocation");
    }
  }

  static void release(InvocationContext context) throws IOException {
    require(context);
    Files.delete(context.root().resolve(".build-state/active-owner.properties"));
    Files.delete(context.root().resolve(".build-state/lease-claims").resolve(context.nonce() + ".properties"));
  }
}
