package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.ToolProvider;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.syntax.JavaDeclarationModifier;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaVariableDeclarationAstTest {
  private static final CompilerConfiguration JAVA_25 = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  @TempDir Path temporaryDirectory;

  @Test
  void givenFieldsAndEnumConstants_whenAnalyzed_thenDeclarationShellsRemainDistinctAndOrdered() throws IOException {
    // Arrange
    var source = """
        class Fields {
          @Deprecated private int plain;
          protected int initialized = 2;
        }
        enum Tone {
          @Deprecated QUIET,
          LOUD(2) { int code() { return 2; } };
          Tone() {}
          Tone(int volume) {}
        }
        """;
    Files.writeString(temporaryDirectory.resolve("Declarations.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var root = result.aggregate().orElseThrow().roots().getFirst();
    var fieldType = root.children(SyntaxRole.TYPE_DECLARATION).getFirst();
    var enumType = root.children(SyntaxRole.TYPE_DECLARATION).get(1);
    var fields = fieldType.children(SyntaxRole.MEMBER);
    var constants = enumType.children(SyntaxRole.MEMBER).stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).toList();
    var modeledFields = result.program().types().stream().filter(type -> type.qualifiedName().equals("Fields"))
        .findFirst().orElseThrow();
    var modeledEnum = result.program().types().stream().filter(type -> type.qualifiedName().equals("Tone"))
        .findFirst().orElseThrow();

    // Assert
    assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.FIELD_DECLARATION),
        fields.stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(List.of("plain", "initialized"),
        fields.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).name()).toList());
    assertEquals(List.of(false, true),
        fields.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).initializer()).toList());
    assertEquals(List.of("@Deprecated private int plain;", "protected int initialized = 2;"),
        fields.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of("@Deprecated"),
        fields.getFirst().children(SyntaxRole.ANNOTATION).stream().map(node -> excerpt(source, node)).toList());
    assertTrue(fields.get(1).children(SyntaxRole.ANNOTATION).isEmpty());
    assertEquals(List.of(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int")),
        fields.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).declaredType()).toList());
    assertEquals(List.of(Set.of(JavaDeclarationModifier.PRIVATE), Set.of(JavaDeclarationModifier.PROTECTED)),
        fields.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).writtenModifiers()).toList());
    assertTrue(fields.stream().allMatch(node -> node.children(SyntaxRole.DECLARED_TYPE).size() == 1));
    assertEquals(modeledFields.fields().stream().map(field -> field.fieldKey()).toList(),
        fields.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).declarationKey()).toList());
    assertTrue(fields.stream().allMatch(node -> node.value(SyntaxValue.VariableDeclaration.class).ownerKey()
        .equals(modeledFields.typeKey())));
    assertEquals(List.of("QUIET", "LOUD"),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).name()).toList());
    assertEquals(List.of("@Deprecated"),
        constants.getFirst().children(SyntaxRole.ANNOTATION).stream().map(node -> excerpt(source, node)).toList());
    assertTrue(constants.get(1).children(SyntaxRole.ANNOTATION).isEmpty());
    assertEquals(List.of(0, 1),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).arguments()).toList());
    assertEquals(List.of(false, true),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).constantBody()).toList());
    assertEquals(List.of(Set.of(), Set.of()),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).writtenModifiers()).toList());
    assertEquals(List.of(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Tone", "Tone"),
        new JavaTypeIdentity(JavaTypeCategory.DECLARED, "Tone", "Tone")),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).declaredType()).toList());
    assertEquals(modeledEnum.fields().stream().filter(field -> field.enumConstant()).map(field -> field.fieldKey()).toList(),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).declarationKey()).toList());
    assertTrue(constants.stream().allMatch(node -> node.value(SyntaxValue.VariableDeclaration.class).ownerKey()
        .equals(modeledEnum.typeKey())));
    assertTrue(fields.stream().allMatch(node -> node.origin().isPresent()));
    assertTrue(constants.stream().allMatch(node -> node.origin().isPresent()));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
  }

  @Test
  void givenRecordHeaders_whenAnalyzed_thenComponentsHaveIndependentOrderedSourceIdentity() throws IOException {
    // Arrange
    var source = """
        record Empty() {}
        record Pair(int left, String right) {}
        record Bag(@Deprecated String label, String... tags) {}
        """;
    Files.writeString(temporaryDirectory.resolve("Records.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var types = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION);
    var pair = types.get(1).children(SyntaxRole.RECORD_COMPONENT);
    var bag = types.get(2).children(SyntaxRole.RECORD_COMPONENT);
    var modeledPair = type(result.program().types(), "Pair");
    var modeledBag = type(result.program().types(), "Bag");

    // Assert
    assertTrue(types.getFirst().children(SyntaxRole.RECORD_COMPONENT).isEmpty());
    assertTrue(type(result.program().types(), "Empty").recordComponents().isEmpty());
    assertEquals(List.of("left", "right"),
        pair.stream().map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class).name()).toList());
    assertEquals(List.of("label", "tags"),
        bag.stream().map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class).name()).toList());
    assertEquals(List.of("int left", "String right"), pair.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of(new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int"),
        new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String")),
        modeledPair.recordComponents().stream().map(component -> component.staticType()).toList());
    assertEquals(modeledPair.recordComponents().stream().map(component -> component.staticType()).toList(),
        pair.stream().map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class).declaredType()).toList());
    assertEquals(List.of("int", "String"), pair.stream().map(node -> excerpt(source, node.children(SyntaxRole.DECLARED_TYPE).getFirst())).toList());
    assertEquals(List.of("@Deprecated String label", "String... tags"),
        bag.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of(new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String"),
        new JavaTypeIdentity(JavaTypeCategory.ARRAY, "java.lang.String[]", "java.lang.String[]")),
        modeledBag.recordComponents().stream().map(component -> component.staticType()).toList());
    assertEquals(modeledBag.recordComponents().stream().map(component -> component.staticType()).toList(),
        bag.stream().map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class).declaredType()).toList());
    assertEquals(List.of("String", "String..."),
        bag.stream().map(node -> excerpt(source, node.children(SyntaxRole.DECLARED_TYPE).getFirst())).toList());
    assertEquals(List.of("@Deprecated"),
        bag.getFirst().children(SyntaxRole.ANNOTATION).stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of(0, 1), modeledPair.recordComponents().stream().map(component -> component.ordinal()).toList());
    assertEquals(List.of(0, 1), modeledBag.recordComponents().stream().map(component -> component.ordinal()).toList());
    assertEquals(modeledPair.recordComponents().stream().map(component -> component.componentKey()).toList(),
        pair.stream().map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class).declarationKey()).toList());
    assertEquals(modeledBag.recordComponents().stream().map(component -> component.componentKey()).toList(),
        bag.stream().map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class).declarationKey()).toList());
    assertEquals(List.of(false, true),
        bag.stream().map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class).varArgs()).toList());
    assertEquals(List.of(1, 0),
        bag.stream().map(node -> node.children(SyntaxRole.ANNOTATION).size()).toList());
    assertTrue(pair.stream().allMatch(node -> node.children(SyntaxRole.DECLARED_TYPE).size() == 1));
    assertTrue(bag.stream().allMatch(node -> node.children(SyntaxRole.DECLARED_TYPE).size() == 1));
    assertTrue(modeledPair.recordComponents().stream().allMatch(component -> component.accessorKey().isPresent()
        && component.backingFieldKey().isPresent() && component.canonicalParameterKey().isPresent()));
    assertTrue(modeledBag.recordComponents().stream().allMatch(component -> component.accessorKey().isPresent()
        && component.backingFieldKey().isPresent() && component.canonicalParameterKey().isPresent()));
    assertTrue(pair.stream().allMatch(node -> node.origin().isPresent()));
    assertTrue(bag.stream().allMatch(node -> node.origin().isPresent()));
    assertTrue(types.stream().noneMatch(node -> node.children(SyntaxRole.MEMBER).stream()
        .anyMatch(member -> member.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION)));
    assertFalse(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-record-components")));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
  }

  @Test
  void givenCanonicalConstructorForms_whenAnalyzed_thenFormalProvenanceMatchesWrittenParameters() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Canonical.java"), """
        record Implicit(int value) {}
        record Compact(int value) { Compact {} }
        record Explicit(int value) { Explicit(int value) { this.value = value; } }
        """);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);

    // Assert
    for (var name : List.of("Implicit", "Compact", "Explicit")) {
      var record = type(result.program().types(), name);
      var parameterKey = record.recordComponents().getFirst().canonicalParameterKey().orElseThrow();
      var parameter = record.methods().stream().flatMap(method -> method.parameters().stream())
          .filter(candidate -> candidate.parameterKey().equals(parameterKey)).findFirst().orElseThrow();
      assertEquals(name.equals("Explicit"), parameter.sourceDeclared(), name);
      assertEquals(parameter.sourceDeclared(), !parameter.origins().isEmpty(), name);
      assertEquals("value", parameter.name(), name);
    }
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("frontend.unresolved-parameter")));
  }

  @Test
  void givenRecordAndEnum_whenComparedWithCompiler_thenDirectKeysEqualDetailedAndSynthesizedKeys() throws IOException {
    // Arrange
    var path = temporaryDirectory.resolve("Inventory.java");
    Files.writeString(path, """
        record Pair<T>(T left, T right) {}
        enum State { READY; }
        """);
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var compiler = ToolProvider.getSystemJavaCompiler();

    // Act and assert
    try (var manager = compiler.getStandardFileManager(null, null, null)) {
      var files = manager.getJavaFileObjectsFromPaths(List.of(path));
      var task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none", "--release", "25"), null, files);
      var units = new ArrayList<CompilationUnitTree>();
      task.parse().forEach(units::add);
      var inventory = ParsedTreeInventory.capture(units, ignored -> Optional.of(result.snapshot().sourceFiles().getFirst()),
          new JavaSourceEvidence(Trees.instance(task).getSourcePositions()));
      task.analyze();
      var trees = Trees.instance(task);
      var identities = new DeclarationIdentityRegistry(trees, task.getTypes());
      for (var name : List.of("Pair", "State")) {
        TypeElement element = task.getElements().getTypeElement(name);
        var modeled = type(result.program().types(), name);
        var compilerKeys = element.getEnclosedElements().stream().map(member -> identities.keyOf(member).orElseThrow()).toList();
        var detailedKeys = new HashSet<String>();
        modeled.fields().forEach(field -> assertTrue(detailedKeys.add(field.fieldKey())));
        modeled.recordComponents().forEach(component -> assertTrue(detailedKeys.add(component.componentKey())));
        modeled.methods().forEach(method -> assertTrue(detailedKeys.add(method.methodKey())));
        modeled.synthesizedDeclarations().forEach(declaration -> assertTrue(detailedKeys.add(declaration.declarationKey())));
        assertEquals(compilerKeys.size(), new HashSet<>(compilerKeys).size(), name);
        assertEquals(Set.copyOf(compilerKeys), detailedKeys, name + " " + element.getEnclosedElements().stream()
            .map(member -> identities.keyOf(member).orElseThrow() + ":" + task.getElements().getOrigin(member)
                + ":" + (trees.getTree(member) == null ? "null" : inventory.isWritten(trees.getTree(member))))
            .toList());
        for (var field : modeled.fields()) {
          if (field.sourceDeclared()) {
            var compilerField = element.getEnclosedElements().stream()
                .filter(member -> identities.keyOf(member).filter(field.fieldKey()::equals).isPresent()).findFirst().orElseThrow();
            assertEquals(Elements.Origin.EXPLICIT, task.getElements().getOrigin(compilerField));
            assertTrue(inventory.isWritten(trees.getTree(compilerField)));
          }
        }
        for (var component : modeled.recordComponents()) {
          var compilerComponent = element.getRecordComponents().get(component.ordinal());
          assertEquals(Elements.Origin.EXPLICIT, task.getElements().getOrigin(compilerComponent));
          assertEquals(identities.keyOf(compilerComponent.getAccessor()), component.accessorKey());
          var backingField = element.getEnclosedElements().stream().filter(VariableElement.class::isInstance)
              .map(VariableElement.class::cast)
              .filter(member -> member.getKind() == ElementKind.FIELD
                  && member.getSimpleName().contentEquals(compilerComponent.getSimpleName()))
              .findFirst().orElseThrow();
          assertEquals(identities.keyOf(backingField), component.backingFieldKey());
          var canonical = element.getEnclosedElements().stream().filter(ExecutableElement.class::isInstance)
              .map(ExecutableElement.class::cast).filter(member -> member.getKind() == ElementKind.CONSTRUCTOR)
              .findFirst().orElseThrow();
          assertEquals(identities.keyOf(canonical.getParameters().get(component.ordinal())), component.canonicalParameterKey());
          var componentTree = trees.getTree(compilerComponent);
          var written = ((ClassTree) trees.getTree(element)).getMembers().stream().filter(VariableTree.class::isInstance)
              .map(VariableTree.class::cast).filter(candidate -> inventory.isWritten(candidate)
                  && candidate.getName().contentEquals(compilerComponent.getSimpleName())
                  && trees.getSourcePositions().getStartPosition(units.getFirst(), candidate)
                      == trees.getSourcePositions().getStartPosition(units.getFirst(), componentTree))
              .toList();
          assertEquals(1, written.size());
        }
        for (var declaration : modeled.synthesizedDeclarations()) {
          var compilerMember = element.getEnclosedElements().stream()
              .filter(member -> identities.keyOf(member).filter(declaration.declarationKey()::equals).isPresent())
              .findFirst().orElseThrow();
          var tree = trees.getTree(compilerMember);
          assertTrue(tree == null || !inventory.isWritten(tree));
        }
        assertEquals(modeled.synthesizedDeclarations().stream().map(declaration -> declaration.declarationKey()).sorted().toList(),
            modeled.synthesizedDeclarations().stream().map(declaration -> declaration.declarationKey()).toList(), name);
      }
    }
  }

  @Test
  void givenMultiFieldAndGenericRecord_whenAnalyzed_thenEachSourceOccurrenceKeepsItsOwnTuple() throws IOException {
    // Arrange
    var source = """
        class Values { private int first, second; }
        record Generic<T>(T left, T right) {}
        """;
    Files.writeString(temporaryDirectory.resolve("Occurrences.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var types = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION);
    var fields = types.getFirst().children(SyntaxRole.MEMBER);
    var components = types.get(1).children(SyntaxRole.RECORD_COMPONENT);

    // Assert
    assertEquals(List.of("first", "second"),
        fields.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).name()).toList());
    assertEquals(List.of("left", "right"),
        components.stream().map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class).name()).toList());
    assertEquals(1, types.get(1).children(SyntaxRole.TYPE_PARAMETER).size());
    assertEquals(List.of(0, 1), fields.stream().map(node -> node.structuralPath().steps().getLast().position()).toList());
    assertEquals(List.of(0, 1), components.stream().map(node -> node.structuralPath().steps().getLast().position()).toList());
    assertEquals(List.of("private int first,", "private int first, second;"),
        fields.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of("T left", "T right"), components.stream().map(node -> excerpt(source, node)).toList());
    assertEquals(List.of("int", "int"),
        fields.stream().map(node -> excerpt(source, node.children(SyntaxRole.DECLARED_TYPE).getFirst())).toList());
    assertEquals(List.of("T", "T"),
        components.stream().map(node -> excerpt(source, node.children(SyntaxRole.DECLARED_TYPE).getFirst())).toList());
  }

  @Test
  void givenDeclarations_whenAnalyzedTwice_thenFullSourceAndDiagnosticTuplesAreStable() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Stable.java"), """
        class Stable { int first, second = 2; }
        enum State { READY, BUSY(1); State() {} State(int value) {} }
        record Pair<T>(T left, T right) {}
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.analyze(temporaryDirectory, JAVA_25);
    var second = frontend.analyze(temporaryDirectory, JAVA_25);

    // Assert
    assertEquals(first.program(), second.program());
    assertEquals(first.aggregate(), second.aggregate());
    assertEquals(first.diagnostics(), second.diagnostics());
  }

  @Test
  void givenEnumForms_whenAnalyzed_thenAbsentEmptyAndPopulatedArgumentListsRemainDistinct() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Form.java"),
        "enum Form { A, B(), C(1), D/* comment */(); Form() {} Form(int value) {} }");

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var constants = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).getFirst()
        .children(SyntaxRole.MEMBER).stream().filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).toList();

    // Assert
    assertEquals(List.of("A", "B", "C", "D"),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).name()).toList());
    assertEquals(List.of(false, true, true, true),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).argumentList()).toList());
    assertEquals(List.of(0, 0, 1, 0),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).arguments()).toList());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-enum-argument-list")));
  }

  @Test
  void givenIneligibleUnicodeEscapeInsideEnumComment_whenAnalyzed_thenEmptyArgumentListRemainsWritten() throws IOException {
    // Arrange
    var source = "enum Form { A /* " + "\\\\" + "u002a/ */ (), B; Form() {} }";
    Files.writeString(temporaryDirectory.resolve("Form.java"), source);

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);
    var constants = result.aggregate().orElseThrow().roots().getFirst().children(SyntaxRole.TYPE_DECLARATION).getFirst()
        .children(SyntaxRole.MEMBER).stream().filter(node -> node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION).toList();

    // Assert
    assertEquals(List.of("A", "B"),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).name()).toList());
    assertEquals(List.of(true, false),
        constants.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).argumentList()).toList());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-enum-argument-list")));
  }

  @Test
  void givenDuplicateTopLevelDeclarations_whenAnalyzed_thenCompilerErrorsAreReturnedWithoutTranslationFailure()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("First.java"), "class Duplicate {}");
    Files.writeString(temporaryDirectory.resolve("Second.java"), "class Duplicate {}");

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);

    // Assert
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.duplicate-type-declaration")
        && !diagnostic.origins().isEmpty()));
    assertTrue(result.aggregate().isEmpty());
    assertTrue(result.program().types().isEmpty());
  }

  @Test
  void givenDuplicateFieldAndMethodDeclarations_whenAnalyzed_thenCompilerErrorsAndLocatedMemberDiagnosticsAreReturned()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Duplicate.java"),
        "class Duplicate { int value; int value; void run() {} void run() {} }");

    // Act
    var result = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory, JAVA_25);

    // Assert
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertEquals(2, result.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("frontend.duplicate-member-declaration")
        && !diagnostic.origins().isEmpty()).count());
    assertTrue(result.aggregate().isEmpty());
    assertTrue(result.program().types().isEmpty());
  }

  private static JavaType type(List<JavaType> types, String name) {
    return types.stream().filter(type -> type.qualifiedName().equals(name)).findFirst().orElseThrow();
  }

  private static String excerpt(String source, SyntaxNode node) {
    var range = node.origin().orElseThrow();
    return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
  }
}
