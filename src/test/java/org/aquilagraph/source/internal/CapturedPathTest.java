package org.aquilagraph.source.internal;

import org.aquilagraph.source.internal.CapturedPaths.Syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.List;
import java.util.Objects;
import javax.tools.SimpleJavaFileObject;
import javax.tools.JavaFileObject.Kind;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.DependencySnapshot;
import org.aquilagraph.identity.api.DependencyKind;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SourceInput.Snapshot;
import org.junit.jupiter.api.Test;

class CapturedPathTest {
  @Test
  void givenRetainedSnapshotsFromEachRootFamily_whenTheFrontendReusesTheirContent_thenCompleteSourceValuesRemainAvailable() {
    // Arrange
    var roots = List.of("D:\\retained-source", "\\\\server\\share\\retained-source", "/retained-source");
    var syntaxes = List.of(Syntax.WINDOWS, Syntax.WINDOWS, Syntax.POSIX);

    // Act and Assert
    for (var index = 0; index < roots.size(); index++) {
      var fileSystem = new CapturedFileSystem(syntaxes.get(index));
      var source = SourceFile.fromContent(fileSystem.getPath("Example.java"), "class Example {}");
      var snapshot = InputSnapshot.fromSources(fileSystem.getPath(roots.get(index)), List.of(source));
      var restored = (InputSnapshot) roundTrip(snapshot);
      var request = new AstProductionRequest(new Snapshot(restored), CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
          AstProfile.JAVA_SOURCE_FIDELITY);
      var result = new JavaFrontend(new JavaSourceLoader()).produce(request).result().orElseThrow();
      assertEquals(AstCompletion.COMPLETE, result.completion());
      assertEquals(restored, result.snapshot());
      assertTrue(result.program().types().stream().anyMatch(type -> "Example".equals(type.qualifiedName())));
    }
  }

  @Test
  void givenTheHostSyntax_whenCapturedMatchersReadStandardGlobOperators_thenTheyAgreeWithTheJdk() {
    // Arrange
    var nativeSystem = FileSystems.getDefault();
    var syntax = "\\".equals(nativeSystem.getSeparator()) ? Syntax.WINDOWS : Syntax.POSIX;
    var patterns = List.of("[a&&b]", "[[]", "[a-c]", "[!a-c]", "[^a]", "{a,b}", "*", "?", "\\{", "**/*.java", "source/*.java");
    var names = List.of("a", "b", "c", "A", "&", "[", "^", "{", "source/Example.java", "source/nested/Example.java");

    // Act and Assert
    try (var captured = new CapturedFileSystem(syntax)) {
      for (var pattern : patterns) {
        var expected = nativeSystem.getPathMatcher("glob:" + pattern);
        var actual = captured.getPathMatcher("glob:" + pattern);
        for (var name : names) {
          assertEquals(expected.matches(nativeSystem.getPath(name)), actual.matches(captured.getPath(name)), pattern + " -> " + name);
        }
      }
    }
  }

  @Test
  void givenSnapshotAndIndependentSourceCopies_whenEachFrameIsDecoded_thenAllCopiesKeepTheirCapturedPathSyntax() {
    // Arrange
    var syntaxes = List.of(Syntax.WINDOWS, Syntax.POSIX);
    var roots = List.of("D:\\source", "/source");
    var names = List.of("nested\\Example.java", "C:Example.java");

    // Act and Assert
    for (var index = 0; index < syntaxes.size(); index++) {
      var fileSystem = new CapturedFileSystem(syntaxes.get(index));
      var source = SourceFile.fromContent(fileSystem.getPath(names.get(index)), "class Example {}");
      var snapshot = InputSnapshot.fromSources(fileSystem.getPath(roots.get(index)), List.of(source));
      var decoded = (List<?>) roundTrip(List.of(snapshot, source));
      var restored = (InputSnapshot) decoded.getFirst();
      assertEquals(roots.get(index), restored.sourceRoot().toString());
      assertEquals(names.get(index), restored.sourceFiles().getFirst().relativePath().toString());
      assertEquals(restored.sourceFiles().getFirst(), decoded.get(1));
      assertEquals(restored.sourceRoot().resolve(names.get(index)),
          restored.sourceRoot().resolve(restored.sourceFiles().getFirst().relativePath()));
    }
  }

