package org.aquilagraph.build;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.w3c.dom.Element;

/** Verifies editor source ownership and portable build-path resources. */
public final class EditorModelValidation {
  private static final String ROOT_VARIABLE = "PARENT-3-PROJECT_LOC/";

  private EditorModelValidation() {
  }

  /**
   * Checks the generated editor projects after canonical product compilation.
   *
   * @param arguments
   *          the repository root
   * @throws IOException
   *           if an editor project cannot resolve its sources or dependencies
   */
  public static void main(String[] arguments) throws IOException {
    BuildAdmission.main(new String[]{"default"});
    verify(Path.of(arguments[0]));
    System.getLogger(EditorModelValidation.class.getName()).log(Level.INFO, "Validated editor source ownership and portable classpaths");
  }

  static void verify(Path repository) throws IOException {
    var root = repository.toRealPath();
    var owners = new HashMap<Path, String>();
    try (var projects = Files.newDirectoryStream(root.resolve("target/editor"), Files::isDirectory)) {
      for (var project : projects) {
        verifyProject(root, project, owners);
      }
    }
    var missing = expectedSources(root);
    missing.removeAll(owners.keySet());
    if (!missing.isEmpty()) {
      throw new IOException("Canonical Java sources have no editor project: " + missing);
    }
  }

  private static Set<Path> expectedSources(Path root) throws IOException {
    var sources = new HashSet<Path>();
    try (var modules = Files.newDirectoryStream(root.resolve("src/main/java"), Files::isDirectory)) {
      for (var module : modules) {
        sources.add(module.toRealPath());
      }
    }
    for (var source : Set.of("src/main/observability", "src/test/java", "config/build", "config/build-checks")) {
      sources.add(root.resolve(source).toRealPath());
    }
    return sources;
  }

  private static void verifyProject(Path root, Path project, Map<Path, String> owners) throws IOException {
    var description = BuildXml.read(project.resolve(".project")).getDocumentElement();
    var name = requiredText(description, "name");
    var projectName = project.getFileName();
    if (projectName == null || !name.equals(projectName.toString())
        || !"org.eclipse.jdt.core.javanature".equals(requiredText(description, "nature"))) {
      throw new IOException("Invalid Java editor project: " + project);
    }
    var links = new HashMap<String, Path>();
    var resources = description.getElementsByTagName("link");
    for (var index = 0; index < resources.getLength(); index++) {
      var link = (Element) resources.item(index);
      var location = requiredText(link, "locationURI");
      if (!location.startsWith(ROOT_VARIABLE)) {
        throw invalidResource("Editor link must be relative to the repository", project, location);
      }
      var target = root.resolve(location.substring(ROOT_VARIABLE.length())).normalize();
      if (!target.startsWith(root) || !Files.isDirectory(target)) {
        throw invalidResource("Editor link does not resolve to a repository directory", project, target.toString());
      }
      if (links.putIfAbsent(requiredText(link, "name"), target) != null) {
        throw new IOException("Duplicate editor resource link: " + project);
      }
    }
    verifyClasspath(project, links, owners);
  }

  private static void verifyClasspath(Path project, Map<String, Path> links, Map<Path, String> owners) throws IOException {
    var entries = BuildXml.read(project.resolve(".classpath")).getElementsByTagName("classpathentry");
    for (var index = 0; index < entries.getLength(); index++) {
      var entry = (Element) entries.item(index);
      var value = entry.getAttribute("path");
      switch (entry.getAttribute("kind")) {
        case "src" -> verifySource(project, value, links, owners);
        case "lib" -> verifyLibrary(project, value, links);
        case "output" -> {
          if (!"bin".equals(value)) {
            throw new IOException("Editor compilation must use its isolated bin directory: " + project);
          }
        }
        case "con" -> {
        }
        default -> throw invalidResource("Unsupported editor classpath entry", project, value);
      }
    }
  }

  private static void verifySource(Path project, String value, Map<String, Path> links, Map<Path, String> owners) throws IOException {
    if (value.startsWith("/")) {
      var editor = Objects.requireNonNull(project.getParent(), "editor project directory");
      var referenced = editor.resolve(value.substring(1)).normalize();
      if (!editor.equals(referenced.getParent()) || !Files.isRegularFile(referenced.resolve(".project"))) {
        throw invalidResource("Missing editor project dependency", project, value);
      }
      return;
    }
    var target = resolveEntry(project, value, links);
    if (!Files.isDirectory(target)) {
      throw invalidResource("Missing editor source resource", project, target.toString());
    }
    if (owners.putIfAbsent(target.toRealPath(), project.toString()) != null) {
      throw new IOException("Java source has multiple editor owners: " + target);
    }
  }

  private static void verifyLibrary(Path project, String value, Map<String, Path> links) throws IOException {
    var target = resolveEntry(project, value, links);
    if (!Files.exists(target)) {
      throw invalidResource("Missing editor lib resource", project, target.toString());
    }
    var source = links.get("source");
    if (source != null && Files.isRegularFile(source.resolve("module-info.java")) && Files.isDirectory(target)) {
      throw invalidResource("Named editor modules require project dependencies instead of class folders", project, value);
    }
  }

  private static Path resolveEntry(Path project, String value, Map<String, Path> links) throws IOException {
    var relative = Path.of(value);
    var link = links.get(relative.getName(0).toString());
    if (relative.isAbsolute() || value.contains("\\") || link == null) {
      throw invalidResource("Editor classpath must use a portable resource link", project, value);
    }
    var target = relative.getNameCount() == 1 ? link : link.resolve(relative.subpath(1, relative.getNameCount())).normalize();
    if (!target.startsWith(link)) {
      throw invalidResource("Editor classpath leaves its resource link", project, value);
    }
    return target;
  }

  private static String requiredText(Element element, String tag) throws IOException {
    var child = element.getElementsByTagName(tag).item(0);
    if (child == null || child.getTextContent().isBlank()) {
      throw new IOException("Missing editor project field: " + tag);
    }
    return child.getTextContent().strip();
  }

  private static IOException invalidResource(String reason, Path project, String resource) {
    return new IOException(reason + ": " + project + " -> " + resource);
  }
}
