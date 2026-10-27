package org.aquilagraph.build;

import static org.aquilagraph.build.BuildResourceAssertions.assertClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.aquilagraph.testsupport.IsolatedBuild;
import org.aquilagraph.testsupport.OwnedProcess;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

@Tag("process")
class CompilationPreparationProcessIT {
  @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path directory;

  @AfterEach
  void closeCaptures() throws IOException {
    try (var files = Files.walk(directory)) {
      for (var path : files.filter(Files::isRegularFile).toList()) {
        assertTrue(path.toFile().setWritable(true), path.toString());
      }
    }
  }

  @Test
  void givenReadOnlyHardLinksInPreparedStorage_whenNativeRebuildResetAndCleanRun_thenOutsideProtectionAndFreshConsumersRemain() throws Exception {
    // Arrange
    var fixture = fixture();
    writeSubject(fixture, 137);
    assertEquals(0, nativeCompile(fixture).exitCode());
    var outside = fixture.root().resolve("outside-selected-tree.txt");
    Files.writeString(outside, "unrelated controlled bytes");
    assertTrue(outside.toFile().setReadOnly());
    var before = protection(outside);
    for (var scope : List.of("compilation/production/capture", "compilation/production/staged", "classes")) {
      var selected = Files.createDirectories(fixture.root().resolve("target").resolve(scope));
      assertTrue(Files.isSameFile(Files.createLink(selected.resolve("linked.txt"), outside), outside));
    }
    writeSubject(fixture, 149);

    // Act
    var rebuilt = nativeCompile(fixture);

    // Assert
    assertEquals(0, rebuilt.exitCode(), rebuilt.output());
    assertEquals("149", readSubject(fixture));
    assertEquals("unrelated controlled bytes", Files.readString(outside));
    assertEquals(before, protection(outside));
    for (var scope : List.of("resource-staging/production", "resource-staging/tests")) {
      Files.createLink(Files.createDirectories(fixture.root().resolve("target").resolve(scope)).resolve("linked.txt"), outside);
    }
    var reset = fixture.run("test");
    assertEquals(0, reset.exitCode(), reset.output());
    assertEquals(before, protection(outside));
    for (var scope : List.of("resource-staging/production", "resource-staging/tests")) {
      assertFalse(Files.exists(fixture.root().resolve("target").resolve(scope).resolve("linked.txt")));
    }
    Files.createLink(Files.createDirectories(fixture.root().resolve("target/compilation/production/capture")).resolve("linked.txt"), outside);
    var clean = fixture.run("clean");
    assertEquals(0, clean.exitCode(), clean.output());
    assertEquals("unrelated controlled bytes", Files.readString(outside));
    assertEquals(before, protection(outside));
    assertFalse(Files.exists(fixture.root().resolve("target/compilation")));
    writeSubject(fixture, 151);
    var recovered = nativeCompile(fixture);
    assertEquals(0, recovered.exitCode(), recovered.output());
    assertEquals("151", readSubject(fixture));
    assertEquals(before, protection(outside));
    assertClosed(fixture.root());
  }

  private static String protection(Path file) throws IOException {
    return IsolatedBuild.windows()
        ? "dos.readOnly=" + Files.readAttributes(file, DosFileAttributes.class).isReadOnly()
        : "posix.permissions=" + Files.getPosixFilePermissions(file).stream().sorted().toList();
  }

  @Test
  void givenRealNativeCompilationAndFilteredResources_whenInputsAreReusedChangedAndRemoved_thenOnlyCurrentConsumerValuesRemain() throws Exception {
    // Arrange
    var fixture = fixture();
    writeSubject(fixture, 83);
    var resource = Files.createDirectories(fixture.root().resolve("src/main/resources")).resolve("message.properties");
    Files.writeString(resource, "value=${fixture.value}");

    // Act
    var cold = nativeCompile(fixture);
    var hit = nativeCompile(fixture);
    var before = readSubject(fixture);
    var firstResource = Files.readString(fixture.root().resolve("target/classes/message.properties"));
    writeSubject(fixture, 89);
    var pom = fixture.root().resolve("pom.xml");
    Files.writeString(pom, Files.readString(pom).replace("<fixture.value>first</fixture.value>", "<fixture.value>second</fixture.value>"));
    var changed = nativeCompile(fixture);
    var after = readSubject(fixture);
    var secondResource = Files.readString(fixture.root().resolve("target/classes/message.properties"));
    Files.delete(resource);
    var removed = nativeCompile(fixture);

    // Assert
    assertEquals(0, cold.exitCode(), cold.output());
    assertTrue(cold.output().contains("compiling captured inputs"), cold.output());
    assertEquals(0, hit.exitCode(), hit.output());
    assertTrue(hit.output().contains("reused verified output; javac was not invoked"), hit.output());
    assertEquals("83", before);
    assertEquals("value=first", firstResource);
    assertEquals(0, changed.exitCode(), changed.output());
    assertEquals("89", after);
    assertEquals("value=second", secondResource);
    assertEquals(0, removed.exitCode(), removed.output());
    assertFalse(Files.exists(fixture.root().resolve("target/classes/message.properties")));
    assertClosed(fixture.root());
  }

