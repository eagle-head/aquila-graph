package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.AstResultAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class PatternAstTest {
  @TempDir Path directory;

  @Test
  void givenTraditionalTypeTest_whenProducedAndDecoded_thenCompleteWrittenTypeAndOperandSurviveClosure() throws IOException {
    // Arrange
    var source = "class Example { boolean subject(Object value) { return value instanceof String; } }";
    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var result = AstCodec.decode(AstCodec.encode(original), original.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var test = result.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.INSTANCE_OF).findFirst().orElseThrow();
    assertEquals("INSTANCE_OF", test.syntaxKind().name());
    assertEquals(List.of(SyntaxRole.EXPRESSION, SyntaxRole.TYPE_OPERAND), test.children().stream().map(child -> child.role()).toList());
    assertEquals("java.lang.String", test.requiredChild(SyntaxRole.TYPE_OPERAND).resolvedType().orElseThrow().erasedName());
    assertTrue(test.requiredChild(SyntaxRole.EXPRESSION).semanticReference().isPresent());
  }

  @TestFactory
  Stream<DynamicTest> givenLegalPatternForms_whenProducedAndDecoded_thenOrderedBindingsTypesAndEmptyFormsSurviveClosure() {
    return Stream.of(
        new Variant("String text", "", List.of("text"), List.of(LocalBindingEvidence.TypeForm.DECLARED), 0, 0),
        new Variant("String _", "", List.of("_"), List.of(LocalBindingEvidence.TypeForm.DECLARED), 0, 0),
        new Variant("final String text", "", List.of("text"), List.of(LocalBindingEvidence.TypeForm.DECLARED), 0, 0),
        new Variant("String[] text", "", List.of("text"), List.of(LocalBindingEvidence.TypeForm.DECLARED), 0, 0),
        new Variant("Empty()", "record Empty() {}", List.of(), List.of(), 1, 0),
        new Variant("Pair(String text, int count)", "record Pair(Object text, int count) {}", List.of("text", "count"), List.of(LocalBindingEvidence.TypeForm.DECLARED, LocalBindingEvidence.TypeForm.DECLARED), 1, 0),
        new Variant("Pair(var text, var count)", "record Pair(String text, int count) {}", List.of("text", "count"), List.of(LocalBindingEvidence.TypeForm.INFERRED, LocalBindingEvidence.TypeForm.INFERRED), 1, 0),
        new Variant("Pair(var _, var count)", "record Pair(String text, int count) {}", List.of("_", "count"), List.of(LocalBindingEvidence.TypeForm.INFERRED, LocalBindingEvidence.TypeForm.INFERRED), 1, 0),
        new Variant("Pair(String _, int _)", "record Pair(Object text, int count) {}", List.of("_", "_"), List.of(LocalBindingEvidence.TypeForm.DECLARED, LocalBindingEvidence.TypeForm.DECLARED), 1, 0),
        new Variant("Pair(_, _)", "record Pair(Object text, int count) {}", List.of(), List.of(), 1, 2),
        new Variant("Pair(\\u005f, _)", "record Pair(Object text, int count) {}", List.of(), List.of(), 1, 2),
        new Variant("Pair(\\uu005f, _)", "record Pair(Object text, int count) {}", List.of(), List.of(), 1, 2),
        new Variant("Box(var text)", "record Box<T>(T text) {}", List.of("text"), List.of(LocalBindingEvidence.TypeForm.INFERRED), 1, 0),
        new Variant("Box<?>(var text)", "record Box<T>(T text) {}", List.of("text"), List.of(LocalBindingEvidence.TypeForm.INFERRED), 1, 0),
        new Variant("Outer(Pair(String text, int _), Empty())", "record Pair(Object text, int count) {} record Empty() {} record Outer(Pair pair, Empty empty) {}", List.of("text", "_"), List.of(LocalBindingEvidence.TypeForm.DECLARED, LocalBindingEvidence.TypeForm.DECLARED), 3, 0),
        new Variant("Pair(@Mark String text, int count)", "@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE, java.lang.annotation.ElementType.LOCAL_VARIABLE}) @interface Mark {} record Pair(Object text, int count) {}", List.of("text", "count"), List.of(LocalBindingEvidence.TypeForm.DECLARED, LocalBindingEvidence.TypeForm.DECLARED), 1, 0))
        .map(variant -> DynamicTest.dynamicTest(variant.pattern(), () -> {
          // Arrange
          var source = "class Example { " + variant.members() + " boolean subject(Object value) { return value instanceof " + variant.pattern() + "; } }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          var test = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INSTANCE_OF).findFirst().orElseThrow();
          assertEquals(List.of(SyntaxRole.EXPRESSION, SyntaxRole.PATTERN), test.children().stream().map(child -> child.role()).toList());
          assertTrue(test.optionalChild(SyntaxRole.TYPE_OPERAND).isEmpty());
          var patterns = test.requiredChild(SyntaxRole.PATTERN).descendantsAndSelf();
          var bindings = patterns.stream().filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();
          assertEquals(variant.names(), bindings.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
          assertEquals(variant.typeForms(), bindings.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().typeForm()).toList());
          var expectedTypes = variant.pattern().startsWith("Box") ? List.of("java.lang.Object")
              : variant.names().isEmpty() ? List.<String>of()
              : variant.names().size() == 1 ? List.of(variant.pattern().contains("[]") ? "java.lang.String[]" : "java.lang.String")
              : List.of("java.lang.String", "int");
          assertEquals(expectedTypes, bindings.stream().map(node -> node.resolvedType().orElseThrow().erasedName()).toList());
          if (variant.pattern().startsWith("final")) assertEquals(List.of("final"), bindings.getFirst().value(SyntaxValue.LocalDeclaration.class).writtenModifiers());
          for (var binding : bindings) {
            var local = binding.value(SyntaxValue.LocalDeclaration.class);
            assertEquals(LocalBindingEvidence.Context.PATTERN, local.bindingEvidence().orElseThrow().context());
            assertTrue(binding.type() instanceof SyntaxType.Resolved);
            assertFalse(local.bindingEvidence().orElseThrow().initialized());
            assertEquals(local.bindingEvidence().orElseThrow().typeForm() == LocalBindingEvidence.TypeForm.DECLARED, binding.optionalChild(SyntaxRole.DECLARED_TYPE).isPresent());
            assertEquals(!"_".equals(local.name()), binding.semanticReference().isPresent());
          }
          assertEquals(variant.records(), patterns.stream().filter(node -> node.syntaxKind() == SyntaxKind.DECONSTRUCTION_PATTERN).count());
          assertEquals(variant.matchAll(), patterns.stream().filter(node -> node.syntaxKind() == SyntaxKind.ANY_PATTERN).count());
          var allPatterns = patterns.stream().filter(node -> node.syntaxKind() == SyntaxKind.ANY_PATTERN).toList();
          for (var matchAll : allPatterns) {
            assertEquals(allPatterns.indexOf(matchAll) == 0 ? "java.lang.Object" : "int", matchAll.resolvedType().orElseThrow().erasedName());
            assertTrue(matchAll.children().isEmpty());
            assertTrue(matchAll.semanticReference().isEmpty());
            var origin = matchAll.origin().orElseThrow();
            var expectedToken = allPatterns.indexOf(matchAll) == 0 && variant.pattern().contains("\\u")
                ? variant.pattern().substring(variant.pattern().indexOf('(') + 1, variant.pattern().indexOf(',')) : "_";
            assertEquals(expectedToken, source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive())));
          }
          for (var record : patterns.stream().filter(node -> node.syntaxKind() == SyntaxKind.DECONSTRUCTION_PATTERN).toList()) {
            assertEquals(SyntaxRole.TYPE_OPERAND, record.children().getFirst().role());
            assertTrue(record.requiredChild(SyntaxRole.TYPE_OPERAND).type() instanceof SyntaxType.Resolved);
          }
          var outcomes = result.coverage().validationEvidence().outcomes().stream().filter(outcome -> outcome.address().role().startsWith("InstanceOfTree::")).toList();
          assertEquals(4, outcomes.size());
          assertTrue(outcomes.stream().noneMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
          assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
          if (variant.pattern().equals("Empty()")) {
            var components = result.coverage().validationEvidence().outcomes().stream().filter(outcome -> outcome.address().role().endsWith("#getNestedPatterns()")).findFirst().orElseThrow();
            assertEquals(State.EMPTY, components.support().negativeState().orElseThrow().state());
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenLegalFlowScopes_whenProduced_thenAccessesSelectExactPatternBindings() {
    return Stream.of(
        "return value instanceof String text && text.length() > 0;",
        "return !(value instanceof String text) || text.length() > 0;",
        "return value instanceof String text ? text.length() > 0 : false;",
        "if (value instanceof String text) return text.length() > 0; return false;",
        "if (!(value instanceof String text)) return false; return text.length() > 0;",
        "if (!(value instanceof String text)) { return false; } else { return text.length() > 0; }",
        "while (value instanceof String text) { return text.length() > 0; } return false;",
        "while (!(value instanceof String text)) { value = \"text\"; } return text.length() > 0;",
        "for (; value instanceof String text; text.length()) { return text.length() > 0; } return false;",
        "if (value instanceof String text) { java.util.function.IntSupplier length = () -> text.length(); return length.getAsInt() > 0; } return false;",
        "if (value instanceof Pair(var text, int count)) return text.length() > count; return false;")
        .map(body -> DynamicTest.dynamicTest(body, () -> {
          // Arrange
          var source = "class Example { record Pair(String text, int count) {} boolean subject(Object value) { " + body + " } }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          var patterns = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN).toList();
          assertFalse(patterns.isEmpty());
          for (var pattern : patterns) {
            var binding = pattern.requiredChild(SyntaxRole.PARAMETER);
            var key = binding.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow();
            var accesses = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS && node.semanticReference().flatMap(reference -> reference.symbolKey()).equals(Optional.of(key))).toList();
            assertFalse(accesses.isEmpty());
            assertEquals(accesses.stream().map(node -> node.origin().orElseThrow()).sorted(Comparator.comparingLong(origin -> origin.startOffset())).toList(), pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow());
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenIllegalPatternInputs_whenProduced_thenCompilerErrorsRemainIncomplete() {
    return Stream.of(
        "return value instanceof int number;",
        "return value instanceof var name;",
        "return value instanceof _;",
        "return value instanceof Pair(String text);",
        "return value instanceof Pair(String text, int count, _);",
        "if (value instanceof String text) {} return text.length() > 0;",
        "return value instanceof String text || text.length() > 0;",
        "return value instanceof Pair(@Wrong String text, int count);",
        "return value instanceof String text && value instanceof String text;")
        .map(body -> DynamicTest.dynamicTest(body, () -> {
          // Arrange
          var source = "class Example { @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD) @interface Wrong {} record Pair(Object text, int count) {} boolean subject(Object value) { " + body + " } }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, result.completion(), result.diagnostics().toString());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")), result.diagnostics().toString());
        }));
  }

  @Test
  void givenExpandedPatternRequest_whenApplicationSelectsCfg_thenRefusalPreservesCompleteSourceAndBoundedQualification() throws IOException {
    // Arrange
    var source = "class Example { boolean subject(Object value) { return value instanceof String text && text.length() > 0; } }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    // Act
    var astOnly = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED, Optional.empty(), PublicationIntent.DO_NOT_SAVE));
    var selected = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
    // Assert
    assertEquals(AnalysisCompletion.COMPLETE, astOnly.completion());
    assertEquals(AnalysisCompletion.PARTIAL, selected.completion());
    var original = astOnly.representation().ast().result().orElseThrow();
    var refused = selected.representation().ast().result().orElseThrow();
    assertEquals(AstCompletion.COMPLETE, refused.completion());
    assertEquals(original.identity(), refused.identity());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(refused));
    assertEquals(AstCompletion.INCOMPLETE, produce(source, AstProfile.JAVA_SOURCE_BOUNDED).completion());
  }

  private record Variant(String pattern, String members, List<String> names, List<LocalBindingEvidence.TypeForm> typeForms, int records, int matchAll) {}

  @TestFactory
  Stream<DynamicTest> givenMatchAllComponentTypes_whenProducedAndDecoded_thenReferencePrimitiveAndGenericTypesSurviveClosure() {
    return Stream.of(
        List.of("record Pair(String text, int count) {}", "Object", "Pair(_, _)", "java.lang.String", "int"),
        List.of("record Box<T>(T item) {}", "Box<String>", "Box(_)", "java.lang.String"),
        List.of("record Box<T>(T item) {}", "Box<java.util.List<String>>", "Box(_)", "java.util.List<java.lang.String>"))
        .map(variant -> DynamicTest.dynamicTest(variant.toString(), () -> {
          // Arrange
          var source = "class Example { " + variant.getFirst() + " boolean subject(" + variant.get(1) + " value) { return value instanceof " + variant.get(2) + "; } }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertEquals(AstCompletion.COMPLETE, decoded.completion(), decoded.diagnostics().toString());
          var patterns = decoded.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.ANY_PATTERN)
              .sorted(Comparator.comparingLong(node -> node.origin().orElseThrow().startOffset())).toList();
          assertEquals(variant.subList(3, variant.size()), patterns.stream().map(node -> node.resolvedType().orElseThrow().displayName()).toList());
          assertTrue(patterns.stream().allMatch(node -> node.children().isEmpty() && node.semanticReference().isEmpty()));
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenMissingOrErroneousMatchAllAttribution_whenCopiedValuesAreAdmitted_thenCompletionIsPrevented() {
    return Stream.of("any-unresolved", "any-error", "any-wrong").map(fact -> DynamicTest.dynamicTest(fact, () -> {
      // Arrange
      var original = produce("class Example { record Pair(String text, int count) {} boolean subject(Object value) { return value instanceof Pair(_, _); } }", AstProfile.JAVA_SOURCE_EXPANDED);
      var originalInput = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()), original.diagnostics(), original.coverage());
      // Act
      var aggregate = altered(original, fact);
      var input = JavaCoverageTestSupport.withFacts(originalInput, original.program(), Optional.of(aggregate), JavaCoverageTestSupport.diagnostics(originalInput, "copied-pattern-type"));
      var result = AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1").result().orElseThrow();
      var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
      // Assert
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertTrue(decoded.coverage().validationEvidence().outcomes().stream().anyMatch(outcome -> outcome.address().role().equals("match-all-type") && outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenMissingRequiredPatternFacts_whenCopiedValuesAreAdmitted_thenSourceRemainsIncompleteAndCoverageDetectsChangedProperties() {
    return Stream.of("scope", "type", "name").map(fact -> DynamicTest.dynamicTest(fact, () -> {
      // Arrange
      var source = "class Example { boolean subject(Object value) { return value instanceof String text; } }";
      var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
      var originalInput = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()), original.diagnostics(), original.coverage());
      // Act
      var aggregate = altered(original, fact);
      var input = JavaCoverageTestSupport.withFacts(originalInput, original.program(), Optional.of(aggregate), JavaCoverageTestSupport.diagnostics(originalInput, "copied-pattern-facts"));
      var result = AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1").result().orElseThrow();
      var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
      // Assert
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      if (fact.equals("name")) {
        assertTrue(decoded.coverage().validationEvidence().outcomes().stream().anyMatch(outcome -> outcome.address().role().endsWith("#getName()") && outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
      }
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenContradictoryPatternScope_whenCopiedValuesAreAdmitted_thenMissingAccessAndForeignSelectionAreRejected() {
    return Stream.of("missing-access", "foreign-selection").map(fact -> DynamicTest.dynamicTest(fact, () -> {
      // Arrange
      var original = produce("class Example { boolean subject(Object value) { return value instanceof String text && text.length() > 0; } }", AstProfile.JAVA_SOURCE_EXPANDED);
      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> altered(original, fact));
      // Assert
      assertTrue(failure.getMessage().contains("pattern") || failure.getMessage().contains("Pattern"), failure.getMessage());
    }));
  }

  private static JavaAstAggregate altered(AstResult original, String fact) {
    var captured = CopiedAstValues.capture(original.aggregate());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var inputAccess = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS && node.value(SyntaxValue.SourceSymbol.class).name().equals("value")).findFirst().orElseThrow().origin().orElseThrow();
    var booleanType = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INSTANCE_OF).findFirst().orElseThrow().type();
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(), replace(source.root(), replacements, fact, inputAccess, booleanType), source.nodes().stream().map(node -> replace(node, replacements, fact, inputAccess, booleanType)).toList(), source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), replace(body.root(), replacements, fact, inputAccess, booleanType), body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    return AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes()));
  }

  private static SyntaxNode replace(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements, String fact, SourceSpan inputAccess, SyntaxType booleanType) {
    if (replacements.containsKey(node)) return replacements.get(node);
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replace(child.node(), replacements, fact, inputAccess, booleanType))).toList();
    var type = node.type();
    var value = node.value();
    if (node.syntaxKind() == SyntaxKind.ANY_PATTERN && fact.equals("any-unresolved")) type = SyntaxType.Unresolved.INSTANCE;
    if (node.syntaxKind() == SyntaxKind.ANY_PATTERN && fact.equals("any-error")) type = SyntaxType.Erroneous.INSTANCE;
    if (node.syntaxKind() == SyntaxKind.ANY_PATTERN && fact.equals("any-wrong")) type = booleanType;
    if (node.syntaxKind() == SyntaxKind.TYPE_USE && node.value() instanceof SyntaxValue.SourceSymbol symbol && symbol.name().equals("String") && fact.equals("type")) type = SyntaxType.Unresolved.INSTANCE;
    if (node.syntaxKind() == SyntaxKind.BINDING_PATTERN) {
      if (fact.equals("scope")) value = new SyntaxValue.PatternBinding(Optional.empty());
      if (fact.equals("missing-access")) value = new SyntaxValue.PatternBinding(Optional.of(List.of()));
      if (fact.equals("foreign-selection")) value = new SyntaxValue.PatternBinding(Optional.of(List.of(inputAccess)));
    }
    if (node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION && fact.equals("name")) {
      var local = node.value(SyntaxValue.LocalDeclaration.class);
      value = new SyntaxValue.LocalDeclaration("renamed", local.writtenType(), local.writtenModifiers(), local.bindingEvidence());
    }
    var copy = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, value, children, node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, value, children, node.semanticReference());
    replacements.put(node, copy);
    return copy;
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
  }
}
