package org.aquilagraph.source.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.SourceSpan;

/**
 * A written record-header declaration, separate from its compiler-derived members.
 *
 * @param componentKey
 *          stable owner-and-ordinal declaration key
 * @param ownerTypeKey
 *          declaring record key
 * @param ordinal
 *          zero-based header position
 * @param name
 *          written component name
 * @param staticType
 *          attributed component type
 * @param writtenModifiers
 *          source-written modifier flags
 * @param effectiveModifiers
 *          attributed modifier flags
 * @param varArgs
 *          whether this is the variable-arity final component
 * @param accessorKey
 *          correlated accessor identity
 * @param backingFieldKey
 *          correlated backing-field identity
 * @param canonicalParameterKey
 *          correlated canonical-constructor parameter identity
 * @param origin
 *          written source range
 */
public record JavaRecordComponent(String componentKey, String ownerTypeKey, int ordinal, String name, JavaTypeIdentity staticType,
    Set<JavaTypeModifier> writtenModifiers, Set<JavaTypeModifier> effectiveModifiers, boolean varArgs, Optional<String> accessorKey,
    Optional<String> backingFieldKey, Optional<String> canonicalParameterKey, SourceSpan origin) {
  /** Validates component identity, ownership, and compiler-derived relationships. */
  public JavaRecordComponent {
    requireText(componentKey, "componentKey");
    requireText(ownerTypeKey, "ownerTypeKey");
    requireText(name, "name");
    if (ordinal < 0 || !componentKey.equals(ownerTypeKey + "#record-component:" + ordinal)) {
      throw new IllegalArgumentException("A record component requires its owner-and-ordinal identity");
    }
    Objects.requireNonNull(staticType, "staticType");
    writtenModifiers = Set.copyOf(Objects.requireNonNull(writtenModifiers, "writtenModifiers"));
    effectiveModifiers = Set.copyOf(Objects.requireNonNull(effectiveModifiers, "effectiveModifiers"));
    Objects.requireNonNull(accessorKey, "accessorKey");
    Objects.requireNonNull(backingFieldKey, "backingFieldKey");
    Objects.requireNonNull(canonicalParameterKey, "canonicalParameterKey");
    accessorKey.ifPresent(key -> requireText(key, "accessorKey"));
    backingFieldKey.ifPresent(key -> requireText(key, "backingFieldKey"));
    canonicalParameterKey.ifPresent(key -> requireText(key, "canonicalParameterKey"));
    Objects.requireNonNull(origin, "origin");
  }

  /**
   * Returns the sole written source range of this component.
   *
   * @return a single source origin
   */
  public List<SourceSpan> origins() {
    return List.of(origin);
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " cannot be blank");
    }
  }
}
