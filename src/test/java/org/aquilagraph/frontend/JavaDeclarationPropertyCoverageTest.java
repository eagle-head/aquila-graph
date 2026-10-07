package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.junit.jupiter.api.Test;

class JavaDeclarationPropertyCoverageTest {
  private static final CompilerConfiguration JAVA_25_CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final Set<String> EXPECTED_PROPERTIES = Set.of(
      "AssertTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "AssertTree::Tree#getKind()", "AssertTree::AssertTree#getCondition()", "AssertTree::AssertTree#getDetail()",
      "SynchronizedTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "SynchronizedTree::Tree#getKind()", "SynchronizedTree::SynchronizedTree#getExpression()", "SynchronizedTree::SynchronizedTree#getBlock()",
      "WhileLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "WhileLoopTree::Tree#getKind()",
      "WhileLoopTree::WhileLoopTree#getCondition()",
      "WhileLoopTree::WhileLoopTree#getStatement()",
      "DoWhileLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "DoWhileLoopTree::Tree#getKind()",
      "DoWhileLoopTree::DoWhileLoopTree#getCondition()",
      "DoWhileLoopTree::DoWhileLoopTree#getStatement()",
      "ForLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "ForLoopTree::Tree#getKind()",
      "ForLoopTree::ForLoopTree#getInitializer()",
      "ForLoopTree::ForLoopTree#getCondition()",
      "ForLoopTree::ForLoopTree#getUpdate()",
      "ForLoopTree::ForLoopTree#getStatement()",
      "EnhancedForLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "EnhancedForLoopTree::Tree#getKind()",
      "EnhancedForLoopTree::EnhancedForLoopTree#getVariable()",
      "EnhancedForLoopTree::EnhancedForLoopTree#getExpression()",
      "EnhancedForLoopTree::EnhancedForLoopTree#getStatement()",
      "LabeledStatementTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "LabeledStatementTree::Tree#getKind()",
      "LabeledStatementTree::LabeledStatementTree#getLabel()",
      "LabeledStatementTree::LabeledStatementTree#getStatement()",
      "BreakTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "BreakTree::Tree#getKind()",
      "BreakTree::BreakTree#getLabel()",
      "ContinueTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "ContinueTree::Tree#getKind()",
      "ContinueTree::ContinueTree#getLabel()",

      "NewArrayTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "NewArrayTree::Tree#getKind()", "NewArrayTree::NewArrayTree#getType()", "NewArrayTree::NewArrayTree#getDimensions()",
      "NewArrayTree::NewArrayTree#getInitializers()", "NewArrayTree::NewArrayTree#getAnnotations()", "NewArrayTree::NewArrayTree#getDimAnnotations()",
      "CompilationUnitTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "CompilationUnitTree::CompilationUnitTree#getImports()",
      "CompilationUnitTree::Tree#getKind()",
      "CompilationUnitTree::CompilationUnitTree#getLineMap()",
      "CompilationUnitTree::CompilationUnitTree#getModule()",
      "CompilationUnitTree::CompilationUnitTree#getPackage()",
      "CompilationUnitTree::CompilationUnitTree#getPackageAnnotations()",
      "CompilationUnitTree::CompilationUnitTree#getPackageName()",
      "CompilationUnitTree::CompilationUnitTree#getSourceFile()",
      "CompilationUnitTree::CompilationUnitTree#getTypeDecls()",
      "ClassTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "ClassTree::ClassTree#getExtendsClause()",
      "ClassTree::ClassTree#getImplementsClause()",
      "ClassTree::Tree#getKind()",
      "ClassTree::ClassTree#getMembers()",
      "ClassTree::ClassTree#getModifiers()",
      "ClassTree::ClassTree#getPermitsClause()",
      "ClassTree::ClassTree#getSimpleName()",
      "ClassTree::ClassTree#getTypeParameters()",
      "MethodTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "MethodTree::MethodTree#getBody()",
      "MethodTree::MethodTree#getDefaultValue()",
      "MethodTree::Tree#getKind()",
      "MethodTree::MethodTree#getModifiers()",
      "MethodTree::MethodTree#getName()",
      "MethodTree::MethodTree#getParameters()",
      "MethodTree::MethodTree#getReceiverParameter()",
      "MethodTree::MethodTree#getReturnType()",
      "MethodTree::MethodTree#getThrows()",
      "MethodTree::MethodTree#getTypeParameters()",
      "VariableTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "VariableTree::VariableTree#getInitializer()",
      "VariableTree::Tree#getKind()",
      "VariableTree::VariableTree#getModifiers()",
      "VariableTree::VariableTree#getName()",
      "VariableTree::VariableTree#getNameExpression()",
      "VariableTree::VariableTree#getType()",
      "TypeElement::Element#accept(javax.lang.model.element.ElementVisitor,java.lang.Object)",
      "TypeElement::TypeElement#asType()",
      "TypeElement::Element#equals(java.lang.Object)",
      "TypeElement::Element#getAnnotation(java.lang.Class)",
      "TypeElement::Element#getAnnotationMirrors()",
      "TypeElement::Element#getAnnotationsByType(java.lang.Class)",
      "TypeElement::TypeElement#getEnclosedElements()",
      "TypeElement::TypeElement#getEnclosingElement()",
      "TypeElement::TypeElement#getInterfaces()",
      "TypeElement::Element#getKind()",
      "TypeElement::Element#getModifiers()",
      "TypeElement::TypeElement#getNestingKind()",
      "TypeElement::TypeElement#getPermittedSubclasses()",
      "TypeElement::TypeElement#getQualifiedName()",
      "TypeElement::TypeElement#getRecordComponents()",
      "TypeElement::TypeElement#getSimpleName()",
      "TypeElement::TypeElement#getSuperclass()",
      "TypeElement::TypeElement#getTypeParameters()",
      "TypeElement::Element#hashCode()",
      "ExecutableElement::Element#accept(javax.lang.model.element.ElementVisitor,java.lang.Object)",
      "ExecutableElement::ExecutableElement#asType()",
      "ExecutableElement::Element#equals(java.lang.Object)",
      "ExecutableElement::Element#getAnnotation(java.lang.Class)",
      "ExecutableElement::Element#getAnnotationMirrors()",
      "ExecutableElement::Element#getAnnotationsByType(java.lang.Class)",
      "ExecutableElement::ExecutableElement#getDefaultValue()",
      "ExecutableElement::Element#getEnclosedElements()",
      "ExecutableElement::ExecutableElement#getEnclosingElement()",
      "ExecutableElement::Element#getKind()",
      "ExecutableElement::Element#getModifiers()",
      "ExecutableElement::ExecutableElement#getParameters()",
      "ExecutableElement::ExecutableElement#getReceiverType()",
      "ExecutableElement::ExecutableElement#getReturnType()",
      "ExecutableElement::ExecutableElement#getSimpleName()",
      "ExecutableElement::ExecutableElement#getThrownTypes()",
      "ExecutableElement::ExecutableElement#getTypeParameters()",
      "ExecutableElement::Element#hashCode()",
      "ExecutableElement::ExecutableElement#isDefault()",
      "ExecutableElement::ExecutableElement#isVarArgs()");
  private static final Set<String> EXPECTED_IRRELEVANT_PROPERTIES = Set.of(
      "AssertTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "SynchronizedTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "WhileLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "DoWhileLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "ForLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "EnhancedForLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "LabeledStatementTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "BreakTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "ContinueTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",

      "NewArrayTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "CompilationUnitTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "CompilationUnitTree::CompilationUnitTree#getLineMap()",
      "ClassTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "MethodTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "VariableTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)",
      "TypeElement::Element#accept(javax.lang.model.element.ElementVisitor,java.lang.Object)",
      "TypeElement::Element#equals(java.lang.Object)",
      "TypeElement::Element#getAnnotation(java.lang.Class)",
      "TypeElement::Element#getAnnotationsByType(java.lang.Class)",
      "TypeElement::Element#hashCode()",
      "ExecutableElement::Element#accept(javax.lang.model.element.ElementVisitor,java.lang.Object)",
      "ExecutableElement::Element#equals(java.lang.Object)",
      "ExecutableElement::Element#getAnnotation(java.lang.Class)",
      "ExecutableElement::Element#getAnnotationsByType(java.lang.Class)",
      "ExecutableElement::Element#getEnclosedElements()",
      "ExecutableElement::Element#hashCode()");
  private static final Set<String> EXPECTED_DIAGNOSED_PROPERTIES = Set.of();

