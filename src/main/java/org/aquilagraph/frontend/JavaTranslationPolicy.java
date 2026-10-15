package org.aquilagraph.frontend;

import java.util.Objects;
import org.aquilagraph.source.api.AstProfile;

/** Immutable selection of source capabilities and their existing production identity. */
final class JavaTranslationPolicy {
  private final AstProfile selectedProfile;
  private final String productionRevision;

  private JavaTranslationPolicy(AstProfile profile) {
    selectedProfile = Objects.requireNonNull(profile, "profile");
    if (!profile.isSupported()) {
      throw new IllegalArgumentException("Unsupported source profile: " + profile.version());
    }
    productionRevision = switch (profile.version()) {
      case "java-source-expanded/2" -> "javac-public/2";
      case "java-source-expanded/3" -> "javac-public/3";
      case "java-source-expanded/4" -> "javac-public/4";
      case "java-source-expanded/5" -> "javac-public/5";
      case "java-source-expanded/6" -> "javac-public/6";
      case "java-source-bounded/1", "java-source-expanded/1" -> "javac-public/1";
      default -> throw new IllegalArgumentException("Unsupported source profile: " + profile.version());
    };
  }

  static JavaTranslationPolicy forProfile(AstProfile profile) {
    return new JavaTranslationPolicy(profile);
  }

  static JavaTranslationPolicy legacyAnalysis() {
    return forProfile(AstProfile.JAVA_SOURCE_BOUNDED);
  }

  AstProfile profile() {
    return selectedProfile;
  }

  String semanticRevision() {
    return productionRevision;
  }

  boolean supportsMemberInitialization() {
    return selectedProfile.supportsMemberInitialization();
  }

  boolean supportsNestedDeclarations() {
    return selectedProfile.supportsNestedDeclarations();
  }

  boolean supportsEnumInitialization() {
    return selectedProfile.supportsEnumInitialization();
  }

  boolean supportsDeclarationFidelity() {
    return selectedProfile.supportsDeclarationFidelity();
  }

  boolean supportsSourceEnvelope() {
    return selectedProfile.supportsSourceEnvelope();
  }
}
