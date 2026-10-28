package org.aquilagraph.build;

import java.io.StringWriter;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/** Bounded real Maven workload for the complete owner's phase, failure and report contracts. */
final class CompleteBuildFixture {
  private final IntegrationBuildFixture integration;
  private final IsolatedBuild build;

  CompleteBuildFixture(Path directory) throws Exception {
    integration = new IntegrationBuildFixture(directory);
    build = integration.build();
    var pom = Files.readString(build.root().resolve("pom.xml"));
    var surefire = plugin("maven-surefire-plugin");
    var additional = surefire.getOwnerDocument().createElement("additionalClasspathElement");
    additional.setTextContent(build.tools().resolve("target/test-classes").toString());
    surefire.getElementsByTagName("additionalClasspathElements").item(0).appendChild(additional);
    var old = "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>"
        + build.version("maven-surefire-plugin.version") + "</version></plugin>";
    pom = pom.replace(old, xml(surefire));
    pom = pom.replace("<arg>--class-path</arg><arg>",
        "<arg>--class-path</arg><arg>" + IsolatedBuild.xml(build.root().resolve("target/classes").toString()) + File.pathSeparator);
    var hooks = build.executions("discover-unit-catalog", "capture-unit-validation", "reconcile-integration-validation");
    var exec = pom.indexOf("<executions>", pom.indexOf("<artifactId>exec-maven-plugin</artifactId>"));
    pom = pom.substring(0, exec) + pom.substring(exec).replaceFirst("<executions>", "<executions>" + Matcher.quoteReplacement(hooks));
    var antrun = plugin("maven-antrun-plugin");
    var executions = (Element) antrun.getElementsByTagName("executions").item(0);
    while (executions.hasChildNodes()) {
      executions.removeChild(executions.getFirstChild());
    }
    var original = plugin("maven-antrun-plugin").getElementsByTagName("execution");
    for (var index = 0; index < original.getLength(); index++) {
      var execution = (Element) original.item(index);
      if ("report-unit-coverage".equals(execution.getElementsByTagName("id").item(0).getTextContent())) {
        executions.appendChild(antrun.getOwnerDocument().importNode(execution, true));
      }
    }
    var report = xml(antrun);
    var start = report.indexOf("<structure ");
    var end = report.indexOf("</structure>", start) + "</structure>".length();
    report = report.substring(0, start) + """
        <structure name="complete-control"><classfiles><fileset dir="${project.build.outputDirectory}" includes="**/*.class" /></classfiles>
        <sourcefiles><fileset dir="${project.basedir}/src/main/java" includes="**/*.java" /></sourcefiles></structure>
        """ + report.substring(end);
    report = report.replace("</executions>", """
        <execution><id>prepare-control-tools</id><phase>generate-sources</phase><goals><goal>run</goal></goals><configuration><target>
        <copy todir="${project.build.directory}/build-tool-classes"><fileset dir="%s/target/build-tool-classes" /></copy>
        </target></configuration></execution>
        <execution><id>require-analysis-reports</id><phase>verify</phase><goals><goal>run</goal></goals><configuration><target>
        <available file="${project.build.directory}/checkstyle-result.xml" property="control.report.present" />
        <fail unless="control.report.present" message="Missing required current control analysis report" />
        </target></configuration></execution></executions>
        """.formatted(IsolatedBuild.xml(build.tools().toString().replace('\\', '/'))));
    pom = pom.replace("</plugins>", xml(plugin("jacoco-maven-plugin")) + xml(plugin("maven-help-plugin")) + """
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-checkstyle-plugin</artifactId><version>%s</version>
        <configuration><configLocation>${project.basedir}/config/control-checkstyle.xml</configLocation><failOnViolation>true</failOnViolation>
        <includeResources>false</includeResources></configuration><executions><execution><id>check-control-source</id><phase>verify</phase>
        <goals><goal>check</goal></goals></execution></executions></plugin>
        """.formatted(build.version("maven-checkstyle-plugin.version")) + report + "</plugins>");
    pom = pom.replace("<properties>", "<properties><build.complete.skip>true</build.complete.skip><build.unit.directory /><build.failure.limit>0</build.failure.limit>")
        .replace("</project>", """
        <profiles><profile><id>neo4j-integration</id><properties><build.integration.scope>all</build.integration.scope>
        <build.integration.includes>**/*IT.java</build.integration.includes></properties></profile></profiles></project>
        """);
    Files.writeString(build.root().resolve("pom.xml"), preparedValidationTools(pom));
    Files.writeString(build.root().resolve("config/build/verification.properties"),
        "required.executions=check-control-source,require-analysis-reports\n");
    Files.writeString(build.root().resolve("config/control-checkstyle.xml"), """
        <?xml version="1.0"?>
        <!DOCTYPE module PUBLIC "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN" "https://checkstyle.org/dtds/configuration_1_3.dtd">
        <module name="Checker"><module name="TreeWalker"><module name="AvoidStarImport" /></module></module>
        """);
    Files.writeString(build.root().resolve("src/main/java/fixture/FixtureApplication.java"), """
        package fixture;
        public final class FixtureApplication {
          private FixtureApplication() { }
          public static int unitValue() { return 7; }
          public static int integrationValue() { return 11; }
        }
        """);
    integration.source("ExampleTest", "", """
        @Test void givenUnitInput_whenEvaluated_thenTheUnitValueIsObserved() {
          // Arrange
          var expected = 7;
          // Act
          var actual = FixtureApplication.unitValue();
          // Assert
          assertEquals(expected, actual);
        }
        """);
    integration.source("ExamplePersistenceIT", "@Tag(\"persistence\")", """
        @Test void givenIntegrationInput_whenEvaluated_thenTheIntegrationOnlyValueIsObserved() {
          // Arrange
          var expected = 11;
          // Act
          var actual = FixtureApplication.integrationValue();
          // Assert
          assertEquals(expected, actual);
        }
        """);
  }

