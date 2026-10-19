package org.aquilagraph.source.api;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Correlates parsed declaration attachments with independently retained attributed member order. */
final class AttributedDeclarationPaths {
  private final Map<String, Map<SourceOwnerPath, DeclarationMemberOrder.TreeOrder>> orders;
  private final Map<String, Map<SourceOwnerPath, JavaSourceKind>> sourceKinds;
  private final Map<String, Map<SourceOwnerPath, Integer>> statementOffsets = new HashMap<>();

  AttributedDeclarationPaths(List<DeclarationMemberOrder> declarations, Map<String, SyntaxNode> nodes,
      Map<String, WrittenSourceAssociation> associations) {
    sourceKinds = associations.values().stream()
        .filter(
            association -> association.normalizedExpression().map(value -> value.occurrencePath().isEmpty()).orElse(true))
        .collect(
            Collectors
                .groupingBy(WrittenSourceAssociation::sourceFileKey,
                    Collectors
                        .toMap(
                            WrittenSourceAssociation::ownerPath, association -> association.normalizedExpression()
                                .map(NormalizedExpressionCorrespondence::compilerKind).orElse(association.sourceKind()),
                            (first, second) -> {
                              if (first != second) {
                                throw new IllegalArgumentException("Parsed source owners require one exact source kind");
                              }
                              return first;
                            })));
    for (var declaration : declarations) {
      declaration.attributedTreeOrder().ifPresent(order -> {
        validateOwner(declaration, order, nodes, associations);
      });
    }
    orders = declarations.stream().filter(declaration -> declaration.attributedTreeOrder().isPresent())
        .collect(Collectors.groupingBy(DeclarationMemberOrder::sourceFileKey,
            Collectors.toMap(declaration -> declaration.attributedTreeOrder().orElseThrow().ownerPath(),
                declaration -> declaration.attributedTreeOrder().orElseThrow(), (first, second) -> {
                  throw new IllegalArgumentException("Attributed declaration orders require unique parsed type owners");
                })));
  }

  Optional<SyntaxStructuralPath> compilerPath(String sourceFileKey, SourceOwnerPath ownerPath) {
    var parsed = SourceOwnerPath.root();
    var attributed = SyntaxStructuralPath.sourceFileRoot();
    var sourceOrders = orders.getOrDefault(sourceFileKey, Map.of());
    for (var step : ownerPath.steps()) {
      var next = parsed.child(step.role(), step.ordinal());
      if (step.role() == SourceOwnerPath.Role.TYPE_DECLARATION && parsed.isEmpty()) {
        attributed = attributed.child(SyntaxRole.TYPE_DECLARATION, step.ordinal());
      } else if (step.role() == SourceOwnerPath.Role.MEMBER) {
        var order = sourceOrders.get(parsed);
        if (order == null) {
          return Optional.empty();
        }
        var position = order.members().indexOf(Optional.of(next));
        if (position < 0) {
          throw new IllegalArgumentException("Attributed declaration order omits its actual parsed member");
        }
        attributed = attributed.child(SyntaxRole.MEMBER, position);
      } else {
        var parentKind = Optional.ofNullable(sourceKinds.getOrDefault(sourceFileKey, Map.of()).get(parsed));
        var role = JavaSourcePathRoles.syntaxRole(step.role(), parentKind);
        var offset = role == SyntaxRole.STATEMENT ? statementOffsets.getOrDefault(sourceFileKey, Map.of()).getOrDefault(parsed, 0) : 0;
        attributed = attributed.child(role, step.ordinal() + offset);
      }
      parsed = next;
    }
    return Optional.of(attributed);
  }

  boolean requiresExactPath(String sourceFileKey, SourceOwnerPath ownerPath) {
    // Earlier formats did not retain attributed member order for ordinary method bodies.
    return orders.containsKey(sourceFileKey)
        || ownerPath.steps().stream().anyMatch(step -> step.role() != SourceOwnerPath.Role.TYPE_DECLARATION
            && step.role() != SourceOwnerPath.Role.MEMBER && step.role() != SourceOwnerPath.Role.BODY);
  }

  void retainStatementOffset(String sourceFileKey, SourceOwnerPath ownerPath, int offset) {
    statementOffsets.computeIfAbsent(sourceFileKey, ignored -> new HashMap<>()).put(ownerPath, offset);
  }

  private static void validateOwner(DeclarationMemberOrder declaration, DeclarationMemberOrder.TreeOrder order,
      Map<String, SyntaxNode> nodes, Map<String, WrittenSourceAssociation> associations) {
    var owner = nodes.values().stream().filter(node -> node.sourceFileKey().equals(declaration.sourceFileKey()))
        .filter(
            node -> node.value() instanceof SyntaxValue.TypeDeclaration type && type.declarationKey().equals(declaration.ownerTypeKey()))
        .findFirst();
    if (owner.isPresent()) {
      var association = associations.get(owner.orElseThrow().nodeId());
      if (association == null || !association.ownerPath().equals(order.ownerPath())) {
        throw new IllegalArgumentException("Attributed tree order requires its exact parsed type declaration");
      }
    } else {
      var compact = nodes.values().stream().filter(node -> node.sourceFileKey().equals(declaration.sourceFileKey())).anyMatch(
          node -> node.value() instanceof SyntaxValue.CompilationUnit unit && unit.form() == SyntaxValue.CompilationUnitForm.COMPACT
              && node.children(SyntaxRole.COMPACT_MEMBER).stream()
                  .anyMatch(member -> member.value() instanceof SyntaxValue.ExecutableDeclaration executable
                      && executable.ownerKey().equals(declaration.ownerTypeKey())));
      if (!compact || !order.ownerPath().equals(SourceOwnerPath.root().child(SourceOwnerPath.Role.TYPE_DECLARATION, 0))) {
        throw new IllegalArgumentException("Attributed tree order requires its actual compact type owner");
      }
    }
    var expected = new HashSet<SourceOwnerPath>();
    for (var association : associations.values()) {
      if (association.sourceFileKey().equals(declaration.sourceFileKey())
          && association.ownerPath().parent().equals(Optional.of(order.ownerPath()))
          && association.ownerPath().lastStep().orElseThrow().role() == SourceOwnerPath.Role.MEMBER) {
        expected.add(association.ownerPath());
      }
    }
    Set<SourceOwnerPath> retained = new HashSet<>();
    order.members().forEach(member -> member.ifPresent(retained::add));
    if (!retained.equals(expected)) {
      throw new IllegalArgumentException("Attributed tree order must correspond to every actual parsed source member");
    }
  }

}
