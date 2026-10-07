package org.aquilagraph.frontend;

import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.SynchronizedTree;
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
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.jspecify.annotations.Nullable;

/** Dispatches the closed declaration-property API through statically typed calls. */
final class JavaDeclarationAccessor {
  private static final String STATEMENT = "getStatement";
  private static final String CONDITION = "getCondition";
  private static final String MODIFIERS = "getModifiers";
  private static final String TYPE_PARAMETERS = "getTypeParameters";
  private static final Map<Method, Function<Object, @Nullable Object>> READERS = Map.ofEntries(
      reader(ClassTree.class, "getExtendsClause", ClassTree::getExtendsClause),
      reader(ClassTree.class, "getImplementsClause", ClassTree::getImplementsClause),
      reader(ClassTree.class, "getMembers", ClassTree::getMembers), reader(ClassTree.class, MODIFIERS, ClassTree::getModifiers),
      reader(ClassTree.class, "getPermitsClause", ClassTree::getPermitsClause),
      reader(ClassTree.class, "getSimpleName", ClassTree::getSimpleName),
      reader(ClassTree.class, TYPE_PARAMETERS, ClassTree::getTypeParameters),
      reader(CompilationUnitTree.class, "getImports", CompilationUnitTree::getImports),
      reader(CompilationUnitTree.class, "getModule", CompilationUnitTree::getModule),
      reader(CompilationUnitTree.class, "getPackage", CompilationUnitTree::getPackage),
      reader(CompilationUnitTree.class, "getPackageAnnotations", CompilationUnitTree::getPackageAnnotations),
      reader(CompilationUnitTree.class, "getPackageName", CompilationUnitTree::getPackageName),
      reader(CompilationUnitTree.class, "getSourceFile", CompilationUnitTree::getSourceFile),
      reader(CompilationUnitTree.class, "getTypeDecls", CompilationUnitTree::getTypeDecls),
      reader(MethodTree.class, "getBody", MethodTree::getBody), reader(MethodTree.class, "getDefaultValue", MethodTree::getDefaultValue),
      reader(MethodTree.class, MODIFIERS, MethodTree::getModifiers), reader(MethodTree.class, "getName", MethodTree::getName),
      reader(MethodTree.class, "getParameters", MethodTree::getParameters),
      reader(MethodTree.class, "getReceiverParameter", MethodTree::getReceiverParameter),
      reader(MethodTree.class, "getReturnType", MethodTree::getReturnType), reader(MethodTree.class, "getThrows", MethodTree::getThrows),
      reader(MethodTree.class, TYPE_PARAMETERS, MethodTree::getTypeParameters), reader(Tree.class, "getKind", Tree::getKind),
      reader(AssertTree.class, CONDITION, AssertTree::getCondition), reader(AssertTree.class, "getDetail", AssertTree::getDetail),
      reader(SynchronizedTree.class, "getExpression", SynchronizedTree::getExpression),
      reader(SynchronizedTree.class, "getBlock", SynchronizedTree::getBlock),
      reader(WhileLoopTree.class, CONDITION, WhileLoopTree::getCondition),
      reader(WhileLoopTree.class, STATEMENT, WhileLoopTree::getStatement),
      reader(DoWhileLoopTree.class, CONDITION, DoWhileLoopTree::getCondition),
      reader(DoWhileLoopTree.class, STATEMENT, DoWhileLoopTree::getStatement),
      reader(ForLoopTree.class, "getInitializer", ForLoopTree::getInitializer),
      reader(ForLoopTree.class, CONDITION, ForLoopTree::getCondition), reader(ForLoopTree.class, "getUpdate", ForLoopTree::getUpdate),
      reader(ForLoopTree.class, STATEMENT, ForLoopTree::getStatement),
      reader(EnhancedForLoopTree.class, "getVariable", EnhancedForLoopTree::getVariable),
      reader(EnhancedForLoopTree.class, "getExpression", EnhancedForLoopTree::getExpression),
      reader(EnhancedForLoopTree.class, STATEMENT, EnhancedForLoopTree::getStatement),
      reader(LabeledStatementTree.class, "getLabel", LabeledStatementTree::getLabel),
      reader(LabeledStatementTree.class, STATEMENT, LabeledStatementTree::getStatement),
      reader(BreakTree.class, "getLabel", BreakTree::getLabel), reader(ContinueTree.class, "getLabel", ContinueTree::getLabel),
      reader(NewArrayTree.class, "getType", NewArrayTree::getType),
      reader(NewArrayTree.class, "getDimensions", NewArrayTree::getDimensions),
      reader(NewArrayTree.class, "getInitializers", NewArrayTree::getInitializers),
      reader(NewArrayTree.class, "getAnnotations", NewArrayTree::getAnnotations),
      reader(NewArrayTree.class, "getDimAnnotations", NewArrayTree::getDimAnnotations),
      reader(VariableTree.class, "getInitializer", VariableTree::getInitializer),
      reader(VariableTree.class, MODIFIERS, VariableTree::getModifiers), reader(VariableTree.class, "getName", VariableTree::getName),
      reader(VariableTree.class, "getNameExpression", VariableTree::getNameExpression),
      reader(VariableTree.class, "getType", VariableTree::getType),
      reader(Element.class, "getAnnotationMirrors", Element::getAnnotationMirrors), reader(Element.class, "getKind", Element::getKind),
      reader(Element.class, MODIFIERS, Element::getModifiers), reader(ExecutableElement.class, "asType", ExecutableElement::asType),
      reader(ExecutableElement.class, "getDefaultValue", ExecutableElement::getDefaultValue),
      reader(ExecutableElement.class, "getEnclosingElement", ExecutableElement::getEnclosingElement),
      reader(ExecutableElement.class, "getParameters", ExecutableElement::getParameters),
      reader(ExecutableElement.class, "getReceiverType", ExecutableElement::getReceiverType),
      reader(ExecutableElement.class, "getReturnType", ExecutableElement::getReturnType),
      reader(ExecutableElement.class, "getSimpleName", ExecutableElement::getSimpleName),
      reader(ExecutableElement.class, "getThrownTypes", ExecutableElement::getThrownTypes),
      reader(ExecutableElement.class, TYPE_PARAMETERS, ExecutableElement::getTypeParameters),
      reader(ExecutableElement.class, "isDefault", ExecutableElement::isDefault),
      reader(ExecutableElement.class, "isVarArgs", ExecutableElement::isVarArgs), reader(TypeElement.class, "asType", TypeElement::asType),
      reader(TypeElement.class, "getEnclosedElements", TypeElement::getEnclosedElements),
      reader(TypeElement.class, "getEnclosingElement", TypeElement::getEnclosingElement),
      reader(TypeElement.class, "getInterfaces", TypeElement::getInterfaces),
      reader(TypeElement.class, "getNestingKind", TypeElement::getNestingKind),
      reader(TypeElement.class, "getPermittedSubclasses", TypeElement::getPermittedSubclasses),
      reader(TypeElement.class, "getQualifiedName", TypeElement::getQualifiedName),
      reader(TypeElement.class, "getRecordComponents", TypeElement::getRecordComponents),
      reader(TypeElement.class, "getSimpleName", TypeElement::getSimpleName),
      reader(TypeElement.class, "getSuperclass", TypeElement::getSuperclass),
      reader(TypeElement.class, TYPE_PARAMETERS, TypeElement::getTypeParameters));

