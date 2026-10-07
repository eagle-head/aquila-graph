package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Corroborated superclass declaration types and optional compiler-inserted invocation provenance.
 *
 * @param superclassParameterTypes
 *          ordered actual public superclass constructor parameter types
 * @param insertedInvocation
 *          actual inserted selection, absent for written delegation
 */
public record ConstructorContext(List<JavaTypeIdentity> superclassParameterTypes, Optional<CompilerInsertedInvocation> insertedInvocation) {
  /** Copies ordered declaration facts without imposing a graph binding shape. */
  public ConstructorContext {
    superclassParameterTypes = List.copyOf(superclassParameterTypes);
    superclassParameterTypes.forEach(type -> DecisionValueValidation.requireValueType(type, "superclassParameterType"));
    Objects.requireNonNull(insertedInvocation, "insertedInvocation");
    var parameterCount = superclassParameterTypes.size();
    insertedInvocation.ifPresent(value -> value.applicability().applicability().knownValue().ifPresent(classification -> {
      if (classification.formalCount() != parameterCount) {
        throw new IllegalArgumentException("Inserted applicability must retain the actual selected superclass parameter count");
      }
    }));
  }
}
