package org.aquilagraph.source.api;

import java.util.Objects;

/**
 * Versioned source representation contract, independent of graph support.
 *
 * @param version
 *          exact source profile version
 */
public record AstProfile(String version) {
  /** Initial bounded, non-preview Java 25 source profile. */
  public static final AstProfile JAVA_SOURCE_BOUNDED = new AstProfile("java-source-bounded/1");

  /** Expanded non-preview Java 25 source profile; unrepresented forms remain incomplete. */
  public static final AstProfile JAVA_SOURCE_EXPANDED = new AstProfile("java-source-expanded/1");

  /** Expanded Java 25 executable syntax in methods, fields and static/instance initializer blocks. */
  public static final AstProfile JAVA_SOURCE_INITIALIZATION = new AstProfile("java-source-expanded/2");

  /** Requires an explicit version without interpreting unknown versions. */
  public AstProfile {
    if (Objects.requireNonNull(version, "version").isBlank()) {
      throw new IllegalArgumentException("An AST profile requires a nonblank version");
    }
  }

  /**
   * Identifies source representation contracts supported by this model version.
   *
   * @return whether source admission recognizes this exact profile version
   */
  public boolean isSupported() {
    return JAVA_SOURCE_BOUNDED.equals(this) || JAVA_SOURCE_EXPANDED.equals(this) || JAVA_SOURCE_INITIALIZATION.equals(this);
  }
}
