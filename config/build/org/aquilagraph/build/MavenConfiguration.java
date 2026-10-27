package org.aquilagraph.build;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Effective project, environment and settings inputs cannot override the owned operation. */
final class MavenConfiguration {
  private MavenConfiguration() {
  }

  static void validate(BuildRequest request, Map<String, String> environment) throws IOException {
    if (Runtime.version().feature() != BuildExecution.REQUIRED_JAVA_FEATURE) {
      throw new IllegalArgumentException("Local build execution requires a JDK 25 launcher");
    }
    CompilationRequest.environmentArguments(environment);
    for (var name : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "MAVEN_CONFIG", "MAVEN_DEBUG_OPTS")) {
      if (!environment.getOrDefault(name, "").isBlank()) {
        throw new IllegalArgumentException("Unsupported influencing environment: " + name);
      }
    }
    for (var flag : ArgumentWords.parse(environment.getOrDefault("MAVEN_ARGS", ""))) {
      validateFlag(request.operation(), flag, "MAVEN_ARGS");
    }
    var base = environment.getOrDefault("MAVEN_BASEDIR", "");
    if (!base.isBlank() && !Path.of(base).toRealPath().equals(request.root())) {
      throw new IllegalArgumentException("MAVEN_BASEDIR differs from the owned project root");
    }
    validateJvmOptions(environment.getOrDefault("MAVEN_OPTS", ""));
    var jvmConfiguration = request.root().resolve(".mvn/jvm.config");
    if (Files.exists(jvmConfiguration)) {
      validateJvmOptions(Files.readString(jvmConfiguration));
    }
    var mavenConfiguration = request.root().resolve(".mvn/maven.config");
    if (Files.exists(mavenConfiguration)) {
      for (var line : Files.readAllLines(mavenConfiguration)) {
        var flag = line.strip();
        if (!flag.isEmpty() && !flag.startsWith("#")) {
          validateFlag(request.operation(), flag, ".mvn/maven.config");
        }
      }
    }
    if (Files.exists(request.root().resolve(".mvn/extensions.xml"))) {
      throw new IllegalArgumentException("Unmodeled Maven core extensions cannot be admitted by this local build");
    }
    for (var settings : settings(request, environment)) {
      validateSettings(settings, request.operation());
    }
  }

  static void validateSettings(Path settings, BuildOperation operation) throws IOException {
    if (!Files.isRegularFile(settings)) {
      return;
    }
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    try {
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      var document = factory.newDocumentBuilder().parse(settings.toFile());
      var active = document.getElementsByTagNameNS("*", "activeProfile");
      for (var index = 0; index < active.getLength(); index++) {
        validateFlag(operation, "-P" + active.item(index).getTextContent().strip(), "Maven settings active profile");
      }
      var profiles = document.getElementsByTagNameNS("*", "profile");
      for (var index = 0; index < profiles.getLength(); index++) {
        var properties = ((Element) profiles.item(index)).getElementsByTagNameNS("*", "properties");
        for (var group = 0; group < properties.getLength(); group++) {
          var entries = properties.item(group).getChildNodes();
          for (var entry = 0; entry < entries.getLength(); entry++) {
            var property = entries.item(entry);
            if (property.getNodeType() == Node.ELEMENT_NODE) {
              validateFlag(operation, "-D" + property.getLocalName() + "=" + property.getTextContent().strip(), "Maven settings property");
            }
          }
        }
      }
    } catch (ParserConfigurationException | SAXException exception) {
      throw new IOException("Maven settings could not be inspected: " + settings, exception);
    }
  }

  private static void validateFlag(BuildOperation operation, String flag, String origin) {
    try {
      BuildRequest.validateFlag(operation, flag);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unsupported " + origin + ": " + exception.getMessage(), exception);
    }
  }

  private static List<Path> settings(BuildRequest request, Map<String, String> environment) throws IOException {
    var result = new ArrayList<Path>();
    var user = Path.of(System.getProperty("user.home"), ".m2");
    result.add(user.resolve("settings.xml"));
    var wrapperHome = Path.of(environment.getOrDefault("MAVEN_USER_HOME", user.toString()));
    result.add(wrapperHome.resolve("settings.xml"));
    for (var name : List.of("MAVEN_HOME", "M2_HOME")) {
      var home = environment.getOrDefault(name, "");
      if (!home.isBlank()) {
        result.add(Path.of(home).resolve("conf/settings.xml"));
      }
    }
    var executableDirectory = request.maven().getParent();
    if (executableDirectory != null && Path.of("bin").equals(executableDirectory.getFileName())) {
      var home = executableDirectory.getParent();
      if (home == null) {
        throw new IOException("The selected Maven bin directory has no installation root");
      }
      result.add(home.resolve("conf/settings.xml"));
    }
    var wrapperProperties = request.toolRoot().resolve(".mvn/wrapper/maven-wrapper.properties");
    if (Files.isRegularFile(wrapperProperties)) {
      var url = BuildFiles.required(BuildFiles.read(wrapperProperties), "distributionUrl");
      var artifact = Path.of(URI.create(url).getPath()).getFileName();
      if (artifact == null) {
        throw new IOException("The Maven wrapper distribution requires an artifact filename");
      }
      var distribution = artifact.toString().replaceFirst("-bin\\.(zip|tar\\.gz)$", "");
      var distributions = wrapperHome.resolve("wrapper/dists").resolve(distribution);
      if (Files.isDirectory(distributions)) {
        try (var entries = Files.newDirectoryStream(distributions)) {
          for (var entry : entries) {
            result.add(entry.resolve("conf/settings.xml"));
          }
        }
      }
    }
    return result.stream().distinct().toList();
  }

  private static void validateJvmOptions(String text) {
    for (var option : text.strip().split("\\s+", -1)) {
      if (!option.isEmpty() && !option.matches("-Xm[sx][1-9][0-9]*[kKmMgG]") && !"-Djava.io.tmpdir=target".equals(option)) {
        throw new IllegalArgumentException(
            "Unsupported Maven JVM option; only memory budgets and the project temporary directory are supported");
      }
    }
  }
}
