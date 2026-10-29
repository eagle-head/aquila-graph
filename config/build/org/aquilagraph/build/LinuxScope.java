package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** A hierarchical native slice retains scopes and descendants created by nested builds. */
final class LinuxScope {
  private static final String INVOCATION_SLICE = "containment.invocation.slice";
  private static final String NATIVE_CLOSED = "native-closed.properties";
  private static final int MAX_UNIT_NAME_LENGTH = 255;
  private static final int REQUIRED_CGROUP_MOUNTS = 1;
  private static final int MAX_OBSERVATION_BYTES = 64 * 1024;

  private LinuxScope() {
  }

  static void record(Properties intent) throws IOException {
    var parent = System.getenv("AQUILA_BUILD_PARENT_SLICE");
    var prefix = "aquila";
    if (parent != null) {
      if (!parent.matches("aquila(-[0-9a-f]{32})+\\.slice")
          || Files.readString(Path.of("/proc/self/cgroup")).lines().noneMatch(line -> unifiedMembership(line, parent))) {
        throw new IOException("The inherited native parent slice does not contain this build process");
      }
      prefix = parent.substring(0, parent.length() - ".slice".length());
    }
    var slice = prefix + "-" + BuildFiles.required(intent, "nonce").replace("-", "") + "-"
        + BuildFiles.required(intent, "reservation").replace("-", "") + ".slice";
    if (slice.length() > MAX_UNIT_NAME_LENGTH) {
      throw new IOException("The nested native slice exceeds the operating system unit-name bound");
    }
    intent.setProperty("containment.slice", slice);
    intent.setProperty(INVOCATION_SLICE, prefix + "-" + BuildFiles.required(intent, "nonce").replace("-", "") + ".slice");
  }

  static void registerInvocation(InvocationContext context, Properties intent) throws IOException {
    var file = context.directory().resolve("invocation.properties");
    var record = BuildFiles.read(file);
    var slice = BuildFiles.required(intent, INVOCATION_SLICE);
    var existing = record.getProperty(INVOCATION_SLICE);
    if (existing != null && !slice.equals(existing)) {
      throw new IOException("The invocation cannot change its native parent slice");
    }
    record.setProperty(INVOCATION_SLICE, slice);
    record.setProperty("containment.account", BuildFiles.required(intent, "account"));
    BuildFiles.write(file, record);
  }

  static void closeInvocation(InvocationContext context) throws IOException {
    var record = BuildFiles.read(context.directory().resolve("invocation.properties"));
    if (!record.containsKey(INVOCATION_SLICE) || invocationClosed(context.directory(), record)) {
      return;
    }
    record.setProperty("containment.slice", BuildFiles.required(record, INVOCATION_SLICE));
    record.setProperty("account", BuildFiles.required(record, "containment.account"));
    close(record);
    BuildFiles.write(context.directory().resolve(NATIVE_CLOSED), context.properties());
  }

  static boolean invocationClosed(Path directory, Properties ownership) throws IOException {
    try {
      InvocationContext.requireIdentity(ownership, BuildFiles.read(directory.resolve(NATIVE_CLOSED)));
      return true;
    } catch (NoSuchFileException exception) {
      return false;
    }
  }

  private static String name(Properties intent) throws IOException {
    return "aquila-" + BuildFiles.required(intent, "nonce") + "-" + BuildFiles.required(intent, "reservation") + ".scope";
  }

  private static String slice(Properties intent) throws IOException {
    return BuildFiles.required(intent, "containment.slice");
  }

  static void configure(ProcessBuilder builder, Properties intent) throws IOException {
    var command = new ArrayList<>(List.of(executable("systemd-run").toString(), "--user", "--scope", "--quiet", "--expand-environment=no",
        "--unit=" + name(intent), "--slice=" + slice(intent)));
    command.addAll(builder.command());
    builder.environment().put("AQUILA_BUILD_PARENT_SLICE", slice(intent));
    builder.command(command);
  }

