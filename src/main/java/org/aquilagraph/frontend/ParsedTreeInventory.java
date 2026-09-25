package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.Collections;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.syntax.SyntaxValue;

/** Identifies written syntax before compiler attribution can insert tree nodes. */
final class ParsedTreeInventory {
  private final Set<Tree> writtenTrees;
  private final Map<AnnotationTree, SyntaxValue.Annotation> annotations;
  private final Map<AnnotationTree, TreePath> annotationTypePaths;
  private final Map<VariableTree, List<AnnotationTree>> variableAnnotationAttachments;

  private ParsedTreeInventory(Set<Tree> writtenTrees, Map<AnnotationTree, SyntaxValue.Annotation> annotations,
      Map<AnnotationTree, TreePath> annotationTypePaths, Map<VariableTree, List<AnnotationTree>> variableAnnotations) {
    this.writtenTrees = writtenTrees;
    this.annotations = annotations;
    this.annotationTypePaths = annotationTypePaths;
    this.variableAnnotationAttachments = variableAnnotations;
  }

  static ParsedTreeInventory capture(List<CompilationUnitTree> units, Function<CompilationUnitTree, Optional<SourceFile>> sources,
      JavaSourceEvidence evidence) {
    Objects.requireNonNull(units, "units");
    Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(evidence, "evidence");
    var writtenTrees = Collections.newSetFromMap(new IdentityHashMap<Tree, Boolean>());
    var annotations = new IdentityHashMap<AnnotationTree, SyntaxValue.Annotation>();
    var annotationTypePaths = new IdentityHashMap<AnnotationTree, TreePath>();
    var variableAnnotations = new IdentityHashMap<VariableTree, List<AnnotationTree>>();
    for (var unit : units) {
      scanUnit(unit, sources.apply(unit), evidence, writtenTrees, annotations, annotationTypePaths, variableAnnotations);
    }
    return new ParsedTreeInventory(writtenTrees, Collections.unmodifiableMap(annotations), Collections.unmodifiableMap(annotationTypePaths),
        Collections.unmodifiableMap(variableAnnotations));
  }

  private static void scanUnit(CompilationUnitTree unit, Optional<SourceFile> source, JavaSourceEvidence evidence, Set<Tree> writtenTrees,
      Map<AnnotationTree, SyntaxValue.Annotation> annotations, Map<AnnotationTree, TreePath> annotationTypePaths,
      Map<VariableTree, List<AnnotationTree>> variableAnnotations) {
    var scanner = new TreePathScanner<Void, Void>() {
      @Override
      public Void scan(Tree tree, Void unused) {
        if (tree != null) {
          writtenTrees.add(tree);
        }
        return super.scan(tree, null);
      }

      @Override
      public Void visitAnnotation(AnnotationTree annotation, Void unused) {
        annotationTypePaths.put(annotation, new TreePath(getCurrentPath(), annotation.getAnnotationType()));
        source.flatMap(file -> annotationValue(unit, file, annotation, evidence)).ifPresent(value -> annotations.put(annotation, value));
        return super.visitAnnotation(annotation, null);
      }

      @Override
      public Void visitVariable(VariableTree variable, Void unused) {
        variableAnnotations.put(variable, List.copyOf(variable.getModifiers().getAnnotations()));
        return super.visitVariable(variable, null);
      }
    };
    scanner.scan(unit, null);
  }

  Optional<SyntaxValue.Annotation> annotation(AnnotationTree tree) {
    return Optional.ofNullable(annotations.get(Objects.requireNonNull(tree)));
  }

  Optional<TreePath> annotationTypePath(AnnotationTree tree) {
    return Optional.ofNullable(annotationTypePaths.get(Objects.requireNonNull(tree)));
  }

  Optional<List<AnnotationTree>> variableAnnotations(VariableTree tree) {
    return Optional.ofNullable(variableAnnotationAttachments.get(Objects.requireNonNull(tree)));
  }

