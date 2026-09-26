package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeatureObservation;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.syntax.SyntaxSemanticRelation;
import org.aquilagraph.syntax.SyntaxSemanticTarget;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaAnnotationAstTest {
  @TempDir Path sourceRoot;

  @Test
  void givenDeclarationMarkersAndEmptyNormals_whenAnalyzed_thenEachOccurrenceKeepsItsFormTypeAndTarget() throws IOException {
    // Arrange
    var source = """
        @interface A {}
        @A class Sample<@A T> {
          @A() int field;
          @A Sample(@A int input) {}
          @A() void method(@A int input) {}
        }
        """;
    Files.writeString(sourceRoot.resolve("Sample.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var occurrences = aggregate.roots().getFirst().descendantsAndSelf().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation).toList();

    // Assert
    assertEquals(7, occurrences.size(), () -> result.diagnostics().toString());
    assertEquals(List.of(SyntaxValue.AnnotationForm.MARKER, SyntaxValue.AnnotationForm.MARKER,
        SyntaxValue.AnnotationForm.EMPTY_NORMAL, SyntaxValue.AnnotationForm.MARKER, SyntaxValue.AnnotationForm.MARKER,
        SyntaxValue.AnnotationForm.EMPTY_NORMAL, SyntaxValue.AnnotationForm.MARKER),
        occurrences.stream().map(node -> node.value(SyntaxValue.Annotation.class).form()).toList());
    assertTrue(occurrences.stream().allMatch(node -> node.semanticReference().orElseThrow().status() == ResolutionStatus.RESOLVED));
    assertTrue(occurrences.stream().allMatch(node -> node.semanticReference().orElseThrow().symbolKey().isPresent()));
    for (var occurrence : occurrences) {
      var targets = aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(occurrence.nodeId())
          && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).toList();
      assertEquals(1, targets.size());
      assertTrue(targets.getFirst().target() instanceof SyntaxSemanticTarget.AnnotationApplication);
      var range = occurrence.origin().orElseThrow();
      assertTrue(source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())).startsWith("@A"));
    }
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenAllWrittenFormsWithCommentsAndEscapedDelimiters_whenAnalyzed_thenPreAttributionFormsAndOffsetsRemainDistinct()
      throws IOException {
    // Arrange
    var escapedEmpty = "@V" + '\\' + "u0028" + '\\' + "u0029";
    var escapedComment = "@V" + '\\' + "u002f" + '\\' + "u002a hidden ( " + '\\' + "u002a" + '\\' + "u002f()";
    var source = "@interface V { int value() default 0; }\n"
        + "@V class Marker {}\n"
        + "@V/* comment */() class Empty {}\n"
        + "@V(2) class Single {}\n"
        + "@V(value=3) class Named {}\n"
        + escapedEmpty + " class Escaped {}\n"
        + escapedComment + " class Commented {}\n";
    Files.writeString(sourceRoot.resolve("Forms.java"), source);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var repeated = frontend.analyze(sourceRoot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var occurrences = result.aggregate().orElseThrow().roots().getFirst().descendantsAndSelf().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation).toList();

    // Assert
    assertEquals(List.of(SyntaxValue.AnnotationForm.MARKER, SyntaxValue.AnnotationForm.EMPTY_NORMAL,
        SyntaxValue.AnnotationForm.SINGLE_ELEMENT, SyntaxValue.AnnotationForm.NAMED_NORMAL,
        SyntaxValue.AnnotationForm.EMPTY_NORMAL, SyntaxValue.AnnotationForm.EMPTY_NORMAL),
        occurrences.stream().map(node -> node.value(SyntaxValue.Annotation.class).form()).toList());
    assertEquals(List.of("@V", "@V/* comment */()", "@V(2)", "@V(value=3)", escapedEmpty, escapedComment),
        occurrences.stream().map(node -> {
          var range = node.origin().orElseThrow();
          return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
        }).toList());
    assertEquals(List.of(),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(result.aggregate(), repeated.aggregate());
    assertEquals(result.diagnostics(), repeated.diagnostics());
    assertEquals(List.of(), occurrences.get(2).value(SyntaxValue.Annotation.class).arguments().getFirst().explicitName().stream().toList());
    assertEquals("value", occurrences.get(3).value(SyntaxValue.Annotation.class).arguments().getFirst().explicitName().orElseThrow());
  }

  @Test
  void givenPackageAndModuleAnnotationForms_whenAnalyzed_thenEachTargetsItsOwnDeclaration() throws IOException {
    // Arrange
    var packageDirectory = Files.createDirectory(sourceRoot.resolve("sample"));
    Files.writeString(packageDirectory.resolve("A.java"), "package sample; public @interface A {}\n");
    Files.writeString(packageDirectory.resolve("package-info.java"), "@sample.A package sample;\n");
    Files.writeString(sourceRoot.resolve("module-info.java"), "@sample.A module sample.module {}\n");

    for (var form : List.of(SyntaxValue.AnnotationForm.MARKER, SyntaxValue.AnnotationForm.EMPTY_NORMAL)) {
      // Act
      var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
          CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
      var aggregate = result.aggregate().orElseThrow();
      var declarations = aggregate.nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.PACKAGE
          || node.sourceKind() == JavaSourceKind.MODULE).filter(node -> !node.children(SyntaxRole.ANNOTATION).isEmpty()).toList();

      // Assert
      assertEquals(2, declarations.size(), () -> result.diagnostics().toString());
      for (var declaration : declarations) {
        var occurrence = declaration.requiredChild(SyntaxRole.ANNOTATION);
        assertEquals(form, occurrence.value(SyntaxValue.Annotation.class).form());
        assertEquals(ResolutionStatus.RESOLVED, occurrence.semanticReference().orElseThrow().status());
        assertEquals(1, aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(occurrence.nodeId())
            && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
            && targetsDeclaration(edge.target(), declaration.nodeId())).count());
      }
      assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
      Files.writeString(packageDirectory.resolve("package-info.java"), "@sample.A() package sample;\n");
      Files.writeString(sourceRoot.resolve("module-info.java"), "@sample.A() module sample.module {}\n");
    }
  }

  @Test
  void givenAnnotatedDeclarationKinds_whenAnalyzed_thenEveryWrittenSiteHasOneTypedTarget() throws IOException {
    // Arrange
    var source = """
        @interface A {}
        @A @interface Meta { @A int value(); }
        @A interface Contract {}
        @A enum Choice { @A ONE; }
        @A record Pair(@A int value) {}
        @A class Owner {}
        """;
    Files.writeString(sourceRoot.resolve("Declarations.java"), source);

    for (var form : List.of(SyntaxValue.AnnotationForm.MARKER, SyntaxValue.AnnotationForm.EMPTY_NORMAL)) {
      // Act
      var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
          CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
      var aggregate = result.aggregate().orElseThrow();
      var annotated = aggregate.nodes().stream().filter(node -> !node.children(SyntaxRole.ANNOTATION).isEmpty()).toList();

      // Assert
      assertEquals(8, annotated.size(), () -> result.diagnostics().toString());
      assertEquals(5, annotated.stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION).count());
      assertEquals(1, annotated.stream().filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).count());
      assertEquals(1, annotated.stream().filter(node -> node.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION).count());
      assertEquals(1, annotated.stream().filter(node -> node.syntaxKind() == SyntaxKind.METHOD_DECLARATION).count());
      assertTrue(annotated.stream().allMatch(node -> node.children(SyntaxRole.ANNOTATION).stream()
          .allMatch(annotation -> annotation.value(SyntaxValue.Annotation.class).form() == form)));
      for (var declaration : annotated) {
        var annotation = declaration.requiredChild(SyntaxRole.ANNOTATION);
        assertEquals(ResolutionStatus.RESOLVED, annotation.semanticReference().orElseThrow().status());
        assertEquals(1, aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
            && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
            && targetsDeclaration(edge.target(), declaration.nodeId())).count());
        var range = annotation.origin().orElseThrow();
        assertEquals(form == SyntaxValue.AnnotationForm.MARKER ? "@A" : "@A()",
            aggregate.snapshot().sourceFiles().getFirst().content().substring(Math.toIntExact(range.startOffset()),
                Math.toIntExact(range.endOffsetExclusive())));
      }
      assertEquals(List.of("frontend.unsupported-enum-constant-initialization"),
          result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
      Files.writeString(sourceRoot.resolve("Declarations.java"), source.replace("@A ", "@A() "));
    }
  }

  @Test
  void givenTargetSpecificDeclarationAnnotations_whenAnalyzed_thenEachSelectsItsDeclaredTypeAndExactOwner() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE) @interface OnType {}
        @Target(ElementType.ANNOTATION_TYPE) @interface OnAnnotationType {}
        @Target(ElementType.FIELD) @interface OnField {}
        @Target(ElementType.METHOD) @interface OnMethod {}
        @Target(ElementType.CONSTRUCTOR) @interface OnConstructor {}
        @Target(ElementType.PARAMETER) @interface OnParameter {}
        @Target(ElementType.TYPE_PARAMETER) @interface OnTypeParameter {}
        @Target(ElementType.RECORD_COMPONENT) @interface OnRecordComponent {}
        @OnAnnotationType @interface Meta { @OnMethod int value(); }
        @OnType interface Contract {}
        @OnType enum Choice { @OnField ONE; }
        @OnType record Pair(@OnRecordComponent int value) {}
        @OnType class Owner<@OnTypeParameter T> {
          @OnField int field;
          @OnConstructor Owner(@OnParameter int input) {}
          @OnMethod void method(@OnParameter int input) {}
          @OnType class Nested {}
        }
        """;
    Files.writeString(sourceRoot.resolve("Targeted.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var typeKeys = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION)
        .map(node -> node.value(SyntaxValue.TypeDeclaration.class))
        .collect(Collectors.toMap(SyntaxValue.TypeDeclaration::name, SyntaxValue.TypeDeclaration::declarationKey));
    var applied = aggregate.nodes().stream().flatMap(owner -> owner.children(SyntaxRole.ANNOTATION).stream()
        .map(annotation -> new AppliedAnnotation(owner, annotation)))
        .filter(pair -> annotationExcerpt(source, pair.annotation()).startsWith("@On")).toList();

    // Assert
    assertEquals(15, applied.size(), () -> result.diagnostics().toString());
    for (var pair : applied) {
      var name = annotationExcerpt(source, pair.annotation()).substring(1);
      assertEquals(typeKeys.get(name), pair.annotation().semanticReference().orElseThrow().symbolKey().orElseThrow());
      assertEquals(1, aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(pair.annotation().nodeId())
          && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
          && targetsDeclaration(edge.target(), pair.owner().nodeId())).count());
    }
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenTargetSpecificPackageAndModuleAnnotations_whenAnalyzed_thenEachSelectsItsLegalDeclaration() throws IOException {
    // Arrange
    var directory = Files.createDirectory(sourceRoot.resolve("sample"));
    Files.writeString(directory.resolve("OnPackage.java"), "package sample; import java.lang.annotation.*; "
        + "@Target(ElementType.PACKAGE) public @interface OnPackage {}\n");
    Files.writeString(directory.resolve("OnModule.java"), "package sample; import java.lang.annotation.*; "
        + "@Target(ElementType.MODULE) public @interface OnModule {}\n");
    Files.writeString(directory.resolve("package-info.java"), "@sample.OnPackage package sample;\n");
    Files.writeString(sourceRoot.resolve("module-info.java"), "@sample.OnModule module sample.module {}\n");

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var typeKeys = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION)
        .map(node -> node.value(SyntaxValue.TypeDeclaration.class))
        .collect(Collectors.toMap(SyntaxValue.TypeDeclaration::name, SyntaxValue.TypeDeclaration::declarationKey));
    var declarations = aggregate.nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.PACKAGE
        || node.sourceKind() == JavaSourceKind.MODULE).filter(node -> !node.children(SyntaxRole.ANNOTATION).isEmpty()).toList();

    // Assert
    assertEquals(2, declarations.size(), () -> result.diagnostics().toString());
    for (var declaration : declarations) {
      var annotation = declaration.requiredChild(SyntaxRole.ANNOTATION);
      var name = declaration.sourceKind() == JavaSourceKind.PACKAGE ? "OnPackage" : "OnModule";
      assertEquals(typeKeys.get(name), annotation.semanticReference().orElseThrow().symbolKey().orElseThrow());
      assertEquals(1, aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
          && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
          && targetsDeclaration(edge.target(), declaration.nodeId())).count());
    }
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
  }

  @Test
  void givenRepeatableMarkers_whenAnalyzed_thenWrittenOccurrencesStaySeparateAndOrdered() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.Repeatable;
        @Repeatable(Repeat.Container.class)
        @interface Repeat {
          @interface Container { Repeat[] value(); }
        }
        @Repeat @Repeat() class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("Repeat.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var sample = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")).findFirst().orElseThrow();
    var occurrences = sample.children(SyntaxRole.ANNOTATION);

    // Assert
    assertEquals(2, occurrences.size(), () -> result.diagnostics().toString());
    assertTrue(occurrences.getFirst().origin().orElseThrow().startOffset()
        < occurrences.getLast().origin().orElseThrow().startOffset());
    assertEquals(List.of(SyntaxValue.AnnotationForm.MARKER, SyntaxValue.AnnotationForm.EMPTY_NORMAL),
        occurrences.stream().map(node -> node.value(SyntaxValue.Annotation.class).form()).toList());
    assertTrue(occurrences.stream().allMatch(node -> node.semanticReference().orElseThrow().symbolKey()
        .equals(occurrences.getFirst().semanticReference().orElseThrow().symbolKey())));
    assertEquals(2, aggregate.semanticEdges().stream().filter(edge -> edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
        && targetsDeclaration(edge.target(), sample.nodeId())).count());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());

    Files.writeString(sourceRoot.resolve("Repeat.java"), source.replace("@Repeat @Repeat() class", "@Repeat() @Repeat class"));
    var reversed = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var reversedSample = reversed.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")).findFirst().orElseThrow();
    assertEquals(List.of(SyntaxValue.AnnotationForm.EMPTY_NORMAL, SyntaxValue.AnnotationForm.MARKER),
        reversedSample.children(SyntaxRole.ANNOTATION).stream()
            .map(node -> node.value(SyntaxValue.Annotation.class).form()).toList());
    assertTrue(!aggregate.equals(reversed.aggregate().orElseThrow()));
  }

  @Test
  void givenCompiledRepeatableAnnotation_whenAnalyzed_thenWrittenMarkersCompleteWithoutSourceContainer() throws IOException {
    // Arrange
    var library = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "repeatable-library-");
    var classes = Files.createDirectory(library.resolve("classes"));
    var definition = library.resolve("Repeat.java");
    Files.writeString(definition, "import java.lang.annotation.Repeatable; "
        + "@Repeatable(Repeat.Container.class) @interface Repeat { @interface Container { Repeat[] value(); } }");
    assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-d", classes.toString(),
        definition.toString()));
    Files.writeString(sourceRoot.resolve("Sample.java"), "@Repeat @Repeat class Sample {}\n");
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(classes), List.of(), List.of());

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, configuration);
    var aggregate = result.aggregate().orElseThrow();
    var sample = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")).findFirst().orElseThrow();
    var occurrences = sample.children(SyntaxRole.ANNOTATION);

    // Assert
    assertEquals(2, occurrences.size());
    assertEquals(List.of(SyntaxValue.AnnotationForm.MARKER, SyntaxValue.AnnotationForm.MARKER),
        occurrences.stream().map(node -> node.value(SyntaxValue.Annotation.class).form()).toList());
    assertEquals(2, aggregate.semanticEdges().stream().filter(edge -> edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
        && targetsDeclaration(edge.target(), sample.nodeId())).count());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
    assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(result).completion());
  }

  @Test
  void givenUnknownAndNonannotationTypes_whenAnalyzed_thenSourceOccurrencesKeepBlockingResolution() throws IOException {
    // Arrange
    var source = "class Plain {} @Missing class Unknown {} @Plain class WrongKind {}\n";
    Files.writeString(sourceRoot.resolve("Invalid.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var annotations = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation).toList();
    var missing = annotations.stream().filter(node -> annotationExcerpt(source, node).equals("@Missing")).findFirst().orElseThrow();
    var wrongKind = annotations.stream().filter(node -> annotationExcerpt(source, node).equals("@Plain")).findFirst().orElseThrow();
    var unresolved = result.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unresolved-annotation-type")).toList();
    var compilerErrors = result.diagnostics().stream().filter(diagnostic -> diagnostic.code().startsWith("compiler.err.")).toList();

    // Assert
    assertEquals(2, annotations.size(), () -> result.diagnostics().toString());
    assertTrue(annotations.stream().allMatch(node -> node.semanticReference().orElseThrow().status()
        != ResolutionStatus.RESOLVED));
    assertTrue(annotations.stream().allMatch(node -> node.value(SyntaxValue.Annotation.class).location()
        == SyntaxValue.AnnotationLocation.UNRESOLVED));
    assertEquals(2, unresolved.size());
    assertEquals(List.of(missing.origin().orElseThrow(), wrongKind.origin().orElseThrow()),
        unresolved.stream().map(diagnostic -> diagnostic.origins().getFirst()).toList());
    assertTrue(unresolved.stream().allMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
    assertEquals(2, compilerErrors.size());
    var missingType = compilerErrors.stream().filter(diagnostic -> diagnostic.code().equals("compiler.err.cant.resolve"))
        .findFirst().orElseThrow();
    var nonannotationType = compilerErrors.stream().filter(diagnostic -> diagnostic.code().equals("compiler.err.prob.found.req"))
        .findFirst().orElseThrow();
    assertEquals(AnalysisOutcomeKind.COMPILER_ERROR, missingType.outcomeKind());
    assertEquals(AnalysisOutcomeKind.COMPILER_ERROR, nonannotationType.outcomeKind());
    assertEquals(missing.sourceFileKey(), missingType.origins().getFirst().sourceFileKey());
    assertEquals(source.indexOf("Missing"), missingType.origins().getFirst().startOffset());
    assertEquals(wrongKind.sourceFileKey(), nonannotationType.origins().getFirst().sourceFileKey());
    assertEquals(source.indexOf("@Plain") + 1, nonannotationType.origins().getFirst().startOffset());
    assertTrue(result.aggregate().orElseThrow().semanticEdges().stream().noneMatch(edge -> edge.relation()
        == SyntaxSemanticRelation.ANNOTATED_TARGET && annotations.stream().anyMatch(node -> node.nodeId().equals(edge.sourceKey()))));
    assertEquals(AnalysisCompletion.INCOMPLETE, new AnalysisEngine().analyze(result).completion());
  }

  @Test
  void givenFieldAndTypeApplicableAnnotation_whenAnalyzed_thenOneOccurrenceRetainsBothTargets() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.FIELD, ElementType.TYPE_USE}) @interface Dual {}
        class Sample { @Dual String value; }
        """;
    Files.writeString(sourceRoot.resolve("Dual.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var dual = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation)
        .filter(node -> source.substring(Math.toIntExact(node.origin().orElseThrow().startOffset()),
            Math.toIntExact(node.origin().orElseThrow().endOffsetExclusive())).equals("@Dual"))
        .findFirst().orElseThrow();

    // Assert
    assertEquals(SyntaxValue.AnnotationLocation.DUAL, dual.value(SyntaxValue.Annotation.class).location());
    var application = result.aggregate().orElseThrow().semanticEdges().stream().filter(edge -> edge.sourceKey().equals(dual.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET)
        .map(edge -> (SyntaxSemanticTarget.AnnotationApplication) edge.target()).findFirst().orElseThrow();
    assertEquals(Optional.of(application.ownerNodeKey()), application.declarationNodeKey());
    assertTrue(application.typeLocation().isPresent());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unresolved-annotation-location")));
  }

  @Test
  void givenIllegalTargetAndNonrepeatableDuplicate_whenAnalyzed_thenCompilerErrorsBlockCompletion() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.METHOD) @interface OnlyMethod {}
        @interface Single {}
        @OnlyMethod class WrongTarget {}
        @Single @Single class Duplicate {}
        """;
    Files.writeString(sourceRoot.resolve("InvalidSites.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var wrongTarget = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("WrongTarget")).findFirst().orElseThrow();
    var duplicate = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Duplicate")).findFirst().orElseThrow();

    // Assert
    assertEquals(1, wrongTarget.children(SyntaxRole.ANNOTATION).size());
    assertEquals(2, duplicate.children(SyntaxRole.ANNOTATION).size());
    var compilerErrors = result.diagnostics().stream().filter(diagnostic -> diagnostic.code().startsWith("compiler.err.")).toList();
    assertEquals(2, compilerErrors.size(), () -> result.diagnostics().toString());
    var illegalTarget = compilerErrors.stream().filter(diagnostic -> diagnostic.code().equals("compiler.err.annotation.type.not.applicable"))
        .findFirst().orElseThrow();
    var nonrepeatableDuplicate = compilerErrors.stream()
        .filter(diagnostic -> diagnostic.code().equals("compiler.err.duplicate.annotation.missing.container")).findFirst().orElseThrow();
    assertEquals(AnalysisOutcomeKind.COMPILER_ERROR, illegalTarget.outcomeKind());
    assertEquals(AnalysisOutcomeKind.COMPILER_ERROR, nonrepeatableDuplicate.outcomeKind());
    assertEquals(wrongTarget.sourceFileKey(), illegalTarget.origins().getFirst().sourceFileKey());
    assertEquals(source.indexOf("@OnlyMethod class"), illegalTarget.origins().getFirst().startOffset());
    assertEquals(duplicate.sourceFileKey(), nonrepeatableDuplicate.origins().getFirst().sourceFileKey());
    assertEquals(source.indexOf("@Single @Single") + "@Single ".length(),
        nonrepeatableDuplicate.origins().getFirst().startOffset());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unresolved-annotation-type")));
    assertEquals(AnalysisCompletion.INCOMPLETE, new AnalysisEngine().analyze(result).completion());
  }

  @Test
  void givenSourceClassAndRuntimeRetention_whenAnalyzed_thenEachWrittenAnnotationIsRetained() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        @Retention(RetentionPolicy.SOURCE) @interface SourceMark {}
        @Retention(RetentionPolicy.CLASS) @interface ClassMark {}
        @Retention(RetentionPolicy.RUNTIME) @interface RuntimeMark {}
        @SourceMark @ClassMark @RuntimeMark class Sample {}
        """;
    Files.writeString(sourceRoot.resolve("RetentionKinds.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = result.aggregate().orElseThrow();
    var sample = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION
        && node.value(SyntaxValue.TypeDeclaration.class).name().equals("Sample")).findFirst().orElseThrow();
    var occurrences = sample.children(SyntaxRole.ANNOTATION);

    // Assert
    assertEquals(List.of("@SourceMark", "@ClassMark", "@RuntimeMark"), occurrences.stream().map(node -> {
      var range = node.origin().orElseThrow();
      return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
    }).toList());
    assertTrue(occurrences.stream().allMatch(node -> node.semanticReference().orElseThrow().status() == ResolutionStatus.RESOLVED));
    var annotationTypes = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION)
        .map(node -> node.value(SyntaxValue.TypeDeclaration.class))
        .collect(Collectors.toMap(SyntaxValue.TypeDeclaration::name, SyntaxValue.TypeDeclaration::declarationKey));
    assertEquals(List.of("SourceMark", "ClassMark", "RuntimeMark").stream().map(annotationTypes::get).toList(),
        occurrences.stream().map(node -> node.semanticReference().orElseThrow().symbolKey().orElseThrow()).toList());
    assertEquals(3, aggregate.semanticEdges().stream().filter(edge -> edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
        && targetsDeclaration(edge.target(), sample.nodeId())).count());
    assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
  }

  @Test
  void givenTwoAnnotationsWithoutRanges_whenCompletenessChecks_thenEachKeepsDistinctSourceContext() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Missing.java"), "@A @A class Missing {}\n");
    var snapshot = new JavaSourceLoader().discover(sourceRoot);
    var classification = JavaCompilerKindCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25))
        .classificationOf(new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, "ANNOTATION")).orElseThrow();
    var first = new LanguageFeatureObservation(snapshot.sourceFiles().getFirst().sourceFileKey(), classification, List.of(),
        Optional.empty());
    var second = new LanguageFeatureObservation(snapshot.sourceFiles().getFirst().sourceFileKey(), classification, List.of(),
        Optional.empty());

    // Act
    var diagnostics = JavaTypeUseCompleteness.diagnostics(snapshot, List.of(first, second), Optional.empty());

    // Assert
    assertEquals(2, diagnostics.size());
    assertEquals(List.of("frontend.unavailable-source-annotation-range", "frontend.unavailable-source-annotation-range"),
        diagnostics.stream().map(diagnostic -> diagnostic.code()).toList());
    assertTrue(diagnostics.stream().allMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
    assertTrue(!diagnostics.getFirst().diagnosticKey().equals(diagnostics.getLast().diagnosticKey()));
    assertTrue(diagnostics.getFirst().message().contains("Missing.java at ANNOTATION occurrence 1"));
    assertTrue(diagnostics.getLast().message().contains("Missing.java at ANNOTATION occurrence 2"));
    assertTrue(diagnostics.stream().allMatch(diagnostic -> diagnostic.origins().isEmpty()));
  }

  private static String annotationExcerpt(String source, SyntaxNode annotation) {
    var range = annotation.origin().orElseThrow();
    return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
  }

  private static boolean targetsDeclaration(SyntaxSemanticTarget target, String key) {
    return target instanceof SyntaxSemanticTarget.AnnotationApplication application
        && application.declarationNodeKey().equals(Optional.of(key));
  }

  private record AppliedAnnotation(SyntaxNode owner, SyntaxNode annotation) {
  }
}
