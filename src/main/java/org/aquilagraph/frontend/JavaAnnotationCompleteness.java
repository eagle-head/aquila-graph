package org.aquilagraph.frontend;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

/** Reconciles independent written annotation obligations with retained source and compiler facts in both directions. */
final class JavaAnnotationCompleteness {
  private static final int UNIQUE_OCCURRENCE_COUNT = 1;
  private final JavaAnnotationSourceInventory inventory;
  private final SemanticResolver semantics;
  private final JavaAnnotationValues values;
  private final List<AnalysisDiagnostic> priorDiagnostics;
  private final List<AnalysisDiagnostic> reconciliationDiagnostics = new TranslationDiagnosticEvidence("JavaAnnotationCompleteness",
      "input-annotation-reconciliation");
  private final Set<String> checked = new HashSet<>();

  JavaAnnotationCompleteness(JavaAnnotationSourceInventory inventory, SemanticResolver semantics,
      List<AnalysisDiagnostic> priorDiagnostics) {
    this.inventory = inventory;
    this.semantics = semantics;
    this.values = new JavaAnnotationValues(semantics);
    this.priorDiagnostics = List.copyOf(priorDiagnostics);
  }

  List<AnalysisDiagnostic> diagnostics(Optional<JavaAstAggregate> aggregate) {
    reconciliationDiagnostics.clear();
    checked.clear();
    var nodes = aggregate.map(JavaAstAggregate::nodes).orElseGet(List::of);
    var ordinals = new HashMap<String, Integer>();
    for (var occurrence : inventory.annotations()) {
      var ordinal = ordinals.merge(occurrence.source().sourceFileKey(), 1, Integer::sum);
      var expected = occurrence.tree();
      var matches = nodes.stream()
          .filter(node -> node.syntaxKind() == SyntaxKind.ANNOTATION && node.sourceFileKey().equals(occurrence.source().sourceFileKey())
              && expected.range().isPresent() && node.origin().equals(expected.range()))
          .toList();
      if (expected.range().isEmpty()) {
        report(occurrence.source(), "annotation:" + ordinal,
            expected.position() instanceof JavaSourceEvidence.Position.Invalid
                ? "frontend.invalid-source-range"
                : "frontend.unavailable-source-annotation-range",
            "Written " + expected.sourceKind() + " occurrence " + ordinal + " has no exact source range", Optional.empty());
      } else if (matches.size() != UNIQUE_OCCURRENCE_COUNT) {
        TranslationDiagnosticEvidence.emit(reconciliationDiagnostics, "JavaAnnotationCompleteness",
            FrontendDiagnosticFactory.capabilityError(occurrence.source().sourceFileKey() + ":annotation:" + ordinal,
                "frontend.unrepresented-source-annotation", "A written annotation has no unique retained occurrence", expected.range()));
      } else {
        var annotation = matches.getFirst();
        check(occurrence.source(), expected, annotation);
        if (annotation.value(SyntaxValue.Annotation.class).location() != SyntaxValue.AnnotationLocation.VALUE) {
          checkOwner(occurrence, annotation, aggregate.orElseThrow());
        }
      }
    }
    for (var declaration : inventory.declarations()) {
      checkDeclaration(declaration, nodes);
    }
    for (var node : nodes) {
      if (annotationFact(node) && !checked.contains(node.nodeId())) {
        report(source(aggregate.orElseThrow(), node.sourceFileKey()), node.nodeId(), "frontend.unexpected-annotation-source-fact",
            "An annotation fact has no matching written obligation", node.origin());
      }
    }
    return TranslationDiagnosticEvidence.copyOf(reconciliationDiagnostics);
  }

  List<DiagnosticEmission> reconcileWithEvents(Optional<JavaAstAggregate> aggregate) {
    return TranslationDiagnosticEvidence.events(diagnostics(aggregate));
  }

  List<LanguageFeatureObservation> propertyObservations(LanguageFeatureCoverage coverage) {
    return inventory.declarations().stream().flatMap(declaration -> declaration.properties().stream().map(identifier -> {
      var feature = new LanguageFeature(LanguageFeatureCategory.DECLARATION_PROPERTY, identifier);
      var classification = coverage.classificationOf(feature).orElseThrow();
      return new LanguageFeatureObservation(declaration.source().sourceFileKey(), classification, declaration.range().stream().toList(),
          Optional.empty());
    })).toList();
  }

