package org.aquilagraph.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.aquilagraph.testsupport.OwnedProcess;
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

  private static Result run(Path fixture, String goal) throws Exception {
    var root = Path.of("").toAbsolutePath();
    var command = new ArrayList<String>();
    if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
      command.addAll(Arrays.asList("cmd.exe", "/d", "/c", root.resolve("mvnw.cmd").toString()));
    } else {
      command.add(root.resolve("mvnw").toString());
    }
    command.addAll(Arrays.asList("-B", "--no-transfer-progress",
        "-Dmaven.repo.local=" + root.resolve("target/maven-repository"),
        "-Danalysis.sourceDirectory=" + fixture.resolve("src/main/java"),
        "-Danalysis.ruleset=" + fixture.resolve("ruleset.xml"),
        "-Danalysis.outputDirectory=" + fixture.resolve("target"),
        "-Danalysis.classpathFile=" + fixture.resolve("target/analysis.classpath"),
        "validate", "antrun:run@prepare-analysis-inputs",
        "exec:exec@" + (goal.equals("cpd-check") ? "analyze-duplication" : "analyze-source")));
    var log = fixture.resolve("build.log");
    try (var process = OwnedProcess.start(new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()))) {
      if (!process.await(Duration.ofSeconds(60))) {
        throw new IOException("Maven analysis did not finish within 60 seconds: " + Files.readString(log));
      }
      return new Result(process.exitValue(), Files.readString(log));
    }
  }

  private static int reportCount(Path report, String element) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report.toFile()).getElementsByTagName(element).getLength();
  }

  private record Result(int exitCode, String output) {
  }
}
