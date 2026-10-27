package org.aquilagraph.build;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.util.Optional;

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
    request.validateEnvironment(System.getenv());
    int exit;
    try (var lease = BuildLease.acquire(request);
        var processes = new ManagedProcesses(lease, Optional.empty());
        var shutdown = new ShutdownRegistration(processes)) {
      shutdown.requireActive();
      if (request.operation().checksLineEndings()) {
        processes.checkLineEndings();
      }
      if (request.operation() == BuildOperation.COVERAGE) {
        Files.deleteIfExists(request.root().resolve("target/jacoco.exec"));
      }
      exit = request.operation() == BuildOperation.LINE_ENDINGS_CHECK ? 0 : processes.maven();
      if (exit == 0 && request.operation().integrationScope().isPresent()) {
        var scope = request.operation().integrationScope().orElseThrow();
        var directory = IntegrationCatalog.directory(request.root(), lease.context().nonce());
        var catalog = IntegrationCatalog.read(directory.resolve("catalog.properties"));
        catalog.requireSelection(scope, request.integrationClasses());
        IntegrationResults.verify(catalog, directory, request.root().resolve("target/failsafe-reports/failsafe-summary.xml"));
        System.getLogger(BuildExecution.class.getName()).log(Level.INFO,
            "Verified integration scope: " + scope + "; complete validation: incomplete");
      }
    }
    System.exit(exit);
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
