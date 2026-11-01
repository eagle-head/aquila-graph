package org.aquilagraph.frontend.internal;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.frontend.internal.ConstructorWitnessFileManager.Replacement;

/** Copies exact constructor instantiation facts from private public-compiler witnesses. */
final class JavaConstructorSelection {
  private static final int SELECTED_CALL_COUNT = 1;
  private final JavaCompiler compiler;
  private final JavaCompilerInvocation invocation;
  private final SnapshotSourceCatalog sources;
  private final StandardJavaFileManager manager;
  private final Trees originalTrees;
  private final Types originalTypes;
  private final Elements originalElements;
  private final SemanticResolver originalSemantics;
  private final JavaTranslationPolicy policy;
  private final Function<CompilationUnitTree, JavaSourceTokens> lexicalView;
  private final ConstructorIdentityLookup<Tree, ConstructorCorrelation> selections = ConstructorIdentityLookup.usingReferenceIdentity();

  JavaConstructorSelection(JavaCompiler compiler, JavaCompilerInvocation invocation, SnapshotSourceCatalog sources,
      StandardJavaFileManager manager, Trees trees, Types types, Elements elements, JavaTranslationPolicy policy,
      Function<CompilationUnitTree, JavaSourceTokens> lexicalView) {
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.invocation = Objects.requireNonNull(invocation, "invocation");
    this.sources = Objects.requireNonNull(sources, "sources");
    this.manager = Objects.requireNonNull(manager, "manager");
    originalTrees = Objects.requireNonNull(trees, "trees");
    originalTypes = Objects.requireNonNull(types, "types");
    originalElements = Objects.requireNonNull(elements, "elements");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.lexicalView = Objects.requireNonNull(lexicalView, "lexicalView");
    originalSemantics = new SemanticResolver(trees, types, elements, new DeclarationIdentityRegistry(trees, types, elements, policy));
  }

