package org.aquilagraph.build;

import java.io.IOException;
import org.aquilagraph.build.ProcessGroup.RetainedJobs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.Objects;
import org.aquilagraph.build.WindowsBootSession.Restart;

/** Write-ahead reservations preserve unknown creation and observed closure as different states. */
final class BuildJournal {
  private static final String RESERVATION_PROPERTY = "reservation";
  private static final String KIND = "kind";
  private static final String INVOCATION_RECORD = "invocation.properties";
  private static final String GIT_OBSERVATION = "git-observation";
  private static final String PROCESS_OBSERVATION = "process-observation";
  private static final String CONTAINMENT_KIND = "containment.kind";
  private static final String OWNER_NAMESPACE = "owner.namespace";
  private static final String CONTAINMENT_PARENT = "containment.parent";
  private static final String CONTAINED_CLOSED = "contained-closed.properties";
  private static final String CONTAINED_START = "contained-start.properties";
  private static final String CLOSURE_EVIDENCE = "closure.evidence";
  private static final String HOST_RESTARTED = "host-restarted";
  private static final String CREATION_FAILED = "creation failed before a process was returned";
  private static final String NATIVE_CLOSED = "native containment and all recorded process identities were observed closed";
  private static final String PROCESSES_CLOSED = "all recorded process identities were observed closed";
  private static final long MINIMUM_PID = 1;
  private static final String INTENT_RECORD = "intent.properties";
  private static final String MAVEN_WRAPPER = "maven-wrapper";
  private static final String GIT_INSPECTION = "git-inspection";
  private static final String COMMAND_LAUNCHER = "command-launcher";
  private static final String WINDOWS_JOB = "windows-job";
  private static final String LINUX_SCOPE = "systemd-user-scope";
  private static final String RESOURCES_DIRECTORY = "resources";
  private static final String CLOSED_RECORD = "closed.properties";
  private final InvocationContext context;
  private final Path resourceDirectory;
  private final Properties ownership;

  private BuildJournal(InvocationContext context, Path resourceDirectory) {
    this(context, resourceDirectory, context.properties());
  }

  private BuildJournal(InvocationContext context, Path resourceDirectory, Properties ownership) {
    this.context = context;
    this.resourceDirectory = resourceDirectory;
    this.ownership = ownership;
  }

  static BuildJournal reserve(InvocationContext context, String kind) throws IOException {
    return reserve(context, kind, ProcessGroup::record);
  }

  /** Records the native containment boundary before a process can be created. */
  @FunctionalInterface
  interface ContainmentRecorder {
    void record(Properties intent) throws IOException;
  }

  static BuildJournal reserve(InvocationContext context, String kind, ContainmentRecorder containment) throws IOException {
    var resourceDirectory = context.directory().resolve(RESOURCES_DIRECTORY).resolve(UUID.randomUUID().toString());
    var journal = new BuildJournal(context, resourceDirectory);
    var intent = context.properties();
    intent.setProperty(RESERVATION_PROPERTY, name(resourceDirectory));
    intent.setProperty(KIND, kind);
    if (List.of(MAVEN_WRAPPER, GIT_INSPECTION).contains(kind)) {
      containment.record(intent);
    }
    intent.setProperty("intended.images", switch (kind) {
      case COMMAND_LAUNCHER -> "java,java.exe";
      case MAVEN_WRAPPER -> "java,java.exe,cmd.exe,sh,dash,bash,mvn,mvnw,systemd-run,"
          + Path.of(BuildFiles.required(BuildFiles.read(context.directory().resolve(INVOCATION_RECORD)), "maven")).getFileName();
      case GIT_INSPECTION -> "java,java.exe,git,git.exe,systemd-run";
      case GIT_OBSERVATION -> "git,git.exe";
      case "job-keeper" -> "java,java.exe,cmd,cmd.exe";
      case "group-control" -> "bash,systemctl";
      case PROCESS_OBSERVATION -> "powershell.exe,pwsh.exe";
      default -> throw new IllegalArgumentException("Unsupported managed process kind: " + kind);
    });
    intent.setProperty("intended.entry", switch (kind) {
      case COMMAND_LAUNCHER -> context.entry("CommandChild").toString();
      case MAVEN_WRAPPER -> context.entry("MavenWrapper").toString();
      case GIT_INSPECTION -> context.entry("GitInspection").toString();
      case GIT_OBSERVATION -> "git";
      case "job-keeper" -> context.entry("WindowsJobKeeper").toString();
      case "group-control" -> "/usr/bin/systemctl";
      case PROCESS_OBSERVATION -> "powershell.exe";
      default -> throw new IllegalArgumentException("Unsupported managed process kind: " + kind);
    });
    intent.setProperty("created", Instant.now().toString());
    intent.setProperty("account",
        ProcessHandle.current().info().user().orElseThrow(() -> new IOException("The launch account cannot be observed")));
    if (LINUX_SCOPE.equals(intent.getProperty(CONTAINMENT_KIND))) {
      LinuxScope.registerInvocation(context, intent);
    }
    BuildFiles.write(resourceDirectory.resolve(INTENT_RECORD), intent);
    return journal;
  }

