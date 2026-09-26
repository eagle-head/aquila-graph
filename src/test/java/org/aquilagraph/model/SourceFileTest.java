package org.aquilagraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

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
}
