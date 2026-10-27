package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilationPreparationTest {
  @TempDir Path directory;

  @AfterEach
  void closeCapturedInputs() throws IOException {
    PreparationFiles.clear(directory.toRealPath(), directory.resolve("target/compilation"));
  }

  @Test
  void givenPublishedClasses_whenUnchangedInputsArePreparedAgain_thenCompilerIsOmittedAndTheConsumerStillReadsTheValue() throws Exception {
    // Arrange
    var request = fixture("public class Subject { public static final int VALUE = 17; }");
    assertEquals(0, CompilationPreparation.prepare(request));
    var log = request.state().resolve("compiler.log");
    Files.writeString(log, "compiler was not invoked again");

    // Act
    var exit = CompilationPreparation.prepare(request);

    // Assert
    assertEquals(0, exit);
    assertEquals("compiler was not invoked again", Files.readString(log));
    assertEquals(17, value(request));
  }

  @Test
  void givenRenamedSourcesAndRemovedNestedTypes_whenPreparationRuns_thenOnlyCurrentClassesRemainAndOldClientsFail() throws Exception {
    // Arrange
    var request = fixture("public class Subject { public static class Nested {} }");
    assertEquals(0, CompilationPreparation.prepare(request));
    Files.delete(request.sources().resolve("Subject.java"));
    Files.writeString(request.sources().resolve("Current.java"), "public class Current {}");
    request = request(request, List.of("--release", "25", "-d", request.output().toString(), request.sources().resolve("Current.java").toString()), request.environment(), false);

    // Act
    var exit = CompilationPreparation.prepare(request);

    // Assert
    assertEquals(0, exit);
    assertEquals(Set.of("Current.class"), outputFiles(request));
    var client = directory.resolve("Client.java");
    Files.writeString(client, "class Client { Subject.Nested old; }");
    assertNotEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-cp", request.output().toString(), client.toString()));
  }

  @Test
  void givenCapturedResourceDirectory_whenReplacedByItsInventoryBytes_thenRootKindChangesIdentityAndReconciliationRefuses() throws Exception {
    // Arrange
    var request = fixture("public class Subject {}");
    Files.writeString(request.resources().resolve("entry.txt"), "payload");
    var inputs = new CompilationInputs(request);
    inputs.capture();
    var original = PreparationFiles.digest(request.resources());
    Files.delete(request.resources().resolve("entry.txt"));
    Files.delete(request.resources());

    // Act
    Files.writeString(request.resources(), "0:9:directory9:entry.txt4:file1:7payload");

    // Assert
    assertNotEquals(original, PreparationFiles.digest(request.resources()));
    assertThrows(IOException.class, inputs::requireUnchanged);
  }

  @Test
  void givenCorruptedPublishedStates_whenEachIsPrepared_thenRealCompilationRestoresTheCompleteInventory() throws Exception {
    // Arrange
    var request = fixture("public class Subject { public static final int VALUE = 23; }");
    assertEquals(0, CompilationPreparation.prepare(request));
    var expected = Files.readAllBytes(request.output().resolve("Subject.class"));

    // Act
    for (var fault : List.of("missing", "extra", "altered", "manifest", "input-manifest", "root-file", "root-file-record")) {
      switch (fault) {
        case "missing" -> Files.delete(request.output().resolve("Subject.class"));
        case "extra" -> Files.writeString(request.output().resolve("Obsolete.class"), "obsolete");
        case "altered" -> Files.writeString(request.output().resolve("Subject.class"), "invalid bytes");
        case "manifest" -> Files.writeString(request.state().resolve("success.properties"), "corrupt=\\uZZZZ");
        case "input-manifest" -> Files.delete(request.state().resolve("inputs.properties"));
        case "root-file" -> replaceSubjectOutput(request);
        case "root-file-record" -> {
          replaceSubjectOutput(request);
          var marker = request.state().resolve("success.properties");
          var record = BuildFiles.read(marker);
          record.setProperty("output.digest", PreparationFiles.digest(request.output()));
          BuildFiles.write(marker, record);
        }
        default -> throw new AssertionError(fault);
      }
      assertEquals(0, CompilationPreparation.prepare(request), fault);

      // Assert
      assertEquals(Set.of("Subject.class"), outputFiles(request), fault);
      assertArrayEquals(expected, Files.readAllBytes(request.output().resolve("Subject.class")), fault);
      assertEquals(23, value(request));
    }
  }

  @Test
  void givenPriorSuccess_whenCompilationFailsAndIsCorrected_thenFailureCannotPublishOrReuseAnOldSuccess() throws Exception {
    // Arrange
    var request = fixture("public class Subject { public static final int VALUE = 31; }");
    assertEquals(0, CompilationPreparation.prepare(request));
    Files.writeString(request.sources().resolve("Subject.java"), "public class Subject { syntax error }");

    // Act
    var failed = CompilationPreparation.prepare(request);

    // Assert
    assertNotEquals(0, failed);
    assertFalse(Files.exists(request.state().resolve("success.properties")));
    assertTrue(Files.readString(request.state().resolve("compiler.log")).contains("error:"));
    Files.writeString(request.sources().resolve("Subject.java"), "public class Subject { public static final int VALUE = 37; }");
    assertEquals(0, CompilationPreparation.prepare(request));
    assertEquals(37, value(request));
  }

  @Test
  void givenResourcesAndAClassNameCollision_whenResourcesChangeAndDisappear_thenOnlyEffectiveResourcesAndCompilerClassesArePublished() throws Exception {
    // Arrange
    var request = fixture("public class Subject { public static final int VALUE = 41; }");
    Files.writeString(request.resources().resolve("message.properties"), "value=first");
    Files.writeString(request.resources().resolve("Subject.class"), "resource must not override javac");
    assertEquals(0, CompilationPreparation.prepare(request));
    assertEquals(41, value(request));
    assertEquals("value=first", Files.readString(request.output().resolve("message.properties")));
    Files.delete(request.resources().resolve("message.properties"));
    Files.writeString(request.resources().resolve("replacement.properties"), "value=filtered-second");

    // Act
    var exit = CompilationPreparation.prepare(request);

    // Assert
    assertEquals(0, exit);
    assertEquals(Set.of("Subject.class", "replacement.properties"), outputFiles(request));
    assertEquals("value=filtered-second", Files.readString(request.output().resolve("replacement.properties")));
    assertEquals(41, value(request));
  }

  @Test
  void givenAcceptedCompilerEnvironment_whenItChanges_thenOutputChangesAndUnsupportedOptionsAreRejected() throws Exception {
    // Arrange
    var request = fixture("public class Subject { public void call(String named) {} }");
    assertEquals(0, CompilationPreparation.prepare(request));
    var original = Files.readAllBytes(request.output().resolve("Subject.class"));
    var environment = new HashMap<>(request.environment());
    environment.put("JDK_JAVAC_OPTIONS", "-parameters");
    var changed = request(request, request.arguments(), environment, false);

    // Act
    var exit = CompilationPreparation.prepare(changed);

    // Assert
    assertEquals(0, exit);
    assertFalse(Arrays.equals(original, Files.readAllBytes(request.output().resolve("Subject.class"))));
    try (var loader = loader(changed)) {
      assertEquals("named", loader.loadClass("Subject").getMethod("call", String.class).getParameters()[0].getName());
    }
    environment.put("JDK_JAVAC_OPTIONS", "@unmodeled.args");
    var unsupported = request(request, request.arguments(), environment, false);
    assertThrows(IllegalArgumentException.class, () -> CompilationPreparation.prepare(unsupported));
  }

  @Test
  void givenSamePathJarAndDirectoryDependencies_whenTheirContentsChange_thenTheConsumerObservesEachNewConstant() throws Exception {
    // Arrange
    var request = fixture("public class Subject { public static final int VALUE = dependency.Value.NUMBER; }");
    var dependencies = Files.createDirectories(directory.resolve("dependencies"));
    var jar = directory.resolve("dependency.jar");
    compileDependency(dependencies, 43);
    writeJar(dependencies, jar);
    var arguments = new ArrayList<>(request.arguments());
    arguments.addAll(List.of("--class-path", jar.toString()));
    var jarRequest = request(request, arguments, request.environment(), false);
    assertEquals(0, CompilationPreparation.prepare(jarRequest));
    assertEquals(43, value(jarRequest));
    compileDependency(dependencies, 47);
    writeJar(dependencies, jar);

    // Act
    assertEquals(0, CompilationPreparation.prepare(jarRequest));
    var jarValue = value(jarRequest);
    arguments.set(arguments.size() - 1, dependencies.toString());
    var directoryRequest = request(request, arguments, request.environment(), false);
    assertEquals(0, CompilationPreparation.prepare(directoryRequest));
    compileDependency(dependencies, 53);
    assertEquals(0, CompilationPreparation.prepare(directoryRequest));

    // Assert
    assertEquals(47, jarValue);
    assertEquals(53, value(directoryRequest));
  }

  @Test
  void givenHardLinkedPreparationFiles_whenEitherBatchCompiles_thenUnrelatedBytesRemainAndFreshOutputsAreConsumed() throws Exception {
    // Arrange
    var production = fixture("public class Subject { public static final int VALUE = 109; }");
    var sources = Files.createDirectories(directory.resolve("src/test/java"));
    var source = sources.resolve("TestSubject.java");
    Files.writeString(source, "public class TestSubject { public static final int VALUE = Subject.VALUE; }");
    var resources = Files.createDirectories(directory.resolve("target/resource-staging/tests"));
    var output = directory.resolve("target/test-classes");
    var tests = new CompilationRequest(production.root(), "tests", sources, resources, output, false,
        List.of("--release", "25", "--class-path", production.output().toString(), "-d", output.toString(), source.toString()), production.environment());
    for (var request : List.of(production, tests)) {
      Files.createDirectories(request.state());
      var markers = new HashMap<Path, String>();
      for (var name : List.of("compiler.args", "compiler.log", "provenance.properties", "inputs.properties", "success.properties")) {
        var marker = directory.resolve(request.batch() + "-unrelated-" + name);
        var content = "Unrelated content for " + request.batch() + " " + name;
        Files.writeString(marker, content);
        Files.createLink(request.state().resolve(name), marker);
        markers.put(marker, content);
      }

      // Act
      var exit = CompilationPreparation.prepare(request);

      // Assert
      assertEquals(0, exit);
      for (var marker : markers.entrySet()) {
        assertEquals(marker.getValue(), Files.readString(marker.getKey()));
      }
      try (var loader = loader(request)) {
        assertEquals(109, loader.loadClass(request == production ? "Subject" : "TestSubject").getField("VALUE").getInt(null));
      }
    }
  }

  @Test
  void givenVerifiedOutput_whenForcedPreparationRuns_thenTheSameCompilerPathRunsAgain() throws Exception {
    // Arrange
    var request = fixture("public class Subject { public static final int VALUE = 59; }");
    assertEquals(0, CompilationPreparation.prepare(request));
    Files.writeString(request.state().resolve("compiler.log"), "old compiler observation");
    var forced = request(request, request.arguments(), request.environment(), true);

    // Act
    var exit = CompilationPreparation.prepare(forced);

    // Assert
    assertEquals(0, exit);
    assertNotEquals("old compiler observation", Files.readString(request.state().resolve("compiler.log")));
    assertEquals(59, value(request));
  }

  @Test
  void givenTestOutputsBasedOnProduction_whenProductionChanges_thenTestsRecompileAgainstTheExactPublishedClasses() throws Exception {
    // Arrange
    var production = fixture("public class Subject { public static final int VALUE = 61; }");
    assertEquals(0, CompilationPreparation.prepare(production));
    var sources = Files.createDirectories(directory.resolve("src/test/java"));
    var test = sources.resolve("TestSubject.java");
    Files.writeString(test, "public class TestSubject { public static final int VALUE = Subject.VALUE; }");
    var resources = Files.createDirectories(directory.resolve("target/resource-staging/tests"));
    var output = directory.resolve("target/test-classes");
    var tests = new CompilationRequest(production.root(), "tests", sources, resources, output, false,
        List.of("--release", "25", "--class-path", production.output().toString(), "-d", output.toString(), test.toString()), production.environment());
    assertEquals(0, CompilationPreparation.prepare(tests));
    Files.writeString(production.sources().resolve("Subject.java"), "public class Subject { public static final int VALUE = 67; }");

    // Act
    assertEquals(0, CompilationPreparation.prepare(production));
    var exit = CompilationPreparation.prepare(tests);

    // Assert
    assertEquals(0, exit);
    try (var loader = loader(tests)) {
      assertEquals(67, loader.loadClass("TestSubject").getField("VALUE").getInt(null));
    }
    Files.writeString(production.output().resolve("Subject.class"), "corrupt production");
    assertThrows(IOException.class, () -> CompilationPreparation.prepare(tests));
    assertEquals(0, CompilationPreparation.prepare(production));
    replaceSubjectOutput(production);
    var marker = production.state().resolve("success.properties");
    var record = BuildFiles.read(marker);
    record.setProperty("output.digest", PreparationFiles.digest(production.output()));
    BuildFiles.write(marker, record);
    assertThrows(IOException.class, () -> CompilationPreparation.prepare(tests));
    assertEquals(0, CompilationPreparation.prepare(production));
    assertEquals(0, CompilationPreparation.prepare(tests));
    assertEquals(67, value(production));
    try (var loader = loader(tests)) {
      assertEquals(67, loader.loadClass("TestSubject").getField("VALUE").getInt(null));
    }
  }

  @Test
  void givenAnExistingCompiledCaller_whenTheProductionSignatureChanges_thenTheOldCallerFailsAndTheMigratedCallerCompiles() throws Exception {
    // Arrange
    var production = fixture("public class Subject { public static int value() { return 1; } }");
    assertEquals(0, CompilationPreparation.prepare(production));
    var sources = Files.createDirectories(directory.resolve("src/test/java"));
    var test = sources.resolve("Caller.java");
    Files.writeString(test, "class Caller { int read() { return Subject.value(); } }");
    var resources = Files.createDirectories(directory.resolve("target/resource-staging/tests"));
    var output = directory.resolve("target/test-classes");
    var tests = new CompilationRequest(production.root(), "tests", sources, resources, output, false,
        List.of("--release", "25", "--class-path", production.output().toString(), "-d", output.toString(), test.toString()), production.environment());
    assertEquals(0, CompilationPreparation.prepare(tests));
    Files.writeString(production.sources().resolve("Subject.java"), "public class Subject { public static String value() { return \"changed\"; } }");

    // Act
    assertEquals(0, CompilationPreparation.prepare(production));
    var failed = CompilationPreparation.prepare(tests);

    // Assert
    assertNotEquals(0, failed);
    assertFalse(Files.exists(tests.state().resolve("success.properties")));
    assertTrue(Files.readString(tests.state().resolve("compiler.log")).contains("incompatible types"));
    Files.writeString(test, "class Caller { String read() { return Subject.value(); } }");
    assertEquals(0, CompilationPreparation.prepare(tests));
    assertTrue(Files.isRegularFile(output.resolve("Caller.class")));
  }

  @Test
  void givenCapturedSourceBytes_whenTheCheckoutChanges_thenJavacReadsTheCaptureAndPublicationIsRefused() throws Exception {
    // Arrange
    var request = fixture("public class Subject { public static final int VALUE = 71; }");
    var inputs = new CompilationInputs(request);
    inputs.capture();
    PreparationFiles.clear(request.root(), request.state().resolve("output"));
    Files.writeString(request.sources().resolve("Subject.java"), "public class Subject { syntax error }");

    // Act
    var exit = CompilationCompiler.execute(request, inputs);
    var mismatch = assertThrows(IOException.class, inputs::requireUnchanged);

    // Assert
    assertEquals(0, exit);
    assertTrue(Files.isRegularFile(request.state().resolve("output/Subject.class")));
    assertTrue(mismatch.getMessage().contains("inputs changed"));
    assertFalse(Files.exists(request.state().resolve("success.properties")));
    assertEquals("public class Subject { public static final int VALUE = 71; }", Files.readString(request.state().resolve("capture/sources/Subject.java")));
  }

  @Test
  void givenARealGeneratingProcessor_whenItsSamePathImplementationChanges_thenGeneratedConsumerValuesChange() throws Exception {
    // Arrange
    var request = fixture("public class Subject {}");
    var processor = Files.createDirectories(directory.resolve("processor"));
    compileProcessor(processor, 73);
    var arguments = new ArrayList<>(request.arguments());
    arguments.addAll(List.of("--processor-path", processor.toString(), "-processor", "Generator", "-proc:full"));
    var generated = request(request, arguments, request.environment(), false);
    assertEquals(0, CompilationPreparation.prepare(generated));
    try (var loader = loader(generated)) {
      assertEquals(73, loader.loadClass("Generated").getField("VERSION").getInt(null));
    }
    compileProcessor(processor, 79);

    // Act
    var exit = CompilationPreparation.prepare(generated);

    // Assert
    assertEquals(0, exit);
    try (var loader = loader(generated)) {
      assertEquals(79, loader.loadClass("Generated").getField("VERSION").getInt(null));
    }
  }

  @Test
  void givenProductionAndTestOutputs_whenOnlyNativeInvocationMetadataChanges_thenBothBatchesReuseTheirExactPublishedValues() throws Exception {
    // Arrange
    var base = fixture("public class Subject { public static final int VALUE = 103; }");
    var first = new HashMap<>(base.environment());
    first.put("INVOCATION_ID", "first-native-invocation");
    var production = request(base, base.arguments(), first, false);
    assertEquals(0, CompilationPreparation.prepare(production));
    var sources = Files.createDirectories(directory.resolve("src/test/java"));
    var test = sources.resolve("TestSubject.java");
    Files.writeString(test, "public class TestSubject { public static final int VALUE = Subject.VALUE; }");
    var resources = Files.createDirectories(directory.resolve("target/resource-staging/tests"));
    var output = directory.resolve("target/test-classes");
    var tests = new CompilationRequest(production.root(), "tests", sources, resources, output, false,
        List.of("--release", "25", "--class-path", production.output().toString(), "-d", output.toString(), test.toString()), first);
    assertEquals(0, CompilationPreparation.prepare(tests));
    Files.writeString(production.state().resolve("compiler.log"), "production compiler omitted");
    Files.writeString(tests.state().resolve("compiler.log"), "test compiler omitted");
    var second = new HashMap<>(first);
    second.put("INVOCATION_ID", "second-native-invocation");

    // Act
    assertEquals(0, CompilationPreparation.prepare(request(production, production.arguments(), second, false)));
    assertEquals(0, CompilationPreparation.prepare(request(tests, tests.arguments(), second, false)));

    // Assert
    assertEquals("production compiler omitted", Files.readString(production.state().resolve("compiler.log")));
    assertEquals("test compiler omitted", Files.readString(tests.state().resolve("compiler.log")));
    assertEquals(103, value(production));
    try (var loader = loader(tests)) {
      assertEquals(103, loader.loadClass("TestSubject").getField("VALUE").getInt(null));
    }
  }

  private void compileProcessor(Path output, int version) throws IOException {
    var source = directory.resolve("Generator.java");
    Files.writeString(source, """
        import java.util.Set;
        import java.io.IOException;
        import javax.annotation.processing.AbstractProcessor;
        import javax.annotation.processing.RoundEnvironment;
        import javax.annotation.processing.SupportedAnnotationTypes;
        import javax.annotation.processing.SupportedSourceVersion;
        import javax.lang.model.SourceVersion;
        import javax.lang.model.element.TypeElement;
        @SupportedAnnotationTypes("*")
        @SupportedSourceVersion(SourceVersion.RELEASE_25)
        public class Generator extends AbstractProcessor {
          private boolean emitted;
          public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (System.getenv("INVOCATION_ID") != null) { throw new IllegalStateException("Compiler received native invocation metadata"); }
            if (!emitted) {
              emitted = true;
              try (var writer = processingEnv.getFiler().createSourceFile("Generated").openWriter()) {
                writer.write("public class Generated { public static final int VERSION = %d; }");
              } catch (IOException failure) { throw new IllegalStateException(failure); }
            }
            return false;
          }
        }
        """.formatted(version));
    assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", output.toString(), source.toString()));
  }

  private CompilationRequest fixture(String source) throws IOException {
    var root = directory.toRealPath();
    var sources = Files.createDirectories(root.resolve("src/main/java"));
    var resources = Files.createDirectories(root.resolve("target/resource-staging/production"));
    var file = sources.resolve("Subject.java");
    Files.writeString(file, source);
    var output = root.resolve("target/classes");
    var environment = new HashMap<>(System.getenv());
    environment.remove("CLASSPATH");
    return new CompilationRequest(root, "production", sources, resources, output, false,
        List.of("--release", "25", "-d", output.toString(), file.toString()), environment);
  }

  private static CompilationRequest request(CompilationRequest base, List<String> arguments, Map<String, String> environment, boolean force) {
    return new CompilationRequest(base.root(), base.batch(), base.sources(), base.resources(), base.output(), force, arguments, environment);
  }

  private static Set<String> outputFiles(CompilationRequest request) throws IOException {
    try (var paths = Files.walk(request.output())) {
      return paths.filter(Files::isRegularFile).map(path -> request.output().relativize(path).toString().replace('\\', '/')).collect(Collectors.toSet());
    }
  }

  private static void replaceSubjectOutput(CompilationRequest request) throws IOException {
    var file = request.output().resolve("Subject.class");
    var content = Files.readAllBytes(file);
    var size = Integer.toString(content.length);
    var serialized = new ByteArrayOutputStream();
    serialized.writeBytes(("0:9:directory13:Subject.class4:file" + size.length() + ":" + size).getBytes(StandardCharsets.UTF_8));
    serialized.writeBytes(content);
    Files.delete(file);
    Files.delete(request.output());
    Files.write(request.output(), serialized.toByteArray());
  }

  private static URLClassLoader loader(CompilationRequest request) throws Exception {
    return new URLClassLoader(new URL[] {request.output().toUri().toURL()}, ClassLoader.getPlatformClassLoader());
  }

  private static int value(CompilationRequest request) throws Exception {
    try (var loader = loader(request)) {
      return loader.loadClass("Subject").getField("VALUE").getInt(null);
    }
  }

  private void compileDependency(Path output, int number) throws IOException {
    var source = directory.resolve("Value.java");
    Files.writeString(source, "package dependency; public class Value { public static final int NUMBER = " + number + "; }");
    assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", output.toString(), source.toString()));
  }

  private static void writeJar(Path classes, Path jar) throws IOException {
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("dependency/Value.class"));
      Files.copy(classes.resolve("dependency/Value.class"), output);
      output.closeEntry();
    }
  }
}
