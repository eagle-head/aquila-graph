package org.aquilagraph.source.api;

import java.util.List;
import java.util.Map;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;

/** Reconciles independent parsed iteration accessors against canonical written source values. */
final class CoverageIterationFacts {
  private static final Map<String, JavaSourceKind> SURFACES = Map.of("WhileLoopTree", JavaSourceKind.WHILE_LOOP, "DoWhileLoopTree",
      JavaSourceKind.DO_WHILE_LOOP, "ForLoopTree", JavaSourceKind.FOR_LOOP, "EnhancedForLoopTree", JavaSourceKind.ENHANCED_FOR_LOOP,
      "LabeledStatementTree", JavaSourceKind.LABELED_STATEMENT, "BreakTree", JavaSourceKind.BREAK, "ContinueTree", JavaSourceKind.CONTINUE);

  private CoverageIterationFacts() {
  }

  static boolean isSurface(String role) {
    var separator = role.indexOf("::");
    return separator >= 0 && SURFACES.containsKey(role.substring(0, separator));
  }

  static boolean matches(String role, CoverageSyntaxFactValue value) {
    return value.sourceKind() == SURFACES.get(role.substring(0, role.indexOf("::"))) && value.syntaxKind() != SyntaxKind.UNSUPPORTED;
  }

  static boolean supports(String accessor, CoveragePropertyValue expected, CoverageSyntaxFactValue owner, CoverageActualFactView facts,
      InputSnapshot snapshot) {
    return switch (accessor) {
      case "getKind" -> expected.equals(new CoveragePropertyValue(State.SCALAR,
          List.of(new Item(owner.sourceKind().name(), owner.sourceKind().name(), Provenance.WRITTEN))));
      case "getLabel" -> owner.value() instanceof SyntaxValue.StatementLabel label && expected
          .equals(label.name().map(name -> new CoveragePropertyValue(State.SCALAR, List.of(new Item("item:0", name, Provenance.WRITTEN))))
              .orElse(new CoveragePropertyValue(State.ABSENT, List.of())));
      case "getCondition" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.CONDITION), facts, snapshot, true);
      case "getStatement" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.BODY), facts, snapshot, true);
      case "getInitializer" ->
        CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.INITIALIZER), facts, snapshot, false);
      case "getUpdate" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.UPDATE), facts, snapshot, false);
      case "getVariable" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.PARAMETER), facts, snapshot, true);
      case "getExpression" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.ITERABLE), facts, snapshot, true);
      default -> false;
    };
  }
}
