package org.aquilagraph.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Copied compiler order of the canonical direct members of one translated type.
 *
 * @param sourceFileKey
 *          captured source that owns the type
 * @param ownerTypeKey
 *          canonical declaring type identity
 * @param members
 *          copied direct members in compiler order
 */
public record DeclarationMemberOrder(String sourceFileKey, String ownerTypeKey, List<Member> members) {
  /** Validates source ownership and unique member identities without claiming compiler provenance. */
  public DeclarationMemberOrder {
    requireText(sourceFileKey);
    requireText(ownerTypeKey);
    members = List.copyOf(Objects.requireNonNull(members, "members"));
    var keys = new HashSet<String>();
    for (var member : members) {
      if (member.declarationKey().equals(ownerTypeKey) || !keys.add(member.declarationKey())) {
        throw new IllegalArgumentException("Ordered declaration members require unique canonical owner identities");
      }
    }
  }

  /**
   * One copied canonical member and its exact public compiler projection.
   *
   * @param declarationKey
   *          canonical member declaration identity
   * @param kind
   *          copied public compiler element kind
   * @param displayValue
   *          copied public compiler display text
   * @param provenance
   *          relationship of the member to written source
   * @param writtenOrdinal
   *          original parsed member position, when present
   */
  public record Member(String declarationKey, String kind, String displayValue, CoveragePropertyValue.Provenance provenance,
      Optional<Integer> writtenOrdinal) {
    /** Validates immutable copied values. */
    public Member {
      requireText(declarationKey);
      requireText(kind);
      requireText(displayValue);
      Objects.requireNonNull(provenance, "provenance");
      Objects.requireNonNull(writtenOrdinal, "writtenOrdinal");
      if (writtenOrdinal.filter(value -> value < 0).isPresent()) {
        throw new IllegalArgumentException("Written member attachment ordinal cannot be negative");
      }
    }
  }

  private static void requireText(String value) {
    if (Objects.requireNonNull(value, "value").isBlank()) {
      throw new IllegalArgumentException("Declaration member order identities and values cannot be blank");
    }
  }
}