  @Test
  void givenAnActiveCompiler_whenAContenderRunsAndTheCompilerIsCanceled_thenNoSuccessIsPublishedAndNativeRecoveryCompilesAgain() throws Exception {
    // Arrange
    var fixture = fixture();
    writeSubject(fixture, 91);
    var gate = addCompilerGate(fixture);
    var builder = fixture.builder("compile");
    builder.environment().put("COMPILATION_GATE", gate.toString());
    var log = fixture.root().resolve("canceled-build.log");
    IsolatedBuild.Result contender;
    long compilerPid;

    // Act
    try (var process = OwnedProcess.start(builder.redirectOutput(log.toFile()))) {
      var compiler = awaitCompiler(process);
      compilerPid = compiler.pid();
      var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (!Files.exists(gate) && process.handle().isAlive() && System.nanoTime() < deadline) {
        TimeUnit.MILLISECONDS.sleep(10);
      }
      assertTrue(Files.exists(gate), Files.readString(log));
      contender = fixture.run("compile");
      assertTrue(compiler.isAlive(), "The controlled compiler must still be active before cancellation");
      assertTrue(compiler.destroyForcibly());
      assertTrue(process.await(Duration.ofSeconds(60)), Files.readString(log));
      assertNotEquals(0, process.exitValue(), Files.readString(log));
    }

    // Assert
    assertNotEquals(0, contender.exitCode(), contender.output());
    assertTrue(contender.output().contains("Another local build owns"), contender.output());
    assertFalse(ProcessHandle.of(compilerPid).filter(ProcessHandle::isAlive).isPresent());
    assertFalse(Files.exists(fixture.root().resolve("target/compilation/production/success.properties")));
    Files.writeString(gate.resolveSibling("release"), "compiler may finish");
    writeSubject(fixture, 97);
    var recoveryBuilder = fixture.builder("compile");
    recoveryBuilder.environment().put("COMPILATION_GATE", gate.toString());
    var recovery = fixture.run(recoveryBuilder);
    assertEquals(0, recovery.exitCode(), recovery.output());
    assertEquals("97", readSubject(fixture));
    assertClosed(fixture.root());
  }

