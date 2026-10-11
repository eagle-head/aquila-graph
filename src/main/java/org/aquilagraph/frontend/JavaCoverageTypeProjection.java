package org.aquilagraph.frontend;

import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import org.aquilagraph.source.api.CoverageTypeStructure;

/** Copies recursive public mirror selections without retaining compiler objects. */
final class JavaCoverageTypeProjection {
  private JavaCoverageTypeProjection() {
  }

  static CoverageTypeStructure copy(TypeMirror mirror, SemanticResolver semantics) {
    return switch (mirror) {
      case ArrayType array -> new CoverageTypeStructure.Array(copy(array.getComponentType(), semantics));
      case DeclaredType declared -> {
        var owner = declared.asElement().getEnclosingElement();
        var lexical = owner instanceof TypeElement || owner instanceof ExecutableElement || owner instanceof VariableElement
            ? semantics.declarationKey(owner)
            : Optional.<String>empty();
        yield new CoverageTypeStructure.Declared(semantics.declarationKey(declared.asElement()).orElseThrow(), lexical,
            declared.getEnclosingType().getKind() == TypeKind.NONE
                ? Optional.empty()
                : Optional.of(copy(declared.getEnclosingType(), semantics)),
            declared.getTypeArguments().stream().map(argument -> copy(argument, semantics)).toList());
      }
      case TypeVariable variable -> new CoverageTypeStructure.Variable(semantics.declarationKey(variable.asElement()).orElseThrow());
      case WildcardType wildcard ->
        new CoverageTypeStructure.Wildcard(Optional.ofNullable(wildcard.getExtendsBound()).map(bound -> copy(bound, semantics)),
            Optional.ofNullable(wildcard.getSuperBound()).map(bound -> copy(bound, semantics)));
      case IntersectionType intersection ->
        new CoverageTypeStructure.Intersection(intersection.getBounds().stream().map(bound -> copy(bound, semantics)).toList());
      case UnionType union ->
        new CoverageTypeStructure.Union(union.getAlternatives().stream().map(alternative -> copy(alternative, semantics)).toList());
      default -> new CoverageTypeStructure.Simple(semantics.typeIdentity(mirror).category());
    };
  }
}
