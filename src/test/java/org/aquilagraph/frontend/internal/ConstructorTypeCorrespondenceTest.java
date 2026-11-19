package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class ConstructorTypeCorrespondenceTest {
  private static final String RECURSIVE_TYPES = """
      class Recursive<T extends Comparable<T>> {}
      class Different<T extends Comparable<String>> {}
      class Alternative<T extends Comparable<T>> {}
      class Uses { Recursive<?> value; }
      """;

  @Test
  void givenMultiCatchArgumentsWithDifferentAlternatives_whenCorrelated_thenTheirCommonSupertypeDoesNotEstablishAMatch() throws IOException {
    // Arrange
    var source = """
        class Types {
          void collect() {
            try {} catch (IllegalArgumentException | IllegalStateException failure) { Throwable value = failure; }
          }
        }
        """;
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var originalFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        var witnessFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var original = attribute(originalFiles, source);
      var witness = attribute(witnessFiles, source.replace("IllegalStateException", "ArithmeticException"));
      var correspondence = new ConstructorTypeCorrespondence(original.task().getElements(), witness.task().getElements());
      var originalArgument = original.initializer();
      var witnessArgument = witness.initializer();
      var witnessType = witness.trees().getTypeMirror(witnessArgument);

      // Act
      var matched = correspondence.matchesArgument(originalArgument, original.trees(), witnessArgument, witness.trees());

      // Assert
      assertEquals("java.lang.RuntimeException", original.task().getTypes().erasure(original.trees().getTypeMirror(originalArgument)).toString());
      assertEquals("java.lang.RuntimeException", witness.task().getTypes().erasure(witnessType).toString());
      assertFalse(matched);
      assertTrue(correspondence.originalType(witnessType, original.task().getTypes(), witness.task().getTypes()).isEmpty());
    }
  }

  @Test
  void givenSemanticTypeCorrespondence_whenStrictMetadataIsCheckedAfterward_thenSemanticCachingCannotHideAnnotationChanges() throws IOException {
    // Arrange
    var source = "import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface Mark { int value(); } "
        + "class Types { Object read() { Object value = new @Mark(1) Object(); return value; } }";
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var originalFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        var witnessFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var original = attribute(originalFiles, source);
      var witness = attribute(witnessFiles, source.replace("@Mark(1)", "@Mark(2)"));
      var correspondence = new ConstructorTypeCorrespondence(original.task().getElements(), witness.task().getElements());
      var originalType = original.trees().getTypeMirror(original.initializer());
      var witnessType = witness.trees().getTypeMirror(witness.initializer());

      // Act
      var semantic = correspondence.matchesJavaType(originalType, witnessType);
      var strict = correspondence.matches(originalType, witnessType);

      // Assert
      assertTrue(semantic);
      assertFalse(strict);
      assertFalse(correspondence.matches(originalType, witnessType));
    }
  }

  @Test
  void givenDifferentWrittenTypeAnnotations_whenTypesAreCompared_thenMetadataLossRemainsRejected() throws IOException {
    // Arrange
    var source = """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Mark { int value(); }
        class Types { Object read() { Object value = new @Mark(1) Object(); return value; } }
        """;
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var originalFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        var witnessFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var original = attribute(originalFiles, source);
      var witness = attribute(witnessFiles, source.replace("@Mark(1)", "@Mark(2)"));
      var correspondence = new ConstructorTypeCorrespondence(original.task().getElements(), witness.task().getElements());
      var originalType = original.trees().getTypeMirror(original.initializer());
      var witnessType = witness.trees().getTypeMirror(witness.initializer());

      // Act
      var matched = correspondence.matches(originalType, witnessType);
      var restored = correspondence.originalType(witnessType, original.task().getTypes(), witness.task().getTypes());

      // Assert
      assertFalse(matched);
      assertTrue(restored.isEmpty());
      assertEquals("@Mark(1)", originalType.getAnnotationMirrors().getFirst().toString());
      assertEquals("@Mark(2)", witnessType.getAnnotationMirrors().getFirst().toString());
    }
  }

  @Test
  void givenDifferentDeclaredTypes_whenComparedRepeatedly_thenRejectionCannotBecomeAnAcceptedCorrespondence() throws IOException {
    // Arrange
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var originalFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        var witnessFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var original = attribute(originalFiles, "class Types {}");
      var witness = attribute(witnessFiles, "class Types {}");
      var correspondence = new ConstructorTypeCorrespondence(original.task().getElements(), witness.task().getElements());
      var originalString = original.type("java.lang.String");
      var witnessInteger = witness.type("java.lang.Integer");

      // Act
      var first = correspondence.matches(originalString, witnessInteger);
      var repeated = correspondence.matches(originalString, witnessInteger);
      var restored = correspondence.originalType(witnessInteger, original.task().getTypes(), witness.task().getTypes());

      // Assert
      assertFalse(first);
      assertFalse(repeated);
      assertTrue(original.task().getTypes().isSameType(original.type("java.lang.Integer"), restored.orElseThrow()));
    }
  }

  @Test
  void givenRejectedRecursiveBounds_whenCompatibleVariableIsCompared_thenProvisionalBindingsDoNotPreventAcceptance() throws IOException {
    // Arrange
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var originalFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        var witnessFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var original = attribute(originalFiles, RECURSIVE_TYPES);
      var witness = attribute(witnessFiles, RECURSIVE_TYPES);
      var correspondence = new ConstructorTypeCorrespondence(original.task().getElements(), witness.task().getElements());
      var originalVariable = original.variable("Recursive");
      var rejectedVariable = witness.variable("Different");
      var acceptedVariable = witness.variable("Recursive");

      // Act
      var rejected = correspondence.matches(originalVariable, rejectedVariable);
      var rejectedOrigin = correspondence.originalType(rejectedVariable, original.task().getTypes(), witness.task().getTypes());
      var accepted = correspondence.matches(originalVariable, acceptedVariable);
      var restored = correspondence.originalType(acceptedVariable, original.task().getTypes(), witness.task().getTypes());

      // Assert
      assertFalse(rejected);
      assertTrue(rejectedOrigin.isEmpty());
      assertTrue(accepted);
      assertSame(originalVariable, restored.orElseThrow());
    }
  }

  @Test
  void givenAcceptedRecursiveBinding_whenConflictingCandidateIsRetried_thenOriginalBindingRemainsTheOnlyCorrespondence() throws IOException {
    // Arrange
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var originalFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        var witnessFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var original = attribute(originalFiles, RECURSIVE_TYPES);
      var witness = attribute(witnessFiles, RECURSIVE_TYPES);
      var correspondence = new ConstructorTypeCorrespondence(original.task().getElements(), witness.task().getElements());
      var originalVariable = original.variable("Recursive");
      var acceptedVariable = witness.variable("Recursive");
      var conflictingVariable = witness.variable("Alternative");

      // Act
      var accepted = correspondence.matches(originalVariable, acceptedVariable);
      var conflict = correspondence.matches(originalVariable, conflictingVariable);
      var repeatedConflict = correspondence.matches(originalVariable, conflictingVariable);
      var restored = correspondence.originalType(acceptedVariable, original.task().getTypes(), witness.task().getTypes());
      var rejectedOrigin = correspondence.originalType(conflictingVariable, original.task().getTypes(), witness.task().getTypes());

      // Assert
      assertTrue(accepted);
      assertFalse(conflict);
      assertFalse(repeatedConflict);
      assertSame(originalVariable, restored.orElseThrow());
      assertTrue(rejectedOrigin.isEmpty());
    }
  }

  @Test
  void givenRecursiveWildcardCaptures_whenComparedAndRestored_thenAcceptedCaptureIdentitySurvivesRepeatedMatching() throws IOException {
    // Arrange
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var originalFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        var witnessFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var original = attribute(originalFiles, RECURSIVE_TYPES);
      var witness = attribute(witnessFiles, RECURSIVE_TYPES);
      var correspondence = new ConstructorTypeCorrespondence(original.task().getElements(), witness.task().getElements());
      var originalCapture = original.capture();
      var witnessCapture = witness.capture();

      // Act
      var accepted = correspondence.matches(originalCapture, witnessCapture);
      var repeated = correspondence.matches(originalCapture, witnessCapture);
      var restored = correspondence.originalType(witnessCapture, original.task().getTypes(), witness.task().getTypes());

      // Assert
      assertTrue(accepted);
      assertTrue(repeated);
      assertSame(originalCapture, restored.orElseThrow());
    }
  }

  @Test
  void givenMatchingArgumentTextWithDifferentResultTypes_whenArgumentIsRejected_thenNestedDeclarationCopiesAreNotPublished() throws IOException {
    // Arrange
    var source = """
        class Base {}
        class Left extends Base {}
        class Right extends Base {}
        class Argument {
          static Object select(boolean chooseLeft) {
            var value = chooseLeft ? new Left() {} : new Right() {};
            return value;
          }
        }
        """;
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var originalFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
        var witnessFiles = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var original = attribute(originalFiles, source);
      var witness = attribute(witnessFiles, source.replace("Base", "Other"));
      var correspondence = new ConstructorTypeCorrespondence(original.task().getElements(), witness.task().getElements());
      var originalArgument = original.initializer();
      var witnessArgument = witness.initializer();
      var nestedWitness = witness.anonymousType(witnessArgument);

      // Act
      var accepted = correspondence.matchesArgument(originalArgument, original.trees(), witnessArgument, witness.trees());
      var restored = correspondence.originalType(nestedWitness, original.task().getTypes(), witness.task().getTypes());

      // Assert
      assertFalse(accepted);
      assertTrue(restored.isEmpty());
    }
  }

  private static AttributedSource attribute(StandardJavaFileManager files, String text) throws IOException {
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var source = SimpleJavaFileObject.forSource(URI.create("memory:///Types.java"), text);
    var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, files, diagnostics,
        List.of("-proc:none", "--release", "25"), null, List.of(source));
    var unit = task.parse().iterator().next();
    task.analyze();
    assertEquals(List.of(), diagnostics.getDiagnostics());
    return new AttributedSource(task, unit, Trees.instance(task));
  }

  private record AttributedSource(JavacTask task, CompilationUnitTree unit, Trees trees) {
    TypeMirror type(String name) {
      return task.getElements().getTypeElement(name).asType();
    }

    TypeVariable variable(String owner) {
      return (TypeVariable) task.getElements().getTypeElement(owner).getTypeParameters().getFirst().asType();
    }

    TypeVariable capture() {
      var field = task.getElements().getTypeElement("Uses").getEnclosedElements().stream().filter(VariableElement.class::isInstance)
          .map(VariableElement.class::cast).findFirst().orElseThrow();
      return (TypeVariable) ((DeclaredType) task.getTypes().capture(field.asType())).getTypeArguments().getFirst();
    }

    TreePath initializer() {
      var initializers = new ArrayList<TreePath>();
      new TreePathScanner<Void, Void>() {
        @Override
        public Void visitVariable(VariableTree tree, Void ignored) {
          if (tree.getName().contentEquals("value")) {
            initializers.add(new TreePath(getCurrentPath(), tree.getInitializer()));
          }
          return super.visitVariable(tree, ignored);
        }
      }.scan(unit, null);
      return initializers.getFirst();
    }

    TypeMirror anonymousType(TreePath argument) {
      var declarations = new ArrayList<TypeMirror>();
      new TreePathScanner<Void, Void>() {
        @Override
        public Void visitNewClass(NewClassTree tree, Void ignored) {
          declarations.add(trees.getTypeMirror(getCurrentPath()));
          return super.visitNewClass(tree, ignored);
        }
      }.scan(argument, null);
      return declarations.getFirst();
    }
  }
}