  private JavaDeclarationAccessor() {
  }

  static Function<Object, @Nullable Object> bind(Method method) {
    var reader = READERS.get(method);
    if (reader == null) {
      throw new IllegalArgumentException("Unsupported declaration accessor: " + method);
    }
    return reader;
  }

  static void validateCatalog(List<Class<?>> surfaces, LanguageFeatureCoverage coverage) {
    var represented = new HashSet<Method>();
    for (var surface : surfaces) {
      for (var method : surface.getMethods()) {
        var identifier = JavaDeclarationPropertyCoverage.accessorIdentifier(surface, method);
        if (coverage.classifications().stream()
            .anyMatch(classification -> classification.feature().category() == LanguageFeatureCategory.DECLARATION_PROPERTY
                && classification.feature().identifier().equals(identifier)
                && classification.policy() == LanguageFeaturePolicy.REPRESENTED)) {
          represented.add(method);
        }
      }
    }
    if (!represented.equals(READERS.keySet())) {
      var missing = new HashSet<>(represented);
      missing.removeAll(READERS.keySet());
      var extra = new HashSet<>(READERS.keySet());
      extra.removeAll(represented);
      throw new IllegalStateException(
          "Declaration accessor bindings mismatch: missing=" + descriptions(missing) + ", extra=" + descriptions(extra));
    }
  }

  private static Set<String> descriptions(Set<Method> methods) {
    var descriptions = new TreeSet<String>();
    methods.stream().map(Method::toGenericString).forEach(descriptions::add);
    return descriptions;
  }

  private static <T> Entry<Method, Function<Object, @Nullable Object>> reader(Class<T> type, String name,
      Function<T, @Nullable Object> invocation) {
    try {
      return Map.entry(type.getMethod(name), owner -> invocation.apply(type.cast(owner)));
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException("Declaration accessor is unavailable: " + type.getName() + '#' + name, exception);
    }
  }
}
