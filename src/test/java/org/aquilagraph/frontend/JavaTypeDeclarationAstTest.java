package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaTypeModifier;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxSemanticRelation;
import org.aquilagraph.syntax.SyntaxSemanticTarget;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaTypeDeclarationAstTest {
  private static final CompilerConfiguration JAVA_25 = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

  @TempDir Path sourceRoot;

  @Test
  void givenFiveTypeFormsAndHeaders_whenAnalyzed_thenExactTypeFactsAndLexicalChildrenAreRetained() throws IOException {
    // Arrange
    var source = """
        package sample;
        @Deprecated public sealed class Root<T> extends Base implements Face permits Child {
          int number;
          Root() {}
          void action() {}
          class Inner {}
        }
        class Base {}
        interface Face {}
        final class Child extends Root<String> {}
        enum Shade { RED }
        record Pair(int left) { int twice() { return left * 2; } }
        @interface Flag { String value(); }
        """;
    var directory = Files.createDirectory(sourceRoot.resolve("sample"));
    Files.writeString(directory.resolve("Root.java"), source);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.analyze(sourceRoot, JAVA_25);
    var second = frontend.analyze(sourceRoot, JAVA_25);
    var aggregate = first.aggregate().orElseThrow();
    var types = aggregate.roots().getFirst().children(SyntaxRole.TYPE_DECLARATION);
    var root = types.getFirst();
    var value = root.value(SyntaxValue.TypeDeclaration.class);
    var members = root.children(SyntaxRole.MEMBER);
    var inner = members.getLast();

    // Assert
    assertTrue(first.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> first.diagnostics().toString());
    assertEquals(List.of("Root", "Base", "Face", "Child", "Shade", "Pair", "Flag"),
        types.stream().map(type -> type.value(SyntaxValue.TypeDeclaration.class).name()).toList());
    assertEquals(List.of(JavaSourceKind.CLASS, JavaSourceKind.CLASS, JavaSourceKind.INTERFACE, JavaSourceKind.CLASS,
        JavaSourceKind.ENUM, JavaSourceKind.RECORD, JavaSourceKind.ANNOTATION_TYPE),
        types.stream().map(type -> type.sourceKind()).toList());
    assertEquals(List.of("""
        @Deprecated public sealed class Root<T> extends Base implements Face permits Child {
          int number;
          Root() {}
          void action() {}
          class Inner {}
        }
        """.stripTrailing(), "class Base {}", "interface Face {}", "final class Child extends Root<String> {}",
        "enum Shade { RED }", "record Pair(int left) { int twice() { return left * 2; } }",
        "@interface Flag { String value(); }"), excerpts(source, types));
    assertEquals(List.of(Set.of(JavaTypeModifier.PUBLIC, JavaTypeModifier.SEALED), Set.of(), Set.of(),
        Set.of(JavaTypeModifier.FINAL), Set.of(), Set.of(), Set.of()),
        types.stream().map(type -> type.value(SyntaxValue.TypeDeclaration.class).writtenModifiers()).toList());
    assertEquals(List.of(Set.of(JavaTypeModifier.PUBLIC, JavaTypeModifier.SEALED), Set.of(),
        Set.of(JavaTypeModifier.ABSTRACT), Set.of(JavaTypeModifier.FINAL), Set.of(JavaTypeModifier.FINAL),
        Set.of(JavaTypeModifier.FINAL), Set.of(JavaTypeModifier.ABSTRACT)),
        types.stream().map(type -> type.value(SyntaxValue.TypeDeclaration.class).effectiveModifiers()).toList());
    assertEquals(List.of(new SyntaxValue.TypeDeclaration.Shape(1, 1, true, 1, 1, 0, 4),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 0),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 0),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, true, 0, 0, 0, 0),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 1),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 1, 1),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 1)),
        types.stream().map(type -> type.value(SyntaxValue.TypeDeclaration.class).shape()).toList());
    assertEquals(Set.of(JavaTypeModifier.PUBLIC, JavaTypeModifier.SEALED), value.writtenModifiers());
    assertTrue(value.effectiveModifiers().containsAll(value.writtenModifiers()));
    assertEquals(new SyntaxValue.TypeDeclaration.Shape(1, 1, true, 1, 1, 0, 4), value.shape());
    assertEquals(List.of("@Deprecated"), excerpts(source, root.children(SyntaxRole.ANNOTATION)));
    assertEquals(List.of("T"), excerpts(source, root.children(SyntaxRole.TYPE_PARAMETER)));
    assertEquals(List.of(SyntaxKind.TYPE_PARAMETER_DECLARATION),
        root.children(SyntaxRole.TYPE_PARAMETER).stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals("T", root.requiredChild(SyntaxRole.TYPE_PARAMETER).value(SyntaxValue.TypeParameterDeclaration.class).name());
    assertEquals(List.of("Base"), excerpts(source, root.children(SyntaxRole.SUPERCLASS)));
    assertEquals(List.of("Face"), excerpts(source, root.children(SyntaxRole.INTERFACE)));
    assertEquals(List.of("Child"), excerpts(source, root.children(SyntaxRole.PERMITTED_SUBCLASS)));
    assertEquals(List.of(SyntaxKind.TYPE_USE, SyntaxKind.TYPE_USE, SyntaxKind.TYPE_USE),
        List.of(root.requiredChild(SyntaxRole.SUPERCLASS), root.requiredChild(SyntaxRole.INTERFACE),
            root.requiredChild(SyntaxRole.PERMITTED_SUBCLASS)).stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(List.of("Root<String>"), excerpts(source, types.get(3).children(SyntaxRole.SUPERCLASS)));
    assertEquals(List.of(List.of("int number;", "Root() {}", "void action() {}", "class Inner {}"), List.of(), List.of(),
        List.of(), List.of("RED"), List.of("int twice() { return left * 2; }"), List.of("String value();")),
        types.stream().map(type -> excerpts(source, type.children(SyntaxRole.MEMBER))).toList());
    assertEquals(List.of(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION, SyntaxKind.METHOD_DECLARATION,
        SyntaxKind.TYPE_DECLARATION), List.of(), List.of(), List.of(), List.of(SyntaxKind.ENUM_CONSTANT_DECLARATION),
        List.of(SyntaxKind.METHOD_DECLARATION), List.of(SyntaxKind.METHOD_DECLARATION)),
        types.stream().map(type -> type.children(SyntaxRole.MEMBER).stream().map(SyntaxNode::syntaxKind).toList()).toList());
    assertEquals(List.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.METHOD_DECLARATION, SyntaxKind.METHOD_DECLARATION,
        SyntaxKind.TYPE_DECLARATION), members.stream().map(member -> member.syntaxKind()).toList());
    assertEquals(List.of(0, 1, 2, 3), root.children().stream().filter(child -> child.role() == SyntaxRole.MEMBER)
        .map(child -> child.position()).toList());
    assertEquals("Inner", inner.value(SyntaxValue.TypeDeclaration.class).name());
    assertEquals(root.structuralPath().child(SyntaxRole.MEMBER, 3), inner.structuralPath());
    assertEquals(List.of("class Inner {}"), excerpts(source, List.of(inner)));
    for (var ordinal = 0; ordinal < types.size(); ordinal++) {
      var type = types.get(ordinal);
      assertEquals(aggregate.roots().getFirst().structuralPath().child(SyntaxRole.TYPE_DECLARATION, ordinal), type.structuralPath());
      assertEquals(IntStream.range(0, type.children(SyntaxRole.MEMBER).size()).boxed().toList(),
          type.children().stream().filter(child -> child.role() == SyntaxRole.MEMBER).map(child -> child.position()).toList());
      assertTrue(type.origin().orElseThrow().endOffsetExclusive() > type.origin().orElseThrow().startOffset());
    }
    assertTrue(aggregate.semanticEdges().stream().anyMatch(edge -> edge.sourceKey().equals(root.nodeId())
        && edge.relation() == SyntaxSemanticRelation.DECLARATION_REFERENCE
        && edge.target() instanceof SyntaxSemanticTarget.Declaration));
    assertTrue(types.stream().allMatch(type -> type.origin().isPresent() && type.sourceFileKey().equals(root.sourceFileKey())));
    assertEquals(aggregate, second.aggregate().orElseThrow());
    assertEquals(first.diagnostics(), second.diagnostics());
  }

  @Test
  void givenNestedFiveTypeForms_whenAnalyzed_thenEachHasExactLexicalOwnerKindAndRange() throws IOException {
    // Arrange
    var source = """
        class Container {
          class NestedClass {}
          interface NestedFace {}
          enum NestedShade { BLUE }
          record NestedPair(int value) {}
          @interface NestedFlag {}
        }
        """;
    Files.writeString(sourceRoot.resolve("Container.java"), source);

    // Act
    var analyzed = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var root = analyzed.aggregate().orElseThrow().roots().getFirst();
    var container = root.requiredChild(SyntaxRole.TYPE_DECLARATION);
    var nested = container.children(SyntaxRole.MEMBER);

    // Assert
    assertEquals(List.of("NestedClass", "NestedFace", "NestedShade", "NestedPair", "NestedFlag"),
        nested.stream().map(node -> node.value(SyntaxValue.TypeDeclaration.class).name()).toList());
    assertEquals(List.of(JavaSourceKind.CLASS, JavaSourceKind.INTERFACE, JavaSourceKind.ENUM, JavaSourceKind.RECORD,
        JavaSourceKind.ANNOTATION_TYPE), nested.stream().map(SyntaxNode::sourceKind).toList());
    assertEquals(List.of(Set.of(), Set.of(), Set.of(), Set.of(), Set.of()),
        nested.stream().map(node -> node.value(SyntaxValue.TypeDeclaration.class).writtenModifiers()).toList());
    assertEquals(List.of(Set.of(), Set.of(JavaTypeModifier.STATIC, JavaTypeModifier.ABSTRACT),
        Set.of(JavaTypeModifier.STATIC, JavaTypeModifier.FINAL), Set.of(JavaTypeModifier.STATIC, JavaTypeModifier.FINAL),
        Set.of(JavaTypeModifier.STATIC, JavaTypeModifier.ABSTRACT)),
        nested.stream().map(node -> node.value(SyntaxValue.TypeDeclaration.class).effectiveModifiers()).toList());
    assertEquals(List.of(new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 0),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 0),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 1),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 1, 0),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 0)),
        nested.stream().map(node -> node.value(SyntaxValue.TypeDeclaration.class).shape()).toList());
    assertEquals(List.of("class NestedClass {}", "interface NestedFace {}", "enum NestedShade { BLUE }",
        "record NestedPair(int value) {}", "@interface NestedFlag {}"), excerpts(source, nested));
    assertEquals(List.of(List.of(), List.of(), List.of("BLUE"), List.of(), List.of()),
        nested.stream().map(node -> excerpts(source, node.children(SyntaxRole.MEMBER))).toList());
    for (var ordinal = 0; ordinal < nested.size(); ordinal++) {
      assertEquals(container.structuralPath().child(SyntaxRole.MEMBER, ordinal), nested.get(ordinal).structuralPath());
      assertEquals(root.sourceFileKey(), nested.get(ordinal).sourceFileKey());
    }
  }

  @Test
  void givenUnresolvedWrittenSuperclass_whenAnalyzed_thenAttachmentRemainsAndCompletionIsBlocked() throws IOException {
    // Arrange
    var source = "class Derived extends Missing {}";
    Files.writeString(sourceRoot.resolve("Derived.java"), source);

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var run = new AnalysisEngine().analyze(input);
    var type = input.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION);
    var superclass = type.requiredChild(SyntaxRole.SUPERCLASS);

    // Assert
    assertEquals("Missing", excerpts(source, List.of(superclass)).getFirst());
    assertEquals(JavaSourceKind.IDENTIFIER, superclass.sourceKind());
    assertTrue(superclass.type() == SyntaxType.Erroneous.INSTANCE || superclass.type() == SyntaxType.Unresolved.INSTANCE);
    assertTrue(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.erroneous-type-use")
        || diagnostic.code().equals("frontend.unavailable-type-use-attribution")));
    assertTrue(input.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")));
    assertFalse(run.completion() == AnalysisCompletion.COMPLETED);
  }

  @Test
  void givenStaticAndInstanceInitializerBlocks_whenAnalyzed_thenTypedSourceMembersRetainTheirForms() throws IOException {
    // Arrange
    var source = "class Example { static { int first = 1; } { int second = 2; } }";
    Files.writeString(sourceRoot.resolve("Example.java"), source);

    // Act
    var analyzed = new JavaFrontend(new JavaSourceLoader()).analyze(sourceRoot, JAVA_25);
    var type = analyzed.aggregate().orElseThrow().roots().getFirst().requiredChild(SyntaxRole.TYPE_DECLARATION);
    var members = type.children(SyntaxRole.MEMBER);

    // Assert
    assertEquals(List.of(SyntaxKind.STATIC_INITIALIZER_BLOCK, SyntaxKind.INSTANCE_INITIALIZER_BLOCK),
        members.stream().map(SyntaxNode::syntaxKind).toList());
    assertEquals(List.of("static { int first = 1; }", "{ int second = 2; }"), excerpts(source, members));
    assertEquals(2, analyzed.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-initializer")).count());
  }

  private static List<String> excerpts(String source, List<SyntaxNode> nodes) {
    return nodes.stream().map(node -> {
      var range = node.origin().orElseThrow();
      return source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
    }).toList();
  }
}
