package org.aquilagraph.frontend.internal;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/** Resolves the parameterized declaration view of a member's actual receiver. */
final class JavaMemberTypeView {
  private JavaMemberTypeView() {
  }

  static Optional<DeclaredType> owner(TypeMirror receiver, TypeElement owner, Types types) {
    return owner(receiver, owner, types, new HashSet<>());
  }

  private static Optional<DeclaredType> owner(TypeMirror receiver, TypeElement owner, Types types, Set<Element> visited) {
    var element = types.asElement(receiver);
    if (receiver instanceof DeclaredType declared && declared.asElement().equals(owner)) {
      return Optional.of(declared);
    }
    if (element != null && !visited.add(element)) {
      return Optional.empty();
    }
    for (var parent : types.directSupertypes(receiver)) {
      var result = owner(parent, owner, types, visited);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }
}
