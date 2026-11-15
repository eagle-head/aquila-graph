package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrittenAttachmentProjectionTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final List<AstProfile> PROFILES = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED,
      AstProfile.JAVA_SOURCE_INITIALIZATION, AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION,
      AstProfile.JAVA_SOURCE_DECLARATIONS, AstProfile.JAVA_SOURCE_UNITS);
  private static final String LOCALS = """
      import java.lang.annotation.*;
      @Target({ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE}) @interface Mark {}
      class Example {
        int subject() {
          @Mark int first = 1, second = 2;
          @Mark var inferred = first + second;
          return inferred;
        }
      }
      """;
  private static final String MIXED = """
      import java.lang.annotation.*;
      import java.util.function.*;
      @Target(ElementType.TYPE_USE) @interface Use {}
      record Pair(String value) {}
      class Example {
        Object field = new @Use String("field");
        static { int[] numbers = new int @Use [1]; }
        { Object value = String.class; }
        Example() { Object value = new @Use String("constructor"); }
        Object subject(Object input) {
          String[][] array = new @Use String @Use [1] @Use [];
          Supplier<String> created = @Use String::new;
          IntFunction<String[]> sized = String @Use []::new;
          Supplier<String> bound = "bound"::toString;
          Function<String, String> selected = Example::<String>identity;
          String invoked = Example.<String>identity("value");
          Object cast = (java.io.Serializable & Comparable<String>) invoked;
          boolean ordinary = input instanceof String;
          if (input instanceof Pair(String value)) return value;
          try (java.io.StringReader reader = new java.io.StringReader(invoked)) {
            return new @Use String(created.get() + reader.read());
          } catch (java.io.IOException failure) { return cast; }
        }
        static <T> T identity(T value) { return value; }
      }
      """;
  @TempDir Path directory;

  @Test
  void givenSharedAnnotatedLocalsAndInferredType_whenProducedAndDecoded_thenOrderAndCanonicalAnnotationBindingsSurviveClosure()
      throws IOException {
    // Arrange
    var snapshot = snapshot("locals", LOCALS);

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);
    var result = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var nodes = decoded.aggregate().nodes();
    var first = local(nodes, "first");
    var second = local(nodes, "second");
    var inferred = local(nodes, "inferred");
    assertEquals(List.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE, SyntaxRole.INITIALIZER), roles(first));
    assertEquals(List.of(SyntaxRole.DECLARED_TYPE, SyntaxRole.INITIALIZER), roles(second));
    assertEquals(List.of(SyntaxRole.ANNOTATION, SyntaxRole.INITIALIZER), roles(inferred));
    var annotation = first.requiredChild(SyntaxRole.ANNOTATION);
    assertEquals(1, first.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().annotations());
    assertEquals(0, second.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().annotations());
    assertEquals(List.of(annotation.nodeId()), second.value(SyntaxValue.LocalDeclaration.class)
        .bindingEvidence().orElseThrow().sharedAnnotationKeys());
    assertTrue(first.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().sharedAnnotationKeys().isEmpty());
    assertTrue(inferred.children(SyntaxRole.DECLARED_TYPE).isEmpty());
    assertEquals(1, nodes.stream().filter(node -> node.origin().equals(annotation.origin()) && node.syntaxKind() == SyntaxKind.ANNOTATION).count());
    var method = decoded.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var declaration = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration value
        && value.declarationKey().equals(method.methodKey())).findFirst().orElseThrow();
    assertSame(declaration.requiredChild(SyntaxRole.BODY), decoded.body(method.methodKey()).orElseThrow().root());
    assertArrayEquals(AstCodec.encode(result), AstCodec.encode(decoded));
    compareBaseline("locals", AstProfile.JAVA_SOURCE_UNITS, outcome);
  }

  @Test
  void givenMixedWrittenAttachmentsAcrossExecutableOwners_whenEveryProfileProduces_thenQualificationAndCanonicalValuesStayStable()
      throws IOException {
    // Arrange
    var snapshot = snapshot("mixed-attachments", MIXED);
    var frontend = new JavaFrontend(new JavaSourceLoader());
    for (var profile : PROFILES) {
      // Act
      var outcome = frontend.produce(snapshot, CONFIGURATION, profile);

      // Assert
      assertFalse(outcome.diagnostics().stream().anyMatch(value -> value.code().startsWith("compiler.err.")), outcome.diagnostics().toString());
      var result = outcome.result().orElseThrow();
      assertArrayEquals(AstCodec.encode(result), AstCodec.encode(AstCodec.decode(AstCodec.encode(result), result.identity())));
      if (profile.equals(AstProfile.JAVA_SOURCE_UNITS)) {
        assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
        var nodes = result.aggregate().nodes();
        var array = local(nodes, "array").requiredChild(SyntaxRole.INITIALIZER);
        assertEquals(List.of(SyntaxRole.DIMENSION, SyntaxRole.TYPE_OPERAND, SyntaxRole.ANNOTATION), roles(array));
        assertEquals(List.of(SyntaxRole.QUALIFIER), roles(local(nodes, "created").requiredChild(SyntaxRole.INITIALIZER)));
        assertEquals(List.of(SyntaxRole.QUALIFIER), roles(local(nodes, "sized").requiredChild(SyntaxRole.INITIALIZER)));
        assertEquals(List.of(SyntaxRole.QUALIFIER), roles(local(nodes, "bound").requiredChild(SyntaxRole.INITIALIZER)));
        assertEquals(List.of(SyntaxRole.QUALIFIER, SyntaxRole.TYPE_ARGUMENT), roles(local(nodes, "selected").requiredChild(SyntaxRole.INITIALIZER)));
        assertEquals(List.of(SyntaxRole.ARGUMENT, SyntaxRole.TYPE_ARGUMENT, SyntaxRole.QUALIFIER), roles(local(nodes, "invoked").requiredChild(SyntaxRole.INITIALIZER)));
        assertEquals(List.of(SyntaxRole.EXPRESSION, SyntaxRole.TYPE_OPERAND), roles(local(nodes, "cast").requiredChild(SyntaxRole.INITIALIZER)));
        assertEquals(List.of(SyntaxRole.EXPRESSION, SyntaxRole.TYPE_OPERAND), roles(local(nodes, "ordinary").requiredChild(SyntaxRole.INITIALIZER)));
        assertTrue(nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL)
            .allMatch(node -> roles(node).equals(List.of(SyntaxRole.TYPE_OPERAND))));
      }
      compareBaseline("mixed-attachments", profile, outcome);
    }
  }

  @Test
  void givenLeadingComponentAnnotationsOnArrayReferences_whenProduced_thenQualifierRangesContainTheirWrittenAnnotations()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Use {}
        class Example {
          void subject() {
            java.util.function.IntFunction<String[]> first = @Use String[]::new;
            java.util.function.IntFunction<String[][]> second = @Use String[][]::new;
            java.util.function.IntFunction<String[]> dimension = String @Use []::new;
          }
        }
        """;

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot("annotated-references", source), CONFIGURATION,
        AstProfile.JAVA_SOURCE_UNITS);

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    var result = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    for (var name : List.of("first", "second", "dimension")) {
      var reference = local(decoded.aggregate().nodes(), name).requiredChild(SyntaxRole.INITIALIZER);
      var qualifier = reference.requiredChild(SyntaxRole.QUALIFIER);
      var annotation = qualifier.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
          .findFirst().orElseThrow();
      var expectedStart = source.indexOf(name.equals("dimension") ? "String @Use []::new" : "@Use String" + (name.equals("first") ? "[]" : "[][]") + "::new");
      assertEquals(source.indexOf("String", expectedStart), reference.origin().orElseThrow().startOffset());
      assertEquals(expectedStart, qualifier.origin().orElseThrow().startOffset());
      assertTrue(qualifier.origin().orElseThrow().startOffset() <= annotation.origin().orElseThrow().startOffset());
      assertTrue(qualifier.origin().orElseThrow().endOffsetExclusive() >= annotation.origin().orElseThrow().endOffsetExclusive());
    }
    assertArrayEquals(AstCodec.encode(result), AstCodec.encode(decoded));
  }

  private InputSnapshot snapshot(String name, String source) {
    var preservation = System.getProperty("frontend.preservation.directory");
    var root = preservation == null ? directory.resolve(name) : Path.of(preservation).resolve("inputs").resolve(name);
    return InputSnapshot.fromSources(capturedAbsolute(root), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
  }

  private static SyntaxNode local(List<SyntaxNode> nodes, String name) {
    return nodes.stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local && local.name().equals(name))
        .findFirst().orElseThrow();
  }

  private static List<SyntaxRole> roles(SyntaxNode node) {
    return node.children().stream().map(child -> child.role()).toList();
  }

  private static void compareBaseline(String name, AstProfile profile, AstOutcome outcome) throws IOException {
    var preservation = System.getProperty("frontend.preservation.directory");
    if (preservation == null) return;
    var baseline = Path.of(preservation).resolve("baseline");
    var key = "attachments-" + name + "-" + profile.version().replace('/', '-');
    var bytes = AstCodec.encode(outcome.result().orElseThrow());
    var qualification = (outcome.profile() + "\n" + outcome.completion() + "\n" + outcome.diagnostics()).getBytes(StandardCharsets.UTF_8);
    if (Boolean.getBoolean("frontend.preservation.capture")) {
      Files.createDirectories(baseline);
      Files.write(baseline.resolve(key + ".ast"), bytes);
      Files.write(baseline.resolve(key + ".qualification"), qualification);
    } else {
      assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".ast")), bytes, key);
      assertArrayEquals(Files.readAllBytes(baseline.resolve(key + ".qualification")), qualification, key);
    }
  }
}
