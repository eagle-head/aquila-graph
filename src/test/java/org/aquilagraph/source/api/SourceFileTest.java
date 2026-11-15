package org.aquilagraph.source.api;

import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    var path = capturedPath(Path.of("sample/../Sample.java"));
    var content = "class Sample {}";

    // Act
    var source = SourceFile.fromContent(path, content);

    // Assert
    assertEquals(capturedPath(Path.of("Sample.java")), source.relativePath());
    assertEquals("Sample.java", source.canonicalRelativePath());
    assertEquals(content, source.content());
    assertEquals("857549eae38e7d257853af1350165d1067a49f7148b797e186d51cc8d8d9edc3", source.contentDigest());
    assertEquals("source:5b7d302b83ed255231899a484fac0ec5daf17b2311738c6a8d6eb46f7687faf7", source.sourceFileKey());
    assertEquals(source, SourceFile.fromContent(capturedPath(Path.of("Sample.java")), content));
  }

  @Test
  void givenNestedMixedCasePath_whenCanonicalPathIsQueried_thenCaseAndSlashSeparatedNamesArePreserved() {
    // Arrange
    var source = SourceFile.fromContent(capturedPath(Path.of("Upper", "nested", "É.java")), "class Example {}");

    // Act
    var path = source.canonicalRelativePath();

    // Assert
    assertEquals("Upper/nested/É.java", path);
  }

  @Test
  void givenChangedPathCaseOrContent_whenCaptured_thenSourceIdentityDistinguishesEachChange() {
    // Arrange
    var original = SourceFile.fromContent(capturedPath(Path.of("Sample.java")), "class Sample {}");

    // Act
    var renamed = SourceFile.fromContent(capturedPath(Path.of("sample.java")), original.content());
    var edited = SourceFile.fromContent(original.relativePath(), "class Sample { int value; }");

    // Assert
    assertNotEquals(original.sourceFileKey(), renamed.sourceFileKey());
    assertEquals(original.contentDigest(), renamed.contentDigest());
    assertNotEquals(original.sourceFileKey(), edited.sourceFileKey());
    assertNotEquals(original.contentDigest(), edited.contentDigest());
  }

  @Test
  void givenAbsoluteLocation_whenUsedAsASourceName_thenBothAdmissionRoutesRejectIt() {
    // Arrange
    var absolute = CapturedLocation.parse(Syntax.POSIX, "/Sample.java");

    // Act
    var produced = assertThrows(IllegalArgumentException.class, () -> SourceFile.fromContent(absolute, "class Sample {}"));
    var copied = assertThrows(IllegalArgumentException.class, () -> new SourceFile("supplied-key", absolute, "digest", ""));

    // Assert
    assertTrue(produced.getMessage().contains("relative"), produced::getMessage);
    assertEquals(produced.getMessage(), copied.getMessage());
  }

  @Test
  void givenEscapingAndPartialWindowsRoots_whenCaptured_thenTheyAreRejectedBeforeSourceConstruction() {
    // Arrange
    var names = List.of("nested/../../Sample.java", "\\Sample.java", "C:Sample.java");

    // Act
    var failures = names.stream().map(name -> assertThrows(IllegalArgumentException.class,
        () -> CapturedLocation.parse(Syntax.WINDOWS, name))).toList();

    // Assert
    assertEquals(3, failures.size());
    assertTrue(failures.stream().allMatch(failure -> failure.getMessage() != null && !failure.getMessage().isBlank()));
  }

  @Test
  void givenSuppliedRelativePathAndIdentity_whenConstructed_thenOnlyThePathIsNormalized() {
    // Arrange
    var path = capturedPath(Path.of("nested/discard/../Sample.java"));

    // Act
    var source = new SourceFile("supplied-key", path, "supplied-digest", "class Sample {}");

    // Assert
    assertEquals(capturedPath(Path.of("nested/Sample.java")), source.relativePath());
    assertEquals("nested/Sample.java", source.canonicalRelativePath());
    assertEquals("supplied-key", source.sourceFileKey());
    assertEquals("supplied-digest", source.contentDigest());
    assertEquals("class Sample {}", source.content());
  }
}