  private static Optional<SyntaxValue.Annotation> annotationValue(CompilationUnitTree unit, SourceFile source, AnnotationTree annotation,
      JavaSourceEvidence evidence) {
    var position = evidence.position(unit, source, annotation);
    var typePosition = evidence.position(unit, source, annotation.getAnnotationType());
    if (!(position instanceof JavaSourceEvidence.Position.Available annotationRange)
        || !(typePosition instanceof JavaSourceEvidence.Position.Available typeRange)
        || typeRange.span().endOffsetExclusive() > annotationRange.span().endOffsetExclusive()) {
      return Optional.empty();
    }
    var start = Math.toIntExact(typeRange.span().endOffsetExclusive());
    var end = Math.toIntExact(annotationRange.span().endOffsetExclusive());
    var delimiter = firstSignificant(decodeUnicode(source.content().substring(start, end)));
    var arguments = annotation.getArguments();
    if (arguments.isEmpty()) {
      if (delimiter.isPresent() && delimiter.orElseThrow() != '(') {
        return Optional.empty();
      }
      var form = delimiter.isPresent() ? SyntaxValue.AnnotationForm.EMPTY_NORMAL : SyntaxValue.AnnotationForm.MARKER;
      return Optional.of(new SyntaxValue.Annotation(form, List.of(), SyntaxValue.AnnotationLocation.UNRESOLVED));
    }
    if (delimiter.isEmpty() || delimiter.orElseThrow() != '(') {
      return Optional.empty();
    }
    var headers = new ArrayList<SyntaxValue.AnnotationArgumentHeader>();
    for (ExpressionTree argument : arguments) {
      if (argument instanceof AssignmentTree assignment) {
        if (!(assignment.getVariable() instanceof IdentifierTree name)) {
          return Optional.empty();
        }
        headers.add(new SyntaxValue.AnnotationArgumentHeader(Optional.of(name.getName().toString())));
      } else {
        headers.add(new SyntaxValue.AnnotationArgumentHeader(Optional.empty()));
      }
    }
    var named = headers.stream().allMatch(header -> header.explicitName().isPresent());
    var form = named ? SyntaxValue.AnnotationForm.NAMED_NORMAL : SyntaxValue.AnnotationForm.SINGLE_ELEMENT;
    if (!named && headers.size() != 1) {
      return Optional.empty();
    }
    return Optional.of(new SyntaxValue.Annotation(form, headers, SyntaxValue.AnnotationLocation.UNRESOLVED));
  }

  private static String decodeUnicode(String source) {
    var decoded = new StringBuilder(source.length());
    var index = 0;
    var previousWasEscape = false;
    var trailingBackslashes = 0;
    while (index < source.length()) {
      var eligible = previousWasEscape || trailingBackslashes % 2 == 0;
      if (source.charAt(index) == '\\' && eligible && index + 1 < source.length() && source.charAt(index + 1) == 'u') {
        var hex = index + 2;
        while (hex < source.length() && source.charAt(hex) == 'u') {
          hex++;
        }
        if (hex + 4 <= source.length()) {
          try {
            var character = (char) Integer.parseInt(source.substring(hex, hex + 4), 16);
            decoded.append(character);
            trailingBackslashes = character == '\\' ? trailingBackslashes + 1 : 0;
            index = hex + 4;
            previousWasEscape = true;
            continue;
          } catch (NumberFormatException ignored) {
            // Invalid escapes remain visible for source-correlation failure.
          }
        }
      }
      var character = source.charAt(index);
      decoded.append(character);
      trailingBackslashes = character == '\\' ? trailingBackslashes + 1 : 0;
      previousWasEscape = false;
      index++;
    }
    return decoded.toString();
  }

  private static Optional<Character> firstSignificant(String source) {
    var index = 0;
    while (index < source.length()) {
      var current = source.charAt(index);
      if (Character.isWhitespace(current)) {
        index++;
      } else if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
        index += 2;
        while (index < source.length() && source.charAt(index) != '\n' && source.charAt(index) != '\r') {
          index++;
        }
      } else if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
        var close = source.indexOf("*/", index + 2);
        if (close < 0) {
          return Optional.empty();
        }
        index = close + 2;
      } else {
        return Optional.of(current);
      }
    }
    return Optional.empty();
  }

  boolean isWritten(Tree tree) {
    return writtenTrees.contains(Objects.requireNonNull(tree));
  }

  boolean isCompilerInsertedSuperCall(TreePath statementPath) {
    Objects.requireNonNull(statementPath, "statementPath");
    var tree = statementPath.getLeaf();
    if (!(tree instanceof ExpressionStatementTree statement) || !(statement.getExpression() instanceof MethodInvocationTree invocation)
        || !(invocation.getMethodSelect() instanceof IdentifierTree selector) || !selector.getName().contentEquals("super")
        || !(statementPath.getParentPath().getLeaf() instanceof BlockTree block) || block.getStatements().isEmpty()
        || !block.getStatements().getFirst().equals(tree)
        || !(statementPath.getParentPath().getParentPath().getLeaf() instanceof MethodTree method) || method.getReturnType() != null) {
      return false;
    }
    return !isWritten(tree);
  }
}
