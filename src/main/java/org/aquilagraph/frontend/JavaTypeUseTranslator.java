package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import javax.lang.model.type.TypeKind;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisOutcomeKind;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.ResolutionStatus;
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

/** Projects written Java type trees while compiler attribution is available. */
final class JavaTypeUseTranslator {
  private static final String DIAGNOSTIC_PRODUCER = "JavaTypeUseTranslator";
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory inventory;
  private final Optional<Map<SourceSpan, String>> sharedAnnotations;
  private final JavaWrittenSourceAssociations sourceAssociations;

  JavaTypeUseTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory inventory) {
    this(semantics, sourceEvidence, inventory, Optional.empty());
  }

  JavaTypeUseTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory inventory,
      Optional<Map<SourceSpan, String>> sharedAnnotations) {
    this(semantics, sourceEvidence, inventory, sharedAnnotations, new JavaWrittenSourceAssociations(inventory));
  }

  JavaTypeUseTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory inventory,
      Optional<Map<SourceSpan, String>> sharedAnnotations, JavaWrittenSourceAssociations sourceAssociations) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.inventory = Objects.requireNonNull(inventory, "inventory");
    this.sharedAnnotations = Objects.requireNonNull(sharedAnnotations, "sharedAnnotations");
    this.sourceAssociations = Objects.requireNonNull(sourceAssociations, "sourceAssociations");
  }

  Optional<SyntaxNode> translate(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      SyntaxStructuralPath occurrencePath, List<AnalysisDiagnostic> diagnostics) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(occurrencePath, "occurrencePath");
    Objects.requireNonNull(diagnostics, "diagnostics");
    var treePath = TreePath.getPath(unit, tree);
    if (treePath == null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-type-tree-path",
              "A written type has no compiler tree path", sourceEvidence.origin(unit, sourceFile, tree)));
      return Optional.empty();
    }
    return translate(unit, sourceFile, tree, treePath, path, occurrencePath, diagnostics);
  }

  Optional<SyntaxNode> translateDeclaration(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      SyntaxStructuralPath occurrencePath, SyntaxType attributedType, List<AnalysisDiagnostic> diagnostics) {
    var treePath = TreePath.getPath(unit, tree);
    if (treePath == null) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unavailable-type-tree-path",
              "A written type has no compiler tree path", sourceEvidence.origin(unit, sourceFile, tree)));
      return Optional.empty();
    }
    return translateDeclaration(unit, sourceFile, tree, treePath, path, occurrencePath, attributedType, diagnostics);
  }

  Optional<SyntaxNode> translateDeclaration(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, TreePath treePath,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, SyntaxType attributedType, List<AnalysisDiagnostic> diagnostics) {
    if (!JavaProjectionIdentity.sameInstance(treePath.getLeaf(), tree)
        || !JavaProjectionIdentity.sameInstance(treePath.getCompilationUnit(), unit)) {
      throw new IllegalArgumentException("A declaration type projection requires its exact creating tree and compilation unit");
    }
    return translateAttributed(unit, sourceFile, tree, treePath, path, occurrencePath, attributedType, diagnostics);
  }

  private Optional<SyntaxNode> translate(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, TreePath treePath,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, List<AnalysisDiagnostic> diagnostics) {
    return translateAttributed(unit, sourceFile, tree, treePath, path, occurrencePath, semantics.explicitType(treePath), diagnostics);
  }

  Optional<SyntaxNode> translateAttributed(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, TreePath treePath,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, SyntaxType type, List<AnalysisDiagnostic> diagnostics) {
    Objects.requireNonNull(type, "type");
    var range = requiredRange(unit, sourceFile, tree, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var sourceKind = JavaSourceKind.valueOf(tree.getKind().name());
    if (!sourceKind.identifiesTypeUseSyntax()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.invalid-type-header-attachment", "A type header attachment does not contain type-use syntax", range));
      return Optional.empty();
    }
    requiredTypeOutcomeDiagnostic(sourceFile.sourceFileKey(), type, range).ifPresent(diagnostics::add);
    if (type instanceof SyntaxType.StructureUnavailable
        || (type instanceof SyntaxType.Resolved resolved && resolved.structure().isEmpty())) {
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    SyntaxValue value = SyntaxValue.None.INSTANCE;
    var complete = true;
    switch (tree) {
      case PrimitiveTypeTree primitive -> value = new SyntaxValue.PrimitiveType(primitiveKind(primitive.getPrimitiveTypeKind()));
      case IdentifierTree identifier -> value = new SyntaxValue.SourceSymbol(identifier.getName().toString());
      case MemberSelectTree selection -> {
        value = new SyntaxValue.SourceSymbol(selection.getIdentifier().toString());
        complete = appendQualifier(unit, sourceFile, selection.getExpression(), new TreePath(treePath, selection.getExpression()), path,
            occurrencePath, children, diagnostics);
      }
      case ParameterizedTypeTree parameterized -> {
        if (parameterized.getTypeArguments().isEmpty()) {
          var parent = treePath.getParentPath();
          if (parent == null || !(parent.getLeaf() instanceof NewClassTree creation)
              || !JavaProjectionIdentity.sameInstance(creation.getIdentifier(), parameterized)) {
            TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER,
                FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.invalid-diamond-owner",
                    "Diamond syntax requires its exact creating class expression", range));
            return Optional.empty();
          }
          value = SyntaxValue.DiamondType.INSTANCE;
        }
        complete = appendTypeChild(unit, sourceFile, parameterized.getType(), new TreePath(treePath, parameterized.getType()), path,
            occurrencePath, SyntaxRole.TYPE_NAME, 0, children, diagnostics);
        for (var ordinal = 0; ordinal < parameterized.getTypeArguments().size(); ordinal++) {
          var argument = parameterized.getTypeArguments().get(ordinal);
          complete &= appendTypeArgument(unit, sourceFile, argument, treePath, path, occurrencePath, ordinal, children, diagnostics);
        }
      }
      case ArrayTypeTree array -> complete = appendTypeChild(unit, sourceFile, array.getType(), new TreePath(treePath, array.getType()),
          path, occurrencePath, SyntaxRole.COMPONENT_TYPE, 0, children, diagnostics);
      case AnnotatedTypeTree annotated -> {
        var sharedKeys = new ArrayList<String>();
        for (var ordinal = 0; ordinal < annotated.getAnnotations().size(); ordinal++) {
          var annotation = annotated.getAnnotations().get(ordinal);
          var origin = sourceEvidence.origin(unit, sourceFile, annotation);
          var previousOwner = origin.flatMap(span -> sharedAnnotations.map(owners -> owners.get(span)));
          if (previousOwner.isPresent()) {
            sharedKeys.add(previousOwner.orElseThrow());
          } else {
            var position = (int) children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION).count();
            complete &= appendAnnotation(unit, sourceFile, annotation, path, occurrencePath, position, children, diagnostics);
            if (complete && sharedAnnotations.isPresent()) {
              sharedAnnotations.orElseThrow().put(origin.orElseThrow(), children.getLast().node().nodeId());
            }
          }
        }
        if (!sharedKeys.isEmpty()) {
          value = new SyntaxValue.SharedTypeAnnotations(sharedKeys);
        }
        complete &= appendTypeChild(unit, sourceFile, annotated.getUnderlyingType(), new TreePath(treePath, annotated.getUnderlyingType()),
            path, occurrencePath, SyntaxRole.UNDERLYING_TYPE, 0, children, diagnostics);
      }
      case WildcardTree wildcard -> {
        if (wildcard.getKind() != Tree.Kind.UNBOUNDED_WILDCARD && wildcard.getBound() == null) {
          TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory
              .otherError(sourceFile.sourceFileKey(), "frontend.missing-wildcard-bound", "A bounded wildcard has no written bound", range));
          return Optional.empty();
        }
        if (wildcard.getKind() == Tree.Kind.UNBOUNDED_WILDCARD && wildcard.getBound() != null) {
          TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(
              sourceFile.sourceFileKey(), "frontend.unexpected-wildcard-bound", "An unbounded wildcard has a written bound", range));
          return Optional.empty();
        }
        if (wildcard.getBound() != null) {
          var role = switch (wildcard.getKind()) {
            case EXTENDS_WILDCARD -> SyntaxRole.UPPER_BOUND;
            case SUPER_WILDCARD -> SyntaxRole.LOWER_BOUND;
            default -> throw new IllegalArgumentException("A bounded wildcard requires an extends or super source kind");
          };
          complete = appendTypeChild(unit, sourceFile, wildcard.getBound(), new TreePath(treePath, wildcard.getBound()), path,
              occurrencePath, role, 0, children, diagnostics);
        }
      }
      case IntersectionTypeTree intersection -> {
        for (var ordinal = 0; ordinal < intersection.getBounds().size(); ordinal++) {
          var bound = intersection.getBounds().get(ordinal);
          complete &= appendTypeChildFromParent(unit, sourceFile, bound, treePath, path, occurrencePath, SyntaxRole.INTERSECTION_BOUND,
              ordinal, children, diagnostics);
        }
      }
      case UnionTypeTree union -> {
        for (var ordinal = 0; ordinal < union.getTypeAlternatives().size(); ordinal++) {
          var alternative = union.getTypeAlternatives().get(ordinal);
          complete &= appendTypeChildFromParent(unit, sourceFile, alternative, treePath, path, occurrencePath, SyntaxRole.UNION_ALTERNATIVE,
              ordinal, children, diagnostics);
        }
      }
      default -> {
        // Erroneous forms retain their exact source occurrence and blocking compiler outcome.
      }
    }
    if (!complete) {
      return Optional.empty();
    }
    if (tree instanceof MemberSelectTree selection && type instanceof SyntaxType.Resolved resolved
        && resolved.identity().category() == JavaTypeCategory.DECLARED
        && !(children.getFirst().node().value() instanceof SyntaxValue.PackageQualifier)
        && !semantics.selectsTypeMember(treePath, new TreePath(treePath, selection.getExpression()))) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.inconsistent-type-member-selection", "The selected type declaration is not a member of the written qualifier", range));
      return Optional.empty();
    }
    var reference = semantics.typeUseReference(treePath);
    if (type instanceof SyntaxType.Resolved resolved
        && (resolved.identity().category() == JavaTypeCategory.DECLARED || resolved.identity().category() == JavaTypeCategory.TYPE_VARIABLE)
        && (reference.isEmpty() || reference.orElseThrow().status() != ResolutionStatus.RESOLVED)) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.unavailable-type-declaration-reference", "A written nominal or type-variable use has no selected declaration", range));
      return Optional.empty();
    }
    if (sourceKind == JavaSourceKind.PRIMITIVE_TYPE && type instanceof SyntaxType.Resolved resolved
        && resolved.identity().category() != ((SyntaxValue.PrimitiveType) value).kind()) {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.inconsistent-primitive-type", "Written and attributed primitive kinds disagree", range));
      return Optional.empty();
    }
    return Optional.of(sourceAssociations.created(SyntaxNode.source(sourceFile.sourceFileKey(), path, sourceKind, SyntaxKind.TYPE_USE, type,
        value, children, reference, range.orElseThrow()), treePath));
  }

  static Optional<AnalysisDiagnostic> requiredTypeOutcomeDiagnostic(String sourceFileKey, SyntaxType type, Optional<SourceSpan> range) {
    Objects.requireNonNull(sourceFileKey, "sourceFileKey");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(range, "range");
    if (type == SyntaxType.Unresolved.INSTANCE) {
      return Optional.of(FrontendDiagnosticFactory.otherError(sourceFileKey, "frontend.unavailable-type-use-attribution",
          "A written type use has no compiler attribution", range));
    }
    if (type == SyntaxType.Erroneous.INSTANCE) {
      return Optional.of(FrontendDiagnosticFactory.error(sourceFileKey, "frontend.erroneous-type-use",
          "The compiler reported an erroneous written type use", range, AnalysisOutcomeKind.ERRONEOUS_COMPILER_STATE));
    }
    if (type instanceof SyntaxType.NoType noType) {
      return Optional.of(FrontendDiagnosticFactory.otherError(sourceFileKey, "frontend.no-type-in-required-type-use",
          "A written type use resolved to compiler pseudo-type " + noType.kind(), range));
    }
    if (type == SyntaxType.Unsupported.INSTANCE) {
      return Optional.of(FrontendDiagnosticFactory.otherError(sourceFileKey, "frontend.unsupported-attributed-type-use",
          "A written type use has an implementation-reserved compiler type", range));
    }
    if (type instanceof SyntaxType.StructureUnavailable
        || (type instanceof SyntaxType.Resolved resolved && resolved.structure().isEmpty())) {
      return Optional.of(FrontendDiagnosticFactory.otherError(sourceFileKey, "frontend.unavailable-type-use-structure",
          "A written type use has no complete compiler-derived structure", range));
    }
    return Optional.empty();
  }

  private boolean appendTypeChild(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, TreePath treePath,
      SyntaxStructuralPath parentPath, SyntaxStructuralPath parentOccurrencePath, SyntaxRole role, int ordinal, List<SyntaxChild> children,
      List<AnalysisDiagnostic> diagnostics) {
    var childPath = parentPath.child(role, ordinal);
    var childOccurrencePath = parentOccurrencePath.child(role, ordinal);
    var child = translate(unit, sourceFile, tree, treePath, childPath, childOccurrencePath, diagnostics);
    child.ifPresent(node -> children.add(new SyntaxChild(role, ordinal, node)));
    return child.isPresent();
  }

  private boolean appendTypeArgument(CompilationUnitTree unit, SourceFile sourceFile, Tree argument, TreePath parentTreePath,
      SyntaxStructuralPath parentPath, SyntaxStructuralPath parentOccurrencePath, int ordinal, List<SyntaxChild> children,
      List<AnalysisDiagnostic> diagnostics) {
    return appendTypeChildFromParent(unit, sourceFile, argument, parentTreePath, parentPath, parentOccurrencePath, SyntaxRole.TYPE_ARGUMENT,
        ordinal, children, diagnostics);
  }

  private boolean appendTypeChildFromParent(CompilationUnitTree unit, SourceFile sourceFile, Tree childTree, TreePath parentTreePath,
      SyntaxStructuralPath parentPath, SyntaxStructuralPath parentOccurrencePath, SyntaxRole role, int ordinal, List<SyntaxChild> children,
      List<AnalysisDiagnostic> diagnostics) {
    return appendTypeChild(unit, sourceFile, childTree, new TreePath(parentTreePath, childTree), parentPath, parentOccurrencePath, role,
        ordinal, children, diagnostics);
  }

  private boolean appendQualifier(CompilationUnitTree unit, SourceFile sourceFile, Tree qualifier, TreePath qualifierPath,
      SyntaxStructuralPath parentPath, SyntaxStructuralPath parentOccurrencePath, List<SyntaxChild> children,
      List<AnalysisDiagnostic> diagnostics) {
    var path = parentPath.child(SyntaxRole.TYPE_QUALIFIER, 0);
    var occurrencePath = parentOccurrencePath.child(SyntaxRole.TYPE_QUALIFIER, 0);
    var state = semantics.attributedType(qualifierPath);
    var child = state instanceof JavaTypeAttribution.NoType noType && noType.kind() == TypeKind.PACKAGE
        ? packageQualifier(unit, sourceFile, qualifier, qualifierPath, path, occurrencePath, diagnostics)
        : translate(unit, sourceFile, qualifier, qualifierPath, path, occurrencePath, diagnostics);
    child.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.TYPE_QUALIFIER, 0, node)));
    return child.isPresent();
  }

  private Optional<SyntaxNode> packageQualifier(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, TreePath treePath,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, tree, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    String name;
    if (tree instanceof IdentifierTree identifier) {
      name = identifier.getName().toString();
    } else if (tree instanceof MemberSelectTree selection) {
      name = selection.getIdentifier().toString();
      if (!appendQualifier(unit, sourceFile, selection.getExpression(), new TreePath(treePath, selection.getExpression()), path,
          occurrencePath, children, diagnostics)) {
        return Optional.empty();
      }
    } else {
      TranslationDiagnosticEvidence.emit(diagnostics, DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(),
          "frontend.invalid-type-name-qualifier", "A package qualifier has no supported written name", range));
      return Optional.empty();
    }
    return Optional.of(sourceAssociations.created(
        SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.valueOf(tree.getKind().name()), SyntaxKind.TYPE_USE,
            SyntaxType.NotApplicable.INSTANCE, new SyntaxValue.PackageQualifier(name), children, Optional.empty(), range.orElseThrow()),
        treePath));
  }

  private boolean appendAnnotation(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation,
      SyntaxStructuralPath parentPath, SyntaxStructuralPath parentOccurrencePath, int ordinal, List<SyntaxChild> children,
      List<AnalysisDiagnostic> diagnostics) {
    var path = parentPath.child(SyntaxRole.ANNOTATION, ordinal);
    var occurrencePath = parentOccurrencePath.child(SyntaxRole.ANNOTATION, ordinal);
    var projected = new JavaAnnotationProjector(semantics, sourceEvidence, inventory, sourceAssociations).typeUse(unit, sourceFile,
        annotation, path, occurrencePath, diagnostics);
    projected.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.ANNOTATION, ordinal, node)));
    return projected.isPresent();
  }

  private Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      List<AnalysisDiagnostic> diagnostics) {
    return sourceEvidence.requiredRange(unit, sourceFile, tree, path, diagnostics);
  }

  private static JavaTypeCategory primitiveKind(TypeKind kind) {
    return switch (kind) {
      case BOOLEAN -> JavaTypeCategory.BOOLEAN;
      case BYTE -> JavaTypeCategory.BYTE;
      case SHORT -> JavaTypeCategory.SHORT;
      case INT -> JavaTypeCategory.INT;
      case LONG -> JavaTypeCategory.LONG;
      case CHAR -> JavaTypeCategory.CHAR;
      case FLOAT -> JavaTypeCategory.FLOAT;
      case DOUBLE -> JavaTypeCategory.DOUBLE;
      case VOID -> JavaTypeCategory.VOID;
      default -> throw new IllegalArgumentException("A primitive source tree has no primitive kind: " + kind);
    };
  }
}
