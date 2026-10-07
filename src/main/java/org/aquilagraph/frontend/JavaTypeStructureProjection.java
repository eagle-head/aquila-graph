package org.aquilagraph.frontend;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeStructure;

/** Copies one complete attributed type with type-local binders for implicit variables. */
final class JavaTypeStructureProjection {
  private static final int MIN_COMPOSITE_COMPONENTS = 2;
  private final DeclarationIdentityRegistry identities;
  private final CaptureBindings captures = CaptureBindings.usingReferenceIdentity();

  JavaTypeStructureProjection(DeclarationIdentityRegistry identities) {
    this.identities = Objects.requireNonNull(identities, "identities");
  }

  Optional<JavaTypeStructure> project(TypeMirror type) {
    return switch (type.getKind()) {
      case BOOLEAN -> primitive(JavaTypeCategory.BOOLEAN);
      case BYTE -> primitive(JavaTypeCategory.BYTE);
      case SHORT -> primitive(JavaTypeCategory.SHORT);
      case INT -> primitive(JavaTypeCategory.INT);
      case LONG -> primitive(JavaTypeCategory.LONG);
      case CHAR -> primitive(JavaTypeCategory.CHAR);
      case FLOAT -> primitive(JavaTypeCategory.FLOAT);
      case DOUBLE -> primitive(JavaTypeCategory.DOUBLE);
      case VOID -> primitive(JavaTypeCategory.VOID);
      case ARRAY -> project(((ArrayType) type).getComponentType()).map(JavaTypeStructure.Array::new);
      case TYPEVAR -> variable((TypeVariable) type);
      case DECLARED -> declared((DeclaredType) type);
      case WILDCARD -> wildcard((WildcardType) type);
      case INTERSECTION -> components(((IntersectionType) type).getBounds(), true);
      case UNION -> components(((UnionType) type).getAlternatives(), false);
      default -> Optional.empty();
    };
  }

  private static Optional<JavaTypeStructure> primitive(JavaTypeCategory category) {
    return Optional.of(new JavaTypeStructure.Primitive(category));
  }

  private Optional<JavaTypeStructure> variable(TypeVariable type) {
    var element = type.asElement();
    var key = identities.keyOf(element);
    if (key.isPresent()) {
      return Optional.of(new JavaTypeStructure.Variable(key.orElseThrow()));
    }
    if (element instanceof TypeParameterElement parameter && parameter.getGenericElement() instanceof Parameterizable owner
        && owner.getTypeParameters().contains(parameter)) {
      return Optional.empty();
    }
    var existing = captures.find(element);
    if (existing.isPresent()) {
      return Optional.of(new JavaTypeStructure.CaptureReference(existing.orElseThrow()));
    }
    var ordinal = captures.size();
    captures.remember(element, ordinal);
    var upper = project(type.getUpperBound());
    var lowerMirror = type.getLowerBound();
    var lower = lowerMirror.getKind() == TypeKind.NULL ? Optional.<JavaTypeStructure>empty() : project(lowerMirror);
    if (upper.isEmpty() || (lowerMirror.getKind() != TypeKind.NULL && lower.isEmpty())) {
      return Optional.empty();
    }
    return Optional.of(new JavaTypeStructure.Captured(ordinal, upper.orElseThrow(), lower));
  }

  private Optional<JavaTypeStructure> wildcard(WildcardType type) {
    var upper = type.getExtendsBound();
    var lower = type.getSuperBound();
    if (upper != null && lower != null) {
      return Optional.empty();
    }
    if (upper != null) {
      return project(upper).map(bound -> new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.EXTENDS, Optional.of(bound)));
    }
    if (lower != null) {
      return project(lower).map(bound -> new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.SUPER, Optional.of(bound)));
    }
    return Optional.of(new JavaTypeStructure.Wildcard(JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED, Optional.empty()));
  }

  private Optional<JavaTypeStructure> components(List<? extends TypeMirror> mirrors, boolean intersection) {
    var components = new ArrayList<JavaTypeStructure>();
    for (var mirror : mirrors) {
      var component = project(mirror);
      if (component.isEmpty()) {
        return Optional.empty();
      }
      components.add(component.orElseThrow());
    }
    if (components.size() < MIN_COMPOSITE_COMPONENTS || new HashSet<>(components).size() != components.size()) {
      return Optional.empty();
    }
    return Optional.of(intersection ? new JavaTypeStructure.Intersection(components) : new JavaTypeStructure.Union(components));
  }

  private Optional<JavaTypeStructure> declared(DeclaredType type) {
    var element = (TypeElement) type.asElement();
    var key = identities.keyOf(element);
    if (key.isEmpty()) {
      return Optional.empty();
    }
    Optional<String> lexicalOwner = Optional.empty();
    var nesting = element.getNestingKind();
    if (nesting != NestingKind.TOP_LEVEL) {
      var owner = element.getEnclosingElement();
      if (owner == null || (nesting == NestingKind.MEMBER && !(owner instanceof TypeElement))) {
        return Optional.empty();
      }
      lexicalOwner = identities.keyOf(owner);
      if (lexicalOwner.isEmpty()) {
        return Optional.empty();
      }
    }
    Optional<JavaTypeStructure> enclosing = Optional.empty();
    var enclosingMirror = type.getEnclosingType();
    if (enclosingMirror.getKind() != TypeKind.NONE) {
      enclosing = project(enclosingMirror);
      if (enclosing.isEmpty()) {
        return Optional.empty();
      }
    }
    var arguments = new ArrayList<JavaTypeStructure>();
    for (var argument : type.getTypeArguments()) {
      var component = project(argument);
      if (component.isEmpty()) {
        return Optional.empty();
      }
      arguments.add(component.orElseThrow());
    }
    if (nesting == NestingKind.LOCAL || nesting == NestingKind.ANONYMOUS) {
      var kind = nesting == NestingKind.LOCAL ? JavaTypeStructure.LocalDeclared.Kind.LOCAL : JavaTypeStructure.LocalDeclared.Kind.ANONYMOUS;
      return Optional.of(new JavaTypeStructure.LocalDeclared(key.orElseThrow(), lexicalOwner.orElseThrow(), kind, enclosing, arguments));
    }
    return Optional.of(new JavaTypeStructure.Declared(key.orElseThrow(), lexicalOwner, enclosing, arguments));
  }

  private record CaptureBindings(Function<Element, Optional<Integer>> lookup, BiConsumer<Element, Integer> storage, IntSupplier count) {
    private CaptureBindings {
      Objects.requireNonNull(lookup, "lookup");
      Objects.requireNonNull(storage, "storage");
      Objects.requireNonNull(count, "count");
    }

    private static CaptureBindings usingReferenceIdentity() {
      var entries = new IdentityHashMap<Element, Integer>();
      return new CaptureBindings(element -> Optional.ofNullable(entries.get(element)), entries::put, entries::size);
    }

    private Optional<Integer> find(Element element) {
      return lookup.apply(element);
    }

    private void remember(Element element, int ordinal) {
      storage.accept(element, ordinal);
    }

    private int size() {
      return count.getAsInt();
    }
  }
}
