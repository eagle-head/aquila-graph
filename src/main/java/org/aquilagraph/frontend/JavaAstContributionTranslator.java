package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.MethodBodyOrigin;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxSemanticEdge;
import org.aquilagraph.syntax.SyntaxSemanticRelation;
import org.aquilagraph.syntax.SyntaxSemanticTarget;
import org.aquilagraph.syntax.SyntaxSourceContribution;
import org.aquilagraph.syntax.SyntaxStructuralPath;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;

/** Assembles the source-only declaration/body tree for one attributed compilation unit. */
final class JavaAstContributionTranslator {
  private static final char DECLARATION_TERMINATOR = ';';
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaModuleTranslator moduleTranslator;

  JavaAstContributionTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    moduleTranslator = new JavaModuleTranslator(sourceEvidence, treeInventory);
  }

  UnitTranslation translate(CompilationUnitTree unit, SourceFile sourceFile, List<TranslatedMethodBody> bodies) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(bodies, "bodies");
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    var bodyByPath = new HashMap<SyntaxStructuralPath, TranslatedMethodBody>();
    for (var body : bodies) {
      if (body.root().sourceFileKey().equals(sourceFile.sourceFileKey())
          && bodyByPath.putIfAbsent(body.root().structuralPath(), body) != null) {
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
              bodyByPath, publishedBodies, unavailableBodies, declarationEdges, diagnostics);
        }
      }
      children.addAll(compactMembers);
      if (compactMembers.stream().noneMatch(child -> child.node().syntaxKind() == SyntaxKind.METHOD_DECLARATION)) {
        diagnostics.add(FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-compact-method",
            "A compact compilation unit has no publishable written method", rootRange));
        return new UnitTranslation(Optional.empty(), List.of(), diagnostics);
      }
    } else {
      var typeChildren = new ArrayList<SyntaxChild>();
      for (var sourceOrdinal = 0; sourceOrdinal < unit.getTypeDecls().size(); sourceOrdinal++) {
        var declaration = unit.getTypeDecls().get(sourceOrdinal);
        if (declaration instanceof ClassTree type) {
          appendTopLevelType(unit, sourceFile, unitPath, type, rootPath, sourceOrdinal, typeChildren, bodyByPath, publishedBodies,
              unavailableBodies, declarationEdges, diagnostics);
        }
      }
      children.addAll(typeChildren);
    }
    var form = unit.getModule() != null
        ? SyntaxValue.CompilationUnitForm.MODULAR
        : compactClass.isPresent() ? SyntaxValue.CompilationUnitForm.COMPACT : SyntaxValue.CompilationUnitForm.ORDINARY;
    var root = SyntaxNode.source(sourceFile.sourceFileKey(), rootPath, JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.CompilationUnit(form), children, Optional.empty(), rootRange.orElseThrow());
    var nodes = root.descendantsAndSelf();
    var structural = nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList();
    var semantic = new ArrayList<>(declarationEdges);
    nodes.forEach(node -> semantic.addAll(node.semanticEdges()));
    var contribution = new SyntaxSourceContribution(sourceFile.sourceFileKey(), root, nodes, structural, semantic);
    var registrations = new ArrayList<BodyRegistration>();
    for (var body : bodies) {
      if (!body.root().sourceFileKey().equals(sourceFile.sourceFileKey())) {
        continue;
      }
      var published = publishedBodies.get(body.methodKey());
      var origin = published != null && sameInstance(published, body.root())
          ? MethodBodyOrigin.SOURCE_AST
          : unavailableBodies.contains(body.methodKey())
              ? MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION
              : MethodBodyOrigin.EXECUTION_PROJECTION;
      registrations.add(new BodyRegistration(body.methodKey(), body.root(), origin));
    }
    return new UnitTranslation(Optional.of(contribution), registrations, diagnostics);
  }

  private Optional<SyntaxNode> packageDeclaration(CompilationUnitTree unit, SourceFile sourceFile, PackageTree declaration,
      SyntaxStructuralPath rootPath, List<AnalysisDiagnostic> diagnostics) {
    var path = rootPath.child(SyntaxRole.PACKAGE_DECLARATION, 0);
    var range = requiredRange(unit, sourceFile, declaration, path, diagnostics);
    var name = qualifiedName(declaration.getPackageName());
    if (name.isEmpty()) {
      diagnostics.add(FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.missing-package-name",
          "A written package declaration has no qualified name", range));
    }
    if (range.isEmpty() || name.isEmpty()) {
      return Optional.empty();
    }
    if (!hasTerminatingSemicolon(sourceFile, range.orElseThrow())) {
      diagnostics.add(FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.incomplete-package-declaration",
          "A written package declaration has no terminating semicolon", range));
      return Optional.empty();
    }
    var annotations = new ArrayList<SyntaxChild>();
    for (var position = 0; position < declaration.getAnnotations().size(); position++) {
      var annotation = declaration.getAnnotations().get(position);
      var annotationPath = path.child(SyntaxRole.ANNOTATION, annotations.size());
      var occurrencePath = path.child(SyntaxRole.ANNOTATION, position);
      annotationAttachment(unit, sourceFile, annotation, annotationPath, occurrencePath, diagnostics)
          .ifPresent(node -> annotations.add(new SyntaxChild(SyntaxRole.ANNOTATION, annotations.size(), node)));
    }
    return Optional.of(SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.PACKAGE, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageDeclaration(name.orElseThrow()), annotations, Optional.empty(),
        range.orElseThrow()));
  }

  private Optional<SyntaxNode> annotationAttachment(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, List<AnalysisDiagnostic> diagnostics) {
    return requiredRange(unit, sourceFile, annotation, occurrencePath, diagnostics)
        .map(range -> SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.ANNOTATION, SyntaxKind.COMPILATION_UNIT,
            SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), range));
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
      diagnostics.add(FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.missing-import-name",
          "A written import declaration has no qualified target", range));
    }
    if (declaration.isModule() && (declaration.isStatic() || onDemand)) {
      diagnostics.add(FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.invalid-import-form",
          "A module import cannot be static or on demand", range));
      return Optional.empty();
    }
    if (range.isEmpty() || name.isEmpty()) {
      return Optional.empty();
    }
    if (!hasTerminatingSemicolon(sourceFile, range.orElseThrow())) {
      diagnostics.add(FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.incomplete-import-declaration",
          "A written import declaration has no terminating semicolon at " + occurrencePath, range));
      return Optional.empty();
    }
    var value = new SyntaxValue.ImportDeclaration(name.orElseThrow(), declaration.isStatic(), declaration.isModule(), onDemand);
    return Optional.of(SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.IMPORT, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, value, List.of(), Optional.empty(), range.orElseThrow()));
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
      List<AnalysisDiagnostic> diagnostics) {
    var position = children.size();
    var path = rootPath.child(SyntaxRole.TYPE_DECLARATION, position);
    var occurrencePath = rootPath.child(SyntaxRole.TYPE_DECLARATION, sourceOrdinal);
    var declarationPath = new TreePath(unitPath, declaration);
    var translated = type(unit, sourceFile, declarationPath, declaration, path, occurrencePath, bodies, publishedBodies, unavailableBodies,
        semanticEdges, diagnostics);
    translated.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, position, node)));
  }

  private Optional<SyntaxNode> type(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, ClassTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, Map<SyntaxStructuralPath, TranslatedMethodBody> bodies,
      Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics) {
    if (semantics.isImplicitDeclaration(classPath)) {
      return Optional.empty();
    }
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var members = new ArrayList<SyntaxChild>();
    for (var sourceOrdinal = 0; sourceOrdinal < declaration.getMembers().size(); sourceOrdinal++) {
      var member = declaration.getMembers().get(sourceOrdinal);
      appendMember(unit, sourceFile, classPath, member, path, occurrencePath, sourceOrdinal, SyntaxRole.MEMBER, members, bodies,
          publishedBodies, unavailableBodies, semanticEdges, diagnostics);
    }
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.valueOf(declaration.getKind().name()),
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, members, Optional.empty(),
        range.orElseThrow());
    declarationEdge(classPath, node, semanticEdges);
    return Optional.of(node);
  }

  private void appendMember(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, Tree member, SyntaxStructuralPath typePath,
      SyntaxStructuralPath typeOccurrencePath, int sourceOrdinal, SyntaxRole role, List<SyntaxChild> members,
      Map<SyntaxStructuralPath, TranslatedMethodBody> bodies, Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies,
      List<SyntaxSemanticEdge> semanticEdges, List<AnalysisDiagnostic> diagnostics) {
    var memberTreePath = new TreePath(classPath, member);
    if (member instanceof MethodTree && semantics.isImplicitDeclaration(memberTreePath)) {
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
          unavailableBodies, semanticEdges, diagnostics);
      default -> Optional.empty();
    };
    translated.ifPresent(node -> members.add(new SyntaxChild(role, position, node)));
  }

  private Optional<SyntaxNode> method(CompilationUnitTree unit, SourceFile sourceFile, TreePath methodPath, MethodTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, Map<SyntaxStructuralPath, TranslatedMethodBody> bodies,
      Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      if (declaration.getBody() != null) {
        var bodyPath = JavaSyntaxStructuralPath.methodBody(new TreePath(methodPath, declaration.getBody()));
        var body = bodies.get(bodyPath);
        if (body != null) {
          unavailableBodies.add(body.methodKey());
        }
      }
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    if (declaration.getBody() != null) {
      var originalPath = JavaSyntaxStructuralPath.methodBody(new TreePath(methodPath, declaration.getBody()));
      var body = bodies.get(originalPath);
      if (body == null) {
        throw new IllegalStateException("Translated body is unavailable for source method: " + originalPath);
      }
      var projected = sourceBody(body.root(), path.child(SyntaxRole.BODY, 0), methodPath, declaration);
      if (projected.isPresent()) {
        var node = projected.orElseThrow();
        children.add(new SyntaxChild(SyntaxRole.BODY, 0, node));
        publishedBodies.put(body.methodKey(), node);
      } else {
        unavailableBodies.add(body.methodKey());
      }
    }
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, children, Optional.empty(), range.orElseThrow());
    declarationEdge(methodPath, node, semanticEdges);
    return Optional.of(node);
  }

  private Optional<SyntaxNode> field(CompilationUnitTree unit, SourceFile sourceFile, TreePath fieldPath, VariableTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.VARIABLE, SyntaxKind.FIELD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), range.orElseThrow());
    declarationEdge(fieldPath, node, semanticEdges);
    return Optional.of(node);
  }

  private Optional<SyntaxNode> sourceBody(SyntaxNode body, SyntaxStructuralPath path, TreePath methodPath, MethodTree declaration) {
    var statements = declaration.getBody().getStatements();
    var bodyPath = new TreePath(methodPath, declaration.getBody());
    var omitInsertedCall = !statements.isEmpty()
        && treeInventory.isCompilerInsertedSuperCall(new TreePath(bodyPath, statements.getFirst()));
    return project(body, path, omitInsertedCall, true);
  }

  private Optional<SyntaxNode> project(SyntaxNode original, SyntaxStructuralPath path, boolean omitInsertedCall, boolean bodyRoot) {
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
      var projected = project(child.node(), path.child(child.role(), position), omitInsertedCall, false);
      if (projected.isEmpty()) {
        return Optional.empty();
      }
      var node = projected.orElseThrow();
      children.add(new SyntaxChild(child.role(), position, node));
      positions.put(child.role(), position + 1);
      unchanged &= child.position() == position && sameInstance(node, child.node());
    }
    if (unchanged) {
      return Optional.of(original);
    }
    return Optional.of(SyntaxNode.source(original.sourceFileKey(), path, original.sourceKind(), original.syntaxKind(), original.type(),
        original.value(), children, original.semanticReference(), range.orElseThrow()));
  }

  private static boolean sameInstance(SyntaxNode first, SyntaxNode second) {
    var identity = new IdentityHashMap<SyntaxNode, Boolean>();
    identity.put(first, Boolean.TRUE);
    return identity.containsKey(second);
  }

  private Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      List<AnalysisDiagnostic> diagnostics) {
    return switch (sourceEvidence.position(unit, sourceFile, tree)) {
      case JavaSourceEvidence.Position.Available available -> Optional.of(available.span());
      case JavaSourceEvidence.Position.Missing ignored -> {
        diagnostics.add(FrontendDiagnosticFactory.sourcePositionError(sourceFile, path, "frontend.missing-source-position",
            "A required source occurrence has no compiler position"));
        yield Optional.empty();
      }
      case JavaSourceEvidence.Position.Invalid ignored -> {
        diagnostics.add(FrontendDiagnosticFactory.sourcePositionError(sourceFile, path, "frontend.invalid-source-range",
            "A required source occurrence has an invalid compiler range"));
        yield Optional.empty();
      }
    };
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
      case ENUM_CONSTANT -> Optional.of(ReferenceKind.ENUM_CONSTANT);
      default -> Optional.empty();
    };
  }

  record UnitTranslation(Optional<SyntaxSourceContribution> contribution, List<BodyRegistration> bodies,
      List<AnalysisDiagnostic> diagnostics) {
    UnitTranslation {
      Objects.requireNonNull(contribution, "contribution");
      bodies = List.copyOf(bodies);
      diagnostics = List.copyOf(diagnostics);
    }
  }

  record BodyRegistration(String methodKey, SyntaxNode root, MethodBodyOrigin origin) {
  }
}