  @Test
  void givenForeignCompilerAndDependencyLocations_whenRetainedValuesAreDecoded_thenAbsoluteRootsRemainExact() {
    // Arrange
    var windows = new CapturedFileSystem(Syntax.WINDOWS);
    var posix = new CapturedFileSystem(Syntax.POSIX);
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25,
        List.of(windows.getPath("D:\\libraries\\types.jar")), List.of(posix.getPath("/libraries/modules")), List.of());
    var dependency = new DependencySnapshot(windows.getPath("\\\\server\\share\\types.jar"), DependencyKind.MISSING, 0, 0, "missing");

    // Act
    var decoded = (List<?>) roundTrip(List.of(configuration, dependency));
    var restored = (CompilerConfiguration) decoded.getFirst();

    // Assert
    assertEquals("D:\\libraries\\types.jar", restored.classPath().getFirst().toString());
    assertEquals("/libraries/modules", restored.modulePath().getFirst().toString());
    assertEquals("\\\\server\\share\\types.jar", ((DependencySnapshot) decoded.get(1)).location().toString());
    assertTrue(restored.classPath().getFirst().isAbsolute());
    assertTrue(restored.modulePath().getFirst().isAbsolute());
  }

  @Test
  void givenReadOnlyCapturedFilesystem_whenWritesOrClosedAccessAreRequested_thenItsLifetimeContractIsEnforced() throws Exception {
    // Arrange
    var fileSystem = new CapturedFileSystem(Syntax.POSIX);
    var path = fileSystem.getPath("/source/Example.java");

    // Act and Assert
    assertTrue(Files.isSameFile(path, path));
    assertThrows(ReadOnlyFileSystemException.class, () -> Files.writeString(path, "content"));
    assertThrows(IllegalArgumentException.class, () -> path.getName(-1));
    assertThrows(ClassCastException.class, () -> path.compareTo(Path.of("Example.java")));
    fileSystem.close();
    assertFalse(fileSystem.isOpen());
    assertThrows(ClosedFileSystemException.class, () -> Files.readString(path));
    assertThrows(ClosedFileSystemException.class, () -> fileSystem.getPath("/another"));
  }

  private static Object roundTrip(Object value) {
    var writer = new CanonicalValueWriter("retained-path/1");
    new RetainedValueCodec(false).write(writer, value);
    var bytes = writer.toByteArray();
    var reader = new CanonicalValueReader(bytes, "retained-path/1");
    var decoded = new RetainedValueCodec(false).read(reader);
    reader.requireFinished();
    var reencoded = new CanonicalValueWriter("retained-path/1");
    new RetainedValueCodec(false).write(reencoded, decoded);
    assertArrayEquals(bytes, reencoded.toByteArray());
    return decoded;
  }
  @Test
  void givenAbsoluteLocationsFromBothSystems_whenSnapshotConstructionNormalizesThem_thenTheirCapturedRootsRemainExact() {
    // Arrange
    var roots = List.of("D:\\Projects\\retained-source", "\\\\server\\share\\retained-source", "//server/share/retained-source", "/opt/retained-source");
    var syntaxes = List.of(Syntax.WINDOWS, Syntax.WINDOWS, Syntax.WINDOWS, Syntax.POSIX);

    // Act and Assert
    for (var index = 0; index < roots.size(); index++) {
      var path = new CapturedFileSystem(syntaxes.get(index)).getPath(roots.get(index));
      var snapshot = InputSnapshot.fromSources(path, List.of());
      assertEquals(roots.get(index), snapshot.sourceRoot().toString());
      assertTrue(snapshot.sourceRoot().isAbsolute());
      assertEquals(path, path.getFileSystem().provider().getPath(path.toUri()));
      var root = Objects.requireNonNull(path.getRoot());
      assertEquals(root, root.getFileSystem().provider().getPath(root.toUri()));
    }
  }

  @Test
  void givenWindowsAndPosixNames_whenLocationsAreResolvedAndRelativized_thenTheirOwnHierarchyIsUsed() {
    // Arrange
    var windows = new CapturedFileSystem(Syntax.WINDOWS);
    var posix = new CapturedFileSystem(Syntax.POSIX);
    var first = windows.getPath("D:\\source\\nested\\..\\Example.java");
    var second = posix.getPath("/source/nested/../Example.java");

    // Act
    var normalizedWindows = first.normalize();
    var normalizedPosix = second.normalize();

    // Assert
    assertEquals("D:\\source\\Example.java", normalizedWindows.toString());
    assertEquals("/source/Example.java", normalizedPosix.toString());
    assertEquals("Example.java", windows.getPath("D:\\source").relativize(normalizedWindows).toString());
    assertEquals("Example.java", posix.getPath("/source").relativize(normalizedPosix).toString());
    assertEquals(normalizedWindows, windows.getPath("D:\\source").resolve("Example.java"));
    assertEquals(normalizedPosix, posix.getPath("/source").resolve("Example.java"));
    assertEquals("D:\\", Objects.requireNonNull(normalizedWindows.getRoot()).toString());
    assertEquals("/source", Objects.requireNonNull(normalizedPosix.getParent()).toString());
    assertTrue(normalizedWindows.endsWith("source/Example.java"));
    assertTrue(normalizedPosix.startsWith("/source"));
  }

  @Test
  void givenCapturedCaseRules_whenPathsAreCompared_thenWindowsFoldingDoesNotExpandDistinctNames() {
    // Arrange
    var windows = new CapturedFileSystem(Syntax.WINDOWS);
    var posix = new CapturedFileSystem(Syntax.POSIX);

    // Act and Assert
    assertEquals(windows.getPath("D:\\Source"), windows.getPath("d:\\source"));
    assertEquals(windows.getPath("D:\\Source").hashCode(), windows.getPath("d:\\source").hashCode());
    // Character folding must preserve the distinction from an expanding uppercase spelling.
    assertNotEquals(windows.getPath("D:\\ß"), windows.getPath("D:\\SS"));
    assertNotEquals(posix.getPath("/Source"), posix.getPath("/source"));
  }

  @Test
  void givenForeignPathMetadata_whenFilesystemAccessIsRequested_thenNoLocalFileIsOpened() {
    // Arrange
    var paths = List.of(new CapturedFileSystem(Syntax.WINDOWS).getPath("D:\\source\\Example.java"),
        new CapturedFileSystem(Syntax.WINDOWS).getPath("\\\\server\\share\\Example.java"),
        new CapturedFileSystem(Syntax.POSIX).getPath("/source/Example.java"));

    // Act and Assert
    for (var path : paths) {
      assertThrows(IOException.class, () -> Files.readString(path));
      assertThrows(UnsupportedOperationException.class, path::toFile);
      assertThrows(IOException.class, path::toRealPath);
      assertEquals(path, path.getFileSystem().provider().getPath(path.toUri()));
      assertTrue(path.toUri().isAbsolute());
      assertTrue(Objects.requireNonNull(path.toUri().getPath()).endsWith("/Example.java"));
      assertTrue(SimpleJavaFileObject.forSource(path.toUri(), "class Example {}").isNameCompatible("Example", Kind.SOURCE));
    }
  }

  @Test
  void givenCapturedSyntaxMatchers_whenNamesAreMatched_thenDirectoryAndAlternativeBoundariesRemainDistinct() {
    // Arrange
    var fileSystem = new CapturedFileSystem(Syntax.WINDOWS);
    var matcher = fileSystem.getPathMatcher("glob:D:/{source,tests}/*.java");

    // Act and Assert
    assertTrue(matcher.matches(fileSystem.getPath("D:\\SOURCE\\Example.java")));
    assertFalse(matcher.matches(fileSystem.getPath("D:\\source\\nested\\Example.java")));
    assertFalse(matcher.matches(fileSystem.getPath("D:\\other\\Example.java")));
    for (var member : List.of("a", "b", "&")) {
      assertTrue(fileSystem.getPathMatcher("glob:[a&&b]").matches(fileSystem.getPath(member)));
    }
    assertTrue(fileSystem.getPathMatcher("glob:[[]").matches(fileSystem.getPath("[")));
    assertTrue(fileSystem.getPathMatcher("glob:[^a]").matches(fileSystem.getPath("^")));
    assertTrue(fileSystem.getPathMatcher("glob:[a-c]").matches(fileSystem.getPath("b")));
    assertFalse(fileSystem.getPathMatcher("glob:[!a-c]").matches(fileSystem.getPath("b")));
    assertTrue(fileSystem.getPathMatcher("glob:[!a-c]").matches(fileSystem.getPath("z")));
  }
}
