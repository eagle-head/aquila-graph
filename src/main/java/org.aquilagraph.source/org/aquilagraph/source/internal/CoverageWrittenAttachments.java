package org.aquilagraph.source.internal;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.source.admission.CoverageActualFactView;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxRole;

/** Reconciles written attachment order and exact captured source text. */
public final class CoverageWrittenAttachments {
  private CoverageWrittenAttachments() {
  }

  /** Written attachment states selected by the actual accessor's syntax contract. */
  public enum Cardinality {
    /** A missing or single written value. */
    OPTIONAL_VALUE(State.ABSENT, State.SCALAR),
    /** An empty or populated written list. */
    LIST(State.EMPTY, State.ITEMS),
    /** A missing or populated written list. */
    OPTIONAL_LIST(State.ABSENT, State.ITEMS);

    private final State whenEmpty;
    private final State whenPresent;

    Cardinality(State whenEmpty, State whenPresent) {
      this.whenEmpty = whenEmpty;
      this.whenPresent = whenPresent;
    }
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
   * @param cardinality
   *          actual accessor's written attachment cardinality
   * @return whether the facts satisfy the required relation
   */
  public static boolean matches(CoveragePropertyValue expected, CoverageFactReference owner, List<SyntaxRole> roles,
      CoverageActualFactView actualFacts, Cardinality cardinality) {
    var children = select(owner, roles, actualFacts);
    var actualState = children.isEmpty() ? cardinality.whenEmpty : cardinality.whenPresent;
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
      var text = actualFacts.capturedText(child);
      if (text.isEmpty() || writtenItem(actualFacts.requiredSyntax(child), index, text.orElseThrow()).filter(item::equals).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static List<CoverageFactReference> select(CoverageFactReference owner, List<SyntaxRole> roles,
      CoverageActualFactView actualFacts) {
    return actualFacts.children(owner, roles).stream()
        .filter(fact -> actualFacts.requiredSyntax(fact).sourceAssociation().flatMap(association -> association.ownerPath().lastStep())
            .filter(step -> step.role() == SourceOwnerPath.Role.EMPTY_DECLARATION).isEmpty())
        .toList();
  }

  private static Optional<CoveragePropertyValue.Item> writtenItem(CoverageSyntaxFactValue syntax, int ordinal, String text) {
    return syntax.observedCompilerKind()
        .map(kind -> new CoveragePropertyValue.Item(kind.name() + ':' + ordinal, text, CoveragePropertyValue.Provenance.WRITTEN));
  }
}
