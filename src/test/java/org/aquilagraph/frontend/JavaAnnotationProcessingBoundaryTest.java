package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.junit.jupiter.api.Test;

class JavaAnnotationProcessingBoundaryTest {
  private static final String PROCESSOR = "probe.BoundaryProcessor";
  private static final String LIFECYCLE_PROPERTY = "aquilagraph.processor.lifecycle";

  @Test
  void givenDiscoverableAndExplicitProcessors_whenAnalyzed_thenNeitherClassLoadingNorLifecycleRuns() throws IOException {
    // Arrange
    var workspace = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "annotation-processor-");
    var processorJar = processorJar(workspace);
    var input = Files.createDirectory(workspace.resolve("input"));
    Files.writeString(input.resolve("Input.java"), "@Deprecated class Input {}\n");
    try (var jar = new JarFile(processorJar.toFile())) {
      var service = jar.getJarEntry("META-INF/services/javax.annotation.processing.Processor");
      assertTrue(service != null);
      assertEquals(PROCESSOR + "\n", new String(jar.getInputStream(service).readAllBytes(), StandardCharsets.UTF_8));
      assertTrue(jar.getJarEntry("probe/BoundaryProcessor.class") != null);
    }
    var discoverable = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(processorJar), List.of(), List.of());
    var explicit = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(processorJar), List.of(),
        List.of("-processor", PROCESSOR));
    assertTrue(JavaCompilerInvocation.from(discoverable).compilerOptions().contains("-proc:none"));
    assertTrue(JavaCompilerInvocation.from(discoverable).analysisOptions().contains("-proc:none"));
    assertTrue(JavaCompilerInvocation.from(explicit).compilerOptions().contains("-proc:none"));
    assertTrue(JavaCompilerInvocation.from(explicit).analysisOptions().contains("-proc:none"));
    System.clearProperty(LIFECYCLE_PROPERTY);

    // Act
    var discoverableResult = observeAnalysis(workspace.resolve("discoverable.jfr"), input, discoverable);
    var explicitResult = observeAnalysis(workspace.resolve("explicit.jfr"), input, explicit);

    // Assert
    assertTrue(discoverableResult.loadedClasses().isEmpty(), discoverableResult.loadedClasses()::toString);
    assertTrue(explicitResult.loadedClasses().isEmpty(), explicitResult.loadedClasses()::toString);
    assertNull(System.getProperty(LIFECYCLE_PROPERTY));
    assertFalse(Files.exists(workspace.resolve("GeneratedByProbe.java")));
    assertTrue(discoverableResult.result().program().types().stream().noneMatch(type -> type.qualifiedName().contains("GeneratedByProbe")));
    assertTrue(explicitResult.result().program().types().stream().noneMatch(type -> type.qualifiedName().contains("GeneratedByProbe")));
    assertTrue(discoverableResult.result().diagnostics().isEmpty(), discoverableResult.result().diagnostics()::toString);
    assertTrue(explicitResult.result().diagnostics().isEmpty(), explicitResult.result().diagnostics()::toString);
    try (var loader = new URLClassLoader(new URL[] {processorJar.toUri().toURL()}, getClass().getClassLoader());
        var recording = new Recording()) {
      recording.enable("jdk.ClassLoad");
      recording.start();
      try {
        Class.forName(PROCESSOR, false, loader);
      } catch (ClassNotFoundException failure) {
        throw new AssertionError(failure);
      }
      recording.stop();
      var control = workspace.resolve("control.jfr");
      recording.dump(control);
      assertFalse(loadedProcessorClasses(control).isEmpty(), "The loading probe must observe a known processor class load");
    }
    assertNull(System.getProperty(LIFECYCLE_PROPERTY));
  }

  private static ObservedAnalysis observeAnalysis(Path recordingPath, Path input, CompilerConfiguration configuration) throws IOException {
    try (var recording = new Recording()) {
      recording.enable("jdk.ClassLoad");
      recording.start();
      var result = new JavaFrontend(new JavaSourceLoader()).analyze(input, configuration);
      recording.stop();
      recording.dump(recordingPath);
      return new ObservedAnalysis(result, loadedProcessorClasses(recordingPath));
    }
  }

  private static List<String> loadedProcessorClasses(Path recordingPath) throws IOException {
    return RecordingFile.readAllEvents(recordingPath).stream().filter(event -> event.getEventType().getName().equals("jdk.ClassLoad"))
        .map(event -> event.getClass("loadedClass").getName()).filter(PROCESSOR::equals).toList();
  }

  private static Path processorJar(Path workspace) throws IOException {
    var sourceDirectory = Files.createDirectories(workspace.resolve("processor-source/probe"));
    var classes = Files.createDirectory(workspace.resolve("processor-classes"));
    var source = sourceDirectory.resolve("BoundaryProcessor.java");
    Files.writeString(source, """
        package probe;
        import java.util.Set;
        import java.io.IOException;
        import javax.annotation.processing.AbstractProcessor;
        import javax.annotation.processing.ProcessingEnvironment;
        import javax.annotation.processing.RoundEnvironment;
        import javax.annotation.processing.SupportedAnnotationTypes;
        import javax.annotation.processing.SupportedSourceVersion;
        import javax.lang.model.SourceVersion;
        import javax.lang.model.element.TypeElement;
        @SupportedAnnotationTypes("*")
        @SupportedSourceVersion(SourceVersion.RELEASE_25)
        public final class BoundaryProcessor extends AbstractProcessor {
          static { System.setProperty("aquilagraph.processor.lifecycle", "initialized"); }
          public BoundaryProcessor() { System.setProperty("aquilagraph.processor.lifecycle", "constructed"); }
          @Override public synchronized void init(ProcessingEnvironment environment) {
            super.init(environment);
            System.setProperty("aquilagraph.processor.lifecycle", "init");
          }
          @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            System.setProperty("aquilagraph.processor.lifecycle", "process");
            try {
              processingEnv.getFiler().createSourceFile("GeneratedByProbe").openWriter().close();
            } catch (IOException failure) {
              throw new IllegalStateException(failure);
            }
            return false;
          }
        }
        """);
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertEquals(0, compiler.run(null, null, null, "-proc:none", "-d", classes.toString(), source.toString()));
    var jarPath = workspace.resolve("processor.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
      jar.putNextEntry(new JarEntry("probe/BoundaryProcessor.class"));
      jar.write(Files.readAllBytes(classes.resolve("probe/BoundaryProcessor.class")));
      jar.closeEntry();
      jar.putNextEntry(new JarEntry("META-INF/services/javax.annotation.processing.Processor"));
      jar.write((PROCESSOR + "\n").getBytes(StandardCharsets.UTF_8));
      jar.closeEntry();
    }
    return jarPath;
  }

  private record ObservedAnalysis(SyntaxAnalysisInput result, List<String> loadedClasses) {
  }
}