  private void checkOwner(JavaAnnotationSourceInventory.Occurrence occurrence, SyntaxNode annotation, JavaAstAggregate aggregate) {
    var incoming = aggregate.structuralEdges().stream().filter(edge -> edge.childKey().equals(annotation.nodeId())).toList();
    var ancestors = new ArrayList<JavaAnnotationSourceInventory.Ancestor>();
    var parent = incoming.isEmpty() ? Optional.<SyntaxNode>empty() : aggregate.node(incoming.getFirst().parentKey());
    var ownerRange = parent.flatMap(SyntaxNode::origin);
    while (parent.isPresent() && parent.orElseThrow().sourceKind() != JavaSourceKind.COMPILATION_UNIT) {
      var current = parent.orElseThrow();
      if (!(current.value() instanceof SyntaxValue.AnnotationArgument pair && pair.explicitName().isEmpty())) {
        ancestors.add(new JavaAnnotationSourceInventory.Ancestor(current.sourceKind(), current.origin()));
      }
      parent = aggregate.structuralEdges().stream().filter(edge -> edge.childKey().equals(current.nodeId())).findFirst()
          .flatMap(edge -> aggregate.node(edge.parentKey()));
    }
    if (incoming.size() != UNIQUE_OCCURRENCE_COUNT || incoming.getFirst().role() != SyntaxRole.ANNOTATION
        || incoming.getFirst().position() != occurrence.ordinal() || !ownerRange.equals(occurrence.ownerRange())
        || !ancestors.equals(occurrence.ancestors())) {
      report(occurrence.source(), annotation.nodeId(), "frontend.inconsistent-annotation-source-owner",
          "The physical annotation owner differs from its captured source owner: expected ancestor kinds "
              + occurrence.ancestors().stream().map(JavaAnnotationSourceInventory.Ancestor::kind).toList() + " ordinal "
              + occurrence.ordinal() + ", retained kinds " + ancestors.stream().map(JavaAnnotationSourceInventory.Ancestor::kind).toList(),
          annotation.origin());
    }
  }

  private void checkDeclaration(JavaAnnotationSourceInventory.Declaration declaration, List<SyntaxNode> nodes) {
    var matches = nodes.stream()
        .filter(node -> node.origin().equals(declaration.range()) && node.sourceFileKey().equals(declaration.source().sourceFileKey())
            && (node.syntaxKind() == SyntaxKind.TYPE_DECLARATION || node.syntaxKind() == SyntaxKind.METHOD_DECLARATION))
        .toList();
    if (matches.size() != UNIQUE_OCCURRENCE_COUNT) {
      report(declaration.source(), declaration.path().getLeaf().getKind() + ":declaration:" + declaration.ordinal(),
          "frontend.unrepresented-annotation-declaration", "A declaration annotation/default surface has no unique source declaration",
          declaration.range());
      return;
    }
    var node = matches.getFirst();
    var element = semantics.element(declaration.path());
    var retainedKey = node.value() instanceof SyntaxValue.TypeDeclaration type
        ? type.declarationKey()
        : node.value(SyntaxValue.ExecutableDeclaration.class).declarationKey();
    if (element == null || !semantics.declarationKey(element).filter(retainedKey::equals).isPresent()) {
      report(declaration.source(), node.nodeId(), "frontend.inconsistent-annotation-declaration-reference",
          "The declaration surface does not select its attributed declaration", node.origin());
    }
    if (!(node.value() instanceof SyntaxValue.ExecutableDeclaration executable)) {
      return;
    }
    var defaults = node.children(SyntaxRole.DEFAULT_VALUE);
    if (declaration.defaultTree().isEmpty()) {
      if (!defaults.isEmpty() || executable.defaultValue().isPresent()) {
        report(declaration.source(), node.nodeId(), "frontend.unexpected-annotation-default",
            "An unwritten annotation default was retained as source syntax", node.origin());
      }
      return;
    }
    var expected = declaration.defaultTree().orElseThrow();
    if (defaults.size() != UNIQUE_OCCURRENCE_COUNT || executable.defaultValue().isEmpty()) {
      report(declaration.source(), node.nodeId(), "frontend.unrepresented-annotation-default",
          "A written annotation default has no complete declaration-owned source and value facts", expected.range());
      return;
    }
    var expression = defaults.getFirst();
    check(declaration.source(), expected, expression);
    if (!(element instanceof ExecutableElement selected) || selected.getDefaultValue() == null
        || !valueMatches(expected, executable.defaultValue().orElseThrow(), selected.getReturnType())
        || !new JavaAnnotationMirrorCorrelation(semantics).matchesValue(selected.getDefaultValue(), executable.defaultValue().orElseThrow(),
            expression)) {
      report(declaration.source(), expression.nodeId(), "frontend.inconsistent-annotation-default-value",
          "The written default disagrees with its attributed element value", expected.range());
    }
  }

