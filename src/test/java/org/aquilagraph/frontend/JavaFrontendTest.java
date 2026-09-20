package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.FrontendResult;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.MethodBody;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SyntaxKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaFrontendTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenResolvedJavaSource_whenFrontendRuns_thenCompilerSemanticsAndOriginsAreCaptured()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Resolved.java"),
        """
        package sample;
        final class Resolved {
          int length(String value) { return value.length(); }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertTrue(result.diagnostics().isEmpty());
    assertEquals("sample.Resolved", result.program().types().getFirst().qualifiedName());
    var method =
        result.program().methods().stream()
            .filter(candidate -> candidate.name().equals("length"))
            .findFirst()
            .orElseThrow();
    assertFalse(method.origins().isEmpty());
    assertTrue(
        method.body().orElseThrow().syntaxRoot().descendantsAndSelf().stream()
            .flatMap(node -> node.semanticReferences().stream())
            .anyMatch(reference -> reference.status() == ResolutionStatus.RESOLVED));
    assertFalse(Files.exists(temporaryDirectory.resolve("sample/Resolved.class")));
  }

  @Test
  void givenCapturedSourceChangesOnDisk_whenSnapshotIsAnalyzed_thenCapturedContentIsAuthoritative()
      throws IOException {
    // Arrange
    var sourcePath = temporaryDirectory.resolve("Captured.java");
    Files.writeString(
        sourcePath, "class Captured { int value() { return 1; } }");
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(temporaryDirectory);
    Files.writeString(
        sourcePath, "class Captured { int value() { return missingName; } }");
    var frontend = new JavaFrontend(loader);

    // Act
    var result = frontend.analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertTrue(result.diagnostics().isEmpty());
    assertTrue(
        result.program().methods().stream()
            .filter(method -> method.name().equals("value"))
            .flatMap(method -> method.body().stream())
            .flatMap(body -> body.syntaxRoot().descendantsAndSelf().stream())
            .anyMatch(
                node ->
                    node.syntaxKind() == SyntaxKind.LITERAL && node.detail().equals("1")));
  }

  @Test
  void givenAbstractNativeAndUnsupportedMethods_whenFrontendRuns_thenBodiesAreClassifiedExplicitly()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Mixed.java"),
        """
        abstract class Mixed {
          abstract void abstractCall();
          native int nativeCall();
          void unsupported() { while (true) { return; } }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertTrue(
        result.program().methods().stream()
            .filter(method -> method.name().equals("abstractCall") || method.name().equals("nativeCall"))
            .allMatch(method -> method.body().isEmpty()));
    assertTrue(
        result.program().methods().stream()
            .filter(method -> method.name().equals("unsupported"))
            .allMatch(method -> !method.body().orElseThrow().supported()));
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")));
  }

  @Test
  void givenCompositeSupportedBody_whenFrontendRuns_thenOrderedSyntaxStructureAndIdentityAreStable()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Structured.java"),
        """
        final class Structured {
          int evaluate(int left, int right) {
            int sum = left + right;
            if (sum > 0) {
              return Math.max(sum, 1);
            }
            return -sum;
          }
        }
        """);
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(temporaryDirectory);
    var frontend = new JavaFrontend(loader);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var first = frontend.analyze(snapshot, configuration);
    var second = frontend.analyze(snapshot, configuration);

    // Assert
    assertTrue(first.diagnostics().isEmpty());
    var firstBody = methodBody(first, "evaluate");
    var secondBody = methodBody(second, "evaluate");
    assertTrue(firstBody.supported());
    assertEquals(firstBody.syntaxRoot(), secondBody.syntaxRoot());
    assertEquals(
        List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.IF, SyntaxKind.RETURN),
        firstBody.syntaxRoot().children().stream().map(node -> node.syntaxKind()).toList());
    assertEquals(
        List.of(SyntaxKind.PARENTHESIZED, SyntaxKind.BLOCK),
        firstBody.syntaxRoot().children().get(1).children().stream().map(node -> node.syntaxKind()).toList());
  }

  @Test
  void givenTryWithResourcesAndFinally_whenFrontendRuns_thenBodyIsRejectedAndChildrenRemainOrdered()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("ResourceOwner.java"),
        """
        final class ResourceOwner {
          void read() {
            try (var reader = new java.io.StringReader("value")) {
              reader.read();
            } finally {
              System.out.println("closed");
            }
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "read");
    assertFalse(body.supported());
    assertEquals(1, result.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")).count());
    var tryNode = body.syntaxRoot().children().getFirst();
    assertEquals(SyntaxKind.TRY, tryNode.syntaxKind());
    assertEquals(
        List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.BLOCK, SyntaxKind.BLOCK),
        tryNode.children().stream().map(node -> node.syntaxKind()).toList());
  }

  @Test
  void givenUnsupportedUnaryUpdate_whenFrontendRuns_thenBodyAndDiagnosticRejectConstruct()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Updating.java"),
        "final class Updating { int increment(int value) { return ++value; } }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "increment");
    assertFalse(body.supported());
    assertEquals(
        List.of(SyntaxKind.BLOCK, SyntaxKind.RETURN, SyntaxKind.UNSUPPORTED, SyntaxKind.VARIABLE_ACCESS),
        body.syntaxRoot().descendantsAndSelf().stream().map(node -> node.syntaxKind()).toList());
    assertEquals(1, result.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")).count());
  }

  @Test
  void givenUnresolvedSymbol_whenFrontendRuns_thenCompilerDiagnosticPreventsSilentAcceptance()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Unresolved.java"),
        "class Unresolved { int value() { return missingName; } }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
    assertTrue(
        result.program().methods().stream()
            .filter(method -> method.name().equals("value"))
            .flatMap(method -> method.body().stream())
            .flatMap(body -> body.syntaxRoot().descendantsAndSelf().stream())
            .flatMap(node -> node.semanticReferences().stream())
            .anyMatch(reference -> reference.status() != ResolutionStatus.RESOLVED));
  }

  @Test
  void givenMultipleSources_whenFrontendRuns_thenProgramsAndDiagnosticsRetainSourceOwnership()
      throws IOException {
    // Arrange
    Files.createDirectories(temporaryDirectory.resolve("sample"));
    Files.writeString(
        temporaryDirectory.resolve("sample/Valid.java"),
        "package sample; final class Valid { int value() { return 1; } }");
    Files.writeString(
        temporaryDirectory.resolve("sample/Broken.java"),
        "package sample; final class Broken { int value() { return missingName; } }");
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(temporaryDirectory);
    var brokenSource =
        snapshot.sourceFiles().stream()
            .filter(sourceFile -> sourceFile.relativePath().endsWith("Broken.java"))
            .findFirst()
            .orElseThrow();
    var frontend = new JavaFrontend(loader);

    // Act
    var result = frontend.analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertEquals(
        Set.of("sample.Valid", "sample.Broken"),
        result.program().types().stream()
            .map(type -> type.qualifiedName())
            .collect(Collectors.toSet()));
    var errors = result.diagnostics().stream().filter(diagnostic -> diagnostic.isError()).toList();
    assertFalse(errors.isEmpty());
    assertTrue(
        errors.stream()
            .allMatch(
                diagnostic ->
                    !diagnostic.origins().isEmpty()
                        && diagnostic.origins().stream()
                            .allMatch(
                                origin ->
                                    origin
                                        .sourceFileKey()
                                        .equals(brokenSource.sourceFileKey()))));
  }

  @Test
  void givenEmptySourceRoot_whenFrontendRuns_thenEmptyProgramRetainsCompilerEnvironment()
      throws IOException {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, configuration);

    // Assert
    assertTrue(result.program().types().isEmpty());
    assertTrue(result.diagnostics().isEmpty());
    assertEquals(configuration, result.environmentSnapshot().compilerConfiguration());
  }

  @Test
  void givenFrontendIsReusedAcrossDependencyChanges_whenAnalysisRuns_thenEnvironmentSnapshotsRemainIsolated()
      throws IOException {
    // Arrange
    var sourceRoot = Files.createDirectory(temporaryDirectory.resolve("source"));
    var dependencyRoot = Files.createDirectory(temporaryDirectory.resolve("dependency"));
    Files.writeString(sourceRoot.resolve("Stable.java"), "final class Stable { int value() { return 1; } }");
    var dependencyFile = dependencyRoot.resolve("version.txt");
    Files.writeString(dependencyFile, "first");
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(dependencyRoot), List.of(), List.of());
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.analyze(sourceRoot, configuration);
    Files.writeString(dependencyFile, "second");
    var second = frontend.analyze(sourceRoot, configuration);

    // Assert
    assertTrue(first.diagnostics().isEmpty());
    assertTrue(second.diagnostics().isEmpty());
    assertEquals(first.program(), second.program());
    assertNotEquals(first.environmentSnapshot().environmentDigest(), second.environmentSnapshot().environmentDigest());
  }

  private static MethodBody methodBody(FrontendResult result, String methodName) {
    return result.program().methods().stream().filter(method -> method.name().equals(methodName)).findFirst().orElseThrow().body()
        .orElseThrow();
  }
}
