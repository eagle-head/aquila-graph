package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.InvocationSelection;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ArrayCloneInvocationTest {
  @Test
  void givenAttributedArrayAndNamedCloneCalls_whenDeclarationKeysAreCopied_thenOnlyNamedSelectionHasDeclarationIdentity() throws IOException {
    // Arrange
    var source = """
        class Example {
          int[] array(int[] value) { return value.clone(); }
          Example named(Example value) { return value.clone(); }
          public Example clone() { return this; }
        }
        """;

    // Act
    var observations = compilerSelections(source);

    // Assert
    assertEquals(2, observations.size());
    var intrinsic = observations.getFirst();
    assertEquals("int[]", intrinsic.receiver());
    assertEquals("int[]", intrinsic.result());
    assertEquals("()java.lang.Object", intrinsic.selectedType());
    assertEquals("Array", intrinsic.owner());
    assertTrue(intrinsic.ownerModule().isEmpty());
    assertTrue(intrinsic.declarationKey().isEmpty());
    assertEquals(0, intrinsic.parameters());
    assertEquals(0, intrinsic.thrownTypes());
    assertFalse(intrinsic.variableArity());
    assertTrue(intrinsic.publicMember());
    var named = observations.getLast();
    assertEquals("Example", named.receiver());
    assertEquals("Example", named.result());
    assertEquals("()Example", named.selectedType());
    assertEquals("Example", named.owner());
    assertEquals(Optional.of(""), named.ownerModule());
    assertEquals(Optional.of(new JavaTypeDeclarationIdentity(Optional.empty(), "Example").declarationKey() + "#clone()"),
        named.declarationKey());
  }

  @TestFactory
  Stream<DynamicTest> givenArrayReceiverVariants_whenPublicProducerAndCodecAreConsumed_thenExactIntrinsicTypesAndApplicabilitySurvive() {
    return Stream.of(
        new Variant("int[]", "int[]", "int[]", new JavaTypeStructure.Primitive(JavaTypeCategory.INT), "value.clone()", 0),
        new Variant("String[]", "java.lang.String[]", "java.lang.String[]",
            new JavaTypeStructure.Declared(new JavaTypeDeclarationIdentity(Optional.of("java.base"), "java.lang.String").declarationKey(),
                Optional.empty(), List.of()), "value.clone()", 0),
        new Variant("T[]", "T[]", "java.lang.Object[]",
            new JavaTypeStructure.Variable(new JavaTypeDeclarationIdentity(Optional.empty(), "Example").declarationKey() + "#type-parameter:0"),
            "value.clone()", 0),
        new Variant("int[][]", "int[][]", "int[][]", new JavaTypeStructure.Array(new JavaTypeStructure.Primitive(JavaTypeCategory.INT)),
            "value.clone()", 0),
        new Variant("T[][]", "T[][]", "java.lang.Object[][]",
            new JavaTypeStructure.Array(new JavaTypeStructure.Variable(
                new JavaTypeDeclarationIdentity(Optional.empty(), "Example").declarationKey() + "#type-parameter:0")), "value.clone()", 0),
        new Variant("java.util.List<String>[]", "java.util.List<java.lang.String>[]", "java.util.List[]",
            new JavaTypeStructure.Declared(new JavaTypeDeclarationIdentity(Optional.of("java.base"), "java.util.List").declarationKey(),
                Optional.empty(), List.of(new JavaTypeStructure.Declared(
                    new JavaTypeDeclarationIdentity(Optional.of("java.base"), "java.lang.String").declarationKey(), Optional.empty(), List.of()))),
            "value.clone()", 0),
        new Variant("int[]", "int[]", "int[]", new JavaTypeStructure.Primitive(JavaTypeCategory.INT), "value.<String>clone()", 1),
        new Variant("int[]", "int[]", "int[]", new JavaTypeStructure.Primitive(JavaTypeCategory.INT), "(value).clone()", 0))
        .map(variant -> DynamicTest.dynamicTest("givenArrayVariant[" + variant.type() + ";" + variant.expression()
            + "]_whenProducedAndRetained_thenExactIntrinsicTypesAndApplicabilitySurvive", () -> {
          // Arrange
          var source = "class Example<T> { " + variant.type() + " subject(" + variant.type() + " value) { return "
              + variant.expression() + "; } }";

          // Act
          var produced = produce(source);
          var restored = AstCodec.decode(AstCodec.encode(produced), produced.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, produced.completion(), produced.qualification().reasons().toString());
          assertEquals(AstCompletion.COMPLETE, restored.completion());
          var call = calls(restored).getFirst();
          assertEquals(1, calls(restored).size());
          var invocation = call.value(SyntaxValue.Invocation.class);
          var selection = invocation.semantics().orElseThrow();
          assertEquals("clone", invocation.name());
          assertEquals(InvocationSelection.ARRAY_CLONE, selection.selection());
          assertEquals(InvocationMode.VIRTUAL, selection.mode());
          assertEquals(InvocationForm.INSTANCE_EXPRESSION, selection.form());
          assertEquals(variant.displayName(), selection.qualifyingType().identity().displayName());
          assertEquals(variant.erasedName(), selection.qualifyingType().identity().erasedName());
          assertTrue(selection.qualifyingType().declarationKey().isEmpty());
          assertTrue(call.semanticReference().isEmpty());
          assertTrue(call.children(SyntaxRole.ARGUMENT).isEmpty());
          assertEquals(variant.typeArguments(), call.children(SyntaxRole.TYPE_ARGUMENT).size());
          var result = assertInstanceOf(SyntaxType.Resolved.class, call.type());
          assertEquals(variant.displayName(), result.identity().displayName());
          assertEquals(variant.erasedName(), result.identity().erasedName());
          assertEquals(new JavaTypeStructure.Array(variant.component()), result.structure().orElseThrow());
          assertEquals(result, call.requiredChild(SyntaxRole.RECEIVER).type());
          var method = restored.program().types().getFirst().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
          var applicability = restored.body(method.methodKey()).orElseThrow().invocationArity(call.nodeId()).orElseThrow();
          assertEquals(InvocationApplicabilityMode.FIXED, applicability.mode());
          assertEquals(0, applicability.formalCount());
          assertTrue(applicability.varargArrayType().isEmpty());
          assertEquals(produced.aggregate(), restored.aggregate());
          assertArrayEquals(AstCodec.encode(produced), AstCodec.encode(restored));
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenSupportedSourceProfiles_whenArrayCloneIsProduced_thenEveryProfileRetainsIntrinsicSelectionWithoutNamedTarget() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED, AstProfile.JAVA_SOURCE_INITIALIZATION,
        AstProfile.JAVA_SOURCE_NESTED, AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION, AstProfile.JAVA_SOURCE_DECLARATIONS,
        AstProfile.JAVA_SOURCE_UNITS, AstProfile.JAVA_SOURCE_FIDELITY, AstProfile.JAVA_SOURCE_MODULE_SERVICES)
        .map(profile -> DynamicTest.dynamicTest("givenSourceProfile[" + profile.version()
            + "]_whenArrayCloneIsProduced_thenIntrinsicSelectionHasNoNamedTarget", () -> {
          // Arrange
          var request = new AstProductionRequest(snapshot("class Example { int[] subject(int[] value) { return value.clone(); } }"),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);

          // Act
          var result = JavaFrontends.standard().produce(request).result().orElseThrow();

          // Assert
          assertEquals(AstCompletion.COMPLETE, result.completion(), result.qualification().reasons().toString());
          var call = calls(result).getFirst();
          assertEquals(InvocationSelection.ARRAY_CLONE, call.value(SyntaxValue.Invocation.class).semantics().orElseThrow().selection());
          assertTrue(call.semanticReference().isEmpty());
          assertEquals("int[]", call.resolvedType().orElseThrow().displayName());
        }));
  }

  @Test
  void givenOrdinaryCloneOverloadsAndConstructorCalls_whenProduced_thenNamedSelectionsAndReferencesRemainResolved() {
    // Arrange
    var source = """
        class Example {
          public Example clone() { return this; }
          public int[] clone(int count) { return new int[count]; }
          Example subject(Example value) { value.clone(2); new Example(); return value.clone(); }
        }
        """;

    // Act
    var result = produce(source);

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.qualification().reasons().toString());
    var calls = result.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.Invocation).toList();
    assertEquals(3, calls.size());
    assertTrue(calls.stream().allMatch(node -> node.value(SyntaxValue.Invocation.class).semantics().orElseThrow().selection()
        == InvocationSelection.NAMED_EXECUTABLE));
    assertTrue(calls.stream().allMatch(node -> node.semanticReference().orElseThrow().status() == ResolutionStatus.RESOLVED));
    assertTrue(calls.stream().allMatch(node -> node.semanticReference().orElseThrow().symbolKey().isPresent()));
  }

  @TestFactory
  Stream<DynamicTest> givenInvalidOrUnresolvedCloneCalls_whenPublicProducerIsConsumed_thenDiagnosticsRemainAndIntrinsicSelectionIsAbsent() {
    return Stream.of(
        "class Example { Object subject(int[] value) { return value.clone(1); } }",
        "class Example { Object subject(Missing value) { return value.clone(); } }",
        "class Example { Object subject(Missing[] value) { return value.clone(); } }",
        "class Example { Object subject(Object value) { return value.clone(); } }")
        .flatMap(source -> Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED, AstProfile.JAVA_SOURCE_MODULE_SERVICES)
            .map(profile -> DynamicTest.dynamicTest("givenInvalidOrUnresolvedCall[" + profile.version() + ";" + source
                + "]_whenProduced_thenDiagnosticsRemainAndIntrinsicSelectionIsAbsent", () -> {
          // Arrange
          var request = new AstProductionRequest(snapshot(source), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);

          // Act
          var outcome = JavaFrontends.standard().produce(request);

          // Assert
          var result = outcome.result().orElseThrow();
          assertEquals(AstCompletion.INCOMPLETE, result.completion());
          assertFalse(result.diagnostics().isEmpty());
          assertTrue(calls(result).stream().noneMatch(node -> node.value(SyntaxValue.Invocation.class).semantics()
              .filter(value -> value.selection() == InvocationSelection.ARRAY_CLONE).isPresent()));
        })));
  }

  @TestFactory
  Stream<DynamicTest> givenMissingAttributedInvocationEvidence_whenProgramIsTranslated_thenNoIntrinsicSelectionIsInvented() {
    return Stream.of(AttributionGap.values()).map(gap -> DynamicTest.dynamicTest("givenAttributionGap[" + gap.name()
        + "]_whenProgramIsTranslated_thenNoIntrinsicSelectionIsInvented", () -> {
      // Arrange
      var source = "class Example { int[] subject(int[] value) { return value.clone(); } }";

      // Act
      var invocation = translateWithGap(source, gap);

      // Assert
      assertEquals("clone", invocation.value(SyntaxValue.Invocation.class).name());
      assertTrue(invocation.value(SyntaxValue.Invocation.class).semantics()
          .filter(value -> value.selection() == InvocationSelection.ARRAY_CLONE).isEmpty());
      assertEquals(ResolutionStatus.UNRESOLVED, invocation.semanticReference().orElseThrow().status());
      assertTrue(invocation.semanticReference().orElseThrow().symbolKey().isEmpty());
    }));
  }

  private static List<SyntaxNode> calls(AstResult result) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).toList();
  }

  private static AstResult produce(String source) {
    return JavaFrontends.standard().produce(request(source)).result().orElseThrow();
  }

  private static AstProductionRequest request(String source) {
    return new AstProductionRequest(snapshot(source), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES);
  }

  private static InputSnapshot snapshot(String source) {
    return InputSnapshot.fromSources(capturedAbsolute(Path.of(".")),
        List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
  }

  private static List<CompilerSelection> compilerSelections(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
      var inputs = new SnapshotSourceCatalog(snapshot(source)).compilationUnits();
      var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("--release", "25", "-proc:none"), null, inputs);
      var units = task.parse();
      task.analyze();
      var trees = Trees.instance(task);
      var registry = new DeclarationIdentityRegistry(trees, task.getTypes(), task.getElements(),
          JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_MODULE_SERVICES));
      var observed = new ArrayList<CompilerSelection>();
      new TreePathScanner<Void, Void>() {
        @Override
        public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
          if (call.getMethodSelect() instanceof MemberSelectTree selector && selector.getIdentifier().contentEquals("clone")) {
            var path = getCurrentPath();
            var selectorPath = new TreePath(path, selector);
            var executable = assertInstanceOf(ExecutableElement.class, trees.getElement(path));
            var owner = assertInstanceOf(TypeElement.class, executable.getEnclosingElement());
            var selected = assertInstanceOf(ExecutableType.class, trees.getTypeMirror(selectorPath));
            var receiver = trees.getTypeMirror(new TreePath(selectorPath, selector.getExpression()));
            var result = trees.getTypeMirror(path);
            assertEquals(TypeKind.EXECUTABLE, selected.getKind());
            observed.add(new CompilerSelection(receiver.toString(), result.toString(), selected.toString(),
                owner.getQualifiedName().toString(), Optional.ofNullable(task.getElements().getModuleOf(owner))
                    .map(module -> module.getQualifiedName().toString()), registry.keyOf(executable), executable.getParameters().size(),
                executable.getThrownTypes().size(), executable.isVarArgs(), executable.getModifiers().contains(Modifier.PUBLIC)));
          }
          return super.visitMethodInvocation(call, unused);
        }
      }.scan(units, null);
      assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR),
          diagnostics.getDiagnostics().toString());
      return List.copyOf(observed);
    }
  }

  private static SyntaxNode translateWithGap(String source, AttributionGap gap) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var snapshot = snapshot(source);
    var sources = new SnapshotSourceCatalog(snapshot);
    var policy = JavaTranslationPolicy.forProfile(AstProfile.JAVA_SOURCE_MODULE_SERVICES);
    try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("--release", "25", "-proc:none"), null,
          sources.compilationUnits());
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      var trees = Trees.instance(task);
      var evidence = new JavaSourceEvidence(trees.getSourcePositions());
      var inventory = ParsedTreeInventory.capture(units, sources::find, evidence);
      task.analyze();
      assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      var controlled = new IncompleteInvocationTrees(trees, gap);
      var semantics = new SemanticResolver(controlled, task.getTypes(), task.getElements(),
          new DeclarationIdentityRegistry(controlled, task.getTypes(), task.getElements(), policy));
      var translated = new JavaProgramTranslator(sources, semantics, evidence, inventory, policy).translate(snapshot, units);
      return translated.value().aggregate().orElseThrow().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
          .findFirst().orElseThrow();
    }
  }

  private enum AttributionGap { SELECTED_ELEMENT, SELECTED_TYPE, RECEIVER_TYPE }

  private static final class IncompleteInvocationTrees extends Trees {
    private final Trees delegate;
    private final AttributionGap gap;

    private IncompleteInvocationTrees(Trees delegate, AttributionGap gap) {
      this.delegate = delegate;
      this.gap = gap;
    }

    @Override public Element getElement(TreePath path) {
      return gap == AttributionGap.SELECTED_ELEMENT && path.getLeaf() instanceof MethodInvocationTree ? null : delegate.getElement(path);
    }
    @Override public TypeMirror getTypeMirror(TreePath path) {
      var parent = path.getParentPath();
      if (parent != null && ((gap == AttributionGap.SELECTED_TYPE && parent.getLeaf() instanceof MethodInvocationTree)
          || (gap == AttributionGap.RECEIVER_TYPE && parent.getLeaf() instanceof MemberSelectTree
              && parent.getParentPath() != null && parent.getParentPath().getLeaf() instanceof MethodInvocationTree))) {
        return null;
      }
      return delegate.getTypeMirror(path);
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
    @Override public Scope getScope(TreePath path) { return delegate.getScope(path); }
    @Override public String getDocComment(TreePath path) { return delegate.getDocComment(path); }
    @Override public boolean isAccessible(Scope scope, TypeElement type) { return delegate.isAccessible(scope, type); }
    @Override public boolean isAccessible(Scope scope, Element element, DeclaredType type) { return delegate.isAccessible(scope, element, type); }
    @Override public TypeMirror getOriginalType(ErrorType type) { return delegate.getOriginalType(type); }
    @Override public void printMessage(Diagnostic.Kind kind, CharSequence message, Tree tree, CompilationUnitTree unit) { delegate.printMessage(kind, message, tree, unit); }
    @Override public TypeMirror getLub(CatchTree tree) { return delegate.getLub(tree); }
  }

  private record Variant(String type, String displayName, String erasedName, JavaTypeStructure component, String expression,
      int typeArguments) {}

  private record CompilerSelection(String receiver, String result, String selectedType, String owner, Optional<String> ownerModule,
      Optional<String> declarationKey, int parameters, int thrownTypes, boolean variableArity, boolean publicMember) {}
}
