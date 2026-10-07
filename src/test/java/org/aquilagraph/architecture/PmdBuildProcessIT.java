package org.aquilagraph.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
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
    assertFalse(result.output().contains("[WARNING]"), result.output());
    var expectedRuntime = Path.of(System.getProperty("java.home"), "lib", "jrt-fs.jar").toRealPath();
    var classpath = analysisClasspath(fixture);
    assertTrue(Arrays.stream(classpath.split(Pattern.quote(File.pathSeparator)))
        .map(Path::of).anyMatch(path -> path.toAbsolutePath().normalize().equals(expectedRuntime)), classpath);
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
    assertTrue(result.output().contains("PMD processing errors"), result.output());
  }

  private Path fixture() throws Exception {
    var fixture = Files.createTempDirectory(temporaryDirectory, "pmd-build-");
    var pluginVersion = property("maven-pmd-plugin.version");
    var toolchainVersion = property("maven-toolchains-plugin.version");
    Files.writeString(fixture.resolve("pom.xml"), """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>validation</groupId><artifactId>analysis-rules</artifactId><version>1.0</version>
          <properties><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
          <build><plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId><artifactId>maven-toolchains-plugin</artifactId>
              <version>%s</version>
              <configuration><version>[25,26)</version><useJdk>Never</useJdk></configuration>
            </plugin>
            <plugin>
              <groupId>org.aquilagraph.build</groupId><artifactId>pmd-runtime-maven-plugin</artifactId>
              <version>%s</version>
              <configuration>
                <targetJdk>25</targetJdk><includeTests>false</includeTests>
                <rulesets><ruleset>ruleset.xml</ruleset></rulesets>
                <failOnViolation>true</failOnViolation><skipPmdError>false</skipPmdError>
                <linkXRef>false</linkXRef><minimumTokens>100</minimumTokens>
              </configuration>
            </plugin>
          </plugins></build>
        </project>
        """.formatted(toolchainVersion, pluginVersion));
    Files.writeString(fixture.resolve("ruleset.xml"), """
        <ruleset name="Unused values" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
          <description>Detect unused local variables.</description>
          <rule ref="category/java/bestpractices.xml/UnusedLocalVariable" />
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
    command.addAll(Arrays.asList("-B", "--no-transfer-progress", "-f", fixture.resolve("pom.xml").toString(),
        "-Dmaven.repo.local=" + root.resolve("target/maven-repository"),
        "toolchains:select-jdk-toolchain",
        "org.aquilagraph.build:pmd-runtime-maven-plugin:" + property("maven-pmd-plugin.version") + ":" + goal));
    var log = fixture.resolve("build.log");
    var process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        throw new IOException("Maven analysis did not finish within 60 seconds: " + Files.readString(log));
      }
      return new Result(process.exitValue(), Files.readString(log));
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
      }
    }
  }

  private static String analysisClasspath(Path fixture) throws Exception {
    var originalPlugin = Path.of("target/maven-repository/org/apache/maven/plugins/maven-pmd-plugin/3.28.0/maven-pmd-plugin-3.28.0.jar");
    try (var loader = new URLClassLoader(new URL[] {originalPlugin.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        var input = new ObjectInputStream(Files.newInputStream(fixture.resolve("target/pmd/pmdrequest.bin"))) {
          @Override
          protected Class<?> resolveClass(ObjectStreamClass descriptor) throws ClassNotFoundException {
            return Class.forName(descriptor.getName(), false, loader);
          }
        }) {
      var request = input.readObject();
      return (String) request.getClass().getMethod("getAuxClasspath").invoke(request);
    }
  }

  private static String property(String name) throws Exception {
    var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("pom.xml").toFile());
    return document.getElementsByTagName(name).item(0).getTextContent().strip();
  }

  private static int reportCount(Path report, String element) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report.toFile()).getElementsByTagName(element).getLength();
  }

  private record Result(int exitCode, String output) {
  }
}
