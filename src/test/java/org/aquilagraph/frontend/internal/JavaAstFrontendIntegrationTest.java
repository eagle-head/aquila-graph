package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.tools.ToolProvider;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.PackageQualifierEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaAstFrontendIntegrationTest {
  private static final CompilerConfiguration JAVA_25 = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @TempDir Path sourceRoot;

  @Test
  void givenEmptyAndNamedPackageSources_whenAnalyzed_thenEachRootRetainsItsWrittenUnitFacts() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Empty.java"), "");
    var packageDirectory = Files.createDirectory(sourceRoot.resolve("sample"));
    var source = "package sample; class Named { }";
    Files.writeString(packageDirectory.resolve("Named.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var empty = aggregate.roots().getFirst();
    var named = aggregate.roots().get(1);
    var packageNode = named.requiredChild(SyntaxRole.PACKAGE_DECLARATION);
    var range = packageNode.origin().orElseThrow();

    // Assert
    assertEquals(2, aggregate.roots().size());
    assertEquals(new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY), empty.value());
    assertEquals(new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY), named.value());
    assertTrue(empty.children().isEmpty());
    assertEquals(new SyntaxValue.PackageDeclaration("sample", Optional.of(new PackageQualifierEvidence(JavaSourceKind.IDENTIFIER,
        new SourceSpan(named.sourceFileKey(), 8, 14)))), packageNode.value());
    assertEquals(named.sourceFileKey(), packageNode.sourceFileKey());
    assertEquals("package sample;", source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())));
    assertEquals(List.of(), named.children(SyntaxRole.IMPORT_DECLARATION));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals(
        "frontend.unsupported-package-declaration")
        || diagnostic.code().equals("frontend.unsupported-tree-kind.package")));
  }

  @Test
  void givenOrderedImportForms_whenAnalyzed_thenNamesFlagsOrderAndRangesAreRetained() throws IOException {
    // Arrange
    var source = """
        import java.util.List;
        import java.util.*;
        import static java.util.Collections.emptyList;
        import static java.util.Collections.*;
        import module java.base;
        class Imports { }
        """;
    Files.writeString(sourceRoot.resolve("Imports.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var repeated = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var root = result.aggregate().orElseThrow().roots().getFirst();
    var imports = root.children(SyntaxRole.IMPORT_DECLARATION);

    // Assert
    assertEquals(List.of(
        new SyntaxValue.ImportDeclaration("java.util.List", false, false, false),
        new SyntaxValue.ImportDeclaration("java.util", false, false, true),
        new SyntaxValue.ImportDeclaration("java.util.Collections.emptyList", true, false, false),
        new SyntaxValue.ImportDeclaration("java.util.Collections", true, false, true),
        new SyntaxValue.ImportDeclaration("java.base", false, true, false)),
        imports.stream().map(node -> node.value()).toList());
    assertEquals(List.of(0, 1, 2, 3, 4), root.children().stream()
        .filter(child -> child.role() == SyntaxRole.IMPORT_DECLARATION).map(child -> child.position()).toList());
    assertEquals(source.lines().filter(line -> line.startsWith("import ")).toList(), imports.stream()
        .map(node -> {
          var range = node.origin().orElseThrow();
          return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
        }).toList());
    assertTrue(imports.stream().allMatch(node -> node.sourceFileKey().equals(root.sourceFileKey())));
    assertEquals(result.aggregate().orElseThrow(), repeated.aggregate().orElseThrow());
    assertEquals(result.diagnostics(), repeated.diagnostics());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals(
        "frontend.unsupported-import-declaration")
        || diagnostic.code().equals("frontend.unsupported-tree-kind.import")
        || diagnostic.code().startsWith("compiler.err.")), () -> result.diagnostics().toString());
  }

  @Test
  void givenModularUnitWithImport_whenAnalyzed_thenModuleAndImportRemainOwnedByRoot() throws IOException {
    // Arrange
    var source = "import java.lang.Deprecated; module sample.module { }";
    Files.writeString(sourceRoot.resolve("module-info.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var root = result.aggregate().orElseThrow().roots().getFirst();
    var imported = root.requiredChild(SyntaxRole.IMPORT_DECLARATION);
    var module = root.requiredChild(SyntaxRole.MODULE_DECLARATION);

    // Assert
    assertEquals(new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.MODULAR), root.value());
    assertEquals(new SyntaxValue.ImportDeclaration("java.lang.Deprecated", false, false, false), imported.value());
    assertEquals(root.sourceFileKey(), imported.sourceFileKey());
    var range = imported.origin().orElseThrow();
    assertEquals("import java.lang.Deprecated;",
        source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())));
    assertEquals(List.of(), root.children(SyntaxRole.TYPE_DECLARATION));
    assertEquals(JavaSourceKind.MODULE, module.sourceKind());
    assertEquals(new SyntaxValue.ModuleDeclaration(name("sample", "module"), false), module.value());
    assertEquals(List.of(), module.children(SyntaxRole.MODULE_DIRECTIVE));
    assertEquals("module sample.module { }", source.substring(Math.toIntExact(module.origin().orElseThrow().startOffset()),
        Math.toIntExact(module.origin().orElseThrow().endOffsetExclusive())));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-module-declaration")));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-import-declaration")
        || diagnostic.code().startsWith("compiler.err.")), () -> result.diagnostics().toString());
  }

  @Test
  void givenOrderedModuleDirectives_whenAnalyzed_thenEveryTypedOperandAndRangeIsRetained() throws IOException {
    // Arrange
    var moduleSource = """
        module sample.module {
          requires static transitive java.sql;
          exports sample.api;
          exports sample.impl to java.sql, java.logging;
          opens sample.impl;
          opens sample.api to java.sql, java.logging;
          uses sample.api.Service;
          provides sample.api.Service with sample.impl.First, sample.impl.Second;
        }
        """;
    Files.writeString(sourceRoot.resolve("module-info.java"), moduleSource);
    var api = Files.createDirectories(sourceRoot.resolve("sample/api"));
    var implementation = Files.createDirectories(sourceRoot.resolve("sample/impl"));
    Files.writeString(api.resolve("Service.java"), "package sample.api; public interface Service { }");
    Files.writeString(implementation.resolve("First.java"),
        "package sample.impl; public final class First implements sample.api.Service { }");
    Files.writeString(implementation.resolve("Second.java"),
        "package sample.impl; public final class Second implements sample.api.Service { }");

    // Act
    var first = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var second = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var module = first.aggregate().orElseThrow().roots().stream()
        .filter(root -> root.value().equals(new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.MODULAR)))
        .findFirst().orElseThrow().requiredChild(SyntaxRole.MODULE_DECLARATION);
    var directives = module.children(SyntaxRole.MODULE_DIRECTIVE);

    // Assert
    assertEquals(new SyntaxValue.ModuleDeclaration(name("sample", "module"), false), module.value());
    assertEquals(List.of(JavaSourceKind.REQUIRES, JavaSourceKind.EXPORTS, JavaSourceKind.EXPORTS, JavaSourceKind.OPENS,
        JavaSourceKind.OPENS, JavaSourceKind.USES, JavaSourceKind.PROVIDES),
        directives.stream().map(node -> node.sourceKind()).toList());
    assertEquals(List.of(
        new SyntaxValue.Requires(name("java", "sql"), true, true),
        new SyntaxValue.Exports(name("sample", "api"), Optional.empty()),
        new SyntaxValue.Exports(name("sample", "impl"), Optional.of(List.of(name("java", "sql"), name("java", "logging")))),
        new SyntaxValue.Opens(name("sample", "impl"), Optional.empty()),
        new SyntaxValue.Opens(name("sample", "api"), Optional.of(List.of(name("java", "sql"), name("java", "logging")))),
        new SyntaxValue.Uses(name("sample", "api", "Service")),
        new SyntaxValue.Provides(name("sample", "api", "Service"),
            List.of(name("sample", "impl", "First"), name("sample", "impl", "Second")))),
        directives.stream().map(node -> node.value()).toList());
    assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), module.children().stream()
        .filter(child -> child.role() == SyntaxRole.MODULE_DIRECTIVE).map(child -> child.position()).toList());
    assertEquals(moduleSource.lines().filter(line -> line.stripLeading().startsWith("requires ")
        || line.stripLeading().startsWith("exports ") || line.stripLeading().startsWith("opens ")
        || line.stripLeading().startsWith("uses ") || line.stripLeading().startsWith("provides "))
        .map(String::strip).toList(), directives.stream().map(node -> {
          var range = node.origin().orElseThrow();
          return moduleSource.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
        }).toList());
    assertTrue(directives.stream().allMatch(node -> node.sourceFileKey().equals(module.sourceFileKey())));
    assertEquals(first.aggregate(), second.aggregate());
    assertEquals(first.diagnostics(), second.diagnostics());
    assertTrue(first.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")
        || diagnostic.code().equals("frontend.unsupported-module-declaration")
        || diagnostic.code().startsWith("frontend.unsupported-tree-kind.requires")
        || diagnostic.code().startsWith("frontend.unsupported-tree-kind.exports")
        || diagnostic.code().startsWith("frontend.unsupported-tree-kind.opens")
        || diagnostic.code().startsWith("frontend.unsupported-tree-kind.uses")
        || diagnostic.code().startsWith("frontend.unsupported-tree-kind.provides")), () -> first.diagnostics().toString());
  }

  @Test
  void givenAnnotatedOpenModule_whenAnalyzed_thenOpenStateAndAnnotationAttachmentAreRetained() throws IOException {
    // Arrange
    var source = "@Deprecated open module sample.access { }";
    Files.writeString(sourceRoot.resolve("module-info.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var module = result.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.MODULE_DECLARATION);
    var annotation = module.requiredChild(SyntaxRole.ANNOTATION);

    // Assert
    assertEquals(new SyntaxValue.ModuleDeclaration(name("sample", "access"), true), module.value());
    assertEquals(JavaSourceKind.ANNOTATION, annotation.sourceKind());
    assertEquals("@Deprecated", source.substring(Math.toIntExact(annotation.origin().orElseThrow().startOffset()),
        Math.toIntExact(annotation.origin().orElseThrow().endOffsetExclusive())));
    assertEquals(List.of(), module.children(SyntaxRole.MODULE_DIRECTIVE));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")
        || diagnostic.code().equals("frontend.unsupported-module-declaration")), () -> result.diagnostics().toString());
  }

  @Test
  void givenMalformedModuleDirective_whenAnalyzed_thenCompilerErrorDoesNotPublishAnEmptyDirective() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("module-info.java"), "module sample.module { requires ; }");

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var run = new SourceAnalysisFixture().analyze(result);

    // Assert
    assertFalse(run.completion() == AnalysisCompletion.COMPLETE);
    assertTrue(run.diagnostics().values().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertTrue(result.aggregate().stream().flatMap(aggregate -> aggregate.roots().stream())
        .flatMap(root -> root.children(SyntaxRole.MODULE_DECLARATION).stream())
        .flatMap(module -> module.children(SyntaxRole.MODULE_DIRECTIVE).stream()).noneMatch(node -> node.value() instanceof SyntaxValue.Requires));
  }

  @Test
  void givenCompactUnitWithImport_whenAnalyzed_thenWrittenMembersBelongToCompactRoot() throws IOException {
    // Arrange
    var source = "import java.util.List; int count; void main() { System.out.println(count); }";
    Files.writeString(sourceRoot.resolve("Compact.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    assertTrue(result.aggregate().isPresent(), () -> result.diagnostics().toString());
    var root = result.aggregate().orElseThrow().roots().getFirst();
    var members = root.children(SyntaxRole.COMPACT_MEMBER);
    var fieldRange = members.getFirst().origin().orElseThrow();
    var methodRange = members.get(1).origin().orElseThrow();

    // Assert
    assertEquals(new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.COMPACT), root.value());
    assertEquals(new SyntaxValue.ImportDeclaration("java.util.List", false, false, false),
        root.requiredChild(SyntaxRole.IMPORT_DECLARATION).value());
    assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION),
        members.stream().map(node -> node.syntaxKind()).toList());
    assertTrue(members.stream().allMatch(member -> member.sourceFileKey().equals(root.sourceFileKey())));
    assertEquals("int count;", source.substring(Math.toIntExact(fieldRange.startOffset()), Math.toIntExact(fieldRange.endOffsetExclusive())));
    assertEquals("void main() { System.out.println(count); }",
        source.substring(Math.toIntExact(methodRange.startOffset()), Math.toIntExact(methodRange.endOffsetExclusive())));
    assertEquals(source, source.substring(Math.toIntExact(root.origin().orElseThrow().startOffset()),
        Math.toIntExact(root.origin().orElseThrow().endOffsetExclusive())));
    assertEquals(List.of(), root.children(SyntaxRole.TYPE_DECLARATION));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenMalformedImport_whenAnalyzed_thenCompilerErrorPreventsCompletionWithoutImportFact() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Broken.java"), "import ; class Broken { }");

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertTrue(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    var imports = input.aggregate().stream().flatMap(aggregate -> aggregate.roots().stream())
        .flatMap(root -> root.children(SyntaxRole.IMPORT_DECLARATION).stream()).toList();
    assertTrue(imports.isEmpty(), () -> imports + " diagnostics=" + input.diagnostics());
    assertFalse(run.completion() == AnalysisCompletion.COMPLETE);
  }

  @Test
  void givenMalformedPackage_whenAnalyzed_thenCompilerErrorPreventsCompletionWithoutPackageFact() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Broken.java"), "package ; class Broken { }");

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertTrue(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertTrue(input.aggregate().stream().flatMap(aggregate -> aggregate.roots().stream())
        .noneMatch(root -> !root.children(SyntaxRole.PACKAGE_DECLARATION).isEmpty()));
    assertFalse(run.completion() == AnalysisCompletion.COMPLETE);
  }

  @Test
  void givenImportWithoutSemicolon_whenAnalyzed_thenRecoveredNameIsNotPublished() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Broken.java"), "import java.util.List");

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertTrue(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertTrue(input.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.IMPORT_DECLARATION).isEmpty());
    assertFalse(run.completion() == AnalysisCompletion.COMPLETE);
  }

  @Test
  void givenPackageWithoutSemicolon_whenAnalyzed_thenRecoveredNameIsNotPublished() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Broken.java"), "package sample");

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertTrue(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertTrue(input.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.PACKAGE_DECLARATION).isEmpty());
    assertFalse(run.completion() == AnalysisCompletion.COMPLETE);
  }

  @Test
  void givenUnicodeEscapedSemicolons_whenAnalyzed_thenDeclarationsRemainRepresented() throws IOException {
    // Arrange
    var unicodeSemicolon = "\\" + "u003b";
    Files.writeString(sourceRoot.resolve("Example.java"),
        "package sample" + unicodeSemicolon + " import java.util.List" + unicodeSemicolon + " class Example { }");

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var root = input.aggregate().orElseThrow().roots().getFirst();

    // Assert
    assertEquals(1, root.children(SyntaxRole.PACKAGE_DECLARATION).size());
    assertEquals(1, root.children(SyntaxRole.IMPORT_DECLARATION).size());
    assertTrue(input.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> input.diagnostics().toString());
  }

  @Test
  void givenAnnotatedPackageInfo_whenAnalyzed_thenAnnotationAttachmentRetainsOwnerAndRange() throws IOException {
    // Arrange
    var directory = Files.createDirectory(sourceRoot.resolve("sample"));
    var source = "@Deprecated @SuppressWarnings(\"unused\") package sample;";
    Files.writeString(directory.resolve("package-info.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var root = result.aggregate().orElseThrow().roots().getFirst();
    var packageNode = root.requiredChild(SyntaxRole.PACKAGE_DECLARATION);
    var annotations = packageNode.children(SyntaxRole.ANNOTATION);
    var firstRange = annotations.getFirst().origin().orElseThrow();
    var secondRange = annotations.get(1).origin().orElseThrow();

    // Assert
    assertEquals(1, result.aggregate().orElseThrow().roots().size());
    assertEquals(List.of(0, 1), packageNode.children().stream().map(child -> child.position()).toList());
    assertTrue(annotations.stream().allMatch(annotation -> annotation.sourceKind() == JavaSourceKind.ANNOTATION
        && annotation.sourceFileKey().equals(packageNode.sourceFileKey())));
    assertEquals("@Deprecated", source.substring(Math.toIntExact(firstRange.startOffset()),
        Math.toIntExact(firstRange.endOffsetExclusive())));
    assertEquals("@SuppressWarnings(\"unused\")", source.substring(Math.toIntExact(secondRange.startOffset()),
        Math.toIntExact(secondRange.endOffsetExclusive())));
    assertEquals(1, annotations.get(1).children(SyntaxRole.ANNOTATION_ARGUMENT).size());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals(
        "frontend.unsupported-annotation-value")));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals(
        "frontend.unsupported-package-declaration")));
  }

  @Test
  void givenTwoCapturedSources_whenAnalyzedTwice_thenExactAstTuplesAndDiagnosticsRepeat() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Zeta.java"), "import java.util.List; class Zeta { }");
    Files.writeString(sourceRoot.resolve("Alpha.java"),
        "import static java.util.Collections.emptyList; class Alpha { Alpha() { super(); } int value() { return 1; } }");
    var snapshot = new JavaSourceLoader().discover(sourceRoot);

    // Act
    var first = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var second = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var firstAst = first.aggregate().orElseThrow();
    var secondAst = second.aggregate().orElseThrow();

    // Assert
    assertEquals(firstAst, secondAst);
    assertEquals(firstAst.orderedNodeKeys(), secondAst.orderedNodeKeys());
    assertEquals(firstAst.nodeTuples(), secondAst.nodeTuples());
    assertEquals(firstAst.rangeTuples(), secondAst.rangeTuples());
    assertEquals(firstAst.structuralEdges(), secondAst.structuralEdges());
    assertEquals(firstAst.semanticEdges(), secondAst.semanticEdges());
    assertEquals(first.diagnostics(), second.diagnostics());
    assertEquals(first.environmentSnapshot(), second.environmentSnapshot());
    assertEquals(snapshot.sourceFiles().stream().map(source -> source.sourceFileKey()).toList(),
        firstAst.roots().stream().map(root -> root.sourceFileKey()).toList());
    var valueMethod = first.program().methods().stream().filter(method -> method.name().equals("value")).findFirst()
        .orElseThrow();
    assertTrue(firstAst.semanticEdges().stream().anyMatch(edge -> edge.relation() == SyntaxSemanticRelation.DECLARATION_REFERENCE
        && edge.target() instanceof SyntaxSemanticTarget.Declaration target
        && target.reference().symbolKey().orElseThrow().equals(valueMethod.methodKey())
        && firstAst.node(edge.sourceKey()).orElseThrow().syntaxKind() == SyntaxKind.METHOD_DECLARATION));
    assertTrue(firstAst.roots().stream().allMatch(root -> root.sourceKind() == JavaSourceKind.COMPILATION_UNIT
        && root.syntaxKind() == SyntaxKind.COMPILATION_UNIT));
    assertEquals(firstAst.nodes().size(), firstAst.rangeTuples().size());
    assertTrue(firstAst.rangeTuples().stream().allMatch(tuple -> tuple.range().endOffsetExclusive() <= snapshot.sourceFiles().stream()
        .filter(source -> source.sourceFileKey().equals(tuple.range().sourceFileKey())).findFirst().orElseThrow().content().length()));
  }

  @Test
  void givenCompilerUnitsInReverseOrder_whenTranslated_thenAggregateFollowsSnapshotOrder() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Zeta.java"), "import java.util.List; class Zeta { }");
    Files.writeString(sourceRoot.resolve("Alpha.java"), "import static java.util.Collections.emptyList; class Alpha { int value() { return 1; } }");
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    var sources = new SnapshotSourceCatalog(snapshot);
    var invocation = JavaCompilerInvocation.from(JAVA_25);
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var session = CompilerSessionAcquisition.open(compiler, invocation, sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var reversed = new ArrayList<>(compilation.units());
      Collections.reverse(reversed);
      assertFalse(sources.find(reversed.getFirst()).orElseThrow().sourceFileKey()
          .equals(snapshot.sourceFiles().getFirst().sourceFileKey()));

      // Act
      var coverage = JavaLanguageFeatureCoverage.fromConfiguration(JAVA_25);
      var forward = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), JavaTranslationPolicy.legacyAnalysis()).translate(snapshot, compilation.units());
      var translated = new JavaProgramTranslator(sources, compilation.semantics(), compilation.sourceEvidence(),
          compilation.treeInventory(), JavaTranslationPolicy.legacyAnalysis()).translate(snapshot, reversed);
      var aggregate = translated.value().aggregate().orElseThrow();
      var normal = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, JAVA_25, JavaTranslationPolicy.legacyAnalysis()).aggregate().orElseThrow();

      // Assert
      assertEquals(snapshot.sourceFiles().stream().map(source -> source.sourceFileKey()).toList(),
          aggregate.roots().stream().map(root -> root.sourceFileKey()).toList());
      assertEquals(normal.nodeTuples(), aggregate.nodeTuples());
      assertEquals(normal.rangeTuples(), aggregate.rangeTuples());
      assertEquals(normal.structuralEdges(), aggregate.structuralEdges());
      assertEquals(normal.semanticEdges(), aggregate.semanticEdges());
      assertEquals(forward.value().aggregate(), translated.value().aggregate());
      assertEquals(forward.diagnostics(), translated.diagnostics());
    }
  }

  @Test
  void givenWrittenMethodWithoutCompilerAdditions_whenAnalyzed_thenCoreViewUsesIdenticalSourceBody() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Example.java"),
        "class Example { Example() { super(); } int value() { return 1; } }");

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var aggregate = input.aggregate().orElseThrow();
    var method = input.program().methods().stream().filter(candidate -> candidate.name().equals("value")).findFirst().orElseThrow();
    var body = input.methodSyntax(method.methodKey()).orElseThrow().root();

    // Assert
    assertEquals(MethodBodyOrigin.SOURCE_AST, aggregate.methodBodyOrigin(method.methodKey()).orElseThrow());
    assertSame(body, aggregate.node(body.nodeId()).orElseThrow());
    assertEquals(SyntaxKind.BLOCK, body.syntaxKind());
    assertFalse(body.children(SyntaxRole.STATEMENT).isEmpty());
  }

  @Test
  void givenImplicitAndWrittenConstructors_whenAnalyzed_thenSourceAstOmitsGeneratedSyntaxAndCfgRetainsBoth() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Constructors.java"), "class Plain { } class Written { Written() { } }");

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var aggregate = input.aggregate().orElseThrow();
    var run = new SourceAnalysisFixture().analyze(input);
    var constructors = input.program().methods().stream().filter(method -> method.name().equals("<init>")).toList();
    var plain = constructors.stream().filter(method -> method.ownerTypeKey().endsWith("Plain")).findFirst().orElseThrow();
    var written = constructors.stream().filter(method -> method.ownerTypeKey().endsWith("Written")).findFirst().orElseThrow();
    var writtenBody = input.methodSyntax(written.methodKey()).orElseThrow().root();
    var sourceBody = aggregate.nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.BLOCK).findFirst().orElseThrow();

    // Assert
    assertEquals(2, constructors.size());
    assertEquals(MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, aggregate.methodBodyOrigin(plain.methodKey()).orElseThrow());
    assertEquals(MethodBodyOrigin.SOURCE_AST, aggregate.methodBodyOrigin(written.methodKey()).orElseThrow());
    assertTrue(aggregate.nodes().stream().noneMatch(node -> node.sourceKind() == JavaSourceKind.METHOD
        && node.structuralPath().steps().get(1).position() == 0));
    assertTrue(sourceBody.children(SyntaxRole.STATEMENT).isEmpty());
    assertTrue(writtenBody.children(SyntaxRole.STATEMENT).isEmpty());
    assertSame(sourceBody, writtenBody);
    assertEquals(List.of(plain.methodKey(), written.methodKey()), run.representation().cfg().orElseThrow().methods().stream()
        .map(analysis -> analysis.methodKey()).toList());
  }

  private static SyntaxValue.QualifiedName name(String... components) {
    return new SyntaxValue.QualifiedName(List.of(components));
  }
}
