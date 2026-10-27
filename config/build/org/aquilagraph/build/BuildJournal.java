package org.aquilagraph.build;

import java.io.IOException;
import org.aquilagraph.build.ProcessGroup.RetainedJobs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

/** Write-ahead reservations preserve unknown creation and observed closure as different states. */
final class BuildJournal {
  private static final String INTENT_RECORD = "intent.properties";
  private static final String LINUX_SCOPE = "systemd-user-scope";
  private static final String RESOURCES_DIRECTORY = "resources";
  private static final String STARTED_PROPERTY = "started";
  private static final String CLOSED_RECORD = "closed.properties";
  private final InvocationContext context;
  private final Path resourceDirectory;

  private BuildJournal(InvocationContext context, Path resourceDirectory) {
    this.context = context;
    this.resourceDirectory = resourceDirectory;
  }

  static BuildJournal reserve(InvocationContext context, String kind) throws IOException {
    var resourceDirectory = context.directory().resolve(RESOURCES_DIRECTORY).resolve(UUID.randomUUID().toString());
    var journal = new BuildJournal(context, resourceDirectory);
    var intent = context.properties();
    intent.setProperty("reservation", name(resourceDirectory));
    intent.setProperty("kind", kind);
    if (List.of("maven-wrapper", "git-inspection").contains(kind)) {
      ProcessGroup.record(intent);
    }
    intent.setProperty("intended.images", switch (kind) {
      case "command-launcher" -> "java,java.exe";
      case "maven-wrapper" -> "java,java.exe,cmd.exe,sh,dash,bash,mvn,mvnw,systemd-run,"
          + Path.of(BuildFiles.required(BuildFiles.read(context.directory().resolve("invocation.properties")), "maven")).getFileName();
      case "git-inspection" -> "java,java.exe,git,git.exe,systemd-run";
      case "git-observation" -> "git,git.exe";
      case "group-control" -> "bash,systemctl";
      case "process-observation" -> "powershell.exe,pwsh.exe";
      default -> throw new IllegalArgumentException("Unsupported managed process kind: " + kind);
    });
    intent.setProperty("intended.entry", switch (kind) {
      case "command-launcher" -> context.entry("CommandChild").toString();
      case "maven-wrapper" -> context.entry("MavenWrapper").toString();
      case "git-inspection" -> context.entry("GitInspection").toString();
      case "git-observation" -> "git";
      case "group-control" -> "/usr/bin/systemctl";
      case "process-observation" -> "powershell.exe";
      default -> throw new IllegalArgumentException("Unsupported managed process kind: " + kind);
    });
    intent.setProperty("created", Instant.now().toString());
    intent.setProperty("account",
        ProcessHandle.current().info().user().orElseThrow(() -> new IOException("The launch account cannot be observed")));
    if (LINUX_SCOPE.equals(intent.getProperty("containment.kind"))) {
      LinuxScope.registerInvocation(context, intent);
    }
    BuildFiles.write(resourceDirectory.resolve(INTENT_RECORD), intent);
    return journal;
  }

  static BuildJournal open(InvocationContext context, String reservation) throws IOException {
    if (!reservation.matches("[0-9a-f-]{36}")) {
      throw new IOException("Invalid process reservation");
    }
    var result = new BuildJournal(context, context.directory().resolve(RESOURCES_DIRECTORY).resolve(reservation));
    var intent = BuildFiles.read(result.resourceDirectory.resolve(INTENT_RECORD));
    if (!context.nonce().equals(BuildFiles.required(intent, "nonce"))) {
      throw new IOException("Process reservation belongs to another invocation");
    }
    return result;
  }

  String reservation() {
    return name(resourceDirectory);
  }

  private static String name(Path resourceDirectory) {
    return Objects.requireNonNull(resourceDirectory.getFileName(), "An owned reservation or invocation requires a leaf name").toString();
  }

  Path directory() {
    return resourceDirectory;
  }

  InvocationContext invocationContext() {
    return context;
  }

  void recordEntry(Path executable) throws IOException {
    var file = resourceDirectory.resolve(INTENT_RECORD);
    var intent = BuildFiles.read(file);
    intent.setProperty("intended.entry", executable.toString());
    BuildFiles.write(file, intent);
  }

