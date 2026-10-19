package org.aquilagraph.frontend;

import com.sun.source.util.TreePath;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Types;
import javax.lang.model.util.Elements;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import javax.tools.Diagnostic;

/** Derives and memoizes canonical declaration identities while compiler elements remain inside the frontend. */
final class DeclarationIdentityRegistry {
  private final Trees trees;
  private final Types types;
  private final Elements elements;
  private final JavaTranslationPolicy policy;
  private final DeclarationIdentityState identityState = DeclarationIdentityState.usingReferenceIdentity();

  DeclarationIdentityRegistry(Trees trees, Types types, Elements elements, JavaTranslationPolicy policy) {
    this.trees = Objects.requireNonNull(trees, "trees");
    this.types = Objects.requireNonNull(types, "types");
    this.elements = Objects.requireNonNull(elements, "elements");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  Optional<JavaTypeDeclarationIdentity> typeIdentity(Element element) {
    if (!policy.profile().supportsModuleServices() || !(element instanceof TypeElement type) || !hasCanonicalTypeName(type)) {
      return Optional.empty();
    }
    var module = elements.getModuleOf(type);
    var binaryName = elements.getBinaryName(type).toString();
    if (module == null || binaryName.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new JavaTypeDeclarationIdentity(
        module.isUnnamed() ? Optional.empty() : Optional.of(module.getQualifiedName().toString()), binaryName));
  }

  Optional<String> keyOf(Element element) {
    Objects.requireNonNull(element, "element");
    var existing = identityState.find(element);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }
    var key = deriveKey(element);
    identityState.remember(element, key);
    return key;
  }

  void registerSourceDeclaration(Element element, TreePath path) {
    if (!Objects.equals(trees.getElement(path), element)) {
      throw new IllegalArgumentException("A source declaration requires its exact compiler element and creating path");
    }
    identityState.sourceStorage().accept(element, path);
    var owner = element.getEnclosingElement();
    if (owner != null && (owner.getKind() == ElementKind.STATIC_INIT || owner.getKind() == ElementKind.INSTANCE_INIT)) {
      var scope = JavaExecutionOwner.find(path);
      if (scope.isPresent() && scope.orElseThrow().getLeaf() instanceof BlockTree block && scope.orElseThrow().getParentPath() != null
          && Objects.equals(trees.getElement(scope.orElseThrow().getParentPath()), owner.getEnclosingElement())
          && block.isStatic() == (owner.getKind() == ElementKind.STATIC_INIT)) {
        identityState.sourceStorage().accept(owner, scope.orElseThrow());
      }
      if (scope.isPresent() && scope.orElseThrow().getLeaf() instanceof VariableTree && scope.orElseThrow().getParentPath() != null
          && Objects.equals(trees.getElement(scope.orElseThrow().getParentPath()), owner.getEnclosingElement())
          && trees.getElement(scope.orElseThrow()) instanceof VariableElement field
          && (field.getKind() == ElementKind.FIELD || field.getKind() == ElementKind.ENUM_CONSTANT)
          && field.getModifiers().contains(Modifier.STATIC) && owner.getKind() == ElementKind.STATIC_INIT) {
        identityState.sourceStorage().accept(owner, scope.orElseThrow());
      }
    }
  }

  boolean isLambdaParameter(Element element) {
    var path = identityState.sourceLookup().apply(element).orElse(null);
    return element.getKind() == ElementKind.PARAMETER && path != null && path.getParentPath() != null
        && path.getParentPath().getLeaf() instanceof LambdaExpressionTree;
  }

