package org.aquilagraph.source.admission;

import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import java.util.Optional;
import org.aquilagraph.source.api.SourceOwnerPath.Role;

/** Checks actual type-use parent attachments without consulting coverage expectations or outcomes. */
public final class CoverageWrittenTypeUseTopology {
  private static final int EXPECTED_PARENT_ATTACHMENTS = 1;
  private CoverageWrittenTypeUseTopology() {
  }

  /**
   * Checks that the source association extends the unique actual parent's exact parsed role and ordinal.
   *
   * @param reference
   *          exact actual type-use reference
   * @param facts
   *          independently enumerated actual facts
   * @return whether the direct actual attachment and copied parsed relationship agree
   */
  public static boolean matchesParent(CoverageFactReference reference, CoverageActualFactView facts) {
    var source = facts.sourceValue(reference);
    if (source.isEmpty() || source.orElseThrow().syntaxKind() != SyntaxKind.TYPE_USE
        || source.orElseThrow().sourceAssociation().isEmpty()) {
      return false;
    }
    var association = source.orElseThrow().sourceAssociation().orElseThrow();
    var parents = facts.stream().filter(fact -> fact.sourceFileKey().equals(reference.sourceFileKey())).flatMap(facts::syntaxValues)
        .flatMap(parent -> parent.children().stream().filter(child -> child.nodeKey().equals(reference.identity()))
            .map(child -> new Parent(parent, child)))
        .toList();
    if (parents.size() != EXPECTED_PARENT_ATTACHMENTS) {
      return false;
    }
    var parent = parents.getFirst();
    var role = parsedRole(parent.source().sourceKind(), parent.child().role());
    return role.isPresent() && parent.source().sourceAssociation().filter(owner -> owner.sourceFileKey().equals(association.sourceFileKey())
        && owner.ownerPath().child(role.orElseThrow(), parent.child().ordinal()).equals(association.ownerPath())).isPresent();
  }

  private static Optional<Role> parsedRole(JavaSourceKind parent, SyntaxRole role) {
    return switch (role) {
      case DECLARED_TYPE -> Optional.of(Role.DECLARED_TYPE);
      case RETURN_TYPE -> Optional.of(Role.RETURN_TYPE);
      case THROWN_TYPE -> Optional.of(Role.THROWN_TYPE);
      case SUPERCLASS -> Optional.of(Role.SUPERCLASS);
      case INTERFACE -> Optional.of(Role.INTERFACE);
      case PERMITTED_SUBCLASS -> Optional.of(Role.PERMITTED_SUBCLASS);
      case TYPE_BOUND -> Optional.of(Role.TYPE_BOUND);
      case TYPE_NAME -> Optional.of(Role.TYPE_NAME);
      case TYPE_ARGUMENT -> Optional.of(Role.TYPE_ARGUMENT);
      case TYPE_QUALIFIER -> Optional.of(Role.TYPE_QUALIFIER);
      case COMPONENT_TYPE -> Optional.of(Role.COMPONENT_TYPE);
      case UNDERLYING_TYPE -> Optional.of(Role.UNDERLYING_TYPE);
      case UPPER_BOUND -> Optional.of(Role.UPPER_BOUND);
      case LOWER_BOUND -> Optional.of(Role.LOWER_BOUND);
      case INTERSECTION_BOUND -> Optional.of(Role.INTERSECTION_BOUND);
      case UNION_ALTERNATIVE -> Optional.of(Role.UNION_ALTERNATIVE);
      case TYPE_OPERAND -> switch (parent) {
        case MEMBER_SELECT -> Optional.of(Role.TYPE_QUALIFIER);
        case TYPE_CAST -> Optional.of(Role.CAST_TYPE);
        case NEW_CLASS -> Optional.of(Role.CONSTRUCTED_TYPE);
        case NEW_ARRAY -> Optional.of(Role.ARRAY_ELEMENT_TYPE);
        case INSTANCE_OF -> Optional.of(Role.TESTED_TYPE);
        case DECONSTRUCTION_PATTERN -> Optional.of(Role.DECONSTRUCTOR);
        default -> Optional.empty();
      };
      default -> Optional.empty();
    };
  }

  private record Parent(CoverageSyntaxFactValue source, CoverageSyntaxFactValue.Child child) {
  }
}
