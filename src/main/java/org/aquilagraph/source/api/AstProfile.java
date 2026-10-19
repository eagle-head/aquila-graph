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

  /** Expanded Java 25 source including local and anonymous type declarations. */
  public static final AstProfile JAVA_SOURCE_NESTED = new AstProfile("java-source-expanded/3");

  /** Expanded Java 25 source including enum arguments and constant-specific bodies. */
  public static final AstProfile JAVA_SOURCE_ENUM_INITIALIZATION = new AstProfile("java-source-expanded/4");

  /** Expanded Java 25 declaration fidelity, including standalone body declarations. */
  public static final AstProfile JAVA_SOURCE_DECLARATIONS = new AstProfile("java-source-expanded/5");

  /** Expanded Java 25 compilation units, including captured legal units without types. */
  public static final AstProfile JAVA_SOURCE_UNITS = new AstProfile("java-source-expanded/6");

  /** Full Java source with independent written-expression region proof. */
  public static final AstProfile JAVA_SOURCE_FIDELITY = new AstProfile("java-source-expanded/7");

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
    return JAVA_SOURCE_BOUNDED.equals(this) || JAVA_SOURCE_EXPANDED.equals(this) || JAVA_SOURCE_INITIALIZATION.equals(this)
        || JAVA_SOURCE_NESTED.equals(this) || JAVA_SOURCE_ENUM_INITIALIZATION.equals(this) || supportsDeclarationFidelity();
  }

  /**
   * Identifies contracts with declaration-owned field and block execution scopes.
   *
   * @return whether member initialization is represented
   */
  public boolean supportsMemberInitialization() {
    return JAVA_SOURCE_INITIALIZATION.equals(this) || supportsNestedDeclarations();
  }

  /**
   * Identifies contracts with local and anonymous declaration bodies.
   *
   * @return whether nested declarations are represented
   */
  public boolean supportsNestedDeclarations() {
    return JAVA_SOURCE_NESTED.equals(this) || supportsEnumInitialization();
  }

  /**
   * Identifies contracts with directly owned enum arguments and constant bodies.
   *
   * @return whether enum initialization is represented
   */
  public boolean supportsEnumInitialization() {
    return JAVA_SOURCE_ENUM_INITIALIZATION.equals(this) || supportsDeclarationFidelity();
  }

  /**
   * Identifies contracts retaining exact empty body-declaration occurrences.
   *
   * @return whether declaration fidelity is represented
   */
  public boolean supportsDeclarationFidelity() {
    return JAVA_SOURCE_DECLARATIONS.equals(this) || supportsSourceEnvelope();
  }

  /**
   * Identifies contracts using the captured file envelope independently of declared types.
   *
   * @return whether complete unit envelopes are represented
   */
  public boolean supportsSourceEnvelope() {
    return JAVA_SOURCE_UNITS.equals(this) || supportsWrittenExpressions();
  }
  /**
   * Identifies contracts requiring independent proof of normalized written regions.
   *
   * @return whether written-expression evidence is mandatory
   */
  public boolean supportsWrittenExpressions() {
    return JAVA_SOURCE_FIDELITY.equals(this);
  }
}
