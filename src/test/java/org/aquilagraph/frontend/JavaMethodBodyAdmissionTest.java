package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CatchTree;
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
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AggregateFixture;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaType;

import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaMethodBodyAdmissionTest {
  private static final String SOURCE = "class Example { Example() {} void action() { {} } void other() { {} } }";

  @TempDir
  Path sourceRoot;

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
      var projector = new JavaExecutableSyntaxProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(),
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
      assertSame(initializer, projection.nodes().getLast().path().getLeaf());
      assertEquals(root.descendantsAndSelf().size(), projection.nodes().size());
      assertThrows(UnsupportedOperationException.class, () -> projection.nodes().clear());
      assertThrows(UnsupportedOperationException.class, () -> root.children().clear());
    });
  }

  @Test
  void givenRealInitializerBlock_whenProjectedWithTypeOwner_thenLocalBindingAndUpdateRemainInsideTheWrittenBlock() throws IOException {
    // Arrange
    withCompiledSource("initializer-statements", "class Example { { int value = 1; value += 2; } }", fixture -> {
      var type = (ClassTree) fixture.unit().getTypeDecls().getFirst();
      var block = type.getMembers().stream().filter(BlockTree.class::isInstance).map(BlockTree.class::cast).findFirst().orElseThrow();
      var typePath = TreePath.getPath(fixture.unit(), type);
      var path = new TreePath(typePath, block);
      var structuralPath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0)
          .child(SyntaxRole.MEMBER, type.getMembers().indexOf(block));
      var projector = new JavaExecutableSyntaxProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(),
          "JavaAstContributionTranslator", "initializer block");

      // Act
      var projection = projector.project(fixture.unit(), fixture.source(), fixture.semantics().declarationKey(fixture.semantics().element(typePath)).orElseThrow(),
          block, path, structuralPath, false, false);
      var root = projection.translation().value();

      // Assert
      assertEquals(List.of(), projection.translation().diagnostics());
      assertEquals(List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.EXPRESSION_STATEMENT), root.children(SyntaxRole.STATEMENT).stream().map(SyntaxNode::syntaxKind).toList());
      assertEquals("value", root.children(SyntaxRole.STATEMENT).getFirst().value(SyntaxValue.LocalDeclaration.class).name());
      assertEquals(SyntaxKind.COMPOUND_ASSIGNMENT, root.children(SyntaxRole.STATEMENT).getLast().requiredChild(SyntaxRole.EXPRESSION).syntaxKind());
      assertSame(block, projection.nodes().getLast().path().getLeaf());
      assertEquals(root.descendantsAndSelf().size(), root.descendantsAndSelf().stream().map(SyntaxNode::nodeId).distinct().count());
    });
  }

  @Test
  void givenForeignCompilerPath_whenExecutableSyntaxIsProjected_thenMismatchedTreeIsRejected() throws IOException {
    // Arrange
    withCompiledSource("projection-path", fixture -> {
      var original = fixture.body("action");
      var other = fixture.body("other");
      var projector = new JavaExecutableSyntaxProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(),
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
      assertSame(action.root(), action.projection().producedRoot());
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
      var translator = new JavaMethodBodyTranslator(fixture.semantics(), fixture.evidence(), fixture.inventory());

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
      var contributor = new JavaDeclarationProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaTranslationPolicy.legacyAnalysis());

      // Act
      var error = assertThrows(IllegalArgumentException.class,
          () -> contributor.translate(fixture.unit(), fixture.source(), fixture.translated().methodBodies(), List.of()));

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
      var contributor = new JavaDeclarationProjector(fixture.semantics(), fixture.evidence(), fixture.inventory(), JavaTranslationPolicy.legacyAnalysis());

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
      var unavailable = new MissingOwnerTrees(fixture.trees(), (ClassTree) fixture.unit().getTypeDecls().getFirst());
      var semantics = new SemanticResolver(unavailable, fixture.task().getTypes(), fixture.task().getElements(),
          new DeclarationIdentityRegistry(unavailable, fixture.task().getTypes()));

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
      var translator = new JavaMethodBodyTranslator(fixture.semantics(), fixture.evidence(), fixture.inventory());

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
          new JavaMethodBodyTranslator(fixture.semantics(), fixture.evidence(), fixture.inventory()));

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
      var semantics = new SemanticResolver(trees, task.getTypes(), task.getElements(), new DeclarationIdentityRegistry(trees, task.getTypes()));
      var coverage = JavaLanguageFeatureCoverage.fromConfiguration(CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
      var methodTranslator = new JavaMethodTranslator(semantics, evidence, inventory, new JavaMethodBodyTranslator(semantics, evidence, inventory));
      var typeTranslator = new JavaTypeTranslator(semantics, evidence, inventory, new JavaDeclarationPropertyObserver(coverage), methodTranslator, JavaTranslationPolicy.legacyAnalysis());
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

    JavaDeclarationProjector.UnitTranslation contribute(List<TranslatedMethodBody> bodies, SemanticResolver resolver) {
      return new JavaDeclarationProjector(resolver, evidence, inventory, JavaTranslationPolicy.legacyAnalysis()).translate(unit, source, bodies, translated.types());
    }
  }

  private static final class MissingOwnerTrees extends Trees {
    private final Trees delegate;
    private final ClassTree owner;

    private MissingOwnerTrees(Trees delegate, ClassTree owner) {
      this.delegate = delegate;
      this.owner = owner;
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
    @Override public Element getElement(TreePath path) { return path.getLeaf() == owner ? null : delegate.getElement(path); }
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
