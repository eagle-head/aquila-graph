package org.aquilagraph.frontend.internal;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.WrittenExpressionRegion;

/** Parses written additive regions while keeping higher-precedence compiler operands intact. */
final class JavaWrittenExpression {
  private JavaWrittenExpression() {
  }

  static Capture capture(TreePath path, SourceFile source, JavaSourceEvidence evidence, JavaSourceTokens sourceTokens) {
    if (!(path.getLeaf() instanceof LiteralTree) && !additive(path.getLeaf())) {
      return Unchanged.INSTANCE;
    }
    if (erroneousOperand(path.getLeaf())) {
      // Malformed compiler occurrences retain their diagnosed shape rather than inventing a legal written region.
      return Unchanged.INSTANCE;
    }
    var range = evidence.origin(path.getCompilationUnit(), source, path.getLeaf());
    if (range.isEmpty()) {
      return Unchanged.INSTANCE;
    }
    var span = range.orElseThrow();
    var tokens = sourceTokens.within(span.startOffset(), span.endOffsetExclusive());
    var anchors = new ArrayList<Anchor>();
    anchors(path, source, evidence, anchors);
    var parser = new Parser(source, tokens, anchors);
    var node = parser.additive();
    if (node.isEmpty() || parser.index != tokens.size()) {
      return new Unavailable(span);
    }
    var result = node.orElseThrow();
    return matches(result, path, source, evidence) ? Unchanged.INSTANCE : new Recovered(result);
  }

  sealed interface Capture permits Unchanged, Recovered, Unavailable {
  }

  enum Unchanged implements Capture {
    INSTANCE
  }

  record Recovered(Node root) implements Capture {
  }

  record Unavailable(SourceSpan origin) implements Capture {
  }

  static List<WrittenExpressionRegion.Occurrence> occurrences(Node root, Function<TreePath, SourceOwnerPath> owners) {
    var result = new ArrayList<WrittenExpressionRegion.Occurrence>();
    collect(root, List.of(), owners, result, false);
    return List.copyOf(result);
  }

  private static void collect(Node node, List<SyntaxStructuralPath.Step> path, Function<TreePath, SourceOwnerPath> owners,
      List<WrittenExpressionRegion.Occurrence> result, boolean immediateMinus) {
    var kind = switch (node) {
      case Anchor anchor -> JavaSourceKind.valueOf(anchor.path().getLeaf().getKind().name());
      case Literal literal -> literal.sourceKind();
      case Unary unary -> JavaSourceKind.valueOf(unary.operator().name());
      case Binary binary -> JavaSourceKind.valueOf(binary.operator().name());
    };
    result.add(new WrittenExpressionRegion.Occurrence(path, kind, node.origin(),
        node instanceof Anchor anchor ? Optional.of(owners.apply(anchor.path())) : Optional.empty(),
        node instanceof Literal literal
            ? JavaSourceLiteral.parse(literal, immediateMinus).flatMap(JavaSourceLiteral.Value::restrictedDecimal)
            : Optional.empty(),
        node instanceof Literal literal
            ? JavaSourceLiteral.parse(literal, immediateMinus).flatMap(JavaSourceLiteral.Value::constant)
                .flatMap(JavaConstantExpression::copyScalar)
            : Optional.empty()));
    switch (node) {
      case Unary unary ->
        collect(unary.operand(), child(path, SyntaxRole.OPERAND), owners, result, unary.operator() == SyntaxOperator.UNARY_MINUS);
      case Binary binary -> {
        collect(binary.left(), child(path, SyntaxRole.LEFT_OPERAND), owners, result, false);
        collect(binary.right(), child(path, SyntaxRole.RIGHT_OPERAND), owners, result, false);
      }
      case Anchor _,Literal _ -> {
      }
    }
  }

  static List<SyntaxStructuralPath.Step> child(List<SyntaxStructuralPath.Step> path, SyntaxRole role) {
    var result = new ArrayList<>(path);
    result.add(new SyntaxStructuralPath.Step(role, 0));
    return List.copyOf(result);
  }

  private static void anchors(TreePath path, SourceFile source, JavaSourceEvidence evidence, List<Anchor> result) {
    if (path.getLeaf() instanceof BinaryTree binary && additive(binary)) {
      anchors(new TreePath(path, binary.getLeftOperand()), source, evidence, result);
      anchors(new TreePath(path, binary.getRightOperand()), source, evidence, result);
    } else if (!(path.getLeaf() instanceof LiteralTree)) {
      evidence.origin(path.getCompilationUnit(), source, path.getLeaf()).ifPresent(span -> result.add(new Anchor(path, span)));
    }
  }

  private static boolean additive(Tree tree) {
    return tree.getKind() == Tree.Kind.PLUS || tree.getKind() == Tree.Kind.MINUS;
  }

  private static boolean erroneousOperand(Tree tree) {
    return tree instanceof ErroneousTree || (tree instanceof BinaryTree binary && additive(binary)
        && (erroneousOperand(binary.getLeftOperand()) || erroneousOperand(binary.getRightOperand())));
  }