  static BuildJournal open(InvocationContext context, String reservation) throws IOException {
    return open(context, reservation, context.properties());
  }

  private static BuildJournal open(InvocationContext context, String reservation, Properties ownership) throws IOException {
    if (!reservation.matches("[0-9a-f-]{36}")) {
      throw new IOException("Invalid process reservation");
    }
    var result = new BuildJournal(context, context.directory().resolve(RESOURCES_DIRECTORY).resolve(reservation), ownership);
    result.intent();
    return result;
  }

  private Properties intent() throws IOException {
    var record = BuildFiles.read(resourceDirectory.resolve(INTENT_RECORD));
    InvocationContext.requireIdentity(ownership, record);
    if (!reservation().equals(BuildFiles.required(record, RESERVATION_PROPERTY))) {
      throw new IOException("Process reservation belongs to another resource");
    }
    var kind = BuildFiles.required(record, KIND);
    var legacy = !ownership.containsKey(OWNER_NAMESPACE);
    if (!List.of(COMMAND_LAUNCHER, MAVEN_WRAPPER, GIT_INSPECTION, GIT_OBSERVATION, "group-control", PROCESS_OBSERVATION, "job-keeper")
        .contains(kind) && !(legacy && "maven-launcher".equals(kind))) {
      throw new IOException("An unrecognized resource cannot authorize process closure");
    }
    var contained = List.of(MAVEN_WRAPPER, GIT_INSPECTION).contains(kind) && (!legacy || record.containsKey(CONTAINMENT_KIND));
    var containment = record.getProperty(CONTAINMENT_KIND, "");
    if (contained ? !List.of(WINDOWS_JOB, LINUX_SCOPE).contains(containment) : !containment.isEmpty()) {
      throw new IOException("The reservation has an unsupported native containment kind");
    }
    if (record.containsKey(CONTAINMENT_PARENT)) {
      var parent = BuildFiles.required(record, CONTAINMENT_PARENT);
      if (!parent.matches("[0-9a-f-]{36}") || !GIT_OBSERVATION.equals(kind) || !GIT_INSPECTION.equals(BuildFiles.required(
          new BuildJournal(context, context.directory().resolve(RESOURCES_DIRECTORY).resolve(parent), ownership).intent(), KIND))) {
        throw new IOException("The observation does not belong to its native Git container");
      }
    }
    return record;
  }

  private void requireRecord(Properties record) throws IOException {
    InvocationContext.requireIdentity(ownership, record);
    if (record.containsKey(RESERVATION_PROPERTY) && !reservation().equals(BuildFiles.required(record, RESERVATION_PROPERTY))) {
      throw new IOException("Closure evidence belongs to another resource reservation");
    }
  }

  boolean containmentClosed() throws IOException {
    if (!intent().containsKey(CONTAINMENT_KIND)) {
      throw new IOException("A process-only reservation cannot supply native group closure");
    }
    Properties record;
    try {
      record = BuildFiles.read(resourceDirectory.resolve(CONTAINED_CLOSED));
    } catch (NoSuchFileException exception) {
      return false;
    }
    requireRecord(record);
    var evidence = record.getProperty(CLOSURE_EVIDENCE, "native-group-closed");
    if (HOST_RESTARTED.equals(evidence)) {
      WindowsBootSession.requireRestartEvidence(context.directory(), ownership, record);
    } else if (!"native-group-closed".equals(evidence) || !"0".equals(BuildFiles.required(record, "active.members"))) {
      throw new IOException("Native closure evidence requires an observed zero-member group");
    }
    try {
      if (Long.parseLong(BuildFiles.required(record, "observer.pid")) < MINIMUM_PID) {
        throw new IOException("Native closure observer identity is invalid");
      }
      Instant.parse(BuildFiles.required(record, "observer.started"));
    } catch (IllegalArgumentException exception) {
      throw new IOException("Native closure observer identity is invalid", exception);
    }
    return true;
  }

