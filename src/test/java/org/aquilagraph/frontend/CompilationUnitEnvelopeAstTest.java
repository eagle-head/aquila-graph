package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.SourceInput;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class CompilationUnitEnvelopeAstTest {
  @TempDir Path directory;
  @TestFactory
  Stream<DynamicTest> givenLegalUnitsWithoutTypes_whenProducedAndDecoded_thenExactCapturedRootsRemainComplete() {
    // Arrange
    var sources = List.of("", "// comment only\n", "/* comment */", "   \n", "package example;", "import java.util.List;",
        "import java.util.*; import static java.lang.Math.PI; import static java.lang.Math.*;", "import module java.base;", "; ;", "/* ; */ \\u003b ; // ;");
    // Act
    var tests = sources.stream().map(source -> DynamicTest.dynamicTest(
        "givenLegalNoTypeUnit_whenProducedAndDecoded_thenCapturedEnvelopeIsComplete: " + source, () -> {
          var original = produce(source, AstProfile.JAVA_SOURCE_UNITS);
          var ast = AstCodec.decode(AstCodec.encode(original), original.identity());
          var file = ast.snapshot().sourceFiles().getFirst();
          var root = ast.aggregate().roots().getFirst();
          // Assert
          assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
          assertTrue(ast.program().types().isEmpty());
          assertEquals(1, ast.aggregate().roots().size());
          assertEquals(new SourceSpan(file.sourceFileKey(), 0, source.length()), root.origin().orElseThrow());
          assertEquals(SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE, root.value(SyntaxValue.CompilationUnit.class).range());
          assertEquals(original.identity(), ast.identity());
          assertEquals(original.aggregate().writtenSourceAssociations(), ast.aggregate().writtenSourceAssociations());
          assertTrue(ast.aggregate().writtenSourceAssociation(root.nodeId()).orElseThrow().ownerPath().isEmpty());
          assertTrue(ast.diagnostics().stream().noneMatch(diagnostic -> diagnostic.isError()));
        }));
    // Assert
    return tests;
  }

  @Test
  void givenEveryImportFormWithoutTypes_whenProduced_thenQualifiedNamesFlagsAndOrderRemainWritten() throws IOException {
    // Arrange
    var source = "import java.util.List; import java.util.*; import static java.lang.Math.PI; import static java.lang.Math.*; import module java.base;";
    // Act
    var ast = produce(source, AstProfile.JAVA_SOURCE_UNITS);
    var imports = ast.aggregate().roots().getFirst().children(SyntaxRole.IMPORT_DECLARATION);
    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertEquals(List.of(new SyntaxValue.ImportDeclaration("java.util.List", false, false, false),
        new SyntaxValue.ImportDeclaration("java.util", false, false, true),
        new SyntaxValue.ImportDeclaration("java.lang.Math.PI", true, false, false),
        new SyntaxValue.ImportDeclaration("java.lang.Math", true, false, true),
        new SyntaxValue.ImportDeclaration("java.base", false, true, false)), imports.stream().map(node -> node.value()).toList());
  }

  @Test
  void givenTopLevelSemicolonsBetweenTypes_whenProducedAndDecoded_thenExactWrittenOrderAndOwnersSurvive() throws IOException {
    // Arrange
    var source = "; class Example { ; } ; interface Other {} ;";
    // Act
    var ast = produce(source, AstProfile.JAVA_SOURCE_UNITS);
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    var members = retained.aggregate().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION);
    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(List.of(SyntaxKind.EMPTY, SyntaxKind.TYPE_DECLARATION, SyntaxKind.EMPTY, SyntaxKind.TYPE_DECLARATION, SyntaxKind.EMPTY),
        members.stream().map(node -> node.syntaxKind()).toList());
    assertEquals(List.of(0L, 2L, 22L, 24L, 43L), members.stream().map(node -> node.origin().orElseThrow().startOffset()).toList());
    assertEquals(3, retained.coverage().validationEvidence().writtenInventory().contexts().stream()
        .filter(context -> context.address().partition() == SourceContext.Partition.EMPTY_UNIT_DECLARATION).count());
    assertEquals(ast.aggregate().nodes(), retained.aggregate().nodes());
  }

  @Test
  void givenCompactMembersAndEmptyDeclarations_whenProducedAndDecoded_thenUnitOwnsAllWrittenMembersInOrder() throws IOException {
    // Arrange
    var source = "import java.util.List; int field = 1; ; void main() { ; } ; class Nested { ; } ;";
    // Act
    var ast = produce(source, AstProfile.JAVA_SOURCE_UNITS);
    var retained = AstCodec.decode(AstCodec.encode(ast), ast.identity());
    var root = retained.aggregate().roots().getFirst();
    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(SyntaxValue.CompilationUnitForm.COMPACT, root.value(SyntaxValue.CompilationUnit.class).form());
    assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.EMPTY, SyntaxKind.METHOD_DECLARATION, SyntaxKind.EMPTY,
        SyntaxKind.TYPE_DECLARATION, SyntaxKind.EMPTY), root.children(SyntaxRole.COMPACT_MEMBER).stream().map(node -> node.syntaxKind()).toList());
    assertTrue(root.children(SyntaxRole.TYPE_DECLARATION).isEmpty());
    assertTrue(retained.aggregate().writtenSourceAssociation(root.nodeId()).orElseThrow().origin().isEmpty());
    assertEquals(3, retained.coverage().validationEvidence().writtenInventory().contexts().stream()
        .filter(context -> context.address().partition() == SourceContext.Partition.EMPTY_UNIT_DECLARATION).count());
  }

  @Test
  void givenCapturedPackageAndModuleAnnotations_whenProducedAndDecoded_thenRealUnitFormsAndOrderedDirectivesSurvive() throws IOException {
    // Arrange
    var sources = List.of(
        SourceFile.fromContent(Path.of("sample/Mark.java"), "package sample; import java.lang.annotation.*; @Target({ElementType.PACKAGE, ElementType.MODULE}) public @interface Mark {}"),
        SourceFile.fromContent(Path.of("sample/package-info.java"), "@sample.Mark package sample;"),
        SourceFile.fromContent(Path.of("sample/Service.java"), "package sample; public interface Service {}"),
        SourceFile.fromContent(Path.of("sample/First.java"), "package sample; public class First implements Service {}"),
        SourceFile.fromContent(Path.of("sample/Second.java"), "package sample; public class Second implements Service {}"),
        SourceFile.fromContent(Path.of("module-info.java"), "@sample.Mark module sample.module { requires static transitive java.sql; exports sample to java.sql, java.logging; opens sample to java.logging, java.sql; uses sample.Service; provides sample.Service with sample.First, sample.Second; }"));
    var snapshot = InputSnapshot.fromSources(directory, sources);
    // Act
    var produced = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, configuration(), AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
    var retained = AstCodec.decode(AstCodec.encode(produced), produced.identity());
    var moduleRoot = retained.aggregate().roots().getFirst();
    var module = moduleRoot.requiredChild(SyntaxRole.MODULE_DECLARATION);
    var directives = module.children(SyntaxRole.MODULE_DIRECTIVE);
    var packageRoot = retained.aggregate().roots().stream().filter(root -> root.sourceFileKey().equals(sources.get(1).sourceFileKey())).findFirst().orElseThrow();
    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(SyntaxValue.CompilationUnitForm.MODULAR, moduleRoot.value(SyntaxValue.CompilationUnit.class).form());
    assertEquals(List.of(JavaSourceKind.REQUIRES, JavaSourceKind.EXPORTS, JavaSourceKind.OPENS, JavaSourceKind.USES, JavaSourceKind.PROVIDES), directives.stream().map(node -> node.sourceKind()).toList());
    assertEquals(List.of("java.sql", "java.logging"), directives.get(1).value(SyntaxValue.Exports.class).targets().orElseThrow().stream().map(name -> String.join(".", name.components())).toList());
    assertEquals(List.of("java.logging", "java.sql"), directives.get(2).value(SyntaxValue.Opens.class).targets().orElseThrow().stream().map(name -> String.join(".", name.components())).toList());
    assertEquals(List.of("sample.First", "sample.Second"), directives.get(4).value(SyntaxValue.Provides.class).providers().stream().map(name -> String.join(".", name.components())).toList());
    assertEquals(1, module.children(SyntaxRole.ANNOTATION).size());
    assertEquals(1, packageRoot.requiredChild(SyntaxRole.PACKAGE_DECLARATION).children(SyntaxRole.ANNOTATION).size());
    assertTrue(packageRoot.children(SyntaxRole.TYPE_DECLARATION).isEmpty());
    assertEquals(produced.identity(), retained.identity());
    assertEquals(produced.aggregate().nodes(), retained.aggregate().nodes());
  }

  @Test
  void givenAnOpenModuleWithoutTypes_whenProduced_thenNoDeclarationIsInvented() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("module-info.java"), "open module example.module { requires java.logging; }")));
    // Act
    var ast = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, configuration(), AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
    // Assert
    assertEquals(AstCompletion.COMPLETE, ast.completion(), ast.diagnostics().toString());
    assertTrue(ast.program().types().isEmpty());
    assertTrue(ast.aggregate().roots().getFirst().requiredChild(SyntaxRole.MODULE_DECLARATION).value(SyntaxValue.ModuleDeclaration.class).open());
  }

  @TestFactory
  Stream<DynamicTest> givenOriginalProfilesAndNoTypeSources_whenProduced_thenOriginalUnavailableQualificationRemains() {
    // Arrange
    var profiles = List.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED, AstProfile.JAVA_SOURCE_INITIALIZATION,
        AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION, AstProfile.JAVA_SOURCE_DECLARATIONS);
    // Act
    var tests = profiles.stream().map(profile -> DynamicTest.dynamicTest("given" + profile.version() + "NoTypeUnit_whenProduced_thenOriginalQualificationRemains", () -> {
      var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "package example;")));
      var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, configuration(), profile);
      // Assert
      assertEquals(AstCompletion.UNAVAILABLE, outcome.completion());
      assertTrue(outcome.result().isEmpty());
      assertEquals(profile, outcome.profile());
    }));
    // Assert
    return tests;
  }

  @Test
  void givenActualFilesAndUnorderedSnapshotInput_whenProduced_thenCaptureIdentityIsCanonicalAndContentChangesRemainQualified() throws IOException {
    // Arrange
    Files.writeString(directory.resolve("Empty.java"), "");
    Files.writeString(directory.resolve("Comment.java"), "// comment\n");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var captured = new JavaSourceLoader().discover(directory);
    var reversed = InputSnapshot.fromSources(directory, captured.sourceFiles().reversed());
    // Act
    var fromRoot = frontend.produce(new AstProductionRequest(new SourceInput.Root(directory), configuration(), AstProfile.JAVA_SOURCE_UNITS)).result().orElseThrow();
    var fromSnapshot = frontend.produce(reversed, configuration(), AstProfile.JAVA_SOURCE_UNITS).result().orElseThrow();
    var changed = produce("// changed\n", AstProfile.JAVA_SOURCE_UNITS);
    // Assert
    assertEquals(AstCompletion.COMPLETE, fromRoot.completion());
    assertEquals(captured.sourceFiles(), fromRoot.snapshot().sourceFiles());
    assertEquals(fromRoot.identity(), fromSnapshot.identity());
    assertEquals(fromRoot.aggregate().roots(), fromSnapshot.aggregate().roots());
    assertNotEquals(fromRoot.identity(), changed.identity());
    var local = fromRoot.occurrence(fromRoot.aggregate().roots().getFirst()).orElseThrow();
    assertEquals(fromRoot.aggregate().roots().getFirst(), fromRoot.node(local).orElseThrow());
    assertThrows(IllegalArgumentException.class, () -> changed.node(local));
    assertThrows(IllegalArgumentException.class, () -> fromRoot.node(new SourceOccurrence(changed.identity(), local.nodeKey())));
  }

  @Test
  void givenInvalidImportWithoutTypes_whenProduced_thenActualCompilerErrorsPreventCompleteness() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), "import missing.Type;")));
    // Act
    AstOutcome outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, configuration(), AstProfile.JAVA_SOURCE_UNITS);
    // Assert
    assertNotEquals(AstCompletion.COMPLETE, outcome.completion());
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().contains("doesnt.exist")));
  }

  @Test
  void givenAMissingRequiredCompilerUnitInMixedCapture_whenRealCompilerValuesAreTranslated_thenEveryRootIsWithheld() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Empty.java"), ""),
        SourceFile.fromContent(Path.of("Example.java"), "class Example {}")));
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration()), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, true);
      var translator = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(), compilation.treeInventory(),
          JavaLanguageFeatureCoverage.fromConfiguration(configuration()), true, true, true);
      translator.enableDeclarationFidelity(true);
      translator.enableSourceEnvelope(true);
      // Act
      var result = translator.translate(snapshot, compilation.units().subList(0, 1));
      // Assert
      assertTrue(result.value().aggregate().isEmpty());
      assertTrue(result.value().program().types().isEmpty());
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-compilation-unit") && diagnostic.isError()));
    }
  }

  @Test
  void givenZeroCapturedFiles_whenProduced_thenNoCompilerOrUnitIsInvented() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of());
    var frontend = new JavaFrontend(new JavaSourceLoader(), new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()), () -> { throw new AssertionError("Zero capture must not acquire the compiler"); }));
    // Act
    var outcome = frontend.produce(snapshot, configuration(), AstProfile.JAVA_SOURCE_UNITS);
    // Assert
    assertEquals(AstCompletion.UNAVAILABLE, outcome.completion());
    assertTrue(outcome.result().isEmpty());
    assertTrue(outcome.capturedEnvironment().isEmpty());
    assertEquals(snapshot, outcome.capturedInput().orElseThrow());
  }

  private static AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, configuration(), profile).result().orElseThrow();
  }

  private static CompilerConfiguration configuration() {
    return new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of());
  }
}
