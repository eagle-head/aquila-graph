package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import org.aquilagraph.identity.api.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class SourceFileTest {
  @Test
  void givenSourceContentAndNormalizedPath_whenCaptured_thenExistingContentDigestAndSourceKeyArePreserved() {
    // Arrange
    var path = Path.of("sample/../Sample.java");
    var content = "class Sample {}";

    // Act
    var source = SourceFile.fromContent(path, content);

    // Assert
    assertEquals(Path.of("Sample.java"), source.relativePath());
    assertEquals("Sample.java", source.canonicalRelativePath());
    assertEquals(content, source.content());
    assertEquals("857549eae38e7d257853af1350165d1067a49f7148b797e186d51cc8d8d9edc3", source.contentDigest());
    assertEquals("source:5b7d302b83ed255231899a484fac0ec5daf17b2311738c6a8d6eb46f7687faf7", source.sourceFileKey());
    assertEquals(source, SourceFile.fromContent(Path.of("Sample.java"), content));
  }

  @Test
  void givenNestedMixedCasePath_whenCanonicalPathIsQueried_thenCaseAndSlashSeparatedNamesArePreserved() {
    // Arrange
    var source = SourceFile.fromContent(Path.of("Upper", "nested", "É.java"), "class Example {}");

    // Act
    var path = source.canonicalRelativePath();

    // Assert
    assertEquals("Upper/nested/É.java", path);
  }

  @Test
  void givenChangedPathCaseOrContent_whenCaptured_thenSourceIdentityDistinguishesEachChange() {
    // Arrange
    var original = SourceFile.fromContent(Path.of("Sample.java"), "class Sample {}");

    // Act
    var renamed = SourceFile.fromContent(Path.of("sample.java"), original.content());
    var edited = SourceFile.fromContent(original.relativePath(), "class Sample { int value; }");

    // Assert
    assertNotEquals(original.sourceFileKey(), renamed.sourceFileKey());
    assertEquals(original.contentDigest(), renamed.contentDigest());
    assertNotEquals(original.sourceFileKey(), edited.sourceFileKey());
    assertNotEquals(original.contentDigest(), edited.contentDigest());
  }

  @Test
  void givenAbsoluteOrEscapingPath_whenCaptured_thenSourceOwnershipIsRejected() {
    // Arrange
    var absolute = Path.of("Sample.java").toAbsolutePath();
    var escaping = Path.of("sample/../../Sample.java");

    // Act
    var absoluteFailure = assertThrows(IllegalArgumentException.class, () -> SourceFile.fromContent(absolute, "class Sample {}"));
    var escapingFailure = assertThrows(IllegalArgumentException.class, () -> SourceFile.fromContent(escaping, "class Sample {}"));

    // Assert
    assertEquals("Source-file paths must be relative to the source root", absoluteFailure.getMessage());
    assertEquals(absoluteFailure.getMessage(), escapingFailure.getMessage());
  }

  @Test
  void givenRootedPath_whenCapturedOrConstructed_thenSourceOwnershipIsRejected() {
    // Arrange
    var paths = new ArrayList<Path>();
    paths.add(Path.of("/Sample.java"));
    if (FileSystems.getDefault().getSeparator().equals("\\")) {
      paths.add(Path.of("\\Sample.java"));
      paths.add(Path.of("D:Sample.java"));
      paths.add(Path.of("C:Sample.java"));
    }

    // Act and assert
    assertAll(paths.stream().<Executable>map(path -> () -> assertAll(path.toString(),
        () -> assertEquals("Source-file paths must be relative to the source root",
            assertThrows(IllegalArgumentException.class, () -> SourceFile.fromContent(path, "class Sample {}"))
                .getMessage()),
        () -> assertEquals("Source-file paths must be relative to the source root",
            assertThrows(IllegalArgumentException.class,
                () -> new SourceFile("supplied-key", path, "supplied-digest", "class Sample {}"))
                .getMessage()))));
  }

  @Test
  void givenSuppliedRelativePathAndIdentity_whenConstructed_thenOnlyThePathIsNormalized() {
    // Arrange
    var path = Path.of("nested/discard/../Sample.java");

    // Act
    var source = new SourceFile("supplied-key", path, "supplied-digest", "class Sample {}");

    // Assert
    assertEquals(Path.of("nested/Sample.java"), source.relativePath());
    assertEquals("nested/Sample.java", source.canonicalRelativePath());
    assertEquals("supplied-key", source.sourceFileKey());
    assertEquals("supplied-digest", source.contentDigest());
    assertEquals("class Sample {}", source.content());
  }
}
