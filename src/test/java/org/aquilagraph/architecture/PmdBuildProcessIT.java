package org.aquilagraph.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.aquilagraph.testsupport.IsolatedBuild;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PmdBuildProcessIT {
  @TempDir
  Path temporaryDirectory;

  @Test
  void givenCleanJavaSource_whenPmdRuns_thenSelectedJdkIsExplicitInAnalysisClasspath() throws Exception {
    // Arrange
    var fixture = fixture();
    writeSource(fixture, "Sample", "public class Sample { public int value() { return 42; } }");

    // Act
    var result = run(fixture, "check");

    // Assert
    assertEquals(0, result.exitCode(), result.output());
    assertFalse(Pattern.compile("(?im)\\[WARNING\\]|\\bWARN\\b|falling back").matcher(result.output()).find(), result.output());
    var expectedRuntime = Path.of(System.getProperty("java.home"), "lib", "jrt-fs.jar").toRealPath();
    var classpath = Files.readString(fixture.resolve("target/pmd-classpath.txt"));
    assertTrue(Arrays.stream(classpath.split(Pattern.quote(File.pathSeparator)))
        .map(Path::of).findFirst().orElseThrow().toRealPath().equals(expectedRuntime), classpath);
    assertEquals(0, reportCount(fixture.resolve("target/pmd.xml"), "violation"));
  }

  @Test
  void givenUnusedLocalVariable_whenPmdRuns_thenViolationFailsBuild() throws Exception {
    // Arrange
    var fixture = fixture();
    writeSource(fixture, "Sample", "public class Sample { public void value() { int discarded = 42; } }");

    // Act
    var result = run(fixture, "check");

    // Assert
    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("BUILD FAILURE"), result.output());
    var report = Files.readString(fixture.resolve("target/pmd.xml"));
    assertTrue(report.contains("rule=\"UnusedLocalVariable\""), report);
    assertEquals(1, reportCount(fixture.resolve("target/pmd.xml"), "violation"));
  }

  @Test
  void givenDuplicatedMethodBodies_whenCpdRuns_thenDuplicationFailsBuild() throws Exception {
    // Arrange
    var fixture = fixture();
    var body = new StringBuilder("public int value(int input) { int result = input;");
    for (var index = 1; index <= 30; index++) {
      body.append("result += ").append(index).append(";");
    }
    body.append("return result; }");
    writeSource(fixture, "First", "public class First { " + body + " }");
    writeSource(fixture, "Second", "public class Second { " + body + " }");

    // Act
    var result = run(fixture, "cpd-check");

    // Assert
    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("BUILD FAILURE"), result.output());
    assertTrue(reportCount(fixture.resolve("target/cpd.xml"), "duplication") > 0);
  }

  @Test
  void givenMalformedSource_whenPmdRuns_thenProcessingErrorFailsBuild() throws Exception {
    // Arrange
    var fixture = fixture();
    writeSource(fixture, "Sample", "public class Sample { public void value( { }");

    // Act
    var result = run(fixture, "check");

    // Assert
    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("BUILD FAILURE"), result.output());
    assertTrue(result.output().contains("PMD processing or configuration errors"), result.output());
    assertTrue(reportCount(fixture.resolve("target/pmd.xml"), "error") > 0);
  }

  @Test
  void givenRedundantReferenceCast_whenPmdResolvesSelectedRuntimeTypes_thenTypeDependentRuleFails() throws Exception {
    // Arrange
    var fixture = fixture();
    writeSource(fixture, "Sample", "public class Sample { public CharSequence value(String input) { return (CharSequence) input; } }");

    // Act
    var result = run(fixture, "check");

    // Assert
    assertEquals(1, result.exitCode(), result.output());
    assertTrue(Files.readString(fixture.resolve("target/pmd.xml")).contains("rule=\"UnnecessaryCast\""), result.output());
  }

  @Test
  void givenEmptySourceInventory_whenPmdRuns_thenMissingInputFailsBuild() throws Exception {
    // Arrange
    var fixture = fixture();
    Files.createDirectories(fixture.resolve("src/main/java"));

    // Act
    var result = run(fixture, "check");

    // Assert
    assertEquals(1, result.exitCode(), result.output());
    assertTrue(result.output().contains("nonempty production source inventory"), result.output());
  }

  private Path fixture() throws Exception {
    var fixture = Files.createTempDirectory(temporaryDirectory, "pmd-build-");
    Files.writeString(fixture.resolve("ruleset.xml"), """
        <ruleset name="Unused values" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
          <description>Detect unused local variables and type-dependent redundant casts.</description>
          <rule ref="category/java/bestpractices.xml/UnusedLocalVariable" />
          <rule ref="category/java/codestyle.xml/UnnecessaryCast" />
        </ruleset>
        """);
    return fixture;
  }

  private static void writeSource(Path fixture, String name, String source) throws IOException {
    var root = Files.createDirectories(fixture.resolve("src/main/java"));
    Files.writeString(root.resolve(name + ".java"), source);
  }

  private static Result run(Path directory, String goal) throws Exception {
    var fixture = new IsolatedBuild(directory);
    Files.copy(fixture.tools().resolve("target/build-inputs/analysis.classpath"), fixture.root().resolve("target/analysis.classpath"));
    var dependencies = new StringBuilder("<dependencies>");
    for (var artifact : new String[][]{{"org.apache.ant", "ant", "ant.version"}, {"org.apache.ant", "ant-launcher", "ant.version"},
        {"net.sourceforge.pmd", "pmd-ant", "pmd.version"}, {"net.sourceforge.pmd", "pmd-java", "pmd.version"},
        {"net.sourceforge.pmd", "pmd-cli", "pmd.version"}}) {
      dependencies.append("<dependency><groupId>").append(artifact[0]).append("</groupId><artifactId>").append(artifact[1])
          .append("</artifactId><version>").append(fixture.version(artifact[2])).append("</version></dependency>");
    }
    dependencies.append("</dependencies>");
    var arguments = goal.equals("cpd-check") ? """
        <argument>net.sourceforge.pmd.cli.PmdCli</argument><argument>cpd</argument><argument>--language</argument><argument>java</argument>
        <argument>--minimum-tokens</argument><argument>100</argument><argument>--dir</argument><argument>${project.basedir}/src/main/java</argument>
        <argument>--encoding</argument><argument>UTF-8</argument><argument>--format</argument><argument>xml</argument>
        <argument>--report-file</argument><argument>${project.build.directory}/cpd.xml</argument>
        """ : """
        <argument>org.apache.tools.ant.launch.Launcher</argument><argument>-f</argument><argument>%s</argument>
        <argument>-Danalysis.sourceDirectory=${project.basedir}/src/main/java</argument><argument>-Danalysis.ruleset=${project.basedir}/ruleset.xml</argument>
        <argument>-Danalysis.outputDirectory=${project.build.directory}</argument><argument>-Danalysis.classpathFile=${project.build.directory}/analysis.classpath</argument>
        <argument>analyze</argument>
        """.formatted(IsolatedBuild.xml(fixture.tools().resolve("config/pmd/analysis.xml").toString()));
    fixture.writePom("""
        <execution><id>analyze-fixture</id><phase>compile</phase><goals><goal>exec</goal></goals><configuration>
        <executable>${java.home}/bin/java</executable><includePluginDependencies>true</includePluginDependencies><addOutputToClasspath>false</addOutputToClasspath>
        <arguments><argument>-Xms128m</argument><argument>-Xmx1g</argument><argument>-cp</argument><classpath />%s</arguments>
        </configuration></execution>
        """.formatted(arguments), dependencies.toString(), "", """
        <executions><execution><id>default-compile</id><phase>none</phase></execution></executions>
        """);
    var result = fixture.run("compile");
    return new Result(result.exitCode(), result.output());
  }

  private static int reportCount(Path report, String element) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report.toFile()).getElementsByTagName(element).getLength();
  }

  private record Result(int exitCode, String output) {
  }
}
