package org.aquilagraph.source.api;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * Explicit region corroboration for an occurrence without a direct compiler tree.
 *
 * @param region
 *          independently captured written topology
 * @param occurrencePath
 *          region-relative written occurrence
 * @param compilerAnchor
 *          actual normalization address in its governing compiler/source family
 * @param compilerKind
 *          actual normalization anchor category
 * @param compilerType
 *          observed normalization anchor type
 * @param derivedConstant
 *          justified scalar value of this written occurrence, when available
 * @param derivedConstantExpression
 *          explicit constant-expression classification of this occurrence
 * @param observations
 *          actual copied compiler occurrences, separate from written children
 */
public record NormalizedExpressionCorrespondence(WrittenExpressionRegion region, List<SyntaxStructuralPath.Step> occurrencePath,
    CompilerExpressionAnchor compilerAnchor, JavaSourceKind compilerKind, SyntaxType compilerType,
    Optional<AnnotationScalar> derivedConstant, Optional<Boolean> derivedConstantExpression, List<Observation> observations) {
  /** Requires a captured occurrence and defensively copies all observations. */
  public NormalizedExpressionCorrespondence {
    Objects.requireNonNull(region, "region");
    occurrencePath = List.copyOf(occurrencePath);
    Objects.requireNonNull(compilerAnchor, "compilerAnchor");
    if (compilerAnchor instanceof CompilerExpressionAnchor.AnnotationSource annotation
        && !annotation.ownerPath().equals(region.compilerOwner())) {
      throw new IllegalArgumentException("An annotation normalization anchor requires its exact physical source owner");
    }
    Objects.requireNonNull(compilerKind, "compilerKind");
    Objects.requireNonNull(compilerType, "compilerType");
    Objects.requireNonNull(derivedConstant, "derivedConstant");
    Objects.requireNonNull(derivedConstantExpression, "derivedConstantExpression");
    observations = List.copyOf(observations);
    var owners = new HashSet<SourceOwnerPath>();
    for (var observation : observations) {
      if (!owners.add(observation.ownerPath()) || !region.compilerOwner().isAncestorOf(observation.ownerPath())
          || observation.origin()
              .filter(span -> !span.sourceFileKey().equals(region.origin().sourceFileKey())
                  || span.startOffset() < region.origin().startOffset() || span.endOffsetExclusive() > region.origin().endOffsetExclusive())
              .isPresent()) {
        throw new IllegalArgumentException("Normalized compiler observations require unique original owners and bounded ranges");
      }
    }
    var observedKind = compilerKind;
    if (observations.stream().noneMatch(value -> value.ownerPath().equals(region.compilerOwner()) && value.sourceKind() == observedKind
        && value.origin().equals(Optional.of(region.origin())))) {
      throw new IllegalArgumentException("A normalized relation requires its actual bounded compiler-root observation");
    }
    var observedRoot = observations.stream().filter(value -> value.ownerPath().equals(region.compilerOwner())).findFirst().orElseThrow();
    if (!observedRoot.type().equals(compilerType) || (occurrencePath.isEmpty()
        && (!observedRoot.constant().equals(derivedConstant) || !observedRoot.constantExpression().equals(derivedConstantExpression)))) {
      throw new IllegalArgumentException("A normalized root requires its exact observed result type, value and constant status");
    }
    var selected = occurrencePath;
    if (region.occurrences().stream()
        .noneMatch(occurrence -> occurrence.path().equals(selected) && occurrence.compilerOperand().isEmpty())) {
      throw new IllegalArgumentException("Derived correspondence requires its actual captured grammar occurrence");
    }
  }

  /**
   * Returns the unique source address of this explicitly derived occurrence.
   *
   * @return actual region owner for the root, or a distinct grammar-owned child address
   */
  public SourceOwnerPath writtenOwner() {
    var owner = region.compilerOwner();
    if (!occurrencePath.isEmpty()) {
      owner = owner.child(SourceOwnerPath.Role.WRITTEN_EXPRESSION, 0);
      for (var step : occurrencePath) {
        owner = owner.child(
            step.role() == SyntaxRole.OPERAND ? SourceOwnerPath.Role.EXPRESSION : SourceOwnerPath.Role.valueOf(step.role().name()),
            step.position());
      }
    }
    return owner;
  }

  /**
   * An original compiler occurrence observed independently of written emission.
   *
   * @param ownerPath
   *          actual parsed compiler owner
   * @param sourceKind
   *          actual observed compiler category
   * @param origin
   *          actual observed compiler range
   * @param type
   *          actual copied attributed type
   * @param constant
   *          actual compiler-backed scalar value when available
   * @param constantExpression
   *          actual compiler-backed constant-expression classification
   */
  public record Observation(SourceOwnerPath ownerPath, JavaSourceKind sourceKind, Optional<SourceSpan> origin, SyntaxType type,
      Optional<AnnotationScalar> constant, Optional<Boolean> constantExpression) {
    /** Requires explicit owner, category and range availability. */
    public Observation {
      Objects.requireNonNull(ownerPath, "ownerPath");
      Objects.requireNonNull(sourceKind, "sourceKind");
      Objects.requireNonNull(origin, "origin");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(constant, "constant");
      Objects.requireNonNull(constantExpression, "constantExpression");
    }
  }
}
