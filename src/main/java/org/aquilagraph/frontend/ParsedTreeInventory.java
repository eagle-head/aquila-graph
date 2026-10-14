package org.aquilagraph.frontend;

import com.sun.source.tree.NewClassTree;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxType;

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
  private final Map<VariableTree, Boolean> inferredVariableTypes;
  private final Map<LambdaExpressionTree, SyntaxValue.Lambda> lambdaForms;
  private final Map<VariableTree, LocalBindingEvidence.TypeForm> lambdaParameterTypes;
  private final Map<ClassTree, List<SourceSpan>> capturedEmptyDeclarations;
  private final Map<CompilationUnitTree, List<SourceSpan>> capturedUnitEmptyDeclarations;

  private ParsedTreeInventory(Set<Tree> writtenTrees, Map<AnnotationTree, SyntaxValue.Annotation> annotations,
      Map<AnnotationTree, TreePath> annotationTypePaths, Map<AnnotationTree, TreePath> annotationPaths,
      Map<AnnotationTree, List<WrittenAnnotationArgument>> annotationArguments, Map<Tree, List<AnnotationTree>> annotationsByOwner,
      Map<VariableTree, List<AnnotationTree>> variableAnnotations, Map<ModifiersTree, Set<Modifier>> modifiers,
      Map<Tree, List<ParsedSourceOwner>> owners, Map<VariableTree, Boolean> inferredTypes,
      Map<LambdaExpressionTree, SyntaxValue.Lambda> lambdas, Map<VariableTree, LocalBindingEvidence.TypeForm> parameterTypes,
      Map<ClassTree, List<SourceSpan>> emptyDeclarations, Map<CompilationUnitTree, List<SourceSpan>> unitEmptyDeclarations) {
    this.writtenTrees = writtenTrees;
    this.annotations = annotations;
    this.annotationTypePaths = annotationTypePaths;
    this.annotationPaths = annotationPaths;
    this.capturedArguments = annotationArguments;
    this.annotationsByOwner = annotationsByOwner;
    this.variableAnnotationAttachments = variableAnnotations;
    this.capturedModifiers = modifiers;
    this.capturedOwners = owners;
    this.inferredVariableTypes = inferredTypes;
    this.lambdaForms = lambdas;
    this.lambdaParameterTypes = parameterTypes;
    this.capturedEmptyDeclarations = emptyDeclarations;
    this.capturedUnitEmptyDeclarations = unitEmptyDeclarations;
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
    var inferredTypes = new IdentityHashMap<VariableTree, Boolean>();
    var lambdas = new IdentityHashMap<LambdaExpressionTree, SyntaxValue.Lambda>();
    var parameterTypes = new IdentityHashMap<VariableTree, LocalBindingEvidence.TypeForm>();
    var emptyDeclarations = new IdentityHashMap<ClassTree, List<SourceSpan>>();
    var unitEmptyDeclarations = new IdentityHashMap<CompilationUnitTree, List<SourceSpan>>();
    JavaCoverageTopology.owners(units, sources, evidence).forEach(owner -> {
      owners.computeIfAbsent(owner.tree(), ignored -> new ArrayList<>()).add(new ParsedSourceOwner(owner.treePath(),
          owner.source().sourceFileKey(), owner.path(), JavaSourceKind.valueOf(owner.tree().getKind().name()), owner.origin()));
      if (owner.tree() instanceof LambdaExpressionTree lambda) {
        captureLambda(owner, lambda, evidence, lambdas, parameterTypes);
      }
    });
    owners.replaceAll((tree, values) -> List.copyOf(values));
    for (var unit : units) {
      sources.apply(unit).ifPresent(source -> unitEmptyDeclarations.put(unit, captureUnitEmptyDeclarations(unit, source, evidence)));
      scanUnit(unit, sources.apply(unit), evidence, writtenTrees, annotations, annotationTypePaths, annotationPaths, annotationArguments,
          annotationsByOwner, variableAnnotations, modifiers, inferredTypes, emptyDeclarations);
    }
    return new ParsedTreeInventory(writtenTrees, Collections.unmodifiableMap(annotations), Collections.unmodifiableMap(annotationTypePaths),
        Collections.unmodifiableMap(annotationPaths), Collections.unmodifiableMap(annotationArguments),
        Collections.unmodifiableMap(annotationsByOwner), Collections.unmodifiableMap(variableAnnotations),
        Collections.unmodifiableMap(modifiers), Collections.unmodifiableMap(owners), Collections.unmodifiableMap(inferredTypes),
        Collections.unmodifiableMap(lambdas), Collections.unmodifiableMap(parameterTypes), Collections.unmodifiableMap(emptyDeclarations),
        Collections.unmodifiableMap(unitEmptyDeclarations));
  }

  private static List<SourceSpan> captureUnitEmptyDeclarations(CompilationUnitTree unit, SourceFile source, JavaSourceEvidence evidence) {
    if (unit.getTypeDecls().stream().anyMatch(tree -> tree instanceof ClassTree && evidence.origin(unit, source, tree).isEmpty())) {
      return List.of();
    }
    var occupied = Stream.concat(unit.getImports().stream(), unit.getTypeDecls().stream())
        .filter(tree -> !(tree instanceof EmptyStatementTree)).flatMap(tree -> evidence.origin(unit, source, tree).stream()).toList();
    var header = Stream.of(unit.getPackage(), unit.getModule()).filter(Objects::nonNull)
        .flatMap(tree -> evidence.origin(unit, source, tree).stream()).toList();
    return sourceTokens(source.content()).stream().filter(token -> ";".equals(token.value()))
        .filter(token -> Stream.concat(occupied.stream(), header.stream())
            .noneMatch(range -> token.start() >= range.startOffset() && token.end() <= range.endOffsetExclusive()))
        .map(token -> new SourceSpan(source.sourceFileKey(), token.start(), token.end())).toList();
  }

  List<SourceSpan> emptyUnitDeclarations(CompilationUnitTree unit) {
    return capturedUnitEmptyDeclarations.getOrDefault(unit, List.of());
  }

  SourceOwnerPath emptyUnitDeclarationPath(CompilationUnitTree unit, SourceSpan range, int ordinal) {
    return unit.getTypeDecls().stream().filter(EmptyStatementTree.class::isInstance)
        .flatMap(tree -> capturedOwners.getOrDefault(tree, List.of()).stream()).filter(owner -> owner.origin().equals(Optional.of(range)))
        .map(ParsedSourceOwner::path).findFirst()
        .orElseGet(() -> SourceOwnerPath.root().child(SourceOwnerPath.Role.EMPTY_DECLARATION, ordinal));
  }

  WrittenSourceAssociation emptyUnitDeclarationAssociation(SyntaxNode node, CompilationUnitTree unit, int ordinal) {
    var range = node.origin().orElseThrow();
    return new WrittenSourceAssociation(node.nodeId(), node.sourceFileKey(), emptyUnitDeclarationPath(unit, range, ordinal),
        JavaSourceKind.EMPTY_STATEMENT, Optional.of(range));
  }

  private static void captureLambda(JavaCoverageTopology.Owner owner, LambdaExpressionTree lambda, JavaSourceEvidence evidence,
      Map<LambdaExpressionTree, SyntaxValue.Lambda> forms, Map<VariableTree, LocalBindingEvidence.TypeForm> parameterTypes) {
    var parameters = new ArrayList<SyntaxValue.LambdaParameter>();
    for (var parameter : lambda.getParameters()) {
      var tokens = evidence.origin(owner.unit(), owner.source(), parameter)
          .map(span -> sourceTokens(
              owner.source().content().substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()))).stream()
              .map(SourceToken::value).toList())
          .orElse(List.of());
      var typeForm = parameter.getType() != null
          ? LocalBindingEvidence.TypeForm.DECLARED
          : tokens.size() == 1 ? LocalBindingEvidence.TypeForm.IMPLICIT : LocalBindingEvidence.TypeForm.INFERRED;
      parameterTypes.put(parameter, typeForm);
      parameters.add(new SyntaxValue.LambdaParameter(typeForm, tokens.contains("...")));
    }
    var parenthesized = owner.origin()
        .map(span -> firstSignificant(decodeUnicode(
            owner.source().content().substring(Math.toIntExact(span.startOffset()), Math.toIntExact(span.endOffsetExclusive()))).text())
            .filter(value -> value == '(').isPresent())
        .orElse(true);
    var bodyKind = lambda.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION
        ? SyntaxValue.LambdaBodyKind.EXPRESSION
        : SyntaxValue.LambdaBodyKind.BLOCK;
    forms.put(lambda, new SyntaxValue.Lambda(bodyKind, parenthesized, parameters, SyntaxType.Unresolved.INSTANCE));
  }

  SyntaxValue.Lambda lambdaForm(LambdaExpressionTree lambda) {
    return Objects.requireNonNull(lambdaForms.get(lambda), "parsed lambda form");
  }

  LocalBindingEvidence.TypeForm bindingTypeForm(VariableTree variable) {
    return lambdaParameterTypes.getOrDefault(variable,
        hasInferredVariableType(variable) ? LocalBindingEvidence.TypeForm.INFERRED : LocalBindingEvidence.TypeForm.DECLARED);
  }

  private static List<SourceToken> sourceTokens(String source) {
    var decoded = decodeUnicode(source);
    var text = decoded.text();
    var result = new ArrayList<SourceToken>();
    var index = 0;
    while (index < text.length()) {
      var tokenStart = index;
      var character = text.charAt(index);
      if (Character.isWhitespace(character)) {
        index++;
      } else if (text.startsWith("/*", index)) {
        var end = text.indexOf("*/", index + 2);
        index = end < 0 ? text.length() : end + 2;
      } else if (text.startsWith("//", index)) {
        while (index < text.length() && text.charAt(index) != '\n' && text.charAt(index) != '\r') {
          index++;
        }
      } else if (character == '"' || character == '\'') {
        var delimiter = text.startsWith("\"\"\"", index) ? "\"\"\"" : Character.toString(character);
        index += delimiter.length();
        while (index < text.length()) {
          if (text.startsWith(delimiter, index)) {
            index += delimiter.length();
            break;
          }
          var next = text.charAt(index);
          index++;
          if (next == '\\' && index < text.length()) {
            index++;
          }
        }
        result.add(new SourceToken("literal", decoded.starts().get(tokenStart), decoded.ends().get(index - 1)));
      } else if (Character.isJavaIdentifierStart(text.codePointAt(index))) {
        var start = index;
        index += Character.charCount(text.codePointAt(index));
        while (index < text.length() && Character.isJavaIdentifierPart(text.codePointAt(index))) {
          index += Character.charCount(text.codePointAt(index));
        }
        result.add(new SourceToken(text.substring(start, index), decoded.starts().get(start), decoded.ends().get(index - 1)));
      } else if (text.startsWith("...", index)) {
        index += 3;
        result.add(new SourceToken("...", decoded.starts().get(tokenStart), decoded.ends().get(index - 1)));
      } else {
        index++;
        result.add(new SourceToken(Character.toString(character), decoded.starts().get(tokenStart), decoded.ends().get(index - 1)));
      }
    }
    return List.copyOf(result);
  }

  private static void scanUnit(CompilationUnitTree unit, Optional<SourceFile> source, JavaSourceEvidence evidence, Set<Tree> writtenTrees,
      Map<AnnotationTree, SyntaxValue.Annotation> annotations, Map<AnnotationTree, TreePath> annotationTypePaths,
      Map<AnnotationTree, TreePath> annotationPaths, Map<AnnotationTree, List<WrittenAnnotationArgument>> annotationArguments,
      Map<Tree, List<AnnotationTree>> annotationsByOwner, Map<VariableTree, List<AnnotationTree>> variableAnnotations,
      Map<ModifiersTree, Set<Modifier>> modifiers, Map<VariableTree, Boolean> inferredTypes,
      Map<ClassTree, List<SourceSpan>> emptyDeclarations) {
    var tokens = source.map(file -> sourceTokens(file.content())).orElse(List.of());
    var scanner = new TreePathScanner<Void, Void>() {
      @Override
      public Void visitClass(ClassTree tree, Void unused) {
        source.ifPresent(file -> {
          var envelope = evidence.origin(unit, file, tree);
          var parent = getCurrentPath().getParentPath();
          if (envelope.isEmpty() && parent != null && JavaProjectionIdentity.sameInstance(parent.getLeaf(), unit)
              && unit.getTypeDecls().size() == 1 && unit.getPackage() == null && unit.getModule() == null
              && evidence.position(unit, file, tree) instanceof JavaSourceEvidence.Position.Missing) {
            // An implicit compact type has no written end; its member area belongs to the source file.
            envelope = Optional.of(new SourceSpan(file.sourceFileKey(), 0, file.content().length()));
          }
          envelope.ifPresent(origin -> {
            var bodyStart = origin.startOffset();
            if (tree.getSimpleName().isEmpty() && parent != null && parent.getLeaf() instanceof NewClassTree creation) {
              var boundary = creation.getArguments().isEmpty()
                  ? origin.startOffset()
                  : evidence.rawEnd(unit, creation.getArguments().getLast());
              bodyStart = tokens.stream()
                  .filter(token -> "{".equals(token.value()) && token.start() >= boundary && token.end() <= origin.endOffsetExclusive())
                  .findFirst().map(token -> (long) token.start()).orElse(bodyStart);
            }
            var memberStart = bodyStart;
            var represented = Stream.concat(tree.getMembers().stream(), unit.getImports().stream())
                .flatMap(member -> evidence.origin(unit, file, member).stream()).toList();
            var declarations = tokens.stream().filter(token -> ";".equals(token.value()))
                .filter(token -> token.start() >= memberStart && token.end() <= origin.endOffsetExclusive())
                .filter(token -> represented.stream()
                    .noneMatch(member -> token.start() >= member.startOffset() && token.end() <= member.endOffsetExclusive()))
                .skip(tree.getKind() == Kind.ENUM && !tree.getSimpleName().isEmpty() ? 1 : 0)
                .map(token -> new SourceSpan(file.sourceFileKey(), token.start(), token.end())).toList();
            emptyDeclarations.put(tree, declarations);
          });
        });
        return super.visitClass(tree, null);
      }

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
        inferredTypes.put(variable, variable.getType() == null);
        return super.visitVariable(variable, null);
      }
    };
    scanner.scan(unit, null);
  }

  boolean hasInferredVariableType(VariableTree variable) {
    return inferredVariableTypes.getOrDefault(variable, false);
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

  DeclarationMemberOrder.TreeOrder attributedTreeOrder(TreePath path) {
    var owner = parsedOwnerPath(path)
        .orElseThrow(() -> new IllegalArgumentException("Attributed member order requires its parsed type owner"));
    var members = ((ClassTree) path.getLeaf()).getMembers().stream().map(member -> parsedOwnerPath(new TreePath(path, member))).toList();
    return new DeclarationMemberOrder.TreeOrder(owner, members);
  }

  private Optional<SourceOwnerPath> parsedOwnerPath(TreePath path) {
    return capturedOwners.getOrDefault(path.getLeaf(), List.of()).stream().filter(owner -> samePath(owner.treePath(), path))
        .map(ParsedSourceOwner::path).findFirst();
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
    var delimiter = firstSignificant(decodeUnicode(source.content().substring(start, end)).text());
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

  private static DecodedSource decodeUnicode(String source) {
    var decoded = new StringBuilder(source.length());
    var starts = new ArrayList<Integer>();
    var ends = new ArrayList<Integer>();
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
            starts.add(index);
            ends.add(hex + 4);
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
      starts.add(index);
      ends.add(index + 1);
      trailingBackslashes = character == '\\' ? trailingBackslashes + 1 : 0;
      previousWasEscape = false;
      index++;
    }
    return new DecodedSource(decoded.toString(), List.copyOf(starts), List.copyOf(ends));
  }

  List<SourceSpan> emptyBodyDeclarations(ClassTree tree) {
    return capturedEmptyDeclarations.getOrDefault(tree, List.of());
  }

  WrittenSourceAssociation emptyDeclarationAssociation(SyntaxNode node, TreePath owner, int ordinal) {
    var range = emptyBodyDeclarations((ClassTree) owner.getLeaf()).get(ordinal);
    if (node.sourceKind() != JavaSourceKind.EMPTY_STATEMENT || !node.origin().equals(Optional.of(range))) {
      throw new IllegalArgumentException("An empty declaration requires its exact captured source occurrence");
    }
    var path = emptyDeclarationPath(owner, ordinal);
    return new WrittenSourceAssociation(node.nodeId(), range.sourceFileKey(), path, JavaSourceKind.EMPTY_STATEMENT, Optional.of(range));
  }

  SourceOwnerPath emptyDeclarationPath(TreePath owner, int ordinal) {
    return parsedOwnerPath(owner).orElseThrow().child(SourceOwnerPath.Role.EMPTY_DECLARATION, ordinal);
  }

  private record SourceToken(String value, int start, int end) {
  }

  private record DecodedSource(String text, List<Integer> starts, List<Integer> ends) {
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
