package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Correlates physical annotation occurrences with direct or synthesized container values. */
final class JavaAnnotationOccurrenceCorrelation {
  private static final String VALUE_ELEMENT_NAME = "value";
  private static final int UNIQUE_CANDIDATE_COUNT = 1;
  private final SemanticResolver semantics;
  private final JavaSourceEvidence evidence;
  private final ParsedTreeInventory inventory;

  JavaAnnotationOccurrenceCorrelation(SemanticResolver semantics, JavaSourceEvidence evidence, ParsedTreeInventory inventory) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.evidence = Objects.requireNonNull(evidence, "evidence");
    this.inventory = Objects.requireNonNull(inventory, "inventory");
  }

  Result correlate(AnnotationTree annotation, TypeElement annotationType) {
    var captured = inventory.annotationPath(annotation);
    if (captured.isEmpty()) {
      return new Unavailable("The written annotation has no captured physical site");
    }
    var ownerPath = captured.orElseThrow().getParentPath();
    while (ownerPath != null && ownerPath.getLeaf() instanceof ModifiersTree) {
      ownerPath = ownerPath.getParentPath();
    }
    if (ownerPath == null) {
      return new Unavailable("The written annotation has no physical owner");
    }
    var site = physicalSite(ownerPath, annotation);
    var peers = site.stream().filter(candidate -> selects(candidate, annotationType)).toList();
    var ordinal = peers.indexOf(annotation);
    if (ordinal < 0) {
      return new Unavailable("The annotation is absent from its captured owner/type occurrences");
    }
    var containingType = repeatableContainingType(annotationType);
    var attributed = mirrors(ownerPath, annotationType, containingType);
    if (peers.size() > UNIQUE_CANDIDATE_COUNT && containingType.isPresent()
        && site.stream().anyMatch(candidate -> selects(candidate, containingType.orElseThrow()))) {
      return new Unavailable("Repeated annotations and their explicit container do not define an unambiguous correspondence");
    }
    var direct = attributed.stream().filter(candidate -> sameAnnotationType(candidate, annotationType)).toList();
    if (direct.size() == peers.size()) {
      return new Correlated(direct.get(ordinal));
    }
    var containers = containingType.stream().flatMap(type -> attributed.stream().filter(candidate -> sameAnnotationType(candidate, type)))
        .toList();
    if (direct.isEmpty() && peers.size() > UNIQUE_CANDIDATE_COUNT) {
      if (containers.size() == UNIQUE_CANDIDATE_COUNT) {
        var nested = nestedMirrors(containers.getFirst(), annotationType);
        if (nested.size() == peers.size()) {
          return new Correlated(nested.get(ordinal));
        }
      }
    }
    if (direct.isEmpty() && containers.isEmpty() && ownerPath.getLeaf() instanceof NewArrayTree && JavaAnnotationProjector
        .applicableTargets(annotationType).filter(targets -> targets.contains(ElementType.TYPE_USE)).isPresent()) {
      return LegitimateAbsence.CREATED_ARRAY_DIMENSION;
    }
    if (direct.isEmpty() && containers.isEmpty() && constructorTypeUseWithoutMirror(ownerPath, annotationType)) {
      return LegitimateAbsence.CONSTRUCTED_TYPE;
    }
    if (direct.isEmpty() && containers.isEmpty() && propagatedRecordComponent(ownerPath, annotationType)) {
      return LegitimateAbsence.RECORD_PROPAGATION;
    }
    return new Unavailable("The physical annotation occurrences have no unique attributed counterpart with matching cardinality");
  }

  private List<AnnotationTree> physicalSite(TreePath ownerPath, AnnotationTree annotation) {
    if (ownerPath.getLeaf() instanceof NewArrayTree array) {
      if (array.getAnnotations().contains(annotation)) {
        return List.copyOf(array.getAnnotations());
      }
      for (var dimension : array.getDimAnnotations()) {
        if (dimension.contains(annotation)) {
          return List.copyOf(dimension);
        }
      }
      return List.of();
    }
    return inventory.ownerAnnotations(ownerPath.getLeaf()).stream().distinct().toList();
  }

  private boolean selects(AnnotationTree annotation, TypeElement annotationType) {
    return inventory.annotationTypePath(annotation).map(semantics::element).filter(annotationType::equals).isPresent();
  }

  private List<AnnotationMirror> mirrors(TreePath ownerPath, TypeElement annotationType, Optional<TypeElement> containingType) {
    if (ownerPath.getLeaf() instanceof AnnotatedTypeTree) {
      return List.copyOf(semantics.typeAnnotationMirrors(ownerPath));
    }
    var owner = annotationOwner(ownerPath);
    if (owner == null) {
      return List.of();
    }
    var declarations = owner.getAnnotationMirrors();
    if (declarations.stream().anyMatch(mirror -> sameAnnotationType(mirror, annotationType)
        || containingType.filter(type -> sameAnnotationType(mirror, type)).isPresent())) {
      return List.copyOf(declarations);
    }
    var type = owner instanceof ExecutableElement executable ? executable.getReturnType() : owner.asType();
    return List.copyOf(closestType(type).getAnnotationMirrors());
  }

  private boolean constructorTypeUseWithoutMirror(TreePath ownerPath, TypeElement annotationType) {
    var owner = semantics.element(ownerPath);
    var targets = JavaAnnotationProjector.applicableTargets(annotationType);
    return owner != null && owner.getKind() == ElementKind.CONSTRUCTOR
        && targets.filter(value -> value.contains(ElementType.TYPE_USE) && !value.contains(ElementType.CONSTRUCTOR)).isPresent();
  }

  private boolean propagatedRecordComponent(TreePath ownerPath, TypeElement annotationType) {
    if (!(annotationOwner(ownerPath) instanceof RecordComponentElement)) {
      return false;
    }
    // The record header corroborates these values against each applicable derived counterpart.
    return JavaAnnotationProjector.applicableTargets(annotationType)
        .filter(targets -> !targets.contains(ElementType.RECORD_COMPONENT) && !targets.contains(ElementType.TYPE_USE)
            && targets.stream()
                .anyMatch(target -> target == ElementType.FIELD || target == ElementType.METHOD || target == ElementType.PARAMETER))
        .isPresent();
  }

  private static List<AnnotationMirror> nestedMirrors(AnnotationMirror container, TypeElement annotationType) {
    var nested = new ArrayList<AnnotationMirror>();
    for (var entry : container.getElementValues().entrySet()) {
      if (!entry.getKey().getSimpleName().contentEquals(VALUE_ELEMENT_NAME) || !(entry.getValue().getValue() instanceof List<?> values)) {
        continue;
      }
      for (var value : values) {
        if (!(value instanceof AnnotationValue annotationValue) || !(annotationValue.getValue() instanceof AnnotationMirror candidate)
            || !sameAnnotationType(candidate, annotationType)) {
          return List.of();
        }
        nested.add(candidate);
      }
    }
    return List.copyOf(nested);
  }

  private static TypeMirror closestType(TypeMirror type) {
    var current = type;
    while (current instanceof ArrayType array) {
      current = array.getComponentType();
    }
    while (current instanceof DeclaredType declared && declared.getEnclosingType().getKind() != TypeKind.NONE) {
      current = declared.getEnclosingType();
    }
    return current;
  }

  private Element annotationOwner(TreePath ownerPath) {
    if (ownerPath.getLeaf() instanceof VariableTree variable) {
      var parent = ownerPath.getParentPath();
      while (parent != null) {
        if (semantics.element(parent) instanceof TypeElement type && type.getKind() == ElementKind.RECORD) {
          var components = type.getRecordComponents().stream()
              .filter(component -> component.getSimpleName().contentEquals(variable.getName())).filter(component -> {
                var componentTree = semantics.sourceTree(component);
                return componentTree != null && evidence.rawStart(ownerPath.getCompilationUnit(), componentTree) >= 0 && evidence
                    .rawStart(ownerPath.getCompilationUnit(), componentTree) == evidence.rawStart(ownerPath.getCompilationUnit(), variable);
              }).toList();
          if (components.size() == UNIQUE_CANDIDATE_COUNT) {
            return components.getFirst();
          }
          break;
        }
        parent = parent.getParentPath();
      }
    }
    return semantics.element(ownerPath);
  }

  private static Optional<TypeElement> repeatableContainingType(TypeElement annotationType) {
    for (var mirror : annotationType.getAnnotationMirrors()) {
      if (!(mirror.getAnnotationType().asElement() instanceof TypeElement metaType)
          || !metaType.getQualifiedName().contentEquals("java.lang.annotation.Repeatable")) {
        continue;
      }
      for (var entry : mirror.getElementValues().entrySet()) {
        if (entry.getKey().getSimpleName().contentEquals(VALUE_ELEMENT_NAME) && entry.getValue().getValue() instanceof DeclaredType declared
            && declared.asElement() instanceof TypeElement containingType) {
          return Optional.of(containingType);
        }
      }
    }
    return Optional.empty();
  }

  private static boolean sameAnnotationType(AnnotationMirror mirror, TypeElement annotationType) {
    return mirror.getAnnotationType().asElement().equals(annotationType);
  }

  sealed interface Result permits Correlated, LegitimateAbsence, Unavailable {
  }

  record Correlated(AnnotationMirror mirror) implements Result {
    Correlated {
      Objects.requireNonNull(mirror, "mirror");
    }
  }

  enum LegitimateAbsence implements Result {
    CONSTRUCTED_TYPE, RECORD_PROPAGATION, CREATED_ARRAY_DIMENSION
  }

  record Unavailable(String reason) implements Result {
    Unavailable {
      Objects.requireNonNull(reason, "reason");
    }
  }
}
