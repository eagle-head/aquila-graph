package org.aquilagraph.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Exercises actual nested Linux build roots without an outer fixture cleanup oracle. */
public final class NestedBuildOwnership {
  private NestedBuildOwnership() {
  }

  public static void main(String[] arguments) throws Exception {
    var directory = Path.of(arguments[0]).toAbsolutePath();
    var result = run(directory, Path.of(arguments[1]).toAbsolutePath());
    try (var writer = Files.newBufferedWriter(directory.resolve("result.properties"))) {
      result.store(writer, "Nested native build ownership");
    }
  }

  public static Properties run(Path directory, Path project) throws Exception {
    if (!"Linux".equals(System.getProperty("os.name"))) {
      throw new IOException("This observation requires native Linux cgroup ownership");
    }
    var outer = Files.createDirectories(directory.resolve("outer"));
    var inner = Files.createDirectories(directory.resolve("inner"));
    Files.createDirectories(inner.resolve("target"));
    var java = Path.of(System.getProperty("java.home"), "bin", "java");
    var entry = project.resolve("config/build/org/aquilagraph/build/BuildExecution.java");
    var heldSource = inner.resolve("HeldWork.java");
    Files.writeString(heldSource, """
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.StandardCopyOption;
        import java.nio.file.StandardOpenOption;
        import java.time.Duration;
        import java.util.Properties;
        class HeldWork {
          public static void main(String[] args) throws Exception {
            var root = Path.of(args[0]);
            var identity = new Properties();
            identity.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            identity.setProperty("started", ProcessHandle.current().info().startInstant().orElseThrow().toString());
            var pending = root.resolve("held-writing.properties");
            try (var writer = Files.newBufferedWriter(pending)) { identity.store(writer, "Held nested process"); }
            Files.move(pending, root.resolve("held.properties"), StandardCopyOption.ATOMIC_MOVE);
            var end = System.nanoTime() + Duration.ofSeconds(120).toNanos();
            while (!Files.exists(root.resolve("release")) && System.nanoTime() < end) {
              Files.writeString(root.resolve("target/heartbeat.txt"), "written\\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
              Thread.sleep(20);
            }
          }
        }
        """);
    var innerWrapper = wrapper(inner.resolve("held"), List.of(java.toString(), heldSource.toString(), inner.toString()));
    var nestedSource = outer.resolve("NestedBuild.java");
    Files.writeString(nestedSource, """
        import java.nio.file.Path;
        import java.util.List;
        class NestedBuild {
          public static void main(String[] args) throws Exception {
            var builder = new ProcessBuilder(args[0], "--enable-native-access=ALL-UNNAMED", args[1], "--root", args[2],
                "--tool-root", args[3], "--maven", args[4], "compile").directory(Path.of(args[2]).toFile()).inheritIO();
            for (var name : List.of("MAVEN_BASEDIR", "MAVEN_PROJECTBASEDIR", "AQUILA_BUILD_INVOCATION", "AQUILA_BUILD_RESERVATION",
                "AQUILA_BUILD_WRAPPER", "AQUILA_BUILD_WRAPPER_COMMAND", "AQUILA_BUILD_PROCESS", "AQUILA_BUILD_EFFECTIVE_GOALS")) {
              builder.environment().remove(name);
            }
            var child = builder.start();
            int exit;
            try { exit = child.waitFor(); } finally {
              child.getInputStream().close(); child.getErrorStream().close(); child.getOutputStream().close();
            }
            System.exit(exit);
          }
        }
        """);
    var outerWrapper = wrapper(outer.resolve("nested"), List.of(java.toString(), nestedSource.toString(), java.toString(),
        entry.toString(), inner.toString(), project.toString(), innerWrapper.toString()));
    var log = outer.resolve("owner.log");
    var owner = builder(java, entry, outer, project, outerWrapper, "compile", log).start();
    Optional<Properties> identity = Optional.empty();
    try {
      var deadline = System.nanoTime() + Duration.ofSeconds(50).toNanos();
      while (!Files.exists(inner.resolve("held.properties")) && owner.isAlive() && System.nanoTime() < deadline) {
        TimeUnit.MILLISECONDS.sleep(20);
      }
      if (!Files.exists(inner.resolve("held.properties"))) {
        throw new IOException("The nested native build did not start its held process: " + Files.readString(log));
      }
      var held = read(inner.resolve("held.properties"));
      identity = Optional.of(held);
      var parentSlice = slice(outer);
      var childSlice = slice(inner);
      var membership = Files.readString(Path.of("/proc", held.getProperty("pid"), "cgroup"));
      var hierarchy = !parentSlice.equals(childSlice)
          && childSlice.startsWith(parentSlice.substring(0, parentSlice.length() - ".slice".length()) + "-")
          && membership.contains("/" + parentSlice + "/") && membership.contains("/" + childSlice + "/");
      if (!hierarchy || live(held).isEmpty()) {
        throw new IOException("The actual nested worker is outside the expected native parent hierarchy: " + membership);
      }
      owner.destroyForcibly();
      if (!owner.waitFor(10, TimeUnit.SECONDS)) {
        throw new IOException("The terminated outer owner did not exit");
      }
      deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while (live(held).isPresent() && System.nanoTime() < deadline) {
        TimeUnit.MILLISECONDS.sleep(20);
      }
      var aliveBeforeRecovery = live(held).isPresent();
      var outerRecovery = recover(java, entry, outer, project, outerWrapper);
      var innerRecovery = recover(java, entry, inner, project, innerWrapper);
      var nativePath = Path.of(membership.strip().substring("0::".length()));
      var outerInvocation = parentUnit(nativePath, parentSlice);
      var innerInvocation = parentUnit(nativePath, childSlice);
      var unitsClosed = inactive(outerInvocation, outer.resolve("native-unit.log")) && inactive(innerInvocation, inner.resolve("native-unit.log"));
      var result = new Properties();
      result.setProperty("parent.slice", parentSlice);
      result.setProperty("child.slice", childSlice);
      result.setProperty("worker.cgroup", membership.strip());
      result.setProperty("hierarchy.verified", Boolean.toString(hierarchy));
      result.setProperty("invocation.units.closed", Boolean.toString(unitsClosed));
      result.setProperty("worker.alive.before.recovery", Boolean.toString(aliveBeforeRecovery));
      result.setProperty("worker.alive.after.recovery", Boolean.toString(live(held).isPresent()));
      result.setProperty("outer.recovery.exit", Integer.toString(outerRecovery));
      result.setProperty("inner.recovery.exit", Integer.toString(innerRecovery));
      if (aliveBeforeRecovery || live(held).isPresent() || outerRecovery != 0 || innerRecovery != 0 || !unitsClosed) {
        throw new IOException("Nested native closure or durable recovery failed: " + result);
      }
      return result;
    } finally {
      Files.writeString(inner.resolve("release"), "release");
      if (owner.isAlive()) {
        owner.destroyForcibly();
        owner.waitFor(10, TimeUnit.SECONDS);
      }
      if (identity.isPresent()) {
        var survivor = live(identity.orElseThrow());
        if (survivor.isPresent()) {
          survivor.orElseThrow().destroyForcibly();
          survivor.orElseThrow().onExit().get(10, TimeUnit.SECONDS);
        }
      }
      owner.getInputStream().close();
      owner.getErrorStream().close();
      owner.getOutputStream().close();
    }
  }

