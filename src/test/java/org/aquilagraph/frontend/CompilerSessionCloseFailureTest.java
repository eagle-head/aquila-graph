package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class CompilerSessionCloseFailureTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenActualTransferredUnparsedSessionAndPrimedArchive_whenArchiveIsRemovedAndSessionCloses_thenRawCloseFailurePreventsNormalClose()
      throws IOException {
    // Arrange
    var archive = temporaryDirectory.resolve("dependency.jar");
    writeArchive(archive);
    var delegate = ToolProvider.getSystemJavaCompiler();
    try (var _ = new ArchiveDisposal(archive);
        var manager = delegate.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      manager.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of(archive));
      var entry = manager.list(StandardLocation.CLASS_PATH, "dependency", Set.of(Kind.CLASS), false).iterator().next();
      var compiler = new CompilerManagerSource(delegate, manager);
      var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(archive), List.of(), List.of());
      var snapshot = InputSnapshot.fromSources(temporaryDirectory,
          List.of(SourceFile.fromContent(Path.of("Sample.java"), "class Sample {}")));
      var session = CompilerSessionAcquisition.open(compiler, JavaCompilerInvocation.from(configuration), new SnapshotSourceCatalog(snapshot));

      // Act
      Files.delete(archive);
      var failure = assertThrows(NoSuchFileException.class, session::close);

      // Assert
      assertTrue(entry.getName().contains("dependency/Dependency.class"));
      assertEquals(archive.toString(), failure.getFile());
      assertEquals(0, failure.getSuppressed().length);
      assertFalse(Files.exists(archive));
    }
  }

  private record ArchiveDisposal(Path archive) implements AutoCloseable {
    @Override
    public void close() throws IOException {
      if (Files.notExists(archive)) {
        // Restore the path so the provider can dispose its closed archive cache.
        writeArchive(archive);
        try (var restoredFileSystem = FileSystems.newFileSystem(archive)) {
          assertTrue(restoredFileSystem.isOpen());
        }
      }
      Files.deleteIfExists(archive);
    }
  }

  private static void writeArchive(Path archive) throws IOException {
    try (var output = new JarOutputStream(Files.newOutputStream(archive))) {
      output.putNextEntry(new JarEntry("dependency/Dependency.class"));
      output.write(new byte[] {0});
      output.closeEntry();
    }
  }

  private record CompilerManagerSource(JavaCompiler delegate, StandardJavaFileManager manager) implements JavaCompiler {
    @Override
    public CompilationTask getTask(Writer output, JavaFileManager fileManager, DiagnosticListener<? super JavaFileObject> listener,
        Iterable<String> options, Iterable<String> classes, Iterable<? extends JavaFileObject> units) {
      return delegate.getTask(output, fileManager, listener, options, classes, units);
    }

    @Override
    public StandardJavaFileManager getStandardFileManager(DiagnosticListener<? super JavaFileObject> listener, Locale locale,
        Charset charset) {
      return manager;
    }

    @Override
    public int run(InputStream input, OutputStream output, OutputStream error, String... arguments) {
      return delegate.run(input, output, error, arguments);
    }

    @Override
    public Set<SourceVersion> getSourceVersions() {
      return delegate.getSourceVersions();
    }

    @Override
    public int isSupportedOption(String option) {
      return delegate.isSupportedOption(option);
    }
  }
}
