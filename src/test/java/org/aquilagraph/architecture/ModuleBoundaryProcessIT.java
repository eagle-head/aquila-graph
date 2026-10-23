package org.aquilagraph.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleBoundaryProcessIT {
  private static final String CONSUMER = "org.aquilagraph.consumer";
  private static final Path DISTRIBUTION = Path.of("target/aquila-graph/lib").toAbsolutePath();
  private static final List<String> READERS = List.of("org.aquilagraph.source", "org.aquilagraph.cfg");
  @TempDir Path directory;

  @Test
  void givenDeliveredProductJars_whenInspectingOwnership_thenDescriptorsExportsAndTheDependencyDagAreExact() throws Exception {
    // Arrange
    var dependencies = Map.of(
        "identity", Set.<String>of(),
        "source", Set.of("identity"),
        "cfg", Set.of("identity", "source"),
        "frontend", Set.of("identity", "source"),
        "application", Set.of("identity", "source", "cfg"),
        "repository", Set.of("identity", "source", "cfg", "application"),
        "cli", Set.of("identity", "source", "cfg", "frontend", "application", "repository"));
    var packageOwners = new HashMap<String, String>();
    var descriptors = new HashMap<String, ModuleDescriptor>();

    // Act
    try (var files = Files.list(DISTRIBUTION)) {
      var jars = files.filter(path -> path.getFileName().toString().startsWith("aquila-graph-")).toList();
      assertEquals(7, jars.size());
      for (var path : jars) {
        try (var jar = new JarFile(path.toFile()); var input = jar.getInputStream(jar.getJarEntry("module-info.class"))) {
          var descriptor = ModuleDescriptor.read(input);
          descriptors.put(descriptor.name(), descriptor);
          assertFalse(descriptor.isAutomatic(), path.toString());
          for (var name : descriptor.packages()) {
            assertEquals(null, packageOwners.putIfAbsent(name, descriptor.name()), "Split package: " + name);
          }
        }
      }
    }

    // Assert
    for (var entry : dependencies.entrySet()) {
      var descriptor = descriptors.get("org.aquilagraph." + entry.getKey());
      assertTrue(descriptor.opens().isEmpty(), descriptor.name());
      var actual = descriptor.requires().stream().map(ModuleDescriptor.Requires::name)
          .filter(name -> name.startsWith("org.aquilagraph.")).map(name -> name.substring("org.aquilagraph.".length()))
          .collect(Collectors.toSet());
      assertEquals(entry.getValue(), actual, descriptor.name());
    }
    var source = descriptors.get("org.aquilagraph.source");
    assertEquals(Set.of("org.aquilagraph.source.api"), source.exports().stream().filter(value -> !value.isQualified())
        .map(ModuleDescriptor.Exports::source).collect(Collectors.toSet()));
    assertEquals(Set.of("org.aquilagraph.frontend"), source.exports().stream()
        .filter(value -> value.source().equals("org.aquilagraph.source.admission")).findFirst().orElseThrow().targets());
    assertEquals(Set.of("org.aquilagraph.repository"), source.exports().stream()
        .filter(value -> value.source().equals("org.aquilagraph.source.retention")).findFirst().orElseThrow().targets());
    assertEquals(Set.of("org.aquilagraph.frontend"), descriptors.get("org.aquilagraph.frontend").exports().stream()
        .map(ModuleDescriptor.Exports::source).collect(Collectors.toSet()));
    assertEquals(Set.of("org.aquilagraph.repository.neo4j"), descriptors.get("org.aquilagraph.repository").exports().stream()
        .map(ModuleDescriptor.Exports::source).collect(Collectors.toSet()));
    assertTrue(descriptors.get("org.aquilagraph.cli").exports().isEmpty());
  }

  @Test
  void givenAnOrdinaryModule_whenImportingConstructionOrImplementingReadRoots_thenAccessAndSealingRejectTheExactBoundary() throws Exception {
    // Arrange
    var positive = "import org.aquilagraph.source.api.AstResult; import org.aquilagraph.cfg.api.ControlFlowGraph; "
        + "public final class Client { public int read(AstResult ast, ControlFlowGraph graph) { "
        + "return ast.aggregate().nodes().size() + graph.nodes().size(); } }";
    var forbidden = List.of("org.aquilagraph.source.admission.SyntaxAnalysisInput", "org.aquilagraph.source.retention.AstCodec",
        "org.aquilagraph.source.internal.AdmittedAstResult", "org.aquilagraph.cfg.internal.ControlFlowGraphBuilder",
        "org.aquilagraph.frontend.internal.JavaFrontend", "org.aquilagraph.repository.internal.neo4j.Neo4jAnalysisRunRepository");
    var requirements = List.of("org.aquilagraph.source", "org.aquilagraph.cfg", "org.aquilagraph.frontend", "org.aquilagraph.repository");

    for (var type : forbidden) {
      // Act
      var control = compile(requirements, positive);
      var denied = compile(requirements, "import " + type + "; public final class Client { "
          + type.substring(type.lastIndexOf('.') + 1) + " value; }");
      // Assert
      assertEquals(0, control.exitCode(), control.diagnostics());
      assertTrue(denied.exitCode() != 0, type);
      assertTrue(denied.diagnostics().contains("does not export"), denied.diagnostics());
    }
    for (var type : List.of("org.aquilagraph.source.api.AstOutcome", "org.aquilagraph.source.api.AstResult",
        "org.aquilagraph.source.api.JavaAstAggregate", "org.aquilagraph.source.api.LanguageFeatureCoverageResult",
        "org.aquilagraph.source.api.ExecutionSyntax", "org.aquilagraph.source.api.MethodSyntax", "org.aquilagraph.cfg.api.ControlFlowGraph")) {
      // Act
      var control = compile(READERS, positive);
      var denied = compile(READERS, "import " + type + "; public abstract class Client implements " + type.substring(type.lastIndexOf('.') + 1) + " {}");
      // Assert
      assertEquals(0, control.exitCode(), control.diagnostics());
      assertTrue(denied.exitCode() != 0, type);
      assertTrue(denied.diagnostics().contains("permits"), denied.diagnostics());
      assertFalse(denied.diagnostics().contains("does not export"), denied.diagnostics());
    }
  }

  @Test
  void givenRealProductionModules_whenAnExternalConsumerReadsThenCloses_thenAdmittedSourceViewsAndGraphsWorkWithoutConstructionAccess()
      throws Exception {
    // Arrange
    var client = compile(List.of("org.aquilagraph.source", "org.aquilagraph.cfg", "org.aquilagraph.frontend"), """
        import java.nio.file.Path;
        import java.util.List;
        import org.aquilagraph.frontend.JavaFrontends;
        import org.aquilagraph.cfg.CfgDerivers;
        import org.aquilagraph.cfg.api.CfgProfile;
        import org.aquilagraph.cfg.api.CfgRequest;
        import org.aquilagraph.identity.api.CompilerConfiguration;
        import org.aquilagraph.identity.api.InputSnapshot;
        import org.aquilagraph.identity.api.JavaRelease;
        import org.aquilagraph.identity.api.SourceFile;
        import org.aquilagraph.source.api.AstProductionRequest;
        import org.aquilagraph.source.api.AstProfile;
        public final class Client {
          public static void main(String[] arguments) {
            for (var profile : List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_MODULE_SERVICES)) {
              var input = InputSnapshot.fromSources(Path.of("captured"), List.of(SourceFile.fromContent(Path.of("Example.java"),
                  "class Example { int value() { return 1; } }")));
              var ast = JavaFrontends.standard().produce(new AstProductionRequest(input,
                  CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)).result().orElseThrow();
              var method = ast.program().methods().stream().filter(value -> value.name().equals("value")).findFirst().orElseThrow();
              var body = ast.body(method.methodKey()).orElseThrow();
              var execution = ast.aggregate().executionSyntax(method.methodKey()).orElseThrow();
              if (body.source() != ast.aggregate() || execution.source() != ast.aggregate() || !body.equals(execution)
                  || body.hashCode() != execution.hashCode() || body.root() != execution.root()
                  || ast.node(ast.occurrence(body.root()).orElseThrow()).orElseThrow() != body.root()
                  || !ast.coverage().validationEvidence().writtenInventory().snapshot().equals(input)) {
                throw new IllegalStateException("Admitted source ownership changed across the public query boundary");
              }
              var graph = CfgDerivers.standard().derive(ast, new CfgRequest(ast.identity(), List.of(method.methodKey()),
                  CfgProfile.JAVA_BOUNDED)).methods().getFirst().graph().orElseThrow();
              if (!graph.identity().astIdentity().equals(ast.identity()) || !graph.body().equals(ast.bodyRef(method.methodKey()).orElseThrow())
                  || graph.operationNodes().size() != 2 || graph.operationNodes().stream().anyMatch(node -> !node.sourceRef().body().equals(graph.body()))) {
                throw new IllegalStateException("The external consumer did not receive the complete admitted graph");
              }
            }
            System.out.println("external-production-ok");
          }
        }
        """);
    // Act
    var result = run(client, List.of());
    // Assert
    assertEquals(0, client.exitCode(), client.diagnostics());
    assertEquals(0, result.exitCode(), result.output());
    assertEquals("external-production-ok", result.output().trim());
  }

  @Test
  void givenACompilerFreeReaderModule_whenReflectingOnControlledState_thenPublicValuesWorkAndInternalConstructionRemainsClosed()
      throws Exception {
    // Arrange
    var client = compile(READERS, """
        import java.lang.reflect.InaccessibleObjectException;
        import java.nio.file.Path;
        import java.util.List;
        import org.aquilagraph.identity.api.SourceFile;
        public final class Client {
          public static void main(String[] arguments) throws Exception {
            if (ModuleLayer.boot().findModule("org.aquilagraph.frontend").isPresent()
                || ModuleLayer.boot().findModule("jdk.compiler").isPresent() || ModuleLayer.boot().findModule("java.compiler").isPresent()) {
              throw new IllegalStateException("Reader resolution acquired the compiler");
            }
            if (!SourceFile.fromContent(Path.of("Example.java"), "class Example {}").content().equals("class Example {}")
                || !SourceFile.class.getDeclaredConstructors()[0].trySetAccessible()) {
              throw new IllegalStateException("Public leaf access is unavailable");
            }
            for (var name : List.of("org.aquilagraph.source.internal.AdmittedAstOutcome", "org.aquilagraph.source.internal.AdmittedAstResult",
                "org.aquilagraph.source.internal.AdmittedJavaAstAggregate", "org.aquilagraph.source.internal.AdmittedLanguageFeatureCoverageResult",
                "org.aquilagraph.source.internal.AdmittedExecutionSyntax", "org.aquilagraph.source.internal.AdmittedMethodSyntax",
                "org.aquilagraph.cfg.internal.AdmittedControlFlowGraph")) {
              try {
                Class.forName(name).getDeclaredConstructors()[0].setAccessible(true);
                throw new IllegalStateException("Internal construction became reflectively accessible: " + name);
              } catch (InaccessibleObjectException expected) {
                if (!expected.getMessage().contains("org.aquilagraph")) {
                  throw expected;
                }
              }
            }
            System.out.println("closed-reader-ok");
          }
        }
        """);
    // Act
    var result = run(client, List.of("--limit-modules", "java.base,org.aquilagraph.identity,org.aquilagraph.source,org.aquilagraph.cfg," + CONSUMER));
    // Assert
    assertEquals(0, client.exitCode(), client.diagnostics());
    assertEquals(0, result.exitCode(), result.output());
    assertEquals("closed-reader-ok", result.output().trim());
  }

  private Compilation compile(List<String> requirements, String source) throws Exception {
    var input = Files.createTempDirectory(directory, "module-consumer-");
    var file = Files.createDirectories(input.resolve("org/aquilagraph/consumer")).resolve("Client.java");
    Files.writeString(file, "package " + CONSUMER + ";\n" + source);
    var descriptor = input.resolve("module-info.java");
    Files.writeString(descriptor, "module " + CONSUMER + " { "
        + requirements.stream().map(name -> "requires " + name + ";").collect(Collectors.joining(" ")) + " }");
    var classes = Files.createDirectory(input.resolve("classes"));
    var annotations = Path.of(NullMarked.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var diagnostics = new ByteArrayOutputStream();
    var code = ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics, "--release", "25", "-Xlint:all", "-Werror",
        "--module-path", DISTRIBUTION + File.pathSeparator + annotations, "--class-path", classes.toString(), "-d", classes.toString(), descriptor.toString(), file.toString());
    return new Compilation(code, diagnostics.toString(StandardCharsets.UTF_8), classes);
  }

  private ProcessResult run(Compilation client, List<String> options) throws Exception {
    assertEquals(0, client.exitCode(), client.diagnostics());
    var executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    var command = new ArrayList<>(List.of(executable.toString(), "-Xmx512m"));
    command.addAll(options);
    command.addAll(List.of("--module-path", DISTRIBUTION + File.pathSeparator + client.classes(), "--module", CONSUMER + "/" + CONSUMER + ".Client"));
    var output = client.classes().resolveSibling("process-output.txt");
    var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(process.waitFor(45, TimeUnit.SECONDS), "External module process did not finish");
      return new ProcessResult(process.exitValue(), Files.readString(output));
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly().waitFor();
      }
    }
  }

  private record Compilation(int exitCode, String diagnostics, Path classes) {}
  private record ProcessResult(int exitCode, String output) {}
}
