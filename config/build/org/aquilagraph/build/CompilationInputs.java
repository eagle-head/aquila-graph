package org.aquilagraph.build;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Objects;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/** Inventories live inputs and maps compiler reads exclusively onto their private capture. */
final class CompilationInputs {
  private static final String SOURCE_PATH = "--source-path";
  private static final String MODULE_SOURCE_PATH = "--module-source-path";
  private static final String OUTPUT_OPTION = "-d";
  private static final String SOURCE_DIRECTORY = "sources";
  private static final List<String> PATH_OPTIONS = List.of("--module-path", "--class-path", "--processor-path", SOURCE_PATH,
      MODULE_SOURCE_PATH);
  private static final List<String> VALUE_OPTIONS = List.of("--release", "-encoding", "--module", "-processor");
  private final CompilationRequest request;
  private final Path captureDirectory;
  private final Map<Path, Path> copies = new LinkedHashMap<>();
  private final Properties inputManifest = new Properties();
  private final List<String> compiler = new ArrayList<>();

  CompilationInputs(CompilationRequest request) throws IOException {
    this.request = request;
    captureDirectory = request.state().resolve("capture");
    inputManifest.setProperty("schema", "compilation-inputs/1");
    inputManifest.setProperty("batch", request.batch());
    inputManifest.setProperty("jdk.home", System.getProperty("java.home"));
    inputManifest.setProperty("jdk.version", Runtime.version().toString());
    inputManifest.setProperty("jdk.vendor", System.getProperty("java.vendor"));
    inputManifest.setProperty("platform", System.getProperty("os.name") + "/" + System.getProperty("os.arch") + "/"
        + System.getProperty("os.version") + "/" + File.separator + "/" + File.pathSeparator);
    observe(Path.of(System.getProperty("java.home"), "release"));
    observe(request.root().resolve("pom.xml"));
    observe(request.root().resolve(".mvn"));
    observe(request.root().resolve("config"));
    observe(request.root().resolve("src/main/resources"));
    observe(request.root().resolve("src/test/resources"));
    copy(request.sources(), captureDirectory.resolve(SOURCE_DIRECTORY));
    copy(request.resources(), captureDirectory.resolve("resources"));
    if (CompilationRequest.TEST_BATCH.equals(request.batch())) {
      CompilationPublication.requireProduction(request.root());
      observe(request.root().resolve("target/compilation/production/success.properties"));
    }
    var environment = new Properties();
    var effectiveEnvironment = compilerEnvironment(request.environment());
    environment.putAll(effectiveEnvironment);
    inputManifest.setProperty("environment.digest", PreparationFiles.digest(environment));
    effectiveEnvironment.forEach((key, value) -> inputManifest.setProperty("environment.key." + key, PreparationFiles.digest(value)));
    for (var index = 0; index < request.arguments().size(); index++) {
      var argument = request.arguments().get(index);
      inputManifest.setProperty("argument." + index, argument);
      if (argument.startsWith("@")) {
        observe(request.root().resolve(argument.substring(1)));
      }
    }
    rewrite(request.effectiveArguments());
  }

  private void rewrite(List<String> arguments) throws IOException {
    var values = arguments.iterator();
    while (values.hasNext()) {
      var option = values.next();
      if (PATH_OPTIONS.contains(option) || VALUE_OPTIONS.contains(option) || OUTPUT_OPTION.equals(option)) {
        if (!values.hasNext()) {
          throw new IOException("Missing compiler option value: " + option);
        }
        var value = values.next();
        compiler.add(option);
        compiler.add(rewriteValue(option, value));
      } else if (option.endsWith(".java")) {
        var source = request.root().resolve(option).normalize();
        if (!source.startsWith(request.sources())) {
          throw new IOException("Compiler source is outside the captured source root: " + source);
        }
        compiler.add(captureDirectory.resolve(SOURCE_DIRECTORY).resolve(request.sources().relativize(source)).toString());
      } else if (supportedOption(option)) {
        compiler.add(option);
      } else {
        throw new IOException("Unsupported influencing compiler option: " + option);
      }
    }
    if (!arguments.contains(OUTPUT_OPTION)) {
      throw new IOException("Compilation requires an explicit owned output directory");
    }
    if (!arguments.contains(SOURCE_PATH) && !arguments.contains(MODULE_SOURCE_PATH)) {
      compiler.add(SOURCE_PATH);
      compiler.add(captureDirectory.resolve(SOURCE_DIRECTORY).toString());
    }
    for (var index = 0; index < compiler.size(); index++) {
      inputManifest.setProperty("effective.argument." + index, compiler.get(index));
    }
  }

