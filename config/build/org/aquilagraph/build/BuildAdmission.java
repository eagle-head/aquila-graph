package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Path;
import java.lang.System.Logger.Level;

/** Default and clean lifecycles admit only the exact live, leased native request. */
public final class BuildAdmission {
  private static final String NATIVE_ENTRY_GUIDANCE = "Use .\\Invoke-Build.ps1 <target> or make <target>";
  private static final int LIFECYCLE_ARGUMENT_COUNT = 1;
  private BuildAdmission() {
  }

  /**
   * Verifies the effective Maven goals before shared output mutation.
   *
   * @param arguments
   *          the default or clean lifecycle kind
   * @throws IOException
   *           if the request does not have exact live ownership
   */
  public static void main(String[] arguments) throws IOException {
    try {
      require(arguments);
    } catch (IOException | IllegalArgumentException exception) {
      throw new IOException("Maven lifecycle admission refused. " + NATIVE_ENTRY_GUIDANCE + ": " + exception.getMessage(), exception);
    }
  }

  private static void require(String... arguments) throws IOException {
    if (Runtime.version().feature() != BuildExecution.REQUIRED_JAVA_FEATURE) {
      throw new IOException("Maven admission requires a Java 25 runtime");
    }
    var nonce = System.getenv("AQUILA_BUILD_INVOCATION");
    var reservation = System.getenv("AQUILA_BUILD_RESERVATION");
    var wrapperReservation = System.getenv("AQUILA_BUILD_WRAPPER");
    if (nonce == null || reservation == null || wrapperReservation == null) {
      throw new IOException("Unsupervised Maven lifecycle refused; use .\\Invoke-Build.ps1 <target> or make <target>");
    }
    if (arguments.length != LIFECYCLE_ARGUMENT_COUNT || !("default".equals(arguments[0]) || "clean".equals(arguments[0]))) {
      throw new IOException("A default or clean lifecycle identity is required");
    }
    var context = InvocationContext.read(Path.of(""), nonce);
    var journal = LaunchAdmission.require(context, reservation);
    var wrapper = BuildJournal.open(context, wrapperReservation);
    var wrapperIntent = BuildFiles.read(wrapper.directory().resolve("intent.properties"));
    var launcherIntent = BuildFiles.read(journal.directory().resolve("intent.properties"));
    if (!"maven-wrapper".equals(BuildFiles.required(wrapperIntent, "kind"))
        || !wrapperReservation.equals(BuildFiles.required(launcherIntent, "command.reservation"))) {
      throw new IOException("The Maven request does not belong to its permitted wrapper reservation");
    }
    var request = BuildFiles.read(context.directory().resolve("invocation.properties"));
    var effectiveGoals = System.getenv("AQUILA_BUILD_EFFECTIVE_GOALS");
    if (!("goals=" + BuildFiles.required(request, "goals")).equals(effectiveGoals)
        || "clean".equals(arguments[0]) != "clean".equals(BuildFiles.required(request, "operation"))) {
      throw new IOException("Effective Maven goals or lifecycle differ from the recorded native operation: expected "
          + BuildFiles.required(request, "goals") + ", observed " + effectiveGoals + ", lifecycle " + arguments[0]);
    }
    wrapper.observe(ProcessHandle.current().parent().orElseThrow(() -> new IOException("Maven parent identity is unavailable")));
    if (BuildOperation.VERIFY_ALL.command().equals(BuildFiles.required(request, "operation"))) {
      CompleteConfiguration.admit(context);
    }
    System.getLogger(BuildAdmission.class.getName()).log(Level.INFO,
        "Admitted leased local operation " + BuildFiles.required(request, "operation"));
  }

}