  private static boolean matches(Node node, TreePath path, SourceFile source, JavaSourceEvidence evidence) {
    if (!evidence.origin(path.getCompilationUnit(), source, path.getLeaf()).equals(Optional.of(node.origin()))) {
      return false;
    }
    return switch (node) {
      case Anchor anchor -> JavaProjectionIdentity.sameInstance(anchor.path().getLeaf(), path.getLeaf());
      case Literal literal ->
        path.getLeaf() instanceof LiteralTree compiler && literal.sourceKind().name().equals(compiler.getKind().name())
            && (literal.token().kind() != JavaSourceTokens.Kind.IDENTIFIER || JavaSourceLiteral.parse(literal, false)
                .map(value -> value.constant().equals(Optional.ofNullable(compiler.getValue()))).orElse(false));
      case Unary _ -> false;
      case Binary binary -> path.getLeaf() instanceof BinaryTree compiler && binary.operator().name().equals(compiler.getKind().name())
          && matches(binary.left(), new TreePath(path, compiler.getLeftOperand()), source, evidence)
          && matches(binary.right(), new TreePath(path, compiler.getRightOperand()), source, evidence);
    };
  }

  sealed interface Node permits Anchor, Literal, Unary, Binary {
    SourceSpan origin();
  }

  record Anchor(TreePath path, SourceSpan origin) implements Node {
  }

  record Literal(JavaSourceTokens.Token token, SourceSpan origin) implements Node {
    JavaSourceKind sourceKind() {
      return switch (token.kind()) {
        case STRING, TEXT_BLOCK -> JavaSourceKind.STRING_LITERAL;
        case CHARACTER -> JavaSourceKind.CHAR_LITERAL;
        case NUMBER -> numberKind(token.value());
        default -> "null".equals(token.meaning()) ? JavaSourceKind.NULL_LITERAL : JavaSourceKind.BOOLEAN_LITERAL;
      };
    }

    private static JavaSourceKind numberKind(String text) {
      var normalized = text.toLowerCase(Locale.ROOT);
      if (normalized.endsWith("l")) {
        return JavaSourceKind.LONG_LITERAL;
      }
      var hexadecimal = normalized.startsWith("0x");
      var floating = normalized.contains(".") || normalized.contains(hexadecimal ? "p" : "e")
          || (!hexadecimal && (normalized.endsWith("f") || normalized.endsWith("d")));
      return floating
          ? normalized.endsWith("f") ? JavaSourceKind.FLOAT_LITERAL : JavaSourceKind.DOUBLE_LITERAL
          : JavaSourceKind.INT_LITERAL;
    }
  }

  record Unary(SyntaxOperator operator, Node operand, SourceSpan origin) implements Node {
  }

  record Binary(SyntaxOperator operator, Node left, Node right, SourceSpan origin) implements Node {
  }

  private static final class Parser {
    private final SourceFile source;
    private final List<JavaSourceTokens.Token> tokens;
    private final List<Anchor> anchors;
    private int index;

    private Parser(SourceFile source, List<JavaSourceTokens.Token> tokens, List<Anchor> anchors) {
      this.source = source;
      this.tokens = tokens;
      this.anchors = anchors;
    }

    private Optional<Node> additive() {
      return operand().flatMap(this::additiveTail);
    }

    private Optional<Node> additiveTail(Node left) {
      if (index >= tokens.size() || !("+".equals(tokens.get(index).value()) || "-".equals(tokens.get(index).value()))) {
        return Optional.of(left);
      }
      var operator = "+".equals(tokens.get(index).value()) ? SyntaxOperator.PLUS : SyntaxOperator.MINUS;
      index++;
      return operand().flatMap(
          right -> additiveTail(new Binary(operator, left, right, span(left.origin().startOffset(), right.origin().endOffsetExclusive()))));
    }

    private Optional<Node> operand() {
      if (index >= tokens.size()) {
        return Optional.empty();
      }
      var token = tokens.get(index);
      var anchor = anchors.stream().filter(value -> value.origin().startOffset() == token.start()).findFirst();
      if (anchor.isPresent()) {
        var selected = anchor.orElseThrow();
        while (index < tokens.size() && tokens.get(index).end() <= selected.origin().endOffsetExclusive()) {
          index++;
        }
        if (tokens.get(index - 1).end() != selected.origin().endOffsetExclusive()) {
          return Optional.empty();
        }
        return Optional.of(selected);
      }
      index++;
      if ("-".equals(token.value()) || "+".equals(token.value()) || "~".equals(token.value()) || "!".equals(token.value())) {
        var operator = switch (token.value()) {
          case "-" -> SyntaxOperator.UNARY_MINUS;
          case "+" -> SyntaxOperator.UNARY_PLUS;
          case "~" -> SyntaxOperator.BITWISE_COMPLEMENT;
          default -> SyntaxOperator.LOGICAL_COMPLEMENT;
        };
        return operand().map(value -> new Unary(operator, value, span(token.start(), value.origin().endOffsetExclusive())));
      }
      return token.kind() != JavaSourceTokens.Kind.SYMBOL
          && (token.kind() != JavaSourceTokens.Kind.IDENTIFIER || List.of("true", "false", "null").contains(token.meaning()))
              ? Optional.of(new Literal(token, span(token.start(), token.end())))
              : Optional.empty();
    }

    private SourceSpan span(long start, long end) {
      return new SourceSpan(source.sourceFileKey(), start, end);
    }
  }
}
