package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.lang.annotation.ElementType;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import org.aquilagraph.frontend.JavaAnnotationValueProjector.ProjectedValue;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

/** Projects parsed annotation evidence onto attributed declaration and type sites. */
final class JavaAnnotationProjector {
  private static final String DIAGNOSTIC_PRODUCER = "JavaAnnotationProjector";
  enum DeclarationSite {
    MODULE(ElementType.MODULE, false, false), PACKAGE(ElementType.PACKAGE, false, false), TYPE(ElementType.TYPE, false, true),
    ANNOTATION_TYPE(ElementType.ANNOTATION_TYPE, false, true), TYPE_PARAMETER(ElementType.TYPE_PARAMETER, false, true),
    METHOD(ElementType.METHOD, true, false), VOID_METHOD(ElementType.METHOD, false, false),
    CONSTRUCTOR(ElementType.CONSTRUCTOR, true, false), FIELD(ElementType.FIELD, true, false), ENUM_CONSTANT(ElementType.FIELD, true, false),
    PARAMETER(ElementType.PARAMETER, true, false), RECEIVER(ElementType.PARAMETER, true, false),
    RECORD_COMPONENT(ElementType.RECORD_COMPONENT, true, false), LOCAL_VARIABLE(ElementType.LOCAL_VARIABLE, true, false);

    private final ElementType target;
    private final boolean closestType;
    private final boolean typeUseIsDeclaration;

    DeclarationSite(ElementType target, boolean closestType, boolean typeUseIsDeclaration) {
      this.target = target;
      this.closestType = closestType;
      this.typeUseIsDeclaration = typeUseIsDeclaration;
    }
  }

  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory inventory;
  private final JavaAnnotationValueProjector values;
  private final JavaWrittenSourceAssociations sourceAssociations;

  JavaAnnotationProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory inventory) {
    this(semantics, sourceEvidence, inventory, new JavaWrittenSourceAssociations(inventory));
  }

  JavaAnnotationProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory inventory,
      JavaWrittenSourceAssociations sourceAssociations) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.inventory = Objects.requireNonNull(inventory, "inventory");
    this.sourceAssociations = Objects.requireNonNull(sourceAssociations, "sourceAssociations");
    values = new JavaAnnotationValueProjector(semantics, sourceEvidence, inventory, sourceAssociations);
  }

  SourceProjectionResult<SyntaxNode> declaration(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DeclarationSite site, DiagnosticEmissionCollector diagnostics) {
    return project(unit, sourceFile, annotation, path, occurrencePath, Optional.of(site), diagnostics);
  }

  SourceProjectionResult<SyntaxNode> typeUse(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DiagnosticEmissionCollector diagnostics) {
    return project(unit, sourceFile, annotation, path, occurrencePath, Optional.empty(), diagnostics);
  }

  private SourceProjectionResult<SyntaxNode> project(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, Optional<DeclarationSite> site,
      DiagnosticEmissionCollector diagnostics) {
    var projectedRange = sourceEvidence.requiredRange(unit, sourceFile, annotation, occurrencePath, diagnostics);
    var range = projectedRange.value();
    var missingWritten = Optional.<DiagnosticEmission>empty();
    var written = inventory.annotation(annotation);
    if (!inventory.isWritten(annotation) || written.isEmpty()) {
      missingWritten = Optional
          .of(diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey() + ':' + occurrencePath,
              "frontend.missing-annotation-source-evidence", "A written annotation has no complete pre-attribution form evidence", range)));
    }
    if (range.isEmpty()) {
      return projectedRange.refusal();
    }
    if (missingWritten.isPresent()) {
      return SourceProjectionResult.failure(missingWritten.orElseThrow());
    }
    var typePath = inventory.annotationTypePath(annotation);
    var element = typePath.isEmpty() ? null : semantics.element(typePath.orElseThrow());
    var reference = typePath.isEmpty()
        ? Optional.<SemanticReference>empty()
        : semantics.reference(typePath.orElseThrow(), ReferenceKind.TYPE);
    var validType = element instanceof TypeElement annotationType && annotationType.getKind() == ElementKind.ANNOTATION_TYPE
        && reference.isPresent() && reference.orElseThrow().status() == ResolutionStatus.RESOLVED;
    Set<ElementType> targets = Set.of();
    var location = SyntaxValue.AnnotationLocation.UNRESOLVED;
    var closestTypeAdmissible = site.map(candidate -> closestTypeAdmissible(annotation, candidate)).orElse(true);
    if (!validType) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey() + ':' + occurrencePath,
          "frontend.unresolved-annotation-type", "The written annotation does not select a resolved annotation type", range));
      reference = Optional.of(
          new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of()));
    } else {
      var annotationType = (TypeElement) Objects.requireNonNull(element);
      var applicable = applicableTargets(annotationType);
      if (applicable.isPresent()) {
        var declaredTargets = applicable.orElseThrow();
        targets = declaredTargets;
        var declarationApplicable = site.filter(candidate -> candidate != DeclarationSite.RECEIVER)
            .map(candidate -> declaredTargets.contains(candidate.target)
                || (candidate == DeclarationSite.ANNOTATION_TYPE && declaredTargets.contains(ElementType.TYPE))
                || (candidate.typeUseIsDeclaration && declaredTargets.contains(ElementType.TYPE_USE))
                || (candidate == DeclarationSite.RECORD_COMPONENT && declaredTargets.stream()
                    .anyMatch(target -> Set.of(ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER).contains(target))))
            .orElse(false);
        var typeApplicable = declaredTargets.contains(ElementType.TYPE_USE) && site.map(candidate -> candidate.closestType).orElse(true)
            && closestTypeAdmissible;
        location = declarationApplicable && typeApplicable
            ? SyntaxValue.AnnotationLocation.DUAL
            : declarationApplicable
                ? SyntaxValue.AnnotationLocation.DECLARATION
                : typeApplicable ? SyntaxValue.AnnotationLocation.TYPE_USE : SyntaxValue.AnnotationLocation.UNRESOLVED;
      }
      if (location == SyntaxValue.AnnotationLocation.UNRESOLVED) {
        diagnostics.emit(DIAGNOSTIC_PRODUCER, FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey() + ':' + occurrencePath,
            "frontend.unresolved-annotation-location", "The written annotation has no applicable declaration or exact type site", range));
        targets = Set.of();
      }
    }
    var parsed = written.orElseThrow();
    List<SyntaxChild> children = List.of();
    String argumentFailureCode = null;
    if (parsed.form() == SyntaxValue.AnnotationForm.SINGLE_ELEMENT || parsed.form() == SyntaxValue.AnnotationForm.NAMED_NORMAL) {
      if (validType && location != SyntaxValue.AnnotationLocation.UNRESOLVED) {
        var projected = values.arguments(unit, sourceFile, annotation, (TypeElement) Objects.requireNonNull(element), path, diagnostics);
        diagnostics.include(projected.emissions());
        var arguments = projected.value();
        children = arguments.value().orElse(List.of());
        argumentFailureCode = arguments.failure().map(event -> event.diagnostic().code()).orElse(null);
      } else {
        argumentFailureCode = validType ? "frontend.unresolved-annotation-location" : "frontend.unresolved-annotation-type";
      }
    }
    var value = argumentFailureCode == null
        ? new SyntaxValue.Annotation(parsed.form(), parsed.arguments(), location, targets, closestTypeAdmissible)
        : SyntaxValue.Annotation.withArgumentFailure(parsed.form(), parsed.arguments(), location, targets, closestTypeAdmissible,
            argumentFailureCode);
    if (validType) {
      value = value.withRepeatableContainer(
          JavaCoverageAnnotationProjection.repeatableContainer((TypeElement) Objects.requireNonNull(element), semantics));
    }
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.ANNOTATION, SyntaxKind.ANNOTATION,
        SyntaxType.NotApplicable.INSTANCE, value, children, reference, range.orElseThrow());
    inventory.annotationPath(annotation).ifPresent(annotationPath -> sourceAssociations.created(node, annotationPath));
    return SourceProjectionResult.success(node);
  }

  private boolean closestTypeAdmissible(AnnotationTree annotation, DeclarationSite site) {
    if (!site.closestType) {
      return false;
    }
    if (site == DeclarationSite.CONSTRUCTOR || site == DeclarationSite.ENUM_CONSTANT) {
      return true;
    }
    var annotationPath = inventory.annotationPath(annotation);
    if (annotationPath.isEmpty()) {
      return false;
    }
    var ownerPath = annotationPath.orElseThrow().getParentPath();
    while (ownerPath != null && ownerPath.getLeaf() instanceof ModifiersTree) {
      ownerPath = ownerPath.getParentPath();
    }
    if (ownerPath == null) {
      return false;
    }
    if (ownerPath.getLeaf() instanceof VariableTree variable && inventory.hasInferredVariableType(variable)) {
      return false;
    }
    Tree writtenType = switch (ownerPath.getLeaf()) {
      case VariableTree variable -> variable.getType();
      case MethodTree method -> method.getReturnType();
      default -> null;
    };
    if (writtenType == null) {
      return false;
    }
    while (writtenType instanceof ArrayTypeTree || writtenType instanceof ParameterizedTypeTree
        || writtenType instanceof AnnotatedTypeTree) {
      writtenType = switch (writtenType) {
        case ArrayTypeTree array -> array.getType();
        case ParameterizedTypeTree parameterized -> parameterized.getType();
        case AnnotatedTypeTree annotated -> annotated.getUnderlyingType();
        default -> throw new IllegalStateException("Unexpected closest type wrapper");
      };
    }
    while (writtenType instanceof MemberSelectTree selection) {
      var memberPath = TreePath.getPath(ownerPath, selection);
      if (memberPath == null || !(semantics.element(memberPath) instanceof TypeElement member)
          || member.getModifiers().contains(Modifier.STATIC)) {
        return false;
      }
      writtenType = selection.getExpression();
    }
    var closestPath = TreePath.getPath(ownerPath, writtenType);
    if (closestPath == null
        || (semantics.attributedType(closestPath) instanceof JavaTypeAttribution.NoType noType && noType.kind() == TypeKind.PACKAGE)) {
      return false;
    }
    return true;
  }

  SourceProjectionResult<ProjectedValue> defaultValue(CompilationUnitTree unit, SourceFile sourceFile, MethodTree method,
      ExecutableElement element, SyntaxStructuralPath path, DiagnosticEmissionCollector diagnostics) {
    return values.defaultValue(unit, sourceFile, method, element, path, diagnostics);
  }

  static Optional<Set<ElementType>> applicableTargets(TypeElement annotationType) {
    var targetMirrors = annotationType.getAnnotationMirrors().stream()
        .filter(mirror -> mirror.getAnnotationType().asElement() instanceof TypeElement targetType
            && targetType.getQualifiedName().contentEquals("java.lang.annotation.Target"))
        .toList();
    if (targetMirrors.isEmpty()) {
      var declarationTargets = EnumSet.allOf(ElementType.class);
      declarationTargets.remove(ElementType.TYPE_USE);
      return Optional.of(Set.copyOf(declarationTargets));
    }
    for (var entry : targetMirrors.getFirst().getElementValues().entrySet()) {
      if (!"value".contentEquals(entry.getKey().getSimpleName())) {
        continue;
      }
      var target = entry.getValue().getValue();
      if (target instanceof List<?> values) {
        var result = EnumSet.noneOf(ElementType.class);
        for (var value : values) {
          if (!(value instanceof AnnotationValue annotationValue) || !(annotationValue.getValue() instanceof VariableElement constant)) {
            return Optional.empty();
          }
          try {
            result.add(ElementType.valueOf(constant.getSimpleName().toString()));
          } catch (IllegalArgumentException invalidTarget) {
            return Optional.empty();
          }
        }
        return Optional.of(Set.copyOf(result));
      }
    }
    return Optional.empty();
  }
}
