package org.aquilagraph.frontend;

import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.AnyPatternTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.VariableTree;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.TreeSet;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverage;

/** Defines and verifies the closed declaration-property coverage contract for Java 25. */
final class JavaDeclarationPropertyCoverage {
  static final String COMPILATION_UNIT_IMPORTS = "CompilationUnitTree::CompilationUnitTree#getImports()";
  static final String COMPILATION_UNIT_MODULE = "CompilationUnitTree::CompilationUnitTree#getModule()";
  static final String COMPILATION_UNIT_PACKAGE = "CompilationUnitTree::CompilationUnitTree#getPackage()";
  static final String COMPILATION_UNIT_PACKAGE_ANNOTATIONS = "CompilationUnitTree::CompilationUnitTree#getPackageAnnotations()";
  static final String COMPILATION_UNIT_PACKAGE_NAME = "CompilationUnitTree::CompilationUnitTree#getPackageName()";
  static final String METHOD_DEFAULT_VALUE = "MethodTree::MethodTree#getDefaultValue()";
  static final String METHOD_RECEIVER_PARAMETER = "MethodTree::MethodTree#getReceiverParameter()";
  static final String METHOD_THROWS = "MethodTree::MethodTree#getThrows()";
  static final String METHOD_TYPE_PARAMETERS = "MethodTree::MethodTree#getTypeParameters()";
  static final String VARIABLE_INITIALIZER = "VariableTree::VariableTree#getInitializer()";
  static final String VARIABLE_MODIFIERS = "VariableTree::VariableTree#getModifiers()";
  static final String VARIABLE_NAME_EXPRESSION = "VariableTree::VariableTree#getNameExpression()";
  static final String TYPE_ELEMENT_ANNOTATION_MIRRORS = "TypeElement::Element#getAnnotationMirrors()";
  static final String TYPE_ELEMENT_ENCLOSED_ELEMENTS = "TypeElement::TypeElement#getEnclosedElements()";
  static final String TYPE_ELEMENT_RECORD_COMPONENTS = "TypeElement::TypeElement#getRecordComponents()";
  static final String EXECUTABLE_ELEMENT_ANNOTATION_MIRRORS = "ExecutableElement::Element#getAnnotationMirrors()";
  static final String EXECUTABLE_ELEMENT_DEFAULT_VALUE = "ExecutableElement::ExecutableElement#getDefaultValue()";
  static final String EXECUTABLE_ELEMENT_RECEIVER_TYPE = "ExecutableElement::ExecutableElement#getReceiverType()";
  static final String EXECUTABLE_ELEMENT_THROWN_TYPES = "ExecutableElement::ExecutableElement#getThrownTypes()";
  static final String EXECUTABLE_ELEMENT_TYPE_PARAMETERS = "ExecutableElement::ExecutableElement#getTypeParameters()";
  static final String EXECUTABLE_ELEMENT_VARARGS = "ExecutableElement::ExecutableElement#isVarArgs()";

  private static final List<Class<?>> DECLARATION_SURFACES = List.of(CompilationUnitTree.class, ClassTree.class, MethodTree.class,
      VariableTree.class, NewArrayTree.class, AssertTree.class, SynchronizedTree.class, LambdaExpressionTree.class,
      MemberReferenceTree.class, InstanceOfTree.class, BindingPatternTree.class, DeconstructionPatternTree.class, AnyPatternTree.class,
      SwitchTree.class, SwitchExpressionTree.class, CaseTree.class, YieldTree.class, WhileLoopTree.class, DoWhileLoopTree.class,
      ForLoopTree.class, EnhancedForLoopTree.class, LabeledStatementTree.class, BreakTree.class, ContinueTree.class, TryTree.class,
      CatchTree.class, UnionTypeTree.class, TypeElement.class, ExecutableElement.class);
  private static final String STATEMENT_KIND_BOUNDARY = "Canonical written statement kind";
  private static final String STATEMENT_VALUE_BOUNDARY = "Canonical written statement value and ordered children";
  private static final String TREE_API = "https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/";
  private static final String JAVA_COMPILER_API = "https://docs.oracle.com/en/java/javase/25/docs/api/java.compiler/";
  private static final String ELEMENT_API = JAVA_COMPILER_API + "javax/lang/model/element/";
  private static final String ANNOTATED_CONSTRUCT_API = JAVA_COMPILER_API + "javax/lang/model/AnnotatedConstruct.html";
  private static final String ANNOTATION_PROXY_REASON = "Annotation proxy lookup is not a separate declaration fact";
  private static final String TREE_VISITOR_REASON = "Visitor dispatch is a traversal protocol, not a Java declaration fact";
  private static final String TREE_ACCEPT_SOURCE = TREE_API + "Tree.html#accept(V,D)";

