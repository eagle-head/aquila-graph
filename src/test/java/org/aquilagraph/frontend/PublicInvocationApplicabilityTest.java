package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.JavaInvocationApplicability.Phase;
import org.junit.jupiter.api.Test;

class PublicInvocationApplicabilityTest {
  @Test
  void givenArrayNullAndTrailingArguments_whenPublicCompilerAttributes_thenApplicabilityUsesTypesBeforeVariablePhase() throws IOException {
    // Arrange
    var source = """
        class Applicability {
          static void strings(String... values) {}
          static void calls(String[] array) {
            strings(array);
            strings((String[]) null);
            strings((String) null);
            strings();
            strings("one");
            strings("one", "two");
          }
        }
        """;

    // Act
    var calls = observe(source);

    // Assert
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.String[]")), calls.get("strings(array)"));
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.String[]")), calls.get("strings((String[])null)"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("java.lang.String")), calls.get("strings((String)null)"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of()), calls.get("strings()"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("java.lang.String")), calls.get("strings(\"one\")"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("java.lang.String", "java.lang.String")), calls.get("strings(\"one\", \"two\")"));
  }

  @Test
  void givenBoxingUnboxingAndWidening_whenPublicCompilerAttributes_thenFixedPhasesAndVariableElementTargetsAreDistinct() throws IOException {
    // Arrange
    var source = """
        class Applicability {
          static void reference(Object value) {}
          static void primitive(long value) {}
          static void primitiveArray(long... values) {}
          static void referenceArray(Object... values) {}
          static void calls(Integer boxed, int value) {
            reference(value);
            reference(boxed);
            primitive(boxed);
            primitive(value);
            primitiveArray(boxed);
            referenceArray(value);
          }
        }
        """;

    // Act
    var calls = observe(source);

    // Assert
    assertEquals(new Evidence(Phase.LOOSE_FIXED, List.of("java.lang.Object")), calls.get("reference(value)"));
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.Object")), calls.get("reference(boxed)"));
    assertEquals(new Evidence(Phase.LOOSE_FIXED, List.of("long")), calls.get("primitive(boxed)"));
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("long")), calls.get("primitive(value)"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("long")), calls.get("primitiveArray(boxed)"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("java.lang.Object")), calls.get("referenceArray(value)"));
  }

  @Test
  void givenExternalInstantiatedGenericMethod_whenPublicCompilerAttributes_thenApplicabilityUsesItsSelectedFormal() throws IOException {
    // Arrange
    var source = """
        class Applicability {
          static void calls(String[] values) {
            java.util.Arrays.asList(values);
            java.util.Arrays.<String>asList();
            java.util.Arrays.asList("one");
            java.util.Collections.<Object>singletonList(1);
          }
        }
        """;

    // Act
    var calls = observe(source);

    // Assert
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.String[]")), calls.get("java.util.Arrays.asList(values)"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of()), calls.get("java.util.Arrays.<String>asList()"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("java.lang.String")), calls.get("java.util.Arrays.asList(\"one\")"));
    assertEquals(new Evidence(Phase.LOOSE_FIXED, List.of("java.lang.Object")), calls.get("java.util.Collections.<Object>singletonList(1)"));
  }

  @Test
  void givenNestedGenericAndConditionalArguments_whenPublicCompilerAttributes_thenTargetsDistinguishNullArraysAndElements() throws IOException {
    // Arrange
    var source = """
        class Applicability {
          static <T> T identity(T value) { return value; }
          static void strings(String... values) {}
          static void calls(boolean flag, String[] array, int[] primitiveArray) {
            strings(identity(null));
            strings(flag ? identity(null) : identity(null));
            strings(flag ? identity("one") : identity("two"));
            java.util.Arrays.asList(identity(array));
            java.util.Arrays.<Object>asList(identity(array));
            java.util.Arrays.asList(primitiveArray);
          }
          static java.util.List<String> nullResult() { return java.util.Arrays.asList(identity(null)); }
        }
        """;

    // Act
    var calls = observe(source);

    // Assert
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.String[]")), calls.get("strings(identity(null))"));
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.String[]")),
        calls.get("strings(flag ? identity(null) : identity(null))"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("java.lang.String")),
        calls.get("strings(flag ? identity(\"one\") : identity(\"two\"))"));
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.String[]")), calls.get("java.util.Arrays.asList(identity(array))"));
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.Object[]")), calls.get("java.util.Arrays.<Object>asList(identity(array))"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("int[]")), calls.get("java.util.Arrays.asList(primitiveArray)"));
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.String[]")), calls.get("java.util.Arrays.asList(identity(null))"));
  }

  @Test
  void givenPhaseSensitiveOverloadsAndLoosePrefixes_whenPublicCompilerAttributes_thenSelectedTargetsPrecedeArrayPacking() throws IOException {
    // Arrange
    var source = """
        class Applicability {
          static void selected(long value) {}
          static void selected(Integer value) {}
          static void selected(int... values) {}
          static void mixed(long value, Object... trailing) {}
          static void calls(int primitive, Integer boxed, Object[] array) {
            selected(primitive);
            selected(boxed);
            selected();
            selected((int[]) null);
            mixed(boxed, array);
            mixed(boxed);
            mixed(boxed, 1);
          }
        }
        """;

    // Act
    var calls = observe(source);

    // Assert
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("long")), calls.get("selected(primitive)"));
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("java.lang.Integer")), calls.get("selected(boxed)"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of()), calls.get("selected()"));
    assertEquals(new Evidence(Phase.STRICT_FIXED, List.of("int[]")), calls.get("selected((int[])null)"));
    assertEquals(new Evidence(Phase.LOOSE_FIXED, List.of("long", "java.lang.Object[]")), calls.get("mixed(boxed, array)"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("long")), calls.get("mixed(boxed)"));
    assertEquals(new Evidence(Phase.VARIABLE, List.of("long", "java.lang.Object")), calls.get("mixed(boxed, 1)"));
  }

  private static Map<String, Evidence> observe(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var file = SimpleJavaFileObject.forSource(Path.of("D:/Projects/aquila-graph/aquila-graph/target/compiler-input/Applicability.java").toUri(), source);
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none"), null, List.of(file));
      var units = task.parse();
      task.analyze();
      assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      var trees = Trees.instance(task);
      var types = task.getTypes();
      var semantics = new SemanticResolver(trees, types, task.getElements(), new DeclarationIdentityRegistry(trees, types, task.getElements(), JavaTranslationPolicy.legacyAnalysis()));
      var applicability = new JavaInvocationApplicability(semantics);
      var result = new LinkedHashMap<String, Evidence>();
      for (var unit : units) {
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
            var path = getCurrentPath();
            var selected = trees.getElement(path);
            var mirror = trees.getTypeMirror(new TreePath(path, invocation.getMethodSelect()));
            if (selected instanceof ExecutableElement executable && mirror instanceof ExecutableType executableType) {
              var arguments = invocation.getArguments().stream().map(argument -> trees.getTypeMirror(new TreePath(path, argument))).toList();
              var selection = applicability.select(executableType, executable.isVarArgs(), arguments).orElseThrow();
              result.put(invocation.toString(), new Evidence(selection.phase(), selection.argumentTargets().stream().map(Object::toString).toList()));
            }
            return super.visitMethodInvocation(invocation, unused);
          }
        }.scan(unit, null);
      }
      return Map.copyOf(result);
    }
  }

  private record Evidence(Phase phase, List<String> targets) {}
}
