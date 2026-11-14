package org.aquilagraph.frontend.internal;

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
import com.sun.source.tree.MemberSelectTree;
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
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
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
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import java.util.Optional;
import java.util.Comparator;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverage;

/** Owns typed declaration-property descriptors and independently validates the Java 25 surface inventory. */
final class JavaDeclarationPropertyCoverage {
  private static final String MODIFIERS = "getModifiers";
  private static final String ACCEPT = "accept";
  private static final String TREE_TRAVERSAL_RATIONALE = "Visitor dispatch is a traversal protocol, not a Java declaration fact";
  private static final String TREE_TRAVERSAL_REFERENCE = "https://docs.oracle.com/en/java/javase/25/docs/api/"
      + "jdk.compiler/com/sun/source/tree/Tree.html#accept(V,D)";
  private static final String CREATION_BOUNDARY = "Written creation operands and optional anonymous body";
  private static final String STATEMENT_KIND_BOUNDARY = "Canonical written statement kind";
  private static final String CONDITION = "getCondition";
  private static final String STATEMENT_BOUNDARY = "Canonical written statement value and ordered children";
  private static final String EXPRESSION = "getExpression";
  private static final String RESOURCE_BOUNDARY = "Ordered written resources, try body, catches, and optional finally body";
  private static final String CASE_BOUNDARY = "Written case labels, guard, and form-specific body or absence";
  private static final String STATEMENT = "getStatement";
  private static final String TYPE_PARAMETERS = "getTypeParameters";
  private static final String JAVA_API_REFERENCE = "https://docs.oracle.com/en/java/javase/25/docs/api/";
  private static final String ANNOTATION_PROXY_RATIONALE = "Annotation proxy lookup is not a separate declaration fact";
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
      VariableTree.class, BlockTree.class, NewArrayTree.class, NewClassTree.class, AssertTree.class, SynchronizedTree.class,
      LambdaExpressionTree.class, MemberReferenceTree.class, MemberSelectTree.class, InstanceOfTree.class, BindingPatternTree.class,
      DeconstructionPatternTree.class, AnyPatternTree.class, SwitchTree.class, SwitchExpressionTree.class, CaseTree.class, YieldTree.class,
      WhileLoopTree.class, DoWhileLoopTree.class, ForLoopTree.class, EnhancedForLoopTree.class, LabeledStatementTree.class, BreakTree.class,
      ContinueTree.class, TryTree.class, CatchTree.class, UnionTypeTree.class, TypeElement.class, ExecutableElement.class);

  private static final JavaDeclarationAccessor TREE_KIND = JavaDeclarationAccessor.of(Tree.class, "getKind", Tree::getKind);
  private static final JavaDeclarationAccessor ELEMENT_KIND = JavaDeclarationAccessor.of(Element.class, "getKind", Element::getKind);
  private static final JavaDeclarationAccessor ELEMENT_MODIFIERS = JavaDeclarationAccessor.of(Element.class, MODIFIERS,
      Element::getModifiers);
  private static final JavaDeclarationAccessor ELEMENT_ANNOTATIONS = JavaDeclarationAccessor.of(Element.class, "getAnnotationMirrors",
      Element::getAnnotationMirrors);
  private static final List<JavaDeclarationProperty> CATALOG = createDescriptors();

  private JavaDeclarationPropertyCoverage() {
  }

  static LanguageFeatureCoverage fromConfiguration(CompilerConfiguration configuration) {
    return fromDescriptors(configuration, CATALOG);
  }

  static List<JavaDeclarationProperty> descriptors() {
    return CATALOG;
  }

  static List<JavaDeclarationProperty> accessors(Class<?> surface, LanguageFeatureCoverage coverage) {
    if (!DECLARATION_SURFACES.contains(surface)) {
      throw new IllegalArgumentException("Unsupported declaration surface: " + surface.getName());
    }
    var result = new ArrayList<JavaDeclarationProperty>();
    for (var descriptor : CATALOG) {
      if (descriptor.surface() != surface) {
        continue;
      }
      var classification = coverage.classificationOf(descriptor.classification().feature());
      if (!classification.filter(descriptor.classification()::equals).isPresent()) {
        throw new IllegalArgumentException(
            "Declaration property classification mismatch: " + descriptor.classification().feature().identifier());
      }
      if (descriptor.binding().isPresent()) {
        result.add(descriptor);
      }
    }
    return List.copyOf(result);
  }

  static LanguageFeatureCoverage fromDescriptors(CompilerConfiguration configuration, List<JavaDeclarationProperty> descriptors) {
    Objects.requireNonNull(configuration, "configuration");
    var coverage = LanguageFeatureCoverageFactory.fromConfiguration(configuration,
        descriptors.stream().map(JavaDeclarationProperty::classification).toList());
    var expected = accessorIdentifiers();
    var actual = new TreeSet<String>();
    var unrecognized = new TreeSet<String>();
    for (var descriptor : descriptors) {
      var identifier = descriptor.classification().feature().identifier();
      if (!DECLARATION_SURFACES.contains(descriptor.surface()) || !expected.contains(identifier)) {
        unrecognized.add(identifier);
      }
      actual.add(identifier);
    }
    if (!unrecognized.isEmpty()) {
      var message = unrecognized.size() == 1
          ? "Unrecognized Java declaration property: " + unrecognized.getFirst()
          : "Unrecognized Java declaration properties: " + unrecognized;
      throw new IllegalArgumentException(message);
    }
    var missing = new TreeSet<>(expected);
    missing.removeAll(actual);
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Java declaration property coverage mismatch: missing=" + missing + ", extra=[]");
    }
    return coverage;
  }

  private static List<JavaDeclarationProperty> createDescriptors() {
    var descriptors = new ArrayList<JavaDeclarationProperty>();
    descriptors.addAll(creationDescriptors());
    descriptors.addAll(blockDescriptors());
    descriptors.addAll(assertionMonitorDescriptors());
    descriptors.addAll(exceptionDescriptors());
    descriptors.addAll(referenceDescriptors());
    descriptors.addAll(classLiteralDescriptors());
    descriptors.addAll(switchDescriptors());
    descriptors.addAll(patternDescriptors());
    descriptors.addAll(lambdaDescriptors());
    descriptors.addAll(iterationDescriptors());
    descriptors.addAll(arrayDescriptors());
    descriptors.addAll(compilationUnitDescriptors());
    descriptors.addAll(classDescriptors());
    descriptors.addAll(methodDescriptors());
    descriptors.addAll(variableDescriptors());
    descriptors.addAll(typeElementDescriptors());
    descriptors.addAll(executableElementDescriptors());
    descriptors.sort(Comparator.comparing(descriptor -> descriptor.classification().feature().identifier()));
    return List.copyOf(descriptors);
  }

  private static List<JavaDeclarationProperty> creationDescriptors() {
    return List.of(
        irrelevant(NewClassTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(NewClassTree.class, TREE_KIND, "Canonical object creation kind"),
        represented(NewClassTree.class, "getEnclosingExpression", NewClassTree::getEnclosingExpression, CREATION_BOUNDARY),
        represented(NewClassTree.class, "getIdentifier", NewClassTree::getIdentifier, CREATION_BOUNDARY),
        represented(NewClassTree.class, "getTypeArguments", NewClassTree::getTypeArguments, CREATION_BOUNDARY),
        represented(NewClassTree.class, "getArguments", NewClassTree::getArguments, CREATION_BOUNDARY),
        represented(NewClassTree.class, "getClassBody", NewClassTree::getClassBody, CREATION_BOUNDARY));
  }

  private static List<JavaDeclarationProperty> blockDescriptors() {
    return List.of(irrelevant(BlockTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(BlockTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
        represented(BlockTree.class, "isStatic", BlockTree::isStatic, "Written static initializer distinction"),
        represented(BlockTree.class, "getStatements", BlockTree::getStatements,
            "Complete ordered written block statements, including empty blocks"));
  }

  private static List<JavaDeclarationProperty> assertionMonitorDescriptors() {
    return List.of(
        irrelevant(AssertTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(AssertTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
        represented(AssertTree.class, CONDITION, AssertTree::getCondition, STATEMENT_BOUNDARY),
        represented(AssertTree.class, "getDetail", AssertTree::getDetail, STATEMENT_BOUNDARY),
        irrelevant(SynchronizedTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(SynchronizedTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
        represented(SynchronizedTree.class, EXPRESSION, SynchronizedTree::getExpression, STATEMENT_BOUNDARY),
        represented(SynchronizedTree.class, "getBlock", SynchronizedTree::getBlock, STATEMENT_BOUNDARY));
  }

  private static List<JavaDeclarationProperty> exceptionDescriptors() {
    return List.of(irrelevant(TryTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(TryTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
        irrelevant(CatchTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(CatchTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
        irrelevant(UnionTypeTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(UnionTypeTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
        represented(TryTree.class, "getResources", TryTree::getResources, RESOURCE_BOUNDARY),
        represented(TryTree.class, "getBlock", TryTree::getBlock, RESOURCE_BOUNDARY),
        represented(TryTree.class, "getCatches", TryTree::getCatches, RESOURCE_BOUNDARY),
        represented(TryTree.class, "getFinallyBlock", TryTree::getFinallyBlock, RESOURCE_BOUNDARY),
        represented(CatchTree.class, "getParameter", CatchTree::getParameter, "Written catch binding and body"),
        represented(CatchTree.class, "getBlock", CatchTree::getBlock, "Written catch binding and body"), represented(UnionTypeTree.class,
            "getTypeAlternatives", UnionTypeTree::getTypeAlternatives, "Every ordered written catch type alternative"));
  }

  private static List<JavaDeclarationProperty> referenceDescriptors() {
    return List.of(
        irrelevant(MemberReferenceTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(MemberReferenceTree.class, TREE_KIND, "Canonical written reference kind"),
        represented(MemberReferenceTree.class, "getMode", MemberReferenceTree::getMode, "Method or constructor reference mode"),
        represented(MemberReferenceTree.class, "getName", MemberReferenceTree::getName, "Written reference name"),
        represented(MemberReferenceTree.class, "getQualifierExpression", MemberReferenceTree::getQualifierExpression,
            "Written reference qualifier subtree"),
        represented(MemberReferenceTree.class, "getTypeArguments", MemberReferenceTree::getTypeArguments,
            "Ordered explicit reference type arguments"));
  }

  private static List<JavaDeclarationProperty> classLiteralDescriptors() {
    return List.of(
        irrelevant(MemberSelectTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(MemberSelectTree.class, TREE_KIND, "Written class literal kind"),
        represented(MemberSelectTree.class, "getIdentifier", MemberSelectTree::getIdentifier, "Written class literal selector"),
        represented(MemberSelectTree.class, EXPRESSION, MemberSelectTree::getExpression, "Written class literal type operand"));
  }

  private static List<JavaDeclarationProperty> switchDescriptors() {
    return List
        .of(irrelevant(SwitchTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
            represented(SwitchTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            irrelevant(SwitchExpressionTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class,
                Object.class),
            represented(SwitchExpressionTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            irrelevant(CaseTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
            represented(CaseTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            irrelevant(YieldTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
            represented(YieldTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            represented(SwitchTree.class, EXPRESSION, SwitchTree::getExpression, "Written switch selector"),
            represented(SwitchTree.class, "getCases", SwitchTree::getCases, "Ordered written switch cases including an empty block"),
            represented(SwitchExpressionTree.class, EXPRESSION, SwitchExpressionTree::getExpression, "Written switch selector"),
            represented(SwitchExpressionTree.class, "getCases", SwitchExpressionTree::getCases,
                "Ordered written switch cases including an empty block"),
            represented(CaseTree.class, "getLabels", CaseTree::getLabels, CASE_BOUNDARY),
            represented(CaseTree.class, "getGuard", CaseTree::getGuard, CASE_BOUNDARY),
            represented(CaseTree.class, "getBody", CaseTree::getBody, CASE_BOUNDARY),
            represented(CaseTree.class, "getStatements", CaseTree::getStatements, CASE_BOUNDARY),
            represented(CaseTree.class, "getCaseKind", CaseTree::getCaseKind, CASE_BOUNDARY),
            represented(CaseTree.class, "getExpressions", CaseTree::getExpressions, CASE_BOUNDARY),
            irrelevant(CaseTree.class, EXPRESSION, "Deprecated first-label alias; getExpressions and getLabels retain the complete list",
                "https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/CaseTree.html#getExpression()"),
            represented(YieldTree.class, "getValue", YieldTree::getValue, "Written yielded expression"));
  }

  private static List<JavaDeclarationProperty> patternDescriptors() {
    return List.of(
        irrelevant(InstanceOfTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(InstanceOfTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
        represented(InstanceOfTree.class, EXPRESSION, InstanceOfTree::getExpression, "Written tested expression"),
        represented(InstanceOfTree.class, "getType", InstanceOfTree::getType, "Traditional written type or shared binding-pattern type"),
        represented(InstanceOfTree.class, "getPattern", InstanceOfTree::getPattern, "One written pattern subtree or explicit absence"),
        irrelevant(BindingPatternTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(BindingPatternTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
        represented(BindingPatternTree.class, "getVariable", BindingPatternTree::getVariable, "Written named or unnamed pattern binding"),
        irrelevant(
            DeconstructionPatternTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(DeconstructionPatternTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
        represented(DeconstructionPatternTree.class, "getDeconstructor", DeconstructionPatternTree::getDeconstructor,
            "Written record type subtree"),
        represented(DeconstructionPatternTree.class, "getNestedPatterns", DeconstructionPatternTree::getNestedPatterns,
            "Ordered record component patterns including empty lists"),
        irrelevant(AnyPatternTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(AnyPatternTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY));
  }

  private static List<JavaDeclarationProperty> lambdaDescriptors() {
    return List.of(
        irrelevant(LambdaExpressionTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(LambdaExpressionTree.class, TREE_KIND, "Canonical written lambda kind"),
        represented(LambdaExpressionTree.class, "getParameters", LambdaExpressionTree::getParameters, "Ordered written lambda bindings"),
        represented(LambdaExpressionTree.class, "getBody", LambdaExpressionTree::getBody, "Written lambda expression or block body"),
        represented(LambdaExpressionTree.class, "getBodyKind", LambdaExpressionTree::getBodyKind, "Written lambda body distinction"));
  }

  private static List<JavaDeclarationProperty> iterationDescriptors() {
    return List
        .of(irrelevant(WhileLoopTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
            represented(WhileLoopTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            represented(WhileLoopTree.class, CONDITION, WhileLoopTree::getCondition, STATEMENT_BOUNDARY),
            represented(WhileLoopTree.class, STATEMENT, WhileLoopTree::getStatement, STATEMENT_BOUNDARY),
            irrelevant(DoWhileLoopTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
            represented(DoWhileLoopTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            represented(DoWhileLoopTree.class, CONDITION, DoWhileLoopTree::getCondition, STATEMENT_BOUNDARY),
            represented(DoWhileLoopTree.class, STATEMENT, DoWhileLoopTree::getStatement, STATEMENT_BOUNDARY),
            irrelevant(ForLoopTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
            represented(ForLoopTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            represented(ForLoopTree.class, "getInitializer", ForLoopTree::getInitializer, STATEMENT_BOUNDARY),
            represented(ForLoopTree.class, CONDITION, ForLoopTree::getCondition, STATEMENT_BOUNDARY),
            represented(ForLoopTree.class, "getUpdate", ForLoopTree::getUpdate, STATEMENT_BOUNDARY),
            represented(ForLoopTree.class, STATEMENT, ForLoopTree::getStatement, STATEMENT_BOUNDARY),
            irrelevant(EnhancedForLoopTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class,
                Object.class),
            represented(EnhancedForLoopTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            represented(EnhancedForLoopTree.class, "getVariable", EnhancedForLoopTree::getVariable, STATEMENT_BOUNDARY),
            represented(EnhancedForLoopTree.class, EXPRESSION, EnhancedForLoopTree::getExpression, STATEMENT_BOUNDARY),
            represented(EnhancedForLoopTree.class, STATEMENT, EnhancedForLoopTree::getStatement, STATEMENT_BOUNDARY),
            irrelevant(LabeledStatementTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class,
                Object.class),
            represented(LabeledStatementTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            represented(LabeledStatementTree.class, "getLabel", LabeledStatementTree::getLabel, STATEMENT_BOUNDARY),
            represented(LabeledStatementTree.class, STATEMENT, LabeledStatementTree::getStatement, STATEMENT_BOUNDARY),
            irrelevant(BreakTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
            represented(BreakTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            represented(BreakTree.class, "getLabel", BreakTree::getLabel, STATEMENT_BOUNDARY),
            irrelevant(ContinueTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
            represented(ContinueTree.class, TREE_KIND, STATEMENT_KIND_BOUNDARY),
            represented(ContinueTree.class, "getLabel", ContinueTree::getLabel, STATEMENT_BOUNDARY));
  }

  private static List<JavaDeclarationProperty> arrayDescriptors() {
    return List.of(
        irrelevant(NewArrayTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(NewArrayTree.class, TREE_KIND, "JavaCompilerKindCoverage exact tree-kind contract"),
        represented(NewArrayTree.class, "getType", NewArrayTree::getType, "JavaAstAggregate optional written array component type"),
        represented(NewArrayTree.class, "getDimensions", NewArrayTree::getDimensions,
            "JavaAstAggregate ordered sized dimension expressions"),
        represented(NewArrayTree.class, "getInitializers", NewArrayTree::getInitializers,
            "JavaAstAggregate absent, empty or ordered initializer elements"),
        represented(NewArrayTree.class, "getAnnotations", NewArrayTree::getAnnotations,
            "JavaAstAggregate initialized creation first dimension annotations"),
        represented(NewArrayTree.class, "getDimAnnotations", NewArrayTree::getDimAnnotations,
            "JavaAstAggregate ordered sized dimension annotation groups"));
  }

  private static List<JavaDeclarationProperty> compilationUnitDescriptors() {
    return List.of(
        irrelevant(CompilationUnitTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(CompilationUnitTree.class, "getImports", CompilationUnitTree::getImports,
            "JavaAstAggregate ordered import declarations"),
        represented(CompilationUnitTree.class, TREE_KIND, "JavaCompilerKindCoverage exact tree-kind contract"),
        irrelevant(CompilationUnitTree.class, "getLineMap",
            "SourceFile content and SourceSpan offsets own source evidence; line-number conversion is presentation metadata",
            "https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/CompilationUnitTree.html#getLineMap()"),
        represented(CompilationUnitTree.class, "getModule", CompilationUnitTree::getModule,
            "JavaAstAggregate source-owned module declaration and directives"),
        represented(CompilationUnitTree.class, "getPackage", CompilationUnitTree::getPackage,
            "JavaAstAggregate source-owned package declaration"),
        represented(CompilationUnitTree.class, "getPackageAnnotations", CompilationUnitTree::getPackageAnnotations,
            "JavaAstAggregate ordered package annotation attachments"),
        represented(CompilationUnitTree.class, "getPackageName", CompilationUnitTree::getPackageName,
            "JavaAstAggregate package qualified name"),
        represented(CompilationUnitTree.class, "getSourceFile", CompilationUnitTree::getSourceFile, "SourceFile input snapshot"),
        represented(CompilationUnitTree.class, "getTypeDecls", CompilationUnitTree::getTypeDecls,
            "JavaProgram top-level type declarations"));
  }

  private static List<JavaDeclarationProperty> classDescriptors() {
    return List.of(irrelevant(ClassTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(ClassTree.class, "getExtendsClause", ClassTree::getExtendsClause, "JavaType direct superclass reference"),
        represented(ClassTree.class, "getImplementsClause", ClassTree::getImplementsClause, "JavaType direct interface references"),
        represented(ClassTree.class, TREE_KIND, "JavaType declaration kind"),
        represented(ClassTree.class, "getMembers", ClassTree::getMembers, "JavaType field, method, and nested-type declarations"),
        represented(ClassTree.class, MODIFIERS, ClassTree::getModifiers, "JavaType modifier set and source annotation attachments"),
        represented(ClassTree.class, "getPermitsClause", ClassTree::getPermitsClause, "JavaType permitted-subclass references"),
        represented(ClassTree.class, "getSimpleName", ClassTree::getSimpleName, "JavaType qualified name and declaration identity"),
        represented(ClassTree.class, TYPE_PARAMETERS, ClassTree::getTypeParameters, "JavaType type-parameter aggregates"));
  }

  private static List<JavaDeclarationProperty> methodDescriptors() {
    return List.of(
        irrelevant(MethodTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(MethodTree.class, "getBody", MethodTree::getBody, "MethodSyntax body occurrence and JavaMethod body-presence flag"),
        represented(MethodTree.class, "getDefaultValue", MethodTree::getDefaultValue,
            "JavaAstAggregate declaration-owned typed annotation default"),
        represented(MethodTree.class, TREE_KIND, "JavaMethod declaration aggregate"),
        represented(MethodTree.class, MODIFIERS, MethodTree::getModifiers, "JavaMethod modifier set and source annotation attachments"),
        represented(MethodTree.class, "getName", MethodTree::getName, "JavaMethod name and declaration identity"),
        represented(MethodTree.class, "getParameters", MethodTree::getParameters, "JavaMethod ordered JavaParameter aggregates"),
        represented(MethodTree.class, "getReceiverParameter", MethodTree::getReceiverParameter,
            "JavaAstAggregate explicit receiver syntax occurrence"),
        represented(MethodTree.class, "getReturnType", MethodTree::getReturnType, "JavaAstAggregate written return-type attachment"),
        represented(MethodTree.class, "getThrows", MethodTree::getThrows, "JavaAstAggregate ordered thrown-type attachments"),
        represented(MethodTree.class, TYPE_PARAMETERS, MethodTree::getTypeParameters,
            "JavaAstAggregate ordered executable type-parameter declarations"));
  }

  private static List<JavaDeclarationProperty> variableDescriptors() {
    return List.of(
        irrelevant(VariableTree.class, ACCEPT, TREE_TRAVERSAL_RATIONALE, TREE_TRAVERSAL_REFERENCE, TreeVisitor.class, Object.class),
        represented(VariableTree.class, "getInitializer", VariableTree::getInitializer,
            "JavaAstAggregate field and enum initializer presence; expression content remains diagnosed"),
        represented(VariableTree.class, TREE_KIND, "JavaField, JavaParameter, or MethodSyntax declaration aggregate"),
        represented(VariableTree.class, MODIFIERS, VariableTree::getModifiers,
            "JavaAstAggregate written declaration modifiers and annotation attachment points"),
        represented(VariableTree.class, "getName", VariableTree::getName, "JavaField or JavaParameter name"),
        represented(VariableTree.class, "getNameExpression", VariableTree::getNameExpression,
            "JavaAstAggregate receiver qualifier source occurrence"),
        represented(VariableTree.class, "getType", VariableTree::getType, "JavaField or JavaParameter type identity"));
  }

  private static List<JavaDeclarationProperty> typeElementDescriptors() {
    return List.of(
        irrelevant(TypeElement.class, ACCEPT, "Visitor dispatch is an element traversal protocol, not a Java declaration fact",
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/element/Element.html#accept(javax.lang.model.element.ElementVisitor,P)",
            ElementVisitor.class, Object.class),
        represented(TypeElement.class, "asType", TypeElement::asType, "JavaType declaration identity"),
        irrelevant(TypeElement.class, "equals",
            "Element object equality is a compiler-model protocol; Aquila Graph uses stable declaration keys",
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/element/Element.html#equals(java.lang.Object)", Object.class),
        irrelevant(TypeElement.class, "getAnnotation", ANNOTATION_PROXY_RATIONALE,
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/AnnotatedConstruct.html#getAnnotation(java.lang.Class)", Class.class),
        represented(TypeElement.class, ELEMENT_ANNOTATIONS, "JavaAstAggregate reconciled written type annotations"),
        irrelevant(TypeElement.class, "getAnnotationsByType", ANNOTATION_PROXY_RATIONALE,
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/AnnotatedConstruct.html#getAnnotationsByType(java.lang.Class)",
            Class.class),
        represented(TypeElement.class, "getEnclosedElements", TypeElement::getEnclosedElements,
            "JavaType direct declaration inventory with typed synthesized counterparts"),
        represented(TypeElement.class, "getEnclosingElement", TypeElement::getEnclosingElement, "JavaType enclosing-type reference"),
        represented(TypeElement.class, "getInterfaces", TypeElement::getInterfaces, "JavaType direct interface references"),
        represented(TypeElement.class, ELEMENT_KIND, "JavaType declaration kind"),
        represented(TypeElement.class, ELEMENT_MODIFIERS, "JavaType modifier set"),
        represented(TypeElement.class, "getNestingKind", TypeElement::getNestingKind,
            "JavaType enclosing-type reference and qualified identity"),
        represented(TypeElement.class, "getPermittedSubclasses", TypeElement::getPermittedSubclasses,
            "JavaType permitted-subclass references"),
        represented(TypeElement.class, "getQualifiedName", TypeElement::getQualifiedName, "JavaType qualified name"),
        represented(TypeElement.class, "getRecordComponents", TypeElement::getRecordComponents,
            "JavaType ordered JavaRecordComponent declarations"),
        represented(TypeElement.class, "getSimpleName", TypeElement::getSimpleName, "JavaType qualified name and declaration identity"),
        represented(TypeElement.class, "getSuperclass", TypeElement::getSuperclass, "JavaType direct superclass reference"),
        represented(TypeElement.class, TYPE_PARAMETERS, TypeElement::getTypeParameters, "JavaType type-parameter aggregates"),
        irrelevant(TypeElement.class, "hashCode",
            "Element object hashing is a compiler-model protocol; Aquila Graph uses stable declaration keys",
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/element/Element.html#hashCode()"));
  }

  private static List<JavaDeclarationProperty> executableElementDescriptors() {
    return List.of(
        irrelevant(ExecutableElement.class, ACCEPT, "Visitor dispatch is an element traversal protocol, not a Java declaration fact",
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/element/Element.html#accept(javax.lang.model.element.ElementVisitor,P)",
            ElementVisitor.class, Object.class),
        represented(ExecutableElement.class, "asType", ExecutableElement::asType,
            "JavaMethod signature, parameter, and return-type aggregates"),
        irrelevant(ExecutableElement.class, "equals",
            "Element object equality is a compiler-model protocol; Aquila Graph uses stable declaration keys",
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/element/Element.html#equals(java.lang.Object)", Object.class),
        irrelevant(ExecutableElement.class, "getAnnotation", ANNOTATION_PROXY_RATIONALE,
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/AnnotatedConstruct.html#getAnnotation(java.lang.Class)", Class.class),
        represented(ExecutableElement.class, ELEMENT_ANNOTATIONS, "JavaAstAggregate reconciled written executable annotations"),
        irrelevant(ExecutableElement.class, "getAnnotationsByType", ANNOTATION_PROXY_RATIONALE,
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/AnnotatedConstruct.html#getAnnotationsByType(java.lang.Class)",
            Class.class),
        represented(ExecutableElement.class, "getDefaultValue", ExecutableElement::getDefaultValue,
            "JavaAstAggregate attributed annotation default value"),
        irrelevant(ExecutableElement.class, "getEnclosedElements",
            "Executable elements do not contribute separately queryable enclosed declarations to the current semantic graph",
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/element/ExecutableElement.html"),
        represented(ExecutableElement.class, "getEnclosingElement", ExecutableElement::getEnclosingElement, "JavaMethod owner type key"),
        represented(ExecutableElement.class, ELEMENT_KIND, "JavaMethod constructor flag"),
        represented(ExecutableElement.class, ELEMENT_MODIFIERS, "JavaMethod modifier set"),
        represented(ExecutableElement.class, "getParameters", ExecutableElement::getParameters,
            "JavaMethod ordered JavaParameter aggregates"),
        represented(ExecutableElement.class, "getReceiverType", ExecutableElement::getReceiverType,
            "JavaAstAggregate compiler-attributed receiver-type outcome"),
        represented(ExecutableElement.class, "getReturnType", ExecutableElement::getReturnType, "JavaMethod return type"),
        represented(ExecutableElement.class, "getSimpleName", ExecutableElement::getSimpleName, "JavaMethod name and declaration identity"),
        represented(ExecutableElement.class, "getThrownTypes", ExecutableElement::getThrownTypes,
            "JavaAstAggregate ordered written attachments and attributed thrown types"),
        represented(ExecutableElement.class, TYPE_PARAMETERS, ExecutableElement::getTypeParameters,
            "JavaAstAggregate ordered executable type parameters"),
        irrelevant(ExecutableElement.class, "hashCode",
            "Element object hashing is a compiler-model protocol; Aquila Graph uses stable declaration keys",
            JAVA_API_REFERENCE + "java.compiler/javax/lang/model/element/Element.html#hashCode()"),
        represented(ExecutableElement.class, "isDefault", ExecutableElement::isDefault, "JavaMethod modifier set"),
        represented(ExecutableElement.class, "isVarArgs", ExecutableElement::isVarArgs,
            "JavaAstAggregate executable and final formal-parameter arity facts"));
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

  private static <T> JavaDeclarationProperty represented(Class<T> surface, String name, Function<T, @Nullable Object> invocation,
      String boundary) {
    return represented(surface, JavaDeclarationAccessor.of(surface, name, invocation), boundary);
  }

  private static JavaDeclarationProperty represented(Class<?> surface, JavaDeclarationAccessor binding, String boundary) {
    var feature = new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY, accessorIdentifier(surface, binding.method()));
    return new JavaDeclarationProperty(surface, binding.method(), LanguageFeatureClassification.represented(feature, boundary),
        Optional.of(binding));
  }

  private static JavaDeclarationProperty irrelevant(Class<?> surface, String name, String rationale, String sourceLocator,
      Class<?>... parameters) {
    var method = JavaDeclarationAccessor.method(surface, name, parameters);
    var feature = new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY, accessorIdentifier(surface, method));
    return new JavaDeclarationProperty(surface, method, LanguageFeatureClassification.irrelevant(feature, rationale, sourceLocator),
        Optional.empty());
  }
}
