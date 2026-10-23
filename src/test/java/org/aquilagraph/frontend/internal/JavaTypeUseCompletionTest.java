package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaTypeUseCompletionTest {
  private static final CompilerConfiguration JAVA_25 = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String HEADERS = """
      package sample;
      import java.util.List;
      import java.io.IOException;
      import java.sql.SQLException;
      abstract class Headers<T extends Number & Comparable<T>> implements java.io.Serializable {
        int count;
        List<String>[] values;
        abstract int map(Headers<T> this, int input, List<? extends Number> upper,
            List<? super T> lower, List<?> any) throws IOException, SQLException;
        abstract void absent();
      }
      record Pair(int left, List<String> right) {}
      """;

  @TempDir Path sourceRoot;

  @Test
  void givenValidDeclarationHeaderTypes_whenFrontendAnalyzes_thenPoliciesAndWrittenOccurrencesAreComplete() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Headers.java"), HEADERS);

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var run = new SourceAnalysisFixture().analyze(input);
    var aggregate = input.aggregate().orElseThrow();
    var coverage = input.languageFeatureCoverageResult().coverage();

    // Assert
    assertEquals(List.of(), input.diagnostics());
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    assertEquals(Set.of(JavaSourceKind.ARRAY_TYPE, JavaSourceKind.EXTENDS_WILDCARD, JavaSourceKind.PARAMETERIZED_TYPE,
        JavaSourceKind.PRIMITIVE_TYPE, JavaSourceKind.SUPER_WILDCARD, JavaSourceKind.UNBOUNDED_WILDCARD),
        aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE).map(node -> node.sourceKind())
            .filter(kind -> kind != JavaSourceKind.IDENTIFIER && kind != JavaSourceKind.MEMBER_SELECT).collect(Collectors.toSet()));
    var primitiveFacts = aggregate.structuralEdges().stream().map(edge -> {
      var child = aggregate.node(edge.childKey()).orElseThrow();
      if (child.sourceKind() != JavaSourceKind.PRIMITIVE_TYPE || child.syntaxKind() != SyntaxKind.TYPE_USE) {
        return null;
      }
      var parent = aggregate.node(edge.parentKey()).orElseThrow();
      var range = child.origin().orElseThrow();
      assertEquals(aggregate.roots().getFirst().sourceFileKey(), range.sourceFileKey());
      var written = child.value(SyntaxValue.PrimitiveType.class).kind();
      var attributed = assertInstanceOf(SyntaxType.Resolved.class, child.type()).identity().category();
      return new PrimitiveFact(edge.role(), parent.syntaxKind(), written, attributed, range.startOffset(),
          range.endOffsetExclusive());
    }).filter(fact -> fact != null).toList();
    assertEquals(Set.of(
        primitive(SyntaxRole.DECLARED_TYPE, SyntaxKind.FIELD_DECLARATION, JavaTypeCategory.INT, "int count"),
        primitive(SyntaxRole.RETURN_TYPE, SyntaxKind.METHOD_DECLARATION, JavaTypeCategory.INT, "int map"),
        primitive(SyntaxRole.DECLARED_TYPE, SyntaxKind.PARAMETER_DECLARATION, JavaTypeCategory.INT, "int input"),
        primitive(SyntaxRole.RETURN_TYPE, SyntaxKind.METHOD_DECLARATION, JavaTypeCategory.VOID, "void absent"),
        primitive(SyntaxRole.DECLARED_TYPE, SyntaxKind.RECORD_COMPONENT_DECLARATION, JavaTypeCategory.INT, "int left")),
        Set.copyOf(primitiveFacts));
    assertEquals(5, primitiveFacts.size());
    for (var kind : Set.of("ANNOTATED_TYPE", "ARRAY_TYPE", "EXTENDS_WILDCARD", "PARAMETERIZED_TYPE", "PRIMITIVE_TYPE",
        "SUPER_WILDCARD", "UNBOUNDED_WILDCARD")) {
      assertEquals(LanguageFeaturePolicy.REPRESENTED, policy(coverage, LanguageFeatureCategory.COMPILER_TREE_KIND, kind));
    }
    for (var kind : Set.of("INT", "VOID", "DECLARED", "ARRAY", "TYPEVAR", "WILDCARD", "INTERSECTION", "UNION")) {
      assertEquals(LanguageFeaturePolicy.REPRESENTED, policy(coverage, LanguageFeatureCategory.COMPILER_TYPE_KIND, kind));
    }
    for (var property : Set.of("MethodTree::MethodTree#getReturnType()", "MethodTree::MethodTree#getThrows()",
        "MethodTree::MethodTree#getReceiverParameter()", "VariableTree::VariableTree#getType()")) {
      assertEquals(LanguageFeaturePolicy.REPRESENTED, policy(coverage, LanguageFeatureCategory.DECLARATION_PROPERTY, property));
    }
    assertTrue(input.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-tree-kind.primitive-type")));
  }

  @Test
  void givenSameCapturedHeaderTypes_whenFrontendAnalyzesTwice_thenTypeFactsAndDiagnosticsAreIdentical() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Headers.java"), HEADERS);
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(sourceRoot);

    // Act
    var first = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var second = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var firstAggregate = first.aggregate().orElseThrow();
    var secondAggregate = second.aggregate().orElseThrow();

    // Assert
    assertEquals(typeFacts(firstAggregate), typeFacts(secondAggregate));
    assertEquals(typeEdges(firstAggregate), typeEdges(secondAggregate));
    assertEquals(first.diagnostics(), second.diagnostics());
    assertEquals(first.languageFeatureCoverageResult().observations(), second.languageFeatureCoverageResult().observations());
  }

  @Test
  void givenWrittenBodyLocalType_whenFrontendAnalyzes_thenLocatedTypeOccurrenceCompletesAnalysis() throws IOException {
    // Arrange
    var source = "class LocalTypes { void execute() { int value = 1; } }";
    Files.writeString(sourceRoot.resolve("LocalTypes.java"), source);

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertEquals(List.of(), input.diagnostics());
    var local = input.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).findFirst().orElseThrow();
    var type = local.requiredChild(SyntaxRole.DECLARED_TYPE);
    assertEquals(JavaSourceKind.PRIMITIVE_TYPE, type.sourceKind());
    assertEquals(JavaTypeCategory.INT, type.resolvedType().orElseThrow().category());
    var range = type.origin().orElseThrow();
    assertEquals("int", source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())));
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
  }

  @Test
  void givenEarlierIndependentSource_whenFrontendAnalyzes_thenLocalTypeOccurrenceAndMissingOccurrenceDiagnosticAreStable() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("B.java"), "class B { void execute() { int value = 1; } }");

    // Act
    var before = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    Files.writeString(sourceRoot.resolve("A.java"), "class A {}");
    var after = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var beforeType = before.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).findFirst().orElseThrow().requiredChild(SyntaxRole.DECLARED_TYPE);
    var afterType = after.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).findFirst().orElseThrow().requiredChild(SyntaxRole.DECLARED_TYPE);
    var beforeMissing = JavaTypeUseCompleteness.reconcile(before.snapshot(), before.languageFeatureCoverageResult().observations().stream()
        .filter(item -> item.origins().equals(List.of(beforeType.origin().orElseThrow()))).toList(),
        Optional.empty()).diagnostics();
    var afterMissing = JavaTypeUseCompleteness.reconcile(after.snapshot(), after.languageFeatureCoverageResult().observations().stream()
        .filter(item -> item.origins().equals(List.of(afterType.origin().orElseThrow()))).toList(),
        Optional.empty()).diagnostics();

    // Assert
    assertEquals(List.of(), before.diagnostics());
    assertEquals(List.of(), after.diagnostics());
    assertEquals(beforeType.nodeTuple(), afterType.nodeTuple());
    assertEquals(beforeType.rangeTuple(), afterType.rangeTuple());
    assertEquals(beforeType.type(), afterType.type());
    assertEquals(List.of("frontend.unrepresented-source-type-use"), beforeMissing.stream().map(item -> item.code()).toList());
    assertEquals(List.of("frontend.unrepresented-source-type-use"), afterMissing.stream().map(item -> item.code()).toList());
    assertEquals(beforeMissing.getFirst().diagnosticKey(), afterMissing.getFirst().diagnosticKey());
    assertEquals(beforeMissing.getFirst().origins(), afterMissing.getFirst().origins());
  }

  @Test
  void givenWrittenTypeObservationWithoutRange_whenCompletenessChecks_thenSourcePathAndTypedFailureAreRetained() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Missing.java"), "class Missing { int value; }");
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    var classification = JavaCompilerKindCoverage.fromConfiguration(JAVA_25)
        .classificationOf(new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, "PRIMITIVE_TYPE")).orElseThrow();
    var observation = new LanguageFeatureObservation(snapshot.sourceFiles().getFirst().sourceFileKey(), classification, List.of(),
        Optional.empty());

    // Act
    var diagnostics = JavaTypeUseCompleteness.reconcile(snapshot, List.of(observation), Optional.empty()).diagnostics();

    // Assert
    assertEquals(1, diagnostics.size());
    var diagnostic = diagnostics.getFirst();
    assertEquals("frontend.unavailable-source-type-range", diagnostic.code());
    assertEquals(AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, diagnostic.outcomeKind());
    assertEquals(List.of(), diagnostic.origins());
    assertTrue(diagnostic.message().contains("Missing.java"));
    assertEquals(diagnostic.diagnosticKey(),
        JavaTypeUseCompleteness.reconcile(snapshot, List.of(observation), Optional.empty()).diagnostics().getFirst().diagnosticKey());
  }

  private static PrimitiveFact primitive(SyntaxRole role, SyntaxKind parent, JavaTypeCategory kind, String fragment) {
    var start = HEADERS.indexOf(fragment);
    var length = kind == JavaTypeCategory.VOID ? 4 : 3;
    return new PrimitiveFact(role, parent, kind, kind, start, start + length);
  }

  @Test
  void givenAnnotatedHeaderAndBodyCast_whenAnalyzed_thenEachWrittenAnnotationHasItsOwnTypeOccurrence() throws IOException {
    // Arrange
    var source = "import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface A {} class Header { String @A [] field; Object body(Object value) { return (@A String)value; } }";
    Files.writeString(sourceRoot.resolve("Header.java"), source);
    var headerOffset = source.indexOf("@A []");
    var bodyOffset = source.indexOf("(@A") + 1;

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var aggregate = input.aggregate().orElseThrow();

    // Assert
    assertEquals(1, aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION
        && node.origin().orElseThrow().startOffset() == headerOffset).count());
    assertEquals(1, aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION
        && node.origin().orElseThrow().startOffset() == bodyOffset).count());
    var cast = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
    var castType = cast.requiredChild(SyntaxRole.TYPE_OPERAND);
    assertEquals(JavaSourceKind.ANNOTATED_TYPE, castType.sourceKind());
    assertEquals(bodyOffset, castType.requiredChild(SyntaxRole.ANNOTATION).origin().orElseThrow().startOffset());
    assertEquals(List.of(), input.diagnostics());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(input).completion());
  }

  private static LanguageFeaturePolicy policy(LanguageFeatureCoverage coverage, LanguageFeatureCategory category, String identifier) {
    return coverage.classificationOf(new LanguageFeature(category, identifier)).orElseThrow().policy();
  }

  private static List<?> typeFacts(JavaAstAggregate aggregate) {
    return aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE)
        .map(node -> List.of(node.nodeTuple(), node.rangeTuple().orElseThrow(), node.type(), node.value(), node.semanticReference()))
        .toList();
  }

  private static List<?> typeEdges(JavaAstAggregate aggregate) {
    return List.of(aggregate.structuralEdges().stream().filter(edge -> aggregate.node(edge.childKey()).orElseThrow().syntaxKind()
        == SyntaxKind.TYPE_USE).toList(), aggregate.semanticEdges().stream().filter(edge -> aggregate.node(edge.sourceKey()).orElseThrow()
            .syntaxKind() == SyntaxKind.TYPE_USE).toList());
  }

  private record PrimitiveFact(SyntaxRole role, SyntaxKind parent, JavaTypeCategory writtenKind, JavaTypeCategory attributedKind,
      long start, long end) {
  }
}
