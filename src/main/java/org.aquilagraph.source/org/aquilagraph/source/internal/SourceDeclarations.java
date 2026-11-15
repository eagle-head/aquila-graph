package org.aquilagraph.source.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

/** Shared indexes of type and executable declarations with their exact source owners. */
record SourceDeclarations(Map<TypeOwner, List<SyntaxNode>> types, Map<String, List<SyntaxNode>> typesByKey,
    Map<String, List<SyntaxNode>> executables, Map<String, String> packages) {
  private static final int SINGLE_SOURCE_MODULE = 1;

  record TypeOwner(String sourceFileKey, String declarationKey) {
  }

  static SourceDeclarations index(List<SyntaxNode> nodes) {
    var types = new HashMap<TypeOwner, List<SyntaxNode>>();
    for (var node : nodes) {
      if (node.value() instanceof SyntaxValue.TypeDeclaration declaration) {
        addType(types, node, declaration.declarationKey());
      } else if (node.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT) {
        for (var member : node.children(SyntaxRole.COMPACT_MEMBER)) {
          if (member.value() instanceof SyntaxValue.ExecutableDeclaration executable) {
            addType(types, node, executable.ownerKey());
          }
        }
      }
    }
    var executables = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.ExecutableDeclaration).collect(Collectors
        .groupingBy(node -> node.value(SyntaxValue.ExecutableDeclaration.class).declarationKey(), Collectors.toUnmodifiableList()));
    types.replaceAll((owner, candidates) -> List.copyOf(candidates));
    return new SourceDeclarations(Map.copyOf(types), indexTypeKeys(types), Map.copyOf(executables), indexPackages(nodes));
  }

  private static Map<String, List<SyntaxNode>> indexTypeKeys(Map<TypeOwner, List<SyntaxNode>> types) {
    var keys = new HashMap<String, List<SyntaxNode>>();
    types.forEach((owner, candidates) -> keys.computeIfAbsent(owner.declarationKey(), ignored -> new ArrayList<>()).addAll(candidates));
    keys.replaceAll((key, candidates) -> List.copyOf(candidates));
    return Map.copyOf(keys);
  }

  private static Map<String, String> indexPackages(List<SyntaxNode> nodes) {
    var units = nodes.stream().filter(node -> node.value() instanceof SyntaxValue.CompilationUnit).toList();
    var modules = units.stream()
        .filter(unit -> unit.value(SyntaxValue.CompilationUnit.class).form() == SyntaxValue.CompilationUnitForm.MODULAR).count();
    if (modules > SINGLE_SOURCE_MODULE) {
      // The copied unit facts do not assign ordinary sources to distinct captured modules.
      return Map.of();
    }
    var known = new HashMap<String, String>();
    for (var unit : units) {
      unit.optionalChild(SyntaxRole.PACKAGE_DECLARATION).map(node -> node.value(SyntaxValue.PackageDeclaration.class).name())
          .or(() -> modules == 0 && hasUnnamedPackage(unit) ? Optional.of("") : Optional.empty())
          .ifPresent(name -> known.put(unit.sourceFileKey(), name));
    }
    return Map.copyOf(known);
  }

  private static boolean hasUnnamedPackage(SyntaxNode unit) {
    if (unit.value(SyntaxValue.CompilationUnit.class).form() == SyntaxValue.CompilationUnitForm.COMPACT) {
      return true;
    }
    // An absent copied package declaration may also be a withheld source attachment.
    var declarations = unit.children(SyntaxRole.TYPE_DECLARATION).stream().map(SyntaxNode::value)
        .filter(SyntaxValue.TypeDeclaration.class::isInstance).map(SyntaxValue.TypeDeclaration.class::cast).toList();
    return !declarations.isEmpty() && declarations.stream().allMatch(SourceDeclarations::hasUnqualifiedKey);
  }

  private static boolean hasUnqualifiedKey(SyntaxValue.TypeDeclaration declaration) {
    var key = declaration.declarationKey();
    return key.startsWith("type:")
        ? key.equals("type:" + declaration.name())
        : key.equals(new JavaTypeDeclarationIdentity(Optional.empty(), declaration.name()).declarationKey());
  }

  private static void addType(Map<TypeOwner, List<SyntaxNode>> types, SyntaxNode node, String declarationKey) {
    var candidates = types.computeIfAbsent(new TypeOwner(node.sourceFileKey(), declarationKey), ignored -> new ArrayList<>());
    if (candidates.isEmpty() || !OccurrenceIndex.same(node, candidates.getLast())) {
      candidates.add(node);
    }
  }

  boolean ownsMember(SyntaxNode parent, String declarationKey) {
    return typeOccurrences(parent.sourceFileKey(), declarationKey).stream().anyMatch(candidate -> OccurrenceIndex.same(candidate, parent));
  }

  List<SyntaxNode> typeOccurrences(String sourceFileKey, String declarationKey) {
    return types.getOrDefault(new TypeOwner(sourceFileKey, declarationKey), List.of());
  }

  Optional<SyntaxNode> uniqueType(String declarationKey) {
    var candidates = typesByKey.getOrDefault(declarationKey, List.of());
    return candidates.size() == 1
        ? Optional.of(candidates.getFirst()).filter(node -> node.value() instanceof SyntaxValue.TypeDeclaration)
        : Optional.empty();
  }

  boolean samePackage(SyntaxNode left, SyntaxNode right) {
    if (left.sourceFileKey().equals(right.sourceFileKey())) {
      return true;
    }
    var leftPackage = packages.get(left.sourceFileKey());
    return leftPackage != null && leftPackage.equals(packages.get(right.sourceFileKey()));
  }

  List<SyntaxNode> executables(String declarationKey) {
    return executables.getOrDefault(declarationKey, List.of());
  }

  void validateEvidence(Map<String, DeclarationMemberOrder> memberOrders, Map<String, DeclarationTypeEvidence> declarationTypes) {
    for (var order : memberOrders.values()) {
      if (typeOccurrences(order.sourceFileKey(), order.ownerTypeKey()).isEmpty()) {
        throw new IllegalArgumentException("Declaration member order requires an actual source-owned declaration");
      }
    }
    for (var evidence : declarationTypes.values()) {
      if (typeOccurrences(evidence.sourceFileKey(), evidence.ownerTypeKey()).isEmpty()) {
        throw new IllegalArgumentException("Declaration type evidence requires an actual source-owned canonical declaration");
      }
    }
  }
}
