package org.aquilagraph.frontend;

import static org.aquilagraph.frontend.SyntaxAcceptance.ACCEPTED;
import static org.aquilagraph.frontend.SyntaxAcceptance.UNSUPPORTED;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.aquilagraph.model.SyntaxKind;
import org.jspecify.annotations.Nullable;

final class MethodBodySyntaxProfile extends SimpleTreeVisitor<SyntaxNodeMapping, MethodBodySyntaxProfile.MappingContext> {
  private static final String EXPRESSION_ROLE = "expression";

  private final JavaSemanticResolver semantics;

  MethodBodySyntaxProfile(JavaSemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  SyntaxNodeMapping describe(Tree tree, TreePath treePath, SyntaxStructuralPath structuralPath) {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(treePath, "treePath");
    Objects.requireNonNull(structuralPath, "structuralPath");
    return tree.accept(this, new MappingContext(treePath, structuralPath));
  }

  @Override
  public SyntaxNodeMapping visitBlock(BlockTree block, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.addAll(block.getStatements());
    return accepted(SyntaxKind.BLOCK, block, children);
  }

  @Override
  public SyntaxNodeMapping visitVariable(VariableTree variable, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("initializer", variable.getInitializer());
    return accepted(SyntaxKind.LOCAL_DECLARATION, variable.getType() + " " + variable.getName(), children);
  }

  @Override
  public SyntaxNodeMapping visitExpressionStatement(ExpressionStatementTree statement, MappingContext context) {
    return acceptedWithChild(SyntaxKind.EXPRESSION_STATEMENT, statement, context, EXPRESSION_ROLE, statement.getExpression());
  }

  @Override
  public SyntaxNodeMapping visitReturn(ReturnTree statement, MappingContext context) {
    return acceptedWithChild(SyntaxKind.RETURN, statement, context, EXPRESSION_ROLE, statement.getExpression());
  }

  @Override
  public SyntaxNodeMapping visitThrow(ThrowTree statement, MappingContext context) {
    return acceptedWithChild(SyntaxKind.THROW, statement, context, EXPRESSION_ROLE, statement.getExpression());
  }

  @Override
  public SyntaxNodeMapping visitYield(YieldTree statement, MappingContext context) {
    return acceptedWithChild(SyntaxKind.YIELD, statement, context, "value", statement.getValue());
  }

  @Override
  public SyntaxNodeMapping visitIf(IfTree statement, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("condition", statement.getCondition());
    children.add("then", statement.getThenStatement());
    children.add("else", statement.getElseStatement());
    return accepted(SyntaxKind.IF, statement, children);
  }

  @Override
  public SyntaxNodeMapping visitTry(TryTree statement, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.addAll("resource", statement.getResources());
    children.add("try", statement.getBlock());
    children.addAll("catch", statement.getCatches());
    children.add("finally", statement.getFinallyBlock());
    var acceptance = statement.getResources().isEmpty() && statement.getFinallyBlock() == null ? ACCEPTED : UNSUPPORTED;
    return mapping(SyntaxKind.TRY, statement.getKind().name(), children, acceptance);
  }

  @Override
  public SyntaxNodeMapping visitCatch(CatchTree statement, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("parameter", statement.getParameter());
    children.add("body", statement.getBlock());
    return accepted(SyntaxKind.CATCH, statement.getParameter().getType() + " " + statement.getParameter().getName(), children);
  }

  @Override
  public SyntaxNodeMapping visitSwitchExpression(SwitchExpressionTree expression, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("selector", expression.getExpression());
    children.addAll("case", expression.getCases());
    return accepted(SyntaxKind.SWITCH_EXPRESSION, expression, children);
  }

  @Override
  public SyntaxNodeMapping visitCase(CaseTree caseTree, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    if (caseTree.getBody() != null) {
      children.add("body", caseTree.getBody());
    } else {
      children.addAll("statement", caseTree.getStatements());
    }
    var detail = caseTree.getLabels().stream().map(Object::toString).collect(Collectors.joining(","));
    return accepted(SyntaxKind.CASE, detail, children);
  }

  @Override
  public SyntaxNodeMapping visitMethodInvocation(MethodInvocationTree invocation, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("target", invocation.getMethodSelect());
    children.addAll("argument", invocation.getArguments());
    return accepted(SyntaxKind.INVOCATION, invocation, children);
  }

  @Override
  public SyntaxNodeMapping visitNewClass(NewClassTree creation, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("enclosing", creation.getEnclosingExpression());
    children.addAll("argument", creation.getArguments());
    return accepted(SyntaxKind.OBJECT_CREATION, creation, children);
  }

  @Override
  public SyntaxNodeMapping visitMemberSelect(MemberSelectTree selection, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("qualifier", selection.getExpression());
    var kind = semantics.isArrayLength(selection, context.treePath()) ? SyntaxKind.ARRAY_LENGTH : SyntaxKind.MEMBER_ACCESS;
    return accepted(kind, selection.getIdentifier().toString(), children);
  }

  @Override
  public SyntaxNodeMapping visitUnary(UnaryTree unary, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("operand", unary.getExpression());
    return switch (unary.getKind()) {
      case UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT -> accepted(SyntaxKind.UNARY, unary.getKind().name(), children);
      default -> unsupported(unary, children);
    };
  }

  @Override
  public SyntaxNodeMapping visitBinary(BinaryTree binary, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("left", binary.getLeftOperand());
    children.add("right", binary.getRightOperand());
    var kind = switch (binary.getKind()) {
      case CONDITIONAL_AND -> SyntaxKind.CONDITIONAL_AND;
      case CONDITIONAL_OR -> SyntaxKind.CONDITIONAL_OR;
      default -> SyntaxKind.BINARY;
    };
    return accepted(kind, binary.getKind().name(), children);
  }

  @Override
  public SyntaxNodeMapping visitAssignment(AssignmentTree assignment, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("target", assignment.getVariable());
    children.add("value", assignment.getExpression());
    return accepted(SyntaxKind.ASSIGNMENT, assignment, children);
  }

  @Override
  public SyntaxNodeMapping visitArrayAccess(ArrayAccessTree access, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("array", access.getExpression());
    children.add("index", access.getIndex());
    return accepted(SyntaxKind.ARRAY_ACCESS, access, children);
  }

  @Override
  public SyntaxNodeMapping visitParenthesized(ParenthesizedTree expression, MappingContext context) {
    return acceptedWithChild(SyntaxKind.PARENTHESIZED, expression, context, EXPRESSION_ROLE, expression.getExpression());
  }

  @Override
  public SyntaxNodeMapping visitTypeCast(TypeCastTree expression, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add(EXPRESSION_ROLE, expression.getExpression());
    return accepted(SyntaxKind.TYPE_CAST, expression.getType().toString(), children);
  }

  @Override
  public SyntaxNodeMapping visitConditionalExpression(ConditionalExpressionTree expression, MappingContext context) {
    var children = new ChildMappings(context.structuralPath());
    children.add("condition", expression.getCondition());
    children.add("true", expression.getTrueExpression());
    children.add("false", expression.getFalseExpression());
    return accepted(SyntaxKind.CONDITIONAL_EXPRESSION, expression, children);
  }

  @Override
  public SyntaxNodeMapping visitIdentifier(IdentifierTree identifier, MappingContext context) {
    return accepted(SyntaxKind.VARIABLE_ACCESS, identifier.getName().toString());
  }

  @Override
  public SyntaxNodeMapping visitLiteral(LiteralTree literal, MappingContext context) {
    return accepted(SyntaxKind.LITERAL, String.valueOf(literal.getValue()));
  }

  @Override
  public SyntaxNodeMapping visitEmptyStatement(EmptyStatementTree statement, MappingContext context) {
    return accepted(SyntaxKind.EMPTY, statement);
  }

  @Override
  protected SyntaxNodeMapping defaultAction(Tree tree, MappingContext context) {
    return unsupported(tree, new ChildMappings(context.structuralPath()));
  }

  private static SyntaxNodeMapping acceptedWithChild(SyntaxKind kind, Tree tree, MappingContext context, String role,
      @Nullable Tree child) {
    var children = new ChildMappings(context.structuralPath());
    children.add(role, child);
    return accepted(kind, tree, children);
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind, Tree tree) {
    return accepted(kind, tree.getKind().name());
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind, String detail) {
    return new SyntaxNodeMapping(kind, detail, List.of(), ACCEPTED);
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind, Tree tree, ChildMappings children) {
    return accepted(kind, tree.getKind().name(), children);
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind, String detail, ChildMappings children) {
    return mapping(kind, detail, children, ACCEPTED);
  }

  private static SyntaxNodeMapping unsupported(Tree tree, ChildMappings children) {
    return mapping(SyntaxKind.UNSUPPORTED, tree.getKind().name(), children, UNSUPPORTED);
  }

  private static SyntaxNodeMapping mapping(SyntaxKind kind, String detail, ChildMappings children, SyntaxAcceptance acceptance) {
    return new SyntaxNodeMapping(kind, detail, children.value(), acceptance);
  }

  record MappingContext(TreePath treePath, SyntaxStructuralPath structuralPath) {
    MappingContext {
      Objects.requireNonNull(treePath, "treePath");
      Objects.requireNonNull(structuralPath, "structuralPath");
    }
  }

  private static final class ChildMappings {
    private final SyntaxStructuralPath parentPath;
    private final List<SyntaxChildMapping> children = new ArrayList<>();

    private ChildMappings(SyntaxStructuralPath parentPath) {
      this.parentPath = Objects.requireNonNull(parentPath, "parentPath");
    }

    private void add(String role, @Nullable Tree tree) {
      if (tree != null) {
        children.add(new SyntaxChildMapping(tree, parentPath.child(role)));
      }
    }

    private void addAll(List<? extends Tree> trees) {
      Objects.requireNonNull(trees, "trees");
      for (var index = 0; index < trees.size(); index++) {
        children.add(new SyntaxChildMapping(trees.get(index), parentPath.element(index)));
      }
    }

    private void addAll(String role, List<? extends Tree> trees) {
      Objects.requireNonNull(trees, "trees");
      var rolePath = parentPath.child(role);
      for (var index = 0; index < trees.size(); index++) {
        children.add(new SyntaxChildMapping(trees.get(index), rolePath.element(index)));
      }
    }

    private List<SyntaxChildMapping> value() {
      return List.copyOf(children);
    }
  }
}
