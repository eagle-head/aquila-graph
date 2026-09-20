package org.aquilagraph.frontend;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
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
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.DiagnosticSeverity;
import org.aquilagraph.model.DiagnosticStage;
import org.aquilagraph.model.MethodBody;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.StableId;
import org.aquilagraph.model.SyntaxKind;
import org.aquilagraph.model.SyntaxNode;

final class JavaSyntaxTranslator {
  private final JavaTreeSemantics semantics;
  private final JavaSyntaxClassifier classifier;
  private final CompilationUnitTree unit;
  private final SourceFile sourceFile;
  private final String methodKey;
  private final List<AnalysisDiagnostic> diagnostics = new ArrayList<>();
  private boolean supported = true;

  JavaSyntaxTranslator(
      JavaTreeSemantics semantics,
      CompilationUnitTree unit,
      SourceFile sourceFile,
      String methodKey) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    classifier = new JavaSyntaxClassifier(semantics);
    this.unit = Objects.requireNonNull(unit, "unit");
    this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
    this.methodKey = Objects.requireNonNull(methodKey, "methodKey");
  }

  Translation translate(BlockTree body, TreePath bodyPath) {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(bodyPath, "bodyPath");
    var syntaxRoot = translateTree(body, bodyPath, "body");
    return new Translation(new MethodBody(syntaxRoot, supported), diagnostics);
  }

  private SyntaxNode translateTree(Tree tree, TreePath path, String structuralPath) {
    var children = new ArrayList<SyntaxNode>();
    var kind = classifier.classify(tree, path);
    var detail = classifier.detail(tree);
    switch (tree) {
      case BlockTree block -> addAll(children, block.getStatements(), path, structuralPath);
      case VariableTree variable ->
          add(children, variable.getInitializer(), path, structuralPath + ".initializer");
      case ExpressionStatementTree statement ->
          add(children, statement.getExpression(), path, structuralPath + ".expression");
      case ReturnTree statement ->
          add(children, statement.getExpression(), path, structuralPath + ".expression");
      case ThrowTree statement ->
          add(children, statement.getExpression(), path, structuralPath + ".expression");
      case YieldTree statement ->
          add(children, statement.getValue(), path, structuralPath + ".value");
      case IfTree statement -> {
        add(children, statement.getCondition(), path, structuralPath + ".condition");
        add(children, statement.getThenStatement(), path, structuralPath + ".then");
        add(children, statement.getElseStatement(), path, structuralPath + ".else");
      }
      case TryTree statement -> {
        if (!statement.getResources().isEmpty() || statement.getFinallyBlock() != null) {
          addUnsupportedDiagnostic(statement);
        }
        addAll(children, statement.getResources(), path, structuralPath + ".resource");
        add(children, statement.getBlock(), path, structuralPath + ".try");
        addAll(children, statement.getCatches(), path, structuralPath + ".catch");
        add(children, statement.getFinallyBlock(), path, structuralPath + ".finally");
      }
      case CatchTree statement -> {
        add(children, statement.getParameter(), path, structuralPath + ".parameter");
        add(children, statement.getBlock(), path, structuralPath + ".body");
      }
      case SwitchExpressionTree expression -> {
        add(children, expression.getExpression(), path, structuralPath + ".selector");
        addAll(children, expression.getCases(), path, structuralPath + ".case");
      }
      case CaseTree caseTree -> {
        if (caseTree.getBody() != null) {
          add(children, caseTree.getBody(), path, structuralPath + ".body");
        } else {
          addAll(children, caseTree.getStatements(), path, structuralPath + ".statement");
        }
      }
      case MethodInvocationTree invocation -> {
        add(children, invocation.getMethodSelect(), path, structuralPath + ".target");
        addAll(children, invocation.getArguments(), path, structuralPath + ".argument");
      }
      case NewClassTree creation -> {
        add(children, creation.getEnclosingExpression(), path, structuralPath + ".enclosing");
        addAll(children, creation.getArguments(), path, structuralPath + ".argument");
      }
      case MemberSelectTree selection ->
          add(children, selection.getExpression(), path, structuralPath + ".qualifier");
      case UnaryTree unary ->
          add(children, unary.getExpression(), path, structuralPath + ".operand");
      case BinaryTree binary -> {
        add(children, binary.getLeftOperand(), path, structuralPath + ".left");
        add(children, binary.getRightOperand(), path, structuralPath + ".right");
      }
      case AssignmentTree assignment -> {
        add(children, assignment.getVariable(), path, structuralPath + ".target");
        add(children, assignment.getExpression(), path, structuralPath + ".value");
      }
      case ArrayAccessTree access -> {
        add(children, access.getExpression(), path, structuralPath + ".array");
        add(children, access.getIndex(), path, structuralPath + ".index");
      }
      case ParenthesizedTree expression ->
          add(children, expression.getExpression(), path, structuralPath + ".expression");
      case TypeCastTree expression ->
          add(children, expression.getExpression(), path, structuralPath + ".expression");
      case ConditionalExpressionTree expression -> {
        add(children, expression.getCondition(), path, structuralPath + ".condition");
        add(children, expression.getTrueExpression(), path, structuralPath + ".true");
        add(children, expression.getFalseExpression(), path, structuralPath + ".false");
      }
      case IdentifierTree ignored -> {}
      case LiteralTree ignored -> {}
      default -> {
        if (kind == SyntaxKind.UNSUPPORTED) {
          addUnsupportedDiagnostic(tree);
        }
      }
    }

    var origins = semantics.sourceOrigins(unit, sourceFile, tree);
    var nodeId =
        StableId.sha256(
            "syntax",
            methodKey,
            structuralPath,
            tree.getKind().name(),
            origins.stream().map(Object::toString).collect(Collectors.joining("|")));
    return new SyntaxNode(
        nodeId,
        kind,
        detail,
        children,
        semantics.semanticReferences(path),
        origins,
        origins.isEmpty());
  }

  private void add(
      List<SyntaxNode> children, Tree child, TreePath parent, String structuralPath) {
    if (child != null) {
      children.add(translateTree(child, new TreePath(parent, child), structuralPath));
    }
  }

  private void addAll(
      List<SyntaxNode> children,
      List<? extends Tree> childTrees,
      TreePath parent,
      String structuralPath) {
    for (var index = 0; index < childTrees.size(); index++) {
      add(children, childTrees.get(index), parent, structuralPath + "." + index);
    }
  }

  private void addUnsupportedDiagnostic(Tree tree) {
    supported = false;
    var origins = semantics.sourceOrigins(unit, sourceFile, tree);
    var message = "Unsupported Java construct in method body: " + tree.getKind();
    diagnostics.add(
        new AnalysisDiagnostic(
            StableId.sha256("diagnostic", methodKey, tree.getKind().name(), message),
            "frontend.unsupported-syntax",
            DiagnosticStage.FRONTEND,
            DiagnosticSeverity.ERROR,
            message,
            origins));
  }

  record Translation(MethodBody body, List<AnalysisDiagnostic> diagnostics) {
    Translation {
      body = Objects.requireNonNull(body, "body");
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
  }
}