  static void close(Properties intent) throws IOException {
    var account = ProcessHandle.current().info().user()
        .orElseThrow(() -> new IOException("The current native process account cannot be observed"));
    if (!account.equals(BuildFiles.required(intent, "account"))) {
      throw new IOException("Native containment belongs to another account; mutation remains refused");
    }
    var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    var interrupted = Thread.interrupted();
    try {
      while (true) {
        var state = observe(intent);
        if (state.empty()) {
          if (state.loaded()) {
            var stopped = control(intent, List.of("stop", slice(intent)));
            if (stopped.exitCode() != 0 || !observe(intent).empty()) {
              throw new IOException("The empty native slice could not be stopped: " + stopped.output());
            }
          }
          return;
        }
        var result = control(intent, List.of("kill", "--kill-whom=all", "--signal=KILL", slice(intent)));
        if (result.exitCode() != 0 && !observe(intent).empty()) {
          throw new IOException("Owned native slice termination failed: " + result.output());
        }
        if (System.nanoTime() >= deadline) {
          throw new IOException("Owned native scope closure remains unresolved: " + slice(intent));
        }
        try {
          TimeUnit.MILLISECONDS.sleep(25);
        } catch (InterruptedException exception) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static State observe(Properties intent) throws IOException {
    var result = control(intent, List.of("show", slice(intent), "--property=Id", "--property=LoadState", "--property=ActiveState",
        "--property=ControlGroup", "--property=InvocationID"));
    if (result.exitCode() != 0) {
      throw new IOException("Owned native scope observation failed: " + result.output());
    }
    var state = new Properties();
    for (var line : result.output().lines().toList()) {
      var separator = line.indexOf('=');
      if (separator < 0) {
        throw new IOException("Unexpected native scope observation: " + result.output());
      }
      state.setProperty(line.substring(0, separator), line.substring(separator + 1));
    }
    if (!slice(intent).equals(BuildFiles.required(state, "Id"))) {
      throw new IOException("The observed native scope identity differs from its durable reservation");
    }
    var loadState = BuildFiles.required(state, "LoadState");
    var activeState = BuildFiles.required(state, "ActiveState");
    if ("not-found".equals(loadState) && "inactive".equals(activeState)) {
      return new State(true, false);
    }
    if (!"loaded".equals(loadState)) {
      throw new IOException("The owned slice has an unrecognized native state: " + result.output());
    }
    var group = state.getProperty("ControlGroup", "");
    if (group.isEmpty() && ("inactive".equals(activeState) || "failed".equals(activeState))) {
      return new State(true, true);
    }
    if (!BuildFiles.required(state, "InvocationID").matches("[0-9a-f]{32}") || !group.startsWith("/")
        || !group.endsWith("/" + slice(intent))) {
      throw new IOException("The observed native slice control group differs from its reservation: " + result.output());
    }
    var root = cgroupRoot();
    var events = root.resolve(group.substring(1)).resolve("cgroup.events").normalize();
    if (!events.startsWith(root)) {
      throw new IOException("The observed native scope control group is outside cgroupfs");
    }
    try {
      var content = Files.readString(events);
      if (content.lines().anyMatch("populated 0"::equals)) {
        return new State(true, true);
      }
      if (content.lines().noneMatch("populated 1"::equals)) {
        throw new IOException("The native slice population cannot be interpreted: " + events);
      }
      return new State(false, true);
    } catch (NoSuchFileException exception) {
      return new State("inactive".equals(activeState) || "failed".equals(activeState), true);
    }
  }

  private static Result control(Properties intent, List<String> arguments) throws IOException {
    var context = InvocationContext.observation(intent);
    var journal = BuildJournal.reserve(context, "group-control");
    var controller = executable("systemctl");
    journal.recordEntry(controller);
    var directory = context.root().resolve("target/build-observation").resolve(context.nonce());
    Files.createDirectories(directory);
    var output = directory.resolve("group-control.log");
    var command = new ArrayList<>(
        List.of(executable("bash").toString(), "--noprofile", "--norc", "-p", "-c", "exec -a \"$0\" \"$1\" \"${@:2}\"",
            "systemctl-" + context.nonce() + "-" + journal.reservation(), controller.toString(), "--user", "--no-pager"));
    command.addAll(arguments);
    var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
    builder.environment().keySet()
        .removeIf(name -> name.startsWith("BASH_") || List.of("ENV", "SHELLOPTS", "CDPATH", "GLOBIGNORE").contains(name));
    try (var observation = new NativeObservation(context, journal)) {
      var process = observation.start(builder);
      try {
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
          throw new IOException("Native scope control exceeded three seconds: " + output);
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Native scope control was interrupted: " + output, exception);
      }
      byte[] bytes;
      try (var stream = Files.newInputStream(output)) {
        bytes = stream.readNBytes(MAX_OBSERVATION_BYTES + 1);
      }
      if (bytes.length > MAX_OBSERVATION_BYTES) {
        throw new IOException("Native scope control exceeded its bounded observation size: " + output);
      }
      return new Result(process.exitValue(), new String(bytes, StandardCharsets.UTF_8));
    }
  }

  private static boolean unifiedMembership(String line, String parent) {
    var fields = line.split(":", 3);
    return fields.length == 3 && "0".equals(fields[0]) && fields[1].isEmpty() && fields[2].contains("/" + parent + "/");
  }

  private static Path executable(String name) throws IOException {
    for (var entry : System.getenv().getOrDefault("PATH", "").split(":", -1)) {
      var directory = Path.of(entry);
      if (directory.isAbsolute()) {
        var candidate = directory.resolve(name);
        if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
          return candidate.toRealPath();
        }
      }
    }
    throw new IOException("Native descendant containment requires an executable on PATH: " + name);
  }

  private static Path cgroupRoot() throws IOException {
    var roots = new ArrayList<Path>();
    for (var line : Files.readString(Path.of("/proc/self/mountinfo")).lines().toList()) {
      var sections = line.split(" - ", 2);
      if (sections.length == 2 && sections[1].startsWith("cgroup2 ")) {
        var fields = sections[0].split(" ", -1);
        if (fields.length < 6 || !"/".equals(fields[3])) {
          throw new IOException("Native containment requires an observable complete cgroup2 mount");
        }
        var mount = fields[4].replace("\\040", " ").replace("\\011", "\t").replace("\\012", "\n").replace("\\134", "\\");
        roots.add(Path.of(mount).toRealPath());
      }
    }
    var unique = roots.stream().distinct().toList();
    if (unique.size() != REQUIRED_CGROUP_MOUNTS) {
      throw new IOException("Native containment requires one observable complete cgroup2 mount");
    }
    return unique.getFirst();
  }

  private record State(boolean empty, boolean loaded) {
  }

  private record Result(int exitCode, String output) {
  }
}