  void recordCommand(String command) throws IOException {
    var file = resourceDirectory.resolve(INTENT_RECORD);
    var intent = BuildFiles.read(file);
    intent.setProperty("command.reservation", command);
    BuildFiles.write(file, intent);
  }

  void containedStart() throws IOException {
    BuildFiles.write(resourceDirectory.resolve("contained-start.properties"), context.properties());
  }

  void containedClosed() throws IOException {
    var record = context.properties();
    var observer = ProcessIdentity.observe(ProcessHandle.current());
    record.setProperty("observer.pid", Long.toString(observer.pid()));
    record.setProperty("observer.started", observer.started().toString());
    record.setProperty("active.members", "0");
    BuildFiles.write(resourceDirectory.resolve("contained-closed.properties"), record);
  }

  Path observationDirectory() throws IOException {
    return Files.createDirectories(context.root().resolve("target/build-observation").resolve(context.nonce()).resolve(reservation()));
  }

  void recordInspection(Optional<List<String>> arguments, int expectedExit) throws IOException {
    var file = resourceDirectory.resolve(INTENT_RECORD);
    var intent = BuildFiles.read(file);
    intent.setProperty("inspection.mode", arguments.isPresent() ? "single" : "line-endings");
    intent.setProperty("inspection.expected.exit", Integer.toString(expectedExit));
    var values = arguments.orElseGet(List::of);
    intent.setProperty("inspection.argument.count", Integer.toString(values.size()));
    for (var index = 0; index < values.size(); index++) {
      intent.setProperty("inspection.argument." + index, values.get(index));
    }
    BuildFiles.write(file, intent);
  }

  void recordContainer(String reservation) throws IOException {
    var file = resourceDirectory.resolve(INTENT_RECORD);
    var intent = BuildFiles.read(file);
    intent.setProperty("containment.parent", reservation);
    BuildFiles.write(file, intent);
  }

  void observe(ProcessHandle process) throws IOException {
    var observed = ProcessIdentity.observeAvailable(process);
    if (observed.isPresent()) {
      var identity = observed.orElseThrow();
      var file = resourceDirectory.resolve("process-" + identity.pid() + ".properties");
      if (Files.exists(file) && identity.started().toString().equals(BuildFiles.read(file).getProperty(STARTED_PROPERTY))) {
        return;
      }
      var record = context.properties();
      record.setProperty("pid", Long.toString(identity.pid()));
      record.setProperty(STARTED_PROPERTY, identity.started().toString());
      BuildFiles.write(file, record);
    }
  }

  void acknowledge() throws IOException {
    observe(ProcessHandle.current());
    var record = context.properties();
    var identity = ProcessIdentity.observe(ProcessHandle.current());
    record.setProperty("pid", Long.toString(identity.pid()));
    record.setProperty(STARTED_PROPERTY, identity.started().toString());
    BuildFiles.write(resourceDirectory.resolve("acknowledged.properties"), record);
  }

  void closeWithoutLaunch() throws IOException {
    closeRecord("creation failed before a process was returned");
  }

  void closeObserved() throws IOException {
    if (Files.exists(resourceDirectory.resolve(CLOSED_RECORD))) {
      return;
    }
    var intent = BuildFiles.read(resourceDirectory.resolve(INTENT_RECORD));
    ProcessGroup.close(this);
    ProcessClosure.close(this, Duration.ofSeconds(10));
    if (intent.containsKey("containment.kind")) {
      closeRecord("native containment and all recorded process identities were observed closed");
    } else if (intent.containsKey("containment.parent")) {
      closeRecord("recorded identities were observed closed; descendants remain owned by native group "
          + BuildFiles.required(intent, "containment.parent"));
    } else {
      closeRecord("all recorded process identities were observed closed");
    }
  }

  private void closeRecord(String reason) throws IOException {
    var record = context.properties();
    record.setProperty("reason", reason);
    record.setProperty("closed", Instant.now().toString());
    BuildFiles.write(resourceDirectory.resolve(CLOSED_RECORD), record);
  }