  private static String parentUnit(Path path, String unit) throws IOException {
    var entry = path;
    while (entry != null && entry.getFileName() != null) {
      if (entry.getFileName().toString().equals(unit)) {
        var parent = entry.getParent();
        if (parent != null && parent.getFileName() != null) {
          return parent.getFileName().toString();
        }
      }
      entry = entry.getParent();
    }
    throw new IOException("The native hierarchy does not contain its recorded slice: " + unit);
  }

  private static boolean inactive(String unit, Path log) throws Exception {
    var process = new ProcessBuilder("/usr/bin/systemctl", "--user", "show", unit, "--property=ActiveState", "--value")
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      if (!process.waitFor(3, TimeUnit.SECONDS)) {
        throw new IOException("Native invocation unit observation exceeded its bound");
      }
      return process.exitValue() == 0 && "inactive".equals(Files.readString(log).strip());
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
      }
      process.getInputStream().close();
      process.getErrorStream().close();
      process.getOutputStream().close();
    }
  }

  private static int recover(Path java, Path entry, Path root, Path project, Path wrapper) throws Exception {
    var process = builder(java, entry, root, project, wrapper, "line-endings-check", root.resolve("recovery.log")).start();
    try {
      if (!process.waitFor(45, TimeUnit.SECONDS)) {
        throw new IOException("Nested build recovery exceeded its bound: " + root);
      }
      return process.exitValue();
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
      }
      process.getInputStream().close();
      process.getErrorStream().close();
      process.getOutputStream().close();
    }
  }

  private static ProcessBuilder builder(Path java, Path entry, Path root, Path project, Path wrapper, String operation, Path log) {
    return IsolatedBuild.isolate(new ProcessBuilder(java.toString(), "--enable-native-access=ALL-UNNAMED", entry.toString(), "--root", root.toString(),
        "--tool-root", project.toString(), "--maven", wrapper.toString(), operation).directory(root.toFile()).redirectErrorStream(true)
        .redirectOutput(log.toFile()));
  }

  private static Path wrapper(Path file, List<String> words) throws IOException {
    var command = new StringBuilder("#!/bin/sh\nexec ");
    for (var word : words) {
      command.append('\'').append(word.replace("'", "'\"'\"'")).append("' ");
    }
    Files.writeString(file, command.append('\n'));
    if (!file.toFile().setExecutable(true)) {
      throw new IOException("The controlled native wrapper could not be made executable: " + file);
    }
    return file;
  }

  private static String slice(Path root) throws IOException {
    try (var files = Files.walk(root.resolve(".build-state/invocations"))) {
      for (var file : files.filter(path -> path.getFileName().toString().equals("intent.properties")).toList()) {
        var record = read(file);
        if ("maven-wrapper".equals(record.getProperty("kind"))) {
          return record.getProperty("containment.slice");
        }
      }
    }
    throw new IOException("The actual native wrapper reservation is missing: " + root);
  }

  private static Properties read(Path file) throws IOException {
    var result = new Properties();
    try (var input = Files.newInputStream(file)) {
      result.load(input);
    }
    return result;
  }

  private static Optional<ProcessHandle> live(Properties identity) {
    var started = Instant.parse(identity.getProperty("started"));
    return ProcessHandle.of(Long.parseLong(identity.getProperty("pid"))).filter(ProcessHandle::isAlive)
        .filter(process -> process.info().startInstant().filter(started::equals).isPresent());
  }
}
