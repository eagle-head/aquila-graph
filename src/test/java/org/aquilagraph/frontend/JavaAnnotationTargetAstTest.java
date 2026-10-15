package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.ToolProvider;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.AggregateFixture;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SharedVariableDeclarations;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaAnnotationTargetAstTest {
  @TempDir Path sourceRoot;

  @Test
  void givenRepeatedCommonTypeAnnotations_whenAReferenceAndItsDestinationAreOmittedTogether_thenPublicConstructionRejectsTheLoss() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @Repeatable(Marks.class) @interface Mark { int value(); }
        @Target(ElementType.TYPE_USE) @interface Marks { Mark[] value(); }
        class Sample { java.util.List<@Mark(1) @Mark(2) String> first, second; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var shared = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.SharedTypeAnnotations).findFirst().orElseThrow();
    var keys = shared.value(SyntaxValue.SharedTypeAnnotations.class).annotationNodeKeys();
    assertEquals(2, keys.size());
    var root = replaceValue(aggregate.roots().getFirst(), shared.nodeId(), new SyntaxValue.SharedTypeAnnotations(List.of(keys.getFirst())));

    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> sealWithRecomputedApplications(result.snapshot(), aggregate, root));
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion());
  }

  @Test
  void givenEqualSpanArrayDimensions_whenSharedReferencesAreSwappedAndApplicationsRecomputed_thenPublicConstructionRejectsBothSuffixShapes() throws IOException {
    for (var declarators : List.of("first, second", "first @Use(3) [], second @Use(4) [][]")) {
      // Arrange
      var source = """
          import java.lang.annotation.*;
          @Target(ElementType.TYPE_USE) @interface Use { int value(); }
          class Sample { String @Use(1) [] @Use(2) [] %s; }
          """.formatted(declarators);
      Files.writeString(sourceRoot.resolve("Sample.java"), source);
      var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
      var aggregate = result.aggregate().orElseThrow();
      var shared = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.SharedTypeAnnotations).toList();
      assertEquals(2, shared.size());
      assertEquals(shared.getFirst().origin(), shared.getLast().origin());
      assertNotEquals(shared.getFirst().structuralPath(), shared.getLast().structuralPath());
      assertNotEquals(shared.getFirst().type(), shared.getLast().type());
      for (var node : shared) {
        var key = node.value(SyntaxValue.SharedTypeAnnotations.class).annotationNodeKeys().getFirst();
        var physical = aggregate.nodes().stream().filter(candidate -> candidate.children(SyntaxRole.ANNOTATION).stream()
            .anyMatch(annotation -> annotation.nodeId().equals(key))).findFirst().orElseThrow();
        assertEquals(physical.type(), node.type());
      }
      var root = replaceValue(aggregate.roots().getFirst(), shared.getFirst().nodeId(), shared.getLast().value());
      root = replaceValue(root, shared.getLast().nodeId(), shared.getFirst().value());
      var swapped = root;

      // Act and Assert
      assertThrows(IllegalArgumentException.class, () -> sealWithRecomputedApplications(result.snapshot(), aggregate, swapped));
      assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion());
    }
  }

  @Test
  void givenAStaticMemberAfterAnInnerQualifier_whenAnalyzed_thenDualIsDeclarationOnlyAndTypeOnlyIsDiagnosed() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Both {}
        @Target(ElementType.TYPE_USE) @interface Use {}
        class Outer { class Inner { static class Nested {} } }
        class Sample { @Both Outer.Inner.Nested value; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var written = annotation(aggregate, source, "@Both");
    var app = application(aggregate, written);

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.DECLARATION, written.value(SyntaxValue.Annotation.class).location());
    assertFalse(written.value(SyntaxValue.Annotation.class).closestTypeAdmissible());
    assertTrue(app.declarationNodeKey().isPresent());
    assertTrue(app.typeLocation().isEmpty());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(), () -> result.diagnostics().toString());
    var invalid = source.replace("@Both Outer", "@Use Outer");
    Files.writeString(sourceRoot.resolve("Sample.java"), invalid);
    var rejected = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    assertTrue(rejected.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("compiler.err.type.annotation.inadmissible")
        && diagnostic.origins().stream().anyMatch(origin -> invalid.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive())).equals("Outer.Inner.Nested"))), () -> rejected.diagnostics().toString());
    assertTrue(rejected.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unresolved-annotation-location")
        && diagnostic.origins().stream().anyMatch(origin -> invalid.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive())).equals("@Use"))), () -> rejected.diagnostics().toString());
    assertEquals(AnalysisCompletion.PARTIAL, new SourceAnalysisFixture().analyze(rejected).completion());
  }

  @Test
  void givenAnAdmissiblePrimitiveType_whenDualApplicabilityIsSuppressed_thenPublicFactoryRejectsTheDeclarationOnlyForgery() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Both {}
        class Sample { @Both int value; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var written = annotation(aggregate, source, "@Both");
    var facts = written.value(SyntaxValue.Annotation.class);
    var forged = new SyntaxValue.Annotation(facts.form(), facts.arguments(), SyntaxValue.AnnotationLocation.DECLARATION,
        facts.applicableTargets(), false);
    var field = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION).findFirst().orElseThrow();
    var altered = replaceValue(field, written.nodeId(), forged);

    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> SyntaxSemanticTarget.AnnotationApplication.forAttachment(altered, altered.children(SyntaxRole.ANNOTATION).getFirst()));
  }

  @Test
  void givenSharedFieldModifiers_whenAnalyzed_thenOneOccurrenceAppliesToEveryWrittenDeclarator() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.FIELD) @interface Declaration {}
        @Target(ElementType.TYPE_USE) @interface Use { int value(); }
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @Repeatable(BothList.class)
        @interface Both { int value(); }
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface BothList { Both[] value(); }
        class Sample { @Declaration @Use(1) @Both(2) @Both(3) int first, second, third; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var fields = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION).toList();
    var fieldKeys = fields.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).declarationKey()).toList();

    // Assert
    assertEquals(List.of("first", "second", "third"), fields.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).name()).toList());
    assertEquals(List.of(4, 0, 0), fields.stream().map(node -> node.children(SyntaxRole.ANNOTATION).size()).toList());
    for (var field : fields) {
      assertEquals(fieldKeys, field.value(SyntaxValue.VariableDeclaration.class).sharedDeclaratorKeys());
    }
    for (var text : List.of("@Declaration", "@Use(1)", "@Both(2)", "@Both(3)")) {
      var written = annotation(aggregate, source, text);
      var app = application(aggregate, written);
      assertEquals(fields.getFirst().nodeId(), app.ownerNodeKey());
      assertEquals(fields.subList(1, 3).stream().map(SyntaxNode::nodeId).toList(),
          app.fieldDeclarators().stream().map(SyntaxSemanticTarget.AnnotationApplication.FieldDeclaratorApplication::fieldNodeKey).toList());
      var declaration = !text.startsWith("@Use");
      var type = !text.equals("@Declaration");
      assertEquals(declaration, app.declarationNodeKey().isPresent());
      assertEquals(type, app.typeLocation().isPresent());
      for (var destination : app.fieldDeclarators()) {
        assertEquals(declaration, destination.declarationApplicable());
        assertEquals(type, destination.typeLocation().isPresent());
        if (type) {
          var target = assertInstanceOf(SyntaxSemanticTarget.AnnotationTypeLocation.Written.class, destination.typeLocation().orElseThrow());
          var field = fields.stream().filter(node -> node.nodeId().equals(destination.fieldNodeKey())).findFirst().orElseThrow();
          assertEquals(field.requiredChild(SyntaxRole.DECLARED_TYPE).nodeId(), target.nodeKey());
        }
      }
    }
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(), () -> result.diagnostics().toString());
    var again = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25)).aggregate().orElseThrow();
    assertEquals(aggregate.nodes().stream().map(SyntaxNode::nodeId).toList(), again.nodes().stream().map(SyntaxNode::nodeId).toList());
    assertEquals(aggregate.semanticEdges(), again.semanticEdges());

    var written = annotation(aggregate, source, "@Both(2)");
    var app = application(aggregate, written);
    var omitted = new SyntaxSemanticTarget.AnnotationApplication(app.ownerNodeKey(), app.declarationNodeKey(), app.typeLocation(), app.propagatedCounterparts(), List.of());
    var edges = aggregate.semanticEdges().stream().map(edge -> edge.sourceKey().equals(written.nodeId()) && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
        ? new SyntaxSemanticEdge(written.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET, omitted) : edge).toList();
    var root = aggregate.roots().getFirst();
    var contribution = new SyntaxSourceContribution(root.sourceFileKey(), root, aggregate.nodes(), aggregate.structuralEdges(), edges);
    assertThrows(IllegalArgumentException.class, () -> AggregateFixture.builder(result.snapshot()).add(contribution).seal());
  }

  @Test
  void givenSharedGenericAndArrayAnnotations_whenAnalyzed_thenCommonOccurrencesRetainEveryDeclaratorSpecificTypePosition() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Use { int value(); }
        class Sample {
          java.util.List<@Use(1) String @Use(2) []> @Use(3) [] first @Use(4) [], second @Use(5) [][];
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var fields = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION).toList();

    // Assert
    assertEquals(2, fields.size());
    assertNotEquals(fields.getFirst().value(SyntaxValue.VariableDeclaration.class).declaredType(), fields.getLast().value(SyntaxValue.VariableDeclaration.class).declaredType());
    for (var ordinal = 1; ordinal <= 5; ordinal++) {
      var text = "@Use(" + ordinal + ")";
      var written = annotation(aggregate, source, text);
      assertEquals(1, aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION && node.origin().equals(written.origin())).count());
      var app = application(aggregate, written);
      assertEquals(ordinal <= 3 ? 1 : 0, app.fieldDeclarators().size());
      if (ordinal <= 3) {
        var destination = app.fieldDeclarators().getFirst();
        assertEquals(fields.getLast().nodeId(), destination.fieldNodeKey());
        assertFalse(destination.declarationApplicable());
        var target = assertInstanceOf(SyntaxSemanticTarget.AnnotationTypeLocation.Written.class, destination.typeLocation().orElseThrow());
        var node = aggregate.nodes().stream().filter(candidate -> candidate.nodeId().equals(target.nodeKey())).findFirst().orElseThrow();
        assertTrue(node.value(SyntaxValue.SharedTypeAnnotations.class).annotationNodeKeys().contains(written.nodeId()));
        assertEquals(writtenLocationNode(aggregate, app).sourceKind(), node.sourceKind());
        assertEquals(writtenLocationNode(aggregate, app).origin(), node.origin());
      }
    }
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(), () -> result.diagnostics().toString());
  }

  @Test
  void givenSharedFieldTypeReferences_whenTheirSourceIdentityIsForged_thenSealingRejectsTheAggregate() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Use { int value(); }
        class Sample { java.util.List<@Use(1) String @Use(2) []> first, second; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var root = aggregate.roots().getFirst();
    var shared = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.SharedTypeAnnotations value
        && value.annotationNodeKeys().contains(annotation(aggregate, source, "@Use(1)").nodeId())).findFirst().orElseThrow();

    // Act and Assert
    for (var wrongKey : List.of("foreign:annotation", annotation(aggregate, source, "@Use(2)").nodeId())) {
      var forged = replaceValue(root, shared.nodeId(), new SyntaxValue.SharedTypeAnnotations(List.of(wrongKey)));
      var nodes = forged.descendantsAndSelf();
      var contribution = new SyntaxSourceContribution(root.sourceFileKey(), forged, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), aggregate.semanticEdges());
      var failure = assertThrows(IllegalArgumentException.class, () -> AggregateFixture.builder(result.snapshot()).add(contribution).seal());
      assertTrue(failure.getMessage().contains("shared type annotation"), failure::getMessage);
    }
  }

  @Test
  void givenDeclarationTypeAndDualFieldAnnotations_whenAnalyzed_thenEachWrittenOccurrenceHasItsExactApplication()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.FIELD) @interface Declaration {}
        @Target(ElementType.TYPE_USE) @interface TypeOnly { int value() default 0; }
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Both {}
        class Sample {
          @Declaration @TypeOnly(1) @Both String value;
          java.util.List<@TypeOnly(2) ? extends @TypeOnly(3) String> nested;
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var declaration = annotation(aggregate, source, "@Declaration");
    var closestType = annotation(aggregate, source, "@TypeOnly(1)");
    var dual = annotation(aggregate, source, "@Both");
    var wildcard = annotation(aggregate, source, "@TypeOnly(2)");
    var bound = annotation(aggregate, source, "@TypeOnly(3)");

    // Assert
    assertEquals(List.of(SyntaxValue.AnnotationLocation.DECLARATION, SyntaxValue.AnnotationLocation.TYPE_USE,
        SyntaxValue.AnnotationLocation.DUAL, SyntaxValue.AnnotationLocation.TYPE_USE, SyntaxValue.AnnotationLocation.TYPE_USE),
        List.of(declaration, closestType, dual, wildcard, bound).stream()
            .map(node -> node.value(SyntaxValue.Annotation.class).location()).toList());
    var declarationTarget = application(aggregate, declaration);
    var closestTypeTarget = application(aggregate, closestType);
    var dualTarget = application(aggregate, dual);
    var wildcardTarget = application(aggregate, wildcard);
    var boundTarget = application(aggregate, bound);
    assertEquals(Optional.of(declarationTarget.ownerNodeKey()), declarationTarget.declarationNodeKey());
    assertTrue(declarationTarget.typeLocation().isEmpty());
    assertTrue(closestTypeTarget.declarationNodeKey().isEmpty());
    assertEquals(Optional.of(dualTarget.ownerNodeKey()), dualTarget.declarationNodeKey());
    assertEquals(closestTypeTarget.typeLocation(), dualTarget.typeLocation());
    assertTrue(wildcardTarget.declarationNodeKey().isEmpty());
    assertTrue(boundTarget.declarationNodeKey().isEmpty());
    assertTrue(!wildcardTarget.typeLocation().equals(boundTarget.typeLocation()));
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenUpperAndLowerWildcardAnnotations_whenAnalyzed_thenEachBoundKeepsItsOwnLocation() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.util.List;
        @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
        class Sample {
          List<@Mark(1) ? extends @Mark(2) String> upper;
          List<@Mark(3) ? super @Mark(4) String> lower;
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var applications = List.of(1, 2, 3, 4).stream()
        .map(index -> application(aggregate, annotation(aggregate, source, "@Mark(" + index + ")"))).toList();

    // Assert
    assertEquals(4, applications.stream().map(SyntaxSemanticTarget.AnnotationApplication::typeLocation).distinct().count());
    var upperRoles = writtenLocationNode(aggregate, applications.get(1)).structuralPath().steps().stream()
        .map(step -> step.role()).toList();
    var lowerRoles = writtenLocationNode(aggregate, applications.get(3)).structuralPath().steps().stream()
        .map(step -> step.role()).toList();
    assertTrue(upperRoles.contains(SyntaxRole.UPPER_BOUND));
    assertTrue(!upperRoles.contains(SyntaxRole.LOWER_BOUND));
    assertTrue(lowerRoles.contains(SyntaxRole.LOWER_BOUND));
    assertTrue(!lowerRoles.contains(SyntaxRole.UPPER_BOUND));
    assertEquals(SyntaxRole.TYPE_ARGUMENT, writtenLocationNode(aggregate, applications.getFirst())
        .structuralPath().steps().getLast().role());
    assertEquals(SyntaxRole.TYPE_ARGUMENT, writtenLocationNode(aggregate, applications.get(2))
        .structuralPath().steps().getLast().role());
    assertNotEquals(applications.get(1).typeLocation(), applications.get(3).typeLocation());
    for (var index = 1; index <= 4; index++) {
      var occurrence = annotation(aggregate, source, "@Mark(" + index + ")");
      assertEquals(source.indexOf("@Mark(" + index + ")"), occurrence.origin().orElseThrow().startOffset());
    }
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenQualifiedFieldTypes_whenAnalyzed_thenEachClosestNameReceivesItsExactApplication()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Both {}
        class Outer { static class Static {} class Inner {} }
        class Sample {
          @Both java.lang.String packageQualified;
          @Both Outer.Static staticQualified;
          @Both Outer.Inner innerQualified;
          @Both String simple;
          Outer.@Both Inner memberAnnotated;
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotations = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.origin().isPresent())
        .filter(node -> source.substring(Math.toIntExact(node.origin().orElseThrow().startOffset()),
            Math.toIntExact(node.origin().orElseThrow().endOffsetExclusive())).equals("@Both"))
        .sorted((left, right) -> Long.compare(left.origin().orElseThrow().startOffset(),
            right.origin().orElseThrow().startOffset())).toList();

    // Assert
    assertEquals(5, annotations.size());
    assertEquals(List.of(SyntaxValue.AnnotationLocation.DECLARATION, SyntaxValue.AnnotationLocation.DECLARATION,
        SyntaxValue.AnnotationLocation.DUAL, SyntaxValue.AnnotationLocation.DUAL, SyntaxValue.AnnotationLocation.TYPE_USE), annotations.stream()
            .map(node -> node.value(SyntaxValue.Annotation.class).location()).toList());
    for (var index = 0; index < annotations.size(); index++) {
      var application = application(aggregate, annotations.get(index));
      assertEquals(index < 4 ? Optional.of(application.ownerNodeKey()) : Optional.empty(), application.declarationNodeKey());
      assertEquals(index >= 2, application.typeLocation().isPresent());
    }
    var qualifier = writtenLocationNode(aggregate, application(aggregate, annotations.get(2)));
    assertEquals("Outer", source.substring(Math.toIntExact(qualifier.origin().orElseThrow().startOffset()),
        Math.toIntExact(qualifier.origin().orElseThrow().endOffsetExclusive())));
    assertEquals(SyntaxRole.TYPE_QUALIFIER, qualifier.structuralPath().steps().getLast().role());
    var member = writtenLocationNode(aggregate, application(aggregate, annotations.get(4)));
    assertEquals("Inner", member.children(SyntaxRole.UNDERLYING_TYPE).getFirst().value(SyntaxValue.SourceSymbol.class).name());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenPackageQualifiedField_whenDualApplicabilityIsForged_thenTargetConstructionRejectsTheType()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Both {}
        class Sample { @Both java.lang.String value; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var original = annotation(result.aggregate().orElseThrow(), source, "@Both");
    var field = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION)
        .findFirst().orElseThrow();
    var facts = original.value(SyntaxValue.Annotation.class);
    var forgedValue = new SyntaxValue.Annotation(facts.form(), facts.arguments(), SyntaxValue.AnnotationLocation.DUAL,
        facts.applicableTargets(), true);
    var forgedAnnotation = SyntaxNode.source(original.sourceFileKey(), original.structuralPath(), original.sourceKind(),
        original.syntaxKind(), original.type(), forgedValue, original.children(), original.semanticReference(),
        original.origin().orElseThrow());
    var children = field.children().stream().map(child -> child.node().nodeId().equals(original.nodeId())
        ? new SyntaxChild(child.role(), child.position(), forgedAnnotation) : child).toList();
    var forgedField = SyntaxNode.source(field.sourceFileKey(), field.structuralPath(), field.sourceKind(),
        field.syntaxKind(), field.type(), field.value(), children, field.semanticReference(), field.origin().orElseThrow());

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> SyntaxSemanticTarget.AnnotationApplication.forAttachment(forgedField, forgedAnnotation));

    // Assert
    assertTrue(failure.getMessage().contains("package qualifier"), failure::getMessage);
  }

  @Test
  void givenStaticQualifiedField_whenDualApplicabilityIsForged_thenTargetConstructionRejectsTheType()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Both {}
        class Outer { static class Nested {} }
        class Sample { @Both Outer.Nested value; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var original = annotation(result.aggregate().orElseThrow(), source, "@Both");
    var field = result.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION)
        .findFirst().orElseThrow();
    var facts = original.value(SyntaxValue.Annotation.class);
    assertEquals(SyntaxValue.AnnotationLocation.DECLARATION, facts.location());
    assertFalse(facts.closestTypeAdmissible());
    var forgedValue = new SyntaxValue.Annotation(facts.form(), facts.arguments(), SyntaxValue.AnnotationLocation.DUAL,
        facts.applicableTargets(), true);
    var forgedAnnotation = SyntaxNode.source(original.sourceFileKey(), original.structuralPath(), original.sourceKind(),
        original.syntaxKind(), original.type(), forgedValue, original.children(), original.semanticReference(), original.origin().orElseThrow());
    var children = field.children().stream().map(child -> child.node().nodeId().equals(original.nodeId())
        ? new SyntaxChild(child.role(), child.position(), forgedAnnotation) : child).toList();
    var forgedField = SyntaxNode.source(field.sourceFileKey(), field.structuralPath(), field.sourceKind(), field.syntaxKind(),
        field.type(), field.value(), children, field.semanticReference(), field.origin().orElseThrow());

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> SyntaxSemanticTarget.AnnotationApplication.forAttachment(forgedField, forgedAnnotation));

    // Assert
    assertTrue(failure.getMessage().contains("static type qualifier"), failure::getMessage);
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion());
  }

  @Test
  void givenArrayDimensionsAndVarargs_whenAnalyzed_thenElementAndDimensionTargetsRemainDistinct() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
        class Sample {
          @Mark(1) String @Mark(2) [] @Mark(3) [] values;
          void accept(@Mark(4) String @Mark(5) ... items) {}
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var applications = List.of(1, 2, 3, 4, 5).stream()
        .map(index -> application(aggregate, annotation(aggregate, source, "@Mark(" + index + ")"))).toList();

    // Assert
    assertEquals(5, applications.stream().map(SyntaxSemanticTarget.AnnotationApplication::typeLocation).distinct().count());
    assertTrue(applications.stream().allMatch(application -> application.declarationNodeKey().isEmpty()));
    assertEquals(List.of(
        List.of(SyntaxRole.SOURCE_FILE_ROOT, SyntaxRole.TYPE_DECLARATION, SyntaxRole.MEMBER, SyntaxRole.DECLARED_TYPE,
            SyntaxRole.UNDERLYING_TYPE, SyntaxRole.COMPONENT_TYPE, SyntaxRole.UNDERLYING_TYPE, SyntaxRole.COMPONENT_TYPE),
        List.of(SyntaxRole.SOURCE_FILE_ROOT, SyntaxRole.TYPE_DECLARATION, SyntaxRole.MEMBER, SyntaxRole.DECLARED_TYPE),
        List.of(SyntaxRole.SOURCE_FILE_ROOT, SyntaxRole.TYPE_DECLARATION, SyntaxRole.MEMBER, SyntaxRole.DECLARED_TYPE,
            SyntaxRole.UNDERLYING_TYPE, SyntaxRole.COMPONENT_TYPE),
        List.of(SyntaxRole.SOURCE_FILE_ROOT, SyntaxRole.TYPE_DECLARATION, SyntaxRole.MEMBER, SyntaxRole.PARAMETER,
            SyntaxRole.DECLARED_TYPE, SyntaxRole.UNDERLYING_TYPE, SyntaxRole.COMPONENT_TYPE),
        List.of(SyntaxRole.SOURCE_FILE_ROOT, SyntaxRole.TYPE_DECLARATION, SyntaxRole.MEMBER, SyntaxRole.PARAMETER,
            SyntaxRole.DECLARED_TYPE)), applications.stream().map(application -> writtenLocationNode(aggregate, application)
                .structuralPath().steps().stream().map(step -> step.role()).toList()).toList());
    for (var index = 1; index <= 5; index++) {
      var written = "@Mark(" + index + ")";
      assertEquals(new AnnotationScalar.IntValue(index), annotation(aggregate, source, written)
          .requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).value(SyntaxValue.AnnotationArgument.class).value());
      var origin = annotation(aggregate, source, written).origin().orElseThrow();
      assertEquals(source.indexOf(written), origin.startOffset());
      assertEquals(source.indexOf(written) + written.length(), origin.endOffsetExclusive());
    }
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenTypeParameterReceiverConstructorAndHeaderTypes_whenAnalyzed_thenSourceTargetsFollowTheirExactSites()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Mark { int value() default 0; }
        class Base {}
        interface Contract {}
        class Sample<@Mark(1) T extends @Mark(2) Number> extends @Mark(3) Base implements @Mark(4) Contract {
          @Mark Sample() {}
          @Mark(6) String call(@Mark(7) Sample<T> this, @Mark(8) String input) throws @Mark(9) Exception {
            return input;
          }
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var applications = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9).stream()
        .map(index -> application(aggregate, annotation(aggregate, source, index == 5 ? "@Mark" : "@Mark(" + index + ")")))
        .toList();

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.DECLARATION,
        annotation(aggregate, source, "@Mark(1)").value(SyntaxValue.Annotation.class).location());
    assertTrue(applications.getFirst().declarationNodeKey().isPresent());
    assertTrue(applications.getFirst().typeLocation().isEmpty());
    assertInstanceOf(SyntaxSemanticTarget.AnnotationTypeLocation.Constructed.class,
        applications.get(4).typeLocation().orElseThrow());
    assertTrue(applications.subList(1, applications.size()).stream()
        .allMatch(application -> application.declarationNodeKey().isEmpty() && application.typeLocation().isPresent()));
    assertEquals(List.of(SyntaxRole.TYPE_BOUND, SyntaxRole.SUPERCLASS, SyntaxRole.INTERFACE, SyntaxRole.RETURN_TYPE,
        SyntaxRole.RECEIVER_PARAMETER, SyntaxRole.PARAMETER, SyntaxRole.THROWN_TYPE),
        List.of(1, 2, 3, 5, 6, 7, 8).stream().map(index -> {
          var roles = writtenLocationNode(aggregate, applications.get(index)).structuralPath().steps().stream()
              .map(step -> step.role()).toList();
          return List.of(SyntaxRole.TYPE_BOUND, SyntaxRole.SUPERCLASS, SyntaxRole.INTERFACE, SyntaxRole.RETURN_TYPE,
              SyntaxRole.RECEIVER_PARAMETER, SyntaxRole.PARAMETER, SyntaxRole.THROWN_TYPE).stream()
              .filter(roles::contains).findFirst().orElseThrow();
        }).toList());
    assertEquals(List.of("@Mark(2) Number", "@Mark(3) Base", "@Mark(4) Contract", "String", "Sample", "String",
        "@Mark(9) Exception"),
        List.of(1, 2, 3, 5, 6, 7, 8).stream().map(index -> {
          var range = writtenLocationNode(aggregate, applications.get(index)).origin().orElseThrow();
          return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
        }).toList());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenRecordComponentAndPropagatedTargets_whenAnalyzed_thenOneSourceOccurrenceOwnsEachApplication()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) @interface Propagated {}
        @Target({ElementType.RECORD_COMPONENT, ElementType.TYPE_USE}) @interface Component {}
        record Sample(@Propagated @Component String name) {}
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var propagated = application(aggregate, annotation(aggregate, source, "@Propagated"));
    var component = application(aggregate, annotation(aggregate, source, "@Component"));

    // Assert
    assertTrue(propagated.declarationNodeKey().isEmpty());
    assertTrue(propagated.typeLocation().isEmpty());
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER),
        propagated.propagatedCounterparts().stream().map(counterpart -> counterpart.reference().referenceKind()).toList());
    assertTrue(propagated.propagatedCounterparts().stream().allMatch(counterpart -> counterpart.declarationApplicable()
        && counterpart.typeLocation().isEmpty()));
    assertEquals(Optional.of(component.ownerNodeKey()), component.declarationNodeKey());
    assertTrue(component.typeLocation().isPresent());
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER),
        component.propagatedCounterparts().stream().map(counterpart -> counterpart.reference().referenceKind()).toList());
    assertTrue(component.propagatedCounterparts().stream().allMatch(counterpart -> !counterpart.declarationApplicable()
        && counterpart.typeLocation().equals(component.typeLocation())));
    assertEquals(1, aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.origin().isPresent())
        .filter(node -> {
          var range = node.origin().orElseThrow();
          return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()))
              .equals("@Propagated");
        }).count());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenNestedRecordComponentTypeAnnotations_whenAnalyzed_thenExactTypePathsReachDerivedCounterparts()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.util.List;
        @Target(ElementType.TYPE_USE) @interface Use { int value(); }
        record Implicit(@Use(0) List<@Use(1) String> value) {}
        record Accessor(List<@Use(2) String> value) {
          public List<String> value() { return value; }
        }
        record Canonical(List<@Use(3) String> value) {
          Canonical(List<String> value) { this.value = value; }
        }
        """;
    Files.writeString(sourceRoot.resolve("Records.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var outer = application(aggregate, annotation(aggregate, source, "@Use(0)"));
    var implicit = application(aggregate, annotation(aggregate, source, "@Use(1)"));
    var accessor = application(aggregate, annotation(aggregate, source, "@Use(2)"));
    var canonical = application(aggregate, annotation(aggregate, source, "@Use(3)"));

    // Assert
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER), counterpartKinds(outer));
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER), counterpartKinds(implicit));
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.PARAMETER), counterpartKinds(accessor));
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD), counterpartKinds(canonical));
    for (var target : List.of(implicit, accessor, canonical)) {
      assertTrue(target.declarationNodeKey().isEmpty());
      assertTrue(target.typeLocation().isPresent());
      assertTrue(target.propagatedCounterparts().stream().allMatch(counterpart -> !counterpart.declarationApplicable()
          && counterpart.typeLocation().equals(target.typeLocation())));
      assertEquals(SyntaxRole.TYPE_ARGUMENT,
          writtenLocationNode(aggregate, target).structuralPath().steps().getLast().role());
    }
    assertEquals(SyntaxRole.TYPE_NAME, writtenLocationNode(aggregate, outer).structuralPath().steps().getLast().role());
    assertNotEquals(outer.typeLocation(), implicit.typeLocation());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenRepeatedEqualRecordAnnotations_whenAnalyzed_thenEveryPropagatedOccurrenceIsCorrelated() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Repeatable;
        import java.lang.annotation.Target;
        @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
        @Repeatable(Tags.class) @interface Tag { int value(); }
        @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
        @interface Tags { Tag[] value(); }
        record Sample(@Tag(1) @Tag(1) String value) {}
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var repeated = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.origin().isPresent()).filter(node -> {
          var range = node.origin().orElseThrow();
          return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())).equals("@Tag(1)");
        }).toList();

    // Assert
    assertEquals(2, repeated.size());
    assertNotEquals(repeated.getFirst().nodeId(), repeated.getLast().nodeId());
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER),
        counterpartKinds(application(aggregate, repeated.getFirst())));
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER),
        counterpartKinds(application(aggregate, repeated.getLast())));
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenUnavailableRecordMirrorEvidence_whenTranslated_thenSourceDiagnosticWithholdsTheRecord() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.util.List;
        @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
        @interface Mark { int value(); }
        record Sample(@Mark(1) List<@Mark(2) String> value) {}
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var valid = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, configuration);
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(valid).completion(),
        () -> valid.diagnostics().toString());
    var snapshot = valid.snapshot();
    var sources = new SnapshotSourceCatalog(snapshot);

    // Act
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(),
        JavaCompilerInvocation.from(configuration), sources)) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var semantics = compilation.semantics();
      var attributed = new JavaAnnotationMirrorCorrelation(semantics);
      RecordAnnotationMirrorCorrelation missingValue = new RecordAnnotationMirrorCorrelation() {
        @Override
        public boolean containsAll(List<? extends AnnotationMirror> mirrors, List<SyntaxNode> expected) {
          return expected.isEmpty() && attributed.containsAll(mirrors, expected);
        }

        @Override
        public boolean sameAnnotatedType(TypeMirror component, TypeMirror counterpart) {
          return attributed.sameAnnotatedType(component, counterpart);
        }
      };
      RecordAnnotationMirrorCorrelation wrongTypePosition = new RecordAnnotationMirrorCorrelation() {
        @Override
        public boolean containsAll(List<? extends AnnotationMirror> mirrors, List<SyntaxNode> expected) {
          return attributed.containsAll(mirrors, expected);
        }

        @Override
        public boolean sameAnnotatedType(TypeMirror component, TypeMirror counterpart) {
          return false;
        }
      };
      var outcomes = List.of(missingValue, wrongTypePosition).stream()
          .map(correlation -> new JavaAstContributionTranslator(semantics, parsed.sourceEvidence(), parsed.treeInventory(),
              correlation, JavaTranslationPolicy.legacyAnalysis()).translate(compilation.units().getFirst(), snapshot.sourceFiles().getFirst(), List.of(),
                  valid.program().types()))
          .toList();

      // Assert
      for (var outcome : outcomes) {
        assertTrue(outcome.contribution().orElseThrow().nodes().stream()
            .noneMatch(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
                && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")));
        assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-record-annotation-propagation")
            && diagnostic.origins().stream().anyMatch(origin -> source.substring(Math.toIntExact(origin.startOffset()),
                Math.toIntExact(origin.endOffsetExclusive())).contains("@Mark(1)"))));
      }
    }
  }

  @Test
  void givenMatchingRecordMirrorOmissionsOrCorruption_whenComparedWithWrittenAnnotations_thenSourceEvidenceRejectsBothSides()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Repeatable;
        import java.lang.annotation.Target;
        import java.util.List;
        @Target(ElementType.TYPE_USE) @Repeatable(Marks.class) @interface Mark { int value(); }
        @Target(ElementType.TYPE_USE) @interface Marks { Mark[] value(); }
        record Sample(List<@Mark(1) @Mark(1) String> value) {}
        record Missing(List<String> value) {}
        record Wrong(List<@Mark(2) @Mark(2) String> value) {}
        record Fewer(List<@Mark(1) String> value) {}
        record Moved(@Mark(1) @Mark(1) List<String> value) {}
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var valid = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, configuration);
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(valid).completion(),
        () -> valid.diagnostics().toString());

    // Act
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration),
        new SnapshotSourceCatalog(valid.snapshot()))) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var classes = unit.getTypeDecls().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
          .collect(Collectors.toMap(tree -> tree.getSimpleName().toString(), tree -> tree));
      var written = valid.aggregate().orElseThrow().nodes().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample"))
          .findFirst().orElseThrow().children(SyntaxRole.RECORD_COMPONENT).getFirst().requiredChild(SyntaxRole.DECLARED_TYPE);
      var sourceTypes = new JavaWrittenTypeAnnotationCorrelation(compilation.semantics());
      var mirrorTypes = new JavaAnnotationMirrorCorrelation(compilation.semantics());
      var original = (TypeElement) compilation.semantics().element(TreePath.getPath(unit, classes.get("Sample")));

      // Assert
      assertTrue(sourceTypes.matches(written, List.of(), original.getRecordComponents().getFirst().asType()));
      assertTrue(sourceTypes.matches(written, List.of(), original.getRecordComponents().getFirst().getAccessor().getReturnType()));
      for (var name : List.of("Missing", "Wrong", "Fewer", "Moved")) {
        var substituted = (TypeElement) compilation.semantics().element(TreePath.getPath(unit, classes.get(name)));
        var component = substituted.getRecordComponents().getFirst();
        var counterpart = component.getAccessor().getReturnType();
        assertTrue(mirrorTypes.sameAnnotatedType(component.asType(), counterpart), name);
        assertFalse(sourceTypes.matches(written, List.of(), component.asType()), name);
        assertFalse(sourceTypes.matches(written, List.of(), counterpart), name);
      }
    }
  }

  @Test
  void givenWildcardRecordComponentTypes_whenAnalyzed_thenBoundsAndNestedArrayAnnotationsReachDerivedCounterparts()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.util.List;
        @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
        record Plain(List<?> value) {}
        record Upper(List<? extends @Mark(1) Number> value) {}
        record Lower(List<? super @Mark(2) String> value) {}
        record Nested(List<? extends List<? super @Mark(3) String @Mark(4) []>> value) {}
        """;
    Files.writeString(sourceRoot.resolve("Records.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();

    // Assert
    assertTrue(aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION)
        .map(node -> node.value(SyntaxValue.TypeDeclaration.class).name()).toList()
        .containsAll(List.of("Plain", "Upper", "Lower", "Nested")), () -> result.diagnostics().toString());
    for (var index = 1; index <= 4; index++) {
      var occurrence = annotation(aggregate, source, "@Mark(" + index + ")");
      var target = application(aggregate, occurrence);
      assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER), counterpartKinds(target));
      assertEquals(new AnnotationScalar.IntValue(index), occurrence.children(SyntaxRole.ANNOTATION_ARGUMENT).getFirst()
          .value(SyntaxValue.AnnotationArgument.class).value());
      var roles = writtenLocationNode(aggregate, target).structuralPath().steps().stream().map(step -> step.role()).toList();
      assertTrue(roles.contains(index == 1 ? SyntaxRole.UPPER_BOUND : SyntaxRole.LOWER_BOUND));
      assertEquals(index == 3, roles.contains(SyntaxRole.COMPONENT_TYPE));
      assertEquals(source.indexOf("@Mark(" + index + ")"), occurrence.origin().orElseThrow().startOffset());
      assertTrue(target.propagatedCounterparts().stream().allMatch(counterpart -> !counterpart.declarationApplicable()
          && counterpart.typeLocation().equals(target.typeLocation())));
    }
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenDifferentWildcardBoundsAndAnnotationFacts_whenCorrelated_thenOnlyIdenticalStructureMatches()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Repeatable;
        import java.lang.annotation.Target;
        import java.util.List;
        @Target(ElementType.TYPE_USE) @Repeatable(Marks.class) @interface Mark { int value(); }
        @Target(ElementType.TYPE_USE) @interface Marks { Mark[] value(); }
        record Sample(List<? extends @Mark(1) @Mark(1) Number[]> value) {}
        record Kind(List<? super @Mark(1) @Mark(1) Number[]> value) {}
        record Bound(List<? extends @Mark(1) @Mark(1) Integer[]> value) {}
        record Value(List<? extends @Mark(2) @Mark(2) Number[]> value) {}
        record Fewer(List<? extends @Mark(1) Number[]> value) {}
        record Position(List<? extends Number @Mark(1) @Mark(1) []> value) {}
        record Unbounded(List<?> value) {}
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var valid = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, configuration);

    // Act
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration),
        new SnapshotSourceCatalog(valid.snapshot()))) {
      var parsed = session.parse();
      var compilation = session.analyze(parsed, JavaTranslationPolicy.legacyAnalysis());
      var unit = compilation.units().getFirst();
      var classes = unit.getTypeDecls().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast)
          .collect(Collectors.toMap(tree -> tree.getSimpleName().toString(), tree -> tree));
      var original = (TypeElement) compilation.semantics().element(TreePath.getPath(unit, classes.get("Sample")));
      var component = original.getRecordComponents().getFirst();
      var correlation = new JavaAnnotationMirrorCorrelation(compilation.semantics());

      // Assert
      assertTrue(correlation.sameAnnotatedType(component.asType(), component.getAccessor().getReturnType()));
      for (var name : List.of("Kind", "Bound", "Value", "Fewer", "Position", "Unbounded")) {
        var other = (TypeElement) compilation.semantics().element(TreePath.getPath(unit, classes.get(name)));
        assertFalse(correlation.sameAnnotatedType(component.asType(), other.getRecordComponents().getFirst().asType()), name);
      }
    }
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(valid).completion(),
        () -> valid.diagnostics().toString());
  }

  @Test
  void givenExplicitAndDerivedRecordCounterparts_whenAnalyzed_thenOnlyDerivedCounterpartsReceivePropagation()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
        @interface All { int value(); }
        @Target(ElementType.METHOD) @interface WrittenAccessor {}
        @Target(ElementType.PARAMETER) @interface WrittenParameter {}
        record Implicit(@All(1) String value) {}
        record Accessor(@All(2) String value) {
          @WrittenAccessor public String value() { return value; }
        }
        record Canonical(@All(3) String value) {
          Canonical(@WrittenParameter String value) { this.value = value; }
        }
        record Compact(@All(4) String value) { Compact {} }
        """;
    Files.writeString(sourceRoot.resolve("Records.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var implicit = application(aggregate, annotation(aggregate, source, "@All(1)"));
    var accessor = application(aggregate, annotation(aggregate, source, "@All(2)"));
    var canonical = application(aggregate, annotation(aggregate, source, "@All(3)"));
    var compact = application(aggregate, annotation(aggregate, source, "@All(4)"));

    // Assert
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER),
        counterpartKinds(implicit));
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.PARAMETER), counterpartKinds(accessor));
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD), counterpartKinds(canonical));
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER), counterpartKinds(compact));
    for (var application : List.of(implicit, accessor, canonical, compact)) {
      assertTrue(application.declarationNodeKey().isPresent());
      assertTrue(application.typeLocation().isPresent());
      assertTrue(application.propagatedCounterparts().stream().allMatch(counterpart -> counterpart.declarationApplicable()
          && counterpart.typeLocation().equals(application.typeLocation())));
    }
    assertEquals(6, aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.origin().isPresent()).filter(node -> {
          var range = node.origin().orElseThrow();
          var text = source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
          return text.startsWith("@All(") || text.equals("@WrittenAccessor") || text.equals("@WrittenParameter");
        }).count());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenRecordAnnotationsWithOnlyExplicitRecipients_whenAnalyzed_thenSourceApplicationsRemainRepresented()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.METHOD) @interface MethodTag {}
        @Target(ElementType.PARAMETER) @interface ParameterTag {}
        record Accessor(@MethodTag int value) {
          public int value() { return value; }
        }
        record Canonical(@ParameterTag int value) {
          Canonical(int value) { this.value = value; }
        }
        """;
    Files.writeString(sourceRoot.resolve("Records.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var method = application(aggregate, annotation(aggregate, source, "@MethodTag"));
    var parameter = application(aggregate, annotation(aggregate, source, "@ParameterTag"));

    // Assert
    for (var application : List.of(method, parameter)) {
      assertTrue(application.declarationNodeKey().isEmpty());
      assertTrue(application.typeLocation().isEmpty());
      assertTrue(application.propagatedCounterparts().isEmpty());
      assertTrue(aggregate.nodes().stream().anyMatch(node -> node.nodeId().equals(application.ownerNodeKey())
          && node.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION));
    }
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenQualifiedRecordComponentType_whenTypeUseIsInadmissible_thenCompilerRejectsTheSourceSite()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.TYPE_USE}) @interface Both {}
        record Qualified(@Both java.lang.String value) {}
        record Simple(@Both String value) {}
        """;
    Files.writeString(sourceRoot.resolve("Records.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotations = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.origin().isPresent())
        .filter(node -> source.substring(Math.toIntExact(node.origin().orElseThrow().startOffset()),
            Math.toIntExact(node.origin().orElseThrow().endOffsetExclusive())).equals("@Both"))
        .sorted((left, right) -> Long.compare(left.origin().orElseThrow().startOffset(),
            right.origin().orElseThrow().startOffset())).toList();
    assertEquals(2, annotations.size());
    var qualified = application(aggregate, annotations.getFirst());
    var simple = application(aggregate, annotations.getLast());

    // Assert
    assertEquals(Optional.of(qualified.ownerNodeKey()), qualified.declarationNodeKey());
    assertTrue(qualified.typeLocation().isEmpty());
    assertEquals(List.of(ReferenceKind.FIELD), counterpartKinds(qualified));
    assertTrue(qualified.propagatedCounterparts().getFirst().declarationApplicable());
    assertTrue(qualified.propagatedCounterparts().getFirst().typeLocation().isEmpty());
    assertEquals(Optional.of(simple.ownerNodeKey()), simple.declarationNodeKey());
    assertTrue(simple.typeLocation().isPresent());
    assertEquals(List.of(ReferenceKind.FIELD, ReferenceKind.METHOD, ReferenceKind.PARAMETER), counterpartKinds(simple));
    assertTrue(simple.propagatedCounterparts().getFirst().declarationApplicable());
    assertTrue(simple.propagatedCounterparts().stream().allMatch(counterpart -> counterpart.typeLocation().equals(simple.typeLocation())));
    assertTrue(result.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.code().equals("compiler.err.type.annotation.inadmissible")));
    assertTrue(result.diagnostics().stream()
        .noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-record-annotation-propagation")));
    assertEquals(AnalysisCompletion.PARTIAL, new SourceAnalysisFixture().analyze(result).completion());
  }

  @Test
  void givenDeclarationOnlyAnnotationOnTypeArgument_whenAnalyzed_thenCompilerAndLocationRejectTheApplication()
      throws IOException {
    // Arrange
    var source = """
        @interface Plain {}
        class Sample { java.util.List<@Plain String> values; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotation = annotation(aggregate, source, "@Plain");

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.UNRESOLVED, annotation.value(SyntaxValue.Annotation.class).location());
    assertTrue(aggregate.semanticEdges().stream().noneMatch(edge -> edge.sourceKey().equals(annotation.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unresolved-annotation-location")));
    assertEquals(AnalysisCompletion.PARTIAL, new SourceAnalysisFixture().analyze(result).completion());
  }

  @Test
  void givenTypeUseOnlyAnnotationOnTypeDeclaration_whenAnalyzed_thenItTargetsTheDeclarationSite() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Use {}
        @Use class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotation = annotation(aggregate, source, "@Use");
    var application = application(aggregate, annotation);

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.DECLARATION, annotation.value(SyntaxValue.Annotation.class).location());
    assertEquals(Optional.of(application.ownerNodeKey()), application.declarationNodeKey());
    assertTrue(application.typeLocation().isEmpty());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenAnnotatedCastInAnnotationValue_whenAnalyzed_thenCastTypeReceivesItsOwnTarget() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Use {}
        @interface Value { int value(); }
        @Value((@Use int) 1) class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotation = annotation(aggregate, source, "@Use");
    var application = application(aggregate, annotation);

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.TYPE_USE, annotation.value(SyntaxValue.Annotation.class).location());
    assertTrue(application.declarationNodeKey().isEmpty());
    var type = writtenLocationNode(aggregate, application);
    assertTrue(type.structuralPath().steps().stream().anyMatch(step -> step.role() == SyntaxRole.TYPE_OPERAND));
    assertEquals("@Use int", source.substring(Math.toIntExact(type.origin().orElseThrow().startOffset()),
        Math.toIntExact(type.origin().orElseThrow().endOffsetExclusive())));
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenQualifiedMemberType_whenItsMemberIsAnnotated_thenTargetKeepsTheMemberPosition() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Use {}
        class Outer { static class Inner {} }
        class Sample { Outer.@Use Inner value; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotation = annotation(aggregate, source, "@Use");
    var application = application(aggregate, annotation);

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.TYPE_USE, annotation.value(SyntaxValue.Annotation.class).location());
    var member = writtenLocationNode(aggregate, application);
    var underlying = member.children(SyntaxRole.UNDERLYING_TYPE).getFirst();
    assertEquals("Inner", underlying.value(SyntaxValue.SourceSymbol.class).name());
    assertTrue(member.structuralPath().steps().stream().anyMatch(step -> step.role() == SyntaxRole.DECLARED_TYPE));
    assertEquals("Outer.@Use Inner", source.substring(Math.toIntExact(member.origin().orElseThrow().startOffset()),
        Math.toIntExact(member.origin().orElseThrow().endOffsetExclusive())));
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenTypeOnlyAnnotationAtVoidMethod_whenAnalyzed_thenCompilerAndLocationRejectIt() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Use {}
        class Sample { @Use void call() {} }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotation = annotation(aggregate, source, "@Use");

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.UNRESOLVED, annotation.value(SyntaxValue.Annotation.class).location());
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unresolved-annotation-location")));
    assertEquals(AnalysisCompletion.PARTIAL, new SourceAnalysisFixture().analyze(result).completion());
  }

  @Test
  void givenValuedConstructorTypeAnnotation_whenAnalyzed_thenConstructedTargetRetainsItsAttributedValue()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
        class Sample { @Mark(17) Sample() {} }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotation = annotation(aggregate, source, "@Mark(17)");

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.TYPE_USE, annotation.value(SyntaxValue.Annotation.class).location());
    assertInstanceOf(SyntaxSemanticTarget.AnnotationTypeLocation.Constructed.class,
        application(aggregate, annotation).typeLocation().orElseThrow());
    assertEquals(new AnnotationScalar.IntValue(17), annotation.children(SyntaxRole.ANNOTATION_ARGUMENT).getFirst()
        .value(SyntaxValue.AnnotationArgument.class).value());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-annotation-attribution")));
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenSourceRetentionConstructorValues_whenAnalyzed_thenEveryValueFamilyIsAttributed()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.SOURCE) @interface Nested { int value(); }
        @Retention(RetentionPolicy.SOURCE) @Target(ElementType.TYPE_USE)
        @interface Mark {
          byte b(); short s(); char c(); int i(); long l(); float f(); double d();
          boolean z(); String text(); Class<?> type(); RetentionPolicy kind(); Nested nested(); int[] ints(); int shift();
        }
        class Sample {
          @Mark(b=(byte)(Integer.BYTES+1), s=300, c='Z', i=(1<<3)|2, l=1L<<33,
              f=1.5f+0.5f, d=3.0/2, z=!false&&true, text="a"+Integer.BYTES,
              type=String.class, kind=RetentionPolicy.SOURCE, nested=@Nested(7), ints={1,Integer.BYTES}, shift=1<<33L)
          Sample() {}
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotation = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.origin().isPresent()).filter(node -> {
          var range = node.origin().orElseThrow();
          return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())).startsWith("@Mark(");
        }).findFirst().orElseThrow();
    Map<String, AnnotationElementValue> values = annotation.children(SyntaxRole.ANNOTATION_ARGUMENT).stream()
        .map(node -> node.value(SyntaxValue.AnnotationArgument.class))
        .collect(Collectors.toMap(argument -> argument.explicitName().orElseThrow(), SyntaxValue.AnnotationArgument::value));

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.TYPE_USE, annotation.value(SyntaxValue.Annotation.class).location());
    assertEquals(14, values.size());
    assertEquals(new AnnotationScalar.ByteValue((byte) 5), values.get("b"));
    assertEquals(new AnnotationScalar.ShortValue((short) 300), values.get("s"));
    assertEquals(new AnnotationScalar.CharValue('Z'), values.get("c"));
    assertEquals(new AnnotationScalar.IntValue(10), values.get("i"));
    assertEquals(new AnnotationScalar.LongValue(1L << 33), values.get("l"));
    assertEquals(new AnnotationScalar.FloatValue(2.0f), values.get("f"));
    assertEquals(new AnnotationScalar.DoubleValue(1.5), values.get("d"));
    assertEquals(new AnnotationScalar.BooleanValue(true), values.get("z"));
    assertEquals(new AnnotationScalar.StringValue("a4"), values.get("text"));
    assertInstanceOf(AnnotationScalar.ClassLiteral.class, values.get("type"));
    assertInstanceOf(AnnotationScalar.EnumConstant.class, values.get("kind"));
    assertInstanceOf(AnnotationElementValue.Nested.class, values.get("nested"));
    assertEquals(new AnnotationElementValue.Array(List.of(new AnnotationScalar.IntValue(1),
        new AnnotationScalar.IntValue(4))), values.get("ints"));
    assertEquals(new AnnotationScalar.IntValue(2), values.get("shift"));
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenConstantStringComparisonsOnRecordAndConstructor_whenAnalyzed_thenValuesAndSourceExpressionsArePreserved()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        import java.lang.annotation.Target;
        @Retention(RetentionPolicy.SOURCE) @Target({ElementType.FIELD, ElementType.TYPE_USE})
        @interface Mark { boolean[] value(); }
        record Sample(@Mark({"a" == "a", "a" + "b" == "ab", "a" != "b", "a" == "b", "a" != "a"}) int value) {}
        class Constructed {
          @Mark({"a" == "a", "a" + "b" == "ab", "a" != "b", "a" == "b", "a" != "a"}) Constructed() {}
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var occurrences = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.origin().isPresent()).filter(node -> {
          var range = node.origin().orElseThrow();
          return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())).startsWith("@Mark(");
        }).toList();

    // Assert
    assertEquals(2, occurrences.size(), () -> result.diagnostics().toString());
    var expected = new AnnotationElementValue.Array(List.of(new AnnotationScalar.BooleanValue(true),
        new AnnotationScalar.BooleanValue(true), new AnnotationScalar.BooleanValue(true),
        new AnnotationScalar.BooleanValue(false), new AnnotationScalar.BooleanValue(false)));
    for (var occurrence : occurrences) {
      var argument = occurrence.children(SyntaxRole.ANNOTATION_ARGUMENT).getFirst();
      assertEquals(expected, argument.value(SyntaxValue.AnnotationArgument.class).value());
      var items = argument.requiredChild(SyntaxRole.VALUE).children(SyntaxRole.VALUE);
      assertEquals(List.of("EQUAL_TO", "EQUAL_TO", "NOT_EQUAL_TO", "EQUAL_TO", "NOT_EQUAL_TO"),
          items.stream().map(item -> item.sourceKind().name()).toList());
      assertEquals("PLUS", items.get(1).requiredChild(SyntaxRole.LEFT_OPERAND).sourceKind().name());
      assertTrue(application(aggregate, occurrence).typeLocation().isPresent());
    }
    assertEquals(1, occurrences.stream().filter(node -> application(aggregate, node).typeLocation().orElseThrow()
        instanceof SyntaxSemanticTarget.AnnotationTypeLocation.Constructed).count());
    assertEquals(1, occurrences.stream().filter(node -> application(aggregate, node).typeLocation().orElseThrow()
        instanceof SyntaxSemanticTarget.AnnotationTypeLocation.Written).count());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion(),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenDeclarationOnlyReceiverAnnotations_whenAnalyzed_thenLocatedErrorsBlockCompletionWithoutDeclarationApplications()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.PARAMETER) @interface ParameterOnly {}
        @interface WithoutTarget {}
        abstract class Sample {
          abstract void first(@ParameterOnly Sample this);
          abstract void second(@WithoutTarget Sample this);
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();

    // Assert
    for (var spelling : List.of("@ParameterOnly", "@WithoutTarget")) {
      var occurrence = annotation(aggregate, source, spelling);
      var range = occurrence.origin().orElseThrow();
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("compiler.err.annotation.type.not.applicable.to.type")
          && diagnostic.origins().stream().anyMatch(origin -> origin.startOffset() == range.startOffset()
              && origin.endOffsetExclusive() == range.endOffsetExclusive())), () -> result.diagnostics().toString());
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unresolved-annotation-location")
          && diagnostic.origins().contains(range)), () -> result.diagnostics().toString());
      assertEquals(SyntaxValue.AnnotationLocation.UNRESOLVED, occurrence.value(SyntaxValue.Annotation.class).location());
      assertTrue(aggregate.semanticEdges().stream().noneMatch(edge -> edge.sourceKey().equals(occurrence.nodeId())
          && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET));
    }
    assertEquals(AnalysisCompletion.PARTIAL, new SourceAnalysisFixture().analyze(result).completion());
  }

  @Test
  void givenValidNeighborTypeEndpoints_whenApplicationIsRetargeted_thenAggregateRejectsTheSemanticMismatch()
      throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Mark {}
        class Sample { java.util.List<@Mark ? extends @Mark String> values; }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var annotations = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.origin().isPresent())
        .filter(node -> source.substring(Math.toIntExact(node.origin().orElseThrow().startOffset()),
            Math.toIntExact(node.origin().orElseThrow().endOffsetExclusive())).equals("@Mark"))
        .sorted((left, right) -> Long.compare(left.origin().orElseThrow().startOffset(),
            right.origin().orElseThrow().startOffset())).toList();
    assertEquals(2, annotations.size());
    var first = annotations.getFirst();
    var firstApplication = application(aggregate, first);
    var secondApplication = application(aggregate, annotations.getLast());
    var wrong = new SyntaxSemanticTarget.AnnotationApplication(firstApplication.ownerNodeKey(),
        firstApplication.declarationNodeKey(), secondApplication.typeLocation(), firstApplication.propagatedCounterparts(), firstApplication.fieldDeclarators());
    var replacement = new SyntaxSemanticEdge(first.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET, wrong);
    var edges = aggregate.semanticEdges().stream().map(edge -> edge.sourceKey().equals(first.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET ? replacement : edge).toList();
    var root = aggregate.roots().getFirst();
    var contribution = new SyntaxSourceContribution(root.sourceFileKey(), root, aggregate.nodes(),
        aggregate.structuralEdges(), edges);

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> AggregateFixture.builder(result.snapshot()).add(contribution).seal());

    // Assert
    assertTrue(failure.getMessage().contains("does not match its exact source location"), failure::getMessage);
    assertTrue(failure.getMessage().contains(first.nodeId()));
  }

  @Test
  void givenIdenticalArrayAnnotationAtDifferentDimensions_whenAnalyzed_thenTargetPathChangesDeterministically()
      throws IOException {
    // Arrange
    var firstSource = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Mark {}
        class Sample { String @Mark [] [] values; }
        """;
    var secondSource = firstSource.replace("String @Mark [] []", "String [] @Mark []");
    var file = sourceRoot.resolve("Sample.java");
    Files.writeString(file, firstSource);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var repeated = frontend.analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var firstTarget = application(first.aggregate().orElseThrow(), annotation(first.aggregate().orElseThrow(), firstSource, "@Mark"));
    Files.writeString(file, secondSource);
    var moved = frontend.analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var movedTarget = application(moved.aggregate().orElseThrow(), annotation(moved.aggregate().orElseThrow(), secondSource, "@Mark"));

    // Assert
    assertEquals(first.aggregate(), repeated.aggregate());
    assertEquals(first.diagnostics(), repeated.diagnostics());
    assertTrue(!firstTarget.typeLocation().equals(movedTarget.typeLocation()));
    assertTrue(!writtenLocationNode(first.aggregate().orElseThrow(), firstTarget).structuralPath()
        .equals(writtenLocationNode(moved.aggregate().orElseThrow(), movedTarget).structuralPath()));
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(first).completion(),
        () -> first.diagnostics().toString());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(moved).completion(),
        () -> moved.diagnostics().toString());
  }

  @Test
  void givenBodyLocalAnnotatedCast_whenAnalyzed_thenTheExactWrittenAnnotationAndTypeApplicationComplete() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Mark {}
        class Sample { int value(Object input) { return (@Mark Integer) input; } }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.isError()), result.diagnostics().toString());
    var aggregate = result.aggregate().orElseThrow();
    var cast = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
    var writtenType = cast.requiredChild(SyntaxRole.TYPE_OPERAND);
    assertEquals(SyntaxKind.TYPE_USE, writtenType.syntaxKind());
    assertEquals(JavaSourceKind.ANNOTATED_TYPE, writtenType.sourceKind());
    assertEquals("java.lang.Integer", assertInstanceOf(SyntaxType.Resolved.class, writtenType.type()).identity().erasedName());
    var start = source.indexOf("@Mark Integer");
    assertEquals(Optional.of(new SourceSpan(writtenType.sourceFileKey(), start, start + "@Mark Integer".length())), writtenType.origin());
    var mark = annotation(aggregate, source, "@Mark");
    assertEquals(List.of(mark), writtenType.children(SyntaxRole.ANNOTATION));
    assertEquals(SyntaxValue.AnnotationLocation.TYPE_USE, mark.value(SyntaxValue.Annotation.class).location());
    var target = application(aggregate, mark);
    assertTrue(target.declarationNodeKey().isEmpty());
    assertEquals(writtenType, writtenLocationNode(aggregate, target));
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(result).completion());
  }

  @Test
  void givenExplicitRecordAccessor_whenCounterpartOriginIsForged_thenTypeConstructionRejectsIt() throws IOException {
    // Arrange
    var source = """
        record Sample(String value) { public String value() { return value; } }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var type = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
            && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample"))
        .findFirst().orElseThrow();
    var component = type.children(SyntaxRole.RECORD_COMPONENT).getFirst();
    var facts = component.value(SyntaxValue.RecordComponentDeclaration.class);
    var keys = facts.counterparts();
    var altered = new SyntaxValue.RecordComponentDeclaration(facts.name(), facts.ownerKey(), facts.ordinal(),
        facts.writtenModifiers(), facts.effectiveModifiers(), facts.declaredType(), facts.annotations(), facts.varArgs(),
        new SyntaxValue.RecordComponentDeclaration.Counterparts(keys.backingFieldKey(), keys.accessorKey(),
            keys.canonicalParameterKey(), true, keys.canonicalParameterDerived()));
    var forged = SyntaxNode.source(component.sourceFileKey(), component.structuralPath(), component.sourceKind(),
        component.syntaxKind(), component.type(), altered, component.children(), component.semanticReference(),
        component.origin().orElseThrow());
    var children = type.children().stream().map(child -> child.node().nodeId().equals(component.nodeId())
        ? new SyntaxChild(child.role(), child.position(), forged) : child).toList();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(type.sourceFileKey(),
        type.structuralPath(), type.sourceKind(), type.syntaxKind(), type.type(), type.value(), children,
        type.semanticReference(), type.origin().orElseThrow()));

    // Assert
    assertTrue(failure.getMessage().contains("Record counterpart provenance"));
  }

  @Test
  void givenForeignRecordCounterpartKeys_whenTypeIsReconstructed_thenEveryIdentityIsRejected() throws IOException {
    // Arrange
    var source = "record Sample(String value) {}";
    Files.writeString(sourceRoot.resolve("Sample.java"), source);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var type = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
            && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample"))
        .findFirst().orElseThrow();
    var component = type.children(SyntaxRole.RECORD_COMPONENT).getFirst();
    var facts = component.value(SyntaxValue.RecordComponentDeclaration.class);
    var keys = facts.counterparts();
    var forgedKeys = List.of(
        new SyntaxValue.RecordComponentDeclaration.Counterparts("type:Foreign#field:value", keys.accessorKey(),
            keys.canonicalParameterKey(), keys.accessorImplicit(), keys.canonicalParameterDerived()),
        new SyntaxValue.RecordComponentDeclaration.Counterparts(keys.backingFieldKey(), "type:Foreign#value()",
            keys.canonicalParameterKey(), keys.accessorImplicit(), keys.canonicalParameterDerived()),
        new SyntaxValue.RecordComponentDeclaration.Counterparts(keys.backingFieldKey(), keys.accessorKey(),
            "type:Foreign#<init>(java.lang.String):parameter:0", keys.accessorImplicit(),
            keys.canonicalParameterDerived()));

    // Act and Assert
    for (var forgedKey : forgedKeys) {
      var altered = new SyntaxValue.RecordComponentDeclaration(facts.name(), facts.ownerKey(), facts.ordinal(),
          facts.writtenModifiers(), facts.effectiveModifiers(), facts.declaredType(), facts.annotations(), facts.varArgs(), forgedKey);
      var forged = SyntaxNode.source(component.sourceFileKey(), component.structuralPath(), component.sourceKind(),
          component.syntaxKind(), component.type(), altered, component.children(), component.semanticReference(),
          component.origin().orElseThrow());
      var children = type.children().stream().map(child -> child.node().nodeId().equals(component.nodeId())
          ? new SyntaxChild(child.role(), child.position(), forged) : child).toList();
      var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(type.sourceFileKey(),
          type.structuralPath(), type.sourceKind(), type.syntaxKind(), type.type(), type.value(), children,
          type.semanticReference(), type.origin().orElseThrow()));
      assertTrue(failure.getMessage().contains("Record counterpart identities"));
    }
  }

  private static SyntaxNode annotation(JavaAstAggregate aggregate, String source, String text) {
    return aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .filter(node -> node.origin().isPresent())
        .filter(node -> {
          var range = node.origin().orElseThrow();
          return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())).equals(text);
        }).findFirst().orElseThrow();
  }

  private static SyntaxNode replaceValue(SyntaxNode node, String selectedKey, SyntaxValue value) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        replaceValue(child.node(), selectedKey, value))).toList();
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(),
        node.nodeId().equals(selectedKey) ? value : node.value(), children, node.semanticReference(), node.origin().orElseThrow());
  }

  private static JavaAstAggregate sealWithRecomputedApplications(InputSnapshot snapshot,
      JavaAstAggregate aggregate, SyntaxNode root) {
    var nodes = root.descendantsAndSelf();
    var groups = SharedVariableDeclarations.index(nodes);
    var edges = new ArrayList<>(aggregate.semanticEdges().stream()
        .filter(edge -> edge.relation() != SyntaxSemanticRelation.ANNOTATED_TARGET).toList());
    nodes.forEach(parent -> parent.children(SyntaxRole.ANNOTATION).forEach(annotation -> edges.add(new SyntaxSemanticEdge(annotation.nodeId(),
        SyntaxSemanticRelation.ANNOTATED_TARGET, SyntaxSemanticTarget.AnnotationApplication.forAttachment(parent, annotation, Optional.empty(), groups.getOrDefault(parent.nodeId(), List.of()))))));
    return AggregateFixture.builder(snapshot).add(new SyntaxSourceContribution(root.sourceFileKey(), root, nodes,
        nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), edges)).seal();
  }

  private static SyntaxSemanticTarget.AnnotationApplication application(JavaAstAggregate aggregate, SyntaxNode annotation) {
    var edges = aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId()))
        .filter(edge -> edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).toList();
    assertEquals(1, edges.size());
    return assertInstanceOf(SyntaxSemanticTarget.AnnotationApplication.class, edges.getFirst().target());
  }

  private static List<ReferenceKind> counterpartKinds(SyntaxSemanticTarget.AnnotationApplication application) {
    return application.propagatedCounterparts().stream().map(counterpart -> counterpart.reference().referenceKind()).toList();
  }

  private static SyntaxNode writtenLocationNode(JavaAstAggregate aggregate,
      SyntaxSemanticTarget.AnnotationApplication application) {
    var written = assertInstanceOf(SyntaxSemanticTarget.AnnotationTypeLocation.Written.class,
        application.typeLocation().orElseThrow());
    return aggregate.nodes().stream().filter(node -> node.nodeId().equals(written.nodeKey())).findFirst().orElseThrow();
  }
}
