package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.tools.ToolProvider;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclarationIdentityRegistryTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenPublicCompilerElementsForOverloadsGenericsEnumAndRecord_whenKeysAreDerived_thenExactCanonicalIdentityGrammarIsRetained()
      throws IOException {
    // Arrange
    var source = SourceFile.fromContent(Path.of("Box.java"), """
        package example;
        class Box<T> {
          int value;
          Box(int value) { this.value = value; }
          int read(int value) { return value; }
          long read(long value) { return value; }
          <U> U convert(U value) { return value; }
        }
        enum Mode { VALUE }
        record Pair(int value) {}
        """);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var sources = new SnapshotSourceCatalog(InputSnapshot.fromSources(temporaryDirectory, List.of(source)));
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var manager = compiler.getStandardFileManager(null, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, null, JavaCompilerInvocation.from(configuration).compilerOptions(), null,
          sources.compilationUnits());
      task.parse();
      task.analyze();
      var registry = new DeclarationIdentityRegistry(Trees.instance(task), task.getTypes());
      var box = task.getElements().getTypeElement("example.Box");
      var methods = box.getEnclosedElements().stream().filter(element -> element.getKind() == ElementKind.METHOD)
          .map(ExecutableElement.class::cast).toList();
      var constructor = (ExecutableElement) box.getEnclosedElements().stream()
          .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR).findFirst().orElseThrow();
      var field = box.getEnclosedElements().stream().filter(element -> element.getKind() == ElementKind.FIELD).findFirst().orElseThrow();
      var mode = task.getElements().getTypeElement("example.Mode");
      var constant = mode.getEnclosedElements().stream().filter(element -> element.getKind() == ElementKind.ENUM_CONSTANT).findFirst().orElseThrow();
      var pair = task.getElements().getTypeElement("example.Pair");

      // Act
      var methodKeys = methods.stream().map(method -> registry.keyOf(method).orElseThrow()).toList();

      // Assert
      assertEquals(Optional.of("type:example.Box"), registry.keyOf(box));
      assertEquals(List.of("type:example.Box#read(int)", "type:example.Box#read(long)", "type:example.Box#convert(java.lang.Object)"), methodKeys);
      assertEquals(Optional.of("type:example.Box#<init>(int)"), registry.keyOf(constructor));
      assertEquals(Optional.of("type:example.Box#field:value"), registry.keyOf(field));
      assertEquals(Optional.of("type:example.Mode#enum:VALUE"), registry.keyOf(constant));
      assertEquals(Optional.of("type:example.Box#read(int):parameter:0"), registry.keyOf(methods.get(0).getParameters().getFirst()));
      assertEquals(Optional.of("type:example.Box#type-parameter:0"), registry.keyOf(box.getTypeParameters().getFirst()));
      assertEquals(Optional.of("type:example.Box#convert(java.lang.Object)#type-parameter:0"),
          registry.keyOf(methods.get(2).getTypeParameters().getFirst()));
      assertEquals(Optional.of("type:example.Pair#record-component:0"), registry.keyOf(pair.getRecordComponents().getFirst()));
      assertEquals("read(int)", registry.signatureOf(methods.getFirst()));
    }
  }

  @Test
  void givenAnElementWithNoDerivableIdentity_whenLookupIsRepeated_thenAbsentIdentityIsMemoizedByElementReference() throws IOException {
    // Arrange
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var manager = compiler.getStandardFileManager(null, null, null)) {
      var sources = new SnapshotSourceCatalog(InputSnapshot.fromSources(temporaryDirectory,
          List.of(SourceFile.fromContent(Path.of("Sample.java"), "class Sample {}"))));
      var task = (JavacTask) compiler.getTask(null, manager, null,
          JavaCompilerInvocation.from(CompilerConfiguration.forRelease(JavaRelease.JAVA_25)).compilerOptions(), null, sources.compilationUnits());
      task.parse();
      task.analyze();
      var registry = new DeclarationIdentityRegistry(Trees.instance(task), task.getTypes());
      var kindRequests = new AtomicInteger();
      var element = (Element) Proxy.newProxyInstance(Element.class.getClassLoader(), new Class<?>[] {Element.class}, (_, method, _) -> {
        if (method.getName().equals("getKind")) {
          kindRequests.incrementAndGet();
          return ElementKind.OTHER;
        }
        throw new AssertionError("Unexpected element query: " + method.getName());
      });

      // Act
      var first = registry.keyOf(element);
      var repeated = registry.keyOf(element);

      // Assert
      assertEquals(Optional.empty(), first);
      assertEquals(first, repeated);
      assertEquals(1, kindRequests.get());
    }
  }

  @Test
  void givenEqualButDistinctElementReferences_whenAbsentKeysAreMemoized_thenEachReferenceHasItsOwnCacheEntry() throws IOException {
    // Arrange
    withCompiledSource("class Sample {}", observation -> {
      var firstRequests = new AtomicInteger();
      var secondRequests = new AtomicInteger();
      var first = absentElement(firstRequests);
      var second = absentElement(secondRequests);
      assertTrue(first.equals(second));

      // Act
      var firstKey = observation.registry().keyOf(first);
      var secondKey = observation.registry().keyOf(second);
      var firstRepeated = observation.registry().keyOf(first);
      var secondRepeated = observation.registry().keyOf(second);

      // Assert
      assertEquals(Optional.empty(), firstKey);
      assertEquals(Optional.empty(), secondKey);
      assertEquals(firstKey, firstRepeated);
      assertEquals(secondKey, secondRepeated);
      assertEquals(1, firstRequests.get());
      assertEquals(1, secondRequests.get());
    });
  }

  @Test
  void givenCompilerLocalVariableAndOwnedTypes_whenKeysAreDerived_thenLiteralCoordinatesAndReferenceOnlyCategoriesAreRetained()
      throws IOException {
    // Arrange
    var content = "class Sample { void read() { int local = 1; class Local { class Nested {} } Object value = new Object() {}; } }";
    withCompiledSource(content, observation -> {
      var elements = new HashMap<String, Element>();
      var scanner = new TreePathScanner<Void, Void>() {
        @Override public Void visitClass(ClassTree tree, Void unused) {
          elements.put(tree.getSimpleName().toString(), observation.trees().getElement(getCurrentPath()));
          return super.visitClass(tree, unused);
        }
        @Override public Void visitVariable(VariableTree tree, Void unused) {
          elements.put(tree.getName().toString(), observation.trees().getElement(getCurrentPath()));
          return super.visitVariable(tree, unused);
        }
      };
      observation.units().forEach(unit -> scanner.scan(unit, null));

      // Act
      var localKey = observation.registry().keyOf(elements.get("local"));
      var localTypeKey = observation.registry().keyOf(elements.get("Local"));
      var nestedTypeKey = observation.registry().keyOf(elements.get("Nested"));
      var packageKey = observation.registry().keyOf(observation.task().getElements().getPackageElement(""));

      // Assert
      assertEquals(Optional.of("type:Sample#read():local_variable:29:43"), localKey);
      assertEquals(Optional.of("type:Sample#read():type:44:75"), localTypeKey);
      assertEquals(Optional.of("type:Sample#read():type:44:75:type:58:73"), nestedTypeKey);
      assertEquals(Optional.of("package:"), packageKey);
    });
  }

  @Test
  void givenMissingOwnersAndAbsentOwnerOrdinals_whenCanonicalFamiliesAreDerived_thenNoIdentityIsInvented() throws IOException {
    // Arrange
    withCompiledSource("class Sample<T> { void read(int input) {} } record Pair(int value) {}", observation -> {
      var owner = observation.task().getElements().getTypeElement("Sample");
      var methodOwner = (ExecutableElement) owner.getEnclosedElements().stream()
          .filter(element -> element.getKind() == ElementKind.METHOD).findFirst().orElseThrow();
      var recordOwner = observation.task().getElements().getTypeElement("Pair");
      var orphanField = elementWithOwner(VariableElement.class, ElementKind.FIELD, null);
      var orphanMethod = elementWithOwner(ExecutableElement.class, ElementKind.METHOD, null);
      var orphanParameter = elementWithOwner(VariableElement.class, ElementKind.PARAMETER, null);
      var missingParameter = elementWithOwner(VariableElement.class, ElementKind.PARAMETER, methodOwner);
      var orphanGeneric = elementWithOwner(TypeParameterElement.class, ElementKind.TYPE_PARAMETER, null);
      var missingGeneric = elementWithOwner(TypeParameterElement.class, ElementKind.TYPE_PARAMETER, owner);
      var orphanComponent = elementWithOwner(RecordComponentElement.class, ElementKind.RECORD_COMPONENT, null);
      var missingComponent = elementWithOwner(RecordComponentElement.class, ElementKind.RECORD_COMPONENT, recordOwner);
      var orphanLocal = elementWithOwner(VariableElement.class, ElementKind.LOCAL_VARIABLE, null);

      // Act
      var keys = List.of(orphanField, orphanMethod, orphanParameter, missingParameter, orphanGeneric, missingGeneric,
          orphanComponent, missingComponent, orphanLocal).stream().map(observation.registry()::keyOf).toList();

      // Assert
      assertEquals(List.of(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()), keys);
    });
  }

  private static Element absentElement(AtomicInteger requests) {
    return (Element) Proxy.newProxyInstance(Element.class.getClassLoader(), new Class<?>[] {Element.class}, (_, method, _) -> {
      return switch (method.getName()) {
        case "getKind" -> {
          requests.incrementAndGet();
          yield ElementKind.OTHER;
        }
        case "equals" -> true;
        case "hashCode" -> 0;
        default -> throw new AssertionError("Unexpected cache-probe query: " + method.getName());
      };
    });
  }

  private static <T extends Element> T elementWithOwner(Class<T> family, ElementKind kind, Element owner) {
    return family.cast(Proxy.newProxyInstance(family.getClassLoader(), new Class<?>[] {family}, (_, method, _) -> {
      return switch (method.getName()) {
        case "getKind" -> kind;
        case "getEnclosingElement", "getGenericElement" -> owner;
        case "equals" -> false;
        default -> throw new AssertionError("Unexpected unavailable-identity query: " + method.getName());
      };
    }));
  }

  private void withCompiledSource(String content, Consumer<RegistryObservation> assertion) throws IOException {
    var sources = new SnapshotSourceCatalog(InputSnapshot.fromSources(temporaryDirectory,
        List.of(SourceFile.fromContent(Path.of("Sample.java"), content))));
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var manager = compiler.getStandardFileManager(null, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, null,
          JavaCompilerInvocation.from(CompilerConfiguration.forRelease(JavaRelease.JAVA_25)).compilerOptions(), null, sources.compilationUnits());
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      task.analyze();
      var trees = Trees.instance(task);
      assertion.accept(new RegistryObservation(task, trees, new DeclarationIdentityRegistry(trees, task.getTypes()), units));
    }
  }

  private record RegistryObservation(JavacTask task, Trees trees, DeclarationIdentityRegistry registry, List<CompilationUnitTree> units) {}
}
