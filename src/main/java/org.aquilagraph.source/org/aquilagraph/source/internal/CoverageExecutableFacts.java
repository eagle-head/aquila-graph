package org.aquilagraph.source.internal;

import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.LIST;
import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.OPTIONAL_LIST;
import static org.aquilagraph.source.internal.CoverageWrittenAttachments.Cardinality.OPTIONAL_VALUE;

import org.aquilagraph.source.admission.CoverageActualFactView;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import java.util.List;
import java.util.Map;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;

/** Reconciles independent executable accessors against canonical written source values. */
public final class CoverageExecutableFacts {
  private static final String SCALAR_ITEM_KEY = "item:0";
  private static final Map<String, JavaSourceKind> SURFACES = Map.ofEntries(Map.entry("WhileLoopTree", JavaSourceKind.WHILE_LOOP),
      Map.entry("DoWhileLoopTree", JavaSourceKind.DO_WHILE_LOOP), Map.entry("ForLoopTree", JavaSourceKind.FOR_LOOP),
      Map.entry("EnhancedForLoopTree", JavaSourceKind.ENHANCED_FOR_LOOP),
      Map.entry("LabeledStatementTree", JavaSourceKind.LABELED_STATEMENT), Map.entry("BreakTree", JavaSourceKind.BREAK),
      Map.entry("ContinueTree", JavaSourceKind.CONTINUE), Map.entry("AssertTree", JavaSourceKind.ASSERT),
      Map.entry("SynchronizedTree", JavaSourceKind.SYNCHRONIZED), Map.entry("LambdaExpressionTree", JavaSourceKind.LAMBDA_EXPRESSION),
      Map.entry("MemberReferenceTree", JavaSourceKind.MEMBER_REFERENCE), Map.entry("MemberSelectTree", JavaSourceKind.MEMBER_SELECT),
      Map.entry("InstanceOfTree", JavaSourceKind.INSTANCE_OF), Map.entry("BindingPatternTree", JavaSourceKind.BINDING_PATTERN),
      Map.entry("DeconstructionPatternTree", JavaSourceKind.DECONSTRUCTION_PATTERN), Map.entry("SwitchTree", JavaSourceKind.SWITCH),
      Map.entry("SwitchExpressionTree", JavaSourceKind.SWITCH_EXPRESSION), Map.entry("CaseTree", JavaSourceKind.CASE),
      Map.entry("YieldTree", JavaSourceKind.YIELD), Map.entry("AnyPatternTree", JavaSourceKind.ANY_PATTERN),
      Map.entry("TryTree", JavaSourceKind.TRY), Map.entry("CatchTree", JavaSourceKind.CATCH),
      Map.entry("UnionTypeTree", JavaSourceKind.UNION_TYPE), Map.entry("BlockTree", JavaSourceKind.BLOCK),
      Map.entry("NewClassTree", JavaSourceKind.NEW_CLASS));

  private CoverageExecutableFacts() {
  }

  /**
   * Reports whether the role identifies an executable declaration surface.
   *
   * @param role
   *          role
   * @return whether the facts satisfy the required relation
   */
  public static boolean isSurface(String role) {
    var separator = role.indexOf("::");
    return separator >= 0 && SURFACES.containsKey(role.substring(0, separator));
  }

