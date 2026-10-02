package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.LanguageFeatureClassification;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.junit.jupiter.api.Test;

class DeclarationAccessorDispatchTest {
  private static final List<Class<?>> SURFACES = List.of(CompilationUnitTree.class, ClassTree.class, MethodTree.class,
      VariableTree.class, TypeElement.class, ExecutableElement.class);

  @Test
  void givenEveryRepresentedAccessor_whenItsTargetThrowsRuntimeFailure_thenTheExactMethodAndFailureArePreserved() {
    // Arrange
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var visited = 0;
    for (var surface : SURFACES) {
      for (var accessor : JavaCoveragePropertyProjection.accessors(surface, coverage)) {
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
    assertEquals(60, visited);
  }

  @Test
  void givenEveryRepresentedAccessor_whenItsTargetThrowsAnError_thenTheExactMethodAndErrorArePreserved() {
    // Arrange
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var visited = 0;
    for (var surface : SURFACES) {
      for (var accessor : JavaCoveragePropertyProjection.accessors(surface, coverage)) {
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
    assertEquals(60, visited);
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
    var actual = assertThrows(IllegalArgumentException.class, () -> JavaCoveragePropertyProjection.accessors(String.class, coverage));

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
  void givenANewRepresentedPropertyWithoutABinding_whenTheCatalogIsValidated_thenTheMissingBindingIsReported() throws Exception {
    // Arrange
    var coverage = JavaDeclarationPropertyCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var method = CompilationUnitTree.class.getMethod("getLineMap");
    var identifier = JavaDeclarationPropertyCoverage.accessorIdentifier(CompilationUnitTree.class, method);
    var classifications = coverage.classifications().stream()
        .map(value -> value.feature().identifier().equals(identifier)
            ? LanguageFeatureClassification.represented(value.feature(), "Line map representation") : value).toList();
    var changed = LanguageFeatureCoverage.forRelease(JavaRelease.JAVA_25, classifications);

    // Act
    var actual = assertThrows(IllegalStateException.class, () -> JavaDeclarationAccessor.validateCatalog(SURFACES, changed));

    // Assert
    assertEquals("Declaration accessor bindings mismatch: missing=[" + method.toGenericString() + "], extra=[]", actual.getMessage());
  }

  @Test
  void givenABindingWhosePropertyIsNoLongerRepresented_whenTheCatalogIsValidated_thenTheExtraBindingIsReported() throws Exception {
    // Arrange
    var coverage = JavaDeclarationPropertyCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var method = ClassTree.class.getMethod("getExtendsClause");
    var identifier = JavaDeclarationPropertyCoverage.accessorIdentifier(ClassTree.class, method);
    var classifications = coverage.classifications().stream()
        .map(value -> value.feature().identifier().equals(identifier)
            ? LanguageFeatureClassification.diagnosed(value.feature(), "frontend.changed-superclass-policy") : value).toList();
    var changed = LanguageFeatureCoverage.forRelease(JavaRelease.JAVA_25, classifications);

    // Act
    var actual = assertThrows(IllegalStateException.class, () -> JavaDeclarationAccessor.validateCatalog(SURFACES, changed));

    // Assert
    assertEquals("Declaration accessor bindings mismatch: missing=[], extra=[" + method.toGenericString() + "]", actual.getMessage());
  }

  private static JavaCoveragePropertyProjection.Accessor accessor(Class<?> surface, String getter) {
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    return JavaCoveragePropertyProjection.accessors(surface, coverage).stream()
        .filter(value -> value.method().getName().equals(getter)).findFirst().orElseThrow();
  }
}
