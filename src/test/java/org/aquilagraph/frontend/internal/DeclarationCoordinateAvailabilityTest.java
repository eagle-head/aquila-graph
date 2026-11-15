package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class DeclarationCoordinateAvailabilityTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenMissingPathsUnavailableEndpointsAndReversedCoordinates_whenReferenceOnlyKeysAreDerived_thenAvailabilityIsNotInvented()
      throws IOException {
    // Arrange
    var source = SourceFile.fromContent(capturedPath(Path.of("Sample.java")), "class Sample { void read() { int local = 1; class Local {} } }");
    var compiler = ToolProvider.getSystemJavaCompiler();
    var sources = new SnapshotSourceCatalog(InputSnapshot.fromSources(capturedAbsolute(temporaryDirectory), List.of(source)));
    try (var manager = compiler.getStandardFileManager(null, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, null,
          JavaCompilerInvocation.from(CompilerConfiguration.forRelease(JavaRelease.JAVA_25)).compilerOptions(), null, sources.compilationUnits());
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      task.analyze();
      var trees = Trees.instance(task);
      var declarations = new HashMap<String, Element>();
      var paths = new HashMap<Element, TreePath>();
      var scanner = new TreePathScanner<Void, Void>() {
        @Override public Void visitVariable(VariableTree tree, Void unused) {
          var element = trees.getElement(getCurrentPath());
          declarations.put(tree.getName().toString(), element);
          paths.put(element, getCurrentPath());
          return super.visitVariable(tree, unused);
        }
        @Override public Void visitClass(ClassTree tree, Void unused) {
          var element = trees.getElement(getCurrentPath());
          declarations.put(tree.getSimpleName().toString(), element);
          paths.put(element, getCurrentPath());
          return super.visitClass(tree, unused);
        }
      };
      units.forEach(unit -> scanner.scan(unit, null));
      var cases = List.of(new CoordinateCase(false, 10, 20, Optional.empty()),
          new CoordinateCase(true, Diagnostic.NOPOS, 20, Optional.empty()),
          new CoordinateCase(true, 10, Diagnostic.NOPOS, Optional.empty()),
          new CoordinateCase(true, 20, 10, Optional.empty()),
          new CoordinateCase(true, 10, 20, Optional.of("10:20")),
          new CoordinateCase(true, 0, 0, Optional.of("0:0")),
          new CoordinateCase(true, -2, -2, Optional.of("-2:-2")));

      // Act
      for (var coordinate : cases) {
        var registry = new DeclarationIdentityRegistry(new CoordinateTrees(coordinate.pathAvailable() ? paths : Map.of(),
            coordinate.start(), coordinate.end()), task.getTypes(), task.getElements(), JavaTranslationPolicy.legacyAnalysis());
        var local = registry.keyOf(declarations.get("local"));
        var localType = registry.keyOf(declarations.get("Local"));

        // Assert
        assertEquals(coordinate.suffix().map(suffix -> "type:Sample#read():local_variable:" + suffix), local);
        assertEquals(coordinate.suffix().map(suffix -> "type:Sample#read():type:" + suffix), localType);
      }
    }
  }

  private record CoordinateCase(boolean pathAvailable, long start, long end, Optional<String> suffix) {}

  private static final class CoordinateTrees extends Trees {
    private final Map<Element, TreePath> paths;
    private final SourcePositions positions;

    private CoordinateTrees(Map<Element, TreePath> paths, long start, long end) {
      this.paths = paths;
      positions = new SourcePositions() {
        @Override public long getStartPosition(CompilationUnitTree unit, Tree tree) {
          return start;
        }
        @Override public long getEndPosition(CompilationUnitTree unit, Tree tree) {
          return end;
        }
      };
    }

    @Override public SourcePositions getSourcePositions() {
      return positions;
    }
    @Override public TreePath getPath(Element element) {
      return paths.get(element);
    }
    @Override public Tree getTree(Element element) {
      throw unused();
    }
    @Override public ClassTree getTree(TypeElement element) {
      throw unused();
    }
    @Override public MethodTree getTree(ExecutableElement element) {
      throw unused();
    }
    @Override public Tree getTree(Element element, AnnotationMirror annotation) {
      throw unused();
    }
    @Override public Tree getTree(Element element, AnnotationMirror annotation, AnnotationValue value) {
      throw unused();
    }
    @Override public TreePath getPath(CompilationUnitTree unit, Tree tree) {
      throw unused();
    }
    @Override public TreePath getPath(Element element, AnnotationMirror annotation) {
      throw unused();
    }
    @Override public TreePath getPath(Element element, AnnotationMirror annotation, AnnotationValue value) {
      throw unused();
    }
    @Override public Element getElement(TreePath path) {
      throw unused();
    }
    @Override public TypeMirror getTypeMirror(TreePath path) {
      throw unused();
    }
    @Override public Scope getScope(TreePath path) {
      throw unused();
    }
    @Override public String getDocComment(TreePath path) {
      throw unused();
    }
    @Override public boolean isAccessible(Scope scope, TypeElement element) {
      throw unused();
    }
    @Override public boolean isAccessible(Scope scope, Element element, DeclaredType type) {
      throw unused();
    }
    @Override public TypeMirror getOriginalType(ErrorType type) {
      throw unused();
    }
    @Override public void printMessage(Kind kind, CharSequence message, Tree tree, CompilationUnitTree unit) {
      throw unused();
    }
    @Override public TypeMirror getLub(CatchTree tree) {
      throw unused();
    }
    private static UnsupportedOperationException unused() {
      return new UnsupportedOperationException("This coordinate observation does not supply other compiler operations");
    }
  }
}
