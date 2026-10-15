package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.ToolProvider;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaAnnotationValueEvaluationTest {
  @TempDir Path sourceRoot;

  @Test
  void givenConstantExpressionBoundariesAtDifferentSites_whenAnalyzed_thenValuesAndDefaultsEqualActualCompilerValues()
      throws IOException {
    // Arrange
    var expressions = new LinkedHashMap<String, String>();
    expressions.put("byte", "{127, -128, (byte)128, (byte)Double.POSITIVE_INFINITY}");
    expressions.put("short", "{32767, -32768, (short)65535, (short)Double.NaN}");
    expressions.put("char", "{'A', 0, 65535, (char)-1, (char)Double.NEGATIVE_INFINITY}");
    expressions.put("int", """
        {Integer.MIN_VALUE/-1, Integer.MAX_VALUE+1, 1<<33L, -1>>>33L, -8>>1,
         (byte)128, (short)65535, (char)-1, (int)Double.NaN,
         (int)Double.POSITIVE_INFINITY, (int)Double.NEGATIVE_INFINITY,
         true ? (byte)127 : (short)128, false ? 2 : 'Z', ~0, 'A'+1,
         -2147483648, (int)(16777217L + 0.0f), 7*3, 7/3, 7%3, 7-3, 7&3, 7|3, 7^3, +2}
        """);
    expressions.put("long", """
        {Long.MIN_VALUE/-1L, Long.MAX_VALUE+1L, -1L>>>65,
         (long)Double.NaN, (long)Double.POSITIVE_INFINITY, 1L<<-1, -8L>>1,
         7L*3, 7L/3, 7L%3, 7L-3, 7L&3, 7L|3, 7L^3, ~0L, +2L}
        """);
    expressions.put("float", """
        {0.0f, -0.0f, 0.0f/0.0f, 1.0f/0.0f, -1.0f/0.0f,
         16777217L, 16777216.0f+1.0f, Float.MIN_VALUE/2, 7.0f%3, 7.0f*3, 7.0f-3, +2.0f}
        """);
    expressions.put("double", """
        {0.0, -0.0, 0.0/0.0, 1.0/0.0, -1.0/0.0,
         9007199254740993L, Double.MIN_VALUE/2, 7.0%3, 7.0*3, 7.0-3, +2.0}
        """);
    expressions.put("boolean", """
        {true^false, true!=false, "a"+"b"=="ab", "a"!="b", Float.NaN==Float.NaN,
         16777217L==16777216.0f, 9007199254740993L==9007199254740992.0,
         1<2, 1L<=2, 3.0f>2, 3.0>=2, 0.0==-0.0, !(1==2), true&false, true|false,
         true&&false, false||true, false ? false : true}
        """);
    expressions.put("String", """
        {"a"+1+2, 1+2+"a", ""+'a', ""+(-0.0f), ""+(0.0/0.0),
         ""+(true ? 1 : 2.0), ""+(false ? 'a' : (byte)2)}
        """);
    var members = expressions.entrySet().stream().map(entry -> entry.getKey() + "[] " + entry.getKey().toLowerCase()
        + "Values() default " + entry.getValue() + ";").collect(Collectors.joining("\n"));
    var annotation = "@Values(" + expressions.entrySet().stream().map(entry -> entry.getKey().toLowerCase()
        + "Values=" + entry.getValue()).collect(Collectors.joining(",")) + ")";
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.SOURCE) @Target({ElementType.TYPE, ElementType.TYPE_USE})
        @interface Values { %s }
        %s class Sample { %s Sample() {} %s String field; }
        record RecordSample(%s String value) {}
        """.formatted(members, annotation, annotation, annotation, annotation);
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, configuration);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration),
        new SnapshotSourceCatalog(result.snapshot()))) {
      var compilation = session.analyze(session.parse(), JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var sample = unit.getTypeDecls().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
          .filter(tree -> tree.getSimpleName().contentEquals("Sample")).findFirst().orElseThrow();
      var element = (TypeElement) compilation.semantics().element(TreePath.getPath(unit, sample));
      var actualMirror = element.getAnnotationMirrors().getFirst();
      var compilerValues = actualMirror.getElementValues().entrySet().stream().collect(Collectors.toMap(
          entry -> entry.getKey().getSimpleName().toString(), entry -> rawArray(entry.getValue())));
      assertEquals(9, compilerValues.size());
      var occurrences = result.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
          .filter(node -> node.semanticReference().flatMap(reference -> reference.symbolKey()).filter("type:Values"::equals).isPresent())
          .toList();
      assertEquals(4, occurrences.size());
      for (var occurrence : occurrences) {
        var projected = occurrence.children(SyntaxRole.ANNOTATION_ARGUMENT).stream()
            .map(node -> node.value(SyntaxValue.AnnotationArgument.class)).collect(Collectors.toMap(
                argument -> argument.explicitName().orElseThrow(), argument -> primitiveArray(argument.value())));
        assertEquals(compilerValues, projected, occurrence.structuralPath().toString());
      }
      var defaults = result.aggregate().orElseThrow().nodes().stream().map(node -> node.value())
          .filter(SyntaxValue.ExecutableDeclaration.class::isInstance).map(SyntaxValue.ExecutableDeclaration.class::cast)
          .filter(value -> value.defaultValue().isPresent()).collect(Collectors.toMap(
              SyntaxValue.ExecutableDeclaration::name, value -> primitiveArray(value.defaultValue().orElseThrow())));
      var compilerDefaults = actualMirror.getAnnotationType().asElement().getEnclosedElements().stream()
          .filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
          .collect(Collectors.toMap(member -> member.getSimpleName().toString(), member -> rawArray(member.getDefaultValue())));
      assertEquals(compilerValues, compilerDefaults);
      assertEquals(compilerDefaults, defaults);
    }
  }

  @Test
  void givenDifferentRealCompilerMirrors_whenComparedWithSourceValues_thenChangedScalarNestedAndArrayValuesAreRejected()
      throws IOException {
    // Arrange
    var source = """
        @interface Inner { int value(); }
        @interface Choice { int scalar(); Inner nested(); int[] array(); }
        @Choice(scalar=1,nested=@Inner(2),array={3,4}) class Original {}
        @Choice(scalar=9,nested=@Inner(2),array={3,4}) class Scalar {}
        @Choice(scalar=1,nested=@Inner(9),array={3,4}) class Nested {}
        @Choice(scalar=1,nested=@Inner(2),array={4,3}) class Reordered {}
        @Choice(scalar=1,nested=@Inner(2),array={3}) class Fewer {}
        """;
    Files.writeString(sourceRoot.resolve("Choices.java"), source);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, configuration);
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
    var written = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration type && type.name().equals("Original"))
        .findFirst().orElseThrow().requiredChild(SyntaxRole.ANNOTATION);

    // Act
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration),
        new SnapshotSourceCatalog(result.snapshot()))) {
      var compilation = session.analyze(session.parse(), JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var correlation = new JavaAnnotationMirrorCorrelation(compilation.semantics());
      var mirrors = unit.getTypeDecls().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
          .filter(tree -> !tree.getModifiers().getAnnotations().isEmpty()).collect(Collectors.toMap(
              tree -> tree.getSimpleName().toString(), tree -> compilation.semantics().element(TreePath.getPath(unit, tree))
                  .getAnnotationMirrors().getFirst()));

      // Assert
      assertTrue(correlation.matches(mirrors.get("Original"), written));
      for (var name : List.of("Scalar", "Nested", "Reordered", "Fewer")) {
        assertFalse(correlation.matches(mirrors.get(name), written), name);
      }
    }
  }

  private static List<?> rawArray(AnnotationValue value) {
    return ((List<?>) value.getValue()).stream().map(item -> ((AnnotationValue) item).getValue()).toList();
  }

  private static List<?> primitiveArray(AnnotationElementValue value) {
    return ((AnnotationElementValue.Array) value).elements().stream().map(item -> switch (item) {
      case AnnotationScalar.BooleanValue scalar -> scalar.value();
      case AnnotationScalar.ByteValue scalar -> scalar.value();
      case AnnotationScalar.ShortValue scalar -> scalar.value();
      case AnnotationScalar.CharValue scalar -> scalar.value();
      case AnnotationScalar.IntValue scalar -> scalar.value();
      case AnnotationScalar.LongValue scalar -> scalar.value();
      case AnnotationScalar.FloatValue scalar -> scalar.value();
      case AnnotationScalar.DoubleValue scalar -> scalar.value();
      case AnnotationScalar.StringValue scalar -> scalar.value();
      default -> throw new AssertionError("Expected a primitive or String array element: " + item);
    }).toList();
  }
}