  private String rewriteValue(String option, String value) throws IOException {
    if (VALUE_OPTIONS.contains(option)) {
      return value;
    }
    if (OUTPUT_OPTION.equals(option)) {
      if (!request.root().resolve(value).normalize().equals(request.output())) {
        throw new IOException("Compiler output differs from its publication owner");
      }
      return request.state().resolve("output").toString();
    }
    if (SOURCE_PATH.equals(option) || MODULE_SOURCE_PATH.equals(option)) {
      if (!request.root().resolve(value).normalize().equals(request.sources())) {
        throw new IOException("Source lookup must use exactly the captured source root");
      }
      return captureDirectory.resolve(SOURCE_DIRECTORY).toString();
    }
    var paths = new ArrayList<String>();
    for (var element : value.split(File.pathSeparator, -1)) {
      if (element.isEmpty()) {
        throw new IOException("An empty compiler dependency path would select the live working directory");
      }
      var original = request.root().resolve(element).normalize();
      if (original.startsWith(request.output())) {
        continue;
      }
      if (!Files.exists(original)) {
        throw new IOException("Missing compiler dependency: " + original);
      }
      requireExplicitDependencies(original);
      var destination = copies.get(original);
      if (destination == null) {
        destination = captureDirectory.resolve("dependencies").resolve(Integer.toString(copies.size())).resolve(original.getFileName());
        copy(original, destination);
      }
      paths.add(destination.toString());
    }
    return paths.isEmpty() ? captureDirectory.resolve("empty").toString() : String.join(File.pathSeparator, paths);
  }

  private static void requireExplicitDependencies(Path path) throws IOException {
    if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
      try (var jar = new JarFile(path.toFile())) {
        var manifest = jar.getManifest();
        if (manifest != null && manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH) != null) {
          throw new IOException("Implicit manifest Class-Path dependencies are unsupported; declare the complete compiler path: " + path);
        }
      }
    }
  }

  private static boolean supportedOption(String option) {
    return List
        .of("-g", "-g:none", "-g:lines,vars,source", "-parameters", "-Xlint:all", "-Werror", "-proc:none", "-proc:full",
            "-XDcompilePolicy=simple", "--should-stop=ifError=FLOW")
        .contains(option) || option.startsWith("-Xplugin:ErrorProne ") || option.matches("-J-Xm[sx][1-9][0-9]*[mMgG]")
        || option.matches("-J--add-(exports|opens)=jdk.compiler/com\\.sun\\.tools\\.javac\\.[a-z]+=ALL-UNNAMED")
        || option.matches("-A[A-Za-z][A-Za-z0-9_.]*=[A-Za-z0-9_.-]+");
  }

  private void observe(Path path) throws IOException {
    inputManifest.setProperty("input." + path, PreparationFiles.digest(path));
  }

  private void copy(Path original, Path destination) throws IOException {
    observe(original);
    copies.put(original, destination);
  }

  String identity() {
    return PreparationFiles.digest(inputManifest);
  }

  boolean hasMatchingManifest() throws IOException {
    var manifest = request.state().resolve("inputs.properties");
    if (!Files.isRegularFile(manifest)) {
      return false;
    }
    try {
      var previous = BuildFiles.read(manifest);
      var matches = identity().equals(PreparationFiles.digest(previous));
      if (!matches) {
        var names = new TreeSet<>(inputManifest.stringPropertyNames());
        names.addAll(previous.stringPropertyNames());
        var changed = names.stream().filter(key -> !Objects.equals(inputManifest.getProperty(key), previous.getProperty(key))).toList();
        System.getLogger(CompilationInputs.class.getName()).log(Level.INFO,
            "Compilation " + request.batch() + ": changed input fields " + changed);
      }
      return matches;
    } catch (IllegalArgumentException | MalformedInputException exception) {
      System.getLogger(CompilationInputs.class.getName()).log(Level.INFO, "Unreadable captured-input manifest; rebuilding: " + manifest,
          exception);
      return false;
    }
  }

  void capture() throws IOException {
    PreparationFiles.clear(request.root(), captureDirectory);
    Files.createDirectories(captureDirectory.resolve("empty"));
    var provenance = new Properties();
    for (var entry : copies.entrySet()) {
      PreparationFiles.copy(entry.getKey(), entry.getValue());
      if (!PreparationFiles.digest(entry.getValue()).equals(inputManifest.getProperty("input." + entry.getKey()))) {
        throw new IOException("Input changed during capture: " + entry.getKey());
      }
      provenance.setProperty(entry.getKey().toString(), entry.getValue().toString());
    }
    BuildFiles.write(request.state().resolve("provenance.properties"), provenance);
    BuildFiles.write(request.state().resolve("inputs.properties"), inputManifest);
    PreparationFiles.freeze(captureDirectory);
  }

  void requireUnchanged() throws IOException {
    if (!identity().equals(new CompilationInputs(request).identity())) {
      throw new IOException("Compilation inputs changed during preparation; current candidate is invalid");
    }
  }

  void requireCaptured() throws IOException {
    for (var entry : copies.entrySet()) {
      if (!PreparationFiles.digest(entry.getValue()).equals(inputManifest.getProperty("input." + entry.getKey()))) {
        throw new IOException("Captured compiler input changed: " + entry.getKey());
      }
    }
  }

  List<String> compilerArguments() {
    return List.copyOf(compiler);
  }

  static Map<String, String> compilerEnvironment(Map<String, String> environment) {
    var result = new LinkedHashMap<>(environment);
    result.keySet().removeIf(key -> key.startsWith("AQUILA_BUILD_") || key.startsWith("MAVEN_") || key.startsWith("MVNW_")
        || "JDK_JAVAC_OPTIONS".equals(key) || "INVOCATION_ID".equals(key));
    return Collections.unmodifiableMap(result);
  }
}
