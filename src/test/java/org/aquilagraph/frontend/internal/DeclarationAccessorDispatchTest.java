package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.AnyPatternTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.junit.jupiter.api.Test;

class DeclarationAccessorDispatchTest {
  private static final List<Class<?>> SURFACES = List.of(CompilationUnitTree.class, ClassTree.class, MethodTree.class, BlockTree.class,
      VariableTree.class, NewArrayTree.class, NewClassTree.class, AssertTree.class, SynchronizedTree.class, LambdaExpressionTree.class, MemberReferenceTree.class, MemberSelectTree.class, InstanceOfTree.class, BindingPatternTree.class, DeconstructionPatternTree.class, AnyPatternTree.class, WhileLoopTree.class, DoWhileLoopTree.class, ForLoopTree.class, EnhancedForLoopTree.class, LabeledStatementTree.class, BreakTree.class, ContinueTree.class, SwitchTree.class, SwitchExpressionTree.class, CaseTree.class, YieldTree.class, TryTree.class, CatchTree.class, UnionTypeTree.class, TypeElement.class, ExecutableElement.class);

  @Test
  void givenEveryRepresentedAccessor_whenItsTargetThrowsRuntimeFailure_thenTheExactMethodAndFailureArePreserved() {
    // Arrange
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var visited = 0;
    for (var surface : SURFACES) {
      for (var accessor : JavaDeclarationPropertyCoverage.accessors(surface, coverage)) {
        var expected = new IllegalArgumentException(accessor.classification().feature().identifier());
        var invoked = new AtomicReference<Method>();
        var owner = Proxy.newProxyInstance(surface.getClassLoader(), new Class<?>[] {surface}, (_, method, _) -> {
          invoked.set(method);
          throw expected;
        });

        // Act
        var actual = assertThrows(IllegalArgumentException.class, () -> accessor.read(owner));

        // Assert
        assertSame(expected, actual);
        assertEquals(accessor.method(), invoked.get());
        assertEquals(0, accessor.method().getExceptionTypes().length);
        visited++;
      }
    }
    assertEquals(150, visited);
  }

  @Test
  void givenEveryRepresentedAccessor_whenItsTargetThrowsAnError_thenTheExactMethodAndErrorArePreserved() {
    // Arrange
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var visited = 0;
    for (var surface : SURFACES) {
      for (var accessor : JavaDeclarationPropertyCoverage.accessors(surface, coverage)) {
        var expected = new AssertionError(accessor.classification().feature().identifier());
        var invoked = new AtomicReference<Method>();
        var owner = Proxy.newProxyInstance(surface.getClassLoader(), new Class<?>[] {surface}, (_, method, _) -> {
          invoked.set(method);
          throw expected;
        });

        // Act
        var actual = assertThrows(AssertionError.class, () -> accessor.read(owner));

        // Assert
        assertSame(expected, actual);
        assertEquals(accessor.method(), invoked.get());
        visited++;
      }
    }
    assertEquals(150, visited);
  }

  @Test
  void givenAnAbsentSuperclass_whenItsAccessorRuns_thenAbsenceIsPreserved() {
    // Arrange
    var owner = Proxy.newProxyInstance(ClassTree.class.getClassLoader(), new Class<?>[] {ClassTree.class}, (_, _, _) -> null);

    // Act
    var actual = accessor(ClassTree.class, "getExtendsClause").read(owner);

    // Assert
    assertEquals(Optional.empty(), actual);
  }

  @Test
  void givenBothArityStates_whenThePrimitiveAccessorRuns_thenTheBooleanIsBoxedExactly() {
    // Arrange
    for (var expected : List.of(false, true)) {
      var owner = Proxy.newProxyInstance(ExecutableElement.class.getClassLoader(), new Class<?>[] {ExecutableElement.class},
          (_, _, _) -> expected);

      // Act
      var actual = accessor(ExecutableElement.class, "isVarArgs").read(owner);

      // Assert
      assertEquals(Optional.of(expected), actual);
    }
  }

