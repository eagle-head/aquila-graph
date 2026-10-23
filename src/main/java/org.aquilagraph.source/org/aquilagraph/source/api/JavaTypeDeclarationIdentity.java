package org.aquilagraph.source.api;

import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.StableId;

/**
 * Immutable module-qualified identity of a canonical Java type declaration.
 *
 * @param moduleName
 *          named module, or empty for the explicitly unnamed module
 * @param binaryName
 *          compiler-reported binary type name
 */
public record JavaTypeDeclarationIdentity(Optional<String> moduleName, String binaryName) {
  /** Validates the copied module and binary name. */
  public JavaTypeDeclarationIdentity {
    Objects.requireNonNull(moduleName, "moduleName");
    if (moduleName.filter(String::isBlank).isPresent() || Objects.requireNonNull(binaryName, "binaryName").isBlank()) {
      throw new IllegalArgumentException("A type declaration requires an explicit module kind and nonblank names");
    }
  }

  /**
   * Derives the canonical declaration key using unambiguous component framing.
   *
   * @return module-qualified declaration identity
   */
  public String declarationKey() {
    return StableId.sha256("java-type-declaration", moduleName.isPresent() ? "named" : "unnamed", moduleName.orElse(""), binaryName);
  }

  /**
   * Recognizes a platform declaration under either supported identity policy.
   *
   * @param key
   *          copied declaration key
   * @param binaryName
   *          exact java.base binary name
   * @return whether the key denotes that platform declaration
   */
  public static boolean isPlatformType(String key, String binaryName) {
    return ("type:" + binaryName).equals(key)
        || new JavaTypeDeclarationIdentity(Optional.of("java.base"), binaryName).declarationKey().equals(key);
  }

  /**
   * Recognizes an unparameterized platform type structure without accepting other declarations.
   *
   * @param structure
   *          copied type structure
   * @param binaryName
   *          exact java.base binary name
   * @return whether the complete structure denotes the platform type
   */
  public static boolean isPlatformType(JavaTypeStructure structure, String binaryName) {
    return structure instanceof JavaTypeStructure.Declared declared && isPlatformType(declared.declarationKey(), binaryName)
        && declared.arguments().isEmpty() && declared.enclosingType().isEmpty() && declared.lexicalOwnerKey().isEmpty();
  }
}
