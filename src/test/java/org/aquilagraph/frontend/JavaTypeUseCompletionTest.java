package org.aquilagraph.frontend;

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
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.LanguageFeaturePolicy;
import org.aquilagraph.syntax.JavaAstAggregate;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var input = frontend.analyze(sourceRoot, JAVA_25);
    var run = new AnalysisEngine().analyze(input);
    var aggregate = input.aggregate().orElseThrow();
    var coverage = input.languageFeatureCoverageResult().coverage();

    // Assert
    assertEquals(List.of(), input.diagnostics());
    assertEquals(AnalysisCompletion.COMPLETED, run.completion());
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
    var frontend = new JavaFrontend(loader);

    // Act
    var first = frontend.analyze(snapshot, JAVA_25);
    var second = frontend.analyze(snapshot, JAVA_25);
    var firstAggregate = first.aggregate().orElseThrow();
    var secondAggregate = second.aggregate().orElseThrow();

    // Assert
    assertEquals(typeFacts(firstAggregate), typeFacts(secondAggregate));
    assertEquals(typeEdges(firstAggregate), typeEdges(secondAggregate));
    assertEquals(first.diagnostics(), second.diagnostics());
    assertEquals(first.languageFeatureCoverageResult().observations(), second.languageFeatureCoverageResult().observations());
  }

  @Test
  void givenBodyLocalTypeWithoutSourceTypeOccurrence_whenFrontendAnalyzes_thenLocatedDiagnosticBlocksCompletion() throws IOException {
    // Arrange
    var source = "class LocalTypes { void execute() { int value = 1; } }";
    Files.writeString(sourceRoot.resolve("LocalTypes.java"), source);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var input = frontend.analyze(sourceRoot, JAVA_25);
    var run = new AnalysisEngine().analyze(input);

    // Assert
    assertEquals(List.of("frontend.unrepresented-source-type-use"),
        input.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    var range = input.diagnostics().getFirst().origins().getFirst();
    assertEquals("int", source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())));
    assertEquals(AnalysisCompletion.INCOMPLETE, run.completion());
  }

  @Test
  void givenEarlierIndependentSource_whenFrontendAnalyzes_thenLocalTypeDiagnosticIdentityIsStable() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("B.java"), "class B { void execute() { int value = 1; } }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var before = frontend.analyze(sourceRoot, JAVA_25);
    Files.writeString(sourceRoot.resolve("A.java"), "class A {}");
    var after = frontend.analyze(sourceRoot, JAVA_25);

    // Assert
    assertEquals(List.of("frontend.unrepresented-source-type-use"),
        before.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(List.of("frontend.unrepresented-source-type-use"),
        after.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(before.diagnostics().getFirst().diagnosticKey(), after.diagnostics().getFirst().diagnosticKey());
    assertEquals(before.diagnostics().getFirst().origins(), after.diagnostics().getFirst().origins());
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
    var diagnostics = JavaTypeUseCompleteness.diagnostics(snapshot, List.of(observation), Optional.empty());

    // Assert
    assertEquals(1, diagnostics.size());
    var diagnostic = diagnostics.getFirst();
    assertEquals("frontend.unavailable-source-type-range", diagnostic.code());
    assertEquals(AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, diagnostic.outcomeKind());
    assertEquals(List.of(), diagnostic.origins());
    assertTrue(diagnostic.message().contains("Missing.java"));
    assertEquals(diagnostic.diagnosticKey(),
        JavaTypeUseCompleteness.diagnostics(snapshot, List.of(observation), Optional.empty()).getFirst().diagnosticKey());
  }

  private static PrimitiveFact primitive(SyntaxRole role, SyntaxKind parent, JavaTypeCategory kind, String fragment) {
    var start = HEADERS.indexOf(fragment);
    var length = kind == JavaTypeCategory.VOID ? 4 : 3;
    return new PrimitiveFact(role, parent, kind, kind, start, start + length);
  }

  @Test
  void givenAnnotatedHeaderAndBodyCast_whenAnalyzed_thenHeaderFactsCompleteWhileBodySiteKeepsItsIndependentFailure() throws IOException {
    // Arrange
    var source = "import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface A {} class Header { String @A [] field; Object body(Object value) { return (@A String)value; } }";
    Files.writeString(sourceRoot.resolve("Header.java"), source);
    var headerOffset = source.indexOf("@A []");
    var bodyOffset = source.indexOf("(@A") + 1;

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var aggregate = input.aggregate().orElseThrow();

    // Assert
    assertEquals(1, aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION
        && node.origin().orElseThrow().startOffset() == headerOffset).count());
    assertEquals(0, aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION
        && node.origin().orElseThrow().startOffset() == bodyOffset).count());
    assertTrue(input.diagnostics().stream().anyMatch(item -> item.code().equals("frontend.unrepresented-source-annotation")
        && item.origins().getFirst().startOffset() == bodyOffset));
    assertTrue(input.diagnostics().stream().anyMatch(item -> item.code().equals("frontend.unrepresented-source-type-use")));
    assertTrue(input.diagnostics().stream().noneMatch(item -> item.code().equals("frontend.inconsistent-annotation-source-owner")));
    assertEquals(AnalysisCompletion.INCOMPLETE, new AnalysisEngine().analyze(input).completion());
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
