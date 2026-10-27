package org.aquilagraph.build;

import static org.aquilagraph.build.BuildResourceAssertions.assertClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import org.aquilagraph.testsupport.IsolatedBuild;
import org.aquilagraph.testsupport.OwnedProcess;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

@Tag("process")
class LocalBuildProcessIT {
  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path directory;

  @Test
  void givenValidAndMalformedSource_whenTheRealCompilerRuns_thenValuesAndFailureReachTheCaller() throws Exception {
    // Arrange
    var fixture = fixture();
    var source = Files.createDirectories(fixture.root().resolve("src/main/java")).resolve("Sample.java");
    Files.writeString(source, "public class Sample { public static void main(String[] args) { System.out.println(42); } }");

    // Act
    var compiled = fixture.run("compile");
    var value = fixture.run(new ProcessBuilder(IsolatedBuild.java().toString(), "-cp", fixture.root().resolve("target/classes").toString(), "Sample"));
    Files.writeString(source, "public class Sample { this is invalid; }");
    var failed = fixture.run("compile");

    // Assert
    assertEquals(0, compiled.exitCode(), compiled.output());
    assertEquals(0, value.exitCode(), value.output());
    assertEquals("42", value.output().strip());
    assertNotEquals(0, failed.exitCode(), failed.output());
    assertTrue(failed.output().contains("COMPILATION ERROR"), failed.output());
    assertClosed(fixture.root());
  }

  @Test
  void givenLiteralSelectorAndSpecialPath_whenWindowsOrLinuxWrapperRuns_thenMavenReceivesTheExactProperty() throws Exception {
    // Arrange
    var fixture = fixture();
    fixture.writePom("", "", """
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-antrun-plugin</artifactId><version>%s</version><executions>
        <execution><id>capture-property</id><phase>initialize</phase><goals><goal>run</goal></goals><configuration><target>
        <mkdir dir="${project.build.directory}" /><echo file="${project.build.directory}/received.txt" encoding="UTF-8">${test}</echo>
        </target></configuration></execution></executions></plugin>
        """.formatted(fixture.version("maven-antrun-plugin.version")));
    var value = "MissingTest#literal \"quote\" & pipe| redir> less< bang! percent%PATH% slash\\\"quote\\\" tail\\";

    // Act
    var result = IsolatedBuild.windows() ? fixture.run(fixture.powerShell("test", "-Dtest=" + value)) : fixture.run("test", "-Dtest=" + value);

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertEquals(value, Files.readString(fixture.root().resolve("target/received.txt")));
    assertTrue(result.output().contains("No tests matching pattern"), result.output());
    assertClosed(fixture.root());
  }

  @Test
  void givenCleanRequest_whenTheAdmittedCleanLifecycleRuns_thenTargetIsRemovedAndLeaseAndBaselineSurvive() throws Exception {
    // Arrange
    var fixture = fixture();
    var baseline = Files.createDirectories(fixture.root().resolve(".build-state/baselines")).resolve("compiler.properties");
    Files.writeString(baseline, "retained baseline");
    Files.writeString(fixture.root().resolve("target/owned-output"), "replaceable");

    // Act
    var result = fixture.run("clean");

    // Assert
    assertEquals(0, result.exitCode(), result.output());
    assertFalse(Files.exists(fixture.root().resolve("target/owned-output")));
    assertEquals("retained baseline", Files.readString(baseline));
    assertTrue(Files.exists(fixture.root().resolve(".build-state/build.lock")));
    assertClosed(fixture.root());
  }

  @Test
  void givenAnUnsatisfiedRuntimeContract_whenFormattingOrCleaning_thenEnforcerRefusesBeforeMutation() throws Exception {
    // Arrange
    var fixture = fixture();
    var marker = Files.createDirectories(fixture.root().resolve("target/classes")).resolve("retained.txt");
    Files.writeString(marker, "retained");
    fixture.writePom("", "", fixture.runtimeEnforcer("[999,1000)") + """
        <plugin><groupId>com.diffplug.spotless</groupId><artifactId>spotless-maven-plugin</artifactId><version>%s</version></plugin>
        """.formatted(fixture.version("spotless-maven-plugin.version")));

    // Act
    var results = new ArrayList<IsolatedBuild.Result>();
    for (var operation : List.of("format", "format-check", "clean")) {
      results.add(fixture.run(operation));
    }

    // Assert
    assertEquals(3, results.size());
    for (var result : results) {
      assertNotEquals(0, result.exitCode(), result.output());
      assertTrue(result.output().contains("RequireJavaVersion"), result.output());
    }
    assertEquals("retained", Files.readString(marker));
    assertClosed(fixture.root());
  }

