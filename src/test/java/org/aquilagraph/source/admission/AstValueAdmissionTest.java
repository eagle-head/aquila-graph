package org.aquilagraph.source.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.AstContractException;
import org.junit.jupiter.api.Test;

class AstValueAdmissionTest {
  @Test
  void givenEmptyCapture_whenCopiedValuesAreAdmitted_thenTheImmutableReaderIsEmpty() {
    // Arrange
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of());

    // Act
    var aggregate = AstValueAdmission.admit(CopiedAstValues.empty(snapshot));

    // Assert
    assertSame(snapshot, aggregate.snapshot());
    assertTrue(aggregate.roots().isEmpty());
    assertTrue(aggregate.methodBodyKeys().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> aggregate.nodes().clear());
  }

  @Test
  void givenMutableContributionList_whenCopiedValuesAreCreated_thenLaterMutationCannotChangeAdmission() {
    // Arrange
    var source = SourceFile.fromContent(Path.of("Source.java"), " ");
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(source));
    var root = root(source);
    var sources = new ArrayList<>(List.of(contribution(root)));
    var values = new CopiedAstValues(snapshot, sources, List.of(), List.of(), List.of(), List.of());
    sources.clear();

    // Act
    var aggregate = AstValueAdmission.admit(values);

    // Assert
    assertEquals(List.of(root.nodeId()), aggregate.orderedNodeKeys());
    assertSame(root, aggregate.node(root.nodeId()).orElseThrow());
  }

  @Test
  void givenDuplicateContributions_whenCompleteValuesAreAdmitted_thenNoAggregateIsReturned() {
    // Arrange
    var source = SourceFile.fromContent(Path.of("Source.java"), " ");
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(source));
    var contribution = contribution(root(source));
    var values = new CopiedAstValues(snapshot, List.of(contribution, contribution), List.of(), List.of(), List.of(), List.of());

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(values));

    // Assert
    assertTrue(exception.getMessage().contains("Duplicate AST source contribution"));
    assertInstanceOf(AstContractException.class, exception);
    assertInstanceOf(IllegalArgumentException.class, exception.getCause());
  }

  @Test
  void givenCapturedSourceWithoutRoot_whenCompleteValuesAreAdmitted_thenMissingSourceIsRejected() {
    // Arrange
    var source = SourceFile.fromContent(Path.of("Source.java"), " ");
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(source));

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> AstValueAdmission.admit(CopiedAstValues.empty(snapshot)));

    // Assert
    assertTrue(exception.getMessage().contains("Missing AST source root"));
    assertTrue(exception.getMessage().contains(source.sourceFileKey()));
  }

  private static SyntaxNode root(SourceFile source) {
    return SyntaxNode.source(source.sourceFileKey(), SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY), List.of(), Optional.empty(),
        new SourceSpan(source.sourceFileKey(), 0, source.content().length()));
  }

  private static SyntaxSourceContribution contribution(SyntaxNode root) {
    return new SyntaxSourceContribution(root.sourceFileKey(), root, List.of(root), List.of(), List.of());
  }
}
