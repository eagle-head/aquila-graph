package org.aquilagraph.source.admission;

import java.util.List;
import org.aquilagraph.source.internal.JavaAstAggregateBuilder;
import org.aquilagraph.source.internal.ExecutionDecisionRegistration;

import java.util.Objects;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.JavaAstAggregate;

/** Atomic admission of copied source values; mutable construction never escapes. */
public final class AstValueAdmission {
  private AstValueAdmission() {
  }

  /**
   * Validates the complete copied source set before exposing an immutable aggregate.
   *
   * @param values
   *          complete copied source values
   * @return admitted immutable aggregate
   * @throws AstContractException
   *           if supplied source values violate an intrinsic invariant
   */
  public static JavaAstAggregate admit(CopiedAstValues values) {
    Objects.requireNonNull(values, "values");
    try {
      var builder = new JavaAstAggregateBuilder(values.snapshot());
      values.sources().forEach(builder::add);
      values.bodies().forEach(body -> builder.addMethodBody(body.methodKey(), body.root(), body.origin(), body.uses(), body.invocations(),
          body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences()));
      values.scopes().forEach(scope -> builder.addDeclarationScope(scope.scope(), scope.root(),
          new ExecutionDecisionRegistration(scope.uses(), scope.invocations(), scope.staticAccesses(), scope.concatenations(), List.of()),
          scope.attributedOccurrences()));
      values.memberOrders().forEach(builder::addDeclarationMemberOrder);
      values.associations().forEach(builder::addWrittenSourceAssociation);
      values.declarationTypes().forEach(builder::addDeclarationTypeEvidence);
      return builder.seal();
    } catch (IllegalArgumentException exception) {
      throw new AstContractException(Objects.toString(exception.getMessage(), "Invalid copied source values"), exception);
    }
  }
}