  @Test
  void givenJava25DeclarationApis_whenPropertyCoverageIsCreated_thenEveryAccessorHasExactlyOnePolicy() {
    // Arrange
    var expectedProperties = EXPECTED_PROPERTIES;

    // Act
    var coverage = JavaDeclarationPropertyCoverage.fromConfiguration(JAVA_25_CONFIGURATION);
    var actualProperties = coverage.classifications().stream().map(classification -> classification.feature().identifier())
        .collect(Collectors.toUnmodifiableSet());

    // Assert
    assertEquals(JavaRelease.JAVA_25, coverage.release());
    assertEquals(expectedProperties, actualProperties);
    assertEquals(expectedProperties.size(), coverage.classifications().size());
    assertTrue(coverage.classifications().stream()
        .allMatch(classification -> classification.feature().category() == LanguageFeatureCategory.DECLARATION_PROPERTY));
  }

  @Test
  void givenReviewedPropertyPolicies_whenCoverageIsInspected_thenEachPolicyRetainsRequiredEvidence() {
    // Arrange
    var coverage = JavaDeclarationPropertyCoverage.fromConfiguration(JAVA_25_CONFIGURATION);

    // Act
    var irrelevantProperties = propertiesWithPolicy(coverage, LanguageFeaturePolicy.IRRELEVANT);
    var diagnosed = coverage.classifications().stream()
        .filter(classification -> classification.policy() == LanguageFeaturePolicy.DIAGNOSED).toList();
    var diagnosedProperties = propertiesWithPolicy(coverage, LanguageFeaturePolicy.DIAGNOSED);

    // Assert
    assertEquals(EXPECTED_IRRELEVANT_PROPERTIES, irrelevantProperties);
    assertEquals(EXPECTED_DIAGNOSED_PROPERTIES, diagnosedProperties);
    for (var identifier : List.of(JavaDeclarationPropertyCoverage.METHOD_DEFAULT_VALUE,
        JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_DEFAULT_VALUE, JavaDeclarationPropertyCoverage.TYPE_ELEMENT_ANNOTATION_MIRRORS,
        JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_ANNOTATION_MIRRORS)) {
      assertEquals(LanguageFeaturePolicy.REPRESENTED, policyOf(coverage, identifier));
      assertEquals(Optional.empty(), classificationOf(coverage, identifier).diagnosticCode());
      assertTrue(classificationOf(coverage, identifier).representationBoundary().orElseThrow().contains("JavaAstAggregate"));
    }
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_IMPORTS));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_MODULE));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_PACKAGE));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_PACKAGE_NAME));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, JavaDeclarationPropertyCoverage.COMPILATION_UNIT_PACKAGE_ANNOTATIONS));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, JavaDeclarationPropertyCoverage.VARIABLE_NAME_EXPRESSION));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, JavaDeclarationPropertyCoverage.METHOD_DEFAULT_VALUE));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_DEFAULT_VALUE));
    for (var represented : Set.of(JavaDeclarationPropertyCoverage.METHOD_TYPE_PARAMETERS, JavaDeclarationPropertyCoverage.METHOD_THROWS,
        JavaDeclarationPropertyCoverage.METHOD_RECEIVER_PARAMETER, JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_TYPE_PARAMETERS,
        JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_THROWN_TYPES,
        JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_RECEIVER_TYPE,
        JavaDeclarationPropertyCoverage.EXECUTABLE_ELEMENT_VARARGS)) {
      assertEquals(LanguageFeaturePolicy.REPRESENTED, policyOf(coverage, represented));
    }
    assertTrue(coverage.classifications().stream()
        .filter(classification -> classification.policy() == LanguageFeaturePolicy.REPRESENTED)
        .allMatch(classification -> classification.representationBoundary().isPresent()));
    assertTrue(coverage.classifications().stream()
        .filter(classification -> classification.policy() == LanguageFeaturePolicy.IRRELEVANT)
        .allMatch(classification -> classification.irrelevanceRationale().isPresent()
            && classification.sourceLocator().isPresent()));
    assertTrue(diagnosed.stream().allMatch(classification -> classification.blocksCompletion()
        && classification.diagnosticCode().orElseThrow().startsWith("frontend.unsupported-")));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, "MethodTree::MethodTree#getTypeParameters()"));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, "TypeElement::TypeElement#getRecordComponents()"));
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, "TypeElement::TypeElement#getEnclosedElements()"));
    assertEquals(LanguageFeaturePolicy.REPRESENTED, policyOf(coverage, "VariableTree::VariableTree#getInitializer()"));
    assertEquals(LanguageFeaturePolicy.REPRESENTED, policyOf(coverage, "VariableTree::VariableTree#getModifiers()"));
    assertEquals(Optional.empty(), classificationOf(coverage, "VariableTree::VariableTree#getInitializer()").diagnosticCode());
    assertEquals(Optional.empty(), classificationOf(coverage, "VariableTree::VariableTree#getModifiers()").diagnosticCode());
    assertEquals(LanguageFeaturePolicy.REPRESENTED,
        policyOf(coverage, "ExecutableElement::ExecutableElement#getParameters()"));
  }

  @Test
  void givenMissingAccessor_whenPropertyCoverageIsValidated_thenDeterministicDifferenceIsRejected() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.removeIf(classification -> classification.feature().identifier()
        .equals("MethodTree::MethodTree#getThrows()"));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaDeclarationPropertyCoverage.fromClassifications(JAVA_25_CONFIGURATION, classifications));

    // Assert
    assertEquals("Java declaration property coverage mismatch: missing=[MethodTree::MethodTree#getThrows()], extra=[]",
        exception.getMessage());
  }

  @Test
  void givenDuplicateAccessor_whenPropertyCoverageIsValidated_thenDuplicateIsRejectedBeforeKeyNormalization() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.add(classifications.stream().filter(classification -> classification.feature().identifier()
        .equals("MethodTree::MethodTree#getThrows()")).findFirst().orElseThrow());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaDeclarationPropertyCoverage.fromClassifications(JAVA_25_CONFIGURATION, classifications));

    // Assert
    assertEquals("Duplicate language feature classification: DECLARATION_PROPERTY:MethodTree::MethodTree#getThrows()",
        exception.getMessage());
  }

  @Test
  void givenRenamedAccessor_whenPropertyCoverageIsValidated_thenUnrecognizedIdentityIsRejected() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.removeIf(classification -> classification.feature().identifier()
        .equals("MethodTree::MethodTree#getThrows()"));
    classifications.add(LanguageFeatureClassification.diagnosed(
        new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY,
            "MethodTree::MethodTree#getThrownTypes()"),
        "frontend.unsupported-renamed-declaration-property"));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaDeclarationPropertyCoverage.fromClassifications(JAVA_25_CONFIGURATION, classifications));

    // Assert
    assertEquals("Unrecognized Java declaration property: MethodTree::MethodTree#getThrownTypes()",
        exception.getMessage());
  }

  @Test
  void givenExtraUnknownAccessor_whenPropertyCoverageIsValidated_thenUnrecognizedIdentityIsRejected() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.add(LanguageFeatureClassification.diagnosed(
        new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY,
            "MethodTree::MethodTree#getFutureProperty()"),
        "frontend.unsupported-future-declaration-property"));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaDeclarationPropertyCoverage.fromClassifications(JAVA_25_CONFIGURATION, classifications));

    // Assert
    assertEquals("Unrecognized Java declaration property: MethodTree::MethodTree#getFutureProperty()",
        exception.getMessage());
  }

  @Test
  void givenPolicylessAccessor_whenClassificationIsCreated_thenAbsentPolicyIsRejected() {
    // Arrange
    var feature = new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY,
        "MethodTree::MethodTree#getThrows()");

    // Act
    var exception = assertThrows(NullPointerException.class,
        () -> new LanguageFeatureClassification(feature, null, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty()));

    // Assert
    assertEquals("policy", exception.getMessage());
  }

  @Test
  void givenSameConfiguration_whenPropertyCoverageIsCreatedTwice_thenOrderingAndMetadataAreDeterministic() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var first = JavaDeclarationPropertyCoverage.fromConfiguration(configuration);
    var second = JavaDeclarationPropertyCoverage.fromConfiguration(configuration);

    // Assert
    assertEquals(first, second);
    assertEquals(first.toString(), second.toString());
    assertEquals(first.classifications(), second.classifications());
    assertEquals("java-language-feature-coverage:0acd09f319196e03148af21d885419a440820cc7506a081b9f8cad0222eecb91",
        JavaLanguageFeatureCoverage.fromConfiguration(configuration).contractIdentity());
  }

  private static ArrayList<LanguageFeatureClassification> mutableClassifications() {
    return new ArrayList<>(JavaDeclarationPropertyCoverage.fromConfiguration(JAVA_25_CONFIGURATION).classifications());
  }

  private static Set<String> propertiesWithPolicy(LanguageFeatureCoverage coverage, LanguageFeaturePolicy policy) {
    return coverage.classifications().stream().filter(classification -> classification.policy() == policy)
        .map(classification -> classification.feature().identifier()).collect(Collectors.toUnmodifiableSet());
  }

  private static LanguageFeaturePolicy policyOf(LanguageFeatureCoverage coverage, String identifier) {
    return classificationOf(coverage, identifier).policy();
  }

  private static LanguageFeatureClassification classificationOf(LanguageFeatureCoverage coverage,
      String identifier) {
    var feature = new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY, identifier);
    return coverage.classificationOf(feature).orElseThrow();
  }
}
