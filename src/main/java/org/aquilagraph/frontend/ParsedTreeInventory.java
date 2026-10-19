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
import java.util.Comparator;
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
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.api.WrittenExpressionRegion;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxType;

/** Identifies written syntax before compiler attribution can insert tree nodes. */
final class ParsedTreeInventory {
  private static final int SINGLE_ORIGINAL_OWNER_COUNT = 1;
  private final Map<CompilationUnitTree, JavaSourceTokens> sourceTokens;
  private final Map<Tree, List<Expression>> writtenExpressions;
  private final JavaSourceEvidence expressionEvidence;
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
  private final Map<CompilationUnitTree, SourceFile> capturedSources;

  private ParsedTreeInventory(Set<Tree> writtenTrees, Map<AnnotationTree, SyntaxValue.Annotation> annotations,
      Map<AnnotationTree, TreePath> annotationTypePaths, Map<AnnotationTree, TreePath> annotationPaths,
      Map<AnnotationTree, List<WrittenAnnotationArgument>> annotationArguments, Map<Tree, List<AnnotationTree>> annotationsByOwner,
      Map<VariableTree, List<AnnotationTree>> variableAnnotations, Map<ModifiersTree, Set<Modifier>> modifiers,
      Map<Tree, List<ParsedSourceOwner>> owners, Map<VariableTree, Boolean> inferredTypes,
      Map<LambdaExpressionTree, SyntaxValue.Lambda> lambdas, Map<VariableTree, LocalBindingEvidence.TypeForm> parameterTypes,
      Map<ClassTree, List<SourceSpan>> emptyDeclarations, Map<CompilationUnitTree, List<SourceSpan>> unitEmptyDeclarations,
      Map<CompilationUnitTree, SourceFile> sources, Map<CompilationUnitTree, JavaSourceTokens> tokens, JavaSourceEvidence evidence) {
    this.sourceTokens = tokens;
    expressionEvidence = evidence;
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
    this.capturedSources = sources;
    writtenExpressions = captureExpressions(evidence);
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
    var capturedSources = new IdentityHashMap<CompilationUnitTree, SourceFile>();
    var tokenViews = new IdentityHashMap<CompilationUnitTree, JavaSourceTokens>();
    for (var unit : units) {
      tokenViews.put(unit, JavaSourceTokens.capture(sources.apply(unit).map(SourceFile::content).orElse("")));
    }
    JavaCoverageTopology.owners(units, sources, evidence).forEach(owner -> {
      owners.computeIfAbsent(owner.tree(), ignored -> new ArrayList<>()).add(new ParsedSourceOwner(owner.treePath(),
          owner.source().sourceFileKey(), owner.path(), JavaSourceKind.valueOf(owner.tree().getKind().name()), owner.origin()));
      if (owner.tree() instanceof LambdaExpressionTree lambda) {
        captureLambda(owner, lambda, evidence, Objects.requireNonNull(tokenViews.get(owner.unit()), "captured unit tokens"), lambdas,
            parameterTypes);
      }
    });
    owners.replaceAll((tree, values) -> List.copyOf(values));
    for (var unit : units) {
      sources.apply(unit).ifPresent(source -> capturedSources.put(unit, source));
      sources.apply(unit).ifPresent(source -> unitEmptyDeclarations.put(unit,
          captureUnitEmptyDeclarations(unit, source, evidence, Objects.requireNonNull(tokenViews.get(unit), "captured unit tokens"))));
      scanUnit(unit, sources.apply(unit), evidence, writtenTrees, annotations, annotationTypePaths, annotationPaths, annotationArguments,
          annotationsByOwner, variableAnnotations, modifiers, inferredTypes, emptyDeclarations,
          Objects.requireNonNull(tokenViews.get(unit), "captured unit tokens"));
    }
    return new ParsedTreeInventory(writtenTrees, Collections.unmodifiableMap(annotations), Collections.unmodifiableMap(annotationTypePaths),
        Collections.unmodifiableMap(annotationPaths), Collections.unmodifiableMap(annotationArguments),
        Collections.unmodifiableMap(annotationsByOwner), Collections.unmodifiableMap(variableAnnotations),
        Collections.unmodifiableMap(modifiers), Collections.unmodifiableMap(owners), Collections.unmodifiableMap(inferredTypes),
        Collections.unmodifiableMap(lambdas), Collections.unmodifiableMap(parameterTypes), Collections.unmodifiableMap(emptyDeclarations),
        Collections.unmodifiableMap(unitEmptyDeclarations), Collections.unmodifiableMap(capturedSources),
        Collections.unmodifiableMap(tokenViews), evidence);
  }

  void requireSource(CompilationUnitTree unit, SourceFile source) {
    if (!source.equals(capturedSources.get(unit))) {
      throw new IllegalArgumentException("Executable projection requires the captured source of its exact compilation unit");
    }
  }

