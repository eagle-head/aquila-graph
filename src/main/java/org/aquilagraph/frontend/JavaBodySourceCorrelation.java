package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.aquilagraph.frontend.JavaAnnotationProjector.DeclarationSite;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxNodeKey;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.jspecify.annotations.Nullable;

/** Owns one unit's body corroboration, canonical copying, and sealed execution contributions. */
final class JavaBodySourceCorrelation {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaWrittenSourceAssociations sourceAssociations;
  private final JavaDeclarationScope declarations;
  private final JavaAnnotationProjector annotationProjector;
  private final JavaTypeUseTranslator typeUseTranslator;
  private final JavaSourceCopyCorrespondence sourceCopies;
  private final Optional<JavaBodySourceCorrelation> parent;
  private final List<TranslatedMethodBody> projectedBodies;
  private final Map<SyntaxStructuralPath, TranslatedMethodBody> bodies;
  private final List<CopiedAstValues.Scope> scopes = new ArrayList<>();
  private final Publication rootPublication;

  JavaBodySourceCorrelation(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaWrittenSourceAssociations sourceAssociations, JavaDeclarationScope declarations) {
    parent = Optional.empty();
    sourceCopies = new JavaSourceCopyCorrespondence();
    projectedBodies = new ArrayList<>();
    bodies = new HashMap<>();
    rootPublication = new Publication(Optional.empty());
    this.semantics = semantics;
    this.sourceEvidence = sourceEvidence;
    this.treeInventory = treeInventory;
    this.sourceAssociations = sourceAssociations;
    this.declarations = declarations;
    annotationProjector = new JavaAnnotationProjector(semantics, sourceEvidence, treeInventory, sourceAssociations);
    typeUseTranslator = new JavaTypeUseTranslator(semantics, sourceEvidence, treeInventory, Optional.empty(), sourceAssociations);
  }

  private JavaBodySourceCorrelation(JavaBodySourceCorrelation parent, JavaWrittenSourceAssociations sourceAssociations,
      JavaDeclarationScope declarations) {
    this.parent = Optional.of(parent);
    semantics = parent.semantics;
    sourceEvidence = parent.sourceEvidence;
    treeInventory = parent.treeInventory;
    this.sourceAssociations = sourceAssociations;
    this.declarations = declarations;
    projectedBodies = parent.projectedBodies;
    bodies = parent.bodies;
    sourceCopies = parent.sourceCopies.child();
    rootPublication = parent.rootPublication.child();
    annotationProjector = new JavaAnnotationProjector(semantics, sourceEvidence, treeInventory, sourceAssociations);
    typeUseTranslator = new JavaTypeUseTranslator(semantics, sourceEvidence, treeInventory, Optional.empty(), sourceAssociations);
  }

  JavaBodySourceCorrelation child(JavaWrittenSourceAssociations associations, JavaDeclarationScope scope) {
    return new JavaBodySourceCorrelation(this, associations, scope);
  }

  void accept() {
    var owner = parent.orElseThrow();
    sourceCopies.accept();
    rootPublication.accept();
    owner.scopes.addAll(scopes);
  }

  void refuse() {
    rootPublication.refuse();
  }

  void correlate(CompilationUnitTree unit, SourceFile sourceFile, List<TranslatedMethodBody> supplied, List<JavaType> types) {
    var methodsByKey = new HashMap<String, JavaMethod>();
    for (var type : types) {
      for (var method : type.methods()) {
        if (methodsByKey.putIfAbsent(method.methodKey(), method) != null) {
          throw new IllegalArgumentException("Duplicate canonical executable for body correlation: " + method.methodKey());
        }
      }
    }
    for (var body : supplied) {
      corroborateBody(unit, sourceFile, body, methodsByKey);
      addBody(body);
    }
  }

  void addBody(TranslatedMethodBody body) {
    if (bodies.putIfAbsent(body.root().structuralPath(), body) != null) {
      throw new IllegalArgumentException("Duplicate translated body path: " + body.root().structuralPath());
    }
    projectedBodies.add(body);
    body.projection().evidence().nodes()
        .forEach(projection -> projection.sourceAssociation().ifPresent(value -> sourceAssociations.retain(projection.node(), value)));
  }

  Optional<TranslatedMethodBody> body(SyntaxStructuralPath path) {
    return Optional.ofNullable(bodies.get(path));
  }

  boolean hasBody(String key) {
    return projectedBodies.stream().anyMatch(body -> body.methodKey().equals(key));
  }

