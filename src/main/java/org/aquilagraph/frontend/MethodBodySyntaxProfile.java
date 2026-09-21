package org.aquilagraph.frontend;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.DefaultCaseLabelTree;
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
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.InvocationSemantics;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxOperator;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.jspecify.annotations.Nullable;

final class MethodBodySyntaxProfile extends SimpleTreeVisitor<SyntaxNodeMapping, MethodBodySyntaxProfile.MappingContext> {
  private static final String THIS_NAME = "this";
  private static final String SUPER_NAME = "super";
  private static final String CONSTRUCTOR_NAME = "<init>";
  private final JavaSemanticResolver semantics;

  MethodBodySyntaxProfile(JavaSemanticResolver semantics) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
  }

  SyntaxNodeMapping describe(Tree tree, TreePath treePath, SyntaxStructuralPath structuralPath, SyntaxUse use) {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(treePath, "treePath");
    Objects.requireNonNull(structuralPath, "structuralPath");
    Objects.requireNonNull(use, "use");
    return tree.accept(this, new MappingContext(treePath, structuralPath, use));
  }

  @Override
  public SyntaxNodeMapping visitBlock(BlockTree block, MappingContext context) {
    var children = new ChildMappings(context);
    children.addSequence(SyntaxRole.STATEMENT, block.getStatements());
    return accepted(SyntaxKind.BLOCK, children);
  }

  @Override
  public SyntaxNodeMapping visitVariable(VariableTree variable, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.INITIALIZER, variable.getInitializer());
    return accepted(SyntaxKind.LOCAL_DECLARATION, new SyntaxValue.LocalDeclaration(variable.getName().toString()), children,
        semantics.reference(context.treePath(), ReferenceKind.LOCAL));
  }

  @Override
  public SyntaxNodeMapping visitExpressionStatement(ExpressionStatementTree statement, MappingContext context) {
    return acceptedWithChild(SyntaxKind.EXPRESSION_STATEMENT, context, SyntaxRole.EXPRESSION, statement.getExpression());
  }

  @Override
  public SyntaxNodeMapping visitReturn(ReturnTree statement, MappingContext context) {
    return acceptedWithChild(SyntaxKind.RETURN, context, SyntaxRole.EXPRESSION, statement.getExpression());
  }

  @Override
  public SyntaxNodeMapping visitThrow(ThrowTree statement, MappingContext context) {
    return acceptedWithChild(SyntaxKind.THROW, context, SyntaxRole.EXPRESSION, statement.getExpression());
  }

  @Override
  public SyntaxNodeMapping visitYield(YieldTree statement, MappingContext context) {
    return acceptedWithChild(SyntaxKind.YIELD, context, SyntaxRole.VALUE, statement.getValue());
  }

  @Override
  public SyntaxNodeMapping visitIf(IfTree statement, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.CONDITION, statement.getCondition());
    children.add(SyntaxRole.THEN_BRANCH, statement.getThenStatement());
    children.add(SyntaxRole.ELSE_BRANCH, statement.getElseStatement());
    return accepted(SyntaxKind.IF, children);
  }

  @Override
  public SyntaxNodeMapping visitTry(TryTree statement, MappingContext context) {
    var children = new ChildMappings(context);
    children.addAll(SyntaxRole.RESOURCE, statement.getResources());
    children.add(SyntaxRole.TRY_BLOCK, statement.getBlock());
    children.addAll(SyntaxRole.CATCH_CLAUSE, statement.getCatches());
    children.add(SyntaxRole.FINALLY_BLOCK, statement.getFinallyBlock());
    return statement.getResources().isEmpty() && statement.getFinallyBlock() == null
        ? accepted(SyntaxKind.TRY, children)
        : unsupported(statement, children);
  }

  @Override
  public SyntaxNodeMapping visitCatch(CatchTree statement, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.PARAMETER, statement.getParameter());
    children.add(SyntaxRole.BODY, statement.getBlock());
    return accepted(SyntaxKind.CATCH, children);
  }

  @Override
  public SyntaxNodeMapping visitSwitchExpression(SwitchExpressionTree expression, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.SELECTOR, expression.getExpression());
    children.addAll(SyntaxRole.CASE, expression.getCases());
    return accepted(SyntaxKind.SWITCH_EXPRESSION, children);
  }

  @Override
  public SyntaxNodeMapping visitCase(CaseTree caseTree, MappingContext context) {
    var children = new ChildMappings(context);
    var labels = new ArrayList<SyntaxValue.SwitchLabel>();
    var supported = caseTree.getGuard() == null && caseTree.getCaseKind() == CaseTree.CaseKind.RULE;
    for (var label : caseTree.getLabels()) {
      supported &= addCaseLabel(label, context, children, labels);
    }
    children.add(SyntaxRole.GUARD, caseTree.getGuard());
    var form = switch (caseTree.getCaseKind()) {
      case RULE -> {
        children.add(SyntaxRole.BODY, caseTree.getBody());
        yield SyntaxValue.SwitchCaseForm.RULE;
      }
      case STATEMENT -> {
        children.addAll(SyntaxRole.STATEMENT, caseTree.getStatements());
        yield SyntaxValue.SwitchCaseForm.STATEMENT_GROUP;
      }
    };
    return supported ? accepted(SyntaxKind.CASE, new SyntaxValue.SwitchCase(form, labels), children) : unsupported(caseTree, children);
  }

  private boolean addCaseLabel(Tree label, MappingContext context, ChildMappings children, List<SyntaxValue.SwitchLabel> labels) {
    if (label instanceof DefaultCaseLabelTree) {
      labels.add(SyntaxValue.SwitchLabel.Default.INSTANCE);
      return true;
    }
    if (!(label instanceof ConstantCaseLabelTree constantLabel)) {
      return false;
    }
    var labelPath = new TreePath(context.treePath(), label);
    var expression = constantLabel.getConstantExpression();
    var expressionPath = new TreePath(labelPath, expression);
    var childPosition = children.addMultiple(SyntaxRole.LABEL, expression, expressionPath, SyntaxUse.VALUE);
    labels.add(new SyntaxValue.SwitchLabel.Expression(childPosition));
    return (expression instanceof LiteralTree && expression.getKind() != Tree.Kind.NULL_LITERAL)
        || semantics.isEnumConstant(expressionPath);
  }

  @Override
  public SyntaxNodeMapping visitMethodInvocation(MethodInvocationTree invocation, MappingContext context) {
    var children = new ChildMappings(context);
    var element = semantics.element(context.treePath());
    var executable = element instanceof ExecutableElement candidate ? candidate : null;
    var expectedReference = executable != null && executable.getKind() == ElementKind.CONSTRUCTOR
        ? ReferenceKind.CONSTRUCTOR
        : ReferenceKind.METHOD;
    var description = invocationDescription(invocation, context, executable, children);
    children.addAll(SyntaxRole.ARGUMENT, invocation.getArguments());
    return accepted(SyntaxKind.INVOCATION, description, children, semantics.reference(context.treePath(), expectedReference));
  }

  private SyntaxValue.Invocation invocationDescription(MethodInvocationTree invocation, MappingContext context,
      @Nullable ExecutableElement executable, ChildMappings children) {
    var selector = invocation.getMethodSelect();
    var name = executable == null ? selectorName(selector) : executable.getSimpleName().toString();
    if (selector instanceof IdentifierTree identifier) {
      if (identifier.getName().contentEquals(THIS_NAME)) {
        return invocationValue(CONSTRUCTOR_NAME, InvocationForm.THIS_CONSTRUCTOR, context, executable);
      }
      if (identifier.getName().contentEquals(SUPER_NAME)) {
        return invocationValue(CONSTRUCTOR_NAME, InvocationForm.SUPER_CONSTRUCTOR, context, executable);
      }
      if (executable != null && semantics.isStatic(executable)) {
        return invocationValue(name, InvocationForm.STATIC_UNQUALIFIED, context, executable);
      }
      return invocationValue(name, InvocationForm.INSTANCE_IMPLICIT, context, executable);
    }

    var selection = (MemberSelectTree) selector;
    var qualifier = selection.getExpression();
    var qualifierPath = new TreePath(new TreePath(context.treePath(), selector), qualifier);
    if (selection.getIdentifier().contentEquals(SUPER_NAME) && executable != null && executable.getKind() == ElementKind.CONSTRUCTOR) {
      children.add(SyntaxRole.RECEIVER, qualifier, qualifierPath, SyntaxUse.VALUE);
      return invocationValue(CONSTRUCTOR_NAME, InvocationForm.QUALIFIED_SUPER_CONSTRUCTOR, context, executable);
    }
    if (isSuper(qualifier)) {
      return invocationValue(name, InvocationForm.SUPER, context, executable);
    }
    if (isQualifiedSuper(qualifier)) {
      return executable != null && executable.getKind() == ElementKind.CONSTRUCTOR
          ? invocationValue(CONSTRUCTOR_NAME, InvocationForm.QUALIFIED_SUPER_CONSTRUCTOR, context, executable)
          : invocationValue(name, InvocationForm.QUALIFIED_SUPER, context, executable);
    }
    if (executable != null && semantics.isStatic(executable)) {
      if (semantics.isTypeOrPackage(qualifierPath)) {
        return invocationValue(name, InvocationForm.STATIC_TYPE, context, executable);
      }
      children.add(SyntaxRole.RECEIVER, qualifier, qualifierPath, SyntaxUse.VALUE);
      return invocationValue(name, InvocationForm.STATIC_EXPRESSION, context, executable);
    }
    if (isThis(qualifier)) {
      return invocationValue(name, InvocationForm.INSTANCE_THIS, context, executable);
    }
    if (isQualifiedThis(qualifier)) {
      return invocationValue(name, InvocationForm.INSTANCE_QUALIFIED_THIS, context, executable);
    }
    children.add(SyntaxRole.RECEIVER, qualifier, qualifierPath, SyntaxUse.VALUE);
    return invocationValue(name, InvocationForm.INSTANCE_EXPRESSION, context, executable);
  }

  private SyntaxValue.Invocation invocationValue(String name, InvocationForm form, MappingContext context,
      @Nullable ExecutableElement executable) {
    var invocationSemantics = executable == null
        ? Optional.<InvocationSemantics>empty()
        : semantics.invocationSemantics(context.treePath(), executable, form);
    return new SyntaxValue.Invocation(name, form, invocationSemantics);
  }

  @Override
  public SyntaxNodeMapping visitNewClass(NewClassTree creation, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.ENCLOSING_INSTANCE, creation.getEnclosingExpression());
    children.addAll(SyntaxRole.ARGUMENT, creation.getArguments());
    var element = semantics.element(context.treePath());
    var executable = element instanceof ExecutableElement candidate ? candidate : null;
    var form = creation.getEnclosingExpression() == null ? InvocationForm.OBJECT_CREATION : InvocationForm.QUALIFIED_OBJECT_CREATION;
    return creation.getClassBody() == null
        ? accepted(SyntaxKind.OBJECT_CREATION, invocationValue(CONSTRUCTOR_NAME, form, context, executable), children,
            semantics.reference(context.treePath(), ReferenceKind.CONSTRUCTOR))
        : unsupported(creation, children);
  }

  @Override
  public SyntaxNodeMapping visitMemberSelect(MemberSelectTree selection, MappingContext context) {
    if (semantics.isArrayLength(selection, context.treePath())) {
      var children = new ChildMappings(context);
      children.add(SyntaxRole.QUALIFIER, selection.getExpression());
      return accepted(SyntaxKind.ARRAY_LENGTH, new SyntaxValue.Symbol(selection.getIdentifier().toString()), children);
    }
    if (selection.getIdentifier().contentEquals(THIS_NAME) || selection.getIdentifier().contentEquals(SUPER_NAME)) {
      var receiver = selection.getIdentifier().contentEquals(THIS_NAME)
          ? SyntaxValue.ReceiverKind.QUALIFIED_THIS
          : SyntaxValue.ReceiverKind.QUALIFIED_SUPER;
      return accepted(SyntaxKind.RECEIVER, new SyntaxValue.Receiver(receiver));
    }
    return fieldSelection(selection, context);
  }

  private SyntaxNodeMapping fieldSelection(MemberSelectTree selection, MappingContext context) {
    var children = new ChildMappings(context);
    var element = semantics.element(context.treePath());
    var qualifier = selection.getExpression();
    var qualifierPath = new TreePath(context.treePath(), qualifier);
    SyntaxValue.FieldReceiver receiver;
    if (element != null && semantics.isStatic(element)) {
      if (semantics.isTypeOrPackage(qualifierPath)) {
        receiver = SyntaxValue.FieldReceiver.STATIC_TYPE;
      } else {
        receiver = SyntaxValue.FieldReceiver.STATIC_EXPRESSION;
        children.add(SyntaxRole.RECEIVER, qualifier, qualifierPath, SyntaxUse.VALUE);
      }
    } else if (isThis(qualifier)) {
      receiver = SyntaxValue.FieldReceiver.INSTANCE_THIS;
    } else if (isQualifiedThis(qualifier)) {
      receiver = SyntaxValue.FieldReceiver.INSTANCE_QUALIFIED_THIS;
    } else if (isSuper(qualifier)) {
      receiver = SyntaxValue.FieldReceiver.SUPER;
    } else if (isQualifiedSuper(qualifier)) {
      receiver = SyntaxValue.FieldReceiver.QUALIFIED_SUPER;
    } else {
      receiver = SyntaxValue.FieldReceiver.INSTANCE_EXPRESSION;
      children.add(SyntaxRole.RECEIVER, qualifier, qualifierPath, SyntaxUse.VALUE);
    }
    var kind = context.use() == SyntaxUse.ASSIGNMENT_LOCATION ? SyntaxKind.FIELD_LOCATION : SyntaxKind.FIELD_ACCESS;
    var referenceKind = element != null && element.getKind() == ElementKind.ENUM_CONSTANT
        ? ReferenceKind.ENUM_CONSTANT
        : ReferenceKind.FIELD;
    return accepted(kind, new SyntaxValue.FieldAccess(selection.getIdentifier().toString(), receiver), children,
        semantics.reference(context.treePath(), referenceKind));
  }

  @Override
  public SyntaxNodeMapping visitUnary(UnaryTree unary, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.OPERAND, unary.getExpression());
    return switch (unary.getKind()) {
      case UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT ->
        accepted(SyntaxKind.UNARY, new SyntaxValue.Operator(operator(unary.getKind())), children);
      default -> unsupported(unary, children);
    };
  }

  @Override
  public SyntaxNodeMapping visitBinary(BinaryTree binary, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.LEFT_OPERAND, binary.getLeftOperand());
    children.add(SyntaxRole.RIGHT_OPERAND, binary.getRightOperand());
    var kind = switch (binary.getKind()) {
      case CONDITIONAL_AND -> SyntaxKind.CONDITIONAL_AND;
      case CONDITIONAL_OR -> SyntaxKind.CONDITIONAL_OR;
      default -> SyntaxKind.BINARY;
    };
    return accepted(kind, new SyntaxValue.Operator(operator(binary.getKind())), children);
  }

  @Override
  public SyntaxNodeMapping visitAssignment(AssignmentTree assignment, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.LOCATION, assignment.getVariable(), SyntaxUse.ASSIGNMENT_LOCATION);
    children.add(SyntaxRole.VALUE, assignment.getExpression());
    return accepted(SyntaxKind.ASSIGNMENT, children);
  }

  @Override
  public SyntaxNodeMapping visitArrayAccess(ArrayAccessTree access, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.ARRAY, access.getExpression());
    children.add(SyntaxRole.INDEX, access.getIndex());
    var kind = context.use() == SyntaxUse.ASSIGNMENT_LOCATION ? SyntaxKind.ARRAY_LOCATION : SyntaxKind.ARRAY_ACCESS;
    return accepted(kind, children);
  }

  @Override
  public SyntaxNodeMapping visitParenthesized(ParenthesizedTree expression, MappingContext context) {
    if (context.use() == SyntaxUse.ASSIGNMENT_LOCATION) {
      return unsupported(expression, new ChildMappings(context));
    }
    return acceptedWithChild(SyntaxKind.PARENTHESIZED, context, SyntaxRole.EXPRESSION, expression.getExpression());
  }

  @Override
  public SyntaxNodeMapping visitTypeCast(TypeCastTree expression, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.EXPRESSION, expression.getExpression());
    var typePath = new TreePath(context.treePath(), expression.getType());
    var displayName = semantics.explicitType(typePath) instanceof SyntaxType.Resolved resolved
        ? resolved.identity().displayName()
        : "unresolved";
    return accepted(SyntaxKind.TYPE_CAST, new SyntaxValue.TypeName(displayName), children, semantics.typeReference(typePath));
  }

  @Override
  public SyntaxNodeMapping visitConditionalExpression(ConditionalExpressionTree expression, MappingContext context) {
    var children = new ChildMappings(context);
    children.add(SyntaxRole.CONDITION, expression.getCondition());
    children.add(SyntaxRole.TRUE_EXPRESSION, expression.getTrueExpression());
    children.add(SyntaxRole.FALSE_EXPRESSION, expression.getFalseExpression());
    return accepted(SyntaxKind.CONDITIONAL_EXPRESSION, children);
  }

  @Override
  public SyntaxNodeMapping visitIdentifier(IdentifierTree identifier, MappingContext context) {
    if (identifier.getName().contentEquals(THIS_NAME) || identifier.getName().contentEquals(SUPER_NAME)) {
      var kind = identifier.getName().contentEquals(THIS_NAME) ? SyntaxValue.ReceiverKind.THIS : SyntaxValue.ReceiverKind.SUPER;
      return accepted(SyntaxKind.RECEIVER, new SyntaxValue.Receiver(kind));
    }
    var element = semantics.element(context.treePath());
    if (element != null && (element.getKind() == ElementKind.FIELD || element.getKind() == ElementKind.ENUM_CONSTANT)) {
      var receiver = semantics.isStatic(element)
          ? SyntaxValue.FieldReceiver.STATIC_UNQUALIFIED
          : SyntaxValue.FieldReceiver.INSTANCE_IMPLICIT;
      var kind = context.use() == SyntaxUse.ASSIGNMENT_LOCATION ? SyntaxKind.FIELD_LOCATION : SyntaxKind.FIELD_ACCESS;
      var referenceKind = element.getKind() == ElementKind.ENUM_CONSTANT ? ReferenceKind.ENUM_CONSTANT : ReferenceKind.FIELD;
      return accepted(kind, new SyntaxValue.FieldAccess(identifier.getName().toString(), receiver), List.of(),
          semantics.reference(context.treePath(), referenceKind));
    }
    var kind = context.use() == SyntaxUse.ASSIGNMENT_LOCATION ? SyntaxKind.VARIABLE_LOCATION : SyntaxKind.VARIABLE_ACCESS;
    var referenceKind = element != null && element.getKind() == ElementKind.PARAMETER ? ReferenceKind.PARAMETER : ReferenceKind.LOCAL;
    return accepted(kind, new SyntaxValue.Symbol(identifier.getName().toString()), List.of(),
        semantics.reference(context.treePath(), referenceKind));
  }

  @Override
  public SyntaxNodeMapping visitLiteral(LiteralTree literal, MappingContext context) {
    return accepted(SyntaxKind.LITERAL, new SyntaxValue.Literal(String.valueOf(literal.getValue())));
  }

  @Override
  public SyntaxNodeMapping visitEmptyStatement(EmptyStatementTree statement, MappingContext context) {
    return accepted(SyntaxKind.EMPTY);
  }

  @Override
  protected SyntaxNodeMapping defaultAction(Tree tree, MappingContext context) {
    return unsupported(tree, new ChildMappings(context));
  }

  private SyntaxNodeMapping acceptedWithChild(SyntaxKind kind, MappingContext context, SyntaxRole role, @Nullable Tree child) {
    var children = new ChildMappings(context);
    children.add(role, child);
    return accepted(kind, children);
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind) {
    return accepted(kind, SyntaxValue.None.INSTANCE, List.of(), Optional.empty());
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind, SyntaxValue value) {
    return accepted(kind, value, List.of(), Optional.empty());
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind, ChildMappings children) {
    return accepted(kind, SyntaxValue.None.INSTANCE, children.value(), Optional.empty());
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind, SyntaxValue value, ChildMappings children) {
    return accepted(kind, value, children.value(), Optional.empty());
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind, SyntaxValue value, ChildMappings children,
      Optional<SemanticReference> reference) {
    return accepted(kind, value, children.value(), reference);
  }

  private static SyntaxNodeMapping accepted(SyntaxKind kind, SyntaxValue value, List<SyntaxChildMapping> children,
      Optional<SemanticReference> reference) {
    return new SyntaxNodeMapping(kind, value, children, reference);
  }

  private static SyntaxNodeMapping unsupported(Tree tree, ChildMappings children) {
    return new SyntaxNodeMapping(SyntaxKind.UNSUPPORTED, new SyntaxValue.Unsupported(tree.getKind().name()), children.value(),
        Optional.empty());
  }

  private static SyntaxOperator operator(Tree.Kind kind) {
    return switch (kind) {
      case UNARY_PLUS -> SyntaxOperator.UNARY_PLUS;
      case UNARY_MINUS -> SyntaxOperator.UNARY_MINUS;
      case BITWISE_COMPLEMENT -> SyntaxOperator.BITWISE_COMPLEMENT;
      case LOGICAL_COMPLEMENT -> SyntaxOperator.LOGICAL_COMPLEMENT;
      case MULTIPLY -> SyntaxOperator.MULTIPLY;
      case DIVIDE -> SyntaxOperator.DIVIDE;
      case REMAINDER -> SyntaxOperator.REMAINDER;
      case PLUS -> SyntaxOperator.PLUS;
      case MINUS -> SyntaxOperator.MINUS;
      case LEFT_SHIFT -> SyntaxOperator.LEFT_SHIFT;
      case RIGHT_SHIFT -> SyntaxOperator.RIGHT_SHIFT;
      case UNSIGNED_RIGHT_SHIFT -> SyntaxOperator.UNSIGNED_RIGHT_SHIFT;
      case LESS_THAN -> SyntaxOperator.LESS_THAN;
      case GREATER_THAN -> SyntaxOperator.GREATER_THAN;
      case LESS_THAN_EQUAL -> SyntaxOperator.LESS_THAN_EQUAL;
      case GREATER_THAN_EQUAL -> SyntaxOperator.GREATER_THAN_EQUAL;
      case EQUAL_TO -> SyntaxOperator.EQUAL_TO;
      case NOT_EQUAL_TO -> SyntaxOperator.NOT_EQUAL_TO;
      case AND -> SyntaxOperator.AND;
      case XOR -> SyntaxOperator.XOR;
      case OR -> SyntaxOperator.OR;
      case CONDITIONAL_AND -> SyntaxOperator.CONDITIONAL_AND;
      case CONDITIONAL_OR -> SyntaxOperator.CONDITIONAL_OR;
      default -> throw new IllegalArgumentException("Not a supported operator: " + kind);
    };
  }

  private static String selectorName(Tree selector) {
    return switch (selector) {
      case IdentifierTree identifier -> identifier.getName().toString();
      case MemberSelectTree selection -> selection.getIdentifier().toString();
      default -> "unresolved";
    };
  }

  private static boolean isThis(Tree tree) {
    return tree instanceof IdentifierTree identifier && identifier.getName().contentEquals(THIS_NAME);
  }

  private static boolean isSuper(Tree tree) {
    return tree instanceof IdentifierTree identifier && identifier.getName().contentEquals(SUPER_NAME);
  }

  private static boolean isQualifiedThis(Tree tree) {
    return tree instanceof MemberSelectTree selection && selection.getIdentifier().contentEquals(THIS_NAME);
  }

  private static boolean isQualifiedSuper(Tree tree) {
    return tree instanceof MemberSelectTree selection && selection.getIdentifier().contentEquals(SUPER_NAME);
  }

  record MappingContext(TreePath treePath, SyntaxStructuralPath structuralPath, SyntaxUse use) {
    MappingContext {
      Objects.requireNonNull(treePath, "treePath");
      Objects.requireNonNull(structuralPath, "structuralPath");
      Objects.requireNonNull(use, "use");
    }
  }

  private static final class ChildMappings {
    private final SyntaxStructuralPath parentPath;
    private final TreePath parentTreePath;
    private final List<SyntaxChildMapping> children = new ArrayList<>();

    private ChildMappings(MappingContext context) {
      Objects.requireNonNull(context, "context");
      parentPath = context.structuralPath();
      parentTreePath = context.treePath();
    }

    private void add(SyntaxRole role, @Nullable Tree tree) {
      add(role, tree, SyntaxUse.VALUE);
    }

    private void add(SyntaxRole role, @Nullable Tree tree, SyntaxUse use) {
      if (tree != null) {
        add(role, tree, new TreePath(parentTreePath, tree), use);
      }
    }

    private void add(SyntaxRole role, Tree tree, TreePath treePath, SyntaxUse use) {
      children.add(new SyntaxChildMapping(role, 0, tree, treePath, parentPath.child(role), use));
    }

    private int addMultiple(SyntaxRole role, Tree tree, TreePath treePath, SyntaxUse use) {
      var position = (int) children.stream().filter(child -> child.role() == role).count();
      children.add(new SyntaxChildMapping(role, position, tree, treePath, parentPath.child(role).element(position), use));
      return position;
    }

    private void addSequence(SyntaxRole role, List<? extends Tree> trees) {
      Objects.requireNonNull(trees, "trees");
      for (var index = 0; index < trees.size(); index++) {
        var tree = trees.get(index);
        children
            .add(new SyntaxChildMapping(role, index, tree, new TreePath(parentTreePath, tree), parentPath.element(index), SyntaxUse.VALUE));
      }
    }

    private void addAll(SyntaxRole role, List<? extends Tree> trees) {
      Objects.requireNonNull(trees, "trees");
      var rolePath = parentPath.child(role);
      for (var index = 0; index < trees.size(); index++) {
        var tree = trees.get(index);
        children
            .add(new SyntaxChildMapping(role, index, tree, new TreePath(parentTreePath, tree), rolePath.element(index), SyntaxUse.VALUE));
      }
    }

    private List<SyntaxChildMapping> value() {
      return List.copyOf(children);
    }
  }
}
