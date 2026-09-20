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

final class JavaMethodBodyTranslator {
  private static final String EXPRESSION_PATH = ".expression";

  private final JavaSemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final JavaSyntaxClassifier classifier;

  JavaMethodBodyTranslator(JavaSemanticResolver semantics, JavaSourceEvidence sourceEvidence) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    classifier = new JavaSyntaxClassifier(semantics);
  }

  TranslationResult<MethodBody> translate(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, BlockTree body,
      TreePath bodyPath) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(methodKey, "methodKey");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(bodyPath, "bodyPath");
    var translation = translateTree(unit, sourceFile, methodKey, body, bodyPath, "body");
    return new TranslationResult<>(new MethodBody(translation.node(), translation.supported()), translation.diagnostics());
  }

  private SyntaxTreeTranslation translateTree(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree, TreePath path,
      String structuralPath) {
    var children = new SyntaxChildren();
    var description = classifier.classify(tree, path);
    switch (tree) {
      case BlockTree block -> addAll(children, unit, sourceFile, methodKey, block.getStatements(), path, structuralPath);
      case VariableTree variable ->
        add(children, unit, sourceFile, methodKey, variable.getInitializer(), path, structuralPath + ".initializer");
      case ExpressionStatementTree statement ->
        add(children, unit, sourceFile, methodKey, statement.getExpression(), path, structuralPath + EXPRESSION_PATH);
      case ReturnTree statement ->
        add(children, unit, sourceFile, methodKey, statement.getExpression(), path, structuralPath + EXPRESSION_PATH);
      case ThrowTree statement ->
        add(children, unit, sourceFile, methodKey, statement.getExpression(), path, structuralPath + EXPRESSION_PATH);
      case YieldTree statement -> add(children, unit, sourceFile, methodKey, statement.getValue(), path, structuralPath + ".value");
      case IfTree statement -> {
        add(children, unit, sourceFile, methodKey, statement.getCondition(), path, structuralPath + ".condition");
        add(children, unit, sourceFile, methodKey, statement.getThenStatement(), path, structuralPath + ".then");
        add(children, unit, sourceFile, methodKey, statement.getElseStatement(), path, structuralPath + ".else");
      }
      case TryTree statement -> {
        if (!statement.getResources().isEmpty() || statement.getFinallyBlock() != null) {
          children.reject(unsupportedDiagnostic(unit, sourceFile, methodKey, statement));
        }
        addAll(children, unit, sourceFile, methodKey, statement.getResources(), path, structuralPath + ".resource");
        add(children, unit, sourceFile, methodKey, statement.getBlock(), path, structuralPath + ".try");
        addAll(children, unit, sourceFile, methodKey, statement.getCatches(), path, structuralPath + ".catch");
        add(children, unit, sourceFile, methodKey, statement.getFinallyBlock(), path, structuralPath + ".finally");
      }
      case CatchTree statement -> {
        add(children, unit, sourceFile, methodKey, statement.getParameter(), path, structuralPath + ".parameter");
        add(children, unit, sourceFile, methodKey, statement.getBlock(), path, structuralPath + ".body");
      }
      case SwitchExpressionTree expression -> {
        add(children, unit, sourceFile, methodKey, expression.getExpression(), path, structuralPath + ".selector");
        addAll(children, unit, sourceFile, methodKey, expression.getCases(), path, structuralPath + ".case");
      }
      case CaseTree caseTree -> {
        if (caseTree.getBody() != null) {
          add(children, unit, sourceFile, methodKey, caseTree.getBody(), path, structuralPath + ".body");
        } else {
          addAll(children, unit, sourceFile, methodKey, caseTree.getStatements(), path, structuralPath + ".statement");
        }
      }
      case MethodInvocationTree invocation -> {
        add(children, unit, sourceFile, methodKey, invocation.getMethodSelect(), path, structuralPath + ".target");
        addAll(children, unit, sourceFile, methodKey, invocation.getArguments(), path, structuralPath + ".argument");
      }
      case NewClassTree creation -> {
        add(children, unit, sourceFile, methodKey, creation.getEnclosingExpression(), path, structuralPath + ".enclosing");
        addAll(children, unit, sourceFile, methodKey, creation.getArguments(), path, structuralPath + ".argument");
      }
      case MemberSelectTree selection ->
        add(children, unit, sourceFile, methodKey, selection.getExpression(), path, structuralPath + ".qualifier");
      case UnaryTree unary -> add(children, unit, sourceFile, methodKey, unary.getExpression(), path, structuralPath + ".operand");
      case BinaryTree binary -> {
        add(children, unit, sourceFile, methodKey, binary.getLeftOperand(), path, structuralPath + ".left");
        add(children, unit, sourceFile, methodKey, binary.getRightOperand(), path, structuralPath + ".right");
      }
      case AssignmentTree assignment -> {
        add(children, unit, sourceFile, methodKey, assignment.getVariable(), path, structuralPath + ".target");
        add(children, unit, sourceFile, methodKey, assignment.getExpression(), path, structuralPath + ".value");
      }
      case ArrayAccessTree access -> {
        add(children, unit, sourceFile, methodKey, access.getExpression(), path, structuralPath + ".array");
        add(children, unit, sourceFile, methodKey, access.getIndex(), path, structuralPath + ".index");
      }
      case ParenthesizedTree expression ->
        add(children, unit, sourceFile, methodKey, expression.getExpression(), path, structuralPath + EXPRESSION_PATH);
      case TypeCastTree expression ->
        add(children, unit, sourceFile, methodKey, expression.getExpression(), path, structuralPath + EXPRESSION_PATH);
      case ConditionalExpressionTree expression -> {
        add(children, unit, sourceFile, methodKey, expression.getCondition(), path, structuralPath + ".condition");
        add(children, unit, sourceFile, methodKey, expression.getTrueExpression(), path, structuralPath + ".true");
        add(children, unit, sourceFile, methodKey, expression.getFalseExpression(), path, structuralPath + ".false");
      }
      case IdentifierTree ignored -> {
      }
      case LiteralTree ignored -> {
      }
      default -> {
        if (description.kind() == SyntaxKind.UNSUPPORTED) {
          children.reject(unsupportedDiagnostic(unit, sourceFile, methodKey, tree));
        }
      }
    }

    var origins = sourceEvidence.origins(unit, sourceFile, tree);
    var nodeId = StableId.sha256("syntax", methodKey, structuralPath, tree.getKind().name(),
        origins.stream().map(Object::toString).collect(Collectors.joining("|")));
    var node = new SyntaxNode(nodeId, description.kind(), description.detail(), children.nodes(), semantics.references(path), origins,
        origins.isEmpty());
    return new SyntaxTreeTranslation(node, children.supported(), children.diagnostics());
  }

  private void add(SyntaxChildren children, CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree child, TreePath parent,
      String structuralPath) {
    if (child != null) {
      children.add(translateTree(unit, sourceFile, methodKey, child, new TreePath(parent, child), structuralPath));
    }
  }

  private void addAll(SyntaxChildren children, CompilationUnitTree unit, SourceFile sourceFile, String methodKey,
      List<? extends Tree> childTrees, TreePath parent, String structuralPath) {
    for (var index = 0; index < childTrees.size(); index++) {
      add(children, unit, sourceFile, methodKey, childTrees.get(index), parent, structuralPath + "." + index);
    }
  }

  private AnalysisDiagnostic unsupportedDiagnostic(CompilationUnitTree unit, SourceFile sourceFile, String methodKey, Tree tree) {
    var origins = sourceEvidence.origins(unit, sourceFile, tree);
    var message = "Unsupported Java construct in method body: " + tree.getKind();
    return new AnalysisDiagnostic(StableId.sha256("diagnostic", methodKey, tree.getKind().name(), message), "frontend.unsupported-syntax",
        DiagnosticStage.FRONTEND, DiagnosticSeverity.ERROR, message, origins);
  }

  private record SyntaxTreeTranslation(SyntaxNode node, boolean supported, List<AnalysisDiagnostic> diagnostics) {
    private SyntaxTreeTranslation {
      Objects.requireNonNull(node, "node");
      diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
  }

  private static final class SyntaxChildren {
    private final List<SyntaxNode> childNodes = new ArrayList<>();
    private final List<AnalysisDiagnostic> childDiagnostics = new ArrayList<>();
    private boolean allSupported = true;

    void add(SyntaxTreeTranslation translation) {
      childNodes.add(translation.node());
      childDiagnostics.addAll(translation.diagnostics());
      if (!translation.supported()) {
        allSupported = false;
      }
    }

    void reject(AnalysisDiagnostic diagnostic) {
      childDiagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic"));
      allSupported = false;
    }

    List<SyntaxNode> nodes() {
      return List.copyOf(childNodes);
    }

    List<AnalysisDiagnostic> diagnostics() {
      return List.copyOf(childDiagnostics);
    }

    boolean supported() {
      return allSupported;
    }
  }
}
