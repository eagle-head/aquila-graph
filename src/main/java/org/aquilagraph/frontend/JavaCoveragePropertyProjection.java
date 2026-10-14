package org.aquilagraph.frontend;

import com.sun.source.tree.VariableTree;
import com.sun.source.tree.NewClassTree;

import com.sun.source.tree.Tree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.ModifiersTree;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoverageAnnotationValue;
import org.aquilagraph.source.api.CoverageExecutableType;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.jspecify.annotations.Nullable;

/** Copies only exact represented public accessor values; compiler objects remain session-local. */
final class JavaCoveragePropertyProjection {
  private JavaCoveragePropertyProjection() {
  }

  static List<Accessor> accessors(Class<?> surface, LanguageFeatureCoverage coverage) {
    if (!JavaDeclarationPropertyCoverage.isDeclarationSurface(surface)) {
      throw new IllegalArgumentException("Unsupported declaration surface: " + surface.getName());
    }
    var result = new ArrayList<Accessor>();
    for (var method : surface.getMethods()) {
      if (method.getParameterCount() != 0 || method.getReturnType() == void.class) {
        continue;
      }
      var identifier = JavaDeclarationPropertyCoverage.accessorIdentifier(surface, method);
      coverage.classifications().stream()
          .filter(classification -> classification.feature().category() == LanguageFeatureCategory.DECLARATION_PROPERTY
              && classification.feature().identifier().equals(identifier) && classification.policy() == LanguageFeaturePolicy.REPRESENTED)
          .findFirst().ifPresent(classification -> result.add(new Accessor(method, classification)));
    }
    result.sort(Comparator.comparing(accessor -> accessor.classification().feature().identifier()));
    return List.copyOf(result);
  }

  static CoveragePropertyValue query(Accessor accessor, Object owner, JavaCoverageTopology.Owner sourceOwner, JavaSourceEvidence evidence,
      Optional<SemanticResolver> semantics) {
    return query(accessor, owner, sourceOwner, evidence, semantics, false);
  }

  static CoveragePropertyValue query(Accessor accessor, Object owner, JavaCoverageTopology.Owner sourceOwner, JavaSourceEvidence evidence,
      Optional<SemanticResolver> semantics, boolean enumInitialization) {
    if (enumInitialization && owner instanceof VariableTree variable
        && JavaEnumConstantSyntax.isConstant(sourceOwner.treePath(), evidence)) {
      switch (accessor.method().getName()) {
        case "getType" -> {
          return new CoveragePropertyValue(State.ABSENT, List.of());
        }
        case "getModifiers" -> {
          var flags = List.<String>of();
          return new CoveragePropertyValue(State.SCALAR, List.of(new Item("MODIFIERS:0", CoveragePropertyValue.canonicalFlags(flags),
              Provenance.WRITTEN, Optional.empty(), Optional.empty(), Optional.of(flags))));
        }
        case "getInitializer" -> {
          var creation = (NewClassTree) variable.getInitializer();
          var items = new ArrayList<Item>();
          creation.getArguments().forEach(argument -> items.add(copy(argument, items.size(), sourceOwner, evidence, semantics)));
          if (creation.getClassBody() != null) {
            items.add(copy(creation.getClassBody(), items.size(), sourceOwner, evidence, semantics));
          }
          return new CoveragePropertyValue(items.isEmpty() ? State.EMPTY : State.ITEMS, items);
        }
        default -> {
          // Other accessors describe the constant's actual written declaration.
        }
      }
    }
    if (owner instanceof NewArrayTree array && "getDimAnnotations".equals(accessor.method().getName())) {
      var items = new ArrayList<Item>();
      for (var dimension = 0; dimension < array.getDimAnnotations().size(); dimension++) {
        var annotations = array.getDimAnnotations().get(dimension);
        items.add(new Item("dimension:" + dimension, Integer.toString(annotations.size()), Provenance.WRITTEN));
        for (var ordinal = 0; ordinal < annotations.size(); ordinal++) {
          var annotation = copy(annotations.get(ordinal), ordinal, sourceOwner, evidence, semantics);
          items.add(new Item("dimension:" + dimension + '/' + annotation.identity(), annotation.value(), Provenance.WRITTEN));
        }
      }
      return new CoveragePropertyValue(items.isEmpty() ? State.EMPTY : State.ITEMS, items);
    }
    var value = accessor.read(owner).orElse(null);
    if (value == null || (value instanceof TypeMirror type && type.getKind() == TypeKind.NONE)) {
      return new CoveragePropertyValue(State.ABSENT, List.of());
    }
    if (value instanceof Collection<?> collection) {
      var items = new ArrayList<Item>();
      for (var item : collection) {
        if (item instanceof Tree tree && JavaCoverageTopology.isDerivedCompactParameter(tree, sourceOwner.treePath(), sourceOwner.unit(),
            sourceOwner.source(), evidence)) {
          continue;
        }
        var copied = copy(Objects.requireNonNull(item, "accessor item"), items.size(), sourceOwner, evidence, semantics);
        if ((item instanceof AnnotationMirror || item instanceof AnnotationValue) && copied.annotationValue().isEmpty()) {
          return new CoveragePropertyValue(State.UNAVAILABLE, List.of());
        }
        items.add(copied);
      }
      if (value instanceof Set<?>) {
        items.sort(Comparator.comparing(Item::value));
      }
      return new CoveragePropertyValue(items.isEmpty() ? State.EMPTY : State.ITEMS, items);
    }
    var item = copy(value, 0, sourceOwner, evidence, semantics);
    if ((value instanceof AnnotationMirror || value instanceof AnnotationValue) && item.annotationValue().isEmpty()) {
      return new CoveragePropertyValue(State.UNAVAILABLE, List.of());
    }
    if (owner instanceof TypeElement && value instanceof TypeMirror mirror && "asType".equals(accessor.method().getName())) {
      item = new Item(item.identity(), item.value(), item.provenance(), item.executableType(), item.typeIdentity(), item.modifierFlags(),
          item.annotationValue(), Optional.of(JavaCoverageTypeProjection.copy(mirror, semantics.orElseThrow())));
    }
    return new CoveragePropertyValue(State.SCALAR, List.of(item));
  }