  boolean closed() throws IOException {
    var intent = intent();
    Properties record;
    try {
      record = BuildFiles.read(resourceDirectory.resolve(CLOSED_RECORD));
    } catch (NoSuchFileException exception) {
      return false;
    }
    requireRecord(record);
    try {
      Instant.parse(BuildFiles.required(record, "closed"));
    } catch (IllegalArgumentException exception) {
      throw new IOException("Resource closure timestamp is invalid", exception);
    }
    var reason = BuildFiles.required(record, "reason");
    if (HOST_RESTARTED.equals(record.getProperty(CLOSURE_EVIDENCE))) {
      if (!"native processes belong to a verified earlier host boot".equals(reason)) {
        throw new IOException("Restart resource closure has an unsupported reason");
      }
      WindowsBootSession.requireRestartEvidence(context.directory(), ownership, record);
    } else if (record.containsKey(CLOSURE_EVIDENCE)) {
      throw new IOException("Unsupported resource closure evidence");
    } else if (CREATION_FAILED.equals(reason)) {
      if (!identities().isEmpty() || Files.exists(resourceDirectory.resolve(CONTAINED_START))) {
        throw new IOException("Creation-failure closure contradicts observed process creation");
      }
    } else if (intent.containsKey(CONTAINMENT_KIND)) {
      if (!NATIVE_CLOSED.equals(reason)
          && !"native containment belongs to an earlier host boot; recorded process identities were reconciled".equals(reason)) {
        throw new IOException("Unsupported native resource closure reason");
      }
      if (!containmentClosed()) {
        // Pre-namespace closure declarations do not establish a new native observation.
        if (ownership.containsKey(OWNER_NAMESPACE) || Files.exists(resourceDirectory.resolve(CONTAINED_START))) {
          throw new IOException("Native resource closure lacks contained closure evidence: " + resourceDirectory);
        }
      }
    } else if (!PROCESSES_CLOSED.equals(reason) && !(!ownership.containsKey(OWNER_NAMESPACE) && NATIVE_CLOSED.equals(reason))
        && !(intent.containsKey(CONTAINMENT_PARENT)
            && reason.equals("recorded identities were observed closed; descendants remain owned by native group "
                + BuildFiles.required(intent, CONTAINMENT_PARENT)))) {
      throw new IOException("Unsupported process resource closure reason: " + resourceDirectory + "; " + reason);
    }
    return true;
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
    BuildFiles.write(resourceDirectory.resolve(CONTAINED_START), context.properties());
  }

  void containedClosed() throws IOException {
    var record = context.properties();
    record.setProperty(RESERVATION_PROPERTY, reservation());
    var observer = ProcessIdentity.observe(ProcessHandle.current());
    record.setProperty("observer.pid", Long.toString(observer.pid()));
    record.setProperty("observer.started", observer.started().toString());
    record.setProperty("active.members", "0");
    record.setProperty(CLOSURE_EVIDENCE, "native-group-closed");
    BuildFiles.write(resourceDirectory.resolve(CONTAINED_CLOSED), record);
  }

  void closeAfterRestart(Restart restart) throws IOException {
    restart.requireOwner(context);
    var intent = intent();
    var record = context.properties();
    record.setProperty(RESERVATION_PROPERTY, reservation());
    var observer = ProcessIdentity.observe(ProcessHandle.current());
    record.setProperty("observer.pid", Long.toString(observer.pid()));
    record.setProperty("observer.started", observer.started().toString());
    restart.record(record);
    if (WINDOWS_JOB.equals(intent.getProperty(CONTAINMENT_KIND))) {
      BuildFiles.write(resourceDirectory.resolve(CONTAINED_CLOSED), record);
    }
    record.setProperty("reason", "native processes belong to a verified earlier host boot");
    record.setProperty("closed", Instant.now().toString());
    BuildFiles.write(resourceDirectory.resolve(CLOSED_RECORD), record);
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
    intent.setProperty(CONTAINMENT_PARENT, reservation);
    BuildFiles.write(file, intent);
  }

