package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AnalysisInputException;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotSourceCatalogLocationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenSameTextPosixAndUncSnapshots_whenOpenedByTheCompiler_thenNamesAndDiagnosticOriginsMatchEachCapture() throws IOException {
    // Arrange
    var root = "//LexicalServer/LexicalShare/Captured Sources";
    var name = "pkg/Example.java";
    var content = "package pkg; public class Example { Missing missing; }";
    var posix = snapshot(Syntax.POSIX, root, name, content);
    var windows = snapshot(Syntax.WINDOWS, root, name, content);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var posixInput = new SnapshotSourceCatalog(posix).compilationUnits().getFirst();
    var windowsInput = new SnapshotSourceCatalog(windows).compilationUnits().getFirst();
    var posixOutcome = frontend.produce(posix, configuration, AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    var windowsOutcome = frontend.produce(windows, configuration, AstProfile.JAVA_SOURCE_MODULE_SERVICES);

    // Assert
    assertEquals(posix.sourceRoot().spelling(), windows.sourceRoot().spelling());
    assertEquals(posixInput.toUri().getPath(), windowsInput.toUri().getPath());
    assertNotEquals(posixInput.toUri(), windowsInput.toUri());
    assertTrue(posixInput.isNameCompatible("Example", JavaFileObject.Kind.SOURCE));
    assertTrue(windowsInput.isNameCompatible("Example", JavaFileObject.Kind.SOURCE));
    assertEquals(content, posixInput.getCharContent(false));
    assertEquals(content, windowsInput.getCharContent(false));
    assertEquals(AstCompletion.INCOMPLETE, posixOutcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, windowsOutcome.completion());
    for (var outcome : List.of(posixOutcome, windowsOutcome)) {
      assertFalse(outcome.diagnostics().stream().anyMatch(candidate -> "compiler.err.bad.file.name".equals(candidate.code())));
      var diagnostic = outcome.diagnostics().stream().filter(candidate -> candidate.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR)
          .findFirst().orElseThrow();
      var origin = diagnostic.origins().getFirst();
      assertEquals(posix.sourceFiles().getFirst().sourceFileKey(), origin.sourceFileKey());
      assertEquals(content.indexOf("Missing"), origin.startOffset());
      assertEquals(content.indexOf("Missing") + "Missing".length(), origin.endOffsetExclusive());
    }
  }

  @Test
  void givenNativeDependencyPathWithAPathListSeparator_whenCompilerAcquiresIt_thenOnePathIsUsedAndExactSpellingIsRetained() throws IOException {
    // Arrange
    var dependencyDirectory = Files.createDirectory(temporaryDirectory.resolve("dependency" + File.pathSeparator + "classes"));
    var sourceFile = Files.writeString(temporaryDirectory.resolve("Library.java"), "package library; public class Library {}\n");
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertEquals(0, compiler.run(null, null, null, "-d", dependencyDirectory.toString(), sourceFile.toString()));
    var nativeLocation = NativeLocations.capture(dependencyDirectory);
    var requestedSpelling = nativeLocation.syntax() == Syntax.WINDOWS ? nativeLocation.spelling().replace('\\', '/') : nativeLocation.spelling();
    var requestedLocation = CapturedLocation.parse(nativeLocation.syntax(), requestedSpelling);
    var request = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(requestedLocation, requestedLocation), List.of(), List.of());
    var input = snapshot(Syntax.POSIX, "/captured/sources", "Client.java", "public class Client { library.Library library; }\n");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var outcome = frontend.produce(input, request, AstProfile.JAVA_SOURCE_MODULE_SERVICES);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var environment = outcome.capturedEnvironment().orElseThrow();
    assertEquals(List.of(requestedLocation, requestedLocation), environment.classPath().stream().map(dependency -> dependency.location()).toList());
    assertEquals(1, environment.classPath().getFirst().entryCount());
    assertTrue(environment.modulePath().isEmpty());
  }

  @Test
  void givenEmptyConfiguredClassPath_whenTheCompilerManagerIsPrepared_thenNoAmbientDirectoryIsSearched() throws IOException {
    // Arrange
    var compiler = ToolProvider.getSystemJavaCompiler();
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var invocation = JavaCompilerInvocation.from(configuration);

    try (var manager = compiler.getStandardFileManager(null, null, null);
        var emptyOptionManager = compiler.getStandardFileManager(null, null, null)) {
      // Act
      invocation.configurePaths(manager);
      var emptyOptionAccepted = emptyOptionManager.handleOption("--class-path", List.of("").iterator());

      // Assert
      assertTrue(emptyOptionAccepted);
      assertFalse(manager.getLocationAsPaths(StandardLocation.CLASS_PATH).iterator().hasNext());
      var ambient = emptyOptionManager.getLocationAsPaths(StandardLocation.CLASS_PATH).iterator();
      assertEquals(Path.of("").toAbsolutePath().normalize(), ambient.next().toAbsolutePath().normalize());
      assertFalse(ambient.hasNext());
    }
  }

  @Test
  void givenForeignDependencySyntax_whenTheFrontendRequestsNativeCompilerInput_thenItPreservesTheConfigurationFailure() {
    // Arrange
    var nativeSyntax = NativeLocations.capture(temporaryDirectory).syntax();
    var foreignLocation = nativeSyntax == Syntax.WINDOWS ? CapturedLocation.parse(Syntax.POSIX, "/dependency/classes")
        : CapturedLocation.parse(Syntax.WINDOWS, "C:/dependency/classes");
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(foreignLocation), List.of(), List.of());
    var input = snapshot(Syntax.POSIX, "/captured/sources", "Example.java", "public class Example {}\n");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var failure = assertThrows(AnalysisInputException.class,
        () -> frontend.produce(input, configuration, AstProfile.JAVA_SOURCE_MODULE_SERVICES));

    // Assert
    assertEquals("The compiler rejected the analysis configuration", failure.getMessage());
    var cause = assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    assertTrue(cause.getMessage().contains(foreignLocation.spelling()));
  }

  private static InputSnapshot snapshot(Syntax syntax, String root, String name, String content) {
    return InputSnapshot.fromSources(CapturedLocation.parse(syntax, root), List.of(SourceFile.fromContent(CapturedLocation.parse(syntax, name), content)));
  }
}
