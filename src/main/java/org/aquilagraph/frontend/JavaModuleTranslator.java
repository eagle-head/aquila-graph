package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DirectiveTree;
import com.sun.source.tree.ExportsTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.OpensTree;
import com.sun.source.tree.ProvidesTree;
import com.sun.source.tree.RequiresTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UsesTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.SourceVersion;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxStructuralPath;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;
import org.aquilagraph.frontend.JavaAnnotationProjector.DeclarationSite;

/** Projects one written Java module declaration into source-owned AST facts. */
final class JavaModuleTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaModuleTranslator";
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaAnnotationProjector annotationProjector;
  private final JavaWrittenSourceAssociations sourceAssociations;

  JavaModuleTranslator(JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory, JavaAnnotationProjector annotationProjector) {
    this(sourceEvidence, treeInventory, annotationProjector, new JavaWrittenSourceAssociations(treeInventory));
  }

  JavaModuleTranslator(JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory, JavaAnnotationProjector annotationProjector,
      JavaWrittenSourceAssociations sourceAssociations) {
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.annotationProjector = Objects.requireNonNull(annotationProjector, "annotationProjector");
    this.sourceAssociations = Objects.requireNonNull(sourceAssociations, "sourceAssociations");
  }

  Optional<SyntaxNode> translate(CompilationUnitTree unit, SourceFile sourceFile, ModuleTree declaration, SyntaxStructuralPath rootPath,
      List<AnalysisDiagnostic> diagnostics) {
    var path = rootPath.child(SyntaxRole.MODULE_DECLARATION, 0);
    var range = requiredRange(unit, sourceFile, declaration, path, diagnostics);
    var name = requiredName(declaration.getName(), sourceFile, path, "module name", range, diagnostics);
    if (!treeInventory.isWritten(declaration)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          error(sourceFile, path, "frontend.missing-written-module", "Module declaration has no written source occurrence", range));
    }
    var children = new ArrayList<SyntaxChild>();
    var complete = range.isPresent() && name.isPresent() && treeInventory.isWritten(declaration);
    for (var ordinal = 0; ordinal < declaration.getAnnotations().size(); ordinal++) {
      var annotation = declaration.getAnnotations().get(ordinal);
      var annotationPath = path.child(SyntaxRole.ANNOTATION, ordinal);
      var translated = annotation(unit, sourceFile, annotation, annotationPath, diagnostics);
      if (translated.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.ANNOTATION, ordinal, translated.orElseThrow()));
      } else {
        complete = false;
      }
    }
    var position = 0;
    for (var ordinal = 0; ordinal < declaration.getDirectives().size(); ordinal++) {
      var directive = declaration.getDirectives().get(ordinal);
      if (!treeInventory.isWritten(directive)) {
        continue;
      }
      var directivePath = path.child(SyntaxRole.MODULE_DIRECTIVE, position);
      var translated = directive(unit, sourceFile, directive, directivePath, diagnostics);
      if (translated.isPresent()) {
        children.add(new SyntaxChild(SyntaxRole.MODULE_DIRECTIVE, position, translated.orElseThrow()));
        position++;
      } else {
        complete = false;
      }
    }
    if (!complete) {
      return Optional.empty();
    }
    var value = new SyntaxValue.ModuleDeclaration(name.orElseThrow(), declaration.getModuleType() == ModuleTree.ModuleKind.OPEN);
    return Optional.of(
        sourceAssociations.created(SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.MODULE, SyntaxKind.COMPILATION_UNIT,
            SyntaxType.NotApplicable.INSTANCE, value, children, Optional.empty(), range.orElseThrow()), declaration));
  }

  private Optional<SyntaxNode> annotation(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation,
      SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics) {
    return annotationProjector.declaration(unit, sourceFile, annotation, path, path, DeclarationSite.MODULE, diagnostics);
  }

  private Optional<SyntaxNode> directive(CompilationUnitTree unit, SourceFile sourceFile, DirectiveTree declaration,
      SyntaxStructuralPath path, List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, path, diagnostics);
    Optional<SyntaxValue.ModuleDirective> value = switch (declaration) {
      case RequiresTree requires -> requiredName(requires.getModuleName(), sourceFile, path, "required module", range, diagnostics)
          .map(name -> (SyntaxValue.ModuleDirective) new SyntaxValue.Requires(name, requires.isStatic(), requires.isTransitive()));
      case ExportsTree exports ->
        packageTargets(exports.getPackageName(), exports.getModuleNames(), sourceFile, path, range, diagnostics, true);
      case OpensTree opens -> packageTargets(opens.getPackageName(), opens.getModuleNames(), sourceFile, path, range, diagnostics, false);
      case UsesTree uses -> requiredName(uses.getServiceName(), sourceFile, path, "used service", range, diagnostics)
          .map(name -> (SyntaxValue.ModuleDirective) new SyntaxValue.Uses(name));
      case ProvidesTree provides -> providedService(provides, sourceFile, path, range, diagnostics);
      default -> {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
            error(sourceFile, path, "frontend.unknown-module-directive", "A written module directive has no supported source form", range));
        yield Optional.empty();
      }
    };
    if (range.isEmpty() || value.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(sourceAssociations.created(SyntaxNode.source(sourceFile.sourceFileKey(), path,
        JavaSourceKind.valueOf(declaration.getKind().name()), SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        value.orElseThrow(), List.of(), Optional.empty(), range.orElseThrow()), declaration));
  }

  private Optional<SyntaxValue.ModuleDirective> packageTargets(Tree packageTree, List<? extends Tree> targetTrees, SourceFile sourceFile,
      SyntaxStructuralPath path, Optional<SourceSpan> range, List<AnalysisDiagnostic> diagnostics, boolean export) {
    var packageName = requiredName(packageTree, sourceFile, path, "directive package", range, diagnostics);
    if (packageName.isEmpty()) {
      return Optional.empty();
    }
    if (targetTrees == null) {
      return Optional.of(export
          ? new SyntaxValue.Exports(packageName.orElseThrow(), Optional.empty())
          : new SyntaxValue.Opens(packageName.orElseThrow(), Optional.empty()));
    }
    var targets = requiredNames(targetTrees, sourceFile, path, "target module", range, diagnostics);
    return targets.map(names -> export
        ? (SyntaxValue.ModuleDirective) new SyntaxValue.Exports(packageName.orElseThrow(), Optional.of(names))
        : new SyntaxValue.Opens(packageName.orElseThrow(), Optional.of(names)));
  }

  private Optional<SyntaxValue.ModuleDirective> providedService(ProvidesTree declaration, SourceFile sourceFile, SyntaxStructuralPath path,
      Optional<SourceSpan> range, List<AnalysisDiagnostic> diagnostics) {
    var service = requiredName(declaration.getServiceName(), sourceFile, path, "provided service", range, diagnostics);
    var providers = requiredNames(declaration.getImplementationNames(), sourceFile, path, "service provider", range, diagnostics);
    if (service.isEmpty() || providers.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new SyntaxValue.Provides(service.orElseThrow(), providers.orElseThrow()));
  }

  private Optional<List<SyntaxValue.QualifiedName>> requiredNames(List<? extends Tree> trees, SourceFile sourceFile,
      SyntaxStructuralPath path, String label, Optional<SourceSpan> range, List<AnalysisDiagnostic> diagnostics) {
    if (trees == null || trees.isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          error(sourceFile, path, "frontend.missing-module-operand", "Module directive has no " + label, range));
      return Optional.empty();
    }
    var names = new ArrayList<SyntaxValue.QualifiedName>();
    for (var tree : trees) {
      var name = requiredName(tree, sourceFile, path, label, range, diagnostics);
      name.ifPresent(names::add);
    }
    return names.size() == trees.size() ? Optional.of(List.copyOf(names)) : Optional.empty();
  }

  private Optional<SyntaxValue.QualifiedName> requiredName(Tree tree, SourceFile sourceFile, SyntaxStructuralPath path, String label,
      Optional<SourceSpan> range, List<AnalysisDiagnostic> diagnostics) {
    var name = qualifiedName(tree);
    if (name.isEmpty()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          error(sourceFile, path, "frontend.missing-module-operand", "Module declaration has no " + label, range));
    }
    return name;
  }

  private static Optional<SyntaxValue.QualifiedName> qualifiedName(Tree tree) {
    if (tree == null) {
      return Optional.empty();
    }
    return switch (tree) {
      case IdentifierTree identifier when SourceVersion.isIdentifier(identifier.getName()) ->
        Optional.of(new SyntaxValue.QualifiedName(List.of(identifier.getName().toString())));
      case MemberSelectTree selection when SourceVersion.isIdentifier(selection.getIdentifier()) ->
        qualifiedName(selection.getExpression()).map(prefix -> {
          var components = new ArrayList<>(prefix.components());
          components.add(selection.getIdentifier().toString());
          return new SyntaxValue.QualifiedName(components);
        });
      default -> Optional.empty();
    };
  }

  private Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      List<AnalysisDiagnostic> diagnostics) {
    return switch (sourceEvidence.position(unit, sourceFile, tree)) {
      case JavaSourceEvidence.Position.Available available -> Optional.of(available.span());
      case JavaSourceEvidence.Position.Missing ignored -> {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.sourcePositionError(sourceFile, path,
            "frontend.missing-source-position", "A required module occurrence has no compiler position"));
        yield Optional.empty();
      }
      case JavaSourceEvidence.Position.Invalid ignored -> {
        TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.sourcePositionError(sourceFile, path,
            "frontend.invalid-source-range", "A required module occurrence has an invalid compiler range"));
        yield Optional.empty();
      }
    };
  }

  private static AnalysisDiagnostic error(SourceFile sourceFile, SyntaxStructuralPath path, String code, String message,
      Optional<SourceSpan> range) {
    var context = sourceFile.sourceFileKey() + ':' + String.join(":", path.identityComponents());
    return FrontendDiagnosticFactory.otherError(context, code, message, range);
  }
}
