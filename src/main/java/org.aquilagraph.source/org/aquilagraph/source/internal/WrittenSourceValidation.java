package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.CompilerExpressionAnchor;
import org.aquilagraph.source.api.DecimalMinimumOperand;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenExpressionConstants;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import java.util.Collection;

/** Proofs linking retained written facts to their exact captured occurrences and ranges. */
final class WrittenSourceValidation {
  private WrittenSourceValidation() {
  }

  static OccurrenceIndex<WrittenSourceAssociation> validate(Map<String, SyntaxNode> nodes, OccurrenceIndex<SyntaxNode> parents,
      Map<String, SourceFile> sources, Map<String, WrittenSourceAssociation> associations) {
    var byOccurrence = validateAssociations(nodes, sources, associations);
    validateRestrictedOperands(nodes.values(), parents, associations);
    validateEmptyDeclarationOwners(nodes.values(), parents, associations);
    validateAnnotationRanges(nodes.values());
    return byOccurrence;
  }

  private static OccurrenceIndex<WrittenSourceAssociation> validateAssociations(Map<String, SyntaxNode> nodes,
      Map<String, SourceFile> sources, Map<String, WrittenSourceAssociation> associations) {
    for (var node : nodes.values()) {
      if (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.range() == SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE
          && !associations.containsKey(node.nodeId())) {
        throw new IllegalArgumentException("A captured source envelope requires its original compilation unit association");
      }
    }
    var byOccurrence = new OccurrenceIndex.Builder<WrittenSourceAssociation>();
    for (var association : associations.values()) {
      var node = nodes.get(association.nodeKey());
      if (node == null || !node.sourceFileKey().equals(association.sourceFileKey()) || !associationRangeMatches(node, association, sources)
          || (node.sourceKind() != association.sourceKind()
              && !(node.value() instanceof SyntaxValue.Annotation && association.sourceKind() == JavaSourceKind.TYPE_ANNOTATION)
              && !normalizedAnnotationArgument(node, association, associations))) {
        throw new IllegalArgumentException("Written source associations must bind exact actual source nodes, kinds, and ranges");
      }
      if (association.normalizedExpression().isPresent()) {
        WrittenExpressionSemantics.validate(node);
        WrittenExpressionSemantics.validateCorrespondence(node, association.normalizedExpression().orElseThrow(), associations);
        validateDerivedLiteral(node, association.normalizedExpression().orElseThrow(), sources);
      }
      if (association.normalizedExpression().filter(value -> value.occurrencePath().isEmpty()).isPresent()) {
        var normalized = association.normalizedExpression().orElseThrow();
        if (!normalized.region().matches(node) || !node.type().equals(normalized.compilerType())) {
          throw new IllegalArgumentException("A normalized region requires complete written topology and corroborated result type");
        }
      }
      byOccurrence.putIfAbsent(node, association);
    }
    return byOccurrence.freeze();
  }

