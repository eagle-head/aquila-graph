package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
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
import javax.lang.model.element.Modifier;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceOwnerPath;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.SyntaxValue;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.WrittenSourceAssociation;

/** Identifies written syntax before compiler attribution can insert tree nodes. */
final class ParsedTreeInventory {
  private static final int SINGLE_ORIGINAL_OWNER_COUNT = 1;
  private final Set<Tree> writtenTrees;
  private final Map<AnnotationTree, SyntaxValue.Annotation> annotations;
  private final Map<AnnotationTree, TreePath> annotationTypePaths;
  private final Map<AnnotationTree, TreePath> annotationPaths;
  private final Map<AnnotationTree, List<WrittenAnnotationArgument>> capturedArguments;
  private final Map<Tree, List<AnnotationTree>> annotationsByOwner;
  private final Map<VariableTree, List<AnnotationTree>> variableAnnotationAttachments;
  private final Map<ModifiersTree, Set<Modifier>> capturedModifiers;
  private final Map<Tree, List<ParsedSourceOwner>> capturedOwners;

  private ParsedTreeInventory(Set<Tree> writtenTrees, Map<AnnotationTree, SyntaxValue.Annotation> annotations,
      Map<AnnotationTree, TreePath> annotationTypePaths, Map<AnnotationTree, TreePath> annotationPaths,
      Map<AnnotationTree, List<WrittenAnnotationArgument>> annotationArguments, Map<Tree, List<AnnotationTree>> annotationsByOwner,
      Map<VariableTree, List<AnnotationTree>> variableAnnotations, Map<ModifiersTree, Set<Modifier>> modifiers,
      Map<Tree, List<ParsedSourceOwner>> owners) {
    this.writtenTrees = writtenTrees;
    this.annotations = annotations;
    this.annotationTypePaths = annotationTypePaths;
    this.annotationPaths = annotationPaths;
    this.capturedArguments = annotationArguments;
    this.annotationsByOwner = annotationsByOwner;
    this.variableAnnotationAttachments = variableAnnotations;
    this.capturedModifiers = modifiers;
    this.capturedOwners = owners;
  }

  static ParsedTreeInventory capture(List<CompilationUnitTree> units, Function<CompilationUnitTree, Optional<SourceFile>> sources,
      JavaSourceEvidence evidence) {
    Objects.requireNonNull(units, "units");
    Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(evidence, "evidence");
    var writtenTrees = Collections.newSetFromMap(new IdentityHashMap<Tree, Boolean>());
    var annotations = new IdentityHashMap<AnnotationTree, SyntaxValue.Annotation>();
    var annotationTypePaths = new IdentityHashMap<AnnotationTree, TreePath>();
    var annotationPaths = new IdentityHashMap<AnnotationTree, TreePath>();
    var annotationArguments = new IdentityHashMap<AnnotationTree, List<WrittenAnnotationArgument>>();
    var annotationsByOwner = new IdentityHashMap<Tree, List<AnnotationTree>>();
    var variableAnnotations = new IdentityHashMap<VariableTree, List<AnnotationTree>>();
    var modifiers = new IdentityHashMap<ModifiersTree, Set<Modifier>>();
    var owners = new IdentityHashMap<Tree, List<ParsedSourceOwner>>();
    JavaCoverageTopology.owners(units, sources, evidence)
        .forEach(owner -> owners.computeIfAbsent(owner.tree(), ignored -> new ArrayList<>()).add(new ParsedSourceOwner(owner.treePath(),
            owner.source().sourceFileKey(), owner.path(), JavaSourceKind.valueOf(owner.tree().getKind().name()), owner.origin())));
    owners.replaceAll((tree, values) -> List.copyOf(values));
    for (var unit : units) {
      scanUnit(unit, sources.apply(unit), evidence, writtenTrees, annotations, annotationTypePaths, annotationPaths, annotationArguments,
          annotationsByOwner, variableAnnotations, modifiers);
    }
    return new ParsedTreeInventory(writtenTrees, Collections.unmodifiableMap(annotations), Collections.unmodifiableMap(annotationTypePaths),
        Collections.unmodifiableMap(annotationPaths), Collections.unmodifiableMap(annotationArguments),
        Collections.unmodifiableMap(annotationsByOwner), Collections.unmodifiableMap(variableAnnotations),
        Collections.unmodifiableMap(modifiers), Collections.unmodifiableMap(owners));
  }

