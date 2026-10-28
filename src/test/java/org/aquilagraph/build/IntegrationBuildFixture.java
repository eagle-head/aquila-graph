package org.aquilagraph.build;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.aquilagraph.testsupport.IsolatedBuild;
import org.aquilagraph.testsupport.IsolatedBuild.Result;
import org.w3c.dom.Element;

/** Bounded runner controls consume the delivered configuration, discovery and result owners. */
final class IntegrationBuildFixture {
  private final IsolatedBuild build;
  private final List<String> runtimeEntries;

  IntegrationBuildFixture(Path directory) throws Exception {
    build = new IsolatedBuild(directory);
    var arguments = Files.readAllLines(build.tools().resolve("target/build-inputs/test-runtime.args"));
    var runtime = arguments.get(1);
    runtime = runtime.substring(1, runtime.length() - 1);
    runtimeEntries = List.of(runtime.split(Pattern.quote(File.pathSeparator), -1));
    var classpath = build.root().resolve("target/test-classes") + File.pathSeparator + runtime;
    var inputs = Files.createDirectories(build.root().resolve("target/build-inputs"));
    Files.writeString(inputs.resolve("test-runtime.args"), "--class-path\n\"" + classpath.replace('\\', '/') + "\"\n");
    build.writePom(build.executions("discover-integration-catalog"), "", failsafe());
    var pom = Files.readString(build.root().resolve("pom.xml"));
    pom = pom.replace("<properties>", """
        <properties><maven.test.skip>false</maven.test.skip><build.integration.scope>legacy-process</build.integration.scope>
        <build.integration.groups /><build.integration.classes /><build.integration.includes>**/*ProcessIT.java</build.integration.includes>
        <test.integration.forks>1</test.integration.forks><test.integration.reuse.forks>true</test.integration.reuse.forks>
        <coverageAgentArguments /><test.jvm.arguments>-Xms128m -Xmx512m --enable-native-access=ALL-UNNAMED</test.jvm.arguments>
        <build.failure.limit>0</build.failure.limit>
        """);
    var dependencies = new StringBuilder("<dependencies>");
    for (var artifact : List.of("junit-jupiter-api", "junit-jupiter-engine", "junit-platform-launcher")) {
      dependencies.append("<dependency><groupId>org.junit.").append(artifact.startsWith("junit-platform") ? "platform" : "jupiter")
          .append("</groupId><artifactId>").append(artifact).append("</artifactId><version>")
          .append(build.version("junit.version")).append("</version><scope>test</scope></dependency>");
    }
    dependencies.append("</dependencies>");
    pom = pom.replace("<build>", dependencies + "<build>")
        .replace("<arg>-Werror</arg>", "<arg>-Werror</arg><arg>--class-path</arg><arg>" + IsolatedBuild.xml(classpath) + "</arg>");
    Files.writeString(build.root().resolve("pom.xml"), pom);
    var main = Files.createDirectories(build.root().resolve("src/main/java/fixture")).resolve("FixtureApplication.java");
    Files.writeString(main, """
        package fixture;
        public final class FixtureApplication {
          private FixtureApplication() { }
          public static int value() { return 7; }
        }
        """);
    source("ExampleProcessIT", "@Tag(\"process\")", """
        @Test void givenSelectedProcess_whenRunnerExecutes_thenItsEffectIsVisible() throws Exception {
          // Arrange
          var output = Path.of("process.marker");
          // Act
          Files.writeString(output, "observed process");
          // Assert
          assertEquals("observed process", Files.readString(output));
        }
        @TestFactory Stream<DynamicTest> givenFactoryInputs_whenRunnerExecutes_thenEveryChildEffectIsVisible() {
          // Arrange
          var values = List.of("first", "second");
          // Act
          var cases = values.stream().map(value -> DynamicTest.dynamicTest(value, () -> {
            Files.writeString(Path.of(value + ".marker"), value);
            assertEquals(value, Files.readString(Path.of(value + ".marker")));
          }));
          // Assert
          return cases;
        }
        """);
    source("ExamplePersistenceIT", "@Tag(\"persistence\")", marker("persistence"));
    source("ExampleLifecycleIT", "@Tag(\"lifecycle\")", marker("lifecycle"));
    source("ExampleTest", "", """
        @Test @Tag("first-unit") void givenFirstUnit_whenRunnerExecutes_thenItsEffectIsVisible() throws Exception {
          // Arrange
          var output = Path.of("first-unit.marker");
          // Act
          Files.writeString(output, "first");
          // Assert
          assertEquals("first", Files.readString(output));
        }
        @Test @Tag("second-unit") void givenSecondUnit_whenRunnerExecutes_thenItsEffectIsVisible() throws Exception {
          // Arrange
          var output = Path.of("second-unit.marker");
          // Act
          Files.writeString(output, "second");
          // Assert
          assertEquals("second", Files.readString(output));
        }
        """);
  }

  IsolatedBuild build() {
    return build;
  }

  Result run(String operation, String... flags) throws Exception {
    return retain(build.run(operation, flags));
  }

  Result retain(Result result) throws Exception {
    var output = Files.createDirectories(build.tools().resolve("target/implementation/integration-selection/controls"));
    var id = UUID.randomUUID().toString();
    Files.writeString(output.resolve(id + ".log"), result.output());
    Files.writeString(output.resolve(id + ".exit"), Integer.toString(result.exitCode()));
    return result;
  }

  void source(String name, String annotations, String body) throws Exception {
    source(name, "", annotations, body);
  }

  void source(String name, String imports, String annotations, String body) throws Exception {
    var directory = Files.createDirectories(build.root().resolve("src/test/java/fixture"));
    Files.writeString(directory.resolve(name + ".java"), """
        package fixture;
        import static org.junit.jupiter.api.Assertions.assertEquals;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.util.List;
        import java.util.stream.Stream;
        import org.junit.jupiter.api.DynamicTest;
        import org.junit.jupiter.api.Tag;
        import org.junit.jupiter.api.Test;
        import org.junit.jupiter.api.TestFactory;
        %s
        %s
        class %s {
        %s
        }
        """.formatted(imports, annotations, name, body));
  }

  private String failsafe() throws Exception {
    var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(build.tools().resolve("pom.xml").toFile());
    var plugin = (Element) XPathFactory.newInstance().newXPath().evaluate(
        "/project/build/plugins/plugin[artifactId='maven-failsafe-plugin']", document, XPathConstants.NODE);
    var copy = (Element) plugin.cloneNode(true);
    copy.getElementsByTagName("version").item(0).setTextContent(build.version("maven-failsafe-plugin.version"));
    for (var path : runtimeEntries) {
      var additional = document.createElement("additionalClasspathElement");
      additional.setTextContent(path);
      copy.getElementsByTagName("additionalClasspathElements").item(0).appendChild(additional);
    }
    var transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    var result = new StringWriter();
    transformer.transform(new DOMSource(copy), new StreamResult(result));
    return result.toString().replace("${build.tools.outputDirectory}", IsolatedBuild.xml(build.tools().resolve("target/build-tool-classes").toString()))
        .replace("${project.build.outputDirectory}", IsolatedBuild.xml(build.tools().resolve("target/classes").toString()));
  }

  static String marker(String group) {
    return """
        @Test void givenSelectedGroup_whenRunnerExecutes_thenItsEffectIsVisible() throws Exception {
          // Arrange
          var output = Path.of("%s.marker");
          // Act
          Files.writeString(output, "%s");
          // Assert
          assertEquals("%s", Files.readString(output));
        }
        """.formatted(group, group, group);
  }
}
