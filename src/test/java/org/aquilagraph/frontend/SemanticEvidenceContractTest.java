package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements.Origin;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.syntax.JavaTypeStructure.Declared;
import org.aquilagraph.syntax.JavaTypeStructure.LocalDeclared;
import org.aquilagraph.syntax.JavaTypeStructure.LocalDeclared.Kind;
import org.aquilagraph.syntax.SyntaxType.Erroneous;
import org.aquilagraph.syntax.SyntaxType.NoType;
import org.aquilagraph.syntax.SyntaxType.NotApplicable;
import org.aquilagraph.syntax.SyntaxType.Resolved;
import org.aquilagraph.syntax.SyntaxType.StructureUnavailable;
import org.aquilagraph.syntax.SyntaxType.Unresolved;
import org.aquilagraph.syntax.SyntaxType.Unsupported;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticEvidenceContractTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenMissingWrongKindResolvedAndErroneousCompilerElements_whenReferencesAreProjected_thenAbsenceAndEachSemanticStatusRemainDistinct()
      throws IOException {
    // Arrange
    try (var observation = new CompilerObservation("class Sample { Missing field; int read(int input) { return input; } Object value() { return null; } Object erroneous() { return field; } }", true)) {
      var resolver = observation.resolver;
      var inputPath = observation.identifiers.get("input");
      var missingPath = observation.identifiers.get("Missing");
      var literalPath = observation.literals.getFirst();

      // Act
      var absent = resolver.typeReference(literalPath);
      var unresolved = resolver.reference(literalPath, ReferenceKind.LOCAL).orElseThrow();
      var wrongKind = resolver.reference(inputPath, ReferenceKind.FIELD).orElseThrow();
      var resolved = resolver.reference(inputPath, ReferenceKind.PARAMETER).orElseThrow();
      var erroneous = resolver.reference(missingPath, ReferenceKind.TYPE).orElseThrow();
      var erroneousField = resolver.reference(observation.identifiers.get("field"), ReferenceKind.FIELD).orElseThrow();

      // Assert
      assertEquals(Optional.empty(), absent);
      assertEquals(new SemanticReference(ReferenceKind.LOCAL, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of()), unresolved);
      assertEquals(new SemanticReference(ReferenceKind.FIELD, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of()), wrongKind);
      assertEquals(ResolutionStatus.RESOLVED, resolved.status());
      assertEquals(Optional.of("type:Sample#read(int):parameter:0"), resolved.symbolKey());
      assertEquals(ResolutionStatus.ERROR, erroneous.status());
      assertEquals(Optional.empty(), erroneous.symbolKey());
      assertEquals(ResolutionStatus.ERROR, erroneousField.status());
      assertEquals(Optional.of("type:Sample#field:field"), erroneousField.symbolKey());
      assertEquals(Erroneous.INSTANCE, resolver.type(missingPath));
      assertEquals(JavaTypeCategory.ERROR, resolver.typeReference(resolver.element(missingPath).asType()).orElseThrow().identity().category());
    }
  }

  @Test
  void givenParsedUnattributedExpressionAndNonExpressionTree_whenTypesAreQueried_thenUnresolvedIsDistinctFromNotApplicable() throws IOException {
    // Arrange
    try (var observation = new CompilerObservation("class Sample { int read(int input) { return input; } }", false)) {
      var expression = observation.identifiers.get("input");
      var declaration = observation.classes.get("Sample");

      // Act
      var unresolved = observation.resolver.type(expression);
      var notApplicable = observation.resolver.type(declaration);

      // Assert
      assertEquals(Unresolved.INSTANCE, unresolved);
      assertEquals(NotApplicable.INSTANCE, notApplicable);
      assertEquals(Optional.empty(), observation.resolver.attributedMirror(expression));
    }
  }

  @Test
  void givenNonTypeAndUnsupportedMirrorKinds_whenTypesAndReferencesAreProjected_thenNoTypeAndAbsentReferenceAreNotResolvedEvidence()
      throws IOException {
    // Arrange
    try (var observation = new CompilerObservation("class Sample {}", true)) {
      var none = observation.task.getTypes().getNoType(TypeKind.NONE);
      var packageType = observation.task.getElements().getPackageElement("").asType();
      var moduleType = observation.task.getElements().getModuleElement("java.base").asType();
      var other = (TypeMirror) Proxy.newProxyInstance(TypeMirror.class.getClassLoader(), new Class<?>[] {TypeMirror.class}, (_, method, _) -> {
        if (method.getName().equals("getKind")) {
          return TypeKind.OTHER;
        }
        throw new AssertionError("Unexpected unsupported-mirror query: " + method.getName());
      });

      // Act
      var outcomes = List.of(observation.resolver.declarationType(none), observation.resolver.declarationType(packageType),
          observation.resolver.declarationType(moduleType), observation.resolver.declarationType(other));
      var references = List.of(observation.resolver.typeReference(none), observation.resolver.typeReference(packageType),
          observation.resolver.typeReference(moduleType), observation.resolver.typeReference(other));

      // Assert
      assertEquals(List.of(new NoType(NoType.Kind.NONE), new NoType(NoType.Kind.PACKAGE),
          new NoType(NoType.Kind.MODULE), Unsupported.INSTANCE), outcomes);
      assertEquals(List.of(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()), references);
    }
  }

  @Test
  void givenActualNullAndExecutableMirrors_whenSemanticTypesAreProjected_thenIdentityOnlyResolutionHasNoInventedRecursiveStructureOrDeclarationKey()
      throws IOException {
    // Arrange
    try (var observation = new CompilerObservation("class Sample { int read(int input) { return input; } }", true)) {
      var nullType = observation.task.getTypes().getNullType();
      var executable = observation.resolver.element(observation.methods.get("read")).asType();

      // Act
      var nullOutcome = assertInstanceOf(Resolved.class, observation.resolver.declarationType(nullType));
      var executableOutcome = assertInstanceOf(Resolved.class, observation.resolver.declarationType(executable));
      var nullReference = observation.resolver.typeReference(nullType).orElseThrow();
      var executableReference = observation.resolver.typeReference(executable).orElseThrow();

      // Assert
      assertEquals(JavaTypeCategory.NULL, nullOutcome.identity().category());
      assertEquals(JavaTypeCategory.EXECUTABLE, executableOutcome.identity().category());
      assertEquals(Optional.empty(), nullOutcome.structure());
      assertEquals(Optional.empty(), executableOutcome.structure());
      assertEquals(JavaTypeCategory.NULL, nullReference.identity().category());
      assertEquals(JavaTypeCategory.EXECUTABLE, executableReference.identity().category());
      assertEquals(Optional.empty(), nullReference.declarationKey());
      assertEquals(Optional.empty(), executableReference.declarationKey());
    }
  }

  @Test
  void givenErroneousRequiredArrayWildcardAndIntersectionChildren_whenStructuresAreProjected_thenParentEvidenceRemainsUnavailable()
      throws IOException {
    // Arrange
    try (var observation = new CompilerObservation("class Sample<T extends Missing & Runnable> { Missing[] values; java.util.List<? extends Missing> list; }", true)) {
      var array = observation.variables.get("values");
      var list = observation.variables.get("list");
      var parameter = observation.task.getElements().getTypeElement("Sample").getTypeParameters().getFirst();

      // Act
      var arrayType = observation.resolver.type(array);
      var listType = observation.resolver.type(list);
      var upperBound = observation.resolver.typeParameterUpperBound(parameter);

      // Assert
      assertEquals(new StructureUnavailable(new JavaTypeIdentity(JavaTypeCategory.ARRAY, "Missing[]", "Missing[]")), arrayType);
      assertInstanceOf(StructureUnavailable.class, listType);
      assertEquals(Optional.empty(), upperBound);
    }
  }

  @Test
  void givenImplicitConstructorAndWrittenMethod_whenAttributionAddsSyntax_thenParsedInventoryAndDeclarationOriginsKeepTheirProvenance()
      throws IOException {
    // Arrange
    try (var observation = new CompilerObservation("class Sample { void written() {} }", true)) {
      var constructorPath = observation.methods.get("<init>");
      var writtenPath = observation.methods.get("written");
      var constructor = observation.resolver.element(constructorPath);
      var written = observation.resolver.element(writtenPath);

      // Act
      var constructorOrigin = observation.resolver.declarationOrigin(constructor);
      var writtenOrigin = observation.resolver.declarationOrigin(written);

      // Assert
      assertEquals(Origin.MANDATED, constructorOrigin);
      assertEquals(Origin.EXPLICIT, writtenOrigin);
      assertTrue(observation.resolver.isImplicitDeclaration(constructorPath));
      assertFalse(observation.resolver.isImplicitDeclaration(writtenPath));
      assertFalse(observation.inventory.isWritten(constructorPath.getLeaf()));
      assertTrue(observation.inventory.isWritten(writtenPath.getLeaf()));
      assertEquals(ElementKind.CONSTRUCTOR, constructor.getKind());
    }
  }

  @Test
  void givenStaticMemberAndLocalClassInAnInstanceMethod_whenDeclaredStructuresAreProjected_thenLexicalOwnersAndEnclosingInstancesRemainIndependent()
      throws IOException {
    // Arrange
    try (var observation = new CompilerObservation("class Sample { static class Nested {} Nested nested; void read() { class Local {} Local local = null; } }", true)) {
      var staticMember = observation.variables.get("nested");
      var local = observation.variables.get("local");

      // Act
      var staticType = assertInstanceOf(Resolved.class, observation.resolver.type(staticMember));
      var localType = assertInstanceOf(Resolved.class, observation.resolver.type(local));

      // Assert
      var staticStructure = assertInstanceOf(Declared.class, staticType.structure().orElseThrow());
      assertEquals("type:Sample.Nested", staticStructure.declarationKey());
      assertEquals(Optional.of("type:Sample"), staticStructure.lexicalOwnerKey());
      assertEquals(Optional.empty(), staticStructure.enclosingType());
      var localStructure = assertInstanceOf(LocalDeclared.class, localType.structure().orElseThrow());
      assertEquals("type:Sample#read()", localStructure.lexicalOwnerKey());
      assertEquals(Kind.LOCAL, localStructure.kind());
      var instance = assertInstanceOf(Declared.class, localStructure.enclosingType().orElseThrow());
      assertEquals("type:Sample", instance.declarationKey());
    }
  }

  private final class CompilerObservation implements AutoCloseable {
    private final StandardJavaFileManager manager;
    private final JavacTask task;
    private final SemanticResolver resolver;
    private final ParsedTreeInventory inventory;
    private final Map<String, TreePath> identifiers = new HashMap<>();
    private final Map<String, TreePath> variables = new HashMap<>();
    private final Map<String, TreePath> classes = new HashMap<>();
    private final Map<String, TreePath> methods = new HashMap<>();
    private final List<TreePath> literals = new ArrayList<>();

    private CompilerObservation(String content, boolean attribute) throws IOException {
      var source = SourceFile.fromContent(Path.of("Sample.java"), content);
      var sources = new SnapshotSourceCatalog(InputSnapshot.fromSources(temporaryDirectory, List.of(source)));
      var compiler = ToolProvider.getSystemJavaCompiler();
      manager = compiler.getStandardFileManager(null, null, null);
      task = (JavacTask) compiler.getTask(null, manager, new DiagnosticCollector<JavaFileObject>(),
          JavaCompilerInvocation.from(CompilerConfiguration.forRelease(JavaRelease.JAVA_25)).compilerOptions(), null, sources.compilationUnits());
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      var trees = Trees.instance(task);
      inventory = ParsedTreeInventory.capture(units, sources::find, new JavaSourceEvidence(trees.getSourcePositions()));
      if (attribute) {
        task.analyze();
      }
      resolver = new SemanticResolver(trees, task.getTypes(), task.getElements(), new DeclarationIdentityRegistry(trees, task.getTypes()));
      var scanner = new TreePathScanner<Void, Void>() {
        @Override public Void visitIdentifier(IdentifierTree tree, Void unused) {
          identifiers.put(tree.getName().toString(), getCurrentPath());
          return super.visitIdentifier(tree, unused);
        }
        @Override public Void visitVariable(VariableTree tree, Void unused) {
          variables.put(tree.getName().toString(), getCurrentPath());
          return super.visitVariable(tree, unused);
        }
        @Override public Void visitClass(ClassTree tree, Void unused) {
          classes.put(tree.getSimpleName().toString(), getCurrentPath());
          return super.visitClass(tree, unused);
        }
        @Override public Void visitMethod(MethodTree tree, Void unused) {
          methods.put(tree.getName().toString(), getCurrentPath());
          return super.visitMethod(tree, unused);
        }
        @Override public Void visitLiteral(LiteralTree tree, Void unused) {
          literals.add(getCurrentPath());
          return super.visitLiteral(tree, unused);
        }
      };
      units.forEach(unit -> scanner.scan(unit, null));
    }

    @Override public void close() throws IOException {
      manager.close();
    }
  }
}
