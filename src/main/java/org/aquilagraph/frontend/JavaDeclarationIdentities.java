package org.aquilagraph.frontend;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/** Assigns canonical declaration identities while compiler elements remain inside the frontend. */
final class JavaDeclarationIdentities {
  private final Trees trees;
  private final Types types;
  private final Map<ElementIdentity, Optional<String>> keys = new HashMap<>();

  JavaDeclarationIdentities(Trees trees, Types types) {
    this.trees = Objects.requireNonNull(trees, "trees");
    this.types = Objects.requireNonNull(types, "types");
  }

  Optional<String> key(Element element) {
    Objects.requireNonNull(element, "element");
    var identity = new ElementIdentity(element);
    var existing = keys.get(identity);
    if (existing != null) {
      return existing;
    }
    var key = computeKey(element);
    keys.put(identity, key);
    return key;
  }

  private record ElementIdentity(Element element) {
    private ElementIdentity {
      Objects.requireNonNull(element, "element");
    }

    @Override
    public boolean equals(Object other) {
      return this == other || other instanceof ElementIdentity that && element == that.element;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(element);
    }
  }

  String executableSignature(ExecutableElement element) {
    Objects.requireNonNull(element, "element");
    var name = element.getKind() == ElementKind.CONSTRUCTOR ? "<init>" : element.getSimpleName().toString();
    var parameterTypes = element.getParameters().stream().map(VariableElement::asType).map(types::erasure).map(Object::toString)
        .collect(Collectors.joining(","));
    return name + "(" + parameterTypes + ")";
  }

  private Optional<String> computeKey(Element element) {
    return switch (element.getKind()) {
      case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> typeKey((TypeElement) element);
      case CONSTRUCTOR, METHOD -> executableKey((ExecutableElement) element);
      case FIELD -> ownedVariableKey((VariableElement) element, "field");
      case ENUM_CONSTANT -> ownedVariableKey((VariableElement) element, "enum");
      case PARAMETER -> parameterKey((VariableElement) element);
      case TYPE_PARAMETER -> typeParameterKey((TypeParameterElement) element);
      case LOCAL_VARIABLE, EXCEPTION_PARAMETER, RESOURCE_VARIABLE, BINDING_VARIABLE -> localKey((VariableElement) element);
      case PACKAGE -> Optional.of("package:" + ((PackageElement) element).getQualifiedName());
      default -> Optional.empty();
    };
  }

  private Optional<String> typeKey(TypeElement element) {
    var qualifiedName = element.getQualifiedName().toString();
    if (!qualifiedName.isBlank()) {
      return Optional.of("type:" + qualifiedName);
    }
    return ownedStructuralKey(element, "type");
  }

  private Optional<String> executableKey(ExecutableElement element) {
    if (!(element.getEnclosingElement() instanceof TypeElement owner)) {
      return Optional.empty();
    }
    return typeKey(owner).map(ownerKey -> ownerKey + "#" + executableSignature(element));
  }

  private Optional<String> ownedVariableKey(VariableElement element, String category) {
    if (!(element.getEnclosingElement() instanceof TypeElement owner)) {
      return Optional.empty();
    }
    return typeKey(owner).map(ownerKey -> ownerKey + "#" + category + ":" + element.getSimpleName());
  }

  private Optional<String> parameterKey(VariableElement parameter) {
    if (!(parameter.getEnclosingElement() instanceof ExecutableElement executable)) {
      return Optional.empty();
    }
    for (var index = 0; index < executable.getParameters().size(); index++) {
      if (executable.getParameters().get(index).equals(parameter)) {
        var ordinal = index;
        return executableKey(executable).map(ownerKey -> ownerKey + ":parameter:" + ordinal);
      }
    }
    return Optional.empty();
  }

  private Optional<String> typeParameterKey(TypeParameterElement parameter) {
    if (!(parameter.getGenericElement() instanceof Parameterizable owner)) {
      return Optional.empty();
    }
    for (var index = 0; index < owner.getTypeParameters().size(); index++) {
      if (owner.getTypeParameters().get(index).equals(parameter)) {
        var ordinal = index;
        return key((Element) owner).map(ownerKey -> ownerKey + "#type-parameter:" + ordinal);
      }
    }
    return Optional.empty();
  }

  private Optional<String> localKey(VariableElement variable) {
    var owner = enclosingExecutable(variable);
    if (owner.isEmpty()) {
      return Optional.empty();
    }
    var category = variable.getKind().name().toLowerCase(Locale.ROOT);
    return executableKey(owner.orElseThrow())
        .flatMap(ownerKey -> sourceCoordinate(variable).map(coordinate -> ownerKey + ':' + category + ':' + coordinate));
  }

  private Optional<String> ownedStructuralKey(Element element, String category) {
    var owner = element.getEnclosingElement();
    if (owner == null) {
      return Optional.empty();
    }
    return key(owner).flatMap(ownerKey -> sourceCoordinate(element).map(coordinate -> ownerKey + ':' + category + ':' + coordinate));
  }

  private Optional<String> sourceCoordinate(Element element) {
    TreePath path = trees.getPath(element);
    if (path == null) {
      return Optional.empty();
    }
    var positions = trees.getSourcePositions();
    var start = positions.getStartPosition(path.getCompilationUnit(), path.getLeaf());
    var end = positions.getEndPosition(path.getCompilationUnit(), path.getLeaf());
    if (start == Diagnostic.NOPOS || end == Diagnostic.NOPOS || end < start) {
      return Optional.empty();
    }
    return Optional.of(start + ":" + end);
  }

  private static Optional<ExecutableElement> enclosingExecutable(Element element) {
    for (var owner = element.getEnclosingElement(); owner != null; owner = owner.getEnclosingElement()) {
      if (owner instanceof ExecutableElement executable) {
        return Optional.of(executable);
      }
    }
    return Optional.empty();
  }
}