  @Test
  void givenPublicationInProgress_whenItsOwnedProcessIsStopped_thenPartialOutputCannotReuseAndRealCompilationRestoresIt() throws Exception {
    // Arrange
    var fixture = fixture();
    writeSubject(fixture, 101);
    assertEquals(0, nativeCompile(fixture).exitCode());
    var output = fixture.root().resolve("target/classes");
    var staged = Files.createDirectories(fixture.root().resolve("target/compilation/production/output"));
    var resourceCount = 1000;
    for (var index = 0; index < resourceCount; index++) {
      Files.writeString(staged.resolve("resource-" + index), "complete staged value");
    }
    var command = new ProcessBuilder(IsolatedBuild.java().toString(), "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
        CompilationPublicationProbe.class.getName(), fixture.root().toString()).redirectErrorStream(true)
        .redirectOutput(fixture.root().resolve("publication.log").toFile());

    // Act
    try (var process = OwnedProcess.start(command)) {
      var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (!Files.exists(output.resolve("resource-0")) && process.handle().isAlive() && System.nanoTime() < deadline) {
        TimeUnit.MILLISECONDS.sleep(10);
      }
      assertTrue(process.handle().isAlive(), Files.readString(fixture.root().resolve("publication.log")));
      assertTrue(Files.exists(output.resolve("resource-0")));
      assertFalse(Files.exists(fixture.root().resolve("target/compilation/production/success.properties")));
      try (var files = Files.list(output)) {
        assertTrue(files.count() < resourceCount, "The control must observe an incomplete output inventory");
      }
    }

    // Assert
    assertFalse(Files.exists(fixture.root().resolve("target/compilation/production/success.properties")));
    var recovered = nativeCompile(fixture);
    assertEquals(0, recovered.exitCode(), recovered.output());
    assertTrue(recovered.output().contains("no successful publication marker"), recovered.output());
    assertEquals("101", readSubject(fixture));
    try (var files = Files.list(output)) {
      assertEquals(List.of("Subject.class"), files.map(path -> path.getFileName().toString()).toList());
    }
    assertClosed(fixture.root());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  void givenAliasedCompilerArtifacts_whenNativePreparationRebuilds_thenUnrelatedBytesRemainAndRecoveryConsumesFreshClasses() throws Exception {
    // Arrange
    var fixture = fixture();
    var value = 109;
    for (var name : List.of("compiler.args", "compiler.log")) {
      for (var symbolic : IsolatedBuild.windows() ? List.of(false) : List.of(true, false)) {
        writeSubject(fixture, value);
        var state = Files.createDirectories(fixture.root().resolve("target/compilation/production"));
        var artifact = state.resolve(name);
        Files.deleteIfExists(artifact);
        var marker = fixture.root().resolve("unrelated-" + name + "-" + symbolic);
        Files.writeString(marker, "Unrelated compiler artifact remains intact.");
        if (symbolic) {
          Files.createSymbolicLink(artifact, marker);
        } else {
          Files.createLink(artifact, marker);
        }
        try {
          // Act
          var result = nativeCompile(fixture);

          // Assert
          assertEquals("Unrelated compiler artifact remains intact.", Files.readString(marker));
          if (symbolic) {
            assertNotEquals(0, result.exitCode(), result.output());
            assertTrue(result.output().contains("Preparation does not follow symbolic links"), result.output());
            assertFalse(Files.exists(state.resolve("success.properties")));
            Files.delete(artifact);
            var recovery = nativeCompile(fixture);
            assertEquals(0, recovery.exitCode(), recovery.output());
          } else {
            assertEquals(0, result.exitCode(), result.output());
            assertFalse(Files.isSameFile(artifact, marker));
          }
          assertEquals(Integer.toString(value), readSubject(fixture));
          assertClosed(fixture.root());
        } finally {
          if (Files.isSymbolicLink(artifact) || (Files.exists(artifact) && Files.isSameFile(artifact, marker))) {
            Files.delete(artifact);
          }
        }
        value += 2;
      }
    }
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.WINDOWS})
  void givenAliasedResourceStaging_whenEitherNativeBatchPrepares_thenItRefusesBeforeDeletingOrFilteringUnrelatedFiles() throws Exception {
    // Arrange
    var fixture = fixture();
    writeSubject(fixture, 103);
    for (var batch : List.of("production", "tests")) {
      for (var nested : List.of(false, true)) {
        var staging = fixture.root().resolve("target/resource-staging").resolve(batch);
        Files.createDirectories(staging.getParent());
        if (Files.exists(staging)) {
          PreparationFiles.clear(fixture.root(), staging);
          Files.delete(staging);
        }
        var outside = Files.createDirectory(fixture.root().resolve("unrelated-" + batch + "-" + nested));
        var marker = outside.resolve("preserved.txt");
        Files.writeString(marker, "unrelated content remains intact");
        var alias = nested ? Files.createDirectory(staging).resolve("alias") : staging;
        if (IsolatedBuild.windows()) {
          var linked = fixture.run(new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.toString(), outside.toString()));
          assertEquals(0, linked.exitCode(), linked.output());
        } else {
          Files.createSymbolicLink(alias, outside);
        }
        try {
          // Act
          var refused = fixture.run("production".equals(batch) ? "compile" : "test");

          // Assert
          assertNotEquals(0, refused.exitCode(), refused.output());
          var rootDiagnostic = IsolatedBuild.windows() ? "Preparation does not mutate aliased paths" : "Preparation does not follow symbolic links";
          assertTrue(refused.output().contains(nested ? "Preparation requires regular files and directories" : rootDiagnostic), refused.output());
          assertEquals("unrelated content remains intact", Files.readString(marker));
          assertClosed(fixture.root());
        } finally {
          Files.delete(alias);
        }
      }
    }
  }

  private static Path addCompilerGate(IsolatedBuild fixture) throws IOException {
    var classes = Files.createDirectories(fixture.root().resolve("processor"));
    var source = fixture.root().resolve("CompilationGate.java");
    Files.writeString(source, """
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.util.Set;
        import java.util.concurrent.TimeUnit;
        import javax.annotation.processing.AbstractProcessor;
        import javax.annotation.processing.RoundEnvironment;
        import javax.annotation.processing.SupportedAnnotationTypes;
        import javax.annotation.processing.SupportedSourceVersion;
        import javax.lang.model.SourceVersion;
        import javax.lang.model.element.TypeElement;
        @SupportedAnnotationTypes("*")
        @SupportedSourceVersion(SourceVersion.RELEASE_25)
        public class CompilationGate extends AbstractProcessor {
          public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            var gate = Path.of(System.getenv("COMPILATION_GATE"));
            try {
              Files.writeString(gate, "compiler reached real processor");
              while (!Files.exists(gate.resolveSibling("release"))) { TimeUnit.MILLISECONDS.sleep(10); }
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            return false;
          }
        }
        """);
    assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()));
    var pom = fixture.root().resolve("pom.xml");
    Files.writeString(pom, Files.readString(pom).replace("<argument>-d</argument>",
        "<argument>--processor-path</argument><argument>" + IsolatedBuild.xml(classes.toString())
            + "</argument><argument>-processor</argument><argument>CompilationGate</argument><argument>-proc:full</argument><argument>-d</argument>"));
    return fixture.root().resolve("compiler-ready");
  }

  private static ProcessHandle awaitCompiler(OwnedProcess process) throws Exception {
    var deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
    while (process.handle().isAlive() && System.nanoTime() < deadline) {
      try (var descendants = process.handle().descendants()) {
        var compiler = descendants.filter(child -> child.info().command().orElse("").matches(".*[/\\\\]javac(?:\\.exe)?")).findFirst();
        if (compiler.isPresent()) {
          return compiler.orElseThrow();
        }
      }
      TimeUnit.MILLISECONDS.sleep(10);
    }
    throw new IOException("The owned build did not expose an active real javac process");
  }

  private static IsolatedBuild.Result nativeCompile(IsolatedBuild fixture) throws Exception {
    return IsolatedBuild.windows() ? fixture.run(fixture.powerShell("compile")) : fixture.run("compile");
  }

  private static String readSubject(IsolatedBuild fixture) throws Exception {
    var result = fixture.run(new ProcessBuilder(IsolatedBuild.java().toString(), "-cp", fixture.root().resolve("target/classes").toString(), "Subject"));
    assertEquals(0, result.exitCode(), result.output());
    return result.output().strip();
  }

  private static void writeSubject(IsolatedBuild fixture, int value) throws IOException {
    Files.writeString(fixture.root().resolve("src/main/java/Subject.java"),
        "public class Subject { public static void main(String[] arguments) { System.out.println(" + value + "); } }");
  }

  private IsolatedBuild fixture() throws Exception {
    var fixture = new IsolatedBuild(directory.resolve("compilation # spaces"));
    Files.createDirectories(fixture.root().resolve("src/main/java"));
    fixture.writePom(fixture.executions("prepare-production-resources", "prepare-test-resources") + """
        <execution><id>prepare-compilation</id><phase>compile</phase><goals><goal>exec</goal></goals><configuration>
          <executable>@JAVA@</executable>
          <environmentVariables><AQUILA_BUILD_EFFECTIVE_GOALS>goals=${session.request.goals}</AQUILA_BUILD_EFFECTIVE_GOALS></environmentVariables>
          <arguments><argument>--enable-native-access=ALL-UNNAMED</argument>
            <argument>${project.basedir}/config/build/org/aquilagraph/build/CompilationPreparation.java</argument>
            <argument>${project.basedir}</argument><argument>production</argument>
            <argument>${project.basedir}/src/main/java</argument><argument>${project.build.directory}/resource-staging/production</argument>
            <argument>${project.build.outputDirectory}</argument><argument>false</argument>
            <argument>--release</argument><argument>25</argument><argument>-Xlint:all</argument><argument>-Werror</argument>
            <argument>-d</argument><argument>${project.build.outputDirectory}</argument><argument>${project.basedir}/src/main/java/Subject.java</argument>
          </arguments>
        </configuration></execution>
        """.replace("@JAVA@", IsolatedBuild.xml(IsolatedBuild.java().toString())), "", "", """
        <executions><execution><id>default-compile</id><phase>none</phase></execution></executions>
        """);
    var pom = fixture.root().resolve("pom.xml");
    var text = Files.readString(pom).replace("<properties>", "<properties><fixture.value>first</fixture.value>")
        .replace("<build><plugins>", "<build><resources><resource><directory>src/main/resources</directory><filtering>true</filtering></resource></resources><plugins>")
        .replace("<artifactId>maven-resources-plugin</artifactId><version>" + fixture.version("maven-resources-plugin.version") + "</version>",
            "<artifactId>maven-resources-plugin</artifactId><version>" + fixture.version("maven-resources-plugin.version")
                + "</version><configuration><outputDirectory>${project.build.directory}/resource-staging/production</outputDirectory></configuration>");
    Files.writeString(pom, text);
    return fixture;
  }
}
