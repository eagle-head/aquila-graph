package org.aquilagraph.frontend.internal;

import org.aquilagraph.source.admission.CoverageAdmission;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.identity.api.StableId;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CoverageAnnotationValue;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.aquilagraph.source.api.CoverageExecutableType;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoverageSourceFact;
import org.aquilagraph.source.api.CoverageTypeStructure;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.UnmatchedSourceFact;
import org.aquilagraph.source.admission.CoverageActualFacts;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.AggregateFixture;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.PackageQualifierEvidence;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class JavaCoverageAdmissionTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @TestFactory
  Stream<DynamicTest> givenIndependentlyStatedSourceFields_whenOneNeutralFieldChangesWithTheSameReference_thenInputRejectsTheFalseValue() {
    return sourceFieldContrasts().map(contrast -> DynamicTest.dynamicTest(contrast.tag() + "." + contrast.field(), () -> assertSourceFieldRejected(contrast)));
  }

  private static void assertSourceFieldRejected(SourceFieldContrast contrast) throws IOException {
    // Arrange
    var original = analyze("""
        public class Example<T> {
          private static int count;
          public int value(final int parameter) {
            final int local = 2;
            return parameter + local;
          }
        }
        """);
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var actual = CoverageActualFacts.capture(original.program(), original.aggregate()).stream()
        .filter(fact -> fact.value() instanceof CoverageFactValue.Source source && source.value().value().tag() == contrast.tag())
        .findFirst().orElseThrow();
    var source = ((CoverageFactValue.Source) actual.value()).value();
    var fields = source.value().fields();
    assertEquals(contrast.expected(), fields.stream().filter(field -> field.name().equals(contrast.field())).findFirst().orElseThrow().value(), contrast.toString());
    var selected = evidence.outcomes().stream().filter(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FACTS
        && outcome.support().facts().contains(actual)).findFirst().orElseThrow();
    var alteredFields = fields.stream().map(field -> field.name().equals(contrast.field())
        ? new CoverageEvidenceValue.Field(field.name(), contrast.forged()) : field).toList();
    var changed = new CoverageSourceFact(source.structuralPath(), source.sourceKind(), source.syntaxKind(), source.type(),
        new CoverageEvidenceValue.Structure(source.value().tag(), alteredFields), source.children(), source.semanticReference(), source.sourceAssociation());
    var claimed = new CoverageFactReference(actual.kind(), actual.identity(), actual.sourceFileKey(), new CoverageFactValue.Source(changed), actual.origin());
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(selected)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
            new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.FACTS, outcome.support().facts().stream()
                .map(fact -> fact.equals(actual) ? claimed : fact).toList(), Optional.empty(), Optional.empty(), Optional.empty()), outcome.emissions())
        : outcome).toList();
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(),
        original.aggregate(), original.diagnostics(), result), contrast.toString());

    // Assert
    assertEquals("Claimed positive coverage support must match an exact actual owned fact value", failure.getMessage(), contrast.toString());
  }

  private static Stream<SourceFieldContrast> sourceFieldContrasts() {
    var publicFlags = new CoverageEvidenceValue.Sequence(List.of(new CoverageEvidenceValue.Named("PUBLIC")));
    var finalFlags = new CoverageEvidenceValue.Sequence(List.of(new CoverageEvidenceValue.Named("FINAL")));
    var emptyFlags = new CoverageEvidenceValue.Sequence(List.of());
    var intType = new CoverageEvidenceValue.TypeIdentity(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"));
    var longType = new CoverageEvidenceValue.TypeIdentity(new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long"));
    return Stream.of(
        textContrast(CoverageEvidenceValue.Tag.TYPE_DECLARATION, "name", "Example", "Other"),
        new SourceFieldContrast(CoverageEvidenceValue.Tag.TYPE_DECLARATION, "writtenModifiers", publicFlags, emptyFlags),
        new SourceFieldContrast(CoverageEvidenceValue.Tag.TYPE_DECLARATION, "effectiveModifiers", publicFlags, emptyFlags),
        textContrast(CoverageEvidenceValue.Tag.VARIABLE_DECLARATION, "name", "count", "other"),
        new SourceFieldContrast(CoverageEvidenceValue.Tag.VARIABLE_DECLARATION, "declaredType", intType, longType),
        countContrast(CoverageEvidenceValue.Tag.VARIABLE_DECLARATION, "annotations"),
        flagContrast(CoverageEvidenceValue.Tag.VARIABLE_DECLARATION, "type", true),
        flagContrast(CoverageEvidenceValue.Tag.VARIABLE_DECLARATION, "initializer", false),
        namedContrast(CoverageEvidenceValue.Tag.VARIABLE_DECLARATION, "kind", "FIELD", "ENUM_CONSTANT"),
        textContrast(CoverageEvidenceValue.Tag.EXECUTABLE_DECLARATION, "name", "value", "other"),
        namedContrast(CoverageEvidenceValue.Tag.EXECUTABLE_DECLARATION, "kind", "METHOD", "CONSTRUCTOR"),
        new SourceFieldContrast(CoverageEvidenceValue.Tag.EXECUTABLE_DECLARATION, "writtenModifiers", publicFlags, emptyFlags),
        new SourceFieldContrast(CoverageEvidenceValue.Tag.EXECUTABLE_DECLARATION, "effectiveModifiers", publicFlags, emptyFlags),
        textContrast(CoverageEvidenceValue.Tag.PARAMETER, "name", "parameter", "other"),
        countContrast(CoverageEvidenceValue.Tag.PARAMETER, "annotations"),
        flagContrast(CoverageEvidenceValue.Tag.PARAMETER, "type", true),
        flagContrast(CoverageEvidenceValue.Tag.PARAMETER, "varArgs", false),
        new SourceFieldContrast(CoverageEvidenceValue.Tag.PARAMETER, "writtenModifiers", finalFlags, emptyFlags),
        new SourceFieldContrast(CoverageEvidenceValue.Tag.PARAMETER, "effectiveModifiers", finalFlags, emptyFlags),
        textContrast(CoverageEvidenceValue.Tag.LOCAL_DECLARATION, "name", "local", "other"),
        new SourceFieldContrast(CoverageEvidenceValue.Tag.LOCAL_DECLARATION, "writtenModifiers",
            new CoverageEvidenceValue.Sequence(List.of(new CoverageEvidenceValue.Text("final"))), emptyFlags),
        textContrast(CoverageEvidenceValue.Tag.TYPE_PARAMETER, "name", "T", "U"),
        countContrast(CoverageEvidenceValue.Tag.TYPE_PARAMETER, "ordinal"),
        countContrast(CoverageEvidenceValue.Tag.TYPE_PARAMETER, "bounds"),
        namedContrast(CoverageEvidenceValue.Tag.OPERATOR, "operator", "PLUS", "MINUS"));
  }

  private static SourceFieldContrast textContrast(CoverageEvidenceValue.Tag tag, String field, String expected, String forged) {
    return new SourceFieldContrast(tag, field, new CoverageEvidenceValue.Text(expected), new CoverageEvidenceValue.Text(forged));
  }

  private static SourceFieldContrast namedContrast(CoverageEvidenceValue.Tag tag, String field, String expected, String forged) {
    return new SourceFieldContrast(tag, field, new CoverageEvidenceValue.Named(expected), new CoverageEvidenceValue.Named(forged));
  }

  private static SourceFieldContrast countContrast(CoverageEvidenceValue.Tag tag, String field) {
    return new SourceFieldContrast(tag, field, new CoverageEvidenceValue.Integral(0), new CoverageEvidenceValue.Integral(1));
  }

  private static SourceFieldContrast flagContrast(CoverageEvidenceValue.Tag tag, String field, boolean expected) {
    return new SourceFieldContrast(tag, field, new CoverageEvidenceValue.Flag(expected), new CoverageEvidenceValue.Flag(!expected));
  }

  private record SourceFieldContrast(CoverageEvidenceValue.Tag tag, String field, CoverageEvidenceValue expected, CoverageEvidenceValue forged) {
  }

  @TestFactory
  Stream<DynamicTest> givenExactCompilerAnnotationItems_whenAnOuterIdentityProvenanceOrComponentIsForged_thenInputRejectsTheFalseItem() {
    return Stream.of("TypeElement::Element#getAnnotationMirrors()", "ExecutableElement::Element#getAnnotationMirrors()",
        "ExecutableElement::ExecutableElement#getDefaultValue()").flatMap(role -> Arrays.stream(OuterItemField.values())
            .map(field -> DynamicTest.dynamicTest(role + "/" + field, () -> assertOuterItemRejected(role, field))));
  }

  private static void assertOuterItemRejected(String role, OuterItemField field) throws IOException {
    // Arrange
    var original = analyze("@A(7) class Example { @A(7) void value() {} } @interface A { int value() default 7; }");
    var selected = original.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals(role) && !outcome.encounteredValue().orElseThrow().items().isEmpty())
        .findFirst().orElseThrow();
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    var item = selected.encounteredValue().orElseThrow().items().getFirst();
    assertEquals("item:0", item.identity());
    assertEquals(CoveragePropertyValue.Provenance.COMPILER_DERIVED, item.provenance());
    var altered = new CoveragePropertyValue.Item(field == OuterItemField.IDENTITY ? "foreign-item:99" : item.identity(),
        field == OuterItemField.VALUE_TEXT ? "false semantic text" : item.value(),
        field == OuterItemField.PROVENANCE ? CoveragePropertyValue.Provenance.WRITTEN : item.provenance(),
        field == OuterItemField.EXECUTABLE_COMPONENTS
            ? Optional.of(new CoverageExecutableType(List.of(), List.of(), new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), List.of(), Optional.empty()))
            : item.executableType(),
        field == OuterItemField.TYPE_IDENTITY ? Optional.of(new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long")) : item.typeIdentity(),
        field == OuterItemField.MODIFIER_FLAGS ? Optional.of(List.of("private")) : item.modifierFlags(), item.annotationValue(),
        field == OuterItemField.TYPE_SHAPE ? Optional.of(new CoverageTypeStructure.Simple(JavaTypeCategory.LONG)) : item.typeStructure());
    var result = claimedValue(original, selected, new CoveragePropertyValue(selected.encounteredValue().orElseThrow().state(), List.of(altered)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(),
        original.aggregate(), original.diagnostics(), result), role + '/' + field);

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage(), role + '/' + field);
  }

  private enum OuterItemField {
    IDENTITY, VALUE_TEXT, PROVENANCE, EXECUTABLE_COMPONENTS, TYPE_IDENTITY, MODIFIER_FLAGS, TYPE_SHAPE
  }

  @TestFactory
  Stream<DynamicTest> givenActualMethodTypeParameterBounds_whenOneVariableIdentityFieldIsForged_thenTheFalseValueIsRejected() {
    return genericFixtures().flatMap(fixture -> Arrays.stream(GenericIdentityField.values())
        .map(field -> DynamicTest.dynamicTest(fixture.name() + '/' + field, () -> assertGenericIdentityRejected(fixture, field))));
  }

  @TestFactory
  Stream<DynamicTest> givenActualMethodTypeParameterBounds_whenExecutableSupportIsAdmitted_thenEachVariableRetainsItsFullIdentity() {
    return genericFixtures().map(fixture -> DynamicTest.dynamicTest(fixture.name(), () -> assertGenericIdentityRetained(fixture)));
  }

  private static Stream<GenericFixture> genericFixtures() {
    return Stream.of(
        new GenericFixture("bounded-used", "class Example { <T extends Number> T value(T input) { return input; } }", "java.lang.Number", 1),
        new GenericFixture("bounded-unused", "class Example { <T extends Number> void value() {} }", "java.lang.Number", 1),
        new GenericFixture("unbounded-used", "class Example { <T> T value(T input) { return input; } }", "java.lang.Object", 0),
        new GenericFixture("unbounded-unused", "class Example { <T> void value() {} }", "java.lang.Object", 0),
        new GenericFixture("class-first-intersection", "import java.io.Serializable; class Example { <T extends Number & Serializable> void value() {} }", "java.lang.Number", 2),
        new GenericFixture("first-interface", "import java.io.Serializable; class Example { <T extends Runnable & Serializable> void value() {} }", "java.lang.Runnable", 2),
        new GenericFixture("reversed-first-interface", "import java.io.Serializable; class Example { <T extends Serializable & Runnable> void value() {} }", "java.io.Serializable", 2),
        new GenericFixture("variable-bound", "class Example { <U extends Number, T extends U> void value() {} }", "java.lang.Number", 1));
  }

  private static void assertGenericIdentityRejected(GenericFixture fixture, GenericIdentityField field) throws IOException {
    // Arrange
    var original = analyze(fixture.source());
    var selected = genericExecutableOutcome(original);
    var item = selected.encounteredValue().orElseThrow().items().getFirst();
    var copied = item.executableType().orElseThrow();
    var variable = copied.variables().stream().filter(value -> value.type().displayName().equals("T")).findFirst().orElseThrow();
    assertEquals(fixture.erasedName(), variable.type().erasedName());
    var forgedType = new JavaTypeIdentity(field == GenericIdentityField.CATEGORY ? JavaTypeCategory.DECLARED : variable.type().category(),
        field == GenericIdentityField.NAME ? "Foreign" : variable.type().displayName(),
        field == GenericIdentityField.ERASURE ? "java.lang.String" : variable.type().erasedName());
    if (field == GenericIdentityField.CATEGORY) {
      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> new CoverageExecutableType.Variable(variable.declarationKey(), forgedType));

      // Assert
      assertEquals("Executable variables require canonical declaration identities and type-variable values", failure.getMessage());
      return;
    }
    var forged = new CoverageExecutableType.Variable(field == GenericIdentityField.KEY ? variable.declarationKey() + ":foreign" : variable.declarationKey(), forgedType);
    var variables = copied.variables().stream().map(value -> value.equals(variable) ? forged : value).toList();
    var changed = new CoverageExecutableType(variables, copied.parameters(), copied.returnType(), copied.thrownTypes(), copied.receiverType());
    var altered = new CoveragePropertyValue.Item(item.identity(), item.value(), item.provenance(), Optional.of(changed), item.typeIdentity(),
        item.modifierFlags(), item.annotationValue(), item.typeStructure());
    var result = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR, List.of(altered)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result), fixture.name() + '/' + field);

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
  }

  private static void assertGenericIdentityRetained(GenericFixture fixture) throws IOException {
    // Arrange
    var input = analyze(fixture.source());

    // Act
    var selected = genericExecutableOutcome(input);
    var variable = selected.encounteredValue().orElseThrow().items().getFirst().executableType().orElseThrow().variables().stream()
        .filter(value -> value.type().displayName().equals("T")).findFirst().orElseThrow();
    var node = input.aggregate().orElseThrow().nodes().stream().filter(value -> value.value() instanceof SyntaxValue.TypeParameterDeclaration parameter
        && parameter.name().equals("T")).findFirst().orElseThrow();
    var parameter = (SyntaxValue.TypeParameterDeclaration) node.value();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    assertEquals(new JavaTypeIdentity(JavaTypeCategory.TYPE_VARIABLE, "T", fixture.erasedName()), variable.type());
    assertEquals(parameter.declarationKey(), variable.declarationKey());
    assertEquals(fixture.writtenBounds(), node.children(SyntaxRole.TYPE_BOUND).size());
    if (fixture.writtenBounds() == 0) {
      assertEquals(new JavaTypeStructure.Declared("type:java.lang.Object", Optional.empty(), Optional.empty(), List.of()), parameter.upperBound());
    } else {
      assertEquals(fixture.erasedName(), ((SyntaxType.Resolved) node.children(SyntaxRole.TYPE_BOUND).getFirst().type()).identity().erasedName());
    }
  }

  private static FeatureContextOutcome genericExecutableOutcome(SyntaxAnalysisInput input) {
    return input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(value -> value.address().role().equals("ExecutableElement::ExecutableElement#asType()"))
        .filter(value -> value.encounteredValue().orElseThrow().items().getFirst().executableType().orElseThrow().variables().stream()
            .anyMatch(variable -> variable.type().displayName().equals("T")))
        .findFirst().orElseThrow();
  }

  private enum GenericIdentityField {
    CATEGORY, NAME, KEY, ERASURE
  }

  private record GenericFixture(String name, String source, String erasedName, int writtenBounds) {
  }

  @TestFactory
  Stream<DynamicTest> givenTypedAnnotationDefaults_whenTypedValueAndCanonicalTextAreForgedTogether_thenActualSourceFactsRejectTheContradiction() {
    return annotationSemanticContrasts().map(contrast -> DynamicTest.dynamicTest(contrast.name(), () -> assertAnnotationSemanticRejected(contrast)));
  }

  private static Stream<AnnotationSemanticContrast> annotationSemanticContrasts() {
    var integer = new CoverageAnnotationValue.Scalar(CoverageAnnotationValue.Scalar.Kind.INT, "7");
    var wrongInteger = new CoverageAnnotationValue.Scalar(CoverageAnnotationValue.Scalar.Kind.INT, "8");
    var enumValue = new CoverageAnnotationValue.EnumConstant("type:java.lang.annotation.RetentionPolicy#enum:RUNTIME", "type:java.lang.annotation.RetentionPolicy");
    var enumSource = "import java.lang.annotation.RetentionPolicy; class Example {} @interface A { RetentionPolicy value() default RetentionPolicy.RUNTIME; }";
    var declared = new CoverageAnnotationValue.TypeOperand(JavaTypeCategory.DECLARED, Optional.of("type:Outer.Inner"), Optional.of("type:Outer"), Optional.empty(), List.of());
    var declaredSource = "class Example {} class Outer { static class Inner {} } @interface A { Class<?> value() default Outer.Inner.class; }";
    var primitive = new CoverageAnnotationValue.TypeOperand(JavaTypeCategory.INT, Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    var arrayOperand = new CoverageAnnotationValue.TypeOperand(JavaTypeCategory.ARRAY, Optional.empty(), Optional.empty(), Optional.empty(), List.of(primitive));
    var nested = new CoverageAnnotationValue.Annotation("type:N", List.of(new CoverageAnnotationValue.Entry("type:N#value()", integer)), CoveragePropertyValue.Provenance.WRITTEN);
    var nestedSource = "class Example {} @interface N { int value(); } @interface A { N value() default @N(7); }";
    var first = new CoverageAnnotationValue.Scalar(CoverageAnnotationValue.Scalar.Kind.INT, "1");
    var second = new CoverageAnnotationValue.Scalar(CoverageAnnotationValue.Scalar.Kind.INT, "2");
    var array = new CoverageAnnotationValue.Array(List.of(first, first, second));
    var arraySource = "class Example {} @interface A { int[] value() default {1, 1, 2}; }";
    return Stream.concat(Stream.of(
        scalarContrast("boolean-kind", "boolean", "true", CoverageAnnotationValue.Scalar.Kind.BOOLEAN, "true"),
        scalarContrast("byte-kind", "byte", "7", CoverageAnnotationValue.Scalar.Kind.BYTE, "7"),
        scalarContrast("short-kind", "short", "7", CoverageAnnotationValue.Scalar.Kind.SHORT, "7"),
        scalarContrast("char-kind", "char", "'A'", CoverageAnnotationValue.Scalar.Kind.CHAR, "65"),
        scalarContrast("int-kind", "int", "7", CoverageAnnotationValue.Scalar.Kind.INT, "7"),
        scalarContrast("long-kind", "long", "7L", CoverageAnnotationValue.Scalar.Kind.LONG, "7"),
        scalarContrast("float-kind", "float", "1.5F", CoverageAnnotationValue.Scalar.Kind.FLOAT, "1.5"),
        scalarContrast("double-kind", "double", "1.5", CoverageAnnotationValue.Scalar.Kind.DOUBLE, "1.5"),
        scalarContrast("double-negative-zero", "double", "-0.0", CoverageAnnotationValue.Scalar.Kind.DOUBLE, "-0.0"),
        scalarContrast("string-framing", "String", "\"a)1:[b\"", CoverageAnnotationValue.Scalar.Kind.STRING, "a)1:[b")),
        Stream.of(
            new AnnotationSemanticContrast("enum-selected-key", enumSource, enumValue, new CoverageAnnotationValue.EnumConstant("type:java.lang.annotation.RetentionPolicy#enum:SOURCE", "type:java.lang.annotation.RetentionPolicy")),
            new AnnotationSemanticContrast("enum-owner-key", enumSource, enumValue, new CoverageAnnotationValue.EnumConstant(enumValue.declarationKey(), "type:Foreign")),
            new AnnotationSemanticContrast("class-selected-key", declaredSource, new CoverageAnnotationValue.ClassLiteral(declared),
                new CoverageAnnotationValue.ClassLiteral(new CoverageAnnotationValue.TypeOperand(JavaTypeCategory.DECLARED, Optional.of("type:Foreign.Inner"), declared.lexicalOwnerKey(), declared.enclosingType(), declared.components()))),
            new AnnotationSemanticContrast("class-lexical-owner", declaredSource, new CoverageAnnotationValue.ClassLiteral(declared),
                new CoverageAnnotationValue.ClassLiteral(new CoverageAnnotationValue.TypeOperand(declared.category(), declared.declarationKey(), Optional.of("type:Foreign"), declared.enclosingType(), declared.components()))),
            new AnnotationSemanticContrast("class-invented-enclosing-instance", declaredSource, new CoverageAnnotationValue.ClassLiteral(declared),
                new CoverageAnnotationValue.ClassLiteral(new CoverageAnnotationValue.TypeOperand(declared.category(), declared.declarationKey(), declared.lexicalOwnerKey(), Optional.of(new CoverageAnnotationValue.TypeOperand(JavaTypeCategory.DECLARED, Optional.of("type:Outer"), Optional.empty(), Optional.empty(), List.of())), declared.components()))),
            new AnnotationSemanticContrast("class-array-component", "class Example {} @interface A { Class<?> value() default int[].class; }", new CoverageAnnotationValue.ClassLiteral(arrayOperand),
                new CoverageAnnotationValue.ClassLiteral(new CoverageAnnotationValue.TypeOperand(JavaTypeCategory.ARRAY, Optional.empty(), Optional.empty(), Optional.empty(), List.of(new CoverageAnnotationValue.TypeOperand(JavaTypeCategory.LONG, Optional.empty(), Optional.empty(), Optional.empty(), List.of()))))),
            new AnnotationSemanticContrast("nested-annotation-key", nestedSource, nested, new CoverageAnnotationValue.Annotation("type:Foreign", nested.explicitValues(), nested.provenance())),
            new AnnotationSemanticContrast("nested-element-key", nestedSource, nested, new CoverageAnnotationValue.Annotation(nested.typeKey(), List.of(new CoverageAnnotationValue.Entry("type:N#foreign()", integer)), nested.provenance())),
            new AnnotationSemanticContrast("nested-scalar-value", nestedSource, nested, new CoverageAnnotationValue.Annotation(nested.typeKey(), List.of(new CoverageAnnotationValue.Entry("type:N#value()", wrongInteger)), nested.provenance())),
            new AnnotationSemanticContrast("nested-provenance", nestedSource, nested, new CoverageAnnotationValue.Annotation(nested.typeKey(), nested.explicitValues(), CoveragePropertyValue.Provenance.COMPILER_DERIVED)),
            new AnnotationSemanticContrast("array-order", arraySource, array, new CoverageAnnotationValue.Array(List.of(second, first, first))),
            new AnnotationSemanticContrast("array-lost-repeat", arraySource, array, new CoverageAnnotationValue.Array(List.of(first, second))),
            new AnnotationSemanticContrast("array-invented-empty", arraySource, array, new CoverageAnnotationValue.Array(List.of())),
            new AnnotationSemanticContrast("array-changed-value", arraySource, array, new CoverageAnnotationValue.Array(List.of(first, first, wrongInteger)))));
  }

  private static AnnotationSemanticContrast scalarContrast(String name, String type, String literal, CoverageAnnotationValue.Scalar.Kind kind, String value) {
    return new AnnotationSemanticContrast(name, "class Example {} @interface A { " + type + " value() default " + literal + "; }",
        new CoverageAnnotationValue.Scalar(kind, value), new CoverageAnnotationValue.Scalar(kind == CoverageAnnotationValue.Scalar.Kind.LONG
            ? CoverageAnnotationValue.Scalar.Kind.INT : CoverageAnnotationValue.Scalar.Kind.LONG, value));
  }

  private static void assertAnnotationSemanticRejected(AnnotationSemanticContrast contrast) throws IOException {
    // Arrange
    var input = analyze(contrast.source());
    var selected = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("ExecutableElement::ExecutableElement#getDefaultValue()")
            && outcome.encounteredValue().orElseThrow().state() == CoveragePropertyValue.State.SCALAR).findFirst().orElseThrow();
    var item = selected.encounteredValue().orElseThrow().items().getFirst();
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    assertEquals(Optional.of(contrast.expected()), item.annotationValue());
    assertEquals(contrast.expected().canonicalText(), item.value());
    var changed = new CoveragePropertyValue.Item(item.identity(), contrast.forged().canonicalText(), item.provenance(), item.executableType(),
        item.typeIdentity(), item.modifierFlags(), Optional.of(contrast.forged()), item.typeStructure());
    var result = claimedValue(input, selected, new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR, List.of(changed)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(),
        input.aggregate(), input.diagnostics(), result), contrast.name());

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
  }

  @Test
  void givenDelimiterBearingSemanticText_whenEncoded_thenTheExactScalarCategoryAndTextRemainFramed() {
    // Arrange
    var value = new CoverageAnnotationValue.Scalar(CoverageAnnotationValue.Scalar.Kind.STRING, "a)1:[b");

    // Act
    var encoded = value.canonicalText();

    // Assert
    assertEquals("S(6:STRING6:a)1:[b)", encoded);
    assertEquals("A(0:[])", new CoverageAnnotationValue.Array(List.of()).canonicalText());
    assertEquals("N(6:type:A7:WRITTEN1:[14:type:A#value()S(3:INT1:7)])", new CoverageAnnotationValue.Annotation("type:A",
        List.of(new CoverageAnnotationValue.Entry("type:A#value()", new CoverageAnnotationValue.Scalar(CoverageAnnotationValue.Scalar.Kind.INT, "7"))),
        CoveragePropertyValue.Provenance.WRITTEN).canonicalText());
  }

  private record AnnotationSemanticContrast(String name, String source, CoverageAnnotationValue expected, CoverageAnnotationValue forged) {
  }

  @Test
  void givenARepresentedAccessorThrowingARuntimeFailure_whenItsPublicMethodIsInvoked_thenTheOriginalFailurePropagates() throws Exception {
    // Arrange
    var failure = new IllegalArgumentException("accessor failure");

    // Act
    var actual = assertThrows(IllegalArgumentException.class, () -> invokeFailingAccessor(failure));

    // Assert
    assertSame(failure, actual);
  }

  @Test
  void givenARepresentedAccessorThrowingAnError_whenItsPublicMethodIsInvoked_thenTheOriginalErrorPropagates() throws Exception {
    // Arrange
    var failure = new AssertionError("accessor error");

    // Act
    var actual = assertThrows(AssertionError.class, () -> invokeFailingAccessor(failure));

    // Assert
    assertSame(failure, actual);
  }

  private static void invokeFailingAccessor(Throwable failure) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/coverage-check/sources"),
        List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example {}")));
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      var parsed = session.parse();
      var owner = JavaCoverageTopology.owners(parsed.units(), sources, parsed.sourceEvidence()).stream().filter(value -> value.tree() instanceof ClassTree)
          .findFirst().orElseThrow();
      var accessor = JavaCoveragePropertyProjection.accessors(ClassTree.class, JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION)).stream()
          .filter(value -> value.method().getName().equals("getKind")).findFirst().orElseThrow();
      var supplied = Proxy.newProxyInstance(ClassTree.class.getClassLoader(), new Class<?>[] { ClassTree.class }, (_, _, _) -> { throw failure; });
      JavaCoveragePropertyProjection.query(accessor, supplied, owner, parsed.sourceEvidence(), Optional.empty(), JavaTranslationPolicy.legacyAnalysis());
    }
  }

  @TestFactory
  Stream<DynamicTest> givenActualWrittenModifierOwners_whenOneUnusedItemFieldIsForged_thenInputRejectsTheFalseProperty() {
    // Arrange
    var fixtures = modifierFixtures();
    // Act
    var tests = fixtures.flatMap(fixture -> Arrays.stream(PropertyItemField.values()).filter(field -> field != PropertyItemField.MODIFIER_FLAGS)
        .map(field -> DynamicTest.dynamicTest("given" + fixture.name() + "WithForged" + field
            + "_whenCopiedPropertyIsAdmitted_thenFalsePropertyIsRejected", () -> assertPropertyItemRejected(fixture, field))));
    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenActualWrittenModifierOwners_whenPropertySupportIsAdmitted_thenEmptyAndNonemptyFlagsKeepTheirExactOwner() {
    // Arrange
    var fixtures = modifierFixtures();
    // Act
    var tests = fixtures.map(fixture -> DynamicTest.dynamicTest("given" + fixture.name()
        + "_whenWrittenModifiersAreAdmitted_thenExactOwnerFlagsAreRetained", () -> assertModifierOwner(fixture)));
    // Assert
    return tests;
  }

  private static Stream<PropertyFixture> modifierFixtures() {
    return Stream.of(
        new PropertyFixture("NonSealedClass", "non-sealed class Example extends Base {} sealed class Base permits Example {}",
            "ClassTree::ClassTree#getModifiers()", List.of("non-sealed"), List.of()),
        new PropertyFixture("NonSealedInterface", "non-sealed interface Example extends Base {} sealed interface Base permits Example {}",
            "ClassTree::ClassTree#getModifiers()", List.of("non-sealed"), List.of()),
        new PropertyFixture("PublicClass", "public class Example {}", "ClassTree::ClassTree#getModifiers()", List.of("public"), List.of()),
        new PropertyFixture("EmptyClass", "class Example {}", "ClassTree::ClassTree#getModifiers()", List.of(), List.of()),
        new PropertyFixture("PublicMethod", "class Example { public void value() {} }", "MethodTree::MethodTree#getModifiers()", List.of("public"), List.of()),
        new PropertyFixture("EmptyMethod", "class Example { void value() {} }", "MethodTree::MethodTree#getModifiers()", List.of(), List.of()),
        new PropertyFixture("PrivateField", "class Example { private int field; }", "VariableTree::VariableTree#getModifiers()", List.of("private"), List.of()),
        new PropertyFixture("EmptyFormal", "class Example { void value(int parameter) {} }", "VariableTree::VariableTree#getModifiers()", List.of(), List.of()),
        new PropertyFixture("FinalFormal", "class Example { void value(final int parameter) {} }", "VariableTree::VariableTree#getModifiers()", List.of("final"), List.of()),
        new PropertyFixture("FinalLocal", "class Example { void value() { final int local = 1; } }", "VariableTree::VariableTree#getModifiers()", List.of("final"),
            List.of()),
        new PropertyFixture("FinalCatch", "class Example { void value() { try { throw new IllegalArgumentException(); } catch (final IllegalArgumentException problem) {} } }",
            "VariableTree::VariableTree#getModifiers()", List.of("final"), List.of()));
  }

  @TestFactory
  Stream<DynamicTest> givenActualExecutableTypeOrWrittenLocalType_whenOneUnusedItemFieldIsForged_thenInputRejectsTheFalseProperty() {
    var executable = new PropertyFixture("executable", "class Example { <T extends Number> T value(T input) { return input; } }",
        "ExecutableElement::ExecutableElement#asType()", List.of(), List.of());
    var local = new PropertyFixture("local-type", "class Example { void value() { int local = 1; } }", "VariableTree::VariableTree#getType()", List.of(), List.of());
    var caught = new PropertyFixture("catch-type", "class Example { void value() { try { throw new IllegalArgumentException(); } catch (IllegalArgumentException problem) {} } }",
        "VariableTree::VariableTree#getType()", List.of(), List.of());
    return Stream.of(executable, local, caught).flatMap(fixture -> Arrays.stream(PropertyItemField.values())
        .filter(field -> fixture.name().equals("executable") ? field != PropertyItemField.EXECUTABLE_TYPE : field != PropertyItemField.VALUE_TEXT)
        .map(field -> DynamicTest.dynamicTest(fixture.name() + '/' + field, () -> assertPropertyItemRejected(fixture, field))));
  }

  private static void assertModifierOwner(PropertyFixture fixture) throws IOException {
    // Arrange
    var input = analyze(fixture.source());

    // Act
    var selected = property(input, fixture.role(), CoveragePropertyValue.State.SCALAR);
    var item = selected.encounteredValue().orElseThrow().items().getFirst();

    // Assert
    assertEquals(fixture.diagnostics(), input.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    assertEquals("MODIFIERS:0", item.identity());
    assertEquals(CoveragePropertyValue.Provenance.WRITTEN, item.provenance());
    assertEquals(Optional.of(fixture.flags()), item.modifierFlags());
    assertEquals(CoveragePropertyValue.canonicalFlags(fixture.flags()), item.value());
    assertWrittenLocalTypes(input, fixture.source());
  }

  @TestFactory
  Stream<DynamicTest> givenNonSealedSemanticModifiers_whenExpectedAndEncounteredPropertiesAreForged_thenActualFactsRejectThem() {
    // Arrange
    var forged = List.of(Map.entry("EmptyModifierClaims", new CoveragePropertyValue(CoveragePropertyValue.State.EMPTY, List.of())),
        Map.entry("AbsentModifierClaims", new CoveragePropertyValue(CoveragePropertyValue.State.ABSENT, List.of())),
        Map.entry("OrdinalModifierIdentityClaims", new CoveragePropertyValue(CoveragePropertyValue.State.ITEMS,
            List.of(new CoveragePropertyValue.Item("item:0", "non-sealed", CoveragePropertyValue.Provenance.COMPILER_DERIVED)))));
    // Act
    var tests = forged.stream().map(contrast -> DynamicTest.dynamicTest("givenNonSealedClassAnd" + contrast.getKey()
        + "_whenCopiedPropertiesAreAdmitted_thenFalsePropertyIsRejected", () -> {
      // Arrange
      var input = analyze("non-sealed class Example extends Base {} sealed class Base permits Example {}");
      var selected = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
          .filter(outcome -> outcome.address().role().equals("TypeElement::Element#getModifiers()")
              && outcome.encounteredValue().orElseThrow().items().stream().anyMatch(item -> item.value().equals("non-sealed")))
          .findFirst().orElseThrow();
      assertTrue(input.diagnostics().isEmpty(), input.diagnostics().toString());
      assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> {
        var changed = claimedValue(input, selected, contrast.getValue());
        new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(), input.aggregate(), input.diagnostics(), changed);
      });
      // Assert
      assertTrue(failure.getMessage().contains("support") || failure.getMessage().contains("owner facts"), failure.toString());
    }));
    // Assert
    return tests;
  }

  private static void assertPropertyItemRejected(PropertyFixture fixture, PropertyItemField field) throws IOException {
    // Arrange
    var input = analyze(fixture.source());
    var selected = property(input, fixture.role(), CoveragePropertyValue.State.SCALAR);
    var item = selected.encounteredValue().orElseThrow().items().getFirst();
    assertEquals(fixture.diagnostics(), input.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    assertWrittenLocalTypes(input, fixture.source());
    var changed = new CoveragePropertyValue.Item(item.identity(), field == PropertyItemField.VALUE_TEXT ? "false property text" : item.value(), item.provenance(),
        field == PropertyItemField.EXECUTABLE_TYPE ? Optional.of(new CoverageExecutableType(List.of(), List.of(), new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), List.of(), Optional.empty())) : item.executableType(),
        field == PropertyItemField.TYPE_IDENTITY ? Optional.of(new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long")) : item.typeIdentity(),
        field == PropertyItemField.MODIFIER_FLAGS ? Optional.of(List.of("private")) : item.modifierFlags(),
        field == PropertyItemField.ANNOTATION_VALUE ? Optional.of(new CoverageAnnotationValue.Scalar(CoverageAnnotationValue.Scalar.Kind.INT, "7")) : item.annotationValue(),
        field == PropertyItemField.TYPE_STRUCTURE ? Optional.of(new CoverageTypeStructure.Simple(JavaTypeCategory.LONG)) : item.typeStructure());
    var result = claimedValue(input, selected, new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR, List.of(changed)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(),
        input.aggregate(), input.diagnostics(), result), fixture.name() + '/' + field);

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
  }

  private enum PropertyItemField {
    VALUE_TEXT, EXECUTABLE_TYPE, TYPE_IDENTITY, MODIFIER_FLAGS, ANNOTATION_VALUE, TYPE_STRUCTURE
  }

  private static void assertWrittenLocalTypes(SyntaxAnalysisInput input, String source) {
    for (var node : input.aggregate().orElseThrow().nodes()) {
      if (node.syntaxKind() != SyntaxKind.LOCAL_DECLARATION) {
        continue;
      }
      var expected = node.value(SyntaxValue.LocalDeclaration.class).writtenType().orElseThrow();
      assertEquals(1, node.children(SyntaxRole.DECLARED_TYPE).size());
      var child = node.children(SyntaxRole.DECLARED_TYPE).getFirst();
      assertEquals(SyntaxKind.TYPE_USE, child.syntaxKind());
      assertEquals(Optional.of(expected.origin()), child.origin());
      assertEquals(expected.type(), child.type());
      assertEquals(expected.sourceKind(), child.sourceKind());
      assertTrue(List.of("int", "String", "IllegalArgumentException", "IllegalStateException").contains(
          source.substring(Math.toIntExact(expected.origin().startOffset()), Math.toIntExact(expected.origin().endOffsetExclusive()))));
    }
  }

  private record PropertyFixture(String name, String source, String role, List<String> flags, List<String> diagnostics) {
  }

  @Test
  void givenARecordComponent_whenItsParsedModifierGetterIsSupported_thenParserFlagsAndLexicalFlagsRemainSeparate() throws IOException {
    // Arrange
    var input = analyze("record Example(int component) {}");

    // Act
    var selected = property(input, "VariableTree::VariableTree#getModifiers()", CoveragePropertyValue.State.SCALAR);
    var component = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.RecordComponentDeclaration)
        .findFirst().orElseThrow().value(SyntaxValue.RecordComponentDeclaration.class);
    var canonical = input.program().types().getFirst().recordComponents().getFirst();
    var field = input.program().types().getFirst().fields().stream().filter(value -> value.fieldKey().equals(component.counterparts().backingFieldKey()))
        .findFirst().orElseThrow();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    assertEquals(Optional.of(List.of("final", "private")), selected.encounteredValue().orElseThrow().items().getFirst().modifierFlags());
    assertTrue(component.writtenModifiers().isEmpty());
    assertTrue(canonical.writtenModifiers().isEmpty());
    assertEquals(canonical.backingFieldKey(), Optional.of(field.fieldKey()));
    assertEquals(List.of("final", "private"), field.modifiers().stream().sorted().toList());
  }

  @Test
  void givenARecordComponentWithActualCounterparts_whenItsBackingFieldOwnerIsForged_thenTheOwningDeclarationRejectsTheAssociation() throws IOException {
    // Arrange
    var input = analyze("record Example(int component) {}");
    var owner = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration)
        .findFirst().orElseThrow();
    var original = owner.children(SyntaxRole.RECORD_COMPONENT).getFirst();
    var component = original.value(SyntaxValue.RecordComponentDeclaration.class);
    var counterparts = component.counterparts();
    var changedValue = new SyntaxValue.RecordComponentDeclaration(component.name(), component.ownerKey(), component.ordinal(), component.writtenModifiers(),
        component.effectiveModifiers(), component.declaredType(), component.annotations(), component.varArgs(),
        new SyntaxValue.RecordComponentDeclaration.Counterparts("type:Foreign#field:component", counterparts.accessorKey(), counterparts.canonicalParameterKey(),
            counterparts.accessorImplicit(), counterparts.canonicalParameterDerived()));
    var changed = SyntaxNode.source(original.sourceFileKey(), original.structuralPath(), original.sourceKind(), original.syntaxKind(), original.type(),
        changedValue, original.children(), original.semanticReference(), original.origin().orElseThrow());
    var children = owner.children().stream().map(child -> child.role() == SyntaxRole.RECORD_COMPONENT
        ? new SyntaxChild(child.role(), child.position(), changed) : child).toList();

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(owner.sourceFileKey(), owner.structuralPath(), owner.sourceKind(), owner.syntaxKind(),
        owner.type(), owner.value(), children, owner.semanticReference(), owner.origin().orElseThrow()));

    // Assert
    assertEquals("Record counterpart identities conflict with the record header", failure.getMessage());
  }

  @Test
  void givenEmptyFlagsAndAParameterlessVoidMethod_whenPropertiesAreCopied_thenCanonicalTextRetainsExactEmptyListsAndAbsentReceiver() throws IOException {
    // Arrange
    var input = analyze("class Example { void value() {} }");

    // Act
    var flags = property(input, "ClassTree::ClassTree#getModifiers()", CoveragePropertyValue.State.SCALAR);
    var executable = property(input, "ExecutableElement::ExecutableElement#asType()", CoveragePropertyValue.State.SCALAR);
    var type = new CoverageExecutableType(List.of(), List.of(), new JavaTypeIdentity(JavaTypeCategory.VOID, "void", "void"), List.of(), Optional.empty());

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(FeatureContextOutcome.Mode.FACTS, flags.support().mode());
    assertEquals("M(0:[])", flags.encounteredValue().orElseThrow().items().getFirst().value());
    assertEquals("M(2:[5:final7:private])", CoveragePropertyValue.canonicalFlags(List.of("final", "private")));
    assertEquals(FeatureContextOutcome.Mode.FACTS, executable.support().mode());
    assertEquals(new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR, List.of(new CoveragePropertyValue.Item("EXECUTABLE:0",
        "X(0:[]0:[]I(4:VOID4:void4:void)0:[]O())", CoveragePropertyValue.Provenance.COMPILER_DERIVED, Optional.of(type)))), executable.encounteredValue().orElseThrow());
  }

  @TestFactory
  Stream<DynamicTest> givenPackageOrCompactUnitProperties_whenEveryOuterFieldIsCopied_thenActualSourceSpellingAndImplicitClassPresenceAreRetained() {
    return unitPropertyFixtures().map(fixture -> DynamicTest.dynamicTest(fixture.name(), () -> {
      // Arrange
      var input = analyze(fixture.source());

      // Act
      var selected = property(input, fixture.role(), fixture.state());

      // Assert
      assertEquals(fixture.diagnostics(), input.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
      assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
      assertEquals(new CoveragePropertyValue(fixture.state(), List.of(new CoveragePropertyValue.Item(fixture.identity(), fixture.text(),
          CoveragePropertyValue.Provenance.WRITTEN))), selected.encounteredValue().orElseThrow());
    }));
  }

  @Test
  void givenAQualifiedPackageNameWithWrittenCommentsAndWhitespace_whenAnalyzed_thenTheExactQualifierSubstringHasActualSupport() throws IOException {
    // Arrange
    var input = analyze("package sample /*gap*/ . nested; class Example {}");

    // Act
    var selected = property(input, "CompilationUnitTree::CompilationUnitTree#getPackageName()", CoveragePropertyValue.State.SCALAR);
    var packageNode = input.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.PACKAGE_DECLARATION);

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals("sample.nested", packageNode.value(SyntaxValue.PackageDeclaration.class).qualifiedName());
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    assertEquals(new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR, List.of(new CoveragePropertyValue.Item("MEMBER_SELECT:0",
        "sample /*gap*/ . nested", CoveragePropertyValue.Provenance.WRITTEN))), selected.encounteredValue().orElseThrow());
  }

  @TestFactory
  Stream<DynamicTest> givenActualPackageNameOrCompactTypeDeclarations_whenOneOuterItemFieldIsForged_thenInputRejectsFalseUnitSupport() {
    return unitPropertyFixtures().flatMap(fixture -> Arrays.stream(UnitItemField.values())
        .map(field -> DynamicTest.dynamicTest(fixture.name() + '/' + field, () -> assertUnitItemRejected(fixture, field))));
  }

  @Test
  void givenAQualifiedPackageNameWithUnicodeSpelling_whenAnalyzed_thenQualifierEvidenceAndSupportRetainTheExactWrittenRange() throws IOException {
    // Arrange
    var qualifier = "sam\\u0070le /*gap*/ . nested";
    var source = "package " + qualifier + "; class Example {}";

    // Act
    var input = analyze(source);
    var packageNode = input.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.PACKAGE_DECLARATION);
    var selected = property(input, "CompilationUnitTree::CompilationUnitTree#getPackageName()", CoveragePropertyValue.State.SCALAR);

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(new SyntaxValue.PackageDeclaration("sample.nested", Optional.of(new PackageQualifierEvidence(JavaSourceKind.MEMBER_SELECT,
        new SourceSpan(packageNode.sourceFileKey(), 8, 8 + qualifier.length())))), packageNode.value());
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    assertEquals(new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR, List.of(new CoveragePropertyValue.Item("MEMBER_SELECT:0", qualifier,
        CoveragePropertyValue.Provenance.WRITTEN))), selected.encounteredValue().orElseThrow());
  }

  @Test
  void givenAnActualPackageNameWithoutQualifierEvidence_whenReconciled_thenTheCapturedPropertyRemainsDiagnosedAndUnsupported() throws IOException {
    // Arrange
    var original = analyze("package sample.nested; class Example {}");
    var changed = copyPackageValue(original, new SyntaxValue.PackageDeclaration("sample.nested"));

    // Act
    var partial = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(changed),
        JavaCoverageTestSupport.emissions(original));
    var selected = property(partial, "CompilationUnitTree::CompilationUnitTree#getPackageName()", CoveragePropertyValue.State.SCALAR);

    // Assert
    assertEquals(FeatureContextOutcome.Mode.FAILED, selected.support().mode());
    assertEquals(new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR, List.of(new CoveragePropertyValue.Item("MEMBER_SELECT:0", "sample.nested",
        CoveragePropertyValue.Provenance.WRITTEN))), selected.encounteredValue().orElseThrow());
    assertEquals(List.of("frontend.missing-source-evidence"), partial.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
    assertEquals(original.languageFeatureCoverageResult().validationEvidence().writtenInventory(),
        partial.languageFeatureCoverageResult().validationEvidence().writtenInventory());
  }

  @TestFactory
  Stream<DynamicTest> givenPackageQualifierEvidenceOutsideItsActualDeclaration_whenActualFactsAreCaptured_thenForeignAndUnrelatedRangesAreRejected() {
    return Stream.of("foreign-source", "outside-declaration").map(contrast -> DynamicTest.dynamicTest(contrast, () -> {
      // Arrange
      var original = analyze("package sample; class Example {}");
      var packageNode = original.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.PACKAGE_DECLARATION);
      var origin = "foreign-source".equals(contrast) ? new SourceSpan("foreign-source", 8, 14)
          : new SourceSpan(packageNode.sourceFileKey(), 22, 29);
      var changed = copyPackageValue(original, new SyntaxValue.PackageDeclaration("sample", Optional.of(new PackageQualifierEvidence(JavaSourceKind.IDENTIFIER, origin))));

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> CoverageActualFacts.capture(original.program(), Optional.of(changed)));

      // Assert
      assertEquals("Package qualifier evidence must belong to its actual declaration and captured source", failure.getMessage());
    }));
  }

  @Test
  void givenPackageQualifierKindsAndRanges_whenInvalidValuesAreConstructed_thenOnlyNonemptyNameTreesAreAccepted() {
    // Arrange
    var range = new SourceSpan("source", 8, 14);
    var empty = new SourceSpan("source", 8, 8);

    // Act
    var wrongKind = assertThrows(IllegalArgumentException.class, () -> new PackageQualifierEvidence(JavaSourceKind.PACKAGE, range));
    var emptyRange = assertThrows(IllegalArgumentException.class, () -> new PackageQualifierEvidence(JavaSourceKind.IDENTIFIER, empty));
    var identifier = new PackageQualifierEvidence(JavaSourceKind.IDENTIFIER, range);
    var selected = new PackageQualifierEvidence(JavaSourceKind.MEMBER_SELECT, range);

    // Assert
    assertEquals("Package qualifier evidence requires a name tree and a nonempty source range", wrongKind.getMessage());
    assertEquals(wrongKind.getMessage(), emptyRange.getMessage());
    assertEquals(JavaSourceKind.IDENTIFIER, identifier.sourceKind());
    assertEquals(JavaSourceKind.MEMBER_SELECT, selected.sourceKind());
    assertEquals(range, identifier.origin());
  }

  private static JavaAstAggregate copyPackageValue(SyntaxAnalysisInput original, SyntaxValue.PackageDeclaration value) {
    var aggregate = original.aggregate().orElseThrow();
    var root = aggregate.roots().getFirst();
    var declaration = root.requiredChild(SyntaxRole.PACKAGE_DECLARATION);
    var changedPackage = SyntaxNode.source(declaration.sourceFileKey(), declaration.structuralPath(), declaration.sourceKind(), declaration.syntaxKind(),
        declaration.type(), value, declaration.children(), declaration.semanticReference(), declaration.origin().orElseThrow());
    var children = root.children().stream().map(child -> child.role() == SyntaxRole.PACKAGE_DECLARATION
        ? new SyntaxChild(child.role(), child.position(), changedPackage) : child).toList();
    var changedRoot = SyntaxNode.source(root.sourceFileKey(), root.structuralPath(), root.sourceKind(), root.syntaxKind(), root.type(), root.value(), children,
        root.semanticReference(), root.origin().orElseThrow());
    var nodes = changedRoot.descendantsAndSelf();
    var builder = AggregateFixture.builder(original.snapshot()).add(new SyntaxSourceContribution(root.sourceFileKey(), changedRoot, nodes,
        nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), aggregate.semanticEdges()));
    aggregate.declarationMemberOrders().forEach(builder::addDeclarationMemberOrder);
    aggregate.declarationTypeEvidence().forEach(builder::addDeclarationTypeEvidence);
    nodes.forEach(node -> aggregate.writtenSourceAssociation(node.nodeId()).ifPresent(builder::addWrittenSourceAssociation));
    copyMethodBodies(aggregate, builder);
    return builder.seal();
  }

  private static Stream<UnitPropertyFixture> unitPropertyFixtures() {
    return Stream.of(
        new UnitPropertyFixture("package-simple", "package sample; class Example {}", "CompilationUnitTree::CompilationUnitTree#getPackageName()",
            CoveragePropertyValue.State.SCALAR, "IDENTIFIER:0", "sample", List.of()),
        new UnitPropertyFixture("package-qualified", "package sample.nested; class Example {}", "CompilationUnitTree::CompilationUnitTree#getPackageName()",
            CoveragePropertyValue.State.SCALAR, "MEMBER_SELECT:0", "sample.nested", List.of()),
        new UnitPropertyFixture("compact-types", "void main() {}", "CompilationUnitTree::CompilationUnitTree#getTypeDecls()",
            CoveragePropertyValue.State.ITEMS, "CLASS:0", "CLASS", List.of()));
  }

  private static void assertUnitItemRejected(UnitPropertyFixture fixture, UnitItemField field) throws IOException {
    // Arrange
    var input = analyze(fixture.source());
    var selected = property(input, fixture.role(), fixture.state());
    assertEquals(fixture.diagnostics(), input.diagnostics().stream().map(AnalysisDiagnostic::code).toList());
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    var item = selected.encounteredValue().orElseThrow().items().getFirst();
    var changed = new CoveragePropertyValue.Item(field == UnitItemField.IDENTITY ? "foreign-item:0" : item.identity(),
        field == UnitItemField.VALUE_TEXT ? "false property text" : item.value(),
        field == UnitItemField.PROVENANCE ? CoveragePropertyValue.Provenance.COMPILER_DERIVED : item.provenance(),
        field == UnitItemField.EXECUTABLE_TYPE ? Optional.of(new CoverageExecutableType(List.of(), List.of(),
            new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"), List.of(), Optional.empty())) : item.executableType(),
        field == UnitItemField.TYPE_IDENTITY ? Optional.of(new JavaTypeIdentity(JavaTypeCategory.LONG, "long", "long")) : item.typeIdentity(),
        field == UnitItemField.MODIFIER_FLAGS ? Optional.of(List.of("private")) : item.modifierFlags(),
        field == UnitItemField.ANNOTATION_VALUE ? Optional.of(new CoverageAnnotationValue.Scalar(CoverageAnnotationValue.Scalar.Kind.INT, "7")) : item.annotationValue(),
        field == UnitItemField.TYPE_STRUCTURE ? Optional.of(new CoverageTypeStructure.Simple(JavaTypeCategory.LONG)) : item.typeStructure());
    var result = claimedValue(input, selected, new CoveragePropertyValue(fixture.state(), List.of(changed)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(),
        input.aggregate(), input.diagnostics(), result), fixture.name() + '/' + field);

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
  }

  private enum UnitItemField {
    IDENTITY, VALUE_TEXT, PROVENANCE, EXECUTABLE_TYPE, TYPE_IDENTITY, MODIFIER_FLAGS, ANNOTATION_VALUE, TYPE_STRUCTURE
  }

  private record UnitPropertyFixture(String name, String source, String role, CoveragePropertyValue.State state, String identity, String text,
      List<String> diagnostics) {
  }

  @Test
  void givenCallerOwnedNestedGenericTypeCollections_whenTheyChangeAfterFactoryAdmission_thenTheOptionalTypeAndArgumentsStayExact() throws IOException {
    // Arrange
    var original = analyze("import java.util.List; class Example { List<String> values; }");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var actual = CoverageActualFacts.capture(original.program(), original.aggregate()).stream()
        .filter(fact -> fact.value() instanceof CoverageFactValue.Source source && source.value().sourceKind().equals("PARAMETERIZED_TYPE"))
        .findFirst().orElseThrow();
    var source = ((CoverageFactValue.Source) actual.value()).value();
    assertEquals(CoverageEvidenceValue.Tag.TYPE_RESOLVED, source.type().tag());
    var optionalStructure = (CoverageEvidenceValue.OptionalValue) source.type().fields().stream().filter(field -> field.name().equals("structure"))
        .findFirst().orElseThrow().value();
    var declared = (CoverageEvidenceValue.Structure) optionalStructure.value().orElseThrow();
    assertEquals(CoverageEvidenceValue.Tag.TYPE_DECLARED, declared.tag());
    var arguments = (CoverageEvidenceValue.Sequence) declared.fields().stream().filter(field -> field.name().equals("arguments"))
        .findFirst().orElseThrow().value();
    assertEquals(1, arguments.values().size());
    var mutableArguments = new ArrayList<>(arguments.values());
    var copiedArguments = new CoverageEvidenceValue.Sequence(mutableArguments);
    var mutableDeclaredFields = new ArrayList<>(declared.fields().stream().map(field -> field.name().equals("arguments")
        ? new CoverageEvidenceValue.Field(field.name(), copiedArguments) : field).toList());
    var copiedDeclared = new CoverageEvidenceValue.Structure(declared.tag(), mutableDeclaredFields);
    var mutableTypeFields = new ArrayList<>(source.type().fields().stream().map(field -> field.name().equals("structure")
        ? new CoverageEvidenceValue.Field(field.name(), new CoverageEvidenceValue.OptionalValue(Optional.of(copiedDeclared))) : field).toList());
    var copiedType = new CoverageEvidenceValue.Structure(source.type().tag(), mutableTypeFields);
    var copied = new CoverageSourceFact(source.structuralPath(), source.sourceKind(), source.syntaxKind(), copiedType, source.value(),
        source.children(), source.semanticReference(), source.sourceAssociation());
    var supplied = new CoverageFactReference(actual.kind(), actual.identity(), actual.sourceFileKey(), new CoverageFactValue.Source(copied), actual.origin());
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.support().facts().contains(actual)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
            new FeatureContextOutcome.Support(outcome.support().mode(), outcome.support().facts().stream().map(fact -> fact.equals(actual) ? supplied : fact).toList(),
                outcome.support().policyContext(), outcome.support().sourceFile(), outcome.support().negativeState()), outcome.emissions()) : outcome).toList();
    assertTrue(outcomes.stream().anyMatch(outcome -> outcome.support().facts().contains(supplied)));
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    mutableArguments.clear();
    mutableDeclaredFields.clear();
    mutableTypeFields.clear();
    var retained = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), original.aggregate(), original.diagnostics(), result);

    // Assert
    assertEquals(source.type(), copiedType);
    assertEquals(original.languageFeatureCoverageResult(), retained.languageFeatureCoverageResult());
    assertThrows(UnsupportedOperationException.class, () -> copiedArguments.values().clear());
  }

  @Test
  void givenOrderedFormalParameters_whenAClaimKeepsNamesButChangesTheCanonicalKey_thenInputRejectsTheFalseSequence() throws IOException {
    // Arrange
    var original = analyze("class Example { void value(int left, int right) {} }");
    var selected = original.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("ExecutableElement::ExecutableElement#getParameters()")
            && outcome.encounteredValue().orElseThrow().items().size() == 2).findFirst().orElseThrow();
    var items = new ArrayList<>(selected.encounteredValue().orElseThrow().items());
    var first = items.getFirst();
    items.set(0, new CoveragePropertyValue.Item("foreign-parameter", first.value(), first.provenance()));
    var result = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.ITEMS, items));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Positive coverage support cannot account for unrelated actual facts", exception.getMessage());
  }

  @Test
  void givenWrittenFormalParameter_whenAClaimLabelsItCompilerDerived_thenInputRejectsTheFalseProvenance() throws IOException {
    // Arrange
    var original = analyze("class Example { void value(int input) {} }");
    var selected = original.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("ExecutableElement::ExecutableElement#getParameters()")
            && outcome.encounteredValue().orElseThrow().items().size() == 1).findFirst().orElseThrow();
    var first = selected.encounteredValue().orElseThrow().items().getFirst();
    var result = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.ITEMS,
        List.of(new CoveragePropertyValue.Item(first.identity(), first.value(), CoveragePropertyValue.Provenance.COMPILER_DERIVED))));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", exception.getMessage());
  }

  @Test
  void givenPublicMethodModifiers_whenAClaimRetainsTextButChangesCopiedFlags_thenInputRejectsTheFalseFlags() throws IOException {
    // Arrange
    var original = analyze("class Example { public void value() {} }");
    var selected = original.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("MethodTree::MethodTree#getModifiers()")
            && outcome.encounteredValue().orElseThrow().items().getFirst().modifierFlags().orElseThrow().contains("public")).findFirst().orElseThrow();
    var first = selected.encounteredValue().orElseThrow().items().getFirst();
    var result = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR,
        List.of(new CoveragePropertyValue.Item(first.identity(), first.value(), first.provenance(), Optional.empty(), Optional.empty(), Optional.of(List.of("private"))))));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", exception.getMessage());
  }

  @Test
  void givenDeclaredInterfaceType_whenAClaimKeepsDisplayButChangesErasure_thenInputRejectsFalseTypedSupport() throws IOException {
    // Arrange
    var original = analyze("class Example implements java.io.Serializable {}");
    var selected = original.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("TypeElement::TypeElement#getInterfaces()")
            && !outcome.encounteredValue().orElseThrow().items().isEmpty()).findFirst().orElseThrow();
    var first = selected.encounteredValue().orElseThrow().items().getFirst();
    var type = first.typeIdentity().orElseThrow();
    var result = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.ITEMS,
        List.of(new CoveragePropertyValue.Item(first.identity(), first.value(), first.provenance(), Optional.empty(),
            Optional.of(new JavaTypeIdentity(type.category(), type.displayName(), "java.lang.Object")), Optional.empty()))));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", exception.getMessage());
  }

  @Test
  void givenLocalAndCatchTypesAcrossOverloads_whenAnalyzed_thenWrittenTypesKeepExactSourceOwnersAndRanges() throws IOException {
    // Arrange
    var source = """
        class FirstOwner {
          void overloaded(int marker) {
            int repeated = marker;
            try { throw new IllegalArgumentException(); } catch (IllegalArgumentException problem) { }
            try { throw new IllegalStateException(); } catch (IllegalStateException problem) { }
          }
          void overloaded(String marker) { String repeated = marker; }
        }
        class SecondOwner { void overloaded(int marker) { int repeated = marker; } }
        """;

    // Act
    var input = analyze(source);
    var locals = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION).toList();
    var localKeys = locals.stream().map(SyntaxNode::nodeId).toList();
    var typeOutcomes = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("VariableTree::VariableTree#getType()")
            && outcome.support().facts().stream().anyMatch(fact -> localKeys.contains(fact.identity()))).toList();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(5, locals.size());
    assertEquals(5, typeOutcomes.size());
    assertTrue(typeOutcomes.stream().allMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FACTS));
    assertEquals(List.of("int", "IllegalArgumentException", "IllegalStateException", "String", "int"), locals.stream()
        .map(node -> node.value(SyntaxValue.LocalDeclaration.class).writtenType().orElseThrow().origin())
        .map(origin -> source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()))).toList());
    assertWrittenLocalTypes(input, source);
  }

  @Test
  void givenRetainedLocalType_whenCapturedKindClaimsAPrimitiveForWrittenString_thenInputRejectsFalseWrittenSupport() throws IOException {
    // Arrange
    var original = analyze("class Example { void value(String input) { String local = input; } }");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var selected = evidence.outcomes().stream().filter(outcome -> outcome.address().role().equals("VariableTree::VariableTree#getType()")
        && outcome.support().facts().stream().anyMatch(fact -> fact.value() instanceof CoverageFactValue.Source value
            && value.value().value().tag() == CoverageEvidenceValue.Tag.LOCAL_DECLARATION)).findFirst().orElseThrow();
    var altered = new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR,
        List.of(new CoveragePropertyValue.Item("PRIMITIVE_TYPE:0", "String", CoveragePropertyValue.Provenance.WRITTEN)));
    var contexts = evidence.writtenInventory().contexts().stream().map(context -> context.address().equals(selected.address())
        ? new SourceContext(context.address(), context.feature(), context.origins(), Optional.of(altered), context.requiresAttribution()) : context).toList();
    var inventory = new SourceCoverageInventory(original.snapshot(), evidence.writtenInventory().release(), evidence.writtenInventory().contractIdentity(), contexts);
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.address().equals(selected.address())
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), Optional.of(altered), outcome.support(), outcome.emissions()) : outcome).toList();
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), inventory,
        new AttributedCoverageExpectations(inventory, evidence.attributedExpectations().projections()), outcomes,
        evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", exception.getMessage());
  }

  @Test
  void givenCompactRecordConstructor_whenCoverageIsCaptured_thenMandatedParametersKeepTheirSemanticOwnerWithoutWrittenAliases() throws IOException {
    // Arrange
    var source = "record Example(int value) { Example { } }";

    // Act
    var input = analyze(source);
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var members = evidence.outcomes().stream().filter(outcome -> outcome.address().role().equals("ClassTree::ClassTree#getMembers()"))
        .findFirst().orElseThrow();
    var enclosed = evidence.outcomes().stream().filter(outcome -> outcome.address().role().equals(JavaDeclarationPropertyCoverage.TYPE_ELEMENT_ENCLOSED_ELEMENTS))
        .findFirst().orElseThrow();
    var parameter = input.program().methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow().parameters().getFirst();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(1, evidence.writtenInventory().contexts().stream().filter(context -> context.address().partition() == SourceContext.Partition.TREE
        && context.feature().filter(feature -> feature.identifier().equals("VARIABLE")).isPresent()).count());
    assertEquals(FeatureContextOutcome.Mode.FACTS, members.support().mode());
    assertEquals(FeatureContextOutcome.Mode.FACTS, enclosed.support().mode());
    assertTrue(enclosed.support().facts().stream().anyMatch(fact -> fact.kind() == CoverageFactReference.Kind.PARAMETER
        && fact.identity().equals(parameter.parameterKey())));
  }

  @Test
  void givenConstantAndDefaultSwitchLabels_whenAnalyzed_thenEachLabelUsesItsActualOwnedCaseFact() throws IOException {
    // Arrange
    var source = "class Example { int value(int input) { return switch (input) { case 0 -> 1; default -> 2; }; } }";

    // Act
    var input = analyze(source);
    var labels = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream().filter(outcome -> outcome.observation()
        .filter(observation -> List.of("CONSTANT_CASE_LABEL", "DEFAULT_CASE_LABEL").contains(observation.classification().feature().identifier())).isPresent()).toList();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(2, labels.size());
    assertTrue(labels.stream().allMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FACTS));
    assertTrue(labels.stream().allMatch(outcome -> outcome.support().facts().stream().anyMatch(fact -> fact.value() instanceof CoverageFactValue.Source value
        && value.value().value().tag() == CoverageEvidenceValue.Tag.SWITCH_CASE)));
  }

  @Test
  void givenGenericMethodWithOrderedTypeParameters_whenAnalyzed_thenExecutableTypeSupportRetainsTypedHeaderComponents() throws IOException {
    // Arrange
    var source = "class Example<T> { <U extends Number, V extends Exception> U value(U parameter) throws V { return parameter; } }";

    // Act
    var input = analyze(source);
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var outcome = evidence.outcomes().stream().filter(value -> value.address().role().equals("ExecutableElement::ExecutableElement#asType()"))
        .findFirst().orElseThrow();
    var projection = outcome.encounteredValue().orElseThrow().items().getFirst().executableType().orElseThrow();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(FeatureContextOutcome.Mode.FACTS, outcome.support().mode());
    assertEquals(List.of("U", "V"), projection.variables().stream().map(variable -> variable.type().displayName()).toList());
    assertEquals(List.of("U"), projection.parameters().stream().map(type -> type.displayName()).toList());
    assertEquals("U", projection.returnType().displayName());
    assertEquals(List.of("V"), projection.thrownTypes().stream().map(type -> type.displayName()).toList());
    var header = input.aggregate().orElseThrow().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration declaration
        && declaration.name().equals("value")).findFirst().orElseThrow();
    assertEquals(List.of("U", "V"), header.children(SyntaxRole.TYPE_PARAMETER).stream()
        .map(node -> ((SyntaxValue.TypeParameterDeclaration) node.value()).name()).toList());
  }

  @Test
  void givenGenericMethodEvidence_whenTypedVariableOrderIsReversed_thenInputRejectsFalseExecutableSupport() throws IOException {
    // Arrange
    var original = analyze("class Example { <U, V> U value(U parameter) { return parameter; } }");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var selected = evidence.outcomes().stream().filter(value -> value.address().role().equals("ExecutableElement::ExecutableElement#asType()"))
        .findFirst().orElseThrow();
    var value = selected.encounteredValue().orElseThrow();
    var item = value.items().getFirst();
    var type = item.executableType().orElseThrow();
    var reversed = new ArrayList<>(type.variables());
    Collections.reverse(reversed);
    var altered = new CoveragePropertyValue(value.state(), List.of(new CoveragePropertyValue.Item(item.identity(), item.value(), item.provenance(),
        Optional.of(new CoverageExecutableType(reversed, type.parameters(), type.returnType(), type.thrownTypes(), type.receiverType())))));
    var projections = evidence.attributedExpectations().projections().stream().map(projection -> projection.address().equals(selected.address())
        ? new AttributedCoverageExpectations.Projection(projection.address(), altered, projection.ownerIdentity()) : projection).toList();
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.address().equals(selected.address())
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), Optional.of(altered), outcome.support(), outcome.emissions()) : outcome).toList();
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(),
        new AttributedCoverageExpectations(evidence.writtenInventory(), projections), outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", exception.getMessage());
  }

  @Test
  void givenDeclarationPropertySurfaces_whenFrontendCapturesCoverage_thenEveryActualRepresentedAccessorHasIndependentEvidence() throws IOException {
    // Arrange
    var source = "class Example { int field; int value(int parameter) { return parameter; } }";

    // Act
    var input = analyze(source);
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var properties = evidence.outcomes().stream().filter(outcome -> outcome.observation().filter(observation ->
        observation.classification().feature().category() == LanguageFeatureCategory.DECLARATION_PROPERTY).isPresent()).toList();

    // Assert
    assertEquals(List.of("BlockTree", "ClassTree", "CompilationUnitTree", "ExecutableElement", "MethodTree", "TypeElement", "VariableTree"),
        properties.stream().map(outcome -> outcome.address().role().substring(0, outcome.address().role().indexOf("::"))).distinct().sorted().toList());
    assertEquals(evidence.writtenInventory().contexts().stream().map(SourceContext::address).toList(), evidence.outcomes().stream().map(FeatureContextOutcome::address).toList());
    assertTrue(properties.stream().allMatch(outcome -> outcome.occurrence().isPresent() && outcome.encounteredValue().isPresent()));
    assertTrue(properties.stream().anyMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.NEGATIVE));
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
  }

  @Test
  void givenFrozenExpectations_whenAnEntireOutcomeBundleIsOmitted_thenFactoryRejectsTheMissingContext() throws IOException {
    // Arrange
    var original = analyze("class Example { int value() { return 1; } }");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var outcomes = evidence.outcomes().stream().filter(outcome -> !outcome.observation().filter(observation ->
        observation.classification().feature().identifier().equals("INT_LITERAL")).isPresent()).toList();

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> CoverageAdmission.fromEvidence(original.languageFeatureCoverage(),
        evidence.writtenInventory(), evidence.attributedExpectations(), outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions()));

    // Assert
    assertEquals("Coverage outcomes must exactly match the independent context inventory in order", exception.getMessage());
    assertTrue(evidence.writtenInventory().contexts().stream().anyMatch(context -> context.feature().filter(feature -> feature.identifier().equals("INT_LITERAL")).isPresent()));
  }

  @Test
  void givenKnownPairsWithWithheldFacts_whenSupportIsReconciled_thenPairsSurviveWithBlockingFailedSupport() throws IOException {
    // Arrange
    var original = analyze("class Example { int value() { return 1; } }");
    var diagnostics = JavaCoverageTestSupport.emissions(original);

    // Act
    var partial = JavaCoverageTestSupport.withFacts(original, new JavaProgram(List.of()), Optional.empty(), diagnostics);

    // Assert
    assertEquals(original.languageFeatureCoverageResult().occurrences(), partial.languageFeatureCoverageResult().occurrences());
    assertEquals(original.languageFeatureCoverageResult().observations(), partial.languageFeatureCoverageResult().observations());
    assertTrue(partial.languageFeatureCoverageResult().validationEvidence().outcomes().stream().anyMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
    assertTrue(partial.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-source-evidence") && diagnostic.isError()));
  }

  @Test
  void givenClaimedPositiveFacts_whenActualProgramAndAggregateAreRemoved_thenInputRejectsTheFalseSuccess() throws IOException {
    // Arrange
    var original = analyze("class Example { int value() { return 1; } }");

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        new JavaProgram(List.of()), Optional.empty(), original.diagnostics(), original.languageFeatureCoverageResult()));

    // Assert
    assertEquals("Claimed positive coverage support must match an exact actual owned fact value", exception.getMessage());
  }

  @Test
  void givenNonemptyActualImports_whenSuppliedExpectationsClaimEmptyImports_thenInputIndependentlyRejectsNegativeSupport() throws IOException {
    // Arrange
    var original = analyze("import java.util.List; class Example {} ");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var absent = new CoveragePropertyValue(CoveragePropertyValue.State.EMPTY, List.of());
    var contexts = evidence.writtenInventory().contexts().stream().map(context -> context.address().role().equals(JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS)
        ? new SourceContext(context.address(), context.feature(), context.origins(), Optional.of(absent), false) : context).toList();
    var written = new SourceCoverageInventory(original.snapshot(), CONFIGURATION.release(), original.languageFeatureCoverage().contractIdentity(), contexts);
    var attributed = new AttributedCoverageExpectations(written, evidence.attributedExpectations().projections());
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.address().role().equals(JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), Optional.of(absent),
            new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.NEGATIVE, List.of(), Optional.empty(), Optional.empty(), Optional.of(absent)), List.of()) : outcome).toList();
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), written, attributed, outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed negative coverage support requires an actual existing owner with absent or empty accessor state", exception.getMessage());
  }

  @Test
  void givenEqualDiagnosticValuesAtDistinctCreatingSites_whenEventsAreAdmitted_thenTwoSlotsRemainDistinct() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var diagnostic = note();
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var events = new ArrayList<>(evidence.diagnosticEmissions());
    events.add(new DiagnosticEmission(new DiagnosticEmission.Address("first-note-site", "source-owner", 0), diagnostic, diagnostic.outcomeKind(), List.of(), events.size()));
    events.add(new DiagnosticEmission(new DiagnosticEmission.Address("second-note-site", "source-owner", 0), diagnostic, diagnostic.outcomeKind(), List.of(), events.size()));

    // Act
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        evidence.outcomes(), evidence.unmatchedFacts(), events);
    var diagnostics = events.stream().map(DiagnosticEmission::diagnostic).toList();
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), original.aggregate(), diagnostics, result);

    // Assert
    assertEquals(2, input.diagnostics().size());
    assertEquals(input.diagnostics().getFirst(), input.diagnostics().getLast());
    assertFalse(events.getFirst().address().equals(events.getLast().address()));
  }

  @Test
  void givenOneEventOwningSeveralContexts_whenEventsAreAdmitted_thenOneFinalDiagnosticSlotIsRetained() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var diagnostic = note();
    var contexts = evidence.outcomes().stream().limit(2).map(FeatureContextOutcome::address).toList();
    var event = new DiagnosticEmission(new DiagnosticEmission.Address("shared-note-site", "source-owner", 0), diagnostic, diagnostic.outcomeKind(), contexts, 0);

    // Act
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        evidence.outcomes(), evidence.unmatchedFacts(), List.of(event));
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), original.aggregate(), List.of(diagnostic), result);

    // Assert
    assertEquals(1, input.diagnostics().size());
    assertEquals(contexts, result.validationEvidence().diagnosticEmissions().getFirst().contexts());
  }

  @Test
  void givenDuplicateEventAddresses_whenFactoryIsCalled_thenDistinctSlotsDoNotHideTheCollision() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var diagnostic = note();
    var first = new DiagnosticEmission(new DiagnosticEmission.Address("note-site", "source-owner", 0), diagnostic, diagnostic.outcomeKind(), List.of(), 0);

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(),
        evidence.attributedExpectations(), evidence.outcomes(), evidence.unmatchedFacts(), List.of(first, first.atSlot(1))));

    // Assert
    assertEquals("Diagnostic events require unique creating-site addresses and exact ordered slots", exception.getMessage());
  }

  @Test
  void givenCoverageResultClass_whenPublicConstructionIsInspected_thenOnlyControlledEvidenceFactoryIsAvailable() {
    // Arrange
    var type = LanguageFeatureCoverageResult.class;

    // Act
    var publicConstructors = Arrays.stream(type.getDeclaredConstructors()).filter(constructor -> Modifier.isPublic(constructor.getModifiers())).count();

    // Assert
    assertEquals(0, publicConstructors);
    assertFalse(type.isRecord());
  }

  private static AnalysisDiagnostic note() {
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", "coverage-note"), "frontend.note", DiagnosticStage.FRONTEND, DiagnosticSeverity.INFO,
        "Copied frontend note", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
  }

  @Test
  void givenSiblingCreatingCollectorsWithEqualValues_whenNestedTransportJoinsThem_thenCreatingOwnersKeepTwoEvents() {
    // Arrange
    var left = new DiagnosticEmissionCollector("nested-producer", "left-source-path");
    var right = new DiagnosticEmissionCollector("nested-producer", "right-source-path");
    left.emit("nested-error-site", note());
    right.emit("nested-error-site", note());
    var joined = new DiagnosticEmissionCollector("parent", "source-root");

    // Act
    joined.include(left.snapshot());
    joined.include(right.snapshot());
    var result = new TranslationResult<>("value", joined.snapshot());

    // Assert
    assertEquals(2, result.diagnostics().size());
    assertEquals(result.diagnostics().getFirst(), result.diagnostics().getLast());
    assertEquals(List.of("left-source-path", "right-source-path"), result.emissions().events().stream().map(event -> event.address().owner()).toList());
    assertFalse(result.emissions().events().getFirst().address().equals(result.emissions().events().getLast().address()));
  }

  @Test
  void givenOneCreatingEventTransportedByTwoCarriers_whenJoined_thenOneEventKeepsItsOriginalAddress() {
    // Arrange
    var created = new DiagnosticEmissionCollector("nested-producer", "shared-source-path");
    created.emit("nested-error-site", note());
    var joined = new DiagnosticEmissionCollector("parent", "source-root");

    // Act
    joined.include(created.snapshot());
    joined.include(created.snapshot());

    // Assert
    assertEquals(1, joined.emissionCount());
    assertEquals(created.snapshot().events(), joined.snapshot().events());
  }

  @Test
  void givenUnequalCreatingEvents_whenActualDiagnosticSlotsAreReversed_thenInputRejectsTheChangedOrder() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var events = new DiagnosticEmissionCollector("notes", "source-root");
    events.emit("first-note", note());
    events.emit("second-note", new AnalysisDiagnostic(StableId.sha256("diagnostic", "second-note"), "frontend.second-note",
        DiagnosticStage.FRONTEND, DiagnosticSeverity.INFO, "Second copied note", List.of(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR));
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var ordered = List.of(events.snapshot().events().getFirst().atSlot(0), events.snapshot().events().getLast().atSlot(1));
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        evidence.outcomes(), evidence.unmatchedFacts(), ordered);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(),
        original.aggregate(), List.of(ordered.getLast().diagnostic(), ordered.getFirst().diagnostic()), result));

    // Assert
    assertEquals("Actual frontend diagnostic slots must exactly match ordered creating-site events", failure.getMessage());
  }

  @Test
  void givenARepresentedLiteral_whenSameFileMethodFactsReplaceItsSupport_thenInputRejectsTheWrongKindAndOwnerPath() throws IOException {
    // Arrange
    var original = analyze("class Example { int first() { return 1; } int second() { return 2; } }");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var literal = evidence.outcomes().stream().filter(outcome -> outcome.observation().filter(value -> value.classification().feature().identifier().equals("INT_LITERAL")).isPresent()).findFirst().orElseThrow();
    var method = CoverageActualFacts.capture(original.program(), original.aggregate()).references().stream().filter(fact -> fact.kind() == CoverageFactReference.Kind.METHOD).findFirst().orElseThrow();
    var changed = new FeatureContextOutcome(literal.address(), literal.occurrence(), literal.observation(), literal.encounteredValue(),
        new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.FACTS, List.of(method), Optional.empty(), Optional.empty(), Optional.empty()), literal.emissions());
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(literal) ? changed : outcome).toList();
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(), outcomes,
        evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(),
        original.aggregate(), original.diagnostics(), result));

    // Assert
    assertTrue(failure.getMessage().startsWith("Tree support must match the exact retained kind and captured owner topology:"));
  }

  @Test
  void givenARealUnmatchedLiteral_whenItsCapturedOwnerIsChangedToAnotherSameFileLiteral_thenInputRejectsTheFalseOwner() throws IOException {
    // Arrange
    var original = analyze("class Example { int first() { return 1; } int second() { return 2; } }");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var literals = evidence.outcomes().stream().filter(outcome -> outcome.observation().filter(value -> value.classification().feature().identifier().equals("INT_LITERAL")).isPresent()).toList();
    var first = literals.getFirst();
    var second = literals.getLast();
    var fact = first.support().facts().stream().filter(value -> value.value() instanceof CoverageFactValue.Source).findFirst().orElseThrow();
    var facts = CoverageActualFacts.capture(original.program(), original.aggregate()).references();
    var missing = failureDiagnostic(first, "frontend.missing-source-evidence", DiagnosticStage.FRONTEND, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, true);
    var missingEvent = new DiagnosticEmission(new DiagnosticEmission.Address("missing-literal-site", "first-literal-path", 0), missing, missing.outcomeKind(),
        List.of(first.address()), 0);
    var diagnostic = new AnalysisDiagnostic(StableId.sha256("diagnostic", "unmatched-literal"), "frontend.unexpected-source-evidence", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, "An actual retained literal has no successful support slot", fact.origin().stream().toList(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var event = new DiagnosticEmission(new DiagnosticEmission.Address("unmatched-literal-site", "first-literal-path", 0), diagnostic, diagnostic.outcomeKind(),
        List.of(first.address()), 1);
    var foreignDiagnostic = new AnalysisDiagnostic(StableId.sha256("diagnostic", "foreign-unmatched-literal"), "frontend.unexpected-source-evidence", DiagnosticStage.FRONTEND,
        DiagnosticSeverity.ERROR, "An actual retained literal has no successful support slot", second.occurrence().orElseThrow().origins(), AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR);
    var foreignEvent = new DiagnosticEmission(new DiagnosticEmission.Address("unmatched-literal-site", "second-literal-path", 0), foreignDiagnostic,
        foreignDiagnostic.outcomeKind(), List.of(second.address()), 1);
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(first) ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(),
        outcome.encounteredValue(), new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.FAILED, List.of(), Optional.empty(), Optional.empty(), Optional.empty()), List.of(missingEvent.address())) : outcome).toList();
    var valid = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(), outcomes,
        List.of(new UnmatchedSourceFact(first.address(), fact, facts.indexOf(fact), UnmatchedSourceFact.Reason.EXTRA, event.address())), List.of(missingEvent, event));
    var foreign = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(), outcomes,
        List.of(new UnmatchedSourceFact(second.address(), fact, facts.indexOf(fact), UnmatchedSourceFact.Reason.EXTRA, foreignEvent.address())), List.of(missingEvent, foreignEvent));

    // Act
    var retained = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), original.aggregate(), List.of(missing, diagnostic), valid);
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(),
        original.aggregate(), List.of(missing, foreignDiagnostic), foreign));

    // Assert
    assertEquals(1, retained.languageFeatureCoverageResult().validationEvidence().unmatchedFacts().size());
    assertEquals("Diagnosed unmatched facts must form an exact bijection with actual extra fact slots", failure.getMessage());
  }

  @Test
  void givenTwoImportsWithFrozenWrittenOrder_whenActualAttachmentsAreSwapped_thenEqualCountsDoNotCertifySupport() throws IOException {
    // Arrange
    var original = analyze("import java.util.List; import java.util.Set; class Example {}");
    var aggregate = original.aggregate().orElseThrow();
    var root = aggregate.roots().getFirst();
    var imports = root.children(SyntaxRole.IMPORT_DECLARATION);
    var children = new ArrayList<SyntaxChild>();
    for (var child : root.children()) {
      var node = child.role() == SyntaxRole.IMPORT_DECLARATION ? imports.get(1 - child.position()) : child.node();
      if (child.role() == SyntaxRole.IMPORT_DECLARATION) {
        node = SyntaxNode.source(node.sourceFileKey(), root.structuralPath().child(child.role(), child.position()), node.sourceKind(), node.syntaxKind(), node.type(),
            node.value(), node.children(), node.semanticReference(), node.origin().orElseThrow());
      }
      children.add(new SyntaxChild(child.role(), child.position(), node));
    }
    var changedRoot = SyntaxNode.source(root.sourceFileKey(), root.structuralPath(), root.sourceKind(), root.syntaxKind(), root.type(), root.value(), children,
        root.semanticReference(), root.origin().orElseThrow());
    var changed = copyAggregate(original, changedRoot, aggregate.declarationMemberOrders());

    // Act
    var partial = JavaCoverageTestSupport.withFacts(original, original.program(), Optional.of(changed), JavaCoverageTestSupport.emissions(original));
    var outcome = partial.languageFeatureCoverageResult().validationEvidence().outcomes().stream().filter(value -> value.address().role().equals(JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS)).findFirst().orElseThrow();

    // Assert
    assertEquals(imports.size(), changed.roots().getFirst().children(SyntaxRole.IMPORT_DECLARATION).size());
    assertEquals(FeatureContextOutcome.Mode.FAILED, outcome.support().mode());
    assertTrue(partial.diagnostics().stream().anyMatch(value -> value.code().equals("frontend.missing-source-evidence")));
  }

  @Test
  void givenMixedDirectMembers_whenTranslated_thenActualOrderSidecarMatchesTheIndependentSemanticProjection() throws IOException {
    // Arrange
    var original = analyze("class Example { int first; void value() {} int second; class Nested {} }");
    var owner = original.program().types().getFirst();

    // Act
    var order = original.aggregate().orElseThrow().declarationMemberOrder(owner.typeKey()).orElseThrow();
    var outcome = original.languageFeatureCoverageResult().validationEvidence().outcomes().stream().filter(value ->
        value.address().role().equals(JavaDeclarationPropertyCoverage.TYPE_ELEMENT_ENCLOSED_ELEMENTS) && value.support().facts().stream().anyMatch(fact -> fact.identity().equals(owner.typeKey()))).findFirst().orElseThrow();

    // Assert
    assertTrue(outcome.support().facts().stream().anyMatch(fact -> fact.kind() == CoverageFactReference.Kind.DECLARATION_MEMBER_ORDER));
    assertEquals(outcome.encounteredValue().orElseThrow().items().stream().map(CoveragePropertyValue.Item::identity).toList(), order.members().stream().map(DeclarationMemberOrder.Member::declarationKey).toList());
    assertEquals(FeatureContextOutcome.Mode.FACTS, outcome.support().mode());
  }

  @Test
  void givenMixedDirectMemberOrder_whenOnlyTheActualSidecarIsReversed_thenInputRejectsSourceAndFamilyOrderMismatch() throws IOException {
    // Arrange
    var original = analyze("class Example { int first; void value() {} int second; }");
    var aggregate = original.aggregate().orElseThrow();
    var order = aggregate.declarationMemberOrders().getFirst();
    var reversed = new ArrayList<>(order.members());
    Collections.reverse(reversed);
    var changed = copyAggregate(original, aggregate.roots().getFirst(), List.of(new DeclarationMemberOrder(order.sourceFileKey(), order.ownerTypeKey(), reversed)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(),
        Optional.of(changed), original.diagnostics(), original.languageFeatureCoverageResult()));

    // Assert
    assertTrue(failure.getMessage().startsWith("Retained member order must preserve actual program order within each declaration family"));
  }

  private static JavaAstAggregate copyAggregate(SyntaxAnalysisInput original, SyntaxNode root, List<DeclarationMemberOrder> memberOrders) {
    var nodes = root.descendantsAndSelf();
    var originalAggregate = original.aggregate().orElseThrow();
    var builder = AggregateFixture.builder(original.snapshot()).add(new SyntaxSourceContribution(root.sourceFileKey(), root, nodes,
        nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), originalAggregate.semanticEdges()));
    memberOrders.forEach(builder::addDeclarationMemberOrder);
    nodes.forEach(node -> originalAggregate.nodes().stream().filter(previous -> previous.sourceKind() == node.sourceKind()
        && previous.origin().equals(node.origin()) && previous.value().equals(node.value())).findFirst()
        .flatMap(previous -> originalAggregate.writtenSourceAssociation(previous.nodeId())).ifPresent(association ->
            builder.addWrittenSourceAssociation(new WrittenSourceAssociation(node.nodeId(), association.sourceFileKey(), association.ownerPath(),
                association.sourceKind(), association.origin()))));
    originalAggregate.declarationTypeEvidence().forEach(builder::addDeclarationTypeEvidence);
    copyMethodBodies(originalAggregate, builder);
    return builder.seal();
  }

  private static void copyMethodBodies(JavaAstAggregate aggregate, AggregateFixture builder) {
    aggregate.methodBodyKeys().forEach(key -> {
      var syntax = aggregate.methodSyntax(key).orElseThrow();
      var bodyNodes = syntax.root().descendantsAndSelf();
      var uses = bodyNodes.stream().flatMap(node -> node.children().stream().flatMap(child -> syntax.useDecision(node.nodeId(), child.role(), child.position()).stream())).toList();
      var invocations = bodyNodes.stream().flatMap(node -> syntax.invocationArity(node.nodeId()).stream()).toList();
      var staticAccesses = bodyNodes.stream().flatMap(node -> syntax.staticAccess(node.nodeId()).stream()).toList();
      var concatenations = bodyNodes.stream().flatMap(node -> syntax.stringConcatenation(node.nodeId()).stream()).toList();
      builder.addMethodBody(key, syntax.root(), aggregate.methodBodyOrigin(key).orElseThrow(), uses, invocations, staticAccesses, concatenations);
    });
  }

  @Test
  void givenNestedGenericDeclarations_whenTranslated_thenActualDeclarationTypesRetainCanonicalOwnersAndParameterOrder() throws IOException {
    // Arrange
    var source = "class Example<T, U> { class Inner<V> {} }";

    // Act
    var input = analyze(source);
    var aggregate = input.aggregate().orElseThrow();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(2, aggregate.declarationTypeEvidence().size());
    input.program().types().forEach(type -> {
      var actual = aggregate.declarationTypeEvidence(type.typeKey()).orElseThrow();
      var selected = (CoverageTypeStructure.Declared) actual.structure();
      assertEquals(type.typeKey(), selected.declarationKey());
      assertEquals(type.typeParameters().stream().map(parameter -> new CoverageTypeStructure.Variable(parameter.typeParameterKey())).toList(), selected.arguments());
      assertEquals(type.enclosingType().flatMap(reference -> reference.declarationKey()), selected.lexicalOwnerKey());
    });
  }

  @Test
  void givenGenericDeclarationType_whenClaimedArgumentsChangeButDisplayStaysEqual_thenInputRejectsTheForeignSelection() throws IOException {
    // Arrange
    var input = analyze("class Example<T> {}");
    var selected = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("TypeElement::TypeElement#asType()")).findFirst().orElseThrow();
    var item = selected.encounteredValue().orElseThrow().items().getFirst();
    var actual = (CoverageTypeStructure.Declared) item.typeStructure().orElseThrow();
    var changed = new CoveragePropertyValue.Item(item.identity(), item.value(), item.provenance(), item.executableType(), item.typeIdentity(),
        item.modifierFlags(), item.annotationValue(), Optional.of(new CoverageTypeStructure.Declared(actual.declarationKey(), actual.lexicalOwnerKey(),
            actual.enclosingType(), List.of(new CoverageTypeStructure.Variable("foreign-type-parameter")))));
    var result = claimedValue(input, selected, new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR, List.of(changed)));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(),
        input.aggregate(), input.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
  }

  @Test
  void givenTypedAnnotationArray_whenClaimedValuesAreReversedWithUnchangedPresentation_thenInputRejectsTheWrongOrder() throws IOException {
    // Arrange
    var input = analyze("@A({1, 2}) class Example {} @interface A { int[] value(); }");
    var selected = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("TypeElement::Element#getAnnotationMirrors()")
            && outcome.encounteredValue().orElseThrow().state() == CoveragePropertyValue.State.ITEMS).findFirst().orElseThrow();
    var item = selected.encounteredValue().orElseThrow().items().getFirst();
    var annotation = (CoverageAnnotationValue.Annotation) item.annotationValue().orElseThrow();
    var entry = annotation.explicitValues().getFirst();
    var array = (CoverageAnnotationValue.Array) entry.value();
    var changed = new CoverageAnnotationValue.Annotation(annotation.typeKey(), List.of(new CoverageAnnotationValue.Entry(entry.elementKey(),
        new CoverageAnnotationValue.Array(array.elements().reversed()))), annotation.provenance());
    var result = claimedValue(input, selected, new CoveragePropertyValue(CoveragePropertyValue.State.ITEMS,
        List.of(new CoveragePropertyValue.Item(item.identity(), item.value(), item.provenance(), item.executableType(), item.typeIdentity(),
            item.modifierFlags(), Optional.of(changed), item.typeStructure()))));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(),
        input.aggregate(), input.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
  }

  @Test
  void givenWrittenAnnotationDefault_whenClaimedTypedScalarChangesWithUnchangedPresentation_thenInputRejectsTheFalseValue() throws IOException {
    // Arrange
    var input = analyze("class Example {} @interface A { int value() default 7; }");
    var selected = input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().role().equals("ExecutableElement::ExecutableElement#getDefaultValue()")
            && outcome.encounteredValue().orElseThrow().state() == CoveragePropertyValue.State.SCALAR).findFirst().orElseThrow();
    var item = selected.encounteredValue().orElseThrow().items().getFirst();
    var result = claimedValue(input, selected, new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR,
        List.of(new CoveragePropertyValue.Item(item.identity(), item.value(), item.provenance(), item.executableType(), item.typeIdentity(),
            item.modifierFlags(), Optional.of(new CoverageAnnotationValue.Scalar(CoverageAnnotationValue.Scalar.Kind.INT, "8")), item.typeStructure()))));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(input.snapshot(), input.environmentSnapshot(), input.program(),
        input.aggregate(), input.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
  }

  @Test
  void givenSharedWrittenFieldType_whenActualSourceNodesArePublished_thenEqualSpansRetainTheirDistinctParsedOwnerPaths() throws IOException {
    // Arrange
    var source = "class Example { int first, second; }";

    // Act
    var input = analyze(source);
    var aggregate = input.aggregate().orElseThrow();
    var types = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE).toList();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(2, types.size());
    assertEquals(types.getFirst().origin(), types.getLast().origin());
    assertFalse(aggregate.writtenSourceAssociation(types.getFirst().nodeId()).orElseThrow().ownerPath()
        .equals(aggregate.writtenSourceAssociation(types.getLast().nodeId()).orElseThrow().ownerPath()));
  }

  @Test
  void givenSharedWrittenFieldType_whenIndependentHeaderContextsAreCaptured_thenEachOriginalOwnerPathIsRetained() throws IOException {
    // Arrange
    var source = "class Example { int first, second; }";

    // Act
    var input = analyze(source);
    var aggregate = input.aggregate().orElseThrow();
    var headers = input.languageFeatureCoverageResult().validationEvidence().writtenInventory().contexts().stream()
        .filter(context -> context.address().partition() == SourceContext.Partition.TYPE).toList();
    var actualPaths = aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE)
        .map(node -> aggregate.writtenSourceAssociation(node.nodeId()).orElseThrow().ownerPath()).toList();

    // Assert
    assertTrue(input.diagnostics().isEmpty(), () -> input.diagnostics().toString());
    assertEquals(2, headers.size());
    assertEquals(headers.getFirst().origins(), headers.getLast().origins());
    assertFalse(headers.getFirst().address().ownerPath().equals(headers.getLast().address().ownerPath()));
    assertEquals(actualPaths, headers.stream().map(context -> context.address().ownerPath()).toList());
  }

  @Test
  void givenAnAbsentFieldInitializer_whenAClaimUsesEmptyListState_thenInputRejectsTheFalseShape() throws IOException {
    // Arrange
    var original = analyze("class Example { int value; }");
    var selected = property(original, "VariableTree::VariableTree#getInitializer()", CoveragePropertyValue.State.ABSENT);
    var result = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.EMPTY, List.of()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed negative coverage support requires an actual existing owner with absent or empty accessor state", failure.getMessage());
  }

  @Test
  void givenAnEmptyImportList_whenAClaimUsesAbsentScalarState_thenInputRejectsTheFalseShape() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var selected = property(original, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS, CoveragePropertyValue.State.EMPTY);
    var result = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.ABSENT, List.of()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed negative coverage support requires an actual existing owner with absent or empty accessor state", failure.getMessage());
  }

  @Test
  void givenOneActualImport_whenAClaimUsesScalarState_thenInputRejectsTheFalseShape() throws IOException {
    // Arrange
    var original = analyze("import java.util.List; class Example {}");
    var selected = property(original, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS, CoveragePropertyValue.State.ITEMS);
    var result = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR, selected.encounteredValue().orElseThrow().items()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
  }

  @Test
  void givenAnActualLocalInitializer_whenAClaimUsesListState_thenInputRejectsTheFalseShape() throws IOException {
    // Arrange
    var original = analyze("class Example { void value() { int local = 1; } }");
    var selected = property(original, "VariableTree::VariableTree#getInitializer()", CoveragePropertyValue.State.SCALAR);
    assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
    var result = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.ITEMS, selected.encounteredValue().orElseThrow().items()));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
  }

  @Test
  void givenClosedSourceFactSupport_whenItsLiteralTextChangesWithTheSameIdentityAndTopology_thenInputRejectsTheFalseValue() throws IOException {
    // Arrange
    var original = analyze("class Example { int value() { return 1; } }");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var selected = evidence.outcomes().stream().filter(outcome -> outcome.observation().filter(observation ->
        observation.classification().feature().identifier().equals("INT_LITERAL")).isPresent()).findFirst().orElseThrow();
    var actual = selected.support().facts().stream().filter(fact -> fact.kind() == CoverageFactReference.Kind.SYNTAX).findFirst().orElseThrow();
    var source = ((CoverageFactValue.Source) actual.value()).value();
    var changed = new CoverageSourceFact(source.structuralPath(), source.sourceKind(), source.syntaxKind(), source.type(),
        new CoverageEvidenceValue.Structure(CoverageEvidenceValue.Tag.LITERAL,
            List.of(new CoverageEvidenceValue.Field("text", new CoverageEvidenceValue.Text("2")))),
        source.children(), source.semanticReference(), source.sourceAssociation());
    var supplied = new CoverageFactReference(actual.kind(), actual.identity(), actual.sourceFileKey(), new CoverageFactValue.Source(changed), actual.origin());
    var claimed = selected.support().facts().stream().map(fact -> fact.equals(actual) ? supplied : fact).toList();
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(selected)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
            new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.FACTS, claimed, Optional.empty(), Optional.empty(), Optional.empty()), outcome.emissions()) : outcome).toList();
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
        original.program(), original.aggregate(), original.diagnostics(), result));

    // Assert
    assertFalse(actual.equals(supplied));
    assertEquals("Claimed positive coverage support must match an exact actual owned fact value", failure.getMessage());
  }

  @Test
  void givenClosedFactValues_whenConstructionFamiliesAreInspected_thenNoCallerDefinedEqualityImplementationIsPermitted() {
    // Arrange
    var type = CoverageFactValue.class;

    // Act
    var implementations = type.getPermittedSubclasses();

    // Assert
    assertTrue(type.isSealed());
    assertEquals(9, implementations.length);
    assertTrue(Arrays.stream(implementations).allMatch(implementation -> Modifier.isFinal(implementation.getModifiers())));
  }

  @Test
  void givenCallerOwnedSourceFactCollections_whenTheyChangeAfterFactoryAdmission_thenTheRetainedValueAndInputStayUnchanged() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var selected = evidence.outcomes().stream().filter(outcome -> outcome.address().partition() == SourceContext.Partition.TREE
        && outcome.observation().filter(observation -> observation.classification().feature().identifier().equals("COMPILATION_UNIT")).isPresent()).findFirst().orElseThrow();
    var actual = selected.support().facts().stream().filter(fact -> fact.kind() == CoverageFactReference.Kind.SYNTAX).findFirst().orElseThrow();
    var source = ((CoverageFactValue.Source) actual.value()).value();
    var steps = new ArrayList<>(source.structuralPath());
    var children = new ArrayList<>(source.children());
    var fields = new ArrayList<>(source.value().fields());
    var association = source.sourceAssociation().orElseThrow();
    var ownerPath = new ArrayList<>(association.ownerPath().steps());
    var copied = new CoverageSourceFact(steps, source.sourceKind(), source.syntaxKind(), source.type(),
        new CoverageEvidenceValue.Structure(source.value().tag(), fields), children, source.semanticReference(),
        Optional.of(new CoverageSourceFact.Association(association.nodeKey(), association.sourceFileKey(), new SourceOwnerPath(ownerPath), association.sourceKind(), association.origin())));
    var supplied = new CoverageFactReference(actual.kind(), actual.identity(), actual.sourceFileKey(), new CoverageFactValue.Source(copied), actual.origin());
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(selected)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
            new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.FACTS, List.of(supplied), Optional.empty(), Optional.empty(), Optional.empty()), outcome.emissions()) : outcome).toList();
    var result = CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(),
        outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());

    // Act
    steps.clear();
    children.clear();
    fields.clear();
    ownerPath.add(new SourceOwnerPath.Step(SourceOwnerPath.Role.MEMBER, 99));
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(), original.program(), original.aggregate(), original.diagnostics(), result);

    // Assert
    assertEquals(actual, input.languageFeatureCoverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().equals(selected.address())).findFirst().orElseThrow().support().facts().getFirst());
    assertFalse(copied.children().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> copied.value().fields().clear());
  }

  @Test
  void givenFailedExpectedSupport_whenItsBlockingEventComesFromAnotherStage_thenFactoryRejectsIt() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var selected = property(original, "TypeElement::TypeElement#getQualifiedName()", CoveragePropertyValue.State.SCALAR);
    var diagnostic = failureDiagnostic(selected, "frontend.missing-source-evidence", DiagnosticStage.PERSISTENCE, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, true);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> failedEvidence(original, selected, diagnostic));

    // Assert
    assertEquals("Blocking source evidence requires an exact retained owner-associated error event", failure.getMessage());
  }

  @Test
  void givenFailedExpectedSupport_whenItsGeneralizedCodeDescribesAnExtraActualFact_thenFactoryRejectsTheWrongPartition() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var selected = property(original, "TypeElement::TypeElement#getQualifiedName()", CoveragePropertyValue.State.SCALAR);
    var diagnostic = failureDiagnostic(selected, "frontend.unexpected-source-evidence", DiagnosticStage.FRONTEND, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, true);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> failedEvidence(original, selected, diagnostic));

    // Assert
    assertEquals("Failed expected support requires its exact missing or inconsistent evidence cause", failure.getMessage());
  }

  @Test
  void givenGeneralizedMissingSupport_whenItsBlockingEventDropsCapturedOrigins_thenFactoryRejectsTheLoss() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var selected = property(original, "TypeElement::TypeElement#getQualifiedName()", CoveragePropertyValue.State.SCALAR);
    var diagnostic = failureDiagnostic(selected, "frontend.missing-source-evidence", DiagnosticStage.FRONTEND, AnalysisOutcomeKind.OTHER_ANALYSIS_ERROR, false);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> failedEvidence(original, selected, diagnostic));

    // Assert
    assertEquals("Generalized source evidence requires its exact frontend cause and captured origins", failure.getMessage());
  }

  @Test
  void givenGeneralizedMissingSupport_whenItsBlockingEventUsesACompilerCause_thenFactoryRejectsTheFalseCause() throws IOException {
    // Arrange
    var original = analyze("class Example {}");
    var selected = property(original, "TypeElement::TypeElement#getQualifiedName()", CoveragePropertyValue.State.SCALAR);
    var diagnostic = failureDiagnostic(selected, "frontend.missing-source-evidence", DiagnosticStage.FRONTEND, AnalysisOutcomeKind.COMPILER_ERROR, true);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> failedEvidence(original, selected, diagnostic));

    // Assert
    assertEquals("Generalized source evidence requires its exact frontend cause and captured origins", failure.getMessage());
  }

  private static AnalysisDiagnostic failureDiagnostic(FeatureContextOutcome selected, String code, DiagnosticStage stage, AnalysisOutcomeKind cause, boolean retainOrigins) {
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", code), code, stage, DiagnosticSeverity.ERROR, "Required represented support is unavailable",
        retainOrigins ? selected.occurrence().orElseThrow().origins() : List.of(), cause);
  }

  private static LanguageFeatureCoverageResult failedEvidence(SyntaxAnalysisInput input, FeatureContextOutcome selected, AnalysisDiagnostic diagnostic) {
    var evidence = input.languageFeatureCoverageResult().validationEvidence();
    var event = new DiagnosticEmission(new DiagnosticEmission.Address("creating-site", selected.address().toString(), 0), diagnostic,
        diagnostic.outcomeKind(), List.of(selected.address()), 0);
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.equals(selected)
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), outcome.encounteredValue(),
            new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.FAILED, List.of(), Optional.empty(), Optional.empty(), Optional.empty()), List.of(event.address())) : outcome).toList();
    return CoverageAdmission.fromEvidence(input.languageFeatureCoverage(), evidence.writtenInventory(), evidence.attributedExpectations(), outcomes,
        evidence.unmatchedFacts(), List.of(event));
  }

  private static FeatureContextOutcome property(SyntaxAnalysisInput input, String role, CoveragePropertyValue.State state) {
    return input.languageFeatureCoverageResult().validationEvidence().outcomes().stream().filter(outcome -> outcome.address().role().equals(role)
        && outcome.encounteredValue().orElseThrow().state() == state).findFirst().orElseThrow();
  }

  @Test
  void givenAReusedCreatingCollector_whenEarlierEventsAreTransportedThenCleared_thenLaterEqualEventsKeepDistinctAddresses() {
    // Arrange
    var producer = new DiagnosticEmissionCollector("nested-producer", "one-source-path");
    producer.emit("one-creating-site", note());
    var earlier = producer.snapshot();
    var joined = new DiagnosticEmissionCollector("parent", "source-root");

    // Act
    producer.startBatch();
    producer.emit("one-creating-site", note());
    joined.include(earlier);
    joined.include(producer.snapshot());
    var result = new TranslationResult<>("value", joined.snapshot());

    // Assert
    assertEquals(2, result.emissions().events().size());
    assertEquals(List.of(0, 1), result.emissions().events().stream().map(event -> event.address().ordinal()).toList());
    assertEquals(result.diagnostics().getFirst(), result.diagnostics().getLast());
  }

  private static LanguageFeatureCoverageResult claimedValue(SyntaxAnalysisInput original, FeatureContextOutcome selected, CoveragePropertyValue value) {
    var evidence = original.languageFeatureCoverageResult().validationEvidence();
    var inventory = new SourceCoverageInventory(original.snapshot(), evidence.writtenInventory().release(), evidence.writtenInventory().contractIdentity(),
        evidence.writtenInventory().contexts().stream().map(context -> context.address().equals(selected.address()) && !context.requiresAttribution()
            ? new SourceContext(context.address(), context.feature(), context.origins(), Optional.of(value), false) : context).toList());
    var attributed = new AttributedCoverageExpectations(inventory, evidence.attributedExpectations().projections().stream().map(projection ->
        projection.address().equals(selected.address()) ? new AttributedCoverageExpectations.Projection(projection.address(), value, projection.ownerIdentity()) : projection).toList());
    var outcomes = evidence.outcomes().stream().map(outcome -> outcome.address().equals(selected.address())
        ? new FeatureContextOutcome(outcome.address(), outcome.occurrence(), outcome.observation(), Optional.of(value),
            outcome.support().mode() == FeatureContextOutcome.Mode.NEGATIVE
                ? new FeatureContextOutcome.Support(FeatureContextOutcome.Mode.NEGATIVE, List.of(), Optional.empty(), Optional.empty(), Optional.of(value))
                : outcome.support(), outcome.emissions()) : outcome).toList();
    return CoverageAdmission.fromEvidence(original.languageFeatureCoverage(), inventory, attributed, outcomes, evidence.unmatchedFacts(), evidence.diagnosticEmissions());
  }

  @TestFactory
  Stream<DynamicTest> givenWrittenExceptionProperties_whenAClaimDropsTheirValues_thenSourceCoverageRejectsFalseCompletion() {
    return Stream.of("TryTree::TryTree#getResources()", "TryTree::TryTree#getCatches()", "TryTree::TryTree#getFinallyBlock()",
        "CatchTree::CatchTree#getParameter()", "UnionTypeTree::UnionTypeTree#getTypeAlternatives()")
        .map(accessor -> DynamicTest.dynamicTest(accessor, () -> {
          // Arrange
          var original = analyze("class Example { void subject() throws Exception { try (AutoCloseable first = null; var second = first) {} catch (IllegalArgumentException | IllegalStateException failure) {} finally {} } }");
          var list = accessor.endsWith("getResources()") || accessor.endsWith("getCatches()") || accessor.endsWith("getTypeAlternatives()");
          var selected = property(original, accessor, list ? CoveragePropertyValue.State.ITEMS : CoveragePropertyValue.State.SCALAR);
          // Act
          var failure = assertThrows(IllegalArgumentException.class, () -> claimedValue(original, selected,
              new CoveragePropertyValue(list ? CoveragePropertyValue.State.EMPTY : CoveragePropertyValue.State.ABSENT, List.of())));
          // Assert
          assertEquals("Represented success requires its exact catalog support mode", failure.getMessage());
          assertEquals(FeatureContextOutcome.Mode.FACTS, selected.support().mode());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenOrderedExceptionProperties_whenAClaimReversesTheWrittenSequence_thenPublicInputRejectsTheFalseOrder() {
    return Stream.of("TryTree::TryTree#getResources()", "TryTree::TryTree#getCatches()", "UnionTypeTree::UnionTypeTree#getTypeAlternatives()")
        .map(accessor -> DynamicTest.dynamicTest(accessor, () -> {
          // Arrange
          var original = analyze("class Example { void subject() throws Exception { try (AutoCloseable first = null; var second = first) {} catch (IllegalArgumentException | IllegalStateException failure) {} catch (RuntimeException remaining) {} } }");
          var selected = property(original, accessor, CoveragePropertyValue.State.ITEMS);
          var supplied = claimedValue(original, selected, new CoveragePropertyValue(CoveragePropertyValue.State.ITEMS,
              selected.encounteredValue().orElseThrow().items().reversed()));
          // Act
          var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environmentSnapshot(),
              original.program(), original.aggregate(), original.diagnostics(), supplied));
          // Assert
          assertEquals("Claimed accessor support disagrees with the actual retained owner facts", failure.getMessage());
          assertEquals(2, selected.encounteredValue().orElseThrow().items().size());
        }));
  }

  @Test
  void givenExceptionFactsWithheld_whenSupportIsReconciled_thenMissingRequiredSourceEvidenceRemainsBlocking() throws IOException {
    // Arrange
    var original = analyze("class Example { void subject() throws Exception { try (AutoCloseable first = null) {} catch (IllegalArgumentException | IllegalStateException failure) {} finally {} } }");
    var diagnostics = JavaCoverageTestSupport.emissions(original);
    // Act
    var partial = JavaCoverageTestSupport.withFacts(original, new JavaProgram(List.of()), Optional.empty(), diagnostics);
    // Assert
    assertEquals(original.languageFeatureCoverageResult().occurrences(), partial.languageFeatureCoverageResult().occurrences());
    assertEquals(original.languageFeatureCoverageResult().observations(), partial.languageFeatureCoverageResult().observations());
    assertTrue(partial.languageFeatureCoverageResult().validationEvidence().outcomes().stream().filter(outcome -> outcome.address().role().startsWith("TryTree::")
        || outcome.address().role().startsWith("CatchTree::") || outcome.address().role().startsWith("UnionTypeTree::"))
        .anyMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
    assertTrue(partial.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-source-evidence") && diagnostic.isError()));
  }

  private static SyntaxAnalysisInput analyze(String content) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/coverage-check/sources"),
        List.of(SourceFile.fromContent(Path.of("Example.java"), content)));
    return new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CONFIGURATION, JavaTranslationPolicy.legacyAnalysis());
  }
}
