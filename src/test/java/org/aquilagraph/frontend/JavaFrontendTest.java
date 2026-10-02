package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.core.AnalysisEngine;
import org.aquilagraph.model.AnalysisCompletion;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.model.SourceOwnerPath;
import org.aquilagraph.syntax.MethodSyntax;
import org.aquilagraph.syntax.JavaTypeStructure;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.SyntaxReadiness;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaFrontendTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenResolvedJavaSource_whenFrontendRuns_thenCompilerSemanticsAndOriginsAreCaptured()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Resolved.java"),
        """
        package sample;
        final class Resolved {
          int length(String value) { return value.length(); }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertDiagnosticCodes(result, List.of());
    assertEquals("sample.Resolved", result.program().types().getFirst().qualifiedName());
    var method =
        result.program().methods().stream()
            .filter(candidate -> candidate.name().equals("length"))
            .findFirst()
            .orElseThrow();
    assertFalse(method.origins().isEmpty());
    assertTrue(
        result.methodSyntax(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
            .flatMap(node -> node.semanticReference().stream())
            .anyMatch(reference -> reference.status() == ResolutionStatus.RESOLVED));
    assertFalse(Files.exists(temporaryDirectory.resolve("sample/Resolved.class")));
  }

  @Test
  void givenCapturedSourceChangesOnDisk_whenSnapshotIsAnalyzed_thenCapturedContentIsAuthoritative()
      throws IOException {
    // Arrange
    var sourcePath = temporaryDirectory.resolve("Captured.java");
    Files.writeString(
        sourcePath, "class Captured { int value() { return 1; } }");
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(temporaryDirectory);
    Files.writeString(
        sourcePath, "class Captured { int value() { return missingName; } }");
    var frontend = new JavaFrontend(loader);

    // Act
    var result = frontend.analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertDiagnosticCodes(result, List.of());
    assertTrue(
        result.methodSyntax().stream().flatMap(body -> body.root().descendantsAndSelf().stream())
            .anyMatch(node -> node.syntaxKind() == SyntaxKind.LITERAL
                && node.value(SyntaxValue.Literal.class).text().equals("1")));
  }

  @Test
  void givenAbstractNativeAndUnsupportedMethods_whenFrontendRuns_thenBodiesAreClassifiedExplicitly()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Mixed.java"),
        """
        abstract class Mixed {
          abstract void abstractCall();
          native int nativeCall();
          void unsupported() { while (true) { return; } }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertTrue(
        result.program().methods().stream()
            .filter(method -> method.name().equals("abstractCall") || method.name().equals("nativeCall"))
            .noneMatch(method -> method.hasBody() || result.methodSyntax(method.methodKey()).isPresent()));
    assertTrue(
        result.program().methods().stream()
            .filter(method -> method.name().equals("unsupported"))
            .allMatch(method -> result.methodSyntax(method.methodKey()).orElseThrow().readiness() == SyntaxReadiness.UNSUPPORTED));
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")));
  }

  @Test
  void givenCompositeSupportedBody_whenFrontendRuns_thenOrderedSyntaxStructureAndIdentityAreStable()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Structured.java"),
        """
        final class Structured {
          int evaluate(int left, int right) {
            int sum = left + right;
            if (sum > 0) {
              return Math.max(sum, 1);
            }
            return -sum;
          }
        }
        """);
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(temporaryDirectory);
    var frontend = new JavaFrontend(loader);
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var first = frontend.analyze(snapshot, configuration);
    var second = frontend.analyze(snapshot, configuration);

    // Assert
    assertDiagnosticCodes(first, List.of());
    assertDiagnosticCodes(second, List.of());
    var firstBody = methodBody(first, "evaluate");
    var secondBody = methodBody(second, "evaluate");
    assertEquals(SyntaxReadiness.LOWERABLE, firstBody.readiness());
    assertEquals(firstBody.root(), secondBody.root());
    var local = firstBody.root().children(SyntaxRole.STATEMENT).getFirst();
    var writtenType = assertSourceOccurrence(first, firstBody, local).requiredChild(SyntaxRole.DECLARED_TYPE);
    assertEquals(SyntaxKind.TYPE_USE, writtenType.syntaxKind());
    assertEquals(JavaSourceKind.PRIMITIVE_TYPE, writtenType.sourceKind());
    assertEquals(new JavaTypeStructure.Primitive(JavaTypeCategory.INT),
        assertInstanceOf(SyntaxType.Resolved.class, writtenType.type()).structure().orElseThrow());
    var source = first.snapshot().sourceFiles().getFirst();
    var start = source.content().indexOf("int sum");
    assertEquals(Optional.of(new SourceSpan(source.sourceFileKey(), start, start + 3)), writtenType.origin());
    assertEquals(AnalysisCompletion.COMPLETED, new AnalysisEngine().analyze(first).completion());
    assertEquals(
        List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.IF, SyntaxKind.RETURN),
        firstBody.root().children(SyntaxRole.STATEMENT).stream().map(node -> node.syntaxKind()).toList());
    var ifNode = firstBody.root().children(SyntaxRole.STATEMENT).get(1);
    assertEquals(List.of(SyntaxRole.CONDITION, SyntaxRole.THEN_BRANCH),
        ifNode.children().stream().map(SyntaxChild::role).toList());
    assertEquals(
        List.of(SyntaxKind.PARENTHESIZED, SyntaxKind.BLOCK),
        ifNode.children().stream().map(SyntaxChild::node).map(node -> node.syntaxKind()).toList());
  }

  @Test
  void givenElementFreeExpressions_whenFrontendRuns_thenEachExpressionCarriesItsResolvedType() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("TypedExpressions.java"), """
        final class TypedExpressions {
          long evaluate(boolean condition) {
            return condition ? 1L + 2L : 3L;
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertDiagnosticCodes(result, List.of());
    var nodes = methodBody(result, "evaluate").root().descendantsAndSelf();
    var binary = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.BINARY).findFirst().orElseThrow();
    var conditional = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.CONDITIONAL_EXPRESSION).findFirst().orElseThrow();
    assertTrue(binary.semanticReference().isEmpty());
    assertEquals(JavaTypeCategory.LONG, binary.resolvedType().orElseThrow().category());
    assertEquals("long", binary.resolvedType().orElseThrow().erasedName());
    assertTrue(conditional.semanticReference().isEmpty());
    assertEquals(JavaTypeCategory.LONG, conditional.resolvedType().orElseThrow().category());
  }

  @Test
  void givenLiteralAndDefaultSwitchLabels_whenFrontendRuns_thenCaseFormAndLabelsRemainTyped() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("TypedSwitch.java"), """
        final class TypedSwitch {
          int classify(int value) {
            return switch (value) {
              case 1, 2 -> 3;
              default -> 4;
            };
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "classify");
    assertDiagnosticCodes(result, List.of());
    assertEquals(SyntaxReadiness.LOWERABLE, body.readiness());
    var cases = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.CASE).toList();
    assertEquals(2, cases.size());
    var literalCase = cases.getFirst();
    var literalCaseValue = literalCase.value(SyntaxValue.SwitchCase.class);
    assertEquals(SyntaxValue.SwitchCaseForm.RULE, literalCaseValue.form());
    assertEquals(List.of(new SyntaxValue.SwitchLabel.Expression(0), new SyntaxValue.SwitchLabel.Expression(1)),
        literalCaseValue.labels());
    assertEquals(2, literalCase.children(SyntaxRole.LABEL).size());
    assertEquals(List.of(SyntaxValue.SwitchLabel.Default.INSTANCE),
        cases.get(1).value(SyntaxValue.SwitchCase.class).labels());
  }

  @Test
  void givenEnumSwitchLabels_whenFrontendRuns_thenLabelsRetainResolvedSymbolIdentity() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("EnumSwitch.java"), """
        final class EnumSwitch {
          enum Mode { FIRST, SECOND }
          int classify(Mode mode) {
            return switch (mode) {
              case FIRST -> 1;
              case SECOND -> 2;
            };
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "classify");
    assertEquals(List.of("frontend.unsupported-enum-constant-initialization", "frontend.unsupported-enum-constant-initialization"),
        result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(SyntaxReadiness.LOWERABLE, body.readiness());
    var labels = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.CASE)
        .flatMap(node -> node.children(SyntaxRole.LABEL).stream()).toList();
    assertEquals(2, labels.size());
    assertTrue(labels.stream().allMatch(label -> label.semanticReference().stream()
        .anyMatch(reference -> reference.referenceKind() == ReferenceKind.ENUM_CONSTANT && reference.symbolKey().isPresent())));
  }

  @Test
  void givenUnmodeledConstantSwitchExpression_whenFrontendRuns_thenEntireCaseIsRejected() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("ConstantExpressionSwitch.java"), """
        final class ConstantExpressionSwitch {
          int classify(int value) {
            return switch (value) {
              case 1 + 2 -> 1;
              default -> 0;
            };
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "classify");
    assertEquals(SyntaxReadiness.UNSUPPORTED, body.readiness());
    assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")));
  }

  @Test
  void givenGuardedPatternSwitch_whenFrontendRuns_thenEntireCaseIsRejected() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("GuardedSwitch.java"), """
        final class GuardedSwitch {
          int classify(Object value) {
            return switch (value) {
              case String text when !text.isEmpty() -> 1;
              default -> 0;
            };
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "classify");
    assertEquals(SyntaxReadiness.UNSUPPORTED, body.readiness());
    assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")));
  }

  @Test
  void givenNullSwitchLabel_whenFrontendRuns_thenEntireCaseIsRejected() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("NullSwitch.java"), """
        final class NullSwitch {
          int classify(Object value) {
            return switch (value) {
              case null -> 0;
              default -> 1;
            };
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "classify");
    assertEquals(SyntaxReadiness.UNSUPPORTED, body.readiness());
    assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")));
  }

  @Test
  void givenStatementGroupSwitchExpression_whenFrontendRuns_thenEntireCaseIsRejected() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("StatementGroupSwitch.java"), """
        final class StatementGroupSwitch {
          int classify(int value) {
            return switch (value) {
              case 1:
                yield 1;
              default:
                yield 0;
            };
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "classify");
    assertEquals(SyntaxReadiness.UNSUPPORTED, body.readiness());
    assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")));
  }

  @Test
  void givenAnonymousClassCreation_whenFrontendRuns_thenEntireConstructionIsRejected() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("AnonymousCreation.java"), """
        final class AnonymousCreation {
          Object create() {
            return new Object() {
              int value = 1;
            };
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "create");
    assertEquals(SyntaxReadiness.UNSUPPORTED, body.readiness());
    assertTrue(body.root().descendantsAndSelf().stream().anyMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED
        && node.value(SyntaxValue.Unsupported.class).compilerKind().equals("NEW_CLASS")));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")));
    var creation = body.root().descendantsAndSelf().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Unsupported unsupported && unsupported.compilerKind().equals("NEW_CLASS"))
        .findFirst().orElseThrow();
    var resolved = assertInstanceOf(SyntaxType.Resolved.class, creation.type());
    var local = assertInstanceOf(JavaTypeStructure.LocalDeclared.class, resolved.structure().orElseThrow());
    assertEquals(JavaTypeStructure.LocalDeclared.Kind.ANONYMOUS, local.kind());
    assertEquals("type:AnonymousCreation#create()", local.lexicalOwnerKey());
    assertEquals(Optional.of(new JavaTypeStructure.Declared("type:AnonymousCreation", Optional.empty(), List.of())),
        local.enclosingType());
  }

  @Test
  void givenLocalClassCreation_whenFrontendRuns_thenAttributedTypeRetainsLocalOwnership() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("LocalCreation.java"), """
        final class LocalCreation {
          Object create() {
            class Local { }
            return new Local();
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var body = methodBody(result, "create");
    var creation = body.root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).findFirst().orElseThrow();
    var resolved = assertInstanceOf(SyntaxType.Resolved.class, creation.type());
    var local = assertInstanceOf(JavaTypeStructure.LocalDeclared.class, resolved.structure().orElseThrow());

    // Assert
    assertEquals(JavaTypeStructure.LocalDeclared.Kind.LOCAL, local.kind());
    assertEquals("type:LocalCreation#create()", local.lexicalOwnerKey());
    assertTrue(local.declarationKey().startsWith("type:LocalCreation#create():type:"), local.declarationKey());
    assertEquals(Optional.of(new JavaTypeStructure.Declared("type:LocalCreation", Optional.empty(), List.of())),
        local.enclosingType());
    var writtenType = assertSourceOccurrence(result, body, creation).requiredChild(SyntaxRole.TYPE_OPERAND);
    assertEquals(local, assertInstanceOf(SyntaxType.Resolved.class, writtenType.type()).structure().orElseThrow());
    assertEquals(ReferenceKind.TYPE, writtenType.requiredReference().referenceKind());
    assertEquals(Optional.of(local.declarationKey()), writtenType.requiredReference().symbolKey());
    assertEquals("Local", writtenText(result, writtenType));
  }

  @Test
  void givenMemberOfLocalClass_whenFrontendRuns_thenEnclosingInstanceRetainsLocalStructure() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("LocalMemberCreation.java"), """
        final class LocalMemberCreation {
          Object create() {
            class Local { class Member { } }
            return new Local().new Member();
          }
          Object createAgain() {
            class Local { class Member { } }
            return new Local().new Member();
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var firstRun = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var secondRun = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var member = localMemberCreationType(firstRun, "create");
    var enclosing = assertInstanceOf(JavaTypeStructure.LocalDeclared.class, member.enclosingType().orElseThrow());
    var anotherMember = localMemberCreationType(firstRun, "createAgain");

    // Assert
    assertEquals(Optional.of(enclosing.declarationKey()), member.lexicalOwnerKey());
    assertTrue(member.declarationKey().startsWith(enclosing.declarationKey() + ":type:"));
    assertEquals(JavaTypeStructure.LocalDeclared.Kind.LOCAL, enclosing.kind());
    assertEquals("type:LocalMemberCreation#create()", enclosing.lexicalOwnerKey());
    assertEquals(Optional.of(new JavaTypeStructure.Declared("type:LocalMemberCreation", Optional.empty(), List.of())),
        enclosing.enclosingType());
    assertNotEquals(member.declarationKey(), anotherMember.declarationKey());
    assertEquals(member, localMemberCreationType(secondRun, "create"));
    assertEquals(anotherMember, localMemberCreationType(secondRun, "createAgain"));
    assertTrue(firstRun.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-type-structure")));
    var body = methodBody(firstRun, "create");
    var creations = body.root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).toList();
    var sourceCreations = creations.stream().map(node -> assertSourceOccurrence(firstRun, body, node)).toList();
    assertEquals(List.of("Member", "Local"), sourceCreations.stream()
        .map(node -> writtenText(firstRun, node.requiredChild(SyntaxRole.TYPE_OPERAND))).toList());
    for (var sourceCreation : sourceCreations) {
      assertEquals(assertInstanceOf(SyntaxType.Resolved.class, sourceCreation.type()).structure(),
          assertInstanceOf(SyntaxType.Resolved.class, sourceCreation.requiredChild(SyntaxRole.TYPE_OPERAND).type()).structure());
    }
  }

  @Test
  void givenSameNamedLocalClassesInDifferentMethods_whenAnalyzedTwice_thenDeclarationKeysStayDistinctAndStable()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("LocalOwners.java"), """
        final class LocalOwners {
          Object first() { class Local { } return new Local(); }
          Object second() { class Local { } return new Local(); }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var firstRun = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var secondRun = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var firstKey = localCreationType(firstRun, "first").declarationKey();
    var secondKey = localCreationType(firstRun, "second").declarationKey();

    // Assert
    assertTrue(firstKey.startsWith("type:LocalOwners#first():type:"));
    assertTrue(secondKey.startsWith("type:LocalOwners#second():type:"));
    assertNotEquals(firstKey, secondKey);
    assertEquals(firstKey, localCreationType(secondRun, "first").declarationKey());
    assertEquals(secondKey, localCreationType(secondRun, "second").declarationKey());
    for (var methodName : List.of("first", "second")) {
      var body = methodBody(firstRun, methodName);
      var creation = body.root().descendantsAndSelf().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).findFirst().orElseThrow();
      var written = assertSourceOccurrence(firstRun, body, creation).requiredChild(SyntaxRole.TYPE_OPERAND);
      assertEquals(Optional.of(localCreationType(firstRun, methodName).declarationKey()),
          written.requiredReference().symbolKey());
      assertEquals("Local", writtenText(firstRun, written));
    }
  }

  @Test
  void givenWrittenLocalType_whenItsSelectedDeclarationIsForged_thenTypeUseConstructionRejectsTheWrongTarget() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("LocalTarget.java"), """
        class LocalTarget {
          Object create() { class Local { } return new Local(); }
        }
        """);
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(temporaryDirectory,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var body = methodBody(input, "create");
    var creation = body.root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).findFirst().orElseThrow();
    var sourceCreation = assertSourceOccurrence(input, body, creation);
    var written = sourceCreation.requiredChild(SyntaxRole.TYPE_OPERAND);
    var selected = written.requiredReference();
    var foreign = new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.RESOLVED, Optional.of("type:Foreign"), selected.typeName(),
        selected.typeAlternativeNames(), selected.assignableToTypeNames());

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> SyntaxNode.source(written.sourceFileKey(), written.structuralPath(),
        written.sourceKind(), written.syntaxKind(), written.type(), written.value(), written.children(), Optional.of(foreign),
        written.origin().orElseThrow()));

    // Assert
    assertEquals(sourceCreation, assertSourceOccurrence(input, body, creation));
    assertEquals("A type use requires its compiler-selected declaration target", failure.getMessage());
  }

  @Test
  void givenNullLiteral_whenFrontendRuns_thenExactTypeOutcomeHasNoRecursiveComponents() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("NullValue.java"), """
        final class NullValue {
          Object value() { return null; }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var body = methodBody(result, "value");
    var literal = body.root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).findFirst().orElseThrow();
    var resolved = assertInstanceOf(SyntaxType.Resolved.class, literal.type());

    // Assert
    assertEquals(JavaTypeCategory.NULL, resolved.identity().category());
    assertTrue(resolved.structure().isEmpty());
    assertEquals(SyntaxReadiness.LOWERABLE, body.readiness());
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-type-structure")));
  }

  @Test
  void givenTryWithResourcesAndFinally_whenFrontendRuns_thenBodyIsRejectedAndChildrenRemainOrdered()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("ResourceOwner.java"),
        """
        final class ResourceOwner {
          void read() {
            try (var reader = new java.io.StringReader("value")) {
              reader.read();
            } finally {
              System.out.println("closed");
            }
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "read");
    assertEquals(SyntaxReadiness.UNSUPPORTED, body.readiness());
    assertEquals(1, result.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")).count());
    var tryNode = body.root().children(SyntaxRole.STATEMENT).getFirst();
    assertEquals(SyntaxKind.UNSUPPORTED, tryNode.syntaxKind());
    assertEquals(List.of(SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK, SyntaxRole.FINALLY_BLOCK),
        tryNode.children().stream().map(SyntaxChild::role).toList());
    assertEquals(
        List.of(SyntaxKind.LOCAL_DECLARATION, SyntaxKind.BLOCK, SyntaxKind.BLOCK),
        tryNode.children().stream().map(SyntaxChild::node).map(node -> node.syntaxKind()).toList());
  }

  @Test
  void givenUnsupportedUnaryUpdate_whenFrontendRuns_thenBodyAndDiagnosticRejectConstruct()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Updating.java"),
        "final class Updating { int increment(int value) { return ++value; } }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var body = methodBody(result, "increment");
    assertEquals(SyntaxReadiness.UNSUPPORTED, body.readiness());
    assertEquals(
        List.of(SyntaxKind.BLOCK, SyntaxKind.RETURN, SyntaxKind.UNSUPPORTED, SyntaxKind.VARIABLE_ACCESS),
        body.root().descendantsAndSelf().stream().map(node -> node.syntaxKind()).toList());
    assertEquals(1, result.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")).count());
  }

  @Test
  void givenUnresolvedSymbol_whenFrontendRuns_thenCompilerDiagnosticPreventsSilentAcceptance()
      throws IOException {
    // Arrange
    Files.writeString(
        temporaryDirectory.resolve("Unresolved.java"),
        "class Unresolved { int value() { return missingName; } }");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
    assertTrue(
        methodBody(result, "value").root().descendantsAndSelf().stream()
            .flatMap(node -> node.semanticReference().stream())
            .anyMatch(reference -> reference.status() != ResolutionStatus.RESOLVED));
  }

  @Test
  void givenMultipleSources_whenFrontendRuns_thenProgramsAndDiagnosticsRetainSourceOwnership()
      throws IOException {
    // Arrange
    Files.createDirectories(temporaryDirectory.resolve("sample"));
    Files.writeString(
        temporaryDirectory.resolve("sample/Valid.java"),
        "package sample; final class Valid { int value() { return 1; } }");
    Files.writeString(
        temporaryDirectory.resolve("sample/Broken.java"),
        "package sample; final class Broken { int value() { return missingName; } }");
    var loader = new JavaSourceLoader();
    var snapshot = loader.discover(temporaryDirectory);
    var brokenSource =
        snapshot.sourceFiles().stream()
            .filter(sourceFile -> sourceFile.relativePath().endsWith("Broken.java"))
            .findFirst()
            .orElseThrow();
    var sourceKeys = snapshot.sourceFiles().stream().map(sourceFile -> sourceFile.sourceFileKey()).collect(Collectors.toSet());
    var frontend = new JavaFrontend(loader);

    // Act
    var result = frontend.analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertEquals(
        Set.of("sample.Valid", "sample.Broken"),
        result.program().types().stream()
            .map(type -> type.qualifiedName())
            .collect(Collectors.toSet()));
    var errors = result.diagnostics().stream().filter(diagnostic -> diagnostic.isError()).toList();
    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().allMatch(diagnostic -> !diagnostic.origins().isEmpty()
        && diagnostic.origins().stream().allMatch(origin -> sourceKeys.contains(origin.sourceFileKey()))));
    var compilerErrors = errors.stream().filter(diagnostic -> diagnostic.code().startsWith("compiler.err.")).toList();
    assertFalse(compilerErrors.isEmpty());
    assertTrue(compilerErrors.stream().allMatch(diagnostic -> diagnostic.origins().stream()
            .allMatch(origin -> origin.sourceFileKey().equals(brokenSource.sourceFileKey()))));
  }

  @Test
  void givenEmptySourceRoot_whenFrontendRuns_thenEmptyProgramRetainsCompilerEnvironment()
      throws IOException {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, configuration);

    // Assert
    assertTrue(result.program().types().isEmpty());
    assertTrue(result.diagnostics().isEmpty());
    assertEquals(configuration, result.environmentSnapshot().compilerConfiguration());
  }

  @Test
  void givenFrontendIsReusedAcrossDependencyChanges_whenAnalysisRuns_thenEnvironmentSnapshotsRemainIsolated()
      throws IOException {
    // Arrange
    var sourceRoot = Files.createDirectory(temporaryDirectory.resolve("source"));
    var dependencyRoot = Files.createDirectory(temporaryDirectory.resolve("dependency"));
    Files.writeString(sourceRoot.resolve("Stable.java"), "final class Stable { int value() { return 1; } }");
    var dependencyFile = dependencyRoot.resolve("version.txt");
    Files.writeString(dependencyFile, "first");
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(dependencyRoot), List.of(), List.of());
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var first = frontend.analyze(sourceRoot, configuration);
    Files.writeString(dependencyFile, "second");
    var second = frontend.analyze(sourceRoot, configuration);

    // Assert
    assertDiagnosticCodes(first, List.of());
    assertDiagnosticCodes(second, List.of());
    assertEquals(first.program(), second.program());
    assertNotEquals(first.environmentSnapshot().environmentDigest(), second.environmentSnapshot().environmentDigest());
  }

  private static MethodSyntax methodBody(SyntaxAnalysisInput result, String methodName) {
    var method = result.program().methods().stream().filter(candidate -> candidate.name().equals(methodName)).findFirst().orElseThrow();
    return result.methodSyntax(method.methodKey()).orElseThrow();
  }

  private static SyntaxNode assertSourceOccurrence(SyntaxAnalysisInput input, MethodSyntax body, SyntaxNode executionNode) {
    var aggregate = input.aggregate().orElseThrow();
    var declarations = aggregate.nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration declaration
            && declaration.declarationKey().equals(body.methodKey()))
        .toList();
    assertEquals(1, declarations.size());
    var declaration = declarations.getFirst();
    var sourceBody = declaration.requiredChild(SyntaxRole.BODY);
    var matches = sourceBody.descendantsAndSelf().stream()
        .filter(node -> node.sourceFileKey().equals(executionNode.sourceFileKey())
            && node.sourceKind() == executionNode.sourceKind() && node.syntaxKind() == executionNode.syntaxKind()
            && node.origin().equals(executionNode.origin()) && node.value().equals(executionNode.value())
            && node.type().equals(executionNode.type()) && node.semanticReference().equals(executionNode.semanticReference()))
        .toList();
    assertEquals(1, matches.size());
    var source = matches.getFirst();
    assertEquals(source, aggregate.node(source.nodeId()).orElseThrow());
    var executionSteps = executionNode.structuralPath().steps();
    var executionRootSteps = body.root().structuralPath().steps();
    var sourceSteps = source.structuralPath().steps();
    var sourceRootSteps = sourceBody.structuralPath().steps();
    assertEquals(executionRootSteps, executionSteps.subList(0, executionRootSteps.size()));
    assertEquals(sourceRootSteps, sourceSteps.subList(0, sourceRootSteps.size()));
    assertEquals(executionSteps.subList(executionRootSteps.size(), executionSteps.size()),
        sourceSteps.subList(sourceRootSteps.size(), sourceSteps.size()));
    var owner = aggregate.writtenSourceAssociation(declaration.nodeId()).orElseThrow();
    var sourceBodyOwner = aggregate.writtenSourceAssociation(sourceBody.nodeId()).orElseThrow();
    var sourceOwner = aggregate.writtenSourceAssociation(source.nodeId()).orElseThrow();
    assertEquals(owner.ownerPath().child(SourceOwnerPath.Role.BODY, 0), sourceBodyOwner.ownerPath());
    assertTrue(sourceBodyOwner.ownerPath().isAncestorOf(sourceOwner.ownerPath()));
    assertEquals(source.nodeId(), sourceOwner.nodeKey());
    assertEquals(executionNode.sourceFileKey(), sourceOwner.sourceFileKey());
    assertEquals(executionNode.sourceKind(), sourceOwner.sourceKind());
    assertEquals(executionNode.origin(), sourceOwner.origin());
    assertTrue(body.root().descendantsAndSelf().stream()
        .allMatch(node -> node.children(SyntaxRole.DECLARED_TYPE).isEmpty() && node.children(SyntaxRole.TYPE_OPERAND).isEmpty()));
    assertTrue(body.root().descendantsAndSelf().stream()
        .noneMatch(node -> node.syntaxKind() == SyntaxKind.TYPE_USE || node.syntaxKind() == SyntaxKind.ANNOTATION));
    return source;
  }

  private static JavaTypeStructure.LocalDeclared localCreationType(SyntaxAnalysisInput result, String methodName) {
    var creation = methodBody(result, methodName).root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).findFirst().orElseThrow();
    var resolved = assertInstanceOf(SyntaxType.Resolved.class, creation.type());
    return assertInstanceOf(JavaTypeStructure.LocalDeclared.class, resolved.structure().orElseThrow());
  }

  private static JavaTypeStructure.Declared localMemberCreationType(SyntaxAnalysisInput result, String methodName) {
    return methodBody(result, methodName).root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
        .map(node -> assertInstanceOf(SyntaxType.Resolved.class, node.type()).structure().orElseThrow())
        .filter(JavaTypeStructure.Declared.class::isInstance).map(JavaTypeStructure.Declared.class::cast).findFirst().orElseThrow();
  }

  private static void assertDiagnosticCodes(SyntaxAnalysisInput result, List<String> expected) {
    assertEquals(expected, result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
  }

  private static String writtenText(SyntaxAnalysisInput input, SyntaxNode node) {
    var source = input.snapshot().sourceFiles().stream().filter(file -> file.sourceFileKey().equals(node.sourceFileKey())).findFirst().orElseThrow();
    var origin = node.origin().orElseThrow();
    return source.content().substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
  }
}
