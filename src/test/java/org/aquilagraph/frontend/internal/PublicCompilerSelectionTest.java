package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.source.internal.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.application.AnalysisCompletion;
import org.junit.jupiter.api.Test;

class PublicCompilerSelectionTest {
  @Test
  void givenExternalOverloadsAndGenericMethods_whenPublicCompilerAttributes_thenSelectedFormalTypesAreExposed() throws IOException {
    // Arrange
    var source = """
        class Selection {
          static Number boxed() { return Integer.valueOf(1); }
          static String selected(String text) { return String.valueOf(text); }
          static String generic() { return java.util.Objects.requireNonNull("text"); }
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(List.of("int"), observations.calls().get("Integer.valueOf(1)").formalTypes());
    assertEquals(List.of("java.lang.Object"), observations.calls().get("String.valueOf(text)").formalTypes());
    assertEquals(List.of("java.lang.String"), observations.calls().get("java.util.Objects.requireNonNull(\"text\")").formalTypes());
  }

  @Test
  void givenFixedAndVariableArrayCalls_whenPublicCompilerAttributes_thenArrayFormalAndActualTypesRemainDistinct() throws IOException {
    // Arrange
    var source = """
        class Selection {
          static java.util.List<String> fixed(String[] values) { return java.util.Arrays.asList(values); }
          static java.util.List<String> zero() { return java.util.Arrays.<String>asList(); }
          static java.util.List<String> one() { return java.util.Arrays.asList("one"); }
          static java.util.List<String> two() { return java.util.Arrays.asList("one", "two"); }
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(new CallEvidence(List.of("java.lang.String[]"), List.of("java.lang.String[]"), true),
        observations.calls().get("java.util.Arrays.asList(values)"));
    assertEquals(new CallEvidence(List.of("java.lang.String[]"), List.of(), true),
        observations.calls().get("java.util.Arrays.<String>asList()"));
    assertEquals(new CallEvidence(List.of("java.lang.String[]"), List.of("java.lang.String"), true),
        observations.calls().get("java.util.Arrays.asList(\"one\")"));
    assertEquals(new CallEvidence(List.of("java.lang.String[]"), List.of("java.lang.String", "java.lang.String"), true),
        observations.calls().get("java.util.Arrays.asList(\"one\", \"two\")"));
  }

  @Test
  void givenExternalBoxingUnboxingAndWideningUses_whenPublicCompilerAttributes_thenExactSelectedAndActualTypesAreAvailable()
      throws IOException {
    // Arrange
    var source = """
        class Selection {
          static java.util.List<Object> box(int value) { return java.util.Collections.<Object>singletonList(value); }
          static java.util.List<Integer> exactBox(int value) { return java.util.Collections.<Integer>singletonList(value); }
          static long unboxWiden(Integer value) { return Math.max(value, 2L); }
          static int unbox(Integer value) { return Math.abs(value); }
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(new CallEvidence(List.of("java.lang.Object"), List.of("int"), false),
        observations.calls().get("java.util.Collections.<Object>singletonList(value)"));
    assertEquals(new CallEvidence(List.of("java.lang.Integer"), List.of("int"), false),
        observations.calls().get("java.util.Collections.<Integer>singletonList(value)"));
    assertEquals(new CallEvidence(List.of("long", "long"), List.of("java.lang.Integer", "long"), false),
        observations.calls().get("Math.max(value, 2L)"));
    assertEquals(new CallEvidence(List.of("int"), List.of("java.lang.Integer"), false), observations.calls().get("Math.abs(value)"));
  }

  @Test
  void givenGenericConstructor_whenPublicCompilerAttributes_thenCallPathsExposeClassTypesAndDeclarationRetainsTypeVariable()
      throws IOException {
    // Arrange
    var source = """
        class Selection {
          <T> Selection(T value) {}
          static Selection create() { return new Selection("text"); }
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(new ConstructorEvidence("Selection", "Selection", List.of("T"), List.of("java.lang.String")),
        observations.constructors().get("new Selection(\"text\")"));
  }

  @Test
  void givenGenericConstructorInCapturedSource_whenFrontendAnalyzes_thenCurrentProfileAdmitsItsCallAndAuxiliaryConstructorBody()
      throws IOException {
    // Arrange
    var source = SourceFile.fromContent(capturedPath(Path.of("Selection.java")), """
        class Selection {
          <T> Selection(T value) {}
          static Selection create() { return new Selection("text"); }
        }
        class Auxiliary {}
        """);
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(Path.of("D:/Projects/aquila-graph/aquila-graph/target/compiler-input")), List.of(source));

    // Act
    var input = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    assertEquals(List.of(), input.diagnostics());
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
    assertTrue(input.methodSyntax().stream().anyMatch(body -> body.methodKey().equals("type:Auxiliary#<init>()")));
  }

  @Test
  void givenConstantsAndStringPlus_whenPublicCompilerAttributes_thenConstantsAndExpressionTypesArePublicFacts() throws IOException {
    // Arrange
    var source = """
        class Selection {
          static final int SMALL = 1 + 2;
          static final String TEXT = "prefix" + SMALL;
          static Byte narrowBox() { return SMALL; }
          static String concat(int value) { return "prefix" + value; }
          static int numeric(int value) { return 1 + value; }
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(3, observations.constants().get("SMALL"));
    assertEquals("prefix3", observations.constants().get("TEXT"));
    assertEquals(List.of("int", "java.lang.String", "java.lang.String", "int"), observations.plusTypes());
  }

  @Test
  void givenInheritedAndExternalStaticMembers_whenPublicCompilerAttributes_thenSelectedDeclaringOwnersAndConstantsAreExposed()
      throws IOException {
    // Arrange
    var source = """
        class Parent {
          static int nonconstant = 1;
          static final int CONSTANT = 3;
          static String text() { return "text"; }
        }
        class Selection extends Parent {
          static int fields() { return Selection.nonconstant + Selection.CONSTANT + Integer.MAX_VALUE; }
          static String call() { return Selection.text(); }
          static Selection create() { return new Selection(); }
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(new SelectedField("Parent", "nonconstant", Optional.empty()), observations.selectedFields().get("Selection.nonconstant"));
    assertEquals(new SelectedField("Parent", "CONSTANT", Optional.of(3)), observations.selectedFields().get("Selection.CONSTANT"));
    assertEquals(new SelectedField("java.lang.Integer", "MAX_VALUE", Optional.of(2147483647)),
        observations.selectedFields().get("Integer.MAX_VALUE"));
    assertEquals("Parent", observations.callOwners().get("Selection.text()"));
    assertEquals("Selection", observations.creationOwners().get("new Selection()"));
  }

  private static Evidence observe(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var input = SimpleJavaFileObject.forSource(Path.of("D:/Projects/aquila-graph/aquila-graph/target/compiler-input/Selection.java").toUri(),
        source);
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none"), null, List.of(input));
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      task.analyze();
      assertEquals(List.of(), diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList());
      var trees = Trees.instance(task);
      var calls = new LinkedHashMap<String, CallEvidence>();
      var constructors = new LinkedHashMap<String, ConstructorEvidence>();
      var constants = new LinkedHashMap<String, Object>();
      var selectedFields = new LinkedHashMap<String, SelectedField>();
      var callOwners = new LinkedHashMap<String, String>();
      var creationOwners = new LinkedHashMap<String, String>();
      var plusTypes = new ArrayList<String>();
      var visitor = new TreePathScanner<Void, Void>() {
        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
          if (!tree.getMethodSelect().toString().equals("super")) {
            var selected = assertInstanceOf(ExecutableElement.class, trees.getElement(getCurrentPath()));
            callOwners.put(tree.toString(), assertInstanceOf(TypeElement.class, selected.getEnclosingElement()).getQualifiedName().toString());
            var mirror = assertInstanceOf(ExecutableType.class, trees.getTypeMirror(new TreePath(getCurrentPath(), tree.getMethodSelect())));
            calls.put(tree.toString(), new CallEvidence(mirror.getParameterTypes().stream().map(Object::toString).toList(),
                tree.getArguments().stream().map(argument -> trees.getTypeMirror(new TreePath(getCurrentPath(), argument)).toString())
                    .toList(),
                selected.isVarArgs()));
          }
          return super.visitMethodInvocation(tree, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree tree, Void unused) {
          var selected = assertInstanceOf(ExecutableElement.class, trees.getElement(getCurrentPath()));
          creationOwners.put(tree.toString(), assertInstanceOf(TypeElement.class, selected.getEnclosingElement()).getQualifiedName().toString());
          var created = assertInstanceOf(DeclaredType.class, trees.getTypeMirror(getCurrentPath()));
          var identifier = trees.getTypeMirror(new TreePath(getCurrentPath(), tree.getIdentifier()));
          assertEquals(TypeKind.DECLARED, identifier.getKind());
          var declaration = assertInstanceOf(ExecutableType.class, task.getTypes().asMemberOf(created, selected));
          constructors.put(tree.toString(), new ConstructorEvidence(created.toString(), identifier.toString(),
              declaration.getParameterTypes().stream().map(Object::toString).toList(),
              tree.getArguments().stream().map(argument -> trees.getTypeMirror(new TreePath(getCurrentPath(), argument)).toString())
                  .toList()));
          return super.visitNewClass(tree, unused);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
          var element = assertInstanceOf(VariableElement.class, trees.getElement(getCurrentPath()));
          var value = element.getConstantValue();
          if (value != null) {
            constants.put(tree.getName().toString(), value);
          }
          return super.visitVariable(tree, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
          var selected = trees.getElement(getCurrentPath());
          if (selected instanceof VariableElement field && field.getModifiers().contains(Modifier.STATIC)) {
            var owner = assertInstanceOf(TypeElement.class, field.getEnclosingElement());
            selectedFields.put(tree.toString(), new SelectedField(owner.getQualifiedName().toString(), field.getSimpleName().toString(),
                Optional.ofNullable(field.getConstantValue())));
          }
          return super.visitMemberSelect(tree, unused);
        }

        @Override
        public Void scan(Tree tree, Void unused) {
          if (tree != null && tree.getKind() == Tree.Kind.PLUS) {
            plusTypes.add(trees.getTypeMirror(new TreePath(getCurrentPath(), tree)).toString());
          }
          return super.scan(tree, unused);
        }
      };
      units.forEach(unit -> visitor.scan(unit, null));
      assertTrue(diagnostics.getDiagnostics().isEmpty());
      return new Evidence(Map.copyOf(calls), Map.copyOf(constructors), Map.copyOf(constants), List.copyOf(plusTypes), Map.copyOf(selectedFields),
          Map.copyOf(callOwners), Map.copyOf(creationOwners));
    }
  }

  private record CallEvidence(List<String> formalTypes, List<String> actualTypes, boolean varargsDeclaration) {}

  private record ConstructorEvidence(String createdType, String identifierType, List<String> formalTypes, List<String> actualTypes) {}

  private record SelectedField(String owner, String name, Optional<Object> constant) {}

  private record Evidence(Map<String, CallEvidence> calls, Map<String, ConstructorEvidence> constructors, Map<String, Object> constants,
      List<String> plusTypes, Map<String, SelectedField> selectedFields, Map<String, String> callOwners, Map<String, String> creationOwners) {}
}
