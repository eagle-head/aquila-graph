package org.aquilagraph.frontend.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeaturePolicy;

/** Keeps surface-specific policy separate from the callable identity and its typed reader. */
record JavaDeclarationProperty(Class<?> surface, Method method, LanguageFeatureClassification classification,
    Optional<JavaDeclarationAccessor> binding) {
  JavaDeclarationProperty {
    Objects.requireNonNull(surface, "surface");
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(classification, "classification");
    Objects.requireNonNull(binding, "binding");
    var identifier = classification.feature().identifier();
    if (classification.feature().category() != LanguageFeatureCategory.DECLARATION_PROPERTY
        || !identifier.equals(JavaDeclarationPropertyCoverage.accessorIdentifier(surface, method))
        || Modifier.isStatic(method.getModifiers())
        || !method.equals(JavaDeclarationAccessor.method(surface, method.getName(), method.getParameterTypes()))) {
      throw new IllegalArgumentException("Declaration property identity does not match its compiler surface: " + identifier);
    }
    if ((classification.policy() == LanguageFeaturePolicy.REPRESENTED) != binding.isPresent()) {
      throw new IllegalArgumentException("Declaration property reader policy mismatch: " + identifier);
    }
    if (binding.filter(reader -> !reader.method().equals(method) || !reader.receiverType().isAssignableFrom(surface)).isPresent()) {
      throw new IllegalArgumentException("Declaration property reader does not match its compiler callable: " + identifier);
    }
  }

  Optional<Object> read(Object owner) {
    var reader = binding
        .orElseThrow(() -> new IllegalStateException("Declaration property is not represented: " + classification.feature().identifier()));
    return reader.read(surface.cast(owner));
  }
}
