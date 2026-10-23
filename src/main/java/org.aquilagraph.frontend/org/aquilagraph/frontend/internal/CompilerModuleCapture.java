package org.aquilagraph.frontend.internal;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.InvalidModuleDescriptorException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.jar.JarFile;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.DependencyKind;
import org.aquilagraph.identity.api.DependencySemantics;
import org.aquilagraph.identity.api.DependencySnapshot;
import org.aquilagraph.identity.api.ModuleCandidate;
import org.aquilagraph.identity.api.ModuleNameOrigin;
import org.aquilagraph.source.api.AnalysisInputException;

/** Owns fresh compiler module discovery without sharing the analysis session's diagnostics or caches. */
final class CompilerModuleCapture {
  private static final String ARCHIVE_URI_SCHEME = "jar";

  List<DependencySnapshot> capture(JavaCompiler compiler, CompilerConfiguration configuration, List<DependencySnapshot> physical,
      boolean modular) throws IOException {
    var captured = new ArrayList<DependencySnapshot>(physical.size());
    for (var dependency : physical) {
      captured.add(capture(compiler, configuration, dependency, modular));
    }
    return List.copyOf(captured);
  }

  private static DependencySnapshot capture(JavaCompiler compiler, CompilerConfiguration configuration, DependencySnapshot dependency,
      boolean modular) throws IOException {
    var semantics = !modular
        ? new DependencySemantics(DependencySemantics.State.CLASS_PATH, List.of())
        : dependency.kind() == DependencyKind.MISSING
            ? new DependencySemantics(DependencySemantics.State.MISSING, List.of())
            : observe(compiler, configuration, dependency.location());
    return new DependencySnapshot(dependency.location(), dependency.kind(), dependency.byteCount(), dependency.entryCount(),
        dependency.fingerprint(), semantics);
  }

  private static DependencySemantics observe(JavaCompiler compiler, CompilerConfiguration configuration, Path entry) throws IOException {
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
      try {
        JavaCompilerInvocation.from(configuration).configureModuleView(manager, entry);
      } catch (IllegalArgumentException exception) {
        throw new AnalysisInputException("The compiler rejected the analysis configuration", exception);
      }
      var modules = new ArrayList<ModuleCandidate>();
      for (var group : manager.listLocationsForModules(StandardLocation.MODULE_PATH)) {
        for (var location : group) {
          var name = manager.inferModuleName(location);
          if (name == null || name.isBlank()) {
            throw new IOException("Compiler module location has no lookup name: " + location.getName());
          }
          modules.add(candidate(manager, location, entry, name, configuration.release().feature()));
        }
      }
      var invalid = false;
      for (var diagnostic : diagnostics.getDiagnostics()) {
        if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
          var code = diagnostic.getCode();
          if (!"compiler.err.locn.bad.module-info".equals(code) && !"compiler.err.locn.cant.get.module.name.for.jar".equals(code)) {
            throw new IOException("Compiler module observation failed: " + diagnostic.getMessage(Locale.ROOT));
          }
          invalid = true;
        }
      }
      return new DependencySemantics(invalid ? DependencySemantics.State.INVALID : DependencySemantics.State.MODULES, modules);
    } catch (UnsupportedOperationException exception) {
      throw new IOException("The compiler file manager does not support module observation", exception);
    }
  }

  private static ModuleCandidate candidate(StandardJavaFileManager manager, Location location, Path entry, String lookupName, int release)
      throws IOException {
    var roots = manager.getLocationAsPaths(location).iterator();
    if (!roots.hasNext()) {
      throw new IOException("Compiler module location has no artifact: " + location.getName());
    }
    var root = artifact(roots.next());
    if (roots.hasNext()) {
      throw new IOException("Compiler module location has multiple artifact roots: " + location.getName());
    }
    var member = root.equals(entry) ? "." : entry.relativize(root).toString().replace('\\', '/');
    var descriptor = manager.getJavaFileForInput(location, "module-info", JavaFileObject.Kind.CLASS);
    if (descriptor != null) {
      String declaredName;
      try (var input = descriptor.openInputStream()) {
        declaredName = ModuleDescriptor.read(input).name();
      } catch (InvalidModuleDescriptorException exception) {
        throw new IOException("Cannot copy the compiler-selected module descriptor: " + descriptor.getName(), exception);
      }
      var origin = Files.isDirectory(root) && !".".equals(member) ? ModuleNameOrigin.DIRECTORY : ModuleNameOrigin.DESCRIPTOR;
      return new ModuleCandidate(member, lookupName, Optional.of(declaredName), origin);
    }
    if (Files.isDirectory(root) || !root.toString().endsWith(".jar")) {
      throw new IOException("Compiler-selected explicit module has no readable descriptor: " + root);
    }
    try (var jar = new JarFile(root.toFile(), false, JarFile.OPEN_READ, Runtime.Version.parse(Integer.toString(release)))) {
      var manifest = jar.getManifest();
      var manifestName = manifest == null ? null : manifest.getMainAttributes().getValue("Automatic-Module-Name");
      return new ModuleCandidate(member, lookupName, Optional.empty(),
          manifestName == null ? ModuleNameOrigin.FILENAME : ModuleNameOrigin.MANIFEST);
    }
  }

  private static Path artifact(Path root) throws IOException {
    var uri = root.toUri();
    if (ARCHIVE_URI_SCHEME.equals(uri.getScheme())) {
      var value = uri.getRawSchemeSpecificPart();
      var separator = value.lastIndexOf("!/");
      if (separator < 0) {
        throw new IOException("Compiler archive location has no artifact boundary: " + uri);
      }
      return Path.of(URI.create(value.substring(0, separator))).toAbsolutePath().normalize();
    }
    return root.toAbsolutePath().normalize();
  }
}
