package org.aquilagraph.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.CapturedLocation.RootKind;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;
import org.junit.jupiter.api.Test;

class CapturedLocationTest {
  @Test
  void givenSameTextWithDifferentSyntax_whenLocationsAreParsed_thenRootsAndContainmentKeepTheirMeanings() {
    // Arrange
    var spelling = "//Server/Share/Source";

    // Act
    var windows = CapturedLocation.parse(Syntax.WINDOWS, spelling);
    var posix = CapturedLocation.parse(Syntax.POSIX, spelling);

    // Assert
    assertEquals(spelling, windows.spelling());
    assertEquals(spelling, posix.spelling());
    assertNotEquals(windows, posix);
    assertEquals(RootKind.WINDOWS_UNC, windows.rootKind());
    assertEquals(List.of("Server", "Share"), windows.rootComponents());
    assertEquals(List.of("Source"), windows.components());
    assertEquals(RootKind.POSIX_ABSOLUTE, posix.rootKind());
    assertEquals(List.of(), posix.rootComponents());
    assertEquals(List.of("Server", "Share", "Source"), posix.components());
    assertTrue(windows.startsWith(CapturedLocation.parse(Syntax.WINDOWS, "\\\\Server\\Share")));
    assertFalse(windows.startsWith(CapturedLocation.parse(Syntax.WINDOWS, "//server/Share")));
    assertFalse(windows.startsWith(CapturedLocation.parse(Syntax.WINDOWS, "//Server/Share/Sour")));
    assertFalse(windows.startsWith(posix));
  }

  @Test
  void givenMixedWindowsSeparators_whenNamesAreNormalizedAndResolved_thenSpellingAndCaseRemainExact() {
    // Arrange
    var root = CapturedLocation.parse(Syntax.WINDOWS, "C:/Projects\\Sources");
    var relative = CapturedLocation.parse(Syntax.WINDOWS, "pkg/other/../.\\Main.java");

    // Act
    var resolved = root.resolve(relative);

    // Assert
    assertEquals("pkg\\Main.java", relative.spelling());
    assertEquals("pkg/Main.java", relative.canonicalRelativeName());
    assertEquals("C:/Projects\\Sources\\pkg\\Main.java", resolved.spelling());
    assertEquals(RootKind.WINDOWS_DRIVE, resolved.rootKind());
    assertEquals(List.of("C"), resolved.rootComponents());
    assertEquals(List.of("Projects", "Sources", "pkg", "Main.java"), resolved.components());
    assertTrue(resolved.startsWith(root));
    assertNotEquals(resolved, CapturedLocation.parse(Syntax.WINDOWS, "C:/Projects/Sources/pkg/Main.java"));
    assertNotEquals(resolved, CapturedLocation.parse(Syntax.WINDOWS, "c:/Projects\\Sources\\pkg\\Main.java"));
    assertEquals(resolved, CapturedLocation.parse(Syntax.WINDOWS, resolved.spelling()));
  }

  @Test
  void givenInvalidRootsAndEscapingNames_whenParsedOrResolved_thenLocationsAreRefused() {
    // Arrange
    var root = CapturedLocation.parse(Syntax.POSIX, "/sources");

    // Act and assert
    assertThrows(IllegalArgumentException.class, () -> CapturedLocation.parse(Syntax.POSIX, "a/../../b"));
    assertThrows(IllegalArgumentException.class, () -> CapturedLocation.parse(Syntax.POSIX, "/../b"));
    assertThrows(IllegalArgumentException.class, () -> CapturedLocation.parse(Syntax.WINDOWS, "C:relative"));
    assertThrows(IllegalArgumentException.class, () -> CapturedLocation.parse(Syntax.WINDOWS, "/root-without-drive"));
    assertThrows(IllegalArgumentException.class, () -> CapturedLocation.parse(Syntax.WINDOWS, "//server/"));
    assertThrows(IllegalArgumentException.class, () -> CapturedLocation.parse(Syntax.WINDOWS, "//server/share/../b"));
    assertThrows(IllegalArgumentException.class, () -> root.resolve(CapturedLocation.parse(Syntax.POSIX, "/foreign")));
    assertThrows(IllegalArgumentException.class, () -> root.resolve(CapturedLocation.parse(Syntax.WINDOWS, "relative")));
    assertEquals(root, root.resolve(CapturedLocation.parse(Syntax.POSIX, "local/..")));
  }

  @Test
  void givenWindowsSeparatorVariantsAndPosixBackslashNames_whenSnapshotsAreCaptured_thenOnlyCanonicalNamesCollide() {
    // Arrange
    var windowsRoot = CapturedLocation.parse(Syntax.WINDOWS, "C:/sources");
    var posixRoot = CapturedLocation.parse(Syntax.POSIX, "/sources");
    var posixFileWithBackslash = source(Syntax.POSIX, "pkg\\Main.java");
    var posixNestedFile = source(Syntax.POSIX, "pkg/Main.java");
    var windowsFileWithBackslash = source(Syntax.WINDOWS, "pkg\\Main.java");
    var windowsFileWithSlash = source(Syntax.WINDOWS, "pkg/Main.java");

    // Act
    var snapshot = InputSnapshot.fromSources(posixRoot, List.of(posixFileWithBackslash, posixNestedFile));

    // Assert
    assertEquals(List.of("pkg/Main.java", "pkg\\Main.java"), snapshot.sourceFiles().stream().map(SourceFile::canonicalRelativePath).toList());
    assertNotEquals(posixFileWithBackslash.sourceFileKey(), posixNestedFile.sourceFileKey());
    assertEquals(windowsFileWithBackslash.sourceFileKey(), windowsFileWithSlash.sourceFileKey());
    assertThrows(IllegalArgumentException.class,
        () -> InputSnapshot.fromSources(windowsRoot, List.of(windowsFileWithSlash, windowsFileWithBackslash)));
    assertThrows(IllegalArgumentException.class, () -> InputSnapshot.fromSources(windowsRoot, List.of(posixNestedFile)));
  }

  @Test
  void givenAColonInAPosixModuleMember_whenAConfiguredDependencyAdmitsIt_thenTheCapturedSyntaxGovernsTheMember() {
    // Arrange
    var posix = CapturedLocation.parse(Syntax.POSIX, "/dependencies");
    var windows = CapturedLocation.parse(Syntax.WINDOWS, "C:/dependencies");
    var candidate = new ModuleCandidate("a:module.jar", "named.module", Optional.of("named.module"), ModuleNameOrigin.DESCRIPTOR);
    var semantics = new DependencySemantics(DependencySemantics.State.MODULES, List.of(candidate));

    // Act
    var dependency = new DependencySnapshot(posix, DependencyKind.DIRECTORY, 12, 1, "physical-fingerprint", semantics);

    // Assert
    assertEquals(List.of(candidate), dependency.semantics().modules());
    assertThrows(IllegalArgumentException.class,
        () -> new DependencySnapshot(windows, DependencyKind.DIRECTORY, 12, 1, "physical-fingerprint", semantics));
  }

  private static SourceFile source(Syntax syntax, String path) {
    return SourceFile.fromContent(CapturedLocation.parse(syntax, path), "class Main {}\n");
  }
}
