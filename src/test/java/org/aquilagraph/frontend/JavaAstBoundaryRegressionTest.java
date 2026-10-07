package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.source.admission.SourceAnalysisFixture;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.application.AnalysisCompletion;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeModifier;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.MethodSyntax;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaAstBoundaryRegressionTest {
  @TempDir Path temporaryDirectory;

  @Test
  void givenExecutableAndNestedClassMembers_whenFrontendRuns_thenEveryMemberIsTranslatedOrDiagnosed() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Members.java"), """
        class Members {
          static int staticValue = 1;
          int instanceValue = 2;
          ;
          static { staticValue = 3; }
          { instanceValue = 4; }

          static class Nested {
            int nestedMethod() { return 1; }
          }

          enum Mode {
            SPECIAL {
              int code() { return 1; }
            }
          }

          Members() { }
          void ordinaryMethod() { }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertEquals(Set.of("Members", "Members.Nested", "Members.Mode"), result.program().types().stream()
        .map(type -> type.qualifiedName()).collect(Collectors.toSet()));
    assertTrue(result.program().methods().stream().anyMatch(method -> method.name().equals("nestedMethod")));
    assertTrue(result.program().methods().stream().anyMatch(method -> method.name().equals("ordinaryMethod")));
    assertTrue(result.program().methods().stream().anyMatch(method -> method.constructor() && method.ownerTypeKey().equals("type:Members")));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-field-initializer")));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-initializer")));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-enum-constant-body")));
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-class-member")));
  }

  @Test
  void givenParametersAndSameNamedSiblingLocals_whenFrontendRuns_thenUseSitesMatchUniqueDeclarationKeys() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Identities.java"), """
        class Identities {
          int evaluate(int input) {
            { int repeated = input; }
            { int repeated = input; }
            return input;
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var method = result.program().methods().stream().filter(candidate -> candidate.name().equals("evaluate")).findFirst().orElseThrow();
    var nodes = result.methodSyntax(method.methodKey()).orElseThrow().root().descendantsAndSelf();
    var parameterKey = method.parameters().getFirst().parameterKey();
    assertTrue(nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS)
        .filter(node -> node.value(SyntaxValue.Symbol.class).name().equals("input"))
        .allMatch(node -> node.semanticReference().stream().anyMatch(reference -> reference.referenceKind() == ReferenceKind.PARAMETER
            && reference.symbolKey().orElseThrow().equals(parameterKey))));
    var localKeys = nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)
        .filter(node -> node.value(SyntaxValue.LocalDeclaration.class).name().equals("repeated"))
        .flatMap(node -> node.semanticReference().stream()).map(reference -> reference.symbolKey().orElseThrow()).collect(Collectors.toSet());
    assertEquals(2, localKeys.size());
  }

  @Test
  void givenTwoUnsupportedOccurrencesOfTheSameKind_whenFrontendRunsTwice_thenDiagnosticKeysAreDistinctAndRepeatable()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("UnsupportedOccurrences.java"), """
        class UnsupportedOccurrences {
          void execute() {
            while (false) { }
            while (false) { }
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
    var firstKeys = first.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax"))
        .map(diagnostic -> diagnostic.diagnosticKey()).toList();
    var secondKeys = second.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax"))
        .map(diagnostic -> diagnostic.diagnosticKey()).toList();
    assertEquals(2, firstKeys.stream().distinct().count());
    assertEquals(firstKeys, secondKeys);
  }

  @Test
  void givenInvocationContexts_whenFrontendRuns_thenSelectorsAndReceiversRemainTyped() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("InvocationContexts.java"), """
        class InvocationBase {
          void inherited() { }
        }
        class InvocationContexts extends InvocationBase {
          static void staticCall(Object value) { }
          void foo() { }
          InvocationContexts receiver() { return this; }

          void execute(Object value, InvocationContexts object) {
            foo();
            object.foo();
            InvocationContexts.staticCall(value);
            java.util.Objects.requireNonNull(value);
            receiver().staticCall(value);
            super.inherited();
          }

          class Inner {
            void call() { InvocationContexts.super.inherited(); }
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertEquals(List.of(), result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    var execute = syntax(result, "execute");
    var forms = execute.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .map(node -> node.value(SyntaxValue.Invocation.class).form()).collect(Collectors.toSet());
    assertTrue(forms.containsAll(Set.of(InvocationForm.INSTANCE_IMPLICIT, InvocationForm.INSTANCE_EXPRESSION,
        InvocationForm.STATIC_TYPE, InvocationForm.STATIC_EXPRESSION, InvocationForm.SUPER)));
    assertTrue(execute.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS)
        .map(node -> node.value(SyntaxValue.Symbol.class).name())
        .noneMatch(name -> Set.of("foo", "staticCall", "InvocationContexts", "java", "util", "Objects").contains(name)));
    assertTrue(syntax(result, "call").root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION)
        .map(node -> node.value(SyntaxValue.Invocation.class).form())
        .anyMatch(form -> form == InvocationForm.QUALIFIED_SUPER));
  }

  @Test
  void givenQualifiedSuperclassConstructorInvocation_whenFrontendRuns_thenEnclosingInstanceEvaluationIsRetained() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("QualifiedConstructor.java"), """
        class Host {
          class Parent { Parent(int value) { } }
        }
        class Owner {
          class Child extends Host.Parent {
            Child(Host host) { host.super(1); }
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    assertEquals(List.of(), result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    var child = result.program().types().stream().filter(type -> type.qualifiedName().equals("Owner.Child")).findFirst().orElseThrow();
    var constructor = child.methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow();
    var invocation = result.methodSyntax(constructor.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
    assertEquals(InvocationForm.QUALIFIED_SUPER_CONSTRUCTOR,
        invocation.value(SyntaxValue.Invocation.class).form());
    assertEquals(List.of(SyntaxKind.VARIABLE_ACCESS), invocation.children(SyntaxRole.RECEIVER).stream()
        .map(node -> node.syntaxKind()).toList());
  }

  @Test
  void givenCatchLocalsOverloadsAndDifferentOwners_whenFrontendRunsTwice_thenDeclarationKeysAreExactAndStable() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("ScopedIdentities.java"), """
        class FirstOwner {
          void overloaded(int marker) {
            int repeated = marker;
            try { throw new IllegalArgumentException(); } catch (IllegalArgumentException problem) { }
            try { throw new IllegalStateException(); } catch (IllegalStateException problem) { }
          }
          void overloaded(String marker) { String repeated = marker; }
        }
        class SecondOwner {
          void overloaded(int marker) { int repeated = marker; }
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
    assertEquals(List.of(),
        first.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(List.of(), second.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(first).completion());
    assertEquals(AnalysisCompletion.COMPLETE, new SourceAnalysisFixture().analyze(second).completion());
    var firstKeys = localDeclarationKeys(first, Set.of("repeated", "problem"));
    var secondKeys = localDeclarationKeys(second, Set.of("repeated", "problem"));
    assertEquals(5, firstKeys.size());
    assertEquals(firstKeys, secondKeys);
    var locals = first.methodSyntax().stream()
        .flatMap(method -> method.root().descendantsAndSelf().stream()
            .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)
            .map(node -> assertSourceLocal(first, method, node))).toList();
    assertEquals(5, locals.size());
    assertEquals(List.of("IllegalArgumentException", "IllegalStateException", "String", "int", "int"),
        locals.stream().map(node -> {
          var origin = node.requiredChild(SyntaxRole.DECLARED_TYPE).origin().orElseThrow();
          return snapshot.sourceFiles().getFirst().content().substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
        }).sorted().toList());
    for (var local : locals) {
      var written = local.value(SyntaxValue.LocalDeclaration.class).writtenType().orElseThrow();
      var retained = local.requiredChild(SyntaxRole.DECLARED_TYPE);
      assertEquals(SyntaxKind.TYPE_USE, retained.syntaxKind());
      assertEquals(written.sourceKind(), retained.sourceKind());
      assertEquals(written.origin(), retained.origin().orElseThrow());
      assertEquals(written.type(), retained.type());
      var resolved = (SyntaxType.Resolved) retained.type();
      assertEquals(retained.sourceKind() == JavaSourceKind.PRIMITIVE_TYPE ? JavaTypeCategory.INT : JavaTypeCategory.DECLARED,
          resolved.identity().category());
    }
    for (var method : first.program().methods().stream().filter(candidate -> candidate.name().equals("overloaded")).toList()) {
      assertTrue(first.methodSyntax(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)
          .map(node -> node.requiredReference().symbolKey().orElseThrow()).allMatch(key -> key.startsWith(method.methodKey() + ':')));
    }
  }

  @Test
  void givenDeclaredTypesFieldsExecutablesAndEnumConstants_whenFrontendRuns_thenUseSitesReferenceDurableDeclarationKeys()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("DeclarationTarget.java"), """
        class DeclarationTarget {
          int field;
          DeclarationTarget() { }
          int method() { return field; }
          Object use(Object value, Mode mode) {
            method();
            new DeclarationTarget();
            var cast = (DeclarationTarget) value;
            return switch (mode) { case ONE -> cast; };
          }
          enum Mode { ONE }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var target = result.program().types().stream().filter(type -> type.qualifiedName().equals("DeclarationTarget")).findFirst()
        .orElseThrow();
    var mode = result.program().types().stream().filter(type -> type.qualifiedName().equals("DeclarationTarget.Mode")).findFirst()
        .orElseThrow();
    var references = result.methodSyntax().stream().flatMap(method -> method.root().descendantsAndSelf().stream())
        .flatMap(node -> node.semanticReference().stream()).toList();
    var fieldKey = target.fields().stream().filter(field -> field.name().equals("field")).findFirst().orElseThrow().fieldKey();
    var enumKey = mode.fields().stream().filter(field -> field.enumConstant()).findFirst().orElseThrow().fieldKey();
    var methodKey = target.methods().stream().filter(method -> method.name().equals("method")).findFirst().orElseThrow().methodKey();
    var constructorKey = target.methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow().methodKey();
    assertTrue(references.stream().anyMatch(reference -> reference.referenceKind() == ReferenceKind.FIELD
        && reference.symbolKey().orElseThrow().equals(fieldKey)));
    assertTrue(references.stream().anyMatch(reference -> reference.referenceKind() == ReferenceKind.ENUM_CONSTANT
        && reference.symbolKey().orElseThrow().equals(enumKey)));
    assertTrue(references.stream().anyMatch(reference -> reference.referenceKind() == ReferenceKind.METHOD
        && reference.symbolKey().orElseThrow().equals(methodKey)));
    assertTrue(references.stream().anyMatch(reference -> reference.referenceKind() == ReferenceKind.CONSTRUCTOR
        && reference.symbolKey().orElseThrow().equals(constructorKey)));
    assertTrue(references.stream().anyMatch(reference -> reference.referenceKind() == ReferenceKind.TYPE
        && reference.symbolKey().orElseThrow().equals(target.typeKey())));
  }

  @Test
  void givenInheritanceOwnershipPermitsAndGenericBounds_whenFrontendRunsTwice_thenDeclarationFactsAreCompleteAndStable()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("TypeHeaders.java"), """
        sealed interface Contract<T extends Number & Comparable<T>> extends java.io.Serializable permits Implementation {
        }
        final class Implementation extends java.util.ArrayList<String> implements Contract<Integer>, Runnable {
          public void run() { }
        }
        class Owner {
          private static final class Nested { }
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
    assertEquals(List.of(), first.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    assertEquals(first.program(), second.program());
    var contract = first.program().types().stream().filter(type -> type.qualifiedName().equals("Contract")).findFirst().orElseThrow();
    assertEquals(Set.of(JavaTypeModifier.ABSTRACT, JavaTypeModifier.SEALED), contract.modifiers());
    assertTrue(contract.directSuperclass().isEmpty());
    assertEquals(List.of("type:java.io.Serializable"), contract.directInterfaces().stream()
        .map(reference -> reference.declarationKey().orElseThrow()).toList());
    assertEquals(List.of("type:Implementation"), contract.permittedSubclasses().stream()
        .map(reference -> reference.declarationKey().orElseThrow()).toList());
    var parameter = contract.typeParameters().getFirst();
    assertEquals("type:Contract#type-parameter:0", parameter.typeParameterKey());
    assertEquals(List.of("type:java.lang.Number", "type:java.lang.Comparable"), parameter.bounds().stream()
        .map(reference -> reference.declarationKey().orElseThrow()).toList());
    var implementation = first.program().types().stream().filter(type -> type.qualifiedName().equals("Implementation")).findFirst()
        .orElseThrow();
    assertEquals("type:java.util.ArrayList", implementation.directSuperclass().orElseThrow().declarationKey().orElseThrow());
    assertEquals("java.util.ArrayList<java.lang.String>", implementation.directSuperclass().orElseThrow().identity().displayName());
    assertEquals(List.of("type:Contract", "type:java.lang.Runnable"), implementation.directInterfaces().stream()
        .map(reference -> reference.declarationKey().orElseThrow()).toList());
    var nested = first.program().types().stream().filter(type -> type.qualifiedName().equals("Owner.Nested")).findFirst().orElseThrow();
    assertEquals(Set.of(JavaTypeModifier.PRIVATE, JavaTypeModifier.STATIC, JavaTypeModifier.FINAL), nested.modifiers());
    assertEquals("type:Owner", nested.enclosingType().orElseThrow().declarationKey().orElseThrow());
  }

  @Test
  void givenAnnotationsInTypeHeaders_whenAnalysisRuns_thenTypedLocationsComplete() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("Annotated.java"), """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target({ElementType.TYPE, ElementType.TYPE_PARAMETER, ElementType.TYPE_USE}) @interface Marker { }
        @Marker class Annotated<@Marker T extends @Marker Number> { }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var input = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var run = new SourceAnalysisFixture().analyze(input);

    // Assert
    var codes = input.diagnostics().stream().map(diagnostic -> diagnostic.code()).collect(Collectors.toSet());
    assertTrue(codes.isEmpty(), () -> input.diagnostics().toString());
    assertEquals(AnalysisCompletion.COMPLETE, run.completion());
  }

  @Test
  void givenEnumConstantBodyAndAnonymousClassFieldInitializer_whenFrontendRuns_thenDiagnosticsRetainDistinctTaxonomyAndOrigins()
      throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("AnonymousInitializers.java"), """
        enum Mode { SPECIAL { } }
        class FieldOwner {
          Runnable task = new Runnable() { public void run() { } };
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var repeated = frontend.analyze(temporaryDirectory, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    var enumDiagnostic = result.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-enum-constant-body")).findFirst().orElseThrow();
    var fieldDiagnostic = result.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-anonymous-class-field-initializer")).findFirst()
        .orElseThrow();
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-field-initializer")));
    assertEquals(1, enumDiagnostic.origins().size());
    assertEquals(1, fieldDiagnostic.origins().size());
    assertTrue(!enumDiagnostic.diagnosticKey().equals(fieldDiagnostic.diagnosticKey()));
    var repeatedEnum = repeated.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-enum-constant-body")).findFirst().orElseThrow();
    var repeatedField = repeated.diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals("frontend.unsupported-anonymous-class-field-initializer")).findFirst()
        .orElseThrow();
    assertEquals(enumDiagnostic.diagnosticKey(), repeatedEnum.diagnosticKey());
    assertEquals(fieldDiagnostic.diagnosticKey(), repeatedField.diagnosticKey());
    var content = result.snapshot().sourceFiles().getFirst().content();
    assertEquals("SPECIAL { }", sourceText(content, enumDiagnostic.origins().getFirst()));
    assertTrue(sourceText(content, fieldDiagnostic.origins().getFirst()).contains("public void run()"));
  }

  private static MethodSyntax syntax(SyntaxAnalysisInput result, String methodName) {
    var method = result.program().methods().stream().filter(candidate -> candidate.name().equals(methodName)).findFirst().orElseThrow();
    return result.methodSyntax(method.methodKey()).orElseThrow();
  }

  private static Set<String> localDeclarationKeys(SyntaxAnalysisInput result, Set<String> names) {
    return result.methodSyntax().stream().flatMap(method -> method.root().descendantsAndSelf().stream())
        .filter(node -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)
        .filter(node -> names.contains(node.value(SyntaxValue.LocalDeclaration.class).name()))
        .map(node -> node.requiredReference().symbolKey().orElseThrow()).collect(Collectors.toSet());
  }

  private static SyntaxNode assertSourceLocal(SyntaxAnalysisInput input, MethodSyntax method, SyntaxNode executionLocal) {
    var aggregate = input.aggregate().orElseThrow();
    var declarations = aggregate.nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration declaration
            && declaration.declarationKey().equals(method.methodKey())).toList();
    assertEquals(1, declarations.size());
    var sourceMethod = declarations.getFirst();
    var sourceBody = sourceMethod.requiredChild(SyntaxRole.BODY);
    var matches = sourceBody.descendantsAndSelf().stream()
        .filter(node -> node.sourceFileKey().equals(executionLocal.sourceFileKey())
            && node.sourceKind() == executionLocal.sourceKind() && node.syntaxKind() == executionLocal.syntaxKind()
            && node.origin().equals(executionLocal.origin()) && node.value().equals(executionLocal.value())
            && node.type().equals(executionLocal.type()) && node.semanticReference().equals(executionLocal.semanticReference()))
        .toList();
    assertEquals(1, matches.size());
    var local = matches.getFirst();
    assertEquals(local, aggregate.node(local.nodeId()).orElseThrow());
    var executionSteps = executionLocal.structuralPath().steps();
    var executionRootSteps = method.root().structuralPath().steps();
    var sourceSteps = local.structuralPath().steps();
    var sourceRootSteps = sourceBody.structuralPath().steps();
    assertEquals(executionRootSteps, executionSteps.subList(0, executionRootSteps.size()));
    assertEquals(sourceRootSteps, sourceSteps.subList(0, sourceRootSteps.size()));
    assertEquals(executionSteps.subList(executionRootSteps.size(), executionSteps.size()),
        sourceSteps.subList(sourceRootSteps.size(), sourceSteps.size()));
    var owner = aggregate.writtenSourceAssociation(sourceMethod.nodeId()).orElseThrow();
    var bodyOwner = aggregate.writtenSourceAssociation(sourceBody.nodeId()).orElseThrow();
    var localOwner = aggregate.writtenSourceAssociation(local.nodeId()).orElseThrow();
    assertEquals(owner.ownerPath().child(SourceOwnerPath.Role.BODY, 0), bodyOwner.ownerPath());
    assertTrue(bodyOwner.ownerPath().isAncestorOf(localOwner.ownerPath()));
    assertEquals(local.nodeId(), localOwner.nodeKey());
    assertEquals(executionLocal.sourceFileKey(), localOwner.sourceFileKey());
    assertEquals(executionLocal.sourceKind(), localOwner.sourceKind());
    assertEquals(executionLocal.origin(), localOwner.origin());
    assertSame(sourceBody, method.root());
    assertSame(local, executionLocal);
    for (var node : method.root().descendantsAndSelf()) {
      assertSame(aggregate.node(node.nodeId()).orElseThrow(), node);
    }
    return local;
  }

  private static String sourceText(String content, SourceSpan origin) {
    return content.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
  }
}
