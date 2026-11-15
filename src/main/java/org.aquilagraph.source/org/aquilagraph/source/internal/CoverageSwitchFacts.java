package org.aquilagraph.source.internal;

import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.LIST;
import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.OPTIONAL_VALUE;

import org.aquilagraph.source.admission.CoverageActualFactView;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;

/** Reconciles case accessor aliases and form-specific absence against canonical label subtrees. */
public final class CoverageSwitchFacts {
  private CoverageSwitchFacts() {
  }

  /**
   * Checks whether the copied facts establish the requested coverage property.
   *
   * @param accessor
   *          accessor
   * @param expected
   *          expected
   * @param owner
   *          owner
   * @param facts
   *          facts
   * @return whether the facts satisfy the required relation
   */
  public static boolean supports(String accessor, CoveragePropertyValue expected, CoverageFactReference owner,
      CoverageActualFactView facts) {
    var value = (SyntaxValue.SwitchCase) facts.requiredSyntax(owner).value();
    return switch (accessor) {
      case "getKind" -> expected.equals(scalar("CASE"));
      case "getCaseKind" -> expected.equals(scalar(value.form() == SyntaxValue.SwitchCaseForm.RULE ? "RULE" : "STATEMENT"));
      case "getGuard" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.GUARD), facts, OPTIONAL_VALUE);
      case "getBody" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.BODY), facts, OPTIONAL_VALUE);
      case "getStatements" -> value.form() == SyntaxValue.SwitchCaseForm.RULE
          ? expected.state() == State.ABSENT && expected.items().isEmpty() && facts.children(owner, List.of(SyntaxRole.STATEMENT)).isEmpty()
          : CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.STATEMENT), facts, LIST);
      case "getLabels", "getExpressions" -> labels(accessor, expected, value, owner, facts);
      default -> false;
    };
  }

  private static CoveragePropertyValue scalar(String text) {
    return new CoveragePropertyValue(State.SCALAR, List.of(new Item(text, text, Provenance.WRITTEN)));
  }

  private static boolean labels(String accessor, CoveragePropertyValue expected, SyntaxValue.SwitchCase value, CoverageFactReference owner,
      CoverageActualFactView facts) {
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
      var child = facts.child(owner, SyntaxRole.LABEL, ordinal);
      var text = child.flatMap(facts::capturedText);
      if (text.isEmpty()) {
        return false;
      }
      var observed = child.map(facts::requiredSyntax).flatMap(syntax -> syntax.observedCompilerKind());
      if (expressions && observed.isEmpty()) {
        return false;
      }
      var kind = expressions
          ? observed.orElseThrow().name()
          : label instanceof SyntaxValue.SwitchLabel.Pattern ? "PATTERN_CASE_LABEL" : "CONSTANT_CASE_LABEL";
      items.add(new Item(kind + ':' + items.size(), text.orElseThrow(), Provenance.WRITTEN));
    }
    return expected.equals(new CoveragePropertyValue(items.isEmpty() ? State.EMPTY : State.ITEMS, items));
  }
}
