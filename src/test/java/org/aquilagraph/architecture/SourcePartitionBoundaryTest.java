package org.aquilagraph.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

class SourcePartitionBoundaryTest {
  private static final List<String> ADMISSION_TYPES = List.of("AstAggregateAdmission", "JavaAstAggregateBuilder",
      "AttributedDeclarationPaths", "ExecutionDecisionRegistration", "ExecutionDecisionValidation");
  private static final Set<String> NULLNESS_METADATA = Set.of("org.jspecify.annotations.NullMarked", "org.jspecify.annotations.Nullable");

  @Test
  void givenCompiledSourceReader_whenEveryClassfileIsInspected_thenOnlySourceIdentityAndJavaBaseAreReachable() throws IOException {
    // Arrange
    var root = partition("source-reader");
    var baseTypes = platformTypes("java.base");

    // Act and Assert
    inspect(root, baseTypes, List.of("org.aquilagraph.source.api.", "org.aquilagraph.identity.api."));
    for (var type : ADMISSION_TYPES) {
      assertFalse(Files.exists(root.resolve("org/aquilagraph/source/api/" + type + ".class")), type + " escaped into reader output");
    }
  }

  @Test
  void givenCompiledCfgPartition_whenEveryClassfileIsInspected_thenSourceAdmissionAndOtherContextsAreAbsent() throws IOException {
    // Arrange
    var root = partition("cfg");
    var baseTypes = platformTypes("java.base");

    // Act and Assert
    inspect(root, baseTypes, List.of("org.aquilagraph.cfg.", "org.aquilagraph.source.api.", "org.aquilagraph.identity.api."));
  }

  @Test
  void givenCompiledCfgReaderAndApplication_whenClassfilesAreInspected_thenPrivilegedGraphConstructionIsUnreachable() throws IOException {
    // Arrange
    var baseTypes = platformTypes("java.base");

    // Act and Assert
    inspect(partition("cfg-reader"), baseTypes,
        List.of("org.aquilagraph.cfg.api.", "org.aquilagraph.source.api.", "org.aquilagraph.identity.api."));
    inspect(partition("application"), baseTypes, List.of("org.aquilagraph.application.", "org.aquilagraph.cfg.api.",
        "org.aquilagraph.source.api.", "org.aquilagraph.identity.api."));
    for (var type : List.of("GraphValueAdmission", "CanonicalGraphEncoding", "LoweringPlan", "CfgAdmissionResult")) {
      assertFalse(Files.exists(partition("cfg-reader").resolve("org/aquilagraph/cfg/api/" + type + ".class")));
    }
  }

  @Test
  void givenCfgReader_whenConsumersTryConstructionAdmissionOrImplementationFactories_thenActualCompilationRejectsAccess()
      throws IOException, URISyntaxException {
    // Arrange
    var forbidden = List.of("org.aquilagraph.cfg.api.GraphValueAdmission", "org.aquilagraph.cfg.api.LoweringPlan",
        "org.aquilagraph.cfg.api.CfgAdmissionResult",
        "org.aquilagraph.cfg.internal.ControlFlowGraphBuilder", "org.aquilagraph.cfg.internal.JavaCfgDeriver",
        "org.aquilagraph.cfg.CfgDerivers");

    for (var type : forbidden) {
      var simpleName = type.substring(type.lastIndexOf('.') + 1);
      var source = "package reader; import " + type + "; public final class Consumer { private " + simpleName + " value; }";
      var diagnostics = new DiagnosticCollector<JavaFileObject>();

      // Act
      var compiled = compileConsumer(source, true, diagnostics);

      // Assert
      assertFalse(compiled, type + " unexpectedly escaped into the CFG reader contract");
      assertTrue(diagnostics.getDiagnostics().stream().anyMatch(value -> value.getKind() == Diagnostic.Kind.ERROR),
          diagnostics.getDiagnostics().toString());
    }
  }

  @Test
  void givenCfgReader_whenConsumerUsesSourceAssociationAndGraphQueries_thenCompilationSucceedsWithoutLoweringImplementation()
      throws IOException, URISyntaxException {
    // Arrange
    var source = "package reader; import org.aquilagraph.cfg.api.CfgDeriver; import org.aquilagraph.cfg.api.ControlFlowGraph; "
        + "public final class Consumer { public int nodes(ControlFlowGraph graph, CfgDeriver port) { "
        + "return graph.nodes().size() + graph.body().stream().toList().size(); } }";
    var diagnostics = new DiagnosticCollector<JavaFileObject>();

    // Act and Assert
    assertTrue(compileConsumer(source, true, diagnostics), diagnostics.getDiagnostics().toString());
  }

  @Test
  void givenCompiledFrontendPartition_whenEveryClassfileIsInspected_thenOnlySourceAndPublicCompilerDependenciesRemain()
      throws IOException {
    // Arrange
    var root = partition("frontend");
    var platformTypes = platformTypes("java.base", "java.compiler", "jdk.compiler");

    // Act and Assert
    inspect(root, platformTypes, List.of("org.aquilagraph.frontend.", "org.aquilagraph.source.api.",
        "org.aquilagraph.source.admission.", "org.aquilagraph.identity.api."));
  }