  @Test
  void givenNoOwnerAndExecutionOverrides_whenDefaultOrCleanRuns_thenActualAdmissionRefusesBeforeMutation() throws Exception {
    // Arrange
    var fixture = fixture();
    fixture.writePom("", "", """
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-antrun-plugin</artifactId><version>%s</version><executions>
        <execution><id>observe-mutation</id><phase>initialize</phase><goals><goal>run</goal></goals><configuration><target>
        <touch file="${project.basedir}/mutation-reached" />
        </target></configuration></execution></executions></plugin>
        """.formatted(fixture.version("maven-antrun-plugin.version")));
    var marker = fixture.root().resolve("target/retained-output");
    Files.writeString(marker, "retained");
    var results = new ArrayList<IsolatedBuild.Result>();

    // Act
    for (var goal : List.of("initialize", "clean")) {
      for (var override : List.of("-Dexec.skip=true", "-Dexec.args=-version", "-Dexec.async=true", "-Dexec.successCodes=1")) {
        var builder = IsolatedBuild.isolate(new ProcessBuilder().directory(fixture.root().toFile()).redirectErrorStream(true));
        WrapperCommand.configure(builder, fixture.tools().resolve(IsolatedBuild.windows() ? "mvnw.cmd" : "mvnw"), List.of(
            "-B", "--no-transfer-progress", "-Dmaven.repo.local=" + fixture.tools().resolve("target/maven-repository"), goal, override));
        results.add(fixture.run(builder));
      }
      var stale = IsolatedBuild.isolate(new ProcessBuilder().directory(fixture.root().toFile()).redirectErrorStream(true));
      stale.environment().put("AQUILA_BUILD_INVOCATION", UUID.randomUUID().toString());
      stale.environment().put("AQUILA_BUILD_RESERVATION", UUID.randomUUID().toString());
      stale.environment().put("AQUILA_BUILD_WRAPPER", UUID.randomUUID().toString());
      WrapperCommand.configure(stale, fixture.tools().resolve(IsolatedBuild.windows() ? "mvnw.cmd" : "mvnw"),
          List.of("-B", "--no-transfer-progress", "-Dmaven.repo.local=" + fixture.tools().resolve("target/maven-repository"), goal));
      results.add(fixture.run(stale));
    }

    // Assert
    assertEquals(10, results.size());
    for (var result : results) {
      assertNotEquals(0, result.exitCode(), result.output());
      assertTrue(result.output().contains("admit-"), result.output());
      assertTrue(result.output().contains("Use .\\Invoke-Build.ps1 <target> or make <target>"), result.output());
    }
    assertEquals("retained", Files.readString(marker));
    assertFalse(Files.exists(fixture.root().resolve("mutation-reached")));
  }

  @Test
  void givenAnUnmodeledMavenDebugProperty_whenCompileOrCleanIsRequested_thenTheOwnerRefusesBeforeMutation() throws Exception {
    // Arrange
    var fixture = fixture();
    var marker = fixture.root().resolve("target/retained.txt");
    Files.writeString(marker, "retained");

    // Act
    var results = new ArrayList<IsolatedBuild.Result>();
    for (var operation : List.of("compile", "clean")) {
      for (var property : List.of("-Dexec.skip=true", "-Dexec.args=-version")) {
        var builder = fixture.builder(operation);
        builder.environment().put("MAVEN_DEBUG_OPTS", property);
        results.add(fixture.run(builder));
      }
    }

    // Assert
    assertEquals(4, results.size());
    for (var result : results) {
      assertNotEquals(0, result.exitCode(), result.output());
      assertTrue(result.output().contains("Unsupported influencing environment: MAVEN_DEBUG_OPTS"), result.output());
    }
    assertEquals("retained", Files.readString(marker));
    assertFalse(Files.exists(fixture.root().resolve(".build-state")));
    try (var files = Files.list(marker.getParent())) {
      assertEquals(List.of(marker), files.toList());
    }
  }