  private static void scanUnit(CompilationUnitTree unit, Optional<SourceFile> source, JavaSourceEvidence evidence, Set<Tree> writtenTrees,
      Map<AnnotationTree, SyntaxValue.Annotation> annotations, Map<AnnotationTree, TreePath> annotationTypePaths,
      Map<AnnotationTree, TreePath> annotationPaths, Map<AnnotationTree, List<WrittenAnnotationArgument>> annotationArguments,
      Map<Tree, List<AnnotationTree>> annotationsByOwner, Map<VariableTree, List<AnnotationTree>> variableAnnotations,
      Map<ModifiersTree, Set<Modifier>> modifiers) {
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
        annotationPaths.putIfAbsent(annotation, getCurrentPath());
        var owner = getCurrentPath().getParentPath();
        while (owner != null && owner.getLeaf() instanceof ModifiersTree) {
          owner = owner.getParentPath();
        }
        if (owner != null) {
          annotationsByOwner.computeIfAbsent(owner.getLeaf(), ignored -> new ArrayList<>()).add(annotation);
        }
        annotationTypePaths.putIfAbsent(annotation, new TreePath(getCurrentPath(), annotation.getAnnotationType()));
        annotationArguments.putIfAbsent(annotation,
            annotation.getArguments().stream().map(argument -> writtenArgument(getCurrentPath(), argument)).toList());
        source.flatMap(file -> annotationValue(unit, file, annotation, evidence)).ifPresent(value -> annotations.put(annotation, value));
        return super.visitAnnotation(annotation, null);
      }

      @Override
      public Void visitModifiers(ModifiersTree tree, Void unused) {
        modifiers.put(tree, Set.copyOf(tree.getFlags()));
        return super.visitModifiers(tree, null);
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

  Optional<TreePath> annotationPath(AnnotationTree tree) {
    return Optional.ofNullable(annotationPaths.get(Objects.requireNonNull(tree)));
  }

  List<AnnotationTree> ownerAnnotations(Tree owner) {
    return List.copyOf(annotationsByOwner.getOrDefault(Objects.requireNonNull(owner), List.of()));
  }

  Set<Modifier> writtenModifiers(ModifiersTree owner) {
    var flags = capturedModifiers.get(Objects.requireNonNull(owner, "owner"));
    if (flags == null) {
      throw new IllegalArgumentException("Written modifier evidence requires an actual parsed modifier owner");
    }
    return flags;
  }

  Optional<WrittenSourceAssociation> sourceAssociation(SyntaxNode node, Tree tree) {
    var owners = capturedOwners.getOrDefault(Objects.requireNonNull(tree, "tree"), List.of());
    if (owners.size() > SINGLE_ORIGINAL_OWNER_COUNT) {
      throw new IllegalArgumentException("A shared parsed tree requires its exact creating owner path");
    }
    return owners.stream().findFirst().map(owner -> association(node, owner));
  }

  Optional<WrittenSourceAssociation> sourceAssociation(SyntaxNode node, TreePath path) {
    return capturedOwners.getOrDefault(Objects.requireNonNull(path, "path").getLeaf(), List.of()).stream()
        .filter(owner -> samePath(owner.treePath(), path)).findFirst().map(owner -> association(node, owner));
  }

  private static WrittenSourceAssociation association(SyntaxNode node, ParsedSourceOwner owner) {
    return new WrittenSourceAssociation(node.nodeId(), owner.sourceFileKey(), owner.path(), owner.sourceKind(), owner.origin());
  }

  private record ParsedSourceOwner(TreePath treePath, String sourceFileKey, SourceOwnerPath path, JavaSourceKind sourceKind,
      Optional<SourceSpan> origin) {
  }

  private static boolean samePath(TreePath first, TreePath second) {
    var left = first;
    var right = second;
    while (left != null && right != null) {
      if (!JavaProjectionIdentity.sameInstance(left.getLeaf(), right.getLeaf())) {
        return false;
      }
      left = left.getParentPath();
      right = right.getParentPath();
    }
    return left == null && right == null;
  }

  Optional<List<WrittenAnnotationArgument>> annotationArguments(AnnotationTree tree) {
    return Optional.ofNullable(capturedArguments.get(Objects.requireNonNull(tree)));
  }

  private static WrittenAnnotationArgument writtenArgument(TreePath annotationPath, ExpressionTree argument) {
    var argumentPath = new TreePath(annotationPath, argument);
    if (argument instanceof AssignmentTree assignment && assignment.getVariable() instanceof IdentifierTree name) {
      return new WrittenAnnotationArgument(argument, assignment.getExpression(), Optional.of(name),
          new TreePath(argumentPath, assignment.getExpression()));
    }
    return new WrittenAnnotationArgument(argument, argument, Optional.empty(), argumentPath);
  }

  record WrittenAnnotationArgument(ExpressionTree argument, ExpressionTree value, Optional<IdentifierTree> name, TreePath valuePath) {
    WrittenAnnotationArgument {
      Objects.requireNonNull(argument, "argument");
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(valuePath, "valuePath");
    }
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
