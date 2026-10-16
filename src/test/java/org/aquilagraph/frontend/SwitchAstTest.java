package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.AstResultAdmission;
import java.util.IdentityHashMap;
import java.util.Map;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.application.CfgSelection;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.application.SourceAnalysisRunner;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.source.api.SourceInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class SwitchAstTest {
  @TempDir Path directory;

  @Test
  void givenColonStatementWithEmptyAndTrailingGroups_whenProducedAndDecoded_thenWrittenGroupsRemainDistinct() throws IOException {
    // Arrange
    var source = "class Example { void subject(int value) { switch (value) { case 1: case 2: value++; break; default: } } }";
    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var result = AstCodec.decode(AstCodec.encode(original), original.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var selection = result.aggregate().nodes().stream().filter(node -> node.syntaxKind().name().equals("SWITCH_STATEMENT")).findFirst().orElseThrow();
    assertEquals(List.of(SyntaxRole.SELECTOR, SyntaxRole.CASE, SyntaxRole.CASE, SyntaxRole.CASE), selection.children().stream().map(child -> child.role()).toList());
    var cases = selection.children(SyntaxRole.CASE);
    assertEquals(List.of(0, 2, 0), cases.stream().map(node -> node.children(SyntaxRole.STATEMENT).size()).toList());
    assertTrue(cases.stream().allMatch(node -> node.value(SyntaxValue.SwitchCase.class).form() == SyntaxValue.SwitchCaseForm.STATEMENT_GROUP));
    assertEquals(List.of("1", "2"), cases.stream().flatMap(node -> node.children(SyntaxRole.LABEL).stream()).map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
    assertEquals(List.of(SyntaxValue.SwitchLabel.Default.INSTANCE), cases.getLast().value(SyntaxValue.SwitchCase.class).labels());
  }

  @TestFactory
  Stream<DynamicTest> givenExpandedLabels_whenProducedAndDecoded_thenWrittenLabelAndGuardSubtreesSurviveClosure() {
    return Stream.of(
        "int subject(int value) { return switch (value) { case 1 + 2, 4 << 1 -> 10; default -> 20; }; }",
        "int subject(String value) { return switch (value) { case \"a\" + \"b\" -> 10; case null, default -> 20; }; }",
        "int subject(Object value) { return switch (value) { case String text when text.length() > 0 -> text.length(); case null, default -> 20; }; }",
        "int subject(Object value) { return switch (value) { case Pair(String text, int count) when text.length() > count -> count; default -> 20; }; }")
        .map(method -> DynamicTest.dynamicTest(method, () -> {
          // Arrange
          var source = "class Example { record Pair(Object text, int count) {} " + method + " }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          var selection = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION).findFirst().orElseThrow();
          assertEquals(2, selection.children(SyntaxRole.CASE).size());
          assertTrue(selection.children(SyntaxRole.CASE).stream().allMatch(node -> node.optionalChild(SyntaxRole.BODY).isPresent()));
          assertEquals(method.contains(" when "), selection.children(SyntaxRole.CASE).getFirst().optionalChild(SyntaxRole.GUARD).isPresent());
          assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
          assertFalse(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
        }));
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile).result().orElseThrow();
  }

  @TestFactory
  Stream<DynamicTest> givenLegalSwitchVariants_whenProducedAndDecoded_thenExactSourceFormsAndLabelsRemainQueryable() {
    return Stream.of(
        new Selection("void subject(int value) { switch (value) {} }", true, List.of(), List.of()),
        new Selection("void subject(int value) { switch (value) { case 1, 2 -> value++; default -> {} } }", true, List.of("1", "2"), List.of("RULE", "RULE")),
        new Selection("int subject(int value) { return switch (value) { case 1: yield 2; default: yield 3; }; }", false, List.of("1"), List.of("STATEMENT_GROUP", "STATEMENT_GROUP")),
        new Selection("int subject(int value) { final int limit = 3; return switch (value) { case limit - 1, -1, (int) 4.0, (true ? 5 : 6) -> 10; default -> 20; }; }", false, List.of("limit - 1", "-1", "(int) 4.0", "(true ? 5 : 6)"), List.of("RULE", "RULE")),
        new Selection("int subject(String value) { return switch (value) { case null -> 0; case \"a\", \"b\" -> 1; default -> 2; }; }", false, List.of("null", "\"a\"", "\"b\""), List.of("RULE", "RULE", "RULE")),
        new Selection("int subject(Object value) { return switch (value) { case RetentionPolicy.SOURCE -> 1; default -> 2; }; }", false, List.of("RetentionPolicy.SOURCE"), List.of("RULE", "RULE")),
        new Selection("int subject(RetentionPolicy value) { return switch (value) { case SOURCE, CLASS -> 1; case RUNTIME -> 2; }; }", false, List.of("SOURCE", "CLASS", "RUNTIME"), List.of("RULE", "RULE")),
        new Selection("int subject(Object value) { return switch (value) { case Integer _, String _ when value != null -> 1; default -> 2; }; }", false, List.of("Integer _", "String _"), List.of("RULE", "RULE")),
        new Selection("int subject(Object value) { return switch (value) { case Pair(_, int count) when count > 0 -> count; case null, default -> 0; }; }", false, List.of("Pair(_, int count)", "null"), List.of("RULE", "RULE")),
        new Selection("int subject(Object value) { switch (value) { case String text when text.length() > 0: return text.length(); default: return 0; } }", true, List.of("String text"), List.of("STATEMENT_GROUP", "STATEMENT_GROUP")),
        new Selection("int subject(int value) { switch (value) { case 0: int count = 1; break; case 1: count = 2; return count; default: break; } return 0; }", true, List.of("0", "1"), List.of("STATEMENT_GROUP", "STATEMENT_GROUP", "STATEMENT_GROUP")),
        new Selection("int subject(int value) { outer: switch (value) { case 1: break outer; default: break; } return 0; }", true, List.of("1"), List.of("STATEMENT_GROUP", "STATEMENT_GROUP")))
        .map(variant -> DynamicTest.dynamicTest(variant.method(), () -> {
          // Arrange
          var source = "import java.lang.annotation.RetentionPolicy; class Example { record Pair(Object text, int count) {} " + variant.method() + " }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var result = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          var kind = variant.statement() ? SyntaxKind.SWITCH_STATEMENT : SyntaxKind.SWITCH_EXPRESSION;
          var selection = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind).findFirst().orElseThrow();
          var cases = selection.children(SyntaxRole.CASE);
          assertEquals(variant.forms(), cases.stream().map(node -> node.value(SyntaxValue.SwitchCase.class).form().name()).toList());
          assertEquals(variant.labels(), cases.stream().flatMap(node -> node.children(SyntaxRole.LABEL).stream()).map(node -> written(source, node)).toList());
          assertTrue(result.coverage().validationEvidence().outcomes().stream().noneMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
          assertTrue(result.coverage().validationEvidence().unmatchedFacts().isEmpty());
          assertEquals(original.aggregate(), result.aggregate());
        }));
  }

  @Test
  void givenNestedSwitchesAndGuardBindings_whenProduced_thenYieldsAndAccessesRetainExactOwners() throws IOException {
    // Arrange
    var source = "class Example { String subject(Object value, int number) { return switch (value) { case String text when text.length() > 0 -> { int size = switch (number) { case 1: yield text.length(); default: yield 0; }; yield text + size; } default -> \"empty\"; }; } }";
    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var result = AstCodec.decode(AstCodec.encode(original), original.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var selections = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.SWITCH_EXPRESSION).toList();
    assertEquals(2, selections.size());
    var outer = selections.stream().filter(node -> node.resolvedType().orElseThrow().erasedName().equals("java.lang.String")).findFirst().orElseThrow();
    var inner = selections.stream().filter(node -> node.resolvedType().orElseThrow().erasedName().equals("int")).findFirst().orElseThrow();
    assertEquals(2, inner.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.YIELD).count());
    assertEquals(3, outer.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.YIELD).count());
    var pattern = outer.children(SyntaxRole.CASE).getFirst().children(SyntaxRole.LABEL).getFirst();
    var key = pattern.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow();
    var accesses = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS && node.semanticReference().flatMap(reference -> reference.symbolKey()).equals(Optional.of(key))).sorted(Comparator.comparingLong(node -> node.origin().orElseThrow().startOffset())).toList();
    assertEquals(3, accesses.size());
    assertEquals(accesses.stream().map(node -> node.origin().orElseThrow()).toList(), pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow());
    var body = result.body(result.program().methods().stream().filter(method -> method.name().equals("subject")).findFirst().orElseThrow().methodKey()).orElseThrow();
    var guard = outer.children(SyntaxRole.CASE).getFirst();
    assertEquals("boolean", body.useEvidence(guard.nodeId(), SyntaxRole.GUARD, 0).orElseThrow().selectedTarget().erasedName());
    for (var yield : outer.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.YIELD).toList()) {
      var expected = written(source, yield).contains("text + size") ? "java.lang.String" : "int";
      assertEquals(expected, body.useEvidence(yield.nodeId(), SyntaxRole.VALUE, 0).orElseThrow().selectedTarget().erasedName());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidSwitchInputs_whenProduced_thenActualCompilerErrorsPreventCompletion() {
    return Stream.of(
        "int subject(int value) { return switch (value) { case 1, 1 -> 1; default -> 0; }; }",
        "int subject(int value) { return switch (value) { case 1, default -> 1; }; }",
        "int subject(String value) { return switch (value) { case null, \"x\" -> 1; default -> 0; }; }",
        "int subject(String value) { return switch (value) { case null -> 1; case null, default -> 0; }; }",
        "int subject(String value) { return switch (value) { case default, null -> 0; }; }",
        "int subject(int value) { return switch (value) { case 1 -> 1; default: yield 0; }; }",
        "int subject(int value) { int label = 1; return switch (value) { case label -> 1; default -> 0; }; }",
        "int subject(int value) { return switch (value) { case subject(1) -> 1; default -> 0; }; }",
        "int subject(int value) { return switch (value) { case 1 -> 1; }; }",
        "int subject(Object value) { return switch (value) { case String text when 1 -> 1; default -> 0; }; }",
        "int subject(Object value) { return switch (value) { case String text when false -> 1; default -> 0; }; }",
        "int subject(Object value) { return switch (value) { case Integer number, String text -> 1; default -> 0; }; }",
        "int subject(Object value) { return switch (value) { case Object other -> 1; case String text -> 2; }; }",
        "int subject(int value) { return switch (value) { case null, default -> 1; }; }",
        "int subject(int value) { return switch (value) { default -> { java.util.function.IntSupplier supplier = () -> { yield 1; }; yield supplier.getAsInt(); } }; }",
        "int subject(Object value) { switch (value) { case String text: break; default: return text.length(); } return 0; }")
        .map(method -> DynamicTest.dynamicTest(method, () -> {
          // Arrange
          var source = "class Example { " + method + " }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertEquals(AstCompletion.INCOMPLETE, result.completion(), result.diagnostics().toString());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.code().startsWith("compiler.")), result.diagnostics().toString());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenSwitchProfiles_whenApplicationRequestsCfg_thenSourceQualificationAndHistoricalGraphBoundaryRemainIndependent() {
    return Stream.of(
        new ProfileCase("int subject(int value) { return switch (value) { case 1 -> 2; default -> 3; }; }", true),
        new ProfileCase("int subject(int value) { switch (value) { case 1: return 2; default: return 3; } }", false),
        new ProfileCase("int subject(int value) { return switch (value) { case 1 + 2 -> 2; default -> 3; }; }", false),
        new ProfileCase("int subject(Object value) { return switch (value) { case String text when text.length() > 0 -> text.length(); case null, default -> 0; }; }", false))
        .map(variant -> DynamicTest.dynamicTest(variant.method(), () -> {
          // Arrange
          var source = "class Example { " + variant.method() + " }";
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
          var runner = new SourceAnalysisRunner(new JavaFrontend(new JavaSourceLoader()), CfgDerivers.standard());
          var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
          // Act
          var astOnly = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED, Optional.empty(), PublicationIntent.DO_NOT_SAVE));
          var selected = runner.analyze(new AnalysisRequest(new SourceInput.Snapshot(snapshot), configuration, AstProfile.JAVA_SOURCE_EXPANDED, Optional.of(CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED)), PublicationIntent.DO_NOT_SAVE));
          var bounded = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);
          // Assert
          assertEquals(AnalysisCompletion.COMPLETE, astOnly.completion());
          assertEquals(variant.bounded() ? AnalysisCompletion.COMPLETE : AnalysisCompletion.PARTIAL, selected.completion());
          var original = astOnly.representation().ast().result().orElseThrow();
          var refused = selected.representation().ast().result().orElseThrow();
          assertEquals(AstCompletion.COMPLETE, refused.completion());
          assertEquals(original.identity(), refused.identity());
          assertEquals(original.aggregate(), refused.aggregate());
          assertEquals(variant.bounded() ? AstCompletion.COMPLETE : AstCompletion.INCOMPLETE, bounded.completion());
        }));
  }

  private static String written(String source, SyntaxNode node) {
    var origin = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
  }

  @Test
  void givenEscapedDefaultKeyword_whenProducedAndDecoded_thenLegalLabelAndExactWrittenOriginRemainComplete() throws IOException {
    // Arrange
    var source = "class Example { int subject(String value) { return switch (value) { case null, \\u0064efault -> 0; }; } }";
    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var result = AstCodec.decode(AstCodec.encode(original), original.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    var label = result.coverage().validationEvidence().writtenInventory().contexts().stream().filter(context -> context.feature().filter(feature -> feature.identifier().equals("DEFAULT_CASE_LABEL")).isPresent()).findFirst().orElseThrow();
    var origin = label.origins().getFirst();
    assertEquals("\\u0064efault", source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive())));
    var caseNode = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CASE).findFirst().orElseThrow();
    assertEquals(List.of(new SyntaxValue.SwitchLabel.Expression(0), SyntaxValue.SwitchLabel.Default.INSTANCE), caseNode.value(SyntaxValue.SwitchCase.class).labels());
  }

  @TestFactory
  Stream<DynamicTest> givenMissingSwitchAttribution_whenCopiedFactsAreAdmitted_thenCompletionAndIndependentCoverageRemainTruthful() {
    return Stream.of("guard-type", "selector-type", "pattern-scope").map(slot -> DynamicTest.dynamicTest(slot, () -> {
      // Arrange
      var original = produce("class Example { int subject(Object value) { return switch (value) { case String text when text.length() > 0 -> text.length(); default -> 0; }; } }", AstProfile.JAVA_SOURCE_EXPANDED);
      var originalInput = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()), original.diagnostics(), original.coverage());
      // Act
      var aggregate = withoutAttribution(original, slot);
      var input = JavaCoverageTestSupport.withFacts(originalInput, original.program(), Optional.of(aggregate), JavaCoverageTestSupport.emissions(originalInput));
      var result = AstResultAdmission.outcome(input, AstProfile.JAVA_SOURCE_EXPANDED, "javac-public/1").result().orElseThrow();
      var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
      // Assert
      assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
      assertEquals(original.aggregate().nodes().size(), decoded.aggregate().nodes().size());
    }));
  }

  @Test
  void givenCaseMetadataContradictingPatternAndDefaultChildren_whenConstructed_thenIntrinsicAdmissionRejectsTheContradiction() throws IOException {
    // Arrange
    var original = produce("class Example { int subject(Object value) { return switch (value) { case String text -> text.length(); default -> 0; }; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var pattern = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CASE && !node.children(SyntaxRole.LABEL).isEmpty()).findFirst().orElseThrow();
    var wrongKind = new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE, List.of(new SyntaxValue.SwitchLabel.Expression(0)));
    var missing = pattern.children().stream().filter(child -> child.role() != SyntaxRole.BODY).toList();
    var constant = produce("class Example { int subject(int value) { return switch (value) { case 1 -> 2; default -> 3; }; } }", AstProfile.JAVA_SOURCE_EXPANDED).aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CASE && !node.children(SyntaxRole.LABEL).isEmpty()).findFirst().orElseThrow();
    var combined = new SyntaxValue.SwitchCase(SyntaxValue.SwitchCaseForm.RULE, List.of(new SyntaxValue.SwitchLabel.Expression(0), SyntaxValue.SwitchLabel.Default.INSTANCE));
    // Act
    var kind = assertThrows(IllegalArgumentException.class, () -> copyCase(pattern, wrongKind, pattern.children()));
    var body = assertThrows(IllegalArgumentException.class, () -> copyCase(pattern, pattern.value(), missing));
    var nullLabel = assertThrows(IllegalArgumentException.class, () -> copyCase(constant, combined, constant.children()));
    // Assert
    assertTrue(kind.getMessage().contains("expression child"));
    assertTrue(body.getMessage().contains("BODY"));
    assertTrue(nullLabel.getMessage().contains("null literal"));
  }

  private static SyntaxNode copyCase(SyntaxNode original, SyntaxValue value, List<SyntaxChild> children) {
    return SyntaxNode.source(original.sourceFileKey(), original.structuralPath(), original.sourceKind(), original.syntaxKind(), original.type(), value, children, original.semanticReference(), original.origin().orElseThrow());
  }

  private static JavaAstAggregate withoutAttribution(AstResult original, String slot) {
    var captured = CopiedAstValues.capture(original.aggregate());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = captured.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(), replaceAttribution(source.root(), replacements, slot), source.nodes().stream().map(node -> replaceAttribution(node, replacements, slot)).toList(), source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = captured.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), replaceAttribution(body.root(), replacements, slot), body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    return AstValueAdmission.admit(new CopiedAstValues(captured.snapshot(), sources, bodies, captured.memberOrders(), captured.associations(), captured.declarationTypes()));
  }

  private static SyntaxNode replaceAttribution(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements, String slot) {
    if (replacements.containsKey(node)) return replacements.get(node);
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), replaceAttribution(child.node(), replacements, slot))).toList();
    var type = node.type();
    var value = node.value();
    var role = node.structuralPath().steps().isEmpty() ? Optional.<SyntaxRole>empty() : Optional.of(node.structuralPath().steps().getLast().role());
    if (slot.equals("guard-type") && role.equals(Optional.of(SyntaxRole.GUARD))) type = SyntaxType.Unresolved.INSTANCE;
    if (slot.equals("selector-type") && role.equals(Optional.of(SyntaxRole.SELECTOR))) type = SyntaxType.Unresolved.INSTANCE;
    if (slot.equals("pattern-scope") && node.syntaxKind() == SyntaxKind.BINDING_PATTERN) value = new SyntaxValue.PatternBinding(Optional.empty());
    var copy = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, value, children, node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, value, children, node.semanticReference());
    replacements.put(node, copy);
    return copy;
  }

  private record Selection(String method, boolean statement, List<String> labels, List<String> forms) {}
  private record ProfileCase(String method, boolean bounded) {}
}