  private void check(SourceFile source, JavaAnnotationSourceInventory.WrittenTree expected, SyntaxNode actual) {
    checked.add(actual.nodeId());
    var exactKind = expected.syntaxKind() == SyntaxKind.ANNOTATION ? JavaSourceKind.ANNOTATION : expected.sourceKind();
    if (!actual.sourceFileKey().equals(source.sourceFileKey()) || !actual.origin().equals(expected.range()) || expected.range().isEmpty()
        || actual.sourceKind() != exactKind || actual.syntaxKind() != expected.syntaxKind()) {
      mismatch(source, expected, actual, "source identity, range or kind");
      return;
    }
    if (expected.value() instanceof SyntaxValue.Annotation written) {
      var retained = actual.value(SyntaxValue.Annotation.class);
      if (written.form() != retained.form() || !written.arguments().equals(retained.arguments())) {
        mismatch(source, expected, actual, "written annotation form or ordered headers");
      }
      var tree = (AnnotationTree) expected.path().getLeaf();
      var typePath = new TreePath(expected.path(), tree.getAnnotationType());
      if (!semantics.reference(typePath, ReferenceKind.TYPE).equals(actual.semanticReference())) {
        mismatch(source, expected, actual, "annotation type reference");
      }
      var element = semantics.element(typePath);
      if (retained.location() == SyntaxValue.AnnotationLocation.UNRESOLVED && priorDiagnostics.stream()
          .noneMatch(diagnostic -> diagnostic.isError()
              && ("frontend.unresolved-annotation-type".equals(diagnostic.code())
                  || "frontend.unresolved-annotation-location".equals(diagnostic.code()))
              && diagnostic.origins().equals(actual.origin().stream().toList()))) {
        mismatch(source, expected, actual, "missing blocking annotation resolution outcome");
      }
      if (retained.location() != SyntaxValue.AnnotationLocation.UNRESOLVED && retained.location() != SyntaxValue.AnnotationLocation.VALUE
          && (!(element instanceof TypeElement type)
              || !JavaAnnotationProjector.applicableTargets(type).filter(retained.applicableTargets()::equals).isPresent())) {
        mismatch(source, expected, actual, "compiler-derived applicable targets");
      }
      if (retained.argumentFailureCode().isPresent()) {
        var code = retained.argumentFailureCode().orElseThrow();
        if (priorDiagnostics.stream().noneMatch(diagnostic -> diagnostic.isError() && diagnostic.code().equals(code)
            && diagnostic.origins().stream().anyMatch(span -> contains(actual.origin().orElseThrow(), span)))) {
          mismatch(source, expected, actual, "missing blocking argument outcome");
        }
        return;
      }
    } else if (actual.syntaxKind() == SyntaxKind.ANNOTATION) {
      mismatch(source, expected, actual, "unavailable written annotation form");
    } else if (actual.syntaxKind() != SyntaxKind.ANNOTATION_ARGUMENT) {
      checkExpression(source, expected, actual);
    }
    if (actual.children().size() != expected.children().size()) {
      mismatch(source, expected, actual, "written child inventory");
    }
    for (var branch : expected.children()) {
      var matches = actual.children().stream().filter(child -> child.role() == branch.role() && child.position() == branch.ordinal())
          .toList();
      if (matches.size() != UNIQUE_OCCURRENCE_COUNT) {
        mismatch(source, branch.tree(), actual, "child role or ordinal " + branch.role() + ':' + branch.ordinal());
      } else {
        check(source, branch.tree(), matches.getFirst().node());
      }
    }
    if (actual.syntaxKind() == SyntaxKind.ANNOTATION) {
      checkArguments(source, expected, actual);
    }
  }

  private void checkExpression(SourceFile source, JavaAnnotationSourceInventory.WrittenTree expected, SyntaxNode actual) {
    var packageQualifier = semantics.attributedType(expected.path()) instanceof JavaTypeAttribution.NoType noType
        && noType.kind() == TypeKind.PACKAGE;
    var expectedValue = packageQualifier && expected.value() instanceof SyntaxValue.SourceSymbol symbol
        ? new SyntaxValue.PackageQualifier(symbol.name())
        : expected.value();
    var expectedType = switch (actual.syntaxKind()) {
      case ANNOTATION_ELEMENT_NAME, ANNOTATION_ARRAY -> SyntaxType.NotApplicable.INSTANCE;
      case TYPE_USE -> packageQualifier ? SyntaxType.NotApplicable.INSTANCE : semantics.explicitType(expected.path());
      default -> semantics.type(expected.path());
    };
    if (!actual.value().equals(expectedValue) || !actual.type().equals(expectedType)) {
      mismatch(source, expected, actual, "written value or attributed expression type");
    }
    var reference = Optional.empty();
    if (actual.syntaxKind() == SyntaxKind.TYPE_USE && !packageQualifier) {
      if (!semantics.typeUseReference(expected.path()).equals(actual.semanticReference())) {
        mismatch(source, expected, actual, "type operand reference");
      }
    } else if (actual.syntaxKind() == SyntaxKind.ANNOTATION_NAME_REFERENCE) {
      var element = semantics.element(expected.path());
      if (!(element instanceof VariableElement variable)
          || !semantics.declarationKey(variable).equals(actual.semanticReference().flatMap(item -> item.symbolKey()))) {
        mismatch(source, expected, actual, "constant or enum declaration reference");
      }
    } else if (!actual.semanticReference().equals(reference)) {
      mismatch(source, expected, actual, "unexpected expression reference");
    }
  }

