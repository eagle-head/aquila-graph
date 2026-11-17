package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.CoverageActualFacts;
import org.aquilagraph.source.admission.CoverageActualFactView;
import org.aquilagraph.source.admission.CoverageContextIndex;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageFactIndexTest {
  @TempDir
  Path directory;

  @Test
  void givenIndependentSourcesAndNormalizedExpressions_whenOwnersAreSelected_thenExactOrderedFactsMatchTheOwnershipContract()
      throws IOException {
    // Arrange
    var result = produce();
    var facts = CoverageActualFacts.capture(result.program(), Optional.of(result.aggregate()));
    var contexts = result.coverage().validationEvidence().writtenInventory().contexts();
    assertTrue(contexts.stream().anyMatch(context -> context.address().partition() == SourceContext.Partition.WRITTEN_EXPRESSION));

    // Act
    var selected = contexts.stream().map(facts::ownedBy).toList();

    // Assert
    for (var ordinal = 0; ordinal < contexts.size(); ordinal++) {
      var context = contexts.get(ordinal);
      assertEquals(scanOwned(context, facts), selected.get(ordinal), context.address().toString());
      assertTrue(selected.get(ordinal).stream().allMatch(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey())));
    }
  }

  @Test
  void givenCopiedReferencesWithTheSameIdentity_whenValuesOrOriginsDiffer_thenExactLookupRejectsTheAlteredClaim() throws IOException {
    // Arrange
    var result = produce();
    var facts = CoverageActualFacts.capture(result.program(), Optional.of(result.aggregate()));
    var syntax = facts.stream().filter(fact -> fact.kind() == CoverageFactReference.Kind.SYNTAX && fact.origin().isPresent())
        .findFirst().orElseThrow();
    var other = facts.stream().filter(fact -> fact.kind() == CoverageFactReference.Kind.SYNTAX && !fact.value().equals(syntax.value()))
        .findFirst().orElseThrow();
    var copied = new CoverageFactReference(syntax.kind(), syntax.identity(), syntax.sourceFileKey(), syntax.value(), syntax.origin());
    var alteredValue = new CoverageFactReference(syntax.kind(), syntax.identity(), syntax.sourceFileKey(), other.value(), syntax.origin());
    var alteredOrigin = new CoverageFactReference(syntax.kind(), syntax.identity(), syntax.sourceFileKey(), syntax.value(), Optional.empty());
    var alteredKind = new CoverageFactReference(CoverageFactReference.Kind.METHOD, syntax.identity(), syntax.sourceFileKey(),
        syntax.value(), syntax.origin());

    // Act
    var accepted = facts.reference(copied);
    var rejected = List.of(alteredValue, alteredOrigin, alteredKind).stream().map(facts::reference).toList();

    // Assert
    assertEquals(Optional.of(syntax), accepted);
    assertEquals(facts.references().indexOf(syntax), facts.indexOf(copied));
    assertTrue(rejected.stream().allMatch(Optional::isEmpty));
    for (var claim : List.of(alteredValue, alteredOrigin, alteredKind)) {
      assertEquals(-1, facts.indexOf(claim));
      assertTrue(facts.sourceValue(claim).isEmpty());
      assertThrows(IllegalArgumentException.class, () -> facts.requiredSyntax(claim));
    }
  }

  @Test
  void givenDeclarationsWithSidecars_whenSelectingByDeclaration_thenOrderedEvidenceIsCompleteAndImmutable() throws IOException {
    // Arrange
    var result = produce();
    var facts = CoverageActualFacts.capture(result.program(), Optional.of(result.aggregate()));
    var type = facts.stream().filter(fact -> fact.kind() == CoverageFactReference.Kind.TYPE).findFirst().orElseThrow();
    var expected = facts.stream().filter(fact -> fact.sourceFileKey().equals(type.sourceFileKey())
        && (fact.identity().equals(type.identity())
            || fact.value() instanceof CoverageFactValue.MemberOrder order && order.value().ownerTypeKey().equals(type.identity())
            || fact.value() instanceof CoverageFactValue.DeclarationType value && value.value().ownerTypeKey().equals(type.identity())))
        .toList();

    // Act
    var selected = facts.forDeclaration(type.sourceFileKey(), type.identity());

    // Assert
    assertEquals(expected, selected);
    assertTrue(selected.stream().anyMatch(fact -> fact.kind() == CoverageFactReference.Kind.DECLARATION_MEMBER_ORDER));
    assertTrue(selected.stream().anyMatch(fact -> fact.kind() == CoverageFactReference.Kind.DECLARATION_TYPE));
    assertThrows(UnsupportedOperationException.class, selected::clear);
    assertTrue(facts.forDeclaration("another-source", type.identity()).isEmpty());
  }

  @Test
  void givenRepeatedIdentitiesInReverseOrder_whenSelectingFacts_thenOriginalOrdinalsAndSourceIsolationArePreserved() throws IOException {
    // Arrange
    var result = produce();
    var facts = CoverageActualFacts.capture(result.program(), Optional.of(result.aggregate()));
    var source = facts.get(0).sourceFileKey();
    var expected = facts.inSource(source);
    var identities = expected.stream().map(CoverageFactReference::identity).toList().reversed();
    var repeated = new ArrayList<>(identities);
    repeated.addAll(identities);

    // Act
    var selected = facts.withIdentities(source, repeated);

    // Assert
    assertEquals(expected, selected);
    assertTrue(facts.withIdentities("another-source", repeated).isEmpty());
    assertThrows(UnsupportedOperationException.class, selected::clear);
  }

  @Test
  void givenIndependentContextPartitions_whenNavigatingOwners_thenTypedAncestorsStayWithinTheirSourceAndInventoryOrder() throws IOException {
    // Arrange
    var result = produce();
    var evidence = result.coverage().validationEvidence();
    var contexts = evidence.writtenInventory().contexts();
    var index = new CoverageContextIndex(evidence.writtenInventory(), evidence.attributedExpectations(), Optional.of(result.aggregate()));

    // Act
    var ancestors = contexts.stream().map(index::ancestors).toList();

    // Assert
    for (var ordinal = 0; ordinal < contexts.size(); ordinal++) {
      var context = contexts.get(ordinal);
      var expected = contexts.stream().filter(candidate -> candidate.address().partition() == SourceContext.Partition.TREE
          && candidate.address().sourceFileKey().equals(context.address().sourceFileKey())
          && candidate.address().ownerPath().isAncestorOf(context.address().ownerPath()))
          .sorted((first, second) -> Integer.compare(second.address().ownerPath().size(), first.address().ownerPath().size())).toList();
      assertEquals(expected, ancestors.get(ordinal));
      assertEquals(Optional.of(context), index.context(context.address()));
    }
    for (var projection : evidence.attributedExpectations().projections()) {
      assertEquals(Optional.of(projection), index.findProjection(projection.address()));
    }
  }

  private AstResult produce() throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), List.of(
        SourceFile.fromContent(capturedPath(Path.of("First.java")),
            "record First(int value) { String render() { return -1 * 2 + \"a\" + \"b\"; } }"),
        SourceFile.fromContent(capturedPath(Path.of("Second.java")),
            "class Second { java.util.List<String> names; int sum(int x) { return (x + 1) + 2; } }")));
    var result = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES).result().orElseThrow();
    assertFalse(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()), () -> result.diagnostics().toString());
    return result;
  }

  private static List<CoverageFactReference> scanOwned(SourceContext context, CoverageActualFactView facts) {
    var sourceFacts = facts.stream().filter(fact -> fact.sourceFileKey().equals(context.address().sourceFileKey())
        && facts.isSource(fact, syntax -> syntax.sourceAssociation().filter(association ->
            association.ownerPath().equals(context.address().ownerPath())
                || association.normalizedExpression().filter(value -> normalizedOwner(context, value)).isPresent()).isPresent())).toList();
    var keys = sourceFacts.stream().map(facts::requiredSyntax).flatMap(syntax -> canonicalKeys(syntax.value()).stream()).toList();
    return facts.stream().filter(fact -> sourceFacts.contains(fact)
        || fact.sourceFileKey().equals(context.address().sourceFileKey()) && (keys.contains(fact.identity())
            || fact.value() instanceof CoverageFactValue.MemberOrder order && keys.contains(order.value().ownerTypeKey())
            || fact.value() instanceof CoverageFactValue.DeclarationType type && keys.contains(type.value().ownerTypeKey()))).toList();
  }

  private static boolean normalizedOwner(SourceContext context, NormalizedExpressionCorrespondence correspondence) {
    if (context.address().partition() == SourceContext.Partition.WRITTEN_EXPRESSION) {
      return correspondence.region().compilerOwner().equals(context.address().ownerPath());
    }
    return correspondence.occurrencePath().isEmpty()
        && correspondence.region().occurrences().stream()
            .noneMatch(occurrence -> occurrence.compilerOperand().filter(context.address().ownerPath()::equals).isPresent())
        && correspondence.observations().stream().anyMatch(property -> property.ownerPath().equals(context.address().ownerPath())
            && property.origin().stream().toList().equals(context.origins()));
  }

  private static List<String> canonicalKeys(SyntaxValue value) {
    return switch (value) {
      case SyntaxValue.TypeDeclaration declaration -> List.of(declaration.declarationKey());
      case SyntaxValue.ExecutableDeclaration declaration -> List.of(declaration.declarationKey());
      case SyntaxValue.VariableDeclaration declaration -> List.of(declaration.declarationKey());
      case SyntaxValue.ParameterDeclaration declaration -> List.of(declaration.declarationKey());
      case SyntaxValue.RecordComponentDeclaration declaration ->
        List.of(declaration.declarationKey(), declaration.counterparts().backingFieldKey());
      default -> List.of();
    };
  }
}

