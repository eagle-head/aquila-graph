package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Proxy;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AggregateFixture;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaType;

import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.EvidenceState;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaMethodBodyAdmissionTest {
  private static final String SOURCE = "class Example { Example() {} void action() { {} } void other() { {} } }";

  @TempDir
  Path sourceRoot;

  @Test
  void givenForeignExecutableEntryValues_whenProjectionIsRequested_thenEveryMismatchRejectsBeforeRecursiveEffects() throws IOException {
    // Arrange
    withCompiledSource("entry-values", "class Example { void action() { class Local {} } void other() {} }", fixture -> {
      var body = fixture.body("action");
      var path = body.projection().bodyPath();
      var effects = new AtomicInteger();
      var context = JavaExecutableProjectionContext.canonicalMethod(JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS),
          (_, _, _) -> { effects.incrementAndGet(); throw new AssertionError("Rejected input must not project descendants"); });
      var projector = new JavaExecutableSyntaxProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(), context,
          "JavaMethodBodyTranslator", "method body");
      var foreignSource = SourceFile.fromContent(Path.of("Example.java"), "class Example {}");

      // Act
      var scopeError = assertThrows(IllegalArgumentException.class, () -> projector.project(fixture.unit(), fixture.source(),
          fixture.body("other").methodKey(), path.getLeaf(), path, JavaSyntaxStructuralPath.executionRoot(path), false, false));
      var sourceError = assertThrows(IllegalArgumentException.class, () -> projector.project(fixture.unit(), foreignSource,
          body.methodKey(), path.getLeaf(), path, JavaSyntaxStructuralPath.executionRoot(path), false, false));
      var rootError = assertThrows(IllegalArgumentException.class, () -> projector.project(fixture.unit(), fixture.source(),
          body.methodKey(), path.getLeaf(), path, SyntaxStructuralPath.sourceFileRoot(), false, false));
      var flagError = assertThrows(IllegalArgumentException.class, () -> projector.project(fixture.unit(), fixture.source(),
          body.methodKey(), path.getLeaf(), path, JavaSyntaxStructuralPath.executionRoot(path), true, false));

      // Assert
      assertTrue(scopeError.getMessage().contains("method owner"));
      assertTrue(sourceError.getMessage().contains("captured source"));
      assertTrue(rootError.getMessage().contains("structural root"));
      assertTrue(flagError.getMessage().contains("method owner"));
      assertEquals(0, effects.get());
      assertTrue(Arrays.stream(JavaExecutableSyntaxProjector.Projection.class.getDeclaredConstructors())
          .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
      assertEquals(1, Arrays.stream(JavaBodyDecisionProducer.class.getDeclaredMethods()).filter(method -> method.getName().equals("produce")).count());
    });
  }

  @Test
  void givenControlledErroneousOrMissingOperands_whenEachExecutablePurposeProducesDecisions_thenExactLocalEvidenceRemainsAssociated()
      throws IOException {
    // Arrange
    var sources = List.of(
        "class Example { String action() { return \"m\" + 1; } String field = \"f\" + 2; static { String local = \"s\" + 3; } { String local = \"i\" + 4; } }",
        "enum Example { FIRST(\"a\" + 1, \"b\" + 2); Example(String first, String second) {} }");
    for (var index = 0; index < sources.size(); index++) {
      withCompiledSource("operand-evidence-" + index, sources.get(index), fixture -> {
        for (var state : List.of(EvidenceState.ERRONEOUS, EvidenceState.MISSING)) {
          var errorType = (TypeMirror) Proxy.newProxyInstance(TypeMirror.class.getClassLoader(), new Class<?>[] {TypeMirror.class},
              (_, method, _) -> { if (method.getName().equals("getKind")) { return TypeKind.ERROR; } throw new AssertionError(method); });
          var controlledTrees = new ProjectionTrees(fixture.trees()) {
            @Override public TypeMirror getTypeMirror(TreePath path) {
              return path.getLeaf() instanceof LiteralTree literal && literal.getValue() instanceof Integer
                  ? state == EvidenceState.ERRONEOUS ? errorType : null : super.getTypeMirror(path);
            }
          };
          var semantics = new SemanticResolver(controlledTrees, fixture.task().getTypes(), fixture.task().getElements(),
              new DeclarationIdentityRegistry(controlledTrees, fixture.task().getTypes(), fixture.task().getElements(), JavaTranslationPolicy.legacyAnalysis()));
          var inputs = executableInputs(fixture, semantics);
          for (var input : inputs) {
            var projector = new JavaExecutableSyntaxProjector(semantics, fixture.evidence(), fixture.inventory(), input.context(),
                "JavaAstContributionTranslator", "declaration-initialization");

            // Act
            var projection = projector.project(fixture.unit(), fixture.source(), input.scope(), input.path().getLeaf(), input.path(),
                JavaSyntaxStructuralPath.executionRoot(input.path()), false, false);
            var decisions = new JavaBodyDecisionProducer(semantics).produce(projection).value();

            // Assert
            assertEquals(1, decisions.concatenations().size());
            var concatenation = decisions.concatenations().getFirst();
            assertEquals(input.scope(), concatenation.scopeKey());
            assertEquals(Optional.of(true), concatenation.leftIsString().knownValue());
            assertEquals(state, concatenation.rightIsString().state());
            var operand = projection.translation().value().descendantsAndSelf().stream()
                .filter(node -> node.sourceKind().name().equals("INT_LITERAL")).findFirst().orElseThrow();
            if (state == EvidenceState.ERRONEOUS) {
              var diagnostic = concatenation.rightIsString().diagnostic().orElseThrow();
              assertSame(projection.erroneousType(operand).orElseThrow(), diagnostic);
              assertEquals(operand.origin().stream().toList(), diagnostic.origins());
              assertTrue(projection.translation().emissions().events().stream().anyMatch(event -> event.diagnostic() == diagnostic));
              assertEquals("frontend.error-type", diagnostic.code());
            } else {
              assertTrue(concatenation.rightIsString().diagnostic().isEmpty());
              assertTrue(projection.erroneousType(operand).isEmpty());
            }
            assertThrows(UnsupportedOperationException.class, () -> projection.nodes().clear());
            assertThrows(UnsupportedOperationException.class, () -> projection.translation().emissions().events().clear());
            assertThrows(IllegalArgumentException.class, () -> new JavaBodyDecisionProducer(fixture.semantics()).produce(projection));
          }
        }
      });
    }
  }

  private static List<ExecutableInput> executableInputs(Fixture fixture, SemanticResolver semantics) {
    var result = new ArrayList<ExecutableInput>();
    var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
    var policy = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
    JavaExecutableProjectionContext.DeclarationProjection nested = (_, _, _) -> { throw new AssertionError("No nested declaration"); };
    for (var member : type.getMembers()) {
      var path = TreePath.getPath(fixture.unit(), member);
      if (member instanceof MethodTree method && method.getName().contentEquals("action")) {
        result.add(new ExecutableInput(new TreePath(path, method.getBody()), semantics.declarationKey(semantics.element(path)).orElseThrow(),
            JavaExecutableProjectionContext.canonicalMethod(policy, nested)));
      } else if (member instanceof VariableTree field && field.getInitializer() != null) {
        var key = semantics.declarationKey(semantics.element(path)).orElseThrow();
        if (field.getInitializer() instanceof NewClassTree creation) {
          var creationPath = new TreePath(path, creation);
          for (var argument : creation.getArguments()) {
            result.add(new ExecutableInput(new TreePath(creationPath, argument), key, JavaExecutableProjectionContext.enumInitialization(policy, nested)));
          }
        } else {
          result.add(new ExecutableInput(new TreePath(path, field.getInitializer()), key, JavaExecutableProjectionContext.memberInitialization(policy, nested)));
        }
      } else if (member instanceof BlockTree block) {
        var origin = fixture.evidence().origin(fixture.unit(), fixture.source(), block).orElseThrow();
        var key = semantics.declarationKey(semantics.element(path.getParentPath())).orElseThrow() + ":initializer:"
            + origin.startOffset() + ':' + origin.endOffsetExclusive();
        result.add(new ExecutableInput(path, key, JavaExecutableProjectionContext.memberInitialization(policy, nested)));
      }
    }
    assertEquals(type.getKind() == Tree.Kind.ENUM ? 2 : 4, result.size());
    return List.copyOf(result);
  }

  private record ExecutableInput(TreePath path, String scope, JavaExecutableProjectionContext context) {}

  @Test
  void givenInitializationScopesWithRecursiveWork_whenWrongOwnerOrPurposeIsSupplied_thenRefusalPrecedesEveryCallback() throws IOException {
    // Arrange
    var sources = List.of(
        "class Example { Object field = new Object() {}; static { Object local = new Object() {}; } { Object local = new Object() {}; } }",
        "enum Example { FIRST(new Object() {}, new Object() {}); Example(Object first, Object second) {} }");
    for (var index = 0; index < sources.size(); index++) {
      withCompiledSource("initialization-entry-" + index, sources.get(index), fixture -> {
        var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
        var policy = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_UNITS);
        var effects = new AtomicInteger();
        var failure = new IllegalStateException("Recursive projection reached");
        JavaExecutableProjectionContext.DeclarationProjection nested = (_, _, _) -> { effects.incrementAndGet(); throw failure; };
        var inputs = new ArrayList<ExecutableInput>();
        for (var member : type.getMembers()) {
          var path = TreePath.getPath(fixture.unit(), member);
          if (member instanceof VariableTree field && field.getInitializer() != null) {
            var key = fixture.semantics().declarationKey(fixture.semantics().element(path)).orElseThrow();
            var initializerPath = new TreePath(path, field.getInitializer());
            if (type.getKind() == Tree.Kind.ENUM && field.getInitializer() instanceof NewClassTree creation) {
              for (var argument : creation.getArguments()) {
                inputs.add(new ExecutableInput(new TreePath(initializerPath, argument), key,
                    JavaExecutableProjectionContext.enumInitialization(policy, nested)));
              }
            } else {
              inputs.add(new ExecutableInput(initializerPath, key, JavaExecutableProjectionContext.memberInitialization(policy, nested)));
            }
          } else if (member instanceof BlockTree block) {
            var range = fixture.evidence().origin(fixture.unit(), fixture.source(), block).orElseThrow();
            var key = fixture.semantics().declarationKey(fixture.semantics().element(path.getParentPath())).orElseThrow()
                + ":initializer:" + range.startOffset() + ':' + range.endOffsetExclusive();
            inputs.add(new ExecutableInput(path, key, JavaExecutableProjectionContext.memberInitialization(policy, nested)));
          }
        }
        assertEquals(type.getKind() == Tree.Kind.ENUM ? 2 : 3, inputs.size());
        for (var input : inputs) {
          var projector = new JavaExecutableSyntaxProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(), input.context(),
              "JavaAstContributionTranslator", "declaration-initialization");
          var wrongPurpose = new JavaExecutableSyntaxProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(),
              JavaExecutableProjectionContext.canonicalMethod(policy, nested), "JavaMethodBodyTranslator", "method body");
          var before = effects.get();

          // Act
          assertThrows(IllegalArgumentException.class, () -> projector.project(fixture.unit(), fixture.source(), "type:Foreign",
              input.path().getLeaf(), input.path(), JavaSyntaxStructuralPath.executionRoot(input.path()), false, false));
          assertThrows(IllegalArgumentException.class, () -> wrongPurpose.project(fixture.unit(), fixture.source(), input.scope(),
              input.path().getLeaf(), input.path(), JavaSyntaxStructuralPath.executionRoot(input.path()), false, false));
          var afterRefusals = effects.get();
          var recursiveFailure = assertThrows(IllegalStateException.class, () -> projector.project(fixture.unit(), fixture.source(), input.scope(),
              input.path().getLeaf(), input.path(), JavaSyntaxStructuralPath.executionRoot(input.path()), false, false));

          // Assert
          assertEquals(before, afterRefusals);
          assertSame(failure, recursiveFailure);
          assertEquals(before + 1, effects.get());
        }
      });
    }
  }

  @Test
  void givenRealFieldArrayInitializer_whenProjectedWithoutMethodRegistration_thenOrderedValuesAndExactCompilerPathsAreRetained()
      throws IOException {
    // Arrange
    withCompiledSource("field-expression", "class Example { int[] values = new int[] {1, 2}; }", fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var field = type.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast).findFirst().orElseThrow();
      var fieldPath = TreePath.getPath(fixture.unit(), field);
      var initializer = field.getInitializer();
      var path = new TreePath(fieldPath, initializer);
      var structuralPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
          .child(SyntaxRole.MEMBER, type.getMembers().indexOf(field)).child(SyntaxRole.INITIALIZER, 0);
      var projector = new JavaExecutableSyntaxProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaExecutableProjectionContext.memberInitialization(JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_INITIALIZATION)),
          "JavaAstContributionTranslator", "field initializer");

      // Act
      var projection = projector.project(fixture.unit(), fixture.source(), fixture.semantics().declarationKey(fixture.semantics().element(fieldPath)).orElseThrow(),
          initializer, path, structuralPath, false, false);
      var root = projection.translation().value();

      // Assert
      assertEquals(List.of(), projection.translation().diagnostics());
      assertEquals(SyntaxKind.ARRAY_CREATION, root.syntaxKind());
      assertEquals(structuralPath, root.structuralPath());
      assertEquals(List.of("1", "2"), root.children(SyntaxRole.ARRAY_ELEMENT).stream().map(node -> node.value(SyntaxValue.Literal.class).text()).toList());
      assertSame(root, projection.nodes().getLast().node());
      assertSame(initializer, projection.nodes().getLast().compilerAnchor().getLeaf());
      assertEquals(root.descendantsAndSelf().size(), projection.nodes().size());
      assertThrows(UnsupportedOperationException.class, () -> projection.nodes().clear());
      assertThrows(UnsupportedOperationException.class, () -> root.children().clear());
    });
  }

  @Test
  void givenRealInitializerBlock_whenProjectedWithInitializerScope_thenLocalBindingAndUpdateRemainInsideTheWrittenBlock() throws IOException {
    // Arrange
    withCompiledSource("initializer-statements", "class Example { { int value = 1; value += 2; } }", fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var block = type.getMembers().stream().filter(BlockTree.class::isInstance).map(BlockTree.class::cast).findFirst().orElseThrow();
      var typePath = TreePath.getPath(fixture.unit(), type);
      var path = new TreePath(typePath, block);
      var structuralPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
          .child(SyntaxRole.MEMBER, type.getMembers().indexOf(block));
      var projector = new JavaExecutableSyntaxProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaExecutableProjectionContext.memberInitialization(JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_INITIALIZATION)),
          "JavaAstContributionTranslator", "initializer block");
      var origin = fixture.evidence().origin(fixture.unit(), fixture.source(), block).orElseThrow();
      var key = fixture.semantics().declarationKey(fixture.semantics().element(typePath)).orElseThrow()
          + ":initializer:" + origin.startOffset() + ':' + origin.endOffsetExclusive();

      // Act
      var projection = projector.project(fixture.unit(), fixture.source(), key,
          block, path, structuralPath, false, false);
      var root = projection.translation().value();

      // Assert
      assertEquals(List.of(), projection.translation().diagnostics());
      assertEquals(List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.EXPRESSION_STATEMENT), root.children(SyntaxRole.STATEMENT).stream().map(SyntaxNode::syntaxKind).toList());
      assertEquals("value", root.children(SyntaxRole.STATEMENT).getFirst().value(SyntaxValue.LocalDeclaration.class).name());
      assertEquals(SyntaxKind.COMPOUND_ASSIGNMENT, root.children(SyntaxRole.STATEMENT).getLast().requiredChild(SyntaxRole.EXPRESSION).syntaxKind());
      assertSame(block, projection.nodes().getLast().compilerAnchor().getLeaf());
      assertEquals(root.descendantsAndSelf().size(), root.descendantsAndSelf().stream().map(SyntaxNode::nodeId).distinct().count());
    });
  }

  @Test
  void givenForeignCompilerPath_whenExecutableSyntaxIsProjected_thenMismatchedTreeIsRejected() throws IOException {
    // Arrange
    withCompiledSource("projection-path", fixture -> {
      var original = fixture.body("action");
      var other = fixture.body("other");
      var projector = new JavaExecutableSyntaxProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()),
          "JavaMethodBodyTranslator", "method body");

      // Act
      var error = assertThrows(IllegalArgumentException.class, () -> projector.project(fixture.unit(), fixture.source(), original.methodKey(),
          original.projection().declaration().getBody(), other.projection().bodyPath(), original.root().structuralPath(), false, false));

      // Assert
      assertTrue(error.getMessage().contains("exact compiler tree and compilation-unit path"));
    });
  }

  @Test
  void givenRealAttributedBodies_whenContributedAndSealed_thenSourceAndAuxiliaryProofsRemainDistinct() throws IOException {
    // Arrange
    withCompiledSource("valid", fixture -> {
      var action = fixture.body("action");
      var constructor = fixture.body("<init>");

      // Act
      var contribution = fixture.contribute(fixture.translated().methodBodies(), fixture.semantics());
      var builder = AggregateFixture.builder(fixture.snapshot()).add(contribution.contribution().orElseThrow());
      contribution.bodies().forEach(body -> builder.addMethodBody(body.methodKey(), body.root(), body.origin()));
      var aggregate = builder.seal();

      // Assert
      assertSame(action.root(), action.projection().evidence().translation().value());
      assertSame(action.projection().declaration().getBody(), action.projection().bodyPath().getLeaf());
      assertSame(action.root(), aggregate.methodSyntax(action.methodKey()).orElseThrow().root());
      assertSame(action.root(), aggregate.node(action.root().nodeId()).orElseThrow());
      assertEquals(MethodBodyOrigin.SOURCE_AST, aggregate.methodBodyOrigin(action.methodKey()).orElseThrow());
      assertEquals(MethodBodyOrigin.SOURCE_AST, aggregate.methodBodyOrigin(constructor.methodKey()).orElseThrow());
      var sourceConstructor = aggregate.nodes().stream()
          .filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration declaration
              && declaration.declarationKey().equals(constructor.methodKey()))
          .findFirst().orElseThrow().requiredChild(SyntaxRole.BODY);
      assertNotSame(constructor.root(), sourceConstructor);
      assertSame(sourceConstructor, aggregate.methodSyntax(constructor.methodKey()).orElseThrow().root());
      Set<SyntaxNode> sourceInstances = Collections.newSetFromMap(new IdentityHashMap<>());
      sourceInstances.addAll(aggregate.nodes());
      assertTrue(constructor.root().descendantsAndSelf().stream().noneMatch(sourceInstances::contains));
    });
  }

  @Test
  void givenSwappedRootWithOriginalProjection_whenContributed_thenProducedInstanceMismatchIsRejected() throws IOException {
    // Arrange
    withCompiledSource("swapped-root", fixture -> {
      var original = fixture.body("action");
      var root = original.root();
      var replacement = SyntaxNode.source(root.sourceFileKey(), root.structuralPath(), root.sourceKind(), root.syntaxKind(), root.type(),
          root.value(), root.children(), root.semanticReference(), root.origin().orElseThrow());
      var swapped = new TranslatedMethodBody(original.methodKey(), replacement, original.projection());

      // Act
      var error = assertThrows(IllegalArgumentException.class, () -> fixture.contribute(fixture.replace(original, swapped), fixture.semantics()));

      // Assert
      assertEquals(root, replacement);
      assertNotSame(root, replacement);
      assertTrue(error.getMessage().contains("does not retain its executable body projection"));
    });
  }

  @Test
  void givenBodyPathFromAnotherMethod_whenProduced_thenExactExecutableBodyPathIsRejected() throws IOException {
    // Arrange
    withCompiledSource("swapped-path", fixture -> {
      var original = fixture.body("action");
      var other = fixture.body("other");
      var translator = new JavaMethodBodyTranslator(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));

      // Act
      var error = assertThrows(IllegalArgumentException.class,
          () -> translator.translate(fixture.unit(), fixture.source(), original.methodKey(), original.projection().declaration().getBody(),
              other.projection().bodyPath(), false, false));

      // Assert
      assertTrue(error.getMessage().contains("requires its attributed executable and exact body path"));
    });
  }

  @Test
  void givenAnotherCanonicalKeyOnBody_whenContributed_thenAttributedExecutableCorrespondenceIsRejected() throws IOException {
    // Arrange
    withCompiledSource("swapped-key", fixture -> {
      var original = fixture.body("action");
      var swapped = new TranslatedMethodBody(fixture.body("other").methodKey(), original.root(), original.projection());

      // Act
      var error = assertThrows(IllegalArgumentException.class, () -> fixture.contribute(fixture.replace(original, swapped), fixture.semantics()));

      // Assert
      assertTrue(error.getMessage().contains("does not match its canonical executable"));
    });
  }

  @Test
  void givenSameKeyFromAnotherCompilerExecutable_whenContributed_thenSourceTreeIdentityIsRejected() throws IOException {
    // Arrange
    withCompiledSource("first-executable", first -> withCompiledSource("second-executable", second -> {
      var original = first.body("action");
      var foreign = second.body("action");
      var swapped = new TranslatedMethodBody(original.methodKey(), original.root(), foreign.projection());

      // Act
      var error = assertThrows(IllegalArgumentException.class, () -> first.contribute(first.replace(original, swapped), first.semantics()));

      // Assert
      assertEquals(original.methodKey(), foreign.methodKey());
      assertNotSame(original.projection().declaration(), foreign.projection().declaration());
      assertTrue(error.getMessage().contains("no independently attributed source executable"));
    }));
  }

  @Test
  void givenCanonicalMethodMissingFromContribution_whenCorrelated_thenBodyIsRejected() throws IOException {
    // Arrange
    withCompiledSource("missing-method", fixture -> {
      var original = fixture.translated().types().getFirst();
      var missingMethod = new JavaType(original.typeKey(), original.qualifiedName(), original.kind(), original.modifiers(),
          original.enclosingType(), original.directSuperclass(), original.directInterfaces(), original.permittedSubclasses(),
          original.typeParameters(), original.sourceDeclared(), original.fields(), original.recordComponents(), List.of(),
          original.synthesizedDeclarations(), original.origins());
      var contributor = new UnitSourceFixture(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaTranslationPolicy.legacyAnalysis());

      // Act
      var error = assertThrows(IllegalArgumentException.class,
          () -> contributor.translate(fixture.unit(), fixture.source(), fixture.translated().methodBodies(), List.of(missingMethod)));

      // Assert
      assertTrue(error.getMessage().contains("does not match its canonical executable"));
    });
  }

  @Test
  void givenSameKeyMethodWithChangedSignature_whenContributed_thenCompleteDeclarationMismatchIsRejected() throws IOException {
    assertChangedDeclaration("changed-signature", "action(long)", "void");
  }

  @Test
  void givenSameKeyMethodWithChangedReturnType_whenContributed_thenCompleteDeclarationMismatchIsRejected() throws IOException {
    assertChangedDeclaration("changed-return-type", "action()", "int");
  }

  private void assertChangedDeclaration(String directory, String signature, String returnType) throws IOException {
    // Arrange
    withCompiledSource(directory, fixture -> {
      var type = fixture.translated().types().getFirst();
      var original = type.methods().stream().filter(method -> method.name().equals("action")).findFirst().orElseThrow();
      var changed = new JavaMethod(original.methodKey(), original.ownerTypeKey(), original.name(), signature, returnType,
          original.parameters(), original.modifiers(), original.constructor(), original.sourceDeclared(), original.hasBody(),
          original.origins());
      var changedType = new JavaType(type.typeKey(), type.qualifiedName(), type.kind(), type.modifiers(), type.enclosingType(),
          type.directSuperclass(), type.directInterfaces(), type.permittedSubclasses(), type.typeParameters(), type.sourceDeclared(),
          type.fields(), type.recordComponents(), type.methods().stream().map(method -> method.equals(original) ? changed : method).toList(),
          type.synthesizedDeclarations(), type.origins());
      var contributor = new UnitSourceFixture(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaTranslationPolicy.legacyAnalysis());

      // Act
      var error = assertThrows(IllegalArgumentException.class,
          () -> contributor.translate(fixture.unit(), fixture.source(), fixture.translated().methodBodies(), List.of(changedType)));

      // Assert
      assertEquals(original.methodKey(), changed.methodKey());
      assertTrue(error.getMessage().contains("canonical executable"));
    });
  }

  @Test
  void givenUnavailableAttributedOwner_whenContributed_thenIndependentOwnerProofIsRejected() throws IOException {
    // Arrange
    withCompiledSource("missing-owner", fixture -> {
      var unavailable = new ProjectionTrees(fixture.trees(), (ClassTree) fixture.unit().getTypeDecls().getFirst());
      var semantics = new SemanticResolver(unavailable, fixture.task().getTypes(), fixture.task().getElements(),
          new DeclarationIdentityRegistry(unavailable, fixture.task().getTypes(), fixture.task().getElements(), JavaTranslationPolicy.legacyAnalysis()));

      // Act
      var error = assertThrows(IllegalArgumentException.class, () -> fixture.contribute(fixture.translated().methodBodies(), semantics));

      // Assert
      assertTrue(error.getMessage().contains("no independently attributed source owner"));
    });
  }

  @Test
  void givenBodyAndKeyFromDifferentExecutables_whenProduced_thenMismatchedCompilerProjectionIsRejected() throws IOException {
    // Arrange
    withCompiledSource("producer-key", fixture -> {
      var action = fixture.body("action");
      var other = fixture.body("other");
      var translator = new JavaMethodBodyTranslator(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis()));

      // Act
      var error = assertThrows(IllegalArgumentException.class,
          () -> translator.translate(fixture.unit(), fixture.source(), action.methodKey(), other.projection().declaration().getBody(),
              other.projection().bodyPath(), false, false));

      // Assert
      assertTrue(error.getMessage().contains("requires its attributed executable and exact body path"));
    });
  }

  @Test
  void givenRealBodylessMethod_whenTranslatedAndContributed_thenNoEmptyBodyIsInvented() throws IOException {
    // Arrange
    withCompiledSource("bodyless", "interface Example { void action(); }", fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var methodTree = (MethodTree) type.getMembers().getFirst();
      var translator = new JavaMethodTranslator(fixture.semantics(), fixture.evidence(), fixture.inventory(),
          new JavaMethodBodyTranslator(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis())));

      // Act
      var method = translator.translate(fixture.unit(), new TreePath(new TreePath(fixture.unit()), type), fixture.source(),
          "type:Example", methodTree).value();
      var contribution = fixture.contribute(fixture.translated().methodBodies(), fixture.semantics());
      var aggregate = AggregateFixture.builder(fixture.snapshot()).add(contribution.contribution().orElseThrow()).seal();

      // Assert
      assertFalse(method.method().hasBody());
      assertTrue(method.body().isEmpty());
      assertTrue(fixture.translated().methodBodies().isEmpty());
      assertTrue(aggregate.methodSyntax(method.method().methodKey()).isEmpty());
      assertTrue(aggregate.methodBodyOrigin(method.method().methodKey()).isEmpty());
      assertTrue(aggregate.methodBodyKeys().isEmpty());
    });
  }

  @Test
  void givenJava25ImplicitAndGeneratedExecutables_whenContributed_thenEveryRetainedBodyHasLiveCompilerCorrelation() throws IOException {
    // Arrange
    var sources = List.of("void main() {}", "record Example(int value) {}", "enum Example { FIRST }");
    var directories = List.of("compact-unit", "record-declaration", "enum-declaration");
    for (var index = 0; index < sources.size(); index++) {
      withCompiledSource(directories.get(index), sources.get(index), fixture -> {
        var constructor = fixture.body("<init>");

        // Act
        var contribution = fixture.contribute(fixture.translated().methodBodies(), fixture.semantics());
        var builder = AggregateFixture.builder(fixture.snapshot()).add(contribution.contribution().orElseThrow());
        contribution.bodies().forEach(body -> builder.addMethodBody(body.methodKey(), body.root(), body.origin()));
        var aggregate = builder.seal();

        // Assert
        assertEquals(MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, aggregate.methodBodyOrigin(constructor.methodKey()).orElseThrow());
        for (var body : fixture.translated().methodBodies()) {
          var path = TreePath.getPath(fixture.unit(), body.projection().declaration());
          var executable = fixture.semantics().element(path);
          assertSame(body.projection().declaration(), fixture.semantics().sourceTree(executable));
          assertSame(body.projection().declaration().getBody(), body.projection().bodyPath().getLeaf());
          assertEquals(fixture.semantics().declarationKey(executable).orElseThrow(), aggregate.methodSyntax(body.methodKey()).orElseThrow().methodKey());
          var retained = aggregate.methodSyntax(body.methodKey()).orElseThrow().root();
          if (aggregate.methodBodyOrigin(body.methodKey()).orElseThrow() == MethodBodyOrigin.SOURCE_AST) {
            assertSame(aggregate.node(retained.nodeId()).orElseThrow(), retained);
            assertEquals(body.root().origin(), retained.origin());
            assertEquals(body.root().sourceFileKey(), retained.sourceFileKey());
          } else {
            assertSame(body.root(), retained);
          }
        }
      });
    }
  }

  private void withCompiledSource(String directory, FixtureAction action) throws IOException {
    withCompiledSource(directory, SOURCE, action);
  }

  private void withCompiledSource(String directory, String sourceText, FixtureAction action) throws IOException {
    var root = Files.createDirectories(sourceRoot.resolve(directory));
    var file = root.resolve("Example.java");
    Files.writeString(file, sourceText);
    var snapshot = new JavaSourceLoader().discover(root);
    var source = snapshot.sourceFiles().getFirst();
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none", "--release", "25"), null,
          manager.getJavaFileObjects(file));
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      var unit = units.getFirst();
      var trees = Trees.instance(task);
      var evidence = new JavaSourceEvidence(trees.getSourcePositions());
      var inventory = ParsedTreeInventory.capture(units, ignored -> Optional.of(source), evidence);
      task.analyze();
      assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      var semantics = new SemanticResolver(trees, task.getTypes(), task.getElements(), new DeclarationIdentityRegistry(trees, task.getTypes(), task.getElements(), JavaTranslationPolicy.legacyAnalysis()));
      var methodTranslator = new JavaMethodTranslator(semantics, evidence, inventory, new JavaMethodBodyTranslator(semantics, evidence, inventory, JavaExecutableProjectionContext.metadata(JavaTranslationPolicy.legacyAnalysis())));
      var typeTranslator = new JavaTypeTranslator(semantics, evidence, inventory, methodTranslator, JavaTranslationPolicy.legacyAnalysis());
      var translated = typeTranslator.translate(unit, new TreePath(unit), source, (ClassTree) unit.getTypeDecls().getFirst())
          .value().orElseThrow();
      action.accept(new Fixture(snapshot, source, unit, task, trees, semantics, evidence, inventory, translated));
    }
  }

  @FunctionalInterface
  private interface FixtureAction {
    void accept(Fixture fixture) throws IOException;
  }

  private record Fixture(InputSnapshot snapshot, SourceFile source, CompilationUnitTree unit, JavacTask task, Trees trees,
      SemanticResolver semantics, JavaSourceEvidence evidence, ParsedTreeInventory inventory, TranslatedType translated) {
    TranslatedMethodBody body(String name) {
      var key = translated.types().getFirst().methods().stream().filter(method -> method.name().equals(name)).findFirst().orElseThrow().methodKey();
      return translated.methodBodies().stream().filter(body -> body.methodKey().equals(key)).findFirst().orElseThrow();
    }

    List<TranslatedMethodBody> replace(TranslatedMethodBody original, TranslatedMethodBody replacement) {
      return translated.methodBodies().stream().map(body -> body == original ? replacement : body).toList();
    }

    FinalizedUnit contribute(List<TranslatedMethodBody> bodies, SemanticResolver resolver) {
      return new UnitSourceFixture(resolver, evidence, inventory, JavaTranslationPolicy.legacyAnalysis()).translate(unit, source, bodies, translated.types());
    }
  }

  private static class ProjectionTrees extends Trees {
    private final Trees delegate;
    private final Optional<ClassTree> owner;

    private ProjectionTrees(Trees delegate, ClassTree owner) {
      this.delegate = delegate;
      this.owner = Optional.of(owner);
    }

    private ProjectionTrees(Trees delegate) {
      this.delegate = delegate;
      this.owner = Optional.empty();
    }

    @Override public SourcePositions getSourcePositions() { return delegate.getSourcePositions(); }
    @Override public Tree getTree(Element element) { return delegate.getTree(element); }
    @Override public ClassTree getTree(TypeElement element) { return delegate.getTree(element); }
    @Override public MethodTree getTree(ExecutableElement element) { return delegate.getTree(element); }
    @Override public Tree getTree(Element element, AnnotationMirror mirror) { return delegate.getTree(element, mirror); }
    @Override public Tree getTree(Element element, AnnotationMirror mirror, AnnotationValue value) { return delegate.getTree(element, mirror, value); }
    @Override public TreePath getPath(CompilationUnitTree unit, Tree tree) { return delegate.getPath(unit, tree); }
    @Override public TreePath getPath(Element element) { return delegate.getPath(element); }
    @Override public TreePath getPath(Element element, AnnotationMirror mirror) { return delegate.getPath(element, mirror); }
    @Override public TreePath getPath(Element element, AnnotationMirror mirror, AnnotationValue value) { return delegate.getPath(element, mirror, value); }
    @Override public Element getElement(TreePath path) { return owner.filter(value -> path.getLeaf() == value).isPresent() ? null : delegate.getElement(path); }
    @Override public TypeMirror getTypeMirror(TreePath path) { return delegate.getTypeMirror(path); }
    @Override public Scope getScope(TreePath path) { return delegate.getScope(path); }
    @Override public String getDocComment(TreePath path) { return delegate.getDocComment(path); }
    @Override public boolean isAccessible(Scope scope, TypeElement type) { return delegate.isAccessible(scope, type); }
    @Override public boolean isAccessible(Scope scope, Element element, DeclaredType type) { return delegate.isAccessible(scope, element, type); }
    @Override public TypeMirror getOriginalType(ErrorType type) { return delegate.getOriginalType(type); }
    @Override public void printMessage(Diagnostic.Kind kind, CharSequence message, Tree tree, CompilationUnitTree unit) { delegate.printMessage(kind, message, tree, unit); }
    @Override public TypeMirror getLub(CatchTree tree) { return delegate.getLub(tree); }
  }
}