  private void checkArguments(SourceFile source, JavaAnnotationSourceInventory.WrittenTree expected, SyntaxNode actual) {
    var annotation = (AnnotationTree) expected.path().getLeaf();
    var selectedType = semantics.element(new TreePath(expected.path(), annotation.getAnnotationType()));
    for (var branch : expected.children()) {
      var candidates = actual.children(SyntaxRole.ANNOTATION_ARGUMENT);
      if (branch.ordinal() >= candidates.size() || !(selectedType instanceof TypeElement type)) {
        continue;
      }
      var argument = candidates.get(branch.ordinal());
      var retained = argument.value(SyntaxValue.AnnotationArgument.class);
      var writtenName = expected.value() instanceof SyntaxValue.Annotation written
          ? written.arguments().get(branch.ordinal()).explicitName()
          : Optional.<String>empty();
      var element = type.getEnclosedElements().stream()
          .filter(item -> item instanceof ExecutableElement && item.getSimpleName().contentEquals(writtenName.orElse("value")))
          .map(ExecutableElement.class::cast).findFirst();
      var valueTree = branch.tree().children().stream().filter(child -> child.role() == SyntaxRole.VALUE).findFirst().orElseThrow().tree();
      if (!retained.explicitName().equals(writtenName) || element.isEmpty()
          || !semantics.declarationKey(element.orElseThrow()).filter(retained.selectedElementKey()::equals).isPresent()
          || !argument.type().equals(semantics.declarationType(element.orElseThrow().getReturnType()))
          || !valueMatches(valueTree, retained.value(), element.orElseThrow().getReturnType())) {
        mismatch(source, branch.tree(), argument, "selected annotation element, type or value");
      }
    }
  }

  private boolean valueMatches(JavaAnnotationSourceInventory.WrittenTree tree, AnnotationElementValue value, TypeMirror expectedType) {
    if (expectedType instanceof ArrayType arrayType) {
      if (!(value instanceof AnnotationElementValue.Array array)) {
        return false;
      }
      var items = tree.syntaxKind() == SyntaxKind.ANNOTATION_ARRAY
          ? tree.children().stream().map(JavaAnnotationSourceInventory.Branch::tree).toList()
          : List.of(tree);
      if (items.size() != array.elements().size()) {
        return false;
      }
      for (var ordinal = 0; ordinal < items.size(); ordinal++) {
        if (!valueMatches(items.get(ordinal), array.elements().get(ordinal), arrayType.getComponentType())) {
          return false;
        }
      }
      return true;
    }
    if (expectedType instanceof DeclaredType declared && declared.asElement().getKind() == ElementKind.ANNOTATION_TYPE) {
      return value instanceof AnnotationElementValue.Nested nested
          && semantics.declarationKey(declared.asElement()).filter(nested.annotationTypeKey()::equals).isPresent();
    }
    return values.scalar(tree.path(), expectedType).filter(value::equals).isPresent();
  }

  private static boolean annotationFact(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case ANNOTATION, ANNOTATION_ARGUMENT, ANNOTATION_ELEMENT_NAME, ANNOTATION_ARRAY, ANNOTATION_NAME_REFERENCE, ANNOTATION_CLASS_LITERAL,
          ANNOTATION_CAST ->
        true;
      default -> false;
    };
  }

  private static boolean contains(SourceSpan owner, SourceSpan child) {
    return owner.sourceFileKey().equals(child.sourceFileKey()) && owner.startOffset() <= child.startOffset()
        && owner.endOffsetExclusive() >= child.endOffsetExclusive();
  }

  private static SourceFile source(JavaAstAggregate aggregate, String key) {
    return aggregate.snapshot().sourceFiles().stream().filter(source -> source.sourceFileKey().equals(key)).findFirst().orElseThrow();
  }

  private void mismatch(SourceFile source, JavaAnnotationSourceInventory.WrittenTree expected, SyntaxNode actual, String fact) {
    report(source, actual.nodeId(), "frontend.inconsistent-annotation-source-fact", "Annotation reconciliation disagrees on " + fact,
        expected.range());
  }

  private void report(SourceFile source, String context, String code, String message, Optional<SourceSpan> range) {
    TranslationDiagnosticEvidence.emit(reconciliationDiagnostics, "JavaAnnotationCompleteness", FrontendDiagnosticFactory
        .otherError(source.sourceFileKey() + ':' + context, code, message + " in " + source.relativePath(), range));
  }
}
