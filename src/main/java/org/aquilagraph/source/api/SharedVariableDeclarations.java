package org.aquilagraph.source.api;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Validates and indexes source declarators that share one written variable header. */
public final class SharedVariableDeclarations {
  private static final int UNIQUE_SOURCE_OWNER_COUNT = 1;
  private SharedVariableDeclarations() {
  }

  /**
   * Indexes each shared declarator and its type contexts by their source group.
   *
   * @param nodes
   *          owned source nodes
   * @return immutable source-ordered groups indexed by field and type node identities
   */
  public static Map<String, List<SyntaxNode>> index(List<SyntaxNode> nodes) {
    Objects.requireNonNull(nodes, "nodes");
    var groups = new HashMap<String, List<SyntaxNode>>();
    for (var parent : nodes) {
      var declarations = parent.children().stream().map(SyntaxChild::node)
          .filter(node -> node.syntaxKind() == SyntaxKind.FIELD_DECLARATION || node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION)
          .collect(Collectors.groupingBy(node -> node.origin().orElseThrow().startOffset(), LinkedHashMap::new, Collectors.toList()));
      for (var group : declarations.values()) {
        if (group.getFirst().syntaxKind() == SyntaxKind.LOCAL_DECLARATION) {
          registerLocals(group, groups);
          continue;
        }
        var keys = group.size() > 1
            ? group.stream().map(node -> node.value(SyntaxValue.VariableDeclaration.class).declarationKey()).toList()
            : List.<String>of();
        var first = group.getFirst();
        var header = first.value(SyntaxValue.VariableDeclaration.class);
        var previousEnd = first.origin().orElseThrow().startOffset();
        for (var field : group) {
          var facts = field.value(SyntaxValue.VariableDeclaration.class);
          if (!facts.sharedDeclaratorKeys().equals(keys) || !facts.ownerKey().equals(header.ownerKey())
              || !facts.writtenModifiers().equals(header.writtenModifiers()) || !field.sourceFileKey().equals(first.sourceFileKey())
              || field.origin().orElseThrow().endOffsetExclusive() <= previousEnd
              || (!field.equals(first) && !field.children(SyntaxRole.ANNOTATION).isEmpty())) {
            throw new IllegalArgumentException("Shared field declarations disagree with their source header: " + field.nodeId());
          }
          previousEnd = field.origin().orElseThrow().endOffsetExclusive();
          if (!keys.isEmpty()) {
            var immutableGroup = List.copyOf(group);
            groups.put(field.nodeId(), immutableGroup);
            field.children(SyntaxRole.DECLARED_TYPE).forEach(type -> type.descendantsAndSelf().stream()
                .filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE).forEach(node -> groups.put(node.nodeId(), immutableGroup)));
          }
        }
      }
    }
    for (var node : nodes) {
      if (node.value() instanceof SyntaxValue.VariableDeclaration field && !field.sharedDeclaratorKeys().isEmpty()
          && !groups.containsKey(node.nodeId())) {
        throw new IllegalArgumentException("A shared field declarator requires its owned source siblings");
      }
      if (!(node.value() instanceof SyntaxValue.SharedTypeAnnotations shared)) {
        continue;
      }
      var group = groups.getOrDefault(node.nodeId(), List.of());
      var fieldIndex = fieldIndex(group, node);
      if (node.sourceKind() != JavaSourceKind.ANNOTATED_TYPE || fieldIndex < 1) {
        throw new IllegalArgumentException("Shared type annotations require a later declarator of the same source header");
      }
      var physicalOwners = group.subList(0, fieldIndex).stream().flatMap(field -> field.children(SyntaxRole.DECLARED_TYPE).stream())
          .flatMap(type -> type.descendantsAndSelf().stream())
          .filter(owner -> !owner.children(SyntaxRole.ANNOTATION).isEmpty() && sameTypePosition(owner, node)).toList();
      if (physicalOwners.size() != UNIQUE_SOURCE_OWNER_COUNT) {
        throw new IllegalArgumentException("A shared type annotation requires its unique earlier typed source position");
      }
      requireCompleteAnnotations(physicalOwners.getFirst(), node, shared);
    }
    for (var group : groups.values().stream().distinct().toList()) {
      for (var field : group) {
        var references = field.children(SyntaxRole.DECLARED_TYPE).stream().flatMap(type -> type.descendantsAndSelf().stream())
            .filter(node -> node.value() instanceof SyntaxValue.SharedTypeAnnotations)
            .flatMap(node -> node.value(SyntaxValue.SharedTypeAnnotations.class).annotationNodeKeys().stream()).toList();
        if (references.stream().distinct().count() != references.size()) {
          throw new IllegalArgumentException("A shared type annotation requires one type context per field declarator");
        }
      }
    }
    return Map.copyOf(groups);
  }

  private static void registerLocals(List<SyntaxNode> group, Map<String, List<SyntaxNode>> groups) {
    var first = group.getFirst();
    var expectedAnnotations = first.children(SyntaxRole.ANNOTATION).stream().map(SyntaxNode::nodeId).toList();
    for (var index = 0; index < group.size(); index++) {
      var ordinal = index;
      var local = group.get(index);
      var facts = local.value(SyntaxValue.LocalDeclaration.class);
      if (!facts.writtenModifiers().equals(first.value(SyntaxValue.LocalDeclaration.class).writtenModifiers())
          || (index > 0 && !local.children(SyntaxRole.ANNOTATION).isEmpty())
          || (index > 0
              && local.origin().orElseThrow().endOffsetExclusive() <= group.get(index - 1).origin().orElseThrow().endOffsetExclusive())
          || facts.bindingEvidence()
              .filter(binding -> !binding.sharedAnnotationKeys().equals(ordinal == 0 ? List.of() : expectedAnnotations)).isPresent()) {
        throw new IllegalArgumentException("Shared local declarations disagree with their written header");
      }
      if (group.size() > UNIQUE_SOURCE_OWNER_COUNT) {
        groups.put(local.nodeId(), List.copyOf(group));
        local.children(SyntaxRole.DECLARED_TYPE).forEach(type -> type.descendantsAndSelf().stream()
            .filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE).forEach(node -> groups.put(node.nodeId(), List.copyOf(group))));
      }
    }
  }

  private static int fieldIndex(List<SyntaxNode> group, SyntaxNode node) {
    for (var index = 0; index < group.size(); index++) {
      if (group.get(index).children(SyntaxRole.DECLARED_TYPE).stream().anyMatch(type -> type.descendantsAndSelf().contains(node))) {
        return index;
      }
    }
    return -1;
  }

  /**
   * Finds the independently corresponding common type context in another declarator.
   *
   * @param physicalOwner
   *          type context owning the physical source annotations
   * @param field
   *          written field declarator
   * @return corresponding context, empty for the owner itself or a declarator-specific source position
   */
  public static Optional<SyntaxNode> commonTypeContext(SyntaxNode physicalOwner, SyntaxNode field) {
    Objects.requireNonNull(physicalOwner, "physicalOwner");
    Objects.requireNonNull(field, "field");
    var matches = field.children(SyntaxRole.DECLARED_TYPE).stream().flatMap(type -> type.descendantsAndSelf().stream())
        .filter(node -> sameTypePosition(physicalOwner, node)).toList();
    if (matches.size() > UNIQUE_SOURCE_OWNER_COUNT) {
      throw new IllegalArgumentException("A shared type annotation requires an unambiguous typed source position");
    }
    if (matches.isEmpty() || matches.getFirst().nodeId().equals(physicalOwner.nodeId())) {
      return Optional.empty();
    }
    var context = matches.getFirst();
    if (!(context.value() instanceof SyntaxValue.SharedTypeAnnotations shared)) {
      throw new IllegalArgumentException("A common field type context requires shared source annotation references");
    }
    requireCompleteAnnotations(physicalOwner, context, shared);
    return Optional.of(context);
  }

  private static boolean sameTypePosition(SyntaxNode first, SyntaxNode second) {
    return first.sourceKind() == JavaSourceKind.ANNOTATED_TYPE && second.sourceKind() == JavaSourceKind.ANNOTATED_TYPE
        && first.origin().equals(second.origin()) && first.type().equals(second.type());
  }

  private static void requireCompleteAnnotations(SyntaxNode physicalOwner, SyntaxNode context, SyntaxValue.SharedTypeAnnotations shared) {
    var expected = physicalOwner.children(SyntaxRole.ANNOTATION).stream().map(SyntaxNode::nodeId).toList();
    if (!context.children(SyntaxRole.ANNOTATION).isEmpty() || !expected.equals(shared.annotationNodeKeys())) {
      throw new IllegalArgumentException("A shared type annotation context requires the complete ordered physical annotation set");
    }
  }
}
