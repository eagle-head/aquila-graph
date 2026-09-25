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
  Stream<DynamicTest> givenUnrepresentedDeclarationFacts_whenAnalysisRuns_thenStableDiagnosticsPreventCompletion() {
    return unrepresentedDeclarationFacts().stream().map(sourceCase -> DynamicTest.dynamicTest(sourceCase.description(), () -> {
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
  void givenDeclarationOnlyMarkers_whenAnalysisRuns_thenAnnotationContentPermitsCompletion() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("AnnotatedDeclarations.java"), """
        @interface First { }
        @interface Second { }
        @First @Second final class AnnotatedDeclarations {
          @First int value;
          @First @Second void annotated(@First int input) { }
        }
        """);

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25_CONFIGURATION);
    var run = new AnalysisEngine().analyze(input);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETED, run.completion(), () -> run.diagnostics().toString());
    assertEquals(6, input.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation).count());
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
  void givenModuleSyntax_whenCoverageIsLoaded_thenModuleAndDirectivePoliciesAreRepresented() {
    // Arrange
    var property = JavaDeclarationPropertyCoverage.fromConfiguration(JAVA_25_CONFIGURATION);
    var kinds = JavaCompilerKindCoverage.fromConfiguration(JAVA_25_CONFIGURATION);

    // Act
    var moduleProperty = property.classificationOf(new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY,
        JavaDeclarationPropertyCoverage.COMPILATION_UNIT_MODULE)).orElseThrow().policy();
    var directivePolicies = List.of("MODULE", "REQUIRES", "EXPORTS", "OPENS", "USES", "PROVIDES").stream()
        .map(identifier -> kinds.classificationOf(new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, identifier))
            .orElseThrow().policy()).toList();

    // Assert
    assertEquals(LanguageFeaturePolicy.REPRESENTED, moduleProperty);
    assertEquals(List.of(LanguageFeaturePolicy.REPRESENTED, LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.REPRESENTED, LanguageFeaturePolicy.REPRESENTED, LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.REPRESENTED), directivePolicies);
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
    assertEquals(List.of("frontend.unsupported-local-declaration-modifiers", "frontend.unsupported-local-annotation-content"),
        run.diagnostics().stream().map(diagnostic -> diagnostic.code())
            .filter(code -> code.startsWith("frontend.unsupported-local-")).toList());
    assertTrue(run.diagnostics().stream()
        .noneMatch(diagnostic -> diagnostic.code().equals(JavaDeclarationPropertyCoverage.DECLARATION_ANNOTATIONS_DIAGNOSTIC)));
    assertTrue(run.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
  }

  @Test
  void givenQualifiedReceiver_whenFrontendRuns_thenReceiverAndNameExpressionAreRetained() throws IOException {
    // Arrange
    writeSource(new SourceCase("qualified receiver", "Outer.java", """
        class Outer {
          class Inner { Inner(Outer Outer.this) { } }
        }
        """, "unused", 1));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION);
    var receiver = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION)
        .requiredChild(SyntaxRole.MEMBER).requiredChild(SyntaxRole.MEMBER).requiredChild(SyntaxRole.RECEIVER_PARAMETER);

    // Assert
    assertEquals("this", receiver.value(SyntaxValue.ReceiverParameter.class).name());
    assertEquals("Outer.this", receiver.requiredChild(SyntaxRole.QUALIFIER).value(SyntaxValue.SourceSymbol.class).name());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals(
        "frontend.unsupported-receiver-parameter")
        || diagnostic.code().equals("frontend.unsupported-variable-name-expression")));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
  }

  @Test
  void givenImplicitExecutableModifiers_whenFrontendRuns_thenAttributedModifiersAreRepresented() throws IOException {
    // Arrange
    writeSource(new SourceCase("implicit method modifiers", "Contract.java", "interface Contract { void run(); }", "unused", 1));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION);

    // Assert
    assertEquals(List.of(), result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    var method = result.program().methods().stream().filter(candidate -> candidate.name().equals("run")).findFirst().orElseThrow();
    assertEquals(Set.of("abstract", "public"), method.modifiers());
  }

  @Test
  void givenImplicitEnumMembers_whenAnalysisRuns_thenSynthesizedDeclarationsAreRetained() throws IOException {
    // Arrange
    writeSource(new SourceCase("implicit enum members", "Empty.java", "enum Empty { ; }", "unused", 1));
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION);
    var run = new AnalysisEngine().analyze(result);

    // Assert
    assertEquals(AnalysisCompletion.COMPLETED, run.completion(), () -> run.diagnostics().toString());
    assertFalse(result.program().types().getFirst().synthesizedDeclarations().isEmpty());
    assertTrue(result.program().types().getFirst().synthesizedDeclarations().stream()
        .allMatch(declaration -> declaration.ownerTypeKey().equals(result.program().types().getFirst().typeKey())));
    assertTrue(run.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-enclosed-declarations")));
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

  private static List<SourceCase> unrepresentedDeclarationFacts() {
    return List.of(
        source("annotation defaults", "Configuration.java", """
            @interface Configuration {
              int required();
              int count() default 1;
              String[] names() default {"first", "second"};
            }
            """, JavaDeclarationPropertyCoverage.ANNOTATION_DEFAULT_DIAGNOSTIC, 2),
        source("field initializer content", "Initialized.java", "class Initialized { int value = 1; }",
            "frontend.unsupported-field-initializer"));
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
