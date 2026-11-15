package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.source.tree.ClassTree;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.junit.jupiter.api.Test;

/** Exercises invocation failures independently of contextual coverage admission. */
public class DeclarationAccessorInvocationTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @Test
  void givenAPublicTreeGetterThrowingARuntimeFailure_whenInvoked_thenTheSameTargetPropagates() throws Exception {
    // Arrange
    var expected = new IllegalArgumentException("runtime target");
    var owner = failingTree(expected);

    // Act
    var actual = assertThrows(IllegalArgumentException.class, () -> query(ClassTree.class, "getKind", owner));

    // Assert
    assertSame(expected, actual);
  }

  @Test
  void givenAPublicTreeGetterThrowingAnError_whenInvoked_thenTheSameTargetPropagates() throws Exception {
    // Arrange
    var expected = new AssertionError("error target");
    var owner = failingTree(expected);

    // Act
    var actual = assertThrows(AssertionError.class, () -> query(ClassTree.class, "getKind", owner));

    // Assert
    assertSame(expected, actual);
  }

  @Test
  void givenAPublicTreeGetterReturningNull_whenInvoked_thenItsScalarIsAbsent() throws Exception {
    // Arrange
    var owner = Proxy.newProxyInstance(ClassTree.class.getClassLoader(), new Class<?>[] { ClassTree.class }, (_, _, _) -> null);

    // Act
    var actual = query(ClassTree.class, "getKind", owner);

    // Assert
    assertEquals(new CoveragePropertyValue(CoveragePropertyValue.State.ABSENT, List.of()), actual);
  }

  @Test
  void givenAPublicDefaultGetter_whenInvoked_thenItsDefaultImplementationReturnsTheValue() throws Exception {
    // Arrange
    var owner = Proxy.newProxyInstance(ClassTree.class.getClassLoader(), new Class<?>[] { ClassTree.class },
        (proxy, method, arguments) -> InvocationHandler.invokeDefault(proxy, method, arguments));

    // Act
    var actual = query(ClassTree.class, "getPermitsClause", owner);

    // Assert
    assertEquals(new CoveragePropertyValue(CoveragePropertyValue.State.EMPTY, List.of()), actual);
  }

  @Test
  void givenAPublicGetterWithAVirtualOverride_whenInvoked_thenTheOverrideReturnsTheValue() throws Exception {
    // Arrange
    var owner = Proxy.newProxyInstance(ClassTree.class.getClassLoader(), new Class<?>[] { ClassTree.class },
        (_, _, _) -> new TextName("overridden value"));

    // Act
    var actual = query(ClassTree.class, "getSimpleName", owner);

    // Assert
    assertEquals(text("overridden value"), actual);
  }

  @Test
  void givenAPublicPrimitiveBooleanGetter_whenInvoked_thenItsBoxedValueIsCopiedExactly() throws Exception {
    // Arrange
    var owner = Proxy.newProxyInstance(ExecutableElement.class.getClassLoader(), new Class<?>[] { ExecutableElement.class },
        (_, _, _) -> true);

    // Act
    var actual = query(ExecutableElement.class, "isVarArgs", owner);

    // Assert
    assertEquals(text("true"), actual);
  }

  @Test
  void givenAForeignGetterSurface_whenDescriptorsAreCreated_thenTheSurfaceIsRejected() {
    // Arrange
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION);

    // Act
    var actual = assertThrows(IllegalArgumentException.class,
        () -> JavaDeclarationPropertyCoverage.accessors(BooleanSupplier.class, coverage));

    // Assert
    assertEquals("Unsupported declaration surface: java.util.function.BooleanSupplier", actual.getMessage());
  }

  @Test
  void givenTheClassDeclarationSurface_whenDescriptorsAreCreated_thenOnlyRepresentedGettersAreExposed() {
    // Arrange
    var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION);
    var expected = Set.of("getExtendsClause", "getImplementsClause", "getKind", "getMembers", "getModifiers",
        "getPermitsClause", "getSimpleName", "getTypeParameters");

    // Act
    var actual = JavaDeclarationPropertyCoverage.accessors(ClassTree.class, coverage).stream()
        .map(accessor -> accessor.method().getName()).collect(Collectors.toUnmodifiableSet());

    // Assert
    assertEquals(expected, actual);
  }

  @Test
  void givenAClassGetterAndAnExecutableOwner_whenQueried_thenTheReceiverIsRejectedBeforeItsGetterRuns() throws Exception {
    // Arrange
    var owner = Proxy.newProxyInstance(ExecutableElement.class.getClassLoader(), new Class<?>[] { ExecutableElement.class },
        (_, _, _) -> { throw new AssertionError("Incompatible owner must not be invoked"); });

    // Act
    var actual = assertThrows(ClassCastException.class, () -> query(ClassTree.class, "getSimpleName", owner));

    // Assert
    assertEquals(ClassCastException.class, actual.getClass());
  }

  private static Object failingTree(Throwable failure) {
    return Proxy.newProxyInstance(ClassTree.class.getClassLoader(), new Class<?>[] { ClassTree.class }, (_, _, _) -> { throw failure; });
  }

  private static CoveragePropertyValue text(String value) {
    return new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR,
        List.of(new CoveragePropertyValue.Item("item:0", value, CoveragePropertyValue.Provenance.WRITTEN)));
  }

  private static CoveragePropertyValue query(Class<?> surface, String getter, Object owner) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(Path.of("D:/Projects/aquila-graph/aquila-graph/target/coverage-check/sources")),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), "class Example {}")));
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(CONFIGURATION), sources)) {
      var parsed = session.parse();
      var sourceOwner = JavaCoverageTopology.owners(parsed.units(), sources, parsed.sourceEvidence()).stream()
          .filter(value -> value.tree() instanceof ClassTree).findFirst().orElseThrow();
      var accessor = JavaDeclarationPropertyCoverage.accessors(surface, JavaLanguageFeatureCoverage.fromConfiguration(CONFIGURATION)).stream()
          .filter(value -> value.method().getName().equals(getter)).findFirst().orElseThrow();
      return JavaCoveragePropertyProjection.query(accessor, owner, sourceOwner,
          parsed.sourceEvidence(), Optional.empty(), JavaTranslationPolicy.legacyAnalysis());
    }
  }

  private record TextName(String value) implements Name {
    @Override
    public boolean contentEquals(CharSequence other) {
      return value.contentEquals(other);
    }

    @Override
    public int length() {
      return value.length();
    }

    @Override
    public char charAt(int index) {
      return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return value.subSequence(start, end);
    }

    @Override
    public String toString() {
      return value;
    }
  }
}