  @Test
  void givenTheCompilerApiDefaultAccessor_whenInvoked_thenItsDefaultImplementationReturnsTheEmptyList() {
    // Arrange
    var owner = Proxy.newProxyInstance(ClassTree.class.getClassLoader(), new Class<?>[] {ClassTree.class},
        (proxy, method, arguments) -> InvocationHandler.invokeDefault(proxy, method, arguments));

    // Act
    var actual = accessor(ClassTree.class, "getPermitsClause").read(owner);

    // Assert
    assertEquals(Optional.of(List.of()), actual);
  }

  @Test
  void givenAnOverriddenCompilerApiAccessor_whenInvoked_thenItsActualOrderedListIsRetained() {
    // Arrange
    var first = Proxy.newProxyInstance(Tree.class.getClassLoader(), new Class<?>[] {Tree.class}, (_, _, _) -> Tree.Kind.IDENTIFIER);
    var second = Proxy.newProxyInstance(Tree.class.getClassLoader(), new Class<?>[] {Tree.class}, (_, _, _) -> Tree.Kind.MEMBER_SELECT);
    var expected = List.of(first, second);
    var owner = Proxy.newProxyInstance(ClassTree.class.getClassLoader(), new Class<?>[] {ClassTree.class}, (_, _, _) -> expected);

    // Act
    var actual = accessor(ClassTree.class, "getPermitsClause").read(owner);

    // Assert
    assertSame(expected, actual.orElseThrow());
  }

  @Test
  void givenAForeignSurface_whenQueried_thenTheAccessorBoundaryRejectsIt() {
    // Arrange
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Act
    var actual = assertThrows(IllegalArgumentException.class, () -> JavaDeclarationPropertyCoverage.accessors(String.class, coverage));

    // Assert
    assertEquals("Unsupported declaration surface: java.lang.String", actual.getMessage());
  }

  @Test
  void givenARegisteredAccessorAndIncompatibleOwner_whenQueried_thenTheOwnerTypeIsRejected() {
    // Arrange
    var accessor = accessor(ClassTree.class, "getPermitsClause");

    // Act
    var actual = assertThrows(ClassCastException.class, () -> accessor.read("example"));

    // Assert
    assertEquals(ClassCastException.class, actual.getClass());
  }

  @Test
  void givenTwoSurfacesSharingATreeCallable_whenAccessorsAreSelected_thenSurfaceIdentitiesAndMeaningRemainDistinct() throws Exception {
    // Arrange
    var classOwner = Proxy.newProxyInstance(ClassTree.class.getClassLoader(), new Class<?>[] {ClassTree.class},
        (_, _, _) -> Tree.Kind.CLASS);
    var blockOwner = Proxy.newProxyInstance(BlockTree.class.getClassLoader(), new Class<?>[] {BlockTree.class},
        (_, _, _) -> Tree.Kind.BLOCK);

    // Act
    var classKind = accessor(ClassTree.class, "getKind");
    var blockKind = accessor(BlockTree.class, "getKind");
    var classValue = classKind.read(classOwner);
    var blockValue = blockKind.read(blockOwner);

    // Assert
    assertEquals(Tree.class.getMethod("getKind"), classKind.method());
    assertEquals(classKind.method(), blockKind.method());
    assertSame(classKind.binding().orElseThrow(), blockKind.binding().orElseThrow());
    assertEquals("ClassTree::Tree#getKind()", classKind.classification().feature().identifier());
    assertEquals("BlockTree::Tree#getKind()", blockKind.classification().feature().identifier());
    assertEquals(Optional.of("JavaType declaration kind"), classKind.classification().representationBoundary());
    assertEquals(Optional.of("Canonical written statement kind"), blockKind.classification().representationBoundary());
    assertEquals(Optional.of(Tree.Kind.CLASS), classValue);
    assertEquals(Optional.of(Tree.Kind.BLOCK), blockValue);
  }

  @Test
  void givenTheClassSurface_whenAccessorsAreSelected_thenCanonicalIdentifiersAreInExactOrder() {
    // Arrange
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Act
    var identifiers = JavaDeclarationPropertyCoverage.accessors(ClassTree.class, coverage).stream()
        .map(value -> value.classification().feature().identifier()).toList();

    // Assert
    assertEquals(List.of("ClassTree::ClassTree#getExtendsClause()", "ClassTree::ClassTree#getImplementsClause()",
        "ClassTree::ClassTree#getMembers()", "ClassTree::ClassTree#getModifiers()", "ClassTree::ClassTree#getPermitsClause()",
        "ClassTree::ClassTree#getSimpleName()", "ClassTree::ClassTree#getTypeParameters()", "ClassTree::Tree#getKind()"), identifiers);
  }

