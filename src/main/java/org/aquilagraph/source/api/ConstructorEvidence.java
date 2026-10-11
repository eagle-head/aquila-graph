package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Required constructor selection retained independently of optional inserted-call corroboration.
 *
 * @param scopeKey
 *          canonical containing constructor
 * @param invocationNodeId
 *          exact creating body occurrence
 * @param ownerTypeKey
 *          canonical containing declaration
 * @param selectedConstructorKey
 *          actual selected constructor
 * @param form
 *          actual delegation form
 * @param corroboration
 *          independent superclass declaration and insertion evidence
 */
public record ConstructorEvidence(String scopeKey, String invocationNodeId, String ownerTypeKey, String selectedConstructorKey,
    InvocationForm form, EvidenceValue<ConstructorContext> corroboration) {
  /** Exact creating slot for optional constructor corroboration. */
  public static final String SLOT = "constructor.context";

  /** Checks supplied owned slots without choosing runtime bindings or argument adaptation. */
  public ConstructorEvidence {
    DecisionValueValidation.requireKey(scopeKey, "scopeKey");
    DecisionValueValidation.requireKey(invocationNodeId, "invocationNodeId");
    DecisionValueValidation.requireKey(ownerTypeKey, "ownerTypeKey");
    DecisionValueValidation.requireKey(selectedConstructorKey, "selectedConstructorKey");
    Objects.requireNonNull(form, "form");
    Objects.requireNonNull(corroboration, "corroboration");
    var origin = corroboration.origin();
    if (!scopeKey.equals(origin.scopeKey()) || !invocationNodeId.equals(origin.nodeKey()) || !SLOT.equals(origin.slot())
        || (form != InvocationForm.SUPER_CONSTRUCTOR && form != InvocationForm.THIS_CONSTRUCTOR)
        || corroboration.state() == EvidenceState.NOT_APPLICABLE) {
      throw new IllegalArgumentException("Constructor evidence requires its exact applicable creating slot and delegation form");
    }
    corroboration.knownValue().flatMap(ConstructorContext::insertedInvocation).ifPresent(value -> {
      if (form != InvocationForm.SUPER_CONSTRUCTOR || !value.reference().symbolKey().equals(Optional.of(selectedConstructorKey))) {
        throw new IllegalArgumentException("Inserted invocation contradicts the selected constructor");
      }
      var applicabilityOrigin = value.applicability().applicability().origin();
      if (!scopeKey.equals(applicabilityOrigin.scopeKey()) || !invocationNodeId.equals(applicabilityOrigin.nodeKey())
          || !origin.sourceRange().equals(applicabilityOrigin.sourceRange())) {
        throw new IllegalArgumentException("Inserted applicability must retain the exact creating constructor evidence slot");
      }
    });
  }

  /**
   * Copies actual public declaration types and insertion provenance.
   *
   * @param scopeKey
   *          actual containing constructor
   * @param invocation
   *          actual correlated compiler occurrence
   * @param ownerTypeKey
   *          actual source owner
   * @param selectedConstructorKey
   *          selected public constructor
   * @param superclassParameterTypes
   *          ordered public superclass constructor types
   * @return independently retained constructor facts
   */
  public static ConstructorEvidence known(String scopeKey, SyntaxNode invocation, String ownerTypeKey, String selectedConstructorKey,
      List<JavaTypeIdentity> superclassParameterTypes) {
    var selection = invocation.value(SyntaxValue.Invocation.class);
    var applicability = new InvocationEvidence(
        EvidenceValue.missing(new EvidenceOrigin(scopeKey, invocation.nodeId(), InvocationEvidence.SLOT, invocation.origin()),
            "Compiler applicability has not yet been independently copied"));
    var inserted = selection.form() == InvocationForm.SUPER_CONSTRUCTOR && invocation.origin().isEmpty()
        ? Optional.of(new CompilerInsertedInvocation(invocation.sourceFileKey(), invocation.structuralPath(),
            invocation.requiredReference(), selection.semantics().orElseThrow(), applicability))
        : Optional.<CompilerInsertedInvocation>empty();
    var origin = new EvidenceOrigin(scopeKey, invocation.nodeId(), SLOT, invocation.origin());
    return new ConstructorEvidence(scopeKey, invocation.nodeId(), ownerTypeKey, selectedConstructorKey, selection.form(),
        EvidenceValue.known(new ConstructorContext(superclassParameterTypes, inserted), origin));
  }
}
