package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.SyntaxNode;

/** Compares the known parts of a declaration type and a selected variable use. */
final class BindingTypeCorrespondence {
  private BindingTypeCorrespondence() {
  }

  static boolean contradicts(SyntaxNode access, JavaTypeIdentity declared) {
    return available(declared) && access.type().knownIdentity().filter(BindingTypeCorrespondence::available)
        .filter(actual -> !matchesBindingType(declared, actual)).isPresent();
  }

  private static boolean matchesBindingType(JavaTypeIdentity declared, JavaTypeIdentity actual) {
    // Capture changes display names; a union use can expose its effective class or intersection type.
    if (declared.category() != JavaTypeCategory.UNION) {
      return sameCategoryAndErasure(declared, actual);
    }
    return switch (actual.category()) {
      case UNION, DECLARED, INTERSECTION -> declared.erasedName().equals(actual.erasedName());
      default -> false;
    };
  }

  static boolean available(JavaTypeIdentity type) {
    return type.category() != JavaTypeCategory.ERROR && type.category() != JavaTypeCategory.OTHER;
  }

  static boolean sameCategoryAndErasure(JavaTypeIdentity first, JavaTypeIdentity second) {
    return first.category() == second.category() && first.erasedName().equals(second.erasedName());
  }
}
