package org.aquilagraph.source.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.source.api.JavaTypeStructure;

/** Rebinds a projected owner view to its own ordered capture scope. */
final class EnclosingCaptureScope {
  private final Map<Integer, JavaTypeStructure.Captured> bindings = new HashMap<>();

  EnclosingCaptureScope(JavaTypeStructure root) {
    collect(root);
  }

  Optional<JavaTypeStructure.Captured> resolve(JavaTypeStructure.CaptureReference reference) {
    return Optional.ofNullable(bindings.get(reference.ordinal()));
  }

  JavaTypeStructure normalize(JavaTypeStructure type) {
    return new Projection().copy(type);
  }

  private void collect(JavaTypeStructure type) {
    if (type instanceof JavaTypeStructure.Captured captured) {
      bindings.put(captured.ordinal(), captured);
    }
    children(type).forEach(this::collect);
  }

  private static List<JavaTypeStructure> children(JavaTypeStructure type) {
    return switch (type) {
      case JavaTypeStructure.Declared declared -> Stream.concat(declared.enclosingType().stream(), declared.arguments().stream()).toList();
      case JavaTypeStructure.LocalDeclared local -> Stream.concat(local.enclosingType().stream(), local.arguments().stream()).toList();
      case JavaTypeStructure.Captured captured -> Stream.concat(Stream.of(captured.upperBound()), captured.lowerBound().stream()).toList();
      case JavaTypeStructure.Array array -> List.of(array.component());
      case JavaTypeStructure.Wildcard wildcard -> wildcard.bound().stream().toList();
      case JavaTypeStructure.Intersection intersection -> intersection.bounds();
      case JavaTypeStructure.Union union -> union.alternatives();
      default -> List.of();
    };
  }

  private final class Projection {
    private final Map<Integer, Integer> ordinals = new HashMap<>();

    private JavaTypeStructure capture(int ordinal) {
      var existing = ordinals.get(ordinal);
      if (existing != null) {
        return new JavaTypeStructure.CaptureReference(existing);
      }
      var original = bindings.get(ordinal);
      if (original == null) {
        throw new IllegalArgumentException("An enclosing capture projection requires its original binder");
      }
      var projected = ordinals.size();
      ordinals.put(ordinal, projected);
      return new JavaTypeStructure.Captured(projected, copy(original.upperBound()), original.lowerBound().map(this::copy));
    }

    private JavaTypeStructure copy(JavaTypeStructure type) {
      return switch (type) {
        case JavaTypeStructure.Captured captured -> capture(captured.ordinal());
        case JavaTypeStructure.CaptureReference reference -> capture(reference.ordinal());
        case JavaTypeStructure.Declared declared -> new JavaTypeStructure.Declared(declared.declarationKey(), declared.lexicalOwnerKey(),
            declared.enclosingType().map(this::copy), declared.arguments().stream().map(this::copy).toList(),
            declared.enclosingOwnerType().map(view -> new EnclosingCaptureScope(view).normalize(view)));
        case JavaTypeStructure.LocalDeclared local -> new JavaTypeStructure.LocalDeclared(local.declarationKey(), local.lexicalOwnerKey(),
            local.kind(), local.enclosingType().map(this::copy), local.arguments().stream().map(this::copy).toList());
        case JavaTypeStructure.Array array -> new JavaTypeStructure.Array(copy(array.component()));
        case JavaTypeStructure.Wildcard wildcard -> new JavaTypeStructure.Wildcard(wildcard.kind(), wildcard.bound().map(this::copy));
        case JavaTypeStructure.Intersection intersection ->
          new JavaTypeStructure.Intersection(intersection.bounds().stream().map(this::copy).toList());
        case JavaTypeStructure.Union union -> new JavaTypeStructure.Union(union.alternatives().stream().map(this::copy).toList());
        default -> type;
      };
    }
  }
}