  void observe(ProcessHandle process) throws IOException {
    var observed = ProcessIdentity.observeAvailable(process);
    if (observed.isPresent()) {
      var identity = observed.orElseThrow();
      var file = resourceDirectory.resolve("process-" + identity.pid() + ".properties");
      if (Files.exists(file) && identity.equals(ProcessIdentity.read(BuildFiles.read(file), ""))) {
        return;
      }
      var record = context.properties();
      identity.write(record, "");
      BuildFiles.write(file, record);
    }
  }

  void acknowledge() throws IOException {
    BuildFiles.write(resourceDirectory.resolve("acknowledged.properties"), acknowledgement());
  }

  Properties acknowledgement() throws IOException {
    observe(ProcessHandle.current());
    var record = context.properties();
    var identity = ProcessIdentity.observe(ProcessHandle.current());
    record.setProperty(RESERVATION_PROPERTY, reservation());
    identity.write(record, "");
    return record;
  }

  void closeWithoutLaunch() throws IOException {
    intent();
    if (!identities().isEmpty() || Files.exists(resourceDirectory.resolve(CONTAINED_START))) {
      throw new IOException("A returned process cannot be closed as failed creation");
    }
    closeRecord(CREATION_FAILED);
  }

  void closeObserved() throws IOException {
    stopObserved();
    completeObserved();
  }

  void stopObserved() throws IOException {
    if (closed()) {
      return;
    }
    ProcessGroup.close(this);
    ProcessClosure.close(this, Duration.ofSeconds(10));
  }

  void completeObserved() throws IOException {
    if (closed()) {
      return;
    }
    var intent = intent();
    if (intent.containsKey(CONTAINMENT_KIND)) {
      if (!containmentClosed()) {
        closeWithoutLaunch();
        return;
      }
      var evidence = resourceDirectory.resolve(CONTAINED_CLOSED);
      closeRecord(Files.exists(evidence) && HOST_RESTARTED.equals(BuildFiles.read(evidence).getProperty(CLOSURE_EVIDENCE))
          ? "native containment belongs to an earlier host boot; recorded process identities were reconciled"
          : NATIVE_CLOSED);
    } else if (intent.containsKey(CONTAINMENT_PARENT)) {
      closeRecord("recorded identities were observed closed; descendants remain owned by native group "
          + BuildFiles.required(intent, CONTAINMENT_PARENT));
    } else {
      closeRecord(PROCESSES_CLOSED);
    }
  }

  private void closeRecord(String reason) throws IOException {
    if (closed()) {
      return;
    }
    var record = context.properties();
    record.setProperty(RESERVATION_PROPERTY, reservation());
    record.setProperty("reason", reason);
    record.setProperty("closed", Instant.now().toString());
    BuildFiles.write(resourceDirectory.resolve(CLOSED_RECORD), record);
  }

  List<ProcessIdentity> identities() throws IOException {
    var result = new ArrayList<ProcessIdentity>();
    try (var entries = Files.newDirectoryStream(resourceDirectory, "process-*.properties")) {
      for (var entry : entries) {
        var record = BuildFiles.read(entry);
        requireRecord(record);
        result.add(ProcessIdentity.read(record, ""));
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
        if (!open(context, name(resource)).closed()) {
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
          if (launchersFirst == "command-launcher".equals(BuildFiles.required(intent, KIND))) {
            var journal = open(context, name(resource));
            for (var process : ProcessInventory.reservation(resource)) {
              journal.observe(process);
            }
            journal.stopObserved();
          }
        }
      }
    }
    for (var resource : pending) {
      open(context, name(resource)).completeObserved();
    }
  }

