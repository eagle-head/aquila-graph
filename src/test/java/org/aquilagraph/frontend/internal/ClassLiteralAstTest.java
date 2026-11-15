package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.aquilagraph.source.admission.AstProductionAdmission;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceOccurrence;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.SyntaxRole;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ClassLiteralAstTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenWrittenClassLiteralVariants_whenProducedAndDecoded_thenCompleteTypesAndCanonicalOperandsSurvive() {
    return Stream.of("byte", "short", "int", "long", "char", "float", "double", "boolean", "void",
        "String", "java.lang.String", "int[][]", "String[]", "Example.Inner")
        .map(type -> DynamicTest.dynamicTest(type + ".class", () -> {
          // Arrange
          var source = "class Example { static class Inner {} Class<?> subject() { return " + type + ".class; } }";

          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
          assertEquals(original.aggregate(), retained.aggregate());
          assertEquals(original.coverage(), retained.coverage());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
          var literal = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL)
              .findFirst().orElseThrow();
          assertFalse(literal.semanticReference().isPresent());
          var boxed = Map.of("byte", "java.lang.Byte", "short", "java.lang.Short", "int", "java.lang.Integer",
              "long", "java.lang.Long", "char", "java.lang.Character", "float", "java.lang.Float",
              "double", "java.lang.Double", "boolean", "java.lang.Boolean", "void", "java.lang.Void");
          var operandType = boxed.getOrDefault(type, type.equals("String") ? "java.lang.String"
              : type.equals("String[]") ? "java.lang.String[]" : type);
          assertEquals("java.lang.Class<" + operandType + ">", literal.resolvedType().orElseThrow().displayName());
          assertEquals(List.of(SyntaxRole.TYPE_OPERAND), literal.children().stream().map(child -> child.role()).toList());
          var operand = literal.requiredChild(SyntaxRole.TYPE_OPERAND);
          assertEquals(SyntaxKind.TYPE_USE, operand.syntaxKind());
          var origin = operand.origin().orElseThrow();
          assertEquals(type, source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive())));
          assertSame(operand, retained.node(new SourceOccurrence(retained.identity(), operand.nodeId())).orElseThrow());
          assertTrue(retained.coverage().validationEvidence().unmatchedFacts().isEmpty());
          var method = retained.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
          var cfg = CfgDerivers.standard().derive(retained,
              new CfgRequest(retained.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
          assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(retained));
        }));
  }

  @Test
  void givenNestedClassLiterals_whenCfgIsRequested_thenRefusalPreservesCompleteSourceAndBoundedQualification() throws IOException {
    // Arrange
    var source = """
        import java.util.function.Supplier;
        class Example {
          Class<?> subject(boolean selected) {
            Class<?>[] values = {int[].class, void.class};
            Supplier<Class<?>> deferred = () -> String.class;
            return selected ? values[0] : deferred.get();
          }
        }
        """;
    var expanded = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var bounded = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);
    var method = expanded.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var before = AstCodec.encode(expanded);

    // Act
    var cfg = CfgDerivers.standard().derive(expanded,
        new CfgRequest(expanded.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, expanded.completion(), expanded.diagnostics().toString());
    assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
    assertArrayEquals(before, AstCodec.encode(expanded));
    assertEquals(3, expanded.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL).count());
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
  }

  @Test
  void givenSuppliedMissingClassLiteralType_whenAdmitted_thenIncompleteSourceCannotAcquireRetention() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "class Example { Class<?> subject() { return int[].class; } }")));
    var original = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var captured = SourceValueCapture.capture(original.aggregate().orElseThrow());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
        missingLiteralType(source.root(), replacements), source.nodes().stream().map(node -> missingLiteralType(node, replacements)).toList(),
        source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), missingLiteralType(body.root(), replacements),
        body.origin(), List.of(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var supplied = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(),
        captured.associations(), captured.declarationTypes(), captured.scopes()));
    var input = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(supplied),
        JavaCoverageTestSupport.emissions(original));

    // Act
    var outcome = AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
    var retained = AstCodec.decode(AstCodec.encode(outcome.result().orElseThrow()), outcome.result().orElseThrow().identity());
    var publication = AnalysisCapabilities.astRetention(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(new AstPublicationRequest(outcome,
        PublicationIntent.SAVE_COMPLETE_AST));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
    assertEquals(AstCompletion.INCOMPLETE, retained.completion());
    assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
    var literals = retained.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL).toList();
    assertEquals(1, literals.size());
    assertEquals(SyntaxType.Unresolved.INSTANCE, literals.getFirst().type());
  }

  @Test
  void givenContradictoryClassLiteralValueType_whenConstructed_thenIntrinsicSourceAdmissionRejectsIt() throws IOException {
    // Arrange
    var result = produce("class Example { Class<?> subject() { return int.class; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var literal = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL).findFirst().orElseThrow();
    var wrongType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(literal.sourceFileKey(), literal.structuralPath(),
        literal.sourceKind(), literal.syntaxKind(), wrongType, literal.value(), literal.children(), literal.semanticReference(), literal.origin().orElseThrow()));

    // Assert
    assertEquals("A class literal value requires a java.lang.Class type", failure.getMessage());
    assertEquals(AstCompletion.COMPLETE, result.completion());
  }

  @Test
  void givenContradictoryClassLiteralArgument_whenConstructed_thenTheKnownTypeOperandMismatchIsRejected() throws IOException {
    // Arrange
    var original = produce("class Example { Class<?> subject() { return int.class; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var alternate = produce("class Example { Class<?> subject() { return String.class; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var literal = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL).findFirst().orElseThrow();
    var wrongType = alternate.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CLASS_LITERAL).findFirst().orElseThrow().type();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(literal.sourceFileKey(), literal.structuralPath(),
        literal.sourceKind(), literal.syntaxKind(), wrongType, literal.value(), literal.children(), literal.semanticReference(), literal.origin().orElseThrow()));

    // Assert
    assertEquals("A known class literal type argument must match its written operand", failure.getMessage());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @Test
  void givenIllegalTypeVariableClassLiteral_whenProduced_thenActualCompilerErrorsPreventCompletion() throws IOException {
    // Arrange
    var source = "class Example<T> { Class<?> subject() { return T.class; } }";

    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")));
  }

  private static SyntaxNode missingLiteralType(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements) {
    var existing = replacements.get(node);
    if (existing != null) {
      return existing;
    }
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), missingLiteralType(child.node(), replacements))).toList();
    var type = node.syntaxKind() == SyntaxKind.CLASS_LITERAL ? SyntaxType.Unresolved.INSTANCE : node.type();
    var copied = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(), children, node.semanticReference());
    replacements.put(node, copied);
    return copied;
  }
}
