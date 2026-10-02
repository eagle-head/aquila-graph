package org.aquilagraph.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.EnumConstructorContextDecision.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnumConstructorContextAdmissionTest {
  private static final JavaTypeIdentity STRING = new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String");
  private static final JavaTypeIdentity INT = new JavaTypeIdentity(JavaTypeCategory.INT, "int", "int");

  @TempDir
  Path sourceRoot;

  @Test
  void givenCopiedEnumContext_whenCallerClearsInputList_thenSealedQueryAndSourceIdentityRemainStable() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST, SECOND }", "<init>()");
    var context = fixture.context();
    var contexts = new ArrayList<>(List.of(context));
    var builder = fixture.builder(fixture.syntax().root(), fixture.arity(), contexts);
    contexts.clear();

    // Act
    var aggregate = builder.seal();
    var view = aggregate.methodSyntax(fixture.syntax().methodKey()).orElseThrow();

    // Assert
    assertSame(context, view.enumConstructorContext(fixture.call().nodeId()).orElseThrow());
    assertEquals(SyntaxReadiness.LOWERABLE, view.readiness());
    assertSame(fixture.syntax().root(), view.root());
    assertEquals(fixture.syntax(), view);
    assertEquals(fixture.syntax().hashCode(), view.hashCode());
    assertEquals(fixture.aggregate().nodeTuples(), aggregate.nodeTuples());
    assertEquals(fixture.aggregate().rangeTuples(), aggregate.rangeTuples());
  }

  @Test
  void givenMissingEnumSuperclassContextAndApplicability_whenBodyIsSealed_thenReadinessRemainsUnresolved() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST, SECOND }", "<init>()");

    // Act
    var view = fixture.builder(fixture.syntax().root(), Optional.empty(), List.of()).seal()
        .methodSyntax(fixture.syntax().methodKey()).orElseThrow();

    // Assert
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
    assertTrue(view.enumConstructorContext(fixture.call().nodeId()).isEmpty());
    assertTrue(view.invocationArity(fixture.call().nodeId()).isEmpty());
  }

  @Test
  void givenSameEnumThisDelegationWithoutContext_whenOrdinaryArityIsPresent_thenReadinessStillRefusesMissingForwarding() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST; Status() { this(1); } Status(int value) {} }", "<init>()");

    // Act
    var view = fixture.builder(fixture.syntax().root(), fixture.arity(), List.of()).seal()
        .methodSyntax(fixture.syntax().methodKey()).orElseThrow();

    // Assert
    assertEquals(1, view.invocationArity(fixture.call().nodeId()).orElseThrow().formalCount());
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
    assertTrue(view.enumConstructorContext(fixture.call().nodeId()).isEmpty());
  }

  @Test
  void givenEnumContextWithoutSelectedApplicability_whenBodyIsSealed_thenContextDoesNotInventAnArityDecision() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");

    // Act
    var view = fixture.builder(fixture.syntax().root(), Optional.empty(), List.of(fixture.context())).seal()
        .methodSyntax(fixture.syntax().methodKey()).orElseThrow();

    // Assert
    assertTrue(view.enumConstructorContext(fixture.call().nodeId()).isPresent());
    assertTrue(view.invocationArity(fixture.call().nodeId()).isEmpty());
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
  }

  @Test
  void givenEnumSuperclassArityWithoutContext_whenSealed_thenCopiedApplicabilityRemainsUnresolvedUntilContextIsAvailable() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");

    // Act
    var view = fixture.builder(fixture.syntax().root(), fixture.arity(), List.of()).seal()
        .methodSyntax(fixture.syntax().methodKey()).orElseThrow();

    // Assert
    assertEquals(2, view.invocationArity(fixture.call().nodeId()).orElseThrow().formalCount());
    assertTrue(view.enumConstructorContext(fixture.call().nodeId()).isEmpty());
    assertEquals(SyntaxReadiness.UNRESOLVED_SEMANTICS, view.readiness());
  }

  @Test
  void givenEnumSuperclassContextWithWrongFormalCount_whenSealed_thenContradictoryBindingRejects() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");
    var zero = new InvocationArityDecision(fixture.syntax().methodKey(), fixture.call().nodeId(), InvocationArityMode.FIXED, 0,
        Optional.empty(), Optional.empty(), Optional.empty());

    // Act
    var wrongCount = assertThrows(IllegalArgumentException.class,
        () -> fixture.builder(fixture.syntax().root(), Optional.of(zero), List.of(fixture.context())).seal());

    // Assert
    assertTrue(wrongCount.getMessage().contains("source argument count"));
  }

  @Test
  void givenDuplicateForeignMethodOrForeignNodeEnumContext_whenSealed_thenEachBindingRejects() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");
    var valid = fixture.context();
    var foreignMethod = new EnumConstructorContextDecision("type:Status#<init>(long)", valid.invocationNodeId(), valid.enumOwnerKey(),
        valid.selectedConstructorKey(), valid.target(), valid.nameType(), valid.ordinalType());
    var foreignNode = new EnumConstructorContextDecision(valid.methodKey(), "foreign", valid.enumOwnerKey(),
        valid.selectedConstructorKey(), valid.target(), valid.nameType(), valid.ordinalType());

    // Act
    var errors = List.of(List.of(valid, valid), List.of(foreignMethod), List.of(foreignNode)).stream()
        .map(contexts -> assertThrows(IllegalArgumentException.class,
            () -> fixture.builder(fixture.syntax().root(), fixture.arity(), contexts).seal())).toList();

    // Assert
    assertEquals(3, errors.size());
    assertTrue(errors.getFirst().getMessage().contains("Duplicate"));
    assertTrue(errors.subList(1, 3).stream().allMatch(error -> error.getMessage().contains("registered method and body")));
  }

  @Test
  void givenReversedInputTypesOrDifferentSelectedOwner_whenContextIsCreated_thenEachLocalContradictionRejects() {
    // Arrange
    var owner = "type:Status";
    var method = owner + "#<init>()";
    var selected = "type:java.lang.Enum#<init>(java.lang.String,int)";

    // Act
    var reversed = assertThrows(IllegalArgumentException.class,
        () -> new EnumConstructorContextDecision(method, "call", owner, selected, Target.ENUM_SUPERCLASS, INT, STRING));
    var foreign = assertThrows(IllegalArgumentException.class,
        () -> new EnumConstructorContextDecision(method, "call", owner, "type:java.lang.Object#<init>()", Target.ENUM_SUPERCLASS, STRING, INT));

    // Assert
    assertTrue(reversed.getMessage().contains("ordered String name and int ordinal"));
    assertTrue(foreign.getMessage().contains("selected constructor owners"));
  }

  @Test
  void givenOrdinaryClassConstructorWithEnumContext_whenSealed_thenSourceOwnerCannotBeRelabeledAsEnum() throws IOException {
    // Arrange
    var fixture = fixture("class Status { Status() {} }", "<init>()");
    var context = new EnumConstructorContextDecision(fixture.syntax().methodKey(), fixture.call().nodeId(), "type:Status",
        "type:java.lang.Enum#<init>(java.lang.String,int)", Target.ENUM_SUPERCLASS, STRING, INT);

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> fixture.builder(fixture.syntax().root(), fixture.arity(), List.of(context)).seal());

    // Assert
    assertTrue(error.getMessage().contains("actual source enum"));
  }

  @Test
  void givenSourceOriginOnAnEnumSuperclassCall_whenContextIsRegistered_thenInsertedCallRuleRejectsTheWrittenOccurrence() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");
    var call = fixture.call();
    var written = SyntaxNode.source(call.sourceFileKey(), call.structuralPath(), call.sourceKind(), call.syntaxKind(), call.type(),
        call.value(), call.children(), call.semanticReference(), new SourceSpan(call.sourceFileKey(), 0, 1));
    var statement = fixture.syntax().root().requiredChild(SyntaxRole.STATEMENT);
    var changedStatement = SyntaxNode.withoutDirectSource(statement.sourceFileKey(), statement.structuralPath(), statement.sourceKind(),
        statement.syntaxKind(), statement.type(), statement.value(), List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, written)),
        statement.semanticReference());
    var root = fixture.syntax().root();
    var changedRoot = SyntaxNode.withoutDirectSource(root.sourceFileKey(), root.structuralPath(), root.sourceKind(), root.syntaxKind(),
        root.type(), root.value(), List.of(new SyntaxChild(SyntaxRole.STATEMENT, 0, changedStatement)), root.semanticReference());

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> fixture.builder(changedRoot, fixture.arity(), List.of(fixture.context())).seal());

    // Assert
    assertTrue(error.getMessage().contains("inserted zero-source-argument Enum call"));
  }

  @Test
  void givenEnumContextQueriedByForeignOrInvalidNode_whenSealedViewIsUsed_thenOptionalAndInvalidInputContractsRemainDistinct() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");
    var view = fixture.syntax();

    // Act
    var foreign = view.enumConstructorContext("foreign");
    var body = view.enumConstructorContext(view.root().nodeId());

    // Assert
    assertTrue(foreign.isEmpty());
    assertTrue(body.isEmpty());
    assertThrows(IllegalArgumentException.class, () -> view.enumConstructorContext(" "));
    assertThrows(IllegalArgumentException.class, () -> view.enumConstructorContext(null));
  }

  private Fixture fixture(String content, String signature) throws IOException {
    var source = SourceFile.fromContent(Path.of("Status.java"), content);
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(InputSnapshot.fromSources(sourceRoot, List.of(source)),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var aggregate = input.aggregate().orElseThrow();
    var method = input.program().methods().stream().filter(candidate -> candidate.signature().equals(signature)).findFirst().orElseThrow();
    var syntax = input.methodSyntax(method.methodKey()).orElseThrow();
    var call = syntax.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
    return new Fixture(aggregate, syntax, call);
  }

  private record Fixture(JavaAstAggregate aggregate, MethodSyntax syntax, SyntaxNode call) {
    EnumConstructorContextDecision context() {
      return syntax.enumConstructorContext(call.nodeId()).orElseThrow();
    }

    Optional<InvocationArityDecision> arity() {
      return syntax.invocationArity(call.nodeId());
    }

    JavaAstAggregate.Builder builder(SyntaxNode body, Optional<InvocationArityDecision> arity,
        List<EnumConstructorContextDecision> contexts) {
      var root = aggregate.roots().getFirst();
      var builder = JavaAstAggregate.builder(aggregate.snapshot()).add(new SyntaxSourceContribution(root.sourceFileKey(), root,
          aggregate.nodes(), aggregate.structuralEdges(), aggregate.semanticEdges()));
      aggregate.writtenSourceAssociations().forEach(builder::addWrittenSourceAssociation);
      aggregate.declarationMemberOrders().forEach(builder::addDeclarationMemberOrder);
      aggregate.declarationTypeEvidence().forEach(builder::addDeclarationTypeEvidence);
      var uses = syntax.root().descendantsAndSelf().stream().flatMap(node -> node.children().stream()
          .flatMap(child -> syntax.useDecision(node.nodeId(), child.role(), child.position()).stream())).toList();
      return builder.addMethodBody(syntax.methodKey(), body, aggregate.methodBodyOrigin(syntax.methodKey()).orElseThrow(), uses,
          arity.stream().toList(), List.of(), List.of(), contexts);
    }
  }
}