  Publication publication() {
    return rootPublication;
  }

  void retainScope(CopiedAstValues.Scope scope) {
    scopes.add(scope);
  }

  List<CopiedAstValues.Scope> sealedScopes() {
    return scopes.stream().map(this::sealScope).toList();
  }

  void retainCopy(SyntaxNode original, SyntaxNode copied) {
    sourceCopies.retain(original, copied);
  }

  JavaBodyDecisions rebind(JavaBodyDecisions decisions, SyntaxNode original, SyntaxNode copied) {
    return decisions.rebind(sourceCopies.scopeCopies(original, true), copied);
  }

  List<SyntaxSemanticEdge> declarationEdges(JavaDeclarationScope scope) {
    return scope.rebind(sourceCopies);
  }

  List<CopiedAstValues.Body> registrations(SourceFile sourceFile) {
    var registrations = new ArrayList<CopiedAstValues.Body>();
    for (var body : projectedBodies) {
      if (!body.root().sourceFileKey().equals(sourceFile.sourceFileKey())) {
        continue;
      }
      var published = rootPublication.published(body.methodKey());
      if (published != null) {
        published = sourceCopies.finalCopy(published);
      }
      if (published != null) {
        var decisions = rebind(body.decisions(), body.root(), published);
        registrations.add(
            new CopiedAstValues.Body(body.methodKey(), published, MethodBodyOrigin.SOURCE_AST, decisions.uses(), decisions.invocations(),
                decisions.staticAccesses(), decisions.concatenations(), decisions.constructors(), attributions(body, true)));
        continue;
      }
      var origin = rootPublication.isUnavailable(body.methodKey())
          ? MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION
          : MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY;
      registrations.add(new CopiedAstValues.Body(body.methodKey(), body.root(), origin, body.decisions().uses(),
          body.decisions().invocations(), body.decisions().staticAccesses(), body.decisions().concatenations(),
          body.decisions().constructors(), attributions(body, false)));
    }
    return List.copyOf(registrations);
  }

  /** Stages publication at an explicit lexical parent until its declaration is complete. */
  static final class Publication {
    private final Optional<Publication> parent;
    private final Map<String, SyntaxNode> publishedBodies = new HashMap<>();
    private final List<String> unavailableBodies = new ArrayList<>();

    private Publication(Optional<Publication> parent) {
      this.parent = parent;
    }

    Publication child() {
      return new Publication(Optional.of(this));
    }

    void publish(String key, SyntaxNode node) {
      publishedBodies.put(key, node);
    }

    void unavailable(String key) {
      unavailableBodies.add(key);
    }

    private @Nullable SyntaxNode published(String key) {
      return publishedBodies.get(key);
    }

    private boolean isUnavailable(String key) {
      return unavailableBodies.contains(key);
    }

    void accept() {
      var owner = parent.orElseThrow();
      owner.publishedBodies.putAll(publishedBodies);
      owner.unavailableBodies.addAll(unavailableBodies);
    }

    void refuse() {
      parent.orElseThrow().unavailableBodies.addAll(unavailableBodies);
    }
  }

