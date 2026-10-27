package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.aquilagraph.testsupport.IsolatedBuild;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

@Tag("process")
class StrictBuildToolProcessIT {
  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path directory;

  @Test
  void givenMalformedWarningAndNullnessFaults_whenTheDeliveredToolCompilerRuns_thenEveryRequiredControlRejectsItsFault() throws Exception {
    // Arrange
    var faults = List.of(
        new Fault("syntax", "final class InvalidTool { void value( { }", "error:"),
        new Fault("warning", "final class InvalidTool { String value() { return (String) \"value\"; } }", "warning:"),
        new Fault("nullness", "final class InvalidTool { String value() { return null; } }", "[NullAway]"));

    // Act
    for (var fault : faults) {
      var fixture = new IsolatedBuild(Files.createDirectories(directory.resolve(fault.name())));
      var sources = Files.createDirectories(fixture.root().resolve("config/build/org/aquilagraph/build"));
      var implementation = sources.resolve("InvalidTool.java");
      var metadata = Files.createDirectories(fixture.root().resolve("config/build-checks/org/aquilagraph/build")).resolve("package-info.java");
      Files.writeString(implementation, "package org.aquilagraph.build;\n" + fault.source());
      Files.writeString(metadata, """
          @NullMarked
          package org.aquilagraph.build;
          import org.jspecify.annotations.NullMarked;
          """);
      var arguments = Files.readAllLines(fixture.tools().resolve("target/build-inputs/build-tools.args"));
      var inputs = Files.createDirectories(fixture.root().resolve("target/build-inputs")).resolve("build-tools.args");
      Files.writeString(inputs, String.join("\n", arguments.subList(0, 4)) + "\n\"" + implementation.toString().replace('\\', '/')
          + "\"\n\"" + metadata.toString().replace('\\', '/') + "\"\n");
      fixture.writePom(compilerExecution(fixture), "", "", """
          <executions><execution><id>default-compile</id><phase>none</phase></execution></executions>
          """);
      var result = fixture.run("compile");

      // Assert
      assertNotEquals(0, result.exitCode(), result.output());
      assertTrue(result.output().contains(fault.diagnostic()), result.output());
    }
  }

  private static String compilerExecution(IsolatedBuild fixture) throws Exception {
    var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fixture.tools().resolve("pom.xml").toFile());
    var executions = document.getElementsByTagName("execution");
    for (var index = 0; index < executions.getLength(); index++) {
      var execution = (Element) executions.item(index);
      var identifiers = execution.getElementsByTagName("id");
      if (identifiers.getLength() > 0 && identifiers.item(0).getTextContent().equals("compile-build-tools")) {
        var copy = (Element) execution.cloneNode(true);
        copy.getElementsByTagName("executable").item(0).setTextContent(IsolatedBuild.java().getParent()
            .resolve(IsolatedBuild.windows() ? "javac.exe" : "javac").toString());
        var result = new StringWriter();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(copy), new StreamResult(result));
        return result.toString().replaceFirst("<\\?xml[^?]*\\?>", "")
            .replace("${build.tools.outputDirectory}", "${project.build.directory}/classes");
      }
    }
    throw new IllegalStateException("The delivered strict tool compiler execution is missing");
  }

  private record Fault(String name, String source, String diagnostic) {
  }
}