  private String preparedValidationTools(String pom) throws Exception {
    // This bounded workload consumes the same checked tool classes that it stages.
    var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
        new ByteArrayInputStream(pom.getBytes(StandardCharsets.UTF_8)));
    var xpath = XPathFactory.newInstance().newXPath();
    var prefix = "/project/build/plugins/plugin[artifactId='exec-maven-plugin']/executions/execution";
    var admission = (Element) xpath.evaluate(prefix + "[id='admit-default-lifecycle']/configuration", document, XPathConstants.NODE);
    var source = admission.getElementsByTagName("commandlineArgs").item(0);
    if (source == null || !source.getTextContent().equals("config/build/org/aquilagraph/build/BuildAdmission.java default")) {
      throw new IllegalStateException("The fixture requires the delivered lifecycle admission command");
    }
    admission.removeChild(source);
    var arguments = document.createElement("arguments");
    for (var value : List.of("--class-path", build.tools().resolve("target/build-tool-classes").toString(),
        "org.aquilagraph.build.BuildAdmission", "default")) {
      arguments.appendChild(argument(arguments, value));
    }
    admission.appendChild(arguments);
    for (var id : List.of("capture-unit-validation", "reconcile-integration-validation")) {
      var checkpoint = (Element) xpath.evaluate(prefix + "[id='" + id + "']/configuration/arguments", document, XPathConstants.NODE);
      var entry = checkpoint.getElementsByTagName("argument").item(0);
      if (entry == null || !entry.getTextContent().endsWith("/CompleteValidationCheckpoint.java")) {
        throw new IllegalStateException("The fixture requires the delivered validation checkpoint: " + id);
      }
      entry.setTextContent("org.aquilagraph.build.CompleteValidationCheckpoint");
      checkpoint.insertBefore(argument(checkpoint, "--class-path"), entry);
      checkpoint.insertBefore(argument(checkpoint, build.root().resolve("target/build-tool-classes").toString()), entry);
    }
    return xml(document.getDocumentElement());
  }

  private static Element argument(Element parent, String value) {
    var result = parent.getOwnerDocument().createElement("argument");
    result.setTextContent(value);
    return result;
  }

  IsolatedBuild build() {
    return build;
  }

  void source(String name, String annotations, String body) throws Exception {
    integration.source(name, annotations, body);
  }

  void source(String name, String imports, String annotations, String body) throws Exception {
    integration.source(name, imports, annotations, body);
  }

  Result run(String... flags) throws Exception {
    return retain(build.run("verify-all", flags));
  }

  Result retain(Result result) throws Exception {
    var output = Files.createDirectories(build.tools().resolve("target/implementation/complete-validation/controls"));
    var id = UUID.randomUUID().toString();
    Files.writeString(output.resolve(id + ".log"), result.output());
    Files.writeString(output.resolve(id + ".exit"), Integer.toString(result.exitCode()));
    var directory = build.root().resolve("target/complete-validation");
    if (Files.isDirectory(directory)) {
      try (var entries = Files.list(directory)) {
        for (var entry : entries.filter(Files::isDirectory).toList()) {
          var record = entry.resolve("result.properties");
          if (Files.isRegularFile(record)) {
            Files.copy(record, output.resolve(id + ".result.properties"));
          }
        }
      }
    }
    return result;
  }

  void requireDocker() throws Exception {
    var pom = build.root().resolve("pom.xml");
    var contents = Files.readString(pom);
    var position = contents.indexOf("<executions>", contents.indexOf("<artifactId>exec-maven-plugin</artifactId>"));
    contents = contents.substring(0, position) + contents.substring(position).replaceFirst("<executions>",
        "<executions>" + Matcher.quoteReplacement(build.executions("require-docker-engine")));
    Files.writeString(pom, contents);
  }

  ProcessBuilder dockerProbe(String endpoint) throws Exception {
    var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(build.root().resolve("pom.xml").toFile());
    var configuration = (Element) XPathFactory.newInstance().newXPath().evaluate(
        "/project/build/plugins/plugin[artifactId='exec-maven-plugin']/executions/execution[id='require-docker-engine']/configuration",
        document, XPathConstants.NODE);
    var command = new ArrayList<String>();
    command.add(configuration.getElementsByTagName("executable").item(0).getTextContent());
    var arguments = configuration.getElementsByTagName("arguments").item(0).getChildNodes();
    for (var index = 0; index < arguments.getLength(); index++) {
      if (arguments.item(index) instanceof Element argument) {
        command.add(argument.getTextContent());
      }
    }
    var builder = new ProcessBuilder(command).directory(build.root().toFile()).redirectErrorStream(true);
    builder.environment().remove("DOCKER_CONTEXT");
    builder.environment().put("DOCKER_HOST", endpoint);
    return IsolatedBuild.isolate(builder);
  }

  Path resultDirectory() throws Exception {
    try (var files = Files.list(build.root().resolve("target/complete-validation"))) {
      return files.filter(Files::isDirectory).findFirst().orElseThrow();
    }
  }

  void verificationAction(String target) throws Exception {
    var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(build.root().resolve("pom.xml").toFile());
    var plugin = (Element) XPathFactory.newInstance().newXPath().evaluate(
        "/project/build/plugins/plugin[artifactId='maven-antrun-plugin']", document, XPathConstants.NODE);
    var fragment = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(
        ("<execution><id>control-observation</id><phase>verify</phase><goals><goal>run</goal></goals><configuration><target>" + target
            + "</target></configuration></execution>").getBytes(StandardCharsets.UTF_8)));
    plugin.getElementsByTagName("executions").item(0).appendChild(document.importNode(fragment.getDocumentElement(), true));
    Files.writeString(build.root().resolve("pom.xml"), xml(document.getDocumentElement()));
  }

  private Element plugin(String artifact) throws Exception {
    var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(build.tools().resolve("pom.xml").toFile());
    var selected = (Element) XPathFactory.newInstance().newXPath().evaluate(
        "/project/build/plugins/plugin[artifactId='" + artifact + "']", document, XPathConstants.NODE);
    var text = xml(selected);
    var properties = document.getElementsByTagName("properties").item(0).getChildNodes();
    for (var index = 0; index < properties.getLength(); index++) {
      var property = properties.item(index);
      if (property.getNodeName().endsWith(".version")) {
        text = text.replace("${" + property.getNodeName() + "}", property.getTextContent());
      }
    }
    text = text.replace("${build.tools.outputDirectory}", IsolatedBuild.xml(build.tools().resolve("target/build-tool-classes").toString()));
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(
        text.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
  }

  private static String xml(Element element) throws Exception {
    var transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    var result = new StringWriter();
    transformer.transform(new DOMSource(element), new StreamResult(result));
    return result.toString();
  }
}
