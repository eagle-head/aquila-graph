package org.aquilagraph.frontend;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/** Derives and memoizes canonical declaration identities while compiler elements remain inside the frontend. */
final class DeclarationIdentityRegistry {
  private final Trees trees;
  private final Types types;
  private final DeclarationKeyCache keyCache = DeclarationKeyCache.usingReferenceIdentity();

  DeclarationIdentityRegistry(Trees trees, Types types) {
    this.trees = Objects.requireNonNull(trees, "trees");
    this.types = Objects.requireNonNull(types, "types");
  }

  Optional<String> keyOf(Element element) {
    Objects.requireNonNull(element, "element");
    var existing = keyCache.find(element);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }
    var key = deriveKey(element);
    keyCache.remember(element, key);
    return key;
  }

  private record DeclarationKeyCache(Function<Element, Optional<Optional<String>>> lookup, BiConsumer<Element, Optional<String>> storage) {
    private DeclarationKeyCache {
      Objects.requireNonNull(lookup, "lookup");
      Objects.requireNonNull(storage, "storage");
    }

    private static DeclarationKeyCache usingReferenceIdentity() {
      var entries = new IdentityHashMap<Element, Optional<String>>();
      return new DeclarationKeyCache(element -> Optional.ofNullable(entries.get(element)), entries::put);
    }

    private Optional<Optional<String>> find(Element element) {
      return lookup.apply(element);
    }

    private void remember(Element element, Optional<String> key) {
      storage.accept(element, key);
    }
  }

  String signatureOf(ExecutableElement element) {
    Objects.requireNonNull(element, "element");
    var name = element.getKind() == ElementKind.CONSTRUCTOR ? "<init>" : element.getSimpleName().toString();
    var parameterTypes = element.getParameters().stream().map(VariableElement::asType).map(types::erasure).map(Object::toString)
        .collect(Collectors.joining(","));
    return name + "(" + parameterTypes + ")";
  }

  private Optional<String> deriveKey(Element element) {
    return switch (element.getKind()) {
      case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> keyOfType((TypeElement) element);
      case CONSTRUCTOR, METHOD -> keyOfExecutable((ExecutableElement) element);
      case FIELD -> keyOfOwnedVariable((VariableElement) element, "field");
      case ENUM_CONSTANT -> keyOfOwnedVariable((VariableElement) element, "enum");
      case PARAMETER -> keyOfParameter((VariableElement) element);
      case TYPE_PARAMETER -> keyOfTypeParameter((TypeParameterElement) element);
      case RECORD_COMPONENT -> keyOfRecordComponent((RecordComponentElement) element);
      case LOCAL_VARIABLE, EXCEPTION_PARAMETER, RESOURCE_VARIABLE, BINDING_VARIABLE -> keyOfLocalVariable((VariableElement) element);
      case PACKAGE -> Optional.of("package:" + ((PackageElement) element).getQualifiedName());
      default -> Optional.empty();
    };
  }

  private Optional<String> keyOfType(TypeElement element) {
    var qualifiedName = element.getQualifiedName().toString();
    if (!qualifiedName.isBlank()) {
      return Optional.of("type:" + qualifiedName);
    }
    return keyOfOwnedStructure(element, "type");
  }

  private Optional<String> keyOfExecutable(ExecutableElement element) {
    if (!(element.getEnclosingElement() instanceof TypeElement owner)) {
      return Optional.empty();
    }
    return keyOfType(owner).map(ownerKey -> ownerKey + "#" + signatureOf(element));
  }

  private Optional<String> keyOfOwnedVariable(VariableElement element, String category) {
    if (!(element.getEnclosingElement() instanceof TypeElement owner)) {
      return Optional.empty();
    }
    return keyOfType(owner).map(ownerKey -> ownerKey + "#" + category + ":" + element.getSimpleName());
  }

  private Optional<String> keyOfParameter(VariableElement parameter) {
    if (!(parameter.getEnclosingElement() instanceof ExecutableElement executable)) {
      return Optional.empty();
    }
    for (var index = 0; index < executable.getParameters().size(); index++) {
      if (executable.getParameters().get(index).equals(parameter)) {
        var ordinal = index;
        return keyOfExecutable(executable).map(ownerKey -> ownerKey + ":parameter:" + ordinal);
      }
    }
    return Optional.empty();
  }

  private Optional<String> keyOfTypeParameter(TypeParameterElement parameter) {
    if (!(parameter.getGenericElement() instanceof Parameterizable owner)) {
      return Optional.empty();
    }
    for (var index = 0; index < owner.getTypeParameters().size(); index++) {
      if (owner.getTypeParameters().get(index).equals(parameter)) {
        var ordinal = index;
        return keyOf((Element) owner).map(ownerKey -> ownerKey + "#type-parameter:" + ordinal);
      }
    }
    return Optional.empty();
  }

  private Optional<String> keyOfRecordComponent(RecordComponentElement component) {
    if (!(component.getEnclosingElement() instanceof TypeElement owner)) {
      return Optional.empty();
    }
    for (var ordinal = 0; ordinal < owner.getRecordComponents().size(); ordinal++) {
      if (owner.getRecordComponents().get(ordinal).equals(component)) {
        var position = ordinal;
        return keyOfType(owner).map(ownerKey -> ownerKey + "#record-component:" + position);
      }
    }
    return Optional.empty();
  }

  private Optional<String> keyOfLocalVariable(VariableElement variable) {
    var owner = enclosingExecutableOf(variable);
    if (owner.isEmpty()) {
      return Optional.empty();
    }
    var category = variable.getKind().name().toLowerCase(Locale.ROOT);
    return keyOfExecutable(owner.orElseThrow())
        .flatMap(ownerKey -> coordinateOf(variable).map(coordinate -> ownerKey + ':' + category + ':' + coordinate));
  }

  private Optional<String> keyOfOwnedStructure(Element element, String category) {
    var owner = element.getEnclosingElement();
    if (owner == null) {
      return Optional.empty();
    }
    return keyOf(owner).flatMap(ownerKey -> coordinateOf(element).map(coordinate -> ownerKey + ':' + category + ':' + coordinate));
  }

  private Optional<String> coordinateOf(Element element) {
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

  private static Optional<ExecutableElement> enclosingExecutableOf(Element element) {
    for (var owner = element.getEnclosingElement(); owner != null; owner = owner.getEnclosingElement()) {
      if (owner instanceof ExecutableElement executable) {
        return Optional.of(executable);
      }
    }
    return Optional.empty();
  }
}
