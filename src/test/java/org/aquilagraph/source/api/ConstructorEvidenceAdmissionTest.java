package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class ConstructorEvidenceAdmissionTest {



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
    assertSame(context, view.constructorEvidence(fixture.call().nodeId()).orElseThrow());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(view));
    assertSame(fixture.syntax().root(), view.root());
    assertEquals(fixture.syntax().root(), view.root());
    assertEquals(fixture.syntax().evidence(), view.evidence());
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
    assertEquals(CfgReadiness.UNRESOLVED_SEMANTICS, CfgAdmission.classify(view));
    assertTrue(view.constructorEvidence(fixture.call().nodeId()).isEmpty());
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
    assertEquals(CfgReadiness.UNRESOLVED_SEMANTICS, CfgAdmission.classify(view));
    assertTrue(view.constructorEvidence(fixture.call().nodeId()).isEmpty());
  }

  @Test
  void givenEnumContextWithoutSelectedApplicability_whenBodyIsSealed_thenContextDoesNotInventAnArityDecision() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");

    // Act
    var view = fixture.builder(fixture.syntax().root(), Optional.empty(), List.of(fixture.context())).seal()
        .methodSyntax(fixture.syntax().methodKey()).orElseThrow();

    // Assert
    assertTrue(view.constructorEvidence(fixture.call().nodeId()).isPresent());
    assertTrue(view.invocationArity(fixture.call().nodeId()).isEmpty());
    assertEquals(CfgReadiness.LOWERABLE, CfgAdmission.classify(view));
  }

  @Test
  void givenEnumSuperclassArityWithoutContext_whenSealed_thenCopiedApplicabilityRemainsUnresolvedUntilContextIsAvailable() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");

    // Act
    var view = fixture.builder(fixture.syntax().root(), fixture.arity(), List.of()).seal()
        .methodSyntax(fixture.syntax().methodKey()).orElseThrow();

    // Assert
    assertTrue(view.invocationArity(fixture.call().nodeId()).isEmpty());
    assertTrue(view.constructorEvidence(fixture.call().nodeId()).isEmpty());
    assertEquals(CfgReadiness.UNRESOLVED_SEMANTICS, CfgAdmission.classify(view));
  }

  @Test
  void givenEnumSuperclassContextWithWrongFormalCount_whenSealed_thenContradictoryBindingRejects() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");
    var zero = new InvocationApplicability(fixture.syntax().methodKey(), fixture.call().nodeId(), InvocationApplicabilityMode.FIXED, 0,
        Optional.empty(), Optional.empty(), Optional.empty());

    // Act
    var wrongCount = assertThrows(IllegalArgumentException.class,
        () -> fixture.builder(fixture.syntax().root(), Optional.of(zero), List.of(fixture.context())).seal());

    // Assert
    assertTrue(wrongCount.getMessage().contains("actual selected superclass parameters"));
  }

  @Test
  void givenDuplicateForeignMethodOrForeignNodeEnumContext_whenSealed_thenEachBindingRejects() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");
    var valid = fixture.context();

    // Act
    var errors = List.of(assertThrows(IllegalArgumentException.class,
        () -> fixture.builder(fixture.syntax().root(), fixture.arity(), List.of(valid, valid)).seal()),
        assertThrows(IllegalArgumentException.class, () -> copied(valid, "type:Status#<init>(long)", valid.invocationNodeId())),
        assertThrows(IllegalArgumentException.class, () -> copied(valid, valid.scopeKey(), "foreign")));

    // Assert
    assertEquals(3, errors.size());
    assertTrue(errors.getFirst().getMessage().contains("Duplicate"));
    assertTrue(errors.subList(1, 3).stream().allMatch(error -> error.getMessage().contains("exact creating constructor evidence slot")));
  }

  @Test
  void givenWrongCreatingSlotOrDifferentInsertedSelection_whenContextIsCreated_thenEachLocalContradictionRejects() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");
    var value = fixture.context();
    var origin = value.corroboration().origin();

    // Act
    var reversed = assertThrows(IllegalArgumentException.class,
        () -> new ConstructorEvidence(value.scopeKey(), value.invocationNodeId(), value.ownerTypeKey(), value.selectedConstructorKey(), value.form(),
            EvidenceValue.known(value.corroboration().knownValue().orElseThrow(), new EvidenceOrigin(origin.scopeKey(), origin.nodeKey(), "foreign", origin.sourceRange()))));
    var foreign = assertThrows(IllegalArgumentException.class,
        () -> new ConstructorEvidence(value.scopeKey(), value.invocationNodeId(), value.ownerTypeKey(), "type:java.lang.Object#<init>()", value.form(), value.corroboration()));

    // Assert
    assertTrue(reversed.getMessage().contains("exact applicable creating slot"));
    assertTrue(foreign.getMessage().contains("selected constructor"));
  }

  @Test
  void givenOrdinaryClassConstructorWithEnumContext_whenSealed_thenSourceOwnerCannotBeRelabeledAsEnum() throws IOException {
    // Arrange
    var fixture = fixture("class Status {}", "<init>()");
    var context = new ConstructorEvidence(fixture.syntax().methodKey(), fixture.call().nodeId(), "type:Status",
        "type:java.lang.Enum#<init>(java.lang.String,int)", InvocationForm.SUPER_CONSTRUCTOR,
        EvidenceValue.missing(new EvidenceOrigin(fixture.syntax().methodKey(), fixture.call().nodeId(), ConstructorEvidence.SLOT,
            fixture.call().origin()), "Corroboration unavailable"));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> fixture.builder(fixture.syntax().root(), fixture.arity(), List.of(context)).seal());

    // Assert
    assertTrue(error.getMessage().contains("selected constructor reference"));
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
    var context = fixture.context();
    var evidence = context.corroboration();
    var payload = evidence.knownValue().orElseThrow();
    var insertion = payload.insertedInvocation().orElseThrow();
    var copiedInsertion = new CompilerInsertedInvocation(insertion.sourceFileKey(), insertion.compilerPath(), insertion.reference(), insertion.semantics(),
        new InvocationEvidence(EvidenceValue.missing(new EvidenceOrigin(context.scopeKey(), written.nodeId(), InvocationEvidence.SLOT, written.origin()), "Applicability unavailable")));
    var changedContext = new ConstructorEvidence(context.scopeKey(), context.invocationNodeId(), context.ownerTypeKey(),
        context.selectedConstructorKey(), context.form(), new EvidenceValue<>(evidence.state(), Optional.of(new ConstructorContext(payload.superclassParameterTypes(), Optional.of(copiedInsertion))),
            new EvidenceOrigin(context.scopeKey(), written.nodeId(), ConstructorEvidence.SLOT, written.origin()), evidence.explanation()));

    // Act
    var error = assertThrows(IllegalArgumentException.class,
        () -> fixture.builder(changedRoot, fixture.arity(), List.of(changedContext)).seal());

    // Assert
    assertTrue(error.getMessage().contains("inserted zero-source-argument call"));
  }

  @Test
  void givenEnumContextQueriedByForeignOrInvalidNode_whenSealedViewIsUsed_thenOptionalAndInvalidInputContractsRemainDistinct() throws IOException {
    // Arrange
    var fixture = fixture("enum Status { FIRST }", "<init>()");
    var view = fixture.syntax();

    // Act
    var foreign = view.constructorEvidence("foreign");
    var body = view.constructorEvidence(view.root().nodeId());

    // Assert
    assertTrue(foreign.isEmpty());
    assertTrue(body.isEmpty());
    assertThrows(IllegalArgumentException.class, () -> view.constructorEvidence(" "));
    assertThrows(IllegalArgumentException.class, () -> view.constructorEvidence(null));
  }

  private Fixture fixture(String content, String signature) throws IOException {
    var source = SourceFile.fromContent(Path.of("Status.java"), content);
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(InputSnapshot.fromSources(sourceRoot, List.of(source)),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var aggregate = input.aggregate();
    var method = input.program().methods().stream().filter(candidate -> candidate.signature().equals(signature)).findFirst().orElseThrow();
    var syntax = input.aggregate().methodSyntax(method.methodKey()).orElseThrow();
    var call = syntax.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
    return new Fixture(aggregate, syntax, call);
  }

  private static ConstructorEvidence copied(ConstructorEvidence value, String methodKey, String nodeKey) {
    var evidence = value.corroboration();
    return new ConstructorEvidence(methodKey, nodeKey, value.ownerTypeKey(), value.selectedConstructorKey(), value.form(),
        new EvidenceValue<>(evidence.state(), evidence.knownValue(),
            new EvidenceOrigin(methodKey, nodeKey, ConstructorEvidence.SLOT, evidence.origin().sourceRange()), evidence.explanation()));
  }

  private record Fixture(JavaAstAggregate aggregate, MethodSyntax syntax, SyntaxNode call) {
    ConstructorEvidence context() {
      return syntax.constructorEvidence(call.nodeId()).orElseThrow();
    }

    Optional<InvocationApplicability> arity() {
      return syntax.invocationArity(call.nodeId());
    }

    AggregateFixture builder(SyntaxNode body, Optional<InvocationApplicability> arity,
        List<ConstructorEvidence> contexts) {
      var root = aggregate.roots().getFirst();
      var builder = AggregateFixture.builder(aggregate.snapshot()).add(new SyntaxSourceContribution(root.sourceFileKey(), root,
          aggregate.nodes(), aggregate.structuralEdges(), aggregate.semanticEdges()));
      aggregate.writtenSourceAssociations().forEach(builder::addWrittenSourceAssociation);
      aggregate.declarationMemberOrders().forEach(builder::addDeclarationMemberOrder);
      aggregate.declarationTypeEvidence().forEach(builder::addDeclarationTypeEvidence);
      var invocations = arity.equals(arity()) ? syntax.evidence().invocations()
          : arity.stream().map(value -> InvocationEvidence.known(value, call.origin())).toList();
      var evidence = new ExecutionEvidence(syntax.evidence().uses(), invocations, syntax.evidence().staticMembers(), syntax.evidence().stringExpressions(), contexts);
      return builder.addMethodEvidenceBody(syntax.methodKey(), body, aggregate.methodBodyOrigin(syntax.methodKey()).orElseThrow(), evidence);
    }
  }
}
