package org.aquilagraph.source.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StaticMemberKind;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticTarget.Declaration;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.CoverageActualFacts;
import org.aquilagraph.source.api.CoveragePropertyValue;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberInitializationAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenCompactFieldScopeWithForeignOwner_whenCopiedValuesAreAdmitted_thenActualCompactContainerCannotAuthorizeIt() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "int count = Math.abs(-1); void main() {}")));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_INITIALIZATION).result().orElseThrow();
    var values = CopiedAstValues.capture(original.aggregate());
    var field = values.scopes().getFirst();
    var foreign = new CopiedAstValues.Scope(new ExecutionScope.Field("type:Foreign#field:count", "type:Foreign"), field.root(),
        field.uses(), field.invocations(), field.staticAccesses(), field.concatenations(), field.attributedOccurrences());
    var copied = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), values.associations(),
        values.declarationTypes(), List.of(foreign));

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(copied));

    // Assert
    assertTrue(failure.getMessage().contains("scope") || failure.getMessage().contains("owned"));
  }

  @Test
  void givenCompactFieldAndScopeConsistentlyRetargetedToForeignType_whenAdmitted_thenDirectContainerOwnerRejectsTheAttachment() throws IOException {
    // Arrange
    var original = produce("int count; void main() {}");
    var changed = mapSyntax(original, node -> {
      if (!(node.value() instanceof SyntaxValue.VariableDeclaration declaration)) {
        return node;
      }
      var foreign = new SyntaxValue.VariableDeclaration(declaration.name(), "type:Foreign", "type:Foreign#field:count", declaration.kind(),
          declaration.writtenModifiers(), declaration.effectiveModifiers(), declaration.declaredType(), declaration.annotations(),
          declaration.type(), declaration.initializer(), declaration.argumentList(), declaration.arguments(), declaration.constantBody(),
          declaration.sharedDeclaratorKeys());
      var reference = node.semanticReference().map(value -> new SemanticReference(value.referenceKind(), value.status(),
          Optional.of("type:Foreign#field:count"), value.typeName(), value.typeAlternativeNames(), value.assignableToTypeNames()));
      return rebuild(node, node.syntaxKind(), foreign, node.children(), reference);
    });
    var field = changed.scopes().getFirst();
    var scope = new CopiedAstValues.Scope(new ExecutionScope.Field("type:Foreign#field:count", "type:Foreign"), field.root(),
        List.of(), List.of(), List.of(), List.of(), List.of());
    var sources = changed.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(), source.root(),
        source.nodes(), source.structuralEdges(), source.semanticEdges().stream().map(edge -> {
          if (!edge.sourceKey().equals(field.root().nodeId()) || !(edge.target() instanceof Declaration declaration)) {
            return edge;
          }
          var reference = declaration.reference();
          return new SyntaxSemanticEdge(edge.sourceKey(), edge.relation(), new Declaration(new SemanticReference(reference.referenceKind(),
              reference.status(), Optional.of("type:Foreign#field:count"), reference.typeName(), reference.typeAlternativeNames(),
              reference.assignableToTypeNames())));
        }).toList())).toList();
    var supplied = new CopiedAstValues(changed.snapshot(), sources, changed.bodies(), changed.memberOrders(),
        changed.associations(), changed.declarationTypes(), List.of(scope));

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("belong directly to its actual type owner"), failure.toString());
  }

  @Test
  void givenMissingFieldAssignmentTarget_whenCopiedValuesAreQualified_thenSourceIsIncompleteWithoutLosingSyntax() throws IOException {
    // Arrange
    var original = produce();
    var copied = transform(original, scope -> new CopiedAstValues.Scope(scope.scope(), scope.root(),
        scope.uses().stream().filter(use -> !use.consumerNodeId().equals(scope.root().nodeId())).toList(), scope.invocations(),
        scope.staticAccesses(), scope.concatenations(), scope.attributedOccurrences()));

    // Act
    var result = qualify(original, copied);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertEquals(original.aggregate().nodes(), result.aggregate().nodes());
    assertEquals(2, result.aggregate().executionScopes().stream().filter(ExecutionScope.Field.class::isInstance).count());
  }

  @Test
  void givenMissingSelectedArgumentTarget_whenCopiedValuesAreQualified_thenInvocationClassificationCannotSubstituteForRequiredFacts() throws IOException {
    // Arrange
    var original = produce();
    var copied = transform(original, scope -> new CopiedAstValues.Scope(scope.scope(), scope.root(),
        scope.uses().stream().filter(use -> use.role() != SyntaxRole.ARGUMENT).toList(), scope.invocations(), scope.staticAccesses(),
        scope.concatenations(), scope.attributedOccurrences()));

    // Act
    var result = qualify(original, copied);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertEquals(original.aggregate().nodes(), result.aggregate().nodes());
  }

  @Test
  void givenMissingOptionalConversions_whenCopiedValuesAreQualifiedAndRetained_thenRequiredTargetsRemainComplete() throws IOException {
    // Arrange
    var original = produce();
    var copied = transform(original, scope -> new CopiedAstValues.Scope(scope.scope(), scope.root(), scope.uses().stream()
        .map(use -> new ChildUseEvidence(use.scopeKey(), use.consumerNodeId(), use.role(), use.position(), use.sourceType(),
            use.selectedTarget(), EvidenceValue.missing(use.conversion().origin(), "Optional classification unavailable"), use.referenceCheck()))
        .toList(), scope.invocations(), scope.staticAccesses(), scope.concatenations(), scope.attributedOccurrences()));

    // Act
    var result = qualify(original, copied);
    var retained = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion());
    assertEquals(result.aggregate(), retained.aggregate());
    assertTrue(retained.aggregate().executionSyntax("type:Example#field:first").orElseThrow().evidence().uses().stream()
        .allMatch(use -> use.conversion().knownValue().isEmpty()));
  }

  @Test
  void givenMissingRequiredStaticOwner_whenCopiedValuesAreQualified_thenTheDeclarationScopeIsIncomplete() throws IOException {
    // Arrange
    var original = produce();
    var copied = transform(original, scope -> new CopiedAstValues.Scope(scope.scope(), scope.root(), scope.uses(), scope.invocations(),
        scope.staticAccesses().stream().map(member -> new StaticMemberEvidence(member.scopeKey(), member.accessNodeId(),
            member.selectedMemberKey(), member.kind(), EvidenceValue.missing(member.declaringOwner().origin(), "Required owner unavailable"),
            member.constantVariable())).toList(), scope.concatenations(), scope.attributedOccurrences()));

    // Act
    var result = qualify(original, copied);

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertEquals(original.aggregate().nodes(), result.aggregate().nodes());
  }

  @Test
  void givenAnotherFieldsScopeOnAnUnchangedAnchor_whenValuesAreAdmitted_thenIntrinsicOwnershipRejectsTheAttachment() throws IOException {
    // Arrange
    var original = produce();
    var values = CopiedAstValues.capture(original.aggregate());
    var first = values.scopes().getFirst();
    var foreign = new CopiedAstValues.Scope(new ExecutionScope.Field("type:Example#field:second", "type:Example"), first.root(),
        first.uses(), first.invocations(), first.staticAccesses(), first.concatenations(), first.attributedOccurrences());
    var scopes = values.scopes().stream().map(scope -> scope.equals(first) ? foreign : scope).toList();
    var copied = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), values.associations(),
        values.declarationTypes(), scopes);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(copied));

    // Assert
    assertTrue(failure.getMessage().contains("scope") || failure.getMessage().contains("owned"));
  }

  private AstResult produce() throws IOException {
    var source = "class Example { int first = Math.abs(-1); int second = Integer.MAX_VALUE; }";
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_INITIALIZATION).result().orElseThrow();
  }

  @TestFactory
  Stream<DynamicTest> givenEarlierInitializerAccessRetargetedToLaterBinding_whenCopiedValuesAreAdmitted_thenDeclarationOrderIsRejected() {
    return Stream.of("static", "").map(modifier -> DynamicTest.dynamicTest("givenLaterLocalIn" + (modifier.isEmpty() ? "Instance" : "Static") + "Initializer_whenRetargetedBeforeDeclaration_thenRejected", () -> {
    // Arrange
    var original = produce("class Example { " + modifier + " { int first=1; int second=first; int later=2; } }");
    var access = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS).findFirst().orElseThrow();
    var later = original.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local
        && local.name().equals("later")).findFirst().orElseThrow();
    var reference = access.requiredReference();
    var supplied = mapSyntax(original, node -> node.nodeId().equals(access.nodeId())
        ? rebuild(node, node.syntaxKind(), new SyntaxValue.SourceSymbol("later"), node.children(),
            Optional.of(new SemanticReference(reference.referenceKind(), reference.status(), later.requiredReference().symbolKey(),
                reference.typeName(), reference.typeAlternativeNames(), reference.assignableToTypeNames()))) : node);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("precede its binding declaration"), failure.toString());
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenValidDirectAndNestedInitializerBindings_whenCopiedValuesAreAdmitted_thenLexicalReferabilityIsPreserved() {
    return Stream.of("static", "").map(modifier -> DynamicTest.dynamicTest("givenValidLocalBindingsIn" + (modifier.isEmpty() ? "Instance" : "Static") + "Initializer_whenAdmitted_thenLexicalScopesArePreserved", () -> {
    // Arrange
    var original = produce("class Example { " + modifier + " { int first=1; int second=first; { int nested=second; } } }");

    // Act
    var admitted = AstValueAdmission.admit(CopiedAstValues.capture(original.aggregate()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(original.aggregate(), admitted);
    }));
  }

  @TestFactory
  Stream<DynamicTest> givenInitializerKindChangedWithMatchingScope_whenOriginalExpectationsAreReused_thenIndependentStaticFlagRejectsIt() {
    return Stream.of("static", "").map(modifier -> DynamicTest.dynamicTest("given" + (modifier.isEmpty() ? "Instance" : "Static") + "Initializer_whenKindChanged_thenIndependentCoverageRejectsCompleteness", () -> {
      // Arrange
      var original = produce("class Example { " + modifier + " {} }");
      var kind = modifier.isEmpty() ? SyntaxKind.STATIC_INITIALIZER_BLOCK : SyntaxKind.INSTANCE_INITIALIZER_BLOCK;
      var mapped = mapSyntax(original, node -> node.syntaxKind() == SyntaxKind.STATIC_INITIALIZER_BLOCK
          || node.syntaxKind() == SyntaxKind.INSTANCE_INITIALIZER_BLOCK
              ? rebuild(node, kind, node.value(), node.children(), node.semanticReference()) : node);
      var scopes = mapped.scopes().stream().map(scope -> {
        var owner = (ExecutionScope.Initializer) scope.scope();
        return new CopiedAstValues.Scope(new ExecutionScope.Initializer(owner.key(), owner.ownerKey(), owner.origin(), kind), scope.root(),
            scope.uses(), scope.invocations(), scope.staticAccesses(), scope.concatenations(), scope.attributedOccurrences());
      }).toList();
      var supplied = AstValueAdmission.admit(new CopiedAstValues(mapped.snapshot(), mapped.sources(), mapped.bodies(), mapped.memberOrders(),
          mapped.associations(), mapped.declarationTypes(), scopes));
      var context = original.coverage().validationEvidence().writtenInventory().contexts().stream()
          .filter(value -> value.address().role().equals("BlockTree::BlockTree#isStatic()") && value.origins().equals(List.of(scopes.getFirst().root().origin().orElseThrow())))
          .findFirst().orElseThrow();
      var expected = context.writtenValue().orElseThrow();
      var actual = CoverageActualFacts.capture(original.program(), Optional.of(supplied));
      var owned = actual.stream().filter(fact -> fact.identity().equals(scopes.getFirst().root().nodeId())).toList();

      // Act
      var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environment(),
          original.program(), Optional.of(supplied), original.diagnostics(), original.coverage()));

      // Assert
      assertTrue(failure.getMessage().contains("fact") || failure.getMessage().contains("source"), failure.toString());
      assertEquals(new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR,
          List.of(new CoveragePropertyValue.Item("item:0", Boolean.toString(!modifier.isEmpty()), CoveragePropertyValue.Provenance.WRITTEN))), expected);
      assertFalse(CoverageActualFacts.supportsProperty(context, expected, owned, actual, original.snapshot()));
    }));
  }

  private AstResult produce(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_INITIALIZATION).result().orElseThrow();
  }

  private static CopiedAstValues mapSyntax(AstResult result, UnaryOperator<SyntaxNode> change) {
    var values = CopiedAstValues.capture(result.aggregate());
    var roots = values.sources().stream().map(source -> mapNode(source.root(), change)).toList();
    var nodes = roots.stream().flatMap(root -> root.descendantsAndSelf().stream()).collect(Collectors.toMap(SyntaxNode::nodeId, node -> node));
    var sources = roots.stream().map(root -> {
      var original = values.sources().stream().filter(source -> source.sourceFileKey().equals(root.sourceFileKey())).findFirst().orElseThrow();
      var edges = new ArrayList<>(original.semanticEdges().stream().filter(edge -> nodes.containsKey(edge.sourceKey())
          && nodes.get(edge.sourceKey()).syntaxKind() != SyntaxKind.VARIABLE_ACCESS).toList());
      nodes.values().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS).forEach(node -> edges.addAll(node.semanticEdges()));
      return new SyntaxSourceContribution(root.sourceFileKey(), root, root.descendantsAndSelf(),
          root.descendantsAndSelf().stream().flatMap(node -> node.structuralEdges().stream()).toList(), edges);
    }).toList();
    var bodies = values.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), nodes.getOrDefault(body.root().nodeId(), body.root()),
        body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var scopes = values.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(), nodes.get(scope.root().nodeId()),
        scope.uses(), scope.invocations(), scope.staticAccesses(), scope.concatenations(), scope.attributedOccurrences())).toList();
    return new CopiedAstValues(values.snapshot(), sources, bodies, values.memberOrders(),
        values.associations().stream().filter(association -> nodes.containsKey(association.nodeKey())).toList(), values.declarationTypes(), scopes);
  }

  private static SyntaxNode mapNode(SyntaxNode node, UnaryOperator<SyntaxNode> change) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), mapNode(child.node(), change))).toList();
    return change.apply(rebuild(node, node.syntaxKind(), node.value(), children, node.semanticReference()));
  }

  private static SyntaxNode rebuild(SyntaxNode node, SyntaxKind kind, SyntaxValue value, List<SyntaxChild> children,
      Optional<SemanticReference> reference) {
    return node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), kind, node.type(), value, children, reference, node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), kind, node.type(), value, children, reference);
  }

  @Test
  void givenMissingOptionalApplicabilityAndConstantClassifications_whenQualified_thenRequiredTargetsAndStaticOwnersStillEstablishCompleteSource() throws IOException {
    // Arrange
    var original = produce();
    var copied = transform(original, scope -> new CopiedAstValues.Scope(scope.scope(), scope.root(), scope.uses(),
        scope.invocations().stream().map(invocation -> new InvocationEvidence(
            EvidenceValue.missing(invocation.applicability().origin(), "Optional applicability unavailable"))).toList(),
        scope.staticAccesses().stream().map(member -> new StaticMemberEvidence(member.scopeKey(), member.accessNodeId(),
            member.selectedMemberKey(), member.kind(), member.declaringOwner(),
            member.kind() == StaticMemberKind.FIELD_READ
                ? EvidenceValue.missing(member.constantVariable().origin(), "Optional constant classification unavailable")
                : member.constantVariable())).toList(),
        scope.concatenations(), scope.attributedOccurrences()));

    // Act
    var result = qualify(original, copied);

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion());
    assertEquals(original.aggregate().nodes(), result.aggregate().nodes());
    assertEquals(result.aggregate(), AstCodec.decode(AstCodec.encode(result), result.identity()).aggregate());
  }

  @Test
  void givenAnotherFieldsRangeOnSelectedUseEvidence_whenValuesAreAdmitted_thenIntrinsicOriginCorrelationRejectsIt() throws IOException {
    // Arrange
    var original = produce();
    var foreignRange = original.aggregate().executionSyntax("type:Example#field:second").orElseThrow().root().origin();
    var copied = transform(original, scope -> {
      if (!scope.scope().key().equals("type:Example#field:first")) {
        return scope;
      }
      var uses = scope.uses().stream().map(use -> new ChildUseEvidence(use.scopeKey(), use.consumerNodeId(), use.role(), use.position(),
          use.sourceType(), use.selectedTarget(), EvidenceValue.missing(
              new EvidenceOrigin(use.scopeKey(), use.consumerNodeId(), use.conversion().origin().slot(), foreignRange), "Supplied foreign range"),
          use.referenceCheck())).toList();
      return new CopiedAstValues.Scope(scope.scope(), scope.root(), uses, scope.invocations(), scope.staticAccesses(),
          scope.concatenations(), scope.attributedOccurrences());
    });

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(copied));

    // Assert
    assertTrue(failure.getMessage().contains("range"));
  }

  @TestFactory
  Stream<DynamicTest> givenOneInitializationFamilyIsRemoved_whenOriginalCapturedExpectationsAreReused_thenEachMissingOutputIsRejected() {
    return Stream.of(SyntaxKind.FIELD_DECLARATION, SyntaxKind.STATIC_INITIALIZER_BLOCK, SyntaxKind.INSTANCE_INITIALIZER_BLOCK)
        .map(removedKind -> DynamicTest.dynamicTest("given" + removedKind + "Contents_whenOmitted_thenIndependentCoverageDetectsMissingOutput", () -> {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { int field = 1; static { int value = 2; } { int other = 3; } }")));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_INITIALIZATION).result().orElseThrow();
    var values = CopiedAstValues.capture(original.aggregate());
    var roots = values.sources().stream().map(source -> omitInitialization(source.root(), removedKind)).toList();
    var nodes = roots.stream().flatMap(root -> root.descendantsAndSelf().stream()).collect(Collectors.toMap(SyntaxNode::nodeId, node -> node));
    var sources = roots.stream().map(root -> new SyntaxSourceContribution(root.sourceFileKey(), root, root.descendantsAndSelf(),
        root.descendantsAndSelf().stream().flatMap(node -> node.structuralEdges().stream()).toList(),
        values.sources().stream().filter(source -> source.sourceFileKey().equals(root.sourceFileKey())).findFirst().orElseThrow()
            .semanticEdges().stream().filter(edge -> nodes.containsKey(edge.sourceKey())).toList())).toList();
    var scopes = values.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(), nodes.get(scope.root().nodeId()),
        scope.uses().stream().filter(use -> nodes.containsKey(use.consumerNodeId()) && nodes.get(use.consumerNodeId()).children().stream()
            .anyMatch(child -> child.role() == use.role() && child.position() == use.position())).toList(),
        scope.invocations().stream().filter(value -> nodes.containsKey(value.consumerNodeId())).toList(),
        scope.staticAccesses().stream().filter(value -> nodes.containsKey(value.accessNodeId())).toList(),
        scope.concatenations().stream().filter(value -> nodes.containsKey(value.binaryNodeId())).toList(),
        scope.attributedOccurrences().stream().filter(address -> nodes.containsKey(address.nodeKey())).toList())).toList();
    var associations = values.associations().stream().filter(association -> nodes.containsKey(association.nodeKey())).toList();
    var supplied = AstValueAdmission.admit(new CopiedAstValues(values.snapshot(), sources, values.bodies(), values.memberOrders(),
        associations, values.declarationTypes(), scopes));

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environment(),
        original.program(), Optional.of(supplied), original.diagnostics(), original.coverage()));

    // Assert
    assertTrue(failure.getMessage().contains("source") || failure.getMessage().contains("fact"));
    assertEquals(original.coverage().validationEvidence().writtenInventory(), original.coverage().validationEvidence().attributedExpectations().writtenInventory());
    assertEquals(2, supplied.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LITERAL).count());
    if (removedKind != SyntaxKind.FIELD_DECLARATION) {
      var context = original.coverage().validationEvidence().writtenInventory().contexts().stream()
          .filter(value -> value.address().role().equals("BlockTree::BlockTree#getStatements()")
              && value.origins().equals(List.of(scopes.stream().filter(scope -> scope.root().syntaxKind() == removedKind)
                  .findFirst().orElseThrow().root().origin().orElseThrow()))).findFirst().orElseThrow();
      var actual = CoverageActualFacts.capture(original.program(), Optional.of(supplied));
      var rootKey = scopes.stream().filter(scope -> scope.root().syntaxKind() == removedKind).findFirst().orElseThrow().root().nodeId();
      assertFalse(CoverageActualFacts.supportsProperty(context, context.writtenValue().orElseThrow(),
          actual.stream().filter(fact -> fact.identity().equals(rootKey)).toList(), actual, snapshot));
    }
    }));
  }

  private static SyntaxNode omitInitialization(SyntaxNode node, SyntaxKind removedKind) {
    var removedRoles = node.syntaxKind() != removedKind ? Set.<SyntaxRole>of()
        : Set.of(removedKind == SyntaxKind.FIELD_DECLARATION ? SyntaxRole.INITIALIZER : SyntaxRole.STATEMENT);
    var children = node.children().stream().filter(child -> !removedRoles.contains(child.role()))
        .map(child -> new SyntaxChild(child.role(), child.position(), omitInitialization(child.node(), removedKind))).toList();
    return children.equals(node.children()) ? node : SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(),
        node.syntaxKind(), node.type(), node.value(), children, node.semanticReference(), node.origin().orElseThrow());
  }

  private static CopiedAstValues transform(AstResult original, UnaryOperator<CopiedAstValues.Scope> transform) {
    var values = CopiedAstValues.capture(original.aggregate());
    return new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(), values.associations(),
        values.declarationTypes(), values.scopes().stream().map(transform).toList());
  }

  private static AstResult qualify(AstResult original, CopiedAstValues values) {
    var aggregate = AstValueAdmission.admit(values);
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
        original.diagnostics(), original.coverage());
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
  }
}