  @Test
  void givenHeldWork_whenAContenderAndThenOwnerTerminationOccur_thenMutationIsRefusedAndRecoveryClosesOwnedProcesses() throws Exception {
    // Arrange
    var fixture = fixture();
    heldWork(fixture);
    var log = fixture.root().resolve("active-build.log");
    try (var process = OwnedProcess.start(fixture.builder("compile").redirectOutput(log.toFile()))) {
      await(fixture.root().resolve("active.properties"), process, log);
      var active = BuildFiles.read(fixture.root().resolve("active.properties"));
      var identity = new ProcessIdentity(Long.parseLong(active.getProperty("pid")), Instant.parse(active.getProperty("started")));

      // Act
      var contender = fixture.run("compile");
      process.handle().destroyForcibly();
      assertTrue(process.await(Duration.ofSeconds(15)), Files.readString(log));
      fixture.writePom("", "", "");
      var recovered = fixture.run("compile");

      // Assert
      assertNotEquals(0, contender.exitCode(), contender.output());
      assertTrue(contender.output().contains("Another local build owns"), contender.output());
      assertEquals(0, recovered.exitCode(), recovered.output());
      assertTrue(identity.liveProcess().isEmpty());
      assertClosed(fixture.root());
    }
  }

  @Test
  void givenAbruptDeathAtLaunchAndClosureBoundaries_whenTheNextOwnerRuns_thenDurableReservationsAreReconciled() throws Exception {
    // Arrange
    var stages = List.of("intent", "created", "closure");
    var results = new ArrayList<IsolatedBuild.Result>();

    // Act
    for (var stage : stages) {
      var fixture = fixture();
      var command = List.of(IsolatedBuild.java().toString(), "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"), BuildLifecycleProbe.class.getName(),
          fixture.root().toString(), fixture.tools().toString(), fixture.tools().resolve(IsolatedBuild.windows() ? "mvnw.cmd" : "mvnw").toString(), stage);
      var log = fixture.root().resolve("probe.log");
      try (var process = OwnedProcess.start(IsolatedBuild.isolate(new ProcessBuilder(command).directory(fixture.tools().toFile()).redirectErrorStream(true).redirectOutput(log.toFile())))) {
        await(fixture.root().resolve("probe-stage.txt"), process, log);
        process.handle().destroyForcibly();
        assertTrue(process.await(Duration.ofSeconds(15)), Files.readString(log));
        var result = fixture.run("compile");
        results.add(result);
        assertEquals(0, result.exitCode(), result.output());
        assertClosed(fixture.root());
      }
    }

