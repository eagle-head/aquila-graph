package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.aquilagraph.frontend.JavaAnnotationProjector.DeclarationSite;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.JavaRecordComponent;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.JavaDeclarationModifier;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.PackageQualifierEvidence;
import org.aquilagraph.source.api.SharedFieldDeclarations;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.jspecify.annotations.Nullable;

/** Assembles the source-only declaration/body tree for one attributed compilation unit. */
final class JavaAstContributionTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaAstContributionTranslator";
  private static final char DECLARATION_TERMINATOR = ';';
  private static final String RECEIVER_NAME = "this";
  private static final int REQUIRED_COMPONENT_SOURCE_COUNT = 1;
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaModuleTranslator moduleTranslator;
  private final JavaAnnotationProjector annotationProjector;
  private final JavaTypeUseTranslator typeUseTranslator;
  private final RecordAnnotationMirrorCorrelation recordMirrorCorrelation;
  private final JavaWrittenSourceAssociations sourceAssociations;
  private final Map<String, Map<SourceSpan, String>> sharedFieldAnnotations = new HashMap<>();
  private final Map<String, SyntaxNode> canonicalBodyCopies = new HashMap<>();

  JavaAstContributionTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory) {
    this(semantics, sourceEvidence, treeInventory, new JavaAnnotationMirrorCorrelation(semantics));
  }

  JavaAstContributionTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      RecordAnnotationMirrorCorrelation recordMirrorCorrelation) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.recordMirrorCorrelation = Objects.requireNonNull(recordMirrorCorrelation, "recordMirrorCorrelation");
    sourceAssociations = new JavaWrittenSourceAssociations(treeInventory);
    annotationProjector = new JavaAnnotationProjector(semantics, sourceEvidence, treeInventory, sourceAssociations);
    moduleTranslator = new JavaModuleTranslator(sourceEvidence, treeInventory, annotationProjector, sourceAssociations);
    typeUseTranslator = new JavaTypeUseTranslator(semantics, sourceEvidence, treeInventory, Optional.empty(), sourceAssociations);
  }

  UnitTranslation translate(CompilationUnitTree unit, SourceFile sourceFile, List<TranslatedMethodBody> bodies,
      List<JavaType> declaredTypes) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(bodies, "bodies");
    sourceAssociations.clear();
    canonicalBodyCopies.clear();
    var diagnostics = new TranslationDiagnosticEvidence(DIAGNOSTIC_PRODUCER, sourceFile.sourceFileKey());
    var typesByKey = new HashMap<String, JavaType>();
    for (var type : Objects.requireNonNull(declaredTypes, "declaredTypes")) {
      if (typesByKey.putIfAbsent(type.typeKey(), type) != null) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(type.typeKey(), "frontend.duplicate-type-declaration",
                "Translated type declarations share a canonical key", type.origins().stream().findFirst()));
      }
    }
    if (!diagnostics.isEmpty()) {
      return new UnitTranslation(Optional.empty(), List.of(), diagnostics);
    }
    var methodsByKey = new HashMap<String, JavaMethod>();
    for (var type : declaredTypes) {
      for (var method : type.methods()) {
        if (methodsByKey.putIfAbsent(method.methodKey(), method) != null) {
          throw new IllegalArgumentException("Duplicate canonical executable for body correlation: " + method.methodKey());
        }
      }
    }
    var bodyByPath = new HashMap<SyntaxStructuralPath, TranslatedMethodBody>();
    for (var body : bodies) {
      corroborateBody(unit, sourceFile, body, methodsByKey);
      body.projection().nodeProjections().forEach(
          projection -> projection.sourceAssociation().ifPresent(association -> sourceAssociations.retain(projection.node(), association)));
      if (bodyByPath.putIfAbsent(body.root().structuralPath(), body) != null) {
        throw new IllegalArgumentException("Duplicate translated body path: " + body.root().structuralPath());
      }
    }
    var publishedBodies = new HashMap<String, SyntaxNode>();
    var unavailableBodies = new ArrayList<String>();
    var declarationEdges = new ArrayList<SyntaxSemanticEdge>();
    var rootPath = SyntaxStructuralPath.sourceFileRoot();
    var unitPath = new TreePath(unit);
    var compactClass = compactWrapper(unit, sourceFile, unitPath);
    var rootRange = compactClass.isPresent() && sourceEvidence.rawStart(unit, unit) == 0
        ? Optional.of(new SourceSpan(sourceFile.sourceFileKey(), 0, sourceFile.content().length()))
        : requiredRange(unit, sourceFile, unit, rootPath, diagnostics);
    if (rootRange.isEmpty()) {
      return new UnitTranslation(Optional.empty(), List.of(), diagnostics);
    }

    var children = new ArrayList<SyntaxChild>();
    if (unit.getPackage() != null) {
      packageDeclaration(unit, sourceFile, unit.getPackage(), rootPath, diagnostics)
          .ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.PACKAGE_DECLARATION, 0, node)));
    }
    var imports = new ArrayList<SyntaxChild>();
    for (var sourceOrdinal = 0; sourceOrdinal < unit.getImports().size(); sourceOrdinal++) {
      var declaration = unit.getImports().get(sourceOrdinal);
      var position = imports.size();
      importDeclaration(unit, sourceFile, declaration, rootPath, position, sourceOrdinal, diagnostics)
          .ifPresent(node -> imports.add(new SyntaxChild(SyntaxRole.IMPORT_DECLARATION, position, node)));
    }
    children.addAll(imports);
    if (unit.getModule() != null) {
      var module = moduleTranslator.translate(unit, sourceFile, unit.getModule(), rootPath, diagnostics);
      if (module.isEmpty()) {
        return new UnitTranslation(Optional.empty(), List.of(), diagnostics);
      }
      children.add(new SyntaxChild(SyntaxRole.MODULE_DECLARATION, 0, module.orElseThrow()));
    }
    if (compactClass.isPresent()) {
      var declaration = compactClass.orElseThrow();
      var classPath = new TreePath(unitPath, declaration);
      var compactMembers = new ArrayList<SyntaxChild>();
      for (var sourceOrdinal = 0; sourceOrdinal < declaration.getMembers().size(); sourceOrdinal++) {
        var member = declaration.getMembers().get(sourceOrdinal);
        if (isWrittenMember(classPath, member)) {
          appendMember(unit, sourceFile, classPath, member, rootPath, rootPath, sourceOrdinal, SyntaxRole.COMPACT_MEMBER, compactMembers,
              bodyByPath, publishedBodies, unavailableBodies, declarationEdges, diagnostics, typesByKey);
        }
      }
      children.addAll(compactMembers);
      if (compactMembers.stream().noneMatch(child -> child.node().syntaxKind() == SyntaxKind.METHOD_DECLARATION)) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-compact-method",
                "A compact compilation unit has no publishable written method", rootRange));
        return new UnitTranslation(Optional.empty(), List.of(), diagnostics);
      }
    } else {
      var typeChildren = new ArrayList<SyntaxChild>();
      for (var sourceOrdinal = 0; sourceOrdinal < unit.getTypeDecls().size(); sourceOrdinal++) {
        var declaration = unit.getTypeDecls().get(sourceOrdinal);
        if (declaration instanceof ClassTree type) {
          appendTopLevelType(unit, sourceFile, unitPath, type, rootPath, sourceOrdinal, typeChildren, bodyByPath, publishedBodies,
              unavailableBodies, declarationEdges, diagnostics, typesByKey);
        }
      }
      children.addAll(typeChildren);
    }
    var form = unit.getModule() != null
        ? SyntaxValue.CompilationUnitForm.MODULAR
        : compactClass.isPresent() ? SyntaxValue.CompilationUnitForm.COMPACT : SyntaxValue.CompilationUnitForm.ORDINARY;
    var root = SyntaxNode.source(sourceFile.sourceFileKey(), rootPath, JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.CompilationUnit(form), children, Optional.empty(), rootRange.orElseThrow());
    sourceAssociations.created(root, unitPath);
    var nodes = root.descendantsAndSelf();
    var structural = nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList();
    var semantic = new ArrayList<>(declarationEdges);
    nodes.forEach(node -> semantic.addAll(node.semanticEdges()));
    var recordTypeOwners = new HashMap<String, SyntaxNode>();
    var fieldGroups = SharedFieldDeclarations.index(nodes);
    nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION).forEach(component -> component
        .children(SyntaxRole.DECLARED_TYPE).forEach(type -> collectRecordTypeOwners(type, component, recordTypeOwners)));
    nodes.forEach(parent -> parent.children().stream()
        .filter(child -> child.role() == SyntaxRole.ANNOTATION && child.node().value() instanceof SyntaxValue.Annotation annotation
            && annotation.location() != SyntaxValue.AnnotationLocation.VALUE
            && annotation.location() != SyntaxValue.AnnotationLocation.UNRESOLVED)
        .forEach(child -> semantic.add(new SyntaxSemanticEdge(child.node().nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET,
            SyntaxSemanticTarget.AnnotationApplication.forAttachment(parent, child.node(),
                Optional.ofNullable(recordTypeOwners.get(parent.nodeId())), fieldGroups.getOrDefault(parent.nodeId(), List.of()))))));
    var contribution = new SyntaxSourceContribution(sourceFile.sourceFileKey(), root, nodes, structural, semantic);
    var registrations = new ArrayList<BodyRegistration>();
    for (var body : bodies) {
      if (!body.root().sourceFileKey().equals(sourceFile.sourceFileKey())) {
        continue;
      }
      var published = publishedBodies.get(body.methodKey());
      if (published != null) {
        registrations.add(new BodyRegistration(body.methodKey(), published, MethodBodyOrigin.SOURCE_AST,
            body.decisions().rebind(canonicalBodyCopies, published), attributions(body, true)));
        continue;
      }
      var origin = unavailableBodies.contains(body.methodKey())
          ? MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION
          : MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY;
      registrations.add(new BodyRegistration(body.methodKey(), body.root(), origin, body.decisions(), attributions(body, false)));
    }
    return new UnitTranslation(Optional.of(contribution), registrations, diagnostics, sourceAssociations.forNodes(nodes));
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
        || !JavaProjectionIdentity.sameInstance(body.root(), projection.producedRoot())
        || !body.root().sourceFileKey().equals(sourceFile.sourceFileKey())
        || !body.root().structuralPath().equals(JavaSyntaxStructuralPath.methodBody(independentBodyPath))) {
      throw new IllegalArgumentException("Translated body does not retain its executable body projection: " + body.methodKey());
    }
    new JavaBodyDecisionProducer(semantics).corroborate(body.root(), projection.nodeProjections());
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

  private static void collectRecordTypeOwners(SyntaxNode type, SyntaxNode component, Map<String, SyntaxNode> owners) {
    owners.put(type.nodeId(), component);
    type.children().stream().map(SyntaxChild::node).filter(child -> child.syntaxKind() == SyntaxKind.TYPE_USE)
        .forEach(child -> collectRecordTypeOwners(child, component, owners));
  }

  private Optional<SyntaxNode> packageDeclaration(CompilationUnitTree unit, SourceFile sourceFile, PackageTree declaration,
      SyntaxStructuralPath rootPath, List<AnalysisDiagnostic> diagnostics) {
    var path = rootPath.child(SyntaxRole.PACKAGE_DECLARATION, 0);
    var range = requiredRange(unit, sourceFile, declaration, path, diagnostics);
    var name = qualifiedName(declaration.getPackageName());
    if (name.isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.missing-package-name", "A written package declaration has no qualified name", range));
    }
    if (range.isEmpty() || name.isEmpty()) {
      return Optional.empty();
    }
    if (!hasTerminatingSemicolon(sourceFile, range.orElseThrow())) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.incomplete-package-declaration", "A written package declaration has no terminating semicolon", range));
      return Optional.empty();
    }
    var annotations = new ArrayList<SyntaxChild>();
    for (var position = 0; position < declaration.getAnnotations().size(); position++) {
      var annotation = declaration.getAnnotations().get(position);
      var annotationPath = path.child(SyntaxRole.ANNOTATION, annotations.size());
      var occurrencePath = path.child(SyntaxRole.ANNOTATION, position);
      annotationAttachment(unit, sourceFile, annotation, annotationPath, occurrencePath, DeclarationSite.PACKAGE, diagnostics)
          .ifPresent(node -> annotations.add(new SyntaxChild(SyntaxRole.ANNOTATION, annotations.size(), node)));
    }
    var qualifierTree = declaration.getPackageName();
    var qualifier = sourceEvidence.origin(unit, sourceFile, qualifierTree)
        .map(origin -> new PackageQualifierEvidence(JavaSourceKind.valueOf(qualifierTree.getKind().name()), origin));
    return Optional.of(sourceAssociations.created(SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.PACKAGE,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageDeclaration(name.orElseThrow(), qualifier),
        annotations, Optional.empty(), range.orElseThrow()), declaration));
  }

  private Optional<SyntaxNode> annotationAttachment(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DeclarationSite site, List<AnalysisDiagnostic> diagnostics) {
    return annotationProjector.declaration(unit, sourceFile, annotation, path, occurrencePath, site, diagnostics);
  }

  private Optional<SyntaxNode> importDeclaration(CompilationUnitTree unit, SourceFile sourceFile, ImportTree declaration,
      SyntaxStructuralPath rootPath, int publishedPosition, int sourceOrdinal, List<AnalysisDiagnostic> diagnostics) {
    var path = rootPath.child(SyntaxRole.IMPORT_DECLARATION, publishedPosition);
    var occurrencePath = rootPath.child(SyntaxRole.IMPORT_DECLARATION, sourceOrdinal);
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    var target = declaration.getQualifiedIdentifier();
    var onDemand = target instanceof MemberSelectTree selection && selection.getIdentifier().contentEquals("*");
    var name = qualifiedName(onDemand ? ((MemberSelectTree) target).getExpression() : target);
    if (name.isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.missing-import-name", "A written import declaration has no qualified target", range));
    }
    if (declaration.isModule() && (declaration.isStatic() || onDemand)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.invalid-import-form", "A module import cannot be static or on demand", range));
      return Optional.empty();
    }
    if (range.isEmpty() || name.isEmpty()) {
      return Optional.empty();
    }
    if (!hasTerminatingSemicolon(sourceFile, range.orElseThrow())) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.incomplete-import-declaration",
              "A written import declaration has no terminating semicolon at " + occurrencePath, range));
      return Optional.empty();
    }
    var value = new SyntaxValue.ImportDeclaration(name.orElseThrow(), declaration.isStatic(), declaration.isModule(), onDemand);
    return Optional.of(
        sourceAssociations.created(SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.IMPORT, SyntaxKind.COMPILATION_UNIT,
            SyntaxType.NotApplicable.INSTANCE, value, List.of(), Optional.empty(), range.orElseThrow()), declaration));
  }

  private static boolean hasTerminatingSemicolon(SourceFile sourceFile, SourceSpan range) {
    if (range.endOffsetExclusive() <= range.startOffset()) {
      return false;
    }
    var end = Math.toIntExact(range.endOffsetExclusive());
    var content = sourceFile.content();
    if (content.charAt(end - 1) == DECLARATION_TERMINATOR) {
      return true;
    }
    var hexStart = end - 4;
    if (hexStart < range.startOffset() || !content.regionMatches(true, hexStart, "003b", 0, 4)) {
      return false;
    }
    var uStart = hexStart;
    while (uStart > range.startOffset() && content.charAt(uStart - 1) == 'u') {
      uStart--;
    }
    var slashStart = uStart;
    while (slashStart > range.startOffset() && content.charAt(slashStart - 1) == '\\') {
      slashStart--;
    }
    return ((uStart - slashStart) & 1) == 1;
  }

  private static Optional<String> qualifiedName(Tree tree) {
    if (tree == null) {
      return Optional.empty();
    }
    return switch (tree) {
      case IdentifierTree identifier -> Optional.of(identifier.getName().toString()).filter(SourceVersion::isIdentifier);
      case MemberSelectTree selection -> qualifiedName(selection.getExpression())
          .filter(prefix -> SourceVersion.isIdentifier(selection.getIdentifier())).map(prefix -> prefix + '.' + selection.getIdentifier());
      default -> Optional.empty();
    };
  }

  private static Optional<String> receiverName(Tree tree) {
    return switch (tree) {
      case IdentifierTree identifier when identifier.getName().contentEquals(RECEIVER_NAME) -> Optional.of(RECEIVER_NAME);
      case MemberSelectTree selection when selection.getIdentifier().contentEquals(RECEIVER_NAME)
          && selection.getExpression() instanceof IdentifierTree identifier
          && SourceVersion.isIdentifier(identifier.getName().toString()) ->
        Optional.of(identifier.getName() + "." + RECEIVER_NAME);
      default -> Optional.empty();
    };
  }

  private Optional<ClassTree> compactWrapper(CompilationUnitTree unit, SourceFile sourceFile, TreePath unitPath) {
    if (unit.getModule() != null || unit.getPackage() != null || unit.getTypeDecls().size() != 1
        || !(unit.getTypeDecls().getFirst() instanceof ClassTree declaration)) {
      return Optional.empty();
    }
    var wrapperStart = sourceEvidence.rawStart(unit, declaration);
    if (wrapperStart < 0 || !(sourceEvidence.position(unit, sourceFile, declaration) instanceof JavaSourceEvidence.Position.Missing)) {
      return Optional.empty();
    }
    var classPath = new TreePath(unitPath, declaration);
    Tree firstWritten = null;
    var hasWrittenMethod = false;
    for (var member : declaration.getMembers()) {
      if (!isWrittenMember(classPath, member)) {
        continue;
      }
      if (firstWritten == null) {
        firstWritten = member;
      }
      hasWrittenMethod |= member instanceof MethodTree;
    }
    return hasWrittenMethod && firstWritten != null && sourceEvidence.rawStart(unit, firstWritten) == wrapperStart
        ? Optional.of(declaration)
        : Optional.empty();
  }

  private boolean isWrittenMember(TreePath classPath, Tree member) {
    return treeInventory.isWritten(member) && !semantics.isImplicitDeclaration(new TreePath(classPath, member));
  }

  private void appendTopLevelType(CompilationUnitTree unit, SourceFile sourceFile, TreePath unitPath, ClassTree declaration,
      SyntaxStructuralPath rootPath, int sourceOrdinal, List<SyntaxChild> children, Map<SyntaxStructuralPath, TranslatedMethodBody> bodies,
      Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics, Map<String, JavaType> typesByKey) {
    var position = children.size();
    var path = rootPath.child(SyntaxRole.TYPE_DECLARATION, position);
    var occurrencePath = rootPath.child(SyntaxRole.TYPE_DECLARATION, sourceOrdinal);
    var declarationPath = new TreePath(unitPath, declaration);
    var translated = type(unit, sourceFile, declarationPath, declaration, path, occurrencePath, bodies, publishedBodies, unavailableBodies,
        semanticEdges, diagnostics, typesByKey);
    translated.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, position, node)));
  }

  private Optional<SyntaxNode> type(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, ClassTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, Map<SyntaxStructuralPath, TranslatedMethodBody> bodies,
      Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics, Map<String, JavaType> typesByKey) {
    if (!treeInventory.isWritten(declaration)) {
      return Optional.empty();
    }
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var element = semantics.element(classPath);
    if (!(element instanceof TypeElement typeElement) || semantics.declarationKey(typeElement).isEmpty()
        || !typeElement.getKind().name().equals(declaration.getKind().name()) || semantics.isImplicitDeclaration(classPath)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-source-type-declaration",
              "A written type has no matching explicit declaration element and stable key", range));
      return Optional.empty();
    }
    var name = declaration.getSimpleName().toString();
    if (!SourceVersion.isIdentifier(name)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.missing-type-name", "A written type declaration has no valid simple name", range));
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var stagedBodies = new HashMap<String, SyntaxNode>();
    var stagedUnavailableBodies = new ArrayList<String>();
    var stagedEdges = new ArrayList<SyntaxSemanticEdge>();
    var complete = true;
    var annotations = declaration.getModifiers().getAnnotations();
    for (var ordinal = 0; ordinal < annotations.size(); ordinal++) {
      var position = ordinal;
      var role = SyntaxRole.ANNOTATION;
      var annotation = annotations.get(ordinal);
      var attachment = annotationAttachment(unit, sourceFile, annotation, path.child(role, position), occurrencePath.child(role, ordinal),
          declaration.getKind() == Tree.Kind.ANNOTATION_TYPE ? DeclarationSite.ANNOTATION_TYPE : DeclarationSite.TYPE, diagnostics);
      attachment.ifPresent(node -> children.add(new SyntaxChild(role, position, node)));
      complete &= attachment.isPresent();
    }
    for (var ordinal = 0; ordinal < declaration.getTypeParameters().size(); ordinal++) {
      var parameter = declaration.getTypeParameters().get(ordinal);
      var parameterPath = path.child(SyntaxRole.TYPE_PARAMETER, ordinal);
      var parameterOccurrencePath = occurrencePath.child(SyntaxRole.TYPE_PARAMETER, ordinal);
      var translated = typeParameter(unit, sourceFile, TreePath.getPath(classPath, parameter), parameter, parameterPath,
          parameterOccurrencePath, semantics.declarationKey(typeElement).orElseThrow(), ordinal, stagedEdges, diagnostics);
      if (translated.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.TYPE_PARAMETER, ordinal, translated.orElseThrow()));
      }
      complete &= translated.isPresent();
    }
    if (declaration.getExtendsClause() != null) {
      complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getExtendsClause(), path, occurrencePath, SyntaxRole.SUPERCLASS,
          0, typeElement.getSuperclass(), children, diagnostics);
    }
    if ((!declaration.getImplementsClause().isEmpty() && declaration.getImplementsClause().size() != typeElement.getInterfaces().size())
        || (!declaration.getPermitsClause().isEmpty()
            && declaration.getPermitsClause().size() != typeElement.getPermittedSubclasses().size())) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.inconsistent-type-header", "Written hierarchy attachments do not match compiler-attributed declaration order", range));
      complete = false;
    }
    for (var ordinal = 0; ordinal < declaration.getImplementsClause().size(); ordinal++) {
      if (ordinal < typeElement.getInterfaces().size()) {
        complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getImplementsClause().get(ordinal), path, occurrencePath,
            SyntaxRole.INTERFACE, ordinal, typeElement.getInterfaces().get(ordinal), children, diagnostics);
      }
    }
    for (var ordinal = 0; ordinal < declaration.getPermitsClause().size(); ordinal++) {
      if (ordinal < typeElement.getPermittedSubclasses().size()) {
        complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getPermitsClause().get(ordinal), path, occurrencePath,
            SyntaxRole.PERMITTED_SUBCLASS, ordinal, typeElement.getPermittedSubclasses().get(ordinal), children, diagnostics);
      }
    }
    var modeledType = typesByKey.get(semantics.declarationKey(typeElement).orElseThrow());
    if (modeledType == null && typesByKey.isEmpty() && declaration.getKind() != Tree.Kind.RECORD) {
      // Non-record source-evidence fixtures may omit the semantic program inventory.
    } else if (modeledType == null || modeledType.recordComponents().size() != typeElement.getRecordComponents().size()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-record-header", "A written type has no complete correlated record-component inventory", range));
      complete = false;
    } else {
      for (var component : modeledType.recordComponents()) {
        var attributedComponent = typeElement.getRecordComponents().get(component.ordinal());
        var componentNode = recordComponent(unit, sourceFile, declaration, typeElement, component, attributedComponent.asType(), path,
            occurrencePath, stagedEdges, diagnostics);
        componentNode.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.RECORD_COMPONENT, component.ordinal(), node)));
        complete &= componentNode.isPresent();
      }
    }
    var members = new ArrayList<SyntaxChild>();
    for (var sourceOrdinal = 0; sourceOrdinal < declaration.getMembers().size(); sourceOrdinal++) {
      var member = declaration.getMembers().get(sourceOrdinal);
      if (!treeInventory.isWritten(member) || member instanceof EmptyStatementTree) {
        continue;
      }
      if (declaration.getKind() == Tree.Kind.RECORD && member instanceof VariableTree field
          && typeElement.getRecordComponents().stream().anyMatch(component -> component.getSimpleName().contentEquals(field.getName()))) {
        continue;
      }
      var before = members.size();
      appendMember(unit, sourceFile, classPath, member, path, occurrencePath, sourceOrdinal, SyntaxRole.MEMBER, members, bodies,
          stagedBodies, stagedUnavailableBodies, stagedEdges, diagnostics, typesByKey);
      complete &= members.size() == before + 1;
    }
    if (!complete) {
      unavailableBodies.addAll(stagedUnavailableBodies);
      return Optional.empty();
    }
    children.addAll(members);
    var shape = new SyntaxValue.TypeDeclaration.Shape(annotations.size(), declaration.getTypeParameters().size(),
        declaration.getExtendsClause() != null, declaration.getImplementsClause().size(), declaration.getPermitsClause().size(),
        modeledType == null ? 0 : modeledType.recordComponents().size(), members.size());
    var value = new SyntaxValue.TypeDeclaration(name, semantics.declarationKey(typeElement).orElseThrow(),
        JavaTypeTranslator.typeModifiers(treeInventory.writtenModifiers(declaration.getModifiers())),
        JavaTypeTranslator.typeModifiers(typeElement.getModifiers()), shape);
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.valueOf(declaration.getKind().name()),
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value, children, Optional.empty(), range.orElseThrow());
    sourceAssociations.created(node, classPath);
    declarationEdge(classPath, node, stagedEdges);
    publishedBodies.putAll(stagedBodies);
    unavailableBodies.addAll(stagedUnavailableBodies);
    semanticEdges.addAll(stagedEdges);
    return Optional.of(node);
  }

  private boolean appendTypeHeaderAttachment(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath parentPath,
      SyntaxStructuralPath parentOccurrencePath, SyntaxRole role, int ordinal, TypeMirror declarationType, List<SyntaxChild> children,
      List<AnalysisDiagnostic> diagnostics) {
    var path = parentPath.child(role, ordinal);
    var attributed = semantics.declarationType(declarationType);
    var node = role == SyntaxRole.DECLARED_TYPE || role == SyntaxRole.RETURN_TYPE
        ? typeUseTranslator.translateDeclaration(unit, sourceFile, tree, path, parentOccurrencePath.child(role, ordinal), attributed,
            diagnostics)
        : typeUseTranslator.translate(unit, sourceFile, tree, path, parentOccurrencePath.child(role, ordinal), diagnostics);
    if (node.isEmpty()) {
      return false;
    }
    var written = node.orElseThrow().type();
    if (written instanceof SyntaxType.Resolved writtenType
        && (!(attributed instanceof SyntaxType.Resolved declaredType) || !matchingDeclarationType(role, writtenType, declaredType))) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.inconsistent-declaration-type",
              "A written type attachment conflicts with its compiler-attributed declaration type", node.orElseThrow().origin()));
      return false;
    }
    children.add(new SyntaxChild(role, ordinal, node.orElseThrow()));
    return true;
  }

  private static boolean matchingDeclarationType(SyntaxRole role, SyntaxType.Resolved written, SyntaxType.Resolved attributed) {
    if (role == SyntaxRole.PERMITTED_SUBCLASS && written.structure().orElseThrow() instanceof JavaTypeStructure.Declared source
        && attributed.structure().orElseThrow() instanceof JavaTypeStructure.Declared selected) {
      return source.declarationKey().equals(selected.declarationKey());
    }
    return written.structure().equals(attributed.structure());
  }

  private Optional<SyntaxNode> recordComponent(CompilationUnitTree unit, SourceFile sourceFile, ClassTree declaration,
      TypeElement typeElement, JavaRecordComponent component, TypeMirror attributedType, SyntaxStructuralPath typePath,
      SyntaxStructuralPath typeOccurrencePath, List<SyntaxSemanticEdge> semanticEdges, List<AnalysisDiagnostic> diagnostics) {
    var candidates = declaration.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast)
        .filter(tree -> treeInventory.isWritten(tree) && tree.getName().contentEquals(component.name())
            && sourceEvidence.origin(unit, sourceFile, tree).filter(component.origin()::equals).isPresent())
        .toList();
    var path = typePath.child(SyntaxRole.RECORD_COMPONENT, component.ordinal());
    var occurrencePath = typeOccurrencePath.child(SyntaxRole.RECORD_COMPONENT, component.ordinal());
    if (candidates.size() != REQUIRED_COMPONENT_SOURCE_COUNT) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(component.componentKey(), "frontend.unavailable-record-component-source",
              "A record component has no unique written source occurrence", Optional.of(component.origin())));
      return Optional.empty();
    }
    var tree = candidates.getFirst();
    var annotations = treeInventory.variableAnnotations(tree);
    if (annotations.isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(component.componentKey(), "frontend.missing-record-component-annotations",
              "A written record component has no pre-attribution annotation inventory", Optional.of(component.origin())));
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var complete = appendAnnotations(unit, sourceFile, annotations.orElseThrow(), path, occurrencePath, DeclarationSite.RECORD_COMPONENT,
        children, diagnostics);
    complete &= appendTypeHeaderAttachment(unit, sourceFile, tree.getType(), path, occurrencePath, SyntaxRole.DECLARED_TYPE, 0,
        attributedType, children, diagnostics);
    if (!complete) {
      return Optional.empty();
    }
    var effectiveModifiers = component.effectiveModifiers().stream().map(modifier -> JavaDeclarationModifier.valueOf(modifier.name()))
        .collect(Collectors.toUnmodifiableSet());
    var counterparts = recordCounterparts(typeElement, component);
    if (counterparts.isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(component.componentKey(), "frontend.unavailable-record-counterpart",
              "A record component has no uniquely correlated accessor or canonical parameter provenance", Optional.of(component.origin())));
      return Optional.empty();
    }
    if (!recordAnnotationMirrors(children, counterparts.orElseThrow())) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(component.componentKey(), "frontend.unavailable-record-annotation-propagation",
              "A record component annotation has no matching compiler-propagated counterpart mirror", Optional.of(component.origin())));
      return Optional.empty();
    }
    var value = new SyntaxValue.RecordComponentDeclaration(component.name(), component.ownerTypeKey(), component.ordinal(), Set.of(),
        effectiveModifiers, component.staticType(), annotations.orElseThrow().size(), component.varArgs(),
        counterparts.orElseThrow().counterparts());
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.VARIABLE, SyntaxKind.RECORD_COMPONENT_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, value, children, Optional.empty(), component.origin());
    sourceAssociations.created(node, tree);
    var reference = new SemanticReference(ReferenceKind.RECORD_COMPONENT, ResolutionStatus.RESOLVED, Optional.of(component.componentKey()),
        Optional.empty(), List.of(), List.of());
    semanticEdges.add(new SyntaxSemanticEdge(node.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference)));
    return Optional.of(node);
  }

  private Optional<RecordCounterpartEvidence> recordCounterparts(TypeElement owner, JavaRecordComponent component) {
    var recordComponent = owner.getRecordComponents().get(component.ordinal());
    var accessor = recordComponent.getAccessor();
    if (accessor == null || semantics.declarationKey(accessor).filter(component.accessorKey().orElseThrow()::equals).isEmpty()) {
      return Optional.empty();
    }
    var canonical = owner.getEnclosedElements().stream().filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
        .filter(executable -> executable.getKind() == ElementKind.CONSTRUCTOR)
        .filter(executable -> executable.getParameters().size() > component.ordinal())
        .filter(executable -> semantics.declarationKey(executable.getParameters().get(component.ordinal()))
            .filter(component.canonicalParameterKey().orElseThrow()::equals).isPresent())
        .toList();
    if (canonical.size() != REQUIRED_COMPONENT_SOURCE_COUNT) {
      return Optional.empty();
    }
    var fields = owner.getEnclosedElements().stream().filter(VariableElement.class::isInstance).map(VariableElement.class::cast)
        .filter(field -> field.getKind() == ElementKind.FIELD)
        .filter(field -> semantics.declarationKey(field).filter(component.backingFieldKey().orElseThrow()::equals).isPresent()).toList();
    if (fields.size() != REQUIRED_COMPONENT_SOURCE_COUNT) {
      return Optional.empty();
    }
    var constructor = canonical.getFirst();
    var accessorImplicit = semantics.sourceTree(accessor) == null;
    var parameterDerived = semantics.declarationOrigin(constructor) == Elements.Origin.MANDATED
        || semantics.isCompactConstructor(constructor);
    var facts = new SyntaxValue.RecordComponentDeclaration.Counterparts(component.backingFieldKey().orElseThrow(),
        component.accessorKey().orElseThrow(), component.canonicalParameterKey().orElseThrow(), accessorImplicit, parameterDerived);
    return Optional.of(new RecordCounterpartEvidence(facts, recordComponent.asType(), fields.getFirst(), accessor,
        constructor.getParameters().get(component.ordinal())));
  }

  private record RecordCounterpartEvidence(SyntaxValue.RecordComponentDeclaration.Counterparts counterparts, TypeMirror componentType,
      VariableElement field, ExecutableElement accessor, VariableElement parameter) {
  }

  private boolean recordAnnotationMirrors(List<SyntaxChild> children, RecordCounterpartEvidence evidence) {
    var writtenType = children.stream().filter(child -> child.role() == SyntaxRole.DECLARED_TYPE).map(SyntaxChild::node).findFirst()
        .orElseThrow();
    var correlation = recordMirrorCorrelation;
    var fieldValues = new ArrayList<SyntaxNode>();
    var accessorValues = new ArrayList<SyntaxNode>();
    var parameterValues = new ArrayList<SyntaxNode>();
    var typeValues = new ArrayList<SyntaxNode>();
    for (var expected : children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION).map(SyntaxChild::node).toList()) {
      var annotation = expected.value(SyntaxValue.Annotation.class);
      var targets = annotation.applicableTargets();
      var counterparts = evidence.counterparts();
      if (targets.contains(ElementType.FIELD)) {
        fieldValues.add(expected);
      }
      if (annotation.location() == SyntaxValue.AnnotationLocation.TYPE_USE
          || annotation.location() == SyntaxValue.AnnotationLocation.DUAL) {
        typeValues.add(expected);
      }
      if (counterparts.accessorImplicit() && targets.contains(ElementType.METHOD)) {
        accessorValues.add(expected);
      }
      if (counterparts.canonicalParameterDerived() && targets.contains(ElementType.PARAMETER)) {
        parameterValues.add(expected);
      }
    }
    var sourceTypes = new JavaWrittenTypeAnnotationCorrelation(semantics);
    return sourceTypes.matches(writtenType, typeValues, evidence.componentType())
        && sourceTypes.matches(writtenType, typeValues, evidence.field().asType())
        && (!evidence.counterparts().accessorImplicit()
            || sourceTypes.matches(writtenType, typeValues, evidence.accessor().getReturnType()))
        && (!evidence.counterparts().canonicalParameterDerived()
            || sourceTypes.matches(writtenType, typeValues, evidence.parameter().asType()))
        && correlation.containsAll(evidence.field().getAnnotationMirrors(), fieldValues)
        && correlation.containsAll(evidence.accessor().getAnnotationMirrors(), accessorValues)
        && correlation.containsAll(evidence.parameter().getAnnotationMirrors(), parameterValues)
        && correlation.sameAnnotatedType(evidence.componentType(), evidence.field().asType())
        && (!evidence.counterparts().accessorImplicit()
            || correlation.sameAnnotatedType(evidence.componentType(), evidence.accessor().getReturnType()))
        && (!evidence.counterparts().canonicalParameterDerived()
            || correlation.sameAnnotatedType(evidence.componentType(), evidence.parameter().asType()));
  }

  private Optional<SyntaxNode> typeParameter(CompilationUnitTree unit, SourceFile sourceFile, TreePath treePath,
      TypeParameterTree declaration, SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, String ownerKey, int ordinal,
      List<SyntaxSemanticEdge> semanticEdges, List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    var expectedKey = ownerKey + "#type-parameter:" + ordinal;
    var element = treePath == null ? null : semantics.element(treePath);
    if (!treeInventory.isWritten(declaration) || treePath == null || semantics.isImplicitDeclaration(treePath)
        || !(element instanceof TypeParameterElement parameterElement)
        || semantics.declarationKey(parameterElement).filter(expectedKey::equals).isEmpty()
        || !SourceVersion.isIdentifier(declaration.getName())) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(expectedKey, "frontend.unavailable-type-parameter-declaration",
              "A written type parameter has no matching attributed declaration and canonical key", range));
      return Optional.empty();
    }
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var complete = appendAnnotations(unit, sourceFile, declaration.getAnnotations(), path, occurrencePath, DeclarationSite.TYPE_PARAMETER,
        children, diagnostics);
    if (declaration.getBounds().size() != parameterElement.getBounds().size() && !declaration.getBounds().isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(expectedKey,
          "frontend.inconsistent-type-parameter-bounds", "Written and attributed type-parameter bounds have different cardinality", range));
      complete = false;
    }
    for (var index = 0; index < declaration.getBounds().size(); index++) {
      if (index < parameterElement.getBounds().size()) {
        complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getBounds().get(index), path, occurrencePath,
            SyntaxRole.TYPE_BOUND, index, parameterElement.getBounds().get(index), children, diagnostics);
      }
    }
    if (!complete) {
      return Optional.empty();
    }
    var upperBound = semantics.typeParameterUpperBound(parameterElement);
    if (upperBound.isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(expectedKey,
          "frontend.unavailable-type-parameter-bound", "The attributed type parameter has no complete upper-bound structure", range));
      return Optional.empty();
    }
    var value = new SyntaxValue.TypeParameterDeclaration(declaration.getName().toString(), ownerKey, ordinal,
        declaration.getAnnotations().size(), declaration.getBounds().size(), upperBound.orElseThrow());
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.TYPE_PARAMETER, SyntaxKind.TYPE_PARAMETER_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, value, children, Optional.empty(), range.orElseThrow());
    sourceAssociations.created(node, treePath);
    declarationEdge(treePath, node, semanticEdges);
    return Optional.of(node);
  }

  private boolean appendAnnotations(CompilationUnitTree unit, SourceFile sourceFile, List<? extends AnnotationTree> annotations,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DeclarationSite site, List<SyntaxChild> children,
      List<AnalysisDiagnostic> diagnostics) {
    var complete = true;
    for (var index = 0; index < annotations.size(); index++) {
      var annotation = annotationAttachment(unit, sourceFile, annotations.get(index), path.child(SyntaxRole.ANNOTATION, index),
          occurrencePath.child(SyntaxRole.ANNOTATION, index), site, diagnostics);
      if (annotation.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.ANNOTATION, index, annotation.orElseThrow()));
      }
      complete &= annotation.isPresent();
    }
    return complete;
  }

  private static Set<JavaDeclarationModifier> declarationModifiers(Set<Modifier> modifiers) {
    return modifiers.stream().map(modifier -> JavaDeclarationModifier.valueOf(modifier.name())).collect(Collectors.toUnmodifiableSet());
  }

  private void appendMember(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, Tree member, SyntaxStructuralPath typePath,
      SyntaxStructuralPath typeOccurrencePath, int sourceOrdinal, SyntaxRole role, List<SyntaxChild> members,
      Map<SyntaxStructuralPath, TranslatedMethodBody> bodies, Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies,
      List<SyntaxSemanticEdge> semanticEdges, List<AnalysisDiagnostic> diagnostics, Map<String, JavaType> typesByKey) {
    var memberTreePath = new TreePath(classPath, member);
    if (!treeInventory.isWritten(member)) {
      return;
    }
    if (semantics.isImplicitDeclaration(memberTreePath)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.inconsistent-member-origin",
              "A written member has a compiler-generated declaration origin", sourceEvidence.origin(unit, sourceFile, member)));
      return;
    }
    var position = members.size();
    var memberPath = typePath.child(role, position);
    var memberOccurrencePath = typeOccurrencePath.child(role, sourceOrdinal);
    Optional<SyntaxNode> translated = switch (member) {
      case MethodTree method -> method(unit, sourceFile, memberTreePath, method, memberPath, memberOccurrencePath, bodies, publishedBodies,
          unavailableBodies, semanticEdges, diagnostics);
      case VariableTree field ->
        field(unit, sourceFile, memberTreePath, field, memberPath, memberOccurrencePath, semanticEdges, diagnostics);
      case ClassTree nested -> type(unit, sourceFile, memberTreePath, nested, memberPath, memberOccurrencePath, bodies, publishedBodies,
          unavailableBodies, semanticEdges, diagnostics, typesByKey);
      case BlockTree initializer -> requiredRange(unit, sourceFile, initializer, memberOccurrencePath, diagnostics)
          .map(range -> sourceAssociations.created(SyntaxNode.source(sourceFile.sourceFileKey(), memberPath, JavaSourceKind.BLOCK,
              initializer.isStatic() ? SyntaxKind.STATIC_INITIALIZER_BLOCK : SyntaxKind.INSTANCE_INITIALIZER_BLOCK,
              SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), range), memberTreePath));
      case EmptyStatementTree _ -> Optional.empty();
      default -> {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unclassified-type-member",
                "A written type member has no source AST declaration form", sourceEvidence.origin(unit, sourceFile, member)));
        yield Optional.empty();
      }
    };
    translated.ifPresent(node -> members.add(new SyntaxChild(role, position, node)));
  }

  private Optional<SyntaxNode> method(CompilationUnitTree unit, SourceFile sourceFile, TreePath methodPath, MethodTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, Map<SyntaxStructuralPath, TranslatedMethodBody> bodies,
      Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    var body = declaration.getBody() == null
        ? null
        : bodies.get(JavaSyntaxStructuralPath.methodBody(new TreePath(methodPath, declaration.getBody())));
    if (range.isEmpty()) {
      if (body != null) {
        unavailableBodies.add(body.methodKey());
      }
      return Optional.empty();
    }
    var element = semantics.element(methodPath);
    if (!(element instanceof ExecutableElement executable) || semantics.declarationKey(executable).isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-executable-declaration", "A written executable has no attributed declaration and canonical key", range));
      if (body != null) {
        unavailableBodies.add(body.methodKey());
      }
      return Optional.empty();
    }
    var owner = methodPath.getParentPath().getLeaf();
    if (!(owner instanceof ClassTree ownerType)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-executable-owner", "A written executable has no source type owner", range));
      if (body != null) {
        unavailableBodies.add(body.methodKey());
      }
      return Optional.empty();
    }
    var sourceOwner = semantics.element(methodPath.getParentPath());
    var sourceOwnerKey = sourceOwner instanceof TypeElement ? semantics.declarationKey(sourceOwner) : Optional.<String>empty();
    var attributedOwner = executable.getEnclosingElement();
    var attributedOwnerKey = attributedOwner instanceof TypeElement ? semantics.declarationKey(attributedOwner) : Optional.<String>empty();
    if (sourceOwnerKey.isEmpty() || !sourceOwnerKey.equals(attributedOwnerKey)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-executable-owner", "A written executable has no matching source and attributed type owner", range));
      if (body != null) {
        unavailableBodies.add(body.methodKey());
      }
      return Optional.empty();
    }
    var constructor = executable.getKind() == ElementKind.CONSTRUCTOR;
    var compactConstructor = constructor && semantics.isCompactConstructor(executable);
    var kind = constructor
        ? compactConstructor
            ? SyntaxValue.ExecutableDeclaration.ExecutableKind.COMPACT_CONSTRUCTOR
            : SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR
        : ownerType.getKind() == Tree.Kind.ANNOTATION_TYPE
            ? SyntaxValue.ExecutableDeclaration.ExecutableKind.ANNOTATION_ELEMENT
            : SyntaxValue.ExecutableDeclaration.ExecutableKind.METHOD;
    var name = constructor ? ownerType.getSimpleName().toString() : declaration.getName().toString();
    if (!SourceVersion.isIdentifier(name) || constructor != (declaration.getReturnType() == null)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.invalid-executable-header", "A written executable has an inconsistent source name or return-type form", range));
      if (body != null) {
        unavailableBodies.add(body.methodKey());
      }
      return Optional.empty();
    }
    var methodKey = semantics.declarationKey(executable).orElseThrow();
    var children = new ArrayList<SyntaxChild>();
    var stagedBodies = new HashMap<String, SyntaxNode>();
    var stagedEdges = new ArrayList<SyntaxSemanticEdge>();
    var site = constructor
        ? DeclarationSite.CONSTRUCTOR
        : declaration.getReturnType() instanceof PrimitiveTypeTree primitive && primitive.getPrimitiveTypeKind() == TypeKind.VOID
            ? DeclarationSite.VOID_METHOD
            : DeclarationSite.METHOD;
    var complete = appendAnnotations(unit, sourceFile, declaration.getModifiers().getAnnotations(), path, occurrencePath, site, children,
        diagnostics);
    for (var index = 0; index < declaration.getTypeParameters().size(); index++) {
      var parameter = declaration.getTypeParameters().get(index);
      var translated = typeParameter(unit, sourceFile, TreePath.getPath(methodPath, parameter), parameter,
          path.child(SyntaxRole.TYPE_PARAMETER, index), occurrencePath.child(SyntaxRole.TYPE_PARAMETER, index), methodKey, index,
          stagedEdges, diagnostics);
      if (translated.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.TYPE_PARAMETER, index, translated.orElseThrow()));
      }
      complete &= translated.isPresent();
    }
    var receiverTree = declaration.getReceiverParameter();
    var receiverType = semantics.receiverType(executable);
    var receiverOwnerKey = semantics.receiverOwnerKey(executable);
    var receiverSimpleName = semantics.receiverSimpleName(executable);
    var enclosingTypeKey = constructor && attributedOwner instanceof TypeElement ownerTypeElement
        ? semantics.enclosingTypeKey(ownerTypeElement)
        : Optional.<String>empty();
    if (receiverType == SyntaxType.Erroneous.INSTANCE) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.erroneousCompilerState(methodKey,
          "frontend.error-receiver-type", "javac reported an error type for the executable receiver", range));
      complete = false;
    } else if (receiverType instanceof SyntaxType.NoType noType) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey,
          "frontend.invalid-receiver-no-type", "The executable receiver resolved to compiler pseudo-type " + noType.kind(), range));
      complete = false;
    } else if (receiverType == SyntaxType.Unsupported.INSTANCE) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey,
          "frontend.unsupported-receiver-type", "The executable receiver has an implementation-reserved compiler type", range));
      complete = false;
    } else if (receiverType == SyntaxType.Unresolved.INSTANCE || receiverType instanceof SyntaxType.StructureUnavailable
        || (receiverTree != null && receiverType == SyntaxType.NotApplicable.INSTANCE)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey,
          "frontend.unavailable-receiver-type", "The attributed receiver type is unavailable for the executable", range));
      complete = false;
    }
    if (receiverType instanceof SyntaxType.Resolved && (receiverOwnerKey.isEmpty() || receiverSimpleName.isEmpty())) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey,
          "frontend.unavailable-receiver-owner", "The attributed receiver has no canonical type owner", range));
      complete = false;
    }
    if (receiverTree != null) {
      var receiverNameExpression = receiverTree.getNameExpression();
      var receiverFormValid = switch (kind) {
        case METHOD -> receiverNameExpression instanceof IdentifierTree;
        case CONSTRUCTOR -> receiverNameExpression instanceof MemberSelectTree;
        case COMPACT_CONSTRUCTOR, ANNOTATION_ELEMENT -> false;
      };
      if (receiverNameExpression != null && !receiverFormValid) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.invalid-executable-header",
                "An explicit receiver has a name form incompatible with its executable", range));
        if (body != null) {
          unavailableBodies.add(body.methodKey());
        }
        return Optional.empty();
      }
      var receiver = parameter(unit, sourceFile, methodPath, receiverTree, path.child(SyntaxRole.RECEIVER_PARAMETER, 0),
          occurrencePath.child(SyntaxRole.RECEIVER_PARAMETER, 0), methodKey, executable.getReceiverType(), true, false, stagedEdges,
          diagnostics);
      receiver.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.RECEIVER_PARAMETER, 0, node)));
      complete &= receiver.isPresent();
    }
    var writtenParameters = compactConstructor ? List.<VariableTree>of() : declaration.getParameters();
    if (executable.getParameters().size() != declaration.getParameters().size()
        || (executable.isVarArgs() && declaration.getParameters().isEmpty())) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(methodKey, "frontend.inconsistent-formal-parameters",
              "The attributed formal-parameter list does not match written source parameters", range));
      complete = false;
    }
    for (var index = 0; index < writtenParameters.size(); index++) {
      var formal = writtenParameters.get(index);
      var parameterKey = methodKey + ":parameter:" + index;
      var translated = index < executable.getParameters().size()
          ? parameter(unit, sourceFile, methodPath, formal, path.child(SyntaxRole.PARAMETER, index),
              occurrencePath.child(SyntaxRole.PARAMETER, index), parameterKey, executable.getParameters().get(index).asType(), false,
              executable.isVarArgs() && index == writtenParameters.size() - 1, stagedEdges, diagnostics)
          : Optional.<SyntaxNode>empty();
      if (translated.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.PARAMETER, index, translated.orElseThrow()));
      }
      complete &= translated.isPresent();
    }
    if (declaration.getReturnType() != null) {
      complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getReturnType(), path, occurrencePath, SyntaxRole.RETURN_TYPE, 0,
          executable.getReturnType(), children, diagnostics);
    }
    var thrownTypes = semantics.thrownTypes(executable);
    if (thrownTypes.size() != declaration.getThrows().size()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(methodKey,
          "frontend.inconsistent-thrown-types", "The attributed thrown-type list does not match written source attachments", range));
      complete = false;
    }
    for (var index = 0; index < declaration.getThrows().size(); index++) {
      if (index < executable.getThrownTypes().size()) {
        complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getThrows().get(index), path, occurrencePath,
            SyntaxRole.THROWN_TYPE, index, executable.getThrownTypes().get(index), children, diagnostics);
      }
      if (index < thrownTypes.size() && !(thrownTypes.get(index) instanceof SyntaxType.Resolved)) {
        var thrownType = thrownTypes.get(index);
        var code = thrownType instanceof SyntaxType.NoType
            ? "frontend.invalid-thrown-no-type"
            : thrownType == SyntaxType.Unsupported.INSTANCE ? "frontend.unsupported-thrown-type" : "frontend.unavailable-thrown-type";
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(methodKey, code, "A written thrown type has no resolved attributed outcome",
                sourceEvidence.origin(unit, sourceFile, declaration.getThrows().get(index))));
        complete = false;
      }
    }
    if (declaration.getBody() != null) {
      if (body == null) {
        throw new IllegalStateException("Translated body is unavailable for source method: " + methodPath);
      }
      var projected = sourceBody(body, path.child(SyntaxRole.BODY, 0), methodPath, declaration, unit, sourceFile, diagnostics);
      if (projected.isPresent()) {
        var node = projected.orElseThrow();
        children.add(new SyntaxChild(SyntaxRole.BODY, 0, node));
        stagedBodies.put(body.methodKey(), node);
      } else {
        complete = false;
      }
    }
    Optional<AnnotationElementValue> defaultValue = Optional.empty();
    if (declaration.getDefaultValue() != null || executable.getDefaultValue() != null) {
      defaultValue = appendDefault(unit, sourceFile, declaration, executable, path, children, diagnostics);
      complete &= defaultValue.isPresent();
    }
    if (!complete) {
      if (body != null) {
        unavailableBodies.add(body.methodKey());
      }
      return Optional.empty();
    }
    var shape = new SyntaxValue.ExecutableDeclaration.HeaderShape(declaration.getModifiers().getAnnotations().size(),
        declaration.getTypeParameters().size(), receiverTree != null, writtenParameters.size(), declaration.getReturnType() != null,
        declaration.getThrows().size(), declaration.getBody() != null, executable.isVarArgs());
    SyntaxValue.ExecutableDeclaration value;
    try {
      value = new SyntaxValue.ExecutableDeclaration(name, sourceOwnerKey.orElseThrow(), methodKey, kind,
          declarationModifiers(treeInventory.writtenModifiers(declaration.getModifiers())), declarationModifiers(executable.getModifiers()),
          shape, new SyntaxValue.ExecutableDeclaration.AttributedHeader(receiverType, receiverOwnerKey, receiverSimpleName,
              enclosingTypeKey, thrownTypes, defaultValue));
    } catch (IllegalArgumentException exception) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.invalid-executable-header",
              Objects.requireNonNullElse(exception.getMessage(), "The executable header is inconsistent"), range));
      if (body != null) {
        unavailableBodies.add(body.methodKey());
      }
      return Optional.empty();
    }
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, value, children, Optional.empty(), range.orElseThrow());
    sourceAssociations.created(node, methodPath);
    publishedBodies.putAll(stagedBodies);
    declarationEdge(methodPath, node, stagedEdges);
    semanticEdges.addAll(stagedEdges);
    return Optional.of(node);
  }

  Optional<AnnotationElementValue> appendDefault(CompilationUnitTree unit, SourceFile sourceFile, MethodTree declaration,
      ExecutableElement executable, SyntaxStructuralPath path, List<SyntaxChild> children, List<AnalysisDiagnostic> diagnostics) {
    var defaultPath = path.child(SyntaxRole.DEFAULT_VALUE, 0);
    var projected = annotationProjector.defaultValue(unit, sourceFile, declaration, executable, defaultPath, diagnostics);
    projected.ifPresent(result -> children.add(new SyntaxChild(SyntaxRole.DEFAULT_VALUE, 0, result.node())));
    return projected.map(result -> result.value());
  }

  private Optional<SyntaxNode> parameter(CompilationUnitTree unit, SourceFile sourceFile, TreePath methodPath, VariableTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, String identityKey, TypeMirror attributedType, boolean receiver,
      boolean varArgs, List<SyntaxSemanticEdge> semanticEdges, List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    var parameterPath = TreePath.getPath(methodPath, declaration);
    var element = parameterPath == null ? null : semantics.element(parameterPath);
    if (!treeInventory.isWritten(declaration) || parameterPath == null || semantics.isImplicitDeclaration(parameterPath)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey,
          "frontend.unavailable-written-parameter", "An attributed parameter is absent from the parsed source declaration", range));
      return Optional.empty();
    }
    if (!receiver
        && (!(element instanceof VariableElement variable) || semantics.declarationKey(variable).filter(identityKey::equals).isEmpty())) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(identityKey, "frontend.unavailable-formal-parameter",
              "A written formal parameter has no matching attributed declaration and canonical key", range));
      return Optional.empty();
    }
    var nameExpression = declaration.getNameExpression();
    if (range.isEmpty() || declaration.getType() == null || declaration.getInitializer() != null || (!receiver && nameExpression != null)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey,
          "frontend.invalid-parameter-header", "A written parameter has an unavailable type or invalid source form", range));
      return Optional.empty();
    }
    var name = receiver ? RECEIVER_NAME : declaration.getName().toString();
    if (!receiver && !SourceVersion.isIdentifier(name)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey,
          "frontend.invalid-parameter-name", "A written formal parameter has no valid simple name", range));
      return Optional.empty();
    }
    if (receiver && nameExpression == null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey,
          "frontend.unavailable-receiver-name", "An explicit receiver has no structural name expression", range));
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var complete = appendAnnotations(unit, sourceFile, declaration.getModifiers().getAnnotations(), path, occurrencePath,
        receiver ? DeclarationSite.RECEIVER : DeclarationSite.PARAMETER, children, diagnostics);
    complete &= appendTypeHeaderAttachment(unit, sourceFile, declaration.getType(), path, occurrencePath, SyntaxRole.DECLARED_TYPE, 0,
        attributedType, children, diagnostics);
    if (receiver) {
      var qualified = receiverName(nameExpression);
      var qualifierRange = requiredRange(unit, sourceFile, nameExpression, occurrencePath.child(SyntaxRole.QUALIFIER, 0), diagnostics);
      if (qualified.isPresent() && qualifierRange.isPresent()) {
        var sourceKind = JavaSourceKind.valueOf(nameExpression.getKind().name());
        var qualifier = SyntaxNode.source(sourceFile.sourceFileKey(), path.child(SyntaxRole.QUALIFIER, 0), sourceKind,
            SyntaxKind.RECEIVER_NAME, SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.SourceSymbol(qualified.orElseThrow()), List.of(),
            Optional.empty(), qualifierRange.orElseThrow());
        sourceAssociations.created(qualifier, new TreePath(parameterPath, nameExpression));
        children.add(new SyntaxChild(SyntaxRole.QUALIFIER, 0, qualifier));
      } else {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(identityKey,
            "frontend.unavailable-receiver-name", "An explicit receiver has no structural name expression and source range", range));
        complete = false;
      }
    }
    if (!complete) {
      return Optional.empty();
    }
    var effective = element instanceof VariableElement variable ? variable.getModifiers() : declaration.getModifiers().getFlags();
    SyntaxValue.ParameterHeader value = receiver
        ? new SyntaxValue.ReceiverParameter(identityKey, declarationModifiers(treeInventory.writtenModifiers(declaration.getModifiers())),
            declarationModifiers(effective), declaration.getModifiers().getAnnotations().size())
        : new SyntaxValue.ParameterDeclaration(identityKey, name,
            declarationModifiers(treeInventory.writtenModifiers(declaration.getModifiers())), declarationModifiers(effective),
            declaration.getModifiers().getAnnotations().size(), true, varArgs);
    var syntaxKind = receiver ? SyntaxKind.RECEIVER_PARAMETER : SyntaxKind.PARAMETER_DECLARATION;
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.VARIABLE, syntaxKind, SyntaxType.NotApplicable.INSTANCE,
        value, children, Optional.empty(), range.orElseThrow());
    sourceAssociations.created(node, parameterPath);
    if (!receiver) {
      declarationEdge(parameterPath, node, semanticEdges);
    }
    return Optional.of(node);
  }

  private Optional<SyntaxNode> field(CompilationUnitTree unit, SourceFile sourceFile, TreePath fieldPath, VariableTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var element = semantics.element(fieldPath);
    var owner = semantics.element(fieldPath.getParentPath());
    var type = semantics.type(fieldPath);
    if (!(element instanceof VariableElement variable) || !(owner instanceof TypeElement ownerType)
        || !(type instanceof SyntaxType.Resolved resolved) || resolved.identity().category() == JavaTypeCategory.ERROR
        || resolved.identity().category() == JavaTypeCategory.OTHER || semantics.declarationKey(variable).isEmpty()
        || semantics.declarationKey(ownerType).isEmpty() || !Objects.equals(variable.getEnclosingElement(), ownerType)
        || (variable.getKind() != ElementKind.FIELD && variable.getKind() != ElementKind.ENUM_CONSTANT)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-field-declaration",
              "A written field or enum constant has no matching attributed declaration, owner, key, or type", range));
      return Optional.empty();
    }
    var enumConstant = variable.getKind() == ElementKind.ENUM_CONSTANT;
    var initializer = declaration.getInitializer();
    if (enumConstant && !(initializer instanceof NewClassTree)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-enum-constant-shape",
              "An enum constant has no attributed constructor-argument and body source shape", range));
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var sharedKeys = enumConstant ? List.<String>of() : sharedFieldKeys(unit, fieldPath, declaration);
    var annotationOwners = sharedKeys.isEmpty()
        ? Optional.<Map<SourceSpan, String>>empty()
        : Optional.of(sharedFieldAnnotations.computeIfAbsent(sharedKeys.getFirst(), ignored -> new HashMap<>()));
    var ownsModifiers = sharedKeys.isEmpty() || sharedKeys.getFirst().equals(semantics.declarationKey(variable).orElseThrow());
    var modifierAnnotations = ownsModifiers ? declaration.getModifiers().getAnnotations() : List.<AnnotationTree>of();
    var complete = appendAnnotations(unit, sourceFile, modifierAnnotations, path, occurrencePath,
        enumConstant ? DeclarationSite.ENUM_CONSTANT : DeclarationSite.FIELD, children, diagnostics);
    if (complete && annotationOwners.isPresent()) {
      children.forEach(child -> annotationOwners.orElseThrow().put(child.node().origin().orElseThrow(), child.node().nodeId()));
    }
    if (!enumConstant) {
      if (declaration.getType() == null) {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-field-type-attachment",
                "A written field has no source type attachment", range));
        return Optional.empty();
      }
      var typePath = path.child(SyntaxRole.DECLARED_TYPE, 0);
      var typeNode = new JavaTypeUseTranslator(semantics, sourceEvidence, treeInventory, annotationOwners, sourceAssociations)
          .translateDeclaration(unit, sourceFile, declaration.getType(), new TreePath(fieldPath, declaration.getType()), typePath,
              occurrencePath.child(SyntaxRole.DECLARED_TYPE, 0), semantics.declarationType(variable.asType()), diagnostics);
      typeNode.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, node)));
      complete &= typeNode.isPresent();
    }
    if (!complete) {
      return Optional.empty();
    }
    var writtenModifiers = enumConstant
        ? Set.<JavaDeclarationModifier>of()
        : declarationModifiers(treeInventory.writtenModifiers(declaration.getModifiers()));
    var kind = enumConstant ? SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT : SyntaxValue.VariableDeclaration.Kind.FIELD;
    var creation = enumConstant && initializer instanceof NewClassTree newClass ? newClass : null;
    var argumentList = creation == null
        ? Optional.of(false)
        : creation.getArguments().isEmpty()
            ? JavaEnumConstantSourceEvidence.argumentList(sourceFile, range.orElseThrow(),
                sourceEvidence.rawStart(unit, creation.getIdentifier()))
            : Optional.of(true);
    if (argumentList.isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-enum-argument-list", "A written enum constant has no verifiable argument-list form", range));
      return Optional.empty();
    }
    var value = new SyntaxValue.VariableDeclaration(declaration.getName().toString(), semantics.declarationKey(ownerType).orElseThrow(),
        semantics.declarationKey(variable).orElseThrow(), kind, writtenModifiers, declarationModifiers(variable.getModifiers()),
        resolved.identity(), modifierAnnotations.size(), !enumConstant, initializer != null, argumentList.orElseThrow(),
        creation == null ? 0 : creation.getArguments().size(), creation != null && creation.getClassBody() != null, sharedKeys);
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.VARIABLE,
        enumConstant ? SyntaxKind.ENUM_CONSTANT_DECLARATION : SyntaxKind.FIELD_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        children, Optional.empty(), range.orElseThrow());
    sourceAssociations.created(node, fieldPath);
    declarationEdge(fieldPath, node, semanticEdges);
    return Optional.of(node);
  }

  private List<String> sharedFieldKeys(CompilationUnitTree unit, TreePath fieldPath, VariableTree declaration) {
    if (!(fieldPath.getParentPath().getLeaf() instanceof ClassTree owner)) {
      return List.of();
    }
    var start = sourceEvidence.rawStart(unit, declaration);
    var keys = owner.getMembers().stream().filter(treeInventory::isWritten).filter(VariableTree.class::isInstance)
        .map(VariableTree.class::cast).filter(candidate -> sourceEvidence.rawStart(unit, candidate) == start)
        .map(candidate -> semantics.element(new TreePath(fieldPath.getParentPath(), candidate)))
        .filter(element -> element != null && element.getKind() == ElementKind.FIELD).map(semantics::declarationKey)
        .flatMap(Optional::stream).toList();
    return keys.size() > 1 ? keys : List.of();
  }

  private Optional<SyntaxNode> sourceBody(TranslatedMethodBody body, SyntaxStructuralPath path, TreePath methodPath, MethodTree declaration,
      CompilationUnitTree unit, SourceFile sourceFile, List<AnalysisDiagnostic> diagnostics) {
    var statements = declaration.getBody().getStatements();
    var bodyPath = new TreePath(methodPath, declaration.getBody());
    var omitInsertedCall = !statements.isEmpty()
        && treeInventory.isCompilerInsertedSuperCall(new TreePath(bodyPath, statements.getFirst()));
    var paths = new HashMap<String, TreePath>();
    body.projection().nodeProjections().forEach(projection -> paths.put(projection.node().nodeId(), projection.path()));
    var context = new BodySourceContext(unit, sourceFile, paths, diagnostics);
    return project(body.root(), path, omitInsertedCall, true, context);
  }

  private Optional<SyntaxNode> project(SyntaxNode original, SyntaxStructuralPath path, boolean omitInsertedCall, boolean bodyRoot,
      BodySourceContext context) {
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
      canonicalBodyCopies.put(original.nodeId(), original);
      return Optional.of(original);
    }
    var copied = sourceAssociations.copied(original, SyntaxNode.source(original.sourceFileKey(), path, original.sourceKind(),
        original.syntaxKind(), original.type(), original.value(), children, original.semanticReference(), range.orElseThrow()));
    canonicalBodyCopies.put(original.nodeId(), copied);
    return Optional.of(copied);
  }

  private boolean appendBodySourceAttachments(SyntaxNode original, TreePath creatingPath, SyntaxStructuralPath path,
      List<SyntaxChild> children, BodySourceContext context) {
    var complete = true;
    switch (creatingPath.getLeaf()) {
      case NewClassTree creation -> {
        complete = appendBodyType(creation.getIdentifier(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
        for (var index = 0; index < creation.getTypeArguments().size(); index++) {
          complete &= appendBodyType(creation.getTypeArguments().get(index), SyntaxRole.TYPE_ARGUMENT, index, path, children, context);
        }
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
        if ((original.syntaxKind() == SyntaxKind.FIELD_ACCESS
            && original.value(SyntaxValue.FieldAccess.class).receiver() == SyntaxValue.FieldReceiver.STATIC_TYPE)
            || original.syntaxKind() == SyntaxKind.RECEIVER) {
          complete = appendBodyType(selection.getExpression(), SyntaxRole.QUALIFIER, 0, path, children, context);
        }
      }
      case TypeCastTree cast -> complete = appendBodyType(cast.getType(), SyntaxRole.TYPE_OPERAND, 0, path, children, context);
      case VariableTree variable -> {
        if (variable.getType() != null && treeInventory.isWritten(variable.getType())) {
          complete = appendBodyType(variable.getType(), SyntaxRole.DECLARED_TYPE, 0, path, children, context);
        }
      }
      default -> {
      }
    }
    return complete;
  }

  private boolean appendBodyType(Tree tree, SyntaxRole role, int position, SyntaxStructuralPath parentPath, List<SyntaxChild> children,
      BodySourceContext context) {
    var path = parentPath.child(role, position);
    var translated = typeUseTranslator.translate(context.unit(), context.sourceFile(), tree, path, path, context.diagnostics());
    translated.ifPresent(node -> children.add(new SyntaxChild(role, position, node)));
    return translated.isPresent();
  }

  private record BodySourceContext(CompilationUnitTree unit, SourceFile sourceFile, Map<String, TreePath> paths,
      List<AnalysisDiagnostic> diagnostics) {
  }

  private Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      List<AnalysisDiagnostic> diagnostics) {
    return sourceEvidence.requiredRange(unit, sourceFile, tree, path, diagnostics);
  }

  private void declarationEdge(TreePath path, SyntaxNode node, List<SyntaxSemanticEdge> edges) {
    var declaration = semantics.element(path);
    if (declaration == null) {
      return;
    }
    var key = semantics.declarationKey(declaration);
    var kind = referenceKind(declaration.getKind());
    if (key.isEmpty() || kind.isEmpty()) {
      return;
    }
    var reference = new SemanticReference(kind.orElseThrow(), ResolutionStatus.RESOLVED, key, Optional.empty(), List.of(), List.of());
    edges.add(new SyntaxSemanticEdge(node.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference)));
  }

  private static Optional<ReferenceKind> referenceKind(ElementKind kind) {
    return switch (kind) {
      case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> Optional.of(ReferenceKind.TYPE);
      case CONSTRUCTOR -> Optional.of(ReferenceKind.CONSTRUCTOR);
      case METHOD -> Optional.of(ReferenceKind.METHOD);
      case FIELD -> Optional.of(ReferenceKind.FIELD);
      case TYPE_PARAMETER -> Optional.of(ReferenceKind.TYPE_PARAMETER);
      case PARAMETER -> Optional.of(ReferenceKind.PARAMETER);
      case ENUM_CONSTANT -> Optional.of(ReferenceKind.ENUM_CONSTANT);
      case RECORD_COMPONENT -> Optional.of(ReferenceKind.RECORD_COMPONENT);
      default -> Optional.empty();
    };
  }

  record UnitTranslation(Optional<SyntaxSourceContribution> contribution, List<BodyRegistration> bodies,
      List<AnalysisDiagnostic> diagnostics, List<WrittenSourceAssociation> sourceAssociations) {
    UnitTranslation(Optional<SyntaxSourceContribution> contribution, List<BodyRegistration> bodies, List<AnalysisDiagnostic> diagnostics) {
      this(contribution, bodies, diagnostics, List.of());
    }

    UnitTranslation {
      Objects.requireNonNull(contribution, "contribution");
      bodies = List.copyOf(bodies);
      diagnostics = TranslationDiagnosticEvidence.copyOf(diagnostics);
      sourceAssociations = List.copyOf(sourceAssociations);
    }
  }

  record BodyRegistration(String methodKey, SyntaxNode root, MethodBodyOrigin origin, JavaBodyDecisions decisions,
      List<AttributedBodyOccurrence> attributedOccurrences) {
  }

  private List<AttributedBodyOccurrence> attributions(TranslatedMethodBody body, boolean copied) {
    return body.projection().nodeProjections().stream()
        .filter(projection -> !copied || canonicalBodyCopies.containsKey(projection.node().nodeId())).map(projection -> {
          var original = projection.node();
          var retained = copied
              ? Objects.requireNonNull(canonicalBodyCopies.get(original.nodeId()), "exact canonical occurrence")
              : original;
          return new AttributedBodyOccurrence(body.methodKey(), retained.nodeId(), retained.sourceFileKey(), retained.sourceKind(),
              original.structuralPath());
        }).toList();
  }
}
