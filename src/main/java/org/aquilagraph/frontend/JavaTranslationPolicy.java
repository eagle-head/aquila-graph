package org.aquilagraph.frontend;

import java.util.Objects;
import org.aquilagraph.source.api.AstProfile;

/** Immutable selection of source capabilities and their production identity. */
final class JavaTranslationPolicy {
  private final AstProfile selectedProfile;

  private JavaTranslationPolicy(AstProfile profile) {
    selectedProfile = Objects.requireNonNull(profile, "profile");
    if (!profile.isSupported()) {
      throw new IllegalArgumentException("Unsupported source profile: " + profile.version());
    }
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
    return "javac-public/9";
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