    // Assert
    assertEquals(stages.size(), results.size());
    results.forEach(result -> assertEquals(0, result.exitCode(), result.output()));
  }

  private IsolatedBuild fixture() throws Exception {
    var fixture = new IsolatedBuild(Files.createTempDirectory(directory, "working space & values-"));
    fixture.writePom("", "", "");
    return fixture;
  }

  @Test
  void givenAWrapperThatChangesGoals_whenTheLifecycleIsAdmitted_thenGuidancePreservesTheMismatchAndOutputs() throws Exception {
    // Arrange
    var fixture = fixture();
    var marker = fixture.root().resolve("target/retained.txt");
    Files.writeString(marker, "retained");
    fixture.writePom("", "", """
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-antrun-plugin</artifactId><version>%s</version><executions>
        <execution><id>observe-initialization</id><phase>initialize</phase><goals><goal>run</goal></goals><configuration><target>
        <echo file="${project.basedir}/mutation-reached">unexpected</echo>
        </target></configuration></execution></executions></plugin>
        """.formatted(fixture.version("maven-antrun-plugin.version")));
    var wrapper = fixture.root().resolve(IsolatedBuild.windows() ? "changed-goals.cmd" : "changed-goals");
    var words = List.of(fixture.tools().resolve(IsolatedBuild.windows() ? "mvnw.cmd" : "mvnw").toString(),
        "-B", "--no-transfer-progress", "-Dmaven.repo.local=" + fixture.tools().resolve("target/maven-repository"), "initialize");
    var command = new StringBuilder(IsolatedBuild.windows() ? "@echo off\r\ncall " : "#!/bin/sh\nexec ");
    for (var word : words) {
      command.append(IsolatedBuild.windows() ? '"' + word + '"' : "'" + word.replace("'", "'\"'\"'") + "'").append(' ');
    }
    Files.writeString(wrapper, command.append(IsolatedBuild.windows() ? "\r\nexit /b %errorlevel%\r\n" : "\n"));
    if (!IsolatedBuild.windows()) {
      assertTrue(wrapper.toFile().setExecutable(true));
    }

    // Act
    var result = fixture.run(fixture.builder(wrapper, "compile"));

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("Effective Maven goals or lifecycle differ"), result.output());
    assertTrue(result.output().contains("Use .\\Invoke-Build.ps1 <target> or make <target>"), result.output());
    assertEquals("retained", Files.readString(marker));
    assertFalse(Files.exists(fixture.root().resolve("mutation-reached")));
    assertClosed(fixture.root());
  }

  @Test
  void givenWritingChild_whenTheAcknowledgedLauncherDies_thenTheOwnerFailsAndClosesEveryOwnedProcess() throws Exception {
    // Arrange
    var fixture = fixture();
    heldWork(fixture);
    var log = fixture.root().resolve("child-termination.log");
    try (var process = OwnedProcess.start(fixture.builder("compile").redirectOutput(log.toFile()))) {
      await(fixture.root().resolve("active.properties"), process, log);
      Path acknowledgement;
      try (var files = Files.walk(fixture.root().resolve(".build-state/invocations"))) {
        var candidates = files.filter(path -> path.getFileName().toString().equals("intent.properties")).toList();
        Optional<Path> selected = Optional.empty();
        for (var candidate : candidates) {
          if ("command-launcher".equals(BuildFiles.read(candidate).getProperty("kind"))) {
            selected = Optional.of(candidate.getParent().resolve("acknowledged.properties"));
            break;
          }
        }
        acknowledgement = selected.orElseThrow(() -> new IOException("The exact source-launcher reservation is required"));
      }
      var record = BuildFiles.read(acknowledgement);
      var launcher = new ProcessIdentity(Long.parseLong(record.getProperty("pid")), Instant.parse(record.getProperty("started")));

      // Act
      launcher.liveProcess().orElseThrow().destroyForcibly();
      assertTrue(process.await(Duration.ofSeconds(30)), Files.readString(log));

      // Assert
      assertNotEquals(0, process.exitValue(), Files.readString(log));
      assertTrue(launcher.liveProcess().isEmpty());
      assertClosed(fixture.root());
      assertFalse(Files.exists(fixture.root().resolve(".build-state/active-owner.properties")));
    }
  }

  @Test
  void givenWritingMavenWork_whenItsOwnerWaitIsInterrupted_thenInterruptionPropagatesAndResourcesClose() throws Exception {
    // Arrange
    var fixture = fixture();
    heldWork(fixture);
    var request = BuildRequest.parse(new String[]{"--root", fixture.root().toString(), "--tool-root", fixture.tools().toString(),
        "--maven", fixture.tools().resolve(IsolatedBuild.windows() ? "mvnw.cmd" : "mvnw").toString(), "compile"});
    var environment = new HashMap<>(System.getenv());
    environment.remove("MAVEN_BASEDIR");
    request.validateEnvironment(environment);
    var failure = new AtomicReference<Exception>();
    try (var lease = BuildLease.acquire(request); var processes = new ManagedProcesses(lease,
        Optional.of(fixture.root().resolve("interrupted-maven.log")))) {
      var owner = new Thread(() -> {
        try {
          processes.maven();
        } catch (IOException exception) {
          failure.set(exception);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          failure.set(exception);
        }
      });
      owner.start();
      var deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
      while (!Files.exists(fixture.root().resolve("active.properties")) && owner.isAlive() && System.nanoTime() < deadline) {
        TimeUnit.MILLISECONDS.sleep(10);
      }
      assertTrue(Files.exists(fixture.root().resolve("active.properties")), String.valueOf(failure.get()));

      // Act
      owner.interrupt();
      owner.join(15000);

      // Assert
      assertFalse(owner.isAlive());
      assertInstanceOf(InterruptedException.class, failure.get());
    }
    Files.delete(fixture.root().resolve("interrupted-maven.log"));
    assertClosed(fixture.root());
  }

  @Test
  void givenFailingWrapper_whenItsRealProcessExits_thenTheExactExitAndClosureReachTheCaller() throws Exception {
    // Arrange
    var fixture = fixture();
    var wrapper = fixture.root().resolve(IsolatedBuild.windows() ? "failure.CMD" : "failure");
    Files.writeString(wrapper, IsolatedBuild.windows() ? "@exit /b 17\n" : "#!/bin/sh\nexit 17\n");
    if (!IsolatedBuild.windows()) {
      assertTrue(wrapper.toFile().setExecutable(true));
    }

    // Act
    var result = fixture.run(fixture.builder(wrapper, "compile"));

    // Assert
    assertEquals(17, result.exitCode(), result.output());
    assertFalse(Files.exists(fixture.root().resolve("target/classes")));
    assertClosed(fixture.root());
  }

  @Test
  void givenAbsentWrapper_whenTheOwnerStarts_thenStartupFailureCannotBecomeSuccess() throws Exception {
    // Arrange
    var fixture = fixture();

    // Act
    var result = fixture.run(fixture.builder(fixture.root().resolve("absent-maven"), "compile"));

    // Assert
    assertNotEquals(0, result.exitCode(), result.output());
    assertTrue(result.output().contains("NoSuchFileException"), result.output());
    assertFalse(Files.exists(fixture.root().resolve(".build-state/active-owner.properties")));
  }

  @Test
  void givenAWrapperThatLeavesADescendant_whenTheOwnerReturns_thenNativeClosurePrecedesLeaseReuseAndFixtureCleanup() throws Exception {
    // Arrange
    var fixture = fixture();
    var wrapper = orphaningWrapper(fixture);
    var marker = fixture.root().resolve("descendant.properties");
    var log = fixture.root().resolve("orphaning-owner.log");
    Optional<ProcessIdentity> descendant = Optional.empty();

    // Act
    try (var owner = OwnedProcess.start(fixture.builder(wrapper, "compile").redirectOutput(log.toFile()))) {
      try {
        assertTrue(owner.await(Duration.ofSeconds(60)), Files.readString(log));
        assertTrue(Files.exists(marker), Files.readString(log));
        var record = BuildFiles.read(marker);
        var identity = new ProcessIdentity(Long.parseLong(record.getProperty("pid")), Instant.parse(record.getProperty("started")));
        descendant = Optional.of(identity);

        // Assert
        assertEquals(0, owner.exitValue(), Files.readString(log));
        assertTrue(identity.liveProcess().isEmpty(), "The independently identified descendant survived owner completion");
        assertClosed(fixture.root());
        var next = fixture.run("compile");
        assertEquals(0, next.exitCode(), next.output());
        assertTrue(identity.liveProcess().isEmpty());
        assertClosed(fixture.root());
      } finally {
        Files.writeString(fixture.root().resolve("release-descendant"), "release");
        if (descendant.isPresent()) {
          var survivor = descendant.orElseThrow().liveProcess();
          if (survivor.isPresent()) {
            survivor.orElseThrow().destroyForcibly();
            survivor.orElseThrow().onExit().get(10, TimeUnit.SECONDS);
          }
        }
      }
    }
  }

  private static Path orphaningWrapper(IsolatedBuild fixture) throws Exception {
    Files.writeString(fixture.root().resolve("HeldDescendant.java"), """
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.StandardOpenOption;
        import java.time.Duration;
        class HeldDescendant {
          public static void main(String[] arguments) throws Exception {
            var root = Path.of(arguments[0]);
            var deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
            while (!Files.exists(root.resolve("release-descendant")) && System.nanoTime() < deadline) {
              Files.writeString(root.resolve("target/descendant-output.txt"), "written\\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
              Thread.sleep(20);
            }
          }
        }
        """);
    var launcher = fixture.root().resolve("DescendantLauncher.java");
    Files.writeString(launcher, """
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.StandardCopyOption;
        import java.util.Properties;
        class DescendantLauncher {
          public static void main(String[] arguments) throws Exception {
            var root = Path.of(arguments[0]);
            var child = new ProcessBuilder(arguments[1], root.resolve("HeldDescendant.java").toString(), root.toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("descendant.log").toFile()).start();
            var record = new Properties();
            record.setProperty("pid", Long.toString(child.pid()));
            record.setProperty("started", child.info().startInstant().orElseThrow().toString());
            var pending = root.resolve("descendant-writing.properties");
            try (var writer = Files.newBufferedWriter(pending)) { record.store(writer, "Created descendant identity"); }
            Files.move(pending, root.resolve("descendant.properties"), StandardCopyOption.ATOMIC_MOVE);
          }
        }
        """);
    var arguments = List.of(IsolatedBuild.java().toString(), launcher.toString(), fixture.root().toString(), IsolatedBuild.java().toString());
    var command = new StringBuilder();
    for (var argument : arguments) {
      command.append(IsolatedBuild.windows() ? '"' + argument + '"' : "'" + argument.replace("'", "'\"'\"'") + "'").append(' ');
    }
    var wrapper = fixture.root().resolve(IsolatedBuild.windows() ? "orphaning-wrapper.cmd" : "orphaning-wrapper");
    Files.writeString(wrapper, IsolatedBuild.windows() ? "@echo off\r\n" + command + "\r\nexit /b %errorlevel%\r\n" : "#!/bin/sh\nexec " + command + "\n");
    if (!IsolatedBuild.windows()) {
      assertTrue(wrapper.toFile().setExecutable(true));
    }
    return wrapper;
  }

  @Test
  void givenAGitMonitorThatLeavesADescendant_whenTextVerificationFails_thenTheSharedOwnerClosesTheWholeGroup() throws Exception {
    // Arrange
    var fixture = fixture();
    orphaningWrapper(fixture);
    var initialized = fixture.run(new ProcessBuilder("git", "-C", fixture.root().toString(), "-c", "core.fsmonitor=false", "init", "--quiet"));
    assertEquals(0, initialized.exitCode(), initialized.output());
    Files.writeString(fixture.root().resolve("sample.txt"), "line\r\n");
    var added = fixture.run(new ProcessBuilder("git", "-C", fixture.root().toString(), "-c", "core.fsmonitor=false", "-c", "core.autocrlf=false", "add", "sample.txt"));
    assertEquals(0, added.exitCode(), added.output());
    var hook = fixture.root().resolve("fsmonitor-hook");
    var command = new StringBuilder("#!/bin/sh\n");
    for (var argument : List.of(IsolatedBuild.java().toString(), fixture.root().resolve("DescendantLauncher.java").toString(), fixture.root().toString(), IsolatedBuild.java().toString())) {
      command.append('\'').append(argument.replace('\\', '/').replace("'", "'\"'\"'")).append("' ");
    }
    command.append("\nprintf 'clock\\0/\\0'\n");
    Files.writeString(hook, command);
    if (!IsolatedBuild.windows()) {
      assertTrue(hook.toFile().setExecutable(true));
    }
    var hookCommand = "'" + hook.toString().replace('\\', '/').replace("'", "'\"'\"'") + "'";
    var configured = fixture.run(new ProcessBuilder("git", "-C", fixture.root().toString(), "config", "core.fsmonitor", hookCommand));
    assertEquals(0, configured.exitCode(), configured.output());
    var log = fixture.root().resolve("git-monitor-owner.log");
    Optional<ProcessIdentity> descendant = Optional.empty();

    // Act
    try (var owner = OwnedProcess.start(fixture.builder("line-endings-check").redirectOutput(log.toFile()))) {
      try {
        assertTrue(owner.await(Duration.ofSeconds(60)), Files.readString(log));
        var record = BuildFiles.read(fixture.root().resolve("descendant.properties"));
        var identity = new ProcessIdentity(Long.parseLong(record.getProperty("pid")), Instant.parse(record.getProperty("started")));
        descendant = Optional.of(identity);

        // Assert
        assertNotEquals(0, owner.exitValue(), Files.readString(log));
        assertTrue(Files.readString(log).contains("Non-LF text is present in the Git index"));
        assertTrue(identity.liveProcess().isEmpty(), "The Git hook descendant survived failed owner completion");
        assertClosed(fixture.root());
        var next = fixture.run("compile");
        assertEquals(0, next.exitCode(), next.output());
        assertTrue(identity.liveProcess().isEmpty());
      } finally {
        Files.writeString(fixture.root().resolve("release-descendant"), "release");
        if (descendant.isPresent()) {
          var survivor = descendant.orElseThrow().liveProcess();
          if (survivor.isPresent()) {
            survivor.orElseThrow().destroyForcibly();
            survivor.orElseThrow().onExit().get(10, TimeUnit.SECONDS);
          }
        }
      }
    }
  }

  @Test
  void givenLostContainmentEvidence_whenRecoveryCannotObserveAWindowsJob_thenMutationRemainsRefused() throws Exception {
    // Arrange
    var fixture = fixture();
    var completed = fixture.run("compile");
    assertEquals(0, completed.exitCode(), completed.output());
    Optional<Path> wrapper = Optional.empty();
    try (var files = Files.walk(fixture.root().resolve(".build-state/invocations"))) {
      for (var intent : files.filter(path -> path.getFileName().toString().equals("intent.properties")).toList()) {
        if ("maven-wrapper".equals(BuildFiles.read(intent).getProperty("kind"))) {
          wrapper = Optional.of(intent.getParent());
          break;
        }
      }
    }
    var resource = wrapper.orElseThrow();
    var closed = resource.resolve("closed.properties");
    var contained = resource.resolve("contained-closed.properties");
    var originalClosed = Files.readAllBytes(closed);
    var originalContained = Files.readAllBytes(contained);
    Files.delete(closed);
    Files.delete(contained);

    // Act
    try {
      var recovered = fixture.run("compile");

      // Assert
      if (IsolatedBuild.windows()) {
        assertNotEquals(0, recovered.exitCode(), recovered.output());
        assertTrue(recovered.output().contains("All native Windows job handles were lost"), recovered.output());
        assertFalse(Files.exists(closed));
      } else {
        assertEquals(0, recovered.exitCode(), recovered.output());
        assertClosed(fixture.root());
      }
    } finally {
      Files.write(contained, originalContained);
      Files.write(closed, originalClosed);
    }
    var afterRestoration = fixture.run("compile");
    assertEquals(0, afterRestoration.exitCode(), afterRestoration.output());
    assertClosed(fixture.root());
  }

  private static void heldWork(IsolatedBuild fixture) throws Exception {
    Files.writeString(fixture.root().resolve("HeldWork.java"), """
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.StandardCopyOption;
        import java.nio.file.StandardOpenOption;
        import java.util.Properties;
        class HeldWork {
          public static void main(String[] arguments) throws Exception {
            var properties = new Properties();
            properties.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            properties.setProperty("started", ProcessHandle.current().info().startInstant().orElseThrow().toString());
            var marker = Path.of(arguments[0]);
            var temporary = marker.resolveSibling("active-writing.properties");
            try (var writer = Files.newBufferedWriter(temporary)) { properties.store(writer, "Owned fixture work"); }
            Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE);
            while (!Files.exists(Path.of(arguments[1]))) {
              Files.writeString(marker.getParent().resolve("target/work-output.txt"), "written" + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
              Thread.sleep(25);
            }
          }
        }
        """);
    fixture.writePom("""
        <execution><id>hold-owned-work</id><phase>compile</phase><goals><goal>exec</goal></goals><configuration>
        <executable>${java.home}/bin/java</executable><arguments><argument>${project.basedir}/HeldWork.java</argument>
        <argument>${project.basedir}/active.properties</argument><argument>${project.basedir}/release-work</argument></arguments>
        </configuration></execution>
        """, "", "");
  }

  private static void await(Path marker, OwnedProcess owner, Path log) throws Exception {
    var deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (!Files.exists(marker)) {
      if (!owner.handle().isAlive() || System.nanoTime() >= deadline) {
        throw new IOException("The isolated owner did not reach its observable boundary: " + Files.readString(log));
      }
      TimeUnit.MILLISECONDS.sleep(10);
    }
  }

}
