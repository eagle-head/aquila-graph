package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AnalysisComputationRequest;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.aquilagraph.source.admission.AstProductionAdmission;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.application.PublicationStatus;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class MemberReferenceAstTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenLegalReferenceForms_whenProducedAndDecoded_thenWrittenStructureAndApplicableSelectionSurviveClosure() {
    return Stream.of(
        new Variant("java.util.function.IntSupplier", "text::length", "String text", "", SyntaxValue.ReferenceQualifier.EXPRESSION, "length", false, 0),
        new Variant("java.util.function.ToIntFunction<String>", "String::length", "", "", SyntaxValue.ReferenceQualifier.TYPE, "length", false, 0),
        new Variant("java.util.function.IntFunction<String>", "String::valueOf", "", "", SyntaxValue.ReferenceQualifier.TYPE, "valueOf", true, 0),
        new Variant("java.util.function.Supplier<String>", "super::toString", "", "", SyntaxValue.ReferenceQualifier.SUPER, "toString", false, 0),
        new Variant("java.util.function.Supplier<String>", "this::toString", "", "", SyntaxValue.ReferenceQualifier.EXPRESSION, "toString", false, 0),
        new Variant("java.util.function.Function<String,String>", "Example::<String>identity", "", "static <T> T identity(T value) { return value; }", SyntaxValue.ReferenceQualifier.TYPE, "identity", true, 1),
        new Variant("java.util.function.Function<String,String>", "values::selected", "Parent<String> values", "static class Parent<T> { T selected(T value) { return value; } }", SyntaxValue.ReferenceQualifier.EXPRESSION, "selected", false, 0),
        new Variant("java.util.function.Supplier<java.util.ArrayList<String>>", "java.util.ArrayList<String>::new", "", "", SyntaxValue.ReferenceQualifier.TYPE, "new", false, 0),
        new Variant("java.util.function.IntFunction<int[]>", "int[]::new", "", "", SyntaxValue.ReferenceQualifier.ARRAY_TYPE, "new", false, 0),
        new Variant("java.util.function.Function<int[],int[]>", "int[]::clone", "", "", SyntaxValue.ReferenceQualifier.TYPE, "clone", false, 0),
        new Variant("java.util.function.Supplier<int[]>", "values::clone", "int[] values", "", SyntaxValue.ReferenceQualifier.EXPRESSION, "clone", false, 0),
        new Variant("java.util.function.Function<int[],String>", "int[]::toString", "", "", SyntaxValue.ReferenceQualifier.TYPE, "toString", false, 0),
        new Variant("<T extends Runnable> java.util.function.Consumer<T>", "T::run", "", "", SyntaxValue.ReferenceQualifier.TYPE, "run", false, 0),
        new Variant("java.util.function.IntFunction<String[]>", "String[]::new", "", "", SyntaxValue.ReferenceQualifier.ARRAY_TYPE, "new", false, 0),
        new Variant("java.util.function.IntFunction<int[][]>", "int[][]::new", "", "", SyntaxValue.ReferenceQualifier.ARRAY_TYPE, "new", false, 0),
        new Variant("java.util.function.IntFunction<String[]>", "String @Mark []::new", "", "@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Mark {}", SyntaxValue.ReferenceQualifier.ARRAY_TYPE, "new", false, 0),
        new Variant("java.util.function.Function<String[],String[]>", "String @Mark []::clone", "", "@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Mark {}", SyntaxValue.ReferenceQualifier.TYPE, "clone", false, 0),
        new Variant("java.util.function.Supplier<Example>", "Example::<String>new", "", "<T> Example() {}", SyntaxValue.ReferenceQualifier.TYPE, "new", false, 1),
        new Variant("java.util.function.Supplier<Constructed>", "Constructed::new", "", "record Constructed(String value) { Constructed { } Constructed() { this(\"value\"); } }", SyntaxValue.ReferenceQualifier.TYPE, "new", false, 0),
        new Variant("java.util.function.Function<String,Constructed>", "Constructed::new", "", "record Constructed(String value) { Constructed { } }", SyntaxValue.ReferenceQualifier.TYPE, "new", false, 0),
        new Variant("java.util.function.Function<String,String>", "Example::<String>identity", "", "static <T> T identity(T value) { return value; }", SyntaxValue.ReferenceQualifier.TYPE, "identity", true, 1),
        new Variant("java.util.function.IntSupplier", "(text.trim())::length", "String text", "", SyntaxValue.ReferenceQualifier.EXPRESSION, "length", false, 0))
        .map(variant -> DynamicTest.dynamicTest(variant.expression(), () -> {
          // Arrange
          var source = "class Example { " + variant.members() + " " + variant.target() + " subject(" + variant.parameters()
              + ") { return " + variant.expression() + "; } }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(original.aggregate(), result.aggregate());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(result));
          var reference = references(result).getFirst();
          var value = reference.value(SyntaxValue.MemberReference.class);
          assertEquals(variant.qualifier(), value.qualifier());
          assertEquals(variant.name(), value.name());
          assertEquals(variant.staticMember(), value.staticMember());
          assertEquals(variant.typeArguments(), reference.children(SyntaxRole.TYPE_ARGUMENT).size());
          assertEquals(variant.name().equals("new") ? SyntaxValue.ReferenceMode.CONSTRUCTOR : SyntaxValue.ReferenceMode.METHOD, value.mode());
          var qualifier = reference.requiredChild(SyntaxRole.QUALIFIER);
          var range = qualifier.origin().orElseThrow();
          assertEquals(variant.expression().substring(0, variant.expression().indexOf("::")),
              source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive())));
          assertTrue(reference.type() instanceof SyntaxType.Resolved);
          assertEquals(variant.qualifier() == SyntaxValue.ReferenceQualifier.ARRAY_TYPE ? JavaTypeCategory.ARRAY : JavaTypeCategory.EXECUTABLE,
              ((SyntaxType.Resolved) value.selectedType()).identity().category());
          assertEquals(variant.qualifier() != SyntaxValue.ReferenceQualifier.ARRAY_TYPE && !variant.name().equals("clone"), reference.semanticReference().isPresent());
          var propertyOutcomes = result.coverage().validationEvidence().outcomes().stream()
              .filter(outcome -> outcome.occurrence().map(occurrence -> occurrence.classification().feature().identifier().startsWith("MemberReferenceTree::")).orElse(false))
              .toList();
          var expectedAccessors = Set.of("MemberReferenceTree::Tree#getKind()", "MemberReferenceTree::MemberReferenceTree#getMode()",
              "MemberReferenceTree::MemberReferenceTree#getName()", "MemberReferenceTree::MemberReferenceTree#getQualifierExpression()",
              "MemberReferenceTree::MemberReferenceTree#getTypeArguments()");
          assertEquals(5, propertyOutcomes.size());
          assertEquals(expectedAccessors, propertyOutcomes.stream().map(outcome -> outcome.address().role()).collect(Collectors.toSet()));
          assertEquals(expectedAccessors, result.coverage().validationEvidence().writtenInventory().contexts().stream()
              .filter(context -> context.address().role().startsWith("MemberReferenceTree::")).map(context -> context.address().role())
              .collect(Collectors.toSet()));
          for (var property : propertyOutcomes) {
            if (property.address().role().equals("MemberReferenceTree::MemberReferenceTree#getTypeArguments()") && variant.typeArguments() == 0) {
              assertEquals(FeatureContextOutcome.Mode.NEGATIVE, property.support().mode());
              assertEquals(State.ABSENT, property.support().negativeState().orElseThrow().state());
              assertEquals(List.of(), property.support().negativeState().orElseThrow().items());
            } else assertEquals(FeatureContextOutcome.Mode.FACTS, property.support().mode());
          }
          assertTrue(AstCodec.materialize(result).nodes().stream().anyMatch(node -> node.tag().equals("syntax-value/member-reference")));
        }));
  }

  @Test
  void givenQualifiedSuperAndNestedReferenceArguments_whenProduced_thenRealSelectionsAndWrittenQualifiersAreRetained() throws IOException {
    // Arrange
    var source = "interface Named { default String name() { return \"named\"; } } class Example implements Named { "
        + "static void consume(java.util.function.IntFunction<int[]> value) {} "
        + "void subject() { consume(int[]::new); } "
        + "java.util.function.Supplier<String> named() { return Named.super::name; } "
        + "class Inner { java.util.function.Supplier<String> outer() { return Example.super::toString; } } }";
    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(3, references(result).size());
    assertEquals(2, references(result).stream().filter(node -> node.value(SyntaxValue.MemberReference.class).qualifier() == SyntaxValue.ReferenceQualifier.QUALIFIED_SUPER).count());
    assertTrue(references(result).stream().filter(node -> node.value(SyntaxValue.MemberReference.class).qualifier() == SyntaxValue.ReferenceQualifier.QUALIFIED_SUPER)
        .allMatch(node -> node.requiredChild(SyntaxRole.QUALIFIER).requiredChild(SyntaxRole.QUALIFIER).syntaxKind() == SyntaxKind.TYPE_USE));
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidReferenceTargets_whenProduced_thenActualCompilerDiagnosticsRemainIncomplete() {
    return Stream.of(
        "java.util.function.IntSupplier subject(String text) { return text::missing; }",
        "java.util.function.Function<String,String> subject() { return Example::pick; } static String pick(CharSequence x) { return \"\"; } static String pick(java.io.Serializable x) { return \"\"; }",
        "int subject() { return String::length; }",
        "java.util.function.Function<Integer,String> subject(String text) { return text::valueOf; }",
        "java.util.function.Supplier<int[]> subject() { return int[]::new; }",
        "java.util.function.Supplier<Example> subject(Example object) { return object::new; }",
        "java.util.function.Supplier<Example> subject() { return super::new; }",
        "java.util.function.Supplier<Example> subject() { return Example.super::new; }")
        .map(member -> DynamicTest.dynamicTest(member, () -> {
          // Arrange
          var source = "class Example { " + member + " }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, result.completion());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")), result.diagnostics().toString());
          var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
          assertEquals(result.diagnostics(), decoded.diagnostics());
        }));
  }

  @Test
  void givenCompleteReferenceSource_whenCfgIsRequested_thenRefusalPreservesSourceAndBoundedProfileRemainsIncomplete() throws IOException {
    // Arrange
    var source = "class Example { java.util.function.IntFunction<int[]> subject() { return int[]::new; } }";
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var before = AstCodec.encode(result);
    var method = result.program().methods().stream().filter(candidate -> candidate.name().equals("subject")).findFirst().orElseThrow();
    // Act
    var graph = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(MethodCfgState.UNSUPPORTED, graph.methods().getFirst().state());
    assertArrayEquals(before, AstCodec.encode(result));
    assertEquals(AstCompletion.INCOMPLETE, produce(source, AstProfile.JAVA_SOURCE_BOUNDED).completion());
    var reference = references(result).getFirst();
    assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(reference.sourceFileKey(), reference.structuralPath(), reference.sourceKind(),
        reference.syntaxKind(), reference.type(), reference.value(), reference.children(), Optional.of(new SemanticReference(ReferenceKind.CONSTRUCTOR,
            ResolutionStatus.RESOLVED, Optional.of("supplied-constructor"), Optional.empty(), List.of(), List.of(), Optional.empty())), reference.origin().orElseThrow()));
  }

  @TestFactory
  Stream<DynamicTest> givenUnresolvedArrayComponentsInHeader_whenCloneReferenceIsProduced_thenWrittenSelectionAndCompilerErrorsSurviveClosure() {
    return Stream.of("Missing[]", "Missing[][]").map(written -> DynamicTest.dynamicTest(written, () -> {
      // Arrange
      var arrayType = written.equals("Missing[][]") ? "Missing[][]" : "Missing[]";
      var source = "class Example { java.util.function.Function<" + arrayType + "," + arrayType + "> subject() { return " + written + "::clone; } }";
      var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
      // Act
      var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED);
      var result = outcome.result().orElseThrow();
      var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
      var retained = AnalysisCapabilities.astRetention(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(new AstPublicationRequest(outcome,
          PublicationIntent.SAVE_COMPLETE_AST));
      // Assert
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertTrue(decoded.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")), decoded.diagnostics().toString());
      assertTrue(decoded.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-type-use-structure")));
      var reference = references(decoded).getFirst();
      assertEquals(SyntaxValue.ReferenceSelection.ARRAY_CLONE, reference.value(SyntaxValue.MemberReference.class).selection());
      assertEquals(JavaSourceKind.ARRAY_TYPE, reference.requiredChild(SyntaxRole.QUALIFIER).sourceKind());
      assertTrue(reference.semanticReference().isEmpty());
      assertEquals(result.aggregate(), decoded.aggregate());
      assertEquals(result.diagnostics(), decoded.diagnostics());
      assertEquals(PublicationStatus.NOT_ELIGIBLE, retained.status());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidArrayCloneWithRetainableHeader_whenProduced_thenWrittenSelectionAndCompilerErrorsSurviveClosure() {
    return Stream.of("Missing[]", "Missing[][]", "String @Missing []").map(written -> DynamicTest.dynamicTest(written, () -> {
      // Arrange
      var arrayType = written.equals("Missing[][]") ? "Object[][]" : written.startsWith("String") ? "String[]" : "Object[]";
      var source = "class Example { java.util.function.Function<" + arrayType + "," + arrayType + "> subject() { return " + written + "::clone; } }";
      var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
      // Act
      var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_EXPANDED);
      var result = outcome.result().orElseThrow();
      var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
      var retained = AnalysisCapabilities.astRetention(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(new AstPublicationRequest(outcome,
          PublicationIntent.SAVE_COMPLETE_AST));
      // Assert
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertTrue(decoded.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")), decoded.diagnostics().toString());
      var reference = references(decoded).getFirst();
      assertEquals(SyntaxValue.ReferenceSelection.ARRAY_CLONE, reference.value(SyntaxValue.MemberReference.class).selection());
      assertEquals(SyntaxValue.ReferenceQualifier.TYPE, reference.value(SyntaxValue.MemberReference.class).qualifier());
      assertTrue(reference.semanticReference().isEmpty());
      assertEquals(written.contains("@") ? JavaSourceKind.ANNOTATED_TYPE : JavaSourceKind.ARRAY_TYPE, reference.requiredChild(SyntaxRole.QUALIFIER).sourceKind());
      assertEquals(result.aggregate(), decoded.aggregate());
      assertEquals(result.diagnostics(), decoded.diagnostics());
      assertEquals(PublicationStatus.NOT_ELIGIBLE, retained.status());
    }));
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile).result().orElseThrow();
  }

  @Test
  void givenIntrinsicArraySelectionOnANonArrayQualifier_whenConstructed_thenContradictorySourceFactsAreRejected() throws IOException {
    // Arrange
    var source = "class Example { java.util.function.ToIntFunction<String> subject() { return String::length; } }";
    var reference = references(produce(source, AstProfile.JAVA_SOURCE_EXPANDED)).getFirst();
    var selected = reference.value(SyntaxValue.MemberReference.class).selectedType();
    var contradictory = new SyntaxValue.MemberReference("clone", SyntaxValue.ReferenceMode.METHOD, SyntaxValue.ReferenceQualifier.TYPE,
        selected, SyntaxValue.ReferenceSelection.ARRAY_CLONE, false);
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(reference.sourceFileKey(), reference.structuralPath(), reference.sourceKind(),
        reference.syntaxKind(), reference.type(), contradictory, reference.children(), Optional.empty(), reference.origin().orElseThrow()));
    // Assert
    assertEquals("An intrinsic array clone reference requires its actual array qualifier", failure.getMessage());
  }

  @Test
  void givenOverloadedMethodReference_whenCompilerSelectsItsTarget_thenExactWrittenDeclarationIsRetained() throws IOException {
    // Arrange
    var source = "class Example { static String pick(int value) { return \"int\"; } static String pick(long value) { return \"long\"; } "
        + "java.util.function.IntFunction<String> subject() { return Example::pick; } }";
    // Act
    var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var expected = result.program().methods().stream().filter(method -> method.name().equals("pick") && method.parameters().getFirst().staticType().erasedName().equals("int"))
        .findFirst().orElseThrow();
    assertEquals(Optional.of(expected.methodKey()), references(result).getFirst().requiredReference().symbolKey());
    assertEquals("(int)java.lang.String", ((SyntaxType.Resolved) references(result).getFirst().value(SyntaxValue.MemberReference.class).selectedType()).identity().displayName());
  }

  @TestFactory
  Stream<DynamicTest> givenMissingRequiredReferenceFacts_whenCopied_thenLegalGapsRemainIneligibleAndContradictoryTypeUsesAreRejected() {
    return Stream.of("java.util.function.IntFunction<int[]> subject() { return int[]::new; }",
        "java.util.function.Supplier<int[]> subject(int[] values) { return values::clone; }",
        "java.util.function.Supplier<Example> subject() { return Example::new; }",
        "java.util.function.IntFunction<String> subject() { return String::valueOf; }")
        .flatMap(member -> Stream.of("target", "selected-type", "qualifier", "target-structure", "target-identity-only", "selected-structure", "selected-identity-only", "qualifier-structure", "qualifier-identity-only")
            .map(missingFact -> DynamicTest.dynamicTest(member + " " + missingFact, () -> {
      // Arrange
      var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
          "class Example { " + member + " }")));
      var original = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
      var complete = AstProductionAdmission.outcome(original, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1").result().orElseThrow();
      assertEquals(AstCompletion.COMPLETE, complete.completion());
      var originalReference = references(complete).getFirst();
      var captured = SourceValueCapture.capture(original.aggregate().orElseThrow());
      if (missingFact.equals("qualifier-identity-only") && originalReference.requiredChild(SyntaxRole.QUALIFIER).syntaxKind() == SyntaxKind.TYPE_USE) {
        // Act
        var failure = assertThrows(IllegalArgumentException.class, () -> replaceFact(captured.sources().getFirst().root(), new IdentityHashMap<>(), missingFact));
        // Assert
        assertEquals("A resolved type use requires recursive attributed structure", failure.getMessage());
        assertEquals(complete.aggregate(), AstCodec.decode(AstCodec.encode(complete), complete.identity()).aggregate());
        return;
      }
      var originalSelection = captured.sources().stream().flatMap(source -> source.nodes().stream())
          .filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).findFirst().orElseThrow().value(SyntaxValue.MemberReference.class).selection();
      var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
      var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
          replaceFact(source.root(), replacements, missingFact), source.nodes().stream().map(node -> replaceFact(node, replacements, missingFact)).toList(),
          source.structuralEdges(), source.semanticEdges())).toList();
      var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), replaceFact(body.root(), replacements, missingFact),
          body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
      var aggregate = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes(), captured.scopes()));
      var input = JavaCoverageTestSupport.capture(original, AstProfile.JAVA_SOURCE_BOUNDED).withFacts(original.program(), Optional.of(aggregate));
      // Act
      var outcome = AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
      var result = outcome.result().orElseThrow();
      var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
      var publication = AnalysisCapabilities.astRetention(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(new AstPublicationRequest(outcome,
          PublicationIntent.SAVE_COMPLETE_AST));
      // Assert
      assertEquals(AstCompletion.INCOMPLETE, result.completion());
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertEquals(result.aggregate(), decoded.aggregate());
      assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
      var reference = references(decoded).getFirst();
      if (missingFact.equals("target")) assertEquals(SyntaxType.Unresolved.INSTANCE, reference.type());
      else if (missingFact.equals("target-identity-only")) {
        assertTrue(reference.type() instanceof SyntaxType.Resolved);
        assertEquals(originalReference.type().knownIdentity(), reference.type().knownIdentity());
        assertTrue(((SyntaxType.Resolved) reference.type()).structure().isEmpty());
        assertEquals(originalReference.value(), reference.value());
        assertEquals(originalReference.requiredChild(SyntaxRole.QUALIFIER), reference.requiredChild(SyntaxRole.QUALIFIER));
      }
      else if (missingFact.equals("selected-identity-only") || missingFact.equals("qualifier-identity-only")) {
        var partialType = missingFact.equals("selected-identity-only") ? reference.value(SyntaxValue.MemberReference.class).selectedType() : reference.requiredChild(SyntaxRole.QUALIFIER).type();
        assertTrue(partialType instanceof SyntaxType.Resolved);
        assertTrue(((SyntaxType.Resolved) partialType).structure().isEmpty());
        assertTrue(partialType.knownIdentity().isPresent());
      }
      else if (missingFact.equals("selected-type")) assertEquals(SyntaxType.Unresolved.INSTANCE, reference.value(SyntaxValue.MemberReference.class).selectedType());
      else if (missingFact.equals("qualifier")) {
        assertEquals(SyntaxType.Unresolved.INSTANCE, reference.requiredChild(SyntaxRole.QUALIFIER).type());
        assertEquals(originalSelection, reference.value(SyntaxValue.MemberReference.class).selection());
        assertTrue(reference.value(SyntaxValue.MemberReference.class).selectedType() instanceof SyntaxType.Resolved);
      } else {
        var partialType = missingFact.equals("target-structure") ? reference.type()
            : missingFact.equals("selected-structure") ? reference.value(SyntaxValue.MemberReference.class).selectedType()
                : reference.requiredChild(SyntaxRole.QUALIFIER).type();
        assertTrue(partialType instanceof SyntaxType.StructureUnavailable);
        assertTrue(partialType.knownIdentity().isPresent());
      }
    })));
  }

  private static SyntaxNode replaceFact(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements, String missingFact) {
    var previous = replacements.get(node);
    if (previous != null) return previous;
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replaceFact(child.node(), replacements, missingFact))).toList();
    var type = node.type();
    var value = node.value();
    var semanticReference = node.semanticReference();
    if ((missingFact.equals("qualifier") || missingFact.equals("qualifier-structure") || missingFact.equals("qualifier-identity-only")) && node.structuralPath().steps().getLast().role() == SyntaxRole.QUALIFIER) {
      type = missingFact.equals("qualifier") ? SyntaxType.Unresolved.INSTANCE : missingFact.equals("qualifier-identity-only") ? new SyntaxType.Resolved(((SyntaxType.Resolved) type).identity()) : new SyntaxType.StructureUnavailable(((SyntaxType.Resolved) type).identity());
    }
    if (value instanceof SyntaxValue.MemberReference reference) {
      if (missingFact.equals("target")) type = SyntaxType.Unresolved.INSTANCE;
      else if (missingFact.equals("target-structure")) type = new SyntaxType.StructureUnavailable(((SyntaxType.Resolved) type).identity());
      else if (missingFact.equals("target-identity-only")) type = new SyntaxType.Resolved(((SyntaxType.Resolved) type).identity());
      else if (missingFact.equals("member")) semanticReference = Optional.empty();
      else if (missingFact.equals("name")) value = new SyntaxValue.MemberReference("different", reference.mode(), reference.qualifier(), reference.selectedType(), reference.selection(), reference.staticMember());
      else if (missingFact.equals("selected-type")) value = new SyntaxValue.MemberReference(reference.name(), reference.mode(), reference.qualifier(), SyntaxType.Unresolved.INSTANCE, reference.selection(), reference.staticMember());
      else if (missingFact.equals("selected-structure")) value = new SyntaxValue.MemberReference(reference.name(), reference.mode(), reference.qualifier(),
          new SyntaxType.StructureUnavailable(((SyntaxType.Resolved) reference.selectedType()).identity()), reference.selection(), reference.staticMember());
      else if (missingFact.equals("selected-identity-only")) value = new SyntaxValue.MemberReference(reference.name(), reference.mode(), reference.qualifier(),
          new SyntaxType.Resolved(((SyntaxType.Resolved) reference.selectedType()).identity()), reference.selection(), reference.staticMember());
    }
    var replacement = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, value, children, semanticReference, node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, value, children, semanticReference);
    replacements.put(node, replacement);
    return replacement;
  }

  @TestFactory
  Stream<DynamicTest> givenMissingNamedReferenceSelection_whenCopiedAndDecoded_thenSourceRemainsIncompleteAndRetentionIneligible() {
    return Stream.of("java.util.function.IntSupplier subject() { return Example::selected; } static int selected() { return 1; }",
        "java.util.function.Supplier<Example> subject() { return Example::new; }")
        .map(member -> DynamicTest.dynamicTest(member, () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "class Example { " + member + " }")));
          var original = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
          var captured = SourceValueCapture.capture(original.aggregate().orElseThrow());
          var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
          var referenceKeys = captured.sources().stream().flatMap(source -> source.nodes().stream()).filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE)
              .map(SyntaxNode::nodeId).collect(Collectors.toSet());
          var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
              replaceFact(source.root(), replacements, "member"), source.nodes().stream().map(node -> replaceFact(node, replacements, "member")).toList(),
              source.structuralEdges(), source.semanticEdges().stream().filter(edge -> !referenceKeys.contains(edge.sourceKey())).toList())).toList();
          var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), replaceFact(body.root(), replacements, "member"),
              body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
          var aggregate = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes(), captured.scopes()));
          var input = JavaCoverageTestSupport.capture(original, AstProfile.JAVA_SOURCE_BOUNDED).withFacts(original.program(), Optional.of(aggregate));
          // Act
          var outcome = AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1");
          var result = outcome.result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
          var publication = AnalysisCapabilities.astRetention(ignored -> { throw new AssertionError("Incomplete source cannot acquire storage"); }).retainAst(new AstPublicationRequest(outcome,
              PublicationIntent.SAVE_COMPLETE_AST));
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(SyntaxValue.ReferenceSelection.EXECUTABLE, references(decoded).getFirst().value(SyntaxValue.MemberReference.class).selection());
          assertTrue(references(decoded).getFirst().semanticReference().isEmpty());
          assertEquals(PublicationStatus.NOT_ELIGIBLE, publication.status());
        }));
  }

  @Test
  void givenChangedReferenceName_whenReconciledAgainstIndependentWrittenProperties_thenTheExactNameObligationFails() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example { java.util.function.ToIntFunction<String> subject() { return Integer::parseInt; } }")));
    var original = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var captured = SourceValueCapture.capture(original.aggregate().orElseThrow());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
        replaceFact(source.root(), replacements, "name"), source.nodes().stream().map(node -> replaceFact(node, replacements, "name")).toList(),
        source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), replaceFact(body.root(), replacements, "name"),
        body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes(), captured.scopes()));
    // Act
    var input = JavaCoverageTestSupport.capture(original, AstProfile.JAVA_SOURCE_BOUNDED).withFacts(original.program(), Optional.of(aggregate));
    var result = AstProductionAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1").result().orElseThrow();
    // Assert
    var failed = result.coverage().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("MemberReferenceTree::MemberReferenceTree#getName()")).toList();
    assertEquals(1, failed.size());
    assertEquals(FeatureContextOutcome.Mode.FAILED, failed.getFirst().support().mode());
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
  }

  private static List<SyntaxNode> references(AstResult result) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MEMBER_REFERENCE).toList();
  }

  @TestFactory
  Stream<DynamicTest> givenReferencesInSupportedExpressionContexts_whenProduced_thenActualConsumersRetainWrittenFunctionalValues() {
    return Stream.of(
        "class Example { static void consume(java.util.function.IntFunction<int[]> value) {} void subject() { consume(int[]::new); } }",
        "class Example { Thread subject(String text) { return new Thread(text::trim); } }",
        "class Example { java.util.function.IntFunction<int[]> subject(boolean flag) { return flag ? int[]::new : int[]::new; } }",
        "class Example { java.util.function.Supplier<java.util.function.IntFunction<int[]>> subject() { return () -> int[]::new; } }",
        "class Example { Object subject() { return (java.util.function.IntFunction<int[]> & java.io.Serializable) int[]::new; } }")
        .map(source -> DynamicTest.dynamicTest(source, () -> {
          // Arrange
          var expectedCount = source.contains(" ? ") ? 2 : 1;
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertEquals(expectedCount, references(result).size());
          assertTrue(references(result).stream().allMatch(node -> node.optionalChild(SyntaxRole.QUALIFIER).isPresent() && node.type() instanceof SyntaxType.Resolved));
        }));
  }

  @Test
  void givenExpandedReferenceRequest_whenApplicationSelectsCfg_thenRealRefusalKeepsTheCompleteSource() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")),
        "class Example { java.util.function.IntFunction<int[]> subject() { return int[]::new; } }")));
    var runner = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    // Act
    var astOnly = runner.analyze(new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_EXPANDED), Optional.empty()));
    var selected = runner.analyze(new AnalysisComputationRequest(
        new AstProductionRequest(snapshot, configuration, AstProfile.JAVA_SOURCE_EXPANDED), Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED))));
    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, astOnly.completion());
    assertEquals(AnalysisCompletion.PARTIAL, selected.completion());
    var original = astOnly.representation().ast().result().orElseThrow();
    var refused = selected.representation().ast().result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, refused.completion());
    assertEquals(original.identity(), refused.identity());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(refused));
  }

  private record Variant(String target, String expression, String parameters, String members, SyntaxValue.ReferenceQualifier qualifier,
      String name, boolean staticMember, int typeArguments) {}
}
