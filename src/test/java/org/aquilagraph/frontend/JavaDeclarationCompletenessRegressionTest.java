package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeaturePolicy;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class JavaDeclarationCompletenessRegressionTest {
  private static final CompilerConfiguration JAVA_25_CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  @TempDir Path temporaryDirectory;

  @TestFactory
  Stream<DynamicTest> givenRequiredDeclarationFacts_whenAnalysisRuns_thenStableDiagnosticsPreventCompletion() {
    return requiredDeclarationFacts().stream().map(sourceCase -> DynamicTest.dynamicTest(sourceCase.description(), () -> {
      // Arrange
      var caseDirectory = temporaryDirectory.resolve(sourceCase.description().replace(' ', '-'));
      writeSource(caseDirectory, sourceCase);
      var frontend = new JavaFrontend(new JavaSourceLoader());

      // Act
      var run = new AnalysisEngine().analyze(frontend.analyze(caseDirectory, JAVA_25_CONFIGURATION));

      // Assert
      assertNotEquals(AnalysisCompletion.COMPLETED, run.completion(), sourceCase.description());
      assertTrue(run.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
          () -> sourceCase + ": " + run.diagnostics());
      var matchingDiagnostics = run.diagnostics().stream()
          .filter(diagnostic -> diagnostic.code().equals(sourceCase.expectedDiagnosticCode())).toList();
      assertEquals(sourceCase.expectedOccurrences(), matchingDiagnostics.size(), () -> sourceCase + ": " + run.diagnostics());
      assertTrue(matchingDiagnostics.stream().allMatch(diagnostic -> diagnostic.stage() == DiagnosticStage.FRONTEND
          && diagnostic.isError() && !diagnostic.origins().isEmpty()), sourceCase.description());
    }));
  }

  @Test
  void givenImportDeclarations_whenFrontendRuns_thenImportPolicyMatchesRetainedSourceFacts() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Imported.java"), """
        import java.util.List;
        import java.util.Map;
        final class Imported { List<String> values() { return List.of(); } }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION);
    var coverage = JavaDeclarationPropertyCoverage.fromConfiguration(JAVA_25_CONFIGURATION);
    var imports = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.IMPORT_DECLARATION);

    // Assert
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        coverage.classificationOf(new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY,
            "CompilationUnitTree::CompilationUnitTree#getImports()")).orElseThrow().policy());
    assertEquals(List.of(new SyntaxValue.ImportDeclaration("java.util.List", false, false, false),
        new SyntaxValue.ImportDeclaration("java.util.Map", false, false, false)),
        imports.stream().map(node -> node.value()).toList());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals(
        "frontend.unsupported-import-declaration")));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
  }

  @Test
  void givenLocalVariableModifierAndAnnotation_whenAnalysisRuns_thenBothAbsentFactsAreDiagnosed() throws IOException {
    // Arrange
    writeSource(new SourceCase("local properties", "LocalProperties.java", """
        @interface Marker { }
        final class LocalProperties {
          int run() { @Marker final int value = 1; return value; }
        }
        """, "unused", 1));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run = new AnalysisEngine().analyze(frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION));

    // Assert
    assertNotEquals(AnalysisCompletion.COMPLETED, run.completion());
    assertEquals(2, run.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals(JavaDeclarationPropertyCoverage.VARIABLE_MODIFIERS_DIAGNOSTIC)).count());
    assertTrue(run.diagnostics().stream()
        .noneMatch(diagnostic -> diagnostic.code().equals(JavaDeclarationPropertyCoverage.DECLARATION_ANNOTATIONS_DIAGNOSTIC)));
    assertTrue(run.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
  }

  @Test
  void givenQualifiedReceiver_whenAnalysisRuns_thenReceiverAndNameExpressionAreDiagnosed() throws IOException {
    // Arrange
    writeSource(new SourceCase("qualified receiver", "Outer.java", """
        class Outer {
          class Inner { Inner(Outer Outer.this) { } }
        }
        """, "unused", 1));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run = new AnalysisEngine().analyze(frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION));

    // Assert
    assertTrue(run.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.code().equals(JavaDeclarationPropertyCoverage.RECEIVER_PARAMETER_DIAGNOSTIC)));
    assertTrue(run.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.code().equals(JavaDeclarationPropertyCoverage.VARIABLE_NAME_EXPRESSION_DIAGNOSTIC)));
    assertTrue(run.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
  }

  @Test
  void givenImplicitExecutableModifiers_whenFrontendRuns_thenAttributedModifiersAreRepresented() throws IOException {
    // Arrange
    writeSource(new SourceCase("implicit method modifiers", "Contract.java", "interface Contract { void run(); }", "unused", 1));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION);

    // Assert
    assertEquals(List.of("frontend.unsupported-tree-kind.primitive-type"),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    var method = result.program().methods().stream().filter(candidate -> candidate.name().equals("run")).findFirst().orElseThrow();
    assertEquals(Set.of("abstract", "public"), method.modifiers());
  }

  @Test
  void givenImplicitEnumMembers_whenAnalysisRuns_thenUnrepresentedEnclosedDeclarationsAreDiagnosed() throws IOException {
    // Arrange
    writeSource(new SourceCase("implicit enum members", "Empty.java", "enum Empty { ; }", "unused", 1));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run = new AnalysisEngine().analyze(frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION));

    // Assert
    assertNotEquals(AnalysisCompletion.COMPLETED, run.completion());
    assertTrue(run.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.code().equals(JavaDeclarationPropertyCoverage.ENCLOSED_DECLARATIONS_DIAGNOSTIC)));
    assertTrue(run.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
  }

  @Test
  void givenMalformedDeclaration_whenAnalysisRuns_thenCompilerErrorRemainsDistinctFromUnsupportedCapability()
      throws IOException {
    // Arrange
    writeSource(new SourceCase("malformed declaration", "Broken.java", "class Broken { void run( }", "unused", 1));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var run = new AnalysisEngine().analyze(frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION));

    // Assert
    assertEquals(AnalysisCompletion.INCOMPLETE, run.completion());
    assertTrue(run.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertTrue(run.diagnostics().stream()
        .noneMatch(diagnostic -> JavaDeclarationPropertyCoverage.isDeclarationPropertyDiagnostic(diagnostic.code())));
  }

  private static List<SourceCase> requiredDeclarationFacts() {
    return List.of(
        source("method type parameters", "GenericMethod.java", """
            final class GenericMethod {
              int plain(int value) { return value; }
              <T> T identity(T value) { return value; }
              <T extends Number, U> T convert(T value, U ignored) { return value; }
            }
            """, JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS_DIAGNOSTIC, 2),
        source("thrown types", "Throwing.java", """
            final class Throwing {
              void plain() { }
              void one() throws java.io.IOException { }
              void multiple() throws java.io.IOException, ReflectiveOperationException { }
            }
            """, JavaDeclarationPropertyCoverage.THROWN_TYPES_DIAGNOSTIC, 2),
        source("receiver parameter", "Receiver.java", """
            final class Receiver {
              void run(Receiver this) { }
            }
            """, JavaDeclarationPropertyCoverage.RECEIVER_PARAMETER_DIAGNOSTIC),
        source("variable arity", "VariableArity.java", """
            final class VariableArity {
              void run(String... values) { }
            }
            """, JavaDeclarationPropertyCoverage.VARARGS_DIAGNOSTIC),
        source("declaration annotations", "AnnotatedDeclarations.java", """
            @interface First { }
            @interface Second { }
            @First @Second final class AnnotatedDeclarations {
              int plain;
              @First int value;
              void plain(int input) { }
              @First @Second void annotated(@First int input) { }
            }
            """, JavaDeclarationPropertyCoverage.DECLARATION_ANNOTATIONS_DIAGNOSTIC, 4),
        source("annotation defaults", "Configuration.java", """
            @interface Configuration {
              int required();
              int count() default 1;
              String[] names() default {"first", "second"};
            }
            """, JavaDeclarationPropertyCoverage.ANNOTATION_DEFAULT_DIAGNOSTIC, 2),
        source("record components", "Pair.java", "record Empty() { } record Pair(int left, int right) { }",
            JavaDeclarationPropertyCoverage.RECORD_COMPONENTS_DIAGNOSTIC),
        source("module declaration", "module-info.java", "module sample.module { }",
            JavaDeclarationPropertyCoverage.MODULE_DECLARATION_DIAGNOSTIC));
  }

  private static SourceCase source(
      String description, String relativePath, String content, String expectedDiagnosticCode) {
    return source(description, relativePath, content, expectedDiagnosticCode, 1);
  }

  private static SourceCase source(
      String description, String relativePath, String content, String expectedDiagnosticCode, int expectedOccurrences) {
    return new SourceCase(description, relativePath, content, expectedDiagnosticCode, expectedOccurrences);
  }

  private void writeSource(SourceCase sourceCase) throws IOException {
    writeSource(temporaryDirectory, sourceCase);
  }

  private static void writeSource(Path directory, SourceCase sourceCase) throws IOException {
    var path = directory.resolve(sourceCase.relativePath());
    Files.createDirectories(path.getParent());
    Files.writeString(path, sourceCase.content());
  }

  private record SourceCase(
      String description, String relativePath, String content, String expectedDiagnosticCode, int expectedOccurrences) {
    @Override
    public String toString() {
      return description;
    }
  }
}
