package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class UpdateExpressionAstTest {
  @TempDir Path directory;

  @Test
  void givenAllUpdateOperators_whenProducedAndDecoded_thenExactOperatorsRolesTypesAndBindingsSurviveClosure() throws IOException {
    // Arrange
    var source = """
        class Example {
          int field;
          Example receiver() { return this; }
          int next() { return 0; }
          int subject(int[] input) {
            int value = 8;
            value *= 2; value /= 2; value %= 3; value += 2; value -= 1;
            value <<= 1; value >>= 1; value >>>= 1; value &= 7; value ^= 3; value |= 2;
            ++value; --value; value++; value--;
            boolean flag = true; flag &= false; flag ^= true; flag |= false;
            String text = "prefix"; text += value;
            byte small = 1; small += 2;
            Integer boxed = 1; ++boxed;
            field += value; ++field; input[0] *= value; input[0]--;
            return input[next()] += receiver().field++;
          }
        }
        """;

    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var nodes = body(decoded);
    var compounds = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.COMPOUND_ASSIGNMENT).toList();
    var updates = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.UPDATE).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(original.coverage(), decoded.coverage());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
    assertEquals(List.of(SyntaxOperator.MULTIPLY_ASSIGNMENT, SyntaxOperator.DIVIDE_ASSIGNMENT, SyntaxOperator.REMAINDER_ASSIGNMENT,
        SyntaxOperator.PLUS_ASSIGNMENT, SyntaxOperator.MINUS_ASSIGNMENT, SyntaxOperator.LEFT_SHIFT_ASSIGNMENT,
        SyntaxOperator.RIGHT_SHIFT_ASSIGNMENT, SyntaxOperator.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT, SyntaxOperator.AND_ASSIGNMENT,
        SyntaxOperator.XOR_ASSIGNMENT, SyntaxOperator.OR_ASSIGNMENT),
        compounds.subList(0, 11).stream().map(node -> node.value(SyntaxValue.Operator.class).operator()).toList());
    assertEquals(List.of(SyntaxOperator.PREFIX_INCREMENT, SyntaxOperator.PREFIX_DECREMENT, SyntaxOperator.POSTFIX_INCREMENT,
        SyntaxOperator.POSTFIX_DECREMENT), updates.subList(0, 4).stream().map(node -> node.value(SyntaxValue.Operator.class).operator()).toList());
    for (var node : compounds) {
      assertEquals(List.of(SyntaxRole.LOCATION, SyntaxRole.RIGHT_OPERAND), node.children().stream().map(SyntaxChild::role).toList());
      assertEquals(node.sourceKind().name(), node.value(SyntaxValue.Operator.class).operator().name());
      assertTrue(node.type() instanceof SyntaxType.Resolved);
      assertEquals(node.requiredChild(SyntaxRole.LOCATION).type(), node.type());
      assertFalse(node.children().stream().anyMatch(child -> child.role() == SyntaxRole.VALUE));
    }
    for (var node : updates) {
      assertEquals(List.of(SyntaxRole.OPERAND), node.children().stream().map(SyntaxChild::role).toList());
      assertEquals(node.sourceKind().name(), node.value(SyntaxValue.Operator.class).operator().name());
      assertTrue(node.type() instanceof SyntaxType.Resolved);
    }
    var valueKey = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local && local.name().equals("value"))
        .findFirst().orElseThrow().semanticReference().orElseThrow().symbolKey();
    assertTrue(compounds.subList(0, 11).stream()
        .allMatch(node -> node.requiredChild(SyntaxRole.LOCATION).semanticReference().orElseThrow().symbolKey().equals(valueKey)));
    assertEquals(JavaTypeCategory.DECLARED, ((SyntaxType.Resolved) compounds.get(14).type()).identity().category());
    assertEquals("java.lang.String", ((SyntaxType.Resolved) compounds.get(14).type()).identity().displayName());
    assertEquals(JavaTypeCategory.BYTE, ((SyntaxType.Resolved) compounds.get(15).type()).identity().category());
    var nested = compounds.getLast();
    assertEquals(SyntaxKind.ARRAY_ACCESS, nested.requiredChild(SyntaxRole.LOCATION).syntaxKind());
    assertEquals(SyntaxKind.UPDATE, nested.requiredChild(SyntaxRole.RIGHT_OPERAND).syntaxKind());
    assertEquals(SyntaxKind.FIELD_ACCESS, nested.requiredChild(SyntaxRole.RIGHT_OPERAND).requiredChild(SyntaxRole.OPERAND).syntaxKind());
    assertEquals(List.of("next", "receiver"), nested.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .map(node -> node.value(SyntaxValue.Invocation.class).name()).toList());
    assertEquals(nested.descendantsAndSelf().size(), nested.descendantsAndSelf().stream().map(SyntaxNode::nodeId).distinct().count());
  }

  @Test
  void givenAnnotatedIntersectionCast_whenProducedAndDecoded_thenWrittenBoundsAndOperandRemainOrdered() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE) @interface Use { int value(); }
        interface First {} interface Second {} interface Third {}
        class Example {
          Object subject(Object value) { return (@Use(1) First & @Use(2) Second & @Use(3) Third) value; }
        }
        """;

    // Act
    var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var cast = body(decoded).stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
    var intersection = cast.requiredChild(SyntaxRole.TYPE_OPERAND);
    var bounds = intersection.children(SyntaxRole.INTERSECTION_BOUND);

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(JavaSourceKind.INTERSECTION_TYPE, intersection.sourceKind());
    assertEquals(List.of(0, 1, 2), intersection.children().stream().map(SyntaxChild::position).toList());
    assertEquals(List.of("First", "Second", "Third"), bounds.stream()
        .map(bound -> bound.requiredChild(SyntaxRole.UNDERLYING_TYPE).value(SyntaxValue.SourceSymbol.class).name()).toList());
    assertEquals(List.of("1", "2", "3"), bounds.stream().map(bound -> bound.requiredChild(SyntaxRole.ANNOTATION)
        .requiredChild(SyntaxRole.ANNOTATION_ARGUMENT).requiredChild(SyntaxRole.VALUE).value(SyntaxValue.Literal.class).text()).toList());
    assertEquals(List.of("type:First", "type:Second", "type:Third"), bounds.stream().map(bound -> bound.requiredChild(SyntaxRole.UNDERLYING_TYPE)
        .semanticReference().orElseThrow().symbolKey().orElseThrow()).toList());
    assertEquals("value", cast.requiredChild(SyntaxRole.EXPRESSION).value(SyntaxValue.SourceSymbol.class).name());
    assertEquals(JavaTypeCategory.INTERSECTION, ((SyntaxType.Resolved) cast.type()).identity().category());
    assertTrue(cast.semanticReference().isEmpty());
    assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
  }

  @Test
  void givenExpandedUpdateAndIntersectionForms_whenBoundedProfileIsRequested_thenBoundedQualificationRemainsIncomplete() throws IOException {
    // Arrange
    var source = "interface First {} interface Second {} class Example { Object subject(int value, Object input) { value++; return (First & Second) input; } }";

    // Act
    var expanded = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
    var bounded = produce(source, AstProfile.JAVA_SOURCE_BOUNDED);

    // Assert
    assertEquals(AstCompletion.COMPLETE, expanded.completion(), expanded.diagnostics().toString());
    assertEquals(AstCompletion.INCOMPLETE, bounded.completion());
    assertEquals(expanded.aggregate(), bounded.aggregate());
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidUpdatesOrCastBounds_whenProduced_thenCompilerErrorsPreventCompleteSource() {
    return Stream.of("1++;", "(1 + 2) += 3;", "final int value = 1; value++;", "boolean value = true; ++value;",
        "Object value = (String & Integer) null;", "Object value = (Runnable & Runnable) null;", "missing++;",
        "Object value = (Missing & Runnable) null;")
        .map(statement -> DynamicTest.dynamicTest(statement, () -> {
          // Arrange
          var source = "class Example { void subject() { " + statement + " } }";
          // Act
          var result = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          // Assert
          assertNotEquals(AstCompletion.COMPLETE, result.completion());
          assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError() && diagnostic.stage() == DiagnosticStage.FRONTEND
              && diagnostic.code().startsWith("compiler.err.")), result.diagnostics().toString());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenLegalCastVariants_whenProducedAndDecoded_thenTypesBoundsAndOrdinaryOperatorsRemainComplete() {
    return Stream.of("return (int) 3L;", "return (Number & Runnable) input;", "return (First<String> & Second) input;",
        "return (First<?> & Second) input;", "return (First<? extends Number> & Second) input;", "return (First<? super String> & Second) input;",
        "return (First<?>) input;", "return (First<? extends Number>) input;", "return (First<? super String>) input;",
        "return ((First<String> & Second) input);", "int value=3; (value) += 2; ++(value); return -value + ~value;")
        .map(statement -> DynamicTest.dynamicTest(statement, () -> {
          // Arrange
          var source = "interface First<T> {} interface Second {} class Example { Object subject(Object input) { " + statement + " } }";
          // Act
          var original = produce(source, AstProfile.JAVA_SOURCE_EXPANDED);
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
          assertEquals(original.aggregate(), decoded.aggregate());
          assertArrayEquals(AstCodec.encode(original), AstCodec.encode(decoded));
        }));
  }

  @Test
  void givenCompleteUpdates_whenCfgIsRequested_thenTruthfulRefusalPreservesSourceBytes() throws IOException {
    // Arrange
    var result = produce("class Example { int subject(int value) { value += 2; return value++; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var bytes = AstCodec.encode(result);
    // Act
    var cfg = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(MethodCfgState.UNSUPPORTED, cfg.methods().getFirst().state());
    assertArrayEquals(bytes, AstCodec.encode(result));
  }

  @Test
  void givenCompleteIntersectionCast_whenBoundedCfgIsRequested_thenExistingCastEffectsPreserveSourceBoundsAndBytes() throws IOException {
    // Arrange
    var result = produce("interface First {} interface Second {} class Example { Object subject(Object value) { return (First & Second) value; } }",
        AstProfile.JAVA_SOURCE_EXPANDED);
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var bytes = AstCodec.encode(result);
    // Act
    var cfg = CfgDerivers.standard().derive(result, new CfgRequest(result.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED));
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(MethodCfgState.AVAILABLE, cfg.methods().getFirst().state());
    assertTrue(cfg.methods().getFirst().graph().orElseThrow().operationNodes().stream()
        .anyMatch(node -> node.kind() == OperationKind.CAST && node.possibleExceptions().contains("java.lang.ClassCastException")));
    assertArrayEquals(bytes, AstCodec.encode(result));
  }

  @Test
  void givenCopiedOperatorShape_whenOperandIsMissingOrOperatorContradictsSource_thenIntrinsicConstructionRejectsIt() throws IOException {
    // Arrange
    var result = produce("class Example { int subject(int value) { value += 2; return value++; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var update = body(result).stream().filter(node -> node.syntaxKind() == SyntaxKind.UPDATE).findFirst().orElseThrow();
    var compound = body(result).stream().filter(node -> node.syntaxKind() == SyntaxKind.COMPOUND_ASSIGNMENT).findFirst().orElseThrow();
    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> replace(update, update.value(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> replace(compound, compound.value(),
        List.of(new SyntaxChild(SyntaxRole.LOCATION, 0, compound.requiredChild(SyntaxRole.LOCATION)))));
    assertThrows(IllegalArgumentException.class, () -> replace(update, new SyntaxValue.Operator(SyntaxOperator.PREFIX_INCREMENT), update.children()));
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile)
        .result().orElseThrow();
  }

  @TestFactory
  Stream<DynamicTest> givenCopiedCastTypesWithTheSameErasure_whenBoundsOrArgumentsDiffer_thenPublicConstructionRejectsTheContradiction() {
    return Stream.of(List.of("First<String> & Second", "First<String> & Third"),
        List.of("First<String> & Second", "First<Integer> & Second"),
        List.of("First<String> & Second", "First<String> & Second & Third"),
        List.of("First<String> & Second & Third", "First<String> & Third & Second"),
        List.of("First<String>", "First<Integer>"))
        .map(types -> DynamicTest.dynamicTest(types.toString(), () -> {
          // Arrange
          var result = produce("interface First<T> {} interface Second {} interface Third {} class Example { "
              + "Object subject(Object input) { return (" + types.getFirst() + ") input; } "
              + "Object alternate(Object input) { return (" + types.getLast() + ") input; } }", AstProfile.JAVA_SOURCE_EXPANDED);
          var original = body(result).stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
          var method = result.program().methods().stream().filter(value -> value.name().equals("alternate")).findFirst().orElseThrow();
          var other = result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
              .filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();

          // Act
          var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(original.sourceFileKey(), original.structuralPath(),
              original.sourceKind(), original.syntaxKind(), other.type(), original.value(), original.children(), original.semanticReference(),
              original.origin().orElseThrow()));

          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
          assertTrue(failure.getMessage().contains("written body type"), failure.toString());
          assertArrayEquals(AstCodec.encode(result), AstCodec.encode(AstCodec.decode(AstCodec.encode(result), result.identity())));
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenCopiedParameterizedCastWithAConvertedWildcard_whenConstructed_thenPermittedCapturesRemainDistinctAndContradictoryLowerBoundsReject() {
    return Stream.of("?", "? extends Number", "? super String").map(argument -> DynamicTest.dynamicTest(argument, () -> {
    // Arrange
    var result = produce("interface First<T> {} class Example { Object subject(Object input) { return (First<" + argument + ">) input; } }",
        AstProfile.JAVA_SOURCE_EXPANDED);
    var nodes = body(result);
    var cast = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_CAST).findFirst().orElseThrow();
    var written = (SyntaxType.Resolved) cast.requiredChild(SyntaxRole.TYPE_OPERAND).type();
    var target = (JavaTypeStructure.Declared) written.structure().orElseThrow();
    var object = ((SyntaxType.Resolved) cast.requiredChild(SyntaxRole.EXPRESSION).type()).structure().orElseThrow();
    var wildcard = (JavaTypeStructure.Wildcard) target.arguments().getFirst();
    var upper = wildcard.kind() == JavaTypeStructure.Wildcard.BoundKind.EXTENDS ? wildcard.bound().orElseThrow() : object;
    var lower = wildcard.kind() == JavaTypeStructure.Wildcard.BoundKind.SUPER ? wildcard.bound() : Optional.<JavaTypeStructure>empty();
    var capture = new JavaTypeStructure.Captured(0, upper, lower);
    var structure = new JavaTypeStructure.Declared(target.declarationKey(), target.lexicalOwnerKey(), target.enclosingType(), List.of(capture));
    var converted = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "First<capture>", written.identity().erasedName()),
        Optional.of(structure));
    var contradictoryLower = lower.isPresent() ? Optional.<JavaTypeStructure>empty() : Optional.of(object);
    var contradictoryStructure = new JavaTypeStructure.Declared(target.declarationKey(), target.lexicalOwnerKey(), target.enclosingType(),
        List.of(new JavaTypeStructure.Captured(0, upper, contradictoryLower)));
    var contradictory = new SyntaxType.Resolved(converted.identity(), Optional.of(contradictoryStructure));

    // Act
    var supplied = SyntaxNode.source(cast.sourceFileKey(), cast.structuralPath(), cast.sourceKind(), cast.syntaxKind(), converted, cast.value(),
        cast.children(), cast.semanticReference(), cast.origin().orElseThrow());
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(cast.sourceFileKey(), cast.structuralPath(), cast.sourceKind(),
        cast.syntaxKind(), contradictory, cast.value(), cast.children(), cast.semanticReference(), cast.origin().orElseThrow()));

    // Assert
    assertEquals(converted, supplied.type());
    assertEquals(written, supplied.requiredChild(SyntaxRole.TYPE_OPERAND).type());
    assertNotEquals(written.structure(), converted.structure());
    assertEquals(AstCompletion.COMPLETE, result.completion());
    assertTrue(failure.getMessage().contains("written body type"), failure.toString());
    }));
  }

  @Test
  void givenCopiedUpdateTypes_whenResultHasNoTypeOrContradictsItsLocation_thenIntrinsicConstructionRejectsTheSuppliedValue() throws IOException {
    // Arrange
    var result = produce("class Example { int subject(int value) { long other=1L; return value++; } }", AstProfile.JAVA_SOURCE_EXPANDED);
    var nodes = body(result);
    var update = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.UPDATE).findFirst().orElseThrow();
    var differentType = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).findFirst().orElseThrow().type();
    // Act and Assert
    assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(update.sourceFileKey(), update.structuralPath(), update.sourceKind(),
        update.syntaxKind(), differentType, update.value(), update.children(), Optional.empty(), update.origin().orElseThrow()));
    assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(update.sourceFileKey(), update.structuralPath(), update.sourceKind(),
        update.syntaxKind(), SyntaxType.NotApplicable.INSTANCE, update.value(), update.children(), Optional.empty(), update.origin().orElseThrow()));
  }

  private static List<SyntaxNode> body(AstResult result) {
    var method = result.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    return result.body(method.methodKey()).orElseThrow().root().descendantsAndSelf();
  }

  private static SyntaxNode replace(SyntaxNode node, SyntaxValue value, List<SyntaxChild> children) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value, children,
        Optional.empty(), node.origin().orElseThrow());
  }
}