  private static List<SourceSpan> captureUnitEmptyDeclarations(CompilationUnitTree unit, SourceFile source, JavaSourceEvidence evidence,
      JavaSourceTokens tokens) {
    if (unit.getTypeDecls().stream().anyMatch(tree -> tree instanceof ClassTree && evidence.origin(unit, source, tree).isEmpty())) {
      return List.of();
    }
    var occupied = Stream.concat(unit.getImports().stream(), unit.getTypeDecls().stream())
        .filter(tree -> !(tree instanceof EmptyStatementTree)).flatMap(tree -> evidence.origin(unit, source, tree).stream()).toList();
    var header = Stream.of(unit.getPackage(), unit.getModule()).filter(Objects::nonNull)
        .flatMap(tree -> evidence.origin(unit, source, tree).stream()).toList();
    return tokens.all().stream().filter(token -> ";".equals(token.value()))
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
      JavaSourceTokens tokenView, Map<LambdaExpressionTree, SyntaxValue.Lambda> forms,
      Map<VariableTree, LocalBindingEvidence.TypeForm> parameterTypes) {
    var parameters = new ArrayList<SyntaxValue.LambdaParameter>();
    for (var parameter : lambda.getParameters()) {
      var tokens = evidence.origin(owner.unit(), owner.source(), parameter)
          .map(span -> tokenView.within(span.startOffset(), span.endOffsetExclusive()).stream().map(JavaSourceTokens.Token::value).toList())
          .orElse(List.of());
      var typeForm = parameter.getType() != null
          ? LocalBindingEvidence.TypeForm.DECLARED
          : tokens.size() == 1 ? LocalBindingEvidence.TypeForm.IMPLICIT : LocalBindingEvidence.TypeForm.INFERRED;
      parameterTypes.put(parameter, typeForm);
      parameters.add(new SyntaxValue.LambdaParameter(typeForm, tokens.contains("...")));
    }
    var parenthesized = owner.origin().map(span -> tokenView.within(span.startOffset(), span.endOffsetExclusive()).stream().findFirst()
        .filter(token -> "(".equals(token.value())).isPresent()).orElse(true);
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

  private static void scanUnit(CompilationUnitTree unit, Optional<SourceFile> source, JavaSourceEvidence evidence, Set<Tree> writtenTrees,
      Map<AnnotationTree, SyntaxValue.Annotation> annotations, Map<AnnotationTree, TreePath> annotationTypePaths,
      Map<AnnotationTree, TreePath> annotationPaths, Map<AnnotationTree, List<WrittenAnnotationArgument>> annotationArguments,
      Map<Tree, List<AnnotationTree>> annotationsByOwner, Map<VariableTree, List<AnnotationTree>> variableAnnotations,
      Map<ModifiersTree, Set<Modifier>> modifiers, Map<VariableTree, Boolean> inferredTypes,
      Map<ClassTree, List<SourceSpan>> emptyDeclarations, JavaSourceTokens tokenView) {
    var tokens = tokenView.all();
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
        source.flatMap(file -> annotationValue(unit, file, annotation, evidence, tokens))
            .ifPresent(value -> annotations.put(annotation, value));
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

  Optional<SourceOwnerPath> parsedOwnerPath(TreePath path) {
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
      JavaSourceEvidence evidence, List<JavaSourceTokens.Token> tokens) {
    var position = evidence.position(unit, source, annotation);
    var typePosition = evidence.position(unit, source, annotation.getAnnotationType());
    if (!(position instanceof JavaSourceEvidence.Position.Available annotationRange)
        || !(typePosition instanceof JavaSourceEvidence.Position.Available typeRange)
        || typeRange.span().endOffsetExclusive() > annotationRange.span().endOffsetExclusive()) {
      return Optional.empty();
    }
    var start = Math.toIntExact(typeRange.span().endOffsetExclusive());
    var end = Math.toIntExact(annotationRange.span().endOffsetExclusive());
    var delimiter = tokens.stream().filter(token -> token.start() >= start && token.end() <= end).findFirst()
        .map(JavaSourceTokens.Token::value);
    var arguments = annotation.getArguments();
    if (arguments.isEmpty()) {
      if (delimiter.isPresent() && !"(".equals(delimiter.orElseThrow())) {
        return Optional.empty();
      }
      var form = delimiter.isPresent() ? SyntaxValue.AnnotationForm.EMPTY_NORMAL : SyntaxValue.AnnotationForm.MARKER;
      return Optional.of(new SyntaxValue.Annotation(form, List.of(), SyntaxValue.AnnotationLocation.UNRESOLVED));
    }
    if (delimiter.isEmpty() || !"(".equals(delimiter.orElseThrow())) {
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

  private Map<Tree, List<Expression>> captureExpressions(JavaSourceEvidence evidence) {
    var expressions = new IdentityHashMap<Tree, List<Expression>>();
    capturedOwners.values().stream().flatMap(List::stream).forEach(owner -> {
      var source = Objects.requireNonNull(capturedSources.get(owner.treePath().getCompilationUnit()), "expression source");
      var capture = JavaWrittenExpression.capture(owner.treePath(), source, evidence, tokens(owner.treePath().getCompilationUnit()));
      if (!(capture instanceof JavaWrittenExpression.Unchanged)) {
        var region = capture instanceof JavaWrittenExpression.Recovered recovered
            ? Optional.of(new WrittenExpressionRegion(owner.path(), recovered.root().origin(),
                JavaWrittenExpression.occurrences(recovered.root(), path -> parsedOwnerPath(path).orElseThrow())))
            : Optional.<WrittenExpressionRegion>empty();
        expressions.computeIfAbsent(owner.treePath().getLeaf(), ignored -> new ArrayList<>())
            .add(new Expression(owner.treePath(), capture, region));
      }
    });
    expressions.replaceAll((tree, values) -> List.copyOf(values));
    return Collections.unmodifiableMap(expressions);
  }

  Optional<Expression> writtenExpression(TreePath path) {
    return writtenExpressions.getOrDefault(path.getLeaf(), List.of()).stream().filter(value -> samePath(value.compilerAnchor(), path))
        .findFirst();
  }

  List<WrittenExpressionRegion> expressionRegions() {
    var regions = writtenExpressions.values().stream().flatMap(List::stream).flatMap(value -> value.region().stream()).toList();
    return regions.stream()
        .filter(region -> regions.stream().noneMatch(parent -> !parent.compilerOwner().equals(region.compilerOwner())
            && parent.origin().sourceFileKey().equals(region.origin().sourceFileKey())
            && parent.compilerOwner().isAncestorOf(region.compilerOwner())
            && parent.occurrences().stream().noneMatch(
                occurrence -> occurrence.compilerOperand().filter(operand -> operand.isAncestorOf(region.compilerOwner())).isPresent())))
        .sorted(Comparator.comparing((WrittenExpressionRegion region) -> region.origin().sourceFileKey())
            .thenComparingLong(region -> region.origin().startOffset()).thenComparingLong(region -> region.origin().endOffsetExclusive())
            .thenComparing(WrittenExpressionRegion::compilerOwner, ParsedTreeInventory::compareOwnerPaths))
        .toList();
  }

  private static int compareOwnerPaths(SourceOwnerPath first, SourceOwnerPath second) {
    for (var index = 0; index < Math.min(first.size(), second.size()); index++) {
      var left = first.steps().get(index);
      var right = second.steps().get(index);
      var role = left.role().compareTo(right.role());
      if (role != 0) {
        return role;
      }
      var ordinal = Integer.compare(left.ordinal(), right.ordinal());
      if (ordinal != 0) {
        return ordinal;
      }
    }
    return Integer.compare(first.size(), second.size());
  }

  List<NormalizedExpressionCorrespondence.Observation> expressionObservations(Expression expression, SemanticResolver semantics) {
    var region = expression.region().orElseThrow();
    var source = Objects.requireNonNull(capturedSources.get(expression.compilerAnchor().getCompilationUnit()), "expression source");
    var observations = new ArrayList<NormalizedExpressionCorrespondence.Observation>();
    var constants = new JavaConstantExpression(semantics);
    for (var owners : capturedOwners.values()) {
      for (var owner : owners) {
        if (owner.sourceFileKey().equals(region.origin().sourceFileKey()) && region.compilerOwner().isAncestorOf(owner.path())
            && region.occurrences().stream().noneMatch(occurrence -> occurrence.compilerOperand()
                .filter(operand -> !operand.equals(owner.path()) && operand.isAncestorOf(owner.path())).isPresent())) {
          observations.add(new NormalizedExpressionCorrespondence.Observation(owner.path(),
              JavaSourceKind.valueOf(owner.treePath().getLeaf().getKind().name()),
              expressionEvidence.origin(owner.treePath().getCompilationUnit(), source, owner.treePath().getLeaf()),
              semantics.type(owner.treePath()), constants.value(owner.treePath()).flatMap(JavaConstantExpression::copyScalar),
              constants.constantStatus(owner.treePath())));
        }
      }
    }
    observations.sort(Comparator.comparing((NormalizedExpressionCorrespondence.Observation value) -> value.ownerPath().toString()));
    return List.copyOf(observations);
  }

  record Expression(TreePath compilerAnchor, JavaWrittenExpression.Capture capture, Optional<WrittenExpressionRegion> region) {
  }

  JavaSourceTokens tokens(CompilationUnitTree unit) {
    return Objects.requireNonNull(sourceTokens.get(unit), "captured source tokens");
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
