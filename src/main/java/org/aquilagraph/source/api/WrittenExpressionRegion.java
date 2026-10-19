package org.aquilagraph.source.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Source-grammar obligations captured independently of expression emission.
 *
 * @param compilerOwner
 *          original parsed owner of the normalized region
 * @param origin
 *          complete written region in its captured source
 * @param occurrences
 *          ordered written operators, literals and intact operand anchors
 */
public record WrittenExpressionRegion(SourceOwnerPath compilerOwner, SourceSpan origin, List<Occurrence> occurrences) {
  /** Validates a unique, bounded, parent-first written topology. */
  public WrittenExpressionRegion {
    Objects.requireNonNull(compilerOwner, "compilerOwner");
    Objects.requireNonNull(origin, "origin");
    occurrences = List.copyOf(occurrences);
    var paths = new HashSet<List<SyntaxStructuralPath.Step>>();
    for (var occurrence : occurrences) {
      var path = occurrence.path();
      if (!paths.add(path) || !occurrence.origin().sourceFileKey().equals(origin.sourceFileKey())
          || occurrence.origin().startOffset() < origin.startOffset()
          || occurrence.origin().endOffsetExclusive() > origin.endOffsetExclusive()
          || (!path.isEmpty() && !paths.contains(path.subList(0, path.size() - 1)))) {
        throw new IllegalArgumentException("Written expression occurrences require unique bounded parent-first paths");
      }
    }
    if (occurrences.isEmpty() || !occurrences.getFirst().path().isEmpty() || !occurrences.getFirst().origin().equals(origin)) {
      throw new IllegalArgumentException("A written expression region requires its complete root occurrence");
    }
    for (var occurrence : occurrences) {
      if (occurrence.compilerOperand().isPresent()) {
        continue;
      }
      var children = occurrences.stream().filter(child -> child.path().size() == occurrence.path().size() + 1
          && child.path().subList(0, occurrence.path().size()).equals(occurrence.path())).map(child -> child.path().getLast()).toList();
      var required = switch (occurrence.sourceKind()) {
        case PLUS, MINUS ->
          List.of(new SyntaxStructuralPath.Step(SyntaxRole.LEFT_OPERAND, 0), new SyntaxStructuralPath.Step(SyntaxRole.RIGHT_OPERAND, 0));
        case UNARY_MINUS, UNARY_PLUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT ->
          List.of(new SyntaxStructuralPath.Step(SyntaxRole.OPERAND, 0));
        case INT_LITERAL, LONG_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL, STRING_LITERAL, CHAR_LITERAL, BOOLEAN_LITERAL, NULL_LITERAL ->
          List.<SyntaxStructuralPath.Step>of();
        default -> throw new IllegalArgumentException("An unanchored written occurrence must have a supported grammar category");
      };
      if (!children.equals(required)) {
        throw new IllegalArgumentException("Written expression grammar requires exact ordered operand roles");
      }
    }
  }

  /**
   * Tests the independent topology against one emitted region root.
   *
   * @param root
   *          emitted written root
   * @return whether every required occurrence has its exact kind, role and range
   */
  public boolean matches(SyntaxNode root) {
    for (var occurrence : occurrences) {
      var selected = Optional.of(root);
      for (var step : occurrence.path()) {
        selected = selected.flatMap(node -> node.children().stream()
            .filter(child -> child.role() == step.role() && child.position() == step.position()).map(SyntaxChild::node).findFirst());
      }
      if (selected.isEmpty() || selected.orElseThrow().sourceKind() != occurrence.sourceKind()
          || !selected.orElseThrow().origin().equals(Optional.of(occurrence.origin()))
          || (selected.orElseThrow().value() instanceof SyntaxValue.Literal literal
              && !literal.restrictedDecimal().equals(occurrence.restrictedDecimal()))) {
        return false;
      }
      if (occurrence.compilerOperand().isEmpty()) {
        var expected = occurrences.stream().filter(child -> child.path().size() == occurrence.path().size() + 1
            && child.path().subList(0, occurrence.path().size()).equals(occurrence.path())).count();
        if (selected.orElseThrow().children().size() != expected) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * One grammar-derived occurrence or intact compiler-backed operand.
   *
   * @param path
   *          region-relative typed roles, empty for the root
   * @param sourceKind
   *          written grammar category
   * @param origin
   *          exact written range
   * @param compilerOperand
   *          original parsed owner when the operand remains intact
   * @param restrictedDecimal
   *          independently captured context-restricted decimal magnitude
   * @param literalValue
   *          independently interpreted source-token value, when available
   */
  public record Occurrence(List<SyntaxStructuralPath.Step> path, JavaSourceKind sourceKind, SourceSpan origin,
      Optional<SourceOwnerPath> compilerOperand, Optional<DecimalMinimumOperand> restrictedDecimal,
      Optional<AnnotationScalar> literalValue) {
    /** Copies the path and requires consistent literal evidence and explicit provenance. */
    public Occurrence {
      path = List.copyOf(path);
      Objects.requireNonNull(sourceKind, "sourceKind");
      Objects.requireNonNull(origin, "origin");
      Objects.requireNonNull(compilerOperand, "compilerOperand");
      Objects.requireNonNull(restrictedDecimal, "restrictedDecimal");
      Objects.requireNonNull(literalValue, "literalValue");
      if (literalValue.isPresent() && (compilerOperand.isPresent() || !matchesLiteral(sourceKind, literalValue.orElseThrow()))) {
        throw new IllegalArgumentException("A captured literal scalar must match its grammar-derived source category");
      }
      if (restrictedDecimal.isPresent()) {
        var expectedKind = restrictedDecimal.orElseThrow().category() == JavaTypeCategory.INT
            ? JavaSourceKind.INT_LITERAL
            : JavaSourceKind.LONG_LITERAL;
        if (compilerOperand.isPresent() || sourceKind != expectedKind || literalValue.isPresent()) {
          throw new IllegalArgumentException(
              "A restricted decimal magnitude requires its exact integral literal category without a runtime scalar");
        }
      }
    }

    private static boolean matchesLiteral(JavaSourceKind kind, AnnotationScalar value) {
      return switch (kind) {
        case INT_LITERAL -> value instanceof AnnotationScalar.IntValue;
        case LONG_LITERAL -> value instanceof AnnotationScalar.LongValue;
        case FLOAT_LITERAL -> value instanceof AnnotationScalar.FloatValue;
        case DOUBLE_LITERAL -> value instanceof AnnotationScalar.DoubleValue;
        case CHAR_LITERAL -> value instanceof AnnotationScalar.CharValue;
        case BOOLEAN_LITERAL -> value instanceof AnnotationScalar.BooleanValue;
        case STRING_LITERAL -> value instanceof AnnotationScalar.StringValue;
        default -> false;
      };
    }
  }
}