  private record DeclarationIdentityState(Function<Element, Optional<Optional<String>>> lookup,
      BiConsumer<Element, Optional<String>> storage, Function<Element, Optional<TreePath>> sourceLookup,
      BiConsumer<Element, TreePath> sourceStorage) {
    private DeclarationIdentityState {
      Objects.requireNonNull(lookup, "lookup");
      Objects.requireNonNull(storage, "storage");
      Objects.requireNonNull(sourceLookup, "sourceLookup");
      Objects.requireNonNull(sourceStorage, "sourceStorage");
    }

    private static DeclarationIdentityState usingReferenceIdentity() {
      var entries = new IdentityHashMap<Element, Optional<String>>();
      var declarations = new IdentityHashMap<Element, TreePath>();
      return new DeclarationIdentityState(element -> Optional.ofNullable(entries.get(element)), entries::put,
          element -> Optional.ofNullable(declarations.get(element)), declarations::put);
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
      case STATIC_INIT, INSTANCE_INIT -> keyOfOwnedStructure(element, "initializer");
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
    if (hasCanonicalTypeName(element)) {
      if (policy.profile().supportsModuleServices()) {
        return typeIdentity(element).map(JavaTypeDeclarationIdentity::declarationKey);
      }
      var qualifiedName = element.getQualifiedName().toString();
      if (!qualifiedName.isBlank()) {
        return Optional.of("type:" + qualifiedName);
      }
    }
    return keyOfOwnedStructure(element, "type");
  }

  private static boolean hasCanonicalTypeName(TypeElement element) {
    return switch (element.getNestingKind()) {
      case TOP_LEVEL -> true;
      case MEMBER -> element.getEnclosingElement() instanceof TypeElement owner && hasCanonicalTypeName(owner);
      case LOCAL, ANONYMOUS -> false;
    };
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
    if (isLambdaParameter(parameter)) {
      return keyOfLocalVariable(parameter);
    }
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
    var category = variable.getKind().name().toLowerCase(Locale.ROOT);
    return lexicalExecutionOwner(variable)
        .flatMap(ownerKey -> coordinateOf(variable).map(coordinate -> ownerKey + ':' + category + ':' + coordinate));
  }

  private Optional<String> lexicalExecutionOwner(VariableElement variable) {
    if (variable.getEnclosingElement() == null) {
      return Optional.empty();
    }
    var path = identityState.sourceLookup().apply(variable).orElseGet(() -> trees.getPath(variable));
    var cursor = path == null ? null : JavaExecutionOwner.find(path).orElse(null);
    if (cursor != null && cursor.getParentPath() != null) {
      if (cursor.getLeaf() instanceof MethodTree) {
        return enclosingExecutableOf(variable).flatMap(this::keyOfExecutable);
      }
      if (cursor.getParentPath().getLeaf() instanceof ClassTree && trees.getElement(cursor.getParentPath()) instanceof TypeElement owner) {
        if (cursor.getLeaf() instanceof VariableTree && trees.getElement(cursor) instanceof VariableElement field
            && (field.getKind() == ElementKind.FIELD || field.getKind() == ElementKind.ENUM_CONSTANT)) {
          return keyOfOwnedVariable(field, field.getKind() == ElementKind.ENUM_CONSTANT ? "enum" : "field");
        }
        if (cursor.getLeaf() instanceof BlockTree) {
          var positions = trees.getSourcePositions();
          var start = positions.getStartPosition(cursor.getCompilationUnit(), cursor.getLeaf());
          var end = positions.getEndPosition(cursor.getCompilationUnit(), cursor.getLeaf());
          if (start != Diagnostic.NOPOS && end >= start) {
            return keyOfType(owner).map(ownerKey -> ownerKey + ":initializer:" + start + ':' + end);
          }
          return Optional.empty();
        }
      }
    }
    return enclosingExecutableOf(variable).flatMap(this::keyOfExecutable);
  }

  private Optional<String> keyOfOwnedStructure(Element element, String category) {
    var owner = element.getEnclosingElement();
    if (owner == null) {
      return Optional.empty();
    }
    return keyOf(owner).flatMap(ownerKey -> coordinateOf(element).map(coordinate -> ownerKey + ':' + category + ':' + coordinate));
  }

  private Optional<String> coordinateOf(Element element) {
    var path = identityState.sourceLookup().apply(element).orElseGet(() -> trees.getPath(element));
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
