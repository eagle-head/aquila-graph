package org.aquilagraph.frontend;

import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.TypeAnnotation.FormalParameterTarget;
import java.lang.classfile.TypeAnnotation.TargetInfo;
import java.lang.classfile.TypeAnnotation.TargetType;
import java.lang.classfile.TypeAnnotation.TypeParameterBoundTarget;
import java.lang.classfile.TypeAnnotation.TypeParameterTarget;
import java.lang.classfile.TypeAnnotation.TypePathComponent;
import java.util.ArrayList;
import java.util.List;

/** Retargets declaration annotations to the corresponding private notional signature. */
final class ConstructorWitnessTypeAnnotations {
  private final int liftedClassParameters;
  private final int finalParameter;
  private final int expandedTail;
  private final boolean expanded;

  ConstructorWitnessTypeAnnotations(int liftedClassParameters, int formalCount, int expandedTail, boolean expanded) {
    this.liftedClassParameters = liftedClassParameters;
    finalParameter = formalCount - 1;
    this.expandedTail = expandedTail;
    this.expanded = expanded;
  }

  List<TypeAnnotation> select(List<TypeAnnotation> classAnnotations, List<TypeAnnotation> constructorAnnotations) {
    var result = new ArrayList<TypeAnnotation>();
    if (liftedClassParameters > 0) {
      classAnnotations.forEach(annotation -> liftClassAnnotation(annotation, result));
    }
    constructorAnnotations.forEach(annotation -> copyConstructorAnnotation(annotation, result));
    return List.copyOf(result);
  }

  private static void liftClassAnnotation(TypeAnnotation annotation, List<TypeAnnotation> result) {
    var target = annotation.targetInfo();
    if (target instanceof TypeParameterTarget parameter) {
      result.add(copy(annotation, TargetInfo.ofMethodTypeParameter(parameter.typeParameterIndex()), annotation.targetPath()));
    } else if (target instanceof TypeParameterBoundTarget bound) {
      result.add(
          copy(annotation, TargetInfo.ofMethodTypeParameterBound(bound.typeParameterIndex(), bound.boundIndex()), annotation.targetPath()));
    }
  }

  private void copyConstructorAnnotation(TypeAnnotation annotation, List<TypeAnnotation> result) {
    var target = annotation.targetInfo();
    if (target instanceof TypeParameterTarget parameter) {
      result.add(copy(annotation, TargetInfo.ofMethodTypeParameter(liftedClassParameters + parameter.typeParameterIndex()),
          annotation.targetPath()));
    } else if (target instanceof TypeParameterBoundTarget bound) {
      result.add(
          copy(annotation, TargetInfo.ofMethodTypeParameterBound(liftedClassParameters + bound.typeParameterIndex(), bound.boundIndex()),
              annotation.targetPath()));
    } else if (target instanceof FormalParameterTarget parameter) {
      result.add(annotation);
      var path = annotation.targetPath();
      if (expanded && parameter.formalParameterIndex() == finalParameter && !path.isEmpty()
          && path.getFirst().equals(TypePathComponent.ARRAY)) {
        var componentPath = path.subList(1, path.size());
        for (var index = 0; index < expandedTail; index++) {
          result.add(copy(annotation, TargetInfo.ofMethodFormalParameter(finalParameter + index + 1), componentPath));
        }
      }
    } else if (target.targetType() == TargetType.THROWS) {
      result.add(annotation);
    }
  }

  private static TypeAnnotation copy(TypeAnnotation original, TargetInfo target, List<TypePathComponent> path) {
    return TypeAnnotation.of(target, path, original.annotation());
  }
}
