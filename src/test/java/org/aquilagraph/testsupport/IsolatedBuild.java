package org.aquilagraph.testsupport;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Small real Maven projects that consume the delivered local execution and admission owners. */
public final class IsolatedBuild {
  private final Path root;
  private final Path tools;

  public IsolatedBuild(Path directory) throws IOException {
    root = Files.createDirectories(directory).toRealPath();
    tools = Path.of("").toRealPath();
    Files.createDirectories(root.resolve(".mvn"));
    Files.createDirectories(root.resolve("target"));
    linkAdmissionSources();
  }

  private void linkAdmissionSources() throws IOException {
    var sources = tools.resolve("config/build");
    try (var files = Files.walk(sources)) {
      for (var source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        var destination = root.resolve("config/build").resolve(sources.relativize(source));
        Files.createDirectories(destination.getParent());
        Files.createLink(destination, source);
      }
    }
  }

  public Path root() {
    return root;
  }

  public Path tools() {
    return tools;
  }

  public void writePom(String executions, String dependencies, String plugins) throws Exception {
    writePom(executions, dependencies, plugins, "");
  }

  public void writePom(String executions, String dependencies, String plugins, String compilerExecutions) throws Exception {
    var pom = """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion><groupId>org.aquilagraph.fixture</groupId><artifactId>local-build</artifactId><version>1</version>
          <properties><maven.compiler.release>25</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
          <build><plugins>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-clean-plugin</artifactId><version>@CLEAN@</version></plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-resources-plugin</artifactId><version>@RESOURCES@</version></plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>@COMPILER@</version>
              <configuration><compilerArgs><arg>-Xlint:all</arg><arg>-Werror</arg></compilerArgs></configuration>
              @COMPILER_EXECUTIONS@
            </plugin>
            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>@SUREFIRE@</version></plugin>
            <plugin><groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId><version>@EXEC@</version>
              @DEPENDENCIES@
              <executions>
                @ADMISSION_EXECUTIONS@
                @EXECUTIONS@
              </executions>
            </plugin>
            @PLUGINS@
          </plugins></build>
        </project>
        """;
    pom = pom.replace("@CLEAN@", version("maven-clean-plugin.version"))
        .replace("@RESOURCES@", version("maven-resources-plugin.version"))
        .replace("@COMPILER@", version("maven-compiler-plugin.version"))
        .replace("@SUREFIRE@", version("maven-surefire-plugin.version"))
        .replace("@EXEC@", version("exec-maven-plugin.version"))
                .replace("@DEPENDENCIES@", dependencies).replace("@EXECUTIONS@", executions).replace("@PLUGINS@", plugins)
        .replace("@ADMISSION_EXECUTIONS@", admissionExecutions())
        .replace("@COMPILER_EXECUTIONS@", compilerExecutions);
    Files.writeString(root.resolve("pom.xml"), pom);
  }

