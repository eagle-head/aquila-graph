package org.aquilagraph.cfg.internal;

import java.util.Objects;
import org.aquilagraph.source.api.StaticMemberEvidence;

/** CFG effects selected from immutable static-member source facts. */
final class ClassInitializationPolicy {
  private ClassInitializationPolicy() {
  }

  static boolean required(StaticMemberEvidence access) {
    return switch (Objects.requireNonNull(access, "access").kind()) {
      case FIELD_READ -> !access.constantVariable().knownValue().orElseThrow();
      case FIELD_WRITE, METHOD_INVOKE -> true;
    };
  }
}
