package org.aquilagraph.frontend;

import java.util.Objects;
import javax.lang.model.type.TypeKind;
import org.jspecify.annotations.Nullable;

/** Distinguishes effective compiler type kinds from unavailable and non-type outcomes. */
sealed interface JavaTypeAttribution {
  /** A concrete attributed Java type kind, including the written {@code void} result. */
  record Resolved(TypeKind kind) implements JavaTypeAttribution {
    public Resolved {
      Objects.requireNonNull(kind, "kind");
      if (kind == TypeKind.ERROR || kind == TypeKind.NONE || kind == TypeKind.PACKAGE || kind == TypeKind.MODULE
          || kind == TypeKind.OTHER) {
        throw new IllegalArgumentException("A non-success compiler type kind cannot be resolved: " + kind);
      }
    }
  }

  /** An attributed pseudo-type that is not a source type in a required header position. */
  record NoType(TypeKind kind) implements JavaTypeAttribution {
    public NoType {
      Objects.requireNonNull(kind, "kind");
      if (kind != TypeKind.NONE && kind != TypeKind.PACKAGE && kind != TypeKind.MODULE) {
        throw new IllegalArgumentException("A no-type outcome requires NONE, PACKAGE, or MODULE: " + kind);
      }
    }
  }

  /** A compiler type that could not be resolved. */
  enum Erroneous implements JavaTypeAttribution {
    INSTANCE
  }

  /** An implementation-reserved attributed kind. */
  enum Unsupported implements JavaTypeAttribution {
    INSTANCE
  }

  /** No compiler type was supplied for a required written type. */
  enum Unresolved implements JavaTypeAttribution {
    INSTANCE
  }

  static JavaTypeAttribution fromKind(@Nullable TypeKind kind) {
    if (kind == null) {
      return Unresolved.INSTANCE;
    }
    return switch (kind) {
      case ERROR -> Erroneous.INSTANCE;
      case NONE, PACKAGE, MODULE -> new NoType(kind);
      case OTHER -> Unsupported.INSTANCE;
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID, NULL, ARRAY, DECLARED, TYPEVAR, WILDCARD, EXECUTABLE, UNION,
          INTERSECTION ->
        new Resolved(kind);
    };
  }
}
