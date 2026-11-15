package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.AnnotationAttachmentAdmission;
import org.aquilagraph.source.api.JavaDeclarationModifier;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.admission.SharedVariableDeclarations;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.source.api.SyntaxValue.ExecutableDeclaration.ExecutableKind;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationTypeLocation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.Declaration;
import org.aquilagraph.source.api.SyntaxSemanticTarget.SourceOccurrence;

/** Exact reference and annotation relations for owned source occurrences. */
final class SourceSemanticValidation {
  private static final Comparator<SyntaxSemanticEdge> SEMANTIC_ORDER = Comparator.comparing(SyntaxSemanticEdge::sourceKey)
      .thenComparing(edge -> edge.relation().stableId()).thenComparing(SourceSemanticValidation::compareTargets);

  private SourceSemanticValidation() {
  }

  static List<SyntaxSemanticEdge> validate(List<SyntaxSemanticEdge> edges, Map<String, SyntaxNode> nodes,
      OccurrenceIndex<SyntaxNode> parents, Map<String, List<SyntaxNode>> executables, LexicalBindings bindings) {
    var seen = new HashSet<SyntaxSemanticEdge>();
    var declarationEdges = new HashMap<String, List<SyntaxSemanticEdge>>();
    var annotationTargets = new HashMap<String, List<SyntaxSemanticEdge>>();
    var recordTypeOwners = new HashMap<String, SyntaxNode>();
    var fieldGroups = SharedVariableDeclarations.index(List.copyOf(nodes.values()));
    for (var parent : nodes.values()) {
      if (parent.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION) {
        parent.children(SyntaxRole.DECLARED_TYPE).forEach(type -> collectRecordTypeOwners(type, parent, recordTypeOwners));
      }
    }
    for (var edge : edges) {
      Objects.requireNonNull(edge, "semanticEdge");
      if (!seen.add(edge)) {
        throw new IllegalArgumentException("Duplicate AST semantic edge tuple: " + edge);
      }
      if (!nodes.containsKey(edge.sourceKey())) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: semantic source " + edge.sourceKey());
      }
      if (edge.target() instanceof SourceOccurrence occurrence && !nodes.containsKey(occurrence.nodeKey())) {
        throw new IllegalArgumentException("AST edge endpoint is not owned by the aggregate: semantic target " + occurrence.nodeKey());
      }
      if (edge.target() instanceof SourceOccurrence occurrence
          && !isDeclaration(Objects.requireNonNull(nodes.get(occurrence.nodeKey()), "owned semantic target"))
          && !bindings.isBindingDeclaration(Objects.requireNonNull(nodes.get(occurrence.nodeKey())))) {
        throw new IllegalArgumentException("AST semantic target must identify a declaration occurrence: " + occurrence.nodeKey());
      }
      if (edge.relation() == SyntaxSemanticRelation.DECLARATION_REFERENCE) {
        appendEdge(declarationEdges, edge);
      } else if (edge.relation() == SyntaxSemanticRelation.ANNOTATED_TARGET) {
        appendEdge(annotationTargets, edge);
      }
    }
    for (var node : nodes.values()) {
      if (!seen.containsAll(node.semanticEdges())) {
        throw new IllegalArgumentException("AST semantic edges omit an owned node reference: " + node.nodeId());
      }
      validateDeclarationEdge(node, declarationEdges.getOrDefault(node.nodeId(), List.of()), nodes, bindings);
      validateMemberReferenceSelection(node, executables, nodes);
      var targets = annotationTargets.getOrDefault(node.nodeId(), List.of());
      if (node.sourceKind() == JavaSourceKind.ANNOTATION && node.value() instanceof SyntaxValue.Annotation annotation
          && annotation.location() != SyntaxValue.AnnotationLocation.UNRESOLVED
          && annotation.location() != SyntaxValue.AnnotationLocation.VALUE) {
        if (node.semanticReference().orElseThrow().status() != ResolutionStatus.RESOLVED) {
          throw new IllegalArgumentException("AST applied annotation requires a resolved annotation type: " + node.nodeId());
        }
        if (targets.size() != 1 || !(targets.getFirst().target() instanceof AnnotationApplication application)) {
          throw new IllegalArgumentException("AST annotation requires exactly one typed application: " + node.nodeId());
        }
        var parent = parents.get(node);
        if (parent == null || node.structuralPath().steps().getLast().role() != SyntaxRole.ANNOTATION
            || !application.equals(AnnotationAttachmentAdmission.forAttachment(parent, node,
                Optional.ofNullable(recordTypeOwners.get(parent.nodeId())), fieldGroups.getOrDefault(parent.nodeId(), List.of())))) {
          throw new IllegalArgumentException("AST annotation application does not match its exact source location: " + node.nodeId());
        }
      } else if (!targets.isEmpty()) {
        throw new IllegalArgumentException("AST annotation target relation has a nonannotation source: " + node.nodeId());
      }
    }
    return edges.stream().sorted(SEMANTIC_ORDER).toList();
  }

  private static void collectRecordTypeOwners(SyntaxNode type, SyntaxNode component, Map<String, SyntaxNode> owners) {
    owners.put(type.nodeId(), component);
    type.children().stream().map(SyntaxChild::node).filter(child -> child.syntaxKind() == SyntaxKind.TYPE_USE)
        .forEach(child -> collectRecordTypeOwners(child, component, owners));
  }

  private static void validateMemberReferenceSelection(SyntaxNode node, Map<String, List<SyntaxNode>> ownedExecutables,
      Map<String, SyntaxNode> nodes) {
    if (!(node.value() instanceof SyntaxValue.MemberReference reference) || node.semanticReference().isEmpty()
        || node.requiredReference().symbolKey().isEmpty()) {
      return;
    }
    for (var declaration : ownedExecutables.getOrDefault(node.requiredReference().symbolKey().orElseThrow(), List.of())) {
      var executable = declaration.value(SyntaxValue.ExecutableDeclaration.class);
      if (node.requiredReference().referenceKind() != executableReferenceKind(executable.kind())) {
        throw new IllegalArgumentException("A member reference must agree with its owned executable kind: " + node.nodeId());
      }
      CanonicalMethodCorrespondence.validateSelection(reference.selectedType(), declaration, nodes);
      if (node.requiredReference().referenceKind() != ReferenceKind.METHOD) {
        validateConstructedTypeSelection(node.requiredChild(SyntaxRole.QUALIFIER), executable.ownerKey());
        continue;
      }
      if (!reference.name().equals(executable.name())) {
        throw new IllegalArgumentException("A member reference must agree with its owned executable name: " + node.nodeId());
      }
      if (reference.staticMember() != executable.effectiveModifiers().contains(JavaDeclarationModifier.STATIC)) {
        throw new IllegalArgumentException("A member reference must agree with its owned executable static modifier: " + node.nodeId());
      }
    }
  }

  private static void validateConstructedTypeSelection(SyntaxNode qualifier, String ownerKey) {
    if (qualifier.semanticReference().filter(reference -> reference.status() == ResolutionStatus.RESOLVED
        && reference.referenceKind() == ReferenceKind.TYPE && !reference.symbolKey().equals(Optional.of(ownerKey))).isPresent()) {
      throw new IllegalArgumentException("A member reference must agree with its owned constructor type: " + qualifier.nodeId());
    }
    if (qualifier.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      validateConstructedTypeSelection(qualifier.requiredChild(SyntaxRole.UNDERLYING_TYPE), ownerKey);
    } else if (qualifier.sourceKind() == JavaSourceKind.PARAMETERIZED_TYPE) {
      validateConstructedTypeSelection(qualifier.requiredChild(SyntaxRole.TYPE_NAME), ownerKey);
    }
  }

  private static void appendEdge(Map<String, List<SyntaxSemanticEdge>> indexedEdges, SyntaxSemanticEdge edge) {
    indexedEdges.computeIfAbsent(edge.sourceKey(), ignored -> new ArrayList<>()).add(edge);
  }

  private static boolean isDeclaration(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case TYPE_DECLARATION, TYPE_PARAMETER_DECLARATION, METHOD_DECLARATION, FIELD_DECLARATION, ENUM_CONSTANT_DECLARATION,
          RECORD_COMPONENT_DECLARATION, PARAMETER_DECLARATION, LOCAL_DECLARATION ->
        true;
      default -> false;
    };
  }

  private static void validateDeclarationEdge(SyntaxNode node, List<SyntaxSemanticEdge> declarationEdges, Map<String, SyntaxNode> nodes,
      LexicalBindings bindings) {
    if (switch (node.syntaxKind()) {
      case INVOCATION, OBJECT_CREATION, MEMBER_REFERENCE, VARIABLE_ACCESS, FIELD_ACCESS, LOCAL_DECLARATION -> true;
      default -> false;
    }) {
      var selected = declarationEdges.stream().filter(edge -> edge.target() instanceof Declaration).toList();
      if (!selected.equals(node.semanticEdges())
          || declarationEdges.stream().anyMatch(edge -> edge.target() instanceof SourceOccurrence occurrence
              && !sameSelectedDeclaration(node, Objects.requireNonNull(nodes.get(occurrence.nodeKey()), "owned semantic target")))) {
        throw new IllegalArgumentException("AST executable declaration reference must match its selected target: " + node.nodeId());
      }
    }
    for (var edge : declarationEdges) {
      if (edge.target() instanceof SourceOccurrence occurrence) {
        bindings.validateSelectedOccurrence(node, Objects.requireNonNull(nodes.get(occurrence.nodeKey())));
      } else if (edge.target() instanceof Declaration target
          && (target.reference().referenceKind() == ReferenceKind.LOCAL || target.reference().referenceKind() == ReferenceKind.PARAMETER)
          && !node.semanticEdges().contains(edge) && declaredTarget(node).filter(target::equals).isEmpty()) {
        throw new IllegalArgumentException("A binding declaration reference must agree with its owned source selection");
      }
    }
    if (node.value() instanceof SyntaxValue.Annotation && !declarationEdges.equals(node.semanticEdges())) {
      throw new IllegalArgumentException("AST annotation type reference must match its selected target: " + node.nodeId());
    }
    if ((node.syntaxKind() == SyntaxKind.TYPE_USE || node.syntaxKind() == SyntaxKind.MODULE_TYPE_REFERENCE)
        && !declarationEdges.equals(node.semanticEdges())) {
      throw new IllegalArgumentException("AST type-use declaration reference must match its selected target: " + node.nodeId());
    }
    if (switch (node.syntaxKind()) {
      case ANNOTATION_ARGUMENT, ANNOTATION_ELEMENT_NAME, ANNOTATION_NAME_REFERENCE, ANNOTATION_CLASS_LITERAL, ANNOTATION_CAST -> true;
      default -> false;
    } && !declarationEdges.equals(node.semanticEdges())) {
      throw new IllegalArgumentException("AST annotation value reference must match its selected target: " + node.nodeId());
    }
    var declaration = declaredTarget(node);
    if (declaration.isEmpty()) {
      return;
    }
    if (declarationEdges.size() != 1 || !declarationEdges.getFirst().target().equals(declaration.orElseThrow())) {
      throw new IllegalArgumentException("AST declaration reference must match its source declaration: " + node.nodeId());
    }
  }

  private static Optional<Declaration> declaredTarget(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case TYPE_DECLARATION ->
        Optional.of(declarationTarget(ReferenceKind.TYPE, node.value(SyntaxValue.TypeDeclaration.class).declarationKey()));
      case TYPE_PARAMETER_DECLARATION -> Optional
          .of(declarationTarget(ReferenceKind.TYPE_PARAMETER, node.value(SyntaxValue.TypeParameterDeclaration.class).declarationKey()));
      case METHOD_DECLARATION -> {
        var value = node.value(SyntaxValue.ExecutableDeclaration.class);
        yield Optional.of(declarationTarget(executableReferenceKind(value.kind()), value.declarationKey()));
      }
      case FIELD_DECLARATION ->
        Optional.of(declarationTarget(ReferenceKind.FIELD, node.value(SyntaxValue.VariableDeclaration.class).declarationKey()));
      case ENUM_CONSTANT_DECLARATION ->
        Optional.of(declarationTarget(ReferenceKind.ENUM_CONSTANT, node.value(SyntaxValue.VariableDeclaration.class).declarationKey()));
      case RECORD_COMPONENT_DECLARATION -> Optional
          .of(declarationTarget(ReferenceKind.RECORD_COMPONENT, node.value(SyntaxValue.RecordComponentDeclaration.class).declarationKey()));
      case PARAMETER_DECLARATION ->
        Optional.of(declarationTarget(ReferenceKind.PARAMETER, node.value(SyntaxValue.ParameterDeclaration.class).declarationKey()));
      default -> Optional.<Declaration>empty();
    };
  }

  private static ReferenceKind executableReferenceKind(ExecutableKind kind) {
    return switch (kind) {
      case CONSTRUCTOR, COMPACT_CONSTRUCTOR -> ReferenceKind.CONSTRUCTOR;
      case METHOD, ANNOTATION_ELEMENT -> ReferenceKind.METHOD;
    };
  }

  private static boolean sameSelectedDeclaration(SyntaxNode source, SyntaxNode target) {
    return source.semanticReference()
        .filter(reference -> declaredTarget(target).map(Declaration::reference).or(target::semanticReference)
            .filter(declaration -> declaration.referenceKind() == reference.referenceKind()
                && declaration.symbolKey().equals(reference.symbolKey()))
            .isPresent())
        .isPresent();
  }

  private static Declaration declarationTarget(ReferenceKind kind, String key) {
    return new Declaration(
        new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(), List.of(), List.of(), Optional.empty()));
  }

  private static int compareTargets(SyntaxSemanticEdge left, SyntaxSemanticEdge right) {
    var first = left.target();
    var second = right.target();
    if (first instanceof SourceOccurrence source) {
      if (second instanceof SourceOccurrence other) {
        return source.nodeKey().compareTo(other.nodeKey());
      }
      return -1;
    }
    if (second instanceof SourceOccurrence) {
      return 1;
    }
    if (first instanceof Declaration leftDeclaration) {
      return second instanceof Declaration rightDeclaration
          ? compareReferences(leftDeclaration.reference(), rightDeclaration.reference())
          : -1;
    }
    if (second instanceof Declaration) {
      return 1;
    }
    var leftApplication = (AnnotationApplication) first;
    var rightApplication = (AnnotationApplication) second;
    var result = leftApplication.ownerNodeKey().compareTo(rightApplication.ownerNodeKey());
    if (result == 0) {
      result = compareOptional(leftApplication.declarationNodeKey(), rightApplication.declarationNodeKey());
    }
    if (result == 0) {
      result = compareTypeLocation(leftApplication.typeLocation(), rightApplication.typeLocation());
    }
    for (var index = 0; result == 0
        && index < Math.min(leftApplication.propagatedCounterparts().size(), rightApplication.propagatedCounterparts().size()); index++) {
      var leftCounterpart = leftApplication.propagatedCounterparts().get(index);
      var rightCounterpart = rightApplication.propagatedCounterparts().get(index);
      result = compareReferences(leftCounterpart.reference(), rightCounterpart.reference());
      if (result == 0) {
        result = Boolean.compare(leftCounterpart.declarationApplicable(), rightCounterpart.declarationApplicable());
      }
      if (result == 0) {
        result = compareTypeLocation(leftCounterpart.typeLocation(), rightCounterpart.typeLocation());
      }
    }
    if (result == 0) {
      result = Integer.compare(leftApplication.propagatedCounterparts().size(), rightApplication.propagatedCounterparts().size());
    }
    for (var index = 0; result == 0
        && index < Math.min(leftApplication.fieldDeclarators().size(), rightApplication.fieldDeclarators().size()); index++) {
      var leftField = leftApplication.fieldDeclarators().get(index);
      var rightField = rightApplication.fieldDeclarators().get(index);
      result = leftField.fieldNodeKey().compareTo(rightField.fieldNodeKey());
      if (result == 0) {
        result = Boolean.compare(leftField.declarationApplicable(), rightField.declarationApplicable());
      }
      if (result == 0) {
        result = compareTypeLocation(leftField.typeLocation(), rightField.typeLocation());
      }
    }
    return result == 0 ? Integer.compare(leftApplication.fieldDeclarators().size(), rightApplication.fieldDeclarators().size()) : result;
  }

  private static int compareTypeLocation(Optional<AnnotationTypeLocation> left, Optional<AnnotationTypeLocation> right) {
    if (left.isEmpty()) {
      return right.isEmpty() ? 0 : -1;
    }
    if (right.isEmpty()) {
      return 1;
    }
    if (left.orElseThrow() instanceof AnnotationTypeLocation.Written written) {
      return right.orElseThrow() instanceof AnnotationTypeLocation.Written other ? written.nodeKey().compareTo(other.nodeKey()) : -1;
    }
    if (right.orElseThrow() instanceof AnnotationTypeLocation.Written) {
      return 1;
    }
    if (left.orElseThrow() instanceof AnnotationTypeLocation.ArrayDimension dimension) {
      if (!(right.orElseThrow() instanceof AnnotationTypeLocation.ArrayDimension other)) {
        return -1;
      }
      var result = dimension.nodeKey().compareTo(other.nodeKey());
      return result == 0 ? Integer.compare(dimension.dimension(), other.dimension()) : result;
    }
    if (right.orElseThrow() instanceof AnnotationTypeLocation.ArrayDimension) {
      return 1;
    }
    return ((AnnotationTypeLocation.Constructed) left.orElseThrow()).declarationKey()
        .compareTo(((AnnotationTypeLocation.Constructed) right.orElseThrow()).declarationKey());
  }

  private static int compareReferences(SemanticReference left, SemanticReference right) {
    var result = left.referenceKind().name().compareTo(right.referenceKind().name());
    if (result == 0) {
      result = left.status().name().compareTo(right.status().name());
    }
    if (result == 0) {
      result = compareOptional(left.symbolKey(), right.symbolKey());
    }
    if (result == 0) {
      result = compareOptional(left.typeName(), right.typeName());
    }
    if (result == 0) {
      result = compareStrings(left.typeAlternativeNames(), right.typeAlternativeNames());
    }
    if (result == 0) {
      result = compareStrings(left.assignableToTypeNames(), right.assignableToTypeNames());
    }
    if (result != 0 || (left.declarationIdentity().isEmpty() && right.declarationIdentity().isEmpty())) {
      return result;
    }
    if (left.declarationIdentity().isEmpty() || right.declarationIdentity().isEmpty()) {
      return left.declarationIdentity().isEmpty() ? -1 : 1;
    }
    var leftIdentity = left.declarationIdentity().orElseThrow();
    var rightIdentity = right.declarationIdentity().orElseThrow();
    result = compareOptional(leftIdentity.moduleName(), rightIdentity.moduleName());
    return result == 0 ? leftIdentity.binaryName().compareTo(rightIdentity.binaryName()) : result;
  }

  private static int compareOptional(Optional<String> left, Optional<String> right) {
    if (left.isEmpty()) {
      return right.isEmpty() ? 0 : -1;
    }
    return right.isEmpty() ? 1 : left.orElseThrow().compareTo(right.orElseThrow());
  }

  private static int compareStrings(List<String> left, List<String> right) {
    for (var index = 0; index < Math.min(left.size(), right.size()); index++) {
      var result = left.get(index).compareTo(right.get(index));
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(left.size(), right.size());
  }

}