  List<ProcessIdentity> identities() throws IOException {
    var result = new ArrayList<ProcessIdentity>();
    try (var entries = Files.newDirectoryStream(resourceDirectory, "process-*.properties")) {
      for (var entry : entries) {
        var record = BuildFiles.read(entry);
        result.add(new ProcessIdentity(Long.parseLong(BuildFiles.required(record, "pid")),
            Instant.parse(BuildFiles.required(record, STARTED_PROPERTY))));
      }
    }
    return List.copyOf(result);
  }

  static void closeInvocation(InvocationContext context) throws IOException {
    var resources = context.directory().resolve(RESOURCES_DIRECTORY);
    if (!Files.isDirectory(resources)) {
      return;
    }
    closeResources(context, resources);
    LinuxScope.closeInvocation(context);
  }

  private static void closeResources(InvocationContext context, Path resources) throws IOException {
    var pending = new ArrayList<Path>();
    try (var entries = Files.newDirectoryStream(resources)) {
      for (var resource : entries) {
        if (!Files.exists(resource.resolve(CLOSED_RECORD))) {
          pending.add(resource);
        }
      }
    }
    try (var retained = new RetainedJobs()) {
      for (var resource : pending) {
        retained.retain(open(context, name(resource)));
      }
      for (var launchersFirst : List.of(true, false)) {
        for (var resource : pending) {
          var intent = BuildFiles.read(resource.resolve(INTENT_RECORD));
          if (launchersFirst == "command-launcher".equals(BuildFiles.required(intent, "kind"))) {
            var journal = open(context, name(resource));
            for (var process : ProcessInventory.reservation(resource)) {
              journal.observe(process);
            }
            journal.closeObserved();
          }
        }
      }
    }
  }

  static void requireClosed(InvocationContext context) throws IOException {
    var invocation = BuildFiles.read(context.directory().resolve("invocation.properties"));
    if (invocation.containsKey("containment.invocation.slice") && !Files.exists(context.directory().resolve("native-closed.properties"))) {
      throw new IOException("The build owner has an unresolved native invocation slice: " + context.directory());
    }
    var resources = context.directory().resolve(RESOURCES_DIRECTORY);
    if (Files.isDirectory(resources)) {
      try (var entries = Files.newDirectoryStream(resources)) {
        for (var resource : entries) {
          if (!Files.exists(resource.resolve(CLOSED_RECORD))) {
            throw new IOException("The build owner has unresolved resources: " + resource);
          }
        }
      }
    }
  }

  static void recover(Path root) throws IOException {
    var invocations = root.resolve(".build-state/invocations");
    if (!Files.isDirectory(invocations)) {
      return;
    }
    try (var entries = Files.newDirectoryStream(invocations)) {
      for (var invocation : entries) {
        var resources = invocation.resolve(RESOURCES_DIRECTORY);
        if (Files.isDirectory(resources)) {
          recoverResources(root, invocation, resources);
        }
      }
    }
  }

  private static void recoverResources(Path root, Path invocation, Path resources) throws IOException {
    var unresolved = false;
    try (var entries = Files.newDirectoryStream(resources)) {
      for (var resource : entries) {
        unresolved |= !Files.exists(resource.resolve(CLOSED_RECORD));
      }
    }
    var invocationRecord = BuildFiles.read(invocation.resolve("invocation.properties"));
    var nativePending = invocationRecord.containsKey("containment.invocation.slice")
        && !Files.exists(invocation.resolve("native-closed.properties"));
    if (!unresolved && !nativePending) {
      return;
    }
    if (!ProcessIdentity.namespace().equals(BuildFiles.required(invocationRecord, "owner.namespace"))) {
      throw new IOException("Unresolved build resources belong to another process namespace: " + resources
          + "; reconcile them on their recorded operating system before another mutation");
    }
    var context = InvocationContext.read(root, name(invocation));
    if (context.owner().liveProcess().isPresent()) {
      throw new IOException("An earlier live owner still has unresolved resources; another mutation is refused");
    }
    closeResources(context, resources);
    LinuxScope.closeInvocation(context);
  }
}
