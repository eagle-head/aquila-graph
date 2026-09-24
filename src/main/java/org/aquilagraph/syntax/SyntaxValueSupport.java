package org.aquilagraph.syntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Shared validation for scalar syntax evidence. */
final class SyntaxValueSupport {
  static final String WRITTEN_MODIFIERS_ARGUMENT = "writtenModifiers";
  static final String EFFECTIVE_MODIFIERS_ARGUMENT = "effectiveModifiers";
  static final String OWNER_KEY_ARGUMENT = "ownerKey";
  static final String DECLARATION_KEY_ARGUMENT = "declarationKey";
  private static final Set<String> RESERVED_TYPE_IDENTIFIERS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch",
      "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for",
      "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected",
      "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
      "void", "volatile", "while", "_", "true", "false", "null", "permits", "record", "sealed", "var", "yield");

  private SyntaxValueSupport() {
  }

  static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }

  static void requireName(String value) {
    requireText(value, "name");
  }

  static boolean isTypeIdentifier(String value) {
    return !value.isEmpty() && !RESERVED_TYPE_IDENTIFIERS.contains(value) && Character.isJavaIdentifierStart(value.codePointAt(0))
        && value.codePoints().allMatch(Character::isJavaIdentifierPart);
  }

  static List<SyntaxValue.QualifiedName> copyProviders(List<SyntaxValue.QualifiedName> providers) {
    var copy = List.copyOf(Objects.requireNonNull(providers, "providers"));
    if (copy.isEmpty()) {
      throw new IllegalArgumentException("A provides directive requires at least one provider");
    }
    return copy;
  }

  static Optional<List<SyntaxValue.QualifiedName>> copyTargets(Optional<List<SyntaxValue.QualifiedName>> targets) {
    return Objects.requireNonNull(targets, "targets").map(names -> {
      var copy = List.copyOf(names);
      if (copy.isEmpty()) {
        throw new IllegalArgumentException("A qualified module directive requires at least one target");
      }
      return copy;
    });
  }
}
