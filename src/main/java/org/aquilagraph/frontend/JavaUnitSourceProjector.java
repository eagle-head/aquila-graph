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
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.SourceVersion;
import org.aquilagraph.frontend.JavaAnnotationProjector.DeclarationSite;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.PackageQualifierEvidence;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

/** Projects compilation-unit envelopes and delegates canonical declarations. */
final class JavaUnitSourceProjector {
  private static final String DIAGNOSTIC_PRODUCER = "JavaAstContributionTranslator";
  private static final char DECLARATION_TERMINATOR = ';';
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaTranslationPolicy policy;
  private final JavaWrittenSourceAssociations sourceAssociations;
  private final JavaBodySourceCorrelation bodyCorrelation;
  private final JavaDeclarationProjector declarations;
  private final JavaAnnotationProjector annotationProjector;
  private final JavaModuleTranslator moduleTranslator;

  JavaUnitSourceProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaTranslationPolicy policy, JavaWrittenSourceAssociations sourceAssociations, JavaBodySourceCorrelation bodyCorrelation,
      JavaDeclarationProjector declarations) {
    this.semantics = semantics;
    this.sourceEvidence = sourceEvidence;
    this.treeInventory = treeInventory;
    this.policy = policy;
    this.sourceAssociations = sourceAssociations;
    this.bodyCorrelation = bodyCorrelation;
    this.declarations = declarations;
    annotationProjector = new JavaAnnotationProjector(semantics, sourceEvidence, treeInventory, sourceAssociations);
    moduleTranslator = new JavaModuleTranslator(sourceEvidence, treeInventory, annotationProjector, sourceAssociations);
  }

  TranslationResult<Optional<SyntaxNode>> project(CompilationUnitTree unit, SourceFile sourceFile, List<TranslatedMethodBody> bodies,
      List<JavaType> declaredTypes) {
    var diagnostics = new DiagnosticEmissionCollector(DIAGNOSTIC_PRODUCER, sourceFile.sourceFileKey());
    try {
      return new TranslationResult<>(projectUnit(unit, sourceFile, bodies, declaredTypes, diagnostics), diagnostics.snapshot());
    } catch (JavaSourceProjectionFailure failure) {
      throw new JavaSourceProjectionFailure(diagnostics.snapshot(), failure);
    }
  }

  private Optional<SyntaxNode> projectUnit(CompilationUnitTree unit, SourceFile sourceFile, List<TranslatedMethodBody> bodies,
      List<JavaType> declaredTypes, DiagnosticEmissionCollector diagnostics) {
    var typesByKey = new HashMap<String, JavaType>();
    for (var type : Objects.requireNonNull(declaredTypes, "declaredTypes")) {
      if (typesByKey.putIfAbsent(type.typeKey(), type) != null) {
        diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(type.typeKey(), "frontend.duplicate-type-declaration",
            "Translated type declarations share a canonical key", type.origins().stream().findFirst()));
      }
    }
    if (!diagnostics.isEmpty()) {
      return Optional.empty();
    }
    bodyCorrelation.correlate(unit, sourceFile, bodies, declaredTypes);
    var rootPath = SyntaxStructuralPath.sourceFileRoot();
    var unitPath = new TreePath(unit);
    var compactClass = compactWrapper(unit, sourceFile, unitPath);
    var rootRange = policy.supportsSourceEnvelope()
        ? Optional.of(sourceEvidence.capturedUnitRange(sourceFile))
        : compactClass.isPresent() && sourceEvidence.rawStart(unit, unit) == 0
            ? Optional.of(new SourceSpan(sourceFile.sourceFileKey(), 0, sourceFile.content().length()))
            : requiredRange(unit, sourceFile, unit, rootPath, diagnostics);
    if (rootRange.isEmpty()) {
      return Optional.empty();
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
        return Optional.empty();
      }
      children.add(new SyntaxChild(SyntaxRole.MODULE_DECLARATION, 0, module.orElseThrow()));
    }
    if (compactClass.isPresent()) {
      var declaration = compactClass.orElseThrow();
      var classPath = new TreePath(unitPath, declaration);
      var compactMembers = new ArrayList<SyntaxChild>();
      var emptyDeclarations = policy.supportsSourceEnvelope() ? treeInventory.emptyBodyDeclarations(declaration) : List.<SourceSpan>of();
      var emptyOrdinal = 0;
      for (var sourceOrdinal = 0; sourceOrdinal < declaration.getMembers().size(); sourceOrdinal++) {
        var member = declaration.getMembers().get(sourceOrdinal);
        if (isWrittenMember(classPath, member)) {
          while (emptyOrdinal < emptyDeclarations.size()
              && emptyDeclarations.get(emptyOrdinal).startOffset() < sourceEvidence.rawStart(unit, member)) {
            appendEmptyUnitDeclaration(unit, sourceFile, rootPath, SyntaxRole.COMPACT_MEMBER, emptyOrdinal,
                emptyDeclarations.get(emptyOrdinal), compactMembers);
            emptyOrdinal++;
          }
          declarations.appendMember(unit, sourceFile, classPath, member, rootPath, rootPath, sourceOrdinal, SyntaxRole.COMPACT_MEMBER,
              compactMembers, diagnostics, typesByKey);
        }
      }
      while (emptyOrdinal < emptyDeclarations.size()) {
        appendEmptyUnitDeclaration(unit, sourceFile, rootPath, SyntaxRole.COMPACT_MEMBER, emptyOrdinal, emptyDeclarations.get(emptyOrdinal),
            compactMembers);
        emptyOrdinal++;
      }
      children.addAll(compactMembers);
      if (compactMembers.stream().noneMatch(child -> child.node().syntaxKind() == SyntaxKind.METHOD_DECLARATION)) {
        diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
            "frontend.unavailable-compact-method", "A compact compilation unit has no publishable written method", rootRange));
        return Optional.empty();
      }
    } else {
      var typeChildren = new ArrayList<SyntaxChild>();
      var emptyDeclarations = policy.supportsSourceEnvelope() ? treeInventory.emptyUnitDeclarations(unit) : List.<SourceSpan>of();
      var emptyOrdinal = 0;
      for (var sourceOrdinal = 0; sourceOrdinal < unit.getTypeDecls().size(); sourceOrdinal++) {
        var declaration = unit.getTypeDecls().get(sourceOrdinal);
        if (declaration instanceof ClassTree type) {
          while (emptyOrdinal < emptyDeclarations.size()
              && emptyDeclarations.get(emptyOrdinal).startOffset() < sourceEvidence.rawStart(unit, type)) {
            appendEmptyUnitDeclaration(unit, sourceFile, rootPath, SyntaxRole.TYPE_DECLARATION, emptyOrdinal,
                emptyDeclarations.get(emptyOrdinal), typeChildren);
            emptyOrdinal++;
          }
          declarations.appendTopLevelType(unit, sourceFile, unitPath, type, rootPath, sourceOrdinal, typeChildren, diagnostics, typesByKey);
        }
      }
      while (emptyOrdinal < emptyDeclarations.size()) {
        appendEmptyUnitDeclaration(unit, sourceFile, rootPath, SyntaxRole.TYPE_DECLARATION, emptyOrdinal,
            emptyDeclarations.get(emptyOrdinal), typeChildren);
        emptyOrdinal++;
      }
      children.addAll(typeChildren);
    }
    if (policy.supportsNestedDeclarations()) {
      new TreePathScanner<Void, Void>() {
        @Override
        public Void visitMethod(MethodTree method, Void unused) {
          var element = semantics.element(getCurrentPath());
          var key = element instanceof ExecutableElement ? semantics.declarationKey(element) : Optional.<String>empty();
          if (method.getBody() != null && key.isPresent() && !bodyCorrelation.hasBody(key.orElseThrow())) {
            var translator = new JavaMethodBodyTranslator(semantics, sourceEvidence, treeInventory,
                JavaExecutableProjectionContext.remainingAttributedBody(policy));
            var translated = translator.translate(unit, sourceFile, key.orElseThrow(), method.getBody(),
                new TreePath(getCurrentPath(), method.getBody()), element.getKind() == ElementKind.CONSTRUCTOR,
                semantics.isImplicitDeclaration(getCurrentPath()));
            bodyCorrelation.addBody(translated.value());
            diagnostics.include(translated.emissions());
          }
          return super.visitMethod(method, null);
        }
      }.scan(unit, null);
    }
    var form = unit.getModule() != null
        ? SyntaxValue.CompilationUnitForm.MODULAR
        : compactClass.isPresent() ? SyntaxValue.CompilationUnitForm.COMPACT : SyntaxValue.CompilationUnitForm.ORDINARY;
    var root = SyntaxNode.source(sourceFile.sourceFileKey(), rootPath, JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(form,
            policy.supportsSourceEnvelope()
                ? SyntaxValue.CompilationUnitRange.CAPTURED_SOURCE
                : SyntaxValue.CompilationUnitRange.LEGACY_PROFILE),
        children, Optional.empty(), rootRange.orElseThrow());
    sourceAssociations.created(root, unitPath);
    return Optional.of(root);
  }

  private Optional<SyntaxNode> packageDeclaration(CompilationUnitTree unit, SourceFile sourceFile, PackageTree declaration,
      SyntaxStructuralPath rootPath, DiagnosticEmissionCollector diagnostics) {
    var path = rootPath.child(SyntaxRole.PACKAGE_DECLARATION, 0);
    var range = requiredRange(unit, sourceFile, declaration, path, diagnostics);
    var name = qualifiedName(declaration.getPackageName());
    if (name.isEmpty()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.missing-package-name", "A written package declaration has no qualified name", range));
    }
    if (range.isEmpty() || name.isEmpty()) {
      return Optional.empty();
    }
    if (!hasTerminatingSemicolon(sourceFile, range.orElseThrow())) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
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

  private Optional<SyntaxNode> importDeclaration(CompilationUnitTree unit, SourceFile sourceFile, ImportTree declaration,
      SyntaxStructuralPath rootPath, int publishedPosition, int sourceOrdinal, DiagnosticEmissionCollector diagnostics) {
    var path = rootPath.child(SyntaxRole.IMPORT_DECLARATION, publishedPosition);
    var occurrencePath = rootPath.child(SyntaxRole.IMPORT_DECLARATION, sourceOrdinal);
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    var target = declaration.getQualifiedIdentifier();
    var onDemand = target instanceof MemberSelectTree selection && selection.getIdentifier().contentEquals("*");
    var name = qualifiedName(onDemand ? ((MemberSelectTree) target).getExpression() : target);
    if (name.isEmpty()) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.missing-import-name",
          "A written import declaration has no qualified target", range));
    }
    if (declaration.isModule() && (declaration.isStatic() || onDemand)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.invalid-import-form",
          "A module import cannot be static or on demand", range));
      return Optional.empty();
    }
    if (range.isEmpty() || name.isEmpty()) {
      return Optional.empty();
    }
    if (!hasTerminatingSemicolon(sourceFile, range.orElseThrow())) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
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

  private void appendEmptyUnitDeclaration(CompilationUnitTree unit, SourceFile source, SyntaxStructuralPath rootPath, SyntaxRole role,
      int ordinal, SourceSpan range, List<SyntaxChild> children) {
    var position = children.size();
    var node = SyntaxNode.source(source.sourceFileKey(), rootPath.child(role, position), JavaSourceKind.EMPTY_STATEMENT, SyntaxKind.EMPTY,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), range);
    sourceAssociations.retain(node, treeInventory.emptyUnitDeclarationAssociation(node, unit, ordinal));
    children.add(new SyntaxChild(role, position, node));
  }

  private Optional<SyntaxNode> annotationAttachment(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DeclarationSite site, DiagnosticEmissionCollector diagnostics) {
    return annotationProjector.declaration(unit, sourceFile, annotation, path, occurrencePath, site, diagnostics).value();
  }

  private Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile source, Tree tree, SyntaxStructuralPath path,
      DiagnosticEmissionCollector diagnostics) {
    return sourceEvidence.requiredRange(unit, source, tree, path, diagnostics).value();
  }
}
