package org.aquilagraph.source.api;

import java.util.Objects;

/**
 * Exact compiler attribution of an inserted superclass invocation, separate from written syntax.
 *
 * @param sourceFileKey
 *          captured containing source
 * @param compilerPath
 *          actual compiler occurrence address
 * @param reference
 *          selected public constructor reference
 * @param semantics
 *          selected public invocation semantics
 * @param applicability
 *          independently retained compiler applicability classification
 */
public record CompilerInsertedInvocation(String sourceFileKey, SyntaxStructuralPath compilerPath, SemanticReference reference,
    InvocationSemantics semantics, InvocationEvidence applicability) {
  /** Checks copied provenance and the supplied selected constructor shape. */
  public CompilerInsertedInvocation {
    DecisionValueValidation.requireKey(sourceFileKey, "sourceFileKey");
    Objects.requireNonNull(compilerPath, "compilerPath");
    Objects.requireNonNull(reference, "reference");
    Objects.requireNonNull(semantics, "semantics");
    Objects.requireNonNull(applicability, "applicability");
    if (reference.referenceKind() != ReferenceKind.CONSTRUCTOR || reference.status() != ResolutionStatus.RESOLVED
        || reference.symbolKey().isEmpty() || semantics.mode() != InvocationMode.CONSTRUCTOR
        || semantics.form() != InvocationForm.SUPER_CONSTRUCTOR) {
      throw new IllegalArgumentException("Inserted invocation provenance requires selected superclass constructor facts");
    }
  }
}
