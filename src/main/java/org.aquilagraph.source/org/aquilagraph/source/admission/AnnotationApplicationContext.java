package org.aquilagraph.source.admission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticTarget.AnnotationApplication;

/** Immutable source topology context for deriving annotation applications independently at each boundary. */
public final class AnnotationApplicationContext {
  private final Map<String, List<SyntaxNode>> sharedDeclarations;
  private final Map<String, SyntaxNode> recordTypeOwners;

  /**
   * Prepares annotation context and validates shared headers, including declarations without annotations.
   *
   * @param occurrences
   *          complete detached occurrence collection with established structural ownership
   * @throws IllegalArgumentException
   *           if shared declaration headers are inconsistent
   */
  public AnnotationApplicationContext(List<SyntaxNode> occurrences) {
    var nodes = List.copyOf(occurrences);
    sharedDeclarations = SharedVariableDeclarations.index(nodes);
    var owners = new HashMap<String, SyntaxNode>();
    nodes.stream().filter(node -> node.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION).forEach(
        component -> component.children(SyntaxRole.DECLARED_TYPE).forEach(type -> collectRecordTypeOwners(type, component, owners)));
    recordTypeOwners = Map.copyOf(owners);
  }

  /**
   * Determines whether an occurrence requires an application without deriving its destinations.
   *
   * @param node
   *          source occurrence
   * @return whether the occurrence is an annotation outside a nested value or unresolved location
   */
  public boolean requiresApplication(SyntaxNode node) {
    return AnnotationAttachmentAdmission.requiresApplication(node);
  }

  /**
   * Derives an eligible annotation's exact destinations after the caller has checked its references and ownership.
   *
   * @param parent
   *          actual structural parent in the prepared occurrence collection
   * @param annotation
   *          annotation attached to that parent
   * @return the complete typed application, including shared declarators and record counterparts
   * @throws IllegalArgumentException
   *           if the annotation is ineligible or its destinations contradict its source context
   */
  public AnnotationApplication forAttachment(SyntaxNode parent, SyntaxNode annotation) {
    return AnnotationAttachmentAdmission.forAttachment(parent, annotation, Optional.ofNullable(recordTypeOwners.get(parent.nodeId())),
        sharedDeclarations.getOrDefault(parent.nodeId(), List.of()));
  }

  private static void collectRecordTypeOwners(SyntaxNode type, SyntaxNode component, Map<String, SyntaxNode> owners) {
    owners.put(type.nodeId(), component);
    type.children().stream().map(SyntaxChild::node).filter(child -> child.syntaxKind() == SyntaxKind.TYPE_USE)
        .forEach(child -> collectRecordTypeOwners(child, component, owners));
  }
}
