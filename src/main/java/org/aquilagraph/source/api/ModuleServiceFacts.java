package org.aquilagraph.source.api;

import java.util.List;
import org.aquilagraph.source.api.SourceOwnerPath.Role;

/** Source-owned consistency and qualification of module service/provider operands. */
public final class ModuleServiceFacts {
  private ModuleServiceFacts() {
  }

  /**
   * Copies an independently selected declaration into an endpoint expectation.
   *
   * @param key
   *          exact module-qualified declaration key
   * @return scalar required-target evidence
   */
  public static CoveragePropertyValue targetValue(String key) {
    return new CoveragePropertyValue(CoveragePropertyValue.State.SCALAR,
        List.of(new CoveragePropertyValue.Item("module-type-target", key, CoveragePropertyValue.Provenance.WRITTEN)));
  }

  /**
   * Rejects conflicting known operand facts independently of their coverage disposition.
   *
   * @param evidence
   *          independent source obligations and attributed expectations
   * @param facts
   *          actual copied source facts
   */
  public static void validateKnownOperands(CoverageValidationEvidence evidence, CoverageActualFactView facts) {
    var contexts = evidence.writtenInventory().contexts().stream()
        .filter(context -> context.address().partition() == SourceContext.Partition.MODULE_ENDPOINT).toList();
    if (contexts.isEmpty()) {
      return;
    }
    for (var fact : facts) {
      if (!facts.isSource(fact, value -> value.syntaxKind() == SyntaxKind.MODULE_TYPE_REFERENCE)) {
        continue;
      }
      var syntax = facts.requiredSyntax(fact);
      var owner = operandOwner(syntax);
      for (var context : contexts) {
        if (!fact.sourceFileKey().equals(context.address().sourceFileKey()) || !owner.equals(context.address().ownerPath())) {
          continue;
        }
        var ownerMatches = syntax.sourceAssociation().filter(value -> value.ownerPath().equals(context.address().ownerPath())).isPresent();
        var originMatches = !context.origins().isEmpty() && fact.origin().stream().toList().equals(context.origins());
        var steps = syntax.structuralPath().steps();
        var role = "SERVICE_NAME".equals(context.address().role()) ? SyntaxRole.SERVICE_TYPE : SyntaxRole.PROVIDER_TYPE;
        if ((!context.origins().isEmpty() && !originMatches) || (syntax.sourceAssociation().isPresent() && !ownerMatches) || steps.isEmpty()
            || steps.getLast().role() != role || steps.getLast().position() != context.address().ordinal()) {
          throw new IllegalArgumentException("Written module operand contradicts its independent source association");
        }
        var expected = evidence.attributedExpectations().projections().stream()
            .filter(projection -> projection.address().equals(context.address())).map(AttributedCoverageExpectations.Projection::value)
            .findFirst().orElseThrow();
        if (expected.state() == CoveragePropertyValue.State.SCALAR
            && syntax.semanticReference().filter(reference -> reference.status() == ResolutionStatus.RESOLVED
                && reference.symbolKey().map(ModuleServiceFacts::targetValue).filter(expected::equals).isEmpty()).isPresent()) {
          throw new IllegalArgumentException("Resolved module operand contradicts its independent declaration target");
        }
      }
    }
  }

  private static SourceOwnerPath operandOwner(CoverageSyntaxFactValue syntax) {
    var steps = syntax.structuralPath().steps();
    if (steps.size() != 4 || steps.get(1).role() != SyntaxRole.MODULE_DECLARATION || steps.get(2).role() != SyntaxRole.MODULE_DIRECTIVE
        || (steps.getLast().role() != SyntaxRole.SERVICE_TYPE && steps.getLast().role() != SyntaxRole.PROVIDER_TYPE)) {
      throw new IllegalArgumentException("A module operand requires its exact structural directive slot");
    }
    var role = steps.getLast().role() == SyntaxRole.SERVICE_TYPE ? Role.SERVICE_NAME : Role.IMPLEMENTATION_NAME;
    return SourceOwnerPath.root().child(Role.MODULE_DECLARATION, steps.get(1).position()).child(Role.DIRECTIVE, steps.get(2).position())
        .child(role, steps.getLast().position());
  }

  /**
   * Reconciles an independent operand target with one exact actual written occurrence.
   *
   * @param context
   *          parsed operand obligation
   * @param expected
   *          attributed target
   * @param owned
   *          actual facts at that parsed address
   * @param facts
   *          complete actual fact view
   * @return whether role, origin, source address and typed declaration agree
   */
  public static boolean supports(SourceContext context, CoveragePropertyValue expected, List<CoverageFactReference> owned,
      CoverageActualFactView facts) {
    if (owned.size() != 1 || !owned.getFirst().origin().stream().toList().equals(context.origins())) {
      return false;
    }
    var syntax = facts.syntaxValues(owned.getFirst()).findFirst();
    if (syntax.isEmpty() || syntax.orElseThrow().syntaxKind() != SyntaxKind.MODULE_TYPE_REFERENCE) {
      return false;
    }
    var value = syntax.orElseThrow();
    var steps = value.structuralPath().steps();
    var role = "SERVICE_NAME".equals(context.address().role()) ? SyntaxRole.SERVICE_TYPE : SyntaxRole.PROVIDER_TYPE;
    return !steps.isEmpty() && steps.getLast().role() == role && steps.getLast().position() == context.address().ordinal()
        && value.sourceAssociation().filter(association -> association.ownerPath().equals(context.address().ownerPath())).isPresent()
        && value.semanticReference()
            .filter(reference -> reference.status() == ResolutionStatus.RESOLVED && reference.declarationIdentity().isPresent()
                && reference.symbolKey().map(ModuleServiceFacts::targetValue).filter(expected::equals).isPresent())
            .isPresent();
  }

  /**
   * Checks all successor directive slots and their independent required coverage.
   *
   * @param input
   *          admitted source facts
   * @return whether any required endpoint or captured obligation is absent or unresolved
   */
  public static boolean hasGap(SyntaxAnalysisInput input) {
    var aggregate = input.aggregate().orElseThrow();
    var inventory = input.languageFeatureCoverageResult().validationEvidence().writtenInventory();
    for (var node : aggregate.nodes()) {
      if (node.value() instanceof SyntaxValue.Uses && node.children(SyntaxRole.SERVICE_TYPE).size() != 1) {
        return true;
      }
      if (node.value() instanceof SyntaxValue.Provides provides && (node.children(SyntaxRole.SERVICE_TYPE).size() != 1
          || node.children(SyntaxRole.PROVIDER_TYPE).size() != provides.providers().size())) {
        return true;
      }
      if (node.syntaxKind() != SyntaxKind.MODULE_TYPE_REFERENCE) {
        continue;
      }
      if (node.origin().isEmpty() || node.semanticReference()
          .filter(reference -> reference.status() == ResolutionStatus.RESOLVED && reference.declarationIdentity().isPresent()).isEmpty()) {
        return true;
      }
      var association = aggregate.writtenSourceAssociation(node.nodeId());
      if (association.isEmpty() || inventory.contexts().stream()
          .noneMatch(context -> context.address().partition() == SourceContext.Partition.MODULE_ENDPOINT
              && context.address().sourceFileKey().equals(node.sourceFileKey()) && context.origins().equals(node.origin().stream().toList())
              && association.orElseThrow().ownerPath().equals(context.address().ownerPath()))) {
        return true;
      }
    }
    return false;
  }
}
