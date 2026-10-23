package org.aquilagraph.frontend.internal;

import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxNode;

/** A detached declaration reference awaiting lexical acceptance and canonical rebinding. */
record JavaDeclarationReference(SyntaxNode source, SemanticReference reference) {
  static Optional<JavaDeclarationReference> attributed(SemanticResolver semantics, TreePath path, SyntaxNode node) {
    var declaration = semantics.element(path);
    if (declaration == null) {
      return Optional.empty();
    }
    var key = semantics.declarationKey(declaration);
    var kind = referenceKind(declaration.getKind());
    if (key.isEmpty() || kind.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new JavaDeclarationReference(node,
        new SemanticReference(kind.orElseThrow(), ResolutionStatus.RESOLVED, key, Optional.empty(), List.of(), List.of())));
  }

  private static Optional<ReferenceKind> referenceKind(ElementKind kind) {
    return switch (kind) {
      case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> Optional.of(ReferenceKind.TYPE);
      case CONSTRUCTOR -> Optional.of(ReferenceKind.CONSTRUCTOR);
      case METHOD -> Optional.of(ReferenceKind.METHOD);
      case FIELD -> Optional.of(ReferenceKind.FIELD);
      case TYPE_PARAMETER -> Optional.of(ReferenceKind.TYPE_PARAMETER);
      case PARAMETER -> Optional.of(ReferenceKind.PARAMETER);
      case ENUM_CONSTANT -> Optional.of(ReferenceKind.ENUM_CONSTANT);
      case RECORD_COMPONENT -> Optional.of(ReferenceKind.RECORD_COMPONENT);
      default -> Optional.empty();
    };
  }
}
