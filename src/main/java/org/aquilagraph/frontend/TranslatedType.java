package org.aquilagraph.frontend;

import java.util.List;
import java.util.Objects;
import org.aquilagraph.model.JavaType;
import org.aquilagraph.model.DeclarationMemberOrder;
import org.aquilagraph.model.DeclarationTypeEvidence;

record TranslatedType(List<JavaType> types, List<TranslatedMethodBody> methodBodies, List<DeclarationMemberOrder> memberOrders,
    List<DeclarationTypeEvidence> declarationTypes) {
  TranslatedType(List<JavaType> types, List<TranslatedMethodBody> methodBodies, List<DeclarationMemberOrder> memberOrders) {
    this(types, methodBodies, memberOrders, List.of());
  }

  TranslatedType {
    types = List.copyOf(Objects.requireNonNull(types, "types"));
    if (types.isEmpty()) {
      throw new IllegalArgumentException("A translated type tree must contain its root type");
    }
    methodBodies = List.copyOf(Objects.requireNonNull(methodBodies, "methodBodies"));
    memberOrders = List.copyOf(Objects.requireNonNull(memberOrders, "memberOrders"));
    declarationTypes = List.copyOf(Objects.requireNonNull(declarationTypes, "declarationTypes"));
  }
}