  /**
   * Checks whether a copied fact has the executable shape required by the role.
   *
   * @param role
   *          role
   * @param value
   *          copied value to process
   * @return whether the facts satisfy the required relation
   */
  public static boolean matches(String role, CoverageSyntaxFactValue value) {
    var surface = role.substring(0, role.indexOf("::"));
    return value.sourceKind() == SURFACES.get(surface) && value.syntaxKind() != SyntaxKind.UNSUPPORTED
        && (!"MemberSelectTree".equals(surface) || value.syntaxKind() == SyntaxKind.CLASS_LITERAL
            || value.syntaxKind() == SyntaxKind.ANNOTATION_CLASS_LITERAL);
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
    var syntax = facts.requiredSyntax(owner);
    if (syntax.syntaxKind() == SyntaxKind.CASE) {
      return CoverageSwitchFacts.supports(accessor, expected, owner, facts);
    }
    return switch (accessor) {
      case "isStatic" -> expected.equals(new CoveragePropertyValue(State.SCALAR, List.of(
          new Item(SCALAR_ITEM_KEY, Boolean.toString(syntax.syntaxKind() == SyntaxKind.STATIC_INITIALIZER_BLOCK), Provenance.WRITTEN))));
      case "getStatements" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.STATEMENT), facts, LIST);
      case "getIdentifier" -> syntax.sourceKind() == JavaSourceKind.NEW_CLASS
          ? CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.TYPE_OPERAND), facts, OPTIONAL_VALUE)
          : (syntax.syntaxKind() == SyntaxKind.CLASS_LITERAL || syntax.syntaxKind() == SyntaxKind.ANNOTATION_CLASS_LITERAL)
              && expected.equals(new CoveragePropertyValue(State.SCALAR, List.of(new Item(SCALAR_ITEM_KEY, "class", Provenance.WRITTEN))));
      case "getCases" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.CASE), facts, LIST);
      case "getResources" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.RESOURCE), facts, LIST);
      case "getCatches" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.CATCH_CLAUSE), facts, LIST);
      case "getFinallyBlock" ->
        CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.FINALLY_BLOCK), facts, OPTIONAL_VALUE);
      case "getTypeAlternatives" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.UNION_ALTERNATIVE), facts, LIST);
      case "getEnclosingExpression" ->
        CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.ENCLOSING_INSTANCE), facts, OPTIONAL_VALUE);
      case "getArguments" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.ARGUMENT), facts, LIST);
      case "getClassBody" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.CLASS_BODY), facts, OPTIONAL_VALUE);
      case "getValue" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.VALUE), facts, OPTIONAL_VALUE);
      case "getPattern" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.PATTERN), facts, OPTIONAL_VALUE);
      case "getDeconstructor" ->
        CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.TYPE_OPERAND), facts, OPTIONAL_VALUE);
      case "getNestedPatterns" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.PATTERN_COMPONENT), facts, LIST);
      case "getType" -> CoveragePatternFacts.testedType(expected, owner, facts);
      case "getMode" ->
        syntax.value() instanceof SyntaxValue.MemberReference reference && expected.equals(new CoveragePropertyValue(State.SCALAR,
            List.of(new Item(reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR ? "NEW" : "INVOKE",
                reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR ? "NEW" : "INVOKE", Provenance.WRITTEN))));
      case "getName" -> syntax.value() instanceof SyntaxValue.MemberReference reference
          && expected.equals(new CoveragePropertyValue(State.SCALAR, List.of(new Item(SCALAR_ITEM_KEY,
              reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR ? "<init>" : reference.name(), Provenance.WRITTEN))));
      case "getQualifierExpression" ->
        CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.QUALIFIER), facts, OPTIONAL_VALUE);
      case "getTypeArguments" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.TYPE_ARGUMENT), facts,
          syntax.sourceKind() == JavaSourceKind.MEMBER_REFERENCE ? OPTIONAL_LIST : LIST);
      case "getKind" -> expected.equals(new CoveragePropertyValue(State.SCALAR,
          List.of(new Item(syntax.sourceKind().name(), syntax.sourceKind().name(), Provenance.WRITTEN))));
      case "getLabel" -> syntax.value() instanceof SyntaxValue.StatementLabel label && expected.equals(
          label.name().map(name -> new CoveragePropertyValue(State.SCALAR, List.of(new Item(SCALAR_ITEM_KEY, name, Provenance.WRITTEN))))
              .orElse(new CoveragePropertyValue(State.ABSENT, List.of())));
      case "getCondition" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.CONDITION), facts, OPTIONAL_VALUE);
      case "getBlock" -> CoverageWrittenAttachments.matches(expected, owner,
          List.of(syntax.syntaxKind() == SyntaxKind.TRY ? SyntaxRole.TRY_BLOCK : SyntaxRole.BODY), facts, OPTIONAL_VALUE);
      case "getStatement", "getBody" ->
        CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.BODY), facts, OPTIONAL_VALUE);
      case "getInitializer" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.INITIALIZER), facts, LIST);
      case "getUpdate" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.UPDATE), facts, LIST);
      case "getParameter", "getVariable" ->
        CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.PARAMETER), facts, OPTIONAL_VALUE);
      case "getParameters" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.PARAMETER), facts, LIST);
      case "getBodyKind" -> syntax.value() instanceof SyntaxValue.Lambda lambda && expected.equals(new CoveragePropertyValue(State.SCALAR,
          List.of(new Item(lambda.bodyKind() == SyntaxValue.LambdaBodyKind.BLOCK ? "STATEMENT" : "EXPRESSION",
              lambda.bodyKind() == SyntaxValue.LambdaBodyKind.BLOCK ? "STATEMENT" : "EXPRESSION", Provenance.WRITTEN))));
      case "getExpression" -> CoverageWrittenAttachments.matches(expected, owner,
          List.of(syntax.sourceKind() == JavaSourceKind.MEMBER_SELECT
              ? SyntaxRole.TYPE_OPERAND
              : syntax.sourceKind() == JavaSourceKind.INSTANCE_OF
                  ? SyntaxRole.EXPRESSION
                  : syntax.sourceKind() == JavaSourceKind.ENHANCED_FOR_LOOP
                      ? SyntaxRole.ITERABLE
                      : syntax.sourceKind() == JavaSourceKind.SWITCH || syntax.sourceKind() == JavaSourceKind.SWITCH_EXPRESSION
                          ? SyntaxRole.SELECTOR
                          : SyntaxRole.MONITOR),
          facts, OPTIONAL_VALUE);
      case "getDetail" -> CoverageWrittenAttachments.matches(expected, owner, List.of(SyntaxRole.DETAIL), facts, OPTIONAL_VALUE);
      default -> false;
    };
  }
}