  @Test
  void givenASurfaceSpecificInheritedGetterAndAnotherTreeSurface_whenRead_thenTheForeignSurfaceIsRejectedBeforeInvocation() {
    // Arrange
    var classKind = accessor(ClassTree.class, "getKind");
    var foreignOwner = Proxy.newProxyInstance(BlockTree.class.getClassLoader(), new Class<?>[] {BlockTree.class},
        (_, _, _) -> { throw new AssertionError("Foreign surface must not be invoked"); });

    // Act
    var actual = assertThrows(ClassCastException.class, () -> classKind.read(foreignOwner));

    // Assert
    assertEquals(ClassCastException.class, actual.getClass());
  }

  @Test
  void givenANewRepresentedPropertyWithoutABinding_whenTheCatalogIsValidated_thenTheMissingBindingIsReported() throws Exception {
    // Arrange
    var method = CompilationUnitTree.class.getMethod("getLineMap");
    var identifier = JavaDeclarationPropertyCoverage.accessorIdentifier(CompilationUnitTree.class, method);
    var feature = new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY, identifier);
    var classification = LanguageFeatureClassification.represented(feature, "Line map representation");

    // Act
    var actual = assertThrows(IllegalArgumentException.class,
        () -> new JavaDeclarationProperty(CompilationUnitTree.class, method, classification, Optional.empty()));

    // Assert
    assertEquals("Declaration property reader policy mismatch: " + identifier, actual.getMessage());
  }

  @Test
  void givenABindingWhosePropertyIsNoLongerRepresented_whenTheCatalogIsValidated_thenTheExtraBindingIsReported() throws Exception {
    // Arrange
    var method = ClassTree.class.getMethod("getExtendsClause");
    var identifier = JavaDeclarationPropertyCoverage.accessorIdentifier(ClassTree.class, method);
    var original = accessor(ClassTree.class, "getExtendsClause");
    var classification = LanguageFeatureClassification.diagnosed(original.classification().feature(), "frontend.changed-superclass-policy");

    // Act
    var actual = assertThrows(IllegalArgumentException.class,
        () -> new JavaDeclarationProperty(ClassTree.class, method, classification, original.binding()));

    // Assert
    assertEquals("Declaration property reader policy mismatch: " + identifier, actual.getMessage());
  }

  @Test
  void givenAReaderForAnotherMethod_whenThePropertyDescriptorIsCreated_thenTheCallableMismatchIsRejected() {
    // Arrange
    var original = accessor(ClassTree.class, "getExtendsClause");
    var differentBinding = JavaDeclarationAccessor.of(ClassTree.class, "getMembers", ClassTree::getMembers);

    // Act
    var actual = assertThrows(IllegalArgumentException.class, () -> new JavaDeclarationProperty(ClassTree.class, original.method(),
        original.classification(), Optional.of(differentBinding)));

    // Assert
    assertEquals("Declaration property reader does not match its compiler callable: ClassTree::ClassTree#getExtendsClause()",
        actual.getMessage());
  }

  @Test
  void givenAnInheritedReaderForAnIncompatibleSurface_whenThePropertyDescriptorIsCreated_thenTheReceiverMismatchIsRejected() {
    // Arrange
    var original = accessor(BlockTree.class, "getKind");
    var classBinding = JavaDeclarationAccessor.of(ClassTree.class, "getKind", ClassTree::getKind);
    assertEquals(original.method(), classBinding.method());

    // Act
    var actual = assertThrows(IllegalArgumentException.class, () -> new JavaDeclarationProperty(BlockTree.class, original.method(),
        original.classification(), Optional.of(classBinding)));

    // Assert
    assertEquals("Declaration property reader does not match its compiler callable: BlockTree::Tree#getKind()", actual.getMessage());
  }

  private static JavaDeclarationProperty accessor(Class<?> surface, String getter) {
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    return JavaDeclarationPropertyCoverage.accessors(surface, coverage).stream()
        .filter(value -> value.method().getName().equals(getter)).findFirst().orElseThrow();
  }
}