  static void requireClosed(InvocationContext context) throws IOException {
    var invocation = BuildFiles.read(context.directory().resolve(INVOCATION_RECORD));
    if (invocation.containsKey("containment.invocation.slice") && !LinuxScope.invocationClosed(context.directory(), invocation)) {
      throw new IOException("The build owner has an unresolved native invocation slice: " + context.directory());
    }
    var resources = context.directory().resolve(RESOURCES_DIRECTORY);
    if (Files.isDirectory(resources)) {
      try (var entries = Files.newDirectoryStream(resources)) {
        for (var resource : entries) {
          if (!open(context, name(resource)).closed()) {
            throw new IOException("The build owner has unresolved resources: " + resource);
          }
        }
      }
    }
    var invocations = context.root().resolve(".build-state/invocations");
    try (var entries = Files.newDirectoryStream(invocations)) {
      for (var entry : entries) {
        var record = BuildFiles.read(entry.resolve(INVOCATION_RECORD));
        if (PROCESS_OBSERVATION.equals(record.getProperty("operation")) && context.namespace().equals(record.getProperty(OWNER_NAMESPACE))
            && Long.toString(context.owner().pid()).equals(record.getProperty("owner.pid"))
            && context.owner().started().toString().equals(record.getProperty("owner.started"))) {
          var observation = InvocationContext.read(context.root(), name(entry));
          observation.requireIdentity(BuildFiles.read(entry.resolve("owner-closed.properties")));
          try (var observed = Files.newDirectoryStream(entry.resolve(RESOURCES_DIRECTORY))) {
            for (var resource : observed) {
              if (!open(observation, name(resource)).closed()) {
                throw new IOException("The build owner has an unresolved native observation: " + resource);
              }
            }
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
    var invocationRecord = BuildFiles.read(invocation.resolve(INVOCATION_RECORD));
    if (!name(invocation).equals(BuildFiles.required(invocationRecord, "nonce"))) {
      throw new IOException("Historical build invocation differs from its directory nonce: " + invocation);
    }
    if (!ProcessIdentity.namespace().equals(invocationRecord.getProperty(OWNER_NAMESPACE))) {
      var historical = new InvocationContext(root, invocation, root, BuildFiles.required(invocationRecord, "nonce"),
          ProcessIdentity.read(invocationRecord, "owner."),
          invocationRecord.getProperty(OWNER_NAMESPACE, invocationRecord.getProperty("owner.platform", "")));
      try (var entries = Files.newDirectoryStream(resources)) {
        for (var resource : entries) {
          if (!open(historical, name(resource), invocationRecord).closed()) {
            throw new IOException("Unresolved build resources belong to another process namespace: " + resources);
          }
        }
      }
      if (invocationRecord.containsKey("containment.invocation.slice")) {
        if (!LinuxScope.invocationClosed(invocation, invocationRecord)) {
          throw new IOException("Unresolved native invocation belongs to another process namespace: " + invocation);
        }
      }
      return;
    }
    var context = InvocationContext.read(root, name(invocation));
    var unresolved = false;
    try (var entries = Files.newDirectoryStream(resources)) {
      for (var resource : entries) {
        unresolved |= !open(context, name(resource)).closed();
      }
    }
    var nativePending = invocationRecord.containsKey("containment.invocation.slice")
        && !LinuxScope.invocationClosed(invocation, invocationRecord);
    if (!unresolved && !nativePending) {
      return;
    }
    if (!ProcessIdentity.namespace().equals(BuildFiles.required(invocationRecord, OWNER_NAMESPACE))) {
      throw new IOException("Unresolved build resources belong to another process namespace: " + resources
          + "; reconcile them on their recorded operating system before another mutation");
    }
    if (context.owner().liveProcess().isPresent()) {
      throw new IOException("An earlier live owner still has unresolved resources; another mutation is refused");
    }
    if (BuildRequest.windows() && unresolved && recoverAfterRestart(context, resources)) {
      return;
    }
    closeResources(context, resources);
    LinuxScope.closeInvocation(context);
  }

  private static boolean recoverAfterRestart(InvocationContext context, Path resources) throws IOException {
    var observed = WindowsProcessInventory.boot(context.properties());
    Optional<Restart> restarted = Optional.empty();
    try (var entries = Files.newDirectoryStream(resources)) {
      for (var resource : entries) {
        if (!open(context, name(resource)).closed()) {
          restarted = WindowsBootSession.reconcile(open(context, name(resource)), observed);
          break;
        }
      }
    }
    if (restarted.isEmpty()) {
      return false;
    }
    try (var entries = Files.newDirectoryStream(resources)) {
      for (var resource : entries) {
        if (!open(context, name(resource)).closed()) {
          open(context, name(resource)).closeAfterRestart(restarted.orElseThrow());
        }
      }
    }
    return true;
  }
}
