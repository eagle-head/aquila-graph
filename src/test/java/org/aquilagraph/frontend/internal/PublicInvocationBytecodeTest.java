package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
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

class PublicInvocationBytecodeTest {
  @TempDir
  Path output;

  @Test
  void givenFixedAndVariableCalls_whenJavacGeneratesClassFile_thenActualPackingMatchesArrayAndElementPartitions() throws IOException {
    // Arrange
    var source = """
        class Packing {
          static void strings(String... values) {}
          static void fixed(String[] values) { strings(values); }
          static void nullArray() { strings((String[]) null); }
          static void nullElement() { strings((String) null); }
          static void zero() { strings(); }
          static void one() { strings("one"); }
          static void two() { strings("one", "two"); }
          static void genericFixed(String[] values) { java.util.Arrays.asList(values); }
          static void genericZero() { java.util.Arrays.<String>asList(); }
          static void genericTwo() { java.util.Arrays.asList("one", "two"); }
        }
        """;
    // Act
    var observations = observe(source);

    // Assert
    assertEquals(new Packing(0, 0, "Packing", "strings"), observations.get("fixed"));
    assertEquals(new Packing(0, 0, "Packing", "strings"), observations.get("nullArray"));
    assertEquals(new Packing(1, 1, "Packing", "strings"), observations.get("nullElement"));
    assertEquals(new Packing(1, 0, "Packing", "strings"), observations.get("zero"));
    assertEquals(new Packing(1, 1, "Packing", "strings"), observations.get("one"));
    assertEquals(new Packing(1, 2, "Packing", "strings"), observations.get("two"));
    assertEquals(new Packing(0, 0, "java/util/Arrays", "asList"), observations.get("genericFixed"));
    assertEquals(new Packing(1, 0, "java/util/Arrays", "asList"), observations.get("genericZero"));
    assertEquals(new Packing(1, 2, "java/util/Arrays", "asList"), observations.get("genericTwo"));
  }

  @Test
  void givenNestedGenericAndConditionalArguments_whenJavacGeneratesClassFile_thenNullResultsStayFixedAndElementsArePacked() throws IOException {
    // Arrange
    var source = """
        class Packing {
          static <T> T identity(T value) { return value; }
          static void strings(String... values) {}
          static void nullPoly() { strings(identity(null)); }
          static void nullConditional(boolean flag) { strings(flag ? identity(null) : identity(null)); }
          static void elementConditional(boolean flag) { strings(flag ? identity("one") : identity("two")); }
          static void inferred(String[] array) { java.util.Arrays.asList(identity(array)); }
          static void explicit(String[] array) { java.util.Arrays.<Object>asList(identity(array)); }
          static void primitive(int[] array) { java.util.Arrays.asList(array); }
          static java.util.List<String> nullResult() { return java.util.Arrays.asList(identity(null)); }
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(new Packing(0, 0, "Packing", "strings"), observations.get("nullPoly"));
    assertEquals(new Packing(0, 0, "Packing", "strings"), observations.get("nullConditional"));
    assertEquals(new Packing(1, 1, "Packing", "strings"), observations.get("elementConditional"));
    assertEquals(new Packing(0, 0, "java/util/Arrays", "asList"), observations.get("inferred"));
    assertEquals(new Packing(0, 0, "java/util/Arrays", "asList"), observations.get("explicit"));
    assertEquals(new Packing(1, 1, "java/util/Arrays", "asList"), observations.get("primitive"));
    assertEquals(new Packing(0, 0, "java/util/Arrays", "asList"), observations.get("nullResult"));
  }

  @Test
  void givenPhaseSensitiveOverloadsAndWrapperPrefixes_whenJavacGeneratesClassFile_thenPrimitiveAndReferencePackingRemainDistinct() throws IOException {
    // Arrange
    var source = """
        class Packing {
          static void selected(long value) {}
          static void selected(Integer value) {}
          static void selected(int... values) {}
          static void longs(long... values) {}
          static void mixed(long value, Object... trailing) {}
          static void strictPrimitive(int value) { selected(value); }
          static void strictWrapper(Integer value) { selected(value); }
          static void variablePrimitive() { selected(); }
          static void fixedNullArray() { selected((int[]) null); }
          static void wrapperElement(Integer value) { longs(value); }
          static void fixedLoose(Integer value, Object[] array) { mixed(value, array); }
          static void variableLooseZero(Integer value) { mixed(value); }
          static void variableLooseOne(Integer value) { mixed(value, 1); }
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(new Packing(0, 0, "Packing", "selected"), observations.get("strictPrimitive"));
    assertEquals(new Packing(0, 0, "Packing", "selected"), observations.get("strictWrapper"));
    assertEquals(new Packing(0, 0, 1, 0, "Packing", "selected"), observations.get("variablePrimitive"));
    assertEquals(new Packing(0, 0, "Packing", "selected"), observations.get("fixedNullArray"));
    assertEquals(new Packing(0, 0, 1, 1, "Packing", "longs"), observations.get("wrapperElement"));
    assertEquals(new Packing(0, 0, "Packing", "mixed"), observations.get("fixedLoose"));
    assertEquals(new Packing(1, 0, "Packing", "mixed"), observations.get("variableLooseZero"));
    assertEquals(new Packing(1, 1, "Packing", "mixed"), observations.get("variableLooseOne"));
  }

  private Map<String, Packing> observe(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var file = SimpleJavaFileObject.forSource(output.resolve("Packing.java").toUri(), source);
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      assertTrue(compiler.getTask(null, manager, diagnostics,
          List.of("--release", "25", "-proc:none", "-d", output.toString()), null, List.of(file)).call());
    }
    assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
    return packing(ClassFile.of().parse(output.resolve("Packing.class")));
  }

  private static Map<String, Packing> packing(ClassModel classFile) {
    var observations = new LinkedHashMap<String, Packing>();
    for (var method : classFile.methods()) {
      var code = method.code().orElseThrow();
      var instructions = code.elementList().stream().filter(Instruction.class::isInstance).map(Instruction.class::cast).toList();
      for (var index = 0; index < instructions.size(); index++) {
        if (instructions.get(index) instanceof InvokeInstruction invocation
            && (invocation.name().equalsString("strings") || invocation.name().equalsString("asList")
                || invocation.name().equalsString("selected") || invocation.name().equalsString("longs") || invocation.name().equalsString("mixed"))) {
          var beforeCall = instructions.subList(0, index);
          observations.put(method.methodName().stringValue(), new Packing(
              beforeCall.stream().filter(instruction -> instruction.opcode() == Opcode.ANEWARRAY).count(),
              beforeCall.stream().filter(instruction -> instruction.opcode() == Opcode.AASTORE).count(),
              beforeCall.stream().filter(instruction -> instruction.opcode() == Opcode.NEWARRAY).count(),
              beforeCall.stream().filter(instruction -> instruction.opcode() == Opcode.LASTORE).count(),
              invocation.owner().asInternalName(), invocation.name().stringValue()));
          assertTrue(instructions.subList(index + 1, instructions.size()).stream()
              .noneMatch(instruction -> instruction.opcode() == Opcode.ANEWARRAY || instruction.opcode() == Opcode.AASTORE
                  || instruction.opcode() == Opcode.NEWARRAY || instruction.opcode() == Opcode.LASTORE));
        }
      }
    }
    return Map.copyOf(observations);
  }

  private record Packing(long arrays, long stores, long primitiveArrays, long longStores, String owner, String name) {
    Packing(long arrays, long stores, String owner, String name) {
      this(arrays, stores, 0, 0, owner, name);
    }
  }
}
