package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnitTranslationOwnershipTest {
  private static final CompilerConfiguration CONFIGURATION = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String FIRST = """
      package sample;
      @java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD,
          java.lang.annotation.ElementType.LOCAL_VARIABLE, java.lang.annotation.ElementType.TYPE_USE})
      @interface Mark {}
      class First {
        @Mark int left = 1, right = 2;
        { left = Math.abs(-3); }
        int subject() {
          @Mark int left = 4, right = 5;
          class Local { int value() { return Second.target(); } }
          Object first = new Object() { int value() { return 6; } };
          class Sibling { int value() { return left; } }
          Object second = new Object() { int value() { return 7; } };
          return new Local().value() + new Sibling().value();
        }
      }
      """;
  private static final String SECOND = """
      package sample;
      class Second {
        @Mark int left = 8, right = 9;
        static { int left = Math.abs(-10); }
        static int target() { return 11; }
        int subject() {
          @Mark int left = 12, right = 13;
          class Local { int value() { return First.class.hashCode(); } }
          class Sibling { int value() { return left; } }
          return new Local().value() + new Sibling().value();
        }
        enum Choice {
          FIRST(Math.abs(-14)) { int value() { return Second.target(); } };
          Choice(int value) {}
        }
      }
      """;
  @TempDir Path directory;

  @Test
  void givenRepeatedNamesAndRecursiveOwnersAcrossUnits_whenRequestsAlternate_thenCanonicalOwnersBindingsAndRetainedValuesRemain()
      throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var snapshot = snapshot("recursive", List.of(SourceFile.fromContent(Path.of("First.java"), FIRST),
        SourceFile.fromContent(Path.of("Second.java"), SECOND)));

    // Act
    var outcome = frontend.produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);
    var unrelated = frontend.produce(snapshot("ordinary", List.of(SourceFile.fromContent(Path.of("Other.java"),
        "class Other { int subject() { return 20; } }"))), CONFIGURATION, AstProfile.JAVA_SOURCE_BOUNDED);
    var repeated = frontend.produce(snapshot, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);
    var result = outcome.result().orElseThrow();
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(AstCompletion.COMPLETE, unrelated.completion(), unrelated.diagnostics().toString());
    assertArrayEquals(AstCodec.encode(result), AstCodec.encode(repeated.result().orElseThrow()));
    assertArrayEquals(AstCodec.encode(result), AstCodec.encode(decoded));
    assertEquals(result.identity(), decoded.identity());
    assertEquals(result.aggregate(), decoded.aggregate());
    assertEquals(snapshot.sourceFiles().stream().map(SourceFile::sourceFileKey).toList(), result.aggregate().roots().stream()
        .map(source -> source.sourceFileKey()).toList());
    for (var name : List.of("First", "Second")) {
      var file = name.equals("First") ? snapshot.sourceFiles().getFirst().sourceFileKey() : snapshot.sourceFiles().getLast().sourceFileKey();
      var type = type(result, name, file);
      var key = type.value(SyntaxValue.TypeDeclaration.class).declarationKey();
      assertEquals("type:sample." + name, key);
      var members = type.children(SyntaxRole.MEMBER);
      var fields = members.stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION).toList();
      assertEquals(List.of("left", "right"), fields.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).name()).toList());
      assertTrue(fields.stream().allMatch(node -> node.value(SyntaxValue.VariableDeclaration.class).ownerKey().equals(key)));
      var firstAnnotation = fields.getFirst().children(SyntaxRole.ANNOTATION).getFirst();
      assertEquals(file, firstAnnotation.sourceFileKey());
      assertTrue(fields.getLast().children(SyntaxRole.ANNOTATION).isEmpty());
      var source = name.equals("First") ? FIRST : SECOND;
      var association = result.aggregate().writtenSourceAssociation(firstAnnotation.nodeId()).orElseThrow();
      assertEquals(firstAnnotation.origin(), association.origin());
      var origin = association.origin().orElseThrow();
      assertEquals("@Mark", source.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive())));
      var subject = members.stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration method
          && method.name().equals("subject")).findFirst().orElseThrow();
      var methodKey = subject.value(SyntaxValue.ExecutableDeclaration.class).declarationKey();
      assertEquals("type:sample." + name + "#subject()", methodKey);
      var body = result.body(methodKey).orElseThrow();
      assertEquals(Optional.of(MethodBodyOrigin.SOURCE_AST), result.aggregate().methodBodyOrigin(methodKey));
      assertSame(subject.requiredChild(SyntaxRole.BODY), body.root());
      var locals = body.root().children(SyntaxRole.STATEMENT).stream()
          .filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local && List.of("left", "right").contains(local.name()))
          .toList();
      assertEquals(List.of("left", "right"), locals.stream().map(node -> node.value(SyntaxValue.LocalDeclaration.class).name()).toList());
      var localAnnotation = locals.getFirst().children(SyntaxRole.ANNOTATION).getFirst();
      assertEquals(List.of(localAnnotation.nodeId()), locals.getLast().value(SyntaxValue.LocalDeclaration.class).bindingEvidence()
          .orElseThrow().sharedAnnotationKeys());
      for (var nested : List.of("Local", "Sibling")) {
        var local = type(result, nested, file);
        var declaration = result.program().types().stream().filter(value -> value.typeKey()
            .equals(local.value(SyntaxValue.TypeDeclaration.class).declarationKey())).findFirst().orElseThrow();
        assertEquals(Optional.of(methodKey), declaration.nesting().lexicalOwnerKey());
        assertEquals(Optional.of(key), declaration.enclosingType().orElseThrow().declarationKey());
        assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node == local));
      }
      if (name.equals("First")) {
        var anonymous = result.program().types().stream().filter(value -> value.nesting().kind() == JavaTypeNesting.Kind.ANONYMOUS)
            .filter(value -> value.origins().stream().anyMatch(typeOrigin -> typeOrigin.sourceFileKey().equals(file))).toList();
        assertEquals(2, anonymous.size());
        assertTrue(anonymous.stream().allMatch(value -> value.nesting().lexicalOwnerKey().equals(Optional.of(methodKey))));
      }
    }
    var firstLocal = type(result, "Local", snapshot.sourceFiles().getFirst().sourceFileKey());
    var secondLocal = type(result, "Local", snapshot.sourceFiles().getLast().sourceFileKey());
    assertNotEquals(firstLocal.value(SyntaxValue.TypeDeclaration.class).declarationKey(),
        secondLocal.value(SyntaxValue.TypeDeclaration.class).declarationKey());
    var target = result.program().methods().stream().filter(method -> method.name().equals("target")).findFirst().orElseThrow();
    assertEquals("type:sample.Second#target()", target.methodKey());
    assertTrue(firstLocal.descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .anyMatch(node -> node.semanticReference().orElseThrow().symbolKey().equals(Optional.of(target.methodKey()))));
    for (var scope : result.aggregate().executionScopes()) {
      var syntax = result.aggregate().executionSyntax(scope.key()).orElseThrow();
      if (result.aggregate().node(syntax.root().nodeId()).isPresent()) {
        assertSame(result.aggregate().node(syntax.root().nodeId()).orElseThrow(), syntax.root());
      }
    }
    assertFalse(result.coverage().validationEvidence().unmatchedFacts().stream().findAny().isPresent());
    observeBaseline("recursive", result);
  }

  @Test
  void givenInvalidMiddleUnit_whenProductionFailsThenValidWorkFollows_thenTruthfulFailureAndLaterCanonicalRequestRemain() throws IOException {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var valid = snapshot("valid", List.of(SourceFile.fromContent(Path.of("First.java"), FIRST),
        SourceFile.fromContent(Path.of("Second.java"), SECOND)));
    var invalid = snapshot("invalid", List.of(SourceFile.fromContent(Path.of("Before.java"), "class Before { int value = 1; }"),
        SourceFile.fromContent(Path.of("Broken.java"), "class Broken { int subject() { return missing; } }"),
        SourceFile.fromContent(Path.of("After.java"), "class After { int value = 2; }")));

    // Act
    var failure = frontend.produce(invalid, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);
    var recovered = frontend.produce(valid, CONFIGURATION, AstProfile.JAVA_SOURCE_UNITS);

    // Assert
    assertNotEquals(AstCompletion.COMPLETE, failure.completion());
    assertTrue(failure.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
    assertEquals(AstCompletion.COMPLETE, recovered.completion(), recovered.diagnostics().toString());
    assertEquals(valid.sourceFiles().stream().map(SourceFile::sourceFileKey).toList(), recovered.result().orElseThrow().aggregate().roots().stream()
        .map(source -> source.sourceFileKey()).toList());
  }

  private InputSnapshot snapshot(String name, List<SourceFile> files) {
    var preservationDirectory = System.getProperty("unit.preservation.directory");
    var root = preservationDirectory == null ? directory : Path.of(preservationDirectory).resolve("inputs");
    return InputSnapshot.fromSources(root.resolve(name), files);
  }

  private static SyntaxNode type(AstResult result, String name, String file) {
    return result.aggregate().nodes().stream().filter(node -> node.sourceFileKey().equals(file))
        .filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration declaration && declaration.name().equals(name))
        .findFirst().orElseThrow();
  }

  private static void observeBaseline(String name, AstResult result) throws IOException {
    var preservationDirectory = System.getProperty("unit.preservation.directory");
    if (preservationDirectory == null) {
      return;
    }
    var baseline = Path.of(preservationDirectory).resolve("baseline").resolve(name + ".ast");
    var bytes = AstCodec.encode(result);
    if (Boolean.getBoolean("unit.preservation.capture")) {
      Files.createDirectories(baseline.getParent());
      Files.write(baseline, bytes);
    } else {
      assertArrayEquals(Files.readAllBytes(baseline), bytes, name);
    }
  }
}