  private JavaDeclarationPropertyCoverage() {
  }

  static LanguageFeatureCoverage fromConfiguration(CompilerConfiguration configuration) {
    var coverage = fromClassifications(configuration, classifications());
    JavaDeclarationAccessor.validateCatalog(DECLARATION_SURFACES, coverage);
    return coverage;
  }

  static boolean isDeclarationSurface(Class<?> surface) {
    return DECLARATION_SURFACES.contains(surface);
  }

  static LanguageFeatureCoverage fromClassifications(CompilerConfiguration configuration,
      List<LanguageFeatureClassification> classifications) {
    Objects.requireNonNull(configuration, "configuration");
    var coverage = LanguageFeatureCoverageFactory.fromConfiguration(configuration, classifications);
    var expected = accessorIdentifiers();
    var actual = new TreeSet<String>();
    var unrecognized = new TreeSet<String>();
    for (var classification : coverage.classifications()) {
      var feature = classification.feature();
      if (feature.category() != LanguageFeatureCategory.DECLARATION_PROPERTY) {
        throw new IllegalArgumentException("Java declaration property coverage contains a non-property feature: " + feature.identifier());
      }
      if (!expected.contains(feature.identifier())) {
        unrecognized.add(feature.identifier());
      }
      actual.add(feature.identifier());
    }
    if (!unrecognized.isEmpty()) {
      var message = unrecognized.size() == 1
          ? "Unrecognized Java declaration property: " + unrecognized.getFirst()
          : "Unrecognized Java declaration properties: " + unrecognized;
      throw new IllegalArgumentException(message);
    }
    var missing = new TreeSet<>(expected);
    missing.removeAll(actual);
    var extra = new TreeSet<>(actual);
    extra.removeAll(expected);
    if (!missing.isEmpty() || !extra.isEmpty()) {
      throw new IllegalArgumentException("Java declaration property coverage mismatch: missing=" + missing + ", extra=" + extra);
    }
    return coverage;
  }

  private static List<LanguageFeatureClassification> classifications() {
    var classifications = new ArrayList<LanguageFeatureClassification>();
    classifications.addAll(compilationUnitClassifications());
    classifications.addAll(classClassifications());
    classifications.addAll(methodClassifications());
    classifications.addAll(variableClassifications());
    classifications.addAll(arrayClassifications());
    classifications.addAll(iterationClassifications());
    classifications.addAll(assertionMonitorClassifications());
    classifications.addAll(lambdaClassifications());
    classifications.addAll(referenceClassifications());
    classifications.addAll(patternClassifications());
    classifications.addAll(switchClassifications());
    classifications.addAll(exceptionClassifications());
    classifications.addAll(typeElementClassifications());
    classifications.addAll(executableElementClassifications());
    return List.copyOf(classifications);
  }

