package org.aquilagraph.frontend.internal;

import javax.lang.model.element.Element;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SyntaxNode;

/** Corroborates selected member identity shared by invocation and static-access evidence. */
enum JavaSelectedMemberProof {
  ;

  static boolean matches(SyntaxNode node, Element element, SemanticResolver semantics) {
    var key = semantics.declarationKey(element);
    var reference = node.semanticReference();
    if (reference.isEmpty() || reference.orElseThrow().status() != ResolutionStatus.RESOLVED || key.isEmpty()) {
      return false;
    }
    if (!reference.orElseThrow().symbolKey().equals(key)) {
      throw new IllegalArgumentException("Selected compiler member contradicts the emitted syntax reference");
    }
    return true;
  }
}
