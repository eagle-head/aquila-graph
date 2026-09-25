package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxStructuralPath;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;

/** Projects parsed annotation evidence onto one attributed declaration attachment. */
final class JavaAnnotationProjector {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory inventory;
  private final JavaAnnotationValueProjector values;

  JavaAnnotationProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory inventory) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.inventory = Objects.requireNonNull(inventory, "inventory");
    values = new JavaAnnotationValueProjector(semantics, sourceEvidence, inventory);
  }

  Optional<SyntaxNode> declaration(CompilationUnitTree unit, SourceFile sourceFile, AnnotationTree annotation, SyntaxStructuralPath path,
      SyntaxStructuralPath occurrencePath, List<AnalysisDiagnostic> diagnostics) {
    var range = sourceEvidence.requiredRange(unit, sourceFile, annotation, occurrencePath, diagnostics);
    var written = inventory.annotation(annotation);
    if (!inventory.isWritten(annotation) || written.isEmpty()) {
      diagnostics.add(FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey() + ':' + occurrencePath,
          "frontend.missing-annotation-source-evidence", "A written annotation has no complete pre-attribution form evidence", range));
    }
    if (range.isEmpty() || written.isEmpty() || !inventory.isWritten(annotation)) {
      return Optional.empty();
    }
    var typePath = inventory.annotationTypePath(annotation);
    var element = typePath.isEmpty() ? null : semantics.element(typePath.orElseThrow());
    var reference = typePath.isEmpty()
        ? Optional.<SemanticReference>empty()
        : semantics.reference(typePath.orElseThrow(), ReferenceKind.TYPE);
    var validType = element instanceof TypeElement annotationType && annotationType.getKind() == ElementKind.ANNOTATION_TYPE
        && reference.isPresent() && reference.orElseThrow().status() == ResolutionStatus.RESOLVED;
    var declarationOnly = false;
    if (!validType) {
      diagnostics.add(FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey() + ':' + occurrencePath,
          "frontend.unresolved-annotation-type", "The written annotation does not select a resolved annotation type", range));
      reference = Optional.of(
          new SemanticReference(ReferenceKind.TYPE, ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of()));
    } else {
      var annotationType = (TypeElement) Objects.requireNonNull(element);
      declarationOnly = !allowsTypeUse(annotationType);
      if (!declarationOnly) {
        diagnostics.add(FrontendDiagnosticFactory.capabilityError(sourceFile.sourceFileKey() + ':' + occurrencePath,
            "frontend.unresolved-annotation-location", "Annotation type-use applicability is outside the declaration-only profile", range));
      }
    }
    var parsed = written.orElseThrow();
    var location = declarationOnly ? SyntaxValue.AnnotationLocation.DECLARATION : SyntaxValue.AnnotationLocation.UNRESOLVED;
    List<SyntaxChild> children = List.of();
    String argumentFailureCode = null;
    if (parsed.form() == SyntaxValue.AnnotationForm.SINGLE_ELEMENT || parsed.form() == SyntaxValue.AnnotationForm.NAMED_NORMAL) {
      if (validType && declarationOnly) {
        var diagnosticCount = diagnostics.size();
        children = values.arguments(unit, sourceFile, annotation, (TypeElement) element, path, diagnostics);
        if (children.isEmpty()) {
          if (diagnostics.size() == diagnosticCount) {
            diagnostics.add(FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey() + ':' + occurrencePath,
                "frontend.unavailable-annotation-attribution", "The written annotation arguments could not be projected", range));
          }
          argumentFailureCode = diagnostics.get(diagnosticCount).code();
        }
      } else {
        argumentFailureCode = validType ? "frontend.unresolved-annotation-location" : "frontend.unresolved-annotation-type";
      }
    }
    var value = argumentFailureCode == null
        ? new SyntaxValue.Annotation(parsed.form(), parsed.arguments(), location)
        : SyntaxValue.Annotation.withArgumentFailure(parsed.form(), parsed.arguments(), location, argumentFailureCode);
    return Optional.of(SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.ANNOTATION, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, value, children, reference, range.orElseThrow()));
  }

  private static boolean allowsTypeUse(TypeElement annotationType) {
    for (var mirror : annotationType.getAnnotationMirrors()) {
      if (!(mirror.getAnnotationType().asElement() instanceof TypeElement targetType)
          || !targetType.getQualifiedName().contentEquals("java.lang.annotation.Target")) {
        continue;
      }
      for (var entry : mirror.getElementValues().entrySet()) {
        if (!"value".contentEquals(entry.getKey().getSimpleName())) {
          continue;
        }
        var target = entry.getValue().getValue();
        if (target instanceof List<?> values && values.stream().anyMatch(JavaAnnotationProjector::isTypeUseTarget)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isTypeUseTarget(Object value) {
    return value instanceof AnnotationValue annotationValue && annotationValue.getValue() instanceof VariableElement element
        && element.getSimpleName().contentEquals("TYPE_USE");
  }
}