  private static List<LanguageFeatureClassification> assertionMonitorClassifications() {
    return List.of(
        irrelevant("AssertTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE),
        represented("AssertTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY),
        represented("AssertTree::AssertTree#getCondition()", STATEMENT_VALUE_BOUNDARY),
        represented("AssertTree::AssertTree#getDetail()", STATEMENT_VALUE_BOUNDARY),
        irrelevant("SynchronizedTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
            TREE_ACCEPT_SOURCE),
        represented("SynchronizedTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY),
        represented("SynchronizedTree::SynchronizedTree#getExpression()", STATEMENT_VALUE_BOUNDARY),
        represented("SynchronizedTree::SynchronizedTree#getBlock()", STATEMENT_VALUE_BOUNDARY));
  }

  private static List<LanguageFeatureClassification> exceptionClassifications() {
    var result = new ArrayList<LanguageFeatureClassification>();
    for (var surface : List.of(TryTree.class, CatchTree.class, UnionTypeTree.class)) {
      var name = surface.getSimpleName();
      result.add(
          irrelevant(name + "::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE));
      result.add(represented(name + "::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
    }
    for (var accessor : List.of("getResources", "getBlock", "getCatches", "getFinallyBlock")) {
      result.add(
          represented("TryTree::TryTree#" + accessor + "()", "Ordered written resources, try body, catches, and optional finally body"));
    }
    for (var accessor : List.of("getParameter", "getBlock")) {
      result.add(represented("CatchTree::CatchTree#" + accessor + "()", "Written catch binding and body"));
    }
    result.add(represented("UnionTypeTree::UnionTypeTree#getTypeAlternatives()", "Every ordered written catch type alternative"));
    return List.copyOf(result);
  }

  private static List<LanguageFeatureClassification> referenceClassifications() {
    return List.of(
        irrelevant("MemberReferenceTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
            TREE_ACCEPT_SOURCE),
        represented("MemberReferenceTree::Tree#getKind()", "Canonical written reference kind"),
        represented("MemberReferenceTree::MemberReferenceTree#getMode()", "Method or constructor reference mode"),
        represented("MemberReferenceTree::MemberReferenceTree#getName()", "Written reference name"),
        represented("MemberReferenceTree::MemberReferenceTree#getQualifierExpression()", "Written reference qualifier subtree"),
        represented("MemberReferenceTree::MemberReferenceTree#getTypeArguments()", "Ordered explicit reference type arguments"));
  }

  private static List<LanguageFeatureClassification> switchClassifications() {
    var result = new ArrayList<LanguageFeatureClassification>();
    for (var surface : List.of(SwitchTree.class, SwitchExpressionTree.class, CaseTree.class, YieldTree.class)) {
      var name = surface.getSimpleName();
      result.add(
          irrelevant(name + "::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE));
      result.add(represented(name + "::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
    }
    for (var surface : List.of("SwitchTree", "SwitchExpressionTree")) {
      result.add(represented(surface + "::" + surface + "#getExpression()", "Written switch selector"));
      result.add(represented(surface + "::" + surface + "#getCases()", "Ordered written switch cases including an empty block"));
    }
    for (var accessor : List.of("getLabels", "getGuard", "getBody", "getStatements", "getCaseKind", "getExpressions")) {
      result.add(represented("CaseTree::CaseTree#" + accessor + "()", "Written case labels, guard, and form-specific body or absence"));
    }
    result.add(irrelevant("CaseTree::CaseTree#getExpression()",
        "Deprecated first-label alias; getExpressions and getLabels retain the complete list", TREE_API + "CaseTree.html#getExpression()"));
    result.add(represented("YieldTree::YieldTree#getValue()", "Written yielded expression"));
    return List.copyOf(result);
  }

  private static List<LanguageFeatureClassification> patternClassifications() {
    return List.of(
        irrelevant("InstanceOfTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
            TREE_ACCEPT_SOURCE),
        represented("InstanceOfTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY),
        represented("InstanceOfTree::InstanceOfTree#getExpression()", "Written tested expression"),
        represented("InstanceOfTree::InstanceOfTree#getType()", "Traditional written type or shared binding-pattern type"),
        represented("InstanceOfTree::InstanceOfTree#getPattern()", "One written pattern subtree or explicit absence"),
        irrelevant("BindingPatternTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
            TREE_ACCEPT_SOURCE),
        represented("BindingPatternTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY),
        represented("BindingPatternTree::BindingPatternTree#getVariable()", "Written named or unnamed pattern binding"),
        irrelevant("DeconstructionPatternTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
            TREE_ACCEPT_SOURCE),
        represented("DeconstructionPatternTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY),
        represented("DeconstructionPatternTree::DeconstructionPatternTree#getDeconstructor()", "Written record type subtree"),
        represented("DeconstructionPatternTree::DeconstructionPatternTree#getNestedPatterns()",
            "Ordered record component patterns including empty lists"),
        irrelevant("AnyPatternTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
            TREE_ACCEPT_SOURCE),
        represented("AnyPatternTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
  }

  private static List<LanguageFeatureClassification> lambdaClassifications() {
    return List.of(
        irrelevant("LambdaExpressionTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
            TREE_ACCEPT_SOURCE),
        represented("LambdaExpressionTree::Tree#getKind()", "Canonical written lambda kind"),
        represented("LambdaExpressionTree::LambdaExpressionTree#getParameters()", "Ordered written lambda bindings"),
        represented("LambdaExpressionTree::LambdaExpressionTree#getBody()", "Written lambda expression or block body"),
        represented("LambdaExpressionTree::LambdaExpressionTree#getBodyKind()", "Written lambda body distinction"));
  }

  private static List<LanguageFeatureClassification> iterationClassifications() {
    var result = new ArrayList<LanguageFeatureClassification>();
    result.add(irrelevant("WhileLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
        TREE_ACCEPT_SOURCE));
    result.add(represented("WhileLoopTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
    result.add(represented("WhileLoopTree::WhileLoopTree#getCondition()", STATEMENT_VALUE_BOUNDARY));
    result.add(represented("WhileLoopTree::WhileLoopTree#getStatement()", STATEMENT_VALUE_BOUNDARY));
    result.add(irrelevant("DoWhileLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
        TREE_ACCEPT_SOURCE));
    result.add(represented("DoWhileLoopTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
    result.add(represented("DoWhileLoopTree::DoWhileLoopTree#getCondition()", STATEMENT_VALUE_BOUNDARY));
    result.add(represented("DoWhileLoopTree::DoWhileLoopTree#getStatement()", STATEMENT_VALUE_BOUNDARY));
    result.add(
        irrelevant("ForLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE));
    result.add(represented("ForLoopTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
    result.add(represented("ForLoopTree::ForLoopTree#getInitializer()", STATEMENT_VALUE_BOUNDARY));
    result.add(represented("ForLoopTree::ForLoopTree#getCondition()", STATEMENT_VALUE_BOUNDARY));
    result.add(represented("ForLoopTree::ForLoopTree#getUpdate()", STATEMENT_VALUE_BOUNDARY));
    result.add(represented("ForLoopTree::ForLoopTree#getStatement()", STATEMENT_VALUE_BOUNDARY));
    result.add(irrelevant("EnhancedForLoopTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
        TREE_ACCEPT_SOURCE));
    result.add(represented("EnhancedForLoopTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
    result.add(represented("EnhancedForLoopTree::EnhancedForLoopTree#getVariable()", STATEMENT_VALUE_BOUNDARY));
    result.add(represented("EnhancedForLoopTree::EnhancedForLoopTree#getExpression()", STATEMENT_VALUE_BOUNDARY));
    result.add(represented("EnhancedForLoopTree::EnhancedForLoopTree#getStatement()", STATEMENT_VALUE_BOUNDARY));
    result.add(irrelevant("LabeledStatementTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
        TREE_ACCEPT_SOURCE));
    result.add(represented("LabeledStatementTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
    result.add(represented("LabeledStatementTree::LabeledStatementTree#getLabel()", STATEMENT_VALUE_BOUNDARY));
    result.add(represented("LabeledStatementTree::LabeledStatementTree#getStatement()", STATEMENT_VALUE_BOUNDARY));
    result.add(
        irrelevant("BreakTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE));
    result.add(represented("BreakTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
    result.add(represented("BreakTree::BreakTree#getLabel()", STATEMENT_VALUE_BOUNDARY));
    result.add(
        irrelevant("ContinueTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE));
    result.add(represented("ContinueTree::Tree#getKind()", STATEMENT_KIND_BOUNDARY));
    result.add(represented("ContinueTree::ContinueTree#getLabel()", STATEMENT_VALUE_BOUNDARY));
    return List.copyOf(result);
  }

  private static List<LanguageFeatureClassification> arrayClassifications() {
    return List.of(
        irrelevant("NewArrayTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE),
        represented("NewArrayTree::Tree#getKind()", "JavaCompilerKindCoverage exact tree-kind contract"),
        represented("NewArrayTree::NewArrayTree#getType()", "JavaAstAggregate optional written array component type"),
        represented("NewArrayTree::NewArrayTree#getDimensions()", "JavaAstAggregate ordered sized dimension expressions"),
        represented("NewArrayTree::NewArrayTree#getInitializers()", "JavaAstAggregate absent, empty or ordered initializer elements"),
        represented("NewArrayTree::NewArrayTree#getAnnotations()", "JavaAstAggregate initialized creation first dimension annotations"),
        represented("NewArrayTree::NewArrayTree#getDimAnnotations()", "JavaAstAggregate ordered sized dimension annotation groups"));
  }

  private static List<LanguageFeatureClassification> compilationUnitClassifications() {
    return List.of(
        irrelevant("CompilationUnitTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON,
            TREE_ACCEPT_SOURCE),
        represented(COMPILATION_UNIT_IMPORTS, "JavaAstAggregate ordered import declarations"),
        represented("CompilationUnitTree::Tree#getKind()", "JavaCompilerKindCoverage exact tree-kind contract"),
        irrelevant("CompilationUnitTree::CompilationUnitTree#getLineMap()",
            "SourceFile content and SourceSpan offsets own source evidence; line-number conversion is presentation metadata",
            TREE_API + "CompilationUnitTree.html#getLineMap()"),
        represented(COMPILATION_UNIT_MODULE, "JavaAstAggregate source-owned module declaration and directives"),
        represented(COMPILATION_UNIT_PACKAGE, "JavaAstAggregate source-owned package declaration"),
        represented(COMPILATION_UNIT_PACKAGE_ANNOTATIONS, "JavaAstAggregate ordered package annotation attachments"),
        represented(COMPILATION_UNIT_PACKAGE_NAME, "JavaAstAggregate package qualified name"),
        represented("CompilationUnitTree::CompilationUnitTree#getSourceFile()", "SourceFile input snapshot"),
        represented("CompilationUnitTree::CompilationUnitTree#getTypeDecls()", "JavaProgram top-level type declarations"));
  }

  private static List<LanguageFeatureClassification> classClassifications() {
    return List.of(
        irrelevant("ClassTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE),
        represented("ClassTree::ClassTree#getExtendsClause()", "JavaType direct superclass reference"),
        represented("ClassTree::ClassTree#getImplementsClause()", "JavaType direct interface references"),
        represented("ClassTree::Tree#getKind()", "JavaType declaration kind"),
        represented("ClassTree::ClassTree#getMembers()", "JavaType field, method, and nested-type declarations"),
        represented("ClassTree::ClassTree#getModifiers()", "JavaType modifier set and source annotation attachments"),
        represented("ClassTree::ClassTree#getPermitsClause()", "JavaType permitted-subclass references"),
        represented("ClassTree::ClassTree#getSimpleName()", "JavaType qualified name and declaration identity"),
        represented("ClassTree::ClassTree#getTypeParameters()", "JavaType type-parameter aggregates"));
  }

  private static List<LanguageFeatureClassification> methodClassifications() {
    return List.of(
        irrelevant("MethodTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE),
        represented("MethodTree::MethodTree#getBody()", "MethodSyntax body occurrence and JavaMethod body-presence flag"),
        represented(METHOD_DEFAULT_VALUE, "JavaAstAggregate declaration-owned typed annotation default"),
        represented("MethodTree::Tree#getKind()", "JavaMethod declaration aggregate"),
        represented("MethodTree::MethodTree#getModifiers()", "JavaMethod modifier set and source annotation attachments"),
        represented("MethodTree::MethodTree#getName()", "JavaMethod name and declaration identity"),
        represented("MethodTree::MethodTree#getParameters()", "JavaMethod ordered JavaParameter aggregates"),
        represented(METHOD_RECEIVER_PARAMETER, "JavaAstAggregate explicit receiver syntax occurrence"),
        represented("MethodTree::MethodTree#getReturnType()", "JavaAstAggregate written return-type attachment"),
        represented(METHOD_THROWS, "JavaAstAggregate ordered thrown-type attachments"),
        represented(METHOD_TYPE_PARAMETERS, "JavaAstAggregate ordered executable type-parameter declarations"));
  }

  private static List<LanguageFeatureClassification> variableClassifications() {
    return List.of(
        irrelevant("VariableTree::Tree#accept(com.sun.source.tree.TreeVisitor,java.lang.Object)", TREE_VISITOR_REASON, TREE_ACCEPT_SOURCE),
        represented(VARIABLE_INITIALIZER, "JavaAstAggregate field and enum initializer presence; expression content remains diagnosed"),
        represented("VariableTree::Tree#getKind()", "JavaField, JavaParameter, or MethodSyntax declaration aggregate"),
        represented(VARIABLE_MODIFIERS, "JavaAstAggregate written declaration modifiers and annotation attachment points"),
        represented("VariableTree::VariableTree#getName()", "JavaField or JavaParameter name"),
        represented(VARIABLE_NAME_EXPRESSION, "JavaAstAggregate receiver qualifier source occurrence"),
        represented("VariableTree::VariableTree#getType()", "JavaField or JavaParameter type identity"));
  }

  private static List<LanguageFeatureClassification> typeElementClassifications() {
    return List.of(
        irrelevant("TypeElement::Element#accept(javax.lang.model.element.ElementVisitor,java.lang.Object)",
            "Visitor dispatch is an element traversal protocol, not a Java declaration fact",
            ELEMENT_API + "Element.html#accept(javax.lang.model.element.ElementVisitor,P)"),
        represented("TypeElement::TypeElement#asType()", "JavaType declaration identity"),
        irrelevant("TypeElement::Element#equals(java.lang.Object)",
            "Element object equality is a compiler-model protocol; Aquila Graph uses stable declaration keys",
            ELEMENT_API + "Element.html#equals(java.lang.Object)"),
        irrelevant("TypeElement::Element#getAnnotation(java.lang.Class)", ANNOTATION_PROXY_REASON,
            ANNOTATED_CONSTRUCT_API + "#getAnnotation(java.lang.Class)"),
        represented(TYPE_ELEMENT_ANNOTATION_MIRRORS, "JavaAstAggregate reconciled written type annotations"),
        irrelevant("TypeElement::Element#getAnnotationsByType(java.lang.Class)", ANNOTATION_PROXY_REASON,
            ANNOTATED_CONSTRUCT_API + "#getAnnotationsByType(java.lang.Class)"),
        represented(TYPE_ELEMENT_ENCLOSED_ELEMENTS, "JavaType direct declaration inventory with typed synthesized counterparts"),
        represented("TypeElement::TypeElement#getEnclosingElement()", "JavaType enclosing-type reference"),
        represented("TypeElement::TypeElement#getInterfaces()", "JavaType direct interface references"),
        represented("TypeElement::Element#getKind()", "JavaType declaration kind"),
        represented("TypeElement::Element#getModifiers()", "JavaType modifier set"),
        represented("TypeElement::TypeElement#getNestingKind()", "JavaType enclosing-type reference and qualified identity"),
        represented("TypeElement::TypeElement#getPermittedSubclasses()", "JavaType permitted-subclass references"),
        represented("TypeElement::TypeElement#getQualifiedName()", "JavaType qualified name"),
        represented(TYPE_ELEMENT_RECORD_COMPONENTS, "JavaType ordered JavaRecordComponent declarations"),
        represented("TypeElement::TypeElement#getSimpleName()", "JavaType qualified name and declaration identity"),
        represented("TypeElement::TypeElement#getSuperclass()", "JavaType direct superclass reference"),
        represented("TypeElement::TypeElement#getTypeParameters()", "JavaType type-parameter aggregates"),
        irrelevant("TypeElement::Element#hashCode()",
            "Element object hashing is a compiler-model protocol; Aquila Graph uses stable declaration keys",
            ELEMENT_API + "Element.html#hashCode()"));
  }

  private static List<LanguageFeatureClassification> executableElementClassifications() {
    return List.of(
        irrelevant("ExecutableElement::Element#accept(javax.lang.model.element.ElementVisitor,java.lang.Object)",
            "Visitor dispatch is an element traversal protocol, not a Java declaration fact",
            ELEMENT_API + "Element.html#accept(javax.lang.model.element.ElementVisitor,P)"),
        represented("ExecutableElement::ExecutableElement#asType()", "JavaMethod signature, parameter, and return-type aggregates"),
        irrelevant("ExecutableElement::Element#equals(java.lang.Object)",
            "Element object equality is a compiler-model protocol; Aquila Graph uses stable declaration keys",
            ELEMENT_API + "Element.html#equals(java.lang.Object)"),
        irrelevant("ExecutableElement::Element#getAnnotation(java.lang.Class)", ANNOTATION_PROXY_REASON,
            ANNOTATED_CONSTRUCT_API + "#getAnnotation(java.lang.Class)"),
        represented(EXECUTABLE_ELEMENT_ANNOTATION_MIRRORS, "JavaAstAggregate reconciled written executable annotations"),
        irrelevant("ExecutableElement::Element#getAnnotationsByType(java.lang.Class)", ANNOTATION_PROXY_REASON,
            ANNOTATED_CONSTRUCT_API + "#getAnnotationsByType(java.lang.Class)"),
        represented(EXECUTABLE_ELEMENT_DEFAULT_VALUE, "JavaAstAggregate attributed annotation default value"),
        irrelevant("ExecutableElement::Element#getEnclosedElements()",
            "Executable elements do not contribute separately queryable enclosed declarations to the current semantic graph",
            ELEMENT_API + "ExecutableElement.html"),
        represented("ExecutableElement::ExecutableElement#getEnclosingElement()", "JavaMethod owner type key"),
        represented("ExecutableElement::Element#getKind()", "JavaMethod constructor flag"),
        represented("ExecutableElement::Element#getModifiers()", "JavaMethod modifier set"),
        represented("ExecutableElement::ExecutableElement#getParameters()", "JavaMethod ordered JavaParameter aggregates"),
        represented(EXECUTABLE_ELEMENT_RECEIVER_TYPE, "JavaAstAggregate compiler-attributed receiver-type outcome"),
        represented("ExecutableElement::ExecutableElement#getReturnType()", "JavaMethod return type"),
        represented("ExecutableElement::ExecutableElement#getSimpleName()", "JavaMethod name and declaration identity"),
        represented(EXECUTABLE_ELEMENT_THROWN_TYPES, "JavaAstAggregate ordered written attachments and attributed thrown types"),
        represented(EXECUTABLE_ELEMENT_TYPE_PARAMETERS, "JavaAstAggregate ordered executable type parameters"),
        irrelevant("ExecutableElement::Element#hashCode()",
            "Element object hashing is a compiler-model protocol; Aquila Graph uses stable declaration keys",
            ELEMENT_API + "Element.html#hashCode()"),
        represented("ExecutableElement::ExecutableElement#isDefault()", "JavaMethod modifier set"),
        represented(EXECUTABLE_ELEMENT_VARARGS, "JavaAstAggregate executable and final formal-parameter arity facts"));
  }

  private static Set<String> accessorIdentifiers() {
    return DECLARATION_SURFACES.stream().flatMap(surface -> Arrays.stream(surface.getMethods())
        .filter(method -> !Modifier.isStatic(method.getModifiers())).map(method -> accessorIdentifier(surface, method)))
        .collect(Collectors.toUnmodifiableSet());
  }

  static String accessorIdentifier(Class<?> surface, Method method) {
    var parameters = Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(","));
    return surface.getSimpleName() + "::" + method.getDeclaringClass().getSimpleName() + '#' + method.getName() + '(' + parameters + ')';
  }

  private static LanguageFeatureClassification represented(String identifier, String boundary) {
    return LanguageFeatureClassification.represented(feature(identifier), boundary);
  }

  private static LanguageFeatureClassification irrelevant(String identifier, String rationale, String sourceLocator) {
    return LanguageFeatureClassification.irrelevant(feature(identifier), rationale, sourceLocator);
  }

  private static LanguageFeature feature(String identifier) {
    return new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY, identifier);
  }
}
