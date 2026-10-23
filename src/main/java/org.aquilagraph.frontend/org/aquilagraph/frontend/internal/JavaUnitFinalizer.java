package org.aquilagraph.frontend.internal;

import org.aquilagraph.source.admission.AnnotationAttachmentAdmission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.admission.SharedVariableDeclarations;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;

/** Assembles one detached source contribution and its retained unit evidence. */
final class JavaUnitFinalizer {
  private final JavaBodySourceCorrelation bodyCorrelation;
  private final JavaWrittenSourceAssociations sourceAssociations;
  private final JavaDeclarationScope declarations;
  private boolean finalized;

  JavaUnitFinalizer(JavaBodySourceCorrelation bodyCorrelation, JavaWrittenSourceAssociations sourceAssociations,
      JavaDeclarationScope declarations) {
    this.bodyCorrelation = bodyCorrelation;
    this.sourceAssociations = sourceAssociations;
    this.declarations = declarations;
  }

  FinalizedUnit finish(SourceFile sourceFile, Optional<SyntaxNode> projected, UnitDeclarations metadata, DiagnosticEmissions emissions) {
    if (finalized) {
      throw new IllegalStateException("Unit source payload has already been finalized");
    }
    finalized = true;
    if (projected.isEmpty()) {
      return new FinalizedUnit(Optional.empty(), List.of(), emissions, List.of(), List.of(), List.of(), List.of());
    }
    var root = projected.orElseThrow();
    var nodes = root.descendantsAndSelf();
    var structural = nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList();
    var semantic = new ArrayList<>(bodyCorrelation.declarationEdges(declarations));
    nodes.forEach(node -> semantic.addAll(node.semanticEdges()));
    var recordTypeOwners = new HashMap<String, SyntaxNode>();
    var fieldGroups = SharedVariableDeclarations.index(nodes);
    nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION).forEach(component -> component
        .children(SyntaxRole.DECLARED_TYPE).forEach(type -> collectRecordTypeOwners(type, component, recordTypeOwners)));
    nodes.forEach(parent -> parent.children().stream()
        .filter(child -> child.role() == SyntaxRole.ANNOTATION && child.node().value() instanceof SyntaxValue.Annotation annotation
            && annotation.location() != SyntaxValue.AnnotationLocation.VALUE
            && annotation.location() != SyntaxValue.AnnotationLocation.UNRESOLVED)
        .forEach(child -> semantic.add(new SyntaxSemanticEdge(child.node().nodeId(), SyntaxSemanticRelation.ANNOTATED_TARGET,
            AnnotationAttachmentAdmission.forAttachment(parent, child.node(), Optional.ofNullable(recordTypeOwners.get(parent.nodeId())),
                fieldGroups.getOrDefault(parent.nodeId(), List.of()))))));
    var contribution = new SyntaxSourceContribution(sourceFile.sourceFileKey(), root, nodes, structural, semantic);
    var contributionValue = Optional.of(contribution);
    return new FinalizedUnit(contributionValue, bodyCorrelation.registrations(sourceFile), emissions, sourceAssociations.forNodes(nodes),
        bodyCorrelation.sealedScopes(),
        metadata.declarationTypes().stream()
            .filter(evidence -> retainsTypeOwner(contribution, evidence.sourceFileKey(), evidence.ownerTypeKey())).toList(),
        metadata.memberOrders().stream().filter(order -> retainsTypeOwner(contribution, order.sourceFileKey(), order.ownerTypeKey()))
            .map(order -> attachWrittenOrder(order, contribution)).toList());
  }

  private static void collectRecordTypeOwners(SyntaxNode type, SyntaxNode component, Map<String, SyntaxNode> owners) {
    owners.put(type.nodeId(), component);
    type.children().stream().map(SyntaxChild::node).filter(child -> child.syntaxKind() == SyntaxKind.TYPE_USE)
        .forEach(child -> collectRecordTypeOwners(child, component, owners));
  }

  private static boolean retainsTypeOwner(SyntaxSourceContribution source, String sourceFileKey, String ownerTypeKey) {
    return source.nodes().stream().filter(node -> node.sourceFileKey().equals(sourceFileKey)).anyMatch(
        node -> (node.value() instanceof SyntaxValue.TypeDeclaration declaration && declaration.declarationKey().equals(ownerTypeKey))
            || (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT
                && node.children(SyntaxRole.COMPACT_MEMBER).stream()
                    .anyMatch(member -> member.value() instanceof SyntaxValue.ExecutableDeclaration declaration
                        && declaration.ownerKey().equals(ownerTypeKey))));
  }

  private static DeclarationMemberOrder attachWrittenOrder(DeclarationMemberOrder order, SyntaxSourceContribution source) {
    var owner = source.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration declaration
        && declaration.declarationKey().equals(order.ownerTypeKey())).findFirst();
    var members = owner.map(node -> node.children(SyntaxRole.MEMBER)).orElseGet(() -> source.root().children(SyntaxRole.COMPACT_MEMBER));
    var positions = new HashMap<String, Integer>();
    for (var index = 0; index < members.size(); index++) {
      var key = sourceMemberKey(members.get(index));
      if (key.isPresent()) {
        positions.put(key.orElseThrow(), index);
      }
    }
    return new DeclarationMemberOrder(order.sourceFileKey(), order.ownerTypeKey(),
        order.members().stream().map(member -> new DeclarationMemberOrder.Member(member.declarationKey(), member.kind(),
            member.displayValue(), member.provenance(), Optional.ofNullable(positions.get(member.declarationKey())))).toList(),
        order.attributedTreeOrder());
  }

  private static Optional<String> sourceMemberKey(SyntaxNode member) {
    return switch (member.value()) {
      case SyntaxValue.TypeDeclaration type -> Optional.of(type.declarationKey());
      case SyntaxValue.ExecutableDeclaration method -> Optional.of(method.declarationKey());
      case SyntaxValue.VariableDeclaration field -> Optional.of(field.declarationKey());
      default -> Optional.empty();
    };
  }

}
