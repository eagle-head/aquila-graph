package org.aquilagraph.source.api;

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
 * @param attributedTreeOrder
 *          parsed ownership of each attributed tree member, including inserted members without written owners
 */
public record DeclarationMemberOrder(String sourceFileKey, String ownerTypeKey, List<Member> members,
    Optional<TreeOrder> attributedTreeOrder) {
  /** Validates source ownership and unique member identities without claiming compiler provenance. */
  public DeclarationMemberOrder {
    requireText(sourceFileKey);
    requireText(ownerTypeKey);
    Objects.requireNonNull(attributedTreeOrder, "attributedTreeOrder");
    members = List.copyOf(Objects.requireNonNull(members, "members"));
    var keys = new HashSet<String>();
    for (var member : members) {
      if (member.declarationKey().equals(ownerTypeKey) || !keys.add(member.declarationKey())) {
        throw new IllegalArgumentException("Ordered declaration members require unique canonical owner identities");
      }
    }
  }

  /**
   * Independent parsed ownership in the actual attributed ClassTree member order.
   *
   * @param ownerPath
   *          parsed type declaration path
   * @param members
   *          one slot per attributed tree member, empty only for compiler-inserted members
   */
  public record TreeOrder(SourceOwnerPath ownerPath, List<Optional<SourceOwnerPath>> members) {
    /** Copies and validates direct, unique parsed member attachments. */
    public TreeOrder {
      Objects.requireNonNull(ownerPath, "ownerPath");
      members = List.copyOf(Objects.requireNonNull(members, "members"));
      var owners = new HashSet<SourceOwnerPath>();
      for (var member : members) {
        Objects.requireNonNull(member, "member").ifPresent(path -> {
          if (!path.parent().equals(Optional.of(ownerPath)) || path.lastStep().orElseThrow().role() != SourceOwnerPath.Role.MEMBER
              || !owners.add(path)) {
            throw new IllegalArgumentException("Attributed tree order requires unique direct parsed member owners");
          }
        });
      }
      var writtenOrder = members.stream().flatMap(Optional::stream).map(path -> path.lastStep().orElseThrow().ordinal()).toList();
      if (!writtenOrder.equals(writtenOrder.stream().sorted().toList())) {
        throw new IllegalArgumentException("Attributed tree order must preserve parsed member ordering");
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