  private static void validateDerivedLiteral(SyntaxNode node, NormalizedExpressionCorrespondence correspondence,
      Map<String, SourceFile> sources) {
    if (!(node.value() instanceof SyntaxValue.Literal literal)) {
      return;
    }
    var annotation = node.structuralPath().isAnnotationValue();
    var origin = node.origin().orElseThrow();
    var source = Objects.requireNonNull(sources.get(node.sourceFileKey()), "derived literal source");
    var expected = annotation
        ? source.content().substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()))
        : literal.restrictedDecimal().map(DecimalMinimumOperand::magnitude)
            .orElseGet(() -> correspondence.derivedConstant().flatMap(WrittenExpressionConstants::text).orElse("null"));
    if (!literal.text().equals(expected)) {
      throw new IllegalArgumentException("A derived literal must retain its exact annotation spelling or executable scalar value");
    }
  }

  private static boolean normalizedAnnotationArgument(SyntaxNode node, WrittenSourceAssociation association,
      Map<String, WrittenSourceAssociation> associations) {
    if (!(node.value() instanceof SyntaxValue.AnnotationArgument argument) || argument.explicitName().isPresent()) {
      return false;
    }
    var value = node.requiredChild(SyntaxRole.VALUE);
    return Optional.ofNullable(associations.get(value.nodeId())).flatMap(WrittenSourceAssociation::normalizedExpression)
        .filter(correspondence -> correspondence.occurrencePath().isEmpty()
            && correspondence.compilerAnchor() instanceof CompilerExpressionAnchor.AnnotationSource
            && correspondence.compilerKind() == association.sourceKind()
            && correspondence.region().compilerOwner().equals(association.ownerPath()) && value.sourceKind() == node.sourceKind()
            && value.origin().equals(association.origin()))
        .isPresent();
  }

  private static void validateRestrictedOperands(Collection<SyntaxNode> nodes, OccurrenceIndex<SyntaxNode> parents,
      Map<String, WrittenSourceAssociation> associations) {
    for (var node : nodes) {
      if (node.value() instanceof SyntaxValue.Literal literal && literal.restrictedDecimal().isPresent()) {
        var association = associations.get(node.nodeId());
        if (association == null || association.normalizedExpression().isEmpty()) {
          throw new IllegalArgumentException("A restricted decimal operand requires its explicit grammar-derived correspondence");
        }
        WrittenExpressionSemantics.validateRestrictedParent(node, Optional.ofNullable(parents.get(node)));
      }
    }
  }

  private static void validateEmptyDeclarationOwners(Collection<SyntaxNode> nodes, OccurrenceIndex<SyntaxNode> parents,
      Map<String, WrittenSourceAssociation> associations) {
    for (var root : nodes) {
      if (!(root.value() instanceof SyntaxValue.CompilationUnit unit)) {
        continue;
      }
      var members = root
          .children(unit.form() == SyntaxValue.CompilationUnitForm.COMPACT ? SyntaxRole.COMPACT_MEMBER : SyntaxRole.TYPE_DECLARATION);
      var emptyOrdinal = 0;
      for (var member : members) {
        if (member.syntaxKind() != SyntaxKind.EMPTY) {
          continue;
        }
        var ordinal = emptyOrdinal;
        emptyOrdinal++;
        var association = associations.get(member.nodeId());
        if (unit.range() != SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE || association == null
            || !association.ownerPath().parent().equals(Optional.of(SourceOwnerPath.root()))
            || association.ownerPath().lastStep()
                .filter(step -> (step.role() == SourceOwnerPath.Role.EMPTY_DECLARATION && step.ordinal() == ordinal)
                    || (step.role() == SourceOwnerPath.Role.TYPE_DECLARATION && step.ordinal() == members.indexOf(member)))
                .isEmpty()) {
          throw new IllegalArgumentException("An empty unit declaration requires its exact parsed or captured unit owner and ordinal");
        }
      }
    }
    for (var node : nodes) {
      if (node.syntaxKind() != SyntaxKind.EMPTY || node.structuralPath().steps().getLast().role() != SyntaxRole.MEMBER) {
        continue;
      }
      var association = associations.get(node.nodeId());
      var parent = parents.get(node);
      if (parent == null || parent.syntaxKind() != SyntaxKind.TYPE_DECLARATION) {
        throw new IllegalArgumentException("An empty body declaration requires a source type parent");
      }
      var owner = associations.get(parent.nodeId());
      var ordinal = parent.children(SyntaxRole.MEMBER).stream().filter(member -> member.syntaxKind() == SyntaxKind.EMPTY).toList()
          .indexOf(node);
      if (association == null || owner == null || !association.ownerPath().parent().equals(Optional.of(owner.ownerPath()))
          || association.ownerPath().lastStep()
              .filter(step -> step.role() == SourceOwnerPath.Role.EMPTY_DECLARATION && step.ordinal() == ordinal).isEmpty()) {
        throw new IllegalArgumentException(
            "An empty body declaration requires its exact parsed type owner association and role-local ordinal");
      }
    }
  }

  private static boolean associationRangeMatches(SyntaxNode node, WrittenSourceAssociation association, Map<String, SourceFile> sources) {
    var source = sources.get(node.sourceFileKey());
    if (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.range() == SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE) {
      return source != null && node.sourceKind() == JavaSourceKind.COMPILATION_UNIT
          && association.sourceKind() == JavaSourceKind.COMPILATION_UNIT && association.ownerPath().isEmpty()
          && node.origin().filter(span -> span.startOffset() == 0 && span.endOffsetExclusive() == source.content().length()).isPresent()
          && association.origin().map(span -> span.endOffsetExclusive() <= source.content().length()).orElse(true);
    }
    if (node.origin().equals(association.origin())) {
      return true;
    }
    return source != null && node.value() instanceof SyntaxValue.CompilationUnit unit
        && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT && association.sourceKind() == JavaSourceKind.COMPILATION_UNIT
        && association.ownerPath().isEmpty()
        && node.origin().filter(span -> span.startOffset() == 0 && span.endOffsetExclusive() == source.content().length()).isPresent()
        && association.origin().map(span -> span.startOffset() >= 0 && span.endOffsetExclusive() <= source.content().length()).orElse(true);
  }

  private static void validateAnnotationRanges(Collection<SyntaxNode> nodes) {
    var writtenOrigins = new HashSet<SourceSpan>();
    for (var parent : nodes) {
      var parentRange = parent.origin().orElseThrow();
      var previousEnd = parentRange.startOffset();
      for (var child : parent.children()) {
        if (child.role() != SyntaxRole.ANNOTATION || !(child.node().value() instanceof SyntaxValue.Annotation)) {
          continue;
        }
        var annotation = child.node();
        var range = annotation.origin().orElseThrow();
        if (!writtenOrigins.add(range)) {
          throw new IllegalArgumentException("Duplicate AST written annotation range: " + annotation.nodeId());
        }
        if (range.length() == 0 || !range.sourceFileKey().equals(parentRange.sourceFileKey()) || range.startOffset() < previousEnd
            || range.endOffsetExclusive() > parentRange.endOffsetExclusive()) {
          throw new IllegalArgumentException("AST annotation range must be nonempty, contained, and ordered: " + annotation.nodeId());
        }
        previousEnd = range.endOffsetExclusive();
      }
    }
  }

}