  public String version(String property) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(tools.resolve("pom.xml").toFile())
        .getElementsByTagName(property).item(0).getTextContent();
  }

  private String admissionExecutions() throws Exception {
    var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(tools.resolve("pom.xml").toFile());
    var result = new StringWriter();
    for (var kind : List.of("default", "clean")) {
      var source = (Node) XPathFactory.newInstance().newXPath().evaluate(
          "/project/build/plugins/plugin[artifactId='exec-maven-plugin']/executions/execution[id='admit-" + kind + "-lifecycle']",
          document, XPathConstants.NODE);
      if (source == null) {
        throw new IllegalStateException("The actual admission execution is missing: " + kind);
      }
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.transform(new DOMSource(source), new StreamResult(result));
    }
    return result.toString();
  }

  public String runtimeEnforcer(String requiredJava) throws Exception {
    var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(tools.resolve("pom.xml").toFile());
    var plugin = (Element) XPathFactory.newInstance().newXPath().evaluate(
        "/project/build/plugins/plugin[artifactId='maven-enforcer-plugin']", document, XPathConstants.NODE);
    if (plugin == null) {
      throw new IllegalStateException("The actual runtime contract plugin is missing");
    }
    var copy = (Element) plugin.cloneNode(true);
    copy.getElementsByTagName("version").item(0).setTextContent(version("maven-enforcer-plugin.version"));
    var rules = (Element) copy.getElementsByTagName("requireJavaVersion").item(0);
    rules.getElementsByTagName("version").item(0).setTextContent(requiredJava);
    var result = new StringWriter();
    TransformerFactory.newInstance().newTransformer().transform(new DOMSource(copy), new StreamResult(result));
    return result.toString().replaceFirst("<\\?xml[^?]*\\?>", "");
  }

  public ProcessBuilder builder(String operation, String... flags) throws IOException {
    return builder(tools.resolve(windows() ? "mvnw.cmd" : "mvnw"), operation, flags);
  }

  public ProcessBuilder powerShell(String operation, String... flags) {
    var command = new StringBuilder("& ").append(powerShellWord(tools.resolve("Invoke-Build.ps1").toString()))
        .append(" -ProjectRoot ").append(powerShellWord(root.toString()))
        .append(" -MavenCommand ").append(powerShellWord(tools.resolve("mvnw.cmd").toString()))
        .append(" -Target ").append(powerShellWord(operation)).append(" -MavenFlags @('-B','--no-transfer-progress'");
    for (var flag : flags) {
      command.append(',').append(powerShellWord(flag));
    }
    command.append(')');
    return isolate(new ProcessBuilder("pwsh", "-NoProfile", "-NonInteractive", "-EncodedCommand",
        Base64.getEncoder().encodeToString(command.toString().getBytes(StandardCharsets.UTF_16LE)))
        .directory(root.toFile()).redirectErrorStream(true));
  }

  private static String powerShellWord(String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  public ProcessBuilder builder(Path maven, String operation, String... flags) throws IOException {
    var command = new ArrayList<>(List.of(java().toString(), "--enable-native-access=ALL-UNNAMED", tools.resolve("config/build/org/aquilagraph/build/BuildExecution.java").toString(),
        "--root", root.toString(), "--tool-root", tools.toString(), "--maven", maven.toString(), operation,
        "-B", "--no-transfer-progress"));
    command.addAll(List.of(flags));
    var argumentFile = root.resolve("java-" + UUID.randomUUID() + ".args");
    var contents = new StringBuilder();
    for (var argument : command.subList(1, command.size())) {
      contents.append('"').append(argument.replace("\\", "\\\\").replace("\"", "\\\"")).append('"').append('\n');
    }
    Files.writeString(argumentFile, contents);
    return isolate(new ProcessBuilder(command.getFirst(), "@" + argumentFile).directory(root.toFile()).redirectErrorStream(true));
  }

  public static ProcessBuilder isolate(ProcessBuilder builder) {
    for (var name : List.of("MAVEN_BASEDIR", "MAVEN_PROJECTBASEDIR", "AQUILA_BUILD_INVOCATION", "AQUILA_BUILD_RESERVATION",
        "AQUILA_BUILD_WRAPPER", "AQUILA_BUILD_WRAPPER_COMMAND", "AQUILA_BUILD_PROCESS", "AQUILA_BUILD_EFFECTIVE_GOALS")) {
      builder.environment().remove(name);
    }
    return builder;
  }

  public Result run(String operation, String... flags) throws Exception {
    return run(builder(operation, flags));
  }

  public Result run(ProcessBuilder builder) throws Exception {
    var log = root.resolve("build-" + UUID.randomUUID() + ".log");
    try (var process = OwnedProcess.start(builder.redirectOutput(log.toFile()))) {
      if (!process.await(Duration.ofSeconds(90))) {
        throw new IOException("Isolated build exceeded 90 seconds: " + Files.readString(log));
      }
      return new Result(process.exitValue(), Files.readString(log));
    }
  }

  public static Path java() {
    return Path.of(System.getProperty("java.home"), "bin", windows() ? "java.exe" : "java");
  }

  public static boolean windows() {
    return System.getProperty("os.name").startsWith("Windows");
  }

  public static String xml(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
  }

  public record Result(int exitCode, String output) {
  }
}
