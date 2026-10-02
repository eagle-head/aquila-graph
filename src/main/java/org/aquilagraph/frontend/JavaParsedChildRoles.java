package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExportsTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.OpensTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.ProvidesTree;
import com.sun.source.tree.RequiresTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.UsesTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.tree.YieldTree;
import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.model.SourceOwnerPath.Role;
import org.aquilagraph.model.SourceOwnerPath.Step;
import org.jspecify.annotations.Nullable;

/** Enumerates direct parsed child slots in compiler scanner order with explicit parent roles. */
final class JavaParsedChildRoles {
  private JavaParsedChildRoles() {
  }

  static List<Child> children(Tree parent) {
    var children = new Children();
    switch (parent) {
      case CompilationUnitTree tree -> children.add(Role.PACKAGE_DECLARATION, tree.getPackage()).add(Role.IMPORT, tree.getImports())
          .add(Role.TYPE_DECLARATION, tree.getTypeDecls()).add(Role.MODULE_DECLARATION, tree.getModule());
      case PackageTree tree -> children.add(Role.ANNOTATION, tree.getAnnotations()).add(Role.PACKAGE_NAME, tree.getPackageName());
      case ImportTree tree -> children.add(Role.IMPORT_NAME, tree.getQualifiedIdentifier());
      case ClassTree tree -> children.add(Role.MODIFIERS, tree.getModifiers()).add(Role.TYPE_PARAMETER, tree.getTypeParameters())
          .add(Role.SUPERCLASS, tree.getExtendsClause()).add(Role.INTERFACE, tree.getImplementsClause())
          .add(Role.PERMITTED_SUBCLASS, tree.getPermitsClause()).add(Role.MEMBER, tree.getMembers());
      case MethodTree tree -> children.add(Role.MODIFIERS, tree.getModifiers()).add(Role.RETURN_TYPE, tree.getReturnType())
          .add(Role.TYPE_PARAMETER, tree.getTypeParameters()).add(Role.PARAMETER, tree.getParameters())
          .add(Role.RECEIVER_PARAMETER, tree.getReceiverParameter()).add(Role.THROWN_TYPE, tree.getThrows()).add(Role.BODY, tree.getBody())
          .add(Role.DEFAULT_VALUE, tree.getDefaultValue());
      case VariableTree tree -> children.add(Role.MODIFIERS, tree.getModifiers()).add(Role.DECLARED_TYPE, tree.getType())
          .add(Role.NAME_EXPRESSION, tree.getNameExpression()).add(Role.INITIALIZER, tree.getInitializer());
      case BlockTree tree -> children.add(Role.STATEMENT, tree.getStatements());
      case DoWhileLoopTree tree -> children.add(Role.STATEMENT, tree.getStatement()).add(Role.CONDITION, tree.getCondition());
      case WhileLoopTree tree -> children.add(Role.CONDITION, tree.getCondition()).add(Role.STATEMENT, tree.getStatement());
      case ForLoopTree tree -> children.add(Role.INITIALIZER, tree.getInitializer()).add(Role.CONDITION, tree.getCondition())
          .add(Role.UPDATE, tree.getUpdate()).add(Role.STATEMENT, tree.getStatement());
      case EnhancedForLoopTree tree ->
        children.add(Role.PARAMETER, tree.getVariable()).add(Role.ITERABLE, tree.getExpression()).add(Role.STATEMENT, tree.getStatement());
      case LabeledStatementTree tree -> children.add(Role.STATEMENT, tree.getStatement());
      case SwitchTree tree -> children.add(Role.EXPRESSION, tree.getExpression()).add(Role.CASE, tree.getCases());
      case SwitchExpressionTree tree -> children.add(Role.EXPRESSION, tree.getExpression()).add(Role.CASE, tree.getCases());
      case CaseTree tree -> {
        children.add(Role.LABEL, tree.getLabels()).add(Role.GUARD, tree.getGuard());
        if (tree.getCaseKind() == CaseTree.CaseKind.RULE) {
          children.add(Role.BODY, tree.getBody());
        } else {
          children.add(Role.STATEMENT, tree.getStatements());
        }
      }
      case SynchronizedTree tree -> children.add(Role.MONITOR, tree.getExpression()).add(Role.BODY, tree.getBlock());
      case TryTree tree -> children.add(Role.RESOURCE, tree.getResources()).add(Role.BODY, tree.getBlock())
          .add(Role.CATCH, tree.getCatches()).add(Role.FINALLY, tree.getFinallyBlock());
      case CatchTree tree -> children.add(Role.PARAMETER, tree.getParameter()).add(Role.BODY, tree.getBlock());
      case ConditionalExpressionTree tree -> children.add(Role.CONDITION, tree.getCondition())
          .add(Role.TRUE_EXPRESSION, tree.getTrueExpression()).add(Role.FALSE_EXPRESSION, tree.getFalseExpression());
      case IfTree tree -> children.add(Role.CONDITION, tree.getCondition()).add(Role.THEN_STATEMENT, tree.getThenStatement())
          .add(Role.ELSE_STATEMENT, tree.getElseStatement());
      case ExpressionStatementTree tree -> children.add(Role.EXPRESSION, tree.getExpression());
      case ReturnTree tree -> children.add(Role.EXPRESSION, tree.getExpression());
      case ThrowTree tree -> children.add(Role.EXPRESSION, tree.getExpression());
      case AssertTree tree -> children.add(Role.CONDITION, tree.getCondition()).add(Role.DETAIL, tree.getDetail());
      case MethodInvocationTree tree -> children.add(Role.TYPE_ARGUMENT, tree.getTypeArguments())
          .add(Role.METHOD_SELECT, tree.getMethodSelect()).add(Role.ARGUMENT, tree.getArguments());
      case NewClassTree tree -> children.add(Role.ENCLOSING_EXPRESSION, tree.getEnclosingExpression())
          .add(Role.CONSTRUCTED_TYPE, tree.getIdentifier()).add(Role.TYPE_ARGUMENT, tree.getTypeArguments())
          .add(Role.ARGUMENT, tree.getArguments()).add(Role.CLASS_BODY, tree.getClassBody());
      case NewArrayTree tree -> {
        children.add(Role.ARRAY_ELEMENT_TYPE, tree.getType()).add(Role.DIMENSION, tree.getDimensions())
            .add(Role.ARRAY_INITIALIZER, tree.getInitializers()).add(Role.ANNOTATION, tree.getAnnotations());
        for (var dimension = 0; dimension < tree.getDimAnnotations().size(); dimension++) {
          var annotations = tree.getDimAnnotations().get(dimension);
          for (var ordinal = 0; ordinal < annotations.size(); ordinal++) {
            children.values.add(new Child(annotations.get(ordinal),
                List.of(new Step(Role.DIMENSION_ANNOTATION_GROUP, dimension), new Step(Role.ANNOTATION, ordinal))));
          }
        }
      }
      case LambdaExpressionTree tree -> children.add(Role.PARAMETER, tree.getParameters()).add(Role.BODY, tree.getBody());
      case ParenthesizedTree tree -> children.add(Role.EXPRESSION, tree.getExpression());
      case AssignmentTree tree -> children.add(Role.ASSIGNED_VARIABLE, tree.getVariable()).add(Role.EXPRESSION, tree.getExpression());
      case CompoundAssignmentTree tree ->
        children.add(Role.ASSIGNED_VARIABLE, tree.getVariable()).add(Role.EXPRESSION, tree.getExpression());
      case UnaryTree tree -> children.add(Role.EXPRESSION, tree.getExpression());
      case BinaryTree tree -> children.add(Role.LEFT_OPERAND, tree.getLeftOperand()).add(Role.RIGHT_OPERAND, tree.getRightOperand());
      case TypeCastTree tree -> children.add(Role.CAST_TYPE, tree.getType()).add(Role.EXPRESSION, tree.getExpression());
      case InstanceOfTree tree -> {
        children.add(Role.EXPRESSION, tree.getExpression());
        if (tree.getPattern() != null) {
          children.add(Role.PATTERN, tree.getPattern());
        } else {
          children.add(Role.TESTED_TYPE, tree.getType());
        }
      }
      case BindingPatternTree tree -> children.add(Role.DECLARED_TYPE, tree.getVariable());
      case ConstantCaseLabelTree tree -> children.add(Role.CONSTANT_EXPRESSION, tree.getConstantExpression());
      case PatternCaseLabelTree tree -> children.add(Role.PATTERN, tree.getPattern());
      case DeconstructionPatternTree tree ->
        children.add(Role.DECONSTRUCTOR, tree.getDeconstructor()).add(Role.NESTED_PATTERN, tree.getNestedPatterns());
      case ArrayAccessTree tree -> children.add(Role.EXPRESSION, tree.getExpression()).add(Role.INDEX, tree.getIndex());
      case MemberSelectTree tree -> children.add(Role.TYPE_QUALIFIER, tree.getExpression());
      case MemberReferenceTree tree ->
        children.add(Role.TYPE_QUALIFIER, tree.getQualifierExpression()).add(Role.TYPE_ARGUMENT, tree.getTypeArguments());
      case ArrayTypeTree tree -> children.add(Role.COMPONENT_TYPE, tree.getType());
      case ParameterizedTypeTree tree -> children.add(Role.TYPE_NAME, tree.getType()).add(Role.TYPE_ARGUMENT, tree.getTypeArguments());
      case UnionTypeTree tree -> children.add(Role.UNION_ALTERNATIVE, tree.getTypeAlternatives());
      case IntersectionTypeTree tree -> children.add(Role.INTERSECTION_BOUND, tree.getBounds());
      case TypeParameterTree tree -> children.add(Role.ANNOTATION, tree.getAnnotations()).add(Role.TYPE_BOUND, tree.getBounds());
      case WildcardTree tree ->
        children.add(tree.getKind() == Tree.Kind.SUPER_WILDCARD ? Role.LOWER_BOUND : Role.UPPER_BOUND, tree.getBound());
      case ModifiersTree tree -> children.add(Role.ANNOTATION, tree.getAnnotations());
      case AnnotationTree tree -> children.add(Role.ANNOTATION_TYPE, tree.getAnnotationType()).add(Role.ARGUMENT, tree.getArguments());
      case AnnotatedTypeTree tree ->
        children.add(Role.ANNOTATION, tree.getAnnotations()).add(Role.UNDERLYING_TYPE, tree.getUnderlyingType());
      case ModuleTree tree -> children.add(Role.ANNOTATION, tree.getAnnotations()).add(Role.MODULE_NAME, tree.getName()).add(Role.DIRECTIVE,
          tree.getDirectives());
      case ExportsTree tree -> children.add(Role.PACKAGE_NAME, tree.getPackageName()).add(Role.MODULE_NAME, tree.getModuleNames());
      case OpensTree tree -> children.add(Role.PACKAGE_NAME, tree.getPackageName()).add(Role.MODULE_NAME, tree.getModuleNames());
      case ProvidesTree tree ->
        children.add(Role.SERVICE_NAME, tree.getServiceName()).add(Role.IMPLEMENTATION_NAME, tree.getImplementationNames());
      case RequiresTree tree -> children.add(Role.MODULE_NAME, tree.getModuleName());
      case UsesTree tree -> children.add(Role.SERVICE_NAME, tree.getServiceName());
      case YieldTree tree -> children.add(Role.YIELDED_VALUE, tree.getValue());
      default -> {
        // Compiler scanner leaf forms have no direct child encounter.
      }
    }
    return List.copyOf(children.values);
  }

  record Child(Tree tree, List<Step> steps) {
    Child {
      steps = List.copyOf(steps);
    }

    Step step() {
      return steps.getLast();
    }
  }

  private static final class Children {
    private final List<Child> values = new ArrayList<>();

    private Children add(Role role, @Nullable Tree tree) {
      if (tree != null) {
        values.add(new Child(tree, List.of(new Step(role, 0))));
      }
      return this;
    }

    private Children add(Role role, @Nullable List<? extends Tree> trees) {
      if (trees != null) {
        for (var ordinal = 0; ordinal < trees.size(); ordinal++) {
          var tree = trees.get(ordinal);
          if (tree != null) {
            values.add(new Child(tree, List.of(new Step(role, ordinal))));
          }
        }
      }
      return this;
    }
  }
}