  private void corroborateBody(CompilationUnitTree unit, SourceFile sourceFile, TranslatedMethodBody body,
      Map<String, JavaMethod> methodsByKey) {
    var projection = body.projection();
    var declaration = projection.declaration();
    var methodPath = TreePath.getPath(unit, declaration);
    if (methodPath == null || !JavaProjectionIdentity.sameInstance(methodPath.getLeaf(), declaration) || declaration.getBody() == null
        || !(semantics.element(methodPath) instanceof ExecutableElement executable)
        || !JavaProjectionIdentity.sameInstance(semantics.sourceTree(executable), declaration)) {
      throw new IllegalArgumentException("Translated body has no independently attributed source executable: " + body.methodKey());
    }
    var ownerPath = methodPath.getParentPath();
    var enclosingElement = executable.getEnclosingElement();
    if (ownerPath == null || !(ownerPath.getLeaf() instanceof ClassTree) || !(semantics.element(ownerPath) instanceof TypeElement owner)
        || enclosingElement == null || !enclosingElement.equals(owner)
        || !JavaProjectionIdentity.sameInstance(semantics.sourceTree(owner), ownerPath.getLeaf())) {
      throw new IllegalArgumentException("Translated body has no independently attributed source owner: " + body.methodKey());
    }
    var key = semantics.declarationKey(executable).orElseThrow();
    var canonical = methodsByKey.get(key);
    if (!key.equals(body.methodKey()) || canonical == null || !canonical.hasBody()
        || !semantics.declarationKey(owner).equals(Optional.of(canonical.ownerTypeKey()))) {
      throw new IllegalArgumentException("Translated body does not match its canonical executable: " + body.methodKey());
    }
    var independentDeclaration = new JavaMethodDeclarationTranslator(semantics, sourceEvidence, treeInventory)
        .translate(unit, ownerPath, sourceFile, canonical.ownerTypeKey(), declaration).value();
    if (!canonical.equals(independentDeclaration)) {
      throw new IllegalArgumentException(
          "Translated body does not match its complete canonical executable declaration: " + body.methodKey());
    }
    var independentBodyPath = new TreePath(methodPath, declaration.getBody());
    if (!sameTreePath(independentBodyPath, projection.bodyPath())
        || !JavaProjectionIdentity.sameInstance(body.root(), projection.evidence().translation().value())
        || !body.root().sourceFileKey().equals(sourceFile.sourceFileKey())
        || !body.root().structuralPath().equals(JavaSyntaxStructuralPath.executionRoot(independentBodyPath))) {
      throw new IllegalArgumentException("Translated body does not retain its executable body projection: " + body.methodKey());
    }
    projection.evidence().requireSemantics(semantics);
    new JavaBodyDecisionProducer(semantics).corroborate(body.root(), projection.evidence().nodes());
  }

  private static boolean sameTreePath(TreePath first, TreePath second) {
    @Nullable
    TreePath firstCursor = first;
    @Nullable
    TreePath secondCursor = second;
    while (firstCursor != null && secondCursor != null) {
      if (!JavaProjectionIdentity.sameInstance(firstCursor.getLeaf(), secondCursor.getLeaf())) {
        return false;
      }
      firstCursor = firstCursor.getParentPath();
      secondCursor = secondCursor.getParentPath();
    }
    return firstCursor == null && secondCursor == null;
  }

  private CopiedAstValues.Scope sealScope(CopiedAstValues.Scope scope) {
    var copied = sourceCopies.finalCopy(scope.root());
    var nodes = sourceCopies.scopeCopies(scope.root(), false);
    var decisions = new JavaBodyDecisions(scope.uses(), scope.invocations(), scope.staticAccesses(), scope.concatenations(), List.of())
        .rebind(nodes, copied);
    var attributions = scope.attributedOccurrences().stream()
        .map(occurrence -> new AttributedBodyOccurrence(occurrence.scopeKey(),
            Objects.requireNonNull(nodes.get(occurrence.nodeKey()), "canonical scope occurrence").nodeId(), occurrence.sourceFileKey(),
            occurrence.sourceKind(), occurrence.compilerPath()))
        .toList();
    return new CopiedAstValues.Scope(scope.scope(), copied, decisions.uses(), decisions.invocations(), decisions.staticAccesses(),
        decisions.concatenations(), attributions);
  }

  Optional<SyntaxNode> projectExecutable(JavaExecutableSyntaxProjector.Projection projection, SyntaxStructuralPath path,
      DiagnosticEmissionCollector diagnostics) {
    projection.requireSemantics(semantics);
    diagnostics.include(projection.translation().emissions());
    var paths = new HashMap<String, TreePath>();
    projection.nodes().forEach(binding -> {
      paths.put(binding.node().nodeId(), binding.path());
      binding.sourceAssociation().ifPresent(value -> sourceAssociations.retain(binding.node(), value));
    });
    return project(projection.translation().value(), path, false, true,
        new BodySourceContext(projection.unit(), projection.source(), paths, declarations, diagnostics));
  }

  List<AttributedBodyOccurrence> executionAttributions(JavaExecutableSyntaxProjector.Projection projection) {
    projection.requireSemantics(semantics);
    return projection.nodes().stream().map(binding -> {
      var node = sourceCopies.finalCopy(binding.node());
      return new AttributedBodyOccurrence(projection.scopeKey(), node.nodeId(), node.sourceFileKey(), node.sourceKind(),
          binding.node().structuralPath());
    }).toList();
  }

