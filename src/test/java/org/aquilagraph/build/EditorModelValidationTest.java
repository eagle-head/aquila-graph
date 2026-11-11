package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorModelValidationTest {
  @TempDir Path directory;

  @Test
  void givenPortableEditorProjectsInDifferentRoots_whenValidated_thenEachRootResolvesItsOwnSourcesAndLibraries() throws IOException {
    // Arrange
    var original = fixture("original");
    var relocated = fixture("relocated");

    // Act
    // Assert
    assertDoesNotThrow(() -> EditorModelValidation.verify(original));
    assertDoesNotThrow(() -> EditorModelValidation.verify(relocated));
  }

  @Test
  void givenAnAbsoluteSourceLinkFromAnotherHost_whenValidated_thenTheUnresolvedProjectIsRejected() throws IOException {
    // Arrange
    var root = fixture("foreign-source");
    var project = root.resolve("target/editor/sample/.project");
    Files.writeString(project, Files.readString(project).replace(
        "<locationURI>PARENT-3-PROJECT_LOC/src/main/java/sample</locationURI>",
        "<location>/mnt/d/Projects/foreign/src/main/java/sample</location>"));

    // Act
    var failure = assertThrows(IOException.class, () -> EditorModelValidation.verify(root));

    // Assert
    assertTrue(failure.getMessage().contains("locationURI"), failure.getMessage());
  }

  @Test
  void givenForeignPlatformLibraryPaths_whenValidated_thenNeitherCanPassAsAnEditorDependency() throws IOException {
    // Arrange
    var root = fixture("foreign-library");
    var classpath = root.resolve("target/editor/sample/.classpath");
    var portable = Files.readString(classpath);
    for (var foreign : List.of("/mnt/d/Projects/foreign/api.jar", "D:/Projects/foreign/api.jar")) {
      Files.writeString(classpath, portable.replace("libraries/api.jar", foreign));

      // Act
      var failure = assertThrows(IOException.class, () -> EditorModelValidation.verify(root));

      // Assert
      assertTrue(failure.getMessage().contains("portable resource link"), failure.getMessage());
    }
  }

  @Test
  void givenAnUnownedCanonicalModule_whenValidated_thenTheMissingEditorSourceIsReported() throws IOException {
    // Arrange
    var root = fixture("unowned");
    Files.createDirectories(root.resolve("src/main/java/another.module"));

    // Act
    var failure = assertThrows(IOException.class, () -> EditorModelValidation.verify(root));

    // Assert
    assertTrue(failure.getMessage().contains("no editor project"), failure.getMessage());
    assertTrue(failure.getMessage().contains("another.module"), failure.getMessage());
  }

  @Test
  void givenAMissingLinkedLibrary_whenValidated_thenTheBrokenClasspathIsReported() throws IOException {
    // Arrange
    var root = fixture("missing-library");
    var classpath = root.resolve("target/editor/sample/.classpath");
    Files.writeString(classpath, Files.readString(classpath).replace("libraries/api.jar", "libraries/absent.jar"));

    // Act
    var failure = assertThrows(IOException.class, () -> EditorModelValidation.verify(root));

    // Assert
    assertTrue(failure.getMessage().contains("Missing editor lib resource"), failure.getMessage());
    assertTrue(failure.getMessage().contains("absent.jar"), failure.getMessage());
  }

  @Test
  void givenTwoEditorProjectsOwningTheSameSource_whenValidated_thenTheAmbiguousImportIsRejected() throws IOException {
    // Arrange
    var root = fixture("duplicate-source");
    project(root, "duplicate", "src/main/java/sample");

    // Act
    var failure = assertThrows(IOException.class, () -> EditorModelValidation.verify(root));

    // Assert
    assertTrue(failure.getMessage().contains("multiple editor owners"), failure.getMessage());
  }

  @Test
  void givenAnEditorOutputPointingAtCanonicalClasses_whenValidated_thenSharedBuildOutputIsRejected() throws IOException {
    // Arrange
    var root = fixture("shared-output");
    var classpath = root.resolve("target/editor/sample/.classpath");
    Files.writeString(classpath, Files.readString(classpath).replace("path=\"bin\"", "path=\"classes\""));

    // Act
    var failure = assertThrows(IOException.class, () -> EditorModelValidation.verify(root));

    // Assert
    assertTrue(failure.getMessage().contains("isolated bin directory"), failure.getMessage());
  }

  private Path fixture(String name) throws IOException {
    var root = Files.createDirectories(directory.resolve(name)).toRealPath();
    Files.createDirectories(root.resolve("target/maven-repository"));
    Files.writeString(root.resolve("target/maven-repository/api.jar"), "controlled library resource");
    project(root, "sample", "src/main/java/sample");
    project(root, "tests", "src/test/java");
    project(root, "build", "config/build");
    project(root, "checks", "config/build-checks");
    return root;
  }

  @Test
  void givenAModuleProjectDependency_whenValidated_thenReferencedSourcesKeepTheirOwnProjectOwner() throws IOException {
    // Arrange
    var root = fixture("project-reference");
    project(root, "another.module", "src/main/java/another.module");
    var classpath = root.resolve("target/editor/sample/.classpath");
    Files.writeString(classpath, Files.readString(classpath).replace("</classpath>",
        "<classpathentry kind=\"src\" path=\"/another.module\"/></classpath>"));

    // Act
    // Assert
    assertDoesNotThrow(() -> EditorModelValidation.verify(root));
  }

  @Test
  void givenAMissingProjectDependency_whenValidated_thenTheIncompleteModulePathIsRejected() throws IOException {
    // Arrange
    var root = fixture("missing-project");
    var classpath = root.resolve("target/editor/sample/.classpath");
    Files.writeString(classpath, Files.readString(classpath).replace("</classpath>",
        "<classpathentry kind=\"src\" path=\"/absent.module\"/></classpath>"));

    // Act
    var failure = assertThrows(IOException.class, () -> EditorModelValidation.verify(root));

    // Assert
    assertTrue(failure.getMessage().contains("Missing editor project dependency"), failure.getMessage());
  }

  @Test
  void givenANamedModuleUsingALocalClassFolder_whenValidated_thenConflictingModuleOwnershipIsRejected() throws IOException {
    // Arrange
    var root = fixture("module-class-folder");
    Files.writeString(root.resolve("src/main/java/sample/module-info.java"), "module sample {}");
    Files.createDirectories(root.resolve("target/classes/dependency"));
    var project = root.resolve("target/editor/sample/.project");
    Files.writeString(project, Files.readString(project).replace("</linkedResources>",
        "<link><name>classes</name><locationURI>PARENT-3-PROJECT_LOC/target/classes</locationURI></link></linkedResources>"));
    var classpath = root.resolve("target/editor/sample/.classpath");
    Files.writeString(classpath, Files.readString(classpath).replace("libraries/api.jar", "classes/dependency"));

    // Act
    var failure = assertThrows(IOException.class, () -> EditorModelValidation.verify(root));

    // Assert
    assertTrue(failure.getMessage().contains("Named editor modules require project dependencies"), failure.getMessage());
  }

  private static void project(Path root, String name, String source) throws IOException {
    Files.createDirectories(root.resolve(source));
    var project = Files.createDirectories(root.resolve("target/editor").resolve(name));
    Files.writeString(project.resolve(".project"), """
        <projectDescription>
          <name>%s</name>
          <natures><nature>org.eclipse.jdt.core.javanature</nature></natures>
          <linkedResources>
            <link><name>source</name><locationURI>PARENT-3-PROJECT_LOC/%s</locationURI></link>
            <link><name>libraries</name><locationURI>PARENT-3-PROJECT_LOC/target/maven-repository</locationURI></link>
          </linkedResources>
        </projectDescription>
        """.formatted(name, source));
    Files.writeString(project.resolve(".classpath"), """
        <classpath>
          <classpathentry kind="src" path="source"/>
          <classpathentry kind="lib" path="libraries/api.jar"/>
          <classpathentry kind="output" path="bin"/>
        </classpath>
        """);
  }
}
