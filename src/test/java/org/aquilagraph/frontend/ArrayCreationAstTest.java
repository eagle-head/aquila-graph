package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ArrayCreationAstTest {
  @TempDir Path directory;

  @Test
  void givenArrayForms_whenProducedAndDecoded_thenDimensionsTypesAndInitializerPresenceSurviveClosure() throws IOException {
    // Arrange
    var source = """
        class Example {
          int[][] subject(int n, int m) {
            int[] single = new int[n];
            int[][] sized = new int[n][m + 1];
            int[][] omitted = new int[n][];
            int[] empty = new int[]{};
            int[][] nested = new int[][]{{1}, {2, 3}, {}};
            int[][] local = {{4}, {}};
            String[] names = new String[]{"one", "two"};
            return nested;
          }
        }
        """;

    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.coverage(), decoded.coverage());
    assertEquals(AstCodec.materialize(original), AstCodec.materialize(decoded));
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    var single = initializer(decoded, "single");
    assertEquals(new SyntaxValue.ArrayCreation(true, false, List.of(0)), single.value());
    assertEquals("n", single.children(SyntaxRole.DIMENSION).getFirst().value(SyntaxValue.SourceSymbol.class).name());
    assertEquals(JavaSourceKind.PRIMITIVE_TYPE, single.requiredChild(SyntaxRole.TYPE_OPERAND).sourceKind());
    var sized = initializer(decoded, "sized");
    assertEquals(2, sized.children(SyntaxRole.DIMENSION).size());
    assertEquals(SyntaxKind.BINARY, sized.children(SyntaxRole.DIMENSION).get(1).syntaxKind());
    var omitted = initializer(decoded, "omitted");
    assertEquals(1, omitted.children(SyntaxRole.DIMENSION).size());
    assertEquals(JavaSourceKind.ARRAY_TYPE, omitted.requiredChild(SyntaxRole.TYPE_OPERAND).sourceKind());
    var empty = initializer(decoded, "empty");
    assertEquals(new SyntaxValue.ArrayCreation(true, true, List.of(0)), empty.value());
    assertTrue(empty.children(SyntaxRole.ARRAY_ELEMENT).isEmpty());
    assertFalse(single.value(SyntaxValue.ArrayCreation.class).initializer());
    var nested = initializer(decoded, "nested");
    assertEquals(JavaSourceKind.ARRAY_TYPE, nested.requiredChild(SyntaxRole.TYPE_OPERAND).sourceKind());
    assertEquals(List.of(1, 2, 0), nested.children(SyntaxRole.ARRAY_ELEMENT).stream().map(node -> node.children(SyntaxRole.ARRAY_ELEMENT).size()).toList());
    assertEquals(List.of("2", "3"), nested.children(SyntaxRole.ARRAY_ELEMENT).get(1).children(SyntaxRole.ARRAY_ELEMENT).stream()
        .map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
    var local = initializer(decoded, "local");
    assertEquals(new SyntaxValue.ArrayCreation(false, true, List.of()), local.value());
    assertTrue(local.optionalChild(SyntaxRole.TYPE_OPERAND).isEmpty());
    assertEquals(List.of("one", "two"), initializer(decoded, "names").children(SyntaxRole.ARRAY_ELEMENT).stream()
        .map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
    assertTrue(body(decoded).stream().filter(node -> node.syntaxKind() == SyntaxKind.ARRAY_CREATION).allMatch(node -> node.origin().isPresent()));
  }

  @Test
  void givenAnnotatedArrays_whenProduced_thenBaseSizedAndOmittedDimensionAnnotationsRemainAtTheirOwners() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
        class Example {
          int[][] subject(int n) {
            int[][] sized = new @Mark(1) int @Mark(2)[n] @Mark(3)[];
            int[][] initialized = new @Mark(4) int @Mark(5)[] @Mark(6)[]{{7}};
            return initialized;
          }
        }
        """;

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics() + " failed contexts: "
        + result.coverage().validationEvidence().outcomes().stream()
            .filter(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED)
            .map(outcome -> outcome.address() + " expected " + outcome.encounteredValue()).toList());
    var sized = initializer(decoded, "sized");
    assertEquals(new SyntaxValue.ArrayCreation(true, false, List.of(1)), sized.value());
    assertEquals("@Mark(2)", text(decoded, sized.children(SyntaxRole.ANNOTATION).getFirst()));
    var type = sized.requiredChild(SyntaxRole.TYPE_OPERAND);
    assertEquals(List.of("@Mark(3)", "@Mark(1)"), type.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .map(node -> text(decoded, node)).toList());
    var initialized = initializer(decoded, "initialized");
    assertEquals(new SyntaxValue.ArrayCreation(true, true, List.of(1)), initialized.value());
    assertEquals("@Mark(5)", text(decoded, initialized.children(SyntaxRole.ANNOTATION).getFirst()));
    assertEquals(List.of("@Mark(6)", "@Mark(4)"), initialized.requiredChild(SyntaxRole.TYPE_OPERAND).descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION).map(node -> text(decoded, node)).toList());
    assertEquals(List.of("@Mark(1)", "@Mark(2)", "@Mark(3)", "@Mark(4)", "@Mark(5)", "@Mark(6)"), body(decoded).stream().filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION)
        .map(node -> text(decoded, node)).sorted().toList());
  }

  @Test
  void givenCompleteArrays_whenCfgIsRequestedAndBoundedProfileIsCompared_thenSourceRemainsCompleteAndBoundedRemainsIncomplete() throws IOException {
    // Arrange
    var source = "class Example { int[] subject(int n) { return new int[n]; } }";
    var expanded = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var bounded = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var bytes = AstCodec.encode(expanded);
    var method = expanded.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();

    // Act
    var cfg = CfgDerivers.standard().derive(expanded, new CfgRequest(expanded.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, expanded.completion(), expanded.diagnostics().toString());
    assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
    assertArrayEquals(bytes, AstCodec.encode(expanded));
  }

  @Test
  void givenMultipleAnnotatedDimensionsAndGenericBase_whenProduced_thenEachGroupAndEffectfulExpressionHasOneOrderedOccurrence() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
        class Example {
          int next() { return 2; }
          int[][][][] subject(Integer n) {
            int[][][][] array = new int @Mark(1)[n++] @Mark(2)[next()] @Mark(3)[] @Mark(4)[];
            java.util.List<?>[] lists = new java.util.List<?>[n];
            return array;
          }
        }
        """;

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var array = initializer(decoded, "array");
    assertEquals(new SyntaxValue.ArrayCreation(true, false, List.of(1, 1)), array.value());
    assertEquals(List.of(SyntaxKind.UPDATE, SyntaxKind.INVOCATION), array.children(SyntaxRole.DIMENSION).stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(List.of("@Mark(1)", "@Mark(2)"), array.children(SyntaxRole.ANNOTATION).stream().map(node -> text(decoded, node)).toList());
    var locations = array.children(SyntaxRole.ANNOTATION).stream().map(annotation -> decoded.aggregate().semanticEdges().stream()
        .filter(edge -> edge.sourceKey().equals(annotation.nodeId()) && edge.target() instanceof SyntaxSemanticTarget.AnnotationApplication)
        .map(edge -> ((SyntaxSemanticTarget.AnnotationApplication) edge.target()).typeLocation().orElseThrow()).findFirst().orElseThrow()).toList();
    assertEquals(List.of(new SyntaxSemanticTarget.AnnotationTypeLocation.ArrayDimension(array.nodeId(), 0),
        new SyntaxSemanticTarget.AnnotationTypeLocation.ArrayDimension(array.nodeId(), 1)), locations);
    assertEquals(List.of("@Mark(3)", "@Mark(4)"), array.requiredChild(SyntaxRole.TYPE_OPERAND).descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION).map(node -> text(decoded, node)).toList());
    assertEquals(1, array.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.UPDATE).count());
    assertEquals(1, array.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).count());
    assertEquals(JavaSourceKind.PARAMETERIZED_TYPE, initializer(decoded, "lists").requiredChild(SyntaxRole.TYPE_OPERAND).sourceKind());
    assertEquals(JavaSourceKind.UNBOUNDED_WILDCARD, initializer(decoded, "lists").requiredChild(SyntaxRole.TYPE_OPERAND)
        .children(SyntaxRole.TYPE_ARGUMENT).getFirst().sourceKind());
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidArrayInputs_whenProduced_thenActualCompilerErrorsPreventCompleteQualification() {
    return Stream.of("new int[1]{2}", "new int[]{\"wrong\"}", "new int[1L]", "new Missing[1]", "new int[]")
        .map(expression -> DynamicTest.dynamicTest(expression, () -> {
          // Arrange
          var source = "class Example { Object subject() { return " + expression + "; } }";
          // Act
          var outcome = new JavaFrontend(new JavaSourceLoader()).produce(
              InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source))),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertTrue(outcome.completion() != AstCompletion.COMPLETE);
          assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()
              && diagnostic.stage() == DiagnosticStage.FRONTEND && diagnostic.code().startsWith("compiler.err.")), outcome.diagnostics().toString());
        }));
  }

  @Test
  void givenSuppliedMissingArrayType_whenAdmitted_thenClosedSourceIsIncompleteAndRetentionDoesNotAcquireARepository() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { int[] subject(int n) { return new int[n]; } }")));
    var original = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var captured = CopiedAstValues.capture(original.aggregate().orElseThrow());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
        missingArrayType(source.root(), replacements), source.nodes().stream().map(node -> missingArrayType(node, replacements)).toList(),
        source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), missingArrayType(body.root(), replacements),
        body.origin(), List.of(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var supplied = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(),
        captured.associations(), captured.declarationTypes()));
    var input = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(supplied),
        JavaCoverageTestSupport.diagnostics(original, "copied-array-type-qualification"));

    // Act
    var outcome = AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
    var result = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var publication = new AstRetentionService(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(outcome);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(SyntaxType.Unresolved.INSTANCE, body(decoded).stream().filter(node -> node.syntaxKind() == SyntaxKind.ARRAY_CREATION).findFirst().orElseThrow().type());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
  }

  @Test
  void givenContradictoryArrayTypesAndForms_whenConstructed_thenIntrinsicSourceAdmissionRejectsThem() throws IOException {
    // Arrange
    var result = produce("class Example { int[] subject(int n) { int[] a=new int[n]; int[][] b=new int[n][]; return a; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var array = initializer(result, "a");
    var other = initializer(result, "b");
    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> copyArray(array, other.type(), array.value(), array.children()));
    assertThrows(IllegalArgumentException.class, () -> copyArray(array, array.requiredChild(SyntaxRole.TYPE_OPERAND).type(), array.value(), array.children()));
    assertThrows(IllegalArgumentException.class, () -> copyArray(array, SyntaxType.NotApplicable.INSTANCE, array.value(), array.children()));
    assertThrows(IllegalArgumentException.class, () -> copyArray(array, array.type(), new SyntaxValue.ArrayCreation(true, true, List.of(0)), array.children()));
    assertThrows(IllegalArgumentException.class, () -> new SyntaxValue.ArrayCreation(false, false, List.of()));
  }

  private static SyntaxNode copyArray(SyntaxNode node, SyntaxType type, SyntaxValue value, List<SyntaxChild> children) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, value, children,
        node.semanticReference(), node.origin().orElseThrow());
  }

  private static SyntaxNode missingArrayType(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements) {
    var previous = replacements.get(node);
    if (previous != null) {
      return previous;
    }
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), missingArrayType(child.node(), replacements))).toList();
    var type = node.syntaxKind() == SyntaxKind.ARRAY_CREATION ? SyntaxType.Unresolved.INSTANCE : node.type();
    var copied = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference());
    replacements.put(node, copied);
    return copied;
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
  }

  private static List<SyntaxNode> body(AstResult result) {
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    return result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
  }

  private static SyntaxNode initializer(AstResult result, String name) {
    return body(result).stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local && local.name().equals(name))
        .findFirst().orElseThrow().requiredChild(SyntaxRole.INITIALIZER);
  }

  private static String text(AstResult result, SyntaxNode node) {
    var span = node.origin().orElseThrow();
    var file = result.snapshot().sourceFiles().stream().filter(source -> source.sourceFileKey().equals(span.sourceFileKey())).findFirst().orElseThrow();
    return file.content().substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()));
  }
}
