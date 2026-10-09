package org.aquilagraph.source.api;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;

/** Reconciles case accessor aliases and form-specific absence against canonical label subtrees. */
final class CoverageSwitchFacts {
  private CoverageSwitchFacts() {
  }

  static boolean supports(String accessor, CoveragePropertyValue expected, CoverageSyntaxFactValue owner, CoverageActualFactView facts,
      InputSnapshot snapshot) {
    var value = (SyntaxValue.SwitchCase) owner.value();
    return switch (accessor) {
      case "getKind" -> expected.equals(scalar("CASE"));
      case "getCaseKind" -> expected.equals(scalar(value.form() == SyntaxValue.SwitchCaseForm.RULE ? "RULE" : "STATEMENT"));
      case "getGuard" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.GUARD), facts, snapshot, true);
      case "getBody" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.BODY), facts, snapshot, true);
      case "getStatements" -> value.form() == SyntaxValue.SwitchCaseForm.RULE
          ? expected.state() == State.ABSENT && expected.items().isEmpty()
              && owner.children().stream().noneMatch(child -> child.role() == SyntaxRole.STATEMENT)
          : CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.STATEMENT), facts, snapshot, false);
      case "getLabels", "getExpressions" -> labels(accessor, expected, value, owner, facts, snapshot);
      default -> false;
    };
  }

  private static CoveragePropertyValue scalar(String text) {
    return new CoveragePropertyValue(State.SCALAR, List.of(new Item(text, text, Provenance.WRITTEN)));
  }

  private static boolean labels(String accessor, CoveragePropertyValue expected, SyntaxValue.SwitchCase value,
      CoverageSyntaxFactValue owner, CoverageActualFactView facts, InputSnapshot snapshot) {
    var items = new ArrayList<Item>();
    var expressions = "getExpressions".equals(accessor);
    for (var label : value.labels()) {
      if (label instanceof SyntaxValue.SwitchLabel.Default) {
        if (!expressions) {
          items.add(new Item("DEFAULT_CASE_LABEL:" + items.size(), "default", Provenance.WRITTEN));
        }
        continue;
      }
      if (expressions && label instanceof SyntaxValue.SwitchLabel.Pattern) {
        continue;
      }
      var ordinal = label instanceof SyntaxValue.SwitchLabel.Expression expression
          ? expression.childPosition()
          : ((SyntaxValue.SwitchLabel.Pattern) label).childPosition();
      var child = owner.children().stream().filter(candidate -> candidate.role() == SyntaxRole.LABEL && candidate.ordinal() == ordinal)
          .findFirst();
      if (child.isEmpty()) {
        return false;
      }
      var fact = facts.stream().filter(candidate -> candidate.identity().equals(child.orElseThrow().nodeKey())).findFirst();
      if (fact.isEmpty() || fact.orElseThrow().origin().isEmpty()) {
        return false;
      }
      var source = snapshot.sourceFiles().stream().filter(file -> file.sourceFileKey().equals(fact.orElseThrow().sourceFileKey()))
          .findFirst().orElseThrow();
      var origin = fact.orElseThrow().origin().orElseThrow();
      var text = source.content().substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive()));
      var kind = expressions
          ? facts.requiredSyntax(fact.orElseThrow()).sourceKind().name()
          : label instanceof SyntaxValue.SwitchLabel.Pattern ? "PATTERN_CASE_LABEL" : "CONSTANT_CASE_LABEL";
      items.add(new Item(kind + ':' + items.size(), text, Provenance.WRITTEN));
    }
    return expected.equals(new CoveragePropertyValue(items.isEmpty() ? State.EMPTY : State.ITEMS, items));
  }
}
