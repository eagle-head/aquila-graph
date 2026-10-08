package org.aquilagraph.source.api;

import java.util.List;
import java.util.Map;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;

/** Reconciles independent executable accessors against canonical written source values. */
final class CoverageExecutableFacts {
  private static final Map<String, JavaSourceKind> SURFACES = Map.ofEntries(Map.entry("WhileLoopTree", JavaSourceKind.WHILE_LOOP),
      Map.entry("DoWhileLoopTree", JavaSourceKind.DO_WHILE_LOOP), Map.entry("ForLoopTree", JavaSourceKind.FOR_LOOP),
      Map.entry("EnhancedForLoopTree", JavaSourceKind.ENHANCED_FOR_LOOP),
      Map.entry("LabeledStatementTree", JavaSourceKind.LABELED_STATEMENT), Map.entry("BreakTree", JavaSourceKind.BREAK),
      Map.entry("ContinueTree", JavaSourceKind.CONTINUE), Map.entry("AssertTree", JavaSourceKind.ASSERT),
      Map.entry("SynchronizedTree", JavaSourceKind.SYNCHRONIZED), Map.entry("LambdaExpressionTree", JavaSourceKind.LAMBDA_EXPRESSION),
      Map.entry("MemberReferenceTree", JavaSourceKind.MEMBER_REFERENCE), Map.entry("InstanceOfTree", JavaSourceKind.INSTANCE_OF),
      Map.entry("BindingPatternTree", JavaSourceKind.BINDING_PATTERN),
      Map.entry("DeconstructionPatternTree", JavaSourceKind.DECONSTRUCTION_PATTERN),
      Map.entry("AnyPatternTree", JavaSourceKind.ANY_PATTERN));

  private CoverageExecutableFacts() {
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
      case "getPattern" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.PATTERN), facts, snapshot, true);
      case "getDeconstructor" ->
        CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.TYPE_OPERAND), facts, snapshot, true);
      case "getNestedPatterns" ->
        CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.PATTERN_COMPONENT), facts, snapshot, false);
      case "getType" -> CoveragePatternFacts.testedType(expected, owner, facts, snapshot);
      case "getMode" ->
        owner.value() instanceof SyntaxValue.MemberReference reference && expected.equals(new CoveragePropertyValue(State.SCALAR,
            List.of(new Item(reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR ? "NEW" : "INVOKE",
                reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR ? "NEW" : "INVOKE", Provenance.WRITTEN))));
      case "getName" -> owner.value() instanceof SyntaxValue.MemberReference reference
          && expected.equals(new CoveragePropertyValue(State.SCALAR, List.of(new Item("item:0",
              reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR ? "<init>" : reference.name(), Provenance.WRITTEN))));
      case "getQualifierExpression" ->
        CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.QUALIFIER), facts, snapshot, true);
      case "getTypeArguments" -> expected.state() == State.ABSENT
          ? owner.children().stream().noneMatch(child -> child.role() == SyntaxRole.TYPE_ARGUMENT)
          : CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.TYPE_ARGUMENT), facts, snapshot, false);
      case "getKind" -> expected.equals(new CoveragePropertyValue(State.SCALAR,
          List.of(new Item(owner.sourceKind().name(), owner.sourceKind().name(), Provenance.WRITTEN))));
      case "getLabel" -> owner.value() instanceof SyntaxValue.StatementLabel label && expected
          .equals(label.name().map(name -> new CoveragePropertyValue(State.SCALAR, List.of(new Item("item:0", name, Provenance.WRITTEN))))
              .orElse(new CoveragePropertyValue(State.ABSENT, List.of())));
      case "getCondition" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.CONDITION), facts, snapshot, true);
      case "getStatement", "getBlock", "getBody" ->
        CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.BODY), facts, snapshot, true);
      case "getInitializer" ->
        CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.INITIALIZER), facts, snapshot, false);
      case "getUpdate" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.UPDATE), facts, snapshot, false);
      case "getVariable" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.PARAMETER), facts, snapshot, true);
      case "getParameters" ->
        CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.PARAMETER), facts, snapshot, false);
      case "getBodyKind" -> owner.value() instanceof SyntaxValue.Lambda lambda && expected.equals(new CoveragePropertyValue(State.SCALAR,
          List.of(new Item(lambda.bodyKind() == SyntaxValue.LambdaBodyKind.BLOCK ? "STATEMENT" : "EXPRESSION",
              lambda.bodyKind() == SyntaxValue.LambdaBodyKind.BLOCK ? "STATEMENT" : "EXPRESSION", Provenance.WRITTEN))));
      case "getExpression" -> CoverageActualFacts.writtenAttachments(expected, owner,
          List.of(owner.sourceKind() == JavaSourceKind.INSTANCE_OF
              ? SyntaxRole.EXPRESSION
              : owner.sourceKind() == JavaSourceKind.ENHANCED_FOR_LOOP ? SyntaxRole.ITERABLE : SyntaxRole.MONITOR),
          facts, snapshot, true);
      case "getDetail" -> CoverageActualFacts.writtenAttachments(expected, owner, List.of(SyntaxRole.DETAIL), facts, snapshot, true);
      default -> false;
    };
  }
}
