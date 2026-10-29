package org.aquilagraph.build;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Optional;
import org.aquilagraph.build.BuildCancellation.Requested;

/** Shared local entry; platform adapters forward literal argument elements and exit codes. */
public final class BuildExecution {
  static final int REQUIRED_JAVA_FEATURE = 25;

  private BuildExecution() {
  }

  /**
   * Runs exactly one local operation under exclusive output and process ownership.
   *
   * @param arguments
   *          optional root/executable, operation, and admitted Maven flags
   * @throws IOException
   *           if configuration, ownership, launch, or closure fails
   * @throws InterruptedException
   *           if local execution is canceled
   */
  public static void main(String[] arguments) throws IOException, InterruptedException {
    var request = BuildRequest.parse(AdapterArguments.read(arguments, System.getenv()));
    if (request.operation() == BuildOperation.CANCEL) {
      var observed = BuildCancellation.request(request.root()).awaitClosure(Duration.ofSeconds(60));
      System.getLogger(BuildExecution.class.getName()).log(Level.INFO,
          observed
              ? "Local build canceled; resources and output lease are closed"
              : "Local build completed before observing cancellation; resources and output lease are closed");
      return;
    }
    request.validateEnvironment(System.getenv());
    int exit = -1;
    Optional<CompleteValidation> complete = Optional.empty();
    try {
      try (var lease = BuildLease.acquire(request)) {
        if (request.operation() == BuildOperation.VERIFY_ALL) {
          complete = Optional.of(CompleteValidation.prepare(lease.context()));
        }
        try (var processes = new ManagedProcesses(lease, Optional.empty()); var shutdown = new ShutdownRegistration(processes)) {
          shutdown.requireActive();
          if (request.operation().checksLineEndings()) {
            processes.checkLineEndings();
          }
          if (request.operation() == BuildOperation.COVERAGE) {
            CompleteValidation.invalidateCoverage(request.root());
          }
          exit = request.operation() == BuildOperation.LINE_ENDINGS_CHECK ? 0 : processes.maven();
          if (exit == 0 && request.operation() != BuildOperation.VERIFY_ALL && request.operation().integrationScope().isPresent()) {
            verifyIntegrations(request, lease.context());
          }
        }
        if (exit == 0 && complete.isPresent()) {
          complete.orElseThrow().verify();
        }
        if (complete.isPresent()) {
          complete.orElseThrow().record(exit == 0 ? "PASSED" : "FAILED", exit == 0 ? "All current obligations reconciled" : "Maven failed",
              exit);
        }
      }
    } catch (Requested exception) {
      if (exception.getSuppressed().length != 0) {
        recordFailure(complete, exception, -1);
        throw exception;
      }
      exit = 130;
      if (complete.isPresent()) {
        try {
          complete.orElseThrow().record("CANCELED", exception.toString(), exit);
        } catch (IOException recording) {
          exception.addSuppressed(recording);
          throw exception;
        }
      }
      System.getLogger(BuildExecution.class.getName()).log(Level.INFO, exception.toString());
    } catch (IOException | InterruptedException | IllegalArgumentException | IllegalStateException exception) {
      recordFailure(complete, exception, exit == 0 ? -1 : exit);
      throw exception;
    }
    System.exit(exit);
  }

  private static void recordFailure(Optional<CompleteValidation> complete, Exception exception, int exit) {
    if (complete.isPresent()) {
      try {
        complete.orElseThrow().record("FAILED", exception.toString(), exit);
      } catch (IOException recording) {
        exception.addSuppressed(recording);
      }
    }
  }

  private static void verifyIntegrations(BuildRequest request, InvocationContext context) throws IOException {
    var scope = request.operation().integrationScope().orElseThrow();
    var directory = SuiteCatalog.directory(request.root(), context.nonce());
    var catalog = SuiteCatalog.read(directory.resolve("catalog.properties"));
    catalog.requireSelection(scope, request.integrationClasses());
    SuiteResults.verify(catalog, directory, request.root().resolve("target/failsafe-reports/failsafe-summary.xml"));
    System.getLogger(BuildExecution.class.getName()).log(Level.INFO, "Verified integration scope: " + scope + "; complete validation: "
        + (request.operation() == BuildOperation.VERIFY_ALL ? "pending reconciliation" : "incomplete"));
  }

  private static void closeOnShutdown(ManagedProcesses processes) {
    try {
      processes.close();
    } catch (IOException exception) {
      System.getLogger(BuildExecution.class.getName()).log(Level.ERROR, "Owned build process closure failed", exception);
    }
  }

  private static final class ShutdownRegistration implements AutoCloseable {
    private final ManagedProcesses ownedProcesses;
    private final Thread hook;
    private volatile boolean runtimeClosing;

    ShutdownRegistration(ManagedProcesses processes) {
      ownedProcesses = processes;
      hook = new Thread(() -> {
        runtimeClosing = true;
        closeOnShutdown(ownedProcesses);
      }, "local-build-closure");
      Runtime.getRuntime().addShutdownHook(hook);
    }

    void requireActive() throws IOException {
      if (runtimeClosing) {
        throw new IOException("Local execution was canceled during shutdown registration");
      }
    }

    @Override
    public void close() throws IOException {
      ownedProcesses.close();
      if (!runtimeClosing) {
        try {
          Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException exception) {
          if (!runtimeClosing) {
            throw new IOException("Local shutdown registration could not be released", exception);
          }
        }
      }
    }
  }
}