  @Test
  void givenSourceReaderArtifacts_whenAConsumerCompilesWithoutOtherContexts_thenPublicSourceQueriesRemainUsable()
      throws IOException, URISyntaxException {
    // Arrange
    var source = "package reader; import org.aquilagraph.source.api.JavaProgram; "
        + "public final class Consumer { public int methods(JavaProgram program) { return program.methodCount(); } }";
    var diagnostics = new DiagnosticCollector<JavaFileObject>();

    // Act
    var compiled = compileConsumer(source, false, diagnostics);

    // Assert
    assertTrue(compiled, diagnostics.getDiagnostics().toString());
  }

  @Test
  void givenSourceReaderOrCfgArtifacts_whenConsumersTryAdmissionOrProducerTypes_thenActualCompilationRejectsAccess()
      throws IOException, URISyntaxException {
    // Arrange
    var forbidden = List.of("org.aquilagraph.source.api.AstAggregateAdmission", "org.aquilagraph.source.api.JavaAstAggregateBuilder",
        "org.aquilagraph.source.admission.AstValueAdmission", "org.aquilagraph.source.admission.CopiedAstValues",
        "org.aquilagraph.frontend.JavaFrontend", "org.aquilagraph.application.AnalysisRun");

    for (var withCfg : List.of(false, true)) {
      for (var type : forbidden) {
        var simpleName = type.substring(type.lastIndexOf('.') + 1);
        var source = "package reader; import " + type + "; public final class Consumer { private " + simpleName + " value; }";
        var diagnostics = new DiagnosticCollector<JavaFileObject>();

        // Act
        var compiled = compileConsumer(source, withCfg, diagnostics);

        // Assert
        assertFalse(compiled, type + " unexpectedly compiled with CFG=" + withCfg);
        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR
            && Set.of("compiler.err.doesnt.exist", "compiler.err.cant.resolve.location", "compiler.err.not.def.public.cant.access")
                .contains(diagnostic.getCode())), diagnostics.getDiagnostics().toString());
      }
    }
  }

  private static void inspect(Path root, Set<String> platformTypes, List<String> prefixes) throws IOException {
    try (var paths = Files.walk(root)) {
      var files = paths.filter(path -> path.toString().endsWith(".class")).toList();
      assertFalse(files.isEmpty(), "Partition has no compiled classes: " + root);
      for (var file : files) {
        var references = ClassfileDependencies.read(Files.readAllBytes(file));
        for (var type : references.types()) {
          assertTrue(platformTypes.contains(type) || prefixes.stream().anyMatch(type::startsWith), file + " depends on " + type);
          assertFalse(type.startsWith("com.sun.tools.javac.") || type.startsWith("jdk.internal.") || type.startsWith("sun."),
              file + " depends on an internal compiler type " + type);
          assertFalse(ADMISSION_TYPES.stream().anyMatch(name -> type.equals("org.aquilagraph.source.api." + name)),
              file + " depends on privileged admission " + type);
        }
        for (var annotation : references.annotations()) {
          assertTrue(platformTypes.contains(annotation) || NULLNESS_METADATA.contains(annotation), file + " retains " + annotation);
        }
      }
    }
  }

  private static Set<String> platformTypes(String... modules) throws IOException {
    var types = new HashSet<String>();
    for (var moduleName : modules) {
      var module = ModuleFinder.ofSystem().find(moduleName).orElseThrow();
      try (var reader = module.open(); var resources = reader.list()) {
        resources.filter(name -> name.endsWith(".class"))
            .map(name -> name.substring(0, name.length() - 6).replace('/', '.')).forEach(types::add);
      }
    }
    return Set.copyOf(types);
  }

  private static Path partition(String name) {
    return Path.of("target", "partitions", name, "classes");
  }

  private static boolean compileConsumer(String source, boolean withCfg, DiagnosticCollector<JavaFileObject> diagnostics)
      throws IOException, URISyntaxException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var nullnessDefinitions = Path.of(NullMarked.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var dependencies = new ArrayList<Path>(List.of(partition("source-reader"), partition("identity"), nullnessDefinitions));
    if (withCfg) {
      dependencies.add(partition("cfg-reader"));
    }
    var classPath = dependencies.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    var output = Files.createTempDirectory(Path.of("target"), "source-reader-consumer-");
    var options = List.of("--release", "25", "--limit-modules", "java.base", "--class-path", classPath, "--source-path",
        partition("identity").toString(), "-proc:none", "-d", output.toString());
    var input = new SimpleJavaFileObject(URI.create("string:///reader/Consumer.java"), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return source;
      }
    };
    try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
      return compiler.getTask(null, manager, diagnostics, options, null, List.of(input)).call();
    }
  }
}
