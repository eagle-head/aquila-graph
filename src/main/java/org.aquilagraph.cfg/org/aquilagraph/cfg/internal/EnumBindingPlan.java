package org.aquilagraph.cfg.internal;

import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;

/** Private ordered incoming enum bindings derived from corroborated declaration facts. */
record EnumBindingPlan(String enumOwnerKey, InvocationForm form, JavaTypeIdentity nameType, JavaTypeIdentity ordinalType) {
  static EnumBindingPlan from(ConstructorEvidence evidence) {
    if (!supports(evidence)) {
      throw new IllegalArgumentException("Enum binding requires ordered String name and int ordinal input types");
    }
    var types = evidence.corroboration().knownValue().orElseThrow().superclassParameterTypes();
    return new EnumBindingPlan(evidence.ownerTypeKey(), evidence.form(), types.getFirst(), types.getLast());
  }

  static boolean supports(ConstructorEvidence evidence) {
    return evidence.corroboration().knownValue().map(value -> {
      var types = value.superclassParameterTypes();
      return types.size() == 2 && types.getFirst().category() == JavaTypeCategory.DECLARED
          && "java.lang.String".equals(types.getFirst().erasedName()) && types.getLast().category() == JavaTypeCategory.INT;
    }).orElse(false);
  }
}