  Optional<SyntaxNode> sourceBody(TranslatedMethodBody body, SyntaxStructuralPath path, TreePath methodPath, MethodTree declaration,
      CompilationUnitTree unit, SourceFile sourceFile, DiagnosticEmissionCollector diagnostics) {
    var statements = declaration.getBody().getStatements();
    var bodyPath = new TreePath(methodPath, declaration.getBody());
    var omitInsertedCall = !statements.isEmpty()
        && treeInventory.isCompilerInsertedSuperCall(new TreePath(bodyPath, statements.getFirst()));
    var paths = new HashMap<String, TreePath>();
    body.projection().evidence().nodes().forEach(projection -> paths.put(projection.node().nodeId(), projection.path()));
    var context = new BodySourceContext(unit, sourceFile, paths, declarations, diagnostics);
    return project(body.root(), path, omitInsertedCall, true, context);
  }

  private Optional<SyntaxNode> project(SyntaxNode original, SyntaxStructuralPath path, boolean omitInsertedCall, boolean bodyRoot,
      BodySourceContext context) {
    if (original.syntaxKind() == SyntaxKind.TYPE_DECLARATION) {
      return Optional.of(copyDeclaration(original, path));
    }
    var range = original.origin();
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var positions = new EnumMap<SyntaxRole, Integer>(SyntaxRole.class);
    var unchanged = original.structuralPath().equals(path);
    for (var child : original.children()) {
      if (bodyRoot && omitInsertedCall && child.role() == SyntaxRole.STATEMENT && child.position() == 0) {
        unchanged = false;
        continue;
      }
      var position = positions.getOrDefault(child.role(), 0);
      var projected = project(child.node(), path.child(child.role(), position), omitInsertedCall, false, context);
      if (projected.isEmpty()) {
        return Optional.empty();
      }
      var node = projected.orElseThrow();
      children.add(new SyntaxChild(child.role(), position, node));
      positions.put(child.role(), position + 1);
      unchanged &= child.position() == position && JavaProjectionIdentity.sameInstance(node, child.node());
    }
    var creatingPath = Objects.requireNonNull(context.paths().get(original.nodeId()), "body creating path");
    var previousSize = children.size();
    if (!appendBodySourceAttachments(original, creatingPath, path, children, context)) {
      return Optional.empty();
    }
    unchanged &= children.size() == previousSize;
    if (unchanged) {
      sourceCopies.retain(original, original);
      return Optional.of(original);
    }
    var value = original.value();
    if (value instanceof SyntaxValue.LocalDeclaration local && creatingPath.getLeaf() instanceof VariableTree variable) {
      var binding = local.bindingEvidence().orElseThrow();
      var annotations = children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION).count();
      var shared = context.declarations().localAnnotations(localHeaderKey(context, variable));
      var sharedKeys = variable.getModifiers().getAnnotations().stream()
          .flatMap(annotation -> sourceEvidence.origin(context.unit(), context.sourceFile(), annotation).stream()).map(shared::find)
          .filter(Objects::nonNull).filter(key -> children.stream().noneMatch(child -> child.node().nodeId().equals(key))).toList();
      value = new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers(),
          Optional.of(new LocalBindingEvidence(binding.declarationKey(), binding.typeForm(), binding.context(), binding.initialized(),
              Math.toIntExact(annotations), sharedKeys)));
    }
    var copied = sourceAssociations.copied(original, SyntaxNode.source(original.sourceFileKey(), path, original.sourceKind(),
        original.syntaxKind(), original.type(), value, children, original.semanticReference(), range.orElseThrow()));
    sourceCopies.retain(original, copied);
    return Optional.of(copied);
  }

  private SyntaxNode copyDeclaration(SyntaxNode original, SyntaxStructuralPath path) {
    var keys = new HashMap<String, String>();
    declarationCopyKeys(original, path, keys);
    return copyDeclaration(original, path, keys);
  }

  private SyntaxNode copyDeclaration(SyntaxNode original, SyntaxStructuralPath path, Map<String, String> keys) {
    var children = original.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        copyDeclaration(child.node(), path.child(child.role(), child.position()), keys))).toList();
    var copy = sourceAssociations.copied(original,
        SyntaxNode.source(original.sourceFileKey(), path, original.sourceKind(), original.syntaxKind(), original.type(),
            declarationCopyValue(original.value(), keys), children, original.semanticReference(), original.origin().orElseThrow()));
    sourceCopies.retain(original, copy);
    return copy;
  }

  private void declarationCopyKeys(SyntaxNode original, SyntaxStructuralPath path, Map<String, String> keys) {
    keys.put(original.nodeId(), SyntaxNodeKey.from(original.sourceFileKey(), path, original.sourceKind()).value());
    original.children().forEach(child -> declarationCopyKeys(child.node(), path.child(child.role(), child.position()), keys));
  }

  private static SyntaxValue declarationCopyValue(SyntaxValue value, Map<String, String> keys) {
    if (value instanceof SyntaxValue.SharedTypeAnnotations shared) {
      return new SyntaxValue.SharedTypeAnnotations(shared.annotationNodeKeys().stream().map(key -> keys.getOrDefault(key, key)).toList());
    }
    if (!(value instanceof SyntaxValue.LocalDeclaration local) || local.bindingEvidence().isEmpty()) {
      return value;
    }
    var binding = local.bindingEvidence().orElseThrow();
    var annotations = binding.sharedAnnotationKeys().stream().map(key -> keys.getOrDefault(key, key)).toList();
    return new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers(),
        Optional.of(new LocalBindingEvidence(binding.declarationKey(), binding.typeForm(), binding.context(), binding.initialized(),
            binding.annotations(), annotations)));
  }

  private boolean appendBodySourceAttachments(SyntaxNode original, TreePath creatingPath, SyntaxStructuralPath path,
      List<SyntaxChild> children, BodySourceContext context) {
    var complete = true;
    switch (creatingPath.getLeaf()) {
      case NewArrayTree array -> {
        if (array.getType() != null) {
          complete = appendBodyType(array.getType(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        }
        var annotations = new ArrayList<AnnotationTree>();
        annotations.addAll(array.getAnnotations());
        array.getDimAnnotations().forEach(annotations::addAll);
        var projector = new JavaAnnotationProjector(semantics, sourceEvidence, treeInventory, sourceAssociations);
        for (var ordinal = 0; ordinal < annotations.size(); ordinal++) {
          var annotationPath = path.child(SyntaxRole.ANNOTATION, ordinal);
          var annotation = projector.typeUse(context.unit(), context.sourceFile(), annotations.get(ordinal), annotationPath, annotationPath,
              context.diagnostics()).value();
          if (annotation.isPresent()) {
            children.add(new SyntaxChild(SyntaxRole.ANNOTATION, ordinal, annotation.orElseThrow()));
          }
          complete &= annotation.isPresent();
        }
      }
      case NewClassTree creation -> {
        complete = appendBodyType(creation.getIdentifier(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        for (var index = 0; index < creation.getTypeArguments().size(); index++) {
          complete &= appendBodyType(creation.getTypeArguments().get(index), SyntaxRole.TYPE_ARGUMENT, index, path, children, context);
        }
      }
      case MemberReferenceTree reference -> {
        var form = original.value(SyntaxValue.MemberReference.class).qualifier();
        if (form == SyntaxValue.ReferenceQualifier.TYPE || form == SyntaxValue.ReferenceQualifier.ARRAY_TYPE) {
          complete = appendBodyType(reference.getQualifierExpression(), SyntaxRole.QUALIFIER, 0, path, children, context);
        }
        var arguments = reference.getTypeArguments();
        if (arguments != null) {
          for (var index = 0; index < arguments.size(); index++) {
            complete &= appendBodyType(arguments.get(index), SyntaxRole.TYPE_ARGUMENT, index, path, children, context);
          }
        }
        children.sort(Comparator.comparingInt(child -> child.role() == SyntaxRole.QUALIFIER ? 0 : 1));
      }
      case MethodInvocationTree invocation -> {
        for (var index = 0; index < invocation.getTypeArguments().size(); index++) {
          complete &= appendBodyType(invocation.getTypeArguments().get(index), SyntaxRole.TYPE_ARGUMENT, index, path, children, context);
        }
        if (original.value(SyntaxValue.Invocation.class).form() == InvocationForm.STATIC_TYPE
            && invocation.getMethodSelect() instanceof MemberSelectTree selection) {
          complete &= appendBodyType(selection.getExpression(), SyntaxRole.QUALIFIER, 0, path, children, context);
        }
      }
      case MemberSelectTree selection -> {
        if (original.syntaxKind() == SyntaxKind.CLASS_LITERAL) {
          complete = appendBodyType(selection.getExpression(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        } else if ((original.syntaxKind() == SyntaxKind.FIELD_ACCESS
            && original.value(SyntaxValue.FieldAccess.class).receiver() == SyntaxValue.FieldReceiver.STATIC_TYPE)
            || original.syntaxKind() == SyntaxKind.RECEIVER) {
          complete = appendBodyType(selection.getExpression(), SyntaxRole.QUALIFIER, 0, path, children, context);
        }
      }
      case TypeCastTree cast -> complete = appendBodyType(cast.getType(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
      case InstanceOfTree test -> {
        if (test.getPattern() == null) {
          complete = appendBodyType(test.getType(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        }
      }
      case DeconstructionPatternTree pattern -> {
        complete = appendBodyType(pattern.getDeconstructor(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        children.sort(Comparator.comparingInt(child -> child.role() == SyntaxRole.TYPE_OPERAND ? 0 : 1));
      }
      case VariableTree variable -> {
        complete = appendLocalAttachments(variable, creatingPath, path, children, context);
      }
      default -> {
      }
    }
    return complete;
  }

  private String localHeaderKey(BodySourceContext context, VariableTree variable) {
    return context.sourceFile().sourceFileKey() + ':' + sourceEvidence.rawStart(context.unit(), variable);
  }

  private boolean appendLocalAttachments(VariableTree variable, TreePath creatingPath, SyntaxStructuralPath path,
      List<SyntaxChild> children, BodySourceContext context) {
    var owners = context.declarations().localAnnotations(localHeaderKey(context, variable));
    var annotations = treeInventory.variableAnnotations(variable).orElse(List.of());
    var ownAnnotations = annotations.stream()
        .filter(annotation -> sourceEvidence.origin(context.unit(), context.sourceFile(), annotation).filter(owners::contains).isEmpty())
        .toList();
    var site = semantics.element(creatingPath) instanceof VariableElement element
        && (element.getKind() == ElementKind.EXCEPTION_PARAMETER || element.getKind() == ElementKind.PARAMETER)
            ? DeclarationSite.PARAMETER
            : DeclarationSite.LOCAL_VARIABLE;
    var complete = true;
    for (var index = 0; index < ownAnnotations.size(); index++) {
      var attachment = path.child(SyntaxRole.ANNOTATION, index);
      var annotation = annotationProjector
          .declaration(context.unit(), context.sourceFile(), ownAnnotations.get(index), attachment, attachment, site, context.diagnostics())
          .value();
      if (annotation.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.ANNOTATION, index, annotation.orElseThrow()));
      }
      complete &= annotation.isPresent();
    }
    children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION)
        .forEach(child -> owners.retain(child.node().origin().orElseThrow(), child.node().nodeId()));
    if (variable.getType() != null && !treeInventory.hasInferredVariableType(variable)) {
      var typePath = path.child(SyntaxRole.DECLARED_TYPE, 0);
      var translated = new JavaTypeUseTranslator(semantics, sourceEvidence, treeInventory, Optional.of(owners), sourceAssociations)
          .translateDeclaration(context.unit(), context.sourceFile(), variable.getType(), new TreePath(creatingPath, variable.getType()),
              typePath, typePath, semantics.type(creatingPath), context.diagnostics())
          .value();
      translated.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, node)));
      complete &= translated.isPresent();
    }
    children.sort(Comparator.comparingInt(child -> switch (child.role()) {
      case ANNOTATION -> 0;
      case DECLARED_TYPE -> 1;
      default -> 2;
    }));
    return complete;
  }

  private boolean appendBodyType(Tree tree, SyntaxRole role, int position, SyntaxStructuralPath parentPath, List<SyntaxChild> children,
      BodySourceContext context) {
    var path = parentPath.child(role, position);
    var translated = typeUseTranslator.translate(context.unit(), context.sourceFile(), tree, path, path, context.diagnostics()).value();
    translated.ifPresent(node -> children.add(new SyntaxChild(role, position, node)));
    return translated.isPresent();
  }

  private List<AttributedBodyOccurrence> attributions(TranslatedMethodBody body, boolean copied) {
    return body.projection().evidence().nodes().stream().filter(projection -> !copied || sourceCopies.contains(projection.node()))
        .map(projection -> {
          var original = projection.node();
          var retained = copied ? sourceCopies.finalCopy(original) : original;
          return new AttributedBodyOccurrence(body.methodKey(), retained.nodeId(), retained.sourceFileKey(), retained.sourceKind(),
              original.structuralPath());
        }).toList();
  }

  private record BodySourceContext(CompilationUnitTree unit, SourceFile sourceFile, Map<String, TreePath> paths,
      JavaDeclarationScope declarations, DiagnosticEmissionCollector diagnostics) {
  }

}