  ConstructorCorrelation select(TreePath path, ExecutableElement selected) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(selected, "selected");
    if (!selected.equals(originalTrees.getElement(path))) {
      throw new IllegalArgumentException("Constructor selection requires its exact original attributed element");
    }
    return selections.find(path.getLeaf()).orElseGet(ConstructorCorrelation::unavailable);
  }

  void prepare(List<CompilationUnitTree> units) throws IOException {
    var paths = new ArrayList<TreePath>();
    var scanner = new TreePathScanner<Void, Void>() {
      @Override
      public Void visitNewClass(NewClassTree creation, Void ignored) {
        if (!policy.supportsNestedDeclarations() && creation.getClassBody() != null) {
          return null;
        }
        if (policy.supportsNestedDeclarations() || insideMethod(getCurrentPath())) {
          paths.add(getCurrentPath());
        }
        return super.visitNewClass(creation, null);
      }

    };
    units.forEach(unit -> scanner.scan(unit, null));
    for (var path : paths) {
      if (!(path.getLeaf() instanceof NewClassTree creation) || !(originalTrees.getElement(path) instanceof ExecutableElement original)
          || (original.getTypeParameters().isEmpty() && (creation.getClassBody() == null || creation.getArguments().isEmpty()))) {
        continue;
      }
      var target = constructorTarget(path, creation, original);
      if (target.isEmpty()) {
        continue;
      }
      var selected = target.orElseThrow().constructor();
      var created = target.orElseThrow().created();
      var owner = target.orElseThrow().owner();
      if (originalTypes.asMemberOf(created, selected) instanceof ExecutableType executable && executable.getTypeVariables().isEmpty()) {
        selections.remember(creation, attributedSelection(path, creation, selected, executable));
        continue;
      }
      var fixed = creation.getArguments().size() == selected.getParameters().size()
          && !fixedArrayCannotAcceptCast(path, creation, selected, created)
              ? attribute(path, creation, selected, owner, created, false)
              : ConstructorCorrelation.inapplicable();
      var facts = fixed.status() == CorrelationStatus.INAPPLICABLE && selected.isVarArgs()
          ? attribute(path, creation, selected, owner, created, true)
          : fixed;
      selections.remember(creation, facts);
    }
  }

  private ConstructorCorrelation attributedSelection(TreePath path, NewClassTree creation, ExecutableElement selected,
      ExecutableType executable) {
    var arguments = creation.getArguments().stream().map(argument -> childType(originalTrees, path, argument)).toList();
    if (arguments.stream().anyMatch(Objects::isNull)) {
      return ConstructorCorrelation.unavailable();
    }
    var identities = arguments.stream().map(originalSemantics::typeIdentity).toList();
    return new JavaInvocationApplicability(originalSemantics).select(executable, selected.isVarArgs(), arguments)
        .flatMap(selection -> copyFacts(originalSemantics, selection, arguments, identities,
            type -> Optional.of(originalSemantics.typeIdentity(type))))
        .map(ConstructorCorrelation::selected).orElseGet(ConstructorCorrelation::unavailable);
  }

  private static boolean insideMethod(TreePath path) {
    for (var current = path.getParentPath(); current != null; current = current.getParentPath()) {
      if (current.getLeaf() instanceof MethodTree method) {
        return method.getBody() != null;
      }
    }
    return false;
  }

  private boolean fixedArrayCannotAcceptCast(TreePath path, NewClassTree creation, ExecutableElement selected, DeclaredType created) {
    if (!selected.isVarArgs() || creation.getArguments().isEmpty() || !(creation.getArguments().getLast() instanceof TypeCastTree)
        || !(originalTypes.asMemberOf(created, selected) instanceof ExecutableType member)) {
      return false;
    }
    var array = member.getParameterTypes().getLast();
    var source = childType(originalTrees, path, creation.getArguments().getLast());
    return source != null && !ConstructorTypeCorrespondence.retainsWitnessParameter(array, selected)
        && !new JavaInvocationApplicability(originalSemantics).looseConversion(source, array);
  }

  private Optional<ConstructorTarget> constructorTarget(TreePath path, NewClassTree creation, ExecutableElement original) {
    if (creation.getClassBody() == null) {
      return original.getEnclosingElement() instanceof TypeElement owner
          && originalTrees.getTypeMirror(path) instanceof DeclaredType created && created.asElement().equals(owner)
              ? Optional.of(new ConstructorTarget(original, created, owner))
              : Optional.empty();
    }
    var declaration = originalTrees.getPath(original);
    if (declaration == null || !(declaration.getLeaf() instanceof MethodTree method) || method.getBody() == null
        || method.getBody().getStatements().isEmpty()
        || !(method.getBody().getStatements().getFirst() instanceof ExpressionStatementTree statement)
        || !(statement.getExpression() instanceof MethodInvocationTree delegation)) {
      return Optional.empty();
    }
    var call = new TreePath(new TreePath(new TreePath(declaration, method.getBody()), statement), delegation);
    if (!(originalTrees.getElement(call) instanceof ExecutableElement selected)
        || !(selected.getEnclosingElement() instanceof TypeElement owner)
        || !(original.getEnclosingElement() instanceof TypeElement anonymous)
        || !(anonymous.getSuperclass() instanceof DeclaredType created) || !created.asElement().equals(owner)) {
      return Optional.empty();
    }
    return Optional.of(new ConstructorTarget(selected, created, owner));
  }

  private record ConstructorTarget(ExecutableElement constructor, DeclaredType created, TypeElement owner) {
  }

  private ConstructorCorrelation attribute(TreePath path, NewClassTree creation, ExecutableElement selected, TypeElement owner,
      DeclaredType created, boolean expanded) throws IOException {
    if (creation.getClassBody() != null && creation.getArguments().isEmpty()) {
      return ConstructorCorrelation.unavailable();
    }
    var name = uniqueName(owner, "constructorSelection");
    var diamond = creation.getClassBody() == null && creation.getIdentifier() instanceof ParameterizedTypeTree parameterized
        && parameterized.getTypeArguments().isEmpty();
    var notional = new ConstructorNotionalMethod(selected, owner, originalTypes, originalElements, name, diamond,
        creation.getArguments().size(), expanded);
    var projection = Optional.<ConstructorReceiverProjection>empty();
    if (creation.getEnclosingExpression() != null) {
      if (!(owner.getEnclosingElement() instanceof TypeElement enclosingOwner)) {
        return ConstructorCorrelation.unavailable();
      }
      projection = Optional.of(new ConstructorReceiverProjection(owner, created, uniqueName(enclosingOwner, "constructorReceiver"), diamond,
          originalTypes, originalElements));
    }
    var original = path.getCompilationUnit();
    var content = original.getSourceFile().getCharContent(true).toString();
    var enumConstant = path.getParentPath() != null && originalTrees.getElement(path.getParentPath()) != null
        && originalTrees.getElement(path.getParentPath()).getKind() == ElementKind.ENUM_CONSTANT;
    if (enumConstant && creation.getArguments().isEmpty()) {
      return ConstructorCorrelation.unavailable();
    }
    var replaced = creation.getClassBody() != null && projection.isPresent()
        ? creation.getEnclosingExpression()
        : creation.getClassBody() == null && !enumConstant ? creation : creation.getArguments().getFirst();
    var start = position(original, replaced, false);
    var end = position(original, replaced, true);
    if (start < 0 || end < start) {
      return ConstructorCorrelation.unavailable();
    }
    var edits = new ArrayList<SourceEdit>();
    var adaptations = new ArrayList<Replacement>();
    if (!addWitnessDeclaration(owner, notional.sourceMethod(), notional::adaptBinary, edits, adaptations)
        || (projection.isPresent() && !addWitnessDeclaration(projection.orElseThrow().owner(), projection.orElseThrow().sourceMethod(),
            projection.orElseThrow()::adaptBinary, edits, adaptations))) {
      return ConstructorCorrelation.unavailable();
    }
    var selectionExpression = replacement(original, creation, created, content, name, diamond, expanded,
        selected.getParameters().size() - 1, projection, edits);
    var proof = creation.getClassBody() != null || enumConstant;
    // Only the original expression arm completes normally; proof arguments cannot change its flow.
    var expression = proof
        ? "switch (0) { case 1 -> { " + selectionExpression + "; throw null; } default -> " + sourceText(original, replaced, content, edits)
            + "; }"
        : selectionExpression;
    var replacement = proof && projection.isPresent() ? "(" + expression + ")" : expression;
    edits.addFirst(new SourceEdit(original.getSourceFile(), start, end, replacement));
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (
        var witnessManager = new CompilerFileManager(Objects.requireNonNull(
            compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8), "constructor witness file manager"));
        var files = new ConstructorWitnessFileManager(witnessManager, adaptations)) {
      return attributeWitness(path, creation, selected, owner, created, name, expanded, edits, files, diagnostics, projection);
    }
  }

  private boolean addWitnessDeclaration(TypeElement owner, String method, Function<byte[], byte[]> adapt, List<SourceEdit> edits,
      List<Replacement> adaptations) throws IOException {
    var declaration = originalTrees.getPath(owner);
    if (declaration != null && declaration.getLeaf() instanceof ClassTree classTree) {
      var ownerUnit = declaration.getCompilationUnit();
      var ownerStart = position(ownerUnit, classTree, false);
      var ownerEnd = position(ownerUnit, classTree, true);
      var delimiter = lexicalView.apply(ownerUnit).closingBrace(ownerStart, ownerEnd);
      if (ownerStart < 0 || delimiter.isEmpty()) {
        return false;
      }
      var insertion = delimiter.orElseThrow().start();
      edits.add(new SourceEdit(ownerUnit.getSourceFile(), insertion, insertion, method));
    } else {
      var binary = binaryInput(owner);
      if (binary.isEmpty()) {
        return false;
      }
      var input = binary.orElseThrow();
      byte[] adapted;
      try (var stream = input.openInputStream()) {
        adapted = adapt.apply(stream.readAllBytes());
      }
      adaptations.add(new Replacement(originalElements.getBinaryName(owner).toString(), input.toUri(), adapted));
    }
    return true;
  }

  private ConstructorCorrelation attributeWitness(TreePath path, NewClassTree creation, ExecutableElement selected, TypeElement owner,
      DeclaredType created, String name, boolean expanded, List<SourceEdit> edits, JavaFileManager files,
      DiagnosticCollector<JavaFileObject> diagnostics, Optional<ConstructorReceiverProjection> projection) throws IOException {
    var task = (JavacTask) compiler.getTask(new StringWriter(), files, diagnostics, invocation.compilerOptions(), null,
        editedSources(edits));
    var units = new ArrayList<CompilationUnitTree>();
    task.parse().forEach(units::add);
    task.analyze();
    var errors = diagnostics.getDiagnostics().stream().filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
    if (!errors.isEmpty()) {
      return !expanded && onlyInapplicabilityErrors(errors, edits.getFirst(), edits)
          ? ConstructorCorrelation.inapplicable()
          : ConstructorCorrelation.rejected();
    }
    var trees = Trees.instance(task);
    var resolver = new SemanticResolver(trees, task.getTypes(), task.getElements(),
        new DeclarationIdentityRegistry(trees, task.getTypes(), task.getElements(), policy));
    var calls = witnessCalls(units, trees, task.getElements(), originalElements.getBinaryName(owner).toString(), name);
    if (calls.size() != SELECTED_CALL_COUNT) {
      return ConstructorCorrelation.unavailable();
    }
    var call = calls.getFirst();
    var returned = trees.getTypeMirror(call);
    var correspondence = new ConstructorTypeCorrespondence(originalElements, task.getElements());
    if (projection.isPresent()) {
      var receiver = projection.orElseThrow();
      var receivers = witnessCalls(units, trees, task.getElements(), originalElements.getBinaryName(receiver.owner()).toString(),
          receiver.name());
      if (receivers.size() != SELECTED_CALL_COUNT) {
        return ConstructorCorrelation.unavailable();
      }
      var receiverPath = receivers.getFirst();
      var receiverCall = (MethodInvocationTree) receiverPath.getLeaf();
      var receiverCorrespondence = new ConstructorTypeCorrespondence(originalElements, task.getElements());
      var projectedSource = sourceText(path.getCompilationUnit(), creation.getEnclosingExpression(),
          path.getCompilationUnit().getSourceFile().getCharContent(true).toString(),
          edits.stream().filter(edit -> edit.start() == edit.end()).toList());
      if (!(receiverCall.getMethodSelect() instanceof MemberSelectTree select)
          || !(select.getExpression() instanceof ParenthesizedTree expression)
          || !receiverCorrespondence.matchesCapturedReceiver(new TreePath(path, creation.getEnclosingExpression()), originalTrees,
              originalTypes, new TreePath(new TreePath(new TreePath(receiverPath, select), expression), expression.getExpression()), trees,
              task.getTypes(), projectedSource)) {
        return ConstructorCorrelation.unavailable();
      }
      correspondence.retainDeclarations(receiverCorrespondence);
    }
    if (!(call.getLeaf() instanceof MethodInvocationTree method)
        || !(trees.getElement(call) instanceof ExecutableElement witnessDeclaration)
        || !(trees.getTypeMirror(new TreePath(call, method.getMethodSelect())) instanceof ExecutableType executable)
        || !correspondence.matchesJavaType(created, returned)) {
      return ConstructorCorrelation.unavailable();
    }
    var arguments = new ArrayList<TypeMirror>();
    var originalArguments = new ArrayList<JavaTypeIdentity>();
    var prefix = selected.getParameters().size() - 1;
    if (method.getArguments().size() != creation.getArguments().size() + (expanded ? 1 : 0)) {
      return ConstructorCorrelation.unavailable();
    }
    var witnessArgumentPaths = method.getArguments().stream().map(argument -> new TreePath(call, argument)).toList();
    var originalArgumentPaths = creation.getArguments().stream().map(argument -> new TreePath(path, argument)).toList();
    for (var index = 0; index < creation.getArguments().size(); index++) {
      var witnessIndex = expanded && index >= prefix ? index + 1 : index;
      var witnessArgumentPath = witnessArgumentPaths.get(witnessIndex);
      var originalArgumentPath = originalArgumentPaths.get(index);
      var argument = trees.getTypeMirror(witnessArgumentPath);
      var originalArgument = originalTrees.getTypeMirror(originalArgumentPath);
      if (argument == null || originalArgument == null
          || !correspondence.matchesArgument(originalArgumentPath, originalTrees, witnessArgumentPath, trees)) {
        return ConstructorCorrelation.unavailable();
      }
      arguments.add(argument);
      originalArguments.add(originalSemantics.typeIdentity(originalArgument));
    }
    var selection = expanded
        ? expandedSelection(resolver, task.getTypes(), executable, selected.getParameters().size(), arguments)
        : new JavaInvocationApplicability(resolver).select(executable, false, arguments);
    var facts = selection
        .filter(value -> value.argumentTargets().stream()
            .noneMatch(target -> ConstructorTypeCorrespondence.retainsWitnessParameter(target, witnessDeclaration))
            && value.variableArray().stream()
                .noneMatch(array -> ConstructorTypeCorrespondence.retainsWitnessParameter(array, witnessDeclaration)))
        .flatMap(value -> copyFacts(resolver, value, arguments, originalArguments,
            type -> selectedTypeIdentity(resolver, correspondence, task.getTypes(), type)));
    return facts.map(ConstructorCorrelation::selected).orElseGet(ConstructorCorrelation::unavailable);
  }

  private static Optional<JavaInvocationApplicability.Selection> expandedSelection(SemanticResolver resolver, Types types,
      ExecutableType executable, int formalCount, List<TypeMirror> arguments) {
    var formals = executable.getParameterTypes();
    var prefix = formalCount - 1;
    if (prefix < 0 || formals.size() != arguments.size() + 1 || !(formals.get(prefix) instanceof ArrayType array)) {
      return Optional.empty();
    }
    var applicability = new JavaInvocationApplicability(resolver);
    var targets = new ArrayList<TypeMirror>();
    for (var index = 0; index < arguments.size(); index++) {
      var target = formals.get(index < prefix ? index : index + 1);
      if ((index >= prefix && !types.isSameType(target, array.getComponentType()))
          || !applicability.looseConversion(arguments.get(index), target)) {
        return Optional.empty();
      }
      targets.add(target);
    }
    return Optional.of(
        new JavaInvocationApplicability.Selection(JavaInvocationApplicability.Phase.VARIABLE, formalCount, targets, Optional.of(array)));
  }

  private static boolean onlyInapplicabilityErrors(List<Diagnostic<? extends JavaFileObject>> errors, SourceEdit replacement,
      List<SourceEdit> edits) {
    var shift = edits.stream().filter(edit -> edit.input().toUri().equals(replacement.input().toUri()) && edit.end() <= replacement.start())
        .mapToInt(edit -> edit.replacement().length() - edit.end() + edit.start()).sum();
    var start = replacement.start() + shift;
    var end = start + replacement.replacement().length();
    return errors.stream()
        .allMatch(error -> error.getSource() != null && error.getSource().toUri().equals(replacement.input().toUri())
            && ("compiler.err.cant.apply.symbol".equals(error.getCode()) || "compiler.err.cant.apply.symbols".equals(error.getCode()))
            && error.getPosition() >= start && error.getPosition() < end);
  }

  private static TypeMirror childType(Trees trees, TreePath parent, Tree child) {
    return trees.getTypeMirror(new TreePath(parent, child));
  }

  private List<JavaFileObject> editedSources(List<SourceEdit> edits) throws IOException {
    var inputs = new ArrayList<JavaFileObject>();
    for (var input : sources.compilationUnits()) {
      inputs.add(editedSource(input, edits));
    }
    return inputs;
  }

  private static JavaFileObject editedSource(JavaFileObject input, List<SourceEdit> edits) throws IOException {
    var content = new StringBuilder(input.getCharContent(true));
    var matching = edits.stream().filter(edit -> edit.input().toUri().equals(input.toUri()))
        .filter(edit -> edit.start() != edit.end() || edits.stream()
            .noneMatch(parent -> parent.input().toUri().equals(input.toUri()) && parent.start() != parent.end()
                && parent.start() <= edit.start() && edit.end() <= parent.end()))
        .sorted(Comparator.comparingInt(SourceEdit::start).reversed()).toList();
    for (var edit : matching) {
      content.replace(edit.start(), edit.end(), edit.replacement());
    }
    return SimpleJavaFileObject.forSource(input.toUri(), content.toString());
  }

  private String replacement(CompilationUnitTree unit, NewClassTree creation, DeclaredType created, String content, String name,
      boolean diamond, boolean expanded, int prefix, Optional<ConstructorReceiverProjection> projection, List<SourceEdit> edits) {
    var renderer = new ConstructorTypeRenderer(originalElements, Map.of());
    String qualifier;
    if (projection.isPresent()) {
      var memberArguments = creation.getIdentifier() instanceof ParameterizedTypeTree parameterized
          && !parameterized.getTypeArguments().isEmpty()
              ? parameterized.getTypeArguments().stream().map(argument -> sourceText(unit, argument, content, edits))
                  .collect(Collectors.joining(",", "<", ">"))
              : !diamond && !created.getTypeArguments().isEmpty()
                  ? created.getTypeArguments().stream().map(renderer::source).collect(Collectors.joining(",", "<", ">"))
                  : "";
      qualifier = "(" + sourceText(unit, creation.getEnclosingExpression(), content, edits) + ")." + memberArguments
          + projection.orElseThrow().name() + "()";
    } else {
      qualifier = "((" + (diamond ? renderer.diamondReceiver(created) : renderer.source(created)) + ")null)";
    }
    var arguments = new ArrayList<>(creation.getArguments().stream().map(argument -> sourceText(unit, argument, content, edits)).toList());
    if (expanded) {
      arguments.add(prefix, "null");
    }
    var explicit = creation.getTypeArguments().isEmpty()
        ? ""
        : creation.getTypeArguments().stream().map(argument -> sourceText(unit, argument, content, edits))
            .collect(Collectors.joining(",", "<", ">"));
    return qualifier + "." + explicit + name + "(" + String.join(",", arguments) + ")";
  }

  private String sourceText(CompilationUnitTree unit, Tree tree, String content, List<SourceEdit> declarations) {
    var start = position(unit, tree, false);
    var end = position(unit, tree, true);
    var copied = new StringBuilder(content.substring(start, end));
    var contained = declarations.stream()
        .filter(edit -> edit.input().toUri().equals(unit.getSourceFile().toUri()) && start <= edit.start() && edit.end() <= end)
        .sorted(Comparator.comparingInt(SourceEdit::start).reversed()).toList();
    for (var edit : contained) {
      copied.replace(edit.start() - start, edit.end() - start, edit.replacement());
    }
    return copied.toString();
  }

  private int position(CompilationUnitTree unit, Tree tree, boolean end) {
    var positions = originalTrees.getSourcePositions();
    var value = end ? positions.getEndPosition(unit, tree) : positions.getStartPosition(unit, tree);
    return Math.toIntExact(value);
  }

  private Optional<JavaFileObject> binaryInput(TypeElement owner) throws IOException {
    var name = originalElements.getBinaryName(owner).toString();
    var module = originalElements.getModuleOf(owner);
    if (!module.isUnnamed()) {
      var moduleName = module.getQualifiedName().toString();
      for (var location : List.of(StandardLocation.MODULE_PATH, StandardLocation.SYSTEM_MODULES)) {
        if (manager.hasLocation(location)) {
          var moduleLocation = manager.getLocationForModule(location, moduleName);
          if (moduleLocation != null) {
            var input = manager.getJavaFileForInput(moduleLocation, name, JavaFileObject.Kind.CLASS);
            if (input != null) {
              return Optional.of(input);
            }
          }
        }
      }
    }
    return Optional.ofNullable(manager.getJavaFileForInput(StandardLocation.CLASS_PATH, name, JavaFileObject.Kind.CLASS));
  }

  private static List<TreePath> witnessCalls(List<CompilationUnitTree> units, Trees trees, Elements elements, String expectedOwner,
      String name) {
    var calls = new ArrayList<TreePath>();
    var scanner = new TreePathScanner<Void, Void>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree method, Void ignored) {
        var path = getCurrentPath();
        if (trees.getElement(path) instanceof ExecutableElement selected && selected.getSimpleName().contentEquals(name)
            && selected.getEnclosingElement() instanceof TypeElement selectedOwner
            && elements.getBinaryName(selectedOwner).contentEquals(expectedOwner)) {
          calls.add(path);
        }
        return super.visitMethodInvocation(method, null);
      }
    };
    units.forEach(unit -> scanner.scan(unit, null));
    return calls;
  }

  private static Optional<SelectedConstructorFacts> copyFacts(SemanticResolver resolver, JavaInvocationApplicability.Selection selection,
      List<TypeMirror> arguments, List<JavaTypeIdentity> originalArguments,
      Function<TypeMirror, Optional<JavaTypeIdentity>> selectedIdentity) {
    var facts = new ArrayList<ArgumentFacts>();
    for (var index = 0; index < arguments.size(); index++) {
      var source = arguments.get(index);
      var target = selection.argumentTargets().get(index);
      var identity = selectedIdentity.apply(target);
      if (identity.isEmpty()) {
        return Optional.empty();
      }
      var preBox = Optional.<JavaTypeIdentity>empty();
      var boxed = Optional.<JavaTypeIdentity>empty();
      var unboxed = Optional.<JavaTypeIdentity>empty();
      var effective = Optional.<JavaTypeIdentity>empty();
      if (source instanceof PrimitiveType primitive && !target.getKind().isPrimitive()) {
        var wrapper = resolver.boxedType(primitive);
        if (!resolver.assignable(wrapper, target)) {
          return Optional.empty();
        }
        preBox = Optional.of(resolver.typeIdentity(primitive));
        boxed = Optional.of(resolver.typeIdentity(wrapper));
      } else if (!source.getKind().isPrimitive() && target.getKind().isPrimitive()) {
        var primitive = resolver.unboxedType(source);
        if (primitive.isEmpty() || !resolver.assignable(primitive.orElseThrow(), target)) {
          return Optional.empty();
        }
        unboxed = Optional.of(resolver.typeIdentity(primitive.orElseThrow()));
        effective = Optional.of(resolver.typeIdentity(target));
      }
      facts.add(argumentFacts(originalArguments.get(index), identity.orElseThrow(), unboxed, effective, preBox, boxed));
    }
    var array = selection.variableArray();
    var arrayIdentity = array.flatMap(selectedIdentity);
    var elementIdentity = array.flatMap(value -> selectedIdentity.apply(value.getComponentType()));
    if (array.isPresent() && (arrayIdentity.isEmpty() || elementIdentity.isEmpty())) {
      return Optional.empty();
    }
    return Optional.of(new SelectedConstructorFacts(selection.mode(), selection.formalCount(), facts, arrayIdentity,
        array.map(value -> resolver.typeIdentity(resolver.erasedType(value))), elementIdentity));
  }

  private static ArgumentFacts argumentFacts(JavaTypeIdentity source, JavaTypeIdentity target, Optional<JavaTypeIdentity> unboxed,
      Optional<JavaTypeIdentity> effective, Optional<JavaTypeIdentity> preBox, Optional<JavaTypeIdentity> boxed) {
    return new ArgumentFacts(source, target, unboxed, effective, preBox, boxed);
  }

  private Optional<JavaTypeIdentity> selectedTypeIdentity(SemanticResolver resolver, ConstructorTypeCorrespondence correspondence,
      Types witnessTypes, TypeMirror type) {
    var original = correspondence.originalType(type, originalTypes, witnessTypes);
    if (original.isPresent()) {
      return original.map(originalSemantics::typeIdentity);
    }
    return ConstructorTypeCorrespondence.containsVariable(type) ? Optional.empty() : Optional.of(resolver.typeIdentity(type));
  }

  private String uniqueName(TypeElement owner, String prefix) {
    var names = originalElements.getAllMembers(owner).stream().map(element -> element.getSimpleName().toString()).toList();
    var name = prefix;
    for (var index = 0; names.contains(name); index++) {
      name = prefix + index;
    }
    return name;
  }

  record SelectedConstructorFacts(InvocationApplicabilityMode mode, int formalCount, List<ArgumentFacts> arguments,
      Optional<JavaTypeIdentity> varargArrayType, Optional<JavaTypeIdentity> erasedArrayType, Optional<JavaTypeIdentity> elementType) {
    SelectedConstructorFacts {
      Objects.requireNonNull(mode, "mode");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(varargArrayType, "varargArrayType");
      Objects.requireNonNull(erasedArrayType, "erasedArrayType");
      Objects.requireNonNull(elementType, "elementType");
    }
  }

  record ArgumentFacts(JavaTypeIdentity sourceType, JavaTypeIdentity selectedTarget, Optional<JavaTypeIdentity> unboxedType,
      Optional<JavaTypeIdentity> effectivePrimitiveType, Optional<JavaTypeIdentity> preBoxPrimitiveType,
      Optional<JavaTypeIdentity> boxedType) {
    ArgumentFacts {
      Objects.requireNonNull(sourceType, "sourceType");
      Objects.requireNonNull(selectedTarget, "selectedTarget");
      Objects.requireNonNull(unboxedType, "unboxedType");
      Objects.requireNonNull(effectivePrimitiveType, "effectivePrimitiveType");
      Objects.requireNonNull(preBoxPrimitiveType, "preBoxPrimitiveType");
      Objects.requireNonNull(boxedType, "boxedType");
    }
  }

  private record SourceEdit(JavaFileObject input, int start, int end, String replacement) {
  }

  enum CorrelationStatus {
    SELECTED, INAPPLICABLE, UNAVAILABLE, WITNESS_REJECTED
  }

  record ConstructorCorrelation(CorrelationStatus status, Optional<SelectedConstructorFacts> facts) {
    ConstructorCorrelation {
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(facts, "facts");
      if ((status == CorrelationStatus.SELECTED) != facts.isPresent()) {
        throw new IllegalArgumentException("Only a selected witness may retain constructor facts");
      }
    }

    static ConstructorCorrelation selected(SelectedConstructorFacts facts) {
      return new ConstructorCorrelation(CorrelationStatus.SELECTED, Optional.of(facts));
    }

    static ConstructorCorrelation inapplicable() {
      return new ConstructorCorrelation(CorrelationStatus.INAPPLICABLE, Optional.empty());
    }

    static ConstructorCorrelation rejected() {
      return new ConstructorCorrelation(CorrelationStatus.WITNESS_REJECTED, Optional.empty());
    }

    static ConstructorCorrelation unavailable() {
      return new ConstructorCorrelation(CorrelationStatus.UNAVAILABLE, Optional.empty());
    }
  }
}
