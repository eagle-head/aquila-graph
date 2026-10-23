package org.aquilagraph.source.internal;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.admission.CoverageActualFactView;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactReference.Kind;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxRole;

/** Reconciles written attachment order and exact captured source text. */
public final class CoverageWrittenAttachments {
  private CoverageWrittenAttachments() {
  }

  /**
   * Derives the written attachment facts for the selected declaration or source node.
   *
   * @param expected
   *          expected
   * @param owner
   *          owner
   * @param roles
   *          roles
   * @param actualFacts
   *          actual facts
   * @param snapshot
   *          snapshot
   * @return whether the facts satisfy the required relation
   */
  public static boolean matches(CoveragePropertyValue expected, CoverageSyntaxFactValue owner, List<SyntaxRole> roles,
      CoverageActualFactView actualFacts, InputSnapshot snapshot) {
    var scalar = roles.size() == 1 && switch (roles.getFirst()) {
      case BODY, DEFAULT_VALUE, RECEIVER_PARAMETER, QUALIFIER, RETURN_TYPE, INITIALIZER, PACKAGE_DECLARATION, MODULE_DECLARATION,
          SUPERCLASS, DECLARED_TYPE, TYPE_OPERAND ->
        true;
      default -> false;
    };
    return matches(expected, owner, roles, actualFacts, snapshot, scalar);
  }

  /**
   * Derives the written attachment facts for the selected declaration or source node.
   *
   * @param expected
   *          expected
   * @param owner
   *          owner
   * @param roles
   *          roles
   * @param actualFacts
   *          actual facts
   * @param snapshot
   *          snapshot
   * @param scalar
   *          scalar
   * @return whether the facts satisfy the required relation
   */
  public static boolean matches(CoveragePropertyValue expected, CoverageSyntaxFactValue owner, List<SyntaxRole> roles,
      CoverageActualFactView actualFacts, InputSnapshot snapshot, boolean scalar) {
    var children = select(owner, roles, actualFacts).stream()
        .filter(child -> !roles.equals(List.of(SyntaxRole.TYPE_DECLARATION)) || actualFacts.requiredSyntax(child).sourceAssociation()
            .flatMap(value -> value.ownerPath().lastStep()).filter(step -> step.role() == SourceOwnerPath.Role.EMPTY_DECLARATION).isEmpty())
        .toList();
    var actualState = children.isEmpty() ? (scalar ? State.ABSENT : State.EMPTY) : (scalar ? State.SCALAR : State.ITEMS);
    if (expected.state() != actualState) {
      return false;
    }
    if (children.isEmpty()) {
      return true;
    }
    if (expected.items().size() != children.size()) {
      return false;
    }
    for (var index = 0; index < children.size(); index++) {
      var child = children.get(index);
      var item = expected.items().get(index);
      if (child.origin().isEmpty()) {
        return false;
      }
      var syntax = actualFacts.requiredSyntax(child);
      var source = snapshot.sourceFiles().stream().filter(file -> file.sourceFileKey().equals(child.sourceFileKey())).findFirst()
          .orElseThrow();
      var origin = child.origin().orElseThrow();
      var text = source.content().substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
      if (!writtenItem(syntax, index, text).filter(item::equals).isPresent()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Selects actual written child facts in their retained structural order.
   *
   * @param owner
   *          copied structural owner
   * @param roles
   *          selected attachment roles
   * @param actualFacts
   *          complete retained fact view
   * @return ordered actual written children
   */
  public static List<CoverageFactReference> select(CoverageSyntaxFactValue owner, List<SyntaxRole> roles,
      CoverageActualFactView actualFacts) {
    return owner.children().stream().filter(child -> roles.contains(child.role()))
        .map(child -> actualFacts.stream().filter(fact -> fact.kind() == Kind.SYNTAX && fact.identity().equals(child.nodeKey())).findFirst()
            .orElseThrow())
        .filter(fact -> actualFacts.requiredSyntax(fact).sourceAssociation().flatMap(association -> association.ownerPath().lastStep())
            .filter(step -> step.role() == SourceOwnerPath.Role.EMPTY_DECLARATION).isEmpty())
        .toList();
  }

  private static Optional<CoveragePropertyValue.Item> writtenItem(CoverageSyntaxFactValue syntax, int ordinal, String text) {
    return syntax.observedCompilerKind()
        .map(kind -> new CoveragePropertyValue.Item(kind.name() + ':' + ordinal, text, CoveragePropertyValue.Provenance.WRITTEN));
  }
}
