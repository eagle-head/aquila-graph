package org.aquilagraph.frontend;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.MethodInvocationTree;
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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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
  private final ConstructorIdentityLookup<Tree, Optional<SelectedConstructorFacts>> selections = ConstructorIdentityLookup
      .usingReferenceIdentity();

  JavaConstructorSelection(JavaCompiler compiler, JavaCompilerInvocation invocation, SnapshotSourceCatalog sources,
      StandardJavaFileManager manager, Trees trees, Types types, Elements elements, JavaTranslationPolicy policy) {
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.invocation = Objects.requireNonNull(invocation, "invocation");
    this.sources = Objects.requireNonNull(sources, "sources");
    this.manager = Objects.requireNonNull(manager, "manager");
    originalTrees = Objects.requireNonNull(trees, "trees");
    originalTypes = Objects.requireNonNull(types, "types");
    originalElements = Objects.requireNonNull(elements, "elements");
    this.policy = Objects.requireNonNull(policy, "policy");
    originalSemantics = new SemanticResolver(trees, types, elements, new DeclarationIdentityRegistry(trees, types, elements, policy));
  }

  Optional<SelectedConstructorFacts> select(TreePath path, ExecutableElement selected) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(selected, "selected");
    if (!selected.equals(originalTrees.getElement(path))) {
      throw new IllegalArgumentException("Constructor selection requires its exact original attributed element");
    }
    return selections.find(path.getLeaf()).orElseGet(Optional::empty);
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
      var fixed = creation.getArguments().size() == selected.getParameters().size()
          && !fixedArrayCannotAcceptCast(path, creation, selected, created)
              ? attribute(path, creation, selected, owner, created, false)
              : WitnessAttribution.inapplicable();
      var facts = fixed.status() == WitnessStatus.INAPPLICABLE && selected.isVarArgs()
          ? attribute(path, creation, selected, owner, created, true).facts()
          : fixed.facts();
      selections.remember(creation, facts);
    }
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

  private WitnessAttribution attribute(TreePath path, NewClassTree creation, ExecutableElement selected, TypeElement owner,
      DeclaredType created, boolean expanded) throws IOException {
    if (creation.getClassBody() != null && creation.getArguments().isEmpty()) {
      return WitnessAttribution.unavailable();
    }
    var name = uniqueName(owner);
    var diamond = creation.getClassBody() == null && creation.getIdentifier() instanceof ParameterizedTypeTree parameterized
        && parameterized.getTypeArguments().isEmpty();
    var notional = new ConstructorNotionalMethod(selected, owner, originalTypes, originalElements, name, diamond,
        creation.getArguments().size(), expanded);
    var original = path.getCompilationUnit();
    var content = original.getSourceFile().getCharContent(true).toString();
    var enumConstant = path.getParentPath() != null && originalTrees.getElement(path.getParentPath()) != null
        && originalTrees.getElement(path.getParentPath()).getKind() == ElementKind.ENUM_CONSTANT;
    if (enumConstant && creation.getArguments().isEmpty()) {
      return WitnessAttribution.unavailable();
    }
    var replaced = creation.getClassBody() == null && !enumConstant ? creation : creation.getArguments().getFirst();
    var start = position(original, replaced, false);
    var end = position(original, replaced, true);
    if (start < 0 || end < start) {
      return WitnessAttribution.unavailable();
    }
    var receiver = receiverType(path, creation, owner, created);
    if (receiver.isEmpty()) {
      return WitnessAttribution.unavailable();
    }
    var replacement = replacement(original, creation, receiver.orElseThrow(), content, name, diamond, expanded,
        selected.getParameters().size() - 1);
    if (creation.getClassBody() != null || enumConstant) {
      // Only the original argument arm completes normally; proof arguments cannot change its flow.
      replacement = "switch (0) { case 1 -> { " + replacement + "; throw null; } default -> " + sourceText(original, replaced, content)
          + "; }";
    }
    var edits = new ArrayList<SourceEdit>();
    edits.add(new SourceEdit(original.getSourceFile(), start, end, replacement));
    var adaptation = Optional.<BinaryAdaptation>empty();
    var declaration = originalTrees.getPath(owner);
    if (declaration != null && declaration.getLeaf() instanceof ClassTree classTree) {
      var ownerUnit = declaration.getCompilationUnit();
      var ownerContent = ownerUnit.getSourceFile().getCharContent(true).toString();
      var ownerEnd = position(ownerUnit, classTree, true);
      if (ownerEnd <= 0 || ownerContent.charAt(ownerEnd - 1) != '}') {
        return WitnessAttribution.unavailable();
      }
      edits.add(new SourceEdit(ownerUnit.getSourceFile(), ownerEnd - 1, ownerEnd - 1, notional.sourceMethod()));
    } else {
      var binary = binaryInput(owner);
      if (binary.isEmpty()) {
        return WitnessAttribution.unavailable();
      }
      var input = binary.orElseThrow();
      byte[] adapted;
      try (var stream = input.openInputStream()) {
        adapted = notional.adaptBinary(stream.readAllBytes());
      }
      adaptation = Optional.of(new BinaryAdaptation(input, adapted));
    }
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var witnessManager = new CompilerFileManager(Objects.requireNonNull(
        compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8), "constructor witness file manager"))) {
      if (adaptation.isPresent()) {
        var binary = adaptation.orElseThrow();
        try (var files = new ConstructorWitnessFileManager(witnessManager, originalElements.getBinaryName(owner).toString(),
            binary.input().toUri(), binary.content())) {
          return attributeWitness(path, creation, selected, owner, created, name, expanded, edits, files, diagnostics);
        }
      }
      return attributeWitness(path, creation, selected, owner, created, name, expanded, edits, witnessManager, diagnostics);
    }
  }

  private Optional<DeclaredType> receiverType(TreePath path, NewClassTree creation, TypeElement owner, DeclaredType created) {
    var enclosing = creation.getEnclosingExpression();
    if (enclosing == null) {
      return Optional.of(created);
    }
    var qualifier = new TreePath(path, enclosing);
    var candidate = declaredReceiverType(qualifier);
    if (candidate.isEmpty()) {
      return Optional.of(created);
    }
    var declared = candidate.orElseThrow();
    var correspondence = new ConstructorTypeCorrespondence(originalElements, originalElements);
    if (!correspondence.matches(originalTrees.getTypeMirror(qualifier), originalTypes.capture(declared))
        || !(originalTypes.asMemberOf(declared, owner) instanceof DeclaredType member)
        || !(member.getEnclosingType() instanceof DeclaredType containing)) {
      return Optional.empty();
    }
    return Optional.of(originalTypes.getDeclaredType(containing, owner, created.getTypeArguments().toArray(TypeMirror[]::new)));
  }

  private Optional<DeclaredType> declaredReceiverType(TreePath qualifier) {
    var expression = qualifier.getLeaf();
    if (expression instanceof ParenthesizedTree parenthesized) {
      return declaredReceiverType(new TreePath(qualifier, parenthesized.getExpression()));
    }
    if (expression instanceof AssignmentTree assignment) {
      return childType(originalTrees, qualifier, assignment.getVariable()) instanceof DeclaredType declared
          ? Optional.of(declared)
          : Optional.empty();
    }
    if (expression instanceof TypeCastTree cast) {
      return childType(originalTrees, qualifier, cast.getType()) instanceof DeclaredType declared
          ? Optional.of(declared)
          : Optional.empty();
    }
    if (expression instanceof MethodInvocationTree invocation) {
      return childType(originalTrees, qualifier, invocation.getMethodSelect()) instanceof ExecutableType executable
          && executable.getReturnType() instanceof DeclaredType declared ? Optional.of(declared) : Optional.empty();
    }
    var source = originalTrees.getElement(qualifier);
    return source instanceof VariableElement variable && variable.asType() instanceof DeclaredType declared
        ? Optional.of(declared)
        : Optional.empty();
  }

  private WitnessAttribution attributeWitness(TreePath path, NewClassTree creation, ExecutableElement selected, TypeElement owner,
      DeclaredType created, String name, boolean expanded, List<SourceEdit> edits, JavaFileManager files,
      DiagnosticCollector<JavaFileObject> diagnostics) throws IOException {
    var task = (JavacTask) compiler.getTask(new StringWriter(), files, diagnostics, invocation.compilerOptions(), null,
        editedSources(edits));
    var units = new ArrayList<CompilationUnitTree>();
    task.parse().forEach(units::add);
    task.analyze();
    var errors = diagnostics.getDiagnostics().stream().filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
    if (!errors.isEmpty()) {
      return !expanded && onlyInapplicabilityErrors(errors, edits.getFirst(), edits)
          ? WitnessAttribution.inapplicable()
          : WitnessAttribution.unavailable();
    }
    var trees = Trees.instance(task);
    var resolver = new SemanticResolver(trees, task.getTypes(), task.getElements(),
        new DeclarationIdentityRegistry(trees, task.getTypes(), task.getElements(), policy));
    var calls = witnessCalls(units, trees, task.getElements(), originalElements.getBinaryName(owner).toString(), name);
    if (calls.size() != SELECTED_CALL_COUNT) {
      return WitnessAttribution.unavailable();
    }
    var call = calls.getFirst();
    var returned = trees.getTypeMirror(call);
    var correspondence = new ConstructorTypeCorrespondence(originalElements, task.getElements());
    if (!(call.getLeaf() instanceof MethodInvocationTree method)
        || !(trees.getElement(call) instanceof ExecutableElement witnessDeclaration)
        || !(trees.getTypeMirror(new TreePath(call, method.getMethodSelect())) instanceof ExecutableType executable)
        || !correspondence.matches(created, returned)) {
      return WitnessAttribution.unavailable();
    }
    var arguments = new ArrayList<TypeMirror>();
    var originalArguments = new ArrayList<JavaTypeIdentity>();
    var prefix = selected.getParameters().size() - 1;
    if (method.getArguments().size() != creation.getArguments().size() + (expanded ? 1 : 0)) {
      return WitnessAttribution.unavailable();
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
        return WitnessAttribution.unavailable();
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
        .flatMap(value -> copyFacts(resolver, correspondence, task.getTypes(), value, arguments, originalArguments));
    return facts.map(WitnessAttribution::selected).orElseGet(WitnessAttribution::unavailable);
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
        .sorted(Comparator.comparingInt(SourceEdit::start).reversed()).toList();
    for (var edit : matching) {
      content.replace(edit.start(), edit.end(), edit.replacement());
    }
    return SimpleJavaFileObject.forSource(input.toUri(), content.toString());
  }

  private String replacement(CompilationUnitTree unit, NewClassTree creation, DeclaredType created, String content, String name,
      boolean diamond, boolean expanded, int prefix) {
    var renderer = new ConstructorTypeRenderer(originalElements, Map.of());
    var qualifier = "((" + (diamond ? renderer.diamondReceiver(created) : renderer.source(created)) + ")null)";
    var arguments = new ArrayList<>(creation.getArguments().stream().map(argument -> sourceText(unit, argument, content)).toList());
    if (expanded) {
      arguments.add(prefix, "null");
    }
    var explicit = creation.getTypeArguments().isEmpty()
        ? ""
        : creation.getTypeArguments().stream().map(argument -> sourceText(unit, argument, content))
            .collect(Collectors.joining(",", "<", ">"));
    return qualifier + "." + explicit + name + "(" + String.join(",", arguments) + ")";
  }

  private String sourceText(CompilationUnitTree unit, Tree tree, String content) {
    return content.substring(position(unit, tree, false), position(unit, tree, true));
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

  private Optional<SelectedConstructorFacts> copyFacts(SemanticResolver resolver, ConstructorTypeCorrespondence correspondence,
      Types witnessTypes, JavaInvocationApplicability.Selection selection, List<TypeMirror> arguments,
      List<JavaTypeIdentity> originalArguments) {
    var facts = new ArrayList<ArgumentFacts>();
    for (var index = 0; index < arguments.size(); index++) {
      var source = arguments.get(index);
      var target = selection.argumentTargets().get(index);
      var identity = selectedTypeIdentity(resolver, correspondence, witnessTypes, target);
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
    var arrayIdentity = array.flatMap(value -> selectedTypeIdentity(resolver, correspondence, witnessTypes, value));
    var elementIdentity = array.flatMap(value -> selectedTypeIdentity(resolver, correspondence, witnessTypes, value.getComponentType()));
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

  private String uniqueName(TypeElement owner) {
    var names = originalElements.getAllMembers(owner).stream().map(element -> element.getSimpleName().toString()).toList();
    var name = "constructorSelection";
    for (var index = 0; names.contains(name); index++) {
      name = "constructorSelection" + index;
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

  private static final class BinaryAdaptation {
    private final JavaFileObject sourceInput;
    private final byte[] binaryContent;

    BinaryAdaptation(JavaFileObject input, byte[] content) {
      sourceInput = Objects.requireNonNull(input, "input");
      binaryContent = content.clone();
    }

    JavaFileObject input() {
      return sourceInput;
    }

    byte[] content() {
      return binaryContent.clone();
    }
  }

  private enum WitnessStatus {
    SELECTED, INAPPLICABLE, UNAVAILABLE
  }

  private record WitnessAttribution(WitnessStatus status, Optional<SelectedConstructorFacts> facts) {
    WitnessAttribution {
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(facts, "facts");
      if ((status == WitnessStatus.SELECTED) != facts.isPresent()) {
        throw new IllegalArgumentException("Only a selected witness may retain constructor facts");
      }
    }

    static WitnessAttribution selected(SelectedConstructorFacts facts) {
      return new WitnessAttribution(WitnessStatus.SELECTED, Optional.of(facts));
    }

    static WitnessAttribution inapplicable() {
      return new WitnessAttribution(WitnessStatus.INAPPLICABLE, Optional.empty());
    }

    static WitnessAttribution unavailable() {
      return new WitnessAttribution(WitnessStatus.UNAVAILABLE, Optional.empty());
    }
  }
}