  private static Item copy(Object value, int ordinal, JavaCoverageTopology.Owner owner, JavaSourceEvidence evidence,
      Optional<SemanticResolver> semantics) {
    var provenance = semantics.isPresent() ? Provenance.COMPILER_DERIVED : Provenance.WRITTEN;
    if (value instanceof JavaFileObject file) {
      if (!file.toUri().equals(owner.unit().getSourceFile().toUri())) {
        throw new IllegalArgumentException("A copied source-file accessor must refer to its captured compilation unit");
      }
      return new Item(owner.source().sourceFileKey(), owner.source().canonicalRelativePath(), Provenance.WRITTEN);
    }
    if (value instanceof AnnotationMirror annotation && semantics.isPresent()) {
      var resolver = semantics.orElseThrow();
      var declaration = Objects.requireNonNull(resolver.element(owner.treePath()), "annotation owner");
      var copied = JavaCoverageAnnotationProjection.annotation(annotation, declaration, resolver).map(CoverageAnnotationValue.class::cast);
      return annotationItem(ordinal, provenance, copied);
    }
    if (value instanceof AnnotationValue annotation && semantics.isPresent()) {
      return annotationItem(ordinal, provenance, JavaCoverageAnnotationProjection.value(annotation, semantics.orElseThrow()));
    }
    if (value instanceof Tree tree) {
      if (tree instanceof DefaultCaseLabelTree) {
        // The keyword has no descendant value; its exact range remains in the parsed context inventory.
        return new Item(tree.getKind().name() + ':' + ordinal, "default", Provenance.WRITTEN);
      }
      if (tree instanceof ModifiersTree modifiers) {
        var flags = modifiers.getFlags().stream().map(Object::toString).sorted().toList();
        return new Item(tree.getKind().name() + ':' + ordinal, CoveragePropertyValue.canonicalFlags(flags), Provenance.WRITTEN,
            Optional.empty(), Optional.empty(), Optional.of(flags));
      }
      var origin = evidence.origin(owner.unit(), owner.source(), tree);
      var text = origin
          .map(span -> owner.source().content().substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive())))
          .orElse(tree.getKind().name());
      return new Item(tree.getKind().name() + ':' + ordinal, text, Provenance.WRITTEN);
    }
    if (value instanceof Element element && semantics.isPresent()) {
      var identity = semantics.orElseThrow().declarationKey(element).orElse(element.getKind().name() + ':' + element);
      var written = semantics.orElseThrow().declarationOrigin(element) == Elements.Origin.EXPLICIT;
      var display = element instanceof PackageElement packageElement ? packageElement.getQualifiedName().toString() : element.toString();
      return new Item(identity, element.getKind().name() + ':' + display, written ? Provenance.WRITTEN : provenance);
    }
    if (value instanceof ExecutableType executable && semantics.isPresent()) {
      var resolver = semantics.orElseThrow();
      var projection = new CoverageExecutableType(
          executable.getTypeVariables().stream()
              .map(variable -> new CoverageExecutableType.Variable(resolver.declarationKey(variable.asElement()).orElseThrow(),
                  resolver.typeIdentity(variable)))
              .toList(),
          executable.getParameterTypes().stream().map(resolver::typeIdentity).toList(), resolver.typeIdentity(executable.getReturnType()),
          executable.getThrownTypes().stream().map(resolver::typeIdentity).toList(), Optional.ofNullable(executable.getReceiverType())
              .filter(receiver -> receiver.getKind() != TypeKind.NONE).map(resolver::typeIdentity));
      return new Item(executable.getKind().name() + ':' + ordinal, projection.canonicalText(), provenance, Optional.of(projection));
    }
    if (value instanceof TypeMirror type) {
      return new Item(type.getKind().name() + ':' + ordinal, type.toString(), provenance, Optional.empty(),
          semantics.map(resolver -> resolver.typeIdentity(type)), Optional.empty());
    }
    return new Item(value.getClass().isEnum() ? value.toString() : "item:" + ordinal, value.toString(), provenance);
  }

  private static Item annotationItem(int ordinal, Provenance provenance, Optional<CoverageAnnotationValue> copied) {
    return new Item("item:" + ordinal, copied.map(CoverageAnnotationValue::canonicalText).orElse(""), provenance, Optional.empty(),
        Optional.empty(), Optional.empty(), copied);
  }

  static final class Accessor {
    private final Method accessorMethod;
    private final LanguageFeatureClassification featureClassification;
    private final Function<Object, @Nullable Object> reader;

    private Accessor(Method method, LanguageFeatureClassification classification) {
      accessorMethod = method;
      featureClassification = classification;
      reader = JavaDeclarationAccessor.bind(method);
    }

    Method method() {
      return accessorMethod;
    }

    LanguageFeatureClassification classification() {
      return featureClassification;
    }

    Optional<Object> read(Object owner) {
      return Optional.ofNullable(reader.apply(owner));
    }
  }
}
