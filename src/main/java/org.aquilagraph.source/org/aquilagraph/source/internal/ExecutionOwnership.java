package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxNodeKey;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.SourceFile;

/** Exact execution ownership and the validated links between written and auxiliary trees. */
record ExecutionOwnership(List<ExecutionRegion> regions, OccurrenceIndex<SyntaxNode> parents, OccurrenceIndex<String> owners,
    List<SyntaxNode> allNodes) {
  static ExecutionOwnership resolve(SourceTopology topology, SourceDeclarations declarations, Map<String, SourceFile> sources,
      Map<String, ExecutionRegistration> registrations, Map<String, DeclarationMemberOrder> memberOrders) {
    var regions = new ArrayList<ExecutionRegion>();
    var compilerAddresses = new HashSet<SyntaxNodeKey>();
    var rootOwners = new OccurrenceIndex.Builder<String>();
    var owners = new OccurrenceIndex.Builder<String>();
    var auxiliaryOwners = new OccurrenceIndex.Builder<String>();
    var parents = topology.parents().extend();
    var allNodes = new ArrayList<>(topology.orderedNodes());
    var memberSources = new HashMap<String, Set<String>>();
    for (var order : memberOrders.values()) {
      for (var member : order.members()) {
        indexMemberSource(memberSources, member.declarationKey(), order.sourceFileKey());
      }
    }
    for (var registration : registrations.values()) {
      var region = ExecutionRegion.resolve(registration, compilerAddresses);
      var facts = region.facts();
      var key = facts.scope().key();
      if (facts.scope() instanceof ExecutionScope.Method) {
        var executables = declarations.executables(key);
        validateMethod(region, topology, sources, executables, memberSources.getOrDefault(key, Set.of()), rootOwners, auxiliaryOwners);
        if (facts.methodOrigin().orElseThrow() != MethodBodyOrigin.SOURCE_AST) {
          allNodes.addAll(region.descendants());
          for (var node : region.descendants()) {
            node.children().forEach(child -> parents.putIfAbsent(child.node(), node));
          }
        }
      } else {
        validateDeclarationScope(facts, topology, declarations, memberOrders);
      }
      regions.add(region);
    }
    // An independently registered nested execution owns its occurrences before any enclosing same-tree region.
    regions.sort(Comparator.comparingInt((ExecutionRegion region) -> region.facts().root().structuralPath().steps().size()).reversed());
    for (var region : regions) {
      region.nodes().values().forEach(node -> owners.putIfAbsent(node, region.facts().scope().key()));
    }
    return new ExecutionOwnership(List.copyOf(regions), parents.freeze(), owners.freeze(), List.copyOf(allNodes));
  }

  private static void indexMemberSource(Map<String, Set<String>> memberSources, String declarationKey, String sourceFileKey) {
    memberSources.computeIfAbsent(declarationKey, ignored -> new HashSet<>()).add(sourceFileKey);
  }

  private static void validateMethod(ExecutionRegion region, SourceTopology topology, Map<String, SourceFile> sources,
      List<SyntaxNode> declarations, Set<String> memberSources, OccurrenceIndex.Builder<String> rootOwners,
      OccurrenceIndex.Builder<String> auxiliaryOwners) {
    var facts = region.facts();
    var methodKey = facts.scope().key();
    var root = facts.root();
    var source = sources.get(root.sourceFileKey());
    if (source == null) {
      throw new IllegalArgumentException("Method body source is not in the captured snapshot: " + methodKey);
    }
    if (memberSources.stream().anyMatch(key -> !root.sourceFileKey().equals(key))
        || (!declarations.isEmpty() && declarations.stream().noneMatch(node -> root.sourceFileKey().equals(node.sourceFileKey())))) {
      throw new IllegalArgumentException("Method body source must match its declared member owner: " + methodKey);
    }
    var previousRootOwner = rootOwners.putIfAbsent(root, methodKey);
    if (previousRootOwner != null) {
      throw new IllegalArgumentException(
          "Method body root is shared by executable declarations: " + previousRootOwner + " and " + methodKey);
    }
    if (facts.methodOrigin().orElseThrow() == MethodBodyOrigin.SOURCE_AST) {
      if (!OccurrenceIndex.same(root, topology.nodes().get(root.nodeId()))) {
        throw new IllegalArgumentException("Source method body must be the identical aggregate occurrence: " + methodKey);
      }
      if (declarations.size() != 1 || declarations.getFirst().children(SyntaxRole.BODY).size() != 1
          || !OccurrenceIndex.same(root, declarations.getFirst().requiredChild(SyntaxRole.BODY))) {
        throw new IllegalArgumentException("Source method body must belong to its unique executable declaration: " + methodKey);
      }
      return;
    }
    for (var node : region.descendants()) {
      var previousOwner = auxiliaryOwners.putIfAbsent(node, methodKey);
      if (previousOwner != null) {
        throw new IllegalArgumentException(
            "Auxiliary method body occurrence is shared by executable declarations: " + previousOwner + " and " + methodKey);
      }
      if (OccurrenceIndex.same(node, topology.nodes().get(node.nodeId()))) {
        throw new IllegalArgumentException("Auxiliary execution body cannot also be a source AST occurrence: " + methodKey);
      }
      validateAuxiliaryBodyNode(node, source, methodKey);
    }
    if (declarations.stream().anyMatch(declaration -> !declaration.children(SyntaxRole.BODY).isEmpty())) {
      throw new IllegalArgumentException("A published written body must retain its source occurrence provenance: " + methodKey);
    }
  }

  private static void validateDeclarationScope(SourceExecutionFacts facts, SourceTopology topology, SourceDeclarations declarations,
      Map<String, DeclarationMemberOrder> memberOrders) {
    var root = facts.root();
    if (!OccurrenceIndex.same(root, topology.nodes().get(root.nodeId()))) {
      throw new IllegalArgumentException("A declaration scope requires its identical canonical source anchor");
    }
    String ownerKey;
    if (facts.scope() instanceof ExecutionScope.Field field) {
      if (root.syntaxKind() != SyntaxKind.FIELD_DECLARATION || !(root.value() instanceof SyntaxValue.VariableDeclaration declaration)
          || !declaration.declarationKey().equals(field.key()) || !declaration.ownerKey().equals(field.ownerKey())) {
        throw new IllegalArgumentException("A field scope requires its exact source declaration and owner");
      }
      ownerKey = field.ownerKey();
    } else if (facts.scope() instanceof ExecutionScope.EnumConstant constant) {
      if (root.syntaxKind() != SyntaxKind.ENUM_CONSTANT_DECLARATION
          || !(root.value() instanceof SyntaxValue.VariableDeclaration declaration) || !declaration.declarationKey().equals(constant.key())
          || !declaration.ownerKey().equals(constant.ownerKey())) {
        throw new IllegalArgumentException("An enum scope requires its exact constant declaration and enum owner");
      }
      constant.selectedConstructor().flatMap(SemanticReference::symbolKey).ifPresent(selected -> {
        var members = memberOrders.get(constant.ownerKey());
        if (members != null && members.members().stream().noneMatch(member -> member.declarationKey().equals(selected))) {
          throw new IllegalArgumentException("An enum constant must select an actual constructor in its declared enum");
        }
      });
      ownerKey = constant.ownerKey();
    } else if (facts.scope() instanceof ExecutionScope.Initializer initializer) {
      if (!root.origin().equals(Optional.of(initializer.origin())) || root.syntaxKind() != initializer.kind()) {
        throw new IllegalArgumentException("An initializer scope requires its exact written block occurrence");
      }
      ownerKey = initializer.ownerKey();
    } else {
      throw new IllegalArgumentException("A declaration scope requires field, enum constant or initializer ownership");
    }
    var parent = topology.parents().get(root);
    var role = root.structuralPath().steps().getLast().role();
    if (parent == null || !declarations.ownsMember(parent, ownerKey)
        || !(role == SyntaxRole.MEMBER || (role == SyntaxRole.COMPACT_MEMBER && facts.scope() instanceof ExecutionScope.Field))) {
      throw new IllegalArgumentException("A declaration execution anchor must belong directly to its actual type owner");
    }
  }

  private static void validateAuxiliaryBodyNode(SyntaxNode node, SourceFile source, String methodKey) {
    var sourceFileKey = source.sourceFileKey();
    if (!node.sourceFileKey().equals(sourceFileKey)) {
      throw new IllegalArgumentException("Auxiliary method body descendants must retain their root source: " + methodKey);
    }
    node.origin().ifPresent(origin -> {
      if (!origin.sourceFileKey().equals(sourceFileKey) || origin.endOffsetExclusive() > source.content().length()) {
        throw new IllegalArgumentException("Auxiliary method body origin is outside its captured source: " + methodKey);
      }
    });
    for (var child : node.children()) {
      if (!child.node().structuralPath().equals(node.structuralPath().child(child.role(), child.position()))) {
        throw new IllegalArgumentException("Auxiliary method body child path does not match its attachment: " + methodKey);
      }
    }
  }

}
