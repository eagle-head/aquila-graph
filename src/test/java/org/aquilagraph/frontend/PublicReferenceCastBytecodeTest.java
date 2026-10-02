package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicReferenceCastBytecodeTest {
  @TempDir Path output;

  @Test
  void givenWideningLiteralConditionalAndArrayCasts_whenPublicCompilerGeneratesClassFile_thenNoReferenceCheckIsEmitted() throws IOException {
    // Arrange
    var source = """
        class Casts {
          Object literal() { return (Object) "text"; }
          Object conditional(boolean flag) { return (Object) (flag ? "left" : "right"); }
          Object array(int[] values) { return (Object) values; }
        }
        """;
    // Act
    var checks = observe(source);
    // Assert
    assertEquals(List.of(), checks.get("literal"));
    assertEquals(List.of(), checks.get("conditional"));
    assertEquals(List.of(), checks.get("array"));
  }

  @Test
  void givenCheckedClassArrayAndTypeVariableCasts_whenPublicCompilerGeneratesClassFile_thenEachCheckUsesItsErasedRuntimeTarget() throws IOException {
    // Arrange
    var source = """
        class Casts {
          String reference(Object value) { return (String) value; }
          int[] array(Object value) { return (int[]) value; }
          <T> String variable(T value) { return (String) value; }
        }
        """;
    // Act
    var checks = observe(source);
    // Assert
    assertEquals(List.of("java/lang/String"), checks.get("reference"));
    assertEquals(List.of("[I"), checks.get("array"));
    assertEquals(List.of("java/lang/String"), checks.get("variable"));
  }

  @Test
  void givenCompletelyAndPartiallyUncheckedGenericCasts_whenPublicCompilerGeneratesClassFile_thenOnlyTheErasureChangingCastNeedsACheck() throws IOException {
    // Arrange
    var source = """
        class Casts {
          java.util.List<String> complete(java.util.List<?> values) { return (java.util.List<String>) values; }
          java.util.List<String> partial(Object values) { return (java.util.List<String>) values; }
        }
        """;
    // Act
    var checks = observe(source);
    // Assert
    assertEquals(List.of(), checks.get("complete"));
    assertEquals(List.of("java/util/List"), checks.get("partial"));
  }

  @Test
  void givenNullAndIntersectionCasts_whenPublicCompilerGeneratesClassFile_thenNullRetainsACheckInstructionAndIntersectionRetainsBothTargets() throws IOException {
    // Arrange
    var source = """
        class Casts {
          String nullValue() { return (String) null; }
          CharSequence intersection(Object value) { return (CharSequence & java.io.Serializable) value; }
        }
        """;
    // Act
    var checks = observe(source);
    // Assert
    assertEquals(List.of("java/lang/String"), checks.get("nullValue"));
    assertEquals(List.of("java/io/Serializable", "java/lang/CharSequence"), checks.get("intersection"));
  }

  private Map<String, List<String>> observe(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var file = SimpleJavaFileObject.forSource(output.resolve("Casts.java").toUri(), source);
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      assertTrue(compiler.getTask(null, manager, diagnostics,
          List.of("--release", "25", "-proc:none", "-d", output.toString()), null, List.of(file)).call());
    }
    assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
    var observations = new LinkedHashMap<String, List<String>>();
    for (var method : ClassFile.of().parse(output.resolve("Casts.class")).methods()) {
      observations.put(method.methodName().stringValue(), method.code().orElseThrow().elementList().stream()
          .filter(TypeCheckInstruction.class::isInstance).map(TypeCheckInstruction.class::cast)
          .filter(instruction -> instruction.opcode() == Opcode.CHECKCAST)
          .map(instruction -> instruction.type().asInternalName()).toList());
    }
    return Map.copyOf(observations);
  }
}
