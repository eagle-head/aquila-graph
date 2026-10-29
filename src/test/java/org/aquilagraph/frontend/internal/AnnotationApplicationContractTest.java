package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationTypeLocation;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class AnnotationApplicationContractTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenAnnotatedNeighborBindings_whenProducedAndDecoded_thenExactDeclarationAndTypeEndpointsRemainCanonical() {
    // Arrange
    var variants = bindings();

    // Act
    var observations = variants.stream().map(variant -> DynamicTest.dynamicTest(
        "given" + variant.name() + "_whenProducedAndDecoded_thenExactDeclarationAndTypeEndpointsRemainCanonical", () -> {
          // Arrange
          var source = source(variant);

          // Act
          var results = produceAndDecode(source);

          // Assert
          for (var result : results) {
            var aggregate = result.aggregate();
            var first = binding(aggregate, "first");
            var second = binding(aggregate, "second");
            assertNotEquals(first.nodeId(), second.nodeId());
            assertNotEquals(first.requiredReference().symbolKey(), second.requiredReference().symbolKey());
            assertEquals(variant.context(), first.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().context());
            assertEquals(variant.context(), second.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().context());
            for (var owner : List.of(first, second)) {
              var type = owner.requiredChild(SyntaxRole.DECLARED_TYPE);
              var annotations = owner.children(SyntaxRole.ANNOTATION);
              assertEquals(3, annotations.size());
              assertEquals(List.of("Declaration", "Use", "Both").stream()
                  .map(name -> new JavaTypeDeclarationIdentity(Optional.empty(), name).declarationKey()).toList(),
                  annotations.stream().map(node -> node.requiredReference().symbolKey().orElseThrow()).toList());
              for (var ordinal = 0; ordinal < annotations.size(); ordinal++) {
                var annotation = annotations.get(ordinal);
                var application = application(aggregate, annotation);
                assertEquals(owner.nodeId(), application.ownerNodeKey());
                assertEquals(ordinal == 1 ? Optional.empty() : Optional.of(owner.nodeId()), application.declarationNodeKey());
                assertEquals(ordinal == 0 ? Optional.empty() : Optional.of(new AnnotationTypeLocation.Written(type.nodeId())),
                    application.typeLocation());
                assertEquals(List.of(), application.propagatedCounterparts());
                assertEquals(List.of(), application.fieldDeclarators());
                assertEquals(List.of(SyntaxValue.AnnotationLocation.DECLARATION, SyntaxValue.AnnotationLocation.TYPE_USE,
                    SyntaxValue.AnnotationLocation.DUAL).get(ordinal), annotation.value(SyntaxValue.Annotation.class).location());
                assertEquals(owner.structuralPath().child(SyntaxRole.ANNOTATION, ordinal), annotation.structuralPath());
                var spelling = List.of("@Declaration", "@Use", "@Both").get(ordinal);
                var declarationStart = Math.toIntExact(owner.origin().orElseThrow().startOffset());
                var start = source.indexOf(spelling, declarationStart);
                assertEquals(new SourceSpan(owner.sourceFileKey(), start, start + spelling.length()), annotation.origin().orElseThrow());
                assertEquals(1, aggregate.nodes().stream().filter(node -> node.nodeId().equals(annotation.nodeId())).count());
              }
            }
          }
        }));

    // Assert
    return observations;
  }

  @TestFactory
  Stream<DynamicTest> givenAnnotatedPatternNeighbors_whenSuppliedEndpointSelectsNeighbor_thenIntrinsicAdmissionRejectsExactMismatch() {
    // Arrange
    var variants = bindings().stream().filter(variant -> variant.context() == LocalBindingEvidence.Context.PATTERN);

    // Act
    var observations = variants.map(variant -> DynamicTest.dynamicTest(
        "given" + variant.name() + "_whenSuppliedEndpointSelectsNeighbor_thenIntrinsicAdmissionRejectsExactMismatch", () -> {
          // Arrange
          var results = produceAndDecode(source(variant));

          // Act
          for (var result : results) {
            var aggregate = result.aggregate();
            var copied = SourceValueCapture.capture(aggregate);
            assertEquals(aggregate, AstValueAdmission.admit(copied));
            var first = binding(aggregate, "first").children(SyntaxRole.ANNOTATION).getLast();
            var original = application(aggregate, first);
            var neighbor = application(aggregate, binding(aggregate, "second").children(SyntaxRole.ANNOTATION).getLast());
            var contradictions = List.of(
                new AnnotationApplication(neighbor.ownerNodeKey(), original.declarationNodeKey(), original.typeLocation(), List.of(), List.of()),
                new AnnotationApplication(original.ownerNodeKey(), neighbor.declarationNodeKey(), original.typeLocation(), List.of(), List.of()),
                new AnnotationApplication(original.ownerNodeKey(), original.declarationNodeKey(), neighbor.typeLocation(), List.of(), List.of()),
                neighbor);
            for (var contradiction : contradictions) {
              var edges = aggregate.semanticEdges().stream().map(edge -> edge.sourceKey().equals(first.nodeId())
                  && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET
                      ? new SyntaxSemanticEdge(first.nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET, contradiction) : edge).toList();
              var root = aggregate.roots().getFirst();
              var supplied = new SyntaxSourceContribution(root.sourceFileKey(), root, aggregate.nodes(), aggregate.structuralEdges(), edges);
              var values = new CopiedAstValues(copied.snapshot(), List.of(supplied), copied.bodies(), copied.memberOrders(),
                  copied.associations(), copied.declarationTypes(), copied.scopes());
              var failure = assertThrows(IllegalArgumentException.class,
                  () -> AstValueAdmission.admit(values));

              // Assert
              assertEquals("AST annotation application does not match its exact source location: " + first.nodeId(), failure.getMessage());
            }
          }
        }));

    // Assert
    return observations;
  }

  private List<AstResult> produceAndDecode(String source) {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Sample.java"), source)));
    var outcome = JavaFrontends.standard().produce(new AstProductionRequest(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
    var original = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    assertEquals(AstCompletion.COMPLETE, decoded.completion());
    assertEquals(AstProfile.JAVA_SOURCE_MODULE_SERVICES, decoded.profile());
    assertTrue(decoded.diagnostics().isEmpty(), decoded.diagnostics().toString());
    return List.of(original, decoded);
  }

  private static SyntaxNode binding(JavaAstAggregate aggregate, String name) {
    var matches = aggregate.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration value
        && value.name().equals(name)).toList();
    assertEquals(1, matches.size(), name);
    return matches.getFirst();
  }

  private static AnnotationApplication application(JavaAstAggregate aggregate, SyntaxNode annotation) {
    var edges = aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(annotation.nodeId())
        && edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET).toList();
    assertEquals(1, edges.size());
    return assertInstanceOf(AnnotationApplication.class, edges.getFirst().target());
  }

  private static String source(BindingCase variant) {
    var target = variant.context() == LocalBindingEvidence.Context.LAMBDA || variant.context() == LocalBindingEvidence.Context.CATCH
        ? "PARAMETER" : "LOCAL_VARIABLE";
    return """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.io.StringReader;
        import java.util.List;
        import java.util.function.ToIntBiFunction;
        @Target(ElementType.%s) @interface Declaration {}
        @Target(ElementType.TYPE_USE) @interface Use {}
        @Target({ElementType.%s, ElementType.TYPE_USE}) @interface Both {}
        record Pair(String left, String right) {}
        class Sample { int subject(Object left, Object right) { %s } }
        """.formatted(target, target, variant.body().replace("ANNOTATIONS", "@Declaration @Use @Both"));
  }

  private static List<BindingCase> bindings() {
    return List.of(
        new BindingCase("InstancePatterns", LocalBindingEvidence.Context.PATTERN,
            "if (left instanceof ANNOTATIONS String first && right instanceof ANNOTATIONS String second) "
                + "{ return first.length() + second.length(); } return 0;"),
        new BindingCase("RecordPatterns", LocalBindingEvidence.Context.PATTERN,
            "if (left instanceof Pair(ANNOTATIONS String first, ANNOTATIONS String second)) "
                + "{ return first.length() + second.length(); } return 0;"),
        new BindingCase("LambdaParameters", LocalBindingEvidence.Context.LAMBDA,
            "ToIntBiFunction<String, String> fn = (ANNOTATIONS String first, ANNOTATIONS String second) "
                + "-> first.length() + second.length(); return fn.applyAsInt(\"\", \"\");"),
        new BindingCase("EnhancedForVariables", LocalBindingEvidence.Context.ENHANCED_FOR,
            "for (ANNOTATIONS String first : List.of(\"a\")) { for (ANNOTATIONS String second : List.of(\"b\")) "
                + "{ return first.length() + second.length(); } } return 0;"),
        new BindingCase("ResourceVariables", LocalBindingEvidence.Context.RESOURCE,
            "try (ANNOTATIONS StringReader first = new StringReader(\"a\"); "
                + "ANNOTATIONS StringReader second = new StringReader(\"b\")) { return first.hashCode() + second.hashCode(); }"),
        new BindingCase("CatchParameters", LocalBindingEvidence.Context.CATCH,
            "try { return left.hashCode(); } catch (ANNOTATIONS IllegalArgumentException first) { return first.hashCode(); } "
                + "catch (ANNOTATIONS IllegalStateException second) { return second.hashCode(); }"));
  }

  private record BindingCase(String name, LocalBindingEvidence.Context context, String body) {}
}
