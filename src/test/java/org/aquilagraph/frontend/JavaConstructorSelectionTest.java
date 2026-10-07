package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.MethodSyntax;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.ConversionEvidence;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaConstructorSelectionTest {
  @Test
  void givenExplicitAndInferredConstructorArguments_whenAnalyzed_thenExactTargetsAndBoxingAreRetained() throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T> Selection(T value) {}
          static Selection inferred(int value) { return new Selection(value); }
          static Selection explicitBox(int value) { return new <Object> Selection(value); }
          static Selection explicitReference(String value) { return new <CharSequence> Selection(value); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    var inferred = argument(input, "inferred", 0);
    assertEquals("int", inferred.sourceType().displayName());
    assertEquals("java.lang.Integer", inferred.selectedTarget().displayName());
    assertEquals("java.lang.Integer", inferred.boxedType().orElseThrow().displayName());
    var boxed = argument(input, "explicitBox", 0);
    assertEquals("java.lang.Object", boxed.selectedTarget().displayName());
    assertEquals("int", boxed.preBoxPrimitiveType().orElseThrow().displayName());
    assertEquals("java.lang.Integer", boxed.boxedType().orElseThrow().displayName());
    var reference = argument(input, "explicitReference", 0);
    assertEquals("java.lang.String", reference.sourceType().displayName());
    assertEquals("java.lang.CharSequence", reference.selectedTarget().displayName());
    assertTrue(reference.boxedType().isEmpty());
    assertFalse(input.program().types().stream().flatMap(type -> type.methods().stream())
        .anyMatch(method -> method.name().startsWith("constructorSelection")));
  }

  @Test
  void givenFixedClassArgumentsAndGenericVarargs_whenAnalyzed_thenFixedAndVariableApplicabilityRemainDistinct() throws IOException {
    // Arrange
    var source = """
        class Selection<A> {
          <B extends A> Selection(B... values) {}
          static Selection<Object[]> arrayElement(Object[] values) { return new Selection<Object[]>(values); }
          static Selection<Number> arrayFixed(Integer[] values) { return new Selection<Number>(values); }
          static Selection<Number> zero() { return new Selection<Number>(); }
          static Selection<Number> tail(Integer first, Integer second) { return new Selection<Number>(first, second); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    var variable = body(input, "arrayElement");
    var variableArity = variable.invocationArity(creation(variable).nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, variableArity.mode());
    assertEquals("java.lang.Object[][]", variableArity.varargArrayType().orElseThrow().displayName());
    assertEquals("java.lang.Object[]", argument(input, "arrayElement", 0).selectedTarget().displayName());
    var fixed = body(input, "arrayFixed");
    assertEquals(InvocationApplicabilityMode.FIXED, fixed.invocationArity(creation(fixed).nodeId()).orElseThrow().mode());
    assertEquals("java.lang.Integer[]", argument(input, "arrayFixed", 0).selectedTarget().displayName());
    var zero = body(input, "zero");
    var zeroArity = zero.invocationArity(creation(zero).nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, zeroArity.mode());
    assertEquals(1, zeroArity.formalCount());
    assertEquals("java.lang.Number[]", zeroArity.varargArrayType().orElseThrow().displayName());
    assertEquals("java.lang.Integer", argument(input, "tail", 0).selectedTarget().displayName());
    assertEquals("java.lang.Integer", argument(input, "tail", 1).selectedTarget().displayName());
  }

  @Test
  void givenDiamondAndNestedPolyArguments_whenAnalyzed_thenTargetContextAndSelectedConstructorBoundsArePreserved() throws IOException {
    // Arrange
    var source = """
        class Selection<A> {
          <B extends A> Selection(B value) {}
          static Selection<CharSequence> text() { return new Selection<>("text"); }
          static Selection<java.util.List<String>> nested() { return new Selection<>(java.util.Collections.emptyList()); }
          static Selection<Object> conditional(boolean flag) { return new Selection<>(flag ? "text" : "other"); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals("java.lang.String", argument(input, "text", 0).selectedTarget().displayName());
    assertEquals("java.util.List<java.lang.String>", argument(input, "nested", 0).selectedTarget().displayName());
    assertEquals("Selection<java.lang.CharSequence>", creation(body(input, "text")).resolvedType().orElseThrow().displayName());
    assertEquals("java.lang.String", argument(input, "conditional", 0).selectedTarget().displayName());
  }

  @Test
  void givenNestedArrayAndWildcardConstructorFormals_whenAnalyzed_thenInstantiatedStructuresRemainExact() throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T> Selection(java.util.List<T[]> values) {}
          <T extends Number> Selection(java.util.List<? extends T> values, int discriminator) {}
          static Selection arrays(java.util.List<String[]> values) { return new Selection(values); }
          static Selection bounded(java.util.List<Integer> values) { return new Selection(values, 1); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals("java.util.List<java.lang.String[]>", argument(input, "arrays", 0).selectedTarget().displayName());
    assertEquals("java.util.List<? extends java.lang.Integer>", argument(input, "bounded", 0).selectedTarget().displayName());
    assertEquals("int", argument(input, "bounded", 1).selectedTarget().displayName());
  }

  @Test
  void givenExternalFinalConstructorWithPrivateBound_whenAnalyzed_thenExactPrivateTargetIsCopiedWithoutChangingDependency(
      @TempDir Path directory) throws IOException {
    // Arrange
    var dependency = directory.resolve("support/GenericConstruction.java");
    Files.createDirectories(dependency.getParent());
    Files.writeString(dependency, """
        package support;
        public final class GenericConstruction {
          private static final class Hidden {}
          public GenericConstruction(int discriminator) {}
          public <T extends Hidden> GenericConstruction(T value) {}
          public <T> GenericConstruction(T value, int discriminator) {}
        }
        """, StandardCharsets.UTF_8);
    var classes = directory.resolve("classes");
    Files.createDirectories(classes);
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      assertTrue(compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none", "-d", classes.toString()), null,
          manager.getJavaFileObjects(dependency)).call());
    }
    var binary = classes.resolve("support/GenericConstruction.class");
    var originalBytes = Files.readAllBytes(binary);
    var archive = directory.resolve("constructors.jar");
    writeJar(classes, archive);
    var archiveBytes = Files.readAllBytes(archive);
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(archive), List.of(), List.of());
    var baseline = InputSnapshot.fromSources(directory.resolve("baseline"), List.of(SourceFile.fromContent(Path.of("Selection.java"), """
        class Selection {
          static support.GenericConstruction create() { return new support.GenericConstruction(1); }
        }
        """)));
    var baselineInput = new JavaFrontend(new JavaSourceLoader()).analyze(baseline, configuration);
    assertCompleted(baselineInput);
    var movedArchive = directory.resolve("constructors-moved.jar");
    Files.move(archive, movedArchive);
    Files.move(movedArchive, archive);
    var source = SourceFile.fromContent(Path.of("Selection.java"), """
        class Selection {
          static support.GenericConstruction privateBound() { return new support.GenericConstruction(null); }
          static support.GenericConstruction explicit(String value) { return new <CharSequence> support.GenericConstruction(value, 1); }
        }
        """);
    var snapshot = InputSnapshot.fromSources(directory.resolve("input"), List.of(source));

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot,
        configuration);

    // Assert
    assertCompleted(input);
    assertEquals("support.GenericConstruction.Hidden", argument(input, "privateBound", 0).selectedTarget().displayName());
    assertEquals("java.lang.CharSequence", argument(input, "explicit", 0).selectedTarget().displayName());
    assertArrayEquals(originalBytes, Files.readAllBytes(binary));
    assertArrayEquals(archiveBytes, Files.readAllBytes(archive));
    Files.move(archive, movedArchive);
    assertTrue(Files.exists(movedArchive));
    assertFalse(Files.exists(archive));
    Files.move(movedArchive, archive);
    assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
  }

  @Test
  void givenGenericExplicitConstructorDelegation_whenAnalyzed_thenOriginalPublicSelectorSuppliesItsSelectedTarget() throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T> Selection(T value) {}
          Selection() { <CharSequence>this("delegated"); }
          static Selection create() { return new Selection(); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    var constructor = input.methodSyntax("type:Selection#<init>()").orElseThrow();
    var invocation = constructor.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .findFirst().orElseThrow();
    assertEquals("java.lang.CharSequence",
        constructor.useDecision(invocation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget().displayName());
  }

  @Test
  void givenRecursiveBoundsNullAndCapture_whenAnalyzed_thenSelectedTargetsPreserveEachInferenceConstraint() throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T extends Comparable<T>> Selection(T value) {}
          <T extends Number> Selection(T value, boolean discriminator) {}
          <T> Selection(java.util.List<T> values, long discriminator) {}
          static Selection recursive() { return new Selection("text"); }
          static Selection boundedNull() { return new Selection(null, true); }
          static Selection capture(java.util.List<?> values) { return new Selection(values, 1L); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals("java.lang.String", argument(input, "recursive", 0).selectedTarget().displayName());
    assertEquals("java.lang.Number", argument(input, "boundedNull", 0).selectedTarget().displayName());
    var capture = argument(input, "capture", 0);
    assertEquals(capture.sourceType(), capture.selectedTarget());
    assertEquals("java.util.List", capture.selectedTarget().erasedName());
    assertTrue(capture.selectedTarget().displayName().contains("capture#"), capture.toString());
    assertTrue(capture.selectedTarget().displayName().contains(" of ?"), capture.toString());
  }

  @Test
  void givenMixedConstructorArgumentTypes_whenAnalyzed_thenTheSameInferredIntersectionTargetsBothArguments() throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T> Selection(T first, T second) {}
          static Selection mixed() { return new Selection("text", 1); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    var first = argument(input, "mixed", 0);
    var second = argument(input, "mixed", 1);
    assertEquals(JavaTypeCategory.INTERSECTION, first.selectedTarget().category());
    assertEquals(first.selectedTarget(), second.selectedTarget());
    assertTrue(first.selectedTarget().displayName().contains("java.io.Serializable"));
    assertTrue(first.selectedTarget().displayName().contains("java.lang.Comparable"));
    assertEquals("java.lang.Integer", second.boxedType().orElseThrow().displayName());
  }

  @Test
  void givenQualifiedInnerCreationWithFixedAndDiamondArguments_whenAnalyzed_thenEnclosingClassArgumentsRemainFixed() throws IOException {
    // Arrange
    var source = """
        class Selection<A> {
          class Nested<C> {
            <T extends C> Nested(T value, A enclosingValue) {}
          }
          static Selection<String>.Nested<Number> fixed(Selection<String> owner, Integer value) {
            return owner.new Nested<Number>(value, "outer");
          }
          static Selection<String>.Nested<Number> diamond(Selection<String> owner, Integer value) {
            return owner.new Nested<>(value, "outer");
          }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals("java.lang.Integer", argument(input, "fixed", 0).selectedTarget().displayName());
    assertEquals("java.lang.String", argument(input, "fixed", 1).selectedTarget().displayName());
    assertEquals("java.lang.Integer", argument(input, "diamond", 0).selectedTarget().displayName());
    assertEquals("java.lang.String", argument(input, "diamond", 1).selectedTarget().displayName());
    assertEquals("Selection<java.lang.String>.Nested<java.lang.Number>",
        creation(body(input, "diamond")).resolvedType().orElseThrow().displayName());
  }

  @Test
  void givenQualifiedInnerCreationWithWildcardEnclosingOwner_whenAnalyzed_thenNullInferenceRetainsTheOriginalEnclosingCapture()
      throws IOException {
    // Arrange
    var source = """
        class Selection<A extends Number> {
          class Nested {
            <T extends A> Nested(T value) {}
          }
          static Selection<?>.Nested create(Selection<?> owner) { return owner.new Nested(null); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertEnclosingCaptureSelection(source);
    var method = body(input, "create");
    var selected = method.useDecision(creation(method).nodeId(), SyntaxRole.ARGUMENT, 0);
    assertTrue(selected.isPresent(), () -> method.root().descendantsAndSelf().toString());
    assertEquals(JavaTypeCategory.TYPE_VARIABLE, selected.orElseThrow().selectedTarget().category());
    assertEquals("java.lang.Number", selected.orElseThrow().selectedTarget().erasedName());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(method), () -> method.root().descendantsAndSelf().toString());
    assertCompleted(input);
  }

  @Test
  void givenQualifiedInnerCreationWithComputedWildcardOwner_whenAnalyzed_thenSelectedTargetRetainsTheOriginalEnclosingCapture()
      throws IOException {
    // Arrange
    var source = """
        class Selection<A extends Number> {
          class Nested {
            <T extends A> Nested(T value) {}
          }
          static Selection<?> owner() { return null; }
          static Selection<?>.Nested create() { return owner().new Nested(null); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertEnclosingCaptureSelection(source);
    var method = body(input, "create");
    var selected = method.useDecision(creation(method).nodeId(), SyntaxRole.ARGUMENT, 0);
    assertTrue(selected.isPresent(), () -> method.root().descendantsAndSelf().toString());
    assertEquals(JavaTypeCategory.TYPE_VARIABLE, selected.orElseThrow().selectedTarget().category());
    assertEquals("java.lang.Number", selected.orElseThrow().selectedTarget().erasedName());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(method), () -> method.root().descendantsAndSelf().toString());
    assertEquals(List.of(), input.diagnostics());
    assertCompleted(input);
  }

  @Test
  void givenQualifiedInnerCreationWithParenthesizedWildcardCast_whenAnalyzed_thenSelectedTargetRetainsTheOriginalEnclosingCapture()
      throws IOException {
    // Arrange
    var source = """
        class Selection<A extends Number> {
          class Nested {
            <T extends A> Nested(T value) {}
          }
          static Selection<?>.Nested create(Selection<?> owner) { return ((Selection<?>)owner).new Nested(null); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertEnclosingCaptureSelection(source);
    var method = body(input, "create");
    var selected = method.useDecision(creation(method).nodeId(), SyntaxRole.ARGUMENT, 0);
    assertTrue(selected.isPresent(), () -> method.root().descendantsAndSelf().toString());
    assertEquals(JavaTypeCategory.TYPE_VARIABLE, selected.orElseThrow().selectedTarget().category());
    assertEquals("java.lang.Number", selected.orElseThrow().selectedTarget().erasedName());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(method), () -> method.root().descendantsAndSelf().toString());
    assertEquals(List.of(), input.diagnostics());
    assertCompleted(input);
  }

  @Test
  void givenRawClassWithGenericConstructor_whenAnalyzed_thenSelectedFormalRetainsTheOriginalRawMemberErasure() throws IOException {
    // Arrange
    var source = """
        class Selection<A> {
          <T extends A> Selection(T value) {}
          static Selection create() { return new Selection("text"); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertEquals("java.lang.Object", argument(input, "create", 0).selectedTarget().displayName());
    assertEquals("java.lang.String", argument(input, "create", 0).sourceType().displayName());
    assertEquals(InvocationApplicabilityMode.FIXED,
        body(input, "create").invocationArity(creation(body(input, "create")).nodeId()).orElseThrow().mode());
    assertCompleted(input);
  }

  @Test
  void givenExternalQualifiedInnerGenericConstructor_whenAnalyzed_thenBinaryEnclosingParametersStayOutsideWrittenArgumentSlots(
      @TempDir Path directory) throws IOException {
    // Arrange
    var dependency = directory.resolve("support/GenericConstruction.java");
    Files.createDirectories(dependency.getParent());
    Files.writeString(dependency, """
        package support;
        public class GenericConstruction<A> {
          public class Nested<C> {
            public <T extends C> Nested(T value, A enclosingValue) {}
          }
        }
        """, StandardCharsets.UTF_8);
    var classes = directory.resolve("classes");
    Files.createDirectories(classes);
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      assertTrue(compiler.getTask(null, manager, null, List.of("--release", "25", "-proc:none", "-d", classes.toString()), null,
          manager.getJavaFileObjects(dependency)).call());
    }
    var binary = classes.resolve("support/GenericConstruction$Nested.class");
    var originalBytes = Files.readAllBytes(binary);
    var source = SourceFile.fromContent(Path.of("Selection.java"), """
        class Selection {
          static support.GenericConstruction<String>.Nested<Number> fixed(support.GenericConstruction<String> owner, Integer value) {
            return owner.new Nested<Number>(value, "outer");
          }
          static support.GenericConstruction<String>.Nested<Number> diamond(support.GenericConstruction<String> owner, Integer value) {
            return owner.new Nested<>(value, "outer");
          }
        }
        """);
    var snapshot = InputSnapshot.fromSources(directory.resolve("input"), List.of(source));

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(classes), List.of(), List.of()));

    // Assert
    assertCompleted(input);
    assertEquals("java.lang.Integer", argument(input, "fixed", 0).selectedTarget().displayName());
    assertEquals("java.lang.String", argument(input, "fixed", 1).selectedTarget().displayName());
    assertEquals("java.lang.Integer", argument(input, "diamond", 0).selectedTarget().displayName());
    assertEquals("java.lang.String", argument(input, "diamond", 1).selectedTarget().displayName());
    var method = body(input, "diamond");
    assertEquals(2, method.invocationArity(creation(method).nodeId()).orElseThrow().formalCount());
    assertArrayEquals(originalBytes, Files.readAllBytes(binary));
  }

  @Test
  void givenEnclosingTypeParameterNamesAndInheritedMethodCollision_whenAnalyzed_thenWitnessNamesPreserveOriginalBindings()
      throws IOException {
    // Arrange
    var source = """
        class Parent {
          final int constructorSelection(String value, String enclosingValue) { return 1; }
        }
        class Selection<ClassType0> {
          class Nested<A> extends Parent {
            <ConstructorType0 extends A> Nested(ConstructorType0 value, ClassType0 enclosingValue) {}
          }
          static Selection<String>.Nested<CharSequence> create(Selection<String> owner) {
            return owner.new Nested<>("text", "outer");
          }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals("java.lang.String", argument(input, "create", 0).selectedTarget().displayName());
    assertEquals("java.lang.String", argument(input, "create", 1).selectedTarget().displayName());
    assertEquals("Selection<java.lang.String>.Nested<java.lang.CharSequence>",
        creation(body(input, "create")).resolvedType().orElseThrow().displayName());
  }

  @Test
  void givenNullArrayAndExpandedGenericVarargs_whenAnalyzed_thenFixedPriorityAndBoundOnlyArrayRemainExact() throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T extends Number> Selection(T... values) {}
          static Selection arrayNull() { return new Selection(null); }
          static Selection componentNull() { return new Selection((Number)null); }
          static Selection zero() { return new Selection(); }
          static Selection explicit(Integer first, Integer second) { return new <Number> Selection(first, second); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    var fixed = body(input, "arrayNull");
    assertEquals(InvocationApplicabilityMode.FIXED, fixed.invocationArity(creation(fixed).nodeId()).orElseThrow().mode());
    assertEquals("java.lang.Number[]", argument(input, "arrayNull", 0).selectedTarget().displayName());
    var expanded = body(input, "componentNull");
    var arity = expanded.invocationArity(creation(expanded).nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, arity.mode());
    assertEquals(1, arity.formalCount());
    assertEquals("java.lang.Number[]", arity.varargArrayType().orElseThrow().displayName());
    assertEquals("java.lang.Number", argument(input, "componentNull", 0).selectedTarget().displayName());
    var zero = body(input, "zero");
    assertEquals("java.lang.Number[]", zero.invocationArity(creation(zero).nodeId()).orElseThrow().varargArrayType().orElseThrow().displayName());
    assertEquals("java.lang.Number", argument(input, "explicit", 0).selectedTarget().displayName());
    assertEquals("java.lang.Number", argument(input, "explicit", 1).selectedTarget().displayName());
  }

  @Test
  void givenGenericConstructorWithReifiableVarargsAndWarningsAsErrors_whenAnalyzed_thenOriginalCompilerOptionsRemainEffective()
      throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T extends Number> Selection(T discriminator, Object... values) {}
          Selection(Integer value, boolean discriminator) { <Integer>this(value, (Object)null); }
          static Selection fixed(Integer value, Object[] values) { return new Selection(value, values); }
          static Selection zero(Integer value) { return new Selection(value); }
          static Selection expanded(Integer value) { return new Selection(value, "text", 1); }
          static Selection arrayNull(Integer value) { return new Selection(value, (Object[])null); }
          static Selection componentNull(Integer value) { return new Selection(value, (Object)null); }
        }
        """;
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of("-Xlint:all", "-Werror"));

    // Act
    var input = analyze(source, configuration);

    // Assert
    var componentNull = body(input, "componentNull");
    var componentCreation = creation(componentNull);
    assertTrue(componentNull.useDecision(componentCreation.nodeId(), SyntaxRole.ARGUMENT, 1).isPresent(),
        () -> componentNull.root().descendantsAndSelf().toString());
    var component = argument(input, "componentNull", 1);
    assertEquals("java.lang.Object", component.sourceType().displayName());
    assertEquals("java.lang.Object", component.selectedTarget().displayName());
    assertEquals(InvocationApplicabilityMode.VARIABLE, componentNull.invocationArity(componentCreation.nodeId()).orElseThrow().mode());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(componentNull),
        () -> componentNull.root().descendantsAndSelf() + " arguments=" + component + " arity="
            + componentNull.invocationArity(componentCreation.nodeId()));
    var delegation = input.methodSyntax("type:Selection#<init>(java.lang.Integer,boolean)").orElseThrow();
    var delegated = delegation.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .findFirst().orElseThrow();
    assertEquals(delegation.useDecision(delegated.nodeId(), SyntaxRole.ARGUMENT, 1).orElseThrow().selectedTarget(), component.selectedTarget());
    assertCompleted(input);
    assertEquals(List.of(), input.diagnostics());
    assertEquals("java.lang.Integer", argument(input, "fixed", 0).selectedTarget().displayName());
    assertEquals("java.lang.Object[]", argument(input, "fixed", 1).selectedTarget().displayName());
    var fixed = body(input, "fixed");
    assertEquals(InvocationApplicabilityMode.FIXED, fixed.invocationArity(creation(fixed).nodeId()).orElseThrow().mode());
    var zero = body(input, "zero");
    var zeroArity = zero.invocationArity(creation(zero).nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, zeroArity.mode());
    assertEquals(2, zeroArity.formalCount());
    assertEquals("java.lang.Object[]", zeroArity.varargArrayType().orElseThrow().displayName());
    assertEquals("java.lang.Object", argument(input, "expanded", 1).selectedTarget().displayName());
    assertEquals("java.lang.Object", argument(input, "expanded", 2).selectedTarget().displayName());
    assertEquals("java.lang.Integer", argument(input, "expanded", 2).boxedType().orElseThrow().displayName());
    assertEquals("java.lang.Object[]", argument(input, "arrayNull", 1).selectedTarget().displayName());
    assertEquals("java.lang.Object", argument(input, "componentNull", 1).selectedTarget().displayName());
  }

  @Test
  void givenNonReifiableGenericVarargsWithWarningsAsErrors_whenAnalyzed_thenOriginalWarningAndCompilerFailureRemainVisible()
      throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T> Selection(T... values) {}
          static Selection create() { return new Selection("text", "other"); }
        }
        """;
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of("-Xlint:all", "-Werror"));

    // Act
    var input = analyze(source, configuration);

    // Assert
    assertTrue(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("compiler.warn.unchecked.varargs.non.reifiable.type")),
        input.diagnostics().toString());
    assertTrue(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()
        && diagnostic.code().equals("compiler.err.warnings.and.werror")), input.diagnostics().toString());
    assertTrue(input.diagnostics().stream().noneMatch(diagnostic -> diagnostic.message().contains("constructorSelection")));
  }

  @Test
  void givenCaptureInAWiderConstructorFormal_whenAnalyzed_thenSelectedCompositeTargetUsesTheOriginalCapturedVariable() throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T> Selection(java.util.Collection<T> values) {}
          static Selection capture(java.util.List<?> values) { return new Selection(values); }
        }
        """;
    var snapshot = InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/compiler-input"),
        List.of(SourceFile.fromContent(Path.of("Selection.java"), source)));
    var catalog = new SnapshotSourceCatalog(snapshot);
    var invocation = JavaCompilerInvocation.from(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();

    // Act
    var input = analyze(source);
    try (var owned = new CompilerFileManager(compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8))) {
      var manager = owned.standardFileManager();
      var task = (JavacTask) compiler.getTask(null, owned, diagnostics, invocation.compilerOptions(), null, catalog.compilationUnits());
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      task.analyze();
      var trees = Trees.instance(task);
      var paths = new ArrayList<TreePath>();
      var scanner = new TreePathScanner<Void, Void>() {
        @Override
        public Void visitNewClass(NewClassTree tree, Void ignored) {
          paths.add(getCurrentPath());
          return super.visitNewClass(tree, null);
        }
      };
      units.forEach(unit -> scanner.scan(unit, null));
      var path = paths.getFirst();
      var creation = assertInstanceOf(NewClassTree.class, path.getLeaf());
      var selected = assertInstanceOf(ExecutableElement.class, trees.getElement(path));
      var argument = assertInstanceOf(DeclaredType.class, trees.getTypeMirror(new TreePath(path, creation.getArguments().getFirst())));
      var collection = task.getTypes().getDeclaredType(task.getElements().getTypeElement("java.util.Collection"),
          argument.getTypeArguments().getFirst());
      var selection = new JavaConstructorSelection(compiler, invocation, catalog, manager, trees, task.getTypes(), task.getElements());
      selection.prepare(units);
      var facts = selection.select(path, selected).orElseThrow();

      // Assert
      assertEquals(1, paths.size());
      assertCompleted(input);
      assertEquals("java.util.Collection", argument(input, "capture", 0).selectedTarget().erasedName());
      assertEquals(argument.toString(), facts.arguments().getFirst().sourceType().displayName());
      assertEquals(collection.toString(), facts.arguments().getFirst().selectedTarget().displayName());
      assertEquals(List.of(), diagnostics.getDiagnostics());
    }
  }

  @Test
  void givenAnnotatedGenericConstructorFormal_whenAnalyzed_thenCreationMatchesTheOriginalDelegationSelector() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.util.List;
        @Target(ElementType.TYPE_USE) @interface Marker {}
        class Selection {
          <T> Selection(@Marker T value) {}
          <T> Selection(@Marker List<T> values, int discriminator) {}
          Selection() { <String>this("text"); }
          Selection(boolean discriminator) { <String>this(List.of("text"), 1); }
          static Selection create() { return new <String> Selection("text"); }
          static Selection declared() { return new <String> Selection(List.of("text"), 1); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    var constructor = input.methodSyntax("type:Selection#<init>()").orElseThrow();
    var delegation = constructor.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .findFirst().orElseThrow();
    var originalTarget = constructor.useDecision(delegation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget();
    assertEquals(originalTarget, argument(input, "create", 0).selectedTarget());
    assertEquals("java.lang.String", originalTarget.displayName());
    var annotatedConstructor = input.methodSyntax("type:Selection#<init>(boolean)").orElseThrow();
    var annotatedDelegation = annotatedConstructor.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .findFirst().orElseThrow();
    var annotatedTarget = annotatedConstructor.useDecision(annotatedDelegation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget();
    assertEquals(annotatedTarget, argument(input, "declared", 0).selectedTarget());
    assertEquals("java.util.List<java.lang.String>", annotatedTarget.displayName());
    assertAnnotatedFormal(InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/compiler-input"),
        List.of(SourceFile.fromContent(Path.of("Selection.java"), source))), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), "Selection");
  }

  @Test
  void givenAnnotatedExternalGenericConstructor_whenAnalyzed_thenBinaryCreationMatchesItsOriginalSuperSelector(@TempDir Path directory)
      throws IOException {
    // Arrange
    var dependency = directory.resolve("support/GenericConstruction.java");
    Files.createDirectories(dependency.getParent());
    Files.writeString(dependency, """
        package support;
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        import java.util.List;
        public class GenericConstruction {
          @Target(ElementType.TYPE_USE) public @interface Marker {}
          public <T> GenericConstruction(@Marker List<T> values, int discriminator) {}
        }
        """, StandardCharsets.UTF_8);
    var classes = directory.resolve("classes");
    Files.createDirectories(classes);
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      assertTrue(compiler.getTask(null, manager, null, List.of("--release", "25", "-proc:none", "-d", classes.toString()), null,
          manager.getJavaFileObjects(dependency)).call());
    }
    var binary = classes.resolve("support/GenericConstruction.class");
    var originalBytes = Files.readAllBytes(binary);
    var snapshot = InputSnapshot.fromSources(directory.resolve("input"), List.of(SourceFile.fromContent(Path.of("Selection.java"), """
        class Selection extends support.GenericConstruction {
          Selection() { <String>super(java.util.List.of("text"), 1); }
          static support.GenericConstruction create() { return new <String> support.GenericConstruction(java.util.List.of("text"), 1); }
        }
        """)));

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(classes), List.of(), List.of()));

    // Assert
    assertCompleted(input);
    var constructor = input.methodSyntax("type:Selection#<init>()").orElseThrow();
    var delegation = constructor.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .findFirst().orElseThrow();
    var target = constructor.useDecision(delegation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().selectedTarget();
    assertEquals("java.util.List<java.lang.String>", target.displayName());
    assertEquals(target, argument(input, "create", 0).selectedTarget());
    assertArrayEquals(originalBytes, Files.readAllBytes(binary));
    assertAnnotatedFormal(snapshot, new CompilerConfiguration(JavaRelease.JAVA_25, List.of(classes), List.of(), List.of()),
        "support.GenericConstruction");
  }

  @Test
  void givenAssignedCapturedEnclosingReceiver_whenAnalyzed_thenSelectedTargetRetainsTheOriginalCapture() throws IOException {
    // Arrange
    var source = """
        class Selection<T> {
          class Nested { <U extends T> Nested(U value) {} }
          static Object create(Selection<? extends Number> first, Selection<? extends Number> second) {
            return (first = second).new Nested(null);
          }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals("java.lang.Number", argument(input, "create", 0).selectedTarget().erasedName());
    assertEquals(JavaTypeCategory.TYPE_VARIABLE, argument(input, "create", 0).selectedTarget().category());
    assertEnclosingCaptureSelection(source);
  }

  @Test
  void givenAssignedFieldEnclosingReceiver_whenAnalyzed_thenSelectedTargetRetainsTheOriginalCapture() throws IOException {
    // Arrange
    var source = """
        class Selection<T> {
          class Nested { <U extends T> Nested(U value) {} }
          Selection<? extends Number> stored;
          Object create(Selection<? extends Number> value) {
            return (stored = value).new Nested(null);
          }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals("java.lang.Number", argument(input, "create", 0).selectedTarget().erasedName());
    assertEquals(JavaTypeCategory.TYPE_VARIABLE, argument(input, "create", 0).selectedTarget().category());
    assertEnclosingCaptureSelection(source);
  }

  @Test
  void givenAssignedArrayElementEnclosingReceiver_whenAnalyzed_thenSelectedTargetRetainsTheOriginalCapture() throws IOException {
    // Arrange
    var source = """
        class Selection<T> {
          class Nested { <U extends T> Nested(U value) {} }
          static Object create(Selection<? extends Number>[] values, Selection<? extends Number> source) {
            return (values[0] = source).new Nested(null);
          }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals("java.lang.Number", argument(input, "create", 0).selectedTarget().erasedName());
    assertEquals(JavaTypeCategory.TYPE_VARIABLE, argument(input, "create", 0).selectedTarget().category());
    assertEnclosingCaptureSelection(source);
  }

  private static void assertAnnotatedFormal(InputSnapshot snapshot, CompilerConfiguration configuration, String owner) throws IOException {
    var catalog = new SnapshotSourceCatalog(snapshot);
    var invocation = JavaCompilerInvocation.from(configuration);
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var owned = new CompilerFileManager(compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8))) {
      var task = (JavacTask) compiler.getTask(null, owned, diagnostics, invocation.compilerOptions(), null, catalog.compilationUnits());
      task.parse();
      task.analyze();
      var declaration = assertInstanceOf(TypeElement.class, task.getElements().getTypeElement(owner));
      var constructor = declaration.getEnclosedElements().stream().filter(ExecutableElement.class::isInstance)
          .map(ExecutableElement.class::cast).filter(element -> !element.getTypeParameters().isEmpty() && element.getParameters().size() == 2)
          .findFirst().orElseThrow();
      assertTrue(constructor.getParameters().getFirst().asType().getAnnotationMirrors().stream()
          .anyMatch(annotation -> annotation.getAnnotationType().asElement().getSimpleName().contentEquals("Marker")));
      assertEquals(List.of(), diagnostics.getDiagnostics());
    }
  }

  private static void assertEnclosingCaptureSelection(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/compiler-input"),
        List.of(SourceFile.fromContent(Path.of("Selection.java"), source)));
    var catalog = new SnapshotSourceCatalog(snapshot);
    var invocation = JavaCompilerInvocation.from(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var owned = new CompilerFileManager(compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8))) {
      var task = (JavacTask) compiler.getTask(null, owned, diagnostics, invocation.compilerOptions(), null, catalog.compilationUnits());
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      task.analyze();
      var trees = Trees.instance(task);
      var paths = new ArrayList<TreePath>();
      var scanner = new TreePathScanner<Void, Void>() {
        @Override
        public Void visitNewClass(NewClassTree creation, Void ignored) {
          paths.add(getCurrentPath());
          return super.visitNewClass(creation, null);
        }
      };
      units.forEach(unit -> scanner.scan(unit, null));
      assertEquals(1, paths.size());
      var path = paths.getFirst();
      var selected = assertInstanceOf(ExecutableElement.class, trees.getElement(path));
      var created = assertInstanceOf(DeclaredType.class, trees.getTypeMirror(path));
      var enclosing = assertInstanceOf(DeclaredType.class, created.getEnclosingType());
      var originalCapture = assertInstanceOf(TypeVariable.class, enclosing.getTypeArguments().getFirst());
      var semantics = new SemanticResolver(trees, task.getTypes(), task.getElements(), new DeclarationIdentityRegistry(trees, task.getTypes()));
      var selection = new JavaConstructorSelection(compiler, invocation, catalog, owned.standardFileManager(), trees, task.getTypes(),
          task.getElements());
      selection.prepare(units);
      assertEquals(semantics.typeIdentity(originalCapture), selection.select(path, selected).orElseThrow().arguments().getFirst().selectedTarget());
      assertEquals(List.of(), diagnostics.getDiagnostics());
    }
  }

  @Test
  void givenDiamondGenericVarargsWithPolyAndCaptureArguments_whenAnalyzed_thenExpandedArrayRetainsTargetContextAndOriginalCaptures()
      throws IOException {
    // Arrange
    var source = """
        class Selection<A> {
          <T extends A> Selection(T... values) {}
          static Selection<java.util.List<String>> poly() {
            return new Selection<>(java.util.Collections.emptyList(), java.util.Collections.singletonList("text"));
          }
          static Selection<java.util.List<?>> capture(java.util.List<?> values) { return new Selection<>(values); }
        }
        """;

    // Act
    var input = analyze(source);

    // Assert
    assertCompleted(input);
    assertEquals("java.util.List<java.lang.String>", argument(input, "poly", 0).selectedTarget().displayName());
    assertEquals("java.util.List<java.lang.String>", argument(input, "poly", 1).selectedTarget().displayName());
    var poly = body(input, "poly");
    var array = poly.invocationArity(creation(poly).nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, array.mode());
    assertEquals("java.util.List<java.lang.String>[]", array.varargArrayType().orElseThrow().displayName());
    var captured = argument(input, "capture", 0);
    assertEquals(captured.sourceType(), captured.selectedTarget());
    var capture = body(input, "capture");
    var captureArity = capture.invocationArity(creation(capture).nodeId()).orElseThrow();
    assertEquals(InvocationApplicabilityMode.VARIABLE, captureArity.mode());
    assertEquals(captured.selectedTarget(), captureArity.elementType().orElseThrow());
  }

  private static void writeJar(Path classes, Path archive) throws IOException {
    try (var paths = Files.walk(classes); var output = new JarOutputStream(Files.newOutputStream(archive))) {
      for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
        output.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
        Files.copy(path, output);
        output.closeEntry();
      }
    }
  }

  private static SyntaxAnalysisInput analyze(String source) throws IOException {
    return analyze(source, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
  }

  private static SyntaxAnalysisInput analyze(String source, CompilerConfiguration configuration) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("D:/Projects/aquila-graph/aquila-graph/target/compiler-input"),
        List.of(SourceFile.fromContent(Path.of("Selection.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, configuration);
  }

  private static void assertCompleted(SyntaxAnalysisInput input) {
    assertTrue(input.diagnostics().stream().noneMatch(diagnostic -> diagnostic.isError()), input.diagnostics().toString());
    var run = new SourceAnalysisFixture().analyze(input);
    assertEquals(AnalysisCompletion.COMPLETE, run.completion(), run.diagnostics().toString());
  }

  private static MethodSyntax body(SyntaxAnalysisInput input, String name) {
    return input.methodSyntax().stream().filter(method -> method.methodKey().startsWith("type:Selection#" + name + "("))
        .findFirst().orElseThrow();
  }

  private static SyntaxNode creation(MethodSyntax method) {
    return method.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).findFirst()
        .orElseThrow();
  }

  private static ConversionEvidence argument(SyntaxAnalysisInput input, String name, int position) {
    var method = body(input, name);
    return method.useDecision(creation(method).nodeId(), SyntaxRole.ARGUMENT, position).orElseThrow();
  }
}
