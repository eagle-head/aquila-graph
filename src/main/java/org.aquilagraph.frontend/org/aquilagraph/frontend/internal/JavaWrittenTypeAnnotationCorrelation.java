package org.aquilagraph.frontend.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.JavaSourceKind;

/** Requires written type annotations at their exact positions in an attributed type. */
final class JavaWrittenTypeAnnotationCorrelation {
  private final JavaAnnotationMirrorCorrelation mirrors;

  JavaWrittenTypeAnnotationCorrelation(SemanticResolver semantics) {
    mirrors = new JavaAnnotationMirrorCorrelation(Objects.requireNonNull(semantics, "semantics"));
  }

  boolean matches(SyntaxNode written, List<SyntaxNode> closest, TypeMirror attributed) {
    return matches(written, closest, List.of(), attributed);
  }

  private boolean matches(SyntaxNode written, List<SyntaxNode> closest, List<SyntaxNode> current, TypeMirror attributed) {
    return switch (written.sourceKind()) {
      case ANNOTATED_TYPE -> matches(written.requiredChild(SyntaxRole.UNDERLYING_TYPE), closest,
          combined(current, written.children(SyntaxRole.ANNOTATION)), attributed);
      case ARRAY_TYPE -> attributed instanceof ArrayType type && mirrors.containsAll(attributed.getAnnotationMirrors(), current)
          && matches(written.requiredChild(SyntaxRole.COMPONENT_TYPE), closest, List.of(), type.getComponentType());
      case PARAMETERIZED_TYPE -> parameterized(written, closest, current, attributed);
      case MEMBER_SELECT -> selected(written, closest, current, attributed);
      case UNBOUNDED_WILDCARD, EXTENDS_WILDCARD, SUPER_WILDCARD -> wildcard(written, combined(current, closest), attributed);
      default -> mirrors.containsAll(attributed.getAnnotationMirrors(), combined(current, closest));
    };
  }

  private boolean parameterized(SyntaxNode written, List<SyntaxNode> closest, List<SyntaxNode> current, TypeMirror attributed) {
    if (!(attributed instanceof DeclaredType type) || written.children(SyntaxRole.TYPE_ARGUMENT).size() != type.getTypeArguments().size()
        || !matches(written.requiredChild(SyntaxRole.TYPE_NAME), closest, current, attributed)) {
      return false;
    }
    for (var index = 0; index < written.children(SyntaxRole.TYPE_ARGUMENT).size(); index++) {
      if (!matches(written.children(SyntaxRole.TYPE_ARGUMENT).get(index), List.of(), type.getTypeArguments().get(index))) {
        return false;
      }
    }
    return true;
  }

  private boolean selected(SyntaxNode written, List<SyntaxNode> closest, List<SyntaxNode> current, TypeMirror attributed) {
    if (!(attributed instanceof DeclaredType type)) {
      return false;
    }
    if (type.getEnclosingType().getKind() == TypeKind.NONE) {
      return mirrors.containsAll(attributed.getAnnotationMirrors(), combined(current, closest));
    }
    return mirrors.containsAll(attributed.getAnnotationMirrors(), current)
        && matches(written.requiredChild(SyntaxRole.TYPE_QUALIFIER), closest, List.of(), type.getEnclosingType());
  }

  private boolean wildcard(SyntaxNode written, List<SyntaxNode> expected, TypeMirror attributed) {
    if (!(attributed instanceof WildcardType type) || !mirrors.containsAll(attributed.getAnnotationMirrors(), expected)) {
      return false;
    }
    var role = written.sourceKind() == JavaSourceKind.EXTENDS_WILDCARD ? SyntaxRole.UPPER_BOUND : SyntaxRole.LOWER_BOUND;
    var bound = role == SyntaxRole.UPPER_BOUND ? type.getExtendsBound() : type.getSuperBound();
    return written.children(role).isEmpty() || (bound != null && matches(written.requiredChild(role), List.of(), bound));
  }

  private static List<SyntaxNode> combined(List<SyntaxNode> first, List<SyntaxNode> second) {
    var result = new ArrayList<>(first);
    result.addAll(second);
    return result;
  }
}
